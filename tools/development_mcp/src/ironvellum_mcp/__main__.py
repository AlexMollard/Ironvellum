"""Start the local Ironvellum development MCP server over stdio."""

import argparse
from pathlib import Path

from .adb import AdbClient
from .operations import DevelopmentTools
from .server import create_server


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", required=True, type=Path, help="Absolute path to the Ironvellum checkout")
    args = parser.parse_args()
    if not args.repo_root.is_absolute() or not (args.repo_root / "tools" / "device.py").is_file():
        parser.error("--repo-root must be the absolute path to an Ironvellum checkout")
    create_server(DevelopmentTools(AdbClient(args.repo_root))).run(transport="stdio")


if __name__ == "__main__":
    main()
