# FOSS Wear OS connectivity probe

This is phase-zero experimental software for the [approved companion plan](../../docs/superpowers/plans/2026-11-13-fdroid-wear-os-companion.md), **not the Ironvellum watch app**. The six approved watch designs remain the production direction; this diagnostic screen deliberately exposes transport state instead.

Alex approved the plan/designs on 2026-11-13 and explicitly chose to build/check the isolated prototype while real hardware is unavailable. First intended hardware target: Galaxy Watch6. Cross-brand coverage still requires a non-Samsung Wear OS watch and the plan's broader matrix.

## Isolation

- Independent Gradle build under this directory; root settings and the production app are unchanged.
- Phone package: `com.ironvellum.watchprobe.phone`.
- Watch package: `com.ironvellum.watchprobe.wear`.
- No production database, Health Connect, account, Supabase or photo access.
- No internet permission, Play services, Firebase or Samsung SDK dependency.
- One **synthetic fixture set**, not a copied/reimplemented workout repository. Reps, load, completion/undo, RPE and timer messages exercise real serialization/transport paths without touching training data.
- Fixture data and command receipts are volatile. A service/process restart resets the fixture. This is intentional for the isolated experiment, not acceptable production recovery behaviour.
- Identity and peer pin are app-private preferences excluded from backup/device transfer. Forget clears both; the next pairing uses a new identity.

Native RFCOMM is an unsupported-by-Wear-guidance candidate, **not proven cross-brand transport**. Google directs phone/watch companion communication through Data Layer and cautions against low-level sockets. Building these APKs does not overturn that limitation. Do not integrate the production workout coordinator until actual hardware passes the plan's feasibility gate.

## Build and checks

From the repository root on this Windows workstation:

```powershell
python tools/watch-spike/verify.py
```

The runner uses the repository's Gradle wrapper and version catalog, points at this independent build and selects both `phone` and `wear` debug variants. It builds, lints, runs actual JVM cryptographic/protocol tests, checks expected executed test counts and inspects both resulting APKs for proprietary/production classes and internet permission.

Do not overlap this with the main local gate. To queue behind the shared emulator/build run and then smoke only the disposable phone package:

```powershell
python tools/device.py lock --label watch-probe -- python tools/watch-spike/verify.py
python tools/device.py lock --label watch-probe-ui -- python tools/watch-spike/smoke.py --serial emulator-5554
```

`smoke.py` refuses physical devices and requires the lock helper's matching `ANDROID_SERIAL`. It changes only the disposable phone fixture, captures screenshots under `.tmp/watch-spike/` and force-stops only the probe package. It does not clear, uninstall or launch `com.ironvellum.app`.

Outputs:

- `probe/build/outputs/apk/phone/debug/probe-phone-debug.apk`
- `probe/build/outputs/apk/wear/debug/probe-wear-debug.apk`
- `probe/build/reports/tests/` and `probe/build/reports/` for unit/lint evidence.

The stock Android phone emulator is useful for the phone UI, not a Bluetooth radio or Wear OS proof. Do not call phone-emulator rendering a verified watch UI.

## Real hardware experiment

Use explicit serials and these **separate probe packages**. Do not run the production instrumented suite or `pm clear`/uninstall against an owner's phone/watch. Installing this separate package does not upgrade or replace Ironvellum.

