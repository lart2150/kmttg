package com.tivo.kmttg.task;

import java.io.Serializable;
import java.util.Date;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class remote implements Serializable {
   private static final long serialVersionUID = 1L;
   private Thread thread = null;
   private Boolean thread_running = false;
   private jobData job;
   private Boolean success = false;
   private backgroundProcess process;
   private JSONArray data = null;
   private String jobName;
   
   public remote(jobData job) {
      this.job = job;
   }   
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      
      if (! config.TIVOS.containsKey(job.tivoName)) {
         log.error("Cannot determine IP for TiVo named: " + job.tivoName);
         return false;
      }
               
      if ( start() ) {
         job.process_remote = this;
         jobMonitor.updateJobStatus(job, "running");
         job.time = new Date().getTime();
      }
      return true;
   }

   private Boolean start() {
      debug.print("");
      // Run Remote in a separate thread
      class AutoThread implements Runnable {
         AutoThread() {}       
         public void run () {
            Remote r = new Remote(job.tivoName);
            if (r.success) {
               if (job.remote_todo)
                  data = r.ToDo();
               if (job.remote_sp)
                  data = r.SeasonPasses();
               if (job.remote_cancel)
                  data = r.CancelledShows();
               if (job.remote_rnpl)
                  data = r.MyShows();
               if (job.remote_channels)
                  data = r.ChannelList();
               if (job.remote_premiere)
                  data = r.SeasonPremieres(config.gui.remote_gui.getSelectedChannelData(job.tivoName), job);
               if (data != null) {
                  success = true;
               } else {
                  success = false;
               }
            }
            thread_running = false;
         }
      }
      thread_running = true;
      AutoThread t = new AutoThread();
      thread = new Thread(t);
      jobName = "REMOTE";
      if (job.remote_todo)     jobName += " ToDo List";
      if (job.remote_sp)       jobName += " Season Pass List";
      if (job.remote_cancel)   jobName += " Will Not Record List";
      if (job.remote_rnpl)     jobName += " NP List";
      if (job.remote_channels) jobName += " Channels List";
      if (job.remote_premiere) jobName += " Season Premieres";
      log.print(">> RUNNING '" + jobName + "' JOB FOR TiVo: " + job.tivoName);
      thread.start();

      return true;
   }
   
   public void kill() {
      debug.print("");
      thread.interrupt();
      log.warn("Killing '" + jobName + "' TiVo: " + job.tivoName);
      thread_running = false;
      success = false;
   }
   
   // Check status of a currently running job
   // Returns true if still running, false if job finished
   public Boolean check() {
      if (thread_running) {
         // Still running
         if (config.GUIMODE && ! job.remote_premiere) {
            // Update STATUS column
            config.gui.jobTab_UpdateJobMonitorRowStatus(job, "running");
         }
         return true;
      } else {
         // Job finished
         jobMonitor.removeFromJobList(job);
         if (success) {
            if (job.remote_todo & job.todo != null) {
               // ToDo list job => populate ToDo table
               job.todo.AddRows(job.tivoName, data);
            }
            if (job.remote_sp & job.sp != null) {
               // SP job => populate SP table
               job.sp.AddRows(job.tivoName, data);
            }
            if (job.remote_cancel & job.cancelled != null) {
               job.cancelled.AddRows(job.tivoName, data);
            }
            if (job.remote_rnpl) {
               rnpl.setNPLData(job.tivoName, data);
            }
            if (job.remote_channels && data != null) {
               config.gui.remote_gui.putChannelData(job.tivoName, data);
            }
            if (job.remote_premiere & job.premiere != null) {
               job.premiere.AddRows(job.tivoName, data);
            }

            log.warn("REMOTE job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE--- job='" + jobName + "' TiVo=" + job.tivoName);
         }
      }
      return false;
   }

}
