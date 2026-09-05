// Video Streaming - browse a TiVo's recordings or the server's video shares
// and hand them to the transcoder.

const MONITOR_INTERVAL = 5000;
const RATES = ["500k", "1000k", "1500k", "2000k", "2500k", "3000k",
   "3500k", "4000k", "4500k", "5000k"];

let TIVO, MAXRATE, BROWSE, nplTable, fileTable;
let nplDiv, fileDiv;
let monitorTimer = 0;
let fileMode = "";
// Which TiVos speak RPC; the rest fall back to the XML listing
const isRpc = {};

document.addEventListener("DOMContentLoaded", async () => {
   TIVO = document.getElementById("TIVO");
   MAXRATE = document.getElementById("MAXRATE");
   BROWSE = document.getElementById("BROWSE");
   nplDiv = document.getElementById("NPLTABLE_DIV");
   fileDiv = document.getElementById("FILETABLE_DIV");

   fillSelect(MAXRATE, RATES);
   MAXRATE.value = "2000k";
   MAXRATE.addEventListener("change", retargetLinks);

   nplTable = new KTable(document.getElementById("NPLTABLE"), {
      columns: [
         { details: true },
         { label: "SHOW" },
         { label: "DATE" },
         { label: "CHANNEL" },
         { label: "DUR" },
         { label: "SIZE" }
      ],
      details: row => detailsNode(row.data),
      emptyText: "No shows"
   });

   fileTable = new KTable(document.getElementById("FILETABLE"), {
      columns: [{ label: "NAME" }],
      emptyText: "No files"
   });

   const actions = {
      MYSHOWS: MyShows, BROWSEFILES: FileBrowser, GetCached: GetCached,
      ShowRunning: Running, KILLALL: KillAll
   };
   for (const [id, fn] of Object.entries(actions))
      document.getElementById(id).addEventListener("click", () => fn());

   for (const radio of document.querySelectorAll('input[name="type"]'))
      radio.addEventListener("change", retargetLinks);

   try {
      const data = await getJSON("/getTivos");
      for (const json of data)
         isRpc[json.tivo] = json.rpc;
      fillSelect(TIVO, data.map(json => json.tivo));
   } catch (e) {
      util_handleError("/getTivos", e);
   }
});

function format() {
   return document.querySelector('input[name="type"]:checked').value;
}

// Point already-rendered transcode links at the current rate and format,
// so changing either does not mean re-listing everything
function retargetLinks() {
   for (const link of document.querySelectorAll("a[href*='/transcode']")) {
      const u = new URL(link.href);
      u.searchParams.set("maxrate", MAXRATE.value);
      u.searchParams.set("format", format());
      link.href = u.toString();
   }
}

function transcodeUrl(params) {
   const u = new URL("/transcode", window.location);
   u.searchParams.set("format", format());
   u.searchParams.set("maxrate", MAXRATE.value);
   for (const [key, value] of Object.entries(params))
      u.searchParams.set(key, value);
   return u;
}

function link(text, onclick) {
   const a = document.createElement("a");
   a.href = "#";
   a.textContent = text;
   a.addEventListener("click", e => {
      e.preventDefault();
      onclick();
   });
   return a;
}

// A show cell: the title, plus play/transcode links when it is not protected
function showCell(title, playUrl, onTranscode) {
   const wrap = document.createDocumentFragment();
   wrap.append(title);
   if (!playUrl)
      return wrap;
   const actions = document.createElement("div");
   actions.className = "row-actions";
   const play = document.createElement("a");
   play.href = playUrl.toString();
   play.target = "_blank";
   play.textContent = "[transcode & play]";
   actions.append(play, " ", link("[transcode]", onTranscode));
   wrap.append(actions);
   return wrap;
}

function detailsNode(data) {
   const pre = document.createElement("pre");
   if (data instanceof Node)
      pre.textContent = formatXml(new XMLSerializer().serializeToString(data));
   else
      pre.textContent = JSON.stringify(data, null, 3);
   return pre;
}

