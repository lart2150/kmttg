package com.tivo.kmttg.gui;

import java.util.Hashtable;

public class sortableShow {
   String titleOnly;
   String title;
   long gmt;
   
   sortableShow(Hashtable<String,String> entry) {
      title = (String)entry.get("title");
      titleOnly = (String)entry.get("titleOnly");
      gmt = Long.parseLong(entry.get("gmt"));
   }
   
   public String toString() {
      return title;
   }
}
