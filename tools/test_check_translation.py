"""Check that the translation checker keeps what it claims: python3 tools/test_check_translation.py"""

import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import check_translation as ct

EN = """<?xml version="1.0" encoding="UTF-8"?>
<resources>
  <string name="app_name" translatable="false">Ironvellum</string>
  <string name="today_button">Begin</string>
  <string name="today_caption__t">Seal %1$s of %2$d sets.</string>
  <plurals name="sets_done">
    <item quantity="one">%d set done</item>
    <item quantity="other">%d sets done</item>
  </plurals>
</resources>
"""


class CheckerTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.en_path = os.path.join(self.tmp, "strings.xml")
        with open(self.en_path, "w", encoding="utf-8") as h:
            h.write(EN)
        self.english = ct.read_strings(self.en_path)

    def write_translation(self, xml):
        d = os.path.join(self.tmp, "values-de")
        os.makedirs(d, exist_ok=True)
        p = os.path.join(d, "strings.xml")
        with open(p, "w", encoding="utf-8") as h:
            h.write(xml)
        return d, ct.read_strings(p)

    def test_a_clean_translation_reports_nothing(self):
        d, tr = self.write_translation(
            """<resources>
            <string name="today_button">Starten</string>
            <string name="today_caption__t">Versegle %1$s von %2$d Sätzen.</string>
            <plurals name="sets_done">
              <item quantity="one">%d Satz fertig</item>
              <item quantity="other">%d Sätze fertig</item>
            </plurals>
            </resources>"""
        )
        errors, warnings = ct.check(self.english, tr, "de")
        errors += ct.check_bools(d, self.english, tr)
        self.assertTrue(any("bools.xml" in e for e in errors))  # bools missing yet
        with open(os.path.join(d, "bools.xml"), "w", encoding="utf-8") as h:
            h.write('<resources><bool name="themed_strings_complete">true</bool></resources>')
        errors, warnings = ct.check(self.english, tr, "de")
        errors += ct.check_bools(d, self.english, tr)
        self.assertEqual((errors, warnings), ([], []))

    def test_missing_extra_and_copied_entries_are_named(self):
        d, tr = self.write_translation(
            """<resources>
            <string name="app_name">Eisenbuch</string>
            <string name="sets_done_missing">x</string>
            </resources>"""
        )
        errors, _ = ct.check(self.english, tr, "de")
        self.assertIn("missing: today_button", errors)
        self.assertIn("missing: today_caption__t", errors)
        self.assertIn("missing: sets_done", errors)
        self.assertIn("extra: sets_done_missing (no such key in English)", errors)
        self.assertIn('copied a translatable=\"false\" entry verbatim: app_name', errors)

    def test_placeholder_mismatch_apostrophe_and_plural_quantity(self):
        d, tr = self.write_translation(
            """<resources>
            <string name="today_button">Starten</string>
            <string name="today_caption__t">Versegle %1$s von Sätzen, don't panic.</string>
            <plurals name="sets_done">
              <item quantity="one">%d Satz fertig</item>
            </plurals>
            </resources>"""
        )
        errors, _ = ct.check(self.english, tr, "de")
        self.assertTrue(any("placeholders for today_caption__t" in e for e in errors), errors)
        self.assertTrue(any("unescaped apostrophe" in e for e in errors), errors)
        self.assertTrue(any("plural quantities for sets_done" in e for e in errors), errors)

    def test_reordering_positional_placeholders_is_fine(self):
        d, tr = self.write_translation(
            """<resources>
            <string name="today_button">Starten</string>
            <string name="today_caption__t">Von %2$d Sätzen %1$s versiegelt.</string>
            <plurals name="sets_done">
              <item quantity="one">%d Satz fertig</item>
              <item quantity="other">%d Sätze fertig</item>
            </plurals>
            </resources>"""
        )
        errors, _ = ct.check(self.english, tr, "de")
        self.assertEqual(errors, [])

    def test_japanese_uses_other_only_and_bools_honesty(self):
        d, tr = self.write_translation(
            """<resources>
            <string name="today_button">開始</string>
            <string name="today_caption__t">%2$dセット中%1$sを封じる。</string>
            <plurals name="sets_done">
              <item quantity="other">%dセット完了</item>
            </plurals>
            </resources>"""
        )
        with open(os.path.join(d, "bools.xml"), "w", encoding="utf-8") as h:
            h.write('<resources><bool name="themed_strings_complete">true</bool></resources>')
        errors, _ = ct.check(self.english, tr, "ja")
        self.assertEqual(errors, [])
        # A false declaration while everything is translated is an error.
        with open(os.path.join(d, "bools.xml"), "w", encoding="utf-8") as h:
            h.write('<resources><bool name="themed_strings_complete">false</bool></resources>')
        errors, _ = ct.check(self.english, tr, "ja")
        errors += ct.check_bools(d, self.english, tr)
        self.assertTrue(any("set it true" in e for e in errors), errors)

    def test_long_button_is_a_warning_not_an_error(self):
        d, tr = self.write_translation(
            """<resources>
            <string name="today_button">Jetzt mit dem Training beginnen</string>
            <string name="today_caption__t">Versiegle %1$s von %2$d Sätzen.</string>
            <plurals name="sets_done">
              <item quantity="one">%d Satz fertig</item>
              <item quantity="other">%d Sätze fertig</item>
            </plurals>
            </resources>"""
        )
        errors, warnings = ct.check(self.english, tr, "de")
        self.assertEqual(errors, [])
        self.assertTrue(any("length: today_button" in w for w in warnings), warnings)


if __name__ == "__main__":
    unittest.main()
