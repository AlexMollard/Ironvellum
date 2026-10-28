import { t } from '../i18n.js';
import { snapshotForWorkout } from '../source.js';
import { formatSets } from '../format.js';
import { artImg } from '../components/artImg.js';
import { node, card, sortedWorkouts, dayKey, displayDate } from '../components/statCard.js';
import { calendar } from '../components/calendar.js';
import { workoutRow } from '../components/workoutRow.js';
import { pager } from '../components/pager.js';
export async function renderWorkouts(root, { source, snapshot, router }) {
  root.replaceChildren();
  const rows = sortedWorkouts(await source.listWorkouts(), source, snapshot?.zone);
  const zone = snapshot?.zone || 'UTC';
  let month = dayKey(snapshot?.builtAtMs ?? Date.now(), zone).slice(0, 7), filter = 'all', query = '', page = 0;
  const header = node('header', null, 'screen-header'); header.append(node('h1', t('concept_trial_cpl')));
  const first = rows.reduce((earliest, row) => !earliest || row.startedAt < earliest ? row.startedAt : earliest, null);
  header.append(node('p', `${rows.length} ${t(rows.length === 1 ? 'concept_trial_s' : 'concept_trial_pl')}${first ? ` since ${displayDate(first, zone)}` : ''}`, 'muted'));
  root.append(header);
  const controls = node('div', null, 'button-row');
  const search = node('input'); search.type = 'search'; search.placeholder = 'Search title or label'; search.setAttribute('aria-label', `Search ${t('concept_trial_pl')}`);
  search.addEventListener('input', () => { query = search.value.toLocaleLowerCase(); page = 0; drawList(); }); controls.append(search);
  for (const [value, label] of [['all', 'All'], ['prs', 'With PRs'], ['imported', 'Imported']]) {
    const button = node('button', label); button.type = 'button'; button.dataset.filter = value;
    button.onclick = () => { filter = value; page = 0; drawList(); }; controls.append(button);
  }
  root.append(controls);
  const calendarArea = node('div'); const listArea = node('div'); root.append(calendarArea, listArea);
  function drawCalendar() {
    calendarArea.replaceChildren();
    calendarArea.append(calendar(month, rows, zone, delta => { const [y, m] = month.split('-').map(Number); const d = new Date(Date.UTC(y, m - 1 + delta, 1)); month = `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`; drawCalendar(); }));
    const entry = snapshot?.months?.[month]; const panel = card('Month summary');
    if (!entry) panel.append(node('p', `— ${t('concept_trial_pl')} · — per week`), node('p', '— hard sets'), node('p', '— top lifts'));
    else {
      panel.append(node('p', `${entry.workouts} ${t('concept_trial_pl')} · ${formatSets(entry.perWeek)} per week`));
      const bars = node('div', null, 'month-weeks');
      const weeks = entry.weeks || [];
      const max = Math.max(1, ...weeks.map(week => week.hardSets));
      for (const week of weeks) {
        const row = node('div', null, 'month-week-bar');
        row.append(node('span', `${week.start} · ${formatSets(week.hardSets)} hard sets`));
        const meter = node('meter'); meter.min = 0; meter.max = max; meter.value = week.hardSets; meter.setAttribute('aria-label', `${week.start}: ${formatSets(week.hardSets)} hard sets`); row.append(meter); bars.append(row);
      }
      panel.append(bars);
      const lifts = node('div', null, 'top-lifts');
      for (const lift of entry.topLifts || []) { const item = node('div', null, 'record-row'); item.append(artImg(lift.name, lift.art), node('span', `${lift.name} · ${lift.sessions} ${t('concept_trial_pl')}`)); lifts.append(item); }
      panel.append(lifts);
    }
    calendarArea.append(panel);
  }
  function drawList() {
    listArea.replaceChildren();
    const filtered = rows.filter(w => (`${w.title || ''} ${w.label || ''}`).toLocaleLowerCase().includes(query) && (filter === 'all' || (filter === 'imported' ? w.origin === 'imported' : (snapshotForWorkout(snapshot, w)?.peaks ?? 0) > 0)));
    for (const button of controls.querySelectorAll('button')) button.setAttribute('aria-pressed', String(button.dataset.filter === filter));
    if (!filtered.length) { listArea.append(node('p', rows.length ? `No matching ${t('concept_trial_pl')}.` : `No ${t('concept_trial_pl')} yet.`)); return; }
    const size = 25, pages = Math.ceil(filtered.length / size); page = Math.min(page, pages - 1);
    for (const row of filtered.slice(page * size, (page + 1) * size)) listArea.append(workoutRow(row, snapshotForWorkout(snapshot, row), snapshot, router));
    if (pages > 1) listArea.append(pager(page, pages, n => { page = n; drawList(); }));
  }
  drawCalendar(); drawList();
}
