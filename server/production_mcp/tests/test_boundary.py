import base64
from dataclasses import replace

import httpx2 as httpx
import pytest
from cryptography.fernet import Fernet
from ironvellum_production_mcp.backend import Failure, SupabaseBackend
from ironvellum_production_mcp.config import Settings
from ironvellum_production_mcp.server import create_app, fingerprint
from test_service import (
    CALLBACK,
    CANARY,
    FIX,
    ORIGIN,
    TOKEN,
    Gateway,
    authorize_params,
    begin,
    decide,
    register,
    session,
)


def test_manifest_and_configuration(settings, tmp_path, monkeypatch):
    with pytest.raises(ValueError):
        replace(settings, encryption_key="")
    for url in [
        "http://remote.example",
        "https://bad.example/path",
        "https://user@bad.example",
        "https://bad.example?",
    ]:
        with pytest.raises(ValueError):
            replace(settings, issuer=url)
    with pytest.raises(ValueError):
        replace(settings, issuer="http://127.0.0.1")
    replace(settings, issuer="http://127.0.0.1", loopback=True)
    with pytest.raises(ValueError):
        create_app(replace(settings, manifest=tmp_path / "missing"), Gateway())
    assert (
        fingerprint(settings.catalogue())[1] == FIX["gateway"]["configuration.get"]["result"]["catalogueHash"]
    )
    for unsafe in ("sb_secret_private", "x.eyJyb2xlIjoic2VydmljZV9yb2xlIn0.x"):
        with pytest.raises(ValueError):
            replace(settings, public_key=unsafe)
    replace(settings, public_key="x.eyJyb2xlIjoiYW5vbiJ9.x")
    monkeypatch.delenv("IRONVELLUM_MCP_ENCRYPTION_KEY", raising=False)
    with pytest.raises(ValueError):
        Settings.from_env()


@pytest.mark.anyio
@pytest.mark.parametrize(
    "mismatch", [{"resourceUrl": None}, {"catalogueCount": 0}, {"catalogueHash": "0" * 64}]
)
async def test_readiness_requires_matching_database_state(settings, mismatch):
    class Mismatch:
        async def gateway(self, op, args):
            assert op == "configuration.get"
            return {**FIX["gateway"]["configuration.get"]["result"], **mismatch}

    app = create_app(settings, Mismatch())
    with pytest.raises(Failure, match="unavailable"):
        async with app.router.lifespan_context(app):
            pytest.fail("Startup should have failed")


@pytest.mark.anyio
async def test_wrong_key_and_swapped_client_metadata_fail_closed(settings):
    db = Gateway()
    async with session(settings, db) as c:
        a, b = await register(c), await register(c)
        db.clients[a["client_id"]]["metadataCiphertext"] = db.clients[b["client_id"]]["metadataCiphertext"]
        resp = await c.post("/revoke", data={"client_id": a["client_id"], "token": TOKEN})
        assert resp.status_code == 503
    async with session(replace(settings, encryption_key=Fernet.generate_key().decode()), db) as c:
        resp = await c.post("/revoke", data={"client_id": b["client_id"], "token": TOKEN})
        assert resp.status_code == 503
    assert not any(op == "token.revoke" for op, _ in db.calls)


