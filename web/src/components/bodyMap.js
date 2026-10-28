import {bodyMapShapes} from '../generated/bodyMapShapes.js';
import {t} from '../i18n.js';
import {bindTips, roving} from './interact.js';
import {workedLevel} from './muscleStrip.js';
const {male, female} = bodyMapShapes;
const FATIGUE=['fatigue_level_fresh','fatigue_level_light','fatigue_level_moderate','fatigue_level_high','fatigue_level_very_high'];
const COVERAGE={covered:'Covered',below:'Below',heavy:'Heavy',none:'None'};
// What a region's level means in words, for the tooltip and the focus label.
export function describeLevel(look,value) {
  if(look==='fatigue') return FATIGUE[value]?t(FATIGUE[value]):'—';
  if(look==='coverage') return COVERAGE[value]??String(value);
  return workedLevel(value);
}
const tidy=code=>String(code).toLowerCase().replace(/_/g,' ').replace(/^./,c=>c.toUpperCase());
const ns='http://www.w3.org/2000/svg';
function svg(tag, attrs={}) {const e=document.createElementNS(ns,tag); for(const [k,v] of Object.entries(attrs)) e.setAttribute(k,String(v)); return e;}
let serial=0;
export function renderBodyMap(el, {levels={},view='front',figure='male',look='worked',names={}}={}) {
  const shape=figure==='female'?female:male; const id=`body-hatch-${++serial}`;
  const map=svg('svg',{viewBox:'-2650 -60 5300 10120',role:'group','aria-label':`${view} muscle map`,class:'body-map'});
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
  el.replaceChildren(map);
  // Hover, tap or focus a region for its muscle and level. One region per muscle is a tab stop; the arrow keys walk the muscles.
  const label=muscle=>`${names[muscle]||tidy(muscle)}: ${describeLevel(look,levels[muscle]??(look==='coverage'?'none':0))}`;
  const regions=[...map.querySelectorAll('.body-region')], stops=[];
  for(const region of regions) if(!stops.some(s=>s.dataset.muscle===region.dataset.muscle)) stops.push(region);
  for(const stop of stops) {stop.setAttribute('role','img');stop.setAttribute('aria-label',label(stop.dataset.muscle));}
  const mark=(region,on)=>{for(const r of regions) if(r.dataset.muscle===region.dataset.muscle) r.classList.toggle('is-active',on);};
  if(stops.length) roving(stops,(index,key)=>({ArrowRight:index+1,ArrowDown:index+1,ArrowLeft:index-1,ArrowUp:index-1})[key]);
  bindTips(map,'.body-region',region=>{const muscle=region.dataset.muscle;return {title:names[muscle]||tidy(muscle),value:describeLevel(look,levels[muscle]??(look==='coverage'?'none':0))};},{onShow:r=>mark(r,true),onHide:r=>mark(r,false)});
  return map;
}
