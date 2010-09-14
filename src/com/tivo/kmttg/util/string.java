package com.tivo.kmttg.util;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
         return "";
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
      debug.print("s=" + s);
      try {
         return(URLDecoder.decode(s, "UTF-8"));
      } catch (UnsupportedEncodingException e) {
         log.error(e.getMessage());
         return s;
      }
   }
   
   // ISO 8601 format duration to milliseconds
   public static Long isoDurationToMsecs(String isodur)  {
      debug.print("isodur=" + isodur);
      int plusMinus = 1;
      double days = -1.0;
      double hours = -1.0;
      double minutes = -1.0;
      double seconds = -1.0;
      boolean isTime = false;
      String value = null;
      String delim = null;

      // DURATION FORMAT IS: (-)PnYnMnDTnHnMnS
      StringTokenizer st = new StringTokenizer(isodur, "-PYMDTHS", true);

      // OPTIONAL SIGN
      value = st.nextToken();

      if (value.equals("-")) {
         plusMinus= -1;
         value=st.nextToken();
      }

      // DURATION MUST START WITH A "P"
      if (!value.equals("P")) {
         log.error("Invalid isodur string: " + isodur);
         return null;                                 
      }

      // GET NEXT FIELD
      while (st.hasMoreTokens()) {
         // VALUE
         value = new String(st.nextToken());
         if (value.equals("T")) {
            if (!st.hasMoreTokens()) {
               log.error("Invalid isodur string: " + isodur);
               return null;                  
            }
            value = st.nextToken();
            isTime = true;
         }

         // DELIMINATOR
         if (!st.hasMoreTokens()) {
            log.error("Invalid isodur string: " + isodur);
            return null;                                 
         }
         delim = new String(st.nextToken());

         // DAYS
         if (delim.equals("D")) {
            days = Double.parseDouble(value);
         }

         // HOURS
         else if (delim.equals("H")) {
            hours = Double.parseDouble(value);
            isTime = true;
         }

         // MINUTES
         else if (delim.equals("M") && isTime == true) {
            minutes = Double.parseDouble(value);
         }

         // SECONDS
         else if (delim.equals("S")) {
            seconds = Double.parseDouble(value);
         }
         else  {
            log.error("Invalid isodur string: " + isodur);
            return null;                                 
         }
      }
      
      Long msecs = Long.parseLong("0");
      if (days > 0)
         msecs += new Double(days*24*60*60*1000).longValue();
      if (hours > 0)
         msecs += new Double(hours*60*60*1000).longValue();
      if (minutes > 0)
         msecs += new Double(minutes*60*1000).longValue();
      if (seconds > 0)
         msecs += new Double(seconds*1000).longValue();
      
      return msecs * plusMinus;
   }
      
   // For given url strip off port if already there, then
   // add given port as part of url
   public static String addPort(String url, String port) {
      url = url.replaceFirst(":[0-9]+/", "/");
      Pattern p = Pattern.compile("^.+://(.+?)/.+");
      Matcher m = p.matcher(url);
      if (m.matches()) {
         String ip = m.group(1);
         System.out.println("ip=" + ip);
         url = url.replaceFirst(ip, ip + ":" + port);
      }
      return url;
   }

}
