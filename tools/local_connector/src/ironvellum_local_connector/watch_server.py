"""MCP tools over an explicitly selected and versioned training source."""
import re

import mcp_types as types
from mcp.server.lowlevel import Server
from mcp.shared.exceptions import MCPError

from . import __version__
from .contract import FILES, TOOLS, TOOLS_V2, Refusal, Validator, _inline, require, resource, validate
from .journal import Journal
from .server import DESCRIPTIONS, PAGE_HELP, dispatch, tool_error, tool_request, tool_result
from .stdio import RESOURCE_ERROR_CODE, RESOURCE_ERRORS


RESOURCE_SCHEME = "ironvellum-draft"
RESOURCE_URI = re.compile(r"ironvellum-draft://([0-9a-f]{64})/([^/]+)")
RESOURCE_TEMPLATE = "ironvellum-draft://{sourceSha256}/{draftId}"
_manifest = resource("tools-watch-v1.json")
SOURCE_ARG = _manifest["sourceArgument"]["name"]
SOURCE_SCHEMA = _inline(_manifest["sourceArgument"]["schema"], {})
SOURCE_TOKEN = Validator(SOURCE_SCHEMA)
DRAFT_ID = Validator(FILES["uuid"])


def _with_token(schema):
    return dict(schema, properties={**schema.get("properties", {}), SOURCE_ARG: SOURCE_SCHEMA},
                required=[*schema.get("required", ()), SOURCE_ARG])


def _watched_tools():
    tools = _inline(_manifest["tools"], _manifest["$defs"])
    for name in _manifest["dataTools"]:
        legacy, extended = TOOLS[name], TOOLS_V2[name]
        v2 = extended["inputSchema"]
        # Ordinary v1/v2 inputs are identical. Some v2 tools add closed view alternatives.
        inputs = [_with_token(schema) for schema in v2.get("anyOf", [v2])]
        outputs = [legacy["outputSchema"], *extended["outputSchema"].get("anyOf", [extended["outputSchema"]])]
        tools[name] = {
            "inputSchema": inputs[0] if len(inputs) == 1 else {"type": "object", "anyOf": inputs},
            "outputSchema": {"type": "object", "anyOf": outputs},
        }
    return tools


TOOLS_WATCH = _watched_tools()
STATUS = "get_training_status"
LIST_DRAFTS = "list_ready_drafts"
EXTRA_INPUTS = {name: Validator(TOOLS_WATCH[name]["inputSchema"]) for name in (STATUS, LIST_DRAFTS)}


def _summary(summary):
    return {
        "datasetId": summary.dataset_id,
        "snapshotId": summary.snapshot_id,
        "exportedAtMs": summary.exported_at_ms,
        SOURCE_ARG: summary.sha256,
        "coverage": {
            "workoutsIncluded": summary.workouts_included,
            "setsIncluded": summary.sets_included,
            "savedWorkoutsIncluded": summary.saved_workouts_included,
        },
    }


def _status(sources, now):
    refresh = sources.refresh()
    active = refresh.active
    if active is not None:
        status = "ready"
        guidance = "Use sourceSha256 for training and draft requests. Keep it unchanged when following a cursor or retrying a draft."
    elif refresh.pending:
        status = "awaiting_selection"
        guidance = "Choose the training dataset in the connector settings, then check status again."
    else:
        status = "awaiting_training"
        guidance = "Place an Ironvellum training export in the connector input folder, then check status again."
    if refresh.reload_required:
        guidance = "Restart the connector after checking its folder settings."
    elif refresh.error_code:
        guidance += " Check the connector status for a rejected export or folder problem."
    return {
        "status": status,
        "snapshot": active.metadata(now) if active is not None else None,
        SOURCE_ARG: active.sha256 if active is not None else None,
        "pending": [_summary(summary) for summary in refresh.pending],
        "refresh": {"scanning": refresh.scanning, "errorCode": refresh.error_code,
                    "reloadRequired": refresh.reload_required},
        "guidance": guidance,
    }


