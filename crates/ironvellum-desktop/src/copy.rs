//! Offline text catalogs for desktop windows and setup instructions.
use std::{collections::BTreeMap, fmt, sync::LazyLock};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Language {
    En,
    De,
    Es,
    Fr,
    Ja,
    Ko,
    PtBr,
    ZhCn,
}

impl Language {
    pub const ALL: [Self; 8] = [
        Self::En,
        Self::De,
        Self::Es,
        Self::Fr,
        Self::Ja,
        Self::Ko,
        Self::PtBr,
        Self::ZhCn,
    ];

    pub fn tag(self) -> &'static str {
        match self {
            Self::En => "en",
            Self::De => "de",
            Self::Es => "es",
            Self::Fr => "fr",
            Self::Ja => "ja",
            Self::Ko => "ko",
            Self::PtBr => "pt-BR",
            Self::ZhCn => "zh-CN",
        }
    }

    /// An explicit argument must match a supported locale exactly.
    pub fn explicit(tag: &str) -> Option<Self> {
        Self::ALL.into_iter().find(|lang| lang.tag() == tag)
    }

    pub fn for_system(name: &str) -> Self {
        let name = name.replace('_', "-").to_ascii_lowercase();
        match name.as_str() {
            "zh" | "zh-cn" | "chinese-china" | "chinese (simplified)-china" | "zh-hans-cn" => {
                Self::ZhCn
            }
            "pt" | "pt-br" | "portuguese-brazil" => Self::PtBr,
            _ => match name.split('-').next().unwrap_or_default() {
                "de" | "german" => Self::De,
                "es" | "spanish" => Self::Es,
                "fr" | "french" => Self::Fr,
                "ja" | "japanese" => Self::Ja,
                "ko" | "korean" => Self::Ko,
                _ => Self::En,
            },
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Wording {
    Plain,
    Themed,
}

type Messages = BTreeMap<String, String>;
type Modes = BTreeMap<String, Messages>;
static RESOURCES: LazyLock<[Modes; 8]> = LazyLock::new(|| {
    macro_rules! catalogs { ($($tag:literal),*) => {
        [$(serde_json::from_str(include_str!(concat!("../assets/locales/", $tag, ".json"))).expect("validated desktop catalog")),*]
    }; }
    catalogs!("en", "de", "es", "fr", "ja", "ko", "pt-BR", "zh-CN")
});

#[derive(Clone, Copy)]
pub struct Catalog(&'static Messages);

#[derive(Debug, PartialEq, Eq)]
pub enum CopyError {
    UnknownMessage,
    MissingValue,
    InvalidTemplate,
}
impl fmt::Display for CopyError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{self:?}")
    }
}
impl std::error::Error for CopyError {}

impl Catalog {
    pub fn new(language: Language, wording: Wording) -> Self {
        Self(
            &RESOURCES[language as usize][match wording {
                Wording::Plain => "plain",
                Wording::Themed => "themed",
            }],
        )
    }
    pub fn entries(self) -> impl Iterator<Item = (&'static str, &'static str)> {
        self.0.iter().map(|(k, v)| (k.as_str(), v.as_str()))
    }
    pub fn text(self, key: &str) -> Result<&'static str, CopyError> {
        self.0
            .get(key)
            .map(String::as_str)
            .ok_or(CopyError::UnknownMessage)
    }
    /// Each named field is replaced in the template only. Paths and filenames can contain braces.
    pub fn format(self, key: &str, values: &[(&str, &str)]) -> Result<String, CopyError> {
        let mut template = self.text(key)?;
        let mut result = String::with_capacity(template.len());
        while let Some((literal, rest)) = template.split_once('{') {
            if literal.contains('}') {
                return Err(CopyError::InvalidTemplate);
            }
            let (field, tail) = rest.split_once('}').ok_or(CopyError::InvalidTemplate)?;
            let value = values
                .iter()
                .find(|(k, _)| *k == field)
                .ok_or(CopyError::MissingValue)?;
            result.push_str(literal);
            result.push_str(value.1);
            template = tail;
        }
        if template.contains('}') {
            return Err(CopyError::InvalidTemplate);
        }
        result.push_str(template);
        Ok(result)
    }
}
