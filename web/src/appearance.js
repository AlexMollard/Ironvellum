import {applyAppearance, accents} from './theme.js';
import {createElement, Palette} from 'lucide';
import {t} from './i18n.js';
const KEY = 'ironvellum.appearance';
export function loadAppearance() { try { return JSON.parse(localStorage.getItem(KEY)) || {}; } catch { return {}; } }
export function saveAppearance(value) { try { localStorage.setItem(KEY,JSON.stringify(value)); } catch {} }
export function clearAppearance() { try { localStorage.removeItem(KEY); } catch {} }
const el = (tag, text, className) => { const node = document.createElement(tag); if (text != null) node.textContent = text; if (className) node.className = className; return node; };
const ARROWS = {ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1};
let serial = 0;
/** The "Aa" button and its popover: Theme and Names are radio groups, Accent is a row of two-tone swatches (also radios). Keys: arrows move and select, Escape closes, a click outside closes. */
export function createAppearanceMenu(phone, onChange = () => {}) {
  let override = loadAppearance(), open = false;
  const id = `appearance-${++serial}`;
  const wrap = el('div', null, 'appearance');
  const button = el('button', null, 'appearance-button'); button.type = 'button';
  const icon = el('span', null, 'appearance-icon'); icon.append(createElement(Palette, {width: 18, height: 18, 'stroke-width': 1.75, 'aria-hidden': 'true'}));
  button.append(icon, el('span', 'Appearance', 'appearance-button-label'));
  button.setAttribute('aria-label', 'Appearance'); button.setAttribute('aria-haspopup', 'dialog'); button.setAttribute('aria-expanded', 'false'); button.setAttribute('aria-controls', id);
  const panel = el('div', null, 'appearance-menu'); panel.id = id; panel.hidden = true; panel.setAttribute('role', 'dialog'); panel.setAttribute('aria-label', 'Appearance');
  wrap.append(button, panel);
  // Radios in a group: one tab stop (the checked one), arrows move the selection, as native radio buttons do.
  function radios(group, label, entries, value, change, className, build) {
    const box = el('div', null, className); box.setAttribute('role', 'radiogroup'); box.setAttribute('aria-label', label);
    const checkedIndex = Math.max(0, entries.findIndex(([entry]) => entry === value));
    entries.forEach(([entry, name], index) => {
      const radio = build(entry, name); radio.type = 'button'; radio.setAttribute('role', 'radio'); radio.dataset.group = group; radio.dataset.value = entry;
      radio.setAttribute('aria-checked', String(entry === value)); radio.tabIndex = index === checkedIndex ? 0 : -1;
      radio.addEventListener('click', () => change(group, entry)); box.append(radio);
    });
    box.addEventListener('keydown', event => {
      const step = ARROWS[event.key]; if (!step) return;
      const from = entries.findIndex(([entry]) => entry === event.target.dataset?.value); if (from < 0) return;
      event.preventDefault(); change(group, entries[(from + step + entries.length) % entries.length][0]);
    });
    return box;
  }
  function refresh(focus) {
    const effective = applyAppearance(phone, override);
    const mode = override.mode ?? phone?.mode ?? 'system', overridden = Object.keys(override).length > 0;
    panel.replaceChildren();
    const head = el('div', null, 'appearance-head'), reset = el('button', 'Match my phone', 'appearance-reset'); reset.type = 'button';
    reset.addEventListener('click', () => { override = {}; clearAppearance(); refresh(); onChange(); });
    head.append(el('span', 'Appearance', 'appearance-title'), reset); panel.append(head);
    const change = (group, entry) => {
      if (group === 'mode') override.mode = entry; else if (group === 'accent') override.accentId = entry; else override.themed = entry === 'themed';
      saveAppearance(override); refresh(`${group}:${entry}`); onChange();
    };
    const section = (title) => panel.append(el('div', title, 'appearance-label'));
    const segment = (entry, name) => el('button', name, 'segment');
    section('Theme'); panel.append(radios('mode', 'Theme', [['system', 'System'], ['dark', 'Dark'], ['light', 'Light']], mode, change, 'segmented-radio', segment));
    section('Accent');
    const presets = accents[effective.light ? 'light' : 'dark'], themed = effective.themed ? 'themed' : 'plain', current = presets.find(p => p.id === effective.tokens.preset);
    const swatch = (entry) => { const preset = presets.find(p => p.id === entry), dot = el('button', null, 'swatch'); dot.setAttribute('aria-label', preset.name[themed]); dot.title = preset.name[themed]; dot.style.setProperty('--sw-a', preset.tokens.primary); dot.style.setProperty('--sw-b', preset.tokens.secondary); return dot; };
    panel.append(radios('accent', 'Accent', presets.map(p => [p.id, p.name[themed]]), current?.id, change, 'swatches', swatch));
    // No preset matches when the phone uses a custom palette: then no swatch is checked and the first one takes the tab stop.
    panel.append(el('div', current ? current.name[themed] : t('concept_yours_c'), 'accent-name'));
    section('Names'); panel.append(radios('names', 'Names', [['plain', 'Plain'], ['themed', 'Themed']], effective.themed ? 'themed' : 'plain', change, 'segmented-radio', segment));
    panel.append(el('p', overridden ? 'Showing your own choices here, not your phone’s. Saved in this browser only.' : 'Following your phone until you change something here. Saved in this browser only.', 'appearance-note'));
    if (focus) panel.querySelector(`[data-group="${focus.split(':')[0]}"][data-value="${focus.split(':')[1]}"]`)?.focus();
  }
  function setOpen(value, returnFocus) {
    if (value === open) return; open = value; panel.hidden = !open; button.setAttribute('aria-expanded', String(open));
    if (open) { document.addEventListener('click', outside, true); document.addEventListener('keydown', escape); wrap.addEventListener('focusout', leave); }
    else { document.removeEventListener('click', outside, true); document.removeEventListener('keydown', escape); wrap.removeEventListener('focusout', leave); if (returnFocus) button.focus(); }
  }
  // Capture phase: a radio re-renders the panel on click, which would otherwise leave the target detached before this check.
  function outside(event) { if (!wrap.contains(event.target)) setOpen(false); }
  function escape(event) { if (event.key === 'Escape') { event.preventDefault(); setOpen(false, true); } }
  function leave(event) { if (event.relatedTarget && !wrap.contains(event.relatedTarget)) setOpen(false); }
  button.addEventListener('click', () => setOpen(!open, false));
  const media = globalThis.matchMedia?.('(prefers-color-scheme: light)'); const follow = () => refresh(); media?.addEventListener?.('change', follow);
  wrap.dispose = () => { media?.removeEventListener?.('change', follow); setOpen(false); };
  refresh(); return wrap;
}
