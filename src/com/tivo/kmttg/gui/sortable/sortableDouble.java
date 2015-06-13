package com.tivo.kmttg.gui.sortable;

public class sortableDouble {
   public String display;
   public Double sortable;
   
   // Single entry constructor
   public sortableDouble(Double num) {
      display = String.format(" %.2f ", num);
      sortable = num;
   }
      
   public String toString() {
      return display;
   }

}
