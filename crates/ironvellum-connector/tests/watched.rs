use ironvellum_contracts::{Value, snapshot::Snapshot};
use ironvellum_local::{Selected, Settings, save_settings, selected};
use rmcp::{
    RoleClient,
    model::*,
    service::{ClientLifecycleMode, ClientServiceExt, RunningService},
    transport::TokioChildProcess,
};
use serde_json::json;
use sha2::{Digest, Sha256};
use std::{
    fs,
    path::PathBuf,
    sync::atomic::{AtomicUsize, Ordering},
    time::Duration,
};
use tokio::process::Command;
#[path = "watched/retention.rs"]
mod retention;
#[path = "watched/wire.rs"]
mod wire;

struct Fixture(PathBuf);
impl Drop for Fixture {
    fn drop(&mut self) {
        if let Err(e) = fs::remove_dir_all(&self.0)
            && !std::thread::panicking()
        {
            panic!("synthetic fixture cleanup: {e}")
        }
    }
}

type Client = RunningService<RoleClient, ClientConfig>;

#[test]
fn rejected_folder_arguments_keep_private_values_out_of_diagnostics() {
    for args in [
        vec!["serve-folder", "private-canary"],
        vec!["serve-folder", "--settings"],
        vec!["serve-folder", "--set", "private-canary"],
    ] {
        let result = std::process::Command::new(env!("CARGO_BIN_EXE_ironvellum-connector"))
            .args(args)
            .output()
            .unwrap();
        assert_eq!(result.status.code(), Some(2));
        assert!(result.stdout.is_empty());
        assert_eq!(result.stderr, b"Expected serve-folder --settings FILE.\n");
    }
}
const V1: &[u8] = include_bytes!("../../../tools/local_connector/contracts/fixtures/context.json");
const V2: &[u8] =
    include_bytes!("../../../tools/local_connector/contracts/fixtures/v2/context.json");
const PROPOSAL: &[u8] =
    include_bytes!("../../../tools/local_connector/contracts/fixtures/proposal.json");
const POLL: Duration = Duration::from_millis(2080);

impl Fixture {
    fn new() -> (Self, Settings, Selected) {
        static NEXT: AtomicUsize = AtomicUsize::new(0);
        let root = std::env::temp_dir();
        #[cfg(unix)]
        let root = root.canonicalize().unwrap();
        let root = root.join(format!(
            "iv-native-watch-{}-{}",
            std::process::id(),
            NEXT.fetch_add(1, Ordering::Relaxed)
        ));
        fs::create_dir(&root).unwrap();
        let fixture = Fixture(root);
        let path = |leaf| selected(&fixture.0.join(leaf)).unwrap();
        let settings = Settings::new(path("in"), path("out"), path("state"));
        fs::create_dir(settings.input_directory.as_path()).unwrap();
        let config = path("config/settings.json");
        save_settings(&config, &settings).unwrap();
        (fixture, settings, config)
    }
    fn command(&self) -> Command {
        let mut command = Command::new(env!("CARGO_BIN_EXE_ironvellum-connector"));
        command
            .arg("serve-folder")
            .arg("--settings")
            .arg(self.0.join("config/settings.json"))
            .kill_on_drop(true);
        command
    }
    async fn client(&self, modern: bool) -> Client {
        let mode = if modern {
            ClientLifecycleMode::Discover {
                preferred_versions: vec![ProtocolVersion::V_2026_07_28],
            }
        } else {
            ClientLifecycleMode::Initialize
        };
        let mut info = ClientConfig::default();
        info.protocol_version = ProtocolVersion::V_2025_11_25;
        info.serve_with_lifecycle(TokioChildProcess::new(self.command()).unwrap(), mode)
            .await
            .unwrap()
    }
}

async fn call(client: &Client, name: &'static str, args: Value) -> Value {
    let r = client
        .call_tool(
            CallToolRequestParams::new(name).with_arguments(args.as_object().unwrap().clone()),
        )
        .await
        .unwrap();
    if r.is_error == Some(true) {
        json!({"error":r.content[0].as_text().unwrap().text})
    } else {
        r.structured_content.unwrap()
    }
}
async fn status(client: &Client) -> Value {
    call(client, "get_training_status", json!({})).await
}

async fn until_status(client: &Client, predicate: impl Fn(&Value) -> bool) -> Value {
    tokio::time::timeout(Duration::from_secs(16), async {
        loop {
            let value = status(client).await;
            if predicate(&value) {
                return value;
            }
            tokio::time::sleep(POLL).await;
        }
    })
    .await
    .expect("expected watched transition before deadline")
}

