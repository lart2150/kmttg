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

import java.io.File;
import java.io.Serializable;
import java.util.Date;
import java.util.Stack;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.Entities;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class vrdencode extends baseTask implements Serializable {
   private static final long serialVersionUID = 1L;
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
            
      vrdscript = config.programDir + "\\VRDscripts\\encode.vbs";      
      if ( ! file.isFile(vrdscript) ) {
         log.error("File does not exist: " + vrdscript);
         log.error("Aborting. Fix incomplete kmttg installation");
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
      
      if (config.VrdCombineCutEncode == 1 && job.vprjFile != null) {
         if (job.encodeFile.equals(job.mpegFile)) {
            // Don't want encodeFile same as input file depending on VRD profile selected
            if (file.isFile(job.mpegFile)) {
               job.encodeFile = job.mpegFile_cut;
               if (config.GUIMODE) {
                  // Update OUTPUT column in GUI to reflect changed output file name
                  String output;
                  if (config.jobMonitorFullPaths == 1)
                     output = "(" + job.encodeName + ") " + job.encodeFile;
                  else
                     output = "(" + job.encodeName + ") " + string.basename(job.encodeFile);
                  config.gui.jobTab_UpdateJobMonitorRowOutput(job, output);
               }
            }
         }
      }
      
      // Don't encode if encodeFile already exists
      if ( file.isFile(job.encodeFile) ) {
         if (config.OverwriteFiles == 0) {
            log.warn("SKIPPING ENCODE, FILE ALREADY EXISTS: " + job.encodeFile);
            // Schedule an AtomicParsley job if relevant
            scheduleAtomicParsley();
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
      }
      
      if (! file.isFile(job.mpegFile_cut) && config.VrdReview_noCuts == 1) {
         String tryit = file.vrdreviewFileSearch(job.startFile);
         if (tryit != null)
            mpeg = tryit;
      }
      
      // If vprjFile exists and Filename it refers to exists then use it
      if (job.vprjFile != null && file.isFile(job.vprjFile) && vprjReferenceExists(job.vprjFile)) {
         mpeg = job.vprjFile;
         log.warn("NOTE: vrdencode using project file as input: " + mpeg);
      }
      
      if ( ! file.isFile(mpeg)) {
         mpeg = job.tivoFile;
      }
      
      if ( ! file.isFile(mpeg) ) {
         log.error("mpeg file not given or doesn't exist: " + mpeg);
         schedule = false;
      }
      job.inputFile = mpeg;
      
      if (config.VrdCombineCutEncode == 1 && job.vprjFile != null) {
         if (file.isFile(job.vprjFile)) {
            job.inputFile = job.vprjFile;
         } else {
            log.error("VRD combine Ad Cut & Encode option selected but .Vprj file doesn't exist: " + job.vprjFile);
            schedule = false;
         }
      }
                  
      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.encodeFile, job) ) {
            schedule = false;
         }
      }
      
      if (schedule) {
         if ( start() ) {
            job.process = this;
            jobMonitor.updateJobStatus(job, "running");
            job.time              = new Date().getTime();
         }
         return true;
      } else {
         if (lockFile != null) file.delete(lockFile);
         return false;
      }     
   }

   // Return false if starting command fails, true otherwise
   public Boolean start() {
      debug.print("");
      Stack<String> command = new Stack<String>();
      command.add(cscript);
      command.add("//nologo");
      command.add(vrdscript);
      command.add(job.inputFile);
      command.add(job.encodeFile);
      command.add("/l:" + lockFile);
      command.add("/p:" + job.encodeName);
      if (config.VrdAllowMultiple == 1) {
         command.add("/m");
      }
      process = new backgroundProcess();
      log.print(">> ENCODING WITH PROFILE '" + job.encodeName + "' TO FILE " + job.encodeFile + " ...");
      if ( process.run(command) ) {
         log.print(process.toString());
      } else {
         log.error("Failed to start command: " + process.toString());
         process.printStderr();
         process = null;
         jobMonitor.kill(job);
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
         if (config.GUIMODE) {
            String t = jobMonitor.getElapsedTime(job.time);
            String size = null;
            if ( file.isFile(job.encodeFile) ) {               
               size = String.format("%.2f MB", (float)file.size(job.encodeFile)/Math.pow(2,20));
               if (size.equals("0.00 MB")) size = null;
            }
            
            // Update job table
            int pct = encodeGetPct();

            String status = t;
            if ( jobMonitor.isFirstJobInMonitor(job) ) {
               // Update STATUS column, title & progress bar
               String title = String.format("vrdencode: %d%% %s", pct, config.kmttg);
               config.gui.setTitle(title);
               config.gui.progressBar_setValue(pct);
            } else {
               status = String.format("%d%%",pct);
            }
            // Update STATUS column
            if (size != null) status += "---" + size;
            config.gui.jobTab_UpdateJobMonitorRowStatus(job, status);

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
            jobMonitor.kill(job); // This called so that family of jobs is killed
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
            
            // If metadata file exists for input file but not output file, then copy it
            String input_meta = job.inputFile + ".txt";
            String output_meta = job.encodeFile + ".txt";
            if ( ! file.isFile(output_meta) ) {
               if (file.isFile(input_meta)) {
                  if (file.copy(input_meta, output_meta))
                     log.warn("Copied metadata file " + input_meta + " to " + output_meta);
                  else
                     log.error("Failed to copy metadata file: " + input_meta);
               }
            }
            
            // Remove .TiVo file if option enabled
            if ( !job.hasMoreEncodingJobs && job.tivoFile != null && job.inputFile.equals(job.tivoFile) )  {
               if (config.RemoveTivoFile == 1 && file.isFile(job.tivoFile)) {
                  if ( file.delete(job.tivoFile) ) {
                     log.print("(Deleted file: " + job.tivoFile + ")");
                  } else {
                     log.error("Failed to delete file: "+ job.tivoFile);
                  }
               }
            }
            
            // Remove .mpg file if option enabled
            if (!job.hasMoreEncodingJobs && config.RemoveMpegFile == 1) {
               if ( file.delete(job.inputFile) ) {
                  log.print("(Deleted file: " + job.inputFile + ")");
               } else {
                  log.error("Failed to delete file: "+ job.inputFile);
               }
               if ( file.delete(input_meta) ) {
                  log.print("(Deleted file: " + input_meta + ")");
               }               
               if ( file.delete(job.mpegFile) ) {
                  log.print("(Deleted file: " + job.mpegFile + ")");
               }
            }
            
            // Remove Ad Cut files if option enabled
            if ( config.VrdCombineCutEncode == 1 && config.RemoveComcutFiles == 1 ) {
               if (file.isFile(job.vprjFile) && file.delete(job.vprjFile))
                  log.print("(Deleted vprj file: " + job.vprjFile + ")");
               if (file.isFile(job.edlFile) && file.delete(job.edlFile))
                  log.print("(Deleted edl file: " + job.edlFile + ")");
               String xclFile = job.mpegFile + ".Xcl";
               if (file.isFile(xclFile) && file.delete(xclFile))
                  log.print("(Deleted xcl file: " + xclFile + ")");
               String txtFile = string.replaceSuffix(job.mpegFile, ".txt");
               if (file.isFile(txtFile) && file.delete(txtFile))
                  log.print("(Deleted comskip txt file: " + txtFile + ")");
            }
            
            if (config.RemoveComcutFiles == 1 && file.isFile(job.vprjFile))
               if (file.delete(job.vprjFile))
                  log.print("(Deleted vprj file: " + job.vprjFile + ")");
            
            // Schedule an AtomicParsley job if relevant
            scheduleAtomicParsley();
         }
      }
      if (lockFile != null) file.delete(lockFile);
      return false;
   }
   
   private void scheduleAtomicParsley() {      
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
   
   private Boolean vprjReferenceExists(String vprjFile) {
      try {
         DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
         DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
         Document doc = docBuilder.parse(vprjFile);
         NodeList rdList = doc.getElementsByTagName("Filename");
         if (rdList.getLength() > 0) {
            String fileName;
            Node n = rdList.item(0);
            if ( n != null) {
               fileName = n.getTextContent();
               if (file.isFile(fileName))
                  return true;
               fileName = Entities.replaceHtmlEntities(fileName);
               if (file.isFile(fileName))
                  return true;
            }
         }
      } catch (Exception e) {
         log.error("" + e.getMessage());
      }
      log.warn("vrdeconde: Referenced file in .Vprj file doesn't exist, so not using it: " + vprjFile);
      return false;
   }

}

