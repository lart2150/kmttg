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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;

import com.tivo.kmttg.gui.table.imageCell;
import com.tivo.kmttg.util.log;

/**
 * JTable based tree table - replaces JavaFX TreeTableView for the Now Playing
 * List (folders one level deep with expandable children).
 *
 * The tree is flattened into a visible row list so that the row index based
 * APIs of the former TreeTableView are preserved: getTreeItem(row) and
 * getExpandedItemCount() address visible rows exactly like JavaFX did.
 *
 * Sorting reorders the tree structure itself (each level sorted by the
 * active column comparator) so view index == visible index always holds.
 */
public class TreeTable<T> {

   public static class TreeItem<T> {
      private T value;
      private TreeItem<T> parent = null;
      private final List<TreeItem<T>> children = new ArrayList<TreeItem<T>>();
      private boolean expanded = false;
      private TreeTable<T> owner = null;

      public TreeItem(T value) {
         this.value = value;
      }

      public T getValue() {
         return value;
      }

      public void setValue(T value) {
         this.value = value;
         if (owner != null)
            owner.fireItemChanged(this);
      }

      // Read-only access; mutate via addChild/removeChild/clearChildren
      public List<TreeItem<T>> getChildren() {
         return children;
      }

      public TreeItem<T> getParent() {
         return parent;
      }

      public boolean isLeaf() {
         return children.isEmpty();
      }

      public boolean isExpanded() {
         return expanded;
      }

      public void setExpanded(boolean expand) {
         if (expanded == expand)
            return;
         expanded = expand;
         if (owner != null)
            owner.expansionChanged(expand);
      }

      public void addChild(TreeItem<T> child) {
         child.parent = this;
         child.setOwner(owner);
         children.add(child);
         if (owner != null)
            owner.refresh();
      }

      public void removeChild(TreeItem<T> child) {
         children.remove(child);
         child.parent = null;
         if (owner != null)
            owner.refresh();
      }

      public void clearChildren() {
         children.clear();
         if (owner != null)
            owner.refresh();
      }

      private void setOwner(TreeTable<T> owner) {
         this.owner = owner;
         for (TreeItem<T> child : children)
            child.setOwner(owner);
      }
   }

   private static final int INDENT = 16;
   private static final int ARROW_WIDTH = 16;

   public final JTable table;
   private final Model model;
   private final TreeItem<T> root;
   private final String[] colNames;
   private final Hashtable<Integer,Comparator<Object>> comparators = new Hashtable<Integer,Comparator<Object>>();
   private Method[] getters = null;
   private int sortColumn = -1;
   private boolean sortAscending = true;
   private int defaultSortColumn = -1;
   private boolean defaultSortAscending = true;
   private List<TreeItem<T>> visible = new ArrayList<TreeItem<T>>();
   private Runnable onExpand = null;
   private RowColorer<T> colorer = null;

