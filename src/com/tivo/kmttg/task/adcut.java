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
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.ffmpeg;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.mediainfo;
import com.tivo.kmttg.util.string;

public class adcut extends baseTask implements Serializable {
   private static final long serialVersionUID = 1L;
   String  vrdscript = null;
   String  cscript = null;
   String  lockFile = null;
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
      cscript = System.getenv("SystemRoot") + s + "system32" + s + "cscript.exe";
      
      vrdscript = config.programDir + "\\VRDscripts\\adcut.vbs";      
      if ( ! file.isFile(vrdscript) ) {
         log.error("File does not exist: " + vrdscript);
         log.error("Aborting. Fix incomplete kmttg installation");
         schedule = false;
      }
      
      lockFile = file.makeTempFile("VRDLock");      
      if ( lockFile == null || ! file.isFile(lockFile) ) {
         log.error("Failed to created lock file: " + lockFile);
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
         if ( file.isFile(job.tivoFile)) {
            log.warn("adcut: mpeg file not found, so using TiVo file instead");
            job.mpegFile = job.tivoFile;
         } else {
            log.error("mpeg file not found: " + job.mpegFile);
            schedule = false;
         }
      }
      
      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.mpegFile_cut, job) ) {
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
      
      Hashtable<String,String> info = null;
      if (file.isFile(config.mediainfo))
         info = mediainfo.getVideoInfo(job.mpegFile);
      else if (file.isFile(config.ffmpeg))
         info = ffmpeg.getVideoInfo(job.mpegFile);
      // Handle input files different than mpeg2 program stream
      // which changes output file suffix from .mpg to something else
      Boolean isFileChanged = false;
      if (info != null && info.get("container").equals("mpegts")) {
         if (job.mpegFile_cut.endsWith(".mpg")) {
            job.mpegFile_cut = string.replaceSuffix(job.mpegFile_cut, ".ts");
            isFileChanged = true;
         }
      }      
      if (info != null && info.get("container").equals("mp4")) {
         if (job.mpegFile_cut.endsWith(".mpg")) {
            job.mpegFile_cut = string.replaceSuffix(job.mpegFile_cut, ".mp4");
            isFileChanged = true;
         }
      }      
      if (isFileChanged) {                     
         // Rename already created metadata file if relevant
         String meta = string.replaceSuffix(job.mpegFile_cut, ".mpg") + ".txt";
         if (file.isFile(meta)) {
            String meta_new = job.mpegFile_cut + ".txt";
            log.print("Renaming metadata file to: " + meta_new);
            file.rename(meta, meta_new);
            // Subsequent jobs need to have metaFile updated
            jobMonitor.updatePendingJobFieldValue(job, "metaFile", meta_new);
         }
         
         // Subsequent jobs need to have mpegFile updated
         jobMonitor.updatePendingJobFieldValue(job, "mpegFile_cut", job.mpegFile_cut);
      }
      
      // If in GUI mode, update job monitor output field
      if (config.GUIMODE) {
         String output = string.basename(job.mpegFile_cut);
         if (config.jobMonitorFullPaths == 1)
            output = job.mpegFile_cut;
         config.gui.jobTab_UpdateJobMonitorRowOutput(job, output);
      }

      Stack<String> command = new Stack<String>();
      command.add(cscript);
      command.add("//nologo");
      command.add(vrdscript);
      command.add(job.vprjFile);
      command.add(job.mpegFile_cut);
      command.add("/l:" + lockFile);
      if (config.VrdAllowMultiple == 1) {
         command.add("/m");
      }
      if (info != null) {
         log.warn("container=" + info.get("container") + ", video=" + info.get("video"));
         command.add("/c:" + info.get("container"));
         command.add("/v:" + info.get("video"));
      }
      process = new backgroundProcess();
      log.print(">> Running adcut on " + job.mpegFile + " ...");
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
            // Update STATUS column
            if ( file.isFile(job.mpegFile_cut) ) {               
               // Update status in job table
               String s = String.format("%.2f MB", (float)file.size(job.mpegFile_cut)/Math.pow(2,20));
               String t = jobMonitor.getElapsedTime(job.time);
               int pct = encodeGetPct();               
               
               if ( jobMonitor.isFirstJobInMonitor(job) ) {
                  // Update STATUS column
                  config.gui.jobTab_UpdateJobMonitorRowStatus(job, t + "---" + s);
                  
                  // Update title & progress bar
                  String title = String.format("adcut: %d%% %s", pct, config.kmttg);
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
            jobMonitor.kill(job); // This called so that family of jobs is killed
         } else {
            log.warn("adcut job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE--- job=" + job.type + " output=" + job.mpegFile_cut);
            // Remove Ad Cut files if option enabled
            if ( config.RemoveComcutFiles == 1 ) {
               String f = string.replaceSuffix(job.mpegFile, ".VPrj");
               if (job.vprjFile != null)
                  f = job.vprjFile;
               if (file.delete(f))
                  log.print("(Deleted vprj file: " + f + ")");
               f = string.replaceSuffix(job.mpegFile, ".edl");
               if (job.edlFile != null)
                  f = job.edlFile;
               if (file.delete(f))
                  log.print("(Deleted edl file: " + f + ")");
               f = job.mpegFile + ".Xcl";
               if (file.isFile(f) && file.delete(f))
                  log.print("(Deleted xcl file: " + f + ")");
               f = string.replaceSuffix(job.mpegFile, ".txt");
               if (file.isFile(f) && file.delete(f))
                  log.print("(Deleted comskip txt file: " + f + ")");
            }
            
            // Remove .TiVo file if option enabled
            if (config.RemoveTivoFile == 1 && file.isFile(job.tivoFile)) {
               if ( file.delete(job.tivoFile) ) {
                  log.print("(Deleted file: " + job.tivoFile + ")");
               } else {
                  log.error("Failed to delete file: "+ job.tivoFile);
               }
            }
            
            // Remove .mpg file if option enabled
            if ( config.RemoveComcutFiles_mpeg == 1 ) {
               if (file.delete(job.mpegFile))
                  log.print("(Deleted mpeg file: " + job.mpegFile + ")");
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
