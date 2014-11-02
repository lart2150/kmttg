$(document).ready(function() {
   TIVO = document.getElementById("TIVO");
   MESSAGE = document.getElementById("MESSAGE");
   TABLE = document.getElementById("TABLE");
   NUMCOLS = 5;

   // Retrieve rpc enabled TiVos
   $.getJSON("/getRpcTivos", function(data) {
      $.each(data, function( i, value ) {
         var option = document.createElement("option");
         option.text = value;
         option.value = value;
         TIVO.appendChild(option);
      });
      $('.TIVO').change(function() { tivoChanged(); });
   })
   .error(function(xhr, status) {
      util_handleError("/getRpcTivos", xhr, status);
   });

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
   $('#TABLE tbody').on('click', 'td.details-control', detailsClicked);
});

// Callback when details column is clicked on in a row
// Hides or unhides row child data
function detailsClicked() {
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

function Refresh() {
   var html = '<div style="color: blue">';
   message = 'PLEASE WAIT: GETTING TODO FROM ' + TIVO.value + ' ...';
   html += message + '</div>';
   MESSAGE.innerHTML = html;
   clearTable();
   var format = $('input[name="type"]:checked').val();
   var tivo = encodeURIComponent(TIVO.value);
   var url = "/getToDo?tivo=" + tivo;
   $.getJSON(url, function(data) {
      loadData(data, tivo);
      MESSAGE.innerHTML = "";
   })
   .error(function(xhr, status) {
      go = 0;
      MESSAGE.innerHTML = "";
      util_handleError("ToDo", xhr, status);
   });
}

// Load ToDo data
function loadData(data, tivo) {
   $.each(data, function (i, json) {
      //console.log(JSON.stringify(json, null, 3));

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
      
      var duration = end - start;
      var dur = util_secsToHM(json.duration);   
      var show_name = util_getShowName(json);
      var channel = util_getChannel(json);

      // NOTE: 1st column is dummy used for hiding/unhiding row child info
      // NOTE: Adding json data to end not associated with a table column
      var row = $('#TABLE').DataTable().row.add(
         ["", date, show_name, channel, dur, json]
      );
      row.draw();
   });
}

// For each selected row, send rpc cancel operation & delete table row
function Cancel() {
   var table = $('#TABLE').DataTable();
   $.each(table.rows('.selected'), function(i, rowNum) {
      var row = table.row(rowNum);
      var json = row.data()[NUMCOLS];
      if (json.recordingId) {
         var url = "/rpc?operation=Cancel&tivo=";
         url += encodeURIComponent(TIVO.value);
         var js = '{"recordingId":["' + json.recordingId + '"]}';
         url += "&json=" + encodeURIComponent(js);
         $.getJSON(url, function(data) {
            console.log(JSON.stringify(data, null, 3));
            if (data.type && data.type == "success") {
               row.remove().draw(false);
               showDialog("Cancelled",json.title,'warning',2);
            }
            else {
               showDialog("Cancel failed",JSON.stringify(data, null, 3),'error');
            }
         })
         .error(function(xhr, status) {
            util_handleError("Cancel", xhr, status);
         });
      }
   });
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
   var table = $('#TABLE').DataTable();
   var rows = TABLE[TIVO.value];
   $.each(rows, function(i, row) {
      table.row.add(row).draw();
   });
}