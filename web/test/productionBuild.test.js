import {it,expect} from 'vitest';
import {spawnSync} from 'node:child_process';
import {mkdtempSync,readdirSync,readFileSync,rmSync,statSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
function walk(dir) {return readdirSync(dir).flatMap(name=>{const path=join(dir,name);return statSync(path).isDirectory()?walk(path):/\.(js|css|html|json)$/.test(name)?[path]:[];});}
it('a production build contains no test fixture, no screenshot overlay and no fixture entry point',()=>{
  const out=mkdtempSync(join(tmpdir(),'ironvellum-dist-'));
  try {
    const run=spawnSync(process.execPath,['node_modules/vite/bin/vite.js','build','--outDir',out,'--emptyOutDir'],{env:{...process.env,NODE_ENV:'production'},encoding:'utf8'});
    expect(run.status,run.stderr||run.stdout).toBe(0);
    const text=walk(out).map(path=>readFileSync(path,'utf8')).join('\n');
    for(const marker of ['Fictional','Synthetic example deed','screenshotOverlay','Example data is unavailable','fixture-14']) expect(text).not.toContain(marker);
    expect(text).toContain('Example lifter');
    const html=readFileSync(join(out,'index.html'),'utf8');
    expect(html).toContain("style-src 'self'"); expect(html).not.toContain('unsafe-inline');
  } finally {rmSync(out,{recursive:true,force:true});}
},120000);
