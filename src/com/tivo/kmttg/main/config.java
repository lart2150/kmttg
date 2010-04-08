
package com.tivo.kmttg.main;

import java.awt.Color;
import java.awt.Font;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Stack;


import com.tivo.kmttg.util.*;
import com.tivo.kmttg.gui.gui;

public class config {
   public static String kmttg = "kmttg v0p7k_beta";
   
   // encoding related
   public static String encProfDir = "";
   public static Hashtable<String,Hashtable<String,String>> ENCODE
      = new Hashtable<String,Hashtable<String,String>>();
   public static Stack<String> ENCODE_NAMES = new Stack<String>();
   public static int cpu_cores = 1;
   
   // 3rd party executable files
   public static String curl = "";
   public static String tivodecode = "";
   public static String outputDir = "";
   public static String mpegDir = "";
   public static String mpegCutDir = "";
   public static String encodeDir = "";
   public static String ffmpeg = "";
   public static String mencoder = "";
   public static String handbrake = "";
   public static String comskip = "";
   public static String AtomicParsley = "";
   public static String VRD = "";
   public static String t2extract = "";
   public static String ccextractor = "";

   // config preferences
   public static String MAK = "";               // MAK id for NPL downloads
   public static int RemoveTivoFile = 0;
   public static int RemoveComcutFiles = 0;
   public static int RemoveComcutFiles_mpeg = 0;
   public static int RemoveMpegFile = 0;
   public static int MaxJobs = 1;
   public static int MinChanDigits = 1;
   public static int CheckDiskSpace = 0;
   public static int LowSpaceSize = 0;
   public static int CheckBeacon = 1;
   public static int TivoWebPlusDelete = 0;
   public static int UseAdscan = 0;
   public static int VrdReview = 0;
   public static int VrdEncode = 0;
   public static int VrdReview_noCuts = 0;
   public static int VrdQsFilter = 0;
   public static int VrdDecrypt = 0;
   public static int VrdAllowMultiple = 0; // Allow multiple VRD instances at once
   public static int TSDownload = 0;
   public static int OverwriteFiles = 0;
   public static int HideProtectedFiles = 0;
   public static String comskipIni = "";
   public static String wan_http_port = "";
   public static String configIni = "";
   public static String tivoFileNameFormat = null; 
   
   // custom related
   public static String customCommand = "";

   // batch/auto related
   public static int GUI_AUTO = 0;              // Auto mode in GUI
   public static int GUI_LOOP = 0;              // Loop mode in GUI
   public static Boolean LOOP = false;          // true=>auto loop
   public static String autoIni = "";
   public static String autoLog = "";
   public static String autoHistory = "";
   public static Hashtable<String,String> KEYWORDS = new Hashtable<String,String>();
   
   // Hash to store tivo related information
   public static Hashtable<String,String> TIVOS = new Hashtable<String,String>();
    
   // GUI related
   public static Boolean GUI = false;       // true=>GUI, false=>batch/auto            
   public static String encodeName = "";    // Saves currently selected encode name
   public static gui gui;                   // Access to any GUI functions through here
   public static String gui_settings = null; // File in which to save GUI settings on exit
   public static int toolTips = 1;          // If 1 then display component toolTips
   public static int jobMonitorFullPaths = 1; // If 1 then show full paths in job monitor
   public static int toolTipsTimeout = 20;  // Set # seconds for tooltip display to timeout
   public static int FontSize = 12;

   // GUI table related
   public static Color tableBkgndDarker = new Color(235,235,235); // light grey
   public static Color tableBkgndLight = Color.white;
   public static Color tableBkgndProtected = new Color(191,156,94); // tan
   public static Color tableBkgndRecording = new Color(149, 151, 221); // light blue
   public static Color lightRed = new Color(250, 190, 190); // light red
   public static Font  tableFont = new Font("System", Font.BOLD, FontSize);
   public static int   tableColAutoSize = 1; // If 0 then don't auto size table columns
   
   // GUI free space related
   public static Hashtable<String,Float> diskSpace = new Hashtable<String,Float>();

   // misc
   public static String programDir = "";
   public static String OS = "other";
   public static String tmpDir = "/tmp";
   public static String perl = "perl";
   
