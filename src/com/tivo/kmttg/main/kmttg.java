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

import java.security.Security;
import java.util.Arrays;
import java.util.Timer;

import com.tivo.kmttg.rpc.SkipManager;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.*;
import com.tivo.kmttg.gui.gui;
import com.tivo.kmttg.httpserver.kmttgServer;

public class kmttg {
   public static Timer timer;
   static Boolean gui_mode = true;
   public static boolean _shuttingDown = false;
   public static boolean _startingUp = true;
   static Boolean autoconflicts = false; // Special batch mode run for RPC conflicts
   static String autoskip = null; // Special batch mode for AutoSkip from SkipMode
   static String autoskipService = null; // Special batch mode for AutoSkip service
      
   public static void main(String[] argv) {
      debug.enabled = false;
      
      // This is to workaround issue that started with Java 7 update 40 which sets
      // jdk.certpath.disabledAlgorithms property to "MD2, RSA keySize < 1024" and
      // prevents authentication from working (because mind.tivo.com has a 512 bit key in chain).
      // Debug issues using following when running java: -Djavax.net.debug=all
      // Search for: "Cipher Suite: ..."
      // This cipher suite works with mind.tivo.com: SSL_RSA_WITH_RC4_128_SHA
      System.setProperty("https.cipherSuites", "SSL_RSA_WITH_RC4_128_SHA");
      // This needs to be set to something to override default setting of: "MD2, RSA keySize < 1024"
      Security.setProperty("jdk.certpath.disabledAlgorithms","");
      // This also needed for recent releases of Java 8
      Security.setProperty("jdk.tls.disabledAlgorithms", "SSLv3");
      
      // Java 7 bug workaround to avoid stacktrace when switching to Remote Guide tab
      System.getProperties().setProperty("java.util.Arrays.useLegacyMergeSort", "true");
      
      // Parse command lines and set options accordingly
      getopt(argv);
      
      // Handle any uncaught exceptions
      Thread.setDefaultUncaughtExceptionHandler(new myExceptionHandler());
      
      // Register a shutdown thread
      Runtime.getRuntime().addShutdownHook(new Thread() {
          // This method is called during shutdown
          public void run() {
             System.out.println("Shutdown hook executing");
             log.warn("SHUTTING DOWN");
             log.stopLogger();
             config.GUIMODE = false;             
             _shuttingDown = true; // Global flag used by various methods
             
             // Kill any running background jobs
             jobMonitor.killRunning();
             if (config.httpserver != null)
                config.httpserver.killTranscodes();
             if (debug.enabled) debug.close();             
          }
      });
      
      if (gui_mode) {
         // GUI mode
         config.gui = new gui();
         config.parse();
         
         // Start web server if configured
         if (config.httpserver_enable == 1)
            new kmttgServer();
         
         // All uncaught exceptions go to message window in GUI
         Thread.setDefaultUncaughtExceptionHandler(new myExceptionHandler());
         config.gui.Launch();
      } else {         
         // Batch/auto mode
    	   config.parse();	// persist queue held in main config file
    	   if (autoconflicts) {
            log.print("START AUTO-CONFLICTS RESOLVER");
    	      rnpl.AutomaticConflictsHandler();
    	      log.print("\nEND AUTO-CONFLICTS RESOLVER");
    	      System.exit(0);
    	   } else if (autoskipService != null) {
    	     _startingUp = false;
    	      SkipManager.skipServiceBatch(autoskipService);
    	   } else if (autoskip != null) {
    	      _startingUp = false;
            log.print("Processing AutoSkip from SkipMode for tivo '" + autoskip + "'");
            SkipManager.visualDetectBatch(autoskip);
            System.exit(0);
    	   } else {
       	   // Upon startup, try and load saved queue. Must be done before starting auto loop
           	if (config.persistQueue)
           		jobMonitor.loadAllJobs(1);	// doesn't need any time to setup
           	_startingUp = false;
            auto.startBatchMode();
    	   }
      }
   }
   
   private static void getopt(String[] argv) {
      debug.print("argv=" + Arrays.toString(argv));
      int i=0;
      String arg;
      while ( i<argv.length ) {
         arg = argv[i++];
         if (arg.equals("-a")) {
            gui_mode = false;
            config.LOOP = true;
         }
         else if (arg.equals("-b")) {
            gui_mode = false;
            config.LOOP = false;
         }
         else if (arg.equals("-c")) {
            gui_mode = false;
            config.LOOP = false;
            autoconflicts = true;
         }
         else if (arg.equals("-d")) {
            debug.enabled = true;
         }
         else if (arg.equals("-h")) {
            useage();
         }
         else if (arg.equals("-k")) {
            autoskipService = argv[i++];
            gui_mode = false;
         }
         else if (arg.equals("-s")) {
            autoskip = argv[i++];
            gui_mode = false;
         }
         else if (arg.equals("-v")) {
            gui_mode = false;
            String[] s = config.kmttg.split("\\s+");
            System.out.println(s[1]);
            System.exit(0);
         }
      }                
   }
   
   // Print available command line options and exit
   private static void useage() {
      debug.print("");
      System.out.println("-h => Print this help message\n");
      System.out.println("-a => Run in auto download batch mode - loop forever\n");
      System.out.println("-b => Run in auto download batch mode - single loop\n");
      System.out.println("-c => Run auto-conflict resolver in batch mode - single run\n");
      System.out.println("-d => Enable verbose debug mode\n");
      System.out.println("-k \"tivoName\" => Run background mode AutoSkip service for given TiVo\n");
      System.out.println("-k all => Run background mode AutoSkip service for all eligible TiVos\n");
      System.out.println("-s \"tivoName\" => Process AutoSkip from SkipMode for given TiVo\n");
      System.out.println("-v => Print version and exit\n");
      System.exit(0);
   }   
}

