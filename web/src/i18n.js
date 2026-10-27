import words from './generated/words.json';
let themed = false;
export function setWordsMode(value) { themed = !!value; }
export function t(key, form) { const entry = words[key]; if (!entry) { if (import.meta.env.DEV) throw new Error(`Unknown word: ${key}`); return key; } return entry[form ?? (themed ? 'themed' : 'plain')]; }
export function fromServer(text) { if (themed) return text; let result = text; for (const term of words.serverTerms ?? []) { for (const [a,b] of [[term.themedPl,term.plainPl],[term.themed,term.plain]]) { if (a && b) result = result.replace(new RegExp(`\\b${a.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')}\\b`, 'gi'), b); } } return result; }
