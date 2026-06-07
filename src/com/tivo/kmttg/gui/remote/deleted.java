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
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.deletedTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.log;

public class deleted {
   public JPanel panel = null;
   public deletedTable tab = null;
   public JComboBox<String> tivo = null;
   public JButton refresh = null;
   public JLabel label = null;
   public JButton recover = null;
   public JButton permDelete = null;
   public JTextField filter = null;

   public deleted(final JFrame frame) {

      // Deleted table items
      JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

      JLabel title = new JLabel("Recently Deleted list");

      JLabel tivo_label = new JLabel();

      tivo = new JComboBox<String>();
      tivo.addActionListener(new ActionListener() {
         @Override public void actionPerformed(ActionEvent e) {
            String newVal = (String)tivo.getSelectedItem();
            if (newVal != null && config.gui.remote_gui != null) {
               // TiVo selection changed for Deleted tab
               TableUtil.clear(tab.TABLE);
               label.setText("");
               String tivoName = newVal;
               config.gui.remote_gui.updateButtonStates(tivoName, "Deleted");
               if (tab.tivo_data.containsKey(tivoName))
                  tab.AddRows(tivoName, tab.tivo_data.get(tivoName));
            }
         }
      });
      tivo.setToolTipText(tooltip.getToolTip("tivo_deleted"));

      refresh = new JButton("Refresh");
      refresh.setToolTipText(tooltip.getToolTip("refresh_deleted"));
      refresh.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // Refresh deleted list
            TableUtil.clear(tab.TABLE);
            label.setText("");
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               deletedJob(tivoName);
            }
         }
      });

      recover = new JButton("Recover");
      recover.setToolTipText(tooltip.getToolTip("recover_deleted"));
      recover.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               tab.recoverSingle(tivoName);
            }
         }
      });

      permDelete = new JButton("Permanently Delete");
      permDelete.setToolTipText(tooltip.getToolTip("permDelete_deleted"));
      permDelete.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               Runnable task = new Runnable() {
                  @Override public void run() {
                        tab.permanentlyDelete(tivoName);
                  }
               };
               new Thread(task).start();
            }
         }
      });

      JButton export = new JButton("Export");
      export.setToolTipText(tooltip.getToolTip("export_deleted"));
      export.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName == null || tivoName.length() == 0) {
               log.warn("select a tivo to export the deleted list" );
               return;
            }
            JFileChooser Browser = config.gui.remote_gui.Browser;
            Browser.resetChoosableFileFilters();
            Browser.addChoosableFileFilter(new FileNameExtensionFilter("CSV Files", "csv"));
            Browser.setDialogTitle("Save to file");
            Browser.setCurrentDirectory(new File(config.programDir));
            Browser.setSelectedFile(new File(config.programDir, tivoName + "_deleted.csv"));
            final String ftivoName = tivoName;
            final File selectedFile;
            if (Browser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION)
               selectedFile = Browser.getSelectedFile();
            else
               selectedFile = null;
            if (selectedFile != null) {
               Runnable task = new Runnable() {
                  @Override public void run() {
                     log.warn("Exporting '" + ftivoName + "' deleted list to csv file: " + selectedFile.getAbsolutePath());
                     Remote r = config.initRemote(ftivoName);
                     if (r.success) {
                        jobData job = deletedJob(ftivoName);
                        r.DeletedShowsCSV(selectedFile, job);
                        r.disconnect();
                     }
                  }
               };
               new Thread(task).start();
            }
         }
      });

      label = new JLabel();

      // Filter the table by show title (live, as you type)
      JLabel filter_label = new JLabel("Filter:");
      filter = new JTextField(15);
      filter.setToolTipText("Filter the list by show title");
      filter.getDocument().addDocumentListener(new DocumentListener() {
         @Override public void insertUpdate(DocumentEvent e) { apply(); }
         @Override public void removeUpdate(DocumentEvent e) { apply(); }
         @Override public void changedUpdate(DocumentEvent e) { apply(); }
         private void apply() { tab.setFilter(filter.getText()); }
      });

      row1.add(title);
      row1.add(tivo_label);
      row1.add(tivo);
      row1.add(refresh);
      row1.add(recover);
      row1.add(permDelete);
      row1.add(export);
      row1.add(filter_label);
      row1.add(filter);
      row1.add(label);

      tab = new deletedTable();

      panel = new JPanel(new BorderLayout());
      panel.add(row1, BorderLayout.NORTH);
      panel.add(new JScrollPane(tab.TABLE), BorderLayout.CENTER); // stretch vertically

   }

   private jobData deletedJob(String tivoName) {
      jobData job = new jobData();
      job.source         = tivoName;
      job.tivoName       = tivoName;
      job.type           = "remote";
      job.name           = "Remote";
      job.remote_deleted = true;
      job.deleted        = tab;
      jobMonitor.submitNewJob(job);
      return job;
   }
}
