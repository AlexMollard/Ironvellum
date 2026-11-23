use crate::personal::{self, NAME, ResultCode, TOOLS, VERSION, now_ms, tool_result};
use ironvellum_connector::{OriginalRequest, WriteReceipt};
use ironvellum_contracts::{Value, schema, snapshot::Snapshot};
use ironvellum_local::{
    EndpointBinding, Journal, PrivateFolder, RefreshStatus, Selected, WatchedSource, load_settings,
    status::{Evidence, Heartbeat},
};
use rmcp::{ErrorData, RoleServer, ServerHandler, model::*, service::RequestContext};
use serde_json::json;
use std::{
    sync::{Arc, LazyLock, Mutex},
    time::Instant,
};

const STATUS: &str = "get_training_status";
const DRAFTS: &str = "list_ready_drafts";
const SOURCE: &str = "sourceSha256";
const TOKEN_HELP: &str = " Required sourceSha256 comes from get_training_status. Reuse the same token for cursors and retries. An expired source returns source_expired.";
static MANIFEST: LazyLock<Value> = LazyLock::new(|| {
    schema::resolved_resource("tools-watch-v1.json").expect("embedded watched tools")
});

fn as_tool(name: &'static str, description: String, input: Value, output: Value) -> Tool {
    Tool::new(
        name,
        description,
        input.as_object().expect("embedded input schema").clone(),
    )
    .with_raw_output_schema(
        output
            .as_object()
            .expect("embedded output schema")
            .clone()
            .into(),
    )
}

fn schema_alternatives(schema: &Value) -> Vec<Value> {
    schema["anyOf"]
        .as_array()
        .cloned()
        .unwrap_or_else(|| vec![schema.clone()])
}

fn page_index(args: &Value, key: &str, default: usize) -> usize {
    // The frozen schema already validated a nonnegative integer. An index
    // larger than usize::MAX falls beyond every possible journal row.
    args.get(key).map_or(default, |value| {
        ironvellum_contracts::pages::exact_index(value).unwrap_or(usize::MAX)
    })
}

fn catalogue() -> Vec<Tool> {
    let mut tools = Vec::new();
    for (name, description) in [
        (STATUS, "Check training availability and get sourceSha256 before using training or draft tools.".to_owned()),
        (DRAFTS, format!("List proposed workout files by untrusted display name, draft ID, filename and resource URI.{TOKEN_HELP}")),
    ] {
        let schema = &MANIFEST["tools"][name];
        tools.push(as_tool(
            name,
            description,
            schema["inputSchema"].clone(),
            schema["outputSchema"].clone(),
        ));
    }
    for (name, base, help) in TOOLS {
        let v1 = schema::tool(1, name).expect("embedded legacy tool");
        let v2 = schema::tool(2, name).expect("embedded extended tool");
        let mut inputs = schema_alternatives(&v2["inputSchema"]);
        for input in &mut inputs {
            input["properties"][SOURCE] = MANIFEST["sourceArgument"]["schema"].clone();
            if input.get("required").is_none() {
                input["required"] = json!([]);
            }
            input["required"]
                .as_array_mut()
                .expect("required fields")
                .push(json!(SOURCE));
        }
        let input = if inputs.len() == 1 {
            inputs.remove(0)
        } else {
            json!({"type":"object","anyOf":inputs})
        };
        let mut outputs = vec![v1["outputSchema"].clone()];
        outputs.extend(schema_alternatives(&v2["outputSchema"]));
        let mut description = base.to_owned();
        if !help.is_empty() {
            description.push_str(&format!(" For a version 2 export: {help}"));
        }
        description.push_str(TOKEN_HELP);
        tools.push(as_tool(
            name,
            description,
            input,
            json!({"type":"object","anyOf":outputs}),
        ));
    }
    tools
}

fn status(refresh: RefreshStatus, now: u64) -> Value {
    let (state, mut guidance) = if refresh.active.is_some() {
        ("ready", "Use sourceSha256 for training and draft requests. Keep it unchanged when following a cursor or retrying a draft.".to_owned())
    } else if !refresh.pending.is_empty() {
        (
            "awaiting_selection",
            "Choose the training dataset in the connector settings, then check status again."
                .to_owned(),
        )
    } else {
        ("awaiting_training", "Place an Ironvellum training export in the connector input folder, then check status again.".to_owned())
    };
    if refresh.reload_required {
        guidance = "Restart the connector after checking its folder settings.".into();
    } else if refresh.error_code.is_some() {
        guidance.push_str(" Check the connector status for a rejected export or folder problem.");
    }
    let pending: Vec<_> = refresh
        .pending
        .into_iter()
        .map(|s| {
            json!({
                "datasetId": s.dataset_id,
                "snapshotId": s.snapshot_id,
                "exportedAtMs": s.exported_at_ms,
                "sourceSha256": s.sha256,
                "coverage": {
                    "workoutsIncluded": s.workouts_included,
                    "setsIncluded": s.sets_included,
                    "savedWorkoutsIncluded": s.saved_workouts_included
                }
            })
        })
        .collect();
    json!({
        "status": state,
        "snapshot": refresh.active.as_ref().map(|s| s.metadata(now)),
        "sourceSha256": refresh.active.as_ref().map(|s| s.sha256()),
        "pending": pending,
        "refresh": {
            "scanning": refresh.scanning,
            "errorCode": refresh.error_code.map(|e| e.code()),
            "reloadRequired": refresh.reload_required
        },
        "guidance": guidance
    })
}

