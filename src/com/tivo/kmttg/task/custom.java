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

public class custom {
   private backgroundProcess process;
   private jobData job;

   // constructor
   public custom(jobData job) {
      debug.print("job=" + job);
      this.job = job;
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;
            
      if ( ! customCommandExists() ) {
         log.error("Invalid custom command: " + config.customCommand);
         schedule = false;
      }
                  
      if (schedule) {
         if ( start() ) {
            job.process_custom   = this;
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
      if (job.tivoFile == null) job.tivoFile = "";
      if (job.metaFile == null) job.metaFile = "";
      if (job.mpegFile == null) job.mpegFile = "";
      if (job.mpegFile_cut == null) job.mpegFile_cut = "";
      if (job.srtFile == null) job.srtFile = "";
      if (job.encodeFile == null) job.encodeFile = "";
      
      // Build initial literal command
      Stack<String>s = buildCustomCommandStack(config.customCommand);
      
      // Replace any special file arguments with their real values
      Stack<String>command = new Stack<String>();
      String string;
      for (int i=0; i<s.size(); ++i) {
         string = s.get(i);
         string = string.replaceAll("\\[tivoFile\\]", escapeBackSlashes(job.tivoFile));
         string = string.replaceAll("\\[metaFile\\]", escapeBackSlashes(job.metaFile));
         string = string.replaceAll("\\[mpegFile\\]", escapeBackSlashes(job.mpegFile));
         string = string.replaceAll("\\[mpegFile_cut\\]", escapeBackSlashes(job.mpegFile_cut));
         string = string.replaceAll("\\[srtFile\\]", escapeBackSlashes(job.srtFile));
         string = string.replaceAll("\\[encodeFile\\]", escapeBackSlashes(job.encodeFile));
         command.add(string);
      }
      
      process = new backgroundProcess();
      log.print(">> Running custom command ...");
      if ( process.run(command) ) {
         job.customCommand = process.toString();
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
               String title = String.format("custom: %s %s", t, config.kmttg);
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
         
         log.warn("custom job completed: " + jobMonitor.getElapsedTime(job.time));
         log.warn("exit code: " + exit_code);
         log.print("---DONE---");
      }
      return false;
   }
   
   public static Boolean customCommandExists() {
      debug.print("");
      return customCommandExists(config.customCommand);
   }
   
   public static Boolean customCommandExists(String command) {
      debug.print("command=" + command);
      if (command == null) return false;
      if (command.length() > 0) {
         Stack<String> s = buildCustomCommandStack(command);
         if ( file.isFile(s.get(0)) ) return true;
      }
      return false;
   }
   
   private static Stack<String> buildCustomCommandStack(String command) {
      debug.print("command=" + command);
      Stack<String> c = new Stack<String>();
      // First separate out any quotes
      if (command.contains("\"") ) {
         String[] p = command.split("\"");
         String s;
         for(int i=0; i<p.length; ++i) {
            s = p[i];
            s = string.removeLeadingTrailingSpaces(s);
            if (s.length() > 0 ) c.add(s);
         }
      } else {
         String[] p = command.split("\\s+");
         for(int i=0; i<p.length; ++i) {
            c.add(p[i]);
         }
      }
      return c;
   }
   
   // It's crazy but in Java regex one backslash = 4 ...
   private static String escapeBackSlashes(String s) {
      return s.replaceAll("\\\\", "\\\\\\\\");
   }
   
}
