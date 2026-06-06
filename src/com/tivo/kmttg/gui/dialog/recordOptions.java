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

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.swing.SwingUtil;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.TwoWayHashmap;
import com.tivo.kmttg.util.log;

public class recordOptions {
   JPanel components;
   JLabel label;
   JComboBox<String> record, number, until, start, stop;
   JCheckBox anywhere;
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
      label = new JLabel();
      label.setText("");
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
      anywhere = new JCheckBox();
      anywhere.setText("Try scheduling on all TiVos");
      anywhere.setSelected(false);

      components = new JPanel();
      components.setLayout(new BoxLayout(components, BoxLayout.Y_AXIS));
      components.add(label);
      components.add(new JLabel("Keep until"));      components.add(until);
      components.add(new JLabel("Start recording")); components.add(start);
      components.add(new JLabel("Stop recording"));  components.add(stop);
      components.add(anywhere);
   }

   public JSONObject promptUser(String title, JSONObject json) {
      try {
         if (json != null)
            setValues(json);
         label.setText(title);
         final JDialog dialog = new JDialog(config.gui.getFrame());
         dialog.setModal(true);
         SwingUtil.loadIcons(dialog);
         dialog.setTitle("Recording Options");

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

         JPanel root = new JPanel(new java.awt.BorderLayout());
         root.add(components, java.awt.BorderLayout.CENTER);
         root.add(buttons, java.awt.BorderLayout.SOUTH);
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
            j.put("keepBehavior",     untilHash.getV((String)until.getSelectedItem()));
            j.put("startTimePadding", startHash.getV((String)start.getSelectedItem()));
            j.put("endTimePadding",   stopHash.getV((String)stop.getSelectedItem()));
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
            until.setSelectedItem(untilHash.getK(json.getString("keepBehavior")));
         if(json.has("startTimePadding"))
            start.setSelectedItem(startHash.getK(json.getInt("startTimePadding")));
         else if(json.has("requestedStartPadding"))
            start.setSelectedItem(startHash.getK(json.getInt("requestedStartPadding")));
         if(json.has("endTimePadding"))
            stop.setSelectedItem(stopHash.getK(json.getInt("endTimePadding")));
         else if(json.has("requestedEndPadding"))
            stop.setSelectedItem(stopHash.getK(json.getInt("requestedEndPadding")));
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
         json.put("keepBehavior", untilHash.getV((String)until.getSelectedItem()));
         json.put("startTimePadding", startHash.getV((String)start.getSelectedItem()));
         json.put("endTimePadding", stopHash.getV((String)stop.getSelectedItem()));
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
