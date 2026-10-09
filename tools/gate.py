"""Run Ironvellum's whole gate locally.

This is the replacement for CI, not a convenience wrapper. The repository is
private, so every GitHub runner minute is billed, and the work is identical to
what this machine can do: `.github/workflows/ci.yml` is manual-only and this
script covers all three of its jobs.

    python3 tools/gate.py              # build, unit, lint, instrumented
    python3 tools/gate.py --backend    # plus the Supabase assertions
    python3 tools/gate.py --no-device  # skip instrumented (no emulator running)

Device safety: the instrumented suite calls `pm clear`, deletes rows from the
app's own database, and uninstalls the app when it finishes. Run against the
owner's phone it destroys real training history. So this script resolves an
emulator serial itself and pins ANDROID_SERIAL to it; a physical device is
refused unless --serial names it explicitly.
"""

from __future__ import annotations

import argparse
import contextlib
import glob
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ADB = os.path.join(
    os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk", "platform-tools", "adb.exe"
)


def run(cmd: list[str] | str, env: dict | None = None, shell: bool = False) -> int:
    """Stream a command to the terminal and hand back its exit code."""
    started = time.time()
    proc = subprocess.run(cmd, cwd=ROOT, env=env, shell=shell)
    print(f"    ({time.time() - started:.0f}s)")
    return proc.returncode


def devices() -> list[str]:
    out = subprocess.run([ADB, "devices"], capture_output=True, text=True).stdout
    return [
        line.split("\t")[0]
        for line in out.splitlines()[1:]
        if line.strip().endswith("device")
    ]


def pick_serial(requested: str | None) -> str | None:
    attached = devices()
    if requested:
        if requested not in attached:
            print(f"!! {requested} is not attached. Attached: {attached or 'none'}")
            return None
        return requested
    emulators = [s for s in attached if s.startswith("emulator-")]
    if not emulators:
        print(f"!! no emulator attached (found: {attached or 'none'}).")
        print("   The instrumented suite wipes app data and uninstalls the app, so")
        print("   it will not be pointed at a physical device by default. Start an")
        print("   emulator, or pass --serial to accept that on a real phone.")
        return None
    if len(emulators) > 1:
        print(f"!! more than one emulator attached ({emulators}); name one with --serial")
        return None
    return emulators[0]


def counts(pattern: str) -> tuple[int, int]:
    total = failed = 0
    for path in glob.glob(os.path.join(ROOT, pattern), recursive=True):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        total += int(root.get("tests", 0))
        failed += int(root.get("failures", 0)) + int(root.get("errors", 0))
    return total, failed


def responsive(serial: str) -> bool:
    """True when the device's shell answers; `adb devices` alone is not proof."""
    try:
        out = subprocess.run(
            [ADB, "-s", serial, "shell", "getprop ro.build.version.sdk; pm path android"],
            capture_output=True, text=True, timeout=10,
        ).stdout.split()
    except subprocess.TimeoutExpired:
        return False
    # A system_server that died mid-suite leaves the shell answering while the package service is
    # gone, and every install then fails; ask for both.
    return len(out) >= 2 and out[0].isdigit() and out[1].startswith("package:")


