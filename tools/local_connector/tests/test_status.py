import asyncio
from dataclasses import replace
import time

from ironvellum_local_connector.settings import save_settings
from ironvellum_local_connector.status import connector_status
from test_watch_stdio import call, connect, folder


def test_status_handshake_and_frozen_endpoints(folder):
    path, settings = folder

    async def check():
        async with connect(path) as session:
            assert connector_status(path)["running"]
            assert not connector_status(path)["clientVerified"]
            await session.list_tools()
            await call(session, "get_training_status")
            result = connector_status(path)
            assert result["clientVerified"]
            assert len(result["sessions"]) == 1
            assert result["sessions"][0]["loadedSource"] is None
            assert not connector_status(path, now=time.time() + 16)["running"]
            save_settings(path, replace(settings, state_directory=settings.state_directory.with_name("next")))
            assert connector_status(path)["status"] == "client_reload_required"
        assert not connector_status(path)["running"]
    asyncio.run(check())

def test_distinct_loaded_sources_and_detached_archive(folder):
    from ironvellum_local_connector.contract import Refusal
    from ironvellum_local_connector.status import ready_drafts
    from test_watch_stdio import adopted, export, PROPOSAL
    import pytest

    path, settings = folder
    a, source = export(settings, 101)
    save_settings(path, replace(settings, selected_dataset_id=source["datasetId"]))

    async def check():
        async with connect(path) as first:
            await adopted(first, a)
            proposal = await call(first, "create_workout_draft", sourceSha256=a,
                                  idempotencyKey="detached", draft=PROPOSAL["draft"])
            b, _ = export(settings, 102)
            async with connect(path) as second:
                await adopted(second, b)
                result = connector_status(path)
                assert {s["loadedSource"]["sourceSha256"] for s in result["sessions"]} == {a, b}
                assert all(s["loadedSource"]["coverage"]["setsIncluded"] is not None for s in result["sessions"])
        return settings.outbox_directory / proposal["filename"]

    draft = asyncio.run(check())
    for name in ("101.json", "102.json"):
        (settings.input_directory / name).unlink()
    [item] = ready_drafts(path)
    assert item["sourceSha256"] == a and item["filename"] == draft.name
    original = draft.read_bytes()
    draft.write_bytes(original + b" ")
    with pytest.raises(Refusal, match="outbox_conflict"):
        ready_drafts(path)
    draft.unlink()
    assert ready_drafts(path) == [] and not draft.exists()

def test_status_does_not_recreate_missing_endpoint(folder):
    path, settings = folder
    settings.state_directory.rmdir()
    result = connector_status(path)
    assert result["code"] == "storage_unavailable"
    assert not settings.state_directory.exists()
    from ironvellum_local_connector.status import StatusReader
    assert StatusReader(path).read()["code"] == "storage_unavailable"
    assert not settings.state_directory.exists()

def test_damaged_records_keep_verified_client_visible(folder):
    import os
    import subprocess
    import sys
    import json
    from ironvellum_local_connector.status import StatusReader, _directory
    from ironvellum_local_connector.storage import FolderFiles

    from test_watch_stdio import adopted, export
    path, settings = folder
    digest, source = export(settings, 33)
    save_settings(path, replace(settings, selected_dataset_id=source["datasetId"]))

    async def check():
        async with connect(path) as client:
            await client.list_tools()
            await adopted(client, digest)
            directory = _directory(path)
            with FolderFiles(directory) as files:
                for name in ("1" * 32 + ".json", "2" * 32 + ".json"):
                    files.write(name, b'{"private":"CANARY"', replace=False)
                os.link(directory / name, directory / "second-name")
            cli = subprocess.run([sys.executable, "-I", "-m", "ironvellum_local_connector", "status",
                                  "--settings", str(path), "--json"], capture_output=True, check=True)
            for result in (json.loads(cli.stdout), StatusReader(path).read()):
                assert result["code"] == "invalid_session"
                assert result["running"] and result["clientVerified"] and len(result["sessions"]) == 1
                assert result["sessions"][0]["loadedSource"]["sourceSha256"] == digest
                assert "CANARY" not in str(result)
            assert (directory / name).stat().st_nlink == 2
    asyncio.run(check())


