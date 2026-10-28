import tokens from './generated/tokens.json';
import accents from './generated/accents.json';
import chartPalette from './generated/chartPalette.json';
import {setWordsMode} from './i18n.js';
export function effectiveAppearance(phone, override = {}) {
  const mode = override.mode ?? phone?.mode ?? 'system';
  const light = mode === 'light' || (mode === 'system' && !!globalThis.matchMedia?.('(prefers-color-scheme: light)').matches);
  const choices = accents[light ? 'light' : 'dark'];
  const selected = choices.find(x => x.id === override.accentId);
  return {light, tokens:selected?.tokens ?? phone?.[light ? 'light' : 'dark'] ?? choices[0].tokens, themed:override.themed ?? phone?.themed ?? false};
}
export function applyAppearance(phone, override = {}) {
  const appearance = effectiveAppearance(phone,override);
  const root = document.documentElement;
  root.dataset.theme = appearance.light ? 'light' : 'dark';
  const palette = tokens[appearance.light ? 'light' : 'dark'];
  const paper = palette.paper;
  for (const [key,value] of Object.entries(paper)) if (typeof value === 'string') {
    const cssValue = key === 'Scrim' && value.length === 9 ? `#${value.slice(3)}${value.slice(1,3)}` : value;
    root.style.setProperty(`--paper-${key.replace(/[A-Z]/g,(c,i)=>(i?'-':'')+c.toLowerCase())}`,cssValue);
  }
  for (const [name,value] of Object.entries(palette.rarity)) root.style.setProperty(`--rarity-${name.toLowerCase()}`,value);
  for (const [name,ramp] of Object.entries(palette.metal)) root.style.setProperty(`--metal-${name.toLowerCase()}`,`linear-gradient(135deg, ${ramp.colors.map((color,i)=>`${color} ${ramp.stops[i]*100}%`).join(', ')})`);
  for (const [key,value] of Object.entries(appearance.tokens)) if (typeof value === 'string' && key !== 'preset') root.style.setProperty(`--${key.replace(/[A-Z]/g,c=>'-'+c.toLowerCase())}`,value);
  root.style.setProperty('--accent',appearance.tokens.fill);
  chartPalette[appearance.light ? 'light' : 'dark'].forEach((color,i)=>root.style.setProperty(`--series-${i}`,color));
  setWordsMode(appearance.themed);
  return appearance;
}
export {accents};
