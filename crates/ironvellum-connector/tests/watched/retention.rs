use super::*;
use ironvellum_local::{
    Install, LockMode, LocklessOpen, PrivateFolder, name, status::StatusReader,
};
use std::time::Instant;

fn replacement(sequence: u64) -> Vec<u8> {
    let mut doc: Value = serde_json::from_slice(V2).unwrap();
    doc["snapshotId"] = json!(format!("00000000-0000-4000-8000-{sequence:012x}"));
    doc["exportedAtMs"] = json!(doc["exportedAtMs"].as_u64().unwrap() + sequence);
    serde_json::to_vec(&doc).unwrap()
}

#[tokio::test]
async fn retained_cursor_draft_and_read_expire_on_fifth_adoption() {
    let (fixture, mut settings, path) = Fixture::new();
    let initial = Snapshot::from_bytes(V2).unwrap();
    settings.selected_dataset_id = Some(initial.dataset().into());
    save_settings(&path, &settings).unwrap();
    let training = settings.input_directory.as_path().join("training.json");
    fs::write(&training, V2).unwrap();
    let client = fixture.client(true).await;
    let first = until_status(&client, |s| s["sourceSha256"] == initial.sha256()).await;
    let token = &first["sourceSha256"];
    let workouts = call(&client, "list_workouts", json!({"sourceSha256":token})).await;
    let id = &workouts["workouts"][0]["id"];
    let mut page = json!({"sourceSha256":token,"id":id,"view":"sets","limit":1,"fields":["order"]});
    let p = call(&client, "get_workout", page.clone()).await;
    page["cursor"] = p["nextCursor"].clone();
    assert!(page["cursor"].is_string());
    let draft = serde_json::from_slice::<Value>(PROPOSAL).unwrap()["draft"].clone();
    let create = json!({"sourceSha256":token,"idempotencyKey":"old","draft":draft});
    let proposal = call(&client, "create_workout_draft", create.clone()).await["proposal"].clone();
    let listed = call(&client, "list_ready_drafts", json!({"sourceSha256":token})).await;
    let uri = listed["items"][0]["uri"].as_str().unwrap();
    let text = resource(&client, uri).await.unwrap()["contents"][0]["text"].clone();
    let mut reader = StatusReader::new(path.clone());
    for i in 2..=5 {
        let next = replacement(i);
        let new = Snapshot::from_bytes(&next).unwrap();
        fs::write(&training, next).unwrap();
        if i == 2 {
            // Detached diagnostics observe a newer available export; the live
            // connection continues serving the source actually adopted.
            let now = Instant::now();
            reader.read(now, 1).unwrap();
            let report = reader.read(now + POLL, 1).unwrap();
            assert_eq!(report.inventory.available.unwrap().sha256, new.sha256());
            for _ in 0..2 {
                tokio::time::sleep(POLL).await;
                assert_eq!(status(&client).await["sourceSha256"], *token);
            }
            settings.watch_enabled = true;
            save_settings(&path, &settings).unwrap();
        }
        until_status(&client, |s| s["sourceSha256"] == new.sha256()).await;
        assert_eq!(
            call(
                &client,
                "list_ready_drafts",
                json!({"sourceSha256":new.sha256()})
            )
            .await["total"],
            0
        );
        let continued = call(&client, "get_workout", page.clone()).await;
        let retry = call(&client, "create_workout_draft", create.clone()).await;
        if i < 5 {
            assert_eq!(continued["items"], json!([{"order":1}]));
            assert_eq!(retry["proposal"], proposal);
            assert_eq!(
                resource(&client, uri).await.unwrap()["contents"][0]["text"],
                text
            );
        } else {
            assert_eq!(continued["error"], "source_expired");
            assert_eq!(retry["error"], "source_expired");
            assert_eq!(resource(&client, uri).await.unwrap_err(), "source_expired");
        }
    }
    client.cancel().await.unwrap();
}