   // tivo beacon listening
   //public static beacon tivo_beacon;
   public static mdns jmdns = null;
   
   // t2extract related
   public static String t2extract_args = "";
   
   // mencoder related (for Ad Cut task)
   public static String mencoder_args = "";
   
   // metadata related
   public static String metadata_files = "last";
   
   // pyTivo push related
   public static String pyTivo_config = "";
   public static String pyTivo_host = "localhost";
   public static String pyTivo_tivo = "";
   public static String pyTivo_files = "last";
   
   // download retry related
   public static int download_tries = 5;        // Number of times to retry downloads
   public static int download_retry_delay = 10; // Delay in secs between retry attempts
   
   public static Stack<String> parse() {
      debug.print("");
      String result;
      Stack<String> errors = new Stack<String>();
      defineDefaults();
      
      if (file.isFile(configIni)) {
         parseIni(configIni);
      }
      
      // Could be output dirs are configured incorrectly, so try and
      // correct them automatically
      if (! file.isDir(outputDir)) {
         log.warn("Configured outputDir does not exist, resetting to default");
         outputDir = programDir;
      }
      if (! file.isDir(TIVOS.get("FILES"))) {
         log.warn("Configured FILES dir does not exist, resetting to default");
         TIVOS.put("FILES", outputDir);
      }
      if (! file.isDir(mpegDir)) {
         log.warn("Configured mpegDir does not exist, resetting to default");
         mpegDir = outputDir;
      }
      if (! file.isDir(mpegCutDir)) {
         log.warn("Configured mpegCutDir does not exist, resetting to default");
         mpegCutDir = outputDir;
      }
      if (! file.isDir(encodeDir)) {
         log.warn("Configured encodeDir does not exist, resetting to default");
         encodeDir = outputDir;
      }
      
      // Could be the 3rd party tools are installed locally but just configured
      // wrong, so try and automatically correct them
      if ( ! file.isFile(curl) ) {
         result = getProgramDefault("curl");
         if ( file.isFile(result) )
            curl = result;
      }
      if ( ! file.isFile(tivodecode) ) {
         result = getProgramDefault("tivodecode");
         if ( file.isFile(result) )
            tivodecode = result;
      }
      if ( ! file.isFile(ffmpeg) ) {
         result = getProgramDefault("ffmpeg");
         if ( file.isFile(result) )
            ffmpeg = result;
      }
      if ( ! file.isFile(mencoder) ) {
         result = getProgramDefault("mencoder");
         if ( file.isFile(result) )
            mencoder = result;
      }
      if ( ! file.isFile(handbrake) ) {
         result = getProgramDefault("handbrake");
         if ( file.isFile(result) )
            handbrake = result;
      }
      if ( ! file.isFile(comskip) ) {
         result = getProgramDefault("comskip");
         if ( file.isFile(result) )
            comskip = result;
      }
      if ( ! file.isFile(comskipIni) ) {
         result = getProgramDefault("comskipIni");
         if ( file.isFile(result) )
            comskipIni = result;
      }
      if ( ! file.isFile(AtomicParsley) ) {
         result = getProgramDefault("AtomicParsley");
         if ( file.isFile(result) )
            AtomicParsley = result;
      }
      // Intentionally disabled for now
      /*
      if ( ! file.isFile(ccextractor) ) {
          result = getProgramDefault("ccextractor");
          if ( file.isFile(result) )
             ccextractor = result;
       }
       */

      // Parse encoding profiles
      encodeConfig.parseEncodingProfiles();
      
      // Error checking
      if (MAK.equals(""))
         errors.add("MAK not defined!");
      if ( ! file.isFile(tivodecode) )
         errors.add("tivodecode not defined!");

      if (outputDir.equals("")) {
         errors.add("Output Dir not defined!");
      } else {
         if ( ! file.isDir(outputDir) )
            errors.add("Output Dir does not exist: " + outputDir);
      }

      if ( ! mpegDir.equals("") && ! file.isDir(mpegDir) )
         errors.add("Mpeg Dir does not exist: " + mpegDir);

      if ( ! mpegCutDir.equals("") && ! file.isDir(mpegCutDir) )
         errors.add("Mpeg Cut Dir does not exist: " + mpegCutDir);

      if ( ! encodeDir.equals("") && ! file.isDir(encodeDir) )
         errors.add("Encode Dir does not exist: " + encodeDir);
      
      // Start tivo beacon listener if option enabled
      //if (CheckBeacon == 1) tivo_beacon = new beacon();
      if (CheckBeacon == 1) jmdns = new mdns();

      return errors;
   }            
   
