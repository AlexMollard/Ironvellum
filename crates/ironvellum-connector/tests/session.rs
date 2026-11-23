use ironvellum_connector::{BoundedTransport, OriginalRequest};
use rmcp::{
    ErrorData, RoleServer, ServerHandler, ServiceExt,
    model::*,
    service::{ClientLifecycleMode, ClientServiceExt, RequestContext},
    transport::{TokioChildProcess, stdio_pipes},
};
use serde_json::{Value, json};
use std::{env, process::Stdio, time::Duration};
use tokio::{
    io::{AsyncBufReadExt, AsyncReadExt, AsyncWriteExt, BufReader},
    process::{Child, Command},
    time::timeout,
};

struct Fixture;
impl ServerHandler for Fixture {
    fn get_info(&self) -> ServerConfig {
        ServerConfig::new(ServerCapabilities::builder().enable_tools().build())
            .with_server_info(Implementation::new("fixture", "1"))
    }
    async fn call_tool(
        &self,
        params: CallToolRequestParams,
        ctx: RequestContext<RoleServer>,
    ) -> Result<CallToolResponse, ErrorData> {
        let original = ctx.extensions.get::<OriginalRequest>().unwrap();
        let mut value = original
            .arguments
            .clone()
            .unwrap_or_else(|| json!({"missing":true}));
        if params.name == "original_id" {
            value = json!({});
            value["budgetId"] = original.id.clone();
        } else if params.name == "argument_types" {
            let projected = original.tool_arguments().unwrap();
            let lexical_integer = |v: &Value| {
                v.as_number()
                    .is_some_and(ironvellum_contracts::wire::lexical_integer)
            };
            value = json!({"originalInteger":lexical_integer(&value),"toolInteger":lexical_integer(&projected),"toolValue":projected});
        } else if params.name == "cancel_me" {
            timeout(Duration::from_secs(2), ctx.ct.cancelled())
                .await
                .unwrap();
            return Err(ErrorData::invalid_request(
                "private cancellation details",
                None,
            ));
        } else if params.name == "resource_error" {
            return Err(ErrorData::new(
                ErrorCode(-32002),
                value["message"].as_str().unwrap().to_owned(),
                None,
            ));
        } else if params.name == "size" {
            let n = value["size"].as_u64().unwrap() as usize;
            let view = value["view"].as_bool().unwrap();
            value = json!({"body": format!("🦊\"{}", "a".repeat(n))});
            if view {
                value["view"] = "fixture".into();
            }
        } else if params.name == "zero" {
            value = json!({});
            value["value"] = ironvellum_contracts::wire::binary64(0.0).unwrap();
        } else if params.name == "echo_arg" {
            let mut wrapper = json!({});
            wrapper["arg"] = value;
            value = wrapper;
        }
        let text = String::from_utf8(ironvellum_contracts::encode(&value).unwrap()).unwrap();
        let mut result = CallToolResult::success(vec![ContentBlock::text(text)]);
        result.structured_content = Some(value);
        Ok(CallToolResponse::Complete(result))
    }
}

const INIT: &str = r#"{"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"fixture","version":"1"}}}"#;
const READY: &str = r#"{"jsonrpc":"2.0","method":"notifications/initialized"}"#;
fn modern_meta() -> Value {
    json!({"io.modelcontextprotocol/protocolVersion":"2026-07-28",
        "io.modelcontextprotocol/clientCapabilities":{},
        "io.modelcontextprotocol/clientInfo":{"name":"fixture","version":"1"}})
}

async fn raw_process(bytes: Vec<u8>, open: bool) -> (Vec<String>, Vec<u8>) {
    raw_mode(bytes, open, false).await
}

fn raw_fixture(folder: bool) -> Child {
    Command::new(env::current_exe().unwrap())
        .arg(if folder {
            "--folder-fixture"
        } else {
            "--fixture"
        })
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .kill_on_drop(true)
        .spawn()
        .unwrap()
}

