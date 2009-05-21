package com.tivo.kmttg.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class debug {
   private static String file = null;
   private static BufferedWriter ofp = null;
   public static Boolean enabled = false;
   
   private static void init() {
      String dir = new File(
         debug.class.getProtectionDomain().getCodeSource().getLocation().getPath()
      ).getParent();
      dir = string.urlDecode(dir);

      file = dir + File.separator + "debug.log";
      try {
         ofp = new BufferedWriter(new FileWriter(file));
      }
      catch (IOException ex) {
         System.out.println("Problem writing to debug file: " + file);
      }
   }
   
   public static void print(String msg) {
      if (enabled) {
         if (ofp == null) init();
         
         StackTraceElement[] trace = new Throwable().getStackTrace();
         String output;
         if (trace.length >= 1) {
            StackTraceElement elt = trace[1];
            output = ">>>" + elt.getFileName() + ": " + elt.getClassName() + "." +
               elt.getMethodName() + "(line " + elt.getLineNumber() + "): " + msg;
         } else {
            output = ">>> [UNKNOWN CALLER]: " + msg;
         }
         System.out.println(output);
         if (ofp != null) {
            try {
               ofp.write(output + "\n");
            } catch (IOException e) {
               e.printStackTrace();
            }
         }
      }
   }
   
   public static void close() {
      if (ofp != null) {
         try {
            ofp.close();
         } catch (IOException e) {
            e.printStackTrace();
         }
      }
   }
}
