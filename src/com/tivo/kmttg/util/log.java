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
package com.tivo.kmttg.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Stack;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javafx.application.Platform;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.kmttg;

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
      } catch (Exception e) {
         System.out.println("Failed to initialize logger handler. Reason: " + e);
         return false;
      }      
   }
   
   // Log entries to config.autoLog file
   // Uses logger for 2 file limited size rotation
   private static void logToFile(String type, String s) {
      if (kmttg._shuttingDown || kmttg._startingUp) {
         // Don't log if in GUI mode and shutting down or starting up
         // so as to avoid creating auto.log* files
         System.out.println(type + ": "+ s);
      } else {
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
   }
   
   public static void print(String s) {
      Boolean log = true;
      if (s != null && s.length() > 0) {
         s = filterMAK(s);
         if (config.GUIMODE) {
            final String s_final = s;
            Platform.runLater(new Runnable() {
               @Override public void run() {
                  config.gui.text_print(s_final);
               }
            });
            if (config.GUI_LOOP == 0) {
               log = false;
            }
         }
         else {
            // Non GUI mode
            System.out.println(s);
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
         if (config.GUIMODE) {
            final String s_final = s;
            Platform.runLater(new Runnable() {
               @Override public void run() {
                  config.gui.text_warn(s_final);
               }
            });
            if (config.GUI_LOOP == 0) {
               log = false;
            }
         }
         else {
            // Non GUI mode
            System.out.println(s);
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
         if (config.GUIMODE) {
            final String s_final = s;
            Platform.runLater(new Runnable() {
               @Override public void run() {
                  config.gui.text_error(s_final);
               }
            });
            if (config.GUI_LOOP == 0) {
               log = false;
            }
         }
         else {
            // Non GUI mode
            System.out.println("ERROR: " + s);
         }
         
         if (log) {
            logToFile("error", s);
         }
      }
   }
   
   public static void print(final Stack<String> s) {
      if (s != null && s.size() > 0) {
         if (config.GUIMODE) {
            Platform.runLater(new Runnable() {
               @Override public void run() {
                  config.gui.text_print(s);
               }
            });
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
   
   public static void warn(final Stack<String> s) {
      if (s != null && s.size() > 0) {
         if (config.GUIMODE) {
            Platform.runLater(new Runnable() {
               @Override public void run() {
                  config.gui.text_warn(s);
               }
            });
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
   
   public static void error(final Stack<String> s) {
      if (s != null && s.size() > 0) {
         if (config.GUIMODE) {
            Platform.runLater(new Runnable() {
               @Override public void run() {
                  config.gui.text_error(s);
               }
            });
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
