import { t } from '../i18n.js';
import { node } from './statCard.js';
export function pager(page, pages, onChange) {
  const el = node('nav', null, 'button-row pager'); el.setAttribute('aria-label', `${t('concept_trial_c')} pages`);
  const prev = node('button', 'Previous'); prev.type = 'button'; prev.disabled = page <= 0; prev.onclick = () => onChange(page - 1);
  const next = node('button', 'Next'); next.type = 'button'; next.disabled = page >= pages - 1; next.onclick = () => onChange(page + 1);
  el.append(prev, node('span', `${page + 1} / ${pages}`), next); return el;
}
