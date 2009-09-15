package com.tivo.kmttg.gui;

import java.util.Hashtable;
import java.util.Stack;

public class sortableSize {
   String display;
   long sortable;
   
   // Single entry constructor
   sortableSize(Hashtable<String,String> entry) {
      display = (String)entry.get("sizeGB") + " ";
      sortable = Long.parseLong(entry.get("size"));
   }
   
   // Folder entry constructor
   sortableSize(Stack<Hashtable<String,String>> folderEntry) {
      sortable = 0;
      for (int i=0; i<folderEntry.size(); ++i) {
         sortable += Long.parseLong(folderEntry.get(i).get("size"));
      }
      
      double GB = Math.pow(2,30);
      display = String.format("%.2f GB ", sortable/GB);
   }   
   
   public String toString() {
      return display;
   }
}

