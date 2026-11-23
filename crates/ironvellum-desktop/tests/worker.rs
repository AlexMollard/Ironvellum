use ironvellum_desktop::{
    effects::Effects,
    worker::{Action, Event, Field, Outcome, Work, Worker},
};
use ironvellum_local::{
    Journal, LocalError, LocklessOpen, PrivateFolder, Selected,
    desktop_model::{DesktopState, FolderEdit},
    selected,
};
use std::{
    path::{Path, PathBuf},
    sync::{Arc, Mutex},
    time::Duration,
};
use tempfile::TempDir;

#[derive(Default)]
struct Recorded {
    copied: Vec<String>,
    opened: Vec<PathBuf>,
    revealed: Vec<PathBuf>,
}
struct Fake {
    calls: Arc<Mutex<Recorded>>,
    choice: PathBuf,
}
impl Effects for Fake {
    fn copy(&mut self, s: &str) -> Result<(), LocalError> {
        if s.starts_with("synthetic panic\n") {
            panic!("injected worker failure")
        }
        self.calls.lock().unwrap().copied.push(s.into());
        Ok(())
    }
    fn choose(&mut self) -> Result<Option<PathBuf>, LocalError> {
        Ok(Some(self.choice.clone()))
    }
    fn open_folder(&mut self, p: &Path) -> Result<(), LocalError> {
        self.calls.lock().unwrap().opened.push(p.into());
        Ok(())
    }
    fn reveal(&mut self, p: &Path) -> Result<(), LocalError> {
        self.calls.lock().unwrap().revealed.push(p.into());
        Ok(())
    }
}
struct Fixture {
    _root: TempDir,
    settings: Selected,
    edit: FolderEdit,
    worker: Worker,
    calls: Arc<Mutex<Recorded>>,
}
impl Fixture {
    fn new() -> Self {
        let root = tempfile::tempdir().unwrap();
        #[cfg(unix)]
        let base = root.path().canonicalize().unwrap();
        #[cfg(windows)]
        let base = root.path().to_owned();
        let settings = selected(&base.join("private/settings.json")).unwrap();
        let edit = FolderEdit {
            input: Some(selected(&base.join("exports & 東京")).unwrap()),
            outbox: Some(selected(&base.join("drafts {local}")).unwrap()),
            watch_enabled: false,
        };
        let calls = Arc::new(Mutex::new(Recorded::default()));
        let fake = Fake {
            calls: calls.clone(),
            choice: edit.input.as_ref().unwrap().as_path().into(),
        };
        let worker = Worker::start(
            settings.clone(),
            std::env::current_exe().unwrap(),
            move || fake,
            egui::Context::default(),
        )
        .unwrap();
        Self {
            _root: root,
            settings,
            edit,
            worker,
            calls,
        }
    }
    fn event(&self) -> Event {
        self.worker
            .events
            .recv_timeout(Duration::from_secs(10))
            .unwrap()
    }
    fn observation(&self) -> DesktopState {
        let Event::Observed(Ok(s)) = self.event() else {
            panic!("expected observation")
        };
        Arc::unwrap_or_clone(s)
    }
    fn act(&self, id: u64, action: Action) -> (Result<Outcome, LocalError>, DesktopState) {
        self.worker.requests.send(Work::Act(id, action)).unwrap();
        let Event::Finished(received, result) = self.event() else {
            panic!("completion must precede observation")
        };
        assert_eq!(id, received);
        (result, self.observation())
    }
}
#[test]
fn saves_observes_and_revalidates_each_literal_effect_on_the_worker() {
    let f = Fixture::new();
    f.worker.requests.send(Work::Poll).unwrap();
    assert!(f.observation().settings.is_none());
    let (saved, s) = f.act(
        1,
        Action::Save {
            edit: f.edit.clone(),
            initial: true,
        },
    );
    saved.map(|_| ()).expect("initial folder configuration");
    let configured = s.settings.unwrap();
    assert_eq!(
        configured.state_directory.as_path(),
        f.settings.as_path().with_file_name("settings.json.state")
    );
    let original = std::fs::read(f.settings.as_path()).unwrap();
    let mut change = f.edit.clone();
    change.watch_enabled = true;
    assert!(matches!(
        f.act(
            2,
            Action::Save {
                edit: change.clone(),
                initial: true
            }
        )
        .0,
        Err(LocalError::ReloadRequired)
    ));
    assert_eq!(std::fs::read(f.settings.as_path()).unwrap(), original);
    assert!(
        f.act(
            3,
            Action::Save {
                edit: change,
                initial: false
            }
        )
        .1
        .settings
        .unwrap()
        .watch_enabled
    );
    assert!(
        matches!(f.act(4,Action::Choose(Field::Input)).0,Ok(Outcome::Chosen(Field::Input,Some(ref p))) if p==configured.input_directory.as_path())
    );
    assert!(
        f.act(5, Action::Folder(configured.input_directory.clone()))
            .0
            .is_ok()
    );
    let snapshot = ironvellum_contracts::snapshot::Snapshot::from_bytes(include_bytes!(
        "../../../tools/local_connector/contracts/fixtures/context.json"
    ))
    .unwrap();
    let folder = PrivateFolder::open(&configured.outbox_directory, LocklessOpen::Existing).unwrap();
    let mut proposal: serde_json::Value = serde_json::from_slice(include_bytes!(
        "../../../tools/local_connector/contracts/fixtures/proposal.json"
    ))
    .unwrap();
    proposal["draft"]["name"] = serde_json::json!("{literal} & 東京");
    Journal::new(&folder, &snapshot)
        .create("ready", &proposal["draft"], 1_700_001_000_000)
        .unwrap();
    f.worker.requests.send(Work::Poll).unwrap();
    let row = f.observation().drafts.remove(0);
    assert_eq!(row.display_name, "{literal} & 東京");
    let path = configured.outbox_directory.as_path().join(&row.filename);
    assert!(f.act(6, Action::Reveal(Box::new(row.clone()))).0.is_ok());
    std::fs::remove_file(&path).unwrap();
    assert!(f.act(7, Action::Reveal(Box::new(row))).0.is_err());
    assert!(f.act(8, Action::Copy("synthetic panic".into())).0.is_err());
    assert!(
        f.act(9, Action::Copy("{instructions} & 東京".into()))
            .0
            .is_ok()
    );
    let calls = f.calls.lock().unwrap();
    assert_eq!(calls.opened, [configured.input_directory.as_path()]);
    assert_eq!(calls.revealed, [path]);
    assert_eq!(calls.copied.len(), 1);
    let (prompt, literal) = calls.copied[0].split_once("\n\n").unwrap();
    assert_eq!(prompt, "{instructions} & 東京");
    let locations: serde_json::Value = serde_json::from_str(literal).unwrap();
    assert_eq!(
        locations["settingsFile"].as_str(),
        f.settings.as_path().to_str()
    );
}

