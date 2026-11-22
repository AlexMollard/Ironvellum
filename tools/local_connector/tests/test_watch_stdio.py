"""Exercise the installed folder server through the official SDK over stdio."""
import asyncio
from contextlib import asynccontextmanager
from dataclasses import replace
from hashlib import sha256
import json
import subprocess
from uuid import UUID

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client
from mcp.shared.exceptions import MCPError
import pytest

from ironvellum_local_connector.settings import ConnectorSettings, save_settings
from test_stdio import CONNECTOR_PYTHON, CONTEXT, FIXTURES, MODULE, PROPOSAL, args, paths


@pytest.fixture
def folder(tmp_path):
    settings = ConnectorSettings(tmp_path / "input", tmp_path / "out", tmp_path / "state",
                                 watch_enabled=True)
    settings.input_directory.mkdir()
    config = tmp_path / "config" / "settings.json"
    save_settings(config, settings)
    return config, settings


def export(settings, n, *, legacy=False, other=False, large=False):
    source = json.loads(CONTEXT if legacy else (FIXTURES / "v2/context.json").read_bytes())
    if other:
        source["savedWorkouts"] = []
        source["coverage"]["savedWorkoutsIncluded"] = 0
        source["datasetId"] = str(UUID(int=5432, version=4))
    if large:
        source["workouts"][0]["sets"][0]["modifiers"] = "x" * (1 << 20)
    source.update(snapshotId=str(UUID(int=n, version=4)), exportedAtMs=source["exportedAtMs"] + n)
    raw = json.dumps(source).encode()
    (settings.input_directory / f"{n}.json").write_bytes(raw)
    return sha256(raw).hexdigest(), source


def folder_args(config):
    return ["-I", "-m", MODULE, "serve-folder", "--settings", str(config)]


@asynccontextmanager
async def connect(config):
    params = StdioServerParameters(command=CONNECTOR_PYTHON, args=folder_args(config))
    async with stdio_client(params) as streams, ClientSession(*streams) as session:
        initial = await session.initialize()
        assert initial.capabilities.resources is not None
        yield session


async def call(session, name, **arguments):
    result = await session.call_tool(name, arguments)
    assert not result.is_error, result
    return result.structured_content


async def failure(session, name, code, **arguments):
    result = await session.call_tool(name, arguments)
    assert result.is_error and result.content[0].text == code


async def adopted(session, token):
    async with asyncio.timeout(9):
        while True:
            status = await call(session, "get_training_status")
            if status["sourceSha256"] == token and status["status"] == "ready":
                return status
            await asyncio.sleep(.25)


async def pending_choice(session):
    async with asyncio.timeout(9):
        while True:
            status = await call(session, "get_training_status")
            if status["pending"]:
                return status
            await asyncio.sleep(.25)


def test_startup_and_tool_contract(folder, tmp_path):
    config, settings = folder

    async def run():
        async with connect(config) as session:
            status = await call(session, "get_training_status")
            assert status["status"] == "awaiting_training"
            assert status["snapshot"] is None and status["sourceSha256"] is None
            assert status["guidance"] and str(tmp_path) not in str(status)
            tools = {tool.name: tool for tool in (await session.list_tools()).tools}
            assert set(tools) == {
                "get_training_status", "list_ready_drafts", "list_workouts", "get_workout",
                "get_progress", "list_saved_workouts", "search_movements", "create_workout_draft",
                "get_workout_draft",
            }
            assert tools["get_training_status"].input_schema == {
                "type": "object", "additionalProperties": False}
            template, = (await session.list_resource_templates()).resource_templates
            assert template.uri_template == "ironvellum-draft://{sourceSha256}/{draftId}"
            for name in tools.keys() - {"get_training_status"}:
                assert "sourceSha256" in tools[name].description
                await failure(session, name, "invalid_data")
            await failure(session, "get_training_status", "invalid_data", path=str(tmp_path))
            await failure(session, "get_progress", "invalid_data", sourceSha256="a" * 64 + "\n")
            await failure(session, "get_progress", "source_expired", sourceSha256="0" * 64)
            a, source = export(settings, 1, legacy=True)
            status = await pending_choice(session)
            assert status["status"] == "awaiting_selection"
            assert status["sourceSha256"] is None and status["snapshot"] is None
            assert status["pending"][0]["sourceSha256"] == a
            save_settings(config, replace(settings, selected_dataset_id=source["datasetId"]))
            status = await adopted(session, a)
            assert status["snapshot"]["snapshotId"] == source["snapshotId"]
            await failure(session, "get_progress", "invalid_data", sourceSha256=a, view="totals")
            other, other_source = export(settings, 2, other=True)
            pending = (await pending_choice(session))["pending"]
            assert len(pending) == 1 and pending[0]["sourceSha256"] == other
            assert pending[0]["datasetId"] == other_source["datasetId"]
            assert pending[0]["coverage"] == dict(workoutsIncluded=1, setsIncluded=10, savedWorkoutsIncluded=0)

    asyncio.run(asyncio.wait_for(run(), 45))


