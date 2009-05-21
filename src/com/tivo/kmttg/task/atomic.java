package com.tivo.kmttg.task;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
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

public class atomic {
   private backgroundProcess process;
   private jobData job;
   private Stack<String> args = new Stack<String>();

   // constructor
   public atomic(jobData job) {
      debug.print("job=" + job);
      this.job = job;
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;
      
      if ( ! file.isFile(config.AtomicParsley) ) {
         log.error("AtomicParsley not found: " + config.AtomicParsley);
         schedule = false;
      }
      
      if ( ! file.isFile(job.encodeFile) ) {
         log.error("encode file not found: " + job.encodeFile);
         schedule = false;
      }
      
      job.metaFile = job.encodeFile + ".txt";
      if ( ! file.isFile(job.metaFile) ) {
         log.error("encode file not found: " + job.metaFile);
         schedule = false;
      }
      
      atomicGetArgs();
      if ( args.isEmpty() ) {
         log.error("Failed to parse meta file: " + job.metaFile);
      }
      
      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.encodeFile) ) schedule = false;
      }
      
      if (schedule) {
         if ( start() ) {
            job.process_atomic   = this;
            job.status           = "running";
            job.time             = new Date().getTime();
         }
         return true;
      } else {
         return false;
      }      
   }

   // Return false if starting command fails, true otherwise
   private Boolean start() {
      debug.print("");
      Stack<String> command = new Stack<String>();
      command.add(config.AtomicParsley);
      for (int i=0; i<args.size(); ++i) {
         command.add(args.get(i));
      }
      process = new backgroundProcess();
      log.print(">> Running AtomicParsley on " + job.encodeFile + " ...");
      if ( process.run(command) ) {
         log.print(process.toString());
      } else {
         log.error("Failed to start command: " + process.toString());
         process.printStderr();
         process = null;
         jobMonitor.removeFromJobList(job);
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
         if (config.GUI) {
            // Update status in job table
            String t = jobMonitor.getElapsedTime(job.time);
            config.gui.jobTab_UpdateJobMonitorRowStatus(job, t);               
         }
        return true;
      } else {
         // Job finished         
         jobMonitor.removeFromJobList(job);
         
         // Check for problems
         int failed = 0;
         // No or empty encodeFile means problems
         if ( ! file.isFile(job.encodeFile) || file.isEmpty(job.encodeFile) ) {
            failed = 1;
         }
         
         // exit code != 0 => trouble
         if (exit_code != 0) {
            failed = 1;
         }
         
         if (failed == 1) {
            log.error("AtomicParsley failed (exit code: " + exit_code + " ) - check command: " + process.toString());
            process.printStderr();
         } else {
            // Print statistics for the job
            log.warn("AtomicParsley job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE---");
         }
      }
      return false;
   }

   // Build arguments for AtomicParsley run based on encode file meta data file
   private void atomicGetArgs() {
      if ( ! file.isFile(job.metaFile) ) return;
      Hashtable <String,String> h = new Hashtable<String,String>();
      h.put("MediaKind", "Movie");
      
      // Parse metaFile
      try {
         BufferedReader ifp = new BufferedReader(new FileReader(job.metaFile));
         String line;
         String name="", value="";
         while ( (line = ifp.readLine()) != null ) {
            debug.print("line=" + line);
            name  = line.replaceFirst("(.+)\\s+:\\s+(.+)$", "$1");
            value = line.replaceFirst("(.+)\\s+:\\s+(.+)$", "$2");
            if ( ! h.containsKey(name) ) h.put(name, value);
         }
         ifp.close();
         if (h.get("isEpisodic").equals("true") ) h.put("MediaKind", "TV Show");
         args.add(job.encodeFile);
         args.add("--overWrite");
         args.add("-S");
         args.add(h.get("MediaKind"));
         if (h.containsKey("title") ) {
            args.add("--title");
            args.add(h.get("title"));
         }
         if (h.containsKey("vProgramGenre") ) {
            args.add("--grouping");
            args.add(h.get("vProgramGenre"));
         }
         if (h.containsKey("originalAirDate") ) {
            args.add("--year");
            args.add(h.get("originalAirDate"));
         }
         if (h.containsKey("description") ) {
            args.add("--description");
            args.add(h.get("description"));
         }
         if (h.containsKey("seriesTitle") ) {
            args.add("-H");
            args.add(h.get("seriesTitle"));
         }
         if (h.containsKey("episodeTitle") ) {
            args.add("--TVEpisode");
            args.add(h.get("episodeTitle"));
         }
         if (h.containsKey("episodeNumber") ) {
            args.add("--TVEpisodeNum");
            args.add(h.get("episodeNumber"));
         }
         if (h.containsKey("callsign") ) {
            args.add("--TVNetwork");
            args.add(h.get("callsign"));
         }
         return;
      }
      catch (IOException ex) {
         log.error(ex.toString());
         return;
      }
   }
}
