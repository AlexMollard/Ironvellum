import asyncio
from copy import deepcopy
from decimal import Decimal
from hashlib import sha256
import io
import json
import sqlite3
import subprocess
import sys
from uuid import UUID, uuid4

import pytest

from ironvellum_local_connector.contract import MAX_FRAME, MAX_RESULT, Refusal, encode
from ironvellum_local_connector.journal import DB_NAME, Journal
from ironvellum_local_connector.snapshot import Snapshot
from ironvellum_local_connector.stdio import BoundedInput, ExactOutput
from ironvellum_local_connector.storage import LocalFiles
from test_stdio import args, CONTEXT, FIXTURES, paths, PROPOSAL, raw_session

NOW = 1700001000001


def test_bounded_read_before_decoder(monkeypatch):
    import ironvellum_local_connector.stdio as transport
    called = []
    original = transport.loads
    def record(data, cap):
        called.append(len(data))
        return original(data, cap)
    monkeypatch.setattr(transport, "loads", record)
    stream = io.BytesIO(b"x" * (MAX_FRAME * 4))
    async def collect():
        return [line async for line in BoundedInput(stream)]
    assert not asyncio.run(collect())
    assert stream.tell() == MAX_FRAME + 1
    assert called == []


def test_oversize_while_writer_still_open(paths):
    with subprocess.Popen([sys.executable, *args(paths)], stdin=subprocess.PIPE,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE) as proc:
        proc.stdin.write(b"x"*(MAX_FRAME+1))
        proc.stdin.flush()
        assert proc.wait(timeout=8) == 0
        assert proc.stderr.read().strip() == b"invalid_frame"
        assert proc.stdout.read() == b""


def test_actual_page_binding_and_limits(paths):
    doc = json.loads(CONTEXT)
    saved = doc["savedWorkouts"]
    saved.append(dict(saved[0], id="local:ff000000-0000-4000-8000-000000000001"))
    doc["coverage"]["savedWorkoutsIncluded"] = 2
    paths[0].write_bytes(encode(doc))
    with raw_session(paths) as call:
        cursor = call("list_saved_workouts", {"limit": 1})["structuredContent"]["nextCursor"]
        assert call("list_saved_workouts", {"cursor": cursor})["structuredContent"]["nextCursor"] is None
        assert call("list_workouts", {"cursor": cursor})["isError"]
        for k, a in [("list_workouts", {"limit": 51}), ("list_saved_workouts", {"limit": True}),
                     ("search_movements", {"query": "", "limit": 21}),
                     ("search_movements", {"query": "x"*129}), ("get_workout", {"id": "../../outside"}),
                     ("get_progress", {"path": "/PRIVATE_REJECTED_DATA"})]:
            reply = call(k, a)
            assert reply["isError"] and "PRIVATE_REJECTED_DATA" not in str(reply)
        assert len(call("search_movements", {"query": ""})["structuredContent"]["movements"]) == 20
    doc["snapshotId"] = str(uuid4())
    paths[0].write_bytes(encode(doc))
    with raw_session(paths) as call:
        assert call("list_saved_workouts", {"cursor": cursor})["isError"]


def test_draft_policy_and_zero_scale_on_raw_wire(paths):
    from ironvellum_local_connector.contract import MOVEMENTS
    with raw_session(paths) as call:
        draft = deepcopy(PROPOSAL["draft"])
        for key in ["assisted dip", "assisted pull-up", "assisted chin-up", "unknown", "plank"]:
            assert not MOVEMENTS.get(key, {}).get("draftable")
            for load in (None, 5):
                draft["entries"][0].update(exerciseKey=key, loadKg=load)
                assert call("create_workout_draft", {"draft": draft, "idempotencyKey": "declined"})["isError"]
        draft["entries"][0].update(exerciseKey="bench press", loadKg="NUM:0e-1000000")
        response = call("create_workout_draft", {"draft": draft, "idempotencyKey": "zero"})["structuredContent"]
        assert response["proposal"]["draft"]["entries"][0]["loadKg"] == 0
        assert len((paths[1] / response["filename"]).read_bytes()) < 1024


