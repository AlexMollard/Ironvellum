import { createClient } from '@supabase/supabase-js';
const STORAGE = 'ironvellum.backend';
let client;
const defaults = {url:import.meta.env.VITE_SUPABASE_URL || '', key:import.meta.env.VITE_SUPABASE_KEY || '', own:false};
export function sharedBackend() { return {...defaults}; }
export function loadBackend() { try { const v = JSON.parse(localStorage.getItem(STORAGE)); if (v?.url && v?.key) return {url:v.url,key:v.key,own:true}; } catch {} return {...defaults}; }
export function saveBackend(url, key) { try { localStorage.setItem(STORAGE, JSON.stringify({url:url.trim(),key:key.trim()})); } catch {} }
export function clearBackend() { try { localStorage.removeItem(STORAGE); } catch {} }
export function backendClient(config) { if (!config.url || !config.key) throw new Error('Enter your backend URL and publishable key.'); return createClient(config.url,config.key,{auth:{storage: safeStorage, persistSession:true}}); }
const memory = new Map();
export const safeStorage = {getItem(k){if(memory.has(k)) return memory.get(k);try{return localStorage.getItem(k);}catch{return null;}},setItem(k,v){memory.set(k,v);try{localStorage.setItem(k,v);}catch{}},removeItem(k){memory.set(k,null);try{localStorage.removeItem(k);}catch{}}};
export async function signIn(config,email,password) { client = config.client ?? backendClient(config); const {data,error} = await client.auth.signInWithPassword({email,password}); if(error) throw new Error('Could not sign in. Check your email and password.'); return data.session; }
export async function signInWithGoogle(config) {
  if (config.own) throw new Error('Google sign-in is available on the shared backend only.');
  client = config.client ?? backendClient(config);
  const {data,error} = await client.auth.signInWithOAuth({provider:'google',options:{redirectTo:location.origin + location.pathname}});
  if (error) throw new Error('Could not start Google sign-in. Try again.');
  return data;
}
export async function restoreSession(config) {
  client = config.client ?? backendClient(config);
  const {data,error} = await client.auth.getSession();
  if (error) throw new Error('Could not restore your session. Please sign in again.');
  return {client,session:data.session};
}
export function observeSignOut(client,onSignedOut) { return client.auth.onAuthStateChange(event=>{if(event==='SIGNED_OUT') onSignedOut();}).data.subscription; }
export async function signOut() { if (client) await client.auth.signOut({scope:'local'}); client = undefined; }
export function useClient(value) { client = value; }
