//! Explicit no-window GPU acceptance. Pass an empty output directory and
//! optionally --measure-only to retain the manifest without the curated PNGs.
mod checks;
mod fixtures;
mod gpu;
use egui::{
    Event as Input, Key, Modifiers,
    accesskit::{Action, ActionRequest, TreeUpdate},
};
use ironvellum_desktop::{
    copy::{Catalog, Language, Wording},
    typography,
    window::DesktopWindow,
    worker::Event,
};
use ironvellum_local::{LocalError, desktop_model::DesktopState};
use serde_json::{Value, json};
use sha2::{Digest, Sha256};
use std::{collections::BTreeMap, fs, path::Path, sync::Arc};

#[derive(Clone, Copy)]
struct Case {
    scene: &'static str,
    dark: bool,
    lang: Language,
    wording: Wording,
    points: [u32; 2],
    scale: f32,
    save: bool,
}
impl Case {
    fn name(self) -> String {
        format!(
            "{}-{}-{}-{}",
            self.scene,
            if self.dark { "dark" } else { "vellum" },
            self.lang.tag(),
            if self.wording == Wording::Plain {
                "plain"
            } else {
                "themed"
            }
        )
    }
}
fn cases() -> Vec<Case> {
    let mut result = Vec::new();
    for dark in [true, false] {
        for scene in [
            "overview",
            "manual-long",
            "manual",
            "unconfigured",
            "awaiting",
            "detached",
            "recovery",
            "choice",
            "choice-switched",
            "choice-focus",
            "keep",
            "busy",
            "stale",
            "mixed",
            "draft-error",
            "observer-error",
            "restored",
            "focus",
            "short",
            "narrow",
        ] {
            let (points, scale) = match scene {
                "manual-long" => ([395, 380], 1.5),
                "short" => ([690, 270], 1.5),
                "narrow" => ([330, 560], 1.5),
                "mixed" | "stale" | "draft-error" => ([850, 900], 1.),
                "manual" | "choice" | "choice-switched" | "choice-focus" | "keep" => {
                    ([780, 470], 1.)
                }
                _ => ([780, 680], 1.),
            };
            result.push(Case {
                scene,
                dark,
                lang: Language::En,
                wording: Wording::Plain,
                points,
                scale,
                save: matches!(
                    scene,
                    "overview" | "manual" | "recovery" | "choice" | "choice-focus"
                ) || dark && matches!(scene, "focus" | "mixed")
                    || !dark && scene == "narrow",
            });
        }
        for lang in Language::ALL {
            for wording in [Wording::Plain, Wording::Themed] {
                result.push(Case {
                    scene: "localized",
                    dark,
                    lang,
                    wording,
                    points: [860, 820],
                    scale: 1.25,
                    save: matches!(
                        (lang, wording, dark),
                        (Language::Ja, Wording::Plain, true)
                            | (Language::Ko, Wording::Themed, false)
                            | (Language::ZhCn, Wording::Themed, true)
                    ),
                });
            }
        }
    }
    result
}
struct Frame {
    output: egui::FullOutput,
    tree: TreeUpdate,
}
struct Screen {
    ctx: egui::Context,
    window: DesktopWindow,
    time: f64,
    case: Case,
}
impl Screen {
    fn frame(&mut self, events: Vec<Input>) -> Frame {
        self.time += 0.05;
        let size = self.case.points;
        let mut input = egui::RawInput {
            time: Some(self.time),
            screen_rect: Some(egui::Rect::from_min_size(
                egui::Pos2::ZERO,
                egui::vec2(size[0] as f32, size[1] as f32),
            )),
            events,
            ..Default::default()
        };
        input
            .viewports
            .get_mut(&egui::ViewportId::ROOT)
            .unwrap()
            .native_pixels_per_point = Some(self.case.scale);
        let mut out = self.ctx.run(input, |ctx| {
            let _ = self.window.render(ctx);
        });
        let tree = out
            .platform_output
            .accesskit_update
            .take()
            .expect("semantic tree");
        Frame { output: out, tree }
    }
}
fn click(tree: &TreeUpdate, label: &str) -> Input {
    let id = tree
        .nodes
        .iter()
        .find(|(_, n)| n.label() == Some(label))
        .unwrap_or_else(|| panic!("missing {label}"))
        .0;
    Input::AccessKitActionRequest(ActionRequest {
        action: Action::Click,
        target: id,
        data: None,
    })
}
fn picture(
    gpu: &gpu::Gpu,
    states: &BTreeMap<&str, Arc<DesktopState>>,
    case: Case,
    out: &Path,
    retain: bool,
) -> Value {
    let c = Catalog::new(case.lang, case.wording);
    let ctx = egui::Context::default();
    ctx.enable_accesskit();
    ctx.set_fonts(typography::definitions(case.lang));
    ctx.set_theme(if case.dark {
        egui::ThemePreference::Dark
    } else {
        egui::ThemePreference::Light
    });
    let mut screen = Screen {
        ctx,
        window: DesktopWindow::new(case.lang, case.wording),
        time: 0.,
        case,
    };
    let name = case.name();
    let state = match case.scene {
        "manual-long" | "manual" | "busy" | "focus" | "observer-error" | "restored" | "short" => {
            "overview"
        }
        "localized" | "narrow" => "mixed",
        "choice-switched" | "choice-focus" => "choice",
        x => x,
    };
    if state == "keep" {
        screen
            .window
            .accept(Event::Observed(Ok(states["overview"].clone())));
    }
    screen
        .window
        .accept(Event::Observed(Ok(states[state].clone())));
    if matches!(case.scene, "manual" | "manual-long") {
        screen.window.open_manual();
        let fields = screen.window.folders_mut().unwrap();
        // Staged display-only paths keep machine usernames and random temp names out of evidence.
        fields.input = r"C:\Training\Ironvellum\Exports".into();
        fields.outbox = r"C:\Training\Ironvellum\Drafts".into();
        if case.scene == "manual-long" {
            fields.input = "/東京/훈련/Épreuves & {archives}/".repeat(6);
            fields.outbox = "/서울/計画/Prêts & {drafts}/".repeat(6);
        }
    }
    if matches!(case.scene, "observer-error" | "restored") {
        screen
            .window
            .accept(Event::Observed(Err(LocalError::StorageUnavailable)));
    }

    let mut renderer = gpu.renderer();
    let px = case.points.map(|p| (p as f32 * case.scale).round() as u32);
    let mut warm = |screen: &mut Screen, events: Vec<Input>| {
        let frame = screen.frame(events);
        gpu.draw(&mut renderer, &screen.ctx, frame.output, px, false);
        frame.tree
    };
    let mut tree = warm(&mut screen, vec![]);
    if case.scene == "restored" {
        screen
            .window
            .accept(Event::Observed(Ok(states["overview"].clone())));
    }
    if matches!(
        case.scene,
        "choice" | "choice-switched" | "choice-focus" | "keep" | "busy"
    ) {
        let key = if case.scene == "busy" {
            "setup_copy"
        } else {
            "source_heading"
        };
        warm(&mut screen, vec![click(&tree, c.text(key).unwrap())]);
        if matches!(case.scene, "choice-switched" | "choice-focus") {
            tree = warm(&mut screen, vec![]);
            warm(
                &mut screen,
                vec![click(&tree, c.text("other_archive").unwrap())],
            );
        }
    }
    if matches!(case.scene, "focus" | "choice-focus" | "choice-switched") {
        tree = warm(&mut screen, vec![Input::PointerGone]);
        let label = match case.scene {
            "focus" => "setup_copy",
            "choice-focus" => "keep",
            _ => "confirm",
        };
        let target = tree
            .nodes
            .iter()
            .find(|(_, n)| n.label() == Some(c.text(label).unwrap()))
            .unwrap()
            .0;
        for _ in 0..32 {
            if tree.focus == target {
                break;
            }
            warm(
                &mut screen,
                vec![Input::Key {
                    key: Key::Tab,
                    physical_key: None,
                    pressed: true,
                    repeat: false,
                    modifiers: Modifiers::NONE,
                }],
            );
            tree = warm(&mut screen, vec![]);
        }
        assert_eq!(tree.focus, target, "keyboard focus target");
    }
    warm(&mut screen, vec![]);
    let frame = screen.frame(vec![]);
    assert_eq!(frame.output.pixels_per_point, case.scale);
    let layout = checks::layout(case, c, &frame);
    let rgba = gpu
        .draw(&mut renderer, &screen.ctx, frame.output, px, true)
        .unwrap();
    let colors = checks::pixels(case, &rgba, px, &frame.tree, c);
    let png_name = (case.save && retain).then(|| format!("{name}.png"));
    if let Some(file) = &png_name {
        let mut enc = png::Encoder::new(
            std::io::BufWriter::new(fs::File::create(out.join(file)).unwrap()),
            px[0],
            px[1],
        );
        enc.set_color(png::ColorType::Rgba);
        enc.set_depth(png::BitDepth::Eight);
        enc.write_header().unwrap().write_image_data(&rgba).unwrap();
    }
    println!("PASS {name} {}x{} @{}", px[0], px[1], case.scale);
    json!({"case":name,"fixture":state,"points":case.points,"pixels":px,"scale":case.scale,"png":png_name,"rgba_sha256":format!("{:x}",Sha256::digest(&rgba)),"layout":layout,"pixels_checked":colors})
}
fn main() {
    let mut args = std::env::args_os().skip(1);
    let out = args.next().expect("pass an empty artifact directory");
    let out = Path::new(&out);
    let mode = args.next();
    assert!(mode.is_none() || mode.as_deref() == Some("--measure-only".as_ref()));
    assert!(args.next().is_none());
    fs::create_dir_all(out).unwrap();
    assert!(
        fs::read_dir(out).unwrap().next().is_none(),
        "use an empty output directory"
    );
    let gpu = gpu::Gpu::new();
    let info = &gpu.info;
    println!(
        "Adapter {} / {:?} / {:?} / {} {}",
        info.name, info.backend, info.device_type, info.driver, info.driver_info
    );
    let states = fixtures::observe_states();
    let captures: Vec<_> = cases()
        .into_iter()
        .map(|c| picture(&gpu, &states, c, out, mode.is_none()))
        .collect();
    let manifest = json!({"format_version":1,"command":"cargo run --locked --offline -p ironvellum-desktop --example render_desktop -- OUTPUT [--measure-only]","os":std::env::consts::OS,"adapter":{"name":info.name,"backend":format!("{:?}",info.backend),"device_type":format!("{:?}",info.device_type),"driver":info.driver,"driver_info":info.driver_info},"render_target":"Rgba8Unorm","view":"ironvellum_desktop::window::DesktopWindow::render","fonts":"Packaged Chakra Petch Medium/Semibold/Bold; regional Noto Sans JP/KR/SC Regular; egui symbol fallbacks. See crates/ironvellum-desktop/assets/fonts/PROVENANCE.md.","captures":captures});
    fs::write(
        out.join(format!("manifest-{}.json", std::env::consts::OS)),
        serde_json::to_vec_pretty(&manifest).unwrap(),
    )
    .unwrap();
}
