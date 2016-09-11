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

import java.util.Hashtable;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.dialog.AdvSearch;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.searchTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class search {
   public VBox panel = null;
   public ChoiceBox<String> tivo = null;
   public searchTable tab = null;
   public TextField text = null;
   public Button button = null;
   public Spinner<Integer> max = null;
   public ChoiceBox<String> search_type = null;
   public CheckBox includeFree = null;
   public CheckBox includePaid = null;
   //public CheckBox includeVod = null;
   //public CheckBox unavailable = null;
   public Hashtable<String,JSONArray> search_info = new Hashtable<String,JSONArray>();
   public AdvSearch advSearch = new AdvSearch();
   public Button manual_record = null;
   public Button record = null;    
   public Button recordSP = null;    
   public Button wishlist = null;    
   
   public search (final Stage frame) {
      
      // Search tab items      
      HBox row1 = new HBox();
      row1.setSpacing(5);
      row1.setAlignment(Pos.CENTER_LEFT);
      row1.setPadding(new Insets(5,0,0,5));
      
      HBox row2 = new HBox();
      row2.setSpacing(5);
      row2.setAlignment(Pos.CENTER_LEFT);
      row2.setPadding(new Insets(0,0,0,5));
      
      Label title = new Label("Search");
      
      Label tivo_label = new Label();
      
      tivo = new ChoiceBox<String>();
      tivo.valueProperty().addListener(new ChangeListener<String>() {
         @Override public void changed(ObservableValue<? extends String> ov, String oldVal, String newVal) {
            if (newVal != null && config.gui.remote_gui != null) {
                String tivoName = newVal;
                config.gui.remote_gui.updateButtonStates(tivoName, "Search");
            }
         }
      });
      tivo.setTooltip(tooltip.getToolTip("tivo_search"));

      button = new Button("Search");
      button.setTooltip(tooltip.getToolTip("button_search"));
      button.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            // New search
            tab.clear();
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               String keyword = string.removeLeadingTrailingSpaces(text.getText());
               if (keyword == null || keyword.length() == 0)
                  return;
               int max_val = (Integer)max.getValue();
               
               jobData job = new jobData();
               job.source                = tivoName;
               job.tivoName              = tivoName;
               job.type                  = "remote";
               job.name                  = "Remote";
               job.search                = tab;
               job.remote_search_max     = max_val;
               job.remote_search         = true;
               job.remote_search_keyword = keyword;
               jobMonitor.submitNewJob(job);
            }
         }
      });

      text = new TextField(); text.setMinWidth(15);
      text.setMinWidth(text.getPrefWidth());
      // Press "Search" button when enter pressed in search text field
      text.addEventHandler(KeyEvent.KEY_PRESSED, new EventHandler<KeyEvent>() {
         public void handle(KeyEvent event) {
            if (event.isControlDown())
               return;
            if( event.getCode() == KeyCode.ENTER ) {
               button.fire();
               event.consume();
            }
         }
      });

      text.setTooltip(tooltip.getToolTip("text_search"));

      Button adv = new Button("Search++");
      adv.setTooltip(tooltip.getToolTip("adv_search"));
      adv.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               advSearch.display(
                  config.gui.getFrame(), tivoName, (Integer)max.getValue()
               );
            }
         }
      });

      record = new Button("Record");
      record.setTooltip(tooltip.getToolTip("record_search"));
      record.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               tab.recordSingle(tivoName);
            }
         }
      });

      recordSP = new Button("SP");
      recordSP.setTooltip(tooltip.getToolTip("record_sp_search"));
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

      Button refresh_todo = new Button("Refresh ToDo");
      refresh_todo.setTooltip(tooltip.getToolTip("refresh_todo_search"));
      refresh_todo.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               Task<Void> task = new Task<Void>() {
                  @Override public Void call() {
                     log.warn("Refreshing ToDo list for Search matches...");
                     util.all_todo = util.getTodoLists();
                     log.warn("Refresh ToDo list for Search matches completed.");
                     return null;
                  }
               };
               new Thread(task).start();
            }
         }
      });

      Label max_label = new Label("Max");
      max = new Spinner<Integer>(50, 800, 100, 50);
      max.setEditable(true);
      max.setMaxWidth(90);
      max.setTooltip(tooltip.getToolTip("max_search"));
      
      row1.getChildren().add(title);
      row1.getChildren().add(tivo_label);
      row1.getChildren().add(tivo);
      row1.getChildren().add(button);
      row1.getChildren().add(text);
      row1.getChildren().add(max_label);
      row1.getChildren().add(max);
      row1.getChildren().add(adv);
      row1.getChildren().add(record);
      row1.getChildren().add(recordSP);
      row1.getChildren().add(wishlist);
      row1.getChildren().add(manual_record);
      row1.getChildren().add(refresh_todo);
      
      Label search_type_label = new Label("Type");
      
      search_type = new ChoiceBox<String>();
      search_type.getItems().addAll(
         "keywords", "actor", "director", "producer", "executiveProducer", "writer"
      );
      search_type.setValue("keywords");
      search_type.setTooltip(tooltip.getToolTip("search_type"));
      
      includeFree = new CheckBox("Streaming content");
      includeFree.setSelected(false);
      includeFree.setTooltip(tooltip.getToolTip("includeFree"));
      
      includePaid = new CheckBox("Paid streaming content");
      includePaid.setSelected(false);
      includePaid.setTooltip(tooltip.getToolTip("includePaid"));
      
      //includeVod = new CheckBox("VOD content");
      //includeVod.setSelected(false);
      //includeVod.setTooltip(tooltip.getToolTip("includeVod"));
      
      //unavailable = new CheckBox("Unavailable");
      //unavailable.setSelected(false);
      //unavailable.setTooltip(tooltip.getToolTip("unavailable"));
      
      row2.getChildren().add(search_type_label);
      row2.getChildren().add(search_type);
      row2.getChildren().add(includeFree);
      row2.getChildren().add(includePaid);
      //row2.getChildren().add(includeVod);
      //row2.getChildren().add(unavailable);
      
      tab = new searchTable();
      VBox.setVgrow(tab.TABLE, Priority.ALWAYS); // stretch vertically
      
      panel = new VBox();
      panel.setSpacing(1);
      panel.getChildren().addAll(row1, row2, tab.TABLE);      
   }

}
