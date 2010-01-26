package com.tivo.kmttg.task;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Date;
import java.util.Stack;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class vrdencode {
   String  vrdscript = null;
   String  cscript = null;
   String  lockFile = null;
   private backgroundProcess process;
   private jobData job;

   // constructor
   public vrdencode(jobData job) {
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
            
      vrdscript = createScript();
      if ( vrdscript == null || ! file.isFile(vrdscript) ) {
         log.error("Failed to created script: " + vrdscript);
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
         if (config.VrdReview_noCuts == 1) {
            // Look for VRD default edit file output
            String tryit = string.replaceSuffix(mpeg, " (02).mpg");
            if (file.isFile(tryit))
               mpeg = tryit;
         }
      }
      if ( ! file.isFile(mpeg)) {
         mpeg = job.tivoFile;
      }
      
      if ( ! file.isFile(mpeg) ) {
         log.error("mpeg file not given or doesn't exist: " + mpeg);
         schedule = false;
      }
      job.inputFile = mpeg;
                  
      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.encodeFile, job) ) {
            schedule = false;
         }
      }
      
      if (schedule) {
         if ( start() ) {
            job.process_vrdencode = this;
            jobMonitor.updateJobStatus(job, "running");
            job.time              = new Date().getTime();
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
      command.add(job.inputFile);
      command.add(job.encodeFile);
      command.add("/l:" + lockFile);
      command.add("/p:" + job.encodeName);
      process = new backgroundProcess();
      log.print(">> ENCODING WITH PROFILE '" + job.encodeName + "' TO FILE " + job.encodeFile + " ...");
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
            if ( file.isFile(job.encodeFile) ) {               
               // Update status in job table
               String s = String.format("%.2f MB", (float)file.size(job.encodeFile)/Math.pow(2,20));
               String t = jobMonitor.getElapsedTime(job.time);
               int pct = encodeGetPct();
                              
               if ( jobMonitor.isFirstJobInMonitor(job) ) {
                  // Update STATUS column, title & progress bar
                  config.gui.jobTab_UpdateJobMonitorRowStatus(job, t + "---" + s);                  
                  String title = String.format("vrdencode: %d%% %s", pct, config.kmttg);
                  config.gui.setTitle(title);
                  config.gui.progressBar_setValue(pct);
               } else {
                  // Update STATUS column only
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
         // No or empty encodeFile means problems
         if ( ! file.isFile(job.encodeFile) || file.isEmpty(job.encodeFile) ) {
            failed = 1;
         }
         
         // exit status != 0 => problem
         if (exit_code != 0) {
            failed = 1;
         }
         
         if (failed == 1) {
            log.error("vrdencode failed (exit code: " + exit_code + " ) - check command: " + process.toString());
            process.printStderr();
         } else {
            log.warn("vrdencode job completed: " + jobMonitor.getElapsedTime(job.time));
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
            
            // Remove .mpg file if option enabled
            if (config.RemoveMpegFile == 1) {
               if ( file.delete(job.inputFile) ) {
                  log.print("(Deleted file: " + job.inputFile + ")");
               } else {
                  log.error("Failed to delete file: "+ job.inputFile);
               }
               
               if ( file.delete(job.mpegFile)) {
                  log.print("(Deleted file: " + job.mpegFile + ")");
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
      if (vrdscript != null) file.delete(vrdscript);
      if (lockFile != null) file.delete(lockFile);
      return false;
   }
   
   // Create custom cscript file
   private String createScript() {
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
         ofp.write("profileName  = \"\"" + eol);
         ofp.write("for i = 1 to args.Count" + eol);
         ofp.write("   p = args(i-1)" + eol);
         ofp.write("   if left(p,3)=\"/l:\" then lockFile = mid(p,4)" + eol);
         ofp.write("   if left(p,3)=\"/p:\" then profileName = mid(p,4)" + eol);
         ofp.write("next" + eol);
         ofp.write("" + eol);
         ofp.write("' Check that a lock file name was given" + eol);
         ofp.write("if ( lockFile = \"\" ) then" + eol);
         ofp.write("   wscript.stderr.writeline( \"? Lock file (/l:) not given\" )" + eol);
         ofp.write("   wscript.quit 2" + eol);
         ofp.write("end if" + eol);
         ofp.write("" + eol);
         ofp.write("' Check that a profile name was given" + eol);
         ofp.write("if ( profileName = \"\" ) then" + eol);
         ofp.write("   wscript.stderr.writeline( \"? Profile name not given\" )" + eol);
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
         ofp.write("openFlag = VideoReDo.FileOpen( sourceFile )" + eol);
         ofp.write("" + eol);
         ofp.write("if openFlag = false then" + eol);
         ofp.write("   wscript.stderr.writeline( \"? Unable to open file/project: \" + sourceFile )" + eol);
         ofp.write("   wscript.quit 3" + eol);
         ofp.write("end if" + eol);
         ofp.write("" + eol);
         ofp.write("' Open output file and start processing." + eol);
         ofp.write("outputFlag = false" + eol);
         ofp.write("outputXML = VideoReDo.FileSaveProfile( destFile, profileName )" + eol);
         ofp.write("if ( left(outputXML,1) = \"*\" ) then" + eol);
         ofp.write("   wscript.stderr.writeline(\"? Problem opening output file: \" + outputXML )" + eol);
         ofp.write("   wscript.quit 4" + eol);
         ofp.write("else" + eol);
         ofp.write("   outputFlag = true" + eol);
         ofp.write("end if" + eol);
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

