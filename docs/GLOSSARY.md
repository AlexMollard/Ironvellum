# Ironvellum glossary

The single source of truth for every word the app shows a user. If a screen,
notification, share card, deed or error message names one of these concepts,
it uses the term here and no other. A new concept gets an entry here before it
gets a string in the code.

## The world

Dark fantasy in ink on vellum, matching the house art (sumi-e brush work, bone
ink on black). No armies, no ranks of soldiers, no modern game-UI words.

The app is **the Ledger**, an iron-bound book that records what the
**Ironbound** do. Each week an Ironbound keeps a **Cycle** of **Rites**.
Performing a rite is a **Trial**, and every trial they **seal** is written into
their **Chronicle**. Each trial leaves an **Echo** beyond **the Veil**, where
echoes gather **essence**. **Deeds** earn **Titles** to wear; **Paths** hold
the techniques to master. Allies gather in **Circles** and are measured in
**the Reckoning**.

## Rules

1. **One name per concept.** Never a synonym, never the plain word "for
   clarity" in one place and the themed word in another.
2. **One meaning per word.** A themed word is never reused for a second
   concept. "Seal" means finishing a trial, so private things are "private",
   not "sealed". "Title" means a deed's title, so a trial's headline is its
   "name".
3. **The Ledger is the only narrator.** Loading, empty and error lines speak in
   its voice on every screen, plain screens included. It addresses the user as
   "you", or speaks of "the Ironbound". Its register is ink, pages, shadow,
   ash, oaths and the dark; never soldiers, orders, fronts or marching.
4. **An empty state is a themed line plus a plain next step.** "The Chronicle
   is blank." then "Seal a trial and it is written here."
5. **Always plain wins.** If a word is on the Always plain list, it stays plain
   wherever it appears, including inside themed sentences.
6. **User-visible text only.** Code identifiers, Room columns, cloud wire
   names, nav routes and ids keep their names (see Protected names).

## Navigation

| Tab | Holds |
|---|---|
| **Today** | Today's Trial, your oath, ascension, the way to the Veil |
| **Rites** | Your rites and cycle, the Forge, the Chronicle, muscle coverage |
| **Ledger** | Frame, Training and Daily readings |
| **Codex** | Deeds, Paths, Journal |
| **Allies** | Tidings, the Reckoning, your Circle, Missives |

Settings keeps the name **Settings**.

## You

| Term | Means | Use | Never |
|---|---|---|---|
| **Ironbound** | The user; one or many | "WELCOME, IRONBOUND", default name `Ironbound`, "Blocked Ironbound" | lifter, player, hunter, athlete, vessel, user |
| **True name** | The public display name | "Take your true name", "True name (2–24)", "That true name is taken" | lifter name, handle, username |
| **Folio** | An Ironbound's public page | "Open folio", "Manage folio", "This folio is private" | profile page, lifter page, record sealed |
| **Frame** | The body being measured | "YOUR FRAME", "Frame rating" | body, vessel |
| **Ascension** | The tier ladder that follows level: Acolyte · Adept · Warden · Magister · Archon · Exarch · Sovereign | "ASCENSION · Adept", on reaching one: "ASCENDED · from Acolyte" | class, army class, promotion, Recruit…Grand Marshal |
| **Strength Rank** | Untrained, Novice, Intermediate, Advanced, Elite | its own labelled value, never joined to the ascension | rank alone |

## Training

