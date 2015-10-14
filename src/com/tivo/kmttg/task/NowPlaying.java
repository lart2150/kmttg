package com.tivo.kmttg.task;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
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
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.*;

public class NowPlaying extends baseTask implements Serializable {
   private static final long serialVersionUID = 1L;
   private Stack<Hashtable<String,String>> ENTRIES = new Stack<Hashtable<String,String>>();
   private Hashtable<String,Integer> unique = new Hashtable<String,Integer>();
   private String cookieFile = "";
   private String outputFile = "";
   private int AnchorOffset = 0;
   private int TotalItems = 0;
   private static int limit_npl_fetches = 0;
   private int fetchCount = 0;
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
      
      if (!rpc) {
         // Generate unique cookieFile and outputFile names
         cookieFile = file.makeTempFile("cookie");
         outputFile = file.makeTempFile("NPL");
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
      limit_npl_fetches = config.getLimitNplSetting(job.tivoName);
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
      } else {
         // Traditional XML retrieval
         Stack<String> command = new Stack<String>();
         String urlString = "https://";
         urlString += job.ip;
         
         // Add wan https port if configured
         String wan_port = config.getWanSetting(job.tivoName, "https");
         if (wan_port != null)
            urlString += ":" + wan_port;
         
         // Enable testLimit only for testing multiple downloads
         Boolean testLimit = false;
         if (testLimit) {
            urlString += "/TiVoConnect?Command=QueryContainer&Container=/NowPlaying&Recurse=Yes&ItemCount=5&AnchorOffset=";
         } else {
            urlString += "/TiVoConnect?Command=QueryContainer&Container=/NowPlaying&Recurse=Yes&AnchorOffset=";
         }
         urlString += AnchorOffset;
         command.add(config.curl);
         if (config.OS.equals("windows")) {
            command.add("--retry");
            command.add("3");
         }
         command.add("--anyauth");
         command.add("--globoff");
         command.add("--user");
         command.add("tivo:" + config.MAK);
         command.add("--insecure");
         command.add("--cookie-jar");
         command.add(cookieFile);
         command.add("--url");
         command.add(urlString);
         command.add("--output");
         command.add(outputFile);
         process = new backgroundProcess();
         fetchCount++;
         if (AnchorOffset == 0)
            log.print(">> Getting Now Playing List from " + job.tivoName + " ...");
         else
            log.print(">> Continuing Now Playing List from " + job.tivoName + " (" + AnchorOffset + "/" + TotalItems + ")...");
         if ( process.run(command) ) {
            log.print(process.toString());
         } else {
            log.error("Failed to start command: " + process.toString());
            process.printStderr();
            process = null;
            jobMonitor.removeFromJobList(job);
            return false;
         }
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
      } else {
         process.kill();
         log.warn("Killing '" + job.type + "' job: " + process.toString());
         file.delete(cookieFile);
         file.delete(outputFile);
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
               }
            }
         }
      } else {
         // Traditonal curl job handling
         if (process == null) {
            // Error starting the job
            jobMonitor.removeFromJobList(job);
            return false;
         }
         int exit_code = process.exitStatus();
         if (exit_code == -1) {
            // Still running
            if (config.GUIMODE) {
               // Update STATUS column
               String t = jobMonitor.getElapsedTime(job.time);
               config.gui.jobTab_UpdateJobMonitorRowStatus(job, t);
               if ( jobMonitor.isFirstJobInMonitor(job) ) {
                  String title = String.format("playlist: %s %s", t, config.kmttg);
                  config.gui.setTitle(title);
               }
            }
            return true;
         } else {
            // Job finished
            if (config.GUIMODE) {
               if ( jobMonitor.isFirstJobInMonitor(job) ) {
                  config.gui.setTitle(config.kmttg);
               }
            }
            
            // NOTE: Not removing from job list yet as there could be more runs needed
            
            // Check for problems
            int failed = 0;
            
            // exit code != 0 => trouble
            if (exit_code != 0) {
               failed = 1;
            }
            
            // No or empty output means problems         
            if ( file.isEmpty(outputFile) ) {
               failed = 1;
            }
            
            // Check that first line is xml
            if (failed == 0) {
               try {
                  BufferedReader xml = new BufferedReader(new FileReader(outputFile));
                  String first = xml.readLine();
                  if ( ! first.toLowerCase().matches("^.+xml.+$") ) {
                     failed = 1;
                     log.error(first);
                  }
                  xml.close();
               }
               catch (IOException ex) {
                  failed = 1;
               }
            }
            
            if (failed == 1) {
               log.error("Failed to retrieve Now Playing List from " + job.tivoName);
               log.error("Exit code: " + exit_code);
               log.error("Check YOUR MAK & IP settings");
               process.printStderr();
               jobMonitor.removeFromJobList(job);
            } else {
               log.warn("NPL job completed: " + jobMonitor.getElapsedTime(job.time));
               log.print("---DONE--- job=" + job.type + " tivo=" + job.tivoName);
               
               // Success, so parse the result
               return parseNPL(outputFile);
            }
         }
         file.delete(cookieFile);
         file.delete(outputFile);
      }
      
      return false;
   }

   // Return true if additional downloads needed, false otherwise
   // NOTE: Must use UTF8 for special characters like Spanish/French characters
   private Boolean parseNPL(String file) {
      debug.print("file=" + file);
      Hashtable<String,Integer> result = parseNPL.parseFile(file, job.tivoName, ENTRIES);
      if (result == null) {
         com.tivo.kmttg.util.file.delete(cookieFile);
         com.tivo.kmttg.util.file.delete(outputFile);
         return false;
      }
      TotalItems = result.get("TotalItems");
      
      Boolean done = true;
      
      if ( result.get("ItemCount") > 0 && (AnchorOffset + result.get("ItemCount")) < result.get("TotalItems") )
         done = false;
      
      if (limit_npl_fetches > 0 && fetchCount >= limit_npl_fetches) {
         if ( ! done )
            log.warn(job.tivoName + ": Further NPL listings not obtained due to fetch limit=" + limit_npl_fetches + " exceeded.");
         done = true;
      }
      
      if ( ! done ) {
         // Additional items to retrieve
         AnchorOffset += result.get("ItemCount");
         start();
         return true;
      } else {
         // Done
         jobMonitor.removeFromJobList(job);
         ENTRIES = parseNPL.uniquify(ENTRIES, unique);
         if (config.GUI_AUTO > 0) {
            // Update NPL
            config.gui.nplTab_SetNowPlaying(job.tivoName, ENTRIES);
            if (! config.rpcEnabled(job.tivoName) && ! config.mindEnabled(job.tivoName))
               auto.processAll(job.tivoName, ENTRIES);
         }
         else if (config.GUIMODE) {
            // GUI mode: populate NPL table
            config.gui.nplTab_SetNowPlaying(job.tivoName, ENTRIES);
         } else {
            // Batch mode
            if (! config.rpcEnabled(job.tivoName) && ! config.mindEnabled(job.tivoName))
               auto.processAll(job.tivoName, ENTRIES);            
         }
         com.tivo.kmttg.util.file.delete(cookieFile);
         com.tivo.kmttg.util.file.delete(outputFile);
         
         if (config.rpcEnabled(job.tivoName) || config.mindEnabled(job.tivoName)) {
            // Extra rpc communication to retrieve NPL information
            // used to be able to play/delete shows.
            rnpl.rnplListCB(job.tivoName, ENTRIES);
         }
         return false;
      }
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