async fn resource(client: &Client, uri: &str) -> Result<Value, String> {
    client
        .read_resource(ReadResourceRequestParams::new(uri))
        .await
        .map(|r| serde_json::to_value(r).unwrap())
        .map_err(|e| match e {
            rmcp::service::ServiceError::McpError(e) => {
                assert_eq!(e.code, ErrorCode::RESOURCE_NOT_FOUND, "{uri}");
                assert!(e.data.is_none());
                e.message.to_string()
            }
            other => panic!("unexpected protocol failure: {other}"),
        })
}

async fn matrix(bytes: &[u8], modern: bool) {
    let (fixture, mut settings, path) = Fixture::new();
    let source = Snapshot::from_bytes(bytes).unwrap();
    let token = source.sha256();
    let file = settings.input_directory.as_path().join("shared.json");
    fs::write(&file, bytes).unwrap();
    let client = fixture.client(modern).await;
    let pending = until_status(&client, |v| v["status"] == "awaiting_selection").await;
    assert_eq!(pending["pending"][0]["sourceSha256"], token);
    assert!(pending["snapshot"].is_null());
    settings.selected_dataset_id = Some(source.dataset().to_owned());
    save_settings(&path, &settings).unwrap();
    until_status(&client, |v| v["sourceSha256"] == token).await;
    let scoped = |mut args: Value| {
        args["sourceSha256"] = json!(token);
        args
    };
    let workouts = call(&client, "list_workouts", scoped(json!({}))).await;
    let id = &workouts["workouts"][0]["id"];
    assert_eq!(
        call(&client, "get_workout", scoped(json!({"id":id}))).await["workout"]["id"],
        *id
    );
    assert!(call(&client, "get_progress", scoped(json!({}))).await["progress"].is_object());
    assert!(
        call(&client, "list_saved_workouts", scoped(json!({}))).await["savedWorkouts"].is_array()
    );
    assert!(
        call(&client, "search_movements", scoped(json!({"query":"dip"}))).await["movements"]
            .is_array()
    );
    let view = call(
        &client,
        "get_workout",
        scoped(json!({"id":id,"view":"sets","limit":1})),
    )
    .await;
    if source.version() == 2 {
        assert!(view["nextCursor"].is_string());
    } else {
        assert_eq!(view["error"], "invalid_data");
    }
    let draft = serde_json::from_slice::<Value>(PROPOSAL).unwrap()["draft"].clone();
    let make = scoped(json!({"idempotencyKey":"sdk","draft":draft}));
    let created = call(&client, "create_workout_draft", make.clone()).await;
    assert_eq!(created["status"], "proposed", "{created}");
    let draft_id = &created["proposal"]["draftId"];
    assert_eq!(
        call(
            &client,
            "get_workout_draft",
            scoped(json!({"draftId":draft_id}))
        )
        .await["proposal"],
        created["proposal"]
    );
    let rows = call(&client, "list_ready_drafts", scoped(json!({}))).await;
    assert_eq!(rows["total"], 1, "{rows}");
    let uri = rows["items"][0]["uri"].as_str().unwrap();
    let output = settings
        .outbox_directory
        .as_path()
        .join(created["filename"].as_str().unwrap());
    let raw = fs::read(&output).unwrap();
    let r = resource(&client, uri).await.unwrap();
    assert_eq!(
        r["contents"],
        json!([{"uri":uri,"mimeType":"application/json","text":std::str::from_utf8(&raw).unwrap()}])
    );
    for field in ["ttlMs", "cacheScope", "resultType"] {
        assert_eq!(r.get(field).is_some(), modern, "{field}");
    }
    for bad in [
        "file:///private-canary",
        &format!("{uri}/extra"),
        &uri.to_uppercase(),
    ] {
        assert_eq!(
            resource(&client, bad).await.unwrap_err(),
            "invalid_resource"
        );
    }
    assert_eq!(
        resource(
            &client,
            &format!("ironvellum-draft://{token}/11111111-1111-4111-8111-111111111111")
        )
        .await
        .unwrap_err(),
        "not_found"
    );
    assert_eq!(
        resource(&client, &uri.replace(token, &"0".repeat(64)))
            .await
            .unwrap_err(),
        "source_expired"
    );
    fs::write(&output, b"occupied").unwrap();
    assert_eq!(resource(&client, uri).await.unwrap_err(), "outbox_conflict");
    assert_eq!(fs::read(&output).unwrap(), b"occupied");
    fs::rename(&output, fixture.0.join("occupied")).unwrap();
    client.cancel().await.unwrap();
    let restarted = fixture.client(modern).await;
    until_status(&restarted, |v| v["sourceSha256"] == token).await;
    assert_eq!(
        call(&restarted, "list_ready_drafts", scoped(json!({}))).await["total"],
        0
    );
    assert_eq!(
        call(&restarted, "create_workout_draft", make).await["proposal"],
        created["proposal"]
    );
    assert_eq!(fs::read(&output).unwrap(), raw);
    let mut ids = vec![draft_id.as_str().unwrap().to_owned()];
    for key in ["second", "third"] {
        let args = scoped(json!({"idempotencyKey":key,"draft":draft}));
        let next = call(&restarted, "create_workout_draft", args).await;
        ids.push(next["proposal"]["draftId"].as_str().unwrap().into());
    }
    ids.sort();
    for (offset, id) in ids.iter().enumerate() {
        let r = call(
            &restarted,
            "list_ready_drafts",
            scoped(json!({"limit":1,"offset":offset})),
        )
        .await;
        assert_eq!(r["total"], 3);
        assert_eq!(r["items"].as_array().unwrap().len(), 1);
        assert_eq!(r["items"][0]["draftId"], *id);
        assert_eq!(r["nextOffset"], json!((offset < 2).then_some(offset + 1)));
    }
    for spelling in [
        "9007199254740993",
        "900719925474099300e-2",
        "18446744073709551616",
    ] {
        let offset: Value = serde_json::from_str(spelling).unwrap();
        let page = call(
            &restarted,
            "list_ready_drafts",
            scoped(json!({"offset":offset,"limit":1.0})),
        )
        .await;
        assert_eq!(page["items"], json!([]));
        assert_eq!(page["total"], 3);
        assert_eq!(page["nextOffset"], Value::Null);
    }
    assert_eq!(fs::read(&file).unwrap(), bytes);
    restarted.cancel().await.unwrap();
}

