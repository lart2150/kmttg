/* Browser HLS lifecycle and source-backed stream option tests.
 * Run: node --test test/web/native-hls-player.test.cjs
 */
const {test}=require("node:test");
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");
const source=fs.readFileSync(path.join(__dirname,"../../release/web/js/pages/NativeHls.js"),"utf8");

async function setup({hls=false,levels=[],tracks=[],nativeTracks=[],liveTv=false,liveRange=null,liveSyncPosition=null}={}){
  const onVideo={},onWindow={},calls=[],timers=new Map();
  const controls=Object.fromEntries(["stream-options","quality-control","audio-control"].map(id=>[id,{hidden:true}]));
  for(const id of ["quality","audio"]){
    controls[id]={value:"",options:[],listeners:{},
      replaceChildren(...options){this.options=options},
      addEventListener(name,fn){this.listeners[name]=fn}};
  }
  const audioTracks=Object.assign([...nativeTracks],{listeners:{},
    addEventListener(name,fn){this.listeners[name]=fn}});
  const status={textContent:"Connecting"};
  const back={href:"Stream.html",textContent:"Back to recordings"};
  controls["live-controls"]={hidden:true};
  controls["skip-to-live"]={disabled:true,listeners:{},addEventListener(name,fn){this.listeners[name]=fn}};
  const video={
    src:"",paused:false,currentTime:10,audioTracks,
    seekable:liveRange ? {length:1,start(){return liveRange.start},end(){return liveRange.end}} : {length:0},
    addEventListener(name,callback){onVideo[name]=callback},
    canPlayType(){return hls?"":"probably"},
    play(){this.paused=false;this.playCalls=(this.playCalls||0)+1;return Promise.resolve()},
    pause(){this.paused=true},
    removeAttribute(name){if(name==="src")this.src=""},
    load(){}
  };
  let timerId=0,instance;
  const Events=Object.fromEntries(["MANIFEST_PARSED","LEVELS_UPDATED","LEVEL_SWITCHED","LEVEL_UPDATED","AUDIO_TRACKS_UPDATED","AUDIO_TRACK_SWITCHED"].map(x=>[x,x]));
  class FakeHls{
    static isSupported(){return true}
    static Events=Events
    constructor(){this.handlers={};this.levels=[...levels];this.audioTracks=[...tracks];this.liveSyncPosition=liveSyncPosition;this.currentLevel=-1;this.audioTrack=0;this.autoLevelEnabled=true;instance=this}
    on(name,fn){this.handlers[name]=fn}
    loadSource(src){this.src=src}
    attachMedia(m){this.media=m}
    destroy(){this.destroyed=true}
    emit(name){this.handlers[name]?.()}
  }
  const sandbox={
    URL,URLSearchParams,Hls:FakeHls,
    navigator:{vendor:!hls && nativeTracks.length ? "Apple Computer, Inc." : "Google Inc."},
    document:{getElementById(id){return {status,video,back,...controls}[id]||null},
      createElement(tag){return {tag,value:"",textContent:""}}},
    window:{
      location:{search:liveTv
            ? "?tivo=TiVo&stbChannelId=tivo%3Asch.1234"
            : "?tivo=TiVo&recordingId=tivo%3Arc.123",
         origin:"http://127.0.0.1:8181"},
      addEventListener(name,callback){onWindow[name]=callback}
    },
    fetch(url,opts){
      calls.push({url:String(url),opts});
      if(String(url).includes("/nativeHls/start"))return Promise.resolve({
        ok:true,json:async()=>({token:"a".repeat(48),playlist:"/nativeHls/media/play.m3u8"})
      });
      return Promise.resolve({ok:true,status:200});
    },
    setInterval(callback){const id=++timerId;timers.set(id,callback);return id},
    clearInterval(id){timers.delete(id)}
  };
  vm.runInNewContext(source,sandbox);
  for(let n=0;n<12;n++)await Promise.resolve();
  assert.equal(calls.filter(x=>x.url.includes("/nativeHls/start")).length,1);
  return {onVideo,onWindow,calls,status,video,back,timers,controls,hls:instance};
}

