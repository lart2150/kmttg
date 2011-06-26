package com.tivo.kmttg.gui;

import com.tivo.kmttg.JSON.JSONObject;

public class sortableInt {
   String display;
   int sortable;
   JSONObject json;
   
   // json & priority constructor
   sortableInt(JSONObject json, int priority) {
      this.json = json;
      display = "" + priority;
      sortable = priority;
   }

   public String toString() {
      return display;
   }

}
