import json
import unittest
from dataclasses import replace
from pathlib import Path
from tempfile import TemporaryDirectory

from ironvellum_local_connector.contract import Refusal
from ironvellum_local_connector.settings import ConnectorSettings, load_settings, save_settings
from ironvellum_local_connector.storage import FolderFiles


class SettingsTests(unittest.TestCase):
    def setUp(self):
        root = TemporaryDirectory()
        self.addCleanup(root.cleanup)
        self.root = Path(root.name)
        self.value = ConnectorSettings(self.root / "in", self.root / "out", self.root / "state")
        self.value.input_directory.mkdir()
        self.path = self.root / "config" / "settings.json"

    def test_roundtrip(self):
        save_settings(self.path, self.value)
        self.assertEqual(load_settings(self.path), self.value)
        data = json.loads(self.path.read_bytes())
        self.assertFalse(data["watchEnabled"])
        with FolderFiles(self.path.parent) as files:
            files.write(self.path.name, json.dumps(dict(data, unused=1)).encode(), replace=True)
        with self.assertRaises(Refusal):
            load_settings(self.path)

    def test_overlap(self):
        with self.assertRaisesRegex(Refusal, "overlapping_paths"):
            save_settings(self.path, replace(self.value, state_directory=self.value.input_directory / "state"))

    def test_folder_io_refuses_escaping_names(self):
        with FolderFiles(self.value.outbox_directory) as out:
            with self.assertRaises(Refusal):
                out.write("../outside", b"unexpected", replace=True)
            self.assertFalse(hasattr(out, "context"))

    def test_settings_cannot_be_written_into_shared_folder_or_outbox(self):
        for endpoint in (self.value.input_directory, self.value.outbox_directory):
            with self.subTest(endpoint=endpoint.name), self.assertRaisesRegex(Refusal, "overlapping_paths"):
                save_settings(endpoint / "settings.json", self.value)
            self.assertFalse((endpoint / "settings.json").exists())

    def test_missing_settings_directory_stays_missing(self):
        with self.assertRaises(FileNotFoundError):
            load_settings(self.path)
        self.assertFalse(self.path.parent.exists())
