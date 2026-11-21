import base64
import json
import re
from urllib.parse import parse_qsl, unquote, urlencode, urlsplit

from mcp.server.auth.handlers.authorize import AuthorizationHandler
from mcp.server.auth.handlers.metadata import MetadataHandler
from mcp.server.auth.handlers.register import RegistrationHandler
from mcp.server.auth.handlers.token import TokenHandler
from mcp.server.auth.middleware.client_auth import (
    AuthenticationError,
    ClientAuthenticator,
)
from mcp.server.auth.routes import build_metadata, cors_middleware
from mcp.server.auth.settings import ClientRegistrationOptions, RevocationOptions
from pydantic import AnyHttpUrl, ValidationError
from starlette.requests import Request
from starlette.responses import RedirectResponse, Response
from starlette.routing import Route

from .backend import Failure
from .config import LOOPBACK, SCOPES
from .http_boundary import error_response, unique
from .oauth import CLIENT, OPAQUE

METHODS = ["none", "client_secret_basic", "client_secret_post"]
REG_FIELDS = {
    "redirect_uris",
    "client_name",
    "token_endpoint_auth_method",
    "response_types",
    "grant_types",
    "scope",
    "application_type",
    "software_id",
    "software_version",
}
RESERVED = {"code", "error", "error_description", "error_uri", "state", "iss"}
FORM_FIELDS = {
    "/authorize": {
        "client_id",
        "response_type",
        "code_challenge",
        "code_challenge_method",
        "redirect_uri",
        "scope",
        "state",
        "resource",
    },
    "/token": {
        "client_id",
        "client_secret",
        "grant_type",
        "code",
        "code_verifier",
        "refresh_token",
        "scope",
        "redirect_uri",
        "resource",
    },
    "/revoke": {"client_id", "client_secret", "token", "token_type_hint"},
}


def safe_redirect(value):
    if (
        not isinstance(value, str)
        or len(value) > 2048
        or not value.isascii()
        or any(ord(c) <= 32 or ord(c) >= 127 for c in value)
    ):
        return False
    try:
        url = urlsplit(value)
        if (
            url.scheme != "https"
            and not (url.scheme == "http" and url.hostname in LOOPBACK)
            or not url.hostname
            or url.username is not None
            or url.password is not None
            or "#" in value
            or str(AnyHttpUrl(value)) != value
            or RESERVED & set(dict(parse_qsl(url.query, keep_blank_values=True)))
        ):
            return False
    except ValueError:
        return False
    return True


def valid_scopes(value):
    if not isinstance(value, str):
        return False
    scopes = value.split(" ")
    return len(scopes) == len(set(scopes)) and all(s in SCOPES for s in scopes)


def validate_registration(body):
    if (
        not isinstance(body, dict)
        or set(body) - REG_FIELDS
        or "redirect_uris" not in body
        or body.get("token_endpoint_auth_method", "client_secret_post") not in METHODS
    ):
        raise ValueError
    uris = body["redirect_uris"]
    if (
        not isinstance(uris, list)
        or not 1 <= len(uris) <= 16
        or not all(safe_redirect(u) for u in uris)
        or len(set(uris)) != len(uris)
    ):
        raise ValueError
    for key in ("client_name", "software_id", "software_version"):
        if key in body and (
            not isinstance(body[key], str) or not 1 <= len(body[key]) <= 100 or not body[key].isprintable()
        ):
            raise ValueError
    grants = body.get("grant_types", ["authorization_code", "refresh_token"])
    if (
        not isinstance(grants, list)
        or not 1 <= len(grants) <= 2
        or "authorization_code" not in grants
        or any(g not in ("authorization_code", "refresh_token") for g in grants)
        or len(set(grants)) != len(grants)
        or body.get("response_types", ["code"]) != ["code"]
        or "scope" in body
        and not valid_scopes(body["scope"])
    ):
        raise ValueError


def parsed_form(data):
    if re.search(rb"%(?![A-Fa-f0-9]{2})", data):
        raise ValueError
    return unique(
        parse_qsl(
            data.decode("ascii"),
            keep_blank_values=True,
            strict_parsing=True,
            max_num_fields=20,
            encoding="utf-8",
            errors="strict",
        )
    )


