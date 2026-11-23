use egui::{
    Event as Input, ImeEvent, Key, Modifiers,
    accesskit::{Action, ActionRequest, NodeId, TreeUpdate},
};
use ironvellum_desktop::{
    copy::{Language, Wording},
    window::{DesktopWindow, Page},
    worker::Event,
};
use ironvellum_desktop::{
    typography,
    worker::{Action as Job, Outcome, Work},
};
use ironvellum_local::desktop_model::DesktopState;

struct Screen {
    ctx: egui::Context,
    window: DesktopWindow,
    size: egui::Vec2,
    time: f64,
    shapes: Vec<egui::epaint::ClippedShape>,
}
impl Screen {
    fn new(language: Language) -> Self {
        let ctx = egui::Context::default();
        ctx.enable_accesskit();
        ctx.set_fonts(typography::definitions(language));
        let mut window = DesktopWindow::new(language, Wording::Plain);
        window.accept(Event::Observed(Ok(DesktopState::default().into())));
        Self {
            ctx,
            window,
            size: egui::vec2(760., 680.),
            time: 0.,
            shapes: vec![],
        }
    }
    fn frame(&mut self, events: Vec<Input>) -> (TreeUpdate, Vec<Work>) {
        self.time += 0.1;
        let mut actions = vec![];
        let modifiers = events
            .iter()
            .find_map(|e| {
                if let Input::Key { modifiers, .. } = e {
                    Some(*modifiers)
                } else {
                    None
                }
            })
            .unwrap_or_default();
        let output = self.ctx.run(
            egui::RawInput {
                screen_rect: Some(egui::Rect::from_min_size(egui::Pos2::ZERO, self.size)),
                time: Some(self.time),
                events,
                modifiers,
                ..Default::default()
            },
            |ctx| actions.extend(self.window.render(ctx)),
        );
        self.shapes = output.shapes.clone();
        assert!(
            !self
                .ctx
                .tessellate(output.shapes, output.pixels_per_point)
                .is_empty()
        );
        (output.platform_output.accesskit_update.unwrap(), actions)
    }
}
fn target(tree: &TreeUpdate, label: &str) -> NodeId {
    tree.nodes
        .iter()
        .find(|(_, n)| n.label() == Some(label))
        .unwrap_or_else(|| {
            panic!(
                "missing {label}: {:?}",
                tree.nodes
                    .iter()
                    .map(|(_, n)| (n.role(), n.label(), n.value()))
                    .collect::<Vec<_>>()
            )
        })
        .0
}
fn activate(id: NodeId) -> Input {
    Input::AccessKitActionRequest(ActionRequest {
        action: Action::Click,
        target: id,
        data: None,
    })
}
fn press(key: Key, shift: bool) -> Input {
    Input::Key {
        key,
        physical_key: None,
        pressed: true,
        repeat: false,
        modifiers: Modifiers {
            shift,
            ..Modifiers::NONE
        },
    }
}

fn source(id: &str, n: u64) -> ironvellum_local::SourceSummary {
    ironvellum_local::SourceSummary {
        dataset_id: id.into(),
        snapshot_id: "550e8400-e29b-41d4-a716-446655440001".into(),
        sha256: "a".repeat(64),
        exported_at_ms: 1_735_000_000_000,
        workouts_included: n,
        sets_included: n * 10,
        saved_workouts_included: n / 2,
    }
}
fn label_text(tree: &TreeUpdate) -> Vec<&str> {
    tree.nodes
        .iter()
        .flat_map(|(_, node)| [node.label(), node.value()].into_iter().flatten())
        .collect()
}

fn observed_settings() -> ironvellum_local::Settings {
    let root = std::env::temp_dir();
    let p = |leaf| ironvellum_local::selected(&root.join(leaf)).unwrap();
    ironvellum_local::Settings::new(p("exports"), p("out"), p("state"))
}
fn connection(id: &str, initialized: bool, verified: bool) -> ironvellum_local::status::SessionRow {
    use ironvellum_local::status::{SessionRecord, SessionRow};
    SessionRow {
        id: id.into(),
        reload_required: false,
        record: SessionRecord {
            time_ms: 1,
            endpoints: "a".repeat(64),
            initialized,
            tools_listed: verified,
            status_checked: verified,
            loaded_source: None,
            reload_required: false,
        },
    }
}

