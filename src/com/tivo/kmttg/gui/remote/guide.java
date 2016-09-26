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
package com.tivo.kmttg.gui.remote;

import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.MyListView;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.guideTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.kmttg;
import com.tivo.kmttg.util.log;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class guide {
   public VBox panel = null;
   public guideTable tab = null;
   public MyListView ChanList = null;
   public  Button refresh = null;
   public ChoiceBox<String> tivo = null;
   public ChoiceBox<String> start = null;
   public CheckBox guide_channels = null;
   public Button record = null;
   public Button recordSP = null;
   public Button wishlist = null;
   public  int range = 24; // Number of hours to show in guide at a time
   public int hour_increment = 24; // Number of hours for date increment
   public int total_range = 13;    // Number of days
   public Button manual_record = null;
   
   public guide(final Stage frame) {
      
      // Guide Tab items            
      HBox row1 = new HBox();
      row1.setSpacing(5);
      row1.setAlignment(Pos.CENTER_LEFT);
      row1.setPadding(new Insets(5,0,0,5)); // top, right, bottom, left
      
      Label title = new Label("Guide");
      
      Label tivo_label = new Label();
      
      tivo = new ChoiceBox<String>();
      tivo.valueProperty().addListener(new ChangeListener<String>() {
         @Override public void changed(ObservableValue<? extends String> ov, String oldVal, String newVal) {
            // Don't do anything if oldVal is null or kmttg starting (implies values being reset)
            if (kmttg._startingUp || oldVal == null) return;
            
            if (newVal != null) {
                // Refresh channel list and clear table
               ChanList.getItems().clear();
               tab.clear();
               String tivoName = newVal;
               config.gui.remote_gui.updateButtonStates(tivoName, "Guide");
               tab.updateChannels(tivoName, false);
               if (tab.tivo_data.containsKey(tivoName))
                  tab.AddRows(tivoName, tab.tivo_data.get(tivoName));
            }
         }
      });
      tivo.setTooltip(tooltip.getToolTip("tivo_guide"));
      
      guide_channels = new CheckBox("All");
      guide_channels.setSelected(false);
      guide_channels.setTooltip(tooltip.getToolTip("guide_channels"));
      
      Label guide_start_label = new Label("Start");
      start = new ChoiceBox<String>();
      start.setTooltip(tooltip.getToolTip("guide_start"));
      // When start time changes need to update the table when appropriate
      start.valueProperty().addListener(new ChangeListener<String>() {
         @Override public void changed(ObservableValue<? extends String> ov, String oldVal, String newVal) {
            if (newVal != null) {
               String tivoName = tivo.getValue();
               if (tivoName != null && ChanList != null) {
                  String chanName = ChanList.getSelectionModel().getSelectedItem();
                  if (chanName != null)
                     tab.updateTable(tivoName, chanName);
               }
            }
         }
      });

      refresh = new Button("Channels");
      refresh.setTooltip(tooltip.getToolTip("refresh_guide"));
      refresh.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            ChanList.getItems().clear();
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               // Obtain and display channel list
               tab.updateChannels(tivoName, true);
            }
         }
      });

      record = new Button("Record");
      record.setTooltip(tooltip.getToolTip("guide_record"));
      record.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               tab.recordSingle(tivoName);
            }
         }
      });

      recordSP = new Button("Season Pass");
      recordSP.setTooltip(tooltip.getToolTip("guide_recordSP"));
      recordSP.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               tab.recordSP(tivoName);
            }
         }
      });
      
      wishlist = new Button("WL");
      wishlist.setTooltip(tooltip.getToolTip("wishlist_search"));
      wishlist.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               int[] selected = TableUtil.GetSelectedRows(tab.TABLE);
               JSONObject json = null;
               if (selected.length > 0)
                  json = tab.GetRowData(selected[0]);
               config.gui.remote_gui.createWishlist(tivoName, json);
            }
         }
      });
      
      manual_record = new Button("MR");
      manual_record.setTooltip(tooltip.getToolTip("guide_manual_record"));
      manual_record.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               util.mRecordOpt.promptUser(tivoName);
            }
         }
      });

      Button guide_refresh_todo = new Button("Refresh ToDo");
      guide_refresh_todo.setTooltip(tooltip.getToolTip("guide_refresh_todo"));
      guide_refresh_todo.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               Task<Void> task = new Task<Void>() {
                  @Override public Void call() {
                     log.warn("Refreshing ToDo list for Guide entries...");
                     util.all_todo = util.getTodoLists();
                     log.warn("Refresh ToDo list for Guide entries completed.");
                     return null;
                  }
               };
               new Thread(task).start();
            }
         }
      });
      
      row1.getChildren().add(title);
      row1.getChildren().add(tivo_label);
      row1.getChildren().add(tivo);
      row1.getChildren().add(guide_start_label);
      row1.getChildren().add(start);
      row1.getChildren().add(guide_channels);
      row1.getChildren().add(refresh);
      row1.getChildren().add(record);
      row1.getChildren().add(recordSP);
      row1.getChildren().add(wishlist);
      row1.getChildren().add(manual_record);
      row1.getChildren().add(guide_refresh_todo);
      
      tab = new guideTable();
      VBox.setVgrow(tab.TABLE, Priority.ALWAYS); // stretch vertically
      
      ChanList = new MyListView();
      ChanList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
      ChanList.setOrientation(Orientation.VERTICAL);
      // When a list item is selected, update the table when appropriate
      ChanList.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
         @Override
         public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
            if (newValue != null) {
               String tivoName = tivo.getValue();
               if (tivoName != null) {
                  tab.updateTable(tivoName, newValue);
               }
            }
         }
      });
      ChanList.setTooltip(tooltip.getToolTip("guideChanList"));
      VBox.setVgrow(ChanList, Priority.ALWAYS); // stretch vertically
      
      GridPane tab_row = new GridPane();
      tab_row.setHgap(1);
      tab_row.setPadding(new Insets(0,0,0,5));
      tab_row.getColumnConstraints().add(0, util.cc_none());
      tab_row.getColumnConstraints().add(1, util.cc_stretch());
      tab_row.getRowConstraints().add(0, util.rc_stretch());
      ChanList.setMinWidth(150); ChanList.setMaxWidth(150);
      tab_row.add(ChanList, 0, 0);
      tab_row.add(tab.TABLE, 1, 0);
      VBox.setVgrow(tab_row, Priority.ALWAYS); // stretch vertically
            
      panel = new VBox();
      panel.setSpacing(1);
      panel.getChildren().addAll(row1, tab_row);      
   }

}
