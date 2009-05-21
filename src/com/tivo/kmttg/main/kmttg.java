package com.tivo.kmttg.main;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.tivo.kmttg.util.*;
import com.tivo.kmttg.gui.gui;
import com.tivo.kmttg.install.mainInstall;

public class kmttg {
   static Timer timer;
   static Boolean gui_mode = true;
      
   public static void main(String[] argv) {
      debug.enabled = false;
      
      // Handle any uncaught exceptions
      Thread.setDefaultUncaughtExceptionHandler(new myExceptionHandler());
      
      // Register a shutdown thread
      Runtime.getRuntime().addShutdownHook(new Thread() {
          // This method is called during shutdown
          public void run() {
             // Kill any running background jobs
             jobMonitor.killRunning();
             if (debug.enabled) debug.close();
          }
      });
      
      // Parse command lines and set options accordingly
      getopt(argv);
            
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
      } else {         
         // Batch/auto mode
         auto.startBatchMode();
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
         else if (arg.equals("-d")) {
            debug.enabled = true;
         }
         else if (arg.equals("-h")) {
            useage();
         }
      }                
   }
   
   // Print available command line options and exit
   private static void useage() {
      debug.print("");
      System.out.println("-h => Print this help message\n");
      System.out.println("-a => Run in auto download batch mode - loop forever\n");
      System.out.println("-b => Run in auto download batch mode - single loop\n");
      System.out.println("-d => Enable verbose debug mode\n");
      System.exit(0);
   }   
}

