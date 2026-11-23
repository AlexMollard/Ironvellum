use std::{
    env, fs,
    process::{Command, ExitCode},
};

fn main() -> ExitCode {
    if let Some(output) = env::var_os("IRONVELLUM_TEST_ARGV_CAPTURE") {
        let argv: Vec<_> = env::args_os()
            .skip(1)
            .map(|s| s.into_string().unwrap())
            .collect();
        fs::write(output, serde_json::to_vec(&argv).unwrap()).unwrap();
        return ExitCode::from(37);
    }
    let root = env::temp_dir().join(format!(
        "ironvellum-desktop-dispatch-{}",
        std::process::id()
    ));
    fs::create_dir(&root).unwrap();
    let connector = root.join(format!("ironvellum-connector{}", env::consts::EXE_SUFFIX));
    fs::copy(env!("CARGO_BIN_EXE_ironvellum-connector"), &connector).unwrap();
    let argv = [
        "--settings",
        "literal & {source} – 東京",
        "--language",
        "ja",
        "--wording=themed",
        "--appearance=dark",
    ];
    let run = || {
        Command::new(&connector)
            .arg("desktop")
            .args(argv)
            .env("IRONVELLUM_TEST_ARGV_CAPTURE", root.join("argv.json"))
            .output()
            .unwrap()
    };
    let missing = run();
    assert_eq!(missing.status.code(), Some(2));
    assert!(String::from_utf8_lossy(&missing.stderr).contains("desktop_unavailable"));
    assert!(!String::from_utf8_lossy(&missing.stderr).contains("literal"));
    let companion = root.join(format!("ironvellum-desktop{}", env::consts::EXE_SUFFIX));
    fs::copy(env::current_exe().unwrap(), companion).unwrap();
    let forwarded = run();
    assert_eq!(forwarded.status.code(), Some(37));
    let captured: Vec<String> =
        serde_json::from_slice(&fs::read(root.join("argv.json")).unwrap()).unwrap();
    assert_eq!(captured, argv);
    fs::remove_dir_all(&root).unwrap();
    ExitCode::SUCCESS
}
