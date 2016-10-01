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

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Hashtable;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tivo.kmttg.util.createMeta;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

// Build tivo file name based on certain keyword/specs
public class tivoFileName {
   
   public static String buildTivoFileName(Hashtable<String,String> entry) {
      String tivoFileNameFormat = config.tivoFileNameFormat;
      // File naming override possible from auto transfers setup
      if (entry.containsKey("tivoFileNameFormat"))
         tivoFileNameFormat = entry.get("tivoFileNameFormat");
      if ( tivoFileNameFormat == null )
         tivoFileNameFormat = "[title] ([monthNum]_[mday]_[year])";
      String file = tivoFileNameFormat + ".TiVo";
      
      // Breakdown gmt to local time components
      Hashtable<String,String> keys = new Hashtable<String,String>();
      SimpleDateFormat sdf = new SimpleDateFormat("mm HH dd MM E MMM yyyy");
      long gmt = Long.parseLong(entry.get("gmt"));
      String format = sdf.format(gmt);
      String[] t = format.split("\\s+");
      keys.put("min",      t[0]);
      keys.put("hour",     t[1]);
      keys.put("mday",     t[2]);
      keys.put("monthNum", t[3]);
      keys.put("wday",     t[4]);
      keys.put("month",    t[5]);
      keys.put("year",     t[6]);
      
      // If startTime is desired then need extended metadata
      // which has real program start time (for partial records)
      keys.put("startTime", "");
      keys.put("season", "");
      keys.put("episode", "");
      if (tivoFileNameFormat.contains("startTime") && entry.containsKey("tivoName")) {
         createMeta.getExtendedMetadata(entry.get("tivoName"), entry, true);
         if (entry.containsKey("startTime"))
            keys.put("startTime", entry.get("startTime"));
      }

      if ( ! entry.containsKey("EpisodeNumber") ) entry.put("EpisodeNumber", "");
      // Split up into season & episode components (assuming episode # is 2 digits)
      String epnum = entry.get("EpisodeNumber");
      String SeriesEpNumber="";
      if (entry.containsKey("season") && entry.containsKey("episode")) {
         SeriesEpNumber = "s" + entry.get("season") + "e" + entry.get("episode");
      } else {
         if (epnum.length() > 2) {
            String s="", e="";
            if (epnum.length() <= 3) {
               s = epnum.substring(0,1);
               e = epnum.substring(1);
            } else {
               s = epnum.substring(0,2);
               e = epnum.substring(2);
            }
            if (s.length() == 1)
               s = "0" + s;
            if (s.length() > 0 && e.length() > 0) {
               SeriesEpNumber = "s" + s + "e" + e;
               entry.put("season", s);
               entry.put("episode", e);
            }
         }
      }
      entry.put("SeriesEpNumber", SeriesEpNumber);
      
      // Enter values for these names into keys hash
      String[] names = {
         "title", "titleOnly", "episodeTitle", "channelNum", "channel",
         "EpisodeNumber", "SeriesEpNumber", "season", "episode", "description",
         "tivoName", "originalAirDate", "movieYear"
      };
      for (String name : names) {
         if (entry.containsKey(name)) {
            keys.put(name, entry.get(name));
         } else {
            keys.put(name, "");
         }
      }
      // If originalAirDate is empty then use [year]_[monthNum]_[mday] instead
      if (keys.get("originalAirDate").length() == 0) {
         keys.put("originalAirDate", keys.get("year") + "-" + keys.get("monthNum") + "-" + keys.get("mday"));
      }
      keys.put("oad_no_dashes", keys.get("originalAirDate").replaceAll("-", ""));
      
      // Special keyword "[/]" means use sub-folders
      file = file.replaceAll("\\[/\\]", "__separator__");
      
      // Keyword handling
      // Syntax can be: [keyword] or ["text" keyword "text" ...]
      String n = "";
      char[] chars = file.toCharArray();
      for (int i=0; i<chars.length; ++i) {
         if (chars[i] == '[') {
            Hashtable<String,Object> h = parseKeyword(chars, i, keys);
            int delta = (Integer)h.get("delta");
            String text = (String)h.get("text");
            n += text;
            i += delta;
         } else {
            n += chars[i];
         }
      }
      file = n;
      
      // Remove/replace certain special characters
      file = removeSpecialChars(file);
      
      // Don't allow folders ending in dots or white space
      while (file.matches("^.+[\\.|\\s+]__separator__.*$")) {
         file = file.replaceAll("[\\.|\\s+]__separator__", "__separator__");
      }
      
      // Deal with separators
      String s = File.separator;
      s = s.replaceFirst("\\\\", "\\\\\\\\");
      String[] l = file.split("__separator__");
      if (l.length > 0) {
         file = file.replaceAll("__separator__", s);
      }
            
      debug.print("buildTivoFileName::file=" + file);
      
      // Check file name to make sure basename is not empty
      // If empty print error and return null
      if (string.basename(file).equals(".TiVo")) {
         log.error("File naming template resulted in empty file base name for this entry: " + entry.toString());
         return null;
      }
      
      return file;
   }
   
