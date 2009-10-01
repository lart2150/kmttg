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

public class adcut {
   String  vrdscript = null;
   String  cscript = null;
   private backgroundProcess process;
   private jobData job;

   // constructor
   public adcut(jobData job) {
      debug.print("job=" + job);
      job.vprjFile = string.replaceSuffix(job.mpegFile, ".VPrj");

      this.job = job;
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;
      // Don't adcut if mpegFile_cut already exists
      if ( file.isFile(job.mpegFile_cut) ) {
         if (config.OverwriteFiles == 0) {
            log.warn("SKIPPING ADCUT, FILE ALREADY EXISTS: " + job.mpegFile_cut);
            schedule = false;
         } else {
            log.warn("OVERWRITING EXISTING FILE: " + job.mpegFile_cut);
         }
      }
      
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
      
      if ( ! file.isFile(job.vprjFile) ) {
         log.error("vprj file not found: " + job.vprjFile);
         schedule = false;
      }
            
      if ( ! file.isFile(job.mpegFile) ) {
         log.error("mpeg file not found: " + job.mpegFile);
         schedule = false;
      }
      
      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.mpegFile_cut, job) ) schedule = false;
      }
      
      if (schedule) {
         if ( start() ) {
            job.process_adcut    = this;
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
      command.add(cscript);
      command.add("//nologo");
      command.add(vrdscript);
      command.add(job.vprjFile);
      command.add(job.mpegFile_cut);
      command.add("/t1");
      command.add("/d");
      command.add("/q");
      command.add("/na");
      process = new backgroundProcess();
      log.print(">> Running adcut on " + job.mpegFile + " ...");
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
            if ( file.isFile(job.mpegFile_cut) ) {               
               // Update status in job table
               String s = String.format("%.2f MB", (float)file.size(job.mpegFile_cut)/Math.pow(2,20));
               String t = jobMonitor.getElapsedTime(job.time);
               config.gui.jobTab_UpdateJobMonitorRowStatus(job, t + "---" + s);
               
               if ( jobMonitor.isFirstJobInMonitor(job) ) {
                  // If 1st job then update title & progress bar
                  String title = String.format("adcut: %s %s", t, config.kmttg);
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
         // No or empty mpegFile_cut means problems
         if ( ! file.isFile(job.mpegFile_cut) || file.isEmpty(job.mpegFile_cut) ) {
            failed = 1;
         }
         
         // exit status != 0 => problem
         if (exit_code != 0) {
            failed = 1;
         }
         
         if (failed == 1) {
            log.error("adcut failed (exit code: " + exit_code + " ) - check command: " + process.toString());
            process.printStderr();
         } else {
            log.warn("adcut job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE---");
            // Remove vprjFile & mpegFile files if option enabled
            if ( config.RemoveComcutFiles == 1 ) {
               file.delete(job.vprjFile);
               log.print("(Deleted vprj file: " + job.vprjFile + ")");
               file.delete(job.mpegFile);
               log.print("(Deleted mpeg file: " + job.mpegFile + ")");
               file.delete(job.edlFile);
               log.print("(Deleted edl file: " + job.edlFile + ")");
            }
         }
      }
      return false;
   }

}
