mod management;
mod personal;
mod watched;

use ironvellum_connector::{BoundedTransport, run_protocol_stdio};
use ironvellum_local::{LocalFiles, selected};
use personal::{NAME, Personal, VERSION};
use rmcp::{
    ServiceExt,
    service::{QuitReason, ServerInitializeError},
};
use serde_json::json;
use std::time::Duration;
use std::{env, ffi::OsStr, path::PathBuf, process::ExitCode};
use watched::Watched;

const ARGUMENTS: &str = "Expected --context FILE and --outbox DIRECTORY.";
const UNAVAILABLE: &str = "connector_unavailable";
const HELP: &str = "Usage: ironvellum-connector --context FILE --outbox DIRECTORY
       ironvellum-connector serve-folder --settings FILE
       ironvellum-connector {setup|status|select-source} --help
       ironvellum-connector desktop --settings FILE [--language LANG] [--wording MODE] [--appearance THEME]

Serve an Ironvellum training export or an explicitly configured folder over local stdio.

  --context FILE       Selected training export
  --outbox DIRECTORY   Private workout proposal directory
  --settings FILE      Connector settings for serve-folder
  -h, --help           Show this help";

fn path_argument(value: &OsStr) -> bool {
    let bytes = value.as_encoded_bytes();
    let Some(rest) = bytes.strip_prefix(b"-") else {
        return true;
    };
    // The original argument parser accepts "-" and negative integers/decimals
    // as values. Path validation then reports absolute_path_required.
    if bytes.contains(&b' ') {
        return true;
    }
    let Ok(rest) = std::str::from_utf8(rest) else {
        return false;
    };
    let decimal_digits = |s: &str| {
        s.chars().all(|c| {
            unicode_general_category::get_general_category(c)
                == unicode_general_category::GeneralCategory::DecimalNumber
        })
    };
    if let Some((whole, fraction)) = rest.split_once('.') {
        return decimal_digits(whole) && !fraction.is_empty() && decimal_digits(fraction);
    }
    decimal_digits(rest)
}

async fn serve(
    server: impl rmcp::ServerHandler,
    transport: BoundedTransport<tokio::fs::File>,
) -> Result<(), &'static str> {
    match server.serve(transport).await {
        Ok(service) => match service.waiting().await {
            Ok(QuitReason::Closed | QuitReason::Cancelled) => Ok(()),
            _ => Err(UNAVAILABLE),
        },
        Err(ServerInitializeError::ConnectionClosed(_)) => Ok(()),
        Err(_) => Err(UNAVAILABLE),
    }
}

fn folder(mut args: impl Iterator<Item = std::ffi::OsString>) -> Result<(), &'static str> {
    const EXPECTED: &str = "Expected serve-folder --settings FILE.";
    let mut settings = None;
    while let Some(arg) = args.next() {
        if arg == "-h" || arg == "--help" {
            println!("Usage: ironvellum-connector serve-folder --settings FILE");
            return Ok(());
        }
        let path = if arg == "--settings" {
            args.next().filter(|p| path_argument(p)).ok_or(EXPECTED)?
        } else if let Some(path) = arg.as_encoded_bytes().strip_prefix(b"--settings=") {
            // SAFETY: this suffix starts just after an ASCII '=' from the OsStr.
            unsafe { OsStr::from_encoded_bytes_unchecked(path) }.to_owned()
        } else {
            return Err(EXPECTED);
        };
        settings = Some(PathBuf::from(path));
    }
    let path = selected(&settings.ok_or(EXPECTED)?).map_err(|e| e.code())?;
    run_protocol_stdio(move |input, wire, diagnostics| async move {
        let server = Watched::open(path)?;
        let observer = server.clone();
        let transport = BoundedTransport::new(
            input,
            wire,
            diagnostics,
            json!({"name":NAME,"version":VERSION}),
            true,
            move |r| observer.written(r),
        );
        let mut session = std::pin::pin!(serve(server.clone(), transport));
        let mut interruption = std::pin::pin!(tokio::signal::ctrl_c());
        let period = Duration::from_secs(3);
        let mut ticks = tokio::time::interval_at(tokio::time::Instant::now() + period, period);
        ticks.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Skip);
        let result = loop {
            tokio::select! {
                result = &mut session => break result,
                _ = &mut interruption => break Err(UNAVAILABLE),
                _ = ticks.tick() => server.tick(),
            }
        };
        server.close();
        result
    })
    .map_err(|_| UNAVAILABLE)?
}

fn run() -> Result<(), &'static str> {
    let (mut context, mut outbox) = (None, None);
    let mut extra = false;
    let mut args = env::args_os().skip(1).peekable();
    if args.peek().is_some_and(|s| s == "serve-folder") {
        args.next();
        return folder(args);
    }
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
        serve(server, transport).await
    })
    .map_err(|_| UNAVAILABLE)?
}

fn main() -> ExitCode {
    let mut args = env::args_os().skip(1);
    if args.next().as_deref() == Some(OsStr::new("desktop")) {
        let status = env::current_exe().and_then(|exe| {
            let sibling =
                exe.with_file_name(format!("ironvellum-desktop{}", env::consts::EXE_SUFFIX));
            std::process::Command::new(sibling).args(args).status()
        });
        match status {
            Ok(status) => std::process::exit(status.code().unwrap_or(2)),
            Err(_) => {
                eprintln!(
                    "desktop_unavailable — install ironvellum-desktop beside ironvellum-connector, or use status --help."
                );
                return ExitCode::from(2);
            }
        }
    }
    let mut args = env::args_os().skip(1);
    if let Some(command) = args.next()
        && let Some(command @ ("setup" | "status" | "select-source")) = command.to_str()
    {
        return management::run(command, args);
    }
    match run() {
        Ok(()) => ExitCode::SUCCESS,
        Err(code) => {
            eprintln!("{code}");
            ExitCode::from(2)
        }
    }
}
