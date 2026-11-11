# Changelog

What changed in each release, grouped by what you will notice. The short version ships with the app
store listing; this is the whole list.

## 1.11

### Rest times
- Set compound and isolation rest defaults or override an exercise in its workout settings; turn a custom value off to use the underlying default again. Top-set rest remains separate.
- Use custom rest in rule-based workout estimates; estimates based on your measured pace still reflect that history.
- Keep an exercise's rest override when renaming a custom exercise; remove it when the unused exercise is deleted.
- Keep compound rest defaults out of timer-free custom activity estimates, and use each block's separate top-set rest in the live time remaining.

### Weight units per exercise
- Choose App default, kg or lb from an exercise's About page or its unit dropdown while logging a workout.
- Keep each exercise's choice across variations, workouts, app restarts and local or cloud backup restores. Explicit kg or lb stays pinned when the app's default changes.
- Use the chosen unit for load entry, plate steps, plans, history, records, workout sharing and the web viewer. Recorded kilograms, scores and XP stay unchanged; workout totals still use the app default.
- Show the app-default equivalent beside exercise weights whenever their chosen unit differs, such as 100 lb (45.36 kg), without adding conversions to numeric inputs or duplicating matching units.
- Reject out-of-range worn-load entries instead of displaying a value that will not be saved.
- Choose per-exercise units for weighted timed activities such as Weighted Skipping.
- Preserve the chosen unit in PR chips when a custom exercise name contains spaced dots.

### Exercise details
- Open a custom exercise's About page from the picker without a crash, then use Edit to change or delete it. Exercises already in use remain protected to preserve their history.

### The Veil
- Start the Veil's accrual clock at app startup, so the home bar and essence can update without first opening the Veil.
- Repair missing clocks on older installs and restored backups without collecting rewards, resetting an existing away interval or changing banked essence.

### Notifications
- Check social activity when signing in, returning to the app or allowing notifications, rather than waiting for the next background poll.
- Keep pending social alerts when permission is denied or posting fails; retry temporary connection failures without repeating already-delivered activity.
- Ignore replies from a previous account or backend, and keep existing inbox history silent when first establishing a notification baseline.
- Catch up a due weekly recap after returning or granting notification permission, within its existing 24-hour window, without duplicate alerts.
- Retry failed evening reminders and weekly recaps twice with exponential backoff, while preserving cancellation and the next scheduled occurrence.
- Keep valid social events available when an inbox row contains an invalid or out-of-range timestamp.
- Keep sign-in, restored profiles and privacy changes tied to the current account; cancellation and overlapping requests no longer leave account controls stuck busy or revive a signed-out session.
- Wait for authentication startup before importing saved sessions or signing in, preventing a startup race from making restored accounts disappear.
- Keep inbox unread counts accurate when refreshes and read acknowledgements overlap; isolate cached social data by account and backend, and recover cancelled inbox loads with a working retry.
- Resume background social requests without waiting for the app to reopen; renew expired credentials before querying and preserve retryable session-storage and refresh failures.
- Remember completed authentication setup through startup storage failures, so a background retry does not wait for the app to reopen.
- Retain valid sign-in sessions when a profile read temporarily fails, allowing the profile to recover without entering credentials again.
- Run a fresh notification check when signing back into the same account, even within the polling throttle window.

## 1.10

### Warmups, stretches and cooldowns
- Save reusable warmup and cooldown routines, including stretches, or choose suggestions based on the workout's working exercises.
- Follow gallery movements with rep targets, timers, rounds and both sides or directions. Timed units advance automatically with sound/vibration cues following your alert setting; changing exercise gives five seconds to get ready. Rep targets still use manual completion.
- Keep the screen awake while warmup, cooldown or hold timers run in view, using the existing Keep screen on preference. Paused preparation and read-only previews release their screen-awake ownership.
- Resume preparation after leaving, rotation or restarting the app. Recover malformed saved timers and count unfinished units as skipped when finishing early.
- Legacy warmup exercises share one guided warmup. Preserve the running workout's saved warmup when replacing routines, including repeated exercises with different targets.
- Start cooldown from the final workout summary without changing working sets, XP or rewards.
- Show preparation start failures in place, with retry and no premature navigation.

