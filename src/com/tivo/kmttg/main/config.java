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
package com.tivo.kmttg.main;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.Stack;

import javafx.scene.paint.Color;

import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.*;
import com.tivo.kmttg.gui.gui;
import com.tivo.kmttg.httpserver.kmttgServer;

public class config {
   public static String kmttg = "kmttg v2.4i";
   
   // encoding related
   public static String encProfDir = "";
   public static Hashtable<String,Hashtable<String,String>> ENCODE
      = new Hashtable<String,Hashtable<String,String>>();
   public static Stack<String> ENCODE_NAMES = new Stack<String>();
   public static int cpu_cores = 1;
   
   // 3rd party executable files
   public static String tivodecode = "";
   public static String dsd = "";
   public static String outputDir = "";
   public static String mpegDir = "";
   public static String qsfixDir = "";
   public static String mpegCutDir = "";
   public static String encodeDir = "";
   public static String ffmpeg = "";
   public static String mediainfo = "";
   public static String mencoder = "";
   public static String handbrake = "";
   public static String comskip = "";
   public static String AtomicParsley = "";
   public static String t2extract = "";
   public static String ccextractor = "";

   // config preferences
   public static String MAK = "";               // MAK id for NPL downloads
   public static int RemoveTivoFile = 0;
   public static int RemoveComcutFiles = 0;
   public static int RemoveComcutFiles_mpeg = 0;
   public static int RemoveMpegFile = 0;
   public static int QSFixBackupMpegFile = 0;
   public static int MaxJobs = 1;
   public static int MinChanDigits = 1;
   public static int CheckDiskSpace = 0;
   public static int LowSpaceSize = 0;
   public static int CheckBeacon = 1;
   public static int UseOldBeacon = 0;
   public static int TivoWebPlusDelete = 0;
   public static int rpcDelete = 0;
   public static int rpcOld = 0;
   public static int UseAdscan = 0;
   public static int VrdReview = 0;
   public static int comskip_review = 0;
   public static int VRD = 0;
   public static String VRDexe = "";
   public static int VrdEncode = 0;
   public static int VrdReview_noCuts = 0;
   public static int VrdQsFilter = 0;
   public static int VrdDecrypt = 0;
   public static int DsdDecrypt = 0;
   public static int tivolibreDecrypt = 1; // If 1 decrypt with tivolibre
   public static int tivolibreCompat = 0; // DirectShow compatibility mode
   public static int VrdAllowMultiple = 0; // Allow multiple VRD instances at once
   public static int VrdCombineCutEncode = 0; // Combine VRD Ad Cut and encode
   public static int VrdQsfixMpeg2ps = 0; // If set force VRD QS Fix to output mpeg2 program stream
   public static int VrdOneAtATime = 0; // If set only allow 1 VRD job at a time
   public static int TSDownload = 1;
   public static int OverwriteFiles = 0; // Don't overwrite existing files by default
   public static int DeleteFailedDownloads = 1; // Delete failed download files by default
   public static int HideProtectedFiles = 0;
   public static int TiVoSort = 0;
   public static int combine_download_decrypt = 0; // Combine download and decrypt if possible
   public static int single_download = 0;  // Allow only one download at a time if enabled
   public static int npl_when_started = 0; // Start NPL jobs when kmttg GUI starts
   public static int showHistoryInTable = 0; // If 1 then highlight table entries matching auto.history
   public static int rpcnpl = 1; // Use RPC to obtain NPL when possible
   public static boolean persistQueue = false;	// Save job queue between sessions

   public static String comskipIni = "";
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
   public static int autoLogSizeMB = 10;        // Default log size of 10MB
   public static String autoHistory = "";
   public static Hashtable<String,String> KEYWORDS = new Hashtable<String,String>();
   
   // Hash to store tivo related information
   public static LinkedHashMap<String,String> TIVOS = new LinkedHashMap<String,String>();
   public static Hashtable<String,String> TSN = new Hashtable<String,String>();
   public static Hashtable<String,String> WAN = new Hashtable<String,String>();
   // If > 0 limit # npl fetches to this num
   public static Hashtable<String,String> limit_npl_fetches = new Hashtable<String,String>();
   public static Hashtable<String,String> enableRpc = new Hashtable<String,String>();
    
   // GUI related
   public static Boolean GUIMODE = false;   // true=>GUI, false=>batch/auto            
   public static String encodeName = "";    // Saves currently selected encode name
   public static String encodeName_orig = "";
   public static gui gui;                   // Access to any GUI functions through here
   public static String gui_settings = null; // File in which to save GUI settings on exit
   public static int toolTips = 1;          // If 1 then display component toolTips
   public static int jobMonitorFullPaths = 1; // If 1 then show full paths in job monitor
   public static int toolTipsDelay = 2; // Set # seconds for tooltip to display
   public static int toolTipsTimeout = 20;  // Set # seconds for tooltip display to timeout
   public static int FontSize = 10;
   public static String lookAndFeel = "default";
   public static Boolean resumeDownloads = false;
   public static Hashtable<String,String> partners = new Hashtable<String,String>();
   
   // Slingbox related
   public static int slingBox = 0;         // If 1 then display Slingbox tab
   public static String slingBox_perl = null;
   public static String slingBox_dir = null;
   public static String slingBox_ip = "";
   public static String slingBox_port = "5201";
   public static String slingBox_pass = "";
   public static String slingBox_res = "1920x1080";
   public static String slingBox_vbw = "4000";
   public static String slingBox_type = "Slingbox 350/500";
   public static String slingBox_container = "mpegts";

