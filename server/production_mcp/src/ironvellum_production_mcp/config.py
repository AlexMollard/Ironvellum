import base64
import json
import os
import re
from dataclasses import dataclass, field
from pathlib import Path
from urllib.parse import urlsplit

from cryptography.fernet import Fernet
from pydantic import AnyHttpUrl

SCOPES = ("training:read", "progress:read", "savedworkouts:read", "workoutdrafts:create")
MAX_BODY = 65536
MAX_RESULT = 1048576
LOOPBACK = {"localhost", "127.0.0.1", "::1"}


def origin(value, loopback):
    url = urlsplit(value)
    if (
        not value.isascii()
        or url.scheme != "https"
        and not (loopback and url.scheme == "http" and url.hostname in LOOPBACK)
        or not url.hostname
        or url.username is not None
        or url.password is not None
        or value != f"{url.scheme}://{url.netloc}"
        or str(AnyHttpUrl(value)).rstrip("/") != value
    ):
        raise ValueError("Expected a canonical HTTPS origin")
    return value


def browser_public_key(value):
    if re.fullmatch(r"sb_publishable_[A-Za-z0-9_-]+", value):
        return True
    # Classification only. Supabase verifies the actual key. Never expose a private role to the browser.
    try:
        _, payload, _ = value.split(".")
        claims = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
        return isinstance(claims, dict) and claims.get("role") == "anon"
    except (ValueError, TypeError, RecursionError):
        return False


@dataclass(frozen=True)
class Settings:
    issuer: str
    backend_origin: str
    public_key: str = field(repr=False)
    gateway_key: str = field(repr=False)
    encryption_key: str = field(repr=False)
    manifest: Path
    loopback: bool = False

    def __post_init__(self):
        origin(self.issuer, self.loopback)
        origin(self.backend_origin, self.loopback)
        if not browser_public_key(self.public_key):
            raise ValueError("A Supabase publishable key or legacy anon JWT is required for the browser")
        if not self.gateway_key or self.public_key == self.gateway_key:
            raise ValueError("Distinct public and private backend keys are required")
        try:
            Fernet(self.encryption_key)
        except (ValueError, TypeError):
            raise ValueError("A persistent Fernet key is required") from None

    @property
    def resource(self):
        return self.issuer + "/mcp"

    @classmethod
    def from_env(cls):
        def required(name):
            value = os.getenv("IRONVELLUM_MCP_" + name)
            if not value:
                raise ValueError("Missing IRONVELLUM_MCP_" + name)
            return value

        return cls(
            required("ISSUER"),
            required("BACKEND_URL"),
            required("PUBLIC_KEY"),
            required("GATEWAY_KEY"),
            required("ENCRYPTION_KEY"),
            Path(required("MANIFEST")),
            os.getenv("IRONVELLUM_MCP_LOOPBACK") == "1",
        )

    def catalogue(self):
        try:
            with self.manifest.open("rb") as f:
                data = f.read(MAX_RESULT + 1)
            if len(data) > MAX_RESULT:
                raise ValueError
            manifest = json.loads(data)
            if (
                not isinstance(manifest, dict)
                or set(manifest) != {"schemaVersion", "movements"}
                or type(manifest["schemaVersion"]) is not int
                or manifest["schemaVersion"] != 1
            ):
                raise ValueError
            rows = manifest["movements"]
            if not isinstance(rows, list) or not 1 <= len(rows) <= 5000:
                raise ValueError
            keys = set()
            for row in rows:
                if (
                    set(row) != {"key", "name", "metric"}
                    or not isinstance(row["name"], str)
                    or not 1 <= len(row["name"]) <= 128
                    or "\0" in row["name"]
                    or row["key"] != row["name"].strip().lower()
                    or row["key"] in keys
                    or row["metric"] not in {"REPS", "HOLD", "DURATION", "DISTANCE_TIME", "ATTEMPTS_GRADE"}
                ):
                    raise ValueError
                keys.add(row["key"])
            return manifest
        except (OSError, ValueError, KeyError, TypeError, RecursionError):
            raise ValueError("Valid version-1 movement manifest required") from None