#[derive(Clone)]
pub struct Watched {
    sources: Arc<Mutex<WatchedSource>>,
    heartbeat: Arc<Mutex<Option<Heartbeat>>>,
    tools: Arc<Vec<Tool>>,
}
impl Watched {
    pub fn open(path: Selected) -> ResultCode<Self> {
        let settings = load_settings(&path).map_err(|e| e.code())?;
        let binding = EndpointBinding::bind(&settings).map_err(|e| e.code())?;
        let sources = WatchedSource::bind(path.clone(), settings, binding).map_err(|e| e.code())?;
        let hb = Heartbeat::start(&path, sources.binding(), &sources.last_status(), now_ms()?)
            .map_err(|e| e.code())?;
        Ok(Self {
            sources: Arc::new(Mutex::new(sources)),
            heartbeat: Arc::new(Mutex::new(Some(hb))),
            tools: Arc::new(catalogue()),
        })
    }

    fn resolve(&self, token: &str) -> ResultCode<(Arc<Snapshot>, Arc<PrivateFolder>)> {
        let mut sources = self.sources.lock().map_err(|_| "storage_unavailable")?;
        Ok((
            sources
                .resolve(token, Instant::now())
                .map_err(|e| e.code())?,
            sources.outbox(),
        ))
    }

    fn record(&self, update: impl FnOnce(&mut Heartbeat, &RefreshStatus, u64)) {
        if let Ok(now) = now_ms()
            && let Ok(sources) = self.sources.lock()
            && let Ok(mut slot) = self.heartbeat.lock()
            && let Some(hb) = slot.as_mut()
        {
            update(hb, &sources.last_status(), now);
        }
    }
    pub fn tick(&self) {
        self.record(Heartbeat::tick);
    }
    pub fn written(&self, r: WriteReceipt) {
        let data = matches!(
            r.method.as_str(),
            "tools/list"
                | "tools/call"
                | "resources/list"
                | "resources/templates/list"
                | "resources/read"
        );
        self.record(|hb, s, now| {
            if r.method == "initialize" || (r.modern && data) {
                hb.evidence(Evidence::Initialized, s, now);
            }
            if r.method == "tools/list" {
                hb.evidence(Evidence::ToolsListed, s, now);
            }
            if r.method == "tools/call" && r.tool.as_deref() == Some(STATUS) {
                hb.evidence(Evidence::StatusChecked, s, now);
            }
        });
    }
    pub fn close(&self) {
        if let Ok(mut slot) = self.heartbeat.lock()
            && let Some(hb) = slot.take()
        {
            hb.close();
        }
    }
    fn call(&self, name: &str, request: &OriginalRequest) -> ResultCode<CallToolResult> {
        let mut args = request.tool_arguments().map_err(|e| e.code())?;
        let now = now_ms()?;
        if !self.tools.iter().any(|t| t.name == name) {
            return Err("unknown_tool");
        }
        if matches!(name, STATUS | DRAFTS) {
            schema::validate(&MANIFEST["tools"][name]["inputSchema"], &args)
                .map_err(|e| e.code())?;
        }
        if name == STATUS {
            let refresh = self
                .sources
                .lock()
                .map_err(|_| "storage_unavailable")?
                .refresh(Instant::now());
            return tool_result(status(refresh, now));
        }
        let token = args
            .as_object_mut()
            .and_then(|o| o.remove(SOURCE))
            .ok_or("invalid_data")?;
        schema::validate(&MANIFEST["sourceArgument"]["schema"], &token).map_err(|e| e.code())?;
        let (source, outbox) = self.resolve(token.as_str().ok_or("invalid_data")?)?;
        if name != DRAFTS {
            return personal::dispatch(&source, &outbox, name, &args, &request.id, now);
        }
        let page = Journal::new(&outbox, &source)
            .list_ready(
                page_index(&args, "offset", 0),
                page_index(&args, "limit", 20),
            )
            .map_err(|e| e.code())?;
        let items: Vec<_> = page
            .items
            .into_iter()
            .map(|(id, title)| {
                json!({
                    "name": title,
                    "draftId": id,
                    "filename": format!("{id}.json"),
                    "uri": format!("ironvellum-draft://{}/{id}", source.sha256())
                })
            })
            .collect();
        tool_result(json!({
            "snapshot": source.metadata(now),
            "items": items,
            "total": page.total,
            "nextOffset": page.next_offset
        }))
    }
    fn read(&self, uri: &str) -> ResultCode<String> {
        let (token, id) = uri
            .strip_prefix("ironvellum-draft://")
            .and_then(|s| s.split_once('/'))
            .ok_or("invalid_resource")?;
        schema::validate(&MANIFEST["sourceArgument"]["schema"], &json!(token))
            .map_err(|_| "invalid_resource")?;
        let uuid =
            &schema::resource("files-v1.schema.json").expect("embedded types")["$defs"]["uuid"];
        schema::validate(uuid, &json!(id)).map_err(|_| "invalid_resource")?;
        let (source, outbox) = self.resolve(token)?;
        let bytes = Journal::new(&outbox, &source)
            .read_ready(id)
            .map_err(|e| e.code())?;
        String::from_utf8(bytes).map_err(|_| "storage_unavailable")
    }
}

