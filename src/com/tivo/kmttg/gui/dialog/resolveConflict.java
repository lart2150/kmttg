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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.log;

public class resolveConflict {
   JPanel components;
   JLabel label;
   JSONArray conflicts;
   int tuners;

   resolveConflict(JSONArray conflicts, int tuners) {
      this.conflicts = conflicts;
      this.tuners = tuners;
      createComponents();
   }

   private void createComponents() {
      components = new JPanel();
      components.setLayout(new BoxLayout(components, BoxLayout.Y_AXIS));
      label = new JLabel("");
      components.add(label);
      try {
         for (int i=0; i<conflicts.length(); ++i) {
            JSONObject json = conflicts.getJSONObject(i);
            String text = rnpl.formatEntry(json);
            JCheckBox box = new JCheckBox(text);
            box.addActionListener(new ActionListener() {
               public void actionPerformed(ActionEvent e) {
                  // Limited number of tuners means only that many shows can be enabled at a time
                  checkTuners();
               }
            });
            components.add(box);
         }
      } catch (JSONException e) {
         log.error("Conflicts dialog error: " + e.getMessage());
      }
   }

   public JSONArray promptUser(String title) {
      try {
         label.setText(title);
         int response = JOptionPane.showConfirmDialog(
            config.gui.getFrame(), components, "Resolve conflicts", JOptionPane.OK_CANCEL_OPTION
         );
         if (response == JOptionPane.OK_OPTION) {
            for (int i=0; i<conflicts.length(); ++i) {
               JSONObject json = conflicts.getJSONObject(i);
               JCheckBox box = (JCheckBox)components.getComponent(i+1);
               if (box.isSelected()) {
                  json.put("__record__", "yes");
               }
            }
            return conflicts;
         } else {
            return null;
         }
      } catch (JSONException e) {
         log.error("Resolve conflicts dialog error: " + e.getMessage());
         return null;
      }
   }

   private void checkTuners() {
      int count = 0;
      for (int i=0; i<conflicts.length(); ++i) {
         JCheckBox box = (JCheckBox)components.getComponent(i+1);
         if (box.isSelected()) {
            count++;
            if (count > tuners) {
               log.warn("Can only record " + tuners + " shows at once on this box");
               box.setSelected(false);
            }
         }
      }
   }
}
