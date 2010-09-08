package com.tivo.kmttg.task;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Date;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.http;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.createMeta;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class javametadata implements Serializable {
   private static final long serialVersionUID = 1L;
   private Thread thread = null;
   private Boolean thread_running = false;
   private Boolean success = false;
   String outputFile = "";
   private backgroundProcess process;
   public  jobData job;
   
   public javametadata(jobData job) {
      debug.print("job=" + job);
      this.job = job;
      
      // Generate unique outputFile names
      outputFile = file.makeTempFile("meta");
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;
      if ( file.isFile(job.metaFile) ) {
         if (config.OverwriteFiles == 0) {
            log.warn("SKIPPING METADATA GENERATION, FILE ALREADY EXISTS: " + job.metaFile);
            schedule = false;
         } else {
            log.warn("OVERWRITING EXISTING FILE: " + job.metaFile);
         }
      }

      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.metaFile, job) ) schedule = false;
      }
      
      if (schedule) {
         if ( start() ) {
            job.process_javametadata = this;
            jobMonitor.updateJobStatus(job, "running");
            job.time = new Date().getTime();
         }
         return true;
      } else {
         return false;
      }      
   }
   
   private Boolean start() {
      debug.print("");
      if (job.url == null || job.url.length() == 0) {
         log.error("URL not given");
         jobMonitor.removeFromJobList(job);
         return false;
      }
      log.print(">> CREATING " + job.metaFile + " ...");
      log.print(job.url);
      Runnable r = new Runnable() {
         public void run () {
            try {
               success = getXML(job.url, "tivo", config.MAK);
               thread_running = false;
            }
            catch (Exception e) {
               success = false;
               thread_running = false;
               Thread.currentThread().interrupt();
            }
         }
      };
      thread_running = true;
      thread = new Thread(r);
      thread.start();

      return true;
   }
   
   public void kill() {
      debug.print("");
      thread.interrupt();
      log.warn("Killing '" + job.type + "' TiVo: " + job.tivoName);
      thread_running = false;
      file.delete(outputFile);
   }
   
   // Check status of a currently running job
   // Returns true if still running, false if job finished
   // If job is finished then check result
   public Boolean check() {
      if (thread_running) {
         // Still running
         if (config.GUI) {
            // Update STATUS column
            config.gui.jobTab_UpdateJobMonitorRowStatus(job, "running");
         }
         return true;
      } else {
         // Job finished
         jobMonitor.removeFromJobList(job);
         
         // Check for problems
         int failed = 0;
         
         // exit code != 0 => trouble
         if ( ! success ) {
            failed = 1;
         }
         
         // No or empty output means problems         
         if ( file.isEmpty(outputFile) ) {
            failed = 1;
         }
         
         // Check that first line is xml
         if (failed == 0) {
            try {
               BufferedReader xml = new BufferedReader(new FileReader(outputFile));
               String first = xml.readLine();
               xml.close();
               if ( ! first.toLowerCase().matches("^.+xml.+$") ) {
                  failed = 1;
                  log.error(first);
               }
            }
            catch (IOException ex) {
               failed = 1;
            }
         }
         
         if (failed == 1) {
            log.error("Failed to generate metadata file: " + job.metaFile);
         } else {
            log.warn("metadata job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE--- job=" + job.type + " output=" + job.metaFile);
            
            // Success, so create pyTivo metadata file
            job.metaTmpFile = outputFile;
            createMeta.createMetaFile(job, null);
         }
      }
      file.delete(outputFile);
      
      return false;
   }   
   
   private Boolean getXML(String url, String username, String password) throws InterruptedException, IOException, Exception {
      debug.print("url=" + url);
      InputStream in = http.noCookieInputStream(url, username, password);
      if (in == null) {
         return false;
      } else {
         int BUFSIZE = 65536;
         byte[] buffer = new byte[BUFSIZE];
         int c;
         FileOutputStream out = null;
         try {
            out = new FileOutputStream(outputFile);
            while ((c = in.read(buffer, 0, BUFSIZE)) != -1) {
               if (Thread.interrupted()) {
                  out.close();
                  in.close();
                  throw new InterruptedException("Killed by user");
               }
               out.write(buffer, 0, c);
            }
            out.close();
            in.close();
         }
         catch (FileNotFoundException e) {
            log.error(url + ": " + e.getMessage());
            if (out != null) out.close();
            if (in != null) in.close();
            throw new FileNotFoundException(e.getMessage());
         }
         catch (IOException e) {
            log.error(url + ": " + e.getMessage());
            if (out != null) out.close();
            if (in != null) in.close();
            throw new IOException(e.getMessage());
         }
         catch (Exception e) {
            log.error(url + ": " + e.getMessage());
            if (out != null) out.close();
            if (in != null) in.close();
            throw new Exception(e.getMessage(), e);
         }
         finally {
            if (out != null) out.close();
            if (in != null) in.close();
         }
      }

      return true;
   }

}
