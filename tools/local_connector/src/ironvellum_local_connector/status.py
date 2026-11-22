"""Read-only diagnostics and session evidence for the client-owned stdio process."""
import asyncio
from contextlib import suppress
from hashlib import sha256
import os
import re
import time
from uuid import uuid4

from .contract import MAX_TIME, Refusal, encode, loads, require
from .settings import DATASET_ID, endpoint_identities, load_settings
from .storage import FolderFiles
from .watched_source import CANDIDATES, INTERVAL, RefreshStatus, WatchedSource

STALE_SECONDS = 15
SWEEP_BATCH = 16
SCAN_LIMIT = 4096
SESSION_FIELDS = {"timeMs", "endpoints", "initialized", "toolsListed", "statusChecked", "loadedSource", "reloadRequired"}
SESSION_FILE = re.compile(r"[0-9a-f]{32}\.json")
TOKEN = re.compile(r"[0-9a-f]{64}")


def _directory(path):
    # A stable rendezvous lets readers find processes still using an earlier state directory.
    key = sha256(os.fsencode(os.path.normcase(str(path)))).hexdigest()[:24]
    return path.parent / (".ironvellum-sessions-" + key)


def _endpoints(value, identities):
    return sha256(encode([[str(p), list(identity)] for p, identity in zip(value.endpoints, identities)])).hexdigest()


def _loaded(active):
    if active is None:
        return None
    meta = active.metadata(0)
    return dict(datasetId=active.dataset, snapshotId=active.snapshot, sourceSha256=active.sha256,
                exportedAtMs=meta["exportedAtMs"], coverage={k: meta["coverage"][k] for k in
                ("workoutsIncluded", "setsIncluded", "savedWorkoutsIncluded")})


class Heartbeat:
    def __init__(self, path, sources):
        self.directory = _directory(path)
        self.sources = sources
        self.name = uuid4().hex + ".json"
        self.endpoints = _endpoints(sources.settings, sources.identities)
        self.initialized = self.listed = self.checked = False
        self.sweep = None

    def publish(self):
        status = self.sources.last_status
        record = dict(initialized=self.initialized, timeMs=int(time.time() * 1000), endpoints=self.endpoints, toolsListed=self.listed,
                      statusChecked=self.checked, loadedSource=_loaded(status.active), reloadRequired=status.reload_required)
        raw = encode(record)
        with self.files.serialized():
            self.files.write(self.name, raw, replace=True)
            self.last_record = raw

    def close_sweep(self):
        if self.sweep is not None:
            self.sweep.close()
            self.sweep = None

    def reclaim(self):
        # Keep the cursor between ticks so healthy records at the front cannot starve cleanup.
        if self.sweep is None:
            self.sweep = _entries(self.files)
        with self.files.serialized():
            for _ in range(SWEEP_BATCH):
                entry = next(self.sweep, None)
                if entry is None:
                    self.close_sweep()
                    break
                if entry.name == self.name or not SESSION_FILE.fullmatch(entry.name):
                    continue
                try:
                    record = _session(entry.name, self.files.read(entry.name, 8192), self.endpoints)
                    if time.time() - record["lastSeenMs"] / 1000 > STALE_SECONDS:
                        self.files.dir.unlink(entry.name)
                except (OSError, Refusal):
                    continue

    def _try_reclaim(self):
        try:
            self.reclaim()
        except (OSError, Refusal):
            self.close_sweep()

    def evidence(self, method):
        if method == "initialize":
            self.initialized = True
        if method == "list":
            self.listed = True
        if method == "status":
            self.checked = True
        self._try_publish()

    def _try_publish(self):
        # A temporary diagnostic write failure must not interrupt MCP requests.
        with suppress(OSError, Refusal):
            self.publish()

    async def _tick(self):
        while True:
            await asyncio.sleep(3)
            self._try_publish()
            self._try_reclaim()

    async def serve(self, files):
        from .stdio import run
        from .watch_server import create_watch_server
        with FolderFiles(self.directory) as self.files:
            self.publish()
            self._try_reclaim()
            tick = asyncio.create_task(self._tick())
            try:
                await run(files, lambda opened: create_watch_server(opened, self.sources, self.evidence),
                          initialized=lambda: self.evidence("initialize"))
            finally:
                tick.cancel()
                try:
                    with suppress(asyncio.CancelledError):
                        await tick
                finally:
                    self.close_sweep()
                    with suppress(OSError, Refusal), self.files.serialized():
                        if self.files.read(self.name, 8192) == self.last_record:
                            self.files.dir.unlink(self.name)


def _entries(files):
    return os.scandir(files.dir.path if os.name == "nt" else files.dir.fd)