#[tokio::test]
async fn source_versions_and_official_clients() {
    tokio::join!(
        matrix(V1, false),
        matrix(V1, true),
        matrix(V2, false),
        matrix(V2, true)
    );
}

#[tokio::test]
async fn actual_watched_discovery_and_empty_status() {
    for modern in [false, true] {
        let (fixture, _, _) = Fixture::new();
        let client = fixture.client(modern).await;
        let caps = serde_json::to_value(&client.peer_info().unwrap().capabilities).unwrap();
        assert_eq!(
            caps,
            json!({"resources":{"subscribe":false,"listChanged":false},"tools":{"listChanged":false}})
        );
        let tools = client.list_tools(None).await.unwrap().tools;
        assert_eq!(tools.len(), 9);
        assert_eq!(tools[0].name, "get_training_status");
        assert_eq!(tools[1].name, "list_ready_drafts");
        // Independently captured from the previous runtime. Sorting object keys
        // preserves schema array order, descriptions, annotations and field presence.
        let frozen = [
            "cd7a833fedd5ddbc4525a7335d6698cc93d677583589d01b2060d39dc9a4e420",
            "fc88df6356ace461379c58897c49263154f8abe74ddd1dec27be641ef47da2a7",
            "1f83828febee335051471fbfa6f8922e4faa5581beb18b9e5785d9458d5e7761",
            "9c7c0c067810260b02685a795e566bb4ef239b4174f77e29a3f9385aff5cc3f5",
            "57d5753b23b8835e4a7372d869322d6aa66144f17836966b309b3b5e432f41f8",
            "37912780959ddbdecb8f4875d7254d27aeb26bc76e1749c47ec0bb7a4ca7b08c",
            "75dd8e92d652b03734e6b991490a2660050a92b51677db4e0952a1d20f3a08b4",
            "0be29604fd748979eb9f22202a6d2bbda5c154e738546c878b713a5dcc827fdb",
            "c64fe710494c9e0fba067bc85ee3f9fe49c0e0739f96c88f42639db4b2ac0cf3",
        ];
        for (tool, hash) in tools.iter().zip(frozen) {
            let mut object = serde_json::to_value(tool).unwrap();
            object.sort_all_objects();
            assert_eq!(
                format!("{:x}", Sha256::digest(serde_json::to_vec(&object).unwrap())),
                hash,
                "{}",
                tool.name
            );
        }
        let result = client
            .call_tool(CallToolRequestParams::new("get_training_status"))
            .await
            .unwrap();
        let status = result.structured_content.unwrap();
        assert_eq!(status["status"], "awaiting_training");
        assert_eq!(status["sourceSha256"], json!(null));
        assert!(
            client
                .list_resources(None)
                .await
                .unwrap()
                .resources
                .is_empty()
        );
        let templates = client
            .list_resource_templates(None)
            .await
            .unwrap()
            .resource_templates;
        assert_eq!(
            templates[0].uri_template,
            "ironvellum-draft://{sourceSha256}/{draftId}"
        );
        client.cancel().await.unwrap();
    }
}
