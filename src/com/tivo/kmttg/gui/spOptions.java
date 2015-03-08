package com.tivo.kmttg.gui;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.Stack;
import java.util.TimeZone;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.TwoWayHashmap;
import com.tivo.kmttg.util.log;

public class spOptions {
   int maxSeason = 40;
   JComponent[] components;
   JLabel label;
   JComboBox record, channel, number, until, start, stop, include, startFrom, rentOrBuy, hd;
   TwoWayHashmap<String,String> recordHash = new TwoWayHashmap<String,String>();
   TwoWayHashmap<String,Integer> numberHash = new TwoWayHashmap<String,Integer>();
   TwoWayHashmap<String,String> untilHash = new TwoWayHashmap<String,String>();
   TwoWayHashmap<String,Integer> startHash = new TwoWayHashmap<String,Integer>();
   TwoWayHashmap<String,Integer> stopHash = new TwoWayHashmap<String,Integer>();
   TwoWayHashmap<String,String> includeHash = new TwoWayHashmap<String,String>();
   TwoWayHashmap<String,Integer> startFromHash = new TwoWayHashmap<String,Integer>();
   TwoWayHashmap<String,String> rentOrBuyHash = new TwoWayHashmap<String,String>();
   TwoWayHashmap<String,String> hdHash = new TwoWayHashmap<String,String>();
   Hashtable<String,JSONObject> channelHash = new Hashtable<String,JSONObject>();
   
