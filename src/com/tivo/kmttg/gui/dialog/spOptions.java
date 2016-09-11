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
import java.util.Stack;
import java.util.TimeZone;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.TwoWayHashmap;
import com.tivo.kmttg.util.log;

public class spOptions {
   VBox components;
   Label label;
   ChoiceBox<String> record, channel, number, until, start, stop, include, startFrom, rentOrBuy, hd;
   TwoWayHashmap<String,String> recordHash = new TwoWayHashmap<String,String>();
   TwoWayHashmap<String,Integer> numberHash = new TwoWayHashmap<String,Integer>();
   TwoWayHashmap<String,String> untilHash = new TwoWayHashmap<String,String>();
   TwoWayHashmap<String,Integer> startHash = new TwoWayHashmap<String,Integer>();
   TwoWayHashmap<String,Integer> stopHash = new TwoWayHashmap<String,Integer>();
   TwoWayHashmap<String,String> includeHash = new TwoWayHashmap<String,String>();
   TwoWayHashmap<String,Integer> startFromHash = new TwoWayHashmap<String,Integer>();
   TwoWayHashmap<String,String> rentOrBuyHash = new TwoWayHashmap<String,String>();
   TwoWayHashmap<String,String> hdHash = new TwoWayHashmap<String,String>();
   volatile Hashtable<String,JSONObject> channelHash = new Hashtable<String,JSONObject>();
   
   public spOptions() {      
      recordHash.add("New & repeats",   "rerunsAllowed");
      recordHash.add("New only",        "firstRunOnly");
      recordHash.add("Everything", "everyEpisode");
            
      numberHash.add("1 recorded show", 1);
      numberHash.add("2 recorded shows", 2);
      numberHash.add("3 recorded shows", 3);
      numberHash.add("4 recorded shows", 4);
      numberHash.add("5 recorded shows", 5);
      numberHash.add("10 recorded shows", 10);
      numberHash.add("25 recorded shows", 25);
      numberHash.add("All shows", 0);
      
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
      
      // Include
      // idSetSource->consumptionSource
      includeHash.add("Recordings Only",               "linear");
      includeHash.add("Recordings & Streaming Videos", "all");
      includeHash.add("Streaming Only",                "onDemand");
      
      // Start From
      // Variable entries depending on 1st season available
      // idSetSource->"episodeGuideType": "season", "startSeasonOrYear": 1
      startFromHash.add("Season 1", 1);
      // idSetSource->"episodeGuideType": "none", "newOnlyDate": "2015-02-21 02:35:07" (GMT time)
      startFromHash.add("New episodes only", -1);
      // idSetSource->"episodeGuideType": "season", "startSeasonOrYear": 2      
      // startFromHash.add("Season 2", 2);
      
      // Rent or Buy
      // Only used if if includeHash != linear (Default to "free")
      // idSetSource->costFilter
      rentOrBuyHash.add("Include", "any");
      rentOrBuyHash.add("Don't Include", "free");

      // Get in HD
      // Only used if Channel="All Channels" (default to "prefer" otherwise)
      // hdPreference
      hdHash.add("If Possible", "prefer");
      hdHash.add("Always", "always");
      hdHash.add("Never", "never");
      
      createComponents();      
   }
   
