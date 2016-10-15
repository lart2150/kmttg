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

import java.io.File;

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
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.todoTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.log;

public class todo {
   public VBox panel = null;
   public todoTable tab = null;
   public ChoiceBox<String> tivo = null;
   public Label label = null;
   public Button cancel = null;
   public Button modify = null;
   
   public todo(final Stage frame) {
      // ToDo Tab items            
      HBox row1 = new HBox();
      row1.setSpacing(5);
      row1.setPadding(new Insets(5,0,0,5));
      row1.setAlignment(Pos.CENTER_LEFT);
      
      Label title = new Label("ToDo list");
      
      Label tivo_label = new Label();
      
      tivo = new ChoiceBox<String>();
      tivo.valueProperty().addListener(new ChangeListener<String>() {
         @Override public void changed(ObservableValue<? extends String> ov, String oldVal, String newVal) {
            if (newVal != null && config.gui.remote_gui != null) {
                TableUtil.clear(tab.TABLE);
                label.setText("");
                String tivoName = newVal;
                config.gui.remote_gui.updateButtonStates(tivoName, "ToDo");
                if (tab.tivo_data.containsKey(tivoName))
                   tab.AddRows(tivoName, tab.tivo_data.get(tivoName));
            }
         }
      });
      tivo.setTooltip(tooltip.getToolTip("tivo_todo"));

      Button refresh = new Button("Refresh");
      refresh.setTooltip(tooltip.getToolTip("refresh_todo"));
      refresh.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            // Refresh to do list
            TableUtil.clear(tab.TABLE);
            label.setText("");
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               jobData job = new jobData();
               job.source      = tivoName;
               job.tivoName    = tivoName;
               job.type        = "remote";
               job.name        = "Remote";
               job.remote_todo = true;
               job.todo        = tab;
               jobMonitor.submitNewJob(job);
            }
         }
      });

      cancel = new Button("Cancel");
      cancel.setTooltip(tooltip.getToolTip("cancel_todo"));
      cancel.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            tab.DeleteCB();
         }
      });

      modify = new Button("Modify");
      modify.setTooltip(tooltip.getToolTip("modify_todo"));
      modify.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               tab.recordSingle(tivoName);
            }
         }
      });

      Button export = new Button("Export ...");
      export.setTooltip(tooltip.getToolTip("export_todo"));
      export.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            final String tivoName = tivo.getValue();
            config.gui.remote_gui.Browser.getExtensionFilters().clear();
            config.gui.remote_gui.Browser.getExtensionFilters().addAll(new ExtensionFilter("CSV Files", "*.csv"));
            config.gui.remote_gui.Browser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ALL FILES", "*"));
            config.gui.remote_gui.Browser.setTitle("Save to file");
            config.gui.remote_gui.Browser.setInitialDirectory(new File(config.programDir));
            config.gui.remote_gui.Browser.setInitialFileName(tivoName + "_" + TableUtil.currentYearMonthDay() + ".csv");
            final File selectedFile = config.gui.remote_gui.Browser.showSaveDialog(frame);
            if (selectedFile != null) {
               Task<Void> task = new Task<Void>() {
                  @Override public Void call() {
                     log.warn("Exporting '" + tivoName + "' todo list to csv file: " + selectedFile.getAbsolutePath());
                     Remote r = config.initRemote(tivoName);
                     if (r.success) {
                        r.TodoExportCSV(selectedFile);
                        r.disconnect();
                     }
                     return null;
                  }
               };
               new Thread(task).start();
            }
         }
      });
      
      Button trim = new Button("Select repeats");
      trim.setTooltip(tooltip.getToolTip("trim_todo"));
      trim.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            final String tivoName = tivo.getValue();
            if (tivoName != null)
               tab.trimRepeats(tivoName);
         }
      });
      
      label = new Label();
      
      row1.getChildren().add(title);
      row1.getChildren().add(tivo_label);
      row1.getChildren().add(tivo);
      row1.getChildren().add(refresh);
      row1.getChildren().add(cancel);
      row1.getChildren().add(modify);
      row1.getChildren().add(export);
      row1.getChildren().add(trim);
      row1.getChildren().add(label);
      
      tab = new todoTable();
      VBox.setVgrow(tab.TABLE, Priority.ALWAYS); // stretch vertically
            
      panel = new VBox();
      panel.setSpacing(1);
      panel.getChildren().addAll(row1, tab.TABLE);
   }
}
