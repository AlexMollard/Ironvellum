# Sidenotes

Last audited 2026-11-12 against the current 1.11 source. Open boxes are unresolved; completed entries retain their verification. A historical visual complaint is not proof that the current screen still has it.

- [ ] [2026-09-15] I feel like the deeds tab in the codex window is very dese and could do with a little ui/ux work to make it more digestible
  ↳ Equalising empty-state art stroke weight across dashboard/stats after the production-readiness wave · Codex screen deeds tab (app/src/main/kotlin/com/ironvellum/app/ui/titles/DeedsBoard.kt) · main@e5816f8
  Audit: keep as a fresh visual review, not an automatic redesign. Deeds now has close-to-earning rows, category drill-down and grouped ladders (`DeedsBoard.kt:199-220,271-304`); whether it still feels dense needs current screenshots.
- [ ] [2026-09-29] I think a future plan would be to overhaul the social features to compete with the big hitters
  ↳ after tagging v1.3 and pushing F-Droid MR !49794 · Allies tab (ui/social) · main@1ad24e8
  Audit: direction, not a scoped build task. Comments, reactions, inbox, lift boards, sharing and circles already have concrete contracts in `docs/SOCIAL_PLAN.md:16-23,84-94`. Prefer specific gaps such as rolling XP rankings over another blanket overhaul.
- [ ] [2026-09-30] skill-tree descriptions clipped at 8-9sp, no legend for roman tiers
  ↳ extracted from the v1.4 FTUE leftovers · Codex skill paths · main@5b904b9
  Audit: worthwhile readability follow-up. `SkillTree.kt:151-176,300` still permits 8sp labels; current clipping has not been reverified. An I-V introduction now exists (`TitlesScreen.kt:438-440`), but its adequacy as a tier legend still needs a current visual review.
- [ ] [2026-09-30] Training mode in Settings lacks Skills/Mixed from onboarding
  ↳ extracted from the v1.4 FTUE leftovers · training preferences and generated routines · main@5b904b9
  Audit: clarify the concepts, not a confirmed missing enum option. Settings changes the two progression modes (`TrainingModeSettings.kt:43-64`); onboarding chooses four generation focuses (`OnboardingScreen.kt:1134-1156`). Skills/Mixed deliberately do not replace the progression mode (`ProgramEditing.kt:151-162`).
- [ ] [2026-09-30] Victory repeats +XP / total XP on first run
  ↳ extracted from the v1.4 FTUE leftovers · first-workout celebration · main@5b904b9
  Audit: keep for a targeted first-workout review, not a confirmed current duplicate. The simple extra XP row is guarded (`VictoryOverlay.kt:287-305`), but this audit has not visually rechecked the full celebration sequence.
- [ ] [2026-09-30] "5 × BW" spacing vs glossary "5×5"; 'Activity'/'Ledger'/'STR' wording.
  ↳ extracted from the v1.4 FTUE leftovers · plain/themed copy and numerical notation · main@5b904b9
  Audit: low priority; no current functional defect established. Stats/Ledger are intentional plain/themed variants and Activity also names Health Connect data. Revisit only against specific current labels, not as a global terminology rewrite.
- [x] [2026-11-11] We should add a rolling leaderboard as the xp leaderboard will always have the same guy on top everyday if hes used the app teh longest which kinda makes the leaderboards useless to others
  ↳ Independently disproving five social bugs before fixing confirmed findings · social leaderboards · main@005d86f8
  Completed 2026-11-12: rolling 168-hour workout XP on Android and web, separate recent candidates and retained Lifetime XP. Verified Android metric switching, real screenshots, 31 web checks and six Postgres privacy/window mutations with 18 restored full-suite passes. Apply the schema-33 hosted patch before release; it has not been deployed by this task.

## Cleanup record — 2026-11-12

Removed the completed Muster navigation-slot note: the Veil/Garrison is reached from Today, not a sixth bottom-nav slot (`IronvellumNav.kt:276-285`).

Removed the duplicate Social notification-badge proposal: the root already observes `inboxUnread`, draws an 8dp unread dot and announces the count (`IronvellumNav.kt:272-274,967-1024`).

Retired 14 obsolete v1.4 FTUE checks: Settings header/profile summary; Garrison first-run copy; section-header glyph; PickCell selection; deed/title award wording; Update routine/Keep routine naming; per-card rest-note truncation; XP text over the bar fill; backend primary-button hierarchy; Support/About flavor naming; dash-only BMI history; signed-out Allies title; onboarding row affordance; and the old Codex filter sizes/CLOSE label.

Source checks: `SettingsScreen.kt:83-102,151-154`; `strings.xml:4003-4005,4568-4569`; `Common.kt:215-226`; `ProgramPreview.kt:139-155,198-218,668-705`; `VictoryOverlay.kt:287-305`; `CelebrationPages.kt:610-611`; `SessionScreen.kt:4911-4920`; `OnboardingScreen.kt:1264-1277,1302-1317`; `TodaySoul.kt:163-219`; `AdvancedSettings.kt:145-164`; `StatsScreen.kt:670-696`; `AccountScreen.kt:524-526,603-604,635-640`; `TitlesScreen.kt:399-417`; `InkChip.kt:53-65`.
