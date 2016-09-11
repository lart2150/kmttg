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
package com.tivo.kmttg.gui;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class slingboxgui {
   private GridPane panel = null;
   private TextField dir;
   private TextField perl;
   private TextField filename;
   private TextField ip;
   private TextField port;
   private TextField pass;
   private TextField dur;
   private TextField chan;
   private ChoiceBox<String> type;
   private ChoiceBox<String> vbw;
   private ChoiceBox<String> res;
   private ChoiceBox<String> container;
   private CheckBox raw;
   jobData job = null;
   FileChooser fileBrowser = null;
   DirectoryChooser dirBrowser = null;
   
   slingboxgui(Stage frame) {
      getPanel();
      fileBrowser = new FileChooser();
      fileBrowser.setTitle("Choose file");
      fileBrowser.setInitialDirectory(new File(config.programDir));
      dirBrowser = new DirectoryChooser();
      dirBrowser.setTitle("Choose directory");
      dirBrowser.setInitialDirectory(new File(config.programDir));
   }
   
   public GridPane getPanel() {
      if (panel == null) {
         panel = new GridPane();
         panel.setAlignment(Pos.CENTER);
         panel.setHgap(5);
         panel.setVgap(1);
         
         Button start = new Button("Start");
         start.setTooltip(getToolTip("start"));
         start.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               String slingbox_file = getFileName();
               if (slingbox_file == null)
                  return;
               updateConfig();
               
               // Sanity checking
               Boolean OK = true;
               if (config.slingBox_dir.length() == 0) {
                  log.error("Slingbox capture file directory not specified.");
                  OK = false;
               }
               if (config.slingBox_perl.length() == 0) {
                  log.error("Perl executable not specified.");
                  OK = false;
               }
               if (config.slingBox_ip.length() == 0) {
                  log.error("Slingbox IP not specified.");
                  OK = false;
               }
               if (config.slingBox_port.length() == 0) {
                  log.error("Slingbox port not specified.");
                  OK = false;
               }
               if (config.slingBox_pass.length() == 0) {
                  log.error("Slingbox password not specified.");
                  OK = false;
               }
               
               // Proceed
               if (OK) {
                  job = new jobData();
                  job.source        = "slingbox";
                  job.type          = "slingbox";
                  job.name          = "Slingbox";
                  job.tivoName      = "Slingbox";
                  job.slingbox_perl = perl.getText();
                  job.slingbox_file = slingbox_file;
                  job.slingbox_raw  = raw.isSelected();
                  String d = string.removeLeadingTrailingSpaces(dur.getText());
                  if (d.length() > 0 && ! d.equals("0")) {
                     try {
                        float f = Float.parseFloat(d);
                        if (f > 0)
                           job.slingbox_dur = "" + f*60;
                     } catch (NumberFormatException ex) {
                        // Do nothing here
                     }
                  }
                  String c = string.removeLeadingTrailingSpaces(chan.getText());
                  if (c.length() > 0) {
                     try {
                        int n = Integer.parseInt(c);
                        if (n >= 0)
                           job.slingbox_chan = "" + n;
                     } catch (NumberFormatException ex) {
                        // Do nothing here
                     }
                  }
                  jobMonitor.submitNewJob(job);
               }
            }
         });
         
         Button stop = new Button("Stop");
         stop.setTooltip(getToolTip("stop"));
         stop.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               if (job != null) {
                  jobMonitor.kill(job);
                  job = null;
               }
            }
         });
         
         Button Help = new Button("Help");
         Help.setTooltip(getToolTip("help"));
         Help.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               help.showInBrowser("http://sourceforge.net/p/kmttg/wiki/slingbox_capture/");
            }
         });
         
         Label dir_label = new Label("Slingbox capture file directory");
         dir = new TextField(); dir.setMinWidth(30);
         dir.setTooltip(getToolTip("dir"));
         dir.setText(config.slingBox_dir);
         dir.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
               if( mouseEvent.getButton().equals(MouseButton.PRIMARY) ) {
                  if (mouseEvent.getClickCount() == 2) {
                     File result = dirBrowser.showDialog(config.gui.getFrame());
                     if (result != null) {
                        dir.setText(result.getAbsolutePath());
                     }
                  }
               }
            }
         });
         
         Label perl_label = new Label("Perl executable");
         perl = new TextField(); perl.setMinWidth(30);
         perl.setTooltip(getToolTip("perl"));
         perl.setText(config.slingBox_perl);
         perl.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
               if( mouseEvent.getButton().equals(MouseButton.PRIMARY) ) {
                  if (mouseEvent.getClickCount() == 2) {
                     File result = fileBrowser.showOpenDialog(config.gui.getFrame());
                     if (result != null) {
                        perl.setText(result.getPath());
                     }
                  }
               }
            }
         });
         
         Label filename_label = new Label("File name");
         filename = new TextField(); filename.setMinWidth(30);
         filename.setTooltip(getToolTip("filename"));
         
         Label pass_label = new Label("Slingbox password");
         pass = new TextField(); pass.setMinWidth(30);
         pass.setTooltip(getToolTip("pass"));
         pass.setText(config.slingBox_pass);
         
         Label ip_label = new Label("Slingbox IP");
         ip = new TextField(); ip.setMinWidth(30);
         ip.setTooltip(getToolTip("ip"));
         ip.setText(config.slingBox_ip);
         
         Label port_label = new Label("Slingbox port");
         port = new TextField(); port.setMinWidth(30);
         port.setTooltip(getToolTip("ip"));
         port.setText(config.slingBox_port);
         
         Label dur_label = new Label("Capture # minutes");
         dur = new TextField(); dur.setMinWidth(30);
         dur.setTooltip(getToolTip("dur"));
         dur.setText("0");
         
         Label chan_label = new Label("Tune to channel");
         chan = new TextField(); chan.setMinWidth(30);
         chan.setTooltip(getToolTip("chan"));
         chan.setText("");
         
         Label res_label = new Label("Video resolution");
         res = new ChoiceBox<String>();
         res.setTooltip(getToolTip("res"));
         res.getItems().addAll("1920x1080", "640x480");
         res.setValue(config.slingBox_res);
         
         Label vbw_label = new Label("Video bit rate (Kbps)");
         vbw = new ChoiceBox<String>();
         vbw.setTooltip(getToolTip("vbw"));
         vbw.getItems().addAll("4000", "5000", "6000", "7000");
         vbw.setValue(config.slingBox_vbw);
         
         Label type_label = new Label("Slingbox model");
         type = new ChoiceBox<String>();
         type.setTooltip(getToolTip("type"));
         type.getItems().add("Slingbox 350/500");
         type.getItems().add("Slingbox Pro HD");
         type.getItems().add("Slingbox Pro");
         type.getItems().add("Slingbox Solo");
         type.setValue(config.slingBox_type);
         
         Label container_label = new Label("Video container to use");
         container = new ChoiceBox<String>();
         container.setTooltip(getToolTip("container"));
         container.getItems().add("mpegts");
         container.getItems().add("matroska");
         container.setValue(config.slingBox_container);
         
         raw = new CheckBox("Capture raw file");
         raw.setTooltip(getToolTip("raw"));
         raw.setSelected(false);
                                    
         int gy = 0;         
         HBox row = new HBox();
         row.setSpacing(10);
         row.setPadding(new Insets(0,0,5,0));
         row.getChildren().addAll(start, stop, Help);
         panel.add(row, 1, gy);
         panel.add(raw, 2, gy);

         gy++;
         panel.add(ip_label, 0, gy);
         panel.add(ip, 1, gy);
         panel.add(port_label, 2, gy);
         panel.add(port, 3, gy);
         
         gy++;
         panel.add(pass_label, 0, gy);
         panel.add(pass, 1, gy);
         panel.add(dir_label, 2, gy);
         panel.add(dir, 3, gy);
         
         gy++;
         panel.add(perl_label, 0, gy);
         panel.add(perl, 1, gy);
         panel.add(filename_label, 2, gy);
         panel.add(filename, 3, gy);
         
         gy++;
         panel.add(type_label, 0, gy);
         panel.add(type, 1, gy);
         panel.add(container_label, 2, gy);
         panel.add(container, 3, gy);
         
         gy++;
         panel.add(res_label, 0, gy);
         panel.add(res, 1, gy);
         panel.add(vbw_label, 2, gy);
         panel.add(vbw, 3, gy);
         
         gy++;
         panel.add(dur_label, 0, gy);
         panel.add(dur, 1, gy);
         panel.add(chan_label, 2, gy);
         panel.add(chan, 3, gy);         
      }
      return panel;
   }
   
   public void updateConfig() {
      config.slingBox_dir = string.removeLeadingTrailingSpaces(dir.getText());
      config.slingBox_perl = string.removeLeadingTrailingSpaces(perl.getText());
      config.slingBox_pass = string.removeLeadingTrailingSpaces(pass.getText());
      config.slingBox_ip = string.removeLeadingTrailingSpaces(ip.getText());
      config.slingBox_port = string.removeLeadingTrailingSpaces(port.getText());
      config.slingBox_vbw = vbw.getValue();
      config.slingBox_res = res.getValue();
      config.slingBox_type = type.getValue();
      config.slingBox_container = container.getValue();
   }
   
   private String getTimeStamp() {
      long now = new Date().getTime();
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
      return sdf.format(now);
   }
   
   private String getFileName() {
      String name;
      String d = string.removeLeadingTrailingSpaces(dir.getText());
      String c = string.removeLeadingTrailingSpaces(container.getValue());
      String f = string.removeLeadingTrailingSpaces(filename.getText());
      config.slingBox_container = c;
      if (d.length() == 0) {
         log.error("No slingbox directory specified. Aborting...");
         return null;
      }
      String ext = ".ts";
      if (c.equals("matroska"))
         ext = ".mkv";
      
      // kmttg assigned name
      name = d + File.separator + "slingbox_" + getTimeStamp() + ext;
      
      // If filename field not empty then use that file name instead
      if (f.length() > 0)
         name = d + File.separator + f;
      
      return name;
   }
   
   private Tooltip getToolTip(String component) {
      String text = "";
      if (component.equals("start")){
         text = "<b>Start</b><br>";
         text += "Start slingbox capture. The capture will continue until you press <b>Stop</b>.<br>";
         text += "A time stamped slingbox capture file will be created in the specified directory.<br>";
         text += "NOTE: This requires Perl with proper module. Read the kmttg <b>slingbox_capture</b> Wiki.";
      } 
      else if (component.equals("stop")) {
         text = "<b>Stop</b><br>";
         text += "Stop slingbox capture.";
      }
      else if (component.equals("help")) {
         text = "<b>Help</b><br>";
         text += "Click on this button to visit the kmttg <b>slingbox_capture</b> Wiki page<br>";
         text += "that has details on how to setup kmttg for Slingbox captures.";
      }
      else if (component.equals("ip")) {
         text = "<b>Slingbox IP or port</b><br>";
         text += "Slingbox IP or port. To find Slingbox IP & port:<br>";
         text += "While streaming Slingbox stream to browser, click on <b>Settings</b>, then<br>";
         text += "click on <b>NETWORK DETAILS</b> where you can see HOME IP Address and Network Port.";
      }
      else if (component.equals("pass")) {
         text = "<b>Slingbox password</b><br>";
         text += "Slingbox admin password. To find this password:<br>";
         text += "While streaming Slingbox stream to browser, visit following URL:<br>";
         text += "https://newwatchsecure.slingbox.com/watch/slingAccounts/account_boxes_js<br>";
         text += "Then look for <b>adminPassword</b> in the .js file.";
      }
      else if (component.equals("dir")) {
         text = "<b>Slingbox capture file directory</b><br>";
         text += "Directory in which to save Slingbox capture files.<br>";
         text += "NOTE: Double-click in this field to bring up file browser.";
      }
      else if (component.equals("perl")) {
         text = "<b>Perl executable</b><br>";
         text += "Full path to the Perl executable.<br>";
         text += "NOTE: Make sure to add Crypt::Tea_JS module via Perl Package Manager.<br>";
         text += "NOTE: Double-click in this field to bring up file browser.";
      }
      else if (component.equals("filename")) {
         text = "<b>File name</b><br>";
         text += "Optional file name to use for this capture.<br>";
         text += "If you leave this field empty then kmttg will create a unique file name<br>";
         text += "for the capture.";
      }
      else if (component.equals("res")) {
         text = "<b>Video resolution</b><br>";
         text += "Video resolution to use for the capture.<br>";
         text += "NOTE: This is only relevant for Slingbox 350/500 model";
      }
      else if (component.equals("vbw")) {
         text = "<b>Video bit rate (Kbps)</b><br>";
         text += "Video bit rate in Kbps to use for the capture.<br>";
         text += "NOTE: Slingbox 350/500 models only affected by this setting for 1920x1080 resolution";
      }
      else if (component.equals("type")) {
         text = "<b>Slingbox model</b><br>";
         text += "Choose which Slingbox model you have. kmttg uses a different Perl script and<br>";
         text += "options for older models vs newer models so it's important to choose the right one.";
      }
      else if (component.equals("container")) {
         text = "<b>Video container to use</b><br>";
         text += "Choose video container to use for the capture.<br>";
         text += "mpegts = mpeg2 transport stream container<br>";
         text += "matroska = mkv container<br>";
         text += "NOTE: If capturing from Slingbox Pro or Solo models and you plan on editing the capture using<br>";
         text += "VideoRedo TVSuite software, you should use <b>matroska</b> since otherwise VRD won't<br>";
         text += "be able to open the file since there is no frame rate information for TS captures.";
      }
      else if (component.equals("dur")) {
         text = "<b>Capture # minutes</b><br>";
         text += "Capture a specified number of minutes. 0 or empty means unlimited.<br>";
         text += "NOTE: This can be any number > 0 including non integers.";
      }
      else if (component.equals("chan")) {
         text = "<b>Tune to channel</b><br>";
         text += "Tune to specified channel # before starting capture. Empty means don't tune.<br>";
         text += "NOTE: This can be any number >= 0 or empty for none.";
      }
      else if (component.equals("raw")) {
         text = "<b>Capture raw file</b><br>";
         text += "If enabled then capture raw Slingbox file instead of using ffmpeg to remux<br>";
         text += "to selected video container and convert audio to ac3.";
      }
      return MyTooltip.make(text);
   }
}
