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

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;

import com.tivo.kmttg.util.log;

/**
 * Generic table model used by all kmttg flat tables. Replaces the JavaFX
 * TableView + PropertyValueFactory + ObservableList pattern.
 *
 * Column values are extracted from row objects via reflection the same way
 * PropertyValueFactory did: column "DATE" calls getDATE() on the row object
 * (empty column name maps to getIMAGE()).
 *
 * IMPORTANT: sorting reorders this model's backing row list directly (like
 * JavaFX TableView.sort() did) so view row index == model row index always
 * holds. Do NOT attach a javax.swing.RowSorter to tables using this model.
 * Clicking a column header cycles ascending -> descending -> default sort.
 */
public class KmttgTableModel<T> extends AbstractTableModel {
   private static final long serialVersionUID = 1L;
   private final List<T> rows = new ArrayList<T>();
   private final String[] colNames;
   private final Hashtable<Integer,Comparator<Object>> comparators = new Hashtable<Integer,Comparator<Object>>();
   private Method[] getters = null;
   private int sortColumn = -1;
   private boolean sortAscending = true;
   private int defaultSortColumn = -1;
   private boolean defaultSortAscending = true;
   private boolean sortingEnabled = true;
   private JTable table = null;

   public KmttgTableModel(String[] colNames) {
      this.colNames = colNames.clone();
   }

   // ----- TableModel implementation -----

   @Override
   public int getRowCount() {
      return rows.size();
   }

   @Override
   public int getColumnCount() {
      return colNames.length;
   }

   @Override
   public String getColumnName(int col) {
      return colNames[col];
   }

   @Override
   public boolean isCellEditable(int row, int col) {
      return false;
   }

   @Override
   public Object getValueAt(int row, int col) {
      try {
         return getter(rows.get(row), col).invoke(rows.get(row));
      } catch (Exception e) {
         log.error("KmttgTableModel getValueAt - " + e.toString());
         return null;
      }
   }

   private Method getter(T rowObj, int col) throws NoSuchMethodException {
      if (getters == null)
         getters = new Method[colNames.length];
      if (getters[col] == null) {
         String name = colNames[col];
         if (name.length() == 0)
            name = "IMAGE";
         getters[col] = rowObj.getClass().getMethod("get" + name);
      }
      return getters[col];
   }

   // ----- row access/mutation (indexes == view indexes) -----

   public T getRow(int row) {
      return rows.get(row);
   }

   public List<T> getRows() {
      return rows;
   }

   public int size() {
      return rows.size();
   }

   public void addRow(T row) {
      rows.add(row);
      fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
   }

   public void removeRow(int row) {
      rows.remove(row);
      fireTableRowsDeleted(row, row);
   }

   public void removeRow(T row) {
      int idx = rows.indexOf(row);
      if (idx >= 0)
         removeRow(idx);
   }

   public void updateRow(int row) {
      fireTableRowsUpdated(row, row);
   }

   public void clear() {
      int size = rows.size();
      rows.clear();
      if (size > 0)
         fireTableRowsDeleted(0, size - 1);
   }

   // ----- sorting -----

   @SuppressWarnings("unchecked")
   public void setComparator(String colName, Comparator<?> comparator) {
      for (int i = 0; i < colNames.length; ++i) {
         if (colNames[i].equals(colName))
            comparators.put(i, (Comparator<Object>) comparator);
      }
   }

   // Disable header click sorting for tables where row order is meaningful
   // (job queue, priority lists, etc.)
   public void setSortingEnabled(boolean enabled) {
      sortingEnabled = enabled;
   }

   public void setDefaultSort(String colName, boolean ascending) {
      for (int i = 0; i < colNames.length; ++i) {
         if (colNames[i].equals(colName)) {
            defaultSortColumn = i;
            defaultSortAscending = ascending;
         }
      }
   }

