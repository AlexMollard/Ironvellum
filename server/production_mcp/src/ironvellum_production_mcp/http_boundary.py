"""Bound the raw HTTP request before protocol parsing or authentication."""

import json
import re
from urllib.parse import urlsplit

from starlette.datastructures import Headers, MutableHeaders
from starlette.responses import JSONResponse

from .backend import Failure
from .config import MAX_BODY, MAX_RESULT


def unique(pairs):
    values = {}
    for key, value in pairs:
        if key in values:
            raise ValueError("duplicate")
        values[key] = value
    return values


def error_response(code, status=None):
    status = status or {
        "forbidden": 403,
        "not_found": 404,
        "rate_limited": 429,
        "conflict": 409,
        "unavailable": 503,
        "result_too_large": 502,
    }.get(code, 400)
    return JSONResponse(
        {"error": code},
        status_code=status,
        headers={
            "Cache-Control": "no-store",
            "Referrer-Policy": "no-referrer",
            **({"Retry-After": "60"} if status == 429 else {}),
        },
    )


def invalid_constant(_):
    raise ValueError("invalid number")


class HttpBoundary:
    def __init__(self, app, issuer):
        self.app = app
        self.host = urlsplit(issuer).netloc

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            return await self.app(scope, receive, send)
        if sum(len(k) + len(v) for k, v in scope["headers"]) > 32768:
            return await error_response("invalid_request", 431)(scope, receive, send)
        headers = Headers(scope=scope)
        if "content-length" in headers and "transfer-encoding" in headers:
            return await error_response("invalid_request")(scope, receive, send)
        path = scope["path"]
        # All service routes, including request UUIDs and static assets, use literal ASCII.
        # Ingress must preserve the raw target so routing cannot silently normalize an alias.
        if not path.isascii() or scope.get("raw_path") != path.encode("ascii"):
            return await error_response("not_found")(scope, receive, send)
        for name in (
            "host",
            "origin",
            "authorization",
            "content-type",
            "content-length",
            "cookie",
            "transfer-encoding",
            "mcp-protocol-version",
            "mcp-session-id",
            "mcp-method",
            "mcp-name",
        ):
            if len(headers.getlist(name)) > 1:
                return await error_response("invalid_request")(scope, receive, send)
        if headers.get("host") != self.host:
            return await error_response("invalid_request")(scope, receive, send)
        if len(scope["query_string"]) > 8192 or len(headers.get("authorization", "")) > 16400:
            return await error_response("invalid_request", 413)(scope, receive, send)
        if scope["query_string"] and (
            path not in {"/authorize", "/signin/callback"} or scope["method"] != "GET"
        ):
            return await error_response("invalid_request")(scope, receive, send)
        length = headers.get("content-length")
        if length and (not re.fullmatch(r"[0-9]{1,12}", length) or int(length) > MAX_BODY):
            return await error_response("invalid_request", 413)(scope, receive, send)
        body = bytearray()
        if scope["method"] in {"POST", "PUT", "PATCH"} or length or "transfer-encoding" in headers:
            while True:
                message = await receive()
                if message["type"] == "http.disconnect":
                    return
                part = message.get("body", b"")
                if len(body) + len(part) > MAX_BODY:
                    return await error_response("invalid_request", 413)(scope, receive, send)
                body.extend(part)
                if not message.get("more_body"):
                    break
        if body and (path in {"/mcp", "/register"} or path.startswith("/consent/api/")):
            if headers.get("content-type", "").split(";")[0].lower() != "application/json":
                return await error_response("invalid_request", 415)(scope, receive, send)
            try:
                obj = json.loads(
                    body.decode("utf8"), object_pairs_hook=unique, parse_constant=invalid_constant
                )
                # Reject unpaired surrogates and non-finite exponent values before passing to SDK/loggers.
                json.dumps(obj, ensure_ascii=False, allow_nan=False).encode()
                if not isinstance(obj, dict):
                    raise TypeError
            except (ValueError, TypeError, UnicodeError, RecursionError):
                return await error_response("invalid_request")(scope, receive, send)

        delivered = False

        async def replay():
            nonlocal delivered
            if not delivered:
                delivered = True
                return {"type": "http.request", "body": bytes(body), "more_body": False}
            return await receive()

        # The SDK includes detailed validation diagnostics. Keep only safe protocol error codes.
        json_start = None
        parts = bytearray()

        async def protected_send(event):
            nonlocal json_start
            if event["type"] == "http.response.start":
                h = MutableHeaders(scope=event)
                h["Cache-Control"] = "no-store"
                h["Referrer-Policy"] = "no-referrer"
                h["X-Content-Type-Options"] = "nosniff"
                if path == "/mcp" and h.get("content-type", "").startswith("application/json"):
                    json_start = event
                    return
            if json_start is not None and event["type"] == "http.response.body":
                parts.extend(event.get("body", b""))
                if event.get("more_body"):
                    return
                try:
                    doc = json.loads(parts)
                    if isinstance(doc.get("error"), dict):
                        doc["error"] = {"code": doc["error"].get("code", -32600), "message": "Request failed"}
                        parts[:] = json.dumps(doc, separators=(",", ":")).encode()
                    if len(parts) > MAX_RESULT:
                        raise ValueError
                except (TypeError, ValueError, AttributeError, RecursionError):
                    return await error_response("result_too_large", 502)(scope, replay, send)
                MutableHeaders(scope=json_start)["Content-Length"] = str(len(parts))
                await send(json_start)
                return await send({**event, "body": bytes(parts)})
            await send(event)

        try:
            await self.app(scope, replay, protected_send)
        except Failure as exc:
            await error_response(exc.code)(scope, replay, send)
