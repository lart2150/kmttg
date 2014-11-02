$(document).ready(function() {
   // Info.html document elements
   TIVO = document.getElementById("TIVO");
   INFO = document.getElementById("INFO");

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
});

//button callback - RPC call to retrieve information on selected TiVo
function Info() {
   INFO.innerHTML = "";
   var url = "/rpc?operation=SysInfo&tivo=" + encodeURIComponent(TIVO.value);
   $.getJSON(url, SysInfo)
   .error(function(xhr, status) {
      util_handleError("SysInfo", xhr, status);
   });
}

function tivoChanged() {
   if (INFO.hasOwnProperty(TIVO.value)) {
      // Use cached data if available
      INFO.innerHTML = INFO[TIVO.value];
      return;
   }
   Info();
}

function SysInfo(data) {
   var html = "";
   if (data.hasOwnProperty("bodyConfig")) {
      var json = data.bodyConfig[0];
      if (json.hasOwnProperty("userDiskSize") && json.hasOwnProperty("userDiskUsed")) {
         var sizeGB = json.userDiskSize/(1024*1024);
         var pct = 100.0*json.userDiskUsed/json.userDiskSize;
         var pct_string = "" + json.userDiskUsed + " (" + pct.toFixed(2) + "%)";
         var size_string = "" + json.userDiskSize + " (" + sizeGB.toFixed(2) + " GB)";
         json.userDiskSize = size_string;
         json.userDiskUsed = pct_string;
         if (json.hasOwnProperty("bodyId")) {
            html += "%25s %s\n".sprintf("tsn", json.bodyId.replace("tsn:", ""));
         }
         var fields = ["softwareVersion", "userDiskSize", "userDiskUsed", "parentalControlsState"];
         for (var i=0; i<fields.length; ++i) {
            if (json.hasOwnProperty(fields[i]))
               html += "%25s %s\n".sprintf(fields[i], json[fields[i]]);
         }
      }
   }
   INFO.innerHTML += "<pre>" + html + "</pre>";  
   var url = "/rpc?operation=WhatsOn&tivo=" + encodeURIComponent(TIVO.value);
   $.getJSON(url, WhatsOn)
   .error(function(xhr, status) {
      util_handleError("WhatsOn", xhr, status);
   });
}

function WhatsOn(data) {
   var html = "";
   if (data.hasOwnProperty("whatsOn")) {
      $.each(data.whatsOn, function(i, json) {
         if (json.hasOwnProperty("playbackType")) {
            html += "%25s %s".sprintf("What's On", json.playbackType);
         }
         if (json.playbackType != "idle" && json.hasOwnProperty("channelIdentifier")) {
            if (json.channelIdentifier.hasOwnProperty("channelNumber")) {
               html += " (channel " + json.channelIdentifier.channelNumber + ")";
            }
         }
         html += "\n";
      });
   }
   INFO.innerHTML += "<pre>" + html + "</pre>";  
   var url = "/rpc?operation=TunerInfo&tivo=" + encodeURIComponent(TIVO.value);
   $.getJSON(url, TunerInfo)
   .error(function(xhr, status) {
      util_handleError("TunerInfo", xhr, status);
   });
}

function TunerInfo(data) {
   var html = "";
   if (data.hasOwnProperty("state")) {
      $.each(data.state, function(i, json) {
         html += "%25s %s\n".sprintf("tunerId", json.tunerId);
         if (json.hasOwnProperty("channel")) {
            html += "%25s %s".sprintf("channelNumber", json.channel.channelNumber);
            if (json.channel.hasOwnProperty("callSign")) {
               html += " (" + json.channel.callSign + ")";
            }
            html += "\n\n";
         }
      });
   }
   INFO.innerHTML += "<pre>" + html + "</pre>";
   // Cache the information
   INFO[TIVO.value] = INFO.innerHTML;
}

// Network Connect button callback
function NetworkConnect() {
   var url = "/rpc?operation=PhoneHome&tivo=" + encodeURIComponent(TIVO.value);
   $.get(url, function(response) {
      showDialog("Network Connect",response,'warning',2);
   })
   .error(function(xhr, status) {
      util_handleError("NetworkConnect", xhr, status);
   });
}

// Reboot button callback
function Reboot() {
   if (confirm('Reboot ' + TIVO.value + '?')) {
      var url = "/reboot?tivo=" + encodeURIComponent(TIVO.value);
      $.get(url, function(response) {
         showDialog("Reboot",response,'warning',2);
      })
      .error(function(xhr, status) {
         util_handleError("Reboot", xhr, status);
      });
   }
}
