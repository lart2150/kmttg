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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Hashtable;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;

import net.miginfocom.swing.MigLayout;

import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.MyTooltip;
import com.tivo.kmttg.gui.swing.SwingUtil;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.main.tivoFileName;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class SkipShare {
   private JFrame frame = null;
   private JDialog dialog = null;
   private JFileChooser FileBrowser = null;
   private JCheckBox debug = null;
   private JTextField zipFile = null;
   private JTextField srtFile = null;
   private String tivo = null;
   private JSONObject json = null;

   public SkipShare(JFrame frame, String tivoName, Hashtable<String,String> entry, String zipFileName, String srtFileName) {
      this.frame = frame;
      this.tivo = tivoName;
      try {
         json = new JSONObject();
         json.put("title", entry.get("title"));
         json.put("offerId", entry.get("offerId"));
         json.put("contentId", entry.get("contentId"));
         json.put("duration", Long.parseLong(entry.get("duration")));
         if (! file.isFile(srtFileName)) {
            // Prompt to download/create srt file if it doesn't exist
            if (srtDownload(tivoName, entry))
               return;
         }
      } catch (JSONException e) {
         log.error("SkipShare - " + e.getMessage());
      }
      init();
      zipFile.setText(zipFileName);
      srtFile.setText(srtFileName);
   }

   private Boolean srtDownload(String tivoName, Hashtable<String,String> entry) {
      if (SwingUtil.confirm(config.gui.getFrame(), "Confirm",
            "Local srt file for this show not detected. Download and create it?")) {
         String startFile = tivoFileName.buildTivoFileName(entry);
         String mpegFile = config.mpegDir + File.separator + string.replaceSuffix(startFile, ".mpg");
         // tdownload_decrypt job
         config.tivolibreCompat = 1;
         jobData job = new jobData();
         job.startFile    = startFile;
         job.source       = entry.get("url_TiVoVideoDetails");
         job.url          = entry.get("url");
         job.tivoFileSize = Long.parseLong(entry.get("size"));
         job.tivoName     = tivoName;
         job.type         = "tdownload_decrypt";
         job.name         = "java";
         job.mpegFile     = mpegFile;
         job.mpegFile_cut = string.replaceSuffix(mpegFile, "_cut.ts");
         jobMonitor.submitNewJob(job);

         // captions job
         jobData job2 = new jobData();
         job2.source    = job.source;
         job2.startFile = startFile;
         job2.tivoName  = tivoName;
         job2.type      = "captions";
         job2.name      = config.ccextractor;
         job2.videoFile = mpegFile;
         job2.srtFile   = string.replaceSuffix(mpegFile, ".srt");;
         jobMonitor.submitNewJob(job2);

         return true;
      }
      return false;
   }

   private void init() {
      FileBrowser = new JFileChooser(); FileBrowser.setCurrentDirectory(new File(config.mpegDir));
      // Define content for dialog window
      JPanel content = new JPanel(new BorderLayout());

      JPanel panel = new JPanel(new MigLayout("gapx 5, gapy 5"));

      // Import button
      JButton Import = new JButton("Import");
      String tip = "<b>Import</b><br>Import skip share zip file and local srt file into AutoSkip table.";
      Import.setToolTipText(MyTooltip.make(tip));
      Import.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            String zip = string.removeLeadingTrailingSpaces(zipFile.getText());
            String srt = string.removeLeadingTrailingSpaces(srtFile.getText());
            if (! file.isFile(zip)) {
               log.error("zip file not found: " + zip);
               return;
            }
            if (! file.isFile(srt)) {
               log.error("srt file not found: " + srt);
               return;
            }
            if (com.tivo.kmttg.rpc.SkipShare.ZipImport(tivo, json, zip, srt, debug.isSelected())) {
               log.print("Successfully imported skip share");
               dialog.dispose();
            } else {
               log.error("Skip share import failed");
            }
         }
      });

      // debug boolean
      debug = new JCheckBox("ENABLE DEBUG");
      tip = "<b>ENABLE DEBUG</b><br>Print debug info to message window.";
      debug.setToolTipText(MyTooltip.make(tip));
      panel.add(Import, "cell 0 0");
      panel.add(debug, "cell 1 0");

      // Row 2 = zipFile
      JButton zipFile_button = new JButton("Skip Share Zip File...");
      tip = "<b>Skip Share Zip File...</b><br>Browse for skip share zip file";
      zipFile_button.setToolTipText(MyTooltip.make(tip));
      zipFile_button.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            if (FileBrowser.showOpenDialog(config.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
               File selectedFile = FileBrowser.getSelectedFile();
               zipFile.setText(selectedFile.getPath());
            }
         }
      });
      zipFile = new JTextField(80);
      tip = "Skip share zip file containing cut points and srt file generated by someone else";
      zipFile.setToolTipText(MyTooltip.make(tip));
      panel.add(zipFile_button, "cell 0 1");
      panel.add(zipFile, "cell 1 1, growx");

      // Row 3 = srtFile
      JButton srtFile_button = new JButton("Local srt File...");
      tip = "<b>Local srt File...</b><br>Browse for local srt captions file used for time sync";
      srtFile_button.setToolTipText(MyTooltip.make(tip));
      srtFile_button.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            if (FileBrowser.showOpenDialog(config.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
               File selectedFile = FileBrowser.getSelectedFile();
               srtFile.setText(selectedFile.getPath());
            }
         }
      });
      srtFile = new JTextField(80);
      tip = "srt captions file generated by you and used for time sync";
      srtFile.setToolTipText(MyTooltip.make(tip));
      panel.add(srtFile_button, "cell 0 2");
      panel.add(srtFile, "cell 1 2, growx");
      content.add(panel, BorderLayout.CENTER);

      dialog = new JDialog(frame);
      SwingUtil.loadIcons(dialog);
      dialog.setTitle("Skip Share Import");
      dialog.getContentPane().add(content);
      dialog.pack();
      if (dialog.getWidth() < 800)
         dialog.setSize(800, dialog.getHeight());
      dialog.setLocationRelativeTo(frame);
      dialog.setVisible(true);
   }
}
