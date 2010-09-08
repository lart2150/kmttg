package com.tivo.kmttg.task;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;

import com.tivo.kmttg.main.auto;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.http;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class javadownload implements Serializable {
   private static final long serialVersionUID = 1L;
   private Thread thread = null;
   private Boolean thread_running = false;
   private Boolean success = false;
   private backgroundProcess process;
   public jobData job;

   public javadownload(jobData job) {
      debug.print("job=" + job);
      this.job = job;
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;
      if ( file.isFile(job.tivoFile) ) {
         if (config.OverwriteFiles == 0) {
            log.warn("SKIPPING DOWNLOAD, FILE ALREADY EXISTS: " + job.tivoFile);
            schedule = false;
         } else {
            log.warn("OVERWRITING EXISTING FILE: " + job.tivoFile);
            file.delete(job.tivoFile);
         }
      }

      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.tivoFile, job) ) schedule = false;
      }
      
      if (schedule) {
         if ( start() ) {
            job.process_javadownload = this;
            jobMonitor.updateJobStatus(job, "running");
            job.time = new Date().getTime();
         }
         return true;
      } else {
         return false;
      }      
   }
   
   private Boolean start() {
      debug.print("");
      if (job.url == null || job.url.length() == 0) {
         log.error("URL not given");
         jobMonitor.removeFromJobList(job);
         return false;
      }
      String url = job.url;
      if (config.TSDownload == 1)
         url += "&Format=video/x-tivo-mpeg-ts";
      final String urlString = url;
      log.print(">> DOWNLOADING " + job.tivoFile + " ...");
      log.print(urlString);
      // Run download method in a separate thread
      Runnable r = new Runnable() {
         public void run () {
            try {
               success = http.download(urlString, "tivo", config.MAK, job.tivoFile, true);
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
      log.warn("Killing '" + job.type + "' file: " + job.tivoFile);
      thread_running = false;
   }
   
   // Check status of a currently running job
   // Returns true if still running, false if job finished
   public Boolean check() {
      if (thread_running) {
         // Still running
         if (config.GUI && file.isFile(job.tivoFile)) {
            // Update status in job table
            Long size = file.size(job.tivoFile);
            String s = String.format("%.2f MB", (float)size/Math.pow(2,20));
            String t = jobMonitor.getElapsedTime(job.time);
            int pct = Integer.parseInt(String.format("%d", size*100/job.tivoFileSize));
            
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
         if (config.GUI) {
            if ( jobMonitor.isFirstJobInMonitor(job) ) {
               config.gui.setTitle(config.kmttg);
               config.gui.progressBar_setValue(0);
            }
         }
         
         jobMonitor.removeFromJobList(job);
         
         // Check for problems
         int failed = 0;
         
         if ( ! success ) {
            failed = 1;
         }
         
         // No or empty output means problems         
         if ( file.isEmpty(job.tivoFile) ) {
            failed = 1;
         } else {
            // Print statistics for the job
            String s = String.format("%.2f MB", file.size(job.tivoFile)/Math.pow(2,20));
            String t = jobMonitor.getElapsedTime(job.time);
            String r = jobMonitor.getRate(file.size(job.tivoFile), job.time);
            log.warn(job.tivoFile + ": size=" + s + " elapsed=" + t + " (" + r + ")");
         }
         
         // Check first line in tivo file for errors
         // Also if file size is very small then it's likely a failure
         if (failed == 0) {
            try {
               BufferedReader ifp = new BufferedReader(new FileReader(job.tivoFile));
               String first = ifp.readLine();
               ifp.close();
               if ( first.toLowerCase().contains("html") ) failed = 1;
               if ( first.toLowerCase().contains("busy") ) failed = 1;
               if ( first.toLowerCase().contains("failed") ) failed = 1;
               if ( file.size(job.tivoFile) < 1000 ) failed = 1;
               if (failed == 1) {
                  process.printStdout();
                  log.error(first);
               }
            }
            catch (IOException ex) {
               failed = 1;
            }
         }
         
         if (failed == 1) {
            log.error("Download failed to file: " + job.tivoFile);
            file.delete(job.tivoFile);
            
            // Try download again with delayed launch time if specified
            if (job.launch_tries < config.download_tries) {
               job.launch_tries++;
               log.warn(string.basename(job.tivoFile) + ": Download attempt # " +
                     job.launch_tries + " scheduled in " + config.download_retry_delay + " seconds.");
               job.launch_time = new Date().getTime() + config.download_retry_delay*1000;
               jobMonitor.submitNewJob(job);
            } else {
               log.error(string.basename(job.tivoFile) + ": Too many failed downloads, GIVING UP!!");
            }
         } else {
            log.print("---DONE--- job=" + job.type + " output=" + job.tivoFile);
            // Add auto history entry if auto downloads configured
            if (file.isFile(config.autoIni))
               auto.AddHistoryEntry(job);
         }
      }
      return false;
   }

   // Return rate in Mbps (ds=delta bytes, dt=delta time in msecs)
   private String getRate(long ds, long dt) {      
      return String.format("%.1f Mbps", (ds*8000)/(1e6*dt));
   }
}
