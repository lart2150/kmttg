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

import javafx.application.Platform;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.gui.remote.util;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class remote extends baseTask implements Serializable {
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
         job.process = this;
         jobMonitor.updateJobStatus(job, "running");
         job.time = new Date().getTime();
      }
      return true;
   }

   public Boolean start() {
      debug.print("");
      // Run Remote in a separate thread
      class AutoThread implements Runnable {
         AutoThread() {}       
         public void run () {
            Remote r = config.initRemote(job.tivoName);
            if (r.success) {
               if (job.remote_todo)
                  data = r.ToDo(job);
               if (job.remote_upcoming)
                  data = r.Upcoming(job);
               if (job.remote_conflicts) {
                  data = r.Upcoming(job);
                  if (data != null && config.GUIMODE)
                     util.updateTodoIfNeeded("Cancel");
               }
               if (job.remote_sp)
                  data = r.SeasonPasses(job);
               if (job.remote_spreorder)
                  data = r.SPReorder(job);
               if (job.remote_cancel) {
                  data = r.CancelledShows(job);
                  if (data != null && config.GUIMODE)
                     util.updateTodoIfNeeded("Cancel");
               }
               if (job.remote_deleted)
                  data = r.DeletedShows(job);
               if (job.remote_thumbs)
                  data = r.getThumbs(job);
               if (job.remote_channelsTable)
                  data = r.ChannelList(job, false);
               if (job.remote_rnpl) {
                  if (config.rpcEnabled(job.tivoName))
                     data = r.MyShows(job);
                  else
                     data = r.MyShowsS3(job);
               }
               if (job.remote_channels) {
                  data = r.ChannelList(job, true);
               }
               if (job.remote_premiere && config.GUIMODE)
                  data = r.SeasonPremieres(
                     config.gui.remote_gui.premiere_tab.getSelectedChannelData(job.tivoName),
                     job, config.gui.remote_gui.getPremiereDays()
                  );
               if (job.remote_search && config.GUIMODE) {
                  data = r.searchKeywords(job.remote_search_keyword, job, job.remote_search_max);
                  if (data != null)
                     util.updateTodoIfNeeded("Search");
               }
               if (job.remote_adv_search && config.GUIMODE) {
                  config.gui.remote_gui.clearTable("search");
                  data = r.AdvSearch(job);
                  if (data != null)
                     util.updateTodoIfNeeded("Search");
               }
               if (job.remote_guideChannels && config.GUIMODE) {
                  data = r.ChannelList(job, true);
                  if (data != null)
                     util.updateTodoIfNeeded("Guide");
               }
               if (job.remote_stream) {
                  data = r.streamingEntries(null);
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
      if (job.remote_thumbs)        jobName += " Thumbs List";
      if (job.remote_channelsTable) jobName += " Channels List";
      if (job.remote_rnpl)          jobName += " NP List";
      if (job.remote_channels)      jobName += " Channels List";
      if (job.remote_premiere)      jobName += " Season Premieres";
      if (job.remote_search)        jobName += " Keyword Search";
      if (job.remote_adv_search)    jobName += " Advanced Search";
      if (job.remote_guideChannels) jobName += " Guide Channel List";
      if (job.remote_stream)        jobName += " Streaming Entries";
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
             ! job.remote_cancel && ! job.remote_deleted && ! job.remote_search && 
             ! job.remote_adv_search && ! job.remote_thumbs && ! job.remote_channelsTable) {
            // Update STATUS column
            Platform.runLater(new Runnable() {
               @Override public void run() {
                  config.gui.jobTab_UpdateJobMonitorRowStatus(job, "running");
               }
            });
         }
         return true;
      } else {
         // Job finished
         if (config.GUIMODE) {
            if ( jobMonitor.isFirstJobInMonitor(job) ) {
               Platform.runLater(new Runnable() {
                  @Override public void run() {
                     config.gui.setTitle(config.kmttg);
                     config.gui.progressBar_setValue(0);
                  }
               });
            }
         }
         jobMonitor.removeFromJobList(job);
         if (success) {
            if (job.remote_todo && job.todo != null) {
               // ToDo list job => populate ToDo table
               Platform.runLater(new Runnable() {
                  @Override public void run() {
                     job.todo.AddRows(job.tivoName, data);
                  }
               });
            }
            if (job.remote_upcoming && job.todo != null && config.GUIMODE) {
               Platform.runLater(new Runnable() {
                  @Override public void run() {
                     // Upcoming list job => populate ToDo table
                     job.todo.AddRows(job.tivoName, data);
                     // Make the ToDo tab the currently selected tab
                     config.gui.remote_gui.getPanel().getSelectionModel().select(0);
                  }
               });
            }
            if (job.remote_conflicts && job.cancelled != null && config.GUIMODE) {
               Platform.runLater(new Runnable() {
                  @Override public void run() {
                     // Conflicts list job => populate Won't Record table
                     job.cancelled.AddRows(job.tivoName, data);
                     // Make the Won't Record tab the currently selected tab
                     config.gui.remote_gui.getPanel().getSelectionModel().select(2);
                  }
               });
               // Enter the 1st folder in Won't Record table
               job.cancelled.expandFirstFolder();
            }
            if (job.remote_sp && job.sp != null) {
               Platform.runLater(new Runnable() {
                  @Override public void run() {
                     // SP job => populate SP table
                     job.sp.AddRows(job.tivoName, data);
                  }
               });
            }
            if (job.remote_spreorder && data != null && config.GUIMODE) {
               Platform.runLater(new Runnable() {
                  @Override public void run() {
                     // Refresh SP list for TiVo SPs that were just re-ordered
                     config.gui.remote_gui.clearTable("sp");
                     config.gui.remote_gui.setTivoName("sp", job.tivoName);
                     config.gui.remote_gui.sp_tab.SPListCB(job.tivoName);
                  }
               });
            }
            if (job.remote_cancel && job.cancelled != null) {
               Platform.runLater(new Runnable() {
                  @Override public void run() {
                     job.cancelled.AddRows(job.tivoName, data);
                  }
               });
            }
            if (job.remote_deleted && job.deleted != null) {
               Platform.runLater(new Runnable() {
                  @Override public void run() {
                     job.deleted.AddRows(job.tivoName, data);
                  }
               });
            }
            if (job.remote_thumbs && job.thumbs != null) {
               Platform.runLater(new Runnable() {
                  @Override public void run() {
                     job.thumbs.AddRows(job.tivoName, data);
                  }
               });
            }
            if (job.remote_channelsTable && job.channelsTable != null) {
               Platform.runLater(new Runnable() {
                  @Override public void run() {
                     job.channelsTable.AddRows(job.tivoName, data);
                  }
               });
            }
            if (job.remote_rnpl) {
               rnpl.setNPLData(job.tivoName, data, job.auto_entries);
            }
            if (job.remote_channels && data != null && config.GUIMODE) {
               Platform.runLater(new Runnable() {
                  @Override public void run() {
                     config.gui.remote_gui.premiere_tab.putChannelData(job.tivoName, data);
                     config.gui.remote_gui.premiere_tab.saveChannelInfo(job.tivoName);
                  }
               });
            }
            if (job.remote_premiere && job.premiere != null) {
               Platform.runLater(new Runnable() {
                  @Override public void run() {
                     job.premiere.AddRows(job.tivoName, data);
                  }
               });
            }
            if (job.remote_search && job.search != null && data != null) {
               Platform.runLater(new Runnable() {
                  @Override public void run() {
                     job.search.AddRows(job.tivoName, data);
                  }
               });
            }
            if (job.remote_adv_search && job.search != null && data != null) {
               Platform.runLater(new Runnable() {
                  @Override public void run() {
                     job.search.AddRows(job.tivoName, data);
                  }
               });
            }
            if (job.remote_guideChannels && job.gTable != null && data != null) {
               Platform.runLater(new Runnable() {
                  @Override public void run() {
                     job.gTable.updateChannels_gui(job.tivoName, data);
                  }
               });
            }
            if (job.remote_stream && job.stream != null) {
               Platform.runLater(new Runnable() {
                  @Override public void run() {
                     job.stream.AddRows(job.tivoName, data);
                  }
               });
            }

            log.warn("REMOTE job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE--- job='" + jobName + "' TiVo=" + job.tivoName);
         }
      }
      return false;
   }

}
