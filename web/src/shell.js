import {renderOverview} from './screens/overview.js';
import {renderWorkouts} from './screens/workouts.js';
import {renderWorkoutDetail} from './screens/workoutDetail.js';
import {renderLifts} from './screens/lifts.js';
import {renderTrends} from './screens/trends.js';
import {createRouter} from './router.js';
import {createAppearanceMenu} from './appearance.js';
import {plate} from './components/plate.js';
import {t} from './i18n.js';

export function renderShell(root, {source,snapshot,onSignOut}) {
  root.replaceChildren(); const frame=document.createElement('div');frame.className='shell';
  const sidebar=document.createElement('aside');sidebar.className='sidebar';
  const brand=document.createElement('strong');brand.className='brand';brand.textContent='IRONVELLUM'; sidebar.append(brand);
  if(snapshot?.profile) {const p=document.createElement('div');plate(p,snapshot.profile);sidebar.append(p);}
  const nav=document.createElement('nav');nav.setAttribute('aria-label','Main navigation');
  for(const [path,label] of [['/','Overview'],['/workouts','Workouts'],['/lifts','Lifts'],['/trends','Trends']]) {const a=document.createElement('a');a.href=`#${path}`;a.textContent=label;if(path==='/workouts') a.dataset.workouts='true';nav.append(a);} sidebar.append(nav);
  const main=document.createElement('main');main.className='main'; const top=document.createElement('div');top.className='topbar';
  const appearance=createAppearanceMenu(snapshot?.appearance,()=>{nav.querySelector('[data-workouts]').textContent=t('concept_trial_cpl');router.start();});nav.querySelector('[data-workouts]').textContent=t('concept_trial_cpl');top.append(appearance);
  const logout=document.createElement('button');logout.textContent='Sign out';logout.addEventListener('click',onSignOut);top.append(logout);main.append(top);
  const content=document.createElement('div');content.className='content';main.append(content);frame.append(sidebar,main);root.append(frame);
  const router=createRouter(async path=>{
    const page=document.createElement('div');page.className='route-page';content.replaceChildren(page); const ctx={source,snapshot,router};
    try {
      if(path==='/' || !path) await renderOverview(page,ctx);
      else if(path==='/workouts') await renderWorkouts(page,ctx);
      else if(path.startsWith('/workouts/')) {
        const parts=path.slice(10).split('/');
        if(parts.length===2 && ['synced','imported'].includes(parts[0]) && parts[1]) await renderWorkoutDetail(page,ctx,parts[0],decodeURIComponent(parts[1]));
        else page.textContent='Page not found.';
      }
      else if(path==='/lifts' || path.startsWith('/lifts/')) await renderLifts(page,ctx,path.startsWith('/lifts/')?decodeURIComponent(path.slice(7)):undefined);
      else if(path==='/trends') await renderTrends(page,ctx);
      else {page.textContent='Page not found.';}
    } catch(error) {page.textContent='Could not load this page. Check your connection and try again.';console.error(error);}
  });
  if(!snapshot) {const hint=document.createElement('p');hint.className='notice';hint.textContent='No phone snapshot yet. Sync the app to see your statistics.';main.insertBefore(hint,content);}
  const footer=document.createElement('footer');footer.innerHTML='Exercise art: Open Exercise Art · CC BY-SA 4.0 · Colours changed. Body map: <a href="licenses/MIT-react-native-body-highlighter.txt">MIT licence</a>.';main.append(footer);
  const disposeRouter=router.dispose;router.dispose=()=>{disposeRouter();appearance.dispose();};router.start();return router;
}
