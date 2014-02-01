package com.tivo.kmttg.gui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.log;

public class Pushes {
   private JFrame frame = null;
   private JDialog dialog = null;
   private pushTable tab = null;
   private JSONArray data = null;
   private String tivoName = null;
   
   public Pushes(String tivoName, JFrame frame) {
      this.tivoName = tivoName;
      this.frame = frame;      
      getPushes();
   }
   
   // Retrieve queue data from TiVo mind server
   private void getPushes() {
      // Run in separate background thread
      class backgroundRun extends SwingWorker<Void, Void> {
         protected Void doInBackground() {
            if (tab != null)
               tab.clear();
            data = new JSONArray();
            Remote r = new Remote(tivoName, true);
            if (r.success) {
               try {            
                  JSONObject json = new JSONObject();
                  json.put("bodyId", r.bodyId_get());
                  json.put("noLimit", true);
                  json.put("levelOfDetail", "low");
                  JSONObject result = r.Command("downloadSearch", json);
                  if (result != null && result.has("download")) {
                     JSONArray a = result.getJSONArray("download");
                     for (int i=0; i<a.length(); ++i) {
                        JSONObject d = a.getJSONObject(i);
                        if (d.has("state")) {
                           String state = d.getString("state");
                           if (state.equals("scheduled") || state.equals("inProgress")) {
                              JSONObject j = new JSONObject();
                              j.put("bodyId", json.getString("bodyId"));
                              j.put("levelOfDetail", "high");
                              j.put("offerId", d.getString("offerId"));
                              JSONObject detail = r.Command("offerSearch", j);
                              if (detail != null && detail.has("offer")) {
                                 JSONObject o = detail.getJSONArray("offer").getJSONObject(0);
                                 o.put("bodyId", json.getString("bodyId"));
                                 data.put(o);
                              }
                           }
                        }
                     }
                  }
               } catch (Exception e) {
                  e.printStackTrace();
               }
               r.disconnect();
            }
            
            if (data != null && data.length() > 0) {
               if (dialog == null)
                  init();
               else
                  tab.AddRows(data);
            } else {
               log.warn(tivoName + ": No pending pushes found to display");
            }
            return null;
         }
      }
      backgroundRun b = new backgroundRun();
      b.execute();
   }
   
   private void removePushes(JSONArray entries) {
      // Run in separate background thread
      class backgroundRun extends SwingWorker<Void, Void> {
         JSONArray entries;

         public backgroundRun(JSONArray entries) {
            this.entries = entries;
         }

         protected Void doInBackground() {
            try {
               Remote r = new Remote(tivoName, true);
               if (r.success) {
                  for (int i=0; i<entries.length(); ++i) {
                     JSONObject json = new JSONObject();
                     json.put("bodyId", r.bodyId_get());
                     json.put("state", "cancelled");
                     json.put("cancellationReason", "userStoppedTransfer");
                     json.put("offerId", entries.getJSONObject(i).getString("offerId"));
                     //json.put("downloadId", entries.getJSONObject(i).getString("downloadId"));
                     JSONObject result = r.Command("downloadModify", json);
                     if (result != null) {
                        log.print(result.toString(3));
                     } else {
                        log.error("push item remove failed");
                        return null;
                     }
                  }
                  r.disconnect();
              }
            } catch (Exception e) {
               log.error("removePushes - " + e.getMessage());
               return null;
            }
            return null;
         }
      }
      backgroundRun b = new backgroundRun(entries);
      b.execute();
   }
         
   private void init() {
      // Define content for dialog window
      int gy = 0;
      JPanel content = new JPanel();
      content.setLayout(new GridBagLayout());
      GridBagConstraints c = new GridBagConstraints();
      c.ipady = 0;
      c.weighty = 0.0;  // default to no vertical stretch
      c.weightx = 0.0;  // default to no horizontal stretch
      c.gridx = 0;
      c.gridy = gy;
      c.gridwidth = 1;
      c.gridheight = 1;
      c.anchor = GridBagConstraints.CENTER;
      c.fill = GridBagConstraints.NONE;

      // Refresh button
      JButton refresh = new JButton("Refresh");
      String tip = "<html><b>Refresh</b><br>Query queued pushes and refresh table.<br>";
      tip += "NOTE: The mind server listings can be several seconds off compared to what is currently happening.";
      tip += "</html>";
      refresh.setToolTipText(tip);
      refresh.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            getPushes();
         }
      });

      // Remove button
      JButton remove = new JButton("Remove");
      tip = "<html><b>Remove</b><br>Attempt to remove selected entry in the table from push queue.<br>";
      tip += "NOTE: This will not cancel pushes already in progress or very close to starting.<br>";
      tip += "NOTE: The response to this operation from mind server is always 'success' so there<br>";
      tip += "is no guarantee that removing an entry actually works or not.";
      tip += "</html>";
      remove.setToolTipText(tip);
      remove.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            JSONArray entries = new JSONArray();
            Boolean cont = true;
            while (cont) {
               int[] selected = tab.getTable().getSelectedRows();
               if (selected.length > 0) {
                  int row = selected[0];
                 JSONObject json = tab.GetRowData(row);
                  if (json != null)
                     entries.put(json);
                  tab.RemoveRow(row);
               } else {
                  cont = false;
               }
            }
            if (entries.length() > 0)
               removePushes(entries);
         }
      });
      
      // Row 1 = 2 buttons
      JPanel row1 = new JPanel();
      row1.setLayout(new BoxLayout(row1, BoxLayout.LINE_AXIS));
      row1.add(refresh);
      row1.add(remove);
      content.add(row1, c);
      
      // Table
      tab = new pushTable();
      tab.AddRows(data);
      tab.getTable().setPreferredScrollableViewportSize(tab.getTable().getPreferredSize());
      JScrollPane tabScroll = new JScrollPane(tab.getTable());
      gy++;
      c.gridy = gy;
      c.weighty = 1.0;
      c.weightx = 1.0;
      c.fill = GridBagConstraints.BOTH;
      content.add(tabScroll, c);

      dialog = new JDialog(frame, false); // non-modal dialog
      dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE); // Destroy when closed
      dialog.setTitle("Push Queue");
      dialog.setContentPane(content);
      dialog.pack();
      dialog.setSize((int)(frame.getSize().width), (int)(frame.getSize().height/3));
      dialog.setLocationRelativeTo(config.gui.getJFrame().getJMenuBar().getComponent(0));
      dialog.setVisible(true);      
   }
}
