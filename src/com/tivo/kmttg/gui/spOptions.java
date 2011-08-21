package com.tivo.kmttg.gui;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.util.TwoWayHashmap;
import com.tivo.kmttg.util.log;

public class spOptions {
   JComponent[] components;
   JComboBox record, number, until, start, stop;
   TwoWayHashmap<String,String> recordHash = new TwoWayHashmap<String,String>();
   TwoWayHashmap<String,Integer> numberHash = new TwoWayHashmap<String,Integer>();
   TwoWayHashmap<String,String> untilHash = new TwoWayHashmap<String,String>();
   TwoWayHashmap<String,Integer> startHash = new TwoWayHashmap<String,Integer>();
   TwoWayHashmap<String,Integer> stopHash = new TwoWayHashmap<String,Integer>();
   
   spOptions() {      
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
      
      createComponents();      
   }
   
   private void createComponents() {      
      record = new JComboBox(new String[] {
         "Repeats & first-run", "First-run only", "All (with duplicates)"}
      );
      record.setSelectedItem("First-run only");
      
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
         "4 minutes late", "5 minutes late", "10 minutes late"}
      );
      stop.setSelectedItem("On time");
      
      components = new JComponent[] {
         new JLabel("Record"),          record,
         new JLabel("Keep at most"),    number,
         new JLabel("Keep until"),      until,
         new JLabel("Start recording"), start,
         new JLabel("Stop recording"),  stop
      };
   }
   
   public JSONObject promptUser(String title, JSONObject json) {
      try {
         if (json != null)
            setValues(json);
         int response = JOptionPane.showConfirmDialog(
            null, components, title, JOptionPane.OK_CANCEL_OPTION
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
            return j;
         } else {
            return null;
         }
      } catch (JSONException e) {
         log.error("SP dialog error: " + e.getMessage());
         return null;
      }
   }
   
   private void setValues(JSONObject json) {
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
      } catch (JSONException e) {
         log.error("SP dialog setValues error: " + e.getMessage());
      }
   }
}
