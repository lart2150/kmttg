$(document).ready(function() {
   // Stream.html document elements
   TIVO = document.getElementById("TIVO");
   BROWSE = document.getElementById("BROWSE");
   TYPE = document.getElementById("TYPE");
   NPLTABLE = document.getElementById("NPLTABLE");
   $("#TYPE").change(function () {FileBrowser();});
   NUMCOLS = 6;

   // Retrieve rpc enabled TiVos
   $.getJSON("/getRpcTivos", function(data) {
      $.each(data, function( i, value ) {
         var option = document.createElement("option");
         option.text = value;
         option.value = value;
         TIVO.appendChild(option);
      });
   })
   .error(function(xhr, status) {
      handleError("/getRpcTivos", xhr, status);
   });

   // NPL table
   // NOTE: column 0 is a special column reserved for display
   // of additional row information
   $('#NPLTABLE').dataTable({
     dom: 'T<"clear">lfrtip',
     paging: false,
     ordering: false, // turn off column sorting
     autoWidth: true,
     tableTools: {
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
      null,
     ],
   });

   // FILE table
   $('#FILETABLE').dataTable({
     dom: 'T<"clear">lfrtip',
     paging: false,
     ordering: false, // turn off column sorting
     autoWidth: true,
     tableTools: {
       "aButtons": []
     },
     columns: [
      null,
     ],
   });

   // Add event listener for opening and closing row details
   $('#NPLTABLE tbody').on('click', 'td.details-control', nplDetailsClicked);
});


function MyShows(offset) {
   if (! offset)
      offset = 0;
   var limit = 50;
   var html = '<div style="color: blue">';
   message = 'PLEASE WAIT: GETTING SHOWS ' + offset + '-' + (offset+limit) + ' FROM ' + TIVO.value + ' ...';
   html += message + '</div>';
   BROWSE.innerHTML = html;
   if (offset == 0) {
      clearNplTable();
      hideTables();
   }
   showNplTable();
   var format = $('input[name="type"]:checked').val();
   var baseUrl = "/transcode?format=" + format + "&url=";
   var tivo = encodeURIComponent(TIVO.value);
   var url = "/getMyShows?limit=" + limit + "&tivo=" + tivo + "&offset=" + offset;
   $.getJSON(url, function(data) {
      if (data && data.length > 0) {
         loadNplData(data);
         offset += limit;
         if (data.length == limit)
            MyShows(offset);
         else
            BROWSE.innerHTML = "";
      } else {
         BROWSE.innerHTML = "";
      }
   })
   .error(function(xhr, status) {
      go = 0;
      BROWSE.innerHTML = "";
      handleError("/getMyShows", xhr, status);
   });
}

function loadNplData(data) {
   var format = $('input[name="type"]:checked').val();
   var baseUrl = "/transcode?format=" + format + "&url=";
   $.each(data, function (i, entry) {
      if (entry.hasOwnProperty("recording")) {
         var json = entry.recording[0];
         //console.log(JSON.stringify(json, null, 3));
         if (json.hasOwnProperty("__url__")) {

            var date = "";
            if (json.hasOwnProperty("startTime")) {
               date = getTime(json.startTime);
            }
         
            var show_name = "";
            if (json.hasOwnProperty("title")) {
               show_name = json.title;
            }
            if (json.hasOwnProperty("subtitle")) {
               show_name += " - " + json.subtitle;
            }
            var show_url = baseUrl + encodeURIComponent(json.__url__);
            show_url += "&name=" + encodeURIComponent(show_name + " (" + date + ")");
            var show = '<a href="' + show_url + '" target="__blank">' + show_name + '</a>';

            var channel = "";
            if (json.hasOwnProperty("channel")) {
               var chan = json.channel;
               if (chan.hasOwnProperty("callSign")) {
                  channel = chan.callSign;
               } else {
                  if (chan.has("name")) {
                     channel = chan.name;
                  }
               }
               if(chan.hasOwnProperty("channelNumber")) {
                  channel += "=" + chan.channelNumber;
               }
            }

            var dur = "";
            if (json.hasOwnProperty("duration")) {
               dur = secsToHM(json.duration);
            }

            var size = "";
            if (json.hasOwnProperty("size")) {
               var size_GB = json.size/Math.pow(2,20);
               size = "%.2f GB".sprintf(size_GB);
            }

            // NOTE: 1st column is dummy used for hiding/unhiding row child info
            // NOTE: Adding json data to end not associated with a table column
            var row = $('#NPLTABLE').DataTable().row.add(
               ["", show, date, channel, dur, size, json]
            );
            row.draw();
         }
      }
   });
}

