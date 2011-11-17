package com.tivo.kmttg.task;

import java.util.Date;
import java.util.Stack;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class remux {
   private static final long serialVersionUID = 1L;
   private backgroundProcess process;
   private jobData job;

   // constructor
   public remux(jobData job) {
      debug.print("job=" + job);
      this.job = job;
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;
      if (job.demuxFiles == null) {
         log.error("No input files for remux available... aborting");
         schedule = false;
      }
      
      if ( ! file.isFile(config.ffmpeg) ) {
         log.error("ffmpeg not found: " + config.ffmpeg);
         schedule = false;
      }
            
      if ( ! file.isFile(job.mpegFile) ) {
         log.error("mpeg file not found: " + job.mpegFile);
         schedule = false;
      }
      
      if (file.isFile(job.mpegFile_fix))
         file.delete(job.mpegFile_fix);
      
      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.mpegFile, job) ) {
            schedule = false;
         }
      }
      
      if (schedule) {
         if ( start() ) {
            job.process_remux   = this;
            jobMonitor.updateJobStatus(job, "running");
            job.time             = new Date().getTime();
         }
         return true;
      } else {
         return false;
      }      
   }

   // Return false if starting command fails, true otherwise
   private Boolean start() {
      debug.print("");
      Stack<String> command = new Stack<String>();
      command.add(config.ffmpeg);
      command.add("-y");
      for (int i=0; i<job.demuxFiles.size(); ++i) {
         command.add("-i");
         command.add(job.demuxFiles.get(i));
      }
      command.add("-acodec");
      command.add("copy");
      command.add("-vcodec");
      command.add("copy");
      command.add("-f");
      command.add("dvd");
      command.add(job.mpegFile_fix);
      process = new backgroundProcess();
      log.print(">> Running ffmpeg remux to generate " + job.mpegFile_fix + " ...");
      if ( process.run(command) ) {
         log.print(process.toString());
      } else {
         log.error("Failed to start command: " + process.toString());
         process.printStderr();
         process = null;
         jobMonitor.removeFromJobList(job);
         return false;
      }
      return true;
   }
   
   public void kill() {
      debug.print("");
      process.kill();
      log.warn("Killing '" + job.type + "' job: " + process.toString());
      
      // Remove the files created by demux task
      removeDemuxFiles();
   }

   // Check status of a currently running job
   // Returns true if still running, false if job finished
   // If job is finished then check result
   public Boolean check() {
      //debug.print("");
      int exit_code = process.exitStatus();
      if (exit_code == -1) {
         // Still running
         if (config.GUIMODE) {
            // Update STATUS column
            int pct = Integer.parseInt(String.format("%d", file.size(job.mpegFile_fix)*100/file.size(job.mpegFile)));
            // Update status in job table
            String status = String.format("%d%%",pct);
            config.gui.jobTab_UpdateJobMonitorRowStatus(job, status);
            
            if ( jobMonitor.isFirstJobInMonitor(job) ) {
               // If 1st job then update title & progress bar
               String title = String.format("remux: %s %s", status, config.kmttg);
               config.gui.setTitle(title);
               config.gui.progressBar_setValue(pct);
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
         
         // Check for problems
         int failed = 0;
         // No or empty mpegFile_fix means problems
         if ( ! file.isFile(job.mpegFile_fix) || file.isEmpty(job.mpegFile_fix) ) {
            log.error("Unable to find remux output file: " + job.mpegFile_fix);
            failed = 1;
         }
         
         // exit status != 0 => problem
         if (exit_code != 0) {
            failed = 1;
         }
         
         if (failed == 1) {
            log.error("remux failed (exit code: " + exit_code + " ) - check command: " + process.toString());
            process.printStderr();
         } else {
            log.warn("remux job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE--- job=" + job.type);
            
            // Remove the demuxed files
            removeDemuxFiles();
            
            // Rename mpegFile_fix to mpegFile
            Boolean result;
            if (file.isFile(job.mpegFile)) {
               if (config.QSFixBackupMpegFile == 1) {
                  // Rename mpegFile to backupFile if it exists
                  String backupFile = job.mpegFile + ".bak";
                  int count = 1;
                  while (file.isFile(backupFile)) {
                     backupFile = job.mpegFile + ".bak" + count++;
                  }
                  result = file.rename(job.mpegFile, backupFile);
                  if ( result ) {
                     log.print("(Renamed " + job.mpegFile + " to " + backupFile + ")");
                  } else {
                     log.error("Failed to rename " + job.mpegFile + " to " + backupFile);
                     return false;                     
                  }
               } else {
                  // Remove mpegFile if it exists
                  result = file.delete(job.mpegFile);
                  if ( ! result ) {
                     log.error("Failed to delete file in preparation for rename: " + job.mpegFile);
                     return false;
                  }
               }
            }
            // Now do the file rename
            result = file.rename(job.mpegFile_fix, job.mpegFile);
            if (result)
               log.print("(Renamed " + job.mpegFile_fix + " to " + job.mpegFile + ")");
            else
               log.error("Failed to rename " + job.mpegFile_fix + " to " + job.mpegFile);
         }
      }
      return false;
   }
   
   private Boolean removeDemuxFiles() {
      Boolean success = true;
      if (job.demuxFiles != null) {
         for (int i=0; i<job.demuxFiles.size(); ++i) {
            if ( ! file.delete(job.demuxFiles.get(i)) ) {
               try {
                  // Sleep 1 second and try deleting again
                  Thread.sleep(1000);
                  if ( ! file.delete(job.demuxFiles.get(i)) ) {
                     log.error("Failed to delete demux file: " + job.demuxFiles.get(i));
                     success = false;                     
                  }
               } catch (InterruptedException e) {
                  log.error(e.getMessage());
               }
            }
         }
      }
      return success;
   }
}
