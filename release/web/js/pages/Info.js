// System Information - system info, tuner state and what's on, per TiVo

let TIVO, INFO;
const infoCache = {};
// Bumped by anything that takes over the info panel, so a network connect
// still polling in the background knows to stop drawing into it
let infoOwner = 0;

document.addEventListener("DOMContentLoaded", async () => {
   TIVO = document.getElementById("TIVO");
   INFO = document.getElementById("INFO");

   document.getElementById("REFRESH").addEventListener("click", Info);
   document.getElementById("CONNECT").addEventListener("click", NetworkConnect);
   document.getElementById("REBOOT").addEventListener("click", Reboot);
   TIVO.addEventListener("change", tivoChanged);

   try {
      fillSelect(TIVO, await getJSON("/getRpcTivos"));
   } catch (e) {
      util_handleError("/getRpcTivos", e);
   }
});

function tivoChanged() {
   if (infoCache[TIVO.value] !== undefined) {
      ++infoOwner;
      INFO.innerHTML = infoCache[TIVO.value];
      return;
   }
   Info();
}

// Collect the three RPC calls that make up the info panel
async function Info() {
   ++infoOwner;
   INFO.replaceChildren();
   const tivo = TIVO.value;
   if (!tivo)
      return;
   try {
      addSection(sysInfoText(await getJSON("/rpc", rpcParams("SysInfo", tivo))));
      addSection(whatsOnText(await getJSON("/rpc", rpcParams("WhatsOn", tivo))));
      addSection(tunerInfoText(await getJSON("/rpc", rpcParams("TunerInfo", tivo))));
      infoCache[tivo] = INFO.innerHTML;
   } catch (e) {
      util_handleError("SysInfo", e);
   }
}

function addSection(text) {
   if (!text)
      return;
   const pre = document.createElement("pre");
   pre.className = "info";
   pre.textContent = text;
   INFO.appendChild(pre);
}

function field(label, value) {
   return `${padLeft(label, 25)} ${value}\n`;
}

function sysInfoText(data) {
   const json = data.bodyConfig && data.bodyConfig[0];
   if (!json || json.userDiskSize === undefined || json.userDiskUsed === undefined)
      return "";
   const sizeGB = json.userDiskSize / (1024 * 1024);
   const pct = 100.0 * json.userDiskUsed / json.userDiskSize;
   json.userDiskSize = `${json.userDiskSize} (${sizeGB.toFixed(2)} GB)`;
   json.userDiskUsed = `${json.userDiskUsed} (${pct.toFixed(2)}%)`;

   let text = "";
   if (json.bodyId !== undefined)
      text += field("tsn", json.bodyId.replace("tsn:", ""));
   for (const name of ["softwareVersion", "userDiskSize", "userDiskUsed", "parentalControlsState"])
      if (json[name] !== undefined)
         text += field(name, json[name]);
   return text;
}

function whatsOnText(data) {
   let text = "";
   for (const json of data.whatsOn || []) {
      if (json.playbackType !== undefined)
         text += padLeft("What's On", 25) + " " + json.playbackType;
      const channel = json.channelIdentifier;
      if (json.playbackType !== "idle" && channel && channel.channelNumber !== undefined)
         text += ` (channel ${channel.channelNumber})`;
      text += "\n";
   }
   return text;
}

function tunerInfoText(data) {
   let text = "";
   for (const json of data.state || []) {
      text += field("tunerId", json.tunerId);
      if (json.channel !== undefined) {
         text += padLeft("channelNumber", 25) + " " + json.channel.channelNumber;
         if (json.channel.callSign !== undefined)
            text += ` (${json.channel.callSign})`;
         text += "\n\n";
      }
   }
   return text;
}

/* ---- network connect ---- */

