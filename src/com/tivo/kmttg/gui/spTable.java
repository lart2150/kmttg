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
import java.io.IOException;
import java.util.Comparator;
import java.util.Hashtable;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
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
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class spTable {
   private String[] TITLE_cols = {"PRIORITY", "SHOW", "CHANNEL", "NUM"};
   public JXTable TABLE = null;
   public Hashtable<String,JSONArray> tivo_data = new Hashtable<String,JSONArray>();
   private String currentTivo = null;
   public JScrollPane scroll = null;

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
      sorter = TABLE.getColumnExt(3).getSorter();
      sorter.setComparator(sortableComparator);
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
    
    public int getColumnIndex(String name) {
       String cname;
       for (int i=0; i<TABLE.getColumnCount(); i++) {
          cname = (String)TABLE.getColumnModel().getColumn(i).getHeaderValue();
          if (cname.equals(name)) return i;
       }
       return -1;
    }
    
    public void clear() {
       debug.print("");
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
          packColumns(TABLE,2);
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
             title += string.utfString(data.getString("title"));
          // Manual recordings need more information added
          if (title.equals(" Manual")) {
             String time = data.getJSONObject("idSetSource").getString("timeOfDayLocal");
             time = time.replaceFirst(":\\d+$", "");
             String days = data.getJSONObject("idSetSource").getJSONArray("dayOfWeek").toString();
             days = days.replaceAll("\"", "");
             title += " (" + time + ", " + days + ")";
          }
          String channel = " ";
          if (data.has("idSetSource")) {
             o = data.getJSONObject("idSetSource");
             if (o.has("channel")) {
                o2 = o.getJSONObject("channel");
                if (o2.has("channelNumber"))
                   channel += o2.getString("channelNumber");
                if (o2.has("callSign"))
                   channel += "=" + o2.getString("callSign");
             }
          }
          int max = 0;
          if (data.has("maxRecordings"))
             max = data.getInt("maxRecordings");
          
          info[0] = new sortableInt(data, priority);
          info[1] = title;
          info[2] = channel;
          info[3] = new sortableInt(null, max);
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
    
    public int[] GetSelectedRows() {
       debug.print("");
       int[] rows = TABLE.getSelectedRows();
       if (rows.length <= 0)
          log.error("No rows selected");
       return rows;
    }
    
    public JSONObject GetRowData(int row) {
       sortableInt s = (sortableInt) TABLE.getValueAt(row, getColumnIndex("PRIORITY"));
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
    
    public void updateTitleCols(String name) {
       String title;
       int col;
       MyTableModel dm = (MyTableModel)TABLE.getModel();
       for (int row=0; row<TABLE.getRowCount(); ++row) {
          col = getColumnIndex("SHOW");
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
       dm.removeRow(row);
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
       String title = GetRowTitle(0);
       if (title.startsWith(" Loaded:")) {
          log.error("Cannot re-order SPs from loaded file.");
          return null;
       }
       JSONArray array = new JSONArray();
       sortableInt s;
       for (int row=0; row<count; ++row) {
          s = (sortableInt) TABLE.getValueAt(row, getColumnIndex("PRIORITY"));
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
       int keyCode = e.getKeyCode();
       if (keyCode == KeyEvent.VK_DELETE) {
          // Remove selected row from TiVo and table
          int[] selected = GetSelectedRows();
          if (selected == null || selected.length < 0) {
             log.error("No rows selected");
             return;
          }
          int row;
          JSONObject json;
          String title;
          Remote r = new Remote(currentTivo);
          if (r.success) {
             for (int i=0; i<selected.length; ++i) {
                row = selected[i];
                json = GetRowData(row);
                title = GetRowTitle(row);
                if (json != null) {
                   try {
                      if (title.startsWith(" Loaded:")) {
                         log.error("Cannot unsubscribe loaded Season Passes. Refresh list for TiVo passes");
                      } else {
                         log.warn("Deleting SP on TiVo '" + currentTivo + "': " + title);
                         JSONObject o = new JSONObject();
                         o.put("subscriptionId", json.getString("subscriptionId"));
                         if ( r.Command("unsubscribe", o) != null ) {
                            RemoveRow(TABLE, row);
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
       } else if (keyCode == KeyEvent.VK_UP) {
          // Move selected row up
          int[] selected = GetSelectedRows();
          if (selected == null || selected.length < 0) {
             log.error("No rows selected");
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
       } else if (keyCode == KeyEvent.VK_DOWN) {
          // Move selected row down
          int[] selected = GetSelectedRows();
          if (selected == null || selected.length < 0) {
             log.error("No rows selected");
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
       } else {
          // Pass along keyboard action
          e.consume();
       }
    }
}
