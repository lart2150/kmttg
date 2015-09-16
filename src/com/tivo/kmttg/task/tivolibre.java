package com.tivo.kmttg.task;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.Serializable;
import java.util.Date;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

import net.straylightlabs.tivolibre.TivoDecoder;

public class tivolibre extends baseTask implements Serializable {
   private static final long serialVersionUID = 1L;
   private Thread thread = null;
   private Boolean thread_running = false;
   private Boolean success = false;
   private backgroundProcess process;
   public jobData job;
   
   public tivolibre(jobData job) {
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
         if (config.OverwriteFiles == 0) {
            log.warn("SKIPPING DECRYPT, FILE ALREADY EXISTS: " + job.mpegFile);
            schedule = false;
         } else {
            log.warn("OVERWRITING EXISTING FILE: " + job.mpegFile);
         }
      }
      
      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.mpegFile, job) ) {
            schedule = false;
         }
      }
      
      if (schedule) {
         if ( start() ) {
            job.process = this;
            jobMonitor.updateJobStatus(job, "running");
            job.time = new Date().getTime();
         }
         return true;
      } else {
         return false;
      }      
   }

   public Boolean start() {
      debug.print("");
      // Run download method in a separate thread
      Runnable r = new Runnable() {
         public void run () {
            try {
               BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(job.tivoFile));
               BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(job.mpegFile));
               TivoDecoder decoder = new TivoDecoder(inputStream, outputStream, config.MAK);
               success = decoder.decode();
               inputStream.close();
               outputStream.close();
               decoder = null;
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

   public Boolean check() {
      if (thread_running) {
         // Still running
         if (config.GUIMODE) {
            if ( file.isFile(job.mpegFile) ) {               
               // Update status in job table
               if ( job.tivoFileSize == null ) {
                  job.tivoFileSize = file.size(job.tivoFile);
               }
               String s = String.format("%.2f MB", (float)file.size(job.mpegFile)/Math.pow(2,20));
               String t = jobMonitor.getElapsedTime(job.time);
               int pct = Integer.parseInt(String.format("%d", file.size(job.mpegFile)*100/job.tivoFileSize));
                              
               if ( jobMonitor.isFirstJobInMonitor(job) ) {
                  // Update STATUS column
                  config.gui.jobTab_UpdateJobMonitorRowStatus(job, t + "---" + s);
                  
                  // If 1st job then update title & progress bar
                  String title = String.format("tivolibre: %d%% %s", pct, config.kmttg);
                  config.gui.setTitle(title);
                  config.gui.progressBar_setValue(pct);
               } else {
                  // Update STATUS column
                  config.gui.jobTab_UpdateJobMonitorRowStatus(job, String.format("%d%%",pct) + "---" + s);
               }
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
         
         if ( success ) {
            log.warn("tibolibre job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE--- job=" + job.type + " output=" + job.mpegFile);
            
            // Remove .TiVo file if option enabled
            if (config.RemoveTivoFile == 1) {
               if ( file.delete(job.tivoFile) ) {
                  log.print("(Deleted file: " + job.tivoFile + ")");
               } else {
                  log.error("Failed to delete file: "+ job.tivoFile);
               }
            }
            
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
         } else {
            log.error("tivolibre decrypt failed for file: " + job.tivoFile);
         }

         return false;         
      }
   }

   public void kill() {
      debug.print("");
      thread.interrupt();
      log.warn("Killing '" + job.type + "' file: " + job.tivoFile);
      thread_running = false;
   }

}
