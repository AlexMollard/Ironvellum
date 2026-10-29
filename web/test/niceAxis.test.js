import { describe, it, expect } from 'vitest';
import { niceScale, renderLineChart } from '../src/components/lineChart.js';
import { formatLoad } from '../src/format.js';

const labels = root => [...root.querySelectorAll('text.chart-text')].map(t => t.textContent).filter(t => /^-?[\d.]+( |$)/.test(t));
const draw = (values, extra = {}) => {
  const root = document.createElement('div');
  renderLineChart(root, { series: [{ points: values.map((v, i) => [i + 1, null, v, 0]) }], details: false, ...extra });
  return root;
};
const between = (root, grid) => {
  for (const mark of root.querySelectorAll('.chart-point')) {
    const cy = Number(mark.getAttribute('cy') ?? mark.getAttribute('points').split(' ')[1].split(',')[1]);
    expect(cy).toBeGreaterThanOrEqual(Math.min(...grid)); expect(cy).toBeLessThanOrEqual(Math.max(...grid));
  }
};
const gridOf = root => [...root.querySelectorAll('.chart-grid')].map(l => Number(l.getAttribute('y1')));

describe('nice chart axes', () => {
  it('rounds the bounds outward to steps of 1, 2, 2.5 or 5 x 10^n', () => {
    expect(niceScale(80, 105)).toMatchObject({ lo: 80, hi: 110, step: 10 });
    expect(niceScale(62.5, 88)).toMatchObject({ lo: 60, hi: 90, step: 10 });
    expect(niceScale(101, 119)).toMatchObject({ lo: 100, hi: 120, step: 5 });
    expect(niceScale(0.12, 0.4)).toMatchObject({ lo: 0.1, hi: 0.4, step: 0.1 });
    expect(niceScale(3, 41)).toMatchObject({ lo: 0, hi: 50, step: 10 });
    expect(niceScale(100, 110).ticks).toEqual([100, 102.5, 105, 107.5, 110]);
  });
  it('keeps every value inside the axis, whatever the data', () => {
    for (const [min, max] of [[80, 105], [-7.3, 4.1], [0, 0.003], [1234, 98765], [-50, -10], [999.9, 1000.1]]) {
      const { lo, hi, ticks } = niceScale(min, max);
      expect(lo).toBeLessThanOrEqual(min); expect(hi).toBeGreaterThanOrEqual(max);
      expect(ticks[0]).toBe(lo); expect(ticks.at(-1)).toBe(hi);
    }
  });
  it('gives equal values, a single point and zero a sensible span', () => {
    expect(niceScale(100, 100)).toMatchObject({ lo: 95, hi: 105, step: 2.5 });
    expect(niceScale(0, 0)).toMatchObject({ lo: 0, hi: 1 });
    const one = niceScale(60, 60); expect(one.lo).toBeLessThan(60); expect(one.hi).toBeGreaterThan(60);
    expect(labels(draw([100, 100, 100], { unit: 'kg' }))).toEqual(['95 kg', '97.5 kg', '100 kg', '102.5 kg', '105 kg']);
    expect(labels(draw([100], { unit: 'kg' }))).toEqual(['95 kg', '97.5 kg', '100 kg', '102.5 kg', '105 kg']);
  });
  it('handles negative values and zero', () => {
    expect(niceScale(-12, 8)).toMatchObject({ lo: -15, hi: 10, step: 5 });
    const root = draw([-3, 0, 2]);
    expect(labels(root)).toEqual(['-4', '-2', '0', '2']);
    between(root, gridOf(root));
  });
  it('is round in pounds when the data are kilograms', () => {
    const root = draw([60, 72, 85], { unit: 'lb', factor: 1 / 0.45359237, formatValue: v => formatLoad(v, 'lb') });
    expect(labels(root)).toEqual(['120 lb', '140 lb', '160 lb', '180 lb', '200 lb']);
    between(root, gridOf(root));
  });
  it('draws every point between the first and last grid line', () => {
    const root = draw([80, 105, 92]);
    between(root, gridOf(root));
  });
});
