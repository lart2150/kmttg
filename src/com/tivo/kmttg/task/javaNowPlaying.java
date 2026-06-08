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

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.gui.swing.SwingUtil;
import com.tivo.kmttg.main.auto;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.http;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.parseNPL;

public class javaNowPlaying extends baseTask implements Serializable {
   private static final long serialVersionUID = 1L;
   private Thread thread = null;
   private Boolean thread_running = false;
   private Boolean success = false;
   private Stack<Hashtable<String,String>> ENTRIES = new Stack<Hashtable<String,String>>();
   private Hashtable<String,Integer> unique = new Hashtable<String,Integer>();
   private ByteArrayOutputStream nplStream;
   private int AnchorOffset = 0;
   private int TotalItems = 0;
   private static int limit_npl_fetches = 0;
   private int fetchCount = 0;
   private backgroundProcess process;
   public  jobData job;

   public javaNowPlaying(jobData job) {
      debug.print("job=" + job);
      this.job = job;
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
         urlString += "/TiVoConnect?Command=QueryContainer&Container=/NowPlaying&Recurse=Yes&ItemCount=32&AnchorOffset=";
      }
      urlString += AnchorOffset;
      final String url = urlString;
      fetchCount++;
      if (AnchorOffset == 0)
         log.print(">> Getting Now Playing List from " + job.tivoName + " ...");
      else
         log.print(">> Continuing Now Playing List from " + job.tivoName + " (" + AnchorOffset + "/" + TotalItems + ")...");
      log.print(url);
      // Run download method in a separate thread
      nplStream = new ByteArrayOutputStream();
      Runnable r = new Runnable() {
         public void run () {
            try {
               success = http.downloadPiped(url, "tivo", config.MAK, nplStream, false, job.offset);
               thread_running = false;
            }
            catch (Exception e) {
               success = false;
               thread_running = false;
               Thread.currentThread().interrupt();
            }
         }
      };
      thread_running = true;
      thread = new Thread(r);
      thread.start();

      return true;
   }
   
   public void kill() {
      debug.print("");
      thread.interrupt();
      log.warn("Killing '" + job.type + "' TiVo: " + job.tivoName);
      thread_running = false;
   }
   
   // Check status of a currently running job
   // Returns true if still running, false if job finished
   public Boolean check() {
      if (thread_running) {
         // Still running
         if (config.GUIMODE) {
            // Update STATUS column
            final String t = jobMonitor.getElapsedTime(job.time);
            SwingUtil.runLater(new Runnable() {
               @Override public void run() {
                  config.gui.jobTab_UpdateJobMonitorRowStatus(job, t);
                  if ( jobMonitor.isFirstJobInMonitor(job) ) {
                     String title = String.format("playlist: %s %s", t, config.kmttg);
                     config.gui.setTitle(title);
                  }
               }
            });
         }
         return true;
      } else {
         // Job finished
         if (config.GUIMODE) {
            if ( jobMonitor.isFirstJobInMonitor(job) ) {
               SwingUtil.runLater(new Runnable() {
                  @Override public void run() {
                     config.gui.setTitle(config.kmttg);
                  }
               });
            }
         }
         
         // NOTE: Not removing from job list yet as there could be more runs needed
         
         // Check for problems
         int failed = 0;

         String npl = null;
         if ( success && nplStream != null ) {
            try {
               npl = nplStream.toString("UTF8");
            }
            catch (UnsupportedEncodingException ex) {
               failed = 1;
            }
         }

         if ( ! success ) {
            failed = 1;
         }

         // No or empty output means problems
         if ( npl == null || npl.length() == 0 ) {
            failed = 1;
         }

         // Check that first line is xml
         if (failed == 0) {
            String first = npl.split("\\r?\\n", 2)[0];
            if ( ! first.toLowerCase().matches("^.+xml.+$") ) {
               failed = 1;
               log.error(first);
            }
         }

         if (failed == 1) {
            log.error("Failed to retrieve Now Playing List from " + job.tivoName);
            log.error("Check YOUR MAK & IP settings");
            jobMonitor.removeFromJobList(job);
         } else {
            log.warn("NPL job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE--- job=" + job.type + " tivo=" + job.tivoName);

            // Success, so parse the result
            return parseNPL(npl);
         }
      }

      return false;
   }

   // Return true if additional downloads needed, false otherwise
   // NOTE: Must use UTF8 for special characters like Spanish/French characters
   private Boolean parseNPL(String npl) {
      debug.print("");
      Hashtable<String,Integer> result = parseNPL.parseString(npl, job.tivoName, ENTRIES);
      if (result == null) {
         return false;
      }
      TotalItems = result.get("TotalItems");
      
      Boolean done = true;
      
      if ( result.get("ItemCount") > 0 && (AnchorOffset + result.get("ItemCount")) < result.get("TotalItems") ) {
         done = false;
      }
      
      if (limit_npl_fetches > 0 && fetchCount >= limit_npl_fetches) {
         if ( ! done )
            log.warn(job.tivoName + ": Further NPL listings not obtained due to fetch limit=" + limit_npl_fetches + " exceeded.");
         done = true;
      }
      
      if (! done ) {
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
            SwingUtil.runLater(new Runnable() {
               @Override public void run() {
                  config.gui.nplTab_SetNowPlaying(job.tivoName, ENTRIES);
               }
            });
            if (! config.rpcEnabled(job.tivoName) && ! config.mindEnabled(job.tivoName))
               auto.processAll(job.tivoName, ENTRIES);
         }
         else if (config.GUIMODE) {
            // GUI mode: populate NPL table
            SwingUtil.runLater(new Runnable() {
               @Override public void run() {
                  config.gui.nplTab_SetNowPlaying(job.tivoName, ENTRIES);
               }
            });
         } else {
            // Batch mode
            if (! config.rpcEnabled(job.tivoName) && ! config.mindEnabled(job.tivoName))
               auto.processAll(job.tivoName, ENTRIES);
         }

         if (config.rpcEnabled(job.tivoName) || config.mindEnabled(job.tivoName)) {
            // Extra rpc communication to retrieve NPL information
            // used to be able to play/delete shows.
            rnpl.rnplListCB(job.tivoName, ENTRIES);
         }
         return false;
      }
   }   
}
