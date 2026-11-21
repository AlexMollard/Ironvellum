"""Network HTTP and PostgreSQL integration, using disposable stub identities and fixed RPCs."""

import base64
import json
import os
import socket
import subprocess
import sys
import time
from urllib.parse import parse_qs, urlsplit

import httpx2 as httpx
import pytest
from cryptography.fernet import Fernet
from mcp.client import Client
from mcp.client.streamable_http import streamable_http_client
from test_service import (
    CALLBACK,
    CANARY,
    FIX,
    HUMAN,
    ROOT,
    SCOPES,
    VERIFIER,
    authorize_params,
    register,
)


class NetworkService:
    def __init__(self, origin, env, logfile):
        self.origin, self.env, self.logfile = origin, env, logfile
        self.process = None

    def start(self):
        self.process = subprocess.Popen(
            [
                sys.executable,
                "-m",
                "ironvellum_production_mcp",
                "serve",
                "--port",
                str(urlsplit(self.origin).port),
            ],
            env=self.env,
            stdout=self.logfile,
            stderr=self.logfile,
        )
        until = time.monotonic() + 15
        with httpx.Client(trust_env=False) as client:
            while time.monotonic() < until:
                if self.process.poll() is not None:
                    break
                try:
                    if client.get(self.origin + "/healthz", timeout=1).status_code == 200:
                        return
                except httpx.HTTPError:
                    pass
                time.sleep(0.1)
        raise RuntimeError("Disposable service did not become ready")

    def stop(self):
        if self.process and self.process.poll() is None:
            self.process.terminate()
            self.process.wait(timeout=10)


def disposable_network(work):
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        origin = "http://127.0.0.1:" + str(sock.getsockname()[1])
    bridge = subprocess.Popen(
        ["node", str(ROOT / "tests/sql/service-bridge.mjs"), origin + "/mcp"],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        text=True,
    )
    try:
        backend = bridge.stdout.readline().strip()
        if not backend.startswith("http://127.0.0.1:"):
            raise RuntimeError("Install locked native SQL test dependencies first")
        manifest = work / "manifest.json"
        catalogue = FIX["gateway"]["catalogue.put"]["args"]
        # Deliberately unsorted UTF-8 records exercise Python/PostgreSQL fingerprint compatibility.
        rows = [{"key": "é press", "name": "é Press", "metric": "REPS"}, *catalogue["movements"]]
        manifest.write_text(json.dumps({**catalogue, "movements": rows}))
        conf = {
            "ISSUER": origin,
            "BACKEND_URL": backend,
            "PUBLIC_KEY": "sb_publishable_test",
            "GATEWAY_KEY": "private-test",
            "ENCRYPTION_KEY": Fernet.generate_key().decode(),
            "LOOPBACK": "1",
            "MANIFEST": str(manifest),
        }
        env = {
            **os.environ,
            **{"IRONVELLUM_MCP_" + k: v for k, v in conf.items()},
            "PYTHONPATH": str(ROOT / "src"),
        }
        # Install the synthetic catalogue through the operator CLI. Repeating it must be idempotent.
        for _ in range(2):
            subprocess.run(
                [sys.executable, "-m", "ironvellum_production_mcp", "configure-backend"],
                env=env,
                check=True,
                timeout=15,
                capture_output=True,
            )
        logpath = work / "service.log"
        with logpath.open("wb") as log:
            service = NetworkService(origin, env, log)
            try:
                service.start()
                yield service, bridge, logpath
            finally:
                service.stop()
    finally:
        bridge.stdin.close()
        bridge.wait(timeout=25)


@pytest.fixture(scope="module")
def network(tmp_path_factory):
    yield from disposable_network(tmp_path_factory.mktemp("native-http"))


@pytest.fixture
def admission_network(tmp_path):
    yield from disposable_network(tmp_path)


@pytest.mark.anyio
async def test_real_sql_admission_quotas(admission_network):
    service, _, _ = admission_network
    async with httpx.AsyncClient(base_url=service.origin, trust_env=False) as client:
        first = await register(client)
        params = {**authorize_params(first["client_id"]), "resource": service.origin + "/mcp"}
        for _ in range(20):
            assert (await client.get("/authorize", params=params)).status_code == 302
        limited = await client.get("/authorize", params=params)
        assert limited.status_code == 429 and limited.json() == {"error": "rate_limited"}
        for _ in range(19):
            await register(client)
        limited = await client.post("/register", json={"redirect_uris": [CALLBACK]})
        assert limited.status_code == 429 and limited.json() == {"error": "rate_limited"}


