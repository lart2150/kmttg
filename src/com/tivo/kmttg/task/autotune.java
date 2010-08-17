package com.tivo.kmttg.task;

import java.io.Serializable;
import java.util.Date;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.main.telnet;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class autotune implements Serializable {
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
      
      if (config.autotune == null) {
         return false;
      }
      
      if ( ! config.autotune.containsKey(job.tivoName) ) {
         return false;
      }
      
      String[] keys = {"channel_interval", "button_interval", "chan1", "chan2"};
      for (int i=0; i<keys.length; ++i) {
         if ( ! config.autotune.get(job.tivoName).containsKey(keys[i]) ) {
            return false;
         }
      }
               
      if ( start() ) {
         job.process_autotune = this;
         jobMonitor.updateJobStatus(job, "running");
         job.time = new Date().getTime();
      }
      return true;
   }

   private Boolean start() {
      debug.print("");
      // Run telnet in a separate thread
      class AutoThread implements Runnable {
         AutoThread() {}       
         public void run () {
            String[] seq1 = sequenceFromChannel(config.autotune.get(job.tivoName).get("chan1"));
            String[] seq2 = sequenceFromChannel(config.autotune.get(job.tivoName).get("chan2"));
            int channel_interval = Integer.getInteger(config.autotune.get(job.tivoName).get("channel_interval"));
            int button_interval = Integer.getInteger(config.autotune.get(job.tivoName).get("button_interval"));
            telnet t = new telnet(IP, channel_interval, button_interval, seq1, seq2);
            success = t.success;
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
         if (config.GUI) {
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
   
   // LIVETV,CLEAR,CHANNEL DIGIT 1, CHANNEL DIGIT 2...,ENTER
   public String[] sequenceFromChannel(String channel) {
      return null;
   }

}
