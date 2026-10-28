import { t } from '../i18n.js';
import { snapshotForWorkout } from '../source.js';
import { formatSets, formatDayShort, formatDay } from '../format.js';
import { artImg } from '../components/artImg.js';
import { node, card, sortedWorkouts, dayKey, displayDate, workoutDay, link } from '../components/statCard.js';
import { calendar } from '../components/calendar.js';
import { workoutRow } from '../components/workoutRow.js';
import { pager } from '../components/pager.js';
import { bindTips, roving, scrollToElement } from '../components/interact.js';
const monthName = (month, withYear) => new Intl.DateTimeFormat(undefined, { month: 'long', ...(withYear ? { year: 'numeric' } : {}), timeZone: 'UTC' }).format(new Date(`${month}-01T12:00:00Z`));
export async function renderWorkouts(root, { source, snapshot, router, nowMs = Date.now() }) {
  root.replaceChildren();
  const rows = sortedWorkouts(await source.listWorkouts(), source, snapshot?.zone);
  const zone = snapshot?.zone || 'UTC';
  const currentMonth = dayKey(snapshot?.builtAtMs ?? nowMs, zone).slice(0, 7);
  let month = currentMonth, filter = 'all', query = '', page = 0, picked = '';
  const header = node('header', null, 'screen-header'); header.append(node('h1', t('concept_trial_cpl')));
  const first = rows.reduce((earliest, row) => !earliest || row.startedAt < earliest ? row.startedAt : earliest, null);
  header.append(node('p', `${rows.length} ${t(rows.length === 1 ? 'concept_trial_s' : 'concept_trial_pl')}${first ? ` since ${displayDate(first, zone)}` : ''}`, 'muted'));
  root.append(header);
  const layout = node('div', null, 'workouts-layout'), side = node('div', null, 'workouts-side'), listCard = card(null, 'list-card');
  const controls = node('div', null, 'list-controls');
  const search = node('input'); search.type = 'search'; search.placeholder = 'Search workouts or exercises'; search.setAttribute('aria-label', `Search ${t('concept_trial_pl')}`);
  search.addEventListener('input', () => { query = search.value.toLocaleLowerCase(); page = 0; picked = ''; drawList(); }); controls.append(search);
  for (const [value, label] of [['all', 'All'], ['prs', 'With PRs'], ['imported', 'Imported']]) {
    const button = node('button', label, 'pill'); button.type = 'button'; button.dataset.filter = value;
    button.onclick = () => { filter = value; page = 0; picked = ''; drawList(); }; controls.append(button);
  }
  const listArea = node('div', null, 'workout-list'); listCard.append(controls, listArea);
  const calendarArea = node('div'), monthArea = node('div'); side.append(calendarArea, monthArea); layout.append(side, listCard); root.append(layout);
  function drawCalendar() {
    calendarArea.replaceChildren(); monthArea.replaceChildren();
    const el = calendar(month, rows, zone, delta => { const [y, m] = month.split('-').map(Number); const d = new Date(Date.UTC(y, m - 1 + delta, 1)); month = `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`; drawCalendar(); }, nowMs, { router, onPick: pickDay });
    const entry = snapshot?.months?.[month];
    el.append(node('p', entry ? `${entry.workouts} ${t('concept_trial_pl')} this month · ${formatSets(entry.perWeek)} per week on average` : `— ${t('concept_trial_pl')} this month · — per week on average`, 'muted calendar-foot'));
    calendarArea.append(el);
    const panel = card(month === currentMonth ? `${monthName(month)} so far` : monthName(month, true), 'month-card');
    if (!entry) panel.append(node('p', '—', 'muted'));
    else {
      const weeks = entry.weeks || [], max = Math.max(1, ...weeks.map(week => week.hardSets));
      const bars = node('div', null, 'week-bars'), stops = [];
      for (const week of weeks) {
        const column = node('div', null, 'week-col'), bar = node('div', null, 'week-bar'); column.dataset.week = week.start; stops.push(bar);
        bar.style.setProperty('--h', String(Math.max(.04, week.hardSets / max)));
        bar.setAttribute('role', 'meter'); bar.setAttribute('aria-valuemin', '0'); bar.setAttribute('aria-valuemax', String(max)); bar.setAttribute('aria-valuenow', String(week.hardSets)); bar.setAttribute('aria-label', `${week.start}: ${formatSets(week.hardSets)} hard sets`);
        column.append(node('span', formatSets(week.hardSets), 'muted'), bar, node('span', formatDayShort(week.start), 'muted'));
        bars.append(column);
      }
      // Hover, tap or focus a week for its date and hard sets; Left/Right move between weeks.
      roving(stops);
      bindTips(bars, '.week-col', column => { const week = weeks.find(w => w.start === column.dataset.week); return { title: `Week of ${formatDay(week.start)}`, value: `${formatSets(week.hardSets)} hard sets` }; }, { anchorOf: column => column.querySelector('.week-bar'), onShow: c => c.classList.add('is-active'), onHide: c => c.classList.remove('is-active') });
      panel.append(bars, node('p', 'Hard sets per week', 'muted week-caption'));
      for (const lift of entry.topLifts || []) { const item = node('div', null, 'record-row compact is-link'); const frame = node('span', null, 'art-frame'); frame.append(artImg(lift.name, lift.art)); const text = node('span', null, 'record-text'); text.append(link(lift.name, `/lifts/${encodeURIComponent(lift.name)}`, router)); item.append(frame, text, node('span', `${lift.sessions} ${t('concept_trial_pl')}`, 'muted')); panel.append(item); }
    }
    monthArea.append(panel);
  }
  // A calendar day with several workouts: show them all in the list, highlighted, and scroll to them.
  function pickDay(day) { picked = day; filter = 'all'; query = ''; search.value = ''; page = 0; drawList(); }
  function drawList() {
    listArea.replaceChildren();
    const filtered = rows.filter(w => (`${w.title || ''} ${w.label || ''}`).toLocaleLowerCase().includes(query) && (filter === 'all' || (filter === 'imported' ? w.origin === 'imported' : (snapshotForWorkout(snapshot, w)?.peaks ?? 0) > 0)));
    for (const button of controls.querySelectorAll('button')) button.setAttribute('aria-pressed', String(button.dataset.filter === filter));
    if (!filtered.length) { listArea.append(node('p', rows.length ? `No matching ${t('concept_trial_pl')}.` : `No ${t('concept_trial_pl')} yet.`, 'muted empty')); return; }
    const size = 25, pages = Math.ceil(filtered.length / size);
    if (picked) { const at = filtered.findIndex(w => workoutDay(w, zone) === picked); if (at >= 0) page = Math.floor(at / size); }
    page = Math.min(page, pages - 1);
    for (const row of filtered.slice(page * size, (page + 1) * size)) listArea.append(workoutRow(row, snapshotForWorkout(snapshot, row), snapshot, router));
    if (picked) { const hits = [...listArea.querySelectorAll('.workout-row')].filter(r => r.dataset.day === picked); for (const r of hits) r.classList.add('is-highlight'); if (hits[0]) scrollToElement(hits[0]); }
    if (pages > 1) listArea.append(pager(page, pages, n => { page = n; picked = ''; drawList(); }));
  }
  drawCalendar(); drawList();
}
