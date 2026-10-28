import { it, expect, beforeEach, afterEach, vi } from 'vitest';
import { createAppearanceMenu } from '../src/appearance.js';
import { accents, applyAppearance } from '../src/theme.js';
import { renderShell } from '../src/shell.js';
import { prTag } from '../src/components/prTag.js';
beforeEach(() => { localStorage.clear(); document.documentElement.removeAttribute('style'); });
afterEach(() => { document.body.replaceChildren(); applyAppearance({ mode: 'dark', themed: false }); });
const group = (menu, name) => menu.querySelector(`[role="radiogroup"][aria-label="${name}"]`);
const radios = (menu, name) => [...group(menu, name).querySelectorAll('[role="radio"]')];
const checked = (menu, name) => radios(menu, name).map(r => r.getAttribute('aria-checked'));
const key = (el, k) => el.dispatchEvent(new KeyboardEvent('keydown', { key: k, bubbles: true }));
const open = (phone, onChange) => { const menu = createAppearanceMenu(phone, onChange); document.body.append(menu); menu.querySelector('.appearance-button').click(); return menu; };
it('the button is an icon with the label Appearance and opens a labelled dialog', () => {
  const menu = open({ mode: 'dark' }), button = menu.querySelector('.appearance-button'), panel = menu.querySelector('[role="dialog"]');
  expect(button.getAttribute('aria-label')).toBe('Appearance'); expect(button.querySelector('svg')).not.toBeNull(); expect(button.textContent).toBe('Appearance');
  expect(button.getAttribute('aria-expanded')).toBe('true'); expect(panel.hidden).toBe(false); expect(panel.getAttribute('aria-label')).toBe('Appearance'); expect(button.getAttribute('aria-controls')).toBe(panel.id);
  expect(menu.querySelector('select')).toBeNull(); menu.dispose();
});
it('the swatches list every preset of the effective theme, plain and themed, with the current one checked', () => {
  for (const [mode, themed] of [['dark', false], ['light', false], ['dark', true], ['light', true]]) {
    const menu = open({ mode, themed }), swatches = radios(menu, 'Accent');
    expect(swatches.map(o => o.dataset.value)).toEqual(accents[mode].map(a => a.id));
    expect(swatches.map(o => o.getAttribute('aria-label'))).toEqual(accents[mode].map(a => a.name[themed ? 'themed' : 'plain']));
    expect(swatches.map(o => o.style.getPropertyValue('--sw-a'))).toEqual(accents[mode].map(a => a.tokens.primary));
    expect(swatches.map(o => o.style.getPropertyValue('--sw-b'))).toEqual(accents[mode].map(a => a.tokens.secondary));
    expect(swatches.filter(o => o.getAttribute('aria-checked') === 'true')).toHaveLength(1); menu.dispose(); menu.remove();
  }
});
it('segmented controls are radio groups: one checked option, one tab stop, arrows move and select', () => {
  const onChange = vi.fn(), menu = open({ mode: 'dark', themed: false }, onChange);
  expect(radios(menu, 'Theme').map(r => r.textContent)).toEqual(['System', 'Dark', 'Light']); expect(checked(menu, 'Theme')).toEqual(['false', 'true', 'false']); expect(radios(menu, 'Theme').map(r => r.tabIndex)).toEqual([-1, 0, -1]);
  key(radios(menu, 'Theme')[1], 'ArrowRight');
  expect(checked(menu, 'Theme')).toEqual(['false', 'false', 'true']); expect(document.documentElement.dataset.theme).toBe('light'); expect(onChange).toHaveBeenCalledTimes(1); expect(document.activeElement).toBe(radios(menu, 'Theme')[2]);
  key(radios(menu, 'Theme')[2], 'ArrowRight'); expect(checked(menu, 'Theme')[0]).toBe('true');
  key(radios(menu, 'Theme')[0], 'ArrowLeft'); expect(checked(menu, 'Theme')[2]).toBe('true');
  radios(menu, 'Names')[1].click(); expect(checked(menu, 'Names')).toEqual(['false', 'true']); menu.dispose();
});
it('the swatches move with the arrow keys and set the accent', () => {
  const menu = open({ mode: 'dark' }), second = accents.dark[1];
  key(radios(menu, 'Accent')[0], 'ArrowRight');
  expect(checked(menu, 'Accent')[1]).toBe('true'); expect(document.documentElement.style.getPropertyValue('--accent')).toBe(second.tokens.fill);
  expect(menu.querySelector('.accent-name').textContent).toBe(second.name.plain); menu.dispose();
});
it('keeps the choices in this browser and restores them; Match my phone clears them', () => {
  let menu = open({ mode: 'dark' }); radios(menu, 'Theme')[2].click(); radios(menu, 'Names')[1].click(); radios(menu, 'Accent')[2].click();
  expect(JSON.parse(localStorage.getItem('ironvellum.appearance'))).toEqual({ mode: 'light', themed: true, accentId: accents.light[2].id });
  expect(menu.querySelector('.appearance-note').textContent).toContain('not your phone'); menu.dispose(); menu.remove();
  menu = open({ mode: 'dark' }); expect(checked(menu, 'Theme')[2]).toBe('true'); expect(checked(menu, 'Names')[1]).toBe('true'); expect(checked(menu, 'Accent')[2]).toBe('true');
  menu.querySelector('.appearance-reset').click(); expect(localStorage.getItem('ironvellum.appearance')).toBeNull(); expect(checked(menu, 'Theme')[1]).toBe('true');
  expect(menu.querySelector('.appearance-note').textContent).toBe('Following your phone until you change something here. Saved in this browser only.'); menu.dispose();
});
it('Escape closes the popover and returns focus to the button; so does a click outside', () => {
  const menu = open({ mode: 'dark' }), button = menu.querySelector('.appearance-button'), panel = menu.querySelector('[role="dialog"]');
  radios(menu, 'Theme')[0].focus(); key(radios(menu, 'Theme')[0], 'Escape');
  expect(panel.hidden).toBe(true); expect(button.getAttribute('aria-expanded')).toBe('false'); expect(document.activeElement).toBe(button);
  button.click(); expect(panel.hidden).toBe(false); radios(menu, 'Names')[1].click(); expect(panel.hidden).toBe(false);
  document.body.click(); expect(panel.hidden).toBe(true); menu.dispose();
});
it('themed names reach the shell, the screens and the PR marks, and plain names come back', async () => {
  const snapshot = { builtAtMs: 1700000000000, zone: 'UTC', unit: 'kg', profile: { name: 'A', level: 1, intoLevel: 1, needed: 2, totalXp: 1, streakDays: 2, longestStreakDays: 3, deedsEarned: 0, deedsTotal: 1, figure: 'male' }, week: { workouts: 1, hardSets: 1, volumeKg: 1, prevVolumeKg: 1, xp: 1 }, trends: { records30d: 2, recent: [] }, deeds: [], muscles: {}, fatigue: { hasHistory: false }, sessions: {}, appearance: { mode: 'dark', themed: true } };
  const source = { userId: 'me', listWorkouts: async () => [], getSets: async () => [] };
  const root = document.createElement('div'); document.body.append(root); const shell = renderShell(root, { source, snapshot, onSignOut() {} });
  const settle = () => new Promise(r => setTimeout(r, 30)), eyebrows = () => [...root.querySelectorAll('.kpi .eyebrow')].map(e => e.textContent), pr = () => prTag({ label: 'Pull-up . Weighted', weightKg: 5, reps: 5 }).querySelector('b').textContent;
  await settle();
  expect(root.querySelector('[data-workouts] .nav-label').textContent).toBe('Chronicle'); expect(eyebrows()).toEqual(expect.arrayContaining(['Oath', 'Peaks'])); expect(root.querySelector('.records-card h2').textContent).toBe('Recent peaks'); expect(pr()).toBe('PEAK');
  radios(root, 'Names')[0].click(); await settle();
  expect(root.querySelector('[data-workouts] .nav-label').textContent).toBe('Workouts'); expect(eyebrows()).toEqual(expect.arrayContaining(['Streak', 'Records'])); expect(root.querySelector('.records-card h2').textContent).toBe('Recent records'); expect(pr()).toBe('PR');
  shell.dispose();
});
