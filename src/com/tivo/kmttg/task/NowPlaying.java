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
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
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
         for (int i=0; i<data.length(); ++i) {
            JSONObject json = data.getJSONObject(i).getJSONArray("recording").getJSONObject(0);
            Hashtable<String,String> entry = parseNPL.rpcToHashEntry(job.tivoName, json);
            if (entry != null)
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

