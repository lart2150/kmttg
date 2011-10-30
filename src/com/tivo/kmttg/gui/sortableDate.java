package com.tivo.kmttg.gui;

import java.text.SimpleDateFormat;
import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.util.debug;

public class sortableDate {
   String display;
   String sortable;
   Hashtable<String,String> data;
   JSONObject json;
   String folderName = "";
   Stack<Hashtable<String,String>> folderData;
   Stack<JSONObject> folderData_json;
   Boolean folder = false;
   
   // Single entry constructor
   sortableDate(Hashtable<String,String> entry) {
      display = getTime(Long.parseLong(entry.get("gmt"))) + " ";
      sortable = (String)entry.get("gmt");
      data = entry;
   }
   
   // Folder entry constructor
   sortableDate(String folderName, Stack<Hashtable<String,String>> folderEntry, int gmt_index) {
      this.folderName = folderName;
      folder = true;
      display = getTime(Long.parseLong(folderEntry.get(gmt_index).get("gmt"))) + " ";
      sortable = (String)folderEntry.get(gmt_index).get("gmt"); 
      folderData = folderEntry;
   }
   
   // json & gmt constructor
   sortableDate(JSONObject json, long gmt) {
      this.json = json;
      display = getTime(gmt);
      sortable = "" + gmt;
   }
   
   // Folder entry json & gmt constructor
   sortableDate(String folderName, Stack<JSONObject> folderEntry) {
      this.folderName = folderName;
      folder = true;
      display = "";
      sortable = "0"; 
      folderData_json = folderEntry;
   }
   
   // Alternate folder entry with JSONObject
   sortableDate(String folderName, JSONObject json, long gmt) {
      this.folderName = folderName;
      this.json = json;
      folder = true;
      if (gmt == 0) {
         display = "";
         sortable = "0";
      } else {
         display = getTime(gmt);
         sortable = "" + gmt;
      }
   }
   
   private String getTime(long gmt) {
      debug.print("gmt=" + gmt);
      SimpleDateFormat sdf = new SimpleDateFormat("E MM/dd/yy hh:mm a");
      return sdf.format(gmt);
   }

   public String toString() {
      return display;
   }
}
