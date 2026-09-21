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
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.Stack;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.swing.SwingUtil;
import com.tivo.kmttg.gui.tivoTab;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.parseNPL;

public class SkipManager {
   private static Hashtable<String,AutoSkip> instances = new Hashtable<String,AutoSkip>();
   private static Hashtable<String,SkipService> serviceInstances = new Hashtable<String,SkipService>();
   
   // Resolved on each call rather than latched into a static at class load: programDir is
   // only known once config has initialised, and a test that points it somewhere else has no
   // way to know whether this class had already been loaded.
   public static synchronized String iniFile() {
      return config.programDir + File.separator + "AutoSkip.ini";
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
   
   // Save commercial points for current entry to the ini file
   public static synchronized void saveEntry(final String contentId, String offerId, long offset,
         String title, final String tivoName, Stack<Hashtable<String,Long>> data) {
      debug.print("contentId=" + contentId + " offerId=" + offerId + " offset=" + offset);
      // Every lookup is by airing, so an entry without one is written and never found again -
      // and the remove-then-save its callers do would append another copy on the next run
      if (offerId == null || offerId.isEmpty()) {
         log.warn("Not saving AutoSkip entry for '" + title + "': the recording carries no offerId");
         return;
      }
      log.print("Saving AutoSkip entry: " + title);
      try {
         String eol = "\r\n";
         BufferedWriter ofp = new BufferedWriter(new FileWriter(iniFile(), true));
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
            SwingUtil.runLater(new Runnable() {
               @Override public void run() {
                  config.gui.getTab(tivoName).getTable().updateSkipStatus(offerId);
               }
            });
         }
      } catch (IOException e) {
         log.error("saveEntry - " + e.getMessage());
      }
   }
   
   // One entry of AutoSkip.ini as the file holds it
   static class SkipEntry {
      String contentId = "", offerId = "", offset = "", tivoName = "", title = "";
      Stack<Hashtable<String,Long>> cuts = new Stack<Hashtable<String,Long>>();
   }
   
   // Every entry, in file order
   static Stack<SkipEntry> readEntries() {
      return read(null);
   }
   
   // The entry for one airing, or null. An absent id matches nothing rather than everything:
   // offerId is optional in the NPL data (parseNPL only sets it when the recording carries
   // one), so "no id" has to mean "no entry" and not "whichever entry comes first".
   static SkipEntry readEntry(String offerId) {
      if (offerId == null || offerId.isEmpty())
         return null;
      Stack<SkipEntry> entries = read(offerId);
      return entries.isEmpty() ? null : entries.get(0);
   }
   
   // One pass over AutoSkip.ini, stopping early once the wanted airing is in hand. Entries
   // are keyed by the airing rather than the programme: the same episode on two stations is
   // two recordings with two sets of breaks, and keying on the contentId they share let one
   // of them overwrite the other.
   private static Stack<SkipEntry> read(String offerId) {
      Stack<SkipEntry> entries = new Stack<SkipEntry>();
      if ( ! file.isFile(iniFile()) )
         return entries;
      try (BufferedReader ifp = new BufferedReader(new FileReader(iniFile()))) {
         String line = null;
         SkipEntry current = null;
         while (( line = ifp.readLine()) != null) {
            if (line.contains("<entry>")) {
               if (collect(current, offerId, entries))
                  return entries;
               current = new SkipEntry();
            }
            else if (current == null)
               continue;
            else if (line.startsWith("contentId="))
               current.contentId = value(line, "contentId");
            else if (line.startsWith("offerId="))
               current.offerId = value(line, "offerId");
            else if (line.startsWith("offset="))
               current.offset = value(line, "offset");
            else if (line.startsWith("tivoName="))
               current.tivoName = value(line, "tivoName");
            else if (line.startsWith("title="))
               current.title = value(line, "title");
            else if (line.matches("^[0-9]+.*")) {
               String[] l = line.split("\\s+");
               Hashtable<String,Long> h = new Hashtable<String,Long>();
               h.put("start", Long.parseLong(l[0]));
               h.put("end", Long.parseLong(l[1]));
               current.cuts.push(h);
            }
         }
         collect(current, offerId, entries);
      } catch (Exception e) {
         log.error("SkipManager readEntries - " + e.getMessage());
         log.error(Arrays.toString(e.getStackTrace()));
      }
      return entries;
   }
   
   // Keep the finished entry if it was asked for, and say whether the scan can stop here
   private static Boolean collect(SkipEntry entry, String offerId, Stack<SkipEntry> entries) {
      if (entry == null)
         return false;
      if (offerId == null) {
         entries.push(entry);
         return false;
      }
      if (offerId.equals(entry.offerId)) {
         entries.push(entry);
         return true;
      }
      return false;
   }
   
   // Everything after the first "=", so a title carrying one survives
   private static String value(String line, String key) {
      return line.replaceFirst(key + "=", "");
   }
   
   public static synchronized Boolean hasEntry(String offerId) {
      debug.print("offerId=" + offerId);
      if (! skipEnabled() )
         return false;
      return readEntry(offerId) != null;
   }
   
