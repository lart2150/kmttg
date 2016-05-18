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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.Optional;
import java.util.TimeZone;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.log;

public class mRecordOptions {
   GridPane components;
   ChoiceBox<String> often, day, channel;
   ChoiceBox<String> start_hour, start_min, start_ampm, dur_hour, dur_min;
   CheckBox mon,tue,wed,thu,fri,sat,sun;
   String tivoName;
   Hashtable<String,JSONObject> channelHash = new Hashtable<String,JSONObject>();
   
   public mRecordOptions() {
      createComponents();      
   }
   
   private void createComponents() {
      often = new ChoiceBox<String>();
      often.getItems().addAll("Once", "Repeat");
      often.valueProperty().addListener(new ChangeListener<String>() {
         @Override public void changed(ObservableValue<? extends String> ov, String oldVal, String newVal) {
            if (newVal != null) {
               if (newVal.equals("Once"))
                  enableDays(false);
               else
                  enableDays(true);
            }
         }
      });
      often.setValue("Once");
      channel = new ChoiceBox<String>();
      start_hour = new ChoiceBox<String>();
      start_min = new ChoiceBox<String>();
      start_ampm = new ChoiceBox<String>();
      dur_hour = new ChoiceBox<String>();
      dur_min = new ChoiceBox<String>();
      
      day = new ChoiceBox<String>();
      mon = new CheckBox("Mon");
      tue = new CheckBox("Tue");
      wed = new CheckBox("Wed");
      thu = new CheckBox("Thu");
      fri = new CheckBox("Fri");
      sat = new CheckBox("Sat");
      sun = new CheckBox("Sun");
      
      dur_hour.getItems().add("00");
      for (int hour=1; hour<=12; hour++) {
         start_hour.getItems().add(String.format("%02d", hour));
         dur_hour.getItems().add(String.format("%02d", hour));
      }
      start_hour.setValue(start_hour.getItems().get(0));
      dur_hour.setValue(dur_hour.getItems().get(0));
      for (int min=0; min<=55; min += 5) {
         start_min.getItems().add(String.format("%02d", min));
         dur_min.getItems().add(String.format("%02d", min));
      }
      start_min.setValue(start_min.getItems().get(0));
      dur_min.setValue(dur_min.getItems().get(0));
      start_ampm.getItems().addAll("pm", "am");
      start_ampm.setValue(start_ampm.getItems().get(0));
      dur_hour.setValue("01");
      
      HBox days_panel = new HBox();
      days_panel.setSpacing(5);
      days_panel.getChildren().addAll(mon, tue, wed, thu, fri, sat, sun);
      enableDays(false);
      
      components = new GridPane();
      components.setHgap(5); components.setVgap(5);
      components.add(new Label("How Often"), 0, 0);
      components.add(often, 1, 0);
      components.add(new Label("Day"), 0, 1);
      components.add(day, 1, 1);
      components.add(new Label("Repeat"), 0, 2);
      components.add(days_panel, 1, 2);
      components.add(new Label("Channel"), 0, 3);
      components.add(channel, 1, 3);
      
      HBox h1 = new HBox();
      h1.setSpacing(5);
      h1.getChildren().addAll(start_hour, new Label(":"), start_min, start_ampm);
            
      components.add(new Label("Start Time"), 0, 4);
      components.add(h1, 1, 4);
      
      HBox h2 = new HBox();
      h2.setSpacing(5);
      h2.getChildren().addAll(dur_hour, new Label(":"), dur_min);
      
      components.add(new Label("Duration"), 0, 5);
      components.add(h2, 1, 5);            
   }
   
   private void enableDays(Boolean state) {
      if (day == null) return;
      day.setDisable(state);
      mon.setDisable(!state);
      tue.setDisable(!state);
      wed.setDisable(!state);
      thu.setDisable(!state);
      fri.setDisable(!state);
      sat.setDisable(!state);
      sun.setDisable(!state);
   }
   
   @SuppressWarnings("static-access")
   public void promptUser(String tivoName) {
      this.tivoName = tivoName;
      try {
         // Update list of channels in background mode
         getChannels(tivoName);
         
         // Update dates
         getDates();

         // Show dialog and get user response
         Dialog<?> dialog = new Dialog<>();
         dialog.initOwner(config.gui.getFrame());
         config.gui.LoadIcons((Stage) dialog.getDialogPane().getScene().getWindow());
         config.gui.setFontSize(dialog, config.FontSize);
         dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
         dialog.setTitle("Manual Recording - " + tivoName);
         dialog.getDialogPane().setContent(components);
         Optional<?> response = dialog.showAndWait();
         if (response != null && response.get().equals(ButtonType.OK)) {
            JSONObject json = getSettings();
            if (json != null)
               processResponse(json);
         }
      } catch (Exception e) {
         log.error("mRecordOptions.promptUser - " + e.getMessage());
      }
   }
   
