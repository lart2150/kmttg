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
import java.util.LinkedHashMap;
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

import com.tivo.kmttg.main.beacon;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.main.mdns;
import com.tivo.kmttg.task.autotune;
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
   private static JButton autotune_test = null;
   private static JDialog dialog = null;
   private static JComboBox tivos = null;
   private static JCheckBox remove_tivo = null;
   private static JCheckBox remove_comcut = null;
   private static JCheckBox remove_comcut_mpeg = null;
   private static JCheckBox remove_mpeg = null;
   private static JCheckBox QSFixBackupMpegFile = null;
   private static JCheckBox download_check_length = null;
   private static JCheckBox check_space = null;
   private static JCheckBox beacon = null;
   private static JCheckBox npl_when_started = null;
   private static JCheckBox showHistoryInTable = null;
   private static JCheckBox UseOldBeacon = null;
   private static JCheckBox download_time_estimate = null;
   private static JCheckBox UseAdscan = null;
   private static JCheckBox VRD = null;
   private static JCheckBox VrdReview = null;
   private static JCheckBox comskip_review = null;
   private static JCheckBox VrdReview_noCuts = null;
   private static JCheckBox VrdQsFilter = null;
   private static JCheckBox VrdDecrypt = null;
   private static JCheckBox DsdDecrypt = null;
   private static JCheckBox httpserver_enable = null;
   private static JCheckBox VrdEncode = null;
   private static JCheckBox VrdAllowMultiple = null;
   private static JCheckBox VrdCombineCutEncode = null;
   private static JCheckBox VrdQsfixMpeg2ps = null;
   private static JCheckBox TSDownload = null;
   private static JCheckBox TivoWebPlusDelete = null;
   private static JCheckBox iPadDelete = null;
   private static JCheckBox HideProtectedFiles = null;
   private static JCheckBox OverwriteFiles = null;
   private static JCheckBox DeleteFailedDownloads = null;
   private static JCheckBox java_downloads = null;
   private static JCheckBox toolTips = null;
   private static JCheckBox slingBox = null;
   private static JCheckBox tableColAutoSize = null;
   private static JCheckBox jobMonitorFullPaths = null;
   private static JCheckBox autotune_enabled = null;
   private static JCheckBox combine_download_decrypt = null;
   private static JCheckBox single_download = null;
   private static JCheckBox rpcnpl = null;
   private static JCheckBox enableRpc = null;
   private static JCheckBox persistQueue = null;
   private static JTextField tivo_name = null;
   private static JTextField tivo_ip = null;
   private static JTextField files_path = null;
   private static JTextField MAK = null;
   private static JTextField FontSize = null;
   private static JTextField file_naming = null;
   private static JTextField tivo_output_dir = null;
   private static JTextField mpeg_output_dir = null;
   private static JTextField qsfixDir = null;
   private static JTextField mpeg_cut_dir = null;
   private static JTextField encode_output_dir = null;
   private static JTextField tivodecode = null;
   private static JTextField dsd = null;
   private static JTextField curl = null;
   private static JTextField ffmpeg = null;
   private static JTextField mediainfo = null;
   private static JTextField mencoder = null;
   private static JTextField handbrake = null;
   private static JTextField comskip = null;
   private static JTextField comskip_ini = null;
   private static JTextField wan_http_port = null;
   private static JTextField wan_https_port = null;
   private static JTextField wan_ipad_port = null;
   private static JTextField limit_npl_fetches = null;
   private static JTextField active_job_limit = null;
   private static JTextField t2extract = null;
   //private static JTextField t2extract_args = null;
   private static JTextField mencoder_args = null;
   private static JTextField ccextractor = null;
   private static JTextField AtomicParsley = null;
   private static JTextField projectx = null;
   private static JTextField disk_space = null;
   private static JTextField customCommand = null;
   private static JTextField toolTipsTimeout = null;
   private static JTextField cpu_cores = null;
   private static JTextField download_tries = null;
   private static JTextField download_retry_delay = null;
   private static JTextField download_delay = null;
   private static JTextField metadata_entries = null;
   private static JTextField httpserver_port = null;
   private static JTextField autoLogSizeMB = null;
   private static JTextField pyTivo_host = null;
   private static JTextField web_query = null;
   private static JTextField web_browser = null;
   private static JTextField tivo_username = null;
   private static JTextField tivo_password = null;
   private static JTextField pyTivo_config = null;
   private static JTextField autotune_channel_interval = null;
   private static JTextField autotune_button_interval = null;
   private static JTextField autotune_chan1 = null;
   private static JTextField autotune_chan2 = null;
   private static JComboBox MinChanDigits = null;
   private static JComboBox pyTivo_tivo = null;
   private static JComboBox pyTivo_files = null;
   private static JComboBox metadata_files = null;
   private static JComboBox keywords = null;
   private static JComboBox customFiles = null;
   private static JComboBox autotune_tivoName = null;
   private static JComboBox lookAndFeel = null;
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
      config.gui.refreshOptions(true);
   }
   
   // Callback for tivo add button
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
   
   private static void updateWanSettings(String setting) {
      if (setting != null) {
         String tivoName = setting.replaceFirst("=.+$", "");
         // Update http & https setting according to selected TiVo
         String http = config.getWanSetting(tivoName, "http");
         if (http != null) {
            wan_http_port.setText(http);
         } else {
            wan_http_port.setText("");
         }
         String https = config.getWanSetting(tivoName, "https");
         if (https != null) {
            wan_https_port.setText(https);
         } else {
            wan_https_port.setText("");
         }
         String ipad = config.getWanSetting(tivoName, "ipad");
         if (ipad != null) {
            wan_ipad_port.setText(ipad);
         } else {
            wan_ipad_port.setText("");
         }
      }
   }
   
   private static void updateLimitNplSettings(String setting) {
      if (setting != null) {
         String tivoName = setting.replaceFirst("=.+$", "");
         // Update limit_npl_fetches setting according to selected TiVo
         int limit = config.getLimitNplSetting(tivoName);
         limit_npl_fetches.setText("" + limit);
      }
   }
   
   private static void updateEnableRpcSettings(String setting) {
      if (setting != null) {
         String tivoName = setting.replaceFirst("=.+$", "");
         // Update enableRpc setting according to selected TiVo
         if (config.rpcEnabled(tivoName))
            enableRpc.setSelected(true);
         else
            enableRpc.setSelected(false);
      }
   }
   
   // Callback for tivo del button
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
   
   // Callback for autotune test button
   private static void autotune_testCB() {
      debug.print("");
      if ( autotune_tivoName.getComponentCount() > 0 ) {
         String cinterval = string.removeLeadingTrailingSpaces(
            autotune_channel_interval.getText()
         );
         String binterval = string.removeLeadingTrailingSpaces(
            autotune_button_interval.getText()
         );
         String chan1 = string.removeLeadingTrailingSpaces(
            autotune_chan1.getText()
         );
         String chan2 = string.removeLeadingTrailingSpaces(
            autotune_chan2.getText()
         );
         int channel_interval, button_interval;
         if (cinterval.length() == 0) {
            log.error("channel interval number not specified");
            return;
         } else {
            try {
               channel_interval = Integer.parseInt(
                  string.removeLeadingTrailingSpaces(cinterval)
               );
            } catch (Exception e) {
               log.error("channel interval should be an integer");
               return;
            }
         }
         if (binterval.length() == 0) {
            log.error("button interval number not specified");
            return;
         } else {
            try {
               button_interval = Integer.parseInt(
                  string.removeLeadingTrailingSpaces(binterval)
               );
            } catch (Exception e) {
               log.error("button interval should be an integer");
               return;
            }
         }
         if (chan1.length() == 0) {
            log.error("channel 1 not specified");
            return;
         }
         if (chan2.length() == 0) {
            log.error("channel 2 not specified");
            return;
         }
         String tivoName = (String)autotune_tivoName.getSelectedItem();
         if (tivoName == null || tivoName.length() == 0) {
            log.error("No TiVo name selected");
            return;
         }
         jobData job = new jobData();
         job.source   = tivoName;
         job.tivoName = tivoName;
         job.type     = "autotune";
         job.name     = "telnet";
         job.autotune_channel_interval = channel_interval;
         job.autotune_button_interval = button_interval;
         job.autotune_chan1 = chan1;
         job.autotune_chan2 = chan2;
         jobMonitor.submitNewJob(job);
      }
   }
   
   // Callback for keywords combobox
   private static void keywordsCB() {
      debug.print("");
      // Get currently selected item
      String keyword = (String)keywords.getSelectedItem();
      
      if (keyword != null) {
         // Append selected entry to file_naming text field
         // (Replace current selection if any)
         int len = file_naming.getText().length();
         file_naming.setCaretPosition(len);
         file_naming.replaceSelection(keyword);
      }
      keywords.setSelectedItem(null);
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
   
   // Callback for autotune_tivoName combobox
   private static void autotune_tivoNameCB() {
      debug.print("");
      String name = (String)autotune_tivoName.getSelectedItem();
      if (name != null && name.length() > 0) {
         if (autotune.isConfigured(name))
            autotune_enabled.setSelected(true);
         else
            autotune_enabled.setSelected(false);
         autotune_channel_interval.setText("" + config.autotune.get(name).get("channel_interval"));
         autotune_button_interval.setText("" + config.autotune.get(name).get("button_interval"));
         autotune_chan1.setText("" + config.autotune.get(name).get("chan1"));
         autotune_chan2.setText("" + config.autotune.get(name).get("chan2"));
      }
   }
   
   // Update widgets with config settings
   public static void read() {
      debug.print("");
      String name;
      // Tivos
      Stack<String> tivoNames = config.getTivoNames();
      if (tivoNames.size()>0) {
         // Update tivo name lists
         tivos.removeAllItems();
         autotune_tivoName.removeAllItems();
         String ip;
         for (int i=0; i<tivoNames.size(); i++) {
            name = tivoNames.get(i);
            ip = config.TIVOS.get(name);
            tivos.addItem(name + "=" + ip);
            if (config.nplCapable(name))
               autotune_tivoName.addItem(name);
         }         
      }
      
      // enableRpc
      enableRpc.setSelected(false);
      name = (String)tivos.getSelectedItem();
      if (name != null) {
         String tivoName = name.replaceFirst("=.+$", "");
         if (config.rpcEnabled(tivoName))
            enableRpc.setSelected(true);
         else
            enableRpc.setSelected(false);
      }      
      
      // limit_npl_fetches
      limit_npl_fetches.setText("0");
      name = (String)tivos.getSelectedItem();
      if (name != null) {
         String tivoName = name.replaceFirst("=.+$", "");
         int limit = config.getLimitNplSetting(tivoName);
         limit_npl_fetches.setText("" + limit);
      }
      
      // wan http & https ports
      wan_http_port.setText("");
      wan_https_port.setText("");
      name = (String)tivos.getSelectedItem();
      if (name != null) {
         String tivoName = name.replaceFirst("=.+$", "");
         String http = config.getWanSetting(tivoName, "http");
         if (http != null)
            wan_http_port.setText(http);
         String https = config.getWanSetting(tivoName, "https");
         if (https != null)
            wan_https_port.setText(https);
         String ipad = config.getWanSetting(tivoName, "ipad");
         if (ipad != null)
            wan_ipad_port.setText(ipad);
      }
            
      // Beacon
      if (config.CheckBeacon == 1)
         beacon.setSelected(true);
      else
         beacon.setSelected(false);
      
      // UseOldBeacon
      if (config.UseOldBeacon == 1)
         UseOldBeacon.setSelected(true);
      else
         UseOldBeacon.setSelected(false);
      
      // npl_when_started
      if (config.npl_when_started == 1)
         npl_when_started.setSelected(true);
      else
         npl_when_started.setSelected(false);
      
      // showHistoryInTable
      if (config.showHistoryInTable == 1)
         showHistoryInTable.setSelected(true);
      else
         showHistoryInTable.setSelected(false);
      
      // download_time_estimate
      if (config.download_time_estimate == 1)
         download_time_estimate.setSelected(true);
      else
         download_time_estimate.setSelected(false);
      
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

      if (config.QSFixBackupMpegFile == 1)
         QSFixBackupMpegFile.setSelected(true);
      else
         QSFixBackupMpegFile.setSelected(false);

      if (config.download_check_length == 1)
         download_check_length.setSelected(true);
      else
         download_check_length.setSelected(false);
      
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
      
      // comskip_review
      if (config.comskip_review == 1)
         comskip_review.setSelected(true);
      else
         comskip_review.setSelected(false);
      
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
      
      // DsdDecrypt
      if (config.DsdDecrypt == 1)
         DsdDecrypt.setSelected(true);
      else
         DsdDecrypt.setSelected(false);
      
      // httpserver_enable
      if (config.httpserver_enable == 1)
         httpserver_enable.setSelected(true);
      else
         httpserver_enable.setSelected(false);
      
      // VRD flag
      if (config.VRD == 1)
         VRD.setSelected(true);
      else
         VRD.setSelected(false);
      
      // VrdEncode
      if (config.VrdEncode == 1)
         VrdEncode.setSelected(true);
      else
         VrdEncode.setSelected(false);
      
      // VrdAllowMultiple
      if (config.VrdAllowMultiple == 1)
         VrdAllowMultiple.setSelected(true);
      else
         VrdAllowMultiple.setSelected(false);
      
      // VrdCombineCutEncode
      if (config.VrdCombineCutEncode == 1)
         VrdCombineCutEncode.setSelected(true);
      else
         VrdCombineCutEncode.setSelected(false);
      
      // VrdQsfixMpeg2ps
      if (config.VrdQsfixMpeg2ps == 1)
         VrdQsfixMpeg2ps.setSelected(true);
      else
         VrdQsfixMpeg2ps.setSelected(false);
      
      // TSDownload
      if (config.TSDownload == 1)
         TSDownload.setSelected(true);
      else
         TSDownload.setSelected(false);
      
      // TivoWebPlusDelete
      if (config.twpDeleteEnabled())
         TivoWebPlusDelete.setSelected(true);
      else
         TivoWebPlusDelete.setSelected(false);
      
      // iPadDelete
      if (config.iPadDelete == 1)
         iPadDelete.setSelected(true);
      else
         iPadDelete.setSelected(false);
      
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
      
      // DeleteFailedDownloads
      if (config.DeleteFailedDownloads == 1)
         DeleteFailedDownloads.setSelected(true);
      else
         DeleteFailedDownloads.setSelected(false);
      
      // java_downloads
      if (config.java_downloads == 1)
         java_downloads.setSelected(true);
      else
         java_downloads.setSelected(false);
      
      // combine_download_decrypt
      if (config.combine_download_decrypt == 1)
         combine_download_decrypt.setSelected(true);
      else
         combine_download_decrypt.setSelected(false);
      
      // single_download
      if (config.single_download == 1)
         single_download.setSelected(true);
      else
         single_download.setSelected(false);
      
      // rpcnpl
      if (config.rpcnpl == 1)
         rpcnpl.setSelected(true);
      else
         rpcnpl.setSelected(false);
      
      // persistQueue
      if (config.persistQueue)
    	  persistQueue.setSelected(true);
      else
    	  persistQueue.setSelected(false);
      
      // toolTips
      if (config.toolTips == 1)
         toolTips.setSelected(true);
      else
         toolTips.setSelected(false);
      
      // slingBox
      if (config.slingBox == 1)
         slingBox.setSelected(true);
      else
         slingBox.setSelected(false);
      
      // tableColAutoSize
      if (config.tableColAutoSize == 1)
         tableColAutoSize.setSelected(true);
      else
         tableColAutoSize.setSelected(false);
      
      // jobMonitorFullPaths
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
      
      // qsfixDir
      qsfixDir.setText(config.qsfixDir);
      
      // .mpg cut dir
      mpeg_cut_dir.setText(config.mpegCutDir);
      
      // encode output dir
      encode_output_dir.setText(config.encodeDir);
            
      // mencoder
      mencoder.setText(config.mencoder);
      
      // mencoder_args
      mencoder_args.setText(config.mencoder_args);

      // handbrake
      handbrake.setText(config.handbrake);
      
      // comskip
      comskip.setText(config.comskip);
      
      // comskip_ini
      comskip_ini.setText(config.comskipIni);
      
      // tivodecode
      tivodecode.setText(config.tivodecode);
      
      // dsd
      dsd.setText(config.dsd);
      
      // t2extract
      t2extract.setText(config.t2extract);
      
      // t2extract_args
      //t2extract_args.setText(config.t2extract_args);
      
      // ccextractor
      ccextractor.setText(config.ccextractor);
      
      // curl
      curl.setText(config.curl);
      
      // AtomicParsley
      AtomicParsley.setText(config.AtomicParsley);
      
      // projectx
      projectx.setText(config.projectx);
      
      // ffmpeg
      ffmpeg.setText(config.ffmpeg);
      
      // mediainfo
      mediainfo.setText(config.mediainfo);
      
      // customCommand
      customCommand.setText(config.customCommand);
      
      // active job limit
      active_job_limit.setText("" + config.MaxJobs);
      
      // MinChanDigits
      MinChanDigits.setSelectedItem(config.MinChanDigits);
      
      // toolTipsTimeout
      toolTipsTimeout.setText("" + config.toolTipsTimeout);
      
      // cpu_cores
      cpu_cores.setText("" + config.cpu_cores);
      
      // download_tries
      download_tries.setText("" + config.download_tries);
      
      // download_retry_delay
      download_retry_delay.setText("" + config.download_retry_delay);
      
      // download_delay
      download_delay.setText("" + config.download_delay);
      
      // metadata_entries
      metadata_entries.setText("" + config.metadata_entries);
      
      // httpserver_port
      httpserver_port.setText("" + config.httpserver_port);
      
      // autoLogSizeMB
      autoLogSizeMB.setText("" + config.autoLogSizeMB);
      
      // pyTivo_host
      pyTivo_host.setText("" + config.pyTivo_host);
      
      // web_query
      if (config.web_query.length() > 0)
         web_query.setText("" + config.web_query);
      else
         web_query.setText("http://www.imdb.com/find?s=all&q=");
      
      // web_browser
      if (config.web_browser.length() > 0)
         web_browser.setText("" + config.web_browser);
      else
         web_browser.setText("");
      
      // tivo_username
      if (config.getTivoUsername() != null)
         tivo_username.setText("" + config.getTivoUsername());
      else
         tivo_username.setText("");
      
      // tivo_password
      if (config.getTivoPassword() != null)
         tivo_password.setText("" + config.getTivoPassword());
      else
         tivo_password.setText("");
      
      // pyTivo_config
      pyTivo_config.setText("" + config.pyTivo_config);
      
      // pyTivo_tivo
      Stack<String> names = config.getNplTivoNames();
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
      
      // lookAndFeel
      if (lookAndFeel != null && config.lookAndFeel != null)
         lookAndFeel.setSelectedItem(config.lookAndFeel);
      
      // autotune settings
      if (autotune_tivoName != null) {
         name = (String)autotune_tivoName.getSelectedItem();
      } else {
         name = config.getNplTivoNames().get(0);
      }
      if (name != null && name.length() > 0) {
         if (autotune.isConfigured(name))
            autotune_enabled.setSelected(true);
         else
            autotune_enabled.setSelected(false);
         autotune_channel_interval.setText("" + config.autotune.get(name).get("channel_interval"));
         autotune_button_interval.setText("" + config.autotune.get(name).get("button_interval"));
         autotune_chan1.setText("" + config.autotune.get(name).get("chan1"));
         autotune_chan2.setText("" + config.autotune.get(name).get("chan2"));
      }
   }
   
   // Update config settings with widget values
   public static int write() {
      debug.print("");
      int errors = 0;
      String value;
      String name;
      
      // enableRpc
      name = (String)tivos.getSelectedItem();
      if (name != null) {
         String tivoName = name.replaceFirst("=.+$", "");
         if (enableRpc.isSelected())
            config.setRpcSetting("enableRpc_" + tivoName, "1");
         else
            config.setRpcSetting("enableRpc_" + tivoName, "0");
      }
      
      // Tivos
      int count = tivos.getItemCount();
      LinkedHashMap<String,String> h = new LinkedHashMap<String,String>();
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
      
      // limit_npl_fetches
      name = (String)tivos.getSelectedItem();
      if (name != null) {
         String tivoName = name.replaceFirst("=.+$", "");
         value = string.removeLeadingTrailingSpaces(limit_npl_fetches.getText());
         if (value.length() > 0) {
            try {
               Integer.parseInt(value);
               config.setLimitNplSetting("limit_npl_" + tivoName, value);
            } catch(NumberFormatException e) {
               textFieldError(limit_npl_fetches, "limit npl fetches should be a number: '" + value + "'");
               errors++;
            }
         } else {
            config.setLimitNplSetting("limit_npl_" + tivoName, "0");
         }
      }
      
      // wan http & https ports
      name = (String)tivos.getSelectedItem();
      if (name != null) {
         String tivoName = name.replaceFirst("=.+$", "");
         value = string.removeLeadingTrailingSpaces(wan_http_port.getText());
         if (value.length() > 0) {
            try {
               Integer.parseInt(value);
               config.setWanSetting(tivoName, "http", value);
            } catch(NumberFormatException e) {
               textFieldError(wan_http_port, "wan http port should be a number: '" + value + "'");
               errors++;
            }
         } else {
            config.setWanSetting(tivoName, "http", "");
         }
         
         value = string.removeLeadingTrailingSpaces(wan_https_port.getText());
         if (value.length() > 0) {
            try {
               Integer.parseInt(value);
               config.setWanSetting(tivoName, "https", value);
            } catch(NumberFormatException e) {
               textFieldError(wan_https_port, "wan https port should be a number: '" + value + "'");
               errors++;
            }
         } else {
            config.setWanSetting(tivoName, "https", "");
         }
         
         value = string.removeLeadingTrailingSpaces(wan_ipad_port.getText());
         if (value.length() > 0) {
            try {
               Integer.parseInt(value);
               config.setWanSetting(tivoName, "ipad", value);
            } catch(NumberFormatException e) {
               textFieldError(wan_ipad_port, "wan ipad port should be a number: '" + value + "'");
               errors++;
            }
         } else {
            config.setWanSetting(tivoName, "ipad", "");
         }
      }
      
      // UseOldBeacon
      if (UseOldBeacon.isSelected()) {
         config.UseOldBeacon = 1;
      } else {
         config.UseOldBeacon = 0;
      }
      
      // download_time_estimate
      if (download_time_estimate.isSelected()) {
         config.download_time_estimate = 1;
      } else {
         config.download_time_estimate = 0;
      }
      
      // npl_when_started
      if (npl_when_started.isSelected()) {
         config.npl_when_started = 1;
      } else {
         config.npl_when_started = 0;
      }
      
      // showHistoryInTable
      if (showHistoryInTable.isSelected()) {
         config.showHistoryInTable = 1;
      } else {
         config.showHistoryInTable = 0;
      }
      
      // Beacon
      if (beacon.isSelected()) {
         config.CheckBeacon = 1;
         if (config.UseOldBeacon == 0) {
            if (config.jmdns == null) config.jmdns = new mdns();            
         } else {
            if (config.tivo_beacon == null) config.tivo_beacon = new beacon();
         }         
      } else {
         config.CheckBeacon = 0;
         if (config.UseOldBeacon == 0) {
            if (config.jmdns != null) {
               config.jmdns.close();
               config.jmdns = null;
            }
         } else {
            config.tivo_beacon = null;
         }
      }
            
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
      
      if (QSFixBackupMpegFile.isSelected())
         config.QSFixBackupMpegFile = 1;
      else
         config.QSFixBackupMpegFile = 0;
      
      if (download_check_length.isSelected())
         config.download_check_length = 1;
      else
         config.download_check_length = 0;
      
      // Check disk space
      if (check_space.isSelected())
         config.CheckDiskSpace = 1;
      else
         config.CheckDiskSpace = 0;
      
      // VRD flag
      if (VRD.isSelected())
         config.VRD = 1;
      else
         config.VRD = 0;
      
      // UseAdscan
      if (UseAdscan.isSelected() && config.VRD == 1)
         config.UseAdscan = 1;
      else
         config.UseAdscan = 0;
      
      // VrdReview
      if (VrdReview.isSelected() && config.VRD == 1)
         config.VrdReview = 1;
      else
         config.VrdReview = 0;
      
      // comskip_review
      if (comskip_review.isSelected() && file.isFile(config.comskip))
         config.comskip_review = 1;
      else
         config.comskip_review = 0;
      
      // VrdReview_noCuts
      if (VrdReview_noCuts.isSelected() && config.VRD == 1)
         config.VrdReview_noCuts = 1;
      else
         config.VrdReview_noCuts = 0;
      
      // VrdQsFilter
      if (VrdQsFilter.isSelected() && config.VRD == 1)
         config.VrdQsFilter = 1;
      else
         config.VrdQsFilter = 0;
      
      // VrdDecrypt
      if (VrdDecrypt.isSelected() && config.VRD == 1)
         config.VrdDecrypt = 1;
      else
         config.VrdDecrypt = 0;
      
      // DsdDecrypt
      if (DsdDecrypt.isSelected() && file.isFile(config.dsd))
         config.DsdDecrypt = 1;
      else
         config.DsdDecrypt = 0;
      
      // httpserver_enable
      if (httpserver_enable.isSelected())
         config.httpserver_enable = 1;
      else
         config.httpserver_enable = 0;
      
      // VrdEncode
      if (VrdEncode.isSelected() && config.VRD == 1)
         config.VrdEncode = 1;
      else
         config.VrdEncode = 0;
      
      // VrdAllowMultiple
      if (VrdAllowMultiple.isSelected() && config.VRD == 1)
         config.VrdAllowMultiple = 1;
      else
         config.VrdAllowMultiple = 0;
      
      // VrdCombineCutEncode
      if (VrdCombineCutEncode.isSelected() && config.VRD == 1)
         config.VrdCombineCutEncode = 1;
      else
         config.VrdCombineCutEncode = 0;
      
      // VrdQsfixMpeg2ps
      if (VrdQsfixMpeg2ps.isSelected() && config.VRD == 1)
         config.VrdQsfixMpeg2ps = 1;
      else
         config.VrdQsfixMpeg2ps = 0;
      
      // TSDownload
      if (TSDownload.isSelected())
         config.TSDownload = 1;
      else
         config.TSDownload = 0;
      
      // TivoWebPlusDelete
      if (TivoWebPlusDelete.isSelected())
         config.twpDeleteEnabledSet(true);
      else
         config.twpDeleteEnabledSet(false);
      
      // iPadDelete
      if (iPadDelete.isSelected())
         config.iPadDelete = 1;
      else
         config.iPadDelete = 0;
      
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
      
      // DeleteFailedDownloads
      if (DeleteFailedDownloads.isSelected())
         config.DeleteFailedDownloads = 1;
      else
         config.DeleteFailedDownloads = 0;
      
      // java_downloads
      if (java_downloads.isSelected())
         config.java_downloads = 1;
      else
         config.java_downloads = 0;
      
      // combine_download_decrypt
      if (combine_download_decrypt.isSelected())
         config.combine_download_decrypt = 1;
      else
         config.combine_download_decrypt = 0;
      
      // single_download
      if (single_download.isSelected())
         config.single_download = 1;
      else
         config.single_download = 0;
      
      // rpcnpl
      if (rpcnpl.isSelected())
         config.rpcnpl = 1;
      else
         config.rpcnpl = 0;
      
      // persistQueue
      if (persistQueue.isSelected())
         config.persistQueue = true;
      else
         config.persistQueue = false;
      
      // toolTips
      if (toolTips.isSelected())
         config.toolTips = 1;
      else
         config.toolTips = 0;
      config.gui.enableToolTips(config.toolTips);
      
      // slingBox
      if (slingBox.isSelected())
         config.slingBox = 1;
      else
         config.slingBox = 0;
      
      // tableColAutoSize
      if (tableColAutoSize.isSelected())
         config.tableColAutoSize = 1;
      else
         config.tableColAutoSize = 0;
      
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
      
      // qsfixDir
      value = string.removeLeadingTrailingSpaces(qsfixDir.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = config.mpegDir;
      } else {
         if ( ! file.isDir(value) ) {
            textFieldError(qsfixDir, "QS Fix Output Dir setting not a valid dir: '" + value + "'");
            errors++;
         }
      }
      config.qsfixDir = value;
      
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
      
      // mencoder_args
      value = string.removeLeadingTrailingSpaces(mencoder_args.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "";
      }
      config.mencoder_args = value;
      
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
      
      // dsd
      value = string.removeLeadingTrailingSpaces(dsd.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "";
      } else {
         if ( ! file.isFile(value) ) {
            textFieldError(dsd, "dsd setting not a valid file: '" + value + "'");
            errors++;
         }
      }
      config.dsd = value;
      
      // t2extract
      value = string.removeLeadingTrailingSpaces(t2extract.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "";
      } else {
         if ( ! file.isFile(value) ) {
            textFieldError(t2extract, "ccextractor setting not a valid file: '" + value  + "'");
            errors++;
         }
      }
      config.t2extract = value;
      
      /*// t2extract_args
      value = string.removeLeadingTrailingSpaces(t2extract_args.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "";
      }
      config.t2extract_args = value;*/
      
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
      
      // projectx
      value = string.removeLeadingTrailingSpaces(projectx.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "";
      } else {
         if ( ! file.isFile(value) ) {
            textFieldError(projectx, "ProjectX setting not a valid file: '" + value + "'");
            errors++;
         }
      }
      config.projectx = value;
      
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
      
      // mediainfo
      value = string.removeLeadingTrailingSpaces(mediainfo.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "";
      } else {
         if ( ! file.isFile(value) ) {
            textFieldError(mediainfo, "mediainfo setting not a valid file: '" + value + "'");
            errors++;
         }
      }
      config.mediainfo = value;
      
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
      
      // MinChanDigits
      config.MinChanDigits = (Integer)MinChanDigits.getSelectedItem();
      
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
      
      // download_tries
      value = string.removeLeadingTrailingSpaces(download_tries.getText());
      if (value.length() > 0) {
         try {
            config.download_tries = Integer.parseInt(value);
         } catch(NumberFormatException e) {
            textFieldError(download_tries, "Illegal setting for # download tries: '" + value + "'");
            log.error("Setting to 5");
            config.download_tries = 5;
            download_tries.setText("" + config.download_tries);
            errors++;
         }
      } else {
         config.download_tries = 5;
      }
      
      // download_retry_delay
      value = string.removeLeadingTrailingSpaces(download_retry_delay.getText());
      if (value.length() > 0) {
         try {
            config.download_retry_delay = Integer.parseInt(value);
         } catch(NumberFormatException e) {
            textFieldError(download_retry_delay, "Illegal setting for delay between download tries: '" + value + "'");
            log.error("Setting to 10");
            config.download_retry_delay = 10;
            download_retry_delay.setText("" + config.download_retry_delay);
            errors++;
         }
      } else {
         config.download_retry_delay = 10;
      }
      
      // metadata_entries
      value = string.removeLeadingTrailingSpaces(metadata_entries.getText());
      if (value.length() > 0) {
         config.metadata_entries = value;
      } else {
         config.metadata_entries = "";
      }
      
      // httpserver_port
      value = string.removeLeadingTrailingSpaces(httpserver_port.getText());
      if (value.length() > 0) {
         try {
            config.httpserver_port = Integer.parseInt(value);
         } catch(NumberFormatException e) {
            textFieldError(httpserver_port, "Illegal setting for kmttg web server port: '" + value + "'");
            log.error("Setting to 8181");
            config.httpserver_port = 8181;
            httpserver_port.setText("" + config.httpserver_port);
            errors++;
         }
      } else {
         config.httpserver_port = 8181;
      }
            
      // download_delay
      value = string.removeLeadingTrailingSpaces(download_delay.getText());
      if (value.length() > 0) {
         try {
            config.download_delay = Integer.parseInt(value);
         } catch(NumberFormatException e) {
            textFieldError(download_delay, "Illegal setting for download delay: '" + value + "'");
            log.error("Setting to 10");
            config.download_delay = 10;
            download_delay.setText("" + config.download_delay);
            errors++;
         }
      } else {
         config.download_delay = 10;
      }
      
      // autoLogSizeMB
      value = string.removeLeadingTrailingSpaces(autoLogSizeMB.getText());
      if (value.length() > 0) {
         try {
            config.autoLogSizeMB = Integer.parseInt(value);
            if (config.autoLogSizeMB < 1) {
               textFieldError(autoLogSizeMB, "Illegal setting for auto log file size limit (MB): '" + config.autoLogSizeMB + "'");
               log.error("Should be integer > 0... Setting to 10");
               config.autoLogSizeMB = 10;
               autoLogSizeMB.setText("" + config.autoLogSizeMB);
               errors++;               
            }
         } catch(NumberFormatException e) {
            textFieldError(autoLogSizeMB, "Illegal setting for auto log file size limit (MB): '" + value + "'");
            log.error("Should be integer > 0... Setting to 10");
            config.autoLogSizeMB = 10;
            autoLogSizeMB.setText("" + config.autoLogSizeMB);
            errors++;
         }
      } else {
         config.autoLogSizeMB = 10;
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
      
      // web_query
      value = string.removeLeadingTrailingSpaces(web_query.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "http://www.imdb.com/find?s=all&q=";
      }
      config.web_query = value;
      
      // web_browser
      value = string.removeLeadingTrailingSpaces(web_browser.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "";
      }
      config.web_browser = value;
      
      // tivo_username
      value = string.removeLeadingTrailingSpaces(tivo_username.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "";
      }
      config.setTivoUsername(value);
      
      // tivo_password
      value = string.removeLeadingTrailingSpaces(tivo_password.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "";
      }
      config.setTivoPassword(value);
      
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
      
      // lookAndFeel
      config.lookAndFeel = (String)lookAndFeel.getSelectedItem();
      
      // autotune settings
      if (autotune_tivoName != null && autotune_tivoName.getComponentCount() > 0) {
         name = (String)autotune_tivoName.getSelectedItem();
         if (name != null) {
            if (autotune_enabled.isSelected())
               autotune.enable(name);
            else
               autotune.disable(name);
            config.autotune.get(name).put("channel_interval", string.removeLeadingTrailingSpaces(autotune_channel_interval.getText()));
            config.autotune.get(name).put("button_interval", string.removeLeadingTrailingSpaces(autotune_button_interval.getText()));
            config.autotune.get(name).put("chan1", string.removeLeadingTrailingSpaces(autotune_chan1.getText()));
            config.autotune.get(name).put("chan2", string.removeLeadingTrailingSpaces(autotune_chan2.getText()));
         }
      }
      
      return errors;
   }

   private static void create(JFrame frame) {
      debug.print("frame=" + frame);
      encode_output_dir = new javax.swing.JTextField(30);
      textbg_default = encode_output_dir.getBackground();
      mpeg_cut_dir = new javax.swing.JTextField(30);
      mpeg_output_dir = new javax.swing.JTextField(30);
      qsfixDir = new javax.swing.JTextField(30);
      mpeg_cut_dir = new javax.swing.JTextField(30);
      tivo_output_dir = new javax.swing.JTextField(30);
      file_naming = new javax.swing.JTextField(30);
      files_path = new javax.swing.JTextField(30);
      tivodecode = new javax.swing.JTextField(30);
      dsd = new javax.swing.JTextField(30);
      curl = new javax.swing.JTextField(30);
      ffmpeg = new javax.swing.JTextField(30);
      mediainfo = new javax.swing.JTextField(30);
      mencoder = new javax.swing.JTextField(30);
      mencoder_args = new javax.swing.JTextField(30);
      handbrake = new javax.swing.JTextField(30);
      comskip = new javax.swing.JTextField(30);
      comskip_ini = new javax.swing.JTextField(30);
      t2extract = new javax.swing.JTextField(30);
      //t2extract_args = new javax.swing.JTextField(30);
      ccextractor = new javax.swing.JTextField(30);
      AtomicParsley = new javax.swing.JTextField(30);
      projectx = new javax.swing.JTextField(30);
      customCommand = new javax.swing.JTextField(30);
      web_query = new javax.swing.JTextField(30);
      web_browser = new javax.swing.JTextField(30);
      tivo_username = new javax.swing.JTextField(30);
      tivo_password = new javax.swing.JTextField(30);
      pyTivo_config = new javax.swing.JTextField(30);
      
      tivo_name = new javax.swing.JTextField(20);
      tivo_ip = new javax.swing.JTextField(20);
      autotune_channel_interval = new javax.swing.JTextField(20);
      autotune_button_interval = new javax.swing.JTextField(20);
      autotune_chan1 = new javax.swing.JTextField(20);
      autotune_chan2 = new javax.swing.JTextField(20);
      pyTivo_host = new javax.swing.JTextField(20);
      
      MAK = new javax.swing.JTextField(15);
      wan_http_port = new javax.swing.JTextField(15);
      wan_https_port = new javax.swing.JTextField(15);
      wan_ipad_port = new javax.swing.JTextField(15);
      limit_npl_fetches = new javax.swing.JTextField(15);
      active_job_limit = new javax.swing.JTextField(15);
      toolTipsTimeout = new javax.swing.JTextField(15);
      cpu_cores = new javax.swing.JTextField(15);
      download_tries = new javax.swing.JTextField(15);
      download_retry_delay = new javax.swing.JTextField(15);
      download_delay = new javax.swing.JTextField(15);
      metadata_entries = new javax.swing.JTextField(15);
      httpserver_port = new javax.swing.JTextField(15);
      autoLogSizeMB = new javax.swing.JTextField(15);
      
      disk_space = new javax.swing.JTextField(5);
      FontSize = new javax.swing.JTextField(5);
      
      JLabel tivos_label = new javax.swing.JLabel();
      tivos = new javax.swing.JComboBox();
      tivos.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
             if (e.getStateChange() == ItemEvent.SELECTED) {
                String name = (String)tivos.getSelectedItem();
                if (name != null) {
                   updateWanSettings(name);
                   updateLimitNplSettings(name);
                   updateEnableRpcSettings(name);
                }
            }
         }
      });

      add = new javax.swing.JButton();
      del = new javax.swing.JButton();
      JLabel tivo_name_label = new javax.swing.JLabel();
      JLabel tivo_ip_label = new javax.swing.JLabel();
      JLabel autotune_channel_interval_label = new javax.swing.JLabel();
      JLabel autotune_button_interval_label = new javax.swing.JLabel();
      JLabel autotune_chan1_label = new javax.swing.JLabel();
      JLabel autotune_chan2_label = new javax.swing.JLabel();
      JLabel autotune_tivoName_label = new javax.swing.JLabel();
      JLabel files_path_label = new javax.swing.JLabel();
      remove_tivo = new javax.swing.JCheckBox();
      remove_comcut = new javax.swing.JCheckBox();
      remove_comcut_mpeg = new javax.swing.JCheckBox();
      remove_mpeg = new javax.swing.JCheckBox();
      QSFixBackupMpegFile = new javax.swing.JCheckBox();
      download_check_length = new javax.swing.JCheckBox();
      UseAdscan = new javax.swing.JCheckBox();
      VRD = new javax.swing.JCheckBox();
      VrdReview = new javax.swing.JCheckBox();
      comskip_review = new javax.swing.JCheckBox();
      VrdReview_noCuts = new javax.swing.JCheckBox();
      VrdQsFilter = new javax.swing.JCheckBox();
      VrdDecrypt = new javax.swing.JCheckBox();
      DsdDecrypt = new javax.swing.JCheckBox();
      httpserver_enable = new javax.swing.JCheckBox();
      VrdEncode = new javax.swing.JCheckBox();
      VrdAllowMultiple = new javax.swing.JCheckBox();
      VrdCombineCutEncode = new javax.swing.JCheckBox();
      VrdQsfixMpeg2ps = new javax.swing.JCheckBox();
      TSDownload = new javax.swing.JCheckBox();
      TivoWebPlusDelete = new javax.swing.JCheckBox();
      iPadDelete = new javax.swing.JCheckBox();
      HideProtectedFiles = new javax.swing.JCheckBox();
      OverwriteFiles = new javax.swing.JCheckBox();
      DeleteFailedDownloads = new javax.swing.JCheckBox();
      java_downloads = new javax.swing.JCheckBox();
      combine_download_decrypt = new javax.swing.JCheckBox();
      single_download = new javax.swing.JCheckBox();
      rpcnpl = new javax.swing.JCheckBox();
      enableRpc = new javax.swing.JCheckBox();
      persistQueue = new javax.swing.JCheckBox();
      JLabel MAK_label = new javax.swing.JLabel();
      JLabel FontSize_label = new javax.swing.JLabel();
      JLabel file_naming_label = new javax.swing.JLabel();
      JLabel tivo_output_dir_label = new javax.swing.JLabel();
      JLabel mpeg_output_dir_label = new javax.swing.JLabel();
      JLabel qsfixDir_label = new javax.swing.JLabel();
      JLabel mpeg_cut_dir_label = new javax.swing.JLabel();
      JLabel encode_output_dir_label = new javax.swing.JLabel();
      JLabel tivodecode_label = new javax.swing.JLabel();
      JLabel dsd_label = new javax.swing.JLabel();
      JLabel curl_label = new javax.swing.JLabel();
      JLabel ffmpeg_label = new javax.swing.JLabel();
      JLabel mediainfo_label = new javax.swing.JLabel();
      JLabel mencoder_label = new javax.swing.JLabel();
      JLabel mencoder_args_label = new javax.swing.JLabel();
      JLabel handbrake_label = new javax.swing.JLabel();
      JLabel comskip_label = new javax.swing.JLabel();
      JLabel comskip_ini_label = new javax.swing.JLabel();
      JLabel wan_http_port_label = new javax.swing.JLabel();
      JLabel wan_https_port_label = new javax.swing.JLabel();
      JLabel wan_ipad_port_label = new javax.swing.JLabel();
      JLabel limit_npl_fetches_label = new javax.swing.JLabel();
      JLabel active_job_limit_label = new javax.swing.JLabel();
      JLabel t2extract_label = new javax.swing.JLabel();
      //JLabel t2extract_args_label = new javax.swing.JLabel();
      JLabel ccextractor_label = new javax.swing.JLabel();
      JLabel AtomicParsley_label = new javax.swing.JLabel();
      JLabel projectx_label = new javax.swing.JLabel();
      JLabel customCommand_label = new javax.swing.JLabel();
      JLabel customFiles_label = new javax.swing.JLabel();
      JLabel cpu_cores_label = new javax.swing.JLabel();
      JLabel download_tries_label = new javax.swing.JLabel();
      JLabel download_retry_delay_label = new javax.swing.JLabel();
      JLabel download_delay_label = new javax.swing.JLabel();
      JLabel metadata_entries_label = new javax.swing.JLabel();
      JLabel httpserver_port_label = new javax.swing.JLabel();
      JLabel autoLogSizeMB_label = new javax.swing.JLabel();
      JLabel available_keywords_label = new javax.swing.JLabel();
      JLabel pyTivo_host_label = new javax.swing.JLabel();
      JLabel web_query_label = new javax.swing.JLabel();
      JLabel web_browser_label = new javax.swing.JLabel();
      JLabel tivo_username_label = new javax.swing.JLabel();
      JLabel tivo_password_label = new javax.swing.JLabel();
      JLabel pyTivo_config_label = new javax.swing.JLabel();
      JLabel pyTivo_tivo_label = new javax.swing.JLabel();
      JLabel MinChanDigits_label = new javax.swing.JLabel();
      JLabel pyTivo_files_label = new javax.swing.JLabel();
      JLabel metadata_files_label = new javax.swing.JLabel();
      JLabel lookAndFeel_label = new javax.swing.JLabel();
      MinChanDigits = new javax.swing.JComboBox();
      pyTivo_tivo = new javax.swing.JComboBox();
      pyTivo_files = new javax.swing.JComboBox();
      metadata_files = new javax.swing.JComboBox();
      lookAndFeel = new javax.swing.JComboBox();
      keywords = new javax.swing.JComboBox();
      customFiles = new javax.swing.JComboBox();
      autotune_tivoName = new javax.swing.JComboBox();
      check_space = new javax.swing.JCheckBox();
      JLabel disk_space_label = new javax.swing.JLabel();
      beacon = new javax.swing.JCheckBox();
      UseOldBeacon = new javax.swing.JCheckBox();
      npl_when_started = new javax.swing.JCheckBox();
      showHistoryInTable = new javax.swing.JCheckBox();
      download_time_estimate = new javax.swing.JCheckBox();
      toolTips = new javax.swing.JCheckBox();
      slingBox = new javax.swing.JCheckBox();
      tableColAutoSize = new javax.swing.JCheckBox();
      jobMonitorFullPaths = new javax.swing.JCheckBox();
      autotune_enabled = new javax.swing.JCheckBox();
      JLabel toolTipsTimeout_label = new javax.swing.JLabel();
      OK = new javax.swing.JButton();
      CANCEL = new javax.swing.JButton();
      autotune_test = new javax.swing.JButton();
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
      autotune_channel_interval_label.setText("Channel change interval (secs)");
      autotune_button_interval_label.setText("Button press interval (msecs)");
      autotune_chan1_label.setText("Channel number for tuner 1");
      autotune_chan2_label.setText("Channel number for tuner 2");
      autotune_tivoName_label.setText("TiVo to Autotune");
      files_path_label.setText("FILES Default Path"); 
      remove_tivo.setText("Remove .TiVo after file decrypt"); 
      remove_comcut.setText("Remove Ad Detect files after Ad Cut");
      remove_comcut_mpeg.setText("Remove .mpg file after Ad Cut");
      remove_mpeg.setText("Remove .mpg file after encode");
      QSFixBackupMpegFile.setText("For QS Fix of .mpg file backup original .mpg");
      download_check_length.setText("Check download duration");
      UseAdscan.setText("Use VideoRedo AdScan instead of comskip");
      VRD.setText("Enable VideoRedo");
      VrdReview.setText("Use VideoRedo GUI to review detected commercials");
      comskip_review.setText("Use comskip GUI to review detected commercials");
      VrdReview_noCuts.setText("Bring up VideoRedo GUI to make manual cuts");
      VrdQsFilter.setText("Enable VideoRedo QS Fix video dimension filter");
      VrdDecrypt.setText("Decrypt using VideoRedo instead of tivodecode");
      DsdDecrypt.setText("Decrypt using DirectShow Dump instead of tivodecode");
      httpserver_enable.setText("Enable kmttg web server");
      VrdEncode.setText("Show VideoRedo encoding profiles");
      VrdAllowMultiple.setText("Run all VideoRedo jobs in GUI mode");
      VrdCombineCutEncode.setText("Combine Ad Cut & Encode");
      VrdQsfixMpeg2ps.setText("Force QS Fix output to always be mpeg2 Program Stream");
      TSDownload.setText("Download TiVo files in Transport Stream format");
      TivoWebPlusDelete.setText("Enable TivoWebPlus Delete task");
      iPadDelete.setText("Enable iPad style delete task");
      HideProtectedFiles.setText("Do not show copy protected files in table");
      OverwriteFiles.setText("Overwrite existing files");
      DeleteFailedDownloads.setText("Delete failed downloads");
      java_downloads.setText("Use Java for downloads instead of curl");
      combine_download_decrypt.setText("Combine download and tivodecode decrypt");
      single_download.setText("Allow only 1 download at a time");
      rpcnpl.setText("Use RPC to get NPL when possible");
      enableRpc.setText("Enable iPad style communications with this TiVo");
      persistQueue.setText("Automatically restore job queue between sessions");
      MAK_label.setText("MAK"); 
      FontSize_label.setText("GUI Font Size");
      file_naming_label.setText("File Naming"); 
      tivo_output_dir_label.setText(".TiVo Output Dir"); 
      mpeg_output_dir_label.setText(".mpg Output Dir");
      qsfixDir_label.setText("QS Fix Output Dir");
      mpeg_cut_dir_label.setText(".mpg Cut Dir"); 
      encode_output_dir_label.setText("Encode Output Dir"); 
      tivodecode_label.setText("tivodecode"); 
      dsd_label.setText("dsd"); 
      curl_label.setText("curl"); 
      ffmpeg_label.setText("ffmpeg"); 
      mediainfo_label.setText("mediainfo cli"); 
      mencoder_label.setText("mencoder"); 
      mencoder_args_label.setText("mencoder Ad Cut extra args");
      handbrake_label.setText("handbrake"); 
      comskip_label.setText("comskip"); 
      comskip_ini_label.setText("comskip.ini"); 
      wan_http_port_label.setText("wan http port"); 
      wan_https_port_label.setText("wan https port");
      wan_ipad_port_label.setText("wan ipad port"); 
      limit_npl_fetches_label.setText("limit # of npl fetches");
      active_job_limit_label.setText("active job limit"); 
      t2extract_label.setText("ccextractor"); 
      //t2extract_args_label.setText("t2extract extra arguments");
      ccextractor_label.setText("ccextractor");
      AtomicParsley_label.setText("AtomicParsley");
      projectx_label.setText("ProjectX");
      customCommand_label.setText("custom command");
      check_space.setText("Check Available Disk Space");      
      available_keywords_label.setText("Available keywords:"); 
      cpu_cores_label.setText("encoding cpu cores");
      download_tries_label.setText("# download attempts");
      download_retry_delay_label.setText("seconds between download retry attempts");
      download_delay_label.setText("start delay in seconds for download tasks");
      metadata_entries_label.setText("extra metadata entries (comma separated)");
      httpserver_port_label.setText("kmttg web server port");
      autoLogSizeMB_label.setText("auto log file size limit (MB)");
      web_query_label.setText("web query base url (bindkey q)");
      web_browser_label.setText("web browser binary");
      tivo_username_label.setText("tivo.com username");
      tivo_password_label.setText("tivo.com password");
      pyTivo_host_label.setText("pyTivo host name");
      pyTivo_config_label.setText("pyTivo.conf file");
      pyTivo_tivo_label.setText("pyTivo push destination");
      pyTivo_files_label.setText("Files to push");
      metadata_files_label.setText("metadata files");
      lookAndFeel_label.setText("look and feel");
      MinChanDigits_label.setText("Min # Channel Digits");

      keywords.setModel(new javax.swing.DefaultComboBoxModel(
         new String[] { "[title]", "[mainTitle]", "[episodeTitle]", "[channelNum]",
            "[channel]", "[min]", "[hour]", "[wday]", "[mday]", "[month]",
            "[monthNum]", "[year]", "[movieYear]", "[originalAirDate]", "[season]", "[episode]", 
            "[EpisodeNumber]", "[SeriesEpNumber]", "[description]", "[tivoName]", "[startTime]", "[/]"
            }
         )
      );
      keywords.setSelectedItem(null);
      keywords.setName("keywords"); 
      keywords.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
             if (e.getStateChange() == ItemEvent.SELECTED) {
               keywordsCB();
            }
         }
      });

      autotune_tivoName.setModel(new javax.swing.DefaultComboBoxModel(config.getNplTivoNames()));
      autotune_tivoName.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
             if (e.getStateChange() == ItemEvent.SELECTED) {
               autotune_tivoNameCB();
            }
         }
      });
      
      MinChanDigits.setModel(new javax.swing.DefaultComboBoxModel(
         new Integer[] {1, 2, 3, 4}
      ));
      MinChanDigits.setName("MinChanDigits");
      
      pyTivo_tivo.setModel(new javax.swing.DefaultComboBoxModel(config.getNplTivoNames()));
      pyTivo_tivo.setName("pyTivo_tivo");
      
      pyTivo_files.setModel(new javax.swing.DefaultComboBoxModel(
         new String[] { "tivoFile", "mpegFile", "mpegFile_cut", "encodeFile", "last", "all" }
      ));
      pyTivo_files.setName("pyTivo_files");
      
      metadata_files.setModel(new javax.swing.DefaultComboBoxModel(
         new String[] { "tivoFile", "mpegFile", "mpegFile_cut", "encodeFile", "last", "all" }
      ));
      metadata_files.setName("metadata_files");
      
      lookAndFeel.setModel(new javax.swing.DefaultComboBoxModel(config.gui.getAvailableLooks()));
      lookAndFeel.setName("lookAndFeel");
      lookAndFeel.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
             if (e.getStateChange() == ItemEvent.SELECTED) {
               config.gui.setLookAndFeel((String)lookAndFeel.getSelectedItem()); 
            }
         }
      });

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
      UseOldBeacon.setText("Detect with TiVo Beacon instead of Bonjour");
      
      npl_when_started.setText("Start NPL jobs when starting kmttg GUI");
      
      showHistoryInTable.setText("Highlight processed shows in history file");
      
      download_time_estimate.setText("Show estimated time remaining for downloads");
      
      toolTips.setText("Display toolTips");
      toolTipsTimeout_label.setText("toolTip timeout (secs)");

      slingBox.setText("Show Slingbox capture tab");

      tableColAutoSize.setText("Auto size NPL column widths");
      
      jobMonitorFullPaths.setText("Show full paths in Job Monitor");
      
      autotune_enabled.setText("Tune to specified channels before a download");
      
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
      
      autotune_test.setText("TEST");
      //autotune_test.setBackground(Color.green);
      autotune_test.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            autotune_testCB();
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
      
      qsfixDir.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               if(e.getClickCount() == 2) {
                  Browser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                  int result = Browser.showDialog(qsfixDir, "Choose Directory");
                  if (result == JFileChooser.APPROVE_OPTION) {
                     qsfixDir.setText(Browser.getSelectedFile().getPath());
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
      
      dsd.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               if(e.getClickCount() == 2) {
                  Browser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                  int result = Browser.showDialog(dsd, "Choose File");
                  if (result == JFileChooser.APPROVE_OPTION) {
                     dsd.setText(Browser.getSelectedFile().getPath());
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
      
      mediainfo.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               if(e.getClickCount() == 2) {
                  Browser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                  int result = Browser.showDialog(mediainfo, "Choose File");
                  if (result == JFileChooser.APPROVE_OPTION) {
                     mediainfo.setText(Browser.getSelectedFile().getPath());
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
      
      projectx.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               if(e.getClickCount() == 2) {
                  Browser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                  int result = Browser.showDialog(projectx, "Choose File");
                  if (result == JFileChooser.APPROVE_OPTION) {
                     projectx.setText(Browser.getSelectedFile().getPath());
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
      // npl_when_started
      gy = 0;
      c.gridx = 1;
      c.gridy = gy;
      tivo_panel.add(npl_when_started, c);
      
      // Look for Tivos on network
      gy++;
      c.gridx = 1;
      c.gridy = gy;
      tivo_panel.add(beacon, c);
      
      // UseOldBeacon
      gy++;
      c.gridx = 1;
      c.gridy = gy;
      tivo_panel.add(UseOldBeacon, c);
      
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
      
      // vertical space via empty label
      JLabel bogus = new JLabel(" ");
      gy++;
      c.gridx = 1;
      c.gridy = gy;
      tivo_panel.add(bogus, c);
      
      // enableRpc
      gy++;
      c.gridx = 1;
      c.gridy = gy;
      tivo_panel.add(enableRpc, c);
      
      // limit_npl_fetches
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      tivo_panel.add(limit_npl_fetches_label, c);

      c.gridx = 1;
      c.gridy = gy;
      tivo_panel.add(limit_npl_fetches, c);
            
      // wan http port
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      tivo_panel.add(wan_http_port_label, c);

      c.gridx = 1;
      c.gridy = gy;
      tivo_panel.add(wan_http_port, c);
      
      // wan https port
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      tivo_panel.add(wan_https_port_label, c);

      c.gridx = 1;
      c.gridy = gy;
      tivo_panel.add(wan_https_port, c);
      
      // wan ipad port
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      tivo_panel.add(wan_ipad_port_label, c);

      c.gridx = 1;
      c.gridy = gy;
      tivo_panel.add(wan_ipad_port, c);
      
      // tivo.com username & password
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      tivo_panel.add(tivo_username_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      tivo_panel.add(tivo_username, c);

      gy++;
      c.gridx = 0;
      c.gridy = gy;
      tivo_panel.add(tivo_password_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      tivo_panel.add(tivo_password, c);

      // autotune panel
      JPanel autotune_panel = new JPanel(new GridBagLayout());
      
      gy=0;
      c.gridx = 0;
      c.gridy = gy;
      autotune_panel.add(autotune_tivoName_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      autotune_panel.add(autotune_tivoName, c);

      gy++;
      c.gridx = 1;
      c.gridy = gy;
      autotune_panel.add(autotune_enabled, c);
      
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      autotune_panel.add(autotune_chan1_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      autotune_panel.add(autotune_chan1, c);
      
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      autotune_panel.add(autotune_chan2_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      autotune_panel.add(autotune_chan2, c);
      
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      autotune_panel.add(autotune_channel_interval_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      autotune_panel.add(autotune_channel_interval, c);
      
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      autotune_panel.add(autotune_button_interval_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      autotune_panel.add(autotune_button_interval, c);
      
      gy++;
      c.gridx = 1;
      c.gridy = gy;
      autotune_panel.add(autotune_test, c);
      
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
      
      // QSFixBackupMpegFile
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      files_panel.add(QSFixBackupMpegFile, c);
      
      // download_check_length
      c.gridx = 1;
      c.gridy = gy;
      files_panel.add(download_check_length, c);
            
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
      
      // qsfixDir
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      files_panel.add(qsfixDir_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      files_panel.add(qsfixDir, c);
      
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
      
      // autoLogSizeMB
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      files_panel.add(autoLogSizeMB_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      files_panel.add(autoLogSizeMB, c);
      
      // OverwriteFiles
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      files_panel.add(OverwriteFiles, c);
      
      // DeleteFailedDownloads
      c.gridx = 1;
      c.gridy = gy;
      files_panel.add(DeleteFailedDownloads, c);

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
      
      // dsd
      if (config.OS.equals("windows")) {
         gy++;
         c.gridx = 0;
         c.gridy = gy;
         programs_panel.add(dsd_label, c);
   
         c.gridx = 1;
         c.gridy = gy;
         programs_panel.add(dsd, c);
      }
      
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
      
      // projectx
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      programs_panel.add(projectx_label, c);

      c.gridx = 1;
      c.gridy = gy;
      programs_panel.add(projectx, c);
      
      // mediainfo
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      programs_panel.add(mediainfo_label, c);

      c.gridx = 1;
      c.gridy = gy;
      programs_panel.add(mediainfo, c);
      
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
      
      // MAK
      gy=0;
      c.gridx = 0;
      c.gridy = gy;
      program_options_panel.add(MAK_label, c);

      c.gridx = 1;
      c.gridy = gy;
      program_options_panel.add(MAK, c);
            
      // active job limit
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      program_options_panel.add(active_job_limit_label, c);

      c.gridx = 1;
      c.gridy = gy;
      program_options_panel.add(active_job_limit, c);
      
      // cpu_cores
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      program_options_panel.add(cpu_cores_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      program_options_panel.add(cpu_cores, c);
      
      /*// t2extract_args
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      program_options_panel.add(t2extract_args_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      program_options_panel.add(t2extract_args, c);*/
      
      // mencoder_args
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      program_options_panel.add(mencoder_args_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      program_options_panel.add(mencoder_args, c);
      
      // download_tries
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      program_options_panel.add(download_tries_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      program_options_panel.add(download_tries, c);
      
      // download_retry_delay
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      program_options_panel.add(download_retry_delay_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      program_options_panel.add(download_retry_delay, c);
      
      // download_delay
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      program_options_panel.add(download_delay_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      program_options_panel.add(download_delay, c);
      
      // metadata_files
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      program_options_panel.add(metadata_files_label, c);

      c.gridx = 1;
      c.gridy = gy;
      program_options_panel.add(metadata_files, c);
      
      // metadata_entries
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      program_options_panel.add(metadata_entries_label, c);

      c.gridx = 1;
      c.gridy = gy;
      program_options_panel.add(metadata_entries, c);
      
      // httpserver_port
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      program_options_panel.add(httpserver_port_label, c);

      c.gridx = 1;
      c.gridy = gy;
      program_options_panel.add(httpserver_port, c);
      
      // TivoWebPlusDelete
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      program_options_panel.add(TivoWebPlusDelete, c);
      
      // iPadDelete
      c.gridx = 1;
      c.gridy = gy;
      program_options_panel.add(iPadDelete, c);
      
      // TSDownload
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      program_options_panel.add(TSDownload, c);
      
      // java_downloads
      c.gridx = 1;
      c.gridy = gy;
      program_options_panel.add(java_downloads, c);
      
      // download_time_estimate
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      program_options_panel.add(download_time_estimate, c);
      
      // combine_download_decrypt
      c.gridx = 1;
      c.gridy = gy;
      program_options_panel.add(combine_download_decrypt, c);
      
      // single_download
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      program_options_panel.add(single_download, c);
      
      // rpcnpl
      c.gridx = 1;
      c.gridy = gy;
      program_options_panel.add(rpcnpl, c);
      
      // persistJobQueue
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      program_options_panel.add(persistQueue, c);
      
      // comskip_review
      c.gridx = 1;
      c.gridy = gy;
      program_options_panel.add(comskip_review, c);
      
      if (config.OS.equals("windows")) {
         // DsdDecrypt
         gy++;
         c.gridx = 0;
         c.gridy = gy;
         program_options_panel.add(DsdDecrypt, c);
      }
      
      // httpserver_enable
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      program_options_panel.add(httpserver_enable, c);
      
      // Visual Panel
      JPanel visual_panel = new JPanel(new GridBagLayout());       
      
      // lookAndFeel
      gy=0;
      c.gridx = 0;
      c.gridy = gy;
      visual_panel.add(lookAndFeel_label, c);

      c.gridx = 1;
      c.gridy = gy;
      visual_panel.add(lookAndFeel, c);
      
      // FontSize
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      visual_panel.add(FontSize_label, c);

      c.gridx = 1;
      c.gridy = gy;
      visual_panel.add(FontSize, c);

      // toolTipsTimeout
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      visual_panel.add(toolTipsTimeout_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      visual_panel.add(toolTipsTimeout, c);
      
      // MinChanDigits
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      visual_panel.add(MinChanDigits_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      visual_panel.add(MinChanDigits, c);
      
      // toolTips
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      visual_panel.add(toolTips, c);
      
      // jobMonitorFullPaths
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      visual_panel.add(jobMonitorFullPaths, c);

      // HideProtectedFiles
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      visual_panel.add(HideProtectedFiles, c);
      
      // tableColAutoSize
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      visual_panel.add(tableColAutoSize, c);      
      
      // showHistoryInTable
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      visual_panel.add(showHistoryInTable, c);      
      
      // slingBox
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      visual_panel.add(slingBox, c);
      
      // web_query
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      visual_panel.add(web_query_label, c);
      
      c.gridx = 1;
      c.gridy = gy;
      visual_panel.add(web_query, c);
      
      // web_browser - not used for Mac or Windows
      if ( config.OS.equals("other")) {
         gy++;
         c.gridx = 0;
         c.gridy = gy;
         visual_panel.add(web_browser_label, c);
         
         c.gridx = 1;
         c.gridy = gy;
         visual_panel.add(web_browser, c);
      }
      
      // VRD Panel
      JPanel vrd_panel = new JPanel(new GridBagLayout());       
      
      // VRD flag
      gy=0;
      c.gridx = 1;
      c.gridy = gy;
      vrd_panel.add(VRD, c);

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
      
      // VrdEncode
      gy++;
      c.gridx = 1;
      c.gridy = gy;
      vrd_panel.add(VrdEncode, c);
      
      // VrdCombineCutEncode
      gy++;
      c.gridx = 1;
      c.gridy = gy;
      vrd_panel.add(VrdCombineCutEncode, c);
      
      // VrdQsfixMpeg2ps
      gy++;
      c.gridx = 1;
      c.gridy = gy;
      vrd_panel.add(VrdQsfixMpeg2ps, c);
      
      // VrdAllowMultiple
      gy++;
      c.gridx = 1;
      c.gridy = gy;
      vrd_panel.add(VrdAllowMultiple, c);
      
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
      tabbed_panel = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
      tabbed_panel.add("File Settings", files_panel);
      tabbed_panel.add("Programs", programs_panel);
      tabbed_panel.add("Program Options", program_options_panel);
      tabbed_panel.add("Tivos", tivo_panel);
      tabbed_panel.add("Visual", visual_panel);
      if (config.OS.equals("windows"))
         tabbed_panel.add("VideoRedo", vrd_panel);
      tabbed_panel.add("pyTivo", pyTivo_panel);
      tabbed_panel.add("Autotune", autotune_panel);
      
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
      autotune_enabled.setToolTipText(getToolTip("autotune_enabled"));
      autotune_channel_interval.setToolTipText(getToolTip("autotune_channel_interval"));
      autotune_button_interval.setToolTipText(getToolTip("autotune_button_interval"));
      autotune_chan1.setToolTipText(getToolTip("autotune_chan1"));
      autotune_chan2.setToolTipText(getToolTip("autotune_chan2"));
      autotune_tivoName.setToolTipText(getToolTip("autotune_tivoName"));
      add.setToolTipText(getToolTip("add")); 
      del.setToolTipText(getToolTip("del")); 
      remove_tivo.setToolTipText(getToolTip("remove_tivo"));
      remove_comcut.setToolTipText(getToolTip("remove_comcut"));
      remove_comcut_mpeg.setToolTipText(getToolTip("remove_comcut_mpeg"));
      remove_mpeg.setToolTipText(getToolTip("remove_mpeg"));
      QSFixBackupMpegFile.setToolTipText(getToolTip("QSFixBackupMpegFile"));
      download_check_length.setToolTipText(getToolTip("download_check_length"));
      check_space.setToolTipText(getToolTip("check_space"));
      beacon.setToolTipText(getToolTip("beacon"));
      UseOldBeacon.setToolTipText(getToolTip("UseOldBeacon"));
      npl_when_started.setToolTipText(getToolTip("npl_when_started"));
      showHistoryInTable.setToolTipText(getToolTip("showHistoryInTable"));
      download_time_estimate.setToolTipText(getToolTip("download_time_estimate"));
      UseAdscan.setToolTipText(getToolTip("UseAdscan"));
      VRD.setToolTipText(getToolTip("VRD"));
      VrdReview.setToolTipText(getToolTip("VrdReview"));
      comskip_review.setToolTipText(getToolTip("comskip_review"));
      VrdReview_noCuts.setToolTipText(getToolTip("VrdReview_noCuts"));
      VrdQsFilter.setToolTipText(getToolTip("VrdQsFilter"));
      VrdDecrypt.setToolTipText(getToolTip("VrdDecrypt"));
      DsdDecrypt.setToolTipText(getToolTip("DsdDecrypt"));
      httpserver_enable.setToolTipText(getToolTip("httpserver_enable"));
      VrdEncode.setToolTipText(getToolTip("VrdEncode"));
      VrdAllowMultiple.setToolTipText(getToolTip("VrdAllowMultiple"));
      VrdCombineCutEncode.setToolTipText(getToolTip("VrdCombineCutEncode"));
      VrdQsfixMpeg2ps.setToolTipText(getToolTip("VrdQsfixMpeg2ps"));
      TSDownload.setToolTipText(getToolTip("TSDownload"));
      TivoWebPlusDelete.setToolTipText(getToolTip("TivoWebPlusDelete"));
      iPadDelete.setToolTipText(getToolTip("iPadDelete"));
      HideProtectedFiles.setToolTipText(getToolTip("HideProtectedFiles"));
      OverwriteFiles.setToolTipText(getToolTip("OverwriteFiles"));
      DeleteFailedDownloads.setToolTipText(getToolTip("DeleteFailedDownloads"));
      java_downloads.setToolTipText(getToolTip("java_downloads"));
      combine_download_decrypt.setToolTipText(getToolTip("combine_download_decrypt"));
      single_download.setToolTipText(getToolTip("single_download"));
      rpcnpl.setToolTipText(getToolTip("rpcnpl"));
      enableRpc.setToolTipText(getToolTip("enableRpc"));
      persistQueue.setToolTipText(getToolTip("persistQueue"));
      files_path.setToolTipText(getToolTip("files_path"));
      MAK.setToolTipText(getToolTip("MAK"));
      FontSize.setToolTipText(getToolTip("FontSize"));
      file_naming.setToolTipText(getToolTip("file_naming"));
      tivo_output_dir.setToolTipText(getToolTip("tivo_output_dir"));
      mpeg_output_dir.setToolTipText(getToolTip("mpeg_output_dir"));
      qsfixDir.setToolTipText(getToolTip("qsfixDir"));
      mpeg_cut_dir.setToolTipText(getToolTip("mpeg_cut_dir"));
      encode_output_dir.setToolTipText(getToolTip("encode_output_dir"));
      tivodecode.setToolTipText(getToolTip("tivodecode"));
      dsd.setToolTipText(getToolTip("dsd"));
      curl.setToolTipText(getToolTip("curl"));
      ffmpeg.setToolTipText(getToolTip("ffmpeg"));
      mediainfo.setToolTipText(getToolTip("mediainfo"));
      mencoder.setToolTipText(getToolTip("mencoder"));
      mencoder_args.setToolTipText(getToolTip("mencoder_args"));
      handbrake.setToolTipText(getToolTip("handbrake"));
      comskip.setToolTipText(getToolTip("comskip"));
      comskip_ini.setToolTipText(getToolTip("comskip_ini"));
      t2extract.setToolTipText(getToolTip("t2extract"));
      //t2extract_args.setToolTipText(getToolTip("t2extract_args"));
      ccextractor.setToolTipText(getToolTip("ccextractor"));
      AtomicParsley.setToolTipText(getToolTip("AtomicParsley"));
      projectx.setToolTipText(getToolTip("projectx"));
      wan_http_port.setToolTipText(getToolTip("wan_http_port"));
      wan_https_port.setToolTipText(getToolTip("wan_https_port"));
      wan_ipad_port.setToolTipText(getToolTip("wan_ipad_port"));
      limit_npl_fetches.setToolTipText(getToolTip("limit_npl_fetches"));
      active_job_limit.setToolTipText(getToolTip("active_job_limit"));
      disk_space.setToolTipText(getToolTip("disk_space"));
      customCommand.setToolTipText(getToolTip("customCommand"));
      keywords.setToolTipText(getToolTip("keywords"));
      customFiles.setToolTipText(getToolTip("customFiles")); 
      OK.setToolTipText(getToolTip("OK")); 
      CANCEL.setToolTipText(getToolTip("CANCEL"));
      autotune_test.setToolTipText(getToolTip("autotune_test"));
      toolTips.setToolTipText(getToolTip("toolTips"));
      slingBox.setToolTipText(getToolTip("slingBox"));
      tableColAutoSize.setToolTipText(getToolTip("tableColAutoSize"));
      jobMonitorFullPaths.setToolTipText(getToolTip("jobMonitorFullPaths"));
      toolTipsTimeout.setToolTipText(getToolTip("toolTipsTimeout")); 
      cpu_cores.setToolTipText(getToolTip("cpu_cores"));
      download_tries.setToolTipText(getToolTip("download_tries"));
      download_retry_delay.setToolTipText(getToolTip("download_retry_delay"));
      download_delay.setToolTipText(getToolTip("download_delay"));
      metadata_entries.setToolTipText(getToolTip("metadata_entries"));
      httpserver_port.setToolTipText(getToolTip("httpserver_port"));
      autoLogSizeMB.setToolTipText(getToolTip("autoLogSizeMB"));
      web_query.setToolTipText(getToolTip("web_query"));
      web_browser.setToolTipText(getToolTip("web_browser"));
      tivo_username.setToolTipText(getToolTip("tivo_username"));
      tivo_password.setToolTipText(getToolTip("tivo_password"));
      pyTivo_host.setToolTipText(getToolTip("pyTivo_host"));
      pyTivo_config.setToolTipText(getToolTip("pyTivo_config"));
      pyTivo_tivo.setToolTipText(getToolTip("pyTivo_tivo"));
      pyTivo_files.setToolTipText(getToolTip("pyTivo_files"));
      metadata_files.setToolTipText(getToolTip("metadata_files"));
      lookAndFeel.setToolTipText(getToolTip("lookAndFeel"));
      MinChanDigits.setToolTipText(getToolTip("MinChanDigits"));
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
      else if (component.equals("autotune_enabled")) {
         text =  "<b>Tune to specified channels before a download</b><br>";
         text += "For Series 3 & 4 TiVos that have <b>Network Remote Control</b> option enabled<br>";
         text += "you can have kmttg tune to silent channels or channels you don't receive before<br>";
         text += "initiating a download from a TiVo. This helps speed up transfer rates by removing CPU load<br>";
         text += "from the TiVo. In order for this to work you must enabled Network Remote Control feature:<br>";
         text += "<b>Tivo Central-Messages&Settings-Settings-Remote,CableCARD&Devices: Network Remote Control</b><br>";
         text += "kmttg uses LIVETV,CLEAR,CHANNEL #,ENTER network button press sequence to tune to a channel.";
      }
      else if (component.equals("autotune_channel_interval")) {
         text =  "<b>Channel change interval (secs)</b><br>";
         text += "Specifies interval of time in seconds to wait after tuning first tuner before attempting<br>";
         text += "to tune second tuner. Depending on how responsive your TiVo is this may have to be tweeked for<br>";
         text += "both channel changes to work.";
      }
      else if (component.equals("autotune_button_interval")) {
         text =  "<b>Button press interval (msecs)</b><br>";
         text += "Specifies interval of time in milliseconds to wait between network button press commands that<br>";
         text += "are sent to the TiVo for tuning. Depending on how responsive your TiVo is this may have to be<br>";
         text += "tweeked for network based channel tuning to work.";
      }
      else if (component.equals("autotune_chan1")) {
         text =  "<b>Channel number for tuner 1</b><br>";
         text += "Channel number to use for first tuner. Typically you want to set this to a music channel or<br>";
         text += "channel that you don't subscribe to so that it relieves the load on your TiVo CPU.<br>";
         text += "Both conventional integer only channel numbers and OTA style x.y or x-y are supported.";
      }
      else if (component.equals("autotune_chan2")) {
         text =  "<b>Channel number for tuner 2</b><br>";
         text += "Channel number to use for second tuner. Typically you want to set this to a music channel or<br>";
         text += "channel that you don't subscribe to so that it relieves the load on your TiVo CPU.";
         text += "Both conventional integer only channel numbers and OTA style x.y or x-y are supported.";
      }
      else if (component.equals("autotune_test")) {
         text =  "<b>TEST</b><br>";
         text += "Test channel changing for currently selected TiVo based on current form settings.";
      }
      else if (component.equals("autotune_tivoName")) {
         text =  "<b>TiVo to Autotune</b><br>";
         text += "Select which TiVo you would like to configure for/test.<br>";
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
      else if (component.equals("QSFixBackupMpegFile")) {
         text =  "<b>For QS Fix of .mpg file backup original .mpg</b><br>";
         text += "If running VRD QS Fix on a .mpg file kmttg will rename the original .mpg file to .mpg.bak<br>";
         text += "if this option is enabled. Otherwise kmttg removes the original .mpg file and replaces with<br>";
         text += "the fixed version following successful VideoRedo QS Fix run";
      }
      else if (component.equals("download_check_length")) {
         text =  "<b>Check download duration</b><br>";
         text += "When enabled use mediainfo CLI to determine the duration of downloaded .TiVo files<br>";
         text += "and compare against expected duration within a certain tolerance.<br>";
         text += "If a download is not within tolerance then it will be considered an error.<br>";
         text += "NOTE: This option relies on mediainfo CLI being configured and being able to<br>";
         text += "determine duration of .TiVo files properly, and also relies on TiVo duration<br>";
         text += "reported by TiVo to be accurate, so may not be 100% reliable.";
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
      else if (component.equals("UseOldBeacon")) {
         text =  "<b>Detect with TiVo Beacon instead of Bonjour</b><br>";
         text += "Use the old TiVo Beacon method for detecting TiVos on the network instead of the<br>";
         text += "newer Bonjour method. You can try this method if Bonjour is not working for you.";
      }
      else if (component.equals("npl_when_started")) {
         text =  "<b>Start NPL jobs when starting kmttg GUI</b><br>";
         text += "If this option is enabled then kmttg will start NPL jobs for configured TiVos<br>";
         text += "right away when starting kmttg GUI. Otherwise no NPL jobs are started and you<br>";
         text += "can manually select TiVos and click on <b>Refresh</b> button to selectively start<br>";
         text += "NPL jobs.";
      }
      else if (component.equals("showHistoryInTable")) {
         text =  "<b>Highlight processed shows in history file</b><br>";
         text += "If this option is enabled then kmttg will highlight shows that have been previously<br>";
         text += "processed by kmttg and have an entry in the <b>auto.history</b> file.<br>";
         text += "Useful as an easier check to see if a show has been processed before or not.<br>";
         text += "NOTE: This option affects NPL and Remote ToDo table entries only.<br>";
         text += "NOTE: If you clear out auto.history file regularly then obviously this option will<br>";
         text += "not be very useful.";
      }
      else if (component.equals("download_time_estimate")) {
         text =  "<b>Show estimated time remaining for downloads</b><br>";
         text += "If this option is enabled then download tasks will show estimated time remaining<br>";
         text += "instead of download bit rate in the status column.<br>";
         text += "NOTE: Since file size reported by TiVo is not accurate this number is also not accurate<br>";
         text += "and will never reach 0";
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
      else if (component.equals("comskip_review")) {
         text =  "<b>Use comskip GUI to review detected commercials</b><br>";
         text += "If you have comskip configured in kmttg and <b>Ad Detect</b> task enabled,<br>";
         text += "when this option is enabled kmttg will start comskip GUI<br>";
         text += "to allow you to manually review and update the detected commercial segments<br>";
         text += "before starting the commercial cutting job. kmttg will wait until you close<br>";
         text += "the comskip GUI before proceeding.<br>";
         text += "NOTE: Press <b>F1</b> to see list of keyboard shortcuts for comskip GUI which<br>";
         text += "shows you how to set start/end of a commercial segment or insert a new one, etc.<br>";
         text += "Make sure you save your changes by pressing <b>w</b> keyboard button before you exit.<br>";
         text += "<b>IMPORTANT: When done press <b>Esc</b> keyboard button to close comskip GUI.<br>";
         text += "Just closing the window by clicking on the X will NOT terminate comskip</b>.";
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
         text += "Quickstream Fix run that will solve that problem. Note that kmttg uses mediainfo if<br>";
         text += "available, else ffmpeg to automatically detect the mpeg video file dimensions to be used<br>";
         text += "as the filter and prepares a custom version of VRD vp.vbs file with an added filter line.";
      }
      else if (component.equals("VrdDecrypt")) {
         text =  "<b>Decrypt using VideoRedo instead of tivodecode</b><br>";
         text += "If you have VideoRedo and have configured kmttg with the installation path<br>";
         text += "to VideoRedo, when this option is enabled kmttg will use VideoRedo QSFix task<br>";
         text += "to decrypt .TiVo files instead of the standard <b>tivodecode</b> program. This is<br>";
         text += "useful for cases when the .TiVo files are in a format that tivodecode cannot decrypt<br>";
         text += "such as for Transport Stream format .TiVo files.<br>";
         text += "NOTE: You must have TiVo Desktop (or at least TiVoDirectShowFilter.dll) installed for this to work.";
      }
      else if (component.equals("DsdDecrypt")) {
         text =  "<b>Decrypt using DirectShow Dump instead of tivodecode</b><br>";
         text += "If you have at least a partial TiVo Desktop installation with<br>";
         text += "<b>TiVoDirectShowFilter.dll</b> installed then you can enable this option to decrypt<br>";
         text += ".TiVo files instead of the standard <b>tivodecode</b> program. This is<br>";
         text += "useful for cases when the .TiVo files are in a format that tivodecode cannot decrypt<br>";
         text += "such as for Transport Stream (TS) format .TiVo files.<br>";
         text += "NOTE: You must have TiVo Desktop (or at least TiVoDirectShowFilter.dll) installed for this to work.<br>";
         text += "NOTE: DirectShow Dump cannot be combined with download task, so you should disable kmttg<br>";
         text += "config option <b>Combine downlad and tivodecode decrypt</b> if enabled in order to use this option.";
      }
      else if (component.equals("httpserver_enable")) {
         text =  "<b>Enable kmttg web server</b><br>";
         text += "<b>EXPERIMENTAL</b><br>";
         text += "Enabling web browser allows you to interact with kmttg via any web browser with<br>";
         text += "a subset of kmttg capabilities and capability to stream videos from computer<br>";
         text += "running kmttg to any browser. This is experimental because some functions are<br>";
         text += "still a work in progress and not robust, especially video streaming.<br>";
         text += "NOTE: TiVos listed in web browser are intentionally restricted to series 4 or later only.";
      }
      else if (component.equals("httpserver_port")) {
         text =  "<b>kmttg web server port</b><br>";
         text += "Port to use for kmttg web server. See web server setting and associated tooltip below.";
      }
      else if (component.equals("VrdEncode")) {
         text =  "<b>Show VideoRedo encoding profiles</b><br>";
         text += "If you have VideoRedo and have configured kmttg with the installation path<br>";
         text += "to VideoRedo, when this option is enabled kmttg will add VideoRedo encoding<br>";
         text += "profiles to list of available encoding profiles to use for <b>encode</b> task.<br>";
         text += "NOTE: You must have VideoRedo with H.264 support (TVSuite4 or later) in order<br>";
         text += "to use this option. kmttg will scan all your VideoRedo TVS4 output profiles<br>";
         text += "to display as encoding and/or remuxing choices.";
      }
      else if (component.equals("VrdAllowMultiple")) {
         text =  "<b>Run all VideoRedo jobs in GUI mode</b><br>";
         text += "If this option is enabled then kmttg will launch all VideoRedo tasks in GUI mode<br>";
         text += "instead of silent/background mode. Depending on your flow this may be necessary<br>";
         text += "because silent mode runs interfere with any GUI versions of VideoRedo you may be<br>";
         text += "running. If you're going to be actively using VideoRedo GUI while kmttg is running<br>";
         text += "then you will need to turn this option on.";
      }
      else if (component.equals("VrdCombineCutEncode")) {
         text =  "<b>Combine Ad Cut & Encode</b><br>";
         text += "If this option is enabled then for the <b>Ad Cut</b> task, kmttg will use VideoRedo<br>";
         text += "to cut commercials and encode to selected VideoRedo encoding profile in a single step<br>";
         text += "instead of cutting commercials and outputting mpeg2 _cut.mpg file as is the normal flow.<br>";
         text += "NOTE: You must set <b>Encoding Profile</b> in GUI to a VideoRedo encoding profile or<br>";
         text += "else kmttg will generate an error message and the Ad Cut task won't be scheduled.<br>";
         text += "NOTE: You should also enable <b>Show VideoRedo encoding profles</b> option if you<br>";
         text += "enable this option.";
      }
      else if (component.equals("VrdQsfixMpeg2ps")) {
         text =  "<b>Force QS Fix output to always be mpeg2 Program Stream</b><br>";
         text += "If this option is enabled then for the <b>QS Fix</b> task, kmttg will force VideoRedo<br>";
         text += "to output mpeg2 program stream format regardless of the input video format.<br>";
         text += "Thus for example if the input file is Mpeg2 Transport Stream format then the output<br>";
         text += "will be Mpeg2 Program Stream.";
      }
      else if (component.equals("TSDownload")) {
         text =  "<b>Download TiVo files in Transport Stream format</b><br>";
         text += "For TiVo software that properly supports it, this forces TiVo file downloads to use<br>";
         text += "the faster Transport Stream format instead of the default Program Stream format by adding<br>";
         text += "<b>&Format=video/x-tivo-mpeg-ts</b> tag to the download URL.<br>";
         text += "NOTE: Currently only Series 4+, Australia, and New Zealand TiVos support this format and this will<br>";
         text += "have no effect on other TiVos.<br>";
         text += "<b>NOTE: 'tivodecode' cannot properly decrypt TS TiVo files so don't enable this option<br>";
         text += "if you are using it to decrypt TiVo files.</b>";
      }
      else if (component.equals("TivoWebPlusDelete")) {
         text =  "<b>Enable TivoWebPlus Delete task</b><br>";
         text += "If you have TivoWebPlus configured on your TiVo(s) then if you enable this option<br>";
         text += "an optional <b>TWP Delete</b> task is made available in the kmttg GUI or auto transfers<br>";
         text += "task set. When task is enabled, a TivoWebPlus http call to delete show on TiVo will be<br>";
         text += "issued following successful decrypt of a downloaded .TiVo file.<br>";
         text += "NOTE: Once you set and save this option you must restart kmttg to see the change.";

      }
      else if (component.equals("iPadDelete")) {
         text =  "<b>Enable iPad style delete task</b><br>";
         text += "For Series 4 TiVos if you have <b>Network Remote</b> option enabled and this option<br>";
         text += "enabled an optional <b>iPad Delete</b> task is made available in the kmttg GUI or auto transfers<br>";
         text += "task set. When task is enabled, iPad communications protocol is used to delete show on TiVo<br>";
         text += "following a successful decrypt of a downloaded .TiVo file.<br>";
         text += "<b>NOTE: Once you set and save this option you must restart kmttg to see the change.</b>";

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
      else if (component.equals("DeleteFailedDownloads")) {
         text =  "<b>Delete failed downloads</b><br>";
         text += "With this option disabled kmttg will not delete a failed file download attempt.<br>";
         text += "This only useful disabled if you want to keep a partial download and you also have<br>";
         text += "<b>Overwrite existing files</b> option disabled or <b># download attempts</b> option<br>";
         text += "set to 0 or 1. By disabling this option note that you can end up with partial downloads<br>";
         text += "so most users will probably want to keep this option enabled.";
      }
      else if (component.equals("java_downloads")) {
         text =  "<b>Use Java for downloads instead of curl</b><br>";
         text += "If this option is enabled then kmttg will use native Java methods for retrieving Now Playing<br>";
         text += "lists and downloading shows from TiVos instead of using <b>curl</b>.";
      }
      else if (component.equals("combine_download_decrypt")) {
         text =  "<b>Combine download and tivodecode decrypt</b><br>";
         text += "If this option is enabled then kmttg will try to combine the download and decrypt tasks into<br>";
         text += "a single step instead of the default 2 step process (skipping intermediate TiVo file generation).<br>";
         text += "NOTE: You still need to enable both <b>download</b> and <b>decrypt</b> tasks for a show for this<br>";
         text += "to apply - if you do not enable <b>decrypt</b> task then still only download to TiVo file is performed.<br>";
         text += "NOTE: This option only applies if using <b>tivodecode</b> to decrypt, not for<br>";
         text += "VideoRedo qsfix or DirectShow dump decrypt which must be performed separately from downloads.";
      }
      else if (component.equals("single_download")) {
         text =  "<b>Allow only 1 download at a time</b><br>";
         text += "If this option is enabled then kmttg will only download 1 program at a time no matter how many<br>";
         text += "TiVos you have and how many download tasks are queued up. The usual restriction is only 1 download<br>";
         text += "at a time per TiVo, which means you can still have simultaneous downloads for different TiVos. This<br>";
         text += "option restricts that further to only 1 at a time for all TiVos.";
      }
      else if (component.equals("rpcnpl")) {
         text =  "<b>Use RPC to get NPL when possible</b><br>";
         text += "If this option is enabled then kmttg will use RPC for obtaining NPL listings whenever possible<br>";
         text += "for series 4 or later TiVos only. This avoids the traditional 2 step process of obtaining XML<br>";
         text += "listings followed by <b>remote</b> call when refreshing NPL tables.<br>";
         text += "NOTE: RPC data does not contain <b>SeriesId</b>, so if having that field in pyTivo metadata file<br>";
         text += "is important to you then you should not enable this option.<br>";
         text += "NOTE: Enabling this option will prevent <b>Resume Downloads</b> functionality from working since<br>";
         text += "<b>ByteOffset</b> is not available via RPC data.";
        }
      else if (component.equals("enableRpc")) {
         text =  "<b>Enable iPad style communications with this TiVo</b><br>";
         text += "If this option is enabled then kmttg will use iPad style communications with the TiVo to enable<br>";
         text += "extra functionality such as capability to play & delete shows from Now Playing list and also to<br>";
         text += "allow viewing of To Do list, Season Pass list and direct remote control capabilities.<br>";
         text += "If enabled then you can play/delete shows from Now Playing List table as follows:<br>";
         text += "<b>PLAY:</b> Select a show in Now Playing List and press <b>space bar</b> key.<br>";
         text += "<b>DELETE:</b> Select a show in Now Playing List and press <b>delete</b> key.<br>";
         text += "<b>If enabled, you will see an additional 'Remote' tab</b>.<br>";
         text += "<b>NOTE: This only works with Series 4 (Premiere) TiVos or later.</b>";
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
      else if (component.equals("qsfixDir")) {
         text =  "<b>QS Fix Output Dir</b><br>";
         text += "<b>REQUIRED</b> if you plan to run qsfix task.<br>";
         text += "This defines location where qsfix output files will be saved to.<br>";
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
      else if (component.equals("dsd")) {
         text =  "<b>dsd</b><br>";
         text += "This defines the full path to the <b>DirectShow Dump</b> program.<br>";
         text += "For Windows systems this can be used instead of tivodecode to decrypt<br>";
         text += ".TiVo files in either mpeg2 program stream or transport stream containers.<br>";
         text += "<b>NOTE: This requires you have at least a partial install of TiVo Desktop as well with<br>";
         text += "TiVoDirectShowFilter.dll installed</b><br>";
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
      else if (component.equals("mediainfo")) {
         text =  "<b>mediainfo cli</b><br>";
         text += "This defines the full path to the <b>mediainfo cli</b> program.<br>";
         text += "When available kmttg will use this program to determine information on videos<br>";
         text += "such as container, video codec, audio codec, video resolution, etc.<br>";
         text += "which is needed for some kmttg operations. If this program is not available<br>";
         text += "to kmttg then ffmpeg will be used instead.<br>";
         text += "<b>NOTE: This binary should be the Command Line Interface (CLI) version of<br>";
         text += "mediainfo, not the graphical (GUI) version.</b>";
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
      else if (component.equals("mencoder_args")) {
         text =  "<b>mencoder Ad Cut extra args</b><br>";
         text += "Any extra arguments you want kmttg to use when running <b>mencoder</b> for the<br>";
         text += "<b>Ad Cut</b> task. NOTE: If you have VideoRedo configured then VideoRedo is used<br>";
         text += "for Ad Cut so this setting will not be relevant. The default mencoder arguments for the<br>";
         text += "Ad Cut task are as follows:<br>";
         text += "<b>mencoder videoFile -edl edlFile -oac copy -ovc copy -of mpeg -vf harddup -o mpegFile_cut</b><br>";
         text += "Extra arguments you supply are added following the <b>-vf harddup</b> option.";
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
         text =  "<b>ccextractor</b><br>";
         text += "<b>REQUIRED</b> if you plan to use <b>captions</b> task.<br>";
         text += "This program is used for generating closed captions <b>.srt</b> files.<br>";
         text += "This is the full path to <b>ccextractor</b> program which is available<br>";
         text += "from http://ccextractor.sourceforge.net.<br>";
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
      else if (component.equals("VRD")) {
         text =  "<b>Enable VideoRedo</b><br>";
         text += "For Windows systems only if you have VideoRedo program installed on this computer<br>";
         text += "then you can turn on this option to enable VideoRedo functionality in kmttg.<br>";
         text += "This setting is <b>REQUIRED</b> to enable <b>VRD QS fix</b> task which runs VideoRedo<br>";
         text += "to automatically repair glitches/problems in mpeg2 program files.<br>";
         text += "This setting also REQUIRED if you want to use VideoRedo for commercial cutting (<b>comcut</b>) step,<br>";
         text += "and for a bunch of other tasks that can make use of VideoRedo.";
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
      else if (component.equals("projectx")) {
         text =  "<b>ProjectX</b><br>";
         text += "This defines the full path to the <b>ProjectX.jar</b> java program.<br>";
         text += "If defined and you don't have VideoRedo configured for use with kmttg then<br>";
         text += "this program is used for <b>QS Fix</b> task to <b>demux</b> an mpeg2 video file and<br>";
         text += "clean up errors in the video and audio streams. Following ProjectX demux step<br>";
         text += "then ffmpeg is used to <b>remux</b> the video and audio streams back together again.<br>";
         text += "NOTE: If you have VideoRedo configured that it will be used instead for QS Fix tasks.<br>";
         text += "<b>NOTE: Double-click mouse in this field to bring up File Browser</b>.";
      }
      else if (component.equals("wan_http_port")) {
         text =  "<b>wan http port</b><br>";
         text += "<b>Advanced Setting - for normal use leave this setting empty</b>.<br>";
         text += "Set this option only if you plan to use kmttg over a WAN instead of your local LAN.<br>";
         text += "By default http port 80 is used to download shows from the Tivos on the LAN, but from WAN side<br>";
         text += "you will have to setup port forwarding in your router, then you should specify here the WAN (public) side<br>";
         text += "port number you are using in your router port forwarding settings.<br>";
         text += "NOTE: In order to save this setting you must OK the configuration window once for each TiVo";
      }
      else if (component.equals("wan_https_port")) {
         text =  "<b>wan https port</b><br>";
         text += "<b>Advanced Setting - for normal use leave this setting empty</b>.<br>";
         text += "Set this option only if you plan to use kmttg over a WAN instead of your local LAN.<br>";
         text += "By default http port 443 is used to get Now Playing List from the Tivos on the LAN, but from WAN side<br>";
         text += "you will have to setup port forwarding in your router, then you should specify here the WAN (public) side<br>";
         text += "port number you are using in your router port forwarding settings.<br>";
         text += "NOTE: In order to save this setting you must OK the configuration window once for each TiVo";
      }
      else if (component.equals("wan_ipad_port")) {
         text =  "<b>wan ipad port</b><br>";
         text += "<b>Advanced Setting - for normal use leave this setting empty</b>.<br>";
         text += "Set this option only if you plan to use kmttg over a WAN instead of your local LAN.<br>";
         text += "By default http port 1413 for iPad interface to Tivos on the LAN, but from WAN side<br>";
         text += "you will have to setup port forwarding in your router, then you should specify here the WAN (public) side<br>";
         text += "port number you are using in your router port forwarding settings.<br>";
         text += "NOTE: In order to save this setting you must OK the configuration window once for each TiVo";
      }
      else if (component.equals("limit_npl_fetches")) {
         text =  "<b>limit # of npl fetches</b><br>";
         text += "Set this option > 0 only if you want to limit the number of show listings to retrieve for a TiVo.<br>";
         text += "This is useful if your TiVo has a lot recorded shows and you don't care about older shows and<br>";
         text += "want to speed up NPL retrieval by limiting # of shows retrieved.<br>";
         text += "Default setting of 0 means no limit.<br>";
         text += "A setting of 1 means limit to 1 fetch (128 most recent shows max).<br>";
         text += "A setting of 2 means limit to 2 fetches (256 most recent shows max), etc.<br>";
         text += "NOTE: In order to save this setting you must OK the configuration window once for each TiVo.<br>";
         text += "NOTE: The <b>Disk Usage</b> totals will obviously not be complete if you set this > 0";
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
      else if (component.equals("slingBox")) {
         text =  "<b>Show Slingbox capture tab</b><br>";
         text += "Enable or disable display of Slingbox capture tab.";
      }
      else if (component.equals("tableColAutoSize")) {
         text =  "<b>Auto size NPL column widths</b><br>";
         text += "If enabled then automatically size to fit text NPL table column widths.";
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
      else if (component.equals("download_tries")) {
         text =  "<b># download attempts</b><br>";
         text += "Number of times to attempt to download a TiVo file (in case download attempt fails).<br>";
         text += "If you only want 1 attempt then set this to 1 or 0.<br>";
         text += "Occasionally TiVo downloads fail due to <b>Server Busy</b> or other such errors, but<br>";
         text += "sometimes trying a download again after a short delay will work.";
      }
      else if (component.equals("download_retry_delay")) {
         text =  "<b>seconds between download retry attempts</b><br>";
         text += "Number of seconds to wait between download retry attempts. kmttg will wait at least this<br>";
         text += "number of seconds before trying a download again.";
      }
      else if (component.equals("download_delay")) {
         text =  "<b>start delay in seconds for download tasks</b><br>";
         text += "For any download task delay the start of the task by this number of seconds.<br>";
         text += "This helps take stress off TiVo web server to avoid potential <b>server busy</b> messages.";
      }
      else if (component.equals("autoLogSizeMB")) {
         text = "<b>auto log file size limit (MB)</b><br>";
         text += "File size limit for auto.log files which contains message logs when running kmttg <b>Auto Transfers</b><br>";
         text += "in service/background mode or if running <b>Loop in GUI</b> mode in kmttg GUI.<br>";
         text += "kmttg initially logs to <b>auto.log.0</b> file. Once this specified file size limit is reached then<br>";
         text += "contents of <b>auto.log.0</b> are copied to <b>auto.log.1</b> and <b>auto.log.0</b> contents are flushed.<br>";
         text += "This limit prevents auto log file from growing in size indefinitely.";
      }
      else if (component.equals("web_query")) {
         text =  "<b>web query base url (bindkey q)</b><br>";
         text += "For all tables that list shows if you select a table row and press keyboard button<br>";
         text += "<b>q</b> this will send this base url with the show title and subtitle appended<br>";
         text += "to a web browser. With the default imdb base url for example imdb.com query is used<br>";
         text += "in order to provide an easy way to get more information on the show selected in table.<br>";
         text += "Note that if you want to reset this setting to default imdb query then just completely<br>";
         text += "clear this field.";
      }
      else if (component.equals("web_browser")) {
         text =  "<b>web browser binary</b><br>";
         text += "Executable name of web browser to use for web queries.<br>";
         text += "If you leave this empty kmttg will attempt a sequence of popular browsers on Linux.";
      }
      else if (component.equals("tivo_username")) {
         text =  "<b>tivo.com username</b><br>";
         text += "For TiVo models older than series 4 the kmttg Remote can use your tivo.com login to<br>";
         text += "obtain some of the information needed for kmttg Remote functions.<br>";
         text += "This setting is optional. For series 4 or later enabling iPad style communications<br>";
         text += "is a lot more useful.";
      }
      else if (component.equals("tivo_password")) {
         text =  "<b>tivo.com password</b><br>";
         text += "For TiVo models older than series 4 the kmttg Remote can use your tivo.com login to<br>";
         text += "obtain some of the information needed for kmttg Remote functions.<br>";
         text += "This setting is optional. For series 4 or later enabling iPad style communications<br>";
         text += "is a lot more useful.";
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
      else if (component.equals("metadata_entries")) {
         text =  "<b>extra metadata entries (comma separated)</b><br>";
         text += "Extra metadata entries you want added automatically to all metadata files that kmttg<br>";
         text += "creates. You should use the required metadata file syntax of name : value and if there is<br>";
         text += "more that 1 line you should separate each pair of entries with a comma (,).";
      }
      else if (component.equals("lookAndFeel")) {
         text =  "<b>look and feel</b><br>";
         text += "Select look and feel to use for GUI in general.<br>";
         text += "NOTE: Anything other than 'default' may not look as intended.<br>";
         text += "NOTE: The <b>Mac OS</b> choice is reported to cause issues so should not be used";
      }
      else if (component.equals("MinChanDigits")) {
         text =  "<b>Min # Channel Digits</b><br>";
         text += "Set minimum number of digits to display for leading channel number.<br>";
         text += "Leading channel number will be padded with zeros if shorter than this number.<br>";
         text += "For example:<br>";
         text += "1 => channel 2 = 2;    channel 704 = 704<br>";
         text += "2 => channel 2 = 02;   channel 704 = 704<br>";
         text += "3 => channel 2 = 002;  channel 704 = 704<br>";
         text += "4 => channel 2 = 0002; channel 704 = 0704";
      }
      else if (component.equals("persistQueue")) {
          text =  "<b>Persist Job Queue</b><br>";
          text += "Upon exiting, this will auto save the job queue to a data file.<br>";
          text += "The next time kmttg is opened, it will restore the previous job queue and resume<br>";
          text += "the processing. This is particularly useful for running kmttg as a service<br>";
          text += "and the service or host system stops for some reason, then any work it has<br>";
          text += "queued up will not be lost.";
       }
      
      if (text.length() > 0) {
         text = "<html>" + text + "</html>";
      }
      return text;
   }

      
}
