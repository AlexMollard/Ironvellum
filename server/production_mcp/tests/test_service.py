"""Public-boundary tests use the pinned SDK and a fixed, deterministic gateway double."""

import base64
import hashlib
import json
from contextlib import asynccontextmanager
from pathlib import Path
from time import time
from urllib.parse import parse_qs, urlsplit
from uuid import uuid4

import httpx2 as httpx
import pytest
from ironvellum_production_mcp.backend import Failure
from ironvellum_production_mcp.config import SCOPES
from ironvellum_production_mcp.server import create_app
from mcp.client import Client
from mcp.client.streamable_http import streamable_http_client

ROOT = Path(__file__).resolve().parents[1]
FIX = json.loads((ROOT / "contracts/fixtures.json").read_text())
ORIGIN = "https://ironvellum.example"
RESOURCE = ORIGIN + "/mcp"
CALLBACK = "https://client.example/callback"
VERIFIER = "v" * 64
CHALLENGE = base64.urlsafe_b64encode(hashlib.sha256(VERIFIER.encode()).digest()).decode().rstrip("=")
HUMAN = "human.test.session"
TOKEN = "a" * 43
CANARY = "private-canary-do-not-return"


class Gateway:
    def __init__(self):
        self.clients = {}
        self.requests = {}
        self.calls = []
        self.decisions = []
        self.fail = {}
        self.access = True

    async def gateway(self, operation, args):
        self.calls.append((operation, args))
        if operation in self.fail:
            raise Failure(self.fail[operation])
        if operation == "configuration.get":
            return FIX["gateway"]["configuration.get"]["result"]
        if operation == "client.register":
            self.clients[args["clientId"]] = args
            return {}
        if operation == "client.get":
            if args["clientId"] not in self.clients:
                raise Failure("not_found")
            return self.clients[args["clientId"]]
        if operation == "authorization.create":
            key = str(uuid4())
            self.requests[key] = {
                **args,
                "requestId": key,
                "clientName": "Example",
                "expiresAtMs": int(time() * 1000) + 600000,
            }
            return {"requestId": key}
        if operation == "authorization.get":
            if args["requestId"] not in self.requests:
                raise Failure("invalid_request")
            return self.requests[args["requestId"]]
        if operation == "access.load":
            if not self.access or args["accessHash"] != hashlib.sha256(TOKEN.encode()).hexdigest():
                raise Failure("invalid_token")
            return {
                "grantId": "grant-test",
                "clientId": "client",
                "scopes": list(SCOPES),
                "resourceUrl": RESOURCE,
                "expiresAtMs": int(time() * 1000) + 600000,
            }
        if operation == "tool.call":
            return FIX["tools"][args["tool"]]["result"]
        if operation == "token.revoke":
            return {}
        raise AssertionError(operation)

    async def consent(self, bearer, args):
        self.decisions.append((bearer, args))
        return {"approved": args["p_approved"]}


@asynccontextmanager
async def session(settings, gateway):
    app = create_app(settings, gateway)
    async with (
        app.router.lifespan_context(app),
        httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url=ORIGIN) as client,
    ):
        yield client


async def register(client, method="none"):
    response = await client.post(
        "/register",
        json={"client_name": "Example", "redirect_uris": [CALLBACK], "token_endpoint_auth_method": method},
    )
    assert response.status_code == 201, response.text
    assert response.headers["cache-control"] == "no-store"
    return response.json()


def authorize_params(client_id):
    return {
        "client_id": client_id,
        "response_type": "code",
        "code_challenge": CHALLENGE,
        "code_challenge_method": "S256",
        "redirect_uri": CALLBACK,
        "scope": " ".join(SCOPES),
        "state": "return state",
        "resource": RESOURCE,
    }


async def begin(client, client_id):
    response = await client.get("/authorize", params=authorize_params(client_id))
    assert response.status_code == 302
    request_id = urlsplit(response.headers["location"]).path.rsplit("/", 1)[1]
    path = "/consent/api/" + request_id
    view = await client.get(path)
    assert view.status_code == 200, view.text
    return path, view.json()


async def decide(client, path, view, human=HUMAN, scopes=None, approved=True):
    return await client.post(
        path,
        json={
            "approved": approved,
            "scopes": scopes if scopes is not None else list(SCOPES),
            "csrf": view["csrf"],
        },
        headers={"origin": ORIGIN, "authorization": "Bearer " + human},
    )


@pytest.mark.anyio
async def test_discovery_and_challenge(settings):
    async with session(settings, Gateway()) as c:
        for headers in [{}, {"authorization": "Bearer " + HUMAN}]:
            response = await c.post("/mcp", headers=headers)
            assert response.status_code == 401
            assert "/.well-known/oauth-protected-resource/mcp" in response.headers["www-authenticate"]
        meta = (await c.get("/.well-known/oauth-authorization-server")).json()
        assert meta["issuer"] == ORIGIN
        assert set(meta["token_endpoint_auth_methods_supported"]) == {
            "none",
            "client_secret_post",
            "client_secret_basic",
        }
        meta = (await c.get("/.well-known/oauth-protected-resource/mcp")).json()
        assert meta["resource"] == RESOURCE and set(meta["scopes_supported"]) == set(SCOPES)


