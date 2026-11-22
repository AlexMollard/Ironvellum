"""Serialize admissions, commit the original bytes, then publish with no replacement."""
from contextlib import contextmanager
import sqlite3
from uuid import uuid4

from .contract import (INPUTS, MAX_FRAME, MAX_TIME, PROPOSAL_SCHEMA, check_entries, encode, loads,
                       require, validate)

DB_NAME = ".ironvellum-proposals-v1.sqlite"
DB_LIMIT = 32 << 20  # Larger than 1,000 maximum-sized validated v1 bodies/envelopes.
APP_ID = 0x49564331
LIFETIME = 30 * 86400 * 1000
CREATE_TABLE = """CREATE TABLE proposals(
    dataset TEXT, snapshot TEXT, key TEXT, id TEXT UNIQUE, proposal BLOB NOT NULL,
    PRIMARY KEY(dataset, snapshot, key))"""


class Journal:
    CAPACITY = 1000

    def __init__(self, files, snapshot):
        self.files = files
        self.source = snapshot

    @contextmanager
    def _database(self):
        # SQLite sees only memory. Its VFS cannot open paths or follow sidecars in the outbox.
        with self.files.serialized():
            db = sqlite3.connect(":memory:")
            try:
                db.setlimit(sqlite3.SQLITE_LIMIT_ATTACHED, 0)
                db.setlimit(sqlite3.SQLITE_LIMIT_LENGTH, MAX_FRAME + 1024)
                db.execute("PRAGMA trusted_schema=OFF")
                try:
                    original = self.files.read(DB_NAME, DB_LIMIT)
                except FileNotFoundError:
                    original = None
                    db.execute(f"PRAGMA application_id={APP_ID}")
                    db.execute(CREATE_TABLE)
                else:
                    require(original.startswith(b"SQLite format 3\0"), "outbox_conflict")
                    db.deserialize(original)
                    require(db.execute("PRAGMA application_id").fetchone() == (APP_ID,), "outbox_conflict")
                    require(db.execute("SELECT sql FROM sqlite_schema WHERE sql IS NOT NULL LIMIT 2").fetchall()
                            == [(CREATE_TABLE,)], "outbox_conflict")
                yield db, original is not None
            finally:
                db.close()

    def _decode(self, row):
        identifier, raw = row
        require(type(raw) is bytes, "journal_conflict")
        proposal = loads(raw, MAX_FRAME)
        validate(PROPOSAL_SCHEMA, proposal)
        require(type(proposal["schemaVersion"]) is int and type(proposal["createdAtMs"]) is int
                and type(proposal["expiresAtMs"]) is int, "journal_conflict")
        require(0 < proposal["expiresAtMs"] - proposal["createdAtMs"] <= LIFETIME, "journal_conflict")
        require(proposal["draftId"] == identifier and proposal["datasetId"] == self.source.dataset
                and proposal["snapshotId"] == self.source.snapshot and proposal["contextSha256"] == self.source.sha256,
                "journal_conflict")
        check_entries(proposal["draft"]["entries"])
        return proposal

    def _published(self, row, proposal):
        name = proposal["draftId"] + ".json"
        self.files.publish(name, row[1])
        return dict(status="proposed", filename=name, proposal=proposal)

    def create(self, key, draft, now):
        validate(INPUTS["create_workout_draft"], {"idempotencyKey": key, "draft": draft})
        check_entries(draft["entries"])
        source = self.source
        with self._database() as (db, exists):
            row = db.execute("SELECT id,proposal FROM proposals WHERE dataset=? AND snapshot=? AND key=?",
                             (source.dataset, source.snapshot, key)).fetchone()
            if row is not None:
                proposal = self._decode(row)
                require(draft == proposal["draft"], "idempotency_conflict")
            else:
                require(db.execute("SELECT count(*) FROM proposals").fetchone()[0] < self.CAPACITY, "capacity")
                require(0 <= now < MAX_TIME, "clock")
                proposal = dict(type="ironvellum.workout-draft", schemaVersion=1, datasetId=source.dataset,
                                snapshotId=source.snapshot, contextSha256=source.sha256, draftId=str(uuid4()),
                                sender="Personal connector", createdAtMs=now, expiresAtMs=min(now + LIFETIME, MAX_TIME),
                                draft=draft)
                row = (proposal["draftId"], encode(proposal))
                require(len(row[1]) <= MAX_FRAME, "file_limit")
                db.execute("INSERT INTO proposals VALUES (?,?,?,?,?)", (source.dataset, source.snapshot, key, *row))
                db.commit()
                image = db.serialize()
                require(len(image) <= DB_LIMIT, "capacity")
                self.files.write(DB_NAME, image, replace=exists)
            return self._published(row, proposal)

    def get(self, identifier):
        with self._database() as (db, _):
            row = db.execute("SELECT id,proposal FROM proposals WHERE dataset=? AND snapshot=? AND id=?",
                             (self.source.dataset, self.source.snapshot, identifier)).fetchone()
            require(row is not None, "not_found")
            return self._published(row, self._decode(row))
