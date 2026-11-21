import asyncio
import base64
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from mcp import Client, StdioServerParameters
from mcp.types import ImageContent, TextContent

from ironvellum_mcp.adb import DevelopmentError


ROOT = Path(__file__).resolve().parents[3]
SERIAL = "emulator-5554"
PRIVATE = "synthetic-private-data"
PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jK/oAAAAASUVORK5CYII="
)
PROTO_MODES = (("auto", "2026-07-28"), ("legacy", "2025-11-25"))


class FakeDevelopmentTools:
    def list_devices(self):
        return {"devices": [{"serial": SERIAL, "state": "device"},
                            {"serial": "test-phone", "state": "unauthorized"}]}

    def get_app_status(self, serial):
        return {
            "serial": serial, "userId": 0, "installed": True,
            "app": {"package": "com.ironvellum.app", "versionName": "1.0",
                    "versionCode": 1, "debuggable": True},
            "foreground": True, "activity": ".MainActivity", "pid": 142,
            "bridgeAvailable": True, "activeWorkout": None,
        }

    def capture_screen(self, serial):
        if serial == "crash":
            raise RuntimeError(PRIVATE)
        if serial != SERIAL:
            raise DevelopmentError("device_not_found", "The selected ADB serial is absent")
        return PNG

    def inspect_screen(self, serial):
        return {"serial": serial, "activity": ".MainActivity", "nodes": [], "truncated": False}

    def preview_notifications(self, serial, action="show", scenario="all"):
        ids = [0, 1, 2, 3, 4, 5] if scenario == "all" else [5]
        submitted = ids if action == "show" else []
        result = {
            "serial": serial, "userId": 0, "action": action, "scenario": scenario,
            "requestedIds": ids, "submittedIds": submitted, "blocked": [],
            "observedIds": submitted,
        }
        if serial == "timeout":
            raise DevelopmentError(
                "preview_delivery_timeout", "ADB could not confirm the preview change within three seconds",
                details={**result, "observedIds": [], "missingIds": submitted,
                         "observationAvailable": True},
            )
        return result

    def get_diagnostics(self, serial, limit=100):
        return {
            "serial": serial, "userId": 0, "running": True, "pid": 142,
            "lines": ["Sample redacted diagnostic"] * min(limit, 200), "truncated": False,
        }


