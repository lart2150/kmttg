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
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Stack;

import javax.swing.BoxLayout;
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

import net.miginfocom.swing.MigLayout;

import com.tivo.kmttg.gui.MyTooltip;
import com.tivo.kmttg.gui.help;
import com.tivo.kmttg.gui.swing.SwingUtil;
import com.tivo.kmttg.gui.swing.Theme;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.httpserver.kmttgServer;
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
   private static double pos_x = -1;
   private static double pos_y = -1;

   private static JButton add = null;
   private static JButton del = null;
   private static JButton domain_token = null;
   private static JButton share_add = null;
   private static JButton share_del = null;
   private static JButton OK = null;
   private static JButton CANCEL = null;
   private static JButton autotune_test = null;
   private static JDialog dialog = null;
   private static JComboBox<String> tivos = null;
   private static JComboBox<String> shares = null;
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
   private static JCheckBox tivolibreDecrypt = null;
   private static JCheckBox tivolibreCompat = null;
   private static JCheckBox httpserver_enable = null;
   private static JCheckBox httpserver_share_filter = null;
   private static JCheckBox VrdEncode = null;
   private static JCheckBox VrdAllowMultiple = null;
   private static JCheckBox VrdCombineCutEncode = null;
   private static JCheckBox VrdQsfixMpeg2ps = null;
   private static JCheckBox VrdOneAtATime = null;
   private static JCheckBox TivoWebPlusDelete = null;
   private static JCheckBox rpcDelete = null;
   private static JCheckBox rpcOld = null;
   private static JCheckBox HideProtectedFiles = null;
   private static JCheckBox TiVoSort = null;
   private static JCheckBox OverwriteFiles = null;
   private static JCheckBox DeleteFailedDownloads = null;
   private static JCheckBox toolTips = null;
   private static JCheckBox slingBox = null;
   private static JCheckBox tableColAutoSize = null;
   private static JCheckBox jobMonitorFullPaths = null;
   private static JCheckBox autotune_enabled = null;
   private static JCheckBox autoskip_enabled = null;
   private static JCheckBox autoskip_import = null;
   private static JCheckBox autoskip_cutonly = null;
   private static JCheckBox autoskip_prune = null;
   private static JCheckBox autoskip_batch_standby = null;
   private static JCheckBox autoskip_indicate_skip = null;
   private static JCheckBox autoskip_jumpToEnd = null;
   private static JCheckBox combine_download_decrypt = null;
   private static JCheckBox single_download = null;
   private static JCheckBox rpcnpl = null;
   private static JCheckBox enableRpc = null;
   private static JCheckBox persistQueue = null;
   private static JTextField VRDexe = null;
   private static JTextField tivo_name = null;
   private static JTextField tivo_ip = null;
   private static JTextField share_name = null;
   private static JTextField share_dir = null;
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
   private static JTextField ffmpeg = null;
   private static JTextField mediainfo = null;
   private static JTextField mencoder = null;
   private static JTextField handbrake = null;
   private static JTextField comskip = null;
   private static JTextField comskip_ini = null;
   private static JTextField wan_http_port = null;
   private static JTextField wan_https_port = null;
   private static JTextField wan_rpc_port = null;
   private static JTextField limit_npl_fetches = null;
   private static JTextField active_job_limit = null;
   private static JTextField t2extract = null;
   //private static JTextField t2extract_args = null;
   private static JTextField ccextractor = null;
   private static JTextField AtomicParsley = null;
   private static JTextField disk_space = null;
   private static JTextField customCommand = null;
   private static JTextField toolTipsDelay = null;
   private static JTextField toolTipsTimeout = null;
   private static JTextField cpu_cores = null;
   private static JTextField download_tries = null;
   private static JTextField download_retry_delay = null;
   private static JTextField download_delay = null;
   private static JTextField autoskip_padding_start = null;
   private static JTextField autoskip_padding_stop = null;
   private static JTextField autoskip_chan_off = null;
   private static JTextField autoskip_chan_on = null;
   private static JTextField metadata_entries = null;
   private static JTextField httpserver_port = null;
   private static JTextField httpserver_cache = null;
   private static JTextField autoLogSizeMB = null;
   //private static JTextField pyTivo_host = null;
   private static JTextField web_query = null;
   private static JTextField web_browser = null;
   private static JTextField tivo_username = null;
   private static JTextField tivo_password = null;
   //private static JTextField pyTivo_config = null;
   private static JTextField autotune_channel_interval = null;
   private static JTextField autotune_button_interval = null;
   private static JTextField autotune_chan1 = null;
   private static JTextField autotune_chan2 = null;
   private static JComboBox<String> MinChanDigits = null;
   //private static JComboBox<String> pyTivo_tivo = null;
   //private static JComboBox<String> pyTivo_files = null;
   private static JComboBox<String> metadata_files = null;
   private static JComboBox<String> keywords = null;
   private static JComboBox<String> customFiles = null;
   private static JComboBox<String> autotune_tivoName = null;
   private static JComboBox<String> lookAndFeel = null;
   private static JFileChooser FileBrowser = null;
   private static JFileChooser DirBrowser = null;
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
      if (pos_x != -1)
         dialog.setLocation((int)pos_x, (int)pos_y);
      dialog.setVisible(true);
   }

   public static JDialog getDialog() {
      return dialog;
   }

   // Paint text field background to indicate an error setting
   private static void textFieldError(JTextField f, String message) {
      debug.print("f=" + f + " message=" + message);
      log.error(message);
      f.setBackground(TableUtil.lightRed);
      errors.add(f);
      // Set tab background of this text field to error color as well
      int tab = getParentTab(f);
      if (f != null && tab != -1)
         tabbed_panel.setBackgroundAt(tab, TableUtil.lightRed);
   }

   private static int getParentTab(Component node) {
      for (int i=0; i<tabbed_panel.getTabCount(); ++i) {
         if (containsComponent(tabbed_panel.getComponentAt(i), node))
            return i;
      }
      return -1;
   }

   private static boolean containsComponent(Component parent, Component node) {
      if (parent == node)
         return true;
      if (parent instanceof Container) {
         for (Component c : ((Container)parent).getComponents()) {
            if (containsComponent(c, node))
               return true;
         }
      }
      return false;
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
         tabbed_panel.setBackgroundAt(i, null);
   }

   // Callback for OK button
   private static void okCB() {
      debug.print("");
      clearTextFieldErrors();
      int errors = write();
      if (errors > 0) {
         int result = JOptionPane.showConfirmDialog(
            dialog, "" + errors + " error(s). Proceed to save settings anyway?",
            "Confirm", JOptionPane.OK_CANCEL_OPTION
         );
         if (result == JOptionPane.OK_OPTION) {
            config.save();
            pos_x = dialog.getX(); pos_y = dialog.getY();
            dialog.setVisible(false);
         }
      } else {
         config.save();
         pos_x = dialog.getX(); pos_y = dialog.getY();
         dialog.setVisible(false);
      }
      config.gui.refreshOptions(true);
   }

   // Callback for tivo add button
   private static void addCB() {
      debug.print("");
      // Add name=ip to tivos JComboBox
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
            String s = tivos.getItemAt(i);
            if (s.equals(value))
               doit = false;
         }
      }
      if (doit) {
         tivos.addItem(value);
         tivos.setSelectedItem(value);
      }
   }

   // Callback for share add button
   private static void share_addCB() {
      debug.print("");
      // Add name=dir to shares JComboBox
      String name = string.removeLeadingTrailingSpaces(share_name.getText());
      String dir = string.removeLeadingTrailingSpaces(share_dir.getText());
      if ( name.length() == 0) {
         log.error("Enter a name in the 'Share Name' field");
         return;
      }
      if ( dir.length() == 0) {
         log.error("Enter a valid directory in 'Share Directory' field");
         return;
      }
      addShare(name, dir);
   }

   public static void addShare(String name, String dir) {
      debug.print("name=" + name + " dir=" + dir);
      if (dialog == null || shares == null) return;
      String value = name + "=" + dir;
      // Don't add duplicate value
      Boolean doit = true;
      int count = shares.getItemCount();
      if (count > 0) {
         for (int i=0; i<count; i++) {
            String s = shares.getItemAt(i);
            if (s.equals(value))
               doit = false;
         }
      }
      if (doit) {
         shares.addItem(value);
         shares.setSelectedItem(value);
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
         String rpc = config.getWanSetting(tivoName, "rpc");
         if (rpc != null) {
            wan_rpc_port.setText(rpc);
         } else {
            wan_rpc_port.setText("");
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
      // Remove current selection in tivos JComboBox
      int selected = tivos.getSelectedIndex();
      if (selected > -1) {
         tivos.removeItemAt(selected);
      } else {
         log.error("No tivo entries left to remove");
      }
   }

   // Callback for tivo del button
   private static void domainTokenCB() {
	 log.warn("Refreshing token");
	 Thread t = new Thread()
	 {
	     public void run() {
	    	 config.refreshDomainToken();
	     }
	 };
	 t.start();
   }

   // Callback for share del button
   private static void share_delCB() {
      debug.print("");
      // Remove current selection in shares JComboBox
      int selected = shares.getSelectedIndex();
      if (selected > -1) {
         shares.removeItemAt(selected);
      } else {
         log.error("No share entries left to remove");
      }
   }

   // Callback for autotune test button
   private static void autotune_testCB() {
      debug.print("");
      if ( autotune_tivoName.getItemCount() > 0 ) {
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

   // Callback for keywords JComboBox
   private static void keywordsCB(String keyword) {
      debug.print("");
      if (keyword != null) {
         // Append selected entry to file_naming text field
         // (Replace current selection if any)
         int len = file_naming.getText().length();
         file_naming.setCaretPosition(len);
         file_naming.replaceSelection(keyword);
      }
   }

   // Callback for customFiles JComboBox
   private static void customFilesCB(String keyword) {
      debug.print("");

      // Append selected entry to customCommand text field
      // (Replace current selection if any)
      int len = customCommand.getText().length();
      customCommand.setCaretPosition(len);
      customCommand.replaceSelection(keyword);
   }

   // Callback for autotune_tivoName JComboBox
   private static void autotune_tivoNameCB(String name) {
      debug.print("");
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
         if (tivos.getItemCount() > 0)
            tivos.setSelectedItem(tivos.getItemAt(0));
         if (autotune_tivoName.getItemCount() > 0)
            autotune_tivoName.setSelectedItem(autotune_tivoName.getItemAt(0));
      }

      // Shares
      if (config.httpserver_shares.size()>0) {
         // Update share name lists
         shares.removeAllItems();
         for (String dir : config.httpserver_shares.keySet()) {
            shares.addItem(dir + "=" + config.httpserver_shares.get(dir));
         }
         if (shares.getItemCount() > 0)
            shares.setSelectedItem(shares.getItemAt(0));
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
         String rpc = config.getWanSetting(tivoName, "rpc");
         if (rpc != null)
            wan_rpc_port.setText(rpc);
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

      // tivolibreDecrypt
      if (config.tivolibreDecrypt == 1) {
         tivolibreDecrypt.setSelected(true);
         DsdDecrypt.setSelected(false);
         config.DsdDecrypt = 0;
      }

      // tivolibreCompat
      if (config.tivolibreCompat == 1)
         tivolibreCompat.setSelected(true);
      else
         tivolibreCompat.setSelected(false);

      // httpserver_enable
      if (config.httpserver_enable == 1)
         httpserver_enable.setSelected(true);
      else
         httpserver_enable.setSelected(false);

      // httpserver_share_filter
      if (config.httpserver_share_filter == 1)
         httpserver_share_filter.setSelected(true);
      else
         httpserver_share_filter.setSelected(false);

      // VRD flag
      if (config.VRD == 1)
         VRD.setSelected(true);
      else
         VRD.setSelected(false);

      // VRDexe
      if (config.VRD == 1)
         VRDexe.setText(config.VRDexe);

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

      // VrdOneAtATime
      if (config.VrdOneAtATime == 1)
    	  VrdOneAtATime.setSelected(true);
      else
    	  VrdOneAtATime.setSelected(false);

      // TivoWebPlusDelete
      if (config.twpDeleteEnabled())
         TivoWebPlusDelete.setSelected(true);
      else
         TivoWebPlusDelete.setSelected(false);

      // rpcDelete
      if (config.rpcDelete == 1)
         rpcDelete.setSelected(true);
      else
         rpcDelete.setSelected(false);

      // rpcOld
      if (config.rpcOld == 1)
         rpcOld.setSelected(true);
      else
         rpcOld.setSelected(false);

      // HideProtectedFiles
      if (config.HideProtectedFiles == 1)
         HideProtectedFiles.setSelected(true);
      else
         HideProtectedFiles.setSelected(false);

      // TiVoSort
      if (config.TiVoSort == 1)
         TiVoSort.setSelected(true);
      else
         TiVoSort.setSelected(false);

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

      // AtomicParsley
      AtomicParsley.setText(config.AtomicParsley);

      // ffmpeg
      ffmpeg.setText(config.ffmpeg);

      // mediainfo
      mediainfo.setText(config.mediainfo);

      // customCommand
      customCommand.setText(config.customCommand);

      // active job limit
      active_job_limit.setText("" + config.MaxJobs);

      // MinChanDigits
      MinChanDigits.setSelectedItem("" + config.MinChanDigits);

      // toolTipsDelay
      toolTipsDelay.setText("" + config.toolTipsDelay);

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

      // autoskip_enabled
      autoskip_enabled.setSelected(config.autoskip_enabled == 1);

      // autoskip_enabled
      autoskip_import.setSelected(config.autoskip_import == 1);

      // autoskip_cutonly
      autoskip_cutonly.setSelected(config.autoskip_cutonly == 1);

      // autoskip_prune
      autoskip_prune.setSelected(config.autoskip_prune == 1);

      // autoskip_batch_standby
      autoskip_batch_standby.setSelected(config.autoskip_batch_standby == 1);

      // autoskip_indicate_skip
      autoskip_indicate_skip.setSelected(config.autoskip_indicate_skip == 1);

      // autoskip_jumpToEnd
      autoskip_jumpToEnd.setSelected(config.autoskip_jumpToEnd == 1);

      // autoskip_padding_start
      autoskip_padding_start.setText("" + config.autoskip_padding_start);

      // autoskip_padding_stop
      autoskip_padding_stop.setText("" + config.autoskip_padding_stop);

      // autoskip_chan_off
      autoskip_chan_off.setText("" + config.autoskip_chan_off);

      // autoskip_chan_off
      autoskip_chan_on.setText("" + config.autoskip_chan_on);

      // metadata_entries
      metadata_entries.setText("" + config.metadata_entries);

      // httpserver_port
      httpserver_port.setText("" + config.httpserver_port);

      // httpserver_cache
      httpserver_cache.setText(config.httpserver_cache);

      // autoLogSizeMB
      autoLogSizeMB.setText("" + config.autoLogSizeMB);

      // pyTivo_host
      //pyTivo_host.setText("" + config.pyTivo_host);

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
      //pyTivo_config.setText("" + config.pyTivo_config);

      // pyTivo_tivo
      /*Stack<String> names = config.getNplTivoNames();
      if (names.size() > 0) {
         String setting = names.get(0);
         for (int i=0; i<names.size(); ++i) {
            if (names.get(i).equals(config.pyTivo_tivo)) {
               setting = config.pyTivo_tivo;
            }
         }
         pyTivo_tivo.setValue(setting);
      }*/

      // pyTivo_files
      //pyTivo_files.setValue(config.pyTivo_files);

      // metadata_files
      metadata_files.setSelectedItem(config.metadata_files);

      // lookAndFeel (normalize maps historical css names to current themes)
      if (lookAndFeel != null && config.lookAndFeel != null) {
         lookAndFeel.setSelectedItem(Theme.normalize(config.lookAndFeel));
      }

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
            String s = tivos.getItemAt(i);
            String[] l = s.split("=");
            if (l.length == 2) {
               h.put(l[0], l[1]);
            }
         }
      }
      config.setTivoNames(h);

      // Shares
      count = shares.getItemCount();
      if (count > 0) {
         config.httpserver_shares.clear();
         for (int i=0; i<count; i++) {
            String s = shares.getItemAt(i);
            String[] l = s.split("=");
            if (l.length == 2) {
               config.httpserver_shares.put(l[0], l[1]);
            }
         }
      }

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

         value = string.removeLeadingTrailingSpaces(wan_rpc_port.getText());
         if (value.length() > 0) {
            try {
               Integer.parseInt(value);
               config.setWanSetting(tivoName, "rpc", value);
            } catch(NumberFormatException e) {
               textFieldError(wan_rpc_port, "wan rpc port should be a number: '" + value + "'");
               errors++;
            }
         } else {
            config.setWanSetting(tivoName, "rpc", "");
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

      // VRDexe
      value = string.removeLeadingTrailingSpaces(VRDexe.getText());
      if (value.length() > 0) {
    	   if (file.isFile(value)) {
    	      config.VRDexe = value;
    	   } else {
    	      if (config.VRD == 1) {
    	         textFieldError(VRDexe, "Configured path to VRD executable doesn't exist: '" + value + "'");
    	         errors++;
    	      }
    	   }
      }

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
      if (DsdDecrypt.isSelected())
         config.DsdDecrypt = 1;
      else
         config.DsdDecrypt = 0;

      // tivolibreDecrypt
      if (tivolibreDecrypt.isSelected()) {
         config.tivolibreDecrypt = 1;
         config.DsdDecrypt = 0;
      }
      else
         config.tivolibreDecrypt = 0;

      // tivolibreCompat
      if (tivolibreCompat.isSelected())
         config.tivolibreCompat = 1;
      else
         config.tivolibreCompat = 0;

      // httpserver_enable
      if (httpserver_enable.isSelected()) {
         config.httpserver_enable = 1;
         if (config.httpserver == null)
            new kmttgServer();
      }
      else {
         config.httpserver_enable = 0;
         if (config.httpserver != null) {
            config.httpserver.stop();
            config.httpserver = null;
         }
      }

      // httpserver_share_filter
      if (httpserver_share_filter.isSelected()) {
         config.httpserver_share_filter = 1;
      }
      else {
         config.httpserver_share_filter = 0;
      }

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

      // VrdOneAtATime
      if (VrdOneAtATime.isSelected() && config.VRD == 1)
         config.VrdOneAtATime = 1;
      else
         config.VrdOneAtATime = 0;

      // TivoWebPlusDelete
      if (TivoWebPlusDelete.isSelected())
         config.twpDeleteEnabledSet(true);
      else
         config.twpDeleteEnabledSet(false);

      // rpcDelete
      if (rpcDelete.isSelected())
         config.rpcDelete = 1;
      else
         config.rpcDelete = 0;

      // rpcOld
      if (rpcOld.isSelected())
         config.rpcOld = 1;
      else
         config.rpcOld = 0;

      // HideProtectedFiles
      if (HideProtectedFiles.isSelected())
         config.HideProtectedFiles = 1;
      else
         config.HideProtectedFiles = 0;

      // TiVoSort
      if (TiVoSort.isSelected())
         config.TiVoSort = 1;
      else
         config.TiVoSort = 0;

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
      MyTooltip.enableToolTips(config.toolTips);

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

      // t2extract_args
      //value = string.removeLeadingTrailingSpaces(t2extract_args.getText());
      //if (value.length() == 0) {
      //   // Reset to default if none given
      //   value = "";
      //}
      //config.t2extract_args = value;

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
      config.MinChanDigits = Integer.parseInt((String)MinChanDigits.getSelectedItem());

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
            textFieldError(httpserver_port, "Illegal setting for web server port: '" + value + "'");
            log.error("Setting to 8181");
            config.httpserver_port = 8181;
            httpserver_port.setText("" + config.httpserver_port);
            errors++;
         }
      } else {
         config.httpserver_port = 8181;
      }

      // httpserver_cache
      value = string.removeLeadingTrailingSpaces(httpserver_cache.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = config.httpserver_home + File.separator + "cache";
      } else {
         if ( ! file.isDir(value) ) {
            textFieldError(httpserver_cache, "web server cache setting not a valid dir: '" + value  + "'");
            errors++;
         }
      }
      config.httpserver_cache = value;

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

      // autoskip_enabled
      if (autoskip_enabled.isSelected())
         config.autoskip_enabled = 1;
      else
         config.autoskip_enabled = 0;

      // autoskip_import
      if (autoskip_import.isSelected())
         config.autoskip_import = 1;
      else
         config.autoskip_import = 0;

      // autoskip_cutonly
      if (autoskip_cutonly.isSelected())
         config.autoskip_cutonly = 1;
      else
         config.autoskip_cutonly = 0;

      // autoskip_prune
      if (autoskip_prune.isSelected())
         config.autoskip_prune = 1;
      else
         config.autoskip_prune = 0;

      // autoskip_batch_standby
      if (autoskip_batch_standby.isSelected())
         config.autoskip_batch_standby = 1;
      else
         config.autoskip_batch_standby = 0;

      // autoskip_indicate_skip
      if (autoskip_indicate_skip.isSelected())
         config.autoskip_indicate_skip = 1;
      else
         config.autoskip_indicate_skip = 0;

      // autoskip_jumpToEnd
      if (autoskip_jumpToEnd.isSelected())
         config.autoskip_jumpToEnd = 1;
      else
         config.autoskip_jumpToEnd = 0;

      // autoskip_padding_start
      value = string.removeLeadingTrailingSpaces(autoskip_padding_start.getText());
      if (value.length() > 0) {
         try {
            config.autoskip_padding_start = Integer.parseInt(value);
         } catch(NumberFormatException e) {
            textFieldError(autoskip_padding_start, "Illegal setting for AutoSkip padding start: '" + value + "'");
            log.error("Setting to 0");
            config.autoskip_padding_start = 0;
            autoskip_padding_start.setText("" + config.autoskip_padding_start);
            errors++;
         }
      } else {
         config.autoskip_padding_start = 0;
      }

      // autoskip_padding_stop
      value = string.removeLeadingTrailingSpaces(autoskip_padding_stop.getText());
      if (value.length() > 0) {
         try {
            config.autoskip_padding_stop = Integer.parseInt(value);
         } catch(NumberFormatException e) {
            textFieldError(autoskip_padding_stop, "Illegal setting for AutoSkip padding stop: '" + value + "'");
            log.error("Setting to 0");
            config.autoskip_padding_stop = 0;
            autoskip_padding_stop.setText("" + config.autoskip_padding_stop);
            errors++;
         }
      } else {
         config.autoskip_padding_stop = 0;
      }

      // autoskip_chan_off
      value = string.removeLeadingTrailingSpaces(autoskip_chan_off.getText());
      if (value.length() > 0) {
         config.autoskip_chan_off = value;
      } else {
         config.autoskip_chan_off = "0";
      }

      // autoskip_chan_on
      value = string.removeLeadingTrailingSpaces(autoskip_chan_on.getText());
      if (value.length() > 0) {
         config.autoskip_chan_on = value;
      } else {
         config.autoskip_chan_on = "1";
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

      // toolTipsDelay
      value = string.removeLeadingTrailingSpaces(toolTipsDelay.getText());
      if (value.length() > 0) {
         try {
            config.toolTipsDelay = Integer.parseInt(value);
            MyTooltip.setTooltipDelay(config.toolTipsDelay, config.toolTipsTimeout);
         } catch(NumberFormatException e) {
            textFieldError(toolTipsDelay, "Illegal setting for toolTips delay: '" + value + "'");
            log.error("Setting to 2");
            config.toolTipsDelay = 2;
            toolTipsDelay.setText("" + config.toolTipsDelay);
            errors++;
         }
      } else {
         config.toolTipsDelay = 2;
      }

      // toolTipsTimeout
      value = string.removeLeadingTrailingSpaces(toolTipsTimeout.getText());
      if (value.length() > 0) {
         try {
            config.toolTipsTimeout = Integer.parseInt(value);
            MyTooltip.setTooltipDelay(config.toolTipsDelay, config.toolTipsTimeout);
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
      /*value = string.removeLeadingTrailingSpaces(pyTivo_host.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "localhost";
      }
      config.pyTivo_host = value;*/

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
      /*value = string.removeLeadingTrailingSpaces(pyTivo_config.getText());
      if (value.length() == 0) {
         // Reset to default if none given
         value = "";
      }
      config.pyTivo_config = value;*/

      // pyTivo_tivo
      //config.pyTivo_tivo = pyTivo_tivo.getValue();

      // pyTivo_files
      //config.pyTivo_files = pyTivo_files.getValue();

      // metadata_files
      config.metadata_files = (String)metadata_files.getSelectedItem();

      // lookAndFeel
      config.lookAndFeel = (String)lookAndFeel.getSelectedItem();

      // autotune settings
      if (autotune_tivoName != null && autotune_tivoName.getItemCount() > 0) {
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
      encode_output_dir = new JTextField(); encode_output_dir.setColumns(30);
      mpeg_cut_dir = new JTextField(); mpeg_cut_dir.setColumns(30);
      mpeg_output_dir = new JTextField(); mpeg_output_dir.setColumns(30);
      qsfixDir = new JTextField(); qsfixDir.setColumns(30);
      tivo_output_dir = new JTextField(); tivo_output_dir.setColumns(30);
      file_naming = new JTextField(); file_naming.setColumns(30);
      files_path = new JTextField(); files_path.setColumns(30);
      tivodecode = new JTextField(); tivodecode.setColumns(30);
      dsd = new JTextField(); dsd.setColumns(30);
      ffmpeg = new JTextField(); ffmpeg.setColumns(30);
      mediainfo = new JTextField(); mediainfo.setColumns(30);
      mencoder = new JTextField(); mencoder.setColumns(30);
      handbrake = new JTextField(); handbrake.setColumns(30);
      comskip = new JTextField(); comskip.setColumns(30);
      comskip_ini = new JTextField(); comskip_ini.setColumns(30);
      t2extract = new JTextField(); t2extract.setColumns(30);
      //t2extract_args = new JTextField(); t2extract_args.setColumns(30);
      ccextractor = new JTextField(); ccextractor.setColumns(30);
      AtomicParsley = new JTextField(); AtomicParsley.setColumns(30);
      customCommand = new JTextField(); customCommand.setColumns(30);
      web_query = new JTextField(); web_query.setColumns(30);
      web_browser = new JTextField(); web_browser.setColumns(30);
      tivo_username = new JTextField(); tivo_username.setColumns(30);
      tivo_password = new JTextField(); tivo_password.setColumns(30);
      //pyTivo_config = new JTextField(); pyTivo_config.setColumns(30);

      VRDexe = new JTextField(); VRDexe.setColumns(20);
      tivo_name = new JTextField(); tivo_name.setColumns(20);
      tivo_ip = new JTextField(); tivo_ip.setColumns(20);
      share_name = new JTextField(); share_name.setColumns(20);
      share_dir = new JTextField(); share_dir.setColumns(20);
      autotune_channel_interval = new JTextField(); autotune_channel_interval.setColumns(20);
      autotune_button_interval = new JTextField(); autotune_button_interval.setColumns(20);
      autotune_chan1 = new JTextField(); autotune_chan1.setColumns(20);
      autotune_chan2 = new JTextField(); autotune_chan2.setColumns(20);
      //pyTivo_host = new JTextField(); pyTivo_host.setColumns(20);

      MAK = new JTextField(); MAK.setColumns(15);
      wan_http_port = new JTextField(); wan_http_port.setColumns(15);
      wan_https_port = new JTextField(); wan_https_port.setColumns(15);
      wan_rpc_port = new JTextField(); wan_rpc_port.setColumns(15);
      limit_npl_fetches = new JTextField(); limit_npl_fetches.setColumns(15);
      active_job_limit = new JTextField(); active_job_limit.setColumns(15);
      toolTipsDelay = new JTextField(); toolTipsDelay.setColumns(15);
      toolTipsTimeout = new JTextField(); toolTipsTimeout.setColumns(15);
      cpu_cores = new JTextField(); cpu_cores.setColumns(15);
      download_tries = new JTextField(); download_tries.setColumns(15);
      download_retry_delay = new JTextField(); download_retry_delay.setColumns(15);
      download_delay = new JTextField(); download_delay.setColumns(15);
      autoskip_padding_start = new JTextField(); autoskip_padding_start.setColumns(15);
      autoskip_padding_stop = new JTextField(); autoskip_padding_stop.setColumns(15);
      autoskip_chan_off = new JTextField(); autoskip_chan_off.setColumns(15);
      autoskip_chan_on = new JTextField(); autoskip_chan_on.setColumns(15);
      metadata_entries = new JTextField(); metadata_entries.setColumns(15);
      httpserver_port = new JTextField(); httpserver_port.setColumns(15);
      httpserver_cache = new JTextField(); httpserver_cache.setColumns(15);
      autoLogSizeMB = new JTextField(); autoLogSizeMB.setColumns(15);

      disk_space = new JTextField(); disk_space.setColumns(5);
      FontSize = new JTextField(); FontSize.setColumns(5);

      JLabel tivos_label = new JLabel();
      tivos = new JComboBox<String>();
      tivos.addActionListener(new ActionListener() {
         @Override public void actionPerformed(ActionEvent e) {
            String newVal = (String)tivos.getSelectedItem();
            if (newVal != null) {
               updateWanSettings(newVal);
               updateLimitNplSettings(newVal);
               updateEnableRpcSettings(newVal);
            }
         }
      });

      JLabel shares_label = new JLabel();
      shares = new JComboBox<String>();

      add = new JButton();
      del = new JButton();
      domain_token = new JButton();
      share_add = new JButton();
      share_del = new JButton();
      JLabel VRDexe_label = new JLabel();
      JLabel tivo_name_label = new JLabel();
      JLabel tivo_ip_label = new JLabel();
      JLabel share_name_label = new JLabel();
      JLabel share_dir_label = new JLabel();
      JLabel autotune_channel_interval_label = new JLabel();
      JLabel autotune_button_interval_label = new JLabel();
      JLabel autotune_chan1_label = new JLabel();
      JLabel autotune_chan2_label = new JLabel();
      JLabel autotune_tivoName_label = new JLabel();
      JLabel files_path_label = new JLabel();
      remove_tivo = new JCheckBox();
      remove_comcut = new JCheckBox();
      remove_comcut_mpeg = new JCheckBox();
      remove_mpeg = new JCheckBox();
      QSFixBackupMpegFile = new JCheckBox();
      download_check_length = new JCheckBox();
      UseAdscan = new JCheckBox();
      VRD = new JCheckBox();
      VrdReview = new JCheckBox();
      comskip_review = new JCheckBox();
      VrdReview_noCuts = new JCheckBox();
      VrdQsFilter = new JCheckBox();
      VrdDecrypt = new JCheckBox();
      DsdDecrypt = new JCheckBox();
      tivolibreDecrypt = new JCheckBox();
      tivolibreCompat = new JCheckBox();
      httpserver_enable = new JCheckBox();
      httpserver_share_filter = new JCheckBox();
      VrdEncode = new JCheckBox();
      VrdAllowMultiple = new JCheckBox();
      VrdCombineCutEncode = new JCheckBox();
      VrdQsfixMpeg2ps = new JCheckBox();
      VrdOneAtATime = new JCheckBox();
      TivoWebPlusDelete = new JCheckBox();
      rpcDelete = new JCheckBox();
      rpcOld = new JCheckBox();
      HideProtectedFiles = new JCheckBox();
      TiVoSort = new JCheckBox();
      OverwriteFiles = new JCheckBox();
      DeleteFailedDownloads = new JCheckBox();
      combine_download_decrypt = new JCheckBox();
      single_download = new JCheckBox();
      rpcnpl = new JCheckBox();
      enableRpc = new JCheckBox();
      persistQueue = new JCheckBox();
      JLabel MAK_label = new JLabel();
      JLabel FontSize_label = new JLabel();
      JLabel file_naming_label = new JLabel();
      JLabel tivo_output_dir_label = new JLabel();
      JLabel mpeg_output_dir_label = new JLabel();
      JLabel qsfixDir_label = new JLabel();
      JLabel mpeg_cut_dir_label = new JLabel();
      JLabel encode_output_dir_label = new JLabel();
      JLabel tivodecode_label = new JLabel();
      JLabel dsd_label = new JLabel();
      JLabel ffmpeg_label = new JLabel();
      JLabel mediainfo_label = new JLabel();
      JLabel mencoder_label = new JLabel();
      JLabel handbrake_label = new JLabel();
      JLabel comskip_label = new JLabel();
      JLabel comskip_ini_label = new JLabel();
      JLabel wan_http_port_label = new JLabel();
      JLabel wan_https_port_label = new JLabel();
      JLabel wan_rpc_port_label = new JLabel();
      JLabel limit_npl_fetches_label = new JLabel();
      JLabel active_job_limit_label = new JLabel();
      JLabel t2extract_label = new JLabel();
      //JLabel t2extract_args_label = new JLabel();
      JLabel ccextractor_label = new JLabel();
      JLabel AtomicParsley_label = new JLabel();
      JLabel customCommand_label = new JLabel();
      JLabel customFiles_label = new JLabel();
      JLabel cpu_cores_label = new JLabel();
      JLabel download_tries_label = new JLabel();
      JLabel download_retry_delay_label = new JLabel();
      JLabel download_delay_label = new JLabel();
      JLabel autoskip_padding_start_label = new JLabel();
      JLabel autoskip_padding_stop_label = new JLabel();
      JLabel autoskip_chan_off_label = new JLabel();
      JLabel autoskip_chan_on_label = new JLabel();
      JLabel metadata_entries_label = new JLabel();
      JLabel httpserver_port_label = new JLabel();
      JLabel httpserver_cache_label = new JLabel();
      JLabel autoLogSizeMB_label = new JLabel();
      JLabel available_keywords_label = new JLabel();
      //JLabel pyTivo_host_label = new JLabel();
      JLabel web_query_label = new JLabel();
      JLabel web_browser_label = new JLabel();
      JLabel tivo_username_label = new JLabel();
      JLabel tivo_password_label = new JLabel();
      //JLabel pyTivo_config_label = new JLabel();
      //JLabel pyTivo_tivo_label = new JLabel();
      JLabel MinChanDigits_label = new JLabel();
      //JLabel pyTivo_files_label = new JLabel();
      JLabel metadata_files_label = new JLabel();
      JLabel lookAndFeel_label = new JLabel();
      MinChanDigits = new JComboBox<String>();
      //pyTivo_tivo = new JComboBox<String>();
      //pyTivo_files = new JComboBox<String>();
      metadata_files = new JComboBox<String>();
      lookAndFeel = new JComboBox<String>();
      keywords = new JComboBox<String>();
      customFiles = new JComboBox<String>();
      autotune_tivoName = new JComboBox<String>();
      check_space = new JCheckBox();
      JLabel disk_space_label = new JLabel();
      beacon = new JCheckBox();
      UseOldBeacon = new JCheckBox();
      npl_when_started = new JCheckBox();
      showHistoryInTable = new JCheckBox();
      download_time_estimate = new JCheckBox();
      toolTips = new JCheckBox();
      slingBox = new JCheckBox();
      tableColAutoSize = new JCheckBox();
      jobMonitorFullPaths = new JCheckBox();
      autotune_enabled = new JCheckBox();
      autoskip_enabled = new JCheckBox();
      autoskip_import = new JCheckBox();
      autoskip_cutonly = new JCheckBox();
      autoskip_prune = new JCheckBox();
      autoskip_batch_standby = new JCheckBox();
      autoskip_indicate_skip = new JCheckBox();
      autoskip_jumpToEnd = new JCheckBox();
      JLabel toolTipsDelay_label = new JLabel();
      JLabel toolTipsTimeout_label = new JLabel();
      OK = new JButton();
      CANCEL = new JButton();
      autotune_test = new JButton();
      FileBrowser = new JFileChooser(); FileBrowser.setCurrentDirectory(new File(config.programDir));
      FileBrowser.setDialogTitle("Choose File");
      DirBrowser = new JFileChooser(); DirBrowser.setCurrentDirectory(new File(config.programDir));
      DirBrowser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      DirBrowser.setDialogTitle("Choose Directory");

      tivos_label.setText("Tivos");

      add.setText("ADD");
      add.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            addCB();
         }
      });

      del.setText("DEL");
      del.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            delCB();
         }
      });

      domain_token.setText("Refresh Token");
      domain_token.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
        	 domainTokenCB();
         }
      });


      share_add.setText("ADD");
      share_add.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            share_addCB();
         }
      });

      share_del.setText("DEL");
      share_del.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            share_delCB();
         }
      });

      VRDexe_label.setText("VideoRedo executable");
      tivo_name_label.setText("Tivo Name");
      tivo_ip_label.setText("Tivo IP#");
      share_name_label.setText("Share Name");
      share_dir_label.setText("Share Directory");
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
      VrdDecrypt.setText("Decrypt using VideoRedo");
      DsdDecrypt.setText("Decrypt using DirectShow Dump instead of tivodecode");
      tivolibreDecrypt.setText("Decrypt using tivolibre instead of tivodecode");
      tivolibreCompat.setText("tivolibre DirectShow compatibility mode");
      httpserver_enable.setText("Enable web server");
      httpserver_share_filter.setText("Share browser show video files only");
      VrdEncode.setText("Show VideoRedo encoding profiles");
      VrdAllowMultiple.setText("Run all VideoRedo jobs in GUI mode");
      VrdCombineCutEncode.setText("Combine Ad Cut & Encode");
      VrdQsfixMpeg2ps.setText("Force QS Fix output to always be mpeg2 Program Stream");
      VrdOneAtATime.setText("Only allow 1 VRD job at a time");
      TivoWebPlusDelete.setText("Enable TivoWebPlus Delete task");
      rpcDelete.setText("Enable rpc style delete task");
      rpcOld.setText("Use old RPC schema version for older TiVo software");
      HideProtectedFiles.setText("Do not show copy protected files in table");
      TiVoSort.setText("Sort table show titles as a TiVo does");
      OverwriteFiles.setText("Overwrite existing files");
      DeleteFailedDownloads.setText("Delete failed downloads");
      combine_download_decrypt.setText("Combine download and decrypt");
      single_download.setText("Allow only 1 download at a time");
      rpcnpl.setText("Use RPC to get NPL when possible");
      enableRpc.setText("Enable rpc style communications with this TiVo");
      persistQueue.setText("Automatically restore job queue between sessions");
      MAK_label.setText("MAK");
      shares_label.setText("Shares");
      FontSize_label.setText("GUI Font Size");
      file_naming_label.setText("File Naming");
      tivo_output_dir_label.setText(".TiVo Output Dir");
      mpeg_output_dir_label.setText(".mpg Output Dir");
      qsfixDir_label.setText("QS Fix Output Dir");
      mpeg_cut_dir_label.setText(".mpg Cut Dir");
      encode_output_dir_label.setText("Encode Output Dir");
      tivodecode_label.setText("tivodecode");
      dsd_label.setText("dsd");
      ffmpeg_label.setText("ffmpeg");
      mediainfo_label.setText("mediainfo cli");
      mencoder_label.setText("mencoder");
      handbrake_label.setText("handbrake");
      comskip_label.setText("comskip");
      comskip_ini_label.setText("comskip.ini");
      wan_http_port_label.setText("wan http port");
      wan_https_port_label.setText("wan https port");
      wan_rpc_port_label.setText("wan rpc port");
      limit_npl_fetches_label.setText("limit # of npl fetches");
      active_job_limit_label.setText("active job limit");
      t2extract_label.setText("ccextractor");
      //t2extract_args_label.setText("t2extract extra arguments");
      ccextractor_label.setText("ccextractor");
      AtomicParsley_label.setText("AtomicParsley");
      customCommand_label.setText("custom command");
      check_space.setText("Check Available Disk Space");
      available_keywords_label.setText("Available keywords:");
      cpu_cores_label.setText("encoding cpu cores");
      download_tries_label.setText("# download attempts");
      download_retry_delay_label.setText("seconds between download retry attempts");
      download_delay_label.setText("start delay in seconds for download tasks");
      autoskip_padding_start_label.setText("AutoSkip start point padding in msecs");
      autoskip_padding_stop_label.setText("AutoSkip end point padding in msecs");
      autoskip_chan_off_label.setText("AutoSkip service disable channel");
      autoskip_chan_on_label.setText("AutoSkip service enable channel");
      metadata_entries_label.setText("extra metadata entries (comma separated)");
      httpserver_port_label.setText("web server port");
      httpserver_cache_label.setText("web server cache dir");
      autoLogSizeMB_label.setText("auto log file size limit (MB)");
      web_query_label.setText("web query base url (bindkey q)");
      web_browser_label.setText("web browser binary");
      tivo_username_label.setText("tivo.com username");
      tivo_password_label.setText("tivo.com password");
      //pyTivo_host_label.setText("pyTivo host name");
      //pyTivo_config_label.setText("pyTivo.conf file");
      //pyTivo_tivo_label.setText("pyTivo push destination");
      //pyTivo_files_label.setText("Files to push");
      metadata_files_label.setText("metadata files");
      lookAndFeel_label.setText("look and feel");
      MinChanDigits_label.setText("Min # Channel Digits");

      String[] keywordsValues = {
         "[title]", "[mainTitle]", "[episodeTitle]", "[channelNum]",
         "[channel]", "[min]", "[hour]", "[wday]", "[mday]", "[month]",
         "[monthNum]", "[year]", "[movieYear]", "[originalAirDate]", "[oad_no_dashes]", "[season]", "[episode]",
         "[EpisodeNumber]", "[SeriesEpNumber]", "[description]", "[tivoName]", "[startTime]", "[/]"
      };
      for (String s : keywordsValues)
         keywords.addItem(s);
      keywords.addActionListener(new ActionListener() {
         @Override public void actionPerformed(ActionEvent e) {
            String newVal = (String)keywords.getSelectedItem();
            if (newVal != null) {
               keywordsCB(newVal);
            }
         }
      });

      for (String name : config.getNplTivoNames())
         autotune_tivoName.addItem(name);
      autotune_tivoName.addActionListener(new ActionListener() {
         @Override public void actionPerformed(ActionEvent e) {
            String newVal = (String)autotune_tivoName.getSelectedItem();
            if (newVal != null) {
               autotune_tivoNameCB(newVal);
            }
         }
      });

      MinChanDigits.addItem("1");
      MinChanDigits.addItem("2");
      MinChanDigits.addItem("3");
      MinChanDigits.addItem("4");
      MinChanDigits.setSelectedIndex(0);

      /*for (String name : config.getNplTivoNames())
         pyTivo_tivo.getItems().add(name);
      pyTivo_tivo.getSelectionModel().select(0);

      pyTivo_files.getItems().addAll(
         "tivoFile", "mpegFile", "mpegFile_cut", "encodeFile", "last", "all"
      );
      pyTivo_files.getSelectionModel().select(0);*/

      metadata_files.addItem("tivoFile");
      metadata_files.addItem("mpegFile");
      metadata_files.addItem("mpegFile_cut");
      metadata_files.addItem("encodeFile");
      metadata_files.addItem("last");
      metadata_files.addItem("all");
      metadata_files.setSelectedIndex(0);

      for (String name : config.gui.getAvailableLooks())
         lookAndFeel.addItem(name);
      lookAndFeel.setSelectedItem(Theme.normalize(config.lookAndFeel));
      lookAndFeel.addActionListener(new ActionListener() {
         @Override public void actionPerformed(ActionEvent e) {
            String newVal = (String)lookAndFeel.getSelectedItem();
            if (newVal != null) {
               config.gui.setLookAndFeel(newVal);
            }
         }
      });

      customFiles_label.setText("Available file args:");
      customFiles.addItem("[tivoFile]");
      customFiles.addItem("[metaFile]");
      customFiles.addItem("[mpegFile]");
      customFiles.addItem("[mpegFile_cut]");
      customFiles.addItem("[srtFile]");
      customFiles.addItem("[encodeFile]");
      customFiles.addItem("[downloadURL]");
      customFiles.setSelectedIndex(0);
      customFiles.addActionListener(new ActionListener() {
         @Override public void actionPerformed(ActionEvent e) {
            String newVal = (String)customFiles.getSelectedItem();
            if (newVal != null) {
               customFilesCB(newVal);
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
      toolTipsDelay_label.setText("toolTip open delay (secs)");
      toolTipsTimeout_label.setText("toolTip timeout (secs)");

      slingBox.setText("Show Slingbox capture tab");

      tableColAutoSize.setText("Auto size table column widths");

      jobMonitorFullPaths.setText("Show full paths in Job Monitor");

      autotune_enabled.setText("Tune to specified channels before a download");

      autoskip_enabled.setText("Enable AutoSkip functionality");

      autoskip_import.setText("Automatically Import to Skip Table after Ad Detect");

      autoskip_cutonly.setText("Only run Ad Skip/Ad Detect for shows with AutoSkip data");

      autoskip_prune.setText("Prune Skip Table automatically after NPL refresh");

      autoskip_batch_standby.setText("Set standby mode after batch AutoSkip from SkipMode");

      autoskip_indicate_skip.setText("Indicate with play when skipping");

      autoskip_jumpToEnd.setText("Jump to end of recording when last skip block entered");

      OK.setText("OK");
      OK.setName("button_config_ok");
      OK.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            okCB();
         }
      });

      CANCEL.setText("CANCEL");
      CANCEL.setName("button_config_cancel");
      CANCEL.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            pos_x = dialog.getX(); pos_y = dialog.getY();
            dialog.setVisible(false);
         }
      });

      autotune_test.setText("TEST");
      //autotune_test.setBackground(Color.green);
      autotune_test.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            autotune_testCB();
         }
      });

      // File browser mouse double-click listeners
      files_path.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent mouseEvent) {
            if( mouseEvent.getButton() == MouseEvent.BUTTON1 ) {
               if (mouseEvent.getClickCount() == 2) {
                  if (DirBrowser.showOpenDialog(config.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
                     File selectedFile = DirBrowser.getSelectedFile();
                     if (selectedFile != null) {
                        files_path.setText(selectedFile.getPath());
                     }
                  }
               }
            }
         }
      });

      tivo_output_dir.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent mouseEvent) {
            if( mouseEvent.getButton() == MouseEvent.BUTTON1 ) {
               if (mouseEvent.getClickCount() == 2) {
                  if (DirBrowser.showOpenDialog(config.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
                     File selectedFile = DirBrowser.getSelectedFile();
                     if (selectedFile != null) {
                        tivo_output_dir.setText(selectedFile.getPath());
                     }
                  }
               }
            }
         }
      });

      mpeg_output_dir.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent mouseEvent) {
            if( mouseEvent.getButton() == MouseEvent.BUTTON1 ) {
               if (mouseEvent.getClickCount() == 2) {
                  if (DirBrowser.showOpenDialog(config.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
                     File selectedFile = DirBrowser.getSelectedFile();
                     if (selectedFile != null) {
                        mpeg_output_dir.setText(selectedFile.getPath());
                     }
                  }
               }
            }
         }
      });

      qsfixDir.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent mouseEvent) {
            if( mouseEvent.getButton() == MouseEvent.BUTTON1 ) {
               if (mouseEvent.getClickCount() == 2) {
                  if (DirBrowser.showOpenDialog(config.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
                     File selectedFile = DirBrowser.getSelectedFile();
                     if (selectedFile != null) {
                        qsfixDir.setText(selectedFile.getPath());
                     }
                  }
               }
            }
         }
      });

      mpeg_cut_dir.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent mouseEvent) {
            if( mouseEvent.getButton() == MouseEvent.BUTTON1 ) {
               if (mouseEvent.getClickCount() == 2) {
                  if (DirBrowser.showOpenDialog(config.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
                     File selectedFile = DirBrowser.getSelectedFile();
                     if (selectedFile != null) {
                        mpeg_cut_dir.setText(selectedFile.getPath());
                     }
                  }
               }
            }
         }
      });

      encode_output_dir.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent mouseEvent) {
            if( mouseEvent.getButton() == MouseEvent.BUTTON1 ) {
               if (mouseEvent.getClickCount() == 2) {
                  if (DirBrowser.showOpenDialog(config.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
                     File selectedFile = DirBrowser.getSelectedFile();
                     if (selectedFile != null) {
                        encode_output_dir.setText(selectedFile.getPath());
                     }
                  }
               }
            }
         }
      });

      tivodecode.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent mouseEvent) {
            if( mouseEvent.getButton() == MouseEvent.BUTTON1 ) {
               if (mouseEvent.getClickCount() == 2) {
                  if (FileBrowser.showOpenDialog(config.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
                     File selectedFile = FileBrowser.getSelectedFile();
                     if (selectedFile != null) {
                        tivodecode.setText(selectedFile.getPath());
                     }
                  }
               }
            }
         }
      });

      dsd.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent mouseEvent) {
            if( mouseEvent.getButton() == MouseEvent.BUTTON1 ) {
               if (mouseEvent.getClickCount() == 2) {
                  if (FileBrowser.showOpenDialog(config.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
                     File selectedFile = FileBrowser.getSelectedFile();
                     if (selectedFile != null) {
                        dsd.setText(selectedFile.getPath());
                     }
                  }
               }
            }
         }
      });

      ffmpeg.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent mouseEvent) {
            if( mouseEvent.getButton() == MouseEvent.BUTTON1 ) {
               if (mouseEvent.getClickCount() == 2) {
                  if (FileBrowser.showOpenDialog(config.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
                     File selectedFile = FileBrowser.getSelectedFile();
                     if (selectedFile != null) {
                        ffmpeg.setText(selectedFile.getPath());
                     }
                  }
               }
            }
         }
      });

      mediainfo.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent mouseEvent) {
            if( mouseEvent.getButton() == MouseEvent.BUTTON1 ) {
               if (mouseEvent.getClickCount() == 2) {
                  if (FileBrowser.showOpenDialog(config.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
                     File selectedFile = FileBrowser.getSelectedFile();
                     if (selectedFile != null) {
                        mediainfo.setText(selectedFile.getPath());
                     }
                  }
               }
            }
         }
      });

      customCommand.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent mouseEvent) {
            if( mouseEvent.getButton() == MouseEvent.BUTTON1 ) {
               if (mouseEvent.getClickCount() == 2) {
                  if (FileBrowser.showOpenDialog(config.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
                     File selectedFile = FileBrowser.getSelectedFile();
                     if (selectedFile != null) {
                        customCommand.setText(selectedFile.getPath());
                     }
                  }
               }
            }
         }
      });

      mencoder.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent mouseEvent) {
            if( mouseEvent.getButton() == MouseEvent.BUTTON1 ) {
               if (mouseEvent.getClickCount() == 2) {
                  if (FileBrowser.showOpenDialog(config.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
                     File selectedFile = FileBrowser.getSelectedFile();
                     if (selectedFile != null) {
                        mencoder.setText(selectedFile.getPath());
                     }
                  }
               }
            }
         }
      });

      handbrake.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent mouseEvent) {
            if( mouseEvent.getButton() == MouseEvent.BUTTON1 ) {
               if (mouseEvent.getClickCount() == 2) {
                  if (FileBrowser.showOpenDialog(config.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
                     File selectedFile = FileBrowser.getSelectedFile();
                     if (selectedFile != null) {
                        handbrake.setText(selectedFile.getPath());
                     }
                  }
               }
            }
         }
      });

      comskip.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent mouseEvent) {
            if( mouseEvent.getButton() == MouseEvent.BUTTON1 ) {
               if (mouseEvent.getClickCount() == 2) {
                  if (FileBrowser.showOpenDialog(config.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
                     File selectedFile = FileBrowser.getSelectedFile();
                     if (selectedFile != null) {
                        comskip.setText(selectedFile.getPath());
                     }
                  }
               }
            }
         }
      });

      comskip_ini.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent mouseEvent) {
            if( mouseEvent.getButton() == MouseEvent.BUTTON1 ) {
               if (mouseEvent.getClickCount() == 2) {
                  if (FileBrowser.showOpenDialog(config.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
                     File selectedFile = FileBrowser.getSelectedFile();
                     if (selectedFile != null) {
                        comskip_ini.setText(selectedFile.getPath());
                     }
                  }
               }
            }
         }
      });

      t2extract.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent mouseEvent) {
            if( mouseEvent.getButton() == MouseEvent.BUTTON1 ) {
               if (mouseEvent.getClickCount() == 2) {
                  if (FileBrowser.showOpenDialog(config.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
                     File selectedFile = FileBrowser.getSelectedFile();
                     if (selectedFile != null) {
                        t2extract.setText(selectedFile.getPath());
                     }
                  }
               }
            }
         }
      });

      ccextractor.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent mouseEvent) {
            if( mouseEvent.getButton() == MouseEvent.BUTTON1 ) {
               if (mouseEvent.getClickCount() == 2) {
                  if (FileBrowser.showOpenDialog(config.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
                     File selectedFile = FileBrowser.getSelectedFile();
                     if (selectedFile != null) {
                        ccextractor.setText(selectedFile.getPath());
                     }
                  }
               }
            }
         }
      });

      AtomicParsley.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent mouseEvent) {
            if( mouseEvent.getButton() == MouseEvent.BUTTON1 ) {
               if (mouseEvent.getClickCount() == 2) {
                  if (FileBrowser.showOpenDialog(config.gui.getFrame()) == JFileChooser.APPROVE_OPTION) {
                     File selectedFile = FileBrowser.getSelectedFile();
                     if (selectedFile != null) {
                        AtomicParsley.setText(selectedFile.getPath());
                     }
                  }
               }
            }
         }
      });

      /*pyTivo_config.setOnMouseClicked(new EventHandler<MouseEvent>() {
         @Override
         public void handle(MouseEvent mouseEvent) {
            if( mouseEvent.getButton().equals(MouseButton.PRIMARY) ) {
               if (mouseEvent.getClickCount() == 2) {
                  File selectedFile = FileBrowser.showOpenDialog(config.gui.getFrame());
                  if (selectedFile != null) {
                     String selected = selectedFile.getPath();
                     if (string.basename(selected).equals("pyTivo.conf")) {
                        pyTivo_config.setText(selected);
                     } else {
                        log.error("Invalid file chosen - must be pyTivo.conf: " + selected);
                     }
                  }
               }
            }
         }
      });*/

      // Start of layout management
      int gy = 0;

      // Tivos Panel
      JPanel tivo_panel = new JPanel(new MigLayout("gapx 5, gapy 5"));
      // npl_when_started
      gy = 0;
      tivo_panel.add(npl_when_started, "cell 1 " + gy);

      // Look for Tivos on network
      gy++;
      tivo_panel.add(beacon, "cell 1 " + gy);

      // UseOldBeacon
      gy++;
      tivo_panel.add(UseOldBeacon, "cell 1 " + gy);

      // Tivo JComboBox
      gy++;
      tivo_panel.add(tivos_label, "cell 0 " + gy);
      tivo_panel.add(tivos, "cell 1 " + gy);

      // DEL button
      tivo_panel.add(del, "cell 4 " + gy);

      // Tivo name
      gy++;
      tivo_panel.add(tivo_name_label, "cell 0 " + gy);
      tivo_panel.add(tivo_name, "cell 1 " + gy);

      // ADD button
      tivo_panel.add(add, "cell 4 " + gy);

      // Tivo ip
      gy++;
      tivo_panel.add(tivo_ip_label, "cell 0 " + gy);
      tivo_panel.add(tivo_ip, "cell 1 " + gy);

      // vertical space via empty label
      JLabel bogus = new JLabel(" ");
      gy++;
      tivo_panel.add(bogus, "cell 1 " + gy);

      // enableRpc
      gy++;
      tivo_panel.add(enableRpc, "cell 1 " + gy);

      // limit_npl_fetches
      gy++;
      tivo_panel.add(limit_npl_fetches_label, "cell 0 " + gy);
      tivo_panel.add(limit_npl_fetches, "cell 1 " + gy);

      // wan http port
      gy++;
      tivo_panel.add(wan_http_port_label, "cell 0 " + gy);
      tivo_panel.add(wan_http_port, "cell 1 " + gy);

      // wan https port
      gy++;
      tivo_panel.add(wan_https_port_label, "cell 0 " + gy);
      tivo_panel.add(wan_https_port, "cell 1 " + gy);

      // wan rpc port
      gy++;
      tivo_panel.add(wan_rpc_port_label, "cell 0 " + gy);
      tivo_panel.add(wan_rpc_port, "cell 1 " + gy);

      // tivo.com username & password
      gy++;
      tivo_panel.add(tivo_username_label, "cell 0 " + gy);
      tivo_panel.add(tivo_username, "cell 1 " + gy);

      gy++;
      tivo_panel.add(tivo_password_label, "cell 0 " + gy);
      tivo_panel.add(tivo_password, "cell 1 " + gy);
      tivo_panel.add(domain_token, "cell 4 " + gy);


      // autotune panel
      JPanel autotune_panel = new JPanel(new MigLayout("gapx 5, gapy 5"));

      gy=0;
      autotune_panel.add(autotune_tivoName_label, "cell 0 " + gy);
      autotune_panel.add(autotune_tivoName, "cell 1 " + gy);

      gy++;
      autotune_panel.add(autotune_enabled, "cell 1 " + gy);

      gy++;
      autotune_panel.add(autotune_chan1_label, "cell 0 " + gy);
      autotune_panel.add(autotune_chan1, "cell 1 " + gy);

      gy++;
      autotune_panel.add(autotune_chan2_label, "cell 0 " + gy);
      autotune_panel.add(autotune_chan2, "cell 1 " + gy);

      gy++;
      autotune_panel.add(autotune_channel_interval_label, "cell 0 " + gy);
      autotune_panel.add(autotune_channel_interval, "cell 1 " + gy);

      gy++;
      autotune_panel.add(autotune_button_interval_label, "cell 0 " + gy);
      autotune_panel.add(autotune_button_interval, "cell 1 " + gy);

      gy++;
      autotune_panel.add(autotune_test, "cell 1 " + gy);

      // autoskip_panel
      JPanel autoskip_panel = new JPanel(new MigLayout("gapx 5, gapy 5"));

      JButton autoskip_doc = new JButton("Documentation");
      autoskip_doc.setToolTipText(getToolTip("autoskip_doc"));
      autoskip_doc.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            help.showInBrowser("https://sourceforge.net/p/kmttg/wiki/AutoSkip/");
         }
      });

      gy = 0;
      autoskip_panel.add(autoskip_doc, "cell 1 " + gy);

      gy++;
      autoskip_panel.add(autoskip_enabled, "cell 1 " + gy);

      gy++;
      autoskip_panel.add(autoskip_import, "cell 1 " + gy);

      gy++;
      autoskip_panel.add(autoskip_cutonly, "cell 1 " + gy);

      gy++;
      autoskip_panel.add(autoskip_prune, "cell 1 " + gy);

      gy++;
      autoskip_panel.add(autoskip_indicate_skip, "cell 1 " + gy);

      gy++;
      autoskip_panel.add(autoskip_batch_standby, "cell 1 " + gy);

      gy++;
      autoskip_panel.add(autoskip_jumpToEnd, "cell 1 " + gy);

      gy++;
      autoskip_panel.add(autoskip_padding_start_label, "cell 0 " + gy);
      autoskip_panel.add(autoskip_padding_start, "cell 1 " + gy);

      gy++;
      autoskip_panel.add(autoskip_padding_stop_label, "cell 0 " + gy);
      autoskip_panel.add(autoskip_padding_stop, "cell 1 " + gy);

      gy++;
      autoskip_panel.add(autoskip_chan_off_label, "cell 0 " + gy);
      autoskip_panel.add(autoskip_chan_off, "cell 1 " + gy);

      gy++;
      autoskip_panel.add(autoskip_chan_on_label, "cell 0 " + gy);
      autoskip_panel.add(autoskip_chan_on, "cell 1 " + gy);

      // Files panel
      JPanel files_panel = new JPanel(new MigLayout("gapx 5, gapy 5"));

      // Remove .TiVo after file decrypt
      gy=0;
      files_panel.add(remove_tivo, "cell 0 " + gy);

      // Remove Ad Detect files after Ad Cut
      files_panel.add(remove_comcut, "cell 1 " + gy);

      // Remove .mpg file after Ad Cut
      gy++;
      files_panel.add(remove_comcut_mpeg, "cell 0 " + gy);

      // Remove Ad Detect files after Ad Cut
      // Remove .mpg file after encode
      files_panel.add(remove_mpeg, "cell 1 " + gy);

      // QSFixBackupMpegFile
      gy++;
      files_panel.add(QSFixBackupMpegFile, "cell 0 " + gy);

      // download_check_length
      files_panel.add(download_check_length, "cell 1 " + gy);

      // Check Available Disk Space
      gy++;
      files_panel.add(check_space, "cell 0 " + gy);

      // Min requested space
      JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
      p.add(disk_space_label);
      p.add(disk_space);
      files_panel.add(p, "cell 1 " + gy);

      // File naming
      gy++;
      files_panel.add(file_naming_label, "cell 0 " + gy);
      files_panel.add(file_naming, "cell 1 " + gy);

      gy++;
      files_panel.add(available_keywords_label, "cell 0 " + gy);
      files_panel.add(keywords, "cell 1 " + gy);

      // FILES Default Path
      gy++;
      files_panel.add(files_path_label, "cell 0 " + gy);
      files_panel.add(files_path, "cell 1 " + gy);

      // .TiVo Output Dir
      gy++;
      files_panel.add(tivo_output_dir_label, "cell 0 " + gy);
      files_panel.add(tivo_output_dir, "cell 1 " + gy);

      // .mpg Output Dir
      gy++;
      files_panel.add(mpeg_output_dir_label, "cell 0 " + gy);
      files_panel.add(mpeg_output_dir, "cell 1 " + gy);

      // qsfixDir
      gy++;
      files_panel.add(qsfixDir_label, "cell 0 " + gy);
      files_panel.add(qsfixDir, "cell 1 " + gy);

      // .mpg Cut Dir
      gy++;
      files_panel.add(mpeg_cut_dir_label, "cell 0 " + gy);
      files_panel.add(mpeg_cut_dir, "cell 1 " + gy);

      // Encode output dir
      gy++;
      files_panel.add(encode_output_dir_label, "cell 0 " + gy);
      files_panel.add(encode_output_dir, "cell 1 " + gy);

      // autoLogSizeMB
      gy++;
      files_panel.add(autoLogSizeMB_label, "cell 0 " + gy);
      files_panel.add(autoLogSizeMB, "cell 1 " + gy);

      // OverwriteFiles
      gy++;
      files_panel.add(OverwriteFiles, "cell 0 " + gy);

      // DeleteFailedDownloads
      files_panel.add(DeleteFailedDownloads, "cell 1 " + gy);

      // Programs Panel
      JPanel programs_panel = new JPanel(new MigLayout("gapx 5, gapy 5", "[][grow]", ""));

      gy=0;

      // tivodecode
      gy++;
      programs_panel.add(tivodecode_label, "cell 0 " + gy);
      programs_panel.add(tivodecode, "cell 1 " + gy + ", growx");

      // dsd
      if (config.OS.equals("windows")) {
         gy++;
         programs_panel.add(dsd_label, "cell 0 " + gy);
         programs_panel.add(dsd, "cell 1 " + gy + ", growx");
      }

      // mencoder
      gy++;
      programs_panel.add(mencoder_label, "cell 0 " + gy);
      programs_panel.add(mencoder, "cell 1 " + gy + ", growx");

      // ffmpeg
      gy++;
      programs_panel.add(ffmpeg_label, "cell 0 " + gy);
      programs_panel.add(ffmpeg, "cell 1 " + gy + ", growx");

      // handbrake
      gy++;
      programs_panel.add(handbrake_label, "cell 0 " + gy);
      programs_panel.add(handbrake, "cell 1 " + gy + ", growx");

      // comskip
      gy++;
      programs_panel.add(comskip_label, "cell 0 " + gy);
      programs_panel.add(comskip, "cell 1 " + gy + ", growx");

      // comskip.ini
      gy++;
      programs_panel.add(comskip_ini_label, "cell 0 " + gy);
      programs_panel.add(comskip_ini, "cell 1 " + gy + ", growx");

      // t2extract
      gy++;
      programs_panel.add(t2extract_label, "cell 0 " + gy);
      programs_panel.add(t2extract, "cell 1 " + gy + ", growx");

      // ccextractor (intentionally disabled for now)
      //gy++;
      //programs_panel.add(ccextractor_label, "cell 0 " + gy);
      //programs_panel.add(ccextractor, "cell 1 " + gy + ", growx");

      // AtomicParsley
      gy++;
      programs_panel.add(AtomicParsley_label, "cell 0 " + gy);
      programs_panel.add(AtomicParsley, "cell 1 " + gy + ", growx");

      // mediainfo
      gy++;
      programs_panel.add(mediainfo_label, "cell 0 " + gy);
      programs_panel.add(mediainfo, "cell 1 " + gy + ", growx");

      // custom command
      gy++;
      programs_panel.add(customCommand_label, "cell 0 " + gy);
      programs_panel.add(customCommand, "cell 1 " + gy + ", growx");

      // customFiles
      gy++;
      programs_panel.add(customFiles_label, "cell 0 " + gy);
      programs_panel.add(customFiles, "cell 1 " + gy);

      // Program_options Panel
      JPanel program_options_panel = new JPanel(new MigLayout("gapx 5, gapy 5"));

      // MAK
      gy=0;
      program_options_panel.add(MAK_label, "cell 0 " + gy);
      program_options_panel.add(MAK, "cell 1 " + gy);

      // active job limit
      gy++;
      program_options_panel.add(active_job_limit_label, "cell 0 " + gy);
      program_options_panel.add(active_job_limit, "cell 1 " + gy);

      // cpu_cores
      gy++;
      program_options_panel.add(cpu_cores_label, "cell 0 " + gy);
      program_options_panel.add(cpu_cores, "cell 1 " + gy);

      // t2extract_args
      //gy++;
      //program_options_panel.add(t2extract_args_label, "cell 0 " + gy);
      //program_options_panel.add(t2extract_args, "cell 1 " + gy);

      // download_tries
      gy++;
      program_options_panel.add(download_tries_label, "cell 0 " + gy);
      program_options_panel.add(download_tries, "cell 1 " + gy);

      // download_retry_delay
      gy++;
      program_options_panel.add(download_retry_delay_label, "cell 0 " + gy);
      program_options_panel.add(download_retry_delay, "cell 1 " + gy);

      // download_delay
      gy++;
      program_options_panel.add(download_delay_label, "cell 0 " + gy);
      program_options_panel.add(download_delay, "cell 1 " + gy);

      // metadata_files
      gy++;
      program_options_panel.add(metadata_files_label, "cell 0 " + gy);
      program_options_panel.add(metadata_files, "cell 1 " + gy);

      // metadata_entries
      gy++;
      program_options_panel.add(metadata_entries_label, "cell 0 " + gy);
      program_options_panel.add(metadata_entries, "cell 1 " + gy);

      // TivoWebPlusDelete
      gy++;
      program_options_panel.add(TivoWebPlusDelete, "cell 0 " + gy);

      // rpcDelete
      program_options_panel.add(rpcDelete, "cell 1 " + gy);

      // download_time_estimate
      gy++;
      program_options_panel.add(download_time_estimate, "cell 0 " + gy);

      // rpcOld
      program_options_panel.add(rpcOld, "cell 1 " + gy);

      // combine_download_decrypt
      gy++;
      program_options_panel.add(combine_download_decrypt, "cell 0 " + gy);

      // single_download
      program_options_panel.add(single_download, "cell 1 " + gy);

      // rpcnpl
      gy++;
      program_options_panel.add(rpcnpl, "cell 0 " + gy);

      // persistJobQueue
      program_options_panel.add(persistQueue, "cell 1 " + gy);

      // comskip_review
      gy++;
      program_options_panel.add(comskip_review, "cell 0 " + gy);

      // tivolibreDecrypt
      program_options_panel.add(tivolibreDecrypt, "cell 1 " + gy);

      // tivolibreCompat
      gy++;
      program_options_panel.add(tivolibreCompat, "cell 0 " + gy);

      if (config.OS.equals("windows")) {
         // DsdDecrypt
         program_options_panel.add(DsdDecrypt, "cell 1 " + gy);
      }

      // Visual Panel
      JPanel visual_panel = new JPanel(new MigLayout("gapx 5, gapy 5", "[][grow]", ""));

      // lookAndFeel
      gy=0;
      visual_panel.add(lookAndFeel_label, "cell 0 " + gy);
      visual_panel.add(lookAndFeel, "cell 1 " + gy);

      // FontSize
      gy++;
      visual_panel.add(FontSize_label, "cell 0 " + gy);
      visual_panel.add(FontSize, "cell 1 " + gy);

      // toolTipsDelay
      gy++;
      visual_panel.add(toolTipsDelay_label, "cell 0 " + gy);
      visual_panel.add(toolTipsDelay, "cell 1 " + gy);

      // toolTipsTimeout
      gy++;
      visual_panel.add(toolTipsTimeout_label, "cell 0 " + gy);
      visual_panel.add(toolTipsTimeout, "cell 1 " + gy);

      // MinChanDigits
      gy++;
      visual_panel.add(MinChanDigits_label, "cell 0 " + gy);
      visual_panel.add(MinChanDigits, "cell 1 " + gy);

      // toolTips
      gy++;
      visual_panel.add(toolTips, "cell 0 " + gy);

      // jobMonitorFullPaths
      gy++;
      visual_panel.add(jobMonitorFullPaths, "cell 0 " + gy);

      // HideProtectedFiles
      gy++;
      visual_panel.add(HideProtectedFiles, "cell 0 " + gy);

      // TiVoSort
      gy++;
      visual_panel.add(TiVoSort, "cell 0 " + gy);

      // tableColAutoSize
      gy++;
      visual_panel.add(tableColAutoSize, "cell 0 " + gy);

      // showHistoryInTable
      gy++;
      visual_panel.add(showHistoryInTable, "cell 0 " + gy);

      // slingBox
      gy++;
      visual_panel.add(slingBox, "cell 0 " + gy);

      // web_query
      gy++;
      visual_panel.add(web_query_label, "cell 0 " + gy);
      visual_panel.add(web_query, "cell 1 " + gy + ", growx");

      // web_browser - not used for Mac or Windows
      if ( config.OS.equals("other")) {
         gy++;
         visual_panel.add(web_browser_label, "cell 0 " + gy);
         visual_panel.add(web_browser, "cell 1 " + gy + ", growx");
      }

      // Web Panel
      JPanel web_panel = new JPanel(new MigLayout("gapx 5, gapy 5", "[][grow]", ""));

      // httpserver_enable
      gy=0;
      web_panel.add(httpserver_enable, "cell 0 " + gy);

      // httpserver_port
      gy++;
      web_panel.add(httpserver_port_label, "cell 0 " + gy);
      web_panel.add(httpserver_port, "cell 1 " + gy);

      // httpserver_cache
      gy++;
      web_panel.add(httpserver_cache_label, "cell 0 " + gy);
      web_panel.add(httpserver_cache, "cell 1 " + gy);

      // httpserver_enable
      gy++;
      web_panel.add(httpserver_share_filter, "cell 0 " + gy);

      // shares JComboBox
      gy++;
      web_panel.add(shares_label, "cell 0 " + gy);
      web_panel.add(shares, "cell 1 " + gy);

      // DEL button
      web_panel.add(share_del, "cell 4 " + gy);

      // Share name
      gy++;
      web_panel.add(share_name_label, "cell 0 " + gy);
      web_panel.add(share_name, "cell 1 " + gy);

      // ADD button
      web_panel.add(share_add, "cell 4 " + gy);

      // Share dir
      gy++;
      web_panel.add(share_dir_label, "cell 0 " + gy);
      web_panel.add(share_dir, "cell 1 " + gy);

      // VRD Panel
      JPanel vrd_panel = new JPanel(new MigLayout("gapx 5, gapy 5"));

      // VRD flag
      gy=0;
      vrd_panel.add(VRD, "cell 1 " + gy);

      // VRDexe
      gy++;
      vrd_panel.add(VRDexe_label, "cell 0 " + gy);
      vrd_panel.add(VRDexe, "cell 1 " + gy);

      // UseAdscan
      gy++;
      vrd_panel.add(UseAdscan, "cell 1 " + gy);

      // VrdReview
      gy++;
      vrd_panel.add(VrdReview, "cell 1 " + gy);

      // VrdReview_noCuts
      gy++;
      vrd_panel.add(VrdReview_noCuts, "cell 1 " + gy);

      // VrdQsFilter
      gy++;
      vrd_panel.add(VrdQsFilter, "cell 1 " + gy);

      // VrdDecrypt
      gy++;
      vrd_panel.add(VrdDecrypt, "cell 1 " + gy);

      // VrdEncode
      gy++;
      vrd_panel.add(VrdEncode, "cell 1 " + gy);

      // VrdCombineCutEncode
      gy++;
      vrd_panel.add(VrdCombineCutEncode, "cell 1 " + gy);

      // VrdQsfixMpeg2ps
      gy++;
      vrd_panel.add(VrdQsfixMpeg2ps, "cell 1 " + gy);

      // VrdAllowMultiple
      gy++;
      vrd_panel.add(VrdAllowMultiple, "cell 1 " + gy);

      // VrdOneAtATime
      gy++;
      vrd_panel.add(VrdOneAtATime, "cell 1 " + gy);

      // pyTivo Panel
      /*GridPane pyTivo_panel = new GridPane();
      pyTivo_panel.setAlignment(Pos.CENTER);
      pyTivo_panel.setVgap(5);
      pyTivo_panel.setHgap(5);
      pyTivo_panel.getColumnConstraints().addAll(util.cc_none(), util.cc_stretch());
      pyTivo_panel.setPadding(new Insets(5,5,5,5)); // top, right, bottom, left

      // pyTivo_config
      gy=0;
      pyTivo_panel.add(pyTivo_config_label, 0, gy);
      pyTivo_panel.add(pyTivo_config, 1, gy);

      // pyTivo_host
      gy++;
      pyTivo_panel.add(pyTivo_host_label, 0, gy);
      pyTivo_panel.add(pyTivo_host, 1, gy);

      // pyTivo_tivo
      gy++;
      pyTivo_panel.add(pyTivo_tivo_label, 0, gy);
      pyTivo_panel.add(pyTivo_tivo, 1, gy);

      // pyTivo_files
      gy++;
      pyTivo_panel.add(pyTivo_files_label, 0, gy);
      pyTivo_panel.add(pyTivo_files, 1, gy);*/

      // Common panel
      JPanel common_panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 50, 0));
      // OK and CANCEL buttons
      common_panel.add(OK);
      common_panel.add(CANCEL);

      // Tabbed panel
      tabbed_panel = new JTabbedPane();
      addTabPane("File Settings", files_panel);
      addTabPane("Programs", programs_panel);
      addTabPane("Program Options", program_options_panel);
      addTabPane("Tivos", tivo_panel);
      addTabPane("Visual", visual_panel);
      addTabPane("Web", web_panel);
      if (config.OS.equals("windows"))
         addTabPane("VideoRedo", vrd_panel);
      //addTabPane("pyTivo", pyTivo_panel);
      addTabPane("Autotune", autotune_panel);
      addTabPane("AutoSkip", autoskip_panel);

      // Main panel
      JPanel main_panel = new JPanel();
      main_panel.setLayout(new BoxLayout(main_panel, BoxLayout.Y_AXIS));
      main_panel.add(tabbed_panel);
      main_panel.add(common_panel);

      // create dialog window
      dialog = new JDialog(frame);
      dialog.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent arg0) {
            pos_x = dialog.getX(); pos_y = dialog.getY();
         }
      });
      SwingUtil.loadIcons(dialog);
      dialog.setTitle("kmttg configuration");
      JPanel root = new JPanel(new BorderLayout());
      root.add(main_panel, BorderLayout.CENTER);
      dialog.getContentPane().add(root);
      dialog.setResizable(false);
      dialog.pack();
      dialog.setLocationRelativeTo(frame);
  }

   // Add a new tab pane
   public static void addTabPane(String name, JPanel content) {
      tabbed_panel.addTab(name, content);
   }
   public static void setToolTips() {
      debug.print("");
      VRDexe.setToolTipText(getToolTip("VRDexe"));
      tivo_name.setToolTipText(getToolTip("tivo_name"));
      tivo_ip.setToolTipText(getToolTip("tivo_ip"));
      share_name.setToolTipText(getToolTip("share_name"));
      share_dir.setToolTipText(getToolTip("share_dir"));
      autotune_enabled.setToolTipText(getToolTip("autotune_enabled"));
      autotune_channel_interval.setToolTipText(getToolTip("autotune_channel_interval"));
      autotune_button_interval.setToolTipText(getToolTip("autotune_button_interval"));
      autotune_chan1.setToolTipText(getToolTip("autotune_chan1"));
      autotune_chan2.setToolTipText(getToolTip("autotune_chan2"));
      autotune_tivoName.setToolTipText(getToolTip("autotune_tivoName"));
      add.setToolTipText(getToolTip("add")); 
      del.setToolTipText(getToolTip("del"));
      domain_token.setToolTipText(getToolTip("domain_token")); 
      share_add.setToolTipText(getToolTip("share_add")); 
      share_del.setToolTipText(getToolTip("share_del")); 
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
      tivolibreDecrypt.setToolTipText(getToolTip("tivolibreDecrypt"));
      tivolibreCompat.setToolTipText(getToolTip("tivolibreCompat"));
      httpserver_enable.setToolTipText(getToolTip("httpserver_enable"));
      httpserver_share_filter.setToolTipText(getToolTip("httpserver_share_filter"));
      VrdEncode.setToolTipText(getToolTip("VrdEncode"));
      VrdAllowMultiple.setToolTipText(getToolTip("VrdAllowMultiple"));
      VrdCombineCutEncode.setToolTipText(getToolTip("VrdCombineCutEncode"));
      VrdQsfixMpeg2ps.setToolTipText(getToolTip("VrdQsfixMpeg2ps"));
      VrdOneAtATime.setToolTipText(getToolTip("VrdOneAtATime"));
      TivoWebPlusDelete.setToolTipText(getToolTip("TivoWebPlusDelete"));
      rpcDelete.setToolTipText(getToolTip("rpcDelete"));
      rpcOld.setToolTipText(getToolTip("rpcOld"));
      HideProtectedFiles.setToolTipText(getToolTip("HideProtectedFiles"));
      TiVoSort.setToolTipText(getToolTip("TiVoSort"));
      OverwriteFiles.setToolTipText(getToolTip("OverwriteFiles"));
      DeleteFailedDownloads.setToolTipText(getToolTip("DeleteFailedDownloads"));
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
      ffmpeg.setToolTipText(getToolTip("ffmpeg"));
      mediainfo.setToolTipText(getToolTip("mediainfo"));
      mencoder.setToolTipText(getToolTip("mencoder"));
      handbrake.setToolTipText(getToolTip("handbrake"));
      comskip.setToolTipText(getToolTip("comskip"));
      comskip_ini.setToolTipText(getToolTip("comskip_ini"));
      t2extract.setToolTipText(getToolTip("t2extract"));
      //t2extract_args.setToolTipText(getToolTip("t2extract_args"));
      ccextractor.setToolTipText(getToolTip("ccextractor"));
      AtomicParsley.setToolTipText(getToolTip("AtomicParsley"));
      wan_http_port.setToolTipText(getToolTip("wan_http_port"));
      wan_https_port.setToolTipText(getToolTip("wan_https_port"));
      wan_rpc_port.setToolTipText(getToolTip("wan_rpc_port"));
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
      toolTipsDelay.setToolTipText(getToolTip("toolTipsDelay")); 
      toolTipsTimeout.setToolTipText(getToolTip("toolTipsTimeout")); 
      cpu_cores.setToolTipText(getToolTip("cpu_cores"));
      download_tries.setToolTipText(getToolTip("download_tries"));
      download_retry_delay.setToolTipText(getToolTip("download_retry_delay"));
      download_delay.setToolTipText(getToolTip("download_delay"));
      autoskip_enabled.setToolTipText(getToolTip("autoskip_enabled"));
      autoskip_import.setToolTipText(getToolTip("autoskip_import"));
      autoskip_cutonly.setToolTipText(getToolTip("autoskip_cutonly"));
      autoskip_prune.setToolTipText(getToolTip("autoskip_prune"));
      autoskip_batch_standby.setToolTipText(getToolTip("autoskip_batch_standby"));
      autoskip_indicate_skip.setToolTipText(getToolTip("autoskip_indicate_skip"));
      autoskip_jumpToEnd.setToolTipText(getToolTip("autoskip_jumpToEnd"));
      autoskip_padding_start.setToolTipText(getToolTip("autoskip_padding_start"));
      autoskip_padding_stop.setToolTipText(getToolTip("autoskip_padding_stop"));
      autoskip_chan_off.setToolTipText(getToolTip("autoskip_chan_off"));
      autoskip_chan_on.setToolTipText(getToolTip("autoskip_chan_on"));
      metadata_entries.setToolTipText(getToolTip("metadata_entries"));
      httpserver_port.setToolTipText(getToolTip("httpserver_port"));
      httpserver_cache.setToolTipText(getToolTip("httpserver_cache"));
      autoLogSizeMB.setToolTipText(getToolTip("autoLogSizeMB"));
      web_query.setToolTipText(getToolTip("web_query"));
      web_browser.setToolTipText(getToolTip("web_browser"));
      tivo_username.setToolTipText(getToolTip("tivo_username"));
      tivo_password.setToolTipText(getToolTip("tivo_password"));
      //pyTivo_host.setToolTipText(getToolTip("pyTivo_host"));
      //pyTivo_config.setToolTipText(getToolTip("pyTivo_config"));
      //pyTivo_tivo.setToolTipText(getToolTip("pyTivo_tivo"));
      //pyTivo_files.setToolTipText(getToolTip("pyTivo_files"));
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
      else if (component.equals("share_name")) {
         text =  "<b>Share Name</b><br>";
         text += "Enter the name you want to use for a video share.<br>";
         text += "These video shares are used by <b>Share Browser</b> and <b>Video Streaming-Browse Files</b><br>";
         text += "web server pages. If you don't define any custom shares then kmttg will use some default ones instead.<br>";
         text += "Enter corresponding <b>Share Directory</b> below and then click on <b>ADD</b> button.<br>";
         text += "NOTE: When changing these shares you will need to restart kmttg so the web server<br>";
         text += "will pick up the changes.";
      }
      else if (component.equals("share_dir")) {
         text =  "<b>Share Directory</b><br>";
         text += "Enter corresponding <b>Share Name</b> above and then click on <b>ADD</b> button.";
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
         text += "channel that you don't subscribe to so that it relieves the load on your TiVo CPU.<br>";
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
      else if (component.equals("del")) {
          text =  "<b>DEL</b><br>";
          text += "Remove currently selected entry in <b>Tivos</b> list.";
       }
      else if (component.equals("domain_token")) {
         text =  "<b>Refresh Domain token</b><br>";
         text += "Force a refresh of your domain token. This uses the tivo.com username and password.";
      }
      else if (component.equals("share_del")) {
         text =  "<b>DEL</b><br>";
         text += "Remove currently selected entry in <b>Shares</b> list.";
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
         text += "<b>IMPORTANT: When done press <b>Esc</b> keyboard button to close comskip GUI.</b><br>";
         text += "<b>Just closing the window by clicking on the X will NOT terminate comskip</b>.";
      }
      else if (component.equals("VrdReview_noCuts")) {
         text =  "<b>Bring up VideoRedo GUI to make manual cuts</b><br>";
         text += "If you have VideoRedo and have configured kmttg with the installation path<br>";
         text += "to VideoRedo, when this option is enabled kmttg will start VideoRedo GUI<br>";
         text += "to allow you create the commercial edited mpeg file manually. kmttg will wait<br>";
         text += "until you close the VideoRedo GUI before proceeding to next task.<br>";
         text += "<b>NOTE: Be sure to specify the output file using the VRD default ' (02).mpg'</b><br>";
         text += "<b>suffix or using the kmttg conventional '_cut.mpg' suffix when saving output file</b>.<br>";
         text += "NOTE: When using this option you normally want to disable <b>Ad Detect</b> task<br>";
         text += "and enable <b>Ad Cut</b> task.<br>";
         text += "NOTE: The .Vprj file will be deleted following completion of this task unless there<br>";
         text += "is an upcoming <b>encode</b> task using a VRD encoding profile.";
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
         text =  "<b>Decrypt using VideoRedo</b><br>";
         text += "If you have VideoRedo and have configured kmttg with the installation path<br>";
         text += "to VideoRedo, when this option is enabled kmttg will use VideoRedo QSFix task<br>";
         text += "to decrypt .TiVo files instead of other methods.<br>";
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
      else if (component.equals("tivolibreDecrypt")) {
         text =  "<b>Decrypt using tivolibre instead of tivodecode</b><br>";
         text += "Enable this option to decrypt .TiVo files using Java tivolibre instead of the standard<br>";
         text += "<b>tivodecode</b> program. This is useful for cases when the .TiVo files are in a format that<br>";
         text += "tivodecode cannot decrypt such as for Transport Stream (TS) format .TiVo files.";
      }
      else if (component.equals("tivolibreCompat")) {
         text =  "<b>tivolibre DirectShow compatibility mode</b><br>";
         text += "If enabled then tivolibre DirectShow compatiblity will be used which tries to keep the<br>";
         text += "resulting decrypted file binary compatible with a DirectShow decryption, complete with<br>";
         text += "null and unencrypted packets.<br>";
         text += "This is especially useful for debugging tivolibre issues and comparing vs DirectShowDump.";
      }
      else if (component.equals("httpserver_enable")) {
         text =  "<b>Enable web server</b><br>";
         text += "<b>EXPERIMENTAL</b><br>";
         text += "Enabling web browser allows you to interact with kmttg via any web browser with<br>";
         text += "a subset of kmttg capabilities and capability to stream videos from computer<br>";
         text += "running kmttg to any browser. This is experimental because some functions are<br>";
         text += "still a work in progress and not robust, especially video streaming.<br>";
         text += "NOTE: TiVos listed in web browser are intentionally restricted to series 4 or later only.";
      }
      else if (component.equals("httpserver_port")) {
         text =  "<b>web server port</b><br>";
         text += "Port to use for web server. See web server setting and associated tooltip above.<br>";
         text += "NOTE: In your router configuration you can setup WAN port forwarding such as to enable<br>";
         text += "access to web server from outside your home, so if you leave kmttg running then<br>";
         text += "you can access the web server capabilities from anywhere.";
      }
      else if (component.equals("httpserver_cache")) {
         text =  "<b>web server cache dir</b><br>";
         text += "Directory to use for web server cache files. These files are created when using<br>";
         text += "web server <b>Video Streaming</b> page to transcode files to HLS. Note that since these<br>";
         text += "are video files it's possible a lot of space may be needed, so make sure there is plenty<br>";
         text += "of space where you define this directory.";
      }
      else if (component.equals("httpserver_share_filter")) {
         text =  "<b>Share browser show video files only</b><br>";
         text += "For <b>Share Browser</b> page only show video files if this option is enabled.";
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
      else if (component.equals("VrdOneAtATime")) {
          text =  "<b>Only allow 1 VRD job at a time</b><br>";
          text += "If this option is enabled then only 1 VideoRedo job will be allowed to run at a time.";
       }
      else if (component.equals("TSDownload")) {
         text =  "<b>Download TiVo files in Transport Stream format</b><br>";
         text += "For TiVo software that properly supports it, this forces TiVo file downloads to use<br>";
         text += "the faster Transport Stream format instead of the default Program Stream format.<br>";
         text += "NOTE: Only Series 4 or later TiVos support this format and this will have no effect on other TiVos.<br>";
         text += "<b>NOTE: 'tivodecode' cannot properly decrypt TS TiVo files, so if you enable this option</b><br>";
         text += "<b>you should also turn on option to use 'tivolibre' or 'DirectShowDump' or 'VideoRedo' for the</b><br>";
         text += "<b>'decrypt' task</b>.<br>";
         text += "<b>NOTE: Enabling this option is required for H.264 downloads to work properly</b>.";
      }
      else if (component.equals("TivoWebPlusDelete")) {
         text =  "<b>Enable TivoWebPlus Delete task</b><br>";
         text += "If you have TivoWebPlus configured on your TiVo(s) then if you enable this option<br>";
         text += "an optional <b>TWP Delete</b> task is made available in the kmttg GUI or auto transfers<br>";
         text += "task set. When task is enabled, a TivoWebPlus http call to delete show on TiVo will be<br>";
         text += "issued following successful decrypt of a downloaded .TiVo file.<br>";
         text += "NOTE: Once you set and save this option you must restart kmttg to see the change.";

      }
      else if (component.equals("rpcDelete")) {
         text =  "<b>Enable rpc style delete task</b><br>";
         text += "For Series 4 TiVos if you have <b>Network Remote</b> option enabled and this option<br>";
         text += "enabled an optional <b>rpc Delete</b> task is made available in the kmttg GUI or auto transfers<br>";
         text += "task set. When task is enabled, rpc communications protocol is used to delete show on TiVo<br>";
         text += "following a successful decrypt of a downloaded .TiVo file.<br>";
         text += "<b>NOTE: Once you set and save this option you must restart kmttg to see the change.</b>";

      }
      else if (component.equals("rpcOld")) {
         text =  "<b>Use old RPC schema version for older TiVo software</b><br>";
         text += "If you are getting <b>'Unsupported schema version'</b> RPC error messages then<br>";
         text += "it likely means your TiVo is running old software (probably a cable co supplied TiVo),<br>";
         text += "and you should enable this option to force kmttg to use older RPC schema version<br>";
         text += "for RPC operations.";
      }
      else if (component.equals("HideProtectedFiles")) {
         text = "<b>Do not show copy protected files in table</b><br>";
         text += "If this option is enabled then copy protected TiVo shows are not displayed in the<br>";
         text += "TiVo Now Playing lists.";
      }
      else if (component.equals("TiVoSort")) {
         text = "<b>Sort table show titles as a TiVo does</b><br>";
         text += "If this option is enabled then table show column sort behaves as a TiVo<br>";
         text += "alphabetical sort by ignoring leading articles (A , An , & The).";
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
      else if (component.equals("combine_download_decrypt")) {
         text =  "<b>Combine download and decrypt</b><br>";
         text += "If this option is enabled then kmttg will try to combine the download and decrypt tasks into<br>";
         text += "a single step instead of the default 2 step process (skipping intermediate TiVo file generation).<br>";
         text += "NOTE: You still need to enable both <b>download</b> and <b>decrypt</b> tasks for a show for this<br>";
         text += "to apply - if you do not enable <b>decrypt</b> task then still only download to TiVo file is performed.<br>";
         text += "NOTE: This option is not relevant if using VideoRedo qsfix or DirectShow dump decrypt which must<br>";
         text += "be performed separately from downloads.";
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
         text += "listings followed by <b>remote</b> call when refreshing NPL tables.";
        }
      else if (component.equals("enableRpc")) {
         text =  "<b>Enable rpc style communications with this TiVo</b><br>";
         text += "If this option is enabled then kmttg will use rpc style communications with the TiVo to enable<br>";
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
         text += "<b>NOTE: Several special characters are stripped or mapped from file names to avoid</b><br>";
         text += "<b>potential problems with the various helper tools being used.</b><br>";
         text += "<b>Consult the kmttg documentation on this option for details on that</b>";
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
         text += "This defines temporary location where qsfix output file will be saved to.<br>";
         text += "NOTE: Make sure to have plenty of disk space available at this location.<br>";
         text += "NOTE: This is NOT the final destination of qsfix file, merely the temporary work area.<br>.";
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
         text += "<b>NOTE: This requires you have at least a partial install of TiVo Desktop as well with</b><br>";
         text += "<b>TiVoDirectShowFilter.dll installed</b><br>";
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
         text += "<b>NOTE: This binary should be the Command Line Interface (CLI) version of</b><br>";
         text += "<b>mediainfo, not the graphical (GUI) version.</b>";
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
      else if (component.equals("VRDexe")) {
          text =  "<b>VideoRedo executable</b><br>";
          text += "Specify here the full path to VideoRedo executable.<br>";
          text += "This is used for <b>vrdreview</b> task for manually checking/editing commercials.<br>";
          text += "This path needs to be full path to the VideoRedo .exe file.<br>";
          text += "If not configured here, kmttg will attempt to find it automatically.<br>";
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
      else if (component.equals("wan_rpc_port")) {
         text =  "<b>wan rpc port</b><br>";
         text += "<b>Advanced Setting - for normal use leave this setting empty</b>.<br>";
         text += "Set this option only if you plan to use kmttg over a WAN instead of your local LAN.<br>";
         text += "By default http port 1413 for rpc interface to Tivos on the LAN, but from WAN side<br>";
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
         text += "Enable or disable display of these mouse over popup toolTip messages.<br>";
         text += "<b>NOTE: When toggling this setting you have to re-start kmttg for it to take effect.</b>";
      }
      else if (component.equals("slingBox")) {
         text =  "<b>Show Slingbox capture tab</b><br>";
         text += "Enable or disable display of Slingbox capture tab.";
      }
      else if (component.equals("tableColAutoSize")) {
         text =  "<b>Auto size table column widths</b><br>";
         text += "If enabled then automatically size table column widths.<br>";
         text += "Else manually set table column widths will be saved/restored between kmttg sessions.";
      }
      else if (component.equals("jobMonitorFullPaths")) {
         text =  "<b>Show full paths in Job Monitor</b><br>";
         text += "Enable or disable display of full paths in Job Monitor OUTPUT column.";
      }
      else if (component.equals("toolTipsDelay")) {
         text =  "<b>toolTip open delay (secs)</b><br>";
         text += "Time in seconds to display a toolTip message. You need to hover over a widget for this<br>";
         text += "many seconds before the tooltip bubble will appear.<br>";
         text += "NOTE: Changing this setting will only take effect after restarting kmttg.";
      }
      else if (component.equals("toolTipsTimeout")) {
         text =  "<b>toolTip timeout (secs)</b><br>";
         text += "Time in seconds to timeout display of a toolTip message.<br>";
         text += "NOTE: Changing this setting will only take effect after restarting kmttg.";
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
      else if (component.equals("autoskip_doc")) {
         text =  "<b>Documentation</b><br>";
         text += "Click to bring up Wiki documentation on AutoSkip.";
      }
      else if (component.equals("autoskip_enabled")) {
         text =  "<b>Enable AutoSkip functionality</b><br>";
         text += "Enable AutoSkip functionality for series 4 and later TiVos/Minis.<br>";
         text += "NOTE: You should re-start kmttg after changing this setting to see the changes in the GUI.";
      }
      else if (component.equals("autoskip_import")) {
         text =  "<b>Automatically Import to Skip Table after Ad Detect</b><br>";
         text += "If enabled, following Ad Detect or Ad Review job kmttg will attempt to automatically<br>";
         text += "import the cuts file into AutoSkip table";
      }
      else if (component.equals("autoskip_cutonly")) {
         text =  "<b>Only run Ad Skip/Ad Detect for shows with AutoSkip data</b><br>";
         text += "If enabled, only run Ad Skip and Ad Detect tasks for shows that have AutoSkip data;<br>";
         text += "shows without AutoSkip data will not schedule Ad Skip and Ad Detect tasks.";
      }
      else if (component.equals("autoskip_prune")) {
         text =  "<b>Prune Skip Table automatically after NPL refresh</b><br>";
         text += "If enabled, following an NPL tab refresh any shows that have been deleted from the TiVo<br>";
         text += "that have corresponding AutoSkip Table entries will be deleted from AutoSkip table automatically.";
      }
      else if (component.equals("autoskip_batch_standby")) {
         text =  "<b>Set standby mode after batch AutoSkip from SkipMode</b><br>";
         text += "If enabled, following a batch mode <b>AutoSkip from SkipMode</b> run put the TiVo in standby mode.";
      }
      else if (component.equals("autoskip_indicate_skip")) {
         text =  "<b>Indicate with play when skipping</b><br>";
         text += "If enabled, play command is sent to TiVo following a skip as indication of a skip.";
      }
      else if (component.equals("autoskip_jumpToEnd")) {
         text =  "<b>Jump to end of recording when last skip block entered</b><br>";
         text += "If enabled, when the last skip block is entered, automatically jump to the end of the recording.<br>";
         text += "When used with TiVo's \"Play all recordings in this group\", this provides a seemless viewing<br>";
         text += "experience of all recordings in the group.";
      }
      else if (component.equals("autoskip_padding_start")) {
         text =  "<b>AutoSkip start point padding in msecs</b><br>";
         text += "During AutoSkip play this padding will be applied to show start points.<br>";
         text += "This allows you to add an offset to all show start points according to your preference.<br>";
         text += "NOTE: Enter time in msecs = seconds * 1000 (so for 2.5 seconds enter 2500)<br>";
         text += "new start point = start point + padding<br>";
         text += "=> Value of zero means use originally detected/configured start points.<br>";
         text += "=> Use positive value if you want to start later (more likely to start in show).<br>";
         text += "=> Use negative value if you want to start sooner (more likely to start in commercial).<br>";
         text += "If using AutoSkip from SkipMode often a zero value is appropriate here since<br>";
         text += "detected start points tend to be pretty accurate.";
      }
      else if (component.equals("autoskip_padding_stop")) {
         text =  "<b>AutoSkip end point padding in msecs</b><br>";
         text += "During AutoSkip play this padding will be applied to show end points.<br>";
         text += "This allows you to add an offset to all show end points according to your preference.<br>";
         text += "NOTE: Enter time in msecs = seconds * 1000 (so for -2.5 seconds enter -2500)<br>";
         text += "new end point = end point + padding<br>";
         text += "=> Value of zero means use originally detected/configured end points.<br>";
         text += "=> Use positive value if you want to end later (more likely to end in commercial).<br>";
         text += "=> Use negative value if you want to end sooner (more likely to end in show).<br>";
         text += "If using AutoSkip from SkipMode often a negative value is appropriate here if you<br>";
         text += "find you are getting too many starts of commercials during AutoSkip play.";
      }
      else if (component.equals("autoskip_chan_off")) {
         text =  "<b>AutoSkip service disable channel</b><br>";
         text += "With AutoSkip service enabled you can tune to this channel in live TV to<br>";
         text += "suspend AutoSkip service from a TiVo. This does not disable AutoSkip<br>";
         text += "service in kmttg, but suspends AutoSkip service functionality which<br>";
         text += "can be resumed again by tuning to configured <b>AutoSkip service enable channel</b>.";
      }
      else if (component.equals("autoskip_chan_on")) {
         text =  "<b>AutoSkip service enable channel</b><br>";
         text += "With AutoSkip service enabled you can tune to this channel in live TV to<br>";
         text += "resume AutoSkip service from a TiVo. This resumes AutoSkip service that was<br>";
         text += "previously suspended by tuning to configured <b>AutoSkip service disable channel</b>.";
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
         text += "tivo.com login can be used for Remote--Search and Search++ if provided to enhance<br>";
         text += "search capabilities to include OTT/streaming titles in addition to linear.<br>";
         text += "This setting is optional but useful to add if you have any series 4 or later TiVos.";
      }
      else if (component.equals("tivo_password")) {
         text =  "<b>tivo.com password</b><br>";
         text += "tivo.com login can be used for Remote--Search and Search++ if provided to enhance<br>";
         text += "search capabilities to include OTT/streaming titles in addition to linear.<br>";
         text += "This setting is optional but useful to add if you have any series 4 or later TiVos.";
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
         text += "Choices include light and dark themes which apply immediately.";
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
          text += "For kmttg auto transfers mode only this will restore the previous job queue and resume<br>";
          text += "the processing. This only applies to kmttg running auto transfers in service/batch mode.";
       }
      
       return MyTooltip.make(text);
   }

}

