package com.tivo.kmttg.gui;

import java.util.Hashtable;
import java.util.Stack;

public class sortableDate {
   String display;
   String sortable;
   Hashtable<String,String> data;
   Stack<Hashtable<String,String>> folderData;
   Boolean folder = false;
   
   // Single entry constructor
   sortableDate(Hashtable<String,String> entry) {
      display = (String)entry.get("date") + " ";
      sortable = (String)entry.get("gmt");
      data = entry;
   }
   
   // Folder entry constructor
   sortableDate(Stack<Hashtable<String,String>> folderEntry, int gmt_index) {
      folder = true;
      display = (String)folderEntry.get(gmt_index).get("date") + " ";
      sortable = (String)folderEntry.get(gmt_index).get("gmt"); 
      folderData = folderEntry;
   }   
   public String toString() {
      return display;
   }
}
