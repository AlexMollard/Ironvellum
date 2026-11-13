# Continue the Wear OS prep on another PC

## Where this stands

The plan and six-screen design direction are approved. The isolated FOSS phone/watch connectivity prototype is implemented; the production Ironvellum watch companion is **not**.

- [Full implementation plan](../../docs/superpowers/plans/2026-11-13-fdroid-wear-os-companion.md).
- [Approved concept mockups](design-mockups.svg): active set, load editor, rest/effort, timed hold, stopped-time adjustment and preparation countdown. These are designs, not screenshots of implemented watch screens.
- [Prototype operation and limitations](README.md).
- Windows verification already passed: both debug APK builds, both lint tasks and 23 JVM tests per variant, plus phone-emulator fixture controls and compiled-artifact checks.
- **Still unpassed:** actual Android app restoration from the private backup, Watch6 pairing/radio operation, native watch-screen runtime, locked-phone/wrist-lowered reliability, cross-brand support, latency and energy.
- No probe was installed on the owner's physical phone or watch during preparation. Watch developer settings were left for later.

Resume at the backup/hardware gates. Do not rebuild the architecture, treat desktop tests as radio proof, or start production workout integration before the transport gate passes.

## 1. Get the source

```text
git clone https://github.com/AlexMollard/Ironvellum.git
cd Ironvellum
```

For an existing checkout, pull normally without discarding local changes. This repository's workflows are manual-only; pushing does not run the local verification for you.

The source, protocol tests, mockups and instructions are in Git. Generated APKs, local screenshots, debug signing keys and the private phone backup are **not**. No release/tag or public backup upload is needed.

## 2. Prepare the build host

Install Git, Python 3.10+, Java 21 and Android SDK command-line tools (or Android Studio). Put the Java 21 installation on `JAVA_HOME`/`PATH`. The checked-in wrapper supplies Gradle; a global Gradle installation is unnecessary.

Install these SDK packages with SDK Manager:

- `platform-tools`
- `platforms;android-37.0`
- `build-tools;36.0.0` for the runner's artifact inspector
- `build-tools;37.0.0` for the current Android build toolchain

Accept the SDK licenses on the new host. No Supabase project, Google sign-in credentials, production signing key or real training data is needed to build the isolated prototype.

Set `ANDROID_HOME` to the actual SDK location. Examples:

Windows PowerShell:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
python tools/watch-spike/verify.py
```

Linux:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
python3 tools/watch-spike/verify.py
```

macOS: use `$HOME/Library/Android/sdk` for the normal SDK installation. The runner also accepts `ANDROID_SDK_ROOT`; `ANDROID_HOME` takes precedence. It selects the host's `gradlew`/`gradlew.bat` and `aapt2` executable. Windows has been exercised; a macOS/Linux build still needs its first real run.

The runner builds/lints both debug variants, checks the executed JVM test counts and inspects the APKs for GMS/Firebase/production classes and INTERNET permission. It does not contact a phone, install anything or alter training data.

If SDK packages or tools are missing, fix that prerequisite rather than suppressing lint, lowering target SDK or skipping artifact checks. Avoid running the main app gate and this build simultaneously on the same host.

APK outputs, relative to the repository root:

```text
tools/watch-spike/probe/build/outputs/apk/phone/debug/probe-phone-debug.apk
tools/watch-spike/probe/build/outputs/apk/wear/debug/probe-wear-debug.apk
```

Do not copy or install the production app APK as part of the probe setup.

## 3. Transfer the private backup separately

The private backup is not in this public repository and was not pushed. Transfer the **entire private backup directory** from the original PC using trusted private storage, preferably encrypted. Keep it outside the checkout on the new PC. It contains private notes and potentially authentication secrets; do not attach it to a GitHub issue or publish it with the probe APKs.

The directory contains its own recovery README, raw credential/device-protected storage archives, exact installed APK, standalone SQLite copy, captured hashes/table counts and verification records. It also contains a prepared Windows emulator-only restore rehearsal script, which has only been syntax-checked so far.

Important distinctions:

- `local-restore-verification.json` records a successful desktop archive/hash/SQLite round trip, not an Android app restore.
- `restore-verification.json` must only represent a successful actual offline Android restore and app launch. That rehearsal was blocked by the shared QA lock and explicitly deferred.
- Follow the private directory's recovery README to complete that gate on the new host. The prepared script is Windows-specific; do not assume it has been ported or tested elsewhere.
- Recheck hashes after transfer. Refresh the backup before hardware testing if workouts or settings have changed since capture.
- `tools/phone_backup.py` alone is a **partial** helper: database/WAL/SHM and preferences, not all photos/files/no_backup/device-protected data or installed APK.
- Android Keystore keys and OS-managed permission/folder grants are not guaranteed portable. Signing in and granting access again may be required after a real recovery.

