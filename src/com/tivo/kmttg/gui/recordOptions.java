package com.tivo.kmttg.gui;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.util.TwoWayHashmap;
import com.tivo.kmttg.util.log;

public class recordOptions {
   JComponent[] components;
   JLabel label;
   JComboBox record, number, until, start, stop;
   TwoWayHashmap<String,String> untilHash = new TwoWayHashmap<String,String>();
   TwoWayHashmap<String,Integer> startHash = new TwoWayHashmap<String,Integer>();
   TwoWayHashmap<String,Integer> stopHash = new TwoWayHashmap<String,Integer>();
   
   recordOptions() {            
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
      label = new JLabel();
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
      
      components = new JComponent[] {
         label,
         new JLabel("Keep until"),      until,
         new JLabel("Start recording"), start,
         new JLabel("Stop recording"),  stop
      };
   }
   
   public JSONObject promptUser(String title, JSONObject json) {
      try {
         if (json != null)
            setValues(json);
         label.setText(title);
         int response = JOptionPane.showConfirmDialog(
            null, components, "Recording Options", JOptionPane.OK_CANCEL_OPTION
         );
         if (response == JOptionPane.OK_OPTION) {
            // NOTE: Make a copy of json so we don't change existing one
            JSONObject j;
            if (json == null)
               j = new JSONObject();
            else
               j = new JSONObject(json.toString());
            j.put("keepBehavior",     untilHash.getV((String)until.getSelectedItem()));
            j.put("startTimePadding", startHash.getV((String)start.getSelectedItem()));
            j.put("endTimePadding",   stopHash.getV((String)stop.getSelectedItem()));
            return j;
         } else {
            return null;
         }
      } catch (JSONException e) {
         log.error("Record dialog error: " + e.getMessage());
         return null;
      }
   }
   
   private void setValues(JSONObject json) {
      try {
         if(json.has("keepBehavior"))
            until.setSelectedItem(untilHash.getK(json.getString("keepBehavior")));
         if(json.has("startTimePadding"))
            start.setSelectedItem(startHash.getK(json.getInt("startTimePadding")));
         if(json.has("endTimePadding"))
            stop.setSelectedItem(stopHash.getK(json.getInt("endTimePadding")));
      } catch (JSONException e) {
         log.error("Record dialog setValues error: " + e.getMessage());
      }
   }
}
