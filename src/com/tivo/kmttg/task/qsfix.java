package com.tivo.kmttg.task;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Hashtable;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class qsfix {
   String  vrdscript = null;
   String  vrdscript_temp = null;
   String  cscript = null;
   String  sourceFile = null;
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
      vrdscript = config.VRD + s + "vp.vbs";
      cscript = System.getenv("SystemRoot") + s + "system32" + s + "cscript.exe";
      
      if ( ! file.isFile(vrdscript) ) {
         log.error("File does not exist: " + vrdscript);
         schedule = false;
      }
      
      if ( ! file.isFile(cscript) ) {
         log.error("File does not exist: " + cscript);
         schedule = false;
      }
      
      if (config.VrdDecrypt == 0) {
         sourceFile = job.mpegFile;
      } else {
         sourceFile = job.tivoFile;
      }
                  
      if ( ! file.isFile(sourceFile) ) {
         log.error("source file not found: " + sourceFile);
         schedule = false;
      }
            
      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.mpegFile_fix, job) ) schedule = false;
      }
      
      if (config.VrdQsFilter == 1 && schedule) {
         log.warn("VideoRedo video dimensions filter is enabled - making custom VRD script");
         // Want QSFix run with video dimension filter enabled
         Hashtable<String,String> dimensions = ffmpegGetVideoDimensions(sourceFile);
         if (dimensions == null) {
            log.error("VRD QS Filter enabled but unable to determine video dimensions of file: " + sourceFile);
            schedule = false;
         } else {    
            log.warn("VideoRedo video dimensions filter set to: x=" + dimensions.get("x") + ", y=" + dimensions.get("y"));
            // Build a custom vrdscript with video filtering enabled
            vrdscript_temp = makeTempVrdFilterScript(vrdscript, dimensions);
            if (vrdscript_temp == null) {
               schedule = false;
            } else {
               vrdscript = vrdscript_temp;
            }
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
         if (vrdscript_temp != null) file.delete(vrdscript_temp);
         return false;
      }     
   }

   // Return false if starting command fails, true otherwise
   private Boolean start() {
      debug.print("");
      Stack<String> command = new Stack<String>();
      command.add(cscript);
      command.add("//nologo");
      command.add(vrdscript);
      command.add(sourceFile);
      command.add(job.mpegFile_fix);
      command.add("/t1");
      command.add("/d");
      command.add("/q");
      command.add("/na");
      process = new backgroundProcess();
      log.print(">> Running qsfix on " + sourceFile + " ...");
      if ( process.run(command) ) {
         log.print(process.toString());
      } else {
         log.error("Failed to start command: " + process.toString());
         process.printStderr();
         process = null;
         jobMonitor.removeFromJobList(job);
         if (vrdscript_temp != null) file.delete(vrdscript_temp);
         return false;
      }
      return true;
   }
   
   public void kill() {
      debug.print("");
      process.kill();
      log.warn("Killing '" + job.type + "' job: " + process.toString());
      if (vrdscript_temp != null) file.delete(vrdscript_temp);
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
            log.print("---DONE---");
            // Rename mpegFile_fix to mpegFile
            Boolean result;
            if (config.VrdDecrypt == 0) {
               result = file.delete(job.mpegFile);
               if ( ! result ) {
               	log.error("Failed to delete file in preparation for rename: " + job.mpegFile);
               	if (vrdscript_temp != null) file.delete(vrdscript_temp);
               	return false;
               }
            }
            result = file.rename(job.mpegFile_fix, job.mpegFile);
            if (result)
            	log.print("(Renamed " + job.mpegFile_fix + " to " + job.mpegFile + ")");
            else
            	log.error("Failed to rename " + job.mpegFile_fix + " to " + job.mpegFile);
         }
      }
      if (vrdscript_temp != null) file.delete(vrdscript_temp);
      return false;
   }
   
   // Use ffmpeg to get video dimensions from given mpeg video file
   // Returns null if undetermined, a hash with x, y members otherwise
   private Hashtable<String,String> ffmpegGetVideoDimensions(String videoFile) {      
      // Use ffmpeg command to get video information      
      Stack<String> command = new Stack<String>();
      command.add(config.ffmpeg);
      command.add("-i");
      command.add(videoFile);
      backgroundProcess process = new backgroundProcess();
      if ( process.run(command) ) {
         // Wait for command to terminate
         process.Wait();
         
         // Parse stderr
         Stack<String> l = process.getStderr();
         if (l.size() > 0) {
            for (int i=0; i<l.size(); ++i) {
               if (l.get(i).matches("^.+\\s+Video:\\s+.+$")) {
                  Pattern p = Pattern.compile(".*Video: .+, (\\d+)x(\\d+)[, ].*");
                  Matcher m = p.matcher(l.get(i));
                  if (m.matches()) {
                     Hashtable<String,String> dimensions = new Hashtable<String,String>();
                     dimensions.put("x", m.group(1));
                     dimensions.put("y", m.group(2));
                     return dimensions;
                  }
               }
            }
         }
      }
      return null;
   }
   
   // Create a custom VRD script file based on given vrdscript file by adding a video dimension filter
   private String makeTempVrdFilterScript(String inputFile, Hashtable<String,String> dimensions) {
      String script = file.makeTempFile("VRD", ".vbs");
      try {
         BufferedReader ifp = new BufferedReader(new FileReader(inputFile));
         BufferedWriter ofp = new BufferedWriter(new FileWriter(script));
         String line;
         while ( (line = ifp.readLine()) != null ) {
            if (line.matches("^.+FileSaveAs.+$")) {
               // Add filter line right before the FileSaveAs statement which does most of the work
               ofp.write("VideoReDo.SetFilterDimensions " + dimensions.get("x") + ", " + dimensions.get("y") + "\n\r");
            }
            ofp.write(line + "\n\r");
         }
         ifp.close();
         ofp.close();
      }
      catch (IOException ex) {
         log.error(ex.toString());
         return null;
      }

      return script;
   }

}
