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

import java.util.Arrays;

public class TranscodeTemplates {
   
   public static String hls(String urlBase, String maxrate)  {
      Double crf_factor = 19.0/3000.0;
      Double val = Integer.parseInt(maxrate.replace("k", ""))*crf_factor;
      Integer crf_delta = 19 - val.intValue();
      Integer crf_val = 19 + crf_delta;
      String crf = "" + crf_val;
      String args = "-ss 0 -threads 0 -y -map_metadata -1 -vcodec libx264 -crf " + crf;
      args += " -maxrate " + maxrate + " -bufsize 6000k -preset veryfast";
      args += " -x264opts cabac=0:8x8dct=1:bframes=0:subme=0:me_range=4:rc_lookahead=10:me=dia:no_chroma_me:8x8dct=0:partitions=none:bframes=3:cabac=1";
      args += " -flags -global_header -force_key_frames expr:gte(t,n_forced*3) -sn";
      args += " -acodec aac -strict -2 -cutoff 15000 -ac 2 -ab 217k";
      args += " -segment_format mpegts -f segment -segment_time 10 -segment_start_number 0";
      args += " -segment_list_entry_prefix " + urlBase + " -segment_list_flags +cache -segment_list";
      return args;
   }
   
   public static String webm(String maxrate) {
      String args = "-threads 0 -y -vcodec libvpx -deadline realtime -b " + maxrate;
      args += " -sn -acodec libvorbis -ac 2 -ab 217k -f webm";
      return args;
   }
   
   public static String printArray(String[] arr) {
      return Arrays.asList(arr).toString().substring(1).replaceFirst("]", "").replace(", ", " ");
   }

}
