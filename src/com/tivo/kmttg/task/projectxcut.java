package com.tivo.kmttg.task;

import java.io.Serializable;
import java.util.Date;
import java.util.Stack;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.ProjectX;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class projectxcut implements Serializable {
   private static final long serialVersionUID = 1L;
   private backgroundProcess process;
   private jobData job;
   private Boolean remux = false;
   private String mpegFile;
   private long totalSize = 0L;

   // constructor
   public projectxcut(jobData job) {
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
            log.warn("SKIPPING PROJECTX CUT, FILE ALREADY EXISTS: " + job.mpegFile_cut);
            schedule = false;
         } else {
            log.warn("OVERWRITING EXISTING FILE: " + job.mpegFile_cut);
         }
      }
            
      if ( ! file.isFile(config.projectx) ) {
         log.error("ProjectX not found: " + config.projectx);
         schedule = false;
      }
      
      if ( ! file.isFile(job.xclFile) ) {
         log.error("xcl file not found: " + job.xclFile);
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
            job.process_projectxcut   = this;
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
      command.add("java");
      command.add("-jar");
      command.add(config.projectx);
      command.add(job.mpegFile);
      command.add("-demux");
      command.add("-cut");
      command.add(job.xclFile);
      command.add("-out");
      command.add(string.dirname(job.mpegFile));
      process = new backgroundProcess();
      log.print(">> Running ProjectX cut on " + job.mpegFile + " ...");
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
      if (! remux) {
         // THIS IS DEMUX JOB
         //debug.print("");
         int exit_code = process.exitStatus();
         if (exit_code == -1) {
            // Still running
            if (config.GUIMODE) {
               int pct = ProjectX.projectxGetPct(process);
               if ( pct > -1 ) {               
                  // Update status in job table
                  String status = String.format("%d%%",pct);
                  config.gui.jobTab_UpdateJobMonitorRowStatus(job, status);
                  
                  if ( jobMonitor.isFirstJobInMonitor(job) ) {
                     // If 1st job then update title & progress bar
                     String title = String.format("projectxcut: %s %s", status, config.kmttg);
                     config.gui.setTitle(title);
                     config.gui.progressBar_setValue(pct);
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
            
            // Check for problems
            int failed = 0;
            
            // No or empty logFile means problems
            String logFile = string.replaceSuffix(job.mpegFile, "_log.txt");
            if ( ! file.isFile(logFile) || file.isEmpty(logFile) ) {
               log.error("Unable to find projectxcut output log file: " + logFile);
               failed = 1;
            }
            
            Stack<String> outputFiles = ProjectX.getOutputFiles(logFile);
            if (outputFiles == null) {
               log.error("No projectxcut output files found");
               failed = 1;
            }
            job.demuxFiles = outputFiles;
            
            // exit status != 0 => problem
            if (exit_code != 0) {
               failed = 1;
            }
            
            if (failed == 1) {
               log.error("projectxcut failed (exit code: " + exit_code + " ) - check command: " + process.toString());
               process.printStderr();
               jobMonitor.removeFromJobList(job);
               return false;
            } else {
               log.warn("projectx cut demux completed: " + jobMonitor.getElapsedTime(job.time));
               log.print("---DONE--- job=" + job.type + "\ndemux output files:");
               for (int i=0; i<outputFiles.size(); ++i)
                  log.print(outputFiles.get(i));
               file.delete(logFile);
               
               // Remove Ad Cut files if option enabled
               if ( config.RemoveComcutFiles == 1 ) {
                  if (file.delete(job.vprjFile))
                     log.print("(Deleted vprj file: " + job.vprjFile + ")");
                  if (file.delete(job.edlFile))
                     log.print("(Deleted edl file: " + job.edlFile + ")");
                  if (file.delete(job.xclFile))
                     log.print("(Deleted xcl file: " + job.xclFile + ")");
                  String txtFile = string.replaceSuffix(job.mpegFile, ".txt");
                  if (file.isFile(txtFile) && file.delete(txtFile))
                     log.print("(Deleted comskip txt file: " + txtFile + ")");
               }
               
               // Start remux background job               
               mpegFile = job.mpegFile_cut;
               job.mpegFile_fix = job.mpegFile_cut + ".qsfix";               
               totalSize = ProjectX.getDemuxFilesSize(job.demuxFiles);

               Stack<String> command = new Stack<String>();
               command.add(config.ffmpeg);
               command.add("-y");
               command.add("-fflags");
               command.add("genpts");
               for (int i=0; i<job.demuxFiles.size(); ++i) {
                  command.add("-i");
                  command.add(job.demuxFiles.get(i));
               }
               command.add("-acodec");
               command.add("copy");
               command.add("-vcodec");
               command.add("copy");
               command.add("-f");
               command.add("dvd");
               command.add(job.mpegFile_fix);
               process = new backgroundProcess();
               log.print(">> Running ffmpeg remux to generate " + job.mpegFile_fix + " ...");
               if ( process.run(command) ) {
                  log.print(process.toString());
               } else {
                  log.error("Failed to start command: " + process.toString());
                  process.printStderr();
                  process = null;
                  jobMonitor.removeFromJobList(job);
                  return false;
               }
               remux = true;
               return true;
            }
         }
      } else {
         // THIS IS REMUX JOB
         int exit_code = process.exitStatus();
         if (exit_code == -1) {
            // Still running
            if (config.GUIMODE && totalSize > 0) {
               // Update STATUS column
               int pct = Integer.parseInt(String.format("%d", file.size(job.mpegFile_fix)*100/totalSize));
               // Update status in job table
               String status = String.format("%d%%",pct);
               config.gui.jobTab_UpdateJobMonitorRowStatus(job, status);
               
               if ( jobMonitor.isFirstJobInMonitor(job) ) {
                  // If 1st job then update title & progress bar
                  String title = String.format("remux: %s %s", status, config.kmttg);
                  config.gui.setTitle(title);
                  config.gui.progressBar_setValue(pct);
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
               log.error("Unable to find remux output file: " + job.mpegFile_fix);
               failed = 1;
            }
            
            // exit status != 0 => problem
            if (exit_code != 0) {
               failed = 1;
            }
            
            if (failed == 1) {
               log.error("remux failed (exit code: " + exit_code + " ) - check command: " + process.toString());
               process.printStderr();
            } else {
               log.warn("remux job completed: " + jobMonitor.getElapsedTime(job.time));
               log.print("---DONE--- job=" + job.type);
               
               // Remove the demuxed files
               ProjectX.removeDemuxFiles(job.demuxFiles);
               // Remove original .mpg file if this is remux for projectxcut and remove option enabled
               if ( job.mpegFile_cut != null && config.RemoveComcutFiles_mpeg == 1 ) {
                  if (file.delete(job.mpegFile))
                     log.print("(Deleted mpeg file: " + job.mpegFile + ")");
               }
               
               // Rename job.mpegFile_fix to mpegFile
               Boolean result;
               if (file.isFile(mpegFile)) {
                  if (config.QSFixBackupMpegFile == 1) {
                     // Rename mpegFile to backupFile if it exists
                     String backupFile = mpegFile + ".bak";
                     int count = 1;
                     while (file.isFile(backupFile)) {
                        backupFile = mpegFile + ".bak" + count++;
                     }
                     result = file.rename(mpegFile, backupFile);
                     if ( result ) {
                        log.print("(Renamed " + mpegFile + " to " + backupFile + ")");
                     } else {
                        log.error("Failed to rename " + mpegFile + " to " + backupFile);
                        return false;                     
                     }
                  } else {
                     // Remove mpegFile if it exists
                     result = file.delete(mpegFile);
                     if ( ! result ) {
                        log.error("Failed to delete file in preparation for rename: " + mpegFile);
                        return false;
                     }
                  }
               }
               // Now do the file rename
               result = file.rename(job.mpegFile_fix, mpegFile);
               if (result)
                  log.print("(Renamed " + job.mpegFile_fix + " to " + mpegFile + ")");
               else
                  log.error("Failed to rename " + job.mpegFile_fix + " to " + mpegFile);
            }
         }
         return false;
      }
   }
}
