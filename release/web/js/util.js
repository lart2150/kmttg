// Shared helpers for the kmttg web pages.
// Replaces the old js/rpc/util.js and the hand-rolled sprintf in js/String.js.

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

// Zero-pad a number to the given width
function pad(n, width) {
   return String(n).padStart(width, "0");
}

// Right-align a string in the given width, as the old "%25s" format did
function padLeft(s, width) {
   return String(s).padStart(width, " ");
}

// GET a URL and parse the response as JSON. Throws on a non-2xx response so
// callers can report the server's own error text rather than a parse failure.
async function getJSON(url, params) {
   return (await request(url, params)).json();
}

// GET a URL and return the response body as text
async function getText(url, params) {
   return (await request(url, params)).text();
}

async function request(url, params) {
   const u = new URL(url, window.location);
   for (const [key, value] of Object.entries(params || {}))
      u.searchParams.set(key, value);
   const response = await fetch(u);
   if (!response.ok)
      throw new Error(await response.text() || response.statusText);
   return response;
}

// Build the query for an /rpc call. operation and tivo are always required;
// json is stringified when given as an object.
function rpcParams(operation, tivo, json) {
   const params = { operation: operation, tivo: tivo };
   if (json !== undefined)
      params.json = typeof json === "string" ? json : JSON.stringify(json);
   return params;
}

function util_getShowName(json) {
   let title = json.title || "";
   if (json.seasonNumber !== undefined && json.episodeNum !== undefined)
      title += ` [Ep ${json.seasonNumber}${pad(json.episodeNum[0], 2)}]`;
   if (json.movieYear !== undefined)
      title += ` [${json.movieYear}]`;
   if (json.subtitle !== undefined)
      title += ` - ${json.subtitle}`;
   const subs = json.subscriptionIdentifier;
   if (subs && subs.length > 0) {
      const type = subs[0].subscriptionType;
      if (type === "singleTimeChannel" || type === "repeatingTimeChannel")
         title = " Manual:" + title;
   }
   return title;
}

function util_getChannel(json) {
   const chan = json.channel;
   if (!chan)
      return "";
   let channel = chan.channelNumber || "";
   if (chan.callSign !== undefined)
      channel += chan.callSign.toLowerCase() === "all channels" ? chan.callSign : "=" + chan.callSign;
   else if (chan.name !== undefined)
      channel += "=" + chan.name;
   return channel;
}

// TiVo sends times as "YYYY-MM-DD HH:MM:SS" in GMT
function util_parseTime(startTime) {
   return new Date(startTime.replace(/-/g, "/") + " GMT");
}

function util_getTimeLong(startTime) {
   return util_parseTime(startTime).getTime() / 1000;
}

function util_getTime(startTime) {
   return util_formatDate(util_parseTime(startTime));
}

function util_getTimeFromGmt(gmt) {
   return util_formatDate(new Date(gmt));
}

function util_formatDate(dt) {
   return `${WEEKDAYS[dt.getDay()]} ${pad(dt.getMonth() + 1, 2)}/${pad(dt.getDate(), 2)}/` +
      `${pad(dt.getFullYear() - 2000, 2)} ${pad(dt.getHours(), 2)}:${pad(dt.getMinutes(), 2)}`;
}

function util_secsToHM(secs) {
   let hours = Math.floor(secs / 3600);
   let mins = Math.floor((secs - hours * 3600) / 60);
   if (secs - hours * 3600 - mins * 60 > 30)
      mins += 1;
   if (mins === 60) {
      hours += 1;
      mins = 0;
   }
   return `${hours}:${pad(mins, 2)}`;
}

function util_handleError(prefix, e) {
   showDialog(prefix, e && e.message ? e.message : String(e), "error");
}

function basename(path) {
   return path.split(/[\/]/).pop();
}

// Populate a <select> from an array of {text, value} (or plain strings)
function fillSelect(select, items) {
   select.replaceChildren();
   for (const item of items) {
      const option = document.createElement("option");
      option.text = item.text !== undefined ? item.text : item;
      option.value = item.value !== undefined ? item.value : item;
      select.appendChild(option);
   }
}
