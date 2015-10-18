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
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class SkipMode {
   static String ini = config.programDir + File.separator + "SkipMode.ini";
   static Remote r = null;
   static public Boolean monitor = false;
   static Timer timer = null;
   static Stack<Hashtable<String,Long>> skipData = null;
   static Stack<Hashtable<String,Long>> skipData_orig = null;
   static long offset = -1;
   static long end1 = -1;
   static String offerId = null;
   static String recordingId = null;
   static String contentId = null;
   static String title = "";
   static int monitor_count = 1;
   static int monitor_interval = 6;
   static String tivoName = null;
   static int run_count = 1;
   
   public static Boolean fileExists() {
      return file.isFile(ini);
   }
   
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
                  skipData_orig = getShowPoints(r, tivoName, SkipMode.contentId);
                  skipData = hashCopy(skipData_orig);
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
                  if (end1 == -1) {
                     print("REMINDER: 1st pause press will be saved as exact start of 1st commercial");
                     print("REMINDER: Use 'x' bindkey to jump to close to 1st commercial");
                  }
                  
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
         // If pos >= last end point then don't skip
         if (skip) {
            if (pos >= skipData.get(skipData.size()-1).get("end"))
               skip = false;
         }
         if (skip) {
            long jumpto = getClosest(pos);
            if (jumpto != -1) {
               print("IN COMMERCIAL. JUMPING TO: " + toMinSec(jumpto));
               jumpTo(jumpto);
            }
         }
      } else {
         disable();
      }
   }
   
   // Jump to given position in playback
   private static void jumpTo(long position) {
      JSONObject json = new JSONObject();
      try {
         json.put("offset", position);
      } catch (JSONException e) {
         error("jumpTo - " + e.getMessage());
      }
      r.Command("Jump", json);      
   }
   
   // Jump to end of 1st show segment
   public static void jumpTo1st() {
      if (r != null && skipData != null && monitor) {
         long pos = skipData.get(0).get("end");
         print("Jumping to: " + toMinSec(pos));
         jumpTo(pos);
      }
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
                  saveEntry(contentId, offset, title, skipData_orig);
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
   
   // RPC query to determine if show being monitored is still being played
   // If whatsOn query does not have offerId => show no longer being played on TiVo
   private static void shouldStillMonitor() {
      if (r==null)
         return;
      if (monitor) {
         JSONObject result = r.Command("whatsOnSearch", new JSONObject());
         if (result == null) {
            // RPC session may be corrupted, start a new connection
            r.disconnect();
            print("Attempting to re-connect.");
            r = new Remote(tivoName);
            if (! r.success) {
               disable();
               return;
            }
         } else {
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
      offset = -1;
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
   // Return value of -1 => no change wanted
   private static long getClosest(long pos) {
      long closest = -1;
      // If current pos is within any start-end range then no skip necessary
      for (Hashtable<String,Long> h : skipData) {
         if (pos >= h.get("start") && pos <= h.get("end"))
            return -1;
      }
      
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
   private static Stack<Hashtable<String,Long>> getShowPoints(Remote r, String tivoName, String contentId) {
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
         } else {
            log.warn("SkipMode: No skip data available for this show");
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
   
   // Adjust skipData segment 1 end position and
   // all subsequent segment points relative to it
   private static void adjustPoints(long point) {
      print("Adjusting commercial points");
      end1 = point;
      if (skipData.size() > 0) {
         offset = point - skipData.get(0).get("end");
         for (int i=0; i<skipData.size(); ++i) {
            if (i == 0) {
               skipData.get(i).put("end", point);
            } else {
               skipData.get(i).put("start", skipData.get(i).get("start") + offset);
               skipData.get(i).put("end", skipData.get(i).get("end") + offset);
            }
         }
      }
   }
   
   // Save commercial points for current entry to ini file
   private static void saveEntry(String contentId, long offset, String title, Stack<Hashtable<String,Long>> data) {
      print("Saving SkipMode entry: " + title);
      try {
         String eol = "\r\n";
         BufferedWriter ofp = new BufferedWriter(new FileWriter(ini, true));
         ofp.write("<entry>" + eol);
         ofp.write("contentId=" + contentId + eol);
         ofp.write("offset=" + offset + eol);
         ofp.write("title=" + title + eol);
         for (Hashtable<String,Long> entry : data) {
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
                        skipData_orig = new Stack<Hashtable<String,Long>>();
                        while (( line = ifp.readLine()) != null) {
                           if (line.equals("<entry>"))
                              break;
                           if (line.startsWith("offset")) {
                              l = line.split("=");
                              offset = Long.parseLong(l[1]);
                           }
                           if (line.matches("^[0-9]+.*")) {
                              Hashtable<String,Long> h = new Hashtable<String,Long>();
                              l = line.split("\\s+");
                              h.put("start", Long.parseLong(l[0]));
                              h.put("end", Long.parseLong(l[1]));
                              skipData_orig.push(h);
                           }
                        }
                        ifp.close();
                        skipData = hashCopy(skipData_orig);
                        if (skipData.size() > 0) {
                           adjustPoints(skipData_orig.get(0).get("end") + offset);
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
   
   // Remove any entries matching given contentId from ini file
   public static Boolean removeEntry(String contentId) {
      if (file.isFile(ini)) {
         try {
            Boolean itemRemoved = false;
            Stack<String> lines = new Stack<String>();
            BufferedReader ifp = new BufferedReader(new FileReader(ini));
            String line = null;
            Boolean include = true;
            while (( line = ifp.readLine()) != null) {
               if (line.contains("<entry>")) {
                  include = true;
                  String nextline = ifp.readLine();
                  String[] l = nextline.split("=");
                  if (l[1].equals(contentId)) {
                     include = false;
                     itemRemoved = true;
                  }
                  if (include) {
                     lines.push(line);
                     lines.push(nextline);
                  }
               } else {
                  if (include)
                     lines.push(line);
               }
            }
            ifp.close();
            String eol = "\r\n";
            BufferedWriter ofp = new BufferedWriter(new FileWriter(ini));
            for (String l : lines) {
               ofp.write(l + eol);
            }
            ofp.close();
            if (itemRemoved)
               print("Removed entry: " + contentId);
            else
               print("No entry found for: " + contentId);
            return itemRemoved;
         } catch (Exception e) {
            error("removeEntry - " + e.getMessage());
         }
      }
      return false;
   }
   
   // Change offset for given contentId
   public static Boolean changeEntry(String contentId, String offset) {
      if (file.isFile(ini)) {
         try {
            Boolean itemChanged = false;
            Stack<String> lines = new Stack<String>();
            BufferedReader ifp = new BufferedReader(new FileReader(ini));
            String line = null;
            while (( line = ifp.readLine()) != null) {
               if (line.contains("contentId")) {
                  Boolean changed = false;
                  String[] l = line.split("=");
                  if (l[1].equals(contentId)) {
                     itemChanged = true;
                     changed = true;
                  }
                  String nextline = ifp.readLine();
                  l = nextline.split("=");
                  lines.push(line);
                  if (changed)
                     lines.push(l[0] + "=" + offset);
                  else
                     lines.push(nextline);
               } else {
                  lines.push(line);
               }
            }
            ifp.close();
            String eol = "\r\n";
            BufferedWriter ofp = new BufferedWriter(new FileWriter(ini));
            for (String l : lines) {
               ofp.write(l + eol);
            }
            ofp.close();
            if (itemChanged)
               print("contentId '" + contentId + "' offset updated to: " + offset);
            else
               print("contentId '" + contentId + "' not updated.");
            return itemChanged;
         } catch (Exception e) {
            error("removeEntry - " + e.getMessage());
         }
      }
      return false;
   }
   
   // Return entries for use by SkipDialog table
   public static JSONArray getEntries() {
      JSONArray entries = new JSONArray();
      if (file.isFile(ini)) {
         try {
            BufferedReader ifp = new BufferedReader(new FileReader(ini));
            String line=null, contentId="", title="", offset="";
            while (( line = ifp.readLine()) != null) {
               if (line.contains("contentId="))
                  contentId = line.replaceFirst("contentId=", "");
               if (line.contains("offset="))
                  offset = line.replaceFirst("offset=", "");
               if (line.contains("title=")) {
                  title = line.replaceFirst("title=", "");
                  JSONObject json = new JSONObject();
                  json.put("contentId", contentId);
                  json.put("offset", offset);
                  json.put("title", title);
                  entries.put(json);
               }
            }
            ifp.close();
         } catch (Exception e) {
            error("getEntries - " + e.getMessage());
         }
      }
      return entries;
   }
   
   public static void autoDetect(String tivoName, Hashtable<String,String> entry) {
      if( readEntry(entry.get("contentId")) ) {
         log.warn("SkipMode: Already have saved SkipMode entry for: " + entry.get("title"));
         return;
      }
      Remote r2 = new Remote(tivoName);
      if (r2.success) {
         Stack<Hashtable<String,Long>> points = getShowPoints(r2, tivoName, entry.get("contentId"));
         r2.disconnect();
         if (points != null) {
            float firstEnd = points.get(0).get("end");
            float duration = Float.parseFloat(entry.get("duration"));
            float size = Float.parseFloat(entry.get("size"));
            long limit = (long)((firstEnd+30000) * size/duration);
            String mpegFile = config.mpegDir + File.separator + "SkipModeTempFile" + run_count + "_" + tivoName + ".mpg";
            if (file.isFile(mpegFile))
               file.delete(mpegFile);
            jobData job = new jobData();
            job.source       = entry.get("url_TiVoVideoDetails");
            job.tivoName     = tivoName;
            job.type         = "tdownload_decrypt";
            job.name         = "java";
            job.limit        = limit;
            job.SkipPoint    = "" + firstEnd;
            job.contentId    = entry.get("contentId");
            job.title        = entry.get("title");
            job.mpegFile     = mpegFile;
            job.mpegFile_cut = string.replaceSuffix(job.mpegFile, "_cut.mpg");
            job.startFile    = job.tivoFile;
            job.url          = entry.get("url");
            job.tivoFileSize = Long.parseLong(entry.get("size"));
            print("Launching jobs to try and obtain 1st commercial point close to: " + toMinSec((long)firstEnd));
            jobMonitor.submitNewJob(job);
            run_count++;
         }
      }
   }
   
   public static void saveWithOffset(String tivoName, String contentId, String title, long offset) {
      Remote r2 = new Remote(tivoName);
      if (r2.success) {
         Stack<Hashtable<String,Long>>points = getShowPoints(r2, tivoName, contentId);
         r2.disconnect();
         if (points != null) {
            SkipMode.title = title;
            saveEntry(contentId, offset - points.get(0).get("end"), title, points);
            print("1st commercial point saved as: " + toMinSec(offset) +
               " (orig point=" + toMinSec(points.get(0).get("end")) + ")");
         }
      }      
   }
   
   private static Stack<Hashtable<String,Long>> hashCopy(Stack<Hashtable<String,Long>> orig) {
      Stack<Hashtable<String,Long>> copy = new Stack<Hashtable<String,Long>>();
      for (Hashtable<String,Long> h : orig) {
         Hashtable<String,Long> h_copy = new Hashtable<String,Long>(h);
         copy.push(h_copy);
      }
      return copy;
   }
   
   private static void print(String message) {
      log.print("SkipMode: " + message);
   }
   
   private static void error(String message) {
      log.error("SkipMode: " + message);
   }

}
