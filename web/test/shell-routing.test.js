import {it,expect,vi} from 'vitest';
let resolveOverview;
vi.mock('../src/screens/overview.js',()=>({renderOverview:async root=>{await new Promise(resolve=>{resolveOverview=resolve});root.textContent='old private overview';}}));
vi.mock('../src/screens/workouts.js',()=>({renderWorkouts:async root=>{root.textContent='workouts';}}));
vi.mock('../src/screens/workoutDetail.js',()=>({renderWorkoutDetail:async()=>{}}));
vi.mock('../src/screens/lifts.js',()=>({renderLifts:async()=>{}}));
vi.mock('../src/screens/trends.js',()=>({renderTrends:async()=>{}}));
import {renderShell} from '../src/shell.js';
it('detaches an earlier asynchronous page when navigation changes',async()=>{location.hash='#/';const root=document.createElement('div');document.body.append(root);const router=renderShell(root,{source:{},snapshot:null,onSignOut:()=>{}});router.go('/workouts');window.dispatchEvent(new HashChangeEvent('hashchange'));await Promise.resolve();resolveOverview();await Promise.resolve();expect(root.textContent).toContain('workouts');expect(root.textContent).not.toContain('old private overview');router.dispose();root.remove();});
