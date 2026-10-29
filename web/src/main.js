import '@fontsource/chakra-petch/latin-400.css';
import '@fontsource/chakra-petch/latin-500.css';
import '@fontsource/chakra-petch/latin-600.css';
import '@fontsource/chakra-petch/latin-700.css';
import './theme.css';
import './friends.css';
import './routines.css';
import {backendClient,loadBackend,sharedBackend,saveBackend,clearBackend,signIn,signInWithGoogle,restoreSession,observeSignOut,signOut} from './auth.js';
import {createSupabaseSource,createFixtureSource,SnapshotTooNew} from './source.js';
import {createSupabaseSocial} from './data/socialSource.js';
import {NEEDED_SCHEMA_VERSION} from './schema.js';
import {renderShell} from './shell.js';
import {applyAppearance} from './theme.js';
import {exampleWorkout,exampleChart} from './signinExample.js';
import {lastWorkoutCard} from './components/lastWorkoutCard.js';
import {renderLineChart} from './components/lineChart.js';
import {card} from './components/statCard.js';
const root=document.querySelector('#app');
function message(parent,text,cls='notice') {const p=document.createElement('p');p.className=cls;p.textContent=text;parent.append(p);return p;}
let activeRouter, authSubscription, generation=0;
function clearViewer() { generation++; activeRouter?.dispose();activeRouter=undefined;authSubscription?.unsubscribe();authSubscription=undefined; }
async function openSource(source,client,fixtureSocial) {
  clearViewer();const current=generation;
  if(client) authSubscription=observeSignOut(client,()=>signInPage());
  let snapshot=null, warning='';
  try {const version=await source.schemaVersion();if(version!=null && version<NEEDED_SCHEMA_VERSION) warning=`Needs schema ${NEEDED_SCHEMA_VERSION}; this backend reports ${version}.`;snapshot=await source.getSnapshot();} catch(e) { if(e instanceof SnapshotTooNew) warning='This snapshot is newer than this viewer. Update the web viewer.'; else warning='Could not load the phone snapshot. Workouts remain available.'; }
  if(current!==generation) return;
  const social=fixtureSocial??(client&&source.userId?createSupabaseSocial(client,source.userId):null);
  activeRouter=renderShell(root,{source,snapshot,social,onSignOut:async()=>{try {await signOut();} finally {signInPage();}}});
  if(warning) {const p=document.createElement('p');p.className='notice';p.textContent=warning;root.querySelector('main').prepend(p);}
}
let devAppearance, offline=false;
function exampleBlock() {
  const example=document.createElement('div');example.className='example';example.setAttribute('aria-label','Example');
  message(example,'Example · not your data','eyebrow example-label');
  example.append(lastWorkoutCard({...exampleWorkout,heading:'Your last workout'}));
  const chart=card(null,'example-chart');message(chart,exampleChart.title,'muted');const plot=document.createElement('div');chart.append(plot);example.append(chart);
  queueMicrotask(()=>renderLineChart(plot,{series:[{points:exampleChart.points}],label:exampleChart.title,unit:'kg',height:190,details:false}));
  return example;
}
function signInPage(initialMessage = '') {
  clearViewer();
  applyAppearance(devAppearance);root.replaceChildren();const page=document.createElement('main');page.className='signin';
  const brand=document.createElement('div');brand.className='signin-brand';brand.append('Ironvellum ');const web=document.createElement('span');web.className='brand-web';web.textContent='WEB';brand.append(web);
  const intro=document.createElement('section');intro.className='signin-intro';const title=document.createElement('h1');for(const line of ['Your training, ','on a bigger screen.']) {const span=document.createElement('span');span.className='line';span.textContent=line;title.append(span);}intro.append(title);message(intro,'Every workout, every lift and variation, your trends and deeds, straight from your phone. Log in the app; look back here.','lede');
  intro.append(exampleBlock());
  const form=document.createElement('form');form.className='card signin-form';const heading=document.createElement('h2');heading.textContent='Sign in';form.append(heading);message(form,'Use the account you made in the app.','muted');
  const config=loadBackend();
  function field(label,type,value='') {const l=document.createElement('label');l.textContent=label;const input=document.createElement('input');input.type=type;input.value=value;l.append(input);form.append(l);return input;}
  const email=field('Email','email');email.autocomplete='email';email.required=true;
  const password=field('Password','password');password.autocomplete='current-password';password.required=true;
  const settings=document.createElement('details');const summary=document.createElement('summary');summary.textContent='Use my own backend';settings.append(summary);
  function backendField(label,value,type) {const l=document.createElement('label');l.textContent=label;const input=document.createElement('input');input.type=type;input.value=value;l.append(input);settings.append(l);return input;}
  const url=backendField('Backend URL',config.url,'url'), key=backendField('Publishable key',config.key,'text');if(config.own || !config.url) settings.open=true;
  if(config.own) {const button=document.createElement('button');button.type='button';button.textContent='Use shared backend';button.addEventListener('click',()=>{clearBackend();const defaults=loadBackend();url.value=defaults.url;key.value=defaults.key;settings.open=false;updateGoogle();probe();});settings.append(button);}
  const status=message(form,initialMessage,'notice');const submit=document.createElement('button');submit.type='submit';submit.className='primary';submit.textContent='Sign in';form.append(submit);
  const divider=document.createElement('div');divider.className='or-divider';divider.textContent='or';divider.setAttribute('aria-hidden','true');
  const google=document.createElement('button');google.type='button';google.className='google-button';google.textContent='Continue with Google';form.append(divider,google);
  const links=document.createElement('div');links.className='signin-links';const forgot=document.createElement('span');forgot.className='muted';forgot.textContent='Forgot it? Reset in the app.';links.append(settings,forgot);form.append(links);
  const shared=sharedBackend();
  function updateGoogle() {google.hidden=divider.hidden=import.meta.env.VITE_GOOGLE_SIGNIN!=='true' || url.value.trim()!==shared.url || key.value.trim()!==shared.key;}
  url.addEventListener('input',updateGoogle);key.addEventListener('input',updateGoogle);updateGoogle();
  google.addEventListener('click',async()=>{google.disabled=true;try {if(!await probe()) return;clearBackend();await signInWithGoogle(shared);} catch {status.textContent='Could not start Google sign-in. Try again.';} finally {google.disabled=false;}});
  let probeSerial=0;
  async function probe() {if(offline) return true;const serial=++probeSerial;if(!url.value || !key.value) {status.textContent='Enter your backend URL and publishable key.';return false;} try {const c=backendClient({url:url.value,key:key.value});const source=createSupabaseSource(c,'probe');const version=await source.schemaVersion();if(serial!==probeSerial)return true;status.textContent=version==null?'Could not reach this backend. Trying to sign in anyway.':version<NEEDED_SCHEMA_VERSION?`Needs schema ${NEEDED_SCHEMA_VERSION}; this backend reports ${version}.`:'';return true;} catch {if(serial===probeSerial) status.textContent='Could not reach this backend. Trying to sign in anyway.';return true;}}
  url.addEventListener('change',probe);key.addEventListener('change',probe);if(config.url && config.key && !initialMessage) probe();
  form.addEventListener('submit',async event=>{event.preventDefault();submit.disabled=true;try {if(!await probe()) return;const cfg={url:url.value.trim(),key:key.value.trim()};const client=backendClient(cfg);const session=await signIn({...cfg,client},email.value,password.value);if(cfg.url===shared.url && cfg.key===shared.key) clearBackend();else saveBackend(cfg.url,cfg.key);await openSource(createSupabaseSource(client,session.user.id),client);} catch(error) {status.textContent=error.message || 'Could not sign in.';} finally {submit.disabled=false;}});
  page.append(brand,intro,form);const footer=document.createElement('footer');footer.textContent='Exercise art: Open Exercise Art · CC BY-SA 4.0 · Colours changed';page.append(footer);root.append(page);
}
const devParams=import.meta.env.DEV ? new URLSearchParams(location.search) : null;
if(devParams?.has('fixture')) {
  // Development only: `import.meta.env.DEV` is false in a build, so this branch and the fixtures it imports are dropped. ?fixture=1 opens the signed-in screens, ?fixture=signin the sign-in page with no network; &theme=light|dark (default dark).
  const mode=devParams.get('theme')==='light' ? 'light' : 'dark';
  if(devParams.get('fixture')==='signin') {devAppearance={mode};offline=true;signInPage();}
  else Promise.all([import('../test/fixtures/snapshot-v1.json'),import('../test/fixtures/rows.json'),import('../dev/screenshotOverlay.js'),import('../dev/socialFixture.js')]).then(([snapshot,rows,overlay,social])=>{const data=overlay.withScreenshotData(structuredClone(snapshot.default));data.appearance={...data.appearance,mode};return openSource(createFixtureSource({snapshot:data,rows:rows.default}),undefined,social.createFixtureSocial());}).catch(()=>{root.textContent='Example data is unavailable.';});
} else {
  const params=new URLSearchParams(location.search);
  const fragment=location.hash.startsWith('#/') ? new URLSearchParams() : new URLSearchParams(location.hash.slice(1));
  const failed=params.has('error') || fragment.has('error');
  if(failed) {
    const clean=new URL(location.href);for(const key of ['error','error_code','error_description']) clean.searchParams.delete(key);if(fragment.has('error')) clean.hash='';history.replaceState(null,'',clean);
    signInPage('Google sign-in was cancelled or failed. Please try again.');
  } else {
    const config=loadBackend();
    restoreSession(config).then(({client,session})=>session ? openSource(createSupabaseSource(client,session.user.id),client) : signInPage()).catch(()=>signInPage('Could not restore your session. Please sign in again.'));
  }
}
