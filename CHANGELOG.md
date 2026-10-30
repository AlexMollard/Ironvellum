# Changelog

What changed in each release, grouped by what you will notice. The short version ships with the app
store listing; this is the whole list.

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
