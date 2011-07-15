package com.tivo.kmttg.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.Comparator;
import java.util.Hashtable;

import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
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
import com.tivo.kmttg.util.log;

public class channelTable {
   private String[] TITLE_cols = {"NUM", "NAME", "RECEIVED"};
   public JXTable TABLE = null;
   public Hashtable<String,JSONArray> tivo_data = new Hashtable<String,JSONArray>();
   public JScrollPane scroll = null;

   channelTable(JFrame dialog) {
      Object[][] data = {};
      TABLE = new JXTable(data, TITLE_cols);
      TABLE.setModel(new MyTableModel(data, TITLE_cols));
      scroll = new JScrollPane(TABLE);
      
      // Change color & font
      TableColumn tm;
      tm = TABLE.getColumnModel().getColumn(0);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
      tm = TABLE.getColumnModel().getColumn(1);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
      tm = TABLE.getColumnModel().getColumn(2);
      tm.setCellRenderer(new CheckBoxRenderer(config.tableBkgndLight, config.tableFont));
      
      // Define custom column sorting routines
      Comparator<Object> sortableComparator = new Comparator<Object>() {
         public int compare(Object o1, Object o2) {
            if (o1 instanceof sortableChannel && o2 instanceof sortableChannel) {
               sortableChannel s1 = (sortableChannel)o1;
               sortableChannel s2 = (sortableChannel)o2;
               float l1 = s1.sortable;
               float l2 = s2.sortable;
               if (l1 > l2) return 1;
               if (l1 < l2) return -1;
               return 0;
            }
            return 0;
         }
      };
      
      // Use custom sorting routines for certain columns
      Sorter sorter = TABLE.getColumnExt(0).getSorter();
      sorter.setComparator(sortableComparator);
   }

   /**
    * Applied background color to single column of a JTable
    * in order to distinguish it apart from other columns.
    */ 
    class ColorColumnRenderer extends DefaultTableCellRenderer {
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
    
    // Special renderer for JCheckBox type
    class CheckBoxRenderer extends JCheckBox implements TableCellRenderer {
       private static final long serialVersionUID = 1L;
       Color bkgndColor;
       Font font;
       
       public CheckBoxRenderer(Color bkgnd, Font font) {
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
          if(isSelected) {
             setForeground(table.getSelectionForeground());
             super.setBackground(table.getSelectionBackground());
           } else {
             setForeground(table.getForeground());
             setBackground(table.getBackground());
           }
           setSelected((value != null && ((Boolean)value).booleanValue()));
           return this;
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
          if (col == 0)
             return sortableChannel.class;
          if (col == 2)
             return Boolean.class;
          
          return Object.class;
       } 
       
       // Set only 3rd column as editable
       public boolean isCellEditable(int row, int column) {        
          return column == 2;
       }       
    }

    // Pack all table columns to fit widest cell element
    public void packColumns(JXTable table, int margin) {
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
       DefaultTableModel model = (DefaultTableModel)TABLE.getModel(); 
       model.setNumRows(0);
    }

    public void AddRows(String tivoName, JSONArray data) {
       try {
          for (int i=0; i<data.length(); ++i) {
             AddRow(data.getJSONObject(i));
          }
          tivo_data.put(tivoName, data);
          packColumns(TABLE,2);
          if (config.gui.remote_gui != null)
             config.gui.remote_gui.setTivoName("channels", tivoName);
       } catch (JSONException e) {
          log.error("channelTable AddRows - " + e.getMessage());
       }
    }
    
    private void AddRow(JSONObject data) {
       try {
          Object[] info = new Object[TITLE_cols.length];
          String channelNumber = data.getString("channelNumber");
          String callSign = data.getString("callSign");
          Boolean isReceived = data.getBoolean("isReceived");
          
          info[0] = new sortableChannel(data, channelNumber);
          info[1] = callSign;
          info[2] = new Boolean(isReceived);
          AddRow(TABLE, info);       
       } catch (JSONException e) {
          log.error("channelTable AddRow - " + e.getMessage());
       }
    }
    
    private void AddRow(JTable table, Object[] data) {
       DefaultTableModel dm = (DefaultTableModel)table.getModel();
       dm.addRow(data);
    }
    
    public JSONObject GetRowData(int row) {
       sortableChannel s = (sortableChannel) TABLE.getValueAt(row, getColumnIndex("NUM"));
       if (s != null)
          return s.json;
       return null;
    }    
    
    public String GetRowChannelName(int row) {
       String s = (String) TABLE.getValueAt(row, getColumnIndex("NAME"));
       if (s != null)
          return s;
       return null;
    }
    
    public Boolean GetRowReceived(int row) {
       return (Boolean) TABLE.getValueAt(row, getColumnIndex("RECEIVED"));
    }
    
    public void RemoveRow(JXTable table, int row) {
       DefaultTableModel dm = (DefaultTableModel)table.getModel();
       dm.removeRow(row);
    }
        
}
