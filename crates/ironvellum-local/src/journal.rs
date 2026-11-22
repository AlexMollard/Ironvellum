use crate::{Install, LocalError, LockMode, LockedFolder, PrivateFolder, name, storage::uuid_v4};
use ironvellum_contracts::{
    Value,
    catalogue::check_entries,
    encode, loads, schema,
    snapshot::Snapshot,
    wire::{MAX_DEPTH, MAX_FRAME, MAX_TIME},
};
use rusqlite::{Connection, MAIN_DB, Row, limits::Limit, params, types::ValueRef};
use serde_json::json;

const DB: &str = ".ironvellum-proposals-v1.sqlite";
const IMAGE_LIMIT: usize = 32 << 20;
const APP_ID: i64 = 0x49564331;
const CAPACITY: usize = 1000;
const LIFETIME: u64 = 30 * 86400 * 1000;
const DDL: &str = "CREATE TABLE proposals(
    dataset TEXT, snapshot TEXT, key TEXT, id TEXT UNIQUE, proposal BLOB NOT NULL,
    PRIMARY KEY(dataset, snapshot, key))";
type Result<T> = std::result::Result<T, LocalError>;

struct Database<'a> {
    guard: LockedFolder<'a>,
    db: Connection,
    install: Install,
    sql_error: LocalError,
}
impl<'a> Database<'a> {
    fn open(folder: &'a PrivateFolder, detached: bool) -> Result<Self> {
        let guard = folder.serialized(if detached {
            LockMode::Existing
        } else {
            LockMode::Create
        })?;
        let sql_error = if detached {
            LocalError::OutboxConflict
        } else {
            LocalError::StorageUnavailable
        };
        let mut db = Connection::open_in_memory().map_err(|_| sql_error)?;
        db.set_limit(Limit::SQLITE_LIMIT_ATTACHED, 0)
            .map_err(|_| sql_error)?;
        db.set_limit(Limit::SQLITE_LIMIT_LENGTH, (MAX_FRAME + 1024) as i32)
            .map_err(|_| sql_error)?;
        db.execute_batch("PRAGMA trusted_schema=OFF; PRAGMA temp_store=MEMORY")
            .map_err(|_| sql_error)?;
        let install = match guard.read(&name(DB.as_ref())?, IMAGE_LIMIT) {
            Ok(bytes) => {
                if !bytes.starts_with(b"SQLite format 3\0") {
                    return Err(LocalError::OutboxConflict);
                }
                // Exact-length slice, bounded well below i32::MAX. rusqlite allocates with
                // sqlite3_malloc and transfers ownership with SQLITE_DESERIALIZE_FREEONCLOSE.
                db.deserialize_read_exact(MAIN_DB, bytes.as_slice(), bytes.len(), detached)
                    .map_err(|_| sql_error)?;
                let id: i64 = db
                    .query_row("PRAGMA application_id", [], |r| r.get(0))
                    .map_err(|_| sql_error)?;
                if id != APP_ID {
                    return Err(LocalError::OutboxConflict);
                }
                let mut stmt = db
                    .prepare("SELECT sql FROM sqlite_schema WHERE sql IS NOT NULL LIMIT 2")
                    .map_err(|_| sql_error)?;
                let mut rows = stmt.query([]).map_err(|_| sql_error)?;
                let ddl: Option<String> = rows
                    .next()
                    .map_err(|_| sql_error)?
                    .map(|r| r.get(0))
                    .transpose()
                    .map_err(|_| sql_error)?;
                if ddl.as_deref() != Some(DDL) || rows.next().map_err(|_| sql_error)?.is_some() {
                    return Err(LocalError::OutboxConflict);
                }
                Install::Replace
            }
            Err(LocalError::Missing) if !detached => {
                db.pragma_update(None, "application_id", APP_ID)
                    .map_err(|_| sql_error)?;
                db.execute_batch(DDL).map_err(|_| sql_error)?;
                Install::New
            }
            Err(e) => return Err(e),
        };
        Ok(Self {
            guard,
            db,
            install,
            sql_error,
        })
    }
    fn lookup(&self, source: &Snapshot, filter: Lookup<'_>) -> Result<Option<Proposal>> {
        let (sql, value) = match filter {
            Lookup::Key(k) => (
                "SELECT dataset,snapshot,id,proposal FROM proposals WHERE dataset=? AND snapshot=? AND key=?",
                k,
            ),
            Lookup::Id(id) => (
                "SELECT dataset,snapshot,id,proposal FROM proposals WHERE dataset=? AND snapshot=? AND id=?",
                id,
            ),
        };
        let mut stmt = self.db.prepare(sql).map_err(|_| self.sql_error)?;
        let mut rows = stmt
            .query(params![source.dataset(), source.snapshot(), value])
            .map_err(|_| self.sql_error)?;
        rows.next()
            .map_err(|_| self.sql_error)?
            .map(|row| Proposal::decode(row, Some(source)))
            .transpose()
    }
    fn commit(&self, key: &str, p: &Proposal) -> Result<()> {
        // A single INSERT is an autocommit transaction. Install its complete image
        // before publication so replay recovers the assigned UUID and exact BLOB.
        self.db
            .execute(
                "INSERT INTO proposals VALUES(?,?,?,?,?)",
                params![
                    p.value["datasetId"].as_str(),
                    p.value["snapshotId"].as_str(),
                    key,
                    p.id(),
                    p.bytes
                ],
            )
            .map_err(|_| self.sql_error)?;
        let image = self.db.serialize(MAIN_DB).map_err(|_| self.sql_error)?;
        if image.len() > IMAGE_LIMIT {
            return Err(LocalError::Capacity);
        }
        self.guard.write(&name(DB.as_ref())?, &image, self.install)
    }
}
enum Lookup<'a> {
    Key(&'a str),
    Id(&'a str),
}
struct Proposal {
    value: Value,
    bytes: Vec<u8>,
}
impl Proposal {
    fn decode(row: &Row<'_>, source: Option<&Snapshot>) -> Result<Self> {
        let bad = LocalError::JournalConflict;
        let ValueRef::Blob(raw) = row.get_ref(3).map_err(|_| bad)? else {
            return Err(bad);
        };
        let value = loads(raw, MAX_FRAME, MAX_DEPTH)?;
        schema::validate_document("proposal-v1", &value)?;
        let created = value["createdAtMs"].as_u64().ok_or(bad)?;
        let expires = value["expiresAtMs"].as_u64().ok_or(bad)?;
        if value["schemaVersion"].as_u64() != Some(1)
            || expires <= created
            || expires - created > LIFETIME
        {
            return Err(bad);
        }
        for (index, field) in ["datasetId", "snapshotId", "draftId"]
            .into_iter()
            .enumerate()
        {
            let text = row
                .get_ref(index)
                .map_err(|_| bad)?
                .as_str()
                .map_err(|_| bad)?;
            if value[field].as_str() != Some(text) {
                return Err(bad);
            }
        }
        check_entries(value["draft"]["entries"].as_array().ok_or(bad)?)?;
        if source.is_some_and(|s| {
            value["datasetId"] != s.dataset()
                || value["snapshotId"] != s.snapshot()
                || value["contextSha256"] != s.sha256()
        }) {
            return Err(bad);
        }
        Ok(Self {
            value,
            bytes: raw.to_vec(),
        })
    }
    fn id(&self) -> &str {
        self.value["draftId"].as_str().expect("validated UUID")
    }
    fn title(&self) -> &str {
        self.value["draft"]["name"]
            .as_str()
            .expect("validated draft name")
    }
    fn filename(&self) -> String {
        format!("{}.json", self.id())
    }
    fn ready(&self, g: &LockedFolder<'_>) -> Result<bool> {
        match g.read(&name(self.filename().as_ref())?, self.bytes.len()) {
            Ok(current) if current == self.bytes => Ok(true),
            Err(LocalError::Missing) => Ok(false),
            _ => Err(LocalError::OutboxConflict),
        }
    }
    fn publish(self, g: &LockedFolder<'_>) -> Result<Value> {
        let filename = self.filename();
        g.publish(&name(filename.as_ref())?, &self.bytes)?;
        Ok(json!({"status":"proposed","filename":filename,"proposal":self.value}))
    }
}
/// Source-scoped admissions and exact-byte publications in the v1 outbox.
pub struct Journal<'a> {
    files: &'a PrivateFolder,
    source: &'a Snapshot,
}
#[derive(Debug, PartialEq, Eq)]
pub struct ReadyPage {
    pub items: Vec<(String, String)>,
    pub total: usize,
    pub next_offset: Option<usize>,
}
impl<'a> Journal<'a> {
    pub fn new(files: &'a PrivateFolder, source: &'a Snapshot) -> Self {
        Self { files, source }
    }
    pub fn create(&self, key: &str, draft: &Value, now_ms: u64) -> Result<Value> {
        let tool = schema::tool(1, "create_workout_draft").expect("embedded v1 tool");
        schema::validate(
            &tool["inputSchema"],
            &json!({"idempotencyKey":key,"draft":draft}),
        )?;
        check_entries(draft["entries"].as_array().expect("validated draft"))?;
        let db = Database::open(self.files, false)?;
        if let Some(p) = db.lookup(self.source, Lookup::Key(key))? {
            if !schema::equal(draft, &p.value["draft"]) {
                return Err(LocalError::IdempotencyConflict);
            }
            return p.publish(&db.guard);
        }
        let count: usize = db
            .db
            .query_row("SELECT count(*) FROM proposals", [], |r| r.get(0))
            .map_err(|_| db.sql_error)?;
        if count >= CAPACITY {
            return Err(LocalError::Capacity);
        }
        if now_ms >= MAX_TIME {
            return Err(LocalError::Clock);
        }
        let value = json!({"type":"ironvellum.workout-draft","schemaVersion":1,"datasetId":self.source.dataset(),
      "snapshotId":self.source.snapshot(),"contextSha256":self.source.sha256(),"draftId":uuid_v4()?,
      "sender":"Personal connector","createdAtMs":now_ms,"expiresAtMs":(now_ms+LIFETIME).min(MAX_TIME),"draft":draft});
        let bytes = encode(&value)?;
        if bytes.len() > MAX_FRAME {
            return Err(LocalError::FileLimit);
        }
        let p = Proposal { value, bytes };
        db.commit(key, &p)?;
        p.publish(&db.guard)
    }
    pub fn get(&self, id: &str) -> Result<Value> {
        let db = Database::open(self.files, false)?;
        db.lookup(self.source, Lookup::Id(id))?
            .ok_or(LocalError::NotFound)?
            .publish(&db.guard)
    }
    pub fn read_ready(&self, id: &str) -> Result<Vec<u8>> {
        let db = Database::open(self.files, false)?;
        let p = db
            .lookup(self.source, Lookup::Id(id))?
            .ok_or(LocalError::NotFound)?;
        if !p.ready(&db.guard)? {
            return Err(LocalError::NotFound);
        }
        Ok(p.bytes)
    }
    pub fn list_ready(&self, offset: usize, limit: usize) -> Result<ReadyPage> {
        let db = Database::open(self.files, false)?;
        let mut stmt=db.db.prepare("SELECT dataset,snapshot,id,proposal FROM proposals WHERE dataset=? AND snapshot=? ORDER BY id").map_err(|_|db.sql_error)?;
        let mut rows = stmt
            .query(params![self.source.dataset(), self.source.snapshot()])
            .map_err(|_| db.sql_error)?;
        let mut items = Vec::new();
        let mut total = 0;
        while let Some(row) = rows.next().map_err(|_| db.sql_error)? {
            let p = Proposal::decode(row, Some(self.source))?;
            if p.ready(&db.guard)? {
                if total >= offset && items.len() < limit {
                    items.push((p.id().to_owned(), p.title().to_owned()))
                }
                total += 1;
            }
        }
        let end = offset.saturating_add(items.len());
        Ok(ReadyPage {
            items,
            total,
            next_offset: (end < total).then_some(end),
        })
    }
}
/// Detached, read-only view of published proposals across all recorded source identities.
pub struct ReadyArchive<'a> {
    files: &'a PrivateFolder,
}
#[derive(Debug, PartialEq, Eq)]
pub struct ArchiveItem {
    pub draft_id: String,
    pub name: String,
    pub dataset_id: String,
    pub snapshot_id: String,
    pub source_sha256: String,
    pub filename: String,
}
impl<'a> ReadyArchive<'a> {
    pub fn new(files: &'a PrivateFolder) -> Self {
        Self { files }
    }
    pub fn list_ready(&self) -> Result<Vec<ArchiveItem>> {
        if !self.files.dir().exists_no_follow(&name(DB.as_ref())?)? {
            return Ok(vec![]);
        }
        let db = Database::open(self.files, true)?;
        let mut stmt = db
            .db
            .prepare("SELECT dataset,snapshot,id,proposal FROM proposals ORDER BY id LIMIT ?")
            .map_err(|_| db.sql_error)?;
        let mut rows = stmt.query([CAPACITY + 1]).map_err(|_| db.sql_error)?;
        let mut result = Vec::new();
        let mut count = 0;
        while let Some(row) = rows.next().map_err(|_| db.sql_error)? {
            if count == CAPACITY {
                return Err(LocalError::OutboxConflict);
            }
            count += 1;
            let p = Proposal::decode(row, None)?;
            if p.ready(&db.guard)? {
                result.push(ArchiveItem {
                    draft_id: p.id().to_owned(),
                    name: p.title().to_owned(),
                    dataset_id: p.value["datasetId"]
                        .as_str()
                        .expect("validated dataset")
                        .to_owned(),
                    snapshot_id: p.value["snapshotId"]
                        .as_str()
                        .expect("validated snapshot")
                        .to_owned(),
                    source_sha256: p.value["contextSha256"]
                        .as_str()
                        .expect("validated hash")
                        .to_owned(),
                    filename: p.filename(),
                })
            }
        }
        Ok(result)
    }
}
