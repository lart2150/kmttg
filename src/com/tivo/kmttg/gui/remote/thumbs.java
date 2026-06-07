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
import com.tivo.kmttg.gui.table.thumbsTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.log;

public class thumbs {
   public JPanel panel = null;
   public thumbsTable tab = null;
   public JButton refresh = null;
   public JButton copy = null;
   public JButton update = null;
   public JLabel label = null;
   public JComboBox<String> tivo = null;

   public thumbs(final JFrame frame) {

      // Thumbs tab items
      JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

      JLabel title = new JLabel("Thumb Ratings");

      JLabel tivo_label = new JLabel();

      tivo = new JComboBox<String>();
      tivo.addActionListener(new ActionListener() {
         @Override public void actionPerformed(ActionEvent e) {
            String newVal = (String)tivo.getSelectedItem();
            if (newVal != null && config.gui.remote_gui != null) {
               // TiVo selection changed for Thumbs tab
               TableUtil.clear(tab.TABLE);
               label.setText("");
               String tivoName = newVal;
               config.gui.remote_gui.updateButtonStates(tivoName, "Thumbs");
               if (tab.tivo_data.containsKey(tivoName))
                  tab.AddRows(tivoName, tab.tivo_data.get(tivoName));
               tab.updateLoadedStatus();
            }
         }
      });
      tivo.setToolTipText(tooltip.getToolTip("tivo_thumbs"));

      JButton save = new JButton("Save...");
      save.setToolTipText(tooltip.getToolTip("save_thumbs"));
      save.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // Save thumbs list
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               if (tab.isTableLoaded()) {
                  log.error("Cannot save loaded Thumbs");
                  return;
               }  else {
                  JFileChooser Browser = config.gui.remote_gui.Browser;
                  Browser.resetChoosableFileFilters();
                  Browser.addChoosableFileFilter(new FileNameExtensionFilter("Thumbs Files", "thumbs"));
                  Browser.setDialogTitle("Save to file");
                  Browser.setCurrentDirectory(new File(config.programDir));
                  Browser.setSelectedFile(new File(config.programDir, tivoName + ".thumbs"));
                  final File selectedFile;
                  if (Browser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION)
                     selectedFile = Browser.getSelectedFile();
                  else
                     selectedFile = null;
                  if (selectedFile != null) {
                     tab.saveThumbs(tivoName, selectedFile.getAbsolutePath());
                  }
               }
            }
         }
      });

      JButton load = new JButton("Load...");
      load.setToolTipText(tooltip.getToolTip("load_thumbs"));
      load.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // Load thumbs list
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               JFileChooser Browser = config.gui.remote_gui.Browser;
               Browser.resetChoosableFileFilters();
               Browser.addChoosableFileFilter(new FileNameExtensionFilter("Thumbs Files", "thumbs"));
               Browser.setDialogTitle("Load thumbs file");
               Browser.setCurrentDirectory(new File(config.programDir));
               Browser.setSelectedFile(new File(config.programDir, tivoName + ".thumbs"));
               final File selectedFile;
               if (Browser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION)
                  selectedFile = Browser.getSelectedFile();
               else
                  selectedFile = null;
               if (selectedFile != null) {
                  label.setText("");
                  tab.loadThumbs(selectedFile.getAbsolutePath());
               }
            }
         }
      });

      copy = new JButton("Copy");
      copy.setToolTipText(tooltip.getToolTip("copy_thumbs"));
      copy.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // Copy selected thumbs to a TiVo
            // Build list of eligible TiVos (series 4 and later and no Minis)
            String thisTivo = (String)tivo.getSelectedItem();
            Stack<String> all = config.getTivoNames();
            for (int i=0; i<all.size(); ++i) {
               String tivo = all.get(i);
               if (! config.rpcEnabled(tivo)) {
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
               tab.copyThumbs(tivoName);
            }
         }
      });

      refresh = new JButton("Refresh");
      refresh.setToolTipText(tooltip.getToolTip("refresh_thumbs"));
      refresh.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // Refresh thumbs list
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               label.setText("");
               tab.refreshThumbs(tivoName);
            }
         }
      });

      update = new JButton("Modify");
      update.setToolTipText(tooltip.getToolTip("update_thumbs"));
      update.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // Update thumbs list
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0)
               label.setText("");
               tab.updateThumbs(tivoName);
         }
      });

      label = new JLabel();

      row1.add(title);
      row1.add(tivo_label);
      row1.add(tivo);
      row1.add(refresh);
      row1.add(save);
      row1.add(load);
      row1.add(copy);
      row1.add(update);
      row1.add(label);

      tab = new thumbsTable();

      panel = new JPanel(new BorderLayout());
      panel.add(row1, BorderLayout.NORTH);
      panel.add(new JScrollPane(tab.TABLE), BorderLayout.CENTER); // stretch vertically
   }
}