#[tokio::test]
async fn rejected_exports_and_reconfigured_endpoints_keep_pinned_outbox() {
    let (fixture, mut settings, path) = Fixture::new();
    let source = Snapshot::from_bytes(V1).unwrap();
    let token = source.sha256();
    settings.selected_dataset_id = Some(source.dataset().into());
    settings.watch_enabled = true;
    save_settings(&path, &settings).unwrap();
    let file = settings.input_directory.as_path().join("shared.json");
    fs::write(&file, V1).unwrap();
    let client = fixture.client(false).await;
    until_status(&client, |v| v["sourceSha256"] == token).await;
    let extra = settings.input_directory.as_path().join("writing.json");
    fs::write(&extra, b"{").unwrap();
    let rejected = until_status(&client, |s| s["refresh"]["errorCode"].is_string()).await;
    assert_eq!(rejected["sourceSha256"], token);
    assert!(
        rejected["guidance"]
            .as_str()
            .unwrap()
            .contains("rejected export")
    );
    fs::write(&extra, [V1, b"\n"].concat()).unwrap();
    until_status(&client, |s| {
        s["refresh"]["errorCode"] == "snapshot_conflict"
    })
    .await;
    fs::rename(&extra, fixture.0.join("retired")).unwrap();
    let old = settings.outbox_directory.clone();
    settings.outbox_directory = selected(&fixture.0.join("new out")).unwrap();
    save_settings(&path, &settings).unwrap();
    let reloaded = until_status(&client, |s| s["refresh"]["reloadRequired"] == true).await;
    assert_eq!(reloaded["sourceSha256"], token);
    assert_eq!(
        reloaded["guidance"],
        "Restart the connector after checking its folder settings."
    );
    // Remove the startup input endpoint itself, leaving existing open outbox
    // handles and already-adopted immutable snapshots available to this client.
    fs::rename(
        settings.input_directory.as_path(),
        fixture.0.join("previous input"),
    )
    .unwrap();
    until_status(&client, |s| s["refresh"]["errorCode"] == "reload_required").await;
    let fresh_listing = fs::read_dir(settings.outbox_directory.as_path())
        .unwrap()
        .count();
    let draft = serde_json::from_slice::<Value>(PROPOSAL).unwrap()["draft"].clone();
    let create = call(
        &client,
        "create_workout_draft",
        json!({"sourceSha256":token,"idempotencyKey":"after-move","draft":draft}),
    )
    .await;
    assert_eq!(create["status"], "proposed", "{create}");
    assert!(
        old.as_path()
            .join(create["filename"].as_str().unwrap())
            .is_file()
    );
    assert_eq!(
        fs::read_dir(settings.outbox_directory.as_path())
            .unwrap()
            .count(),
        fresh_listing
    );
    assert_eq!(
        fs::read(fixture.0.join("previous input/shared.json")).unwrap(),
        V1
    );
    client.cancel().await.unwrap();
}

#[tokio::test]
async fn native_server_replays_private_legacy_database_without_rewriting() {
    const IMAGE: &[u8] =
        include_bytes!("../../../ironvellum-local/tests/fixtures/legacy-journal/journal.sqlite");
    const RAW: &[u8] =
        include_bytes!("../../../ironvellum-local/tests/fixtures/legacy-journal/proposal.json");
    const DB: &str = ".ironvellum-proposals-v1.sqlite";
    let (fixture, mut settings, path) = Fixture::new();
    let s = Snapshot::from_bytes(V1).unwrap();
    settings.selected_dataset_id = Some(s.dataset().into());
    save_settings(&path, &settings).unwrap();
    fs::write(settings.input_directory.as_path().join("training.json"), V1).unwrap();
    let p: Value = serde_json::from_slice(RAW).unwrap();
    let id = p["draftId"].as_str().unwrap();
    let folder = PrivateFolder::open(&settings.outbox_directory, LocklessOpen::Existing).unwrap();
    folder
        .serialized(LockMode::Create)
        .unwrap()
        .write(&name(DB.as_ref()).unwrap(), IMAGE, Install::New)
        .unwrap();
    let client = fixture.client(false).await;
    until_status(&client, |v| v["sourceSha256"] == s.sha256()).await;
    let row = call(
        &client,
        "create_workout_draft",
        json!({"sourceSha256":s.sha256(),"idempotencyKey":"legacy-reference","draft":p["draft"]}),
    )
    .await;
    assert_eq!(row["proposal"], p);
    let uri = format!("ironvellum-draft://{}/{id}", s.sha256());
    assert_eq!(
        resource(&client, &uri).await.unwrap()["contents"][0]["text"]
            .as_str()
            .unwrap()
            .as_bytes(),
        RAW
    );
    assert_eq!(
        fs::read(settings.outbox_directory.as_path().join(DB)).unwrap(),
        IMAGE
    );
    tokio::join!(
        super::wire::verify_published_resource(&fixture, s.sha256(), &uri, RAW, false),
        super::wire::verify_published_resource(&fixture, s.sha256(), &uri, RAW, true)
    );
    let db = settings.outbox_directory.as_path().join(DB);
    let mut mismatch = IMAGE.to_vec();
    let offset = mismatch
        .windows(s.sha256().len())
        .position(|b| b == s.sha256().as_bytes())
        .unwrap();
    mismatch[offset] = if mismatch[offset] == b'0' { b'1' } else { b'0' };
    fs::write(&db, &mismatch).unwrap();
    assert_eq!(
        resource(&client, &uri).await.unwrap_err(),
        "journal_conflict"
    );
    assert_eq!(fs::read(&db).unwrap(), mismatch);
    let truncated = b"SQLite format 3\0incomplete";
    fs::write(&db, truncated).unwrap();
    assert_eq!(
        resource(&client, &uri).await.unwrap_err(),
        "storage_unavailable"
    );
    assert_eq!(fs::read(db).unwrap(), truncated);
    client.cancel().await.unwrap();
}
