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

public class searchTable {
   private String currentTivo = null;
   public JXTable TABLE = null;
   public JScrollPane scroll = null;
   public String[] TITLE_cols = {"", "TYPE", "SHOW", "DATE", "CHANNEL", "DUR"};
   public Boolean inFolder = false;
   public String folderName = null;
   public int folderEntryNum = -1;
   public Hashtable<String,JSONArray> tivo_data = new Hashtable<String,JSONArray>();
   private Hashtable<String,String> partners = new Hashtable<String,String>();
         
   searchTable(JFrame dialog) {
      Object[][] data = {}; 
      TABLE = new JXTable(data, TITLE_cols);
      TABLE.setModel(new SearchTableModel(data, TITLE_cols));
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
            if (o1 instanceof sortableDuration && o2 instanceof sortableDuration) {
               sortableDuration d1 = (sortableDuration)o1;
               sortableDuration d2 = (sortableDuration)o2;
               if (d1.sortable > d2.sortable) return 1;
               if (d1.sortable < d2.sortable) return -1;
               return 0;
            }
            return 0;
         }
      };
      
      // Use custom sorting routines for certain columns
      Sorter sorter = TABLE.getColumnExt(3).getSorter();
      sorter.setComparator(sortableComparator);
      sorter = TABLE.getColumnExt(5).getSorter();
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
      
      tm = TABLE.getColumnModel().getColumn(2);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
      
      tm = TABLE.getColumnModel().getColumn(3);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
      // Right justify dates
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.RIGHT);
      
      tm = TABLE.getColumnModel().getColumn(4);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
      
      tm = TABLE.getColumnModel().getColumn(5);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
      // Center justify duration
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.CENTER);
               
      //TABLE.setFillsViewportHeight(true);
      //TABLE.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
      
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
   class SearchTableModel extends DefaultTableModel {
      private static final long serialVersionUID = 1L;

      public SearchTableModel(Object[][] data, Object[] columnNames) {
         super(data, columnNames);
      }
      
      @SuppressWarnings("unchecked")
      // This is used to define columns as specific classes
      public Class getColumnClass(int col) {
         if (col == 0) {
            return Icon.class;
         }
         if (col == 3) {
            return sortableDate.class;
         }
         if (col == 5) {
            return sortableDuration.class;
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
            JSONObject json = GetRowData(row);
            if (json != null && json.has("__inTodo__"))
               cell.setBackground(config.tableBkgndProtected);
         }         
         cell.setFont(config.tableFont);
        
         return cell;
      }
   }
   
   private JSONObject GetRowData(int row) {
      return TableUtil.GetRowData(TABLE, row, "DATE");
   }
      
   // Mouse event handler
   // This will display folder entries in table if folder entry single-clicked
   private void MouseClicked(MouseEvent e) {
      if( e.getClickCount() == 1 ) {
         int row = TABLE.rowAtPoint(e.getPoint());
         sortableDate s = (sortableDate)TABLE.getValueAt(row,TableUtil.getColumnIndex(TABLE, "DATE"));
         if (s.folder) {
            folderName = s.folderName;
            folderEntryNum = row;
            setFolderState(true);
            Refresh(s.json);
         }
      }
   }
   
   private void TABLERowSelected(int row) {
      debug.print("row=" + row);
      if (row == -1) return;
      // Get column items for selected row 
      sortableDate s = (sortableDate)TABLE.getValueAt(row,TableUtil.getColumnIndex(TABLE, "DATE"));
      if (s.folder) {
         // Folder entry - don't display anything
      } else {
         try {
            // Non folder entry so print single entry info
            sortableDuration dur = (sortableDuration)TABLE.getValueAt(row,TableUtil.getColumnIndex(TABLE, "DUR"));
            String message = TableUtil.makeShowSummary(s, dur);
            String title = "\nSearch: ";
            if (s.json.has("title"))
               title += s.json.getString("title");
            if (s.json.has("subtitle"))
               title += " - " + s.json.getString("subtitle");            
            if (s.json.has("__inTodo__"))
               title += " (to be recorded on " + s.json.getString("__inTodo__") + ")";
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
   // data is a JSONArray of JSON objects each of following structure:
   // String    title
   // String    type
   // JSONArray entries
   public void AddRows(String tivoName, JSONArray data) {
      Refresh(data);
      TableUtil.packColumns(TABLE, 2);
      
      // Save the data
      currentTivo = tivoName;
      tivo_data.put(tivoName, data);
      
      // Update search tab to show this tivoName
      if (config.gui.remote_gui != null)
         config.gui.remote_gui.setTivoName("search", tivoName);
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
               AddTABLERow(data.getJSONObject(i), true);
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
            AddTABLERow(data.getJSONArray("entries").getJSONObject(i), false);
         }
      } catch (JSONException e) {
         log.error("Refresh - " + e.getMessage());
      }
   }
   
   public void setFolderState(Boolean state) {
      if (state) {
         inFolder = true;
         if (config.gui.remote_gui != null) {
            config.gui.remote_gui.button_search.setText("Back");
            config.gui.remote_gui.button_search.setToolTipText(
               config.gui.remote_gui.getToolTip("refresh_search_folder")
            );
         }
      } else {
         inFolder = false;
         if (config.gui.remote_gui != null) {
            config.gui.remote_gui.button_search.setText("Search");
            config.gui.remote_gui.button_search.setToolTipText(
               config.gui.remote_gui.getToolTip("tivo_search")
            );
         }
      }
   }
      
   // Add a row to table
   public void AddTABLERow(JSONObject entry, Boolean folder) {
      try {
         int cols = TITLE_cols.length;
         Object[] data;
         if (folder) {
            // Initialize to empty strings
            data = new Object[cols];
            for (int i=0; i<cols; ++i) {
               data[i] = "";
            }
            int num = entry.getJSONArray("entries").length();
            if (num > 1) {
               // Multiple items => display as folder
               data[0] = gui.Images.get("folder");
               data[1] = entry.getString("type");
               data[2] = " " + entry.getString("title") + " (" + num + ")";
               String startString = "";
               long start = 0;
               if (entry.getJSONArray("entries").getJSONObject(0).has("startTime")) {
                  startString = entry.getJSONArray("entries").getJSONObject(0).getString("startTime");
                  start = TableUtil.getLongDateFromString(startString);
               }
               else if (entry.getJSONArray("entries").getJSONObject(0).has("releaseDate")) {
                  startString = entry.getJSONArray("entries").getJSONObject(0).getString("releaseDate");
                  start = TableUtil.getLongDateFromString(startString);
               }
               data[3] = new sortableDate(entry.getString("title"), entry, start);
               if (entry.getJSONArray("entries").getJSONObject(0).has("partnerId")) {
                  data[4] = " STREAMING";
               }
            } else {
               // Single item => don't display as folder
               data = makeTableEntry(entry.getJSONArray("entries").getJSONObject(0));
            }
         } else {
            data = makeTableEntry(entry);
         }
         
         TableUtil.AddRow(TABLE, data);
         
         // Adjust column widths to data
         TableUtil.packColumns(TABLE, 2);
      } catch (JSONException e1) {
         log.error("AddTABLERow - " + e1.getMessage());
      }      
   }  
   
   private Object[] makeTableEntry(JSONObject entry) {
      try {
         int cols = TITLE_cols.length;
         Object[] data = new Object[cols];
         // Initialize to empty strings
         for (int i=0; i<cols; ++i) {
            data[i] = "";
         }
         // If entry is in 1 of todo lists then add special __inTodo__ JSON entry
         config.gui.remote_gui.flagIfInTodo(entry, false);
         long start = 0;
         if (entry.has("startTime")) {
            start = TableUtil.getLongDateFromString(entry.getString("startTime"));
         }
         else if (entry.has("releaseDate")) {
            start = TableUtil.getLongDateFromString(entry.getString("releaseDate"));
         }
         long duration = entry.getLong("duration")*1000;
         String type = " ";
         if (entry.has("collectionType")) {
            type = entry.getString("collectionType");
         }
         String title = TableUtil.makeShowTitle(entry);
         if (entry.has("hdtv") && entry.getBoolean("hdtv"))
            title += " [HD]";
         String channel = "";
         if (entry.has("channel"))
            channel = TableUtil.makeChannelName(entry);
         else if (entry.has("partnerId"))
            channel = getPartnerName(entry.getString("partnerId"));
         
         data[1] = type;
         data[2] = title;
         data[3] = new sortableDate(entry, start);
         data[4] = channel;
         data[5] = new sortableDuration(duration, false);
         return data;
      } catch (JSONException e1) {
         log.error("AddTABLERow - " + e1.getMessage());
      }
      return null;
   }
   
   public Boolean isFolder(int row) {
      sortableDate s = (sortableDate)TABLE.getValueAt(row,TableUtil.getColumnIndex(TABLE, "DATE"));
      return s.folder;
   }

   // Look for entry with given folder name and select it
   // (This used when returning back from folder mode to top level mode)
   public void SelectFolder(String folderName) {
      debug.print("folderName=" + folderName);
      for (int i=0; i<TABLE.getRowCount(); ++i) {
         sortableDate s = (sortableDate)TABLE.getValueAt(i,TableUtil.getColumnIndex(TABLE, "DATE"));
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
      else if (keyCode == KeyEvent.VK_R) {
         config.gui.remote_gui.record_search.doClick();
      }
      else if (keyCode == KeyEvent.VK_S) {
         config.gui.remote_gui.recordSP_search.doClick();
      }
      else if (keyCode == KeyEvent.VK_W) {
         config.gui.remote_gui.wishlist_search.doClick();
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
   
   // Schedule a single recording
   public void recordSingle(String tivoName) {
      int[] selected = TableUtil.GetSelectedRows(TABLE);
      if (selected.length > 0) {
         int row;
         JSONArray entries = new JSONArray();
         JSONObject json;
         for (int i=0; i<selected.length; ++i) {
            row = selected[i];
            if ( isFolder(row) )
               continue;
            json = GetRowData(row);
            entries.put(json);
         }
         TableUtil.recordSingleCB(tivoName, entries);
      }
   }
   
   // Create a Season Pass
   public void recordSP(final String tivoName) {
      final int[] selected = TableUtil.GetSelectedRows(TABLE);
      // First check if all selected entries are of type 'series'
      for (int i=0; i<selected.length; ++i) {
         int row = selected[i];
         if ( isFolder(row) )
            continue;
         JSONObject json = GetRowData(row);
         if (json != null) {
            try {
               String type = json.getString("collectionType");
               if (! type.equals("series")) {
                  log.error("Selected entry not of type 'series': " + json.getString("title"));
                  return;
               }
            } catch (JSONException e) {
               log.error("search_sp_recordCB - " + e.getMessage());
               return;
            }
         }
      }

      // Proceed with SP scheduling
      class backgroundRun extends SwingWorker<Object, Object> {
         protected Object doInBackground() {
            int[] selected = TableUtil.GetSelectedRows(TABLE);
            if (selected.length > 0) {
               int row;
               JSONArray existing;
               JSONObject json;
               Remote r = config.initRemote(tivoName);
               if (r.success) {
                  // First load existing SPs from tivoName to check against
                  log.warn("Checking existing season passes on '" + tivoName + "' ...");
                  existing = r.SeasonPasses(null);
                  if (existing == null) {
                     log.error("Failed to grab existing SPs to check against for TiVo: " + tivoName);
                     r.disconnect();
                     return null;
                  }
                  // Now proceed with subscriptions
                  for (int i=0; i<selected.length; ++i) {
                     row = selected[i];
                     if ( isFolder(row) )
                        continue;
                     json = GetRowData(row);
                     if (json != null)
                        r.SPschedule(tivoName, json, existing);
                }
                  r.disconnect();
               }
            }
            return null;
         }
      }
      backgroundRun b = new backgroundRun();
      b.execute();
   }
   
   // Return friendly name of a partner based on id, such as Netflix, Hulu, etc.
   private String getPartnerName(String partnerId) {
	  String name = partnerId;
      if (partners.size() == 0) {
          log.warn("Refreshing partner names");
          Remote r = config.initRemote(currentTivo);
          if (r.success) {
             try {
                JSONObject json = new JSONObject();
                json.put("bodyId", r.bodyId_get());
                json.put("noLimit", true);
                json.put("levelOfDetail", "high");
                JSONObject result = r.Command("partnerInfoSearch", json);
                if (result != null && result.has("partnerInfo")) {
             	   JSONArray info = result.getJSONArray("partnerInfo");
             	   for (int i=0; i<info.length(); ++i) {
             		   JSONObject j = info.getJSONObject(i);
             		   if (j.has("partnerId") && j.has("displayName")) {
             			   partners.put(j.getString("partnerId"), j.getString("displayName"));
             		   }
             	   }
                }            	   
             } catch (JSONException e1) {
                log.error("getPartnerName - " + e1.getMessage());
             }
             r.disconnect();
          }
       }
      
	   if (partners.containsKey(partnerId))
		   name = partners.get(partnerId);
	   return name;
   }
}
