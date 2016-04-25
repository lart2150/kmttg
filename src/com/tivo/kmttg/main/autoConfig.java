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
package com.tivo.kmttg.main;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class autoConfig {
   public static Stack<autoEntry> KEYWORDS = new Stack<autoEntry>();
   public static int noJobWait = 0;
   public static int dryrun = 0;
   public static int CHECK_TIVOS_INTERVAL = 60;
   public static int dateFilter = 0;
   public static String dateOperator = "less than";
   public static float dateHours = 48;
   public static int suggestionsFilter = 0;
   public static int kuidFilter = 0;
   public static int programIdFilter = 0;
   
   public static Boolean parseAuto(String config) {
      debug.print("config=" + config);
      if ( ! file.isFile(config) ) {
         // Create empty file
         if ( ! file.create(config) ) {
            return false;
         }
      }
      KEYWORDS.clear();
            
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
            if (key.equals("noJobWait")) {
               noJobWait = Integer.parseInt(line);
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
            if (key.equals("suggestionsFilter")) {
               suggestionsFilter = Integer.parseInt(line);
            }
            if (key.equals("kuidFilter")) {
               kuidFilter = Integer.parseInt(line);
            }
            if (key.equals("programIdFilter")) {
               programIdFilter = Integer.parseInt(line);
            }
            if (key.equals("options")) {
               String[] l = line.split("\\s+");
               String name = l[0];
               String value = l[1];
               if (name.matches("tivo")) {
                  // tivo value can have spaces
                  Pattern p = Pattern.compile("(\\S+)\\s+(.+)");
                  Matcher m = p.matcher(line);
                  if (m.matches()) {
                     entry.tivo = m.group(2);
                  }
               }
               if (name.matches("comskipIni")) {
                  // comskipIni value can have spaces
                  Pattern p = Pattern.compile("(\\S+)\\s+(.+)");
                  Matcher m = p.matcher(line);
                  if (m.matches()) {
                     entry.comskipIni = m.group(2);
                  }
               }
               if (name.matches("tivoFileNameFormat")) {
                  // tivoFileNameFormat value can have spaces
                  Pattern p = Pattern.compile("(\\S+)\\s+(.+)");
                  Matcher m = p.matcher(line);
                  if (m.matches()) {
                     entry.tivoFileNameFormat = m.group(2);
                  }
               }
               if (name.matches("enabled"))
                  entry.enabled = Integer.parseInt(value);
               if (name.matches("metadata"))
                  entry.metadata = Integer.parseInt(value);
               if (name.matches("decrypt"))
                  entry.decrypt = Integer.parseInt(value);
               if (name.matches("qsfix"))
                  entry.qsfix = Integer.parseInt(value);
               if (name.matches("twpdelete"))
                  entry.twpdelete = Integer.parseInt(value);
               if (name.matches("rpcdelete"))
                  entry.rpcdelete = Integer.parseInt(value);
               if (name.matches("comskip"))
                  entry.comskip = Integer.parseInt(value);
               if (name.matches("comcut"))
                  entry.comcut = Integer.parseInt(value);
               if (name.matches("captions"))
                  entry.captions = Integer.parseInt(value);
               if (name.matches("encode"))
                  entry.encode = Integer.parseInt(value);
               if (name.matches("push"))
                  entry.push = Integer.parseInt(value);
               if (name.matches("custom"))
                  entry.custom = Integer.parseInt(value);
               if (name.matches("suggestionsFilter"))
                  entry.suggestionsFilter = Integer.parseInt(value);
               if (name.matches("useProgramId_unique"))
                  entry.useProgramId_unique = Integer.parseInt(value);
               if (name.matches("channelFilter")) {
                  entry.channelFilter = value;
               }
               if (name.matches("encode_name")) {
                  // encode_name value can have spaces
                  Pattern p = Pattern.compile("(\\S+)\\s+(.+)");
                  Matcher m = p.matcher(line);
                  if (m.matches()) {
                     entry.encode_name = m.group(2);
                  }
               }
               if (name.matches("encode_name2")) {
                   // encode_name value can have spaces
                   Pattern p = Pattern.compile("(\\S+)\\s+(.+)");
                   Matcher m = p.matcher(line);
                   if (m.matches()) {
                      entry.encode_name2 = m.group(2);
                   }
                }
               if (name.matches("encode_name2_suffix")) {
                   // encode_name value can have spaces
                   Pattern p = Pattern.compile("(\\S+)\\s+(.+)");
                   Matcher m = p.matcher(line);
                   if (m.matches()) {
                      entry.encode_name2_suffix = m.group(2);
                   }
                }
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
