$(document).ready(function() {
   // Remote.html document elements
   TIVO = document.getElementById("TIVO");
   LAUNCH = document.getElementById("LAUNCH");
   SPS = document.getElementById("SPS");
   SKIPB = document.getElementById("SKIPB");
   SKIPF = document.getElementById("SKIPF");
   SKIPM = document.getElementById("SKIPM");

   // Add callback for TiVo selection change
   $('.TIVO').change(function() { tivoChanged(); });

   // Scale down the entire remote by a percentage
   $('#REMOTE').css({ transform: 'scale(.7)' });
      
   // Add callback to all buttons in #REMOTE section
   $("#REMOTE :button").click(function(e) { buttonPress(e); });

   // Retrieve rpc enabled TiVos for TIVOS selection
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

   // Add launch selections
   var apps = {};
   apps["Netflix (html)"] = "x-tivo:netflix:netflix";
   apps["YouTube (html)"] = "x-tivo:web:https://www.youtube.com/tv";
   apps["Amazon"] = "x-tivo:hme:uuid:35FE011C-3850-2228-FBC5-1B9EDBBE5863";
   apps["Hulu Plus"] = "x-tivo:flash:uuid:802897EB-D16B-40C8-AEEF-0CCADB480559";
   apps["AOL On"] = "x-tivo:flash:uuid:EA1DEF9D-D346-4284-91A0-FEA8EAF4CD39";
   apps["Launchpad"] = "x-tivo:flash:uuid:545E064D-C899-407E-9814-69A021D68DAD";
   for (var app in apps) {
      if (apps.hasOwnProperty(app)) {
         var option = document.createElement("option");
         option.text = app;
         option.value = app;
         LAUNCH.appendChild(option);
      }
   }

   // Add sps selections
   var codes = {};
   codes["Quick clear play bar: SPSPS"] = "select play select pause select play";
   codes["Clock: SPS9S"] = "select play select 9 select clear";
   codes["30 sec skip: SPS30S"] = "select play select 3 0 select clear";
   codes["Information: SPSRS"] = "select play select replay select";
   codes["4x FF: SPS88S"] = "select play select 8 8 select clear";
   for (var code in codes) {
      if (codes.hasOwnProperty(code)) {
         var option = document.createElement("option");
         option.text = code;
         option.value = code;
         SPS.appendChild(option);
      }
   }

});

//button callback - RPC keyEventSend call
function buttonPress(button) {
   // list className intentionally made to match corresponding event name
   var event = button.currentTarget.parentElement.className;
   var url = "/rpc?operation=keyEventSend&tivo=" + encodeURIComponent(TIVO.value);
   var json = '{"event":"' + event + '"}';
   url += "&json=" + encodeURIComponent(json);
   $.get(url)
   .error(function(xhr, status) {
      handleError("keyEventSend", xhr, status);
   });
}

function launch() {
   var app = LAUNCH.value;
   var uri = apps[app];
   var url = "/rpc?operation=Navigate&tivo=" + encodeURIComponent(TIVO.value);
   var json = '{"uri":"' + uri + '"}';
   url += "&json=" + encodeURIComponent(json);
   $.get(url)
   .error(function(xhr, status) {
      handleError("Navigate", xhr, status);
   });
}

function sps() {
   var name = SPS.value;
   var sequence = codes[name];
   var url = "/rpc?operation=keyEventMacro&tivo=" + encodeURIComponent(TIVO.value);
   url += "&sequence=" + encodeURIComponent(sequence);
   $.get(url)
   .error(function(xhr, status) {
      handleError("keyEventMacro", xhr, status);
   });
}

function skipf() {
   var offset = SKIPF.value*1000*60.0;
   var url = "/rpc?operation=Position&tivo=" + encodeURIComponent(TIVO.value);
   $.getJSON(url, function(data) {
      if (data.hasOwnProperty("position")) {
         var pos = data.position/1.0;
         offset = pos + offset;
         url = "/rpc?operation=Jump&tivo=" + encodeURIComponent(TIVO.value);
         var json = '{"offset":' + offset + '}';
         url += "&json=" + encodeURIComponent(json);
         $.get(url)
         .error(function(xhr, status) {
            handleError("Jump", xhr, status);
         });
      }
   })
   .error(function(xhr, status) {
      handleError("Position", xhr, status);
   });
}

function skipb() {
   var offset = SKIPB.value*1000*60.0;
   var url = "/rpc?operation=Position&tivo=" + encodeURIComponent(TIVO.value);
   $.getJSON(url, function(data) {
      if (data.hasOwnProperty("position")) {
         var pos = data.position/1.0;
         offset = pos - offset;
         if (offset < 0)
            offset = 0;
         url = "/rpc?operation=Jump&tivo=" + encodeURIComponent(TIVO.value);
         var json = '{"offset":' + offset + '}';
         url += "&json=" + encodeURIComponent(json);
         $.get(url)
         .error(function(xhr, status) {
            handleError("Jump", xhr, status);
         });
      }
   })
   .error(function(xhr, status) {
      handleError("Position", xhr, status);
   });
}

function skipm() {
   var offset = SKIPM.value*1000*60;
   var url = "/rpc?operation=Jump&tivo=" + encodeURIComponent(TIVO.value);
   var json = '{"offset":' + offset + '}';
   url += "&json=" + encodeURIComponent(json);
   $.get(url)
   .error(function(xhr, status) {
      handleError("Jump", xhr, status);
   });
}

function tivoChanged() {
   console.log("tivoChanged");
}

function handleError(prefix, xhr, status) {
   if ( status != "success" ) {
      var error = "ERROR (" + prefix + "):\n";
      error += JSON.stringify(xhr, null, 3);
      alert(error);
   }
}
