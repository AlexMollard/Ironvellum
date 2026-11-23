use ironvellum_desktop::copy::{Catalog, Language, Wording};

#[test]
fn all_sixteen_catalogs_have_the_same_keys_and_named_fields() {
    let signature = |catalog: Catalog| {
        catalog
            .entries()
            .map(|(key, text)| {
                assert!(!text.trim().is_empty());
                let mut fields = Vec::new();
                let mut tail = text;
                while let Some((literal, after)) = tail.split_once('{') {
                    assert!(!literal.contains('}'));
                    let (field, remaining) = after.split_once('}').unwrap();
                    assert!(
                        field.chars().all(|c| c.is_ascii_alphanumeric() || c == '_')
                            && !field.is_empty()
                    );
                    fields.push(field);
                    tail = remaining;
                }
                assert!(!tail.contains('}'));
                fields.sort_unstable();
                (key, fields)
            })
            .collect::<Vec<_>>()
    };
    let expected = signature(Catalog::new(Language::En, Wording::Plain));
    for language in Language::ALL {
        assert_eq!(Language::explicit(language.tag()), Some(language));
        for wording in [Wording::Plain, Wording::Themed] {
            let copy = Catalog::new(language, wording);
            assert_eq!(signature(copy), expected);
            assert!(
                copy.text("agent_request")
                    .unwrap()
                    .contains("ironvellum-connector")
            );
        }
    }
}

#[test]
fn locale_aliases_and_untrusted_substitution_match_existing_behavior() {
    for (input, expected) in [
        ("Portuguese_Brazil", Language::PtBr),
        ("Chinese (Simplified)_China", Language::ZhCn),
        ("English_US", Language::En),
        ("ja_JP", Language::Ja),
        ("pt-PT", Language::En),
        ("zh-TW", Language::En),
    ] {
        assert_eq!(Language::for_system(input), expected);
    }
    let copy = Catalog::new(Language::En, Wording::Plain);
    assert_eq!(
        copy.format("connections", &[("count", "{verified}"), ("verified", "2")])
            .unwrap(),
        "Sessions: {verified} · Verified: 2"
    );
    assert!(copy.format("connections", &[("count", "1")]).is_err());
}
