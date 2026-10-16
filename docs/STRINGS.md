# Strings: where screen text lives

Screen text lives in `app/src/main/res/values/strings.xml`, not in Kotlin. This is the developer guide for item 8 of
the 1.8 plan (docs/TODO.md); docs/GLOSSARY.md says which word each concept uses. Phase 1 built the machinery; the
screens move over phase by phase, and the guard below keeps new text from landing in Kotlin meanwhile.

Scope: every screen, dialog, notification, widget, setting, error and generated hint. Not in scope (English data,
a later step): exercise names, the exercise guides (`ExerciseGuides.kt`, `LibraryGuides.kt`), and the title, skill
and crest catalogues (names and descriptions: `Skills.kt`, `TitleEngine.kt`, `Crests.kt`, `CrestCatalogue.kt`, exempt
from the guard below until title/skill names move). User data (a routine called "Full Body A", a typed note) is never a resource: pass it as an argument or
`UiText.Raw`.

## Plain and themed are two whole sentences

Every sentence that names a glossary concept is written twice, as two complete strings:

```xml
<string name="settings_summons_evening_caption">One evening reminder on days your weekly plan holds a routine you have not begun.</string>
<string name="settings_summons_evening_caption__t">One evening reminder on days your cycle holds a rite you have not begun.</string>
```

`key` is the plain wording (the default, and what a language ships on); `key__t` is the themed wording. Never build
one sentence out of concept words (`{trial}`, `%1$s` standing for "workout"): a translator cannot inflect a word they
are handed, and the two modes differ in grammar ("a workout" / "a trial"). StringsXmlTest rejects `{...}` placeholders;
GlossaryTest rejects themed words in plain values and retired words in themed values.

A string that names no concept (a button called "Save", "Rest timer") is plain-only: one `key`, shown in both modes.

## Concept words: `Words.kt` is a facade over resources

A concept word (trial, routine, ally...) is never a Kotlin literal. `Words.trial.s` still works, but each form is a
resource pair, written whole because capitals, plurals and articles are not the same rules in every language:

```xml
<string name="concept_trial_s">workout</string>   <string name="concept_trial_s__t">trial</string>
<string name="concept_trial_a">a workout</string> <string name="concept_trial_a__t">a trial</string>
```

Forms: `s` lower case, `c` capitalised, `t` as the themed name is written mid-sentence, `u` upper case, `pl` `cpl` `tpl`
`upl` their plurals, `a` `ca` `at` `cat` with the article. A `Concept` in `Words.kt` lists only the forms a screen
reads and which resource holds each; there are no derived forms. A form nobody reads has no strings, and asking for it
throws (`WordsFormsTest` scans the sources for every `Words.<concept>.<form>` and resolves each in both modes, so a
new form without strings fails the build, not a screen). To add one: write `concept_<name>_<form>` and `__t`, then add
`"<form>" to f(R.string..., R.string...__t)` to the concept.

A concept is for a word shown ON ITS OWN (a tab label, a heading, a chip, a button). A concept inside a sentence is not
a form: the whole sentence becomes a `key` / `key__t` pair and the call site stops concatenating (see the recipe below).

`bothModes` concepts (the reward layer: deed, the Veil) are themed whatever the switch says, so each form is one
string with no twin, named `always_<name>_<form>`. The `always_` prefix is the contract for any text shown the same in
both modes (it may hold themed words; GlossaryTest skips it): `always_oath_name` is the themed name that stays themed
(`themedName`), `always_themed_names_caption` is the Settings example that names both vocabularies.

