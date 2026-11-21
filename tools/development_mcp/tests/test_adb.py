from pathlib import Path
from contextlib import redirect_stderr
from io import StringIO
import os
import shutil
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

from ironvellum_mcp.adb import AdbClient, DevelopmentError, _lock_module, _run_bounded


ROOT = Path(__file__).resolve().parents[3]
SERIAL = "127.0.0.1:5556"
DEVICES = (
    b"* daemon started successfully\nList of devices attached\n"
    b"emulator-5554   device product:sdk model:Emulator transport_id:1\n"
    b"phone.one\tunauthorized transport_id:2\n"
    b"phone_two\toffline transport_id:3\n"
    b"phone-3\tno permissions (missing udev rules)\n"
    b"127.0.0.1:5556\tdevice model:Test_device\n"
)


class FakeRunner:
    """Require exact command order and reject unexpected calls."""

    def __init__(self, case):
        self.case = case
        self.pending = []
        self.calls = []

    def expect(self, args, stdout=b"", *, stderr=b"", code=0, error=None):
        self.pending.append((tuple(args), stdout, stderr, code, error))

    def __call__(self, argv, *, timeout, max_stdout, max_stderr):
        self.case.assertIsInstance(argv, list)
        self.case.assertEqual(argv[0], str(Path(sys.executable).resolve()))
        self.case.assertGreater(timeout, 0)
        self.case.assertTrue(self.pending, f"Unexpected command: {argv!r}")
        args, stdout, stderr, code, error = self.pending.pop(0)
        self.case.assertEqual(tuple(argv[1:]), args)
        self.calls.append((args, timeout, max_stdout, max_stderr))
        if error is not None:
            raise error
        return subprocess.CompletedProcess(argv, code, stdout, stderr)


