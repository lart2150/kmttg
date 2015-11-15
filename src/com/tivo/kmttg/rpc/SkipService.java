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
   private int interval = 10; // secs between checks
   private Timer timer = null;
   private Boolean running = false;
   private Remote r = null;
   private Boolean enabled = true;
   public String tivoName = null;
   
   public SkipService(String tivoName) {
      this.tivoName = tivoName;
   }
   
   public synchronized void start() {
      debug.print("");
      r = new Remote(tivoName);
      if (r.success) {
         print("monitor started for: " + tivoName);
         running = true;
      } else {
         error("Cannot RPC connect to: " + tivoName);
         running = false;
         return;
      }
      
      // Start timer to monitor shows being played
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
         config.gui.skipServiceMenuItem_cb = false;
         config.gui.skipServiceMenuItem.setSelected(true);
         config.gui.skipServiceMenuItem_cb = true;
   }
   
   public synchronized void stop() {
      debug.print("");
      if (timer != null) {
         timer.cancel();
         if (r != null)
            r.disconnect();
         print("monitor stopped");
         running = false;
      }
      SkipMode.monitor = false;
      if (config.GUIMODE && config.gui.skipServiceMenuItem.isSelected())
         config.gui.skipServiceMenuItem.setSelected(false);
   }
   
   public synchronized Boolean isRunning() {
      debug.print("");
      return running;
   }
   
   private synchronized void monitor() {
      debug.print("");
      if (r==null)
         return;
      if (! running)
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
               if (what.has("playbackType") && what.getString("playbackType").equals("liveCache")) {
                  if (what.has("channelIdentifier")) {
                     JSONObject chan = what.getJSONObject("channelIdentifier");
                     if (chan.has("channelNumber")) {                        
                        if (chan.getString("channelNumber").equals("0")) {
                           if (enabled) {
                              // Disable if live TV tuned to channel 0
                              enabled = false;
                              print("monitor disabled for: " + tivoName);
                              return;
                           }
                        }
                        if (chan.getString("channelNumber").equals("1")) {
                           if (! enabled) {
                              // Re-enable if live TV tuned to channel 1
                              enabled = true;
                              print("monitor re-enabled for: " + tivoName);
                           }
                        }
                     }
                  }
               }
               if (what.has("playbackType") && what.getString("playbackType").equals("recording") && what.has("offerId")) {
                  // Recording playing, so see if it can be monitored
                  String recordingId = null;
                  if (what.has("recordingId"))
                     recordingId = what.getString("recordingId");
                  String offerId = what.getString("offerId");
                  debug.print("offerId=" + offerId + " SkipMode.offerId()=" + SkipMode.offerId());
                  if (SkipMode.offerId() != null && SkipMode.offerId().equals(offerId)) {
                     if (SkipMode.isMonitoring()) {
                        // If SkipMode already monitoring this offerId then nothing to do
                        return;
                     } else {
                        // Find npl entry matching offerId
                        JSONObject entry = getOffer(offerId);
                        // If we have skip data for this entry then start monitoring it
                        monitorEntry(entry, recordingId);
                     }
                  } else {
                     // Current offerId does not match SkipMode.offerId
                     // Disable monitoring of current SkipMode.offerId
                     if (SkipMode.isMonitoring()) {
                        debug.print("SkipService calling SkipMode.disable");
                        SkipMode.disable();
                     }
                     if (enabled) {
                        // Get entry associated with offerId
                        JSONObject entry = getOffer(offerId);
                        // If we have skip data for this entry then start monitoring it
                        monitorEntry(entry, recordingId);
                     }
                  }
               }
            }
         } catch (JSONException e) {
            error("monitor - " + e.getMessage());
         }
      }
   }
   
   // Look for given offerId in SkipMode ini file
   private synchronized JSONObject getOffer(String offerId) {
      debug.print("offerId=" + offerId);
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
   
   private synchronized void monitorEntry(JSONObject entry, String recordingId) {
      debug.print("entry=" + entry + " recordingId=" + recordingId);
      if (entry == null) {
         monitorEntry(recordingId);
         return;
      }
      try {
         if (entry.has("contentId")) {
            SkipMode.setMonitor(tivoName, entry.getString("offerId"), entry.getString("contentId"), entry.getString("title"));
            if (SkipMode.readEntry(entry.getString("contentId"))) {
               print("Entering SkipMode for: " + entry.getString("title"));
               SkipMode.showSkipData();
            }
         }
      } catch (JSONException e) {
         error("monitorEntry - " + e.getMessage());
      }
   }
   
   // Based on recordingId try and retrieve skip data from tivo.com
   // and start play in pause mode if found
   private synchronized void monitorEntry(String recordingId) {
      debug.print("recordingId=" + recordingId);
      if (recordingId == null)
         return;
      try {
         JSONObject json = new JSONObject();
         json.put("recordingId", recordingId);
         JSONObject result = r.Command("recordingSearch", json);
         if (result != null && result.has("recording")) {
            JSONObject recording = result.getJSONArray("recording").getJSONObject(0);
            if (recording.has("contentId")) {
               JSONObject j = new JSONObject();
               j.put("contentId", recording.getString("contentId"));
               result = r.Command("clipMetadataSearch", j);
               if (result != null && result.has("clipMetadata")) {
                  String clipMetadataId = result.getJSONArray("clipMetadata").getJSONObject(0).getString("clipMetadataId");
                  j.remove("contentId");
                  j.put("clipMetadataId", clipMetadataId);
                  result = r.Command("clipMetadataSearch", j);
                  if (result != null && result.has("clipMetadata")) {
                     // We have tivo.com data so start monitoring in pause mode
                     JSONObject clipData = result.getJSONArray("clipMetadata").getJSONObject(0);
                     String title = "";
                     if (recording.has("title"))
                        title = recording.getString("title");
                     if (recording.has("subtitle"))
                        title += " - " + recording.getString("title");
                     SkipMode.setMonitor(
                        tivoName, recording.getString("offerId"), recording.getString("contentId"), title
                     );
                     SkipMode.enableMonitor(tivoName, SkipMode.jsonToShowPoints(clipData), -1L);
                  }
               }
            }
         }
      } catch (JSONException e) {
         error("search - " + e.getMessage());
      }
   }
   
   private synchronized void print(String message) {
      log.print("SkipService: " + message);
   }
   
   private synchronized void error(String message) {
      log.error("SkipService: " + message);
   }

}
