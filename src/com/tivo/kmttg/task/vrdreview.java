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
import java.util.Hashtable;
import java.util.Stack;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

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

public class vrdreview extends baseTask implements Serializable {
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
      
      vrd = config.VRDexe;
      if (! file.isFile(vrd)) {
         // Try and find vrd automatically
         vrd = findVRD();
         if (vrd != null) {
            config.VRDexe = vrd;
            config.save();
         }
      }
      if (vrd == null) {
         log.error("Cannot find VRD executable - please specify path in config");
         schedule = false;
      } else {
         if ( ! file.isFile(vrd)) {
            log.error("Invalid path to VRD executable: " + vrd);
            log.error("Please provide valid path in config");
            schedule = false;
         }
      }
                        
      String sourceFile = job.mpegFile;
      if ( ! file.isFile(sourceFile) ) {
         if (file.isFile(job.tivoFile)) {
            sourceFile = job.tivoFile;
            log.warn("mpegFile not found, so using tivoFile instead: " + sourceFile);
         } else {
            log.error("mpeg file not found: " + job.mpegFile);
            schedule = false;
         }
      }
      
      // Make a vprjFile with no cuts if requested
      if (config.VrdReview_noCuts == 1 && ! file.isFile(job.vprjFile)) {
         if (job.entry != null &&
             job.entry.containsKey("contentId") &&
             SkipManager.hasEntry(job.entry.get("contentId"))) {
            log.warn("vrdreview: Using AutoSkip entry cut points");
            String vprjFile = SkipImport.vrdExport(job.entry);
            if (vprjFile != null)
               job.vprjFile = vprjFile;
            else
               schedule = createBasicVprjFile(job.vprjFile, sourceFile);
         } else {
            schedule = createBasicVprjFile(job.vprjFile, sourceFile);
         }
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
            job.process = this;
            jobMonitor.updateJobStatus(job, "running");
            job.time              = new Date().getTime();
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
            String[] exts = {" (02).mpg", " (02).ts"};
            for (String ext : exts) {
               String s = string.replaceSuffix(job.mpegFile, ext);
               if (file.isFile(s)) {
                  String cutFile = s.replaceFirst(" \\(02\\)", "_cut");
                  if (file.rename(s, cutFile))
                     log.print("(Renamed " + s + " to " + cutFile + ")");
               }
            }
         }
         
         if (failed == 1) {
            log.error("vrdreview failed (exit code: " + exit_code + " ) - check command: " + process.toString());
            process.printStderr();
         } else {
            log.warn("vrdreview job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE--- job=" + job.type + " output=" + job.vprjFile);
                        
            if (job.autoskip && file.isFile(job.vprjFile)) {
               // AutoSkip table entry creation
               Stack<Hashtable<String,Long>> cuts = SkipImport.vrdImport(job.vprjFile, job.duration);
               if (cuts != null && cuts.size() > 0) {
                  if (SkipManager.hasEntry(job.contentId))
                     SkipManager.removeEntry(job.contentId);
                  SkipManager.saveEntry(job.contentId, job.offerId, 0L, job.title, job.tivoName, cuts);
               }
            }
            
            if (config.VrdReview_noCuts == 1) {
               if (config.RemoveComcutFiles == 1) {
                  if (jobMonitor.getJobInFamily(job, "vrdencode") == null) {
                     // No VRD encode task following, so OK to delete .Vprj file
                     if (file.delete(job.vprjFile))
                        log.print("(Deleted vprj file: " + job.vprjFile + ")");
                  } else {
                     // VRD encode task follows, so may need to use .Vprj file
                     log.warn("Not deleting Vprj file, since VRD encode task follows");
                  }
                  String xclFile = job.mpegFile + ".Xcl";
                  if (file.isFile(xclFile) && file.delete(xclFile))
                     log.print("(Deleted xcl file: " + xclFile + ")");
               }
               
               // Remove .mpg file if option enabled
               if (config.RemoveComcutFiles_mpeg == 1) {
                  if (file.delete(job.mpegFile))
                     log.print("(Deleted mpeg file: " + job.mpegFile + ")");
               }
               
               // Remove .TiVo file if option enabled
               if (config.RemoveTivoFile == 1 && job.tivoFile != null) {
                  if (file.delete(job.tivoFile))
                     log.print("(Deleted tivo file: " + job.tivoFile + ")");
               }
            }
            
            // If job.mpegFile ends in .ts might have to rename metaFile
            if (job.mpegFile.endsWith(".ts")) {
               String metaFile = string.replaceSuffix(job.mpegFile, "_cut.mpg.txt");
               if (file.isFile(metaFile)) {
                  String s = string.replaceSuffix(job.mpegFile, "_cut.ts.txt");
                  if (file.rename(metaFile, s))
                     log.print("(Renamed " + metaFile + " to " + s);
               }
            }
         }
      }
      return false;
   }
   
   // Create a VRD vprj file with no file cuts - just source video file
   // NOTE: vprj file is xml, so use official xml writer to get proper character escapes
   private Boolean createBasicVprjFile(String vprjFile, String inputFile) {
      try {
         DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
         DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
         Document doc = docBuilder.newDocument();
         Element rootElement = doc.createElement("VideoReDoProject");
         rootElement.setAttribute("Version", "3");
         doc.appendChild(rootElement);
         Element fileElement = doc.createElement("Filename");
         fileElement.insertBefore(doc.createTextNode(inputFile), fileElement.getLastChild());
         rootElement.appendChild(fileElement);
         TransformerFactory tFactory = TransformerFactory.newInstance();
         Transformer transformer = tFactory.newTransformer();
         transformer.setOutputProperty("omit-xml-declaration", "yes");
         DOMSource source = new DOMSource(doc);
         StreamResult result = new StreamResult(new File(vprjFile));
         transformer.transform(source, result);
      }
      catch (Exception ex) {
         log.error("Failed to write to file: " + vprjFile);
         log.error(ex.toString());
         return false;
      }
      return true;
   }
   
   private String findVRD() {
	   String[] pfiles = {System.getenv("ProgramFiles(x86)"), System.getenv("ProgramFiles")};
	   String[] paths = {
		  "VideoReDoTVSuite6", "VideoReDoTVSuite5", "VideoReDoTVSuite4", "VideoReDoPlus"
	   };
	   String[] pnames = {
	      "VideoReDo6.exe", "VideoReDo5.exe", "VideoReDo4.exe", "VideoReDo3.exe",
		  "VideoReDo.exe", "VRDPlus3.exe", "VRDPlus.exe",    		   
	   };
	   String vrd;
	   for (String pfile : pfiles) {
		   for (String path : paths) {
			   for (String pname : pnames) {
				   vrd = pfile + File.separator + path + File.separator + pname;
				   if (file.isFile(vrd))
					   return vrd;
			   }
		   }
	   }
	   return null;
   }

}
