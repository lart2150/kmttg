package com.tivo.kmttg.task;

import java.io.Serializable;
import java.util.Date;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.gui.TableUtil;
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
                  data = r.ToDo(job);
               if (job.remote_upcoming)
                  data = r.Upcoming(job);
               if (job.remote_conflicts)
                  data = r.Upcoming(job);
               if (job.remote_sp)
                  data = r.SeasonPasses(job);
               if (job.remote_spreorder)
                  data = r.SPReorder(job);
               if (job.remote_cancel) {
                  data = r.CancelledShows(job);
                  if (data != null && config.gui.remote_gui.all_todo.size() == 0) {
                     log.warn("Obtaining todo lists");
                     config.gui.remote_gui.all_todo = config.gui.remote_gui.getTodoLists("Cancel");
                  }
               }
               if (job.remote_deleted)
                  data = r.DeletedShows(job);
               if (job.remote_rnpl)
                  data = r.MyShows(job);
               if (job.remote_channels)
                  data = r.ChannelList(job);
               if (job.remote_premiere)
                  data = r.SeasonPremieres(
                     config.gui.remote_gui.getSelectedChannelData(job.tivoName),
                     job, config.gui.remote_gui.getPremiereDays()
                  );
               if (job.remote_search) {
                  data = r.searchKeywords(job.remote_search_keyword, job, job.remote_search_max);
                  if (data != null && config.gui.remote_gui.all_todo.size() == 0) {
                     log.warn("Obtaining todo lists");
                     config.gui.remote_gui.all_todo = config.gui.remote_gui.getTodoLists("Search");
                  }
               }
               if (job.remote_guideChannels) {
                  data = r.ChannelList(job);
                  if (config.gui.remote_gui.all_todo.size() == 0) {
                     log.warn("Obtaining todo lists");
                     config.gui.remote_gui.all_todo = config.gui.remote_gui.getTodoLists("Guide");
                  }
               }
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
      if (job.remote_todo)          jobName += " ToDo List";
      if (job.remote_upcoming)      jobName += " Upcoming List";
      if (job.remote_conflicts)     jobName += " Conflicts List";
      if (job.remote_sp)            jobName += " Season Pass List";
      if (job.remote_spreorder)     jobName += " Season Pass Re-order";
      if (job.remote_cancel)        jobName += " Will Not Record List";
      if (job.remote_deleted)       jobName += " Deleted List";
      if (job.remote_rnpl)          jobName += " NP List";
      if (job.remote_channels)      jobName += " Channels List";
      if (job.remote_premiere)      jobName += " Season Premieres";
      if (job.remote_search)        jobName += " Keyword Search";
      if (job.remote_guideChannels) jobName += " Guide Channel List";
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
         if (config.GUIMODE && ! job.remote_premiere &&
             ! job.remote_cancel && ! job.remote_deleted && ! job.remote_search ) {
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
            if (job.remote_todo && job.todo != null) {
               // ToDo list job => populate ToDo table
               job.todo.AddRows(job.tivoName, data);
            }
            if (job.remote_upcoming && job.todo != null) {
               // Upcoming list job => populate ToDo table
               job.todo.AddRows(job.tivoName, data);
               // Make the ToDo tab the currently selected tab
               config.gui.remote_gui.getPanel().setSelectedIndex(0);
            }
            if (job.remote_conflicts && job.cancelled != null) {
               // Conflicts list job => populate Won't Record table
               job.cancelled.AddRows(job.tivoName, data);
               // Make the Won't Record tab the currently selected tab
               config.gui.remote_gui.getPanel().setSelectedIndex(2);
               // Enter the 1st folder in Won't Record table
               job.cancelled.enterFirstFolder();
            }
            if (job.remote_sp && job.sp != null) {
               // SP job => populate SP table
               job.sp.AddRows(job.tivoName, data);
            }
            if (job.remote_spreorder && data != null) {
               // Refresh SP list for TiVo SPs that were just re-ordered
               config.gui.remote_gui.clearTable("sp");
               config.gui.remote_gui.setTivoName("sp", job.tivoName);
               config.gui.remote_gui.SPListCB(job.tivoName);
            }
            if (job.remote_cancel && job.cancelled != null) {
               job.cancelled.AddRows(job.tivoName, data);
            }
            if (job.remote_deleted && job.deleted != null) {
               job.deleted.AddRows(job.tivoName, data);
            }
            if (job.remote_rnpl) {
               rnpl.setNPLData(job.tivoName, data);
            }
            if (job.remote_channels && data != null) {
               config.gui.remote_gui.putChannelData(job.tivoName, data);
               config.gui.remote_gui.saveChannelInfo(job.tivoName);
            }
            if (job.remote_premiere && job.premiere != null) {
               job.premiere.AddRows(job.tivoName, data);
            }
            if (job.remote_search && job.search != null && data != null) {
               job.search.AddRows(job.tivoName, data);
            }
            if (job.remote_guideChannels && job.gTable != null && data != null) {
               TableUtil.clear(job.gTable.TABLE);
               job.gTable.AddRows(job.tivoName, data);
            }

            log.warn("REMOTE job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE--- job='" + jobName + "' TiVo=" + job.tivoName);
         }
      }
      return false;
   }

}
