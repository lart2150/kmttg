const {test} = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const source = fs.readFileSync(path.join(__dirname,
   "../../release/web/js/pages/LiveTv.js"), "utf8");

class FakeSelect {
   constructor() {
      this.value = "";
      this.options = [];
      this.disabled = false;
      this.listeners = {};
   }
   addEventListener(event, callback) { this.listeners[event] = callback; }
   append(option) {
      this.options.push(option);
      if (this.options.length === 1) this.value = option.value;
   }
   replaceChildren(...options) {
      this.options = [];
      this.value = "";
      options.forEach(option => this.append(option));
   }
}

async function tick() {
   for (let i = 0; i < 16; i++) await Promise.resolve();
}

function init(devices, channels = async () => ({channel: []})) {
   const tivo = new FakeSelect();
   const channel = new FakeSelect();
   const watch = {disabled: false, listeners: {},
      addEventListener(name, fn) {this.listeners[name] = fn}};
   const status = {textContent: ""};
   const opened = [];
   const requests = [];
   const sandbox = {
      URL, URLSearchParams,
      location: {origin: "http://localhost:8181", search: "?tivo=A"},
      window: {open(...args) {opened.push(args)}},
      document: {
         getElementById(id) {return {tivo, channel, watch, status}[id]},
         createElement(tag) {
            assert.equal(tag, "option");
            return {value: "", textContent: ""};
         }
      },
      fetch(url) {
         const target = String(url);
         requests.push(target);
         if (target === "/getTivos")
            return Promise.resolve({ok: true, json: async () => devices});
         if (target.includes("/nativeHls/channels")) {
            return Promise.resolve().then(async () =>
               ({ok: true, json: async () => channels(new URL(target).searchParams.get("tivo"))}));
         }
         throw new Error("Unexpected URL " + target);
      }
   };
   vm.runInNewContext(source, sandbox);
   return {tivo,channel,watch,status,opened,requests};
}

test("live TV picker lists only known HLS-capable RPC devices", async () => {
   const ui = init([
      {tivo:"A", rpc:1, nativeHls:true},
      {tivo:"NotRPC", rpc:0, nativeHls:true},
      {tivo:"NoTranscoder", rpc:1, nativeHls:false}
   ], async () => ({channel:[
      {stbChannelId:"tivo:sch.123", channelNumber:"15-1", callSign:"<script>alert(1)</script>"}
   ]}));
   await tick();
   assert.deepEqual(ui.tivo.options.map(x=>x.value), ["A"]);
   assert.deepEqual(ui.channel.options.map(x=>x.value), ["tivo:sch.123"]);
   assert.equal(ui.channel.disabled, false);
   assert.equal(ui.watch.disabled, false);
   assert.equal(ui.channel.options[0].textContent, "15-1 · <script>alert(1)</script>");
   ui.watch.listeners.click();
   assert.equal(ui.opened.length, 1);
   const target = new URL(ui.opened[0][0]);
   assert.equal(target.searchParams.get("tivo"), "A");
   assert.equal(target.searchParams.get("stbChannelId"), "tivo:sch.123");
   assert.equal(ui.opened[0][2], "noopener");
});

test("earlier device responses never overwrite a newer channel selection", async () => {
   const pending = {};
   const ui = init([
      {tivo:"A",rpc:1,nativeHls:true},
      {tivo:"B",rpc:1,nativeHls:true}
   ], tivo => new Promise(resolve => {pending[tivo]=resolve}));
   await tick();
   assert.equal(ui.tivo.value, "A");
   assert.equal(typeof pending.A, "function");
   ui.tivo.value = "B";
   ui.tivo.listeners.change();
   await tick();
   assert.equal(typeof pending.B, "function");
   pending.B({channel:[{stbChannelId:"tivo:sch.2",channelNumber:"2",callSign:"B"}]});
   await tick();
   assert.deepEqual(ui.channel.options.map(x=>x.value), ["tivo:sch.2"]);
   pending.A({channel:[{stbChannelId:"tivo:sch.1",channelNumber:"1",callSign:"A"}]});
   await tick();
   assert.deepEqual(ui.channel.options.map(x=>x.value), ["tivo:sch.2"]);
   assert.equal(ui.watch.disabled, false);
});

test("no supported devices or invalid channel response leaves Watch disabled", async () => {
   const empty = init([{tivo:"A",rpc:1,nativeHls:false}]);
   await tick();
   assert.equal(empty.tivo.options.length,0);
   assert.match(empty.status.textContent,/No TiVos with a built-in HLS transcoder/);
   const invalid = init([{tivo:"A",rpc:1,nativeHls:true}],
      async () => ({invalid:"lineup"}));
   await tick();
   assert.equal(invalid.watch.disabled,true);
   assert.match(invalid.status.textContent,/Invalid channel lineup response/);
});
