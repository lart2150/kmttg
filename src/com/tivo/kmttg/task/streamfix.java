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

public class streamfix {
   private backgroundProcess process;
   private jobData job;

   // constructor
   public streamfix(jobData job) {
      debug.print("job=" + job);
      this.job = job;
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;
            
      if ( ! file.isFile(config.mencoder) ) {
         log.error("mencoder not found: " + config.mencoder);
         schedule = false;
      }
      
      if ( ! file.isFile(job.mpegFile) ) {
         log.error("mpeg file not given or doesn't exist: " + job.mpegFile);
         schedule = false;
      }
                  
      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.mpegFile_fix) ) schedule = false;
      }
      
      if (schedule) {
         if ( start() ) {
            job.process_streamfix = this;
            job.status            = "running";
            job.time              = new Date().getTime();
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
      command.add(config.mencoder);
      command.add(job.mpegFile);
      command.add("-oac");
      command.add("copy");
      command.add("-ovc");
      command.add("copy");
      command.add("-of");
      command.add("mpeg");
      command.add("-mpegopts");
      command.add("format=dvd:tsaf");
      command.add("-vf");
      command.add("harddup");
      command.add("-o");
      command.add(job.mpegFile_fix);
      process = new backgroundProcess();
      log.print(">> Running streamfix on " + job.mpegFile + " ...");
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
   }

   // Check status of a currently running job
   // Returns true if still running, false if job finished
   // If job is finished then check result
   public Boolean check() {
      //debug.print("");
      int exit_code = process.exitStatus();
      if (exit_code == -1) {
         // Still running
         if (config.GUI) {
            if ( file.isFile(job.mpegFile_fix) ) {               
               // Update status in job table
               if ( job.tivoFileSize == null ) {
                  job.tivoFileSize = file.size(job.mpegFile);
               }
               String s = String.format("%.2f MB", (float)file.size(job.mpegFile_fix)/Math.pow(2,20));
               String t = jobMonitor.getElapsedTime(job.time);
               int pct = Integer.parseInt(String.format("%d", file.size(job.mpegFile_fix)*100/job.tivoFileSize));
               
               if ( jobMonitor.isFirstJobInMonitor(job) ) {
                  // Update STATUS column
                  config.gui.jobTab_UpdateJobMonitorRowStatus(job, t + "---" + s);
                  
                  // If 1st job then update title & progress bar
                  String title = String.format("streamfix: %d%% %s", pct, config.kmttg);
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
         if ( jobMonitor.isFirstJobInMonitor(job) ) {
            config.gui.setTitle(config.kmttg);
            config.gui.progressBar_setValue(0);
         }
         jobMonitor.removeFromJobList(job);
         
         // Check for problems
         int failed = 0;
         // No or empty mpegFile_fix means problems
         if ( ! file.isFile(job.mpegFile_fix) || file.isEmpty(job.mpegFile_fix) ) {
            failed = 1;
         }
         
         // exit status != 0 => problem
         if (exit_code != 0) {
            failed = 1;
         }
         
         if (failed == 1) {
            log.error("streamfix failed (exit code: " + exit_code + " ) - check command: " + process.toString());
            process.printStderr();
         } else {
            log.warn("streamfix job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE---");
         }
      }
      return false;
   }

}