def _session(name, raw, endpoints):
    row = loads(raw, 8192)
    require(isinstance(row, dict) and row.keys() == SESSION_FIELDS and type(row["timeMs"]) is int
            and 0 < row["timeMs"] <= MAX_TIME, "invalid_session")
    require(all(type(row.get(k)) is bool for k in ("initialized", "toolsListed", "statusChecked", "reloadRequired"))
            and isinstance(row["endpoints"], str) and TOKEN.fullmatch(row["endpoints"]), "invalid_session")
    source = row.get("loadedSource")
    if source is not None:
        require(isinstance(source, dict) and set(source) == {"datasetId", "snapshotId", "sourceSha256", "exportedAtMs", "coverage"}
                and all(isinstance(source[k], str) and DATASET_ID.fullmatch(source[k]) for k in ("datasetId", "snapshotId"))
                and isinstance(source["sourceSha256"], str) and TOKEN.fullmatch(source["sourceSha256"])
                and type(source["exportedAtMs"]) is int and 0 <= source["exportedAtMs"] <= MAX_TIME
                and isinstance(source["coverage"], dict)
                and set(source["coverage"]) == {"workoutsIncluded", "setsIncluded", "savedWorkoutsIncluded"}
                and all(type(v) is int and v >= 0 for v in source["coverage"].values()), "invalid_session")
    return dict(sessionId=name[:-5], lastSeenMs=row["timeMs"], toolsListed=row["toolsListed"],
                statusChecked=row["statusChecked"], loadedSource=source,
                initialized=row["initialized"],
                clientVerified=row["initialized"] and row["toolsListed"] and row["statusChecked"],
                reloadRequired=row["reloadRequired"] or row["endpoints"] != endpoints)


def _sessions(path, value, now):
    sessions = []
    problem = None
    try:
        with FolderFiles(_directory(path), create=False) as files:
            try:
                current_endpoints = _endpoints(value, endpoint_identities(value, create=False))
            except (OSError, Refusal):
                current_endpoints = None
            with _entries(files) as entries:
                for i, entry in enumerate(entries):
                    if i >= SCAN_LIMIT:
                        problem = "session_scan_limit"
                        break
                    if SESSION_FILE.fullmatch(entry.name):
                        try:
                            item = _session(entry.name, files.read(entry.name, 8192), current_endpoints)
                        except FileNotFoundError:
                            continue
                        except (OSError, Refusal):
                            problem = "invalid_session"
                            continue
                        if 0 <= now - item["lastSeenMs"] / 1000 <= STALE_SECONDS:
                            sessions.append(item)
    except FileNotFoundError:
        pass
    return sorted(sessions, key=lambda r: r["sessionId"]), problem


def diagnose_source(path, *, dataset_id=None):
    sources = WatchedSource(path, create_endpoints=False)
    status = sources.refresh()
    # A single read settles fresh file identities. Explicit selection can require more
    # scan batches for a large input folder. Bound work even when files keep changing.
    for _ in range(CANDIDATES if dataset_id is not None else 1):
        if not status.scanning or any(item.dataset_id == dataset_id for item in status.pending):
            break
        time.sleep(INTERVAL)
        status = sources.refresh()
    return sources, status


def select_source(path, dataset_id):
    require(DATASET_ID.fullmatch(dataset_id), "dataset_unavailable")
    sources, _ = diagnose_source(path, dataset_id=dataset_id)
    sources.select_dataset(dataset_id)
    return connector_status(path)


def ready_drafts(path):
    from .journal import ReadyArchive
    value = load_settings(path)
    with FolderFiles(value.outbox_directory, create=False) as files:
        return ReadyArchive(files).list_ready()


class StatusReader:
    """Reuse validated file observations across nonblocking desktop polls."""
    def __init__(self, path):
        self.path = path
        self.observer = None

    def read(self):
        value = load_settings(self.path)
        try:
            if self.observer is None or self.observer.settings.endpoints != value.endpoints:
                self.observer = WatchedSource(self.path, initial_settings=value, create_endpoints=False,
                                              inventory_only=True)
            diagnosis = self.observer.refresh()
            if diagnosis.reload_required:
                self.observer = None
        except (OSError, Refusal) as error:
            self.observer = None
            diagnosis = _problem(error)
        return _result(self.path, value, diagnosis, time.time())


def _problem(error):
    return RefreshStatus(error_code=str(error) if isinstance(error, Refusal) else "storage_unavailable")


def connector_status(path, *, now=None):
    value = load_settings(path)
    try:
        _, diagnosis = diagnose_source(path)
    except (OSError, Refusal) as error:
        diagnosis = _problem(error)
    return _result(path, value, diagnosis, time.time() if now is None else now)


def _result(path, value, diagnosis, now):
    from .watch_server import _summary
    sessions, session_problem = _sessions(path, value, now)
    actions = []
    if any(s["reloadRequired"] for s in sessions):
        state = "client_reload_required"
        actions.append("reload_client")
    elif sessions:
        state = "running"
    elif diagnosis.available or diagnosis.pending:
        state = "configured"
    else:
        state = "awaiting_training"
    if session_problem:
        actions.append("check_session_state")
    if diagnosis.error_code:
        actions.append("check_folders_or_export")
    if diagnosis.pending and not diagnosis.available:
        actions.append("select_source")
    elif not diagnosis.available:
        actions.append("transfer_training")
    if not sessions:
        actions.append("open_client")
    return dict(schemaVersion=1, status=state, code=diagnosis.error_code or session_problem or "ok",
                sessionProblem=session_problem, configured=True,
                running=bool(sessions), clientVerified=any(s["clientVerified"] for s in sessions),
                sessions=sessions, selectedDatasetId=value.selected_dataset_id,
                availableSource=_summary(diagnosis.available) if diagnosis.available else None,
                pending=[_summary(s) for s in diagnosis.pending], scanning=diagnosis.scanning, nextActions=actions)
