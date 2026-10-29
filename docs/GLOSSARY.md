# Ironvellum glossary

The single source of truth for every word the app shows a user. Every concept
has two names: a **plain word** (the default, what a person expects) and a
**themed name** (the Ironvellum world, switched on in Settings > Appearance >
"Themed names", off by default, applied at once). If a screen, notification,
share card, deed or error message names one of these concepts, it asks the
vocabulary layer (`data/Words.kt`) for it, so the switch changes every place
together. A new concept gets an entry here and in `Words` before it gets a
string in the code.

The Plain column below is the default; the Term column is the themed name. A
concept marked "same" keeps its name in both modes.

**The reward layer is themed in both modes** (owner decision, 2026-10-15). The
switch renames the training and account words a newcomer has to understand
(workout, routine, history, PR, streak, friends, ...); it does not touch what
the app gives back. Marked **both modes** below: achievements (**Deed**, and
every deed's own name such as "Threefold Iron"), the rank titles of the
ascension ladder (Acolyte ... Sovereign), crests and other awards, and the
Veil with everything in it. These are rewards, not vocabulary: a plain name
would erase the thing that makes them rewarding, and a new lifter meets each
one with a one-line first-time explanation instead. In code a concept in this
layer is declared `bothModes = true` in `Words`.

## The world

The themed names are dark fantasy in ink on vellum, matching the house art
(sumi-e brush work, bone ink on black). No armies, no ranks of soldiers, no
modern game-UI words. This is the world a lifter opts into; the plain words are
what the app says to everyone else.

An install that already existed when the setting arrived keeps the themed names
(its database, or the Welcome screen having been seen, is the evidence); a
fresh install starts plain. The first run decides once and stores the answer.

The app is **the Ledger**, an iron-bound book that records what the
**Ironbound** do. Each week an Ironbound keeps a **Cycle** of **Rites**.
Performing a rite is a **Trial**, and every trial they **seal** is written into
their **Chronicle**. Each trial leaves an **Echo** beyond **the Veil**, where
echoes gather **essence**. **Deeds** earn **Titles** to wear; **Paths** hold
the techniques to master. Allies gather in **Circles** and are measured in
**the Reckoning**.

## Rules

1. **One name per concept in each mode.** Never a synonym, and never the
   plain word in a themed sentence or the themed word in a plain one. A
   concept word is never typed into a string literal: it comes from `Words`.
2. **One meaning per word.** A themed word is never reused for a second
   concept. "Seal" means finishing a trial, so private things are "private",
   not "sealed". "Title" means a deed's title, so a trial's headline is its
   "name".
3. **The Ledger is the narrator in themed mode only.** Loading, empty and error
   lines speak in its voice there. It addresses the user as "you", or speaks of
   "the Ironbound". Its register is ink, pages, shadow, ash, oaths and the
   dark; never soldiers, orders, fronts or marching. Plain mode says the same
   thing without it ("Something went wrong", "Ready when you are"); those pairs
   live in `data/WordLines.kt` and `ui/dashboard/TodayNarration.kt`.
4. **An empty state is a line plus a plain next step.** Themed: "The Chronicle
   is blank." then "Seal a trial and it is written here." Plain: "Your history
   is blank." then "Finish a workout and it is written here."
5. **Always plain wins.** If a word is on the Always plain list, it stays plain
   in both modes, including inside themed sentences.
6. **User-visible text only.** Code identifiers, Room columns, cloud wire
   names, nav routes and ids keep their names (see Protected names).
7. **User-entered names are never translated.** A routine the lifter called
   "Full Body A", a trial title, a display name: data, shown as typed. The
   stored default profile name `Ironbound` is a sentinel and reads as "Lifter"
   in plain mode.
8. **Plurals, case and verbs come with the word.** `Words` has the plural
   ("3 routines"), the capital ("Routine"), the capitals ("ROUTINES"), the
   article ("a routine", "an ally"), and the verb forms (finish, finished,
   finishing). A sentence that cannot be reached by swapping a word is a line
   in `WordLines.kt`.
9. **The Veil's own words stay.** The Veil, essence, echoes, relics,
   inscriptions and crests keep their names in both modes; the Veil explains
   each in one line the first time it opens, in either mode (and from its "?").
   Deeds and rank titles likewise get a one-line explanation the first time
   one appears.

## Navigation

| Tab | Holds |
|---|---|
| **Today** | Today's Trial, your oath, ascension, the way to the Veil |
| **Ledger** | Frame, Training and Daily readings |
| **Train** | The centre tab, raised: today's rite to begin, your rites and cycle, the Forge, the Chronicle, muscle coverage |
| **Codex** | Deeds, Paths, Journal |
| **Allies** | Tidings, the Reckoning, your Circle, Missives |

Settings keeps the name **Settings**. In plain mode the tabs read Today, Stats,
Train, Progress, Friends.

## You

| Term | Plain (default) | Means | Use |
|---|---|---|---|
| **Ironbound** | lifter ("Lifter"; "you" in the app's own sentences) | The user; one or many | "WELCOME, IRONBOUND", default name `Ironbound`, "Blocked Ironbound" |
| **True name** | display name | The public display name | "Take your true name", "True name (2–24)", "That true name is taken" |
| **Folio** | profile | An Ironbound's public page | "Open folio", "Manage folio", "This folio is private" |
| **Frame** | body | The body being measured | "YOUR FRAME", "Frame rating" |
| **Ascension** (both modes) | same: the sigil reads "Acolyte 4" / "Adept 13" whatever the switch says | The tier ladder that follows level: Acolyte · Adept · Warden · Magister · Archon · Exarch · Sovereign. A band of levels, never a second kind of progress: it names the level, never a stat beside it | on the level sigil in place of "LV": "ADEPT 13"; under the XP rail, the next one: "WARDEN AT 15"; on reaching one, on the level-up page itself: "YOU ASCEND", the old tier struck through; reward line "LEVEL UP 4 → 5 · ADEPT" |
| **Strength Rank** | same | Current strength by pattern, Pull, Push and Legs: each takes its best mark in the last 90 days, a loaded lift's estimated 1-rep max over bodyweight or a cleared technique at its tier, and the rank is their average, rounded down: Untrained, Novice, Intermediate, Advanced, Elite; **Unranked** before any | its own labelled value, never joined to the ascension; "STRENGTH RANK · Unranked" |

## Training

| Term | Plain (default) | Means | Use |
|---|---|---|---|
| **Rite** | routine | A saved, repeatable workout ("Heavy Pull") | "New Rite", "Edit Rite", "Rite name", "Cycle day" |
| **Cycle** | weekly plan | The weekly plan: which rite falls on which day | "YOUR CYCLE", "Replace my cycle", "Share cycle", "Cycle code", "starter cycle" |
| **Trial** | workout | A rite being performed, or once performed | "Begin Trial", "TRIAL IN PROGRESS", "Abandon Trial", "Name this trial" |
| **Today's Trial** | today's workout | The rite the cycle puts on today | "TODAY'S TRIAL · Heavy Pull", "Today's Trial bonus" |
| **Open Trial** | empty workout | A trial not begun from a rite | "Begin an Open Trial"; unnamed ones are called "Open Trial" |
| **Seal** | finish (a workout) | Finishing a trial | button "Seal the Trial", header "SEALED", "Tick a set to seal the trial", "Seal anyway?" |
| **Chronicle** | history | The history of sealed trials | "FULL CHRONICLE", "Trial of 12 Sep", "erased from the Chronicle", imported ones: "Imported trial" |
| **Yours** | custom | An exercise the lifter made | tag "YOURS", picker section "Yours", row "New exercise" |
| **Peak** | PR | A set that beat everything done before it on an exercise: heavier for the same or more reps (3 or more), or more reps at the same or a greater load | "NEW PEAK", "first peak", "PEAKS PER SET" |
| **Oath** | streak | Consecutive days kept | "Oath · 12 days kept", "Your oath holds", "Keep a 7-day oath" |
| **Respite** | rest day | A day the cycle leaves free | "RESPITE", "Your oath holds through respite" |
| **The Summons** | reminder | The daily reminder notification and its setting | "THE SUMMONS · Heavy Pull", settings header THE SUMMONS |
| **The Binding** | setup | First-run setup | step titles: Who you are · How you train · Your cycle |
| **Armoury** | equipment | The equipment an Ironbound owns | "YOUR ARMOURY", settings header ARMOURY, picker filter "MY ARMOURY" |
| **Plates** | same | The plate calculator: a chip on a barbell lift in the live trial that opens a sheet building the load from the bar and plates owned; and the Armoury's "Bar and plates" block | chip "Plates", sheet "Plates", "Closest below", "falls between plates", "Use 102.5 kg", "Heaviest you can load" |
| **Units** | same | kg or lb, a Training mode setting. Storage is kg everywhere; it changes only what is shown and typed | "UNITS · kg lb", "225 lb × 5", "Load (lb)" |
| **The Forge** | plan builder (verb: build) | The builder that makes rites and cycles | "Forge a Cycle", "Forge a Rite", "What my week is missing" |
| **Temper** | improve | The Forge improving an existing rite | "Temper this rite", "Before / After" |
| **Optimise** | same | The coverage page's suggestions that change rites you already have: a movement for a gap, an eased heavy muscle, two movements doing one job swapped, days spread for recovery. Each change is kept or skipped, the kept ones apply together, and Undo puts every touched rite back | "Optimise rites", "Apply 2 changes", "Undo, put Upper A back", "Best match" |
| **Patterns** | templates | Hand-written cycle templates | "FROM A PATTERN", "Start from a pattern" |
| **Set type** | same | How one set was done: Working, Warm-up, Drop, Failure or Rest-pause. A tag on the set row, changed by tapping it. Only a warm-up is left out of XP, volume and peaks | "Set types", "Mark as warm-up", "Count as a working set", tags W · D · F · R |
| **Superset** | same | Adjacent exercises done as one group, a pair or a triple: a set of each in turn, rest only after the last of the round. Linked from an exercise's menu in the trial or from a rite's exercise card | "Superset with Barbell row", "Superset 1 · Barbell row next", "Unlink", "NEXT · NO REST YET" |
| **Effort** | same | How hard a set was, optional: asked after a set is logged when Settings turns on "Log effort". Read as RPE (10 is your limit) or RIR (the reps you had left); one whole number RPE 5 to 10 is stored, RIR is 10 minus it | "Log effort", "How hard was set 2?", "RPE 8: about two reps left", "2 RIR", "Include effort" |
| **Warm up** | same | The action on a loaded exercise's menu that adds warm-up sets before its first working set: the bar for 10 (barbell only), then about 40% for 5, 60% for 3 and 80% for 2 of the working load, rounded to the movement's load step | "Warm up", "Warm-up added · Undo" |
| **Rest timer** | same | The countdown between sets, to the movement's ready time (the window's max is only a soft ceiling). It can be off for every movement (Settings, DURING A TRIAL) or for one movement (its rite, or its menu in the trial). Rest *between sets* stays plain | "Rest timer", "No rest timer", "Skip rest", "+15", "Rest is over" |
| **Rest alert** | same | How a rest's end is announced: Vibrate, Sound + vibrate or Silent. The same choice cues a timed set's get-ready and its target | "WHEN A REST ENDS", "Vibrate", "Sound + vibrate", "Silent" |
| **Pocket lock** | same | A dim cover over the live trial that shows only the rest countdown or the set in hand, and ignores touches until a long hold. The trial keeps running underneath | "Lock the screen", "Hold to unlock", "Unlock the trial" (the TalkBack action) |
| **Timed set** | same | A hold or duration set done against a clock on its row: Start, a 5-second get-ready, then a count up toward the target, which cues once and keeps counting; Stop opens an adjust step (-1s, +1s, Log, Discard). It can also be logged with no clock, at the figure in its row. A distance run has a stopwatch (Start stopwatch, Stop) that fills its time | "Start", "Stop", "GET READY", "TARGET", "TARGET REACHED", "Log", "Discard", "-1s", "+1s", "Start stopwatch", "ELAPSED" |
| **Note** | same | Free text on a trial or rite | "PRIVATE NOTE", "NOTE · shared with allies", "PUBLIC NOTE" |

## Codex

| Term | Plain (default) | Means | Use |
|---|---|---|---|
| **Codex** | progress | Tab holding Deeds, Paths and Journal | nav tab, "6 deeds · 1 technique" |
| **Deed** (both modes) | same (plain: no plain word) | An achievement with a written bar | "DEED EARNED", "EARNED 12", "earned 3 Sep 2026", dashboard "DEEDS 5/121" |
| **Title** | same | The name a deed grants, worn on the folio | "you may wear *First Mark*", "Wear title", "NO TITLE WORN" |
| **Wear** | same | Putting on a title or crest | "Wear", "WORN", "TAP TO WEAR" |
| **Rarity** | same | Common · Rare · Fabled · Masterwork, for deeds and inscriptions | as listed |
| **Paths** | skills | The skill tree | "PATHS", "Choose a path" |
| **Path** | skill tree | One branch: pull, push, handstand… | "the pull path" |
| **Technique** | skill | One node on a path, tier I–V | "Master 5 techniques", "Needs Pull-up first" |
| **Claim** | mark as mastered | Declaring a technique mastered, and only that | "Claim mastery", "CLAIM STANDARD", "Claimed by mistake?" |
| **Journal** | practice log | Attempts logged against techniques | "LOG AN ATTEMPT", "JOURNAL" |

## Ledger

| Term | Plain (default) | Means | Use |
|---|---|---|---|
| **Ledger** | stats (the tab); the narrator becomes "the app" | The narrator, and the stats tab as the Ledger's own page on you. This double use is deliberate and the only one allowed. | nav tab, "The Ledger rates your frame" |
| **Reading** | measurement | One logged measurement | "LOG A READING", "Two readings draw the line", "Your readings never leave this device" |

Support's public cost page is **"View the costs"**, never "the Ledger".

## Allies

| Term | Plain (default) | Means | Use |
|---|---|---|---|
| **Ally** | friend | A friend | "Add an ally", "Ally request", "Remove ally" |
| **Circle** | group | A group of 2-8 allies sharing a weekly goal of days trained | "YOUR CIRCLE", "Form a circle", "Join a circle", "Circle code", "CIRCLE'S GOAL MET" |
| **Keeper** | owner | The Ironbound who runs a circle | badge "KEEPER" |
| **Tidings** | feed | What allies have done | "TIDINGS", "Every Ironbound's public trials" |
| **The Reckoning** | leaderboard | A leaderboard; each lift and the Veil have their own | "THE RECKONING", "Choose a reckoning", "the Pull-up reckoning" |
| **Rung** | tier | One tier on a lift's reckoning: Initiate · Ash I–II · Bone I–II · Silver I–II · Gold I–II · Umbral I–II | "rung 4 of 11", "Counts toward the Pull-up reckoning" |
| **Standing** | position (never "rank": Strength Rank is its own label on the same screens) | Your place in a reckoning | "YOUR STANDING", "#4" |
| **Tribute** | kudos | A reaction: Honour, Iron or Flame | "3 tributes", "WHO PAID TRIBUTE", "Your tribute: Iron" |
| **Remark** | comment | A comment on a trial | "Remarks", "Add a remark", "Delete this remark?" |
| **Missives** | inbox | The inbox | "MISSIVES", "3 new missives", "No missives" |

## The Veil

| Term | Plain (default) | Means | Use |
|---|---|---|---|
| **The Veil** (both modes) | same (screen title "The Veil") | The idle screen, opened from Today | "Open the Veil", "THE VEIL" |
| **Echo** | same | One unit beyond the veil; trials leave them | "+40 ECHOES", "The echoes are gathering…" |
| **Essence** | same | What echoes gather while you are away | "ESSENCE", "ESS" in tight columns |
| **Inscribe** | same | Spend an inscription to draw a reward | "INSCRIBE", "3 INSCRIPTIONS WAITING", each level-up earns one |
| **Relic** | same | A permanent multiplier, kept in **the Vault** | "RELIC VAULT", "Greater Crown of the Abyss" |
| **Crest** | same | A medallion worn on the folio; its perk works only in the Veil, and allies see it on your avatar. 28 of them: ladder (levels), deeds, houses, and six from the Veil | "Wear", "Iron crest", "CREST INSCRIBED", "NOT YET INSCRIBED" |

## Always plain

These stay plain in both modes. Each needs to be instantly clear, and several
are compared outside the app.

- **Measurements and science:** exercise names, set, rep, kg, lb, rest between
  sets, set types (warm-up, drop, failure, rest-pause), time, load, modifiers, BMI, FFMI, body fat, steps, sleep, calories,
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

Deed descriptions, categories and progress labels use the vocabulary too, so
they follow the switch: themed "Seal your first trial.", "Keep a 7-day oath.",
"Master 5 techniques.", the category **Trials**, "reps in one trial"; plain
"Finish your first workout.", "Keep a 7-day streak.", "Master 5 skills.", the
category **Workouts**, "reps in one workout". Deed, crest and relic names are
names, not concept words, so they stay as they are in both modes, except where
one contains a concept word ("Three-Day Oath" reads "Three-Day Streak").

## Protected names

These are identifiers, not copy, and do not change with the glossary: nav
routes (`hunter/{userId}`, `presets`, `titles`, `idle`, `workout_log`,
`session/…`), cloud RPCs and columns (`find_hunter`, `*_circle`, `shadow_*`,
`session_likes`, `friends`, `sessions`), reaction wire values (`salute`,
`iron`, `flame`), Room columns (`shadows`, `essence`, `relics`), title ids
(`awakened`, `shadow_ascendant`, …), enum names (`ArmyClass`, `RewardRarity`),
the routine code prefix `IVR1:`, and relic names, which derive from the roll
and seed the art (so the relic house "the Ledger" stays).

The circle wire names follow the glossary (`circles`, `circle_members`,
`my_circle`, `create_circle`, ...). The one exception is the inbox kinds
`band_join` and `band_goal`: builds already installed parse those, so the
server keeps sending them and the client accepts the `circle_*` spellings too.

## Decisions

- **Stored default name** (approved 2026-09-30). Profiles were seeded with the
  name `Lifter`. A Room migration renames exactly `Lifter` to `Ironbound`;
  someone who typed "Lifter" deliberately is renamed too.
- **Cloud handles** (approved 2026-09-30). The server seeds new accounts
  `Ironbound` + short id; `Lifter` and `Hunter` handles still count as
  unclaimed. Live on the hosted project since the 2026-10-02 release patch.
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
  `short_description.txt`), the README and docs/FEATURES.md.