async def code_for(c, info, human=HUMAN, scopes=SCOPES, approved=True, explicit=True):
    origin = str(c.base_url).rstrip("/")
    params = {**authorize_params(info["client_id"]), "resource": origin + "/mcp", "state": CANARY}
    if not explicit:
        del params["redirect_uri"]
    start = await c.get("/authorize", params=params)
    assert start.status_code == 302
    path = "/consent/api/" + urlsplit(start.headers["location"]).path.rsplit("/", 1)[1]
    view = await c.get(path)
    assert view.status_code == 200
    decision = await c.post(
        path,
        json={"approved": approved, "csrf": view.json()["csrf"], "scopes": list(scopes)},
        headers={"origin": origin, "authorization": "Bearer " + human},
    )
    assert decision.status_code == 200
    redirect = decision.json()["redirectUrl"]
    assert redirect.startswith(CALLBACK + "?")
    args = parse_qs(urlsplit(redirect).query)
    assert args["state"] == [CANARY]
    if approved:
        return args["code"][0]
    assert args["error"] == ["access_denied"] and "code" not in args


async def post_client(c, info, path, data):
    body = dict(data)
    headers = {}
    method = info["token_endpoint_auth_method"]
    if method == "client_secret_basic":
        wire = (info["client_id"] + ":" + info["client_secret"]).encode()
        headers["authorization"] = "Basic " + base64.b64encode(wire).decode()
    else:
        body["client_id"] = info["client_id"]
        if method == "client_secret_post":
            body["client_secret"] = info["client_secret"]
    return await c.post(path, data=body, headers=headers)


def exchange_body(c, code):
    return {
        "grant_type": "authorization_code",
        "code": code,
        "code_verifier": VERIFIER,
        "redirect_uri": CALLBACK,
        "resource": str(c.base_url).rstrip("/") + "/mcp",
    }


async def issue(c, info, human=HUMAN, scopes=SCOPES):
    code = await code_for(c, info, human, scopes)
    response = await post_client(c, info, "/token", exchange_body(c, code))
    assert response.status_code == 200
    row = response.json()
    assert set(row["scope"].split()) == set(scopes)
    assert 0 < row["expires_in"] <= 600
    return row


async def call(origin, token, name, args, mode="2026-07-28"):
    async with (
        httpx.AsyncClient(headers={"authorization": "Bearer " + token}, trust_env=False) as http,
        Client(streamable_http_client(origin + "/mcp", http_client=http), mode=mode) as sdk,
    ):
        return (await sdk.call_tool(name, args)).structured_content


@pytest.mark.anyio
async def test_restart_isolation_read_draft_and_revoke(network):
    service, _, logpath = network
    async with httpx.AsyncClient(base_url=service.origin, trust_env=False) as c:
        info = await register(c)
        first = await issue(c, info)
        second = await issue(c, info, "other.test.session")
        service.stop()
        service.start()
        for row, label, mode in [(first, "Training", "legacy"), (second, "Other workout", "2026-07-28")]:
            history = await call(service.origin, row["access_token"], "list_workouts", {}, mode)
            assert [workout["label"] for workout in history["workouts"]] == [label]
            detail = await call(
                service.origin, row["access_token"], "get_workout", {"id": history["workouts"][0]["id"]}
            )
            assert CANARY not in json.dumps(detail)
        created = await call(
            service.origin,
            first["access_token"],
            "create_workout_draft",
            FIX["tools"]["create_workout_draft"]["args"],
        )
        key = {"id": created["draft"]["id"]}
        assert (await call(service.origin, first["access_token"], "get_workout_draft", key)) == created
        assert (await call(service.origin, second["access_token"], "get_workout_draft", key))[
            "error"
        ] == "not_found"
        assert (await post_client(c, info, "/revoke", {"token": first["access_token"]})).status_code == 200
        assert (
            await c.post("/mcp", headers={"authorization": "Bearer " + first["access_token"]})
        ).status_code == 401
        assert (await call(service.origin, second["access_token"], "get_progress", {}))[
            "freshness"
        ] == "absent"
        log = logpath.read_text(errors="replace")
        assert all(
            value not in log for value in (CANARY, HUMAN, first["access_token"], first["refresh_token"])
        )


