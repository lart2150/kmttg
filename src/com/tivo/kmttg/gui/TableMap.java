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
package com.tivo.kmttg.gui;

import java.util.Hashtable;

import javafx.scene.control.TableView;
import javafx.scene.control.TreeTableView;

import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.log;

public class TableMap {
   private static Hashtable<String, TableMap> map = new Hashtable<String, TableMap>();
   
   public static void init() {
      map.put("todo", config.gui.remote_gui.todo_tab.tab);
      map.put("season passes", config.gui.remote_gui.sp_tab.tab);
      map.put("sp", config.gui.remote_gui.sp_tab.tab);
      map.put("won't record", config.gui.remote_gui.cancel_tab.tab);
      map.put("cancel", config.gui.remote_gui.cancel_tab.tab);
      map.put("deleted", config.gui.remote_gui.deleted_tab.tab);
      map.put("season premieres", config.gui.remote_gui.premiere_tab.tab);
      map.put("premiere", config.gui.remote_gui.premiere_tab.tab);
      map.put("search", config.gui.remote_gui.search_tab.tab);
      map.put("guide", config.gui.remote_gui.guide_tab.tab);
      map.put("streaming", config.gui.remote_gui.stream_tab.tab);
      map.put("stream", config.gui.remote_gui.stream_tab.tab);
      map.put("thumbs", config.gui.remote_gui.thumbs_tab.tab);
      map.put("channels", config.gui.remote_gui.channels_tab.tab);
   }
   
   public JSONObject getJson(int row) {
      return null;
   }
   
   public int[] getSelected() {
      return null;
   }
   
   public Boolean isRemote() {
      return false;
   }
   
   public void clear() {
      // Nothing here
   }
   
   public TableView<?> getTable() {
      return null;
   }
   
   public TreeTableView<?> getTreeTable() {
      return null;
   }
   
   public static TableMap get(String name) {
      if (map.containsKey(name.toLowerCase()))
         return map.get(name.toLowerCase());
      else
         log.error("Missing TableMap for: " + name);
      return null;
   }
   
   public static TableMap getCurrent() {
      String tabName = config.gui.getCurrentTabName();
      if (tabName.equals("Remote")) {
         String subTabName = config.gui.remote_gui.getCurrentTabName().toLowerCase();
         if (map.containsKey(subTabName))
            return map.get(subTabName);
         else
            log.error("Missing TableMap for: " + subTabName);
      }
      return null;
   }
   
   public static void clear(String name) {
      if (map.containsKey(name.toLowerCase()))
         map.get(name.toLowerCase()).clear();
      else
         log.error("Missing TableMap for: " + name);
     
   }
}
