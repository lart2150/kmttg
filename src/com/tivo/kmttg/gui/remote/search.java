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
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Hashtable;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.dialog.AdvSearch;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.searchTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class search {
   public JPanel panel = null;
   public JComboBox<String> tivo = null;
   public searchTable tab = null;
   public JTextField text = null;
   public JButton button = null;
   public JSpinner max = null;
   public JComboBox<String> search_type = null;
   public JCheckBox includeFree = null;
   public JCheckBox includePaid = null;
   //public JCheckBox includeVod = null;
   //public JCheckBox unavailable = null;
   public Hashtable<String,JSONArray> search_info = new Hashtable<String,JSONArray>();
   public AdvSearch advSearch = new AdvSearch();
   public JButton manual_record = null;
   public JButton record = null;
   public JButton recordSP = null;
   public JButton wishlist = null;

   public search (final JFrame frame) {

      // Search tab items
      JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

      JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

      JLabel title = new JLabel("Search");

      JLabel tivo_label = new JLabel();

      tivo = new JComboBox<String>();
      tivo.addActionListener(new ActionListener() {
         @Override public void actionPerformed(ActionEvent e) {
            String newVal = (String)tivo.getSelectedItem();
            if (newVal != null && config.gui.remote_gui != null) {
                String tivoName = newVal;
                config.gui.remote_gui.updateButtonStates(tivoName, "Search");
            }
         }
      });
      tivo.setToolTipText(tooltip.getToolTip("tivo_search"));

      button = new JButton("Search");
      button.setToolTipText(tooltip.getToolTip("button_search"));
      button.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // New search
            tab.clear();
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               String keyword = string.removeLeadingTrailingSpaces(text.getText());
               if (keyword == null || keyword.length() == 0)
                  return;
               int max_val = (Integer)max.getValue();

               jobData job = new jobData();
               job.source                = tivoName;
               job.tivoName              = tivoName;
               job.type                  = "remote";
               job.name                  = "Remote";
               job.search                = tab;
               job.remote_search_max     = max_val;
               job.remote_search         = true;
               job.remote_search_keyword = keyword;
               jobMonitor.submitNewJob(job);
            }
         }
      });

      text = new JTextField(); text.setColumns(15);
      // Press "Search" button when enter pressed in search text field
      text.addKeyListener(new KeyAdapter() {
         public void keyPressed(KeyEvent event) {
            if (event.isControlDown())
               return;
            if( event.getKeyCode() == KeyEvent.VK_ENTER ) {
               button.doClick();
               event.consume();
            }
         }
      });

      text.setToolTipText(tooltip.getToolTip("text_search"));

      JButton adv = new JButton("Search++");
      adv.setToolTipText(tooltip.getToolTip("adv_search"));
      adv.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               advSearch.display(
                  config.gui.getFrame(), tivoName, (Integer)max.getValue()
               );
            }
         }
      });

      record = new JButton("Record");
      record.setToolTipText(tooltip.getToolTip("record_search"));
      record.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               tab.recordSingle(tivoName);
            }
         }
      });

      recordSP = new JButton("SP");
      recordSP.setToolTipText(tooltip.getToolTip("record_sp_search"));
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

      JButton refresh_todo = new JButton("Refresh ToDo");
      refresh_todo.setToolTipText(tooltip.getToolTip("refresh_todo_search"));
      refresh_todo.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               Runnable task = new Runnable() {
                  @Override public void run() {
                     log.warn("Refreshing ToDo list for Search matches...");
                     util.all_todo = util.getTodoLists();
                     log.warn("Refresh ToDo list for Search matches completed.");
                  }
               };
               new Thread(task).start();
            }
         }
      });

      JLabel max_label = new JLabel("Max");
      max = new JSpinner(new SpinnerNumberModel(100, 50, 800, 50));
      max.setMaximumSize(new Dimension(90, max.getPreferredSize().height));

      max.setToolTipText(tooltip.getToolTip("max_search"));

      row1.add(title);
      row1.add(tivo_label);
      row1.add(tivo);
      row1.add(button);
      row1.add(text);
      row1.add(max_label);
      row1.add(max);
      row1.add(adv);
      row1.add(record);
      row1.add(recordSP);
      row1.add(wishlist);
      row1.add(manual_record);
      row1.add(refresh_todo);

      JLabel search_type_label = new JLabel("Type");

      search_type = new JComboBox<String>();
      String[] search_type_items = {
         "keywords", "actor", "director", "producer", "executiveProducer", "writer"
      };
      for (String item : search_type_items)
         search_type.addItem(item);
      search_type.setSelectedItem("keywords");
      search_type.setToolTipText(tooltip.getToolTip("search_type"));

      includeFree = new JCheckBox("Streaming content");
      includeFree.setSelected(false);
      includeFree.setToolTipText(tooltip.getToolTip("includeFree"));

      includePaid = new JCheckBox("Paid streaming content");
      includePaid.setSelected(false);
      includePaid.setToolTipText(tooltip.getToolTip("includePaid"));

      //includeVod = new JCheckBox("VOD content");
      //includeVod.setSelected(false);
      //includeVod.setToolTipText(tooltip.getToolTip("includeVod"));

      //unavailable = new JCheckBox("Unavailable");
      //unavailable.setSelected(false);
      //unavailable.setToolTipText(tooltip.getToolTip("unavailable"));

      row2.add(search_type_label);
      row2.add(search_type);
      row2.add(includeFree);
      row2.add(includePaid);
      //row2.add(includeVod);
      //row2.add(unavailable);

      tab = new searchTable();

      JPanel rows = new JPanel(new BorderLayout());
      rows.add(row1, BorderLayout.NORTH);
      rows.add(row2, BorderLayout.SOUTH);

      panel = new JPanel(new BorderLayout());
      panel.add(rows, BorderLayout.NORTH);
      panel.add(new JScrollPane(tab.TABLE.table), BorderLayout.CENTER); // stretch vertically
   }

}
