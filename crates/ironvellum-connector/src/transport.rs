use crate::Frames;
use ironvellum_contracts::{
    Value, encode,
    wire::{MAX_RESULT, lexical_integer},
};
use rmcp::{
    RoleServer,
    model::{
        ClientJsonRpcMessage, GetExtensions, GetMeta, JsonRpcMessage, ProtocolVersion, RequestId,
    },
    service::{RxJsonRpcMessage, TxJsonRpcMessage},
    transport::Transport,
};
use serde_json::json;
use std::{
    collections::HashMap,
    io,
    sync::{
        Arc, Mutex,
        atomic::{AtomicBool, Ordering},
    },
};
use tokio::{
    io::{AsyncRead, AsyncWrite, AsyncWriteExt},
    sync::{Mutex as AsyncMutex, OwnedSemaphorePermit, Semaphore, mpsc},
    task::JoinHandle,
};

const IN_FLIGHT: u32 = 32;

/// Typed request extension. The transport removes the entire arguments field
/// before SDK deserialization and installs the validated value here.
#[derive(Clone, Debug)]
pub struct OriginalRequest {
    pub id: Value,
    pub arguments: Option<Value>,
}

impl OriginalRequest {
    /// Apply the original tool boundary: missing becomes an empty object,
    /// explicit null stays null, and a Decimal whose canonical spelling is
    /// integral (e.g. 0.0) becomes an integer on this second parse.
    pub fn tool_arguments(&self) -> Result<Value, ironvellum_contracts::Refusal> {
        let Some(value) = &self.arguments else {
            return Ok(json!({}));
        };
        ironvellum_contracts::loads(&encode(value)?, ironvellum_contracts::wire::MAX_FRAME, 16)
    }
}

#[derive(Clone, Debug)]
pub struct WriteReceipt {
    pub id: Value,
    pub method: String,
    pub tool: Option<String>,
    pub modern: bool,
}

struct Pending {
    receipt: WriteReceipt,
    _permit: OwnedSemaphorePermit,
}

type Requests = Arc<Mutex<HashMap<RequestId, Pending>>>;

struct Output<W> {
    sink: AsyncMutex<W>,
    identity: Value,
    folder: bool,
    legacy: AtomicBool,
    receipts: Box<dyn Fn(WriteReceipt) + Send + Sync>,
}

impl<W: AsyncWrite + Unpin> Output<W> {
    async fn write(&self, raw: &[u8]) -> io::Result<()> {
        let mut sink = self.sink.lock().await;
        sink.write_all(raw).await?;
        sink.write_all(b"\n").await?;
        sink.flush().await
    }

    async fn reject(&self, id: Value, code: i64) -> io::Result<()> {
        let mut envelope =
            json!({"jsonrpc":"2.0","id":null,"error":{"code":code,"message":"invalid_request"}});
        envelope["id"] = id;
        self.write(&encoded(&envelope)?).await
    }
}

/// rmcp treats discovery as an optional probe. The personal connector's
/// original SDK selects its lifecycle on the first request instead.
#[derive(Clone, Copy, PartialEq, Eq)]
enum Lifecycle {
    Undecided,
    LegacyBootstrap,
    Legacy,
    Inline,
}

impl Lifecycle {
    fn check(
        &mut self,
        method: &str,
        version: Option<&ProtocolVersion>,
        complete_meta: bool,
    ) -> Option<i64> {
        if *self == Self::Undecided {
            *self = if version.is_some() {
                Self::Inline
            } else {
                Self::LegacyBootstrap
            };
        }
        match (*self, method) {
            (Self::Inline, "initialize") => Some(-32022),
            (Self::Inline, _) if !complete_meta => Some(-32602),
            (Self::Inline, _) if version.is_some_and(|v| v != &ProtocolVersion::V_2026_07_28) => {
                Some(-32022)
            }
            (Self::Inline, "ping") => Some(-32601),
            (Self::Legacy, _) if version.is_some() => Some(-32600),
            (Self::LegacyBootstrap | Self::Legacy, "server/discover") => Some(-32601),
            (Self::LegacyBootstrap, "initialize") => {
                *self = Self::Legacy;
                None
            }
            (Self::LegacyBootstrap, "ping") => None,
            (Self::LegacyBootstrap, _) => Some(-32600),
            _ => None,
        }
    }
}

fn encoded(value: &Value) -> io::Result<Vec<u8>> {
    encode(value).map_err(io::Error::other)
}

fn valid_id(id: &Value) -> bool {
    id.is_string() || id.as_number().is_some_and(lexical_integer)
}

