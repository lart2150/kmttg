/*
 * Copyright 2008-Present Kevin Moye <moyekj@yahoo.com>.
 *
 * This file is part of kmttg package.
 *
 * kmttg is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this project.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tivo.kmttg.task;

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

public class fffix extends baseTask implements Serializable {
   private static final long serialVersionUID = 1L;
   private backgroundProcess process;
   private jobData job;

   // constructor
   public fffix(jobData job) {
      debug.print("job=" + job);
      this.job = job;
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;
      if ( ! file.isFile(config.ffmpeg) ) {
         log.error("ffmpeg not found: " + config.ffmpeg);
         schedule = false;
      }
            
      if ( ! file.isFile(job.mpegFile) ) {
         log.error("mpeg file not found: " + job.mpegFile);
         schedule = false;
      }
      
      if (schedule) {
         // Create sub-folders for output files if needed
         if ( ! jobMonitor.createSubFolders(job.mpegFile, job) ) {
            schedule = false;
         }
         if ( ! jobMonitor.createSubFolders(job.mpegFile_fix, job) ) {
            schedule = false;
         }
      }
      
      if (schedule) {
         if ( start() ) {
            job.process = this;
            jobMonitor.updateJobStatus(job, "running");
            job.time    = new Date().getTime();
         }
         return true;
      } else {
         return false;
      }      
   }

   // Return false if starting command fails, true otherwise
   public Boolean start() {
      debug.print("");
      Hashtable<String,String> info = ffmpeg.getVideoInfo(job.mpegFile);
      String format = "dvd";
      String video = "mpeg2";
      if (info != null) {
         if (info.get("container").equals("mpegts"))
            format = "mpegts";
         if (info.get("container").equals("mp4"))
            format = "mp4";
         if (info.get("video").equals("h264"))
            video = "h264";
      }
      Stack<String> command = new Stack<String>();
      command.add(config.ffmpeg);
      if (video.equals("mpeg2")) {
         command.add("-fflags");
         command.add("+genpts+igndts");
      }
      command.add("-i");
      command.add(job.mpegFile);
      command.add("-acodec");
      command.add("copy");
      command.add("-vcodec");
      command.add("copy");
      command.add("-avoid_negative_ts");
      command.add("make_zero");
      command.add("-f");
      command.add(format);
      command.add("-y");
      command.add(job.mpegFile_fix);
      process = new backgroundProcess();
      log.print(">> Running fffix on " + job.mpegFile + " ...");
      if ( process.run(command) ) {
         log.print(process.toString());
      } else {
         log.error("Failed to start command: " + process.toString());
         process.printStderr();
         process = null;
         jobMonitor.kill(job);
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
      int exit_code = process.exitStatus();
      if (exit_code == -1) {
         // Still running
         if (config.GUIMODE) {
            if ( file.isFile(job.mpegFile_fix) ) {               
               // Update status in job table
               String s = String.format("%.2f MB",
                  (float)file.size(job.mpegFile_fix)/Math.pow(2,20)
               );
               String t = jobMonitor.getElapsedTime(job.time);
               int pct = Integer.parseInt(String.format("%d",
                  file.size(job.mpegFile_fix)*100/file.size(job.mpegFile))
               );
                              
               if ( jobMonitor.isFirstJobInMonitor(job) ) {
                  // Update STATUS column 
                  config.gui.jobTab_UpdateJobMonitorRowStatus(job, t + "---" + s);
                  
                  // If 1st job then update title & progress bar
                  String title = String.format("fffix: %d%% %s", pct, config.kmttg);
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
         if (config.GUIMODE) {
            if ( jobMonitor.isFirstJobInMonitor(job) ) {
               config.gui.setTitle(config.kmttg);
               config.gui.progressBar_setValue(0);
            }
         }
         jobMonitor.removeFromJobList(job);
         
         // Check for problems
         int failed = 0;
         // No or empty mpegFile_fix means problems
         if ( ! file.isFile(job.mpegFile_fix) || file.isEmpty(job.mpegFile_fix) ) {
            log.error("Unable to find output file: " + job.mpegFile_fix);
            failed = 1;
         }
         
         // exit status != 0 => problem
         if (exit_code != 0) {
            failed = 1;
         }
         
         if (failed == 1) {
            log.error("fffix failed (exit code: " + exit_code + " ) - check command: " + process.toString());
            process.printStderr();
            jobMonitor.kill(job); // This called so that family of jobs is killed
         } else {
            log.warn("fffix job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE--- job=" + job.type);
            
            // Remove original .mpg file if remove option enabled
            if ( job.mpegFile_cut != null && config.RemoveComcutFiles_mpeg == 1 ) {
               if (file.delete(job.mpegFile))
                  log.print("(Deleted mpeg file: " + job.mpegFile + ")");
            }
            
            // Rename job.mpegFile_fix to mpegFile
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
                     return false;                     
                  }
               } else {
                  // Remove mpegFile if it exists
                  result = file.delete(job.mpegFile);
                  if ( ! result ) {
                     log.error("Failed to delete file in preparation for rename: " + job.mpegFile);
                     return false;
                  }
               }
            }
            // Create sub-folders for mpegFile if needed
            if ( ! jobMonitor.createSubFolders(job.mpegFile, job) )
               log.error("Failed to create mpegFile directory");
            // Now do the file rename
            result = file.rename(job.mpegFile_fix, job.mpegFile);
            if (result)
               log.print("(Renamed " + job.mpegFile_fix + " to " + job.mpegFile + ")");
            else
               log.error("Failed to rename " + job.mpegFile_fix + " to " + job.mpegFile);
         }
      }
      return false;
   }
}