@pytest.mark.anyio
async def test_browser_scope_bounds_host_and_metadata(settings):
    db = Gateway()
    async with session(settings, db) as c:
        for metadata in [
            {"logo_uri": "https://other.example/img"},
            {"jwks_uri": "https://other.example/keys"},
            {"redirect_uris": [CALLBACK] * 17},
            {"client_name": "x" * 101},
            {"scope": "unknown"},
            {"scope": "training:read training:read"},
            {"token_endpoint_auth_method": "private_key_jwt"},
        ]:
            response = await c.post("/register", json={"redirect_uris": [CALLBACK], **metadata})
            assert response.status_code == 400
        assert not db.calls[1:]
        assert (await c.get("/healthz", headers={"Host": "different.example"})).status_code == 400
        info = await register(c)
        path, view = await begin(c, info["client_id"])
        denied = await decide(c, path, view, approved=False, scopes=[])
        assert "error=access_denied" in denied.json()["redirectUrl"]
        assert db.decisions[-1][1]["p_code_hash"] is None
        path, view = await begin(c, info["client_id"])
        first = c.cookies.get("__Host-iv-consent")
        assert first and not first.startswith(view["requestId"])
        assert (await decide(c, path, view, scopes=["unknown"])).status_code == 400
        c.cookies.clear()
        assert (await c.get(path)).status_code == 403
        assert (await decide(c, path, view)).status_code == 403
        assert len(db.decisions) == 1
        db.fail["authorization.create"] = "rate_limited"
        resp = await c.get("/authorize", params=authorize_params(info["client_id"]))
        assert resp.status_code == 429 and resp.headers["retry-after"] == "60"


@pytest.mark.anyio
async def test_duplicate_headers_chunked_size_and_sanitized_protocol_error(settings):
    async with session(settings, Gateway()) as c:
        response = await c.post(
            "/register",
            content=b'{"redirect_uris":[],"redirect_uris":[]}',
            headers={"content-type": "application/json"},
        )
        assert response.status_code == 400

        async def chunks():
            yield b" " * 50000
            yield b" " * 15537

        response = await c.post("/register", content=chunks(), headers={"content-type": "application/json"})
        assert response.status_code == 413
        response = await c.get("/healthz", headers=[("origin", ORIGIN), ("origin", ORIGIN)])
        assert response.status_code == 400
        response = await c.post(
            "/mcp",
            json={"jsonrpc": "2.0", "id": 1, "method": CANARY, "params": {}},
            headers={
                "authorization": "Bearer " + TOKEN,
                "accept": "application/json, text/event-stream",
                "MCP-Protocol-Version": "2026-07-28",
                "Mcp-Method": CANARY,
            },
        )
        assert CANARY not in response.text


@pytest.mark.anyio
async def test_public_client_rejects_bogus_secret_method(settings):
    db = Gateway()
    async with session(settings, db) as c:
        info = await register(c)
        wire = base64.b64encode((info["client_id"] + ":unused").encode()).decode()
        for headers, data in [
            ({"authorization": "Basic " + wire}, {}),
            ({}, {"client_id": info["client_id"], "client_secret": "unused"}),
        ]:
            response = await c.post("/revoke", data={"token": TOKEN, **data}, headers=headers)
            assert response.status_code == 401
        assert not any(op == "token.revoke" for op, _ in db.calls)


@pytest.mark.anyio
async def test_backend_credentials_only_reach_fixed_rpc_and_errors_are_bounded(settings):
    db = SupabaseBackend(settings)
    await db.close()
    seen = []

    def transport(request):
        seen.append(request)
        if len(seen) == 1:
            return httpx.Response(302, headers={"location": "https://other.example/"})
        if len(seen) == 2:
            return httpx.Response(200, json={"schemaVersion": 1, "ok": False, "error": CANARY})
        return httpx.Response(200, content=b" " * 1048577)

    db.client = httpx.AsyncClient(transport=httpx.MockTransport(transport), follow_redirects=False)
    async with db.client:
        with pytest.raises(Failure, match="^unavailable$"):
            await db.gateway("configuration.get", {})
        with pytest.raises(Failure, match="^unavailable$"):
            await db.consent("human-test", {})
        with pytest.raises(Failure, match="^result_too_large$"):
            await db.gateway("configuration.get", {})
    assert [str(r.url).rsplit("/", 1)[-1] for r in seen] == [
        "ironvellum_mcp_gateway",
        "ironvellum_mcp_consent",
        "ironvellum_mcp_gateway",
    ]
    assert all(r.url.host == "backend.example" for r in seen)
    assert seen[0].headers["authorization"] == "Bearer private-test"
    assert seen[1].headers["apikey"] == "sb_publishable_test"
    assert seen[1].headers["authorization"] == "Bearer human-test"
