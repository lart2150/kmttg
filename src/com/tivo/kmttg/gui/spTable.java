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

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import org.jdesktop.swingx.JXTable;

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
   private JDialog dialog = null;

   spTable(JDialog dialog) {
      this.dialog = dialog;
      Object[][] data = {};
      TABLE = new JXTable(data, TITLE_cols);
      TABLE.setModel(new MyTableModel(data, TITLE_cols));
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
   }
   
   
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
            sortableDuration s1 = (sortableDuration)o1;
            sortableDuration s2 = (sortableDuration)o2;
            if (s1.sortable > s2.sortable) return 1;
            if (s1.sortable < s2.sortable) return -1;
            return 0;
         }
         return 0;
      }
   };

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
       debug.print("table=" + table + " vColIndex=" + vColIndex + " margin=" + margin);
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
        
        // Adjust SHOW column to fit available space
        int last = getColumnIndex("SHOW");
        if (vColIndex == last) {
           int twidth = table.getPreferredSize().width;
           int awidth = dialog.getWidth();
           int offset = 3*scroll.getVerticalScrollBar().getPreferredSize().width+2*margin;
           if ((awidth-offset) > twidth) {
              width += awidth-offset-twidth;
              col.setPreferredWidth(width);
           }
        }
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
       DefaultTableModel model = (DefaultTableModel)TABLE.getModel(); 
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
    
    private void AddRow(JSONObject data, int priority) {
       debug.print("data=" + data);
       try {
          JSONObject o = new JSONObject();
          JSONObject o2 = new JSONObject();
          Object[] info = new Object[TITLE_cols.length];
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
          info[3] = max;
          AddRow(TABLE, info);       
       } catch (Exception e) {
          log.error("spTable AddRow - " + e.getMessage());
       }
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
       DefaultTableModel dm = (DefaultTableModel)TABLE.getModel();
       for (int row=0; row<TABLE.getRowCount(); ++row) {
          col = getColumnIndex("SHOW");
          title = (String)TABLE.getValueAt(row,col);
          title = name + title;
          dm.setValueAt(title, row, col);
       }
    }
    
    private void AddRow(JTable table, Object[] data) {
       debug.print("table=" + table + " data=" + data);
       DefaultTableModel dm = (DefaultTableModel)table.getModel();
       dm.addRow(data);
    }
    
    public void RemoveRow(JXTable table, int row) {
       debug.print("table=" + table + " row=" + row);
       DefaultTableModel dm = (DefaultTableModel)table.getModel();
       dm.removeRow(row);
    }
    
    // Mouse event handler
    // This will display folder entries in table if folder entry single-clicked
    private void MouseClicked(MouseEvent e) {
       if( e.getClickCount() == 1 ) {
          int row = TABLE.rowAtPoint(e.getPoint());
          sortableInt s = (sortableInt)TABLE.getValueAt(row,getColumnIndex("PRIORITY"));
          System.out.println(s.json);
       }
    }
    
    // Handle delete keyboard presses
    private void KeyPressed(KeyEvent e) {
       int keyCode = e.getKeyCode();
       if (keyCode == KeyEvent.VK_DELETE){
          // Delete key has special action
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
                         }
                      }
                   } catch (JSONException e1) {
                      log.error("SP delete - " + e1.getMessage());
                   }
                }
             }
             r.disconnect();                   
          }
       } else {
          // Pass along keyboard action
          e.consume();
       }
    }
}
