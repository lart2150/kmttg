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

import java.io.File;
import java.io.Serializable;
import java.util.Date;
import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.SkipImport;
import com.tivo.kmttg.rpc.SkipManager;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class adscan extends baseTask implements Serializable {
   private static final long serialVersionUID = 1L;
   String  vrdscript = null;
   String  cscript = null;
   String  lockFile = null;
   private backgroundProcess process;
   private jobData job;

   // constructor
   public adscan(jobData job) {
      debug.print("job=" + job);
      this.job = job;
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;
      if (job.exportSkip) {
         // Generate cut points from AutoSkip data if available
         String vprjFile = SkipImport.vrdExport(job.entry);
         if (vprjFile != null)
            schedule = false;
      }
      // Don't adscan if vprjFile already exists
      if ( schedule && file.isFile(job.vprjFile) ) {
         if (config.OverwriteFiles == 0) {
            log.warn("SKIPPING ADSCAN, FILE ALREADY EXISTS: " + job.vprjFile);
            schedule = false;
         } else {
            log.warn("OVERWRITING EXISTING FILE: " + job.vprjFile);
         }
      }
      
      String s = File.separator;
      cscript = System.getenv("SystemRoot") + s + "system32" + s + "cscript.exe";
      
      vrdscript = config.programDir + "\\VRDscripts\\adscan.vbs";      
      if ( schedule && ! file.isFile(vrdscript) ) {
         log.error("File does not exist: " + vrdscript);
         log.error("Aborting. Fix incomplete kmttg installation");
         schedule = false;
      }
      
      lockFile = file.makeTempFile("VRDLock");      
      if ( schedule && (lockFile == null || ! file.isFile(lockFile)) ) {
         log.error("Failed to created lock file: " + lockFile);
         schedule = false;
      }
      
      if ( schedule && ! file.isFile(cscript) ) {
         log.error("File does not exist: " + cscript);
         schedule = false;
      }
                  
      if ( schedule && ! file.isFile(job.mpegFile) ) {
         if ( file.isFile(job.tivoFile)) {
            log.warn("adscan: mpeg file not found, so using TiVo file instead");
            job.mpegFile = job.tivoFile;
         } else {
            log.error("mpeg file not found: " + job.mpegFile);
            schedule = false;
         }
      }
      
      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.vprjFile, job) ) {
            schedule = false;
         }
      }
      
      if (schedule) {
         if ( start() ) {
            job.process = this;
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
   public Boolean start() {
      debug.print("");
      Stack<String> command = new Stack<String>();
      command.add(cscript);
      command.add("//nologo");
      command.add(vrdscript);
      command.add(job.mpegFile);
      command.add(job.vprjFile);
      command.add("/l:" + lockFile);
      if (config.VrdAllowMultiple == 1) {
         command.add("/m");
      }
      process = new backgroundProcess();
      log.print(">> Running adscan on " + job.mpegFile + " ...");
      if ( process.run(command) ) {
         log.print(process.toString());
      } else {
         log.error("Failed to start command: " + process.toString());
         process.printStderr();
         process = null;
         jobMonitor.kill(job);
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
         if (config.GUIMODE) {
            // Update status in job table
            String t = jobMonitor.getElapsedTime(job.time);
            int pct = encodeGetPct();
            
            // Update STATUS column
            config.gui.jobTab_UpdateJobMonitorRowStatus(job, String.format("%d%%",pct) + "---" + t);
            
            if ( jobMonitor.isFirstJobInMonitor(job) ) {
               // Update title & progress bar
               String title = String.format("adscan: %d%% %s", pct, config.kmttg);
               config.gui.setTitle(title);
               config.gui.progressBar_setValue(pct);
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
         // No or empty vprjFile means failure
         if ( ! file.isFile(job.vprjFile) || file.isEmpty(job.vprjFile) ) {
            failed = 1;
         }
         
         // exit status != 0 => problem
         if (exit_code != 0) {
            failed = 1;
         }
         
         if (failed == 1) {
            log.error("adscan failed (exit code: " + exit_code + " ) - check command: " + process.toString());
            process.printStderr();
            jobMonitor.kill(job); // This called so that family of jobs is killed
         } else {
            log.warn("adscan job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE--- job=" + job.type + " output=" + job.vprjFile);
            
            if (job.autoskip && config.VrdReview == 0 && file.isFile(job.vprjFile)) {
               // Skip table entry creation
               Stack<Hashtable<String,Long>> cuts = SkipImport.vrdImport(job.vprjFile, job.duration);
               if (cuts != null && cuts.size() > 0) {
                  if (SkipManager.hasEntry(job.contentId))
                     SkipManager.removeEntry(job.contentId);
                  SkipManager.saveEntry(job.contentId, job.offerId, 0L, job.title, job.tivoName, cuts);
               }
            }
         }
      }
      if (lockFile != null) file.delete(lockFile);
      return false;
   }   
   
   // Obtain pct complete from VRD stdout
   private int encodeGetPct() {
      String last = process.getStdoutLast();
      if (last.matches("")) return 0;
      if (last.contains("Progress: ")) {
         String[] all = last.split("Progress: ");
         if (all.length > 1) {
            String pct_str = all[1].replaceFirst("%", "");
            try {
               return (int)Float.parseFloat(pct_str);
            }
            catch (NumberFormatException n) {
               return 0;
            }
         }
      }
      return 0;
   }

}
