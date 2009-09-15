package com.tivo.kmttg.main;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Stack;

import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class autoConfig {
   public static Stack<autoEntry> KEYWORDS = new Stack<autoEntry>();
   public static int dryrun = 0;
   public static int CHECK_TIVOS_INTERVAL = 60;
   public static Stack<String> ignoreHistory = new Stack<String>();
   public static int dateFilter = 0;
   public static String dateOperator = "less than";
   public static float dateHours = 48;
   
   public static Boolean parseAuto(String config) {
      debug.print("config=" + config);
      if ( ! file.isFile(config) ) {
         // Create empty file
         if ( ! file.create(config) ) {
            return false;
         }
      }
      KEYWORDS.clear();
      ignoreHistory.clear();
            
      try {
         BufferedReader ini = new BufferedReader(new FileReader(config));
         String line = null;
         String key = null;
         autoEntry entry = new autoEntry();
         while (( line = ini.readLine()) != null) {
            // Get rid of leading and trailing white space
            line = line.replaceFirst("^\\s*(.*$)", "$1");
            line = line.replaceFirst("^(.*)\\s*$", "$1");
            if (line.length() == 0) continue; // skip empty lines
            if (line.matches("^#.+")) continue; // skip comment lines
            if (line.matches("^<.+>")) {
               key = line.replaceFirst("<", "");
               key = key.replaceFirst(">", "");
               continue;
            }
            
            // Exact title match
            if (key.equals("title")) {
               if ( entry.type != null ) {
                  debug.print("entry=" + entry);
                  KEYWORDS.add(entry);
                  entry = new autoEntry();
               }
               entry.type = "title";
               entry.keyword = line;
            }
            
            // Keyword entries
            // Multiple | separated entries allowed
            // No brackets => AND
            // Brackets => OR
            // - prepend => NOT
            if (key.equals("keywords")) {
               if ( entry.type != null ) {
                  debug.print("entry=" + entry);
                  KEYWORDS.add(entry);
                  entry = new autoEntry();
               }
               entry.type = "keywords";
               stringToKeywords(line, entry);
           }
               
            if (key.equals("check_tivos_interval")) {
               CHECK_TIVOS_INTERVAL = (int)Float.parseFloat(line);
            }
            if (key.equals("dryrun")) {
               dryrun = Integer.parseInt(line);
            }
            if (key.equals("dateFilter")) {
               dateFilter = Integer.parseInt(line);
            }
            if (key.equals("dateOperator")) {
               dateOperator = line;
            }
            if (key.equals("dateHours")) {
               dateHours = Float.parseFloat(line);
            }
            if (key.equals("options")) {
               String[] l = line.split("\\s+");
               String name = l[0];
               String value = l[1];
               if (name.matches("metadata"))
                  entry.metadata = Integer.parseInt(value);
               if (name.matches("decrypt"))
                  entry.decrypt = Integer.parseInt(value);
               if (name.matches("qsfix"))
                  entry.qsfix = Integer.parseInt(value);
               if (name.matches("comskip"))
                  entry.comskip = Integer.parseInt(value);
               if (name.matches("comcut"))
                  entry.comcut = Integer.parseInt(value);
               if (name.matches("captions"))
                  entry.captions = Integer.parseInt(value);
               if (name.matches("encode"))
                  entry.encode = Integer.parseInt(value);
               if (name.matches("custom"))
                  entry.custom = Integer.parseInt(value);
               if (name.matches("encode_name"))
                  entry.encode_name = value;
            }
            if (key.equals("ignorehistory")) {
               ignoreHistory.add(line);
            }
         }
         if ( entry.type != null ) {
            debug.print("entry=" + entry);
            KEYWORDS.add(entry);
         }

         ini.close();         
      }         
      catch (IOException ex) {
         log.error("Problem parsing config file: " + config);
         return false;
      }
      
      return true;
   }
   
   public static void stringToKeywords(String line, autoEntry entry) {
      debug.print("line=" + line + " entry=" + entry);
      entry.keywords.clear();
      String[] l = line.split("\\|");
      for (int i=0; i<l.length; ++i)
         entry.keywords.add(l[i]);
   }
   
   
   // Convert given keyword set to a displayable form in table
   public static String keywordsToString(Stack<String> k) {
      debug.print("k=" + k);
      String keywords = "";
      if (k.size() > 0) {
         for (int i=0; i<k.size(); i++)
            keywords += k.get(i) + "|";
         keywords = keywords.replaceFirst("\\|$", "");
      }
      return keywords;
   }

}
