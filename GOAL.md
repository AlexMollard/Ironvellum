# Ironvellum — Goal Spec (draft for approval)

Solo Leveling-themed Android fitness app for weighted + bodyweight calisthenics.
Repo: D:\Ironvellum.
Stack: Kotlin + Jetpack Compose + Room. Fully custom dark Solo-Leveling-style theme.
Gamified: XP, levels, unlockable titles.

## Objective
Build Ironvellum v1: a local-first workout tracker where preset training days
("Heavy Pull", "Volume Pull") auto-fill the session so you only enter weight and
reps; the app tracks player stats (weight, height, BMI, FFMI over time) and a
gamification layer (XP, levels, titles) reacts to completed workouts. Installable
on the S25 Ultra, verifiable via adb.

## Success criteria
1. `.\gradlew :app:assembleDebug` exits 0.
2. `.\gradlew :app:testDebugUnitTest` exits 0, with tests covering FFMI/BMI math,
   the XP/level curve, and title-unlock rules.
3. App installs and launches on the S25 Ultra via adb without crashing.
4. Workout flow works on device: create a preset (e.g. "Heavy Pull" with exercises,
   supporting weighted AND bodyweight moves) -> pick the day -> session auto-fills
   -> log weight x reps per set -> complete -> XP awarded; level-up fires at
   threshold.
5. Titles system: at least 5 titles with defined unlock rules (first workout,
   level 5, ten pull sessions, ...), unlock state visible in-app.
6. Player stats: weight/height logged over time; BMI and FFMI computed and shown
   with history.
7. Dashboard shows level, XP bar, current title, recent workouts.
8. Theme is fully custom — own design tokens, colors, typography, shapes,
   system-bar styling; no default Material dynamic colors; proven by device
   screenshots.
9. Data export: one action exports everything as CSV or JSON via the Android
   share sheet. Local Room DB only.

## Verification
- Gradle builds and unit tests as in criteria 1-2.
- `adb install`, launch, and screenshot on the S25 Ultra for every UI criterion.
- Scripted adb interaction (uiautomator dumps + taps) to drive the workout flow
  where practical; screenshots as visual proof.

## Boundaries
- All work stays inside D:\Ironvellum.
- Local-only data: no backend, no accounts (schema structured so sync can be
  added later).
- adb is used only to install, launch, screenshot, and read logcat on your phone.
- Never touch anything outside the repo; no release signing keys, no publishing,
  no purchases, no device data changes.

## Stop conditions
Stop and ask the user when:
- the 60-turn cap is hit;
- the same build/test failure survives 3 fix attempts;
- progress requires money, accounts, or publishing;
- a decision would materially change what Ironvellum is.

## Future (explicitly out of scope for v1)
- v1.5 ideas: leaderboards, viewing others' workouts (need a backend).
- Remote database / sync.
