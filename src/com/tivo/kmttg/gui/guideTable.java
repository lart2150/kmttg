package com.tivo.kmttg.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.Hashtable;
import java.util.Stack;

import javax.swing.Icon;
import javax.swing.JComboBox;
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
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class guideTable {
   private String currentTivo = null;
   public JXTable TABLE = null;
   public JScrollPane scroll = null;
   public String[] TITLE_cols = {"", "DATE", "SHOW", "CHANNEL", "DUR"};
   public Boolean inFolder = false;
   public String folderName = null;
   private JSONObject currentChannel = null;
   public int folderEntryNum = -1;
   public Hashtable<String,JSONArray> tivo_data = new Hashtable<String,JSONArray>();
         
   guideTable(JFrame dialog) {
      Object[][] data = {}; 
      TABLE = new JXTable(data, TITLE_cols);
      TABLE.setModel(new GuideTableModel(data, TITLE_cols));
      TABLE.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
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
               sortableDuration d1 = (sortableDuration)o1;
               sortableDuration d2 = (sortableDuration)o2;
               if (d1.sortable > d2.sortable) return 1;
               if (d1.sortable < d2.sortable) return -1;
               return 0;
            }
            return 0;
         }
      };
      
      // Use custom sorting routines for certain columns
      Sorter sorter = TABLE.getColumnExt(1).getSorter();
      sorter.setComparator(sortableComparator);
      sorter = TABLE.getColumnExt(4).getSorter();
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
                        
      // Change color & font
      TableColumn tm;
      // Allow icons in column 0
      TABLE.setDefaultRenderer(Icon.class, new IconCellRenderer());
      
      tm = TABLE.getColumnModel().getColumn(1);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
      // Right justify dates
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.RIGHT);
      
      tm = TABLE.getColumnModel().getColumn(2);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
      
      tm = TABLE.getColumnModel().getColumn(3);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
      
      tm = TABLE.getColumnModel().getColumn(4);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
      // Center justify duration
      ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.CENTER);
               
      //TABLE.setFillsViewportHeight(true);
      //TABLE.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
   }   

   // Custom table cell renderer to allow for icons
   class IconCellRenderer extends DefaultTableCellRenderer {
      private static final long serialVersionUID = 1L;

      protected void setValue(Object value) {
         debug.print("value=" + value);
         if (value instanceof Icon) {
            setIcon((Icon)value);
            super.setValue(null);
         } else {
            setIcon(null);
            super.setValue(value);
         }
      }
      
      public Component getTableCellRendererComponent
      (JTable table, Object value, boolean isSelected,
       boolean hasFocus, int row, int column) {
         Component cell = super.getTableCellRendererComponent(
            table, value, isSelected, hasFocus, row, column
         );
        
         if ( ! isSelected ) {
            if (column % 2 == 0)
               cell.setBackground(config.tableBkgndLight);
            else
               cell.setBackground(config.tableBkgndDarker);
         }            
         return cell;
      }
   }
   
   // Override some default table model actions
   class GuideTableModel extends DefaultTableModel {
      private static final long serialVersionUID = 1L;

      public GuideTableModel(Object[][] data, Object[] columnNames) {
         super(data, columnNames);
      }
      
      @SuppressWarnings("unchecked")
      // This is used to define columns as specific classes
      public Class getColumnClass(int col) {
         if (col == 0) {
            return Icon.class;
         }
         if (col == 1) {
            return sortableDate.class;
         }
         if (col == 4) {
            return sortableDuration.class;
         }
         return Object.class;
      } 
      
      // Set all cells uneditable
      public boolean isCellEditable(int row, int column) {        
         return false;
      }
   }   
   
   /**
   * Applied background color to single column of a JTable
   * in order to distinguish it apart from other columns.
   */ 
   class ColorColumnRenderer extends DefaultTableCellRenderer 
   {
      /**
       * 
       */
      private static final long serialVersionUID = 1L;
      Color bkgndColor;
      Font font;
      
      public ColorColumnRenderer(Color bkgnd, Font font) {
         super(); 
         bkgndColor = bkgnd;
         this.font = font;
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
            if (json != null && json.has("__inTodo__"))
               cell.setBackground(config.tableBkgndProtected);
         }         
         cell.setFont(config.tableFont);
        
         return cell;
      }
   }
   
   private JSONObject GetRowData(int row) {
      return TableUtil.GetRowData(TABLE, row, "DATE");
   }
      
   // Mouse event handler
   // This will display folder entries in table if folder entry single-clicked
   private void MouseClicked(MouseEvent e) {
      if( e.getClickCount() == 1 ) {
         int row = TABLE.rowAtPoint(e.getPoint());
         sortableDate s = (sortableDate)TABLE.getValueAt(row,TableUtil.getColumnIndex(TABLE, "DATE"));
         if (s.folder) {
            folderName = s.folderName;
            folderEntryNum = row;
            setFolderState(true);
            currentChannel = s.json;
            String start = config.gui.remote_gui.getGuideStartTime();
            int range = config.gui.remote_gui.guide_range;
            updateFolder(start, range);
         }
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
            JSONObject o;
            String channelNum = null;
            String channel = null;
            if (s.json.has("channel")) {
               o = s.json.getJSONObject("channel");
               if ( o.has("channelNumber") ) {
                  channelNum = o.getString("channelNumber");
               }
               if ( o.has("callSign") ) {
                  channel = o.getString("callSign");
               }
            }
            String description = null;
            if ( s.json.has("description") ) {
               description = s.json.getString("description");
            }
            String d = "";
            if (dur.sortable != null) {
               d = rnpl.msecsToMins(dur.sortable);
            }
            String message = "";
            if (s.display != null)
               message = s.display;
            if (channelNum != null && channel != null) {
               message += " on " + channelNum + "=" + channel;
            }
            message += ", Duration = " + d;
            
            if (s.json.has("seasonNumber"))
               message += ", season " + s.json.get("seasonNumber");
            if (s.json.has("episodeNum"))
               message += " episode " + s.json.getJSONArray("episodeNum").get(0);
            if (s.json.has("originalAirdate"))
               message += ", originalAirdate: " + s.json.getString("originalAirdate");
            
            if (description != null) {
               message += "\n" + description;
            }
      
            String title = "\nGuide: ";
            if (s.json.has("title"))
               title += s.json.getString("title");
            if (s.json.has("subtitle"))
               title += " - " + s.json.getString("subtitle");
            if (s.json.has("__inTodo__"))
               title += " (to be recorded on " + s.json.getString("__inTodo__") + ")";
            log.warn(title);
            log.print(message);
         } catch (JSONException e) {
            log.error("TABLERowSelected - " + e.getMessage());
            return;
         }
      }
   }

   // Update table to display given entries
   // data is a JSONArray of channel JSON objects
   public void AddRows(String tivoName, JSONArray data) {
      Refresh(data);
      TableUtil.packColumns(TABLE, 2);
      
      // Save the data
      currentTivo = tivoName;
      tivo_data.put(tivoName, data);
      
      // Update guide tab to show this tivoName
      if (config.gui.remote_gui != null)
         config.gui.remote_gui.setTivoName("guide", tivoName);
   }
   
   // Refresh whole table
   public void Refresh(JSONArray data) {
      if (data == null) {
         if (currentTivo != null)
            AddRows(currentTivo, tivo_data.get(currentTivo));
         return;
      }
      if (TABLE != null) {
         // Top level folder structure
         TableUtil.clear(TABLE);
         // Add all folders
         for (int i=0; i<data.length(); ++i) {
            try {
               AddTABLERow(data.getJSONObject(i), true);
            } catch (JSONException e) {
               log.error("Refresh - " + e.getMessage());
            }
         }
      }
   }
   
   // Refresh to show inside a particular folder
   public void Refresh(JSONObject data) {
      TableUtil.clear(TABLE);
      try {
         for (int i=0; i<data.getJSONArray("entries").length(); ++i) {
            AddTABLERow(data.getJSONArray("entries").getJSONObject(i), false);
         }
      } catch (JSONException e) {
         log.error("Refresh - " + e.getMessage());
      }
   }
   
   public void setFolderState(Boolean state) {
      if (state) {
         inFolder = true;
         if (config.gui.remote_gui != null) {
            config.gui.remote_gui.refresh_guide.setText("Back");
            config.gui.remote_gui.refresh_guide.setToolTipText(
               config.gui.remote_gui.getToolTip("back_guide")
            );
         }
      } else {
         inFolder = false;
         if (config.gui.remote_gui != null) {
            config.gui.remote_gui.refresh_guide.setText("Channels");
            config.gui.remote_gui.refresh_guide.setToolTipText(
               config.gui.remote_gui.getToolTip("refresh_guide")
            );
         }
      }
   }
      
   // Add row to table
   public void AddTABLERow(JSONObject entry, Boolean folder) {
      try {
         int cols = TITLE_cols.length;
         Object[] data;
         if (folder) {
            // Initialize to empty strings
            data = new Object[cols];
            for (int i=0; i<cols; ++i) {
               data[i] = "";
            }
            String channel = "";
            if (entry.has("channelNumber"))
               channel = entry.getString("channelNumber");
            if (entry.has("callSign"))
               channel += "=" + entry.getString("callSign");
            data[0] = gui.Images.get("folder");
            data[1] = new sortableDate(channel, entry, 0);
            data[3] = channel;
         } else {
            data = makeTableEntry(entry);
         }
         
         TableUtil.AddRow(TABLE, data);
         
         // Adjust column widths to data
         TableUtil.packColumns(TABLE, 2);
      } catch (JSONException e1) {
         log.error("AddTABLERow - " + e1.getMessage());
      }      
   }  
   
   private Object[] makeTableEntry(JSONObject entry) {
      try {
         int cols = TITLE_cols.length;
         Object[] data = new Object[cols];
         // Initialize to empty strings
         for (int i=0; i<cols; ++i) {
            data[i] = "";
         }
         // If entry is in 1 of todo lists then add special __inTodo__ JSON entry
         config.gui.remote_gui.flagIfInTodo(entry);
         JSONObject o = new JSONObject();
         String startString = entry.getString("startTime");
         long start = TableUtil.getLongDateFromString(startString);
         long duration = entry.getLong("duration")*1000;
         String title = " ";
         if (entry.has("title"))
            title += entry.getString("title");
         if (entry.has("seasonNumber") && entry.has("episodeNum")) {
            title += " [Ep " + entry.get("seasonNumber") +
            String.format("%02d]", entry.getJSONArray("episodeNum").get(0));
         }
         if (entry.has("subtitle"))
            title += " - " + entry.getString("subtitle");
         String channel = " ";
         if (entry.has("channel")) {
            o = entry.getJSONObject("channel");
            if (o.has("channelNumber"))
               channel += o.getString("channelNumber");
            if (o.has("callSign"))
               channel += "=" + o.getString("callSign");
         }
         data[1] = new sortableDate(entry, start);
         data[2] = title;
         data[3] = channel;
         data[4] = new sortableDuration(duration, false);
         return data;
      } catch (JSONException e1) {
         log.error("AddTABLERow - " + e1.getMessage());
      }
      return null;
   }
   
   // Look for entry with given folder name and select it
   // (This used when returning back from folder mode to top level mode)
   public void SelectFolder(String folderName) {
      debug.print("folderName=" + folderName);
      for (int i=0; i<TABLE.getRowCount(); ++i) {
         sortableDate s = (sortableDate)TABLE.getValueAt(i,TableUtil.getColumnIndex(TABLE, "DATE"));
         if (s.folder) {
            if (s.folderName.equals(folderName)) {
               TABLE.clearSelection();
               try {
                  TABLE.setRowSelectionInterval(i,i);
                  TABLE.scrollRectToVisible(TABLE.getCellRect(i, 0, true));
               }
               catch (Exception e) {
                  // This is here because JXTable seems to have a problem sometimes after table cleared
                  // and an item is selected. This prevents nasty stack trace problem from being
                  // printed to message window
                  System.out.println("Exception: " + e.getMessage());
               }
               return;
            }
         }
      }
   }
   
   // Handle keyboard presses
   private void KeyPressed(KeyEvent e) {
      int keyCode = e.getKeyCode();
      if (keyCode == KeyEvent.VK_J) {
         // Print json of selected row to log window
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         JSONObject json = GetRowData(selected[0]);
         if (json != null)
            rnpl.printJSON(json);
      } else {
         // Pass along keyboard action
         e.consume();
      }
   }
   
   // Return time rounded down to nearest hour in nice display format
   private String getDisplayTime(long gmt) {
      // Round down to nearest hour
      gmt -= gmt % (1000 * 60 * 1);
      SimpleDateFormat sdf = new SimpleDateFormat("E MM/dd/yyyy h a");
      return sdf.format(gmt);
   }
   
   // Return a range of dates as an array
   private Stack<String> getDisplayTimeRange(long gmt, int hourIncrement, int numDays) {
      Stack<String> range = new Stack<String>();
      long increment = hourIncrement*60*60*1000;
      long stop = gmt + (long)numDays*24*60*60*1000;
      long time = gmt;
      while (time <= stop) {
         range.add(getDisplayTime(time));
         time += increment;
      }
      return range;
   }

   // This converts time as displayed in guide tab combo box to long
   public long displayTimeToLong(String date) {
      try {
         SimpleDateFormat sdf = new SimpleDateFormat("E MM/dd/yyyy h a");
         // First convert to long and then to iPad time format (which is in GMT)
         return sdf.parse(date).getTime();
      } catch (ParseException e) {
         log.error("displayTimeToLong error: " + e.getMessage());
      }
      return 0;
      
   }
   
   // Refresh a combo box with new date range
   public void setComboBoxDates(JComboBox widget, int hourIncrement, int numDays) {
      long gmt = new Date().getTime();
      widget.removeAllItems();
      Stack<String> dates = getDisplayTimeRange(gmt, hourIncrement, numDays);
      for(int i=0; i<dates.size(); ++i)
         widget.addItem(dates.get(i));
   }
   
   public void updateChannels(final String tivoName) {
      // Only need to do this once to get channel list
      if (tivo_data.containsKey(tivoName) && tivo_data.get(tivoName) != null) {
         TableUtil.clear(TABLE);
         AddRows(tivoName, tivo_data.get(tivoName));
         return;
      }
      // No data available so queue up a job to get channel list
      log.warn("Obtaining list of channels for TiVo: " + tivoName);
      jobData job = new jobData();
      job.source                = tivoName;
      job.tivoName              = tivoName;
      job.type                  = "remote";
      job.name                  = "Remote";
      job.gTable                = this;
      job.remote_guideChannels  = true;
      jobMonitor.submitNewJob(job);
   }
   
   public void updateFolder(String startDisplayString, int range) {
      final String tivoName = currentTivo;
      long start = displayTimeToLong(startDisplayString);
      final String minEndTime = rnpl.getStringFromLongDate(start);
      long stop = start + range*60*60*1000;
      final String maxStartTime = rnpl.getStringFromLongDate(stop);
      
      class backgroundRun extends SwingWorker<Object, Object> {
         protected Object doInBackground() {
            Remote r = config.initRemote(tivoName);
            if (r.success) {
               try {
                  JSONObject json = new JSONObject();
                  json.put("bodyId", r.bodyId_get());
                  json.put("levelOfDetail", "medium");
                  json.put("isReceived", "true");
                  json.put("orderBy", new JSONArray("[\"channelNumber\"]"));
                  json.put("maxStartTime", maxStartTime);
                  json.put("minEndTime", minEndTime);
                  JSONObject channel = new JSONObject();
                  channel.put("channelNumber", currentChannel.getString("channelNumber"));
                  channel.put("type", "channelIdentifier");
                  channel.put("sourceType", currentChannel.getString("sourceType"));
                  json.put("anchorChannelIdentifier", channel);
                  JSONObject result = r.Command("gridRowSearch", json);
                  r.disconnect();
                  if( result != null ) {
                     if (result.has("gridRow")) {
                        TableUtil.clear(TABLE);
                        JSONArray matches = result.getJSONArray("gridRow").getJSONObject(0).getJSONArray("offer");
                        for (int i=0; i<matches.length(); ++i) {
                           AddTABLERow(matches.getJSONObject(i), false);
                        }
                     } else {
                        log.error(
                           "No guide data available: start=" + minEndTime +
                           ", channel=" + channel.getString("channelNumber")
                        );
                        
                     }
                  }                  
               } catch (JSONException e1) {
                  log.error("updateFolder - " + e1.getMessage());
               }
            } // if r.success
            return null;
         }
      }
      backgroundRun b = new backgroundRun();
      b.execute();
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
   
   // Create a Season Pass
   public void recordSP(final String tivoName) {
      final int[] selected = TableUtil.GetSelectedRows(TABLE);
      // First check if all selected entries are of type 'series'
      for (int i=0; i<selected.length; ++i) {
         int row = selected[i];
         JSONObject json = GetRowData(row);
         if (json != null) {
            try {
               String type = json.getString("collectionType");
               if (! type.equals("series")) {
                  log.error("Selected entry not of type 'series': " + json.getString("title"));
                  return;
               }
            } catch (JSONException e) {
               log.error("search_sp_recordCB - " + e.getMessage());
               return;
            }
         }
      }

      // Proceed with SP scheduling
      class backgroundRun extends SwingWorker<Object, Object> {
         protected Object doInBackground() {
            int[] selected = TableUtil.GetSelectedRows(TABLE);
            if (selected.length > 0) {
               int row;
               JSONArray existing;
               JSONObject json;
               Remote r = config.initRemote(tivoName);
               if (r.success) {
                  // First load existing SPs from tivoName to check against
                  existing = r.SeasonPasses(null);
                  if (existing == null) {
                     log.error("Failed to grab existing SPs to check against for TiVo: " + tivoName);
                     r.disconnect();
                     return null;
                  }
                  // Now proceed with subscriptions
                  for (int i=0; i<selected.length; ++i) {
                     row = selected[i];
                     json = GetRowData(row);
                     if (json != null)
                        r.SPschedule(tivoName, json, existing);
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
}
