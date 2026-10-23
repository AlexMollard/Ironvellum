"""Check that the gate keeps the first instrumented attempt: python3 tools/test_gate.py"""

import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gate  # noqa: E402

XML = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.x.SomeTest" tests="3" failures="1" errors="1">
  <testcase name="passes" classname="com.x.SomeTest" time="0.1"/>
  <testcase name="fails" classname="com.x.SomeTest" time="0.1"><failure message="boom">trace</failure></testcase>
  <testcase name="crashes" classname="com.x.SomeTest" time="0.1"><error message="npe">trace</error></testcase>
</testsuite>
"""


class FirstAttemptTest(unittest.TestCase):
    def test_failing_tests_are_named_and_results_survive_an_overwrite(self):
        base = tempfile.mkdtemp()
        results = os.path.join(base, "results")
        os.makedirs(os.path.join(results, "device"))
        with open(os.path.join(results, "device", "TEST-a.xml"), "w") as handle:
            handle.write(XML)
        with open(os.path.join(results, "logcat-x.txt"), "w") as handle:
            handle.write("log")
        kept = os.path.join(base, "kept")
        names = gate.keep_first_attempt(results, kept)
        self.assertEqual(names, ["com.x.SomeTest.fails", "com.x.SomeTest.crashes"])
        with open(os.path.join(results, "device", "TEST-a.xml"), "w") as handle:
            handle.write("<testsuite/>")  # what the retry does
        self.assertEqual(gate.failing_tests(kept), names)
        self.assertTrue(os.path.exists(os.path.join(kept, "logcat-x.txt")))

    def test_missing_results_directory_is_empty_not_an_error(self):
        self.assertEqual(gate.keep_first_attempt(os.path.join(tempfile.mkdtemp(), "none"), tempfile.mkdtemp() + "/k"), [])


if __name__ == "__main__":
    unittest.main()
