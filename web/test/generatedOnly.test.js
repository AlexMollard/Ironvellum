import {it,expect} from 'vitest';
import {readdirSync,readFileSync,statSync} from 'node:fs';
import {join} from 'node:path';
import words from '../src/generated/words.json';
function files(dir) {return readdirSync(dir).flatMap(name=>{const path=join(dir,name);if(name==='generated') return [];return statSync(path).isDirectory()?files(path):/\.(js|css|html)$/.test(name)?[path]:[];});}
const sources=files('src').map(path=>[path,readFileSync(path,'utf8')]);
it('keeps hex colours in generated files only',()=>{const found=sources.flatMap(([path,text])=>[...text.matchAll(/#[0-9a-fA-F]{6}\b/g)].map(m=>`${path}: ${m[0]}`));expect(found).toEqual([]);});
it('keeps the app sentences in generated words',()=>{
  const sentences=Object.values(words).filter(v=>v&&typeof v==='object'&&typeof v.plain==='string'&&v.plain.length>12).map(v=>v.plain);
  const found=sources.flatMap(([path,text])=>sentences.filter(s=>text.includes(s)).map(s=>`${path}: ${s}`));expect(found).toEqual([]);
});
