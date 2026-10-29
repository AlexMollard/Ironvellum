import { describe, it, expect, vi, afterEach } from 'vitest';
import { link } from '../src/components/statCard.js';

// Small stand-ins for the screens: each draws a heading, and Lifts draws links the way the real one does (one per lift, same href after a choice).
const head = (root, text) => { const h = document.createElement('div'); h.className = 'screen-header'; const h1 = document.createElement('h1'); h1.textContent = text; h.append(h1); root.append(h); };
vi.mock('../src/screens/overview.js', () => ({ renderOverview: async root => { head(root, 'Overview'); } }));
vi.mock('../src/screens/workouts.js', () => ({ renderWorkouts: async root => { head(root, 'Workouts'); } }));
vi.mock('../src/screens/workoutDetail.js', () => ({ renderWorkoutDetail: async root => { head(root, 'One workout'); } }));
vi.mock('../src/screens/trends.js', () => ({ renderTrends: async root => { head(root, 'Trends'); } }));
vi.mock('../src/screens/routines.js', () => ({ renderRoutines: async root => { head(root, 'Routines'); } }));
vi.mock('../src/screens/lifts.js', () => ({
  renderLifts: async (root, ctx, name) => {
    head(root, 'Lifts');
    for (const lift of ['A', 'B']) root.append(link(lift, `/lifts/${lift}`, ctx.router, 'lift-item'));
    root.dataset.title = name ? `${name} · Lifts` : '';
    root.dataset.same = String(Boolean(ctx.sameScreen));
  },
}));
import { renderShell } from '../src/shell.js';

const wait = () => new Promise(resolve => setTimeout(resolve, 20));
const mount = async () => {
  location.hash = '#/'; const root = document.createElement('div'); document.body.append(root);
  const router = renderShell(root, { source: {}, snapshot: null, onSignOut: () => {} }); await wait();
  return { root, router };
};
const go = async path => { location.hash = `#${path}`; await wait(); };
afterEach(() => { document.body.innerHTML = ''; document.title = ''; location.hash = ''; });

describe('moving between pages', () => {
  it('namesEachPageInTheTabTitleAndLeavesFocusAloneOnFirstLoad', async () => {
    await mount();
    expect(document.title).toBe('Overview · Ironvellum'); expect(document.activeElement).toBe(document.body);
    await go('/workouts'); expect(document.title).toBe('Workouts · Ironvellum');
    await go('/friends/inbox'); expect(document.title).toBe('Inbox · Friends · Ironvellum');
  });
  it('movesFocusToThePageHeadingWhenTheScreenChanges', async () => {
    const { root } = await mount();
    await go('/workouts'); const h1 = root.querySelector('.topbar h1');
    expect(h1.textContent).toBe('Workouts'); expect(h1.getAttribute('tabindex')).toBe('-1'); expect(document.activeElement).toBe(h1);
    await go('/workouts/synced/abc'); expect(document.activeElement.textContent).toBe('One workout');
  });
  it('aChoiceInsideAScreenKeepsFocusOnTheSameLink', async () => {
    const { root } = await mount();
    await go('/lifts'); expect(root.querySelector('.route-page').dataset.same).toBe('false');
    const b = root.querySelector('a[href="#/lifts/B"]'); b.focus(); b.click(); await wait();
    expect(root.querySelector('.route-page').dataset.same).toBe('true'); expect(document.title).toBe('B · Lifts · Ironvellum');
    expect(document.activeElement.getAttribute('href')).toBe('#/lifts/B'); expect(document.activeElement.isConnected).toBe(true);
  });
  it('drawingTheSamePageAgainDoesNotStealFocus', async () => {
    const { root, router } = await mount();
    await go('/workouts'); const appearance = root.querySelector('.appearance-button'); appearance.focus();
    router.start(); await wait(); expect(document.activeElement).toBe(appearance);
  });
  it('skipLinkMovesFocusIntoTheContentWithoutRouting', async () => {
    const { root } = await mount(); const skip = root.querySelector('.skip-link');
    expect(root.firstElementChild).toBe(skip); expect(skip.textContent).toBe('Skip to content');
    skip.click(); expect(document.activeElement).toBe(root.querySelector('main')); expect(location.hash).toBe('#/');
  });
  it('aDeadEndOffersTheWayBack', async () => {
    const { root } = await mount();
    await go('/nowhere'); expect(root.querySelector('.route-message').textContent).toBe('Page not found.');
    expect(root.querySelector('.route-home').getAttribute('href')).toBe('#/'); expect(document.title).toBe('Page not found. · Ironvellum');
  });
});

describe('links keep the browser’s own clicks', () => {
  const click = (init = {}) => { const router = { go: vi.fn() }, a = link('x', '/lifts/A', router); document.body.append(a); const event = new MouseEvent('click', { bubbles: true, cancelable: true, ...init }); a.dispatchEvent(event); return { router, event }; };
  it('routesAPlainClickInPlace', () => { const { router, event } = click(); expect(router.go).toHaveBeenCalledWith('/lifts/A'); expect(event.defaultPrevented).toBe(true); });
  it('leavesCtrlCmdShiftAltAndMiddleClicksToTheBrowser', () => {
    for (const init of [{ ctrlKey: true }, { metaKey: true }, { shiftKey: true }, { altKey: true }, { button: 1 }]) { const { router, event } = click(init); expect(router.go).not.toHaveBeenCalled(); expect(event.defaultPrevented).toBe(false); }
  });
});
