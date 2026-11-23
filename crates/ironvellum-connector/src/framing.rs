use ironvellum_contracts::{Value, loads, wire::MAX_FRAME};
use std::io;
use tokio::io::{AsyncRead, AsyncReadExt};

/// A cancellation-safe LF reader. Partial data stays with the connection
/// when the SDK interrupts receive to service an outgoing message.
pub struct Frames<R> {
    reader: R,
    partial: Vec<u8>,
    buffer: Box<[u8; 8192]>,
    start: usize,
    end: usize,
    invalid: bool,
}

impl<R: AsyncRead + Unpin> Frames<R> {
    pub fn new(reader: R) -> Self {
        Self {
            reader,
            partial: Vec::new(),
            buffer: Box::new([0; 8192]),
            start: 0,
            end: 0,
            invalid: false,
        }
    }

    pub async fn next(&mut self) -> io::Result<Option<Value>> {
        if self.invalid {
            return Ok(None);
        }
        loop {
            if self.start == self.end {
                let remaining = self.buffer.len().min(MAX_FRAME + 1 - self.partial.len());
                self.end = self.reader.read(&mut self.buffer[..remaining]).await?;
                self.start = 0;
            }
            let window = &self.buffer[self.start..self.end];
            let newline = window.iter().position(|b| *b == b'\n');
            let count = newline.map_or(window.len(), |i| i + 1);
            let end = window.is_empty() || newline.is_some();
            self.partial.extend_from_slice(&window[..count]);
            self.start += count;
            if self.partial.len() > MAX_FRAME {
                self.invalid = true;
                return Err(io::Error::new(io::ErrorKind::InvalidData, "invalid_frame"));
            }
            if end {
                if self.partial.is_empty() {
                    return Ok(None);
                }
                let value = loads(&self.partial, MAX_FRAME, 16).map_err(|_| {
                    self.invalid = true;
                    io::Error::new(io::ErrorKind::InvalidData, "invalid_frame")
                })?;
                self.partial.clear();
                return Ok(Some(value));
            }
        }
    }
}
