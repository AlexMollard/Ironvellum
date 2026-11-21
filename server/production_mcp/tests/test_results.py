from copy import deepcopy

import pytest
from mcp.client import Client
from mcp.client.streamable_http import streamable_http_client
from test_service import CANARY, RESOURCE, TOKEN, Gateway, session


@pytest.mark.anyio
@pytest.mark.parametrize("change,expected", [("private", "unavailable"), ("large", "result_too_large")])
async def test_output_nested_allowlist_and_rendered_limit(settings, change, expected):
    class ChangedOutput(Gateway):
        async def gateway(self, operation, args):
            result = await super().gateway(operation, args)
            if operation == "tool.call":
                result = deepcopy(result)
                target = result["lifts"][0]["variations"][0]
                if change == "private":
                    target["peak"]["private"] = CANARY
                else:
                    target["label"] = "x" * 600000
            return result

    async with session(settings, ChangedOutput()) as http:
        http.headers["authorization"] = "Bearer " + TOKEN
        async with Client(streamable_http_client(RESOURCE, http_client=http), mode="2026-07-28") as client:
            reply = await client.call_tool("get_progress", {})
            assert reply.is_error and reply.structured_content["error"] == expected
            assert CANARY not in reply.model_dump_json() and len(reply.model_dump_json()) < 1024
