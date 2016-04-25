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
import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.SkipImport;
import com.tivo.kmttg.rpc.SkipManager;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class comskip_review extends baseTask implements Serializable {
   private static final long serialVersionUID = 1L;
   private backgroundProcess process;
   private String txtFile, comskipIni;
   private String outputFile = null;
   private String options = null;
   private jobData job;

   // constructor
   public comskip_review(jobData job) {
      debug.print("job=" + job);
      this.job = job;
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;
      
      if ( ! file.isFile(config.comskip) ) {
         log.error("comskip executable not found: " + config.comskip);
         schedule = false;
      }
      
      // comskipIni can be overriden locally by job
      comskipIni = config.comskipIni;
      if (job.comskipIni != null) {
         if ( file.isFile(job.comskipIni) ) {
            comskipIni = job.comskipIni;
         }
      }
      
      if ( ! file.isFile(comskipIni) ) {
         log.error("comskip.ini not found: " + comskipIni);
         schedule = false;
      }
      
      // Input file is comskip txt file
      txtFile = string.replaceSuffix(job.mpegFile, ".txt");
      if ( ! file.isFile(txtFile) ) {
         log.error("comskip .txt file not found: " + txtFile);
         schedule = false;
      }
      
      // Decide what the output file of interest is
      if (job.vprjFile != null && config.VRD == 1) {
         outputFile = job.vprjFile;
      }
      if (outputFile == null)
         outputFile = job.edlFile;
      if (outputFile == null) {
         log.error("Could not determine output file");
         schedule = false;
      }
      
      if ( ! file.isFile(job.mpegFile) ) {
         log.error("mpeg file not found: " + job.mpegFile);
         schedule = false;
      }
      
      if (schedule) {
         if ( start() ) {
            job.process = this;
            jobMonitor.updateJobStatus(job, "running");
            job.time = new Date().getTime();
         }
         return true;
      } else {
         return false;
      }      
   }

   // Return false if starting command fails, true otherwise
   public Boolean start() {
      debug.print("");
      Stack<String> command = new Stack<String>();
      command.add(config.comskip);
      command.add("--ini");
      command.add(comskipIni);
      if (options != null)
         command.add(options);
      command.add(txtFile);
      process = new backgroundProcess();
      log.print(">> Running comskip_review on " + txtFile + " ...");
      log.warn("IMPORTANT: Remember to press Esc to exit comskip_review, don't click on X to close the window");
      log.warn("If you accidently click on X to close the window you'll have to find and kill comskip in Task Manager");
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
            // Update STATUS column
            String t = jobMonitor.getElapsedTime(job.time);
            config.gui.jobTab_UpdateJobMonitorRowStatus(job, t);
            
            if ( jobMonitor.isFirstJobInMonitor(job) ) {
               // If 1st job then update title & progress bar
               String title = String.format("comskip_review: %s %s", t, config.kmttg);
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
         
         log.warn("comskip_review job completed: " + jobMonitor.getElapsedTime(job.time));
         log.print("---DONE--- job=" + job.type + " output=" + outputFile);
                  
         if (job.autoskip && file.isFile(job.edlFile)) {
            // AutoSkip table entry creation
            Stack<Hashtable<String,Long>> cuts = SkipImport.edlImport(job.edlFile, job.duration);
            if (cuts != null && cuts.size() > 0) {
               if (SkipManager.hasEntry(job.contentId))
                  SkipManager.removeEntry(job.contentId);
               SkipManager.saveEntry(job.contentId, job.offerId, 0L, job.title, job.tivoName, cuts);
            }
         }
      }
      return false;
   }

}