   private void createComponents() {
      label = new Label();
      record = new ChoiceBox<String>();
      record.getItems().addAll(
         "New & repeats", "New only", "Everything"
      );
      record.setValue("New only");
      
      channel = new ChoiceBox<String>();
      channel.getItems().add("All");
      channel.valueProperty().addListener(new ChangeListener<String>() {
         @Override public void changed(ObservableValue<? extends String> ov, String oldVal, String newVal) {
            if (newVal != null) {
               updateStates();
            }
         }
      });
      
      number = new ChoiceBox<String>();
      number.getItems().addAll(
         "1 recorded show", "2 recorded shows", "3 recorded shows",
         "4 recorded shows", "5 recorded shows", "10 recorded shows",
         "25 recorded shows", "All shows"
      );
      number.setValue("25 recorded shows");

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
      
      include = new ChoiceBox<String>();
      include.getItems().addAll(
         "Recordings Only", "Recordings & Streaming Videos", "Streaming Only"
      );
      include.setValue(include.getItems().get(0));
      include.valueProperty().addListener(new ChangeListener<String>() {
         @Override public void changed(ObservableValue<? extends String> ov, String oldVal, String newVal) {
            if (newVal != null) {
               updateStates();
            }
         }
      });
      
      startFrom = new ChoiceBox<String>();
      startFrom.getItems().addAll(
         "Season 1", "New episodes only"
      );
      startFrom.setValue(startFrom.getItems().get(0));
      
      rentOrBuy = new ChoiceBox<String>();
      rentOrBuy.getItems().addAll(
         "Don't Include", "Include"
      );
      rentOrBuy.setValue(rentOrBuy.getItems().get(0));
      
      hd = new ChoiceBox<String>();
      hd.getItems().addAll(
         "If Possible", "Always", "Never"
      );
      hd.setValue(hd.getItems().get(0));
      
      components = new VBox();
      components.getChildren().addAll(
         label,
         new Label("Include"),         include,
         new Label("Start From"),      startFrom,
         new Label("Rent Or Buy"),     rentOrBuy,
         new Label("Record"),          record,
         new Label("Channel"),         channel,
         new Label("Get in HD"),       hd,
         new Label("Keep at most"),    number,
         new Label("Keep until"),      until,
         new Label("Start recording"), start,
         new Label("Stop recording"),  stop
      );
      
      updateStates();
   }
   
