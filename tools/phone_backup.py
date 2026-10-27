"""Back up a debuggable Ironvellum install without changing the device.

Example: python tools/phone_backup.py --serial SERIAL --output .tmp/backup-new
The output directory must not exist. Preferences can contain secrets; keep the
backup private. Repeated reads detect changes, but cannot prove an atomic
snapshot if writes occur and revert between reads. Stop using the app while
capturing the backup for the best chance of a consistent result.
"""

import argparse
import os
import shlex
import sqlite3
import subprocess
import sys
import tempfile
from pathlib import Path, PurePosixPath

PACKAGE = "com.ironvellum.app"
DB = "databases/ironvellum.db"
DB_FILES = (DB, DB + "-wal", DB + "-shm")


class BackupError(Exception):
    pass


def default_adb():
    """Use the project's Windows SDK location when available, otherwise PATH."""
    local_app_data = os.environ.get("LOCALAPPDATA")
    if local_app_data:
        sdk_adb = Path(local_app_data) / "Android" / "Sdk" / "platform-tools" / "adb.exe"
        if sdk_adb.is_file():
            return str(sdk_adb)
    return "adb"


def remote(serial, script, adb):
    """Run a fixed shell script as the app and return its unmodified bytes."""
    remote_command = f"run-as {PACKAGE} sh -c {shlex.quote(script)}"
    command = [adb, "-s", serial, "exec-out", remote_command]
    try:
        result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    except OSError as exc:
        raise BackupError(f"Cannot start adb: {exc}") from exc
    if result.returncode:
        detail = result.stderr.decode("utf-8", "replace").strip()
        raise BackupError(f"Device read failed: {detail or 'adb returned an error'}")
    return result.stdout


def read_file(serial, path, adb):
    name = shlex.quote(path)
    # The marker distinguishes an absent optional file from an empty file.
    data = remote(serial, f"if [ -e {name} ]; then printf '1'; cat {name} || exit 1; else printf '0'; fi", adb)
    if not data or data[:1] not in (b"0", b"1") or (data[:1] == b"0" and len(data) != 1):
        raise BackupError(f"Invalid device response for {path}")
    return data[1:] if data[:1] == b"1" else None


def pref_paths(serial, adb):
    data = remote(serial, "test -d shared_prefs && test -r shared_prefs && "
                  "find shared_prefs -type f -print0", adb)
    if not data or not data.endswith(b"\0"):
        raise BackupError("Shared preferences are missing, unreadable, or empty")
    try:
        paths = [p.decode("utf-8") for p in data[:-1].split(b"\0")]
    except UnicodeDecodeError as exc:
        raise BackupError("A preference filename is not UTF-8") from exc
    for path in paths:
        parts = PurePosixPath(path).parts
        if (len(parts) < 2 or parts[0] != "shared_prefs" or
                any(part in ("", ".", "..") or "\\" in part or ":" in part for part in parts) or
                str(PurePosixPath(path)) != path):
            raise BackupError("Unsafe preference path from device")
    if len(set(paths)) != len(paths):
        raise BackupError("Duplicate preference paths from device")
    return sorted(paths)


def snapshot(serial, adb):
    files = {path: read_file(serial, path, adb) for path in DB_FILES}
    if not files[DB]:
        raise BackupError("Required database is missing or empty")
    for path in pref_paths(serial, adb):
        data = read_file(serial, path, adb)
        if not data:
            raise BackupError(f"Required preference file is missing or empty: {path}")
        files[path] = data
    return files


def check_database(files):
    # SQLite may recover/checkpoint a WAL when opening; work on disposable copies.
    with tempfile.TemporaryDirectory(prefix="ironvellum-check-") as scratch:
        root = Path(scratch)
        for path in DB_FILES:
            if files[path] is not None:
                (root / Path(path).name).write_bytes(files[path])
        try:
            connection = sqlite3.connect(str(root / "ironvellum.db"))
            try:
                result = connection.execute("PRAGMA integrity_check").fetchall()
            finally:
                connection.close()
        except sqlite3.Error as exc:
            raise BackupError(f"Database integrity check failed: {exc}") from exc
        if result != [("ok",)]:
            raise BackupError(f"Database integrity check failed: {result}")

def backup(serial, output, adb="adb"):
    output = Path(output)
    if output.exists() or output.is_symlink():
        raise BackupError(f"Output already exists: {output}")
    first = snapshot(serial, adb)
    second = snapshot(serial, adb)
    # Require identical presence and bytes, including the transient SHM.
    compared = set(first) | set(second)
    if any(first.get(path) != second.get(path) for path in compared):
        raise BackupError("Device data changed during capture; no backup was created")
    check_database(second)
    # mkdir is the exclusive claim; never write into an existing directory.
    output.mkdir()
    for path, data in second.items():
        if data is not None:
            target = output.joinpath(*PurePosixPath(path).parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            with target.open("xb") as handle:
                handle.write(data)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="explicit adb device serial")
    parser.add_argument("--output", required=True, help="new local backup directory")
    parser.add_argument("--adb", default=default_adb(), help="adb executable (defaults to the local Android SDK or PATH)")
    args = parser.parse_args(argv)
    if not args.serial.strip():
        parser.error("--serial must not be empty")
    print("Warning: raw shared preferences can contain secrets; keep the backup private.", file=sys.stderr)
    try:
        backup(args.serial, args.output, args.adb)
    except (BackupError, OSError) as exc:
        print(f"Backup failed: {exc}", file=sys.stderr)
        return 1
    print(f"Backup saved to {args.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
