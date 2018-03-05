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
import java.util.Date;
import java.util.Hashtable;

import com.tivo.kmttg.main.auto;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.http;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.mediainfo;
import com.tivo.kmttg.util.string;

public class tdownload_decrypt extends baseTask implements Serializable {
   private static final long serialVersionUID = 1L;
   private Thread thread = null;
   private Boolean thread_running = false;
   private Boolean success = false;
   private backgroundProcess process = null;
   public jobData job;
   
   public tdownload_decrypt(jobData job) {
      debug.print("job=" + job);
      this.job = job;
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;
      
      // Don't decrypt if mpegFile already exists
      if ( file.isFile(job.mpegFile) ) {
         if (job.offset != null) {
            job.mpegFile = job.mpegFile.replaceFirst("\\.mpg", "(2).mpg");
            log.warn("NOTE: Renaming mpeg file to avoid overwrite: " + job.mpegFile);
            jobMonitor.updatePendingJobFieldValue(job, "mpegFile", job.mpegFile);
         } else {
            if (config.OverwriteFiles == 0) {
               log.warn("SKIPPING DOWNLOAD/DECRYPT, FILE ALREADY EXISTS: " + job.mpegFile);
               schedule = false;
            } else {
               log.warn("OVERWRITING EXISTING FILE: " + job.mpegFile);
            }
         }
      }
      
      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.mpegFile, job) ) schedule = false;
      }
      
      if (schedule) {
         if ( start() ) {
            job.process = this;
            jobMonitor.updateJobStatus(job, "running");
            job.time             = new Date().getTime();
         }
         return true;
      } else {
         return false;
      }      
   }
   
   public Boolean start() {
      debug.print("");
      if (job.url == null || job.url.length() == 0) {
         log.error("URL not given");
         jobMonitor.kill(job);
         return false;
      }
      
      // Add wan http port if configured
      String wan_port = config.getWanSetting(job.tivoName, "http");
      if (wan_port != null)
         job.url = string.addPort(job.url, wan_port);
      
      String url = job.url;
      if (config.TSDownload == 1 && job.offset == null)
         url += "&Format=video/x-tivo-mpeg-ts";

      // For transport stream container input files change output file suffix from .mpg to .ts
      Boolean isFileChanged = false;
      if (config.TSDownload == 1 || job.TSDownload == 1) {
         if (job.mpegFile.endsWith(".mpg")) {
            job.mpegFile = string.replaceSuffix(job.mpegFile, ".ts");
            isFileChanged = true;
         }
      }      
      if (isFileChanged) {            
         // If in GUI mode, update job monitor output field
         if (config.GUIMODE) {
            String output = string.basename(job.mpegFile);
            if (config.jobMonitorFullPaths == 1)
               output = job.mpegFile;
            config.gui.jobTab_UpdateJobMonitorRowOutput(job, output);
         }
         
         // Subsequent jobs need to have mpegFile && mpegFile_cut updated
         jobMonitor.updatePendingJobFieldValue(job, "mpegFile", job.mpegFile);
         String mpegFile_cut = job.mpegFile_cut.replaceFirst("_cut\\.mpg", "_cut.ts");
         jobMonitor.updatePendingJobFieldValue(job, "mpegFile_cut", mpegFile_cut);
         
         // Rename already created metadata file if relevant
         String meta = string.replaceSuffix(job.mpegFile, ".mpg") + ".txt";
         if (file.isFile(meta)) {
            String meta_new = job.mpegFile + ".txt";
            log.print("Renaming metadata file to: " + meta_new);
            file.rename(meta, meta_new);
            // Subsequent jobs need to have metaFile updated
            jobMonitor.updatePendingJobFieldValue(job, "metaFile", meta_new);
         }
         meta = string.replaceSuffix(job.mpegFile_cut, ".mpg") + ".txt";
         if (file.isFile(meta)) {
            String meta_new = job.mpegFile_cut + ".txt";
            log.print("Renaming metadata file to: " + meta_new);
            file.rename(meta, meta_new);
            // Subsequent jobs need to have metaFile updated
            jobMonitor.updatePendingJobFieldValue(job, "metaFile", meta_new);
         }
      }

      String message = "DOWNLOADING/DECRYPTING";
      if (job.offset != null) {
         message = "RESUMING DOWNLOAD/DECRYPT WITH OFFSET=" + job.offset;
         job.tivoFileSize -= Long.parseLong(job.offset);
      }
      log.print(">> " + message + " TO " + job.mpegFile + " ...");
      
      // Run java download + tivolibre pipe in a separate thread
      final String urlString = url;
      try {
         class Thread1 implements Runnable {
            String urlString;
            public Thread1(String urlString) {
               this.urlString = urlString;
            }
            public void run() {
               try {
                  success = http.downloadPipedStream(urlString, "tivo", config.MAK, true, job);
                  thread_running = false;
               } catch (Exception e) {
                  log.error("tdownload_decrypt"); log.error(Arrays.toString(e.getStackTrace()));
                  thread_running = false;
                  success = false;
                  Thread.currentThread().interrupt();
               }
            }
         }
         thread_running = true;
         thread = new Thread(new Thread1(urlString));
         thread.start();         
      } catch (Exception e) {
         log.error("tdownload_decrypt - " + e.getMessage());
         thread_running = false;
         success = false;
      }
      
      return true;
   }
   
   public void kill() {
      debug.print("");
      if (job.limit == 0)
         log.warn("Killing '" + job.type + "' file: " + job.mpegFile);
      thread.interrupt();
      thread_running = false;
   }
   
   // Check status of a currently running job
   // Returns true if still running, false if job finished
   // If job is finished then check result
   public Boolean check() {
      //debug.print("");
      if (thread_running) {
         // Still running
         if (config.GUIMODE && file.isFile(job.mpegFile)) {
            // Update status in job table
            Long size = file.size(job.mpegFile);
            String s = String.format("%.2f MB", (float)size/Math.pow(2,20));
            String t = jobMonitor.getElapsedTime(job.time);
            int pct;
            if (job.limit > 0)
               pct = Integer.parseInt(String.format("%d", size*100/job.limit));
            else
               pct = Integer.parseInt(String.format("%d", size*100/job.tivoFileSize));
            
            // Calculate current transfer rate over last dt msecs
            Long dt = (long)5000;
            job.time2 = new Date().getTime();
            job.size2 = size;
            if (job.time1 == null)
               job.time1 = job.time2;
            if (job.size1 == null)
               job.size1 = job.size2;
            if (job.time2-job.time1 >= dt && job.size2 > job.size1) {
               job.rate = getRate(job.size2-job.size1, job.time2-job.time1);
               job.time1 = job.time2;
               job.size1 = job.size2;
            }
            
            if (config.download_time_estimate == 1) {
               // Estimated time remaining
               job.rate = string.getTimeRemaining(job.time2, job.time, job.tivoFileSize, size);
            }
            
            if ( jobMonitor.isFirstJobInMonitor(job) ) {
               // Update STATUS column 
               config.gui.jobTab_UpdateJobMonitorRowStatus(job, t + "---" + s + "---" + job.rate);
               
               // If 1st job then update title & progress bar
               String title = String.format("download: %d%% %s", pct, config.kmttg);
               config.gui.setTitle(title);
               config.gui.progressBar_setValue(pct);
            } else {
               // Update STATUS column            
               config.gui.jobTab_UpdateJobMonitorRowStatus(job, String.format("%d%%",pct) + "---" + s + "---" + job.rate);
            }
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
         
         if (job.limit > 0) {
            // Skip detect job => Schedule decrypt & comskip jobs
            Hashtable<String,Object> specs = new Hashtable<String,Object>();
            specs.put("mode", "FILES");
            specs.put("source", job.source);
            specs.put("tivoName", job.tivoName);
            specs.put("startFile", job.mpegFile);
            specs.put("SkipPoint", job.SkipPoint);
            specs.put("comskip", true);
            specs.put("decrypt", false);
            specs.put("metadata", false);
            specs.put("metadataTivo", false);
            specs.put("qsfix", false);
            specs.put("twpdelete", false);
            specs.put("rpcdelete", false);
            specs.put("comcut", false);
            specs.put("captions", false);
            specs.put("encode", false);
            specs.put("push", false);
            specs.put("custom", false);
            specs.put("contentId", job.contentId);
            specs.put("offerId", job.offerId);
            specs.put("title", job.title);
            jobMonitor.LaunchJobs(specs);
            return false;
         }
         
         // Check for problems
         int failed = 0;
         
         if ( ! success ) {
            failed = 1;
         }
         
         // No or empty output means problems         
         if ( file.isEmpty(job.mpegFile) ) {
            failed = 1;
         } else {
            // Print statistics for the job
            String s = String.format("%.2f MB", file.size(job.mpegFile)/Math.pow(2,20));
            String t = jobMonitor.getElapsedTime(job.time);
            String r = jobMonitor.getRate(file.size(job.mpegFile), job.time);
            log.warn(job.mpegFile + ": size=" + s + " elapsed=" + t + " (" + r + ")");
         }
         
         // If file size is very small then it's likely a failure
         if (failed == 0) {
            if ( file.size(job.mpegFile) < 1000 ) failed = 1;
         }
         
         if (failed == 0) {
            // Check download duration if configured (and not resume download)
            if ( job.offset == null && ! mediainfo.checkDownloadDuration(job.download_duration, job.mpegFile) )
               failed = 1;
         }
         
         if (failed == 1) {
            log.error("Download failed to file: " + job.mpegFile);
            if (config.DeleteFailedDownloads == 1) {
               if (file.delete(job.mpegFile))
                  log.warn("Removed failed download file: " + job.mpegFile);
            }
            
            // Try download again with delayed launch time if specified
            if (job.launch_tries < config.download_tries) {
               job.launch_tries++;
               log.warn(string.basename(job.mpegFile) + ": Download attempt # " +
                     job.launch_tries + " scheduled in " + config.download_retry_delay + " seconds.");
               job.launch_time = new Date().getTime() + config.download_retry_delay*1000;
               jobMonitor.submitNewJob(job);
            } else {
               log.error(string.basename(job.mpegFile) + ": Too many failed downloads, GIVING UP!!");
               jobMonitor.kill(job); // This called so that family of jobs is killed
            }
         } else {
            log.print("---DONE--- job=" + job.type + " output=" + job.mpegFile);
            // Add auto history entry if auto downloads configured
            if (file.isFile(config.autoIni))
               auto.AddHistoryEntry(job);
            
            // TivoWebPlus call to delete show on TiVo if configured
            if (job.twpdelete && ! config.rpcEnabled(job.tivoName)) {
               file.TivoWebPlusDelete(job.url);
            }
            
            // rpc style delete show on TiVo if configured
            if (job.rpcdelete && config.rpcEnabled(job.tivoName)) {
               String recordingId = rnpl.findRecordingId(job.tivoName, job.entry);
               if ( ! file.rpcDelete(job.tivoName, recordingId) )
                  log.error("Failed to delete show on TiVo");
            }
         }
      }
      
      return false;
   }

   // Return rate in Mbps (ds=delta bytes, dt=delta time in msecs)
   private String getRate(long ds, long dt) {      
      return String.format("%.1f Mbps", (ds*8000)/(1e6*dt));
   }
}
