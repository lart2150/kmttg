package com.tivo.kmttg.captions;

public class util {
   public static String toHourMinSec(long msecs) {
      int sec = (int) (msecs/1000) % 60;
      int min = (int) ((msecs/(1000*60)) % 60);
      int hour = (int) ((msecs/(1000*60*60)) % 24);
      int msec = (int) msecs - (hour*1000*60*60 + min*1000*60 + sec*1000);
      return String.format("%02d:%02d:%02d.%03d", hour, min, sec, msec);
   }

}
