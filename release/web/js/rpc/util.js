// Util functions shared by several files
function util_getShowName(json) {
   var title = "";
   if (json.hasOwnProperty("title")) {
      title = json.title;
   }
   if (json.hasOwnProperty("seasonNumber") && json.hasOwnProperty("episodeNum")) {
      title += " [Ep " + json.seasonNumber + "%02d]".sprintf(json.episodeNum[0]);
   }
   if (json.hasOwnProperty("movieYear"))
      title += " [" + json.movieYear + "]";
   if (json.hasOwnProperty("subtitle")) {
      title += " - " + json.subtitle;
   }
   if (json.hasOwnProperty("subscriptionIdentifier")) {
      var a = json.subscriptionIdentifier;
      if (a.length > 0) {
         if (a[0].hasOwnProperty("subscriptionType")) {
            var type = a[0].subscriptionType;
            if (type === "singleTimeChannel" || type === "repeatingTimeChannel")
               title = " Manual:" + title;
         }
      }
   }
   return title;
}

function util_getChannel(json) {
   var channel = "";
   if (json.hasOwnProperty("channel")) {
      var chan = json.channel;
      if (chan.hasOwnProperty("channelNumber")) {
         channel = chan.channelNumber;
      }
      if (chan.hasOwnProperty("callSign")) {
         if (chan.callSign.toLowerCase() === "all channels")
            channel += chan.callSign;
         else
            channel += "=" + chan.callSign;
      } else if (chan.hasOwnProperty("name")) {
         channel += "=" + chan.name;
      }
   }
   return channel;
}

function util_getTimeLong(startTime) {
   var dt = new Date(Date.parse(startTime.replace(/-/g, "/") + " GMT"));
   return dt.getTime()/1000;
}

function util_getTime(startTime) {
   var week = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
   var dt = new Date(Date.parse(startTime.replace(/-/g, "/") + " GMT"));
   var date = "%s %02d/%02d/%02d %02d:%02d".sprintf(
      week[dt.getDay()],
      dt.getMonth()+1,
      dt.getDate(),
      dt.getFullYear()-2000,
      dt.getHours(),
      dt.getMinutes()
   );
   return date;
}

function util_getTimeFromGmt(gmt) {
   var week = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
   var dt = new Date(gmt);
   var date = "%s %02d/%02d/%02d %02d:%02d".sprintf(
      week[dt.getDay()],
      dt.getMonth()+1,
      dt.getDate(),
      dt.getFullYear()-2000,
      dt.getHours(),
      dt.getMinutes()
   );
   return date;
}

function util_secsToHM(secs) {
   var hours = Math.floor(secs/3600);
   var mins = Math.floor((secs - (hours * 3600))/60);
   var seconds = secs - (hours * 3600) - (mins * 60);
   if (seconds > 30)
      mins += 1;
   if (mins == 60) {
      hours += 1;
      mins = 0;
   }
   return "%d:%02d".sprintf(hours,mins);
}

function util_handleError(prefix, xhr, status) {
   if ( status != "success" ) {
      var message = xhr;
      if ( xhr.hasOwnProperty("responseText") )
         message = xhr.responseText;
      showDialog(prefix,message,'error');
   }
}