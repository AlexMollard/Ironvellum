import hashlib
import re
import secrets
import time

from cryptography.fernet import Fernet, InvalidToken
from mcp.server.auth.provider import (
    AccessToken,
    AuthorizationCode,
    AuthorizeError,
    OAuthAuthorizationServerProvider,
    RefreshToken,
    RegistrationError,
    TokenError,
)
from mcp.shared.auth import OAuthClientInformationFull, OAuthToken
from pydantic import ValidationError

from .backend import Failure

OPAQUE = re.compile(r"[A-Za-z0-9_-]{43}", re.ASCII)
CLIENT = re.compile(r"[A-Za-z0-9._~-]{1,128}", re.ASCII)


def digest(token):
    return hashlib.sha256(token.encode("ascii")).hexdigest()


def secret():
    return secrets.token_urlsafe(32)


class Provider(OAuthAuthorizationServerProvider[AuthorizationCode, RefreshToken, AccessToken]):
    def __init__(self, settings, backend):
        self.settings = settings
        self.backend = backend
        self.cipher = Fernet(settings.encryption_key)

    async def get_client(self, client_id):
        if not CLIENT.fullmatch(client_id):
            return None
        try:
            row = await self.backend.gateway("client.get", {"clientId": client_id})
            data = self.cipher.decrypt(row["metadataCiphertext"])
            info = OAuthClientInformationFull.model_validate_json(data)
            if (
                info.client_id != client_id
                or row["clientId"] != client_id
                or [str(uri) for uri in info.redirect_uris or ()] != row["redirectUris"]
                or (info.client_name or "Unverified client") != row["clientName"]
            ):
                raise Failure("unavailable")
            return info
        except Failure as exc:
            if exc.code == "not_found":
                return None
            raise
        except (InvalidToken, KeyError, ValueError, ValidationError, TypeError):
            raise Failure("unavailable") from None

    async def register_client(self, info):
        ciphertext = self.cipher.encrypt(info.model_dump_json(exclude_none=True).encode()).decode("ascii")
        if len(ciphertext) > 32768:
            raise RegistrationError("invalid_client_metadata")
        await self.backend.gateway(
            "client.register",
            {
                "clientId": info.client_id,
                "clientName": info.client_name or "Unverified client",
                "metadataCiphertext": ciphertext,
                "keyVerification": self.settings.key_verification,
                "redirectUris": [str(uri) for uri in info.redirect_uris],
            },
        )

    async def authorize(self, client, params):
        if params.resource != self.settings.resource:
            raise AuthorizeError("invalid_target")
        try:
            row = await self.backend.gateway(
                "authorization.create",
                {
                    "clientId": client.client_id,
                    "resourceUrl": self.settings.resource,
                    "redirectUri": str(params.redirect_uri),
                    "redirectUriExplicit": params.redirect_uri_provided_explicitly,
                    "scopes": params.scopes or client.scope.split(),
                    "codeChallenge": params.code_challenge,
                    "state": params.state,
                },
            )
        except Failure as exc:
            if exc.code in {"invalid_scope", "invalid_request"}:
                raise AuthorizeError(exc.code) from None
            raise AuthorizeError("temporarily_unavailable", exc.code) from None
        return self.settings.issuer + "/consent/" + row["requestId"]

    async def _load(self, op, args, invalid):
        try:
            row = await self.backend.gateway(op, args)
        except Failure as exc:
            if exc.code == invalid:
                return None
            raise
        if row["resourceUrl"] != self.settings.resource or (
            "clientId" in args and row["clientId"] != args["clientId"]
        ):
            return None
        return row

    async def load_authorization_code(self, client, authorization_code):
        if not OPAQUE.fullmatch(authorization_code):
            return None
        row = await self._load(
            "code.load",
            {"clientId": client.client_id, "codeHash": digest(authorization_code)},
            "invalid_grant",
        )
        if row:
            return AuthorizationCode(
                code=authorization_code,
                scopes=row["scopes"],
                client_id=row["clientId"],
                expires_at=row["expiresAtMs"] / 1000,
                code_challenge=row["codeChallenge"],
                redirect_uri=row["redirectUri"],
                redirect_uri_provided_explicitly=row["redirectUriExplicit"],
                resource=row["resourceUrl"],
                subject=row["grantId"],
            )

    async def load_refresh_token(self, client, refresh_token):
        if not OPAQUE.fullmatch(refresh_token):
            return None
        row = await self._load(
            "refresh.load",
            {"clientId": client.client_id, "refreshHash": digest(refresh_token)},
            "invalid_grant",
        )
        if row:
            return RefreshToken(
                token=refresh_token,
                client_id=row["clientId"],
                scopes=row["scopes"],
                expires_at=row["expiresAtMs"] // 1000,
                subject=row["grantId"],
                resource=row["resourceUrl"],
            )

    async def load_access_token(self, token):
        if not OPAQUE.fullmatch(token):
            return None
        row = await self._load("access.load", {"accessHash": digest(token)}, "invalid_token")
        if row:
            return AccessToken(
                token=token,
                client_id=row["clientId"],
                scopes=row["scopes"],
                resource=row["resourceUrl"],
                expires_at=row["expiresAtMs"] // 1000,
                subject=row["grantId"],
                claims={"iss": self.settings.issuer},
            )

    async def _issue(self, op, args, refresh_field):
        access, refresh = secret(), secret()
        args.update(resourceUrl=self.settings.resource, accessHash=digest(access))
        args[refresh_field] = digest(refresh)
        try:
            row = await self.backend.gateway(op, args)
        except Failure as exc:
            if exc.code in {"invalid_grant", "invalid_scope"}:
                raise TokenError(exc.code) from None
            raise
        if row["resourceUrl"] != self.settings.resource or row["clientId"] != args["clientId"]:
            raise Failure("unavailable")
        return OAuthToken(
            access_token=access,
            refresh_token=refresh,
            token_type="Bearer",
            expires_in=max(0, int(row["accessExpiresAtMs"] / 1000 - time.time())),
            scope=" ".join(row["scopes"]),
        )

    async def exchange_authorization_code(self, client, authorization_code):
        # The SDK has just compared SHA256(the presented verifier) with this loaded challenge.
        return await self._issue(
            "code.exchange",
            {
                "clientId": client.client_id,
                "redirectUri": str(authorization_code.redirect_uri),
                "codeChallenge": authorization_code.code_challenge,
                "codeHash": digest(authorization_code.code),
            },
            "refreshHash",
        )

    async def exchange_refresh_token(self, client, refresh_token, scopes):
        return await self._issue(
            "refresh.exchange",
            {"clientId": client.client_id, "refreshHash": digest(refresh_token.token), "scopes": scopes},
            "newRefreshHash",
        )

    async def revoke_for_client(self, client_id, raw):
        if OPAQUE.fullmatch(raw):
            await self.backend.gateway("token.revoke", {"clientId": client_id, "tokenHash": digest(raw)})

    async def revoke_token(self, token):
        await self.revoke_for_client(token.client_id, token.token)
