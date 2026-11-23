use super::*;
use ironvellum_local::status::StatusReader;
use std::{
    collections::BTreeMap,
    path::Path,
    process::Stdio,
    time::{Instant, SystemTime, UNIX_EPOCH},
};
use tokio::{
    io::{AsyncBufReadExt, AsyncWriteExt, BufReader, Lines},
    process::{Child, ChildStdin, ChildStdout},
};

struct Wire {
    child: Child,
    input: ChildStdin,
    output: Lines<BufReader<ChildStdout>>,
    modern: bool,
}

fn meta() -> Value {
    json!({"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{},"io.modelcontextprotocol/clientInfo":{"name":"watched-test","version":"1"}})
}

#[tokio::test]
async fn oversized_fallback_and_failed_diagnostics_preserve_independent_evidence() {
    let (f, mut settings, path) = Fixture::new();
    let mut doc: Value = serde_json::from_slice(V2).unwrap();
    doc["workouts"][0]["sets"][0]["modifiers"] = json!("x".repeat(640_000));
    let bytes = serde_json::to_vec(&doc).unwrap();
    let source = Snapshot::from_bytes(&bytes).unwrap();
    settings.selected_dataset_id = Some(source.dataset().into());
    save_settings(&path, &settings).unwrap();
    fs::write(
        settings.input_directory.as_path().join("shared.json"),
        bytes,
    )
    .unwrap();
    let mut c = Wire::start(&f, true);
    c.handshake().await;
    let params = json!({"name":"get_workout","arguments":{"sourceSha256":source.sha256(),"id":doc["workouts"][0]["id"]}});
    let warm = c.request(json!(1), "tools/call", params.clone()).await;
    assert_eq!(warm["result"]["isError"], true);
    tokio::time::sleep(POLL).await;
    let fallback = c.request(json!("original"), "tools/call", params).await;
    assert_eq!(
        fallback["result"],
        json!({"isError":true,"content":[{"type":"text","text":"result_limit"}]})
    );
    let before = sessions(&path);
    assert_eq!(before[0]["initialized"], false);
    let directory = path
        .as_path()
        .parent()
        .unwrap()
        .join(ironvellum_local::rendezvous_name(&path).unwrap());
    let guard = directory.join(".ironvellum-lock-v1");
    fs::write(&guard, b"unavailable").unwrap();
    let tools = c.request(json!(2), "tools/list", json!({})).await;
    assert_eq!(tools["result"]["tools"].as_array().unwrap().len(), 9);
    let progress = c
        .request(
            json!(3),
            "tools/call",
            json!({"name":"get_progress","arguments":{"sourceSha256":source.sha256()}}),
        )
        .await;
    assert!(progress["result"]["structuredContent"]["progress"].is_object());
    let barrier = c
        .request(json!(4), "resources/read", json!({"uri":"invalid"}))
        .await;
    assert_eq!(barrier["error"]["code"], -32002);
    let held = sessions(&path);
    assert_eq!(held[0]["initialized"], false);
    assert_eq!(held[0]["toolsListed"], false);
    fs::write(guard, []).unwrap();
    let restored = observe(&path, |rows| {
        rows.iter()
            .any(|row| row["initialized"] == true && row["toolsListed"] == true)
    })
    .await;
    assert_eq!(restored[0]["statusChecked"], false);
    assert_eq!(restored[0]["loadedSource"]["sourceSha256"], source.sha256());
    c.close().await;
}

pub(super) async fn verify_published_resource(
    f: &Fixture,
    token: &str,
    uri: &str,
    bytes: &[u8],
    modern: bool,
) {
    let mut c = Wire::start(f, modern);
    c.handshake().await;
    for _ in 0..5 {
        let s = c
            .request(
                json!(1),
                "tools/call",
                json!({"name":"get_training_status","arguments":{}}),
            )
            .await;
        if s["result"]["structuredContent"]["sourceSha256"] == token {
            break;
        }
        tokio::time::sleep(POLL).await;
    }
    let r = c
        .request(json!("raw"), "resources/read", json!({"uri":uri}))
        .await;
    let mut expected = json!({"contents":[{"uri":uri,"text":std::str::from_utf8(bytes).unwrap(),"mimeType":"application/json"}]});
    if modern {
        expected["_meta"] = json!({"io.modelcontextprotocol/serverInfo":{"name":"ironvellum-personal","version":"1.1.0"}});
        expected["resultType"] = json!("complete");
        expected["ttlMs"] = json!(0);
        expected["cacheScope"] = json!("private");
    }
    assert_eq!(r["result"], expected);
    c.close().await;
}

#[tokio::test]
async fn malformed_resource_parameters_and_uri_grammar_are_distinct() {
    let (f, _, _) = Fixture::new();
    for modern in [false, true] {
        let mut c = Wire::start(&f, modern);
        c.handshake().await;
        for params in [json!({}), json!({"uri":null}), json!({"uri":17})] {
            let r = c
                .request(json!("bad-shape"), "resources/read", params)
                .await;
            assert_eq!(
                r["error"],
                json!({"code":-32602,"message":"invalid_request"})
            );
            assert!(
                c.request(json!("next"), "resources/list", json!({})).await["result"]["resources"]
                    .is_array()
            );
        }
        for uri in [
            "",
            "relative/path",
            "ironvellum-draft://%31/uuid",
            "ironvellum-draft://invalid/not-a-draft",
        ] {
            let r = c
                .request(json!("bad-uri"), "resources/read", json!({"uri":uri}))
                .await;
            assert_eq!(
                r["error"],
                json!({"code":-32002,"message":"invalid_resource"})
            );
        }
        c.close().await;
    }
}

#[tokio::test]
async fn watched_paging_budgets_original_wire_ids_and_full_envelope() {
    let (f, mut settings, path) = Fixture::new();
    let mut doc: Value = serde_json::from_slice(V2).unwrap();
    let id = doc["workouts"][0]["id"].clone();
    for (i, size) in [250_000, 250_000, 700_000, 1 << 20].into_iter().enumerate() {
        doc["workouts"][0]["sets"][i]["modifiers"] = json!("x".repeat(size));
    }
    let bytes = serde_json::to_vec(&doc).unwrap();
    let source = Snapshot::from_bytes(&bytes).unwrap();
    settings.selected_dataset_id = Some(source.dataset().into());
    save_settings(&path, &settings).unwrap();
    fs::write(settings.input_directory.as_path().join("big.json"), bytes).unwrap();
    let mut c = Wire::start(&f, true);
    c.handshake().await;
    let token = source.sha256();
    for _ in 0..5 {
        let r = c
            .request(
                json!(1),
                "tools/call",
                json!({"name":"get_training_status","arguments":{}}),
            )
            .await;
        if r["result"]["structuredContent"]["sourceSha256"] == token {
            break;
        }
        tokio::time::sleep(POLL).await;
    }
    for (rid, offset, count) in [
        (json!(8), 0, 2),
        (json!("a".repeat(60000)), 0, 1),
        (json!(9), 2, 1),
        (json!(10), 3, 0),
    ] {
        let r=c.request(rid,"tools/call",json!({"name":"get_workout","arguments":{"sourceSha256":token,"id":id,"view":"sets","limit":2,"offset":offset}})).await;
        if count == 0 {
            assert_eq!(r["result"]["content"][0]["text"], "result_limit");
        } else {
            assert_eq!(
                r["result"]["structuredContent"]["items"]
                    .as_array()
                    .unwrap()
                    .len(),
                count
            );
            if offset == 2 {
                assert_eq!(
                    r["result"]["content"][0]["text"],
                    ironvellum_contracts::pages::STRUCTURED_PAGE_TEXT
                );
            }
        }
    }
    c.close().await;
}

#[cfg(unix)]
#[tokio::test]
async fn interrupt_closes_owned_session_with_open_stdin() {
    let (f, _, path) = Fixture::new();
    let mut server = Wire::start(&f, true);
    server.handshake().await;
    observe(&path, |r| r.len() == 1).await;
    let pid = server.child.id().unwrap().try_into().unwrap();
    // SAFETY: this process spawned the live test child and still owns it.
    assert_eq!(unsafe { libc::kill(pid, libc::SIGINT) }, 0);
    assert_eq!(
        tokio::time::timeout(Duration::from_secs(5), server.child.wait())
            .await
            .unwrap()
            .unwrap()
            .code(),
        Some(2)
    );
    assert!(sessions(&path).is_empty());
}
impl Wire {
    fn start(f: &Fixture, modern: bool) -> Self {
        let mut child = f
            .command()
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .spawn()
            .unwrap();
        let input = child.stdin.take().unwrap();
        let output = BufReader::new(child.stdout.take().unwrap()).lines();
        Self {
            child,
            input,
            output,
            modern,
        }
    }
    async fn send(&mut self, value: &Value) {
        let mut raw = serde_json::to_vec(value).unwrap();
        raw.push(b'\n');
        self.input.write_all(&raw).await.unwrap();
    }
    async fn request(&mut self, id: Value, method: &str, mut params: Value) -> Value {
        if self.modern {
            params["_meta"] = meta();
        }
        self.send(&json!({"jsonrpc":"2.0","id":id,"method":method,"params":params}))
            .await;
        let line = tokio::time::timeout(Duration::from_secs(10), self.output.next_line())
            .await
            .unwrap()
            .unwrap()
            .unwrap();
        assert!(line.len() <= 1 << 20);
        let reply: Value = serde_json::from_str(&line).unwrap();
        assert_eq!(reply["id"], id);
        reply
    }
    async fn handshake(&mut self) {
        if self.modern {
            let r = self.request(json!(1), "server/discover", json!({})).await;
            assert!(r["result"].is_object(), "{r}");
        } else {
            let r=self.request(json!(1),"initialize",json!({"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"watched-test","version":"1"}})).await;
            assert_eq!(r["result"]["protocolVersion"], "2025-11-25");
            self.send(&json!({"jsonrpc":"2.0","method":"notifications/initialized"}))
                .await;
        }
    }
    async fn close(mut self) {
        drop(self.input);
        assert!(
            tokio::time::timeout(Duration::from_secs(5), self.child.wait())
                .await
                .unwrap()
                .unwrap()
                .success()
        );
    }
}

fn tree(root: &Path) -> BTreeMap<PathBuf, Option<Vec<u8>>> {
    let mut result = BTreeMap::new();
    for e in fs::read_dir(root).unwrap() {
        let p = e.unwrap().path();
        if p.is_dir() {
            result.insert(p.clone(), None);
            result.extend(tree(&p));
        } else {
            result.insert(p.clone(), Some(fs::read(p).unwrap()));
        }
    }
    result
}

fn sessions(path: &Selected) -> Vec<Value> {
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_millis() as u64;
    StatusReader::new(path.clone())
        .read(Instant::now(), now)
        .unwrap()
        .sessions
        .iter()
        .map(|s| s.wire())
        .collect()
}

async fn observe(path: &Selected, predicate: impl Fn(&[Value]) -> bool) -> Vec<Value> {
    tokio::time::timeout(Duration::from_secs(6), async {
        loop {
            let rows = sessions(path);
            if predicate(&rows) {
                return rows;
            }
            tokio::time::sleep(Duration::from_millis(30)).await;
        }
    })
    .await
    .expect("diagnostic publication")
}

#[tokio::test]
async fn independent_sessions_post_flush_evidence_and_clean_eof() {
    let (f, settings, path) = Fixture::new();
    let mut old = Wire::start(&f, false);
    let mut new = Wire::start(&f, true);
    observe(&path, |r| r.len() == 2).await;
    new.handshake().await;
    let r = new
        .request(
            json!(2),
            "tools/call",
            json!({"name":"get_progress","arguments":{}}),
        )
        .await;
    assert_eq!(r["result"]["isError"], true);
    let r = new
        .request(json!(3), "resources/read", json!({"uri":"invalid"}))
        .await;
    assert_eq!(
        r["error"],
        json!({"code":-32002,"message":"invalid_resource"})
    );
    fs::write(settings.input_directory.as_path().join("training.json"), V1).unwrap();
    tokio::time::sleep(Duration::from_millis(3200)).await;
    let rows = sessions(&path);
    assert!(rows.iter().all(|s| s["initialized"] == false
        && s["toolsListed"] == false
        && s["statusChecked"] == false
        && s["loadedSource"].is_null()));
    let before = tree(&f.0);
    assert_eq!(sessions(&path), rows);
    assert_eq!(before, tree(&f.0));
    old.handshake().await;
    let rows = observe(&path, |rows| rows.iter().any(|s| s["initialized"] == true)).await;
    let legacy = rows.iter().find(|s| s["initialized"] == true).unwrap()["sessionId"].clone();
    let status = new
        .request(
            json!(4),
            "tools/call",
            json!({"name":"get_training_status","arguments":{}}),
        )
        .await;
    assert_eq!(status["result"]["isError"], false);
    let rows = observe(&path, |r| r.iter().any(|s| s["statusChecked"] == true)).await;
    let m = rows.iter().find(|s| s["sessionId"] != legacy).unwrap();
    assert_eq!(m["initialized"], true);
    assert_eq!(m["toolsListed"], false);
    assert_eq!(m["clientVerified"], false);
    for session in [&mut new, &mut old] {
        for (method, field, expected) in [
            ("resources/list", "resources", json!([])),
            (
                "resources/templates/list",
                "resourceTemplates",
                json!([{"uriTemplate":"ironvellum-draft://{sourceSha256}/{draftId}","name":"Ironvellum workout draft","description":"Get a concrete URI from list_ready_drafts.","mimeType":"application/json"}]),
            ),
        ] {
            let r = session
                .request(json!("original id"), method, json!({}))
                .await;
            let mut expect = json!({field:expected});
            if session.modern {
                expect["_meta"] = json!({"io.modelcontextprotocol/serverInfo":{"name":"ironvellum-personal","version":"1.1.0"}});
                expect["ttlMs"] = json!(0);
                expect["cacheScope"] = json!("private");
                expect["resultType"] = json!("complete");
            }
            assert_eq!(r["result"], expect);
        }
    }
    new.request(
        serde_json::from_str("9223372036854775808").unwrap(),
        "tools/list",
        json!({}),
    )
    .await;
    observe(&path, |r| r.iter().any(|s| s["clientVerified"] == true)).await;
    old.close().await;
    let rows = observe(&path, |r| r.len() == 1).await;
    assert_ne!(rows[0]["sessionId"], legacy);
    assert_eq!(rows[0]["clientVerified"], true);
    new.close().await;
    assert!(sessions(&path).is_empty());
}