   public static void printTivosHash() {
      String name, value;
      log.print("TIVOS:");
      for (Enumeration<String> e=TIVOS.keys(); e.hasMoreElements();) {
         name = e.nextElement();
         value = TIVOS.get(name);
         log.print(name + "=" + value);
      }
   }
   
   public static Stack<String> getTivoNames() {
      Stack<String> tivos = new Stack<String>();
      String name;
      for (Enumeration<String> e=TIVOS.keys(); e.hasMoreElements();) {
         name = e.nextElement();
         if ( ! name.matches("FILES") )
            tivos.add(name);
      }
      return tivos;     
   }
   
   public static void setTivoNames(Hashtable<String,String> h) {
      String path = TIVOS.get("FILES");
      if (path == null) path = config.programDir;
      TIVOS.clear();
      TIVOS.put("FILES", path);
      if (h.size() > 0) {
         for (Enumeration<String> e=h.keys(); e.hasMoreElements();) {
            String name = e.nextElement();
            if ( ! name.matches("FILES") ) {
               TIVOS.put(name, h.get(name));
            }
         }
      }
      
      if (GUI) {
         config.gui.SetTivos(config.TIVOS);
      }
   }

   // Add a newly detected tivos to hash (and GUI if in GUI mode)
   public static void addTivo(Hashtable<String,String> b) {
      log.warn("Adding detected tivo: " + b.get("machine"));
      TIVOS.put(b.get("machine"), b.get("ip"));
      save(configIni);
      if (config.GUI) {
         config.gui.AddTivo(b.get("machine"), b.get("ip"));
      }
   }

   private static void defineDefaults() {
      debug.print("");
      String s = File.separator;
      if (System.getProperty("os.name").toLowerCase().indexOf("windows") > -1) {
         OS = "windows";
      } else if (System.getProperty("os.name").toLowerCase().indexOf("mac") > -1) {
         OS = "mac";
      }
            
      // Define programDir based on location of jar file
      programDir = new File(
         config.class.getProtectionDomain().getCodeSource().getLocation().getPath()
      ).getParent();
      programDir = string.urlDecode(programDir);
      debug.print("programDir=" + programDir);
      
      // tmpDir
      if (OS.equals("windows")) {
         tmpDir = System.getenv("TEMP");
         if ( ! file.isDir(tmpDir) ) {
            tmpDir = System.getenv("TMP");
         }
         if ( ! file.isDir(tmpDir) ) {
            tmpDir = programDir;
         }
      }      
      
      // Try and get MAK from ~/.tivodecode_mak
      String result = getMakFromFile();
      if (result != null) MAK = result;
     
      // These files all should reside along side jar file
      configIni    = programDir + s + "config.ini";
      autoIni      = programDir + s + "auto.ini";
      autoLog      = programDir + s + "auto.log";
      autoHistory  = programDir + s + "auto.history";
      encProfDir   = programDir + s + "encode";
      
      // File to store/restore GUI settings
      gui_settings = programDir + s + ".kmttg_settings";
      if (file.isDir(System.getProperty("user.home"))) {
         // Centralize this non-critical file instead of localizing it
         gui_settings = System.getProperty("user.home") + s + ".kmttg_settings";
      }
      
      // Non-executable defaults
      tivoFileNameFormat = "[title] ([monthNum]_[mday]_[year])";
      outputDir          = programDir;
      TIVOS.put("FILES", outputDir);
      mpegDir            = outputDir;
      mpegCutDir         = outputDir;
      encodeDir          = outputDir;
      wan_http_port      = "";
      customCommand      = "";
      cpu_cores          = Runtime.getRuntime().availableProcessors();
      pyTivo_host        = "localhost";
      pyTivo_config      = "";
      pyTivo_files       = "last";
      metadata_files     = "last";
      
      // 3rd party executable defaults
      curl          = getProgramDefault("curl");
      tivodecode    = getProgramDefault("tivodecode");
      ffmpeg        = getProgramDefault("ffmpeg");
      mencoder      = getProgramDefault("mencoder");
      handbrake     = getProgramDefault("handbrake");
      comskip       = getProgramDefault("comskip");
      comskipIni    = getProgramDefault("comskipIni");
      AtomicParsley = getProgramDefault("AtomicParsley");      
      //ccextractor   = getProgramDefault("ccextractor");
   }
   
