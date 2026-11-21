"""Ironvellum's six development operations and the debug bridge wire contract."""

import base64
import binascii
import json
import re
import time
from uuid import UUID, uuid4
from xml.etree import ElementTree

from .adb import AdbClient, DevelopmentError, PACKAGE, PREVIEW_ACTIONS, SCENARIO_IDS, _Device


_BRIDGE_ERRORS = {"invalid_request", "unsupported_action", "unknown_scenario", "status_unavailable"}
_BLOCK_REASONS = {"permission_denied", "channel_blocked", "app_notifications_disabled"}
_CHANNELS = {"allies", "rest", "rest_sound", "rest_chime", "rest_silent"}
_PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
_PNG_END = b"\0\0\0\0IEND\xaeB\x60\x82"
_LOG_BYTES = 32 * 1024
_NODE_LIMIT = 2000
_LOG_LINE = re.compile(r"([VDIWEFS]/[^\r\n]*?\(\s*([0-9]+)\): )([^\r\n]*)")
_CREDENTIAL = re.compile(
    r"""(?ix)
    \b(?:authorization|proxy[-_]authorization|password|passwd|passphrase|secret|
        (?:access|refresh|id|auth|session)[_-]?token|client[_-]?secret|
        api[_-]?key|apikey|token|credential|credentials|cookie|set-cookie)\b
    ["']?[ \t]*[:=][ \t]*
    (?:
        "(?:\\.|[^"\\])*" | '(?:\\.|[^'\\])*' |
        ["'][\s\S]*\Z | \s*[^\r\n]*
    )
    """,
)
_BEARER = re.compile(r"(?i)\bbearer\s+[a-z0-9._~+/=-]+")
# Compact signed and encrypted tokens have three and five base64url segments,
# respectively. An unsecured signature or a direct encryption key can be empty.
_JWT = re.compile(
    r"(?<![A-Za-z0-9_-])([A-Za-z0-9_-]+)\."
    r"(?:[A-Za-z0-9_-]*\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+"
    r"|[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*)(?![A-Za-z0-9_-])"
)


def _invalid_reply() -> DevelopmentError:
    return DevelopmentError("invalid_bridge_reply", "The Ironvellum debug bridge returned an invalid reply")


def _unique_fields(pairs: list[tuple[str, object]]) -> dict:
    fields = dict(pairs)
    if len(pairs) != len(fields):
        raise ValueError("Duplicate JSON field")
    return fields


def _reject_constant(_: str) -> None:
    raise ValueError("Non-JSON numeric constant")


def _bridge_reply(output: bytes, request: UUID) -> dict | None:
    completion = [line for line in output.splitlines() if line.startswith(b"Broadcast completed:")]
    if len(completion) != 1:
        raise _invalid_reply()
    if re.fullmatch(rb"Broadcast completed: result=0", completion[0]):
        return None
    match = re.fullmatch(rb'Broadcast completed: result=0, data="IVMCP1:([^"]+)"', completion[0])
    if not match:
        raise _invalid_reply()
    try:
        raw = base64.b64decode(match[1], validate=True)
        if len(raw) > 32 * 1024:
            raise ValueError("Oversized bridge reply")
        value = json.loads(
            raw.decode("utf-8"), object_pairs_hook=_unique_fields, parse_constant=_reject_constant,
        )
    except (binascii.Error, ValueError, RecursionError):
        raise _invalid_reply() from None
    if (
        not isinstance(value, dict)
        or type(value.get("schemaVersion")) is not int
        or value["schemaVersion"] != 1
        or value.get("requestId") != request.hex
        or type(value.get("ok")) is not bool
    ):
        raise _invalid_reply()
    if not value["ok"]:
        error = value.get("error")
        code = error.get("code") if isinstance(error, dict) else None
        if not isinstance(code, str) or code not in _BRIDGE_ERRORS:
            raise _invalid_reply()
        raise DevelopmentError(code, "The Ironvellum debug bridge could not complete the request")
    return value


def _valid_text(value: object) -> bool:
    return isinstance(value, str) and re.search(r"[\ud800-\udfff]", value) is None


def _status_fields(reply: dict) -> tuple[dict, dict | None]:
    app, workout = reply.get("app"), reply.get("activeWorkout")
    if (
        not isinstance(app, dict)
        or app.get("package") != PACKAGE
        or "versionName" not in app
        or (app["versionName"] is not None and not _valid_text(app["versionName"]))
        or type(app.get("versionCode")) is not int
        or app["versionCode"] < 0
        or app.get("debuggable") is not True
        or "activeWorkout" not in reply
    ):
        raise _invalid_reply()
    fields = ("id", "label", "startedAtMs", "totalSets", "completedSets")
    if workout is not None:
        if (
            not isinstance(workout, dict)
            or not _valid_text(workout.get("label"))
            or any(type(workout.get(field)) is not int for field in fields if field != "label")
            or not 0 <= workout["completedSets"] <= workout["totalSets"]
        ):
            raise _invalid_reply()
        workout = {field: workout[field] for field in fields}
    return {key: app[key] for key in ("package", "versionName", "versionCode", "debuggable")}, workout


