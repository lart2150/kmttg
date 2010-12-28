package com.tivo.kmttg.task;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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

public class vrdreview implements Serializable {
   private static final long serialVersionUID = 1L;
   String  vrd = null;
   private backgroundProcess process;
   private jobData job;

   // constructor
   public vrdreview(jobData job) {
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
      String[] pnames = {"VRDPlus.exe", "VRDPlus3.exe", "VideoReDo.exe", "VideoReDo3.exe", "VideoReDo4.exe"};
      for (int i=0; i<pnames.length; ++i) {
         vrd = config.VRD + s + pnames[i];
         if (file.isFile(vrd)) break;
      }
      
      if ( ! file.isFile(vrd) ) {
         log.error("Could not determine VideRedo GUI executable path in installation dir: " + config.VRD);
         schedule = false;
      }
                        
      if ( ! file.isFile(job.mpegFile) ) {
         log.error("mpeg file not found: " + job.mpegFile);
         schedule = false;
      }
      
      // Make a vprjFile with no cuts if requested
      if (config.VrdReview_noCuts == 1 && ! file.isFile(job.vprjFile)) {
         schedule = createBasicVprjFile(job.vprjFile, job.mpegFile);
      }
      
      if ( ! file.isFile(job.vprjFile) ) {
         log.error("VPrj file not found: " + job.vprjFile);
         schedule = false;
      }
      
      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.vprjFile, job) ) {
            schedule = false;
         }
      }
      
      if (schedule) {
         if ( start() ) {
            job.process_vrdreview = this;
            jobMonitor.updateJobStatus(job, "running");
            job.time              = new Date().getTime();
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
      command.add(vrd);
      command.add(job.vprjFile);
      process = new backgroundProcess();
      log.print(">> Running vrdreview on " + job.vprjFile + " ...");
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
      //debug.print("");
      int exit_code = process.exitStatus();
      if (exit_code == -1) {
         // Still running
         if (config.GUIMODE) {
            // Update STATUS column
            String t = jobMonitor.getElapsedTime(job.time);
            config.gui.jobTab_UpdateJobMonitorRowStatus(job, t);
            
            if ( jobMonitor.isFirstJobInMonitor(job) ) {
               // If 1st job then update title & progress bar
               String title = String.format("vrdreview: %s %s", t, config.kmttg);
               config.gui.setTitle(title);
            }
         }
        return true;
      } else {
         // Job finished         
         if ( jobMonitor.isFirstJobInMonitor(job) ) {
            config.gui.setTitle(config.kmttg);
         }
         jobMonitor.removeFromJobList(job);
         
         // Check for problems
         int failed = 0;
         // No or empty vprjFile means problems
         if ( ! file.isFile(job.vprjFile) || file.isEmpty(job.vprjFile) ) {
            failed = 1;
         }
         
         if (config.VrdReview_noCuts == 1) {
            // Look for VRD default edit file output
            String tryit = string.replaceSuffix(job.mpegFile, " (02).mpg");
            String tryit2 = string.replaceSuffix(job.mpegFile, "_cut.mpg");
            if ( ! file.isFile(tryit) && ! file.isFile(tryit2)) {
               log.error("vrdreview expected output file not available: " + tryit + " or " + tryit2);
               failed = 1;
            }
         }
         
         if (failed == 1) {
            log.error("vrdreview failed (exit code: " + exit_code + " ) - check command: " + process.toString());
            process.printStderr();
         } else {
            log.warn("vrdreview job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE--- job=" + job.type + " output=" + job.vprjFile);
            
            if (config.VrdReview_noCuts == 1) {
               if (config.RemoveComcutFiles == 1) {
                  if (file.delete(job.vprjFile))
                     log.print("(Deleted vprj file: " + job.vprjFile + ")");
               }
               
               // Remove .mpg file if option enabled
               if (config.RemoveComcutFiles_mpeg == 1) {
                  if (file.delete(job.mpegFile))
                     log.print("(Deleted mpeg file: " + job.mpegFile + ")");
               }
            }

         }
      }
      return false;
   }
   
   // Create a VRD vprj file with no file cuts - just source video file
   private Boolean createBasicVprjFile(String vprjFile, String inputFile) {
      try {
         BufferedWriter ofp = new BufferedWriter(new FileWriter(vprjFile));
         ofp.write("<Version>2\n");
         ofp.write("<Filename>" + inputFile + "\n");
         ofp.close();
      }
      catch (IOException ex) {
         log.error("Failed to write to file: " + vprjFile);
         log.error(ex.toString());
         return false;
      }
      return true;
   }

}
