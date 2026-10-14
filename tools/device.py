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
GPU = os.environ.get("IRONVELLUM_GPU", "angle_indirect")
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


def emulators() -> list[str]:
    """Emulator serials only: a phone on the cable must never count as "an emulator is up"."""
    return [d for d in attached() if d.startswith("emulator-")]


def shell_ok(serial: str, timeout: float = 10) -> float | None:
    """Seconds one trivial shell round trip took, or None when the shell hangs or package service is gone."""
    started = time.time()
    try:
        out = subprocess.run(
            [str(ADB), "-s", serial, "shell", "getprop sys.boot_completed; pm path android"],
            capture_output=True, text=True, timeout=timeout,
        ).stdout.split()
    except subprocess.TimeoutExpired:
        return None
    if len(out) < 2 or out[0] != "1" or not out[1].startswith("package:"):
        return None
    return time.time() - started


def uptime(serial: str) -> float:
    """Seconds since the emulator booted; infinity when it cannot be read."""
    try:
        return float(adb("shell", "cat", "/proc/uptime", serial=serial).split()[0])
    except (IndexError, ValueError):
        return float("inf")


def tune(serial: str) -> None:
    """Switch the UI animations off: the suite waits on idle, and animations only slow that."""
    for key in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        adb("shell", "settings", "put", "global", key, "0", serial=serial)
    # A cold boot leaves the screen off and un-awakened, and a touch cannot be injected into that.
    adb("shell", "svc", "power", "stayon", "true", serial=serial)
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP", serial=serial)
    adb("shell", "wm", "dismiss-keyguard", serial=serial)


def kill(serial: str) -> None:
    """Stop one emulator and wait until its process is gone (a lingering qemu blocks the next boot)."""
    port = serial.split("-", 1)[1]
    adb("emu", "kill", serial=serial)
    time.sleep(2)
    subprocess.run(
        ["powershell", "-NoProfile", "-Command",
         "Get-CimInstance Win32_Process -Filter \"Name like 'qemu-system%' or Name='emulator.exe'\""
         f" | Where-Object {{ $_.CommandLine -match '-port {port}( |$)' }}"
         " | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }"],
        capture_output=True, timeout=60,
    )
    time.sleep(2)


def boot(timeout: int = 300) -> str:
    """Cold boot the emulator: no snapshot is loaded or saved, so every boot starts clean."""
    serial = f"emulator-{PORT}"
    env = dict(os.environ)
    env.setdefault("ANDROID_AVD_HOME", r"D:\caches\android-avd")
    subprocess.Popen(
        [str(EMULATOR), "-avd", AVD, "-no-window", "-no-audio", "-no-boot-anim",
         "-gpu", GPU, "-port", PORT, "-no-snapshot", "-camera-back", "none"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, env=env,
        creationflags=0x00000008 | 0x00000200,  # DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP
    )
    deadline = time.time() + timeout
    while time.time() < deadline:
        if serial in emulators() and shell_ok(serial, 5) is not None:
            tune(serial)
            return serial
        time.sleep(2)
    sys.exit("emulator did not finish booting")


def up(timeout: int = 300) -> str:
    """The running emulator, or a freshly booted one. Only emulators count: the phone is never a substitute."""
    running = emulators()
    return running[0] if running else boot(timeout)


def wake(serial: str) -> None:
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP", serial=serial)
    adb("shell", "input", "swipe", "540", "1900", "540", "700", "250", serial=serial)


def dump(serial: str) -> str:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", serial=serial)
    return adb("shell", "cat", "/sdcard/ui.xml", serial=serial)


def bounds(serial: str, pattern: str) -> tuple[int, int, int, int] | None:
    # [^>]: stay inside one node's attributes. [^/] stopped at the "/" in a resource-id.
    match = re.search(pattern + r'[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', dump(serial))
    return tuple(map(int, match.groups())) if match else None  # type: ignore[return-value]


def loose(needle: str) -> str:
    """Regex for `needle` that treats every apostrophe and quote style as one.

    A typed ' must find a label drawn with a curly apostrophe ("Don’t allow"), and
    the dump may carry either the character or its XML entity.
    """
    # �: the dump arrives through adb's text decode, which can turn a curly mark into U+FFFD.
    apostrophe = "(?:'|‘|’|�|&apos;|&#39;)"
    quote = '(?:\\"|&quot;|“|”)'
    return "".join(
        apostrophe if ch in "'‘’" else quote if ch in '"“”' else re.escape(ch)
        for ch in needle
    )


def tap(serial: str, needle: str, by_desc: bool = False, settle: float = 2.5) -> bool:
    key = "content-desc" if by_desc else "text"
    # Loose match: labels carry padding spaces ("  New Preset") and casing varies.
    box = bounds(serial, rf'{key}="\s*{loose(needle)}[^"]*"')
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


def apk_age(apk: pathlib.Path, src: pathlib.Path = pathlib.Path("app/src/main")) -> tuple[float, float | None]:
    """Seconds since the APK was built, and how far it trails the newest source file.

    The second value is None when the APK is at least as new as every file under
    `src`, otherwise the gap in seconds, so a stale build can be called out.
    """
    built = apk.stat().st_mtime
    newest = max((f.stat().st_mtime for f in src.rglob("*") if f.is_file()), default=0.0)
    return time.time() - built, (newest - built) if newest > built else None


def fmt_age(seconds: float) -> str:
    seconds = int(seconds)
    if seconds < 90:
        return f"{seconds}s"
    if seconds < 5400:
        return f"{seconds // 60}m"
    if seconds < 172800:
        return f"{seconds // 3600}h"
    return f"{seconds // 86400}d"

def main() -> None:
    # Labels carry glyphs (◆, →) the Windows console code page cannot print.
    sys.stdout.reconfigure(encoding="utf-8")
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
        age, behind = apk_age(APK)
        built = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(APK.stat().st_mtime))
        print(f"APK built {built} ({fmt_age(age)} ago)")
        if behind is not None:
            print(f"!! STALE: app/src/main has changes {fmt_age(behind)} newer than this APK; "
                  "run :app:assembleFossDebug first")
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
