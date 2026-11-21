"""Fixed, credential-free HTTP probes and private maintenance commands."""

import http.client
import json
import os
import re
from contextlib import closing
from urllib.parse import urlsplit
from urllib.request import parse_http_list

from .backend import Failure, SupabaseBackend
from .config import SCOPES, origin
from .server import fingerprint, verify_backend


def has_resource_challenge(headers, metadata):
    challenges = headers.get_all("www-authenticate", [])
    if len(challenges) != 1:
        return False
    scheme, _, parameters = challenges[0].partition(" ")
    if scheme.lower() != "bearer":
        return False
    values = {}
    for field in parse_http_list(parameters):
        name, separator, value = field.partition("=")
        key = name.strip().lower()
        if not separator or not re.fullmatch(r"[!#$%&'*+.^_`|~a-z0-9-]+", key) or key in values:
            return False
        values[key] = value.strip()
    return values.get("resource_metadata") == f'"{metadata}"'


def probe(port=None):
    issuer = origin(os.environ.get("IRONVELLUM_MCP_ISSUER", ""), os.getenv("IRONVELLUM_MCP_LOOPBACK") == "1")
    address = urlsplit(issuer)

    def request(method, path, status):
        if port is not None:
            connection = http.client.HTTPConnection("127.0.0.1", port, timeout=12)
        else:
            transport = (
                http.client.HTTPSConnection if address.scheme == "https" else http.client.HTTPConnection
            )
            connection = transport(address.netloc, timeout=12)
        with closing(connection):
            connection.request(method, path, headers={"Host": address.netloc})
            response = connection.getresponse()
            raw = response.read(65537)
            if response.status != status or len(raw) > 65536:
                raise Failure("unavailable")
            return response.headers, raw

    def document(path):
        data = json.loads(request("GET", path, 200)[1])
        if not isinstance(data, dict):
            raise Failure("unavailable")
        return data

    health = document("/healthz")
    if set(health) != {"ready"} or health["ready"] is not True:
        raise Failure("unavailable")
    if port is None:
        metadata = "/.well-known/oauth-protected-resource/mcp"
        resource = document(metadata)
        authorization = document("/.well-known/oauth-authorization-server")
        headers, _ = request("POST", "/mcp", 401)
        scopes = resource.get("scopes_supported")
        if (
            resource.get("resource") != issuer + "/mcp"
            or resource.get("authorization_servers") != [issuer]
            or not isinstance(scopes, list)
            or not all(isinstance(scope, str) for scope in scopes)
            or set(scopes) != set(SCOPES)
            or authorization.get("issuer") != issuer
            or authorization.get("token_endpoint") != issuer + "/token"
            or not has_resource_challenge(headers, issuer + metadata)
        ):
            raise Failure("unavailable")


async def maintenance(settings, command):
    backend = SupabaseBackend(settings)
    try:
        if command == "prune":
            await verify_backend(settings, backend, fingerprint(settings.catalogue()))
            result = await backend.gateway("prune", {})
            affected = result.get("affected")
            if type(affected) is not int or affected < 0:
                raise Failure("unavailable")
            return {"affected": affected}
        current = await backend.gateway("configuration.get", {})
        count, digest = current.get("catalogueCount"), current.get("catalogueHash")
        if (
            current.get("resourceUrl") != settings.resource
            or current.get("keyVerification") != settings.key_verification
            or type(count) is not int
            or not 1 <= count <= 5000
            or not isinstance(digest, str)
            or not re.fullmatch("[a-f0-9]{64}", digest)
        ):
            raise Failure("unavailable")
        return {"catalogueCount": count, "catalogueHash": digest}
    finally:
        await backend.close()
