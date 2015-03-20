package com.tivo.kmttg.gui;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Hashtable;
import java.util.Stack;

import javax.swing.DefaultCellEditor;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;

import org.jdesktop.swingx.JXTable;

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
   private Boolean loaded = false;
   private String loadedPrefix = "Loaded: ";
         
   thumbsTable(JFrame dialog) {
      Object[][] data = {}; 
      TABLE = new JXTable(data, TITLE_cols);
      scroll = new JScrollPane(TABLE);
      reset();
   }
   
   public void reset() {
      Object[][] data = {}; 
      TABLE.setModel(new ThumbsTableModel(data, TITLE_cols));
      TABLE.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
      //TABLE.setRowSelectionAllowed(false);
      
      // Add listener for click handling (for folder entries)
      /*TABLE.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               MouseClicked(e);
            }
         }
      );*/
      
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
      
      tm = TABLE.getColumnModel().getColumn(2);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
      // Special cell editor needed to select all text on focus
      MyCellEditor m = new MyCellEditor(new JTextField());
      m.setClickCountToStart(1); // Change cell to edit mode with single click instead of double click
      tm.setCellEditor(m);
               
      // Add right mouse button handler
      TableUtil.AddRightMouseListener(TABLE);
   }   
   
   // Extend editor to select all text when cell receives focus
   // and to check thumbs value
   class MyCellEditor extends DefaultCellEditor {
      private static final long serialVersionUID = 1L;

      public MyCellEditor(final JTextField textField) {
         super(textField);
         textField.addFocusListener( new FocusAdapter() {
            public void focusGained( final FocusEvent e ) {
               textField.selectAll();
            }
         });
      }
      
      public Object getCellEditorValue() {
         Object value = super.getCellEditorValue();
         int val = 1;
         try {
            val = Integer.parseInt((String)value);
         } catch (NumberFormatException e) {
            log.warn("Illegal value - setting to 1");
            val = 1;
         }
         if (val < -3) {
            log.warn("Illegal value - setting to -3");
            val = -3;
         }
         if (val > 3) {
            val = 3;
            log.warn("Illegal value - setting to 3");
         }
         return "" + val;
      }      
   }
   
   // Override some default table model actions
   class ThumbsTableModel extends DefaultTableModel {
      private static final long serialVersionUID = 1L;

      public ThumbsTableModel(Object[][] data, Object[] columnNames) {
         super(data, columnNames);
      }
      
      // This is used to define columns as specific classes
      public Class<?> getColumnClass(int col) {
         return Object.class;
      } 
      
      // Set selected cells editable or non-editable
      public boolean isCellEditable(int row, int column) {
         if (column == 2 && ! isTableLoaded() )
            return true;
         return false;
      }
   }

   private JSONObject GetRowData(int row) {
      String title = (String)TABLE.getModel().getValueAt(row, 1);
      if (title.startsWith(loadedPrefix))
         title = title.replaceFirst(loadedPrefix, "");
      return table_data.get(title);
   }
   
   // Mouse event handler
   /*private void MouseClicked(MouseEvent e) {
      if( e.getClickCount() == 1 ) {
         int row = TABLE.rowAtPoint(e.getPoint());
         TABLE.editCellAt(row, 2);
      }
   }*/
   
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
      }
      else if (keyCode == KeyEvent.VK_C) {
         config.gui.remote_gui.copy_thumbs.doClick();
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
               TableUtil.webQuery(title);
            } catch (JSONException e1) {
               log.error("KeyPressed Q - " + e1.getMessage());
            }
         }
      }
      else {
         // Pass along keyboard action
         e.consume();
      }
   }
   
   // Update table to display given entries
   public void AddRows(String tivoName, JSONArray data) {
      try {
         table_data = new Hashtable<String, JSONObject>();
         Stack<JSONObject> o = new Stack<JSONObject>();
         for (int i=0; i<data.length(); ++i)
            o.add(data.getJSONObject(i));
         
         // Update table
         Refresh(o);
         TableUtil.packColumns(TABLE, 2);
         if (tivoName != null) {
            tivo_data.put(tivoName, data);
            currentTivo = tivoName;
         }
         if (config.gui.remote_gui != null && tivoName != null) {
            config.gui.remote_gui.setTivoName("thumbs", tivoName);
            refreshNumber();
         }
      } catch (JSONException e) {
         log.error("Thumbs AddRows - " + e.getMessage());
      }      
   }
   
   // Refresh table with given given entries
   public void Refresh(Stack<JSONObject> o) {
      clear();
      if (o == null) {
         if (currentTivo != null)
            AddRows(currentTivo, tivo_data.get(currentTivo));
         return;
      }
      if (TABLE != null) {
         displayFlatStructure(o);
      }
   }
   
   public void clear() {
      TableUtil.clear(TABLE);
      setLoaded(false);
   }
   
   // Update table display to show top level flat structure
   private void displayFlatStructure(Stack<JSONObject> o) {
      for (int i=0; i<o.size(); ++i) {
         AddTABLERow(o.get(i));
      }
   }
   
   private void updateTitleCols(String name) {
      String title;
      int col = TableUtil.getColumnIndex(TABLE, "SHOW");
      ThumbsTableModel dm = (ThumbsTableModel)TABLE.getModel();
      for (int row=0; row<TABLE.getRowCount(); ++row) {
         title = (String)TABLE.getValueAt(row,col);
         title = name + title;
         dm.setValueAt(title, row, col);
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
   
   
   // Refresh the # SHOWS label in the ToDo tab
   private void refreshNumber() {
      config.gui.remote_gui.thumbs_label.setText("" + tivo_data.get(currentTivo).length() + " THUMBS");
   }
   
   public void refreshThumbs(String tivoName) {
      clear();
      setLoaded(false);
      jobData job = new jobData();
      job.source         = tivoName;
      job.tivoName       = tivoName;
      job.type           = "remote";
      job.name           = "Remote";
      job.remote_thumbs  = true;
      job.thumbs         = this;
      jobMonitor.submitNewJob(job);
   }
   
   // For each row value different that current database, update thumbs value
   public void updateThumbs(String tivoName) {
      if (isTableLoaded()) {
         log.error("Cannot update a loaded table");
         return;
      }
      try {
         JSONArray changed = new JSONArray();
         for (int row=0; row<TABLE.getModel().getRowCount(); ++row) {
            String table_value = (String)TABLE.getModel().getValueAt(row, 2);
            JSONObject json = GetRowData(row);
            if (json != null) {
               String data_value = "" + json.getInt("thumbsRating");
               if (! table_value.equals(data_value)) {
                  // Make a copy of json so we don't change it
                  JSONObject j = new JSONObject(json.toString());
                  j.put("thumbsRating", Integer.parseInt(table_value));
                  changed.put(j);
               }
            }
         }
         if (changed.length() > 0) {
            // There are table changes, so update in the background
            class backgroundRun extends SwingWorker<Object, Object> {
               private JSONArray changed;
               private String tivoName;
               public backgroundRun(String tivoName, JSONArray changed) {
                  this.tivoName = tivoName;
                  this.changed = changed;
               }
               protected Object doInBackground() {
                  try {
                     Remote r = config.initRemote(tivoName);
                     if (r.success) {
                        for (int i=0; i<changed.length(); ++i) {
                           JSONObject j = changed.getJSONObject(i);
                           log.print("Updating '" + j.getString("title") + "' to thumbs rating: " + j.getInt("thumbsRating"));
                           JSONObject o = new JSONObject();
                           o.put("bodyId", r.bodyId_get());
                           o.put("collectionId", j.getString("collectionId"));
                           o.put("thumbsRating", j.getInt("thumbsRating"));
                           JSONObject result = r.Command("userContentStore", o);
                           if (result != null) {
                              log.print("Thumbs rating updated");
                           }
                        }
                        r.disconnect();
                     }
                  } catch (JSONException e) {
                     log.error("updateThumbs - " + e.getMessage());
                  }
                  // Now refresh the thumbs table
                  reset();
                  refreshThumbs(tivoName);
                  return null;
          }
            }
            backgroundRun b = new backgroundRun(tivoName, changed);
            b.execute();
         }
      } catch (Exception e) {
         log.error("updateThumbs - " + e.getMessage());
      }
   }
   
   public void saveThumbs(String tivoName, String file) {
      if (tivo_data.containsKey(tivoName) && tivo_data.get(tivoName).length() > 0) {
         log.warn("Saving '" + tivoName + "' Thumbs list to file: " + file);
         JSONFile.write(tivo_data.get(tivoName), file);
      } else {
         log.error("No data available to save.");
      }      
   }
   
   public void loadThumbs(String file) {
      log.print("Loading Thumbs data from file: " + file);
      JSONArray data = JSONFile.readJSONArray(file);
      if (data != null && data.length() > 0) {
         // Clear table and display loaded data
         clear();
         AddRows(null, data);
         updateTitleCols(loadedPrefix);
         TableUtil.packColumns(TABLE, 2);
         setLoaded(true);
      }
   }
   
   public void copyThumbs(final String tivoName) {
      class backgroundRun extends SwingWorker<Object, Object> {
         protected Object doInBackground() {
            int[] selected = TableUtil.GetSelectedRows(TABLE);
            if (selected.length > 0) {
               int row;
               JSONObject json, result;
               Remote r = config.initRemote(tivoName);
               if (r.success) {
                  log.print("Copying thumbs ratings to TiVo: " + tivoName);
                  for (int i=0; i<selected.length; ++i) {
                     row = selected[i];
                     json = GetRowData(row);
                     if (json != null) {
                        try {
                           log.print("Copying: " + json.getString("title"));
                           JSONObject o = new JSONObject();
                           o.put("bodyId", r.bodyId_get());
                           o.put("collectionId", json.getString("collectionId"));
                           o.put("thumbsRating", json.getInt("thumbsRating"));
                           result = r.Command("userContentStore", o);
                           if (result != null)
                              log.print("success");
                        } catch (JSONException e) {
                           log.error("thumbsCopy - " + e.getMessage());
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
   
   public Boolean isTableLoaded() {
      return loaded;
   }
   
   private void setLoaded(Boolean flag) {
      if (flag) {
         loaded = true;
      } else {
         loaded = false;
      }
   }
   
   public void updateLoadedStatus() {
      if (TABLE.getRowCount() > 0) {
         int col = TableUtil.getColumnIndex(TABLE, "SHOW");
         String title = (String)TABLE.getValueAt(0,col);
         if (title != null && title.startsWith(loadedPrefix))
            setLoaded(true);
         else
            setLoaded(false);
      }
   }
            
}
