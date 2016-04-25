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

import com.tivo.kmttg.gui.dialog.autoTableEntry;
import com.tivo.kmttg.main.autoConfig;
import com.tivo.kmttg.main.autoEntry;
import com.tivo.kmttg.util.log;

import javafx.event.EventHandler;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class autoTable {
   public TableView<Tabentry> TABLE = null;

   public autoTable() {
      TABLE = new TableView<Tabentry>();
      //TABLE.setEditable(false);
      TableColumn<Tabentry,autoTableEntry> col1 = new TableColumn<Tabentry,autoTableEntry>("Type");
      col1.setCellValueFactory(new PropertyValueFactory<Tabentry,autoTableEntry>("Type"));
      col1.setComparator(null); // Disable column sorting
      TABLE.getColumns().add(col1);
      TableColumn<Tabentry,String> col2 = new TableColumn<Tabentry,String>("Keywords");
      col2.setCellValueFactory(new PropertyValueFactory<Tabentry,String>("Keywords"));
      col2.setComparator(null); // Disable column sorting
      TABLE.getColumns().add(col2);

      // Add keyboard listener
      TABLE.setOnKeyPressed(new EventHandler<KeyEvent>() {
         public void handle(KeyEvent e) {
            KeyPressed(e);
         }
      });
   }

   public static class Tabentry {
      public autoTableEntry type;
      public String keywords;

      private Tabentry(autoEntry entry) {
         type = new autoTableEntry(entry);
         if (entry.type.equals("title")) {
            keywords = entry.keyword;
         } else {
            keywords = autoConfig.keywordsToString(entry.keywords);
         }
      }

      public autoTableEntry getType() {
         return type;
      }

      public String getKeywords() {
         return keywords;
      }
   }
   
   public autoEntry GetRowData(int row) {
      if (row >= TABLE.getItems().size())
         return null;
      return TABLE.getItems().get(row).getType().entry;
   }
   
   public int[] getSelectedRows() {
      return TableUtil.GetSelectedRows(TABLE);
   }
   
   private void InsertRow(int row, autoEntry entry) {
      TABLE.getItems().add(row, new Tabentry(entry));
   }
   
   public void RemoveRow(int row) {
      TABLE.getItems().remove(row);
      resize();
   }

   public void clear() {
      TABLE.getItems().clear();
   }

   public void AddRow(autoEntry entry) {
      TABLE.getItems().add(new Tabentry(entry));
      resize();
   }
   
   public void resize() {
      TableUtil.autoSizeTableViewColumns(TABLE, true);
   }
   
   private void KeyPressed(KeyEvent e) {
      if (e.isControlDown())
         return;
      KeyCode keyCode = e.getCode();
      if (keyCode == KeyCode.UP) {
         // Move selected row up
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 0) {
            log.error("No rows selected");
            return;
         }
         int row;
         for (int i=0; i<selected.length; ++i) {
            row = selected[i];
            if (row-1 >= 0) {
               autoEntry entry = GetRowData(row);
               RemoveRow(row);
               InsertRow(row-1, entry);
            }
         }
      }
      else if (keyCode == KeyCode.DOWN) {
         // Move selected row down
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 0) {
            log.error("No rows selected");
            return;
         }
         int row;
         for (int i=0; i<selected.length; ++i) {
            row = selected[i];
            if (row < TABLE.getItems().size()-1) {
               autoEntry entry = GetRowData(row);
               RemoveRow(row);
               InsertRow(row+1, entry);
               TABLE.getSelectionModel().select(row);
            }
         }
      }
   }
}
