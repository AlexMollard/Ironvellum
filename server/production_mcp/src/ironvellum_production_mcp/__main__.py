import argparse
import asyncio
import logging
import sys

import uvicorn

from .backend import Failure, SupabaseBackend
from .config import Settings
from .server import create_app, fingerprint, verify_backend


async def configure(settings):
    manifest = settings.catalogue()
    expected = fingerprint(manifest)
    backend = SupabaseBackend(settings)
    try:
        row = await backend.gateway("configuration.get", {})
        if row["resourceUrl"] not in (None, settings.resource):
            raise Failure("conflict")
        await backend.gateway("catalogue.put", manifest)
        installed = await backend.gateway("configuration.get", {})
        if (installed["catalogueCount"], installed["catalogueHash"]) != expected:
            raise Failure("unavailable")
        await backend.gateway("configuration.configure", {"resourceUrl": settings.resource})
        await verify_backend(settings, backend, expected)
    finally:
        await backend.close()


def main():
    parser = argparse.ArgumentParser()
    cmds = parser.add_subparsers(dest="command", required=True)
    serve = cmds.add_parser("serve")
    serve.add_argument("--host", default="127.0.0.1")
    serve.add_argument("--port", type=int, default=8000)
    cmds.add_parser("configure-backend")
    args = parser.parse_args()
    # Upstream protocol and HTTP diagnostics can include user-supplied strings. SQL owns the safe audit.
    for namespace in ("mcp", "httpx2", "httpcore2"):
        logger = logging.getLogger(namespace)
        logger.handlers = [logging.NullHandler()]
        logger.propagate = False
    try:
        settings = Settings.from_env()
        if args.command == "configure-backend":
            asyncio.run(configure(settings))
            print("Backend configuration verified.")
        else:
            uvicorn.run(
                create_app(settings), host=args.host, port=args.port, access_log=False, proxy_headers=False
            )
    except (Failure, ValueError):
        sys.exit(
            "MCP configuration unavailable. Check the configured origins, keys, schema and movement manifest."
        )


if __name__ == "__main__":
    main()