#[test]
fn status_separates_running_initialized_verified_and_mixed_sessions() {
    let mut screen = Screen::new(Language::En);
    let pending = connection("one", false, false);
    let initialized = connection("two", true, false);
    let verified = connection("three", true, true);
    for (sessions, counts) in [
        (vec![], (0, 0, 0)),
        (vec![pending.clone()], (1, 0, 0)),
        (vec![initialized.clone()], (1, 1, 0)),
        (vec![verified.clone()], (1, 1, 1)),
        (vec![pending, initialized, verified], (3, 2, 1)),
    ] {
        screen.window.accept(Event::Observed(Ok(DesktopState {
            settings: Some(observed_settings()),
            sessions,
            ..Default::default()
        }
        .into())));
        let (tree, _) = screen.frame(vec![]);
        let expected = format!(
            "Running: {} · Initialized: {} · Verified: {}",
            counts.0, counts.1, counts.2
        );
        assert!(label_text(&tree).contains(&expected.as_str()), "{expected}");
    }
}

fn loaded_state() -> DesktopState {
    let src = source("550e8400-e29b-41d4-a716-446655440011", 17);
    let mut row = connection("one", true, true);
    row.record.loaded_source =
        Some(ironvellum_local::status::LoadedSource::from_summary(&src).unwrap());
    DesktopState {
        settings: Some(observed_settings()),
        sessions: vec![row],
        ..Default::default()
    }
}

#[test]
fn unrepresentable_decoded_coverage_reports_a_diagnostic_and_keeps_valid_sessions() {
    let mut state = loaded_state();
    let encoded = state.sessions[0].record.encode().unwrap();
    let raw = String::from_utf8(encoded).unwrap().replace(
        "\"workoutsIncluded\":17",
        "\"workoutsIncluded\":18446744073709551616",
    );
    assert!(raw.contains("18446744073709551616"));
    let record = ironvellum_local::status::SessionRecord::decode(raw.as_bytes()).unwrap();
    assert!(
        String::from_utf8(record.encode().unwrap())
            .unwrap()
            .contains("18446744073709551616")
    );
    state.sessions.push(ironvellum_local::status::SessionRow {
        id: "damaged coverage".into(),
        record,
        reload_required: false,
    });
    let mut screen = Screen::new(Language::En);
    screen.window.accept(Event::Observed(Ok(state.into())));
    let (tree, _) = screen.frame(vec![]);
    let labels = label_text(&tree);
    for text in [
        "Some session activity could not be checked.",
        "Running: 2 · Initialized: 2 · Verified: 2",
        "17",
        "one",
    ] {
        assert!(labels.contains(&text), "{text}");
    }
    let (_, work) = screen.frame(vec![activate(target(&tree, "Copy setup instructions"))]);
    assert!(matches!(work.as_slice(), [Work::Act(_, Job::Copy(_))]));
}

#[test]
fn new_watch_preference_does_not_promise_updates_for_an_old_session() {
    let mut s = Screen::new(Language::En);
    let mut data = loaded_state();
    data.settings.as_mut().unwrap().watch_enabled = true;
    data.sessions[0].reload_required = true;
    s.window.accept(Event::Observed(Ok(data.into())));
    let (tree, _) = s.frame(vec![]);
    let labels = label_text(&tree);
    assert!(labels.contains(&"Automatic loading is enabled in settings."));
    assert!(!labels.contains(&"Updated when you add a new training file."));
    assert!(labels.contains(&"Reload your AI client to use the new folders."));
}

#[test]
fn keyboard_focus_draws_a_contrasting_ring_around_the_primary_action_on_both_papers() {
    for (theme, ink) in [
        (
            egui::ThemePreference::Dark,
            egui::Color32::from_rgb(232, 232, 228),
        ),
        (
            egui::ThemePreference::Light,
            egui::Color32::from_rgb(36, 27, 21),
        ),
    ] {
        let mut s = Screen::new(Language::En);
        s.ctx.set_theme(theme);
        let (mut tree, _) = s.frame(vec![]);
        let primary = target(&tree, "Copy setup instructions");
        for _ in 0..12 {
            if tree.focus == primary {
                break;
            }
            s.frame(vec![press(Key::Tab, false)]);
            (tree, _) = s.frame(vec![]);
        }
        assert_eq!(tree.focus, primary);
        let bounds = tree
            .nodes
            .iter()
            .find(|(id, _)| *id == primary)
            .unwrap()
            .1
            .bounds()
            .unwrap();
        assert!(
            s.shapes.iter().any(|cs| {
                let egui::Shape::Path(path) = &cs.shape else {
                    return false;
                };
                let r = egui::Rect::from_points(&path.points);
                matches!(path.stroke.color,egui::epaint::ColorMode::Solid(color) if color==ink)
                    && path.stroke.width >= 2.
                    && r.left() < bounds.x0 as f32
                    && r.right() > bounds.x1 as f32
                    && (r.center().y as f64 - (bounds.y1 + bounds.y0) / 2.).abs() < 1.
            }),
            "missing contrasted perimeter for {theme:?}"
        );
    }
}

