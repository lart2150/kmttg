package com.tivo.kmttg.gui.table;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;
import java.util.Stack;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Callback;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.TableMap;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.comparator.ChannelComparator;
import com.tivo.kmttg.gui.comparator.DateComparator;
import com.tivo.kmttg.gui.comparator.DurationComparator;
import com.tivo.kmttg.gui.remote.util;
import com.tivo.kmttg.gui.sortable.sortableChannel;
import com.tivo.kmttg.gui.sortable.sortableDate;
import com.tivo.kmttg.gui.sortable.sortableDuration;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.log;

public class guideTable extends TableMap {
   private String currentTivo = null;
   public TableView<Tabentry> TABLE = null;
   public ScrollPane scroll = null;
   public String[] TITLE_cols = {"DATE", "SHOW", "CHANNEL", "DUR"};
   public String folderName = null;
   public int folderEntryNum = -1;
   public Hashtable<String,JSONArray> tivo_data = new Hashtable<String,JSONArray>();   
   
   // TableMap overrides
   @Override
   public JSONObject getJson(int row) {
      return GetRowData(row);
   }
   @Override
   public int[] getSelected() {
      return TableUtil.GetSelectedRows(TABLE);
   }
   @Override
   public Boolean isRemote() {
      return true;
   }
   @Override
   public void clear() {
      TABLE.getItems().clear();
   }
   @Override
   public TableView<?> getTable() {
      return TABLE;
   }
         
   public guideTable() {
      TABLE = new TableView<Tabentry>();
      TABLE.getSelectionModel().setSelectionMode(SelectionMode.SINGLE); // Allow multiple row selection
      TABLE.setRowFactory(new ColorRowFactory()); // For row background color handling
      // Special sort listener to set sort order to descending date when no sort is selected
      TABLE.getSortOrder().addListener(new ListChangeListener<TableColumn<Tabentry, ?>>() {
         @Override
         public void onChanged(Change<? extends TableColumn<Tabentry, ?>> change) {
            change.next();
            if (change != null && change.toString().contains("removed")) {
               if (change.getRemoved().get(0).getText().equals("DATE"))
                  return;
               int date_col = TableUtil.getColumnIndex(TABLE, "DATE");
               TABLE.getSortOrder().setAll(Collections.singletonList(TABLE.getColumns().get(date_col)));
               TABLE.getColumns().get(date_col).setSortType(TableColumn.SortType.DESCENDING);
            }
         }
      });
      
      for (String colName : TITLE_cols) {
         if (colName.equals("DATE")) {
            TableColumn<Tabentry,sortableDate> col = new TableColumn<Tabentry,sortableDate>(colName);
            col.setCellValueFactory(new PropertyValueFactory<Tabentry,sortableDate>(colName));
            col.setComparator(new DateComparator());
            TABLE.getColumns().add(col);
         } else if (colName.equals("CHANNEL")) {
            TableColumn<Tabentry,sortableChannel> col = new TableColumn<Tabentry,sortableChannel>(colName);
            col.setCellValueFactory(new PropertyValueFactory<Tabentry,sortableChannel>(colName));
            col.setComparator(new ChannelComparator());
            TABLE.getColumns().add(col);
         } else if (colName.equals("DUR")) {
            TableColumn<Tabentry,sortableDuration> col = new TableColumn<Tabentry,sortableDuration>(colName);
            col.setCellValueFactory(new PropertyValueFactory<Tabentry,sortableDuration>(colName));
            col.setComparator(new DurationComparator());
            TABLE.getColumns().add(col);
         } else {
            // Regular String sort
            TableColumn<Tabentry,String> col = new TableColumn<Tabentry,String>(colName);
            col.setCellValueFactory(new PropertyValueFactory<Tabentry,String>(colName));
            TABLE.getColumns().add(col);
         }
      }
      scroll = new ScrollPane(TABLE);
      scroll.setFitToHeight(true);
      scroll.setFitToWidth(true);
      
      // Add keyboard listener
      TABLE.setOnKeyPressed(new EventHandler<KeyEvent>() {
         public void handle(KeyEvent e) {
            KeyPressed(e);
         }
      });
      
      // Define selection listener to detect table row selection changes
      TABLE.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Tabentry>() {
         @Override
         public void changed(ObservableValue<? extends Tabentry> obs, Tabentry oldSelection, Tabentry newSelection) {
            if (newSelection != null) {
               TABLERowSelected(newSelection);
            }
         }
      });
                              
