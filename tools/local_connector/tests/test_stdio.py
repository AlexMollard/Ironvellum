"""Public entry point tests. Run with the interpreter that installed the wheel."""
import asyncio
import json
import os
from concurrent.futures import ThreadPoolExecutor
from contextlib import contextmanager
from decimal import Decimal
from hashlib import sha256
from pathlib import Path
import subprocess
import sys

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client
import pytest

FIXTURES = Path(__file__).resolve().parents[1] / "contracts" / "fixtures"
CONTEXT = (FIXTURES / "context.json").read_bytes()
PROPOSAL = json.loads((FIXTURES / "proposal.json").read_bytes())
TOOLS = json.loads((FIXTURES.parent / "tools-v1.json").read_text())
CANARY = "PRIVATE_REJECTED_DATA"
MODULE = "ironvellum_local_connector"
CONNECTOR_PYTHON = os.environ.get("IRONVELLUM_LOCAL_TEST_PYTHON", sys.executable)


@pytest.fixture
def paths(tmp_path):
    context = tmp_path / "私 training.json"
    context.write_bytes(CONTEXT)
    outbox = tmp_path / "café outbox"
    if os.name == "nt":
        import win32file
        from ironvellum_local_connector.windows_files import _private_attributes
        win32file.CreateDirectory(str(outbox), _private_attributes())
    else:
        outbox.mkdir(mode=0o700)
    return context, outbox


def args(paths):
    return ["-m", MODULE, "--context", str(paths[0]), "--outbox", str(paths[1])]


def test_official_client_all_seven(paths, tmp_path):
    async def run():
        params = StdioServerParameters(command=CONNECTOR_PYTHON, args=args(paths),
                                      env={"PYTHONPATH": str(Path(__file__).parent / "guard"),
                                           "NETWORK_GUARD_LOG": str(tmp_path / "events")})
        with (tmp_path / "stderr").open("w") as errors:
            async with stdio_client(params, errlog=errors) as streams, ClientSession(*streams) as client:
                await client.initialize()
                assert {t.name for t in (await client.list_tools()).tools} == set(TOOLS)

                async def call(name, arguments):
                    result = await client.call_tool(name, arguments)
                    assert not result.is_error, result
                    assert result.structured_content["snapshot"]["coverage"]["setsIncluded"] == 2
                    return result.structured_content

                paths[0].write_bytes(b"replacement")
                summary = (await call("list_workouts", {}))["workouts"][0]
                workout = (await call("get_workout", {"id": summary["id"]}))["workout"]
                assert len(workout["sets"]) == summary["setCount"] == 2
                assert (await call("get_progress", {}))["progress"]["completedReps"] == 13
                assert (await call("list_saved_workouts", {}))["savedWorkouts"][0]["name"] == PROPOSAL["draft"]["name"]
                matches = (await call("search_movements", {"query": "assisted dip"}))["movements"]
                assert matches == [{"key": "assisted dip", "name": "Assisted Dip", "metric": "REPS", "draftable": False}]
                created = await call("create_workout_draft", {"idempotencyKey": "sdk", "draft": PROPOSAL["draft"]})
                assert created["status"] == "proposed"
                draft = created["proposal"]
                assert draft["contextSha256"] == sha256(CONTEXT).hexdigest()
                assert json.loads((paths[1] / created["filename"]).read_bytes()) == draft
                assert (await call("get_workout_draft", {"draftId": draft["draftId"]}))["proposal"] == draft
                invalid = await client.call_tool("create_workout_draft", {"private": CANARY})
                assert invalid.is_error and CANARY not in str(invalid)
    asyncio.run(asyncio.wait_for(run(), 40))
    assert CANARY not in (tmp_path / "stderr").read_text()
    assert (tmp_path / "events").read_text().splitlines() == ["active"]


def bounded_decimal(token):
    assert len(token) <= 128, "Wire result contains an oversized numeric token"
    return Decimal(token)


