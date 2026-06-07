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
import java.awt.Dimension;
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
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.MyListView;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.guideTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.kmttg;
import com.tivo.kmttg.util.log;

public class guide {
   public JPanel panel = null;
   public guideTable tab = null;
   public MyListView ChanList = null;
   public  JButton refresh = null;
   public JComboBox<String> tivo = null;
   public JComboBox<String> start = null;
   public JCheckBox guide_channels = null;
   public JButton record = null;
   public JButton recordSP = null;
   public JButton wishlist = null;
   public  int range = 24; // Number of hours to show in guide at a time
   public int hour_increment = 24; // Number of hours for date increment
   public int total_range = 13;    // Number of days
   public JButton manual_record = null;

   public guide(final JFrame frame) {

      // Guide Tab items
      JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

      JLabel title = new JLabel("Guide");

      JLabel tivo_label = new JLabel();

      tivo = new JComboBox<String>();
      tivo.addActionListener(new ActionListener() {
         @Override public void actionPerformed(ActionEvent e) {
            String newVal = (String)tivo.getSelectedItem();
            // Don't do anything if kmttg starting (implies values being reset)
            if (kmttg._startingUp) return;

            if (newVal != null) {
                // Refresh channel list and clear table
               ChanList.getItems().clear();
               tab.clear();
               String tivoName = newVal;
               config.gui.remote_gui.updateButtonStates(tivoName, "Guide");
               tab.updateChannels(tivoName, false);
               if (tab.tivo_data.containsKey(tivoName))
                  tab.AddRows(tivoName, tab.tivo_data.get(tivoName));
            }
         }
      });
      tivo.setToolTipText(tooltip.getToolTip("tivo_guide"));

      guide_channels = new JCheckBox("All");
      guide_channels.setSelected(false);
      guide_channels.setToolTipText(tooltip.getToolTip("guide_channels"));

      JLabel guide_start_label = new JLabel("Start");
      start = new JComboBox<String>();
      start.setToolTipText(tooltip.getToolTip("guide_start"));
      // When start time changes need to update the table when appropriate
      start.addActionListener(new ActionListener() {
         @Override public void actionPerformed(ActionEvent e) {
            String newVal = (String)start.getSelectedItem();
            if (newVal != null) {
               String tivoName = (String)tivo.getSelectedItem();
               if (tivoName != null && ChanList != null) {
                  String chanName = ChanList.getSelectedValue();
                  if (chanName != null)
                     tab.updateTable(tivoName, chanName);
               }
            }
         }
      });

      refresh = new JButton("Channels");
      refresh.setToolTipText(tooltip.getToolTip("refresh_guide"));
      refresh.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            ChanList.getItems().clear();
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               // Obtain and display channel list
               tab.updateChannels(tivoName, true);
            }
         }
      });

      record = new JButton("Record");
      record.setToolTipText(tooltip.getToolTip("guide_record"));
      record.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               tab.recordSingle(tivoName);
            }
         }
      });

      recordSP = new JButton("Season Pass");
      recordSP.setToolTipText(tooltip.getToolTip("guide_recordSP"));
      recordSP.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               tab.recordSP(tivoName);
            }
         }
      });

      wishlist = new JButton("WL");
      wishlist.setToolTipText(tooltip.getToolTip("wishlist_search"));
      wishlist.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               int[] selected = TableUtil.GetSelectedRows(tab.TABLE);
               JSONObject json = null;
               if (selected.length > 0)
                  json = tab.GetRowData(selected[0]);
               config.gui.remote_gui.createWishlist(tivoName, json);
            }
         }
      });

      manual_record = new JButton("MR");
      manual_record.setToolTipText(tooltip.getToolTip("guide_manual_record"));
      manual_record.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               util.mRecordOpt.promptUser(tivoName);
            }
         }
      });

      JButton guide_refresh_todo = new JButton("Refresh ToDo");
      guide_refresh_todo.setToolTipText(tooltip.getToolTip("guide_refresh_todo"));
      guide_refresh_todo.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               Runnable task = new Runnable() {
                  @Override public void run() {
                     log.warn("Refreshing ToDo list for Guide entries...");
                     util.all_todo = util.getTodoLists();
                     log.warn("Refresh ToDo list for Guide entries completed.");
                  }
               };
               new Thread(task).start();
            }
         }
      });

      row1.add(title);
      row1.add(tivo_label);
      row1.add(tivo);
      row1.add(guide_start_label);
      row1.add(start);
      row1.add(guide_channels);
      row1.add(refresh);
      row1.add(record);
      row1.add(recordSP);
      row1.add(wishlist);
      row1.add(manual_record);
      row1.add(guide_refresh_todo);

      tab = new guideTable();

      ChanList = new MyListView();
      ChanList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      // When a list item is selected, update the table when appropriate
      ChanList.addListSelectionListener(new ListSelectionListener() {
         @Override
         public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting())
               return;
            String newValue = ChanList.getSelectedValue();
            if (newValue != null) {
               String tivoName = (String)tivo.getSelectedItem();
               if (tivoName != null) {
                  tab.updateTable(tivoName, newValue);
               }
            }
         }
      });
      ChanList.setToolTipText(tooltip.getToolTip("guideChanList"));

      JScrollPane chanScroll = new JScrollPane(ChanList);
      chanScroll.setMinimumSize(new Dimension(150, 0));
      chanScroll.setPreferredSize(new Dimension(150, 0));

      JPanel tab_row = new JPanel(new BorderLayout(1, 0));
      tab_row.add(chanScroll, BorderLayout.WEST);
      tab_row.add(new JScrollPane(tab.TABLE), BorderLayout.CENTER);

      panel = new JPanel(new BorderLayout());
      panel.add(row1, BorderLayout.NORTH);
      panel.add(tab_row, BorderLayout.CENTER);
   }

}
