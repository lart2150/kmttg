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

import java.util.Optional;

import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.TwoWayHashmap;
import com.tivo.kmttg.util.log;

public class recordOptions {
   VBox components;
   Label label;
   ChoiceBox<String> record, number, until, start, stop;
   CheckBox anywhere;
   TwoWayHashmap<String,String> untilHash = new TwoWayHashmap<String,String>();
   TwoWayHashmap<String,Integer> startHash = new TwoWayHashmap<String,Integer>();
   TwoWayHashmap<String,Integer> stopHash = new TwoWayHashmap<String,Integer>();
   
   public recordOptions() {            
      untilHash.add("Space needed",   "fifo");
      untilHash.add("Until I delete", "forever");
      
      startHash.add("On time",          0);
      startHash.add("1 minute early",   60);
      startHash.add("2 minutes early",  120);
      startHash.add("3 minutes early",  180);
      startHash.add("4 minutes early",  240);
      startHash.add("5 minutes early",  300);
      startHash.add("10 minutes early", 600);
      
      stopHash.add("On time",          0);
      stopHash.add("1 minute late",   60);
      stopHash.add("2 minutes late",  120);
      stopHash.add("3 minutes late",  180);
      stopHash.add("4 minutes late",  240);
      stopHash.add("5 minutes late",  300);
      stopHash.add("10 minutes late", 600);
      stopHash.add("15 minutes late", 900);
      stopHash.add("30 minutes late", 1800);
      stopHash.add("60 minutes late", 3600);
      stopHash.add("90 minutes late", 5400);
      stopHash.add("180 minutes late", 10800);
      
      createComponents();      
   }
   
   private void createComponents() {
      label = new Label();
      label.setText("");
      until = new ChoiceBox<String>();
      until.getItems().addAll("Space needed", "Until I delete");
      until.setValue("Space needed");

      start = new ChoiceBox<String>();
      start.getItems().addAll(
         "On time", "1 minute early", "2 minutes early", "3 minutes early",
         "4 minutes early", "5 minutes early", "10 minutes early"
      );
      start.setValue("On time");

      stop = new ChoiceBox<String>();
      stop.getItems().addAll(
         "On time", "1 minute late", "2 minutes late", "3 minutes late",
         "4 minutes late", "5 minutes late", "10 minutes late",
         "15 minutes late", "30 minutes late", "60 minutes late",
         "90 minutes late", "180 minutes late"
      );
      stop.setValue("On time");
      anywhere = new CheckBox();
      anywhere.setText("Try scheduling on all TiVos");
      anywhere.setSelected(false);

      components = new VBox();
      components.setSpacing(5);
      components.getChildren().addAll(
         label,
         new Label("Keep until"),      until,
         new Label("Start recording"), start,
         new Label("Stop recording"),  stop,
         anywhere
      );
   }
   
   @SuppressWarnings("static-access")
   public JSONObject promptUser(String title, JSONObject json) {
      try {
         if (json != null)
            setValues(json);
         label.setText(title);
         Dialog<?> dialog = new Dialog<>();
         dialog.initOwner(config.gui.getFrame());
         config.gui.LoadIcons((Stage) dialog.getDialogPane().getScene().getWindow());
         config.gui.setFontSize(dialog, config.FontSize);
         dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
         dialog.setTitle("Recording Options");
         dialog.getDialogPane().setContent(components);
         Optional<?> response = dialog.showAndWait();
         if (response != null && response.get().equals(ButtonType.OK)) {
            // NOTE: Make a copy of json so we don't change existing one
            JSONObject j;
            if (json == null)
               j = new JSONObject();
            else
               j = new JSONObject(json.toString());
            j.put("keepBehavior",     untilHash.getV((String)until.getValue()));
            j.put("startTimePadding", startHash.getV((String)start.getValue()));
            j.put("endTimePadding",   stopHash.getV((String)stop.getValue()));
            if (anywhere.isSelected())
               j.put("_anywhere_", "true");
            return j;
         } else {
            return null;
         }
      } catch (JSONException e) {
         log.error("recordOptions.promptUser - " + e.getMessage());
         return null;
      }
   }
   
   public void setValues(JSONObject json) {
      try {
         if(json.has("keepBehavior"))
            until.setValue(untilHash.getK(json.getString("keepBehavior")));
         if(json.has("startTimePadding"))
            start.setValue(startHash.getK(json.getInt("startTimePadding")));
         else if(json.has("requestedStartPadding"))
            start.setValue(startHash.getK(json.getInt("requestedStartPadding")));
         if(json.has("endTimePadding"))
            stop.setValue(stopHash.getK(json.getInt("endTimePadding")));
         else if(json.has("requestedEndPadding"))
            stop.setValue(stopHash.getK(json.getInt("requestedEndPadding")));
         if (json.has("anywhere")) {
            if (json.getString("anywhere").equals("true"))
               anywhere.setSelected(true);
            else
               anywhere.setSelected(false);
         }
      } catch (JSONException e) {
         log.error("recordOptions.setValues - " + e.getMessage());
      }
   }
   
   public JSONObject getValues() {
      JSONObject json = new JSONObject();
      try {
         json.put("keepBehavior", untilHash.getV((String)until.getValue()));
         json.put("startTimePadding", startHash.getV((String)start.getValue()));
         json.put("endTimePadding", stopHash.getV((String)stop.getValue()));
         if (anywhere.isSelected())
            json.put("anywhere", "true");
         else
            json.put("anywhere", "false");
      } catch (JSONException e) {
         log.error("recordOptions.getValues - " + e.getMessage());
         return null;
      }
      return json;
   }
}
