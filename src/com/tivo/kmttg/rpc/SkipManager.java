/*
 * Copyright 2008-Present Kevin Moye <moyekj@yahoo.com>.
 *
 * This file is part of kmttg package.
 *
 * kmttg is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this project.  If not, see <http://www.gnu.org/licenses/>.
 */
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

import javafx.application.Platform;
import javafx.concurrent.Task;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.tivoTab;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.parseNPL;

public class SkipManager {
   private static String ini = config.programDir + File.separator + "AutoSkip.ini";
   private static Hashtable<String,AutoSkip> instances = new Hashtable<String,AutoSkip>();
   private static Hashtable<String,SkipService> serviceInstances = new Hashtable<String,SkipService>();
   
   public static synchronized String iniFile() {
      return ini;
   }
   
   public static synchronized void disableMonitor(String tivoName) {
      debug.print("tivoName=" + tivoName);
      if ( instances.containsKey(tivoName)) {
         instances.get(tivoName).monitor = false;
      }            
   }
   
   public static synchronized Boolean skipEnabled() {
      debug.print("");
      // At least 1 TiVo needs to be RPC enabled
      return config.autoskip_enabled == 1 && config.rpcEnabled();
   }
   
   public static synchronized void addSkip(String tivoName) {
      debug.print("tivoName=" + tivoName);
      if ( ! instances.containsKey(tivoName)) {
         instances.put(tivoName, new AutoSkip());
      }      
   }
   
   public static synchronized AutoSkip getSkip(String tivoName) {
      debug.print("tivoName=" + tivoName);
      if (instances.containsKey(tivoName))
         return instances.get(tivoName);
      else
         return null;
   }
   
   public static synchronized void startService(String tivoName) {
      debug.print("tivoName=" + tivoName);
      if ( ! serviceInstances.containsKey(tivoName)) {
         serviceInstances.put(tivoName, new SkipService(tivoName));
         serviceInstances.get(tivoName).start();
      }
   }
   
   public static synchronized void stopService(String tivoName) {
      debug.print("tivoName=" + tivoName);
      if (serviceInstances.containsKey(tivoName)) {
         serviceInstances.get(tivoName).stop();
         serviceInstances.remove(tivoName);
      }
   }
   
   /*public static synchronized SkipService getService(String tivoName) {
      debug.print("tivoName=" + tivoName);
      if (instances.containsKey(tivoName))
         return serviceInstances.get(tivoName);
      else
         return null;
   }*/
   
   public static synchronized Boolean isMonitoring(String tivoName) {
      debug.print("tivoName=" + tivoName);
      if (instances.containsKey(tivoName)) {
         return instances.get(tivoName).isMonitoring();
      }
      return false;
   }
   
   public static synchronized void disable(String tivoName) {
      debug.print("tivoName=" + tivoName);
      if (instances.containsKey(tivoName)) {
         instances.get(tivoName).disable();
         instances.remove(tivoName);
      }
   }
   
   public static synchronized void skipPlay(String tivoName, Hashtable<String,String> entry) {
      debug.print("tivoName=" + tivoName + " entry=" + entry);
      addSkip(tivoName);
      instances.get(tivoName).skipPlay(tivoName, entry);
   }
   
   // Save commercial points for current entry to ini file
   public static synchronized void saveEntry(final String contentId, String offerId, long offset,
         String title, final String tivoName, Stack<Hashtable<String,Long>> data) {
      debug.print("contentId=" + contentId + " offerId=" + offerId + " offset=" + offset);
      log.print("Saving AutoSkip entry: " + title);
      try {
         String eol = "\r\n";
         BufferedWriter ofp = new BufferedWriter(new FileWriter(ini, true));
         ofp.write("<entry>" + eol);
         ofp.write("contentId=" + contentId + eol);
         ofp.write("offerId=" + offerId + eol);
         ofp.write("offset=" + offset + eol);
         ofp.write("tivoName=" + tivoName + eol);
         ofp.write("title=" + title + eol);
         for (Hashtable<String,Long> entry : data) {
            ofp.write(entry.get("start") + " " + entry.get("end") + eol);
         }
         ofp.close();
         if (config.GUIMODE) {
            Platform.runLater(new Runnable() {
               @Override public void run() {
                  config.gui.getTab(tivoName).getTable().updateSkipStatus(contentId);
               }
            });
         }
      } catch (IOException e) {
         log.error("saveEntry - " + e.getMessage());
      }
   }
   
