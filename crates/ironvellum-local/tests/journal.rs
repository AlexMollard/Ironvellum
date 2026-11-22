#![cfg(any(windows, target_os = "linux", target_os = "macos"))]
use ironvellum_contracts::{Value, snapshot::Snapshot, wire::MAX_TIME};
use ironvellum_local::{
    Install, Journal, LocalError, LockMode, LocklessOpen, PrivateFolder, ReadyArchive, name,
    selected,
};
use rusqlite::{Connection, MAIN_DB, params};
use std::{fs, path::PathBuf};
const FIX: &[u8] =
    include_bytes!("../../../tools/local_connector/contracts/fixtures/proposal.json");
const CONTEXT: &[u8] =
    include_bytes!("../../../tools/local_connector/contracts/fixtures/context.json");
const DB: &str = ".ironvellum-proposals-v1.sqlite";
const ID: &str = "caad118d-ff23-4028-a618-114bc2cf4992";
const DDL: &str = "CREATE TABLE proposals(
    dataset TEXT, snapshot TEXT, key TEXT, id TEXT UNIQUE, proposal BLOB NOT NULL,
    PRIMARY KEY(dataset, snapshot, key))";
struct Fixture(PathBuf);
impl Fixture {
    fn new() -> Self {
        let root = std::env::temp_dir();
        #[cfg(unix)]
        let root = root.canonicalize().unwrap();
        let path = root.join(format!(
            "iv-journal-{}-{}",
            std::process::id(),
            std::thread::current().name().unwrap()
        ));
        fs::create_dir(&path).unwrap();
        Self(path)
    }
    fn open(&self) -> PrivateFolder {
        PrivateFolder::open(
            &selected(&self.0.join("out")).unwrap(),
            LocklessOpen::CreateLeaf,
        )
        .unwrap()
    }
}
impl Drop for Fixture {
    fn drop(&mut self) {
        fs::remove_dir_all(&self.0).unwrap();
    }
}
fn proposal() -> Value {
    serde_json::from_slice(FIX).unwrap()
}
fn legacy() -> Connection {
    let c = Connection::open_in_memory().unwrap();
    c.execute_batch(DDL).unwrap();
    c.pragma_update(None, "application_id", 0x49564331).unwrap();
    c
}
fn seed(c: &Connection, raw: &[u8]) {
    let p = proposal();
    c.execute(
        "INSERT INTO proposals VALUES(?,?,?,?,?)",
        params![
            p["datasetId"].as_str(),
            p["snapshotId"].as_str(),
            "same",
            ID,
            raw
        ],
    )
    .unwrap();
}
fn put(folder: &PrivateFolder, file: &str, data: &[u8]) {
    folder
        .serialized(LockMode::Create)
        .unwrap()
        .write(&name(file.as_ref()).unwrap(), data, Install::New)
        .unwrap();
}
#[test]
fn legacy_image_and_pending_publication() {
    let f = Fixture::new();
    let folder = f.open();
    let s = Snapshot::from_bytes(CONTEXT).unwrap();
    let j = Journal::new(&folder, &s);
    assert!(ReadyArchive::new(&folder).list_ready().unwrap().is_empty());
    assert_eq!(fs::read_dir(f.0.join("out")).unwrap().count(), 0);
    let c = legacy();
    seed(&c, FIX);
    let image = c.serialize(MAIN_DB).unwrap();
    put(&folder, DB, &image);
    assert_eq!(j.read_ready(ID), Err(LocalError::NotFound));
    assert_eq!(j.list_ready(0, 10).unwrap().total, 0);
    let mut draft = proposal()["draft"].clone();
    draft["entries"][0]["sets"] = serde_json::json!(3.0);
    assert_eq!(
        j.create("same", &draft, u64::MAX).unwrap()["proposal"]["draftId"],
        ID
    );
    assert_eq!(j.read_ready(ID).unwrap(), FIX);
    assert_eq!(
        ReadyArchive::new(&folder).list_ready().unwrap()[0].draft_id,
        ID
    );
    draft["name"] = serde_json::json!("Changed");
    assert_eq!(
        j.create("same", &draft, 0),
        Err(LocalError::IdempotencyConflict)
    );
    assert_eq!(fs::read(f.0.join("out").join(DB)).unwrap(), &*image);
}
#[test]
fn clipped_expiry_replay_and_refresh() {
    let f = Fixture::new();
    let folder = f.open();
    let s = Snapshot::from_bytes(CONTEXT).unwrap();
    let j = Journal::new(&folder, &s);
    let draft = proposal()["draft"].clone();
    assert_eq!(j.create("key", &draft, MAX_TIME), Err(LocalError::Clock));
    let p = j.create("key", &draft, MAX_TIME - 1).unwrap();
    assert_eq!(p["proposal"]["expiresAtMs"], MAX_TIME);
    let id = p["proposal"]["draftId"].as_str().unwrap();
    assert_eq!(j.get(id).unwrap(), p);
    let mut bytes = CONTEXT.to_vec();
    bytes.push(b' ');
    let refreshed = Snapshot::from_bytes(&bytes).unwrap();
    assert_eq!(
        Journal::new(&folder, &refreshed).get(id),
        Err(LocalError::JournalConflict)
    );
}

