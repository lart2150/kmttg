package com.tivo.kmttg.util;

import java.io.File;
import java.io.IOException;
import java.util.Stack;

import com.tivo.kmttg.main.config;

public class file {
   
   public static Boolean isFile(String f) {
      debug.print("f=" + f);
      try {
         return new File(f).isFile();
      }
      catch (NullPointerException e) {
         return false;
      }
   }
   
   public static Boolean isDir(String d) {
      debug.print("d=" + d);
      try {
         return new File(d).isDirectory();
      }
      catch (NullPointerException e) {
         return false;
      }
   }
   
   public static long size(String f) {
      debug.print("f=" + f);
      try {
         return new File(f).length();
      }
      catch (NullPointerException e) {
         return 0;
      }
      catch (SecurityException e) {
         return 0;
      }
   }
   
   // Java 1.5 compatible free space calculator
   public static long freeSpace(String f) {
      long bad = 0;
      if ( ! file.isDir(f) ) return bad;
      long free;
      Stack<String> command = new Stack<String>();
      if (config.OS.matches("windows")) {
         // Use 'dir' command to get free space
         command.add("cmd");
         command.add("/c");
         command.add("dir");
         command.add(f);
         backgroundProcess process = new backgroundProcess();
         if ( process.run(command) ) {
            if ( process.Wait() == 0 ) {
               Stack<String> l = process.getStdout();
               if (l.size() > 0) {
                  String free_string = l.lastElement();
                  if (free_string.matches(".*bytes\\s+free")) {
                     String[] ll = free_string.split("\\s+");
                     free_string = ll[ll.length-3].replaceAll(",", "");
                     try {
                        free = Long.parseLong(free_string);
                        return free;
                     } catch (NumberFormatException e) {
                        return bad;
                     }
                  } else {
                     return bad;
                  }
               }
            }
         }         
      } else {
         // Use 'df' command to get free space
         backgroundProcess process = new backgroundProcess();
         command.add("/bin/df");
         command.add("-k");
         command.add(f);
         if ( process.run(command) ) {
            if ( process.Wait() == 0 ) {
               Stack<String> l = process.getStdout();
               if (l.size() > 0) {
                  String free_string = l.lastElement();
                  String[] ll = free_string.split("\\s+");
                  if (ll.length-3 >= 0) {
                     free_string = ll[ll.length-3];
                     try {
                        free = Long.parseLong(free_string);
                        free = (long) (free * Math.pow(2, 10));
                        return free;
                     } catch (NumberFormatException e) {
                        return bad;
                     }
                  }
               }
            }
         }         
      }
      return bad;
   }

   public static Boolean isEmpty(String f) {
      debug.print("f=" + f);
      if (size(f) == 0) {
         return true;
      } else {
         return false;
      }
   }
   
   public static Boolean delete(String f) {
      debug.print("f=" + f);
      try {
         return new File(f).delete();
      }
      catch (NullPointerException e) {
         log.error(e.getMessage());
         return false;
      }
   }
   
   public static Boolean rename(String fold, String fnew) {
      debug.print("fold=" + fold + " fnew=" + fnew);
      try {
         return new File(fold).renameTo(new File(fnew));
      }
      catch (NullPointerException e) {
         log.error(e.getMessage());
         return false;
      }
   }
      
   // Create a new empty file
   public static Boolean create(String fileName) {
      debug.print("fileName=" + fileName);
      try {
         File f = new File(fileName);
         return f.createNewFile();
      } catch (IOException e) {
         log.error(e.getMessage());
         return false;
      }
   }
   
   public static String makeTempFile(String prefix) {
      debug.print("prefix=" + prefix);
      try {
         File tmp = File.createTempFile(prefix, ".tmp", new File(config.tmpDir));
         tmp.deleteOnExit();
         return tmp.getPath();
      } catch (IOException e) {
         log.error(e.getMessage());
         return null;
      }
   }
   
   public static String makeTempFile(String prefix, String suffix) {
      debug.print("prefix=" + prefix);
      try {
         File tmp = File.createTempFile(prefix, suffix, new File(config.tmpDir));
         tmp.deleteOnExit();
         return tmp.getPath();
      } catch (IOException e) {
         log.error(e.getMessage());
         return null;
      }
   }
   
   // Locate full path of an executable using "which"
   // Return null if not found
   public static String unixWhich(String c) {
      if (c != null) {
         Stack<String> command = new Stack<String>();
         command.add("/usr/bin/which");
         command.add(c);
         backgroundProcess process = new backgroundProcess();
         if ( process.run(command) ) {
            if ( process.Wait() == 0 ) {
               String result = process.getStdoutLast();
               if (result.length() > 0 && file.isFile(result)) {
                  return result;
               }
            }
         }
      }
      return null;
   }
}
