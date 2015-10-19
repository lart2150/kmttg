package com.tivo.kmttg.rpc;

import java.util.Timer;
import java.util.TimerTask;

import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class SkipService {
   int interval = 10; // secs between checks
   Timer timer = null;
   Boolean running = false;
   Remote r = null;
   public String tivoName = null;
   
   public SkipService(String tivoName) {
      this.tivoName = tivoName;
   }
   
   public void start() {
      r = new Remote(tivoName);
      if (r.success) {
         print("monitor started");
         running = true;
      } else {
         error("Cannot RPC connect to: " + tivoName);
         running = false;
         return;
      }
      
      // Start timer to monitor playback position
      stop();
      timer = new Timer();
      timer.schedule(
         new TimerTask() {
            @Override
            public void run() {
               monitor();
            }
        }
        ,3000,
        interval*1000
      );
   }
   
   public void stop() {
      if (timer != null) {
         timer.cancel();
         if (r != null)
            r.disconnect();
         print("monitor stopped");
         running = false;
      }
   }
   
   public Boolean isRunning() {
      return running;
   }
   
   private void monitor() {
      if (r==null)
         return;
      JSONObject result = r.Command("whatsOnSearch", new JSONObject());
      if (result == null) {
         // RPC session may be corrupted, start a new connection
         r.disconnect();
         print("Attempting to re-connect to " + tivoName);
         r = new Remote(tivoName);
         if (! r.success) {
            stop();
            return;
         }
      } else {
         try {
            if (result.has("whatsOn")) {
               JSONObject what = result.getJSONArray("whatsOn").getJSONObject(0);
               if (what.has("offerId")) {
                  String offerId = what.getString("offerId");
                  debug.print("offerId=" + offerId + " SkipMode.offerId=" + SkipMode.offerId);
                  if (SkipMode.offerId != null && SkipMode.offerId.equals(offerId)) {
                     if (SkipMode.monitor) {
                        // If SkipMode already monitoring this offerId then nothing to do
                        return;
                     } else {
                        // Find npl entry matching offerId
                        JSONObject entry = getOffer(offerId);
                        // If we have skip data for this entry then start monitoring it
                        monitorEntry(entry);
                     }
                  } else {
                     // Current offerId does not match SkipMode.offerId
                     // Disable monitoring of current SkipMode.offerId
                     if (SkipMode.monitor) {
                        debug.print("SkipService calling SkipMode.disable");
                        SkipMode.disable();
                     }
                     // If we have skip data for current offerId then start monitoring it
                     JSONObject entry = getOffer(offerId);
                     // If we have skip data for this entry then start monitoring it
                     monitorEntry(entry);
                  }
               }
            }
         } catch (JSONException e) {
            error("monitor - " + e.getMessage());
         }
      }
   }
   
   // Look for given offerId on tivoName
   private JSONObject getOffer(String offerId) {
      try {
         JSONObject json = new JSONObject();
         json.put("bodyId", r.bodyId_get());
         json.put("offerId", offerId);
         JSONObject result = r.Command("offerSearch", json);
         if (result != null && result.has("offer")) {
            return result.getJSONArray("offer").getJSONObject(0);
         }
      } catch (JSONException e) {
         error("getOffer - " + e.getMessage());
      }
      return null;
   }
   
   private void monitorEntry(JSONObject entry) {
      if (entry == null)
         return;
      try {
         if (entry.has("contentId")) {
            SkipMode.contentId = entry.getString("contentId");
            SkipMode.offerId = entry.getString("offerId");
            SkipMode.title = entry.getString("title");
            if (entry.has("subtitle"))
               SkipMode.title += " - " + entry.getString("subtitle");
            if (SkipMode.readEntry(entry.getString("contentId"))) {
               if (SkipMode.timer != null) {
                  timer.cancel();
               }
               if (SkipMode.r == null) {
                  SkipMode.r = new Remote(tivoName);
                  if (! r.success) {
                     return;
                  }
               }
               SkipMode.startTimer();
               SkipMode.monitor = true;
               SkipMode.showSkipData();
            }
         }
      } catch (JSONException e) {
         error("monitorEntry - " + e.getMessage());
      }
   }
   
   private void print(String message) {
      log.print("SkipService: " + message);
   }
   
   private void error(String message) {
      log.error("SkipService: " + message);
   }

}
