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

import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.channelsTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.log;

public class channels {
   public JPanel panel = null;
   public channelsTable tab = null;
   public JButton refresh = null;
   public JButton copy = null;
   public JButton update = null;
   public JLabel label = null;
   public JComboBox<String> tivo = null;

   public channels(final JFrame frame) {

      // Channels tab items
      JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

      JLabel title = new JLabel("Channels");

      JLabel tivo_label = new JLabel();

      tivo = new JComboBox<String>();
      tivo.addActionListener(new ActionListener() {
         @Override public void actionPerformed(ActionEvent e) {
            String newVal = (String)tivo.getSelectedItem();
            if (newVal != null && config.gui.remote_gui != null) {
               // TiVo selection changed for Channels tab
               TableUtil.clear(tab.TABLE);
               label.setText("");
               String tivoName = newVal;
               config.gui.remote_gui.updateButtonStates(tivoName, "Channels");
               if (tab.tivo_data.containsKey(tivoName))
                  tab.AddRows(tivoName, tab.tivo_data.get(tivoName));
               tab.updateLoadedStatus();
            }
         }
      });
      tivo.setToolTipText(tooltip.getToolTip("tivo_channels"));

      JButton save = new JButton("Save...");
      save.setToolTipText(tooltip.getToolTip("save_channels"));
      save.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // Save channels list
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               if (tab.isTableLoaded()) {
                  log.error("Cannot save loaded Channels");
                  return;
               }  else {
                  JFileChooser Browser = config.gui.remote_gui.Browser;
                  Browser.resetChoosableFileFilters();
                  Browser.addChoosableFileFilter(new FileNameExtensionFilter("Channel Files", "chan"));
                  Browser.setDialogTitle("Save to file");
                  Browser.setCurrentDirectory(new File(config.programDir));
                  Browser.setSelectedFile(new File(config.programDir, tivoName + ".chan"));
                  final File selectedFile;
                  if (Browser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION)
                     selectedFile = Browser.getSelectedFile();
                  else
                     selectedFile = null;
                  if (selectedFile != null) {
                     tab.saveChannels(tivoName, selectedFile.getAbsolutePath());
                  }
               }
            }
         }
      });

      JButton load = new JButton("Load...");
      load.setToolTipText(tooltip.getToolTip("load_channels"));
      load.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // Load channels list
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               JFileChooser Browser = config.gui.remote_gui.Browser;
               Browser.resetChoosableFileFilters();
               Browser.addChoosableFileFilter(new FileNameExtensionFilter("Channel Files", "chan"));
               Browser.setDialogTitle("Load channels file");
               Browser.setCurrentDirectory(new File(config.programDir));
               Browser.setSelectedFile(new File(config.programDir, tivoName + ".chan"));
               final File selectedFile;
               if (Browser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION)
                  selectedFile = Browser.getSelectedFile();
               else
                  selectedFile = null;
               if (selectedFile != null) {
                  label.setText("");
                  tab.loadChannels(selectedFile.getAbsolutePath());
               }
            }
         }
      });

      JButton export_channels = new JButton("Export ...");
      export_channels.setToolTipText(tooltip.getToolTip("export_channels"));
      export_channels.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            final String tivoName = (String)tivo.getSelectedItem();
            JFileChooser Browser = config.gui.remote_gui.Browser;
            Browser.resetChoosableFileFilters();
            Browser.addChoosableFileFilter(new FileNameExtensionFilter("CSV Files", "csv"));
            Browser.setDialogTitle("Save to file");
            Browser.setCurrentDirectory(new File(config.programDir));
            Browser.setSelectedFile(new File(config.programDir, tivoName + "_channels.csv"));
            final File selectedFile;
            if (Browser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION)
               selectedFile = Browser.getSelectedFile();
            else
               selectedFile = null;
            if (selectedFile != null) {
               Runnable task = new Runnable() {
                  @Override public void run() {
                     log.warn("Exporting '" + tivoName + "' channel list to csv file: " + selectedFile.getAbsolutePath());
                     Remote r = config.initRemote(tivoName);
                     if (r.success) {
                        r.ChannelLineupCSV(selectedFile);
                        r.disconnect();
                     }
                  }
               };
               new Thread(task).start();
            }
         }
      });

      copy = new JButton("Copy");
      copy.setToolTipText(tooltip.getToolTip("copy_channels"));
      copy.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // Copy selected channel settings to a TiVo
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
               tab.copyChannels(tivoName);
            }
         }
      });

      refresh = new JButton("Refresh");
      refresh.setToolTipText(tooltip.getToolTip("refresh_channels"));
      refresh.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // Refresh channels list
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               label.setText("");
               tab.refreshChannels(tivoName);
            }
         }
      });

      update = new JButton("Modify");
      update.setToolTipText(tooltip.getToolTip("update_channels"));
      update.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // Update channels list
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0)
               label.setText("");
               tab.updateChannels(tivoName);
         }
      });

      label = new JLabel();

      row1.add(title);
      row1.add(tivo_label);
      row1.add(tivo);
      row1.add(refresh);
      row1.add(save);
      row1.add(load);
      row1.add(export_channels);
      row1.add(copy);
      row1.add(update);
      row1.add(label);

      tab = new channelsTable();

      panel = new JPanel(new BorderLayout());
      panel.add(row1, BorderLayout.NORTH);
      panel.add(new JScrollPane(tab.TABLE), BorderLayout.CENTER); // stretch vertically
   }
}