async def prepare(request, settings):
    path = request.url.path
    if request.method == "GET":
        data = request.scope["query_string"]
    else:
        if request.headers.get("content-type", "").split(";")[0] != "application/x-www-form-urlencoded":
            raise ValueError
        data = await request.body()
    form = parsed_form(data)
    if set(form) - FORM_FIELDS[path] or any(not v.isprintable() for v in form.values()):
        raise ValueError
    if (
        "resource" in form
        and form["resource"] != settings.resource
        or path == "/authorize"
        and "resource" not in form
    ):
        raise Failure("invalid_target")
    if "redirect_uri" in form and not safe_redirect(form["redirect_uri"]):
        raise ValueError
    if "scope" in form and not valid_scopes(form["scope"]):
        raise Failure("invalid_scope")
    if path == "/authorize" and (
        form.get("code_challenge_method") != "S256"
        or not OPAQUE.fullmatch(form.get("code_challenge", ""))
        or len(form.get("state", "")) > 2048
    ):
        raise ValueError
    if (
        path == "/token"
        and form.get("grant_type") == "authorization_code"
        and not re.fullmatch(r"[A-Za-z0-9._~-]{43,128}", form.get("code_verifier", ""))
    ):
        raise ValueError
    auth = request.headers.get("authorization")
    if auth:
        if path == "/authorize" or not auth.startswith("Basic ") or "client_secret" in form:
            raise ValueError
        decoded = base64.b64decode(auth[6:], validate=True).decode("utf8")
        username, separator, password = decoded.partition(":")
        username = unquote(username)
        if not separator or not password or form.get("client_id", username) != username:
            raise ValueError
        form["client_id"] = username
    if not CLIENT.fullmatch(form.get("client_id", "")):
        raise ValueError
    if request.method == "GET":
        return request, form
    # Feed the verified form to SDK public Request.form(), including Basic username when omitted.
    wire = urlencode(form).encode()

    async def receive():
        return {"type": "http.request", "body": wire, "more_body": False}

    return Request(request.scope, receive), form


class RegisteredAuthenticator(ClientAuthenticator):
    async def authenticate_request(self, request):
        client = await super().authenticate_request(request)
        form = await request.form()
        if client.token_endpoint_auth_method == "none" and (
            "authorization" in request.headers or "client_secret" in form
        ):
            raise AuthenticationError("Unexpected client credentials")
        return client


def routes(provider, settings, auth, consent):
    options = ClientRegistrationOptions(enabled=True, valid_scopes=list(SCOPES), default_scopes=list(SCOPES))
    meta = build_metadata(auth.issuer_url, None, options, RevocationOptions(enabled=True))
    meta.token_endpoint_auth_methods_supported = METHODS
    meta.revocation_endpoint_auth_methods_supported = METHODS
    authenticator = RegisteredAuthenticator(provider)
    register = RegistrationHandler(provider, options)
    authorize = AuthorizationHandler(provider)
    token = TokenHandler(provider, authenticator)

    async def endpoint(request):
        path = request.url.path
        try:
            if path == "/register":
                validate_registration(await request.json())
                response = await register.handle(request)
            else:
                request, form = await prepare(request, settings)
                if path == "/authorize":
                    response = await authorize.handle(request)
                elif path == "/token":
                    response = await token.handle(request)
                else:
                    client = await authenticator.authenticate_request(request)
                    if not form.get("token"):
                        raise ValueError
                    await provider.revoke_for_client(client.client_id, form["token"])
                    return Response(status_code=200)
        except AuthenticationError:
            return error_response("invalid_client", 401)
        except (ValueError, TypeError, ValidationError):
            return error_response("invalid_client_metadata" if path == "/register" else "invalid_request")
        except Failure as exc:
            return error_response(exc.code)

        if response.status_code == 302:
            location = response.headers["location"]
            target = urlsplit(location)
            pairs = dict(parse_qsl(target.query, keep_blank_values=True))
            if "error" in pairs:
                detail = pairs.pop("error_description", None)
                if pairs["error"] == "temporarily_unavailable":
                    return error_response("rate_limited" if detail == "rate_limited" else "unavailable")
                # The SDK has validated the redirect but rewrites its query. Recover the exact
                # registered string and append only its safe protocol response parameters.
                try:
                    client = await provider.get_client(form["client_id"])
                except Failure as exc:
                    return error_response(exc.code)
                registered = [str(uri) for uri in client.redirect_uris or ()] if client else []
                callback = form.get("redirect_uri")
                if callback is None and len(registered) == 1:
                    callback = registered[0]
                if callback not in registered:
                    return error_response("invalid_request")
                fields = {"error": pairs["error"]}
                if "state" in form:
                    fields["state"] = form["state"]
                return RedirectResponse(callback + ("&" if "?" in callback else "?") + urlencode(fields), 302)
            if not location.startswith(settings.issuer + "/consent/"):
                return error_response("unavailable")
            consent.bind(response, target.path.rsplit("/", 1)[1])
        elif response.status_code >= 400:
            return error_response(json.loads(response.body)["error"], response.status_code)
        return response

    return [
        Route(
            "/.well-known/oauth-authorization-server",
            cors_middleware(MetadataHandler(meta).handle, ["GET", "OPTIONS"]),
            methods=["GET", "OPTIONS"],
        ),
        Route("/authorize", endpoint, methods=["GET", "POST"]),
        *[
            Route(p, cors_middleware(endpoint, ["POST", "OPTIONS"]), methods=["POST", "OPTIONS"])
            for p in ("/token", "/revoke", "/register")
        ],
    ]
