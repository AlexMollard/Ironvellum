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

Speed: the gate runs in two Gradle invocations. The first compiles what the
instrumented suite needs while the emulator cold boots beside it; the second
runs unit tests, lint, the instrumented suite and the other flavour's
assembles at the same time (the configuration cache lets Gradle run
independent tasks of one project in parallel). The emulator is cold booted
whenever it has been up longer than UPTIME_LIMIT or answers slowly: a
long-lived emulator degrades, and a cold boot costs about 30s. If it drops off
adb mid-run the gate restarts it and retries the instrumented phase once.
Gradle output goes to .tmp/gate-*.log; the console gets one line per phase.
"""

from __future__ import annotations

import argparse
import contextlib
import glob
import os
import re
import shutil
import subprocess
import sys
import threading
import time
import xml.etree.ElementTree as ET

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import device  # noqa: E402

# Wall-clock budget for one gate; raised from 5 to 7 minutes by the owner on 2026-10-27 as the suite grew.
BUDGET_S = 7 * 60

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
        # Only an emulator is ever booted or used by default: the instrumented suite wipes app data and
        # uninstalls the app, so a phone on the cable is never the target unless --serial names it.
        return f"emulator-{device.PORT}"
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


FIRST_ATTEMPT = os.path.join(ROOT, ".tmp", "gate-first-attempt")


def failing_tests(directory: str) -> list[str]:
    """class.method of every failed or errored test in the result XML files under a directory."""
    names = []
    for path in sorted(glob.glob(os.path.join(directory, "**", "*.xml"), recursive=True)):
        try:
            suite = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for case in suite.iter("testcase"):
            if case.find("failure") is not None or case.find("error") is not None:
                names.append(f"{case.get('classname')}.{case.get('name')}")
    return names


def keep_first_attempt(results: str, destination: str = FIRST_ATTEMPT) -> list[str]:
    """Copy the first instrumented attempt's results (test XML, logcat) aside and name the failing tests.

    The retry reruns the suite into the same results directory and overwrites them, so without this copy a
    failure that the retry hides can never be explained afterwards.
    """
    shutil.rmtree(destination, ignore_errors=True)
    if os.path.isdir(results):
        shutil.copytree(results, destination)
    return failing_tests(destination)


UPTIME_LIMIT = 30 * 60  # seconds an emulator may stay up before the gate cold boots it
PROBE_LIMIT = 3.0       # seconds one trivial shell call may take before the emulator counts as degraded


def responsive(serial: str) -> bool:
    """True when the device's shell and package service answer; `adb devices` alone is not proof."""
    return device.shell_ok(serial, 10) is not None


def stale(serial: str) -> str | None:
    """Why this emulator should not be handed the suite as it is, or None when it is fine."""
    if serial not in devices():
        return "not running"
    lag = device.shell_ok(serial, 10)
    if lag is None:
        return "does not answer"
    if lag > PROBE_LIMIT:
        return f"slow ({lag:.1f}s for one shell call)"
    up = device.uptime(serial)
    if up > UPTIME_LIMIT:
        return f"up {up / 60:.0f} min"
    return None


def revive(serial: str, force: bool = False) -> bool:
    """Hand back a device the suite can run on, cold booting the emulator when it is stale.

    A long-lived emulator degrades (graphics services burn CPU, tests slow ten-fold, adb drops), and a cold boot
    costs about 30s, so one that has been up longer than UPTIME_LIMIT, is slow, or is silent is restarted.
    A physical device is never touched: it either answers or the gate stops.
    """
    if not serial.startswith("emulator-"):
        return responsive(serial)
    reason = "restart requested" if force else stale(serial)
    if reason is None:
        device.tune(serial)
        return True
    started = time.time()
    print(f"-- {serial}: {reason}; cold booting")
    device.kill(serial)
    try:
        device.boot()
    except SystemExit as exc:
        print(f"!! {exc}")
        return False
    print(f"-- {serial}: booted in {time.time() - started:.0f}s")
    return True


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


def print_failure(log: str, limit: int = 25) -> None:
    """The part of a Gradle log that says what went wrong, not the thousand lines around it."""
    with open(log, encoding="utf-8", errors="replace") as handle:
        lines = handle.read().splitlines()
    keep = [l for l in lines if l.startswith(("e: ", "FAILURE", "* What went wrong")) or " FAILED" in l
            or l.startswith(("Execution failed", "> ")) and "Task" not in l]
    for line in keep[:limit]:
        print(f"   {line}")


