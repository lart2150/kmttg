package com.tivo.kmttg.httpserver;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Scanner;

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
