// Job Monitor - lists kmttg's running jobs and kills selected ones

let table;

document.addEventListener("DOMContentLoaded", () => {
   table = new KTable(document.getElementById("TABLE"), {
      columns: [
         { label: "STATUS" },
         { label: "JOB" },
         { label: "SOURCE" },
         { label: "OUTPUT" }
      ],
      select: "single",
      sortable: true,
      emptyText: "No jobs"
   });

   document.getElementById("REFRESH").addEventListener("click", Refresh);
   document.getElementById("KILL").addEventListener("click", Kill);
   Refresh();
});

async function Refresh() {
   table.clear();
   try {
      for (const json of await getJSON("/jobs", { get: 1 }))
         table.add([
            json.status || "",
            json.type || "",
            json.source || "",
            json.output ? basename(json.output) : ""
         ], json);
   } catch (e) {
      util_handleError("/jobs", e);
   }
}

async function Kill() {
   const selected = table.selectedRows();
   if (selected.length === 0) {
      showDialog("Kill", "No rows selected!", "warning", 2);
      return;
   }
   const results = await Promise.allSettled(
      selected.map(row => getText("/jobs", { kill: row.data.familyId })));
   const failed = results.filter(r => r.status === "rejected");
   if (failed.length > 0)
      showDialog("Kill", failed.map(f => f.reason.message).join("\n"), "error");
   else
      showDialog("Kill", `Killed ${selected.length} job(s)`, "warning", 2);
   Refresh();
}
