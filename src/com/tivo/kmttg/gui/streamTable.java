package com.tivo.kmttg.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.Hashtable;

import javax.swing.Icon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;

import org.jdesktop.swingx.JXTable;
import org.jdesktop.swingx.decorator.Sorter;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class streamTable {
   private String currentTivo = null;
   public JXTable TABLE = null;
   public JScrollPane scroll = null;
   public String[] TITLE_cols = {"", "CREATED", "ITEM", "SOURCE"};
   public Boolean inFolder = false;
   public String folderName = null;
   private JSONObject currentEntry = null;
   public int folderEntryNum = -1;
   public Hashtable<String,JSONArray> tivo_data = new Hashtable<String,JSONArray>();
   public Hashtable<String,JSONArray> episode_data = new Hashtable<String,JSONArray>();
         
   streamTable(JFrame dialog) {
      Object[][] data = {}; 
      TABLE = new JXTable(data, TITLE_cols);
      TABLE.setModel(new GuideTableModel(data, TITLE_cols));
      TABLE.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
      scroll = new JScrollPane(TABLE);
      
      // Add listener for click handling (for folder entries)
      TABLE.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               MouseClicked(e);
            }
         }
      );
      
      // Add keyboard listener
      TABLE.addKeyListener(
         new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
               KeyPressed(e);
            }
         }
      );
      
      // Define custom column sorting routines
      Comparator<Object> sortableComparator = new Comparator<Object>() {
         public int compare(Object o1, Object o2) {
            if (o1 instanceof sortableDate && o2 instanceof sortableDate) {
               sortableDate s1 = (sortableDate)o1;
               sortableDate s2 = (sortableDate)o2;
               long l1 = Long.parseLong(s1.sortable);
               long l2 = Long.parseLong(s2.sortable);
               if (l1 > l2) return 1;
               if (l1 < l2) return -1;
               return 0;
            }
            return 0;
         }
      };
      
      // Use custom sorting routines for certain columns
      Sorter sorter = TABLE.getColumnExt(1).getSorter();
      sorter.setComparator(sortableComparator);
      
      // Define selection listener to detect table row selection changes
      TABLE.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
         public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting()) return;
            ListSelectionModel rowSM = (ListSelectionModel)e.getSource();
            int row = rowSM.getMinSelectionIndex();
            if (row > -1) {
               TABLERowSelected(row);
            }
         }
      });
                        
      // Change color & font
      TableColumn tm;
      // Allow icons in column 0
      TABLE.setDefaultRenderer(Icon.class, new IconCellRenderer());
      
      tm = TABLE.getColumnModel().getColumn(1);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
      // Right justify dates
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.RIGHT);
      
      tm = TABLE.getColumnModel().getColumn(2);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
      
      tm = TABLE.getColumnModel().getColumn(3);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
               
      // Add right mouse button handler
      TableUtil.AddRightMouseListener(TABLE);
   }   

   // Custom table cell renderer to allow for icons
   class IconCellRenderer extends DefaultTableCellRenderer {
      private static final long serialVersionUID = 1L;

      protected void setValue(Object value) {
         debug.print("value=" + value);
         if (value instanceof Icon) {
            setIcon((Icon)value);
            super.setValue(null);
         } else {
            setIcon(null);
            super.setValue(value);
         }
      }
      
      public Component getTableCellRendererComponent
      (JTable table, Object value, boolean isSelected,
       boolean hasFocus, int row, int column) {
         Component cell = super.getTableCellRendererComponent(
            table, value, isSelected, hasFocus, row, column
         );
        
         if ( ! isSelected ) {
            if (column % 2 == 0)
               cell.setBackground(config.tableBkgndLight);
            else
               cell.setBackground(config.tableBkgndDarker);
         }            
         return cell;
      }
   }
   
   // Override some default table model actions
   class GuideTableModel extends DefaultTableModel {
      private static final long serialVersionUID = 1L;

      public GuideTableModel(Object[][] data, Object[] columnNames) {
         super(data, columnNames);
      }
      
      @SuppressWarnings("unchecked")
      // This is used to define columns as specific classes
      public Class getColumnClass(int col) {
         if (col == 0) {
            return Icon.class;
         }
         if (col == 1) {
            return sortableDate.class;
         }
         return Object.class;
      } 
      
      // Set all cells uneditable
      public boolean isCellEditable(int row, int column) {        
         return false;
      }
   }   
   
   /**
   * Applied background color to single column of a JTable
   * in order to distinguish it apart from other columns.
   */ 
   class ColorColumnRenderer extends DefaultTableCellRenderer 
   {
      /**
       * 
       */
      private static final long serialVersionUID = 1L;
      Color bkgndColor;
      Font font;
      
      public ColorColumnRenderer(Color bkgnd, Font font) {
         super(); 
         bkgndColor = bkgnd;
         this.font = font;
      }
      
      public Component getTableCellRendererComponent
          (JTable table, Object value, boolean isSelected,
           boolean hasFocus, int row, int column) 
      {
         Component cell = super.getTableCellRendererComponent
            (table, value, isSelected, hasFocus, row, column);
         
         if ( ! isSelected ) {
            if (column % 2 == 0)
               cell.setBackground(config.tableBkgndLight);
            else
               cell.setBackground(config.tableBkgndDarker);            
         }         
         cell.setFont(config.tableFont);
        
         return cell;
      }
   }
   
   private JSONObject GetRowData(int row) {
      return TableUtil.GetRowData(TABLE, row, "CREATED");
   }
      
   // Mouse event handler
   // This will display folder entries in table if folder entry single-clicked
   private void MouseClicked(MouseEvent e) {
      if( e.getClickCount() == 1 ) {
         int row = TABLE.rowAtPoint(e.getPoint());
         sortableDate s = (sortableDate)TABLE.getValueAt(row,TableUtil.getColumnIndex(TABLE, "CREATED"));
         if (s.folder) {
            folderName = s.folderName;
            folderEntryNum = row;
            setFolderState(true);
            currentEntry = s.json;
            updateFolder();
         }
      }
   }
      
   private void TABLERowSelected(int row) {
      debug.print("row=" + row);
      if (row == -1) return;
      // Get column items for selected row 
      sortableDate s = (sortableDate)TABLE.getValueAt(row,TableUtil.getColumnIndex(TABLE, "CREATED"));
      if (s.folder) {
         // Folder entry - don't display anything
      } else {
         try {
            // Non folder entry so print single entry info
            String message = TableUtil.makeShowSummary(s, null);
            String title = "\nStreaming: ";
            if (s.json.has("title"))
               title += s.json.getString("title");
            if (s.json.has("subtitle"))
               title += " - " + s.json.getString("subtitle");
            log.warn(title);
            log.print(message);
            
            if (config.gui.show_details.isShowing())
               config.gui.show_details.update(currentTivo, s.json);
         } catch (JSONException e) {
            log.error("TABLERowSelected - " + e.getMessage());
            return;
         }
      }
   }

   // Update table to display given entries
   // data is a JSONArray of channel JSON objects
   public void AddRows(String tivoName, JSONArray data) {
      Refresh(data);
      TableUtil.packColumns(TABLE, 2);
      
      // Save the data
      currentTivo = tivoName;
      tivo_data.put(tivoName, data);
      
      // Update stream tab to show this tivoName
      if (config.gui.remote_gui != null)
         config.gui.remote_gui.setTivoName("stream", tivoName);
   }
   
   // Refresh whole table
   public void Refresh(JSONArray data) {
      if (data == null) {
         if (currentTivo != null)
            AddRows(currentTivo, tivo_data.get(currentTivo));
         return;
      }
      if (TABLE != null) {
         // Top level folder structure
         TableUtil.clear(TABLE);
         // Add all folders
         for (int i=0; i<data.length(); ++i) {
            try {
               AddTABLERow(data.getJSONObject(i));
            } catch (JSONException e) {
               log.error("Refresh - " + e.getMessage());
            }
         }
      }
   }
   
   // Refresh to show inside a particular folder
   public void Refresh(JSONObject data) {
      TableUtil.clear(TABLE);
      try {
         for (int i=0; i<data.getJSONArray("entries").length(); ++i) {
            AddTABLERow(data.getJSONArray("entries").getJSONObject(i));
         }
      } catch (JSONException e) {
         log.error("Refresh - " + e.getMessage());
      }
   }
   
   public void setFolderState(Boolean state) {
      if (state) {
         inFolder = true;
         if (config.gui.remote_gui != null) {
            config.gui.remote_gui.remove_stream.setEnabled(false);
            config.gui.remote_gui.refresh_stream.setText("Back");
            config.gui.remote_gui.refresh_stream.setToolTipText(
               config.gui.remote_gui.getToolTip("back_stream")
            );
         }
      } else {
         inFolder = false;
         if (config.gui.remote_gui != null) {
            config.gui.remote_gui.remove_stream.setEnabled(true);
            config.gui.remote_gui.refresh_stream.setText("Refresh");
            config.gui.remote_gui.refresh_stream.setToolTipText(
               config.gui.remote_gui.getToolTip("refresh_stream")
            );
         }
      }
   }
      
   // Add row to table
   public void AddTABLERow(JSONObject entry) {
      TableUtil.AddRow(TABLE, makeTableEntry(entry));
      
      // Adjust column widths to data
      TableUtil.packColumns(TABLE, 2);
   }  
   
   private Object[] makeTableEntry(JSONObject entry) {
      try {
         int cols = TITLE_cols.length;
         Object[] data = new Object[cols];
         // Initialize to empty strings
         for (int i=0; i<cols; ++i) {
            data[i] = "";
         }
         long start = 0;
         if (entry.has("startTime"))
            start = TableUtil.getLongDateFromString(entry.getString("startTime"));
         String title = TableUtil.makeShowTitle(entry);
         if (entry.has("isFolder") && entry.getBoolean("isFolder")) {
            data[0] = gui.Images.get("folder");
            data[1] = new sortableDate(title, entry, start);
         } else {
            data[1] = new sortableDate(entry, start);
         }
         data[2] = title;
         data[3] = TableUtil.getPartnerName(entry);
         return data;
      } catch (JSONException e1) {
         log.error("AddTABLERow - " + e1.getMessage());
      }
      return null;
   }
   
   // Look for entry with given folder name and select it
   // (This used when returning back from folder mode to top level mode)
   public void SelectFolder(String folderName) {
      debug.print("folderName=" + folderName);
      for (int i=0; i<TABLE.getRowCount(); ++i) {
         sortableDate s = (sortableDate)TABLE.getValueAt(i,TableUtil.getColumnIndex(TABLE, "CREATED"));
         if (s.folder) {
            if (s.folderName.equals(folderName)) {
               TABLE.clearSelection();
               try {
                  TABLE.setRowSelectionInterval(i,i);
                  TableUtil.scrollToCenter(TABLE, i);
               }
               catch (Exception e) {
                  // This is here because JXTable seems to have a problem sometimes after table cleared
                  // and an item is selected. This prevents nasty stack trace problem from being
                  // printed to message window
                  System.out.println("Exception: " + e.getMessage());
               }
               return;
            }
         }
      }
   }
   
   // Handle keyboard presses
   private void KeyPressed(KeyEvent e) {
      if (e.isControlDown())
         return;
      int keyCode = e.getKeyCode();
      if (keyCode == KeyEvent.VK_I) {
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         JSONObject json = GetRowData(selected[0]);
         if (json != null) {
            config.gui.show_details.update(currentTivo, json);
         }
      }
      else if (keyCode == KeyEvent.VK_J) {
         // Print json of selected row to log window
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         JSONObject json = GetRowData(selected[0]);
         if (json != null)
            rnpl.pprintJSON(json);
      } else if (keyCode == KeyEvent.VK_Q) {
         // Web query currently selected entry
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         JSONObject json = GetRowData(selected[0]);
         if (json != null && json.has("title")) {
            try {
               String title = json.getString("title");
               if (json.has("subtitle"))
                  title = title + " - " + json.getString("subtitle");
               TableUtil.webQuery(title);
            } catch (JSONException e1) {
               log.error("KeyPressed Q - " + e1.getMessage());
            }
         }
      } else {
         // Pass along keyboard action
         e.consume();
      }
   }
   
   // Get all episodes for a specific collectionId
   public void updateFolder() {
      TableUtil.clear(TABLE);
      try {         
         final String tivoName = currentTivo;
         final String title = currentEntry.getString("title");
         final String collectionId = currentEntry.getString("collectionId");
         final String partnerId = currentEntry.getString("partnerId");
         if (episode_data.containsKey(collectionId)) {
            Refresh(episode_data.get(collectionId));
            return;
         }
         class backgroundRun extends SwingWorker<Object, Object> {
            protected Object doInBackground() {
               Remote r = config.initRemote(tivoName);
               if (r.success) {
                  log.warn(">> Collecting episode data for: " + title);
                  JSONArray entries = r.getEpisodes(collectionId);
                  r.disconnect();
                  if (entries != null && entries.length() > 0) {
                     try {
                        for (int i=0; i<entries.length(); ++i)
                           entries.getJSONObject(i).put("partnerId", partnerId);
                     } catch (JSONException e) {
                        log.error("streamTable updateFolder - " + e.getMessage());
                     }
                  }
                  episode_data.put(collectionId, entries);
                  Refresh(entries);
               } // if r.success
               return null;
            }
         }
         backgroundRun b = new backgroundRun();
         b.execute();
      } catch (JSONException e) {
         log.error("streamTable updateFolder - " + e.getMessage());
      }
   }
   
   // Attempt to remove currently selected top view table item(s)
   public void removeButtonCB() {
      final String tivoName = currentTivo;
      final int[] selected = TableUtil.GetSelectedRows(TABLE);
      if (selected.length > 0) {
         class backgroundRun extends SwingWorker<Object, Object> {
            protected Object doInBackground() {
               try {
                  Boolean removed = false;
                  Remote r = config.initRemote(tivoName);
                  if (r.success) {
                     int row;
                     for (int i=0; i<selected.length; ++i) {
                        row = selected[i];
                        JSONObject json = GetRowData(row);
                        if (json.has("isFolder") && json.getBoolean("isFolder")) {
                           // A One Pass streaming entry should be removed from Season Passes tab
                           log.warn("NOTE: Must remove using 'Season Passes' tab': " + json.getString("title"));
                        } else {
                           // A non-One Pass entry can be removed
                           if (json.has("contentId")) {
                              JSONObject o = new JSONObject();
                              o.put("contentId", json.getString("contentId"));
                              JSONObject result = r.Command("ContentLocatorRemove", o);
                              if (result != null) {
                                 removed = true;
                                 log.warn("Removed streaming item: " + json.getString("title"));
                              }
                           }
                        }
                     } // for
                     r.disconnect();
                     if (removed) {
                        // Force a table refresh if any items were removed
                        config.gui.remote_gui.refresh_stream.doClick();
                     }
                  } // if r.success
               } catch (JSONException e) {
                  log.error("removeButtonCB - " + e.getMessage());
               }
               return null;
            }
         }
         backgroundRun b = new backgroundRun();
         b.execute();
      }
   }
            
}
