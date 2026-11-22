from importlib.resources import files
import json

from ironvellum_local_connector.desktop_copy import LANGUAGES, WORDINGS, load_copy, validate_resources
from ironvellum_local_connector.setup import GUIDE, agent_setup_instructions


def test_all_locales_are_packaged_and_validate():
    assert set(LANGUAGES) == {"en", "zh-CN", "de", "es", "fr", "ja", "ko", "pt-BR"}
    assert set(WORDINGS) == {"plain", "themed"}
    validate_resources()
    for language in LANGUAGES:
        resource = files("ironvellum_local_connector").joinpath("locales", language + ".json")
        assert set(json.loads(resource.read_text(encoding="utf-8"))) == set(WORDINGS)
        for wording in WORDINGS:
            copy = load_copy(language, wording)
            assert "12" in copy("coverage", workouts=12, sets=180, saved=4)
            assert GUIDE in copy("agent_request")


def test_system_default_and_explicit_overrides(monkeypatch):
    monkeypatch.setattr("locale.getlocale", lambda: ("pt_BR", "UTF-8"))
    assert load_copy()("title") == load_copy("pt-BR", "plain")("title")
    assert load_copy("en", "plain")("agent_request") == agent_setup_instructions()
    assert load_copy("en", "themed")("drafts") != load_copy("en", "plain")("drafts")
    monkeypatch.setattr("locale.getlocale", lambda: ("Japanese_Japan", "932"))
    assert load_copy()("title") == load_copy("ja")("title")
    monkeypatch.setattr("locale.getlocale", lambda: ("zh_TW", "UTF-8"))
    assert load_copy()("title") == load_copy("en")("title")
    monkeypatch.setattr("locale.getlocale", lambda: ("nl_NL", "UTF-8"))
    assert load_copy()("title") == load_copy("en")("title")


def test_cli_forwards_explicit_overrides(monkeypatch, tmp_path):
    import sys
    from ironvellum_local_connector import __main__, desktop
    path = tmp_path / "settings.json"
    monkeypatch.setattr(sys, "argv", ["connector", "desktop", "--settings", str(path),
                                       "--language", "ja", "--wording", "themed"])
    args = []
    monkeypatch.setattr(desktop, "run", lambda *values: args.extend(values) or 0)
    assert __main__.main() == 0
    assert args == [path, "ja", "themed"]