   public TreeTable(String[] colNames) {
      this.colNames = colNames.clone();
      root = new TreeItem<T>(null);
      root.expanded = true;
      root.setOwner(this);
      model = new Model();
      table = new JTable(model) {
         private static final long serialVersionUID = 1L;
         @Override
         public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
            Component c = super.prepareRenderer(renderer, row, column);
            if (isRowSelected(row)) {
               c.setBackground(getSelectionBackground());
            } else {
               Color color = null;
               if (colorer != null && row < visible.size())
                  color = colorer.getColor(visible.get(row).getValue());
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
      // Tree rendering (indent + disclosure arrow) on first model column
      table.getColumnModel().getColumn(0).setCellRenderer(new TreeColumnRenderer());
      installSorting();
      installToggleHandlers();
   }

   private class Model extends AbstractTableModel {
      private static final long serialVersionUID = 1L;
      @Override
      public int getRowCount() {
         return visible.size();
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
         T value = visible.get(row).getValue();
         if (value == null)
            return null;
         try {
            return getter(value, col).invoke(value);
         } catch (Exception e) {
            log.error("TreeTable getValueAt - " + e.toString());
            return null;
         }
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

   // ----- tree access (row indexes address visible rows) -----

   public TreeItem<T> getRoot() {
      return root;
   }

   public TreeItem<T> getTreeItem(int row) {
      return visible.get(row);
   }

   public int getExpandedItemCount() {
      return visible.size();
   }

   public int getRow(TreeItem<T> item) {
      return visible.indexOf(item);
   }

   public Object getValueAt(int row, int col) {
      return model.getValueAt(row, col);
   }

   public void setOnExpand(Runnable r) {
      onExpand = r;
   }

   public void setRowColorer(RowColorer<T> colorer) {
      this.colorer = colorer;
   }

   // Rebuild visible row list from tree structure, preserving selection
   public void refresh() {
      List<TreeItem<T>> selected = selectedItems();
      rebuildVisible();
      model.fireTableDataChanged();
      restoreSelection(selected);
   }

   private void rebuildVisible() {
      visible = new ArrayList<TreeItem<T>>();
      addVisible(root);
   }

   private void addVisible(TreeItem<T> item) {
      for (TreeItem<T> child : item.children) {
         visible.add(child);
         if (!child.isLeaf() && child.expanded)
            addVisible(child);
      }
   }

   private void expansionChanged(boolean expand) {
      refresh();
      if (expand && onExpand != null)
         onExpand.run();
   }

   private void fireItemChanged(TreeItem<T> item) {
      int row = visible.indexOf(item);
      if (row >= 0)
         model.fireTableRowsUpdated(row, row);
   }

   // ----- selection helpers -----

   public List<TreeItem<T>> selectedItems() {
      List<TreeItem<T>> selected = new ArrayList<TreeItem<T>>();
      for (int row : table.getSelectedRows()) {
         if (row >= 0 && row < visible.size())
            selected.add(visible.get(row));
      }
      return selected;
   }

   private void restoreSelection(List<TreeItem<T>> selected) {
      table.clearSelection();
      for (TreeItem<T> item : selected) {
         int idx = visible.indexOf(item);
         if (idx >= 0)
            table.addRowSelectionInterval(idx, idx);
      }
   }

   public void select(TreeItem<T> item) {
      // Make sure parent folder is expanded so item is visible
      if (item.getParent() != null && item.getParent() != root)
         item.getParent().setExpanded(true);
      int row = visible.indexOf(item);
      if (row >= 0) {
         table.addRowSelectionInterval(row, row);
         scrollToCenter(row);
      }
   }

   public void select(int row) {
      if (row >= 0 && row < visible.size())
         table.addRowSelectionInterval(row, row);
   }

   public void clearSelection() {
      table.clearSelection();
   }

   public boolean isSelected(int row) {
      return table.isRowSelected(row);
   }

   public void scrollToCenter(int row) {
      Rectangle rect = table.getCellRect(row, 0, true);
      table.scrollRectToVisible(rect);
   }

   // ----- sorting -----

   @SuppressWarnings("unchecked")
   public void setComparator(String colName, Comparator<?> comparator) {
      for (int i = 0; i < colNames.length; ++i) {
         if (colNames[i].equals(colName))
            comparators.put(i, (Comparator<Object>) comparator);
      }
   }

   public void setDefaultSort(String colName, boolean ascending) {
      for (int i = 0; i < colNames.length; ++i) {
         if (colNames[i].equals(colName)) {
            defaultSortColumn = i;
            defaultSortAscending = ascending;
         }
      }
   }

   // Sort all levels of the tree by active sort column
   public void sort() {
      int col = sortColumn >= 0 ? sortColumn : defaultSortColumn;
      boolean ascending = sortColumn >= 0 ? sortAscending : defaultSortAscending;
      if (col < 0)
         return;
      List<TreeItem<T>> selected = selectedItems();
      Comparator<TreeItem<T>> comparator = itemComparator(col, ascending);
      sortLevel(root, comparator);
      rebuildVisible();
      model.fireTableDataChanged();
      restoreSelection(selected);
      // Keep selection visible following sort (JavaFX setOnSort behavior)
      int row = table.getSelectedRow();
      if (row >= 0)
         scrollToCenter(row);
      table.getTableHeader().repaint();
   }

   private void sortLevel(TreeItem<T> item, Comparator<TreeItem<T>> comparator) {
      item.children.sort(comparator);
      for (TreeItem<T> child : item.children) {
         if (!child.isLeaf())
            sortLevel(child, comparator);
      }
   }

   private Comparator<TreeItem<T>> itemComparator(final int col, final boolean ascending) {
      final Comparator<Object> comparator = comparators.get(col);
      return new Comparator<TreeItem<T>>() {
         @SuppressWarnings({ "unchecked", "rawtypes" })
         public int compare(TreeItem<T> i1, TreeItem<T> i2) {
            int result;
            Object v1 = null, v2 = null;
            try {
               if (i1.getValue() != null)
                  v1 = getter(i1.getValue(), col).invoke(i1.getValue());
               if (i2.getValue() != null)
                  v2 = getter(i2.getValue(), col).invoke(i2.getValue());
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

   private void installSorting() {
      final JTableHeader header = table.getTableHeader();
      header.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent e) {
            int viewCol = header.columnAtPoint(e.getPoint());
            if (viewCol < 0)
               return;
            int col = table.convertColumnIndexToModel(viewCol);
            if (col == sortColumn) {
               if (sortAscending) {
                  sortAscending = false;
               } else {
                  sortColumn = -1;
                  sortAscending = true;
               }
            } else {
               sortColumn = col;
               sortAscending = true;
            }
            sort();
         }
      });
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

   // ----- expand/collapse interaction -----

   private void installToggleHandlers() {
      table.addMouseListener(new MouseAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            int row = table.rowAtPoint(e.getPoint());
            int viewCol = table.columnAtPoint(e.getPoint());
            if (row < 0 || viewCol < 0 || row >= visible.size())
               return;
            TreeItem<T> item = visible.get(row);
            if (item.isLeaf())
               return;
            if (e.getClickCount() >= 2) {
               item.setExpanded(!item.isExpanded());
               return;
            }
            // Single click within disclosure arrow zone of tree column
            if (table.convertColumnIndexToModel(viewCol) == 0) {
               Rectangle rect = table.getCellRect(row, viewCol, true);
               if (e.getX() - rect.x < ARROW_WIDTH)
                  item.setExpanded(!item.isExpanded());
            }
         }
      });
      table.addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(KeyEvent e) {
            int row = table.getSelectionModel().getLeadSelectionIndex();
            if (row < 0 || row >= visible.size())
               return;
            TreeItem<T> item = visible.get(row);
            if (e.getKeyCode() == KeyEvent.VK_RIGHT && !item.isLeaf() && !item.isExpanded()) {
               item.setExpanded(true);
               e.consume();
            } else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
               if (!item.isLeaf() && item.isExpanded()) {
                  item.setExpanded(false);
                  e.consume();
               } else if (item.getParent() != null && item.getParent() != root) {
                  // Inside a folder - collapse parent and select it
                  TreeItem<T> parent = item.getParent();
                  parent.setExpanded(false);
                  clearSelection();
                  select(parent);
                  e.consume();
               }
            }
         }
      });
   }

   // Renderer for the tree column: indent + disclosure arrow + value
   private class TreeColumnRenderer extends JPanel implements TableCellRenderer {
      private static final long serialVersionUID = 1L;
      private JLabel arrow = new JLabel();
      private ImageCellRenderer imageRenderer = new ImageCellRenderer();
      private DefaultTableCellRenderer textRenderer = new DefaultTableCellRenderer();

      public TreeColumnRenderer() {
         super(new BorderLayout());
         setOpaque(true);
         arrow.setHorizontalAlignment(JLabel.CENTER);
         add(arrow, BorderLayout.WEST);
      }

      @Override
      public Component getTableCellRendererComponent(JTable t, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {
         TreeItem<T> item = row < visible.size() ? visible.get(row) : null;
         int indent = 0;
         String arrowText = " ";
         if (item != null) {
            if (item.getParent() != null && item.getParent() != root)
               indent = INDENT;
            if (!item.isLeaf())
               arrowText = item.isExpanded() ? "▾" : "▸";
         }
         arrow.setText(arrowText);
         arrow.setPreferredSize(new java.awt.Dimension(ARROW_WIDTH + indent, 1));
         arrow.setFont(t.getFont());

         Component inner;
         if (value instanceof imageCell) {
            inner = imageRenderer.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
         } else {
            inner = textRenderer.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
         }
         removeAll();
         add(arrow, BorderLayout.WEST);
         add(inner, BorderLayout.CENTER);
         if (isSelected) {
            setBackground(t.getSelectionBackground());
            arrow.setForeground(t.getSelectionForeground());
         } else {
            setBackground(t.getBackground());
            arrow.setForeground(t.getForeground());
         }
         return this;
      }

      // prepareRenderer sets background/foreground on the returned component
      // (this panel); propagate to inner components so row coloring shows
      @Override
      public void setBackground(Color color) {
         super.setBackground(color);
         for (Component c : getComponents())
            c.setBackground(color);
      }

      @Override
      public void setForeground(Color color) {
         super.setForeground(color);
         for (Component c : getComponents()) {
            if (c instanceof JPanel) {
               // ImageCellRenderer panel - propagate to its labels
               for (Component inner : ((JPanel) c).getComponents())
                  inner.setForeground(color);
            } else {
               c.setForeground(color);
            }
         }
      }
   }

   // ----- column header text (SetHeaderText support) -----

   public String getColumnName(int col) {
      return colNames[col];
   }

   public void setColumnName(int col, String text) {
      colNames[col] = text;
      int viewCol = table.convertColumnIndexToView(col);
      if (viewCol >= 0)
         table.getColumnModel().getColumn(viewCol).setHeaderValue(text);
      table.getTableHeader().repaint();
   }
}
