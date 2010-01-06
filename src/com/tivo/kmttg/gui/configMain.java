package com.tivo.kmttg.gui;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Hashtable;
import java.util.Stack;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.mdns;
import com.tivo.kmttg.task.custom;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class configMain {
   private static Stack<JTextField> errors = new Stack<JTextField>();
   private static Color textbg_default = null;
   
   private static JButton add = null;
   private static JButton del = null;
   private static JButton OK = null;
   private static JButton CANCEL = null;
   private static JDialog dialog = null;
   private static JComboBox tivos = null;
   private static JCheckBox remove_tivo = null;
   private static JCheckBox remove_comcut = null;
   private static JCheckBox remove_comcut_mpeg = null;
   private static JCheckBox remove_mpeg = null;
   private static JCheckBox check_space = null;
   private static JCheckBox beacon = null;
   private static JCheckBox UseAdscan = null;
   private static JCheckBox VrdReview = null;
   private static JCheckBox VrdReview_noCuts = null;
   private static JCheckBox VrdQsFilter = null;
   private static JCheckBox VrdDecrypt = null;
   private static JCheckBox TSDownload = null;
   private static JCheckBox HideProtectedFiles = null;
   private static JCheckBox OverwriteFiles = null;
   private static JCheckBox toolTips = null;
   private static JCheckBox jobMonitorFullPaths = null;
   private static JTextField tivo_name = null;
   private static JTextField tivo_ip = null;
   private static JTextField files_path = null;
   private static JTextField MAK = null;
   private static JTextField FontSize = null;
   private static JTextField file_naming = null;
   private static JTextField tivo_output_dir = null;
   private static JTextField mpeg_output_dir = null;
   private static JTextField mpeg_cut_dir = null;
   private static JTextField encode_output_dir = null;
   private static JTextField tivodecode = null;
   private static JTextField curl = null;
   private static JTextField ffmpeg = null;
   private static JTextField mencoder = null;
   private static JTextField handbrake = null;
   private static JTextField comskip = null;
   private static JTextField comskip_ini = null;
   private static JTextField wan_http_port = null;
   private static JTextField active_job_limit = null;
   private static JTextField VRD_path = null;
   private static JTextField t2extract = null;
   private static JTextField t2extract_args = null;
   private static JTextField ccextractor = null;
   private static JTextField AtomicParsley = null;
   private static JTextField disk_space = null;
   private static JTextField customCommand = null;
   private static JTextField toolTipsTimeout = null;
   private static JTextField cpu_cores = null;
   private static JTextField pyTivo_host = null;
   private static JTextField pyTivo_config = null;
   private static JComboBox pyTivo_tivo = null;
   private static JComboBox pyTivo_files = null;
   private static JComboBox metadata_files = null;
   private static JComboBox keywords = null;
   private static JComboBox customFiles = null;
   private static JFileChooser Browser = null;
   private static JTabbedPane tabbed_panel = null;
      
   public static void display(JFrame frame) {
      debug.print("frame=" + frame);
      // Create dialog if not already created
      if (dialog == null) {
         create(frame);
         // Set component tooltips
         setToolTips();
      }
      
      // Update component settings to current configuration
      read();
      
      // Clear out any error highlights
      clearTextFieldErrors();
      
      // Display the dialog
      dialog.setVisible(true);
   }
   
   public static JDialog getDialog() {
      return dialog;
   }
   
   // Paint text field background to indicate an error setting
   private static void textFieldError(JTextField f, String message) {
      debug.print("f=" + f + " message=" + message);
      log.error(message);
      f.setBackground(config.lightRed);
      errors.add(f);
      // Set tab background of this text field to error color as well
      int tab_index = tabbed_panel.indexOfComponent(f.getParent());
      if (tab_index != -1)
         tabbed_panel.setBackgroundAt(tab_index, config.lightRed);
   }
   
   // Clear all text field and tab background color error paint settings
   private static void clearTextFieldErrors() {
      debug.print("");
      if (errors.size() > 0) {
         for (int i=0; i<errors.size(); i++) {
            errors.get(i).setBackground(textbg_default);
         }
         errors.clear();
      }
      // Clear tab background settings as well
      for (int i=0; i<tabbed_panel.getTabCount(); ++i)
         tabbed_panel.setBackgroundAt(i, textbg_default);
   }
   
   // Callback for OK button
   private static void okCB() {
      debug.print("");
      clearTextFieldErrors();
      int errors = write();
      if (errors >0) {
         // Popup confirm dialog (defaulting to No choice)
         int response = JOptionPane.showOptionDialog(
            config.gui.getJFrame(),
            "" + errors + " error(s). Proceed to save settings anyway?",
            "Confirm",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            new String[] {"Yes", "No"},
            "No"
         );
         if (response == JOptionPane.YES_OPTION) {
            config.save(config.configIni);
            dialog.setVisible(false);
         }
      } else {
         config.save(config.configIni);
         dialog.setVisible(false);
      }
      config.gui.refreshOptions();
   }
   
   // Callback for add button
   private static void addCB() {
      debug.print("");
      // Add name=ip to tivos combobox
      String name = string.removeLeadingTrailingSpaces(tivo_name.getText());
      String ip = string.removeLeadingTrailingSpaces(tivo_ip.getText());
      if ( name.length() == 0) {
         log.error("Enter a name in the 'Tivo Name' field");
         return;
      }
      if ( ip.length() == 0) {
         log.error("Enter an ip address in the 'Tivo IP#' field");
         return;
      }
      addTivo(name, ip);      
   }
   
   // Callback for keywords combobox
   private static void keywordsCB() {
      debug.print("");
      // Get currently selected item
      String keyword = (String)keywords.getSelectedItem();
      
      // Append selected entry to file_naming text field
      // (Replace current selection if any)
      int len = file_naming.getText().length();
      file_naming.setCaretPosition(len);
      file_naming.replaceSelection(keyword);
   }
   
   // Callback for customFiles combobox
   private static void customFilesCB() {
      debug.print("");
      // Get currently selected item
      String keyword = (String)customFiles.getSelectedItem();
      
      // Append selected entry to customCommand text field
      // (Replace current selection if any)
      int len = customCommand.getText().length();
      customCommand.setCaretPosition(len);
      customCommand.replaceSelection(keyword);
   }
   
   public static void addTivo(String name, String ip) {  
      debug.print("name=" + name + " ip=" + ip);
      if (dialog == null || tivos == null) return;
      String value = name + "=" + ip;
      // Don't add duplicate value
      Boolean doit = true;
      int count = tivos.getItemCount();
      if (count > 0) {
         for (int i=0; i<count; i++) {
            String s = tivos.getItemAt(i).toString();
            if (s.equals(value))
               doit = false;
         }
      }
      if (doit) {
         tivos.addItem(value);
         tivos.setSelectedItem(value);
      }
   }
   
   // Callback for del button
   private static void delCB() {
      debug.print("");
      // Remove current selection in tivos combobox
      int selected = tivos.getSelectedIndex();
      if (selected > -1) {
         tivos.removeItemAt(selected);
      } else {
         log.error("No tivo entries left to remove");
      }
   }
   
   // Update widgets with config settings
   public static void read() {
      debug.print("");
      // Tivos
      Stack<String> tivoNames = config.getTivoNames();
      if (tivoNames.size()>0) {
         tivos.removeAllItems();
         String name, ip;
         for (int i=0; i<tivoNames.size(); i++) {
            name = tivoNames.get(i);
            ip = config.TIVOS.get(name);
            tivos.addItem(name + "=" + ip);
         }
      }
      
      // Beacon
      if (config.CheckBeacon == 1)
         beacon.setSelected(true);
      else
         beacon.setSelected(false);
      
      // Remove .TiVo
      if (config.RemoveTivoFile == 1)
         remove_tivo.setSelected(true);
      else
         remove_tivo.setSelected(false);
      
      // Remove comcut files
      if (config.RemoveComcutFiles == 1)
         remove_comcut.setSelected(true);
      else
         remove_comcut.setSelected(false);
      
      // Remove mpeg file after comcut
      if (config.RemoveComcutFiles_mpeg == 1)
         remove_comcut_mpeg.setSelected(true);
      else
         remove_comcut_mpeg.setSelected(false);
      
      // Remove .mpg file
      if (config.RemoveMpegFile == 1)
         remove_mpeg.setSelected(true);
      else
         remove_mpeg.setSelected(false);
      
      // Check disk space
      if (config.CheckDiskSpace == 1)
         check_space.setSelected(true);
      else
         check_space.setSelected(false);
            
      // UseAdscan
      if (config.UseAdscan == 1)
         UseAdscan.setSelected(true);
      else
         UseAdscan.setSelected(false);
            
      // VrdReview
      if (config.VrdReview == 1)
         VrdReview.setSelected(true);
      else
         VrdReview.setSelected(false);
      
      // VrdReview_noCuts
      if (config.VrdReview_noCuts == 1)
         VrdReview_noCuts.setSelected(true);
      else
         VrdReview_noCuts.setSelected(false);
      
      // VrdQsFilter
      if (config.VrdQsFilter == 1)
         VrdQsFilter.setSelected(true);
      else
         VrdQsFilter.setSelected(false);
      
      // VrdDecrypt
      if (config.VrdDecrypt == 1)
         VrdDecrypt.setSelected(true);
      else
         VrdDecrypt.setSelected(false);
      
      // TSDownload
      if (config.TSDownload == 1)
         TSDownload.setSelected(true);
      else
         TSDownload.setSelected(false);
      
      // HideProtectedFiles
      if (config.HideProtectedFiles == 1)
         HideProtectedFiles.setSelected(true);
      else
         HideProtectedFiles.setSelected(false);
      
      // OverwriteFiles
      if (config.OverwriteFiles == 1)
         OverwriteFiles.setSelected(true);
      else
         OverwriteFiles.setSelected(false);
      
      // toolTips
      if (config.toolTips == 1)
         toolTips.setSelected(true);
      else
         toolTips.setSelected(false);
      
      // toolTips
      if (config.jobMonitorFullPaths == 1)
         jobMonitorFullPaths.setSelected(true);
      else
         jobMonitorFullPaths.setSelected(false);
      
      // Files naming
      file_naming.setText(config.tivoFileNameFormat);
      
      // FILES Default path
      files_path.setText(config.TIVOS.get("FILES"));
      
      // Min requested space
      disk_space.setText("" + config.LowSpaceSize);
      
      // MAK
      MAK.setText(config.MAK);
      
      // FontSize
      FontSize.setText("" + config.FontSize);
      
      // .TiVo output dir
      tivo_output_dir.setText(config.outputDir);
      
      // .mpg output dir
      mpeg_output_dir.setText(config.mpegDir);
      
      // .mpg cut dir
      mpeg_cut_dir.setText(config.mpegCutDir);
      
      // encode output dir
      encode_output_dir.setText(config.encodeDir);
            
      // mencoder
      mencoder.setText(config.mencoder);

      // handbrake
      handbrake.setText(config.handbrake);
      
      // comskip
      comskip.setText(config.comskip);
      
      // comskip_ini
      comskip_ini.setText(config.comskipIni);
      
      // VRD path
      VRD_path.setText(config.VRD);
      
      // tivodecode
      tivodecode.setText(config.tivodecode);
      
      // t2extract
      t2extract.setText(config.t2extract);
      
      // t2extract_args
      t2extract_args.setText(config.t2extract_args);
      
      // ccextractor
      ccextractor.setText(config.ccextractor);
      
      // curl
      curl.setText(config.curl);
      
      // AtomicParsley
      AtomicParsley.setText(config.AtomicParsley);
      
      // ffmpeg
      ffmpeg.setText(config.ffmpeg);
      
      // customCommand
      customCommand.setText(config.customCommand);
      
      // active job limit
      active_job_limit.setText("" + config.MaxJobs);
      
      // wan http port
      wan_http_port.setText(config.wan_http_port);
      
      // toolTipsTimeout
      toolTipsTimeout.setText("" + config.toolTipsTimeout);
      
      // cpu_cores
      cpu_cores.setText("" + config.cpu_cores);
      
      // pyTivo_host
      pyTivo_host.setText("" + config.pyTivo_host);
      
      // pyTivo_config
      pyTivo_config.setText("" + config.pyTivo_config);
      
      // pyTivo_tivo
      Stack<String> names = config.getTivoNames();
      if (names.size() > 0) {
         String setting = names.get(0);
         for (int i=0; i<names.size(); ++i) {
            if (names.get(i).equals(config.pyTivo_tivo)) {
               setting = config.pyTivo_tivo;
            }
         }
         pyTivo_tivo.setSelectedItem(setting);
      }
      
      // pyTivo_files
      pyTivo_files.setSelectedItem(config.pyTivo_files);
      
      // metadata_files
      metadata_files.setSelectedItem(config.metadata_files);
   }
   
   // Update config settings with widget values
   public static int write() {
      debug.print("");
      int errors = 0;
      String value;
      
      // Tivos
      int count = tivos.getItemCount();
      Hashtable<String,String> h = new Hashtable<String,String>();
      if (count > 0) {
         for (int i=0; i<count; i++) {
            String s = tivos.getItemAt(i).toString();
            String[] l = s.split("=");
            if (l.length == 2) {
               h.put(l[0], l[1]);
            }
         }
      }
      config.setTivoNames(h);
      
      // Beacon
      if (beacon.isSelected()) {
         config.CheckBeacon = 1;
         //if (config.tivo_beacon == null) config.tivo_beacon = new beacon();
         if (config.jmdns == null) config.jmdns = new mdns();
      } else {
         config.CheckBeacon = 0;
         if (config.jmdns != null) {
            config.jmdns.close();
            config.jmdns = null;
         }
         //config.tivo_beacon = null;
      }
                  
      // VRD path
      value = string.removeLeadingTrailingSpaces(VRD_path.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "";
      } else {
         if ( ! file.isDir(value) ) {
            textFieldError(VRD_path, "VideoRedo path setting not a valid dir: '" + value + "'");
            errors++;
         }
      }
      config.VRD = value;
      
      // Remove .TiVo
      if (remove_tivo.isSelected())
         config.RemoveTivoFile = 1;
      else
         config.RemoveTivoFile = 0;
            
      // Remove comcut files
      if (remove_comcut.isSelected())
         config.RemoveComcutFiles = 1;
      else
         config.RemoveComcutFiles = 0;
      
      // Remove mpeg file after comcut
      if (remove_comcut_mpeg.isSelected())
         config.RemoveComcutFiles_mpeg = 1;
      else
         config.RemoveComcutFiles_mpeg = 0;
      
      // Remove .mpg file
      if (remove_mpeg.isSelected())
         config.RemoveMpegFile = 1;
      else
         config.RemoveMpegFile = 0;
      
      // Check disk space
      if (check_space.isSelected())
         config.CheckDiskSpace = 1;
      else
         config.CheckDiskSpace = 0;
      
      // UseAdscan
      if (UseAdscan.isSelected() && file.isDir(config.VRD))
         config.UseAdscan = 1;
      else
         config.UseAdscan = 0;
      
      // VrdReview
      if (VrdReview.isSelected() && file.isDir(config.VRD))
         config.VrdReview = 1;
      else
         config.VrdReview = 0;
      
      // VrdReview_noCuts
      if (VrdReview_noCuts.isSelected() && file.isDir(config.VRD))
         config.VrdReview_noCuts = 1;
      else
         config.VrdReview_noCuts = 0;
      
      // VrdQsFilter
      if (VrdQsFilter.isSelected() && file.isDir(config.VRD))
         config.VrdQsFilter = 1;
      else
         config.VrdQsFilter = 0;
      
      // VrdDecrypt
      if (VrdDecrypt.isSelected() && file.isDir(config.VRD))
         config.VrdDecrypt = 1;
      else
         config.VrdDecrypt = 0;
      
      // TSDownload
      if (TSDownload.isSelected())
         config.TSDownload = 1;
      else
         config.TSDownload = 0;
      
      // HideProtectedFiles
      if (HideProtectedFiles.isSelected())
         config.HideProtectedFiles = 1;
      else
         config.HideProtectedFiles = 0;
      
      // OverwriteFiles
      if (OverwriteFiles.isSelected())
         config.OverwriteFiles = 1;
      else
         config.OverwriteFiles = 0;
      
      // toolTips
      if (toolTips.isSelected())
         config.toolTips = 1;
      else
         config.toolTips = 0;
      config.gui.enableToolTips(config.toolTips);
      
      // jobMonitorFullPaths
      if (jobMonitorFullPaths.isSelected())
         config.jobMonitorFullPaths = 1;
      else
         config.jobMonitorFullPaths = 0;
      
      // Files naming
      value = file_naming.getText();
      if (value.length() == 0) {
         // Reset to default if none given
         value = "[title] ([monthNum]_[mday]_[year])";
      }
      config.tivoFileNameFormat = value;
      
      // FILES Default path
      value = string.removeLeadingTrailingSpaces(files_path.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = config.programDir;
      } else {
         if (! file.isDir(value) ) {
            textFieldError(files_path, "FILES Default Path setting not a valid dir: '" + value + "'");
            errors++;
         }
      }
      config.TIVOS.put("FILES", value);
      
      // Min requested space
      value = string.removeLeadingTrailingSpaces(disk_space.getText());
      if (value.length() > 0) {
         try {
            config.LowSpaceSize = Integer.parseInt(value);
         } catch(NumberFormatException e) {
            textFieldError(disk_space, "Illegal setting for min required disk space (GB): '" + value + "'");
            log.error("Setting to 0 (no check)");
            config.LowSpaceSize = 0;
            disk_space.setText("" + config.LowSpaceSize);
            errors++;
         }
      } else {
         config.LowSpaceSize = 0;
      }
      
      // MAK
      value = string.removeLeadingTrailingSpaces(MAK.getText());
      if (value.length() > 0) {
         if (value.length() == 10) {
            try {
               Long.parseLong(value);
               config.MAK = value;
            } catch(NumberFormatException e) {
               textFieldError(MAK, "MAK should be a 10 digit number: '" + value + "'");
               errors++;
            }
         } else {
            textFieldError(MAK, "MAK should be a 10 digit number: '" + value + "'");
            errors++;
         }
      } else {
         textFieldError(MAK, "MAK not specified - should be a 10 digit number");
         errors++;
      }
      
      // FontSize
      value = string.removeLeadingTrailingSpaces(FontSize.getText());
      int size = 12;
      if (value.length() > 0) {
         try {
            size = Integer.parseInt(value);
         } catch(NumberFormatException e) {
            textFieldError(FontSize, "Illegal setting for FontSize: '" + value + "'");
            log.error("Setting to 12");
            size = 12;
            FontSize.setText("" + size);
            errors++;
         }
      }
      if (config.FontSize != size) {
         config.FontSize = size;
         config.gui.setFontSize(size);
      }
      
      // .TiVo output dir
      value = string.removeLeadingTrailingSpaces(tivo_output_dir.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = config.programDir;         
      } else {
         if ( ! file.isDir(value) ) {
            textFieldError(tivo_output_dir, ".TiVo Output Dir setting not a valid dir: '" + value + "'");
            errors++;
         }
      }
      config.outputDir = value;
      
      // .mpg output dir
      value = string.removeLeadingTrailingSpaces(mpeg_output_dir.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = config.programDir;
      } else {
         if ( ! file.isDir(value) ) {
            textFieldError(mpeg_output_dir, ".mpg Output Dir setting not a valid dir: '" + value + "'");
            errors++;
         }
      }
      config.mpegDir = value;
      
      // .mpg cut dir
      value = string.removeLeadingTrailingSpaces(mpeg_cut_dir.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = config.programDir;
      } else {
         if ( ! file.isDir(value) ) {
            textFieldError(mpeg_cut_dir, ".mpg Cut Dir setting not a valid dir: '" + value + "'");
            errors++;
         }
      }
      config.mpegCutDir = value;
      
      // encode output dir
      value = string.removeLeadingTrailingSpaces(encode_output_dir.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = config.programDir;
      } else {
         if ( ! file.isDir(value) ) {
            textFieldError(encode_output_dir, "Encode Output Dir setting not a valid dir: '" + value  + "'");
            errors++;
         }
      }
      config.encodeDir = value;
      
      // mencoder
      value = string.removeLeadingTrailingSpaces(mencoder.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "";
      } else {
         if ( ! file.isFile(value) ) {
            textFieldError(mencoder, "mencoder setting not a valid file: '" + value + "'");
            errors++;
         }
      }
      config.mencoder = value;
      
      // handbrake
      value = string.removeLeadingTrailingSpaces(handbrake.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "";
      } else {
         if ( ! file.isFile(value) ) {
            textFieldError(handbrake, "handbrake setting not a valid file: '" + value + "'");
            errors++;
         }
      }
      config.handbrake = value;
      
      // comskip
      value = string.removeLeadingTrailingSpaces(comskip.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "";
      } else {
         if ( ! file.isFile(value) ) {
            textFieldError(comskip, "comskip setting not a valid file: '" + value + "'");
            errors++;
         }
      }
      config.comskip = value;
      
      // comskip_ini
      value = string.removeLeadingTrailingSpaces(comskip_ini.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "";
      } else {
         if ( ! file.isFile(value) ) {
            textFieldError(comskip_ini, "comskip.ini setting not a valid file: '" + value + "'");
            errors++;
         }
      }
      config.comskipIni = value;
      
      // tivodecode
      value = string.removeLeadingTrailingSpaces(tivodecode.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "";
      } else {
         if ( ! file.isFile(value) ) {
            textFieldError(tivodecode, "tivodecode setting not a valid file: '" + value + "'");
            errors++;
         }
      }
      config.tivodecode = value;
      
      // t2extract
      value = string.removeLeadingTrailingSpaces(t2extract.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "";
      } else {
         if ( ! file.isFile(value) ) {
            textFieldError(t2extract, "t2extract setting not a valid file: '" + value  + "'");
            errors++;
         }
      }
      config.t2extract = value;
      
      // t2extract_args
      value = string.removeLeadingTrailingSpaces(t2extract_args.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "";
      }
      config.t2extract_args = value;
      
      // ccextractor
      value = string.removeLeadingTrailingSpaces(ccextractor.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "";
      } else {
         if ( ! file.isFile(value) ) {
            textFieldError(ccextractor, "ccextractor setting not a valid file: '" + value  + "'");
            errors++;
         }
      }
      config.ccextractor = value;
      
      // curl
      value = string.removeLeadingTrailingSpaces(curl.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "";
      } else {
         if ( ! file.isFile(value) ) {
            textFieldError(curl, "curl setting not a valid file: '" + value + "'");
            errors++;
         }
      }
      config.curl = value;
      
      // AtomicParsley
      value = string.removeLeadingTrailingSpaces(AtomicParsley.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "";
      } else {
         if ( ! file.isFile(value) ) {
            textFieldError(AtomicParsley, "AtomicParsley setting not a valid file: '" + value + "'");
            errors++;
         }
      }
      config.AtomicParsley = value;
      
      // ffmpeg
      value = string.removeLeadingTrailingSpaces(ffmpeg.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "";
      } else {
         if ( ! file.isFile(value) ) {
            textFieldError(ffmpeg, "ffmpeg setting not a valid file: '" + value + "'");
            errors++;
         }
      }
      config.ffmpeg = value;
      
      // customCommand
      value = string.removeLeadingTrailingSpaces(customCommand.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "";
      } else {
         if ( ! custom.customCommandExists(value) ) {
            textFieldError(customCommand, "custom command setting does not start with a valid file: '" + value + "'");
            errors++;
         }
      }
      config.customCommand = value;
      
      // active job limit
      value = string.removeLeadingTrailingSpaces(active_job_limit.getText());
      if (value.length() > 0) {
         try {
            config.MaxJobs = Integer.parseInt(value);
         } catch(NumberFormatException e) {
            textFieldError(active_job_limit, "Illegal setting for active job limit: '" + value + "'");
            log.error("Setting to 2");
            config.MaxJobs = 2;
            active_job_limit.setText("" + config.MaxJobs);
            errors++;
         }
      } else {
         config.MaxJobs = 2;
      }
      
      // wan http port
      value = string.removeLeadingTrailingSpaces(wan_http_port.getText());
      if (value.length() > 0) {
         try {
            Integer.parseInt(value);
            config.wan_http_port = value;
         } catch(NumberFormatException e) {
            textFieldError(wan_http_port, "wan http port should be a number: '" + value + "'");
            errors++;
         }
      } else {
         config.wan_http_port = "";
      }
      
      // cpu_cores
      value = string.removeLeadingTrailingSpaces(cpu_cores.getText());
      if (value.length() > 0) {
         try {
            config.cpu_cores = Integer.parseInt(value);
         } catch(NumberFormatException e) {
            textFieldError(cpu_cores, "Illegal setting for cpu cores: '" + value + "'");
            log.error("Setting to 1");
            config.cpu_cores = 1;
            cpu_cores.setText("" + config.cpu_cores);
            errors++;
         }
      } else {
         config.cpu_cores = 1;
      }
      
      // toolTipsTimeout
      value = string.removeLeadingTrailingSpaces(toolTipsTimeout.getText());
      if (value.length() > 0) {
         try {
            config.toolTipsTimeout = Integer.parseInt(value);
            config.gui.setToolTipsTimeout(config.toolTipsTimeout);
         } catch(NumberFormatException e) {
            textFieldError(toolTipsTimeout, "Illegal setting for toolTips timeout: '" + value + "'");
            log.error("Setting to 20");
            config.toolTipsTimeout = 20;
            toolTipsTimeout.setText("" + config.toolTipsTimeout);
            errors++;
         }
      } else {
         config.toolTipsTimeout = 20;
      }
      
      // pyTivo_host
      value = string.removeLeadingTrailingSpaces(pyTivo_host.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "localhost";
      }
      config.pyTivo_host = value;
      
      // pyTivo_config
      value = string.removeLeadingTrailingSpaces(pyTivo_config.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "";
      }
      config.pyTivo_config = value;
      
      // pyTivo_tivo
      config.pyTivo_tivo = (String)pyTivo_tivo.getSelectedItem();
      
      // pyTivo_files
      config.pyTivo_files = (String)pyTivo_files.getSelectedItem();
      
      // metadata_files
      config.metadata_files = (String)metadata_files.getSelectedItem();
      
      return errors;
   }

   private static void create(JFrame frame) {
      debug.print("frame=" + frame);
      encode_output_dir = new javax.swing.JTextField(30);
      textbg_default = encode_output_dir.getBackground();
      mpeg_cut_dir = new javax.swing.JTextField(30);
      mpeg_output_dir = new javax.swing.JTextField(30);
      mpeg_cut_dir = new javax.swing.JTextField(30);
      tivo_output_dir = new javax.swing.JTextField(30);
      file_naming = new javax.swing.JTextField(30);
      files_path = new javax.swing.JTextField(30);
      tivodecode = new javax.swing.JTextField(30);
      curl = new javax.swing.JTextField(30);
      ffmpeg = new javax.swing.JTextField(30);
      mencoder = new javax.swing.JTextField(30);
      handbrake = new javax.swing.JTextField(30);
      comskip = new javax.swing.JTextField(30);
      comskip_ini = new javax.swing.JTextField(30);
      VRD_path = new javax.swing.JTextField(30);
      t2extract = new javax.swing.JTextField(30);
      t2extract_args = new javax.swing.JTextField(30);
      ccextractor = new javax.swing.JTextField(30);
      AtomicParsley = new javax.swing.JTextField(30);
      customCommand = new javax.swing.JTextField(30);
      pyTivo_config = new javax.swing.JTextField(30);
      
      tivo_name = new javax.swing.JTextField(20);
      tivo_ip = new javax.swing.JTextField(20);
      pyTivo_host = new javax.swing.JTextField(20);
      
      MAK = new javax.swing.JTextField(15);
      wan_http_port = new javax.swing.JTextField(15);
      active_job_limit = new javax.swing.JTextField(15);
      toolTipsTimeout = new javax.swing.JTextField(15);
      cpu_cores = new javax.swing.JTextField(15);
      
      disk_space = new javax.swing.JTextField(5);
      FontSize = new javax.swing.JTextField(5);
      
      JLabel tivos_label = new javax.swing.JLabel();
      tivos = new javax.swing.JComboBox();
      add = new javax.swing.JButton();
      del = new javax.swing.JButton();
      JLabel tivo_name_label = new javax.swing.JLabel();
      JLabel tivo_ip_label = new javax.swing.JLabel();
      JLabel files_path_label = new javax.swing.JLabel();
      remove_tivo = new javax.swing.JCheckBox();
      remove_comcut = new javax.swing.JCheckBox();
      remove_comcut_mpeg = new javax.swing.JCheckBox();
      remove_mpeg = new javax.swing.JCheckBox();
      UseAdscan = new javax.swing.JCheckBox();
      VrdReview = new javax.swing.JCheckBox();
      VrdReview_noCuts = new javax.swing.JCheckBox();
      VrdQsFilter = new javax.swing.JCheckBox();
      VrdDecrypt = new javax.swing.JCheckBox();
      TSDownload = new javax.swing.JCheckBox();
      HideProtectedFiles = new javax.swing.JCheckBox();
      OverwriteFiles = new javax.swing.JCheckBox();
      JLabel MAK_label = new javax.swing.JLabel();
      JLabel FontSize_label = new javax.swing.JLabel();
      JLabel file_naming_label = new javax.swing.JLabel();
      JLabel tivo_output_dir_label = new javax.swing.JLabel();
      JLabel mpeg_output_dir_label = new javax.swing.JLabel();
      JLabel mpeg_cut_dir_label = new javax.swing.JLabel();
      JLabel encode_output_dir_label = new javax.swing.JLabel();
      JLabel tivodecode_label = new javax.swing.JLabel();
      JLabel curl_label = new javax.swing.JLabel();
      JLabel ffmpeg_label = new javax.swing.JLabel();
      JLabel mencoder_label = new javax.swing.JLabel();
      JLabel handbrake_label = new javax.swing.JLabel();
      JLabel comskip_label = new javax.swing.JLabel();
      JLabel comskip_ini_label = new javax.swing.JLabel();
      JLabel wan_http_port_label = new javax.swing.JLabel();
      JLabel active_job_limit_label = new javax.swing.JLabel();
      JLabel VRD_path_label = new javax.swing.JLabel();
      JLabel t2extract_label = new javax.swing.JLabel();
      JLabel t2extract_args_label = new javax.swing.JLabel();
      JLabel ccextractor_label = new javax.swing.JLabel();
      JLabel AtomicParsley_label = new javax.swing.JLabel();
      JLabel customCommand_label = new javax.swing.JLabel();
      JLabel customFiles_label = new javax.swing.JLabel();
      JLabel cpu_cores_label = new javax.swing.JLabel();
      JLabel available_keywords_label = new javax.swing.JLabel();
      JLabel pyTivo_host_label = new javax.swing.JLabel();
      JLabel pyTivo_config_label = new javax.swing.JLabel();
      JLabel pyTivo_tivo_label = new javax.swing.JLabel();
      JLabel pyTivo_files_label = new javax.swing.JLabel();
      JLabel metadata_files_label = new javax.swing.JLabel();
      pyTivo_tivo = new javax.swing.JComboBox();
      pyTivo_files = new javax.swing.JComboBox();
      metadata_files = new javax.swing.JComboBox();
      keywords = new javax.swing.JComboBox();
      customFiles = new javax.swing.JComboBox();
      check_space = new javax.swing.JCheckBox();
      JLabel disk_space_label = new javax.swing.JLabel();
      beacon = new javax.swing.JCheckBox();
      toolTips = new javax.swing.JCheckBox();
      jobMonitorFullPaths = new javax.swing.JCheckBox();
      JLabel toolTipsTimeout_label = new javax.swing.JLabel();
      OK = new javax.swing.JButton();
      CANCEL = new javax.swing.JButton();
      Browser = new JFileChooser(config.programDir);
      Browser.setMultiSelectionEnabled(false);

      tivos_label.setText("Tivos");
      
      add.setText("ADD"); 
      add.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            addCB();
         }
      });
      
      del.setText("DEL"); 
      del.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            delCB();
         }
      });
      
      tivo_name_label.setText("Tivo Name"); 
      tivo_ip_label.setText("Tivo IP#"); 
      files_path_label.setText("FILES Default Path"); 
      remove_tivo.setText("Remove .TiVo after file decrypt"); 
      remove_comcut.setText("Remove Ad Detect files after Ad Cut");
      remove_comcut_mpeg.setText("Remove .mpg file after Ad Cut");
      remove_mpeg.setText("Remove .mpg file after encode");
      UseAdscan.setText("Use VideoRedo AdScan instead of comskip");
      VrdReview.setText("Use VideoRedo GUI to review detected commercials");
      VrdReview_noCuts.setText("Bring up VideoRedo GUI to make manual cuts");
      VrdQsFilter.setText("Enable VideoRedo QS Fix video dimension filter");
      VrdDecrypt.setText("Decrypt using VideoRedo instead of tivodecode");
      TSDownload.setText("Download TiVo files in Transport Stream format");
      HideProtectedFiles.setText("Do not show copy protected files in table");
      OverwriteFiles.setText("Overwrite existing files");
      MAK_label.setText("MAK"); 
      FontSize_label.setText("GUI Font Size");
      file_naming_label.setText("File Naming"); 
      tivo_output_dir_label.setText(".TiVo Output Dir"); 
      mpeg_output_dir_label.setText(".mpg Output Dir"); 
      mpeg_cut_dir_label.setText(".mpg Cut Dir"); 
      encode_output_dir_label.setText("Encode Output Dir"); 
      tivodecode_label.setText("tivodecode"); 
      curl_label.setText("curl"); 
      ffmpeg_label.setText("ffmpeg"); 
      mencoder_label.setText("mencoder"); 
      handbrake_label.setText("handbrake"); 
      comskip_label.setText("comskip"); 
      comskip_ini_label.setText("comskip.ini"); 
      wan_http_port_label.setText("wan http port"); 
      active_job_limit_label.setText("active job limit"); 
      VRD_path_label.setText("VideoRedo path"); 
      t2extract_label.setText("t2extract"); 
      t2extract_args_label.setText("t2extract extra arguments");
      ccextractor_label.setText("ccextractor");
      AtomicParsley_label.setText("AtomicParsley");
      customCommand_label.setText("custom command");
      check_space.setText("Check Available Disk Space");      
      available_keywords_label.setText("Available keywords:"); 
      cpu_cores_label.setText("encoding cpu cores");
      pyTivo_host_label.setText("pyTivo host name");
      pyTivo_config_label.setText("pyTivo.conf file");
      pyTivo_tivo_label.setText("pyTivo push destination");
      pyTivo_files_label.setText("Files to push");
      metadata_files_label.setText("metadata files");

      keywords.setModel(new javax.swing.DefaultComboBoxModel(
         new String[] { "[title]", "[mainTitle]", "[episodeTitle]", "[channelNum]",
               "[channel]", "[min]", "[hour]", "[wday]", "[mday]", "[month]",
               "[monthNum]", "[year]", "[EpisodeNumber]", "[description]", "[/]" }));
      keywords.setName("keywords"); 
      keywords.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
             if (e.getStateChange() == ItemEvent.SELECTED) {
               keywordsCB();
            }
         }
      });
      
      pyTivo_tivo.setModel(new javax.swing.DefaultComboBoxModel(config.getTivoNames()));
      pyTivo_tivo.setName("pyTivo_tivo");
      
      pyTivo_files.setModel(new javax.swing.DefaultComboBoxModel(
         new String[] { "tivoFile", "mpegFile", "mpegFile_cut", "encodeFile", "last", "all" }
      ));
      pyTivo_files.setName("pyTivo_files");
      
      metadata_files.setModel(new javax.swing.DefaultComboBoxModel(
         new String[] { "tivoFile", "mpegFile", "mpegFile_cut", "encodeFile", "last", "all" }
      ));
      metadata_files.setName("metadata_files");

      customFiles_label.setText("Available file args:");
      customFiles.setModel(new javax.swing.DefaultComboBoxModel(
         new String[] { "[tivoFile]", "[metaFile]", "[mpegFile]", "[mpegFile_cut]",
               "[srtFile]", "[encodeFile]" }));
      customFiles.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
             if (e.getStateChange() == ItemEvent.SELECTED) {
               customFilesCB(); 
            }
         }
      });

      disk_space_label.setText("Min requested space (GB)"); 
      beacon.setText("Look for Tivos on network");
      
      toolTips.setText("Display toolTips");
      toolTipsTimeout_label.setText("toolTip timeout (secs)");
      
      jobMonitorFullPaths.setText("Show full paths in Job Monitor");
      
      OK.setText("OK");
      OK.setBackground(Color.green);
      OK.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            okCB();
         }
      });

      CANCEL.setText("CANCEL"); 
      CANCEL.setBackground(config.lightRed);
      CANCEL.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            dialog.setVisible(false);
         }
      });
      
      // File browser mouse double-click listeners
      files_path.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               if(e.getClickCount() == 2) {
                  Browser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                  int result = Browser.showDialog(files_path, "Choose Directory");
                  if (result == JFileChooser.APPROVE_OPTION) {
                     files_path.setText(Browser.getSelectedFile().getPath());
                  }
               }
            }
         }
      );
      
      tivo_output_dir.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               if(e.getClickCount() == 2) {
                  Browser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                  int result = Browser.showDialog(tivo_output_dir, "Choose Directory");
                  if (result == JFileChooser.APPROVE_OPTION) {
                     tivo_output_dir.setText(Browser.getSelectedFile().getPath());
                  }
               }
            }
         }
      );
      
      mpeg_output_dir.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               if(e.getClickCount() == 2) {
                  Browser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                  int result = Browser.showDialog(mpeg_output_dir, "Choose Directory");
                  if (result == JFileChooser.APPROVE_OPTION) {
                     mpeg_output_dir.setText(Browser.getSelectedFile().getPath());
                  }
               }
            }
         }
      );
      
      mpeg_cut_dir.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               if(e.getClickCount() == 2) {
                  Browser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                  int result = Browser.showDialog(mpeg_cut_dir, "Choose Directory");
                  if (result == JFileChooser.APPROVE_OPTION) {
                     mpeg_cut_dir.setText(Browser.getSelectedFile().getPath());
                  }
               }
            }
         }
      );
      
      encode_output_dir.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               if(e.getClickCount() == 2) {
                  Browser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                  int result = Browser.showDialog(encode_output_dir, "Choose Directory");
                  if (result == JFileChooser.APPROVE_OPTION) {
                     encode_output_dir.setText(Browser.getSelectedFile().getPath());
                  }
               }
            }
         }
      );
      
      VRD_path.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               if(e.getClickCount() == 2) {
                  Browser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                  int result = Browser.showDialog(VRD_path, "Choose Directory");
                  if (result == JFileChooser.APPROVE_OPTION) {
                     VRD_path.setText(Browser.getSelectedFile().getPath());
                  }
               }
            }
         }
      );
      
      tivodecode.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               if(e.getClickCount() == 2) {
                  Browser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                  int result = Browser.showDialog(tivodecode, "Choose File");
                  if (result == JFileChooser.APPROVE_OPTION) {
                     tivodecode.setText(Browser.getSelectedFile().getPath());
                  }
               }
            }
         }
      );
      
      curl.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               if(e.getClickCount() == 2) {
                  Browser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                  int result = Browser.showDialog(curl, "Choose File");
                  if (result == JFileChooser.APPROVE_OPTION) {
                     curl.setText(Browser.getSelectedFile().getPath());
                  }
               }
            }
         }
      );
      
      ffmpeg.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               if(e.getClickCount() == 2) {
                  Browser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                  int result = Browser.showDialog(ffmpeg, "Choose File");
                  if (result == JFileChooser.APPROVE_OPTION) {
                     ffmpeg.setText(Browser.getSelectedFile().getPath());
                  }
               }
            }
         }
      );
      
      customCommand.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               if(e.getClickCount() == 2) {
                  Browser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                  int result = Browser.showDialog(customCommand, "Choose File");
                  if (result == JFileChooser.APPROVE_OPTION) {
                     customCommand.setText(Browser.getSelectedFile().getPath());
                  }
               }
            }
         }
      );
      
      mencoder.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               if(e.getClickCount() == 2) {
                  Browser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                  int result = Browser.showDialog(mencoder, "Choose File");
                  if (result == JFileChooser.APPROVE_OPTION) {
                     mencoder.setText(Browser.getSelectedFile().getPath());
                  }
               }
            }
         }
      );
      
      handbrake.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               if(e.getClickCount() == 2) {
                  Browser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                  int result = Browser.showDialog(handbrake, "Choose File");
                  if (result == JFileChooser.APPROVE_OPTION) {
                     handbrake.setText(Browser.getSelectedFile().getPath());
                  }
               }
            }
         }
      );
      
      comskip.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               if(e.getClickCount() == 2) {
                  Browser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                  int result = Browser.showDialog(comskip, "Choose File");
                  if (result == JFileChooser.APPROVE_OPTION) {
                     comskip.setText(Browser.getSelectedFile().getPath());
                  }
               }
            }
         }
      );
      
      comskip_ini.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               if(e.getClickCount() == 2) {
                  Browser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                  int result = Browser.showDialog(comskip_ini, "Choose File");
                  if (result == JFileChooser.APPROVE_OPTION) {
                     comskip_ini.setText(Browser.getSelectedFile().getPath());
                  }
               }
            }
         }
      );
      
      t2extract.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               if(e.getClickCount() == 2) {
                  Browser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                  int result = Browser.showDialog(t2extract, "Choose File");
                  if (result == JFileChooser.APPROVE_OPTION) {
                     t2extract.setText(Browser.getSelectedFile().getPath());
                  }
               }
            }
         }
      );
      
      ccextractor.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               if(e.getClickCount() == 2) {
                  Browser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                  int result = Browser.showDialog(ccextractor, "Choose File");
                  if (result == JFileChooser.APPROVE_OPTION) {
                     ccextractor.setText(Browser.getSelectedFile().getPath());
                  }
               }
            }
         }
      );
      
      AtomicParsley.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               if(e.getClickCount() == 2) {
                  Browser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                  int result = Browser.showDialog(AtomicParsley, "Choose File");
                  if (result == JFileChooser.APPROVE_OPTION) {
                     AtomicParsley.setText(Browser.getSelectedFile().getPath());
                  }
               }
            }
         }
      );
      
      pyTivo_config.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               if(e.getClickCount() == 2) {
                  Browser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                  int result = Browser.showDialog(pyTivo_config, "Choose File");
                  if (result == JFileChooser.APPROVE_OPTION) {
                     String selected = Browser.getSelectedFile().getPath();
                     if (string.basename(selected).equals("pyTivo.conf")) {
                        pyTivo_config.setText(selected);
                     } else {
                        log.error("Invalid file chosen - must be pyTivo.conf: " + selected);
                     }
                  }
               }
            }
         }
      );

      // Start of layout management
      GridBagConstraints c = new GridBagConstraints();
      int gy = 0;
      c.insets = new Insets(0, 2, 0, 2);
      c.ipady = 0;
      c.weighty = 0.0;  // default to no vertical stretch
      c.weightx = 0.0;  // default to no horizontal stretch
      c.gridx = 0;
      c.gridy = gy;
      c.gridwidth = 1;
      c.gridheight = 1;
      c.anchor = GridBagConstraints.CENTER;
      c.fill = GridBagConstraints.HORIZONTAL;

      // Tivos Panel
      JPanel tivo_panel = new JPanel(new GridBagLayout());      
      // Look for Tivos on network
      gy = 0;
      c.gridx = 1;
      c.gridy = gy;
      tivo_panel.add(beacon, c);
      
      // Tivo combobox
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      tivo_panel.add(tivos_label, c);

      c.gridx = 1;
      c.gridy = gy;
      tivo_panel.add(tivos, c);

      // DEL button
      c.gridx = 4;
      c.gridy = gy;
      tivo_panel.add(del, c);
      
      // Tivo name
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      tivo_panel.add(tivo_name_label,c);

      c.gridx = 1;
      c.gridy = gy;
      tivo_panel.add(tivo_name, c);
      
      // ADD button
      c.gridx = 4;
      c.gridy = gy;
      tivo_panel.add(add, c);
      
      // Tivo ip
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      tivo_panel.add(tivo_ip_label, c);

      c.gridx = 1;
      c.gridy = gy;
      tivo_panel.add(tivo_ip, c);
      
      // Files panel
      JPanel files_panel = new JPanel(new GridBagLayout());      
      
      // Remove .TiVo after file decrypt
      gy=0;
      c.gridx = 0;
      c.gridy = gy;
      files_panel.add(remove_tivo, c);
      
      // Remove Ad Detect files after Ad Cut
      c.gridx = 1;
      c.gridy = gy;
      files_panel.add(remove_comcut, c);
      
      // Remove .mpg file after Ad Cut
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      files_panel.add(remove_comcut_mpeg, c);
      
      // Remove Ad Detect files after Ad Cut
      // Remove .mpg file after encode
      c.gridx = 1;
      c.gridy = gy;
      files_panel.add(remove_mpeg, c);
            
      // Check Available Disk Space
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      files_panel.add(check_space, c);
      
      // Min requested space      
      JPanel p = new JPanel();
      p.setLayout(new GridLayout(1, 2));
      p.add(disk_space_label);
      p.add(disk_space, c);
      c.gridx = 1;
      c.gridy = gy;
      files_panel.add(p, c);
      
      // File naming
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      files_panel.add(file_naming_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      files_panel.add(file_naming, c);
      
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      files_panel.add(available_keywords_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      files_panel.add(keywords, c);
      
      // FILES Default Path
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      files_panel.add(files_path_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      files_panel.add(files_path, c);
      
      // .TiVo Output Dir
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      files_panel.add(tivo_output_dir_label, c);

      c.gridx = 1;
      c.gridy = gy;
      files_panel.add(tivo_output_dir, c);
      
      // .mpg Output Dir
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      files_panel.add(mpeg_output_dir_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      files_panel.add(mpeg_output_dir, c);
      
      // .mpg Cut Dir
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      files_panel.add(mpeg_cut_dir_label, c);

      c.gridx = 1;
      c.gridy = gy;
      files_panel.add(mpeg_cut_dir, c);
      
      // Encode output dir
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      files_panel.add(encode_output_dir_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      files_panel.add(encode_output_dir, c);
      
      // OverwriteFiles
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      files_panel.add(OverwriteFiles, c);

      // Programs Panel
      JPanel programs_panel = new JPanel(new GridBagLayout());      
      
      // curl
      gy=0;
      c.gridx = 0;
      c.gridy = gy;
      programs_panel.add(curl_label, c);

      c.gridx = 1;
      c.gridy = gy;
      programs_panel.add(curl, c);
      
      // tivodecode
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      programs_panel.add(tivodecode_label, c);

      c.gridx = 1;
      c.gridy = gy;
      programs_panel.add(tivodecode, c);
      
      // mencoder
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      programs_panel.add(mencoder_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      programs_panel.add(mencoder, c);
      
      // ffmpeg
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      programs_panel.add(ffmpeg_label, c);

      c.gridx = 1;
      c.gridy = gy;
      programs_panel.add(ffmpeg, c);
      
      // handbrake
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      programs_panel.add(handbrake_label, c);

      c.gridx = 1;
      c.gridy = gy;
      programs_panel.add(handbrake, c);
      
      // comskip
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      programs_panel.add(comskip_label, c);

      c.gridx = 1;
      c.gridy = gy;
      programs_panel.add(comskip, c);
      
      // comskip.ini
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      programs_panel.add(comskip_ini_label, c);

      c.gridx = 1;
      c.gridy = gy;
      programs_panel.add(comskip_ini, c);
      
      // t2extract
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      programs_panel.add(t2extract_label, c);

      c.gridx = 1;
      c.gridy = gy;
      programs_panel.add(t2extract, c);
      
      // ccextractor (intentionally disabled for now)
      /*
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      programs_panel.add(ccextractor_label, c);

      c.gridx = 1;
      c.gridy = gy;
      programs_panel.add(ccextractor, c);
      */
      
      // AtomicParsley
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      programs_panel.add(AtomicParsley_label, c);

      c.gridx = 1;
      c.gridy = gy;
      programs_panel.add(AtomicParsley, c);
      
      // custom command
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      programs_panel.add(customCommand_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      programs_panel.add(customCommand, c);
      
      // customFiles
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      programs_panel.add(customFiles_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      programs_panel.add(customFiles, c);

      // Program_options Panel
      JPanel program_options_panel = new JPanel(new GridBagLayout());      
      
      // TSDownload
      gy=0;
      c.gridx = 1;
      c.gridy = gy;
      program_options_panel.add(TSDownload, c);
      
      // t2extract_args
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      program_options_panel.add(t2extract_args_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      program_options_panel.add(t2extract_args, c);
      
      // metadata_files
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      program_options_panel.add(metadata_files_label, c);

      c.gridx = 1;
      c.gridy = gy;
      program_options_panel.add(metadata_files, c);
      
      // General panel
      JPanel general = new JPanel(new GridBagLayout());
      
      // FontSize
      gy=0;
      c.gridx = 0;
      c.gridy = gy;
      general.add(FontSize_label, c);

      c.gridx = 1;
      c.gridy = gy;
      general.add(FontSize, c);
      
      // MAK
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      general.add(MAK_label, c);

      c.gridx = 1;
      c.gridy = gy;
      general.add(MAK, c);
            
      // active job limit
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      general.add(active_job_limit_label, c);

      c.gridx = 1;
      c.gridy = gy;
      general.add(active_job_limit, c);
      
      // cpu_cores
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      general.add(cpu_cores_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      general.add(cpu_cores, c);
      
      // wan http port
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      general.add(wan_http_port_label, c);

      c.gridx = 1;
      c.gridy = gy;
      general.add(wan_http_port, c);

      // toolTipsTimeout
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      general.add(toolTipsTimeout_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      general.add(toolTipsTimeout, c);
      
      // toolTips
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      general.add(toolTips, c);
      
      // jobMonitorFullPaths
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      general.add(jobMonitorFullPaths, c);

      // HideProtectedFiles
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      general.add(HideProtectedFiles, c);
      
      // VRD Panel
      JPanel vrd_panel = new JPanel(new GridBagLayout());       
      
      // VRD path
      gy=0;
      c.gridx = 0;
      c.gridy = gy;
      vrd_panel.add(VRD_path_label, c);

      c.gridx = 1;
      c.gridy = gy;
      vrd_panel.add(VRD_path, c);

      // UseAdscan
      gy++;
      c.gridx = 1;
      c.gridy = gy;
      vrd_panel.add(UseAdscan, c);      
      
      // VrdReview
      gy++;
      c.gridx = 1;
      c.gridy = gy;
      vrd_panel.add(VrdReview, c);
      
      // VrdReview_noCuts
      gy++;
      c.gridx = 1;
      c.gridy = gy;
      vrd_panel.add(VrdReview_noCuts, c);
      
      // VrdQsFilter
      gy++;
      c.gridx = 1;
      c.gridy = gy;
      vrd_panel.add(VrdQsFilter, c);
      
      // VrdDecrypt
      gy++;
      c.gridx = 1;
      c.gridy = gy;
      vrd_panel.add(VrdDecrypt, c);
      
      // pyTivo Panel
      JPanel pyTivo_panel = new JPanel(new GridBagLayout());      
      
      // pyTivo_config
      gy=0;
      c.gridx = 0;
      c.gridy = gy;
      pyTivo_panel.add(pyTivo_config_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      pyTivo_panel.add(pyTivo_config, c);
      
      // pyTivo_host
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      pyTivo_panel.add(pyTivo_host_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      pyTivo_panel.add(pyTivo_host, c);
      
      // pyTivo_tivo
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      pyTivo_panel.add(pyTivo_tivo_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      pyTivo_panel.add(pyTivo_tivo, c);
      
      // pyTivo_files
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      pyTivo_panel.add(pyTivo_files_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      pyTivo_panel.add(pyTivo_files, c);
      
      // Common panel
      c.ipady = 0;
      c.weighty = 0.0;  // default to no vertical stretch
      c.weightx = 1.0;  // default to horizontal stretch
      c.gridx = 0;
      c.gridy = gy;
      c.gridwidth = 1;
      c.gridheight = 1;
      c.anchor = GridBagConstraints.WEST;
      c.fill = GridBagConstraints.HORIZONTAL;

      // Common panel
      JPanel common_panel = new JPanel(new GridBagLayout());
      // OK and CANCEL buttons
      gy = 0;
      c.gridx = 0;
      c.gridy = gy;
      common_panel.add(OK, c);
      
      c.gridx = 1;
      c.gridy = gy;
      common_panel.add(CANCEL, c);
      
      // Tabbed panel
      tabbed_panel = new JTabbedPane();
      tabbed_panel.add("File Settings", files_panel);
      tabbed_panel.add("Programs", programs_panel);
      tabbed_panel.add("Program Options", program_options_panel);
      tabbed_panel.add("Tivos", tivo_panel);
      tabbed_panel.add("General", general);
      if (config.OS.equals("windows"))
         tabbed_panel.add("VideoRedo", vrd_panel);
      tabbed_panel.add("pyTivo", pyTivo_panel);
      
      // Main panel
      JPanel main_panel = new JPanel(new GridBagLayout());
      gy = 0;
      c.gridx = 0;
      c.gridy = gy;
      main_panel.add(tabbed_panel, c);
      
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      main_panel.add(common_panel, c);      
      
      // create dialog window
      dialog = new JDialog(frame, false); // non-modal dialog
      dialog.setTitle("kmttg configuration");
      dialog.setContentPane(main_panel);
      dialog.setLocationRelativeTo(config.gui.getJFrame().getJMenuBar().getComponent(0));
      dialog.pack();
      dialog.setResizable(false);
  }
   
   public static void setToolTips() {
      debug.print("");
      tivo_name.setToolTipText(getToolTip("tivo_name"));
      tivo_ip.setToolTipText(getToolTip("tivo_ip"));
      add.setToolTipText(getToolTip("add")); 
      del.setToolTipText(getToolTip("del")); 
      remove_tivo.setToolTipText(getToolTip("remove_tivo"));
      remove_comcut.setToolTipText(getToolTip("remove_comcut"));
      remove_comcut_mpeg.setToolTipText(getToolTip("remove_comcut_mpeg"));
      remove_mpeg.setToolTipText(getToolTip("remove_mpeg"));
      check_space.setToolTipText(getToolTip("check_space"));
      beacon.setToolTipText(getToolTip("beacon"));
      UseAdscan.setToolTipText(getToolTip("UseAdscan"));
      VrdReview.setToolTipText(getToolTip("VrdReview"));
      VrdReview_noCuts.setToolTipText(getToolTip("VrdReview_noCuts"));
      VrdQsFilter.setToolTipText(getToolTip("VrdQsFilter"));
      VrdDecrypt.setToolTipText(getToolTip("VrdDecrypt"));
      TSDownload.setToolTipText(getToolTip("TSDownload"));
      HideProtectedFiles.setToolTipText(getToolTip("HideProtectedFiles"));
      OverwriteFiles.setToolTipText(getToolTip("OverwriteFiles"));
      files_path.setToolTipText(getToolTip("files_path"));
      MAK.setToolTipText(getToolTip("MAK"));
      FontSize.setToolTipText(getToolTip("FontSize"));
      file_naming.setToolTipText(getToolTip("file_naming"));
      tivo_output_dir.setToolTipText(getToolTip("tivo_output_dir"));
      mpeg_output_dir.setToolTipText(getToolTip("mpeg_output_dir"));
      mpeg_cut_dir.setToolTipText(getToolTip("mpeg_cut_dir"));
      encode_output_dir.setToolTipText(getToolTip("encode_output_dir"));
      tivodecode.setToolTipText(getToolTip("tivodecode"));
      curl.setToolTipText(getToolTip("curl"));
      ffmpeg.setToolTipText(getToolTip("ffmpeg"));
      mencoder.setToolTipText(getToolTip("mencoder"));
      handbrake.setToolTipText(getToolTip("handbrake"));
      comskip.setToolTipText(getToolTip("comskip"));
      comskip_ini.setToolTipText(getToolTip("comskip_ini"));
      t2extract.setToolTipText(getToolTip("t2extract"));
      t2extract_args.setToolTipText(getToolTip("t2extract_args"));
      ccextractor.setToolTipText(getToolTip("ccextractor"));
      VRD_path.setToolTipText(getToolTip("VRD_path"));
      AtomicParsley.setToolTipText(getToolTip("AtomicParsley"));
      wan_http_port.setToolTipText(getToolTip("wan_http_port"));
      active_job_limit.setToolTipText(getToolTip("active_job_limit"));
      disk_space.setToolTipText(getToolTip("disk_space"));
      customCommand.setToolTipText(getToolTip("customCommand"));
      keywords.setToolTipText(getToolTip("keywords"));
      customFiles.setToolTipText(getToolTip("customFiles")); 
      OK.setToolTipText(getToolTip("OK")); 
      CANCEL.setToolTipText(getToolTip("CANCEL")); 
      toolTips.setToolTipText(getToolTip("toolTips"));
      jobMonitorFullPaths.setToolTipText(getToolTip("jobMonitorFullPaths"));
      toolTipsTimeout.setToolTipText(getToolTip("toolTipsTimeout")); 
      cpu_cores.setToolTipText(getToolTip("cpu_cores"));
      pyTivo_host.setToolTipText(getToolTip("pyTivo_host"));
      pyTivo_config.setToolTipText(getToolTip("pyTivo_config"));
      pyTivo_tivo.setToolTipText(getToolTip("pyTivo_tivo"));
      pyTivo_files.setToolTipText(getToolTip("pyTivo_files"));
      metadata_files.setToolTipText(getToolTip("metadata_files"));
   }
   
   public static String getToolTip(String component) {
      debug.print("component=" + component);
      String text = "";
      if (component.equals("tivo_name")) {
         text =  "<b>Tivo Name</b><br>";
         text += "Enter the name of a <b>TiVo</b> on your network.<br>";
         text += "kmttg tries to detect TiVos on your network automatically but that doesn't always work.<br>";
         text += "Enter corresponding <b>Tivo IP#</b> below and then click on <b>ADD</b> button.";
      }
      else if (component.equals("tivo_ip")) {
         text =  "<b>Tivo IP#</b><br>";
         text += "Enter the corresponding IP number of a TiVo on your home network.<br>";
         text += "You can find the IP number of your TiVo from the TiVo as follows:<br>";
         text += "<b>Tivo Central-Messages&Settings-Settings-Phone&Network: IP addr</b><br>";
         text += "Enter corresponding <b>Tivo Name</b> above and then click on <b>ADD</b> button.";
      }
      else if (component.equals("add")) {
         text =  "<b>ADD</b><br>";
         text += "Add specified <b>Tivo Name</b> and associated <b>Tivo IP#</b> to <b>Tivos</b> list.<br>";
         text += "kmttg tries to detect TiVos on your network automatically but that doesn't always work.";
      }
      else if (component.equals("del")) {
         text =  "<b>DEL</b><br>";
         text += "Remove currently selected entry in <b>Tivos</b> list.";
      }
      else if (component.equals("remove_tivo")) {
         text =  "<b>Remove .TiVo after file decrypt</b><br>";
         text += "Enable this option if you would like kmttg to remove .TiVo files automatically<br>";
         text += "once they have been successfully decrypted to .mpg format.";
      }
      else if (component.equals("remove_comcut")) {
         text =  "<b>Remove Ad Detect files after Ad Cut</b><br>";
         text += "If you use comcut you can enable this option if you would like kmttg to remove files<br>";
         text += "associated with Ad Detect task automatically once Ad Cut job completes successfully.";
      }
      else if (component.equals("remove_comcut_mpeg")) {
         text =  "<b>Remove .mpg file after Ad Cut</b><br>";
         text += "If this option is enabled kmttg will remove the .mpg file (not the _cut.mpg file)<br>";
         text += "automatically once Ad Cut job completes successfully.";
      }
      else if (component.equals("remove_mpeg")) {
         text =  "<b>Remove .mpg file after encode</b><br>";
         text += "If you use encode you can enable this option if you would like kmttg to remove .mpg<br>";
         text += "files automatically once they have been successfully re-encoded.";
      }
      else if (component.equals("check_space")) {
         text =  "<b>Check Available Disk Space</b><br>";
         text += "If this option is enabled then kmttg will check that destination drive has more than<br>";
         text += "the space available defined in <b>Min requested space (GB)</b> field before running jobs.";
      }
      else if (component.equals("beacon")) {
         text =  "<b>Look for Tivos on network</b><br>";
         text += "If this option is enabled then kmttg will try to detect Tivos on your network<br>";
         text += "automatically that you have not already configured manually.<br>";
         text += "NOTE: The automatic detection is disabled automatically after about 10 minutes.";
      }
      else if (component.equals("UseAdscan")) {
         text =  "<b>Use VideoRedo AdScan instead of comskip</b><br>";
         text += "If you have VideoRedo and have configured kmttg with the installation path<br>";
         text += "to VideoRedo, when this option is enabled kmttg will use VideoRedo instead<br>";
         text += "of <b>comskip</b> for commercials detection.";
      }
      else if (component.equals("VrdReview")) {
         text =  "<b>Use VideoRedo GUI to review detected commercials</b><br>";
         text += "If you have VideoRedo and have configured kmttg with the installation path<br>";
         text += "to VideoRedo, when this option is enabled kmttg will start VideoRedo GUI<br>";
         text += "to allow you to manually review and update the detected commercial segments<br>";
         text += "before starting the commercial cutting job. kmttg will wait until you close<br>";
         text += "the VideRedo GUI before proceeding. NOTE: Be sure to save your changes to .VPrj file<br>";
         text += "before you exit VideoRedo or they will not be used in commercial cut step.<br>";
         text += "NOTE: If you have <b>Bring up VideoRedo GUI to make manual cuts</b> option set<br>";
         text += "you will need to save the output file from VideoRedo GUI, otherwise without that<br>";
         text += "option set the cuts will be make by kmttg <b>Ad Cut</b> task.";
      }
      else if (component.equals("VrdReview_noCuts")) {
         text =  "<b>Bring up VideoRedo GUI to make manual cuts</b><br>";
         text += "If you have VideoRedo and have configured kmttg with the installation path<br>";
         text += "to VideoRedo, when this option is enabled kmttg will start VideoRedo GUI<br>";
         text += "to allow you create the commercial edited mpeg file manually. kmttg will wait<br>";
         text += "until you close the VideoRedo GUI before proceeding to next task.<br>";
         text += "<b>NOTE: Be sure to specify the output file using the VRD default ' (02).mpg'<br>";
         text += "suffix or using the kmttg conventional '_cut.mpg' suffix when saving output file</b>.<br>";
         text += "NOTE: When using this option you normally want to disable <b>Ad Detect</b> task<br>";
         text += "and enable <b>Ad Cut</b> task.";
      }
      else if (component.equals("VrdQsFilter")) {
         text =  "<b>Enable VideoRedo QS Fix video dimension filter</b><br>";
         text += "If you have trouble in VideoRedo editing some files due to <b>Video Dimensions Changed</b><br>";
         text += "error message then enabling this option will apply a Video Dimensions filter as part of kmttg VRD<br>";
         text += "Quickstream Fix run that will solve that problem. Note that kmttg uses ffmpeg<br>";
         text += "to automatically detect the mpeg video file dimensions to be used as the filter and<br>";
         text += "prepares a custom version of VRD vp.vbs file with an added filter line.";
      }
      else if (component.equals("VrdDecrypt")) {
         text =  "<b>Decrypt using VideoRedo instead of tivodecode</b><br>";
         text += "If you have VideoRedo and have configured kmttg with the installation path<br>";
         text += "to VideoRedo, when this option is enabled kmttg will use VideoRedo QSFix task<br>";
         text += "to decrypt .TiVo files instead of the standard <b>tivodecode</b> program. This is<br>";
         text += "useful for cases when the .TiVo files are in a format that tivodecode cannot decrypt<br>";
         text += "such as for Transport Stream format .TiVo files used in AU/NZ TiVos.<br>";
         text += "NOTE: You must have TiVo Desktop (or at least TiVoDirectShowFilter.dll) installed for this to work.";
      }
      else if (component.equals("TSDownload")) {
         text =  "<b>Download TiVo files in Transport Stream format</b><br>";
         text += "For TiVo software that properly supports it, this forces TiVo file downloads to use<br>";
         text += "Transport Stream format instead of the default Program Stream format by adding<br>";
         text += "<b>&Format=video/x-tivo-mpeg-ts</b> tag to the download URL.<br>";
         text += "NOTE: Currently only Australia/New Zealand TiVos support this format and this will<br>";
         text += "have no effect on other TiVos";
      }
      else if (component.equals("HideProtectedFiles")) {
         text = "<b>Do not show copy protected files in table</b><br>";
         text += "If this option is enabled then copy protected TiVo shows are not displayed in the<br>";
         text += "TiVo Now Playing lists.";
      }
      else if (component.equals("OverwriteFiles")) {
         text =  "<b>Overwrite existing files</b><br>";
         text += "With this option disabled kmttg will skip tasks for which output files already exist<br>";
         text += "so as not to overwrite any existing files of same name on your computer.<br>";
         text += "With this option enabled kmttg will run tasks regardless of whether their output files<br>";
         text += "exist or not, overwriting existing files as needed.";
      }
      else if (component.equals("files_path")) {
         text =  "<b>FILES Default Path</b><br>";
         text += "Defines where you would like the file browser to start from in <b>FILES</b> mode.<br>";
         text += "<b>NOTE: Double-click mouse in this field to bring up File Browser</b>.";
      }
      else if (component.equals("MAK")) {
         text =  "<b>MAK</b><br>";
         text += "<b>REQUIRED</b> setting if you plan to download and/or decrypt files from your TiVos.<br>";
         text += "This is your TiVo <b>Media Access Key</b> 10 digit number.<br>";
         text += "You can find the number on any of your networked Tivos as follows:<br>";
         text += "<b>Tivo Central-Messages&Settings-Account&System Information-Media Access Key</b>";
      }
      else if (component.equals("FontSize")) {
         text =  "<b>GUI Font Size</b><br>";
         text += "Sets the text font size to use for all text GUI components.";
      }
      else if (component.equals("file_naming")) {
         text =  "<b>File Naming</b><br>";
         text += "This defines the file naming template for kmttg to use when downloading files<br>";
         text += "from your TiVos. The <b>Available Keywords</b> entries to the right contain<br>";
         text += "all the valid recognized keywords. Consult the kmttg documentation for all the<br>";
         text += "details on the meaning of each keyword and for advanced file naming setup.<br>";
         text += "<b>NOTE: Several special characters are stripped or mapped from file names to avoid<br>";
         text += "potential problems with the various helper tools being used.<br>";
         text += "Consult the kmttg documentation on this option for details on that</b>";
      }
      else if (component.equals("tivo_output_dir")) {
         text =  "<b>.TiVo Output Dir</b><br>";
         text += "<b>REQUIRED</b> if you plan to download files from your TiVos.<br>";
         text += "This defines location where TiVo files are download to.<br>";
         text += "NOTE: Make sure to have plenty of disk space available at this location.<br>";
         text += "<b>NOTE: Double-click mouse in this field to bring up File Browser</b>.";
      }
      else if (component.equals("mpeg_output_dir")) {
         text =  "<b>.mpg Output Dir</b><br>";
         text += "<b>REQUIRED</b> if you plan to decrypt TiVo files to mpeg files.<br>";
         text += "This defines location where decrypted mpeg files will be saved to.<br>";
         text += "NOTE: Make sure to have plenty of disk space available at this location.<br>";
         text += "<b>NOTE: Double-click mouse in this field to bring up File Browser</b>.";
      }
      else if (component.equals("mpeg_cut_dir")) {
         text =  "<b>.mpg Cut Dir</b><br>";
         text += "<b>REQUIRED</b> if you plan to use <b>comcut</b> step.<br>";
         text += "This defines location where comcut commercial stripped mpeg file will be saved to.<br>";
         text += "NOTE: Make sure to have plenty of disk space available at this location.<br>";
         text += "<b>NOTE: Double-click mouse in this field to bring up File Browser</b>.";
      }
      else if (component.equals("encode_output_dir")) {
         text =  "<b>Encode Output Dir</b><br>";
         text += "<b>REQUIRED</b> if you plan to re-encode mpeg files to other formats.<br>";
         text += "This defines location where encoded files will be saved to.<br>";
         text += "NOTE: Make sure to have plenty of disk space available at this location.<br>";
         text += "<b>NOTE: Double-click mouse in this field to bring up File Browser</b>.";
      }
      else if (component.equals("tivodecode")) {
         text =  "<b>tivodecode</b><br>";
         text += "<b>REQUIRED</b> if you plan to decrypt TiVo files to unecrypted mpeg2 format.<br>";
         text += "This defines the full path to the <b>tivodecode</b> program.<br>";
         text += "<b>NOTE: Double-click mouse in this field to bring up File Browser</b>.";
      }
      else if (component.equals("curl")) {
         text =  "<b>curl</b><br>";
         text += "<b>REQUIRED</b> if you plan to download files from your TiVos.<br>";
         text += "This defines the full path to the <b>curl</b> program.<br>";
         text += "<b>NOTE: Double-click mouse in this field to bring up File Browser</b>.";
      }
      else if (component.equals("ffmpeg")) {
         text =  "<b>ffmpeg</b><br>";
         text += "This defines the full path to the <b>ffmpeg</b> program.<br>";
         text += "All the encoding profile names starting with <b>ff_</b> prefix<br>";
         text += "use this program, so if you plan on encoding to different video<br>";
         text += "file formats with one of those profiles this setting is required.<br>";
         text += "<b>NOTE: Double-click mouse in this field to bring up File Browser</b>.";
      }
      else if (component.equals("mencoder")) {
         text =  "<b>mencoder</b><br>";
         text += "This defines the full path to the <b>mencoder</b> program.<br>";
         text += "Unless you configure kmttg to use VideoRedo, this program is used<br>";
         text += "during <b>comcut</b> step to remove commercials from an mpeg2 file.<br>";
         text += "NOTE: This program can also be used in a custom defined encoding<br>";
         text += "profile if you wish as the encoding program.<br>";
         text += "<b>NOTE: Double-click mouse in this field to bring up File Browser</b>.";
      }
      else if (component.equals("handbrake")) {
         text =  "<b>handbrake</b><br>";
         text += "This defines the full path to the <b>handbrake</b> program.<br>";
         text += "All the encoding profile names starting with <b>hb_</b> prefix<br>";
         text += "use this program, so if you plan on encoding to different video<br>";
         text += "file formats with one of those profiles this setting is required.<br>";
         text += "<b>NOTE: Double-click mouse in this field to bring up File Browser</b>.";
      }
      else if (component.equals("comskip")) {
         text =  "<b>comskip</b><br>";
         text += "<b>REQUIRED</b> if you plan to use <b>comskip</b> commercial detection program.<br>";
         text += "This defines the full path to the <b>comskip</b> program.<br>";
         text += "NOTE: As an alternative you can configure kmttg to use VideoRedo AdScan instead.<br>";
         text += "<b>NOTE: Double-click mouse in this field to bring up File Browser</b>.";
      }
      else if (component.equals("comskip_ini")) {
         text =  "<b>comskip.ini</b><br>";
         text += "<b>REQUIRED</b> if you plan to use <b>comskip</b> commercial detection program.<br>";
         text += "This defines the full path to the <b>comskip.ini</b> comskip configuration file.<br>";
         text += "NOTE: By default comskip.ini is configured to output .edl files which can be used<br>";
         text += "by <b>mencoder</b> program to subsequently cut out commercial segments from mpeg file.<br>";
         text += "NOTE: If you plan to use VideoRedo to cut out commercials you must edit this file.<br>";
         text += "Consult kmttg documentation for further details.<br>";
         text += "<b>NOTE: Double-click mouse in this field to bring up File Browser</b>.";
      }
      else if (component.equals("t2extract")) {
         text =  "<b>t2extract</b><br>";
         text += "<b>REQUIRED</b> if you plan to use <b>captions</b> task (and don't define ccextractor).<br>";
         text += "For Windows systems this program is used for generating closed captions <b>.srt</b> files.<br>";
         text += "This is the full path to the <b>T2Sami t2extract</b> program.<br>";
         text += "NOTE: For Windows this is a better, more robust solution compared to cross-platform ccextractor.<br>";
         text += "NOTE: kmttg will use t2extract over ccextractor if possible.<br>";
         text += "<b>NOTE: Double-click mouse in this field to bring up File Browser</b>.";
      }
      else if (component.equals("t2extract_args")) {
         text =  "<b>t2extract extra arguments</b><br>";
         text += "Any extra arguments you want kmttg to use when running <b>t2extract</b> which is the<br>";
         text += "program used to generate closed captions <b>.srt</b> file. By default the program<br>";
         text += "arguments are as follows: <b>t2extract -f srt videoFile</b>. Extra arguments you<br>";
         text += "supply are added following the <b>-f srt</b> option.<br>";
         text += "NOTE: kmttg expects <b>srt</b> as output file. If you want to output a different format<br>";
         text += "then consider using <b>custom</b> job to run t2extract with whatever arguments you want.";
      }
      else if (component.equals("ccextractor")) {
          text =  "<b>ccextractor</b><br>";
          text += "<b>REQUIRED</b> if you plan to use <b>captions</b> task (and don't define t2extract).<br>";
          text += "Cross-platform program for generating closed captions <b>.srt</b> files from mpeg files.<br>";
          text += "This is the full path to the <b>ccextractor</b> program executable.<br>";
          text += "NOTE: For Windows platform consider using t2sami instead (faster, more robust and supports .TiVo files).<br>";
          text += "NOTE: kmttg will use t2extract over ccextractor if possible.<br>";
          text += "<b>NOTE: Double-click mouse in this field to bring up File Browser</b>.";
       }
      else if (component.equals("VRD_path")) {
         text =  "<b>VideoRedo path</b><br>";
         text += "For Windows systems only if you have VideoRedo program installed on this computer<br>";
         text += "then supply the full path to the VideoRedo installation directory on your computer.<br>";
         text += "This setting is <b>REQUIRED</b> to enable <b>VRD QS fix</b> task which runs VideoRedo<br>";
         text += "to automatically repair glitches/problems in mpeg2 program files.<br>";
         text += "This setting also REQUIRED if you want to use VideoRedo for commercial cutting (<b>comcut</b>) step.<br>";
         text += "Example path setting for Windows Vista:<br>";
         text += "<b>C:\\Program Files (x86)\\VideoRedoPlus</b><br>";
         text += "<b>NOTE: Double-click mouse in this field to bring up File Browser</b>.";
      }
      else if (component.equals("AtomicParsley")) {
         text =  "<b>AtomicParsley</b><br>";
         text += "This defines the full path to the <b>AtomicParsley</b> program.<br>";
         text += "If defined this program is used to automatically add show information<br>";
         text += "to mpeg4 video files following an <b>encode</b> step. This is useful if<br>";
         text += "for example you transfer mpeg4 files to your iTunes library.<br>";
         text += "NOTE: This will only work if you generate <b>pyTivo metadata</b> files<br>";
         text += "to accompany the mpeg4 video files since information is gathered from those<br>";
         text += "files and passed along to this program by kmttg.<br>";
         text += "<b>NOTE: Double-click mouse in this field to bring up File Browser</b>.";
      }
      else if (component.equals("wan_http_port")) {
         text =  "<b>wan http port</b><br>";
         text += "<b>Advanced Setting</b>.<br>";
         text += "Set this option only if you plan to use kmttg over a WAN instead of your local LAN.<br>";
         text += "By default http port 80 is used to download shows from the Tivos on the LAN, but from WAN side<br>";
         text += "you will have to setup port forwarding in your router, and often service providers do not<br>";
         text += "allow you to use port 80.<br>";
         text += "NOTE: For Now Playing List retrieval https port 443 also should be port forwarded to your Tivo<br>";
         text += "port 443 in your router configuration.";
      }
      else if (component.equals("active_job_limit")) {
         text =  "<b>active job limit</b><br>";
         text += "Limits the number of CPU intensive jobs that can kmttg can run in parallel.<br>";
         text += "If you want to allow more CPU intensive jobs to run at the same time (for example if you<br>";
         text += "have a multi-core processor) you can increase this limit accordingly.<br>";
         text += "NOTE: Be careful not to overwhelm your computer by setting this number too high.";
      }
      else if (component.equals("disk_space")) {
         text =  "<b>Min requested space (GB)</b><br>";
         text += "If <b>Check Available Disk Space</b> option is enabled then this setting<br>";
         text += "defines the minimum required disk space (in GB) to be available in order for kmttg<br>";
         text += "to proceed with certain tasks. If you have less space available then kmttg<br>";
         text += "will abort the task with an error message about low disk space.";
      }
      else if (component.equals("customCommand")) {
         text =  "<b>custom command</b><br>";
         text += "Here you can setup any script you want to run as a post-processing step to all other tasks.<br>";
         text += "You can use certain pre-defined keywords in square brackets as arguments to your<br>";
         text += "script if you wish which kmttg will replace with the associated full path file names.<br>";
         text += "The supported keywords are listed in the <b>Available file args</b> cyclic to the right.<br>";
         text += "<b>NOTE: Double-click mouse in this field to bring up File Browser</b>.";
      }
      else if (component.equals("keywords")) {
         text =  "<b>Available keywords</b><br>";
         text += "These are the valid keywords understood by kmttg for setting up file naming template.<br>";
         text += "Consult kmttg documentation for details.";
      }
      else if (component.equals("customFiles")) {
         text =  "<b>Available file args</b><br>";
         text += "These are the valid file keywords understood by kmttg for use with custom command.<br>";
         text += "You can use 1 or more of these keywords as arguments to your custom command.<br>";
         text += "kmttg will substitute the keywords with full path file names accordingly.";
      }
      else if (component.equals("OK")) {
         text =  "<b>OK</b><br>";
         text += "Save all changes made in this form and close the form.<br>";
         text += "NOTE: Settings are saved to <b>config.ini</b> file which resides by <b>kmttg.jar</b> file.";
      }
      else if (component.equals("CANCEL")) {
         text =  "<b>CANCEL</b><br>";
         text += "Do not save any changes made in this form and close the form.";
      }
      else if (component.equals("toolTips")) {
         text =  "<b>Display toolTips</b><br>";
         text += "Enable or disable display of these mouse over popup toolTip messages.";
      }
      else if (component.equals("jobMonitorFullPaths")) {
         text =  "<b>Show full paths in Job Monitor</b><br>";
         text += "Enable or disable display of full paths in Job Monitor OUTPUT column.";
      }
      else if (component.equals("toolTipsTimeout")) {
         text =  "<b>toolTip timeout (secs)</b><br>";
         text += "Time in seconds to timeout display of a toolTip message.";
      }
      else if (component.equals("cpu_cores")) {
         text =  "<b>encoding cpu cores</b><br>";
         text += "If you have a multi-core machine you can set how many cores you would like to use<br>";
         text += "for the encoding task. NOTE: Consider this setting and <b>active job limit</b> when<br>";
         text += "deciding what number to use here. If you set number too high it may slow down the machine<br>";
         text += "for other tasks running in parallel.";
      }
      else if (component.equals("pyTivo_host")) {
         text =  "<b>pyTivo host name</b><br>";
         text += "Host name of the machine you are running pyTivo server on. If it is the same machine as you<br>";
         text += "are running kmttg then <b>localhost</b> is usually the right setting to use. Note that the port<br>";
         text += "number is obtained by kmttg from the <b>pyTivo.conf</b> file.";
      }
      else if (component.equals("pyTivo_config")) {
         text =  "<b>pyTivo.conf file</b><br>";
         text += "Double click in text field to bring up browser to find and set full path<br>";
         text += "to your pyTivo config file <b>pyTivo.conf</b> file. This is where information<br>";
         text += "on available pyTivo shares and their directory locations is contained.";
      }
      else if (component.equals("pyTivo_tivo")) {
         text =  "<b>pyTivo push destination</b><br>";
         text += "Set which TiVo you would like to send files to via pyTivo push.";
      }
      else if (component.equals("pyTivo_files")) {
         text =  "<b>Files to push</b><br>";
         text += "Select which files to push when the <b>push</b> task is enabled for a job.<br>";
         text += "The meaning of each setting is as follows:<br>";
         text += "<b>tivoFile: </b>Push only TiVo file.<br>";
         text += "<b>mpegFile: </b>Push only mpeg file after decrypt task if that task is enabled.<br>";
         text += "<b>mpegFile_cut: </b>Push only mpeg file after AdCut task if that task is enabled.<br>";
         text += "<b>encodeFile: </b>Push only encoded file after encode task if that task is enabled.<br>";
         text += "<b>last: </b>Push only last video file in sequence of tasks (this is default setting).<br>";
         text += "<b>all: </b>Push all available video files for the task set (except for .TiVo files).";
      }
      else if (component.equals("metadata_files")) {
         text =  "<b>metadata files</b><br>";
         text += "Select which files to create metadata files for when <b>metadata</b> task is enabled for a job.<br>";
         text += "The meaning of each setting is as follows:<br>";
         text += "<b>tivoFile: </b>Only for TiVo file.<br>";
         text += "<b>mpegFile: </b>Only for mpeg file after decrypt task if that task is enabled.<br>";
         text += "<b>mpegFile_cut: </b>Only for mpeg file after AdCut task if that task is enabled.<br>";
         text += "<b>encodeFile: </b>Only for encoded file after encode task if that task is enabled.<br>";
         text += "<b>last: </b>Only last video file in sequence of tasks (this is default setting).<br>";
         text += "<b>all: </b>For all available video files for the task set (except for .TiVo files).";
      }
      
      if (text.length() > 0) {
         text = "<html>" + text + "</html>";
      }
      return text;
   }

      
}
