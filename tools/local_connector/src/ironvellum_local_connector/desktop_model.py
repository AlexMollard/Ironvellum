"""Read-only view data and explicit actions for the optional desktop window."""
from contextlib import ExitStack
from dataclasses import dataclass
import os
from pathlib import Path
import shutil
import subprocess
import sys
from unicodedata import category

from .contract import Refusal, require
from .settings import ConnectorSettings, load_settings
from .setup import configure
from .status import StatusReader, ready_drafts, select_source
from .storage import Directory, selected


def literal(value):
    return "".join("�" if category(char) in {"Cc", "Cf", "Cs"} else char for char in value)


@dataclass(frozen=True)
class Training:
    dataset: str
    snapshot: str
    token: str
    exported_ms: int
    workouts: int
    sets: int
    saved: int

    @classmethod
    def from_report(cls, row):
        coverage = row["coverage"]
        return cls(row["datasetId"], row["snapshotId"], row["sourceSha256"], row["exportedAtMs"],
                   coverage["workoutsIncluded"], coverage["setsIncluded"], coverage["savedWorkoutsIncluded"])


@dataclass(frozen=True)
class Draft:
    name: str
    filename: str


@dataclass(frozen=True)
class DesktopState:
    status_key: str
    loaded: tuple[Training, ...]
    available: Training | None
    pending: tuple[Training, ...]
    drafts: tuple[Draft, ...]
    selected: str | None
    session_count: int
    verified: int
    reload_required: bool
    scanning: bool
    problem: str | None
    session_problem: str | None
    drafts_problem: bool
    settings: ConnectorSettings | None

    @classmethod
    def from_report(cls, report, drafts, settings=None, *, drafts_problem=False):
        sessions = report.get("sessions", [])
        loaded = dict.fromkeys(Training.from_report(row["loadedSource"]) for row in sessions if row["loadedSource"])
        available = report.get("availableSource")
        pending = tuple(Training.from_report(row) for row in report.get("pending", []))
        if sessions:
            status = "running"
        elif not report.get("configured"):
            status = "unconfigured"
        elif available or pending:
            status = "configured"
        else:
            status = "awaiting_training"
        code = report.get("code", "ok")
        session_problem = report.get("sessionProblem")
        return cls(status, tuple(loaded), Training.from_report(available) if available else None,
                   pending, tuple(Draft(literal(row["name"]), row["filename"]) for row in drafts),
                   report.get("selectedDatasetId"), len(sessions), sum(row["clientVerified"] for row in sessions),
                   any(row["reloadRequired"] for row in sessions), report.get("scanning", False),
                   code if code not in ("ok", session_problem) else None, session_problem, drafts_problem, settings)


class DesktopObserver:
    def __init__(self, settings_path):
        self.path = settings_path
        self.reader = StatusReader(settings_path)

    def read(self):
        try:
            settings = load_settings(self.path)
            report = self.reader.read()
        except FileNotFoundError:
            return DesktopState.from_report({}, [])
        except (OSError, Refusal):
            return DesktopState.from_report({"code": "settings_unavailable"}, [])
        try:
            drafts = ready_drafts(self.path)
        except (OSError, Refusal):
            return DesktopState.from_report(report, [], settings, drafts_problem=True)
        return DesktopState.from_report(report, drafts, settings)

    def select(self, dataset):
        # Keeping the persisted choice does not require rescanning its export.
        # A live session may still hold that training after the file is replaced.
        if load_settings(self.path).selected_dataset_id != dataset:
            select_source(self.path, dataset)

    def save(self, inbox, outbox, watch):
        try:
            current = load_settings(self.path)
        except FileNotFoundError:
            current = None
        if current is None:
            # Let the setup path create private storage alongside the selected settings.
            state_directory = self.path.parent / (self.path.name + ".state")
            dataset = None
        else:
            state_directory, dataset = current.state_directory, current.selected_dataset_id
        require(inbox is not None and outbox is not None, "missing_folders")
        configure(self.path, ConnectorSettings(inbox, outbox, state_directory, dataset, watch), "config-only")


class FolderUnavailable(Exception):
    pass


def open_folder(folder: Path):
    """Open the explicitly selected directory. Never interpret a name as a command."""
    try:
        folder = selected(folder)
        if os.name != "nt":
            from .posix_files import check_mounts
            check_mounts(folder, folder)
        with ExitStack() as stack:
            Directory(folder, stack, private=False, create=False)
            if sys.platform == "win32":
                os.startfile(folder, "explore")
            else:
                opener = shutil.which("open" if sys.platform == "darwin" else "xdg-open")
                if not opener:
                    raise FolderUnavailable
                subprocess.run([opener, os.fspath(folder)], check=True, timeout=10,
                               stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except (OSError, Refusal, subprocess.SubprocessError) as error:
        raise FolderUnavailable from error
