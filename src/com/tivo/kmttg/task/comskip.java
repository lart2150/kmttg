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
import com.tivo.kmttg.util.string;

public class comskip {
   private backgroundProcess process;
   private jobData job;
   private String outputFile = null;
   private String options = null;

   // constructor
   public comskip(jobData job) {
      debug.print("job=" + job);
      this.job = job;
      outputFile = job.edlFile;
      if (job.vprjFile != null) {
         outputFile = job.vprjFile;
         options = "--videoredo";
      }
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;
      // Don't comskip if outputFile already exists
      if ( file.isFile(outputFile) ) {
         log.warn("SKIPPING COMSKIP, FILE ALREADY EXISTS: " + outputFile);
         schedule = false;
      }
      
      if ( ! file.isFile(config.comskip) ) {
         log.error("comskip not found: " + config.comskip);
         schedule = false;
      }
      
      if ( ! file.isFile(config.comskipIni) ) {
         log.error("comskip.ini not found: " + config.comskipIni);
         schedule = false;
      }
      
      if ( ! file.isFile(job.mpegFile) ) {
         log.error("mpeg file not found: " + job.mpegFile);
         schedule = false;
      }
      
      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(outputFile) ) schedule = false;
      }
      
      if (schedule) {
         if ( start() ) {
            job.process_comskip  = this;
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
      command.add(config.comskip);
      command.add("--ini");
      command.add(config.comskipIni);
      if (options != null)
         command.add(options);
      command.add(job.mpegFile);
      process = new backgroundProcess();
      log.print(">> Running comskip on " + job.mpegFile + " ...");
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
            // Update status in job table
            String t = jobMonitor.getElapsedTime(job.time);
            config.gui.jobTab_UpdateJobMonitorRowStatus(job, t); 
            
            if ( jobMonitor.isFirstJobInMonitor(job) ) {
               // If 1st job then update title & progress bar
               String title = String.format("comskip: %s %s", t, config.kmttg);
               config.gui.setTitle(title);
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
         // No or empty outputFile means problems
         if ( ! file.isFile(outputFile) || file.isEmpty(outputFile) ) {
            failed = 1;
         }
         
         // NOTE: comskip returns exit status 1!
         //if (exit_code != 0) {
         //   failed = 1;
         //}
         
         if (failed == 1) {
            log.error("comskip failed (exit code: " + exit_code + " ) - check command: " + process.toString());
            process.printStderr();
         } else {
            log.warn("comskip job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE---");
            // Cleanup
            String[] extensions = {".log", ".logo.txt", ".txt"};
            String f;
            for (int i=0; i<extensions.length; ++i) {
               f = string.replaceSuffix(job.mpegFile, extensions[i]);
               // NOTE: Only delete a file ending in one of extensions
               if (f.endsWith(extensions[i]) && file.isFile(f)) file.delete(f);
            }
         }
      }
      return false;
   }

}
