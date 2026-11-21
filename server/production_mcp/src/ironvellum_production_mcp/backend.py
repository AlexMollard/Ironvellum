import asyncio
import json

import httpx2 as httpx

from .config import MAX_RESULT

ERRORS = frozenset(
    [
        "invalid_arguments",
        "invalid_operation",
        "conflict",
        "rate_limited",
        "not_found",
        "invalid_request",
        "invalid_target",
        "invalid_scope",
        "invalid_grant",
        "invalid_token",
        "insufficient_scope",
        "unknown_tool",
        "invalid_cursor",
        "invalid_draft",
        "unsupported_movement",
        "result_too_large",
        "unavailable",
        "forbidden",
    ]
)


class Failure(Exception):
    def __init__(self, code):
        self.code = code if isinstance(code, str) and code in ERRORS else "unavailable"
        super().__init__(self.code)


class SupabaseBackend:
    """Only these two RPC destinations accept credentials. Redirects and proxies are disabled."""

    def __init__(self, settings):
        self.settings = settings
        self.client = httpx.AsyncClient(timeout=10, follow_redirects=False, trust_env=False)

    async def close(self):
        await self.client.aclose()

    async def gateway(self, operation, args):
        key = self.settings.gateway_key
        # Legacy role JWTs use Authorization. Opaque Supabase server keys use only apikey.
        headers = {"apikey": key}
        if not key.startswith("sb_secret_"):
            headers["Authorization"] = "Bearer " + key
        return await self._rpc("ironvellum_mcp_gateway", headers, {"p_operation": operation, "p_args": args})

    async def consent(self, bearer, args):
        return await self._rpc(
            "ironvellum_mcp_consent",
            {"apikey": self.settings.public_key, "Authorization": "Bearer " + bearer},
            args,
        )

    async def _rpc(self, rpc, headers, body):
        url = self.settings.backend_origin + "/rest/v1/rpc/" + rpc
        try:
            headers = {**headers, "Accept-Encoding": "identity"}
            async with asyncio.timeout(10), self.client.stream("POST", url, headers=headers, json=body) as r:
                if r.headers.get("content-encoding", "identity").lower() != "identity":
                    raise Failure("unavailable")
                if r.status_code in {401, 403} and rpc == "ironvellum_mcp_consent":
                    raise Failure("forbidden")
                if r.status_code != 200:
                    raise Failure("unavailable")
                data = bytearray()
                async for chunk in r.aiter_bytes(chunk_size=65536):
                    if len(data) + len(chunk) > MAX_RESULT:
                        raise Failure("result_too_large")
                    data.extend(chunk)
                row = json.loads(data)
        except (httpx.HTTPError, TimeoutError, ValueError, RecursionError):
            raise Failure("unavailable") from None
        if (
            not isinstance(row, dict)
            or type(row.get("schemaVersion")) is not int
            or row["schemaVersion"] != 1
        ):
            raise Failure("unavailable")
        if row.get("ok") is not True:
            raise Failure(row.get("error"))
        return row
