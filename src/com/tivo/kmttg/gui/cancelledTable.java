package com.tivo.kmttg.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Stack;
import java.util.Vector;

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

public class cancelledTable {
   private String currentTivo = null;
   public JXTable TABLE = null;
   public JScrollPane scroll = null;
   public String[] TITLE_cols = {"", "SHOW", "DATE", "CHANNEL", "DUR"};
   public Boolean inFolder = false;
   public String folderName = null;
   public int folderEntryNum = -1;
   private Hashtable<String,Stack<JSONObject>> folders = null;
   private Vector<JSONObject> sortedOrder = null;
   public Hashtable<String,JSONArray> tivo_data = new Hashtable<String,JSONArray>();
         
   cancelledTable(JFrame dialog) {
      Object[][] data = {}; 
      TABLE = new JXTable(data, TITLE_cols);
      TABLE.setModel(new CancelledTableModel(data, TITLE_cols));
      TABLE.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
      scroll = new JScrollPane(TABLE);
      // Add keyboard listener
      TABLE.addKeyListener(
         new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
               KeyPressed(e);
            }
         }
      );
      
      // Add listener for click handling (for folder entries)
      TABLE.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               MouseClicked(e);
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
      Sorter sorter = TABLE.getColumnExt(2).getSorter();
      sorter.setComparator(sortableComparator);
      sorter = TABLE.getColumnExt(4).getSorter();
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
      // Right justify dates
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.RIGHT);
      
      tm = TABLE.getColumnModel().getColumn(3);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
      
      tm = TABLE.getColumnModel().getColumn(4);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
      // Center justify duration
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.CENTER);
               
      //TABLE.setFillsViewportHeight(true);
      //TABLE.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
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
   class CancelledTableModel extends DefaultTableModel {
      private static final long serialVersionUID = 1L;

      public CancelledTableModel(Object[][] data, Object[] columnNames) {
         super(data, columnNames);
      }
      
      @SuppressWarnings("unchecked")
      // This is used to define columns as specific classes
      public Class getColumnClass(int col) {
         if (col == 0) {
            return Icon.class;
         }
         if (col == 2) {
            return sortableDate.class;
         }
         if (col == 4) {
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
   
   // Handle keyboard presses
   private void KeyPressed(KeyEvent e) {
      int keyCode = e.getKeyCode();
      if (keyCode == KeyEvent.VK_J) {
         // Print json of selected row to log window
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         JSONObject json = GetRowData(selected[0]);
         if (json != null)
            rnpl.printJSON(json);
      } else {
         // Pass along keyboard action
         e.consume();
      }
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
            Refresh(s.folderData_json);
         }
      }
   }
   
   // Procedure to mimic clicking on folder entry in row 0
   public void enterFirstFolder() {
      int row = 0;
      sortableDate s = (sortableDate)TABLE.getValueAt(row,TableUtil.getColumnIndex(TABLE, "DATE"));
      if (s.folder) {
         folderName = s.folderName;
         folderEntryNum = row;
         setFolderState(true);
         Refresh(s.folderData_json);
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
            String title = "\nWill not record: ";
            if (s.json.has("title"))
               title += s.json.getString("title");
            if (s.json.has("subtitle"))
               title += " - " + s.json.getString("subtitle");
            if (s.json.has("__inTodo__"))
               title += " (to be recorded on " + s.json.getString("__inTodo__") + ")";
            log.warn(title);
            log.print(message);
         } catch (JSONException e) {
            log.error("TABLERowSelected - " + e.getMessage());
            return;
         }
      }
   }

   // Update table to display given entries
   public void AddRows(String tivoName, JSONArray data) {
      try {
         Stack<JSONObject> o = new Stack<JSONObject>();
         JSONObject json;
         long now = new Date().getTime();
         long start;
         Boolean includePast = config.gui.remote_gui.includeHistory_cancel.isSelected();
         for (int i=0; i<data.length(); ++i) {
            json = data.getJSONObject(i);
            if (includePast) {
               // No filter - include all
               o.add(json);
            } else {
               // Filter out past recordings
               start = getStartTime(json);
               if (start >= now)
                  o.add(json);
            }
         }
         // Reset local entries/folders hashes to new entries
         folderize(o); // create folder structure
         Refresh(o);
         TableUtil.packColumns(TABLE, 2);
         tivo_data.put(tivoName, data);
         currentTivo = tivoName;
         if (config.gui.remote_gui != null)
            config.gui.remote_gui.setTivoName("cancel", tivoName);
      } catch (JSONException e) {
         log.print("AddRows - " + e.getMessage());
      }      
   }
   
   // Refresh table with given given entries
   public void Refresh(Stack<JSONObject> o) {
      if (o == null) {
         if (currentTivo != null)
            AddRows(currentTivo, tivo_data.get(currentTivo));
         return;
      }
      if (TABLE != null) {
         // Folder based structure
         if (inFolder) {
            // Display a particular folder
            displayFlatStructure(o);
         } else {
            // Top level folder structure
            displayFolderStructure();
         }
      }
   }
   
   // Update table display to show either top level flat structure or inside a folder
   private void displayFlatStructure(Stack<JSONObject> o) {
      TableUtil.clear(TABLE);
      for (int i=0; i<o.size(); ++i) {
         AddTABLERow(o.get(i));
      }
   }
   
   // Update table display to show top level folderized entries
   public void displayFolderStructure() {
      debug.print("");
      TableUtil.clear(TABLE);
      // Folder based structure
      String name;
      // Add all folders
      for (int i=0; i<sortedOrder.size(); ++i) {
         try {
            name = sortedOrder.get(i).getString("__folderName__");
            AddTABLERow(name, folders.get(name));
         } catch (JSONException e) {
            log.print("displayFolderStructure - " + e.getMessage());
         }
      }
   }
   
   public void setFolderState(Boolean state) {
      if (state) {
         inFolder = true;
         if (config.gui.remote_gui != null) {
            config.gui.remote_gui.refresh_cancel.setText("Back");
            config.gui.remote_gui.refresh_cancel.setToolTipText(
               config.gui.remote_gui.getToolTip("refresh_cancel_folder")
            );
            config.gui.remote_gui.label_cancel.setText("Viewing '" + folderName + "'");
         }
      } else {
         inFolder = false;
         if (config.gui.remote_gui != null) {
            config.gui.remote_gui.refresh_cancel.setText("Refresh");
            config.gui.remote_gui.refresh_cancel.setToolTipText(
               config.gui.remote_gui.getToolTip("refresh_cancel_top")
            );
            config.gui.remote_gui.label_cancel.setText("Top Level View");
         }
      }
   }
   
   private Boolean shouldIgnoreFolder(String folderName) {
      Boolean ignore = false;
      String[] ignoreFolders = {"none", "convertedLiveCache", "expired"};
      for (int i=0; i<ignoreFolders.length; ++i) {
         if (folderName.equals(ignoreFolders[i]))
            ignore = true;
      }
      return ignore;   
   }
   
   // Create data structure to organize entries in folder format
   private void folderize(Stack<JSONObject> entries) {
      debug.print("entries=" + entries);
      folders = new Hashtable<String,Stack<JSONObject>>();
      String name;
      try {
         for (int i=0; i<entries.size(); i++) {
            // Categorize by cancellationReason
            if (entries.get(i).has("cancellationReason"))
               name = entries.get(i).getString("cancellationReason");
            else
               name = "none";
            if ( ! shouldIgnoreFolder(name) ) {
               if ( ! folders.containsKey(name) ) {
                  // Init new stack
                  Stack<JSONObject> stack = new Stack<JSONObject>();
                  folders.put(name, stack);
               }
            folders.get(name).add(entries.get(i));
            }
         }
         
         // Define default sort order for all folder entries
         // Sort by largest start time first
         Comparator<JSONObject> folderSort = new Comparator<JSONObject>() {
            public int compare(JSONObject o1, JSONObject o2) {
               long gmt1 = getStartTime(o1);
               long gmt2 = getStartTime(o2);
               if (gmt1 < gmt2) return 1;
               if (gmt1 > gmt2) return -1;
               return 0;
            }
         };      
         JSONObject entry;
         sortedOrder = new Vector<JSONObject>();
         for (Enumeration<String> e=folders.keys(); e.hasMoreElements();) {
            name = e.nextElement();
            entry = new JSONObject(folders.get(name).get(0));
            entry.put("__folderName__", name);
            sortedOrder.add(entry);
         }
         Collections.sort(sortedOrder, folderSort);
      } catch (JSONException e1) {
         log.error("folderize - " + e1.getMessage());
      }
   }
   
   // Add a non folder entry to TABLE table
   public void AddTABLERow(JSONObject entry) {
      debug.print("entry=" + entry);
      int cols = TITLE_cols.length;
      Object[] data = new Object[cols];
      // Initialize to empty strings
      for (int i=0; i<cols; ++i) {
         data[i] = "";
      }
      try {
         // If entry is in 1 of todo lists then add special __inTodo__ JSON entry
         config.gui.remote_gui.flagIfInTodo(entry, true);
         String startString=null, endString=null;
         long start=0, end=0;
         if (entry.has("scheduledStartTime")) {
            startString = entry.getString("scheduledStartTime");
            start = TableUtil.getLongDateFromString(startString);
            endString = entry.getString("scheduledEndTime");
            end = TableUtil.getLongDateFromString(endString);
         } else if (entry.has("startTime")) {
            start = TableUtil.getStartTime(entry);
            end = TableUtil.getEndTime(entry);
         }
         String title = TableUtil.makeShowTitle(entry);
         String channel = TableUtil.makeChannelName(entry);
   
         data[1] = title;
         data[2] = new sortableDate(entry, start);
         data[3] = channel;
         data[4] = new sortableDuration(end-start, false);
         
         TableUtil.AddRow(TABLE, data);
         
         // Adjust column widths to data
         TableUtil.packColumns(TABLE, 2);
      } catch (JSONException e1) {
         log.error("AddTABLERow - " + e1.getMessage());
      }      
   }   

   // Add a folder entry to table
   public void AddTABLERow(String fName, Stack<JSONObject> folderEntry) {
      int cols = TITLE_cols.length;
      Object[] data = new Object[cols];
      // Initialize to empty strings
      for (int i=0; i<cols; ++i) {
         data[i] = "";
      }
      // Put folder icon as entry 0
      data[0] = gui.Images.get("folder");      
      data[1] = fName;
      data[2] = new sortableDate(fName, folderEntry);
      TableUtil.AddRow(TABLE, data);
      
      // Adjust column widths to data
      TableUtil.packColumns(TABLE, 2);
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
                  TABLE.scrollRectToVisible(TABLE.getCellRect(i, 0, true));
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
      
   private long getStartTime(JSONObject entry) {
      String startString;
      try {
         if (entry.has("scheduledStartTime")) {
            startString = entry.getString("scheduledStartTime");
            return TableUtil.getLongDateFromString(startString);
         } else
            return 0;
      } catch (JSONException e) {
         log.error("getStartTime - " + e.getMessage());
         return 0;
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
            json = GetRowData(row);
            entries.put(json);
         }
         TableUtil.recordSingleCB(tivoName, entries);
      }
   }
   
   // For show in given row try and obtain and print conflict details to message window
   // This only applies to entries under programSourceConflict folder
   public void getConflictDetails(final String tivoName, final int row) {
      class backgroundRun extends SwingWorker<Object, Object> {
         protected Object doInBackground() {
            JSONObject json = GetRowData(row);
            try {
               if (json != null && json.getString("cancellationReason").equals("programSourceConflict")) {
                  if (json.has("offerId") && json.has("contentId")) {
                     JSONObject j = new JSONObject();
                     j.put("offerId", json.getString("offerId"));
                     j.put("contentId", json.getString("contentId"));
                     if (json.has("requestedStartPadding"))
                        j.put("startTimePadding", json.getInt("requestedStartPadding"));
                     if (json.has("requestedEndPadding"))
                        j.put("endTimePadding", json.getInt("requestedEndPadding"));
                     // This signifies to check conflicts only, don't subscribe
                     j.put("conflictsOnly", true);
                     Remote r = config.initRemote(tivoName);
                     if (r.success) {
                        JSONObject result = r.Command("Singlerecording", j);
                        if (result != null) {
                           log.print(rnpl.recordingConflicts(result,json));
                        }
                        r.disconnect();
                     }
                  }
               } else {
                  log.warn("Explain button is only relevant for 'programSourceConflict' entries");
               }
            } catch (JSONException e) {
               log.error("getConflictDetails error - " + e.getMessage());
            }
            return null;
         }
      }
      backgroundRun b = new backgroundRun();
      b.execute();
   }
}
