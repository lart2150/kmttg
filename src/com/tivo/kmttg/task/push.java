package com.tivo.kmttg.task;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.HttpURLConnection;
import java.net.URL;
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
import com.tivo.kmttg.util.string;

public class push {
   private Thread thread = null;
   private Boolean thread_running = false;
   private String host = "localhost";
   private String port = "9032";
   private int timeout_http = 10;
   private int timeout_ffmpeg = 10;
   private Stack<Hashtable<String,String>> shares = null;
   private String share = null;     // pyTivo share name to push to
   private String path = null;      // pyTivo file path relative to share path
   private String push_file = null; // pyTivo file base name
   private backgroundProcess process;
   public jobData job;
   
   public push(jobData job) {
      debug.print("job=" + job);
      this.job = job;
      if (config.pyTivo_config != null) {
         shares = parsePyTivoConf(config.pyTivo_config);
      }
      host = config.pyTivo_host;
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;
      
      // If no pyTivo shares available then nothing can be done
      if (shares == null) {
         return false;
      }
      if (shares.size() == 0) {
         log.error("No pyTivo video shares found in pyTivo config file: " + config.pyTivo_config);
         return false;
      }
      // Check that file to be pushed resides under a pyTivo share
      // NOTE: This will define share, path & push_file strings
      if ( ! inPyTivoShare(job.videoFile) ) {
         schedule = false;
      }
      
      if ( job.tivoName == null ) {
         log.error("tivoName to push to not defined");
         schedule = false;
      }
      // Check that file to be pushed is a video file
      if ( ! isVideo(job.videoFile) ) {
         schedule = false;
      }
      
      if (schedule) {
         if ( start() ) {
            job.process_push = this;
            jobMonitor.updateJobStatus(job, "running");
            job.time             = new Date().getTime();
         }
         return true;
      } else {
         return false;
      }      
   }
   
   private Boolean start() {
      debug.print("");
      // Run push method in a separate thread
      class AutoThread implements Runnable {
         AutoThread() {}       
         public void run () {
            push_file(job.tivoName, share, path, push_file);
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
      log.warn("Killing '" + job.type + "' file: " + job.videoFile);
   }
   
   // Check status of a currently running job
   // Returns true if still running, false if job finished
   public Boolean check() {
      if (thread_running) {
         // Still running
         if (config.GUI) {
            // Update STATUS column
            config.gui.jobTab_UpdateJobMonitorRowStatus(job, "running");
         }
         return true;
      }      
      return false;
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
            share = shares.get(i).get("share");
            push_file = string.basename(videoFile);
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
   
   private Stack<Hashtable<String,String>> parsePyTivoConf(String config) {
      Stack<Hashtable<String,String>> s = new Stack<Hashtable<String,String>>();
      String username = null;
      String password = null;
      
      try {
         BufferedReader ifp = new BufferedReader(new FileReader(config));
         String line = null;
         String key = null;
         Hashtable<String,String> h = new Hashtable<String,String>();
         while (( line = ifp.readLine()) != null) {
            // Get rid of leading and trailing white space
            line = line.replaceFirst("^\\s*(.*$)", "$1");
            line = line.replaceFirst("^(.*)\\s*$", "$1");
            if (line.length() == 0) continue; // skip empty lines
            if (line.matches("^#.+")) continue; // skip comment lines
            if (line.matches("^\\[.+\\]")) {
               key = line.replaceFirst("\\[", "");
               key = key.replaceFirst("\\]", "");
               if ( ! h.isEmpty() ) {
                  if (h.containsKey("share") && h.containsKey("path")) {
                     s.add(h);
                  }
                  h = new Hashtable<String,String>();
               }
               continue;               
            }
            if (key == null) continue;
            
            if (key.equalsIgnoreCase("server")) {
               if (line.matches("(?i)^port\\s*=.+")) {
                  String[] l = line.split("=");
                  if (l.length > 1) {
                     port = string.removeLeadingTrailingSpaces(l[1]);
                  }
               }
               if (line.matches("(?i)^tivo_username\\s*=.+")) {
                  String[] l = line.split("=");
                  if (l.length > 1) {
                     username = string.removeLeadingTrailingSpaces(l[1]);
                  }
               }
               if (line.matches("(?i)^tivo_password\\s*=.+")) {
                  String[] l = line.split("=");
                  if (l.length > 1) {
                     password = string.removeLeadingTrailingSpaces(l[1]);
                  }
               }
               continue;
            }
            if (line.matches("(?i)^type\\s*=.+")) {
               if (line.matches("(?i)^.+=\\s*video.*")) {
                  if ( ! h.containsKey("share") ) {
                     h.put("share", key);
                  }
               }
               continue;
            }
            if (line.matches("(?i)^path\\s*=.+")) {
               String[] l = line.split("=");
               if (l.length > 1) {
                  h.put("path", string.removeLeadingTrailingSpaces(l[1]));
               }
            }
         }
         ifp.close();
         if ( ! h.isEmpty() ) {
            if (h.containsKey("share") && h.containsKey("path")) {
               s.add(h);
            }
         }
         
         // tivo_username & tivo_password are required for pushes to work
         if (username == null) {
            log.error("Required 'tivo_username' is not set in pyTivo config file: " + config);
         }
         if (password == null) {
            log.error("Required 'tivo_password' is not set in pyTivo config file: " + config);
         }
         if (username == null || password == null) {
            return null;
         }

      }
      catch (Exception ex) {
         log.error("Problem parsing pyTivo config file: " + config);
         log.error(ex.toString());
         return null;
      }
      
      return s;
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
   private Boolean push_file(String tivoName, String share, String path, String push_file) {
      if (file.isFile(push_file)) {
         String header = "http://" + host + ":" + port + "/TiVoConnect?Command=Push&Container=";
         String path_entry;
         if (path.length() == 0) {
            path_entry = "&File=/";
         } else {
            path_entry = "&File=/" + path + "/";
         }
         String urlString = header + share + path_entry + string.basename(push_file) + "&tsn=" + tivoName;
         try {
            URL url = new URL(urlString);
            log.print(url.toString());
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
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

}
