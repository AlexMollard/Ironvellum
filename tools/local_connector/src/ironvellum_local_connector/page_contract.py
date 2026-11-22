"""Version 2 tool views share their row definitions with the selected file contract."""

COUNT = {"type": "integer", "minimum": 0, "maximum": 2147483647}
CURSOR = {"type": "string", "pattern": r"^[0-9]{1,10}:[0-9a-f]{64}(?![\s\S])"}
LIMIT = {"type": "integer", "minimum": 1, "maximum": 512}

# A view identifies one collection. Its required selector fields identify the parent.
VIEWS = {
    "get_workout": {
        "sets": ("id",), "preparations": ("id",), "preparationSteps": ("id", "kind"),
        "completedUnitKeys": ("id", "kind"), "skippedUnitKeys": ("id", "kind"),
        "routines": ("id",), "routineSteps": ("id", "routineId"),
    },
    "list_saved_workouts": {
        "headers": (), "entries": ("id",), "routines": ("id",), "routineSteps": ("id", "routineId"),
    },
    "get_progress": {"totals": (), "lifts": ()},
}


def closed(properties, required=None):
    return {"type": "object", "properties": properties, "additionalProperties": False,
            "required": list(properties) if required is None else required}


def header(schema, collection, count):
    fields = {k: v for k, v in schema["properties"].items() if k != collection}
    fields[count] = COUNT
    return closed(fields)


def with_views(tools, defs):
    progress = header(defs["prepProgress"], "completedUnitKeys", "completedUnitCount")
    progress = header(progress, "skippedUnitKeys", "skippedUnitCount")
    prep = header(defs["preparation"], "steps", "stepCount")
    prep["properties"]["progress"] = progress
    totals = closed({k: v for k, v in defs["progress"]["properties"].items() if k != "lifts"})
    key_item = defs["prepProgress"]["properties"]["completedUnitKeys"]["items"]
    rows = {
        "sets": defs["set"], "preparations": prep, "preparationSteps": defs["step"],
        "completedUnitKeys": key_item, "skippedUnitKeys": key_item,
        "routines": header(defs["routine"], "steps", "stepCount"), "routineSteps": defs["step"],
        "headers": header(defs["preset"], "entries", "entryCount"), "entries": defs["entry"],
        "totals": totals, "lifts": defs["lift"],
    }
    selectors = {"id": defs["opaqueId"], "routineId": defs["opaqueId"], "kind": {"enum": ["WARMUP", "COOLDOWN"]}}
    for name, views in VIEWS.items():
        ordinary = tools[name]
        inputs, outputs = [ordinary["inputSchema"]], [ordinary["outputSchema"]]
        for view, parent in views.items():
            tag = {"const": view}
            arguments = {"view": tag, **{k: selectors[k] for k in parent}, "cursor": CURSOR,
                         "offset": dict(COUNT, description="Zero-based row, including after an oversized item."),
                         "limit": LIMIT}
            row = rows[view]
            if "properties" in row:
                arguments["fields"] = {"type": "array", "minItems": 1, "uniqueItems": True,
                                       "items": {"enum": list(row["properties"])},
                                       "description": "Return only these fields of each row."}
                row = dict(row, required=[], minProperties=1)
            request = closed(arguments, ["view", *parent])
            request["not"] = {"required": ["cursor", "offset"]}
            inputs.append(request)
            outputs.append(closed({
                "snapshot": ordinary["outputSchema"]["properties"]["snapshot"], "view": tag,
                "items": {"type": "array", "items": row, "maxItems": LIMIT["maximum"]},
                "total": COUNT, "nextCursor": {"anyOf": [CURSOR, {"type": "null"}]},
            }))
        tools[name] = {"inputSchema": {"type": "object", "anyOf": inputs},
                       "outputSchema": {"type": "object", "anyOf": outputs}}
    return tools