   @SuppressWarnings("static-access")
   public JSONObject promptUser(String tivoName, String title, JSONObject json, Boolean WL) {
      setChoices(WL);
      try {
         if (json != null) {
            setValues(json);
            if ( ! WL ) {
               setChannels(tivoName, json);
               setStartFrom(tivoName, json);
            } else {
               // This is a WL type
               String hdp = "prefer";
               if (json.has("hdPreference"))
                  hdp = json.getString("hdPreference");
               if (json.has("hdOnly") && json.getBoolean("hdOnly"))
                  hdp = "always";
               hd.setValue(hdHash.getK(hdp));
            }
         }
         label.setText(title);
         Dialog<?> dialog = new Dialog<>();
         dialog.initOwner(config.gui.getFrame());
         config.gui.LoadIcons((Stage) dialog.getDialogPane().getScene().getWindow());
         config.gui.setFontSize(dialog, config.FontSize);
         dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
         dialog.setTitle("Season Pass Options");
         dialog.getDialogPane().setContent(components);
         Optional<?> response = dialog.showAndWait();
         if (response != null && response.get().equals(ButtonType.OK)) {
            // NOTE: Make a copy of json so we don't change existing one
            JSONObject j;
            if (json == null)
               j = new JSONObject();
            else
               j = new JSONObject(json.toString());
            j.put("showStatus",       recordHash.getV((String)record.getValue()));
            j.put("maxRecordings",    numberHash.getV((String)number.getValue()));
            j.put("keepBehavior",     untilHash.getV((String)until.getValue()));
            j.put("startTimePadding", startHash.getV((String)start.getValue()));
            j.put("endTimePadding",   stopHash.getV((String)stop.getValue()));
            // NOTE: For WL types set consumptionSource to null
            String consumptionSource = null;
            if (! include.isDisable())
               consumptionSource = includeHash.getV((String)include.getValue());
            String hdPreference = hdHash.getV((String)hd.getValue());
            String channelName = (String)channel.getValue();
            int startSeasonOrYear = startFromHash.getV((String)startFrom.getValue());
            if (consumptionSource != null) {
               // NOTE: All types have startSeasonOrYear
               if ( consumptionSource.equals("linear") ) {
                  // Recordings only
                  JSONObject idSetSource;
                  Boolean newid = false;
                  if (j.has("idSetSource"))
                     idSetSource = j.getJSONObject("idSetSource");
                  else {
                     idSetSource = new JSONObject();
                     newid = true;
                  }
                  setSeason(idSetSource, startSeasonOrYear);
                                 
                  if (newid)
                     j.put("idSetSource", idSetSource);
               } else {
                  // Streaming elements desired
                  JSONObject idSetSource;
                  Boolean newid = false;
                  if ( j.has("idSetSource") )
                     idSetSource = j.getJSONObject("idSetSource");
                  else {
                     idSetSource = new JSONObject();
                     newid = true;
                  }
                  if (consumptionSource.equals("onDemand") && idSetSource.has("channel"))
                     idSetSource.remove("channel"); // Streaming only should not have channel in idSetSource
                  idSetSource.put("consumptionSource", consumptionSource);
                  idSetSource.put("costFilter", rentOrBuyHash.getV((String)rentOrBuy.getValue()));
                  setSeason(idSetSource, startSeasonOrYear);
                  if (newid)
                     j.put("idSetSource", idSetSource);
               }
            
               // Channel & HD preference only applies for non onDemand content
               if (! consumptionSource.equals("onDemand") && j.has("idSetSource")) {
                  JSONObject idSetSource = j.getJSONObject("idSetSource");
                  if (channelName != null && channelName.equals("All")) {
                     j.put("hdPreference", hdPreference);
                     if (idSetSource.has("channel"))
                        idSetSource.remove("channel");
                     idSetSource.put("type", "seasonPassSource");
                     idSetSource.put("consumptionSource", consumptionSource);
                     if (json != null && json.has("collectionId")) {
                        idSetSource.put("collectionId", json.getString("collectionId"));
                     }
                     idSetSource.put("costFilter", "free");
                  } else {
                     if (j.has("hdPreference"))
                        j.remove("hdPreference");
                     if (j.has("hdOnly"))
                        j.remove("hdOnly");
                  }
                  if (channelName != null && ! channelName.equals("All")) {
                     if (! channelHash.containsKey(channelName))
                        setChannelHash(tivoName, json.getString("collectionId"));
                     idSetSource.put("channel", channelHash.get(channelName));
                  }
               }
               if (consumptionSource.equals("onDemand")) {
                  String [] remove = {"hdPreference"};
                  for (String r : remove)
                     if (j.has(r))
                        j.remove(r);
               }
            } // consumptionSource != null
            else {
               // WL type
               j.put("hdPreference", hdPreference);
            }
            
            if (j.has("hdPreference")) {
               if (j.getString("hdPreference").equals("always"))
                  j.put("hdOnly", true);
               else
                  j.put("hdOnly", false);
            }
            
            String [] remove = {"__priority__", "__upcoming", "priority"};
            for (String r : remove) {
               if (j.has(r))
                  j.remove(r);
            }
            return j;
         } else {
            return null;
         }
      } catch (JSONException e) {
         log.error("spOptions.promptUser - " + e.getMessage());
         return null;
      }
   }
   
   public void setValues(JSONObject json) {
      try {
         if(json.has("showStatus"))
            record.setValue(recordHash.getK(json.getString("showStatus")));
         if(json.has("maxRecordings"))
            number.setValue(numberHash.getK(json.getInt("maxRecordings")));
         if(json.has("keepBehavior"))
            until.setValue(untilHash.getK(json.getString("keepBehavior")));
         if(json.has("startTimePadding"))
            start.setValue(startHash.getK(json.getInt("startTimePadding")));
         if(json.has("endTimePadding"))
            stop.setValue(stopHash.getK(json.getInt("endTimePadding")));
         String consumptionSource = "linear";
         String costFilter = "free";
         int startSeasonOrYear = -1;
         if(json.has("idSetSource")) {
            JSONObject id = json.getJSONObject("idSetSource");
            if(id.has("consumptionSource")) {
               consumptionSource = id.getString("consumptionSource");
               if( ! consumptionSource.equals("linear") ) {
                  if (id.has("costFilter"))
                     costFilter = id.getString("costFilter");
               }
            }
            if(id.has("startSeasonOrYear"))
               startSeasonOrYear = id.getInt("startSeasonOrYear");
         }
         if (consumptionSource.equals("linear") && startSeasonOrYear == -1)
            startSeasonOrYear = 1;
         include.setValue(includeHash.getK(consumptionSource));
         rentOrBuy.setValue(rentOrBuyHash.getK(costFilter));
         startFrom.setValue(startFromHash.getK(startSeasonOrYear));
      } catch (JSONException e) {
         log.error("spOptions.setValues - " + e.getMessage());
      }
   }
   
