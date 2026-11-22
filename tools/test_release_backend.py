"""Exercise the real Gradle configuration in a disposable project with synthetic properties."""
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
FIELDS = ("SUPABASE_URL", "SUPABASE_KEY", "GOOGLE_WEB_CLIENT_ID")


def verify(work):
    (work / "app").mkdir()
    for name in ("settings.gradle.kts", "build.gradle.kts", "gradle.properties", "app/build.gradle.kts"):
        shutil.copy2(ROOT / name, work / name)
    shutil.copytree(ROOT / "gradle", work / "gradle")
    (work / "cloud-defaults.properties").write_text("supabase.url=https://shared.invalid\nsupabase.key=public-fixture\n")

    def run(tasks, properties="", flags=(), error=None):
        (work / "local.properties").write_text(properties)
        wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
        result = subprocess.run([str(wrapper), "-p", str(work), "--console=plain", *flags, *tasks],
                                capture_output=True, text=True)
        if error:
            assert result.returncode and error in result.stdout + result.stderr, result.stderr[-2000:]
        else:
            assert result.returncode == 0, result.stderr[-2000:]

    def fields():
        generated = next((work / "app/build/generated/source/buildConfig/foss/release").rglob("BuildConfig.java"))
        return [re.search(r'String ' + name + r' = "(.*)";', generated.read_text())[1] for name in FIELDS]

    foss = [":app:generateFossReleaseBuildConfig", ":app:validateFossReleaseBackend"]
    custom = "supabase.url=https://mine.invalid\nsupabase.key=mine\ngoogle.webClientId=local-id\n"
    run(foss, custom, ["-PlocalOnly=true"])
    assert fields() == ["", "", ""], "local-only generated fields contain configured values"
    run(foss, custom)
    assert fields() == ["https://mine.invalid", "mine", "local-id"]
    run(foss)
    assert fields() == ["https://shared.invalid", "public-fixture", ""]
    run(foss, "supabase.url=https://mine.invalid\n", error="supabase.key")
    run(foss, "supabase.key=mine\n", error="supabase.url")
    run([":app:validatePlayReleaseBackend"], error="google.webClientId")
    run([":app:validatePlayReleaseBackend"], custom, ["-PlocalOnly=true"], error="FOSS")
    run(foss, custom, ["-PlocalOnly=typo"], error="localOnly")
    print("Eight release configuration cases passed")


if __name__ == "__main__":
    if not __debug__:
        raise SystemExit("Assertions must be enabled")
    with tempfile.TemporaryDirectory() as directory:
        verify(Path(directory))
