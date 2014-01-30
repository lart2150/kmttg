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
   private String[] TITLE_cols = {"NUM", "TITLE", "DEST TiVo"};
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
      
      // Set custom sorting routine for NUM column
      TABLE.getColumnExt(0).getSorter().setComparator(sortableComparator);
   }
   
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
          if (col == 0) {
             return sortableInt.class;
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
    
    public void clear() {
       MyTableModel model = (MyTableModel)TABLE.getModel(); 
       model.setNumRows(0);
    }
    
    public void AddRows(JSONArray data) {
       for (int i=0; i<data.length(); ++i) {
          try {
            AddRow(data.getJSONObject(i), i+1);
         } catch (JSONException e) {
            log.error("pushTable AddRows - " + e.getMessage());
            return;
         }
       }
       TableUtil.packColumns(TABLE,2);
    }
    
    public void AddRow(JSONObject json, int num) {
       Object[] info = new Object[TITLE_cols.length];
       try {
          // NUM
          info[0] = new sortableInt(json, num);
          // Title
          String title = "none";
          if (json.has("title"))
             title = json.getString("title");
          info[1] = title;
          // TiVo
          String tsn = json.getString("bodyId");
          tsn = tsn.replaceFirst("tsn:", "");
          String tivo = config.getTiVoFromTsn(tsn);
          if (tivo == null) tivo = tsn;
          info[2] = tivo;
          TableUtil.AddRow(TABLE, info);       
       } catch (Exception e) {
          log.error("pushTable AddRow - " + e.getMessage());
       }
    }
    
    public void RemoveRow(int row) {
       MyTableModel dm = (MyTableModel)TABLE.getModel();
       dm.removeRow(TABLE.convertRowIndexToModel(row));
    }
    
    public JSONObject GetRowData(int row) {
       sortableInt s = (sortableInt) TABLE.getValueAt(row, TableUtil.getColumnIndex(TABLE, "NUM"));
       if (s != null)
          return s.json;
       return null;
    }    
}
