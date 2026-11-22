"""Install a stdio entry only in an explicitly selected client's configuration."""
from hashlib import sha256
import os
from pathlib import Path
import stat
import sys

from .contract import Refusal, encode, loads, require
from .settings import _settings_location, _validate, endpoint_identities, load_settings, save_settings
from .storage import FolderFiles, selected

NAME = "ironvellum-personal"
GUIDE = "https://github.com/AlexMollard/Ironvellum/blob/main/tools/local_connector/AGENT_SETUP.md"
CONFIG_LIMIT = 4 << 20


def agent_setup_instructions():
    return ("Set up my Ironvellum personal connector using " + GUIDE +
            ". Use the verified source and an isolated Python environment. Ask which local folders and "
            "AI client to use. Preserve my other settings. Verify MCP initialization, tool listing and "
            "read-only training status. Tell me if I need to reload my client or transfer a file.")


def default_client_config():
    if sys.platform == "win32":
        require(os.environ.get("APPDATA"), "client_config_required")
        parent = Path(os.environ["APPDATA"])
    elif sys.platform == "darwin":
        parent = Path.home() / "Library" / "Application Support"
    else:
        parent = Path.home() / ".config"
    return parent / "Claude" / "claude_desktop_config.json"


def _read_optional(files, name):
    try:
        return files.read(name, CONFIG_LIMIT)
    except FileNotFoundError:
        return None


def _client_config(raw):
    if raw is None:
        return {"mcpServers": {}}
    try:
        config = loads(raw, CONFIG_LIMIT, depth_limit=128)
        require(isinstance(config, dict) and isinstance(config.setdefault("mcpServers", {}), dict))
        return config
    except (ValueError, UnicodeError):
        raise Refusal("invalid_client_config") from None


def _prepare(path, value):
    # Both input and settings locations are explicit. Newly created folders are private.
    if not value.input_directory.exists():
        with FolderFiles(value.input_directory):
            pass
    try:
        previous = load_settings(path)
    except FileNotFoundError:
        previous = None
    if previous != value:
        save_settings(path, value)
    endpoint_identities(value)
    require(load_settings(path) == value, "settings_verify_failed")


def configure(path, value, client, client_config=None, *, replace_existing=False):
    from .status import connector_status
    path = selected(path)
    entry = {"command": sys.executable, "args": ["-I", "-m", "ironvellum_local_connector",
                                                 "serve-folder", "--settings", str(path)]}
    _validate(value)
    _settings_location(path, value)
    require(not path.name.startswith(".ironvellum-"), "invalid_settings")
    changed = False
    backup_path = None
    if client == "config-only":
        require(client_config is None and not replace_existing, "invalid_client_option")
        _prepare(path, value)
    else:
        require(client == "claude-desktop", "invalid_client_option")
        target = selected(client_config or default_client_config())
        require(target != path and all(not target.is_relative_to(d) for d in value.endpoints), "overlapping_paths")
        require(not target.name.startswith(".ironvellum-"), "invalid_client_config")
        # Separate lock acquisition from settings writes even if both files use one directory.
        with FolderFiles(target.parent) as files:
            original = _read_optional(files, target.name)
            config = _client_config(original)
            servers = config["mcpServers"]
            changed = servers.get(NAME) != entry
            require(not changed or NAME not in servers or replace_existing, "client_entry_conflict")
            if changed and original is not None:
                with files.dir.open(target.name, "rb") as stream:
                    info = os.fstat(stream.fileno())
                    require(info.st_mode & 0o222 and not getattr(info, "st_file_attributes", 0)
                            & stat.FILE_ATTRIBUTE_READONLY, "client_config_read_only")
            _prepare(path, value)
            if changed:
                with files.serialized():
                    require(_read_optional(files, target.name) == original, "client_config_changed")
                    servers[NAME] = entry
                    updated = encode(config)
                    require(len(updated) <= CONFIG_LIMIT, "file_limit")
                    if original is not None:
                        backup = f"{target.name}.{sha256(original).hexdigest()[:24]}.bak"
                        backup_path = str(target.parent / backup)
                        try:
                            files.write(backup, original, replace=False)
                        except FileExistsError:
                            require(files.read(backup, CONFIG_LIMIT) == original, "client_backup_conflict")
                    files.write(target.name, updated, replace=original is not None)
                    require(files.read(target.name, CONFIG_LIMIT) == updated, "client_config_verify_failed")
    result = connector_status(path)
    result.update(client=client, clientConfigChanged=changed, backupPath=backup_path, fragment={"mcpServers": {NAME: entry}})
    if changed and "reload_client" not in result["nextActions"]:
        result["nextActions"].append("reload_client")
    return result
