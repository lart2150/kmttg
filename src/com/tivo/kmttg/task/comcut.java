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

public class comcut {
   private backgroundProcess process;
   private jobData job;

   // constructor
   public comcut(jobData job) {
      debug.print("job=" + job);
      this.job = job;
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;
      // Don't comcut if mpegFile_cut already exists
      if ( file.isFile(job.mpegFile_cut) ) {
         if (config.OverwriteFiles == 0) {
            log.warn("SKIPPING COMCUT, FILE ALREADY EXISTS: " + job.mpegFile_cut);
            schedule = false;
         } else {
            log.warn("OVERWRITING EXISTING FILE: " + job.mpegFile_cut);
         }
      }
            
      if ( ! file.isFile(config.mencoder) ) {
         log.error("mencoder not found: " + config.mencoder);
         schedule = false;
      }
      
      if ( ! file.isFile(job.edlFile) ) {
         log.error("edl file not found: " + job.edlFile);
         schedule = false;
      }
            
      if ( ! file.isFile(job.mpegFile) ) {
         log.error("mpeg file not found: " + job.mpegFile);
         schedule = false;
      }
      
      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.mpegFile_cut, job) ) {
            schedule = false;
         }
      }
      
      if (schedule) {
         if ( start() ) {
            job.process_comcut   = this;
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
      command.add(config.mencoder);
      command.add(job.mpegFile);
      command.add("-edl");
      command.add(job.edlFile);
      command.add("-oac");
      command.add("copy");
      command.add("-ovc");
      command.add("copy");
      command.add("-of");
      command.add("mpeg");
      command.add("-vf");
      command.add("harddup");
      if (config.mencoder_args.length() > 0) {
         String[] args = config.mencoder_args.split("\\s+");
         for (int i=0; i<args.length; i++)
            command.add(args[i]);
      }
      //command.add("-noskip");
      //command.add("-mpegopts");
      //command.add("vbuf_size=400");
      command.add("-o");
      command.add(job.mpegFile_cut);
      process = new backgroundProcess();
      log.print(">> Running comcut on " + job.mpegFile + " ...");
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
                  String title = String.format("comcut: %s %s", t, config.kmttg);
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
            log.error("comcut failed (exit code: " + exit_code + " ) - check command: " + process.toString());
            process.printStderr();
         } else {
            log.warn("comcut job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE--- job=" + job.type + " output=" + job.mpegFile_cut);
            // Remove Ad Cut files if option enabled
            if ( config.RemoveComcutFiles == 1 ) {
               if (file.delete(job.vprjFile))
                  log.print("(Deleted vprj file: " + job.vprjFile + ")");
               if (file.delete(job.edlFile))
                  log.print("(Deleted edl file: " + job.edlFile + ")");
            }
            // Remove .mpg file if option enabled
            if ( config.RemoveComcutFiles_mpeg == 1 ) {
               if (file.delete(job.mpegFile))
                  log.print("(Deleted mpeg file: " + job.mpegFile + ")");
            }
         }
      }
      return false;
   }

}
