"""Fixed folder endpoints and atomically stored, explicitly chosen preferences."""
from contextlib import ExitStack
from dataclasses import dataclass
from itertools import combinations
import json
import os
from pathlib import Path
import re

from .contract import loads, require
from .storage import Directory, FolderFiles, selected

SETTINGS_LIMIT = 64 * 1024
FIELDS = {"schemaVersion", "inputDirectory", "outboxDirectory", "stateDirectory",
          "selectedDatasetId", "watchEnabled"}
DATASET_ID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")


@dataclass(frozen=True)
class ConnectorSettings:
    input_directory: Path
    outbox_directory: Path
    state_directory: Path
    selected_dataset_id: str | None = None
    watch_enabled: bool = False

    @property
    def endpoints(self):
        return self.input_directory, self.outbox_directory, self.state_directory


def _validate(value):
    for path in value.endpoints:
        require(isinstance(path, Path), "invalid_settings")
        selected(path)
    for a, b in combinations(value.endpoints, 2):
        require(not (a.is_relative_to(b) or b.is_relative_to(a)), "overlapping_paths")
    dataset = value.selected_dataset_id
    require(dataset is None or isinstance(dataset, str) and DATASET_ID.fullmatch(dataset), "invalid_settings")
    require(type(value.watch_enabled) is bool, "invalid_settings")


def endpoint_identities(value, *, create=True):
    _validate(value)
    if os.name != "nt":
        from .posix_files import check_mounts
        for path in value.endpoints:
            check_mounts(path, path)
    with ExitStack() as stack:
        dirs = [Directory(path, stack, private=index != 0, create=create) for index, path in enumerate(value.endpoints)]
        for a, b in combinations(dirs, 2):
            require(a.identity != b.identity and a.identity not in b.ancestors
                    and b.identity not in a.ancestors, "overlapping_paths")
        return tuple(directory.identity for directory in dirs)


def _settings_location(path, value):
    require(all(not path.is_relative_to(directory) for directory in (value.input_directory, value.outbox_directory)),
            "overlapping_paths")


def load_settings(path: Path) -> ConnectorSettings:
    path = selected(path)
    with FolderFiles(path.parent, create=False) as files:
        return _decode(path, files.read(path.name, SETTINGS_LIMIT))


def _decode(path, raw):
    data = loads(raw, SETTINGS_LIMIT)
    require(isinstance(data, dict) and set(data) == FIELDS
            and type(data["schemaVersion"]) is int and data["schemaVersion"] == 1, "invalid_settings")
    names = ("inputDirectory", "outboxDirectory", "stateDirectory")
    require(all(type(data[k]) is str for k in names), "invalid_settings")
    value = ConnectorSettings(*(Path(data[k]) for k in names), data["selectedDatasetId"], data["watchEnabled"])
    _validate(value)
    _settings_location(path, value)
    return value


def save_settings(path: Path, value: ConnectorSettings) -> None:
    path = selected(path)
    _validate(value)
    _settings_location(path, value)
    with FolderFiles(path.parent) as files:
        with files.serialized():
            try:
                files.read(path.name, SETTINGS_LIMIT)
            except FileNotFoundError:
                pass
            files.write(path.name, _encode(value), replace=True)


def _encode(value):
    endpoint_identities(value)
    data = dict(schemaVersion=1, inputDirectory=str(value.input_directory), outboxDirectory=str(value.outbox_directory),
                stateDirectory=str(value.state_directory), selectedDatasetId=value.selected_dataset_id,
                watchEnabled=value.watch_enabled)
    raw = json.dumps(data, ensure_ascii=False).encode()
    require(len(raw) <= SETTINGS_LIMIT, "file_limit")
    return raw


def update_settings(path, change):
    """Apply a preference update without losing concurrent endpoint or watch changes."""
    path = selected(path)
    with FolderFiles(path.parent) as files:
        with files.serialized():
            value = change(_decode(path, files.read(path.name, SETTINGS_LIMIT)))
            _validate(value)
            _settings_location(path, value)
            files.write(path.name, _encode(value), replace=True)
    return value
