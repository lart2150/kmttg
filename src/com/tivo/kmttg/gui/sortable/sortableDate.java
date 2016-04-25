/*
 * Copyright 2008-Present Kevin Moye <moyekj@yahoo.com>.
 *
 * This file is part of kmttg package.
 *
 * kmttg is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this project.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tivo.kmttg.gui.sortable;

import java.text.SimpleDateFormat;
import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.util.debug;

public class sortableDate {
   public String display;
   public String sortable;
   public Hashtable<String,String> data;
   public JSONObject json;
   public String folderName = "";
   public Stack<Hashtable<String,String>> folderData;
   public Stack<JSONObject> folderData_json;
   public Boolean folder = false;
   
   // Single entry constructor
   public sortableDate(Hashtable<String,String> entry) {
      display = getTime(Long.parseLong(entry.get("gmt"))) + " ";
      sortable = (String)entry.get("gmt");
      data = entry;
   }
   
   // Folder entry constructor
   public sortableDate(String folderName, Stack<Hashtable<String,String>> folderEntry, int gmt_index) {
      this.folderName = folderName;
      folder = true;
      display = getTime(Long.parseLong(folderEntry.get(gmt_index).get("gmt"))) + " ";
      sortable = (String)folderEntry.get(gmt_index).get("gmt"); 
      folderData = folderEntry;
   }
   
   // json & gmt constructor
   public sortableDate(JSONObject json, long gmt) {
      this.json = json;
      display = getTime(gmt);
      if (gmt == 0 || gmt == -1)
         sortable = "0";
      else
         sortable = "" + gmt;
   }
   
   // Folder entry json & gmt constructor
   public sortableDate(String folderName, Stack<JSONObject> folderEntry) {
      this.folderName = folderName;
      folder = true;
      display = "";
      sortable = "0"; 
      folderData_json = folderEntry;
   }
   
   // Alternate folder entry with JSONObject
   public sortableDate(String folderName, JSONObject json, long gmt) {
      this.folderName = folderName;
      this.json = json;
      folder = true;
      if (gmt == 0 || gmt == -1) {
         display = "";
         sortable = "0";
      } else {
         display = getTime(gmt);
         sortable = "" + gmt;
      }
   }
   
   private String getTime(long gmt) {
      debug.print("gmt=" + gmt);
      if (gmt == -1)
         return "";
      SimpleDateFormat sdf = new SimpleDateFormat("E MM/dd/yy hh:mm a");
      return sdf.format(gmt);
   }

   public String toString() {
      return display;
   }
}
