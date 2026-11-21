"""Run against an installed production wheel and a local Chromium browser with Playwright."""

import argparse
import base64
import hashlib
import os
import socket
import sys
import tempfile
import threading
import time
from pathlib import Path
from urllib.parse import parse_qs, urlencode, urlsplit

import uvicorn
from cryptography.fernet import Fernet
from ironvellum_production_mcp import __file__ as installed_file
from ironvellum_production_mcp.config import Settings
from ironvellum_production_mcp.server import create_app, fingerprint
from playwright.sync_api import expect, sync_playwright

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from test_service import CALLBACK, FIX, HUMAN, Gateway, authorize_params

CLIENT = "Training assistant <em>sample</em>"
PASSWORD = "synthetic-password-only"


class BrowserGateway(Gateway):
    def __init__(self, cfg):
        super().__init__()
        self.cfg = cfg
        self.ttl = 600000

    async def gateway(self, operation, args):
        if operation == "configuration.get":
            count, digest = fingerprint(self.cfg.catalogue())
            return {
                "resourceUrl": self.cfg.resource,
                "keyVerification": self.cfg.key_verification,
                "catalogueCount": count,
                "catalogueHash": digest,
            }
        result = await super().gateway(operation, args)
        if operation == "authorization.create":
            row = self.requests[result["requestId"]]
            row.update(clientName=CLIENT, expiresAtMs=int(time.time() * 1000) + self.ttl)
        return result


def delayed_management_reads(page, origin, signin, pending, failure):
    """The read races a revoke and completes after the authoritative post-revoke read."""
    page.goto(origin + "/connections")
    signin()
    revoke = page.get_by_role("button", name="Disconnect " + CLIENT)
    expect(revoke).to_be_visible()
    pending["hold"] = True
    with page.expect_request("**/rpc/ironvellum_mcp_revoke_connection"):
        revoke.click()
    with page.expect_request("**/rpc/ironvellum_mcp_connections"):
        page.get_by_role("button", name="Refresh").click()
    page.get_by_text("Loading connections…").wait_for()
    pending["hold"] = False
    pending.pop("revoke")()
    expect(page.get_by_text("No connections yet.")).to_be_visible()
    with page.expect_request_finished(lambda req: req.url.endswith("/rpc/ironvellum_mcp_connections")):
        pending.pop("read")(failure)
    # The fetch handlers paint before the next two browser frames.
    page.evaluate("new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)))")
    expect(revoke).to_have_count(0)
    expect(page.get_by_role("button", name="Try again")).to_have_count(0)
    expect(page.get_by_role("button", name="Refresh")).to_have_count(1)
    expect(page.get_by_text("Disconnected " + CLIENT + ".")).to_be_visible()


