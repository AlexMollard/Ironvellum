import json
import multiprocessing
from decimal import Decimal
import os
from pathlib import Path
import subprocess
import sys

import pytest
from ironvellum_local_connector.contract import Refusal
from ironvellum_local_connector.storage import LocalFiles
from ironvellum_local_connector.journal import Journal
from ironvellum_local_connector.snapshot import Snapshot
from test_stdio import CONTEXT, FIXTURES, paths

PROPOSAL = json.loads((FIXTURES / "proposal.json").read_bytes(), parse_float=Decimal)

NOW = 1700001000001


def make(paths, key="same"):
    with LocalFiles(*paths) as files:
        journal = Journal(files, Snapshot(files.context))
        return journal.create(key, PROPOSAL["draft"], NOW)


def _contender(paths, attempting, acquired, release, results):
    from ironvellum_local_connector import storage
    native_lock = storage.lock
    def observed_lock(stream):
        attempting.set()
        native_lock(stream)
        acquired.set()
        if release is not None:
            assert release.wait(15)
    storage.lock = observed_lock
    results.put(make(paths))


def test_competing_processes_and_conflict(paths):
    ctx = multiprocessing.get_context("spawn")
    release, first_attempt, held, second_attempt, acquired = (ctx.Event() for _ in range(5))
    results = ctx.Queue()
    first = ctx.Process(target=_contender, args=(paths, first_attempt, held, release, results))
    second = ctx.Process(target=_contender, args=(paths, second_attempt, acquired, None, results))
    first.start()
    try:
        assert held.wait(10)
        second.start()
        assert second_attempt.wait(10)
        assert not acquired.wait(0.5), "Second creator entered while the first held the lock"
        release.set()
        a, b = results.get(timeout=10), results.get(timeout=10)
        for worker in (first, second):
            worker.join(timeout=5)
            assert worker.exitcode == 0
    finally:
        release.set()
        for worker in (first, second):
            if worker.is_alive():
                worker.kill()
                worker.join(timeout=5)
        results.close()
    assert a == b and len(list(paths[1].glob("*.json"))) == 1
    target = paths[1] / a["filename"]
    target.write_bytes(b"Unrelated")
    with pytest.raises(Refusal):
        make(paths)
    assert target.read_bytes() == b"Unrelated"
    target.unlink()
    assert make(paths) == a
    assert json.loads(target.read_bytes(), parse_float=Decimal) == a["proposal"]


def test_crash_after_journal_commit(paths):
    code = """
from ironvellum_local_connector.storage import LocalFiles
from ironvellum_local_connector.snapshot import Snapshot
from ironvellum_local_connector.journal import Journal
import sys, json, os
with LocalFiles(*sys.argv[1:3]) as files:
    files.publish = lambda *args: os._exit(49)
    Journal(files, Snapshot(files.context)).create('same',json.loads(sys.argv[3]),1700001000001)
"""
    from ironvellum_local_connector.contract import encode
    p = subprocess.run([sys.executable, "-c", code, *map(str, paths), encode(PROPOSAL["draft"]).decode()])
    assert p.returncode == 49 and not list(paths[1].glob("*.json"))
    assert make(paths)["proposal"]["createdAtMs"] == NOW


def test_other_snapshot_hidden_and_capacity(paths):
    from unittest.mock import patch
    first = make(paths)
    with LocalFiles(*paths) as files:
        journal = Journal(files, Snapshot(files.context))
        with patch.object(journal, "CAPACITY", 1):
            assert journal.create("same", PROPOSAL["draft"], NOW) == first
            with pytest.raises(Refusal, match="capacity"):
                journal.create("new", PROPOSAL["draft"], NOW)
        doc = json.loads(files.context)
        doc["snapshotId"] = "ace00000-0000-4000-8000-000000000000"
        other = Journal(files, Snapshot(json.dumps(doc).encode()))
        with pytest.raises(Refusal, match="not_found"):
            other.get(first["proposal"]["draftId"])
        assert other.create("same", PROPOSAL["draft"], NOW)["filename"] != first["filename"]


def test_path_overlap_and_relative(paths):
    inside = paths[1] / "context.json"
    inside.write_bytes(CONTEXT)
    for ctx, out in [(inside, paths[1]), (Path("relative"), paths[1]), (paths[0], paths[0])]:
        with pytest.raises((Refusal, OSError)), LocalFiles(ctx, out):
            pass