| Term | Means | Use | Never |
|---|---|---|---|
| **Rite** | A saved, repeatable workout ("Heavy Pull") | "New Rite", "Edit Rite", "Rite name", "Cycle day" | workout, preset, training day, template, forge new workout |
| **Cycle** | The weekly plan: which rite falls on which day | "YOUR CYCLE", "Replace my cycle", "Share cycle", "Cycle code", "starter cycle" | routine, plan, program, split, schedule |
| **Trial** | A rite being performed, or once performed | "Begin Trial", "TRIAL IN PROGRESS", "Abandon Trial", "Name this trial" | workout, session, quest, hunt |
| **Today's Trial** | The rite the cycle puts on today | "TODAY'S TRIAL · Heavy Pull", "Today's Trial bonus" | today's quest, today's workout |
| **Open Trial** | A trial not begun from a rite | "Begin an Open Trial"; unnamed ones are called "Open Trial" | quick workout, blank workout, freeform |
| **Seal** | Finishing a trial | button "Seal the Trial", header "SEALED", "Tick a set to seal the trial", "Seal anyway?" | finish, claim victory, victory, conquered, quest complete |
| **Chronicle** | The history of sealed trials | "FULL CHRONICLE", "Trial of 12 Sep", "erased from the Chronicle", imported ones: "Imported trial" | log, workout log, record, history, lifetime record, activity log |
| **Peak** | A personal best on an exercise | "NEW PEAK", "first peak", "PEAKS PER SET" | PR, new record, personal record |
| **Oath** | Consecutive days kept | "Oath · 12 days kept", "Your oath holds", "Keep a 7-day oath" | streak |
| **Respite** | A day the cycle leaves free | "RESPITE", "Your oath holds through respite" | rest day, rest up (rest *between sets* stays plain) |
| **The Summons** | The daily reminder notification and its setting | "THE SUMMONS · Heavy Pull", settings header THE SUMMONS | daily reminder, quest open, nudge |
| **The Binding** | First-run setup | step titles: Who you are · How you train · Your cycle | onboarding, setup |
| **Armoury** | The equipment an Ironbound owns | "YOUR ARMOURY", settings header ARMOURY, picker filter "MY ARMOURY" | gear, my gear, equipment |
| **The Forge** | The builder that makes rites and cycles | "Forge a Cycle", "Forge a Rite", "What my week is missing" | program builder, generate a routine |
| **Temper** | The Forge improving an existing rite | "Temper this rite", "Before / After" | improve |
| **Patterns** | Hand-written cycle templates | "FROM A PATTERN", "Start from a pattern" | templates |
| **Note** | Free text on a trial or rite | "PRIVATE NOTE", "NOTE · shared with allies", "PUBLIC NOTE" | field note, sealed note, mantra |

## Codex