def run(captures, channel, management_race=False):
    assert "site-packages" in Path(installed_file).parts, "Install the wheel in an isolated environment"
    with tempfile.TemporaryDirectory() as tmp, socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        origin = "http://127.0.0.1:" + str(sock.getsockname()[1])
        manifest = Path(tmp) / "manifest.json"
        import json

        manifest.write_text(json.dumps(FIX["gateway"]["configuration.configure"]["args"]["catalogue"]))
        settings = Settings(
            origin,
            "https://backend.example",
            "sb_publishable_test",
            "private-test",
            Fernet.generate_key().decode(),
            manifest,
            True,
            origin + "/signin/callback",
        )
        gateway = BrowserGateway(settings)
        server = uvicorn.Server(
            uvicorn.Config(create_app(settings, gateway), log_level="critical", access_log=False)
        )
        thread = threading.Thread(target=lambda: server.run(sockets=[sock]), daemon=True)
        thread.start()
        try:
            for _ in range(100):
                if server.started:
                    break
                time.sleep(0.05)
            assert server.started
            with sync_playwright() as automation:
                browser = automation.chromium.launch(channel=channel, headless=True)
                with browser.new_context(
                    viewport={"width": 800, "height": 940}, color_scheme="dark"
                ) as context:
                    page = context.new_page()
                    seen, errors, callbacks, challenges = [], [], [], []
                    revoked = False
                    pending = {"hold": False}
                    page.on("pageerror", lambda err: errors.append(str(err)))
                    page.on("console", lambda message: seen.append(("console", message.text)))

                    def intercept(route):
                        nonlocal revoked
                        req = route.request
                        url = urlsplit(req.url)
                        seen.append(("url", req.url))
                        if url.netloc == urlsplit(origin).netloc:
                            return route.continue_()
                        if url.netloc == "client.example":
                            callbacks.append(
                                (parse_qs(url.query), req.all_headers(), req.is_navigation_request())
                            )
                            return route.fulfill(
                                content_type="text/html", body="<title>Sample client</title>"
                            )
                        assert url.netloc == "backend.example"
                        headers = {
                            "Access-Control-Allow-Origin": origin,
                            "Access-Control-Allow-Headers": "authorization,apikey,content-type",
                        }
                        if req.method == "OPTIONS":
                            return route.fulfill(status=204, headers=headers)
                        if url.path == "/auth/v1/authorize":
                            args = parse_qs(url.query)
                            assert args["provider"] == ["google"] and args["code_challenge_method"] == [
                                "s256"
                            ]
                            assert args["redirect_to"] == [settings.google_callback]
                            challenges.append(args["code_challenge"][0])
                            return route.fulfill(
                                status=302,
                                headers={"Location": settings.google_callback + "?code=sample-code"},
                            )
                        args = req.post_data_json
                        if url.path == "/auth/v1/token":
                            assert req.headers["apikey"] == settings.public_key
                            if url.query == "grant_type=password":
                                assert args == {"email": "person@example.invalid", "password": PASSWORD}
                            else:
                                assert url.query == "grant_type=pkce" and args["auth_code"] == "sample-code"
                                challenge = (
                                    base64.urlsafe_b64encode(
                                        hashlib.sha256(args["code_verifier"].encode()).digest()
                                    )
                                    .decode()
                                    .rstrip("=")
                                )
                                assert challenges == [challenge]
                            data = {"access_token": HUMAN, "refresh_token": "synthetic-refresh"}
                        else:
                            assert req.headers["authorization"] == "Bearer " + HUMAN
                            if url.path == "/rest/v1/rpc/ironvellum_mcp_connections":
                                grant = {
                                    "id": "sample",
                                    "clientName": CLIENT,
                                    "scopes": ["training:read"],
                                    "expiresAtMs": int(time.time() * 1000) + 600000,
                                    "revokedAtMs": None,
                                }
                                data = {
                                    "schemaVersion": 1,
                                    "ok": True,
                                    "resourceUrl": settings.resource,
                                    "connections": [] if revoked else [grant],
                                    "recentAccess": [],
                                }
                            else:
                                assert url.path == "/rest/v1/rpc/ironvellum_mcp_revoke_connection"
                                assert args == {"p_grant_id": "sample"}
                                if pending["hold"]:

                                    def finish_revoke():
                                        nonlocal revoked
                                        revoked = True
                                        route.fulfill(
                                            json={"schemaVersion": 1, "ok": True, "revoked": True},
                                            headers=headers,
                                        )

                                    pending["revoke"] = finish_revoke
                                    return
                                revoked = True
                                data = {"schemaVersion": 1, "ok": True, "revoked": True}
                            if pending["hold"]:
                                pending["read"] = lambda fail: route.fulfill(
                                    status=503 if fail else 200, json=data, headers=headers
                                )
                                return
                        return route.fulfill(json=data, headers=headers)

                    context.route("**/*", intercept)
                    client_id = context.request.post(
                        origin + "/register",
                        data={
                            "client_name": CLIENT,
                            "redirect_uris": [CALLBACK],
                            "token_endpoint_auth_method": "none",
                        },
                    ).json()["client_id"]

                    def begin():
                        query = {**authorize_params(client_id), "resource": settings.resource}
                        page.goto(origin + "/authorize?" + urlencode(query))
                        expect(page.get_by_role("heading", name="Sign in to Ironvellum")).to_be_visible()

                    def signin():
                        page.get_by_label("Email").fill("person@example.invalid")
                        page.get_by_label("Password").fill(PASSWORD)
                        page.get_by_label("Password").press("Enter")

                    def shot(name):
                        if captures:
                            captures.mkdir(exist_ok=True, parents=True)
                            page.evaluate("document.fonts.ready")
                            page.screenshot(path=captures / (name + ".png"), full_page=True)

                    if management_race:
                        for failure in (False, True):
                            revoked = False
                            delayed_management_reads(page, origin, signin, pending, failure)
                            assert revoked
                        assert not errors
                        print("Installed wheel / actual browser:", browser.version)
                        print(
                            "Passed: late active grant and late read error both suppressed after confirmed revoke"
                        )
                        return

                    begin()
                    shot("browser_signin")
                    signin()
                    expect(page.get_by_role("heading", name=CLIENT)).to_be_visible()
                    assert page.locator("em").count() == 0
                    shot("browser_consent")
                    checks = page.get_by_role("checkbox")
                    for index in range(1, 4):
                        checks.nth(index).focus()
                        page.keyboard.press("Space")
                    page.get_by_role("button", name="Allow selected permissions").focus()
                    page.keyboard.press("Enter")
                    page.wait_for_url(CALLBACK + "*")
                    assert gateway.decisions[-1][1]["p_scopes"] == ["training:read"]
                    assert "code" in callbacks[-1][0] and callbacks[-1][2]
                    assert "authorization" not in callbacks[-1][1]
                    begin()
                    page.get_by_role("button", name="Continue with Google").click()
                    expect(page.get_by_role("heading", name=CLIENT)).to_be_visible()
                    assert challenges
                    page.get_by_role("button", name="Deny").press("Enter")
                    page.wait_for_url(CALLBACK + "*")
                    assert (
                        callbacks[-1][0]["error"] == ["access_denied"]
                        and gateway.decisions[-1][1]["p_scopes"] == []
                    )
                    assert "authorization" not in callbacks[-1][1] and callbacks[-1][2]
                    page.goto(origin + "/connections")
                    signin()
                    expect(page.get_by_role("heading", name=CLIENT)).to_be_visible()
                    shot("browser_connections")
                    page.get_by_role("button", name="Disconnect " + CLIENT).press("Enter")
                    expect(page.get_by_text("No connections yet.")).to_be_visible()
                    assert revoked and page.locator("em").count() == 0
                    assert page.evaluate("localStorage.length + sessionStorage.length") == 0
                    assert HUMAN not in page.content() and PASSWORD not in page.content()
                    gateway.ttl = 1500
                    begin()
                    expect(page.get_by_role("heading", name="Connection request unavailable")).to_be_visible(
                        timeout=5000
                    )
                    assert len(gateway.decisions) == 2 and not errors
                    for _, item in seen:
                        assert all(
                            value not in item
                            for value in [HUMAN, PASSWORD, "synthetic-refresh", settings.gateway_key]
                        )
                    assert all(HUMAN not in cookie["value"] for cookie in context.cookies())
                    print("Installed wheel / actual browser:", browser.version)
                    print(
                        "Passed: scoped keyboard approval, Google PKCE + deny, expiry, revoke, inert client markup, credential redaction"
                    )
        finally:
            server.should_exit = True
            thread.join(5)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--captures", type=Path)
    parser.add_argument("--channel", default=os.getenv("MCP_BROWSER_CHANNEL", "msedge"))
    parser.add_argument("--management-race", action="store_true")
    args = parser.parse_args()
    run(args.captures, args.channel, args.management_race)
