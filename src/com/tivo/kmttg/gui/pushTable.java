package com.tivo.kmttg.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.Comparator;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;

import org.jdesktop.swingx.JXTable;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.log;

public class pushTable {
   private String[] TITLE_cols = {"TITLE", "DATE", "DEST TSN"};
   public JXTable TABLE = null;
   
   pushTable() {
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
               
      TABLE.setAutoResizeMode(JXTable.AUTO_RESIZE_ALL_COLUMNS);
      
      // Set custom sorting routine for DATE column
      TABLE.getColumnExt(1).getSorter().setComparator(sortableComparator);
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
             return sortableDate.class;
          }
          return Object.class;
       } 
       
       // Set all cells uneditable
       public boolean isCellEditable(int row, int column) {        
          return false;
       }
    }
    
    public JXTable getTable() {
       return TABLE;
    }
    
    public void AddRows(JSONArray data) {
       for (int i=0; i<data.length(); ++i) {
          try {
            AddRow(data.getJSONObject(i));
         } catch (JSONException e) {
            log.error("pushTable AddRows - " + e.getMessage());
            return;
         }
       }
    }
    
    public void AddRow(JSONObject json) {
       Object[] info = new Object[TITLE_cols.length];
       try {
          // Title
         info[0] = json.getString("source");
         // Date
         String dateString = json.getString("publishDate");
         long d = TableUtil.getLongDateFromString(dateString);
         info[1] = new sortableDate(json, d);
         // TSN
         String tsn = json.getString("bodyId");
         tsn = tsn.replaceFirst("tsn:", "");
         info[2] = tsn;
         TableUtil.AddRow(TABLE, info);       
      } catch (JSONException e) {
         log.error("pushTable AddRow - " + e.getMessage());
      }
    }
    
    public void RemoveRow(int row) {
       MyTableModel dm = (MyTableModel)TABLE.getModel();
       dm.removeRow(TABLE.convertRowIndexToModel(row));
    }
}
