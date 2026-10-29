const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * The folio's figures from the sessions already loaded: a port of the phone's `LifterSummary.of`, pinned by `lifterSummaryCases`
 * in test/fixtures/parity.json. `sessions` is [{ completedAtMs, xp, strength }]; `perWeek` is printed with one decimal.
 */
export function lifterSummary(sessions, nowMs) {
  const times = sessions.map(s => s.completedAtMs).filter(ms => ms != null).sort((a, b) => a - b);
  const chrono = [...sessions].sort((a, b) => (a.completedAtMs ?? Infinity) - (b.completedAtMs ?? Infinity));
  const series = chrono.map(s => s.strength);
  return {
    count: sessions.length,
    xp: sessions.reduce((sum, s) => sum + s.xp, 0),
    perWeek: (times.filter(ms => nowMs - ms <= 28 * DAY_MS).length / 4).toFixed(1),
    daysSince: times.length ? Math.floor((nowMs - times[times.length - 1]) / DAY_MS) : null,
    series,
    peak: series.length ? Math.max(...series) : null,
  };
}
