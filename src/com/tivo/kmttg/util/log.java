package com.tivo.kmttg.util;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Stack;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.tivo.kmttg.main.config;

public class log {
   private static Logger logger = null;
   private static FileHandler handler = null;
   private static String n = "\r\n";
   
   public static void stopLogger() {
      if (logger != null && handler != null) {
         handler.close();
         handler = null;
         logger = null;
      }
   }
   
   private static boolean initLogger() {
      class CustomFormatter extends Formatter {
         public synchronized String format(LogRecord record) {
            String time = getDetailedTime();
            Level level = record.getLevel();
            String extra = " ";
            if (level == Level.WARNING)
               extra = " NOTE: ";
            if (level == Level.SEVERE)
               extra = " ERROR: ";
               
            return time + extra + record.getMessage() + n;
         }
       }
      logger = Logger.getLogger("log");
      logger.setUseParentHandlers(false);
      try {
         // Create a FileHandler with file size limit and 2 rotating log files.
         int MB = 1024*1024;
         int size = MB*config.autoLogSizeMB;
         handler = new FileHandler(config.autoLog, size, 2, true);
         handler.setFormatter(new CustomFormatter());
         logger.addHandler(handler);
         return true;
      } catch (IOException e) {
         System.out.println("Failed to initialize logger handler.");
         return false;
      }      
   }
   
   // Log entries to config.autoLog file
   // Uses logger for 2 file limited size rotation
   private static void logToFile(String type, String s) {
      if (logger == null) {
         if ( ! initLogger() )
            return;
      }
      if (type.equals("print"))
         logger.info(s);
      if (type.equals("warn"))
         logger.warning(s);
      if (type.equals("error"))
         logger.severe(s);
   }
   
   public static void print(String s) {
      Boolean log = true;
      if (s != null && s.length() > 0) {
         s = filterMAK(s);
         if (config.GUI) {
            config.gui.text_print(s);
            if (config.GUI_LOOP == 0) {
               log = false;
            }
         }
         
         if (log) {
            logToFile("print", s);
         }
      }
   }
   
   public static void warn(String s) {
      Boolean log = true;
      if (s != null && s.length() > 0) {
         s = filterMAK(s);
         if (config.GUI) {
            config.gui.text_warn(s);
            if (config.GUI_LOOP == 0) {
               log = false;
            }
         }
         
         if (log) {
            logToFile("warn", s);
         }
      }
   }
   
   public static void error(String s) {
      Boolean log = true;
      if (s != null && s.length() > 0) {
         s = filterMAK(s);
         if (config.GUI) {
            config.gui.text_error(s);
            if (config.GUI_LOOP == 0) {
               log = false;
            }
         }
         
         if (log) {
            logToFile("error", s);
         }
      }
   }
   
   public static void print(Stack<String> s) {
      if (s != null && s.size() > 0) {
         if (config.GUI) {
            config.gui.text_print(s);
            if (config.GUI_LOOP == 1) {
               // Log to file for loop in GUI
               for (int i=0; i<s.size(); ++i) {
                  logToFile("print", s.get(i));
               }
            }
         } else {
            for (int i=0; i<s.size(); ++i) {
               print(s.get(i));
            }
         }
      }
   }
   
   public static void warn(Stack<String> s) {
      if (s != null && s.size() > 0) {
         if (config.GUI) {
            config.gui.text_warn(s);
            if (config.GUI_LOOP == 1) {
               // Log to file for loop in GUI
               for (int i=0; i<s.size(); ++i) {
                  logToFile("warn", s.get(i));
               }
            }
         } else {
            for (int i=0; i<s.size(); ++i) {
               warn(s.get(i));
            }
         }
      }
   }
   
   public static void error(Stack<String> s) {
      if (s != null && s.size() > 0) {
         if (config.GUI) {
            config.gui.text_error(s);
            if (config.GUI_LOOP == 1) {
               // Log to file for loop in GUI
               for (int i=0; i<s.size(); ++i) {
                  logToFile("error", s.get(i));
               }
            }
         } else {
            for (int i=0; i<s.size(); ++i) {
               error(s.get(i));
            }
         }
      }
   }
   
   private static String getDetailedTime() {
      debug.print("");
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd_HH:mm:ss");
      return sdf.format(new Date());
   }
   
   private static String filterMAK(String s) {
      if (config.MAK.length() > 0)
         return s.replaceAll(config.MAK, "MAK");
      else
         return s;
   }
   
}
