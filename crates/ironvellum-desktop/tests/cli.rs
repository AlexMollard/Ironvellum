use ironvellum_desktop::copy::Language;
use ironvellum_desktop::options::{Parsed, parse};
use std::ffi::OsString;

#[test]
fn options_preserve_literal_paths_and_use_only_supported_overrides() {
    let path = std::env::temp_dir().join("settings & {literal}.json");
    let options = parse([
        OsString::from("--settings"),
        path.clone().into_os_string(),
        "--appearance=vellum".into(),
        "--language".into(),
        "ja".into(),
    ]);
    let Parsed::Run(options) = options.unwrap() else {
        panic!()
    };
    assert_eq!(options.settings.as_path(), path);
    assert_eq!(options.language, Language::Ja);
    assert!(parse(["--settings".into(), "--language".into()]).is_err());
}

#[test]
fn executable_help_and_malformed_arguments_exit_without_a_display() {
    let executable = env!("CARGO_BIN_EXE_ironvellum-desktop");
    let output = std::process::Command::new(executable)
        .arg("--help")
        .output()
        .unwrap();
    assert!(output.status.success() && output.stderr.is_empty());
    assert!(String::from_utf8_lossy(&output.stdout).contains("--appearance"));
    let bad = std::process::Command::new(executable)
        .args(["--appearance", "private-input"])
        .output()
        .unwrap();
    assert_eq!(bad.status.code(), Some(2));
    assert!(!String::from_utf8_lossy(&bad.stderr).contains("private-input"));
    #[cfg(target_os = "linux")]
    {
        let absent = std::process::Command::new(executable)
            .args(["--settings", "/synthetic/unused.json", "--language", "en"])
            .env_remove("DISPLAY")
            .env_remove("WAYLAND_DISPLAY")
            .env_remove("WAYLAND_SOCKET")
            .output()
            .unwrap();
        assert_eq!(absent.status.code(), Some(2));
        assert!(absent.stdout.is_empty());
        assert!(String::from_utf8_lossy(&absent.stderr).contains("window is unavailable"));
    }
}
