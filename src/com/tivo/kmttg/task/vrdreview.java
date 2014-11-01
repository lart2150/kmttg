package com.tivo.kmttg.task;

import java.io.File;
import java.io.Serializable;
import java.util.Date;
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
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class vrdreview implements Serializable {
   private static final long serialVersionUID = 1L;
   String  vrdscript = null;
   String  cscript = null;
   String  lockFile = null;
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
      cscript = System.getenv("SystemRoot") + s + "system32" + s + "cscript.exe";
                        
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

      if ( schedule ) {
         lockFile = file.makeTempFile("VRDLock");      
         if ( lockFile == null || ! file.isFile(lockFile) ) {
            log.error("Failed to created lock file: " + lockFile);
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
         if (lockFile != null) file.delete(lockFile);
         return false;
      }      
   }

   // Return false if starting command fails, true otherwise
   private Boolean start() {
      debug.print("");
      // Create the vbs script
      vrdscript = config.programDir + "\\VRDscripts\\vrdreview.vbs";      
      if ( ! file.isFile(vrdscript) ) {
         log.error("File does not exist: " + vrdscript);
         log.error("Aborting. Fix incomplete kmttg installation");
         jobMonitor.removeFromJobList(job);
         return false;
      }

      Stack<String> command = new Stack<String>();
      command.add(cscript);
      command.add("//nologo");
      command.add(vrdscript);
      command.add(job.vprjFile);
      command.add("/l:" + lockFile);
      process = new backgroundProcess();
      log.print(">> Running vrdreview on " + job.vprjFile + " ...");
      if ( process.run(command) ) {
         log.print(process.toString());
      } else {
         log.error("Failed to start command: " + process.toString());
         process.printStderr();
         process = null;
         jobMonitor.removeFromJobList(job);
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
            
            if (config.VrdReview_noCuts == 1) {
               if (config.RemoveComcutFiles == 1) {
                  if (file.delete(job.vprjFile))
                     log.print("(Deleted vprj file: " + job.vprjFile + ")");
                  String xclFile = job.mpegFile + ".Xcl";
                  if (file.isFile(xclFile) && file.delete(xclFile))
                     log.print("(Deleted xcl file: " + xclFile + ")");
               }
               
               // Remove .mpg file if option enabled
               if (config.RemoveComcutFiles_mpeg == 1) {
                  if (file.delete(job.mpegFile))
                     log.print("(Deleted mpeg file: " + job.mpegFile + ")");
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
      if (lockFile != null) file.delete(lockFile);
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

}