   // Return default setting for a given programName
   // For windows & Mac define expected default locations
   // For other OSs try and find program using "which"
   public static String getProgramDefault(String programName) {
      debug.print("programName=" + programName);
      String s = File.separator;
      String exe = "";
      String result;
      if (OS.equals("windows")) {
         exe = ".exe";
      }
                                          
      if (programName.equals("curl")) {
        String curl          = programDir + s + "curl"          + s + "curl"          + exe;
        if ( ! OS.equals("windows"))
           curl = "/usr/bin/curl";
        if (OS.equals("other") && ! file.isFile(curl)) {
            result = file.unixWhich("curl");
            if (result != null)
               curl = result;
         }
         return curl;
      }
      
      else if (programName.equals("tivodecode")) {
         String tivodecode    = programDir + s + "tivodecode"    + s + "tivodecode"    + exe;      
         if (OS.equals("other") && ! file.isFile(tivodecode)) {
            result = file.unixWhich("tivodecode");
            if (result != null)
               tivodecode = result;
         }
         return tivodecode;
      }
      
      else if (programName.equals("ffmpeg")) {
         String ffmpeg        = programDir + s + "ffmpeg"        + s + "ffmpeg"        + exe;
         if (OS.equals("other") && ! file.isFile(ffmpeg)) {
            result = file.unixWhich("ffmpeg");
            if (result != null)
               ffmpeg = result;
         }
         return ffmpeg;
      }
      
      else if (programName.equals("mencoder")) {
         String mencoder      = programDir + s + "mencoder"      + s + "mencoder"      + exe;
         if (OS.equals("other") && ! file.isFile(mencoder) ) {
            result = file.unixWhich("mencoder");
            if (result != null)
               mencoder = result;
         }
         return mencoder;
      }
      
      else if (programName.equals("handbrake")) {
         String handbrake     = programDir + s + "handbrake"     + s + "HandBrakeCLI"  + exe;
         if (OS.equals("other") &&  ! file.isFile(handbrake) ) {
            result = file.unixWhich("HandBrakeCLI");
            if (result != null)
               handbrake = result;
         }
         return handbrake;
      }
      
      else if (programName.equals("comskip")) {
         String comskip       = programDir + s + "comskip"       + s + "comskip"       + exe;
         if (OS.equals("other") &&  ! file.isFile(comskip) ) {
            result = file.unixWhich("comskip");
            if (result != null)
               comskip = result;
         }
         return comskip;
      }
            
      else if (programName.equals("comskipIni")) {
         String comskipIni = string.dirname(getProgramDefault("comskip")) + s + "comskip.ini";
         return comskipIni;
      }
      
      else if (programName.equals("AtomicParsley")) {
         String AtomicParsley = programDir + s + "AtomicParsley" + s + "AtomicParsley" + exe;
         if (OS.equals("other") &&  ! file.isFile(AtomicParsley) ) {
            result = file.unixWhich("AtomicParsley");
            if (result != null)
               AtomicParsley = result;
         }
         return AtomicParsley;
      }
      
      else if (programName.equals("ccextractor")) {
         String ccextractor    = programDir + s + "ccextractor"    + s + "ccextractor"    + exe;      
         if (OS.equals("other") && ! file.isFile(ccextractor)) {
            result = file.unixWhich("ccextractor");
            if (result != null)
               ccextractor = result;
         }
         return ccextractor;
      }
            
      else {
         log.error("No default defined for programName=" + programName);
         return "";
      }
      
   }
   
