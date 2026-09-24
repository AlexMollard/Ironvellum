"""Drive Ironvellum on whatever Android target is available.

The phone is not always plugged in, so this resolves a target in one place:
a running emulator first, then a USB device. Everything else in the repo's
verification loop (install, launch, screenshot, tap by label) goes through here
so no session has to rebuild the adb incantations by hand.

    python tools/device.py up                 # boot the emulator if nothing is attached
    python tools/device.py install             # build output -> target
    python tools/device.py launch
    python tools/device.py shot today          # .tmp/shots/today.png
    python tools/device.py labels              # visible text, for finding a tap target
    python tools/device.py tap "SKILL TREE"    # tap by visible text
    python tools/device.py tap --desc Codex    # tap by content-desc (nav bar)
    python tools/device.py down                # stop the emulator

Screenshots land in .tmp/shots so they stay out of git.
"""

from __future__ import annotations

import argparse
import os
import pathlib
import re
import subprocess
import sys
import time

SDK = pathlib.Path(os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk"))
ADB = SDK / "platform-tools" / "adb.exe"
EMULATOR = SDK / "emulator" / "emulator.exe"
AVD = "IronvellumEmu"
PORT = "5554"
APK = pathlib.Path("app/build/outputs/apk/foss/debug/app-foss-debug.apk")  # :app:assembleFossDebug
PKG = "com.ironvellum.app"
SHOTS = pathlib.Path(".tmp/shots")


def adb(*args: str, serial: str | None = None, binary: bool = False):
    cmd = [str(ADB)] + (["-s", serial] if serial else []) + list(args)
    out = subprocess.run(cmd, capture_output=True)
    return out.stdout if binary else out.stdout.decode("utf-8", "replace")


def attached() -> list[str]:
    lines = adb("devices").splitlines()[1:]
    return [l.split()[0] for l in lines if l.strip().endswith("device")]


def target() -> str:
    """Emulator wins when both are present: it is the reproducible one."""
    devices = attached()
    if not devices:
        sys.exit("no target attached; run `python tools/device.py up` first")
    return next((d for d in devices if d.startswith("emulator-")), devices[0])


def up(timeout: int = 300) -> str:
    if attached():
        return target()
    subprocess.Popen(
        [str(EMULATOR), "-avd", AVD, "-no-window", "-no-audio", "-no-boot-anim",
         "-gpu", "swiftshader_indirect", "-port", PORT],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    serial = f"emulator-{PORT}"
    adb("wait-for-device", serial=serial)
    deadline = time.time() + timeout
    while time.time() < deadline:
        if adb("shell", "getprop", "sys.boot_completed", serial=serial).strip() == "1":
            # The launcher needs a moment past boot_completed or the first
            # `am start` lands before the window manager will show anything.
            time.sleep(4)
            return serial
        time.sleep(5)
    sys.exit("emulator did not finish booting")


def wake(serial: str) -> None:
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP", serial=serial)
    adb("shell", "input", "swipe", "540", "1900", "540", "700", "250", serial=serial)


def dump(serial: str) -> str:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", serial=serial)
    return adb("shell", "cat", "/sdcard/ui.xml", serial=serial)


def bounds(serial: str, pattern: str) -> tuple[int, int, int, int] | None:
    match = re.search(pattern + r'[^/]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', dump(serial))
    return tuple(map(int, match.groups())) if match else None  # type: ignore[return-value]


def tap(serial: str, needle: str, by_desc: bool = False, settle: float = 2.5) -> bool:
    key = "content-desc" if by_desc else "text"
    # Loose match: labels carry padding spaces ("  New Preset") and casing varies.
    box = bounds(serial, rf'{key}="\s*{re.escape(needle)}[^"]*"')
    if box is None:
        return False
    adb("shell", "input", "tap", str((box[0] + box[2]) // 2), str((box[1] + box[3]) // 2), serial=serial)
    time.sleep(settle)
    return True


def shot(serial: str, name: str) -> pathlib.Path:
    SHOTS.mkdir(parents=True, exist_ok=True)
    path = SHOTS / f"{name}.png"
    path.write_bytes(adb("exec-out", "screencap", "-p", serial=serial, binary=True))
    return path


def crashes(serial: str) -> int:
    return adb("logcat", "-d", "-b", "crash", serial=serial).count("FATAL EXCEPTION")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("up")
    sub.add_parser("down")
    sub.add_parser("which")
    sub.add_parser("install")
    sub.add_parser("launch")
    sub.add_parser("labels")
    p_shot = sub.add_parser("shot"); p_shot.add_argument("name")
    p_tap = sub.add_parser("tap"); p_tap.add_argument("needle"); p_tap.add_argument("--desc", action="store_true")
    args = ap.parse_args()

    if args.cmd == "up":
        print("target:", up())
        return
    if args.cmd == "down":
        serial = next((d for d in attached() if d.startswith("emulator-")), None)
        print("stopped" if serial and adb("emu", "kill", serial=serial) is not None else "no emulator running")
        return

    serial = target()
    if args.cmd == "which":
        print(serial, "| attached:", ", ".join(attached()))
    elif args.cmd == "install":
        if not APK.exists():
            sys.exit(f"{APK} missing; build first")
        print(adb("install", "-r", str(APK), serial=serial).strip().splitlines()[-1])
    elif args.cmd == "launch":
        wake(serial)
        adb("shell", "am", "force-stop", PKG, serial=serial)
        adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity", serial=serial)
        time.sleep(6)
        print("crashes:", crashes(serial))
    elif args.cmd == "labels":
        print("\n".join(re.findall(r'text="([^"]+)"', dump(serial))))
    elif args.cmd == "shot":
        print(shot(serial, args.name))
    elif args.cmd == "tap":
        print("tapped" if tap(serial, args.needle, args.desc) else "not found")


if __name__ == "__main__":
    main()
