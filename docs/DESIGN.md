# Ironvellum design: the Clean direction

Where the app's look is heading. Clean is the app's only appearance and the base; this document is the target every screen moves toward. The
words on screen are governed separately by [GLOSSARY.md](GLOSSARY.md), which
still applies in full.

The reference mockup is the in-progress trial screen (2026-10-06), built from
these rules: one exercise open, the rest folded, a split action bar and slide
to seal.

## 1. Principles

1. **One home per thing.** Every figure, list and action lives on exactly one
   screen. Elsewhere it can appear only as a smaller summary that taps through
   to that home, never as a second copy shown the same way. Before adding
   anything, find where it already lives.
2. **One next action.** Each screen has one obvious thing to do now, and it is
   the only emerald-filled control on it.
3. **Quiet by default.** Structure is neutral grey. Colour is spent only where
   it carries meaning (see section 3).
4. **Fewer boxes.** Box an area only when it holds a group. A single row, a
   figure or a stepper sits on the surface it belongs to.
5. **Undo over confirm.** Reversible actions happen at once and offer Undo.
   Confirmation is kept for what cannot be undone: seal, abandon, delete,
   discard edits.
6. **Land where you meant.** A link opens the exact page, sheet or dialog it
   names (Health Connect, not the Settings hub).

## 2. Surfaces and shape

| Element | Treatment |
|---|---|
| Page | `Abyss` #0C0C0B |
| Card (a group: an exercise, a cycle, a chart) | `Vault` #171715, 1dp `Rune` border, 8dp cut corners |
| Raised (sheet, dialog, the docked action bar) | `VaultHigh` #1E1E1B, flat with no shadow, top rule in `Rune` |
| Primary control (the action bar button, slide to seal) | 8dp cut corners, the same as cards |
| Everything else inside a card | **No box and no corners**: rows, figures, steppers, ticks, tags, chips inside a list |
| Dividers | 1dp straight `Rune` rules, inset to the content edge |
| Dots and progress | True circles; straight segments with a 3dp gap |

Cut corners mark containers and the primary control, nothing else. If a screen
shows cut corners on more than about three things at once, some of those boxes
should go.

Clean today draws every shape with cut corners (`InkEdgeShape`,
`cutCornerPath` in `ui/theme/Ink.kt`). Moving there means inner elements drop their shape and
border, not that the shape changes.

## 3. Colour

| Role (token) | Default | Used for | Never for |
|---|---|---|---|
| `Ink` | #E8E8E4 | Primary text and figures | |
| `InkMuted` | #A3A099 | Secondary text, labels, done or past rows | |
| `Rune` | #32302B | Borders, rules, empty progress | Text |
| Primary accent (`Emerald`) | #34D399 | The screen's one primary action, the active-item marker, filled progress, muscle heat-map fills | Borders, dividers, decoration, headings |
| Link accent (`SystemGreen`, derived from primary) | #6FAE8C | Quiet links and chips (`InkChip`) | Fills |
| Reward accent (`SovereignGold`) | #F2C14E | Earned moments: a peak, XP gained, the seal | Anything not earned |
| `DangerRed` | #EF5350 | Destructive actions and errors | Warnings that are not destructive |
| `Caution` | #E0B25A | A soft warning on a muscle that runs heavy, always with its own mark (a double chevron) | Errors, earned moments, anything the lifter must obey |

Emerald and gold are the default accent pair. Settings → Appearance lets the
lifter choose a preset or customise the primary and reward accents on this
device. Existing token names retain their roles: `Emerald` follows the primary,
`SystemGreen` and `EmeraldBright` derive from it, and `SovereignGold` follows
the reward accent. Paper, ink and destructive red remain fixed. Filled controls
choose a contrasting foreground; the picker previews the pair and flags colours
that may be difficult to see on dark paper.

