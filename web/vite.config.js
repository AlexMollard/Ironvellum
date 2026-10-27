import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {defineConfig} from 'vitest/config';
const path = fileURLToPath(new URL('../cloud-defaults.properties',import.meta.url));
const entries = Object.fromEntries(readFileSync(path,'utf8').split(/\r?\n/).map(line=>line.trim()).filter(line=>line && !line.startsWith('#') && line.includes('=')).map(line=>{const i=line.indexOf('=');return [line.slice(0,i).trim(),line.slice(i+1).trim()];}));
export default defineConfig({base:'./',define:{'import.meta.env.VITE_SUPABASE_URL':JSON.stringify(entries['supabase.url'] ?? ''),'import.meta.env.VITE_SUPABASE_KEY':JSON.stringify(entries['supabase.key'] ?? '')},test:{environment:'jsdom'}});
