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
import java.util.Optional;
import java.util.Stack;

import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.channelsTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
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
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.FileChooser.ExtensionFilter;

public class channels {
   public VBox panel = null;
   public channelsTable tab = null;
   public Button refresh = null;
   public Button copy = null;
   public Button update = null;
   public Label label = null;
   public ChoiceBox<String> tivo = null;
   
   public channels(final Stage frame) {
      
      // Channels tab items      
      HBox row1 = new HBox();
      row1.setSpacing(5);
      row1.setAlignment(Pos.CENTER_LEFT);
      row1.setPadding(new Insets(5,0,0,5));
      
      Label title = new Label("Channels");
      
      Label tivo_label = new Label();
      
      tivo = new ChoiceBox<String>();
      tivo.valueProperty().addListener(new ChangeListener<String>() {
         @Override public void changed(ObservableValue<? extends String> ov, String oldVal, String newVal) {
            if (newVal != null && config.gui.remote_gui != null) {                
               // TiVo selection changed for Channels tab
               TableUtil.clear(tab.TABLE);
               label.setText("");
               String tivoName = newVal;
               config.gui.remote_gui.updateButtonStates(tivoName, "Channels");
               if (tab.tivo_data.containsKey(tivoName))
                  tab.AddRows(tivoName, tab.tivo_data.get(tivoName));
               tab.updateLoadedStatus();
            }
         }
      });
      tivo.setTooltip(tooltip.getToolTip("tivo_channels"));

      Button save = new Button("Save...");
      save.setTooltip(tooltip.getToolTip("save_channels"));
      save.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            // Save channels list
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               if (tab.isTableLoaded()) {
                  log.error("Cannot save loaded Channels");
                  return;
               }  else {
                  config.gui.remote_gui.Browser.getExtensionFilters().clear();
                  config.gui.remote_gui.Browser.getExtensionFilters().addAll(new ExtensionFilter("Channel Files", "*.chan"));
                  config.gui.remote_gui.Browser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ALL FILES", "*"));
                  config.gui.remote_gui.Browser.setTitle("Save to file");
                  config.gui.remote_gui.Browser.setInitialDirectory(new File(config.programDir));
                  config.gui.remote_gui.Browser.setInitialFileName(tivoName + ".chan");
                  final File selectedFile = config.gui.remote_gui.Browser.showSaveDialog(frame);
                  if (selectedFile != null) {
                     tab.saveChannels(tivoName, selectedFile.getAbsolutePath());
                  }
               }
            }
         }
      });

      Button load = new Button("Load...");
      load.setTooltip(tooltip.getToolTip("load_channels"));
      load.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            // Load channels list
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               config.gui.remote_gui.Browser.getExtensionFilters().clear();
               config.gui.remote_gui.Browser.getExtensionFilters().addAll(new ExtensionFilter("Channel Files", "*.chan"));
               config.gui.remote_gui.Browser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ALL FILES", "*"));
               config.gui.remote_gui.Browser.setTitle("Load channels file");
               config.gui.remote_gui.Browser.setInitialDirectory(new File(config.programDir));
               config.gui.remote_gui.Browser.setInitialFileName(tivoName + ".chan");
               final File selectedFile = config.gui.remote_gui.Browser.showOpenDialog(frame);
               if (selectedFile != null) {
                  label.setText("");
                  tab.loadChannels(selectedFile.getAbsolutePath());
               }
            }
         }
      });
      
      Button export_channels = new Button("Export ...");
      export_channels.setTooltip(tooltip.getToolTip("export_channels"));
      export_channels.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            final String tivoName = tivo.getValue();
            config.gui.remote_gui.Browser.getExtensionFilters().clear();
            config.gui.remote_gui.Browser.getExtensionFilters().addAll(new ExtensionFilter("CSV Files", "*.csv"));
            config.gui.remote_gui.Browser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ALL FILES", "*"));
            config.gui.remote_gui.Browser.setTitle("Save to file");
            config.gui.remote_gui.Browser.setInitialDirectory(new File(config.programDir));
            config.gui.remote_gui.Browser.setInitialFileName(tivoName + "_channels.csv");
            final File selectedFile = config.gui.remote_gui.Browser.showSaveDialog(frame);
            if (selectedFile != null) {
               Task<Void> task = new Task<Void>() {
                  @Override public Void call() {
                     log.warn("Exporting '" + tivoName + "' channel list to csv file: " + selectedFile.getAbsolutePath());
                     Remote r = config.initRemote(tivoName);
                     if (r.success) {
                        r.ChannelLineupCSV(selectedFile);
                        r.disconnect();
                     }
                     return null;
                  }
               };
               new Thread(task).start();
            }
         }
      });

      copy = new Button("Copy");
      copy.setTooltip(tooltip.getToolTip("copy_channels"));
      copy.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            // Copy selected channel settings to a TiVo
            // Build list of eligible TiVos
            String thisTivo = tivo.getValue();
            Stack<String> all = config.getTivoNames();
            for (int i=0; i<all.size(); ++i) {
               String tivo = all.get(i);
               if (! config.rpcEnabled(tivo) && ! config.mindEnabled(tivo)) {
                  all.remove(i);
                  continue;
               }
               if (! config.nplCapable(tivo)) {
                  all.remove(i);
                  continue;
               }
            }
            
            // Prompt user to choose a TiVo
            ChoiceDialog<String> dialog = new ChoiceDialog<String>(all.get(0), all);
            dialog.setTitle("Copy To");
            dialog.setHeaderText("Choose which TiVo to copy to");
            dialog.setContentText("TiVo:");
            String tivoName = null;
            Optional<String> result = dialog.showAndWait();
            if (result.isPresent())
               tivoName = result.get();
            if (tivoName != null && tivoName.length() > 0) {
               if (tivoName.equals(thisTivo)) {
                  // Don't copy to self unless in loaded state
                  if (! tab.isTableLoaded()) {
                     log.error("Destination TiVo is same as source TiVo: " + tivoName);
                     return;
                  }
               }
               tab.copyChannels(tivoName);
            }
         }
      });

      refresh = new Button("Refresh");
      refresh.setTooltip(tooltip.getToolTip("refresh_channels"));
      refresh.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            // Refresh channels list
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               label.setText("");
               tab.refreshChannels(tivoName);
            }
         }
      });

      update = new Button("Modify");
      update.setTooltip(tooltip.getToolTip("update_channels"));
      update.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            // Update channels list
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0)
               label.setText("");
               tab.updateChannels(tivoName);
         }
      });
      
      label = new Label();
            
      row1.getChildren().add(title);
      row1.getChildren().add(tivo_label);
      row1.getChildren().add(tivo);
      row1.getChildren().add(refresh);
      row1.getChildren().add(save);
      row1.getChildren().add(load);
      row1.getChildren().add(export_channels);
      row1.getChildren().add(copy);
      row1.getChildren().add(update);
      row1.getChildren().add(label);
      
      tab = new channelsTable();
      VBox.setVgrow(tab.TABLE, Priority.ALWAYS); // stretch vertically
      
      panel = new VBox();
      panel.setSpacing(1);
      panel.getChildren().addAll(row1, tab.TABLE);      
   }
}