GRADLE_LIMIT = 15 * 60  # seconds before a Gradle run is treated as hung and killed


def kill_tree(proc: subprocess.Popen) -> None:
    subprocess.run(["taskkill", "/PID", str(proc.pid), "/T", "/F"], capture_output=True)


def failed_tasks(log: str) -> list[str]:
    with open(log, encoding="utf-8", errors="replace") as handle:
        return re.findall(r"^> Task (\S+) FAILED", handle.read(), re.M)


def gradle(tasks: list[str], serial: str | None, label: str, watch: bool = False) -> tuple[int, bool, str]:
    """Run Gradle with its output in .tmp/gate-<label>.log: (exit code, device dropped or run hung, log path).

    With `watch` the device is probed every 20s; three misses in a row mean adb dropped mid-run (the symptom
    is a run that hangs forever), so Gradle is killed and the caller can restart the emulator and retry.
    """
    env = dict(os.environ)
    if serial:
        env["ANDROID_SERIAL"] = serial
    # gradlew.bat, via cmd, is how this repo is driven on Windows. cmd will not
    # find it by bare name when NoDefaultCurrentDirectoryInExePath=1, so call it
    # by absolute path and drop the variable from the child env.
    env.pop("NoDefaultCurrentDirectoryInExePath", None)
    gradlew = os.path.join(ROOT, "gradlew.bat")
    log = os.path.join(ROOT, ".tmp", f"gate-{label}.log")
    os.makedirs(os.path.dirname(log), exist_ok=True)
    cmd = f'cmd /c ""{gradlew}" {" ".join(tasks)} --configuration-cache --continue --console=plain"'
    started = time.time()
    done = threading.Event()
    dropped = threading.Event()

    def probe() -> None:
        misses = 0
        while not done.wait(20):
            misses = misses + 1 if device.shell_ok(serial, 15) is None else 0
            if misses >= 3:
                dropped.set()
                kill_tree(proc)
                return

    with open(log, "w", encoding="utf-8") as handle:
        proc = subprocess.Popen(cmd, cwd=ROOT, env=env, shell=True, stdout=handle, stderr=subprocess.STDOUT)
        watcher = threading.Thread(target=probe, daemon=True)
        if watch and serial:
            watcher.start()
        try:
            code = proc.wait(timeout=GRADLE_LIMIT)
        except subprocess.TimeoutExpired:
            dropped.set()
            kill_tree(proc)
            code = proc.wait()
        done.set()
    took = time.time() - started
    failed = failed_tasks(log)
    verdict = "failed: " + ", ".join(failed) if code and failed else ("DROPPED, killed" if dropped.is_set() else "ok" if not code else f"exit {code}")
    print(f"-- {label}: {took:.0f}s, {verdict}  (log .tmp/gate-{label}.log)")
    return code, dropped.is_set(), log


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
            # psql runs through docker exec, so no host port: a fixed one clashes with other containers.
            "-e", "POSTGRES_PASSWORD=probe", "postgres:16",
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
        # newest patch must apply cleanly onto a project that has the baseline
        # and run again as a no-op, and the suite must still hold afterwards.
        # (The older patches migrate warbands and are not re-runnable here.)
        release = os.path.join(ROOT, "supabase/hosted/2026-10-28-web-snapshot-cap.sql")
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


