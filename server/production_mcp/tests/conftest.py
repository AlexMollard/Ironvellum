import json
from pathlib import Path

import pytest
from cryptography.fernet import Fernet
from ironvellum_production_mcp.config import Settings

ROOT = Path(__file__).resolve().parents[1]
ORIGIN = "https://ironvellum.example"
FIX = json.loads((ROOT / "contracts/fixtures.json").read_text())


@pytest.fixture
def anyio_backend():
    return "asyncio"


@pytest.fixture
def settings(tmp_path):
    manifest = tmp_path / "manifest.json"
    manifest.write_text(json.dumps(FIX["gateway"]["catalogue.put"]["args"]))
    return Settings(
        ORIGIN,
        "https://backend.example",
        "sb_publishable_test",
        "private-test",
        Fernet.generate_key().decode(),
        manifest,
    )
