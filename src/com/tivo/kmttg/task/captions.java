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
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.mediainfo;
import com.tivo.kmttg.util.string;

public class captions extends baseTask implements Serializable {
   private static final long serialVersionUID = 1L;
   private String srtFile;
   private backgroundProcess process;
   private jobData job;
   private String executable;
   private Boolean try_again = false;

   // constructor
   public captions(jobData job) {
      debug.print("job=" + job);
      if (job.videoFile.endsWith(".qsfix"))
         job.videoFile = job.videoFile.replaceFirst("\\.qsfix", "");
      if (config.VrdReview_noCuts == 1) {
         // Look for VRD default edit file output
         if (! file.isFile(job.videoFile)) {
            String tryit = file.vrdreviewFileSearch(job.startFile);
            if (tryit != null)
               job.videoFile = tryit;
         }
      }
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
      
      // Find TiVo or mpeg2 video file to process if job.videoFile not available
      if (! file.isFile(job.videoFile))
         log.warn("Intended video file not found, looking for alternate input file...");
      String videoFile = job.videoFile;
      String tryit;
      if ( ! file.isFile(videoFile) && config.VrdReview_noCuts == 1) {
         // Look for VRD default edit file output
         tryit = string.replaceSuffix(videoFile, " (02).mpg");
         if (file.isFile(tryit)) {
            videoFile = tryit;
         }
      }
      String[] suffixes = {".mpg", ".ts", ".mp4", ".TiVo"};
      for (int i=0; i<suffixes.length; ++i) {
         if ( ! file.isFile(videoFile) ) {
            tryit = string.replaceSuffix(videoFile, suffixes[i]);
            if (file.isFile(tryit)) {
               videoFile = tryit;
            }
         }
         if ( ! file.isFile(videoFile) && videoFile.contains("_cut")) {
            String v = videoFile.replaceFirst("_cut", "");
            tryit = string.replaceSuffix(v, suffixes[i]);
            if (file.isFile(tryit)) {
               videoFile = tryit;
               job.srtFile = string.replaceSuffix(v, ".srt");
               srtFile = job.srtFile;
            }
         }
      }
      
      if (file.isFile(videoFile)) {
         job.videoFile = videoFile;
      } else {
         log.error("captions - cannot find an input file to process");
         schedule = false;
      }
      
      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.srtFile, job) ) schedule = false;
      }
      
      if (schedule) {
         if ( start() ) {
            job.process = this;
            jobMonitor.updateJobStatus(job, "running");
            job.time             = new Date().getTime();
         }
         return true;
      } else {
         return false;
      }      
   }

   // Return false if starting command fails, true otherwise
   public Boolean start() {
      debug.print("");
      Stack<String> command = new Stack<String>();
      if (file.isFile(config.t2extract)) {
         executable = "t2extract";
         if (config.t2extract.contains("ccextractor"))
            executable = "ccextractor";
	      command.add(config.t2extract);
	      if (executable.equals("t2extract")) {
   	      command.add("-f");
   	      command.add("srt");
	      }
	      if (config.t2extract_args.length() > 0) {
	         String[] args = config.t2extract_args.split("\\s+");
	         for (int i=0; i<args.length; i++)
	            command.add(args[i]);
	      }
	      log.print(">> Running " + executable + " on " + job.videoFile + " ...");
      } else {
    	  executable = "ccextractor";
    	  command.add(config.ccextractor);
    	  log.print(">> Running ccextractor on " + job.videoFile + " ...");
      }
      if (executable.equals("ccextractor")) {
         // Collect mediainfo information when possible to add arguments if only EIA-708 captions present
         if (file.isFile(config.mediainfo)) {
            Hashtable<String,String> info = mediainfo.getVideoInfo(job.videoFile);
            if (info != null) {
               if (try_again || (! info.containsKey("EIA-608") && info.containsKey("EIA-708"))) {
                  try_again = true;
                  // EIA-708 captions require special options
                  // -autoprogram  -out=srt -bom -latin1 --nofontcolor -svc 1
                  command.add("-autoprogram");
                  command.add("-out=srt");
                  command.add("-bom");
                  command.add("-latin1");
                  command.add("--nofontcolor");
                  command.add("-svc");
                  command.add("1");
                  command.add("-o");
                  command.add(srtFile);
               }
            }
         }
      }
      command.add(job.videoFile);
      process = new backgroundProcess();
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
      //debug.print("");
      int exit_code = process.exitStatus();
      if (exit_code == -1) {
         // Still running
         if (config.GUIMODE) {
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
         // Check for problems
         int failed = 0;
         // No or empty srtFile means problems
         if ( ! file.isFile(srtFile) || file.isEmpty(srtFile) ) {
            failed = 1;
         }
         
         if (failed == 0) {
            // Very small file means problems
            if (file.size(srtFile) < 1000) {
               log.error("srt file size < 1000 probably means a problem");
               failed = 1;
            }
         }
         
         // exit code != 0 => trouble
         if (exit_code != 0) {
            failed = 1;
         }
         
         if (failed == 1) {
            if (try_again || ! executable.equals("ccextractor")) {
               log.error(executable + " failed (exit code: " + exit_code + " ) - check command: " + process.toString());
               process.printStderr();
            } else {
               try_again = true;
               file.delete(job.srtFile);
               log.warn("Trying ccextractor again with different options");
               if ( start() ) {
                  job.process = this;
                  jobMonitor.updateJobStatus(job, "running");
                  job.time = new Date().getTime();
                  return true;
               }
            }
            jobMonitor.removeFromJobList(job);
         } else {
            jobMonitor.removeFromJobList(job);
            log.warn(executable + " job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE--- job=" + job.type + " output=" + job.srtFile);
            // Rename srtFile to job.srtFile if they are different
            if ( ! srtFile.equals(job.srtFile) ) {
               file.rename(srtFile, job.srtFile);
            }
         }
      }
      return false;
   }

}