@pytest.mark.anyio
@pytest.mark.parametrize("method", ["none", "client_secret_post", "client_secret_basic"])
async def test_persisted_pkce_refresh_and_replay(network, method):
    service, _, _ = network
    async with httpx.AsyncClient(base_url=service.origin, trust_env=False) as c:
        info = await register(c, method)
        code = await code_for(c, info, scopes=["training:read"])
        body = exchange_body(c, code)
        for changes, expected in [
            ({"code_verifier": "x" * 64}, "invalid_grant"),
            ({"resource": "https://wrong.example/mcp"}, "invalid_target"),
            ({"redirect_uri": "https://other.example/cb"}, "invalid_request"),
        ]:
            error = await post_client(c, info, "/token", {**body, **changes})
            assert error.json()["error"] == expected
        response = await post_client(c, info, "/token", body)
        assert response.status_code == 200
        access = response.json()["access_token"]
        assert response.json()["scope"] == "training:read"
        assert (await call(service.origin, access, "get_progress", {}))["error"] == "insufficient_scope"
        assert (await post_client(c, info, "/token", body)).json()["error"] == "invalid_grant"
        assert (await c.post("/mcp", headers={"authorization": "Bearer " + access})).status_code == 401

        grant = await issue(c, info)
        refresh = {
            "grant_type": "refresh_token",
            "refresh_token": grant["refresh_token"],
            "scope": "training:read",
        }
        assert (
            await post_client(c, info, "/token", {**refresh, "resource": "https://wrong.example/mcp"})
        ).json()["error"] == "invalid_target"
        rotated = await post_client(c, info, "/token", refresh)
        assert rotated.status_code == 200 and rotated.json()["scope"] == "training:read"
        widened = {**refresh, "refresh_token": rotated.json()["refresh_token"], "scope": "progress:read"}
        assert (await post_client(c, info, "/token", widened)).json()["error"] == "invalid_scope"
        assert (await post_client(c, info, "/token", refresh)).json()["error"] == "invalid_grant"
        token = rotated.json()["access_token"]
        assert (await c.post("/mcp", headers={"authorization": "Bearer " + token})).status_code == 401
        live = await issue(c, info)
        assert (await post_client(c, info, "/revoke", {"token": live["refresh_token"]})).status_code == 200
        assert (
            await c.post("/mcp", headers={"authorization": "Bearer " + live["access_token"]})
        ).status_code == 401


@pytest.mark.anyio
async def test_expired_code_implicit_redirect_and_consent_denial(network):
    service, bridge, _ = network
    async with httpx.AsyncClient(base_url=service.origin, trust_env=False) as c:
        info = await register(c)
        await code_for(c, info, scopes=[], approved=False)
        code = await code_for(c, info, explicit=False)
        exchange = exchange_body(c, code)
        assert (await post_client(c, info, "/token", exchange)).json()["error"] == "invalid_request"
        del exchange["redirect_uri"]
        assert (await post_client(c, info, "/token", exchange)).status_code == 200
        code = await code_for(c, info)
        bridge.stdin.write("expire-codes\n")
        bridge.stdin.flush()
        assert bridge.stdout.readline().strip() == "expired"
        assert (await post_client(c, info, "/token", exchange_body(c, code))).json()[
            "error"
        ] == "invalid_grant"


@pytest.mark.anyio
async def test_official_oauth_client_discovers_registers_and_calls(network):
    from mcp.client.auth import AuthorizationCodeResult, OAuthClientProvider
    from mcp.shared.auth import OAuthClientMetadata

    service, _, _ = network
    resource = service.origin + "/mcp"

    class Storage:
        tokens = None
        client_info = None

        async def get_tokens(self):
            return self.tokens

        async def set_tokens(self, value):
            self.tokens = value

        async def get_client_info(self):
            return self.client_info

        async def set_client_info(self, value):
            self.client_info = value

    store = Storage()
    callback = None
    async with httpx.AsyncClient(base_url=service.origin, trust_env=False) as browser:

        async def redirect(url):
            nonlocal callback
            start = await browser.get(url)
            assert start.status_code == 302
            request_id = urlsplit(start.headers["location"]).path.rsplit("/", 1)[1]
            path = "/consent/api/" + request_id
            view = (await browser.get(path)).json()
            decision = await browser.post(
                path,
                json={"approved": True, "scopes": ["training:read"], "csrf": view["csrf"]},
                headers={"origin": service.origin, "authorization": "Bearer " + HUMAN},
            )
            args = parse_qs(urlsplit(decision.json()["redirectUrl"]).query)
            callback = AuthorizationCodeResult(code=args["code"][0], state=args["state"][0])

        async def complete():
            return callback

        auth = OAuthClientProvider(
            resource,
            OAuthClientMetadata(
                redirect_uris=[CALLBACK],
                client_name="SDK",
                scope="training:read",
                token_endpoint_auth_method="none",
            ),
            store,
            redirect,
            complete,
        )
        async with (
            httpx.AsyncClient(auth=auth, trust_env=False) as http,
            Client(streamable_http_client(resource, http_client=http), mode="2026-07-28") as client,
        ):
            result = await client.call_tool("list_workouts", {})
            assert result.structured_content["workouts"][0]["label"] == "Training"
        assert store.tokens.scope == "training:read"
