// System Information - system info, tuner state and what's on, per TiVo

let TIVO, INFO;
const infoCache = {};

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
      INFO.innerHTML = infoCache[TIVO.value];
      return;
   }
   Info();
}

// Collect the three RPC calls that make up the info panel
async function Info() {
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

async function NetworkConnect() {
   try {
      showDialog("Network Connect", await getText("/rpc", rpcParams("PhoneHome", TIVO.value)), "warning", 3);
   } catch (e) {
      util_handleError("Network Connect", e);
   }
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