class AdbTest(unittest.TestCase):
    def setUp(self):
        self.runner = FakeRunner(self)
        self.adb = AdbClient(ROOT, sys.executable, runner=self.runner)

    def test_list_reports_actual_states_including_unavailable_devices(self):
        self.runner.expect(("devices", "-l"), DEVICES)
        rows = self.adb.list_devices()
        self.assertEqual([(row["serial"], row["state"]) for row in rows], [
            ("emulator-5554", "device"), ("phone.one", "unauthorized"),
            ("phone_two", "offline"), ("phone-3", "no permissions"),
            (SERIAL, "device"),
        ])
        self.assertEqual(rows[0]["model"], "Emulator")

    def test_invalid_serials_never_run_a_command(self):
        for serial in ("", None, "-d", ".phone", "é", "phone\n", "a&b", "a b", "a" * 129):
            with self.subTest(serial=serial), self.assertRaises(DevelopmentError) as error:
                with self.adb.device(serial):
                    self.fail("Expected invalid serial")
            self.assertEqual(error.exception.code, "invalid_serial")

    def test_no_fallback_from_unavailable_or_unknown_serial(self):
        for serial, expected in (
            ("phone.one", "device_unauthorized"), ("phone_two", "device_offline"),
            ("phone-3", "device_unavailable"), ("missing", "device_not_found"),
        ):
            with self.subTest(serial=serial):
                self.runner.expect(("devices", "-l"), DEVICES)
                with self.assertRaises(DevelopmentError) as error:
                    with self.adb.device(serial):
                        self.fail("Expected unavailable device")
                self.assertEqual(error.exception.code, expected)
        self.assertFalse(self.runner.pending)

    def test_duplicate_serial_is_ambiguous(self):
        self.runner.expect(("devices", "-l"), DEVICES + f"{SERIAL}\toffline\n".encode())
        with self.assertRaises(DevelopmentError) as error:
            with self.adb.device(SERIAL):
                self.fail("Expected ambiguity")
        self.assertEqual(error.exception.code, "device_ambiguous")

    def test_busy_shared_lock_prevents_device_commands_for_tcp_serial(self):
        lock = _lock_module(ROOT)
        real_lease = lock.emulator_lock
        self.runner.expect(("devices", "-l"), DEVICES)

        def short_lease(label, *, timeout_s, log):
            self.assertEqual(timeout_s, 30)
            return real_lease(label, timeout_s=0.02, log=log)

        with tempfile.TemporaryDirectory() as directory, patch.object(
            lock, "LOCK", str(Path(directory) / "emulator.lock")
        ), real_lease("local gate"), patch.object(lock, "emulator_lock", short_lease):
            with redirect_stderr(StringIO()), self.assertRaises(DevelopmentError) as error:
                with self.adb.device(SERIAL):
                    self.fail("A TCP serial entered the busy emulator lease")
            self.assertEqual(lock.lock_owner(lock.LOCK)["label"], "local gate")
        self.assertEqual(error.exception.code, "emulator_lock_timeout")

    def test_linked_checkout_resolves_the_same_lock_as_its_main_checkout(self):
        with tempfile.TemporaryDirectory() as directory:
            main = Path(directory) / "main"
            linked = Path(directory) / "linked checkout"
            subprocess.run(["git", "init", "--quiet", str(main)], check=True)
            subprocess.run(["git", "-C", str(main), "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
                            "-c", "commit.gpgsign=false", "commit", "--quiet", "--allow-empty", "-m", "Fixture"],
                           check=True, capture_output=True)
            subprocess.run(["git", "-C", str(main), "worktree", "add", "--quiet", "--detach", str(linked)],
                           check=True, capture_output=True)
            for checkout in (main, linked):
                (checkout / "tools").mkdir()
                shutil.copyfile(ROOT / "tools/device.py", checkout / "tools/device.py")
                self.assertEqual(Path(_lock_module(checkout).LOCK), main / ".tmp/emulator.lock")

    def test_unresolved_linked_checkout_fails_before_selected_device_commands(self):
        for failure in ("rejected", "stalled"):
            with self.subTest(failure=failure), tempfile.TemporaryDirectory() as directory:
                root = Path(directory) / "linked"
                (root / "tools").mkdir(parents=True)
                (root / ".git").write_text("gitdir: ../main/.git/worktrees/linked\n", encoding="utf-8")
                shutil.copyfile(ROOT / "tools/device.py", root / "tools/device.py")

                def git_lookup(*args, **kwargs):
                    if failure == "stalled":
                        raise subprocess.TimeoutExpired("private command", 1)
                    return subprocess.CompletedProcess(args, 128, "", "private error")

                client = AdbClient(root, sys.executable, runner=self.runner)
                self.runner.expect(("devices", "-l"), DEVICES)
                with patch("subprocess.run", side_effect=git_lookup) as lookup:
                    with self.assertRaises(DevelopmentError) as error:
                        with client.device(SERIAL):
                            self.fail("Unresolved checkout acquired a private lock")
                    self.assertLessEqual(lookup.call_args.kwargs["timeout"], 5)
                self.assertEqual(error.exception.code, "emulator_lock_unavailable")
                self.assertNotIn("private", str(error.exception))
                self.assertFalse((root / ".tmp").exists())

    def test_adb_timeout_and_nonzero_exit_are_safe(self):
        for code, kwargs in (
            ("adb_timeout", {"error": subprocess.TimeoutExpired("secret command", 1)}),
            ("adb_failed", {"code": 1, "stderr": b"private bearer token"}),
            ("adb_output_limit", {"stdout": b"x" * (2 * 1024 * 1024)}),
        ):
            with self.subTest(code=code):
                self.runner.expect(("devices", "-l"), **kwargs)
                with self.assertRaises(DevelopmentError) as error:
                    self.adb.list_devices()
                self.assertEqual(error.exception.code, code)
                self.assertNotIn("secret", str(error.exception))
                self.assertNotIn("private", str(error.exception))

    def test_explicit_invalid_configuration_does_not_fall_back_to_path(self):
        with tempfile.TemporaryDirectory() as directory:
            missing = str(Path(directory) / "not adb")
            with patch.object(os, "environ", {**os.environ, "IRONVELLUM_ADB": missing}), patch(
                "ironvellum_mcp.adb.shutil.which", return_value=sys.executable
            ):
                for executable in (None, missing, ""):
                    with self.subTest(executable=executable), self.assertRaises(DevelopmentError) as error:
                        AdbClient(ROOT, executable, runner=self.runner).list_devices()
                    self.assertEqual(error.exception.code, "adb_configuration")

    def test_gradle_sdk_properties_allow_spaces_and_literal_backslashes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sdk = root / "SDK space"
            executable = sdk / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
            executable.parent.mkdir(parents=True)
            executable.touch(mode=0o755)
            escaped = str(sdk).replace("\\", "\\\\").replace(":", "\\:")
            (root / "local.properties").write_text(f"sdk.dir={escaped}\n", encoding="utf-8")
            result = subprocess.CompletedProcess([], 0, DEVICES, b"")
            with patch.object(os, "environ", {}), patch(
                "ironvellum_mcp.adb._run_bounded", return_value=result
            ) as run:
                AdbClient(root).list_devices()
                self.assertEqual(run.call_args.args[0], [str(executable), "devices", "-l"])


class BoundedReaderTest(unittest.TestCase):
    def python(self, script, timeout=3):
        return _run_bounded(
            [sys.executable, "-c", script], timeout=timeout, max_stdout=1024, max_stderr=128,
        )

    def test_drains_both_binary_streams_at_their_limits(self):
        run = self.python("import os; os.write(1, b'x'*1024); os.write(2, b'y'*128)")
        self.assertEqual((run.returncode, run.stdout, run.stderr), (0, b"x" * 1024, b"y" * 128))

    def test_aborts_a_child_on_either_output_limit(self):
        for fd in (1, 2):
            with self.subTest(fd=fd):
                started = time.monotonic()
                with self.assertRaises(DevelopmentError) as error:
                    self.python(f"import os, time; os.write({fd}, b'x'*65536); time.sleep(30)")
                self.assertEqual(error.exception.code, "adb_output_limit")
                self.assertLess(time.monotonic() - started, 3)

    def test_aborts_a_child_that_does_not_exit_before_deadline(self):
        started = time.monotonic()
        with self.assertRaises(subprocess.TimeoutExpired):
            self.python("import time; time.sleep(30)", timeout=0.15)
        self.assertLess(time.monotonic() - started, 3)
