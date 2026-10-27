import catalogue from '../generated/catalogue.json';
export function plate(el, {name='', level, crestId} = {}) {
  el.classList.add('plate'); const crest = catalogue.crests.find(c=>c.id===crestId); if (crest?.ring) {el.dataset.ring = crest.ring; el.style.setProperty('--plate-rim',`var(--metal-${crest.ring.toLowerCase()})`);}
  const initials = document.createElement('span'); initials.className='plate-initials'; initials.textContent=name.trim().split(/\s+/).filter(Boolean).slice(0,2).map(x=>x[0]).join('').toUpperCase() || '•'; el.append(initials);
  if (level != null) { const badge=document.createElement('span');badge.className='plate-level';badge.textContent=String(level);el.append(badge); }
  return el;
}
