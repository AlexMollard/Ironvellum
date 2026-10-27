import {it,expect} from 'vitest';
import {renderBodyMap} from '../src/components/bodyMap.js';
import {bodyMapShapes} from '../src/generated/bodyMapShapes.js';
const {female,male}=bodyMapShapes;
it('renders both generated figures and all polygons for a muscle',()=>{for(const [figure,shape] of [['female',female],['male',male]]) {const el=document.createElement('div');const part=shape.front[0];renderBodyMap(el,{figure,levels:{[part.muscle]:3}});expect(el.querySelectorAll(`[data-muscle="${part.muscle}"]`).length).toBeGreaterThanOrEqual(part.polygons.length*2);expect(el.querySelector('.level-3')).not.toBeNull();}});
it('renders high and very high fatigue hatching',()=>{const el=document.createElement('div');const names=male.front.slice(0,2).map(p=>p.muscle);renderBodyMap(el,{levels:{[names[0]]:3,[names[1]]:4},look:'fatigue'});expect(el.querySelector('.hatch')).not.toBeNull();expect(el.querySelector('.cross-hatch')).not.toBeNull();});
