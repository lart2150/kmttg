package com.tivo.kmttg.main;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.*;
import com.tivo.kmttg.gui.gui;
import com.tivo.kmttg.install.mainInstall;

public class kmttg {
   static Timer timer;
   static Boolean gui_mode = true;
   public static boolean _shuttingDown = false;
   public static boolean _startingUp = true;
   static Boolean autoconflicts = false; // Special batch mode run for RPC conflicts
      
   public static void main(String[] argv) {
      debug.enabled = false;
      
      // Parse command lines and set options accordingly
      getopt(argv);
      
      // Handle any uncaught exceptions
      Thread.setDefaultUncaughtExceptionHandler(new myExceptionHandler());
      
      // Register a shutdown thread
      Runtime.getRuntime().addShutdownHook(new Thread() {
          // This method is called during shutdown
          public void run() {             
             if (config.GUIMODE) {
                // Save GUI settings if in GUI mode
                config.gui.saveSettings();
             } else {
                // Shut down message in in batch mode
                log.warn("SHUTTING DOWN");
             }
             
             config.GUIMODE = false;
             
             // Before shutdown, try and save the job queue
             // 2/16/2012 - Don't need to save on exit, saved on queue change.
             // Because we clear up the jobs before exiting, we *don't* want
             // to save those changes so set flag to tell it not to save.
             _shuttingDown = true;
             /*if (config.persistQueue)
            	 jobMonitor.saveAllJobs();*/
             
             // Kill any running background jobs
             jobMonitor.killRunning();
             if (debug.enabled) debug.close();
             
             // Kill any non-deamon, non-AWT threads
             /*for (Thread thread : Thread.getAllStackTraces().keySet()) {
                // daemon threads will not prevent shutdown
                if (!thread.isDaemon()) {
                   if (! thread.getName().startsWith("AWT") && ! thread.getName().startsWith("Destroy")) {
                      System.err.println("Killing thread: " + thread.getName());
                      thread.interrupt();
                   }
                }
             }*/
          }
      });
      
      if (gui_mode) {
         // GUI mode
         config.gui = new gui();
         config.parse();
         SwingUtilities.invokeLater(
            new Runnable() {
               public void run() {
                  // All uncaught exceptions go to message window in GUI
                  Thread.setDefaultUncaughtExceptionHandler(new myExceptionHandler());
                  
                  // Create and display main frame
                  config.gui.getJFrame().setVisible(true);
                  if (! config.lookAndFeel.equals("default"))
                     config.gui.setLookAndFeel(config.lookAndFeel);
                  
                  // Download tools if necessary
                  mainInstall.install();
               }
            }
         );

         // Invoke a 300ms period timer for job monitor
         timer = new Timer(300, new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
               jobMonitor.monitor(config.gui, timer);
            }    
         });
         timer.start();
         
			// Upon startup, try and load saved queue
			if (config.persistQueue)
				jobMonitor.loadAllJobs(10);	// delay load to give gui time to setup
       	_startingUp = false;
       	
      } else {         
         // Batch/auto mode
    	   config.parse();	// persist queue held in main config file
    	   if (autoconflicts) {
            log.print("START AUTO-CONFLICTS RESOLVER");
    	      rnpl.AutomaticConflictsHandler();
    	      log.print("\nEND AUTO-CONFLICTS RESOLVER");
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
      debug.print("argv=" + argv);
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
      System.out.println("-v => Print version and exit\n");
      System.exit(0);
   }   
}