**Rarity, not accents.** Deed tiers wear a fixed metal ladder (`RarityTint` in `ui/theme`),
never themed and used only for rarity: Common iron #8A8F96, Rare bronze #C98B5B, Fabled gold
#D6A94A, Masterwork prismatic #BFB0F7. An earned deed's glyph and tier word take its metal; the
top two tiers sit on a soft static ring. A locked deed shows its tier word in `InkMuted`.
Crests and relics reuse the same ladder, but a crest is flat line art in one fixed diagonal gradient per crest (never the lifter's accent), a ring in that same ramp, and a faint wash (12% normally, 18% for Aurora's light curtain). Void alone has a dark negative-space centre. No solid metal fill, bevel, highlight or sheen. `SovereignGold` stays the reward accent and is not the
Fabled gold.
Speak in roles, not hues: the lifter may have chosen sapphire and amber. "Primary" is the action
colour, "reward" is the earned colour, "link" is the quiet accent.

Rule of thumb: on any one screen, the primary accent appears on at most three things (the
action, the active marker and progress), and the reward accent only where something was
earned.

## 4. Type

Chakra Petch throughout, at Material's type roles (`ui/theme/Theme.kt`).

| Role | Use | Notes |
|---|---|---|
| headlineSmall bold | Tab title ("TRAIN", "THE LEDGER") | One per screen |
| titleLarge | A hero figure (today's weight, a peak) | One per card at most |
| titleMedium | A card's subject (exercise name, rite name) | |
| bodyMedium | Row labels | Sentence case |
| bodySmall / labelSmall | Sublines and secondary figures | `InkMuted` |
| labelSmall, caps, tracked | Section labels ("YOUR CYCLE", "NEXT") | **One per area.** Caps tracking is a signpost; repeated, it becomes noise |

Numbers are sized by their role, never inflated to fill space. A stepper's
value is around 22–24sp, not a display numeral.

Case: section labels are in caps. Rows, chips, buttons and links are in
sentence case ("Full chronicle", "Log set 2", "Add a note").

## 5. Spacing and targets

- Gutter 16dp. A gap between cards of 12–16dp. Inside a card, 16dp padding.
- Touch targets at least 44dp, and 48dp for anything used mid-trial.
- List rows are 52dp. A row with a subline grows to fit; it is never squeezed.
- Unrelated controls sit at least 16dp apart. Nothing reversible sits next to
  the primary action.
- White space is how groups are separated. Reach for a gap before a rule, and
  for a rule before a box.

## 6. Components

**Row.** Leading icon (optional, `InkMuted`), then the label in bodyMedium with
an optional subline, and a trailing value or chevron. 52dp. Used for ways out
(Exercises, Weekly coverage), settings and end-of-list actions ("Add exercise",
"Add a note", "Name this trial").

**Folded item.** One line for an item that is not open. It shows its state:

| State | Look |
|---|---|
| Not started | Name in `Ink`, subline with the plan ("3 × 8 · 60 kg"), chevron |
| Partly done | Subline "1/3 sets", with a small segment bar before the chevron |
| Done | A small emerald ✓ glyph (no box), name in `InkMuted`, subline with the result ("3/3 · best 60 kg × 8") |

**Open item.** The one item being worked on, in a card. Its title, then one
quiet reference line ("Last 6 × 7.5 kg · Peak 6 × 10 kg", with the peak in
gold), then its rows. The active row is marked with a thin emerald left bar
and a faint tint, not an outline.

**Set number and type tag.** The number at the start of a set row is also the
set's type control: tapping it opens a small menu of the five set types
(Working, Warm-up, Drop, Failure, Rest-pause) and "What are set types?". A warm-up
reads "W"; Drop, Failure and Rest-pause add a 10sp letter under the number
(D, F, R) in a muted token (`SystemGreen`, `DangerRed` at 80%, `Ink` at 85%).
A plain working set adds none, only a faint caret on the active row. The tag is
a label, never a reward colour, and never sheen. The cell is 24dp wide, the
WCAG floor for a target, and the row's start padding gives the 2dp back so the two
steppers still fit a 360dp phone.

**Warm up (exercise menu).** On the open item's overflow menu, "Warm up" adds the
ramp as Warm-up rows ahead of the first working set (`domain/WarmupRamp.kt`). It
is offered only before anything on that exercise is logged, on a loaded rep
movement that has no warm-up yet, and it raises the usual "Warm-up added · Undo"
bar. The rows are ordinary warm-ups: unticked, never paid, never a peak.

**Warm-up exercises (rite).** A rite entry can be marked "Warm-up" in the editor: a whole
movement (a drill such as Knee-to-Wall Dorsiflexion), not a set. Warm-ups lead the rite, are in
no superset and take no scheme. A trial begun from the rite makes no set rows for them, so they
earn no XP, volume, coverage, peak, progression change, streak hard set or deed, and never reach
the end-of-trial rite offer; they are listed instead as tickable items in the trial's warm-up
step (`WarmupStep.Item`, ticks kept per trial in `WarmupStore`). Everything that counts a rite's
work reads `WorkoutPreset.working` (or `WorkoutPreset.toPlanned()`, which the Forge, the coverage
maps and the plan line share), and the Forge puts a rite's warm-ups back when it rewrites it.
The archive carries the flag (format 13); the share code carries the worked plan only.

**Superset (live trial).** Adjacent exercises done as one group, a pair or a triple
(`domain/Supersets.kt`). An exercise's menu offers "Superset with <next exercise>" while
the next one can be linked (a group never passes three); linking writes one number on
every set of the members. Linked exercises share a thin rail in the left gutter, a 2dp
line in `SystemGreen` at 60% between two 8dp square dots, drawn in the gutter's 16dp so no row
moves. The open card carries "Superset 1 · Barbell row next" under its name with an
"Unlink" text action (it ends the whole group); folded members read "Superset 1 · 1/3
sets". A group is read as a superset only while its members are neighbours, so a number
left behind by a removal or a reorder is just not one. Ticking a set opens the partner's
card (A1, B1, A2, B2: the member with the fewest sets done goes next, the earlier on a
tie), judged from the sets alone, so the lock-screen "Set done" and the notification name
the same set. Rest starts only on the tick that closes a round; mid-round the footer
reads "NEXT · NO REST YET", and a rest still running from the round before is ended.
Move up and Move down keep a group whole: a lone exercise steps over a whole group, and a
member that moves out past its group's edge leaves it.

**Superset (rite editor).** An open exercise card has a "Superset" row after Modifiers,
valued "None" or "With Bench press" (a triple reads "With A and B", in `SystemGreen`).
It opens a "Superset with" pick of the other exercises, with "None" to end the group; a
partner that is not next door moves beside the entry, and one that would make four is
greyed. Linked entries share the same gutter rail as the live trial (`SupersetRail.kt`),
and a folded row reads "Superset 1 · 3 × 8 · 60 kg". The group is a number on the rite's
entries (`preset_entries.supersetGroup`); saving keeps it only where the members are
neighbours, a trial begun from the rite starts with it on every set, and the archive
(format 11) writes it only for an entry in one. The Forge never makes or reads a
superset; tempering a rite keeps the lifter's own where the pair is still adjacent.

**Lock-screen trial actions.** The trial notification is public on the lock screen
and carries the movement, the set and its figures, never a note. Its buttons are
broadcasts, so they run without unlocking: while a set waits, "Set done" (ticks it
with the figures shown, starting a rest exactly as the log button does); while
resting, "Skip rest" and "+15 s"; otherwise "Seal", which opens the app. Android
shows three actions at most, so Seal waits until nothing else is on offer. They
call the same code as the screen (`data/LiveTrialActions.kt`, `data/RestClock.kt`).

**Pocket lock.** A lock icon in the live trial's header covers the screen with
`Abyss` at 97%: a lock glyph, the rest countdown (56sp, `Ink` at 85%) or, between
rests, the set in hand, and "Hold to unlock". It swallows every touch and the back
button. A finger held for 1.2 seconds unlocks it, shown by a 3dp `Emerald` bar
filling along the bottom edge; a tap, a swipe or a brush does nothing. Holding is
not possible for everyone, so it also offers an "Unlock the trial" accessibility
action and a long-click. Nothing about the trial pauses underneath it. The screen
stays awake for the whole live trial unless Settings (DURING A TRIAL) turns that off.
Logging a set gives a `Confirm` haptic and every stepper press a `SegmentTick`, both
through `LocalHapticFeedback`, so the system's own haptics setting decides.

**Timed set control.** Under the steppers of the active set of a hold or a duration,
one line: the time in ChakraPetch bold 22sp (`Ink` while it runs, `InkMuted` while idle),
a caption, and a plain text action. Start opens a 5-second "GET READY" (5, 4, 3, 2, 1, ticks
on 3, 2, 1 and a short cue at go), then the clock counts up from 0:00 under "TARGET 1:00",
the set's figure as a reference. Reaching it cues once, turns the time `SovereignGold` and
the caption to "TARGET REACHED" (words as well as colour, announced once); the clock keeps
counting and never logs. Stop during the get-ready records nothing; after it, Stop opens the
adjust step: the stopped time, -1s and +1s (hold to repeat, 1 s floor, no ceiling), "Log 0:42"
and Discard. A timed set can always be logged without the clock: ticking the row logs the figure in
it. Ticking while the clock counts stops it into the adjust step; ticking in the adjust step
logs its figure. A distance run shows "ELAPSED" and one Start stopwatch / Stop action: Stop
fills the time and leaves the set for the lifter to tick. Cues follow the rest alert (Vibrate,
Sound + vibrate or Silent). Its clock is `domain/SetClock.kt`, on the monotonic clock and held
by the view model. There is no timed control in an amendment.

**Stepper.** Label (labelSmall, `InkMuted`) above a value with plain − and +
glyph buttons (44dp targets, no borders). Tapping the value types it.

**Units.** Loads are stored in kg and shown in the unit set under Training mode
(kg or lb). Everything that prints, steps or reads a load goes through one
helper, `Loads` (`LoadUnits.kt`), reached on screen as `Units`: lb shows to the
nearest 0.5, typed lb converts at the exact pound (0.45359237 kg) and is kept to
the gram, and the ± buttons walk 5 lb on compounds and 2.5 lb on small work
(from the 5 / 2.5 / 1.25 kg progression steps; a sled's 10 kg is 10 lb), where kg
walks its 2.5 kg plate grid. Progression advice in lb moves on the lb grid, so
"Last 220.5 lb" is followed by 225 lb. Left in kg on purpose: the CSV export (a
Strong-format file whose reader assumes kg), body weight and measurements, and
the Forge's generated load notes and dumbbell limit.

**Effort (RPE and RIR).** Optional, per set, off by default. Settings > Training mode,
"Effort per set": a "Log effort" switch and an RPE | RIR choice. The figure stored is one
whole-number RPE on the set, 5 to 10 (`domain/Effort.kt`): RIR is 10 minus it, so changing
scale loses nothing, and anything at or under 5 is stored as 5 (the "≤5" chip, "5+" on
RIR). With the switch on, logging a set opens a strip under it on a faint `Ink` 4% wash,
in the 46dp inset of the set number: "How hard was set 2?" with the scale's name at the
right in 10sp spaced caps, six chips (44dp targets, 36dp outlined boxes in `Rune`; the chosen
one `Emerald` at 16% with an `Emerald` rule and bold `Ink` text), and under them a muted line
for the chosen chip ("RPE 8: about two reps left", "2 RIR: two reps left") or what the scale
means. Skipping costs nothing: the strip never blocks the next set, and tapping the chosen chip
again clears it. In a superset the partner's card opens at once, so the strip moves above it
and names the exercise. A logged row shows its effort muted at the right ("RPE 7", "2 RIR"),
even with the switch off. Tapping a logged set to edit it (and amending a sealed trial) offers
the same chips. Un-logging a set, or making it a warm-up, takes its rating off. Elsewhere:
the folded done row and the "Last" line carry the top set's effort; the trial detail reads
"Set 3 · 225 lb × 5 · RPE 8"; the share card adds only the top set's effort after the load
("3×5 @ 225lb · RPE 8", never "@8") behind an "Include effort" switch beside "Include
exercise notes", offered when a set was rated; the CSV export has an RPE column after Set
Type; a Strong or Hevy import fills the field (halves round up, 5 or less reads 5) instead
of writing "RPE 8" into the set's modifiers. Progression never reads it.

**Plates (`PlateSheet`).** A chip beside the open item's reference line on a barbell
lift, opening the app's one pop-up frame. A load stepper that only lands on loads
the plates can make, a tappable bar row (a per-exercise bar), an iron line-art
barbell drawn in the flat diagonal Iron gradient with a faint wash in each plate
(no sheen), "25 + 15 kg a side", and, when the load falls between plates, the
closest build below with "Use X" / "Use Y" for the loads either side. A load is
loadable when some choice of the pairs owned makes it exactly, heaviest plates
tried first, so the drawing can always show what the sheet offers. The bar and the
pairs live under Armoury, "Bar and plates", once Barbell is ticked; kg and lb each
keep their own set (lb: a 45 lb bar, 45 / 35 / 25 / 10 / 5 / 2.5 lb plates).

**Split action bar.** Docked at the bottom on `VaultHigh`. On the left, a small
caps label ("NEXT") over what the action does ("Set 2 · 10 kg × 6", in `Ink`).
On the right, a 52 × 52dp emerald button. It reads as a row you tick, not a
slab. The bar's slot changes with the state: the rest timer while resting, the
next set when ready, slide to seal when everything is logged.

**XP in the trial.** The ticked sets' XP runs live, priced by the one function the seal uses
(`SessionXp.tally`), so the running total is exactly the set-driven part of the award. A
"+164 XP" chip sits in the trial header (a polite live region), each ticked row ends in a
small `EmeraldDeep` "+24", and a lift's "Pull-up +24" floats under the chip for about a
second and goes (it shows and goes without fading when animations are off; it overlays, so
nothing moves). Bonuses land at the seal. While a rest runs, the rest bar asks the set just
logged for its effort even with the switch off: a second line under the timer ("Set 2: reps
left?" on RIR, "how hard?" on RPE), five 48dp chips on the lifter's scale (RIR 0 1 2 3 4+, RPE
≤6 7 8 9 10) in the `EffortStrip` shape (the `shapes.small` cut, the border following it, the
chosen one `Emerald` at 16%). A tap rates the set and the line collapses to a quiet
"Set 2 · 2 RIR ✓" with Change; the block keeps its 76dp so nothing jumps, the footer grows by
it only while the rest shows, and the rest keeps counting. The under-row strip stays where no
rest bar shows (no timer, a hold) and only when the switch is on.

**XP reveal (`XpRevealPanel`).** The summary's XP panel: "+N XP" in `Emerald` with the level in
`SovereignGold` caps, then one bar that fills in the order of the trial, a segment per
movement in emerald shades (deep to light) and then each named bonus in gold shades, flat, with
2dp gaps in the dark and the top-left corner cut, continuing from the XP already in the level.
The segment filling is at 55% and named under the bar ("▲ Chin-up +38"); settled, the line
reads "248 effort · 110 bonus". Under it an EFFORT section, a row per movement (a diamond in its
shade, the name, "4 hard sets · +17.7kg", the amount in its shade) and a BONUSES section; rows not
reached are dimmed with "—", the current one on `Emerald` 8%. A segment that crosses a level
finishes that bar and carries the rest into a fresh one under "LEVEL 17", and "16 → 17" lands in
the Fabled gradient. Everything shown is the breakdown the award is the sum of: nothing is
priced on screen. With animations off the final state shows at once; each row reads whole to a
screen reader whatever the animation has reached.

**Slide to seal.** For commits that pay out and are costly to undo. A neutral
track with a centred label. The fill grows from the left, and its leading cap
(with a chevron) is the knob: one control, not a button sitting on a track.
Emerald while dragging, gold once sealed. Released early, it springs back; at
the end it snaps and plays a short seal flourish. It always also offers an
accessibility action ("Seal the trial") so it works without dragging, and it
respects reduced motion.

**Chip (`InkChip`).** Outlined, sentence case, `SystemGreen`, with an optional
leading icon. For secondary actions in a header ("Open trial", "Share",
"History"). At most two per header.

**Tag.** Small caps text with no box ("NEW PEAK" in gold). It never takes a tap.

**Yours tag.** An exercise the lifter made carries an outlined `Ink` "YOURS" mark
beside its name: 9sp caps, a 1dp `InkMuted` rule, 3dp-ish corners. It is a mark of
ownership, not an award, so it is never gold and it never takes a tap. The picker also pins a
"Yours" section above Favourites; those exercises still sit in their normal category too.

**Exercise form.** A full-screen sheet over the picker (not a route): Name with a
`13 / 40` counter, then Type, Logged as and Needs as underline chips, then the
front and back figure to tap. A tap on a muscle cycles assist, main, off (MAIN reads
1.0, ASSIST 0.5, the library rows' own scale), with the MAIN and ASSIST lines beside the figure. Weighted is
never asked: any barbell, dumbbells or machine or cable makes it weighted. "New exercise"
sits under Filters in every picker, and a search that finds nothing offers
`Create "<query>"` with the name filled in. Edit lives on the info sheet; Delete only
appears when no set, rite or note uses the exercise, so history is never orphaned.

**Custom exercise scoring.** A custom exercise is priced at tier I (intensity 1, the
cheapest tier a catalogue movement has, where curls and raises sit) and a machine
or cable one at the sled load band (0.70). Inventing a name can therefore never earn more per
rep than the cheapest real movement; the lifter's own history, peaks and charts are
unaffected. Its name may never match a catalogue, library, skill-tree or board
name (case-insensitive), which is also what keeps it off every ally board. It is never prescribed by
the Forge. Cardio and Mobility ones are activities and use the activity pricing.

**Optimise rites (Weekly coverage).** The coverage dock offers "Optimise rites" beside "Forge a rite"
(Forge is the filled one when both show; each shows only when it has something to do, and Optimise only on
the Planned view). It opens one screen of small pages, each left with Back: the hub (Gaps, Heavy, Overlap), a gap's three ranked
movements in the bottom sheet and then "Put it on" (the rite, the hard sets, the effect on the week), a heavy
muscle's changes, one change opened, two movements doing one job, the days, and what was applied. A ranked
movement is a radio row: its number, the name, a small caps "BEST MATCH" on the first, one line of why and what it
would be prescribed; the chosen row has the thin Emerald bar and a filled ring (`RankedOptionRow`). A change is a
card (kind in caps, title, why, a chevron) with "N more options" and a 48dp Keep | Skip radio group under it
(`ChangeCard`, `KeepSkip`); MOVE changes start on Skip. "What it fixes" and "Effect on your week" list each muscle
that moves with its state mark and "Covered, 6 → Below minimum, 3 hard sets" (`ImpactList`); the effect is a polite
live region. Nothing is written until Apply; Apply writes every touched rite in one transaction and the next page
says what was applied with "Undo, put Upper A back". Every state is a word as well as a mark, and the Heavy mark
stays the ring with a double up-chevron.

**Day sheet (saving a rite).** Saving a single rite from the Forge asks which weekday it goes on, in the app's
bottom sheet: a four-column grid of Mon to Sun and "No day" (`PickCell`), the first free day ticked, and one line
under it that says what the choice means ("Wednesday is free.", who already holds a taken day, or that a rite
with no day is not counted in Weekly coverage). Weekly coverage says when rites have no day
("1 rite has no day, so it isn't counted") as a quiet link to that rite, or to the rites list when there are
several.

**Pushed screen header.** Title on the left and the BACK chip on the right
(`NavChip`), the same on every pushed screen. No left arrows.

## 7. Feedback and safety

Press feedback covers the whole control immediately with a faint ink wash,
clipped to its existing shape. Buttons and rows share this treatment instead
of an expanding Android ripple. Disabled controls stay unchanged.
Grouped navigation rows use `InkRowPanel`: padding belongs inside each row,
so its tap target and feedback reach the panel edges. Chips, tabs, icon actions,
steppers and clickable panels use the same shared indication.

- Logging, ticking and other reversible actions show "Set 2 logged · Undo" for
  about 5 seconds. Any done row can be tapped to edit or un-log it.
- Back from an editor with changes asks "Discard your changes?" (Keep editing /
  Discard). Without changes, it leaves at once.
- Sealing with sets still unlogged asks first. The track says so beforehand
  ("4 sets left · slide to seal").
- Sealing a trial too soon can be undone from the victory screen for a short
  window ("Sealed too soon? Keep going"). After that, amending from the
  Chronicle is the path (`domain/SealedEdit.kt`).

## 8. Moving the app over

Screen by screen, each as its own commit. For each screen:

1. Remove duplicates first (principle 1), then restyle what remains.
2. Strip boxes from everything inside cards (section 2).
3. Bring emerald and gold back to their jobs (section 3).
4. Cut caps labels to one per area and use sentence case elsewhere (section 4).
5. Check targets and gaps (section 5).
6. Verify on the phone.

Order: the trial screen (the reference), then Today, Train, the Ledger, the
Codex, Allies and Settings.

Decided (2026-10-06): Ink is retired and removed completely. Clean is the only
appearance, so screens no longer need checking in two styles.