   public spOptions() {      
      recordHash.add("Repeats & first-run",   "rerunsAllowed");
      recordHash.add("First-run only",        "firstRunOnly");
      recordHash.add("All (with duplicates)", "everyEpisode");
            
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
      for (int i=2; i<=maxSeason; i++) {
         startFromHash.add("Season " + i, i);
      }
      
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
      label = new JLabel();
      record = new JComboBox(new String[] {
         "Repeats & first-run", "First-run only", "All (with duplicates)"}
      );
      record.setSelectedItem("First-run only");
      
      channel = new JComboBox(new String[] {
         "All"
      });
      channel.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
             if (e.getStateChange() == ItemEvent.SELECTED) {
               updateStates();
            }
         }
      });
      
      number = new JComboBox(new String[] {
         "1 recorded show", "2 recorded shows", "3 recorded shows",
         "4 recorded shows", "5 recorded shows", "10 recorded shows",
         "25 recorded shows", "All shows"}
      );
      number.setSelectedItem("25 recorded shows");

      until = new JComboBox(new String[] {"Space needed", "Until I delete"});
      until.setSelectedItem("Space needed");

      start = new JComboBox(new String[] {
         "On time", "1 minute early", "2 minutes early", "3 minutes early",
         "4 minutes early", "5 minutes early", "10 minutes early"}
      );
      start.setSelectedItem("On time");

      stop = new JComboBox(new String[] {
         "On time", "1 minute late", "2 minutes late", "3 minutes late",
         "4 minutes late", "5 minutes late", "10 minutes late",
         "15 minutes late", "30 minutes late", "60 minutes late",
         "90 minutes late", "180 minutes late"}
      );
      stop.setSelectedItem("On time");
      
      include = new JComboBox(new String[] {
         "Recordings Only", "Recordings & Streaming Videos", "Streaming Only"
      });
      include.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
             if (e.getStateChange() == ItemEvent.SELECTED) {
               updateStates();
            }
         }
      });
      
      startFrom = new JComboBox(new String[] {
         "Season 1", "New episodes only"
      });
      for (int i=2; i<=maxSeason; i++) {
         startFrom.addItem("Season " + i);
      }
      
      rentOrBuy = new JComboBox(new String[] {
         "Don't Include", "Include"
      });
      
      hd = new JComboBox(new String[] {
         "If Possible", "Always", "Never"
      });
      
      components = new JComponent[] {
         label,
         new JLabel("Include"),         include,
         new JLabel("Start From"),      startFrom,
         new JLabel("Rent Or Buy"),     rentOrBuy,
         new JLabel("Record"),          record,
         new JLabel("Channel"),         channel,
         new JLabel("Get in HD"),       hd,
         new JLabel("Keep at most"),    number,
         new JLabel("Keep until"),      until,
         new JLabel("Start recording"), start,
         new JLabel("Stop recording"),  stop,
      };
      
      updateStates();
   }
   
   public JSONObject promptUser(String tivoName, String title, JSONObject json, Boolean WL) {
      setChoices(WL);
      try {
         if (json != null) {
            setValues(json);
            setChannels(tivoName, json);
         }
         label.setText(title);
         int response = JOptionPane.showConfirmDialog(
            null, components, "Season Pass Options", JOptionPane.OK_CANCEL_OPTION
         );
         if (response == JOptionPane.OK_OPTION) {
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
            String hdPreference = hdHash.getV((String)hd.getSelectedItem());
            String channelName = (String)channel.getSelectedItem();
            String consumptionSource = includeHash.getV((String)include.getSelectedItem());
            int startSeasonOrYear = startFromHash.getV((String)startFrom.getSelectedItem());
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
               if (channelName.equals(" All")) {
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
                  idSetSource.put("channel", channelHash.get(channelName));
               }
            }
            if (consumptionSource.equals("onDemand")) {
               String [] remove = {"hdPreference"};
               for (String r : remove)
                  if (j.has(r))
                     j.remove(r);
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
      if (choice.equals("Streaming Only"))
         recording = false;
      if (choice.equals("Recordings Only"))
         streaming = false;
      
      rentOrBuy.setEnabled(streaming);
      record.setEnabled(recording);
      channel.setEnabled(recording);
      number.setEnabled(recording);
      until.setEnabled(recording);
      start.setEnabled(recording);
      stop.setEnabled(recording);
      
      Boolean hdenable = false;
      if (channelChoice.equals(" All"))
         hdenable = true;
      if (choice.equals("Streaming Only"))
         hdenable = false;
      hd.setEnabled(hdenable);
   }
   
   private void setChoices(Boolean WL) {
      String All = "All (with duplicates)";
      String c1 = "Recordings Only";
      String c2 = "Recordings & Streaming Videos";
      String c3 = "Streaming Only";
      if (WL) {
         if( ((DefaultComboBoxModel)record.getModel()).getIndexOf(All) == -1)
            record.addItem(All);
         if( ((DefaultComboBoxModel)include.getModel()).getIndexOf(c2) != -1)
            include.removeItem(c2);         
         if( ((DefaultComboBoxModel)include.getModel()).getIndexOf(c3) != -1)
            include.removeItem(c3);         
         include.setSelectedItem(c1);
         updateStates();
      } else {
         if( ((DefaultComboBoxModel)record.getModel()).getIndexOf(All) != -1)
            record.removeItem(All);         
         if( ((DefaultComboBoxModel)include.getModel()).getIndexOf(c2) == -1)
            include.addItem(c2);
         if( ((DefaultComboBoxModel)include.getModel()).getIndexOf(c3) == -1)
            include.addItem(c3);
      }
   }
   
   private void setChannels(String tivoName, JSONObject json) {
      Stack<String> c = new Stack<String>();
      c.push(" All");
      channelHash.clear();
      try {
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
                  JSONArray channels = r.channelSearch(collectionId);
                  if (channels.length() > 0) {
                     for (int i=0; i<channels.length(); ++i) {
                        JSONObject chan = channels.getJSONObject(i);
                        JSONObject j = new JSONObject();
                        j.put("channel", chan);
                        String channelName = TableUtil.makeChannelName(j);
                        channelHash.put(channelName, chan);
                        c.push(channelName);
                     }
                  }
            }
         }
      } catch (JSONException e) {
         log.error("spOptions setChannels - " + e.getMessage());
      }
      channel.removeAllItems();
      for (Object chan : c.toArray()) {
         channel.addItem((String)chan);
      }
      String chan = TableUtil.makeChannelName(json);
      if (chan.contains("="))
         channel.setSelectedItem(chan);
      else {
         // hdPreference relevant for All Channels
         if (json.has("hdPreference")) {
            try {
               hd.setSelectedItem(hdHash.getK(json.getString("hdPreference")));
            } catch (JSONException e) {
               log.error("spOptions setChannels - " + e.getMessage());
            }
         }
      }
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
