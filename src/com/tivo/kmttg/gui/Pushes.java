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
               log.warn("No pending pushes found to display");
            }
            return null;
         }
      }
      backgroundRun b = new backgroundRun();
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
      
      // Row 1 = refresh button
      JPanel row1 = new JPanel();
      row1.setLayout(new BoxLayout(row1, BoxLayout.LINE_AXIS));
      row1.add(refresh);
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