   // GUI table related
   public static Color tableBkgndDarker = Color.rgb(235,235,235); // light grey
   public static Color tableBkgndLight = Color.WHITE;
   public static Color tableBkgndProtected = Color.rgb(191,156,94); // tan
   public static Color tableBkgndRecording = Color.rgb(149, 151, 221); // light blue
   public static Color tableBkgndInHistory = Color.rgb(250, 252, 164); // light yellow
   public static Color lightRed = Color.rgb(250, 190, 190); // light red
   public static int   tableColAutoSize = 1; // If 0 then don't auto size table columns
   public static String tooltipBG = "#fff7c8";
   
   // GUI free space related
   public static Hashtable<String,Float> diskSpace = new Hashtable<String,Float>();

   // misc
   public static String programDir = "";
   public static String cssDir = "";
   public static String cssFile = "default.css";
   public static String OS = "other";
   public static String tmpDir = "/tmp";
   public static String perl = "perl";
   
   // tivo beacon listening
   public static beacon tivo_beacon = null;
   public static mdns jmdns = null;
   
   // t2extract related
   public static String t2extract_args = "";
   
   // metadata related
   public static String metadata_files = "last";
   public static String metadata_entries = "";
   
   // pyTivo push related
   /*public static String pyTivo_config = null;
   public static String pyTivo_host = "localhost";
   public static String pyTivo_tivo = "";
   public static String pyTivo_files = "last";
   public static String pyTivo_port = "9032";
   public static String pyTivo_mind = "mind.tivo.com:8181";*/
   
   // web query related
   public static String web_query = "http://www.imdb.com/find?s=all&q=";
   public static String web_browser = "";
   
   // download related
   public static int download_delay = 10;       // Delay in secs to apply to each download attempt
   public static int download_tries = 5;        // Number of times to retry downloads
   public static int download_retry_delay = 10; // Delay in secs between retry attempts
   public static int download_time_estimate = 0; // Show estimated remaining time for downloads if enabled
   public static int download_check_length = 0;  // Check download length vs expected
   public static int download_check_tolerance = 200; // Download length mismatch tolerance (secs)
   
   // autotune related
   public static Hashtable<String,Hashtable<String,String>> autotune = null;
   
   // rpc remote related
   private static Hashtable<String,String> bodyId = null;
   public static String middlemind_host = "middlemind.tivo.com";
   public static int middlemind_port = 443;
   private static String tivo_username = "";
   private static String tivo_password = "";
   
   // httpserver related
   public static int httpserver_enable = 0;
   public static int httpserver_port = 8181;
   public static int httpserver_ffmpeg_wait = 20;
   public static kmttgServer httpserver = null;
   public static String httpserver_home = null;
   public static String httpserver_cache = null;
   public static String httpserver_cache_relative = null;
   public static int httpserver_share_filter = 0;
   public static LinkedHashMap<String,String> httpserver_shares = new LinkedHashMap<String,String>();
   
