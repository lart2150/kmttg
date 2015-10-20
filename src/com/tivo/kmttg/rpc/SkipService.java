package com.tivo.kmttg.rpc;

import java.util.Timer;
import java.util.TimerTask;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
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
      if (config.GUIMODE && ! config.gui.skipServiceMenuItem.isSelected())
         config.gui.skipServiceMenuItem.setSelected(true);
   }
   
   public void stop() {
      if (timer != null) {
         timer.cancel();
         if (r != null)
            r.disconnect();
         print("monitor stopped");
         running = false;
      }
      if (config.GUIMODE && config.gui.skipServiceMenuItem.isSelected())
         config.gui.skipServiceMenuItem.setSelected(false);
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
               // Turn off monitoring if tuned to channel 0
               if (what.has("channelIdentifier")) {
                  JSONObject chan = what.getJSONObject("channelIdentifier");
                  if (chan.has("channelNumber") && chan.getString("channelNumber").equals("0")) {
                     stop();
                     return;
                  }
               }
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
   
   // Look for given offerId in SkipMode ini file
   private JSONObject getOffer(String offerId) {
      try {
         JSONArray entries = SkipMode.getEntries();
         if (entries == null)
            return null;
         for (int i=0; i<entries.length(); ++i) {
            JSONObject json = entries.getJSONObject(i);
            if (json.getString("offerId").equals(offerId))
               return json;
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
               print("Entering SkipMode for: " + SkipMode.title);
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
