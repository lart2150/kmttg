$(document).ready(function() {
   TIVO = document.getElementById("TIVO");
   TABLE = document.getElementById("TABLE");
   LOAD = document.getElementById("LOAD");
   SELECT = document.getElementById("SELECT");
   NUMCOLS = 9;

   // NOTE: column 0 is a special column reserved for display
   // of additional row information
   $('#TABLE').dataTable({
     dom: 'T<"clear">lfrtip',
     paging: false,
     ordering: false, // turn off column sorting
     autoWidth: true,
     tableTools: {
       "sRowSelect": "os",
       "aButtons": [ "select_all", "select_none" ]
     },
     columns: [
      {
       "class":          'details-control',
       "orderable":      false,
       "data":           null,
       "defaultContent": '',
       "width":          "10px"
      },
      { "width": "50px" },
      null,
      { "width": "100px" },
      { "width": "60px" },
      { "width": "30px" },
      { "width": "30px" },
      { "width": "30px" },
      { "width": "30px" }
     ],
   });
   
   // Add event listener for opening and closing row details
   $('#TABLE tbody').on('click', 'td.details-control', detailsClicked);
   
   // Add keyboard event listener
   document.onkeydown = keyPressed;
});

function keyPressed(e) {
   switch (e.keyCode) {
      case 38:
         Up();
         break;
      case 40:
         Down();
         break;
   }
}

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
   handleError("/getRpcTivos", xhr, status);
});

//button callback - RPC call to retrieve SP info for selected TiVo
function Refresh() {
   clearTable();
   var url = "/rpc?operation=SeasonPasses&tivo=" + encodeURIComponent(TIVO.value);
   $.getJSON(url, SeasonPasses)
   .error(function(xhr, status) {
      handleError("SeasonPasses", xhr, status);
   });
}

function tivoChanged() {
   if (TABLE.hasOwnProperty(TIVO.value)) {
      clearTable();
      loadTable();
      return;
   }
   Refresh();
}

// Parse SP data from RPC call
function SeasonPasses(data) {
   if (data.hasOwnProperty("subscription")) {
      loadData("", data.subscription);
      saveTable();
   }
}

// Load SP data
function loadData(prefix, data) {
   var priorityNum = 1;
   $.each(data, function(i, j) {
      
      var priority = "" + priorityNum;
      
      var show = prefix;
      if (j.hasOwnProperty("title"))
         show += j.title;
         
      var channel = "";
      if (j.hasOwnProperty("idSetSource")) {
         var o = j.idSetSource;
         if (o.hasOwnProperty("channel")) {
            var o2 = o.channel;
            if (o2.hasOwnProperty("channelNumber"))
               channel += o2.channelNumber;
            if (o2.hasOwnProperty("callSign")) {
               if (o2.callSign.toLowerCase() == "all channels")
                  channel += o2.callSign;
               else
                  channel += "=" + o2.callSign;
            }
         }
      }

      var record = "";
      if (j.hasOwnProperty("showStatus"))
         record = j.showStatus;

      var keep = "";
      if (j.hasOwnProperty("keepBehavior"))
         keep = j.keepBehavior;

      var num = "0";
      if (j.hasOwnProperty("maxRecordings"))
         num = j.maxRecordings;

      var start = "0";
      if (j.hasOwnProperty("startTimePadding"))
         start = Math.round(j.startTimePadding/60);

      var end = "0";
      if (j.hasOwnProperty("endTimePadding"))
         end = Math.round(j.endTimePadding/60);

      // NOTE: 1st column is dummy used for hiding/unhiding row child info
      // NOTE: Adding json data to end not associated with a table column
      var row = $('#TABLE').DataTable().row.add(
         ["", priority, show, channel, record, keep, num, start, end, j]
      );
      row.draw();
      
      priorityNum += 1;
   });
}

// For each selected row, send rpc delete operation & delete table row
function Delete() {
   var table = $('#TABLE').DataTable();
   $.each(table.rows('.selected'), function(i, rowNum) {
      var row = table.row(rowNum);
      var json = row.data()[NUMCOLS];
      if (json.subscriptionId) {
         var url = "/rpc?operation=Unsubscribe&tivo=";
         url += encodeURIComponent(TIVO.value);
         var js = '{"subscriptionId":"' + json.subscriptionId + '"}';
         url += "&json=" + encodeURIComponent(js);
         $.getJSON(url, function(data) {
            console.log(JSON.stringify(data, null, 3));
            if (data.type && data.type == "success")
               row.remove().draw(false);
            else {
               showDialog("Unsubscribe failed",JSON.stringify(data, null, 3),'error');
            }
         })
         .error(function(xhr, status) {
            handleError("Unsubscribe", xhr, status);
         });
      }
   });
}

// rpc call to save Season Passes for currently selected tivo
// NOTE: This is all done kmttg side
function Save() {
   var url = "/rpc?operation=SPSave&tivo=" + encodeURIComponent(TIVO.value);
   $.get(url, function(data) {
      showDialog("SP Save",data,'warning');
   })
   .error(function(xhr, status) {
      handleError("SPSave", xhr, status);
   });
}

function CancelSelected() {
   LOAD.style.display = 'none';
   TABLE.style.display = 'block';
}

