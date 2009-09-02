package com.tivo.kmttg.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

public class debug {
   private static String file = null;
   private static BufferedWriter ofp = null;
   public static Boolean enabled = false;
   
   private static void init() {
      String dir = new File(
         debug.class.getProtectionDomain().getCodeSource().getLocation().getPath()
      ).getParent();
      dir = urlDecode(dir);

      file = dir + File.separator + "debug.log";
      try {
         ofp = new BufferedWriter(new FileWriter(file));
      }
      catch (IOException ex) {
         System.out.println("Problem writing to debug file: " + file);
      }
   }
   
   private static String urlDecode(String s) {
      try {
         return(URLDecoder.decode(s, "UTF-8"));
      } catch (UnsupportedEncodingException e) {
         System.out.println(e.getMessage());
         return s;
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