   public static synchronized Boolean hasEntry(String contentId) {
      debug.print("contentId=" + contentId);
      if (! skipEnabled() )
         return false;
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
                        ifp.close();
                        return true;
                     }
                  }
               }
            }
            ifp.close();
         } catch (Exception e) {
            log.error("readEntry - " + e.getMessage());
            log.error(Arrays.toString(e.getStackTrace()));
         }
      }
      return false;
   }
   
   // Remove any entries matching given contentId from ini file
   public static synchronized Boolean removeEntry(final String contentId) {
      debug.print("contentId=" + contentId);
      if (file.isFile(ini)) {
         try {
            Boolean itemRemoved = false;
            Stack<String> lines = new Stack<String>();
            BufferedReader ifp = new BufferedReader(new FileReader(ini));
            String line = null;
            Boolean include = true;
            String tivoName = null;
            String title = null;
            while (( line = ifp.readLine()) != null) {
               if (line.contains("<entry>")) {
                  include = true;
                  String nextline = ifp.readLine();
                  String[] l = nextline.split("=");
                  if (l[1].equals(contentId)) {
                     include = false;
                     itemRemoved = true;
                     ifp.readLine(); // offerId
                     ifp.readLine(); // offset
                     tivoName = ifp.readLine().split("=")[1];
                     title = ifp.readLine().split("=")[1];
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
            if (itemRemoved) {
               log.print("Removed entry for " + tivoName + ": " + title);
               if (tivoName != null && config.GUIMODE) {
                  // Remove asterisk from associated table
                  final String final_tivoName = tivoName;
                  Platform.runLater(new Runnable() {
                     @Override public void run() {
                        tivoTab t = config.gui.getTab(final_tivoName);
                        if (t != null) {
                           t.getTable().updateSkipStatus(contentId);
                        }
                     }
                  });
               }
            }
            else
               log.print("No entry found for: " + contentId);
            return itemRemoved;
         } catch (Exception e) {
            log.error("removeEntry - " + e.getMessage());
         }
      }
      return false;
   }
   
   // Change offset for given contentId
   public static synchronized Boolean changeEntry(String contentId, String offset, String title) {
      debug.print("contentId=" + contentId + " offset=" + offset + " title=" + title);
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
                  String offerId = ifp.readLine(); // offerId
                  String nextline = ifp.readLine(); // offset
                  l = nextline.split("=");
                  lines.push(line);
                  lines.push(offerId);
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
               log.print("'" + title + "' offset updated to: " + offset);
            else
               log.print("'" + title + "' not updated.");
            return itemChanged;
         } catch (Exception e) {
            log.error("removeEntry - " + e.getMessage());
         }
      }
      return false;
   }
   
   public static Stack<Hashtable<String,Long>> getEntry(String contentId) {
      debug.print("contentId=" + contentId);
      Stack<Hashtable<String,Long>> entry = new Stack<Hashtable<String,Long>>();
      if (file.isFile(SkipManager.iniFile())) {
         try {
            BufferedReader ifp = new BufferedReader(new FileReader(SkipManager.iniFile()));
            String line = null;
            while (( line = ifp.readLine()) != null) {
               if (line.contains("<entry>")) {
                  line = ifp.readLine();
                  if (line.startsWith("contentId")) {
                     String[] l = line.split("=");
                     if (l[1].equals(contentId)) {
                        while (( line = ifp.readLine()) != null) {
                           if (line.equals("<entry>"))
                              break;
                           if (line.matches("^[0-9]+.*")) {
                              Hashtable<String,Long> h = new Hashtable<String,Long>();
                              l = line.split("\\s+");
                              h.put("start", Long.parseLong(l[0]));
                              h.put("end", Long.parseLong(l[1]));
                              entry.push(h);
                           }
                        }
                        break;
                     }
                  }
               }
            }
            ifp.close();
         } catch (Exception e) {
            log.error("SkipManager getEntry - " + e.getMessage());
            log.error(Arrays.toString(e.getStackTrace()));
         }
      }
      return entry;
   }

   
   // Return entries for use by SkipDialog table
   public static synchronized JSONArray getEntries() {
      debug.print("");
      JSONArray entries = new JSONArray();
      if (file.isFile(ini)) {
         try {
            BufferedReader ifp = new BufferedReader(new FileReader(ini));
            String line=null, contentId="", title="", offset="", offerId="", tivoName="";
            JSONArray cuts = new JSONArray();
            while (( line = ifp.readLine()) != null) {
               if (line.contains("<entry>")) {
                  if (cuts.length() > 0) {
                     JSONObject json = new JSONObject();
                     json.put("contentId", contentId);
                     json.put("offerId", offerId);
                     json.put("offset", offset);
                     json.put("tivoName", tivoName);
                     json.put("title", title);
                     json.put("ad1", "" + cuts.getJSONObject(0).get("end"));
                     json.put("cuts", cuts);
                     entries.put(json);
                  }
                  cuts = new JSONArray();
               }
               if (line.contains("contentId="))
                  contentId = line.replaceFirst("contentId=", "");
               if (line.contains("offerId="))
                  offerId = line.replaceFirst("offerId=", "");
               if (line.contains("offset="))
                  offset = line.replaceFirst("offset=", "");
               if (line.contains("tivoName="))
                  tivoName = line.replaceFirst("tivoName=", "");
               if (line.contains("title="))
                  title = line.replaceFirst("title=", "");
               if (line.matches("^[0-9]+.*")) {
                  String[] l = line.split("\\s+");
                  JSONObject j = new JSONObject();
                  j.put("start", Long.parseLong(l[0]));
                  j.put("end", Long.parseLong(l[1]));
                  cuts.put(j);
               }
            } // while
            if (cuts.length() > 0) {
               JSONObject json = new JSONObject();
               json.put("contentId", contentId);
               json.put("offerId", offerId);
               json.put("offset", offset);
               json.put("tivoName", tivoName);
               json.put("title", title);
               json.put("ad1", "" + cuts.getJSONObject(0).get("end"));
               json.put("cuts", cuts);
               entries.put(json);
            }
            ifp.close();
         } catch (Exception e) {
            log.error("getEntries - " + e.getMessage());
         }
      }
      return entries;
   }
   
   // Remove AutoSkip entries that no longer have corresponding NPL entries
   public static synchronized void pruneEntries(String tivoName, Stack<Hashtable<String,String>> nplEntries) {
      debug.print("tivoName=" + tivoName + " nplEntries=" + nplEntries);
      if (nplEntries == null)
         return;
      if (nplEntries.size() == 0)
         return;
      
      try {
         int count = 0;
         JSONArray skipEntries = getEntries();
         for (int i=0; i<skipEntries.length(); ++i) {
            JSONObject json = skipEntries.getJSONObject(i);
            if (json.getString("tivoName").equals(tivoName)) {
               Boolean exists = false;
               for (Hashtable<String,String> nplEntry : nplEntries) {
                  if (nplEntry.containsKey("offerId")) {
                     if (nplEntry.get("offerId").equals(json.getString("offerId")))
                        exists = true;
                  }
               }
               if (! exists) {
                  removeEntry(json.getString("contentId"));
                  count++;
               }
            }
         }
         if (count == 0)
            log.warn("No entries found to prune");
      } catch (JSONException e) {
         log.error("pruneEntries - " + e.getMessage());
      }
   }
   
   private static void visualDetect(Boolean first_try, String tivoName, Stack<Hashtable<String,String>> stack) {
      config.visualDetect_running = true;
      for (Hashtable<String,String> data : stack) {
         log.warn(
            tivoName + ": Scanning SkipMode cut points for '" +
            data.get("title") + "'"
         );
         String recordingId = data.get("recordingId");
         String contentId = data.get("contentId");
         String clipMetadataId = data.get("clipMetadataId");
         if (hasEntry(contentId))
            removeEntry(contentId);
         Long point = -1L, lastPoint = -1L;
         int delta = 5000;
         int sleep_time = 900;
         Stack<Long> points = new Stack<Long>();
         Remote r = new Remote(tivoName);
         if (r.success) {
            try {
               
               // Obtain clipData
               JSONObject clipData = r.getClipData(contentId, clipMetadataId);
               if (clipData == null) {
                  r.disconnect();
                  log.error("Failed to retrieve SkipMode data for contentId: " + contentId);
                  continue;
               }
               // This Stack holds the show segment lengths to compute stop points with
               Stack<Long> lengths = getSegmentLengths(clipData);
               
               // Sequence of button simulated button presses to discover show start points
               long starting = 0;
               if (data.containsKey("TimeOffset"))
                  starting = Long.parseLong(data.get("TimeOffset"))*1000;
               long end = 60*60*5*1000;
               if (data.containsKey("duration"))
                  end = Long.parseLong(data.get("duration"));
               
               // Start play
               JSONObject j = new JSONObject();
               j.put("id", recordingId);
               JSONObject result = r.Command("Playback", j);
               if (result == null) {
                  continue;
               }
               Thread.sleep(sleep_time*3);
                                    
               // Jump to end
               end -= 5000;
               JSONObject json = new JSONObject();
               json.put("offset", end);
               result = r.Command("Jump", json);
               Thread.sleep(sleep_time);
               json.remove("offset");
               
               // Reverse a little then pause just in case beyond end of show
               json.put("event", "reverse");
               r.Command("keyEventSend", json);
               Thread.sleep(sleep_time);
               json.put("event", "play");
               r.Command("keyEventSend", json);
               if (result != null) {
                  Boolean go = true;
                  while (go) {
                     // Send Channel down press and collect time information
                     json.put("event", "channelDown");
                     r.Command("keyEventSend", json);
                     json.put("event", "pause");
                     r.Command("keyEventSend", json);
                     
                     // Get position
                     Thread.sleep(sleep_time);
                     result = r.Command("Position", new JSONObject());
                     if (result != null && result.has("position"))
                        point = result.getLong("position");
                     
                     if (Math.abs(point-lastPoint) > delta)
                        points.push(point);
                     else
                        go = false;
                     lastPoint = point;
                     Thread.sleep(sleep_time);
                     json.remove("event");
                     json.put("offset", point-3000);
                     r.Command("Jump", json);
                     Thread.sleep(sleep_time);
                     json.remove("offset");
                     json.put("event", "play");
                     r.Command("keyEventSend", json);
                     Thread.sleep(sleep_time);
                  } // while
                  
                  // Pause and jump back to starting position
                  Thread.sleep(sleep_time);
                  json.put("event", "pause");
                  r.Command("keyEventSend", json);
                  Thread.sleep(sleep_time);
                  json.remove("event");
                  json.put("offset", starting);
                  result = r.Command("Jump", json);
                  
                  // liveTv button press
                  Thread.sleep(sleep_time);
                  json.remove("offset");
                  json.put("event", "liveTv");
                  result = r.Command("keyEventSend", json);
                  
                  // Reset bookmark position (starting==0 doesn't always work with Jump)
                  log.print("(Setting pause point=" + starting + ")");
                  json = new JSONObject();
                  json.put("bodyId", r.bodyId_get());
                  json.put("recordingId", recordingId);
                  json.put("bookmarkPosition", starting);
                  r.Command("recordingUpdate", json);
               }
               
               points = reverseStack(points);
               Stack<Hashtable<String,Long>> cuts = new Stack<Hashtable<String,Long>>();
               // If lengths.size() != points.size() then decide if we need to skip 1st or last lengths entry
               int diff_size = lengths.size() - points.size();
               int count = 0;
               if (diff_size == 1) {
                  // More clipSegments available than detected start points
                  // Need to decide which clipSegments to use
                  // Skip 1st entry if 1st segment length shorter than last one
                  if (lengths.elementAt(0) < lengths.elementAt(lengths.size()-1))
                     count = 1;
               }
               debug.print("count start=" + count);
               Long stop;
               long total = 0;
               for (Long start : points) {
                  if (count < lengths.size())
                     stop = start + lengths.elementAt(count);
                  else {
                     log.warn("NOTE: End of segment # " + count + " not available");
                     stop = end;
                  }
                  log.print("" + count + ": start=" + toMinSec(start) + " end=" + toMinSec(stop));
                  Hashtable<String,Long> h = new Hashtable<String,Long>();
                  h.put("start", start);
                  h.put("end", stop);
                  cuts.push(h);
                  total += (stop-start);
                  count++;
               }
               
               // Save entry to AutoSkip table with offset=0
               if (cuts.size() > 0) {
                  saveEntry(
                     contentId, data.get("offerId"), 0L, data.get("title"), tivoName, cuts
                  );
                  log.print("TOTAL show time: " + toMinSec(total));
               } else {
                  log.warn("Failed to retrieve cut points for: '" + data.get("title") + "'");
                  if (first_try) {
                     r.disconnect();
                     log.warn("Trying one more time.");
                     Stack<Hashtable<String,String>> new_stack = new Stack<Hashtable<String,String>>();
                     new_stack.push(data);
                     visualDetect(false, tivoName, new_stack);
                  }
               }
               
            } catch (Exception e) {
               log.error("visualDetect - " + e.getMessage());
            }
            r.disconnect();
         }
      }
      config.visualDetect_running = false;
   }
      
   // For Skip enabled program jump to end of playback, then use channel down
   // presses to find commercial end points backwards
   // Backwards way used because doing it forwards could result in false first point
   // This runs as a background thread if background boolean is true
   public static void visualDetect(String tivoName, Stack<Hashtable<String,String>> stack, Boolean background) {
      if (background) {
         // Non blocking mode
         Task<Void> task = new Task<Void>() {
            @Override public Void call() {
               visualDetect(true, tivoName, stack);
               return null;
            }
         };
         new Thread(task).start();
      } else {
         // Blocking mode
         visualDetect(true, tivoName, stack);
      }
   }
   
   // This designed to be called from kmttg command line to run visualDetect in batch mode
   public static synchronized void visualDetectBatch(String tivoName) {
      Remote r = config.initRemote(tivoName);
      if (r.success) {
         // Switch between My Shows and TiVo to force new SKIP processing on TiVo
         try {
            JSONObject j = new JSONObject();
            j.put("event", "nowShowing");
            r.Command("keyEventSend", j);
            Thread.sleep(4000);
            j.put("event", "tivo");
            r.Command("keyEventSend", j);
            Thread.sleep(4000);
            j.put("event", "nowShowing");
            r.Command("keyEventSend", j);
            Thread.sleep(4000);
         } catch (Exception e) {
            log.error("visualDetectBatch - " + e.getMessage());
            return;            
         }

         JSONArray data = r.MyShows(null);
         if (data != null) {
            Stack<Hashtable<String,String>> stack = new Stack<Hashtable<String,String>>();
            for (int i=0; i<data.length(); ++i) {
               try {
                  JSONObject json = data.getJSONObject(i).getJSONArray("recording").getJSONObject(0);
                  Hashtable<String,String> entry = parseNPL.rpcToHashEntry(tivoName, json);
                  if (entry != null && entry.containsKey("contentId")) {
                     if (entry.containsKey("clipMetadataId") && ! hasEntry(entry.get("contentId"))) {
                        stack.push(entry);
                     }
                  }
               } catch (JSONException e) {
                  log.error("visualDetectBatch - " + e.getMessage());
                  return;
               }
            }
            if (stack.isEmpty()) {
               log.warn("No entries found for processing AutoSkip from SkipMode");
            } else {
               log.print("" + stack.size() + " entries found to process for AutoSkip from SkipMode:");
               for (Hashtable<String,String> e : stack)
                  log.print("   " + e.get("title"));
               visualDetect(tivoName, stack, false);
            }
         } // If data != null
         if (config.autoskip_batch_standby == 1) {
            try {
               log.print("Switching to standby mode for TiVo: " + tivoName);
               JSONObject j = new JSONObject();
               Thread.sleep(1000);
               j.put("event", "standby");
               r.Command("keyEventSend", j);               
            } catch (Exception e) {
               log.error("visualDetectBatch - " + e.getMessage());
            }
         }
         r.disconnect();
      } // If r.success
   }
   
   private static Stack<Long> getSegmentLengths(JSONObject clipData) {
      Stack<Long> lengths = new Stack<Long>();
      try {
         if (clipData.has("segment")) {
            JSONArray segments = clipData.getJSONArray("segment");
            for (int i=0; i<segments.length(); ++i) {
               JSONObject segment = segments.getJSONObject(i);
               long startOffset = Long.parseLong(segment.getString("startOffset"));
               long endOffset = Long.parseLong(segment.getString("endOffset"));
               lengths.push(endOffset-startOffset);
            }
         }
      } catch (JSONException e) {
         log.error("getSegmentLengths - " + e.getMessage());
      }
      return lengths;
   }
   
   public static void logTimeSum(JSONArray cuts, long offset) {
      try {
         long total = 0;
         int index = 0;
         for (int i=0; i<cuts.length(); ++i) {
            JSONObject j = cuts.getJSONObject(i);
            long start = j.getLong("start");
            if (index > 0)
               start += offset;
            long end = j.getLong("end") + offset;
            total += (end-start);
            index++;
         }
         log.print("TOTAL show time: " + toMinSec(total));
      } catch (JSONException e) {
         log.error("skipTable TABLERowSelected - " + e.getMessage());
      }
   }
   
   private static Stack<Long> reverseStack(Stack<Long> stack){
      Stack<Long> reverse = new Stack<Long>();
      while(!stack.empty()){
         reverse.push(stack.pop());
      }
      return reverse;
   }
      
   public static synchronized String toMinSec(long msecs) {
      debug.print("msecs=" + msecs);
      return com.tivo.kmttg.captions.util.toHourMinSec(msecs);
   }
   
   public static synchronized void skipServiceBatch(String tivoName) {
      JSONArray skipData = SkipManager.getEntries();
      if (skipData == null) {
         log.error("AutoSkip not configured");
         System.exit(1);
      }
      
      if (tivoName.toLowerCase().equals("all")) {
         // Enabled AutoSkip service on all RPC enabled TiVos
         int count = 0;
         Stack<String> tivoNames = config.getTivoNames();
         for (String name : tivoNames) {
            if (config.rpcEnabled(name)) {
               SkipManager.startService(name);
               count++;
            }
         }
         if (count == 0) {
            log.error("No RPC enabled TiVos found - exiting");
            System.exit(1);
         }
      } else {
         // Enable AutoSkip service on specific TiVo
         if ( ! config.rpcEnabled(tivoName) ) {
            log.error("Given TiVo not RPC enabled - '" + tivoName + "'");
            System.exit(1);
         }
         SkipManager.startService(tivoName);
      }
   }

}
