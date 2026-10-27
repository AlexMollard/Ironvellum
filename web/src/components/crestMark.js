import catalogue from '../generated/catalogue.json';
export function crestMark(el, crestId) {
  const crest=catalogue.crests.find(c=>c.id===crestId); if(!crest) return el;
  const svg=document.createElementNS('http://www.w3.org/2000/svg','svg'); svg.setAttribute('viewBox','0 0 100 100'); svg.setAttribute('aria-label',crest.name); svg.classList.add('crest-mark');
  for(const s of crest.shapes) {const path=document.createElementNS(svg.namespaceURI,'path');path.setAttribute('d',s.d);path.setAttribute('fill',s.wash?'currentColor':'none');path.setAttribute('stroke','currentColor');path.setAttribute('stroke-width',s.width);path.setAttribute('opacity',s.strokeAlpha);svg.append(path);} el.append(svg); return el;
}
