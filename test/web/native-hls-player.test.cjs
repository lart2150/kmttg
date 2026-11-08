/* Browser lifecycle regression tests for TiVo HLS player.
 * Run: node --test test/web/native-hls-player.test.cjs
 */
const {test}=require("node:test");
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");
const source=fs.readFileSync(path.join(__dirname,"../../release/web/js/pages/NativeHls.js"),"utf8");

async function setup(){
  const onVideo={},onWindow={},calls=[];
  const status={textContent:"Connecting"};
  const video={
    src:"",paused:false,
    addEventListener(name,callback){onVideo[name]=callback},
    canPlayType(){return "probably"},
    play(){return Promise.resolve()},
    pause(){this.paused=true},
    removeAttribute(name){if(name==="src")this.src=""},
    load(){}
  };
  let next=0;
  const timers=new Map();
  const sandbox={
    URL,URLSearchParams,
    document:{getElementById(id){return {status,video}[id]||null}},
    window:{
      location:{search:"?tivo=TiVo&recordingId=tivo%3Arc.123",origin:"http://127.0.0.1:8181"},
      addEventListener(name,callback){onWindow[name]=callback}
    },
    fetch(url,opts){
      calls.push({url:String(url),opts});
      if(String(url).includes("/nativeHls/start"))return Promise.resolve({
        ok:true,json:async()=>({token:"a".repeat(48),playlist:"/nativeHls/media/play.m3u8"})
      });
      return Promise.resolve({ok:true,status:200});
    },
    setInterval(callback){const key=++next;timers.set(key,callback);return key},
    clearInterval(key){timers.delete(key)}
  };
  vm.runInNewContext(source,sandbox);
  for(let n=0;n<12;n++)await Promise.resolve();
  assert.equal(calls.filter(x=>x.url.includes("/nativeHls/start")).length,1);
  return {onVideo,onWindow,calls,status,video,timers};
}

test("finished video releases exactly its session and heartbeat, leaving page usable",async()=>{
  const x=await setup();
  assert.equal(x.timers.size,1);
  x.onVideo.ended();
  assert.equal(x.status.textContent,"Recording finished.");
  assert.equal(x.timers.size,0);
  const stop=x.calls.filter(x=>x.url.includes("/nativeHls/stop"));
  assert.equal(stop.length,1);
  assert.equal(stop[0].opts.method,"POST");
  assert.equal(stop[0].opts.keepalive,true);
  assert.equal(stop[0].opts.headers["X-Kmttg-Playback"],"1");
  x.onWindow.pagehide();
  assert.equal(x.calls.filter(c=>c.url.includes("/nativeHls/stop")).length,1);
});

test("pause leaves session allocated; pagehide later releases it",async()=>{
  const x=await setup();
  x.video.pause();
  assert.equal(x.calls.filter(c=>c.url.includes("/nativeHls/stop")).length,0);
  assert.equal(x.timers.size,1);
  x.onWindow.pagehide();
  assert.equal(x.calls.filter(c=>c.url.includes("/nativeHls/stop")).length,1);
  assert.equal(x.timers.size,0);
});
