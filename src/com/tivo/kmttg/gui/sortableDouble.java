package com.tivo.kmttg.gui;

public class sortableDouble {
   String display;
   Double sortable;
   
   // Single entry constructor
   sortableDouble(Double num) {
      display = String.format(" %.2f ", num);
      sortable = num;
   }
      
   public String toString() {
      return display;
   }

}
