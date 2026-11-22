"""Read-only view of one validated export. Public results are detached copies."""
from copy import deepcopy
from decimal import Decimal, localcontext
from hashlib import sha256

from .contract import CONTEXT_SCHEMA, MAX_CONTEXT, Refusal, check_entries, loads, require, validate


def _ordered(rows, key):
    keys = [key(row) for row in rows]
    require(keys == sorted(keys))


def _progress(workouts):
    volume = Decimal(0)
    reps = sets = 0
    lifts = {}
    with localcontext() as ctx:
        # 128-digit tokens, down to binary64 subnormals, plus 20,000 terms.
        ctx.prec = 500
        for workout in workouts:
            for row in workout["sets"]:
                key = row["exerciseKey"]
                n, load = row["reps"], row["loadKg"]
                reps += n
                sets += 1
                lift = lifts.setdefault(key, dict(exerciseKey=key, completedSets=0, maxLoadKg=None, maxReps=0))
                lift["completedSets"] += 1
                lift["maxReps"] = max(lift["maxReps"], n)
                if load is not None:
                    volume += Decimal(load) * n
                    if lift["maxLoadKg"] is None or load > lift["maxLoadKg"]:
                        lift["maxLoadKg"] = load
    ranked = sorted(lifts.values(), key=lambda row: (-row["completedSets"], row["exerciseKey"]))
    return dict(version=1, scope="included-workouts", completedWorkouts=len(workouts), completedSets=sets,
                completedReps=reps, externalVolumeKg=float(volume), lifts=ranked[:20],
                liftsTruncated=max(0, len(ranked) - 20),
                firstCompletedAtMs=workouts[-1]["completedAtMs"] if workouts else None,
                lastCompletedAtMs=workouts[0]["completedAtMs"] if workouts else None)


class Snapshot:
    def __init__(self, raw):
        doc = loads(raw, MAX_CONTEXT)
        validate(CONTEXT_SCHEMA, doc)
        workouts, saved = doc["workouts"], doc["savedWorkouts"]
        require(type(doc["schemaVersion"]) is int and type(doc["exportedAtMs"]) is int)
        _ordered(workouts, lambda w: (-w["completedAtMs"], w["id"]))
        _ordered(saved, lambda w: w["id"])
        require(len({w["id"] for w in workouts + saved}) == len(workouts) + len(saved))
        for w in workouts:
            require(type(w["startedAtMs"]) is int and type(w["completedAtMs"]) is int)
            require(w["startedAtMs"] <= w["completedAtMs"])
            require(all(row["order"] == i for i, row in enumerate(w["sets"])))
            check_entries(w["sets"])
        for w in saved:
            check_entries(w["entries"])
        cov = doc["coverage"]
        require(cov["workoutsIncluded"] == len(workouts) and cov["savedWorkoutsIncluded"] == len(saved))
        require(cov["setsIncluded"] == sum(len(w["sets"]) for w in workouts))
        require(not cov["workoutsTruncated"] or len(workouts) == 100)
        require(not cov["savedWorkoutsTruncated"] or len(saved) == 50)
        require(not cov["setsTruncated"] or any(len(w["sets"]) == 200 for w in workouts))
        require(workouts or not cov["setsUnsupported"])
        progress = doc["progress"]
        require(all(t is None or type(t) is int for t in [progress["firstCompletedAtMs"], progress["lastCompletedAtMs"]]))
        computed = _progress(workouts)
        require(dict(progress, externalVolumeKg=float(progress["externalVolumeKg"])) == computed)
        doc["progress"] = computed
        self.__doc = doc
        self.sha256 = sha256(raw).hexdigest()
        self.dataset = doc["datasetId"]
        self.snapshot = doc["snapshotId"]

    def metadata(self, now):
        result = {k: deepcopy(self.__doc[k]) for k in ("datasetId", "snapshotId", "exportedAtMs", "coverage")}
        return dict(result, ageMs=max(0, now - result["exportedAtMs"]))

    def progress(self):
        return deepcopy(self.__doc["progress"])

    def workout(self, identifier):
        for row in self.__doc["workouts"]:
            if row["id"] == identifier:
                return deepcopy(row)
        raise Refusal("not_found")

    def page(self, kind, args):
        rows = self.__doc[kind]
        cursors = [sha256(f"{self.sha256}/{kind}/{r['id']}".encode()).hexdigest() for r in rows[:-1]]
        start = 0
        if "cursor" in args:
            require(args["cursor"] in cursors, "invalid_cursor")
            start = cursors.index(args["cursor"]) + 1
        end = min(len(rows), start + int(args.get("limit", 20)))
        page = rows[start:end]
        if kind == "workouts":
            page = [dict(id=w["id"], startedAtMs=w["startedAtMs"], completedAtMs=w["completedAtMs"],
                         setCount=len(w["sets"])) for w in page]
        return {kind: deepcopy(page), "nextCursor": cursors[end - 1] if end < len(rows) else None}
