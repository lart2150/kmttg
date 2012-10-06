package com.tivo.kmttg.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Hashtable;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;

import org.jdesktop.swingx.JXTable;
import org.jdesktop.swingx.decorator.Sorter;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.debug;

public class bitrateTable {
   private String[] TITLE_cols = {"CHANNEL", "SIZE (GB)", "TIME", "RATE (Mbps)", "RATE (GB/hour)"};
   public JXTable TABLE = null;
   
   bitrateTable() {
      Object[][] data = {};
      TABLE = new JXTable(data, TITLE_cols);
      TABLE.setModel(new MyTableModel(data, TITLE_cols));
            
      // Change color & font
      TableColumn tm;
      tm = TABLE.getColumnModel().getColumn(0);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
      tm = TABLE.getColumnModel().getColumn(1);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
      tm = TABLE.getColumnModel().getColumn(2);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
      tm = TABLE.getColumnModel().getColumn(3);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
      tm = TABLE.getColumnModel().getColumn(4);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
               
      TABLE.setAutoResizeMode(JXTable.AUTO_RESIZE_ALL_COLUMNS);
      
      // Set sorting routines for cols 1-4
      Sorter sorter;
      for (int i=1; i<TITLE_cols.length; i++) {
         sorter = TABLE.getColumnExt(i).getSorter();
         sorter.setComparator(sortableComparator);
      }
   }
   
   // Define custom column sorting routines
   Comparator<Object> sortableComparator = new Comparator<Object>() {
      public int compare(Object o1, Object o2) {
         if (o1 instanceof sortableDouble && o2 instanceof sortableDouble) {
            sortableDouble s1 = (sortableDouble)o1;
            sortableDouble s2 = (sortableDouble)o2;
            if (s1.sortable > s2.sortable) return 1;
            if (s1.sortable < s2.sortable) return -1;
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
       
       @SuppressWarnings({ "unchecked", "rawtypes" })
       // This is used to define columns as specific classes
       public Class getColumnClass(int col) {
          if (col == 1 || col == 3 || col == 4) {
             return sortableDouble.class;
          }
          if (col == 2) {
             return sortableDuration.class;
          }
          return Object.class;
       } 
       
       // Set all cells uneditable
       public boolean isCellEditable(int row, int column) {        
          return false;
       }
    }
    
    public void AddRows(Hashtable<String,Hashtable<String,Double>> chanData) {
       // Add rows to table in channel name alphabetical order
       Object[] channels = chanData.keySet().toArray();
       Arrays.sort(channels);
       for (int i=0; i<channels.length; ++i) {
          AddRow((String)channels[i], chanData.get(channels[i]));
       }
    }
    
    public void AddRow(String channel, Hashtable<String,Double> data) {
       debug.print("channel=" + channel + " data=" + data);
       Object[] info = new Object[TITLE_cols.length];
       info[0] = channel;
       // Total bytes in GB
       info[1] = new sortableDouble(data.get("bytes")/Math.pow(2,30));
       // Total time
       info[2] = new sortableDuration(data.get("duration").longValue()*1000);
       // Rate in Mbps = (bytes*8)/(1e6*secs)
       info[3] = new sortableDouble(bitRate(data.get("bytes"), data.get("duration")));
       // Rate in GB/hour = (bytes/2^30)/(secs/3600)
       info[4] = new sortableDouble((data.get("bytes")/Math.pow(2,30))/(data.get("duration")/3600.0));       
       AddRow(TABLE, info);       
    }
    
    public void AddRow(JTable table, Object[] data) {
       debug.print("table=" + table + " data=" + data);
       DefaultTableModel dm = (DefaultTableModel)table.getModel();
       dm.addRow(data);
    }
    
    // Mbps = (bytes*8)/(1e6*secs)
    public static Double bitRate(Double bytes, Double secs) {
       return (bytes*8)/(1e6*secs);
    }

}
