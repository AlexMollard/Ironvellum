"""Check the emulator lock's self-healing: python3 tools/test_device_lock.py

Uses a throwaway lock directory, never the real .tmp/emulator.lock.
"""

import os
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from contextlib import redirect_stdout
from io import StringIO
from unittest.mock import patch

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import device  # noqa: E402


def dead_pid() -> int:
    proc = subprocess.Popen([sys.executable, "-c", "pass"])
    proc.wait()
    return proc.pid


class LockTest(unittest.TestCase):
    def setUp(self):
        directory = self.enterContext(tempfile.TemporaryDirectory())
        self.enterContext(patch.object(device, "LOCK", os.path.join(directory, "emulator.lock")))
        self.enterContext(patch.object(device, "WAIT_POLL", 0.1))

    def plant(self, pid, age=0):
        os.mkdir(device.LOCK)
        owner = os.path.join(device.LOCK, device.OWNER)
        with open(owner, "w") as handle:
            handle.write('{"pid": %d, "host": "%s", "label": "planted"}' % (pid, device.socket.gethostname()))
        os.utime(owner, (time.time() - age, time.time() - age))

    def test_dead_owner_is_taken_over(self):
        self.plant(dead_pid())
        with device.emulator_lock("test"):
            self.assertEqual(device.lock_owner(device.LOCK)["pid"], os.getpid())
        self.assertFalse(os.path.exists(device.LOCK))

    def test_live_owner_without_heartbeat_is_taken_over(self):
        self.plant(os.getpid(), age=device.HEARTBEAT_STALE + 60)
        self.assertIn("heartbeat", device.lock_stale(device.LOCK))

    def test_live_owner_with_heartbeat_holds(self):
        self.plant(os.getpid())
        self.assertIsNone(device.lock_stale(device.LOCK))

    def test_bare_mkdir_lock_goes_stale_by_age(self):
        os.mkdir(device.LOCK)
        self.assertIsNone(device.lock_stale(device.LOCK))
        old = time.time() - device.BARE_STALE - 60
        os.utime(device.LOCK, (old, old))
        self.assertIn("no owner", device.lock_stale(device.LOCK))

    def test_released_on_exception(self):
        with self.assertRaises(RuntimeError):
            with device.emulator_lock("test"):
                raise RuntimeError
        self.assertFalse(os.path.exists(device.LOCK))

    def test_release_leaves_a_successors_lock(self):
        with device.emulator_lock("test"):
            os.rename(device.LOCK, device.LOCK + ".moved")
            self.plant(os.getpid())
        self.assertTrue(os.path.exists(device.LOCK))

    def test_two_workers_use_one_lease_and_timeout_keeps_the_holder(self):
        logs = []
        ready, done = threading.Event(), threading.Event()

        def hold():
            with device.emulator_lock("first worker"):
                ready.set()
                done.wait(5)

        worker = threading.Thread(target=hold)
        worker.start()
        self.assertTrue(ready.wait(5))
        try:
            stdout = StringIO()
            started = time.monotonic()
            with redirect_stdout(stdout), self.assertRaises(TimeoutError):
                with device.emulator_lock("second worker", timeout_s=0.15, log=logs.append):
                    self.fail("Second worker acquired the live lock")
            self.assertLess(time.monotonic() - started, 2)
            self.assertEqual(stdout.getvalue(), "")
            self.assertIn("first worker", logs[0])
            self.assertEqual(device.lock_owner(device.LOCK)["label"], "first worker")
        finally:
            done.set()
            worker.join(5)
        self.assertFalse(os.path.exists(device.LOCK))

    def test_windows_probe_failure_retains_holder_with_fresh_heartbeat(self):
        self.plant(os.getpid())
        for result in (
            subprocess.TimeoutExpired("tasklist", 1),
            subprocess.CompletedProcess([], 1, "", "probe failed"),
            subprocess.CompletedProcess([], 0, "unrecognized output", ""),
        ):
            with self.subTest(result=result), patch.object(device.os, "name", "nt"), patch.object(
                device.subprocess, "run", side_effect=[result] if isinstance(result, Exception) else None,
                return_value=result,
            ) as run:
                self.assertIsNone(device.lock_stale(device.LOCK))
                self.assertGreater(run.call_args.kwargs["timeout"], 0)

    def test_posix_probe_permission_denial_is_not_process_absence(self):
        with patch.object(device.os, "name", "posix"), patch.object(device.os, "kill", side_effect=PermissionError):
            self.assertTrue(device.pid_alive(123))
        with patch.object(device.os, "name", "posix"), patch.object(device.os, "kill", side_effect=ProcessLookupError):
            self.assertFalse(device.pid_alive(123))

    def test_stale_takeover_logs_only_to_given_callback(self):
        self.plant(dead_pid())
        logs, stdout = [], StringIO()
        with redirect_stdout(stdout), device.emulator_lock("replacement", timeout_s=5, log=logs.append):
            self.assertEqual(device.lock_owner(device.LOCK)["label"], "replacement")
        self.assertEqual(stdout.getvalue(), "")
        self.assertIn("stale", logs[0])


class LockedEnvTest(unittest.TestCase):
    """A locked run never reaches a phone: Gradle's connected tasks use every attached device otherwise."""

    def test_unpinned_run_is_pinned_to_the_running_emulator(self):
        self.assertEqual(device.locked_env({}, ["emulator-5556"])["ANDROID_SERIAL"], "emulator-5556")

    def test_unpinned_run_with_no_emulator_still_never_picks_the_phone(self):
        self.assertEqual(device.locked_env({}, [])["ANDROID_SERIAL"], "emulator-5554")

    def test_an_emulator_pin_is_kept(self):
        self.assertEqual(device.locked_env({"ANDROID_SERIAL": "emulator-5554"}, ["emulator-5556"])["ANDROID_SERIAL"], "emulator-5554")

    def test_a_phone_pin_is_refused(self):
        with self.assertRaises(SystemExit):
            device.locked_env({"ANDROID_SERIAL": "R5GL14GXV3J"}, ["emulator-5554"])


if __name__ == "__main__":
    unittest.main()
