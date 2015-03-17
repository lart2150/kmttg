package com.tivo.kmttg.gui;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Hashtable;
import java.util.Stack;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;

import org.jdesktop.swingx.JXTable;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class thumbsTable {
   private String currentTivo = null;
   public JXTable TABLE = null;
   public JScrollPane scroll = null;
   public String[] TITLE_cols = {"TYPE", "SHOW", "RATING"};
   public Boolean inFolder = false;
   public String folderName = null;
   public int folderEntryNum = -1;
   public Hashtable<String,JSONArray> tivo_data = new Hashtable<String,JSONArray>();
   private Hashtable<String, JSONObject> table_data = null;
         
   thumbsTable(JFrame dialog) {
      Object[][] data = {}; 
      TABLE = new JXTable(data, TITLE_cols);
      TABLE.setModel(new ThumbsTableModel(data, TITLE_cols));
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
                        
      // Change color & font
      TableColumn tm;
      tm = TABLE.getColumnModel().getColumn(0);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
      
      tm = TABLE.getColumnModel().getColumn(1);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
      
      tm = TABLE.getColumnModel().getColumn(1);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
               
      // Add right mouse button handler
      TableUtil.AddRightMouseListener(TABLE);
   }   
   
   // Override some default table model actions
   class ThumbsTableModel extends DefaultTableModel {
      private static final long serialVersionUID = 1L;

      public ThumbsTableModel(Object[][] data, Object[] columnNames) {
         super(data, columnNames);
      }
      
      @SuppressWarnings("unchecked")
      // This is used to define columns as specific classes
      public Class getColumnClass(int col) {
         return Object.class;
      } 
      
      // Set all cells uneditable
      public boolean isCellEditable(int row, int column) {        
         return false;
      }
   }
   
   private JSONObject GetRowData(int row) {
      String title = (String)TABLE.getModel().getValueAt(row, 0);
      return table_data.get(title);
   }
   
   // Handle keyboard presses
   private void KeyPressed(KeyEvent e) {
      if (e.isControlDown())
         return;
      int keyCode = e.getKeyCode();
      if (keyCode == KeyEvent.VK_I) {
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         JSONObject json = GetRowData(selected[0]);
         if (json != null) {
            config.gui.show_details.update(currentTivo, json);
         }
      }
      else if (keyCode == KeyEvent.VK_J) {
         // Print json of selected row to log window
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         JSONObject json = GetRowData(selected[0]);
         if (json != null)
            rnpl.pprintJSON(json);
      } else {
         // Pass along keyboard action
         e.consume();
      }
   }

   // Update table to display given entries
   public void AddRows(String tivoName, JSONArray data) {
      try {
         Stack<JSONObject> o = new Stack<JSONObject>();
         for (int i=0; i<data.length(); ++i)
            o.add(data.getJSONObject(i));
         
         // Reset local entries to new entries
         Refresh(o);
         TableUtil.packColumns(TABLE, 2);
         tivo_data.put(tivoName, data);
         currentTivo = tivoName;
         if (config.gui.remote_gui != null) {
            config.gui.remote_gui.setTivoName("thumbs", tivoName);
         }
      } catch (JSONException e) {
         log.print("Thumbs AddRows - " + e.getMessage());
      }      
   }
   
   // Refresh table with given given entries
   public void Refresh(Stack<JSONObject> o) {
      table_data = new Hashtable<String, JSONObject>();
      if (o == null) {
         if (currentTivo != null)
            AddRows(currentTivo, tivo_data.get(currentTivo));
         return;
      }
      if (TABLE != null) {
         displayFlatStructure(o);
      }
   }
   
   // Update table display to show top level flat structure
   private void displayFlatStructure(Stack<JSONObject> o) {
      TableUtil.clear(TABLE);
      for (int i=0; i<o.size(); ++i) {
         AddTABLERow(o.get(i));
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
         String type = "";
         if (entry.has("collectionType"))
            type = entry.getString("collectionType");
         String title = "";
         if (entry.has("title"))
            title = entry.getString("title");
         String thumbs = "";
         if (entry.has("thumbsRating"))
            thumbs = "" + entry.getInt("thumbsRating");
   
         data[0] = type;
         data[1] = title;
         data[2] = thumbs;
         
         TableUtil.AddRow(TABLE, data);
         table_data.put(title, entry);
         
         // Adjust column widths to data
         TableUtil.packColumns(TABLE, 2);
      } catch (JSONException e1) {
         log.error("AddTABLERow - " + e1.getMessage());
      }      
   }   
            
}
