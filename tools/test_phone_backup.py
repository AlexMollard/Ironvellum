"""Run with: python -m unittest discover -s tools -p test_phone_backup.py

All adb calls are faked; these tests never contact a device.
"""

import shlex
import sqlite3
import subprocess
import tempfile
import unittest
from contextlib import closing
from pathlib import Path
from unittest.mock import patch

import phone_backup as backup


class FakeAdb:
    def __init__(self, files):
        self.files = files
        self.calls = []
        self.read_counts = {}
        self.fail = False

    def __call__(self, command, **kwargs):
        self.calls.append(command)
        if self.fail:
            return subprocess.CompletedProcess(command, 1, b"", b"run-as failed")
        remote_args = shlex.split(command[-1])
        if remote_args[:4] != ["run-as", backup.PACKAGE, "sh", "-c"] or len(remote_args) != 5:
            raise AssertionError(command)
        script = remote_args[4]
        if script.startswith("test -d shared_prefs"):
            names = sorted(p for p in self.files if p.startswith("shared_prefs/"))
            data = b"".join(name.encode() + b"\0" for name in names)
        else:
            paths = list(backup.DB_FILES) + [p for p in self.files if p.startswith("shared_prefs/")]
            path = next((p for p in paths if script.startswith("if [ -e " + shlex.quote(p) + " ];")), None)
            if path is None:
                raise AssertionError(script)
            self.read_counts[path] = self.read_counts.get(path, 0) + 1
            value = self.files.get(path)
            if callable(value):
                value = value(self.read_counts[path])
            data = b"0" if value is None else b"1" + value
        return subprocess.CompletedProcess(command, 0, data, b"")


class PhoneBackupTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        source = self.root / "source.db"
        with closing(sqlite3.connect(str(source))) as connection:
            with connection:
                connection.execute("CREATE TABLE example (value TEXT)")
                connection.execute("INSERT INTO example VALUES ('workout')")
        self.db = source.read_bytes()
        self.files = {backup.DB: self.db, "shared_prefs/settings.xml": b"<map />"}
        self.adb = FakeAdb(self.files)
        patcher = patch.object(backup.subprocess, "run", self.adb)
        patcher.start()
        self.addCleanup(patcher.stop)
        self.output = self.root / "new-backup"

    def test_success_preserves_bytes_and_pins_serial(self):
        self.files["shared_prefs/other.xml"] = b"<map><string name='x'>secret</string></map>"
        backup.backup("emulator-5554", self.output)
        self.assertEqual((self.output / backup.DB).read_bytes(), self.db)
        for path in ("shared_prefs/settings.xml", "shared_prefs/other.xml"):
            self.assertEqual((self.output / path).read_bytes(), self.files[path])
        self.assertTrue(all(len(c) == 5 and c[:4] == ["adb", "-s", "emulator-5554", "exec-out"] for c in self.adb.calls))

    def test_live_wal_and_shm_are_preserved_without_integrity_check_mutation(self):
        source = self.root / "wal.db"
        connection = sqlite3.connect(str(source))
        self.addCleanup(connection.close)
        connection.execute("PRAGMA journal_mode=WAL")
        connection.execute("PRAGMA wal_autocheckpoint=0")
        connection.execute("CREATE TABLE workouts (value TEXT)")
        connection.execute("INSERT INTO workouts VALUES ('pending')")
        connection.commit()
        for suffix in ("", "-wal", "-shm"):
            self.files[backup.DB + suffix] = Path(str(source) + suffix).read_bytes()
        backup.backup("serial", self.output)
        for suffix in ("", "-wal", "-shm"):
            self.assertEqual((self.output / (backup.DB + suffix)).read_bytes(),
                             self.files[backup.DB + suffix])

    def test_explicit_adb_is_used_for_every_read(self):
        self.assertEqual(backup.main(["--serial", "serial", "--output", str(self.output),
                                      "--adb", "custom adb"]), 0)
        self.assertTrue(self.adb.calls)
        self.assertTrue(all(call[0] == "custom adb" for call in self.adb.calls))

    def test_sdk_adb_default_and_path_fallback(self):
        sdk_adb = self.root / "Android" / "Sdk" / "platform-tools" / "adb.exe"
        sdk_adb.parent.mkdir(parents=True)
        with patch.dict(backup.os.environ, {"LOCALAPPDATA": str(self.root)}):
            self.assertEqual(backup.default_adb(), "adb")
            sdk_adb.write_bytes(b"fake executable")
            self.assertEqual(backup.default_adb(), str(sdk_adb))

    def test_existing_output_is_untouched(self):
        self.output.mkdir()
        sentinel = self.output / "sentinel"
        sentinel.write_bytes(b"keep")
        with self.assertRaises(backup.BackupError):
            backup.backup("serial", self.output)
        self.assertEqual(sentinel.read_bytes(), b"keep")
        self.assertFalse(self.adb.calls)

    def test_changing_database_wal_shm_or_prefs_fails(self):
        for path in (backup.DB, backup.DB + "-wal", backup.DB + "-shm", "shared_prefs/settings.xml"):
            with self.subTest(path=path):
                original = self.files.get(path, b"initial")
                self.files[path] = lambda count, original=original: original if count == 1 else original + b"changed"
                self.adb.read_counts.clear()
                with self.assertRaises(backup.BackupError):
                    backup.backup("serial", self.output)
                self.assertFalse(self.output.exists())
                if path in (backup.DB + "-wal", backup.DB + "-shm"):
                    del self.files[path]
                else:
                    self.files[path] = original

    def test_missing_or_empty_required_data_fails(self):
        for path in (backup.DB, "shared_prefs/settings.xml"):
            for value in (None, b""):
                with self.subTest(path=path, value=value):
                    original = self.files[path]
                    self.files[path] = value
                    with self.assertRaises(backup.BackupError):
                        backup.backup("serial", self.output)
                    self.assertFalse(self.output.exists())
                    self.files[path] = original

    def test_missing_prefs_and_device_errors_fail(self):
        del self.files["shared_prefs/settings.xml"]
        with self.assertRaises(backup.BackupError):
            backup.backup("serial", self.output)
        self.adb.fail = True
        with self.assertRaises(backup.BackupError):
            backup.backup("serial", self.output)
        self.assertFalse(self.output.exists())

    def test_corrupt_database_fails_integrity_check(self):
        self.files[backup.DB] = b"not a database"
        with self.assertRaises(backup.BackupError):
            backup.backup("serial", self.output)
        self.assertFalse(self.output.exists())

    def test_main_reports_nonzero_on_adb_failure(self):
        self.adb.fail = True
        self.assertEqual(backup.main(["--serial", "serial", "--output", str(self.output)]), 1)
        self.assertFalse(self.output.exists())


if __name__ == "__main__":
    unittest.main()
