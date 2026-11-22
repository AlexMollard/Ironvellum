"""Validate the cross-record invariants of a complete v2 training file."""
from collections import Counter
from decimal import Decimal, localcontext
import math

from .contract import MOVEMENTS, OPAQUE_ID_V2, require
from .set_scheme import KOTLIN_SPACE, check_scheme


ASSISTED = {"assisted pull-up", "assisted chin-up", "assisted dip"}
TARGET_UNITS = {
    "REPS": "REPS", "HOLD": "SECONDS", "DURATION": "MINUTES",
    "DISTANCE_TIME": "UNUSED", "ATTEMPTS_GRADE": "ATTEMPTS",
}


def load_semantics(name):
    return "ASSISTANCE" if name.strip(KOTLIN_SPACE).lower() in ASSISTED else "EXTERNAL"


def _indexed(rows, key):
    identifiers = [row[key] for row in rows]
    require(identifiers == sorted(set(identifiers)))
    return dict(zip(identifiers, rows))


def _movements(rows):
    by_key = _indexed(rows, "key")
    live_names = Counter(m["name"].lower().strip(KOTLIN_SPACE) for m in rows if m["source"] != "HISTORICAL")
    for key, movement in by_key.items():
        source = movement["source"]
        if source == "HISTORICAL":
            require(OPAQUE_ID_V2.fullmatch(key) and movement["custom"] is None
                    and movement["loadSemantics"] == "UNKNOWN")
            continue
        require(movement["metric"] is not None and movement["custom"] is not None
                and movement["loadSemantics"] == load_semantics(movement["name"]))
        if source == "LOCAL":
            require(OPAQUE_ID_V2.fullmatch(key))
        else:
            require(key in MOVEMENTS and not movement["custom"] and live_names[key] == 1
                    and key == movement["name"].lower().strip(KOTLIN_SPACE)
                    and movement["metric"] == MOVEMENTS[key]["metric"])
    return by_key


def _target(target):
    require(target["unit"] == TARGET_UNITS[target["metric"]])
    empty = "weightKg" if target["metric"] == "DISTANCE_TIME" else "distanceKm"
    require(target[empty] is None)


def _rows(rows, order_key):
    previous = None
    groups = set()
    for i, row in enumerate(rows):
        order = order_key(row)
        require(row["order"] == i and (previous is None or previous <= order))
        previous = order
        group = row["superset"]
        if group is not None and group not in groups:
            require(group == len(groups))
            groups.add(group)


def _preparation_progress(prep):
    steps, progress = prep["steps"], prep["progress"]
    prefix = int(prep["legacyStepCount"])
    require(prefix <= len(steps))
    active = prefix if progress["legacyOnly"] else len(steps)

    def valid(step, round_, repeat):
        return (step < active and round_ < steps[step]["rounds"]
                and repeat < (1 if steps[step]["repeat"] == "ONCE" else 2))

    cursor = (int(progress["stepIndex"]), int(progress["roundIndex"]), int(progress["repeatIndex"]))
    require(cursor == (active, 0, 0) or valid(*cursor))
    done, skipped = progress["completedUnitKeys"], progress["skippedUnitKeys"]
    for keys in (done, skipped):
        require(keys == sorted(keys))
        for key in keys:
            # Both indices are signed-32-bit nonnegative integers and repeat is one digit.
            require(len(key) <= 23 and valid(*map(int, key.split(":"))))
    require(set(done).isdisjoint(skipped))


