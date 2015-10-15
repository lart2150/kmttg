package com.tivo.kmttg.gui.sortable;

import com.tivo.kmttg.JSON.JSONObject;

public class sortableString {
   public String display;
   public JSONObject json;
   
   public sortableString() {
      json = new JSONObject();
      display = "";
   }
   
   public sortableString(JSONObject json, String s) {
      this.json = json;
      display = s;
   }
   
   public String toString() {
      return display;
   }
}