The words come from `Words.init` (the app's resources). A JVM unit test has no `init`, so `Words` falls back to the
`StringSource` registered in `src/test/resources/META-INF/services`, which is `DiskStrings`: tests run with
`Words.themed = true` and read `strings.xml` from disk, no setup in each test class.

`Words.fromServer` (plain-mode rewrite of the server's English refusals) matches the server's themed English words,
which stay Kotlin data in a small table (`ServerTerm`), and replaces them with the plain form from the concept's
resources.

`data/WordLines.kt` (`Lines`) and Today's narration (`ui/dashboard/TodayNarration.kt`) are the same idea for whole
sentences: `line_*` and `narration_*` pairs (narration counts are `<plurals>`), read through `Words.text(UiText)`, so
callers still get a `String` that follows the switch.

## Naming: `screen_element_role`

Lower snake case, most general first: `settings_summons_evening_label`, `settings_summons_blocked`,
`session_rest_ended_notification_title`. Roles: `label`, `caption`, `title`, `button`, `hint`, `error`, `description`
(accessibility text), `empty`. The themed twin is the same name plus a final `__t` (a double underscore, once, at the
end). Shared text (OK, Cancel) goes under `common_`. The Phase 2 families are `concept_<name>_<form>`, `always_*`, `line_*`
(the shared lines that were `Lines`) and `narration_*`.

## Using them

Pick by what the text needs:

| Text | In a composable | Elsewhere |
|---|---|---|
| plain-only, no arguments | `stringResource(R.string.x)` | `context.getString(R.string.x)` |
| a plain/themed pair, arguments or plurals, or text a domain function returns | `UiText....asString()` | `UiText....asString(context)` |

```kotlin
UiText.themed(R.string.x, R.string.x__t, count, name).asString()   // pair; positional args
UiText.res(R.string.x, name)                                        // plain-only
UiText.plural(R.plurals.sets, n)                                    // count is the first argument
UiText.themedPlural(R.plurals.sets, R.plurals.sets__t, n)
UiText.Raw(userTypedName)                                           // data, shown as is
```

`UiText` (`domain/UiText.kt`) only describes the text: it holds `R.string.x` ints, so domain code returns it and
stays testable on a plain JVM (generated `R`, no `android.*`). It becomes a `String` at the edge:
`asString()` in `ui/UiTextAndroid.kt`. The composable one reads `Words.themed` (Compose state) and the configuration,
so flipping Settings > Appearance > Themed names or changing the language recomposes the screen. Outside composition
(a notification, a widget) call `asString(context)` at the moment you build the text, never cache it.

Arguments are positional (`%1$s`, `%2$d`); two or more placeholders must be positional. An argument can itself be a
`UiText`. Counts use `<plurals>`, never `if (n == 1)`:

```xml
<plurals name="session_sets_done"><item quantity="one">%d set done</item><item quantity="other">%d sets done</item></plurals>
```

A plural pair needs both `<plurals>` (`name` and `name__t`) with the same placeholders.

## Escaping and comments

aapt2 rules, all checked by StringsXmlTest: write `\'` for an apostrophe and `\"` for a quote; `&amp;` and `&lt;`;
a leading `@` or `?` as `\@` or `\?`. A `%` is written `%%` only in a string that has placeholders; in a string with
none it is a plain `%` (getString does not format without arguments). Put a translator comment above any string that
is not obvious: what a placeholder is, where it shows, a length limit.

```xml
<!-- Settings > Summons: the one evening reminder. %1$s is the weekday. -->
```

Product names and anything not to be translated (`app_name`) take `translatable="false"`.

## The themed switch and languages

A language ships on its plain strings; the themed ones are optional. `res/values/bools.xml` holds
`themed_strings_complete` (true for English). A `values-xx/` translation must declare it in its own `bools.xml`:
`true` only when it translates every `key__t`, otherwise `false`. When it is false the app shows plain strings and
hides the Themed names switch (Settings > Appearance, the first-run Names step): `themedStringsComplete(context)`
and `rememberThemedStringsComplete()` in `ui/UiTextAndroid.kt`. StringsXmlTest checks the declaration exists and that
`true` is honest.

## Testing

`DiskStrings` (`src/test`) reads `strings.xml` from disk and resolves a `UiText` to the same English text, with no
Android and no Robolectric (ids map to names through the generated `R`):

```kotlin
UiText.themed(R.string.x, R.string.x__t).plainText()    // the plain wording
UiText.themed(R.string.x, R.string.x__t).themedText()   // the themed wording
UiText.res(R.string.x).plainText(DiskStrings(themedComplete = false))  // a language without themed strings
```

## The guard: HardcodedUiLiteralTest

A source scan, like GlossaryTest. It counts string literals that look like screen text: any literal handed to
`Text(`, a `label =` / `title =` / `contentDescription =` / `placeholder =` / `supportingText =` / `caption =` ...
argument, a notification builder's `setContentTitle(` and friends, or any `String` parameter of a composable in this
tree (found by reading the declarations, so new wrappers are covered), plus any two-word literal elsewhere outside
logs and thrown errors. The per-file counts are checked in at `app/src/test/resources/hardcoded-ui-literals.baseline`.

- A file with MORE literals than its baseline fails and lists them: use a resource.
- A file with FEWER fails too, naming the number to lower the baseline to, so room you made is not spent elsewhere.
- Re-baseline after a migration: `UPDATE_UI_LITERAL_BASELINE=1 ./gradlew :app:testFossDebugUnitTest --tests "*HardcodedUiLiteralTest*"`.
  `DUMP_UI_LITERALS=<file>` writes every counted literal as `path:line<TAB>text`: the migration worklist.
- Data files (exercise and skill catalogues, import aliases, SQL, export keys) are exempt in the test's `exempt` map,
  each with the reason. Add one only for text that is data, never to silence a screen string.

Migrating a screen means: move its literals into `strings.xml` (a pair where a concept word was interpolated), swap the
call sites, delete any `Lines`/`Words` entry it orphaned, then re-baseline. A screen at zero drops out of the baseline.
