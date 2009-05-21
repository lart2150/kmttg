package com.tivo.kmttg.util;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

public class string {
   
   public static String basename(String name) {
      debug.print("name=" + name);
      String s = File.separator;
      if (s.equals("\\")) s = "\\\\";
      String[] l = name.split(s);
      return l[l.length-1];
   }
   
   public static String dirname(String name) {
      debug.print("name=" + name);
      String s = File.separator;
      if (s.equals("\\")) s = "\\\\";
      String[] l = name.split(s);
      if (l.length > 1) {
         String dir = "";
         for (int i=0; i<l.length-1; i++) {
            if (i>0)
               dir += File.separator + l[i];
            else
               dir += l[i];
         }
         return dir;
      } else {
         return name;
      }            
   }

   public static String replaceSuffix(String name, String suffix) {
      if (name.matches("^.+\\..+$"))
         return name.replaceFirst("^(.+)\\..+$", "$1" + suffix);
      else
         return name;
   }
   
   public static String removeLeadingTrailingSpaces(String s) {
      // Remove leading & traling spaces from name
      s = s.replaceFirst("^\\s*", "");
      s = s.replaceFirst("\\s*$", "");
      return s;
   }
      
   public static String urlDecode(String s) {
      try {
         return(URLDecoder.decode(s, "UTF-8"));
      } catch (UnsupportedEncodingException e) {
         log.error(e.getMessage());
         return s;
      }
   }

}
