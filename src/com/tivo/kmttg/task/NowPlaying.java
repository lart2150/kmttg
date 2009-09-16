package com.tivo.kmttg.task;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.main.auto;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.*;

public class NowPlaying  {
   private Stack<Hashtable<String,String>> ENTRIES = new Stack<Hashtable<String,String>>();
   private String cookieFile = "";
   private String outputFile = "";
   private int AnchorOffset = 0;
   private backgroundProcess process;
   public  jobData job;
   
   public NowPlaying(jobData job) {
      debug.print("job=" + job);
      this.job = job;
      
      // Generate unique cookieFile and outputFile names
      cookieFile = file.makeTempFile("cookie");
      outputFile = file.makeTempFile("NPL");
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public static Boolean submitJob(String tivoName) {
      debug.print("");
      String ip = config.TIVOS.get(tivoName);
      if (ip == null || ip.length() == 0) {
         log.error("IP not defined for tivo: " + tivoName);
         return false;
      }
      jobData job = new jobData();
      job.tivoName           = tivoName;
      job.type               = "playlist";
      job.name               = "curl";
      job.ip                 = ip;
      jobMonitor.submitNewJob(job);
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
      Stack<String> command = new Stack<String>();
      String urlString = "https://";
      urlString += job.ip;
      // Enable testLimit only for testing multilple downloads
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
      log.print(">> Getting Now Playing List from " + job.tivoName + " ...");
      if ( process.run(command) ) {
         log.print(process.toString());
      } else {
         log.error("Failed to start command: " + process.toString());
         process.printStderr();
         process = null;
         jobMonitor.removeFromJobList(job);
         return false;
      }
      return true;
   }
   
   public void kill() {
      debug.print("");
      process.kill();
      log.warn("Killing '" + job.type + "' job: " + process.toString());
      file.delete(cookieFile);
      file.delete(outputFile);
   }
   
   // Check status of a currently running job
   // Returns true if still running, false if job finished
   // If job is finished then check result
   public Boolean check() {
      //debug.print("");
      if (process == null) {
         // Error starting the job
         jobMonitor.removeFromJobList(job);
         return false;
      }
      
      int exit_code = process.exitStatus();
      if (exit_code == -1) {
         // Still running
         if (config.GUI) {
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
         if (config.GUI) {
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
            log.print("---DONE---");
            
            // Success, so parse the result
            return parseNPL(outputFile);
         }
      }
      file.delete(cookieFile);
      file.delete(outputFile);
      
      return false;
   }

   // Return true if additional downloads needed, false otherwise
   private Boolean parseNPL(String file) {
      debug.print("file=" + file);
      int TotalItems=0, ItemCount=0, offset=0;
      String ll, l, value;
      Hashtable<String,String> h = new Hashtable<String,String>();
      try {
         BufferedReader xml = new BufferedReader(new FileReader(outputFile));
         ll = xml.readLine();
         xml.close();
         String[] line = ll.split(">");
         // First get TotalItems & ItemCount
         int go = 1;
         while (go == 1) {
            if (offset < line.length) {
               l = line[offset++];
               if (l.matches("^<TotalItems.*$")) {
                  value = line[offset].replaceFirst("^(.+)<\\/.+$", "$1");
                  TotalItems = Integer.parseInt(value);
                  debug.print("TotalItems=" + TotalItems);
               }
               if (l.matches("^<ItemCount.*$")) {
                  value = line[offset].replaceFirst("^(.+)<\\/.+$", "$1");
                  ItemCount = Integer.parseInt(value);
                  go = 0;
                  debug.print("ItemCount=" + ItemCount);
               }
            } else {
               go = 0;
            }
         }
         
         // Now parse all items tagged with <Item>
         for (int j=offset; j<line.length; ++j) {            
            l = line[j];
            if (l.matches("^<Item.*$")) {
               // Start of a new program item
               if ( ! h.isEmpty() ) {
                  if (h.containsKey("episodeTitle")) {
                     h.put("title", h.get("title") + " - " + h.get("episodeTitle"));
                  }
                  h.put("tivoName", job.tivoName);
                  ENTRIES.add(h);
               }
               h = new Hashtable<String,String>();
            }

            // Title
            if (l.matches("^<Title.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               value = Entities.replaceHtmlEntities(value);
               h.put("title", value);
               h.put("titleOnly", value);
            }

            // Copy Protected
            if (l.matches("^<CopyProtected.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               h.put("CopyProtected", value);
            }

            // Size
            if (l.matches("^<SourceSize.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               h.put("size", "" + Long.parseLong(value));
               double GB = Math.pow(2,30);
               h.put("sizeGB", String.format("%.2f GB", Float.parseFloat(value)/GB));
            }

            // Duration
            if (l.matches("^<Duration.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               h.put("duration", value);
            }

            // CaptureDate
            if (l.matches("^<CaptureDate.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               h.put("gmt_hex", value);
               String hex = value;
               hex = hex.replaceFirst("^0x(.+)$", "$1");
               int dec = Integer.parseInt(hex,16);
               long gmt = (long)dec*1000;
               h.put("gmt", "" + gmt);
               h.put("date", getTime(gmt));
               h.put("date_long", getDetailedTime(gmt));
            }
            
            // EpisodeTitle
            if (l.matches("^<EpisodeTitle.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               value = Entities.replaceHtmlEntities(value);
               h.put("episodeTitle", value);
            }            

            // EpisodeNumber (store as 3 digits)
            if (l.matches("^<EpisodeNumber.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               value = Entities.replaceHtmlEntities(value);
               h.put("EpisodeNumber", String.format("%03d", Integer.parseInt(value)));
            }

            // Description
            if (l.matches("^<Description.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               value = Entities.replaceHtmlEntities(value);
               value = value.replaceFirst("Copyright Tribune Media Services, Inc.", "");
               h.put("description", value);
            }

            // Channel #
            if (l.matches("^<SourceChannel.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               h.put("channelNum", value);
               if (value.matches("^.+-.+$")) {
                  int major = Integer.parseInt(value.replaceFirst("(.+)-.+$", "$1"));
                  int minor = Integer.parseInt(value.replaceFirst(".+-(.+)$", "$1"));
                  value = String.format("%d.%03d", major, minor);
               }
               h.put("sortableChannel", value);
            }

            // Channel Name
            if (l.matches("^<SourceStation.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               h.put("channel", value);
            }

            // Recording in progress
            if (l.matches("^<InProgress.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               h.put("InProgress", value);
            }

            // HD tag
            if (l.matches("^<HighDefinition.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               h.put("HD", value);
            }

            // ProgramId
            if (l.matches("^<ProgramId.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               h.put("ProgramId", value);
            }

            // SeriesId
            if (l.matches("^<SeriesId.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               h.put("SeriesId", value);
            }

            // ByteOffset
            if (l.matches("^<ByteOffset.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               h.put("ByteOffset", value);
            }

            // URL
            if (l.matches("^<Url.*$")) {
               j++;
               value = line[j];
               // Download URL
               if (value.matches("^.*download.*$")) {
                  value = value.replaceFirst("^(.+)<\\/.+$", "$1");
                  value = value.replaceFirst("&amp;", "&");
                  if (config.wan_http_port.length() > 0) {
                     value = value.replaceFirst(":80", ":" + config.wan_http_port);
                  }
                  h.put("url", value);
               }
               
               // TivoVideoDetails URL
               if (value.matches("^.*TiVoVideoDetails.*$")) {
                  value = value.replaceFirst("^(.+)<\\/.+$", "$1");
                  value = value.replaceFirst("&amp;", "&");
                  h.put("url_TiVoVideoDetails", value);
               }
               
               // Expiration Image Type
               if (value.matches("^.*save-until-i-delete-recording.*$")) {
                  h.put("ExpirationImage", "save-until-i-delete-recording");
               }
               if (value.matches("^.*in-progress-recording.*$")) {
                  h.put("ExpirationImage", "in-progress-recording");
               }
               if (value.matches("^.*in-progress-transfer.*$")) {
                  h.put("ExpirationImage", "in-progress-transfer");
               }
               if (value.matches("^.*expires-soon-recording.*$")) {
                  h.put("ExpirationImage", "expires-soon-recording");
               }
               if (value.matches("^.*expired-recording.*$")) {
                  h.put("ExpirationImage", "expired-recording");
               }
               if (value.matches("^.*suggestion-recording.*$")) {
                  h.put("ExpirationImage", "suggestion-recording");
               }
            }
            
            // Set copy-protect icon if copy-protected
            if (h.containsKey("CopyProtected")) {
               h.put("ExpirationImage", "copy-protected");
            }
         }
         // Add last entry
         if ( ! h.isEmpty() ) {
            if (h.containsKey("episodeTitle")) {
               h.put("title", h.get("title") + " - " + h.get("episodeTitle"));
            }
            h.put("tivoName", job.tivoName);
            ENTRIES.add(h);
         }
      }
      catch (IOException ex) {
         log.error(ex.toString());
         com.tivo.kmttg.util.file.delete(cookieFile);
         com.tivo.kmttg.util.file.delete(outputFile);
         return false;
      }
      
      if ( (AnchorOffset + ItemCount) < TotalItems ) {
         // Additional items to retrieve
         AnchorOffset += ItemCount;
         start();
         return true;
      } else {
         // Done
         jobMonitor.removeFromJobList(job);
         if (config.GUI_AUTO > 0) {
            // Clear NPL
            config.gui.nplTab_clear(job.tivoName);
            
            // Match auto keywords against entries
            int count = 0;
            for (int j=0; j<ENTRIES.size(); j++) {
               if ( auto.keywordSearch(ENTRIES.get(j)) )
                  count++;
            }
            log.print("TOTAL auto matches for '" + job.tivoName + "' = " + count + "/" + ENTRIES.size());
            config.GUI_AUTO--;
         }
         else if (config.GUI) {
            // GUI mode: populate NPL table
            config.gui.nplTab_SetNowPlaying(job.tivoName, ENTRIES);
         } else {
            // Batch mode
            int count = 0;
            for (int j=0; j<ENTRIES.size(); j++) {
               if ( auto.keywordSearch(ENTRIES.get(j)) )
                  count++;
            }
            log.print("TOTAL auto matches for '" + job.tivoName + "' = " + count + "/" + ENTRIES.size());
         }
         com.tivo.kmttg.util.file.delete(cookieFile);
         com.tivo.kmttg.util.file.delete(outputFile);
         return false;
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
      
   private String getTime(long gmt) {
      debug.print("gmt=" + gmt);
      SimpleDateFormat sdf = new SimpleDateFormat("E MM/dd/yyyy");
      return sdf.format(gmt);
   }
   
   private String getDetailedTime(long gmt) {
      debug.print("gmt=" + gmt);
      SimpleDateFormat sdf = new SimpleDateFormat("E MM/dd/yyyy hh:mm aa");
      return sdf.format(gmt);
   }
}

