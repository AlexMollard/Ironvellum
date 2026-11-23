//! Bundled regional faces and fixed Chakra Petch weights for all desktop text.
use crate::copy::Language;
use egui::{FontData, FontDefinitions, FontFamily};

#[derive(Clone, Copy, Debug)]
pub enum Weight {
    Medium,
    Semibold,
    Bold,
}
impl Weight {
    fn name(self) -> &'static str {
        match self {
            Self::Medium => "Chakra Petch Medium",
            Self::Semibold => "Chakra Petch Semibold",
            Self::Bold => "Chakra Petch Bold",
        }
    }
    pub fn family(self) -> FontFamily {
        FontFamily::Name(self.name().into())
    }
}

const JP: &str = "Noto Sans JP Regular";
const KR: &str = "Noto Sans KR Regular";
const SC: &str = "Noto Sans SC Regular";

fn regional_order(language: Language) -> [&'static str; 3] {
    match language {
        Language::Ja => [JP, SC, KR],
        Language::Ko => [KR, SC, JP],
        _ => [SC, JP, KR],
    }
}
pub fn regional_face(language: Language) -> &'static str {
    regional_order(language)[0]
}

pub fn definitions(language: Language) -> FontDefinitions {
    let mut fonts = FontDefinitions::default();
    let weights = [
        (
            Weight::Medium,
            include_bytes!("../assets/fonts/chakra_petch_medium.ttf").as_slice(),
        ),
        (
            Weight::Semibold,
            include_bytes!("../assets/fonts/chakra_petch_semibold.ttf").as_slice(),
        ),
        (
            Weight::Bold,
            include_bytes!("../assets/fonts/chakra_petch_bold.ttf").as_slice(),
        ),
    ];
    for (name, bytes) in [
        (
            JP,
            include_bytes!("../assets/fonts/NotoSansJP-Regular.ttf").as_slice(),
        ),
        (
            KR,
            include_bytes!("../assets/fonts/NotoSansKR-Regular.ttf").as_slice(),
        ),
        (
            SC,
            include_bytes!("../assets/fonts/NotoSansSC-Regular.ttf").as_slice(),
        ),
    ] {
        fonts
            .font_data
            .insert(name.to_owned(), FontData::from_static(bytes).into());
    }
    let regions = regional_order(language);
    let stack = |weight: Weight, defaults: &[String]| {
        std::iter::once(weight.name())
            .chain(regions)
            .map(str::to_owned)
            .chain(defaults.iter().cloned())
            .collect::<Vec<_>>()
    };
    for (weight, bytes) in weights {
        fonts.font_data.insert(
            weight.name().to_owned(),
            FontData::from_static(bytes).into(),
        );
        let names = stack(weight, &fonts.families[&FontFamily::Proportional]);
        fonts.families.insert(weight.family(), names);
    }
    for family in [FontFamily::Proportional, FontFamily::Monospace] {
        let names = stack(Weight::Medium, &fonts.families[&family]);
        fonts.families.insert(family, names);
    }
    fonts
}
