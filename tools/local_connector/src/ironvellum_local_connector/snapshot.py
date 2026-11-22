"""Read-only view of one validated export. Public results are detached copies."""
from copy import deepcopy
from decimal import Decimal, localcontext
from hashlib import sha256
from itertools import islice

from .complete_context import checked_progress, load_semantics, validate_references
from .pages import bounded_page
from .contract import (CONTEXT_SCHEMA, CONTEXT_SCHEMA_V2, MAX_CONTEXT, MOVEMENTS, Refusal,
                       check_entries, loads, require, validate)


CONTEXT_VERSIONS = {1: CONTEXT_SCHEMA, 2: CONTEXT_SCHEMA_V2}


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


def _header(row, collection, count):
    return {**{k: v for k, v in row.items() if k != collection}, count: len(row[collection])}


def _preparation_header(row):
    result = _header(row, "steps", "stepCount")
    progress = _header(result["progress"], "completedUnitKeys", "completedUnitCount")
    result["progress"] = _header(progress, "skippedUnitKeys", "skippedUnitCount")
    return result


class Snapshot:
    def __init__(self, raw):
        doc = loads(raw, MAX_CONTEXT)
        require(isinstance(doc, dict))
        version = doc.get("schemaVersion")
        require(type(version) is int and version in CONTEXT_VERSIONS)
        validate(CONTEXT_VERSIONS[version], doc)
        workouts, saved = doc["workouts"], doc["savedWorkouts"]
        require(type(doc["exportedAtMs"]) is int)
        _ordered(workouts, lambda w: (-w["completedAtMs"], w["id"]))
        _ordered(saved, lambda w: w["id"])
        require(len({w["id"] for w in workouts + saved}) == len(workouts) + len(saved))
        for w in workouts:
            require(type(w["startedAtMs"]) is int and type(w["completedAtMs"]) is int)
            require(w["startedAtMs"] <= w["completedAtMs"])
        cov = doc["coverage"]
        require(cov["workoutsIncluded"] == len(workouts) and cov["savedWorkoutsIncluded"] == len(saved))
        require(cov["setsIncluded"] == sum(len(w["sets"]) for w in workouts))
        progress = doc["progress"]
        require(all(t is None or type(t) is int for t in [progress["firstCompletedAtMs"], progress["lastCompletedAtMs"]]))
        self.__movements = MOVEMENTS
        self.__routines = {}
        if version == 2:
            registry = validate_references(doc)
            doc["progress"] = checked_progress(workouts, registry, progress)
            self.__movements = self._complete_movements(registry)
            self.__routines = {row["id"]: row for row in doc["routines"]}
        else:
            self._check_legacy(workouts, saved, cov)
            computed = _progress(workouts)
            require(dict(progress, externalVolumeKg=float(progress["externalVolumeKg"])) == computed)
            doc["progress"] = computed
        self.__doc = doc
        self.version = version
        self.sha256 = sha256(raw).hexdigest()
        self.dataset = doc["datasetId"]
        self.snapshot = doc["snapshotId"]

    @staticmethod
    def _check_legacy(workouts, saved, coverage):
        for workout in workouts:
            require(all(row["order"] == i for i, row in enumerate(workout["sets"])))
            check_entries(workout["sets"])
        for preset in saved:
            check_entries(preset["entries"])
        require(not coverage["workoutsTruncated"] or len(workouts) == 100)
        require(not coverage["savedWorkoutsTruncated"] or len(saved) == 50)
        require(not coverage["setsTruncated"] or any(len(w["sets"]) == 200 for w in workouts))
        require(workouts or not coverage["setsUnsupported"])

    @staticmethod
    def _complete_movements(registry):
        rows = {key: dict(row, source="MANIFEST", custom=False, loadSemantics=load_semantics(row["name"]))
                for key, row in MOVEMENTS.items()}
        rows.update({key: dict(row, draftable=row["source"] == "CATALOGUE" and MOVEMENTS[key]["draftable"])
                     for key, row in registry.items()})
        return dict(sorted(rows.items()))

    def metadata(self, now):
        result = {k: deepcopy(self.__doc[k]) for k in ("datasetId", "snapshotId", "exportedAtMs", "coverage")}
        if self.version == 2:
            result["schemaVersion"] = self.version
        return dict(result, ageMs=max(0, now - result["exportedAtMs"]))

    def progress(self):
        return deepcopy(self.__doc["progress"])

    def workout(self, identifier):
        for row in self.__doc["workouts"]:
            if row["id"] == identifier:
                return deepcopy(row)
        raise Refusal("not_found")

    def _referenced_routines(self, identifiers):
        return [deepcopy(self.__routines[key]) for key in sorted(set(identifiers) & self.__routines.keys())]

    def workout_result(self, identifier):
        workout = self.workout(identifier)
        result = {"workout": workout}
        if self.version == 2:
            result["routines"] = self._referenced_routines(prep["sourceRoutineId"] for prep in workout["preparations"])
        return result

    def collection(self, tool, args, metadata, request_id):
        view = args["view"]
        project = lambda row: row
        if tool == "get_progress":
            progress = self.__doc["progress"]
            rows = progress["lifts"] if view == "lifts" else [{k: v for k, v in progress.items() if k != "lifts"}]
        elif view == "headers":
            rows = self.__doc["savedWorkouts"]
            project = lambda row: _header(row, "entries", "entryCount")
        else:
            saved = tool == "list_saved_workouts"
            parents = self.__doc["savedWorkouts" if saved else "workouts"]
            parent = next((row for row in parents if row["id"] == args["id"]), None)
            require(parent is not None, "not_found")
            if view in ("sets", "entries"):
                rows = parent[view]
            elif view == "preparations":
                rows = parent["preparations"]
                project = _preparation_header
            elif view in ("routines", "routineSteps"):
                refs = ({parent[key]["routineId"] for key in ("warmup", "cooldown")} if saved else
                        {prep["sourceRoutineId"] for prep in parent["preparations"]})
                if view == "routines":
                    rows = [self.__routines[key] for key in sorted(refs & self.__routines.keys())]
                    project = lambda row: _header(row, "steps", "stepCount")
                else:
                    key = args["routineId"]
                    require(key in refs and key in self.__routines, "not_found")
                    rows = self.__routines[key]["steps"]
            else:
                prep = next((row for row in parent["preparations"] if row["kind"] == args["kind"]), None)
                require(prep is not None, "not_found")
                rows = prep["steps"] if view == "preparationSteps" else prep["progress"][view]
        return bounded_page(self.sha256, tool, args, metadata, rows, project, request_id)

    def search_movements(self, query, limit):
        query = query.casefold()
        exact = self.__movements.get(query) if self.version == 2 and limit else None
        results = [exact] if exact is not None else []
        matches = (row for row in self.__movements.values() if row is not exact
                   and (query in row["key"].casefold() or query in row["name"].casefold()))
        results.extend(islice(matches, limit - len(results)))
        return deepcopy(results)

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
        result = {kind: deepcopy(page), "nextCursor": cursors[end - 1] if end < len(rows) else None}
        if kind == "savedWorkouts" and self.version == 2:
            result["routines"] = self._referenced_routines(preset[field]["routineId"] for preset in page
                                                          for field in ("warmup", "cooldown"))
        return result
