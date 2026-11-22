import json
import os
import unittest
from dataclasses import replace
from hashlib import sha256
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch
from uuid import UUID

from ironvellum_local_connector.contract import Refusal
from ironvellum_local_connector.settings import ConnectorSettings, save_settings
from ironvellum_local_connector.storage import FolderFiles
from ironvellum_local_connector.watched_source import WatchedSource

FIXTURE = Path(__file__).resolve().parents[1] / "contracts/fixtures/context.json"
BASE = json.loads(FIXTURE.read_bytes())
DATA = BASE["datasetId"]


class SourceTests(unittest.TestCase):
    def setUp(self):
        tmp = TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        self.settings = ConnectorSettings(self.root / "input", self.root / "out", self.root / "state",
                                          DATA, True)
        self.settings.input_directory.mkdir()
        self.config = self.root / "config" / "settings.json"
        save_settings(self.config, self.settings)
        self.now = 0
        self.source = WatchedSource(self.config, lambda: self.now)

    def write(self, n, dataset=DATA):
        path = self.settings.input_directory / f"{n}.json"
        raw = json.dumps(dict(BASE, datasetId=dataset, snapshotId=str(UUID(int=n, version=4)),
                              exportedAtMs=BASE["exportedAtMs"]+n)).encode()
        path.write_bytes(raw)
        return path, sha256(raw).hexdigest()

    def poll(self):
        self.now += 2
        return self.source.refresh()

    def settle(self):
        self.poll()
        return self.poll()

    def test_rejects_bad_and_conflicting_newer_keeps_original(self):
        path, token = self.write(1)
        self.assertIsNone(self.poll().active)
        original = self.poll().active
        self.assertEqual(original.sha256, token)
        for raw in (b"{", b'{"schemaVersion":99}', path.read_bytes()+b" "):
            path.write_bytes(raw)
            self.assertIsNotNone(self.settle().error_code)
            self.assertIs(self.source.current(), original)
            self.assertEqual(sha256(original.raw).hexdigest(), token)

    def test_older_timestamp_and_eviction(self):
        path, token = self.write(5)
        previous = self.settle().active
        self.write(1)
        self.assertIs(self.settle().active, previous)
        for n in range(6, 10):
            self.write(n)
            self.settle()
        with self.assertRaisesRegex(Refusal, "source_expired"):
            self.source.resolve(token)
        self.assertEqual(previous.sha256, token)

    def test_explicit_switch_with_watch_off_and_fixed_outbox(self):
        other = str(UUID(int=900, version=4))
        self.write(1)
        self.write(2, other)
        value = replace(self.settings, selected_dataset_id=None, watch_enabled=False)
        save_settings(self.config, value)
        self.assertEqual(len(self.settle().pending), 2)
        with self.assertRaisesRegex(Refusal, "source_unavailable"):
            self.source.current()
        self.source.select_dataset(DATA)
        self.assertEqual(self.source.current().dataset, DATA)
        self.write(3)
        self.assertEqual(self.settle().active.snapshot, str(UUID(int=1, version=4)))
        self.source.select_dataset(other)
        self.assertEqual(self.source.current().dataset, other)
        with FolderFiles(value.outbox_directory) as out:
            save_settings(self.config, replace(value, outbox_directory=self.root / "newout",
                                               selected_dataset_id=other))
            self.assertTrue(self.poll().reload_required)
            out.publish("draft.json", b"original")
        self.assertTrue((value.outbox_directory / "draft.json").exists())
        self.assertFalse((self.root / "newout/draft.json").exists())

    def test_budget_progress_and_excessive_entries(self):
        self.write(1)
        self.settle()
        for n in range(2, 7):
            self.write(n)
        with patch("ironvellum_local_connector.watched_source.SCAN_BYTES",
                   (self.settings.input_directory / "1.json").stat().st_size+8):
            for _ in range(9):
                self.poll()
            self.assertEqual(self.source.current().snapshot, str(UUID(int=6, version=4)))
        for n in range(260):
            (self.settings.input_directory / f"x{n}.json").touch()
        self.assertEqual(self.poll().error_code, "file_count_limit")

    def test_reparse_candidate(self):
        path, token = self.write(1)
        self.settle()
        # A second hard link is unsafe on all supported platforms.
        os.link(path, self.settings.input_directory / "link.json")
        self.assertIsNotNone(self.poll().error_code)
        self.assertEqual(self.source.current().sha256, token)

    def test_replaced_during_read_retains_exact_request_source(self):
        from ironvellum_local_connector import storage
        _, token = self.write(1)
        held = self.settle().active
        path, _ = self.write(2)
        self.poll()
        inspect = storage.file_stamp
        calls = 0

        def changed(directory, name):
            nonlocal calls
            stamp = inspect(directory, name)
            if name == path.name:
                calls += 1
                if calls == 1:
                    # A replacement is observed by the final guarded name lookup.
                    return (stamp[0], stamp[1] + 1, *stamp[2:])
            return stamp

        with patch.object(storage, "file_stamp", changed):
            status = self.poll()
        self.assertEqual(status.error_code, "source_changed")
        self.assertIs(status.active, held)
        self.assertEqual(held.sha256, token)

    def test_empty_then_partial_then_complete(self):
        self.assertIsNone(self.poll().active)
        with self.assertRaisesRegex(Refusal, "source_unavailable"):
            self.source.current()
        path, token = self.write(1)
        raw = path.read_bytes()
        path.write_bytes(raw[:len(raw)//2])
        self.assertIsNone(self.settle().active)
        path.write_bytes(raw)
        self.assertIsNone(self.poll().active)
        self.now += 1.99
        self.assertIsNone(self.source.refresh().active)
        self.now += .02
        self.assertEqual(self.source.refresh().active.sha256, token)

    def test_symlink_or_junction_input(self):
        import subprocess
        linked = self.root / "linked"
        if os.name == "nt":
            subprocess.run(["cmd", "/d", "/c", "mklink", "/J", str(linked),
                            str(self.settings.input_directory)], check=True, capture_output=True)
        else:
            linked.symlink_to(self.settings.input_directory, target_is_directory=True)
        with self.assertRaises((OSError, Refusal)):
            save_settings(self.config, replace(self.settings, input_directory=linked))

    def test_one_dataset_still_needs_choice(self):
        self.write(1)
        save_settings(self.config, replace(self.settings, selected_dataset_id=None))
        status = self.settle()
        self.assertIsNone(status.active)
        self.assertEqual([item.dataset_id for item in status.pending], [DATA])
        self.assertEqual(status.pending[0].sets_included, BASE["coverage"]["setsIncluded"])
        self.source.select_dataset(DATA)
        self.assertEqual(self.source.current().dataset, DATA)

    def test_startup_binds_previous_endpoints_and_external_selection_is_live(self):
        self.write(1)
        other = str(UUID(int=900, version=4))
        self.write(2, other)
        original = replace(self.settings, watch_enabled=False)
        changed = replace(original, outbox_directory=self.root / "new-output", selected_dataset_id=other)
        with FolderFiles(original.outbox_directory) as out:
            save_settings(self.config, changed)
            self.source = WatchedSource(self.config, lambda: self.now, initial_settings=original)
            status = self.settle()
            self.assertTrue(status.reload_required)
            self.assertEqual(status.active.dataset, other)
            old_request = self.source.resolve(status.active.sha256)
            save_settings(self.config, replace(changed, selected_dataset_id=DATA))
            self.assertEqual(self.poll().active.dataset, DATA)
            self.assertIs(self.source.resolve(old_request.sha256), old_request)
            out.publish("draft.json", b"held")
        self.assertFalse((changed.outbox_directory / "draft.json").exists())

    def test_tie_break_and_enable_watch(self):
        original = replace(self.settings, watch_enabled=False)
        save_settings(self.config, original)
        path, _ = self.write(1)
        held = self.settle().active
        _, expected = self.write(3)
        raw = json.loads(path.read_bytes())
        raw["exportedAtMs"] = BASE["exportedAtMs"] + 3
        raw["snapshotId"] = str(UUID(int=2, version=4))
        path.write_text(json.dumps(raw))
        # The later filesystem timestamp belongs to snapshot 2.
        os.utime(path, (2_000_000_000, 2_000_000_000))
        self.assertIs(self.settle().active, held)
        save_settings(self.config, self.settings)
        self.assertEqual(self.poll().active.sha256, expected)

    def test_disabled_watch_settles_whole_initial_batch(self):
        save_settings(self.config, replace(self.settings, watch_enabled=False))
        for n in range(1, 5):
            _, newest = self.write(n)
        budget = max(p.stat().st_size for p in self.settings.input_directory.iterdir())
        with patch("ironvellum_local_connector.watched_source.SCAN_BYTES", budget):
            self.poll()
            self.assertIsNone(self.poll().active)
            for _ in range(5):
                status = self.poll()
            self.assertEqual(status.active.sha256, newest)

    def test_original_endpoints_detect_same_path_replacement_or_removal(self):
        self.write(1)
        held = self.settle().active
        for endpoint in self.settings.endpoints[1:]:
            with self.subTest(endpoint=endpoint.name):
                retired = endpoint.with_suffix(".retired")
                endpoint.rename(retired)
                try:
                    status = self.poll()
                    self.assertTrue(status.reload_required)
                    self.assertIs(status.active, held)
                    self.assertFalse(endpoint.exists())
                    with FolderFiles(endpoint):
                        pass  # A valid private replacement still has a different identity.
                    self.assertTrue(self.poll().reload_required)
                    self.assertIs(self.source.resolve(held.sha256), held)
                finally:
                    if endpoint.exists():
                        endpoint.rmdir()
                    retired.rename(endpoint)
                self.assertFalse(self.poll().reload_required)

    @unittest.skipIf(os.name == "nt", "POSIX pinned directory descriptors")
    def test_open_outbox_remains_on_retired_directory(self):
        with FolderFiles(self.settings.outbox_directory) as out:
            retired = self.root / "retired"
            self.settings.outbox_directory.rename(retired)
            self.settings.outbox_directory.mkdir()
            self.assertTrue(self.poll().reload_required)
            out.publish("draft.json", b"held")
            self.assertEqual((retired / "draft.json").read_bytes(), b"held")
            self.assertFalse((self.settings.outbox_directory / "draft.json").exists())

    def test_nonregular_entries_do_not_displace_regular_export(self):
        from ironvellum_local_connector import storage
        from unittest.mock import MagicMock
        entries = MagicMock()
        unsafe = MagicMock(name="nonregular")
        unsafe.name = "pipe.json"
        unsafe.is_dir.return_value = unsafe.is_file.return_value = False
        regular = MagicMock()
        regular.name = "training.json"
        regular.is_file.return_value = True
        regular.is_dir.return_value = False
        entries.__enter__.return_value = [unsafe] * 256 + [regular]
        with patch.object(storage.os, "scandir", return_value=entries):
            names, error = storage.candidate_names(MagicMock(), 256, 4096)
        self.assertEqual(names, ["training.json"])
        self.assertEqual(error, "regular_file_required")

    def test_disabled_explicit_choice_waits_for_newer_initial_copies(self):
        other = str(UUID(int=900, version=4))
        save_settings(self.config, replace(self.settings, watch_enabled=False))
        self.write(1)
        self.write(2, other)
        held = self.settle().active
        for n in range(3, 7):
            _, newest = self.write(n, other)
        budget = max(p.stat().st_size for p in self.settings.input_directory.iterdir())
        with patch("ironvellum_local_connector.watched_source.SCAN_BYTES", budget):
            self.source.select_dataset(other)
            self.assertIsNone(self.source.refresh().active)
            self.assertIs(self.source.resolve(held.sha256), held)
            for _ in range(7):
                status = self.poll()
            self.assertEqual(status.active.sha256, newest)
