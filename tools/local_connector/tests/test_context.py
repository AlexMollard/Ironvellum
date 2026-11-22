from decimal import Decimal
import json

import pytest
from ironvellum_local_connector.contract import MAX_CONTEXT, MOVEMENTS, Refusal, encode
from ironvellum_local_connector.snapshot import Snapshot
from test_stdio import CONTEXT


def test_catalogue_scope():
    assert len(MOVEMENTS) == 584 and sum(row["draftable"] for row in MOVEMENTS.values()) == 452
    assert Snapshot(CONTEXT).sha256 == "e6de41c8ac4a11c1d25b21ece9eac40e761b8e430944887c2c98f276eab6cd3e"


@pytest.mark.parametrize("patch", [
    lambda x: x.update(schemaVersion=True), lambda x: x.update(schemaVersion=Decimal("1.0")),
    lambda x: x.update(exportedAtMs=Decimal("1700001000000.0")),
    lambda x: x["coverage"].update(setsIncluded=1),
    lambda x: x["coverage"].update(workoutsTruncated=2),
    lambda x: x["coverage"].update(setsTruncated=1),
    lambda x: x["workouts"][0]["sets"][0].update(order=2),
    lambda x: x["workouts"][0]["sets"][0].update(health="private"),
    lambda x: x["workouts"][0]["sets"][0].update(exerciseKey="assisted dip"),
    lambda x: x["savedWorkouts"][0]["entries"][0].update(exerciseKey="assisted chin-up"),
    lambda x: x["savedWorkouts"][0].update(id=x["workouts"][0]["id"]),
    lambda x: x["savedWorkouts"][0].update(name="invalid "),
    lambda x: x["progress"].update(externalVolumeKg=98.76543),
    lambda x: x["progress"]["lifts"].reverse(),
    lambda x: x["catalogue"].update(sha256="a"*64),
    lambda x: x["workouts"][0].update(startedAtMs=x["exportedAtMs"]+1),
    lambda x: x["workouts"][0].update(id=x["workouts"][0]["id"]+"\n"),
    lambda x: x["savedWorkouts"][0]["entries"][0].update(loadKg=True),
])
def test_inconsistent_context(patch):
    data = json.loads(CONTEXT)
    patch(data)
    with pytest.raises(Refusal):
        Snapshot(encode(data))


@pytest.mark.parametrize("raw", [b'\xff', b'{"k":0,"\\u006b":1}', b'{"a":"\\ud800"}', b"["*17+b"]"*17,
                                 b'1e-9999', b'1e9999', b'1.'+b'0'*128, b"NaN", b" "*(MAX_CONTEXT+1)])
def test_invalid_json(raw):
    from ironvellum_local_connector.contract import loads
    with pytest.raises(Refusal):
        loads(raw, MAX_CONTEXT)
