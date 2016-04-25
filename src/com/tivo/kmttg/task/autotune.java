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
import java.util.Date;
import java.util.Hashtable;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.main.telnet;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class autotune extends baseTask implements Serializable {
   private static final long serialVersionUID = 1L;
   private Thread thread = null;
   private Boolean thread_running = false;
   private String IP = null;
   private jobData job;
   private Boolean success = false;
   private backgroundProcess process;

   public autotune(jobData job) {
      this.job = job;
   }   
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      
      if (config.TIVOS.containsKey(job.tivoName)) {
         IP = config.TIVOS.get(job.tivoName);
      } else {
         log.error("Cannot determine IP for TiVo named: " + job.tivoName);
         return false;
      }
      
      // No configuration => nothing to do
      if ( ! isConfigured(job.tivoName) && job.autotune_chan1 == null ) {
         log.error("autotune missing configuration or disabled");
         return false;
      }
            
      // Check that autotune TiVo hash contains the required keys
      String[] keys = getRequiredElements();
      for (int i=0; i<keys.length; ++i) {
         if ( ! config.autotune.get(job.tivoName).containsKey(keys[i]) ) {
            log.error("autotune missing configuration item: " + keys[i]);
            return false;
         }
      }
               
      if ( start() ) {
         job.process = this;
         jobMonitor.updateJobStatus(job, "running");
         job.time = new Date().getTime();
      }
      return true;
   }

   public Boolean start() {
      debug.print("");
      // Run telnet in a separate thread
      // Send sequence of channel changes for both tuners
      class AutoThread implements Runnable {
         AutoThread() {}       
         public void run () {
            String[] seq1 = sequenceFromChannel("chan1");
            String[] seq2 = sequenceFromChannel("chan2");
            int channel_interval = getInterval("channel_interval");
            int button_interval  = getInterval("button_interval");
            if (seq1 != null && seq2 != null && channel_interval != -1 && button_interval != -1) {
               telnet t = new telnet(IP, channel_interval, button_interval, seq1, seq2);
               success = t.success;
            }
            thread_running = false;
         }
      }
      thread_running = true;
      AutoThread t = new AutoThread();
      thread = new Thread(t);
      log.print(">> RUNNING AUTOTUNE FOR TiVo: " + job.tivoName);
      thread.start();

      return true;
   }
   
   public void kill() {
      debug.print("");
      thread.interrupt();
      log.warn("Killing '" + job.type + "' TiVo: " + job.tivoName);
      thread_running = false;
      success = false;
   }
   
   // Check status of a currently running job
   // Returns true if still running, false if job finished
   public Boolean check() {
      if (thread_running) {
         // Still running
         if (config.GUIMODE) {
            // Update STATUS column
            config.gui.jobTab_UpdateJobMonitorRowStatus(job, "running");
         }
         return true;
      } else {
         // Job finished
         jobMonitor.removeFromJobList(job);
         if (success) {
            log.warn("autotune job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE--- job=" + job.type + " TiVo=" + job.tivoName);
         }
      }
      return false;
   }
   
   // Example: 922 -> LIVETV,CLEAR,9,2,2,ENTER
   // Example: 9-1 -> LIVETV,CLEAR,9,ADVANCE,1
   private String[] sequenceFromChannel(String channelNum) {
      String channel;
      if (job.autotune_chan1 != null && channelNum.equals("chan1"))
         channel = job.autotune_chan1;
      else if (job.autotune_chan2 != null && channelNum.equals("chan2"))
         channel = job.autotune_chan2;
      else
         channel = config.autotune.get(job.tivoName).get(channelNum);
      int index = 0;
      char[] digits = channel.toCharArray();
      String[] seq = new String[digits.length+3];
      seq[index++] = "LIVETV";
      seq[index++] = "CLEAR";
      for (int i=0; i<digits.length; i++) {
         seq[index++] = "" + digits[i];
      }
      seq[index++] = "ENTER";
      return seq;
   }
   
   private int getInterval(String name) {
      int interval = -1;
      try {
         if (job.autotune_channel_interval != -1 && name.equals("channel_interval")) {
            interval = job.autotune_channel_interval;
         } else if (job.autotune_button_interval != -1 && name.equals("button_interval")) {
            interval = job.autotune_channel_interval;
         } else {
            interval = Integer.parseInt(
               string.removeLeadingTrailingSpaces(config.autotune.get(job.tivoName).get(name))
            );
         }
      } catch (Exception e) {
         log.error("autotune error determining " + job.tivoName + " parameter: " + name + " - " + e.getMessage());
      }
      return interval;
   }
      
   // Return true if given tivoName has valid autotune configuration
   public static Boolean isConfigured(String tivoName) {
      init(tivoName);
      return config.autotune.get(tivoName).get("enabled").equals("true");
   }
   
   public static void init(String tivoName) {
      if (config.autotune == null) config.autotune = new Hashtable<String,Hashtable<String,String>>();
      if (tivoName != null) {
         if ( ! config.autotune.containsKey(tivoName))
            config.autotune.put(tivoName, new Hashtable<String,String>());
         if ( ! config.autotune.get(tivoName).containsKey("enabled"))
            config.autotune.get(tivoName).put("enabled", "false");
         if ( ! config.autotune.get(tivoName).containsKey("channel_interval"))
            config.autotune.get(tivoName).put("channel_interval", "5");
         if ( ! config.autotune.get(tivoName).containsKey("button_interval"))
            config.autotune.get(tivoName).put("button_interval", "1000");
         if ( ! config.autotune.get(tivoName).containsKey("chan1"))
            config.autotune.get(tivoName).put("chan1", "0");
         if ( ! config.autotune.get(tivoName).containsKey("chan2"))
            config.autotune.get(tivoName).put("chan2", "1");
      }
   }
   
   public static void enable(String tivoName) {
      if (tivoName != null) {
         init(tivoName);
         config.autotune.get(tivoName).put("enabled", "true");
      }
   }
   
   public static void disable(String tivoName) {
      if (tivoName != null) {
         init(tivoName);
         config.autotune.get(tivoName).put("enabled", "false"); 
      }
   }
   
   public static String[] getRequiredElements() {
      return new String[] {"enabled", "channel_interval", "button_interval", "chan1", "chan2"};
   }

}