   // Sort backing list according to current sort state (or default sort if
   // none selected yet). Selection is preserved by row object identity.
   public void sort() {
      int col = sortColumn >= 0 ? sortColumn : defaultSortColumn;
      boolean ascending = sortColumn >= 0 ? sortAscending : defaultSortAscending;
      if (col < 0)
         return;
      List<T> selected = selectedRowObjects();
      rows.sort(rowComparator(col, ascending));
      fireTableDataChanged();
      restoreSelection(selected);
      if (table != null) {
         // Keep selection visible following sort (JavaFX setOnSort behavior)
         int row = table.getSelectedRow();
         if (row >= 0)
            table.scrollRectToVisible(table.getCellRect(row, 0, true));
         table.getTableHeader().repaint();
      }
   }

   private Comparator<T> rowComparator(final int col, final boolean ascending) {
      final Comparator<Object> comparator = comparators.get(col);
      return new Comparator<T>() {
         @SuppressWarnings({ "unchecked", "rawtypes" })
         public int compare(T r1, T r2) {
            int result;
            Object v1, v2;
            try {
               v1 = getter(r1, col).invoke(r1);
               v2 = getter(r2, col).invoke(r2);
            } catch (Exception e) {
               return 0;
            }
            if (v1 == null && v2 == null)
               result = 0;
            else if (v1 == null)
               result = -1;
            else if (v2 == null)
               result = 1;
            else if (comparator != null)
               result = comparator.compare(v1, v2);
            else if (v1 instanceof Comparable)
               result = ((Comparable) v1).compareTo(v2);
            else
               result = v1.toString().compareToIgnoreCase(v2.toString());
            return ascending ? result : -result;
         }
      };
   }

   private List<T> selectedRowObjects() {
      List<T> selected = new ArrayList<T>();
      if (table != null) {
         for (int row : table.getSelectedRows()) {
            if (row >= 0 && row < rows.size())
               selected.add(rows.get(row));
         }
      }
      return selected;
   }

   private void restoreSelection(List<T> selected) {
      if (table == null || selected.isEmpty())
         return;
      table.clearSelection();
      for (T rowObj : selected) {
         int idx = rows.indexOf(rowObj);
         if (idx >= 0)
            table.addRowSelectionInterval(idx, idx);
      }
   }

   // Attach model to table: installs header click sorting + sort indicator.
   // Call after new JTable(model).
   public void attach(final JTable table) {
      this.table = table;
      final JTableHeader header = table.getTableHeader();
      header.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent e) {
            if (!sortingEnabled)
               return;
            int viewCol = header.columnAtPoint(e.getPoint());
            if (viewCol < 0)
               return;
            int col = table.convertColumnIndexToModel(viewCol);
            if (col == sortColumn) {
               if (sortAscending) {
                  sortAscending = false; // 2nd click: descending
               } else {
                  sortColumn = -1; // 3rd click: revert to default sort
                  sortAscending = true;
               }
            } else {
               sortColumn = col; // 1st click: ascending
               sortAscending = true;
            }
            sort();
         }
      });
      // Header renderer adding sort direction arrow to sorted column
      final TableCellRenderer base = header.getDefaultRenderer();
      header.setDefaultRenderer(new TableCellRenderer() {
         @Override
         public Component getTableCellRendererComponent(JTable t, Object value,
               boolean isSelected, boolean hasFocus, int row, int column) {
            int col = t.convertColumnIndexToModel(column);
            int activeCol = sortColumn >= 0 ? sortColumn : defaultSortColumn;
            boolean ascending = sortColumn >= 0 ? sortAscending : defaultSortAscending;
            String text = value == null ? "" : value.toString();
            if (col == activeCol && activeCol >= 0)
               text += ascending ? " ▴" : " ▾";
            Component c = base.getTableCellRendererComponent(t, text, isSelected, hasFocus, row, column);
            if (c instanceof JLabel)
               ((JLabel) c).setHorizontalAlignment(JLabel.CENTER);
            return c;
         }
      });
   }
}
