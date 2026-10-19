# Translating Ironvellum

Ironvellum ships in English today. This is how a translation works, and what the project owner has to set up to
take one in. No translations are accepted before 1.9, and no Weblate project exists yet: this page is the
preparation.

## What a translator edits

One file per language: `app/src/main/res/values-<code>/strings.xml`, translated from the English
`app/src/main/res/values/strings.xml` (the Weblate "monolingual base"). Do not edit the English file in a
translation. Read the comment above a string first: it says what each placeholder is and where the text shows.

### Plain first, themed optional

The app has two vocabularies (docs/GLOSSARY.md): plain words (workout, routine, streak) and themed names (trial,
rite, oath), switched in Settings > Appearance > Themed names. Every sentence that names one of those concepts
exists twice, as complete sentences:

```xml
<string name="settings_summons_evening_caption">One evening reminder on days your weekly plan holds a routine you have not begun.</string>
<string name="settings_summons_evening_caption__t">One evening reminder on days your cycle holds a rite you have not begun.</string>
```

- `key` is the plain wording. Translate every one of these first: a language ships on its plain strings alone.
- `key__t` is the themed wording. It is optional. Translate it only if you want the language to offer the
  themed switch, and then translate all of them.
- A string with no `__t` twin is shown the same in both modes.
- Each twin is a whole sentence, never a plain sentence with one word swapped. Grammar differs between the two
  ("a workout" / "a trial"), so write each one the way a native speaker would say it.

### `themed_strings_complete`

Each language folder holds `values-<code>/bools.xml`:

```xml
<bool name="themed_strings_complete">false</bool>
```

Set it to `true` only when every `key__t` in the English file has a translation. While it is `false` the app shows
the plain strings in both modes and hides the Themed names switch (Settings > Appearance and the first-run Names
step). The unit test `StringsXmlTest` fails if a language folder lacks this file, or says `true` while a themed
string is missing. English declares `true` in `values/bools.xml`.

### What the themed words mean

The glossary lists every concept with its plain and themed name; the themed ones are dark-fantasy, ink and vellum:

| Plain | Themed | Meaning |
|---|---|---|
| workout | trial | one training session |
| routine | rite | a saved session template |
| weekly plan | cycle | the routines laid out across the week |
| streak | oath | consecutive weeks trained |
| PR | peak | a personal record on a lift |
| history | Chronicle | the log of past workouts |
| friends | allies | people you follow in the cloud features |

The reward layer (deeds, rank titles, crests, the Veil) is themed in both modes: those names are single strings
with an `always_` prefix, and are kept in their themed register in every language. Exercise names, exercise guides
and the title, skill and crest catalogues stay English data for now and are not in `strings.xml`.

## Placeholders and formatting

- Placeholders are positional: `%1$s`, `%2$d`. Keep all of them, and reorder freely to suit your grammar.
- `<plurals>` need every quantity your language uses (CLDR categories: zero, one, two, few, many, other). English
  provides `one` and `other`; Weblate adds the rest.
- `%%` is a literal percent sign. Escape an apostrophe as `\'` and a quote as `\"`, write `&amp;` and `&lt;`.
- Entries marked `translatable="false"` (separators, number patterns, the app name) are not translated.
- A translated string must keep the same placeholders as its source; Android lint flags a mismatch.

## Length limits

Text sits in small controls. Keep to roughly the English length (a language that runs more than 30% longer will be
cut by the layout, which the pseudolocale check shows):

- Buttons, tabs and chips (Today's Begin button, the bottom navigation, segmented pickers such as Low / Standard
  / High, the split picker, goal chips): one to two short words, about 12 characters. Where a string has a
  `_short` form (the split picker) it is the one-line version.
- Bottom navigation labels: one word, 10 characters at most.
- Home-screen widgets (the 4x2 and 2x1 sizes): short phrases only. The 2x1 widget shows a streak figure, one
  line of text and a button; labels there are 10 to 14 characters.
- The en-XA sweep (2026-10-19) cut these in the 1.8 layouts, so keep them short: the split picker tab "Upper / lower", the Friends tab label, routine names in the week strip ("Full Body"), the "Your weekly plan" header (wraps), the plan builder title, and the trailing text of Stats > Body rows.
- Notifications: title about 40 characters, body one or two sentences.
- Spoken text (`..._description`, read by a screen reader) has no length limit, but should read naturally aloud.

## Weblate component settings (owner, later)

When the owner creates the Weblate project, one component carries all languages:

| Setting | Value |
|---|---|
| File format | Android String Resource |
| File mask | `app/src/main/res/values-*/strings.xml` |
| Monolingual base language file | `app/src/main/res/values/strings.xml` |
| Template for new translations | `app/src/main/res/values/strings.xml` |
| Language code style | Android (so folders are `values-de`, `values-pt-rBR`) |
| Add-ons | Android language names; flag untranslated; no automatic commits to `main` without review |
| New language | allowed, but see the checklist below |

Do not set up a GitHub Action or any automatic trigger for this: the repository's CI policy is manual only.

### Checklist for each new language

1. Weblate creates `values-<code>/strings.xml`. Add `values-<code>/bools.xml` with `themed_strings_complete`
   (`false` until every `__t` is done). Without it `StringsXmlTest` fails.
2. The language appears in Android's per-app language list as soon as its folder exists: the build generates the
   locale list from the `values-*` folders (`androidResources.generateLocaleConfig`, with English as the default
   in `res/resources.properties`). A half-translated language is therefore offered to users. Decide a completeness
   threshold (for example, merge the folder only once the plain strings are 100% done).
3. Run `python3 tools/gate.py` and check the pseudolocale screens still fit with the real text.

## Testing with pseudolocales

Debug builds ship the `en-XA` (accented, about 30% longer) and `ar-XB` (right to left) pseudolocales
(`isPseudoLocalesEnabled`). Set one for the app without touching the system language:

```
adb shell cmd locale set-app-locales com.ironvellum.app --locales en-XA
```

Any English text that stays unaccented is a string that is still hard-coded in Kotlin.