#[test]
fn invalid_refresh_places_full_width_compact_coverage_immediately_above_warning() {
    let mut s = Screen::new(Language::En);
    let mut state = loaded_state();
    state.source_problem = Some(ironvellum_local::LocalError::SourceChanged);
    s.window.accept(Event::Observed(Ok(state.into())));
    let (tree, _) = s.frame(vec![]);
    let labels = label_text(&tree);
    assert!(!labels.contains(&"Workouts ready"));
    assert!(labels.contains(&"Workouts: 17 · Sets: 170 · Saved workouts: 8"));
    let extent = |name: &str| {
        tree.nodes
            .iter()
            .find(|(_, n)| n.label() == Some(name) || n.value() == Some(name))
            .unwrap()
            .1
            .bounds()
            .unwrap()
    };
    let coverage = extent("Workouts: 17 · Sets: 170 · Saved workouts: 8");
    let warning = extent("New file could not be read");
    assert!(warning.y0 > coverage.y1 && warning.y0 - coverage.y1 < 70.);
    let panels: Vec<_> = s
        .shapes
        .iter()
        .filter_map(|cs| match &cs.shape {
            egui::Shape::Path(p) if p.points.len() == 8 => Some(egui::Rect::from_points(&p.points)),
            _ => None,
        })
        .collect();
    let card_at = |y: f64| {
        *panels
            .iter()
            .find(|r| r.top() < y as f32 && r.bottom() > y as f32 && r.width() > s.size.x * 0.88)
            .unwrap()
    };
    assert_eq!(
        card_at(coverage.y0).x_range(),
        card_at(warning.y0).x_range()
    );
}

#[test]
fn draft_row_keeps_one_named_action_with_distinct_ready_text_and_trailing_arrow() {
    use ironvellum_local::{
        Journal, LocklessOpen, PrivateFolder,
        desktop_model::DesktopObserver,
        selected,
        setup::{Client, SetupOptions, configure_initial},
    };
    let root = tempfile::tempdir().unwrap();
    #[cfg(unix)]
    let base = root.path().canonicalize().unwrap();
    #[cfg(windows)]
    let base = root.path().to_owned();
    let path = |leaf| selected(&base.join(leaf)).unwrap();
    let settings = path("private/settings.json");
    configure_initial(SetupOptions {
        settings: settings.clone(),
        input: path("in"),
        outbox: path("out"),
        state: path("state"),
        dataset: None,
        watch: Some(false),
        client: Client::ConfigOnly,
        executable: selected(&std::env::current_exe().unwrap()).unwrap(),
    })
    .unwrap();
    let folder = PrivateFolder::open(&path("out"), LocklessOpen::Existing).unwrap();
    let source = ironvellum_contracts::snapshot::Snapshot::from_bytes(include_bytes!(
        "../../../tools/local_connector/contracts/fixtures/context.json"
    ))
    .unwrap();
    let mut proposal: serde_json::Value = serde_json::from_slice(include_bytes!(
        "../../../tools/local_connector/contracts/fixtures/proposal.json"
    ))
    .unwrap();
    proposal["draft"]["name"] = serde_json::json!("{Upper} 東京");
    Journal::new(&folder, &source)
        .create("row", &proposal["draft"], 1_700_001_000_000)
        .unwrap();
    let state = DesktopObserver::new(settings).read(std::time::Instant::now(), 1_700_001_000_010);
    let mut s = Screen::new(Language::En);
    s.ctx.set_theme(egui::ThemePreference::Dark);
    s.window.accept(Event::Observed(Ok(state.into())));
    let (tree, _) = s.frame(vec![]);
    let row = target(&tree, "{Upper} 東京\nReady to review");
    let node = &tree.nodes.iter().find(|(id, _)| *id == row).unwrap().1;
    assert_eq!(node.role(), egui::accesskit::Role::Button);
    let label = |name: &str| {
        s.shapes
            .iter()
            .find_map(|c| match &c.shape {
                egui::Shape::Text(t) if t.galley.text() == name => Some(t),
                _ => None,
            })
            .unwrap()
    };
    let name = label("{Upper} 東京");
    let ready = label("Ready to review");
    let arrow = label("→");
    let green = egui::Color32::from_rgb(0x34, 0xd3, 0x99);
    assert_eq!(
        ready
            .override_text_color
            .unwrap_or(ready.galley.job.sections[0].format.color),
        green
    );
    assert!(ready.pos.y >= name.pos.y + name.galley.size().y);
    assert!(arrow.pos.x > name.pos.x + name.galley.size().x);
    assert!((node.bounds().unwrap().x1 - (arrow.pos.x + arrow.galley.size().x) as f64).abs() < 20.);
    let (_, work) = s.frame(vec![activate(row)]);
    assert!(
        matches!(work.as_slice(),[Work::Act(_,Job::Reveal(draft))] if draft.display_name=="{Upper} 東京")
    );
}

