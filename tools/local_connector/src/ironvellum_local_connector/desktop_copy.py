"""Packaged strings for the standalone desktop window."""
from functools import lru_cache
from importlib.resources import files
import json
import locale
from string import Formatter


LANGUAGES = ("en", "zh-CN", "de", "es", "fr", "ja", "ko", "pt-BR")
WORDINGS = ("plain", "themed")
SYSTEM_LANGUAGES = {
    "en": "en", "english": "en", "de": "de", "german": "de",
    "es": "es", "spanish": "es", "fr": "fr", "french": "fr",
    "ja": "ja", "japanese": "ja", "ko": "ko", "korean": "ko",
    "zh": "zh-CN", "zh-cn": "zh-CN", "chinese-china": "zh-CN",
    "chinese (simplified)-china": "zh-CN", "zh-hans-cn": "zh-CN",
    "pt": "pt-BR", "pt-br": "pt-BR", "portuguese-brazil": "pt-BR",
}


@lru_cache(maxsize=len(LANGUAGES))
def _resource(language):
    path = files(__package__).joinpath("locales", language + ".json")
    return json.loads(path.read_text(encoding="utf-8"))


def _language(system):
    name = (system or "").replace("_", "-").lower()
    if name in SYSTEM_LANGUAGES:
        return SYSTEM_LANGUAGES[name]
    base = SYSTEM_LANGUAGES.get(name.split("-", 1)[0], "en")
    return base if base in ("en", "de", "es", "fr", "ja", "ko") else "en"


def load_copy(language=None, wording="plain"):
    strings = _resource(language or _language(locale.getlocale()[0]))[wording]

    def copy(key, **values):
        return strings[key].format(**values)

    return copy


def validate_resources():
    def signature(strings):
        return {key: sorted((field, spec, conversion) for _, field, spec, conversion in Formatter().parse(value)
                            if field is not None) for key, value in strings.items() if value}

    expected = signature(_resource("en")["plain"])
    for language in LANGUAGES:
        modes = _resource(language)
        if set(modes) != set(WORDINGS) or any(signature(modes[mode]) != expected for mode in WORDINGS):
            raise ValueError(f"Inconsistent desktop resources for {language}")
