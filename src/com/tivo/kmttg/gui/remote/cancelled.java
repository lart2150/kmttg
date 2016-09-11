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

import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.cancelledTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.log;

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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class cancelled {
   public VBox panel = null;
   public cancelledTable tab = null;
   public ChoiceBox<String> tivo = null;
   public Button refresh = null;
   public Button autoresolve = null;
   public CheckBox includeHistory = null;
   public Button record = null;
   public Button explain = null;

   public cancelled(final Stage frame) {
      
      // Cancelled table items            
      HBox row1 = new HBox();
      row1.setSpacing(5);
      row1.setAlignment(Pos.CENTER_LEFT);
      row1.setPadding(new Insets(5,0,0,5));
      
      Label title = new Label("Not Record list");
      
      Label tivo_label = new Label();
      
      tivo = new ChoiceBox<String>();
      tivo.valueProperty().addListener(new ChangeListener<String>() {
         @Override public void changed(ObservableValue<? extends String> ov, String oldVal, String newVal) {
            if (newVal != null && config.gui.remote_gui != null) {
               config.gui.remote_gui.updateButtonStates(newVal, "Won't Record");
            }
         }
      });
      tivo.setTooltip(tooltip.getToolTip("tivo_cancel"));

      refresh = new Button("Refresh");
      refresh.setTooltip(tooltip.getToolTip("refresh_cancel_top"));
      refresh.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            // Refresh will not record list
            tab.clear();
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               jobData job = new jobData();
               job.source        = tivoName;
               job.tivoName      = tivoName;
               job.type          = "remote";
               job.name          = "Remote";
               job.remote_cancel = true;
               job.cancelled     = tab;
               jobMonitor.submitNewJob(job);
            }
         }
      });

      record = new Button("Record");
      record.setTooltip(tooltip.getToolTip("record_cancel"));
      record.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0)
               tab.recordSingle(tivoName);
         }
      });

      explain = new Button("Explain");
      explain.setTooltip(tooltip.getToolTip("explain_cancel"));
      explain.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               int selected[] = TableUtil.GetSelectedRows(tab.TABLE);
               if (selected.length > 0) {
                  tab.getConflictDetails(tivoName, selected[0]);
               }
            }
         }
      });

      Button refresh_todo = new Button("Refresh ToDo");
      refresh_todo.setTooltip(tooltip.getToolTip("refresh_todo"));
      refresh_todo.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               Task<Void> task = new Task<Void>() {
                  @Override public Void call() {
                     log.warn("Refreshing ToDo list for Will Not Record matches...");
                     util.all_todo = util.getTodoLists();
                     log.warn("Refresh ToDo list for Will Not Record matches completed.");
                     return null;
                  }
               };
               new Thread(task).start();
            }
         }
      });
      
      autoresolve = new Button("Autoresolve");
      autoresolve.setTooltip(tooltip.getToolTip("autoresolve"));
      autoresolve.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            autoresolve.setDisable(true);
            Task<Void> task = new Task<Void>() {
               @Override public Void call() {
                  rnpl.AutomaticConflictsHandler();
                  autoresolve.setDisable(false);
                  return null;
               }
            };
            new Thread(task).start();
         }
      });
      
      includeHistory = new CheckBox("Include History");
      includeHistory.setSelected(false);
      includeHistory.setTooltip(tooltip.getToolTip("includeHistory_cancel"));
      
      row1.getChildren().add(title);
      row1.getChildren().add(tivo_label);
      row1.getChildren().add(tivo);
      row1.getChildren().add(refresh);
      row1.getChildren().add(record);
      row1.getChildren().add(explain);
      row1.getChildren().add(refresh_todo);
      row1.getChildren().add(autoresolve);
      row1.getChildren().add(includeHistory);
      
      tab = new cancelledTable();
      VBox.setVgrow(tab.TABLE, Priority.ALWAYS); // stretch vertically
      
      panel = new VBox();
      panel.setSpacing(1);
      panel.setPadding(new Insets(0,0,0,5));      
      panel.getChildren().addAll(row1, tab.TABLE);
      
   }
}
