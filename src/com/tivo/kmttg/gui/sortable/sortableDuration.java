package com.tivo.kmttg.gui.sortable;

import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.util.log;

public class sortableDuration {
   public String display;
   public Long sortable;
      
   // single entry constructor
   public sortableDuration(Hashtable<String,String> entry) {
      sortable = (long)0;
      if (entry.containsKey("duration")) {
         try {
            sortable = Long.parseLong((String)entry.get("duration"));
         }
         catch (Exception ex) {
            log.error(ex.getMessage());
            sortable = (long)0;
         }
      }
      display = millisecsToHMS(sortable, false);
   }
   
   // folder constructor
   public sortableDuration(Stack<Hashtable<String,String>> folderEntry) {
      sortable = (long)0;
      for (int i=0; i<folderEntry.size(); ++i) {
         long duration = 0;
         if (folderEntry.get(i).containsKey("duration")) {
            try {
               duration = Long.parseLong((String)folderEntry.get(i).get("duration"));
            }
            catch (Exception ex) {
               log.error(ex.getMessage());
               duration = (long)0;
            }
         }
         sortable += duration;
         display = millisecsToHMS(sortable, false);
      }
   }
   
   // simple long value constructor used by bitrateTable
   public sortableDuration(long value) {
      sortable = value;
      display = millisecsToHMS(sortable, true);
   }
   
   public sortableDuration(long value, Boolean showSecs) {
      sortable = value;
      if (value == 0)
         display = "";
      else
         display = millisecsToHMS(sortable, showSecs);
   }
   
   public static String millisecsToHMS(long duration, Boolean showSecs) {
      duration /= 1000;
      long hours = duration/3600;
      if (hours > 0) {
         duration -= hours*3600;
      }
      long mins = duration/60;
      if (mins > 0) {
         duration -= mins*60;
      }
      
      if (showSecs) {
         return String.format(" %d:%02d:%02d", hours,mins,duration);
      } else {
         // Round mins +1 if secs > 30
         long secs = duration;
         if (secs > 30) {
            mins += 1;
         }
         if (mins > 59) {
            hours += 1;
            mins = 0;
         }
         return String.format(" %d:%02d ",hours,mins);
      }
   }

   
   public String toString() {
      return display;
   }
}