Never restore over the owner's only live data as a rehearsal. Use a fresh explicitly identified offline emulator so restored account tokens cannot contact the real backend. Preserve the original backup and remove the disposable replay afterwards. Do not interrupt another session holding the shared emulator lock.

## 4. Prepare the Watch6 when ready

Use [Samsung's official wireless debugging instructions](https://developer.samsung.com/sdp/blog/en/2024/04/30/connect-galaxy-watch-to-android-studio-over-wi-fi).

1. Keep the phone and watch normally paired through Samsung's supported companion app. Put watch and PC on the same trusted Wi-Fi network.
2. On the watch: Settings → About watch → Software information → tap Software version five times if Developer options is not already available.
3. In Developer options, enable ADB debugging and Wireless debugging. Record prior settings so they can be restored after testing.
4. Record the connection IP:port from Wireless debugging. Open Pair new device for the separate pairing IP:port and temporary six-digit code. Keep that page open while pairing.
5. Set an `adb` command for the host and run pairing/connection, using the actual endpoints. Enter the pairing code at the prompt; never commit it:

```text
adb pair <watch-IP:pairing-port>
adb connect <watch-IP:connection-port>
adb devices -l
```

On Windows, if `adb` is not on PATH:

```powershell
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
& $adb pair '<watch-IP:pairing-port>'
& $adb connect '<watch-IP:connection-port>'
& $adb devices -l
```

The two ports differ. Wireless ADB pairing is installation/debug access; it is separate from the probe's own authenticated phone/watch pairing.

## 5. Install only the separate probes after the backup gate

Identify the actual phone/watch serials and models first. Never rely on ADB or Gradle selecting the right attached device. Commands below contain deliberate input placeholders; replace them with verified targets.

```text
adb -s <phone-serial> shell getprop ro.product.model
adb -s <watch-serial-or-IP:connection-port> shell getprop ro.product.model
adb -s <phone-serial> install -r tools/watch-spike/probe/build/outputs/apk/phone/debug/probe-phone-debug.apk
adb -s <watch-serial-or-IP:connection-port> install -r tools/watch-spike/probe/build/outputs/apk/wear/debug/probe-wear-debug.apk
adb -s <phone-serial> shell am start -W -n com.ironvellum.watchprobe.phone/com.ironvellum.watchprobe.ProbeActivity
adb -s <watch-serial-or-IP:connection-port> shell am start -W -n com.ironvellum.watchprobe.wear/com.ironvellum.watchprobe.ProbeActivity
```

Use `& $adb` in PowerShell when using the variable above.

Safety boundaries:

- Phone probe: `com.ironvellum.watchprobe.phone`; watch probe: `com.ironvellum.watchprobe.wear`.
- Real training app: `com.ironvellum.app`. Do not clear, uninstall, replace, seed or run connected instrumentation against it on a physical device.
- A new PC creates a different debug signing key. If a previously installed **probe** rejects an update because of a signature mismatch, stop and resolve that probe's signing/reinstallation deliberately. Never fix it by uninstalling the real app. Debug keystores are private, not Git content.
- The existing `smoke.py` is an **emulator-only** harness and refuses physical devices. Do not bypass that guard. Its shared `tools/device.py` workflow is workstation-specific, not required for the manual radio experiment.

## 6. Run the hardware feasibility gate

On both probes, Load bonded devices and grant only their requested Bluetooth/notification permissions. Select the actual other device. Phone listens; watch connects. Compare all 64 fingerprint characters in person and approve both sides only if every character matches. Reject any mismatch.

Record exact watch/phone model, OS version, app version and transport results; do not put private data, raw credentials or pairing codes in the report.

- [ ] Initial bilateral authentication succeeds; rejection and identity-change paths fail closed.
- [ ] Watch reps/load edits update the phone fixture, and phone edits update the watch.
- [ ] Complete → RPE 8 → Undo behaves consistently; stale set/revision and repeated commands do not retarget or duplicate effects.
- [ ] Timer readiness/count-up/Stop is consistent; disconnected watch state is explicitly stale.
- [ ] Phone locked/backgrounded, wrist lowered, Activity reopen and permission withdrawal are handled honestly.
- [ ] Out-of-range/Bluetooth toggle/manual reconnect and lost acknowledgement do not fabricate a saved result or replay a late action.
- [ ] Bluetooth-only operation works without an account or internet; normal watch pairing/music remains usable.
- [ ] Command round-trip, reconnect behaviour and representative workout energy are measured, not inferred from a build.
- [ ] Equivalent coverage passes on a non-Samsung Wear OS watch before broad cross-brand claims.

Only after this gate passes should work proceed to the plan's real workout coordinator, durable receipts/shared timers and approved Wear UI. No proprietary bridge or cloud relay is an approved fallback. If native RFCOMM fails, keep the failure evidence and return to the product decision instead of weakening the requirements silently.