fn request_id(envelope: &Value) -> Option<Value> {
    let id = envelope.get("id")?;
    if valid_id(id) {
        return Some(id.clone());
    }
    // The old object-parameter tool bridge re-encoded the entire envelope.
    // Its canonical spelling can turn a Decimal ID into a JSON integer.
    if !id.is_number()
        || envelope.get("method")?.as_str()? != "tools/call"
        || !envelope.get("params")?.is_object()
    {
        return None;
    }
    let canonical = encode(id).ok()?;
    let projected = ironvellum_contracts::loads(&canonical, canonical.len(), 1).ok()?;
    valid_id(&projected).then_some(projected)
}

pub struct BoundedTransport<W> {
    rx: mpsc::Receiver<ClientJsonRpcMessage>,
    reader: JoinHandle<()>,
    output: Arc<Output<W>>,
    requests: Requests,
    capacity: Arc<Semaphore>,
}

impl<W> Drop for BoundedTransport<W> {
    fn drop(&mut self) {
        self.reader.abort();
    }
}

impl<W: AsyncWrite + Unpin + Send + 'static> BoundedTransport<W> {
    pub fn new<R, D>(
        reader: R,
        writer: W,
        mut diagnostics: D,
        identity: Value,
        folder: bool,
        receipts: impl Fn(WriteReceipt) + Send + Sync + 'static,
    ) -> Self
    where
        R: AsyncRead + Unpin + Send + 'static,
        D: AsyncWrite + Unpin + Send + 'static,
    {
        let (tx, rx) = mpsc::channel(1);
        let requests: Requests = Default::default();
        let capacity = Arc::new(Semaphore::new(IN_FLIGHT as usize));
        let output = Arc::new(Output {
            sink: AsyncMutex::new(writer),
            identity,
            folder,
            legacy: AtomicBool::new(false),
            receipts: Box::new(receipts),
        });
        let reader = tokio::spawn({
            let requests = requests.clone();
            let capacity = capacity.clone();
            let output = output.clone();
            async move {
                let mut frames = Frames::new(reader);
                let mut serial = 0i64;
                let mut lifecycle = Lifecycle::Undecided;
                loop {
                    let mut value = match frames.next().await {
                        Ok(Some(value)) => value,
                        Ok(None) => break,
                        Err(e) => {
                            if e.kind() == io::ErrorKind::InvalidData {
                                let _ = diagnostics.write_all(b"invalid_frame\n").await;
                                let _ = diagnostics.flush().await;
                            }
                            break;
                        }
                    };
                    let original = request_id(&value);
                    let method = value
                        .get("method")
                        .and_then(Value::as_str)
                        .map(str::to_owned);
                    let request = method.is_some() && value.get("id").is_some();
                    let mut arguments = None;
                    if method.as_deref() == Some("tools/call")
                        && let Some(params) = value.get_mut("params").and_then(Value::as_object_mut)
                    {
                        arguments = params.remove("arguments");
                    }
                    if request && original.is_some() {
                        loop {
                            serial = serial.wrapping_add(1);
                            if !requests
                                .lock()
                                .unwrap()
                                .contains_key(&RequestId::Number(serial))
                            {
                                break;
                            }
                        }
                        value["id"] = Value::from(serial);
                    }
                    if method.as_deref() == Some("notifications/cancelled")
                        && let Some(cancel) = value.pointer_mut("/params/requestId")
                    {
                        let found = requests.lock().unwrap().iter().find_map(|(key, pending)| {
                            (&pending.receipt.id == cancel).then(|| key.clone())
                        });
                        if let Some(RequestId::Number(n)) = found {
                            *cancel = Value::from(n);
                        } else {
                            *cancel = Value::from("unmatched");
                        }
                    }
                    let parsed = serde_json::to_vec(&value)
                        .ok()
                        .and_then(|raw| serde_json::from_slice::<ClientJsonRpcMessage>(&raw).ok());
                    let Some(mut message) = parsed.filter(|_| !request || original.is_some())
                    else {
                        // The original SDK discards valid JSON that fails its
                        // envelope model, then continues with the next frame.
                        continue;
                    };
                    let mut wait_for_bootstrap_write = false;
                    if lifecycle == Lifecycle::Inline
                        && method.as_deref() == Some("notifications/initialized")
                        && matches!(&message, JsonRpcMessage::Notification(_))
                    {
                        continue;
                    }
                    if let JsonRpcMessage::Request(req) = &mut message {
                        let Some(id) = original else {
                            continue;
                        };
                        let meta = req.request.get_meta();
                        let version = meta.protocol_version();
                        let complete_meta = meta
                            .missing_required_keys(&ProtocolVersion::V_2026_07_28)
                            .is_empty();
                        let modern =
                            version == Some(ProtocolVersion::V_2026_07_28) && complete_meta;
                        let method = method.unwrap_or_default();
                        wait_for_bootstrap_write = lifecycle == Lifecycle::Undecided
                            && matches!(method.as_str(), "initialize" | "server/discover" | "ping");
                        if let Some(code) =
                            lifecycle.check(&method, version.as_ref(), complete_meta)
                        {
                            if output.reject(id, code).await.is_err() {
                                return;
                            }
                            continue;
                        }
                        // rmcp represents a known method with untyped parameters
                        // as a custom request. Watched resource reads distinguish
                        // invalid parameters from a well-formed, refused URI.
                        if output.folder
                            && method == "resources/read"
                            && !value.pointer("/params/uri").is_some_and(Value::is_string)
                        {
                            if output.reject(id, -32602).await.is_err() {
                                return;
                            }
                            continue;
                        }
                        let permit = match capacity.clone().try_acquire_owned() {
                            Ok(permit) => permit,
                            Err(tokio::sync::TryAcquireError::NoPermits) => {
                                // Keep reading control messages while the
                                // bounded set of accepted calls completes.
                                if output.reject(id, -32603).await.is_err() {
                                    return;
                                }
                                continue;
                            }
                            Err(tokio::sync::TryAcquireError::Closed) => break,
                        };
                        let receipt = WriteReceipt {
                            id: id.clone(),
                            method,
                            tool: value
                                .pointer("/params/name")
                                .and_then(Value::as_str)
                                .map(str::to_owned),
                            modern,
                        };
                        req.request
                            .extensions_mut()
                            .insert(OriginalRequest { id, arguments });
                        requests.lock().unwrap().insert(
                            req.id.clone(),
                            Pending {
                                receipt,
                                _permit: permit,
                            },
                        );
                    }
                    if tx.send(message).await.is_err() {
                        return;
                    }
                    if wait_for_bootstrap_write {
                        let Ok(_all) = capacity.acquire_many(IN_FLIGHT).await else {
                            return;
                        };
                    }
                }
                // Keep the SDK receive channel alive until every accepted
                // request has completed its serialized write and flush.
                let _all = capacity.acquire_many(IN_FLIGHT).await;
            }
        });
        Self {
            rx,
            reader,
            output,
            requests,
            capacity,
        }
    }
}

