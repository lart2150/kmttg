package com.tivo.kmttg.task;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.Serializable;
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

public class adcut implements Serializable {
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
      
      if ( ! file.isFile(job.vprjFile) ) {
         log.error("vprj file not found: " + job.vprjFile);
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
            job.process_adcut    = this;
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
      command.add(job.vprjFile);
      command.add(job.mpegFile_cut);
      command.add("/l:" + lockFile);
      process = new backgroundProcess();
      log.print(">> Running adcut on " + job.mpegFile + " ...");
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
         } else {
            log.warn("adcut job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE--- job=" + job.type + " output=" + job.mpegFile_cut);
            // Remove Ad Cut files if option enabled
            if ( config.RemoveComcutFiles == 1 ) {
               if (file.delete(job.vprjFile))
                  log.print("(Deleted vprj file: " + job.vprjFile + ")");
               if (file.delete(job.edlFile))
                  log.print("(Deleted edl file: " + job.edlFile + ")");
            }
            // Remove .mpg file if option enabled
            if ( config.RemoveComcutFiles_mpeg == 1 ) {
               if (file.delete(job.mpegFile))
                  log.print("(Deleted mpeg file: " + job.mpegFile + ")");
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
         ofp.write("openFlag = VideoReDo.FileOpen( sourceFile )" + eol);
         ofp.write("" + eol);
         ofp.write("if openFlag = false then" + eol);
         ofp.write("   wscript.stderr.writeline( \"? Unable to open file/project: \" + sourceFile )" + eol);
         ofp.write("   wscript.quit 3" + eol);
         ofp.write("end if" + eol);
         ofp.write("" + eol);
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
