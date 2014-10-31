package com.tivo.kmttg.httpserver;

import java.util.Arrays;

public class TranscodeTemplates {
   
   public static String hls(String urlBase)  {
      String args = "-ss 0 -threads 0 -y -map_metadata -1 -vcodec libx264 -crf 19";
      args += " -maxrate 3000k -bufsize 6000k -preset veryfast";
      args += " -x264opts cabac=0:8x8dct=1:bframes=0:subme=0:me_range=4:rc_lookahead=10:me=dia:no_chroma_me:8x8dct=0:partitions=none:bframes=3:cabac=1";
      args += " -flags -global_header -force_key_frames expr:gte(t,n_forced*3) -sn";
      args += " -acodec aac -strict -2 -cutoff 15000 -ac 2 -ab 217k";
      args += " -segment_format mpegts -f segment -segment_time 10 -segment_start_number 0";
      args += " -segment_list_entry_prefix " + urlBase + " -segment_list_flags +cache -segment_list";
      return args;
   }
   
   public static String webm() {
      return "-threads 0 -y -vcodec libvpx -crf 19 -b 1M -sn -acodec libvorbis -ac 2 -ab 217k -f webm";
   }
   
   public static String printArray(String[] arr) {
      return Arrays.asList(arr).toString().substring(1).replaceFirst("]", "").replace(", ", " ");
   }

}
