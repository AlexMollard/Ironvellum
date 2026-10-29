import catalogue from '../generated/catalogue.json';
import { word } from '../i18n.js';
import { node } from './statCard.js';

export const MAX_STEP = 10;
const lift = wire => catalogue.lifts.find(l => l.wire === wire);

/** A tier's name from the generated catalogue (0 is Initiate, 10 Umbral II); a step outside the ladder is clamped as the phone does. */
export const tierName = step => catalogue.tiers[Math.min(MAX_STEP, Math.max(0, step))];
/** "Silver I" on a tiered lift; "<rung word> n" on a ladder lift (the rung names are the phone's own exercise list, which the catalogue does not carry). */
export const stepLabel = (wire, step) => (lift(wire)?.kind === 'LADDER' ? (step > 0 ? `${word('tier', 'concept_rung_s')} ${step}` : '—') : tierName(step));

/** One lift on a board: its name, the step label and a bar of ten, "tier n of 10". Unknown lifts (a newer app's) are skipped by the caller. */
export function liftTier({ lift: wire, step }) {
  const row = node('div', null, 'lift-tier'); row.dataset.lift = wire;
  const bar = node('span', null, 'tier-bar'); bar.setAttribute('role', 'img'); bar.setAttribute('aria-label', `tier ${step} of ${MAX_STEP}`);
  for (let i = 1; i <= MAX_STEP; i++) bar.append(node('span', null, i <= step ? 'tier-cell is-on' : 'tier-cell'));
  row.append(node('span', lift(wire)?.label ?? wire, 'tier-lift'), node('span', stepLabel(wire, step), 'tier-name'), bar, node('span', `tier ${step} of ${MAX_STEP}`, 'muted tier-of'));
  return row;
}
export const knownLift = wire => !!lift(wire);