def test_refresh_preserves_old_cursor_and_draft_bytes(folder):
    config, settings = folder
    a, original = export(settings, 1, large=True)
    save_settings(config, replace(settings, selected_dataset_id=original["datasetId"]))

    async def run():
        async with connect(config) as session:
            await adopted(session, a)
            workout_id = original["workouts"][0]["id"]
            await failure(session, "get_workout", "result_limit", id=workout_id, sourceSha256=a)
            arguments = {"id": workout_id, "view": "sets", "limit": 1, "fields": ["exerciseKey"]}
            page = await call(session, "get_workout", sourceSha256=a, **arguments)
            assert page["nextCursor"] is not None
            draft_args = {"idempotencyKey": "retry", "draft": PROPOSAL["draft"]}
            draft = await call(session, "create_workout_draft", sourceSha256=a, **draft_args)
            path = settings.outbox_directory / draft["filename"]
            raw = path.read_bytes()
            b, _ = export(settings, 2)
            await adopted(session, b)
            followup = await call(session, "get_workout", sourceSha256=a,
                                  cursor=page["nextCursor"], **arguments)
            assert followup["snapshot"]["snapshotId"] == original["snapshotId"]
            await failure(session, "get_workout", "invalid_cursor", sourceSha256=b,
                          cursor=page["nextCursor"], **arguments)
            c, _ = export(settings, 3)
            await adopted(session, c)
            result = await call(session, "create_workout_draft", sourceSha256=a, **draft_args)
            assert result["proposal"] == draft["proposal"]
            assert result["proposal"]["contextSha256"] == a
            assert result["snapshot"]["snapshotId"] == original["snapshotId"]
            assert path.read_bytes() == raw
            different = await call(session, "create_workout_draft", sourceSha256=c, **draft_args)
            assert different["filename"] != draft["filename"]
            await failure(session, "list_ready_drafts", "invalid_data", sourceSha256=a, limit=21)
            await failure(session, "list_ready_drafts", "invalid_data", sourceSha256=a, offset=-1)
            await failure(session, "list_ready_drafts", "invalid_data", sourceSha256=a, path="draft.json")
            listing = await call(session, "list_ready_drafts", sourceSha256=a)
            item, = listing["items"]
            assert listing["total"] == 1 and listing["nextOffset"] is None
            assert listing["snapshot"]["snapshotId"] == original["snapshotId"]
            draft_id = draft["proposal"]["draftId"]
            assert item == {"name": PROPOSAL["draft"]["name"], "filename": draft["filename"],
                            "draftId": draft_id, "uri": f"ironvellum-draft://{a}/{draft_id}"}
            resource, = (await session.read_resource(item["uri"])).contents
            assert resource.text.encode() == raw
            for uri in (path.as_uri(), item["uri"] + "?x=1", f"ironvellum-draft://{a}/../{draft_id}",
                        f"ironvellum-draft://{c}/{draft_id}", item["uri"] + "%00"):
                with pytest.raises(MCPError):
                    await session.read_resource(uri)
            path.unlink()
            with pytest.raises(MCPError, match="not_found"):
                await session.read_resource(item["uri"])
            assert (await call(session, "list_ready_drafts", sourceSha256=a))["items"] == []
            assert not path.exists()
            path.write_bytes(raw[:-1] + b" ")
            with pytest.raises(MCPError, match="outbox_conflict"):
                await session.read_resource(item["uri"])
            await failure(session, "list_ready_drafts", "outbox_conflict", sourceSha256=a)
            path.write_bytes(raw)
            second = await call(session, "create_workout_draft", sourceSha256=a,
                                idempotencyKey="second", draft={**PROPOSAL["draft"], "name": "Other draft"})
            first_page = await call(session, "list_ready_drafts", sourceSha256=a, limit=1)
            next_page = await call(session, "list_ready_drafts", sourceSha256=a, limit=1,
                                   offset=first_page["nextOffset"])
            assert first_page["nextOffset"] == 1 and next_page["nextOffset"] is None
            assert first_page["total"] == next_page["total"] == 2
            assert [row["draftId"] for row in first_page["items"] + next_page["items"]] == sorted(
                [draft_id, second["proposal"]["draftId"]])
            for n in (4, 5):
                token, _ = export(settings, n)
                await adopted(session, token)
            await failure(session, "get_progress", "source_expired", sourceSha256=a)
            with pytest.raises(MCPError, match="source_expired"):
                await session.read_resource(item["uri"])
            assert path.is_file()

    asyncio.run(asyncio.wait_for(run(), 65))


