"""Request-driven polling of explicitly shared exports. Only adopted sources are retained."""
from collections import OrderedDict
from contextlib import ExitStack
from dataclasses import dataclass, replace
from pathlib import Path
from threading import RLock
import time
from typing import Callable

from .contract import MAX_CONTEXT, Refusal, require
from .settings import ConnectorSettings, endpoint_identities, load_settings, update_settings
from .snapshot import Snapshot
from .storage import Directory, candidate_names, file_stamp, stable_bytes

INTERVAL = 2
SCAN_BYTES = 32 * 1024 * 1024
CANDIDATES = 256
ENTRIES = 4096


@dataclass(frozen=True)
class SourceSummary:
    dataset_id: str
    snapshot_id: str
    exported_at_ms: int
    sha256: str
    workouts_included: int
    sets_included: int
    saved_workouts_included: int

    @property
    def order(self):
        return self.exported_at_ms, self.snapshot_id


@dataclass(frozen=True)
class RefreshStatus:
    active: Snapshot | None = None
    pending: tuple[SourceSummary, ...] = ()
    scanning: bool = False
    error_code: str | None = None
    reload_required: bool = False
    available: SourceSummary | None = None


@dataclass
class Observation:
    stamp: tuple
    since: float
    summary: SourceSummary | None = None
    error: str | None = None


def _read(directory, name, stamp):
    raw = stable_bytes(directory, name, stamp)
    snap = Snapshot(raw)
    snap.raw = raw
    return snap


def _code(error):
    return str(error) if isinstance(error, Refusal) else "storage_unavailable"


