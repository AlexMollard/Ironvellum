"""Checked, narrowly scoped commands for the Ironvellum development tools."""

from collections.abc import Callable, Iterator
from contextlib import contextmanager, nullcontext
from dataclasses import dataclass
from functools import lru_cache
import importlib.util
import os
from pathlib import Path
from queue import Empty, Queue
import re
import shutil
import subprocess
import sys
from threading import Thread
import time
from uuid import UUID


PACKAGE = "com.ironvellum.app"
SERIAL_PATTERN = re.compile(r"[a-zA-Z0-9][a-zA-Z0-9._:-]{0,127}")
SCENARIO_IDS = {
    "all": (0, 1, 2, 3, 4, 5), "comment": (0,), "reaction": (1,),
    "group_goal": (2,), "batch": (3,), "rest": (4,), "accepted": (5,),
}
PREVIEW_ACTIONS = {"show": "SHOW_NOTIFICATIONS", "clear": "CLEAR_NOTIFICATIONS"}
_COMMAND_TIMEOUT = 8.0
_TEXT_LIMIT = 512 * 1024
_STDERR_LIMIT = 16 * 1024
_FOREGROUND_RECORD = re.compile(
    r"ActivityRecord\{[a-fA-F0-9]+ u([0-9]+) ([\w.]+)/(\.?[\w.$]+)(?:\s|})",
    re.ASCII,
)
_DISPLAY_HEADER = re.compile(r"^Display #([0-9]+) \(activities from top to bottom\):")
_RESUMED_LINE = re.compile(r"^\s*(topResumedActivity|Resumed|mResumedActivity)\s*[:=]\s*(.*)$")


class DevelopmentError(Exception):
    """A client-safe failure with a stable code and optional structured details."""

    def __init__(self, code: str, message: str, *, details: dict | None = None):
        super().__init__(message)
        self.code = code
        self.details = {} if details is None else details


def _output_limit() -> DevelopmentError:
    return DevelopmentError("adb_output_limit", "ADB returned more data than this operation allows")


def _run_bounded(
    argv: list[str], *, timeout: float, max_stdout: int, max_stderr: int,
) -> subprocess.CompletedProcess[bytes]:
    # Drain both streams concurrently. Each unbuffered pipe may contribute at
    # most its limit plus one sentinel byte, even if ADB never exits on its own.
    deadline = time.monotonic() + timeout
    results: Queue[tuple[str, bytes | Exception]] = Queue()
    process = subprocess.Popen(
        argv, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
        stderr=subprocess.PIPE, shell=False, bufsize=0,
    )
    assert process.stdout is not None and process.stderr is not None

    def read(name, pipe, limit):
        with pipe:
            data = bytearray()
            try:
                while chunk := pipe.read(min(64 * 1024, limit + 1 - len(data))):
                    data.extend(chunk)
                    if len(data) > limit:
                        raise _output_limit()
                results.put((name, bytes(data)))
            except (OSError, DevelopmentError) as error:
                results.put((name, error))

    try:
        for name, pipe, limit in (
            ("stdout", process.stdout, max_stdout), ("stderr", process.stderr, max_stderr),
        ):
            Thread(target=read, args=(name, pipe, limit), daemon=True).start()
        captured = {}
        for _ in range(2):
            try:
                name, result = results.get(timeout=max(0, deadline - time.monotonic()))
            except Empty:
                raise subprocess.TimeoutExpired(argv[0], timeout) from None
            if isinstance(result, Exception):
                raise result
            captured[name] = result
        code = process.wait(timeout=max(0, deadline - time.monotonic()))
        return subprocess.CompletedProcess(argv, code, captured["stdout"], captured["stderr"])
    finally:
        if process.poll() is None:
            process.kill()
            process.wait()


def _binary(path: Path) -> str | None:
    # A .bat or .cmd can invoke cmd.exe on Windows even without shell=True.
    if os.name == "nt" and path.suffix.lower() != ".exe":
        return None
    if path.is_file() and os.access(path, os.X_OK):
        return str(path.resolve())
    return None


