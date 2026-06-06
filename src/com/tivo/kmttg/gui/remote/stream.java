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
package com.tivo.kmttg.gui.remote;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.tivo.kmttg.gui.table.streamTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.log;

public class stream {
   public JPanel panel = null;
   public streamTable tab = null;
   public JButton refresh = null;
   public JButton remove = null;
   public JComboBox<String> tivo = null;

   public stream(final JFrame frame) {

      // Streaming Tab items
      JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

      JLabel title = new JLabel("Streaming");

      JLabel tivo_label = new JLabel();

      tivo = new JComboBox<String>();
      tivo.addActionListener(new ActionListener() {
         @Override public void actionPerformed(ActionEvent e) {
            String newVal = (String)tivo.getSelectedItem();
            if (newVal != null && config.gui.remote_gui != null) {
                // Refresh channel list only if not inside a folder
                tab.clear();
                String tivoName = newVal;
                config.gui.remote_gui.updateButtonStates(tivoName, "Stream");
                if (tab.tivo_data.containsKey(tivoName))
                   tab.AddRows(tivoName, tab.tivo_data.get(tivoName));
            }
         }
      });
      tivo.setToolTipText(tooltip.getToolTip("tivo_stream"));

      refresh = new JButton("Refresh");
      refresh.setToolTipText(tooltip.getToolTip("refresh_stream"));
      refresh.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // At top level => Update current folder contents
            final String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               tab.clear();
               Runnable task = new Runnable() {
                  @Override public void run() {
                     log.warn("Refreshing Streaming entries...");
                     jobData job = new jobData();
                     job.source        = tivoName;
                     job.tivoName      = tivoName;
                     job.type          = "remote";
                     job.name          = "Remote";
                     job.remote_stream = true;
                     job.stream        = tab;
                     jobMonitor.submitNewJob(job);
                  }
               };
               new Thread(task).start();
            }
         }
      });

      remove = new JButton("Remove");
      remove.setToolTipText(tooltip.getToolTip("remove_stream"));
      remove.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            tab.removeButtonCB();
         }
      });

      row1.add(title);
      row1.add(tivo_label);
      row1.add(tivo);
      row1.add(refresh);
      row1.add(remove);

      tab = new streamTable();

      panel = new JPanel(new BorderLayout());
      panel.add(row1, BorderLayout.NORTH);
      panel.add(new JScrollPane(tab.TABLE.table), BorderLayout.CENTER); // stretch vertically
   }
}
