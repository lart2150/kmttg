package com.tivo.kmttg.main;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.task.NowPlaying;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class auto {
   
   // Batch mode processing (no GUI)
   public static void startBatchMode() {
      debug.print("");
      
      log.print("\nSTARTING BATCH MODE");
      
      // Auto download batch mode
      Stack<String> errors = config.parse();
      if(errors.size() > 0) {
         log.error(errors);
         exitAuto(1);
      }
      
      // Enter master check Tivos loop
      Boolean GO = true;
      while (GO) {         
         // Parse auto.ini file (this in main loop in case auto.ini file is updated)
         if ( ! autoConfig.parseAuto(config.autoIni) ) {
            log.error("Auto Transfers config has errors or is not setup");
            exitAuto(1);
         }
         if ( getTitleEntries().isEmpty() && getKeywordsEntries().isEmpty() ) {
            log.error("No keywords defined in " + config.autoIni + "... aborting");
            exitAuto(0);
         }

         // Queue up now playing list downloads from all Tivos
         if ( config.getTivoNames().isEmpty() ) {
            log.error("No tivos defined in config");
            exitAuto(1);
         }
         for (int i=0; i < config.getTivoNames().size(); i++) {
            String tivoName = config.getTivoNames().get(i);
            NowPlaying.submitJob(tivoName);
         }
         
         // Process all queued up jobs
         while( ! jobMonitor.JOBS.isEmpty() ) {
            try {
               Thread.sleep(2000);
            } catch (InterruptedException e) {
               log.error(e.toString());
               exitAuto(1);
            }
            jobMonitor.monitor(null, null);
         }
         
         // Exit now if in single loop mode
         if ( ! config.LOOP  ) exitAuto(0);
         
         // Exit it autoLog file doesn't exist
         if ( ! file.isFile(config.autoLog) ) exitAuto(1);

         // Sleep for a period of time before checking Tivos again
         log.print("\nSLEEPING " + autoConfig.CHECK_TIVOS_INTERVAL + " mins ...");
         try {
            Thread.sleep(autoConfig.CHECK_TIVOS_INTERVAL*60*1000);
         } catch (InterruptedException e) {
            log.error(e.toString());
            exitAuto(1);
         }
      }
   }
   
   private static void exitAuto(int num) {
      debug.print("num=" + num);
      log.print("\nEXITING BATCH MODE");
      System.exit(num);
   }
   
   // Match title & keywords against an entry 
   public static void keywordSearch(Hashtable<String,String> entry) {
      debug.print("entry=" + entry);
      
      // Title matching
      String title, keyword;
      Stack<autoEntry> auto_entries = getTitleEntries();
      autoEntry auto;
      for (int i=0; i<auto_entries.size(); i++) {
         auto = auto_entries.get(i);
         keyword = auto.keyword;
         if (entry.containsKey("titleOnly")) {
            title = entry.get("titleOnly").toLowerCase();
            debug.print("keywordSearch::matching title '" + keyword + "' in '" + title + "'");
            if ( title.matches(keyword) ) {
               // Match found, so queue up relevant job actions
               log.print("Title keyword match: '" + keyword + "' found in '" + title + "'");
               if (autoConfig.dryrun == 1) {
                  log.print("(dry run mode => will not download)");
               } else {
                  keywordMatchJobInit(entry, auto);
               }
               return;
            }
         }
      }
      
      // Keyword matching
      // Match against title & description
      auto_entries = getKeywordsEntries();
      for (int i=0; i<auto_entries.size(); i++) {
         auto = auto_entries.get(i);
         String keywordsList = auto.keywords.get(0);
         for (int j=1; j<auto.keywords.size(); j++) {
            keywordsList += "|" + auto.keywords.get(j);
         }
         // Divide up into and, or, not
         Stack<String> and = new Stack<String>();
         Stack<String> or  = new Stack<String>();
         Stack<String> not = new Stack<String>();
         for (int j=0; j<auto.keywords.size(); j++) {
            keyword = auto.keywords.get(j);
            keyword = keyword.replaceFirst("^\\s+", "");
            keyword = keyword.replaceFirst("\\s+$", "");
            keyword = keyword.toLowerCase();
            if ( keyword.matches("^-") ) {
               keyword = keyword.replaceFirst("^-", "");
               not.add(keyword);
            }
            else if ( keyword.matches("\\(") ) {
               keyword = keyword.replaceAll("\\(", "");
               keyword = keyword.replaceAll("\\)", "");
               or.add(keyword);
            }
            else {
               and.add(keyword);
            }
         }
         
         // Construct all text to be matched against
         String text = "";
         if (entry.containsKey("titleOnly"))
            text = entry.get("titleOnly");
         if (entry.containsKey("episodeTitle"))
            text += " " + entry.get("episodeTitle");
         if (entry.containsKey("description"))
            text += " " + entry.get("description");
         text = text.toLowerCase();
         // Remove punctuation for matching purposes
         text = text.replaceAll(",", "");
         text = text.replaceAll(";", "");
         text = text.replaceAll(":", "");
         text = text.replaceAll("\\.", "");
         text = text.replaceAll("\\!", "");
         text = text.replaceAll("\\?", "");
         text = text.replaceAll("\\(", "");
         text = text.replaceAll("\\)", "");
         
         debug.print("keywordSearch::matching keywords '" + keywordsList +"' in '" + text + "'");
         debug.print("keywordSearch::and=" + and);
         debug.print("keywordSearch:: or=" + or);
         debug.print("keywordSearch::not=" + not);
         // Start with match assumption
         Boolean match = true;

         // All 'and' strings must be present
         String andString;
         for (int j=0; j<and.size(); ++j) {
            andString = and.get(j);
            if ( ! text.contains(andString) ) {
               match = false;
               debug.print("   'and' keyword not found: '" + andString + "'");
            }
         }
         if (! match) {
            debug.print("keywordSearch::no match due to 'and'");
            continue;
         }
         
         // If anything matches not entries that's a no match
         String notString;
         for (int j=0; j<not.size(); ++j) {
            notString = not.get(j);
            if ( text.contains(notString) ) {
               match = false;
               debug.print("   'not' keyword found: '" + notString + "'");
            }
         }
         if (! match) {
            debug.print("keywordSearch::no match due to 'not'");
            continue;
         }
                  
         // At least 1 of the or strings should match
         if ( ! or.isEmpty() ) match = false;
         String orString;
         for (int j=0; j<or.size(); ++j) {
            orString = or.get(j);
            if ( text.contains(orString) ) {
               match = true;
               debug.print("   'or' keyword found: '" + orString + "'");
            }
         }
         
         if( match ) {
            // Match found, so queue up relevant job actions
            debug.print("keywordSearch::KEYWORDS MATCH");
            log.print("keywords match: '" + keywordsList + "' matches '" + text + "'");
            if (autoConfig.dryrun == 1) {
               log.print("(dry run mode => will not download)");
            } else {
               keywordMatchJobInit(entry, auto);
            }
            return;
         } else {
            debug.print("keywordSearch::no match is final determination");
         }
      }
   }
   
   // Return auto title entries
   public static Stack<autoEntry> getTitleEntries() {
      Stack<autoEntry> entries = new Stack<autoEntry>();
      if (autoConfig.KEYWORDS.size() > 0) {
         for (int i=0; i<autoConfig.KEYWORDS.size(); i++) {
            if (autoConfig.KEYWORDS.get(i).type.matches("title")) {
               entries.add(autoConfig.KEYWORDS.get(i));
            }
         }
      } else {
         log.error("No auto keywords setup");
      }
      return entries;
   }

   // Return auto keywords entries
   public static Stack<autoEntry> getKeywordsEntries() {
      Stack<autoEntry> entries = new Stack<autoEntry>();
      for (int i=0; i<autoConfig.KEYWORDS.size(); i++) {
         if (autoConfig.KEYWORDS.get(i).type.matches("keywords")) {
            entries.add(autoConfig.KEYWORDS.get(i));
         }
      }
      return entries;
   }
      
   private static Boolean grep(String string, Stack<String> stack) {
      for (int i=0; i<stack.size(); ++i) {
         if (stack.get(i).contains(string))
            return true;
      }
      return false;
   }
   
   private static void keywordMatchJobInit(Hashtable<String,String> entry, autoEntry auto) {
      debug.print("entry=" + entry + " auto=" + auto);
      
      // Need to check ProgramId to see if we already have processed this previously
      if (entry.containsKey("ProgramId")) {
         if ( ! grep(entry.get("ProgramId"), autoConfig.ignoreHistory) ) {
            if ( keywordMatchHistory(entry.get("ProgramId"), config.autoHistory) ) {
               log.print("(ProgramId=" + entry.get("ProgramId") + " already processed => will not download)");
               return;
            }
         }
      }
      
      Hashtable<String,Object> h = new Hashtable<String,Object>();
      h.put("tivoName", entry.get("tivoName"));
      h.put("mode", "Download");
      if (entry == null) return;
      h.put("entry",        entry);   
      h.put("metadata",     (Boolean)(auto.metadata == 1));
      h.put("metadataTivo", false);
      h.put("decrypt",      (Boolean)(auto.decrypt  == 1));
      h.put("qsfix",        (Boolean)(auto.qsfix    == 1));
      h.put("comskip",      (Boolean)(auto.comskip  == 1));
      h.put("comcut",       (Boolean)(auto.comcut   == 1));
      h.put("captions",     (Boolean)(auto.captions == 1));
      h.put("encode",       (Boolean)(auto.encode   == 1));
      h.put("custom",       (Boolean)(auto.custom   == 1));
      if (auto.encode_name != null)
         h.put("encodeName",   auto.encode_name);

      jobMonitor.LaunchJobs(h);
   }
   
   // Return true if ProgramId exists in autoHistory file
   private static Boolean keywordMatchHistory(String ProgramId, String historyFile) {
      try {
         BufferedReader history = new BufferedReader(new FileReader(historyFile));
         String line = null;
         while (( line = history.readLine()) != null) {
            if (line.contains(ProgramId)) {
               history.close();
               return true;
            }
         }
         history.close();         
      }         
      catch (IOException ex) {
         log.warn("No history file: " + historyFile);
         return false;
      }
      return false;
   }
   
   // Append a ProgramId entry to the autoHistory file based on job data
   public static int AddHistoryEntry(jobData job) {
      debug.print("job=" + job);
      // Don't add if entry already exists
      if (job.ProgramId == null) {
         return 0;
      } else {
         if (keywordMatchHistory(job.ProgramId, config.autoHistory))
            return 2;
      }

      // Append entry to autoHistory file
      try {
         BufferedWriter ofp = new BufferedWriter(new FileWriter(config.autoHistory, true));
         ofp.write(job.ProgramId);
         if (job.title != null)
            ofp.write(" " + job.title);
         ofp.write("\n");
         ofp.close();
         return 1;
      } catch (IOException ex) {
         log.error(ex.toString());
         return 0;
      }
   }
   
   // Append a ProgramId entry to the autoHistory file based on NPL entry
   public static int AddHistoryEntry(Hashtable<String,String> job) {
      debug.print("job=" + job);
      // Don't add if entry already exists
      if ( ! job.containsKey("ProgramId") ) {
         return 0;
      } else {
         if (keywordMatchHistory(job.get("ProgramId"), config.autoHistory))
            return 2;
      }

      // Append entry to autoHistory file
      try {
         BufferedWriter ofp = new BufferedWriter(new FileWriter(config.autoHistory, true));
         ofp.write(job.get("ProgramId"));
         if (job.containsKey("title"))
            ofp.write(" " + job.get("title"));
         ofp.write("\n");
         ofp.close();
         return 1;
      } catch (IOException ex) {
         log.error(ex.toString());
         return 0;
      }
   }
   
   // Add a given title directly to autoIni file
   public static Boolean autoAddTitleEntryToFile(String title) {
      if ( ! file.isFile(config.autoIni) ) {
         autoConfig.parseAuto(config.autoIni);
      }
      title = title.toLowerCase();
      // Check autoIni file to see if entry already exists
      Boolean exists = false;
      try {
         BufferedReader ini = new BufferedReader(new FileReader(config.autoIni));
         String line = null;
         while (( line = ini.readLine()) != null) {
            if (line.contains("<title>")) {
               line = string.removeLeadingTrailingSpaces(ini.readLine());
               if (line.equals(title)) {
                  exists = true;
                  break;
               }
            }
         }
         ini.close();         
      }         
      catch (IOException ex) {
         log.warn("auto config file not setup: " + config.autoIni);
         return false;
      }

      if (exists) {
         log.print("Entry already added in auto config file: " + config.autoIni);
      } else {
         try {
            BufferedWriter ofp = new BufferedWriter(new FileWriter(config.autoIni, true));
            ofp.write("<title>\n");
            ofp.write(title + "\n");
            // Use currently defined options in main GUI as default settings
            ofp.write("<options>\n");
            ofp.write("metadata "    + config.gui.metadata_setting() + "\n");
            ofp.write("decrypt "     + config.gui.decrypt_setting()  + "\n");
            ofp.write("qsfix "       + config.gui.qsfix_setting()    + "\n");
            ofp.write("comskip "     + config.gui.comskip_setting()  + "\n");
            ofp.write("comcut "      + config.gui.comcut_setting()   + "\n");
            ofp.write("captions "    + config.gui.captions_setting() + "\n");
            ofp.write("encode "      + config.gui.encode_setting()   + "\n");
            ofp.write("encode_name " + config.encodeName             + "\n");
            ofp.write("custom "      + config.gui.custom_setting()   + "\n");
            ofp.write("\n");
            ofp.close();
            log.warn("Added title entry '" + title + "' to " + config.autoIni);
            log.warn("(Use Auto Transfers->Configure to modify tasks to perform)");
         } catch (IOException ex) {
            log.error("Cannot write to auto config file: " + config.autoIni);
            log.error(ex.toString());
            return false;
         }         
      }
      return true;
   }
   
   // This will check if kmttg background job is running or not on
   // unix flavors using "ps -ef" command to get running processes
   public static void unixIsRunningAuto() {
      backgroundProcess process = new backgroundProcess();
      Stack<String> command = new Stack<String>();
      command.add("ps");
      command.add("-ef");
      if (process.run(command)) {
         int exit_code = -1;
         while (exit_code == -1) {
            // Wait for job to finish
            exit_code = process.exitStatus();
         }
         if (exit_code == 0) {
            String running = "";
            Stack<String> result = process.getStdout();
            for (int i=0; i<result.size(); ++i) {
               if ( result.get(i).matches("^.*kmttg\\.jar.+-a.*$")) {
                  running = result.get(i);
               }
            }
            if (running.length() > 0) {
               log.warn("Process running: " + running);
               return;
            }
            else {
               log.warn("No background process running");
            }
         } else {
            log.error("Failed to run command: '" + process.toString() + "'");
            log.error(process.getStderr());
         }
      }
   }
   
   // This will check if kmttg background job is running or not on
   // unix flavors using "ps -ef" command to get running processes
   // and then killing job if match found
   public static void unixKillRunningAuto() {
      backgroundProcess process = new backgroundProcess();
      Stack<String> command = new Stack<String>();
      command.add("ps");
      command.add("-ef");
      if (process.run(command)) {
         int exit_code = -1;
         while (exit_code == -1) {
            // Wait for job to finish
            exit_code = process.exitStatus();
         }
         if (exit_code == 0) {
            String running = "";
            Stack<String> result = process.getStdout();
            for (int i=0; i<result.size(); ++i) {
               if ( result.get(i).matches("^.*kmttg\\.jar.+-a.*$")) {
                  running = result.get(i);
                  String[] l = running.split("\\s+");
                  if (l.length > 1) {
                     command.clear();
                     command.add("kill");
                     command.add("-9");
                     command.add(l[1]);
                     if (process.run(command)) {
                        while (exit_code == -1) {
                           // Wait for job to finish
                           exit_code = process.exitStatus();
                        }
                        if (exit_code == 0) {
                           log.warn("Process id killed: " + l[1]);
                        }                   
                     }
                  }
               }
            }
            if (running.length() == 0) {
               log.warn("No background process running");
            }
         } else {
            log.error("Command failed: " + process.toString());
         }
      }
   }
   
   // Windows only: Queries kmttg service using "sc query kmttg"
   public static String serviceStatus() {
      Stack<String> command = new Stack<String>();
      command.add("cmd");
      command.add("/c");
      command.add("sc");
      command.add("query");
      command.add("kmttg");
      backgroundProcess process = new backgroundProcess();
      if ( process.run(command) ) {
         process.Wait();
         Stack<String> result = process.getStdout();
         if (result.size() > 0) {
            Boolean created = false;
            String status = "undetermined";
            for (int i=0; i<result.size(); ++i) {
               if (result.get(i).matches("^SERVICE_NAME.+$"))
                  created = true;
               if (result.get(i).matches("^\\s+STATE.+$")) {
                  status = result.get(i);
                  String[] l = status.split("\\s+");
                  status = l[l.length-1];
               }
            }
            if (created) {
               return "kmttg service is installed: STATUS=" + status;
            } else {
               return "kmttg service has not been installed";
            }
         }
      } else {
         log.error("Command failed: " + process.toString());
         log.error(process.getStderr());
      }
      return null;
   }
   
   // Windows only: Starts kmttg service using "install-kmttg-service.bat" script
   public static Boolean serviceCreate() {
      Stack<String> command = new Stack<String>();
      String script = config.programDir + "\\service\\win32\\install-kmttg-service.bat";
      if (! file.isFile(script) ) {
         script = config.programDir + "\\release\\service\\win32\\install-kmttg-service.bat";
      }
      command.add("cmd");
      command.add("/c");
      command.add(script);
      backgroundProcess process = new backgroundProcess();
      if ( process.run(command) ) {
         process.Wait();
         Stack<String> result = process.getStdout();
         if (result.size() > 0) {
            Boolean good = false;
            // Look for "kmttg installed"
            for (int i=0; i<result.size(); ++i) {
               if (result.get(i).matches("^.+kmttg installed.+$")) {
                  good = true;
               }
            }
            if (good) {
               log.warn("kmttg service installed successfully");
               return true;
            } else {
               log.error("There was a problem installing kmttg service");
               log.error(process.getStdout());
            }
         } else {
            log.error("Problem running command: " + process.toString());
            log.error(process.getStderr());
         }
      } else {
         log.error("Command failed: " + process.toString());
         log.error(process.getStderr());
      }
      return false;
   }      
   // Windows only: Starts kmttg service using "sc start kmttg"
   public static Boolean serviceStart() {
      Stack<String> command = new Stack<String>();
      command.add("cmd");
      command.add("/c");
      command.add("sc");
      command.add("start");
      command.add("kmttg");
      backgroundProcess process = new backgroundProcess();
      if ( process.run(command) ) {
         process.Wait();
         Stack<String> result = process.getStdout();
         if (result.size() > 0) {
            // Look for FAILED
            for (int i=0; i<result.size(); ++i) {
               if (result.get(i).matches("^.+FAILED.+$")) {
                  log.error(result);
                  return false;
               }
            }
            // Seemed to work so sleep for a couple of seconds and print status
            try {
               Thread.sleep(2000);
               log.warn(serviceStatus());
               return true;
            } catch (InterruptedException e) {
               log.error(e.getMessage());
               return false;
            }               
         }
      } else {
         log.error("Command failed: " + process.toString());
         log.error(process.getStderr());
      }
      return false;
   }
   
   // Windows only: Stops kmttg service using "sc stop kmttg"
   public static Boolean serviceStop() {
      Stack<String> command = new Stack<String>();
      command.add("cmd");
      command.add("/c");
      command.add("sc");
      command.add("stop");
      command.add("kmttg");
      backgroundProcess process = new backgroundProcess();
      if ( process.run(command) ) {
         process.Wait();
         Stack<String> result = process.getStdout();
         if (result.size() > 0) {
            // Look for FAILED
            for (int i=0; i<result.size(); ++i) {
               if (result.get(i).matches("^.+FAILED.+$")) {
                  log.error(result);
                  return false;
               }
            }
            // Seemed to work so sleep for a couple of seconds and print status
            try {
               Thread.sleep(2000);
               log.warn(serviceStatus());
               return true;
            } catch (InterruptedException e) {
               log.error(e.getMessage());
               return false;
            }               
         }
      } else {
         log.error("Command failed: " + process.toString());
         log.error(process.getStderr());
      }
      return false;
   }
   
   // Windows only: Deletes kmttg service using "sc delete kmttg"
   public static Boolean serviceDelete() {
      Stack<String> command = new Stack<String>();
      command.add("cmd");
      command.add("/c");
      command.add("sc");
      command.add("delete");
      command.add("kmttg");
      backgroundProcess process = new backgroundProcess();
      if ( process.run(command) ) {
         process.Wait();
         Stack<String> result = process.getStdout();
         if (result.size() > 0) {
            // Look for SUCCESS
            for (int i=0; i<result.size(); ++i) {
               if (result.get(i).matches("^.+SUCCESS.*$")) {
                  log.warn("Successfully removed kmttg service");
                  return true;
               }
            }
            // Did not seem to work
            log.error(result);
            return false;
         }
      } else {
         log.error("Command failed: " + process.toString());
         log.error(process.getStderr());
      }
      return false;
   }

}
