package com.tivo.kmttg.task;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.*;

public class metadata {
   String cookieFile = "";
   String outputFile = "";
   private backgroundProcess process;
   public jobData job;
   
   public metadata(jobData job) {
      debug.print("job=" + job);
      this.job = job;
      
      // Generate unique cookieFile and outputFile names
      cookieFile = file.makeTempFile("cookie");
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
      if ( ! file.isFile(config.curl) ) {             
         log.error("curl not found: " + config.curl);
         schedule = false;
      }

      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.metaFile, job) ) schedule = false;
      }
      
      if (schedule) {
         if ( start() ) {
            job.process_metadata = this;
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
      if (job.url == null || job.url.length() == 0) {
         log.error("URL not given");
         jobMonitor.removeFromJobList(job);
         return false;
      }
      Stack<String> command = new Stack<String>();
      command.add(config.curl);
      if (config.OS.equals("windows")) {
         command.add("--retry");
         command.add("3");
      }
      command.add("--anyauth");
      command.add("--globoff");
      command.add("--user");
      command.add("tivo:" + config.MAK);
      command.add("--insecure");
      command.add("--cookie-jar");
      command.add(cookieFile);
      command.add("--url");
      command.add(job.url);
      command.add("--output");
      command.add(outputFile);
      process = new backgroundProcess();
      log.print(">> CREATING " + job.metaFile + " ...");
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
      file.delete(cookieFile);
      file.delete(outputFile);
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
         if (exit_code != 0) {
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
            log.error("Exit code: " + exit_code);
            process.printStderr();
         } else {
            log.warn("metadata job completed: " + jobMonitor.getElapsedTime(job.time));
            log.print("---DONE---");
            
            // Success, so create pyTivo metadata file
            job.metaTmpFile = outputFile;
            createMetaFile();
         }
      }
      file.delete(cookieFile);
      file.delete(outputFile);
      
      return false;
   }
   
   // Create a pyTivo compatible metadata file from a TiVoVideoDetails xml download
   @SuppressWarnings("unchecked")
   private Boolean createMetaFile() {
      debug.print("");
      try {
         String[] nameValues = {
               "title", "seriesTitle", "description", "time",
               "mpaaRating", "movieYear", "isEpisode",
               "originalAirDate", "episodeTitle", "isEpisodic"
         };
         String[] valuesOnly = {"showingBits", "starRating", "tvRating"};
         String[] arrays = {
               "vActor", "vDirector", "vExecProducer", "vProducer",
               "vProgramGenre", "vSeriesGenre", "vAdvisory", "vHost",
               "vGuestStar", "vWriter", "vChoreographer"
         };
         
         BufferedReader xml = new BufferedReader(new FileReader(outputFile));
         String ll;
         Hashtable<String,Object> data = new Hashtable<String,Object>();
         while ( (ll = xml.readLine()) != null ) {
            debug.print("ll=" + ll);
            String[] line = ll.split(">");
                                   
            // Now parse all items tagged with <Item>
            String l, name, value;
            for (int j=0; j<line.length; ++j) {            
               l = line[j];
               
               // nameValues have value on following line
               for (int k=0; k<nameValues.length; k++) {
                  name = nameValues[k];
                  if (l.matches("^<" + name + ".*$")) {
                     j++;
                     value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
                     value = Entities.replaceHtmlEntities(value);
                     if (value.length() > 0)
                        data.put(name, value);
                     debug.print(name + "=" + value);
                  }
               }
            }
            
            for (int j=0; j<line.length; ++j) {            
               l = line[j];
               
               // valuesOnly have value on same line
               for (int k=0; k<valuesOnly.length; k++) {
                  name = valuesOnly[k];
                  if (l.matches("^<" + name + ".*$")) {
                     value = line[j].replaceFirst("^.+\"(.+)\".*$", "$1");
                     data.put(name, value);
                     debug.print(name + "=" + value);
                  }
               }
            }
            
            for (int j=0; j<line.length; ++j) {            
               l = line[j];
               debug.print("l=" + l);
                
               // arrays have 1 or more values
               for (int k=0; k<arrays.length; k++) {
                  name = arrays[k];
                  if (l.matches("^<" + name + "$")) {
                     Stack<String> values = new Stack<String>();
                     Boolean go = true;
                     while (go && j < line.length) {
                        j++;
                        if ( j < line.length ) {
                           if ( line[j].matches("^</" + name + "$") ) go = false;
                           if ( go && line[j].matches("^<element.*$") ) {
                              j++;
                              if ( ! line[j].matches("^<.+$") ) {
                                 value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
                                 values.add(value);
                              }
                           }
                        }
                     }
                     data.put(name, values);
                     debug.print(name + "=" + values);
                  }
               }           
            }
         }
                  
         // Post-process some of the data
         if ( data.containsKey("starRating") )
            data.put("starRating", "x" + data.get("starRating"));
         if ( data.containsKey("tvRating") )
            data.put("tvRating", "x" + data.get("tvRating"));
         if ( data.containsKey("isEpisodic") )
            data.put("isEpisode", data.get("isEpisodic"));
         if ( data.containsKey("description") )
            data.put("description", ((String) (data.get("description"))).replaceFirst("Copyright Tribune Media Services, Inc.", ""));
         
         if ( data.containsKey("mpaaRating") ) {
            Hashtable<String,String> map = new Hashtable<String,String>();
            map.put("G", "G1");
            map.put("PG", "P2");
            map.put("PG_13", "P3");
            map.put("R", "R4");
            map.put("X", "X5");
            map.put("NC_17", "N6");
            map.put("NR", "N8");
            String mpaaRating = map.get(data.get("mpaaRating"));
            if (mpaaRating != null)
               data.put("mpaaRating", mpaaRating);            
         }
         
         // Add additional data
         if ( job.episodeNumber != null && job.episodeNumber.length() > 0 )
            data.put("episodeNumber", job.episodeNumber);
         if ( job.displayMajorNumber != null ) {
            // Doesn't like sub-channel #s so strip them out
            // NOTE: New versions of pyTivo are fine with dashes now
            //data.put("displayMajorNumber", job.displayMajorNumber.replaceFirst("^(.+)-.+$", "$1"));
            data.put("displayMajorNumber", job.displayMajorNumber);
         }
         if ( job.callsign != null )
            data.put("callsign", job.callsign);
         if ( job.seriesId != null )
            data.put("seriesId", job.seriesId);
         
         // Now write all data to metaFile in pyTivo format
         BufferedWriter ofp = new BufferedWriter(new FileWriter(job.metaFile));
         
         String key;
         for (int i=0; i<nameValues.length; ++i) {
            key = nameValues[i];
            if (data.containsKey(key)) {
               if (data.get(key).toString().length() > 0)
                  ofp.write(key + " : " + data.get(key) + "\n");
            }
         }
         for (int i=0; i<valuesOnly.length; ++i) {
            key = valuesOnly[i];
            if (data.containsKey(key)) {
               if (data.get(key).toString().length() > 0)
                  ofp.write(key + " : " + data.get(key) + "\n");
            }
         }
         String[] additional = {"episodeNumber", "displayMajorNumber", "callsign", "seriesId"};
         for (int i=0; i<additional.length; ++i) {
            key = additional[i];
            if (data.containsKey(key)) {
               if (data.get(key).toString().length() > 0)
                  ofp.write(key + " : " + data.get(key) + "\n");
            }
         }
         for (int i=0; i<arrays.length; i++) {
            key = arrays[i];
            if (data.containsKey(key)) {
               Stack<String> values = (Stack<String>)data.get(key);
               for (int j=0; j<values.size(); ++j) {
                  ofp.write(key + " : " + values.get(j) + "\n");
               }
            }
         }

         xml.close();
         ofp.close();
         
      }
      catch (IOException ex) {
         log.error(ex.toString());
         file.delete(cookieFile);
         file.delete(outputFile);
         return false;
      }
      
      file.delete(cookieFile);
      file.delete(outputFile);
      return true;
   }
   
   public void printData(Hashtable<String,Object> data) {
      debug.print("");
      String name;
      Object value;
      log.print("metadata data:");
      for (Enumeration<String> e=data.keys(); e.hasMoreElements();) {
         name = e.nextElement();
         value = data.get(name);
         log.print(name + "=" + value);
      }
   }

}
