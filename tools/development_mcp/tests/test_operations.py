import base64
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch
from uuid import UUID

from ironvellum_mcp.adb import AdbClient, DevelopmentError, _lock_module
from ironvellum_mcp.operations import DevelopmentTools
from test_adb import DEVICES, FakeRunner, ROOT, SERIAL


PKG = "com.ironvellum.app"
NONCE = "0123456789abcdef0123456789abcdef"
FIXTURES = json.loads((Path(__file__).resolve().parents[1] / "protocol-fixtures.json").read_text())
ACTIVITY = f"  topResumedActivity=ActivityRecord{{4865 u10 {PKG}/.MainActivity t21}}\n".encode()
PACKAGE = f"package:{PKG} versionCode:1 uid:1010234\n".encode()
PROCESS = f"  UID   PID NAME\n10234 43 {PKG}\n1010234 142 {PKG}\n".encode()
PNG = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jK/oAAAAASUVORK5CYII=")


def bridge(value):
    return b"Broadcast completed: result=0, data=\"IVMCP1:" + base64.b64encode(json.dumps(value).encode()) + b"\"\n"


class OperationTest(unittest.TestCase):
    def setUp(self):
        self.lock = _lock_module(ROOT)
        directory = self.enterContext(tempfile.TemporaryDirectory())
        self.enterContext(patch.object(self.lock, "LOCK", str(Path(directory) / "emulator.lock")))
        self.runner = FakeRunner(self)
        self.adb = AdbClient(ROOT, sys.executable, runner=self.runner)
        self.tools = DevelopmentTools(self.adb)
        nonce = patch("ironvellum_mcp.operations.uuid4", return_value=UUID(hex=NONCE))
        nonce.start()
        self.addCleanup(nonce.stop)
        self.addCleanup(lambda: self.assertFalse(self.runner.pending))

    def cmd(self, *args, stdout=b"", **kwargs):
        self.runner.expect(("-s", SERIAL, *args), stdout, **kwargs)

    def begin(self):
        self.runner.expect(("devices", "-l"), DEVICES)
        self.cmd("shell", "am", "get-current-user", stdout=b"10\n")

    def foreground(self, stdout=ACTIVITY):
        self.cmd("shell", "dumpsys", "activity", "activities", stdout=stdout)

    def package(self, stdout=PACKAGE):
        self.cmd("shell", "cmd", "package", "list", "packages", "--user", "10", "-U", "--show-versioncode", PKG, stdout=stdout)

    def process(self, stdout=PROCESS):
        self.cmd("shell", "ps", "-A", "-o", "UID,PID,NAME", stdout=stdout)

    def status(self, stdout, *, process=PROCESS):
        self.begin()
        self.package()
        self.cmd("shell", "dumpsys", "package", PKG, stdout=b"    versionName=1.0.0\n    pkgFlags=[ DEBUGGABLE HAS_CODE ]\n")
        self.foreground()
        self.process(process)
        if stdout is not None:
            self.cmd("shell", "am", "broadcast", "--user", "10", "--receiver-registered-only",
                     "-p", PKG, "-a", f"{PKG}.debug.READ_STATUS", "--es", "request_id", NONCE,
                     stdout=stdout)

    def preview(self, action, scenario, reply):
        self.begin()
        self.cmd("shell", "am", "broadcast", "--user", "10", "-n", f"{PKG}/.debug.NotificationPreviewReceiver",
                 "-a", f"{PKG}.debug.{action.upper()}_NOTIFICATIONS", "--es", "scenario", scenario,
                 "--es", "request_id", NONCE, stdout=bridge(reply))

    def keys(self, stdout, **kwargs):
        self.cmd("shell", "cmd", "notification", "list", stdout=stdout, **kwargs)

    def test_status_allowlists_live_workout_and_metadata(self):
        payload = dict(FIXTURES["status"], account="private")
        payload["app"] = dict(payload["app"], token="private")
        payload["activeWorkout"] = dict(payload["activeWorkout"], notes="private")
        self.status(bridge(payload))
        status = self.tools.get_app_status(SERIAL)
        self.assertTrue(status["installed"])
        self.assertTrue(status["foreground"])
        self.assertEqual(status["activity"], ".MainActivity")
        self.assertEqual(status["pid"], 142)
        self.assertTrue(status["bridgeAvailable"])
        self.assertEqual(status["app"], FIXTURES["status"]["app"])
        self.assertEqual(status["activeWorkout"], FIXTURES["status"]["activeWorkout"])
        self.assertNotIn("private", json.dumps(status))

    def test_no_workout_is_distinct_from_unavailable_bridge_or_process(self):
        for stdout, process, available in (
            (bridge(FIXTURES["status_no_workout"]), PROCESS, True),
            (b"Broadcast completed: result=0\n", PROCESS, False),
            (bridge(FIXTURES["status_error"]), PROCESS, False),
            (None, f"UID PID NAME\n10234 43 {PKG}\n".encode(), False),
        ):
            with self.subTest(stdout=stdout):
                self.status(stdout, process=process)
                status = self.tools.get_app_status(SERIAL)
                self.assertEqual(status["bridgeAvailable"], available)
                self.assertEqual("activeWorkout" in status, available)
                if available:
                    self.assertIsNone(status["activeWorkout"])

    def test_status_rejects_invalid_bridge_replies(self):
        for stdout in (
            bridge(dict(FIXTURES["status"], requestId="1" * 32)),
            bridge(dict(FIXTURES["status"], schemaVersion=True)),
            bridge(dict(FIXTURES["status"], activeWorkout=dict(FIXTURES["status"]["activeWorkout"], label="\ud800"))),
            bridge(dict(FIXTURES["status"], ignored=float("nan"))),
            bridge(FIXTURES["status"]) * 2,
            b'Broadcast completed: result=0, data="IVMCP1:???"\n',
            b'Broadcast completed: result=0, data="IVMCP1:ew=="\n',
        ):
            with self.subTest(stdout=stdout):
                self.status(stdout)
                with self.assertRaises(DevelopmentError) as error:
                    self.tools.get_app_status(SERIAL)
                self.assertEqual(error.exception.code, "invalid_bridge_reply")

    def test_foreground_rejects_history_lookalikes_wrong_user_and_ambiguity(self):
        for stdout in (
            b"  Hist #0: " + ACTIVITY.split(b"=", 1)[1],
            ACTIVITY.replace(PKG.encode(), f"{PKG}.other".encode()),
            ACTIVITY.replace(b"u10", b"u0"),
            ACTIVITY + ACTIVITY.replace(PKG.encode(), b"vendor.other"),
            b"Display #1 (activities from top to bottom):\n" + ACTIVITY,
        ):
            with self.subTest(stdout=stdout):
                self.begin()
                self.foreground(stdout)
                with self.assertRaises(DevelopmentError) as error:
                    self.tools.capture_screen(SERIAL)
                self.assertEqual(error.exception.code, "app_not_foreground")

    def test_screenshot_checks_before_and_after_and_can_include_overlay(self):
        self.begin()
        self.foreground()
        self.cmd("exec-out", "screencap", "-p", stdout=PNG)
        self.foreground(ACTIVITY + b"  System overlay present\n")
        self.assertEqual(self.tools.capture_screen(SERIAL), PNG)
        self.assertEqual(self.runner.calls[-2][2], 16 * 1024 * 1024)

    def test_android_15_resumed_summary_can_identify_the_default_display(self):
        foreground = (
            b"Display #0 (activities from top to bottom):\n"
            b"  Resumed activities in task display areas (from top to bottom):\n"
            + ACTIVITY.replace(b"topResumedActivity=", b"Resumed: ")
        )
        self.begin()
        self.foreground(foreground)
        self.cmd("exec-out", "screencap", "-p", stdout=PNG)
        self.foreground(foreground)
        self.assertEqual(self.tools.capture_screen(SERIAL), PNG)

    def test_agreeing_foreground_fields_allow_default_display_capture(self):
        foreground = (
            b"Display #0 (activities from top to bottom):\n"
            + ACTIVITY + ACTIVITY.replace(b"topResumedActivity=", b"Resumed: ")
            + ACTIVITY.replace(b"topResumedActivity=", b"mResumedActivity: ")
        )
        self.begin()
        self.foreground(foreground)
        self.cmd("exec-out", "screencap", "-p", stdout=PNG)
        self.foreground(foreground)
        self.assertEqual(self.tools.capture_screen(SERIAL), PNG)

    def test_conflicting_foreground_fields_prevent_capture(self):
        default = b"Display #0 (activities from top to bottom):\n" + ACTIVITY
        summary = ACTIVITY.replace(b"topResumedActivity=", b"Resumed: ")
        for conflict in (
            default + summary.replace(PKG.encode(), b"vendor.other"),
            default + summary.replace(b" u10 ", b" u11 "),
            default + b"Display #1 (activities from top to bottom):\n" + summary,
        ):
            for capture in (self.tools.capture_screen, self.tools.inspect_screen):
                with self.subTest(conflict=conflict, capture=capture.__name__):
                    self.begin()
                    self.foreground(conflict)
                    with self.assertRaises(DevelopmentError) as error:
                        capture(SERIAL)
                    self.assertEqual(error.exception.code, "app_not_foreground")

    def test_conflicting_foreground_fields_discard_captures_and_clean_up(self):
        conflict = ACTIVITY + ACTIVITY.replace(
            b"topResumedActivity=", b"Resumed: "
        ).replace(PKG.encode(), b"vendor.other")
        self.begin()
        self.foreground()
        self.cmd("exec-out", "screencap", "-p", stdout=PNG)
        self.foreground(conflict)
        with self.assertRaises(DevelopmentError) as error:
            self.tools.capture_screen(SERIAL)
        self.assertEqual(error.exception.code, "app_not_foreground")

        xml = f'<hierarchy><node package="{PKG}" text="private capture"/></hierarchy>'.encode()
        self.dump(xml, after=conflict)
        with self.assertRaises(DevelopmentError) as error:
            self.tools.inspect_screen(SERIAL)
        self.assertEqual(error.exception.code, "app_not_foreground")

    def test_screenshot_discards_another_apps_pixels_after_a_race(self):
        self.begin()
        self.foreground()
        self.cmd("exec-out", "screencap", "-p", stdout=PNG)
        self.foreground(ACTIVITY.replace(PKG.encode(), b"vendor.other"))
        with self.assertRaises(DevelopmentError):
            self.tools.capture_screen(SERIAL)

    def test_dump_failure_does_not_read_a_stale_file_or_hide_primary_error(self):
        self.begin()
        self.foreground()
        path = f"/data/local/tmp/ironvellum-mcp-{NONCE}.xml"
        self.cmd("shell", "uiautomator", "dump", path, stdout=b"ERROR: could not get idle state\n")
        self.cmd("shell", "rm", "-f", path, code=1)
        with self.assertRaises(DevelopmentError) as error:
            self.tools.inspect_screen(SERIAL)
        self.assertEqual(error.exception.code, "ui_dump_failed")

    def dump(self, xml, after=ACTIVITY):
        self.begin()
        self.foreground()
        path = f"/data/local/tmp/ironvellum-mcp-{NONCE}.xml"
        self.cmd("shell", "uiautomator", "dump", path, stdout=f"UI hierchary dumped to: {path}\n".encode())
        self.cmd("exec-out", "cat", path, stdout=xml)
        self.foreground(after)
        self.cmd("shell", "rm", "-f", path)

    def test_inspection_filters_own_nodes_and_redacts_password_fields(self):
        xml = f"""<hierarchy><node package="android" text="other">
          <node package="{PKG}" text="Heading" content-desc="label">
            <node package="android" text="other child" />
            <node package="{PKG}" password="true" text="secret" content-desc="secret desc" />
          </node></node></hierarchy>"""
        self.dump(xml.encode())
        result = self.tools.inspect_screen(SERIAL)
        self.assertEqual([node["text"] for node in result["nodes"]], ["Heading", "[REDACTED]"])
        self.assertNotIn("secret", json.dumps(result))
        self.assertNotIn("other", json.dumps(result))
        self.assertEqual(self.runner.calls[-3][2], 2 * 1024 * 1024)

    def test_inspection_rejects_malformed_or_declared_entity_xml_and_foreground_race(self):
        for xml, after in (
            (b"<hierarchy><node/>", ACTIVITY),
            (b'<!DOCTYPE hierarchy [<!ENTITY value "secret">]><hierarchy/>', ACTIVITY),
            (b"<hierarchy/>", ACTIVITY.replace(PKG.encode(), b"vendor.other")),
        ):
            with self.subTest(xml=xml, after=after):
                self.dump(xml, after)
                with self.assertRaises(DevelopmentError):
                    self.tools.inspect_screen(SERIAL)

    def test_inspection_caps_nodes(self):
        self.dump(b"<hierarchy>" + f'<node package="{PKG}"/>'.encode() * 2001 + b"</hierarchy>")
        result = self.tools.inspect_screen(SERIAL)
        self.assertEqual(len(result["nodes"]), 2000)
        self.assertTrue(result["truncated"])

    def test_preview_validates_input_before_contacting_device(self):
        for action, scenario in (("post", "all"), ("show", "all;id"), ("clear", "accepted")):
            with self.subTest(action=action, scenario=scenario), self.assertRaises(DevelopmentError):
                self.tools.preview_notifications(SERIAL, action, scenario)

    def test_preview_returns_only_current_users_tagged_keys_and_blocks(self):
        reply = dict(FIXTURES["show"], requestedIds=list(range(6)), scenario="all", submittedIds=[0, 1, 2, 3, 4],
                     blocked=FIXTURES["blocked"]["blocked"])
        self.preview("show", "all", reply)
        keys = [f"10|{PKG}|{id}|notification-preview|1010234" for id in range(5)]
        keys += [
            f"0|{PKG}|5|notification-preview|10234",
            f"10|{PKG}.other|5|notification-preview|1010200",
            f"10|{PKG}|5|notification-preview-other|1010234",
            f"10|{PKG}|7|notification-preview|1010234",
        ]
        self.keys("\n".join(keys).encode())
        result = self.tools.preview_notifications(SERIAL)
        self.assertEqual(result["requestedIds"], list(range(6)))
        self.assertEqual(result["submittedIds"], [0, 1, 2, 3, 4])
        self.assertEqual(result["observedIds"], [0, 1, 2, 3, 4])
        self.assertEqual(result["blocked"], FIXTURES["blocked"]["blocked"])
        self.assertLessEqual(self.runner.calls[-1][1], 3)

    def test_clear_confirms_removal_for_current_user(self):
        self.preview("clear", "all", FIXTURES["clear"])
        self.keys(f"10|{PKG}|0|notification-preview|1010234\n".encode())
        self.keys(f"0|{PKG}|0|notification-preview|10234\n".encode())
        with patch("ironvellum_mcp.operations.time.sleep"):
            result = self.tools.preview_notifications(SERIAL, action="clear")
        self.assertEqual(result["observedIds"], [])

    def test_preview_does_not_accept_observation_after_the_deadline(self):
        self.preview("show", "accepted", FIXTURES["show"])
        self.keys(f"10|{PKG}|5|notification-preview|1010234\n".encode())
        with patch("ironvellum_mcp.operations.time", wraps=time) as clock:
            clock.monotonic.side_effect = [10, 10.1, 13.1]
            with self.assertRaises(DevelopmentError) as error:
                self.tools.preview_notifications(SERIAL, scenario="accepted")
        self.assertEqual(error.exception.code, "preview_delivery_timeout")
        self.assertEqual(error.exception.details["missingIds"], [5])

    def test_clear_probe_timeout_marks_observation_unavailable(self):
        self.preview("clear", "all", FIXTURES["clear"])
        self.keys(b"", error=subprocess.TimeoutExpired("adb", 3))
        with self.assertRaises(DevelopmentError) as error:
            self.tools.preview_notifications(SERIAL, action="clear")
        self.assertEqual(error.exception.code, "preview_delivery_timeout")
        self.assertFalse(error.exception.details["observationAvailable"])

    def test_preview_timeout_carries_missing_or_remaining_ids(self):
        for action, scenario, fixture, stdout, expected in (
            ("show", "accepted", "show", b"", {"missingIds": [5]}),
            ("clear", "all", "clear", f"10|{PKG}|3|notification-preview|1010234\n".encode(), {"remainingIds": [3]}),
        ):
            with self.subTest(action=action):
                self.preview(action, scenario, FIXTURES[fixture])
                self.keys(stdout)
                with patch("ironvellum_mcp.operations.time", wraps=time) as clock:
                    clock.monotonic.side_effect = [10, 10.1, 12.99, 13]
                    with self.assertRaises(DevelopmentError) as error:
                        self.tools.preview_notifications(SERIAL, action, scenario)
                self.assertEqual(error.exception.code, "preview_delivery_timeout")
                for key, value in expected.items():
                    self.assertEqual(error.exception.details[key], value)

    def test_unsupported_notification_list_is_not_successful_clear(self):
        self.preview("clear", "all", FIXTURES["clear"])
        self.keys(b"Unknown command: list")
        with self.assertRaises(DevelopmentError) as error:
            self.tools.preview_notifications(SERIAL, action="clear")
        self.assertEqual(error.exception.code, "notification_observation_unavailable")

    def test_diagnostics_redacts_retained_tokens_and_multiline_credentials(self):
        def compact(header, *segments):
            return b".".join((base64.urlsafe_b64encode(header).rstrip(b"="), *segments))

        formatted_jwt = compact(
            b'{ \n  "typ": "JWT", "alg": "HS256" }', b"eyJzdWIiOiIxMjMifQ", b"signature",
        )
        encrypted_jwt = compact(
            b' { "alg":"dir","enc":"A256GCM" }', b"", b"iv", b"ciphertext", b"tag",
        )
        self.begin()
        self.package()
        self.process()
        self.cmd("logcat", "--pid=142", "--uid=1010234", "-d", "-t", "100", "-v", "brief", stdout=(
            b"I/Tag(  43): profile secret\n"
            b'I/Tag( 142): sending Bearer bearer-token now\n'
            b'I/Tag( 142): {"refresh_token":"first half\n'
            b'I/Tag( 142): second half"}\n'
            b'I/Tag( 142): password=some secret\n'
            b'I/Tag( 142): signed ' + formatted_jwt + b' inspected\n'
            b'I/Tag( 142): encrypted ' + encrypted_jwt + b' inspected\n'
            b'I/Tag( 142): com.ironvellum.app startup complete\n'
        ))
        self.process()
        result = self.tools.get_diagnostics(SERIAL)
        self.assertEqual(result["pid"], 142)
        self.assertFalse(result["truncated"])
        logs = "\n".join(result["lines"])
        for secret in ("profile secret", "bearer-token", "first half", "second half",
                       "some secret", formatted_jwt.decode(), encrypted_jwt.decode()):
            self.assertNotIn(secret, logs)
        self.assertIn("sending Bearer [REDACTED] now", result["lines"])
        self.assertIn("signed [REDACTED] inspected", result["lines"])
        self.assertIn("encrypted [REDACTED] inspected", result["lines"])
        self.assertIn("com.ironvellum.app startup complete", result["lines"])

    def test_diagnostics_clamps_line_limit_and_truncates_utf8_tail(self):
        self.begin()
        self.package()
        self.process()
        self.cmd("logcat", "--pid=142", "--uid=1010234", "-d", "-t", "200", "-v", "brief", stdout=(
            b"I/Tag( 142): " + "é".encode() * 20000 + b" retained\n"
        ))
        self.process()
        result = self.tools.get_diagnostics(SERIAL, limit=500)
        self.assertTrue(result["truncated"])
        logs = "\n".join(result["lines"])
        self.assertLessEqual(len(logs.encode()), 32 * 1024)
        self.assertTrue(logs.endswith("é retained"))

    def test_diagnostics_absence_pid_ambiguity_rotation_and_logcat_failure(self):
        for process, logcat, after, expected in (
            (f"UID PID NAME\n10234 43 {PKG}\n".encode(), None, None, None),
            (PROCESS + f"1010234 143 {PKG}\n".encode(), None, None, "process_unavailable"),
            (PROCESS, {}, PROCESS.replace(b"142", b"143"), "process_changed"),
            (PROCESS, {"code": 1, "stderr": b"private error"}, None, "adb_failed"),
        ):
            with self.subTest(expected=expected):
                self.begin()
                self.package()
                self.process(process)
                if logcat is not None:
                    self.cmd("logcat", "--pid=142", "--uid=1010234", "-d", "-t", "100", "-v", "brief", **logcat)
                if after is not None:
                    self.process(after)
                if expected is None:
                    self.assertFalse(self.tools.get_diagnostics(SERIAL)["running"])
                else:
                    with self.assertRaises(DevelopmentError) as error:
                        self.tools.get_diagnostics(SERIAL)
                    self.assertEqual(error.exception.code, expected)
                    self.assertNotIn("private", str(error.exception))

    def test_emulator_failure_releases_one_shared_lease_and_uses_stderr_logger(self):
        lock = self.lock
        self.runner.expect(("devices", "-l"), DEVICES)
        self.runner.expect(("-s", "emulator-5554", "shell", "am", "get-current-user"), code=1)
        stdout, stderr = StringIO(), StringIO()
        with patch.object(lock, "emulator_lock", wraps=lock.emulator_lock) as lease:
            Path(lock.LOCK).mkdir()
            stale = time.time() - lock.BARE_STALE - 10
            os.utime(lock.LOCK, (stale, stale))
            with redirect_stdout(stdout), redirect_stderr(stderr), self.assertRaises(DevelopmentError):
                self.tools.capture_screen("emulator-5554")
            self.assertEqual(lease.call_count, 1)
            self.assertEqual(lease.call_args.kwargs["timeout_s"], 30)
            self.assertFalse(Path(lock.LOCK).exists())
        self.assertEqual(stdout.getvalue(), "")
        self.assertIn("emulator lock was stale", stderr.getvalue())
