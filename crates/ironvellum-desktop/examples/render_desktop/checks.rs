use super::{Case, Frame};
use egui::accesskit::{Role, TreeUpdate};
use ironvellum_desktop::copy::Catalog;
use serde_json::{Value, json};

fn has(tree: &TreeUpdate, text: &str) -> bool {
    tree.nodes
        .iter()
        .any(|(_, n)| n.label() == Some(text) || n.value() == Some(text))
}
pub(super) fn layout(case: Case, c: Catalog, frame: &Frame) -> Value {
    let tree = &frame.tree;
    for key in ["setup_copy", "save_changes", "retry", "confirm"] {
        let caption = c.text(key).unwrap();
        let node = tree
            .nodes
            .iter()
            .find(|(_, n)| n.role() == Role::Button && n.label() == Some(caption))
            .and_then(|(_, n)| n.bounds());
        let paint = frame.output.shapes.iter().find_map(|s| match &s.shape {
            egui::Shape::Text(t) if t.galley.text() == caption => Some(t),
            _ => None,
        });
        if let (Some(b), Some(t)) = (node, paint) {
            let center = t.galley.rect.translate(t.pos.to_vec2()).center().x;
            assert!(
                (f64::from(center) - (b.x0 + b.x1) / 2.).abs() <= 2.,
                "{} action caption {caption} is off-center",
                case.name()
            );
        }
    }
    let required: &[&str] = match case.scene {
        "manual" | "manual-long" => &["manual", "exports", "save_changes"],
        "choice" | "choice-switched" | "choice-focus" | "keep" => {
            &["source_heading", "keep", "other_archive", "confirm"]
        }
        "unconfigured" => &["unconfigured", "agent_hint"],
        "awaiting" => &["ready_to_connect", "no_training"],
        "detached" => &["disconnected", "available"],
        "recovery" => &["connected", "last_valid", "unreadable", "retry"],
        "busy" => &["working"],
        "observer-error" | "stale" => &["session_problem", "last_observed"],
        "mixed" | "localized" => &["connected", "available", "reload"],
        "draft-error" => &["drafts_problem"],
        "narrow" | "short" => &["companion"],
        _ => &["companion", "connected", "loaded", "drafts"],
    };
    for key in required {
        assert!(
            has(tree, c.text(key).unwrap()),
            "{} missing semantic {key}",
            case.name()
        );
    }
    let mut visible_controls = 0;
    for (_, n) in &tree.nodes {
        if matches!(
            n.role(),
            Role::Button | Role::TextInput | Role::RadioButton | Role::CheckBox | Role::Link
        ) && let Some(b) = n.bounds()
            && b.y0 >= 0.
            && b.y0 < f64::from(case.points[1])
        {
            assert!(
                b.x0 >= -1. && b.x1 <= f64::from(case.points[0]) + 1.,
                "{} clipped control {:?} {b:?}",
                case.name(),
                n.label()
            );
            visible_controls += 1;
        }
    }
    let panel_rects: Vec<_> = frame
        .output
        .shapes
        .iter()
        .filter_map(|s| match &s.shape {
            egui::Shape::Path(p) if p.points.len() == 8 && p.fill != egui::Color32::TRANSPARENT => {
                Some(egui::Rect::from_points(&p.points))
            }
            _ => None,
        })
        .collect();
    assert!(!panel_rects.is_empty());
    let mut clipped_text = 0;
    for s in &frame.output.shapes {
        if let egui::Shape::Text(t) = &s.shape {
            let rect = t.visual_bounding_rect();
            if case.scene == "manual-long"
                && (t.galley.job.text.starts_with("/東京/")
                    || t.galley.job.text.starts_with("/서울/"))
            {
                // Single-line path editors intentionally scroll their literal contents.
                continue;
            }
            if rect.center().y >= 0.
                && rect.center().y < case.points[1] as f32
                && (rect.left() < s.clip_rect.left() - 2.
                    || rect.right() > s.clip_rect.right() + 2.)
            {
                clipped_text += 1;
            }
        }
    }
    assert_eq!(
        clipped_text,
        0,
        "{} has unexpectedly clipped horizontal text",
        case.name()
    );
    if matches!(case.scene, "overview" | "restored") {
        let node = |k| {
            tree.nodes
                .iter()
                .find(|(_, n)| {
                    n.value() == Some(c.text(k).unwrap()) || n.label() == Some(c.text(k).unwrap())
                })
                .unwrap()
                .1
                .bounds()
                .unwrap()
        };
        let left = node("loaded");
        let right = node("drafts");
        assert!(left.x1 < right.x0 && (left.y0 - right.y0).abs() < 4.);
        let enclosing = |x: f64, y: f64| {
            panel_rects
                .iter()
                .find(|r| r.contains(egui::pos2(x as f32, y as f32)))
                .unwrap()
        };
        assert!(
            (enclosing(left.x0, left.y0).bottom() - enclosing(right.x0, right.y0).bottom()).abs()
                < 3.
        );
    }
    if case.scene == "recovery" {
        assert!(!has(tree, c.text("drafts").unwrap()));
        let title = |k| {
            tree.nodes
                .iter()
                .find(|(_, n)| {
                    n.value() == Some(c.text(k).unwrap()) || n.label() == Some(c.text(k).unwrap())
                })
                .unwrap()
                .1
                .bounds()
                .unwrap()
                .y0
        };
        let panel = |y: f64| {
            *panel_rects
                .iter()
                .find(|r| {
                    r.width() > case.points[0] as f32 * 0.88
                        && r.top() < y as f32
                        && r.bottom() > y as f32
                })
                .unwrap()
        };
        let above = panel(title("last_valid"));
        let below = panel(title("unreadable"));
        assert!(below.top() >= above.bottom() && below.top() - above.bottom() < 30.);
    }
    json!({"visible_controls":visible_controls,"painted_chamfers":panel_rects.len(),"horizontal_text_clip_failures":clipped_text,"expected_semantics":required})
}
fn close(p: &[u8], color: [u8; 3]) -> bool {
    p[..3].iter().zip(color).all(|(a, b)| a.abs_diff(b) <= 2)
}
pub(super) fn pixels(
    case: Case,
    rgba: &[u8],
    size: [u32; 2],
    tree: &TreeUpdate,
    c: Catalog,
) -> Value {
    let [paper, card, ink, accent] = if case.dark {
        [[12, 12, 11], [23, 23, 21], [232, 232, 228], [52, 211, 153]]
    } else {
        [
            [247, 243, 234],
            [254, 251, 245],
            [36, 27, 21],
            [32, 128, 70],
        ]
    };
    let mut counts = [0usize; 4];
    for p in rgba.chunks_exact(4) {
        assert_eq!(p[3], 255, "unpainted alpha");
        assert_ne!(&p[..3], &[255, 0, 255], "unpainted clear color");
        for (i, color) in [paper, card, ink, accent].into_iter().enumerate() {
            if close(p, color) {
                counts[i] += 1;
            }
        }
    }
    assert_eq!(rgba.len(), (size[0] * size[1] * 4) as usize);
    assert!(
        counts[0] > 1000 && counts[1] > 1000 && counts[2] > 25,
        "{} semantic pixel counts {counts:?}",
        case.name()
    );
    if case.scene != "busy" {
        assert!(counts[3] > 4, "{} emerald absent", case.name());
    }
    if case.scene == "focus" {
        let n = &tree
            .nodes
            .iter()
            .find(|(_, n)| n.label() == Some(c.text("setup_copy").unwrap()))
            .unwrap();
        assert_eq!(tree.focus, n.0);
        let r = n.1.bounds().unwrap();
        let x = ((r.x0 + r.x1) / 2. * f64::from(case.scale)).round() as u32;
        let y = (r.y0 * f64::from(case.scale)).round() as u32;
        let at = |x, y| {
            let i = ((size[0] * y + x) * 4) as usize;
            &rgba[i..i + 4]
        };
        assert!(
            (y - 5..y).any(|sy| close(at(x, sy), ink)),
            "{} focus ink missing in readback",
            case.name()
        );
        assert!(close(at(x, y + 5), accent));
    }
    let mut result = json!({"paper":counts[0],"card":counts[1],"ink":counts[2],"emerald":counts[3],"opaque_pixels":size[0]*size[1]});
    if matches!(case.scene, "choice" | "choice-switched" | "choice-focus") {
        let pixel = |x: u32, y: u32| {
            let pos = ((y * size[0] + x) * 4) as usize;
            &rgba[pos..pos + 3]
        };
        for key in ["keep", "other_archive"] {
            let radio = &tree
                .nodes
                .iter()
                .find(|(_, n)| n.label() == Some(c.text(key).unwrap()))
                .unwrap()
                .1;
            assert_eq!(radio.role(), Role::RadioButton);
            assert!(!radio.is_disabled());
            let selected = (key == "keep") == (case.scene == "choice");
            assert_eq!(
                radio.toggled(),
                Some(if selected {
                    egui::accesskit::Toggled::True
                } else {
                    egui::accesskit::Toggled::False
                })
            );
            let rect = radio.bounds().unwrap();
            let x = rect.x0.round() as u32;
            let y = ((rect.y0 + rect.y1) / 2.).floor() as u32;
            let samples: Vec<_> = (x - 3..x + 18).map(|i| pixel(i, y)).collect();
            if selected {
                assert!(samples.iter().any(|p| close(p, accent)));
            } else {
                let luminance = |p: &[u8]| {
                    p.iter()
                        .zip([0.2126, 0.7152, 0.0722])
                        .map(|(&v, w)| {
                            let v = f64::from(v) / 255.;
                            w * if v <= 0.04045 {
                                v / 12.92
                            } else {
                                ((v + 0.055) / 1.055).powf(2.4)
                            }
                        })
                        .sum::<f64>()
                };
                let base = luminance(&card);
                let contrast = |p: &&[u8]| {
                    let l = luminance(p);
                    (base.max(l) + 0.05) / (base.min(l) + 0.05)
                };
                let left = samples[..8].iter().map(contrast).fold(0., f64::max);
                let right = samples[13..].iter().map(contrast).fold(0., f64::max);
                assert!(
                    left.min(right) >= 3.,
                    "{} unselected {key} outline {left:.2}/{right:.2}",
                    case.name()
                );
                assert!(close(samples[10], card));
                if case.scene == "choice-focus" {
                    assert!(
                        samples[..8].iter().any(|p| close(p, ink))
                            && samples[13..].iter().any(|p| close(p, ink)),
                        "{} missing keyboard-focus ink on both radio edges",
                        case.name()
                    );
                }
                result["unselected_outline_min_contrast"] = json!(left.min(right));
            }
        }
    }
    result
}