#[test]
fn foreign_schema_corruption_and_unsafe_lock_preserve_originals() {
    let f = Fixture::new();
    let folder = f.open();
    let archive = ReadyArchive::new(&folder);
    let lock = f.0.join("out/.ironvellum-lock-v1");
    let db = f.0.join("out").join(DB);
    for payload in [
        b"user file".to_vec(),
        b"SQLite format 3\0broken".to_vec(),
        {
            let c = legacy();
            c.execute_batch("CREATE TABLE extra(x)").unwrap();
            c.serialize(MAIN_DB).unwrap().to_vec()
        },
        {
            let c = legacy();
            c.pragma_update(None, "application_id", 12).unwrap();
            c.serialize(MAIN_DB).unwrap().to_vec()
        },
        {
            let c = Connection::open_in_memory().unwrap();
            c.execute_batch(&DDL.replace("dataset TEXT", "dataset  TEXT"))
                .unwrap();
            c.pragma_update(None, "application_id", 0x49564331).unwrap();
            c.serialize(MAIN_DB).unwrap().to_vec()
        },
    ] {
        put(&folder, DB, &payload);
        assert_eq!(archive.list_ready(), Err(LocalError::OutboxConflict));
        assert_eq!(fs::read(&db).unwrap(), payload);
        fs::remove_file(&db).unwrap();
    }
    put(&folder, DB, b"user file");
    fs::remove_file(&lock).unwrap();
    assert!(archive.list_ready().is_err());
    assert!(!lock.exists());
    fs::write(&lock, b"occupied").unwrap();
    assert!(archive.list_ready().is_err());
    assert_eq!(fs::read(lock).unwrap(), b"occupied");
    assert_eq!(fs::read(db).unwrap(), b"user file");
}
#[test]
fn invalid_rows_are_classified_before_publication() {
    let f = Fixture::new();
    let folder = f.open();
    let s = Snapshot::from_bytes(CONTEXT).unwrap();
    let j = Journal::new(&folder, &s);
    let invalid = LocalError::Contract(ironvellum_contracts::Refusal::InvalidData);
    let bad = LocalError::JournalConflict;
    let cases: Vec<(Vec<u8>, LocalError)> = vec![
        (b"{".to_vec(), invalid),
        (b"{}".to_vec(), invalid),
        ("3.0".as_bytes().to_vec(), invalid),
    ];
    let update = |field: &str, val: Value| {
        let mut p = proposal();
        p[field] = val;
        serde_json::to_vec(&p).unwrap()
    };
    let cases = cases.into_iter().chain([
        (update("schemaVersion", serde_json::json!(1.0)), bad),
        (
            update("createdAtMs", serde_json::json!(1700001000001.0)),
            bad,
        ),
        (
            update("expiresAtMs", serde_json::json!(1700001000001u64)),
            bad,
        ),
        (
            update("expiresAtMs", serde_json::json!(1800001000001u64)),
            bad,
        ),
        (
            update(
                "draftId",
                serde_json::json!("ace00000-0000-4000-8000-000000000000"),
            ),
            bad,
        ),
    ]);
    for (raw, expected) in cases {
        let c = legacy();
        seed(&c, &raw);
        put(&folder, DB, &c.serialize(MAIN_DB).unwrap());
        assert_eq!(j.get(ID), Err(expected));
        assert_eq!(ReadyArchive::new(&folder).list_ready(), Err(expected));
        assert!(!f.0.join("out").join(format!("{ID}.json")).exists());
        fs::remove_file(f.0.join("out").join(DB)).unwrap();
    }
    let c = legacy();
    seed(&c, FIX);
    c.execute("UPDATE proposals SET proposal=CAST(proposal AS TEXT)", [])
        .unwrap();
    put(&folder, DB, &c.serialize(MAIN_DB).unwrap());
    assert_eq!(j.get(ID), Err(bad));
}
#[test]
fn archive_spans_sources_scoped_pages_skip_missing_and_refuse_unsafe_publications() {
    let f = Fixture::new();
    let folder = f.open();
    let s = Snapshot::from_bytes(CONTEXT).unwrap();
    let j = Journal::new(&folder, &s);
    let d = proposal()["draft"].clone();
    let a = j.create("first", &d, 0).unwrap();
    let b = j.create("second", &d, 1).unwrap();
    let mut context: Value = serde_json::from_slice(CONTEXT).unwrap();
    context["snapshotId"] = serde_json::json!("ace00000-0000-4000-8000-000000000000");
    let other = Snapshot::from_bytes(&serde_json::to_vec(&context).unwrap()).unwrap();
    let j2 = Journal::new(&folder, &other);
    let p = j2.create("first", &d, MAX_TIME - 1).unwrap();
    let id = a["proposal"]["draftId"].as_str().unwrap();
    assert_eq!(j2.get(id), Err(LocalError::NotFound));
    let arc = ReadyArchive::new(&folder);
    let all = arc.list_ready().unwrap();
    assert_eq!(all.len(), 3);
    assert!(all.windows(2).all(|v| v[0].draft_id < v[1].draft_id));
    let old = all.iter().find(|r| r.draft_id == id).unwrap();
    assert_eq!(old.source_sha256, s.sha256());
    assert_eq!(old.dataset_id, s.dataset());
    assert_eq!(old.snapshot_id, s.snapshot());
    assert_eq!(old.filename, a["filename"]);
    assert_ne!(p["filename"], a["filename"]);
    let page = j.list_ready(0, 1).unwrap();
    assert_eq!(page.total, 2);
    assert_eq!(page.next_offset, Some(1));
    assert_eq!(j.list_ready(1, 1).unwrap().next_offset, None);
    assert_eq!(j.list_ready(usize::MAX, usize::MAX).unwrap().items.len(), 0);
    let path = f.0.join("out").join(a["filename"].as_str().unwrap());
    fs::remove_file(&path).unwrap();
    assert_eq!(
        j.list_ready(0, 1).unwrap().items[0].0,
        b["proposal"]["draftId"]
    );
    assert_eq!(arc.list_ready().unwrap().len(), 2);
    fs::write(&path, b"occupied").unwrap();
    assert_eq!(arc.list_ready(), Err(LocalError::OutboxConflict));
    assert_eq!(j.get(id), Err(LocalError::OutboxConflict));
    assert_eq!(fs::read(&path).unwrap(), b"occupied");
    fs::remove_file(&path).unwrap();
    fs::create_dir(&path).unwrap();
    assert_eq!(arc.list_ready(), Err(LocalError::OutboxConflict));
    assert!(path.is_dir());
}
#[test]
fn full_journal_accepts_replay_and_refuses_new_admission() {
    let f = Fixture::new();
    let folder = f.open();
    let s = Snapshot::from_bytes(CONTEXT).unwrap();
    let j = Journal::new(&folder, &s);
    let mut c = legacy();
    seed(&c, FIX);
    let tx = c.transaction().unwrap();
    for i in 1..1000 {
        let id = format!("00000000-0000-4000-8000-{i:012x}");
        let mut p = proposal();
        p["draftId"] = serde_json::json!(id);
        let bytes = serde_json::to_vec(&p).unwrap();
        tx.execute(
            "INSERT INTO proposals VALUES (?,?,?,?,?)",
            params![s.dataset(), s.snapshot(), i.to_string(), id, bytes],
        )
        .unwrap();
    }
    tx.commit().unwrap();
    put(&folder, DB, &c.serialize(MAIN_DB).unwrap());
    let draft = proposal()["draft"].clone();
    assert_eq!(j.create("extra", &draft, 0), Err(LocalError::Capacity));
    assert_eq!(
        j.create("same", &draft, u64::MAX).unwrap()["proposal"]["draftId"],
        ID
    );
    let tx = c.transaction().unwrap();
    let id = "ffffffff-ffff-4fff-bfff-ffffffffffff";
    let mut p = proposal();
    p["draftId"] = serde_json::json!(id);
    tx.execute(
        "INSERT INTO proposals VALUES (?,?,?,?,?)",
        params![
            s.dataset(),
            s.snapshot(),
            "overflow",
            id,
            serde_json::to_vec(&p).unwrap()
        ],
    )
    .unwrap();
    tx.commit().unwrap();
    folder
        .serialized(LockMode::Create)
        .unwrap()
        .write(
            &name(DB.as_ref()).unwrap(),
            &c.serialize(MAIN_DB).unwrap(),
            Install::Replace,
        )
        .unwrap();
    assert_eq!(
        ReadyArchive::new(&folder).list_ready(),
        Err(LocalError::OutboxConflict)
    );
}
#[test]
fn competing_processes_commit_one_replayable_image() {
    use std::process::{Command, Stdio};
    let f = Fixture::new();
    let folder = f.open();
    let spawn = || {
        Command::new(std::env::current_exe().unwrap())
            .args(["--ignored", "--exact", "journal_child", "--nocapture"])
            .env_clear()
            .env("IRONVELLUM_TEST_JOURNAL", f.0.join("out"))
            .stdout(Stdio::piped())
            .spawn()
            .unwrap()
    };
    let children = [spawn(), spawn()];
    let results = children.map(|p| {
        let o = p.wait_with_output().unwrap();
        assert!(o.status.success());
        String::from_utf8(o.stdout).unwrap()
    });
    let archive = ReadyArchive::new(&folder).list_ready().unwrap();
    assert_eq!(archive.len(), 1);
    assert!(results.iter().all(|r| r.contains(&archive[0].draft_id)));
    let image = fs::read(f.0.join("out").join(DB)).unwrap();
    let mut c = Connection::open_in_memory().unwrap();
    c.deserialize_read_exact(MAIN_DB, &image[..], image.len(), true)
        .unwrap();
    assert_eq!(
        c.query_row("SELECT count(*) FROM proposals", [], |r| r.get::<_, i64>(0))
            .unwrap(),
        1
    );
    let sql: String = c
        .query_row(
            "SELECT sql FROM sqlite_schema WHERE sql IS NOT NULL",
            [],
            |r| r.get(0),
        )
        .unwrap();
    assert_eq!(sql, DDL);
    let bytes: Vec<u8> = c
        .query_row("SELECT proposal FROM proposals", [], |r| r.get(0))
        .unwrap();
    assert_eq!(
        bytes,
        fs::read(f.0.join("out").join(&archive[0].filename)).unwrap()
    );
}
#[test]
#[ignore = "invoked only by the synthetic competing-process test"]
fn journal_child() {
    if !std::env::args_os()
        .skip(1)
        .eq(["--ignored", "--exact", "journal_child", "--nocapture"])
    {
        return;
    }
    let Some(path) = std::env::var_os("IRONVELLUM_TEST_JOURNAL") else {
        return;
    };
    let folder = PrivateFolder::open(
        &selected(&PathBuf::from(path)).unwrap(),
        LocklessOpen::Existing,
    )
    .unwrap();
    let source = Snapshot::from_bytes(CONTEXT).unwrap();
    let result = Journal::new(&folder, &source)
        .create("shared", &proposal()["draft"], 11)
        .unwrap();
    println!("{}", result["proposal"]["draftId"].as_str().unwrap());
}

