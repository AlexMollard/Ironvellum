import {applyAppearance, effectiveAppearance, accents} from './theme.js';
const KEY = 'ironvellum.appearance';
export function loadAppearance() { try { return JSON.parse(localStorage.getItem(KEY)) || {}; } catch { return {}; } }
export function saveAppearance(value) { try { localStorage.setItem(KEY,JSON.stringify(value)); } catch {} }
export function clearAppearance() { try { localStorage.removeItem(KEY); } catch {} }
export function createAppearanceMenu(phone, onChange = () => {}) {
  let override = loadAppearance();
  const details = document.createElement('details'); details.className = 'appearance';
  const summary = document.createElement('summary'); summary.textContent = 'Appearance'; details.append(summary);
  const body = document.createElement('div'); body.className = 'appearance-menu'; details.append(body);
  function refresh() {
    const effective = applyAppearance(phone,override); body.replaceChildren();
    function select(label, entries, value, change) {
      const wrap = document.createElement('label'); wrap.textContent = label;
      const input = document.createElement('select');
      for (const [id,name] of entries) { const option = document.createElement('option'); option.value=id; option.textContent=name; input.append(option); }
      input.value = value; input.addEventListener('change',()=>{change(input.value); saveAppearance(override); refresh(); onChange();}); wrap.append(input); body.append(wrap);
    }
    select('Theme', [['system','System'],['dark','Dark'],['light','Light']],override.mode ?? phone?.mode ?? 'system',v=>override.mode=v);
    const options = accents[effective.light ? 'light' : 'dark'];
    select('Accent', [['','Match phone'],...options.map(a=>[a.id,a.name[effective.themed?'themed':'plain']])],override.accentId ?? '',v=>{if(v) override.accentId=v; else delete override.accentId;});
    select('Names',[['plain','Plain'],['themed','Themed']],effective.themed?'themed':'plain',v=>override.themed=v==='themed');
    const reset = document.createElement('button'); reset.type='button'; reset.textContent='Match my phone'; reset.addEventListener('click',()=>{override={};clearAppearance();refresh();onChange();}); body.append(reset);
  }
  const media = globalThis.matchMedia?.('(prefers-color-scheme: light)'); media?.addEventListener?.('change',refresh);
  details.dispose = () => media?.removeEventListener?.('change',refresh);
  refresh(); return details;
}
