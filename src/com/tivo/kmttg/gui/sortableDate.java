package com.tivo.kmttg.gui;

import java.util.Hashtable;

public class sortableDate {
   String display;
   String sortable;
   Hashtable<String,String> data;
   
   sortableDate(Hashtable<String,String> entry) {
      display = (String)entry.get("date");
      sortable = (String)entry.get("gmt");
      data = entry;
   }
   
   public String toString() {
      return display;
   }
}
