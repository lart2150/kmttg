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

import javax.swing.JTable;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.sortable.sortableInt;
import com.tivo.kmttg.gui.swing.KmttgTable;
import com.tivo.kmttg.gui.swing.KmttgTableModel;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.log;

public class pushTable {
   // Model column names map to getXXX() getters via reflection; header text
   // (with spaces) is applied separately below.
   private String[] TITLE_cols = {"NUM", "TITLE", "DEST_TiVo"};
   private String[] HEADER_cols = {"NUM", "TITLE", "DEST TiVo"};
   public JTable TABLE = null;
   public KmttgTableModel<Tabentry> MODEL = null;

   public pushTable() {
      MODEL = new KmttgTableModel<Tabentry>(TITLE_cols);
      MODEL.setSortingEnabled(false); // Row order is meaningful
      TABLE = KmttgTable.create(MODEL, null);
      // Apply display header text
      for (int i=0; i<TITLE_cols.length; ++i) {
         int view = TABLE.convertColumnIndexToView(i);
         if (view >= 0)
            TABLE.getColumnModel().getColumn(view).setHeaderValue(HEADER_cols[i]);
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

      public String getDEST_TiVo() {
         return tivo;
      }
   }

   public JTable getTable() {
      return TABLE;
   }

   public void clear() {
      MODEL.clear();
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
      MODEL.addRow(new Tabentry(json, num));
   }

   public void RemoveRow(int row) {
      MODEL.removeRow(row);
   }

   public JSONObject GetRowData(int row) {
      sortableInt s = MODEL.getRow(row).num;
      if (s != null)
         return s.json;
      return null;
   }
}
