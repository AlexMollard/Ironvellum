import { describe, it, expect, vi } from 'vitest';
import { renderLifts } from '../src/screens/lifts.js';
import { formatPeak } from '../src/components/variationTable.js';

vi.mock('../src/components/artImg.js', () => ({ artImg: (name, art) => { const e = document.createElement('span'); e.dataset.art = art || 'placeholder'; return e; } }));
vi.mock('../src/components/bodyMap.js', () => ({ renderBodyMap: (el, opts) => { el.dataset.figure = opts.figure; } }));
vi.mock('../src/i18n.js', () => ({ t: key => key.startsWith('concept_trial') ? 'workouts' : 'One workout written', word: plain => plain }));
const now = Date.UTC(2026, 9, 28);
const variation = { key: 'weighted', label: 'Weighted', chartMetric: 'e1rm', chartUnit: 'kg', sessions: 2, sets: 2, lastMs: now, perWeek: 1.5, volumeKg: 200, peak: { reps: 6, weightKg: 12.5, e1rmKg: 40 }, heaviest: { reps: 5, weightKg: 15 }, setRecords: [{ position: 0, reps: 6, weightKg: 12.5 }], points: [[now - 86400000, 1, 30, 0], [now, 2, 40, 1]] };
const lift = { name: 'Pull-up', art: 'pull', metric: 'reps', byE1rm: true, muscleSummary: 'Lats', muscleLevels: { LATS: 3 }, variations: [variation] };
const context = () => ({ snapshot: { lifts: [lift], unit: 'kg', zone: 'UTC', builtAtMs: now, profile: { figure: 'female' } }, router: { go: vi.fn() } });

describe('exercise progress', () => {
  it('lists, filters and opens exercises', () => { const root = document.createElement('div'), ctx = context(); renderLifts(root, ctx); expect(root.textContent).toContain('1 variation'); root.querySelector('button').click(); expect(ctx.router.go).toHaveBeenCalledWith('/lifts/Pull-up'); const input = root.querySelector('input'); input.value = 'missing'; input.dispatchEvent(new Event('input')); expect(root.textContent).toContain('No exercises found'); });
  it('renders snapshot marks, records, map and table', () => { const root = document.createElement('div'); renderLifts(root, context(), 'Pull-up'); expect(root.textContent).toContain('Lats'); expect(root.textContent).toContain('+12.5 kg × 6'); expect(root.textContent).toContain('1.5 a week'); expect(root.querySelector('[data-figure]').dataset.figure).toBe('female'); expect(root.querySelectorAll('circle')).toHaveLength(1); expect(root.querySelectorAll('polygon')).toHaveLength(1); expect(root.querySelector('polygon').dataset.value).toBe('40'); root.querySelector('.variation-toggle').click(); expect(root.querySelectorAll('svg circle')).toHaveLength(0); });
  it('filters against snapshot build time', () => { const old = variation.points[0][0]; variation.points[0][0] = now - 100 * 86400000; const root = document.createElement('div'); renderLifts(root, context(), 'Pull-up'); const select = root.querySelector('select'); select.value = '3'; select.dispatchEvent(new Event('change')); expect(root.textContent).toContain('One workout written'); variation.points[0][0] = old; });
  it('preserves assisted and hold values', () => { expect(formatPeak({ weightKg: -20, reps: 10 }, 'kg')).toBe('-20 kg × 10'); expect(formatPeak({ reps: 5, durationSec: 5 }, 'kg')).toBe('5 × 1 at 5 s'); expect(formatPeak({ reps: 12, weightKg: null }, 'kg')).toBe('BW × 12'); });
  it('separates score and e1rm axes, keeping score unconverted for pound users', () => {
    const root = document.createElement('div'), ctx = context();
    const score = { ...variation, key: 'score', label: 'Score', chartMetric: 'strength', chartUnit: 'score', points: [[now - 86400000, 1, 33.97, 0], [now, 2, 40, 1]] };
    ctx.snapshot = { ...ctx.snapshot, unit: 'lb', lifts: [{ ...lift, variations: [variation, score] }] };
    renderLifts(root, ctx, 'Pull-up');
    const charts = root.querySelectorAll('.line-chart');
    expect(charts).toHaveLength(2);
    expect(charts[0].textContent).toContain('lb');
    expect(charts[1].textContent).toContain('33.97 score');
    expect(charts[1].textContent).not.toContain('lb');
  });
  it('renders hold records as seconds with one-based positions', () => {
    const root = document.createElement('div'), ctx = context();
    ctx.snapshot = { ...ctx.snapshot, lifts: [{ ...lift, metric: 'hold', variations: [{ ...variation, chartMetric: 'strength', chartUnit: 'score', setRecords: [{ position: 0, reps: 40, weightKg: null }] }] }] };
    renderLifts(root, ctx, 'Pull-up');
    expect(root.textContent).toContain('Set 1: 40 s');
    expect(root.textContent).not.toContain('BW × 40');
    expect(root.textContent).toContain('Strength score per workout');
  });
});
