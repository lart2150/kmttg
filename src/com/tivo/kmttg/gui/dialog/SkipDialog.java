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
import com.tivo.kmttg.gui.table.skipTable;
import com.tivo.kmttg.rpc.SkipManager;
import com.tivo.kmttg.util.log;

public class SkipDialog {
   private JFrame frame = null;
   private JDialog dialog = null;
   private skipTable tab = null;
   private JSONArray data = null;

   public SkipDialog(JFrame frame) {
      this.frame = frame;
      getEntries();
   }

   // Retrieve entries from AutoSkip.ini file
   private void getEntries() {
      if (tab != null)
         tab.clear();
      data = SkipManager.getEntries();
      if (data != null && data.length() > 0) {
         if (dialog == null)
            init();
         else
            tab.AddRows(data);
      } else {
         log.warn("No data available to display");
      }
   }

   private void removeEntries(JSONArray entries) {
      // Run in separate background thread
      class backgroundRun implements Runnable {
         JSONArray entries;

         public backgroundRun(JSONArray entries) {
            this.entries = entries;
         }
         @Override
         public void run() {
            try {
               for (int i=0; i<entries.length(); ++i) {
                  JSONObject json = entries.getJSONObject(i);
                  SkipManager.removeEntry(json.getString("contentId"));
               }
            } catch (Exception e) {
               log.error("removeEntries - " + e.getMessage());
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
      String tip = "<b>Refresh</b><br>Get list of AutoSkip entries and refresh table.";
      refresh.setToolTipText(MyTooltip.make(tip));
      refresh.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            getEntries();
         }
      });

      // Remove button
      JButton remove = new JButton("Remove");
      tip = "<b>Remove</b><br>Remove selected entry in the table from AutoSkip file.";
      remove.setToolTipText(MyTooltip.make(tip));
      remove.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            int[] selected = TableUtil.GetSelectedRows(tab.getTable());
            if (selected == null || selected.length != 1) {
               log.error("Must select a single table row.");
               return;
            }
            JSONArray entries = new JSONArray();
            int row = selected[0];
            JSONObject json = tab.GetRowData(row);
            if (json != null)
               entries.put(json);
            tab.RemoveRow(row);
            if (entries.length() > 0)
               removeEntries(entries);
         }
      });

      // Row 1 = buttons
      JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
      row1.add(refresh);
      row1.add(remove);
      content.add(row1, BorderLayout.NORTH);

      // Table
      tab = new skipTable();
      tab.AddRows(data);
      content.add(new JScrollPane(tab.TABLE), BorderLayout.CENTER); // stretch vertically

      dialog = new JDialog(frame);
      SwingUtil.loadIcons(dialog);
      dialog.setTitle("AutoSkip Entries");
      dialog.getContentPane().add(content);
      dialog.setSize((int)(frame.getWidth()/1.2), frame.getHeight()/3);
      dialog.setLocationRelativeTo(frame);
      dialog.setVisible(true);
   }
}
