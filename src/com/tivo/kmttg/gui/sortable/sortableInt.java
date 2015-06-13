package com.tivo.kmttg.gui.sortable;

import com.tivo.kmttg.JSON.JSONObject;

public class sortableInt {
   public String display;
   public int sortable;
   public JSONObject json;
   
   // json & priority constructor
   public sortableInt(JSONObject json, int priority) {
      this.json = json;
      display = "" + priority;
      sortable = priority;
   }

   public String toString() {
      return display;
   }

}
