import {bodyMapShapes} from '../generated/bodyMapShapes.js';
const {male, female} = bodyMapShapes;
const ns='http://www.w3.org/2000/svg';
function svg(tag, attrs={}) {const e=document.createElementNS(ns,tag); for(const [k,v] of Object.entries(attrs)) e.setAttribute(k,String(v)); return e;}
let serial=0;
export function renderBodyMap(el, {levels={},view='front',figure='male',look='worked'}={}) {
  const shape=figure==='female'?female:male; const id=`body-hatch-${++serial}`;
  const map=svg('svg',{viewBox:'-2650 -60 5300 10120',role:'img','aria-label':`${view} muscle map`,class:'body-map'});
  const defs=svg('defs');
  for(const [suffix,cross] of [['high',false],['very-high',true]]) {const p=svg('pattern',{id:`${id}-${suffix}`,width:350,height:350,patternUnits:'userSpaceOnUse'});p.append(svg('path',{d:cross?'M 0 0 L 350 350 M 350 0 L 0 350':'M 0 350 L 350 0',stroke:'currentColor','stroke-width':45}));defs.append(p);} map.append(defs);
  const points = p => p.map(([x,y])=>`${x},${y}`).join(' ');
  const outline=[...shape.outline,...shape.outline.slice(1,-1).reverse().map(([x,y])=>[-x,y])]; map.append(svg('polygon',{points:points(outline),class:'body-outline'}));
  for(const part of shape[view] ?? []) for(const poly of part.polygons) for(const sign of [1,-1]) {
    const value=levels[part.muscle] ?? (look==='coverage'?'none':0); const classValue=String(value).replace(/[^a-z0-9-]/gi,'');
    const polygon=svg('polygon',{points:points(poly.map(([x,y])=>[sign*x,y])),class:`body-region ${look} level-${classValue}`,'data-muscle':part.muscle,'data-level':value}); map.append(polygon);
    if(look==='fatigue' && (value===3 || value===4)) map.append(svg('polygon',{points:polygon.getAttribute('points'),class:`body-hatching ${value===4?'cross-hatch':'hatch'}`,fill:`url(#${id}-${value===4?'very-high':'high'})`}));
  }
  for(const poly of shape[view==='back'?'hairBack':'hairFront'] ?? []) for(const sign of [1,-1]) map.append(svg('polygon',{points:points(poly.map(([x,y])=>[sign*x,y])),class:'body-hair'}));
  el.replaceChildren(map); return map;
}
