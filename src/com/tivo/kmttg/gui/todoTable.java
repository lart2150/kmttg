package com.tivo.kmttg.gui;

import java.awt.Component;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Comparator;
import java.util.Hashtable;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;

import org.jdesktop.swingx.JXTable;
import org.jdesktop.swingx.decorator.Sorter;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.auto;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class todoTable {
   private String[] TITLE_cols = {"DATE", "SHOW", "CHANNEL", "DUR"};
   public JXTable TABLE = null;
   public Hashtable<String,JSONArray> tivo_data = new Hashtable<String,JSONArray>();
   private String currentTivo = null;
   public JScrollPane scroll = null;

   todoTable(JFrame dialog) {
      Object[][] data = {};
      TABLE = new JXTable(data, TITLE_cols);
      TABLE.setModel(new MyTableModel(data, TITLE_cols));
      TABLE.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
      TABLE.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      scroll = new JScrollPane(TABLE);
      // Add keyboard listener
      TABLE.addKeyListener(
         new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
               KeyPressed(e);
            }
         }
      );
      
      class ColorColumnRenderer extends DefaultTableCellRenderer {
         private static final long serialVersionUID = 1L;
         
         public ColorColumnRenderer() {
            super(); 
         }
         
         public Component getTableCellRendererComponent
             (JTable table, Object value, boolean isSelected,
              boolean hasFocus, int row, int column) 
         {
            Component cell = super.getTableCellRendererComponent
               (table, value, isSelected, hasFocus, row, column);
            
            if ( ! isSelected ) {
               if (column % 2 == 0)
                  cell.setBackground(config.tableBkgndLight);
               else
                  cell.setBackground(config.tableBkgndDarker);
               JSONObject json = GetRowData(row);
               if (json != null) {
                  try {
                     if (json.has("state")) {
                        if (json.getString("state").equals("inProgress"))
                           cell.setBackground(config.tableBkgndRecording);
                     }
                     
                     if (config.showHistoryInTable == 1 && json.has("partnerCollectionId")) {
                        if (auto.keywordMatchHistoryFast(json.getString("partnerCollectionId"), false))
                           cell.setBackground(config.tableBkgndInHistory);
                     }
                  } catch (JSONException e) {
                     log.error("todoTable ColorColumnRenderer - " + e.getMessage());
                  }
               }
            }         
            cell.setFont(config.tableFont);
           
            return cell;
         }
      }      
      // Change color & font
      TableColumn tm;
      tm = TABLE.getColumnModel().getColumn(0);
      tm.setCellRenderer(new ColorColumnRenderer());
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.RIGHT);
      tm = TABLE.getColumnModel().getColumn(1);
      tm.setCellRenderer(new ColorColumnRenderer());
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.LEFT);
      tm = TABLE.getColumnModel().getColumn(2);
      tm.setCellRenderer(new ColorColumnRenderer());
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.LEFT);
      tm = TABLE.getColumnModel().getColumn(3);
      tm.setCellRenderer(new ColorColumnRenderer());
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.LEFT);
      
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
      
      // Use custom sorting routines for certain columns
      Sorter sorter = TABLE.getColumnExt(0).getSorter();
      sorter.setComparator(sortableComparator);
      sorter = TABLE.getColumnExt(3).getSorter();
      sorter.setComparator(sortableComparator);
      
      // Define selection listener to detect table row selection changes
      TABLE.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
         public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting()) return;
            ListSelectionModel rowSM = (ListSelectionModel)e.getSource();
            int row = rowSM.getMinSelectionIndex();
            if (row > -1) {
               TABLERowSelected(row);
            }
         }
      });
      
      // Add right mouse button handler
      TableUtil.AddRightMouseListener(TABLE);
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
          // NOTE: col index starts at 0
          if (col == 0) {
             return sortableDate.class;
          }
          if (col == 3) {
             return sortableDuration.class;
          }
          return Object.class;
       } 
       
       // Set all cells uneditable
       public boolean isCellEditable(int row, int column) {        
          return false;
       }
    }

    public void AddRows(String tivoName, JSONArray data) {
       try {
          for (int i=0; i<data.length(); ++i) {
             AddRow(data.getJSONObject(i));
          }
          tivo_data.put(tivoName, data);
          currentTivo = tivoName;
          TableUtil.packColumns(TABLE,2);
          if (config.gui.remote_gui != null) {
             config.gui.remote_gui.setTivoName("todo", tivoName);
             refreshNumber();
          }
       } catch (JSONException e) {
          log.error("todoTable AddRows - " + e.getMessage());
       }
    }
    
    private void AddRow(JSONObject data) {
       debug.print("data=" + data);
       try {
          Object[] info = new Object[TITLE_cols.length];
          String startString=null, endString=null;
          long start=0, end=0;
          if (data.has("scheduledStartTime")) {
             startString = data.getString("scheduledStartTime");
             start = TableUtil.getLongDateFromString(startString);
             endString = data.getString("scheduledEndTime");
             end = TableUtil.getLongDateFromString(endString);
          } else if (data.has("startTime")) {
             start = TableUtil.getStartTime(data);
             end = TableUtil.getEndTime(data);
          }
          String title = TableUtil.makeShowTitle(data);
          String channel = TableUtil.makeChannelName(data);
          
          info[0] = new sortableDate(data, start);
          info[1] = title;
          info[2] = channel;
          info[3] = new sortableDuration(end-start, false);
          TableUtil.AddRow(TABLE, info);       
       } catch (Exception e) {
          log.error("todoTable AddRow - " + e.getMessage());
       }
    }
    
    private void TABLERowSelected(int row) {
       debug.print("row=" + row);
       if (row == -1) return;
       // Get column items for selected row 
       sortableDate s = (sortableDate)TABLE.getValueAt(row,TableUtil.getColumnIndex(TABLE, "DATE"));
       if (s.folder) {
          // Folder entry - don't display anything
       } else {
          try {
             // Non folder entry so print single entry info
             sortableDuration dur = (sortableDuration)TABLE.getValueAt(row,TableUtil.getColumnIndex(TABLE, "DUR"));
             String message = TableUtil.makeShowSummary(s, dur);
             String title = "\nToDo: ";
             if (s.json.has("title"))
                title += s.json.getString("title");
             if (s.json.has("subtitle"))
                title += " - " + s.json.getString("subtitle");
             if (s.json.has("state") && s.json.getString("state").equals("inProgress"))
                title += " (currently recording)";
             log.warn(title);
             log.print(message);

             if (config.gui.show_details.isShowing())
                config.gui.show_details.update(currentTivo, s.json);
          } catch (JSONException e) {
             log.error("TABLERowSelected - " + e.getMessage());
             return;
          }
       }
    }
    
    // Handle keyboard presses
    private void KeyPressed(KeyEvent e) {
       if (e.isControlDown())
          return;
       int keyCode = e.getKeyCode();
       if (keyCode == KeyEvent.VK_DELETE){
          // Delete key has special action
          DeleteCB();
       }
       else if (keyCode == KeyEvent.VK_C) {
          config.gui.remote_gui.cancel_todo.doClick();
       }
       else if (keyCode == KeyEvent.VK_M) {
          config.gui.remote_gui.modify_todo.doClick();
       }
       else if (keyCode == KeyEvent.VK_I) {
          int[] selected = TableUtil.GetSelectedRows(TABLE);
          if (selected == null || selected.length < 1)
             return;
          JSONObject json = GetRowData(selected[0]);
          if (json != null) {
             config.gui.show_details.update(currentTivo, json);
          }
       } else if (keyCode == KeyEvent.VK_J) {
          // Print json of selected row to log window
          int[] selected = TableUtil.GetSelectedRows(TABLE);
          if (selected == null || selected.length < 1)
             return;
          JSONObject json = GetRowData(selected[0]);
          if (json != null)
             rnpl.pprintJSON(json);
       } else if (keyCode == KeyEvent.VK_Q) {
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
       } else {
          // Pass along keyboard action
          e.consume();
       }
    }
    
    private JSONObject GetRowData(int row) {
       return TableUtil.GetRowData(TABLE, row, "DATE");
    }
    
    public void DeleteCB() {
       int[] selected = TableUtil.GetSelectedRows(TABLE);
       if (selected == null || selected.length != 1) {
          log.error("Must select a single table row.");
          return;
       }
       if (currentTivo == null) {
          log.error("Table not initialized");
          return;
       }
       int row;
       String title;
       JSONObject json;
       Remote r = config.initRemote(currentTivo);
       if (r.success) {
          // NOTE: Intentionally only remove 1 row at a time because removing rows from table
          row = selected[0];
          json = GetRowData(row);
          if (json != null) {
             try {
                title = json.getString("title");
                if (json.has("subtitle"))
                   title += " - " + json.getString("subtitle");
                log.warn("Cancelling ToDo show on TiVo '" + currentTivo + "': " + title);
                JSONObject o = new JSONObject();
                JSONArray a = new JSONArray();
                a.put(json.getString("recordingId"));
                o.put("recordingId", a);
                if ( r.Command("Cancel", o) != null ) {
                   TableUtil.RemoveRow(TABLE, row);
                   tivo_data.get(currentTivo).remove(row);
                   refreshNumber();
                }
             } catch (JSONException e1) {
                log.error("ToDo cancel - " + e1.getMessage());
             }
          }
          r.disconnect();                   
       }
    }
    
    // Refresh the # SHOWS label in the ToDo tab
    public void refreshNumber() {
       config.gui.remote_gui.label_todo.setText("" + tivo_data.get(currentTivo).length() + " SHOWS");
    }
    
    // Schedule a single recording
    public void recordSingle(String tivoName) {
       int[] selected = TableUtil.GetSelectedRows(TABLE);
       if (selected.length > 0) {
          int row;
          JSONArray entries = new JSONArray();
          JSONObject json;
          for (int i=0; i<selected.length; ++i) {
             row = selected[i];
             json = GetRowData(row);
             entries.put(json);
          }
          TableUtil.recordSingleCB(tivoName, entries);
       }
    }
}
