package com.tivo.kmttg.task;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.main.auto;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.*;

public class NowPlaying implements Serializable {
   private static final long serialVersionUID = 1L;
   private Stack<Hashtable<String,String>> ENTRIES = new Stack<Hashtable<String,String>>();
   private String cookieFile = "";
   private String outputFile = "";
   private int AnchorOffset = 0;
   private int TotalItems = 0;
   private static int limit_npl_fetches = 0;
   private int fetchCount = 0;
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
      limit_npl_fetches = config.getLimitNplSetting(tivoName);
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
      
      if ( (AnchorOffset + result.get("ItemCount")) < result.get("TotalItems") )
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
         if (config.GUI_AUTO > 0) {
            // Update NPL
            config.gui.nplTab_SetNowPlaying(job.tivoName, ENTRIES);
            if (! config.rpcEnabled(job.tivoName))
               auto.processAll(job.tivoName, ENTRIES);
         }
         else if (config.GUIMODE) {
            // GUI mode: populate NPL table
            config.gui.nplTab_SetNowPlaying(job.tivoName, ENTRIES);
         } else {
            // Batch mode
            if (! config.rpcEnabled(job.tivoName))
               auto.processAll(job.tivoName, ENTRIES);            
         }
         com.tivo.kmttg.util.file.delete(cookieFile);
         com.tivo.kmttg.util.file.delete(outputFile);
         
         if (config.rpcEnabled(job.tivoName)) {
            // Extra iPad communication to retrieve NPL information
            // used to be able to play/delete shows. Only works for Premiere or
            // later models.
            rnpl.rnplListCB(job.tivoName, ENTRIES);
         }
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
}

