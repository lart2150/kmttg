$(document).ready(function() {
   // Stream.html document elements
   TIVO = document.getElementById("TIVO");
   BROWSE = document.getElementById("BROWSE");
   TYPE = document.getElementById("TYPE");
   $("#TYPE").change(function () {FileBrowser();});

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
});

function MyShows() {
   var format = $('input[name="type"]:checked').val();
   var baseUrl = "/transcode?format=" + format + "&url=";
   $.getJSON("/getMyShows?tivo=" + encodeURIComponent(TIVO.value), function(data) {
      var html = "";
      $.each(data, function (i, entry) {
         if (entry.hasOwnProperty("recording")) {
            var json = entry.recording[0];
            //console.log(JSON.stringify(json, null, 3));
            html += '<a target="_blank" href="' + baseUrl;
            if (json.hasOwnProperty("__url__")) {
               url = json.__url__;
               name = "";
               if (json.hasOwnProperty("startTime")) {
                  name = "(" + json.startTime + ") ";
               }
               if (json.hasOwnProperty("title")) {
                  name += json.title;
               }
               if (json.hasOwnProperty("subtitle")) {
                  name += " - " + json.subtitle;
               }
               html += encodeURIComponent(url) + '">' + name + '</a><br>';
            }
         }
      });
      BROWSE.innerHTML = html;
   })
   .error(function(xhr, status) {
      handleError("/getMyShows", xhr, status);
   });
}

function FileBrowser() {
   var format = $('input[name="type"]:checked').val();
   var baseUrl = "/transcode?format=" + format + "&file=";
   $.getJSON("/getVideoFiles", function(data) {
      var html = "";
      $.each(data, function (i, file) {
         html += '<a target="_blank" href="' + baseUrl;
         html += encodeURIComponent(file) + '">' + file + '</a><br>';
      });
      BROWSE.innerHTML = html;
   })
   .error(function(xhr, status) {
      handleError("/getVideoFiles", xhr, status);
   });
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
            html = "<div>NONE</div>";
         } else {
            html += '<a target="bottom" href="' + baseUrl;
            html += encodeURIComponent(job) + '">kill ' + job + '</a><br>';
         }
      });
      BROWSE.innerHTML = html;
   })
   .error(function(xhr, status) {
      handleError("running", xhr, status);
   });
}

function handleError(prefix, xhr, status) {
   if ( status != "success" ) {
      var error = "ERROR (" + prefix + "):\n";
      error += JSON.stringify(xhr, null, 3);
      alert(error);
   }
}