test("finished video releases exactly its session and heartbeat",async()=>{
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

test("pause retains the stream until navigation and releases it exactly once",async()=>{
  const x=await setup();
  x.video.pause();
  assert.equal(x.calls.filter(c=>c.url.includes("/nativeHls/stop")).length,0);
  assert.equal(x.timers.size,1);
  x.onWindow.pagehide();
  assert.equal(x.calls.filter(c=>c.url.includes("/nativeHls/stop")).length,1);
  assert.equal(x.timers.size,0);
});

test("one real HLS rendition yields no misleading quality selector",async()=>{
  const x=await setup({hls:true,levels:[{height:720,bitrate:8848000}]});
  x.hls.emit("MANIFEST_PARSED");
  assert.equal(x.controls["stream-options"].hidden,true);
  assert.equal(x.controls["quality-control"].hidden,true);
  x.onWindow.pagehide();
});

test("multiple actual HLS variants and audio tracks expose working selectors",async()=>{
  const x=await setup({hls:true,
    levels:[{height:720,bitrate:8848000},{height:480,bitrate:3200000}],
    tracks:[{name:"English",lang:"en"},{name:"Español",lang:"es"}]});
  x.hls.emit("MANIFEST_PARSED");
  x.hls.emit("AUDIO_TRACKS_UPDATED");
  assert.equal(x.controls["stream-options"].hidden,false);
  assert.equal(x.controls["quality-control"].hidden,false);
  assert.equal(x.controls["audio-control"].hidden,false);
  assert.deepEqual(x.controls.quality.options.map(v=>v.textContent),["Auto","720p (8.8 Mbps)","480p (3.2 Mbps)"]);
  assert.equal(x.controls.quality.value,"-1");
  x.controls.quality.value="1";x.controls.quality.listeners.change();
  assert.equal(x.hls.currentLevel,1);
  x.controls.audio.value="1";x.controls.audio.listeners.change();
  assert.equal(x.hls.audioTrack,1);
  x.onWindow.pagehide();
});

test("Safari native audio tracks are selectable when multiple tracks exist",async()=>{
  const tracks=[{language:"en",enabled:true},{language:"es",enabled:false}];
  const x=await setup({nativeTracks:tracks});
  assert.equal(x.controls["audio-control"].hidden,false);
  assert.equal(x.controls["quality-control"].hidden,true);
  x.controls.audio.value="1";x.controls.audio.listeners.change();
  assert.equal(x.video.audioTracks[0].enabled,false);
  assert.equal(x.video.audioTracks[1].enabled,true);
  x.onWindow.pagehide();
});

test("live channel request uses its own source and changes the back-link",async()=>{
  const x=await setup({liveTv:true});
  const start=x.calls.find(x=>x.url.includes("/nativeHls/start"));
  assert(start);
  assert(start.url.includes("stbChannelId=tivo%3Asch.1234"));
  assert(!start.url.includes("recordingId="));
  assert.equal(x.back.href,"LiveTv.html");
  assert.equal(x.back.textContent,"Back to live TV channels");
  x.onWindow.pagehide();
});

test("Chrome's native HLS capability does not hide hls.js quality controls",async()=>{
  const x=await setup({levels:[{height:240,bitrate:400000},{height:720,bitrate:3400000}]});
  assert(x.hls,"Chrome should prefer hls.js for manual quality and audio control");
  x.hls.emit("MANIFEST_PARSED");
  assert.equal(x.controls["quality-control"].hidden,false);
  x.onWindow.pagehide();
});

test("Skip to Live stays hidden for recordings",async()=>{
  const x=await setup({liveRange:{start:10,end:100},liveSyncPosition:95});
  assert.equal(x.controls["live-controls"].hidden,true);
  assert.equal(x.controls["skip-to-live"].disabled,true);
  x.controls["skip-to-live"].listeners.click();
  assert.equal(x.video.currentTime,10);
  x.onWindow.pagehide();
});

test("Skip to Live uses the HLS live-sync position and resumes playback",async()=>{
  const x=await setup({liveTv:true,liveRange:{start:25,end:120},liveSyncPosition:113});
  assert.equal(x.controls["live-controls"].hidden,false);
  assert.equal(x.controls["skip-to-live"].disabled,false);
  x.video.currentTime=48;
  x.video.pause();
  x.controls["skip-to-live"].listeners.click();
  assert.equal(x.video.currentTime,113);
  assert.equal(x.video.paused,false);
  x.onWindow.pagehide();
  assert.equal(x.controls["skip-to-live"].disabled,true);
});

test("Skip to Live remains disabled until a real seekable range exists",async()=>{
  const x=await setup({liveTv:true,liveSyncPosition:900});
  assert.equal(x.controls["skip-to-live"].disabled,true);
  x.video.seekable={length:1,start:()=>100,end:()=>125};
  x.onVideo.progress();
  assert.equal(x.controls["skip-to-live"].disabled,false);
  x.controls["skip-to-live"].listeners.click();
  assert(x.video.currentTime<=125 && x.video.currentTime>=100);
  x.onWindow.pagehide();
});

test("native Safari seeks slightly behind live edge rather than into unbuffered future",async()=>{
  const x=await setup({liveTv:true,nativeTracks:[{language:"en",enabled:true}],
    liveRange:{start:20,end:110}});
  assert.equal(x.controls["skip-to-live"].disabled,false);
  x.video.currentTime=37;
  x.controls["skip-to-live"].listeners.click();
  assert.equal(x.video.currentTime,108);
  x.onWindow.pagehide();
});
