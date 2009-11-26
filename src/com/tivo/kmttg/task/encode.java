package com.tivo.kmttg.task;

import java.util.Date;
import java.util.Stack;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.encodeConfig;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.*;

// Encoding class for running background ffmpeg, handbrake, etc. encoding jobs
public class encode {
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
         if ( ! jobMonitor.createSubFolders(job.encodeFile, job) ) schedule = false;
      }
      
      if (schedule) {
         if ( start() ) {
            job.process_encode = this;
            jobMonitor.updateJobStatus(job, "running");
            job.time           = new Date().getTime();
         }
         return true;
      } else {
         return false;
      }      
   }

   // Return false if starting command fails, true otherwise
   private Boolean start() {
      debug.print("");
      if ( ! encodeConfig.isValidEncodeName(job.encodeName) ) {
         jobMonitor.removeFromJobList(job);
         return false;
      }
      Stack<String> command = encodeConfig.getFullCommand(
         job.encodeName, job.inputFile, job.encodeFile, job.srtFile
      );
         
      process = new backgroundProcess();
      log.print(">> ENCODING WITH PROFILE '" + job.encodeName + "' TO FILE " + job.encodeFile + " ...");
      if ( process.run(command) ) {
         log.print(process.toString());
      } else {
         log.error("Failed to start command: " + process.toString());
         process.printStderr();
         process = null;
         jobMonitor.removeFromJobList(job);
         jobMonitor.removeFamilyJobs(job);
         return false;
      }
      return true;
   }
   
   public void kill() {
      debug.print("");
      process.kill();
      log.warn("Killing '" + job.type + "' job: " + process.toString());
      jobMonitor.removeFamilyJobs(job);
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
            if ( file.isFile(job.encodeFile) ) {               
               String s = String.format("%.2f MB", (float)file.size(job.encodeFile)/Math.pow(2,20));
               String t = jobMonitor.getElapsedTime(job.time);
               
               // Try and determine pct complete
               int pct = -1;
               // For ffmpeg job get show duration for computing pct complete
               if (process.toString().contains(config.ffmpeg)) {
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
               
               else if (process.toString().contains(config.handbrake)) {
                  // Get pct complete from handbrake stdout
                  pct = handbrakeGetPct();
               }
               
               else {
                  // Some other encoder
                  pct = -2;
               }
                              
               if ( jobMonitor.isFirstJobInMonitor(job) ) {
                  // Update STATUS column
                  config.gui.jobTab_UpdateJobMonitorRowStatus(job, t + "---" + s);
                  
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
                  // Update STATUS column
                  if (pct > -1)
                     config.gui.jobTab_UpdateJobMonitorRowStatus(job, String.format("%d%%",pct) + "---" + s);
                  else
                     config.gui.jobTab_UpdateJobMonitorRowStatus(job, t + "---" + s);                     
               }
            } else {
               // File not yet available so simply update time
               String t = jobMonitor.getElapsedTime(job.time);
               config.gui.jobTab_UpdateJobMonitorRowStatus(job, t);
               if ( jobMonitor.isFirstJobInMonitor(job) ) {
                  String title = String.format("encode: %s %s", t, config.kmttg);
                  config.gui.setTitle(title);
               }
            }
         }
        return true;
      } else {
         // Job finished
         if (config.GUI) {
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
            jobMonitor.removeFamilyJobs(job);
         } else {
            log.warn("encoding job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE---");
            
            // Remove mpegFile.qsfix file if present
            String fix;            
            if (job.mpegFile.matches("^.+\\.qsfix$"))
               fix = job.mpegFile;
            else
               fix = job.mpegFile + ".qsfix";
            if (file.isFile(fix)) {
               if (file.delete(fix)) log.print("(Deleted file: " + fix + ")");
            }
            
            // Remove .mpg file if option enabled
            if (config.RemoveMpegFile == 1) {
               if ( file.delete(job.inputFile) ) {
                  log.print("(Deleted file: " + job.inputFile + ")");
               } else {
                  log.error("Failed to delete file: "+ job.inputFile);
               }
            }
            
            // Schedule an AtomicParsley job if relevant
            if (file.isFile(config.AtomicParsley)) {
               job.metaFile = job.encodeFile + ".txt";
               if ( ! file.isFile(job.metaFile) ) {
                  job.metaFile = job.mpegFile_cut + ".txt";
               }
               if ( ! file.isFile(job.metaFile) ) {
                  job.metaFile = job.mpegFile + ".txt";
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
      }
      return false;
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
            float hour = Float.parseFloat(ll[0]);
            float min  = Float.parseFloat(ll[1]);
            float sec  = Float.parseFloat(ll[2]);
            long ms = (long)(3600*hour + 60*min + sec)*1000;
            return ms;
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
         float sec  = Float.parseFloat(ll[0]);
         long ms = (long)sec*1000;
         return ms;
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
      
}