   public JSONObject getValues() {
      JSONObject json = new JSONObject();
      try {
         json.put("showStatus", recordHash.getV((String)record.getValue()));
         json.put("maxRecordings", numberHash.getV((String)number.getValue()));
         json.put("keepBehavior", untilHash.getV((String)until.getValue()));
         json.put("startTimePadding", startHash.getV((String)start.getValue()));
         json.put("endTimePadding", stopHash.getV((String)stop.getValue()));
      } catch (JSONException e) {
         log.error("spOptions.getValues - " + e.getMessage());
         return null;
      }
      return json;
   }
   
   // include cyclic change callback
   private void updateStates() {
      String choice = (String)include.getValue();
      String channelChoice = (String)channel.getValue();
      Boolean recording = true;
      Boolean streaming = true;
      if (choice != null && choice.equals("Streaming Only"))
         recording = false;
      if (choice != null && choice.equals("Recordings Only"))
         streaming = false;
      
      rentOrBuy.setDisable(streaming);
      record.setDisable(!recording);
      channel.setDisable(!recording);
      number.setDisable(!recording);
      until.setDisable(!recording);
      start.setDisable(!recording);
      stop.setDisable(!recording);
      
      Boolean hdenable = false;
      if (channelChoice != null && channelChoice.equals("All"))
         hdenable = true;
      if (choice != null && choice.equals("Streaming Only"))
         hdenable = false;
      hd.setDisable(!hdenable);
   }
   
   private void setChoices(Boolean WL) {
      String All = "Everything";
      String c1 = "Recordings Only";
      String c2 = "Recordings & Streaming Videos";
      String c3 = "Streaming Only";
      if (WL) {
         if( ! record.getItems().contains(All) )
            record.getItems().add(All);
         if( include.getItems().contains(c2) )
            include.getItems().remove(c2);         
         if( include.getItems().contains(c3) )
            include.getItems().remove(c3);         
         include.setValue(c1);
         updateStates();
      } else {
         if( ! record.getItems().contains(All) )
            record.getItems().add(All);
         if( ! include.getItems().contains(c2) )
            include.getItems().add(c2);
         if( ! include.getItems().contains(c3) )
            include.getItems().add(c3);
      }
      if (WL)
         hd.setDisable(false);
      include.setDisable(WL);
      startFrom.setDisable(WL);
      channel.setDisable(WL);
   }
   
   // This runs in background mode so as not to hang up GUI
   private void setChannels(final String tivoName, final JSONObject json) {
      Task<Void> task = new Task<Void>() {
         @Override public Void call() {
            Stack<String> c = new Stack<String>();
            c.push("All");
            try {
               resetChannels();
               // Set default choice
               setChannelChoice(json);
               
               String collectionId = null;
               if (json.has("collectionId"))
                  collectionId = json.getString("collectionId");
               else {
                  if (json.has("idSetSource")) {
                     JSONObject idSetSource = json.getJSONObject("idSetSource");
                     if (idSetSource.has("collectionId"))
                        collectionId = idSetSource.getString("collectionId");
                  }
               }
               if (collectionId != null) {
                  setChannelHash(tivoName, collectionId);
                  for (String channelName : channelHash.keySet())
                     c.push(channelName);
               }
            } catch (JSONException e) {
               log.error("spOptions setChannels - " + e.getMessage());
            }
            class backgroundRun implements Runnable {
               Stack<String> c;
               public backgroundRun(Stack<String> c) {
                  this.c = c;
               }
               @Override public void run() {
                  channel.getItems().clear();
                  for (Object chan : c.toArray()) {
                     channel.getItems().add((String)chan);
                  }
               }
            }
            Platform.runLater(new backgroundRun(c));
            setChannelChoice(json);
            String defaultChoice = (String)channel.getValue();
            if ( ! defaultChoice.contains("=") ) {
               // hdPreference relevant for All Channels
               if (json.has("hdPreference")) {
                  Platform.runLater(new Runnable() {
                     @Override public void run() {
                        try {
                           hd.setValue(hdHash.getK(json.getString("hdPreference")));
                        } catch (JSONException e) {
                           log.error("spOptions setChannels - " + e.getMessage());
                        }
                     }
                  });
               }
            }
            //log.warn(">> Channel choices completed");
            return null;
         } // doInBackground
      }; // backgroundRun
      new Thread(task).start();
   }
   