def validate_references(doc):
    movements = _movements(doc["movements"])
    routines = _indexed(doc["routines"], "id")
    public_ids = [row["id"] for row in doc["workouts"] + doc["savedWorkouts"]]
    public_ids.extend(routines)
    public_ids.extend(key for key, m in movements.items() if m["source"] != "CATALOGUE")
    all_ids = set(public_ids)
    require(len(all_ids) == len(public_ids))
    used_movements, used_routines, historical_steps = set(), set(), set()
    missing_routines = {}

    def move(key, frozen=False):
        require(key in movements)
        row = movements[key]
        if row["source"] == "HISTORICAL":
            require(frozen and key not in historical_steps)
            historical_steps.add(key)
        used_movements.add(key)
        return row

    def steps(rows, frozen=False):
        for step in rows:
            require(step["name"].strip(KOTLIN_SPACE))
            movement = move(step["exerciseKey"], frozen)
            legacy = step["legacyTarget"]
            if legacy is not None:
                _target(legacy)
            if movement["source"] == "HISTORICAL":
                require(movement["name"] == step["name"] and movement["metric"] == (legacy and legacy["metric"]))

    def routine(key, kind, frozen=False):
        if key in routines:
            require(routines[key]["kind"] == kind)
            used_routines.add(key)
        else:
            require(frozen and key not in all_ids and missing_routines.setdefault(key, kind) == kind)

    for workout in doc["workouts"]:
        _rows(workout["sets"], lambda row: (row["exercisePosition"], row["setIndex"]))
        for row in workout["sets"]:
            move(row["exerciseKey"])
        kinds = [prep["kind"] for prep in workout["preparations"]]
        require(len(kinds) == len(set(kinds)))
        for prep in workout["preparations"]:
            if prep["sourceRoutineId"] is not None:
                routine(prep["sourceRoutineId"], prep["kind"], frozen=True)
            steps(prep["steps"], frozen=True)
            _preparation_progress(prep)

    for preset in doc["savedWorkouts"]:
        for field in ("warmup", "cooldown"):
            choice = preset[field]
            key = choice["routineId"]
            require((choice["mode"] == "SAVED") == (key is not None))
            if key is not None:
                routine(key, field.upper())
        _rows(preset["entries"], lambda row: row["position"])
        for entry in preset["entries"]:
            movement = move(entry["exerciseKey"])
            require(movement["metric"] == entry["target"]["metric"])
            _target(entry["target"])
            check_scheme(entry["schemeRaw"], entry["scheme"])

    require(used_routines == routines.keys())
    for row in routines.values():
        require(row["name"].strip(KOTLIN_SPACE))
        steps(row["steps"])
    require(used_movements == movements.keys())
    return movements


def _totals():
    return dict(completedSets=0, completedReps=0, externalVolumeKg=Decimal(0), holdSeconds=0,
                activitySeconds=0, distanceM=Decimal(0), attempts=0)


def _measurements(row, metric, load_type):
    reps = int(row["recordedReps"])
    duration = row["durationSec"]
    load = row["weightKg"]
    values = {"completedSets": 1}
    if metric == "REPS":
        values["completedReps"] = reps
        if load_type == "EXTERNAL" and load is not None:
            values["externalVolumeKg"] = Decimal(load) * reps
    elif metric == "HOLD":
        values["holdSeconds"] = reps if duration is None else int(duration)
    elif metric == "ATTEMPTS_GRADE":
        values["attempts"] = reps
    else:
        values["activitySeconds"] = int(duration or 0)
        if metric == "DISTANCE_TIME" and row["distanceM"] is not None:
            values["distanceM"] = Decimal(row["distanceM"])
    return values


def _maximum(lift, field, value):
    if value is not None and (lift[field] is None or value > lift[field]):
        lift[field] = value


def _rounded_totals(data):
    result = dict(data, externalVolumeKg=float(data["externalVolumeKg"]), distanceM=float(data["distanceM"]))
    require(math.isfinite(result["externalVolumeKg"]) and math.isfinite(result["distanceM"]))
    return result


def checked_progress(workouts, movements, progress):
    totals, lifts = _totals(), {}
    with localcontext() as ctx:
        # Cover subnormal through maximum binary64 values, 128-character decimal tokens,
        # 32-bit rep counts and the maximum possible terms within the 8 MiB file limit.
        ctx.prec = 800
        for workout in workouts:
            for row in workout["sets"]:
                key = row["exerciseKey"]
                if key not in lifts:
                    lifts[key] = dict(_totals(), exerciseKey=key, maxReps=None,
                                      maxExternalLoadKg=None, maxAssistanceKg=None)
                if not row["done"] or row["kind"] == "WARMUP":
                    continue
                lift, movement = lifts[key], movements[key]
                metric, load_type = movement["metric"], movement["loadSemantics"]
                for field, value in _measurements(row, metric, load_type).items():
                    totals[field] += value
                    lift[field] += value
                if metric == "REPS":
                    _maximum(lift, "maxReps", int(row["recordedReps"]))
                load_field = "maxAssistanceKg" if load_type == "ASSISTANCE" else "maxExternalLoadKg"
                _maximum(lift, load_field, row["weightKg"])
    ranked = sorted(lifts.values(), key=lambda lift: (-lift["completedSets"], lift["exerciseKey"]))
    computed = dict(_rounded_totals(totals), version=2, scope="completed-workouts", completedWorkouts=len(workouts),
                    firstCompletedAtMs=workouts[-1]["completedAtMs"] if workouts else None,
                    lastCompletedAtMs=workouts[0]["completedAtMs"] if workouts else None,
                    lifts=[_rounded_totals(lift) for lift in ranked])
    claimed = dict(_rounded_totals(progress), lifts=[_rounded_totals(lift) for lift in progress["lifts"]])
    require(claimed == computed)
    return computed
