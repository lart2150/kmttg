$(document).ready(function() {
   TABLE = document.getElementById("TABLE");
   NUMCOLS = 4;

   $('#TABLE').dataTable({
     dom: 'T<"clear">lfrtip',
     paging: false,
     ordering: true, // turn on column sorting
     aaSorting: [], // sorting off initially
     autoWidth: true,
     tableTools: {
       "sRowSelect": "single",
       "aButtons": []
     },
     columns: [
      null,
      null,
      null,
      null,
     ],
   });
});

function Refresh() {
   clearTable();
   var url = "/jobs?get=1";
   $.getJSON(url, function(data) {
      loadData(data);
   })
   .error(function(xhr, status) {
      util_handleError("/jobs", xhr, status);
   });
}

// Load job data
function loadData(data) {
   $.each(data, function (i, json) {
      //console.log(JSON.stringify(json, null, 3));

      var status = "";
      if (json.status)
         status = json.status;
         
      var type = "";
      if (json.type)
         type = json.type;
         
      var source = "";
      if (json.source)
         source = json.source;
         
      var output = "";
      if (json.output)
         output = basename(json.output);
         
      var row = $('#TABLE').DataTable().row.add(
         [status, type, source, output, json]
      );
      row.draw();
   });
}

// Kill selected jobs
function Kill() {
   var table = $('#TABLE').DataTable();
   var selected = TableTools.fnGetInstance('TABLE').fnGetSelected();
   if (selected.length == 0) {
      showDialog("Kill","No rows selected!",'warning',2);
      return;
   }
   
   var promises = [];
   $.each(selected, function(i, r) {
      var rowNum = r._DT_RowIndex;
      var row = table.row(rowNum);
      var json = row.data()[NUMCOLS];
      var url = "/jobs?kill=" + encodeURIComponent(json.familyId);
      // Create a deferred event
      var p = $.Deferred();
      promises.push(p);
      $.get(url, function(data) {
         console.log(data);
         p.resolve();
      })
      .error(function(xhr, status) {
         p.resolve();
         util_handleError("kill", xhr, status);
      });
   });
   
   // This only triggered once all deferred events complete
   $.when.apply($, promises).then( function() {
      showDialog("Kill",'Kill succeeded','warning',2);
      Refresh();
   });
}

function clearTable() {
   $('#TABLE').DataTable().clear().draw();
}

function basename(path) {
   return path.split(/[\\/]/).pop();
}