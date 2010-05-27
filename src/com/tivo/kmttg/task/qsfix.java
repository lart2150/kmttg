package com.tivo.kmttg.task;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
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
      
      if (config.OverwriteFiles == 0 && sourceFile.equals(job.tivoFile) && file.isFile(job.mpegFile)) {
         log.warn("SKIPPING QSFIX, FILE ALREADY EXISTS: " + job.mpegFile);
         schedule = false;
      }
      
      if (schedule) {
         if (config.VrdQsFilter == 1) {
            // Create script with video dimensions filter enabled
            log.warn("VideoRedo video dimensions filter is enabled");
            Hashtable<String,String> dimensions = ffmpeg.getVideoDimensions(sourceFile);
            if (dimensions == null) {
               log.warn("ffmpeg on source file didn't work - trying to get dimensions from 2 sec clip");
               String destFile = file.makeTempFile("mpegFile", ".mpg");
               dimensions = getDimensionsFromShortClip(sourceFile, destFile);
            }
            if (dimensions == null) {
               log.error("VRD QS Filter enabled but unable to determine video dimensions of file: " + sourceFile);
               schedule = false;
            } else {    
               log.warn("VideoRedo video dimensions filter set to: x=" + dimensions.get("x") + ", y=" + dimensions.get("y"));
               vrdscript = createScript(dimensions);
            }
         } else {
            // Create script without video dimensions filter
            vrdscript = createScript(null);
         }
      }
            
      if ( schedule && ! file.isFile(vrdscript) ) {
         log.error("File does not exist: " + vrdscript);
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
         if (vrdscript != null) file.delete(vrdscript);
         if (lockFile != null) file.delete(lockFile);
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
      command.add("/l:" + lockFile);
      process = new backgroundProcess();
      log.print(">> Running qsfix on " + sourceFile + " ...");
      if ( process.run(command) ) {
         log.print(process.toString());
      } else {
         log.error("Failed to start command: " + process.toString());
         process.printStderr();
         process = null;
         jobMonitor.removeFromJobList(job);
         if (vrdscript != null) file.delete(vrdscript);
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
      if (vrdscript != null) file.delete(vrdscript);
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
               // Need to 1st remove mpegFile if it exists
               result = file.delete(job.mpegFile);
               if ( ! result ) {
                  log.error("Failed to delete file in preparation for rename: " + job.mpegFile);
                  if (vrdscript != null) file.delete(vrdscript);
                  if (lockFile != null) file.delete(lockFile);
                  return false;
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
      if (vrdscript != null) file.delete(vrdscript);
      if (lockFile != null) file.delete(lockFile);
      return false;
   }
   
   // Create custom cscript file
   private String createScript(Hashtable<String,String> dimensions) {
      // NOTE: In GUI mode we are able to run concurrent VRD COM jobs
      String script = file.makeTempFile("VRD", ".vbs");
      String eol = "\r";
      try {
         BufferedWriter ofp = new BufferedWriter(new FileWriter(script));
         ofp.write("set Args = wscript.Arguments" + eol);
         ofp.write("if Args.Count < 2 then" + eol);
         ofp.write("   wscript.stderr.writeline( \"? Invalid number of arguments\")" + eol);
         ofp.write("   wscript.quit 1" + eol);
         ofp.write("end if" + eol);
         ofp.write("" + eol);
         ofp.write("' Check for flags." + eol);
         ofp.write("lockFile = \"\"" + eol);
         ofp.write("for i = 1 to args.Count" + eol);
         ofp.write("   p = args(i-1)" + eol);
         ofp.write("   if left(p,3)=\"/l:\" then lockFile = mid(p,4)" + eol);
         ofp.write("next" + eol);
         ofp.write("" + eol);
         ofp.write("' Check that a lock file name was given" + eol);
         ofp.write("if ( lockFile = \"\" ) then" + eol);
         ofp.write("   wscript.stderr.writeline( \"? Lock file (/l:) not given\" )" + eol);
         ofp.write("   wscript.quit 2" + eol);
         ofp.write("end if" + eol);
         ofp.write("" + eol);
         ofp.write("Set fso = CreateObject(\"Scripting.FileSystemObject\")" + eol);
         ofp.write("sourceFile = args(0)" + eol);
         ofp.write("destFile   = args(1)" + eol);
         ofp.write("" + eol);
         ofp.write("'Create VideoReDo object and open the source project / file." + eol);
         if (config.VrdAllowMultiple == 1) {
            ofp.write("Set VideoReDo = wscript.CreateObject( \"VideoReDo.Application\" )" + eol);
            ofp.write("VideoReDo.SetQuietMode(true)" + eol);            
         } else {
            ofp.write("Set VideoReDoSilent = wscript.CreateObject( \"VideoReDo.VideoReDoSilent\" )" + eol);
            ofp.write("set VideoReDo = VideoReDoSilent.VRDInterface" + eol);
         }
         ofp.write("" + eol);
         ofp.write("'Hard code no audio alert" + eol);
         ofp.write("VideoReDo.AudioAlert = false" + eol);
         ofp.write("" + eol);
         ofp.write("' Open source file" + eol);
         ofp.write("openFlag = VideoReDo.FileOpenBatch( sourceFile )" + eol);
         ofp.write("" + eol);
         ofp.write("if openFlag = false then" + eol);
         ofp.write("   wscript.stderr.writeline( \"? Unable to open file/project: \" + sourceFile )" + eol);
         ofp.write("   wscript.quit 3" + eol);
         ofp.write("end if" + eol);
         ofp.write("" + eol);
         if (dimensions != null) {
            ofp.write("VideoReDo.SetFilterDimensions " + dimensions.get("x") + ", " + dimensions.get("y") + eol);
         }
         ofp.write("' Open output file and start processing." + eol);
         ofp.write("outputFlag = VideoReDo.FileSaveAsEx( destFile, 1 )" + eol);
         ofp.write("" + eol);
         ofp.write("if outputFlag = false then" + eol);
         ofp.write("   wscript.stderr.writeline(\"? Problem opening output file: \" + destFile )" + eol);
         ofp.write("   wscript.quit 4" + eol);
         ofp.write("end if" + eol);
         ofp.write("" + eol);
         ofp.write("' Wait until output done and output % complete to stdout" + eol);
         ofp.write("while( VideoRedo.IsOutputInProgress() )" + eol);
         ofp.write("   percent = \"Progress: \" & Int(VideoReDo.OutputPercentComplete) & \"%\"" + eol);
         ofp.write("   wscript.echo(percent)" + eol);
         ofp.write("   if not fso.FileExists(lockFile) then" + eol);
         ofp.write("      VideoReDo.Close()" + eol);
         ofp.write("      wscript.quit 5" + eol);
         ofp.write("   end if" + eol);
         ofp.write("   wscript.sleep 2000" + eol);
         ofp.write("wend" + eol);
         ofp.write("" + eol);
         ofp.write("' Close VRD" + eol);
         ofp.write("VideoReDo.Close()" + eol);
         ofp.write("" + eol);
         ofp.write("' Exit with status 0" + eol);
         ofp.write("wscript.echo( \"   Output complete to: \" + destFile )" + eol);
         ofp.write("wscript.quit 0" + eol);
         ofp.close();
      }
      catch (Exception ex) {
         log.error(ex.toString());
         return null;
      }

      return script;
   }
   
   // This procedure uses VRD to create a short 2 sec mpeg2 clip and then calls
   // ffmpegGetVideoDimensions on that clip to try and obtain video dimensions
   // This is a fallback call in case getting dimensions directly from source file fails.
   private Hashtable<String,String> getDimensionsFromShortClip(String sourceFile, String destFile) { 
      String script = createShortClipScript();
      if (script == null) {
         return null;
      }
      
      // Run VRD with above script      
      Stack<String> command = new Stack<String>();
      command.add(cscript);
      command.add("//nologo");
      command.add(script);
      command.add(sourceFile);
      command.add(destFile);
      backgroundProcess process = new backgroundProcess();
      if ( process.run(command) ) {
         // Wait for command to terminate
         process.Wait();
         if (process.exitStatus() != 0) {
            log.error("VideoRedo run failed");
            log.error(process.getStderr());
            file.delete(script);
            return null;
         }
      } 
      Hashtable<String,String> dimensions = ffmpeg.getVideoDimensions(destFile);
      file.delete(script);
      file.delete(destFile);
      return(dimensions);
   }
   
   // Create custom cscript VRD file to create a 2 sec mpg file
   private String createShortClipScript() {
      // NOTE: In GUI mode we are able to run concurrent VRD COM jobs
      String script = file.makeTempFile("VRD", ".vbs");
      String eol = "\r";
      try {
         BufferedWriter ofp = new BufferedWriter(new FileWriter(script));
         ofp.write("set Args = wscript.Arguments" + eol);
         ofp.write("if Args.Count < 2 then" + eol);
         ofp.write("   wscript.stderr.writeline( \"? Invalid number of arguments\")" + eol);
         ofp.write("   wscript.quit 1" + eol);
         ofp.write("end if" + eol);
         ofp.write("" + eol);
         ofp.write("sourceFile = args(0)" + eol);
         ofp.write("destFile   = args(1)" + eol);
         ofp.write("" + eol);
         ofp.write("'Create VideoReDo object and open the source project / file." + eol);
         if (config.VrdAllowMultiple == 1) {
            ofp.write("Set VideoReDo = wscript.CreateObject( \"VideoReDo.Application\" )" + eol);
            ofp.write("VideoReDo.SetQuietMode(true)" + eol);            
         } else {
            ofp.write("Set VideoReDoSilent = wscript.CreateObject( \"VideoReDo.VideoReDoSilent\" )" + eol);
            ofp.write("set VideoReDo = VideoReDoSilent.VRDInterface" + eol);
         }
         ofp.write("" + eol);
         ofp.write("'Hard code no audio alert" + eol);
         ofp.write("VideoReDo.AudioAlert = false" + eol);
         ofp.write("" + eol);
         ofp.write("' Open source file" + eol);
         ofp.write("openFlag = VideoReDo.FileOpen( sourceFile )" + eol);
         ofp.write("" + eol);
         ofp.write("if openFlag = false then" + eol);
         ofp.write("   wscript.stderr.writeline( \"? Unable to open file: \" + sourceFile )" + eol);
         ofp.write("   wscript.quit 2" + eol);
         ofp.write("end if" + eol);
         ofp.write("" + eol);
         ofp.write("' Open output file and start processing." + eol);
         ofp.write("VideoReDo.SetCutMode(false)" + eol);
         ofp.write("if NOT VideoReDo.SelectScene(0, 2000) then" + eol);
         ofp.write("   wscript.stderr.writeline(\"Failed to select scene\")" + eol);
         ofp.write("   wscript.quit 3" + eol);
         ofp.write("end if" + eol);
         ofp.write("VideoReDo.AddToJoiner()" + eol);
         ofp.write("' Save selection to mpeg2 program stream." + eol);
         ofp.write("outputFlag = VideoReDo.SaveJoinerAsEx( destFile, 1 )" + eol);
         ofp.write("" + eol);
         ofp.write("if outputFlag = false then" + eol);
         ofp.write("   wscript.stderr.writeline(\"? Problem opening output file: \" + destFile )" + eol);
         ofp.write("   wscript.quit 4" + eol);
         ofp.write("end if" + eol);
         ofp.write("" + eol);
         ofp.write("' Wait until output done" + eol);
         ofp.write("while( VideoRedo.IsOutputInProgress() )" + eol);
         ofp.write("   wscript.sleep 1000" + eol);
         ofp.write("wend" + eol);
         ofp.write("" + eol);
         ofp.write("' Close VRD" + eol);
         ofp.write("VideoReDo.Close()" + eol);
         ofp.write("wscript.quit 0" + eol);
         ofp.close();
      }
      catch (Exception ex) {
         log.error(ex.toString());
         return null;
      }

      return script;
   }

}
