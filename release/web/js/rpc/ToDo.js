(async () => {
   // Info.html document elements
   window.TIVO = document.getElementById("TIVO");
   window.INFO = document.getElementById("INFO");
   window.TABLE = document.getElementById("TABLE");
   window.NUMCOLS = 5;

   try {
      const response = await fetch('/getRpcTivos');
      const data = await response.json();
      for (const tivo of data) {
         var option = document.createElement("option");
         option.text = tivo;
         option.value = tivo;
         TIVO.appendChild(option);
      }
      TIVO.addEventListener("change", tivoChanged);
   } catch (e) {
      util_handleFetchError("/getRpcTivos", e);
   }

   // NOTE: column 0 is a special column reserved for display
   // of additional row information
   $('#TABLE').dataTable({
     dom: 'T<"clear">lfrtip',
     paging: false,
     ordering: false, // turn off column sorting
     autoWidth: true,
     tableTools: {
       "sRowSelect": "single",
       "aButtons": []
     },
     columns: [
      {
       "class":          'details-control',
       "orderable":      false,
       "data":           null,
       "defaultContent": '',
       "width":          "10px"
      },
      null,
      null,
      null,
      null,
     ],
   });
   
   // Add event listener for opening and closing row details
   $('#TABLE tbody')
      .on('click', 'td.details-control', detailsClicked)
      .on('click', 'tr', (e) => {
         console.log(e);
         e.currentTarget.classList.toggle('selected');
      });
})();

// Callback when details column is clicked on in a row
// Hides or unhides row child data
function detailsClicked() {
   console.log('ping');
   var table = $('#TABLE').DataTable();
   var tr = $(this).closest('tr');
   var row = table.row(tr);

   if ( row.child.isShown() ) {
      // This row is already open - close it
      row.child.hide();
      tr.removeClass('shown');
   }
   else {
      // Open this row
      row.child( detailsFormat(row.data()) ).show();
      tr.addClass('shown');
   }
}

// This is formatting to use for the displaying row child data
// Currently this displays whole json contents
function detailsFormat(d) {
   return '<pre>' + JSON.stringify(d[NUMCOLS], null, 3) + '</pre>';
}

function tivoChanged() {
   if (TABLE.hasOwnProperty(TIVO.value)) {
      clearTable();
      loadTable();
      return;
   }
   Refresh();
}

const Refresh = async () => {
   var html = '<div style="color: blue">';
   message = 'PLEASE WAIT: GETTING TODO FROM ' + TIVO.value + ' ...';
   html += message + '</div>';
   MESSAGE.innerHTML = html;
   clearTable();

   const url = new URL('/getToDo', window.location);
   url.searchParams.set('tivo', TIVO.value);

   try {
      const response = await fetch(url);
      MESSAGE.innerHTML = "";

      if (!response.ok) {
         util_handleFetchError("ToDo", "Error");
         go = 0;
         return;
      }
      
      loadData(await response.json());
   } catch (e) {
      MESSAGE.innerHTML = "";
      go = 0;
      util_handleFetchError("ToDo", e);
      return;
   }

}

// Load ToDo data
function loadData(data) {
   for (const json of data) {

      var date = "";
      var start = 0;
      var end = 0;
      if (json.hasOwnProperty("scheduledStartTime")) {
         start = util_getTimeLong(json.scheduledStartTime);
         date = util_getTime(json.scheduledStartTime);
         end = util_getTimeLong(json.scheduledEndTime);
      }
      else if (json.hasOwnProperty("startTime")) {
         start = util_getTimeLong(json.startTime);
         date = util_getTime(json.startTime);
         end = util_getTimeLong(json.endTime);
      }
      var dur = util_secsToHM(json.duration);   
      var show_name = util_getShowName(json);
      var channel = util_getChannel(json);

      // NOTE: 1st column is dummy used for hiding/unhiding row child info
      // NOTE: Adding json data to end not associated with a table column
      var row = $('#TABLE').DataTable().row.add(
         ["", date, show_name, channel, dur, json]
      );
      row.draw();
   }
}

// For each selected row, send rpc cancel operation & delete table row
const Cancel = async () => {
   var table = $('#TABLE').DataTable();
   for (const rowNum of $('#TABLE').DataTable().rows('.selected')[0]) {
      console.log('rowNum', rowNum);
      var row = table.row(rowNum);
      var json = row.data()[NUMCOLS];
      if (json.recordingId) {
         const url = new URL('/rpc?operation=Cancel', window.location);
         url.searchParams.set('tivo', TIVO.value);
         url.searchParams.set('json', JSON.stringify({recordingId: [json.recordingId]}));
         console.log(url.toString());

         try {
            const response = await fetch(url);
            const data = await response.json();
            if (data?.type && data.type == "success") {
               showDialog("Cancelled",json.title,'warning',2);
            } else {
               showDialog("Cancel failed",JSON.stringify(data, null, 3),'error');
            }
         } catch (e) {
            util_handleFetchError("Cancel", e);
         }
      }
   };
}

function clearTable() {
   $('#TABLE').DataTable().clear().draw();
}

// Cache table data
function saveTable() {
   TABLE[TIVO.value] = $('#TABLE').DataTable().rows().data();
}

// Load cached table data
function loadTable() {
   const table = $('#TABLE').DataTable();


   var rows = TABLE[TIVO.value];
   $.each(rows, function(i, row) {
      table.row.add(row).draw();
   });
}