package com.tivo.kmttg.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.ParseException;
import java.text.SimpleDateFormat;
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
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
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
import com.tivo.kmttg.util.string;

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
   
   // Mouse event handler
   // This will display folder entries in table if folder entry single-clicked
   private void MouseClicked(MouseEvent e) {
      if( e.getClickCount() == 1 ) {
         int row = TABLE.rowAtPoint(e.getPoint());
         sortableDate s = (sortableDate)TABLE.getValueAt(row,getColumnIndex("DATE"));
         if (s.folder) {
            folderName = s.folderName;
            folderEntryNum = row;
            setFolderState(true);
            Refresh(s.folderData_json);
         }
      }
   }
      
   public String getColumnName(int c) {
      return (String)TABLE.getColumnModel().getColumn(c).getHeaderValue();
   }
   
   public int getColumnIndex(String name) {
      String cname;
      for (int i=0; i<TABLE.getColumnCount(); i++) {
         cname = (String)TABLE.getColumnModel().getColumn(i).getHeaderValue();
         if (cname.equals(name)) return i;
      }
      return -1;
   }
   
   public int[] GetSelectedRows() {
      debug.print("");
      int[] rows = TABLE.getSelectedRows();
      if (rows.length <= 0)
         log.error("No rows selected");
      return rows;
   }
   
   private void TABLERowSelected(int row) {
      debug.print("row=" + row);
      if (row == -1) return;
      // Get column items for selected row 
      sortableDate s = (sortableDate)TABLE.getValueAt(row,getColumnIndex("DATE"));
      if (s.folder) {
         // Folder entry - don't display anything
      } else {
         try {
            // Non folder entry so print single entry info
            sortableDuration dur = (sortableDuration)TABLE.getValueAt(row,getColumnIndex("DUR"));
            JSONObject o;
            String channelNum = null;
            String channel = null;
            if (s.json.has("channel")) {
               o = s.json.getJSONObject("channel");
               if ( o.has("channelNumber") ) {
                  channelNum = o.getString("channelNumber");
               }
               if ( o.has("callSign") ) {
                  channel = o.getString("callSign");
               }
            }
            String description = null;
            if ( s.json.has("description") ) {
               description = s.json.getString("description");
            }
            String d = "";
            if (dur.sortable != null) {
               d = rnpl.msecsToMins(dur.sortable);
            }
            String message = "";
            if (s.display != null)
               message = s.display;
            if (channelNum != null && channel != null) {
               message += " on " + channelNum + "=" + channel;
            }
            message += ", Duration = " + d;
            
            if (s.json.has("seasonNumber"))
               message += ", season " + s.json.get("seasonNumber");
            if (s.json.has("episodeNum"))
               message += " episode " + s.json.getJSONArray("episodeNum").get(0);
            
            if (description != null) {
               message += "\n" + description;
            }
      
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
         // Filter out past recordings
         for (int i=0; i<data.length(); ++i) {
            json = data.getJSONObject(i);
            start = getStartTime(json);
            if (start >= now)
               o.add(json);
         }
         // Reset local entries/folders hashes to new entries
         folderize(o); // create folder structure
         Refresh(o);
         packColumns(TABLE, 2);
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
      clear();
      for (int i=0; i<o.size(); ++i) {
         AddTABLERow(o.get(i));
      }
   }
   
   // Update table display to show top level folderized entries
   public void displayFolderStructure() {
      debug.print("");
      clear();
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
            config.gui.remote_gui.label_cancel.setText("Viewing folder: '" + folderName + "'");
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
         config.gui.remote_gui.flagIfInTodo(entry);
         JSONObject o = new JSONObject();
         String startString = entry.getString("scheduledStartTime");
         long start = getLongDateFromString(startString);
         String endString = entry.getString("scheduledEndTime");
         long end = getLongDateFromString(endString);
         String title = " ";
         if (entry.has("title"))
            title += entry.getString("title");
         if (entry.has("seasonNumber") && entry.has("episodeNum")) {
            title += " [Ep " + entry.get("seasonNumber") +
            String.format("%02d]", entry.getJSONArray("episodeNum").get(0));
         }
         if (entry.has("subtitle"))
            title += " - " + entry.getString("subtitle");
         String channel = " ";
         if (entry.has("channel")) {
            o = entry.getJSONObject("channel");
            if (o.has("channelNumber"))
               channel += o.getString("channelNumber");
            if (o.has("callSign"))
               channel += "=" + o.getString("callSign");
         }
   
         data[1] = string.utfString(title);
         data[2] = new sortableDate(entry, start);
         data[3] = channel;
         data[4] = new sortableDuration(end-start, false);
         
         AddRow(TABLE, data);
         
         // Adjust column widths to data
         packColumns(TABLE, 2);
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
      AddRow(TABLE, data);
      
      // Adjust column widths to data
      packColumns(TABLE, 2);
   }
   
   public void clear() {
      debug.print("");
      DefaultTableModel model = (DefaultTableModel)TABLE.getModel(); 
      model.setNumRows(0);
   }
   
   public void AddRow(JXTable table, Object[] data) {
      debug.print("data=" + data);
      DefaultTableModel dm = (DefaultTableModel)table.getModel();
      dm.addRow(data);
   }
      
   public JSONObject GetRowData(int row) {
      sortableDate s = (sortableDate) TABLE.getValueAt(row, getColumnIndex("DATE"));
      if (s != null)
         return s.json;
      return null;
   }    
   
   public String GetRowTitle(int row) {
      String s = (String) TABLE.getValueAt(row, getColumnIndex("SHOW"));
      if (s != null)
         return s;
      return null;
   }

   // Look for entry with given folder name and select it
   // (This used when returning back from folder mode to top level mode)
   public void SelectFolder(String folderName) {
      debug.print("folderName=" + folderName);
      for (int i=0; i<TABLE.getRowCount(); ++i) {
         sortableDate s = (sortableDate)TABLE.getValueAt(i,getColumnIndex("DATE"));
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

   // Pack all table columns to fit widest cell element
   public void packColumns(JXTable table, int margin) {
      debug.print("table=" + table + " margin=" + margin);
      //if (config.tableColAutoSize == 1) {
         for (int c=0; c<table.getColumnCount(); c++) {
             packColumn(table, c, 2);
         }
      //}
   }

   // Sets the preferred width of the visible column specified by vColIndex. The column
   // will be just wide enough to show the column head and the widest cell in the column.
   // margin pixels are added to the left and right
   // (resulting in an additional width of 2*margin pixels).
   public void packColumn(JXTable table, int vColIndex, int margin) {
       DefaultTableColumnModel colModel = (DefaultTableColumnModel)table.getColumnModel();
       TableColumn col = colModel.getColumn(vColIndex);
       int width = 0;
   
       // Get width of column header
       TableCellRenderer renderer = col.getHeaderRenderer();
       if (renderer == null) {
           renderer = table.getTableHeader().getDefaultRenderer();
       }
       Component comp = renderer.getTableCellRendererComponent(
           table, col.getHeaderValue(), false, false, 0, 0);
       width = comp.getPreferredSize().width;
   
       // Get maximum width of column data
       for (int r=0; r<table.getRowCount(); r++) {
           renderer = table.getCellRenderer(r, vColIndex);
           comp = renderer.getTableCellRendererComponent(
               table, table.getValueAt(r, vColIndex), false, false, r, vColIndex);
           width = Math.max(width, comp.getPreferredSize().width);
       }
   
       // Add margin
       width += 2*margin;
              
       // Set the width
       col.setPreferredWidth(width);
   }
   
   // Compute and return all table column widths as an integer array
   public int[] getColWidths() {
      int[] widths = new int[TABLE.getColumnCount()];
      DefaultTableColumnModel colModel = (DefaultTableColumnModel)TABLE.getColumnModel();
      for (int i=0; i<widths.length; ++i) {
         TableColumn col = colModel.getColumn(i);
         widths[i] = col.getWidth();
      }
      return widths;
   }
   
   // Compute and return all table column widths as an integer array
   public void setColWidths(int[] widths) {
      if (widths.length != TABLE.getColumnCount()) {
         return;
      }
      DefaultTableColumnModel colModel = (DefaultTableColumnModel)TABLE.getColumnModel();
      for (int i=0; i<widths.length; ++i) {
         TableColumn col = colModel.getColumn(i);
         col.setPreferredWidth(widths[i]);
      }
   }
   
   private long getStartTime(JSONObject entry) {
      String startString;
      try {
         if (entry.has("scheduledStartTime")) {
            startString = entry.getString("scheduledStartTime");
            return getLongDateFromString(startString);
         } else
            return 0;
      } catch (JSONException e) {
         log.error("getStartTime - " + e.getMessage());
         return 0;
      }
   }

   private long getLongDateFromString(String date) {
      try {
         SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz");
         Date d = format.parse(date + " GMT");
         return d.getTime();
      } catch (ParseException e) {
        log.error("todoTable getLongDate - " + e.getMessage());
        return 0;
      }
   }
   
   // Schedule a single recording
   public void recordSingle(final String tivoName) {
      final int[] selected = GetSelectedRows();
      if (selected.length > 0) {
         log.print("Scheduling individual recordings on TiVo: " + tivoName);
         class backgroundRun extends SwingWorker<Object, Object> {
            protected Object doInBackground() {
               int row;
               JSONObject json;
               String title;
               Remote r = new Remote(tivoName);
               if (r.success) {
                  try {
                     for (int i=0; i<selected.length; ++i) {
                        row = selected[i];
                        json = GetRowData(row);
                        title = GetRowTitle(row);
                        if (json != null) {
                           if (config.gui.remote_gui.recordOpt == null)
                              config.gui.remote_gui.recordOpt = new recordOptions();
                           JSONObject o = config.gui.remote_gui.recordOpt.promptUser(
                              "Schedule Recording - " + title, null
                           );
                           if (o != null) {
                              log.warn("Scheduling Recording: '" + title + "' on TiVo: " + tivoName);
                              o.put("contentId", json.getString("contentId"));
                              o.put("offerId", json.getString("offerId"));
                              json = r.Command("singlerecording", o);
                              if (json == null) {
                                 log.error("Failed to schedule recording for: '" + title + "'");
                              } else {
                                 String conflicts = rnpl.recordingConflicts(json);
                                 if (conflicts == null) {
                                    log.warn("Scheduled recording: '" + title + "' on Tivo: " + tivoName);
                                    // Add to todo list for this tivo
                                    if (config.gui.remote_gui.all_todo.containsKey(tivoName)) {
                                       config.gui.remote_gui.all_todo.get(tivoName).put(GetRowData(row));
                                    }
                                 } else {
                                    log.error(conflicts);
                                 }
                              }
                           }
                        }
                     }
                  } catch (JSONException e) {
                     log.error("record_cancelCB failed - " + e.getMessage());
                  }
                  r.disconnect();
               }
               return null;
            }
         }
         backgroundRun b = new backgroundRun();
         b.execute();
      }
   }
}