| Term | Means | Use | Never |
|---|---|---|---|
| **Codex** | Tab holding Deeds, Paths and Journal | nav tab, "6 deeds · 1 technique" | achievements |
| **Deed** | An achievement with a written bar | "DEED EARNED", "EARNED 12", "earned 3 Sep 2026", dashboard "DEEDS 5/105" | achievement, badge, title unlocked, claimed |
| **Title** | The name a deed grants, worn on the folio | "you may wear *First Mark*", "Wear title", "NO TITLE WORN" | (never a trial's headline) |
| **Wear** | Putting on a title or crest | "Wear", "WORN", "TAP TO WEAR" | equip, equipped |
| **Rarity** | Common · Rare · Fabled · Masterwork, for deeds and inscriptions | as listed | epic |
| **Paths** | The skill tree | "PATHS", "Choose a path" | skill tree |
| **Path** | One branch: pull, push, handstand… | "the pull path" | line, branch |
| **Technique** | One node on a path, tier I–V | "Master 5 techniques", "Needs Pull-up first" | skill, node |
| **Claim** | Declaring a technique mastered, and only that | "Claim mastery", "CLAIM STANDARD", "Claimed by mistake?" | (not for names, deeds, trials or spots) |
| **Journal** | Attempts logged against techniques | "LOG AN ATTEMPT", "JOURNAL" | practice record |

## Ledger

| Term | Means | Use | Never |
|---|---|---|---|
| **Ledger** | The narrator, and the stats tab as the Ledger's own page on you. This double use is deliberate and the only one allowed. | nav tab, "The Ledger rates your frame" | stats, stats log |
| **Reading** | One logged measurement | "LOG A READING", "Two readings draw the line", "Your readings never leave this device" | measurement, entry, weigh-in |

Support's public cost page is **"View the costs"**, never "the Ledger".

## Allies

| Term | Means | Use | Never |
|---|---|---|---|
| **Ally** | A friend | "Add an ally", "Ally request", "Remove ally" | friend, follower, rival |
| **Circle** | A group of up to 8 allies with a shared weekly goal | "YOUR CIRCLE", "Form a circle", "Join a circle", "Circle code", "CIRCLE'S GOAL MET" | warband, band, guild, group, muster |
| **Keeper** | The Ironbound who runs a circle | badge "KEEPER" | owner, leader |
| **Tidings** | What allies have done | "TIDINGS", "Every Ironbound's public trials" | feed, activity |
| **The Reckoning** | A leaderboard; each lift and the Veil have their own | "THE RECKONING", "Choose a reckoning", "the Pull-up reckoning" | leaderboard, board, rankings |
| **Rung** | One tier on a lift's reckoning: Initiate · Ash I–II · Bone I–II · Silver I–II · Gold I–II · Umbral I–II | "rung 4 of 11", "Counts toward the Pull-up reckoning" | tier, level, Iron I, Mythic |
| **Standing** | Your place in a reckoning | "YOUR STANDING", "#4" | rank, position |
| **Tribute** | A reaction: Honour, Iron or Flame | "3 tributes", "WHO PAID TRIBUTE", "Your tribute: Iron" | like, cheer, reaction, salute |
| **Remark** | A comment on a trial | "Remarks", "Add a remark", "Delete this remark?" | comment |
| **Missives** | The inbox | "MISSIVES", "3 new missives", "No missives" | inbox |

## The Veil

| Term | Means | Use | Never |
|---|---|---|---|
| **The Veil** | The idle screen, opened from Today | "Open the Veil", "THE VEIL" | garrison, idle vault |
| **Echo** | One unit beyond the veil; trials leave them | "+40 ECHOES", "The echoes are gathering…" | figure, shadow, soldier |
| **Essence** | What echoes gather while you are away | "ESSENCE", "ESS" in tight columns | |
| **Inscribe** | Spend an inscription to draw a reward | "INSCRIBE", "3 INSCRIPTIONS WAITING", each level-up earns one | roll, pull, summon |
| **Relic** | A permanent multiplier, kept in **the Vault** | "RELIC VAULT", "Greater Crown of the Abyss" | |
| **Crest** | A frame worn on the folio | "CREST INSCRIBED", "NOT YET INSCRIBED" | crest frame unlocked, not yet drawn |

## Always plain

These stay plain under the theme. Each needs to be instantly clear, and several
are compared outside the app.

- **Measurements and science:** exercise names, set, rep, kg, rest between
  sets, time, load, modifiers, BMI, FFMI, body fat, steps, sleep, calories,
  RPE, deload, hypertrophy, 1-rep max, muscle names, muscle coverage. Explain
  them; never rename them.
- **Game stats:** XP, Level (LV), STR, LEVEL UP, strength score.
- **Safety, privacy and anything irreversible:** Block, Mute, Report, Delete,
  Remove, Sign in, Sign out, Back up, Restore, Import, Export, Archive, Sync,
  Delete account, Cancel, Public, Allies only, Private, "stays on this
  device". The Ledger may add a flavour line beside them, never replace them.
- **Settings rows** and **brand names** (Health Connect, Google, Supabase,
  Liberapay, GitHub). Only settings section headers take themed names, and
  only where this glossary names one (ARMOURY, THE SUMMONS).
- **Citations** (Pelland 2026, Schoenfeld…) and rarity-free catalogue text.

## Narrator voice

Loading, empty and error lines. The pattern is a themed line, then a plain
fact or action.

| Situation | Pattern | Example |
|---|---|---|
| Loading | "Reading …", "Turning to …", "Opening …" | "Opening the trial…", "Reading your missives…" |
| Cloud error | "The Ledger …" + what to do | "The Ledger stumbled — try again in a moment" |
| Stale data | "The ink has faded —" + what is shown | "The ink has faded — these standings are from your last sync" |
| Nothing yet | "… is blank / silent / unwritten" + next step | "No tidings yet. Set your trials public on ALLIES and be the first." |
| Private | plain | "This folio is private." |

Retired flavour, because it is military or modern: stand fast, frontline,
scouting, recruit, mustering, marching, orders, raise the stakes, flickered,
stuttered, satisfied, rises tomorrow, summoning (Summons is the reminder).

## Deed names

Deeds keep their ids and bars. Their display names follow the world: no
soldiers, battles, legions, marches, generals or sport-arena words. These are
renamed in the rename pass: Hundred Battles, Eternal Grinder, Royal Apex,
Keeper / Warden / Master of the Garrison, Standing Order, Endless Legion,
Storm of Steel, Steel Tempest, Doorbreaker, Long Marcher, Million March, Great
March, Marching Orders, Hundred-Kilometre March, Wandering Soldier, Field
Commander, Eternal Vanguard, Red Zone Lifter, Ten-K Lifter, Fifty-K Traveler,
Champion of Games, First Arena, Arena Regular; and, because they reuse a
glossary term for something else, Ten Thousand Echoes, Three Paths,
Thirty-Five Paths and Path Carver.

Deed descriptions, categories and progress labels use the glossary too:
"Seal your first trial.", "Keep a 7-day oath.", "Master 5 techniques.", the
category **Trials** (not Workouts), "day oath" (not day streak), "reps in one
trial" (not in one session).

## Protected names

These are identifiers, not copy, and do not change with the glossary: nav
routes (`hunter/{userId}`, `presets`, `titles`, `idle`, `workout_log`,
`session/…`), cloud RPCs and columns (`find_hunter`, `*_warband`, `shadow_*`,
`session_likes`, `friends`, `sessions`), reaction wire values (`salute`,
`iron`, `flame`), Room columns (`shadows`, `essence`, `relics`), title ids
(`awakened`, `shadow_ascendant`, …), enum names (`ArmyClass`, `RewardRarity`),
the routine code prefix `IVR1:`, and relic names, which derive from the roll
and seed the art (so the relic house "the Ledger" stays).

The warband wire names are the exception to revisit: the feature is new, and
renaming `*_warband` to `*_circle` belongs with the warband audit, not this
pass.

## Decisions

- **Stored default name** (approved 2026-09-30). Profiles were seeded with the
  name `Lifter`. A Room migration renames exactly `Lifter` to `Ironbound`;
  someone who typed "Lifter" deliberately is renamed too.
- **Cloud handles** (approved 2026-09-30). The server seeds new accounts
  `Ironbound` + short id; `Lifter` and `Hunter` handles still count as
  unclaimed. Needs the hosted project updated.
- **Art** (checked 2026-09-30). No art needs changing: the rank emblems named
  in `docs/ART_ATTRIBUTION.md` no longer ship, and the empty-state set fits
  the world as drawn (`art_empty_muster` is a rune circle, used by the Veil).

## Scope

The glossary covers every place a user reads words:

- string literals under `app/src/main/kotlin`, including domain text (deeds,
  techniques in `Skills.kt`, the Forge's reasons, progression messages),
  notification channels and the share card;
- server text a user can see: the seeded handle and the hidden-member name
  (`'Lifter' || …` in `supabase/migrations/0001_baseline.sql`), and exception
  messages that reach the screen;
- the store listing (`fastlane/metadata/android/*/full_description.txt`,
  `short_description.txt`) and the README's feature descriptions.

Past changelogs are history and stay as written. Outside the app (store
listing, README) a reader has not learned the words yet, so a themed term may
be glossed once in plain words on first use: "the Reckoning, a leaderboard for
each lift".

## Enforcement

A unit test scans the string literals under `app/src/main` for retired words
taken from the Never columns and the retired flavour list, and fails on a
match. So it does not cry wolf, it:

- strips `${…}` templates before matching, so `${band.first}` or
  `${session.label}` never count;
- matches whole words, case-insensitively;
- keeps an explicit allowlist of literal + reason for the legitimate uses:
  citations ("Hunter 2014"), the legacy handle regex (`Hunter|Lifter`), the
  Room migration that renames `Lifter`, and plain brand phrases such as Health
  Connect's "Activity History".

Add a word to the list when you retire it; add an allowlist entry only with a
reason.