impl<W: AsyncWrite + Unpin + Send + 'static> Transport<RoleServer> for BoundedTransport<W> {
    type Error = io::Error;

    fn send(
        &mut self,
        mut message: TxJsonRpcMessage<RoleServer>,
    ) -> impl Future<Output = io::Result<()>> + Send + 'static {
        let key = match &message {
            JsonRpcMessage::Response(r) => Some(&r.id),
            JsonRpcMessage::Error(e) => e.id.as_ref(),
            _ => None,
        };
        let pending = key.and_then(|k| self.requests.lock().unwrap().remove(k));
        let output = self.output.clone();
        let capacity = self.capacity.clone();
        let reader = self.reader.abort_handle();
        async move {
            // Value's serde map visitor treats a reserved first key as a
            // Number. Keep authored tool data out of that conversion entirely.
            let structured = match &mut message {
                JsonRpcMessage::Response(r) => match &mut r.result {
                    rmcp::model::ServerResult::CallToolResult(result) => {
                        result.structured_content.take()
                    }
                    _ => None,
                },
                _ => None,
            };
            let mut envelope = serde_json::to_value(message).map_err(io::Error::other)?;
            if let Some(value) = structured {
                let raw = encoded(&value)?;
                envelope["result"]["structuredContent"] = ironvellum_contracts::loads(
                    &raw,
                    raw.len(),
                    ironvellum_contracts::wire::MAX_DEPTH,
                )
                .map_err(io::Error::other)?;
            }
            let mut receipt = pending.as_ref().map(|p| p.receipt.clone());
            if let Some(r) = &mut receipt {
                envelope["id"] = r.id.clone();
                r.modern &= !output.legacy.load(Ordering::Acquire);
            }
            if let Some(error) = envelope.get_mut("error") {
                let code = error["code"].as_i64().unwrap_or(-32600);
                let message = error["message"].as_str().unwrap_or_default();
                let known_resource_error = matches!(
                    message,
                    "invalid_resource"
                        | "not_found"
                        | "source_expired"
                        | "outbox_conflict"
                        | "journal_conflict"
                        | "storage_unavailable"
                );
                // rmcp translates RESOURCE_NOT_FOUND to INVALID_PARAMS for
                // modern clients. Restore this connector's frozen resource
                // code only for a correlated read and its public refusals.
                let modern_read = receipt
                    .as_ref()
                    .is_some_and(|r| r.modern && r.method == "resources/read");
                let allowed = output.folder
                    && known_resource_error
                    && (code == -32002 || (modern_read && code == -32602));
                let code = if allowed { -32002 } else { code };
                *error =
                    json!({"code":code,"message": if allowed {message} else {"invalid_request"}});
                receipt = None;
            }
            if let Some(r) = &receipt
                && r.modern
                && envelope["result"].is_object()
                && r.method != "server/discover"
            {
                envelope["result"]["_meta"]["io.modelcontextprotocol/serverInfo"] =
                    output.identity.clone();
            }
            let mut raw = encoded(&envelope)?;
            if raw.len() > MAX_RESULT
                && envelope.pointer("/result/structuredContent/view").is_some()
            {
                envelope["result"]["content"] =
                    json!([{"type":"text","text":"Read this page in structuredContent."}]);
                raw = encoded(&envelope)?;
            }
            if raw.len() > MAX_RESULT {
                let mut minimal = json!({"jsonrpc":"2.0","id":null,"result":{"isError":true,"content":[{"type":"text","text":"result_limit"}]}});
                minimal["id"] = envelope["id"].clone();
                raw = encoded(&minimal)?;
                receipt = None;
            }
            if let Err(e) = output.write(&raw).await {
                capacity.close();
                reader.abort();
                return Err(e);
            }
            if let Some(r) = receipt
                && r.method != "server/discover"
                && envelope.pointer("/result/isError").and_then(Value::as_bool) != Some(true)
            {
                if r.method == "initialize" {
                    output.legacy.store(true, Ordering::Release);
                }
                (output.receipts)(r);
            }
            drop(pending);
            Ok(())
        }
    }

    async fn receive(&mut self) -> Option<RxJsonRpcMessage<RoleServer>> {
        let message = self.rx.recv().await?;
        // The server SDK intentionally drops replies after processing a
        // cancellation. Release its slot at delivery, not at read-ahead:
        // an already completed reply must still retain its original ID.
        if let JsonRpcMessage::Notification(notification) = &message
            && let rmcp::model::ClientNotification::CancelledNotification(cancel) =
                &notification.notification
            && let Some(id) = &cancel.params.request_id
        {
            self.requests.lock().unwrap().remove(id);
        }
        Some(message)
    }
    async fn close(&mut self) -> io::Result<()> {
        self.reader.abort();
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tokio::{
        io::{AsyncBufReadExt, BufReader, duplex},
        time::{Duration, timeout},
    };

    #[tokio::test]
    async fn admission_refusal_keeps_controls_readable_until_capacity_returns() {
        timeout(Duration::from_secs(3), async {
            let (mut client_in, server_in) = duplex(4096);
            let (server_out, client_out) = duplex(4096);
            let (sent, mut receipts) = mpsc::unbounded_channel();
            let mut transport = BoundedTransport::new(server_in, server_out, tokio::io::sink(), json!({}), false,
                move |r| { sent.send(r).unwrap(); });
            // Reserve the real admission budget directly for a deterministic
            // transport-level check independent of handler scheduling.
            let reservation = transport.capacity.clone().acquire_many_owned(IN_FLIGHT).await.unwrap();
            client_in.write_all(b"{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"ping\"}\n").await.unwrap();
            let mut lines = BufReader::new(client_out).lines();
            let refused: Value = serde_json::from_str(&lines.next_line().await.unwrap().unwrap()).unwrap();
            assert_eq!(refused,json!({"jsonrpc":"2.0","id":7,"error":{"code":-32603,"message":"invalid_request"}}));
            assert!(receipts.try_recv().is_err());
            client_in.write_all(b"{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\",\"params\":{\"requestId\":7}}\n").await.unwrap();
            assert!(matches!(transport.receive().await, Some(JsonRpcMessage::Notification(_))));
            drop(reservation);
            client_in.write_all(b"{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"ping\"}\n").await.unwrap();
            drop(client_in);
            let Some(JsonRpcMessage::Request(request)) = transport.receive().await else {panic!("expected ping");};
            transport.send(JsonRpcMessage::response(rmcp::model::ServerResult::EmptyResult(rmcp::model::EmptyResult{}),request.id)).await.unwrap();
            assert_eq!(receipts.recv().await.unwrap().id,8);
            assert!(transport.receive().await.is_none());
        }).await.unwrap();
    }
}