   private static Boolean parseIni(String config) {
      debug.print("config=" + config);
            
      try {
         BufferedReader ini = new BufferedReader(new FileReader(config));
         String line = null;
         String key = null;
         while (( line = ini.readLine()) != null) {
            // Get rid of leading and trailing white space
            line = line.replaceFirst("^\\s*(.*$)", "$1");
            line = line.replaceFirst("^(.*)\\s*$", "$1");
            if (line.length() == 0) continue; // skip empty lines
            if (line.matches("^#.+")) continue; // skip comment lines
            if (line.matches("^<.+>")) {
               key = line.replaceFirst("<", "");
               key = key.replaceFirst(">", "");
               continue;
            }
            if (key.equals("MAK")) {
               MAK = string.removeLeadingTrailingSpaces(line);
            }
            if (key.equals("TIVOS")) {
               String name, value;
               String l[] = line.split("\\s+");
               if (l[0].matches("^.*FILES")) {
                  name = l[0];
                  value = line;
                  value = value.replaceFirst("^(.+)\\s+(.+)$", "$2");
                  name = name.replaceFirst("^\\*", "");
               } else {
                  value = l[l.length-1];
                  name = "";
                  for (int i=0; i<l.length-1; i++) {
                     name += l[i] + " ";
                  }
                  name = name.substring(0,name.length()-1);
               }
               TIVOS.put(name, value);
            }
            if (key.equals("tivoFileNameFormat")) {
               tivoFileNameFormat = line;
            }
            if (key.equals("FontSize")) {
               FontSize = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("tableColAutoSize")) {
               tableColAutoSize = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("RemoveTivoFile")) {
               RemoveTivoFile = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("RemoveComcutFiles")) {
               RemoveComcutFiles = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("RemoveComcutFiles_mpeg")) {
               RemoveComcutFiles_mpeg = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("RemoveMpegFile")) {
               RemoveMpegFile = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("UseAdscan")) {
               UseAdscan = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("VrdReview")) {
               VrdReview = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("VrdReview_noCuts")) {
               VrdReview_noCuts = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("VrdQsFilter")) {
               VrdQsFilter = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("VrdDecrypt")) {
               VrdDecrypt = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("VrdEncode")) {
               VrdEncode = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("VrdAllowMultiple")) {
               VrdAllowMultiple = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("TSDownload")) {
               TSDownload = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("HideProtectedFiles")) {
               HideProtectedFiles = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("OverwriteFiles")) {
               OverwriteFiles = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("outputDir")) {
               outputDir = line;
            }
            if (key.equals("mpegDir")) {
               mpegDir = line;
            }
            if (key.equals("mpegCutDir")) {
               mpegCutDir = line;
            }
            if (key.equals("encodeDir")) {
               encodeDir = line;
            }
            if (key.equals("tivodecode")) {
               tivodecode = line;
            }
            if (key.equals("curl")) {
               curl = line;
            }
            if (key.equals("ffmpeg")) {
               ffmpeg = line;
            }
            if (key.equals("mencoder")) {
               mencoder = line;
            }
            if (key.equals("mencoder_args")) {
               mencoder_args = line;
            }
            if (key.equals("handbrake")) {
               handbrake = line;
            }
            if (key.equals("comskip")) {
               comskip = line;
            }
            if (key.equals("AtomicParsley")) {
               AtomicParsley = line;
            }
            if (key.equals("comskipIni")) {
               comskipIni = line;
            }
            if (key.equals("wan_http_port")) {
               wan_http_port = line;
            }
            if (key.equals("MaxJobs")) {
               MaxJobs = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("MinChanDigits")) {
               MinChanDigits = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("VRD")) {
               VRD = line;
            }
            if (key.equals("t2extract")) {
               t2extract = line;
            }
            if (key.equals("t2extract_args")) {
               t2extract_args = line;
            }
            if (key.equals("ccextractor")) {
                ccextractor = line;
             }
            if (key.equals("custom")) {
               customCommand = line;
            }
            if (key.equals("pyTivo_config")) {
               pyTivo_config = line;
            }
            if (key.equals("pyTivo_host")) {
               pyTivo_host = line;
            }
            if (key.equals("pyTivo_tivo")) {
               pyTivo_tivo = line;
            }
            if (key.equals("pyTivo_files")) {
               pyTivo_files = line;
            }
            if (key.equals("metadata_files")) {
               metadata_files = line;
            }
            if (key.equals("CheckDiskSpace")) {
               CheckDiskSpace = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("LowSpaceSize")) {
               LowSpaceSize = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("CheckBeacon")) {
               CheckBeacon = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("TivoWebPlusDelete")) {
               TivoWebPlusDelete = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("cpu_cores")) {
               cpu_cores = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("download_tries")) {
               download_tries = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("download_retry_delay")) {
               download_retry_delay = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("diskSpace")) {
               String[] l = line.split("=");
               if (l.length == 2) {
                  try {
                     float size = Float.parseFloat(l[1]);
                     diskSpace.put(l[0],size);
                  }
                  catch(NumberFormatException e) {
                     log.warn("Error parsing diskSpace setting");
                  }
               }
            }
         }
         ini.close();

         // Define FILES mode start dir if not configured
         if ( ! TIVOS.containsKey("FILES") ) {
            TIVOS.put("FILES", outputDir);
         }
         if ( ! file.isFile(TIVOS.get("FILES")) ) {
            TIVOS.put("FILES", outputDir);
         }
         
      }         
      catch (IOException ex) {
         log.error("Problem parsing config file: " + config);
         return false;
      }
      
      return true;

   }
   
