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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.Hashtable;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.SkipImport;
import com.tivo.kmttg.rpc.SkipManager;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
//import com.tivo.kmttg.util.ffmpeg;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
//import com.tivo.kmttg.util.mediainfo;
import com.tivo.kmttg.util.string;

public class comskip extends baseTask implements Serializable {
   private static final long serialVersionUID = 1L;
   private backgroundProcess process;
   private jobData job;
   private String outputFile = null;
   private String options = null;
   private String comskipIni = null;

   // constructor
   public comskip(jobData job) {
      debug.print("job=" + job);
      this.job = job;
      outputFile = job.edlFile;
      if (job.vprjFile != null) {
         outputFile = job.vprjFile;
         options = "--videoredo";
      }
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;
      if (job.exportSkip) {
         // Generate cut points from AutoSkip data if available
         String exportFile = null;
         if (job.vprjFile != null) {
            exportFile = SkipImport.vrdExport(job.entry);
            if (exportFile != null)
               job.vprjFile = exportFile;
         }
         else {
            exportFile = SkipImport.edlExport(job.entry);
            if (exportFile != null)
               job.edlFile = exportFile;
            // Add vprj creation here by user request (not normally needed)
            SkipImport.vrdExport(job.entry);
         }
         if (exportFile != null)
            schedule = false;
      }
      // Don't comskip if outputFile already exists
      if ( schedule && file.isFile(outputFile) ) {
         if (config.OverwriteFiles == 0) {
            log.warn("SKIPPING COMSKIP, FILE ALREADY EXISTS: " + outputFile);
            schedule = false;
         } else {
            log.warn("OVERWRITING EXISTING FILE: " + outputFile);
         }
      }
      
      if ( schedule && ! file.isFile(config.comskip) ) {
         log.error("comskip not found: " + config.comskip);
         schedule = false;
      }
      
      // comskipIni can be overriden locally by job
      comskipIni = config.comskipIni;
      if (job.comskipIni != null) {
         if ( file.isFile(job.comskipIni) ) {
            comskipIni = job.comskipIni;
         }
      }
      
      if ( schedule && ! file.isFile(comskipIni) ) {
         log.error("comskip.ini not found: " + comskipIni);
         schedule = false;
      }
      
      if ( schedule && ! file.isFile(job.mpegFile) ) {
         log.error("mpeg file not found: " + job.mpegFile);
         schedule = false;
      }
      
      // Empty mpegFile => don't start
      if ( schedule && file.size(job.mpegFile) == 0) {
         log.error("mpeg file empty: " + job.mpegFile);
         schedule = false;
      }
      
      if (job.vprjFile != null) {
         // Want output_videoredo3=1 in comskipIni if target is VRD
         if ( ! enableVrd3() )
            schedule = false;
      }
      
      // Intentionally commented out as commercial versions of comskip do support H.264
      // Check for non-mpeg2 input file
      /*Hashtable<String,String> info = null;
      if (file.isFile(config.mediainfo))
         info = mediainfo.getVideoInfo(job.mpegFile);
      else if (file.isFile(config.ffmpeg))
         info = ffmpeg.getVideoInfo(job.mpegFile);
      if (info != null) {
         if (! info.get("video").equals("mpeg2video")) {
            log.error("input video=" + info.get("video") + ": comskip only supports mpeg2 video");
            schedule = false;
         }
      }*/      
      
      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(outputFile, job) ) {
            schedule = false;
         }
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
      Stack<String> command = new Stack<String>();
      command.add(config.comskip);
      command.add("--ini");
      command.add(comskipIni);
      if (options != null)
         command.add(options);
      command.add(job.mpegFile);
      process = new backgroundProcess();
      log.print(">> Running comskip on " + job.mpegFile + " ...");
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
            String t = jobMonitor.getElapsedTime(job.time);
            int pct = comskipGetPct();
            
