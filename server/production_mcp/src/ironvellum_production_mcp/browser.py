"""Browser routes expose a public configuration and a fixed set of bundled assets."""

from importlib.resources import files

from starlette.responses import HTMLResponse, JSONResponse, Response
from starlette.routing import Route

from .backend import Failure


def browser_routes(settings, consent):
    assets = files(__package__).joinpath("assets")
    headers = {
        "Content-Security-Policy": (
            "default-src 'none'; script-src 'self'; style-src 'self'; font-src 'self'; "
            f"connect-src 'self' {settings.backend_origin}; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"
        ),
        "X-Frame-Options": "DENY",
    }

    def page(content, status=200, script=False):
        return HTMLResponse(
            '<!doctype html><html lang="en"><head><meta charset="utf-8">'
            '<meta name="viewport" content="width=device-width,initial-scale=1">'
            '<title>AI connections · Ironvellum</title><link rel="stylesheet" href="/ui/paper.css">'
            "</head><body><header>Ironvellum</header><main>"
            + content
            + "</main>"
            + ('<script type="module" src="/ui/browser.js"></script>' if script else "")
            + "</body></html>",
            status_code=status,
            headers=headers,
        )

    async def browser_page(request):
        if "request_id" in request.path_params:
            try:
                consent.binding(request)
            except Failure:
                return page(
                    "<h1>Connection request unavailable</h1><p>Start a new connection from your AI app.</p>",
                    403,
                )
        return page('<h1>AI connections</h1><p role="status">Checking your account…</p>', script=True)

    async def config(request):
        return JSONResponse(
            {
                "backendUrl": settings.backend_origin,
                "publicKey": settings.public_key,
                "googleCallback": settings.google_callback,
            }
        )

    async def callback(request):
        return page("<h1>Return to Ironvellum</h1><p>Your original tab will finish signing you in.</p>")

    async def asset(request):
        name = request.path_params["name"]
        types = {
            "browser.js": "text/javascript",
            "paper.css": "text/css",
            "chakra.ttf": "font/ttf",
            "chakra-medium.ttf": "font/ttf",
        }
        if name not in types:
            return Response(status_code=404)
        return Response(assets.joinpath(name).read_bytes(), media_type=types[name])

    return [
        Route("/connections", browser_page),
        Route("/consent/{request_id:uuid}", browser_page),
        Route("/ui-config", config),
        Route("/ui/{name}", asset),
        *([Route("/signin/callback", callback)] if settings.google_callback else []),
    ]
