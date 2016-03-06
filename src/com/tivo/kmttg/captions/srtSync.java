package com.tivo.kmttg.captions;

import java.util.Stack;

public class srtSync {
   public Stack<ccdiff> ccstack;
   int minSize = 8; // Require at least this many characters in synced cc text
   Boolean debug = false;
   
   public srtSync(String srtFile1, String srtFile2, Boolean debug) {
      this.debug = debug;
      if ( ! syncSrt(srtFile1, srtFile2))
         ccstack = null;
   }
   
   private Boolean syncSrt(String srt1, String srt2) {
      ccstack = new Stack<ccdiff>();
      srtReader s1 = new srtReader(srt1);
      srtReader s2 = new srtReader(srt2);
      if (debug) {
         System.out.println("---DEBUG: srt data for: " + srt1 + "---");
         s1.print();
         System.out.println("---DEBUG: srt data for: " + srt2 + "---");
         s2.print();
      }
      Stack<cc> cc1stack = s1.ccstack;
      if (cc1stack == null)
         return false;
      Stack<cc> cc2stack = s2.ccstack;
      if (cc2stack == null)
         return false;
      int index2 = 0;
      for (cc cc1 : cc1stack) {
         String text1 = cc1.text;
         if (text1.length() >= minSize) {
            for (int i=index2; i<cc2stack.size(); ++i) {
               cc cc2 = cc2stack.get(i);
               if (text1.equals(cc2.text)) {
                  index2 = i;
                  ccdiff cdiff = new ccdiff();
                  cdiff.start1 = cc1.start;
                  cdiff.start2 = cc2.start;
                  cdiff.stop1 = cc1.stop;
                  cdiff.stop2 = cc2.stop;
                  cdiff.text = text1;
                  ccstack.push(cdiff);
                  break;
               }
            }
         }
      }
      return true;
   }
   
   public ccdiff findBefore(long time) {
      ccdiff previous = null;
      if (ccstack != null) {
         for (ccdiff caption : ccstack) {
            if (caption.stop1 > time)
               return previous;
            previous = caption;
         }
      }
      return null;
   }
   
   public ccdiff findAfter(long time) {
      if (ccstack != null) {
         for (ccdiff caption : ccstack) {
            if (caption.start1 > time)
               return caption;
         }
      }
      return null;
   }
   
   // Print content of ccstack for debug
   public void print() {
      if (ccstack != null) {
         int i=1;
         for (ccdiff caption : ccstack) {
            System.out.println(i);
            System.out.println(caption);
            i++;
         }
      }
   }   
}
