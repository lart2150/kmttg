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

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.JTable;

import com.tivo.kmttg.gui.dialog.autoTableEntry;
import com.tivo.kmttg.gui.swing.KmttgTable;
import com.tivo.kmttg.gui.swing.KmttgTableModel;
import com.tivo.kmttg.main.autoConfig;
import com.tivo.kmttg.main.autoEntry;
import com.tivo.kmttg.util.log;

public class autoTable {
   private String[] TITLE_cols = {"Type", "Keywords"};
   public JTable TABLE = null;
   public KmttgTableModel<Tabentry> MODEL = null;

   public autoTable() {
      MODEL = new KmttgTableModel<Tabentry>(TITLE_cols);
      MODEL.setSortingEnabled(false); // Row order is meaningful
      TABLE = KmttgTable.create(MODEL, null);

      // Add keyboard listener
      TABLE.addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(KeyEvent e) {
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
      if (row >= MODEL.size())
         return null;
      return MODEL.getRow(row).getType().entry;
   }

   public int[] getSelectedRows() {
      return TableUtil.GetSelectedRows(TABLE);
   }

   private void InsertRow(int row, autoEntry entry) {
      MODEL.getRows().add(row, new Tabentry(entry));
      MODEL.fireTableRowsInserted(row, row);
   }

   public void RemoveRow(int row) {
      MODEL.removeRow(row);
      resize();
   }

   public void clear() {
      MODEL.clear();
   }

   public void AddRow(autoEntry entry) {
      MODEL.addRow(new Tabentry(entry));
      resize();
   }

   public void resize() {
      TableUtil.autoSizeTableViewColumns(TABLE, true);
   }

   private void KeyPressed(KeyEvent e) {
      if (e.isControlDown())
         return;
      int keyCode = e.getKeyCode();
      if (keyCode == KeyEvent.VK_UP) {
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
      else if (keyCode == KeyEvent.VK_DOWN) {
         // Move selected row down
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 0) {
            log.error("No rows selected");
            return;
         }
         int row;
         for (int i=0; i<selected.length; ++i) {
            row = selected[i];
            if (row < MODEL.size()-1) {
               autoEntry entry = GetRowData(row);
               RemoveRow(row);
               InsertRow(row+1, entry);
               TABLE.addRowSelectionInterval(row, row);
            }
         }
      }
   }
}
