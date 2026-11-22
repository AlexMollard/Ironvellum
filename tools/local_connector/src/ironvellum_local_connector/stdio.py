"""Keep SDK negotiation, routing and envelope serialization; bridge exact numeric payloads."""
import json
import os
import sys

import anyio
from mcp.server.stdio import stdio_server

from .contract import MAX_FRAME, MAX_RESULT, Refusal, encode, loads, require
from .server import create_server
from .pages import STRUCTURED_PAGE_TEXT


RESOURCE_ERROR_CODE = -32002
RESOURCE_ERRORS = frozenset({
    "invalid_resource", "not_found", "source_expired", "outbox_conflict",
    "journal_conflict", "storage_unavailable",
})


class BoundedInput:
    def __init__(self, stream):
        self.stream = stream

    async def __aiter__(self):
        while True:
            raw = await anyio.to_thread.run_sync(self.stream.readline, MAX_FRAME + 1, abandon_on_cancel=True)
            if not raw:
                return
            try:
                require(len(raw) <= MAX_FRAME, "file_limit")
                envelope = loads(raw, MAX_FRAME)
                if isinstance(envelope, dict) and envelope.get("method") == "tools/call":
                    params = envelope.get("params")
                    if isinstance(params, dict):
                        # SDK JSON-RPC models otherwise round argument numbers to binary64 before dispatch.
                        params["arguments"] = {"wire": encode(params.get("arguments", {})).decode()}
                        yield encode(envelope).decode()
                        continue
                yield raw.decode("utf-8")
            except Refusal:
                sys.stderr.write("invalid_frame\n")
                return


class ExactOutput:
    def __init__(self, stream, *, folder_resources=False, initialized=None):
        self.stream = stream
        self.folder_resources = folder_resources
        self.initialized = initialized

    async def write(self, line):
        envelope = json.loads(line)
        result = envelope.get("result")
        if isinstance(result, dict) and "structuredContent" in result:
            # The SDK renders Decimal as a string. Recover exact numbers from our own typed text result.
            result["structuredContent"] = loads(result["content"][0]["text"].encode(), MAX_RESULT)
        if "error" in envelope:
            error = envelope["error"]
            message = error["message"]
            if not (self.folder_resources and error["code"] == RESOURCE_ERROR_CODE and message in RESOURCE_ERRORS):
                message = "invalid_request"
            envelope["error"] = {"code": error["code"], "message": message}
        raw = encode(envelope)
        if len(raw) > MAX_RESULT and isinstance(result, dict) and "view" in result.get("structuredContent", {}):
            result["content"] = [{"type": "text", "text": STRUCTURED_PAGE_TEXT}]
            raw = encode(envelope)
        if len(raw) > MAX_RESULT:
            raw = encode({"jsonrpc": "2.0", "id": envelope.get("id"),
                          "result": {"isError": True, "content": [{"type": "text", "text": "result_limit"}]}})
        self.stream.write(raw + b"\n")
        if (self.initialized and isinstance(result, dict)
                and {"protocolVersion", "serverInfo", "capabilities"} <= result.keys()):
            self.initialized()

    async def flush(self):
        self.stream.flush()


async def run(files, server_factory=None, *, initialized=None):
    server = (create_server if server_factory is None else server_factory)(files)
    # Explicit SDK stdout skips its fd claim. Keep protocol on a private duplicate and divert incidental prints.
    with os.fdopen(os.dup(1), "wb") as wire:
        os.dup2(2, 1)
        try:
            async with stdio_server(stdin=BoundedInput(sys.stdin.buffer),
                                    stdout=ExactOutput(wire, folder_resources=server_factory is not None, initialized=initialized)) as (reader, writer):
                await server.run(reader, writer, server.create_initialization_options())
        finally:
            os.dup2(wire.fileno(), 1)
