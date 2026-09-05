// ToDo - upcoming recordings for a TiVo, with cancel

let TIVO, MESSAGE, table;
const cache = {};

document.addEventListener("DOMContentLoaded", async () => {
   TIVO = document.getElementById("TIVO");
   MESSAGE = document.getElementById("MESSAGE");

   table = new KTable(document.getElementById("TABLE"), {
      columns: [
         { details: true },
         { label: "DATE" },
         { label: "SHOW" },
         { label: "CHANNEL" },
         { label: "DUR" }
      ],
      select: "single",
      details: row => {
         const pre = document.createElement("pre");
         pre.textContent = JSON.stringify(row.data, null, 3);
         return pre;
      },
      emptyText: "No entries"
   });

   document.getElementById("REFRESH").addEventListener("click", Refresh);
   document.getElementById("CANCEL").addEventListener("click", Cancel);
   TIVO.addEventListener("change", tivoChanged);

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
   MESSAGE.textContent = `PLEASE WAIT: GETTING TODO FROM ${tivo} ...`;
   table.clear();
   try {
      loadData(await getJSON("/getToDo", { tivo: tivo }));
      cache[tivo] = table.rows.map(r => ({ cells: r.cells, data: r.data }));
   } catch (e) {
      util_handleError("ToDo", e);
   } finally {
      MESSAGE.textContent = "";
   }
}

function loadData(data) {
   for (const json of data) {
      let date = "";
      if (json.scheduledStartTime !== undefined)
         date = util_getTime(json.scheduledStartTime);
      else if (json.startTime !== undefined)
         date = util_getTime(json.startTime);
      table.add(
         ["", date, util_getShowName(json), util_getChannel(json), util_secsToHM(json.duration)],
         json);
   }
}

// Cancel each selected recording, dropping its row once the TiVo confirms
async function Cancel() {
   const selected = table.selectedRows().filter(row => row.data.recordingId);
   if (selected.length === 0) {
      showDialog("Cancel", "No rows selected!", "warning", 2);
      return;
   }
   for (const row of selected) {
      try {
         const json = { recordingId: [row.data.recordingId] };
         const result = await getJSON("/rpc", rpcParams("Cancel", TIVO.value, json));
         if (result && result.type === "success") {
            table.removeRow(row);
            showDialog("Cancelled", row.data.title, "warning", 2);
         } else {
            showDialog("Cancel failed", JSON.stringify(result, null, 3), "error");
         }
      } catch (e) {
         util_handleError("Cancel", e);
      }
   }
   delete cache[TIVO.value];
}
