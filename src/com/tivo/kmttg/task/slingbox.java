package com.tivo.kmttg.task;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.Stack;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class slingbox implements Serializable {
   private static final long serialVersionUID = 1L;
   String command = "";
   String script = "";
   String pidFile = "";
   String uniqueName = "";
   String perl_script;
   private backgroundProcess process;
   public jobData job;
   
   public slingbox(jobData job) {
      debug.print("job=" + job);
      this.job = job;
            
      // Generate unique script names
      script = file.makeTempFile("script");
      pidFile = file.makeTempFile("pid");
      uniqueName = UUID.randomUUID().toString();
      if (config.OS.equals("windows"))
         script += ".bat";
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;
      
      perl_script = config.programDir + File.separator + "slingbox" + File.separator;
      if (config.slingBox_type.equals("Slingbox 350/500"))
         perl_script += "rec350.pl";
      else
         perl_script += "rec2.pl";
      
      if ( ! file.isFile(perl_script) ) {
         log.error("Can't find perl capture script: " + perl_script);
         schedule = false;
      }
      
      if ( ! file.isFile(job.slingbox_perl) ) {
         log.error("Invalid Perl executable: " + job.slingbox_perl);
         schedule = false;
      }
      
      if ( ! file.isFile(config.ffmpeg) ) {             
         log.error("ffmpeg not found: " + config.ffmpeg);
         schedule = false;
      }
      
      if (config.slingBox_ip.length() == 0) {
         log.error("Slingbox IP not specified");
         schedule = false;
      }
      
      if (config.slingBox_port.length() == 0) {
         log.error("Slingbox port not specified");
         schedule = false;
      }
      
      if (config.slingBox_pass.length() == 0) {
         log.error("Slingbox password not specified");
         schedule = false;
      }
      
      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.slingbox_file, job) ) schedule = false;
      }
      if (file.isFile(job.slingbox_file))
            file.delete(job.slingbox_file);
      
      if (schedule) {
         if ( start() ) {
            job.process_slingbox = this;
            jobMonitor.updateJobStatus(job, "running");
            job.time             = new Date().getTime();
         }
         return true;
      } else {
         return false;
      }      
   }
   
   private Boolean start() {
      debug.print("");
      String vs = "16", hd = null;
      if (config.slingBox_res.equals("1920x1080"))
         vs = "16";
      if (config.slingBox_res.equals("640x480"))
         vs = "5";
      if (! config.slingBox_type.equals("Slingbox 350/500"))
         vs = "5";
      if (config.slingBox_type.equals("Slingbox Pro"))
         hd = "0";
      // Make main piped command string
      command = "\"" + job.slingbox_perl + "\" \"" + perl_script + "\" " +
         "-stdout " +
         "-ip "     + config.slingBox_ip + " " +
         "-port "   + config.slingBox_port + " " +
         "-pass "   + config.slingBox_pass + " " +
         "-vbw "    + config.slingBox_vbw + " " +
         "-vs "     + vs;
      if (hd != null)
         command += " -hd " + hd;
      if (job.slingbox_dur != null)
         command += " -dur " + job.slingbox_dur;
      command += " | \"" + config.ffmpeg + "\" -fflags +genpts -i - ";
      command += "-vcodec copy -acodec ac3 -ab 224k -y -f mpegts \"" + job.slingbox_file + "\"";
      
      // Make temporary script containing command
      try {
         BufferedWriter ofp = new BufferedWriter(new FileWriter(script));
         if (config.OS.equals("windows")) {
            hackToGetPid(ofp, uniqueName, pidFile);
         }
         ofp.write(command);
         if (config.OS.equals("windows"))
            ofp.write("\r");
         ofp.write("\n");
         ofp.close();
      } catch (IOException e) {
         log.error(e.toString());
         return false;
      }

      // Execute above script in native OS shell
      Stack<String> c = new Stack<String>();      
      if (config.OS.equals("windows")) {
         c.add("cmd.exe");
         c.add("/c");
      } else {
         c.add("sh");
      }
      c.add(script);
      process = new backgroundProcess();
      String message = "CAPTURING SLINGBOX";
      log.print(">> " + message + " TO " + job.slingbox_file + " ...");
      if ( process.run(c) ) {
         log.print(command);
      } else {
         log.error("Failed to start command: " + command);
         process.printStderr();
         process = null;
         jobMonitor.removeFromJobList(job);
         return false;
      }
      return true;
   }
   
   public void kill() {
      debug.print("");
      log.warn("Killing '" + job.type + "' job: " + command);
      if (config.OS.equals("windows")) {
         // For Windows process.kill doesn't kill child processes so this is a hack to do that
         try {
            String pid = getPidFromFile();
            if (pid != null) {
               log.warn("killing windows pid=" + pid);
               Process p = Runtime.getRuntime().exec("taskkill /f /t /pid " + pid);
               p.waitFor();
            }
         } catch (Exception e) {
            log.error("Exception finding/killing pid: " + e.getMessage());
         }
      }
      process.kill();
      cleanup();
   }
   
   // Check status of a currently running job
   // Returns true if still running, false if job finished
   // If job is finished then check result
   public Boolean check() {
      //debug.print("");
      int exit_code = process.exitStatus();
      if (exit_code == -1) {
         // Still running
         if (config.GUIMODE && file.isFile(job.slingbox_file)) {
            // Update status in job table
            Long size = file.size(job.slingbox_file);
            String s = String.format("%.2f MB", (float)size/Math.pow(2,20));
            String t = ffmpegGetTime();
            
            if ( jobMonitor.isFirstJobInMonitor(job) ) {
               // Update STATUS column 
               config.gui.jobTab_UpdateJobMonitorRowStatus(job, t + "---" + s);
               
               // If 1st job then update title
               String title = String.format("slingbox %s %s", t, config.kmttg);
               config.gui.setTitle(title);
            } else {
               // Update STATUS column            
               config.gui.jobTab_UpdateJobMonitorRowStatus(job, t + "---" + s);
            }
         }
         return true;
      } else {
         // Job finished
         if (config.GUIMODE) {
            if ( jobMonitor.isFirstJobInMonitor(job) ) {
               config.gui.setTitle(config.kmttg);
            }
         }
         
         jobMonitor.removeFromJobList(job);
         
         // Check for problems
         int failed = 0;
         
         // exit code != 0 => trouble
         if (exit_code != 0) {
            failed = 1;
         }
         
         // No or empty output means problems         
         if ( file.isEmpty(job.slingbox_file) ) {
            failed = 1;
         } else {
            // Print statistics for the job
            String s = String.format("%.2f MB", file.size(job.slingbox_file)/Math.pow(2,20));
            String t = jobMonitor.getElapsedTime(job.time);
            log.warn(job.slingbox_file + ": size=" + s + " elapsed=" + t);
         }
         
         if (failed == 1) {
            log.error("Capture to file failed: " + job.slingbox_file);
            log.error("Exit code: " + exit_code);
            process.printStderr();
            file.delete(job.slingbox_file);
         } else {
            log.print("---DONE--- job=" + job.type + " output=" + job.slingbox_file);
         }
      } // job finished
      cleanup();
      
      return false;
   }
   
   // Hack lines to add to Windows script file to obtain pid
   // This is needed to be able to kill cmd.exe using taskkill
   private void hackToGetPid(BufferedWriter ofp, String uniqueName, String pidFile) {
      try {
         String eol = "\r\n";
         ofp.write("@echo off" + eol);
         ofp.write("set name=" + uniqueName + eol);
         ofp.write("TITLE %name%" + eol);
         ofp.write("TASKLIST /V /NH | findstr /i \"%name%\" > \"" + pidFile + "\"" + eol);
      } catch (IOException e) {
         log.error(e.toString());
      }
   }
  
   // Get pid from file with single line:
   // Sample line looks like (pid is the 2nd column):
   // cmd.exe 2800 RDP-Tcp#1 0 2,988 K Running INET\moyekj 0:00:00 ...
   private String getPidFromFile() {
      if (file.isFile(pidFile)) {
         try {
            BufferedReader ifp = new BufferedReader(new FileReader(pidFile));
            String line = ifp.readLine();
            ifp.close();
            String pid = "";
            String s[] = line.split("\\s+");
            if (s.length > 2)
               pid = s[1];
            if (pid.length() > 0 && pid.matches("^\\d+\\s*")) {
               return pid;
            } else {
               log.error("Unable to determine windows pid to kill windows process");
               log.error("(pidFile line is: '" + line + "')");
            }
         }
         catch (IOException ex) {
            log.error("getPidFromFile error: " + ex.toString());
            return null;
         }
      }
      return null;
   }
   
   private void cleanup() {
      file.delete(script);
      file.delete(pidFile);
   }
      
   // Obtain current length in ms of encoding file from ffmpeg stderr
   private String ffmpegGetTime() {
      String zero = "0:00:00";
      String last = process.getStderrLast();
      if (last.matches("")) return zero;
      if (last.contains("time=")) {
         String[] l = last.split("time=");
         String[] ll = l[l.length-1].split("\\s+");
         try {
            if (ll[0].contains(":")) {
               // "HH:MM:SS.MS" format
               Pattern p = Pattern.compile("(\\d+):(\\d+):(\\d+).(\\d+)");
               Matcher m = p.matcher(ll[0]);
               if (m.matches()) {
                  long HH = Long.parseLong(m.group(1));
                  long MM = Long.parseLong(m.group(2));
                  long SS = Long.parseLong(m.group(3));
                  return String.format("%d:%02d:%02d",HH,MM,SS);
               }
            }
         }
         catch (NumberFormatException n) {
         }
      }
      return zero;
   }

}
