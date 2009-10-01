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

public class captions {
   private String srtFile;
   private backgroundProcess process;
   private jobData job;
   private String executable;

   // constructor
   public captions(jobData job) {
      debug.print("job=" + job);
      srtFile = string.replaceSuffix(job.videoFile, ".srt");
      this.job = job;
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;
      // Don't encode if mpegFile already exists
      if ( file.isFile(job.srtFile) ) {
         if (config.OverwriteFiles == 0) {
            log.warn("SKIPPING CAPTIONS, FILE ALREADY EXISTS: " + job.srtFile);
            schedule = false;
         } else {
            log.warn("OVERWRITING EXISTING FILE: " + job.srtFile);
         }
      }
      
      if ( ! file.isFile(config.t2extract) && ! file.isFile(config.ccextractor) ) {
         log.error("t2extract (" + config.t2extract + ") or ccextractor (" + config.ccextractor + ") not found");
         schedule = false;
      }
      
      if ( ! file.isFile(job.videoFile) ) {
         log.error("video file not found: " + job.videoFile);
         schedule = false;
      }
      
      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.srtFile, job) ) schedule = false;
      }
      
      if (schedule) {
         if ( start() ) {
            job.process_captions = this;
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
      if (file.isFile(config.t2extract)) {
    	  executable = "t2extract";
	      command.add(config.t2extract);
	      command.add("-f");
	      command.add("srt");
	      if (config.t2extract_args.length() > 0) {
	         String[] args = config.t2extract_args.split("\\s+");
	         for (int i=0; i<args.length; i++)
	            command.add(args[i]);
	      }
	      log.print(">> Running t2extract on " + job.videoFile + " ...");
      } else {
    	  executable = "ccextractor";
    	  command.add(config.ccextractor);
          log.print(">> Running ccextractor on " + job.videoFile + " ...");
      }
      command.add(job.videoFile);
      process = new backgroundProcess();
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
            if ( file.isFile(srtFile) ) {               
               // Update status in job table
               String s = String.format("%.2f KB", (float)file.size(srtFile)/Math.pow(2,10));
               String t = jobMonitor.getElapsedTime(job.time);
               config.gui.jobTab_UpdateJobMonitorRowStatus(job, t + "---" + s);               
            }
         }
        return true;
      } else {
         // Job finished         
         jobMonitor.removeFromJobList(job);
         
         // Check for problems
         int failed = 0;
         // No or empty srtFile means problems
         if ( ! file.isFile(srtFile) || file.isEmpty(srtFile) ) {
            failed = 1;
         }
         
         // exit code != 0 => trouble
         if (exit_code != 0) {
            failed = 1;
         }
         
         if (failed == 1) {
            log.error(executable + " failed (exit code: " + exit_code + " ) - check command: " + process.toString());
            process.printStderr();
         } else {
            log.warn(executable + " job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE---");
            // Rename srtFile to job.srtFile if they are different
            if ( ! srtFile.equals(job.srtFile) ) {
               file.rename(srtFile, job.srtFile);
            }
         }
      }
      return false;
   }

}
