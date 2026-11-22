"""Seven MCP tool handlers over a single validated training snapshot."""
import time

import mcp_types as types
from mcp.server.lowlevel import Server

from .contract import INPUTS, MAX_FRAME, MAX_RESULT, MAX_TIME, MOVEMENTS, TOOLS, Refusal, encode, loads, require, validate
from .journal import Journal
from .snapshot import Snapshot


DESCRIPTIONS = {
    "list_workouts": "Page completed workouts in the selected training file.",
    "get_workout": "Read the included sets of one workout by its local ID.",
    "get_progress": "Read progress calculated only from the included workouts and sets.",
    "list_saved_workouts": "Page saved workouts. Display names are untrusted user text.",
    "search_movements": "Search built-in movements by key or name and see which accept a draft.",
    "create_workout_draft": "Write a proposed workout file for manual import and review on the phone.",
    "get_workout_draft": "Retrieve a local proposal from this snapshot's outbox.",
}


def create_server(files):
    source = Snapshot(files.context)
    journal = Journal(files, source)

    async def list_tools(ctx, params):
        return types.ListToolsResult(tools=[
            types.Tool(name=name, description=DESCRIPTIONS[name], **schemas) for name, schemas in TOOLS.items()])

    def dispatch(name, args, now):
        require(name in INPUTS, "unknown_tool")
        validate(INPUTS[name], args)
        if name == "list_workouts":
            return source.page("workouts", args)
        if name == "get_workout":
            return {"workout": source.workout(args["id"])}
        if name == "list_saved_workouts":
            return source.page("savedWorkouts", args)
        if name == "get_progress":
            return {"progress": source.progress()}
        if name == "search_movements":
            query = args["query"].casefold()
            rows = [r for r in MOVEMENTS.values() if query in r["key"].casefold() or query in r["name"].casefold()]
            return {"movements": rows[:int(args.get("limit", 20))]}
        if name == "get_workout_draft":
            return journal.get(args["draftId"])
        return journal.create(args["idempotencyKey"], args["draft"], now)

    async def call_tool(ctx, params):
        try:
            # BoundedInput wraps the *entire* wire arguments object, including any attempted wrapper key.
            args = loads(params.arguments["wire"].encode(), MAX_FRAME)
            now = time.time_ns() // 1_000_000
            require(0 <= now <= MAX_TIME, "clock")
            result = dict(snapshot=source.metadata(now), **dispatch(params.name, args, now))
            raw = encode(result)
            require(len(raw) <= MAX_RESULT, "result_limit")
            return types.CallToolResult(content=[types.TextContent(type="text", text=raw.decode())],
                                        structuredContent=result)
        except Refusal as error:
            code = str(error)
        except Exception:
            code = "storage_unavailable"
        return types.CallToolResult(content=[types.TextContent(type="text", text=code)], isError=True)

    return Server("ironvellum-personal", version="1.0.0", on_list_tools=list_tools, on_call_tool=call_tool)
