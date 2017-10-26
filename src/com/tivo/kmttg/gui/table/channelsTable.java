/*
 * Copyright 2008-Present Kevin Moye <moyekj@yahoo.com>.
 *
 * This file is part of kmttg package.
 *
 * kmttg is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this project.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tivo.kmttg.gui.table;

import java.util.Collections;
import java.util.Hashtable;
import java.util.Stack;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONFile;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.TableMap;
import com.tivo.kmttg.gui.comparator.ChannelNumComparator;
import com.tivo.kmttg.gui.sortable.sortableChannelNum;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class channelsTable extends TableMap {
   private String currentTivo = null;
   public TableView<Tabentry> TABLE = null;
   public String[] TITLE_cols = {"NAME", "NUMBER", "RECEIVED"};
   private double[] weights = {40, 30, 30};
   public String folderName = null;
   public int folderEntryNum = -1;
   public Hashtable<String,JSONArray> tivo_data = new Hashtable<String,JSONArray>();
   private Boolean loaded = false;
   private String loadedPrefix = "Loaded: ";
   
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
      setLoaded(false);
   }
   @Override
   public TableView<?> getTable() {
      return TABLE;
   }
   
   public channelsTable() {
      TABLE = new TableView<Tabentry>();
      TABLE.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE); // Allow multi row selection
      TABLE.setEditable(true); // Allow editing
      // Special sort listener to set sort order to ascending channel num when no sort is selected
      TABLE.getSortOrder().addListener(new ListChangeListener<TableColumn<Tabentry, ?>>() {
         @Override
         public void onChanged(Change<? extends TableColumn<Tabentry, ?>> change) {
            change.next();
            if (change != null && change.toString().contains("removed")) {
               if (change.getRemoved().get(0).getText().equals("NUMBER"))
                  return;
               int num_col = TableUtil.getColumnIndex(TABLE, "NUMBER");
               TABLE.getSortOrder().setAll(Collections.singletonList(TABLE.getColumns().get(num_col)));
               TABLE.getColumns().get(num_col).setSortType(TableColumn.SortType.ASCENDING);
            }
         }
      });
      
      for (String colName : TITLE_cols) {
         if (colName.equals("NAME")) {
            TableColumn<Tabentry,jsonString> col = new TableColumn<Tabentry,jsonString>(colName);
            col.setCellValueFactory(new PropertyValueFactory<Tabentry,jsonString>(colName));
            TABLE.getColumns().add(col);
         } else if (colName.equals("NUMBER")) {
            TableColumn<Tabentry,sortableChannelNum> col = new TableColumn<Tabentry,sortableChannelNum>(colName);
            col.setCellValueFactory(new PropertyValueFactory<Tabentry,sortableChannelNum>(colName));
            col.setComparator(new ChannelNumComparator()); // Custom column sort
            TABLE.getColumns().add(col);
         } else {            
            TableColumn<Tabentry,Boolean> col = new TableColumn<Tabentry,Boolean>(colName);
            col.setCellValueFactory(new PropertyValueFactory<Tabentry, Boolean>(colName));
            col.setCellFactory(CheckBoxTableCell.forTableColumn(col));
            col.setEditable(true);
            col.setStyle("-fx-alignment: CENTER;");
            TABLE.getColumns().add(col);
         }
         TableUtil.setWeights(TABLE, TITLE_cols, weights, false);
      }
      
      // Add keyboard listener
      TABLE.setOnKeyPressed(new EventHandler<KeyEvent>() {
         public void handle(KeyEvent e) {
            KeyPressed(e);
         }
      });      
   }
      
   public static class jsonString {
      String display;
      JSONObject json;
      public jsonString(JSONObject json, String name) {
         this.display = name;
         this.json = json;
      }
      public String toString() {
         return display;
      }
   }
      
   public static class Tabentry {
      public jsonString channelName = null;
      public sortableChannelNum channelNum = null;
      public BooleanProperty received = new SimpleBooleanProperty(false);

      public Tabentry(JSONObject entry) {
         try {
            if (entry.has("callSign"))
               channelName = new jsonString(entry, entry.getString("callSign"));
            else
               channelName = new jsonString(entry, "To be announced");
            if (entry.has("channelNumber"))
               channelNum = new sortableChannelNum(entry.getString("channelNumber"));
            if (entry.has("isReceived"))
               received.set(entry.getBoolean("isReceived"));                    
         } catch (JSONException e1) {
            log.error("channelsTable Tabentry - " + e1.getMessage());
         }      
      }
      
      public jsonString getNAME() {
         return channelName;
      }
      
      public sortableChannelNum getNUMBER() {
         return channelNum;
      }
      
      public boolean isRECEIVED() {
         return received.get();
      }

      public String toString() {
         return channelName.toString();
      }      

      public BooleanProperty RECEIVEDProperty() {
         return received;
      }
   }

   public JSONObject GetRowData(int row) {
      return TABLE.getItems().get(row).getNAME().json;
   }
   
   public Boolean GetValueAt(int row, int col) {
      return (Boolean)TABLE.getColumns().get(col).getCellData(row);
   }
      
   // Handle keyboard presses
   private void KeyPressed(KeyEvent e) {
      if (e.isControlDown())
         return;
      KeyCode keyCode = e.getCode();
      if (keyCode == KeyCode.J) {
         // Print json of selected row to log window
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         JSONObject json = GetRowData(selected[0]);
         if (json != null) {
            rnpl.pprintJSON(json);
         }
      }
   }
   
   // Update table to display given entries
   public void AddRows(String tivoName, JSONArray data) {
      try {
         Stack<JSONObject> o = new Stack<JSONObject>();
         for (int i=0; i<data.length(); ++i)
            o.add(data.getJSONObject(i));
         
         // Update table
         Refresh(o);
         TABLE.sort();
         TableUtil.autoSizeTableViewColumns(TABLE, true);
         if (tivoName != null) {
            tivo_data.put(tivoName, data);
            currentTivo = tivoName;
         }
         if (config.gui.remote_gui != null && tivoName != null) {
            config.gui.remote_gui.setTivoName("channels", tivoName);
            refreshNumber();
         }
      } catch (JSONException e) {
         log.error("Channels AddRows - " + e.getMessage());
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
   
   // Update table display to show top level flat structure
   private void displayFlatStructure(Stack<JSONObject> o) {
      for (int i=0; i<o.size(); ++i) {
         AddTABLERow(o.get(i));
      }
   }
   
   private void updateShowRows(String prefix) {
      for (int row=0; row<TABLE.getItems().size(); ++row) {
         Tabentry e = TABLE.getItems().get(row);
         e.channelName.display = prefix + e.channelName.display;
      }
   }
   
   // Add a non folder entry to TABLE table
   public void AddTABLERow(JSONObject entry) {
      debug.print("entry=" + entry);
      TABLE.getItems().add(new Tabentry(entry));
   }   
   
   // Refresh the # CHANNELS label in the Channels tab
   private void refreshNumber() {
      config.gui.remote_gui.channels_tab.label.setText("" + tivo_data.get(currentTivo).length() + " CHANNELS");
   }
   
   public void refreshChannels(String tivoName) {
      clear();
      setLoaded(false);
      jobData job = new jobData();
      job.source         = tivoName;
      job.tivoName       = tivoName;
      job.type           = "remote";
      job.name           = "Remote";
      job.remote_channelsTable  = true;
      job.channelsTable  = this;
      jobMonitor.submitNewJob(job);
   }
   
   // For each row value different that current database, update channel value
   public void updateChannels(final String tivoName) {
      if (isTableLoaded()) {
         log.error("Cannot update a loaded table");
         return;
      }
      try {
         JSONArray changed = new JSONArray();
         for (int row=0; row<TABLE.getItems().size(); ++row) {
            Boolean received_value = GetValueAt(row, TableUtil.getColumnIndex(TABLE, "RECEIVED"));
            JSONObject json = GetRowData(row);
            if (json != null) {
               Boolean isReceived = json.getBoolean("isReceived");
               if (received_value != isReceived) {
                  // Make a copy of json so we don't change it
                  JSONObject j = formatChannel(json, received_value);
                  changed.put(j);
               }
            }
         }
         if (changed.length() > 0) {
            // There are table changes, so update in the background
            class backgroundRun extends Task<Void> {
               JSONArray changed;
               public backgroundRun(JSONArray changed) {
                  this.changed = changed;
               }
               @Override
               protected Void call() {
                  rpcUpdateChannels(tivoName, changed);
                  // Now refresh the channels table
                  Platform.runLater(new Runnable() {
                     @Override public void run() {
                        refreshChannels(tivoName);
                     }
                  });
                  return null;
               }
            }
            backgroundRun b = new backgroundRun(changed);
            new Thread(b).start();
         }
      } catch (Exception e) {
         log.error("updateChannels - " + e.getMessage());
      }
   }
   
   public void saveChannels(String tivoName, String file) {
      if (isTableLoaded()) {
         log.error("Cannot save a loaded table");
         return;
      }
      if (tivo_data.containsKey(tivoName) && tivo_data.get(tivoName).length() > 0) {
         log.warn("Saving '" + tivoName + "' Channels list to file: " + file);
         JSONFile.write(tivo_data.get(tivoName), file);
      } else {
         log.error("No data available to save.");
      }      
   }
   
   public void loadChannels(String file) {
      log.print("Loading Channels data from file: " + file);
      JSONArray data = JSONFile.readJSONArray(file);
      if (data != null && data.length() > 0) {
         // Clear table and display loaded data
         clear();
         AddRows(null, data);
         updateShowRows(loadedPrefix);
         TableUtil.autoSizeTableViewColumns(TABLE, true);
         setLoaded(true);
      }
   }
   
   public void copyChannels(final String tivoName) {
      Task<Void> task = new Task<Void>() {
         @Override public Void call() {
            int[] selected = TableUtil.GetSelectedRows(TABLE);
            if (selected.length > 0) {
               log.print("Copying channels settings to TiVo: " + tivoName);
               // Get channel list for destination TiVo
               // Get selection list from table
               // Compare entries and update channels where isReceived is different
               Hashtable<String,Boolean> received = new Hashtable<String,Boolean>();
               log.print("Getting current channel list for TiVo: " + tivoName);
               try {
                  Remote r = config.initRemote(tivoName);
                  if (r.success) {
                     JSONObject json = new JSONObject();
                     json.put("noLimit", "true");
                     json.put("bodyId", r.bodyId_get());
                     JSONObject result = r.Command("channelSearch", json);
                     if (result != null && result.has("channel")) {
                        for (int i=0; i<result.getJSONArray("channel").length(); ++i) {
                           json = result.getJSONArray("channel").getJSONObject(i);
                           if (json.has("channelNumber")) {
                              Boolean rec = false;
                              if (json.has("isReceived"))
                                 rec = json.getBoolean("isReceived");
                              received.put(json.getString("channelNumber"), rec);
                           }
                        }
                     }
                     r.disconnect();
                     // This channels array will contain list of channels we want to update
                     JSONArray channels = new JSONArray();
                     for (int i=0; i<selected.length; ++i) {
                        int row = selected[i];
                        Boolean tableReceived = GetValueAt(row, TableUtil.getColumnIndex(TABLE, "RECEIVED"));
                        JSONObject rowJson = GetRowData(row);
                        if (rowJson != null && rowJson.has("channelNumber")) {
                           String channelNumber = rowJson.getString("channelNumber");
                           if (received.containsKey(channelNumber)) {
                              // Only look at updating channels that are currently available on the TiVo
                              Boolean tivoReceived = received.get(channelNumber);
                              if (tivoReceived != tableReceived)
                                 channels.put(formatChannel(rowJson, tableReceived));
                           }
                        }
                     }
                     rpcUpdateChannels(tivoName, channels);   
                  } else {
                     log.error("Failed to get current channel list for TiVo: " + tivoName);
                  }
               } catch (JSONException e) {
                  log.error("copyChannels - " + e.getMessage());
               }
            } else {
               log.warn("No rows selected to copy");
            }
            return null;
         }
      };
      new Thread(task).start();
   }
   
   public void rpcUpdateChannels(String tivoName, JSONArray changed) {
      try {
         if (changed.length() > 0) {
            log.warn("Updating channels for TiVo: " + tivoName);
            Remote r = config.initRemote(tivoName);
            if (r.success) {
               for (int i=0; i<changed.length(); ++i) {
                  JSONObject channel = changed.getJSONObject(i);
                  log.print("Setting " + channel.getString("channelNumber") + "=" + channel.getString("callSign") +
                     " isReceived=" + channel.getBoolean("isReceived"));
                  JSONObject result = r.Command("ChannelUpdate", channel);
                  if (result == null)
                     log.error("Failed to change channel: " + channel.getString("channelNumber") + "=" + channel.getString("callSign"));
               }
               r.disconnect();
               log.warn("Channel update requests completed");
            }
         } else {
            log.warn("No channels differences found to update");
         }
      } catch (JSONException e) {
         log.error("rpcUpdateChannels - " + e.getMessage());
      }

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
   
   private JSONObject formatChannel(JSONObject json, Boolean received_value) {
      JSONObject j = new JSONObject();
      try {
         j.put("type", "channel");
         if (json.has("channelNumber"))
            j.put("channelNumber", json.getString("channelNumber"));
         if (json.has("sourceType"))
            j.put("sourceType", json.getString("sourceType"));
         if (json.has("stationId"))
            j.put("stationId", json.getString("stationId"));
         if (json.has("callSign"))
            j.put("callSign", json.getString("callSign"));
         j.put("isReceived", received_value);
      } catch (JSONException e) {
         log.error("formatChannel - " + e.getMessage());
      }
      return j;
   }
   
   public void updateLoadedStatus() {
      if (TABLE.getItems().size() > 0) {
         int col = TableUtil.getColumnIndex(TABLE, "NAME");
         String title = "" + TABLE.getColumns().get(col).getCellData(0);
         if (title != null && title.startsWith(loadedPrefix))
            setLoaded(true);
         else
            setLoaded(false);
      }
   }   
}