def _resolve_adb(repo_root: Path, override: str | None) -> str:
    configured = override if override is not None else os.environ.get("IRONVELLUM_ADB")
    if configured is not None:
        executable = _binary(Path(configured)) if configured else None
        if executable is None:
            raise DevelopmentError("adb_configuration", "The explicit ADB executable is invalid")
        return executable

    name = "adb.exe" if os.name == "nt" else "adb"
    roots = [os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")]
    try:
        properties = (repo_root / "local.properties").read_text(encoding="utf-8-sig")
    except (OSError, UnicodeError):
        properties = ""
    if match := re.search(r"^\s*sdk\.dir\s*[:=]\s*(.+)$", properties, re.MULTILINE):
        # Gradle writes Java property escapes around Windows drive colons and
        # backslashes. Restrict decoding to property metacharacters.
        roots.append(re.sub(r"\\([\\:= #!])", r"\1", match.group(1).strip()))
    for root in roots:
        if root and (executable := _binary(Path(root) / "platform-tools" / name)):
            return executable
    path = shutil.which(name)
    if path and (executable := _binary(Path(path))):
        return executable
    raise DevelopmentError("adb_configuration", "ADB was not found in the Android SDK or PATH")


@lru_cache
def _lock_module(repo_root: Path):
    # The installed host package is independent of the repository's Python
    # import path. Load its existing lock helper from the configured checkout.
    spec = importlib.util.spec_from_file_location(
        "_ironvellum_device_lock", repo_root / "tools" / "device.py",
    )
    if spec is None or spec.loader is None:
        raise OSError("Shared lock helper missing")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def _stderr(message: str) -> None:
    print(message, file=sys.stderr, flush=True)


class AdbClient:
    def __init__(
        self, repo_root: Path, executable: str | None = None, *,
        runner: Callable[..., subprocess.CompletedProcess[bytes]] | None = None,
    ):
        self._repo_root = Path(repo_root).resolve()
        self._override = executable
        self._executable: str | None = None
        self._runner = runner if runner is not None else _run_bounded

    def _run(
        self, *args: str, timeout: float = _COMMAND_TIMEOUT, max_stdout: int = _TEXT_LIMIT,
    ) -> bytes:
        if self._executable is None:
            self._executable = _resolve_adb(self._repo_root, self._override)
        try:
            result = self._runner(
                [self._executable, *args], timeout=timeout,
                max_stdout=max_stdout, max_stderr=_STDERR_LIMIT,
            )
        except subprocess.TimeoutExpired:
            raise DevelopmentError("adb_timeout", "An ADB command timed out") from None
        except OSError:
            raise DevelopmentError("adb_failed", "The ADB executable could not be run") from None
        if len(result.stdout) > max_stdout or len(result.stderr) > _STDERR_LIMIT:
            raise _output_limit()
        if result.returncode:
            raise DevelopmentError("adb_failed", "An ADB command failed")
        return result.stdout

    def list_devices(self) -> list[dict[str, str]]:
        output = self._run("devices", "-l").decode("utf-8", "replace")
        lines = output.splitlines()
        try:
            start = lines.index("List of devices attached") + 1
        except ValueError:
            raise DevelopmentError("adb_failed", "ADB returned an unrecognized device list") from None
        devices = []
        for line in lines[start:]:
            fields = line.split(maxsplit=1)
            if len(fields) != 2 or not SERIAL_PATTERN.fullmatch(fields[0]):
                continue
            serial, attributes = fields
            if attributes == "no permissions" or attributes.startswith("no permissions "):
                devices.append({"serial": serial, "state": "no permissions"})
                continue
            state, *values = attributes.split()
            row = {"serial": serial, "state": state}
            for value in values:
                key, _, text = value.partition(":")
                if key in {"product", "model", "device", "transport_id"} and text:
                    row[key] = text
            devices.append(row)
        return devices

    @contextmanager
    def device(self, serial: str) -> Iterator["_Device"]:
        """Check the selected device, then pin its user for a whole operation."""
        if not isinstance(serial, str) or not SERIAL_PATTERN.fullmatch(serial):
            raise DevelopmentError("invalid_serial", "Supply a valid, explicit ADB serial")
        matches = [row for row in self.list_devices() if row["serial"] == serial]
        if not matches:
            raise DevelopmentError("device_not_found", "The selected ADB serial is absent")
        if len(matches) != 1:
            raise DevelopmentError("device_ambiguous", "ADB reported the selected serial more than once")
        state = matches[0]["state"]
        if state != "device":
            code = {"offline": "device_offline", "unauthorized": "device_unauthorized"}.get(
                state, "device_unavailable",
            )
            reason = state if state in {"offline", "unauthorized", "no permissions"} else "unavailable"
            raise DevelopmentError(code, f"The selected ADB device is {reason}")

        lease = nullcontext()
        if serial.startswith("emulator-"):
            try:
                lock = _lock_module(self._repo_root)
            except OSError:
                raise DevelopmentError("emulator_lock_unavailable", "The shared emulator lock is unavailable") from None
            lease = lock.emulator_lock("Ironvellum development MCP", timeout_s=30, log=_stderr)
        try:
            with lease:
                raw = self._run("-s", serial, "shell", "am", "get-current-user").strip()
                if not re.fullmatch(rb"[0-9]{1,5}", raw):
                    raise DevelopmentError("android_user_unavailable", "ADB could not identify the current Android user")
                yield _Device(self, serial, int(raw))
        except TimeoutError:
            raise DevelopmentError("emulator_lock_timeout", "The shared emulator lock is busy") from None
        except OSError:
            raise DevelopmentError("emulator_lock_unavailable", "The shared emulator lock is unavailable") from None


@dataclass(frozen=True)
class _InstalledPackage:
    uid: int
    version_code: int


class _Device:
    """Commands bound to one validated serial and one Android user."""

    def __init__(self, adb: AdbClient, serial: str, user: int):
        self._adb = adb
        self.serial = serial
        self.user = user

    def _run(self, *args: str, **bounds) -> bytes:
        return self._adb._run("-s", self.serial, *args, **bounds)

    def _text(self, *args: str, **bounds) -> str:
        return self._run(*args, **bounds).decode("utf-8", "replace")

    def installed_package(self) -> _InstalledPackage | None:
        output = self._text(
            "shell", "cmd", "package", "list", "packages", "--user",
            str(self.user), "-U", "--show-versioncode", PACKAGE,
        )
        matches = []
        for line in output.splitlines():
            parts = line.split()
            if not parts:
                continue
            name, *values = parts
            if not name.startswith("package:"):
                raise DevelopmentError("package_unavailable", "The package list could not be interpreted")
            if name == f"package:{PACKAGE}":
                matches.append(dict(value.split(":", 1) for value in values if ":" in value))
        if not matches:
            return None
        fields = matches[0]
        uid, version = fields.get("uid", ""), fields.get("versionCode", "")
        if (
            len(matches) != 1
            or not re.fullmatch(r"[0-9]{1,10}", uid)
            or not re.fullmatch(r"[0-9]{1,19}", version)
            or int(uid) // 100000 != self.user
        ):
            raise DevelopmentError("package_unavailable", "Ironvellum's package metadata could not be verified")
        return _InstalledPackage(int(uid), int(version))

    def package_metadata(self, installed: _InstalledPackage) -> dict:
        output = self._text("shell", "dumpsys", "package", PACKAGE).replace("\r\n", "\n")
        versions = re.findall(r"^\s*versionName=([^\r\n]*)$", output, re.MULTILINE)
        flags = re.findall(r"^\s*pkgFlags=\[([^\]\r\n]*)\]", output, re.MULTILINE)
        return {
            "package": PACKAGE, "versionCode": installed.version_code,
            "versionName": versions[0] if len(versions) == 1 and versions[0] != "null" else None,
            "debuggable": "DEBUGGABLE" in flags[0].split() if len(flags) == 1 else None,
        }

    def process_id(self, installed: _InstalledPackage) -> int | None:
        output = self._text("shell", "ps", "-A", "-o", "UID,PID,NAME")
        lines = output.splitlines()
        if not lines or lines[0].split() != ["UID", "PID", "NAME"]:
            raise DevelopmentError("process_unavailable", "ADB returned an unrecognized process list")
        pids = []
        for line in lines[1:]:
            fields = line.split(maxsplit=2)
            if len(fields) != 3 or fields[2] != PACKAGE or fields[0] != str(installed.uid):
                continue
            if not re.fullmatch(r"[1-9][0-9]{0,9}", fields[1]):
                raise DevelopmentError("process_unavailable", "Ironvellum's process ID could not be verified")
            pids.append(int(fields[1]))
        if len(pids) > 1:
            raise DevelopmentError("process_unavailable", "Ironvellum's process ID is ambiguous")
        return pids[0] if pids else None

    def foreground_activity(self) -> str | None:
        output = self._text("shell", "dumpsys", "activity", "activities")
        records_by_field = {}
        display = None
        for line in output.splitlines():
            if header := _DISPLAY_HEADER.match(line):
                display = header[1]
            elif resumed := _RESUMED_LINE.match(line):
                records_by_field.setdefault(resumed[1], []).append((display, resumed[2]))
        for field in ("topResumedActivity", "Resumed", "mResumedActivity"):
            records = records_by_field.get(field, [])
            if records:
                # Android 15 uses "Resumed:" summaries under display headings.
                # Only the default display is eligible for a display capture.
                display, record = records[0]
                if len(records) != 1 or display not in {None, "0"}:
                    return None
                match = _FOREGROUND_RECORD.match(record)
                if match and (match[1], match[2]) == (str(self.user), PACKAGE):
                    return match[3]
                return None
        return None

    def read_status(self, request: UUID) -> bytes:
        return self._run(
            "shell", "am", "broadcast", "--user", str(self.user),
            "--receiver-registered-only", "-p", PACKAGE, "-a", f"{PACKAGE}.debug.READ_STATUS",
            "--es", "request_id", request.hex, timeout=12, max_stdout=64 * 1024,
        )

    def screenshot(self) -> bytes:
        return self._run("exec-out", "screencap", "-p", timeout=12, max_stdout=16 * 1024 * 1024)

    @contextmanager
    def ui_dump(self, request: UUID) -> Iterator[bytes]:
        path = f"/data/local/tmp/ironvellum-mcp-{request.hex}.xml"
        try:
            output = self._text("shell", "uiautomator", "dump", path, timeout=15)
            if output.strip() not in {f"UI hierchary dumped to: {path}", f"UI hierarchy dumped to: {path}"}:
                raise DevelopmentError("ui_dump_failed", "Android did not finish writing a fresh UI hierarchy")
            yield self._run("exec-out", "cat", path, max_stdout=2 * 1024 * 1024)
        finally:
            failed = sys.exc_info()[0] is not None
            try:
                self._run("shell", "rm", "-f", path, timeout=3)
            except DevelopmentError:
                if not failed:
                    raise

    def preview(self, action: str, scenario: str, request: UUID) -> bytes:
        if action not in PREVIEW_ACTIONS or scenario not in SCENARIO_IDS or (action == "clear" and scenario != "all"):
            raise DevelopmentError("invalid_preview", "Select a supported preview action and scenario")
        return self._run(
            "shell", "am", "broadcast", "--user", str(self.user),
            "-n", f"{PACKAGE}/.debug.NotificationPreviewReceiver",
            "-a", f"{PACKAGE}.debug.{PREVIEW_ACTIONS[action]}",
            "--es", "scenario", scenario, "--es", "request_id", request.hex,
            timeout=12, max_stdout=64 * 1024,
        )

    def preview_ids(self, *, timeout: float) -> set[int]:
        output = self._text("shell", "cmd", "notification", "list", timeout=timeout)
        ids = set()
        for line in output.splitlines():
            fields = line.strip().split("|", maxsplit=4)
            if fields == [""]:
                continue
            if len(fields) != 5 or not re.fullmatch(r"-?[0-9]+", fields[0]):
                raise DevelopmentError("notification_observation_unavailable", "Android did not return notification keys")
            user, package, id_text, tag, _ = fields
            if (user, package, tag) == (str(self.user), PACKAGE, "notification-preview") and re.fullmatch(r"[0-5]", id_text):
                ids.add(int(id_text))
        return ids

    def logs(self, installed: _InstalledPackage, pid: int, limit: int) -> str:
        if type(pid) is not int or pid <= 0 or type(limit) is not int or not 1 <= limit <= 200:
            raise DevelopmentError("invalid_diagnostics", "Diagnostics require a verified process and a bounded line limit")
        return self._text(
            "logcat", f"--pid={pid}", f"--uid={installed.uid}",
            "-d", "-t", str(limit), "-v", "brief", max_stdout=256 * 1024,
        )
