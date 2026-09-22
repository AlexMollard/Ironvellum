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


def lint_summary() -> str:
    path = os.path.join(ROOT, "app/build/reports/lint-results-release.txt")
    if not os.path.exists(path):
        return "no report"
    with open(path, encoding="utf-8", errors="replace") as handle:
        lines = [line for line in handle.read().strip().splitlines() if line.strip()]
    return lines[-1] if lines else "empty report"


def gradle(tasks: list[str], serial: str | None) -> int:
    env = dict(os.environ)
    if serial:
        env["ANDROID_SERIAL"] = serial
    # gradlew.bat, via cmd, is how this repo is driven on Windows.
    joined = " ".join(tasks)
    return run(f'cmd /c "gradlew.bat {joined} --console=plain"', env=env, shell=True)


def backend() -> int:
    """Apply every migration to a throwaway Postgres and assert the guarantees.

    Mirrors ci.yml's `backend` job: the stub, the migration chain, the
    declares-idempotent-or-immutable check, the re-apply, then assert_all.sql.
    """
    migrations = sorted(glob.glob(os.path.join(ROOT, "supabase/migrations/*.sql")))
    if not migrations:
        print("!! no migrations found")
        return 1

    undeclared = []
    for path in migrations:
        with open(path, encoding="utf-8", errors="replace") as handle:
            if not re.search(r"idempotent|immutable", handle.read(), re.I):
                undeclared.append(os.path.basename(path))
    if undeclared:
        # The re-apply below keys off that word, so a file declaring neither
        # would silently opt out of the idempotency check.
        print(f"!! migrations declare neither idempotent nor immutable: {undeclared}")
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

        steps = [os.path.join(ROOT, "supabase/test/supabase_stub.sql")] + migrations
        # Re-apply only the files that CLAIM idempotency; the early ones are
        # declared immutable and re-running them is expected to fail.
        for path in migrations:
            with open(path, encoding="utf-8", errors="replace") as handle:
                if re.search(r"idempotent", handle.read(), re.I):
                    steps.append(path)
        steps.append(os.path.join(ROOT, "supabase/test/assert_all.sql"))

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
    parser.add_argument("--no-device", action="store_true", help="skip instrumented tests")
    parser.add_argument("--backend", action="store_true", help="also assert the Supabase schema")
    args = parser.parse_args()

    serial = None
    if not args.no_device:
        serial = pick_serial(args.serial)
        if serial is None:
            return 1
        print(f"-- instrumented tests pinned to {serial}")

    tasks = [":app:assembleDebug", ":app:testDebugUnitTest", ":app:lintRelease"]
    if serial:
        tasks.insert(2, ":app:connectedDebugAndroidTest")
    else:
        # Without a device, at least prove the instrumented sources still
        # compile — assembleDebug does not build src/androidTest, so they can
        # be committed broken and nobody notices until a cable appears.
        tasks.append(":app:assembleDebugAndroidTest")

    print(f"-- gradle: {' '.join(tasks)}")
    failures = []
    if gradle(tasks, serial):
        failures.append("gradle")

    if args.backend:
        print("-- backend: supabase migrations + assertions")
        if backend():
            failures.append("backend")

    unit_total, unit_failed = counts("app/build/test-results/testDebugUnitTest/*.xml")
    inst_total, inst_failed = counts(
        "app/build/outputs/androidTest-results/connected/**/*.xml"
    )
    print("\n=== gate ===")
    print(f"  unit          {unit_total:>4} tests, {unit_failed} failed")
    if serial:
        print(f"  instrumented  {inst_total:>4} tests, {inst_failed} failed")
    print(f"  lint          {lint_summary()}")
    if args.backend:
        print(f"  backend       {'FAILED' if 'backend' in failures else 'assertions passed'}")

    if failures or unit_failed or inst_failed:
        print("\nGATE RED")
        return 1
    print("\nGATE GREEN")
    return 0


if __name__ == "__main__":
    sys.exit(main())
