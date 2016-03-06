package com.tivo.kmttg.captions;

public class ccdiff {
   long start1;
   long start2;
   long stop1;
   long stop2;
   String text;
   
   public long startDiff() {
      return start2 - start1;
   }
   
   public long stopDiff() {
      return stop2 - stop1;
   }
   
   public String toString() {
      String string = "";
      long start_diff = start2 - start1;
      long stop_diff = stop2 - stop1;
      string = "start1=" + util.toHourMinSec(start1);
      string += " start2=" + util.toHourMinSec(start2);
      string += " diff=" + start_diff + "\n";
      string += "stop1=" + util.toHourMinSec(stop1);
      string += " stop2=" + util.toHourMinSec(stop2);
      string += " diff=" + stop_diff + "\n";
      string += text;
      return string;
   }
}