   // Return time rounded down to nearest hour in nice display format
   private String getDisplayTime(long gmt) {
      SimpleDateFormat sdf = new SimpleDateFormat("E MM/dd/yyyy");
      return sdf.format(gmt);
   }
   
   private void getDates() {
      int numDays = 30;
      day.getItems().clear();
      long gmt = new Date().getTime();
      long increment = 24*60*60*1000;
      long stop = gmt + (long)numDays*increment;
      long time = gmt;
      while (time <= stop) {
         day.getItems().add(getDisplayTime(time));
         time += increment;
      }
      day.setValue(day.getItems().get(0));
   }
   
   private void getChannels(final String tivoName) {
      Task<Void> task = new Task<Void>() {
         @Override public Void call() {
            log.warn("Getting channel list for '" + tivoName + "'");
            Remote r = config.initRemote(tivoName);
            if (r.success) {
               final JSONArray channels = r.ChannelList(null, true);
               r.disconnect();
               if (channels != null) {
                  Platform.runLater(new Runnable() {
                     @Override public void run() {
                        try {
                           // Clear current list
                           channel.getItems().clear();
                           channelHash.clear();
                           
                           for (int i=0; i<channels.length(); ++i) {
                              JSONObject json = channels.getJSONObject(i);
                              String item = "";
                              if (json.has("channelNumber")) {
                                 item += json.getString("channelNumber");
                              }
                              if (json.has("callSign")) {
                                 item += " " + json.getString("callSign");
                              }
                              channelHash.put(item, json);
                              channel.getItems().add(item);
                           }
                           if (channel.getItems().size() > 0)
                              channel.setValue(channel.getItems().get(0));
                        } catch (JSONException e) {
                           log.error("mRecordOptions.getChannels error - " + e.getMessage());
                        }
                     }
                  });
               }
            }
            return null;
         }
      };
      new Thread(task).start();
   }
   
   private JSONObject getSettings() {
      // The returned JSONObject is idSetSource for Remote Command "Manual" call
      try {
         JSONObject json = new JSONObject();
         int hour = Integer.parseInt((String)start_hour.getValue());
         String ampm = (String)start_ampm.getValue();
         if (ampm.equals("pm") && hour < 12)
            hour += 12;
         String min = (String)start_min.getValue();
         
         if (often.getValue().equals("Once")) {
            json.put("type", "singleTimeChannelSource");
            // "time" needs to be in format: 2012-11-16 09:30:00
            // NOTE: Must also convert from local TZ to UTC
            String dayString = (String)day.getValue(); // Sun 05/04/2014
            dayString = dayString.substring(4);
            String d[] = dayString.split("/");
            String month = d[0];
            String mday = d[1];
            String year = d[2];
            String dateString = String.format("%s-%s-%s %02d:%s:00",year,month,mday,hour,min);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            sdf.setTimeZone(TimeZone.getDefault());
            Date d1 = sdf.parse(dateString);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            json.put("time", sdf.format(d1.getTime()));
         }
         else {
            // Repeat recording
            json.put("type", "repeatingTimeChannelSource");
            JSONArray dayOfWeek = new JSONArray();
            if (mon.isSelected())
               dayOfWeek.put("monday");
            if (tue.isSelected())
               dayOfWeek.put("tuesday");
            if (wed.isSelected())
               dayOfWeek.put("wednesday");
            if (thu.isSelected())
               dayOfWeek.put("thursday");
            if (fri.isSelected())
               dayOfWeek.put("friday");
            if (sat.isSelected())
               dayOfWeek.put("saturday");
            if (sun.isSelected())
               dayOfWeek.put("sunday");
            if (dayOfWeek.length() > 0)
               json.put("dayOfWeek", dayOfWeek);
            else {
               log.error("You must enable at least one day of the week for repeat recording.");
               return null;
            }
            json.put("timeOfDayLocal", String.format("%02d:%s:00",hour,min));
         }
         int duration = 0;
         duration += Integer.parseInt((String)dur_hour.getValue())*60*60;
         duration += Integer.parseInt((String)dur_min.getValue())*60;
         if (duration == 0) {
            log.error("Manual record duration must be > 0");
            return null;
         }
         json.put("duration", duration);
         json.put("channel", channelHash.get((String)channel.getValue()));
         return json;
      } catch (Exception e) {
         log.error("mRecordOptions.getSettings error - " + e.getMessage());
      }
      return null;
   }
   
   private void processResponse(final JSONObject json) {
      Task<Void> task = new Task<Void>() {
         @Override public Void call() {
            log.warn("Scheduling manual recording for '" + tivoName + "'");
            Remote r = config.initRemote(tivoName);
            if (r.success) {
               try {
                  JSONObject result = r.Command("Manual", json);
                  if (result != null)
                     log.print(result.toString(3));
               } catch (JSONException e) {
                  log.error("mRecordOptions.processResponse error - " + e.getMessage());
               }
               r.disconnect();
            }
            return null;
         }
      };
      new Thread(task).start();
   }
   
}