### Workouts and imports
- Import Strong, Hevy or FitNotes history and optionally save the latest session of each selected workout as a reusable workout. Re-importing does not duplicate history, saved workouts or XP.
- Open an exported CSV from Android's share menu, or save a past workout as a new editable workout, without changing its history or rewards.
- Preserve recorded climbing attempts and top/back-off rep and load summaries in copied workouts, including unweighted bodyweight back-offs and their editable fixed-zero setting.
- Recover the saved workout summary after a restart without replaying awards; Share, Done, photos and routine-update offers remain available.

### Assistance and load fixes
- Recognise reduced assistance as progress and a PR on assisted pull-ups, chin-ups and dips. Use more support for easier back-offs and drops.
- Do not count machine counterweights as weight lifted. Bodyweight-aware records use bodyweight minus assistance; weighted bodyweight exercises use bodyweight plus added load.
- Keep imported machine-assisted exercises separate from plain bodyweight exercises, preserving their recorded support.
- Keep unknown support distinct from explicit zero when typing, stepping, reviewing sets, inspecting records and sharing workouts. Share the least assistance and the rating from that same set.
- Repair existing assisted strength scores, including computed zeroes, without changing training logs or banked XP. Preserve the entire existing score when assistance is unknown, and retry pending repairs when a weigh-in is added, corrected, synced or restored.
- Avoid added-load formulas for assisted starter weights and warmup ramps, and avoid extra five-pound assistance steps caused by gram rounding.
- Respect per-dumbbell equipment limits for paired lifts in the workout editor.
- Exclude hidden activity-only reps and invalid negative external weights from lifted-weight totals without deleting imported entries.
- Label assistance explicitly in Android and web records. Use workout-start bodyweight for web best-set calculations without shifting chart dates.
- Complete assistance labels and instructions in all seven translated UI languages.

### Exercise details
- Show larger, full-width artwork, with equipment and last-workout facts underneath instead of squeezing the drawing into a thumbnail.
- Require a dumbbell pair for decline dumbbell presses and two-dumbbell skullcrushers; correct skullcrusher and machine-assisted pull-up instructions.
- Create and edit custom exercises with compact summary rows and focused pickers for exercise type, logging format and equipment. Equipment changes can be applied together or cancelled.
- Select main and supporting muscles on a full-width body diagram or by name in a searchable list, with explicit role controls that adapt to narrow screens and large text.

## 1.9

### Photos
- An optional "Add a photo" step after a sealed workout, once a day and skipped in one tap. The
  photo shows on the workout and can be replaced or removed there.
- Body > Progress photos: a front, side and back pose each week on a day you choose, a reminder on
  Today that day, and a compare view with one photo and a draggable divider between two weeks.
- Photos stay on the phone, with no camera or storage permission. They are not synced to the
  cloud, and they ride along in the weekly backup folder.

### Training
- The effort of your last set is asked above slide to seal, so you rate it before you finish.
- Muscle fatigue: a per-muscle estimate shown on a body heatmap, with a Most worked list and the
  cause behind each muscle. A Muscles card on Train opens muscle fatigue and weekly coverage.
- Today can offer Keep, Lighten or Swap for tired exercises, or another routine or rest. Turn it
  off in Settings > Training (Fatigue advice). The exercise picker marks tired muscles.
- Variations are kept apart: each variation has its own last set and progression history. The
  plain version is called Standard everywhere, and a tapped variation opens on its own lift page.
- Sixteen new variation deeds, and sets past 12 reps count as 12 in the estimated 1RM.
- Weight added to the body shows a plus, a harder or easier arrow marks each modifier chip, and
  two-dumbbell lifts show the load per dumbbell.
- Modifiers only appear where they make sense: deficit on handstand push-ups, and elevated and
  incline on inverted rows. Past sets can be converted onto the dedicated exercise.
- The last five workouts are listed under Recent on Train, and a routine's movements open their
  exercise sheet.
- The exercise sheet is redrawn as About, Form, History and Fatigue pages that each fit without
  scrolling. About says why to do each of the 584 movements and when to pick it, with both sides of
  the body; Fatigue shows each muscle's recovery, picked on the figure or the list.
- A catalogue check corrected muscles, gear, one-sided flags and load factors across the library.
  Bear crawl, crab walk, mountain climber, plank jack and flutter kick are now timed.

