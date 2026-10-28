import catalogue from '../generated/catalogue.json';
import tokens from '../generated/tokens.json';

// The app's DeedSeal (ui/titles/DeedSeal.kt): a dark enamel ground, the tier's metal as a faint wash and a
// diagonal-gradient outline, its ornaments, and the category glyph stroked in the tier's tint.
// A seal is enamel, so it takes the dark theme's colours in either theme.
const METAL={Common:'Iron',Rare:'Bronze',Epic:'Gold',Masterwork:'Prism'};
const ns='http://www.w3.org/2000/svg';
let nextId=0;

function shape(tag,attrs) {const el=document.createElementNS(ns,tag);for(const [k,v] of Object.entries(attrs)) el.setAttribute(k,v);return el;}
const points=list=>list.map(p=>p.join(',')).join(' ');

export function seal(el,{rarity,category,earned=true}={}) {
  const key=Object.keys(catalogue.seals).find(k=>k.toUpperCase()===String(rarity).toUpperCase());
  const geometry=catalogue.seals[key]; if(!geometry) return el;
  const dark=tokens.dark, ramp=dark.metal[METAL[key]];
  const tint=earned?dark.rarity[key]:dark.paper.InkMuted;
  const svg=shape('svg',{viewBox:'0 0 64 64',class:`deed-seal ${earned?'earned':'unearned'}`,'aria-hidden':'true'});svg.dataset.rarity=key;
  let metal=tint;
  if(earned) {
    const id=`seal-metal-${nextId++}`, grad=shape('linearGradient',{id,gradientUnits:'userSpaceOnUse',x1:0,y1:0,x2:64,y2:64});
    ramp.colors.forEach((c,i)=>grad.append(shape('stop',{offset:ramp.stops[i],'stop-color':c})));
    const defs=shape('defs',{});defs.append(grad);svg.append(defs);metal=`url(#${id})`;
  }
  const line=(w,opacity=1)=>({fill:'none',stroke:metal,'stroke-width':w,'stroke-opacity':opacity,'stroke-linecap':'round','stroke-linejoin':'round'});
  const outer=points(geometry.outer);
  svg.append(shape('polygon',{points:outer,fill:dark.paper.PlateGround}),shape('polygon',{points:outer,fill:metal,'fill-opacity':.08}),shape('polygon',{points:outer,...line(1.8)}));
  if(geometry.rim?.length) svg.append(shape('polygon',{points:points(geometry.rim),...line(.85,.48)}));
  if(geometry.branches) svg.append(shape('path',{d:geometry.branches,...line(1.1,.85)}));
  for(const d of geometry.leaves ?? []) svg.append(shape('path',{d,fill:metal,'fill-opacity':.09}),shape('path',{d,...line(1.1,.9)}));
  if(geometry.core?.length) svg.append(shape('polygon',{points:points(geometry.core),...line(.85,.45)}));
  const paths=catalogue.glyphs[category] ?? catalogue.glyphs.Activities;
  if(category!=null && paths) {
    const g=geometry.glyphSize, offset=(64-g)/2;
    const glyph=shape('g',{transform:`translate(${offset} ${offset}) scale(${g/24})`,fill:'none',stroke:tint,'stroke-width':1.8,'stroke-linecap':'round','stroke-linejoin':'round',class:'seal-glyph'});
    for(const d of paths) glyph.append(shape('path',{d}));
    svg.append(glyph);
  }
  el.append(svg);return el;
}