   private void setChannelHash(final String tivoName, final String collectionId) {
      Remote r = config.initRemote(tivoName);
      if (r.success) {
         JSONArray channels = r.channelSearch(collectionId);
         if (channels.length() > 0) {
            channelHash.clear();
            try {
               for (int i=0; i<channels.length(); ++i) {
                  JSONObject chan = channels.getJSONObject(i);
                  JSONObject j = new JSONObject();
                  j.put("channel", chan);
                  String channelName = TableUtil.makeChannelName(j);
                  channelHash.put(channelName, chan);
               }
            } catch (JSONException e) {
               log.error("setChannelHash - " + e.getMessage());
            }
         }
      }

   }
   
   // This runs in background mode so as not to hang up GUI
   private void setStartFrom(final String tivoName, final JSONObject json) {
      Task<Void> task = new Task<Void>() {
         @Override public Void call() {
            try {
               resetStartFrom();
               // Set default choice
               int defaultChoice = 1;
               if (json.has("idSetSource")) {
                  JSONObject idSetSource = json.getJSONObject("idSetSource");
                  if (idSetSource.has("newOnlyDate"))
                     defaultChoice = -1;
                  if (idSetSource.has("startSeasonOrYear"))
                     defaultChoice = idSetSource.getInt("startSeasonOrYear");
               }
               setStartChoice(defaultChoice);
               
               String collectionId = null;
               if (json.has("collectionId"))
                  collectionId = json.getString("collectionId");
               else {
                  if (json.has("idSetSource")) {
                     JSONObject idSetSource = json.getJSONObject("idSetSource");
                     if (idSetSource.has("collectionId"))
                        collectionId = idSetSource.getString("collectionId");
                  }
               }
               if (collectionId != null) {
                  Remote r = config.initRemote(tivoName);
                  if (r.success) {
                     JSONObject info = r.seasonYearSearch(collectionId);
                     class backgroundRun implements Runnable {
                        JSONObject info;
                        int defaultChoice;
                        public backgroundRun(JSONObject info, int defaultChoice) {
                           this.info = info;
                           this.defaultChoice = defaultChoice;
                        }
                        @Override public void run() {
                           try {
                           startFrom.getItems().clear();
                           if (info.has("maxSeason")) {
                              int maxSeason = info.getInt("maxSeason");
                              for (int i=1; i<=maxSeason; ++i) {
                                 startFrom.getItems().add("Season " + i);
                                 startFromHash.add("Season " + i, i);
                                 if (i == 1) {
                                    startFrom.getItems().add("New episodes only");
                                    startFromHash.add("New episodes only", -1);
                                 }
                              }
                           }
                           if (info.has("years")) {
                              Boolean hasDefault = false;
                              JSONArray years = info.getJSONArray("years");
                              for (int i=0; i<years.length(); ++i) {
                                 int year = years.getInt(i);
                                 if (defaultChoice == year)
                                    hasDefault = true;
                                 startFrom.getItems().add("" + year);
                                 startFromHash.add("" + year, year);
                                 if (i == 0) {
                                    startFrom.getItems().add("New episodes only");
                                    startFromHash.add("New episodes only", -1);
                                 }
                              }
                              if (! hasDefault)
                                 defaultChoice = years.getInt(0);
                           }
                           } catch (JSONException e) {
                              log.error("setStartFrom - " + e.getMessage());
                           }
                        }
                     }
                     Platform.runLater(new backgroundRun(info, defaultChoice));
                     // Set default choice
                     setStartChoice(defaultChoice);
                  } // if r.success
               }
            } catch (JSONException e) {
               log.error("spOptions setStartFrom - " + e.getMessage());
            }
            //log.warn(">> Start From choices completed");
            return null;
         } // doInBackground
      }; // backgroundRun
      new Thread(task).start();
   }
   