class ServerProtocolTest(unittest.IsolatedAsyncioTestCase):
    def fixture_client(self, mode, messages):
        async def on_message(message):
            messages.append(message)

        parameters = StdioServerParameters(command=sys.executable, args=[str(Path(__file__).resolve())])
        return Client(parameters, mode=mode, message_handler=on_message, read_timeout_seconds=5)

    def check_schemas(self, tools):
        by_name = {tool.name: tool for tool in tools}
        expected = {
            "list_devices": set(),
            "get_app_status": {"serial"},
            "capture_screen": {"serial"},
            "inspect_screen": {"serial"},
            "preview_notifications": {"serial", "action", "scenario"},
            "get_diagnostics": {"serial", "limit"},
        }
        self.assertEqual(len(tools), 6)
        self.assertEqual(set(by_name), set(expected))
        for name, properties in expected.items():
            tool = by_name[name]
            schema = tool.input_schema
            self.assertEqual(schema["type"], "object")
            self.assertEqual(set(schema.get("properties", ())), properties)
            self.assertEqual(schema.get("required", []), ["serial"] if properties else [])
            self.assertEqual(tool.annotations.read_only_hint, name != "preview_notifications")
            self.assertFalse(tool.annotations.open_world_hint)
            if "serial" in properties:
                serial = schema["properties"]["serial"]
                self.assertEqual(serial["type"], "string")
                self.assertEqual(serial["maxLength"], 128)
            if name == "capture_screen":
                self.assertIn("overlays", tool.description)
                self.assertIsNone(tool.output_schema)
            else:
                self.assertEqual(tool.output_schema["type"], "object")
        preview = by_name["preview_notifications"]
        self.assertTrue(preview.annotations.destructive_hint)
        self.assertFalse(preview.annotations.idempotent_hint)
        inputs = preview.input_schema["properties"]
        self.assertEqual(set(inputs["action"]["enum"]), {"show", "clear"})
        self.assertEqual(inputs["action"]["default"], "show")
        self.assertEqual(set(inputs["scenario"]["enum"]),
                         {"all", "accepted", "comment", "reaction", "group_goal", "batch", "rest"})
        self.assertEqual(inputs["scenario"]["default"], "all")
        limit = by_name["get_diagnostics"].input_schema["properties"]["limit"]
        self.assertEqual(limit["default"], 100)
        self.assertEqual(limit["minimum"], 1)

    def read_data(self, result):
        self.assertFalse(result.is_error)
        self.assertEqual(len(result.content), 1)
        self.assertIsInstance(result.content[0], TextContent)
        self.assertEqual(json.loads(result.content[0].text), result.structured_content)
        return result.structured_content

    async def test_schemas_and_successful_results_in_both_protocol_eras(self):
        for mode, version in PROTO_MODES:
            with self.subTest(mode=mode):
                messages = []
                async with self.fixture_client(mode, messages) as client:
                    self.assertEqual(client.protocol_version, version)
                    self.check_schemas((await client.list_tools()).tools)
                    devices = self.read_data(await client.call_tool("list_devices"))
                    self.assertEqual(devices, {"devices": [
                        {"serial": SERIAL, "state": "device"},
                        {"serial": "test-phone", "state": "unauthorized"},
                    ]})
                    args = {"serial": SERIAL}
                    status = self.read_data(await client.call_tool("get_app_status", args))
                    self.assertEqual(status["serial"], SERIAL)
                    self.assertTrue(status["bridgeAvailable"])
                    self.assertIsNone(status["activeWorkout"])
                    screen = self.read_data(await client.call_tool("inspect_screen", args))
                    self.assertEqual(screen, {"serial": SERIAL, "activity": ".MainActivity",
                                              "nodes": [], "truncated": False})
                    preview = self.read_data(await client.call_tool("preview_notifications", args))
                    self.assertEqual((preview["action"], preview["scenario"]), ("show", "all"))
                    self.assertEqual(preview["observedIds"], [0, 1, 2, 3, 4, 5])
                    selected = self.read_data(await client.call_tool(
                        "preview_notifications", {**args, "action": "show", "scenario": "accepted"}
                    ))
                    self.assertEqual(selected["observedIds"], [5])
                    cleared = self.read_data(await client.call_tool(
                        "preview_notifications", {**args, "action": "clear"}
                    ))
                    self.assertEqual(cleared["observedIds"], [])
                    diagnostics = self.read_data(await client.call_tool("get_diagnostics", args))
                    self.assertEqual(len(diagnostics["lines"]), 100)
                    diagnostics = self.read_data(await client.call_tool("get_diagnostics", {**args, "limit": 2}))
                    self.assertEqual(diagnostics["lines"], ["Sample redacted diagnostic"] * 2)
                    capture = await client.call_tool("capture_screen", args)
                    self.assertFalse(capture.is_error)
                    self.assertIsNone(capture.structured_content)
                    self.assertEqual(len(capture.content), 1)
                    image = capture.content[0]
                    self.assertIsInstance(image, ImageContent)
                    self.assertEqual(image.mime_type, "image/png")
                    self.assertEqual(base64.b64decode(image.data, validate=True), PNG)
                self.assertEqual(messages, [])

    async def test_errors_and_rejected_arguments_remain_safe_in_both_protocol_eras(self):
        for mode, _ in PROTO_MODES:
            with self.subTest(mode=mode):
                messages = []
                async with self.fixture_client(mode, messages) as client:
                    await client.list_tools()
                    for name, args in (
                        ("get_app_status", {}),
                        ("get_app_status", {"serial": {"token": PRIVATE}}),
                        ("capture_screen", {"serial": f"-{PRIVATE}"}),
                        ("capture_screen", {"serial": "x" * 129}),
                        ("inspect_screen", {"serial": "emulator;5554"}),
                        ("preview_notifications", {"serial": SERIAL, "scenario": PRIVATE}),
                        ("get_diagnostics", {"serial": SERIAL, "limit": True}),
                        ("get_diagnostics", {"serial": SERIAL, "limit": "1"}),
                        ("get_diagnostics", {"serial": SERIAL, "limit": 0}),
                    ):
                        with self.subTest(name=name, args=args):
                            outcome = await client.call_tool(name, args)
                            self.assertTrue(outcome.is_error)
                            self.assertNotIn(PRIVATE, outcome.model_dump_json())
                    timeout = await client.call_tool(
                        "preview_notifications", {"serial": "timeout", "scenario": "accepted"}
                    )
                    self.assertTrue(timeout.is_error)
                    error = timeout.structured_content["error"]
                    self.assertEqual(error["code"], "preview_delivery_timeout")
                    self.assertEqual(error["details"]["requestedIds"], [5])
                    self.assertEqual(error["details"]["missingIds"], [5])
                    self.assertIn("preview_delivery_timeout", timeout.content[0].text)
                    failed = await client.call_tool("capture_screen", {"serial": "absent"})
                    self.assertTrue(failed.is_error)
                    self.assertEqual(failed.structured_content["error"]["code"], "device_not_found")
                    crashed = await client.call_tool("capture_screen", {"serial": "crash"})
                    self.assertTrue(crashed.is_error)
                    self.assertNotIn(PRIVATE, crashed.model_dump_json())
                self.assertEqual(messages, [])

    async def test_cli_initializes_and_lists_without_adb_from_another_directory(self):
        messages = []

        async def on_message(message):
            messages.append(message)

        with tempfile.TemporaryDirectory() as folder:
            parameters = StdioServerParameters(
                command=sys.executable,
                args=["-m", "ironvellum_mcp", "--repo-root", str(ROOT)],
                env={"IRONVELLUM_ADB": str(Path(folder) / "missing-adb")},
                cwd=folder,
            )
            for mode, version in PROTO_MODES:
                with self.subTest(mode=mode):
                    async with Client(parameters, mode=mode, message_handler=on_message,
                                      read_timeout_seconds=5) as client:
                        self.assertEqual(client.protocol_version, version)
                        self.check_schemas((await client.list_tools()).tools)
        self.assertEqual(messages, [])

    async def test_cli_reports_invalid_root_on_stderr(self):
        with tempfile.TemporaryDirectory() as folder:
            result = await asyncio.to_thread(
                subprocess.run,
                [sys.executable, "-m", "ironvellum_mcp", "--repo-root", folder],
                capture_output=True, text=True, timeout=5,
            )
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout, "")
        self.assertIn("Ironvellum checkout", result.stderr)


if __name__ == "__main__":
    from ironvellum_mcp.server import create_server

    create_server(FakeDevelopmentTools()).run(transport="stdio")
