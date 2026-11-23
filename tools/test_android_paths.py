"""Portable Android discovery; no real SDK, emulator or phone is accessed."""
from contextlib import chdir
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import device
import gate


class AndroidPathsTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(self.enterContext(tempfile.TemporaryDirectory()))

    def sdk(self, env, platform="linux"):
        return device.sdk_path(env, repo_root=self.root, home=self.root, platform=platform)

    def fake_executable(self, directory, name):
        directory.mkdir(parents=True, exist_ok=True)
        executable = directory / (name + (".exe" if os.name == "nt" else ""))
        executable.touch()
        executable.chmod(0o755)
        return executable

    def import_device(self, sdk, path, **overrides):
        env = dict(os.environ)
        env.pop("IRONVELLUM_ADB", None)
        env.pop("IRONVELLUM_EMULATOR", None)
        env.update(ANDROID_HOME=str(sdk), PATH=str(path) + os.pathsep + env["PATH"], **overrides)
        spec = importlib.util.spec_from_file_location("_test_portable_device", device.__file__)
        module = importlib.util.module_from_spec(spec)
        with patch.dict(os.environ, env, clear=True):
            spec.loader.exec_module(module)
        return module

    def test_sdk_environment_precedes_checkout_and_platform_defaults(self):
        selected = self.root / "custom sdk"
        self.assertEqual(selected, self.sdk({"ANDROID_HOME": str(selected), "ANDROID_SDK_ROOT": "ignored"}))

    def test_legacy_sdk_environment_is_supported(self):
        selected = self.root / "legacy sdk"
        self.assertEqual(selected, self.sdk({"ANDROID_SDK_ROOT": str(selected)}))

    def test_checkout_sdk_decodes_java_property_escapes(self):
        selected = self.root / "sdk with spaces"
        escaped = str(selected).replace("\\", "\\\\").replace(":", "\\:")
        (self.root / "local.properties").write_text("sdk.dir=" + escaped + "\n", encoding="utf-8")
        self.assertEqual(selected, self.sdk({}))

    def test_defaults_follow_current_user_on_each_platform(self):
        self.assertEqual(self.root / "Library/Android/sdk", self.sdk({}, "darwin"))
        self.assertEqual(self.root / "Android/Sdk", self.sdk({}, "linux"))
        app_data = self.root / "app data"
        self.assertEqual(app_data / "Android/Sdk", self.sdk({"LOCALAPPDATA": str(app_data)}, "win32"))

    def test_relative_checkout_sdk_is_based_on_checkout_not_current_directory(self):
        (self.root / "local.properties").write_text("sdk.dir=local-sdk\n", encoding="utf-8")
        self.assertEqual(self.root / "local-sdk", self.sdk({}))

    def test_path_adb_identifies_a_custom_sdk_before_platform_default(self):
        sdk = self.root / "sdk from path"
        adb = sdk / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
        adb.parent.mkdir(parents=True)
        adb.touch()
        with patch("device.shutil.which", return_value=str(adb)):
            self.assertEqual(sdk, self.sdk({}))

    def test_symlinked_path_adb_identifies_the_real_sdk(self):
        sdk = self.root / "real sdk"
        adb = self.fake_executable(sdk / "platform-tools", "adb")
        link = self.root / "bin" / adb.name
        with patch.object(device.shutil, "which", return_value=str(link)), \
                patch.object(Path, "resolve", return_value=adb) as resolve:
            self.assertEqual(sdk, self.sdk({}))
            resolve.assert_called_once()

    def test_explicit_and_sdk_tools_precede_path_tools(self):
        sdk = self.root / "sdk"
        path = self.root / "bin"
        self.fake_executable(path, "adb")
        self.fake_executable(path, "emulator")
        self.fake_executable(sdk / "platform-tools", "adb")
        emulator = self.fake_executable(sdk / "emulator", "emulator")
        override = self.fake_executable(self.root / "override", "adb")
        loaded = self.import_device(sdk, path, IRONVELLUM_ADB=str(override))
        self.assertEqual(override, loaded.ADB)
        self.assertEqual(emulator, loaded.EMULATOR)

    def test_standalone_tools_on_path_are_used_when_sdk_binaries_are_missing(self):
        sdk = self.root / "sdk"
        path = self.root / "bin"
        adb = self.fake_executable(path, "adb")
        emulator = self.fake_executable(path, "emulator")
        loaded = self.import_device(sdk, path)
        self.assertEqual(sdk, loaded.SDK)
        self.assertEqual(adb, loaded.ADB)
        self.assertEqual(emulator, loaded.EMULATOR)

    def test_apk_freshness_uses_checkout_sources_from_another_directory(self):
        repo = self.root / "checkout"
        source = repo / "app/src/main/source.kt"
        source.parent.mkdir(parents=True)
        source.touch()
        apk = repo / "test.apk"
        apk.touch()
        os.utime(apk, (1_700_000_000, 1_700_000_000))
        os.utime(source, (1_700_000_120, 1_700_000_120))
        with patch.object(device, "ROOT", repo), chdir(self.root):
            _, behind = device.apk_age(apk)
        self.assertEqual(120, behind)

    def test_one_installed_avd_is_selected_without_a_machine_specific_name(self):
        self.assertEqual("Disposable_API", device.choose_avd(["Disposable_API"]))

    def test_multiple_avds_require_an_explicit_choice(self):
        with self.assertRaisesRegex(SystemExit, "IRONVELLUM_AVD"):
            device.choose_avd(["First_API", "Second_API"])
        self.assertEqual("Second_API", device.choose_avd(["First_API", "Second_API"], "Second_API"))

    def test_missing_or_stale_avd_configuration_fails_before_boot(self):
        for available, requested in [([], None), (["Installed_API"], "Missing_API")]:
            with self.subTest(available=available, requested=requested):
                with self.assertRaisesRegex(SystemExit, "AVD"):
                    device.choose_avd(available, requested)

    def test_boot_preserves_android_environment_and_uses_requested_port(self):
        avd_home = str(self.root / "avds")
        processes = []

        def spawn(command, **kwargs):
            processes.append((command, kwargs))
            return unittest.mock.Mock(poll=lambda: None)

        replies = [
            subprocess.CompletedProcess([], 0, "Test_API\n", ""),
            subprocess.CompletedProcess([], 0, "Test_API\r\nOK\r\n", ""),
        ]
        with patch.dict(os.environ, {"ANDROID_AVD_HOME": avd_home}, clear=True), \
                patch.object(device, "AVD", None), \
                patch.object(device.subprocess, "run", side_effect=replies), \
                patch.object(device.subprocess, "Popen", side_effect=spawn), \
                patch.object(device, "emulators", return_value=["emulator-5556"]), \
                patch.object(device, "shell_ok", return_value=0.1), \
                patch.object(device, "tune"):
            self.assertEqual("emulator-5556", device.boot(timeout=1, port="5556"))
        command, kwargs = processes[0]
        self.assertEqual("Test_API", command[command.index("-avd") + 1])
        self.assertEqual("5556", command[command.index("-port") + 1])
        self.assertEqual(avd_home, kwargs["env"]["ANDROID_AVD_HOME"])

    def test_boot_rejects_an_invalid_or_odd_port_before_discovery(self):
        for port in ("not-a-port", "5552", "5555", "5684"):
            with self.subTest(port=port), \
                    patch.object(device.subprocess, "run") as run, \
                    patch.object(device.subprocess, "Popen") as popen:
                with self.assertRaisesRegex(SystemExit, "port"):
                    device.boot(timeout=0, port=port)
                run.assert_not_called()
                popen.assert_not_called()


