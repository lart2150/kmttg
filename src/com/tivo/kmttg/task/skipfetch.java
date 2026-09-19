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
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.ClipSegments;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

// Fills the AutoSkip table from tivo.com for every recording that has SkipMode and no entry
// yet. A job rather than a background thread because the first run over a full My Shows list
// is a cloud round trip per recording and can take a long time - as a job it shows progress,
// queues behind other work and can be cancelled.
//
// The work list is handed over through a static rather than carried on jobData: it is a slice
// of the NPL that can run to hundreds of entries, and jobData is Serializable and gets
// persisted. A job that outlives the handover finds nothing and completes, which is the right
// answer - the next NPL refresh will queue it again.
public class skipfetch extends baseTask implements Serializable {
   private static final long serialVersionUID = 1L;
   private static final Map<String,List<Hashtable<String,String>>> pending =
      new ConcurrentHashMap<String,List<Hashtable<String,String>>>();

   private Thread thread = null;
   // Written by the worker thread and read by the job monitor polling check(), same as remux.
   private volatile Boolean thread_running = false;
   private volatile int done = 0;
   private volatile int total = 0;
   private volatile int saved = 0;
   private volatile Boolean cancelled = false;
   public jobData job;

   public skipfetch(jobData job) {
      debug.print("job=" + job);
      this.job = job;
   }

   // Called by whoever decided there is work, just before submitting the job.
   public static void setPending(String tivoName, List<Hashtable<String,String>> entries) {
      pending.put(tivoName, new ArrayList<Hashtable<String,String>>(entries));
   }

   public static int pendingCount(String tivoName) {
      List<Hashtable<String,String>> l = pending.get(tivoName);
      return l == null ? 0 : l.size();
   }

   public backgroundProcess getProcess() {
      return null;
   }

   public Boolean launchJob() {
      debug.print("");
      // No removeFromJobList here: jobData.launch() does that for a job that declines to
      // start, and doing it twice removes the monitor row and saves the queue twice.
      if (pendingCount(job.tivoName) == 0) {
         log.warn("No recordings need SkipMode data - nothing to fetch");
         return false;
      }
      // Unlike the tasks this is modelled on, a failed start here is not "launched anyway":
      // the work list can be taken between the count above and the remove inside start(), and
      // saying true then leaves a job with no thread and no job.time for check() to measure.
      if (! start()) return false;
      job.process = this;
      jobMonitor.updateJobStatus(job, "running");
      job.time = new Date().getTime();
      return true;
   }

   public Boolean start() {
      debug.print("");
      final List<Hashtable<String,String>> entries = pending.remove(job.tivoName);
      if (entries == null || entries.isEmpty()) return false;
      total = entries.size();
      log.warn(">> FETCHING SkipMode data from tivo.com for " + total + " recording(s) ...");
      thread_running = true;
      thread = new Thread() {
         public void run() {
            try {
               saved = ClipSegments.fetchMissing(job.tivoName, entries,
                  new ClipSegments.Progress() {
                     public boolean update(int n, int of, String title) {
                        done = n;
                        if (cancelled) return false;
                        log.print("SkipMode " + n + "/" + of + ": " + title);
                        return true;
                     }
                  });
            } catch (Exception e) {
               log.error("skipfetch - " + e.getMessage());
            } finally {
               thread_running = false;
            }
         }
      };
      thread.start();
      return true;
   }

   public void kill() {
      debug.print("");
      log.warn("Killing '" + job.type + "' job for " + job.tivoName);
      cancelled = true;
      // The batch checks cancelled between recordings; interrupting also breaks the pacing
      // sleep so a cancel does not wait for it.
      if (thread != null) thread.interrupt();
      thread_running = false;
   }

   public Boolean check() {
      if (thread_running) {
         if (config.GUIMODE) {
            String status = done + "/" + total;
            if (jobMonitor.isFirstJobInMonitor(job)) {
               config.gui.jobTab_UpdateJobMonitorRowStatus(job,
                  jobMonitor.getElapsedTime(job.time) + "---" + status);
               if (total > 0) {
                  int pct = done * 100 / total;
                  config.gui.setTitle(String.format("SkipMode: %d%% %s", pct, config.kmttg));
                  config.gui.progressBar_setValue(pct);
               }
            } else {
               config.gui.jobTab_UpdateJobMonitorRowStatus(job, status);
            }
         }
         return true;
      }

      if (config.GUIMODE && jobMonitor.isFirstJobInMonitor(job)) {
         config.gui.setTitle(config.kmttg);
         config.gui.progressBar_setValue(0);
      }
      jobMonitor.removeFromJobList(job);
      log.warn("SkipMode fetch completed: " + saved + " of " + total
         + " saved to AutoSkip table (" + jobMonitor.getElapsedTime(job.time) + ")");
      log.print("---DONE--- job=" + job.type + " tivo=" + job.tivoName);
      return false;
   }
}
