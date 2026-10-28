import {it,expect,beforeEach} from 'vitest';
import tokens from '../src/generated/tokens.json';
import {applyAppearance,accents} from '../src/theme.js';
import {createAppearanceMenu} from '../src/appearance.js';
import {t} from '../src/i18n.js';
import {readFileSync} from 'node:fs';
const root=document.documentElement;
beforeEach(()=>{root.removeAttribute('style');localStorage.clear();});
const kebab=key=>key.replace(/[A-Z]/g,(c,i)=>(i?'-':'')+c.toLowerCase());
it('every accent preset of both themes sets its generated tokens on the page',()=>{
  for(const mode of ['dark','light']) for(const preset of accents[mode]) {
    const applied=applyAppearance({mode},{accentId:preset.id});
    expect(root.dataset.theme).toBe(mode);
    for(const [key,value] of Object.entries(preset.tokens)) if(typeof value==='string'&&key!=='preset') expect(root.style.getPropertyValue(`--${kebab(key)}`)).toBe(value);
    expect(root.style.getPropertyValue('--accent')).toBe(preset.tokens.fill);
    expect(applied.tokens).toEqual(preset.tokens);
  }
});
it('light B is the app’s light paper: every colour of the page comes from the generated tokens',()=>{
  applyAppearance({mode:'light'});
  for(const [key,value] of Object.entries(tokens.light.paper)) if(typeof value==='string'&&key!=='Scrim') expect(root.style.getPropertyValue(`--paper-${kebab(key)}`)).toBe(value);
  expect(root.style.getPropertyValue('--paper-vault')).toBe(tokens.light.paper.Vault);
  applyAppearance({mode:'dark'}); expect(root.style.getPropertyValue('--paper-vault')).toBe(tokens.dark.paper.Vault);
  for(const [name,value] of Object.entries(tokens.light.rarity)) { applyAppearance({mode:'light'}); expect(root.style.getPropertyValue(`--rarity-${name.toLowerCase()}`)).toBe(value); }
});
it('the names toggle switches the vocabulary',()=>{
  applyAppearance({mode:'dark',themed:false}); const plain=t('concept_trial_cpl');
  applyAppearance({mode:'dark',themed:true}); const themed=t('concept_trial_cpl');
  expect(plain).toBe('Workouts'); expect(themed).not.toBe(plain); applyAppearance({mode:'dark',themed:false});
});
it('the font is self-hosted: only the four weights the design uses, and no external stylesheet or font host',()=>{
  const main=readFileSync('src/main.js','utf8'),html=readFileSync('index.html','utf8'),css=readFileSync('src/theme.css','utf8');
  expect([...main.matchAll(/@fontsource\/chakra-petch\/([\w-]+)\.css/g)].map(m=>m[1])).toEqual(['latin-400','latin-500','latin-600','latin-700']);
  expect(css).toContain("'Chakra Petch'"); expect(html+css+main).not.toMatch(/fonts\.(googleapis|gstatic)\.com/); expect(html).toContain("style-src 'self'");
});
