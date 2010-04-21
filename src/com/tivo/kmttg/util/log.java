package com.tivo.kmttg.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Stack;

import com.tivo.kmttg.main.config;

public class log {
   private static String n = "\r\n";
   private static String message;
   
   private static void logToFile(String type, String s) {
      String time = getDetailedTime();
      String extra = " ";
      if (type.equals("warn"))
         extra = " NOTE: ";
      if (type.equals("error"))
         extra = " ERROR: ";
      message = time + extra + s + n;
      try {
         BufferedWriter ofp = new BufferedWriter(new FileWriter(config.autoLog, true));
         System.out.print(message);
         ofp.write(message);
         ofp.close();
      } catch (IOException ex) {
         System.out.print(message);
      }     
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
