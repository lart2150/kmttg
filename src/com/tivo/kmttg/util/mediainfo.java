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

import com.tivo.kmttg.main.config;

public class mediainfo {
   
   // Use mediainfo cli to get video information from given video file
   // Returns null if undetermined, a hash with video info otherwise
   public static Hashtable<String,String> getVideoInfo(String videoFile) {
      if (! file.isFile(config.mediainfo))
         return null;
      if (! file.isFile(videoFile))
         return null;
      // Use mediainfo command to get video information      
      Stack<String> command = new Stack<String>();
      command.add(config.mediainfo);
      command.add(videoFile);
      backgroundProcess process = new backgroundProcess();
      if ( process.run(command) ) {
         // Wait for command to terminate
         process.Wait();
         
         // Parse stdout
         Stack<String> l = process.getStdout();
         if (l.size() > 0) {
            Hashtable<String,String> info = new Hashtable<String,String>();
            String section = "";
            String line;
            info.put("container", "mpeg");
            info.put("video", "mpeg2video");
            for (int i=0; i<l.size(); ++i) {
               line = l.get(i);
               if (!line.contains(":")) {
                  section = line;
               }
               if (section.matches("^General.*$") && line.matches("^Format\\s+:.+$")) {
                  // Format                                   : MPEG-TS
                  String fields[] = line.split(":");
                  String container = fields[1].toLowerCase();
                  container = container.replaceAll(" ", "");
                  container = container.replaceFirst("-ps", "");
                  container = container.replaceAll("-", "");
                  info.put("container", container);
                  if (container.equals("mpeg4"))
                     info.put("container", "mp4");
               }
               if (section.matches("^General.*$") && line.matches("^Duration\\s+:.+$")) {
                  // Duration                                 : 1h 43mn
                  // Duration                                 : 5mn 0s
                  String fields[] = line.split(":");
                  String duration = fields[1].toLowerCase();
                  duration = duration.replaceFirst(" ", "");
                  fields = line.split(" ");
                  if (fields.length > 0) {
                     int h=0, m=0, s=0;
                     for (int j=0; j<fields.length; ++j) {
                        if (fields[j].matches("^\\d+h$"))
                           h = Integer.parseInt(fields[j].replaceFirst("h", ""));
                        if (fields[j].matches("^\\d+mn$"))
                           m = Integer.parseInt(fields[j].replaceFirst("mn", ""));
                        if (fields[j].matches("^\\d+s$"))
                           s = Integer.parseInt(fields[j].replaceFirst("s", ""));
                     }
                     int dur = 60*60*h + 60*m + s;
                     info.put("duration", "" + dur);
                  }
               }
               if (section.matches("^Video.*$") && line.matches("^Format\\s+:.+$")) {
                  String fields[] = line.split(":");
                  String video = fields[1].toLowerCase();
                  video = video.replaceAll(" ", "");
                  info.put("video", video);
                  if (video.equals("mpegvideo"))
                     info.put("video", "mpeg2video");
                  if (video.contains("avc"))
                     info.put("video", "h264");
               }
               if (section.matches("^Video.*$") && line.matches("^Width\\s+:.+$")) {
                  String fields[] = line.split(":");
                  String x = fields[1].toLowerCase();
                  x = x.replaceAll(" ", "");
                  x = x.replaceAll("pixels", "");
                  info.put("x", x);
               }
               if (section.matches("^Video.*$") && line.matches("^Height\\s+:.+$")) {
                  String fields[] = line.split(":");
                  String y = fields[1].toLowerCase();
                  y = y.replaceAll(" ", "");
                  y = y.replaceAll("pixels", "");
                  info.put("y", y);
               }
               if (section.matches("^Video.*$") && line.matches("^Display\\s+aspect.+$")) {
                  // Display aspect ratio                     : 4:3
                  String dar = line.replaceFirst(" ", "");
                  String fields[] = dar.split(":");
                  if (fields.length == 3) {
                     info.put("DAR_x", fields[1].replaceFirst("^\\s+", ""));
                     info.put("DAR_y", fields[2]);
                  }
               }
               if (section.matches("^Text.*$") && line.matches("^Format.+$")) {
                  // Caption information such as EIA-608 and EIA-708
                  // Format : EIA-608
                  String dar = line.replaceFirst(" ", "");
                  String fields[] = dar.split(":");
                  if (fields.length == 2) {
                     info.put(fields[1], "1");
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
   
   public static Boolean checkDownloadDuration(int download_duration, String videoFile) {
      if (config.download_check_length == 1 && download_duration != 0) {
         // Check duration vs expected using mediainfo
         log.warn("'Check download duration' option enabled => checking expected vs. actual");
         log.warn("(Mismatch tolerance = " + config.download_check_tolerance + " secs)");
         log.warn("Expected duration = " + download_duration + " secs");
         Hashtable<String,String> h = getVideoInfo(videoFile);
         if (h != null && h.containsKey("duration")) {
            int actual = Integer.parseInt(h.get("duration"));
            log.warn("Actual duration = " + actual + " secs");
            if (Math.abs(actual-download_duration) > config.download_check_tolerance) {
               log.error("actual download duration not within expected tolerance => error");
               return false;
            }
         } else {
            log.error("Unable to determine duration using mediainfo from file: " + videoFile);
            return false;
         }
      }
      return true;
   }

}
