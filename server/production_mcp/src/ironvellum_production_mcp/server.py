from contextlib import asynccontextmanager
from hashlib import sha256
from urllib.parse import urlsplit

from mcp.server.auth.provider import ProviderTokenVerifier
from mcp.server.auth.routes import create_protected_resource_routes
from mcp.server.auth.settings import AuthSettings
from mcp.server.transport_security import TransportSecuritySettings
from starlette.applications import Starlette
from starlette.middleware import Middleware
from starlette.responses import JSONResponse
from starlette.routing import Mount, Route

from . import auth_routes
from .backend import Failure, SupabaseBackend
from .config import MAX_BODY, SCOPES
from .consent_api import Consent
from .http_boundary import HttpBoundary, error_response
from .oauth import Provider
from .tools import mcp_server


def fingerprint(manifest):
    h = sha256(b"ironvellum-movements-v1\0")
    for row in sorted(manifest["movements"], key=lambda m: m["key"].encode()):
        for key in ("key", "name", "metric"):
            h.update(row[key].encode() + b"\0")
    return len(manifest["movements"]), h.hexdigest()


def verify_configuration(settings, current, expected):
    if (
        current.get("resourceUrl") != settings.resource
        or current.get("keyVerification") != settings.key_verification
        or (current.get("catalogueCount"), current.get("catalogueHash")) != expected
    ):
        raise Failure("unavailable")


async def verify_backend(settings, backend, expected):
    verify_configuration(settings, await backend.gateway("configuration.get", {}), expected)


def create_app(settings, backend=None):
    expected = fingerprint(settings.catalogue())
    managed = backend is None
    if managed:
        backend = SupabaseBackend(settings)
    provider = Provider(settings, backend)
    consent = Consent(settings, backend)
    auth = AuthSettings(
        issuer_url=settings.issuer, resource_server_url=settings.resource, validate_token_resource=True
    )
    mcp = mcp_server(backend)
    transport = mcp.streamable_http_app(
        auth=auth,
        token_verifier=ProviderTokenVerifier(provider),
        streamable_http_path="/mcp",
        stateless_http=True,
        json_response=True,
        max_request_body_size=MAX_BODY,
        transport_security=TransportSecuritySettings(
            enable_dns_rebinding_protection=True,
            allowed_hosts=[urlsplit(settings.issuer).netloc],
            allowed_origins=[settings.issuer],
        ),
    )

    # A TLS-terminating proxy may use HTTP internally. Never redirect a POST using that scheme.
    transport.router.redirect_slashes = False

    @asynccontextmanager
    async def lifespan(app):
        try:
            await verify_backend(settings, backend, expected)
            async with mcp.session_manager.run():
                yield
        finally:
            if managed:
                await backend.close()

    async def health(request):
        await verify_backend(settings, backend, expected)
        return JSONResponse({"ready": True})

    async def failure(request, exc):
        return error_response(exc.code)

    app = Starlette(
        routes=[
            Route("/healthz", health),
            *auth_routes.routes(provider, settings, auth, consent),
            *create_protected_resource_routes(
                auth.resource_server_url, [auth.issuer_url], scopes_supported=list(SCOPES)
            ),
            Route("/consent/api/{request_id:uuid}", consent.api, methods=["GET", "POST"]),
            Mount("/", app=transport),
        ],
        lifespan=lifespan,
        middleware=[Middleware(HttpBoundary, issuer=settings.issuer)],
        exception_handlers={Failure: failure},
    )
    app.router.redirect_slashes = False
    return app
