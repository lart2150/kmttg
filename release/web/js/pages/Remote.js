// TiVo Remote - sends key events and jump/skip commands over RPC

// Backdoor sequences, keyed by the label shown in the dropdown
const SPS_CODES = {
   "Quick clear play bar: SPSPS": "select play select pause select play",
   "Clock: SPS9S": "select play select 9 select clear",
   "30 sec skip: SPS30S": "select play select 3 0 select clear",
   "Information: SPSRS": "select play select replay select",
   "4x FF: SPS88S": "select play select 8 8 select clear"
};

let TIVO, LAUNCH, SPS, SKIPB, SKIPF, SKIPM;

document.addEventListener("DOMContentLoaded", async () => {
   TIVO = document.getElementById("TIVO");
   LAUNCH = document.getElementById("LAUNCH");
   SPS = document.getElementById("SPS");
   SKIPB = document.getElementById("SKIPB");
   SKIPF = document.getElementById("SKIPF");
   SKIPM = document.getElementById("SKIPM");

   fillSelect(SPS, Object.keys(SPS_CODES));

   document.getElementById("LAUNCH_BTN").addEventListener("click", launch);
   document.getElementById("SPS_BTN").addEventListener("click", sps);
   document.getElementById("SKIPB_BTN").addEventListener("click", skipb);
   document.getElementById("SKIPF_BTN").addEventListener("click", skipf);
   document.getElementById("SKIPM_BTN").addEventListener("click", skipm);

   // Each remote button lives in an <li> carrying the RPC event name. That is
   // not always the id: liveTv is served by id "livetv" for the skin CSS.
   document.getElementById("REMOTE").addEventListener("click", e => {
      const li = e.target.closest("li[data-event]");
      if (li && e.target.closest("button"))
         keyEvent(li.dataset.event);
   });

   try {
      fillSelect(TIVO, await getJSON("/getRpcTivos"));
   } catch (e) {
      util_handleError("/getRpcTivos", e);
   }

   try {
      const apps = await getJSON("rc_apps.json");
      fillSelect(LAUNCH, apps.filter(app => !app.disabled).map(app => ({
         text: app.channel !== undefined ? `${app.name} (channel ${app.channel})` : app.name,
         value: app.uri
      })));
   } catch (e) {
      util_handleError("rc_apps.json", e);
   }
});

async function rpc(operation, params) {
   try {
      return await getText("/rpc", Object.assign({ operation: operation, tivo: TIVO.value }, params));
   } catch (e) {
      util_handleError(operation, e);
      throw e;
   }
}

function keyEvent(event) {
   rpc("keyEventSend", { json: JSON.stringify({ event: event }) }).catch(() => {});
}

function launch() {
   // LAUNCH option values are already the app uri
   rpc("Navigate", { json: JSON.stringify({ uri: LAUNCH.value }) }).catch(() => {});
}

function sps() {
   rpc("keyEventMacro", { sequence: SPS_CODES[SPS.value] }).catch(() => {});
}

function jump(offset) {
   return rpc("Jump", { json: JSON.stringify({ offset: offset }) }).catch(() => {});
}

// Skip relative to the current playback position
async function skipBy(minutes) {
   try {
      const data = await getJSON("/rpc", rpcParams("Position", TIVO.value));
      if (data.position === undefined)
         return;
      jump(Math.max(0, data.position + minutes * 60000));
   } catch (e) {
      util_handleError("Position", e);
   }
}

function skipf() {
   skipBy(Number(SKIPF.value));
}

function skipb() {
   skipBy(-Number(SKIPB.value));
}

function skipm() {
   jump(Number(SKIPM.value) * 60000);
}
