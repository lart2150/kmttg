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
package com.tivo.kmttg.gui.swing;

import java.awt.Color;
import java.awt.Component;

import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

import com.tivo.kmttg.gui.table.imageCell;

/**
 * Factory for the standard kmttg flat table: JTable backed by a
 * KmttgTableModel with multi-row selection, header click sorting,
 * optional per-row background coloring and an image cell renderer.
 */
public class KmttgTable {

   public static <T> JTable create(final KmttgTableModel<T> model, final RowColorer<T> colorer) {
      JTable table = new JTable(model) {
         private static final long serialVersionUID = 1L;
         @Override
         public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
            Component c = super.prepareRenderer(renderer, row, column);
            if (isRowSelected(row)) {
               c.setBackground(getSelectionBackground());
            } else {
               Color color = colorer == null ? null : colorer.getColor(model.getRow(row));
               if (color != null) {
                  // Status colors are light pastels - keep text readable
                  c.setBackground(color);
                  c.setForeground(Color.BLACK);
               } else {
                  // Alternating row striping
                  c.setBackground(row % 2 == 1 ?
                     SwingUtil.alternateRowColor(getBackground()) : getBackground());
                  c.setForeground(getForeground());
               }
            }
            return c;
         }
      };
      table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
      // Cell border grid (like the former JavaFX tables - kmttg.css set
      // -fx-border-color: lightgrey on every cell)
      table.setShowGrid(true);
      table.setIntercellSpacing(new java.awt.Dimension(1, 1));
      table.setFillsViewportHeight(true);
      table.setDefaultRenderer(imageCell.class, new ImageCellRenderer());
      model.attach(table);
      return table;
   }

   // Center/right aligned column renderer (replaces -fx-alignment styles)
   public static void setColumnAlignment(JTable table, String colName, int alignment) {
      for (int i = 0; i < table.getModel().getColumnCount(); ++i) {
         if (table.getModel().getColumnName(i).equals(colName)) {
            DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
            renderer.setHorizontalAlignment(alignment);
            table.getColumnModel().getColumn(table.convertColumnIndexToView(i)).setCellRenderer(renderer);
         }
      }
   }

   // Install imageCell renderer on given column (needed because reflection
   // based models report Object.class column types)
   public static void setImageColumn(JTable table, String colName) {
      for (int i = 0; i < table.getModel().getColumnCount(); ++i) {
         if (table.getModel().getColumnName(i).equals(colName)) {
            table.getColumnModel().getColumn(table.convertColumnIndexToView(i))
               .setCellRenderer(new ImageCellRenderer());
         }
      }
   }
}