@pytest.mark.anyio
@pytest.mark.parametrize("mode", ["2026-07-28", "legacy"])
async def test_sdk_calls_exactly_seven_tools(settings, mode):
    db = Gateway()
    async with session(settings, db) as c:
        c.headers["authorization"] = "Bearer " + TOKEN
        async with Client(streamable_http_client(RESOURCE, http_client=c), mode=mode) as client:
            assert client.protocol_version == ("2025-11-25" if mode == "legacy" else mode)
            tools = (await client.list_tools()).tools
            assert {tool.name for tool in tools} == set(FIX["tools"])
            for tool in tools:
                assert tool.input_schema["additionalProperties"] is False
                data = FIX["tools"][tool.name]
                result = await client.call_tool(tool.name, data["args"])
                assert result.structured_content == data["result"]
            for args in [{"limit": "4"}, {"owner": CANARY}, {"limit": True}]:
                result = await client.call_tool("list_workouts", args)
                assert result.is_error and result.structured_content["error"] == "invalid_arguments"
                assert CANARY not in result.model_dump_json()
            # The original nested object is validated before any value can be coerced or dropped.
            create = FIX["tools"]["create_workout_draft"]["args"]
            draft = create["draft"]
            invalid = {**draft, "entries": [{**draft["entries"][0], "note": CANARY}]}
            result = await client.call_tool("create_workout_draft", {**create, "draft": invalid})
            assert result.is_error and CANARY not in result.model_dump_json()
            db.fail["tool.call"] = "insufficient_scope"
            result = await client.call_tool("get_progress", {})
            assert result.is_error and result.structured_content["error"] == "insufficient_scope"
        assert all(set(args) == {"accessHash", "tool", "args"} for op, args in db.calls if op == "tool.call")
        db.access = False
        assert (await c.post("/mcp")).status_code == 401


@pytest.mark.anyio
async def test_consent_binding_and_redirection(settings):
    db = Gateway()
    async with session(settings, db) as c:
        info = await register(c)
        path, view = await begin(c, info["client_id"])
        assert view["scopes"] == list(SCOPES)
        assert "private-test" not in json.dumps(view)
        body = {"approved": True, "scopes": ["training:read"], "csrf": view["csrf"]}
        headers = {"authorization": "Bearer " + HUMAN, "origin": "https://wrong.example"}
        assert (await c.post(path, json=body, headers=headers)).status_code == 403
        headers["origin"] = ORIGIN
        assert (await c.post(path, json={**body, "csrf": "invalid"}, headers=headers)).status_code == 403
        assert (await c.post("/consent/api/" + str(uuid4()), json=body, headers=headers)).status_code == 403
        opaque = await decide(c, path, view, human=TOKEN)
        assert opaque.status_code == 403
        assert not db.decisions
        response = await decide(c, path, view, scopes=["training:read"])
        query = parse_qs(urlsplit(response.json()["redirectUrl"]).query)
        assert query["state"] == ["return state"]
        assert db.decisions[0][1]["p_code_hash"] == hashlib.sha256(query["code"][0].encode()).hexdigest()
        assert response.headers["referrer-policy"] == "no-referrer"
        assert (await c.get(path)).status_code == 403


@pytest.mark.anyio
async def test_registration_limits_raw_redirects_and_redaction(settings):
    db = Gateway()
    async with session(settings, db) as c:
        for uri in [
            "http://remote.example/cb",
            "https://user@client.example/cb",
            CALLBACK + "#frag",
            CALLBACK + "?state=fixed",
            "HTTPS://client.example/callback",
        ]:
            assert (await c.post("/register", json={"redirect_uris": [uri]})).status_code == 400
        assert not db.clients
        info = await register(c, "client_secret_post")
        stored = json.dumps(db.clients)
        assert info["client_secret"] not in stored
        db.fail["client.register"] = "rate_limited"
        response = await c.post("/register", json={"redirect_uris": [CALLBACK]})
        assert response.status_code == 429
        params = authorize_params(info["client_id"])
        for key, value in [
            ("resource", "https://other.example/mcp"),
            ("code_challenge", CANARY),
            ("redirect_uri", CALLBACK.upper()),
        ]:
            response = await c.get("/authorize", params={**params, key: value})
            assert response.status_code == 400 and CANARY not in response.text
        response = await c.post(
            "/token",
            content="client_id=x&client_id=y",
            headers={"content-type": "application/x-www-form-urlencoded"},
        )
        assert response.status_code == 400
        response = await c.post("/register", content=b" " * 65537)
        assert response.status_code == 413
