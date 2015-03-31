// This used for periodic update function for running jobs
RUNNING = 0;
MONITOR_INTERVAL = 5; // Seconds between monitor updates
RPC = {}; // Used to decide between RPC or XML for each TiVo

$(document).ready(function() {
   // Stream.html document elements
   MAXRATE = document.getElementById("MAXRATE");
   TIVO = document.getElementById("TIVO");
   BROWSE = document.getElementById("BROWSE");
   TYPE = document.getElementById("TYPE");
   NPLTABLE = document.getElementById("NPLTABLE");
   $("#TYPE").change(function () {FileBrowser();});
   NUMCOLS = 6;
   
   var rates = [
      "500k", "1000k", "1500k", "2000k", "2500k", "3000k",
      "3500k", "4000k", "4500k", "5000k"
   ];
   $.each(rates, function (i,rate) {
      var option = document.createElement("option");
      option.text = rate;
      option.value = rate;
      MAXRATE.appendChild(option);
   });
   $('#MAXRATE').val("2000k");
   $('#MAXRATE').change(function() { rateChanged(); });

   // Retrieve TiVos (both rpc and non-rpc)
   $.getJSON("/getTivos", function(data) {
      $.each(data, function( i, json ) {
         RPC[json.tivo] = json.rpc;
         var option = document.createElement("option");
         option.text = json.tivo;
         option.value = json.tivo;
         TIVO.appendChild(option);
      });
   })
   .error(function(xhr, status) {
      util_handleError("/getTivos", xhr, status);
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

// Replace all maxrate references in links with currently selected rate
function rateChanged() {
   var maxrate = MAXRATE.value;
   var re = /maxrate=[0-9]+k/;
   $('a').each( function(i, link) {
      link.href = link.href.replace(re, "maxrate=" + maxrate);
   });
}

function getNumRows(table) {
   return table.DataTable().column(0).data().length;
}

function MyShows() {
   // If table is hidden but has data, then simply unhide it and return
   if (NPLTABLE_DIV.style.display === "none") {
      var len = getNumRows($('#NPLTABLE'));
      if (len > 0) {
         showNplTable();
         return;
      }
   }
   
   if (RPC[TIVO.value] == 1) {
      MyShowsRPC();
   } else {
      MyShowsXML();
   }
}

function MyShowsRPC(offset) {   
   if (! offset)
      offset = 0;
   var limit = 50;
   var html = '<div style="color: blue">';
   message = 'PLEASE WAIT: GETTING SHOWS FROM ' + TIVO.value + ' ...';
   html += message + '</div>';
   BROWSE.innerHTML = html;
   if (offset == 0) {
      clearNplTable();
      hideTables();
   }
   showNplTable();
   var tivo = encodeURIComponent(TIVO.value);
   var url = "/getMyShows?limit=" + limit + "&tivo=" + tivo + "&offset=" + offset;
   $.getJSON(url, function(data) {
      if (data && data.length > 0) {
         loadNplDataRPC(data, tivo);
         offset += limit;
         if (data.length == limit)
            MyShowsRPC(offset);
         else
            BROWSE.innerHTML = "";
      } else {
         BROWSE.innerHTML = "";
      }
   })
   .error(function(xhr, status) {
      BROWSE.innerHTML = "";
      util_handleError("/getMyShows", xhr, status);
   });
}

function loadNplDataRPC(data, tivo) {
   var maxrate = MAXRATE.value;
   var format = $('input[name="type"]:checked').val();
   var baseUrl = "/transcode?format=" + format + "&url=";
   $.each(data, function (i, entry) {
      if (entry.hasOwnProperty("recording")) {
         var json = entry.recording[0];
         //console.log(JSON.stringify(json, null, 3));
         if (json.hasOwnProperty("__url__")) {
         
            // Copy protected or recording => not downloadable
            var candownload = true;
            if (json.hasOwnProperty("state") && json.state === "inProgress")
               candownload = false;
            if (json.hasOwnProperty("drm")) {
               if (json.drm.hasOwnProperty("tivoToGo")) {
                  if (json.drm.tivoToGo === false)
                     candownload = false;
               }
            }

            var date = "";
            if (json.hasOwnProperty("startTime")) {
               date = util_getTime(json.startTime);
            }
            
            var duration = 0;
            var dur = "";
            if (json.hasOwnProperty("duration")) {
               duration = json.duration;
               dur = util_secsToHM(json.duration);
            }
         
            var show_name = util_getShowName(json);
            var show_url = baseUrl + encodeURIComponent(json.__url__);
            show_url += "&name=" + encodeURIComponent(show_name + " (" + date + ")");
            show_url += "&tivo=" + encodeURIComponent(tivo);
            show_url += "&duration=" + duration;
            show_url += "&maxrate=" + maxrate;
            var show = show_name;
            if (candownload) {
               show += '<br><a href="' + show_url;
               show += '" target="__blank">[transcode & play]</a>&nbsp;&nbsp;&nbsp;&nbsp;';
               show += '<a href="javascript:;" onclick="TiVoDownload(\'';
               show += encodeURIComponent(json.__url__) + '\'';
               show += ', \'' + encodeURIComponent(show_name + " (" + date + ")") + '\', \'';
               show += encodeURIComponent(tivo) + '\', \'' + duration;
               show += '\')">[transcode]</a>';
            }

            var channel = util_getChannel(json);

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

function xmlGetFirst(xml, key) {
   var nodes = xml.getElementsByTagName(key);
   if (nodes.length > 0) {
      if (nodes[0].childNodes.length > 0) {
         return nodes[0].childNodes[0];
      } else
         return;
   } else
      return;
}

function xmlGetFirstVal(xml, key) {
   var node = xmlGetFirst(xml, key);
   if (node)
      return node.nodeValue;
   else
      return;
}

function MyShowsXML(offset) {
   if (! offset)
      offset = 0;
   var html = '<div style="color: blue">';
   message = 'PLEASE WAIT: GETTING SHOWS ' + offset + '- FROM ' + TIVO.value + ' ...';
   html += message + '</div>';
   BROWSE.innerHTML = html;
   if (offset == 0) {
      clearNplTable();
      hideTables();
   }
   showNplTable();
   var tivo = encodeURIComponent(TIVO.value);
   var url = "/getMyShows?xml=1&tivo=" + tivo + "&offset=" + offset;
   $.get(url, function(data) {
      if (data && data.length > 0) {
         var parser = new DOMParser();
         var xml = parser.parseFromString(data,"text/xml");
         var TotalItems = parseInt(xmlGetFirstVal(xml, "TotalItems"));
         var ItemCount = parseInt(xmlGetFirstVal(xml, "ItemCount"));
         offset += ItemCount;
         loadNplDataXML(xml, tivo);
         if (offset < TotalItems)
            MyShowsXML(offset);
         else
            BROWSE.innerHTML = "";
      } else {
         BROWSE.innerHTML = "";
      }
   })
   .error(function(xhr, status) {
      BROWSE.innerHTML = "";
      util_handleError("/getMyShows", xhr, status);
   });
}

function loadNplDataXML(xml, tivo) {
   var maxrate = MAXRATE.value;
   var format = $('input[name="type"]:checked').val();
   var baseUrl = "/transcode?format=" + format + "&url=";

   var shows = xml.getElementsByTagName("Item");
   $.each(shows, function (i, node) {
         
      // URL
      var url = "";
      var show_url = "";
      var Link = xmlGetFirst(node, "Links");
      if (Link) {
         var Content = xmlGetFirst(Link.parentNode, "Content");
         if (Content) {
            var Url = xmlGetFirstVal(Content.parentNode, "Url");
            if (Url) {
               url = Url;
            }
         }
      }

      if (url.length > 0) {
         // Copy protected or recording => not downloadable
         var candownload = true;
         var CopyProtected = xmlGetFirstVal(node, "CopyProtected");
         if (CopyProtected)
            candownload = false;
         var InProgress = xmlGetFirstVal(node, "InProgress");
         if (InProgress)
            candownload = false;
            
         var date = "";
         var CaptureDate = xmlGetFirstVal(node, "CaptureDate");
         if (CaptureDate) {
            var gmt = parseInt(CaptureDate,16)*1000;
            date = util_getTimeFromGmt(gmt);
         }
         var ShowingStartTime = xmlGetFirstVal(node, "ShowingStartTime");
         if (ShowingStartTime) {
            var gmt = parseInt(ShowingStartTime,16)*1000;
            date = util_getTimeFromGmt(gmt);
         }
         
         var duration = 0;
         var dur = "";
         var Duration = xmlGetFirstVal(node, "Duration");
         if (Duration) {
            duration = Duration/1000;
            dur = util_secsToHM(duration);
         }
               
         var title = xmlGetFirstVal(node, "Title");
         var EpisodeNumber = xmlGetFirstVal(node, "EpisodeNumber");
         if (EpisodeNumber)
            title += " [Ep " + "%03d".sprintf(parseInt(EpisodeNumber)) + "]";
         var subtitle = xmlGetFirstVal(node, "EpisodeTitle");
         if (subtitle)
            title += " - " + subtitle;
         var show = title;
         if (candownload) {
            var show_url = baseUrl + encodeURIComponent(url);
            show_url += "&name=" + encodeURIComponent(title + " (" + date + ")");
            show_url += "&tivo=" + encodeURIComponent(tivo);
            show_url += "&duration=" + duration;
            show_url += "&maxrate=" + maxrate;
            
            show += '<br><a href="' + show_url;
            show += '" target="__blank">[transcode & play]</a>&nbsp;&nbsp;&nbsp;&nbsp;';
            show += '<a href="javascript:;" onclick="TiVoDownload(\'';
            show += encodeURIComponent(url) + '\'';
            show += ', \'' + encodeURIComponent(title + " (" + date + ")") + '\', \'';
            show += encodeURIComponent(tivo) + '\', \'' + duration;
            show += '\')">[transcode]</a>';
         }
         
         var channel = "";
         var SourceStation = xmlGetFirstVal(node, "SourceStation");
         if (SourceStation) {
            channel = SourceStation;
         }
         var SourceChannel = xmlGetFirstVal(node, "SourceChannel");
         if (SourceChannel) {
            channel += "=" + SourceChannel;
         }
         
         var size = "";
         var SourceSize = xmlGetFirstVal(node, "SourceSize");
         if (SourceSize) {
            var size_GB = SourceSize/Math.pow(2,30);
            size = "%.2f GB".sprintf(size_GB);         
         }

         // NOTE: 1st column is dummy used for hiding/unhiding row child info
         // NOTE: Adding node xml data to end not associated with a table column
         var row = $('#NPLTABLE').DataTable().row.add(
            ["", show, date, channel, dur, size, node]
         );
         row.draw();
      }
   });
}

function loadFileData(data, baseUrl) {
   $.each(data, function (i, file) {
      if (file != "NONE") {
         var url = baseUrl + encodeURIComponent(file);
         var link = file;
         link += '<br><a href="' + url + '" target="__blank">[transcode & play]</a>&nbsp;&nbsp;&nbsp;&nbsp;';
         link += '<a href="javascript:;" onclick="FileDownload(\'' + encodeURIComponent(file) + '\')">[transcode]</a>';         
         var row = $('#FILETABLE').DataTable().row.add([link]);
         row.draw();
      }
   });
}

function TiVoDownload(show_url, name, tivo, duration) {
   var maxrate = MAXRATE.value;
   var format = $('input[name="type"]:checked').val();
   url = "/transcode?format=" + format + "&download=1";
   url += "&url=" + show_url + "&name=" + name + "&tivo=" + tivo;
   url += "&duration=" + duration + "&maxrate=" + maxrate;
   $.get(url, function(response) {
      if (response.indexOf("href=") == -1) {
         showDialog("TiVo transcode",response,'warning',2);
      }
   })
   .error(function(xhr, status) {
      util_handleError("transcode", xhr, status);
   });
}

function FileDownload(file) {
   var maxrate = MAXRATE.value;
   var format = $('input[name="type"]:checked').val();
   url = "/transcode?format=" + format + "&download=1";
   url += "&file=" + file + "&maxrate=" + maxrate;
   $.get(url, function(response) {
      if (response.indexOf("href=") == -1) {
         showDialog("File transcode",response,'warning', 2);
      }
   })
   .error(function(xhr, status) {
      util_handleError("transcode", xhr, status);
   });
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

function FileBrowser() {
   FILETABLE_DIV.mode = "Files";
      
   clearFileTable();
   hideTables();
   showFileTable();
   var maxrate = MAXRATE.value;
   var format = $('input[name="type"]:checked').val();
   var baseUrl = "/transcode?format=" + format + "&maxrate=" + maxrate + "&file=";
   $.getJSON("/getVideoFiles", function(data) {
      loadFileData(data, baseUrl);
   })
   .error(function(xhr, status) {
      util_handleError("/getVideoFiles", xhr, status);
   });
}

function GetCached(from_monitor) {
   if ( ! from_monitor )
      FILETABLE_DIV.mode = "Cached";

   clearFileTable();
   hideTables();
   showFileTable();
   $.getJSON("/transcode?getCached=1", function(data) {
      loadCacheData(data);
   })
   .error(function(xhr, status) {
      util_handleError("/transcode?getCached", xhr, status);
   });
}

function loadCacheData(data) {
   var count = 0;
   var running = 0;
   $.each(data, function (i, json) {
      if (json != "NONE") {
         count += 1;
         var url = json.url;
         var name = json.name;

         var time = 0;
         if ( json.hasOwnProperty("time") )
            time = json.time;

         var duration = 0;
         if ( json.hasOwnProperty("duration") )
            duration = json.duration;

         var prefix = "";
         if ( time > 0 )
            prefix = "[" + util_secsToHM(time) + "]";
         if ( json.hasOwnProperty("running") ) {
            running++;
            if (time > 0 && duration > 0) {
               var pct = "%5.1f %%".sprintf(100*time/duration);
               prefix = "(running: " + pct + ")";
            } else if (time > 0) {
               prefix = "(running: " + util_secsToHM(time) + ")";
            } else {
               prefix = "(running)";
            }
         }
         if ( json.hasOwnProperty("partial") ) {
            if ( ! json.hasOwnProperty("running") ) {
               if (time > 0 && duration > 0) {
                  prefix = "(partial: " + util_secsToHM(time) + " / " + util_secsToHM(duration) + ")";
               } else if (time > 0) {
                  prefix = "(partial: " + util_secsToHM(time) + ")";
               } else {
                  prefix = "(partial)";
               }
            }
         }
         if (prefix.length > 0)
            name = prefix + " " + name;

         var link = name;
         link += '<br><a href="' + url + '" target="__blank">[play]</a>';
         link += '&nbsp;&nbsp;&nbsp;&nbsp;';
         if ( ! json.hasOwnProperty("running") ) {
            link += '<a href="javascript:;" onclick="RemoveCached(\'';
            link += encodeURIComponent(json.url) + '\')">[remove]</a>';
         }
         var row = $('#FILETABLE').DataTable().row.add([link]);
         row.draw();
      }
   });
   if (count > 0) {
      var link = '<a href="javascript:;" onclick="RemoveCached(\'all\')">[remove all]</a>';
      var row = $('#FILETABLE').DataTable().row.add([link]);
      row.draw();
   }
      
   if (RUNNING) {
      // If monitor is running, turn it off when if there are no jobs currently running
      if (running == 0)
         RUNNING_monitor_off();
   } else {
      // Start monitoring function to update cached/running table every few seconds
      if (running > 0) {
         console.log("RUNNING monitor started");
         RUNNING = setInterval(function(){RUNNING_monitor();}, 1000*MONITOR_INTERVAL);
      }
   }
}

function RemoveCached(link_url) {
   var url = "/transcode?removeCached=" + link_url;
   $.get(url, function(data) {
      $("#GetCached").click()
      showDialog("Remove cached",data,'warning',2);
   })
   .error(function(xhr, status) {
      util_handleError("removeCached", xhr, status);
   });
}

function KillAll() {
   $.get("/transcode?killall=1", function(data) {
      $("#ShowRunning").click()
      showDialog("Kill all",data,'warning',2);
   })
   .error(function(xhr, status) {
      util_handleError("killall", xhr, status);
   });
}

function Kill(job) {
   var url = "/transcode?kill=" + job;
   $.get(url, function(data) {
      $("#ShowRunning").click()
      showDialog("Kill",data,'warning',2);
   })
   .error(function(xhr, status) {
      util_handleError("kill", xhr, status);
   });
}

function Running(from_monitor) {
   if ( ! from_monitor )
      FILETABLE_DIV.mode = "Running";
   clearFileTable();
   hideTables();
   showFileTable();
   $.getJSON("/transcode?running=1", function(data) {
      loadRunningData(data);
   })
   .error(function(xhr, status) {
      util_handleError("running", xhr, status);
   });
}

function loadRunningData(data) {
   if (data[0] === "NONE") {
      var link = "NO JOBS RUNNING";
      var row = $('#FILETABLE').DataTable().row.add([link]);
      row.draw();
   } else {
      $.each(data, function (i, job) {
         var time = 0;
         if ( job.hasOwnProperty("time") )
            time = job.time;

         var duration = 0;
         if ( job.hasOwnProperty("duration") )
            duration = job.duration;
            
         var name = job.name;
            
         var prefix = "";
         if (time > 0 && duration > 0) {
            var pct = "%5.1f %%".sprintf(100*time/duration);
            prefix = "(" + pct + ")";
         } else if (time > 0) {
            prefix = "(" + util_secsToHM(time) + ")";
         }
         if (prefix.length > 0)
            name = prefix + " " + name;
            
         var link = '<a href="javascript:;" onclick="Kill(\'' + encodeURIComponent(job.inputFile);
         link += '\')">[kill]</a> ' + name;
         var row = $('#FILETABLE').DataTable().row.add([link]);
         row.draw();
      });
      
      // Start monitoring function to update cached/running table every few seconds
      if (! RUNNING) {
         console.log("RUNNING monitor started");
         RUNNING = setInterval(function(){RUNNING_monitor();}, 3000);
      }
   }
}

// Monitor function to periodically update Running or Cached table if currently displayed
function RUNNING_monitor() {
   var stop = false;
   if (FILETABLE_DIV.style.display === "none") {
      stop = true;
   }
   if (FILETABLE_DIV.mode != "Running" && FILETABLE_DIV.mode != "Cached")
      stop = true;
   if ( ! stop ) {
      var len = getNumRows($('#FILETABLE'));
      if (len == 0)
         stop = true;
      else {
         var row1 = $('#FILETABLE').DataTable().column(0).data()[0];
         if (row1 === "NO JOBS RUNNING")
            stop = true;
      }
   }
   
   if (RUNNING && stop) {
      RUNNING_monitor_off();
      return;
   }
   if (FILETABLE_DIV.mode === "Running")
      Running(1);
   if (FILETABLE_DIV.mode === "Cached")
      GetCached(1);
};

function RUNNING_monitor_off() {
   console.log("RUNNING monitor stopped");
   clearInterval(RUNNING);
   RUNNING = 0;
}

// Callback when details column is clicked on in a row
// Hides or unhides row child data
function nplDetailsClicked() {
   var table = $('#NPLTABLE').DataTable();
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

function formatXml(xml) {
    var formatted = '';
    var reg = /(>)(<)(\/*)/g;
    xml = xml.replace(reg, '$1\r\n$2$3');
    var pad = 0;
    jQuery.each(xml.split('\r\n'), function(index, node) {
        var indent = 0;
        if (node.match( /.+<\/\w[^>]*>$/ )) {
            indent = 0;
        } else if (node.match( /^<\/\w/ )) {
            if (pad != 0) {
                pad -= 1;
            }
        } else if (node.match( /^<\w[^>]*[^\/]>.*$/ )) {
            indent = 1;
        } else {
            indent = 0;
        }

        var padding = '';
        for (var i = 0; i < pad; i++) {
            padding += '  ';
        }

        formatted += padding + node + '\r\n';
        pad += indent;
    });

    return formatted;
}

// This is formatting to use for the displaying row child data
// Currently this displays whole json or xml contents
function detailsFormat(d) {
   if (d[NUMCOLS].title)
      return '<pre>' + JSON.stringify(d[NUMCOLS], null, 3) + '</pre>';
   else {
      var s = formatXml(new XMLSerializer().serializeToString(d[NUMCOLS]));
      // Need to replace <> with [] so that it's not interpreted as html
      s = s.replace(/</g, "[");
      s = s.replace(/>/g, "]");
      return '<pre>' + s + '</pre>';
   }
}