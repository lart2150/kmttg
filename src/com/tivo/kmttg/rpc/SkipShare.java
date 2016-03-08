package com.tivo.kmttg.rpc;

import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.captions.ccdiff;
import com.tivo.kmttg.captions.srtSync;

public class SkipShare {
   
   public static void Import(String shareSrt, String shareCut, String mySrt, Boolean debug) {
      srtSync csync = new srtSync(shareSrt, mySrt, false);
      if (csync.ccstack == null) return;
      if (debug) {
         System.out.println("---DEBUG: srtSync data---");
         csync.print();
      }
      Stack<Hashtable<String,Long>> points;
      if (shareCut.endsWith(".Vprj"))
         points = SkipImport.vrdImport(shareCut, 0L);
      else
         points = SkipImport.edlImport(shareCut, 0L);
      if (debug)
         printImportedCuts(points);
      if (points == null) return;
      
      int i=1;
      String line = "";
      for (Hashtable<String,Long> point : points) {
         long start = point.get("start");
         long end = point.get("end");
         long adjusted_start = 0, adjusted_end = 0;
         if (start > 0) {
            ccdiff cc = csync.findAfter(start);
            if (cc != null) {
               adjusted_start = start + cc.startDiff();
            }
         }
         if (end > 0) {
            ccdiff cc = csync.findBefore(end);
            if (cc != null) {
               adjusted_end = end + cc.stopDiff();
            }
         }
         line = "" + i + ": start=" + com.tivo.kmttg.captions.util.toHourMinSec(adjusted_start) +
               " end=" + com.tivo.kmttg.captions.util.toHourMinSec(adjusted_end);
         if (adjusted_start == 0 && adjusted_end == 0)
            continue;
         else {
            System.out.println(line);
            i++;
         }
      }
   }
   
   public static void printImportedCuts(Stack<Hashtable<String,Long>> points) {
      int i=1;
      System.out.println("---DEBUG: imported cut points---");
      if (points == null) return;
      for (Hashtable<String,Long> point : points) {
         String line = "" + i + ": start=" + com.tivo.kmttg.captions.util.toHourMinSec(point.get("start"));
         line += " end=" + com.tivo.kmttg.captions.util.toHourMinSec(point.get("end"));
         if (point.get("start") > 0 || point.get("end") > 0) {
            System.out.println(line);
            i++;
         }
      }
      System.out.println();
   }
}
