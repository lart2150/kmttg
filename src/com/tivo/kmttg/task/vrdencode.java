package com.tivo.kmttg.task;

import java.io.File;
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

public class vrdencode {
   String  vrdscript = null;
   String  cscript = null;
   private backgroundProcess process;
   private jobData job;

   // constructor
   public vrdencode(jobData job) {
      debug.print("job=" + job);
      this.job = job;
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;
            
      String s = File.separator;
      vrdscript = config.VRD + s + "vp.vbs";
      cscript = System.getenv("SystemRoot") + s + "system32" + s + "cscript.exe";
      
      if ( ! file.isFile(vrdscript) ) {
         log.error("File does not exist: " + vrdscript);
         schedule = false;
      }
      
      if ( ! file.isFile(cscript) ) {
         log.error("File does not exist: " + cscript);
         schedule = false;
      }
      
      // Don't encode if encodeFile already exists
      if ( file.isFile(job.encodeFile) ) {
         if (config.OverwriteFiles == 0) {
            log.warn("SKIPPING ENCODE, FILE ALREADY EXISTS: " + job.encodeFile);
            schedule = false;
         } else {
            log.warn("OVERWRITING EXISTING FILE: " + job.encodeFile);
         }
      }
      
      // Decide which file needs to be encoded and update args accordingly
      String mpeg;
      if ( file.isFile(job.mpegFile_cut) ) {
         mpeg = job.mpegFile_cut;
      } else {
         mpeg = job.mpegFile;
         if (config.VrdReview_noCuts == 1) {
            // Look for VRD default edit file output
            String tryit = string.replaceSuffix(mpeg, " (02).mpg");
            if (file.isFile(tryit))
               mpeg = tryit;
         }
      }
      if ( ! file.isFile(mpeg)) {
         mpeg = job.tivoFile;
      }
      
      if ( ! file.isFile(mpeg) ) {
         log.error("mpeg file not given or doesn't exist: " + mpeg);
         schedule = false;
      }
      job.inputFile = mpeg;
                  
      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.encodeFile, job) ) {
            schedule = false;
         }
      }
      
      if (schedule) {
         if ( start() ) {
            job.process_vrdencode = this;
            jobMonitor.updateJobStatus(job, "running");
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
      command.add(cscript);
      command.add("//nologo");
      command.add(vrdscript);
      command.add(job.inputFile);
      command.add(job.encodeFile);
      command.add("/d");
      command.add("/q");
      command.add("/na");
      command.add("/p:" + job.encodeName);
      process = new backgroundProcess();
      log.print(">> ENCODING WITH PROFILE '" + job.encodeName + "' TO FILE " + job.encodeFile + " ...");
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
            if ( file.isFile(job.encodeFile) ) {               
               // Update status in job table
               String s = String.format("%.2f MB", (float)file.size(job.encodeFile)/Math.pow(2,20));
               String t = jobMonitor.getElapsedTime(job.time);
                              
               if ( jobMonitor.isFirstJobInMonitor(job) ) {
                  // Update STATUS column 
                  config.gui.jobTab_UpdateJobMonitorRowStatus(job, t + "---" + s);
                  
                  // If 1st job then update title & progress bar
                  String title = String.format("vrdencode: %s %s", t, config.kmttg);
                  config.gui.setTitle(title);
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
         // No or empty encodeFile means problems
         if ( ! file.isFile(job.encodeFile) || file.isEmpty(job.encodeFile) ) {
            failed = 1;
         }
         
         // exit status != 0 => problem
         if (exit_code != 0) {
            failed = 1;
         }
         
         if (failed == 1) {
            log.error("vrdencode failed (exit code: " + exit_code + " ) - check command: " + process.toString());
            process.printStderr();
         } else {
            log.warn("vrdencode job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE--- job=" + job.type + " output=" + job.encodeFile);            
            
            // Remove mpegFile.qsfix file if present
            String fix;            
            if (job.mpegFile.matches("^.+\\.qsfix$"))
               fix = job.mpegFile;
            else
               fix = job.mpegFile + ".qsfix";
            if (file.isFile(fix)) {
               if (file.delete(fix)) log.print("(Deleted file: " + fix + ")");
            }
            
            // Remove .mpg file if option enabled
            if (config.RemoveMpegFile == 1) {
               if ( file.delete(job.inputFile) ) {
                  log.print("(Deleted file: " + job.inputFile + ")");
               } else {
                  log.error("Failed to delete file: "+ job.inputFile);
               }
               
               if ( file.delete(job.mpegFile)) {
                  log.print("(Deleted file: " + job.mpegFile + ")");
               }
            }
            
            // Schedule an AtomicParsley job if relevant
            if (file.isFile(config.AtomicParsley)) {
               job.metaFile = job.encodeFile + ".txt";
               if ( ! file.isFile(job.metaFile) ) {
                  job.metaFile = job.mpegFile_cut + ".txt";
               }
               if ( ! file.isFile(job.metaFile) ) {
                  job.metaFile = job.mpegFile + ".txt";
               }
               if ( file.isFile(job.metaFile) &&
                    (job.encodeFile.toLowerCase().endsWith(".mp4") ||
                     job.encodeFile.toLowerCase().endsWith(".m4v")) ) {
                  jobData new_job = new jobData();
                  new_job.source       = job.source;
                  new_job.tivoName     = job.tivoName;
                  new_job.type         = "atomic";
                  new_job.name         = config.AtomicParsley;
                  new_job.encodeFile   = job.encodeFile;
                  new_job.metaFile     = job.metaFile;
                  jobMonitor.submitNewJob(new_job);
               }
            }
         }
      }
      return false;
   }

}