class WatchedSource:
    def __init__(self, settings_path: Path, clock: Callable[[], float] = time.monotonic, *,
                 initial_settings: ConnectorSettings | None = None, create_endpoints=True, inventory_only=False):
        self.path = settings_path
        self.clock = clock
        self.inventory_only = inventory_only
        self.settings = initial_settings or load_settings(settings_path)
        self.identities = endpoint_identities(self.settings, create=create_endpoints)
        self._next = float("-inf")
        self._initial_batch = None
        self._observations = {}
        self._cache = OrderedDict()
        self._active = None
        self._status = RefreshStatus()
        self._lock = RLock()

    @property
    def last_status(self):
        with self._lock:
            return self._status

    def current(self) -> Snapshot:
        active = self.refresh().active
        require(active is not None, "source_unavailable")
        return active

    def resolve(self, source_sha256: str) -> Snapshot:
        with self._lock:
            self.refresh()
            require(source_sha256 in self._cache, "source_expired")
            return self._cache[source_sha256]

    def select_dataset(self, dataset_id: str) -> None:
        with self._lock:
            status = self.refresh()
            require(any(s.dataset_id == dataset_id for s in status.pending)
                    or status.active is not None and status.active.dataset == dataset_id, "dataset_unavailable")
            def choose(value):
                require(value.endpoints == self.settings.endpoints, "reload_required")
                return replace(value, selected_dataset_id=dataset_id)
            update_settings(self.path, choose)
            self._next = float("-inf")
            self.refresh()

    def refresh(self) -> RefreshStatus:
        with self._lock:
            now = self.clock()
            if now < self._next:
                return self._status
            self._next = now + INTERVAL
            try:
                with ExitStack() as stack:
                    try:
                        opened = [Directory(path, stack, private=index != 0, create=False)
                                  for index, path in enumerate(self.settings.endpoints)]
                        require(tuple(d.identity for d in opened) == self.identities, "reload_required")
                    except (OSError, Refusal) as exc:
                        raise Refusal("reload_required") from exc
                    configured = load_settings(self.path)
                    reload_needed = configured.endpoints != self.settings.endpoints
                    if configured.selected_dataset_id != self.settings.selected_dataset_id:
                        self._initial_batch = None
                        self._active = None
                    self.settings = replace(self.settings, selected_dataset_id=configured.selected_dataset_id,
                                            watch_enabled=configured.watch_enabled)
                    self._status = replace(self._status, active=self._active, reload_required=reload_needed)
                    self._status = self._scan(opened[0], now, reload_needed)
            except (OSError, Refusal) as error:
                self._status = replace(self._status, active=self._active, available=None, pending=(), scanning=False,
                                       error_code=_code(error),
                                       reload_required=self._status.reload_required or str(error) == "reload_required")
            return self._status

    def _observe(self, directory, now):
        names, error = candidate_names(directory, CANDIDATES, ENTRIES)
        self._observations = {n: self._observations[n] for n in names if n in self._observations}
        budget = SCAN_BYTES
        scanning = False
        best = None
        best_order = None
        for name in names:
            try:
                stamp = file_stamp(directory, name)
                obs = self._observations.get(name)
                if obs is None or obs.stamp != stamp:
                    self._observations[name] = Observation(stamp, now)
                    scanning = True
                    continue
                if now - obs.since < INTERVAL:
                    scanning = True
                    continue
                if obs.error:
                    error = obs.error
                    continue
                if obs.summary:
                    continue
                require(stamp[2] <= MAX_CONTEXT, "file_limit")
                if stamp[2] > budget:
                    scanning = True
                    continue
                budget -= stamp[2]
                snap = _read(directory, name, stamp)
                metadata = snap.metadata(0)
                coverage = metadata["coverage"]
                summary = SourceSummary(snap.dataset, snap.snapshot, metadata["exportedAtMs"], snap.sha256,
                                        coverage["workoutsIncluded"], coverage["setsIncluded"],
                                        coverage["savedWorkoutsIncluded"])
                obs.summary = summary
                if (summary.dataset_id == self.settings.selected_dataset_id
                        and (best_order is None or summary.order > best_order)):
                    best, best_order = snap, summary.order
            except (OSError, Refusal) as exc:
                error = _code(exc)
                if error in ("invalid_data", "file_limit") and name in self._observations:
                    self._observations[name].error = error
                else:
                    self._observations.pop(name, None)

        return budget, scanning, error, best

    def _latest_by_dataset(self):
        ids = {}
        conflicts = set()
        def record(identifier, digest):
            if ids.setdefault(identifier, digest) != digest:
                conflicts.add(identifier)
        for snap in self._cache.values():
            record(snap.snapshot, snap.sha256)
        for obs in self._observations.values():
            if obs.summary:
                record(obs.summary.snapshot_id, obs.summary.sha256)
        latest = {}
        for name, obs in self._observations.items():
            s = obs.summary
            if s and s.snapshot_id not in conflicts:
                prior = latest.get(s.dataset_id)
                if prior is None or s.order > prior[1].order:
                    latest[s.dataset_id] = name, s
        return latest, conflicts

    def _scan(self, directory, now, reload_needed):
        budget, scanning, error, best = self._observe(directory, now)
        latest, conflicts = self._latest_by_dataset()
        if conflicts:
            error = "snapshot_conflict"
        candidate = latest.pop(self.settings.selected_dataset_id, None)
        initial_ready = self._initial_ready()
        if not self.inventory_only and candidate and initial_ready and self._should_adopt(candidate[1]):
            name, s = candidate
            snap = self._cache.get(s.sha256)
            if snap is None and best and best.sha256 == s.sha256:
                snap = best
            if snap is None:
                stamp = self._observations[name].stamp
                if stamp[2] <= budget:
                    snap = _read(directory, name, stamp)
                    require(snap.sha256 == s.sha256, "source_changed")
                else:
                    scanning = True
            if snap is not None:
                self._active = snap
                self._cache[s.sha256] = snap
                self._cache.move_to_end(s.sha256)
                if len(self._cache) > 4:
                    self._cache.popitem(last=False)
        pending = tuple(s for _, s in sorted(latest.values(), key=lambda item: item[1].dataset_id))
        return RefreshStatus(self._active, pending, scanning, error, reload_needed,
                             candidate[1] if candidate and initial_ready else None)

    def _initial_ready(self):
        if self.settings.watch_enabled or self._active is not None:
            self._initial_batch = None
            return True
        # Finish the versions present at selection time. Later arrivals or a file
        # being repeatedly rewritten must not indefinitely delay the first choice.
        if self._initial_batch is None:
            self._initial_batch = {name: obs.stamp for name, obs in self._observations.items()}
        for name, stamp in self._initial_batch.items():
            obs = self._observations.get(name)
            if obs and obs.stamp == stamp and not (obs.summary or obs.error):
                return False
        self._initial_batch = None
        return True

    def _should_adopt(self, summary):
        if self._active is None:
            return True
        current = self._active
        return self.settings.watch_enabled and summary.order > (current.metadata(0)["exportedAtMs"], current.snapshot)
