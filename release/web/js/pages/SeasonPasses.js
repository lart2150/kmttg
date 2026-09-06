// Season Passes - list, reprioritise, copy and delete a TiVo's season passes

let TIVO, table;
const cache = {};

document.addEventListener("DOMContentLoaded", async () => {
   TIVO = document.getElementById("TIVO");

   table = new KTable(document.getElementById("TABLE"), {
      columns: [
         { details: true },
         { label: "PRIORITY", width: "5rem" },
         { label: "SHOW" },
         { label: "CHANNEL", width: "8rem" },
         { label: "RECORD", width: "6rem" },
         { label: "KEEP", width: "6rem" },
         { label: "NUM", width: "4rem" },
         { label: "START", width: "4rem" },
         { label: "END", width: "4rem" }
      ],
      select: "multi",
      details: row => {
         const pre = document.createElement("pre");
         pre.textContent = JSON.stringify(row.data, null, 3);
         return pre;
      },
      emptyText: "No season passes"
   });

   const actions = {
      REFRESH: Refresh, SAVE: Save, LOAD: Load, DELETE: Delete,
      COPY: Copy, REORDER: Reorder, UP: () => move(-1), DOWN: () => move(1)
   };
   for (const [id, fn] of Object.entries(actions))
      document.getElementById(id).addEventListener("click", fn);

   TIVO.addEventListener("change", tivoChanged);

   // Arrow keys reorder the selected row, as long as focus is not in a field
   document.addEventListener("keydown", e => {
      if (e.target.matches("input, select, textarea"))
         return;
      if (e.key === "ArrowUp") {
         e.preventDefault();
         move(-1);
      } else if (e.key === "ArrowDown") {
         e.preventDefault();
         move(1);
      }
   });

   try {
      fillSelect(TIVO, await getJSON("/getRpcTivos"));
   } catch (e) {
      util_handleError("/getRpcTivos", e);
   }
});

function tivoChanged() {
   const rows = cache[TIVO.value];
   if (rows) {
      table.clear();
      for (const entry of rows)
         table.add(entry.cells, entry.data);
      return;
   }
   Refresh();
}

async function Refresh() {
   const tivo = TIVO.value;
   if (!tivo)
      return;
   table.clear();
   try {
      const data = await getJSON("/rpc", rpcParams("SeasonPasses", tivo));
      if (data.subscription) {
         loadData("", data.subscription);
         cache[tivo] = table.rows.map(r => ({ cells: r.cells, data: r.data }));
      }
   } catch (e) {
      util_handleError("SeasonPasses", e);
   }
}

function loadData(prefix, data) {
   let priority = 1;
   for (const j of data) {
      const source = j.idSetSource || {};
      const chan = source.channel;
      let channel = "";
      if (chan) {
         channel = chan.channelNumber || "";
         if (chan.callSign !== undefined)
            channel += chan.callSign.toLowerCase() === "all channels" ? chan.callSign : "=" + chan.callSign;
      }
      table.add([
         "",
         String(priority++),
         prefix + (j.title || ""),
         channel,
         j.showStatus || "",
         j.keepBehavior || "",
         j.maxRecordings !== undefined ? j.maxRecordings : "0",
         j.startTimePadding !== undefined ? Math.round(j.startTimePadding / 60) : "0",
         j.endTimePadding !== undefined ? Math.round(j.endTimePadding / 60) : "0"
      ], j);
   }
}

// Move the selected row and renumber the priority column to match
function move(offset) {
   const selected = table.selectedRows();
   if (selected.length !== 1)
      return;
   if (table.moveRow(selected[0], offset))
      renumber();
}

function renumber() {
   table.rows.forEach((row, i) => {
      row.cells[1] = String(i + 1);
      row.el.cells[1].textContent = row.cells[1];
   });
}

