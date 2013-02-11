package com.tivo.kmttg.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.log;

public class resolveConflict {
   JComponent[] components;
   JLabel label;
   JSONArray conflicts;
   int tuners;
   
   resolveConflict(JSONArray conflicts, int tuners) {
      this.conflicts = conflicts;
      this.tuners = tuners;
      createComponents();      
   }
   
   private void createComponents() {
      components = new JComponent[conflicts.length()+1];
      components[0] = new JLabel("");
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
         components[i+1] = box;
      }
      } catch (JSONException e) {
         log.error("Conflicts dialog error: " + e.getMessage());
      }
   }
   
   public JSONArray promptUser(String title) {
      try {
         label.setText(title);
         int response = JOptionPane.showConfirmDialog(
            null, components, "Resolve conflicts", JOptionPane.OK_CANCEL_OPTION
         );
         if (response == JOptionPane.OK_OPTION) {
            for (int i=0; i<conflicts.length(); ++i) {
               JSONObject json = conflicts.getJSONObject(i);
               JCheckBox box = (JCheckBox)components[i+1];
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
         JCheckBox box = (JCheckBox)components[i+1];
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
