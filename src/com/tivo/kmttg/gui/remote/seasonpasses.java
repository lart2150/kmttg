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
import java.util.Stack;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.spTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.log;

public class seasonpasses {
   public JPanel panel = null;
   public spTable tab = null;
   public JComboBox<String> tivo = null;
   public JButton copy = null;
   public JButton conflicts = null;
   public JButton modify = null;
   public JButton upcoming = null;
   public JButton reorder = null;

   public seasonpasses(final JFrame frame) {

      // Season Passes Tab items
      JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

      JLabel title = new JLabel("Season Passes");

      JLabel tivo_label = new JLabel();

      tivo = new JComboBox<String>();
      tivo.addActionListener(new ActionListener() {
         @Override public void actionPerformed(ActionEvent e) {
            String newVal = (String)tivo.getSelectedItem();
            if (newVal != null && config.gui.remote_gui != null) {
               TableUtil.clear(tab.TABLE);
               String tivoName = newVal;
               config.gui.remote_gui.updateButtonStates(tivoName, "Season Passes");
               if (tab.tivo_data.containsKey(tivoName))
                  tab.AddRows(tivoName, tab.tivo_data.get(tivoName));
               tab.updateLoadedStatus();
            }
         }
      });
      tivo.setToolTipText(tooltip.getToolTip("tivo_sp"));

      JButton refresh = new JButton("Refresh");
      refresh.setToolTipText(tooltip.getToolTip("refresh_sp"));
      refresh.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // Refresh SP list
            TableUtil.clear(tab.TABLE);
            tab.setLoaded(false);
            SPListCB((String)tivo.getSelectedItem());
         }
      });

      JButton save = new JButton("Save...");
      save.setToolTipText(tooltip.getToolTip("save_sp"));
      save.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // Save SP data to a file
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               if (tab.isTableLoaded()) {
                  log.error("Cannot save loaded Season Passes");
                  return;
               }  else {
                  JFileChooser Browser = config.gui.remote_gui.Browser;
                  Browser.resetChoosableFileFilters();
                  Browser.addChoosableFileFilter(new FileNameExtensionFilter("SP Files", "sp"));
                  Browser.setDialogTitle("Save to file");
                  Browser.setCurrentDirectory(new File(config.programDir));
                  Browser.setSelectedFile(new File(config.programDir, tivoName + ".sp"));
                  final File selectedFile;
                  if (Browser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION)
                     selectedFile = Browser.getSelectedFile();
                  else
                     selectedFile = null;
                  if (selectedFile != null) {
                     tab.SPListSave(tivoName, selectedFile.getAbsolutePath());
                  }
               }
            }
         }
      });

      JButton load = new JButton("Load...");
      load.setToolTipText(tooltip.getToolTip("load_sp"));
      load.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // Load SP data from a file
            JFileChooser Browser = config.gui.remote_gui.Browser;
            Browser.resetChoosableFileFilters();
            Browser.addChoosableFileFilter(new FileNameExtensionFilter("SP Files", "sp"));
            Browser.setDialogTitle("Load from file");
            Browser.setCurrentDirectory(new File(config.programDir));
            final File selectedFile;
            if (Browser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION)
               selectedFile = Browser.getSelectedFile();
            else
               selectedFile = null;
            if (selectedFile != null) {
               tab.SPListLoad(selectedFile.getAbsolutePath());
            }
         }
      });

      JButton export = new JButton("Export...");
      export.setToolTipText(tooltip.getToolTip("export_sp"));
      export.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // Export SP data to a file in csv format
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               if (tab.isTableLoaded()) {
                  log.error("Cannot export loaded Season Passes");
                  return;
               }  else {
                  JFileChooser Browser = config.gui.remote_gui.Browser;
                  Browser.resetChoosableFileFilters();
                  Browser.addChoosableFileFilter(new FileNameExtensionFilter("CSV Files", "csv"));
                  Browser.setDialogTitle("Export to csv file");
                  Browser.setCurrentDirectory(new File(config.programDir));
                  Browser.setSelectedFile(new File(config.programDir, tivoName + "" + ".csv"));
                  final File selectedFile;
                  if (Browser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION)
                     selectedFile = Browser.getSelectedFile();
                  else
                     selectedFile = null;
                  if (selectedFile != null) {
                     tab.SPListExport(tivoName, selectedFile.getAbsolutePath());
                  }
               }
            }
         }
      });

      copy = new JButton("Copy");
      copy.setToolTipText(tooltip.getToolTip("copy_sp"));
      copy.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // Copy selected SPs to a TiVo
            // Build list of eligible TiVos
            String thisTivo = (String)tivo.getSelectedItem();
            Stack<String> all = config.getTivoNames();
            for (int i=0; i<all.size(); ++i) {
               String tivo = all.get(i);
               if (! config.rpcEnabled(tivo) && ! config.mindEnabled(tivo)) {
                  all.remove(i);
                  continue;
               }
               if (! config.nplCapable(tivo)) {
                  all.remove(i);
                  continue;
               }
            }

            // Prompt user to choose a TiVo
            String tivoName = (String)JOptionPane.showInputDialog(
               frame, "Choose which TiVo to copy to", "Copy To",
               JOptionPane.QUESTION_MESSAGE, null, all.toArray(new String[0]), all.get(0)
            );
            if (tivoName != null && tivoName.length() > 0) {
               if (tivoName.equals(thisTivo)) {
                  // Don't copy to self unless in loaded state
                  if (! tab.isTableLoaded()) {
                     log.error("Destination TiVo is same as source TiVo: " + tivoName);
                     return;
                  }
               }
               tab.SPListCopy(tivoName);
            }
         }
      });

      JButton delete = new JButton("Delete");
      delete.setToolTipText(tooltip.getToolTip("delete_sp"));
      delete.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            tab.SPListDelete();
         }
      });

      modify = new JButton("Modify");
      modify.setToolTipText(tooltip.getToolTip("modify_sp"));
      modify.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // Modify selected SP
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               if (tab.isTableLoaded()) {
                  log.error("Cannot modify loaded Season Passes");
                  return;
               }  else {
                  tab.SPListModify(tivoName);
               }
            }
         }
      });

      reorder = new JButton("Re-order");
      reorder.setToolTipText(tooltip.getToolTip("reorder_sp"));
      reorder.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // Re-prioritize SPs on TiVo to match current table row order
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               if (tab.isTableLoaded()) {
                  log.error("Cannot re-order loaded Season Passes");
                  return;
               }  else {
                  tab.SPReorderCB(tivoName);
               }
            }
         }
      });

      upcoming = new JButton("Upcoming");
      upcoming.setToolTipText(tooltip.getToolTip("upcoming_sp"));
      upcoming.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            int selected[] = TableUtil.GetSelectedRows(tab.TABLE);
            if (selected.length > 0) {
               int row = selected[0];
               JSONObject json = tab.GetRowData(row);
               if (json.has("__upcoming")) {
                  // Get upcoming SP episodes and display in ToDo table
                  config.gui.remote_gui.todo_tab.tab.clear();
                  config.gui.remote_gui.todo_tab.label.setText("");
                  String tivoName = (String)tivo.getSelectedItem();
                  try {
                     if (tivoName != null && tivoName.length() > 0) {
                        jobData job = new jobData();
                        job.source          = tivoName;
                        job.tivoName        = tivoName;
                        job.type            = "remote";
                        job.name            = "Remote";
                        job.remote_upcoming = true;
                        job.rnpl            = json.getJSONArray("__upcoming");
                        job.todo            = config.gui.remote_gui.todo_tab.tab;
                        jobMonitor.submitNewJob(job);
                     }
                  } catch (JSONException e1) {
                     log.error("upcoming error - " + e1.getMessage());
                  }
               } else {
                  log.warn("No upcoming episodes scheduled for selected Season Pass");
               }
            }
         }
      });

      conflicts = new JButton("Conflicts");
      conflicts.setToolTipText(tooltip.getToolTip("conflicts_sp"));
      conflicts.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            int selected[] = TableUtil.GetSelectedRows(tab.TABLE);
            if (selected.length > 0) {
               int row = selected[0];
               JSONObject json = tab.GetRowData(row);
               if (json.has("__conflicts")) {
                  // Get conflict SP episodes and display in Won't Record table
                  config.gui.remote_gui.cancel_tab.tab.clear();
                  String tivoName = (String)tivo.getSelectedItem();
                  try {
                     if (tivoName != null && tivoName.length() > 0) {
                        jobData job = new jobData();
                        job.source           = tivoName;
                        job.tivoName         = tivoName;
                        job.type             = "remote";
                        job.name             = "Remote";
                        job.remote_conflicts = true;
                        job.rnpl             = json.getJSONArray("__conflicts");
                        job.cancelled        = config.gui.remote_gui.cancel_tab.tab;
                        jobMonitor.submitNewJob(job);
                     }
                  } catch (JSONException e1) {
                     log.error("conflicts error - " + e1.getMessage());
                  }
               } else {
                  log.warn("No conflicting episodes for selected Season Pass");
               }
            }
         }
      });

      row1.add(title);
      row1.add(tivo_label);
      row1.add(tivo);
      row1.add(refresh);
      row1.add(save);
      row1.add(load);
      row1.add(export);
      row1.add(delete);
      row1.add(copy);
      row1.add(modify);
      row1.add(reorder);
      row1.add(upcoming);
      row1.add(conflicts);

      tab = new spTable();

      panel = new JPanel(new BorderLayout());
      panel.add(row1, BorderLayout.NORTH);
      panel.add(new JScrollPane(tab.TABLE), BorderLayout.CENTER); // stretch vertically
   }

   // Submit remote SP request to Job Monitor
   public void SPListCB(String tivoName) {
      jobData job = new jobData();
      job.source      = tivoName;
      job.tivoName    = tivoName;
      job.type        = "remote";
      job.name        = "Remote";
      job.remote_sp   = true;
      job.sp          = tab;
      jobMonitor.submitNewJob(job);
   }

}
