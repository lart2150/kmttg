package com.tivo.kmttg.main;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Hashtable;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

// Build tivo file name based on certain keyword/specs
public class tivoFileName {
   
   public static String buildTivoFileName(Hashtable<String,String> entry) {      
      if ( config.tivoFileNameFormat == null ) {
         config.tivoFileNameFormat = "[title] ([monthNum]_[mday]_[year])";
      }
      String file = config.tivoFileNameFormat + ".TiVo";
      
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
      if ( ! entry.containsKey("EpisodeNumber") ) entry.put("EpisodeNumber", "");
      
      // Enter values for these names into keys hash
      String[] names = {
         "title", "titleOnly", "episodeTitle", "channelNum", "channel",
         "EpisodeNumber", "description", "tivoName", "originalAirDate"
      };
      for (int i=0; i<names.length; ++i) {
         if (entry.containsKey(names[i])) {
            keys.put(names[i], entry.get(names[i]));
         } else {
            keys.put(names[i], "");
         }
      }
      // If originalAirDate is empty then use [year]_[monthNum]_[mday] instead
      if (keys.get("originalAirDate").length() == 0) {
         keys.put("originalAirDate", keys.get("year") + "-" + keys.get("monthNum") + "-" + keys.get("mday"));
      }
      
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
      String text;
      for (int i=0; i<fields.length; i++) {
         text = fields[i];
         // Put spaces back in that we previously eliminated
         text = text.replaceAll("___space___", " ");
         if (text.contains("\"")) {
            text = text.replaceAll("\"", "");
         } else {
            text = text.replaceFirst("^title$",           removeSpecialChars(keys.get("title")));
            text = text.replaceFirst("^mainTitle$",       removeSpecialChars(keys.get("titleOnly")));
            text = text.replaceFirst("^episodeTitle$",    removeSpecialChars(keys.get("episodeTitle")));
            text = text.replaceFirst("^channelNum$",      removeSpecialChars(keys.get("channelNum")));
            text = text.replaceFirst("^channel$",         removeSpecialChars(keys.get("channel")));
            text = text.replaceFirst("^min$",             removeSpecialChars(keys.get("min")));
            text = text.replaceFirst("^hour$",            removeSpecialChars(keys.get("hour")));
            text = text.replaceFirst("^wday$",            removeSpecialChars(keys.get("wday")));
            text = text.replaceFirst("^mday$",            removeSpecialChars(keys.get("mday")));
            text = text.replaceFirst("^month$",           removeSpecialChars(keys.get("month")));
            text = text.replaceFirst("^monthNum$",        removeSpecialChars(keys.get("monthNum")));
            text = text.replaceFirst("^year$",            removeSpecialChars(keys.get("year")));
            text = text.replaceFirst("^EpisodeNumber$",   removeSpecialChars(keys.get("EpisodeNumber")));
            text = text.replaceFirst("^description$",     removeSpecialChars(keys.get("description")));
            text = text.replaceFirst("^tivoName$",        removeSpecialChars(keys.get("tivoName")));
            text = text.replaceFirst("^originalAirDate$", removeSpecialChars(keys.get("originalAirDate")));
            if (text.length() == 0) exists = false;
         }
         newFields.add(text);
      }
      text = "";
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
   
   private static String removeSpecialChars(String s) {
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