      // Add right mouse button handler
      TableUtil.AddRightMouseListener(TABLE);
   }

   // ColorRowFactory for setting row background color
   private class ColorRowFactory implements Callback<TableView<Tabentry>, TableRow<Tabentry>> {
      public TableRow<Tabentry> call(TableView<Tabentry> tableView) {
         TableRow<Tabentry> row = new TableRow<Tabentry>() {
            @Override
            public void updateItem(Tabentry entry, boolean empty) {
               super.updateItem(entry,  empty);
               if (empty) {
                  setStyle("");
               } else {
                  setStyle("");
                  if (entry != null) {
                     // Mark rows that are already in To Do
                     JSONObject json = entry.getDATE().json;
                     if (json != null && json.has("__inTodo__"))
                        TableUtil.setRowColor(this, config.tableBkgndProtected);
                  }
               }
            }
         };
         return row;
      }
   }   
   
   public static class Tabentry {
      public String title = "";
      public sortableDate date = null;
      public sortableChannel channel = null;
      public sortableDuration duration = null;
      
      // Root node constructor
      public Tabentry(String s) {
         // Do nothing
      }

      public Tabentry(JSONObject entry) {
         try {
            // If entry is in 1 of todo lists then add special __inTodo__ JSON entry
            config.gui.remote_gui.flagIfInTodo(entry, false);
            String startString = entry.getString("startTime");
            long start = TableUtil.getLongDateFromString(startString);
            long dur = entry.getLong("duration")*1000;
            title = TableUtil.makeShowTitle(entry);
            if (entry.has("channel")) {
               JSONObject chan = entry.getJSONObject("channel");
               if (chan.has("callSign") && chan.has("channelNumber"))
                  channel = new sortableChannel(chan.getString("callSign"), chan.getString("channelNumber"));
            }
            date = new sortableDate(entry, start);
            duration = new sortableDuration(dur, false);
         } catch (JSONException e1) {
            log.error("AddTABLERow - " + e1.getMessage());
         }      
      }

      public sortableDate getDATE() {
         return date;
      }
      
      public String getSHOW() {
         return title;
      }

      public sortableChannel getCHANNEL() {
         return channel;
      }

      public sortableDuration getDUR() {
         return duration;
      }
      
      public String toString() {
         return title;
      }
   }
   
   // This used by remote guide.java when TiVo selection changes
   public void AddRows(String tivoName, JSONArray data) {
      if (data == null) {
         if (currentTivo != null)
            AddRows(currentTivo, tivo_data.get(currentTivo));
         return;
      }
      
      // update remotegui entries
      if (TABLE != null) {
         // Clear table and update channel list
         clear();
         updateChannels_gui(tivoName, data);
      }
   }
      
   public JSONObject GetRowData(int row) {
      return TABLE.getItems().get(row).getDATE().json;
   }
      
   private void TABLERowSelected(Tabentry entry) {
      // Get column items for selected row
      sortableDate s = entry.getDATE();
      try {
         sortableDuration dur = entry.getDUR();
         String message = TableUtil.makeShowSummary(s, dur);
         String title = "\nGuide: ";
         if (s.json.has("title"))
            title += s.json.getString("title");
         if (s.json.has("subtitle"))
            title += " - " + s.json.getString("subtitle");
         if (s.json.has("__inTodo__"))
            title += " (to be recorded on " + s.json.getString("__inTodo__") + ")";
         log.warn(title);
         log.print(message);
         
         if (config.gui.show_details.isShowing())
            config.gui.show_details.update(TABLE, currentTivo, s.json);
      } catch (JSONException e) {
         log.error("TABLERowSelected - " + e.getMessage());
         return;
      }
   }
      
   // Handle keyboard presses
   private void KeyPressed(KeyEvent e) {
      if (e.isControlDown())
         return;
      KeyCode keyCode = e.getCode();
      if (keyCode == KeyCode.I) {
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         JSONObject json = GetRowData(selected[0]);
         if (json != null) {
            config.gui.show_details.update(TABLE, currentTivo, json);
         }
      }
      else if (keyCode == KeyCode.P) {
         config.gui.remote_gui.guide_tab.recordSP.fire();
      }
      else if (keyCode == KeyCode.R) {
         config.gui.remote_gui.guide_tab.record.fire();
      }
      else if (keyCode == KeyCode.W) {
         config.gui.remote_gui.guide_tab.wishlist.fire();
      }
      else if (keyCode == KeyCode.J) {
         // Print json of selected row to log window
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         JSONObject json = GetRowData(selected[0]);
         if (json != null)
            rnpl.pprintJSON(json);
      } else if (keyCode == KeyCode.Q) {
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
   public void setComboBoxDates(ComboBox<String> widget, int hourIncrement, int numDays) {
      // 1st get current setting to restore selection if still relevant
      String current = widget.getSelectionModel().getSelectedItem();
      
      long gmt = new Date().getTime();
      widget.getItems().clear();
      Stack<String> dates = getDisplayTimeRange(gmt, hourIncrement, numDays);
      for(int i=0; i<dates.size(); ++i) {
         widget.getItems().add(dates.get(i));
         if (dates.get(i).equals(current))
            widget.getSelectionModel().select(i);
      }
      if (dates.size() > 0)
         widget.getSelectionModel().select(dates.get(0));
   }
   
   public void updateChannels(final String tivoName, Boolean force) {
      // If not in force mode, use cached data if available
      if (! force && tivo_data.containsKey(tivoName)) {
         updateChannels_gui(tivoName, tivo_data.get(tivoName));
         return;
      }
      
      // No data available so queue up a job to get channel list
      // NOTE: The "remote" task is responsible for updating chan_data
      // once this job completes.
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
   
   // This is called after "remote" task completes and updates the
   // guide channel list in remotegui according to JSONArray data
   public void updateChannels_gui(String tivoName, JSONArray data) {
      currentTivo = tivoName;
      tivo_data.put(tivoName, data);
      try {
         for (int i=0; i<data.length(); ++i) {
            JSONObject chan = data.getJSONObject(i);
            if (chan.has("channelNumber") && chan.has("callSign")) {
               String name = chan.getString("channelNumber") + "=" + chan.getString("callSign");
               config.gui.remote_gui.guide_tab.ChanList.getItems().add(name);
            }
         }
      } catch (JSONException e) {
         log.error("updateChannels_gui - " + e.getMessage());
      }
   }
   
   // Based on "channelNumber=callSign" setting in remotegui list get the channel json data
   // from tivo_data
   private JSONObject getChanInfo(String tivoName, String chanName) {
      if (tivo_data.containsKey(tivoName)) {
         JSONArray a = tivo_data.get(tivoName);
         String[] l = chanName.split("=");
         String channelNumber = l[0];
         String callSign = l[1];
         try {
            for (int i=0; i<a.length(); ++i) {
               JSONObject chan = a.getJSONObject(i);
               if (chan.has("channelNumber") && chan.has("callSign")) {
                  if (chan.getString("channelNumber").equals(channelNumber) && chan.getString("callSign").equals(callSign)) {
                     return chan;
                  }
               }
            }
         } catch (JSONException e) {
            log.error("getChanInfo - " + e.getMessage());
         }
      }
      return null;
   }
   
   // This function called from remotegui to update channel information in table
   public void updateTable(final String tivoName, String chanName) {
      JSONObject chan_data = getChanInfo(tivoName, chanName);
      if (chan_data != null) {
         String startDisplayString = config.gui.remote_gui.getGuideStartTime();
         int range = config.gui.remote_gui.guide_tab.range;
         long start = displayTimeToLong(startDisplayString);
         String minEndTime = rnpl.getStringFromLongDate(start);
         long stop = start + range*60*60*1000;
         String maxStartTime = rnpl.getStringFromLongDate(stop);
         String isReceived = null;
         if (! config.gui.remote_gui.AllChannels())
            isReceived = "true";
         updateFolder(tivoName, range, start, minEndTime, stop, maxStartTime, isReceived, chan_data);
      } else {
         log.error("chan_data missing information for channel: " + chanName);
      }
   }
   
   public void updateFolder(final String tivoName, final int range, final long start,
         final String minEndTime, final long stop, final String maxStartTime, final String isReceived, final JSONObject chan) {
      Task<Void> task = new Task<Void>() {
         @Override public Void call() {
            Remote r = config.initRemote(tivoName);
            if (r.success) {
               try {
                  JSONObject json = new JSONObject();
                  json.put("bodyId", r.bodyId_get());
                  json.put("levelOfDetail", "medium");
                  if (isReceived != null)
                     json.put("isReceived", isReceived);
                  json.put("orderBy", new JSONArray("[\"channelNumber\"]"));
                  json.put("maxStartTime", maxStartTime);
                  json.put("minEndTime", minEndTime);
                  JSONObject channel = new JSONObject();
                  channel.put("channelNumber", chan.getString("channelNumber"));
                  channel.put("type", "channelIdentifier");
                  channel.put("sourceType", chan.getString("sourceType"));
                  json.put("anchorChannelIdentifier", channel);
                  final JSONObject result = r.Command("gridRowSearch", json);
                  r.disconnect();
                  if( result != null ) {
                     if (result.has("gridRow")) {
                        Platform.runLater(new Runnable() {
                           @Override public void run() {
                              try {
                                 clear();
                                 JSONArray matches = result.getJSONArray("gridRow").getJSONObject(0).getJSONArray("offer");
                                 util.updateTodoIfNeeded("Guide");
                                 for (int i=0; i<matches.length(); ++i) {
                                    TABLE.getItems().add(new Tabentry(matches.getJSONObject(i)));
                                 }
                                 TableUtil.autoSizeTableViewColumns(TABLE, true);
                              } catch (Exception e) {
                                 log.error("guideTable updateFolder - " + e.getMessage());
                              }
                           }
                        });
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
      };
      new Thread(task).start();
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
      Task<Void> task = new Task<Void>() {
         @Override public Void call() {
            int[] selected = TableUtil.GetSelectedRows(TABLE);
            if (selected.length > 0) {
               int row;
               JSONArray existing;
               JSONObject json;
               Remote r = config.initRemote(tivoName);
               if (r.success) {
                  // First load existing SPs from tivoName to check against
                  log.warn("Checking existing season passes on '" + tivoName + "' ...");
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
      };
      new Thread(task).start();
   }
}
