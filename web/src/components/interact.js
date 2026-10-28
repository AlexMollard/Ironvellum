// Shared hover, focus and touch behaviour: one tooltip for the whole page, delegated tooltip binding, roving keyboard focus.
// It only shows text its callers already have; it computes nothing about training.

export const reducedMotion = () => Boolean(globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches);
export const scrollBehavior = () => (reducedMotion() ? 'auto' : 'smooth');
export function scrollToElement(el) { el.scrollIntoView?.({ block: 'nearest', behavior: scrollBehavior() }); }

const TIP_ID = 'viewer-tooltip';
let tip, current = null, listening = false;

function tipElement() {
  if (!tip?.isConnected) {
    tip = document.createElement('div'); tip.id = TIP_ID; tip.className = 'tooltip';
    tip.setAttribute('role', 'status'); tip.setAttribute('aria-live', 'polite'); tip.setAttribute('aria-atomic', 'true');
    document.body.append(tip);
  }
  if (!listening) {
    listening = true;
    // A tap anywhere outside the thing that owns the tooltip dismisses it.
    document.addEventListener('pointerdown', event => { if (current && !current.owner.contains(event.target)) hideTip(); });
    document.addEventListener('keydown', event => { if (event.key === 'Escape') hideTip(); });
  }
  return tip;
}

const part = (tag, text, className) => { const el = document.createElement(tag); el.textContent = text; if (className) el.className = className; return el; };

/** content: { title, value, lines: [string | { text, series }], pr }. `point` is a client position; otherwise the anchor's top centre. */
export function showTip(content, { anchor, point, owner = anchor }) {
  const el = tipElement();
  el.replaceChildren();
  if (content.title) el.append(part('div', content.title, 'tooltip-title'));
  if (content.value) {
    const line = part('div', content.value, 'tooltip-value');
    if (content.pr) line.append(' ', part('b', 'PR', 'tooltip-pr'));
    el.append(line);
  }
  for (const entry of content.lines ?? []) {
    const text = typeof entry === 'string' ? entry : entry.text;
    el.append(part('div', text, entry.series == null ? 'tooltip-line' : `tooltip-line legend-item chart-series-${entry.series % 6}`));
  }
  current = { anchor, owner };
  el.classList.add('is-on');
  const rect = anchor.getBoundingClientRect();
  const x = point?.x ?? rect.left + rect.width / 2, y = point?.y ?? rect.top;
  const width = el.offsetWidth, height = el.offsetHeight, view = document.documentElement.clientWidth || window.innerWidth || 0;
  let left = x - width / 2; if (view) left = Math.max(4, Math.min(left, view - width - 4));
  const above = y - height - 10;
  el.style.left = `${Math.round(left)}px`; el.style.top = `${Math.round(above < 4 ? (point ? y : rect.bottom) + 12 : above)}px`;
}

/** Hides the tooltip, but only when `owner` is the one showing it (so a late blur cannot close another tooltip). */
export function hideTip(owner) {
  if (owner && current?.owner !== owner) return;
  current = null; tip?.classList.remove('is-on');
}

/**
 * Delegated tooltips: elements matching `selector` inside `root` show `contentOf(el)` on hover, keyboard focus and touch.
 * Options: anchorOf(el) for where to place it, onShow(el) / onHide(el) for highlighting.
 */
export function bindTips(root, selector, contentOf, { anchorOf = el => el, onShow, onHide } = {}) {
  let shown = null;
  const hit = event => { const el = event.target.closest?.(selector); return el && root.contains(el) ? el : null; };
  const show = el => { if (shown && shown !== el) onHide?.(shown); shown = el; onShow?.(el); showTip(contentOf(el), { anchor: anchorOf(el), owner: root }); };
  const hide = el => { if (shown !== el) return; shown = null; onHide?.(el); hideTip(root); };
  root.addEventListener('pointerover', event => { const el = hit(event); if (el) show(el); });
  root.addEventListener('pointerout', event => { const el = hit(event); if (el && event.pointerType !== 'touch' && !el.contains(event.relatedTarget)) hide(el); });
  root.addEventListener('focusin', event => { const el = hit(event); if (el) show(el); });
  root.addEventListener('focusout', event => { const el = hit(event); if (el) hide(el); });
  root.addEventListener('keydown', event => { if (event.key === 'Escape' && shown) hide(shown); });
}

const ARROWS = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 };
/**
 * Roving tab stop: one item is reachable with Tab, the arrow keys, Home and End move between the items.
 * `next(index, key)` may return the index to move to (or undefined to ignore the key).
 */
export function roving(items, next = (index, key) => (key in ARROWS ? index + ARROWS[key] : undefined)) {
  const setStop = active => items.forEach(item => item.setAttribute('tabindex', item === active ? '0' : '-1'));
  setStop(items[0]);
  items.forEach((item, index) => {
    item.addEventListener('focus', () => setStop(item));
    item.addEventListener('keydown', event => {
      const target = event.key === 'Home' ? 0 : event.key === 'End' ? items.length - 1 : next(index, event.key);
      if (target == null || !items[target] || target === index) { if (target != null || event.key in ARROWS) event.preventDefault(); return; }
      event.preventDefault(); items[target].focus();
    });
  });
}

/** Where an SVG point is on screen, for placing the tooltip. Null when the svg has no size (not laid out). */
export function clientPoint(svg, x, y, width, height) {
  const rect = svg.getBoundingClientRect();
  return rect.width ? { x: rect.left + x * rect.width / width, y: rect.top + y * rect.height / height } : null;
}
/** The reverse: a pointer position in the svg's own units. */
export function svgPoint(svg, event, width, height) {
  const rect = svg.getBoundingClientRect();
  return rect.width ? { x: (event.clientX - rect.left) * width / rect.width, y: (event.clientY - rect.top) * height / rect.height } : { x: event.clientX, y: event.clientY };
}
