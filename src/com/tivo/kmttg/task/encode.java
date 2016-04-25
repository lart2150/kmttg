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
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.encodeConfig;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.*;

// Encoding class for running background ffmpeg, handbrake, etc. encoding jobs
public class encode extends baseTask implements Serializable {
   private static final long serialVersionUID = 1L;
   private backgroundProcess process;
   private jobData job;

   // constructor
   public encode(jobData job) {
      debug.print("job=" + job);      
      this.job = job;
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;
      // Don't encode if encodeFile already exists
      if ( file.isFile(job.encodeFile) ) {
         if (config.OverwriteFiles == 0) {
            log.warn("SKIPPING ENCODE, FILE ALREADY EXISTS: " + job.encodeFile);            
            // Schedule an AtomicParsley job if relevant
            scheduleAtomicParsley();
            schedule = false;
         } else {
            log.warn("OVERWRITING EXISTING FILE: " + job.encodeFile);
         }
      }
      
      // Decide which file needs to be encoded and update args accordingly
      String mpeg;
      if ( file.isFile(job.mpegFile_cut) ) {
         mpeg = job.mpegFile_cut;
      } else {
         mpeg = job.mpegFile;
      }
      
      if (! file.isFile(job.mpegFile_cut) && config.VrdReview_noCuts == 1) {
         String tryit = file.vrdreviewFileSearch(job.startFile);
         if (tryit != null)
            mpeg = tryit;
      }

      if ( ! file.isFile(mpeg) ) {
         log.error("mpeg file not given or doesn't exist: " + mpeg);
         schedule = false;
      }
      job.inputFile = mpeg;
      
      if (schedule) {
         if ( ! encodeConfig.isValidEncodeName(job.encodeName) ) {
            log.error("invalid encoding profile for this job: " + job.encodeName);
            schedule = false;
         }
      }
      
      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.encodeFile, job) ) {
            schedule = false;
         }
      }
      
      if (schedule) {
         if ( start() ) {
            job.process = this;
            jobMonitor.updateJobStatus(job, "running");
            job.time           = new Date().getTime();
         }
         return true;
      } else {
         return false;
      }      
   }

   // Return false if starting command fails, true otherwise
   public Boolean start() {
      debug.print("");
      if ( ! encodeConfig.isValidEncodeName(job.encodeName) ) {
         jobMonitor.removeFromJobList(job);
         return false;
      }
      Stack<String> command = encodeConfig.getFullCommand(
         job.encodeName, job.inputFile, job.encodeFile, job.srtFile
      );
      
      // This deals with potential ###xHEIGHT or WIDTHx### keywords in encoding profile
      command = ffmpeg.getOutputDimensions(job.inputFile, command);
         
      process = new backgroundProcess();
      log.print(">> ENCODING WITH PROFILE '" + job.encodeName + "' TO FILE " + job.encodeFile + " ...");
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
            String t = jobMonitor.getElapsedTime(job.time);
            String size = null;
            if ( file.isFile(job.encodeFile) ) {               
               size = String.format("%.2f MB", (float)file.size(job.encodeFile)/Math.pow(2,20));
               if (size.equals("0.00 MB")) size = null;
            }
               
            // Try and determine pct complete
            int pct = -1;
            
            if (config.handbrake.length() > 0 && process.toString().contains(config.handbrake)) {
               // Get pct complete from handbrake stdout
               pct = handbrakeGetPct();
            }
            
            else if (config.mencoder.length() > 0 && process.toString().contains(config.mencoder)) {
               // Get pct complete from mencoder stdout
               pct = mencoderGetPct();
            }
            
            else if (process.toString().contains("ffmpeg")) {
               // Get duration from ffmpeg stderr if not yet available
               if ( job.duration == null ) {
                  long duration = ffmpegGetDuration();
                  if ( duration > 0 ) {
                     job.duration = duration;
                  }
               }
               // Get time from ffmpeg stderr
               long time = ffmpegGetTime();
               if (job.duration != null && time > 0) {
                  Long duration = (Long)job.duration;
                  pct = Integer.parseInt(String.format("%d", time*100/duration));
               }
            }
            
            else {
               // Some other encoder
               pct = -2;
            }

            String status = t;
            if ( jobMonitor.isFirstJobInMonitor(job) ) {
               // If 1st job then update title with pct complete
               if (pct > -1) {
                  String title = String.format("encode: %d%% %s", pct, config.kmttg);
                  config.gui.setTitle(title);
                  config.gui.progressBar_setValue(pct);                  
               }
               if (pct == -2) {
                  String title = String.format("encode: %s %s", t, config.kmttg);
                  config.gui.setTitle(title);                     
               }
            } else {
               if (pct > -1)
                  status = String.format("%d%%",pct);
            }            
            // Update STATUS column
            if (size != null) status += "---" + size;               
            config.gui.jobTab_UpdateJobMonitorRowStatus(job, status);
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
         // No or empty output file means problems
         if ( ! file.isFile(job.encodeFile) || file.isEmpty(job.encodeFile) ) {
            failed = 1;
         }
         
         if (failed == 1) {
            log.error("encoding failed (exit code: " + exit_code + " ) - check command: " + process.toString());
            process.printStderr();
            jobMonitor.kill(job); // This called so that family of jobs is killed
         } else {
            log.warn("encoding job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE--- job=" + job.type + " output=" + job.encodeFile);
            
            // Remove mpegFile.qsfix file if present
            String fix;            
            if (job.mpegFile.matches("^.+\\.qsfix$"))
               fix = job.mpegFile;
            else
               fix = job.mpegFile + ".qsfix";
            if (file.isFile(fix)) {
               if (file.delete(fix)) log.print("(Deleted file: " + fix + ")");
            }
            
            // If metadata file exists for input file but not output file, then copy it
            String input_meta = job.inputFile + ".txt";
            String output_meta = job.encodeFile + ".txt";
            if ( ! file.isFile(output_meta) ) {
               if (file.isFile(input_meta)) {
                  if (file.copy(input_meta, output_meta))
                     log.warn("Copied metadata file " + input_meta + " to " + output_meta);
                  else
                     log.error("Failed to copy metadata file: " + input_meta);
               }
            }
            
            // Remove .mpg file if option enabled
            // If there is a second encode job working off the same source file,
            // then the source should not be removed.
            if (!job.hasMoreEncodingJobs && config.RemoveMpegFile == 1) {
               if ( file.delete(job.inputFile) ) {
                  log.print("(Deleted file: " + job.inputFile + ")");
               } else {
                  log.error("Failed to delete file: "+ job.inputFile);
               }
               if ( file.delete(input_meta) ) {
                  log.print("(Deleted file: " + input_meta + ")");
               }
               if ( file.delete(job.mpegFile) ) {
                  log.print("(Deleted file: " + job.mpegFile + ")");
               }
            }
            
            // Schedule an AtomicParsley job if relevant
            scheduleAtomicParsley();
         }
      }
      return false;
   }
   
   private void scheduleAtomicParsley() {      
      // Schedule an AtomicParsley job if relevant
      if (file.isFile(config.AtomicParsley)) {
         job.metaFile = job.encodeFile + ".txt";
         if ( ! file.isFile(job.metaFile) ) {
            job.metaFile = job.mpegFile_cut + ".txt";
         }
         if ( ! file.isFile(job.metaFile) ) {
            job.metaFile = job.mpegFile + ".txt";
         }
         if ( ! file.isFile(job.metaFile) ) {
            job.metaFile = string.replaceSuffix(job.encodeFile, ".txt.TiVo");
         }
         if ( file.isFile(job.metaFile) &&
              (job.encodeFile.toLowerCase().endsWith(".mp4") ||
               job.encodeFile.toLowerCase().endsWith(".m4v")) ) {
            jobData new_job = new jobData();
            new_job.source       = job.source;
            new_job.tivoName     = job.tivoName;
            new_job.type         = "atomic";
            new_job.name         = config.AtomicParsley;
            new_job.encodeFile   = job.encodeFile;
            new_job.metaFile     = job.metaFile;
            jobMonitor.submitNewJob(new_job);
         }
      }
   }
 
   // Obtain total duration time from ffmpeg stderr
   private long ffmpegGetDuration() {
      Stack<String> stderr = process.getStderr();
      String line;
      for (int i=0; i<stderr.size(); ++i) {
         line = stderr.get(i);
         if (line.contains("Duration:")) {
            String[] l = line.split("\\s+");
            String d = l[2].replaceFirst(",", "");
            String[] ll = d.split(":");
            try {
               float hour = Float.parseFloat(ll[0]);
               float min  = Float.parseFloat(ll[1]);
               float sec  = Float.parseFloat(ll[2]);
               long ms = (long)(3600*hour + 60*min + sec)*1000;
               return ms;
            }
            catch (NumberFormatException n) {
            }
         }
      }
      return 0;
   }
 
   // Obtain current length in ms of encoding file from ffmpeg stderr
   private long ffmpegGetTime() {
      String last = process.getStderrLast();
      if (last.matches("")) return 0;
      if (last.contains("time=")) {
         String[] l = last.split("time=");
         String[] ll = l[l.length-1].split("\\s+");
         float sec = (float)0;
         try {
            if (ll[0].contains(":")) {
               // "HH:MM:SS.MS" format
               Pattern p = Pattern.compile("(\\d+):(\\d+):(\\d+).(\\d+)");
               Matcher m = p.matcher(ll[0]);
               if (m.matches()) {
                  long HH = Long.parseLong(m.group(1));
                  long MM = Long.parseLong(m.group(2));
                  long SS = Long.parseLong(m.group(3));
                  long MS = Long.parseLong(m.group(4));
                  long ms = MS + 1000*(SS+60*MM+60*60*HH);
                  return ms;
               }

            } else {
               sec  = Float.parseFloat(ll[0]);
            }
         }
         catch (NumberFormatException n) {
         }
         return (long)sec*1000;
      }
      return 0;
   }

   // Obtain pct complete from handbrake stdout
   private int handbrakeGetPct() {
      String last = process.getStdoutLast();
      if (last.matches("")) return 0;
      if (last.contains("Encoding")) {
         String[] all = last.split("Encoding");
         String pct_str = all[all.length-1].replaceFirst("^.+,\\s+(.+)\\s+%.*$", "$1");
         try {
            return (int)Float.parseFloat(pct_str);
         }
         catch (NumberFormatException n) {
            return 0;
         }
      }
      return 0;
   }

   // Obtain pct complete from handbrake stdout
   private int mencoderGetPct() {
      String last = process.getStdoutLast();
      if (last.matches("")) return 0;
      Pattern pat = Pattern.compile("(\\d+)%");
      Matcher match = pat.matcher(last);
      if (match.find()) {
         String pct_str = match.group(1);
         try {
            return (int)Float.parseFloat(pct_str);
         }
         catch (NumberFormatException n) {
            return 0;
         }
      }
      return 0;
   }
      
}