def retry_instrumented(serial: str, fl: str) -> None:
    """Restart the emulator and rerun the whole instrumented suite, once.

    The emulator can die under load (system_server killed, adb dropped), which fails whichever test was
    running and skips the rest. A second full run on a cold booted emulator separates that from a real
    failure: a suite that fails twice still fails the gate, and the summary says a retry was needed.
    The whole suite is rerun rather than the failed classes because some tests lean on state an earlier
    class sets up (AccessibilityChecksTest finds no "Train" tab on a fresh install), so a class run alone
    can fail for a reason the full order does not.
    """
    print("-- retrying the instrumented suite once")
    if revive(serial, force=True):
        gradle([f":app:connected{fl}DebugAndroidTest"], serial, "retry", watch=True)


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the Ironvellum gate locally.")
    parser.add_argument("--serial", help="device to run instrumented tests on")
    parser.add_argument("--flavour", choices=("foss", "play"), default="foss",
                        help="distribution flavour to build and test (default foss)")
    parser.add_argument("--no-device", action="store_true", help="skip instrumented tests")
    parser.add_argument("--backend", action="store_true", help="also assert the Supabase schema")
    args = parser.parse_args()
    gate_started = time.time()

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
    # Stage 1 is what the instrumented run needs before it can start; it overlaps the emulator boot.
    # Stage 2 is everything else, run in parallel with the instrumented suite. Without a device the
    # instrumented sources are still compiled, so they cannot be committed broken unnoticed.
    stage1 = [f":app:assemble{fl}Debug", f":app:assemble{fl}DebugAndroidTest"]
    stage2 = [
        f":app:test{fl}DebugUnitTest",
        f":app:lint{fl}Release",
        f":app:assemble{other_fl}Debug",
        f":app:assemble{other_fl}DebugAndroidTest",
    ]
    connected = f":app:connected{fl}DebugAndroidTest"
    if serial:
        stage2.insert(0, connected)

    failures = []
    retried = False
    first_failed: list[str] = []
    with device.emulator_lock(f"gate.py {args.flavour}") if serial else contextlib.nullcontext():
        ready: dict[str, bool] = {}
        booting = None
        if serial:
            booting = threading.Thread(target=lambda: ready.update(ok=revive(serial)))
            booting.start()
        code1, _, log1 = gradle(stage1, serial, "build")
        if booting:
            booting.join()
        if code1:
            print("\nGATE RED: the build failed (first failed tasks above; see the log)")
            print_failure(log1)
            return 1
        if serial and not ready.get("ok"):
            print("\nGATE RED: the device does not answer")
            return 1
        code2, dropped, log2 = gradle(stage2, serial, "verify", watch=bool(serial))
        others = [t for t in failed_tasks(log2) if not t.startswith(":app:connected")]
        if others:
            failures.append(", ".join(others))
        inst_bad = bool(serial) and (dropped or any(t.startswith(":app:connected") for t in failed_tasks(log2)))
        if inst_bad:
            retried = True
            first = keep_first_attempt(
                os.path.join(ROOT, f"app/build/outputs/androidTest-results/connected/debug/flavors/{args.flavour}"))
            print(f"-- first attempt failed: {len(first)} test(s) failing (results kept in .tmp/gate-first-attempt)")
            first_failed = first
            for name in first:
                print(f"   FAILED {name}")
            retry_instrumented(serial, fl)
        elif code2 and not others:
            failures.append("gradle")  # a non-zero exit with no failed task named: say so rather than pass
        if code2 and (failures or inst_bad):
            print_failure(log2)

    if args.backend:
        print("-- backend: supabase migrations + assertions")
        if backend():
            failures.append("backend")

    print("-- web: generated assets, tests and production build")
    npm = shutil.which("npm")
    if not npm:
        failures.append("web: npm is not installed")
    else:
        web_commands = [
            [sys.executable, "tools/bodymap_web.py", "--check"],
            [sys.executable, "tools/web_art.py"],
            [sys.executable, "tools/web_art.py", "--check"],
            [sys.executable, "tools/test_web_art.py"],
            [npm, "--prefix", "web", "ci", "--ignore-scripts"],
            [npm, "--prefix", "web", "test"],
            [npm, "--prefix", "web", "run", "build"],
        ]
        for command in web_commands:
            if run(command):
                failures.append("web: " + " ".join(command[1:]))
                break

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
        if retried:
            print("  retried       the instrumented suite ran twice: the first run failed or lost the device")
            for name in first_failed:
                print(f"                first attempt failed: {name}  (results in .tmp/gate-first-attempt)")
    print(f"  lint          {lint_summary(fl)}")
    if args.backend:
        print(f"  backend       {'FAILED' if 'backend' in failures else 'assertions passed'}")
    took = time.time() - gate_started
    print(f"  wall clock    {took // 60:.0f}m{took % 60:02.0f}s" + ("" if took < BUDGET_S else f"  !! over the {BUDGET_S // 60} minute budget"))

    if failures or unit_failed or inst_failed or unit_short or inst_short:
        print("\nGATE RED" + (f": {'; '.join(failures)}" if failures else ""))
        return 1
    print("\nGATE GREEN")
    return 0


if __name__ == "__main__":
    sys.exit(main())
