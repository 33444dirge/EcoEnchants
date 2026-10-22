const fs=require('fs'),path=require('path'),{spawn}=require('child_process');
const task=path.resolve(__dirname,'../..'),lab=path.join(task,'lab-runtime'),outdir=path.join(task,'verification');
const label=process.argv[2];if(!/^[a-z0-9-]+$/.test(label)||fs.existsSync(path.join(outdir,label+'-server.log')))throw Error('New run label required');
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
async function until(fn,ms,name){const end=Date.now()+ms;while(Date.now()<end){if(await fn())return;await sleep(250);}throw Error('Timeout '+name);}
async function post(body){const r=await fetch('http://127.0.0.1:25685',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});if(!r.ok)throw Error(await r.text());return r.json();}
let server,bots,text='',out,botout;
(async()=>{try{
 for(const port of [25684,25685])await new Promise((res,rej)=>{const s=require('net').createServer();s.once('error',rej);s.listen(port,'127.0.0.1',()=>s.close(res));});
 fs.copyFileSync(path.join(task,'repo/build/libs/EcoEnchants-2026.27-1-all.jar'),path.join(lab,'plugins/EcoEnchants.jar'));
 bots=spawn(process.execPath,[path.join(outdir,'packet-bots.cjs')],{windowsHide:true,stdio:['ignore','pipe','pipe']});
 botout=fs.createWriteStream(path.join(outdir,label+'-bots.log'));bots.stdout.pipe(botout);bots.stderr.pipe(botout,{end:false});
 await until(async()=>{try{await post({action:'status'});return true;}catch{return false;}},10000,'bot control');
 out=fs.createWriteStream(path.join(outdir,label+'-server.log'));
 server=spawn('D:/java/bin/java.exe',['--add-opens=java.base/java.util=ALL-UNNAMED','-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-Dterminal.jline=false','-Dterminal.ansi=false','-Dpaper.disablePluginRemapping=true','-Denchants.baseline='+path.join(task,'baseline.jar'),'-Xms1G','-Xmx3G','-jar','C:/Users/Administrator/Desktop/eco系列/shiroha-bundler-26.2-SNAPSHOT (3).jar','--nogui'],{cwd:lab,windowsHide:true,stdio:['pipe','pipe','pipe']});
 for(const stream of [server.stdout,server.stderr]){stream.setEncoding('utf8');stream.on('data',t=>{text+=t;out.write(t);});}server.on('exit',()=>out.end());
 const ready=(needle,ms=180000)=>until(()=>{if(server.exitCode!==null)throw Error('Server exit '+server.exitCode);if(/\[EE\] FAIL|Error occurred while enabling EnchantProbe|Could not load 'plugins/.test(text))throw Error('Fixture/plugin startup failure; inspect log');return text.includes(needle);},ms,needle);
 await ready('Done (');await ready('[EE] READY');console.log(label+' SERVER_READY');
 for(const name of ['Enchant1','Enchant2']){
  await post({action:'connect',name});await until(async()=>(await post({action:'status'})).some(s=>s.name===name&&s.state==='play'),30000,'player '+name);await sleep(1000);
  await post({action:'send',name,command:'enchantsaudit behavior'});await ready('[EE] BEHAVIOR_DONE player='+name);console.log(label+' BEHAVIOR_PASS '+name);
 }
 await post({action:'send',name:'Enchant1',command:'enchantsaudit bench'});await ready('[EE] BENCH_DONE',300000);console.log(label+' BENCH_PASS');
 fs.writeFileSync(path.join(outdir,label+'-clients.json'),JSON.stringify(await post({action:'status'}),null,2));
}finally{
 if(server&&server.exitCode===null){server.stdin.write('stop\n');await until(()=>server.exitCode!==null,60000,'normal shutdown');console.log(label+' EXIT '+server.exitCode);}
 if(bots)bots.kill();if(botout)botout.end();
}})().catch(e=>{console.error(e);process.exitCode=1;});
