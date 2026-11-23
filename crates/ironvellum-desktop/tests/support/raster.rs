use ab_glyph::{Font, ScaleFont, point};

// Fixed baseline, glyph origin, size and scale. Keep blank margins so that
// differences in bearings and outlines are both visible in the comparison.
pub fn raster(font: &impl Font, c: char) -> Vec<u8> {
    let mut glyph = font.as_scaled(66.0).scaled_glyph(c);
    glyph.position = point(8.0, 64.0);
    let outline = font.outline_glyph(glyph).unwrap();
    let bounds = outline.px_bounds();
    let mut image = vec![0; 80 * 80];
    outline.draw(|x, y, alpha| {
        let x = x as i32 + bounds.min.x as i32;
        let y = y as i32 + bounds.min.y as i32;
        assert!((0..80).contains(&x) && (0..80).contains(&y));
        image[(80 * y + x) as usize] = (alpha * 255.0).round() as u8;
    });
    image
}