   private void setChannelChoice(final JSONObject json) {
      Platform.runLater(new Runnable() {
         @Override public void run() {
            String name = "All";
            String chan = TableUtil.makeChannelName(json);
            if (chan.contains("="))
               name = chan;
            Boolean needToAdd = true;
            for (int i=0; i<channel.getItems().size(); ++i) {
               String s = (String)channel.getItems().get(i);
               if (s.equals(name)) {
                  needToAdd = false;
                  channel.setValue(name);
               }
            }
            if (needToAdd) {
               channel.getItems().add(name);
               channel.setValue(name);
            }
            // Make sure channelHash has above entry
            if (json.has("idSetSource")) {
               try {
               JSONObject id = json.getJSONObject("idSetSource");
               if (id.has("channel"))
                  channelHash.put(name, id.getJSONObject("channel"));
               } catch (JSONException e) {
                  log.error("setChannelChoice - " + e.getMessage());
               }
            }
         }
      });
   }
   
   private void resetChannels() {
      Platform.runLater(new Runnable() {
         @Override public void run() {
            channel.getItems().clear();
            channelHash = new Hashtable<String,JSONObject>();
            channel.getItems().add("All");
            channel.setValue("All");
         }
      });
   }
   
   private void setStartChoice(final int season) {
      Platform.runLater(new Runnable() {
         @Override public void run() {
            String item = "Season " + season;
            if (season > 1900)
               item = "" + season;
            if (season == -1) {
               item = "New episodes only";
            }
            Boolean needToAdd = true;
            for (int i=0; i<startFrom.getItems().size(); ++i) {
               String s = (String)startFrom.getItems().get(i);
               if (s.equals(item)) {
                  needToAdd = false;
                  startFrom.setValue(item);
               }
            }
            if (needToAdd) {
               startFrom.getItems().add(item);
               startFrom.setValue(item);
            }
         }
      });
   }
   
   private void resetStartFrom() {
      Platform.runLater(new Runnable() {
         @Override public void run() {
            startFrom.getItems().clear();
            startFromHash = new TwoWayHashmap<String,Integer>();
            startFrom.getItems().add("Season 1");
            startFromHash.add("Season 1", 1);
            startFrom.getItems().add("New episodes only");
            startFromHash.add("New episodes only", -1);
            startFrom.setValue("Season 1");
         }
      });
   }
   
   // Return current GMT time in format example: "2015-02-21 02:35:07"
   private String getGMT() {
      Date currentTime = new Date();
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
      return(sdf.format(currentTime));
   }
   
   private void setSeason(JSONObject idSetSource, int startSeasonOrYear) {
      try {
         if (startSeasonOrYear == -1) {
            idSetSource.put("episodeGuideType", "none");
            idSetSource.put("newOnlyDate", getGMT());
            if (idSetSource.has("startSeasonOrYear"))
               idSetSource.remove("startSeasonOrYear");
         } else {
            idSetSource.put("episodeGuideType", "season");
            idSetSource.put("startSeasonOrYear", startSeasonOrYear);
            if (idSetSource.has("newOnlyDate"))
               idSetSource.remove("newOnlyDate");
         }
      } catch (JSONException e) {
         log.error("spOptions.setSeason - " + e.getMessage());
      }
   }
   
   public String getIncludeValue() {
      return (String)include.getValue();
   }
   
   public void setIncludeValue(String val) {
      include.setValue(val);
   }
}
