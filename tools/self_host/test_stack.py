import hashlib
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import stack


class StackTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.work = self.root / "install"
        (self.root / ".fresh-start").write_text("healthy iv-test")

    def test_existing_directory_is_never_reused(self):
        self.work.mkdir()
        marker = self.work / "precious"
        marker.write_text("keep")
        with self.assertRaises(FileExistsError):
            stack.new_directory(self.work)
        self.assertEqual("keep", marker.read_text())

    def test_checkout_refused_before_writing(self):
        with self.assertRaises(ValueError):
            stack.new_directory(stack.REPO / "untracked-private-stack")

    def test_download_digest_checked_before_file_creation(self):
        with self.assertRaises(ValueError):
            stack.write_verified(self.work, b"changed", hashlib.sha256(b"expected").hexdigest())
        self.assertFalse(self.work.exists())

    @unittest.skipUnless(os.name == "posix" and os.geteuid() == 0,
                         "requires POSIX distinct-UID access checks")
    def test_bind_inputs_readable_after_container_privilege_drop(self):
        self.work.mkdir(mode=0o700)
        for name in stack.LOCK["sha256"]:
            target = self.work / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(b"input")
            target.chmod(0o600)
        (self.work / ".env").write_bytes(b"private fixture")
        (self.work / ".env").chmod(0o600)
        stack.configure_mount_access(self.work)
        check_mount_access(self.work, self.root)

    def test_schema_is_guarded_inside_one_psql_connection(self):
        commands = []
        def fake(args, **kwargs):
            commands.append((args, kwargs))
            return subprocess.CompletedProcess(args, 0)
        with patch("stack.subprocess.run", side_effect=fake):
            stack.operate(self.root, "iv-test", "schema")
        argv, kwargs = commands.pop()
        self.assertFalse(commands)
        self.assertIn("ON_ERROR_STOP=1", argv)
        sql = kwargs["input"].decode()
        self.assertLess(sql.index("LOCK TABLE auth.users"), sql.index("create table if not exists profiles"))
        for check in ["auth.users", "storage.objects", "storage.buckets", "pg_depend", "pg_trigger"]:
            self.assertIn(check, sql[:sql.index("-- 0001_baseline.sql")])
        self.assertNotIn("reset.sql", sql)
        self.assertNotIn("down", argv)

    def test_engine_failure_propagates(self):
        with patch("stack.subprocess.run", side_effect=subprocess.CalledProcessError(1, "compose")):
            with self.assertRaises(subprocess.CalledProcessError):
                stack.operate(self.root, "iv-test", "schema")
        self.assertFalse((self.root / "schema-applied").exists())

    def test_first_start_refuses_existing_database_or_storage(self):
        (self.root / ".fresh-start").unlink()
        for name in ("db/data", "storage"):
            directory = self.root / "volumes" / name
            directory.mkdir(parents=True)
            evidence = directory / "existing"
            evidence.write_text("keep")
            with patch("stack.subprocess.run") as run:
                with self.assertRaises(ValueError):
                    stack.operate(self.root, "iv-test", "start")
                run.assert_not_called()
            self.assertEqual("keep", evidence.read_text())
            evidence.unlink()
        with patch("stack.subprocess.run") as run:
            stack.operate(self.root, "iv-test", "start")
            self.assertEqual(["up", "-d", "--wait"], run.call_args.args[0][-3:])
            self.assertEqual("healthy iv-test", (self.root / ".fresh-start").read_text())

    def test_failed_start_cannot_authorize_schema_or_skip_next_data_check(self):
        fresh = self.root / ".fresh-start"
        fresh.unlink()
        storage = self.root / "volumes/storage"
        storage.mkdir(parents=True)
        with patch("stack.subprocess.run", side_effect=FileNotFoundError("engine unavailable")):
            with self.assertRaises(FileNotFoundError):
                stack.operate(self.root, "iv-test", "start")
        self.assertFalse(fresh.exists())
        (storage / "retained").write_bytes(b"keep")
        with patch("stack.subprocess.run") as run:
            for action in ("schema", "start"):
                with self.assertRaises(ValueError):
                    stack.operate(self.root, "iv-test", action)
            run.assert_not_called()
        self.assertEqual(b"keep", (storage / "retained").read_bytes())

    def test_preconfirmation_record_refuses_first_start_and_schema(self):
        (self.root / ".fresh-start").write_text("iv-test")
        with patch("stack.subprocess.run") as run:
            for action in ("start", "schema"):
                with self.assertRaises(ValueError):
                    stack.operate(self.root, "iv-test", action)
            run.assert_not_called()
            stack.operate(self.root, "iv-test", "stop")
            self.assertEqual("stop", run.call_args.args[0][-1])

    def test_partial_start_preserves_data_and_refuses_retry(self):
        marker = self.root / ".fresh-start"
        marker.unlink()
        storage = self.root / "volumes/storage"
        storage.mkdir(parents=True)
        data = self.root / "volumes/db/data/PG_VERSION"

        def partial(*args, **kwargs):
            self.assertFalse(marker.exists())
            with self.assertRaises(FileExistsError):
                stack.operate(self.root, "iv-test", "start")
            data.parent.mkdir(parents=True)
            data.write_text("17")
            raise subprocess.CalledProcessError(1, "up --wait")

        with patch("stack.subprocess.run", side_effect=partial):
            with self.assertRaises(subprocess.CalledProcessError):
                stack.operate(self.root, "iv-test", "start")
        with patch("stack.subprocess.run") as run:
            for action in ("schema", "start"):
                with self.assertRaises(ValueError):
                    stack.operate(self.root, "iv-test", action)
            run.assert_not_called()
        self.assertEqual("17", data.read_text())
        self.assertFalse(marker.exists())

    def test_stop_preserves_containers_volumes_and_data(self):
        with patch("stack.subprocess.run") as run:
            stack.operate(self.root, "iv-test", "stop")
        self.assertEqual("stop", run.call_args.args[0][-1])
        self.assertTrue(run.call_args.kwargs["check"])
        self.assertNotIn("-v", run.call_args.args[0])


