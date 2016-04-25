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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Serializable;
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
import com.tivo.kmttg.util.ffmpeg;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class ffcut extends baseTask implements Serializable {
   private static final long serialVersionUID = 1L;
   private backgroundProcess process;
   private jobData job;
   private String batchFile = null;
   private Long totalSize = 0L;

   // constructor
   public ffcut(jobData job) {
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
            log.warn("SKIPPING FFCUT, FILE ALREADY EXISTS: " + job.mpegFile_cut);
            schedule = false;
         } else {
            log.warn("OVERWRITING EXISTING FILE: " + job.mpegFile_cut);
         }
      }
            
      if ( ! file.isFile(config.ffmpeg) ) {
         log.error("ffmpeg not found: " + config.ffmpeg);
         schedule = false;
      }
      
      if ( ! file.isFile(job.edlFile) ) {
         log.error("edl file not found: " + job.edlFile);
         schedule = false;
      }
            
      if ( ! file.isFile(job.mpegFile) ) {
         log.error("source file not found: " + job.mpegFile);
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
            job.process = this;
            jobMonitor.updateJobStatus(job, "running");
            job.time    = new Date().getTime();
         }
         return true;
      } else {
         return false;
      }      
   }

   // Return false if starting command fails, true otherwise
   public Boolean start() {
      debug.print("");
      
      // Make batch file
      job.demuxFiles = new Stack<String>();
      batchFile = makeBatchFile();
      if (file.isFile(batchFile)) {
         Stack<String> command = new Stack<String>();
         if (! config.OS.equals("windows"))
            command.add("/bin/sh");
         command.add(batchFile);
         process = new backgroundProcess();
         log.print(">> Running ffcut on " + job.mpegFile + " ...");
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
      } else {
         log.error("Failed to create batch script file: " + batchFile);
         process = null;
         jobMonitor.kill(job);
         return false;
      }
   }
   
   public void kill() {
      debug.print("");
      process.kill();
      log.warn("Killing '" + job.type + "' job: " + process.toString());
      file.delete(batchFile);
   }

   // Check status of a currently running job
   // Returns true if still running, false if job finished
   // If job is finished then check result
   public Boolean check() {
      int exit_code = process.exitStatus();
      if (exit_code == -1) {
         // Still running
         if (config.GUIMODE) {
            // Update STATUS column
            if ( file.isFile(job.mpegFile_cut) ) {
               if (totalSize == 0)
                  sumSegmentFiles(job.demuxFiles);

               // Update status in job table
               Long size = file.size(job.mpegFile_cut);
               String s = String.format("%.2f MB", (float)size/Math.pow(2,20));
               String t = jobMonitor.getElapsedTime(job.time);
               int pct = 0;
               if (totalSize > 0)
                  pct = Math.round((float)((float)size*100.0/totalSize));
               
               if ( jobMonitor.isFirstJobInMonitor(job) ) {
                  // Update STATUS column
                  config.gui.jobTab_UpdateJobMonitorRowStatus(job, t + "---" + s);
                  
                  // Update title & progress bar
                  String title = String.format("ffcut: %d%% %s", pct, config.kmttg);
                  config.gui.setTitle(title);
                  config.gui.progressBar_setValue(pct);
               } else {
                  // Update STATUS column
                  config.gui.jobTab_UpdateJobMonitorRowStatus(job, String.format("%d%%",pct) + "---" + s);
               }
            } else {
               String t = jobMonitor.getElapsedTime(job.time);
               config.gui.jobTab_UpdateJobMonitorRowStatus(job, t);
               if ( jobMonitor.isFirstJobInMonitor(job) ) {
                  // If 1st job then update title & progress bar
                  String title = String.format("ffcut: %s %s", t, config.kmttg);
                  config.gui.setTitle(title);
               }               
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
         // No or empty mpegFile_cut means problems
         if ( ! file.isFile(job.mpegFile_cut) || file.isEmpty(job.mpegFile_cut) ) {
            log.error("Unable to find output file: " + job.mpegFile_cut);
            failed = 1;
         }
         
         // exit status != 0 => problem
         if (exit_code != 0) {
            failed = 1;
         }
         
         if (failed == 1) {
            log.error("ffcut failed (exit code: " + exit_code + " ) - check command: " + process.toString());
            process.printStderr();
            jobMonitor.kill(job); // This called so that family of jobs is killed
         } else {
            log.warn("ffcut job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE--- job=" + job.type);
            
            // Remove original .mpg file if remove option enabled
            if ( job.mpegFile_cut != null && config.RemoveComcutFiles_mpeg == 1 ) {
               if (file.delete(job.mpegFile))
                  log.print("(Deleted mpeg file: " + job.mpegFile + ")");
            }
            
            // Remove Ad Cut files if option enabled
            if ( config.RemoveComcutFiles == 1 ) {
               if (file.delete(job.vprjFile))
                  log.print("(Deleted vprj file: " + job.vprjFile + ")");
               if (file.delete(job.edlFile))
                  log.print("(Deleted edl file: " + job.edlFile + ")");
               String xclFile = job.mpegFile + ".Xcl";
               if (file.isFile(xclFile) && file.delete(xclFile))
                  log.print("(Deleted xcl file: " + xclFile + ")");
               String txtFile = string.replaceSuffix(job.mpegFile, ".txt");
               if (file.isFile(txtFile) && file.delete(txtFile))
                  log.print("(Deleted comskip txt file: " + txtFile + ")");
            }            
         }
         
         // Remove the batch file
         file.delete(batchFile);
         
         // Remove the segment files
         removeSegmentFiles(job.demuxFiles);
      }
      return false;
   }
   
   // Create batch script to create cut segment files and joiner
   private String makeBatchFile() {
      String eol = "\n";
      Stack<String> commands = edlToFFcut(job.edlFile);
      if (commands.size() == 0) {
         log.error("edlToFFcut returned no commands");
         return null;
      }
      
      // Make batch script with ffmpeg commands
      String suffix = ".sh";
      if (config.OS.equals("windows")) {
         suffix = ".bat";
         eol += "\r";
      }
      try {
         File tmp = File.createTempFile("ffcut", suffix, new File(config.programDir));
         tmp.deleteOnExit();
         batchFile = tmp.getPath();
         BufferedWriter ofp = new BufferedWriter(new FileWriter(batchFile, false));
         // Segment file generation
         for (String command : commands) {
            ofp.write(command + eol);
         }
         // Combine all segment files together
         ofp.write("\"" + config.ffmpeg + "\" -fflags +genpts -i \"concat:");
         Boolean first = true;
         for (String f : job.demuxFiles) {
            if (! first)
               ofp.write("|");
            ofp.write(f);
            first = false;
         }
         String format = "";
         if (string.getSuffix(job.mpegFile_cut).equals("mpg"))
            format = "-f dvd";
         ofp.write("\" -acodec copy -vcodec copy " + format + " -y \"" + job.mpegFile_cut + "\"" + eol);
         ofp.close();
         if ( ! config.OS.equals("windows") ) {
            String[] command = new String[] {"chmod", "ugo+x", batchFile};
            Runtime.getRuntime().exec(command);
         }
         return batchFile;
      } catch (Exception e) {
         log.error("ffcut makeBatchFile - " + e.getMessage());
      }
      return null;
   }
   
   private Stack<String> edlToFFcut(String edlFile) {
      Stack<String> commands = new Stack<String>();
      Stack<Hashtable<String,Float>> points = new Stack<Hashtable<String,Float>>();
      try {
         Float duration = -1f;
         Hashtable<String,String> info = ffmpeg.getVideoInfo(job.mpegFile);
         if (info != null)
            duration = Float.parseFloat(info.get("duration"));
         BufferedReader ifp = new BufferedReader(new FileReader(edlFile));
         Pattern p = Pattern.compile("^(\\S+)\\s+(\\S+)\\s+0\\s*$");
         String line;
         while ( (line=ifp.readLine()) != null) {
            Matcher m = p.matcher(line);
            if (m.matches()) {
               Hashtable<String,Float> h = new Hashtable<String,Float>();
               h.put("start", Float.parseFloat(m.group(1)));
               h.put("end", Float.parseFloat(m.group(2)));
               points.push(h);
            }
         }
         ifp.close();
         if (points.size() > 0) {
            if (points.get(0).get("start") != 0f) {
               // Must include segment before 1st block of commercials
               String ss = "0.00";
               Float start = points.get(0).get("start");
               String t = "" + String.format("%.2f", start);
               commands.push(ffCut(ss, t));
            }
            for (int i=1; i<points.size(); ++i) {
               Float end_last = points.get(i-1).get("end");
               String ss = "" + String.format("%.2f", end_last);
               Float delta = points.get(i).get("start") - end_last;
               String t = "" + String.format("%.2f", delta);
               commands.push(ffCut(ss,t));
            }
            // If last commercial end point < duration then one more show segment needed
            Float last = points.get(points.size()-1).get("end");
            if (last < duration) {
               Float delta = duration - last;
               if (delta > 1) {
                  String ss = "" + String.format("%.2f", last);
                  String t = "" + String.format("%.2f", delta);
                  commands.push(ffCut(ss,t));
               }
            }
         }
      } catch (Exception e) {
         log.error("ffcut edlToFFcut - " + e.getMessage());
         commands.clear();
         return commands;
      }
      return commands;
   }
   
   private String ffCut(String ss, String t) {
      int index = job.demuxFiles.size();
      String suffix = string.getSuffix(job.mpegFile);
      String outFile = string.replaceSuffix(job.mpegFile, "_cut_" + index + "." + suffix);
      job.demuxFiles.push(outFile);
      String command = "\"" + config.ffmpeg + "\"";
      command += " -i \"" + job.mpegFile + "\"";
      command += " -acodec copy -vcodec copy -ss " + ss + " -t " + t + " -y \"" + outFile + "\"";
      return command;
   }
   
   private void sumSegmentFiles(Stack<String> files) {
      if (files == null)
         return;
      for (String f : files) {
         totalSize += file.size(f);
      }
   }
   
   private void removeSegmentFiles(Stack<String> files) {
      if (files == null)
         return;
      for (String f : files) {
         file.delete(f);
      }
   }
}