   // Remove the entry for the given airing from the ini file
   public static synchronized Boolean removeEntry(final String offerId) {
      debug.print("offerId=" + offerId);
      if (offerId == null || offerId.isEmpty())
         return false;
      if (file.isFile(iniFile())) {
         try {
            Boolean itemRemoved = false;
            Stack<String> lines = new Stack<String>();
            BufferedReader ifp = new BufferedReader(new FileReader(iniFile()));
            String line = null;
            Boolean include = true;
            String tivoName = null;
            String title = null;
            while (( line = ifp.readLine()) != null) {
               if (line.contains("<entry>")) {
                  include = true;
                  String contentLine = ifp.readLine();
                  String offerLine = ifp.readLine();
                  if (value(offerLine, "offerId").equals(offerId)) {
                     include = false;
                     itemRemoved = true;
                     ifp.readLine(); // offset
                     tivoName = value(ifp.readLine(), "tivoName");
                     title = value(ifp.readLine(), "title");
                  }
                  if (include) {
                     lines.push(line);
                     lines.push(contentLine);
                     lines.push(offerLine);
                  }
               } else {
                  if (include)
                     lines.push(line);
               }
            }
            ifp.close();
            String eol = "\r\n";
            BufferedWriter ofp = new BufferedWriter(new FileWriter(iniFile()));
            for (String l : lines) {
               ofp.write(l + eol);
            }
            ofp.close();
            if (itemRemoved) {
               log.print("Removed entry for " + tivoName + ": " + title);
               if (tivoName != null && config.GUIMODE) {
                  // Remove asterisk from associated table
                  final String final_tivoName = tivoName;
                  SwingUtil.runLater(new Runnable() {
                     @Override public void run() {
                        tivoTab t = config.gui.getTab(final_tivoName);
                        if (t != null) {
                           t.getTable().updateSkipStatus(offerId);
                        }
                     }
                  });
               }
            }
            else
               log.print("No entry found for: " + offerId);
            return itemRemoved;
         } catch (Exception e) {
            log.error("removeEntry - " + e.getMessage());
         }
      }
      return false;
   }
   
   // Change offset for the given airing
   public static synchronized Boolean changeEntry(String offerId, String offset, String title) {
      debug.print("offerId=" + offerId + " offset=" + offset + " title=" + title);
      if (offerId == null || offerId.isEmpty())
         return false;
      if (file.isFile(iniFile())) {
         try {
            Boolean itemChanged = false;
            Stack<String> lines = new Stack<String>();
            BufferedReader ifp = new BufferedReader(new FileReader(iniFile()));
            String line = null;
            while (( line = ifp.readLine()) != null) {
               if (line.contains("<entry>")) {
                  String contentLine = ifp.readLine();
                  String offerLine = ifp.readLine();
                  String offsetLine = ifp.readLine();
                  lines.push(line);
                  // A file cut short mid entry - a save interrupted by a crash or a full
                  // disk - otherwise pushes a null through to the writer below, which puts
                  // the literal text "null" into the user's skip data and leaves read()
                  // mis-parsing that entry on every load afterwards.
                  if (contentLine == null || offerLine == null || offsetLine == null) {
                     if (contentLine != null) lines.push(contentLine);
                     if (offerLine != null)   lines.push(offerLine);
                     break;
                  }
                  lines.push(contentLine);
                  lines.push(offerLine);
                  if (value(offerLine, "offerId").equals(offerId)) {
                     itemChanged = true;
                     lines.push("offset=" + offset);
                  } else {
                     lines.push(offsetLine);
                  }
               } else {
                  lines.push(line);
               }
            }
            ifp.close();
            String eol = "\r\n";
            BufferedWriter ofp = new BufferedWriter(new FileWriter(iniFile()));
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
   
   // Every airing the file holds, in one pass. getEntry re-reads and re-parses the whole ini
   // for each id it is asked about, which is fine for one lookup and quadratic when a caller
   // is asking about a whole Now Playing list.
   public static synchronized Set<String> offerIds() {
      debug.print("");
      Set<String> ids = new HashSet<String>();
      for (SkipEntry e : readEntries())
         ids.add(e.offerId);
      return ids;
   }

   // Synchronized like every other accessor here: removeEntry and changeEntry rewrite the
   // whole ini, so an unsynchronized read can land on a truncated file and report a recording
   // as having no skip data when it has some.
   public static synchronized Stack<Hashtable<String,Long>> getEntry(String offerId) {
      debug.print("offerId=" + offerId);
      SkipEntry entry = readEntry(offerId);
      return entry == null ? new Stack<Hashtable<String,Long>>() : entry.cuts;
   }

   // Return entries for use by SkipDialog table
   public static synchronized JSONArray getEntries() {
      debug.print("");
      JSONArray entries = new JSONArray();
      try {
         for (SkipEntry e : readEntries()) {
            // An entry with no cut pairs has never been listed here
            if (e.cuts.isEmpty())
               continue;
            JSONArray cuts = new JSONArray();
            for (Hashtable<String,Long> cut : e.cuts) {
               JSONObject j = new JSONObject();
               j.put("start", cut.get("start"));
               j.put("end", cut.get("end"));
               cuts.put(j);
            }
            JSONObject json = new JSONObject();
            json.put("contentId", e.contentId);
            json.put("offerId", e.offerId);
            json.put("offset", e.offset);
            json.put("tivoName", e.tivoName);
            json.put("title", e.title);
            json.put("ad1", "" + cuts.getJSONObject(0).get("end"));
            json.put("cuts", cuts);
            entries.put(json);
         }
      } catch (JSONException e) {
         log.error("getEntries - " + e.getMessage());
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
                  // Only what actually went: removeEntry declines an empty offerId, and
                  // counting that anyway reported entries pruned that are still in the file.
                  if (removeEntry(json.getString("offerId")))
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
         String offerId = data.get("offerId");
         String clipMetadataId = data.get("clipMetadataId");
         if (hasEntry(offerId))
            removeEntry(offerId);
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
         Runnable task = new Runnable() {
            @Override public void run() {
               visualDetect(true, tivoName, stack);
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
                  if (entry != null && entry.containsKey("offerId")) {
                     if (entry.containsKey("clipMetadataId") && ! hasEntry(entry.get("offerId"))) {
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
               //if (skipEnabled() && config.autoskip_prune == 1)
               //   pruneEntries(tivoName, stack);
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
