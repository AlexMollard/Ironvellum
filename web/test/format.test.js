import {it,expect} from 'vitest';
import parity from './fixtures/parity.json';
import {exerciseUnit,formatLoad,formatSets,formatDay} from '../src/format.js';
it('matches phone load and set formatting cases',()=>{for(const c of parity.loadCases) expect(formatLoad(c.kg,c.unit)).toBe(c.text);for(const c of parity.formatSetsCases) expect(formatSets(c.sets)).toBe(c.text);});
it('formats calendar days without shifting them to the browser zone',()=>{expect(formatDay('2026-01-01','Australia/Sydney')).toContain('1');});
it('resolves normalized exercise overrides before global and legacy fallback units',()=>{
  const snapshot={unit:'lb',exerciseUnits:{'bench press':'kg','leg press':'lb'}};
  expect(exerciseUnit(snapshot,'  BENCH PRESS ')).toBe('kg');
  expect(exerciseUnit(snapshot,'Leg Press')).toBe('lb');
  expect(exerciseUnit(snapshot,'Row')).toBe('lb');
  expect(exerciseUnit({unit:'kg'},'Leg Press')).toBe('kg');
});