impl ServerHandler for Watched {
    fn get_info(&self) -> ServerConfig {
        let mut caps = ServerCapabilities::builder()
            .enable_tools()
            .enable_resources()
            .build();
        if let Some(t) = &mut caps.tools {
            t.list_changed = Some(false);
        }
        if let Some(r) = &mut caps.resources {
            r.list_changed = Some(false);
            r.subscribe = Some(false);
        }
        ServerConfig::new(caps).with_server_info(Implementation::new(NAME, VERSION))
    }
    async fn discover(&self, _: RequestContext<RoleServer>) -> Result<DiscoverResult, ErrorData> {
        Ok(DiscoverResult::from_server_info(
            vec![ProtocolVersion::V_2026_07_28],
            self.get_info(),
        ))
    }
    async fn list_tools(
        &self,
        _: Option<PaginatedRequestParams>,
        _: RequestContext<RoleServer>,
    ) -> Result<ListToolsResult, ErrorData> {
        Ok(ListToolsResult::with_all_items(self.tools.as_ref().clone()))
    }
    async fn call_tool(
        &self,
        p: CallToolRequestParams,
        ctx: RequestContext<RoleServer>,
    ) -> Result<CallToolResponse, ErrorData> {
        let result = ctx
            .extensions
            .get::<OriginalRequest>()
            .ok_or("invalid_data")
            .and_then(|request| self.call(&p.name, request))
            .unwrap_or_else(|code| CallToolResult::error(vec![ContentBlock::text(code)]));
        Ok(CallToolResponse::Complete(result))
    }
    async fn list_resources(
        &self,
        _: Option<PaginatedRequestParams>,
        _: RequestContext<RoleServer>,
    ) -> Result<ListResourcesResult, ErrorData> {
        Ok(ListResourcesResult::with_all_items(vec![]))
    }
    async fn list_resource_templates(
        &self,
        _: Option<PaginatedRequestParams>,
        _: RequestContext<RoleServer>,
    ) -> Result<ListResourceTemplatesResult, ErrorData> {
        Ok(ListResourceTemplatesResult::with_all_items(vec![
            ResourceTemplate::new(
                "ironvellum-draft://{sourceSha256}/{draftId}",
                "Ironvellum workout draft",
            )
            .with_mime_type("application/json")
            .with_description("Get a concrete URI from list_ready_drafts."),
        ]))
    }
    async fn read_resource(
        &self,
        p: ReadResourceRequestParams,
        _: RequestContext<RoleServer>,
    ) -> Result<ReadResourceResponse, ErrorData> {
        let raw = self.read(&p.uri).map_err(|code| {
            ErrorData::new(
                ErrorCode::RESOURCE_NOT_FOUND,
                match code {
                    "invalid_resource"
                    | "not_found"
                    | "source_expired"
                    | "outbox_conflict"
                    | "journal_conflict"
                    | "storage_unavailable" => code,
                    _ => "outbox_conflict",
                },
                None,
            )
        })?;
        Ok(ReadResourceResult::new(vec![
            ResourceContents::text(raw, p.uri).with_mime_type("application/json"),
        ])
        .into())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ready_draft_indexes_preserve_exact_integers() {
        assert_eq!(page_index(&json!({}), "limit", 20), 20);
        for (spelling, integer) in [
            ("9007199254740991", 9007199254740991u128),
            ("9007199254740992", 9007199254740992),
            ("9007199254740993", 9007199254740993),
            ("9007199254740993.000", 9007199254740993),
            ("900719925474099300e-2", 9007199254740993),
            ("1.900e1", 19),
            ("18446744073709551614", 18446744073709551614),
            ("18446744073709551615", 18446744073709551615),
            ("18446744073709551616", 18446744073709551616),
        ] {
            let args: Value = serde_json::from_str(&format!(r#"{{"offset":{spelling}}}"#)).unwrap();
            assert_eq!(
                page_index(&args, "offset", 0),
                usize::try_from(integer).unwrap_or(usize::MAX),
                "{spelling}"
            );
        }
    }
}