1. Have the phone and watch normally paired through their manufacturer's supported companion setup. The experiment must not replace that pairing or disable normal watch functionality.
2. Install the phone APK on the phone and the wear APK on the watch using explicit `adb -s <serial> install -r <apk>` commands. Wireless ADB requires user-enabled developer settings and pairing; it cannot be guessed or enabled remotely without device access.
3. Open the probe on both. Tap **Load bonded devices** and grant Bluetooth permission; notification permission enables visible service status where applicable.
4. Select the actual watch in the phone's bonded-device list; select the actual phone on the watch. No scanning, hidden Bluetooth APIs or undocumented Google service UUIDs are used.
5. Phone: **Listen for selected watch**. Watch: **Connect to selected phone**. Only one selected bonded peer and one link are permitted. Initial connection has a 20-second timeout; the handshake/confirmation window is bounded.
6. Compare **all 64 hexadecimal characters** of the displayed transcript fingerprint on the two physical devices, in person. Approve on each only if the entire value matches. A screenshot sent over the unverified channel is not an out-of-band comparison. Reject a mismatch. This verbose check is deliberately a diagnostic pairing UI, not the final product experience.
7. Only after both confirmations does the channel become live and the phone send fixture data. Watch controls stay disabled until a confirmed snapshot exists. Reconnect to the same pinned peer needs no repeated manual comparison. Identity changes fail closed and require explicit Forget/re-pair.
8. Change reps/load on each device and observe the other. Complete, rate RPE 8 and undo; undo clears the rating. **Phone: next fixture set** tests target/revision invalidation. A whole-fixture revision conflict refreshes phone state rather than overwriting it.
9. Start the fixture timer: five-second readiness, then count-up toward a 30-second reference. Stop fills the fixture's stopped time, without logging the set. This fixture does not implement the full production hold-adjust/rest/preparation domain.
10. Read command round-trip and clock uncertainty on the watch. Clock mapping uses ping midpoint/minimum observed RTT; it is diagnostic, not the production calibrated Stop-time solution.
11. Lower the wrist, lock the phone, background/reopen each Activity, toggle Bluetooth and move out of range. Record what actually happens. **Disconnect** closes sockets and stops the foreground link; **Forget peer and stop** additionally revokes stored identity. No automatic replay or reconnect is attempted by this experiment.
12. On timeout/disconnect the watch disables controls and marks its timer stale. An action sent before loss can have an unknown outcome; compare the fresh phone snapshot on manual reconnect. No delayed command is silently resubmitted.

A connected-device foreground service owns the link, rather than an Activity-owned socket. There is no continuous wake lock or permanent screen-on request. Handshake, selected-peer, identity-change, lifecycle and notification behaviour still need real-device verification.

## Protocol/security boundary

- Secure OS RFCOMM plus **Noise XX / X25519 / AES-GCM / SHA-256** using the MIT-licensed `org.signal.forks:noise-java:0.1.1` reference implementation. Not home-grown cryptographic primitives.
- App-specific handshake prologue separates this experiment from other Noise consumers.
- Both full transcript confirmations are required before any fixture application channel is exposed. Static remote identity is pinned for subsequent connections.
- Encrypted sequential Noise frames reject changed/replayed ciphertext. Length-prefixed frames are bounded before allocation; protocol payloads have a separate small fixture bound.
- Connection-scoped command identities plus exact set/revision checks protect the fixture from wrong-set writes. The 128-entry receipt cache retains outcomes; directional high-watermarks reject repurposing evicted IDs. This is not production durable receipt recovery.
- Reader, writer and receive-dispatch queues are bounded. Timeouts close the underlying socket to interrupt blocked reads/connect/accept; stale-run callbacks cannot mutate a replacement connection.
- Pairing pin writes are synchronized with cancellation so Forget cannot be undone by a finishing handshake.
- An incoming remote command may not advance to a new fixture set; the phone controls the fixture lifecycle.
- Peer credentials are deliberately not printed to logs. A transcript fingerprint is public diagnostic data, not a reusable authentication secret.

The library being open source and implementing standard Noise **does not constitute an independent security audit**. This experiment's protocol, pairing and lifecycle require review and hardware proof before production use. Never send real health data through it on the strength of a green build.

## Dependency provenance

Noise-Java 0.1.1: MIT, copyright Southern Storm Software, Pty Ltd.; [published POM/license](https://repo.maven.apache.org/maven2/org/signal/forks/noise-java/0.1.1/noise-java-0.1.1.pom), [exact source tag](https://github.com/signalapp/noise-java/tree/noise-java-0.1.1). The POM declares no runtime transitive libraries. JUnit 4.13.2 is test-only and follows the repository version catalog.

The dependency's MIT license is included under [`probe/src/main/assets/licenses/noise-java.txt`](probe/src/main/assets/licenses/noise-java.txt), which is also packaged in each APK. This prototype's sources remain under the repository's GPL-3.0-or-later license. F-Droid inclusion approval, recipe changes and public store distribution are not established by this prototype.

## Completion criteria and evidence

The implementation plan records actual build/test/UI results and the unpassed hardware gate. Do not mark phase zero complete until all of the following are observed:

- Actual phone/Watch6 RFCOMM connection, bilateral pairing, two-way push and failure recovery.
- Non-Samsung watch and intended OS/phone matrix, coexistence with system pairing/music and independently signed updates.
- Locked-phone/wrist-lowered service behaviour, permission revocation, peer forget and reboot/process interruption.
- Measured latency, reconnect behaviour and representative workout energy against baseline.
- Pairing/security review and final dependency/artifact/distribution checks.

If RFCOMM fails this gate, record the exact device/API failure and revisit the approved plan. Do not add a Google bridge, internet relay or a fake loopback watch mode to turn a failed hardware check green.
