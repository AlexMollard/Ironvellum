import json
from ironvellum_local_connector.storage import FolderFiles

import pytest

from ironvellum_local_connector.contract import Refusal
from ironvellum_local_connector.settings import ConnectorSettings
from ironvellum_local_connector.setup import configure


def test_only_selected_entry_is_replaced(tmp_path):
    source = tmp_path / "input"
    source.mkdir()
    settings = ConnectorSettings(source, tmp_path / "outbox", tmp_path / "state")
    path = tmp_path / "config" / "settings.json"
    config = tmp_path / "client" / "client.json"
    original = {"theme": "dark", "mcpServers": {"other": {"env": {"keep": "yes"}},
                                                 "ironvellum-personal": {"command": "legacy"}}}
    raw = json.dumps(original).encode()
    with FolderFiles(config.parent) as files:
        files.write(config.name, raw, replace=False)
    with pytest.raises(Refusal, match="client_entry_conflict"):
        configure(path, settings, "claude-desktop", config)
    assert config.read_bytes() == raw
    assert not path.exists()
    result = configure(path, settings, "claude-desktop", config, replace_existing=True)
    assert result["status"] == "awaiting_training"
    assert result["running"] is False
    updated = json.loads(config.read_bytes())
    assert updated["theme"] == "dark"
    assert updated["mcpServers"]["other"] == original["mcpServers"]["other"]
    assert updated["mcpServers"]["ironvellum-personal"]["args"][-1] == str(path)
    backups = list(config.parent.glob("*.bak"))
    assert len(backups) == 1 and backups[0].read_bytes() == raw
    configure(path, settings, "claude-desktop", config)
    assert list(config.parent.glob("*.bak")) == backups


@pytest.mark.parametrize("raw", [b"{", b'{"mcpServers":{},"mcpServers":{}}'])
def test_bad_client_config_untouched(tmp_path, raw):
    config = tmp_path / "client" / "client.json"
    with FolderFiles(config.parent) as files:
        files.write(config.name, raw, replace=False)
    settings = ConnectorSettings(tmp_path / "input", tmp_path / "out", tmp_path / "state")
    with pytest.raises(Refusal, match="invalid_client_config"):
        configure(tmp_path / "settings.json", settings, "claude-desktop", config)
    assert config.read_bytes() == raw

def test_readonly_and_interrupted_client_write(tmp_path, monkeypatch):
    import stat

    config = tmp_path / "client" / "client.json"
    settings = ConnectorSettings(tmp_path / "input", tmp_path / "out", tmp_path / "state")
    path = tmp_path / "config" / "settings.json"
    original = b'{"other":"preserved"}'
    with FolderFiles(config.parent) as files:
        files.write(config.name, original, replace=False)
    config.chmod(stat.S_IREAD)
    try:
        with pytest.raises(Refusal, match="client_config_read_only"):
            configure(path, settings, "claude-desktop", config)
        assert not path.exists()
    finally:
        config.chmod(stat.S_IREAD | stat.S_IWRITE)
    write = FolderFiles.write
    def interrupted(self, name, data, *, replace):
        if name == config.name:
            raise OSError("interrupted")
        return write(self, name, data, replace=replace)
    monkeypatch.setattr(FolderFiles, "write", interrupted)
    with pytest.raises(OSError):
        configure(path, settings, "claude-desktop", config)
    assert config.read_bytes() == original
    [backup] = config.parent.glob("*.bak")
    assert backup.read_bytes() == original
    monkeypatch.setattr(FolderFiles, "write", write)
    assert configure(path, settings, "claude-desktop", config)["clientConfigChanged"]
    assert list(config.parent.glob("*.bak")) == [backup]


def test_cli_private_fixture_and_rejected_source(tmp_path):
    import subprocess
    import sys

    path = tmp_path / "config" / "settings.json"
    base = [sys.executable, "-I", "-m", "ironvellum_local_connector"]
    common = ["--settings", str(path), "--json"]
    setup = subprocess.run(base + ["setup", *common, "--input-directory", str(tmp_path / "in"),
                           "--state-directory", str(tmp_path / "state"), "--outbox-directory",
                           str(tmp_path / "out"), "--client", "config-only"], capture_output=True, check=True)
    result = json.loads(setup.stdout)
    assert result["status"] == "awaiting_training" and result["configured"] and not result["clientVerified"]
    assert result["fragment"]["mcpServers"]["ironvellum-personal"]["args"][-1] == str(path)
    (tmp_path / "in" / "bad.json").write_bytes(b'{"secret":"CANARY"')
    status = subprocess.run(base + ["status", *common], capture_output=True, check=True)
    assert json.loads(status.stdout)["code"] == "invalid_data" and b"CANARY" not in status.stdout + status.stderr
    choose = subprocess.run(base + ["select-source", *common, "--dataset-id", "absent"], capture_output=True)
    assert choose.returncode == 2 and json.loads(choose.stdout)["code"] == "dataset_unavailable"

def test_cli_argument_error_is_machine_readable_and_redacted():
    import subprocess
    import sys
    result = subprocess.run([sys.executable, "-I", "-m", "ironvellum_local_connector", "status",
                             "--settings", "unused", "--unknown", "CANARY", "--json"], capture_output=True)
    assert result.returncode == 2 and json.loads(result.stdout)["code"] == "invalid_arguments"
    assert b"CANARY" not in result.stdout + result.stderr
