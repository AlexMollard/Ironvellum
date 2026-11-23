//! Generate independent region/weight reference pixels from a pinned original.
//! cargo run --example qualify_font -- <variable.ttf> <reference.gray>
use ab_glyph::{FontRef, VariableFont};
#[path = "../tests/support/raster.rs"]
mod raster;

fn main() {
    let args: Vec<_> = std::env::args_os().skip(1).collect();
    assert_eq!(args.len(), 2);
    let bytes = std::fs::read(&args[0]).unwrap();
    let mut face = FontRef::try_from_slice(&bytes).unwrap();
    assert_eq!(face.variations()[0].default_value, 100.0);
    let thin = raster::raster(&face, '骨');
    assert!(face.set_variation(b"wght", 400.0));
    let regular = raster::raster(&face, '骨');
    let ink = |image: &[u8]| image.iter().map(|x| u64::from(*x)).sum::<u64>();
    assert!(ink(&regular) > ink(&thin) * 13 / 10);
    println!(
        "骨 at weight 100, 400: {}, {} ink units",
        ink(&thin),
        ink(&regular)
    );
    let bytes = [regular, raster::raster(&face, '直')].concat();
    std::fs::write(&args[1], bytes).unwrap();
}
