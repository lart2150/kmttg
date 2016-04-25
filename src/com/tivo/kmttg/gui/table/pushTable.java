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
package com.tivo.kmttg.gui.table;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.sortable.sortableInt;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.log;

public class pushTable {
   private String[] TITLE_cols = {"NUM", "TITLE", "DEST TiVo"};
   public TableView<Tabentry> TABLE = null;

   public pushTable() {
      TABLE = new TableView<Tabentry>();
      for (String colName : TITLE_cols) {
         // Regular String sort
         String cName = colName;
         if (colName.equals("DEST TiVo"))
            cName = "DEST_TIVO";
         if (colName.equals("NUM")) {
            TableColumn<Tabentry,sortableInt> col = new TableColumn<Tabentry,sortableInt>(colName);
            col.setCellValueFactory(new PropertyValueFactory<Tabentry,sortableInt>(cName));
            col.setComparator(null); // disable sorting
            TABLE.getColumns().add(col);
         } else {
            TableColumn<Tabentry,String> col = new TableColumn<Tabentry,String>(colName);
            col.setCellValueFactory(new PropertyValueFactory<Tabentry,String>(cName));
            col.setComparator(null); // disable sorting
            TABLE.getColumns().add(col);
         }
      }
   }

   public class Tabentry {
      public sortableInt num;
      public String title = "";
      public String tivo = "";

      public Tabentry(JSONObject json, int num) {
         try {
            // NUM
            this.num = new sortableInt(json, num);
            // Title
            title = "none";
            if (json.has("title"))
               title = json.getString("title");
            // TiVo
            String tsn = json.getString("bodyId");
            tsn = tsn.replaceFirst("tsn:", "");
            tivo = config.getTiVoFromTsn(tsn);
            if (tivo == null) tivo = tsn;
         } catch (Exception e) {
            log.error("pushTable Tabentry - " + e.getMessage());
         }
      }

      public sortableInt getNUM() {
         return num;
      }

      public String getTITLE() {
         return title;
      }

      public String getDEST_TIVO() {
         return tivo;
      }      
   }

   public TableView<?> getTable() {
      return TABLE;
   }

   public void clear() {
      TABLE.getItems().clear();
   }

   public void AddRows(JSONArray data) {
      for (int i=0; i<data.length(); ++i) {
         try {
            AddRow(data.getJSONObject(i), i+1);
         } catch (JSONException e) {
            log.error("pushTable AddRows - " + e.getMessage());
            return;
         }
      }
      TableUtil.autoSizeTableViewColumns(TABLE, true);
   }

   public void AddRow(JSONObject json, int num) {
      TABLE.getItems().add(new Tabentry(json, num));
   }

   public void RemoveRow(int row) {
      TABLE.getItems().remove(row);
   }

   public JSONObject GetRowData(int row) {
      sortableInt s = TABLE.getItems().get(row).num;
      if (s != null)
         return s.json;
      return null;
   }    
}
