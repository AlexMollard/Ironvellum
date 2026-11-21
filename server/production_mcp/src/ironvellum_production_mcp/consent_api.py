import hmac
import json
import re
from urllib.parse import urlencode
from uuid import UUID

from cryptography.fernet import Fernet, InvalidToken
from starlette.responses import JSONResponse

from .backend import Failure
from .oauth import digest, secret


class Consent:
    def __init__(self, settings, backend):
        self.settings = settings
        self.backend = backend
        self.cipher = Fernet(settings.encryption_key)
        self.secure = settings.issuer.startswith("https://")
        self.cookie = "__Host-iv-consent" if self.secure else "iv-consent"

    def bind(self, response, request_id):
        UUID(request_id)
        value = json.dumps(["consent-v1", self.settings.issuer, request_id, secret()]).encode()
        response.set_cookie(
            self.cookie,
            self.cipher.encrypt(value).decode("ascii"),
            max_age=600,
            path="/",
            secure=self.secure,
            httponly=True,
            samesite="lax",
        )

    def binding(self, request):
        try:
            data = self.cipher.decrypt(request.cookies.get(self.cookie, ""), ttl=600)
            purpose, issuer, bound, csrf = json.loads(data)
            if (
                purpose != "consent-v1"
                or issuer != self.settings.issuer
                or bound != str(request.path_params["request_id"])
            ):
                raise ValueError
            return bound, csrf
        except (InvalidToken, ValueError, TypeError):
            raise Failure("forbidden") from None

    async def api(self, request):
        request_id, csrf = self.binding(request)
        if request.method == "POST":
            if request.headers.get("origin") != self.settings.issuer:
                raise Failure("forbidden")
            try:
                body = await request.json()
            except ValueError:
                raise Failure("invalid_arguments") from None
            if not isinstance(body, dict) or set(body) != {"approved", "scopes", "csrf"}:
                raise Failure("invalid_arguments")
            if not isinstance(body["csrf"], str) or not hmac.compare_digest(
                body["csrf"].encode(), csrf.encode()
            ):
                raise Failure("forbidden")
            auth = request.headers.get("authorization", "")
            scheme, _, bearer = auth.partition(" ")
            if (
                scheme.lower() != "bearer"
                or len(bearer) > 16384
                or not re.fullmatch(r"[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+", bearer)
            ):
                raise Failure("forbidden")
        row = await self.backend.gateway("authorization.get", {"requestId": request_id})
        if row["resourceUrl"] != self.settings.resource or row["requestId"] != request_id:
            raise Failure("invalid_request")
        if request.method == "GET":
            return JSONResponse(
                {
                    "requestId": request_id,
                    "clientName": row["clientName"],
                    "scopes": row["scopes"],
                    "expiresAtMs": row["expiresAtMs"],
                    "backendUrl": self.settings.backend_origin,
                    "publicKey": self.settings.public_key,
                    "csrf": csrf,
                }
            )
        approved, scopes = body["approved"], body["scopes"]
        if (
            type(approved) is not bool
            or not isinstance(scopes, list)
            or len(scopes) > 4
            or any(not isinstance(s, str) or s not in row["scopes"] for s in scopes)
            or len(set(scopes)) != len(scopes)
            or bool(scopes) != approved
        ):
            raise Failure("invalid_scope")
        raw_code = secret() if approved else None
        decision = await self.backend.consent(
            bearer,
            {
                "p_request_id": request_id,
                "p_approved": approved,
                "p_scopes": scopes,
                "p_code_hash": digest(raw_code) if raw_code else None,
            },
        )
        if decision["approved"] is not approved:
            raise Failure("unavailable")
        callback = row["redirectUri"]
        fields = {"code": raw_code} if approved else {"error": "access_denied"}
        if row["state"] is not None:
            fields["state"] = row["state"]
        target = callback + ("&" if "?" in callback else "?") + urlencode(fields)
        response = JSONResponse({"redirectUrl": target})
        response.delete_cookie(self.cookie, path="/", secure=self.secure, httponly=True, samesite="lax")
        return response
