package com.tivo.kmttg.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.main.config;

public class pyTivo {
   
   public static Stack<Hashtable<String,String>> parsePyTivoConf(String conf) {
      Stack<Hashtable<String,String>> s = new Stack<Hashtable<String,String>>();
      String username = null;
      String password = null;
      String mind = null;
      
      try {
         BufferedReader ifp = new BufferedReader(new FileReader(conf));
         String line = null;
         String key = null;
         Hashtable<String,String> h = new Hashtable<String,String>();
         while (( line = ifp.readLine()) != null) {
            // Get rid of leading and trailing white space
            line = line.replaceFirst("^\\s*(.*$)", "$1");
            line = line.replaceFirst("^(.*)\\s*$", "$1");
            if (line.length() == 0) continue; // skip empty lines
            if (line.matches("^#.+")) continue; // skip comment lines
            if (line.matches("^\\[.+\\]")) {
               key = line.replaceFirst("\\[", "");
               key = key.replaceFirst("\\]", "");
               if ( ! h.isEmpty() ) {
                  if (h.containsKey("share") && h.containsKey("path")) {
                     s.add(h);
                  }
                  h = new Hashtable<String,String>();
               }
               continue;               
            }
            if (key == null) continue;
            
            if (key.equalsIgnoreCase("server")) {
               if (line.matches("(?i)^port\\s*=.+")) {
                  String[] l = line.split("=");
                  if (l.length > 1) {
                     config.pyTivo_port = string.removeLeadingTrailingSpaces(l[1]);
                  }
               }
               if (line.matches("(?i)^tivo_username\\s*=.+")) {
                  String[] l = line.split("=");
                  if (l.length > 1) {
                     username = string.removeLeadingTrailingSpaces(l[1]);
                  }
               }
               if (line.matches("(?i)^tivo_password\\s*=.+")) {
                  String[] l = line.split("=");
                  if (l.length > 1) {
                     password = string.removeLeadingTrailingSpaces(l[1]);
                  }
               }
               if (line.matches("(?i)^tivo_mind\\s*=.+")) {
                  String[] l = line.split("=");
                  if (l.length > 1) {
                     mind = string.removeLeadingTrailingSpaces(l[1]);
                  }
               }
               continue;
            }
            if (line.matches("(?i)^type\\s*=.+")) {
               if (line.matches("(?i)^.+=\\s*video.*")) {
                  if ( ! h.containsKey("share") ) {
                     h.put("share", key);
                  }
               }
               continue;
            }
            if (line.matches("(?i)^path\\s*=.+")) {
               String[] l = line.split("=");
               if (l.length > 1) {
                  String p = lowerCaseVolume(string.removeLeadingTrailingSpaces(l[1]));
                  char separator = File.separator.charAt(0);
                  if (p.charAt(p.length()-1) == separator) {
                     // Remove extra ending file separator
                     p = p.substring(0, p.length()-1);
                  }
                  h.put("path", p);
               }
            }
         }
         ifp.close();
         if ( ! h.isEmpty() ) {
            if (h.containsKey("share") && h.containsKey("path")) {
               s.add(h);
            }
         }
         
         // tivo_username & tivo_password are required for pushes to work
         if (username == null) {
            log.error("Required 'tivo_username' is not set in pyTivo config file: " + conf);
         }
         if (password == null) {
            log.error("Required 'tivo_password' is not set in pyTivo config file: " + conf);
         }
         if (username == null || password == null) {
            return null;
         }
         config.pyTivo_username = username;
         config.pyTivo_password = password;
         if (mind != null)
            config.pyTivo_mind = mind;

      }
      catch (Exception ex) {
         log.error("Problem parsing pyTivo config file: " + conf);
         log.error(ex.toString());
         return null;
      }
      
      return s;
   }
   
   // For Windows lowercase file volume
   public static String lowerCaseVolume(String fileName) {
      String lowercased = fileName;
      if (config.OS.equals("windows")) {
         if (fileName.matches("^(.+):.*$") ) {
            String[] l = fileName.split(":");
            if (l.length > 0) {
               lowercased = l[0].toLowerCase() + ":";
               for (int i=1; i<l.length; i++) {
                  lowercased += l[i];
               }
            }
         }
      }
      return lowercased;
   }


}