@contextmanager
def raw_session(paths, *, reply_timeout=8):
    proc = subprocess.Popen([CONNECTOR_PYTHON, "-I", *args(paths)], stdin=subprocess.PIPE,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    reader = ThreadPoolExecutor(max_workers=1)
    killed_on_timeout = False
    try:
        def send(obj):
            data = obj if isinstance(obj, bytes) else json.dumps(obj).encode()
            proc.stdin.write(data + b"\n")
            proc.stdin.flush()

        def read():
            nonlocal killed_on_timeout
            try:
                line = reader.submit(proc.stdout.readline).result(timeout=reply_timeout)
            except TimeoutError:
                killed_on_timeout = True
                proc.kill()
                proc.wait(timeout=5)
                raise AssertionError(f"Connector did not reply within {reply_timeout} seconds") from None
            assert line, "Connector closed stdout before replying"
            return json.loads(line, parse_float=bounded_decimal)

        send({"jsonrpc": "2.0", "id": 0, "method": "initialize", "params": {
            "protocolVersion": "2025-11-25", "capabilities": {}, "clientInfo": {"name": "test", "version": "1"}}})
        assert "result" in read()
        send({"jsonrpc": "2.0", "method": "notifications/initialized"})

        def call(name, arguments):
            raw = json.dumps({"jsonrpc": "2.0", "id": 1, "method": "tools/call",
                              "params": {"name": name, "arguments": arguments}})
            # Replace quoted markers only in synthetic frames, preserving numeric spelling.
            import re
            send(re.sub(r'"NUM:([^"]+)"', r'\1', raw).encode())
            return read()["result"]
        yield call
    finally:
        try:
            proc.stdin.close()
            status = proc.wait(timeout=10)
            if not killed_on_timeout:
                assert status == 0, f"Connector exited with status {status}"
            assert CANARY.encode() not in proc.stderr.read()
        finally:
            proc.kill()
            proc.wait(timeout=5)
            reader.shutdown()


def test_exact_decimal_and_restart(paths):
    def body(load, sets="3.0"):
        draft = json.loads(json.dumps(PROPOSAL["draft"]))
        draft["entries"][0].update(loadKg="NUM:" + load, sets="NUM:" + sets)
        return {"idempotencyKey": "exact", "draft": draft}

    exact = "40.00000000000000000001"
    with raw_session(paths) as call:
        assert call("create_workout_draft", body(exact, "1.00000000000000000001"))["isError"]
        result = call("create_workout_draft", body(exact))["structuredContent"]
        assert result["proposal"]["draft"]["entries"][0]["loadKg"] == Decimal(exact)
        filename = result["filename"]
    original = (paths[1] / filename).read_bytes()
    with raw_session(paths) as call:
        assert call("create_workout_draft", body(exact + "0", "3e0"))["structuredContent"]["filename"] == filename
        assert call("create_workout_draft", body("40.00000000000000000002"))["isError"]
    assert (paths[1] / filename).read_bytes() == original
    assert json.loads(original, parse_float=Decimal)["draft"]["entries"][0]["loadKg"] == Decimal(exact)


def test_maximum_numeric_token_in_proposal_and_restart(paths):
    token = "1" * 123 + "e-128"
    assert len(token) == 128 and len(str(Decimal(token))) == 130
    draft = json.loads(json.dumps(PROPOSAL["draft"]))
    draft["entries"][0]["loadKg"] = "NUM:" + token
    arguments = {"idempotencyKey": "max-token", "draft": draft}
    with raw_session(paths) as call:
        result = call("create_workout_draft", arguments)["structuredContent"]
        assert result["proposal"]["draft"]["entries"][0]["loadKg"] == Decimal(token)
    target = paths[1] / result["filename"]
    original = target.read_bytes()
    # The shipped Android contract caps every lexical numeric token, including persisted ones.
    assert json.loads(original, parse_float=bounded_decimal)["draft"]["entries"][0]["loadKg"] == Decimal(token)
    with raw_session(paths) as call:
        again = call("create_workout_draft", arguments)["structuredContent"]
        assert again["proposal"] == result["proposal"]
        lookup = call("get_workout_draft", {"draftId": result["proposal"]["draftId"]})["structuredContent"]
        assert lookup["proposal"] == result["proposal"]
    assert target.read_bytes() == original


def test_maximum_numeric_token_in_workout_result(paths):
    token = "1" * 123 + "e-128"
    volume = str(float(str(int("1" * 123) * 8) + "e-128")).encode()
    paths[0].write_bytes(CONTEXT.replace(b"12.3456789", token.encode()).replace(b"98.7654312", volume))
    with raw_session(paths) as call:
        wid = json.loads(CONTEXT)["workouts"][0]["id"]
        result = call("get_workout", {"id": wid})["structuredContent"]
        assert result["workout"]["sets"][0]["loadKg"] == Decimal(token)


def test_129_character_numeric_token_rejected(paths):
    token = b"1" * 124 + b"e-128"
    raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"arguments":{"n":' + token + b'}}}\n'
    p = subprocess.run([CONNECTOR_PYTHON, "-I", *args(paths)], input=raw, capture_output=True, timeout=12)
    assert len(token) == 129 and p.returncode == 0 and not p.stdout and p.stderr.strip() == b"invalid_frame"


def test_silent_peer_deadline(paths, monkeypatch):
    monkeypatch.setattr(sys.modules[__name__], "args", lambda _: ["-c", "import time; time.sleep(30)"])
    with pytest.raises(AssertionError, match="did not reply"), raw_session(paths, reply_timeout=0.5):
        pytest.fail("Unresponsive peer completed initialization")


def test_raw_session_checks_exit_after_valid_reply(paths, monkeypatch):
    child = """
import sys
sys.stdin.readline()
print('{"result": {}}', flush=True)
sys.stdin.readline()
sys.stdin.readline()
print('{"result": {"ok": true}}', flush=True)
sys.exit(23)
"""
    monkeypatch.setattr(sys.modules[__name__], "args", lambda _: ["-c", child])
    children = []
    popen = subprocess.Popen

    def record_child(*args, **kwargs):
        children.append(popen(*args, **kwargs))
        return children[-1]

    monkeypatch.setattr(subprocess, "Popen", record_child)
    with pytest.raises(AssertionError, match="exited with status 23"), raw_session(paths) as call:
        assert call("last_reply", {}) == {"ok": True}
        assert children[0].wait(timeout=5) == 23  # Already exited before helper teardown.


@pytest.mark.parametrize("frame", [b"x" * 65537, b"x" * 65537 + b"\n", b'\xff\n', b'{"a":1,"a":2}\n'])
def test_invalid_frame_exits_cleanly(paths, frame):
    p = subprocess.run([sys.executable, *args(paths)], input=frame, capture_output=True, timeout=12)
    assert p.returncode == 0 and not p.stdout and p.stderr.strip() == b"invalid_frame"