def _list_drafts(journal, args):
    rows, total, next_offset = journal.list_ready(int(args.get("offset", 0)), int(args.get("limit", 20)))
    items = [
        {"name": name, "draftId": identifier, "filename": identifier + ".json",
         "uri": f"{RESOURCE_SCHEME}://{journal.source.sha256}/{identifier}"}
        for identifier, name in rows
    ]
    return {"items": items, "total": total, "nextOffset": next_offset}


def create_watch_server(files, sources, evidence=None):
    descriptions = {
        STATUS: "Check training availability and get sourceSha256 before using training or draft tools.",
        LIST_DRAFTS: "List proposed workout files by untrusted display name, draft ID, filename and resource URI.",
        **DESCRIPTIONS,
    }
    for name, help_text in PAGE_HELP.items():
        descriptions[name] += " For a version 2 export: " + help_text
    for name in descriptions.keys() - {STATUS}:
        descriptions[name] += (
            " Required sourceSha256 comes from get_training_status. "
            "Reuse the same token for cursors and retries. An expired source returns source_expired."
        )

    async def list_tools(ctx, params):
        if evidence:
            evidence('list')
        return types.ListToolsResult(tools=[
            types.Tool(name=name, description=descriptions[name], input_schema=schemas["inputSchema"],
                       output_schema=schemas["outputSchema"]) for name, schemas in TOOLS_WATCH.items()
        ])

    async def call_tool(ctx, params):
        name = params.name
        try:
            args, now = tool_request(params)
            require(name in TOOLS_WATCH, "unknown_tool")
            if name in EXTRA_INPUTS:
                validate(EXTRA_INPUTS[name], args)
            if name == STATUS:
                result = tool_result(_status(sources, now))
                if evidence:
                    evidence('status')
                return result
            require(isinstance(args, dict))
            token = args.get(SOURCE_ARG)
            validate(SOURCE_TOKEN, token)
            source = sources.resolve(token)
            args.pop(SOURCE_ARG)
            journal = Journal(files, source)
            metadata = source.metadata(now)
            result = (_list_drafts(journal, args) if name == LIST_DRAFTS
                      else dispatch(source, journal, name, args, now, metadata, ctx.request_id))
            return tool_result(dict(snapshot=metadata, **result))
        except Refusal as error:
            return tool_error(str(error))
        except Exception:
            return tool_error("storage_unavailable")

    async def list_resources(ctx, params):
        # Discover concrete resources using the source-scoped, bounded list_ready_drafts tool.
        return types.ListResourcesResult(resources=[])

    async def list_resource_templates(ctx, params):
        return types.ListResourceTemplatesResult(resource_templates=[
            types.ResourceTemplate(name="Ironvellum workout draft", uri_template=RESOURCE_TEMPLATE,
                                   mime_type="application/json",
                                   description="Get a concrete URI from list_ready_drafts.")
        ])

    async def read_resource(ctx, params):
        uri = params.uri
        try:
            match = RESOURCE_URI.fullmatch(uri)
            require(match is not None and DRAFT_ID.is_valid(match[2]), "invalid_resource")
            journal = Journal(files, sources.resolve(match[1]))
            raw = journal.read_ready(match[2])
            return types.ReadResourceResult(contents=[
                types.TextResourceContents(uri=uri, mime_type="application/json", text=raw.decode("utf-8"))
            ])
        except Refusal as error:
            code = str(error)
            raise MCPError(RESOURCE_ERROR_CODE, code if code in RESOURCE_ERRORS else "outbox_conflict") from None
        except Exception:
            raise MCPError(RESOURCE_ERROR_CODE, "storage_unavailable") from None

    return Server("ironvellum-personal", version=__version__, on_list_tools=list_tools, on_call_tool=call_tool,
                  on_list_resources=list_resources, on_list_resource_templates=list_resource_templates,
                  on_read_resource=read_resource)