// A connect that has not run in a while can take five minutes or more. Every
// poll is a fresh rpc connect and authenticate on the TiVo, so start responsive
// while the early phases are changing quickly, then ease off for the long haul.
const CONNECT_STEPS = [
   { until: 60000, every: 2000 },
   { until: 300000, every: 5000 },
   { until: Infinity, every: 10000 },
];
const CONNECT_LIMIT = 1200000; // stop watching after 20 minutes
// The status reported at the start is the previous connection's, so a run that
// still looks finished this early has not started yet rather than ended
const CONNECT_SETTLE = 15000;

// Phases seen on a real connect, sampled every ~200ms: preparing, calling,
// connecting, downloading, importing, then succeeded. Anything outside this
// set has stopped making progress - reported as-is rather than guessed at,
// since no failure has been observed and its phase name is unknown.
const CONNECT_WORKING = ["preparing", "calling", "connecting", "downloading", "importing"];
const CONNECT_SUCCESS = "succeeded";
const finished = phase => !CONNECT_WORKING.includes(phase);

// Start a connection and watch it through to the end, in place of the info
// panel. Cancelled by anything else that takes the panel over.
async function NetworkConnect() {
   const tivo = TIVO.value;
   if (!tivo)
      return;
   const mine = ++infoOwner;
   const started = Date.now();
   const steps = [];
   INFO.replaceChildren();
   const pre = document.createElement("pre");
   pre.className = "info";
   INFO.appendChild(pre);
   const draw = note =>
      pre.textContent = `Network Connect - ${tivo}\n\n` + steps.join("\n") + (note ? "\n\n" + note : "");
   draw("Starting...");

   try {
      await getText("/rpc", rpcParams("PhoneHome", tivo));
   } catch (e) {
      draw(`Could not start: ${e.message}`);
      return;
   }

   let last;
   while (infoOwner === mine) {
      const elapsed = Date.now() - started;
      let json;
      try {
         json = await getJSON("/rpc", rpcParams("PhoneHomeStatus", tivo));
      } catch (e) {
         draw(`Lost contact with ${tivo}: ${e.message}`);
         return;
      }
      if (infoOwner !== mine)
         return;
      const phase = json.phase || "unknown";
      const status = json.status || phase;
      // Inside the settle window a finished phase is the previous run's, so it
      // is not worth logging as a step of this one
      const stale = finished(phase) && elapsed <= CONNECT_SETTLE;
      if (status !== last && !stale) {
         last = status;
         steps.push(`${clock(elapsed)}  ${padRight(phase, 12)} ${humanize(status)}`);
      }
      if (finished(phase) && elapsed > CONNECT_SETTLE) {
         draw(phase === CONNECT_SUCCESS
            ? `Finished after ${clock(elapsed)}`
            : `Stopped after ${clock(elapsed)} - the TiVo reported ${humanize(status)}`);
         return;
      }
      if (elapsed > CONNECT_LIMIT) {
         draw(`Still ${humanize(status)} after ${clock(elapsed)}. Not watching any longer;`
            + " press Network Connect again to pick the status back up.");
         return;
      }
      draw(`Working... ${clock(elapsed)}`);
      await sleep(CONNECT_STEPS.find(s => elapsed < s.until).every);
   }
}

function sleep(ms) {
   return new Promise(resolve => setTimeout(resolve, ms));
}

// mm:ss from a duration in milliseconds
function clock(ms) {
   const total = Math.round(ms / 1000);
   return `${Math.floor(total / 60)}:${pad(total % 60, 2)}`;
}

// "preparingToCallOverNetwork" reads better as words
function humanize(s) {
   return s.replace(/([a-z0-9])([A-Z])/g, "$1 $2").toLowerCase();
}

function padRight(s, width) {
   return String(s).padEnd(width, " ");
}

async function Reboot() {
   if (!await confirmDialog("Reboot", `Reboot ${TIVO.value}?`))
      return;
   try {
      showDialog("Reboot", await getText("/reboot", { tivo: TIVO.value }), "warning", 3);
   } catch (e) {
      util_handleError("Reboot", e);
   }
}
