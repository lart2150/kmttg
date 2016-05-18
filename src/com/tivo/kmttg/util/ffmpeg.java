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
package com.tivo.kmttg.util;

import java.util.Hashtable;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tivo.kmttg.main.config;

public class ffmpeg {
   
   // Use ffmpeg to get video dimensions from given mpeg video file
   // Returns null if undetermined, a hash with x, y members otherwise
   // Also grabs Display Aspect Ratio numbers if available
   public static Hashtable<String,String> getVideoInfo(String videoFile) {
      if (! file.isFile(config.ffmpeg))
         return null;
      // Use ffmpeg command to get video information      
      Stack<String> command = new Stack<String>();
      command.add(config.ffmpeg);
      command.add("-i");
      command.add(videoFile);
      backgroundProcess process = new backgroundProcess();
      if ( process.run(command) ) {
         // Wait for command to terminate
         process.Wait();
         
         // Parse stderr
         Stack<String> l = process.getStderr();
         if (l.size() > 0) {
            Hashtable<String,String> info = new Hashtable<String,String>();
            String line;
            info.put("container", "mpeg");
            info.put("video", "mpeg2video");
            for (int i=0; i<l.size(); ++i) {
               line = l.get(i);
               if (line.matches("^Input.+$")) {
                  line = line.replaceFirst("from.+$", "");
                  String fields[] = line.split("\\s+");
                  String container = fields[2];
                  container = container.replaceAll(",", "");
                  info.put("container", container);
                  if (line.contains("mpegts"))
                     info.put("container", "mpegts");
                  if (line.contains("mp4"))
                     info.put("container", "mp4");
               }
               if (line.matches("^.+\\s+Video:\\s+.+$")) {
                  Pattern p = Pattern.compile(".*Video: (.+), (\\d+)x(\\d+)[, ].*");
                  Matcher m = p.matcher(line);
                  if (m.matches()) {
                     String video = m.group(1);
                     video = video.replaceFirst("\\s+.+$", "");
                     info.put("video", video);
                     if (line.contains("mpeg2video"))
                        info.put("video", "mpeg2video");
                     if (line.contains("h264"))
                        info.put("video", "h264");
                     info.put("x", m.group(2));
                     info.put("y", m.group(3));
                     p = Pattern.compile(".*Video: .+\\s+DAR\\s+(\\d+):(\\d+).*");
                     m = p.matcher(line);
                     if (m.matches()) {
                        info.put("DAR_x", m.group(1));
                        info.put("DAR_y", m.group(2));
                     }
                  }
               }
               if (line.matches("^\\s+Duration.+$")) {
                  String fields[] = line.split("\\s+");
                  if (fields.length > 2) {
                     String t[] = fields[2].split(":");
                     if (t.length > 2) {
                        int h = Integer.parseInt(t[0]);
                        int m = Integer.parseInt(t[1]);
                        int s = Integer.parseInt(t[2].replaceFirst("\\..+", ""));
                        int duration = 60*60*h + 60*m + s;
                        info.put("duration", "" + duration);
                     }
                  }
               }
            }
            if (info.size() == 0)
               info = null;
            return info;
         }
      }
      return null;
   }
   
   // Compute and return other output dimension based on 1 known output dimension and DAR of an input videoFile
   // i.e. If output_known is "width" then return computed height, else if "height" return computed width
   // Return 0 on failure
   private static int computeOutputDimensions(String videoFile, String output_known, int output_dim) {
      Hashtable<String,String> source_info;
      if (file.isFile(config.mediainfo)) {
         source_info = mediainfo.getVideoInfo(videoFile);
      } else {
         source_info = getVideoInfo(videoFile);
      }
      if (source_info == null) {
         // Try once again in case of transient issue
         log.warn("2nd try to obtain video file dimensions from file: " + videoFile);
         source_info = getVideoInfo(videoFile);
      }
      if (source_info == null) {
         log.error("Failed to determine video dimensions from video file: " + videoFile);
      } else {
         int DAR_x=0, DAR_y=0;
         // Use detected DAR if available
         if (source_info.containsKey("DAR_x")) {
            DAR_x = Integer.parseInt(source_info.get("DAR_x"));
            DAR_y = Integer.parseInt(source_info.get("DAR_y"));
         }
         
         // Assume DAR = source video dimensions if not found from videoFile
         if (source_info.containsKey("x") && source_info.containsKey("y")) {
            if (DAR_x == 0) DAR_x = Integer.parseInt(source_info.get("x"));
            if (DAR_y == 0) DAR_y = Integer.parseInt(source_info.get("y"));
            
            if (output_known.equals("width")) {              
               int output_height = output_dim*DAR_y/DAR_x;
               // Want even number
               if (output_height % 2 != 0) output_height += 1;
               return output_height;
            }
            
            if (output_known.equals("height")) {              
               int output_width = output_dim*DAR_x/DAR_y;
               // Want even number
               if (output_width % 2 != 0) output_width += 1;
               return output_width;
            }
         }
      }
      return 0;
   }
   
   // Examine given ffmpeg encoding string and look for ###xHEIGHT or WIDTHx### keywords
   // indicating that height or width needs to be computed based on DAR of input video file.
   // If keywords found then replace given encodeCommand stack with adjusted one, else return original.
   // A sample command line with keyword is:
   //FFMPEG -y -i INPUT -threads CPU_CORES -vcodec mpeg4 -s 640xHEIGHT -f mp4 OUTPUT
   public static Stack<String> getOutputDimensions(String inputFile, Stack<String> encodeCommand) {
      Pattern p;
      Matcher m;
      String s;
      Stack<String> newCommand = new Stack<String>();
      
      for (int i=0; i<encodeCommand.size(); ++i) {
         s = encodeCommand.get(i);
         
         p = Pattern.compile("^(\\d+)xHEIGHT$");
         m = p.matcher(s);
         if (m.matches()) {
            int width = Integer.parseInt(m.group(1));
            int height = computeOutputDimensions(inputFile, "width", width);
            if (height == 0) {
               return encodeCommand;
            } else {
               s = "" + width + "x" + height;
               log.warn("Computed resolution to use for output file = " + s);
            }
         }
         
         p = Pattern.compile("^WIDTHx(\\d+)$");
         m = p.matcher(s);
         if (m.matches()) {
            int height = Integer.parseInt(m.group(1));
            int width = computeOutputDimensions(inputFile, "height", height);
            if (width == 0) {
               return encodeCommand;
            } else {
               s = "" + width + "x" + height;
               log.warn("Computed resolution to use for output file = " + s);
            }
         }
         
         newCommand.add(s);
      }
      
      return newCommand;
   }

}
