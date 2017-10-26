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

import java.util.Comparator;
import java.util.Hashtable;
import java.util.Stack;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellEditEvent;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONFile;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.PopupHandler;
import com.tivo.kmttg.gui.TableMap;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.id;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class thumbsTable extends TableMap {
   private String currentTivo = null;
   public TableView<Tabentry> TABLE = null;
   public String[] TITLE_cols = {"TYPE", "SHOW", "RATING"};
   private double[] weights = {15, 75, 10};
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
   
   public thumbsTable() {
      TABLE = new TableView<Tabentry>();
      TABLE.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE); // Allow multi row selection
      TABLE.setEditable(true); // Allow editing
      
      for (String colName : TITLE_cols) {
         if (colName.equals("TYPE")) {
            TableColumn<Tabentry,String> col = new TableColumn<Tabentry,String>(colName);
            col.setCellValueFactory(new PropertyValueFactory<Tabentry,String>(colName));
            TABLE.getColumns().add(col);
         } else if (colName.equals("SHOW")) {
            TableColumn<Tabentry,jsonString> col = new TableColumn<Tabentry,jsonString>(colName);
            col.setCellValueFactory(new PropertyValueFactory<Tabentry,jsonString>(colName));
            TABLE.getColumns().add(col);
         } else if (colName.equals("RATING")) {
            TableColumn<Tabentry,String> col = new TableColumn<Tabentry,String>(colName);
            col.setCellValueFactory(new PropertyValueFactory<Tabentry,String>(colName));
            col.setComparator(new RatingComparator()); // Custom column sort
            col.setStyle("-fx-alignment: CENTER;");
            col.setCellFactory(TextFieldTableCell.<Tabentry>forTableColumn());
            // This column is editable
            col.setOnEditCommit( new EventHandler<CellEditEvent<Tabentry, String>>() {
               @Override
               public void handle(CellEditEvent<Tabentry, String> event) {
                  int row = event.getTablePosition().getRow();
                  Tabentry entry = event.getTableView().getItems().get(row);
                  int val = 1;
                  try {
                     val = Integer.parseInt(event.getNewValue());
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
                  // Update row Tabentry value
                  entry.rating = "" + val;
                  TABLE.getItems().set(event.getTablePosition().getRow(), entry);
               }
            });
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
      
      // Mouse listener for single click in RATING column
      TABLE.setOnMousePressed(new EventHandler<MouseEvent>() {
         @Override 
         public void handle(MouseEvent event) {
            // Pass along right mouse button click
            if (event.isSecondaryButtonDown())
               PopupHandler.display(TABLE, event);
            else {
               // Trigger edit for single click in RATING cell
               if (event.getClickCount() == 1 && event.getTarget().getClass() == TextFieldTableCell.class) {
                  @SuppressWarnings("unchecked")
                  TablePosition<Tabentry,?> pos = TABLE.getSelectionModel().getSelectedCells().get(0);
                  TABLE.edit(pos.getRow(), pos.getTableColumn());
               }
            }
         }
      });
   }
   
   public static class jsonString {
      String display;
      JSONObject json;
      public jsonString(JSONObject json, String title) {
         this.display = title;
         this.json = json;
      }
      public String toString() {
         return display;
      }
   }
   
   private class RatingComparator implements Comparator<String> {
      public int compare(String s1, String s2) {
         Integer i1 = Integer.parseInt(s1);
         Integer i2 = Integer.parseInt(s2);
         if (i1 > i2) return 1;
         if (i1 < i2) return -1;
         return 0;         
      }
   }
   
   public static class Tabentry {
      public String type = "";
      public jsonString show = null;
      public String rating = "0";

      public Tabentry(JSONObject entry) {
         try {
            if (entry.has("collectionType"))
               type = entry.getString("collectionType");
            if (entry.has("title"))
               show = new jsonString(entry, entry.getString("title"));
            if (entry.has("thumbsRating"))
               rating = "" + entry.getInt("thumbsRating");                    
         } catch (JSONException e1) {
            log.error("thumbsTable Tabentry - " + e1.getMessage());
         }      
      }
      
      public String getTYPE() {
         return type;
      }
      
      public jsonString getSHOW() {
         return show;
      }

      public String getRATING() {
         return rating;
      }

      public String toString() {
         return show.toString();
      }      
   }

   public JSONObject GetRowData(int row) {
      return TABLE.getItems().get(row).getSHOW().json;
   }
   
   public String GetValueAt(int row, int col) {
      return TABLE.getColumns().get(col).getCellData(row).toString();
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
      }
      else if (keyCode == KeyCode.N) {
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         TableUtil.PrintEpisodes(GetRowData(selected[0]));
      }
      else if (keyCode == KeyCode.C) {
         config.gui.remote_gui.thumbs_tab.copy.fire();
      }
      else if (keyCode == KeyCode.Q) {
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
   
   // Update table display to show top level flat structure
   private void displayFlatStructure(Stack<JSONObject> o) {
      for (int i=0; i<o.size(); ++i) {
         AddTABLERow(o.get(i));
      }
   }
   
   private void updateShowRows(String prefix) {
      for (int row=0; row<TABLE.getItems().size(); ++row) {
         Tabentry e = TABLE.getItems().get(row);
         e.show.display = prefix + e.show.display;
      }
   }
   
   // Add a non folder entry to TABLE table
   public void AddTABLERow(JSONObject entry) {
      debug.print("entry=" + entry);
      TABLE.getItems().add(new Tabentry(entry));
   }   
   
   // Refresh the # SHOWS label in the ToDo tab
   private void refreshNumber() {
      config.gui.remote_gui.thumbs_tab.label.setText("" + tivo_data.get(currentTivo).length() + " THUMBS");
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
   public void updateThumbs(final String tivoName) {
      if (isTableLoaded()) {
         log.error("Cannot update a loaded table");
         return;
      }
      try {
         JSONArray changed = new JSONArray();
         for (int row=0; row<TABLE.getItems().size(); ++row) {
            String table_value = GetValueAt(row, TableUtil.getColumnIndex(TABLE, "RATING"));
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
            class backgroundRun extends Task<Void> {
               JSONArray changed;
               public backgroundRun(JSONArray changed) {
                  this.changed = changed;
               }
               @Override
               protected Void call() {
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
                     log.error("updateThumbs (1) - " + e.getMessage());
                  }
                  // Now refresh the thumbs table
                  Platform.runLater(new Runnable() {
                     @Override public void run() {
                        refreshThumbs(tivoName);
                     }
                  });
                  return null;
               }
            }
            backgroundRun b = new backgroundRun(changed);
            new Thread(b).start();
         }
      } catch (Exception e) {
         log.error("updateThumbs (2) - " + e.getMessage());
      }
   }
   
   public void saveThumbs(String tivoName, String file) {
      if (isTableLoaded()) {
         log.error("Cannot save a loaded table");
         return;
      }
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
         updateShowRows(loadedPrefix);
         TableUtil.autoSizeTableViewColumns(TABLE, true);
         setLoaded(true);
      }
   }
   
   public void copyThumbs(final String tivoName) {
      Task<Void> task = new Task<Void>() {
         @Override public Void call() {
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
      };
      new Thread(task).start();
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
      if (TABLE.getItems().size() > 0) {
         int col = TableUtil.getColumnIndex(TABLE, "SHOW");
         String title = GetValueAt(0,col);
         if (title != null && title.startsWith(loadedPrefix))
            setLoaded(true);
         else
            setLoaded(false);
      }
   }   
}
