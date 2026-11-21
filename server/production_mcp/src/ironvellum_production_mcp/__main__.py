import argparse
import asyncio
import logging
import re
import sys

import uvicorn

from .backend import Failure, SupabaseBackend
from .config import Settings
from .server import create_app, fingerprint, verify_configuration


async def configure(settings, previous_catalogue_hash=None):
    if previous_catalogue_hash is not None and not re.fullmatch(r"[a-f0-9]{64}", previous_catalogue_hash):
        raise ValueError("Previous catalogue fingerprint must be lowercase SHA-256")
    manifest = settings.catalogue()
    expected = fingerprint(manifest)
    backend = SupabaseBackend(settings)
    try:
        installed = await backend.gateway(
            "configuration.configure",
            {
                "resourceUrl": settings.resource,
                "keyVerification": settings.key_verification,
                "previousCatalogueHash": previous_catalogue_hash,
                "catalogue": manifest,
            },
        )
        verify_configuration(settings, installed, expected)
    finally:
        await backend.close()


def main():
    parser = argparse.ArgumentParser()
    cmds = parser.add_subparsers(dest="command", required=True)
    serve = cmds.add_parser("serve")
    serve.add_argument("--host", default="127.0.0.1")
    serve.add_argument("--port", type=int, default=8000)
    setup = cmds.add_parser("configure-backend")
    setup.add_argument(
        "--previous-catalogue-hash", help="Expected installed SHA-256 for an intentional catalogue update"
    )
    args = parser.parse_args()
    # Upstream protocol and HTTP diagnostics can include user-supplied strings. SQL owns the safe audit.
    for namespace in ("mcp", "httpx2", "httpcore2"):
        logger = logging.getLogger(namespace)
        logger.handlers = [logging.NullHandler()]
        logger.propagate = False
    try:
        settings = Settings.from_env()
        if args.command == "configure-backend":
            asyncio.run(configure(settings, args.previous_catalogue_hash))
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
