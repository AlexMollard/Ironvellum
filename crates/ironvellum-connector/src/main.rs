mod personal;

use ironvellum_connector::{BoundedTransport, run_protocol_stdio};
use ironvellum_local::{LocalFiles, selected};
use personal::{NAME, Personal, VERSION};
use rmcp::{
    ServiceExt,
    service::{QuitReason, ServerInitializeError},
};
use serde_json::json;
use std::{env, ffi::OsStr, path::PathBuf, process::ExitCode};

const ARGUMENTS: &str = "Expected --context FILE and --outbox DIRECTORY.";
const UNAVAILABLE: &str = "connector_unavailable";
const HELP: &str = "Usage: ironvellum-connector --context FILE --outbox DIRECTORY

Serve one Ironvellum training export over local stdio.

  --context FILE       Selected training export
  --outbox DIRECTORY   Private workout proposal directory
  -h, --help           Show this help";

fn path_argument(value: &OsStr) -> bool {
    let bytes = value.as_encoded_bytes();
    let Some(rest) = bytes.strip_prefix(b"-") else {
        return true;
    };
    // The original argument parser accepts "-" and negative integers/decimals
    // as values. Path validation then reports absolute_path_required.
    if let Some(dot) = rest.iter().position(|&c| c == b'.') {
        return rest[..dot].iter().all(u8::is_ascii_digit)
            && !rest[dot + 1..].is_empty()
            && rest[dot + 1..].iter().all(u8::is_ascii_digit);
    }
    rest.iter().all(u8::is_ascii_digit)
}

fn run() -> Result<(), &'static str> {
    let (mut context, mut outbox) = (None, None);
    let mut extra = false;
    let mut args = env::args_os().skip(1);
    while let Some(arg) = args.next() {
        if arg == "-h" || arg == "--help" {
            println!("{HELP}");
            return Ok(());
        }
        let bytes = arg.as_encoded_bytes();
        let split = bytes.iter().position(|&b| b == b'=');
        let target = match &bytes[..split.unwrap_or(bytes.len())] {
            b"--context" => &mut context,
            b"--outbox" => &mut outbox,
            _ => {
                extra = true;
                continue;
            }
        };
        let path = if let Some(eq) = split {
            // SAFETY: the suffix starts immediately after an ASCII '=' from this OsStr.
            unsafe { OsStr::from_encoded_bytes_unchecked(&bytes[eq + 1..]) }.to_owned()
        } else {
            args.next().filter(|v| path_argument(v)).ok_or(ARGUMENTS)?
        };
        *target = Some(PathBuf::from(path));
    }
    if extra {
        return Err(ARGUMENTS);
    }
    let (context, outbox) = (context.ok_or(ARGUMENTS)?, outbox.ok_or(ARGUMENTS)?);
    let context = selected(&context).map_err(|e| e.code())?;
    let outbox = selected(&outbox).map_err(|e| e.code())?;
    let files = LocalFiles::open(&context, &outbox).map_err(|e| e.code())?;
    let server = Personal::new(files)?;
    run_protocol_stdio(move |input, wire, diagnostics| async move {
        let transport = BoundedTransport::new(
            input,
            wire,
            diagnostics,
            json!({"name":NAME,"version":VERSION}),
            false,
            |_| {},
        );
        match server.serve(transport).await {
            Ok(service) => match service.waiting().await {
                Ok(QuitReason::Closed | QuitReason::Cancelled) => Ok(()),
                _ => Err(UNAVAILABLE),
            },
            Err(ServerInitializeError::ConnectionClosed(_)) => Ok(()),
            Err(_) => Err(UNAVAILABLE),
        }
    })
    .map_err(|_| UNAVAILABLE)?
}

fn main() -> ExitCode {
    match run() {
        Ok(()) => ExitCode::SUCCESS,
        Err(code) => {
            eprintln!("{code}");
            ExitCode::from(2)
        }
    }
}