def test_maximum_history_scope_and_immutable_return():
    doc = json.loads(CONTEXT)
    w = doc["workouts"][0]
    rows = [dict(order=i, exerciseKey="bench press", reps=1000, loadKg=Decimal("1000")) for i in range(200)]
    doc["workouts"] = [dict(w, id="local:"+str(UUID(int=i, version=4)), sets=rows) for i in range(100)]
    doc["coverage"].update(workoutsIncluded=100, workoutsTruncated=1, setsIncluded=20000, setsTruncated=30)
    doc["progress"].update(completedWorkouts=100, completedSets=20000, completedReps=20000000,
                           externalVolumeKg=20000000000,
                           lifts=[dict(exerciseKey="bench press", completedSets=20000, maxLoadKg=1000, maxReps=1000)])
    source = Snapshot(encode(doc))
    first = source.page("workouts", {"limit": 50})
    last = source.page("workouts", {"cursor": first["nextCursor"], "limit": 50})
    assert len(first["workouts"]) == len(last["workouts"]) == 50 and last["nextCursor"] is None
    source.workout(wid := first["workouts"][0]["id"])["sets"].clear()
    assert len(source.workout(wid)["sets"]) == 200


def test_exact_byte_vector_capacity_and_scope(paths):
    raw = (FIXTURES / "proposal.json").read_bytes()
    assert sha256(raw).hexdigest() == "496d691a0c74620361ebb31016561ed2e1f40ca92fab3b12252e15232cfc61be"
    with LocalFiles(*paths) as files:
        source = Snapshot(CONTEXT)
        journal = Journal(files, source)
        draft = json.loads(raw, parse_float=Decimal)["draft"]
        valid = journal.create("first", draft, NOW)
        # Seed 999 real schema rows in one transaction to exercise the unmodified 1,000 admission limit.
        db = sqlite3.connect(":memory:")
        try:
            db.deserialize((paths[1] / DB_NAME).read_bytes())
            for i in range(998):
                identifier = str(uuid4())
                db.execute("INSERT INTO proposals VALUES (?,?,?,?,?)", (source.dataset, source.snapshot, str(i),
                           identifier, encode(dict(valid["proposal"], draftId=identifier))))
            db.execute("INSERT INTO proposals VALUES (?,?,?,?,?)", (source.dataset, source.snapshot,
                       "vector", PROPOSAL["draftId"], raw))
            db.commit()
            (paths[1] / DB_NAME).write_bytes(db.serialize())
        finally:
            db.close()
        recovered = journal.create("vector", draft, NOW + 90*86400000)
        assert (paths[1] / recovered["filename"]).read_bytes() == raw
        assert journal.get(PROPOSAL["draftId"]) == recovered
        with pytest.raises(Refusal, match="capacity"):
            journal.create("over", draft, NOW)
        different_bytes = Journal(files, Snapshot(CONTEXT + b"\r\n"))
        with pytest.raises(Refusal, match="journal_conflict"):
            different_bytes.get(PROPOSAL["draftId"])


def test_rendered_wire_limit_and_private_errors():
    wire = io.BytesIO()
    writer = ExactOutput(wire)
    frame = json.dumps({"jsonrpc": "2.0", "id": 1, "result": {"structuredContent": {},
                       "content": [{"type": "text", "text": json.dumps({"s": "a"*(MAX_RESULT//2)})}]}})
    asyncio.run(writer.write(frame))
    limited = json.loads(wire.getvalue())["result"]
    assert limited["isError"] and limited["content"][0]["text"] == "result_limit"
    wire.seek(0)
    wire.truncate()
    asyncio.run(writer.write('{"jsonrpc":"2.0","id":1,"error":{"code":-1,"message":"PRIVATE","data":"PRIVATE"}}'))
    assert b"PRIVATE" not in wire.getvalue()


def test_startup_context_redaction(paths):
    paths[0].write_bytes(CONTEXT.replace(b'"setsIncluded":2', b'"setsIncluded":2,"notes":"PRIVATE_REJECTED_DATA"'))
    p = subprocess.run([sys.executable, *args(paths)], input=b"", capture_output=True, timeout=12)
    assert p.returncode == 2 and p.stdout == b"" and p.stderr.strip() == b"invalid_data"


def test_foreign_journal_is_preserved(paths):
    db = paths[1] / DB_NAME
    db.write_bytes(b"precious user file")
    with LocalFiles(*paths) as files, pytest.raises(Refusal, match="outbox_conflict"):
        Journal(files, Snapshot(CONTEXT)).create("first", PROPOSAL["draft"], NOW)
    assert db.read_bytes() == b"precious user file"


def test_progress_rounds_decimal_volume_once():
    doc = json.loads(CONTEXT)
    row = doc["workouts"][0]["sets"][0]
    row.update(reps=3, loadKg=Decimal("0.1"))
    doc["progress"]["lifts"][0].update(maxReps=3, maxLoadKg=Decimal("0.1"))
    doc["progress"].update(completedReps=8, externalVolumeKg=Decimal("0.3"))
    assert Snapshot(encode(doc)).progress()["externalVolumeKg"] == 0.3
    doc["progress"]["externalVolumeKg"] = 3 * 0.1
    with pytest.raises(Refusal):
        Snapshot(encode(doc))
