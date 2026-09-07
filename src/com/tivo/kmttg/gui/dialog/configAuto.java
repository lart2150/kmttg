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
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.Component;
import java.awt.Dimension;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Stack;
import java.util.Arrays;
import java.util.ArrayList;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import net.miginfocom.swing.MigLayout;

import com.tivo.kmttg.gui.MyTooltip;
import com.tivo.kmttg.gui.swing.SwingUtil;
import com.tivo.kmttg.gui.swing.Theme;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.autoTable;
import com.tivo.kmttg.gui.table.autoTable.Tabentry;
import com.tivo.kmttg.main.autoConfig;
import com.tivo.kmttg.main.autoEntry;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.encodeConfig;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class configAuto {
   private static Stack<JTextField> errors = new Stack<JTextField>();
   private static double pos_x = -1;
   private static double pos_y = -1;

   private static JDialog dialog = null;
   private static JPanel content = null;
   private static JButton add = null;
   private static JButton del = null;
   private static JButton update = null;
   private static JTextArea text = null;
   private static autoTable table = null;
   private static JScrollPane table_scroll = null;
   private static JComboBox<String> type = null;
   private static JComboBox<String> tivo = null;
   private static JComboBox<String> encoding_name = null;
   private static JComboBox<String> encoding_name2 = null;
   private static JTextField encoding_name2_suffix = null;
   private static JCheckBox enabled = null;
   private static JCheckBox TSDownload = null;
   private static JCheckBox metadata = null;
   private static JCheckBox decrypt = null;
   private static JCheckBox qsfix = null;
   private static JCheckBox twpdelete = null;
   private static JCheckBox rpcdelete = null;
   private static JCheckBox comskip = null;
   private static JCheckBox comcut = null;
   private static JCheckBox captions = null;
   private static JCheckBox encode = null;
   //private static JCheckBox push = null;
   private static JCheckBox custom = null;
   private static JCheckBox dry_run = null;
   private static JCheckBox noJobWait = null;
   private static JTextField title = null;
   private static JTextField check_interval = null;
   private static JTextField comskipIni = null;
   private static JTextField channelFilter = null;
   private static JCheckBox chlExcludes = null;
   private static JTextField tivoFileNameFormat = null;
   private static JCheckBox dateFilter = null;
   private static JCheckBox suggestionsFilter = null;
   private static JCheckBox suggestionsFilter_single = null;
   private static JCheckBox useProgramId_unique = null;
   private static JCheckBox kuidFilter = null;
   private static JCheckBox programIdFilter = null;
   private static JComboBox<String> dateOperator = null;
   private static JTextField dateHours = null;
   private static JButton OK = null;
   private static JButton CANCEL = null;

   private static final String _noSecondEncodingTxt = "Do not encode twice";

   public void display(JFrame frame) {
      debug.print("frame=" + frame);
      // Create dialog if not already created
      if (dialog == null) {
         create(frame);
         // Set component tooltips
         setToolTips();
      }

      // Parse auto.ini file to define current configuration
      autoConfig.parseAuto(config.autoIni);

      // Clear out any error highlights
      clearTextFieldErrors();

      // Update component settings to current configuration
      update();

      // Refresh available options based on settings
      refreshOptions();

      // Display the dialog
      if (pos_x != -1)
         dialog.setLocation((int)pos_x, (int)pos_y);
      dialog.setVisible(true);
   }

   public static JDialog getDialog() {
      return dialog;
   }

   private void textFieldError(JTextField f, String message) {
      debug.print("f=" + f + " message=" + message);
      log.error(message);
      f.setBackground(TableUtil.lightRed);
      errors.add(f);
   }

   private void clearTextFieldErrors() {
      debug.print("");
      if (errors.size() > 0) {
         for (int i=0; i<errors.size(); i++) {
            errors.get(i).setBackground(UIManager.getColor("TextField.background"));
         }
         errors.clear();
      }
   }

   // Create panel containing a left-justified component
   private JPanel createBoxItemLJ(Component item) {
      JPanel pnl = new JPanel();
      pnl.setLayout(new BoxLayout(pnl, BoxLayout.X_AXIS));
      pnl.add(item);
      pnl.add(Box.createHorizontalGlue());
      return pnl;
   }

   private void create(JFrame frame) {
      debug.print("frame=" + frame);

      // Create all the components of the dialog
      table = new autoTable();
      table.TABLE.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
         @Override
         public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting())
               return;
            int row = table.TABLE.getSelectionModel().getLeadSelectionIndex();
            if (row >= 0 && row < table.MODEL.size() && table.TABLE.isRowSelected(row)) {
               Tabentry newSelection = table.MODEL.getRow(row);
               if (newSelection != null) {
                  TableRowSelected(newSelection.getType().entry);
               }
            }
         }
      });
      table_scroll = new JScrollPane(table.TABLE);
      table_scroll.setPreferredSize(new Dimension(table_scroll.getPreferredSize().width, 150));

      String message = "For Type=keywords: Multiple keywords are allowed separated by '| character";
      message += "\nkeyword=>AND  (keyword)=>OR  -keyword=>NOT";
      message += "\nEXAMPLE: Type=keywords  keywords=(basketball)|(football)|-new york";
      message += "\n  => football OR basketball NOT new york";
      text = new JTextArea(message);
      text.setOpaque(false);

      add = new JButton("ADD");
      add.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            addCB();
         }
      });

      update = new JButton("UPDATE");
      update.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            updateCB();
         }
      });

      del = new JButton("DEL");
      del.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            delCB();
         }
      });

      JLabel type_label = new JLabel("Type");
      type = new JComboBox<String>();
      type.addItem("title");
      type.addItem("keywords");
      type.setSelectedItem(type.getItemAt(0));

      JLabel tivo_label = new JLabel("TiVo");
      tivo = new JComboBox<String>();
      for (String s : getTivoFilterNames()) {
         tivo.addItem(s);
      }
      if (tivo.getItemCount() > 0)
         tivo.setSelectedItem(tivo.getItemAt(0));

      title = new JTextField();

      enabled    = new JCheckBox("enabled"); enabled.setSelected(true);
      TSDownload = new JCheckBox("TS Downloads");
      metadata   = new JCheckBox("metadata");
      decrypt    = new JCheckBox("decrypt");
      qsfix      = new JCheckBox("QS Fix");
      twpdelete  = new JCheckBox("TWP Delete");
      rpcdelete  = new JCheckBox("rpc Delete");
      comskip    = new JCheckBox("Ad Detect");
      comcut     = new JCheckBox("Ad Cut");
      captions   = new JCheckBox("captions");
      qsfix.addActionListener(new ActionListener() {
         // Call refreshOptions whenever this is toggled
         public void actionPerformed(ActionEvent e) {
            refreshOptions();
         }
      });
      encode    = new JCheckBox("encode");
      //push      = new JCheckBox("push");
      suggestionsFilter_single = new JCheckBox("Filter out TiVo Suggestions");
      useProgramId_unique = new JCheckBox("Treat each recording as unique");
      // This intentionally disabled for now
      //encode.addActionListener(new ActionListener() {
      //   public void actionPerformed(ActionEvent e) {
      //      boolean selected = encode.isSelected();
      //      if (config.VRD == 0) {
      //         if (selected) {
      //            if (config.OS.equals("windows") && file.isFile(config.mencoder)) {
      //               qsfix.setEnabled(true);
      //               qsfix.setSelected(true);
      //            }
      //         } else {
      //            qsfix.setEnabled(false);
      //            qsfix.setSelected(false);
      //         }
      //      }
      //   }
      //});
      custom   = new JCheckBox("custom");

      JLabel comskipIni_label = new JLabel("comskip.ini override: ");
      comskipIni = new JTextField(); comskipIni.setMinimumSize(new java.awt.Dimension(30, comskipIni.getPreferredSize().height));

      JLabel channelFilter_label = new JLabel("channel filter: ");
      channelFilter = new JTextField(); channelFilter.setMinimumSize(new java.awt.Dimension(30, channelFilter.getPreferredSize().height));

      chlExcludes = new JCheckBox("Exclude channels");

      JLabel tivoFileNameFormat_label = new JLabel("file name override: ");
      tivoFileNameFormat = new JTextField(); tivoFileNameFormat.setMinimumSize(new java.awt.Dimension(30, tivoFileNameFormat.getPreferredSize().height));

      JLabel encoding_name_label = new JLabel("Encoding Name: ");

      encoding_name = new JComboBox<String>();
      encoding_name2 = new JComboBox<String>();
      encoding_name2_suffix = new JTextField(); encoding_name2_suffix.setMinimumSize(new java.awt.Dimension(15, encoding_name2_suffix.getPreferredSize().height));
      SetEncodings(encodeConfig.getValidEncodeNames());

      JLabel global_settings = new JLabel("GLOBAL SETTINGS:");

      JLabel check_interval_label = new JLabel("Check Tivos Interval (mins)");

      check_interval = new JTextField(); check_interval.setColumns(5);
      check_interval.setText("" + autoConfig.CHECK_TIVOS_INTERVAL);

      dry_run = new JCheckBox("Dry Run Mode (test keywords only)");
      dry_run.setSelected((Boolean)(autoConfig.dryrun == 1));

      noJobWait = new JCheckBox("Do not wait for all jobs to finish before processing new ones");
      noJobWait.setSelected((Boolean)(autoConfig.noJobWait == 1));

      dateFilter = new JCheckBox("Date Filter");
      dateOperator = new JComboBox<String>();
      dateOperator.addItem("more than");
      dateOperator.addItem("less than");
      dateOperator.setSelectedItem(dateOperator.getItemAt(0));
      dateHours = new JTextField("48");
      dateHours.setColumns(5);
      JLabel dateHours_label = new JLabel("hours old");

      suggestionsFilter = new JCheckBox("Filter out TiVo Suggestions");

      kuidFilter = new JCheckBox("Only process KUID recordings");

      programIdFilter = new JCheckBox("Do not process recordings without ProgramId");

      OK = new JButton("OK");
      OK.setName("button_autoconfig_ok");
      OK.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            okCB();
         }
      });

      CANCEL = new JButton("CANCEL");
      CANCEL.setName("button_autoconfig_cancel");
      CANCEL.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            pos_x = dialog.getX(); pos_y = dialog.getY();
            dialog.setVisible(false);
         }
      });

      content = new JPanel();
      content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
      content.setBorder(javax.swing.BorderFactory.createEmptyBorder(5,5,5,5));

      // table
      content.add(table_scroll);

      // text pane
      content.add(createBoxItemLJ(text));

      // row 3 items
      JPanel row3 = new JPanel(new MigLayout("gapx 5", "[][][][][grow]", ""));
      row3.add(type_label, "cell 0 0");
      row3.add(type, "cell 1 0");
      row3.add(tivo_label, "cell 2 0");
      row3.add(tivo, "cell 3 0");
      row3.add(title, "cell 4 0, growx");
      content.add(createBoxItemLJ(row3));

      // row4
      JPanel row4 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
      row4.add(TSDownload);
      row4.add(metadata);
      row4.add(decrypt);
      row4.add(qsfix);
      if (config.twpDeleteEnabled()) {
         row4.add(twpdelete);
      }
      if (config.rpcDeleteEnabled()) {
         row4.add(rpcdelete);
      }
      row4.add(comskip);
      row4.add(comcut);
      row4.add(captions);
      row4.add(encode);
      row4.add(custom);
      content.add(row4);

      // row5
      JPanel row5 = new JPanel(new MigLayout("gapx 5", "[][][grow]", ""));
      row5.add(encoding_name, "cell 0 0");
      row5.add(encoding_name2, "cell 1 0");
      row5.add(encoding_name2_suffix, "cell 2 0, growx");
      content.add(createBoxItemLJ(row5));

      // Put these items in a grid for better alignment
      JPanel gp = new JPanel(new MigLayout("", "[][grow]", ""));
      gp.add(encoding_name_label, "cell 0 0"); gp.add(row5, "cell 1 0, growx");
      gp.add(comskipIni_label, "cell 0 1"); gp.add(comskipIni, "cell 1 1, growx");
      gp.add(tivoFileNameFormat_label, "cell 0 2"); gp.add(tivoFileNameFormat, "cell 1 2, growx");
      gp.add(chlExcludes, "cell 0 3");
      gp.add(channelFilter_label, "cell 0 4"); gp.add(channelFilter, "cell 1 4, growx");
      content.add(createBoxItemLJ(gp));

      // row_misc
      JPanel row_misc = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
      row_misc.add(enabled);
      row_misc.add(suggestionsFilter_single);
      row_misc.add(useProgramId_unique);
      content.add(row_misc);

      // Add, Update, Del
      JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
      buttons.add(add);
      buttons.add(update);
      buttons.add(del);
      content.add(buttons);

      // separator
      JSeparator sep = new JSeparator(JSeparator.HORIZONTAL);
      content.add(sep);

      // global_settings
      content.add(global_settings);

      // row_dry_run
      JPanel row_dry_run = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
      row_dry_run.add(dry_run);
      row_dry_run.add(check_interval_label);
      row_dry_run.add(check_interval);
      content.add(row_dry_run);

      // date filter row
      JPanel date = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
      date.add(dateFilter);
      date.add(dateOperator);
      date.add(dateHours);
      date.add(dateHours_label);
      content.add(date);

      JPanel filter_panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
      filter_panel.add(suggestionsFilter);
      filter_panel.add(kuidFilter);
      filter_panel.add(programIdFilter);
      content.add(filter_panel);

      // noJobWait
      JPanel noWait = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
      noWait.add(noJobWait);
      content.add(noWait);

      // OK & CANCEL. Kept out of content so they stay put when it scrolls.
      JPanel last = new JPanel(new FlowLayout(FlowLayout.CENTER, 50, 0));
      last.add(OK);
      last.add(CANCEL);

      // create dialog window
      dialog = new JDialog(frame);
      dialog.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent arg0) {
            pos_x = dialog.getX(); pos_y = dialog.getY();
         }
      });
      SwingUtil.loadIcons(dialog);
      dialog.setTitle("kmttg auto transfers configuration");
      // The settings scroll and the buttons keep their row, so both stay
      // reachable at a font size that wants more height than the screen has.
      JPanel root = new JPanel(new BorderLayout());
      root.add(SwingUtil.scrollPane(content), BorderLayout.CENTER);
      root.add(last, BorderLayout.SOUTH);
      dialog.getContentPane().add(root);
      Theme.fitToScreen(dialog);
      dialog.setLocationRelativeTo(frame);
   }

   // Component tooltip setup
   public void setToolTips() {
      enabled.setToolTipText(getToolTip("enabled"));
      TSDownload.setToolTipText(config.gui.getToolTip("TSDownload"));
      metadata.setToolTipText(config.gui.getToolTip("metadata"));
      decrypt.setToolTipText(config.gui.getToolTip("decrypt"));
      qsfix.setToolTipText(config.gui.getToolTip("qsfix"));
      twpdelete.setToolTipText(config.gui.getToolTip("twpdelete"));
      rpcdelete.setToolTipText(config.gui.getToolTip("rpcdelete"));
      comskip.setToolTipText(config.gui.getToolTip("comskip"));
      comcut.setToolTipText(config.gui.getToolTip("comcut"));
      captions.setToolTipText(config.gui.getToolTip("captions"));
      encode.setToolTipText(config.gui.getToolTip("encode"));
      //push.setToolTipText(config.gui.getToolTip("push"));
      custom.setToolTipText(config.gui.getToolTip("custom"));
      encoding_name.setToolTipText(config.gui.getToolTip("encoding"));
      encoding_name2.setToolTipText(config.gui.getToolTip("encoding2"));
      encoding_name2_suffix.setToolTipText(config.gui.getToolTip("encoding2_suffix"));
      table.TABLE.setToolTipText(getToolTip("table"));
      type.setToolTipText(getToolTip("type"));
      tivo.setToolTipText(getToolTip("tivo"));
      dry_run.setToolTipText(getToolTip("dry_run"));
      noJobWait.setToolTipText(getToolTip("noJobWait"));
      title.setToolTipText(getToolTip("title"));
      comskipIni.setToolTipText(getToolTip("comskipIni"));
      channelFilter.setToolTipText(getToolTip("channelFilter"));
      tivoFileNameFormat.setToolTipText(getToolTip("tivoFileNameFormat"));
      check_interval.setToolTipText(getToolTip("check_interval"));
      add.setToolTipText(getToolTip("add"));
      update.setToolTipText(getToolTip("update"));
      del.setToolTipText(getToolTip("del"));
      dateFilter.setToolTipText(getToolTip("dateFilter"));
      chlExcludes.setToolTipText(getToolTip("chlExcludes"));
      suggestionsFilter.setToolTipText(getToolTip("suggestionsFilter"));
      suggestionsFilter_single.setToolTipText(getToolTip("suggestionsFilter_single"));
      useProgramId_unique.setToolTipText(getToolTip("useProgramId_unique"));
      kuidFilter.setToolTipText(getToolTip("kuidFilter"));
      programIdFilter.setToolTipText(getToolTip("programIdFilter"));
      dateOperator.setToolTipText(getToolTip("dateOperator"));
      dateHours.setToolTipText(getToolTip("dateHours"));
      OK.setToolTipText(getToolTip("OK"));
      CANCEL.setToolTipText(getToolTip("CANCEL"));
   }

   public String getToolTip(String component) {
      String text = "";
      if (component.equals("table")) {
         text =  "<b>auto transfers entries</b><br>";
         text += "Click on an entry to select it. Form settings will update to match<br>";
         text += "the current settings for that entry. You can then change settings as<br>";
         text += "desired and then use <b>UPDATE</b> button to apply form settings to the entry.<br>";
         text += "Use <b>ADD</b> button to add a new entry<br>";
         text += "Use <b>DEL</b> button to remove selected entries<br>";
         text += "Use <b>up</b> and <b>down</b> arrows to move selected row up and down in priority.<br>";
         text += "NOTE: Entry updates are only saved after you <b>OK</b> this form.";
      }
      else if (component.equals("type")) {
         text =  "<b>Type</b><br>";
         text += "<b>title</b> means exact title matching (case insensitive).<br>";
         text += "<b>keywords</b> means keyword matching (case insensitive) with<br>";
         text += "optional logical operations as illustrated above. Consult the<br>";
         text += "documentation for all the details.";
      }
      else if (component.equals("tivo")) {
         text =  "<b>TiVo</b><br>";
         text += "Restrict transfers to be from this TiVo only.<br>";
         text += "<b>all</b> means all TiVos currently configured in kmttg.";
      }
      else if (component.equals("enabled")) {
         text =  "<b>enabled</b><br>";
         text += "You can use this option to enable or disable an Auto Transfer entry.<br>";
         text += "This is useful to temporarily disable Auto Transfer entries without having<br>";
         text += "to delete them.";
      }
      else if (component.equals("dry_run")) {
         text =  "<b>Dry Run Mode (test keywords only)</b><br>";
         text += "With this option enabled kmttg will exercise the auto transfers setup<br>";
         text += "and will print messages about what shows match your setup, but will<br>";
         text += "not actually run any transfers. This is useful for testing your auto<br>";
         text += "transfers setup to ensure it will do what you want.<br>";
         text += "<b>NOTE: Use Auto Transfers->Run Once in GUI with this option set to test</b>.";
      }
      else if (component.equals("noJobWait")) {
         text =  "<b>Do not wait for all jobs to finish before processing new ones</b><br>";
         text += "With this option enabled kmttg will not wait for all jobs to complete<br>";
         text += "to check TiVos for new potential shows to process. The default behavior of<br>";
         text += "kmttg (this option off) is to wait until all tasks have completed for a TiVo<br>";
         text += "before looking for new shows to process for that TiVo.";
      }
      else if (component.equals("title")) {
         text =  "<b>title/keywords</b><br>";
         text += "Type in or update title or keywords for this entry here.<br>";
         text += "Consult example above and documentation for details on keywords setup.<br>";
         text += "NOTE: title and keywords are all case insensitive.";
      }
      else if (component.equals("comskipIni")) {
         text =  "<b>comskip.ini override</b><br>";
         text += "If you wish to use a specific comskip.ini file to use with <b>comcut</b> for<br>";
         text += "this auto transfer then specify the full path to the file here.<br>";
         text += "This will override the comskip.ini file specified in main kmttg configuration.";
      }
      else if (component.equals("chlExcludes")) {
         text = "<b>Ignore channel list</b><br>";
         text += "Treat channel list as channels to ignore recordings from.";
      }
      else if (component.equals("channelFilter")) {
         text =  "<b>channel filter</b><br>";
         text += "If you wish to filter out by channel number or name for this auto transfer<br>";
         text += "then enter either channel number or name in this field. Leave it empty if you<br>";
         text += "do not want to filter by channel number or name.";
      }
      else if (component.equals("tivoFileNameFormat")) {
         text =  "<b>file name override</b><br>";
         text += "If you wish to use a custom file name format for this auto entry that overrides<br>";
         text += "the global <b>File Naming</b> setting then do so here. Else leave this field blank.";
      }
      else if (component.equals("check_interval")) {
         text =  "<b>Check Tivos Interval (mins)</b><br>";
         text += "Once you start the Auto Transfers service or background job kmttg<br>";
         text += "will run in a loop matching your Auto Transfers entries to shows<br>";
         text += "on your Tivos and performing all the selected tasks for each match.<br>";
         text += "Once all matches have been processed kmttg will sleep for this specified<br>";
         text += "amount of time before checking again.<br>";
         text += "<b>NOTE: Setting this too low will overburden your network and Tivos.</b>";
      }
      else if (component.equals("add")) {
         text =  "<b>ADD</b><br>";
         text += "Add a new Auto Transfers entry based on current form choices.<br>";
         text += "NOTE: Additions won't be saved until you <b>OK</b> this form.";
      }
      else if (component.equals("update")) {
         text =  "<b>UPDATE</b><br>";
         text += "Update the currently selected Auto Transfers entry with current form settings.<br>";
         text += "NOTE: Updates won't be saved until you <b>OK</b> this form.";
      }
      else if (component.equals("del")) {
         text =  "<b>DEL</b><br>";
         text += "Remove currently selected Auto Transfers entries.<br>";
         text += "NOTE: Removals won't be saved until you <b>OK</b> this form.";
      }
      else if (component.equals("dateFilter")) {
         text =  "<b>Date Filter</b><br>";
         text += "If enabled then only process shows earlier or later than the specified<br>";
         text += "number of hours old. Examples:<br>";
         text += "<b>less than 48</b> means only process shows earlier than 2 days old.<br>";
         text += "<b>more than 24</b> means only process shows later than 1 day old.";
      }
      else if (component.equals("dateOperator")) {
         text =  "<b>Date Filter Operator</b><br>";
         text += "Operator for Date Filter setting.";
      }
      else if (component.equals("dateHours")) {
         text =  "<b>Date Filter Hours</b><br>";
         text += "Number of hours to use for filtering by date. Examples:<br>";
         text += "<b>less than 48</b> means only process shows earlier than 2 days old.<br>";
         text += "<b>more than 24</b> means only process shows later than 1 day old.";
      }
      else if (component.equals("suggestionsFilter")) {
         text =  "<b>Filter out TiVo Suggestions</b><br>";
         text += "If enabled then do not process any TiVo Suggestions recordings.<br>";
         text += "NOTE: If enabled this filter overrides any individual suggestions filter settings.";
      }
      else if (component.equals("suggestionsFilter_single")) {
         text =  "<b>Filter out TiVo Suggestions</b><br>";
         text += "If enabled then do not process any TiVo Suggestions recordings for this entry.";
      }
      else if (component.equals("useProgramId_unique")) {
         text =  "<b>Treat each recording as unique</b><br>";
         text += "If enabled then kmttg will generate a unique ProgramId based on ProgramId and recorded<br>";
         text += "time for each recording of this program. This is useful only for programs that do not<br>";
         text += "already have unique ProgramIds for each episode, such as some news programs for example.<br>";
         text += "For such programs kmttg would not ordinarily auto download subsequent episodes because<br>";
         text += "a ProgramId entry already exists in <b>auto.history</b> file. By enabling this option<br>";
         text += "kmttg will instead use a time-based ProgramId entry so that future recordings on different<br>";
         text += "dates with same ProgramId will still auto download<br>";
         text += "<b>NOTE: Enabling this option may lead to repeated downloads of shows so use wisely/sparingly</b><br>";
         text += "<b>only for shows without unique ProgramId</b>";
      }
      else if (component.equals("kuidFilter")) {
         text =  "<b>Only process KUID recordings</b><br>";
         text += "If enabled then only process recordings that are marked as<br>";
         text += "Keep Until I Delete (KUID).";
      }
      else if (component.equals("programIdFilter")) {
         text =  "<b>Do not process recordings without ProgramId</b><br>";
         text += "If enabled then do not process recordings without ProgramId.<br>";
         text += "Typically, these are programs that were transferred to your TiVo(s)<br>";
         text += "from a PC or other source other than a recorded TV station or MRV,<br>";
         text += "such as pyTivo or TiVo Desktop transfers.";
      }
      else if (component.equals("OK")) {
         text =  "<b>OK</b><br>";
         text += "Save all changes made in this form and close the form.<br>";
         text += "NOTE: You need to setup and run kmttg service on Windows for Auto Transfers to run.<br>";
         text += "For non-windows platforms you need to setup a background job for Auto Transfers to run.<br>";
         text += "You can use <b>Auto Transfers->Service</b> or <b>Auto Transfers->Background Job</b><br>";
         text += "menus to do this. Consult documentation for more details.<br>";
         text += "NOTE: Settings are saved to <b>auto.ini</b> file which resides by <b>kmttg.jar</b> file.<br>";
      }
      else if (component.equals("CANCEL")) {
         text =  "<b>CANCEL</b><br>";
         text += "Do not save any changes made in this form and close the form.<br>";
      }
      return MyTooltip.make(text);
   }

   private void setTivoFilterNames() {
      tivo.removeAllItems();
      String[] names = getTivoFilterNames();
      for (int i=0; i<names.length; ++i) {
         tivo.addItem(names[i]);
      }
      if (tivo.getItemCount() > 0)
         tivo.setSelectedItem(tivo.getItemAt(0));
   }

   // Defines choices for tivo name filtering
   private String[] getTivoFilterNames() {
      Stack<String> names = config.getNplTivoNames();
      names.add(0, "all");
      String[] tivoNames = new String[names.size()];
      for (int i=0; i<names.size(); ++i) {
         tivoNames[i] = names.get(i);
      }
      return tivoNames;
   }

   // Checks given tivo name against current valid names and resets to all if not valid
   private String validateTivoName(String tivoName) {
      if ( ! tivoName.equals("all") ) {
         Stack<String> names = config.getNplTivoNames();
         for (int i=0; i<names.size(); ++i) {
            if (tivoName.equals(names.get(i)))
               return tivoName;
         }
         log.error("TiVo '" + tivoName + "' currently not configured in kmttg - resetting to all");
      }
      return "all";
   }

   // This will decide which options are enabled based on current config settings
   // Options are disabled when associated config entry is not setup
   public void refreshOptions() {
      if (config.VRD == 0 && ! file.isFile(config.ffmpeg)) {
         qsfix.setSelected(false);
         qsfix.setEnabled(false);
      } else {
         qsfix.setEnabled(true);
      }

      if (!config.twpDeleteEnabled()) {
         twpdelete.setSelected(false);
         twpdelete.setEnabled(false);
      } else {
         twpdelete.setEnabled(true);
      }

      if ( ! config.rpcDeleteEnabled() ) {
         rpcdelete.setSelected(false);
         rpcdelete.setEnabled(false);
      } else {
         rpcdelete.setEnabled(true);
      }

      if (! file.isFile(config.comskip)) {
         comskip.setSelected(false);
         comskip.setEnabled(false);
      } else {
         comskip.setEnabled(true);
      }

      if (config.VRD == 0 && ! file.isFile(config.ffmpeg)) {
         comcut.setSelected(false);
         comcut.setEnabled(false);
      } else {
         comcut.setEnabled(true);
      }

      if (! file.isFile(config.t2extract) && ! file.isFile(config.ccextractor)) {
         captions.setSelected(false);
         captions.setEnabled(false);
      } else {
         captions.setEnabled(true);
      }
      if (config.VRD == 0 && qsfix.isSelected()) {
         captions.setSelected(false);
         captions.setEnabled(false);
      }

      if (! file.isFile(config.ffmpeg) &&
          ! file.isFile(config.mencoder) &&
          ! file.isFile(config.handbrake) ) {
         encode.setSelected(false);
         encode.setEnabled(false);
      } else {
         encode.setEnabled(true);
      }

      /*if ( ! file.isFile(config.pyTivo_config) ) {
         push.setSelected(false);
         push.setEnabled(false);
      } else {
         push.setEnabled(true);
      }*/

      if ( ! com.tivo.kmttg.task.custom.customCommandExists() ) {
         custom.setSelected(false);
         custom.setEnabled(false);
      } else {
         custom.setEnabled(true);
      }

   }

   public void clearTable() {
      debug.print("");
      table.clear();
   }

   public void addTableRow(autoEntry entry) {
      debug.print("entry=" + entry);
      table.AddRow(entry);
   }

   public void removeTableRow(int row) {
      debug.print("row=" + row);
      table.RemoveRow(row);
   }

   public int[] getTableSelectedRows() {
      debug.print("");
      int[] rows = table.getSelectedRows();
      if (rows.length <= 0)
         log.error("No rows selected");
      return rows;
   }

   // Return autoEntry instance of selected entry
   public autoEntry GetRowData(int row) {
      return table.GetRowData(row);
   }

   // Update dialog settings based on autoConfig current settings
   public void update() {
      SetKeywords(autoConfig.KEYWORDS);
      SetEncodings(encodeConfig.getValidEncodeNames());
      setTivoFilterNames();
      check_interval.setText("" + autoConfig.CHECK_TIVOS_INTERVAL);
      dry_run.setSelected((Boolean)(autoConfig.dryrun == 1));
      noJobWait.setSelected((Boolean)(autoConfig.noJobWait == 1));
      dateFilter.setSelected((Boolean)(autoConfig.dateFilter == 1));
      chlExcludes.setSelected((Boolean)(autoConfig.channelExcludes == 1));
      dateOperator.setSelectedItem(autoConfig.dateOperator);
      dateHours.setText("" + autoConfig.dateHours);
      suggestionsFilter.setSelected((Boolean)(autoConfig.suggestionsFilter == 1));
      kuidFilter.setSelected((Boolean)(autoConfig.kuidFilter == 1));
      programIdFilter.setSelected((Boolean)(autoConfig.programIdFilter == 1));
   }

   // Set encoding_name ComboBox choices
   public void SetEncodings(Stack<String> values) {
      debug.print("values=" + values);

      encoding_name.removeAllItems();
      encoding_name2.removeAllItems();

      // Second encoding optional
      encoding_name2.addItem(_noSecondEncodingTxt);

      for (int i=0; i<values.size(); ++i) {
         encoding_name.addItem(values.get(i));
         encoding_name2.addItem(values.get(i));
      }
      if (encoding_name.getItemCount() > 0)
         encoding_name.setSelectedItem(encoding_name.getItemAt(0));
      if (encoding_name2.getItemCount() > 0)
         encoding_name2.setSelectedItem(encoding_name2.getItemAt(0));
   }

   // Set table entries according to auto config setup
   public void SetKeywords(Stack<autoEntry> entries) {
      debug.print("entries=" + entries);
      clearTable();
      if (entries.size() > 0) {
         for (int i=0; i<entries.size(); i++) {
            addTableRow(entries.get(i));
         }
      }
   }

   // Callback for ADD button
   // Add type & keywords as a table entry
   private void addCB() {
      debug.print("");
      String ktype = (String)type.getSelectedItem();
      String keywords = string.removeLeadingTrailingSpaces(title.getText());
      if (keywords.length() == 0) {
         log.error("No keywords specified");
         return;
      }

      // Make sure this is not a duplicate entry
      Boolean duplicate = false;
      if (table.MODEL.size() > 0) {
         for (int i=0; i<table.MODEL.size(); ++i) {
            autoEntry check = GetRowData(i);
            if (check.type.equals(ktype)) {
               if (check.type.equals("title")) {
                  if (keywords.equals(check.keyword)) duplicate = true;
               } else {
                  if (keywords.equals(autoConfig.keywordsToString(check.keywords))) duplicate = true;
               }
            }
         }
      }
      if (duplicate) {
         log.error("Duplicate entry, not adding");
         return;
      }

      autoEntry entry = new autoEntry();
      // Set entry settings based on dialog settings
      guiToEntry(entry);

      // Add a new table row
      addTableRow(entry);
   }

   // Callback for UPDATE button
   // Update selected table entry with dialog settings
   private void updateCB() {
      debug.print("");
      int[] rows = getTableSelectedRows();
      if (rows.length == 0) {
         log.error("No table row selected");
         return;
      }

      int row = rows[0]; // Process top most row
      autoEntry entry = GetRowData(row);

      // Update entry settings
      guiToEntry(entry);

      // Update table settings
      Tabentry e = table.MODEL.getRow(row);
      e.type = new autoTableEntry(entry);
      if (entry.type.equals("title"))
         e.keywords = entry.keyword;
      else
         e.keywords = autoConfig.keywordsToString(entry.keywords);

      table.resize();
      log.warn("Updated auto transfers entry # " + (row+1));
   }

   // Callback for DEL button
   // Remove selected table entries
   private void delCB() {
      debug.print("");
      int[] rows = getTableSelectedRows();
      for (int i=rows.length-1; i>-1; --i) {
         removeTableRow(rows[i]);
      }
   }

   // Callback for OK button
   // Save table settings to auto.ini and hide the dialog
   private void okCB() {
      debug.print("");
      clearTextFieldErrors();
      // Error checking
      int interval = 60;
      String value = string.removeLeadingTrailingSpaces(check_interval.getText());
      try {
         interval = Integer.parseInt(value);
      } catch(NumberFormatException e) {
         textFieldError(check_interval, "check interval should be an integer: '" + value + "'");
         return;
      }

      float hours = 48;
      value = string.removeLeadingTrailingSpaces(dateHours.getText());
      try {
         hours = Float.parseFloat(value);
      } catch(NumberFormatException e) {
         textFieldError(check_interval, "Date Filter hours should be of type float: '" + value + "'");
         return;
      }

      // Write to file
      try {
         BufferedWriter ofp = new BufferedWriter(new FileWriter(config.autoIni));
         ofp.write("# kmttg auto.ini file\n");
         ofp.write("<check_tivos_interval>\n" + interval + "\n\n");
         ofp.write("<dryrun>\n");
         if (dry_run.isSelected())
            ofp.write("1\n\n");
         else
            ofp.write("0\n\n");
         ofp.write("<noJobWait>\n");
         if (noJobWait.isSelected())
            ofp.write("1\n\n");
         else
            ofp.write("0\n\n");
         ofp.write("<dateFilter>\n");
         if (dateFilter.isSelected())
            ofp.write("1\n\n");
         else
            ofp.write("0\n\n");
         ofp.write("<dateOperator>\n" + dateOperator.getSelectedItem() + "\n\n");
         ofp.write("<dateHours>\n" + hours + "\n\n");
         ofp.write("<suggestionsFilter>\n");
         if (suggestionsFilter.isSelected())
            ofp.write("1\n\n");
         else
            ofp.write("0\n\n");
         ofp.write("<kuidFilter>\n");
         if (kuidFilter.isSelected())
            ofp.write("1\n\n");
         else
            ofp.write("0\n\n");
         ofp.write("<programIdFilter>\n");
         if (programIdFilter.isSelected())
            ofp.write("1\n\n");
         else
            ofp.write("0\n\n");

         int rows = table.MODEL.size();
         if (rows > 0) {
            autoEntry entry;
            for (int i=0; i<rows; ++i) {
               entry = GetRowData(i);
               // Some options may have to be turned off for disabled features
               if ( ! config.twpDeleteEnabled() )
                  entry.twpdelete = 0;
               if ( ! config.rpcDeleteEnabled() )
                  entry.rpcdelete = 0;
               ofp.write("\n");
               if (entry.type.equals("title")) {
                  ofp.write("<title>\n");
                  ofp.write(entry.keyword + "\n");
               } else {
                  ofp.write("<keywords>\n");
                  ofp.write(autoConfig.keywordsToString(entry.keywords) + "\n");
               }
               ofp.write("<options>\n");
               ofp.write("enabled "             + entry.enabled             + "\n");
               ofp.write("tivo "                + entry.tivo                + "\n");
               ofp.write("TSDownload "          + entry.TSDownload          + "\n");
               ofp.write("metadata "            + entry.metadata            + "\n");
               ofp.write("decrypt "             + entry.decrypt             + "\n");
               ofp.write("qsfix "               + entry.qsfix               + "\n");
               ofp.write("twpdelete "           + entry.twpdelete           + "\n");
               ofp.write("rpcdelete "          + entry.rpcdelete          + "\n");
               ofp.write("comskip "             + entry.comskip             + "\n");
               ofp.write("comcut "              + entry.comcut              + "\n");
               ofp.write("captions "            + entry.captions            + "\n");
               ofp.write("encode "              + entry.encode              + "\n");
               //ofp.write("push "                + entry.push                + "\n");
               ofp.write("custom "              + entry.custom              + "\n");
               ofp.write("channelExcludes "     + entry.channelExcludes     + "\n");
               ofp.write("suggestionsFilter "   + entry.suggestionsFilter   + "\n");
               ofp.write("useProgramId_unique " + entry.useProgramId_unique + "\n");
               if (entry.encode_name != null && entry.encode_name.length() > 0)
                  ofp.write("encode_name " + entry.encode_name + "\n");
               if (entry.encode_name2 != null && entry.encode_name2.length() > 0)
                   ofp.write("encode_name2 " + entry.encode_name2 + "\n");
               if (entry.encode_name2_suffix != null && entry.encode_name2_suffix.length() > 0)
                   ofp.write("encode_name2_suffix " + entry.encode_name2_suffix + "\n");
               if (entry.channelFilter != null && entry.channelFilter.length() > 0)
                  ofp.write("channelFilter " + entry.channelFilter + "\n");
               if (entry.tivoFileNameFormat != null && entry.tivoFileNameFormat.length() > 0)
                  ofp.write("tivoFileNameFormat " + entry.tivoFileNameFormat + "\n");
               if (file.isFile(entry.comskipIni))
                  ofp.write("comskipIni " + entry.comskipIni + "\n");
               else
                  ofp.write("comskipIni " + "none" + "\n");
            }
         }

         ofp.close();
      } catch (IOException ex) {
         log.error("Cannot write to auto config file: " + config.autoIni);
         log.error(ex.toString());
         return;
      }

      log.warn("Auto config settings saved");

      // Close dialog
      pos_x = dialog.getX(); pos_y = dialog.getY();
      dialog.setVisible(false);

      // Update autoConfig settings
      autoConfig.parseAuto(config.autoIni);
   }

   // Callback when user clicks on a table row
   // This will update component settings according to selected row data
   private void TableRowSelected(autoEntry entry) {
      enabled.setSelected((Boolean)(entry.enabled == 1));
      TSDownload.setSelected((Boolean)(entry.TSDownload == 1));
      metadata.setSelected((Boolean)(entry.metadata == 1));
      decrypt.setSelected((Boolean)(entry.decrypt == 1));
      qsfix.setSelected((Boolean)(entry.qsfix == 1));
      twpdelete.setSelected((Boolean)(entry.twpdelete == 1));
      rpcdelete.setSelected((Boolean)(entry.rpcdelete == 1));
      comskip.setSelected((Boolean)(entry.comskip == 1));
      comcut.setSelected((Boolean)(entry.comcut == 1));
      captions.setSelected((Boolean)(entry.captions == 1));
      encode.setSelected((Boolean)(entry.encode == 1));
      //push.setSelected((Boolean)(entry.push == 1));
      custom.setSelected((Boolean)(entry.custom == 1));
      suggestionsFilter_single.setSelected((Boolean)(entry.suggestionsFilter == 1));
      useProgramId_unique.setSelected((Boolean)(entry.useProgramId_unique == 1));

      encoding_name.setSelectedItem(entry.encode_name);

      if (entry.encode_name2 != null) {
    	  encoding_name2.setSelectedItem(entry.encode_name2);
    	  encoding_name2_suffix.setText(entry.encode_name2_suffix);
      } else
    	  encoding_name2.setSelectedItem(_noSecondEncodingTxt);

      comskipIni.setText(entry.comskipIni);

      chlExcludes.setSelected((Boolean)(entry.channelExcludes == 1));
      if (entry.channelFilter != null)
         channelFilter.setText(entry.channelFilter);
      else
         channelFilter.setText("");

      if (entry.tivoFileNameFormat != null)
         tivoFileNameFormat.setText(entry.tivoFileNameFormat);
      else
         tivoFileNameFormat.setText("");

      type.setSelectedItem(entry.type);

      entry.tivo = validateTivoName(entry.tivo);
      tivo.setSelectedItem(entry.tivo);

      if (entry.type.equals("title")) {
         title.setText(entry.keyword);
      } else {
         title.setText(autoConfig.keywordsToString(entry.keywords));
      }
   }

   private Boolean guiToEntry(autoEntry entry) {
      String ktype = (String)type.getSelectedItem();
      String ktivo = (String)tivo.getSelectedItem();
      String keywords = string.removeLeadingTrailingSpaces(title.getText());
      if (keywords.length() == 0) {
         log.error("No keywords specified");
         return false;
      }

      if (enabled.isSelected())
         entry.enabled = 1;
      else
         entry.enabled = 0;

      if (TSDownload.isSelected())
         entry.TSDownload = 1;
      else
         entry.TSDownload = 0;

      if (metadata.isSelected())
         entry.metadata = 1;
      else
         entry.metadata = 0;

      if (decrypt.isSelected())
         entry.decrypt = 1;
      else
         entry.decrypt = 0;

      if (qsfix.isSelected())
         entry.qsfix = 1;
      else
         entry.qsfix = 0;

      if (twpdelete.isSelected())
         entry.twpdelete = 1;
      else
         entry.twpdelete = 0;

      if (rpcdelete.isSelected())
         entry.rpcdelete = 1;
      else
         entry.rpcdelete = 0;

      if (comskip.isSelected())
         entry.comskip = 1;
      else
         entry.comskip = 0;

      if (comcut.isSelected())
         entry.comcut = 1;
      else
         entry.comcut = 0;

      if (captions.isSelected())
         entry.captions = 1;
      else
         entry.captions = 0;

      if (encode.isSelected())
         entry.encode = 1;
      else
         entry.encode = 0;

      /*if (push.isSelected())
         entry.push = 1;
      else
         entry.push = 0;*/

      if (custom.isSelected())
         entry.custom = 1;
      else
         entry.custom = 0;

      if (suggestionsFilter_single.isSelected())
         entry.suggestionsFilter = 1;
      else
         entry.suggestionsFilter = 0;

      if (useProgramId_unique.isSelected())
         entry.useProgramId_unique = 1;
      else
         entry.useProgramId_unique = 0;

      entry.encode_name = (String)encoding_name.getSelectedItem();

      // Does user want to encode second time? save profile name
      if (encoding_name2.getSelectedItem().equals(_noSecondEncodingTxt))
    	  entry.encode_name2 = null;
      else {
    	  entry.encode_name2 = (String)encoding_name2.getSelectedItem();
    	  entry.encode_name2_suffix = encoding_name2_suffix.getText();
      }

      String ini = (String)string.removeLeadingTrailingSpaces(comskipIni.getText());
      if (ini.length() > 0 && ! ini.equals("none")) {
         if ( ! file.isFile(ini) ) {
            log.error("Specified comskip.ini override file does not exist...");
         }
      }
      entry.comskipIni = ini;

      if (chlExcludes.isSelected())
         entry.channelExcludes = 1;
      else
         entry.channelExcludes = 0;

      String cFilter = (String)string.removeLeadingTrailingSpaces(channelFilter.getText());
      if (cFilter.length() > 0) {
         entry.channelFilter = cFilter;
         entry.channelFilterList = Arrays.asList(cFilter.split("\\s*,\\s*"));
      } else {
         entry.channelFilter = null;
         entry.channelFilterList = new ArrayList<String>();
      }

      cFilter = (String)string.removeLeadingTrailingSpaces(tivoFileNameFormat.getText());
      if (cFilter.length() > 0)
         entry.tivoFileNameFormat = cFilter;
      else
         entry.tivoFileNameFormat = null;

      entry.type = ktype;

      entry.tivo = ktivo;

      if (ktype.equals("title")) {
         entry.keyword = keywords;
      } else {
         autoConfig.stringToKeywords(keywords, entry);
      }

      return true;

   }
}
