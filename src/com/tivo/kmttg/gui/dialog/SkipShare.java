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

import java.io.File;
import java.util.Hashtable;
import java.util.Optional;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.MyTooltip;
import com.tivo.kmttg.gui.gui;
import com.tivo.kmttg.gui.remote.util;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.main.tivoFileName;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class SkipShare {
   private Stage frame = null;
   private Stage dialog = null;
   private FileChooser FileBrowser = null;
   private CheckBox debug = null;
   private TextField zipFile = null;
   private TextField srtFile = null;
   private String tivo = null;
   private JSONObject json = null;
   
   public SkipShare(Stage frame, String tivoName, Hashtable<String,String> entry, String zipFileName, String srtFileName) {
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
      Alert alert = new Alert(AlertType.CONFIRMATION);
      alert.setTitle("Confirm");
      config.gui.setFontSize(alert, config.FontSize);
      alert.setContentText("Local srt file for this show not detected. Download and create it?");
      Optional<ButtonType> result = alert.showAndWait();
      if (result.get() == ButtonType.OK) {
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
      FileBrowser = new FileChooser(); FileBrowser.setInitialDirectory(new File(config.mpegDir));
      // Define content for dialog window
      VBox content = new VBox();
      content.setPadding(new Insets(5,5,5,5));
      content.setSpacing(5);
      
      GridPane panel = new GridPane();      
      panel.setAlignment(Pos.CENTER);
      panel.setVgap(5);
      panel.setHgap(5);
      panel.getColumnConstraints().addAll(util.cc_none(), util.cc_stretch());
      panel.setPadding(new Insets(5,5,5,5)); // top, right, bottom, left

      // Import button
      Button Import = new Button("Import");
      String tip = "<b>Import</b><br>Import skip share zip file and local srt file into AutoSkip table.";
      Import.setTooltip(MyTooltip.make(tip));
      Import.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
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
               dialog.close();
            } else {
               log.error("Skip share import failed");
            }
         }
      });
      
      // debug boolean
      debug = new CheckBox("ENABLE DEBUG");
      tip = "<b>ENABLE DEBUG</b><br>Print debug info to message window.";
      debug.setTooltip(MyTooltip.make(tip));      
      panel.add(Import, 0, 0);
      panel.add(debug, 1, 0);

      // Row 2 = zipFile
      Button zipFile_button = new Button("Skip Share Zip File...");
      tip = "<b>Skip Share Zip File...</b><br>Browse for skip share zip file";
      zipFile_button.setTooltip(MyTooltip.make(tip));      
      zipFile_button.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            File selectedFile = FileBrowser.showOpenDialog(config.gui.getFrame());
            if (selectedFile != null) {
               zipFile.setText(selectedFile.getPath());
            }
         }
      });
      zipFile = new TextField();
      tip = "Skip share zip file containing cut points and srt file generated by someone else";
      zipFile.setTooltip(MyTooltip.make(tip));      
      zipFile.setPrefWidth(80);
      panel.add(zipFile_button, 0, 1);
      panel.add(zipFile, 1, 1);
      
      // Row 3 = srtFile
      Button srtFile_button = new Button("Local srt File...");
      tip = "<b>Local srt File...</b><br>Browse for local srt captions file used for time sync";
      srtFile_button.setTooltip(MyTooltip.make(tip));      
      srtFile_button.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            File selectedFile = FileBrowser.showOpenDialog(config.gui.getFrame());
            if (selectedFile != null) {
               srtFile.setText(selectedFile.getPath());
            }
         }
      });
      srtFile = new TextField();
      tip = "srt captions file generated by you and used for time sync";
      srtFile.setTooltip(MyTooltip.make(tip));      
      srtFile.setPrefWidth(80);
      panel.add(srtFile_button, 0, 2);
      panel.add(srtFile, 1, 2);
      content.getChildren().add(panel);

      dialog = new Stage();
      dialog.initOwner(frame);
      gui.LoadIcons(dialog);
      dialog.setTitle("Skip Share Import");
      Scene scene = new Scene(new VBox());
      config.gui.setFontSize(scene, config.FontSize);
      ((VBox) scene.getRoot()).getChildren().add(content);
      dialog.setScene(scene);
      dialog.setMinWidth(800);
      dialog.show();      
   }
}
