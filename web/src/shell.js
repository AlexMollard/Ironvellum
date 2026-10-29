import {renderOverview} from './screens/overview.js';
import {renderWorkouts} from './screens/workouts.js';
import {renderWorkoutDetail} from './screens/workoutDetail.js';
import {renderLifts} from './screens/lifts.js';
import {renderTrends} from './screens/trends.js';
import {renderFriends,friendsTitle} from './screens/friends/index.js';
import {unreadCount} from './screens/friends/inbox.js';
import {startPolling} from './data/poll.js';
import {FRIENDS_SCHEMA_VERSION} from './schema.js';
import {createRouter} from './router.js';
import {createAppearanceMenu} from './appearance.js';
import {plate} from './components/plate.js';
import {word} from './i18n.js';
import {dayKey,weekPassed,node} from './components/statCard.js';
import {formatDay,formatClock} from './format.js';
import {createElement,LayoutDashboard,CalendarDays,Dumbbell,ChartLine,Users,Inbox} from 'lucide';

const NAV=[['/','Overview',LayoutDashboard],['/workouts','Workouts',CalendarDays],['/lifts','Lifts',Dumbbell],['/trends','Trends',ChartLine],['/friends','Friends',Users]];
export function statsAsOf(snapshot,nowMs=Date.now()) {
  const day=dayKey(snapshot.builtAtMs,snapshot.zone);
  return `Stats as of ${day===dayKey(nowMs,snapshot.zone)?'today':formatDay(day)} ${formatClock(snapshot.builtAtMs,snapshot.zone)}`;
}
function credit(footer) {
  const link=(text,href)=>{const a=node('a',text);a.href=href;return a;};
  footer.append('Exercise art: Open Exercise Art · CC BY-SA 4.0 · Colours changed. Body map: ',link('MIT licence','licenses/MIT-react-native-body-highlighter.txt'),'. Type: Chakra Petch, ',link('SIL OFL','licenses/OFL-ChakraPetch.txt'),'. Icons: Lucide, ',link('ISC licence','licenses/ISC-lucide.txt'),'.');
}
export function renderShell(root, {source,snapshot,social,onSignOut,nowMs=Date.now()}) {
  root.replaceChildren(); const frame=node('div',null,'shell');
  const sidebar=node('aside',null,'sidebar');
  const brand=node('strong','Ironvellum','brand');brand.append(node('span',' WEB','brand-web'));sidebar.append(brand);
  const nav=node('nav');nav.setAttribute('aria-label','Main navigation');
  for(const [path,label,icon] of NAV) {const a=node('a');a.href=`#${path}`;a.dataset.path=path;const mark=node('span',null,'nav-icon');mark.append(createElement(icon,{width:18,height:18,'stroke-width':1.75,'aria-hidden':'true'}));a.append(mark,node('span',label,'nav-label'));if(path==='/workouts') a.dataset.workouts='true';if(path==='/friends') a.dataset.friends='true';nav.append(a);} sidebar.append(nav);
  const user=node('div',null,'sidebar-user');
  const logout=node('button','Sign out','link-button');logout.type='button';logout.addEventListener('click',onSignOut);
  if(snapshot?.profile) {const who=node('div',null,'user-line');const emblem=node('span');plate(emblem,{name:snapshot.profile.name,crestId:snapshot.profile.crestId});who.append(emblem);const text=node('span',null,'user-name');text.append(snapshot.profile.name||'',node('br'),node('span',`Level ${snapshot.profile.level}`,'muted'));who.append(text);user.append(who);}
  const asof=node('div',null,'muted user-asof');if(snapshot?.builtAtMs!=null) asof.append(node('span',`${statsAsOf(snapshot,nowMs)} · `,'asof-text'));asof.append(logout);user.append(asof,node('div','Log and edit training in the app.','faint user-note'));sidebar.append(user);
  const main=node('main',null,'main'),top=node('div',null,'topbar'),titleSlot=node('div',null,'topbar-title');
  const workoutsLabel=()=>{nav.querySelector('[data-workouts] .nav-label').textContent=word('Workouts','concept_chronicle_c');nav.querySelector('[data-friends] .nav-label').textContent=friendsTitle();inboxLink.setAttribute('aria-label',word('Inbox','concept_inbox_c'));};
  const inboxLink=node('a',null,'inbox-button');inboxLink.href='#/friends/inbox';const inboxIcon=node('span',null,'nav-icon');inboxIcon.append(createElement(Inbox,{width:18,height:18,'stroke-width':1.75,'aria-hidden':'true'}));const badge=node('span',null,'inbox-badge');badge.hidden=true;inboxLink.append(inboxIcon,badge);
  const appearance=createAppearanceMenu(snapshot?.appearance,()=>{workoutsLabel();router.start();});workoutsLabel();top.append(titleSlot,inboxLink,appearance);main.append(top);
  const setInboxUnread=n=>{badge.textContent=n>99?'99+':String(n);badge.hidden=!(n>0);inboxLink.dataset.unread=String(n>0?n:0);};
  const content=node('div',null,'content');main.append(content);frame.append(sidebar,main);root.append(frame);
  const current=path=>{const key=path==='/'||!path?'/':`/${path.split('/')[1]}`;for(const a of nav.querySelectorAll('a')) {if(a.dataset.path===key) a.setAttribute('aria-current','page');else a.removeAttribute('aria-current');}};
  const router=createRouter(async path=>{
    const page=node('div',null,'route-page');content.replaceChildren(page);titleSlot.replaceChildren();current(path); const ctx={source,snapshot,social,router,nowMs,setInboxUnread};
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
      else if(path==='/friends' || path.startsWith('/friends/')) {const parts=path.split('/');await renderFriends(page,ctx,parts[2]||'feed',parts[3]?decodeURIComponent(parts[3]):undefined);}
      else {page.textContent='Page not found.';}
    } catch(error) {page.textContent='Could not load this page. Check your connection and try again.';console.error(error);}
    if(content.firstChild!==page) return;
    const header=page.querySelector(':scope > .screen-header'); if(header) titleSlot.replaceChildren(header);
  });
  if(!snapshot) {const hint=node('p','No phone snapshot yet. Sync the app to see your statistics.','notice');main.insertBefore(hint,content);}
  else if(weekPassed(snapshot,nowMs)) {const hint=node('p',`Stats from ${formatDay(dayKey(snapshot.builtAtMs,snapshot.zone))}. Open the app to sync.`,'notice');main.insertBefore(hint,content);}
  const footer=node('footer');credit(footer);main.append(footer);
  // The inbox badge: read once and then every half hour while the tab is visible, only where the backend has the social schema.
  let stopPolling,disposed=false;
  if(social) (async()=>{
    const version=await source.schemaVersion?.().catch?.(()=>null);if(disposed||(version!=null&&version<FRIENDS_SCHEMA_VERSION)) return;
    const refresh=async()=>{try {const {items,seenAtMs}=await social.inbox();if(!disposed) setInboxUnread(unreadCount(items,seenAtMs));} catch {}};
    refresh();stopPolling=startPolling(refresh);
  })();
  const disposeRouter=router.dispose;router.dispose=()=>{disposed=true;stopPolling?.();disposeRouter();appearance.dispose();};router.start();return router;
}
