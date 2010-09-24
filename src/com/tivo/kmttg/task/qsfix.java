package com.tivo.kmttg.task;

import java.io.File;
import java.io.Serializable;
import java.util.Date;
import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.ffmpeg;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class qsfix implements Serializable {
   private static final long serialVersionUID = 1L;
   String  vrdscript = null;
   String  cscript = null;
   String  sourceFile = null;
   String  lockFile = null;
   private backgroundProcess process;
   private jobData job;

   // constructor
   public qsfix(jobData job) {
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
      cscript = System.getenv("SystemRoot") + s + "system32" + s + "cscript.exe";
      
      sourceFile = job.mpegFile;
      if (config.VrdDecrypt == 1 && ! file.isFile(sourceFile)) {
         sourceFile = job.tivoFile;
      }
                  
      if ( ! file.isFile(sourceFile) ) {
         log.error("source file not found: " + sourceFile);
         schedule = false;
      }
      
      if (schedule && config.OverwriteFiles == 0 && sourceFile.equals(job.tivoFile) && file.isFile(job.mpegFile)) {
         log.warn("SKIPPING QSFIX, FILE ALREADY EXISTS: " + job.mpegFile);
         schedule = false;
      }
      
      if ( schedule && ! file.isFile(cscript) ) {
         log.error("File does not exist: " + cscript);
         schedule = false;
      }

      if ( schedule ) {
         lockFile = file.makeTempFile("VRDLock");      
         if ( lockFile == null || ! file.isFile(lockFile) ) {
            log.error("Failed to created lock file: " + lockFile);
            schedule = false;
         }
      }
            
      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.mpegFile_fix, job) ) {
            schedule = false;
         }
      }

      if (schedule) {
         if ( start() ) {
            job.process_qsfix    = this;
            jobMonitor.updateJobStatus(job, "running");
            job.time             = new Date().getTime();
         }
         return true;
      } else {
         if (lockFile != null) file.delete(lockFile);
         return false;
      }     
   }

   // Return false if starting command fails, true otherwise
   private Boolean start() {
      debug.print("");
      // Check to see if dimension filter is enabled and figure out dimensions if so
      Hashtable<String,String> dimensions = null;
      if (config.VrdQsFilter == 1) {
         // Create script with video dimensions filter enabled
         log.warn("VideoRedo video dimensions filter is enabled");
         dimensions = ffmpeg.getVideoDimensions(sourceFile);
         if (dimensions == null) {
            log.warn("ffmpeg on source file didn't work - trying to get dimensions from 2 sec clip");
            String destFile = file.makeTempFile("mpegFile", ".mpg");
            dimensions = getDimensionsFromShortClip(sourceFile, destFile);
         }
         if (dimensions == null) {
            log.error("VRD QS Filter enabled but unable to determine video dimensions of file: " + sourceFile);
            jobMonitor.removeFromJobList(job);
            return false;
         }    
      }
      // Create the vbs script
      vrdscript = config.programDir + "\\VRDscripts\\qsfix.vbs";      
      if ( ! file.isFile(vrdscript) ) {
         log.error("File does not exist: " + vrdscript);
         log.error("Aborting. Fix incomplete kmttg installation");
         jobMonitor.removeFromJobList(job);
         return false;
      }

      Stack<String> command = new Stack<String>();
      command.add(cscript);
      command.add("//nologo");
      command.add(vrdscript);
      command.add(sourceFile);
      command.add(job.mpegFile_fix);
      command.add("/l:" + lockFile);
      if (config.VrdAllowMultiple == 1) {
         command.add("/m");
      }
      if (dimensions != null) {
         command.add("/x:" + dimensions.get("x"));
         command.add("/y:" + dimensions.get("y"));
         log.warn("VideoRedo video dimensions filter set to: x=" + dimensions.get("x") + ", y=" + dimensions.get("y"));
      }
      process = new backgroundProcess();
      log.print(">> Running qsfix on " + sourceFile + " ...");
      if ( process.run(command) ) {
         log.print(process.toString());
      } else {
         log.error("Failed to start command: " + process.toString());
         process.printStderr();
         process = null;
         jobMonitor.removeFromJobList(job);
         if (lockFile != null) file.delete(lockFile);
         return false;
      }
      return true;
   }
   
   public void kill() {
      debug.print("");
      // NOTE: Instead of process.kill VRD jobs are special case where removing lockFile
      // causes VB script to close VRD. (Otherwise script is killed but VRD still runs).
      file.delete(lockFile);
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
               String s = String.format("%.2f MB", (float)file.size(job.mpegFile_fix)/Math.pow(2,20));
               String t = jobMonitor.getElapsedTime(job.time);
               int pct = Integer.parseInt(String.format("%d", file.size(job.mpegFile_fix)*100/file.size(sourceFile)));
                              
               if ( jobMonitor.isFirstJobInMonitor(job) ) {
                  // Update STATUS column 
                  config.gui.jobTab_UpdateJobMonitorRowStatus(job, t + "---" + s);
                  
                  // If 1st job then update title & progress bar
                  String title = String.format("qsfix: %d%% %s", pct, config.kmttg);
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
            log.error("qsfix failed (exit code: " + exit_code + " ) - check command: " + process.toString());
            process.printStderr();
         } else {
            log.warn("qsfix job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE--- job=" + job.type + " output=" + job.mpegFile_fix);
            
            // Remove .TiVo file if option enabled
            if (job.tivoFile != null && config.RemoveTivoFile == 1 && config.VrdDecrypt == 1) {
               if ( file.delete(job.tivoFile) ) {
                  log.print("(Deleted file: " + job.tivoFile + ")");
               } else {
                  log.error("Failed to delete file: "+ job.tivoFile);
               }
            }      
            
            // TivoWebPlus call to delete show on TiVo if configured
            if (job.twpdelete) {
               file.TivoWebPlusDelete(job.url);
            }
            
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
                     if (lockFile != null) file.delete(lockFile);
                     return false;                     
                  }
               } else {
                  // Remove mpegFile if it exists
                  result = file.delete(job.mpegFile);
                  if ( ! result ) {
                     log.error("Failed to delete file in preparation for rename: " + job.mpegFile);
                     if (lockFile != null) file.delete(lockFile);
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
      if (lockFile != null) file.delete(lockFile);
      return false;
   }
      
   // This procedure uses VRD to create a short 2 sec mpeg2 clip and then calls
   // ffmpegGetVideoDimensions on that clip to try and obtain video dimensions
   // This is a fallback call in case getting dimensions directly from source file fails.
   private Hashtable<String,String> getDimensionsFromShortClip(String sourceFile, String destFile) { 
      String script = config.programDir + "\\VRDscripts\\createShortClip.vbs";
      if ( ! file.isFile(script) ) {
         log.error("File does not exist: " + script);
         log.error("Aborting. Fix incomplete kmttg installation");
         return null;
      }
      
      // Run VRD with above script      
      Stack<String> command = new Stack<String>();
      command.add(cscript);
      command.add("//nologo");
      command.add(script);
      command.add(sourceFile);
      command.add(destFile);
      if (config.VrdAllowMultiple == 1) {
         command.add("/m");
      }
      backgroundProcess process = new backgroundProcess();
      if ( process.run(command) ) {
         log.print(process.toString());
         // Wait for command to terminate
         process.Wait();
         if (process.exitStatus() != 0) {
            log.error("VideoRedo run failed");
            log.error(process.getStderr());
            return null;
         }
      } 
      Hashtable<String,String> dimensions = ffmpeg.getVideoDimensions(destFile);
      file.delete(destFile);
      return(dimensions);
   }
}