   // autoskip related
   public static int autoskip_enabled = 1;
   public static int autoskip_import = 1;
   public static int autoskip_cutonly = 0;
   public static int autoskip_prune = 0;
   public static int autoskip_jumpToEnd = 0;
   public static int autoskip_padding_start = 0; // NOTE: time is stored in msecs
   public static int autoskip_padding_stop = 0; // NOTE: time is stored in msecs
   public static int autoskip_batch_standby = 0;
   public static int autoskip_indicate_skip = 0;
   public static Hashtable<String,Boolean> autoskip_ServiceItems = new Hashtable<String,Boolean>();
   public static Boolean visualDetect_running = false;
   
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
      if (! file.isDir(qsfixDir)) {
         log.warn("Configured qsfixDir does not exist, resetting to default");
         qsfixDir = mpegDir;
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
      if ( ! file.isFile(tivodecode) ) {
         result = getProgramDefault("tivodecode");
         if ( file.isFile(result) )
            tivodecode = result;
      }
      if ( ! file.isFile(dsd) ) {
         result = getProgramDefault("dsd");
         if ( file.isFile(result) )
            dsd = result;
      }
      if ( ! file.isFile(ffmpeg) ) {
         result = getProgramDefault("ffmpeg");
         if ( file.isFile(result) )
            ffmpeg = result;
      }
      if ( ! file.isFile(mediainfo) ) {
         result = getProgramDefault("mediainfo");
         if ( file.isFile(result) )
            mediainfo = result;
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
      if ( ! file.isFile(ccextractor) ) {
          result = getProgramDefault("ccextractor");
          if ( file.isFile(result) )
             ccextractor = result;
       }
      if ( ! file.isFile(t2extract) ) {
         result = getProgramDefault("ccextractor");
         if ( file.isFile(result) )
            t2extract = result;
      }

      // Parse encoding profiles
      encodeConfig.parseEncodingProfiles();
      
      // Parse pyTivo config if specified
      /*if (file.isFile(pyTivo_config))
         pyTivo.parsePyTivoConf(pyTivo_config);*/
      
      // Error checking
      if (MAK.equals(""))
         errors.add("MAK not defined!");
      //if ( ! file.isFile(tivodecode) )
      //   errors.add("tivodecode not defined!");

      if (outputDir.equals("")) {
         errors.add("Output Dir not defined!");
      } else {
         if ( ! file.isDir(outputDir) )
            errors.add("Output Dir does not exist: " + outputDir);
      }

      if ( ! mpegDir.equals("") && ! file.isDir(mpegDir) )
         errors.add("Mpeg Dir does not exist: " + mpegDir);

      if ( ! qsfixDir.equals("") && ! file.isDir(qsfixDir) )
         errors.add("QS Fix Dir does not exist: " + qsfixDir);

      if ( ! mpegCutDir.equals("") && ! file.isDir(mpegCutDir) )
         errors.add("Mpeg Cut Dir does not exist: " + mpegCutDir);

      if ( ! encodeDir.equals("") && ! file.isDir(encodeDir) )
         errors.add("Encode Dir does not exist: " + encodeDir);
      
      // Start tivo beacon listener if option enabled
      if (CheckBeacon == 1) {
         if (UseOldBeacon == 0)
            jmdns = new mdns();
         else
            tivo_beacon = new beacon();
      }

      return errors;
   }            
   
   public static void printTivosHash() {
      String value;
      log.print("TIVOS:");
      for (String name : TIVOS.keySet()) {
         value = TIVOS.get(name);
         log.print(name + "=" + value);
      }
   }
   
   public static Stack<String> getTivoNames() {
      Stack<String> tivos = new Stack<String>();
      for (String name : TIVOS.keySet()) {
         if ( ! name.matches("FILES") )
            tivos.add(name);
      }
      return tivos;     
   }
   
   public static void setTivoNames(LinkedHashMap<String,String> h) {
      String path = TIVOS.get("FILES");
      if (path == null) path = config.programDir;
      TIVOS.clear();
      TIVOS.put("FILES", path);
      if (h.size() > 0) {
         for (String name : h.keySet()) {
            if ( ! name.matches("FILES") && ! name.matches("Remote") ) {
               TIVOS.put(name, h.get(name));
            }
         }
      }
      
      if (GUIMODE) {
         config.gui.SetTivos(config.TIVOS);
      }
   }
   
   public static Stack<String> getNplTivoNames() {
      Stack<String> tivos = new Stack<String>();
      for (String name : TIVOS.keySet()) {
         if ( ! name.matches("FILES") && nplCapable(name) )
            tivos.add(name);
      }
      return tivos;     
   }

   // Add a newly detected tivos to hash (and GUI if in GUI mode)
   public static void addTivo(Hashtable<String,String> b) {
      log.warn("Adding detected tivo: " + b.get("machine"));
      TIVOS.put(b.get("machine"), b.get("ip"));
      if (b.containsKey("identity"))
         setTsn(b.get("machine"), b.get("identity"));
      save();
      if (GUIMODE) {
         if (nplCapable(b.get("machine")))
            gui.AddTivo(b.get("machine"), b.get("ip"));
      }
   }
   
   public static String getWanSetting(String tivoName, String setting) {
      String key = "wan_" + tivoName + "_" + setting;
      if (WAN.containsKey(key))
         return WAN.get(key);
      else
         return null;
   }
   
   public static void setWanSetting(String tivoName, String setting, String value) {
      String key = "wan_" + tivoName + "_" + setting;
      if (value.length() > 0) {      
         WAN.put(key, value);
      } else {
         if (WAN.containsKey(key)) {
            WAN.remove(key);
         }
      }
   }
   
   public static String getTsn(String tivoName) {
      String tsn = null;
      if (TSN.containsKey(tivoName))
         tsn = TSN.get(tivoName);
      return tsn;
   }
   
   public static String getTiVoFromTsn(String tsn) {
      Set<String> set = TSN.keySet();
      Iterator<String> itr = set.iterator();
      while(itr.hasNext()) {
         String tivoName = itr.next();
         if (TSN.get(tivoName).equals(tsn))
            return tivoName;
      }
      return null;
   }
   
   public static void setTsn(String tivoName, String tsn) {
      log.warn("Updating TSN for TiVo: " + tivoName);
      TSN.put(tivoName, tsn);
   }
   
   public static Boolean nplCapable(String tivoName) {
      Boolean capable = true;
      String tsn = getTsn(tivoName);
      if (tsn != null && tsn.startsWith("A")) {
         // TiVo Mini is not NPL capable (AE2, A93)
         capable = false;
      }
      return capable;
   }
   
   // Get configured setting in limit_npl_fetches hash for given tivoName
   public static int getLimitNplSetting(String tivoName) {
      if (limit_npl_fetches.containsKey(tivoName)) {
         String setting = limit_npl_fetches.get(tivoName);
         if (setting.length() == 0)
            return 0;
         Integer i;
         try {
            i = Integer.valueOf(setting);
         }
         catch (Exception e) {
            i = 0;
         }
         if (i == null)
            i = 0;
         return i;
      }
      else
         return 0;
   }
   
   // Set configured setting in limit_npl_fetches hash for given tivoName
   // NOTE: identifier = limit_npl_tivoName
   public static void setLimitNplSetting(String identifier, String value) {
      String tivoName = identifier.replaceFirst("limit_npl_", "");
      if (tivoName.length() > 0) {
         if (value.length() > 0) {      
            limit_npl_fetches.put(tivoName, value);
         } else {
            limit_npl_fetches.put(tivoName, "");
         }
      }
   }
   
   // Get configured setting in enableRpc hash for given tivoName
   private static String getRpcSetting(String tivoName) {
      if (enableRpc.containsKey(tivoName))
         return enableRpc.get(tivoName);
      else
         return "0";
   }
   
   // Set configured setting in limit_npl_fetches hash for given tivoName
   // NOTE: identifier = enableRpc_tivoName
   public static void setRpcSetting(String identifier, String value) {
      String tivoName = identifier.replaceFirst("enableRpc_", "");
      if (tivoName.length() > 0) {
         if (value.length() > 0)     
            enableRpc.put(tivoName, value);
         else
            enableRpc.put(tivoName, "0");
      }
   }
   
   // Return true if RPC enabled in config for given TiVo
   public static Boolean rpcEnabled(String tivoName) {
      String tsn = getTsn(tivoName);
      if (tsn != null) {
         try {
            int first = Integer.parseInt(tsn.substring(0, 1));
            if (first >= 7)
               enableRpc.put(tivoName, "1");
         } catch (NumberFormatException e) {
            // Do nothing except ignore
         }
      }
      return getRpcSetting(tivoName).equals("1");
   }
   
   // rpc enabled =>
   // 1. At least 1 TiVo has RpcSetting of "1"
   public static Boolean rpcEnabled() {
      Boolean rpcSetting = false;
      Stack<String> current_tivoNames = getTivoNames();
      for (int i=0; i<current_tivoNames.size(); ++i) {
         if (rpcEnabled(current_tivoNames.get(i)))
            rpcSetting = true;
      }
      return rpcSetting;
   }
   
   // Return name of 1st rpc enabled TiVo
   public static String getFirstRpcEnabled() {
      Stack<String> current_tivoNames = getTivoNames();
      for (int i=0; i<current_tivoNames.size(); ++i) {
         if (rpcEnabled(current_tivoNames.get(i)))
            return current_tivoNames.get(i);
      }
      return null;
   }
   
   // Return true if this is a series 3 TiVo and tivo.com username & password available
   public static Boolean mindEnabled(String tivoName) {
      /* TiVo broke tivo.com for series 3, so always return false now
      if (getTivoUsername() != null && getTivoPassword() != null) {
         String [] supported = {"648", "652", "658"};
         String tsn = getTsn(tivoName);
         if (tsn == null) {
            // Try and determine tsn from tivo.com
            Remote r = new Remote(tivoName, true);
            if (r.success) {
               r.disconnect();
               tsn = getTsn(tivoName);
            }
         }
         if (tsn != null) {
            for (int i=0; i<supported.length; ++i) {
               if (tsn.startsWith(supported[i]))
                  return true;
            }
         }
      }*/
      return false;
   }
   
   // Return true if:
   // 1. At least 1 TiVo has RpcSetting of "1"
   // 2. rpcDelete == 1
   public static Boolean rpcDeleteEnabled() {
      return rpcEnabled() && rpcDelete == 1;
   }
   
   public static Boolean twpDeleteEnabled() {
      return TivoWebPlusDelete == 1;
   }
   
   public static void twpDeleteEnabledSet(Boolean state) {
      if (state)
         TivoWebPlusDelete = 1;
      else
         TivoWebPlusDelete = 0;
   }
      
   public static Remote initRemote(String tivoName) {
      if (rpcEnabled(tivoName)) {
         Remote r = new Remote(tivoName);
         return(r);
      } else {
         Remote r = new Remote(tivoName, true);
         return(r);
      }
   }
   
   public static String getTivoUsername() {
      if (tivo_username.length() == 0)
         return null;
      return tivo_username;
   }
   
   public static void setTivoUsername(String username) {
      tivo_username = username;
   }
   
   public static String getTivoPassword() {
      if (tivo_password.length() == 0)
         return null;
      return tivo_password;
   }
   
   public static void setTivoPassword(String password) {
      tivo_password = password;
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
      
      httpserver_home = programDir + File.separator + "web";
      // This is configurable but define default
      httpserver_cache = httpserver_home + File.separator + "cache";

      
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
      cssDir       = programDir + s + "css";
      
      // File to store/restore GUI settings
      String settings_name = ".kmttg_settings_v2";
      if (OS.equals("windows"))
         settings_name = "kmttg_settings_v2";
      gui_settings = programDir + s + settings_name;
      if (file.isDir(System.getProperty("user.home"))) {
         // Centralize this non-critical file instead of localizing it
         gui_settings = System.getProperty("user.home") + s + settings_name;
      }
      
      // Non-executable defaults
      tivoFileNameFormat = "[title] ([monthNum]_[mday]_[year])";
      outputDir          = programDir;
      TIVOS.put("FILES", outputDir);
      mpegDir            = outputDir;
      qsfixDir           = outputDir;
      mpegCutDir         = outputDir;
      encodeDir          = outputDir;
      customCommand      = "";
      cpu_cores          = Runtime.getRuntime().availableProcessors();
      //pyTivo_host        = "localhost";
      //pyTivo_config      = "";
      //pyTivo_files       = "last";
      metadata_files     = "last";
      metadata_entries   = "";
      
      // 3rd party executable defaults
      tivodecode    = getProgramDefault("tivodecode");
      dsd           = getProgramDefault("dsd");
      ffmpeg        = getProgramDefault("ffmpeg");
      mencoder      = getProgramDefault("mencoder");
      handbrake     = getProgramDefault("handbrake");
      comskip       = getProgramDefault("comskip");
      comskipIni    = getProgramDefault("comskipIni");
      AtomicParsley = getProgramDefault("AtomicParsley");      
      ccextractor   = getProgramDefault("ccextractor");
      
      // Slingbox settings
      slingBox_perl = "";
      String tryit;
      if (OS.equals("windows"))
         tryit = "c:\\Perl\\bin\\perl.exe";
      else
         tryit = "/usr/bin/perl";
      if (file.isFile(tryit))
         slingBox_perl = tryit;
      slingBox_dir = outputDir;
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
      
      if (programName.equals("tivodecode")) {
         String tivodecode    = programDir + s + "tivodecode"    + s + "tivodecode"    + exe;      
         if (OS.equals("other") && ! file.isFile(tivodecode)) {
            result = file.unixWhich("tivodecode");
            if (result != null)
               tivodecode = result;
         }
         if (!file.isFile(tivodecode))
            tivodecode = "";
         return tivodecode;
      }
      
      else if (programName.equals("dsd")) {
         String dsd = programDir + s + "dsd" + s + "DSDCmd.exe";      
         if (OS.equals("other")) {
            dsd = "";
         }
         if (!file.isFile(dsd))
            dsd = "";
         return dsd;
      }
      
      else if (programName.equals("ffmpeg")) {
         String ffmpeg        = programDir + s + "ffmpeg"        + s + "ffmpeg"        + exe;
         if (OS.equals("other") && ! file.isFile(ffmpeg)) {
            result = file.unixWhich("ffmpeg");
            if (result != null)
               ffmpeg = result;
         }
         if (!file.isFile(ffmpeg))
            ffmpeg = "";
         return ffmpeg;
      }
      
      else if (programName.equals("mediainfo")) {
         String mediainfo = programDir + s + "mediainfo_cli" + s + "mediainfo" + exe;
         if (OS.equals("other") && ! file.isFile(mediainfo)) {
            result = file.unixWhich("mediainfo");
            if (result != null)
               mediainfo = result;
         }
         if (!file.isFile(mediainfo))
            mediainfo = "";
         return mediainfo;
      }
      
      else if (programName.equals("mencoder")) {
         String mencoder      = programDir + s + "mencoder"      + s + "mencoder"      + exe;
         if (OS.equals("other") && ! file.isFile(mencoder) ) {
            result = file.unixWhich("mencoder");
            if (result != null)
               mencoder = result;
         }
         if (!file.isFile(mencoder))
            mencoder = "";
         return mencoder;
      }
      
      else if (programName.equals("handbrake")) {
         String handbrake     = programDir + s + "handbrake"     + s + "HandBrakeCLI"  + exe;
         if (OS.equals("other") &&  ! file.isFile(handbrake) ) {
            result = file.unixWhich("HandBrakeCLI");
            if (result != null)
               handbrake = result;
         }
         if (!file.isFile(handbrake))
            handbrake = "";
         return handbrake;
      }
      
      else if (programName.equals("comskip")) {
         String comskip       = programDir + s + "comskip"       + s + "comskip"       + exe;
         if (OS.equals("other") &&  ! file.isFile(comskip) ) {
            result = file.unixWhich("comskip");
            if (result != null)
               comskip = result;
         }
         if (!file.isFile(comskip))
            comskip = "";
         return comskip;
      }
            
      else if (programName.equals("comskipIni")) {
         if (file.isFile(getProgramDefault("comskip")))
            return string.dirname(getProgramDefault("comskip")) + s + "comskip.ini";
         else
            return "";
      }
      
      else if (programName.equals("AtomicParsley")) {
         String AtomicParsley = programDir + s + "AtomicParsley" + s + "AtomicParsley" + exe;
         if (OS.equals("other") &&  ! file.isFile(AtomicParsley) ) {
            result = file.unixWhich("AtomicParsley");
            if (result != null)
               AtomicParsley = result;
         }
         if (!file.isFile(AtomicParsley))
            AtomicParsley = "";
         return AtomicParsley;
      }
      
      else if (programName.equals("ccextractor")) {
         String ccextractor    = programDir + s + "ccextractor"    + s + "ccextractor"    + exe;
         if (OS.equals("windows"))
            ccextractor    = programDir + s + "ccextractor"    + s + "ccextractorwin"    + exe;
         if (OS.equals("other") && ! file.isFile(ccextractor)) {
            result = file.unixWhich("ccextractor");
            if (result != null)
               ccextractor = result;
         }
         if (!file.isFile(ccextractor))
            ccextractor = "";
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
         String[] autotune_keys = com.tivo.kmttg.task.autotune.getRequiredElements();
         String autotune_tivoName = null;
         Boolean qs = false;
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
               if (l[0].equals("FILES")) {
                  name = l[0];
                  value = line;
                  value = value.replaceFirst("^\\s*FILES\\s+(.+)$", "$1");
                  value = string.removeLeadingTrailingSpaces(value);
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
            if (key.equals("SHARES")) {
               String l[] = line.split("=");
               httpserver_shares.put(l[0], string.removeLeadingTrailingSpaces(l[1]));
            }
            if (key.equals("tivoFileNameFormat")) {
               tivoFileNameFormat = line;
            }
            if (key.equals("FontSize")) {
               FontSize = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("lookAndFeel")) {
               lookAndFeel = string.removeLeadingTrailingSpaces(line);
            }
            if (key.equals("tableColAutoSize")) {
               tableColAutoSize = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("httpserver_enable")) {
               httpserver_enable = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("httpserver_port")) {
               httpserver_port = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("httpserver_cache")) {
               httpserver_cache = string.removeLeadingTrailingSpaces(line);
               // Create cache dir if it doesn't exist and web server is on
               if (! file.isDir(httpserver_cache))
                  new File(httpserver_cache).mkdirs();
            }
            if (key.equals("httpserver_share_filter")) {
               httpserver_share_filter = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
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
            if (key.equals("QSFixBackupMpegFile")) {
               QSFixBackupMpegFile = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("UseAdscan")) {
               UseAdscan = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("VrdReview")) {
               VrdReview = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("comskip_review")) {
               comskip_review = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
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
            if (key.equals("VrdCombineCutEncode")) {
               VrdCombineCutEncode = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("VrdQsfixMpeg2ps")) {
               VrdQsfixMpeg2ps = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("VrdOneAtATime")) {
            	VrdOneAtATime = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
             }
            if (key.equals("TSDownload")) {
               TSDownload = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("HideProtectedFiles")) {
               HideProtectedFiles = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("TiVoSort")) {
               TiVoSort = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("OverwriteFiles")) {
               OverwriteFiles = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("DeleteFailedDownloads")) {
               DeleteFailedDownloads = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("rpcnpl")) {
               rpcnpl = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("combine_download_decrypt")) {
               combine_download_decrypt = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("single_download")) {
               single_download = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("persistQueue")) {
                persistQueue = Boolean.parseBoolean(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("outputDir")) {
               outputDir = line;
            }
            if (key.equals("mpegDir")) {
               mpegDir = line;
            }
            if (key.equals("qsfixDir")) {
               qs = true;
               qsfixDir = line;
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
            if (key.equals("DsdDecrypt")) {
               DsdDecrypt = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("tivolibreDecrypt")) {
               tivolibreDecrypt = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("tivolibreCompat")) {
               tivolibreCompat = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("dsd")) {
               dsd = line;
            }
            if (key.equals("ffmpeg")) {
               ffmpeg = line;
            }
            if (key.equals("mediainfo")) {
               mediainfo = line;
            }
            if (key.equals("mencoder")) {
               mencoder = line;
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
            if (key.matches("^wan_.+$")) {
               key = key.replaceFirst("_ipad", "_rpc");
               WAN.put(key, line);
            }
            if (key.matches("^tsn_.+$")) {
               TSN.put(key.replaceFirst("tsn_", ""), line);
            }
            if (key.matches("^limit_npl_.+$")) {
               setLimitNplSetting(key, string.removeLeadingTrailingSpaces(line));
            }
            if (key.matches("^enableRpc_.+$")) {
               setRpcSetting(key, string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("MaxJobs")) {
               MaxJobs = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("MinChanDigits")) {
               MinChanDigits = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("VRD")) {
               if (! string.removeLeadingTrailingSpaces(line).equals("0"))
               VRD = 1;
            }
            if (key.equals("VRDexe")) {
               VRDexe = string.removeLeadingTrailingSpaces(line);
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
            if (key.equals("web_query")) {
               web_query = line;
            }
            if (key.equals("web_browser")) {
               web_browser = line;
            }
            if (key.equals("tivo_username")) {
               tivo_username = line;
            }
            if (key.equals("tivo_password")) {
               tivo_password = line;
            }
            /*if (key.equals("pyTivo_config")) {
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
            }*/
            if (key.equals("metadata_files")) {
               metadata_files = line;
            }
            if (key.equals("metadata_entries")) {
               metadata_entries = string.removeLeadingTrailingSpaces(line);
            }
            if (key.equals("autotune_tivoName")) {
               autotune_tivoName = line;
            }
            if (autotune_tivoName != null) {
               for (int i=0; i<autotune_keys.length; ++i) {
                  if (key.equals("autotune_" + autotune_keys[i])) {
                     com.tivo.kmttg.task.autotune.init(autotune_tivoName);
                     autotune.get(autotune_tivoName).put(
                        autotune_keys[i], string.removeLeadingTrailingSpaces(line)
                     );
                  }
               }
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
            if (key.equals("UseOldBeacon")) {
               UseOldBeacon = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("TivoWebPlusDelete")) {
               TivoWebPlusDelete = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("rpcDelete") || key.equals("iPadDelete")) {
               rpcDelete = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("rpcOld")) {
               rpcOld = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
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
            if (key.equals("download_delay")) {
               download_delay = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("autoskip_enabled")) {
               autoskip_enabled = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("autoskip_import")) {
               autoskip_import = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("autoskip_cutonly")) {
               autoskip_cutonly = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("autoskip_prune")) {
               autoskip_prune = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("autoskip_batch_standby")) {
               autoskip_batch_standby = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("autoskip_indicate_skip")) {
               autoskip_indicate_skip = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("autoskip_jumpToEnd")) {
               autoskip_jumpToEnd = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("autoskip_padding_start")) {
               autoskip_padding_start = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("autoskip_padding_stop")) {
               autoskip_padding_stop = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("autoskip_ServiceItems")) {
               String name;
               Boolean value;
               String l[] = line.split("\\s+");
               value = Boolean.parseBoolean(l[l.length-1]);
               name = "";
               for (int i=0; i<l.length-1; i++) {
                  name += l[i] + " ";
               }
               name = name.substring(0,name.length()-1);
               autoskip_ServiceItems.put(name, value);
            }
            if (key.equals("download_time_estimate")) {
               download_time_estimate = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("download_check_length")) {
               download_check_length = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("autoLogSizeMB")) {
               autoLogSizeMB = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("npl_when_started")) {
               npl_when_started = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
            }
            if (key.equals("showHistoryInTable")) {
               showHistoryInTable = Integer.parseInt(string.removeLeadingTrailingSpaces(line));
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
         if ( ! file.isDir(TIVOS.get("FILES")) ) {
            TIVOS.put("FILES", outputDir);
         }
         
         if ( ! qs )
            qsfixDir = mpegDir;
      }         
      catch (IOException ex) {
         log.error("Problem parsing config file: " + config);
         return false;
      }
      
      return true;

   }
   
   // Save current settings in memory to config.ini
   public static Boolean save() {
      debug.print("");
      String config = configIni;
      try {
         BufferedWriter ofp = new BufferedWriter(new FileWriter(config));
         
         ofp.write("# kmttg config.ini file\n");
         
         ofp.write("<MAK>\n" + MAK + "\n\n");
         
         ofp.write("<TIVOS>\n");
         for (String name : TIVOS.keySet())
            ofp.write(String.format("%-20s %-20s\n", name, TIVOS.get(name)));
         ofp.write("\n");
         
         ofp.write("<SHARES>\n");
         for (String name : httpserver_shares.keySet())
            ofp.write(name + "=" + httpserver_shares.get(name) + "\n");
         ofp.write("\n");
         
         if (WAN.size() > 0) {
            for (Enumeration<String> e=WAN.keys(); e.hasMoreElements();) {
               String name = e.nextElement();
               ofp.write("<" + name + ">\n");
               ofp.write(WAN.get(name) + "\n\n");
            }
         }
         
         if (TSN.size() > 0) {
            for (Enumeration<String> e=TSN.keys(); e.hasMoreElements();) {
               String name = e.nextElement();
               ofp.write("<tsn_" + name + ">\n");
               ofp.write(TSN.get(name) + "\n\n");
            }
         }
         
         if (limit_npl_fetches.size() > 0) {
            for (Enumeration<String> e=limit_npl_fetches.keys(); e.hasMoreElements();) {
               String tivoName = e.nextElement();
               ofp.write("<limit_npl_" + tivoName + ">\n");
               ofp.write(limit_npl_fetches.get(tivoName) + "\n\n");
            }            
         }
         
         if (enableRpc.size() > 0) {
            for (Enumeration<String> e=enableRpc.keys(); e.hasMoreElements();) {
               String tivoName = e.nextElement();
               ofp.write("<enableRpc_" + tivoName + ">\n");
               ofp.write(enableRpc.get(tivoName) + "\n\n");
            }            
         }
         
         ofp.write("<FontSize>\n" + FontSize + "\n\n");
         
         ofp.write("<lookAndFeel>\n" + lookAndFeel + "\n\n");
         
         ofp.write("<tableColAutoSize>\n" + tableColAutoSize + "\n\n");
         
         ofp.write("<httpserver_enable>\n" + httpserver_enable + "\n\n");
         
         ofp.write("<httpserver_port>\n" + httpserver_port + "\n\n");
         
         ofp.write("<httpserver_cache>\n" + httpserver_cache + "\n\n");
         
         ofp.write("<httpserver_share_filter>\n" + httpserver_share_filter + "\n\n");
         
         ofp.write("<RemoveTivoFile>\n" + RemoveTivoFile + "\n\n");
         
         ofp.write("<RemoveComcutFiles>\n" + RemoveComcutFiles + "\n\n");
         
         ofp.write("<RemoveComcutFiles_mpeg>\n" + RemoveComcutFiles_mpeg + "\n\n");
         
         ofp.write("<RemoveMpegFile>\n" + RemoveMpegFile + "\n\n");
         
         ofp.write("<VRD>\n" + VRD + "\n\n");
         
         ofp.write("<VRDexe>\n" + VRDexe + "\n\n");
         
         ofp.write("<QSFixBackupMpegFile>\n" + QSFixBackupMpegFile + "\n\n");
         
         ofp.write("<UseAdscan>\n" + UseAdscan + "\n\n");
         
         ofp.write("<VrdReview>\n" + VrdReview + "\n\n");
         
         ofp.write("<comskip_review>\n" + comskip_review + "\n\n");
         
         ofp.write("<VrdReview_noCuts>\n" + VrdReview_noCuts + "\n\n");
         
         ofp.write("<VrdQsFilter>\n" + VrdQsFilter + "\n\n");
         
         ofp.write("<VrdDecrypt>\n" + VrdDecrypt + "\n\n");
         
         ofp.write("<VrdEncode>\n" + VrdEncode + "\n\n");
         
         ofp.write("<VrdAllowMultiple>\n" + VrdAllowMultiple + "\n\n");
         
         ofp.write("<VrdCombineCutEncode>\n" + VrdCombineCutEncode + "\n\n");
         
         ofp.write("<VrdQsfixMpeg2ps>\n" + VrdQsfixMpeg2ps + "\n\n");
         
         ofp.write("<VrdOneAtATime>\n" + VrdOneAtATime + "\n\n");
         
         ofp.write("<TSDownload>\n" + TSDownload + "\n\n");
         
         ofp.write("<HideProtectedFiles>\n" + HideProtectedFiles + "\n\n");
         
         ofp.write("<TiVoSort>\n" + TiVoSort + "\n\n");
         
         ofp.write("<OverwriteFiles>\n" + OverwriteFiles + "\n\n");
         
         ofp.write("<DeleteFailedDownloads>\n" + DeleteFailedDownloads + "\n\n");
         
         ofp.write("<rpcnpl>\n" + rpcnpl + "\n\n");
         
         ofp.write("<combine_download_decrypt>\n" + combine_download_decrypt + "\n\n");
         
         ofp.write("<single_download>\n" + single_download + "\n\n");
         
         ofp.write("<persistQueue>\n" + persistQueue + "\n\n");
         
         ofp.write("<tivoFileNameFormat>\n" + tivoFileNameFormat + "\n\n");
         
         ofp.write("<outputDir>\n" + outputDir + "\n\n");
         
         ofp.write("<mpegDir>\n" + mpegDir + "\n\n");
         
         ofp.write("<qsfixDir>\n" + qsfixDir + "\n\n");
         
         ofp.write("<mpegCutDir>\n" + mpegCutDir + "\n\n");
         
         ofp.write("<encodeDir>\n" + encodeDir + "\n\n");
         
         ofp.write("<tivodecode>\n" + tivodecode + "\n\n");
         
         ofp.write("<DsdDecrypt>\n" + DsdDecrypt + "\n\n");
         
         ofp.write("<tivolibreDecrypt>\n" + tivolibreDecrypt + "\n\n");
         
         ofp.write("<tivolibreCompat>\n" + tivolibreCompat + "\n\n");
         
         ofp.write("<dsd>\n" + dsd + "\n\n");
         
         ofp.write("<ffmpeg>\n" + ffmpeg + "\n\n");
         
         ofp.write("<mediainfo>\n" + mediainfo + "\n\n");
         
         ofp.write("<mencoder>\n" + mencoder + "\n\n");
         
         ofp.write("<handbrake>\n" + handbrake + "\n\n");
         
         ofp.write("<comskip>\n" + comskip + "\n\n");
         
         ofp.write("<comskipIni>\n" + comskipIni + "\n\n");
         
         ofp.write("<MaxJobs>\n" + MaxJobs + "\n\n");
         
         ofp.write("<MinChanDigits>\n" + MinChanDigits + "\n\n");
         
         ofp.write("<AtomicParsley>\n" + AtomicParsley + "\n\n");
         
         ofp.write("<t2extract>\n" + t2extract + "\n\n");
         
         ofp.write("<t2extract_args>\n" + t2extract_args + "\n\n");
                                    
         ofp.write("<ccextractor>\n" + ccextractor + "\n\n");
         
         ofp.write("<custom>\n" + customCommand + "\n\n");
         
         ofp.write("<web_query>\n" + web_query + "\n\n");
         
         if (web_browser.length() > 0)
            ofp.write("<web_browser>\n" + web_browser + "\n\n");
         
         ofp.write("<tivo_username>\n" + tivo_username + "\n\n");
         
         ofp.write("<tivo_password>\n" + tivo_password + "\n\n");
         
         //ofp.write("<pyTivo_config>\n" + pyTivo_config + "\n\n");
         
         //ofp.write("<pyTivo_host>\n" + pyTivo_host + "\n\n");
         
         //ofp.write("<pyTivo_tivo>\n" + pyTivo_tivo + "\n\n");
         
         //ofp.write("<pyTivo_files>\n" + pyTivo_files + "\n\n");
         
         ofp.write("<metadata_files>\n" + metadata_files + "\n\n");
         
         ofp.write("<metadata_entries>\n" + metadata_entries + "\n\n");
         
         ofp.write("<CheckDiskSpace>\n" + CheckDiskSpace + "\n\n");
         
         ofp.write("<LowSpaceSize>\n" + LowSpaceSize + "\n\n");
         
         ofp.write("<CheckBeacon>\n" + CheckBeacon + "\n\n");
         
         ofp.write("<UseOldBeacon>\n" + UseOldBeacon + "\n\n");
         
         ofp.write("<TivoWebPlusDelete>\n" + TivoWebPlusDelete + "\n\n");
         
         ofp.write("<rpcDelete>\n" + rpcDelete + "\n\n");
         
         ofp.write("<rpcOld>\n" + rpcOld + "\n\n");
         
         ofp.write("<cpu_cores>\n" + cpu_cores + "\n\n");
         
         ofp.write("<download_tries>\n" + download_tries + "\n\n");
         
         ofp.write("<download_retry_delay>\n" + download_retry_delay + "\n\n");
         
         ofp.write("<download_delay>\n" + download_delay + "\n\n");
         
         ofp.write("<download_time_estimate>\n" + download_time_estimate + "\n\n");
         
         ofp.write("<download_check_length>\n" + download_check_length + "\n\n");
         
         ofp.write("<autoskip_enabled>\n" + autoskip_enabled + "\n\n");
         
         ofp.write("<autoskip_import>\n" + autoskip_import + "\n\n");
         
         ofp.write("<autoskip_cutonly>\n" + autoskip_cutonly + "\n\n");
         
         ofp.write("<autoskip_prune>\n" + autoskip_prune + "\n\n");
         
         ofp.write("<autoskip_batch_standby>\n" + autoskip_batch_standby + "\n\n");
         
         ofp.write("<autoskip_indicate_skip>\n" + autoskip_indicate_skip + "\n\n");
         
         ofp.write("<autoskip_jumpToEnd>\n" + autoskip_jumpToEnd + "\n\n");
         
         ofp.write("<autoskip_padding_start>\n" + autoskip_padding_start + "\n\n");
         
         ofp.write("<autoskip_padding_stop>\n" + autoskip_padding_stop + "\n\n");
         
         ofp.write("<autoskip_ServiceItems>\n");
         for (String name : autoskip_ServiceItems.keySet())
            ofp.write(String.format("%-20s %-20s\n", name, autoskip_ServiceItems.get(name)));
         ofp.write("\n");
         
         ofp.write("<autoLogSizeMB>\n" + autoLogSizeMB + "\n\n");
         
         ofp.write("<npl_when_started>\n" + npl_when_started + "\n\n");
         
         ofp.write("<showHistoryInTable>\n" + showHistoryInTable + "\n\n");
         
         if (autotune != null ) {
            Enumeration<String> tivos = autotune.keys();
            String[] keys = com.tivo.kmttg.task.autotune.getRequiredElements();
            String tivoName;
            while (tivos.hasMoreElements()) {
               tivoName = tivos.nextElement();
               ofp.write("<autotune_tivoName>\n" + tivoName + "\n\n");
               for (int i=0; i<keys.length; ++i) {
                  if ( autotune.get(tivoName).containsKey(keys[i]) ) {
                     ofp.write("<autotune_" + keys[i] + ">\n" + autotune.get(tivoName).get(keys[i]) + "\n\n");
                  }
               }
            }
         }
         
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
      
      log.warn("Configuration saved to file: " + config);
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
   
   // bodyId used by rpc remote
   public static String bodyId_get(String IP, int port) {
      if (bodyId == null)
         bodyId = new Hashtable<String,String>();
      String id = IP + port;
      if (bodyId.containsKey(id))
         return bodyId.get(id);
      else
         return "";
   }
   
   // bodyId used by rpc remote
   public static void bodyId_set(String IP, int port, String bid) {
      if (bodyId == null)
         bodyId = new Hashtable<String,String>();
      String id = IP + port;
      bodyId.put(id, bid);
   }
    
}
