package com.tivo.kmttg.task;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.Stack;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.*;

public class metadata implements Serializable {
   private static final long serialVersionUID = 1L;
   String cookieFile = "";
   String outputFile = "";
   private backgroundProcess process;
   public jobData job;
   
   public metadata(jobData job) {
      debug.print("job=" + job);
      this.job = job;
      
      // Generate unique cookieFile and outputFile names
      cookieFile = file.makeTempFile("cookie");
      outputFile = file.makeTempFile("meta");
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;
      if ( file.isFile(job.metaFile) ) {
         if (config.OverwriteFiles == 0) {
            log.warn("SKIPPING METADATA GENERATION, FILE ALREADY EXISTS: " + job.metaFile);
            schedule = false;
         } else {
            log.warn("OVERWRITING EXISTING FILE: " + job.metaFile);
         }
      }
      if ( ! file.isFile(config.curl) ) {             
         log.error("curl not found: " + config.curl);
         schedule = false;
      }

      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.metaFile, job) ) schedule = false;
      }
      
      if (schedule) {
         if ( start() ) {
            job.process_metadata = this;
            jobMonitor.updateJobStatus(job, "running");
            job.time             = new Date().getTime();
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
      Stack<String> command = new Stack<String>();
      command.add(config.curl);
      if (config.OS.equals("windows")) {
         command.add("--retry");
         command.add("3");
      }
      command.add("--anyauth");
      command.add("--globoff");
      command.add("--user");
      command.add("tivo:" + config.MAK);
      command.add("--insecure");
      command.add("--cookie-jar");
      command.add(cookieFile);
      command.add("--url");
      command.add(job.url);
      command.add("--output");
      command.add(outputFile);
      process = new backgroundProcess();
      log.print(">> CREATING " + job.metaFile + " ...");
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
      file.delete(cookieFile);
      file.delete(outputFile);
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
            config.gui.jobTab_UpdateJobMonitorRowStatus(job, "running");
         }
         return true;
      } else {
         // Job finished
         jobMonitor.removeFromJobList(job);
         
         // Check for problems
         int failed = 0;
         
         // exit code != 0 => trouble
         if (exit_code != 0) {
            failed = 1;
         }
         
         // No or empty output means problems         
         if ( file.isEmpty(outputFile) ) {
            failed = 1;
         }
         
         // Check that first line is xml
         if (failed == 0) {
            try {
               BufferedReader xml = new BufferedReader(new FileReader(outputFile));
               String first = xml.readLine();
               xml.close();
               if ( ! first.toLowerCase().matches("^.+xml.+$") ) {
                  failed = 1;
                  log.error(first);
               }
            }
            catch (IOException ex) {
               failed = 1;
            }
         }
         
         if (failed == 1) {
            log.error("Failed to generate metadata file: " + job.metaFile);
            log.error("Exit code: " + exit_code);
            process.printStderr();
         } else {
            log.warn("metadata job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE--- job=" + job.type + " output=" + job.metaFile);
            
            // Success, so create pyTivo metadata file
            job.metaTmpFile = outputFile;
            createMeta.createMetaFile(job, cookieFile);
         }
      }
      file.delete(cookieFile);
      file.delete(outputFile);
      
      return false;
   }
}
