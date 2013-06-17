package com.tivo.kmttg.gui;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class slingboxgui {
   private JPanel panel = null;
   private JTextField dir;
   private JTextField perl;
   private JTextField ip;
   private JTextField port;
   private JTextField pass;
   private JTextField dur;
   private JComboBox type;
   private JComboBox vbw;
   private JComboBox res;
   jobData job = null;
   JFileChooser Browser = null;
   
   slingboxgui(JFrame frame) {
      getPanel();
      Browser = new JFileChooser(config.programDir);
      Browser.setMultiSelectionEnabled(false);
   }
   
   public JPanel getPanel() {
      if (panel == null) {
         panel = new JPanel(new GridBagLayout());
         
         JButton start = new JButton("Start");
         start.setToolTipText(getToolTip("start"));
         start.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
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
                  String d = string.removeLeadingTrailingSpaces(dur.getText());
                  if (d.length() > 0 && ! d.equals("0")) {
                     try {
                        int i = Integer.parseInt(d);
                        job.slingbox_dur = "" + i*60;
                     } catch (NumberFormatException n) {
                        // Do nothing here
                     }
                  }
                  jobMonitor.submitNewJob(job);
               }
            }
         });
         
         JButton stop = new JButton("Stop");
         stop.setToolTipText(getToolTip("stop"));
         stop.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
               if (job != null) {
                  jobMonitor.kill(job);
                  job = null;
               }
            }
         });
         
         JButton Help = new JButton("Help");
         Help.setToolTipText(getToolTip("help"));
         Help.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
               help.showInBrowser("http://sourceforge.net/p/kmttg/wiki/slingbox_capture/");
            }
         });
         
         JLabel dir_label = new JLabel("Slingbox capture file directory");
         dir = new JTextField(30);
         dir.setToolTipText(getToolTip("dir"));
         dir.setText(config.slingBox_dir);
         dir.addMouseListener(
            new MouseAdapter() {
               public void mouseClicked(MouseEvent e) {
                  if(e.getClickCount() == 2) {
                     Browser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                     int result = Browser.showDialog(dir, "Choose Directory");
                     if (result == JFileChooser.APPROVE_OPTION) {
                        dir.setText(Browser.getSelectedFile().getPath());
                     }
                  }
               }
            }
         );
         
         JLabel perl_label = new JLabel("Perl executable");
         perl = new JTextField(30);
         perl.setToolTipText(getToolTip("perl"));
         perl.setText(config.slingBox_perl);
         perl.addMouseListener(
            new MouseAdapter() {
               public void mouseClicked(MouseEvent e) {
                  if(e.getClickCount() == 2) {
                     Browser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                     int result = Browser.showDialog(perl, "Choose File");
                     if (result == JFileChooser.APPROVE_OPTION) {
                        perl.setText(Browser.getSelectedFile().getPath());
                     }
                  }
               }
            }
         );
         
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
         
         JLabel res_label = new JLabel("Video resolution");
         res = new JComboBox();
         res.setToolTipText(getToolTip("res"));
         String[] r = {"1920x1080", "640x480"};
         for (int i=0; i<r.length; ++i)
            res.addItem(r[i]);
         res.setSelectedItem(config.slingBox_res);
         
         JLabel vbw_label = new JLabel("Video bit rate (Kbps)");
         vbw = new JComboBox();
         vbw.setToolTipText(getToolTip("vbw"));
         String[] vb = {"4000", "5000", "6000", "7000"};
         for (int i=0; i<vb.length; ++i)
            vbw.addItem(vb[i]);
         vbw.setSelectedItem(config.slingBox_vbw);
         
         JLabel type_label = new JLabel("Slingbox model");
         type = new JComboBox();
         type.setToolTipText(getToolTip("type"));
         type.addItem("Slingbox 350/500");
         type.addItem("Slingbox Pro HD");
         type.addItem("Slingbox Pro");
         type.setSelectedItem(config.slingBox_type);
         
         int gy = 0;
         GridBagConstraints c = new GridBagConstraints();
         c.ipady = 0;
         c.weighty = 0.0;  // default to no vertical stretch
         c.weightx = 0.0;  // default to no horizontal stretch
         c.gridx = 0;
         c.gridy = gy;
         c.gridwidth = 1;
         c.gridheight = 1;
         c.anchor = GridBagConstraints.CENTER;
         c.fill = GridBagConstraints.HORIZONTAL;
                  
         Dimension space = new Dimension(10,0);
         c.gridy = gy;
         JPanel row0 = new JPanel();
         row0.setLayout(new BoxLayout(row0, BoxLayout.LINE_AXIS));
         row0.add(start);
         row0.add(Box.createRigidArea(space));
         row0.add(stop);
         panel.add(row0, c);
         c.gridx = 1;
         panel.add(Help, c);
         
         gy++;
         c.gridy = gy;
         c.gridx = 0;
         panel.add(ip_label, c);
         c.gridx = 1;
         panel.add(ip, c);
         
         gy++;
         c.gridy = gy;
         c.gridx = 0;
         panel.add(port_label, c);
         c.gridx = 1;
         panel.add(port, c);
         
         gy++;
         c.gridy = gy;
         c.gridx = 0;
         panel.add(pass_label, c);
         c.gridx = 1;
         panel.add(pass, c);
         
         gy++;
         c.gridy = gy;
         c.gridx = 0;
         panel.add(dir_label, c);
         c.gridx = 1;
         panel.add(dir, c);
         
         gy++;
         c.gridy = gy;
         c.gridx = 0;
         panel.add(perl_label, c);
         c.gridx = 1;
         panel.add(perl, c);
         
         gy++;
         c.gridy = gy;
         c.gridx = 0;
         panel.add(type_label, c);
         c.gridx = 1;
         panel.add(type, c);
         
         gy++;
         c.gridy = gy;
         c.gridx = 0;
         JPanel left_video = new JPanel();
         left_video.setLayout(new BoxLayout(left_video, BoxLayout.LINE_AXIS));
         left_video.add(res_label);
         left_video.add(Box.createRigidArea(space));
         left_video.add(res);
         panel.add(left_video, c);
         c.gridx = 1;
         JPanel right_video = new JPanel();
         right_video.setLayout(new BoxLayout(right_video, BoxLayout.LINE_AXIS));
         right_video.add(Box.createRigidArea(space));
         right_video.add(vbw_label);
         right_video.add(Box.createRigidArea(space));
         right_video.add(vbw);
         panel.add(right_video, c);
         
         gy++;
         c.gridy = gy;
         c.gridx = 0;
         panel.add(dur_label, c);
         c.gridx = 1;
         panel.add(dur, c);
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
   }
   
   private String getTimeStamp() {
      long now = new Date().getTime();
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
      return sdf.format(now);
   }
   
   private String getFileName() {
      String d = string.removeLeadingTrailingSpaces(dir.getText());
      if (d.length() == 0) {
         log.error("No slingbox directory specified. Aborting...");
         return null;
      }
      return d + File.separator + "slingbox_" + getTimeStamp() + ".ts";
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
      else if (component.equals("res")) {
         text = "<b>Video resolution</b><br>";
         text += "Video resolution to use for the capture.<br>";
         text += "NOTE: This is only relevant for Slingbox 350/500 model";
      }
      else if (component.equals("vbw")) {
         text = "<b>Video bit rate (Kbps)</b><br>";
         text += "Video bit rate in Kbps to use for the capture.<br>";
         text += "NOTE: This is only relevant for Slingbox 350/500 model using 1920x1080 resolution";
      }
      else if (component.equals("type")) {
         text = "<b>Slingbox model</b><br>";
         text += "Choose which Slingbox model you have. kmttg uses a different Perl script and<br>";
         text += "options for older models vs newer models so it's important to choose the right one.";
      }
      else if (component.equals("dur")) {
         text = "<b>Capture # minutes</b><br>";
         text += "Capture a specified number of minutes. 0 or empty means unlimited.";
      }
      if (text.length() > 0) {
         text = "<html>" + text + "</html>";
      }
      return text;
   }
}
