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
a label, never a reward colour, and never sheen. The cell keeps the 22dp width of
the number so the two steppers still fit a 360dp phone.

**Warm up (exercise menu).** On the open item's overflow menu, "Warm up" adds the
ramp as Warm-up rows ahead of the first working set (`domain/WarmupRamp.kt`). It
is offered only before anything on that exercise is logged, on a loaded rep
movement that has no warm-up yet, and it raises the usual "Warm-up added · Undo"
bar. The rows are ordinary warm-ups: unticked, never paid, never a peak.

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