   // Keyword handling
   // Syntax can be: [keyword] or ["text" keyword "text" ...]
   // Quoted text => conditional literal text (only display if keyword evaluates to non-nil)
   // Unquoted text => keyword to evaluate
   private static Hashtable<String,Object> parseKeyword(char[] chars, int offset, Hashtable<String,String> keys) {
      char[] text_orig = chars;
      int length = text_orig.length;
      String keyword = "";
      int delta = 0;
      for (int j=offset; j<length; ++j) {
         keyword += text_orig[j];
         if (text_orig[j] == ']') break;
         delta++;
      }
      keyword = keyword.replaceFirst("\\[", "");
      keyword = keyword.replaceFirst("\\]", "");
      
      // Need to preserve spaces inside quotes before splitting
      Pattern p = Pattern.compile("\"(.*?)\"");
      Matcher m = p.matcher(keyword);
      StringBuffer sb = new StringBuffer();
      while (m.find()) {
         m.appendReplacement(sb, m.group().replaceAll("\\s", "___space___"));
      }
      m.appendTail(sb);
      keyword = sb.toString();

      String[] fields = keyword.split("\\s+");
      Stack<String>newFields = new Stack<String>();
      Boolean exists = true;
      for (String text : fields) {
         // Put spaces back in that we previously eliminated
         text = text.replaceAll("___space___", " ");
         if (text.contains("\"")) {
            text = text.replaceAll("\"", "");
         } else {
            String[] keywords = {
               "title", "mainTitle", "episodeTitle", "channelNum", "channel",
               "min", "hour", "wday", "mday", "month", "monthNum", "year",
               "startTime", "SeriesEpNumber", "season", "episode", "EpisodeNumber",
               "description", "tivoName", "originalAirDate", "oad_no_dashes", "movieYear"
            };
            for (String k : keywords) {
               String replacement = k;
               if (k.equals("mainTitle")) replacement = "titleOnly";
               text = text.replaceFirst("^" + k + "$", removeSpecialChars(keys.get(replacement)));
            }
            if (text.length() == 0) exists = false;
         }
         newFields.add(text);
      }
      String text = "";
      if (exists) {
         for (int i=0; i<newFields.size(); ++i) {
            text += newFields.get(i);
         }
      }
      debug.print("parseKeyword::text=" + text);
      Hashtable<String,Object> l = new Hashtable<String,Object>();
      l.put("delta", delta);
      l.put("text", text);
      return l;
   }
   
   public static String removeSpecialChars(String s) {
      //s = s.replaceAll("\\s+","_");
      s = s.replaceAll("/", "_");
      s = s.replaceAll("\\*", "");
      s = s.replaceAll("\"", "");
      s = s.replaceAll("'", "");
      s = s.replaceAll(":", "");
      s = s.replaceAll(";", "");
      //s = s.replaceAll("-", "_");
      s = s.replaceAll("!", "");
      s = s.replaceAll("\\?", "");
      s = s.replaceAll("&", "and");
      s = s.replaceAll("\\\\", "");
      s = s.replaceAll("\\$", "");
      s = s.replaceAll(">", "_");
      s = s.replaceAll("<", "_");
      s = s.replaceAll("\\|", "_");
      s = s.replaceAll("`", "");
      s = s.replaceAll("^", "");
      return s;
   }

}
