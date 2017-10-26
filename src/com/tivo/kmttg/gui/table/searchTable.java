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

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SortEvent;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.cell.TreeItemPropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Callback;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.TableMap;
import com.tivo.kmttg.gui.gui;
import com.tivo.kmttg.gui.comparator.DateComparator;
import com.tivo.kmttg.gui.comparator.DurationComparator;
import com.tivo.kmttg.gui.comparator.StringChannelComparator;
import com.tivo.kmttg.gui.comparator.StringShowComparator;
import com.tivo.kmttg.gui.sortable.sortableDate;
import com.tivo.kmttg.gui.sortable.sortableDuration;
import com.tivo.kmttg.main.auto;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.id;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class searchTable extends TableMap {
   private String currentTivo = null;
   public TreeTableView<Tabentry> TABLE = null;
   public String[] TITLE_cols = {"", "TYPE", "SHOW", "DATE", "CHANNEL", "DUR"};
   private double[] weights = {7, 12, 40, 20, 15, 6};
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
      TABLE.getRoot().getChildren().clear();
   }
   @Override
   public TreeTableView<?> getTreeTable() {
      return TABLE;
   }
         
   public searchTable() {      
      TABLE = new TreeTableView<Tabentry>();
      TABLE.setRoot(new TreeItem<>(new Tabentry("")));
      TABLE.setShowRoot(false); // Don't show the empty root node
      TABLE.getSelectionModel().setSelectionMode(SelectionMode.SINGLE); // Allow multiple row selection
      TABLE.setRowFactory(new ColorRowFactory()); // For row background color handling
      // Special sort listener to set sort order to descending date when no sort is selected
      TABLE.getSortOrder().addListener(new ListChangeListener<TreeTableColumn<Tabentry, ?>>() {
         @Override
         public void onChanged(Change<? extends TreeTableColumn<Tabentry, ?>> change) {
            change.next();
            if (change != null && change.toString().contains("removed")) {
               if (change.getRemoved().get(0).getText().equals("DATE"))
                  return;
               int date_col = TableUtil.getColumnIndex(TABLE, "DATE");
               TABLE.getSortOrder().setAll(Collections.singletonList(TABLE.getColumns().get(date_col)));
               TABLE.getColumns().get(date_col).setSortType(TreeTableColumn.SortType.DESCENDING);
            }
         }
      });
      
      // Keep selection visible following sort event
      TABLE.setOnSort(new EventHandler<SortEvent<TreeTableView<Tabentry>>>() {
         @Override public void handle(SortEvent<TreeTableView<Tabentry>> event) {
            Platform.runLater(new Runnable() {
               @Override public void run() {
                  // If there's a table selection make sure it's visible
                  TableUtil.selectedVisible(TABLE);
               }
            });
         }
      });
      
      for (String colName : TITLE_cols) {
         if (colName.length() == 0)
            colName = "IMAGE";
         if (colName.equals("IMAGE")) {
            TreeTableColumn<Tabentry,ImageView> col = new TreeTableColumn<Tabentry,ImageView>("");
            col.setCellValueFactory(new TreeItemPropertyValueFactory<Tabentry,ImageView>(colName));
            col.setCellFactory(new ImageCellFactory());
            TABLE.getColumns().add(col);
         } else if (colName.equals("DATE")) {
            TreeTableColumn<Tabentry,sortableDate> col = new TreeTableColumn<Tabentry,sortableDate>(colName);
            col.setCellValueFactory(new TreeItemPropertyValueFactory<Tabentry,sortableDate>(colName));
            col.setComparator(new DateComparator());
            col.setStyle("-fx-alignment: CENTER-RIGHT;");
            TABLE.getColumns().add(col);
         } else if (colName.equals("DUR")) {
            TreeTableColumn<Tabentry,sortableDuration> col = new TreeTableColumn<Tabentry,sortableDuration>(colName);
            col.setCellValueFactory(new TreeItemPropertyValueFactory<Tabentry,sortableDuration>(colName));
            col.setComparator(new DurationComparator());
            col.setStyle("-fx-alignment: CENTER;");
            TABLE.getColumns().add(col);
         } else {
            // Regular String sort
            TreeTableColumn<Tabentry,String> col = new TreeTableColumn<Tabentry,String>(colName);
            col.setCellValueFactory(new TreeItemPropertyValueFactory<Tabentry,String>(colName));
            if (colName.equals("CHANNEL"))
               col.setComparator(new StringChannelComparator()); // Custom column sort
            if (colName.equals("SHOW"))
               col.setComparator(new StringShowComparator()); // Custom column sort strips off leading price
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
      TABLE.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<TreeItem<Tabentry>>() {
         @Override
         public void changed(ObservableValue<? extends TreeItem<Tabentry>> obs, TreeItem<Tabentry> oldSelection, TreeItem<Tabentry> newSelection) {
            if (newSelection != null) {
               TABLERowSelected(newSelection.getValue());
            }
         }
      });
                        
      // Add right mouse button handler
      TableUtil.AddRightMouseListener(TABLE);
   }   

   // ColorRowFactory for setting row background color
   private class ColorRowFactory implements Callback<TreeTableView<Tabentry>, TreeTableRow<Tabentry>> {
      public TreeTableRow<Tabentry> call(TreeTableView<Tabentry> tableView) {
         TreeTableRow<Tabentry> row = new TreeTableRow<Tabentry>() {
            @Override
            public void updateItem(Tabentry entry, boolean empty) {
               super.updateItem(entry,  empty);
               styleProperty().unbind(); setStyle("");
               if (entry != null) {
                  // Mark rows that are already in To Do
                  JSONObject json = entry.getDATE().json;
                  if (json != null && json.has("__inTodo__"))
                     TableUtil.setRowColor(this, config.tableBkgndProtected);
                  
                  // Mark rows with entries in auto history file
                  if (config.showHistoryInTable == 1) {
                     try {
                        if (json.has("partnerContentId")) {
                           String programId = json.getString("partnerContentId");
                           programId = programId.replaceFirst("^.+\\.", "");
                           if (auto.keywordMatchHistoryFast(programId, false))
                              TableUtil.setRowColor(this, config.tableBkgndInHistory);
                        }
                        else if (json.has("partnerCollectionId")) {
                           String programId = json.getString("partnerCollectionId");
                           if (auto.keywordMatchHistoryFast(programId, false))
                              TableUtil.setRowColor(this, config.tableBkgndInHistory);                           
                        }
                     } catch (JSONException e) {
                        log.error("searchTable ColorRowFactory - " + e.getMessage());
                     }
                  }
               }
            }
         };
         return row;
      }
   }   

   private class ImageCellFactory implements Callback<TreeTableColumn<Tabentry, ImageView>, TreeTableCell<Tabentry, ImageView>> {
      public TreeTableCell<Tabentry, ImageView> call(TreeTableColumn<Tabentry, ImageView> param) {
         TreeTableCell<Tabentry, ImageView> cell = new TreeTableCell<Tabentry, ImageView>() {
            @Override
            public void updateItem(final ImageView item, boolean empty) {
               super.updateItem(item, empty);
               if (empty) {
                  setGraphic(null);
               } else {
                  if (item != null)
                     setGraphic(item);
               }
            }
         };
         return cell;
      }
   }
   
   public static class Tabentry {
      public ImageView image = new ImageView();
      public String type = "";
      public String title = "";
      public sortableDate date = null;
      public String channel = "";
      public sortableDuration duration = null;
      
      // Root node constructor
      public Tabentry(String s) {
         // Do nothing
      }

      public Tabentry(JSONObject entry, Boolean isFolder) {
         try {
            if (isFolder && entry.getJSONArray("entries").length() > 1) {
               // Multiple items => display as folder
               int num = entry.getJSONArray("entries").length();
               JSONArray entries = entry.getJSONArray("entries");
               String chan = "";
               if (entries.getJSONObject(0).has("channel"))
                  chan = TableUtil.makeChannelName(entries.getJSONObject(0));
               else if (entry.has("partnerId"))
                  chan = TableUtil.getPartnerName(entries.getJSONObject(0));
               Boolean sameChannel = true;
               for (int i=1; i<num; ++i) {
                  String fchan = "";
                  JSONObject j = entries.getJSONObject(i);
                  if (j.has("channel"))
                     fchan = TableUtil.makeChannelName(j);
                  else if (j.has("partnerId"))
                     fchan = TableUtil.getPartnerName(j);
                  if (! fchan.equals(chan))
                     sameChannel = false;
               }
               if (sameChannel)
                  channel = chan;
               else
                  channel = "<various>";
               image = new ImageView(gui.Images.get("folder"));
               type = entry.getString("type");
               title = " " + entry.getString("title") + " (" + num + ")";
               String startString = "";
               long start = -1;
               if (entry.getJSONArray("entries").getJSONObject(0).has("startTime")) {
                  startString = entry.getJSONArray("entries").getJSONObject(0).getString("startTime");
                  start = TableUtil.getLongDateFromString(startString);
               }
               else if (entry.getJSONArray("entries").getJSONObject(0).has("releaseDate")) {
                  startString = entry.getJSONArray("entries").getJSONObject(0).getString("releaseDate");
                  start = TableUtil.getLongDateFromString(startString);
               }
               date = new sortableDate(entry.getString("title"), entry, start);
               if (entry.getJSONArray("entries").getJSONObject(0).has("partnerId")) {
                  channel = "STREAMING";
               }
            } else {
               // Single item => don't display as folder
               if (entry.has("entries"))
                  entry = entry.getJSONArray("entries").getJSONObject(0);
               
               // If entry is in 1 of todo lists then add special __inTodo__ JSON entry
               config.gui.remote_gui.flagIfInTodo(entry, false);
               long start = -1;
               if (entry.has("startTime")) {
                  start = TableUtil.getLongDateFromString(entry.getString("startTime"));
               }
               else if (entry.has("releaseDate")) {
                  start = TableUtil.getLongDateFromString(entry.getString("releaseDate"));
               }
               long dur = 0;
               if (entry.has("duration"))
                  dur = entry.getLong("duration")*1000;
               type = " ";
               if (entry.has("collectionType")) {
                  type = entry.getString("collectionType");
               }
               title = TableUtil.makeShowTitle(entry);
               if (entry.has("partnerId")) {
                  if (entry.has("hdtv") && entry.getBoolean("hdtv"))
                  title += " [HD]";
                  if (entry.has("price")) {
                     String price = entry.getString("price");
                     if (price.equals("USD.0"))
                        price = "free";
                     price = price.replaceFirst("USD\\.", "");
                     if (price.matches("^[0-9]+"))
                        price = String.format("$%.2f", Float.parseFloat(price)/100.0);
                     title = "(" + price + ") " + title;
                  }
               }
               channel = "";
               if (entry.has("channel"))
                  channel = TableUtil.makeChannelName(entry);
               else if (entry.has("partnerId"))
                  channel = TableUtil.getPartnerName(entry);
               
               date = new sortableDate(entry, start);
               duration = new sortableDuration(dur, false);
            } // else
         } catch (JSONException e1) {
            log.error("searchTable Tabentry - " + e1.getMessage());
         }
      }
      
      public ImageView getIMAGE() {
         return image;
      }
      
      public String getTYPE() {
         return type;
      }
      
      public String getSHOW() {
         return title;
      }

      public sortableDate getDATE() {
         return date;
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
      if (row < 0) return null;
      return TABLE.getTreeItem(row).getValue().getDATE().json;
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
            String title = "\nSearch: ";
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
   }

   // Update table to display given entries
   // data is a JSONArray of JSON objects each of following structure:
   // String    title
   // String    type
   // JSONArray entries
   public void AddRows(String tivoName, JSONArray data) {
      Refresh(data);
      TABLE.sort();
      TableUtil.autoSizeTableViewColumns(TABLE, true);
      
      // Save the data
      currentTivo = tivoName;
      tivo_data.put(tivoName, data);
      
      // Update search tab to show this tivoName
      if (config.gui.remote_gui != null)
         config.gui.remote_gui.setTivoName("search", tivoName);
   }
   
   public void Refresh(JSONArray data) {
      if (data == null) {
         if (currentTivo != null)
            AddRows(currentTivo, tivo_data.get(currentTivo));
         return;
      }
      if (TABLE != null) {
         // Top level folder structure
         clear();
         for (int i=0; i<data.length(); ++i) {
            try {
               AddTABLERow(data.getJSONObject(i));
            } catch (JSONException e) {
               log.error("Refresh - " + e.getMessage());
            }
         }
         
         TABLE.getRoot().setExpanded(true);
      }
   }
      
   // Add a row to table
   public void AddTABLERow(JSONObject entry) {
      try {
         Boolean folder = false;
         JSONArray entries = entry.getJSONArray("entries");
         if (entries.length() > 1)
            folder = true;
         if (folder) {
            TreeItem<Tabentry> item = new TreeItem<>( new Tabentry(entry, true) );
            // Want to resize columns whenever a tree is expanded or collapsed
            item.expandedProperty().addListener(new ChangeListener<Boolean>() {
               @Override
               public void changed(ObservableValue<? extends Boolean> arg0, Boolean arg1, Boolean arg2) {
                  TableUtil.autoSizeTableViewColumns(TABLE, true);
               }         
            });
            for (int i=0; i<entries.length(); ++i) {
               TreeItem<Tabentry> subitem = new TreeItem<>(new Tabentry(entries.getJSONObject(i), false));
               item.getChildren().add(subitem);
            }
            TABLE.getRoot().getChildren().add(item); 
         } else {
            TreeItem<Tabentry> item = new TreeItem<>( new Tabentry(entry, false) );
            TABLE.getRoot().getChildren().add(item);
         }
      } catch (JSONException e) {
         log.error("searchTable AddTABLERow - " + e.getMessage());
      }
   }  
      
   private Boolean isFolder(int row) {
      if (row < 0) return false;
      sortableDate s = TABLE.getTreeItem(row).getValue().getDATE();
      return s.folder;
   }

   // Look for entry with given folder name and select it
   // (This used when returning back from folder mode to top level mode)
   public void SelectFolder(String folderName) {
      debug.print("folderName=" + folderName);
      for (int i=0; i<TABLE.getRoot().getChildren().size(); ++i) {
         sortableDate s = TABLE.getRoot().getChildren().get(i).getValue().getDATE();
         if (s.folder) {
            if (s.folderName.equals(folderName)) {
               TABLE.getSelectionModel().clearSelection();
               TABLE.getSelectionModel().select(i);
               TableUtil.scrollToCenter(TABLE, i);
               return;
            }
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
      else if (keyCode == KeyCode.N) {
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         TableUtil.PrintEpisodes(GetRowData(selected[0]));
      }
      else if (keyCode == KeyCode.A) {
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         JSONObject json = GetRowData(selected[0]);
         if (json != null)
            auto.AddHistoryEntry(json);
      }
      else if (keyCode == KeyCode.K) {
         // Print skipmode information if available
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         JSONObject json = GetRowData(selected[0]);
         TableUtil.PrintClipData(currentTivo, json);
      }
      else if (keyCode == KeyCode.P) {
         config.gui.remote_gui.search_tab.recordSP.fire();
      }
      else if (keyCode == KeyCode.R) {
       config.gui.remote_gui.search_tab.record.fire();
      }
      else if (keyCode == KeyCode.W) {
       config.gui.remote_gui.search_tab.wishlist.fire();
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
      } else if (keyCode == KeyCode.T) {
         TableUtil.toggleTreeState(TABLE);
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
            if ( isFolder(row) )
               continue;
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
         if ( isFolder(row) )
            return;
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
                     if ( isFolder(row) )
                        continue;
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
