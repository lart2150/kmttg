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
import java.io.FileReader;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;

import javafx.concurrent.Task;

import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class AutoSkip {
   private Remote r = null;
   private Timer timer = null;
   private long offset = -1;
   private long end1 = -1;
   private String offerId = null;
   private String contentId = null;
   private String title = "";
   private int monitor_count = 1;
   private int monitor_interval = 6;
   private String tivoName = null;
   Boolean monitor = false;
   private long posEnd = -1;
   Stack<Hashtable<String,Long>> skipData = null;
   Stack<Hashtable<String,Long>> skipData_orig = null;
   String recordingId = null;
   
   public synchronized Boolean isMonitoring() {
      debug.print("");
      return monitor;
   }
   
   public synchronized String offerId() {
      debug.print("");
      return offerId;
   }
   
   public synchronized void setMonitor(String tivoName, String offerId, String contentId, String title) {
      debug.print("tivoName=" + tivoName + " offerId=" + offerId + " contentId=" + contentId + " title=" + title);
      if (this.tivoName != null && ! this.tivoName.equals(tivoName)) {
         disable();
      }
      this.tivoName = tivoName;
      this.offerId = offerId;
      this.contentId = contentId;
      this.title = title;
      
      if (r == null) {
         r = new Remote(tivoName);
         if (! r.success) {
            disable();
            return;
         }
      }
      
      startTimer();      
      monitor = true;
   }
   
   // Run this in background mode so as not to block GUI
   public synchronized void skipPlay(final String tivoName, final Hashtable<String,String> nplData) {
      debug.print("tivoName=" + tivoName + " nplData=" + nplData);
      this.tivoName = tivoName;
      Task<Void> task = new Task<Void>() {
         @Override public Void call() {
            if (! nplData.containsKey("contentId")) {
               error("Missing contentId");
               disable();
               return null;
            }
            if (! nplData.containsKey("offerId")) {
               error("Missing offerId");
               disable();
               return null;
            }
            if (! nplData.containsKey("recordingId")) {
               error("Missing recordingId");
               disable();
               return null;
            }
            if (nplData.containsKey("title"))
               title = nplData.get("title");
            contentId = nplData.get("contentId");
            offerId = nplData.get("offerId");
            recordingId = nplData.get("recordingId");
            r = new Remote(tivoName);
            if (r.success) {
               if (readEntry(contentId)) {
                  print("Obtained skip data from file: " + SkipManager.iniFile());
               } else {
                  error("No skip data available for " + title);
                  disable();
                  return null;
               }
               if (skipData != null) {
                  // Start playback
                  print("play: " + nplData.get("title"));
                  JSONObject json = new JSONObject();
                  try {
                     json.put("id", recordingId);
                     r.Command("Playback", json);
                  } catch (Exception e) {
                     error("skipPlay - " + e.getMessage());
                     disable();
                     return null;
                  }
                  enableMonitor(tivoName, skipData, end1);
               } else {
                  disable();
               }
            }
            return null;
         }
      };
      new Thread(task).start();
   }
   
   // end1 = -1 => pause mode, else normal skip mode without pause
   private synchronized void enableMonitor(String tivoName, Stack<Hashtable<String,Long>> points, Long end1) {
      debug.print("tivoName=" + tivoName + " points=" + points);
      this.tivoName = tivoName;
      this.end1 = end1;
      if (r == null) {
         r = new Remote(tivoName);
         if (! r.success) {
            disable();
            return;
         }
      }
      skipData_orig = points;
      skipData = hashCopy(skipData_orig);
      showSkipData();
      if (end1 == -1) {
         print("REMINDER: 1st pause press will be saved as exact start of 1st commercial");
         print("REMINDER: Use 'x' bindkey to jump to close to 1st commercial");
      }
      monitor = true;
      // Start timer to monitor playback position
      startTimer();
   }
   
   // Start timer to monitor playback position
   private synchronized void startTimer() {
      debug.print("");
      if (timer != null)
         timer.cancel();
      timer = new Timer();
      timer.schedule(
         new TimerTask() {
            @Override
            public void run() {
               skipPlayCheck(tivoName, contentId);
            }
        }
        ,2000,
        1000
      );
   }
   
   // This procedure called constantly in a timer
   // This monitors playback position and skips commercials
   private synchronized void skipPlayCheck(String tivoName, String contentId) {
      debug.print("tivoName=" + tivoName + " contentId=" + contentId);
      if (monitor && r != null) {
         Boolean skip = true;
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
         int pad_start = config.autoskip_padding_start;
         int pad_stop = config.autoskip_padding_stop;
         for (Hashtable<String,Long> h : skipData) {
            if (pad_start <= 0) {
               if (pos >= (h.get("start")+pad_start) && pos <= (h.get("end")+pad_stop)) {
                  skip = false;
               }
            } else {
               // Can't use pad_start here when positive, else can double skip if user using D press
               if (pos >= h.get("start") && pos <= (h.get("end")+pad_stop)) {
                  skip = false;
               }               
            }
         }
         // If pos < first end point then don't skip
         //if (skip) {
         //   if (pos < skipData.get(0).get("end"))
         //      skip = false;
         //}
         // If pos >= last end point then don't skip
         if (skip) {
            if (pos >= skipData.get(skipData.size()-1).get("end")) {
               if (config.autoskip_jumpToEnd == 1) {
                  if( posEnd != pos ) {
                     long jumpto = pos + (1000 * 60 * 24);  // arbitrarily jump 1 day
                     print("(pos=" + SkipManager.toMinSec(pos) +
                           ") IN LAST COMMERCIAL. JUMPING TO END");
                     jumpTo(jumpto);
                     posEnd = getPosition();
                  }
               }
               skip = false;
            }
         }
         if (skip) {
            posEnd = -1;
            long jumpto = getClosest(pos);
            if (jumpto != -1) {
               String message = "(pos=" + SkipManager.toMinSec(pos) +
                  ") IN COMMERCIAL. JUMPING TO: " + SkipManager.toMinSec(jumpto+pad_start);
               if (pad_start != 0 || pad_stop != 0) {
                  message += " (start padding=" + config.autoskip_padding_start + " msecs" +
                     ", end padding=" + config.autoskip_padding_stop + " msecs)";
               }
               print(message);
               jumpTo(jumpto+pad_start);
            }
         }
      }
   }
   
   // Jump to given position in playback
   private synchronized void jumpTo(long position) {
      debug.print("position=" + position);
      JSONObject json = new JSONObject();
      JSONObject j = new JSONObject();
      try {
         json.put("offset", position);
         j.put("event", "play");
      } catch (JSONException e) {
         error("jumpTo - " + e.getMessage());
      }
      r.Command("Jump", json);
      if (config.autoskip_indicate_skip == 1)
         r.Command("keyEventSend", j);
   }
   
   // Jump to end of 1st show segment
   /*private synchronized void jumpTo1st() {
      debug.print("");
      if (r != null && skipData != null && monitor) {
         long pos = skipData.get(0).get("end");
         print("Jumping to: " + SkipManager.toMinSec(pos));
         jumpTo(pos);
      }
   }*/
   
   // RPC query to get current playback position
   // NOTE: Returns -1 for speed != 100 to avoid any skipping during trick play
   private synchronized long getPosition() {
      debug.print("");
      if (r==null || ! monitor) return -1;
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
            // DEBUG log.print("reply=" + reply.toString(3));  // TODO
            // DEBUG JSONObject w = r.Command("whatsOnSearch", new JSONObject());
            // DEBUG log.print("w=" + w.toString(3));
            if (reply.has("speed")) {
               int speed = reply.getInt("speed");
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
   private synchronized void shouldStillMonitor() {
      debug.print("");
      if (r == null)
         return;
      if (monitor) {
         JSONObject result = r.Command("whatsOnSearch", new JSONObject());
         if (result == null) {
            // RPC session may be corrupted, start a new connection
            if (r != null) r.disconnect();
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
   public synchronized void disable() {
      debug.print("");
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
      monitor_count = 1;
      title = "";
      posEnd = -1;
   }
   
   // Print current skipData to console
   public synchronized void showSkipData() {
      debug.print("");
      if (skipData == null) {
         error("showSkipData - no skip data available");
         disable();
         return;
      }
         
      int index = 1;
      for (Hashtable<String,Long> h : skipData) {
         String message = "" + index + ": start=";
         message += SkipManager.toMinSec(h.get("start"));
         message += " end=";
         message += SkipManager.toMinSec(h.get("end"));         
         log.print(message);
         index++;
      }
   }
   
   // Get the closest non-commercial start point to given pos
   // This will be point to jump to when in a commercial segment
   // Return value of -1 => no change wanted
   private synchronized long getClosest(long pos) {
      debug.print("pos=" + pos);
      long closest = -1;
      // If current pos is within any start-end range then no skip necessary
      int pad_start = config.autoskip_padding_start;
      int pad_stop = config.autoskip_padding_stop;
      for (Hashtable<String,Long> h : skipData) {
         if (pos >= (h.get("start")+pad_start) && pos <= (h.get("end")+pad_stop))
            return -1;
      }
      
      if (skipData.size() > 1)
         closest = skipData.get(0).get("start");
      long diff = pos;
      int count = 0; int index = 0;
      for (Hashtable<String,Long> h : skipData) {
         long start = h.get("start");
         if (pos < start) {
            if (start - pos < diff) {
               diff = start - pos;
               closest = start;
               index = count;
            }
         }
         count++;
      }
      if (closest < pos) {
         // Don't jump backwards
         if (index+1 < skipData.size())
            closest = skipData.get(index+1).get("start");
         if (closest < pos)
            return -1;
      }
      return closest;
   }
   
   // Convert RPC data to skipData hash
   /*public synchronized Stack<Hashtable<String,Long>> jsonToShowPoints(JSONObject clipData) {
      debug.print("clipData=" + clipData);
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
   }*/
   
   // Adjust skipData segment 1 end position and
   // all subsequent segment points relative to it
   private synchronized void adjustPoints(long point) {
      debug.print("point=" + point);
      //print("Adjusting commercial points");
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
   
   // Obtain commercial points for given contentId if it exists
   // Returns true if contentId found, false otherwise
   // NOTE: Reading assumes file entries are structured just like they were originally written
   synchronized Boolean readEntry(String contentId) {
      debug.print("contentId=" + contentId);
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
                        skipData_orig = new Stack<Hashtable<String,Long>>();
                        while (( line = ifp.readLine()) != null) {
                           if (line.equals("<entry>"))
                              break;
                           if (line.startsWith("offerId")) {
                              l = line.split("=");
                              offerId = l[1];
                           }
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
                           //print("Using existing saved AutoSkip entry for: " + title);
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
            error(Arrays.toString(e.getStackTrace()));
         }
      }
      return false;
   }

   // For Skip enabled program use a D press to detect end of 1st commercial point
   /*public static synchronized Long visualDetect(String tivoName) {
      Long point = -1L;
      Remote r2 = new Remote(tivoName);
      if (r2.success) {
         try {
            long starting = 0;
            
            // Save current position
            JSONObject result = r2.Command("Position", new JSONObject());
            if (result != null && result.has("position")) {
               starting = result.getLong("position");
            }
            
            // Jump to time 0
            JSONObject json = new JSONObject();
            json.put("offset", 0);
            result = r2.Command("Jump", json);
            Thread.sleep(900);
            if (result != null) {
               json.remove("offset");
               json.put("event", "actionD");
               // Send D press and collect time information
               result = r2.Command("keyEventSend", json);
               
               // Get position
               Thread.sleep(100);
               result = r2.Command("Position", new JSONObject());
               if (result != null && result.has("position"))
                  point = result.getLong("position");
               
               if (point < 60000) {
                  log.print("Too small - jumping again");
                  // Time likely too small - jump again
                  Thread.sleep(900);
                  // Send D press and collect time information
                  r2.Command("keyEventSend", json);
                  
                  // Get position
                  Thread.sleep(100);
                  result = r2.Command("Position", new JSONObject());
                  if (result != null && result.has("position"))
                     point = result.getLong("position");
               }
               
               // Jump back to starting position
               json.remove("event");
               json.put("offset", starting);
               result = r2.Command("Jump", json);
            }
            
         } catch (Exception e) {
            error("visualDetect - " + e.getMessage());
         }
         r2.disconnect();
      }
      print("visualDetect point: " + toMinSec(point));

      return point;
   }*/
   
   private synchronized Stack<Hashtable<String,Long>> hashCopy(Stack<Hashtable<String,Long>> orig) {
      debug.print("orig=" + orig);
      Stack<Hashtable<String,Long>> copy = new Stack<Hashtable<String,Long>>();
      for (Hashtable<String,Long> h : orig) {
         Hashtable<String,Long> h_copy = new Hashtable<String,Long>(h);
         copy.push(h_copy);
      }
      return copy;
   }
   
   private synchronized void print(String message) {
      log.print("AutoSkip (" + tivoName + "): " + message);
   }
   
   private synchronized void error(String message) {
      log.error("AutoSkip (" + tivoName + "): " + message);
   }

}