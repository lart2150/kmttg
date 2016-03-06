package com.tivo.kmttg.captions;

public class cc {
   long start;
   long stop;
   String text;
   
   public String toString() {
      String string = "";
      string = "start=" + util.toHourMinSec(start);
      string += " stop=" + util.toHourMinSec(stop) + "\n";
      string += text;
      return string;
   }
}
