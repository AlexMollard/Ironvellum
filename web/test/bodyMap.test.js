import {it,expect} from 'vitest';
import {renderBodyMap} from '../src/components/bodyMap.js';
import {bodyMapShapes} from '../src/generated/bodyMapShapes.js';
const {female,male}=bodyMapShapes;
it('renders both generated figures and all polygons for a muscle',()=>{for(const [figure,shape] of [['female',female],['male',male]]) {const el=document.createElement('div');const part=shape.front[0];renderBodyMap(el,{figure,levels:{[part.muscle]:3}});expect(el.querySelectorAll(`[data-muscle="${part.muscle}"]`).length).toBeGreaterThanOrEqual(part.polygons.length*2);expect(el.querySelector('.level-3')).not.toBeNull();}});
it('renders high and very high fatigue hatching',()=>{const el=document.createElement('div');const names=male.front.slice(0,2).map(p=>p.muscle);renderBodyMap(el,{levels:{[names[0]]:3,[names[1]]:4},look:'fatigue'});expect(el.querySelector('.hatch')).not.toBeNull();expect(el.querySelector('.cross-hatch')).not.toBeNull();});
it('uses the snapshot figure: a female snapshot draws the female outline, anything else the male one',()=>{
  const outline=figure=>{const el=document.createElement('div');renderBodyMap(el,figure?{figure}:{});return el.querySelector('.body-outline').getAttribute('points');};
  const points=shape=>[...shape.outline,...shape.outline.slice(1,-1).reverse().map(([x,y])=>[-x,y])].map(([x,y])=>`${x},${y}`).join(' ');
  expect(outline('female')).toBe(points(female)); expect(outline('male')).toBe(points(male)); expect(outline()).toBe(points(male));
  expect(points(female)).not.toBe(points(male));
});
it('frames the figure inside the view box, so the outline is never clipped',()=>{
  for(const shape of [female,male]) {const el=document.createElement('div');renderBodyMap(el,{figure:shape===female?'female':'male'});const [x,y,w,h]=el.querySelector('svg').getAttribute('viewBox').split(' ').map(Number);const widest=Math.max(...shape.outline.map(p=>p[0]));expect(x).toBeLessThanOrEqual(-widest);expect(x+w).toBeGreaterThanOrEqual(widest);expect(y).toBeLessThanOrEqual(0);expect(y+h).toBeGreaterThanOrEqual(10000);}
});
it('a click shows the muscle without focusing one side of the figure',()=>{const el=document.createElement('div');document.body.append(el);renderBodyMap(el,{levels:{}});const region=el.querySelector('.body-region:not([tabindex])');const down=new MouseEvent('mousedown',{bubbles:true,cancelable:true});region.dispatchEvent(down);expect(down.defaultPrevented).toBe(true);el.remove();});
