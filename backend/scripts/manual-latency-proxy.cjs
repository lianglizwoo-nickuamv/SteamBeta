// Manual QA only. Node.js, loopback, no body/header/cookie logging.
// Create .replica-work/hold-next-detail to hold one completed detail response.
// Create .replica-work/release-detail after changing account to release it.
const http=require('node:http');
const fs=require('node:fs');
const path=require('node:path');
const work=path.resolve(__dirname,'../../.replica-work');
const arm=path.join(work,'hold-next-detail'),release=path.join(work,'release-detail');
http.createServer((incoming,outgoing)=>{
  const hold=incoming.method==='GET' && /^\/api\/me\/library\/[a-f0-9-]+$/.test(incoming.url) && fs.existsSync(arm);
  if(hold)fs.unlinkSync(arm);
  const upstream=http.request({hostname:'127.0.0.1',port:8080,path:incoming.url,method:incoming.method,headers:incoming.headers},response=>{
    if(!hold){outgoing.writeHead(response.statusCode,response.headers);response.pipe(outgoing);return;}
    const chunks=[];
    response.on('data',chunk=>chunks.push(chunk));
    response.on('end',()=>{
      process.stdout.write('Detail response held for manual session-change check.\n');
      const timer=setInterval(()=>{
        if(!fs.existsSync(release))return;
        fs.unlinkSync(release);clearInterval(timer);
        if(!outgoing.destroyed){outgoing.writeHead(response.statusCode,response.headers);outgoing.end(Buffer.concat(chunks));}
        process.stdout.write('Previous-session response released.\n');
      },100);
      outgoing.on('close',()=>clearInterval(timer));
    });
  });
  upstream.on('error',()=>{outgoing.writeHead(502,{'Content-Type':'application/json'});outgoing.end('{"error":"backend_unavailable"}');});
  incoming.pipe(upstream);
}).listen(8687,'127.0.0.1',()=>process.stdout.write('Manual QA proxy ready on loopback port 8687.\n'));