function LoadSelected() {
   CancelSelected();
   var selectedFile = SELECT.value;
   var url = "/rpc?operation=SPLoad&tivo=" + encodeURIComponent(TIVO.value);
   url += "&file=" + encodeURIComponent(selectedFile);
   $.getJSON(url, function(data) {
      clearTable();
      loadData("Loaded: ", data);
   })
   .error(function(xhr, status) {
      handleError("SPLoad", xhr, status);
   });
}

function Load() {
   // Clear out SELECT list
   SELECT.options.length = 0;
   // Get list of sp files & prompt user to choose one
   var url = "/rpc?operation=SPFiles&tivo=" + encodeURIComponent(TIVO.value);
   $.getJSON(url, function(data) {
      $.each(data, function( i, file ) {
         SELECT.options[SELECT.options.length] = new Option(
            file, file
         );
      });
      LOAD.style.display = 'block';
      TABLE.style.display = 'none';
   })
   .error(function(xhr, status) {
      handleError("SPFiles", xhr, status);
   });
}

// NOTE: NOT WORKING
function Copy() {
   // TODO: Obtain destination TiVo to copy to
   var tivo = "Elite";
   
   var url = "/rpc?operation=SeasonPasses&tivo=" + encodeURIComponent(tivo);
   $.getJSON(url, function(data) {
      if (data.hasOwnProperty("subscription")) {
         var spdata = data.subscription;
         var table = $('#TABLE').DataTable();
         $.each(table.rows('.selected'), function(i, rowNum) {
            var row = table.row(rowNum);
            if (! row.data()) {
               showDialog("SP Copy","No rows selected!",'warning',2);
               return;
            }
            var json = row.data()[NUMCOLS];

            if (json.subscriptionId) {
               // Check against existing spdata
               var exists = 0;
               $.each(spdata, function(i, sp) {
                  if (sp.hasOwnProperty("subscriptionId")) {
                     if (sp.subscriptionId === json.subscriptionId) {
                        exists = 1;
                     }
                  }
               });
               if (exists == 0) {
                  var url = "/rpc?operation=Seasonpass&tivo=";
                  url += encodeURIComponent(TIVO.value);
                  var js = '{"subscriptionId":"' + json.subscriptionId + '"}';
                  url += "&json=" + encodeURIComponent(js);
                  $.getJSON(url, function(data) {
                     //console.log(JSON.stringify(data, null, 3));
                     if (data.type && data.type == "success")
                        console.log("SUCCESS");
                     else {
                        console.log("FAILED: " + JSON.stringify(data, null, 3));
                     }
                  })
                  .error(function(xhr, status) {
                     handleError("Seasonpass", xhr, status);
                  });
               } else {
                  console.log("NOT COPYING: already exists");
               }
            }
         });
      }
   })
   .error(function(xhr, status) {
      handleError("SeasonPasses", xhr, status);
   });
}

function Reorder() {
   var table = $('#TABLE').DataTable();
   var numrows = table.column(0).data().length;
   if (numrows > 0) {
      json = '{"subscriptionId" : [';
      var prefix = "";
      for (var i=0; i<numrows; ++i) {
         var row = table.row(i);
         var j = row.data()[NUMCOLS];
         if (j.subscriptionId) {
            json += prefix + '"' + j.subscriptionId + '"';
         }
         prefix = ", ";
      }
      json += ']}';

      // rpc Prioritize call
      var url = "/rpc?operation=Prioritize&tivo=";
      url += encodeURIComponent(TIVO.value);
      url += "&json=" + encodeURIComponent(json);
      $.getJSON(url, function(data) {
         if (data.type && data.type == "success")
            Refresh();
      })
      .error(function(xhr, status) {
         handleError("Prioritize", xhr, status);
      });
   }
}

function Upcoming() {
   // TODO
   console.log("Upcoming");
}

function selectFileDialog(title, text) {
   return $('<div></div>').append(text)
      .dialog({
         resizable: true,
         modal: true,
         buttons: {
            "OK" : function() {$(this).dialog("close");}
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

// Move selected row up
function Up() {
   var table = $('#TABLE').DataTable();
   var selected = table.$('tr.selected').data();
   if (selected) {
      var index = Math.round(table.rows('.selected')[0]);
      if ((index-1) >= 0) {
         var datatable = $('#TABLE').dataTable(); // gets jquery object
         var data = datatable.fnGetData();
         datatable.fnClearTable();
         data.splice((index-1), 0, data.splice(index,1)[0]);
         datatable.fnAddData(data);
         clickRow(index-1);
      }
   }
}

// Move selected row down
function Down() {
   var table = $('#TABLE').DataTable();
   var selected = table.$('tr.selected').data();
   if (selected) {
      var numrows = table.column(0).data().length;
      var index = Math.round(table.rows('.selected')[0]);
      if ((index+1) < numrows) {
         var datatable = $('#TABLE').dataTable(); // gets jquery object
         var data = datatable.fnGetData();
         datatable.fnClearTable();
         data.splice((index+1), 0, data.splice(index,1)[0]);
         datatable.fnAddData(data);
         clickRow(index+1);
      }
   }
}

function clickRow(index) {
   $("#TABLE tbody tr:eq(" + index + ")").click();
}

function handleError(prefix, xhr, status) {
   if ( status != "success" ) {
      var message = xhr;
      if ( xhr.hasOwnProperty("responseText") )
         message = xhr.responseText;
      showDialog(prefix,message,'error');
   }
}