use ironvellum_contracts::Value;
use ironvellum_local::{
    LocalError, selected,
    setup::{self, Client, SetupOptions},
};
use serde_json::json;
use std::{
    collections::BTreeMap,
    ffi::{OsStr, OsString},
    path::Path,
    process::ExitCode,
};

const SETTINGS: &str = "--settings FILE --json";
const SETUP: &str = "--input-directory DIR --outbox-directory DIR --state-directory DIR --client {config-only,claude-desktop} [--client-config FILE] [--replace-existing] [--dataset-id ID] [--watch|--no-watch]";

#[derive(Default)]
struct Arguments {
    values: BTreeMap<String, OsString>,
    json: bool,
    replace: bool,
    watch: Option<bool>,
}

fn parse(command: &str, mut args: impl Iterator<Item = OsString>) -> Result<Option<Arguments>, ()> {
    let mut fields = Arguments::default();
    let mut unknown = false;
    while let Some(token) = args.next() {
        let bytes = token.as_encoded_bytes();
        let eq = bytes.iter().position(|b| *b == b'=');
        let flag = std::str::from_utf8(&bytes[..eq.unwrap_or(bytes.len())]).unwrap_or("");
        if eq.is_none() && matches!(flag, "-h" | "--help") {
            let suffix = match command {
                "setup" => SETUP,
                "select-source" => "--dataset-id ID",
                _ => "",
            };
            println!("Usage: ironvellum-connector {command} {SETTINGS} {suffix}");
            return Ok(None);
        }
        match (command, flag) {
            (_, "--json") | ("setup", "--watch" | "--no-watch" | "--replace-existing") => {
                if eq.is_some() {
                    return Err(());
                }
                match flag {
                    "--json" => fields.json = true,
                    "--replace-existing" => fields.replace = true,
                    _ => fields.watch = Some(flag == "--watch"),
                }
            }
            (_, "--settings")
            | (
                "setup",
                "--input-directory" | "--outbox-directory" | "--state-directory" | "--client"
                | "--client-config",
            )
            | ("setup" | "select-source", "--dataset-id") => {
                let value = if let Some(index) = eq {
                    // SAFETY: the slice follows an ASCII '=' in an encoded OsStr.
                    unsafe { OsStr::from_encoded_bytes_unchecked(&bytes[index + 1..]) }.to_owned()
                } else {
                    args.next().filter(|v| super::path_argument(v)).ok_or(())?
                };
                if flag == "--client" && value != "config-only" && value != "claude-desktop" {
                    return Err(());
                }
                fields.values.insert(flag.into(), value);
            }
            _ => unknown = true,
        }
    }
    let required: &[&str] = match command {
        "setup" => &[
            "--client",
            "--input-directory",
            "--outbox-directory",
            "--state-directory",
        ],
        "select-source" => &["--dataset-id"],
        _ => &[],
    };
    if unknown
        || !fields.json
        || !fields.values.contains_key("--settings")
        || required.iter().any(|key| !fields.values.contains_key(*key))
    {
        return Err(());
    }
    Ok(Some(fields))
}

fn execute(command: &str, args: Arguments) -> Result<Value, LocalError> {
    let path = |key: &str| selected(Path::new(&args.values[key]));
    let settings = path("--settings")?;
    let dataset = args
        .values
        .get("--dataset-id")
        .map(|id| {
            id.to_str().ok_or(if command == "select-source" {
                LocalError::DatasetUnavailable
            } else {
                LocalError::InvalidSettings
            })
        })
        .transpose()?;
    match command {
        "status" => setup::status(&settings)?.wire(),
        "select-source" => {
            setup::select_source(&settings, dataset.ok_or(LocalError::DatasetUnavailable)?)?.wire()
        }
        _ => {
            let client = if args.values["--client"] == "config-only" {
                if args.replace || args.values.contains_key("--client-config") {
                    return Err(LocalError::InvalidClientOption);
                }
                Client::ConfigOnly
            } else {
                Client::ClaudeDesktop {
                    config: args
                        .values
                        .get("--client-config")
                        .map(|v| selected(Path::new(v)))
                        .transpose()?,
                    replace_existing: args.replace,
                }
            };
            setup::configure(SetupOptions {
                settings,
                input: path("--input-directory")?,
                outbox: path("--outbox-directory")?,
                state: path("--state-directory")?,
                dataset: dataset.map(str::to_owned),
                watch: args.watch,
                client,
                executable: selected(
                    &std::env::current_exe().map_err(|_| LocalError::StorageUnavailable)?,
                )?,
            })?
            .wire()
        }
    }
}

pub fn run(command: &str, args: impl Iterator<Item = OsString>) -> ExitCode {
    let result = match parse(command, args) {
        Ok(None) => return ExitCode::SUCCESS,
        Ok(Some(args)) => execute(command, args).map_err(|e| e.code()),
        Err(()) => Err("invalid_arguments"),
    };
    let success = result.is_ok();
    let value = result.unwrap_or_else(|code| {
        let action = match code {"invalid_arguments" => "check_command_help", "client_entry_conflict" => "review_existing_entry", _ => "check_settings"};
        json!({"schemaVersion":1,"status":"error","code":code,"selectedDatasetId":null,"sessions":[],"nextActions":[action]})
    });
    println!("{value}");
    if success {
        ExitCode::SUCCESS
    } else {
        ExitCode::from(2)
    }
}
