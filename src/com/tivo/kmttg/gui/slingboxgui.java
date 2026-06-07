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

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import net.miginfocom.swing.MigLayout;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class slingboxgui {
   private JPanel panel = null;
   private JTextField dir;
   private JTextField perl;
   private JTextField filename;
   private JTextField ip;
   private JTextField port;
   private JTextField pass;
   private JTextField dur;
   private JTextField chan;
   private JComboBox<String> type;
   private JComboBox<String> vbw;
   private JComboBox<String> res;
   private JComboBox<String> container;
   private JCheckBox raw;
   jobData job = null;
   JFileChooser fileBrowser = null;
   JFileChooser dirBrowser = null;

   slingboxgui(JFrame frame) {
      getPanel();
      fileBrowser = new JFileChooser();
      fileBrowser.setDialogTitle("Choose file");
      fileBrowser.setCurrentDirectory(new File(config.programDir));
      dirBrowser = new JFileChooser();
      dirBrowser.setDialogTitle("Choose directory");
      dirBrowser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      dirBrowser.setCurrentDirectory(new File(config.programDir));
   }

   public JComponent getPanel() {
      if (panel == null) {
         panel = new JPanel(new MigLayout("gapx 5, gapy 1"));

         JButton start = new JButton("Start");
         start.setToolTipText(getToolTip("start"));
         start.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
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
         
         JButton stop = new JButton("Stop");
         stop.setToolTipText(getToolTip("stop"));
         stop.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               if (job != null) {
                  jobMonitor.kill(job);
                  job = null;
               }
            }
         });

         JButton Help = new JButton("Help");
         Help.setToolTipText(getToolTip("help"));
         Help.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               help.showInBrowser("http://sourceforge.net/p/kmttg/wiki/slingbox_capture/");
            }
         });

         JLabel dir_label = new JLabel("Slingbox capture file directory");
         dir = new JTextField(30);
         dir.setToolTipText(getToolTip("dir"));
         dir.setText(config.slingBox_dir);
         dir.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
               if( mouseEvent.getButton() == MouseEvent.BUTTON1 ) {
                  if (mouseEvent.getClickCount() == 2) {
                     if (dirBrowser.showOpenDialog(config.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
                        File result = dirBrowser.getSelectedFile();
                        if (result != null) {
                           dir.setText(result.getAbsolutePath());
                        }
                     }
                  }
               }
            }
         });

         JLabel perl_label = new JLabel("Perl executable");
         perl = new JTextField(30);
         perl.setToolTipText(getToolTip("perl"));
         perl.setText(config.slingBox_perl);
         perl.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
               if( mouseEvent.getButton() == MouseEvent.BUTTON1 ) {
                  if (mouseEvent.getClickCount() == 2) {
                     if (fileBrowser.showOpenDialog(config.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
                        File result = fileBrowser.getSelectedFile();
                        if (result != null) {
                           perl.setText(result.getPath());
                        }
                     }
                  }
               }
            }
         });

         JLabel filename_label = new JLabel("File name");
         filename = new JTextField(30);
         filename.setToolTipText(getToolTip("filename"));

         JLabel pass_label = new JLabel("Slingbox password");
         pass = new JTextField(30);
         pass.setToolTipText(getToolTip("pass"));
         pass.setText(config.slingBox_pass);

         JLabel ip_label = new JLabel("Slingbox IP");
         ip = new JTextField(30);
         ip.setToolTipText(getToolTip("ip"));
         ip.setText(config.slingBox_ip);

         JLabel port_label = new JLabel("Slingbox port");
         port = new JTextField(30);
         port.setToolTipText(getToolTip("ip"));
         port.setText(config.slingBox_port);

         JLabel dur_label = new JLabel("Capture # minutes");
         dur = new JTextField(30);
         dur.setToolTipText(getToolTip("dur"));
         dur.setText("0");

         JLabel chan_label = new JLabel("Tune to channel");
         chan = new JTextField(30);
         chan.setToolTipText(getToolTip("chan"));
         chan.setText("");

         JLabel res_label = new JLabel("Video resolution");
         res = new JComboBox<String>();
         res.setToolTipText(getToolTip("res"));
         res.addItem("1920x1080");
         res.addItem("640x480");
         res.setSelectedItem(config.slingBox_res);

         JLabel vbw_label = new JLabel("Video bit rate (Kbps)");
         vbw = new JComboBox<String>();
         vbw.setToolTipText(getToolTip("vbw"));
         vbw.addItem("4000");
         vbw.addItem("5000");
         vbw.addItem("6000");
         vbw.addItem("7000");
         vbw.setSelectedItem(config.slingBox_vbw);

         JLabel type_label = new JLabel("Slingbox model");
         type = new JComboBox<String>();
         type.setToolTipText(getToolTip("type"));
         type.addItem("Slingbox 350/500");
         type.addItem("Slingbox Pro HD");
         type.addItem("Slingbox Pro");
         type.addItem("Slingbox Solo");
         type.setSelectedItem(config.slingBox_type);

         JLabel container_label = new JLabel("Video container to use");
         container = new JComboBox<String>();
         container.setToolTipText(getToolTip("container"));
         container.addItem("mpegts");
         container.addItem("matroska");
         container.setSelectedItem(config.slingBox_container);

         raw = new JCheckBox("Capture raw file");
         raw.setToolTipText(getToolTip("raw"));
         raw.setSelected(false);

         int gy = 0;
         JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
         row.add(start);
         row.add(stop);
         row.add(Help);
         panel.add(row, "cell 1 " + gy);
         panel.add(raw, "cell 2 " + gy);

         gy++;
         panel.add(ip_label, "cell 0 " + gy);
         panel.add(ip, "cell 1 " + gy);
         panel.add(port_label, "cell 2 " + gy);
         panel.add(port, "cell 3 " + gy);

         gy++;
         panel.add(pass_label, "cell 0 " + gy);
         panel.add(pass, "cell 1 " + gy);
         panel.add(dir_label, "cell 2 " + gy);
         panel.add(dir, "cell 3 " + gy);

         gy++;
         panel.add(perl_label, "cell 0 " + gy);
         panel.add(perl, "cell 1 " + gy);
         panel.add(filename_label, "cell 2 " + gy);
         panel.add(filename, "cell 3 " + gy);

         gy++;
         panel.add(type_label, "cell 0 " + gy);
         panel.add(type, "cell 1 " + gy);
         panel.add(container_label, "cell 2 " + gy);
         panel.add(container, "cell 3 " + gy);

         gy++;
         panel.add(res_label, "cell 0 " + gy);
         panel.add(res, "cell 1 " + gy);
         panel.add(vbw_label, "cell 2 " + gy);
         panel.add(vbw, "cell 3 " + gy);

         gy++;
         panel.add(dur_label, "cell 0 " + gy);
         panel.add(dur, "cell 1 " + gy);
         panel.add(chan_label, "cell 2 " + gy);
         panel.add(chan, "cell 3 " + gy);
      }
      return panel;
   }
   
   public void updateConfig() {
      config.slingBox_dir = string.removeLeadingTrailingSpaces(dir.getText());
      config.slingBox_perl = string.removeLeadingTrailingSpaces(perl.getText());
      config.slingBox_pass = string.removeLeadingTrailingSpaces(pass.getText());
      config.slingBox_ip = string.removeLeadingTrailingSpaces(ip.getText());
      config.slingBox_port = string.removeLeadingTrailingSpaces(port.getText());
      config.slingBox_vbw = (String)vbw.getSelectedItem();
      config.slingBox_res = (String)res.getSelectedItem();
      config.slingBox_type = (String)type.getSelectedItem();
      config.slingBox_container = (String)container.getSelectedItem();
   }
   
   private String getTimeStamp() {
      long now = new Date().getTime();
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
      return sdf.format(now);
   }
   
   private String getFileName() {
      String name;
      String d = string.removeLeadingTrailingSpaces(dir.getText());
      String c = string.removeLeadingTrailingSpaces((String)container.getSelectedItem());
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
   
   private String getToolTip(String component) {
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
