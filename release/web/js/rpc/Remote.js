// Remote.html document elements
TIVO = document.getElementById("TIVO");
LAUNCH = document.getElementById("LAUNCH");
SPS = document.getElementById("SPS");
SKIPB = document.getElementById("SKIPB");
SKIPF = document.getElementById("SKIPF");
SKIPM = document.getElementById("SKIPM");

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

$(document).ready(function() {
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
      util_handleError("/getRpcTivos", xhr, status);
   });
   
   // Retrieve launch selections
   $.getJSON("rc_apps.json", function(data) {
      $.each(data, function( i, obj ) {
         if(obj["disabled"] == undefined || !obj.disabled) {
	         var option = document.createElement("option");
	         if(obj["channel"] != undefined) {
	         	option.text = obj.name +" (channel "+obj.channel+")";
	         } else {
		        option.text = obj.name;
	         }
	         option.value = obj.uri;
	         LAUNCH.appendChild(option);
         }
      });
   })
   .error(function(xhr, status) {
      util_handleError("/js/rc_apps.json", xhr, status);
   });
   
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
      util_handleError("keyEventSend", xhr, status);
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
      util_handleError("Navigate", xhr, status);
   });
}

function sps() {
   var name = SPS.value;
   var sequence = codes[name];
   var url = "/rpc?operation=keyEventMacro&tivo=" + encodeURIComponent(TIVO.value);
   url += "&sequence=" + encodeURIComponent(sequence);
   $.get(url)
   .error(function(xhr, status) {
      util_handleError("keyEventMacro", xhr, status);
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
            util_handleError("Jump", xhr, status);
         });
      }
   })
   .error(function(xhr, status) {
      util_handleError("Position", xhr, status);
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
            util_handleError("Jump", xhr, status);
         });
      }
   })
   .error(function(xhr, status) {
      util_handleError("Position", xhr, status);
   });
}

function skipm() {
   var offset = SKIPM.value*1000*60;
   var url = "/rpc?operation=Jump&tivo=" + encodeURIComponent(TIVO.value);
   var json = '{"offset":' + offset + '}';
   url += "&json=" + encodeURIComponent(json);
   $.get(url)
   .error(function(xhr, status) {
      util_handleError("Jump", xhr, status);
   });
}

function tivoChanged() {
   console.log("tivoChanged");
}