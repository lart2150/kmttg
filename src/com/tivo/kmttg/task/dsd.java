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
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.ffmpeg;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.mediainfo;
import com.tivo.kmttg.util.string;

public class dsd extends baseTask implements Serializable {
   private static final long serialVersionUID = 1L;
   private backgroundProcess process;
   private jobData job;

   // constructor
   public dsd(jobData job) {
      debug.print("job=" + job);
      this.job = job;
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;
      // Don't decrypt if mpegFile already exists
      if ( file.isFile(job.mpegFile) ) {
         if (config.OverwriteFiles == 0) {
            log.warn("SKIPPING DECRYPT, FILE ALREADY EXISTS: " + job.mpegFile);
            schedule = false;
         } else {
            log.warn("OVERWRITING EXISTING FILE: " + job.mpegFile);
         }
      }
      
      if ( ! file.isFile(config.dsd) ) {
         log.error("dsd not found: " + config.dsd);
         schedule = false;
      }
      
      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.mpegFile, job) ) {
            schedule = false;
         }
      }
      
      if (schedule) {
         if ( start() ) {
            job.process = this;
            jobMonitor.updateJobStatus(job, "running");
            job.time            = new Date().getTime();
         }
         return true;
      } else {
         return false;
      }      
   }

   // Return false if starting command fails, true otherwise
   public Boolean start() {
      debug.print("");
      
      // Obtain some info on input video
      Hashtable<String,String> info = null;
      if (file.isFile(config.mediainfo))
         info = mediainfo.getVideoInfo(job.tivoFile);
      else if (file.isFile(config.ffmpeg))
         info = ffmpeg.getVideoInfo(job.tivoFile);
       
      Boolean isFileChanged = false;
      if (info != null && info.get("container").equals("mpegts")) {
         if (job.mpegFile.endsWith(".mpg")) {
            job.mpegFile = string.replaceSuffix(job.mpegFile, ".ts");
            isFileChanged = true;
         }
      } 
      
      if (isFileChanged) {            
         // If in GUI mode, update job monitor output field
         if (config.GUIMODE) {
            String output = string.basename(job.mpegFile);
            if (config.jobMonitorFullPaths == 1)
               output = job.mpegFile;
            config.gui.jobTab_UpdateJobMonitorRowOutput(job, output);
         }
         
         // Subsequent jobs need to have mpegFile && mpegFile_cut updated
         jobMonitor.updatePendingJobFieldValue(job, "mpegFile", job.mpegFile);
         String mpegFile_cut = job.mpegFile_cut.replaceFirst("_cut\\.mpg", "_cut.ts");
         jobMonitor.updatePendingJobFieldValue(job, "mpegFile_cut", mpegFile_cut);
         
         // Rename already created metadata file if relevant
         String meta = string.replaceSuffix(job.mpegFile, ".mpg") + ".txt";
         if (file.isFile(meta)) {
            String meta_new = job.mpegFile + ".txt";
            log.print("Renaming metadata file to: " + meta_new);
            file.rename(meta, meta_new);
            // Subsequent jobs need to have metaFile updated
            jobMonitor.updatePendingJobFieldValue(job, "metaFile", meta_new);
         }
         meta = string.replaceSuffix(job.mpegFile_cut, ".mpg") + ".txt";
         if (file.isFile(meta)) {
            String meta_new = job.mpegFile_cut + ".txt";
            log.print("Renaming metadata file to: " + meta_new);
            file.rename(meta, meta_new);
            // Subsequent jobs need to have metaFile updated
            jobMonitor.updatePendingJobFieldValue(job, "metaFile", meta_new);
         }
      }
         
      Stack<String> command = new Stack<String>();
      command.add(config.dsd);
      command.add("-s:" + job.tivoFile);
      command.add("-t:" + job.mpegFile);
      process = new backgroundProcess();
      log.print(">> DSD DECRYPT " + job.tivoFile + " ...");
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
            if ( file.isFile(job.mpegFile) ) {               
               // Update status in job table
               if ( job.tivoFileSize == null ) {
                  job.tivoFileSize = file.size(job.tivoFile);
               }
               String s = String.format("%.2f MB", (float)file.size(job.mpegFile)/Math.pow(2,20));
               String t = jobMonitor.getElapsedTime(job.time);
               int pct = Integer.parseInt(String.format("%d", file.size(job.mpegFile)*100/job.tivoFileSize));
                              
               if ( jobMonitor.isFirstJobInMonitor(job) ) {
                  // Update STATUS column
                  config.gui.jobTab_UpdateJobMonitorRowStatus(job, t + "---" + s);
                  
                  // If 1st job then update title & progress bar
                  String title = String.format("dsd: %d%% %s", pct, config.kmttg);
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
         // No or empty mpegFile means problems
         if ( ! file.isFile(job.mpegFile) || file.isEmpty(job.mpegFile) ) {
            failed = 1;
         }
         
         // exit code != 0 => trouble
         if (exit_code != 0) {
            failed = 1;
         }
         
         if (failed == 1) {
            log.error("dsd failed (exit code: " + exit_code + " ) - check command: " + process.toString());
            log.error("NOTE: You must have at least partial TiVo Desktop install for DirectShow Dump to work");
            process.printStderr();
            log.error(process.getStdout());
            jobMonitor.kill(job); // This called so that family of jobs is killed
         } else {
            log.warn("dsd job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE--- job=" + job.type + " output=" + job.mpegFile);
            
            // Remove .TiVo file if option enabled
            if (config.RemoveTivoFile == 1) {
               if ( file.delete(job.tivoFile) ) {
                  log.print("(Deleted file: " + job.tivoFile + ")");
               } else {
                  log.error("Failed to delete file: "+ job.tivoFile);
               }
            }
            
            // TivoWebPlus call to delete show on TiVo if configured
            if (job.twpdelete && ! config.rpcEnabled(job.tivoName)) {
               file.TivoWebPlusDelete(job.url);
            }
            
            // rpc style delete show on TiVo if configured
            if (job.rpcdelete && config.rpcEnabled(job.tivoName)) {
               String recordingId = rnpl.findRecordingId(job.tivoName, job.entry);
               if ( ! file.rpcDelete(job.tivoName, recordingId) )
                  log.error("Failed to delete show on TiVo");
            }
         }
      }
      return false;
   }

}