function formatXml(xml) {
   let pad = 0;
   return xml.replace(/(>)(<)(\/*)/g, "$1\n$2$3").split("\n").map(node => {
      let indent = 0;
      if (node.match(/.+<\/\w[^>]*>$/))
         indent = 0;
      else if (node.match(/^<\/\w/))
         pad = Math.max(0, pad - 1);
      else if (node.match(/^<\w[^>]*[^/]>.*$/))
         indent = 1;
      const line = "  ".repeat(pad) + node;
      pad += indent;
      return line;
   }).join("\n");
}

/* ---- table visibility ---- */

function hideTables() {
   nplDiv.hidden = true;
   fileDiv.hidden = true;
}

function showNpl() {
   nplDiv.hidden = false;
}

function showFiles() {
   fileDiv.hidden = false;
}

/* ---- My Shows ---- */

function MyShows() {
   // Already loaded and merely hidden - just bring it back
   if (nplDiv.hidden && nplTable.rows.length > 0) {
      hideTables();
      showNpl();
      return;
   }
   nplTable.clear();
   hideTables();
   showNpl();
   if (isRpc[TIVO.value] == 1)
      myShowsRpc(0);
   else
      myShowsXml(0);
}

async function myShowsRpc(offset) {
   const limit = 50;
   const tivo = TIVO.value;
   BROWSE.textContent = `PLEASE WAIT: GETTING SHOWS FROM ${tivo} ...`;
   try {
      const data = await getJSON("/getMyShows", { limit: limit, tivo: tivo, offset: offset });
      if (data && data.length > 0) {
         loadNplRpc(data, tivo);
         if (data.length === limit) {
            myShowsRpc(offset + limit);
            return;
         }
      }
   } catch (e) {
      util_handleError("/getMyShows", e);
   }
   BROWSE.textContent = "";
}

function loadNplRpc(data, tivo) {
   for (const entry of data) {
      const json = entry.recording && entry.recording[0];
      if (!json || json.__url__ === undefined)
         continue;

      // In-progress or copy-protected recordings cannot be pulled
      let candownload = json.state !== "inProgress";
      if (json.drm && json.drm.tivoToGo === false)
         candownload = false;

      const date = json.startTime !== undefined ? util_getTime(json.startTime) : "";
      const duration = json.duration || 0;
      const name = util_getShowName(json);
      const label = `${name} (${date})`;
      const params = { url: json.__url__, name: label, tivo: tivo, duration: duration };

      nplTable.add([
         "",
         showCell(name, candownload ? transcodeUrl(params) : null,
            () => startTranscode("TiVo transcode", params)),
         date,
         util_getChannel(json),
         duration ? util_secsToHM(duration) : "",
         json.size !== undefined ? `${(json.size / Math.pow(2, 20)).toFixed(2)} GB` : ""
      ], json);
   }
}

async function myShowsXml(offset) {
   const tivo = TIVO.value;
   BROWSE.textContent = `PLEASE WAIT: GETTING SHOWS ${offset}- FROM ${tivo} ...`;
   try {
      const text = await getText("/getMyShows", { xml: 1, tivo: tivo, offset: offset });
      if (text && text.length > 0) {
         const xml = new DOMParser().parseFromString(text, "text/xml");
         const total = parseInt(xmlValue(xml, "TotalItems"));
         const count = parseInt(xmlValue(xml, "ItemCount"));
         loadNplXml(xml, tivo);
         if (offset + count < total) {
            myShowsXml(offset + count);
            return;
         }
      }
   } catch (e) {
      util_handleError("/getMyShows", e);
   }
   BROWSE.textContent = "";
}

function xmlValue(xml, key) {
   const nodes = xml.getElementsByTagName(key);
   if (nodes.length > 0 && nodes[0].childNodes.length > 0)
      return nodes[0].childNodes[0].nodeValue;
   return undefined;
}

function loadNplXml(xml, tivo) {
   for (const node of xml.getElementsByTagName("Item")) {
      const url = xmlValue(node, "Url");
      if (!url)
         continue;

      const candownload = !xmlValue(node, "CopyProtected") && !xmlValue(node, "InProgress");

      let date = "";
      const capture = xmlValue(node, "ShowingStartTime") || xmlValue(node, "CaptureDate");
      if (capture)
         date = util_getTimeFromGmt(parseInt(capture, 16) * 1000);

      const millis = xmlValue(node, "Duration");
      const duration = millis ? millis / 1000 : 0;

      let title = xmlValue(node, "Title") || "";
      const episode = xmlValue(node, "EpisodeNumber");
      if (episode)
         title += ` [Ep ${pad(parseInt(episode), 3)}]`;
      const subtitle = xmlValue(node, "EpisodeTitle");
      if (subtitle)
         title += ` - ${subtitle}`;

      let channel = xmlValue(node, "SourceStation") || "";
      const sourceChannel = xmlValue(node, "SourceChannel");
      if (sourceChannel)
         channel += "=" + sourceChannel;

      const size = xmlValue(node, "SourceSize");
      const label = `${title} (${date})`;
      const params = { url: url, name: label, tivo: tivo, duration: duration };

      nplTable.add([
         "",
         showCell(title, candownload ? transcodeUrl(params) : null,
            () => startTranscode("TiVo transcode", params)),
         date,
         channel,
         duration ? util_secsToHM(duration) : "",
         size ? `${(size / Math.pow(2, 30)).toFixed(2)} GB` : ""
      ], node);
   }
}

/* ---- transcoding ---- */

async function startTranscode(title, params) {
   try {
      const response = await getText("/transcode",
         Object.assign({ format: format(), maxrate: MAXRATE.value, download: 1 }, params));
      // A response carrying a link means the job started
      if (response.indexOf("href=") === -1)
         showDialog(title, response, "warning", 3);
   } catch (e) {
      util_handleError("transcode", e);
   }
}

/* ---- file shares ---- */

async function FileBrowser() {
   fileMode = "Files";
   fileTable.clear();
   hideTables();
   showFiles();
   try {
      for (const file of await getJSON("/getVideoFiles")) {
         if (file === "NONE")
            continue;
         fileTable.add([showCell(file, transcodeUrl({ file: file }),
            () => startTranscode("File transcode", { file: file }))]);
      }
   } catch (e) {
      util_handleError("/getVideoFiles", e);
   }
}

/* ---- cached and running transcodes ---- */

async function GetCached(fromMonitor) {
   if (!fromMonitor)
      fileMode = "Cached";
   fileTable.clear();
   hideTables();
   showFiles();
   try {
      loadCached(await getJSON("/transcode", { getCached: 1 }));
   } catch (e) {
      util_handleError("/transcode?getCached", e);
   }
}

function loadCached(data) {
   let count = 0;
   let running = 0;
   for (const json of data) {
      if (json === "NONE")
         continue;
      count++;
      const time = json.time || 0;
      const duration = json.duration || 0;

      let prefix = time > 0 ? `[${util_secsToHM(time)}]` : "";
      if (json.running !== undefined) {
         running++;
         if (time > 0 && duration > 0)
            prefix = `(running: ${(100 * time / duration).toFixed(1)} %)`;
         else if (time > 0)
            prefix = `(running: ${util_secsToHM(time)})`;
         else
            prefix = "(running)";
      } else if (json.partial !== undefined) {
         if (time > 0 && duration > 0)
            prefix = `(partial: ${util_secsToHM(time)} / ${util_secsToHM(duration)})`;
         else if (time > 0)
            prefix = `(partial: ${util_secsToHM(time)})`;
         else
            prefix = "(partial)";
      }

      const cell = document.createDocumentFragment();
      cell.append(prefix ? `${prefix} ${json.name}` : json.name);
      const actions = document.createElement("div");
      actions.className = "row-actions";
      const play = document.createElement("a");
      play.href = json.url;
      play.target = "_blank";
      play.textContent = "[play]";
      actions.append(play);
      if (json.running === undefined)
         actions.append(" ", link("[remove]", () => RemoveCached(json.url)));
      cell.append(actions);
      fileTable.add([cell]);
   }
   if (count > 0)
      fileTable.add([link("[remove all]", () => RemoveCached("all"))]);

   if (monitorTimer && running === 0)
      monitorOff();
   else if (!monitorTimer && running > 0)
      monitorOn();
}

async function Running(fromMonitor) {
   if (!fromMonitor)
      fileMode = "Running";
   fileTable.clear();
   hideTables();
   showFiles();
   try {
      loadRunning(await getJSON("/transcode", { running: 1 }));
   } catch (e) {
      util_handleError("running", e);
   }
}

function loadRunning(data) {
   if (data[0] === "NONE") {
      fileTable.add(["NO JOBS RUNNING"]);
      return;
   }
   for (const job of data) {
      const time = job.time || 0;
      const duration = job.duration || 0;
      let prefix = "";
      if (time > 0 && duration > 0)
         prefix = `(${(100 * time / duration).toFixed(1)} %)`;
      else if (time > 0)
         prefix = `(${util_secsToHM(time)})`;

      const cell = document.createDocumentFragment();
      cell.append(link("[kill]", () => Kill(job.inputFile)), " ",
         prefix ? `${prefix} ${job.name}` : job.name);
      fileTable.add([cell]);
   }
   if (!monitorTimer)
      monitorOn();
}

async function RemoveCached(url) {
   try {
      const data = await getText("/transcode", { removeCached: url });
      GetCached();
      showDialog("Remove cached", data, "warning", 2);
   } catch (e) {
      util_handleError("removeCached", e);
   }
}

async function KillAll() {
   try {
      const data = await getText("/transcode", { killall: 1 });
      Running();
      showDialog("Kill all", data, "warning", 2);
   } catch (e) {
      util_handleError("killall", e);
   }
}

async function Kill(job) {
   try {
      const data = await getText("/transcode", { kill: job });
      Running();
      showDialog("Kill", data, "warning", 2);
   } catch (e) {
      util_handleError("kill", e);
   }
}

/* ---- periodic refresh while jobs are running ---- */

function monitorOn() {
   console.log("RUNNING monitor started");
   monitorTimer = setInterval(monitor, MONITOR_INTERVAL);
}

function monitorOff() {
   console.log("RUNNING monitor stopped");
   clearInterval(monitorTimer);
   monitorTimer = 0;
}

function monitor() {
   const stop = fileDiv.hidden ||
      (fileMode !== "Running" && fileMode !== "Cached") ||
      fileTable.rows.length === 0 ||
      cellText(fileTable.rows[0].cells[0]) === "NO JOBS RUNNING";
   if (stop) {
      monitorOff();
      return;
   }
   if (fileMode === "Running")
      Running(true);
   else
      GetCached(true);
}