class GateEmulatorTest(unittest.TestCase):
    def test_running_avd_accepts_console_blank_lines_but_not_extra_output(self):
        for stdout, expected in (("Test_API\n\nOK\n\n", "Test_API"),
                                 ("Test_API\nUnexpected\nOK\n", None)):
            with self.subTest(stdout=stdout), patch.object(device.subprocess, "run",
                    return_value=subprocess.CompletedProcess([], 0, stdout, "")):
                self.assertEqual(expected, device.running_avd("emulator-5554"))

    def test_configured_avd_is_checked_before_tuning_or_killing(self):
        serial = "emulator-5554"
        different_avd = "Personal_API\r\nOK\r\n"
        unknown_avd = "KO: authentication required\r\n"
        for reason, output in ((None, different_avd), (None, unknown_avd),
                               ("slow", different_avd), ("slow", unknown_avd),
                               ("not running", different_avd)):
            response = subprocess.CompletedProcess([], 0, output, "")
            with self.subTest(reason=reason, output=output), \
                    patch.object(gate, "stale", return_value=reason), \
                    patch.object(device, "AVD", "Disposable_API"), \
                    patch.object(device.subprocess, "run", return_value=response) as probe, \
                    patch.object(device, "tune") as tune, \
                    patch.object(device, "kill") as kill, \
                    patch.object(device, "boot") as boot:
                self.assertFalse(gate.revive(serial))
                self.assertEqual([str(device.ADB), "-s", serial, "emu", "avd", "name"], probe.call_args.args[0])
                tune.assert_not_called()
                kill.assert_not_called()
                boot.assert_not_called()

    def test_running_configured_avd_is_reused(self):
        serial = "emulator-5554"
        response = subprocess.CompletedProcess([], 0, "Disposable_API\r\nOK\r\n", "")
        with patch.object(gate, "stale", return_value=None), \
                patch.object(device, "AVD", "Disposable_API"), \
                patch.object(device.subprocess, "run", return_value=response) as probe, \
                patch.object(device, "tune") as tune, \
                patch.object(device, "kill") as kill:
            self.assertTrue(gate.revive(serial))
            probe.assert_called_once()
            tune.assert_called_once_with(serial)
            kill.assert_not_called()

    def test_configured_avd_boots_on_a_free_port_without_stopping_any_device(self):
        serial = "emulator-5554"
        result = subprocess.CompletedProcess([], 1, "", "could not connect")
        with patch.object(gate, "stale", return_value="not running"), \
                patch.object(device, "AVD", "Disposable_API"), \
                patch.object(device.subprocess, "run", return_value=result), \
                patch.object(device, "kill") as kill, \
                patch.object(device, "boot", return_value=serial) as boot:
            self.assertTrue(gate.revive(serial))
            kill.assert_not_called()
            boot.assert_called_once_with(port="5554")

    def test_restart_preserves_serial_and_refuses_a_different_one(self):
        original = "emulator-5556"
        for booted in (original, "emulator-5554"):
            with self.subTest(booted=booted), \
                    patch.object(gate, "stale", return_value="slow"), \
                    patch.object(device, "AVD", None), \
                    patch.object(device, "kill") as kill, \
                    patch.object(device, "boot", return_value=booted) as boot:
                self.assertEqual(booted == original, gate.revive(original))
                kill.assert_called_once_with(original)
                boot.assert_called_once_with(port="5556")

    def test_gate_rejects_an_odd_emulator_port_without_touching_it(self):
        with patch.object(gate, "stale", return_value="slow") as stale, \
                patch.object(device, "AVD", None), \
                patch.object(device, "kill") as kill, \
                patch.object(device, "boot") as boot:
            self.assertFalse(gate.revive("emulator-5555"))
            stale.assert_not_called()
            kill.assert_not_called()
            boot.assert_not_called()


if __name__ == "__main__":
    unittest.main()