#[test]
fn current_sources_stale_retention_and_cached_choice_are_independent() {
    use ironvellum_local::{
        InventoryStatus, Settings, selected,
        status::{LoadedSource, SessionRecord, SessionRow},
    };
    let mut screen = Screen::new(Language::En);
    let old = source("550e8400-e29b-41d4-a716-446655440011", 17);
    let new = source("550e8400-e29b-41d4-a716-446655440022", 23);
    let other = source("550e8400-e29b-41d4-a716-446655440033", 33);
    let root = std::env::temp_dir();
    let path = |s: &str| selected(&root.join(s)).unwrap();
    let mut settings = Settings::new(path("in"), path("out"), path("state"));
    settings.selected_dataset_id = Some(old.dataset_id.clone());
    let session = |id: &str, src: &ironvellum_local::SourceSummary| SessionRow {
        id: id.into(),
        reload_required: false,
        record: SessionRecord {
            time_ms: 1,
            endpoints: "a".repeat(64),
            initialized: true,
            tools_listed: true,
            status_checked: false,
            loaded_source: Some(LoadedSource::from_summary(src).unwrap()),
            reload_required: false,
        },
    };
    let mut state = DesktopState {
        settings: Some(settings),
        source: Some(InventoryStatus {
            available: Some(old.clone()),
            pending: vec![new.clone()],
            ..Default::default()
        }),
        sessions: vec![session("session A", &old), session("session B", &other)],
        ..Default::default()
    };
    screen
        .window
        .accept(Event::Observed(Ok(state.clone().into())));
    let (overview, _) = screen.frame(vec![]);
    let labels = label_text(&overview);
    for key in ["AI session connected", "session A", "session B", "17", "33"] {
        assert!(labels.contains(&key), "{key}");
    }
    state.source_problem = Some(ironvellum_local::LocalError::SourceChanged);
    state.source.as_mut().unwrap().available = None;
    screen
        .window
        .accept(Event::Observed(Ok(state.clone().into())));
    let (failed, _) = screen.frame(vec![]);
    assert!(label_text(&failed).contains(&"New file could not be read"));
    assert!(label_text(&failed).contains(&"session A"));
    state.sessions.clear();
    screen
        .window
        .accept(Event::Observed(Ok(state.clone().into())));
    let (detached, _) = screen.frame(vec![]);
    assert!(label_text(&detached).contains(&"No AI client is currently connected."));
    screen.frame(vec![activate(target(&detached, "Choose training to use"))]);
    let (choice, _) = screen.frame(vec![]);
    let keep = target(&choice, "Keep current training");
    assert_eq!(
        choice
            .nodes
            .iter()
            .find(|(id, _)| *id == keep)
            .unwrap()
            .1
            .toggled(),
        Some(egui::accesskit::Toggled::True)
    );
    assert!(label_text(&choice).contains(&"Workouts: 17 · Sets: 170 · Saved workouts: 8"));
    let (_, actions) = screen.frame(vec![activate(target(&choice, "Confirm choice"))]);
    assert!(matches!(actions.as_slice(),[Work::Act(_,Job::Select(id))] if id==&old.dataset_id));
}

