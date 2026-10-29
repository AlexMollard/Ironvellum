import {renderOverview} from './screens/overview.js';
import {renderWorkouts} from './screens/workouts.js';
import {renderWorkoutDetail} from './screens/workoutDetail.js';
import {renderLifts} from './screens/lifts.js';
import {renderTrends} from './screens/trends.js';
import {renderRoutines} from './screens/routines.js';
import {renderFriends,friendsTitle,TABS} from './screens/friends/index.js';
import {unreadCount} from './screens/friends/inbox.js';
import {startPolling} from './data/poll.js';
import {FRIENDS_SCHEMA_VERSION} from './schema.js';
import {createRouter} from './router.js';
import {createAppearanceMenu} from './appearance.js';
import {plate} from './components/plate.js';
import {word,workoutsTitle} from './i18n.js';
import {dayKey,weekPassed,node,link} from './components/statCard.js';
import {formatDay,formatClock} from './format.js';
import {createElement,LayoutDashboard,CalendarDays,Dumbbell,ChartLine,ListChecks,Users,Inbox} from 'lucide';

const NAV=[['/','Overview',LayoutDashboard],['/workouts','Workouts',CalendarDays],['/lifts','Lifts',Dumbbell],['/trends','Trends',ChartLine],['/routines','Routines',ListChecks],['/friends','Friends',Users]];
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
  // The Friends pages, nested under Friends and shown while that section is open; the narrow bottom bar swaps them for a select in the page.
  const friendsPages=node('div',null,'nav-sub');friendsPages.hidden=true;
  for(const [path,label,icon] of NAV) {const a=node('a');a.href=`#${path}`;a.dataset.path=path;const mark=node('span',null,'nav-icon');mark.append(createElement(icon,{width:18,height:18,'stroke-width':1.75,'aria-hidden':'true'}));a.append(mark,node('span',label,'nav-label'));if(path==='/workouts') a.dataset.workouts='true';if(path==='/friends') a.dataset.friends='true';if(path==='/routines') a.dataset.routines='true';nav.append(a);
    if(path==='/friends') {nav.append(friendsPages);for(const [id,text] of TABS) {const s=node('a',text(),'nav-sub-link');s.href=`#/friends/${id}`;s.dataset.tab=id;friendsPages.append(s);}}
  } sidebar.append(nav);
  const user=node('div',null,'sidebar-user');
  const logout=node('button','Sign out','link-button');logout.type='button';logout.addEventListener('click',onSignOut);
  if(snapshot?.profile) {const who=node('div',null,'user-line');const emblem=node('span');plate(emblem,{name:snapshot.profile.name,crestId:snapshot.profile.crestId});who.append(emblem);const text=node('span',null,'user-name');text.append(snapshot.profile.name||'',node('br'),node('span',`Level ${snapshot.profile.level}`,'muted'));who.append(text);user.append(who);}
  const asof=node('div',null,'muted user-asof');if(snapshot?.builtAtMs!=null) asof.append(node('span',`${statsAsOf(snapshot,nowMs)} · `,'asof-text'));asof.append(logout);user.append(asof,node('div','Log and edit training in the app.','faint user-note'));sidebar.append(user);
  const main=node('main',null,'main');main.id='content';main.tabIndex=-1;const top=node('div',null,'topbar'),titleSlot=node('div',null,'topbar-title');
  let unread=0;const inboxName=()=>unread>0?`${word('Inbox','concept_inbox_c')}, ${unread} unread`:word('Inbox','concept_inbox_c');
  const workoutsLabel=()=>{nav.querySelector('[data-workouts] .nav-label').textContent=workoutsTitle();nav.querySelector('[data-friends] .nav-label').textContent=friendsTitle();for(const [id,text] of TABS) nav.querySelector(`.nav-sub-link[data-tab="${id}"]`).textContent=text();nav.querySelector('[data-routines] .nav-label').textContent=word('Routines','concept_rite_cpl');inboxLink.setAttribute('aria-label',inboxName());};
  const inboxLink=node('a',null,'inbox-button');inboxLink.href='#/friends/inbox';const inboxIcon=node('span',null,'nav-icon');inboxIcon.append(createElement(Inbox,{width:18,height:18,'stroke-width':1.75,'aria-hidden':'true'}));const badge=node('span',null,'inbox-badge');badge.hidden=true;inboxLink.append(inboxIcon,badge);
  const appearance=createAppearanceMenu(snapshot?.appearance,()=>{workoutsLabel();router.start();});workoutsLabel();top.append(titleSlot,inboxLink,appearance);main.append(top);
  const setInboxUnread=n=>{unread=n>0?n:0;badge.textContent=n>99?'99+':String(n);badge.hidden=!(n>0);inboxLink.dataset.unread=String(unread);inboxLink.setAttribute('aria-label',inboxName());};
  const content=node('div',null,'content');main.append(content);frame.append(sidebar,main);
  // The hash router owns the address, so the skip link moves focus itself instead of pointing at an anchor.
  const skip=node('a','Skip to content','skip-link');skip.href='#content';skip.addEventListener('click',e=>{e.preventDefault();main.focus();});root.append(skip,frame);
  const current=path=>{const key=path==='/'||!path?'/':`/${path.split('/')[1]}`;for(const a of nav.querySelectorAll(':scope > a')) {if(a.dataset.path===key) a.setAttribute('aria-current',key==='/friends'?'true':'page');else a.removeAttribute('aria-current');}
    const inFriends=key==='/friends',tab=path.split('/')[2]||'feed';friendsPages.hidden=!inFriends;for(const a of friendsPages.children) {if(inFriends&&a.dataset.tab===tab) a.setAttribute('aria-current','page');else a.removeAttribute('aria-current');}};
  // A screen is what the path names up to its in-page choice: Lifts and Lifts/<name> are one, Workouts and a workout are two, each Friends page is its own.
  const screenOf=p=>{const s=(p||'/').split('/'),a=s[1]||'';return a==='friends'?`friends/${s[2]||'feed'}`:a==='workouts'?(s[2]?'workout':'workouts'):a;};
  let lastPath=null,lastScreen=null,forceFocus=false;
  const fail=(page,text,retry)=>{const message=node('p',text,'route-message');message.tabIndex=-1;page.replaceChildren(message);if(retry){const again=node('button','Try again','pill');again.type='button';again.addEventListener('click',()=>{forceFocus=true;router.start();});page.append(again);}page.append(link('Back to Overview','/',router,'route-home'));};
  const router=createRouter(async path=>{
    const screen=screenOf(path),same=lastPath!==null&&lastScreen===screen&&lastPath!==path;
    // What had focus, so a choice made inside a screen (a lift, a routine, a friend) can give it back to the same link once the screen is drawn again.
    const held=document.activeElement,focusKey=held&&held!==document.body?(held.getAttribute?.('href')||held.dataset?.focusKey||null):null;
    const page=node('div',null,'route-page');content.replaceChildren(page);titleSlot.replaceChildren();current(path); const ctx={source,snapshot,social,router,nowMs,setInboxUnread,sameScreen:same};
    try {
      if(path==='/' || !path) await renderOverview(page,ctx);
      else if(path==='/workouts') await renderWorkouts(page,ctx);
      else if(path.startsWith('/workouts/')) {
        const parts=path.slice(10).split('/');
        if(parts.length===2 && ['synced','imported'].includes(parts[0]) && parts[1]) await renderWorkoutDetail(page,ctx,parts[0],decodeURIComponent(parts[1]));
        else fail(page,'Page not found.');
      }
      else if(path==='/lifts' || path.startsWith('/lifts/')) await renderLifts(page,ctx,path.startsWith('/lifts/')?decodeURIComponent(path.slice(7)):undefined);
      else if(path==='/trends') await renderTrends(page,ctx);
      else if(path==='/routines' || path.startsWith('/routines/')) await renderRoutines(page,ctx,path.startsWith('/routines/')?decodeURIComponent(path.slice(10)):undefined);
      else if(path==='/friends' || path.startsWith('/friends/')) {const parts=path.split('/');await renderFriends(page,ctx,parts[2]||'feed',parts[3]?decodeURIComponent(parts[3]):undefined);}
      else {fail(page,'Page not found.');}
    } catch(error) {fail(page,'Could not load this page. Check your connection and try again.',true);console.error(error);}
    if(content.firstChild!==page) return;
    const header=page.querySelector(':scope > .screen-header'); if(header) titleSlot.replaceChildren(header);
    // The tab and the history name the page; focus goes to its heading when the screen changes (not on first load, and not when the same page is drawn again, as the appearance menu does).
    const h1=titleSlot.querySelector('h1')??page.querySelector('h1'),tab=screen.startsWith('friends/')?TABS.find(([id])=>id===screen.slice(8)):null;
    const name=page.dataset.title||(tab&&tab[1]()!==(h1?.textContent??friendsTitle())?`${tab[1]()} · ${h1?.textContent??friendsTitle()}`:h1?.textContent)||page.querySelector('.route-message')?.textContent;
    document.title=name?`${name} · Ironvellum`:'Ironvellum';
    if(h1){h1.tabIndex=-1;}
    if(lastPath!==null){
      if(same){if(focusKey&&(!document.activeElement||document.activeElement===document.body)) [...page.querySelectorAll('a,button')].find(e=>(e.getAttribute('href')||e.dataset.focusKey)===focusKey)?.focus({preventScroll:true});}
      else if(path!==lastPath||forceFocus) (h1??page.querySelector('.route-message'))?.focus({preventScroll:true});
    }
    lastPath=path;lastScreen=screen;forceFocus=false;
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
