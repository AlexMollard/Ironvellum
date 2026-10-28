import { it, expect } from 'vitest';
import palette from '../src/generated/chartPalette.json';
import tokens from '../src/generated/tokens.json';
import { applyAppearance } from '../src/theme.js';
// The thresholds of the dataviz validator that need no colour-vision simulation (that part was run with validate_palette.js; see the _note in the palette file).
const lin = c => { c /= 255; return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4; };
const rgb = hex => [1, 3, 5].map(i => lin(parseInt(hex.slice(i, i + 2), 16)));
const lum = hex => { const [r, g, b] = rgb(hex); return 0.2126 * r + 0.7152 * g + 0.0722 * b; };
const contrast = (a, b) => { const [hi, lo] = [lum(a), lum(b)].sort((x, y) => y - x); return (hi + 0.05) / (lo + 0.05); };
function oklab(hex) {
  const [r, g, b] = rgb(hex), l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b), m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b), s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
  return [0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s, 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s, 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s];
}
const deltaE = (a, b) => 100 * Math.hypot(...oklab(a).map((v, i) => v - oklab(b)[i]));
for (const [mode, band] of [['dark', [0.48, 0.67]], ['light', [0.43, 0.77]]]) {
  it(`the ${mode} chart palette keeps the validator's lightness band, chroma floor, 3:1 contrast and neighbour separation on the card surface`, () => {
    const colours = palette[mode], surface = tokens[mode].paper.Vault;
    expect(colours).toHaveLength(8); expect(new Set(colours).size).toBe(8);
    for (const colour of colours) {
      const [L, a, b] = oklab(colour); expect(L).toBeGreaterThanOrEqual(band[0]); expect(L).toBeLessThanOrEqual(band[1]);
      expect(Math.hypot(a, b)).toBeGreaterThanOrEqual(0.1); expect(contrast(colour, surface)).toBeGreaterThanOrEqual(3);
    }
    for (let i = 1; i < colours.length; i++) expect(deltaE(colours[i - 1], colours[i])).toBeGreaterThanOrEqual(15);
  });
}
it('the page takes its series colours from the palette for the theme in use', () => {
  for (const mode of ['dark', 'light']) { applyAppearance({ mode }); palette[mode].forEach((colour, i) => expect(document.documentElement.style.getPropertyValue(`--series-${i}`)).toBe(colour)); }
  applyAppearance({ mode: 'dark' });
});
