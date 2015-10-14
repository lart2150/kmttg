package com.tivo.kmttg.rpc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;

import javafx.concurrent.Task;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class SkipMode {
   static String ini = config.programDir + File.separator + "SkipMode.ini";
   static Remote r = null;
   static public Boolean monitor = false;
   static Timer timer = null;
   static Stack<Hashtable<String,Long>> skipData = null;
   static long end1 = -1;
   static String offerId = null;
   static String recordingId = null;
   static String contentId = null;
   static String title = "";
   static int monitor_count = 1;
   static int monitor_interval = 10;
   static String tivoName = null;
   
   // Run this in background mode so as not to block GUI
   public static void skipPlay(final String tivoName, final Hashtable<String,String> nplData) {
      SkipMode.tivoName = tivoName;
      Task<Void> task = new Task<Void>() {
         @Override public Void call() {
            if (! nplData.containsKey("contentId")) {
               error("Missing contentId");
               monitor = false;
               return null;
            }
            if (! nplData.containsKey("offerId")) {
               error("Missing offerId");
               monitor = false;
               return null;
            }
            if (! nplData.containsKey("recordingId")) {
               error("Missing recordingId");
               monitor = false;
               return null;
            }
            if (nplData.containsKey("title"))
               SkipMode.title = nplData.get("title");
            SkipMode.contentId = nplData.get("contentId");
            SkipMode.offerId = nplData.get("offerId");
            SkipMode.recordingId = nplData.get("recordingId");
            r = new Remote(tivoName);
            if (r.success) {
               if (readEntry(SkipMode.contentId)) {
                  print("Obtained skip data from file: " + ini);
               } else {
                  print("Attempting to obtain skip data for: " + nplData.get("title"));
                  skipData = getShowPoints(tivoName, SkipMode.contentId);
                  end1 = -1;
               }
               showSkipData();
               if (skipData != null) {
                  // Start playback
                  print("play: " + nplData.get("title"));
                  JSONObject json = new JSONObject();
                  try {
                     json.put("id", recordingId);
                     r.Command("Playback", json);
                  } catch (Exception e) {
                     error("skipPlay - " + e.getMessage());
                     monitor = false;
                     return null;
                  }
                  monitor = true;
                  if (end1 == -1)
                     print("REMINDER: 1st pause will be treated as start of 1st commercial");
                  
                  // Start timer to monitor playback position
                  timer = new Timer();
                  timer.schedule(
                     new TimerTask() {
                        @Override
                        public void run() {
                           skipPlayCheck(tivoName, SkipMode.contentId);
                        }
                    }
                    ,5000,
                    1000
                  );                  
               } else {
                  disable();
               }
            }
            return null;
         }
      };
      new Thread(task).start();
   }
   
   // This procedure called constantly in a timer
   // This monitors playback position and skips commercials
   private static void skipPlayCheck(String tivoName, String contentId) {
      if (monitor && r != null) {
         Boolean skip = true;
         // Call shouldStillMonitor once in a while - this not working
         monitor_count++;
         if (monitor_count > monitor_interval) {
            monitor_count = 1;
            shouldStillMonitor();
         }
         long pos = getPosition();
         debug.print("pos=" + pos + " end1=" + end1);
         if (pos == -1)
            return;
         if (end1 == -1)
            return;
         for (Hashtable<String,Long> h : skipData) {
            if (pos > h.get("start") && pos < h.get("end")) {
               skip = false;
            }
         }
         // If pos < first end point then don't skip
         if (skip) {
            if (pos < skipData.get(0).get("end"))
               skip = false;
         }
         // If pos > last end point then don't skip
         if (skip) {
            if (pos > skipData.get(skipData.size()-1).get("end"))
               skip = false;
         }
         if (skip) {
            long jumpto = getClosest(pos);
            if (jumpto != -1) {
               print("IN COMMERCIAL. JUMPING TO: " + toMinSec(jumpto));
               JSONObject json = new JSONObject();
               try {
                  json.put("offset", jumpto);
               } catch (JSONException e) {
                  error("skipPlayCheck - " + e.getMessage());
               }
               r.Command("Jump", json);
            }
         }
      } else {
         disable();
      }
   }
   
   private static void shouldStillMonitor() {
      if (monitor) {
         Remote rm = new Remote(tivoName);
         if (rm.success) {
            JSONObject result = rm.Command("whatsOnSearch", new JSONObject());
            if (result != null) {
               try {
                  if (result.has("whatsOn")) {
                     JSONObject what = result.getJSONArray("whatsOn").getJSONObject(0);
                     if (what.has("offerId") && what.getString("offerId").equals(offerId)) {
                        // Still playing back show so do nothing
                     } else {
                        disable();
                     }
                  }
               } catch (JSONException e) {
                  error("shouldStillMonitor - " + e.getMessage());
               }
            }
            rm.disconnect();
         }
      } else {
         disable();
      }
   }
   
   // Stop monitoring and reset all variables
   private static void disable() {
      print("DISABLED");
      monitor = false;
      if (timer != null)
         timer.cancel();
      if (r != null)
         r.disconnect();
      r = null;
      end1 = -1;
      offerId = null;
      recordingId = null;
      contentId = null;
      timer = null;
      monitor_count = 1;
      title = "";
   }
   
   // Print current skipData to console
   public static void showSkipData() {
      if (skipData == null) {
         error("showSkipData - no skip data available");
         monitor = false;
         return;
      }
         
      int index = 1;
      for (Hashtable<String,Long> h : skipData) {
         String message = "" + index + ": start=";
         message += toMinSec(h.get("start"));
         message += " end=";
         message += toMinSec(h.get("end"));         
         log.print(message);
         index++;
      }
   }
   
   private static String toMinSec(long msecs) {
      int mins = (int)msecs/1000/60;
      int secs = (int)(msecs/1000 - 60*mins);
      return String.format("%02d:%02d", mins, secs);
   }
   
   // Get the closest non-commercial start point to given pos
   // This will be point to jump to when in a commercial segment
   private static long getClosest(long pos) {
      long closest = -1;
      if (skipData.size() > 1)
         closest = skipData.get(1).get("start");
      long diff = pos;
      for (Hashtable<String,Long> h : skipData) {
         if (pos < h.get("start")) {
            if (h.get("start") - pos < diff) {
               diff = h.get("start") - pos;
               closest = h.get("start");
            }
         }
      }
      return closest;
   }
   
   // RPC query to get skip data based on given contentId
   // Returns null if none found
   private static Stack<Hashtable<String,Long>> getShowPoints(String tivoName, String contentId) {
      Stack<Hashtable<String,Long>> points = null;
      try {
         JSONObject j = new JSONObject();
         j.put("contentId", contentId);
         JSONObject result = r.Command("clipMetadataSearch", j);
         if (result != null && result.has("clipMetadata")) {
            String clipMetadataId = result.getJSONArray("clipMetadata").getJSONObject(0).getString("clipMetadataId");
            j.remove("contentId");
            j.put("clipMetadataId", clipMetadataId);
            result = r.Command("clipMetadataSearch", j);
            if (result != null && result.has("clipMetadata")) {
               JSONObject clipData = result.getJSONArray("clipMetadata").getJSONObject(0);
               points = jsonToShowPoints(clipData);
            }
         }
      } catch (JSONException e) {
         error("getShowPoints - " + e.getMessage());
      }
      return points;
   }
   
   // Convert RPC data to skipData hash
   private static Stack<Hashtable<String,Long>> jsonToShowPoints(JSONObject clipData) {
      Stack<Hashtable<String,Long>> points = new Stack<Hashtable<String,Long>>();
      try {
         if (clipData.has("segment")) {
            JSONArray segment = clipData.getJSONArray("segment");
            long offset = 0;
            for (int i=0; i<segment.length(); ++i) {
               JSONObject seg = segment.getJSONObject(i);
               long start = Long.parseLong(seg.getString("startOffset"));
               if (i == 0) {
                  offset = start;
               }
               start -= offset;
               long end = Long.parseLong(seg.getString("endOffset"));
               end -= offset;
               Hashtable<String,Long> h = new Hashtable<String,Long>();
               h.put("start", start);
               h.put("end", end);
               h.put("delta2", end-start);
               if (i>0)
                  h.put("delta1", h.get("start")-points.get(i-1).get("end"));
               points.push(h);
            }
         } else {
            error("jsonToShowPoints - segment data missing");
         }
      } catch (JSONException e) {
         error("jsonToShowPoints - " + e.getMessage());
      }
      return points;
   }
   
   // RPC query to get current playback position
   // This will also listen for 1st pause press (speed==0) if end1 == -1
   // NOTE: Returns -1 for speed != 100 to avoid any skipping during trick play
   private static long getPosition() {
      if (r==null) return -1;
      JSONObject json = new JSONObject();
      JSONObject reply = r.Command("Position", json);
      if (reply == null) {
         // RPC session may be corrupted, start a new connection
         r.disconnect();
         print("Attempting to re-connect.");
         r = new Remote(tivoName);
         if (! r.success) {
            disable();
            return -1;
         }
         reply = r.Command("Position", json);
      }
      if (reply != null && reply.has("position")) {
         try {
            if (reply.getString("position").equals(reply.getString("end"))) {
               monitor = false;
               return -1;
            }
            if (reply.has("speed")) {
               int speed = reply.getInt("speed");
               if (end1 == -1 && speed == 0) {
                  // 1st pause press so adjust skip data accordingly
                  adjustPoints(reply.getLong("position"));
                  showSkipData();
                  saveEntry();
               }
               if (speed != 100)
                  return -1;
            }
            return reply.getLong("position");
         } catch (JSONException e) {
            error("getPosition - " + e.getMessage());
         }
      }
      return -1;
   }
   
   // Adjust skipData segment 1 end position and
   // all subsequent segment points relative to it
   private static void adjustPoints(long point) {
      print("Adjusting commercial points");
      end1 = point;
      if (skipData.size() > 0) {
         for (int i=0; i<skipData.size()-1; ++i) {
            if (i == 0) {
               skipData.get(i).put("end", point);
               skipData.get(i+1).put("start", point + skipData.get(i+1).get("delta1"));
               skipData.get(i+1).put("end", skipData.get(i+1).get("start") + skipData.get(i+1).get("delta2"));
            } else {
               skipData.get(i+1).put("start", skipData.get(i).get("end") + skipData.get(i+1).get("delta1"));
               skipData.get(i+1).put("end", skipData.get(i+1).get("start") + skipData.get(i+1).get("delta2"));
            }
         }
      }
   }
   
   // Save commercial points for current entry to ini file
   private static void saveEntry() {
      print("Saving SkipMode entry: " + title);
      try {
         String eol = "\r\n";
         BufferedWriter ofp = new BufferedWriter(new FileWriter(ini, true));
         ofp.write("<entry>" + eol);
         ofp.write("contentId=" + contentId + eol);
         ofp.write("title=" + title + eol);
         for (Hashtable<String,Long> entry : skipData) {
            ofp.write(entry.get("start") + " " + entry.get("end") + eol);
         }
         ofp.close();
      } catch (IOException e) {
         error("saveEntry - " + e.getMessage());
      }
   }
   
   // Obtain commercial points for given contentId if it exists
   // Returns true if contentId found, false otherwise
   // NOTE: Reading assumes file entries are structured just like they were originally written
   private static Boolean readEntry(String contentId) {
      if (file.isFile(ini)) {
         try {
            BufferedReader ifp = new BufferedReader(new FileReader(ini));
            String line = null;
            while (( line = ifp.readLine()) != null) {
               if (line.contains("<entry>")) {
                  line = ifp.readLine();
                  if (line.startsWith("contentId")) {
                     String[] l = line.split("=");
                     if (l[1].equals(contentId)) {
                        skipData = new Stack<Hashtable<String,Long>>();
                        while (( line = ifp.readLine()) != null) {
                           if (line.equals("<entry>"))
                              break;
                           if (line.matches("^[0-9]+.*")) {
                              Hashtable<String,Long> h = new Hashtable<String,Long>();
                              l = line.split("\\s+");
                              h.put("start", Long.parseLong(l[0]));
                              h.put("end", Long.parseLong(l[1]));
                              skipData.push(h);
                           }
                        }
                        ifp.close();
                        if (skipData.size() > 0) {
                           end1 = skipData.get(0).get("end"); // Don't need the pause adjustment
                           print("Using existing saved SkipMode entry for: " + title);
                           return true;
                        } else {
                           error("NOTE: Failed to read skip data for: " + title);
                           return false;
                        }
                     }
                  }
               }
            }
            ifp.close();
         } catch (Exception e) {
            error("readEntry - " + e.getMessage());
            log.error(Arrays.toString(e.getStackTrace()));
         }
      }
      return false;
   }
   
   static void print(String message) {
      log.print("SkipMode: " + message);
   }
   
   static void error(String message) {
      log.error("SkipMode: " + message);
   }

}
