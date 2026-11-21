"""Exchange one real SQL draft and one production Android acknowledgement using disposable state."""

import asyncio
import base64
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from contextlib import closing
from pathlib import Path

import httpx2 as httpx
from test_native_http import call, disposable_network, issue, post_client, register
from test_service import FIX, ROOT

REPO = ROOT.parents[1]
SERIAL = "emulator-5554"
CASE = "com.ironvellum.app.data.cloud.SqlDraftReceiptTest"
PACKAGE = "com.ironvellum.app.receiptfixture.test/androidx.test.runner.AndroidJUnitRunner"
LOCK_LABEL = "SQL to Room receipt"


def wire(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode("utf-8")


def app_rpc(bridge, action, body, actor="owner"):
    bridge.stdin.write(json.dumps({"actor": actor, "action": action, "body": body}) + "\n")
    bridge.stdin.flush()
    return json.loads(bridge.stdout.readline())


async def prepare(service, bridge, token):
    async def tool(name, args):
        return await call(service.origin, token, name, args)

    movements = await tool("search_movements", {"query": "bench press"})
    assert {"key": "bench press", "name": "Bench Press", "metric": "REPS"} in movements["movements"]
    args = FIX["tools"]["create_workout_draft"]["args"]
    created = await tool("create_workout_draft", args)
    assert created == await tool("create_workout_draft", args)
    draft = created["draft"]
    detail = {"p_draft_id": draft["id"]}
    assert app_rpc(bridge, "detail", detail, "other")["error"] == "not_found"
    pending = app_rpc(bridge, "detail", detail)
    assert pending == created and draft["status"] == "pending"
    approval = {**detail, "p_hash": draft["contentHash"], "p_action": "approve"}
    assert app_rpc(bridge, "review", approval, "other")["error"] == "not_found"
    assert app_rpc(bridge, "review", {**approval, "p_hash": "0" * 64})["error"] == "conflict"
    approved = app_rpc(bridge, "review", approval)
    assert approved == {**pending, "draft": {**draft, "status": "approved"}}
    return {
        "owner": app_rpc(bridge, "identity", {})["userId"],
        "backend": "https://fixture.invalid",
        "pending": pending,
        "approved": approved,
    }


def instrument(fixture):
    sys.path.insert(0, str(REPO / "tools"))
    import device

    holder = device.lock_owner(device.LOCK) or {}
    if holder.get("label") != LOCK_LABEL:
        raise RuntimeError("Run the fixture through the shared emulator lock")
    # Fixture contains only the two synthetic envelopes, owner UUID and test backend scope.
    raw = wire(fixture)
    encoded = base64.urlsafe_b64encode(raw).decode("ascii")
    if len(encoded) > 6000 or os.environ.get("ANDROID_SERIAL") != SERIAL:
        raise RuntimeError("Expected a bounded fixture and emulator-5554")
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk and os.name == "nt":
        sdk = Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk"
    adb = shutil.which("adb")
    if not adb and sdk:
        adb = str(Path(sdk) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb"))
    if not adb:
        raise RuntimeError("Set ANDROID_HOME or put adb on PATH")
    for apk in (
        "receiptFixture/debug/app-receiptFixture-debug.apk",
        "androidTest/receiptFixture/debug/app-receiptFixture-debug-androidTest.apk",
    ):
        subprocess.run(
            [adb, "-s", SERIAL, "install", "-r", "-t", str(REPO / "app/build/outputs/apk" / apk)],
            check=True,
            timeout=60,
            stdout=sys.stderr,
        )
    run = subprocess.run(
        [
            adb,
            "-s",
            SERIAL,
            "shell",
            "am",
            "instrument",
            "-w",
            "-r",
            "-e",
            "class",
            CASE,
            "-e",
            "standaloneReceipt",
            "true",
            "-e",
            "sqlFixture",
            encoded,
            PACKAGE,
        ],
        capture_output=True,
        text=True,
        timeout=90,
        check=True,
    )
    reports = re.findall(
        r"^INSTRUMENTATION_STATUS: sqlReceipt=([A-Za-z0-9_=-]+)\s*$", run.stdout, re.MULTILINE
    )
    if len(reports) != 1 or "OK (1 test)" not in run.stdout or "INSTRUMENTATION_CODE: -1" not in run.stdout:
        raise RuntimeError("One completed receipt test and result are required.\n" + run.stdout[-1800:])
    result_raw = base64.urlsafe_b64decode(reports[0])
    result = json.loads(result_raw)
    assert result["fixtureSha256"] == hashlib.sha256(raw).hexdigest()
    print(json.dumps({"result": result, "resultSha256": hashlib.sha256(result_raw).hexdigest()}))


async def verify(work):
    with closing(disposable_network(work, full_manifest=True)) as network:
        service, bridge, _ = next(network)
        async with httpx.AsyncClient(base_url=service.origin, trust_env=False) as client:
            info = await register(client)
            owner = await issue(client, info, scopes=["workoutdrafts:create"])
            other = await issue(client, info, "other.test.session", scopes=["workoutdrafts:create"])
            token = owner["access_token"]
            service.stop()
            service.start()
            fixture = await prepare(service, bridge, token)
            draft = fixture["pending"]["draft"]
            # Hold the shared lock over both installs and the entire instrumentation call.
            worker = await asyncio.to_thread(
                subprocess.run,
                [
                    sys.executable,
                    str(REPO / "tools/device.py"),
                    "lock",
                    "--label",
                    LOCK_LABEL,
                    "--",
                    sys.executable,
                    str(Path(__file__).resolve()),
                    "--instrument",
                ],
                env={**os.environ, "ANDROID_SERIAL": SERIAL},
                input=wire(fixture),
                check=True,
                stdout=subprocess.PIPE,
            )
            exchange = json.loads(worker.stdout.splitlines()[-1])
            result = exchange["result"]
            expected = {"p_draft_id": draft["id"], "p_hash": draft["contentHash"]}
            assert result["approve"] == {**expected, "p_action": "approve"}
            assert result["accept"] == {**expected, "p_action": "accept"}
            assert result["fixtureSha256"] == hashlib.sha256(wire(fixture)).hexdigest()
            accepted = app_rpc(bridge, "review", result["accept"])
            assert accepted == {**fixture["pending"], "draft": {**draft, "status": "accepted"}}
            assert app_rpc(bridge, "review", result["accept"]) == accepted
            key = {"id": draft["id"]}
            assert await call(service.origin, token, "get_workout_draft", key) == accepted
            hidden = await call(service.origin, other["access_token"], "get_workout_draft", key)
            assert hidden["error"] == "not_found"
            assert (await post_client(client, info, "/revoke", {"token": token})).status_code == 200
            assert (
                await client.post("/mcp", headers={"authorization": "Bearer " + token})
            ).status_code == 401
            print("One emulator receipt, idempotent SQL acceptance, owner isolation and revocation passed.")
            print("Fixture SHA-256", result["fixtureSha256"])
            print("Android result SHA-256", exchange["resultSha256"])
            print("SQL content hash", draft["contentHash"])


if __name__ == "__main__":
    if not __debug__:
        raise SystemExit("Verification requires Python assertions.")
    if sys.argv[1:] == ["--instrument"]:
        instrument(json.load(sys.stdin))
    elif not sys.argv[1:]:
        with tempfile.TemporaryDirectory(prefix="ironvellum-receipt-") as directory:
            asyncio.run(verify(Path(directory)))
    else:
        raise SystemExit("Run verify_android_receipt.py without arguments.")
