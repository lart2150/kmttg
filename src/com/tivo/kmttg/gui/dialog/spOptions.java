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

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.Stack;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONConverter;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.swing.SwingUtil;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.TwoWayHashmap;
import com.tivo.kmttg.util.log;

public class spOptions {
   JPanel components;
   JLabel label;
   JComboBox<String> record, channel, number, until, start, stop, include, startFrom, rentOrBuy, hd;
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

   // Helper - does given combo box contain the given item
   private static boolean contains(JComboBox<String> box, String item) {
      for (int i=0; i<box.getItemCount(); ++i) {
         if (box.getItemAt(i).equals(item))
            return true;
      }
      return false;
   }

   private void createComponents() {
      label = new JLabel();
      record = new JComboBox<String>();
      record.addItem("New & repeats");
      record.addItem("New only");
      record.addItem("Everything");
      record.setSelectedItem("New only");

      channel = new JComboBox<String>();
      channel.addItem("All");
      channel.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            String newVal = (String)channel.getSelectedItem();
            if (newVal != null) {
               updateStates();
            }
         }
      });

      number = new JComboBox<String>();
      String[] numberItems = {
         "1 recorded show", "2 recorded shows", "3 recorded shows",
         "4 recorded shows", "5 recorded shows", "10 recorded shows",
         "25 recorded shows", "All shows"
      };
      for (String s : numberItems)
         number.addItem(s);
      number.setSelectedItem("25 recorded shows");

      until = new JComboBox<String>();
      until.addItem("Space needed");
      until.addItem("Until I delete");
      until.setSelectedItem("Space needed");

      start = new JComboBox<String>();
      String[] startItems = {
         "On time", "1 minute early", "2 minutes early", "3 minutes early",
         "4 minutes early", "5 minutes early", "10 minutes early"
      };
      for (String s : startItems)
         start.addItem(s);
      start.setSelectedItem("On time");

      stop = new JComboBox<String>();
      String[] stopItems = {
         "On time", "1 minute late", "2 minutes late", "3 minutes late",
         "4 minutes late", "5 minutes late", "10 minutes late",
         "15 minutes late", "30 minutes late", "60 minutes late",
         "90 minutes late", "180 minutes late"
      };
      for (String s : stopItems)
         stop.addItem(s);
      stop.setSelectedItem("On time");

      include = new JComboBox<String>();
      include.addItem("Recordings Only");
      include.addItem("Recordings & Streaming Videos");
      include.addItem("Streaming Only");
      include.setSelectedItem(include.getItemAt(0));
      include.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            String newVal = (String)include.getSelectedItem();
            if (newVal != null) {
               updateStates();
            }
         }
      });

      startFrom = new JComboBox<String>();
      startFrom.addItem("Season 1");
      startFrom.addItem("New episodes only");
      startFrom.setSelectedItem(startFrom.getItemAt(0));

      rentOrBuy = new JComboBox<String>();
      rentOrBuy.addItem("Don't Include");
      rentOrBuy.addItem("Include");
      rentOrBuy.setSelectedItem(rentOrBuy.getItemAt(0));

      hd = new JComboBox<String>();
      hd.addItem("If Possible");
      hd.addItem("Always");
      hd.addItem("Never");
      hd.setSelectedItem(hd.getItemAt(0));

      components = new JPanel();
      components.setLayout(new BoxLayout(components, BoxLayout.Y_AXIS));
      components.add(label);
      components.add(new JLabel("Include"));         components.add(include);
      components.add(new JLabel("Start From"));      components.add(startFrom);
      components.add(new JLabel("Rent Or Buy"));     components.add(rentOrBuy);
      components.add(new JLabel("Record"));          components.add(record);
      components.add(new JLabel("Channel"));         components.add(channel);
      components.add(new JLabel("Get in HD"));       components.add(hd);
      components.add(new JLabel("Keep at most"));    components.add(number);
      components.add(new JLabel("Keep until"));      components.add(until);
      components.add(new JLabel("Start recording")); components.add(start);
      components.add(new JLabel("Stop recording"));  components.add(stop);

      updateStates();
   }

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
               hd.setSelectedItem(hdHash.getK(hdp));
            }
         }
         label.setText(title);
         final JDialog dialog = new JDialog(config.gui.getFrame());
         dialog.setModal(true);
         SwingUtil.loadIcons(dialog);
         dialog.setTitle("Season Pass Options");

         final AtomicBoolean ok = new AtomicBoolean(false);
         JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
         JButton okButton = new JButton("OK");
         okButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               ok.set(true);
               dialog.setVisible(false);
            }
         });
         JButton cancelButton = new JButton("CANCEL");
         cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               ok.set(false);
               dialog.setVisible(false);
            }
         });
         buttons.add(okButton);
         buttons.add(cancelButton);

         JPanel root = new JPanel(new BorderLayout());
         root.add(components, BorderLayout.CENTER);
         root.add(buttons, BorderLayout.SOUTH);
         dialog.getContentPane().add(root);
         dialog.pack();
         dialog.setLocationRelativeTo(config.gui.getFrame());
         dialog.setVisible(true);
         dialog.dispose();

         if (ok.get()) {
            // NOTE: Make a copy of json so we don't change existing one
            JSONObject j;
            if (json == null)
               j = new JSONObject();
            else
               j = new JSONObject(json.toString());
            j.put("showStatus",       recordHash.getV((String)record.getSelectedItem()));
            j.put("maxRecordings",    numberHash.getV((String)number.getSelectedItem()));
            j.put("keepBehavior",     untilHash.getV((String)until.getSelectedItem()));
            j.put("startTimePadding", startHash.getV((String)start.getSelectedItem()));
            j.put("endTimePadding",   stopHash.getV((String)stop.getSelectedItem()));
            // NOTE: For WL types set consumptionSource to null
            String consumptionSource = null;
            if (include.isEnabled())
               consumptionSource = includeHash.getV((String)include.getSelectedItem());
            String hdPreference = hdHash.getV((String)hd.getSelectedItem());
            String channelName = (String)channel.getSelectedItem();
            int startSeasonOrYear = startFromHash.getV((String)startFrom.getSelectedItem());
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
                  idSetSource.put("costFilter", rentOrBuyHash.getV((String)rentOrBuy.getSelectedItem()));
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
            record.setSelectedItem(recordHash.getK(json.getString("showStatus")));
         if(json.has("maxRecordings"))
            number.setSelectedItem(numberHash.getK(json.getInt("maxRecordings")));
         if(json.has("keepBehavior"))
            until.setSelectedItem(untilHash.getK(json.getString("keepBehavior")));
         if(json.has("startTimePadding"))
            start.setSelectedItem(startHash.getK(json.getInt("startTimePadding")));
         if(json.has("endTimePadding"))
            stop.setSelectedItem(stopHash.getK(json.getInt("endTimePadding")));
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
         include.setSelectedItem(includeHash.getK(consumptionSource));
         rentOrBuy.setSelectedItem(rentOrBuyHash.getK(costFilter));
         startFrom.setSelectedItem(startFromHash.getK(startSeasonOrYear));
      } catch (JSONException e) {
         log.error("spOptions.setValues - " + e.getMessage());
      }
   }

   public JSONObject getValues() {
      JSONObject json = new JSONObject();
      try {
         json.put("showStatus", recordHash.getV((String)record.getSelectedItem()));
         json.put("maxRecordings", numberHash.getV((String)number.getSelectedItem()));
         json.put("keepBehavior", untilHash.getV((String)until.getSelectedItem()));
         json.put("startTimePadding", startHash.getV((String)start.getSelectedItem()));
         json.put("endTimePadding", stopHash.getV((String)stop.getSelectedItem()));
      } catch (JSONException e) {
         log.error("spOptions.getValues - " + e.getMessage());
         return null;
      }
      return json;
   }

   // include cyclic change callback
   private void updateStates() {
      String choice = (String)include.getSelectedItem();
      String channelChoice = (String)channel.getSelectedItem();
      Boolean recording = true;
      Boolean streaming = true;
      if (choice != null && choice.equals("Streaming Only"))
         recording = false;
      if (choice != null && choice.equals("Recordings Only"))
         streaming = false;

      rentOrBuy.setEnabled(!streaming);
      record.setEnabled(recording);
      channel.setEnabled(recording);
      number.setEnabled(recording);
      until.setEnabled(recording);
      start.setEnabled(recording);
      stop.setEnabled(recording);

      Boolean hdenable = false;
      if (channelChoice != null && channelChoice.equals("All"))
         hdenable = true;
      if (choice != null && choice.equals("Streaming Only"))
         hdenable = false;
      hd.setEnabled(hdenable);
   }

   private void setChoices(Boolean WL) {
      String All = "Everything";
      String c1 = "Recordings Only";
      String c2 = "Recordings & Streaming Videos";
      String c3 = "Streaming Only";
      if (WL) {
         if( ! contains(record, All) )
            record.addItem(All);
         if( contains(include, c2) )
            include.removeItem(c2);
         if( contains(include, c3) )
            include.removeItem(c3);
         include.setSelectedItem(c1);
         updateStates();
      } else {
         if( ! contains(record, All) )
            record.addItem(All);
         if( ! contains(include, c2) )
            include.addItem(c2);
         if( ! contains(include, c3) )
            include.addItem(c3);
      }
      if (WL)
         hd.setEnabled(true);
      include.setEnabled(!WL);
      startFrom.setEnabled(!WL);
      channel.setEnabled(!WL);
   }

   // This runs in background mode so as not to hang up GUI
   private void setChannels(final String tivoName, final JSONObject json) {
      Runnable task = new Runnable() {
         @Override public void run() {
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
                  channel.removeAllItems();
                  for (Object chan : c.toArray()) {
                     channel.addItem((String)chan);
                  }
               }
            }
            SwingUtil.runLater(new backgroundRun(c));
            setChannelChoice(json);
            String defaultChoice = (String)channel.getSelectedItem();
            if ( ! defaultChoice.contains("=") ) {
               // hdPreference relevant for All Channels
               if (json.has("hdPreference")) {
                  SwingUtil.runLater(new Runnable() {
                     @Override public void run() {
                        try {
                           hd.setSelectedItem(hdHash.getK(json.getString("hdPreference")));
                        } catch (JSONException e) {
                           log.error("spOptions setChannels - " + e.getMessage());
                        }
                     }
                  });
               }
            }
            //log.warn(">> Channel choices completed");
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
                  String channelName = JSONConverter.makeChannelName(j);
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
      Runnable task = new Runnable() {
         @Override public void run() {
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
                           startFrom.removeAllItems();
                           if (info.has("maxSeason")) {
                              int maxSeason = info.getInt("maxSeason");
                              for (int i=1; i<=maxSeason; ++i) {
                                 startFrom.addItem("Season " + i);
                                 startFromHash.add("Season " + i, i);
                                 if (i == 1) {
                                    startFrom.addItem("New episodes only");
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
                                 startFrom.addItem("" + year);
                                 startFromHash.add("" + year, year);
                                 if (i == 0) {
                                    startFrom.addItem("New episodes only");
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
                     SwingUtil.runLater(new backgroundRun(info, defaultChoice));
                     // Set default choice
                     setStartChoice(defaultChoice);
                  } // if r.success
               }
            } catch (JSONException e) {
               log.error("spOptions setStartFrom - " + e.getMessage());
            }
            //log.warn(">> Start From choices completed");
         } // doInBackground
      }; // backgroundRun
      new Thread(task).start();
   }

   private void setChannelChoice(final JSONObject json) {
      SwingUtil.runLater(new Runnable() {
         @Override public void run() {
            String name = "All";
            String chan = JSONConverter.makeChannelName(json);
            if (chan.contains("="))
               name = chan;
            Boolean needToAdd = true;
            for (int i=0; i<channel.getItemCount(); ++i) {
               String s = (String)channel.getItemAt(i);
               if (s.equals(name)) {
                  needToAdd = false;
                  channel.setSelectedItem(name);
               }
            }
            if (needToAdd) {
               channel.addItem(name);
               channel.setSelectedItem(name);
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
      SwingUtil.runLater(new Runnable() {
         @Override public void run() {
            channel.removeAllItems();
            channelHash = new Hashtable<String,JSONObject>();
            channel.addItem("All");
            channel.setSelectedItem("All");
         }
      });
   }

   private void setStartChoice(final int season) {
      SwingUtil.runLater(new Runnable() {
         @Override public void run() {
            String item = "Season " + season;
            if (season > 1900)
               item = "" + season;
            if (season == -1) {
               item = "New episodes only";
            }
            Boolean needToAdd = true;
            for (int i=0; i<startFrom.getItemCount(); ++i) {
               String s = (String)startFrom.getItemAt(i);
               if (s.equals(item)) {
                  needToAdd = false;
                  startFrom.setSelectedItem(item);
               }
            }
            if (needToAdd) {
               startFrom.addItem(item);
               startFrom.setSelectedItem(item);
            }
         }
      });
   }

   private void resetStartFrom() {
      SwingUtil.runLater(new Runnable() {
         @Override public void run() {
            startFrom.removeAllItems();
            startFromHash = new TwoWayHashmap<String,Integer>();
            startFrom.addItem("Season 1");
            startFromHash.add("Season 1", 1);
            startFrom.addItem("New episodes only");
            startFromHash.add("New episodes only", -1);
            startFrom.setSelectedItem("Season 1");
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
      return (String)include.getSelectedItem();
   }

   public void setIncludeValue(String val) {
      include.setSelectedItem(val);
   }
}
