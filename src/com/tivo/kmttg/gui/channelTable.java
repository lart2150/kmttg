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
      TABLE.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
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
       
       // This is used to define columns as specific classes
       public Class<?> getColumnClass(int col) {
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

    public void AddRows(String tivoName, JSONArray data) {
       try {
          for (int i=0; i<data.length(); ++i) {
             AddRow(data.getJSONObject(i));
          }
          tivo_data.put(tivoName, data);
          TableUtil.packColumns(TABLE,2);
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
          TableUtil.AddRow(TABLE, info);       
       } catch (JSONException e) {
          log.error("channelTable AddRow - " + e.getMessage());
       }
    }        
}
