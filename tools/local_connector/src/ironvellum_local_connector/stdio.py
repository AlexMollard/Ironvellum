"""Keep SDK negotiation, routing and envelope serialization; bridge exact numeric payloads."""
import json
import os
import sys

import anyio
from mcp.server.stdio import stdio_server

from .contract import MAX_FRAME, MAX_RESULT, Refusal, encode, loads, require
from .server import create_server
from .pages import STRUCTURED_PAGE_TEXT


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
    def __init__(self, stream):
        self.stream = stream

    async def write(self, line):
        envelope = json.loads(line)
        result = envelope.get("result")
        if isinstance(result, dict) and "structuredContent" in result:
            # The SDK renders Decimal as a string. Recover exact numbers from our own typed text result.
            result["structuredContent"] = loads(result["content"][0]["text"].encode(), MAX_RESULT)
        if "error" in envelope:
            envelope["error"] = {"code": envelope["error"]["code"], "message": "invalid_request"}
        raw = encode(envelope)
        if len(raw) > MAX_RESULT and isinstance(result, dict) and "view" in result.get("structuredContent", {}):
            result["content"] = [{"type": "text", "text": STRUCTURED_PAGE_TEXT}]
            raw = encode(envelope)
        if len(raw) > MAX_RESULT:
            raw = encode({"jsonrpc": "2.0", "id": envelope.get("id"),
                          "result": {"isError": True, "content": [{"type": "text", "text": "result_limit"}]}})
        self.stream.write(raw + b"\n")

    async def flush(self):
        self.stream.flush()


async def run(files):
    server = create_server(files)
    # Explicit SDK stdout skips its fd claim. Keep protocol on a private duplicate and divert incidental prints.
    with os.fdopen(os.dup(1), "wb") as wire:
        os.dup2(2, 1)
        try:
            async with stdio_server(stdin=BoundedInput(sys.stdin.buffer), stdout=ExactOutput(wire)) as (reader, writer):
                await server.run(reader, writer, server.create_initialization_options())
        finally:
            os.dup2(wire.fileno(), 1)