async function Delete() {
   const selected = table.selectedRows().filter(row => row.data.subscriptionId);
   if (selected.length === 0) {
      showDialog("Delete", "No rows selected!", "warning", 2);
      return;
   }
   for (const row of selected) {
      try {
         const json = { subscriptionId: row.data.subscriptionId };
         const result = await getJSON("/rpc", rpcParams("Unsubscribe", TIVO.value, json));
         if (result && result.type === "success")
            table.removeRow(row);
         else
            showDialog("Unsubscribe failed", JSON.stringify(result, null, 3), "error");
      } catch (e) {
         util_handleError("Unsubscribe", e);
      }
   }
   renumber();
   delete cache[TIVO.value];
}

async function Save() {
   try {
      showDialog("SP Save", await getText("/rpc", rpcParams("SPSave", TIVO.value)), "warning", 3);
   } catch (e) {
      util_handleError("SPSave", e);
   }
}

async function Load() {
   try {
      const files = await getJSON("/rpc", rpcParams("SPFiles", TIVO.value));
      if (files.length === 0) {
         showDialog("SP Load", "No saved .sp files found.", "warning", 3);
         return;
      }
      const file = await selectDialog("Load Season Passes", "File:", files);
      if (!file)
         return;
      const data = await getJSON("/rpc", Object.assign(rpcParams("SPLoad", TIVO.value), { file: file }));
      table.clear();
      loadData("Loaded: ", data);
   } catch (e) {
      util_handleError("SPLoad", e);
   }
}

// Copy the selected passes to another TiVo, skipping ones it already has
async function Copy() {
   const selected = table.selectedRows().filter(row => row.data.subscriptionId);
   if (selected.length === 0) {
      showDialog("SP Copy", "No rows selected!", "warning", 2);
      return;
   }
   const others = [...TIVO.options].map(o => o.value).filter(v => v !== TIVO.value);
   if (others.length === 0) {
      showDialog("SP Copy", "No other TiVo available.", "warning", 3);
      return;
   }
   const dest = await selectDialog("Copy Season Passes", "Copy to TiVo:", others);
   if (!dest)
      return;

   try {
      const data = await getJSON("/rpc", rpcParams("SeasonPasses", dest));
      const existing = data.subscription || [];
      let copied = 0;
      const skipped = [];
      for (const row of selected) {
         const json = row.data;
         if (alreadyThere(json, existing)) {
            skipped.push(json.title);
            continue;
         }
         const result = await getJSON("/rpc", rpcParams("Seasonpass", dest, json));
         if (result.subscription)
            copied++;
      }
      let message = `Copied ${copied} SP to TiVo ${dest}`;
      if (skipped.length > 0)
         message += `\nSkipped existing: ${skipped.join(", ")}`;
      showDialog("SP Copy", message, "warning", 4);
   } catch (e) {
      util_handleError("SP Copy", e);
   }
}

// A pass counts as present when the title matches and, where the source has a
// call sign, the call sign matches too
function alreadyThere(json, existing) {
   const callSign = json.channel && json.channel.callSign ? json.channel.callSign : "";
   return existing.some(e => {
      if (json.title !== e.title)
         return false;
      if (!callSign || !e.idSetSource || !e.idSetSource.channel)
         return true;
      return callSign === (e.idSetSource.channel.callSign || "");
   });
}

// Push the current row order back to the TiVo
async function Reorder() {
   const ids = table.rows.map(r => r.data.subscriptionId).filter(Boolean);
   if (ids.length === 0)
      return;
   try {
      // The ids are plain subscriptionIds, but subscriptionsReprioritize wants
      // them under subscriptionIdV2 - sent as "subscriptionId" the TiVo rejects
      // the whole request as not conforming to the schema. Remote.SPReorder
      // does the same thing for the desktop UI.
      const result = await getJSON("/rpc",
         rpcParams("Prioritize", TIVO.value, { subscriptionIdV2: ids }));
      if (result.type === "success") {
         delete cache[TIVO.value];
         Refresh();
      } else {
         showDialog("Prioritize failed", JSON.stringify(result, null, 3), "error");
      }
   } catch (e) {
      util_handleError("Prioritize", e);
   }
}
