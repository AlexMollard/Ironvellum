import argparse
import asyncio
import logging
from pathlib import Path
import sys
import warnings

from .contract import Refusal, require
from .stdio import run
from .storage import LocalFiles


class Arguments(argparse.ArgumentParser):
    def error(self, message):
        self.exit(2, "Expected --context FILE and --outbox DIRECTORY.\n")


class ManagementArguments(argparse.ArgumentParser):
    def error(self, message):
        from .contract import encode
        result = dict(schemaVersion=1, status="error", code="invalid_arguments", selectedDatasetId=None,
                      sessions=[], nextActions=["check_command_help"])
        sys.stdout.buffer.write(encode(result) + b"\n")
        self.exit(2)


def serve_folder(settings_path):
    from .settings import load_settings
    from .storage import FolderFiles
    from .status import Heartbeat
    from .watched_source import WatchedSource

    initial = load_settings(settings_path)
    with FolderFiles(initial.outbox_directory, create=False) as files:
        sources = WatchedSource(settings_path, initial_settings=initial)
        require(files.dir.identity == sources.identities[1], "reload_required")
        asyncio.run(Heartbeat(settings_path, sources).serve(files))


def management(command, argv):
    from .contract import encode
    from .settings import ConnectorSettings, load_settings
    from .setup import configure
    from .status import connector_status, select_source

    parser = ManagementArguments(prog=f"ironvellum-local-connector {command}", allow_abbrev=False)
    parser.add_argument("--settings", type=Path, required=True)
    parser.add_argument("--json", action="store_true", required=True)
    if command == "setup":
        for option in ("input-directory", "outbox-directory", "state-directory"):
            parser.add_argument("--" + option, type=Path, required=True)
        parser.add_argument("--client", choices=("config-only", "claude-desktop"), required=True)
        parser.add_argument("--client-config", type=Path)
        parser.add_argument("--replace-existing", action="store_true")
        parser.add_argument("--dataset-id")
        parser.add_argument("--watch", action=argparse.BooleanOptionalAction, default=None)
    if command == "select-source":
        parser.add_argument("--dataset-id", required=True)
    args = parser.parse_args(argv)
    try:
        if command == "setup":
            try:
                previous = load_settings(args.settings)
            except FileNotFoundError:
                previous = None
            dataset = args.dataset_id or (previous.selected_dataset_id if previous else None)
            watch = args.watch if args.watch is not None else bool(previous and previous.watch_enabled)
            value = ConnectorSettings(args.input_directory, args.outbox_directory, args.state_directory, dataset, watch)
            result = configure(args.settings, value, args.client, args.client_config,
                               replace_existing=args.replace_existing)
        elif command == "select-source":
            result = select_source(args.settings, args.dataset_id)
        else:
            result = connector_status(args.settings)
    except (Refusal, OSError) as error:
        code = str(error) if isinstance(error, Refusal) else "storage_unavailable"
        result = dict(schemaVersion=1, status="error", code=code, selectedDatasetId=None, sessions=[],
                      nextActions=["review_existing_entry"] if code == "client_entry_conflict" else ["check_settings"])
    sys.stdout.buffer.write(encode(result) + b"\n")
    return 2 if result["status"] == "error" else 0


def main():
    argv = sys.argv[1:]
    if argv[:1] == ["desktop"]:
        from .desktop import run as run_desktop
        from .desktop_copy import LANGUAGES, WORDINGS

        parser = argparse.ArgumentParser(prog="ironvellum-local-connector desktop", allow_abbrev=False)
        parser.add_argument("--settings", type=Path, required=True)
        parser.add_argument("--language", choices=LANGUAGES)
        parser.add_argument("--wording", choices=WORDINGS, default="plain")
        args = parser.parse_args(argv[1:])
        return run_desktop(args.settings, args.language, args.wording)
    if argv and argv[0] in ('setup', 'status', 'select-source'):
        return management(argv[0], argv[1:])
    folder = argv[:1] == ["serve-folder"]
    if folder:
        parser = argparse.ArgumentParser(prog="ironvellum-local-connector serve-folder",
                                         description="Serve explicitly shared training exports over local stdio.",
                                         allow_abbrev=False)
        parser.add_argument("--settings", type=Path, required=True)
        args = parser.parse_args(argv[1:])
    else:
        parser = Arguments(description="Serve one Ironvellum training export over local stdio.", allow_abbrev=False)
        parser.add_argument("--context", required=True)
        parser.add_argument("--outbox", required=True)
        args = parser.parse_args(argv)
    logging.disable(logging.CRITICAL)
    warnings.simplefilter("ignore")
    try:
        if folder:
            serve_folder(args.settings)
        else:
            with LocalFiles(args.context, args.outbox) as files:
                asyncio.run(run(files))
    except Refusal as error:
        print(str(error), file=sys.stderr)
        return 2
    except (Exception, KeyboardInterrupt):
        print("connector_unavailable", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
