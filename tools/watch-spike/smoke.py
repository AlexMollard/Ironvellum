"""Exercise only the disposable phone probe; invoke under tools/device.py lock."""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
PROBE = Path(__file__).resolve().parent
PACKAGE = "com.ironvellum.watchprobe.phone"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    args = parser.parse_args()
    if not args.serial.startswith("emulator-") or os.environ.get("ANDROID_SERIAL") != args.serial:
        parser.error("require an explicit emulator serial matching the device-lock helper's ANDROID_SERIAL")
    sdk = Path(os.environ.get("ANDROID_HOME") or Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk")
    adb = sdk / "platform-tools/adb.exe"

    def run(*command: str, binary: bool = False) -> str | bytes:
        result = subprocess.run([str(adb), "-s", args.serial, *command], capture_output=True, timeout=30, check=True)
        return result.stdout if binary else result.stdout.decode("utf-8", errors="replace")

    if run("shell", "getprop", "ro.kernel.qemu").strip() != "1":
        parser.error("target is not a verified emulator")
    apk = PROBE / "probe/build/outputs/apk/phone/debug/probe-phone-debug.apk"
    if not apk.is_file():
        parser.error(f"build the phone debug APK first: {apk}")
    output = ROOT / ".tmp/watch-spike"
    output.mkdir(parents=True, exist_ok=True)

    def nodes() -> list[ET.Element]:
        run("shell", "uiautomator", "dump", "/data/local/tmp/ironvellum-watch-probe-ui.xml")
        xml = run("shell", "cat", "/data/local/tmp/ironvellum-watch-probe-ui.xml")
        return list(ET.fromstring(xml).iter("node"))

    def find(text: str, tap: bool = False) -> ET.Element:
        for attempt in range(7):
            for node in nodes():
                if node.get("text", "").casefold() == text.casefold():
                    if tap:
                        assert node.get("enabled") == "true", f"disabled: {text}"
                        coords = [int(x) for x in re.findall(r"\d+", node.attrib["bounds"])]
                        run("shell", "input", "tap", str((coords[0] + coords[2]) // 2), str((coords[1] + coords[3]) // 2))
                        time.sleep(0.2)
                    return node
            if attempt < 6:
                run("shell", "input", "swipe", "500", "1400", "500", "450", "250")
        raise AssertionError(f"not visible/reachable: {text}")

    def assert_fixture(fragment: str) -> None:
        for _ in range(7):
            if any(fragment in node.get("text", "") for node in nodes()):
                return
            run("shell", "input", "swipe", "500", "500", "500", "1300", "250")
        raise AssertionError(f"fixture not visible or incorrect: {fragment}")

    run("install", "-r", str(apk))
    run("shell", "am", "force-stop", PACKAGE)
    run("shell", "am", "start", "-W", "-n", PACKAGE + "/com.ironvellum.watchprobe.ProbeActivity")
    find("Phone · link probe")
    find("Local disposable fixture · not connected")
    (output / "phone-status.png").write_bytes(run("exec-out", "screencap", "-p", binary=True))
    assert_fixture("8 reps · 60.0 kg")
    find("Reps +1", tap=True)
    assert_fixture("9 reps · 60.0 kg")
    find("Load +2.5 kg", tap=True)
    assert_fixture("9 reps · 62.5 kg")
    find("Set complete", tap=True)
    find("Rate RPE 8", tap=True)
    assert_fixture("Done: yes · RPE: 8")
    (output / "phone-fixture.png").write_bytes(run("exec-out", "screencap", "-p", binary=True))
    find("Undo completion", tap=True)
    assert_fixture("Done: no · RPE: not set")
    print("PASS: disposable phone UI edits reps/load, completes/rates and undoes without touching Ironvellum")
    print(f"Screenshots: {output}")
    print("NOT TESTED: watch UI, Bluetooth radios, first pairing, real-device background lifecycle, latency and energy")
    run("shell", "am", "force-stop", PACKAGE)


if __name__ == "__main__":
    main()
