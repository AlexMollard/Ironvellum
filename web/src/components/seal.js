import catalogue from '../generated/catalogue.json';
export function seal(el,{rarity,category,earned=true}={}) {
  const geometry=catalogue.seals[rarity] ?? catalogue.seals[String(rarity).toUpperCase()]; if(!geometry) return el;
  const ns='http://www.w3.org/2000/svg', svg=document.createElementNS(ns,'svg'); svg.setAttribute('viewBox','0 0 64 64');svg.setAttribute('class',`deed-seal ${earned?'earned':'unearned'}`);svg.dataset.rarity=rarity;
  for(const key of ['outer','rim','core']) {if(!geometry[key]?.length) continue;const p=document.createElementNS(ns,'polygon');p.setAttribute('points',geometry[key].map(p=>p.join(',')).join(' '));p.setAttribute('class',`seal-${key}`);svg.append(p);}
  for(const d of catalogue.glyphs[category] ?? []) {const p=document.createElementNS(ns,'path');p.setAttribute('d',d);p.setAttribute('class','seal-glyph');svg.append(p);} el.append(svg);return el;
}