def test_writer_reclaims_only_expired_validated_records_in_batches(folder, monkeypatch):
    import json
    import os
    from ironvellum_local_connector import status
    from ironvellum_local_connector.storage import FolderFiles
    from ironvellum_local_connector.watched_source import WatchedSource

    path, _ = folder
    heartbeat = status.Heartbeat(path, WatchedSource(path))
    directory = status._directory(path)
    with FolderFiles(directory) as heartbeat.files:
        heartbeat.publish()
        record = json.loads(heartbeat.files.read(heartbeat.name, 8192))
        expired = dict(record, timeMs=record["timeMs"] - 30000)
        stale = [f"{i:032x}.json" for i in range(9)]
        for name in stale:
            heartbeat.files.write(name, json.dumps(expired).encode(), replace=False)
        protected = {"a" * 32 + ".json": dict(expired, unexpected=True),
                     "b" * 32 + ".json": dict(record, timeMs=record["timeMs"] + 30000),
                     "c" * 32 + ".json": expired,
                     "other.json": expired}
        for name, value in protected.items():
            heartbeat.files.write(name, json.dumps(value).encode(), replace=False)
        os.link(directory / ("c" * 32 + ".json"), directory / "hardlink")
        monkeypatch.setattr(status, "SCAN_LIMIT", 10)
        assert status.connector_status(path)["sessionProblem"] == "session_scan_limit"
        assert all((directory / n).exists() for n in stale)
        monkeypatch.setattr(status, "SWEEP_BATCH", 2)
        heartbeat.reclaim()
        assert sum(not (directory / n).exists() for n in stale) <= 2
        for _ in range(30):
            heartbeat.reclaim()
        heartbeat.close_sweep()
        assert all(not (directory / n).exists() for n in stale)
        assert all((directory / n).exists() for n in protected)
        assert (directory / heartbeat.name).exists()
        recovered = status.connector_status(path)
        assert recovered["running"] and recovered["sessionProblem"] == "invalid_session"

def test_empty_and_damaged_archive_recovery(folder):
    from ironvellum_local_connector.desktop_model import DesktopObserver
    from ironvellum_local_connector.journal import DB_NAME
    from ironvellum_local_connector.storage import FolderFiles
    path, settings = folder
    observer = DesktopObserver(path)
    directory = settings.outbox_directory
    before = set(directory.iterdir())
    assert not observer.read().drafts_problem
    assert set(directory.iterdir()) == before
    with FolderFiles(directory) as files, files.serialized():
        files.write(DB_NAME, b"SQLite format 3\0" + bytes(400), replace=False)
    assert observer.read().drafts_problem
    observer.save(settings.input_directory, directory, False)
    (directory / DB_NAME).unlink()
    recovered = observer.read()
    assert not recovered.drafts_problem and not recovered.settings.watch_enabled


def test_watch_off_inventory_changes_independently_of_session(folder):
    from ironvellum_local_connector.status import StatusReader
    from test_watch_stdio import adopted, export
    path, settings = folder
    first, source = export(settings, 1)
    save_settings(path, replace(settings, selected_dataset_id=source["datasetId"], watch_enabled=False))
    reader = StatusReader(path)

    async def check():
        reader.read()
        async with connect(path) as client:
            await adopted(client, first)
            assert reader.read()["availableSource"]["sourceSha256"] == first
            (settings.input_directory / "1.json").unlink()
            await asyncio.sleep(2.05)
            report = reader.read()
            assert report["availableSource"] is None
            assert report["sessions"][0]["loadedSource"]["sourceSha256"] == first
            second, _ = export(settings, 2)
            await asyncio.sleep(2.05)
            reader.read()
            await asyncio.sleep(2.05)
            report = reader.read()
            assert report["availableSource"]["sourceSha256"] == second
            assert report["sessions"][0]["loadedSource"]["sourceSha256"] == first
        assert reader.read()["sessions"] == []
    asyncio.run(check())

def test_detached_reader_waits_for_committed_journal(folder):
    from concurrent.futures import ThreadPoolExecutor
    from threading import Event
    from ironvellum_local_connector.journal import DB_NAME
    from ironvellum_local_connector.desktop_model import DesktopObserver
    from ironvellum_local_connector.storage import FolderFiles

    path, settings = folder
    started = Event()
    def read():
        started.set()
        return DesktopObserver(path).read()
    with ThreadPoolExecutor() as executor, FolderFiles(settings.outbox_directory) as files:
        with files.serialized():
            files.write(DB_NAME, b"SQLite format 3\0" + bytes(400), replace=False)
            result = executor.submit(read)
            assert started.wait(5)
            from concurrent.futures import TimeoutError
            import pytest
            with pytest.raises(TimeoutError):
                result.result(timeout=.3)
        assert result.result(timeout=5).drafts_problem
