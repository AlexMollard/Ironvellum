import words from './generated/words.json';
let themed = false;
export function setWordsMode(value) { themed = !!value; }
export const isThemed = () => themed;
// A label the app words one way plain and another way themed: `plain` is this viewer's plain text, the themed word comes from words.json (optionally wrapped).
export function word(plain, key, wrap = w => w) { return themed ? wrap(t(key, 'themed')) : plain; }
export function t(key, form) { const entry = words[key]; if (!entry) { if (import.meta.env.DEV) throw new Error(`Unknown word: ${key}`); return key; } return entry[form ?? (themed ? 'themed' : 'plain')]; }
export function fromServer(text) { if (themed) return text; let result = text; for (const term of words.serverTerms ?? []) { for (const [a,b] of [[term.themedPl,term.plainPl],[term.themed,term.plain]]) { if (a && b) result = result.replace(new RegExp(`\\b${a.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')}\\b`, 'gi'), b); } } return result; }
// Fills the phone's positional placeholders (%1$s, %2$d) in a generated sentence; web figures are formatted by the caller.
export function format(text, ...args) { return String(text).replace(/%(\d+)\$[sd]/g, (_, n) => String(args[Number(n) - 1] ?? '')); }
