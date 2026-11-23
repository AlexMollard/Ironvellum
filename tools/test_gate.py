"""Check that the gate keeps the first instrumented attempt: python3 tools/test_gate.py"""

from contextlib import nullcontext
import os
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

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


class HostInvocationTest(unittest.TestCase):
    @unittest.skipUnless(os.name == "posix", "Run actual POSIX process contracts on a POSIX host")
    def test_actual_posix_shell_receives_each_gradle_argument(self):
        with tempfile.TemporaryDirectory(prefix="gate checkout ") as root, patch.object(gate, "ROOT", root):
            with open(os.path.join(root, "gradlew"), "w") as script:
                script.write('printf \'%s\\n\' "$@"\n')
            tasks = [":app:contract", "-Pname=value with spaces"]
            code, dropped, log = gate.gradle(tasks, None, "actual-host")
            self.assertEqual(0, code)
            self.assertFalse(dropped)
            with open(log) as output:
                self.assertEqual(tasks + ["--configuration-cache", "--continue", "--console=plain"], output.read().splitlines())

    @unittest.skipUnless(os.name == "posix", "Run actual POSIX process contracts on a POSIX host")
    def test_actual_posix_cleanup_stops_its_own_process_group(self):
        proc = subprocess.Popen(["sh", "-c", "sleep 60"], start_new_session=True)
        try:
            gate.kill_tree(proc)
            self.assertLess(proc.wait(timeout=5), 0)
        finally:
            if proc.poll() is None:
                gate.kill_tree(proc)
                proc.wait(timeout=5)

    def test_posix_gradle_preserves_arguments_and_owns_its_process_group(self):
        with tempfile.TemporaryDirectory(prefix="gate checkout ") as root, \
                patch.object(gate, "ROOT", root), \
                patch.object(gate.os, "name", "posix"), \
                patch.object(gate.subprocess, "Popen") as popen, \
                patch.object(gate, "failed_tasks", return_value=[]):
            popen.return_value.wait.return_value = 0
            tasks = [":app:testFossDebugUnitTest", "-Ptest.name=some value"]
            self.assertEqual(0, gate.gradle(tasks, None, "host-test")[0])
            args, kwargs = popen.call_args
            self.assertEqual(["sh", os.path.join(root, "gradlew"), *tasks,
                              "--configuration-cache", "--continue", "--console=plain"], args[0])
            self.assertFalse(kwargs["shell"])
            self.assertTrue(kwargs["start_new_session"])

    def test_posix_cleanup_targets_only_the_owned_process_group(self):
        with patch.object(gate.os, "name", "posix"), \
                patch.object(gate.os, "killpg", create=True) as killpg, \
                patch.object(gate, "signal", Mock(SIGKILL=9), create=True), \
                patch.object(gate.subprocess, "run") as run:
            gate.kill_tree(Mock(pid=1234))
            killpg.assert_called_once_with(1234, 9)
            run.assert_not_called()


class BudgetVerdictTest(unittest.TestCase):
    def test_unavailable_retry_keeps_the_whole_gate_red(self):
        with patch.object(sys, "argv", ["gate.py"]), \
                patch.object(gate, "pick_serial", return_value="emulator-5554"), \
                patch.object(gate.device, "emulator_lock", return_value=nullcontext()), \
                patch.object(gate, "revive", side_effect=[True, False]), \
                patch.object(gate, "gradle", side_effect=[(0, False, "unused"), (0, True, "unused")]) as gradle, \
                patch.object(gate, "web_and_backend", return_value=[]), \
                patch.object(gate, "failed_tasks", return_value=[]), \
                patch.object(gate, "keep_first_attempt", return_value=[]), \
                patch.object(gate, "counts", return_value=(1, 0)), \
                patch.object(gate, "declared_tests", return_value=1), \
                patch.object(gate, "lint_summary", return_value="0 errors, 0 warnings"):
            self.assertEqual(1, gate.main())
            self.assertEqual(2, gradle.call_count)

    def test_gate_verdict_changes_at_budget_boundary(self):
        for elapsed, expected in ((gate.BUDGET_S - 0.01, 0), (gate.BUDGET_S, 1), (gate.BUDGET_S + 0.01, 1)):
            with self.subTest(elapsed=elapsed), \
                    patch.object(sys, "argv", ["gate.py", "--no-device"]), \
                    patch.object(gate.time, "monotonic", side_effect=[0, elapsed]), \
                    patch.object(gate, "gradle", return_value=(0, False, "unused")), \
                    patch.object(gate, "failed_tasks", return_value=[]), \
                    patch.object(gate.shutil, "which", return_value="npm"), \
                    patch.object(gate, "run", return_value=0), \
                    patch.object(gate, "counts", return_value=(1, 0)), \
                    patch.object(gate, "declared_tests", return_value=1), \
                    patch.object(gate, "lint_summary", return_value="0 errors, 0 warnings"):
                self.assertEqual(expected, gate.main())

    def test_failed_web_or_backend_check_keeps_the_whole_gate_red(self):
        for fail_backend in (False, True):
            with self.subTest(backend=fail_backend), \
                    patch.object(sys, "argv", ["gate.py", "--no-device", "--backend"]), \
                    patch.object(gate, "gradle", return_value=(0, False, "unused")), \
                    patch.object(gate, "failed_tasks", return_value=[]), \
                    patch.object(gate.shutil, "which", return_value="npm"), \
                    patch.object(gate, "backend", return_value=int(fail_backend)), \
                    patch.object(gate, "run", side_effect=lambda cmd: int(not fail_backend and cmd[-1] == "test")), \
                    patch.object(gate, "counts", return_value=(1, 0)), \
                    patch.object(gate, "declared_tests", return_value=1), \
                    patch.object(gate, "lint_summary", return_value="0 errors, 0 warnings"):
                self.assertEqual(1, gate.main())


if __name__ == "__main__":
    unittest.main()
