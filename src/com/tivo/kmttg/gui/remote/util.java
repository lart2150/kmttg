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
package com.tivo.kmttg.gui.remote;

import java.util.Date;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Stack;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.dialog.mRecordOptions;
import com.tivo.kmttg.gui.dialog.recordOptions;
import com.tivo.kmttg.gui.dialog.spOptions;
import com.tivo.kmttg.gui.dialog.wlOptions;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.log;

import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;

public class util {
   public static LinkedHashMap<String,String> SPS = new LinkedHashMap<String,String>();
   public static mRecordOptions mRecordOpt = new mRecordOptions();   
   public static recordOptions recordOpt = new recordOptions();
   public static wlOptions wlOpt = new wlOptions();
   public static spOptions spOpt = new spOptions();
   public static Hashtable<String,JSONArray> all_todo = new Hashtable<String,JSONArray>();
   public static long all_todo_time = 0;
   
   // Return list of Tivos that are rpc enabled or mind enabled and NPL capable (i.e. no Mini)
   public static Stack<String> getFilteredTivoNames() {
      Stack<String> tivoNames = new Stack<String>();
      for (String tivoName : config.getTivoNames()) {
         if (config.nplCapable(tivoName)) {
            if (config.rpcEnabled(tivoName) || config.mindEnabled(tivoName))
               tivoNames.add(tivoName);
         }
      }
      return tivoNames;
   }
   
   // Obtain todo lists for specified tivo names
   // Used by Search and Guide tabs to mark recordings
   // NOTE: This called as part of a background job
   // NOTE: This uses CountDownLatch to enable waiting for multiple
   // parallel background jobs to finish before returning so that
   // ToDo lists are retrieved in parallel instead of sequentially
   public static Hashtable<String,JSONArray> getTodoLists() {
      all_todo_time = new Date().getTime();
      return rnpl.getTodoLists(getFilteredTivoNames());
   }
   
   // Check current time vs time of last all_todo refresh to see if we need refreshed
   // Give a 15 min cushion
   private static Boolean todoNeedsRefresh() {
      long cushion = 15*60*1000;
      long now = new Date().getTime();
      if (all_todo_time == 0)
         return true;
      if (now > all_todo_time + cushion)
         return true;
      return false;
   }
   
   public static void updateTodoIfNeeded(String tabName) {
      if (todoNeedsRefresh()) {
         log.warn("Refreshing todo lists");
         all_todo = util.getTodoLists();
      }
   }
   
   public static void addEntryToTodo(String tivoName, JSONObject json) {
      if (all_todo.containsKey(tivoName))
         all_todo.get(tivoName).put(json);
   }
   
   public static HBox space(int size) {
      HBox space = new HBox(); space.setMinWidth(size); space.setPrefWidth(size);
      return space;
   }
   
   public static ColumnConstraints cc_stretch() {
      ColumnConstraints cc_stretch = new ColumnConstraints();
      cc_stretch.setHgrow(Priority.ALWAYS);
      return cc_stretch;
   }
   
   public static RowConstraints rc_stretch() {
      RowConstraints rc_stretch = new RowConstraints();
      rc_stretch.setVgrow(Priority.ALWAYS);
      return rc_stretch;
   }
   
   public static ColumnConstraints cc_none() {
      ColumnConstraints cc_none = new ColumnConstraints();
      cc_none.setHgrow(Priority.NEVER);
      return cc_none;
   }

}