### Languages
- Spanish, German, French, Portuguese (Brazil), Japanese, Korean and Simplified Chinese, with
  themed names in each. The translations were produced with AI and checked
  against per-language term tables.
- Numbers follow the app's language, with a decimal comma and local thousands separators where
  that language uses them. Weights accept a comma or a point when you type them.
- Deed names and descriptions, the why-do-it text and the exercise guides are still in English.

### Web dashboard
- Sign in at alexmollard.github.io/Ironvellum to read your training on a computer: overview,
  workouts, lifts, trends and routines, with your body figure, deeds and charts with tooltips.
- A friends feed with reactions, comments and reports, an inbox, boards, and your group with the
  keeper actions. Mute, block and report work from a lifter's page.
- Your own training is private to you; logging and editing stay in the app.
- Copy a workout as plain text from its page or its row in the workout list.
- A light and dark theme and the Themed names choice from an Appearance menu, and a plus on Lifts
  records for weight added to the body.
- Whole rows and cards are one click target, and focus follows navigation.

### Look and feel
- Most buttons are now raised neutral buttons, with an icon on Back and the photo
  actions.
- The light theme is redrawn as Vellum, with readable small text and chart ink. Charts use a
  checked colour palette and thinner marks.
- Wide screens centre the app, and expanded screens show paired panes.
- Boards are cards that each open their own screen.

### Your data and Play
- Backups include your portable settings.
- A Privacy policy link in Settings > Support, and the Play data-safety notes cover heart rate
  and background reads.

### Fixes
- Opening the app after it was closed mid-workout no longer crashes on the way back into the
  workout. Shortcuts, widgets and notification taps wait for the app to be ready the same way.
- The elevated pistol squat sits after the pistol squat in the Legs line: it is a deficit
  pistol, deeper and harder. The airborne squat now follows the box pistol squat.
- The exercise sheet lists each board once, at the highest rung the movement counts for.

## 1.8

### Look back
- A weekly recap every Sunday at 19:00: workouts, records, muscles trained and your streak. The
  notification shows no numbers on the lock screen. Open it from Today (Monday to Wednesday) or
  Stats, and share it as an image card.
- A month and year in review: your best week, lifts gained, most-trained muscles, volume and a
  calendar, with a share card. It appears on Today in the first week of a month and is listed in
  Stats > Training > Recaps.
- Every exercise sheet has a History tab: each past session, your best set and the estimated 1RM
  trend, filtered by variation.
- Trend charts draw a dashed recent average over the last two weeks, body weight adds a smoothed
  trend, and each chart labels its high and low on the left edge.

### Outside the app
- Home-screen widgets: "Today" and "Streak & week", in two sizes each, on a tinted glass
  background that follows your theme and accent.
- A live workout shows on the lock screen and status bar as an Android 16 Live Update, with set
  progress and the rest countdown. Older Android gets an improved notification.
- Start today and Resume shortcuts on the app icon, and a Quick Settings tile. Settings > During a
  workout > Lock screen and shortcuts.

### Training
- Workouts start on exactly what your routine says. When you are ready for more, the app suggests
  it, and accepting comes with Undo.
- The same movement listed twice in a routine stays two exercises, each with its own sets and
  modifiers.
- Bodyweight estimated 1RMs count your body weight across the catalogue: push-ups, pull-ups,
  dips, rows, split squats, lunges, step-ups, calf raises and pistols, with or without a vest.
- One celebration at a time after a workout: the level-up no longer covers the XP screen.
- Today's card fits more: short exercises share a line and the routine name stays on one line.

### Health Connect and your data
- Save finished workouts to Health Connect (opt-in).
- Health Connect syncs whenever you come back to the app, not only at launch.
- Resting heart rate is worked out from your heart-rate readings when no resting value is logged.
  Allow heart rate in Settings > Health Connect.
- Weekly automatic backup to a folder you choose, keeping the newest five.

### Look and feel
- A light theme, with a System, Dark or Light choice.
- The system font size is respected everywhere, up to 200%.
- Tabs change with a short, slow swipe, and the collection screen swipes too.
- Tap targets are at least 48dp, and sets with no load read "BW" everywhere.
- Fast on long histories: rescoring XP over 450 workouts went from 4 s to under 0.1 s.
- All screen text lives in string resources, ready for translation.

## 1.7