def check_mount_access(work, scratch):
    # Hard links reproduce direct file binds. A child starting in a tree bind's
    # root tests its contents without traversing the private host installation.
    projected = scratch / "mounted-inputs"
    projected.mkdir(mode=0o755)
    projected.chmod(0o755)
    volumes = work / "volumes"
    files = [*volumes.glob("db/*.sql"), *volumes.glob("api/envoy/*"), volumes / "pooler/pooler.exs"]
    assert len(files) == 12
    for source in files:
        os.link(source, projected / source.name)

    def restricted_read(cwd, names, uid):
        subprocess.run([sys.executable, "-c",
                        "import sys; [open(p, 'rb').read(1) for p in sys.argv[1:]]", *names],
                       cwd=cwd, user=uid, group=uid, extra_groups=[], check=True)

    restricted_read(projected, [p.name for p in files], 65534)
    restricted_read(volumes / "functions", ["deno.jsonc", "main/index.ts", "hello/index.ts"], 65534)
    # The pinned Storage API, Studio and Edge Runtime writers use container root.
    # Rootless Docker maps that user to the owner of the prepared directory.
    for name in ("storage", "snippets", "functions"):
        directory = volumes / name
        assert directory.stat().st_mode & 0o700 == 0o700
        assert directory.stat().st_mode & 0o022 == 0
    probe = volumes / "storage" / "synthetic-object"
    with probe.open("xb") as out:
        out.write(b"readable")
    try:
        probe.chmod(0o644)
        restricted_read(probe.parent, [probe.name], 999)  # pinned imgproxy user
    finally:
        probe.unlink()
    assert work.stat().st_mode & 0o777 == 0o700
    assert (work / ".env").stat().st_mode & 0o777 == 0o600
    subprocess.run([sys.executable, "-c",
                    "import sys\ntry: open(sys.argv[1], 'rb')\nexcept PermissionError: sys.exit(0)\nsys.exit(1)",
                    str(work / ".env")], user=65534, group=65534, extra_groups=[], check=True)


if __name__ == "__main__":
    unittest.main()
