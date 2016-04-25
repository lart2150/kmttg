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
package com.tivo.kmttg.httpserver;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class Hlsutils {
   private static String m3u8_terminator = "#EXT-X-ENDLIST";
   
   public static String getTextFileContents(String textFile) {
      if ( ! file.isFile(textFile) )
         return "";
      String text = "";
      try {
         Scanner s = new Scanner(new File(textFile));
         text = s.useDelimiter("\\A").next();
         s.close();
      } catch (Exception e) {}
      return text;
   }
   
   public static Boolean isPartial(String m3u8) {
      Boolean partial = true;
      String contents = getTextFileContents(m3u8);
      if ( contents.contains(m3u8_terminator) )
         partial = false;
      return partial;
   }
   
   public static void fixPartial(String m3u8) {
      try {
      BufferedWriter ofp = new BufferedWriter(new FileWriter(m3u8, true));
      ofp.write(m3u8_terminator + "\r\n");
      ofp.close();
      } catch (Exception e) {
         log.error("fixPartial - " + e.getMessage());
      }
   }
   
   // For .m3u8 file sum up the total time processed
   // Simply sums all lines of following type: #EXTINF:12.078733,
   public static float totalTime_m3u8(String fileName) {
      float total = 0;
      String text = getTextFileContents(fileName);
      String[] lines = text.split("\n");
      for (String line : lines) {
         if (line.contains("#EXTINF")) {
            line = line.replaceFirst("#EXTINF:", "");
            line = line.replace(",", "");
            total += Float.parseFloat(line);
         }
      }
      return total;
   }
   
   // For webm ffmpeg run try and get latest processed time
   // Sample line: frame= 1948 fps= 90 q=0.0 size=    9598kB time=00:01:04.99 bitrate=1209.6kbits/s
   public static float totalTime_webm(Transcode tc) {
      float total = 0;
      String line = null;
      if (tc.process == null) {
         line = tc.lastStderr;
      } else {
         line = tc.process.getStderrLast();
      }
      if (line != null && line.contains("time=")) {
         String[] l = line.split("time=");
         String[] ll = l[l.length-1].split("\\s+");
         try {
            if (ll[0].contains(":")) {
               // "HH:MM:SS.MS" format
               Pattern p = Pattern.compile("(\\d+):(\\d+):(\\d+).(\\d+)");
               Matcher m = p.matcher(ll[0]);
               if (m.matches()) {
                  long HH = Long.parseLong(m.group(1));
                  long MM = Long.parseLong(m.group(2));
                  long SS = Long.parseLong(m.group(3));
                  long MS = Long.parseLong(m.group(4));
                  float ms = (MS + 1000*(SS+60*MM+60*60*HH))/(float)1000.0;
                  return ms;
               }

            } else {
               return Float.parseFloat(ll[0]);
            }
         }
         catch (NumberFormatException n) {}
      }
      return total;
   }
   
   public static boolean isVideoFile(String fileName) {
      String[] extensions = {
         "mp4","mpeg","vob","mpg","mpeg2","mp2","avi","wmv",
         "asf","mkv","tivo","m4v","3gp","mov","flv","ts"
      };
      boolean videoFile = false;
      for (String extension : extensions) {
         if (fileName.toLowerCase().endsWith("." + extension))
            videoFile = true;
      }
      return videoFile;
   }

}