def _ids(values: object) -> list[int]:
    if not isinstance(values, list) or any(type(id) is not int or id not in SCENARIO_IDS["all"] for id in values):
        raise _invalid_reply()
    if len(set(values)) != len(values):
        raise _invalid_reply()
    return values


def _preview_fields(reply: dict, action: str, scenario: str) -> dict:
    if (reply.get("operation"), reply.get("action"), reply.get("scenario")) != ("notification_preview", action, scenario):
        raise _invalid_reply()
    requested = _ids(reply.get("requestedIds"))
    submitted = _ids(reply.get("submittedIds"))
    blocked = reply.get("blocked")
    if requested != list(SCENARIO_IDS[scenario]) or not isinstance(blocked, list):
        raise _invalid_reply()
    clean = []
    for entry in blocked:
        if not isinstance(entry, dict):
            raise _invalid_reply()
        id, channel, reason = entry.get("id"), entry.get("channelId"), entry.get("reason")
        if (
            type(id) is not int
            or not isinstance(channel, str) or channel not in _CHANNELS
            or not isinstance(reason, str) or reason not in _BLOCK_REASONS
        ):
            raise _invalid_reply()
        clean.append({"id": id, "channelId": channel, "reason": reason})
    if action == "clear":
        if submitted or clean:
            raise _invalid_reply()
    elif sorted(submitted + [entry["id"] for entry in clean]) != requested:
        raise _invalid_reply()
    return {"action": action, "scenario": scenario, "requestedIds": requested,
            "submittedIds": submitted, "blocked": clean}


def _ui_nodes(xml: bytes) -> dict:
    # Require UTF-8 before the declaration check so an alternate XML encoding
    # cannot hide a DTD or entity marker from the raw byte search.
    try:
        text = xml.decode("utf-8")
        if "\x00" in text or re.search(r"<!\s*(?:DOCTYPE|ENTITY)\b", text, re.IGNORECASE):
            raise ValueError("XML declarations are disallowed")
        root = ElementTree.fromstring(text)
    except (ElementTree.ParseError, ValueError):
        raise DevelopmentError("invalid_ui_dump", "The UI hierarchy is invalid") from None
    if root.tag != "hierarchy":
        raise DevelopmentError("invalid_ui_dump", "The UI hierarchy is invalid")
    nodes = []
    for node in root.iter("node"):
        if node.get("package") != PACKAGE:
            continue
        if len(nodes) == _NODE_LIMIT:
            return {"nodes": nodes, "truncated": True}
        password = node.get("password") == "true"
        nodes.append({
            "text": "[REDACTED]" if password else node.get("text", ""),
            "contentDescription": "[REDACTED]" if password else node.get("content-desc", ""),
            "resourceId": node.get("resource-id", ""),
            "class": node.get("class", ""),
            "bounds": node.get("bounds", ""),
            "clickable": node.get("clickable") == "true",
            "enabled": node.get("enabled") == "true",
            "password": password,
        })
    return {"nodes": nodes, "truncated": False}


def _redact_jwts(text: str) -> str:
    pieces = []
    copied_through = 0
    search_from = 0
    while match := _JWT.search(text, search_from):
        header = match[1]
        # If this is an ordinary dotted prefix, retry at the next segment.
        # A full candidate may overlap a real token that starts there.
        search_from = match.end(1) + 1
        try:
            decoded = base64.urlsafe_b64decode(header + "=" * (-len(header) % 4))
            fields = json.loads(decoded.decode("utf-8"))
        except (ValueError, RecursionError):
            continue
        if isinstance(fields, dict) and isinstance(fields.get("alg"), str):
            pieces.extend((text[copied_through:match.start()], "[REDACTED]"))
            copied_through = match.end()
            search_from = copied_through
    pieces.append(text[copied_through:])
    return "".join(pieces)


def _diagnostic_lines(raw: str, pid: int, limit: int) -> tuple[list[str], bool]:
    # Separate trusted logcat framing from the payload so a quoted credential
    # spanning log records can be removed before handing anything to a client.
    messages = []
    current = None
    for line in raw.splitlines():
        match = _LOG_LINE.fullmatch(line)
        if match:
            current = int(match[2]) == pid
            if current:
                messages.append(match[3])
        elif current and not line.startswith("---------"):
            messages.append(line)
        else:
            current = None
    clean = _CREDENTIAL.sub("[REDACTED]", "\n".join(messages))
    clean = _redact_jwts(_BEARER.sub("Bearer [REDACTED]", clean))
    lines = clean.splitlines()
    tail = "\n".join(lines[-limit:]).encode("utf-8")
    truncated = len(lines) > limit or len(tail) > _LOG_BYTES
    return tail[-_LOG_BYTES:].decode("utf-8", "ignore").splitlines(), truncated


