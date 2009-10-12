package com.tivo.kmttg.gui;

import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.util.log;

public class sortableDuration {
   String display;
   Long sortable;
      
   // single entry constructor
   sortableDuration(Hashtable<String,String> entry) {
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
      display = millisecsToHM(sortable);
   }
   
   // folder constructor
   sortableDuration(Stack<Hashtable<String,String>> folderEntry) {
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
         display = millisecsToHM(sortable);
      }
   }
   
   public static String millisecsToHM(long duration) {
      long hours = duration/(1000*3600);
      if (hours > 0) {
         duration -= hours*3600*1000;
      }
      long mins = duration/(1000*60);
      return String.format("%02d:%02d",hours,mins);
   }

   
   public String toString() {
      return display;
   }
}