function secsToHM(secs) {
   var hours = Math.floor(secs/3600);
   var mins = Math.floor((secs - (hours * 3600))/60);
   return "%d:%02d".sprintf(hours,mins);
}

function getTime(startTime) {
   var week = ["Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri"];
   var dt = new Date(Date.parse(startTime.replace(/-/g, "/") + " GMT"));
   var date = "%s %02d/%02d/%02d %02d:%02d".sprintf(
      week[dt.getUTCDay()],
      dt.getMonth()+1,
      dt.getDate(),
      dt.getFullYear()-2000,
      dt.getHours(),
      dt.getMinutes()
   );
   return date;
}

function hideTables() {
   NPLTABLE_DIV.style.display = 'none';
   FILETABLE_DIV.style.display = 'none';
}

function clearNplTable() {
   $('#NPLTABLE').DataTable().clear().draw();
}

function hideNplTable() {
   NPLTABLE_DIV.style.display = 'none';
}

function showNplTable() {
   NPLTABLE_DIV.style.display = 'block';
}

function clearFileTable() {
   $('#FILETABLE').DataTable().clear().draw();
}

function hideFileTable() {
   FILETABLE_DIV.style.display = 'none';
}

function showFileTable() {
   FILETABLE_DIV.style.display = 'block';
}

function getUrl(json) {
   var url = "";
   if (json.hasOwnProperty("__url__"))
      url = json.__url__;
   return url;
}

function FileBrowser() {
   clearFileTable();
   hideTables();
   showFileTable();
   var format = $('input[name="type"]:checked').val();
   var baseUrl = "/transcode?format=" + format + "&file=";
   $.getJSON("/getVideoFiles", function(data) {
      loadFileData(data, baseUrl);
   })
   .error(function(xhr, status) {
      handleError("/getVideoFiles", xhr, status);
   });
}

function loadFileData(data, baseUrl) {
   $.each(data, function (i, file) {
      if (file != "NONE") {
         var url = baseUrl + encodeURIComponent(file);
         var link = '<a href="' + url + '" target="__blank">' + file + '</a>';
         var row = $('#FILETABLE').DataTable().row.add([link]);
         row.draw();
      }
   });
}

function GetCached() {
   clearFileTable();
   hideTables();
   showFileTable();
   $.getJSON("/transcode?getCached=1", function(data) {
      loadCacheData(data);
   })
   .error(function(xhr, status) {
      handleError("/transcode?getCached", xhr, status);
   });
}

function loadCacheData(data) {
   var count = 0;
   $.each(data, function (i, json) {
      if (json != "NONE") {
         count += 1;
         var url = json.url;
         var name = json.name;
         if ( json.hasOwnProperty("running") )
            name = "(still running) " + name;
         if ( json.hasOwnProperty("partial") ) {
            if ( ! json.hasOwnProperty("running") )
               name = "(partial) " + name;
         }
         var link = '<a href="' + url + '" target="__blank">' + name + '</a>';
         if ( ! json.hasOwnProperty("running") ) {
            var url2 = "/transcode?removeCached=" + json.url;
            link += '<br><a href="' + url2 + '" target="__blank">' + '[remove]' + '</a>';
         }
         var row = $('#FILETABLE').DataTable().row.add([link]);
         row.draw();
      }
   });
   if (count > 0) {
      var url = "/transcode?removeCached=all";
      var link = '<a href="' + url + '" target="__blank">' + '[remove all]' + '</a>';
      var row = $('#FILETABLE').DataTable().row.add([link]);
      row.draw();
   }
}

function KillAll() {
   $.get("/transcode?killall=1", function(data) {
      alert(data);
   })
   .error(function(xhr, status) {
      handleError("killall", xhr, status);
   });
}

function Running() {
   var baseUrl = "/transcode?kill=";
   $.getJSON("/transcode?running=1", function(data) {
      var html = "";
      $.each(data, function (i, job) {
         if ( job === "NONE" ) {
            html = '<div style="color: blue">NO JOBS RUNNING</div>';
         } else {
            html += '<a target="_blank" href="' + baseUrl;
            html += encodeURIComponent(job) + '">kill ' + job + '</a><br>';
         }
      });
      BROWSE.innerHTML = html;
   })
   .error(function(xhr, status) {
      handleError("running", xhr, status);
   });
}

// Callback when details column is clicked on in a row
// Hides or unhides row child data
function nplDetailsClicked() {
   var table = $('#NPLTABLE').DataTable();
   var tr = $(this).closest('tr');
   var row = table.row(tr);
   console.log(row);

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

function handleError(prefix, xhr, status) {
   if ( status != "success" ) {
      var error = "ERROR (" + prefix + "):\n";
      error += JSON.stringify(xhr, null, 3);
      alert(error);
   }
}
