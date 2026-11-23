//! Bounded stdio transport for the headless personal connector.
mod framing;
pub use framing::Frames;
mod transport;
pub use transport::{BoundedTransport, OriginalRequest, WriteReceipt};
mod stdio;
pub use stdio::run_protocol_stdio;
