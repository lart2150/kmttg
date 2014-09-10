package com.tivo.kmttg.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Comparator;
import java.util.Hashtable;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;

import org.jdesktop.swingx.JXTable;
import org.jdesktop.swingx.decorator.Sorter;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONFile;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class spTable {
   private String[] TITLE_cols = {"PRIORITY", "SHOW", "CHANNEL", "RECORD", "KEEP", "NUM", "START", "END"};
   public JXTable TABLE = null;
   public Hashtable<String,JSONArray> tivo_data = new Hashtable<String,JSONArray>();
   private String currentTivo = null;
   public JScrollPane scroll = null;
   private Boolean loaded = false;

   spTable(JFrame dialog) {
      Object[][] data = {};
      TABLE = new JXTable(data, TITLE_cols);
      TABLE.setModel(new MyTableModel(data, TITLE_cols));
      TABLE.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
      TABLE.setSortable(false); // disable sorting to avoid problems with re-prioritize
      TABLE.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      TABLE.setDragEnabled(true);
      TABLE.setTransferHandler(new TableTransferHandler());
      scroll = new JScrollPane(TABLE);
      // Add keyboard listener
      TABLE.addKeyListener(
         new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
               KeyPressed(e);
            }
         }
      );
      
      // Change color & font
      TableColumn tm;
      tm = TABLE.getColumnModel().getColumn(0);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.RIGHT);
      tm = TABLE.getColumnModel().getColumn(1);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.LEFT);
      tm = TABLE.getColumnModel().getColumn(2);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.LEFT);
      tm = TABLE.getColumnModel().getColumn(3);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.LEFT);
      tm = TABLE.getColumnModel().getColumn(4);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.LEFT);
      tm = TABLE.getColumnModel().getColumn(5);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.LEFT);
      tm = TABLE.getColumnModel().getColumn(6);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.LEFT);
      tm = TABLE.getColumnModel().getColumn(7);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.LEFT);
      
      // Define custom column sorting routines
      Comparator<Object> sortableComparator = new Comparator<Object>() {
         public int compare(Object o1, Object o2) {
            if (o1 instanceof sortableInt && o2 instanceof sortableInt) {
               sortableInt s1 = (sortableInt)o1;
               sortableInt s2 = (sortableInt)o2;
               if (s1.sortable > s2.sortable) return 1;
               if (s1.sortable < s2.sortable) return -1;
               return 0;
            }
            return 0;
         }
      };
      
      // Use custom sorting routines for certain columns
      Sorter sorter = TABLE.getColumnExt(0).getSorter();
      sorter.setComparator(sortableComparator);
      sorter = TABLE.getColumnExt(5).getSorter();
      sorter.setComparator(sortableComparator);
      sorter = TABLE.getColumnExt(6).getSorter();
      sorter.setComparator(sortableComparator);
      sorter = TABLE.getColumnExt(7).getSorter();
      sorter.setComparator(sortableComparator);
      
      // Add right mouse button handler
      TableUtil.AddRightMouseListener(TABLE);
   }   

   /**
    * Applied background color to single column of a JTable
    * in order to distinguish it apart from other columns.
    */ 
    class ColorColumnRenderer extends DefaultTableCellRenderer 
    {
       private static final long serialVersionUID = 1L;
       Color bkgndColor;
       Font font;
       
       public ColorColumnRenderer(Color bkgnd, Font font) {
          super();
          // Center text in cells
          setHorizontalAlignment(CENTER);
          bkgndColor = bkgnd;
          this.font = font;
       }
       
       public Component getTableCellRendererComponent
           (JTable table, Object value, boolean isSelected,
            boolean hasFocus, int row, int column) 
       {
          Component cell = super.getTableCellRendererComponent
             (table, value, isSelected, hasFocus, row, column);
     
          if (bkgndColor != null && ! isSelected)
             cell.setBackground( bkgndColor );
          
          JSONObject json = GetRowData(row);
          if (json != null && json.has("__conflicts"))
             cell.setBackground(config.lightRed);
          
          cell.setFont(config.tableFont);
         
          return cell;
       }
    } 

    // Define clipboard data for drag & drop as string (JSON->string)
    abstract class StringTransferHandler extends TransferHandler {
      private static final long serialVersionUID = 1L;

      protected abstract String exportString(JComponent c);
      protected abstract void importString(JComponent c, String str);
      protected abstract void cleanup(JComponent c, boolean remove);
      protected Transferable createTransferable(JComponent c) {
        return new StringSelection(exportString(c));
      }
      public int getSourceActions(JComponent c) {
        return COPY_OR_MOVE;
      }
      public boolean importData(JComponent c, Transferable t) {
        if (canImport(c, t.getTransferDataFlavors())) {
          try {
            String str = (String) t.getTransferData(DataFlavor.stringFlavor);
            importString(c, str);
            return true;
          } catch (UnsupportedFlavorException ufe) {
          } catch (IOException ioe) {
          }
        }
        return false;
      }

      protected void exportDone(JComponent c, Transferable data, int action) {
        cleanup(c, action == MOVE);
      }

      public boolean canImport(JComponent c, DataFlavor[] flavors) {
        if (isTableLoaded())
           return false;
        for (int i = 0; i < flavors.length; i++) {
          if (DataFlavor.stringFlavor.equals(flavors[i])) {
            return true;
          }
        }
        return false;
      }
    }

    // This handles table row drag & drop data transfer
    // Export->JSON->String->Import->String->JSON
    class TableTransferHandler extends StringTransferHandler {
      private static final long serialVersionUID = 1L;
      private int[] rows = null;
      private int addIndex = -1; //Location where items were added
      private int addCount = 0; //Number of items added.

      protected String exportString(JComponent c) {
        JTable table = (JTable) c;
        rows = table.getSelectedRows();

        StringBuffer buff = new StringBuffer();
        JSONObject json;

        for (int i = 0; i < rows.length; i++) {
           json = GetRowData(rows[i]);
           buff.append(json.toString());
           if (i != rows.length - 1) {
              buff.append("\n");
           }
        }

        return buff.toString();
      }

      protected void importString(JComponent c, String str) {
        JTable target = (JTable) c;
        MyTableModel model = (MyTableModel) target.getModel();
        int index = target.getSelectedRow();

        //Prevent the user from dropping data back on itself.
        //For example, if the user is moving rows #4,#5,#6 and #7 and
        //attempts to insert the rows after row #5, this would
        //be problematic when removing the original rows.
        //So this is not allowed.
        if (rows != null && index >= rows[0] - 1
            && index <= rows[rows.length - 1]) {
          rows = null;
          return;
        }

        int max = model.getRowCount();
        if (index < 0) {
          index = max;
        } else {
          index++;
          if (index > max) {
            index = max;
          }
        }
        addIndex = index;
        String[] values = str.split("\n");
        addCount = values.length;
        JSONObject json;
        try {
           for (int i = 0; i < values.length; i++) {
              json = new JSONObject(values[i]);
              Object[] info = jsonToTableData(json, json.getInt("__priority__"));
              if (info != null)
                 InsertRow(TABLE, index++, info);
           }
        } catch (JSONException e1) {
           log.error("importString - " + e1.getMessage());
        }
      }

      protected void cleanup(JComponent c, boolean remove) {
        JTable source = (JTable) c;
        if (remove && rows != null) {
          MyTableModel model = (MyTableModel) source.getModel();

          //If we are moving items around in the same table, we
          //need to adjust the rows accordingly, since those
          //after the insertion point have moved.
          if (addCount > 0) {
            for (int i = 0; i < rows.length; i++) {
              if (rows[i] > addIndex) {
                rows[i] += addCount;
              }
            }
          }
          for (int i = rows.length - 1; i >= 0; i--) {
            model.removeRow(rows[i]);
          }
        }
        rows = null;
        addCount = 0;
        addIndex = -1;
      }
    }
    
    // Override some default table model actions
    class MyTableModel extends DefaultTableModel {
       private static final long serialVersionUID = 1L;

       public MyTableModel(Object[][] data, Object[] columnNames) {
          super(data, columnNames);
       }
       
       @SuppressWarnings("unchecked")
       // This is used to define columns as specific classes
       public Class getColumnClass(int col) {
          if (col == 1) {
             return sortableInt.class;
          }
          return Object.class;
       } 
       
       // Set all cells uneditable
       public boolean isCellEditable(int row, int column) {        
          return false;
       }
    }
    
    public void clear() {
       debug.print("");
       setLoaded(false);
       MyTableModel model = (MyTableModel)TABLE.getModel(); 
       model.setNumRows(0);
    }
    
    // Add given data to table
    public Boolean AddRows(JSONArray data) {
       try {
          for (int i=0; i<data.length(); ++i) {
             AddRow(data.getJSONObject(i), i+1);
             //System.out.println(data.getJSONObject(i));
          }
          TableUtil.packColumns(TABLE,2);
       } catch (JSONException e) {
          log.error("spTable AddRows - " + e.getMessage());
          return false;
       }
       return true;
    }

    // Add rows and save to tivo_data
    public void AddRows(String tivoName, JSONArray data) {
       if (AddRows(data)) {
          tivo_data.put(tivoName, data);
          currentTivo = tivoName;
          if (config.gui.remote_gui != null)
             config.gui.remote_gui.setTivoName("sp", tivoName);
       }
    }
    
    private Object[] jsonToTableData(JSONObject data, int priority) {
       Object[] info = new Object[TITLE_cols.length];
       try {
          JSONObject o = new JSONObject();
          JSONObject o2 = new JSONObject();
          String title = " ";
          if (data.has("title"))
             title += data.getString("title");
          // Manual recordings need more information added
          if (title.equals(" Manual")) {
             String time = data.getJSONObject("idSetSource").getString("timeOfDayLocal");
             time = time.replaceFirst(":\\d+$", "");
             String days = data.getJSONObject("idSetSource").getJSONArray("dayOfWeek").toString();
             days = days.replaceAll("\"", "");
             title += " (" + time + ", " + days + ")";
          }
          // Add upcoming episode counts to title if available
          if (data.has("__upcoming")) {
             int count = data.getJSONArray("__upcoming").length();
             title += " (" + count + ")";
          }
          String channel = " ";
          if (data.has("idSetSource")) {
             o = data.getJSONObject("idSetSource");
             if (o.has("channel")) {
                o2 = o.getJSONObject("channel");
                if (o2.has("channelNumber"))
                   channel += o2.getString("channelNumber");
                if (o2.has("callSign")) {
                   String callSign = o2.getString("callSign");
                   if (callSign.toLowerCase().equals("all channels"))
                      channel += callSign;
                   else
                      channel += "=" + callSign;
                }
             }
          }
          int max = 0;
          if (data.has("maxRecordings"))
             max = data.getInt("maxRecordings");
          int startPad = 0;
          if (data.has("startTimePadding"))
             startPad = data.getInt("startTimePadding")/60;
          int endPad = 0;
          if (data.has("endTimePadding"))
             endPad = data.getInt("endTimePadding")/60;
          String record = "";
          if (data.has("showStatus"))
             record = data.getString("showStatus");
          String keep = "";
          if (data.has("keepBehavior"))
             keep = data.getString("keepBehavior");
          
          info[0] = new sortableInt(data, priority);
          info[1] = title;
          info[2] = channel;
          info[3] = record;
          info[4] = keep;
          info[5] = new sortableInt(null, max);
          info[6] = startPad;
          info[7] = endPad;
          return info;
       } catch (Exception e) {
          log.error("jsonToTableData - " + e.getMessage());
          return null;
       }
    }
    
    private void AddRow(JSONObject data, int priority) {
       try {
         data.put("__priority__", priority);
      } catch (JSONException e) {
         log.error("AddRow - " + e.getMessage());
      }
       Object[] info = jsonToTableData(data, priority);
       if (info != null)
          AddRow(TABLE, info);
    }
    
    public void setSelectedRow(int row) {
       TABLE.setRowSelectionInterval(row,row);
       TableUtil.scrollToCenter(TABLE, row);
    }
    
    public JSONObject GetRowData(int row) {
       sortableInt s = (sortableInt) TABLE.getValueAt(row, TableUtil.getColumnIndex(TABLE, "PRIORITY"));
       if (s != null)
          return s.json;
       return null;
    }
    
    public String GetRowTitle(int row) {
       String s = (String) TABLE.getValueAt(row, TableUtil.getColumnIndex(TABLE, "SHOW"));
       if (s != null)
          return s;
       return null;
    }
    
    public void updateTitleCols(String name) {
       String title;
       int col;
       MyTableModel dm = (MyTableModel)TABLE.getModel();
       for (int row=0; row<TABLE.getRowCount(); ++row) {
          col = TableUtil.getColumnIndex(TABLE, "SHOW");
          title = (String)TABLE.getValueAt(row,col);
          title = name + title;
          dm.setValueAt(title, row, col);
       }
    }
    
    private void AddRow(JTable table, Object[] data) {
       MyTableModel dm = (MyTableModel)table.getModel();
       dm.addRow(data);
    }
    
    private void InsertRow(JXTable table, int row, Object[] data) {
       MyTableModel dm = (MyTableModel)table.getModel();
       dm.insertRow(row, data);
    }
    
    public void RemoveRow(JXTable table, int row) {
       MyTableModel dm = (MyTableModel)table.getModel();
       dm.removeRow(table.convertRowIndexToModel(row));
    }
    
    private void changeRowPrompt(int from) {
       MyTableModel dm = (MyTableModel)TABLE.getModel();
       from = TABLE.convertRowIndexToModel(from);
       String answer = (String)JOptionPane.showInputDialog(
          config.gui.getJFrame(),
          "Enter desired new priority #",
          "Change Priority",
          JOptionPane.PLAIN_MESSAGE
       );
       try {
          int priority = Integer.parseInt(answer);
          if (priority > 0 && priority <= TABLE.getRowCount()) {
             int to = TABLE.convertRowIndexToModel(priority-1);
             if (to != from) {
                dm.moveRow(from, from, to);
             }
          } else {
             log.error("Invalid priority number entered: " + answer);
          }
       } catch (NumberFormatException e) {
          log.error("Invalid priority number entered: " + answer);
       }
    }
    
    public Boolean isTableLoaded() {
       return loaded;
    }
    
    public void setLoaded(Boolean flag) {
       if (flag) {
          loaded = true;
          TABLE.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
       } else {
          loaded = false;
          TABLE.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
       }
    }
    
    public void updateLoadedStatus() {
       if (TABLE.getRowCount() > 0) {
          int col = TableUtil.getColumnIndex(TABLE, "SHOW");
          String title = (String)TABLE.getValueAt(0,col);
          if (title != null && title.startsWith(" Loaded"))
             setLoaded(true);
          else
             setLoaded(false);
       }
    }
    
    private Boolean removeJson(String tivoName, JSONObject json) {
       Boolean removed = false;
       try {
          for (int i=0; i<tivo_data.get(tivoName).length(); ++i) {
            if (tivo_data.get(tivoName).get(i) == json) {
                tivo_data.get(tivoName).remove(i);
                removed = true;
                break;
            }
          }
       } catch (JSONException e) {
          log.print("removeJson - " + e.getMessage());
       }
       return removed;
    }

    // Return array of subscriptionId's according to current table row order
    public JSONArray GetOrderedIds() {
       int count = TABLE.getRowCount();
       if (count == 0) {
          log.error("Table is empty");
          return null;
       }
       if (isTableLoaded()) {
          log.error("Cannot re-order SPs from loaded file.");
          return null;
       }
       JSONArray array = new JSONArray();
       sortableInt s;
       for (int row=0; row<count; ++row) {
          s = (sortableInt) TABLE.getValueAt(row, TableUtil.getColumnIndex(TABLE, "PRIORITY"));
          if (s != null && s.json.has("subscriptionId")) {
             try {
                array.put(s.json.getString("subscriptionId"));
             } catch (JSONException e) {
                log.error("GetOrderedIds - " + e.getMessage());
                return null;
             }
          }
       }
       if (array.length() == count)
          return array;
       else
          return null;
    }
    
    // Handle keyboard presses
    private void KeyPressed(KeyEvent e) {
       if (e.isControlDown())
          return;
       int keyCode = e.getKeyCode();
       if (keyCode == KeyEvent.VK_J) {
          // Print json of selected row to log window
          int[] selected = TableUtil.GetSelectedRows(TABLE);
          if (selected == null || selected.length < 1)
             return;
          JSONObject json = GetRowData(selected[0]);
          if (json != null)
             rnpl.pprintJSON(json);
       }
       else if (keyCode == KeyEvent.VK_C) {
          config.gui.remote_gui.copy_sp.doClick();
       }
       else if (keyCode == KeyEvent.VK_M) {
          config.gui.remote_gui.modify_sp.doClick();
       }
       else if (keyCode == KeyEvent.VK_P) {
          int[] selected = TableUtil.GetSelectedRows(TABLE);
          if (selected == null || selected.length < 1)
             return;
          changeRowPrompt(selected[0]);
       }
       else if (keyCode == KeyEvent.VK_U) {
          config.gui.remote_gui.upcoming_sp.doClick();
       }
       else if (keyCode == KeyEvent.VK_O) {
          config.gui.remote_gui.conflicts_sp.doClick();
       }
       else if (keyCode == KeyEvent.VK_Q) {
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
       }
       else if (keyCode == KeyEvent.VK_DELETE) {
          // Remove selected row from TiVo and table
          SPListDelete();
       }
       else if (keyCode == KeyEvent.VK_UP) {
          // Move selected row up
          int[] selected = TableUtil.GetSelectedRows(TABLE);
          if (selected == null || selected.length < 0) {
             log.error("No rows selected");
             return;
          }
          if (isTableLoaded()) {
             log.error("Cannot re-order loaded season passes");
             return;
          }
          int row;
          JSONObject json;
          try {
             for (int i=0; i<selected.length; ++i) {
                row = selected[i];
                if (row-1 >= 0) {
                   json = GetRowData(row);
                   Object[] info = jsonToTableData(json, json.getInt("__priority__"));
                   if (info != null) {
                      RemoveRow(TABLE, row);
                      InsertRow(TABLE, row-1, info);
                      TABLE.setRowSelectionInterval(row, row);
                   }
                }
             }
          } catch (JSONException e1) {
             log.error("KeyPressed - " + e1.getMessage());
          }
       }
       else if (keyCode == KeyEvent.VK_DOWN) {
          // Move selected row down
          int[] selected = TableUtil.GetSelectedRows(TABLE);
          if (selected == null || selected.length < 0) {
             log.error("No rows selected");
             return;
          }
          if (isTableLoaded()) {
             log.error("Cannot re-order loaded season passes");
             return;
          }
          int row;
          JSONObject json;
          try {
             for (int i=0; i<selected.length; ++i) {
                row = selected[i];
                if (row < TABLE.getRowCount()-1) {
                   json = GetRowData(row);
                   Object[] info = jsonToTableData(json, json.getInt("__priority__"));
                   if (info != null) {
                      RemoveRow(TABLE, row);
                      InsertRow(TABLE, row+1, info);
                      TABLE.setRowSelectionInterval(row, row);
                   }
                }
             }
          } catch (JSONException e1) {
             log.error("KeyPressed - " + e1.getMessage());
          }
       }
       else {
          // Pass along keyboard action
          e.consume();
       }
    }
    
    // Delete selected Season Pass entries from TiVo and table
    public void SPListDelete() {
       // Remove selected row from TiVo and table
       int[] selected = TableUtil.GetSelectedRows(TABLE);
       if (selected == null || selected.length < 1) {
          log.error("No rows selected");
          return;
       }
       if (currentTivo == null) {
          log.error("Table not initialized");
          return;
       }
       int smallest = -1;
       int row;
       JSONObject json;
       String title;
       Remote r = config.initRemote(currentTivo);
       if (r.success) {
          for (int i=0; i<selected.length; ++i) {
             row = selected[i];
             if (smallest == -1)
                smallest = row;
             if (row < smallest)
                smallest = row;
             json = GetRowData(row);
             if (json != null) {
                try {
                   title = json.getString("title");
                   if (isTableLoaded()) {
                      log.error("Cannot unsubscribe loaded Season Passes. Refresh list for TiVo passes");
                   } else {
                      log.warn("Deleting SP on TiVo '" + currentTivo + "': " + title);
                      JSONObject o = new JSONObject();
                      o.put("subscriptionId", json.getString("subscriptionId"));
                      if ( r.Command("Unsubscribe", o) != null ) {
                         RemoveRow(TABLE, row);
                         smallest -= 1;
                         if (smallest < 0)
                            smallest = 0;
                         if (TABLE.getRowCount() > 0)
                            setSelectedRow(smallest);
                         // Find and remove data entry
                         removeJson(currentTivo, json);
                      }
                   }
                } catch (JSONException e1) {
                   log.error("SP delete - " + e1.getMessage());
                }
             }
          }
          r.disconnect();                   
       }
    }
    
    public void SPListSave(String tivoName, String file) {
       if (tivo_data.containsKey(tivoName) && tivo_data.get(tivoName).length() > 0) {
          log.warn("Saving '" + tivoName + "' SP list to file: " + file);
          JSONFile.write(tivo_data.get(tivoName), file);
       } else {
          log.error("No data available to save.");
       }
    }
    
    public void SPListLoad(String file) {
       log.print("Loading SP data from file: " + file);
       JSONArray data = JSONFile.readJSONArray(file);
       if (data != null && data.length() > 0) {
          // Remove __upcoming && __conflicts entries if there are any
          try {
             for (int i=0; i<data.length(); ++i) {
                if (data.getJSONObject(i).has("__upcoming"))
                   data.getJSONObject(i).remove("__upcoming");
                if (data.getJSONObject(i).has("__conflicts"))
                   data.getJSONObject(i).remove("__conflicts");
             }
          } catch (JSONException e1) {
             log.error("SPListLoad - " + e1.getMessage());
          }

          // Now clear table and display loaded data
          clear();
          AddRows(data);
          updateTitleCols(" Loaded:");
          setLoaded(true);
       }
    }
    
    public void SPListExport(String tivoName, String file) {
       if (tivo_data.containsKey(tivoName) && tivo_data.get(tivoName).length() > 0) {
          try {
             log.warn("Exporting '" + tivoName + "' SP list to csv file: " + file);
             BufferedWriter ofp = new BufferedWriter(new FileWriter(file));
             ofp.write("PRIORITY,SHOW,CHANNEL,KEEP\r\n");
             JSONArray data = tivo_data.get(tivoName);
             for (int i=0; i<data.length(); ++i) {
                JSONObject json = data.getJSONObject(i);
                String priority = "" + i+1;
                String show = "N/A";
                String channel = "";
                String keep = "N/A";
                if (json.has("__priority__"))
                   priority = "" + json.get("__priority__");
                if (json.has("title"))
                   show = json.getString("title");
                if (json.has("idSetSource")) {
                   JSONObject o = json.getJSONObject("idSetSource");
                   if (o.has("channel")) {
                      JSONObject o2 = o.getJSONObject("channel");
                      if (o2.has("channelNumber"))
                         channel += o2.getString("channelNumber");
                      if (o2.has("callSign"))
                         channel += "=" + o2.getString("callSign");
                   }
                }
                if (json.has("maxRecordings"))
                   keep = "" + json.get("maxRecordings");
                ofp.write("\"" + priority + "\",");
                ofp.write("\"" + show + "\",");
                ofp.write("\"" + channel + "\",");
                ofp.write("\"" + keep + "\"");
                ofp.write("\r\n");
             }
             ofp.close();
          } catch (Exception e) {
             log.error("SPListExport - " + e.getMessage());
          }
       } else {
         log.error("No data available to export.");
       }
    }
    
    public void SPListCopy(final String tivoName) {
       class backgroundRun extends SwingWorker<Object, Object> {
          protected Object doInBackground() {
             //SeasonPasses
             int[] selected = TableUtil.GetSelectedRows(TABLE);
             if (selected.length > 0) {
                int row;
                JSONArray existing;
                JSONObject json, result;
                Remote r = config.initRemote(tivoName);
                if (r.success) {
                   // First load existing SPs from tivoName to check against
                   existing = r.SeasonPasses(null);
                   if (existing == null) {
                      log.error("Failed to grab existing SPs to check against for TiVo: " + tivoName);
                      r.disconnect();
                      return null;
                   }
                   // Now proceed with subscriptions
                   log.print("Copying Season Passes to TiVo: " + tivoName);
                   for (int i=0; i<selected.length; ++i) {
                      row = selected[i];
                      json = GetRowData(row);
                      if (json != null) {
                         try {
                            // Check against existing
                            String title = json.getString("title");
                            String channel = "";
                            if (json.has("channel")) {
                               JSONObject o = json.getJSONObject("channel");
                               if (o.has("callSign"))
                                  channel = o.getString("callSign");
                            }
                            Boolean schedule = true;
                            for (int j=0; j<existing.length(); ++j) {
                               JSONObject e = existing.getJSONObject(j);
                               if(title.equals(e.getString("title"))) {
                                  if (channel.length() > 0 && e.has("idSetSource")) {
                                     JSONObject id = e.getJSONObject("idSetSource");
                                     if (id.has("channel")) {
                                        JSONObject c = id.getJSONObject("channel");
                                        String callSign = "";
                                        if (c.has("callSign"))
                                           callSign = c.getString("callSign");
                                        if (channel.equals(callSign)) {
                                           schedule = false;
                                        }
                                     }
                                  } else {
                                     schedule = false;
                                  }
                               }
                            }
                            
                            // OK to subscribe
                            if (schedule) {
                               log.print("Scheduling: " + json.getString("title"));
                               result = r.Command("Seasonpass", json);
                               if (result != null)
                                  log.print("success");
                            } else {
                               log.warn("Existing SP with same title + callSign found, not scheduling: " +
                                  json.getString("title")
                               );
                            }
                         } catch (JSONException e) {
                            log.error("SPListCopy - " + e.getMessage());
                         }
                      }
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
    
    public void SPListModify(final String tivoName) {
       class backgroundRun extends SwingWorker<Object, Object> {
          protected Object doInBackground() {
             int[] selected = TableUtil.GetSelectedRows(TABLE);
             if (selected.length > 0) {
                int row = selected[0];
                JSONObject json = GetRowData(row);
                if (json != null) {
                   String title;
                   try {
                      title = json.getString("title");
                      if (isTableLoaded()) {
                         log.error("Cannot modify SPs from loaded file.");
                         return null;
                      }
                      JSONObject result = config.gui.remote_gui.spOpt.promptUser(
                         "(" + tivoName + ")" + "Modify SP - " + title, json
                      );
                      if (result != null) {
                         Remote r = config.initRemote(tivoName);
                         if (r.success) {
                            if (r.Command("ModifySP", result) != null) {
                               log.warn("Modified SP '" + title + "' for TiVo: " + tivoName);
                            }
                            
                            // Update SP table
                            JSONArray a = r.SeasonPasses(new jobData());
                            if( a != null) {
                               clear();
                               AddRows(tivoName, a);
                               setSelectedRow(row);
                            }
                            
                            r.disconnect();
                         }
                      }
                   } catch (JSONException e) {
                      log.error("SPListModify error: " + e.getMessage());
                      return null;
                   }
                }
             }
             return null;
          }
       }
       backgroundRun b = new backgroundRun();
       b.execute();
    }
    
    // Update SP priority order to match current SP table
    public void SPReorderCB(String tivoName) {
       JSONArray order = GetOrderedIds();
       if (order != null) {
          jobData job = new jobData();
          job.source           = tivoName;
          job.tivoName         = tivoName;
          job.type             = "remote";
          job.name             = "Remote";
          job.remote_spreorder = true;
          job.remote_orderIds  = order;
          jobMonitor.submitNewJob(job);
       }
    }
}
