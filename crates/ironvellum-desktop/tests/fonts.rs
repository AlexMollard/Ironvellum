use ab_glyph::{Font, FontRef, ScaleFont};
use ironvellum_desktop::{
    copy::{Catalog, Language, Wording},
    typography::{Weight, definitions, regional_face},
};
use sha2::{Digest, Sha256};
use ttf_parser::{Face, Tag};
#[path = "support/raster.rs"]
mod raster;

#[test]
fn preferred_han_outlines_match_original_region_at_weight_400() {
    let baseline: &[(Language, &[u8])] = &[
        (Language::Ja, include_bytes!("reference/JP.gray")),
        (Language::Ko, include_bytes!("reference/KR.gray")),
        (Language::ZhCn, include_bytes!("reference/SC.gray")),
    ];
    for (lang, expected) in baseline {
        let defs = definitions(*lang);
        let data = &defs.font_data[regional_face(*lang)].font;
        let face = FontRef::try_from_slice(data).unwrap();
        let output = [raster::raster(&face, '骨'), raster::raster(&face, '直')].concat();
        assert_eq!(expected.len(), output.len());
        let difference: u64 = output
            .iter()
            .zip(*expected)
            .map(|(x, y)| u64::from(x.abs_diff(*y)))
            .sum();
        let ink: u64 = expected.iter().map(|x| u64::from(*x)).sum();
        assert!(
            difference * 100 < 2 * ink,
            "{lang:?} error {difference}, ink {ink}"
        );
    }
    assert_ne!(baseline[0].1, baseline[2].1);
}

// Baselines from the complete cmap tables of the pinned original variable fonts.
// They cover every subtable byte, including format 14 variation selectors.
const ORIGINAL_CMAP: [(Language, &str); 3] = [
    (
        Language::Ja,
        "7632fe7940a0ef10c9d13cfa253c6746f27ffd784e1e0b4185794e627c5f8eb6",
    ),
    (
        Language::Ko,
        "4196d92ca7c8b6b1f682e7a7de52690770073eec87b6ecd2da3542d3dfb4a341",
    ),
    (
        Language::ZhCn,
        "f4ce789020ae9f282718f1c4ef0f6c3551fcd4b08035e008bb5733c2948b5403",
    ),
];

#[test]
fn regional_faces_retain_original_cmap_and_regular_metadata() {
    for (language, expected) in ORIGINAL_CMAP {
        let defs = definitions(language);
        let data = &defs.font_data[regional_face(language)].font;
        let face = Face::parse(data, 0).unwrap();
        let table = |tag| face.raw_face().table(Tag::from_bytes(tag));
        assert_eq!(
            format!("{:x}", Sha256::digest(table(b"cmap").unwrap())),
            expected
        );
        assert!(table(b"glyf").is_some() && table(b"loca").is_some());
        assert!(table(b"gvar").is_none() && table(b"fvar").is_none());
        assert_eq!(face.weight().to_number(), 400);
        for name in face.names() {
            if [1, 2, 3, 4, 6, 16, 17].contains(&name.name_id) {
                let label = name.to_string().unwrap();
                assert!(!label.contains("Thin") && !label.contains("Source"));
            }
        }
        for family in [egui::FontFamily::Proportional, egui::FontFamily::Monospace] {
            assert_eq!(defs.families[&family][1], regional_face(language));
        }
    }
}

#[test]
fn every_catalog_rasterizes_with_the_shipped_stack() {
    for language in Language::ALL {
        let fonts = definitions(language);
        for weight in [Weight::Medium, Weight::Semibold, Weight::Bold] {
            let stack = &fonts.families[&weight.family()];
            assert_eq!(&stack[1], regional_face(language));
            let faces: Vec<_> = stack
                .iter()
                .map(|name| FontRef::try_from_slice(&fonts.font_data[name].font).unwrap())
                .collect();
            for wording in [Wording::Plain, Wording::Themed] {
                let catalog = Catalog::new(language, wording);
                let chars: std::collections::BTreeSet<_> = catalog
                    .entries()
                    .flat_map(|(_, s)| s.chars())
                    .chain("Upper É e\u{301} / 東京 서울 骨直 简体字 · →".chars())
                    .filter(|c| !c.is_whitespace())
                    .collect();
                for c in chars {
                    let font = faces
                        .iter()
                        .find(|f| f.glyph_id(c).0 != 0)
                        .unwrap_or_else(|| panic!("{language:?} missing {c}"));
                    let glyph = font.as_scaled(32.0).scaled_glyph(c);
                    let mut ink = 0.0;
                    font.outline_glyph(glyph)
                        .unwrap_or_else(|| panic!("no outline {c}"))
                        .draw(|_, _, a| ink += a);
                    assert!(ink > 0.0, "no ink {c}");
                }
            }
        }
    }
}

#[test]
fn egui_layout_uses_bundled_families_for_mixed_script_text() {
    for language in [Language::Ja, Language::Ko, Language::ZhCn] {
        let ctx = egui::Context::default();
        ctx.set_fonts(definitions(language));
        let output = ctx.run(egui::RawInput::default(), |ctx| {
            egui::CentralPanel::default().show(ctx, |ui| {
                for family in [
                    Weight::Medium.family(),
                    Weight::Semibold.family(),
                    Weight::Bold.family(),
                    egui::FontFamily::Proportional,
                    egui::FontFamily::Monospace,
                ] {
                    let id = egui::FontId::new(22.0, family);
                    let text = "Upper 東京 / 서울 / 骨直 e\u{301} →";
                    ui.fonts_mut(|fonts| {
                        assert!(text.chars().all(|c| fonts.has_glyph(&id, c)));
                    });
                    let response = ui.label(egui::RichText::new(text).font(id));
                    assert!(response.rect.width() > 100.0);
                }
            });
        });
        assert!(!output.textures_delta.set.is_empty());
        assert!(
            !ctx.tessellate(output.shapes, output.pixels_per_point)
                .is_empty()
        );
    }
}
