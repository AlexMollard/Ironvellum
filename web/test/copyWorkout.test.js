import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { copyButton } from '../src/components/copyWorkout.js';
import { renderWorkoutDetail } from '../src/screens/workoutDetail.js';
import { workoutRow } from '../src/components/workoutRow.js';
const flush = async () => { for (let i = 0; i < 8; i++) await Promise.resolve(); };
beforeEach(() => { vi.useFakeTimers(); });
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); document.body.replaceChildren(); });
describe('copy button', () => {
  it('copies the text, announces Copied through a polite live region and clears it', async () => {
    const writeText = vi.fn().mockResolvedValue(); vi.stubGlobal('navigator', { clipboard: { writeText } });
    const wrap = copyButton({ getText: () => 'the text' }); document.body.append(wrap);
    const status = wrap.querySelector('[aria-live="polite"]'); expect(status.getAttribute('role')).toBe('status');
    wrap.querySelector('button').click(); await flush();
    expect(writeText).toHaveBeenCalledWith('the text'); expect(status.textContent).toBe('Copied'); expect(document.querySelector('[role="dialog"]')).toBeNull();
    vi.advanceTimersByTime(2100); expect(status.textContent).toBe('');
  });
  it('falls back to a selectable dialog when the clipboard rejects, and Escape returns focus', async () => {
    vi.stubGlobal('navigator', { clipboard: { writeText: vi.fn().mockRejectedValue(new Error('denied')) } });
    const wrap = copyButton({ getText: async () => 'by hand' }); document.body.append(wrap); const button = wrap.querySelector('button');
    button.click(); await flush();
    const dialog = document.querySelector('[role="dialog"]'), area = dialog.querySelector('textarea');
    expect(dialog.getAttribute('aria-modal')).toBe('true'); expect(area.value).toBe('by hand'); expect(area.readOnly).toBe(true);
    expect(area.selectionEnd - area.selectionStart).toBe('by hand'.length); expect(document.activeElement).toBe(area);
    area.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(document.querySelector('[role="dialog"]')).toBeNull(); expect(document.activeElement).toBe(button);
  });
  it('falls back when there is no clipboard API', async () => {
    vi.stubGlobal('navigator', {});
    const wrap = copyButton({ getText: () => 'x' }); document.body.append(wrap); wrap.querySelector('button').click(); await flush();
    expect(document.querySelector('[role="dialog"] textarea').value).toBe('x');
    document.querySelector('[role="dialog"] button').click(); expect(document.querySelector('[role="dialog"]')).toBeNull();
  });
  it('an icon-only button keeps an accessible name', () => {
    const button = copyButton({ getText: () => '', iconOnly: true, label: 'Copy Pull' }).querySelector('button');
    expect(button.getAttribute('aria-label')).toBe('Copy Pull'); expect(button.textContent).toBe('');
  });
});
describe('copy on the screens', () => {
  const workout = { id: 'a', origin: 'synced', localId: 1, title: 'Pull', startedAt: '2026-10-17T07:12:00Z', note: 'Good' };
  const sets = [{ exerciseName: 'Row', exercisePosition: 0, setIndex: 0, reps: 8, weightKg: 60 }];
  const snapshot = { unit: 'kg', zone: 'UTC', profile: {}, sessions: {} };
  it('the detail screen copies its own workout text', async () => {
    const writeText = vi.fn().mockResolvedValue(); vi.stubGlobal('navigator', { clipboard: { writeText } });
    const root = document.createElement('main'); document.body.append(root);
    await renderWorkoutDetail(root, { source: { userId: 'me', listWorkouts: async () => [workout], getSets: async () => sets }, snapshot, router: { go() {} } }, 'synced', 'a');
    root.querySelector('.copy-button').click(); await flush();
    const text = writeText.mock.calls[0][0];
    expect(text).toContain('Pull\n'); expect(text).toContain('  1  60 kg × 8'); expect(text).toContain('Notes: Good');
  });
  it('a list row has an icon button that loads the sets first and does not open the workout', async () => {
    const writeText = vi.fn().mockResolvedValue(); vi.stubGlobal('navigator', { clipboard: { writeText } });
    const router = { go: vi.fn() }, getSets = vi.fn(async () => sets);
    const row = workoutRow(workout, null, snapshot, router, { getSets }); document.body.append(row);
    const button = row.querySelector('.copy-button'); expect(button.getAttribute('aria-label')).toBe('Copy Pull');
    button.click(); await flush();
    expect(getSets).toHaveBeenCalledWith(workout); expect(writeText.mock.calls[0][0]).toContain('Row'); expect(router.go).not.toHaveBeenCalled();
  });
});