#[test]
fn original_runtime_image_survives_replay_and_append() {
    const IMAGE: &[u8] = include_bytes!("fixtures/legacy-journal/journal.sqlite");
    const BYTES: &[u8] = include_bytes!("fixtures/legacy-journal/proposal.json");
    let f = Fixture::new();
    let folder = f.open();
    let source = Snapshot::from_bytes(CONTEXT).unwrap();
    let j = Journal::new(&folder, &source);
    let p: Value = serde_json::from_slice(BYTES).unwrap();
    let id = p["draftId"].as_str().unwrap();
    let mut legacy = Connection::open_in_memory().unwrap();
    legacy
        .deserialize_read_exact(MAIN_DB, IMAGE, IMAGE.len(), true)
        .unwrap();
    let key: String = legacy
        .query_row("SELECT key FROM proposals WHERE id=?", [id], |r| r.get(0))
        .unwrap();
    put(&folder, DB, IMAGE);
    put(&folder, &format!("{id}.json"), BYTES);
    assert_eq!(j.get(id).unwrap()["proposal"], p);
    assert_eq!(
        j.create(&key, &p["draft"], u64::MAX).unwrap()["proposal"],
        p
    );
    assert_eq!(j.read_ready(id).unwrap(), BYTES);
    assert_eq!(fs::read(f.0.join("out").join(DB)).unwrap(), IMAGE);
    let second = j
        .create("native-second", &p["draft"], 1700001000333)
        .unwrap();
    assert_ne!(second["proposal"]["draftId"], id);
    assert_eq!(j.read_ready(id).unwrap(), BYTES);
    let updated = fs::read(f.0.join("out").join(DB)).unwrap();
    legacy
        .deserialize_read_exact(MAIN_DB, &updated[..], updated.len(), true)
        .unwrap();
    let old: Vec<u8> = legacy
        .query_row("SELECT proposal FROM proposals WHERE key=?", [key], |r| {
            r.get(0)
        })
        .unwrap();
    assert_eq!(old, BYTES);
    assert_eq!(
        legacy
            .query_row("SELECT count(*) FROM proposals", [], |r| r.get::<_, u64>(0))
            .unwrap(),
        2
    );
}
