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
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class custom extends baseTask implements Serializable {
   private static final long serialVersionUID = 1L;
   private backgroundProcess process;
   private jobData job;

   // constructor
   public custom(jobData job) {
      debug.print("job=" + job);
      this.job = job;
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;
            
      if ( ! customCommandExists() ) {
         log.error("Invalid custom command: " + config.customCommand);
         schedule = false;
      }
                  
      if (schedule) {
         if ( start() ) {
            job.process = this;
            jobMonitor.updateJobStatus(job, "running");
            job.time             = new Date().getTime();
         }
         return true;
      } else {
         return false;
      }      
   }

   // Return false if starting command fails, true otherwise
   public Boolean start() {
      debug.print("");
      String mpegFile_cut = "";
      if (job.tivoFile == null) job.tivoFile = "";
      if (job.metaFile == null) job.metaFile = "";
      if (job.mpegFile == null) job.mpegFile = "";
      if (job.mpegFile_cut != null) {
         mpegFile_cut = job.mpegFile_cut;
         if (! file.isFile(mpegFile_cut)) {
            String tryit = file.vrdreviewFileSearch(job.startFile);
            if (tryit != null && tryit.contains("_cut"))
               mpegFile_cut = tryit;
         }
      }
      if (job.srtFile == null) job.srtFile = "";
      if (job.encodeFile == null) job.encodeFile = "";
      
      // Build initial literal command
      Stack<String>s = buildCustomCommandStack(config.customCommand);
      
      // Replace any special file arguments with their real values
      Stack<String>command = new Stack<String>();
      String string;
      for (int i=0; i<s.size(); ++i) {
         string = s.get(i);
         string = string.replaceAll("\\[tivoFile\\]", escapeBackSlashes(job.tivoFile));
         string = string.replaceAll("\\[metaFile\\]", escapeBackSlashes(job.metaFile));
         string = string.replaceAll("\\[mpegFile\\]", escapeBackSlashes(job.mpegFile));
         string = string.replaceAll("\\[mpegFile_cut\\]", escapeBackSlashes(mpegFile_cut));
         string = string.replaceAll("\\[srtFile\\]", escapeBackSlashes(job.srtFile));
         string = string.replaceAll("\\[encodeFile\\]", escapeBackSlashes(job.encodeFile));
         string = string.replaceAll("\\[downloadURL\\]", escapeAmpersand(job.url));
         command.add(string);
      }
      
      process = new backgroundProcess();
      log.print(">> Running custom command ...");
      if ( process.run(command) ) {
         job.customCommand = process.toString();
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
            // Update status in job table
            String t = jobMonitor.getElapsedTime(job.time);
            config.gui.jobTab_UpdateJobMonitorRowStatus(job, t);
            
            if ( jobMonitor.isFirstJobInMonitor(job) ) {
               // If 1st job then update title & progress bar
               String title = String.format("custom: %s %s", t, config.kmttg);
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
         
         log.warn("custom job completed: " + jobMonitor.getElapsedTime(job.time));
         log.warn("exit code: " + exit_code);
         log.print("---DONE--- job=" + job.type);
         if (exit_code != 0)
            process.printStderr();
      }
      return false;
   }
   
   public static Boolean customCommandExists() {
      debug.print("");
      return customCommandExists(config.customCommand);
   }
   
   public static Boolean customCommandExists(String command) {
      debug.print("command=" + command);
      if (command == null) return false;
      if (command.length() > 0) {
         Stack<String> s = buildCustomCommandStack(command);
         if ( file.isFile(s.get(0)) ) return true;
      }
      return false;
   }
   
   private static Stack<String> buildCustomCommandStack(String command) {
      debug.print("command=" + command);
      Stack<String> c = new Stack<String>();
      if (command.contains("\"") ) {
         // Separate out by spaces with quote delimiters
         Pattern p = Pattern.compile("(?:([^\\s\"]\\S*)|(?:\"((?:i|[^i])*?)(?:(?:\"\\s)|(?:\"$)|$)))");
         Matcher m = p.matcher(command);
         while (m.find()) {
            String s = string.removeLeadingTrailingSpaces(m.group());
            s = s.replaceAll("\"", "");
            c.add(s);
         }
      } else {
         String[] p = command.split("\\s+");
         for(int i=0; i<p.length; ++i) {
            c.add(p[i]);
         }
      }
      return c;
   }
   
   // It's crazy but in Java regex one backslash = 4 ...
   private static String escapeBackSlashes(String s) {
      return s.replaceAll("\\\\", "\\\\\\\\");
   }
   
   private static String escapeAmpersand(String url) {
      if (url != null && config.OS.equals("windows"))
         url = url.replaceAll("&", "^^^&");
      return url;
   }
   
}
