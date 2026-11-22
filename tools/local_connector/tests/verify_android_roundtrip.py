"""Exchange real files between the isolated Android fixture and the installed local MCP wheel."""
import argparse
import asyncio
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from unittest.mock import patch
import uuid

from mcp import ClientSession, StdioServerParameters
from mcp.client import stdio

REPO = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO / "tools"))
import device

SERIAL = "emulator-5554"
PACKAGE = "com.ironvellum.app.receiptfixture"
RUNNER = PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"
CASE = "com.ironvellum.app.data.LocalFileRoundtripTest"


def digest(data):
    return hashlib.sha256(data).hexdigest()


async def propose(work, context):
    source = json.loads(context)
    outbox = work / "outbox"
    args = ["-I", "-m", "ironvellum_local_connector", "--context", str(work / "context.json"),
            "--outbox", str(outbox)]
    params = StdioServerParameters(command=sys.executable, args=args, cwd=str(work))
    # SDK 2.3 owns the child. Retain its real process handle to check the observed exit after SDK cleanup.
    spawn = stdio._create_platform_compatible_process
    processes = []

    async def observed_spawn(**kwargs):
        child = await spawn(**kwargs)
        processes.append(child)
        return child

    with patch.object(stdio, "_create_platform_compatible_process", observed_spawn):
        async with stdio.stdio_client(params) as streams, ClientSession(*streams) as client:
            await client.initialize()

            async def call(tool, arguments):
                response = await client.call_tool(tool, arguments)
                assert not response.is_error, tool
                return response.structured_content

            assert len((await client.list_tools()).tools) == 7
            listed = (await call("list_workouts", {}))["workouts"]
            assert len(listed) == 1 and listed[0]["id"] == source["workouts"][0]["id"]
            workout = (await call("get_workout", {"id": listed[0]["id"]}))["workout"]
            assert workout["sets"] == source["workouts"][0]["sets"]
            assert (await call("get_progress", {}))["progress"]["completedReps"] == 8
            assert any(row["name"] == "Offline practice" for row in
                       (await call("list_saved_workouts", {}))["savedWorkouts"])
            bench = next(row for row in (await call("search_movements", {"query": "bench press"}))["movements"]
                         if row["key"] == "bench press")
            assert bench["draftable"]
            draft = {"schemaVersion": 1, "name": "Next practice", "warmup": "AUTO", "cooldown": "NONE",
                     "entries": [{"exerciseKey": bench["key"], "sets": 3, "reps": 8, "loadKg": 12.5}]}
            arguments = {"draft": draft, "idempotencyKey": "android-roundtrip"}
            created = await call("create_workout_draft", arguments)
            proposal = created["proposal"]
            assert (proposal["datasetId"], proposal["snapshotId"], proposal["contextSha256"]) == (
                source["datasetId"], source["snapshotId"], digest(context))
            path = outbox / (str(uuid.UUID(proposal["draftId"])) + ".json")
            assert created["filename"] == path.name
            raw = path.read_bytes()
            assert json.loads(raw) == proposal
            retry = await call("create_workout_draft", arguments)
            assert retry["proposal"] == proposal and retry["filename"] == path.name and raw == path.read_bytes()
            assert (await call("get_workout_draft", {"draftId": proposal["draftId"]}))["proposal"] == proposal
    assert len(processes) == 1 and processes[0].returncode == 0, "Expected ordinary connector exit"
    return raw


def verify(work):
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk and os.name == "nt":
        sdk = Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk"
    if not sdk:
        raise ValueError("Set ANDROID_HOME")
    sdk = Path(sdk)
    exe = ".exe" if os.name == "nt" else ""
    adb = sdk / "platform-tools" / ("adb" + exe)
    aapt = max((sdk / "build-tools").glob("*/aapt2" + exe), key=lambda f: f.stat().st_mtime)
    run_id = str(uuid.uuid4())

    def device_call(*args):
        return subprocess.check_output([str(adb), "-s", SERIAL, *args], timeout=90)

    def instrument(phase, key, proposal=None):
        args = ["shell", "am", "instrument", "-w", "-r", "-e", "class", CASE, "-e", "localFileId", run_id,
                "-e", "localFilePhase", phase]
        if proposal:
            assert len(proposal) <= 2000
            args.extend(["-e", "localProposal", base64.urlsafe_b64encode(proposal).decode()])
        output = device_call(*args, RUNNER).decode()
        (work / (phase + ".log")).write_text(output, encoding="utf-8")
        hashes = re.findall(r"^INSTRUMENTATION_STATUS: " + key + r"=([0-9a-f]{64})\s*$", output, re.M)
        assert len(hashes) == 1 and "OK (1 test)" in output and "INSTRUMENTATION_CODE: -1" in output, output[-2400:]
        return hashes[0]

    apks = REPO / "app/build/outputs/apk"
    installs = [(apks / "receiptFixture/debug/app-receiptFixture-debug.apk", PACKAGE),
                (apks / "androidTest/receiptFixture/debug/app-receiptFixture-debug-androidTest.apk", PACKAGE + ".test")]
    for apk, package in installs:
        info = subprocess.check_output([str(aapt), "dump", "badging", str(apk)]).decode("utf-8")
        assert re.search(r"^package: name='" + re.escape(package) + "'", info, re.M), apk
    work.mkdir(mode=0o700, parents=False, exist_ok=False)
    with device.emulator_lock("Personal file round trip"):
        assert device_call("shell", "getprop", "ro.kernel.qemu").strip() == b"1"
        for apk, _ in installs:
            assert b"Success" in device_call("install", "-r", "-t", str(apk))
        try:
            exported = instrument("export", "contextSha256")
            source = device_call("exec-out", "run-as", PACKAGE, "cat", f"files/local-roundtrip-{run_id}.json")
            assert digest(source) == exported
            (work / "context.json").write_bytes(source)
            raw = asyncio.run(asyncio.wait_for(propose(work, source), timeout=60))
            saved = instrument("import", "proposalSha256", raw)
            assert saved == digest(raw)
        finally:
            # Clean only this run's generated files, including after a failed PC assertion.
            prefix = "local-roundtrip-" + run_id
            device_call("shell", "run-as", PACKAGE, "rm", "-f", "files/" + prefix + ".json",
                        *["databases/" + prefix + ".db" + suffix for suffix in ("", "-shm", "-wal", "-journal")])
    print(f"Context {len(source)} bytes SHA-256 {exported}\nProposal {len(raw)} bytes SHA-256 {saved}")
    print("Two isolated Android phases, seven installed SDK tool calls and one committed Room receipt passed.")


if __name__ == "__main__":
    if not __debug__:
        raise SystemExit("Verification requires assertions")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path, help="New directory for synthetic files and both Android reports")
    verify(parser.parse_args().output.resolve())
