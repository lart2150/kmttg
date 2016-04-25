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
package com.tivo.kmttg.task;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.main.auto;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.SkipManager;
import com.tivo.kmttg.util.*;

public class NowPlaying extends baseTask implements Serializable {
   private static final long serialVersionUID = 1L;
   private Stack<Hashtable<String,String>> ENTRIES = new Stack<Hashtable<String,String>>();
   private backgroundProcess process;
   public  jobData job;
   
   // rpc related
   private Boolean rpc = false;
   private Thread thread = null;
   private Boolean thread_running = false;
   private Boolean success = false;
   private JSONArray data = null;
   
   public NowPlaying(jobData job) {
      debug.print("job=" + job);
      this.job = job;
      
      if (config.rpcnpl == 1 && config.rpcEnabled(job.tivoName)) {
         rpc = true;
      }      
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      job.ip = config.TIVOS.get(job.tivoName);
      if (job.ip == null || job.ip.length() == 0) {
         log.error("IP not defined for tivo: " + job.tivoName);
         return false;
      }
      return true;      
   }
   
   public Boolean start() {
      debug.print("");
      if (job.ip == null || job.ip.length() == 0) {
         log.error("IP not defined for tivo: " + job.tivoName);
         jobMonitor.removeFromJobList(job);
         return false;
      }
      if (config.MAK == null || config.MAK.length() != 10) {
         log.error("MAK not specified or not correct");
         return false;
      }
      
      if (rpc) {
         // RPC run in a separate thread
         class AutoThread implements Runnable {
            AutoThread() {}       
            public void run () {
               Remote r = config.initRemote(job.tivoName);
               if (r.success) {
                  job.getURLs = true;
                  if (job.partiallyViewed)
                     data = r.MyShowsWatched(job);
                  else
                     data = r.MyShows(job);
                  if (data != null)
                     success = true;
                  else
                     success = false;
               }
               thread_running = false;
            }
         }
         thread_running = true;
         AutoThread t = new AutoThread();
         thread = new Thread(t);
         log.print(">> Getting Now Playing List via RPC from " + job.tivoName + " ...");
         thread.start();
      }
      return true;
   }
   
   public void kill() {
      debug.print("");
      if (rpc) {
         thread.interrupt();
         log.warn("Killing '" + job.type + "' TiVo: " + job.tivoName);
         thread_running = false;
         success = false;
      }
   }
   
   // Check status of a currently running job
   // Returns true if still running, false if job finished
   // If job is finished then check result
   public Boolean check() {
      if (rpc) {
         // rpc job handling
         if (thread_running) {
            // Still running
            if (config.GUIMODE) {
               // Update STATUS column
               config.gui.jobTab_UpdateJobMonitorRowStatus(job, "running");
            }
            return true;
         } else {
            // Job finished
            if (config.GUIMODE) {
               if ( jobMonitor.isFirstJobInMonitor(job) ) {
                  config.gui.setTitle(config.kmttg);
                  config.gui.progressBar_setValue(0);
               }
            }
            jobMonitor.removeFromJobList(job);
            if (success) {
               if (data == null) {
                  log.error("Failed to retrieve Now Playing List from " + job.tivoName);
               } else {
                  // Populate NPL table
                  rpcToNPL();
                  
                  log.warn("NPL job completed: " + jobMonitor.getElapsedTime(job.time));
                  log.print("---DONE--- job=" + job.type + " tivo=" + job.tivoName);
                  
                  if (SkipManager.skipEnabled() && config.autoskip_prune == 1) {
                     log.warn("Pruning AutoSkip table entries");
                     SkipManager.pruneEntries(job.tivoName, ENTRIES);
                  }
               }
            }
         }
      }
      
      return false;
   }
   
