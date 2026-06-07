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
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.cancelledTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.log;

public class cancelled {
   public JPanel panel = null;
   public cancelledTable tab = null;
   public JComboBox<String> tivo = null;
   public JButton refresh = null;
   public JButton autoresolve = null;
   public JCheckBox includeHistory = null;
   public JButton record = null;
   public JButton explain = null;

   public cancelled(final JFrame frame) {

      // Cancelled table items
      JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

      JLabel title = new JLabel("Not Record list");

      JLabel tivo_label = new JLabel();

      tivo = new JComboBox<String>();
      tivo.addActionListener(new ActionListener() {
         @Override public void actionPerformed(ActionEvent e) {
            String newVal = (String)tivo.getSelectedItem();
            if (newVal != null && config.gui.remote_gui != null) {
               config.gui.remote_gui.updateButtonStates(newVal, "Won't Record");
            }
         }
      });
      tivo.setToolTipText(tooltip.getToolTip("tivo_cancel"));

      refresh = new JButton("Refresh");
      refresh.setToolTipText(tooltip.getToolTip("refresh_cancel_top"));
      refresh.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // Refresh will not record list
            tab.clear();
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               jobData job = new jobData();
               job.source        = tivoName;
               job.tivoName      = tivoName;
               job.type          = "remote";
               job.name          = "Remote";
               job.remote_cancel = true;
               job.cancelled     = tab;
               jobMonitor.submitNewJob(job);
            }
         }
      });

      record = new JButton("Record");
      record.setToolTipText(tooltip.getToolTip("record_cancel"));
      record.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0)
               tab.recordSingle(tivoName);
         }
      });

      explain = new JButton("Explain");
      explain.setToolTipText(tooltip.getToolTip("explain_cancel"));
      explain.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               int selected[] = TableUtil.GetSelectedRows(tab.TABLE);
               if (selected.length > 0) {
                  tab.getConflictDetails(tivoName, selected[0]);
               }
            }
         }
      });

      JButton refresh_todo = new JButton("Refresh ToDo");
      refresh_todo.setToolTipText(tooltip.getToolTip("refresh_todo"));
      refresh_todo.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               Runnable task = new Runnable() {
                  @Override public void run() {
                     log.warn("Refreshing ToDo list for Will Not Record matches...");
                     util.all_todo = util.getTodoLists();
                     log.warn("Refresh ToDo list for Will Not Record matches completed.");
                  }
               };
               new Thread(task).start();
            }
         }
      });

      autoresolve = new JButton("Autoresolve");
      autoresolve.setToolTipText(tooltip.getToolTip("autoresolve"));
      autoresolve.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            autoresolve.setEnabled(false);
            Runnable task = new Runnable() {
               @Override public void run() {
                  rnpl.AutomaticConflictsHandler();
                  autoresolve.setEnabled(true);
               }
            };
            new Thread(task).start();
         }
      });

      includeHistory = new JCheckBox("Include History");
      includeHistory.setSelected(false);
      includeHistory.setToolTipText(tooltip.getToolTip("includeHistory_cancel"));

      row1.add(title);
      row1.add(tivo_label);
      row1.add(tivo);
      row1.add(refresh);
      row1.add(record);
      row1.add(explain);
      row1.add(refresh_todo);
      row1.add(autoresolve);
      row1.add(includeHistory);

      tab = new cancelledTable();

      panel = new JPanel(new BorderLayout());
      panel.add(row1, BorderLayout.NORTH);
      panel.add(new JScrollPane(tab.TABLE.table), BorderLayout.CENTER); // stretch vertically

   }
}
