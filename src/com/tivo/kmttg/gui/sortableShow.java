package com.tivo.kmttg.gui;

import java.util.Hashtable;
import java.util.Stack;

public class sortableShow {
   String titleOnly;
   String title;
   long gmt;
   Boolean folder = false;
   int numEntries = 0;
   
   // Single entry constructor
   sortableShow(Hashtable<String,String> entry) {
      title = (String)entry.get("title");
      titleOnly = (String)entry.get("titleOnly");
      gmt = Long.parseLong(entry.get("gmt"));
   }
   
   // Folder entry constructor
   sortableShow(Stack<Hashtable<String,String>> folderEntry, int gmt_index) {
      folder = true;
      numEntries = folderEntry.size();
      gmt = Long.parseLong(folderEntry.get(gmt_index).get("gmt"));
      titleOnly = folderEntry.get(0).get("titleOnly");
      title = titleOnly;
   }
   
   public String toString() {
      if (folder) {
         return titleOnly + " (" + numEntries + ")";
      } else {
         return title;
      }
   }
}
