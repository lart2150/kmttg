(async () => {
   // Info.html document elements
   window.TIVO = document.getElementById("TIVO");
   window.INFO = document.getElementById("INFO");

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
})();

//button callback - RPC call to retrieve information on selected TiVo
const Info = async () => {
   INFO.innerHTML = "";
   const url = new URL('/rpc?operation=SysInfo', window.location);
   url.searchParams.set('tivo', TIVO.value);

   try {
      const response = await fetch(url);

      if (!response.ok) {
         util_handleFetchError("SysInfo", "Error");
      }
      
      await SysInfo(await response.json());
   } catch (e) {
      util_handleFetchError("SysInfo", e);
      return;
   }
}

function tivoChanged() {
   if (INFO.hasOwnProperty(TIVO.value)) {
      // Use cached data if available
      INFO.innerHTML = INFO[TIVO.value];
      return;
   }
   Info();
}

const SysInfo = async (data) => {
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
   const url = new URL('/rpc?operation=WhatsOn', window.location);
   url.searchParams.set('tivo', TIVO.value);

   try {
      const response = await fetch(url);

      if (!response.ok) {
         util_handleFetchError("WhatsOn", "Error");
      }
      
      await WhatsOn(await response.json());
   } catch (e) {
      util_handleFetchError("WhatsOn", e);
      return;
   }
}

const WhatsOn = async (data) => {
   var html = "";
   if (data.hasOwnProperty("whatsOn")) {
      for (const json of data.whatsOn){
         if (json.hasOwnProperty("playbackType")) {
            html += "%25s %s".sprintf("What's On", json.playbackType);
         }
         if (json.playbackType != "idle" && json.hasOwnProperty("channelIdentifier")) {
            if (json.channelIdentifier.hasOwnProperty("channelNumber")) {
               html += " (channel " + json.channelIdentifier.channelNumber + ")";
            }
         }
         html += "\n";
      };
   }
   INFO.innerHTML += "<pre>" + html + "</pre>";  
   const url = new URL('/rpc?operation=TunerInfo', window.location);
   url.searchParams.set('tivo', TIVO.value);
   
   try {
      const response = await fetch(url);

      if (!response.ok) {
         util_handleFetchError("TunerInfo", "Error");
      }
      
      await TunerInfo(await response.json());
   } catch (e) {
      util_handleFetchError("TunerInfo", e);
      return;
   }
}


const TunerInfo = async (data) => {
   var html = "";
   if (data.hasOwnProperty("state")) {
      for (const json of data.state){
         html += "%25s %s\n".sprintf("tunerId", json.tunerId);
         if (json.hasOwnProperty("channel")) {
            html += "%25s %s".sprintf("channelNumber", json.channel.channelNumber);
            if (json.channel.hasOwnProperty("callSign")) {
               html += " (" + json.channel.callSign + ")";
            }
            html += "\n\n";
         }
      };
   }
   INFO.innerHTML += "<pre>" + html + "</pre>";
   // Cache the information
   INFO[TIVO.value] = INFO.innerHTML;
}

// Network Connect button callback
const NetworkConnect = async () => {
   const url = new URL('/rpc?operation=PhoneHome', window.location);
   url.searchParams.set('tivo', TIVO.value);
   
   try {
      const response = await fetch(url);

      if (!response.ok) {
         util_handleFetchError("NetworkConnect", "Error");
         return;
      }
      
      showDialog("NetworkConnect",response,'warning',2)
   } catch (e) {
      util_handleFetchError("NetworkConnect", e);
      return;
   }
}

// Reboot button callback
const Reboot = async () => {
   if (confirm('Reboot ' + TIVO.value + '?')) {
      const url = new URL('/reboot', window.location);
      url.searchParams.set('tivo', TIVO.value);
      
      try {
         const response = await fetch(url);
   
         if (!response.ok) {
            util_handleFetchError("NetworkConnect", "Error");
            return;
         }
         
         showDialog("Reboot",response,'warning',2);
      } catch (e) {
         util_handleFetchError("TunerInfo", e);
         return;
      }
   }
}
