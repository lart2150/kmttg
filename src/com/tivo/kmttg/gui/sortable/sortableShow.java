package com.tivo.kmttg.gui.sortable;

import java.util.Hashtable;
import java.util.Stack;

public class sortableShow {
   public String titleOnly;
   public String title;
   public String episodeTitle = "";
   public String episodeNum = "";
   public String movieYear = null;
   public long gmt;
   public Boolean folder = false;
   public int numEntries = 0;
   public String folderName = "";
   
   // Single entry constructor
   public sortableShow(Hashtable<String,String> entry) {
      title = (String)entry.get("title");
      titleOnly = (String)entry.get("titleOnly");
      gmt = Long.parseLong(entry.get("gmt"));
      if (entry.containsKey("episodeTitle"))
         episodeTitle = entry.get("episodeTitle");
      if (entry.containsKey("EpisodeNumber"))
         episodeNum = entry.get("EpisodeNumber");
      if (episodeNum.matches("^[0]+$"))
         episodeNum = "";
      if (entry.containsKey("movieYear"))
         movieYear = entry.get("movieYear");
   }
   
   // Folder entry constructor
   public sortableShow(String folderName, Stack<Hashtable<String,String>> folderEntry, int gmt_index) {
      folder = true;
      this.folderName = folderName;
      numEntries = folderEntry.size();
      gmt = Long.parseLong(folderEntry.get(gmt_index).get("gmt"));
      titleOnly = folderEntry.get(0).get("titleOnly");
      title = titleOnly;
   }
   
   public String toString() {
      if (folder) {
         return folderName + " (" + numEntries + ")";
      } else {
         if (episodeNum.length() > 0 && titleOnly.length() > 0) {
            String s = titleOnly + " [Ep " + episodeNum + "]";
            if (episodeTitle.length() > 0)
               s += " - " + episodeTitle;
            return s;
         }
         if (movieYear != null) {
            String s = titleOnly + " [" + movieYear + "]";
            return s;
         }
         return title;
      }
   }
}
