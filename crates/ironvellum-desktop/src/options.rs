use crate::copy::{Language, Wording};
use ironvellum_local::{Selected, selected};
use std::{
    ffi::{OsStr, OsString},
    path::Path,
};

pub const HELP: &str = "Usage: ironvellum-desktop --settings FILE [--language {en,de,es,fr,ja,ko,pt-BR,zh-CN}] [--wording {plain,themed}] [--appearance {system,dark,vellum}]";

pub struct Options {
    pub settings: Selected,
    pub language: Language,
    pub wording: Wording,
    pub appearance: egui::ThemePreference,
}
pub enum Parsed {
    Help,
    Run(Options),
}

pub fn parse(args: impl IntoIterator<Item = OsString>) -> Result<Parsed, &'static str> {
    let mut args = args.into_iter();
    let mut settings = None;
    let mut language = None;
    let mut wording = Wording::Plain;
    let mut appearance = egui::ThemePreference::System;
    while let Some(arg) = args.next() {
        if arg == "--help" || arg == "-h" {
            return Ok(Parsed::Help);
        }
        let raw = arg.as_encoded_bytes();
        let split = raw.iter().position(|b| *b == b'=');
        let flag = &raw[..split.unwrap_or(raw.len())];
        let value = if let Some(i) = split {
            // The split follows the ASCII '=' boundary in this same OsStr.
            unsafe { OsStr::from_encoded_bytes_unchecked(&raw[i + 1..]) }.to_owned()
        } else {
            args.next()
                .filter(|v| !v.as_encoded_bytes().starts_with(b"--"))
                .ok_or(HELP)?
        };
        match flag {
            b"--settings" => settings = Some(selected(Path::new(&value)).map_err(|e| e.code())?),
            b"--language" => {
                language = Some(value.to_str().and_then(Language::explicit).ok_or(HELP)?)
            }
            b"--wording" => {
                wording = match value.to_str() {
                    Some("plain") => Wording::Plain,
                    Some("themed") => Wording::Themed,
                    _ => return Err(HELP),
                }
            }
            b"--appearance" => {
                appearance = match value.to_str() {
                    Some("system") => egui::ThemePreference::System,
                    Some("dark") => egui::ThemePreference::Dark,
                    Some("vellum") => egui::ThemePreference::Light,
                    _ => return Err(HELP),
                }
            }
            _ => return Err(HELP),
        }
    }
    Ok(Parsed::Run(Options {
        settings: settings.ok_or(HELP)?,
        language: language
            .unwrap_or_else(|| Language::for_system(&sys_locale::get_locale().unwrap_or_default())),
        wording,
        appearance,
    }))
}