#[test]
#[ignore = "build the sibling ironvellum-connector executable first"]
fn dropping_the_desktop_leaves_the_separate_mcp_session_usable() {
    use ironvellum_desktop::{
        copy::{Language, Wording},
        window::DesktopWindow,
    };
    use std::io::{BufRead, BufReader, Write};
    use std::process::{Child, Command, Stdio};
    use std::sync::mpsc;
    struct OwnedChild(Child);
    impl Drop for OwnedChild {
        fn drop(&mut self) {
            let _ = self.0.kill();
            let _ = self.0.wait();
        }
    }
    let Fixture {
        _root,
        worker,
        settings,
        edit,
        ..
    } = Fixture::new();
    let sibling = std::env::current_exe()
        .unwrap()
        .parent()
        .unwrap()
        .parent()
        .unwrap()
        .join(format!(
            "ironvellum-connector{}",
            std::env::consts::EXE_SUFFIX
        ));
    worker
        .requests
        .send(Work::Act(
            0,
            Action::Save {
                edit,
                initial: true,
            },
        ))
        .unwrap();
    assert!(matches!(
        worker.events.recv_timeout(Duration::from_secs(10)),
        Ok(Event::Finished(0, Ok(_)))
    ));
    let mut window = DesktopWindow::new(Language::En, Wording::Plain);
    window.accept(worker.events.recv_timeout(Duration::from_secs(10)).unwrap());
    let mut child = OwnedChild(
        Command::new(sibling)
            .arg("serve-folder")
            .arg("--settings")
            .arg(settings.as_path())
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .spawn()
            .unwrap(),
    );
    let (tx, rx) = mpsc::channel();
    let output = child.0.stdout.take().unwrap();
    let reader = std::thread::spawn(move || {
        for line in BufReader::new(output).lines().map_while(Result::ok) {
            if tx.send(line).is_err() {
                break;
            }
        }
    });
    let mut input = child.0.stdin.take().unwrap();
    let request = |input: &mut std::process::ChildStdin, v: serde_json::Value| {
        writeln!(input, "{v}").unwrap();
        input.flush().unwrap();
    };
    let reply = |id: u64| {
        let deadline = std::time::Instant::now() + Duration::from_secs(8);
        loop {
            let line = rx
                .recv_timeout(deadline.saturating_duration_since(std::time::Instant::now()))
                .unwrap();
            let v: serde_json::Value = serde_json::from_str(&line).unwrap();
            if v["id"].as_u64() == Some(id) {
                return v;
            }
        }
    };
    request(
        &mut input,
        serde_json::json!({"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"desktop-lifetime-test","version":"1"}}}),
    );
    assert!(reply(1)["result"].is_object());
    request(
        &mut input,
        serde_json::json!({"jsonrpc":"2.0","method":"notifications/initialized"}),
    );
    request(
        &mut input,
        serde_json::json!({"jsonrpc":"2.0","id":2,"method":"tools/list"}),
    );
    assert!(reply(2)["result"]["tools"].is_array());
    worker.requests.send(Work::Poll).unwrap();
    let Event::Observed(Ok(s)) = worker.events.recv_timeout(Duration::from_secs(8)).unwrap() else {
        panic!()
    };
    assert!(!s.sessions.is_empty());
    window.accept(Event::Observed(Ok(s)));
    drop(window);
    drop(worker);
    request(
        &mut input,
        serde_json::json!({"jsonrpc":"2.0","id":3,"method":"tools/list"}),
    );
    assert!(
        reply(3)["result"]["tools"]
            .as_array()
            .is_some_and(|tools| !tools.is_empty())
    );
    drop(input);
    drop(child);
    reader.join().unwrap();
}
