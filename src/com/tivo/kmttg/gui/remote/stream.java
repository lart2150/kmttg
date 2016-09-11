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

import com.tivo.kmttg.gui.table.streamTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.log;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class stream {
   public VBox panel = null;
   public streamTable tab = null;
   public Button refresh = null;
   public Button remove = null;
   public ChoiceBox<String> tivo = null;
   
   public stream(final Stage frame) {
      
      // Streaming Tab items
      HBox row1 = new HBox();
      row1.setSpacing(5);
      row1.setAlignment(Pos.CENTER_LEFT);
      row1.setPadding(new Insets(5,0,0,5));
      
      Label title = new Label("Streaming");
      
      Label tivo_label = new Label();
      
      tivo = new ChoiceBox<String>();
      tivo.valueProperty().addListener(new ChangeListener<String>() {
         @Override public void changed(ObservableValue<? extends String> ov, String oldVal, String newVal) {
            if (newVal != null && config.gui.remote_gui != null) {
                // Refresh channel list only if not inside a folder
                tab.clear();
                String tivoName = newVal;
                config.gui.remote_gui.updateButtonStates(tivoName, "Stream");
                if (tab.tivo_data.containsKey(tivoName))
                   tab.AddRows(tivoName, tab.tivo_data.get(tivoName));
            }
         }
      });
      tivo.setTooltip(tooltip.getToolTip("tivo_stream"));
      
      refresh = new Button("Refresh");
      refresh.setTooltip(tooltip.getToolTip("refresh_stream"));
      refresh.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            // At top level => Update current folder contents
            final String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               tab.clear();
               Task<Void> task = new Task<Void>() {
                  @Override public Void call() {
                     log.warn("Refreshing Streaming entries...");
                     jobData job = new jobData();
                     job.source        = tivoName;
                     job.tivoName      = tivoName;
                     job.type          = "remote";
                     job.name          = "Remote";
                     job.remote_stream = true;
                     job.stream        = tab;
                     jobMonitor.submitNewJob(job);
                     return null;
                  }
               };
               new Thread(task).start();
            }
         }
      });
      
      remove = new Button("Remove");
      remove.setTooltip(tooltip.getToolTip("remove_stream"));
      remove.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            tab.removeButtonCB();
         }
      });
      
      row1.getChildren().add(title);
      row1.getChildren().add(tivo_label);
      row1.getChildren().add(tivo);
      row1.getChildren().add(refresh);
      row1.getChildren().add(remove);
      
      tab = new streamTable();
      VBox.setVgrow(tab.TABLE, Priority.ALWAYS); // stretch vertically
        
      panel = new VBox();
      panel.setSpacing(1);
      panel.setPadding(new Insets(0,0,0,5));
      panel.getChildren().addAll(row1, tab.TABLE);      
   }
}
