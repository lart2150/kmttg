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

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.MyTooltip;
import com.tivo.kmttg.gui.swing.SwingUtil;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.pushTable;
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
      Runnable task = new Runnable() {
         @Override public void run() {
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
               SwingUtil.runLater(new Runnable() {
                  @Override public void run() {
                     if (dialog == null)
                        init();
                     else
                        tab.AddRows(data);
                  }
               });
            } else {
               log.warn(tivoName + ": No pending pushes found to display");
            }
         }
      };
      new Thread(task).start();
   }

   private void removePushes(JSONArray entries) {
      // Run in separate background thread
      class backgroundRun implements Runnable {
         JSONArray entries;

         public backgroundRun(JSONArray entries) {
            this.entries = entries;
         }
         @Override
         public void run() {
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
                        return;
                     }
                  }
                  r.disconnect();
              }
            } catch (Exception e) {
               log.error("removePushes - " + e.getMessage());
               return;
            }
         }
      }
      backgroundRun b = new backgroundRun(entries);
      new Thread(b).start();
   }

   private void init() {
      // Define content for dialog window
      JPanel content = new JPanel(new BorderLayout());

      // Refresh button
      JButton refresh = new JButton("Refresh");
      String tip = "<b>Refresh</b><br>Query queued pushes and refresh table.<br>";
      tip += "NOTE: The mind server listings can be several seconds off compared to what is currently happening.";
      refresh.setToolTipText(MyTooltip.make(tip));
      refresh.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            getPushes();
         }
      });

      // Remove button
      JButton remove = new JButton("Remove");
      tip = "<b>Remove</b><br>Attempt to remove selected entry in the table from push queue.<br>";
      tip += "NOTE: This will not cancel pushes already in progress or very close to starting.<br>";
      tip += "NOTE: The response to this operation from mind server is always 'success' so there<br>";
      tip += "is no guarantee that removing an entry actually works or not.";
      remove.setToolTipText(MyTooltip.make(tip));
      remove.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            JSONArray entries = new JSONArray();
            Boolean cont = true;
            while (cont) {
               int[] selected = TableUtil.GetSelectedRows(tab.getTable());
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
      JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
      row1.add(refresh);
      row1.add(remove);
      content.add(row1, BorderLayout.NORTH);

      // Table
      tab = new pushTable();
      tab.AddRows(data);
      JScrollPane tabScroll = new JScrollPane(tab.getTable());
      content.add(tabScroll, BorderLayout.CENTER);

      dialog = new JDialog(frame); // Non modal
      SwingUtil.loadIcons(dialog);
      dialog.setTitle("Push Queue");
      dialog.getContentPane().add(content);
      dialog.setSize(frame.getWidth(), frame.getHeight()/3);
      dialog.setLocationRelativeTo(frame);
      dialog.setVisible(true);
   }
}