#[test]
fn translated_settings_controls_fit_narrow_and_scaled_viewports() {
    for language in Language::ALL {
        for (width, height, scale) in [(285., 265., 1.), (420., 300., 1.5), (760., 680., 2.)] {
            let mut s = Screen::new(language);
            s.size = egui::vec2(width, height);
            s.ctx.set_pixels_per_point(scale);
            s.window.open_manual();
            let edit = s.window.folders_mut().unwrap();
            edit.input = "私/long path / 서울 / ".repeat(30);
            edit.outbox = edit.input.clone();
            s.frame(vec![]);
            let (tree, _) = s.frame(vec![]);
            for (_, n) in tree.nodes {
                if [
                    egui::accesskit::Role::Button,
                    egui::accesskit::Role::TextInput,
                    egui::accesskit::Role::CheckBox,
                ]
                .contains(&n.role())
                    && let Some(rect) = n.bounds()
                {
                    assert!(
                        rect.x0 >= -1. && rect.x1 <= f64::from(width * scale) + 1.,
                        "{language:?} {width} {scale} {:?} {rect:?}",
                        n.label()
                    );
                }
            }
        }
    }
}

#[test]
fn real_widgets_expose_focus_activation_ime_and_cancelled_save_generation() {
    let mut screen = Screen::new(Language::En);
    let (tree, _) = screen.frame(vec![]);
    let manual = target(&tree, "Manual settings");
    screen.frame(vec![activate(manual)]);
    assert_eq!(screen.window.page(), Page::Manual);
    let (manual_tree, _) = screen.frame(vec![]);
    let edit = manual_tree.focus;
    assert_eq!(
        manual_tree
            .nodes
            .iter()
            .find(|(id, _)| *id == edit)
            .unwrap()
            .1
            .role(),
        egui::accesskit::Role::TextInput
    );
    let (tabbed, _) = screen.frame(vec![press(Key::Tab, false)]);
    assert_ne!(tabbed.focus, edit);
    screen.frame(vec![press(Key::Tab, true)]);
    let (back, _) = screen.frame(vec![]);
    assert_eq!(back.focus, edit);
    let (_, work) = screen.frame(vec![
        Input::Ime(ImeEvent::Enabled),
        Input::Ime(ImeEvent::Preedit("東".into())),
        press(Key::Enter, false),
    ]);
    assert!(work.is_empty());
    screen.frame(vec![
        Input::Ime(ImeEvent::Commit("東京".into())),
        Input::Ime(ImeEvent::Disabled),
    ]);
    assert_eq!(screen.window.folders_mut().unwrap().input, "東京");
    let root = std::env::temp_dir();
    let folders = screen.window.folders_mut().unwrap();
    folders.input = root.join("exports").display().to_string();
    folders.outbox = root.join("drafts").display().to_string();
    let (current, _) = screen.frame(vec![]);
    let (_, work) = screen.frame(vec![activate(target(&current, "Save changes"))]);
    let [Work::Act(id, Job::Save { initial: true, .. })] = work.as_slice() else {
        panic!("expected a first save")
    };
    let (busy, _) = screen.frame(vec![]);
    let save = target(&busy, "Save changes");
    assert!(
        busy.nodes
            .iter()
            .find(|(id, _)| *id == save)
            .unwrap()
            .1
            .is_disabled()
    );
    screen.frame(vec![activate(target(&busy, "Cancel"))]);
    screen
        .window
        .accept(Event::Finished(*id, Ok(Outcome::Done)));
    let (overview, _) = screen.frame(vec![]);
    assert_eq!(screen.window.page(), Page::Overview);
    assert_eq!(overview.focus, target(&overview, "Manual settings"));
    assert!(
        !overview
            .nodes
            .iter()
            .any(|(_, n)| n.value().is_some_and(|v| v.starts_with("Settings saved")))
    );
}

#[test]
fn polling_and_failed_observation_preserve_an_in_progress_folder_edit() {
    let mut app = DesktopWindow::new(Language::En, Wording::Plain);
    app.accept(Event::Observed(Ok(DesktopState::default().into())));
    app.open_manual();
    app.folders_mut().unwrap().input = "Draft {literal} 東京".to_owned();
    app.accept(Event::Observed(Err(
        ironvellum_local::LocalError::StorageUnavailable,
    )));
    assert_eq!(app.page(), Page::Manual);
    assert_eq!(app.folders_mut().unwrap().input, "Draft {literal} 東京");
    app.accept(Event::Observed(Ok(DesktopState::default().into())));
    assert_eq!(app.folders_mut().unwrap().input, "Draft {literal} 東京");
}