   // Save current settings in memory to config.ini
   public static Boolean save(String config) {
      debug.print("config=" + config);
            
      try {
         BufferedWriter ofp = new BufferedWriter(new FileWriter(config));
         
         ofp.write("# kmttg config.ini file\n");
         
         ofp.write("<MAK>\n" + MAK + "\n\n");
         
         ofp.write("<TIVOS>\n");
         Stack<String> tivoNames = getTivoNames();
         if (tivoNames.size() > 0) {
            for (int i=0; i<tivoNames.size(); ++i) {
               String name = tivoNames.get(i);
               ofp.write(String.format("%-20s %-20s\n", name, TIVOS.get(name)));
            }
         }
         ofp.write(String.format("%-20s %-20s\n", "FILES", TIVOS.get("FILES")));
         ofp.write("\n");
         
         ofp.write("<FontSize>\n" + FontSize + "\n\n");
         
         ofp.write("<tableColAutoSize>\n" + tableColAutoSize + "\n\n");
         
         ofp.write("<RemoveTivoFile>\n" + RemoveTivoFile + "\n\n");
         
         ofp.write("<RemoveComcutFiles>\n" + RemoveComcutFiles + "\n\n");
         
         ofp.write("<RemoveComcutFiles_mpeg>\n" + RemoveComcutFiles_mpeg + "\n\n");
         
         ofp.write("<RemoveMpegFile>\n" + RemoveMpegFile + "\n\n");
         
         ofp.write("<UseAdscan>\n" + UseAdscan + "\n\n");
         
         ofp.write("<VrdReview>\n" + VrdReview + "\n\n");
         
         ofp.write("<VrdReview_noCuts>\n" + VrdReview_noCuts + "\n\n");
         
         ofp.write("<VrdQsFilter>\n" + VrdQsFilter + "\n\n");
         
         ofp.write("<VrdDecrypt>\n" + VrdDecrypt + "\n\n");
         
         ofp.write("<VrdEncode>\n" + VrdEncode + "\n\n");
         
         ofp.write("<VrdAllowMultiple>\n" + VrdAllowMultiple + "\n\n");
         
         ofp.write("<TSDownload>\n" + TSDownload + "\n\n");
         
         ofp.write("<HideProtectedFiles>\n" + HideProtectedFiles + "\n\n");
         
         ofp.write("<OverwriteFiles>\n" + OverwriteFiles + "\n\n");
         
         ofp.write("<tivoFileNameFormat>\n" + tivoFileNameFormat + "\n\n");
         
         ofp.write("<outputDir>\n" + outputDir + "\n\n");
         
         ofp.write("<mpegDir>\n" + mpegDir + "\n\n");
         
         ofp.write("<mpegCutDir>\n" + mpegCutDir + "\n\n");
         
         ofp.write("<encodeDir>\n" + encodeDir + "\n\n");
         
         ofp.write("<tivodecode>\n" + tivodecode + "\n\n");
         
         ofp.write("<curl>\n" + curl + "\n\n");
         
         ofp.write("<ffmpeg>\n" + ffmpeg + "\n\n");
         
         ofp.write("<mencoder>\n" + mencoder + "\n\n");
         
         ofp.write("<mencoder_args>\n" + mencoder_args + "\n\n");
         
         ofp.write("<handbrake>\n" + handbrake + "\n\n");
         
         ofp.write("<comskip>\n" + comskip + "\n\n");
         
         ofp.write("<comskipIni>\n" + comskipIni + "\n\n");
         
         ofp.write("<wan_http_port>\n" + wan_http_port + "\n\n");
         
         ofp.write("<MaxJobs>\n" + MaxJobs + "\n\n");
         
         ofp.write("<MinChanDigits>\n" + MinChanDigits + "\n\n");
         
         ofp.write("<VRD>\n" + VRD + "\n\n");
         
         ofp.write("<AtomicParsley>\n" + AtomicParsley + "\n\n");
         
         ofp.write("<t2extract>\n" + t2extract + "\n\n");
         
         ofp.write("<t2extract_args>\n" + t2extract_args + "\n\n");
                                    
         ofp.write("<ccextractor>\n" + ccextractor + "\n\n");
         
         ofp.write("<custom>\n" + customCommand + "\n\n");
         
         ofp.write("<pyTivo_config>\n" + pyTivo_config + "\n\n");
         
         ofp.write("<pyTivo_host>\n" + pyTivo_host + "\n\n");
         
         ofp.write("<pyTivo_tivo>\n" + pyTivo_tivo + "\n\n");
         
         ofp.write("<pyTivo_files>\n" + pyTivo_files + "\n\n");
         
         ofp.write("<metadata_files>\n" + metadata_files + "\n\n");
         
         ofp.write("<CheckDiskSpace>\n" + CheckDiskSpace + "\n\n");
         
         ofp.write("<LowSpaceSize>\n" + LowSpaceSize + "\n\n");
         
         ofp.write("<CheckBeacon>\n" + CheckBeacon + "\n\n");
         
         ofp.write("<TivoWebPlusDelete>\n" + TivoWebPlusDelete + "\n\n");
         
         ofp.write("<cpu_cores>\n" + cpu_cores + "\n\n");
         
         ofp.write("<download_tries>\n" + download_tries + "\n\n");
         
         ofp.write("<download_retry_delay>\n" + download_retry_delay + "\n\n");
         
         if (diskSpace.size() > 0) {
            ofp.write("<diskSpace>\n");
            for (Enumeration<String> e=diskSpace.keys(); e.hasMoreElements();) {
               String name = e.nextElement();
               ofp.write(name + "=" + diskSpace.get(name) + "\n");
            }
            ofp.write("\n");
         }
         
         ofp.close();
         
      }         
      catch (IOException ex) {
         log.error("Problem writing to config file: " + config);
         return false;
      }
      
      return true;
   }
   
   // Try and get MAK from ~/.tivodecode_mak   
   public static String getMakFromFile() {
      if (System.getProperty("user.home") != null) {
         String f = System.getProperty("user.home") +
            File.separator + ".tivodecode_mak";
         if (file.isFile(f)) {
            String mak = null;
            try {
               BufferedReader reader = new BufferedReader(new FileReader(f));
               String line = null;
               while (( line = reader.readLine()) != null) {
                  // Get rid of leading and trailing white space
                  line = line.replaceFirst("^\\s*(.*$)", "$1");
                  line = line.replaceFirst("^(.*)\\s*$", "$1");
                  if (line.length() == 0) continue; // skip empty lines
                  if (line.matches("^\\d+$"))
                     mak = line;
               }
               reader.close();
               return mak;
            }
            catch (IOException ex) {
               return null;
            }

         }
      }
      return null;
   }
 
}