def test_raw_frames_and_cli_modes(folder, paths):
    config, _ = folder
    initialize = {"jsonrpc": "2.0", "id": 0, "method": "initialize", "params": {
        "protocolVersion": "2025-11-25", "capabilities": {}, "clientInfo": {"name": "test", "version": "1"}}}
    notification = {"jsonrpc": "2.0", "method": "notifications/initialized"}
    discovery = {"jsonrpc": "2.0", "id": 1, "method": "tools/list"}
    oversized = {"jsonrpc": "2.0", "id": 2, "method": "tools/call", "params": {
        "name": "get_training_status", "arguments": {"wire": "x" * (64 << 10)}}}

    def run(cli, requests=()):
        return subprocess.run([CONNECTOR_PYTHON, *cli],
                              input=b"".join(json.dumps(frame).encode() + b"\n" for frame in requests),
                              capture_output=True, timeout=20, check=False)

    # Stop on the oversized input without echoing the submitted arguments.
    result = run(folder_args(config), [initialize, notification, discovery, oversized])
    assert result.returncode == 0 and result.stderr.splitlines() == [b"invalid_frame"]
    lines = result.stdout.splitlines()
    assert all(len(line) <= 1 << 20 for line in lines)
    replies = [json.loads(line) for line in lines]
    assert all(reply["jsonrpc"] == "2.0" and "result" in reply for reply in replies)
    assert {reply["id"] for reply in replies} == {0, 1}
    for cli in (folder_args(config) + args(paths)[2:],
                ["-I", *args(paths), "--settings", str(config)]):
        result = run(cli)
        assert result.returncode == 2 and not result.stdout

    # Fail even at import if immutable mode ever constructs or loads a watcher.
    script = f"""
import runpy, sys
sys.modules[{MODULE + '.watched_source'!r}] = None
sys.argv[1:] = {args(paths)[2:]!r}
runpy.run_module({MODULE!r}, run_name='__main__')
"""
    result = run(["-I", "-c", script], [initialize, notification, discovery])
    assert result.returncode == 0 and not result.stderr
    replies = [json.loads(line) for line in result.stdout.splitlines()]
    assert len(replies) == 2 and all("result" in reply for reply in replies)
    assert "resources" not in replies[0]["result"]["capabilities"]
    assert len(replies[1]["result"]["tools"]) == 7
