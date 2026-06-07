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
import java.io.File;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.todoTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.log;

public class todo {
   public JPanel panel = null;
   public todoTable tab = null;
   public JComboBox<String> tivo = null;
   public JLabel label = null;
   public JButton cancel = null;
   public JButton modify = null;

   public todo(final JFrame frame) {
      // ToDo Tab items
      JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

      JLabel title = new JLabel("ToDo list");

      JLabel tivo_label = new JLabel();

      tivo = new JComboBox<String>();
      tivo.addActionListener(new ActionListener() {
         @Override public void actionPerformed(ActionEvent e) {
            String newVal = (String)tivo.getSelectedItem();
            if (newVal != null && config.gui.remote_gui != null) {
                TableUtil.clear(tab.TABLE);
                label.setText("");
                String tivoName = newVal;
                config.gui.remote_gui.updateButtonStates(tivoName, "ToDo");
                if (tab.tivo_data.containsKey(tivoName))
                   tab.AddRows(tivoName, tab.tivo_data.get(tivoName));
            }
         }
      });
      tivo.setToolTipText(tooltip.getToolTip("tivo_todo"));

      JButton refresh = new JButton("Refresh");
      refresh.setToolTipText(tooltip.getToolTip("refresh_todo"));
      refresh.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // Refresh to do list
            TableUtil.clear(tab.TABLE);
            label.setText("");
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               jobData job = new jobData();
               job.source      = tivoName;
               job.tivoName    = tivoName;
               job.type        = "remote";
               job.name        = "Remote";
               job.remote_todo = true;
               job.todo        = tab;
               jobMonitor.submitNewJob(job);
            }
         }
      });

      cancel = new JButton("Cancel");
      cancel.setToolTipText(tooltip.getToolTip("cancel_todo"));
      cancel.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            tab.DeleteCB();
         }
      });

      modify = new JButton("Modify");
      modify.setToolTipText(tooltip.getToolTip("modify_todo"));
      modify.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               tab.recordSingle(tivoName);
            }
         }
      });

      JButton export = new JButton("Export ...");
      export.setToolTipText(tooltip.getToolTip("export_todo"));
      export.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            final String tivoName = (String)tivo.getSelectedItem();
            JFileChooser Browser = config.gui.remote_gui.Browser;
            Browser.resetChoosableFileFilters();
            Browser.addChoosableFileFilter(new FileNameExtensionFilter("CSV Files", "csv"));
            Browser.setDialogTitle("Save to file");
            Browser.setCurrentDirectory(new File(config.programDir));
            Browser.setSelectedFile(new File(config.programDir, tivoName + "_" + TableUtil.currentYearMonthDay() + ".csv"));
            final File selectedFile;
            if (Browser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION)
               selectedFile = Browser.getSelectedFile();
            else
               selectedFile = null;
            if (selectedFile != null) {
               Runnable task = new Runnable() {
                  @Override public void run() {
                     log.warn("Exporting '" + tivoName + "' todo list to csv file: " + selectedFile.getAbsolutePath());
                     Remote r = config.initRemote(tivoName);
                     if (r.success) {
                        r.TodoExportCSV(selectedFile);
                        r.disconnect();
                     }
                  }
               };
               new Thread(task).start();
            }
         }
      });

      JButton trim = new JButton("Select repeats");
      trim.setToolTipText(tooltip.getToolTip("trim_todo"));
      trim.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            final String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null)
               tab.trimRepeats(tivoName);
         }
      });

      label = new JLabel();

      row1.add(title);
      row1.add(tivo_label);
      row1.add(tivo);
      row1.add(refresh);
      row1.add(cancel);
      row1.add(modify);
      row1.add(export);
      row1.add(trim);
      row1.add(label);

      tab = new todoTable();

      panel = new JPanel(new BorderLayout());
      panel.add(row1, BorderLayout.NORTH);
      panel.add(new JScrollPane(tab.TABLE), BorderLayout.CENTER); // stretch vertically
   }
}