   // Convert rpc data to traditional Hash data and populate NPL table
   private void rpcToNPL() {
      // Populate ENTRIES
      try {
         long now = new Date().getTime();
         for (int i=0; i<data.length(); ++i) {
            JSONObject json = data.getJSONObject(i).getJSONArray("recording").getJSONObject(0);
            Hashtable<String,String> entry = new Hashtable<String,String>();
            entry.put("tivoName", job.tivoName);
            if (json.has("title")) {
               entry.put("titleOnly", json.getString("title"));
               entry.put("title", json.getString("title"));
            }
            if (json.has("subtitle")) {
               entry.put("episodeTitle", json.getString("subtitle"));
               if (json.has("title"))
                  entry.put("title", json.getString("title") + " - " + json.getString("subtitle"));
            }
            if (json.has("description")) {
               entry.put("description", json.getString("description"));
            }
            if (json.has("recordingId")) {
               entry.put("recordingId", json.getString("recordingId"));
            }
            if (json.has("contentId")) {
               entry.put("contentId", json.getString("contentId"));
            }
            if (json.has("offerId")) {
               entry.put("offerId", json.getString("offerId"));
            }
            if (json.has("clipMetadata")) {
               JSONArray a = json.getJSONArray("clipMetadata");
               if (a.length() > 0) {
                  entry.put("clipMetadataId", a.getJSONObject(0).getString("clipMetadataId"));
               }
            }
            if (json.has("hdtv")) {
               if (json.getBoolean("hdtv"))
                  entry.put("HD", "Yes");
            }
            if (json.has("size")) {
               long sizeKB = json.getLong("size");
               long size = sizeKB*1024;
               entry.put("size", "" + size);
               double GB = Math.pow(2,30);
               entry.put("sizeGB", String.format("%.2f GB", size/GB));
            }
            if (json.has("originalAirdate")) {
               entry.put("originalAirDate", json.getString("originalAirdate"));
            }
            if (json.has("channel")) {
               JSONObject chan = json.getJSONObject("channel");
               if (chan.has("callSign"))
                  entry.put("channel", chan.getString("callSign"));
               else
                  if (chan.has("name"))
                     entry.put("channel", chan.getString("name"));
               if (chan.has("channelNumber"))
                  entry.put("channelNum", chan.getString("channelNumber"));
            }
            if (json.has("episodeNum") && json.has("seasonNumber")) {
               entry.put(
                  "EpisodeNumber",
                  "" + json.get("seasonNumber") +
                  String.format("%02d", json.getJSONArray("episodeNum").get(0))
               );
            }
            if (json.has("startTime") && json.has("duration")) {
               long start = TableUtil.getStartTime(json);
               entry.put("gmt", "" + start);
               SimpleDateFormat sdf = new SimpleDateFormat("E MM/dd/yyyy");
               entry.put("date", sdf.format(start));
               sdf = new SimpleDateFormat("E MM/dd/yyyy hh:mm aa");
               entry.put("date_long", sdf.format(start));
               entry.put("duration", "" + json.getInt("duration")*1000);
            }
            if (json.has("partnerCollectionId")) {
               entry.put("ProgramId", json.getString("partnerCollectionId"));
               if (entry.containsKey("gmt"))
                  entry.put("ProgramId_unique", entry.get("ProgramId") + "_" + entry.get("gmt"));
            }
            if (json.has("__SeriesId__")) {
               // This data not in TiVo database - added by Remote MyShows method
               entry.put("SeriesId", json.getString("__SeriesId__"));
            }
            if (json.has("drm")) {
               JSONObject drm = json.getJSONObject("drm");
               if (drm.has("tivoToGo")) {
                  if (! drm.getBoolean("tivoToGo")) {
                     //entry.put("ExpirationImage", "copy-protected");
                     entry.put("CopyProtected", "Yes");
                  }
               }
            }
            if (json.has("desiredDeletion")) {
               long desired = TableUtil.getLongDateFromString(json.getString("desiredDeletion"));
               long diff = desired - now;
               double hours = diff/(1000*60*60);
               if (hours > 0 && hours < 24)
                  entry.put("ExpirationImage", "expires-soon-recording");
               if (hours < 0)
                  entry.put("ExpirationImage", "expired-recording");
            }
            if (json.has("deletionPolicy")) {
               String policy = json.getString("deletionPolicy");
               if(policy.equals("neverDelete")) {
                  entry.put("ExpirationImage", "save-until-i-delete-recording");
                  entry.put("kuid", "yes");                  
               }
            }
            if (json.has("bookmarkPosition")) {
               // NOTE: Don't have ByteOffset available, so use TimeOffset (secs) instead
               entry.put("TimeOffset", "" + json.getInt("bookmarkPosition")/1000);
               
               /* This intentionally removed in favor of getting ByteOffset from XML
               if (json.has("size") && json.has("duration")) {
                  // Estimate ByteOffset based on TimeOffset
                  // size (KB) * 1024 * bookmarkPosition/(duration(s)*1000)
                  long size = (long)json.getLong("size")*1024;
                  long duration = (long)json.getLong("duration")*1000;
                  long bookmarkPosition = (long)json.getLong("bookmarkPosition");
                  long ByteOffset = bookmarkPosition*size/duration;
                  entry.put("ByteOffset", "" + ByteOffset);
               }*/
            }
            if (json.has("subscriptionIdentifier")) {
               JSONArray a = json.getJSONArray("subscriptionIdentifier");
               for (int j=0; j<a.length(); ++j) {
                  JSONObject o = a.getJSONObject(j);
                  if (o.has("subscriptionType")) {
                     if (o.getString("subscriptionType").startsWith("suggestion")) {
                        entry.put("ExpirationImage", "suggestion-recording");
                        entry.put("suggestion", "yes");                        
                     }
                  }
               }
            }
            // In progress recordings trump any other kind of icon
            if (json.has("state")) {
               if (json.getString("state").equals("inProgress")) {
                  entry.put("ExpirationImage", "in-progress-recording");
                  entry.put("InProgress", "Yes");
                  if (json.has("collectionTitle")) {
                     String c = json.getString("collectionTitle");
                     if (c.equals("pcBodySubscription"))
                        entry.put("ExpirationImage", "in-progress-transfer");
                  }
               }
            }
            if (json.has("movieYear")) {
               entry.put("movieYear", "" + json.get("movieYear"));
            }
            if (json.has("__url__")) {
               entry.put("url", json.getString("__url__"));
               json.remove("__url__");
            }
            if (json.has("__url_TiVoVideoDetails__")) {
               entry.put("url_TiVoVideoDetails", json.getString("__url_TiVoVideoDetails__"));
               json.remove("__url_TiVoVideoDetails__");
            }

            ENTRIES.add(entry);
         }
      } catch (Exception e) {
         log.error("rpcToNPL - " + e.getMessage());
         log.error(Arrays.toString(e.getStackTrace()));
      }
      
      // Now continue using traditional processing
      if (config.GUI_AUTO > 0) {
         // Update NPL
         config.gui.nplTab_SetNowPlaying(job.tivoName, ENTRIES);
         auto.processAll(job.tivoName, ENTRIES);
      }
      else if (config.GUIMODE) {
         // GUI mode: populate NPL table
         config.gui.nplTab_SetNowPlaying(job.tivoName, ENTRIES);
      } else {
         // Batch mode
         auto.processAll(job.tivoName, ENTRIES);            
      }
   }
   
   public void printNplList() {
      debug.print("");
      String name;
      Object value;
      log.print("NPL:");
      log.print("ENTRIES=" + ENTRIES.size());
      for (int i=0; i<ENTRIES.size(); i++) {
         log.print(" ");
         log.print("ENTRY " + (i+1));
         for (Enumeration<String> e=ENTRIES.get(i).keys(); e.hasMoreElements();) {
            name = e.nextElement();
            value = ENTRIES.get(i).get(name);
            log.print(name + "=" + value);
         }
      }
   }
   
   public Stack<Hashtable<String,String>> getEntries() {
      return ENTRIES;
   }
}

