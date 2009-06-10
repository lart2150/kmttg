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

public class tsremux {
   private backgroundProcess process;
   private jobData job;

   // constructor
   public tsremux(jobData job) {
      debug.print("job=" + job);
      this.job = job;
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;
      // Don't tsremux if tsFile already exists
      if ( file.isFile(job.tsFile) ) {
         log.warn("SKIPPING tsremux, FILE ALREADY EXISTS: " + job.tsFile);
         schedule = false;
      }
            
      if ( ! file.isFile(config.tsremux) ) {
         log.error("tsremux not found: " + config.tsremux);
         schedule = false;
      }
            
      if ( ! file.isFile(job.mpegFile) ) {
         log.error("mpeg file not found: " + job.mpegFile);
         schedule = false;
      }
      
      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.tsFile) ) schedule = false;
      }
      
      if (schedule) {
         if ( start() ) {
            job.process_tsremux  = this;
            job.status           = "running";
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
      command.add(config.tsremux);
      command.add(job.mpegFile);
      command.add(job.tsFile);
      process = new backgroundProcess();
      log.print(">> Running tsremux on " + job.mpegFile + " ...");
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
            // Update STATUS column
            if ( file.isFile(job.tsFile) ) {               
               // Update status in job table
               String s = String.format("%.2f MB", (float)file.size(job.tsFile)/Math.pow(2,20));
               String t = jobMonitor.getElapsedTime(job.time);
               config.gui.jobTab_UpdateJobMonitorRowStatus(job, t + "---" + s);
               
               if ( jobMonitor.isFirstJobInMonitor(job) ) {
                  // If 1st job then update title & progress bar
                  String title = String.format("tsremux: %s %s", t, config.kmttg);
                  config.gui.setTitle(title);
               }
            }
         }
        return true;
      } else {
         // Job finished         
         if ( jobMonitor.isFirstJobInMonitor(job) ) {
            config.gui.setTitle(config.kmttg);
         }
         jobMonitor.removeFromJobList(job);
         
         // Check for problems
         int failed = 0;
         // No or empty tsFile means problems
         if ( ! file.isFile(job.tsFile) || file.isEmpty(job.tsFile) ) {
            failed = 1;
         }
         
         // exit status != 0 => problem
         if (exit_code != 0) {
            failed = 1;
         }
         
         if (failed == 1) {
            log.error("tsremux failed (exit code: " + exit_code + " ) - check command: " + process.toString());
            process.printStderr();
         } else {
            log.warn("tsremux job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE---");
            // Remove mpegFile
            file.delete(job.mpegFile);
            log.print("(Deleted mpeg file: " + job.mpegFile + ")");
         }
      }
      return false;
   }

}
