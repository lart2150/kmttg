package com.tivo.kmttg.gui.sortable;

import java.util.Hashtable;
import java.util.Stack;

public class sortableSize {
   public String display;
   public long sortable;
   
   // Single entry constructor
   public sortableSize(Hashtable<String,String> entry) {
      display = (String)entry.get("sizeGB") + " ";
      sortable = Long.parseLong(entry.get("size"));
   }
   
   // Folder entry constructor
   public sortableSize(Stack<Hashtable<String,String>> folderEntry) {
      sortable = 0;
      for (int i=0; i<folderEntry.size(); ++i) {
         sortable += Long.parseLong(folderEntry.get(i).get("size"));
      }      
      display = String.format("%.2f GB ", sortable/Math.pow(2,30));
   }  
   
   public sortableSize(long size) {
      sortable = size;
      display = String.format("%.2f GB ", sortable/Math.pow(2,30));
   }
   
   public String toString() {
      return display;
   }
}

