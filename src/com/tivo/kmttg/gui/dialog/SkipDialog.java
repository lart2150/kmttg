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
package com.tivo.kmttg.gui.dialog;

import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.MyTooltip;
import com.tivo.kmttg.gui.gui;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.skipTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.SkipManager;
import com.tivo.kmttg.util.log;

public class SkipDialog {
   private Stage frame = null;
   private Stage dialog = null;
   private skipTable tab = null;
   private JSONArray data = null;
   
   public SkipDialog(Stage frame) {
      this.frame = frame;      
      getEntries();
   }
   
   // Retrieve entries from AutoSkip.ini file
   private void getEntries() {
      if (tab != null)
         tab.clear();
      data = SkipManager.getEntries();      
      if (data != null && data.length() > 0) {
         if (dialog == null)
            init();
         else
            tab.AddRows(data);
      } else {
         log.warn("No data available to display");
      }
   }
   
   private void removeEntries(JSONArray entries) {
      // Run in separate background thread
      class backgroundRun extends Task<Void> {
         JSONArray entries;

         public backgroundRun(JSONArray entries) {
            this.entries = entries;
         }
         @Override
         protected Void call() {
            try {
               for (int i=0; i<entries.length(); ++i) {
                  JSONObject json = entries.getJSONObject(i);
                  SkipManager.removeEntry(json.getString("contentId"));
               }
            } catch (Exception e) {
               log.error("removeEntries - " + e.getMessage());
               return null;
            }
            return null;
         }
      }
      backgroundRun b = new backgroundRun(entries);
      new Thread(b).start();
   }
         
   private void init() {
      // Define content for dialog window
      VBox content = new VBox();

      // Refresh button
      Button refresh = new Button("Refresh");
      String tip = "<b>Refresh</b><br>Get list of AutoSkip entries and refresh table.";
      refresh.setTooltip(MyTooltip.make(tip));
      refresh.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            getEntries();
         }
      });

      // Remove button
      Button remove = new Button("Remove");
      tip = "<b>Remove</b><br>Remove selected entry in the table from AutoSkip file.";
      remove.setTooltip(MyTooltip.make(tip));
      remove.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            int[] selected = TableUtil.GetSelectedRows(tab.getTable());
            if (selected == null || selected.length != 1) {
               log.error("Must select a single table row.");
               return;
            }
            JSONArray entries = new JSONArray();
            int row = selected[0];
            JSONObject json = tab.GetRowData(row);
            if (json != null)
               entries.put(json);
            tab.RemoveRow(row);
            if (entries.length() > 0)
               removeEntries(entries);
         }
      });
      
      // Row 1 = buttons
      HBox row1 = new HBox();
      row1.setSpacing(5);
      row1.getChildren().addAll(refresh, remove);
      content.getChildren().add(row1);
      
      // Table
      tab = new skipTable();
      VBox.setVgrow(tab.TABLE, Priority.ALWAYS); // stretch vertically
      tab.AddRows(data);
      content.getChildren().add(tab.TABLE);
      VBox.setVgrow(content, Priority.ALWAYS); // stretch vertically

      dialog = new Stage();
      dialog.initOwner(frame);
      gui.LoadIcons(dialog);
      dialog.setTitle("AutoSkip Entries");
      Scene scene = new Scene(new VBox());
      config.gui.setFontSize(scene, config.FontSize);
      ((VBox) scene.getRoot()).getChildren().add(content);
      dialog.setScene(scene);
      dialog.setWidth(frame.getWidth()/1.2);
      dialog.setHeight(frame.getHeight()/3);
      dialog.show();      
   }
}
