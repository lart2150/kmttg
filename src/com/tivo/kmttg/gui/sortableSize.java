package com.tivo.kmttg.gui;

import java.util.Hashtable;

public class sortableSize {
   String display;
   long sortable;
   
   sortableSize(Hashtable<String,String> entry) {
      display = (String)entry.get("sizeGB");
      sortable = Long.parseLong(entry.get("size"));
   }
   
   public String toString() {
      return display;
   }
}

