from dataclasses import replace

import pytest
from test_service import Gateway, begin, register, session


@pytest.mark.anyio
async def test_consent_page_requires_matching_binding_and_serves_local_assets(settings):
    async with session(settings, Gateway()) as client:
        registration = await register(client)
        _, view = await begin(client, registration["client_id"])
        page = "/consent/" + view["requestId"]
        bound = await client.get(page)
        assert bound.status_code == 200
        assert "text/html" in bound.headers["content-type"]
        assert "frame-ancestors 'none'" in bound.headers["content-security-policy"]
        for asset in ["browser.js", "paper.css", "chakra.ttf", "chakra-medium.ttf"]:
            assert (await client.get("/ui/" + asset)).status_code == 200
        client.cookies.clear()
        expired = await client.get(page)
        assert expired.status_code == 403
        assert "Start a new connection" in expired.text


@pytest.mark.anyio
async def test_management_and_optional_google_callback(settings):
    async with session(settings, Gateway()) as client:
        assert (await client.get("/connections")).status_code == 200
        config = (await client.get("/ui-config")).json()
        assert config == {
            "backendUrl": settings.backend_origin,
            "publicKey": settings.public_key,
            "googleCallback": None,
        }
        assert (await client.get("/signin/callback?code=example")).status_code != 200
    with pytest.raises(ValueError):
        replace(settings, google_callback="https://elsewhere.example/callback")
    enabled = replace(settings, google_callback=settings.issuer + "/signin/callback")
    async with session(enabled, Gateway()) as client:
        assert (await client.get("/ui-config")).json()["googleCallback"] == enabled.google_callback
        assert (await client.get("/signin/callback?code=synthetic")).status_code == 200