            if ( jobMonitor.isFirstJobInMonitor(job) ) {
               // Update STATUS column
               config.gui.jobTab_UpdateJobMonitorRowStatus(job, t);
               
               // If 1st job then update title & progress bar
               String title = String.format("comskip: %d%% %s", pct, config.kmttg);
               config.gui.setTitle(title);
               config.gui.progressBar_setValue(pct);
            } else {
               // Update STATUS column
               if (pct != 0)
                  config.gui.jobTab_UpdateJobMonitorRowStatus(job, String.format("%d%%",pct));
               else
                  config.gui.jobTab_UpdateJobMonitorRowStatus(job, t);
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
         // No or empty outputFile means problems
         if ( ! file.isFile(outputFile) || file.isEmpty(outputFile) ) {
            log.error("Output file not generated or empty: " + outputFile);
            failed = 1;
         }
         
         // NOTE: comskip returns exit status 1!
         //if (exit_code != 0) {
         //   failed = 1;
         //}
         
         if (failed == 1) {
            log.error("comskip failed (exit code: " + exit_code + " ) - check command: " + process.toString());
            process.printStderr();
            jobMonitor.kill(job); // This called so that family of jobs is killed
         } else {
            fixVprj(); // comskip generated scene marker entries may need to be fixed
            log.warn("comskip job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE--- job=" + job.type + " output=" + outputFile);
            // Cleanup
            String[] extensions = {".log", ".logo.txt"};
            String f;
            for (int i=0; i<extensions.length; ++i) {
               f = string.replaceSuffix(job.mpegFile, extensions[i]);
               // NOTE: Only delete a file ending in one of extensions
               if (f.endsWith(extensions[i]) && file.isFile(f)) file.delete(f);
            }
            if (job.SkipPoint != null) {
               String prefix = string.replaceSuffix(string.basename(job.mpegFile), "");
               file.cleanUpFiles(prefix);
            }
            
            if (job.autoskip && config.VrdReview == 0 && config.comskip_review == 0 && file.isFile(job.edlFile)) {
               // Skip table entry creation
               Stack<Hashtable<String,Long>> cuts = SkipImport.edlImport(job.edlFile, job.duration);
               if (cuts != null && cuts.size() > 0) {
                  if (SkipManager.hasEntry(job.contentId))
                     SkipManager.removeEntry(job.contentId);
                  SkipManager.saveEntry(job.contentId, job.offerId, 0L, job.title, job.tivoName, cuts);
               }
            }
         }
      }
      return false;
   }
   
   // Obtain pct complete from comskip stderr
   private int comskipGetPct() {
      String last = process.getStderrLast();
      if (last.matches("^.+%$")) {
         String[] all = last.split("\\s+");
         String pct_str = all[all.length-1].replaceFirst("%", "");
         try {
            return (int)Float.parseFloat(pct_str);
         }
         catch (NumberFormatException n) {
            return 0;
         }
      }
      return 0;
   }
   
   // Fix comskip generated V3 VPrj file if there are scene markers in wrong format
   /* Sample wrong format
   </cutlist></VideoReDoProject>
   <SceneMarker 0>114114000
   <SceneMarker 1>419585833
   <SceneMarker 2>6276770500
   
   Fixed scene marker example:
   <SceneMarker Sequence="1">2650781667</SceneMarker>
   */
   private Boolean fixVprj() {
      Boolean changed = false;
      String tmpFile = file.makeTempFile("vprjfix");
      try {
         BufferedWriter ofp = new BufferedWriter(new FileWriter(tmpFile, true));
         BufferedReader ini = new BufferedReader(new FileReader(outputFile));
         String line = null;
         Pattern marker = Pattern.compile("<SceneMarker (\\d+)>(\\d+)$");
         String endStr = "</VideoReDoProject>";
         Matcher m;
         Stack<String> s = new Stack<String>();
         while (( line = ini.readLine()) != null) {
            // Get rid of leading and trailing white space
            line = line.replaceFirst("^\\s*(.*$)", "$1");
            line = line.replaceFirst("^(.*)\\s*$", "$1");
            if (line.length() == 0) continue; // skip empty lines
            line = line.replace(endStr, "");
            m = marker.matcher(line);
            if (m.matches()) {
               changed = true;
               int num = Integer.parseInt(m.group(1)) + 1;
               String time = m.group(2);
               line = "<SceneMarker Sequence=\"" + num + "\">" + time + "</SceneMarker>";
               s.add(line);
               continue;
            }
            ofp.write(line + "\n");
         }
         if (changed) {
            ofp.write("<SceneList>\n");
            for (String l : s)
               ofp.write(l + "\n");
            ofp.write("</SceneList>\n");
         }
         ofp.write(endStr + "\n");
         ini.close();
         ofp.close();         

         if (changed) {
            // Remove outputFile and replace with tmpFile
            log.warn("Fixing scene markers in file: " + outputFile);         
            file.delete(outputFile);
            return(file.rename(tmpFile, outputFile));
         } else {
            file.delete(tmpFile);
         }
      }         
      catch (IOException ex) {
         log.error("fixVprj: Problem parsing or chaging file: " + outputFile);
         return false;
      }
      return changed;
   }
   
   // Add output_videoredo3=1 if not already there in comskip ini file
   private Boolean enableVrd3() {
      try {
         // First figure out if we need to change
         BufferedReader ini = new BufferedReader(new FileReader(comskipIni));
         String line = null;
         Pattern p_one = Pattern.compile("^output_videoredo3=1.*$");
         Matcher m;
         Boolean found = false;
         while (( line = ini.readLine()) != null) {
            // Get rid of leading and trailing white space
            line = line.replaceFirst("^\\s*(.*$)", "$1");
            line = line.replaceFirst("^(.*)\\s*$", "$1");
            if (line.length() == 0) continue; // skip empty lines
            m = p_one.matcher(line);
            if (m.matches())
               found = true;
         }
         ini.close();
         if (found)
            return true;
         
         log.warn("Adding output_videoredo3=1 in comskip file: " + comskipIni);         
         BufferedWriter ofp = new BufferedWriter(new FileWriter(comskipIni, true));
         ofp.write("\r\noutput_videoredo3=1\r\n");
         ofp.close();         
      }         
      catch (IOException ex) {
         log.error("Problem parsing or writing to comskip ini file: " + comskipIni);
         return false;
      }
      return true;
   }

}