Past changelogs are history and stay as written. Outside the app (store
listing, README) a reader has not learned the words yet, so a themed term may
be glossed once in plain words on first use: "the Reckoning, a leaderboard for
each lift".

## Enforcement

A unit test (`GlossaryTest`) scans the string literals under `app/src/main`
for both vocabularies: the plain words and the themed names. A concept word in
a literal is a bug in one mode, so the only places allowed to spell them are
the vocabulary files (`Words.kt`, `WordLines.kt`, `TodayNarration.kt`) and a
short list of files that hold other data (exercise instructions, citations,
licence texts). So it does not cry wolf, it:

- strips `${…}` templates before matching, so `${Words.rite.s}` never counts;
- matches whole words, case-insensitively, and counts a possessive ("circle's");
- skips key-like literals (routes, tags, ids);
- keeps an explicit allowlist of literal + reason for the legitimate uses:
  citations, the sentinel `Ironbound`, the ascension names (themed only), crest
  and relic names, the wire text `Only the Keeper`, and the English idioms.

Ambiguous English (standing, frame, pattern, yours) is left to review rather
than banned. `WordsTest` covers both modes, plurals, articles, the server
sentences and the first-run default; `NamesToggleTest` flips the switch on a
device and checks the nav bar relabels with no restart.

Add a word to the lists when a new concept arrives; add an allowlist entry only
with a reason. Server messages are themed on the wire and are shown through
`Words.fromServer`, which turns them plain in plain mode.