async fn raw_mode(bytes: Vec<u8>, open: bool, folder: bool) -> (Vec<String>, Vec<u8>) {
    let mut child = raw_fixture(folder);
    let mut input = child.stdin.take().unwrap();
    let writer = async move {
        input.write_all(&bytes).await.unwrap();
        if open { Some(input) } else { None }
    };
    let (_hold, output) = tokio::join!(writer, child.wait_with_output());
    let output = output.unwrap();
    assert!(output.status.success(), "{:?}", output.stderr);
    (
        String::from_utf8(output.stdout)
            .unwrap()
            .lines()
            .map(str::to_owned)
            .collect(),
        output.stderr,
    )
}

fn lookup<'a>(lines: &'a [String], id: &Value) -> &'a str {
    lines
        .iter()
        .find(|line| {
            ironvellum_contracts::loads(line.as_bytes(), 1 << 20, 32).unwrap()["id"] == *id
        })
        .unwrap()
}

async fn raw_boundaries() {
    let list = r#"{"jsonrpc":"2.0","id":1,"method":"tools/list"}"#;
    let mut bytes = format!("{INIT}\n{READY}\n{list}\n").into_bytes();
    bytes.extend(vec![b' '; (64 << 10) + 1]);
    let (lines, diagnostic) = raw_process(bytes, true).await;
    assert_eq!(diagnostic, b"invalid_frame\n");
    assert_eq!(lines.len(), 2);
    assert!(lookup(&lines, &1.into()).contains("\"tools\":[]"));
    for malformed in [
        b"\xff\n".as_slice(),
        br#"{"a":0,"\u0061":1}"#,
        b"1e9999",
        b"1e-9999",
        b"[[[[[[[[[[[[[[[[[0]]]]]]]]]]]]]]]]]",
    ] {
        let (lines, diagnostic) = raw_process(malformed.to_vec(), false).await;
        assert!(lines.is_empty());
        assert_eq!(diagnostic, b"invalid_frame\n");
    }
    let mut exact = list.as_bytes().to_vec();
    exact.resize((64 << 10) - 1, b' ');
    exact.push(b'\n');
    let (lines, diagnostic) = raw_process(exact, false).await;
    assert_eq!(lines.len(), 1);
    assert!(diagnostic.is_empty());
    let num = "18446744073709551617";
    let arg = r#"{"$serde_json::private::Number":"ordinary","wire":"user","n":1234567890123456789.12345678901}"#;
    let mut frames = vec![
        INIT.to_owned(),
        READY.to_owned(),
        r#"{"jsonrpc":"2.0","id":5,"method":42,"params":{"secret":"private-string"}}"#.into(),
    ];
    for (id, argument) in [
        (num.to_owned(), arg),
        (format!("\"{num}\""), "null"),
        ("2".into(), "[1,2]"),
    ] {
        frames.push(format!(r#"{{"jsonrpc":"2.0","id":{id},"method":"tools/call","params":{{"name":"echo_arg","arguments":{argument}}}}}"#));
    }
    frames
        .push(r#"{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"zero"}}"#.into());
    let (lines, err) = raw_process(frames.join("\n").into_bytes(), false).await;
    assert!(err.is_empty());
    assert_eq!(lines.len(), 5);
    let response = lookup(
        &lines,
        &ironvellum_contracts::loads(num.as_bytes(), 128, 1).unwrap(),
    );
    assert!(response.contains(arg), "{response}");
    assert!(lookup(&lines, &Value::from(num)).contains(r#""arg":null"#));
    assert!(lookup(&lines, &2.into()).contains(r#""arg":[1,2]"#));
    assert!(lookup(&lines, &3.into()).contains(r#"value\":0.0"#));
    assert!(lookup(&lines, &3.into()).contains(r#""value":0}"#));
    assert!(!lines.join("").contains("private-string"));
    assert!(!lines.iter().any(|line| line.contains(r#""id":5,"#)));
    let ping = r#"{"jsonrpc":"2.0","id":"pre-init-ping","method":"ping"}"#;
    let invalid = r#"{"jsonrpc":"2.0","id":5,"method":42}"#;
    for prefix in [String::new(), format!("{INIT}\n{READY}\n")] {
        let (actual, err) = raw_process(
            format!("{prefix}{invalid}\nnull\n{ping}").into_bytes(),
            false,
        )
        .await;
        assert!(err.is_empty());
        assert_eq!(actual.len(), if prefix.is_empty() { 1 } else { 2 });
        assert_eq!(
            lookup(&actual, &"pre-init-ping".into()),
            r#"{"jsonrpc":"2.0","id":"pre-init-ping","result":{}}"#
        );
    }
}

async fn argument_types() {
    let mut call_ids = vec![INIT.to_owned(), READY.to_owned()];
    for id in ["1.0", "1e0", "1e128"] {
        call_ids.push(format!(
            r#"{{"jsonrpc":"2.0","id":{id},"method":"tools/call","params":{{"name":"original_id"}}}}"#
        ));
    }
    let (lines, err) = raw_process(call_ids.join("\n").into_bytes(), false).await;
    assert!(err.is_empty());
    assert_eq!(lines.len(), 2);
    assert!(lookup(&lines, &1.into()).contains(r#""id":1,"#));
    assert!(lookup(&lines, &1.into()).contains(r#""budgetId":1}"#));
    let mut invalid_ids = vec![INIT.to_owned(), READY.to_owned()];
    for id in ["1.0", "1e0", "true", "null", "[]", "{}", "1.5"] {
        invalid_ids.push(format!(r#"{{"jsonrpc":"2.0","id":{id},"method":"ping"}}"#));
    }
    let (lines, err) = raw_process(invalid_ids.join("\n").into_bytes(), false).await;
    assert!(err.is_empty());
    assert_eq!(lines.len(), 1);
    for (token, raw_int, tool_int) in [
        ("1", true, true),
        ("0.0", false, true),
        ("1e0", false, true),
        ("1.0", false, false),
        (
            "1.00000000000000000000000000000000000000000000000000001",
            false,
            false,
        ),
        ("5e-324", false, false),
    ] {
        let call = format!(
            r#"{{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{{"name":"argument_types","arguments":{token}}}}}"#
        );
        let (lines, err) =
            raw_process(format!("{INIT}\n{READY}\n{call}").into_bytes(), false).await;
        assert!(err.is_empty());
        let result =
            ironvellum_contracts::loads(lookup(&lines, &1.into()).as_bytes(), 2048, 16).unwrap();
        let data = &result["result"]["structuredContent"];
        assert_eq!(data["originalInteger"], raw_int);
        assert_eq!(data["toolInteger"], tool_int);
    }
}

async fn limits(modern: bool, size: usize, view: bool, pad: usize) -> String {
    let mut frames = if modern {
        vec![]
    } else {
        vec![INIT.to_owned(), READY.to_owned()]
    };
    let mut params = json!({"name":"size","arguments":{"view":view,"size":size}});
    if modern {
        params["_meta"] = modern_meta();
    }
    let id = format!("🦊\"{}", "a".repeat(60_000 + pad));
    frames.push(json!({"jsonrpc":"2.0","id":id,"method":"tools/call","params":params}).to_string());
    let mut params = json!({});
    if modern {
        params["_meta"] = modern_meta();
    }
    frames.push(json!({"jsonrpc":"2.0","id":7,"method":"tools/list","params":params}).to_string());
    let (lines, err) = raw_process(frames.join("\n").into_bytes(), false).await;
    assert!(err.is_empty());
    let listing: Value = serde_json::from_str(lookup(&lines, &7.into())).unwrap();
    assert_eq!(listing["result"]["tools"], json!([]));
    if modern {
        assert_eq!(listing["result"]["ttlMs"], 0);
        assert_eq!(listing["result"]["cacheScope"], "private");
        assert_eq!(listing["result"]["resultType"], "complete");
        assert_eq!(
            listing["result"]["_meta"]["io.modelcontextprotocol/serverInfo"],
            json!({"name":"fixture","version":"1"})
        );
    } else {
        assert_eq!(listing["result"], json!({"tools":[]}));
    }
    lookup(&lines, &id.into()).to_owned()
}

async fn size_boundaries() {
    const CAP: usize = 1 << 20;
    for modern in [false, true] {
        let base = limits(modern, 0, false, 0).await;
        assert_eq!(base.contains("io.modelcontextprotocol/serverInfo"), modern);
        assert_eq!(base.contains("resultType"), modern);
        let result: Value = serde_json::from_str(&base).unwrap();
        assert!(result["result"].get("ttlMs").is_none());
        assert!(result["result"].get("cacheScope").is_none());
        let exact = (CAP - base.len()) / 2;
        let pad = (CAP - base.len()) % 2;
        assert_eq!(limits(modern, exact, false, pad).await.len(), CAP);
        let refused = limits(modern, exact, false, pad + 1).await;
        let projected: Value = serde_json::from_str(&refused).unwrap();
        assert_eq!(
            projected["result"],
            json!({"isError":true,"content":[{"type":"text","text":"result_limit"}]})
        );
        let hint = limits(modern, exact + 100, true, 0).await;
        assert!(hint.contains("Read this page in structuredContent."));
        assert!(hint.len() <= CAP);
    }
}

async fn lifecycle_and_cancellation() {
    let bare = r#"{"jsonrpc":"2.0","id":"discover","method":"server/discover"}"#;
    let inline_list = json!({"jsonrpc":"2.0","id":"modern-list","method":"tools/list","params":{"_meta":modern_meta()}});
    let (mixed, err) = raw_process(format!("{bare}\n{inline_list}").into_bytes(), false).await;
    assert!(err.is_empty());
    for (id, code) in [("discover", -32601), ("modern-list", -32600)] {
        let v: Value = serde_json::from_str(lookup(&mixed, &id.into())).unwrap();
        assert_eq!(v["error"], json!({"code":code,"message":"invalid_request"}));
    }
    let methods = ["tools/list", "tools/call", "server/discover", "ping"];
    let mut mixed_frames = vec![bare.to_owned(), INIT.into(), READY.into()];
    for method in methods {
        let mut params = json!({"_meta":modern_meta()});
        if method == "tools/call" {
            params["name"] = "echo".into();
            params["arguments"] = json!({});
        }
        mixed_frames
            .push(json!({"jsonrpc":"2.0","id":method,"method":method,"params":params}).to_string());
    }
    mixed_frames.push(r#"{"jsonrpc":"2.0","id":"last","method":"ping"}"#.into());
    let (mixed, err) = raw_process(mixed_frames.join("\n").into_bytes(), false).await;
    assert!(err.is_empty());
    assert_eq!(mixed.len(), 7);
    for method in methods {
        let refused: Value = serde_json::from_str(lookup(&mixed, &method.into())).unwrap();
        assert_eq!(
            refused["error"],
            json!({"code":-32600,"message":"invalid_request"}),
            "{method}"
        );
    }
    assert_eq!(
        lookup(&mixed, &"last".into()),
        r#"{"jsonrpc":"2.0","id":"last","result":{}}"#
    );
    let discover = json!({"jsonrpc":"2.0","id":90,"method":"server/discover","params":{"_meta":modern_meta()}}).to_string();
    let pre_ping = r#"{"jsonrpc":"2.0","id":91,"method":"ping"}"#;
    let bad_meta = r#"{"jsonrpc":"2.0","id":92,"method":"tools/list","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28"}}}"#;
    let call = r#"{"jsonrpc":"2.0","id":18446744073709551617,"method":"tools/call","params":{"name":"cancel_me"}}"#;
    let cancel = r#"{"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":18446744073709551617,"reason":"private"}}"#;
    let list = r#"{"jsonrpc":"2.0","id":93,"method":"tools/list"}"#;
    let frames = [bare, pre_ping, bad_meta, INIT, READY, call, cancel, list];
    let (lines, err) = raw_process(frames.join("\n").into_bytes(), false).await;
    assert!(err.is_empty());
    assert_eq!(lines.len(), 5);
    assert!(lookup(&lines, &"discover".into()).contains("invalid_request"));
    assert!(lookup(&lines, &91.into()).contains("result"));
    assert!(lookup(&lines, &92.into()).contains("invalid_request"));
    assert!(!lookup(&lines, &93.into()).contains("serverInfo"));
    assert!(!lines.join("").contains("private cancellation"));
    let modern_ping =
        json!({"jsonrpc":"2.0","id":94,"method":"ping","params":{"_meta":modern_meta()}})
            .to_string();
    let (lines, err) = raw_process(
        [discover.as_str(), INIT, READY, pre_ping, &modern_ping]
            .join("\n")
            .into_bytes(),
        false,
    )
    .await;
    assert!(err.is_empty());
    assert!(lookup(&lines, &90.into()).contains("serverInfo"));
    for (id, code) in [(0, -32022), (91, -32602), (94, -32601)] {
        let v: Value = serde_json::from_str(lookup(&lines, &id.into())).unwrap();
        assert_eq!(v["error"], json!({"code":code,"message":"invalid_request"}));
    }
    let mut modern_call: Value = serde_json::from_str(call).unwrap();
    modern_call["params"]["_meta"] = modern_meta();
    let (lines, err) = raw_process(
        format!("{discover}\n{modern_call}\n{cancel}").into_bytes(),
        false,
    )
    .await;
    assert!(err.is_empty());
    assert_eq!(lines.len(), 1);
    let valid_list =
        json!({"jsonrpc":"2.0","id":95,"method":"tools/list","params":{"_meta":modern_meta()}})
            .to_string();
    let (lines, err) = raw_process(
        [discover.as_str(), &valid_list, bad_meta, &valid_list]
            .join("\n")
            .into_bytes(),
        false,
    )
    .await;
    assert!(err.is_empty());
    assert!(lookup(&lines, &92.into()).contains("invalid_request"));
}

async fn cancellation_at_capacity() {
    const CAPACITY: u32 = 32;
    const OVERLOAD_ID: &str = "overload";
    const RECOVERY_ID: &str = "recovered";

    let mut child = raw_fixture(false);
    let mut input = child.stdin.take().unwrap();
    let mut output = BufReader::new(child.stdout.take().unwrap());
    let mut diagnostics = child.stderr.take().unwrap();
    let exchange = async {
        input
            .write_all(format!("{INIT}\n{READY}\n").as_bytes())
            .await?;
        let mut line = String::new();
        (&mut output).take(2048).read_line(&mut line).await?;
        let initialized: Value = serde_json::from_str(&line)?;
        if initialized["id"] != 0 || initialized["result"]["protocolVersion"] != "2025-11-25" {
            return Err(format!("unexpected initialization reply: {initialized}").into());
        }

        for id in 1..=CAPACITY {
            let call = json!({"jsonrpc":"2.0","id":id,"method":"tools/call","params":{"name":"cancel_me"}});
            input.write_all(format!("{call}\n").as_bytes()).await?;
        }
        let call = json!({"jsonrpc":"2.0","id":OVERLOAD_ID,"method":"tools/call","params":{"name":"original_id"}});
        input.write_all(format!("{call}\n").as_bytes()).await?;
        line.clear();
        (&mut output).take(2048).read_line(&mut line).await?;
        let overloaded: Value = serde_json::from_str(&line)?;
        if overloaded
            != json!({"jsonrpc":"2.0","id":OVERLOAD_ID,"error":{"code":-32603,"message":"invalid_request"}})
        {
            return Err(format!("unexpected overload reply: {overloaded}").into());
        }

        for id in 1..=CAPACITY {
            let cancel = json!({"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":id}});
            input.write_all(format!("{cancel}\n").as_bytes()).await?;
        }
        let call = json!({"jsonrpc":"2.0","id":RECOVERY_ID,"method":"tools/call","params":{"name":"original_id"}});
        input.write_all(format!("{call}\n").as_bytes()).await?;
        line.clear();
        (&mut output).take(2048).read_line(&mut line).await?;
        let recovered: Value = serde_json::from_str(&line)?;
        if recovered["id"] != RECOVERY_ID
            || recovered["result"]["structuredContent"] != json!({"budgetId":RECOVERY_ID})
        {
            return Err(format!("unexpected recovery reply: {recovered}").into());
        }

        drop(input);
        let status = child.wait().await?;
        let mut stderr = Vec::new();
        (&mut diagnostics)
            .take(2048)
            .read_to_end(&mut stderr)
            .await?;
        Ok::<_, Box<dyn std::error::Error>>((status, stderr))
    };

    let (status, stderr) = match timeout(Duration::from_secs(5), exchange).await {
        Ok(Ok(result)) => result,
        failure => {
            let cleanup = child.kill().await;
            panic!("capacity exchange failed: {failure:?}; child cleanup: {cleanup:?}");
        }
    };
    assert!(status.success(), "fixture failed: {status}, {stderr:?}");
    assert!(
        stderr.is_empty(),
        "unexpected fixture diagnostics: {stderr:?}"
    );
}

async fn resource_redaction() {
    for folder in [false, true] {
        let mut frames = vec![INIT.to_owned(), READY.to_owned()];
        let messages = [
            "invalid_resource",
            "not_found",
            "source_expired",
            "outbox_conflict",
            "journal_conflict",
            "storage_unavailable",
            "private-file",
        ];
        for (idx, message) in messages.iter().enumerate() {
            frames.push(json!({"jsonrpc":"2.0","id":idx+1,"method":"tools/call","params":{"name":"resource_error","arguments":{"message":message}}}).to_string());
        }
        let (lines, err) = raw_mode(frames.join("\n").into_bytes(), false, folder).await;
        assert!(err.is_empty());
        for (idx, message) in messages.iter().enumerate() {
            let value: Value = serde_json::from_str(lookup(&lines, &(idx + 1).into())).unwrap();
            assert_eq!(
                value["error"],
                json!({"code":-32002,"message": if folder && idx<6 {message} else {&"invalid_request"}})
            );
        }
    }
}

async fn fixture(folder: bool) {
    let (input, output) = stdio_pipes();
    let transport = BoundedTransport::new(
        input,
        output,
        tokio::io::stderr(),
        json!({"name":"fixture","version":"1"}),
        folder,
        |_| {},
    );
    if let Ok(service) = Fixture.serve(transport).await {
        let _ = service.waiting().await;
    }
}

async fn client(modern: bool) {
    let mut cmd = Command::new(env::current_exe().unwrap());
    cmd.arg("--stdio-fixture");
    let transport = TokioChildProcess::new(cmd).unwrap();
    let mut config = ClientConfig::default();
    config.protocol_version = ProtocolVersion::V_2025_11_25;
    let mode = if modern {
        ClientLifecycleMode::Discover {
            preferred_versions: vec![ProtocolVersion::V_2026_07_28],
        }
    } else {
        ClientLifecycleMode::Initialize
    };
    let service = config.serve_with_lifecycle(transport, mode).await.unwrap();
    let info = service.peer_info().unwrap();
    assert_eq!(
        info.protocol_version,
        if modern {
            ProtocolVersion::V_2026_07_28
        } else {
            ProtocolVersion::V_2025_11_25
        }
    );
    let _ = service.list_tools(None).await.unwrap();
    let result = service
        .call_tool(CallToolRequestParams::new("echo"))
        .await
        .unwrap();
    assert_eq!(
        result
            .structured_content
            .as_ref()
            .and_then(|v| v.get("missing")),
        Some(&Value::Bool(true))
    );
    service.cancel().await.unwrap();
}

struct FlushFailure;
impl tokio::io::AsyncWrite for FlushFailure {
    fn poll_write(
        self: std::pin::Pin<&mut Self>,
        _: &mut std::task::Context<'_>,
        buf: &[u8],
    ) -> std::task::Poll<std::io::Result<usize>> {
        std::task::Poll::Ready(Ok(buf.len()))
    }
    fn poll_flush(
        self: std::pin::Pin<&mut Self>,
        _: &mut std::task::Context<'_>,
    ) -> std::task::Poll<std::io::Result<()>> {
        std::task::Poll::Ready(Err(std::io::ErrorKind::BrokenPipe.into()))
    }
    fn poll_shutdown(
        self: std::pin::Pin<&mut Self>,
        cx: &mut std::task::Context<'_>,
    ) -> std::task::Poll<std::io::Result<()>> {
        self.poll_flush(cx)
    }
}

async fn receipts() {
    use std::sync::{Arc, Mutex};
    let failed = BoundedTransport::new(
        INIT.as_bytes(),
        FlushFailure,
        tokio::io::sink(),
        json!({}),
        false,
        |_| panic!("receipt before successful flush"),
    );
    assert!(Fixture.serve(failed).await.is_err());
    for modern in [false, true] {
        let events = Arc::new(Mutex::new(Vec::new()));
        let log = events.clone();
        let mut frames = Vec::new();
        let mut discovery = json!({"jsonrpc":"2.0","id":99,"method":"server/discover"});
        if modern {
            discovery["params"] = json!({"_meta":modern_meta()});
        }
        frames.push(discovery.to_string());
        frames.push(r#"{"jsonrpc":"2.0","id":98,"method":"tools/list","params":{}}"#.into());
        if !modern {
            frames.push(INIT.into());
            frames.push(READY.into());
            frames.push(json!({"jsonrpc":"2.0","id":22,"method":"tools/list","params":{"_meta":modern_meta()}}).to_string());
        }
        let mut params = json!({});
        if modern {
            params["_meta"] = modern_meta();
        }
        frames.push(
            json!({"jsonrpc":"2.0","id":20,"method":"tools/list","params":params}).to_string(),
        );
        params["name"] = "get_training_status".into();
        frames.push(format!(
            r#"{{"jsonrpc":"2.0","id":21e0,"method":"tools/call","params":{params}}}"#
        ));
        let transport = BoundedTransport::new(
            std::io::Cursor::new(frames.join("\n").into_bytes()),
            tokio::io::sink(),
            tokio::io::sink(),
            json!({}),
            false,
            move |event| log.lock().unwrap().push(event),
        );
        Fixture
            .serve(transport)
            .await
            .unwrap()
            .waiting()
            .await
            .unwrap();
        let events = events.lock().unwrap();
        assert_eq!(events.len(), if modern { 2 } else { 3 });
        assert!(events.iter().all(|e| e.modern == modern));
        assert!(
            events
                .iter()
                .any(|e| e.method == "tools/list" && e.id == 20)
        );
        assert!(
            events
                .iter()
                .any(|e| e.tool.as_deref() == Some("get_training_status") && e.id == 21)
        );
    }
}

fn main() {
    let arg = env::args().nth(1);
    if arg.as_deref() == Some("--stdio-panic") {
        ironvellum_connector::run_protocol_stdio(|_, _, _| async {
            let (started, ready) = tokio::sync::oneshot::channel();
            tokio::task::spawn_blocking(move || {
                use std::io::Read;
                let mut input = std::io::stdin().lock();
                let mut byte = [0];
                input.read_exact(&mut byte).unwrap();
                started.send(()).unwrap();
                let _ = input.read(&mut byte);
            });
            ready.await.unwrap();
            panic!("stdio-panic-canary");
        })
        .unwrap();
        return;
    }
    if arg.as_deref() == Some("--stdio-cleanup") {
        ironvellum_connector::run_protocol_stdio(|_, _, _| async {}).unwrap();
        assert!(ironvellum_connector::run_protocol_stdio(|_, _, _| async {}).is_err());
        println!("cleanup-canary");
        std::io::stdin().read_line(&mut String::new()).unwrap();
        return;
    }
    if arg.as_deref() == Some("--stdio-fixture") {
        ironvellum_connector::run_protocol_stdio(|input, output, diagnostics| async move {
            println!("native-stdout-canary");
            // SAFETY: puts reads a static, NUL-terminated C string.
            unsafe { puts(c"crt-stdout-canary".as_ptr()) };
            let transport = BoundedTransport::new(
                input,
                output,
                diagnostics,
                json!({"name":"fixture","version":"1"}),
                false,
                |_| {},
            );
            if let Ok(service) = Fixture.serve(transport).await {
                let _ = service.waiting().await;
            }
        })
        .unwrap();
        return;
    }
    tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .unwrap()
        .block_on(run(arg));
}

unsafe extern "C" {
    fn puts(message: *const std::ffi::c_char) -> std::ffi::c_int;
}

async fn stdio_process() {
    let mut child = Command::new(env::current_exe().unwrap())
        .arg("--stdio-panic")
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .kill_on_drop(true)
        .spawn()
        .unwrap();
    let mut held = child.stdin.take().unwrap();
    held.write_all(b"x").await.unwrap();
    let status = match timeout(Duration::from_secs(5), child.wait()).await {
        Ok(result) => result.unwrap(),
        Err(_) => {
            child.kill().await.unwrap();
            panic!("callback panic hung with stdin open");
        }
    };
    assert!(!status.success());
    let output = child.wait_with_output().await.unwrap();
    assert!(output.stdout.is_empty());
    assert!(
        String::from_utf8(output.stderr)
            .unwrap()
            .contains("stdio-panic-canary")
    );
    drop(held);

    let mut child = Command::new(env::current_exe().unwrap())
        .arg("--stdio-cleanup")
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .kill_on_drop(true)
        .spawn()
        .unwrap();
    let mut wire = Vec::new();
    timeout(
        Duration::from_secs(5),
        child.stdout.take().unwrap().read_to_end(&mut wire),
    )
    .await
    .unwrap()
    .unwrap();
    assert!(wire.is_empty());
    assert!(child.try_wait().unwrap().is_none());
    drop(child.stdin.take());
    let output = timeout(Duration::from_secs(5), child.wait_with_output())
        .await
        .unwrap()
        .unwrap();
    assert!(output.status.success());
    assert_eq!(
        String::from_utf8(output.stderr).unwrap().trim_end(),
        "cleanup-canary"
    );

    for invalid in [false, true] {
        let mut child = Command::new(env::current_exe().unwrap())
            .arg("--stdio-fixture")
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .kill_on_drop(true)
            .spawn()
            .unwrap();
        let mut input = child.stdin.take().unwrap();
        let list = r#"{"jsonrpc":"2.0","id":1,"method":"tools/list"}"#;
        input
            .write_all(format!("{INIT}\n{READY}\n{list}\n").as_bytes())
            .await
            .unwrap();
        let _held = if invalid {
            input.write_all(b"[invalid\n").await.unwrap();
            Some(input)
        } else {
            drop(input);
            None
        };
        let output = timeout(Duration::from_secs(5), child.wait_with_output())
            .await
            .unwrap()
            .unwrap();
        assert!(output.status.success(), "{:?}", output.stderr);
        let responses: Vec<Value> = std::str::from_utf8(&output.stdout)
            .unwrap()
            .lines()
            .map(|line| serde_json::from_str(line).unwrap())
            .collect();
        assert_eq!(responses.len(), 2);
        assert!(
            responses
                .iter()
                .any(|r| r["id"] == 1 && r["result"]["tools"] == json!([]))
        );
        let errors = String::from_utf8(output.stderr).unwrap();
        for marker in ["native-stdout-canary", "crt-stdout-canary"] {
            assert_eq!(errors.matches(marker).count(), 1);
        }
        assert_eq!(errors.contains("invalid_frame"), invalid);
    }
}

async fn run(arg: Option<String>) {
    if matches!(arg.as_deref(), Some("--fixture" | "--folder-fixture")) {
        fixture(arg.as_deref() == Some("--folder-fixture")).await;
        return;
    }
    timeout(Duration::from_secs(60), async {
        stdio_process().await;
        client(false).await;
        client(true).await;
        raw_boundaries().await;
        argument_types().await;
        size_boundaries().await;
        lifecycle_and_cancellation().await;
        cancellation_at_capacity().await;
        resource_redaction().await;
        receipts().await;
    })
    .await
    .unwrap();
}
