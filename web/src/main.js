import './theme.css';
import {backendClient,loadBackend,sharedBackend,saveBackend,clearBackend,signIn,signInWithGoogle,restoreSession,observeSignOut,signOut} from './auth.js';
import {createSupabaseSource,createFixtureSource,SnapshotTooNew} from './source.js';
import {NEEDED_SCHEMA_VERSION} from './schema.js';
import {renderShell} from './shell.js';
import {applyAppearance} from './theme.js';
const root=document.querySelector('#app');
function message(parent,text,cls='notice') {const p=document.createElement('p');p.className=cls;p.textContent=text;parent.append(p);return p;}
let activeRouter, authSubscription, generation=0;
function clearViewer() { generation++; activeRouter?.dispose();activeRouter=undefined;authSubscription?.unsubscribe();authSubscription=undefined; }
async function openSource(source,client) {
  clearViewer();const current=generation;
  if(client) authSubscription=observeSignOut(client,()=>signInPage());
  let snapshot=null, warning='';
  try {const version=await source.schemaVersion();if(version!=null && version<NEEDED_SCHEMA_VERSION) warning=`Needs schema ${NEEDED_SCHEMA_VERSION}; this backend reports ${version}.`;snapshot=await source.getSnapshot();} catch(e) { if(e instanceof SnapshotTooNew) warning='This snapshot is newer than this viewer. Update the web viewer.'; else warning='Could not load the phone snapshot. Workouts remain available.'; }
  if(current!==generation) return;
  activeRouter=renderShell(root,{source,snapshot,onSignOut:async()=>{try {await signOut();} finally {signInPage();}}});
  if(warning) {const p=document.createElement('p');p.className='notice';p.textContent=warning;root.querySelector('main').prepend(p);}
}
function signInPage(initialMessage = '') {
  clearViewer();
  applyAppearance();root.replaceChildren();const page=document.createElement('main');page.className='signin';
  const intro=document.createElement('section');intro.className='signin-intro';const title=document.createElement('h1');title.textContent='IRONVELLUM';intro.append(title);message(intro,'Your training, on a bigger screen.');
  const example=document.createElement('div');example.className='card example';const e=document.createElement('strong');e.textContent='Example';example.append(e);message(example,'Your workouts, lifts and progress appear here after your phone syncs.');intro.append(example);
  const form=document.createElement('form');form.className='card signin-form';const heading=document.createElement('h2');heading.textContent='Sign in';form.append(heading);
  const config=loadBackend();
  function field(label,type,value='') {const l=document.createElement('label');l.textContent=label;const input=document.createElement('input');input.type=type;input.value=value;l.append(input);form.append(l);return input;}
  const email=field('Email','email');email.autocomplete='email';email.required=true;
  const password=field('Password','password');password.autocomplete='current-password';password.required=true;
  const settings=document.createElement('details');const summary=document.createElement('summary');summary.textContent='Use my own backend';settings.append(summary);form.append(settings);
  function backendField(label,value,type) {const l=document.createElement('label');l.textContent=label;const input=document.createElement('input');input.type=type;input.value=value;l.append(input);settings.append(l);return input;}
  const url=backendField('Backend URL',config.url,'url'), key=backendField('Publishable key',config.key,'text');if(config.own || !config.url) settings.open=true;
  if(config.own) {const button=document.createElement('button');button.type='button';button.textContent='Use shared backend';button.addEventListener('click',()=>{clearBackend();const defaults=loadBackend();url.value=defaults.url;key.value=defaults.key;settings.open=false;updateGoogle();probe();});settings.append(button);}
  const status=message(form,initialMessage,'notice');const submit=document.createElement('button');submit.type='submit';submit.textContent='Sign in';form.append(submit);
  const google=document.createElement('button');google.type='button';google.textContent='Continue with Google';form.append(google);
  const shared=sharedBackend();
  function updateGoogle() {google.hidden=import.meta.env.VITE_GOOGLE_SIGNIN!=='true' || url.value.trim()!==shared.url || key.value.trim()!==shared.key;}
  url.addEventListener('input',updateGoogle);key.addEventListener('input',updateGoogle);updateGoogle();
  google.addEventListener('click',async()=>{google.disabled=true;try {if(!await probe()) return;clearBackend();await signInWithGoogle(shared);} catch {status.textContent='Could not start Google sign-in. Try again.';} finally {google.disabled=false;}});
  let probeSerial=0;
  async function probe() {const serial=++probeSerial;if(!url.value || !key.value) {status.textContent='Enter your backend URL and publishable key.';return false;} try {const c=backendClient({url:url.value,key:key.value});const source=createSupabaseSource(c,'probe');const version=await source.schemaVersion();if(serial!==probeSerial)return true;status.textContent=version==null?'Could not reach this backend. Trying to sign in anyway.':version<NEEDED_SCHEMA_VERSION?`Needs schema ${NEEDED_SCHEMA_VERSION}; this backend reports ${version}.`:'';return true;} catch {if(serial===probeSerial) status.textContent='Could not reach this backend. Trying to sign in anyway.';return true;}}
  url.addEventListener('change',probe);key.addEventListener('change',probe);if(config.url && config.key && !initialMessage) probe();
  form.addEventListener('submit',async event=>{event.preventDefault();submit.disabled=true;try {if(!await probe()) return;const cfg={url:url.value.trim(),key:key.value.trim()};const client=backendClient(cfg);const session=await signIn({...cfg,client},email.value,password.value);if(cfg.url===shared.url && cfg.key===shared.key) clearBackend();else saveBackend(cfg.url,cfg.key);await openSource(createSupabaseSource(client,session.user.id),client);} catch(error) {status.textContent=error.message || 'Could not sign in.';} finally {submit.disabled=false;}});
  page.append(intro,form);const footer=document.createElement('footer');footer.textContent='Open Exercise Art · CC BY-SA 4.0 · Colours changed';page.append(footer);root.append(page);
}
if(import.meta.env.DEV && new URLSearchParams(location.search).get('fixture')==='1') {
  Promise.all([import('../test/fixtures/snapshot-v1.json'),import('../test/fixtures/rows.json')]).then(([snapshot,rows])=>openSource(createFixtureSource({snapshot:snapshot.default,rows:rows.default}))).catch(()=>{root.textContent='Example data is unavailable.';});
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