### Your words, your setup
- Plain gym words are now the default: workout, routine, weekly plan, PR, streak, history, stats,
  friends. The fantasy names are one switch away in Settings > Appearance (Themed names). Updating
  keeps whichever you had.
- The reward layer keeps its names in both modes: deeds, rank titles, crests, relics and the Veil,
  each with a one-line explanation the first time you meet it.
- A new first screen, "Make it yours": pick plain or themed names (with a live preview), kg or lb,
  and your accent colour before setup starts.
- Setup asks how you rate effort (reps left or RPE), no longer preselects your sex, and lets you go
  Back from step 1.
- Skipping setup asks for your name instead of quietly naming you, and scores that depend on sex wait
  for you to choose one instead of assuming.
- Height and weight are entered together in one sheet; logging weight is still one tap on Stats.
- A new welcome screen, and the app opens straight onto a fully loaded Today with no half-drawn frame.

### Plan
- The plan builder is rebuilt as three steps that plan around effort: hard sets with reps in reserve,
  a weekly floor for every muscle, and a cap so none is buried.
- Training days are spaced for recovery, and the week fits the session length you set.
- Every slot offers three ranked suggestions, and each improvement can be ticked on its own.
- Weekly coverage flags heavy and short muscles, Optimise fills the gaps in a tap, and its warnings
  count only the muscles a lift trains directly.
- New plans start loaded lifts on sensible weights from your bodyweight and level, not 0 kg, and so
  does an exercise you add to a routine or mid-workout.
- A new routine asks which day it goes on; routines with no day are flagged.
- Create your own exercises, with their muscles and gear.

### Sets
- Set schemes per lift: straight sets, a top set with back-offs, drop sets, or your own mix.
- A redesigned Sets sheet: the scheme drawn as bars you can read at a glance, then one card per set
  role with compact steppers.
- Rep ranges with double progression: hit the top of the range, then the load goes up.
- "Reps left" targets read as one range in plain words ("Stop with 0-1 reps left").
- Back-offs fill in from the top set, scale with the share of bodyweight a lift moves, and round to
  the weights you own.
- Warm-up ramps for loaded lifts, and warm-up exercises in a routine that never count toward stats.
- Bodyweight lifts read "BW" or "+10 kg"; set notation is "3 × 5" everywhere.

### Live workout
- Supersets, in the routine editor and live.
- Rate each set by reps in reserve or RPE, including the last set and after the rest runs out; the
  question sits under the exercise it is about.
- Hold timers with a get-ready count and an adjust step, and a distance stopwatch.
- Rest controls on the lock screen, a pocket lock, keep-awake and tick haptics.
- When you finish, keep today's changes in the routine: loads, reps, exercises you added and new
  supersets, each ticked on its own.
- The notifications notice stays put under the header, and the app explains notifications before
  Android asks.
- Many layout fixes: Plates in the card header, steady underlines under the numbers, no stray gaps.

### Gear
- Weighted vest and dip belt, each with its own load steps.
- A plate calculator that finds every exact build, and a kg or lb setting.

### Exercises
- 575 movements, every one redrawn at one line weight.
- New: tuck front lever row, front lever row, hanging oblique knee raise, and a single-leg chain from
  step-ups to the cobra squat.
- Inverted rows and Australian pull-ups count as possible with dip bars.
- Records, PRs and progression are kept apart per variation: an archer or deficit set is not mixed
  with a flat one.
- FitNotes joins Strong and Hevy as a CSV import source.

### Progress and rewards
- XP is rebuilt around effort, with a full-screen reveal before the summary.
- A lift's PR, its chart, the Stats board and the live "New PR" tag all use the same rule (best
  estimated 1RM), and "New PR" only shows when your latest workout beat the rest.
- Imported workouts tick their day on Today's week strip.
- 28 crests with perks to collect and wear; friends see the one you wear.
- The Veil is rebuilt around relics in houses with set bonuses, echoes that lift the essence rate,
  and a first-visit guide.
- Deeds, seals, relics and crests share flat metal tiers.

### Look and feel
- Setup, Friends, Inbox, the Leaderboard and your profile are restyled.
- System back matches the in-app back everywhere.

### Fixes
- Sync no longer fails when some workouts have titles and others do not.
- Info and favourite buttons work anywhere in a long exercise list.
- Sign out asks first.
- Plan builder fixes for load, caps, ordering and gear.