@pytest.mark.skipif(os.name != "nt", reason="Windows paths")
def test_windows_ads_unc_devices_junction_and_pinned_directory(paths, tmp_path):
    for bad in [str(paths[0])+":alternate", r"\\server\share\context", r"\\.\NUL", r"\\?\C:\context", str(paths[0].parent/"NUL")]:
        with pytest.raises(Refusal), LocalFiles(bad, paths[1]):
            pass
    alias = tmp_path / "junction"
    subprocess.run(["cmd", "/d", "/c", "mklink", "/J", str(alias), str(paths[1])], check=True, capture_output=True)
    with pytest.raises(Refusal), LocalFiles(paths[0], alias):
        pass
    with LocalFiles(*paths):
        with pytest.raises(OSError):
            paths[1].rename(tmp_path/"moved")


@pytest.mark.skipif(os.name != "nt", reason="Windows reserved components")
@pytest.mark.parametrize("modern_api", [True, False])
def test_windows_reserved_name_api(monkeypatch, paths, modern_api):
    from ironvellum_local_connector.windows_files import validate_path
    calls = []
    def check(name):
        calls.append(name)
        return name == "reserved-by-system"
    if modern_api:
        monkeypatch.setattr(os.path, "isreserved", check, raising=False)
        monkeypatch.setattr(Path, "is_reserved", lambda _: pytest.fail("Used removed pathlib API"), raising=False)
    else:
        monkeypatch.delattr(os.path, "isreserved", raising=False)
        monkeypatch.setattr(Path, "is_reserved", lambda p: check(p.name), raising=False)
    with pytest.raises(Refusal, match="invalid_path"):
        validate_path(paths[0].parent / "reserved-by-system")
    assert calls[-1] == "reserved-by-system"


@pytest.mark.skipif(os.name != "nt", reason="Windows ACL")
def test_windows_refuses_broad_outbox_permissions(paths, tmp_path):
    import win32file
    import win32security as security
    from ironvellum_local_connector.windows_files import _private_attributes
    broad = tmp_path / "shared"
    attributes = _private_attributes()
    descriptor = attributes.SECURITY_DESCRIPTOR
    acl = descriptor.GetSecurityDescriptorDacl()
    acl.AddAccessAllowedAce(security.ACL_REVISION, win32file.FILE_ALL_ACCESS,
                            security.CreateWellKnownSid(security.WinWorldSid))
    descriptor.SetSecurityDescriptorDacl(True, acl, False)
    attributes.SECURITY_DESCRIPTOR = descriptor
    win32file.CreateDirectory(str(broad), attributes)
    with pytest.raises(Refusal, match="private_outbox_required"), LocalFiles(paths[0], broad):
        pass


@pytest.mark.skipif(os.name == "nt", reason="POSIX files")
def test_posix_private_symlink_fifo_and_pinned_directory(paths, tmp_path):
    fifo = tmp_path / "fifo"
    os.mkfifo(fifo)
    alias = tmp_path / "link"
    alias.symlink_to(paths[0])
    dirlink = tmp_path / "linkdir"
    dirlink.symlink_to(paths[1], target_is_directory=True)
    for ctxt, outbox in [(fifo, paths[1]), (alias, paths[1]), (paths[0], dirlink)]:
        with pytest.raises((Refusal, OSError)), LocalFiles(ctxt, outbox):
            pass
    paths[1].chmod(0o755)
    with pytest.raises(Refusal), LocalFiles(*paths):
        pass
    paths[1].chmod(0o700)
    with LocalFiles(*paths) as files:
        moved = tmp_path / "moved"
        paths[1].rename(moved)
        paths[1].mkdir(mode=0o700)
        result = Journal(files, Snapshot(files.context)).create("safe", PROPOSAL["draft"], NOW)
        assert (moved / result["filename"]).is_file()
        assert not list(paths[1].iterdir())


@pytest.mark.skipif(os.name == "nt", reason="POSIX mounts")
def test_remote_mount_is_refused_before_open(paths, monkeypatch):
    from ironvellum_local_connector import posix_files
    monkeypatch.setattr(posix_files, "mounted_paths", lambda: [(paths[0].parent, False)])
    with pytest.raises(Refusal, match="local_volume_required"):
        LocalFiles(*paths)