def revive(serial: str) -> bool:
    """Bring a wedged emulator back before the suite runs.

    An emulator left idle between runs can stay listed by `adb devices` while
    its shell hangs. Gradle then skips it as "Unknown API Level" and the gate
    goes red with one of N tests run. Restart adb first; if the emulator is
    still silent, kill that port's emulator and cold boot it. A physical
    device is never touched.
    """
    if responsive(serial):
        return True
    if not serial.startswith("emulator-"):
        print(f"!! {serial} does not answer; not restarting a physical device")
        return False
    print(f"-- {serial} does not answer; restarting adb")
    for cmd in ("kill-server", "start-server"):
        subprocess.run([ADB, cmd], capture_output=True, timeout=30)
    if responsive(serial):
        print(f"-- {serial} answers again")
        return True

    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import device

    port = serial.split("-", 1)[1]
    print(f"-- {serial} still silent; cold booting the emulator on port {port}")
    subprocess.run(
        ["powershell", "-NoProfile", "-Command",
         "Get-CimInstance Win32_Process -Filter \"Name like 'qemu-system%' or Name='emulator.exe'\""
         f" | Where-Object {{ $_.CommandLine -match '-port {port}( |$)' }}"
         " | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }"],
        capture_output=True, timeout=60,
    )
    time.sleep(3)
    subprocess.Popen(
        [str(device.EMULATOR), "-avd", device.AVD, "-no-window", "-no-audio",
         "-no-boot-anim", "-gpu", "host", "-port", port, "-no-snapshot-load"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    deadline = time.time() + 300
    while time.time() < deadline:
        try:
            booted = subprocess.run(
                [ADB, "-s", serial, "shell", "getprop", "sys.boot_completed"],
                capture_output=True, text=True, timeout=10,
            ).stdout.strip() == "1"
        except subprocess.TimeoutExpired:
            booted = False
        if booted:
            # Package manager and friends settle a little after boot_completed.
            time.sleep(20)
            print(f"-- {serial} cold booted")
            return responsive(serial)
        time.sleep(5)
    print(f"!! {serial} did not finish booting")
    return False


LOCK = os.path.join(ROOT, ".tmp", "emulator.lock")


@contextlib.contextmanager
def emulator_lock(serial: str | None):
    """Hold the emulator lock that parallel sessions share while the suite runs.

    Several sessions use the one emulator. An install or uninstall from another
    one mid-suite kills the test process, and the run reports a fraction of its
    tests. Taking the lock is a mkdir, which is atomic; it is released however
    the run ends.
    """
    if not serial:
        yield
        return
    os.makedirs(os.path.dirname(LOCK), exist_ok=True)
    waited = False
    while True:
        try:
            os.mkdir(LOCK)
            break
        except FileExistsError:
            if not waited:
                print(f"-- {LOCK} is held by another session; waiting")
                waited = True
            time.sleep(30)
    try:
        yield
    finally:
        os.rmdir(LOCK)


def declared_tests(pattern: str) -> int:
    """How many @Test methods the sources declare: the floor a full run must reach."""
    total = 0
    for path in glob.glob(os.path.join(ROOT, pattern), recursive=True):
        with open(path, encoding="utf-8", errors="replace") as handle:
            total += len(re.findall(r"@Test\b", handle.read()))
    return total


def short_run(ran: int, declared: int) -> str | None:
    """Why a run that reported no failures still proves nothing, or None.

    A test process that crashes before its first test, or is killed part way
    (another session uninstalling the app), leaves a results file with fewer
    tests and zero failures, and Gradle can still exit 0. Counting only
    failures turned that into GATE GREEN.
    """
    if ran == 0:
        return "no tests ran"
    if ran < declared:
        return f"only {ran} of {declared} declared tests ran"
    return None


def lint_summary(flavour: str) -> str:
    path = os.path.join(
        ROOT, "app/build/reports", f"lint-results-{flavour}Release.txt"
    )
    if not os.path.exists(path):
        return "no report"
    with open(path, encoding="utf-8", errors="replace") as handle:
        lines = [line for line in handle.read().strip().splitlines() if line.strip()]
    return lines[-1] if lines else "empty report"


def gradle(tasks: list[str], serial: str | None) -> int:
    env = dict(os.environ)
    if serial:
        env["ANDROID_SERIAL"] = serial
    # gradlew.bat, via cmd, is how this repo is driven on Windows. cmd will not
    # find it by bare name when NoDefaultCurrentDirectoryInExePath=1, so call it
    # by absolute path and drop the variable from the child env.
    env.pop("NoDefaultCurrentDirectoryInExePath", None)
    joined = " ".join(tasks)
    gradlew = os.path.join(ROOT, "gradlew.bat")
    return run(f'cmd /c ""{gradlew}" {joined} --console=plain"', env=env, shell=True)


def backend() -> int:
    """Apply the schema to a throwaway Postgres and assert the guarantees.

    Mirrors ci.yml's `backend` job: the stub, the baseline, the
    declares-idempotent check, the re-apply, then assert_all.sql. Then the
    hosted reset round trip: seed a little, run supabase/reset.sql, prove it
    left nothing behind (assert_reset.sql), apply the baseline again onto the
    wiped project and run assert_all.sql once more.
    """
    migrations = sorted(glob.glob(os.path.join(ROOT, "supabase/migrations/*.sql")))
    if not migrations:
        print("!! no migrations found")
        return 1

    undeclared = []
    for path in migrations:
        with open(path, encoding="utf-8", errors="replace") as handle:
            if not re.search(r"idempotent", handle.read(), re.I):
                undeclared.append(os.path.basename(path))
    if undeclared:
        # The re-apply below keys off that word, so a file that does not say it
        # would silently opt out of the idempotency check.
        print(f"!! migrations do not declare themselves idempotent: {undeclared}")
        return 1

    name = "ironvellum-gate-pg"
    subprocess.run(["docker", "rm", "-f", name], capture_output=True)
    print("-- starting throwaway postgres")
    if run(
        [
            "docker", "run", "-d", "--name", name,
            "-e", "POSTGRES_PASSWORD=probe",
            "-p", "55432:5432", "postgres:16",
        ]
    ):
        return 1

    try:
        for _ in range(60):
            ready = subprocess.run(
                ["docker", "exec", name, "pg_isready", "-U", "postgres"],
                capture_output=True,
            )
            if ready.returncode == 0:
                break
            time.sleep(1)
        else:
            print("!! postgres never became ready")
            return 1

        def psql(path: str) -> int:
            with open(path, "rb") as handle:
                sql = handle.read()
            proc = subprocess.run(
                ["docker", "exec", "-i", "-e", "PGPASSWORD=probe", name,
                 "psql", "-v", "ON_ERROR_STOP=1", "-q", "-U", "postgres"],
                input=sql,
            )
            return proc.returncode

        test = lambda fname: os.path.join(ROOT, "supabase/test", fname)
        steps = [test("supabase_stub.sql")] + migrations
        # Re-apply every file: each one declares itself idempotent (checked above).
        steps += migrations
        steps.append(test("assert_all.sql"))
        # The owner pastes supabase/hosted/ patches into the live project. The
        # release patch must apply cleanly onto a project that has the baseline
        # and run again as a no-op, and the suite must still hold afterwards.
        # (The older patches migrate warbands and are not re-runnable here.)
        release = os.path.join(ROOT, "supabase/hosted/2026-10-02-release.sql")
        steps += [release, release, test("assert_all.sql")]
        # The hosted reset round trip. assert_all leaves its fixtures behind and
        # reset_seed adds a sign-up-made profile and a row in every table, so
        # reset.sql has real data to destroy. Then the baseline goes onto the
        # wiped project again and the whole suite runs on top of it.
        steps += [
            test("reset_seed.sql"),
            os.path.join(ROOT, "supabase/reset.sql"),
            test("assert_reset.sql"),
        ]
        steps += migrations
        steps.append(test("assert_all.sql"))

        for path in steps:
            print(f"   {os.path.relpath(path, ROOT)}")
            if psql(path):
                return 1
        return 0
    finally:
        subprocess.run(["docker", "rm", "-f", name], capture_output=True)


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the Ironvellum gate locally.")
    parser.add_argument("--serial", help="device to run instrumented tests on")
    parser.add_argument("--flavour", choices=("foss", "play"), default="foss",
                        help="distribution flavour to build and test (default foss)")
    parser.add_argument("--no-device", action="store_true", help="skip instrumented tests")
    parser.add_argument("--backend", action="store_true", help="also assert the Supabase schema")
    args = parser.parse_args()

    serial = None
    if not args.no_device:
        serial = pick_serial(args.serial)
        if serial is None:
            return 1
        print(f"-- instrumented tests pinned to {serial}")

    # The named flavour is built, tested and linted; the other flavour's debug
    # APK and androidTest compile too, so a play-only break (Google sign-in
    # sources, playImplementation deps) cannot rot unnoticed while everyone
    # runs the foss gate.
    other = "play" if args.flavour == "foss" else "foss"
    fl = args.flavour.capitalize()
    other_fl = other.capitalize()
    tasks = [
        f":app:assemble{fl}Debug",
        f":app:test{fl}DebugUnitTest",
        f":app:lint{fl}Release",
        f":app:assemble{other_fl}Debug",
        f":app:assemble{other_fl}DebugAndroidTest",
    ]
    if serial:
        tasks.insert(3, f":app:connected{fl}DebugAndroidTest")
    else:
        # Without a device, at least prove the instrumented sources still
        # compile — assembleDebug does not build src/androidTest, so they can
        # be committed broken and nobody notices until a cable appears.
        tasks.append(f":app:assemble{fl}DebugAndroidTest")

    print(f"-- gradle: {' '.join(tasks)}")
    failures = []
    with emulator_lock(serial):
        if serial and not revive(serial):
            print("\nGATE RED: the device does not answer")
            return 1
        if gradle(tasks, serial):
            failures.append("gradle")

    if args.backend:
        print("-- backend: supabase migrations + assertions")
        if backend():
            failures.append("backend")

    unit_total, unit_failed = counts(
        f"app/build/test-results/test{fl}DebugUnitTest/*.xml"
    )
    # Only this flavour's directory: results from an earlier run of another
    # flavour (or the pre-flavour `connected/debug/`) are never cleaned and
    # would be counted, and their failures reported, as if this run made them.
    inst_total, inst_failed = counts(
        f"app/build/outputs/androidTest-results/connected/debug/flavors/{args.flavour}/**/*.xml"
    )
    unit_short = short_run(unit_total, declared_tests("app/src/test/**/*.kt"))
    inst_short = short_run(inst_total, declared_tests("app/src/androidTest/**/*.kt")) if serial else None
    print("\n=== gate ===")
    print(f"  flavour       {args.flavour}")
    print(f"  unit          {unit_total:>4} tests, {unit_failed} failed" + (f"  !! {unit_short}" if unit_short else ""))
    if serial:
        print(f"  instrumented  {inst_total:>4} tests, {inst_failed} failed" + (f"  !! {inst_short}" if inst_short else ""))
    print(f"  lint          {lint_summary(fl)}")
    if args.backend:
        print(f"  backend       {'FAILED' if 'backend' in failures else 'assertions passed'}")

    if failures or unit_failed or inst_failed or unit_short or inst_short:
        print("\nGATE RED")
        return 1
    print("\nGATE GREEN")
    return 0


if __name__ == "__main__":
    sys.exit(main())
