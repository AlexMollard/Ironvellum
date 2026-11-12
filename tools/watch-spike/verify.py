"""Build/check only the isolated probe; no emulator or production app mutations."""
from __future__ import annotations

import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
PROBE = Path(__file__).resolve().parent


def main() -> None:
    env = os.environ.copy()
    env.setdefault("ANDROID_HOME", str(Path(env["LOCALAPPDATA"]) / "Android/Sdk"))
    tasks = [f":probe:{task}{variant}" for variant in ("PhoneDebug", "WearDebug") for task in ("assemble", "lint")]
    tasks += [f":probe:test{variant}UnitTest" for variant in ("PhoneDebug", "WearDebug")]
    command = [str(ROOT / "gradlew.bat"), "-p", str(PROBE), *tasks, "--console=plain", "--max-workers=2"]
    result = subprocess.run(command, cwd=ROOT, env=env, check=False)
    if result.returncode:
        raise SystemExit(result.returncode)
    expected = sum(len(re.findall(r"@Test\s+public void", path.read_text(encoding="utf-8")))
                   for path in (PROBE / "probe/src/test").rglob("*Test.java"))
    assert expected > 0, "no declared prototype tests"
    for variant in ("PhoneDebug", "WearDebug"):
        reports = list((PROBE / f"probe/build/test-results/test{variant}UnitTest").glob("TEST-*.xml"))
        suites = [ET.parse(path).getroot() for path in reports]
        tests = sum(int(s.attrib["tests"]) for s in suites)
        problems = sum(int(s.attrib.get(k, "0")) for s in suites for k in ("errors", "failures", "skipped"))
        assert tests == expected and problems == 0, f"{variant}: {tests}/{expected} tests, {problems} failures/errors/skips"
        print(f"PASS: {variant}: {tests} tests; zero failures/errors/skips", flush=True)
    for flavor in ("phone", "wear"):
        apk = PROBE / f"probe/build/outputs/apk/{flavor}/debug/probe-{flavor}-debug.apk"
        with zipfile.ZipFile(apk) as archive:
            names = archive.namelist()
            for name in names:
                if re.fullmatch(r"classes\d*\.dex", name):
                    dex = archive.read(name)
                    for forbidden in (b"Lcom/google/android/gms/", b"Lcom/google/firebase/", b"Lcom/ironvellum/app/"):
                        assert forbidden not in dex, f"{apk.name}: unexpected production or proprietary classes: {forbidden!r}"
        aapt = Path(env["ANDROID_HOME"]) / "build-tools/36.0.0/aapt2.exe"
        permissions = subprocess.run([str(aapt), "dump", "permissions", str(apk)], capture_output=True, text=True, check=True).stdout
        assert "android.permission.INTERNET" not in permissions, "probe unexpectedly declares internet access"
        assert "android.permission.BLUETOOTH_CONNECT" in permissions, "missing Bluetooth permission"
        print(f"PASS: {apk.name}: {apk.stat().st_size} bytes; no GMS/Firebase/production classes or INTERNET permission", flush=True)
    print("NOT VERIFIED: physical watch radios, pairing UI on two devices, background reliability, cross-brand support, latency or energy", flush=True)


if __name__ == "__main__":
    main()