def _require_foreground(device: _Device) -> str:
    activity = device.foreground_activity()
    if activity is None:
        raise DevelopmentError("app_not_foreground", "Ironvellum must be in the foreground for this capture")
    return activity


class DevelopmentTools:
    def __init__(self, adb: AdbClient):
        self._adb = adb

    def list_devices(self) -> dict:
        """List connected ADB devices with their reported connection states."""
        return {"devices": self._adb.list_devices()}

    def get_app_status(self, serial: str) -> dict:
        """Read Ironvellum's version, foreground activity, and any live workout summary."""
        with self._adb.device(serial) as device:
            installed = device.installed_package()
            app = device.package_metadata(installed) if installed else {"package": PACKAGE}
            activity = device.foreground_activity()
            pid = device.process_id(installed) if installed else None
            result = {
                "serial": serial, "userId": device.user, "installed": installed is not None,
                "app": app, "foreground": activity is not None, "activity": activity,
                "pid": pid, "bridgeAvailable": False,
            }
            if pid is None:
                return result
            request = uuid4()
            try:
                reply = _bridge_reply(device.read_status(request), request)
            except DevelopmentError as error:
                if error.code not in {"status_unavailable", "adb_timeout"}:
                    raise
                return result
            if reply is not None:
                result["app"], result["activeWorkout"] = _status_fields(reply)
                result["bridgeAvailable"] = True
            return result

    def capture_screen(self, serial: str) -> bytes:
        """Capture the display as PNG while Ironvellum is foreground, including system overlays."""
        with self._adb.device(serial) as device:
            _require_foreground(device)
            png = device.screenshot()
            _require_foreground(device)
        if not png.startswith(_PNG_SIGNATURE) or not png.endswith(_PNG_END):
            raise DevelopmentError("invalid_screenshot", "ADB did not return a complete PNG capture")
        return png

    def inspect_screen(self, serial: str) -> dict:
        """Read Ironvellum's visible UI nodes with password fields redacted."""
        with self._adb.device(serial) as device:
            _require_foreground(device)
            with device.ui_dump(uuid4()) as xml:
                activity = _require_foreground(device)
                result = _ui_nodes(xml)
        return {"serial": serial, "activity": activity, **result}

    def preview_notifications(self, serial: str, action: str = "show", scenario: str = "all") -> dict:
        """Show selected debug notification previews or clear all debug previews."""
        if (
            not isinstance(action, str) or action not in PREVIEW_ACTIONS
            or not isinstance(scenario, str) or scenario not in SCENARIO_IDS
            or (action == "clear" and scenario != "all")
        ):
            raise DevelopmentError("invalid_preview", "Select a supported preview action and scenario")
        with self._adb.device(serial) as device:
            request = uuid4()
            reply = _bridge_reply(device.preview(action, scenario, request), request)
            if reply is None:
                raise DevelopmentError(
                    "preview_bridge_unavailable", "The preview bridge requires a running debug app with preview support",
                )
            result = {
                "serial": serial, "userId": device.user,
                **_preview_fields(reply, action, scenario), "observedIds": [],
            }
            deadline = time.monotonic() + 3
            submitted = set(result["submittedIds"])
            observation_available = False
            while (remaining := deadline - time.monotonic()) > 0:
                try:
                    observed = device.preview_ids(timeout=remaining)
                except DevelopmentError as error:
                    if error.code != "adb_timeout":
                        raise
                    break
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    break
                observation_available = True
                result["observedIds"] = sorted(observed)
                if (action == "clear" and not observed) or (action == "show" and submitted <= observed):
                    return result
                time.sleep(min(0.15, remaining))
            result["observationAvailable"] = observation_available
            if action == "clear":
                result["remainingIds"] = result["observedIds"]
            else:
                result["missingIds"] = sorted(submitted - set(result["observedIds"]))
            raise DevelopmentError(
                "preview_delivery_timeout", "ADB could not confirm the preview change within three seconds",
                details=result,
            )

    def get_diagnostics(self, serial: str, limit: int = 100) -> dict:
        """Read a bounded, redacted tail from the selected user's current Ironvellum PID."""
        if type(limit) is not int or limit < 1:
            raise DevelopmentError("invalid_diagnostics", "The diagnostic line limit must be a positive integer")
        limit = min(limit, 200)
        with self._adb.device(serial) as device:
            installed = device.installed_package()
            pid = device.process_id(installed) if installed else None
            result = {"serial": serial, "userId": device.user, "running": pid is not None,
                      "pid": pid, "lines": [], "truncated": False}
            if installed is None or pid is None:
                return result
            raw = device.logs(installed, pid, limit)
            if device.process_id(installed) != pid:
                raise DevelopmentError("process_changed", "Ironvellum's process changed during diagnostic capture")
            result["lines"], result["truncated"] = _diagnostic_lines(raw, pid, limit)
            return result
