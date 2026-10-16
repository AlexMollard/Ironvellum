"""Check the emulator lock's self-healing: python3 tools/test_device_lock.py

Uses a throwaway lock directory, never the real .tmp/emulator.lock.
"""

import os
import subprocess
import sys
import tempfile
import time
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import device  # noqa: E402


def dead_pid() -> int:
    proc = subprocess.Popen([sys.executable, "-c", "pass"])
    proc.wait()
    return proc.pid


class LockTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        device.LOCK = os.path.join(self.dir, "emulator.lock")
        device.WAIT_POLL = 0.1

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
            self.plant(dead_pid())
        self.assertTrue(os.path.exists(device.LOCK))


if __name__ == "__main__":
    unittest.main()
