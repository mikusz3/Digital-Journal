import { packager } from '@electron/packager';
import fs from 'node:fs';
import path from 'node:path';
const root=path.resolve(import.meta.dirname,'..');
const platform=process.argv[2] || process.platform;
if (!['linux','win32'].includes(platform)) throw new Error('Choose linux or win32.');
const stage=path.join(root,'.runtime','package-'+platform);
fs.rmSync(stage,{recursive:true,force:true});fs.mkdirSync(stage,{recursive:true});
for(const file of ['desktop','core','ui','LICENSE','CREDITS.md','THIRD_PARTY_NOTICES.md','PRIVACY.md'])fs.cpSync(path.join(root,file),path.join(stage,file),{recursive:true});
const pkg=JSON.parse(fs.readFileSync(path.join(root,'package.json'),'utf8'));delete pkg.scripts;delete pkg.devDependencies;
fs.writeFileSync(path.join(stage,'package.json'),JSON.stringify(pkg,null,2));
const result=await packager({dir:stage,name:'Digital Journal',platform,arch:'x64',out:path.join(root,'dist'),overwrite:true,asar:true,prune:false,electronVersion:JSON.parse(fs.readFileSync(path.join(root,'package.json'))).devDependencies.electron,appCopyright:'Copyright 2026 mikusz3',win32metadata:{CompanyName:'mikusz3',FileDescription:'Digital Journal',ProductName:'Digital Journal'}});
for(const dir of result){for(const file of ['README.md','LICENSE','CREDITS.md','THIRD_PARTY_NOTICES.md','PRIVACY.md'])fs.copyFileSync(path.join(root,file),path.join(dir,file==='LICENSE'?'LICENSE.digital-journal':file));fs.mkdirSync(path.join(dir,'docs'),{recursive:true}); for (const doc of ['BACKUP_FORMAT.md','RELEASING.md','COMPATIBILITY.md']) fs.copyFileSync(path.join(root,'docs',doc),path.join(dir,'docs',doc)); console.log(dir);}
