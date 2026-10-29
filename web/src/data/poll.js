export const INBOX_REFRESH_MS = 30 * 60 * 1000;

/**
 * Runs `run` every `everyMs` (the app's own half hour for the inbox) while the tab is visible. A tick that falls while the
 * tab is hidden is skipped, and when the tab comes back it runs at once if one was missed. Returns a stop function.
 */
export function startPolling(run, everyMs = INBOX_REFRESH_MS) {
  let missed = false;
  const tick = () => { if (document.hidden) { missed = true; return; } missed = false; run(); };
  const timer = setInterval(tick, everyMs);
  const visible = () => { if (!document.hidden && missed) tick(); };
  document.addEventListener('visibilitychange', visible);
  return () => { clearInterval(timer); document.removeEventListener('visibilitychange', visible); };
}
