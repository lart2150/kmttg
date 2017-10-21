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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.UnsupportedEncodingException;
//import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;
import java.util.Stack;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.stage.FileChooser.ExtensionFilter;

//import javafx.scene.control.skin.NestedTableColumnHeader;
//import javafx.scene.control.skin.TableColumnHeader;
//import javafx.scene.control.skin.TableHeaderRow;
//import javafx.scene.control.skin.TableViewSkin;
//import javafx.scene.control.skin.TreeTableViewSkin;
import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.PopupHandler;
import com.tivo.kmttg.gui.TableMap;
import com.tivo.kmttg.gui.gui;
import com.tivo.kmttg.gui.help;
import com.tivo.kmttg.gui.remote.util;
import com.tivo.kmttg.gui.sortable.sortableDate;
import com.tivo.kmttg.gui.sortable.sortableDuration;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.tivoFileName;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.id;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class TableUtil {
   private static Stage searchDialog = null;
   private static TextField searchField = null;
   private static Button find = null;
   private static Stage thumbsDialog = null;
   private static ChoiceBox<String> thumbsChoice = null;
   private static double search_x = -1;
   private static double search_y = -1;
   
   public static String getColumnName(TableView<?> TABLE, int c) {
      return TABLE.getColumns().get(c).getText();
   }
   
   public static int getColumnIndex(TableView<?> TABLE, String name) {
      String cname;
      for (int i=0; i<TABLE.getColumns().size(); i++) {
         cname = TABLE.getColumns().get(i).getText();
         if (cname.equals(name)) return i;
      }
      return -1;
   }
   
   public static int getColumnIndex(TreeTableView<?> TABLE, String name) {
      String cname;
      for (int i=0; i<TABLE.getColumns().size(); i++) {
         cname = (String)TABLE.getColumns().get(i).getText();
         if (cname.equals(name)) return i;
      }
      return -1;
   }
   
   public static void setRowColor(TableRow<?> row, Color color) {
      row.styleProperty().bind(
         Bindings.when(row.selectedProperty())
            .then("")
            .otherwise("-fx-opacity: 0.65; -fx-background-color: " + config.gui.getWebColor(color))
       );
   }
   
   public static void setRowColor(TreeTableRow<?> row, Color color) {
      row.styleProperty().bind(
         Bindings.when(row.selectedProperty())
            .then("")
            .otherwise("-fx-opacity: 0.65; -fx-background-color: " + config.gui.getWebColor(color))
       );
   }
   
   public static int[] GetSelectedRows(TableView<?> TABLE) {
      debug.print("");
      // NOTE: getSelectionModel.getSelectedIndices() is buggy, so avoid using it
      Stack<Integer> stack = new Stack<Integer>();
      for (int i=0; i<TABLE.getItems().size(); ++i) {
         if (TABLE.getSelectionModel().isSelected(i))
            stack.push(i);
      }
      int[] rows = new int[stack.size()];
      int count = 0;
      for (int row : stack)
         rows[count++] = row;
      return rows;
   }
   
   public static int[] GetSelectedRows(TreeTableView<?> TABLE) {
      debug.print("");
      // NOTE: getSelectionModel.getSelectedIndices() is buggy, so avoid using it
      Stack<Integer> stack = new Stack<Integer>();
      for (int i=0; i<TABLE.getExpandedItemCount(); ++i) {
         if (TABLE.getSelectionModel().isSelected(i))
            stack.push(i);
      }
      int[] rows = new int[stack.size()];
      int count = 0;
      for (int row : stack)
         rows[count++] = row;
      return rows;
   }
   
   // Toggle between fully expanded and fully collapsed tree states
   public static void toggleTreeState(TreeTableView<?> TABLE) {
      Boolean fullyExpanded = true;
      for (TreeItem<?> item : TABLE.getRoot().getChildren()) {
         if (item.getChildren().size() > 0 && ! item.isExpanded())
            fullyExpanded = false;
      }
      for (TreeItem<?> item : TABLE.getRoot().getChildren()) {
         item.setExpanded(! fullyExpanded);
      }
   }
   
   public static Integer[] highToLow(int[] unsorted) {
      Integer[] sorted = new Integer[unsorted.length];
      int i=0;
      for (int selected : unsorted)
         sorted[i++] = selected;
      Arrays.sort(sorted, Collections.reverseOrder());
      return sorted;
   }
   
   // Make any selected TABLE row visible in viewport
   public static void selectedVisible(TableView<?> TABLE) {
      Integer[] selected = highToLow(GetSelectedRows(TABLE));     
      if (selected != null && selected.length > 0)
         scrollToCenter(TABLE, selected[0]);

   }
   
   // Make any selected TABLE row visible in viewport
   public static void selectedVisible(TreeTableView<?> TABLE) {
      Integer[] selected = highToLow(GetSelectedRows(TABLE));     
      if (selected != null && selected.length > 0)
         scrollToCenter(TABLE, selected[0]);

   }
   
   public static void DeselectRow(TableView<?> TABLE, int row) {
      TABLE.getSelectionModel().clearSelection(row);
   }
   
   public static void clear(TableView<?> TABLE) {
      debug.print("");
      TABLE.getItems().clear();
   }
      
   public static void RemoveRow(TableView<?> table, int row) {
      table.getItems().remove(row);
   }
   
   public static void scrollToCenter(final TableView<?> table, int rowIndex) {
      table.scrollTo(rowIndex);
   }
   
   public static void scrollToCenter(final TreeTableView<?> table, int rowIndex) {
      table.scrollTo(rowIndex);
   }
   
   public static void setWeights(TableView<?> TABLE, String[] names, double[] weights, Boolean force) {
      // Only do this for Java 9 or later for now
      //if (System.getProperty("java.version").startsWith("1"))
      //   return;
      if (!force && config.tableColAutoSize == 0)
         return;
      TABLE.setColumnResizePolicy( TableView.UNCONSTRAINED_RESIZE_POLICY );
      int c = 0;
      double sum = 0;
      Hashtable<String,Double> h = new Hashtable<String,Double>();
      for (double weight : weights) {
         h.put(names[c++], weight);
         sum += weight;
         sum *= 1.01;
      }
      for (int i=0; i<TABLE.getColumns().size(); i++) {
         TableColumn<?,?> col = TABLE.getColumns().get(i);
         String cname = (String)col.getText();
         col.prefWidthProperty().bind(TABLE.widthProperty().multiply(h.get(cname)/sum));
      }
   }
   
   public static void setWeights(TreeTableView<?> TABLE, String[] names, double[] weights, Boolean force) {
      // Only do this for Java 9 or later for now
      //if (System.getProperty("java.version").startsWith("1"))
      //   return;
      if (!force && config.tableColAutoSize == 0)
         return;
      TABLE.setColumnResizePolicy( TreeTableView.UNCONSTRAINED_RESIZE_POLICY );
      int c = 0;
      double sum = 0;
      Hashtable<String,Double> h = new Hashtable<String,Double>();
      for (double weight : weights) {
         h.put(names[c++], weight);
         sum += weight;
         sum *= 1.01;
      }
      for (int i=0; i<TABLE.getColumns().size(); i++) {
         TreeTableColumn<?,?> col = TABLE.getColumns().get(i);
         String cname = (String)col.getText();
         col.prefWidthProperty().bind(TABLE.widthProperty().multiply(h.get(cname)/sum));
      }
   }
   
   // Call protected method to do tableview column fit to size
   public static void autoSizeTableViewColumns(final TableView<?> tableView, Boolean force) {
      debug.print("tableView=" + tableView + " force=" + force);
      //if (! System.getProperty("java.version").startsWith("1")) {
         // Java 9 doesn't work with custom code so default to built in
         if (!force && config.tableColAutoSize == 0)
            return;
         //tableView.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
      /*} else {
         int maxRows = 500;
         if (tableView == null)
            return;
         if (!force && config.tableColAutoSize == 0)
            return;
         TableViewSkin<?> skin = (TableViewSkin<?>) tableView.getSkin();
         if (skin == null)
            return;
         TableHeaderRow headerRow = skin.getTableHeaderRow();
         NestedTableColumnHeader rootHeader = headerRow.getRootHeader();
         for (TableColumnHeader columnHeader : rootHeader.getColumnHeaders()) {
            try {
               TableColumn<?, ?> column = (TableColumn<?, ?>) columnHeader.getTableColumn();
               if (column != null) {
                  Method method = skin.getClass().getDeclaredMethod("resizeColumnToFitContent", TableColumn.class, int.class);
                  method.setAccessible(true);
                  method.invoke(skin,column,maxRows);
               }
            } catch (Throwable e) {
               e = e.getCause();
               e.printStackTrace(System.err);
            }
         }
      }*/
   }
   
   // Call protected method to do treetableview column fit to size
   // NOTE: Added min setting for IMAGE column (which has empty title)
   public static void autoSizeTableViewColumns(final TreeTableView<?> tableView, Boolean force) {
      debug.print("tableView=" + tableView + " force=" + force);
      //if (! System.getProperty("java.version").startsWith("1")) {
         // Java 9 doesn't work with custom code so default to built in
         if (!force && config.tableColAutoSize == 0)
            return;
         //tableView.setColumnResizePolicy(TreeTableView.UNCONSTRAINED_RESIZE_POLICY);
      /*} else {
         double minImageColWidth = 60;
         int maxRows = 500;
         if (tableView == null)
            return;
         if (!force && config.tableColAutoSize == 0)
            return;
         TreeTableViewSkin<?> skin = (TreeTableViewSkin<?>) tableView.getSkin();
         if (skin == null)
            return;
         TableHeaderRow headerRow = skin.getTableHeaderRow();
         NestedTableColumnHeader rootHeader = headerRow.getRootHeader();
         for (TableColumnHeader columnHeader : rootHeader.getColumnHeaders()) {
            try {
               TreeTableColumn<?, ?> column = (TreeTableColumn<?, ?>) columnHeader.getTableColumn();
               if (column != null) {
                  Method method = skin.getClass().getDeclaredMethod("resizeColumnToFitContent", TreeTableColumn.class, int.class);
                  method.setAccessible(true);
                  method.invoke(skin,column,maxRows);
                  if (columnHeader != null && columnHeader.getTableColumn().getText().equals("")) {
                     // IMAGE column - want min width to be honored
                     column.setMinWidth(minImageColWidth);
                  }
               }
            } catch (Throwable e) {
               e = e.getCause();
               e.printStackTrace(System.err);
            }
         }
      }*/
   }
   
   // Bring up a dialog to allow searching SHOW column of given table
   public static void SearchGUI() {
      if (searchDialog == null) {
         // Dialog not created yet, so do so
         HBox panel = new HBox();
         panel.setSpacing(5);
         find = new Button("FIND");
         find.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
               String text = searchField.getText();
               if (text.length() > 0) {
                  // Issue warning and return for irrelevant tabs/tables
                  String irrelevant = "Currently selected tab doesn't contain a suitable table to search.";
                  String tabName = config.gui.getCurrentTabName();
                  if (tabName.equals("FILES") || tabName.equals("Slingbox")) {
                     log.warn(irrelevant);
                     return;
                  }
                  if (tabName.equals("Remote")) {
                     TableMap tmap = TableMap.getCurrent();
                     String colName = "SHOW";
                     if (config.gui.remote_gui.getCurrentTabName().equals("Streaming"))
                        colName = "ITEM";
                     if (tmap != null) {
                        if (tmap.getTable() != null)
                           Search(tmap.getTable(), text, colName);
                        if (tmap.getTreeTable() != null)
                           Search(tmap.getTreeTable(), text, colName);
                     }
                  } else {
                     Search(config.gui.getTab(tabName).getTable().NowPlaying, text, "SHOW");
                  }
               }
            }
         });
         searchField = new TextField();
         searchField.setPrefWidth(150);
         searchField.setOnKeyPressed(new EventHandler<KeyEvent>() {
             @Override
             public void handle(KeyEvent e) {
                 if (e.getCode().equals(KeyCode.ENTER)) {
                     find.fire();
                 }
             }
         });
         Button close = new Button("CLOSE");
         close.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
               search_x = searchDialog.getX(); search_y = searchDialog.getY();
               searchDialog.close();
            }
         });
         panel.getChildren().addAll(find, searchField, close);
         searchDialog = new Stage();
         searchDialog.initModality(Modality.NONE); // Non modal
         searchDialog.initOwner(config.gui.getFrame());
         gui.LoadIcons(searchDialog);
         searchDialog.setTitle("Search Table");
         // This so we can restore original dialog position when re-opened
         searchDialog.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent arg0) {
               search_x = searchDialog.getX(); search_y = searchDialog.getY();
            }
         });
         Scene scene = new Scene(new VBox());
         config.gui.setFontSize(scene, config.FontSize);
         ((VBox) scene.getRoot()).getChildren().add(panel);
         searchDialog.setScene(scene);
      }
      
      // Dialog already created, so display it and highlight any existing search text
      if (search_x != -1)
         searchDialog.setX(search_x);
      if (search_y != -1)
         searchDialog.setY(search_y);
      searchField.requestFocus();
      searchField.selectAll();
      searchDialog.show();
   }
   
   // Perform a search in given TABLE column name for searchString
   private static void Search(TableView<?> TABLE, String searchString, String colName) {
      int lastRow = TABLE.getItems().size()-1;
      int startRow = TABLE.getSelectionModel().getFocusedIndex();
      if (startRow < 0)
         startRow = 0;
      startRow += 1;
      if (startRow > lastRow)
         startRow = 0;
      TABLE.getSelectionModel().clearSelection(); // Clear selection
      Boolean result = searchMatch(TABLE, colName, searchString, startRow, lastRow);
      if (!result && startRow > 0) {
         searchMatch(TABLE, colName, searchString, 0, startRow);
      }
   }

   public static Boolean searchMatch(TableView<?> TABLE, String colName, String searchString, int start, int stop) {
      String v;
      for (int row=start; row<=stop; row++) {
         Object o = TABLE.getItems().get(row);
         v = null;
         if (o instanceof String)
            v = (String)o;
         else if (o.getClass().toString().contains("Tabentry"));
            v = o.toString();
         if ( v == null ) {
            log.error("searchMatch: Unimplemented SHOW type found");
         } else {
            v = v.toLowerCase();
            if (v.matches("^.*" + searchString.toLowerCase() + ".*$")) {
               // scroll to and set selection to given row
               scrollToCenter(TABLE, row);
               TABLE.getSelectionModel().select(row);
               TABLE.requestFocus();
               return true;
            }
         }
      }
      return false;      
   }
   
   // Perform a search in given TABLE column name for searchString
   private static void Search(TreeTableView<?> TABLE, String searchString, String colName) {
      int lastRow = TABLE.getExpandedItemCount()-1;
      int startRow = TABLE.getSelectionModel().getFocusedIndex();
      if (startRow < 0)
         startRow = 0;
      startRow += 1;
      if (startRow > lastRow)
         startRow = 0;
      TABLE.getSelectionModel().clearSelection(); // Clear selection
      Boolean result = searchMatch(TABLE, colName, searchString, startRow, lastRow);
      if (!result && startRow > 0) {
         searchMatch(TABLE, colName, searchString, 0, startRow);
      }
   }

   public static Boolean searchMatch(final TreeTableView<?> TABLE, String colName, String searchString, int start, int stop) {
      String v;
      for (int row=start; row<=stop; row++) {
         Object o = TABLE.getTreeItem(row).getValue();
         v = null;
         if (o instanceof String)
            v = (String)o;
         else if (o.getClass().toString().contains("Tabentry"));
            v = o.toString();
         if ( v == null ) {
            log.error("searchMatch: Unimplemented SHOW type found");
         } else {
            v = v.toLowerCase();
            if (v.matches("^.*" + searchString.toLowerCase() + ".*$")) {
               // scroll to and set selection to given row
               scrollToCenter(TABLE, row);
               TABLE.getSelectionModel().select(row);
               TABLE.requestFocus();
               return true;
            }
         }
      }
      return false;      
   }
   
   // Bring up set thumbs dialog
   public static void ThumbsGUI() {
      final String tivoName = config.gui.getCurrentRemoteTivoName();
      if (tivoName == null) {
         log.error("Setting thumbs is only valid from Remote tables");
         return;
      }
      final JSONObject json = config.gui.getCurrentRemoteJson();
      if (json == null)
         return;
      if (thumbsDialog == null) {
         // Dialog not created yet, so do so
         HBox row1 = new HBox();
         row1.setSpacing(5);
         Label rating = new Label("Thumbs Rating: ");
         thumbsChoice = new ChoiceBox<String>();
         for (int i=-3; i<=3; ++i)
            thumbsChoice.getItems().add("" + i);
         Button setButton = new Button("SET");
         setButton.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               Task<Void> task = new Task<Void>() {
                  @Override public Void call() {
                     // Determine tivoName and json of currently selected table
                     String tivoName = config.gui.getCurrentRemoteTivoName();
                     if (tivoName == null)
                        return null;
                     JSONObject json = config.gui.getCurrentRemoteJson();
                     if (json == null)
                        return null;
                     String setting = "" + thumbsChoice.getValue();
                     int thumbsRating = Integer.parseInt(setting);
                     Remote r = config.initRemote(tivoName);
                     if (r.success) {
                        Boolean result = r.setThumbsRating(json, thumbsRating, true);
                        r.disconnect();
                        String title = "";
                        try {
                           if (json.has("title"))
                              title = json.getString("title");
                        } catch (JSONException e) {
                           log.error("ThumbsGUI SET - " + e.getMessage());
                        }
                        if (result) {
                           log.warn("Successfully set thumbs rating for '" + title + "' to: " + thumbsRating);
                        }
                        else
                           log.error("Failed to set thumbs rating for '" + title + "'");
                     }
                     return null;
                  }
               };
               if (thumbsDialog != null) {
                  Platform.runLater(new Runnable() {
                     @Override public void run() {
                        thumbsDialog.hide();
                     }
                  });
               }
               new Thread(task).start();
            }
         });
         row1.getChildren().addAll(setButton, rating, thumbsChoice);
         row1.setPrefWidth(300);
         thumbsDialog = new Stage();
         thumbsDialog.initOwner(config.gui.getFrame());
         gui.LoadIcons(thumbsDialog);
         thumbsDialog.setScene(new Scene(row1));
         config.gui.setFontSize(thumbsDialog.getScene(), config.FontSize);
         thumbsDialog.setTitle("Thumbs Rating");
         thumbsDialog.show();
      }
      
      // Set default rating
      if (json != null) {
         Task<Void> task = new Task<Void>() {
            @Override public Void call() {
               int rating = 0;
               Remote r = config.initRemote(tivoName);
               if (r.success) {
                  rating = r.getThumbsRating(json);
                  r.disconnect();
               }
               final int rating_final = rating;
               Platform.runLater(new Runnable() {
                  @Override public void run() {
                     thumbsChoice.setValue("" + rating_final);
                  }
               });
               return null;
            }
         };
         new Thread(task).start();
      }
      
      // Set title
      String title = "Set thumbs: ";
      try {
         if (json != null && json.has("title"))
            title += json.getString("title");
      } catch (JSONException e) {
         log.error("ThumbsGUI - " + e.getMessage());
      }
      thumbsDialog.setTitle(title);
      
      // Display dialog and set default thumbs rating
      thumbsDialog.show();
   }

   // Add right mouse button listener
   public static void AddRightMouseListener(final TableView<?> TABLE) {            
      TABLE.setOnMousePressed(new EventHandler<MouseEvent>() {
         @Override 
         public void handle(MouseEvent event) {
            PopupHandler.hide();
            if (event.isSecondaryButtonDown())
               PopupHandler.display(TABLE, event);
            }
         }
      );
   }

   // Add right mouse button listener
   public static void AddRightMouseListener(final TreeTableView<?> TABLE) {            
      TABLE.setOnMousePressed(new EventHandler<MouseEvent>() {
         @Override 
         public void handle(MouseEvent event) {
            PopupHandler.hide();
            if (event.isSecondaryButtonDown())
               PopupHandler.display(TABLE, event);
            }
         }
      );
   }
   
   public static long getLongDateFromString(String date) {
      try {
         SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz");
         Date d = format.parse(date + " GMT");
         return d.getTime();
      } catch (ParseException e) {
        log.error("getLongDateFromString - " + e.getMessage());
        return 0;
      }
   }
   
   public static String printableTimeFromJSON(JSONObject entry) {
      long start = getStartTime(entry);
      SimpleDateFormat sdf = new SimpleDateFormat("E MM/dd/yy hh:mm a");
      return sdf.format(start);
   }
      
   public static long getStartTime(JSONObject json) {
      try {
         if (json.has("startTime")) {
            String startString = json.getString("startTime");
            long start = getLongDateFromString(startString);
            if (json.has("requestedStartPadding"))
               start -= json.getInt("requestedStartPadding")*1000;
            return start;
         } else {
            return 0;
         }
      } catch (Exception e) {
         log.error("getStartTime - " + e.getMessage());
         return 0;
      }
   }
   
   public static long getEndTime(JSONObject json) {
      try {
         long start = getStartTime(json);
         long end = start + json.getInt("duration")*1000;
         if (json.has("requestedEndPadding"))
            end += json.getInt("requestedEndPadding")*1000;
         return end;
      } catch (Exception e) {
         log.error("getEndTime - " + e.getMessage());
         return 0;
      }
   }
   
   public static Boolean isWL(JSONObject json) {
      Boolean WL = false;
      try {
         if (json.has("idSetSource")) {
            JSONObject idSetSource = json.getJSONObject("idSetSource");
            if (idSetSource.has("type") && idSetSource.getString("type").equals("wishListSource"))
               WL = true;
         }
      } catch (JSONException e) {
         log.error("isWL - " + e.getMessage());
      }
      return WL;
   }
   
   public static String getSortableDate(sortableDate s) {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmm");
      long gmt = Long.parseLong(s.sortable);
      return sdf.format(gmt);
   }
      
   public static String currentYearMonthDay() {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd");
      return sdf.format(new Date().getTime());
   }
   
   public static String makeShowTitle(JSONObject entry) {
      String title = " ";
      try {
         if (entry.has("title"))
            title += entry.getString("title");
         if (entry.has("seasonNumber") && entry.has("episodeNum")) {
            title += " [Ep " + entry.get("seasonNumber") +
            String.format("%02d]", entry.getJSONArray("episodeNum").get(0));
         }
         if (entry.has("movieYear"))
            title += " [" + entry.get("movieYear") + "]";
         if (entry.has("subtitle"))
            title += " - " + entry.getString("subtitle");
         if (entry.has("subscriptionIdentifier")) {
            JSONArray a = entry.getJSONArray("subscriptionIdentifier");
            if (a.length() > 0) {
               if (a.getJSONObject(0).has("subscriptionType")) {
                  String type = a.getJSONObject(0).getString("subscriptionType");
                  if (type.equals("singleTimeChannel") || type.equals("repeatingTimeChannel"))
                     title = " Manual:" + title;
               }
            }
         }
      } catch (JSONException e) {
         log.error("makeShowTitle - " + e.getMessage());
      }
      return title;
   }
   
   public static String makeChannelName(JSONObject entry) {
      String channel = "";
      try {
         if (entry.has("channel")) {
            JSONObject o = entry.getJSONObject("channel");
            if (o.has("channelNumber"))
               channel += o.getString("channelNumber");
            if (o.has("callSign")) {
               String callSign = o.getString("callSign");
               if (callSign.toLowerCase().equals("all channels"))
                  channel += callSign;
               else
                  channel += "=" + callSign;
            }
         } else {
            if (entry.has("idSetSource")) {
               JSONObject idSetSource = entry.getJSONObject("idSetSource");
               if (idSetSource.has("channel"))
                  channel = makeChannelName(idSetSource);
               else {
                  if (idSetSource.has("consumptionSource")) {
                     if (idSetSource.getString("consumptionSource").equals("linear"))
                        channel += "All Channels";
                  }
               }
            }
         }
      } catch (JSONException e) {
         log.error("makeChannelName - " + e.getMessage());
      }
      return channel;
   }
   
   private static String makeDate(JSONObject json) {
      if (json.has("startTime")) {
         SimpleDateFormat sdf = new SimpleDateFormat("E MM/dd/yy hh:mm a");
         long start = getStartTime(json);
         return sdf.format(start);
      }
      return "";
   }
   
   public static String makeShowSummary(JSONObject json) {
      String date = makeDate(json);
      String channel = makeChannelName(json);
      String title = makeShowTitle(json);
      return date + " " + channel + " " + title;
   }
   
   // Used by TABLERowSelected callbacks for printing show info to message window
   public static String makeShowSummary(sortableDate s, sortableDuration dur) {
      try {
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
         if (dur != null && dur.sortable != null && dur.sortable > 0) {
            d = rnpl.msecsToMins(dur.sortable);
         }
         String message = "";
         if (s.display != null && ! s.sortable.equals("0"))
            message = s.display;
         if (channelNum != null && channel != null) {
            message += " on " + channelNum + "=" + channel;
         }
         if (d.length() > 0)
            message += ", Duration = " + d;
         
         if (s.json.has("seasonNumber"))
            message += ", season " + s.json.get("seasonNumber");
         if (s.json.has("episodeNum"))
            message += " episode " + s.json.getJSONArray("episodeNum").get(0);
         if (s.json.has("originalAirdate"))
            message += ", originalAirdate: " + s.json.getString("originalAirdate");
         if (s.json.has("movieYear"))
            message += ", movieYear: " + s.json.get("movieYear");
         
         if (description != null) {
            message += "\n" + description;
         }         
         return message;
      } catch (Exception e) {
         log.error("makeShowSummary - " + e.getMessage());
      }
      return "";
   }

   // Check if given json is a show scheduled to record on this TiVo
   private static Boolean isRecordingScheduled(JSONObject json) {
      try {
         if (json != null && json.has("state")) {
            if (json.getString("state").equals("scheduled") || json.getString("state").equals("inProgress"))
               return(true);
         }
      } catch (JSONException e) {
         log.error("isRecordingScheduled error - " + e.getMessage());
      }
      return(false);
   }
   
   // Main engine for single show scheduling. This can be a new show
   // or an existing show for which to modify recording options.
   private static Boolean recordSingle(final String tivoName, final JSONObject json) {
      try {
         if (json.has("partnerId") && ! json.has("channel")) {
            Boolean streaming = true;
            if (json.has("collectionType") && json.getString("collectionType").equals("webVideo"))
               streaming = false;
            if (streaming) {
               // Streaming only entry requires ContentLocatorStore
               if (json.has("collectionId")) {
                  return bookmarkIfPossible(tivoName, json);
               } else {
                  log.warn("Missing collectionId for streaming title");
                  return false;
               }
            }
         }
         String title = "UNTITLED";
         if (json.has("title"))
            title = json.getString("title");
         String message = "";
         if (json.has("contentId") && json.has("offerId")) {
            JSONObject existing = null;
            if ( isRecordingScheduled(json) )
               existing = json;
            message = "(" + tivoName + ") " + "Schedule Recording: ";
            if (existing != null) {
               message = "(" + tivoName + ") " + "Modify Recording: ";
            }
            message += "'" + title + "'";
            final JSONObject o = util.recordOpt.promptUser(
               message, existing
            );
            if (o != null) {
               Boolean anywhere = false;
               if (o.has("_anywhere_")) {
                  anywhere = true;
                  o.remove("_anywhere_");
               }
               o.put("contentId", json.getString("contentId"));
               o.put("offerId", json.getString("offerId"));
               final String _title = title;
               if (! anywhere) {
                  // Attempt to schedule on tivoName only
                  if (existing == null)
                     message = "Scheduled recording: '" + title + "' on Tivo: " + tivoName;
                  else
                     message = "Modified recording: '" + title + "' on Tivo: " + tivoName;
                  final String _message = message;
                  class backgroundRun extends Task<Object> {
                     JSONObject json;
                     public backgroundRun(JSONObject json) {
                        this.json = json;
                     }
                     @Override
                     protected Object call() {
                        Remote r = config.initRemote(tivoName);
                        if (r.success) {
                           JSONObject result = r.Command("Singlerecording", o);
                           if (result == null) {
                              log.error("Failed to schedule/modify recording for: '" + _title + "'");
                           } else {
                              String conflicts = rnpl.recordingConflicts(result, json);
                              if (conflicts == null) {
                                 log.warn(_message);
                                 // Set thumbs rating if it doesn't exist for this collection
                                 r.setThumbsRating(json, 1, false);
                              } else {
                                 log.error(conflicts);
                                 return(false);
                              }
                           }
                           r.disconnect();
                        }
                        return null;
                     }
                  }
                  backgroundRun b = new backgroundRun(json);
                  new Thread(b).start();
               } else {
                  if (existing == null) {
                     // Attempt to schedule using all RPC enabled TiVos
                     class backgroundRun extends Task<Object> {
                        JSONObject json;
                        public backgroundRun(JSONObject json) {
                           this.json = json;
                        }
                        @Override
                        protected Object call() {
                           Stack<String> tivo_stack = config.getTivoNames();
                           Stack<String> tivos = new Stack<String>();
                           tivos.add(tivoName); // Put original target tivo 1st in stack
                           // RPC only TiVos get priority
                           for (int i=0; i<tivo_stack.size(); ++i) {
                              if ( config.rpcEnabled(tivo_stack.get(i)) ) {
                                 if (tivos.search(tivo_stack.get(i)) == -1)
                                    tivos.add(tivo_stack.get(i));
                              }
                           }
                           // Series 3 TiVos are last resort (if tivo.com username & password are available)
                           if (config.mindEnabled(tivoName)) {
                              for (int i=0; i<tivo_stack.size(); ++i) {
                                 if (tivos.search(tivo_stack.get(i)) == -1)
                                    tivos.add(tivo_stack.get(i));
                              }
                           }
                           for (int i=0; i<tivos.size(); ++i) {
                              String name = tivos.get(i);
                              String message = "Scheduled recording: '" + _title + "' on Tivo: " + name;
                              Remote r = config.initRemote(name);
                              if (r.success) {
                                 JSONObject result = r.Command("Singlerecording", o);
                                 if (result == null) {
                                    log.error("Failed attempt to schedule recording on '" + name + "' for: '" + _title + "'");
                                 } else {
                                    String conflicts = rnpl.recordingConflicts(result, json);
                                    if (conflicts == null) {
                                       log.warn(message);
                                       // Set thumbs rating if it doesn't exist for this collection
                                       r.setThumbsRating(json, 1, false);
                                       return(true);
                                    } else {
                                       log.warn("Cannot schedule '" + _title + "' on '" + name + "' due to conflicts");
                                    }
                                 }
                                 r.disconnect();
                              }
                           }
                           return null;
                        }
                     }
                     backgroundRun b = new backgroundRun(json);
                     new Thread(b).start();                     
                  }
               }
            }
         } else {
            // This is likely unavailable content
            if (json.has("collectionType") && json.getString("collectionType").equals("series")) {
               // for type series bring up SP form
               Remote r = config.initRemote(tivoName);
               if (r.success) {
                  JSONArray existing = r.SeasonPasses(null);
                  if (existing != null) {
                     r.SPschedule(tivoName, json, existing);
                  }
                  r.disconnect();
                  return(true);
               }
               return(false);
            }
            
            if (json.has("collectionId")) {
               // Non-series type with collectionId may be possible to bookmark
               return bookmarkIfPossible(tivoName, json);
            }
            
            // Exhausted all possibilities so error out
            log.error("Missing contentId and/or offerId for: '" + title + "'");
            return(false);
         }
      } catch (JSONException e) {
         log.error("recordSingle failed - " + e.getMessage());
         return(false);
      }
      return(true);
   }
   
   private static Boolean bookmarkIfPossible(String tivoName, JSONObject json) {
      class backgroundRun extends Task<Object> {
         String tivoName;
         JSONObject json;
         public backgroundRun(String tivoName, JSONObject json) {
            this.tivoName = tivoName;
            this.json = json;
         }
         @Override
         protected Boolean call() {
            JSONObject result;
            Remote r = config.initRemote(tivoName);
            if (r.success) {
               try {
                  if (! json.has("contentId")) {
                     // If contentId is missing then look for one
                     JSONObject j = new JSONObject();
                     j.put("bodyId", r.bodyId_get());
                     j.put("collectionId", json.getString("collectionId"));
                     result = r.Command("contentSearch", j);
                     if (result != null && result.has("content")) {
                        JSONObject o = result.getJSONArray("content").getJSONObject(0);
                        if (o.has("contentId"))
                           json.put("contentId", o.getString("contentId"));
                     }
                  }
                  // Without contentId we can't proceed
                  if (! json.has("contentId")) {
                     log.error("Unable to determine/find contentId");
                     r.disconnect();
                     return false;
                  }
                  // Have contentId and collectionId, so proceed with adding content locator
                  String title = "UNTITLED";
                  if (json.has("title"))
                     title = json.getString("title");
                  JSONObject j = new JSONObject();
                  j.put("contentId", json.getString("contentId"));
                  j.put("collectionId", json.getString("collectionId"));
                  result = r.Command("ContentLocatorStore", j);
                  if (result != null) {
                     log.warn("Added bookmark to My Shows: '" + title + "' on Tivo: " + tivoName);
                     // Set thumbs rating if it doesn't exist for this collection
                     r.setThumbsRating(json, 1, false);
                     r.disconnect();
                     return true;
                  } else {
                     log.error("Failed to create content locator for: " + title);
                     r.disconnect();
                     return false;
                  }
               } catch (JSONException e) {
                  log.error("bookmarkIfPossible - " + e.getMessage());
               }
            } // if r.success
            return false;
         } // doInBackground
      } // class backgroundRun
      
      backgroundRun b = new backgroundRun(tivoName, json);
      new Thread(b).start();                    
      return true;
   }
   
   // Method used by various RPC tables for single item recording
   public static void recordSingleCB(final String tivoName, final JSONArray entries) {
      if (entries.length() > 0) {
         JSONObject json;
         for (int i=0; i<entries.length(); ++i) {
            try {
               json = entries.getJSONObject(i);
               if (json != null) {
                  if (recordSingle(tivoName, json) && ! isRecordingScheduled(json)) {
                     // Add to todo list for this tivo
                     util.addEntryToTodo(tivoName, json);
                     addTivoNameFlagtoJson(json, "__inTodo__", tivoName);
                  }
               }
            } catch (JSONException e) {
               log.error("recordSingleCB error - " + e.getMessage());
            }
         }
      }
   }
   
   // For a given array of JSON objects sort by start date - most recent 1st
   static public JSONArray sortByLatestStartDate(JSONArray array) {
      class DateComparator implements Comparator<JSONObject> {      
         public int compare(JSONObject j1, JSONObject j2) {
            long start1 = getStartTime(j1);
            long start2 = getStartTime(j2);
            if (start1 < start2){
               return 1;
            } else if (start1 > start2){
               return -1;
            } else {
               return 0;
            }
         }
      }
      List<JSONObject> arrayList = new ArrayList<JSONObject>();
      for (int i=0; i<array.length(); ++i)
         try {
            arrayList.add(array.getJSONObject(i));
         } catch (JSONException e) {
            log.error("sortByStartDate - " + e.getMessage());
         }
      JSONArray sorted = new JSONArray();
      DateComparator comparator = new DateComparator();
      Collections.sort(arrayList, comparator);
      for (JSONObject ajson : arrayList) {
         sorted.put(ajson);
      }
      return sorted;
   }
   
   // For a given array of JSON objects sort by start date - oldest 1st
   static public JSONArray sortByOldestStartDate(JSONArray array) {
      class DateComparator implements Comparator<JSONObject> {      
         public int compare(JSONObject j1, JSONObject j2) {
            long start1 = getStartTime(j1);
            long start2 = getStartTime(j2);
            if (start1 > start2){
               return 1;
            } else if (start1 < start2){
               return -1;
            } else {
               return 0;
            }
         }
      }
      List<JSONObject> arrayList = new ArrayList<JSONObject>();
      for (int i=0; i<array.length(); ++i)
         try {
            arrayList.add(array.getJSONObject(i));
         } catch (JSONException e) {
            log.error("sortByStartDate - " + e.getMessage());
         }
      JSONArray sorted = new JSONArray();
      DateComparator comparator = new DateComparator();
      Collections.sort(arrayList, comparator);
      for (JSONObject ajson : arrayList) {
         sorted.put(ajson);
      }
      return sorted;
   }
   
   // For a given array of JSON objects sort by episode numbers - earliest 1st
   static public JSONArray sortByEpisode(JSONArray array) {
      class EpComparator implements Comparator<JSONObject> {      
         public int compare(JSONObject j1, JSONObject j2) {
            int ep1 = getEpisodeNum(j1);
            int ep2 = getEpisodeNum(j2);
            if (ep1 > ep2){
               return 1;
            } else if (ep1 < ep2){
               return -1;
            } else {
               return 0;
            }
         }
      }
      List<JSONObject> arrayList = new ArrayList<JSONObject>();
      for (int i=0; i<array.length(); ++i) {
         try {
            arrayList.add(array.getJSONObject(i));
         } catch (JSONException e) {
            log.error("sortByEpisode - " + e.getMessage());
         }
      }
      JSONArray sorted = new JSONArray();
      EpComparator comparator = new EpComparator();
      Collections.sort(arrayList, comparator);
      for (JSONObject ajson : arrayList) {
         sorted.put(ajson);
      }
      return sorted;
   }
   
   public static int getEpisodeNum(JSONObject json) {
      try {
         if (json.has("seasonNumber") && json.has("episodeNum")) {
            int seasonNumber = json.getInt("seasonNumber");
            int episodeNum = json.getJSONArray("episodeNum").getInt(0);
            return 100*seasonNumber + episodeNum;
         }
      } catch (Exception e) {
         log.error("getEpisodeNum - " + e.getMessage());
      }
      return 0;
   }
   
   // Send url to web browser
   static public void webQuery(String title) {
      try {
         String url = config.web_query + URLEncoder.encode(title, "UTF-8");
         help.showInBrowser(url);
      } catch (UnsupportedEncodingException e) {
         log.error("webQuery - " + e.getMessage());
      }
   }
   
   static public void addTivoNameFlagtoJson(JSONObject json, String flag, String tivoName) {
      try {
         if (json.has(flag))
            json.put(flag, json.getString(flag) + ", " + tivoName);
         else
            json.put(flag, tivoName);
      } catch (JSONException e) {
         log.error("addTivoNameFlagtoJson - " + e.getMessage());
      }
   }
   
   // Return friendly name of a partner based on id, such as Netflix, Hulu, etc.
   static public String getPartnerName(JSONObject entry) {
      try {
         if (config.partners.size() == 0) {
            log.warn("Refreshing partner names");
            Remote r = config.initRemote(config.gui.remote_gui.getTivoName("search"));
            if (r.success) {
               JSONObject json = new JSONObject();
               json.put("bodyId", r.bodyId_get());
               json.put("noLimit", true);
               json.put("levelOfDetail", "high");
               JSONObject result = r.Command("partnerInfoSearch", json);
               if (result != null && result.has("partnerInfo")) {
                  JSONArray info = result.getJSONArray("partnerInfo");
                  for (int i=0; i<info.length(); ++i) {
                     JSONObject j = info.getJSONObject(i);
                     if (j.has("partnerId") && j.has("displayName")) {
                        config.partners.put(j.getString("partnerId"), j.getString("displayName"));
                     }
                  }
               }                 
               r.disconnect();
            }
         }
   
         String partnerId = "";
         if (entry.has("partnerId"))
            partnerId = entry.getString("partnerId");
         if (entry.has("brandingPartnerId"))
            partnerId = entry.getString("brandingPartnerId");
         String name = partnerId;
         if (config.partners.containsKey(partnerId))
            name = config.partners.get(partnerId);
         /*if (name.equals(partnerId) && config.getTivoUsername() != null) {
            // Not cached, so find it without bodyId from middlemind if tivo.com credentials available
            Remote r = new Remote(config.gui.remote_gui.getTivoName("search"), true);
            if (r.success) {
               JSONObject json = new JSONObject();
               json.put("partnerId", partnerId);
               JSONObject result = r.Command("partnerInfoSearch", json);
               r.disconnect();
               if (result != null && result.has("partnerInfo")) {
                  JSONObject partner = result.getJSONArray("partnerInfo").getJSONObject(0);
                  if (partner.has("displayName")) {
                     name = partner.getString("displayName");
                     config.partners.put(partnerId, name);
                  }
               }
            }
         }*/
         return name;
      } catch (JSONException e1) {
         log.error("getPartnerName - " + e1.getMessage());
         return "STREAMING";
      }
   }
   
   static public void PrintEpisodes(JSONObject json) {
      if (json == null) return;
      String collectionId = null;
      String title = null;
      try {
         if (json.has("collectionId")) {
            collectionId = json.getString("collectionId");
         } else {
            if (json.has("idSetSource")) {
               JSONObject idSetSource = json.getJSONObject("idSetSource");
               if (idSetSource.has("collectionId"))
                  collectionId = idSetSource.getString("collectionId");
            }
         }
         if (json.has("title"))
            title = json.getString("title");
      } catch (JSONException e) {
         log.error("PrintEpisodes - " + e.getMessage());
      }
      if (collectionId == null) {
         log.warn("No collectionId available for this entry");
         return;
      }
      if (title == null)
         title = collectionId;
      PrintEpisodes(title, collectionId);
   }
   
   static public void PrintEpisodes(String title, String collectionId) {
      String tivoName = config.getFirstRpcEnabled();
      if (tivoName == null)
         return;
      log.warn(">> Collecting episode data for: " + title);
      Task<Void> task = new Task<Void>() {
         @Override public Void call() {
            Remote r = new Remote(tivoName, true);
            if (r.success) {
               try {
                  JSONArray entries = r.getEpisodes(collectionId);
                  r.disconnect();
                  JSONArray episodes = new JSONArray();
                  for (int i=0; i<entries.length(); ++i) {
                     JSONObject entry = entries.getJSONObject(i);
                     if (entry.has("description")) {
                        episodes.put(entry);
                     }
                  }
                  if (episodes.length() > 0) {
                     Platform.runLater(new Runnable() {
                        @Override public void run() {
                           PrintEpisodes_GUI(title, episodes);
                        }
                     });
                  } else {
                     log.warn("No episodes found for: " + title);
                  }
               } catch (JSONException e) {
                  log.error("searchTable GetEpisodes - " + e.getMessage());
               }
            }
            return null;
         }
      };
      new Thread(task).start();
   }
   
   static public void PrintEpisodes_GUI(String title, JSONArray episodes) {
      List<String> choices = new ArrayList<>();
      choices.add("Output to table");
      choices.add("Output CSV File");
      choices.add("Output to table and CSV File");

      ChoiceDialog<String> dialog = new ChoiceDialog<>("Output to table", choices);
      dialog.setTitle("Choose Output");
      dialog.setHeaderText("Episode Output for: " + title);
      dialog.setContentText("Choose output:");

      Optional<String> result = dialog.showAndWait();
      if (result.isPresent()){
         switch (result.get()) {
            case "Output to table and CSV File":
               PrintEpisodes_table(episodes);
               PrintEpisodes_csv(title, episodes);
               break;               
            case "Output CSV File":
               PrintEpisodes_csv(title, episodes);
               break;
            case "Output to table":
               PrintEpisodes_table(episodes);
               break;
         }
      }
   }
   
   static public void PrintEpisodes_csv(String title, JSONArray episodes) {
      config.gui.remote_gui.Browser.getExtensionFilters().clear();
      config.gui.remote_gui.Browser.getExtensionFilters().addAll(new ExtensionFilter("CSV Files", "*.csv"));
      config.gui.remote_gui.Browser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ALL FILES", "*"));
      config.gui.remote_gui.Browser.setTitle("Export to csv file");
      config.gui.remote_gui.Browser.setInitialDirectory(new File(config.programDir));
      config.gui.remote_gui.Browser.setInitialFileName(tivoFileName.removeSpecialChars(title) + "" + ".csv");
      final File selectedFile = config.gui.remote_gui.Browser.showSaveDialog(config.gui.getFrame());
      if (selectedFile != null) {
         String file = selectedFile.getAbsolutePath();
         try {
            BufferedWriter ofp = new BufferedWriter(new FileWriter(file));
            ofp.write("EPISODE NAME,PROGRAMID,SERIESID\r\n");
            for (int i=0; i<episodes.length(); ++i) {
               JSONObject episode = episodes.getJSONObject(i);
               String programId = id.programId(episode);
               String seriesId = id.seriesId(episode);
               if (programId == null) programId = "NONE";
               if (seriesId == null) seriesId = "NONE";
               ofp.write("\"" + makeShowTitle(episode) + "\"");
               ofp.write("," + programId);
               ofp.write("," + seriesId);
               ofp.write("\r\n");
            }
            ofp.close();
            log.print("Output " + "'" + title + "' episodes to csv file: " + file);
         } catch (Exception e) {
            log.error("PrintEpisodes_GUI - " + e.getMessage());
         }
      }
   }
   
   static public void PrintEpisodes_table(JSONArray episodes) {
      try {
         streamTable tab = config.gui.remote_gui.stream_tab.tab;
         String tivoName = config.gui.remote_gui.getTivoName("Streaming");
         tab.AddRows(tivoName, episodes);
         config.gui.remote_gui.getPanel().getSelectionModel().select(6);
         config.gui.SetTivo("Remote");
      } catch (Exception e) {
         log.error("PrintEpisodes_table - " + e.getMessage());
      }      
   }
   
   static public void PrintClipData(String tivoName, JSONObject json) {
      if (json != null && json.has("contentId")) {
         try {
            final String contentId = json.getString("contentId");
            Task<Void> task = new Task<Void>() {
               @Override public Void call() {
                  Remote r = config.initRemote(tivoName);
                  if (r.success) {
                     r.printClipData(contentId);
                     r.disconnect();
                  }
                  return null;
               }
            };
            new Thread(task).start();
         } catch (JSONException e) {
            log.error("PrintClipData - " + e.getMessage());
         }
      }
   }

}
