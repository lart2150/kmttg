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
//import java.net.HttpURLConnection;
//import java.net.URL;
import java.net.URLEncoder;
import java.util.Date;
import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.pyTivo;
import com.tivo.kmttg.util.string;

public class push extends baseTask implements Serializable {
   private static final long serialVersionUID = 1L;
   private Thread thread = null;
   private Boolean thread_running = false;
   //private String host = "localhost";
   //private String port = "9032";
   //private int timeout_http = 60;
   private int timeout_ffmpeg = 10;
   private Stack<Hashtable<String,String>> shares = null;
   private String videoFile = null; // file to be pushed
   //private String share = null;     // pyTivo share name to push to
   private String path = null;      // pyTivo file path relative to share path
   //private String push_file = null; // pyTivo file base name
   private Boolean success = false;
   private backgroundProcess process;
   public jobData job;
   
   public push(jobData job) {
      debug.print("job=" + job);
      if (config.VrdReview_noCuts == 1) {
         // Look for VRD default edit file output
         if (! file.isFile(job.videoFile)) {
            String tryit = file.vrdreviewFileSearch(job.startFile);
            if (tryit != null)
               job.videoFile = tryit;
         }
      }
      this.job = job;
      /* Disabled because push disabled
      if (config.pyTivo_config != null) {
         shares = pyTivo.parsePyTivoConf(config.pyTivo_config);
         port = config.pyTivo_port;
      }
      host = config.pyTivo_host;
      */
      videoFile = pyTivo.lowerCaseVolume(job.videoFile);
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;
      
      // videoFile extension may have changed
      if (! file.isFile(videoFile) && videoFile.endsWith(".mpg")) {
         String extensions[] = {".ts", ".mp4"};
         for (String ext : extensions) {
            String tryit = videoFile;
            tryit = tryit.replaceFirst("\\.mpg", ext);
            if (file.isFile(tryit)) {
               videoFile = tryit;
               break;
            }
         }
      }
      
      // If no pyTivo shares available then nothing can be done
      /* Disabled because push disabled
      if (shares == null) {
         log.error("No pyTivo video shares found in pyTivo config file: " + config.pyTivo_config);
         return false;
      }
      if (shares.size() == 0) {
         log.error("No pyTivo video shares found in pyTivo config file: " + config.pyTivo_config);
         return false;
      }
      */
      // Check that file to be pushed resides under a pyTivo share
      // NOTE: This will define share, path & push_file strings
      if ( ! inPyTivoShare(videoFile) ) {
         log.error("This file is not located in a pyTivo share directory");
         log.error("Available pyTivo shares:");
         log.error(getShareInfo());
         schedule = false;
      }
      
      /* Disabled because push disabled
      if ( job.pyTivo_tivo == null ) {
         log.error("tivoName to push to not defined");
         schedule = false;
      }
      */
      // Check that file to be pushed is a video file
      if ( ! isVideo(videoFile) ) {
         log.error("This is not a valid video file to be pushed");
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
   
   public Boolean start() {
      debug.print("");
      // Run push method in a separate thread
      class AutoThread implements Runnable {
         AutoThread() {}       
         public void run () {
            /* Disabled because push disabled
            success = push_file(job.pyTivo_tivo, share, path, push_file);
            */
         }
      }
      thread_running = true;
      AutoThread t = new AutoThread();
      thread = new Thread(t);
      thread.start();

      return true;
   }
   
   public void kill() {
      debug.print("");
      thread.interrupt();
      log.warn("Killing '" + job.type + "' file: " + videoFile);
   }
   
   // Check status of a currently running job
   // Returns true if still running, false if job finished
   public Boolean check() {
      if (thread_running) {
         // Still running
         if (config.GUIMODE) {
            // Update STATUS column
            config.gui.jobTab_UpdateJobMonitorRowStatus(job, "running");
         }
         return true;
      } else {
         // Job finished
         jobMonitor.removeFromJobList(job);
         if (success) {
            log.warn("push job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE--- job=" + job.type + " video=" + videoFile);
         }
      }
      return false;
   }
   
   private String getShareInfo() {
      String s = "";
      if (shares != null && shares.size() > 0) {
         for (int i=0; i<shares.size(); ++i) {
            s += "share=" + shares.get(i).get("share");
            s += " path=" + shares.get(i).get("path");
            s += "\n";
         }
      }
      return s;
   }
   
   private Boolean inPyTivoShare(String videoFile) {
      if (shares == null) {
         return false;
      }
      if (shares.size() == 0) {
         return false;
      }
      for (int i=0; i<shares.size(); ++i) {
         if (videoFile.startsWith(shares.get(i).get("path"))) {
            String shareDir = shares.get(i).get("path");
            /* Disabled because push disabled
            share = shares.get(i).get("share");
            push_file = videoFile;
            */
            path = string.dirname(videoFile.substring(shareDir.length()+1, videoFile.length()));
            if (config.OS.equals("windows")) {
               path = path.replaceAll("\\\\", "/");
            }
            if (path.endsWith("/")) {
               path = path.substring(0,path.length()-1);
            }

            return true;
         }
      }
      return false;
   }
      
   // Use ffmpeg to determine if given file is a video file
   private Boolean isVideo(String testFile) {
      if (file.isDir(testFile)) {
         return false;
      }
      if ( testFile.matches("^.+\\.txt$") ) {
         return false;
      }
      // Use ffmpeg command to determine if not a recognized video file      
      Stack<String> command = new Stack<String>();
      command.add(config.ffmpeg);
      command.add("-i");
      command.add(testFile);
      backgroundProcess process = new backgroundProcess();
      if ( process.run(command) ) {
         // Wait (with timeout) for command to terminate
         try {
            process.Wait(timeout_ffmpeg*1000);            
            // Parse stderr
            Stack<String> l = process.getStderr();
            if (l.size() > 0) {
               for (int i=0; i<l.size(); ++i) {
                  if (l.get(i).matches("^.+\\s+Video:\\s+.+$")) {
                     return true;
                  }
               }
            }
         } catch (Exception e) {
            log.error("Timing out command that was taking too long: " + process.toString());
         }
         return false;
      } else {
         process.printStderr();
      }
      return false;
   }
   
   // Contact pyTivo to push a file
   /* Disabled because push disabled
   private Boolean push_file(String tivoName, String share, String path, String push_file) {
      if (file.isFile(push_file)) {
         String header = "http://" + host + ":" + port + "/TiVoConnect?Command=Push&Container=";
         String path_entry;
         if (path.length() == 0) {
            path_entry = "&File=/";
         } else {
            path_entry = "&File=/" + urlEncode(path) + "/";
         }
         String urlString = header + urlEncode(share) + path_entry +
            urlEncode(string.basename(push_file)) + "&tsn=" + urlEncode(tivoName);
         try {
            URL url = new URL(urlString);
            log.warn(">> Pushing " + push_file + " to " + tivoName);
            log.print(url.toString());
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.addRequestProperty("REFERER", "/");
            c.setRequestMethod("GET");
            c.setReadTimeout(timeout_http*1000);
            c.connect();
            String response = c.getResponseMessage();
            if (response.equals("OK")) {
               thread_running = false;
               return true;
            } else {
               log.error("Received unexpected response for: " + urlString);
               log.error(response);
               thread_running = false;
               return false;
            }
         }
         catch (Exception e) {
            log.error("Connection failed: " + urlString);
            log.error(e.toString());
         }
      } else {
         log.error("File does not exist - " + push_file);
      }
      thread_running = false;
      return false;
   }
   */
   
   public static String urlEncode(String s) {
      String encoded;
      try {
         encoded = URLEncoder.encode(s, "UTF-8");
         return encoded;
      } catch (Exception e) {
         log.error("Cannot encode url: " + s);
         log.error(e.toString());
         return s;
      }
   }


}
