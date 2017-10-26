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
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SortEvent;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

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
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.id;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.log;

public class deletedTable extends TableMap {
   private String currentTivo = null;
   public TableView<Tabentry> TABLE = null;
   public String[] TITLE_cols = {"SHOW", "DELETED", "RECORDED", "CHANNEL", "DUR"};
   private double[] weights = {45, 17, 17, 15, 6};
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
         
   public deletedTable() {
      TABLE = new TableView<Tabentry>();
      TABLE.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE); // Allow multiple row selection
      // Special sort listener to set sort order to descending date when no sort is selected
      TABLE.getSortOrder().addListener(new ListChangeListener<TableColumn<Tabentry, ?>>() {
         @Override
         public void onChanged(Change<? extends TableColumn<Tabentry, ?>> change) {
            change.next();
            if (change != null && change.toString().contains("removed")) {
               if (change.getRemoved().get(0).getText().equals("DELETED"))
                  return;
               int date_col = TableUtil.getColumnIndex(TABLE, "DELETED");
               TABLE.getSortOrder().setAll(Collections.singletonList(TABLE.getColumns().get(date_col)));
               TABLE.getColumns().get(date_col).setSortType(TableColumn.SortType.DESCENDING);
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
         if (colName.equals("DELETED") || colName.equals("RECORDED")) {
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
   
   public static class Tabentry {
      public String title = "";
      public sortableDate deleted;
      public sortableDate recorded;
      public String channel = "";
      public sortableDuration duration;

      public Tabentry(JSONObject entry) {
         try {
            String startString=null, endString=null, delString=null;
            long start=0, end=0, del=0;
            if (entry.has("scheduledStartTime")) {
               startString = entry.getString("scheduledStartTime");
               start = TableUtil.getLongDateFromString(startString);
               endString = entry.getString("scheduledEndTime");
               end = TableUtil.getLongDateFromString(endString);
            } else {
               start = TableUtil.getStartTime(entry);
               end = TableUtil.getEndTime(entry);
            }
            if (entry.has("deletionTime")) {
               delString = entry.getString("deletionTime");
               del = TableUtil.getLongDateFromString(delString);            
            }
            title = TableUtil.makeShowTitle(entry);
            channel = TableUtil.makeChannelName(entry);
      
            deleted = new sortableDate(entry, del);
            recorded = new sortableDate(new JSONObject(), start);
            duration = new sortableDuration(end-start, false);
         } catch (JSONException e1) {
            log.error("AddTABLERow - " + e1.getMessage());
         }      
      }
      
      public String getSHOW() {
         return title;
      }

      public sortableDate getDELETED() {
         return deleted;
      }
      
      public sortableDate getRECORDED() {
         return recorded;
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
      return TABLE.getItems().get(row).getDELETED().json;
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
      else if (keyCode == KeyCode.R) {
         config.gui.remote_gui.deleted_tab.recover.fire();
      }
      else if (keyCode == KeyCode.DELETE) {
         e.consume(); // Need this so as not to remove focus which is default key action
         config.gui.remote_gui.deleted_tab.permDelete.fire();
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
   
   private void TABLERowSelected(Tabentry entry) {
      sortableDate s = entry.getDELETED();
      // Get column items for selected row 
      try {
         // Non folder entry so print single entry info
         sortableDuration dur = entry.getDUR();
         String message = TableUtil.makeShowSummary(s, dur);
         String title = "\nDeleted: ";
         if (s.json.has("title"))
            title += s.json.getString("title");
         if (s.json.has("subtitle"))
            title += " - " + s.json.getString("subtitle");
         log.warn(title);
         log.print(message);

         if (config.gui.show_details.isShowing())
            config.gui.show_details.update(TABLE, currentTivo, s.json);
      } catch (JSONException e) {
         log.error("TABLERowSelected - " + e.getMessage());
         return;
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
         TABLE.sort();
         TableUtil.autoSizeTableViewColumns(TABLE, true);
         tivo_data.put(tivoName, data);
         currentTivo = tivoName;
         if (config.gui.remote_gui != null) {
            config.gui.remote_gui.setTivoName("deleted", tivoName);
            refreshNumber();
         }
      } catch (JSONException e) {
         log.print("Deleted AddRows - " + e.getMessage());
      }      
   }
   
   // Refresh table with given given entries
   public void Refresh(Stack<JSONObject> o) {
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
      clear();
      for (int i=0; i<o.size(); ++i) {
         AddTABLERow(o.get(i));
      }
   }
   
   // Add a non folder entry to TABLE table
   public void AddTABLERow(JSONObject entry) {
      TABLE.getItems().add(new Tabentry(entry));
   }   
   
   // Refresh the # SHOWS label in the ToDo tab
   private void refreshNumber() {
      Platform.runLater(new Runnable() {
         @Override public void run() {
            config.gui.remote_gui.deleted_tab.label.setText("" + tivo_data.get(currentTivo).length() + " SHOWS");
         }
      });
   }
      
   // Undelete selected recordings
   public void recoverSingle(final String tivoName) {
      // Get selection set ordered highest to lowest
      final Integer[] sorted_final = TableUtil.highToLow(TableUtil.GetSelectedRows(TABLE));
      if (sorted_final.length == 0)
         return;
      log.print("Recovering individual recordings on TiVo: " + tivoName);
      Task<Void> task = new Task<Void>() {
         @Override public Void call() {
            Remote r = config.initRemote(tivoName);
            if (r.success) {
               for (final int row : sorted_final) {
                  try {
                     JSONObject json = GetRowData(row);
                     final String title = json.getString("title");
                     if (json != null) {
                        JSONObject o = new JSONObject();
                        JSONArray a = new JSONArray();
                        a.put(json.getString("recordingId"));
                        o.put("recordingId", a);
                        final JSONObject result = r.Command("Undelete", o);
                        Platform.runLater(new Runnable() {
                           @Override
                           public void run() {
                              if (result == null) {
                                 TABLE.getSelectionModel().clearSelection(row);
                                 log.error("Failed to recover recording: '" + title + "'");
                              } else {
                                 log.warn("Recovered recording: '" + title + "' on TiVo: " + tivoName);
                                 TABLE.getItems().remove(row);
                                 tivo_data.get(currentTivo).remove(row);
                                 refreshNumber();
                              }
                           }
                        });
                     }
                  } catch (JSONException e) {
                     log.error("recoverSingle failed - " + e.getMessage());
                  }
               }
               r.disconnect();
            }
            return null;
         }
      };
      new Thread(task).start();
   }
   
   // Permanently delete selected recordings
   public void permanentlyDelete(final String tivoName) {
      // Get selection set ordered highest to lowest
      final Integer[] sorted_final = TableUtil.highToLow(TableUtil.GetSelectedRows(TABLE));
      if (sorted_final.length == 0)
         return;
      log.print("Permanently deleting individual recordings on TiVo: " + tivoName);
      Task<Void> task = new Task<Void>() {
         @Override public Void call() {
            JSONObject json;
            Remote r = config.initRemote(tivoName);
            if (r.success) {
               for (final int row : sorted_final) {
                  try {
                     json = GetRowData(row);
                     if (json != null) {
                        String title = json.getString("title");
                        if (json.has("subtitle"))
                           title += " - " + json.getString("subtitle");
                        final String title_final = title;
                        JSONObject o = new JSONObject();
                        JSONArray a = new JSONArray();
                        a.put(json.getString("recordingId"));
                        o.put("recordingId", a);
                        final JSONObject result = r.Command("PermanentlyDelete", o);
                        Platform.runLater(new Runnable() {
                           @Override
                           public void run() {
                              if (result == null) {
                                 TABLE.getSelectionModel().clearSelection(row);
                                 log.error("Failed to permanently delete recording: '" + title_final + "'");
                              } else {
                                 log.warn("Permanently deleted recording: '" + title_final + "' on TiVo: " + tivoName);
                                 TABLE.getItems().remove(row);
                                 tivo_data.get(currentTivo).remove(row);
                                 refreshNumber();
                              }
                           }
                        });
                     }
                  } catch (JSONException e) {
                     log.error("permanentlyDelete failed - " + e.getMessage());
                  }
               }
               r.disconnect();
            }
            return null;
         }
      };
      new Thread(task).start();
   }
}
