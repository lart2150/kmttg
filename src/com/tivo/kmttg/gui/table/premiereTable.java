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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SortEvent;
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
import com.tivo.kmttg.gui.comparator.DateComparator;
import com.tivo.kmttg.gui.comparator.DurationComparator;
import com.tivo.kmttg.gui.comparator.StringChannelComparator;
import com.tivo.kmttg.gui.sortable.sortableDate;
import com.tivo.kmttg.gui.sortable.sortableDuration;
import com.tivo.kmttg.main.auto;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.id;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.log;

public class premiereTable extends TableMap {
   private String[] TITLE_cols = {"DATE", "SHOW", "SEA", "CHANNEL", "DUR"};
   private double[] weights = {20, 54, 5, 15, 6};
   private String currentTivo = null;
   public TableView<Tabentry> TABLE = null;
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

   public premiereTable() {
      TABLE = new TableView<Tabentry>();
      TABLE.getSelectionModel().setSelectionMode(SelectionMode.SINGLE); // Allow single row selection
      TABLE.setRowFactory(new ColorRowFactory()); // For row background color handling
      // Special sort listener to set sort order to ascending date when no sort is selected
      TABLE.getSortOrder().addListener(new ListChangeListener<TableColumn<Tabentry, ?>>() {
         @Override
         public void onChanged(Change<? extends TableColumn<Tabentry, ?>> change) {
            change.next();
            if (change != null && change.toString().contains("removed")) {
               if (change.getRemoved().get(0).getText().equals("DATE"))
                  return;
               int date_col = TableUtil.getColumnIndex(TABLE, "DATE");
               TABLE.getSortOrder().setAll(Collections.singletonList(TABLE.getColumns().get(date_col)));
               TABLE.getColumns().get(date_col).setSortType(TableColumn.SortType.ASCENDING);
            }
         }
      });
      
      // Keep selection visible following sort event
      TABLE.setOnSort(new EventHandler<SortEvent<TableView<Tabentry>>>() {
         @Override public void handle(SortEvent<TableView<Tabentry>> event) {
            Platform.runLater(new Runnable() {
               @Override public void run() {
                  // If there's a table selection make sure it's visible
                  TableUtil.selectedVisible(TABLE);
               }
            });
         }
      });

      for (String colName : TITLE_cols) {
         if (colName.equals("DATE")) {
            TableColumn<Tabentry,sortableDate> col = new TableColumn<Tabentry,sortableDate>(colName);
            col.setCellValueFactory(new PropertyValueFactory<Tabentry,sortableDate>(colName));
            col.setComparator(new DateComparator()); // Custom column sort
            col.setStyle("-fx-alignment: CENTER-RIGHT;");
            TABLE.getColumns().add(col);
         } else if (colName.equals("DUR")) {
            TableColumn<Tabentry,sortableDuration> col = new TableColumn<Tabentry,sortableDuration>(colName);
            col.setCellValueFactory(new PropertyValueFactory<Tabentry,sortableDuration>(colName));
            col.setComparator(new DurationComparator()); // Custom column sort
            col.setStyle("-fx-alignment: CENTER;");
            TABLE.getColumns().add(col);
         } else {
            // Regular String sort
            TableColumn<Tabentry,String> col = new TableColumn<Tabentry,String>(colName);
            col.setCellValueFactory(new PropertyValueFactory<Tabentry,String>(colName));
            if (colName.equals("SEA"))
               col.setStyle("-fx-alignment: CENTER;");
            if (colName.equals("CHANNEL"))
               col.setComparator(new StringChannelComparator()); // Custom column sort
            TABLE.getColumns().add(col);
         }
         TableUtil.setWeights(TABLE, TITLE_cols, weights, false);
      }
      
      // Add keyboard listener
      TABLE.setOnKeyPressed(new EventHandler<KeyEvent>() {
         public void handle(KeyEvent e) {
               KeyPressed(e);
            }
         }
      );
      
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
               styleProperty().unbind(); setStyle("");
               if (entry != null) {
                  JSONObject json = entry.getDATE().json;
                  // Mark rows that already have SPs scheduled
                  if (json != null && json.has("__SPscheduled__")) {
                     TableUtil.setRowColor(this, config.tableBkgndProtected);
                  }
               }
            }
         };
         return row;
      }
   }   

   public class Tabentry {
      public String title = "";
      public sortableDate date;
      public String channel = "";
      public String season = "";
      public sortableDuration duration;

      public Tabentry(JSONObject data) {
         try {
            String startString = data.getString("startTime");
            long start = getLongDateFromString(startString);
            long dur = data.getLong("duration")*1000;
            if (data.has("title"))
               title += data.getString("title");
            if (data.has("subtitle"))
               title += " - " + data.getString("subtitle");
            channel = TableUtil.makeChannelName(data);
            if (data.has("seasonNumber")) {
               season = String.format("%02d", data.getInt("seasonNumber"));
            }
            
            date = new sortableDate(data, start);
            duration = new sortableDuration(dur, false);
         } catch (JSONException e1) {
            log.error("premiereTable Tabentry - " + e1.getMessage());
         }      
      }

      public sortableDate getDATE() {
         return date;
      }
      
      public String getSHOW() {
         return title;
      }
      
      public String getSEA() {
         return season;
      }

      public String getCHANNEL() {
         return channel;
      }

      public sortableDuration getDUR() {
         return duration;
      }
      
      public String toString() {
         return title;
      }
   }
         
   public JSONObject GetRowData(int row) {
      return TABLE.getItems().get(row).getDATE().json;
   }

    public void AddRows(String tivoName, JSONArray data) {
       try {
          for (int i=0; i<data.length(); ++i) {
             AddRow(data.getJSONObject(i));
          }
          currentTivo = tivoName;
          tivo_data.put(tivoName, data);
          TABLE.sort();
          TableUtil.autoSizeTableViewColumns(TABLE, true);
          if (config.gui.remote_gui != null)
             config.gui.remote_gui.setTivoName("premiere", tivoName);
       } catch (JSONException e) {
          log.error("premiereTable AddRows - " + e.getMessage());
       }
    }
    
    private void AddRow(JSONObject data) {
       TABLE.getItems().add(new Tabentry(data));
    }
    
    private void TABLERowSelected(Tabentry entry) {
       // Get column items for selected row 
       sortableDate s = entry.getDATE();
       if (s.folder) {
          // Folder entry - don't display anything
       } else {
          try {
             // Non folder entry so print single entry info
             sortableDuration dur = entry.getDUR();
             String message = TableUtil.makeShowSummary(s, dur);
             String title = "\nPremiere: ";
             if (s.json.has("title"))
                title += s.json.getString("title");
             if (s.json.has("subtitle"))
                title += " - " + s.json.getString("subtitle");
             if (s.json.has("__SPscheduled__"))
                title += " (SP on " + s.json.getString("__SPscheduled__") + ")";
                
             log.warn(title);
             log.print(message);
             
             if (config.gui.show_details.isShowing())
                config.gui.show_details.update(TABLE, currentTivo, s.json);
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
       else if (keyCode == KeyCode.A) {
          int[] selected = TableUtil.GetSelectedRows(TABLE);
          if (selected == null || selected.length < 1)
             return;
          JSONObject json = GetRowData(selected[0]);
          if (json != null)
             auto.AddHistoryEntry(json);
       }
       else if (keyCode == KeyCode.P) {
          config.gui.remote_gui.premiere_tab.recordSP.fire();
       }
       else if (keyCode == KeyCode.R) {
          config.gui.remote_gui.premiere_tab.record.fire();
       }
       else if (keyCode == KeyCode.W) {
          config.gui.remote_gui.premiere_tab.wishlist.fire();
       }
       else if (keyCode == KeyCode.J) {
          // Print json of selected row to log window
          int[] selected = TableUtil.GetSelectedRows(TABLE);
          if (selected == null || selected.length < 1)
             return;
          JSONObject json = GetRowData(selected[0]);
          if (json != null) {
             rnpl.pprintJSON(json);
             id.printIds(json);
          }
       } else if (keyCode == KeyCode.N) {
          int[] selected = TableUtil.GetSelectedRows(TABLE);
          if (selected == null || selected.length < 1)
             return;
          TableUtil.PrintEpisodes(GetRowData(selected[0]));
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

    private long getLongDateFromString(String date) {
       try {
          SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz");
          Date d = format.parse(date + " GMT");
          return d.getTime();
       } catch (ParseException e) {
         log.error("premiereTable getLongDate - " + e.getMessage());
         return 0;
       }
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
    
    // Schedule to record selected entries in tab_premiere.TABLE
    public void recordSP(final String tivoName) {
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
                }
             }
             return null;
          }
       };
       new Thread(task).start();
    }
}
