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

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.MyTooltip;
import com.tivo.kmttg.gui.gui;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.pushTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.log;

public class Pushes {
   private Stage frame = null;
   private Stage dialog = null;
   private pushTable tab = null;
   private JSONArray data = null;
   private String tivoName = null;
   
   public Pushes(String tivoName, Stage frame) {
      this.tivoName = tivoName;
      this.frame = frame;      
      getPushes();
   }
   
   // Retrieve queue data from TiVo mind server
   private void getPushes() {
      // Run in separate background thread
      Task<Void> task = new Task<Void>() {
         @Override public Void call() {
            if (tab != null)
               tab.clear();
            data = new JSONArray();
            Remote r = new Remote(tivoName, true);
            if (r.success) {
               try {            
                  JSONObject json = new JSONObject();
                  json.put("bodyId", r.bodyId_get());
                  json.put("noLimit", true);
                  json.put("levelOfDetail", "low");
                  JSONObject result = r.Command("downloadSearch", json);
                  if (result != null && result.has("download")) {
                     JSONArray a = result.getJSONArray("download");
                     for (int i=0; i<a.length(); ++i) {
                        JSONObject d = a.getJSONObject(i);
                        if (d.has("state")) {
                           String state = d.getString("state");
                           if (state.equals("scheduled") || state.equals("inProgress")) {
                              JSONObject j = new JSONObject();
                              j.put("bodyId", json.getString("bodyId"));
                              j.put("levelOfDetail", "high");
                              j.put("offerId", d.getString("offerId"));
                              JSONObject detail = r.Command("offerSearch", j);
                              if (detail != null && detail.has("offer")) {
                                 JSONObject o = detail.getJSONArray("offer").getJSONObject(0);
                                 o.put("bodyId", json.getString("bodyId"));
                                 data.put(o);
                              }
                           }
                        }
                     }
                  }
               } catch (Exception e) {
                  e.printStackTrace();
               }
               r.disconnect();
            }
            
            if (data != null && data.length() > 0) {
               Platform.runLater(new Runnable() {
                  @Override public void run() {
                     if (dialog == null)
                        init();
                     else
                        tab.AddRows(data);
                  }
               });
            } else {
               log.warn(tivoName + ": No pending pushes found to display");
            }
            return null;
         }
      };
      new Thread(task).start();
   }
   
   private void removePushes(JSONArray entries) {
      // Run in separate background thread
      class backgroundRun extends Task<Void> {
         JSONArray entries;

         public backgroundRun(JSONArray entries) {
            this.entries = entries;
         }
         @Override
         protected Void call() {
            try {
               Remote r = new Remote(tivoName, true);
               if (r.success) {
                  for (int i=0; i<entries.length(); ++i) {
                     JSONObject json = new JSONObject();
                     json.put("bodyId", r.bodyId_get());
                     json.put("state", "cancelled");
                     json.put("cancellationReason", "userStoppedTransfer");
                     json.put("offerId", entries.getJSONObject(i).getString("offerId"));
                     //json.put("downloadId", entries.getJSONObject(i).getString("downloadId"));
                     JSONObject result = r.Command("downloadModify", json);
                     if (result != null) {
                        log.print(result.toString(3));
                     } else {
                        log.error("push item remove failed");
                        return null;
                     }
                  }
                  r.disconnect();
              }
            } catch (Exception e) {
               log.error("removePushes - " + e.getMessage());
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
      String tip = "<b>Refresh</b><br>Query queued pushes and refresh table.<br>";
      tip += "NOTE: The mind server listings can be several seconds off compared to what is currently happening.";
      refresh.setTooltip(MyTooltip.make(tip));
      refresh.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            getPushes();
         }
      });

      // Remove button
      Button remove = new Button("Remove");
      tip = "<b>Remove</b><br>Attempt to remove selected entry in the table from push queue.<br>";
      tip += "NOTE: This will not cancel pushes already in progress or very close to starting.<br>";
      tip += "NOTE: The response to this operation from mind server is always 'success' so there<br>";
      tip += "is no guarantee that removing an entry actually works or not.";
      remove.setTooltip(MyTooltip.make(tip));
      remove.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            JSONArray entries = new JSONArray();
            Boolean cont = true;
            while (cont) {
               int[] selected = TableUtil.GetSelectedRows(tab.getTable());
               if (selected.length > 0) {
                  int row = selected[0];
                 JSONObject json = tab.GetRowData(row);
                  if (json != null)
                     entries.put(json);
                  tab.RemoveRow(row);
               } else {
                  cont = false;
               }
            }
            if (entries.length() > 0)
               removePushes(entries);
         }
      });
      
      // Row 1 = 2 buttons
      HBox row1 = new HBox();
      row1.getChildren().addAll(refresh, remove);
      content.getChildren().add(row1);
      
      // Table
      tab = new pushTable();
      tab.AddRows(data);
      ScrollPane tabScroll = new ScrollPane(tab.getTable());
      content.getChildren().add(tabScroll);
      tabScroll.setFitToHeight(true);
      tabScroll.setFitToWidth(true);

      dialog = new Stage();
      dialog.initOwner(frame);
      gui.LoadIcons(dialog);
      dialog.setTitle("Push Queue");
      Scene scene = new Scene(new VBox());
      config.gui.setFontSize(scene, config.FontSize);
      ((VBox) scene.getRoot()).getChildren().add(content);
      dialog.setScene(scene);
      dialog.setWidth(frame.getWidth());
      dialog.setHeight(frame.getHeight()/3);
      dialog.show();      
   }
}
