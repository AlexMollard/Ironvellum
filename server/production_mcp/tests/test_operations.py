"""Probe the same CLI and safe operator output available in the deployment image."""

import json
import os
import subprocess
import sys
from contextlib import closing
from http.server import BaseHTTPRequestHandler, HTTPServer
from threading import Thread

import pytest
from ironvellum_production_mcp.backend import Failure
from ironvellum_production_mcp.config import SCOPES
from ironvellum_production_mcp.operations import probe
from test_native_http import disposable_network
from test_service import ROOT


def command(env, *args):
    return subprocess.run(
        [sys.executable, "-m", "ironvellum_production_mcp", *args],
        env=env,
        check=False,
        capture_output=True,
        text=True,
        timeout=20,
    )


def test_internal_probe_uses_canonical_host_and_requires_exact_ready():
    seen = []
    canonical = "mcp.example.invalid:8443"

    class Handler(BaseHTTPRequestHandler):
        reply = b'{"ready":true}'

        def do_GET(self):
            seen.append((self.path, dict(self.headers)))
            self.send_response(200 if self.headers["Host"] == canonical else 400)
            self.end_headers()
            self.wfile.write(self.reply)

        def log_message(self, *args):
            pass

    with HTTPServer(("127.0.0.1", 0), Handler) as server:
        thread = Thread(target=server.serve_forever)
        thread.start()
        try:
            env = {"PYTHONPATH": str(ROOT / "src"), "IRONVELLUM_MCP_ISSUER": "https://" + canonical}
            env = {**os.environ, **env}
            args = ("healthcheck", "--port", str(server.server_port))
            assert command(env, *args).returncode == 0
            Handler.reply = b'{"ready":false}'
            assert command(env, *args).returncode != 0
        finally:
            server.shutdown()
            thread.join()
    assert len(seen) == 2
    for path, headers in seen:
        assert path == "/healthz" and headers["Host"] == canonical
        assert not {"authorization", "cookie", "apikey", "x-forwarded-host"} & {k.lower() for k in headers}


def test_operator_commands_against_full_native_configuration(tmp_path):
    with closing(disposable_network(tmp_path, full_manifest=True)) as network:
        service, _, _ = next(network)
        env = service.env
        status = command(env, "catalogue-status")
        assert status.returncode == 0
        assert json.loads(status.stdout) == {
            "catalogueCount": 584,
            "catalogueHash": "6b8bea45f8781884088d99fe8fc76c0df4c5755a8c2d1e9914629710bfa18a66",
        }
        result = command(env, "prune")
        assert result.returncode == 0 and json.loads(result.stdout) == {"affected": 0}
        assert command(env, "smoke").returncode == 0


@pytest.mark.parametrize(
    ("challenge", "scopes"),
    [
        ('Basic resource_metadata="{url}"', SCOPES),
        ('Bearer x_resource_metadata="{url}"', SCOPES),
        ('Bearer resource_metadata="{url}", resource_metadata="other"', SCOPES),
        ('Bearer realm="local", Basic realm="other", resource_metadata="{url}"', SCOPES),
        ('Bearer resource_metadata="{url}"', {scope: True for scope in SCOPES}),
        ('Bearer resource_metadata="{url}"', [{"nested": "value"}]),
    ],
)
def test_public_smoke_rejects_malformed_discovery_or_challenge(monkeypatch, challenge, scopes):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            docs = {
                "/healthz": {"ready": True},
                "/.well-known/oauth-authorization-server": {
                    "issuer": issuer,
                    "token_endpoint": issuer + "/token",
                },
                "/.well-known/oauth-protected-resource/mcp": {
                    "resource": issuer + "/mcp",
                    "authorization_servers": [issuer],
                    "scopes_supported": scopes,
                },
            }
            self.send_response(200)
            self.end_headers()
            self.wfile.write(json.dumps(docs[self.path]).encode())

        def do_POST(self):
            self.send_response(401)
            self.send_header(
                "WWW-Authenticate", challenge.format(url=issuer + "/.well-known/oauth-protected-resource/mcp")
            )
            self.end_headers()

        def log_message(self, *args):
            pass

    with HTTPServer(("127.0.0.1", 0), Handler) as server:
        issuer = f"http://127.0.0.1:{server.server_port}"
        monkeypatch.setenv("IRONVELLUM_MCP_ISSUER", issuer)
        monkeypatch.setenv("IRONVELLUM_MCP_LOOPBACK", "1")
        thread = Thread(target=server.serve_forever)
        thread.start()
        try:
            with pytest.raises(Failure, match="unavailable"):
                probe()
        finally:
            server.shutdown()
            thread.join()
