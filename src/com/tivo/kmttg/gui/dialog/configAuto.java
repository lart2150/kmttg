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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Stack;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import com.tivo.kmttg.gui.MyTooltip;
import com.tivo.kmttg.gui.gui;
import com.tivo.kmttg.gui.remote.util;
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
   private static Stack<TextField> errors = new Stack<TextField>();
   private static String textbg_default = "";
   private static double pos_x = -1;
   private static double pos_y = -1;
   
   private static Stage dialog = null;
   private static VBox content = null;
   private static Button add = null;
   private static Button del = null;
   private static Button update = null;
   private static Label text = null;
   private static autoTable table = null;
   private static ScrollPane table_scroll = null;
   private static ChoiceBox<String> type = null;
   private static ChoiceBox<String> tivo = null;
   private static ComboBox<String> encoding_name = null;
   private static ComboBox<String> encoding_name2 = null;
   private static TextField encoding_name2_suffix = null;
   private static CheckBox enabled = null;
   private static CheckBox metadata = null;
   private static CheckBox decrypt = null;
   private static CheckBox qsfix = null;
   private static CheckBox twpdelete = null;
   private static CheckBox rpcdelete = null;
   private static CheckBox comskip = null;
   private static CheckBox comcut = null;
   private static CheckBox captions = null;
   private static CheckBox encode = null;
   //private static CheckBox push = null;
   private static CheckBox custom = null;
   private static CheckBox dry_run = null;
   private static CheckBox noJobWait = null;
   private static TextField title = null;
   private static TextField check_interval = null;
   private static TextField comskipIni = null;
   private static TextField channelFilter = null;
   private static TextField tivoFileNameFormat = null;
   private static CheckBox dateFilter = null;
   private static CheckBox suggestionsFilter = null;
   private static CheckBox suggestionsFilter_single = null;
   private static CheckBox useProgramId_unique = null;
   private static CheckBox kuidFilter = null;
   private static CheckBox programIdFilter = null;
   private static ChoiceBox<String> dateOperator = null;
   private static TextField dateHours = null;
   private static Button OK = null;
   private static Button CANCEL = null;
   
   private static final String _noSecondEncodingTxt = "Do not encode twice";

   public void display(Stage frame) {
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
         dialog.setX(pos_x);
      if (pos_y != -1)
         dialog.setY(pos_y);
      dialog.show();
   }
   
   public static Stage getDialog() {
      return dialog;
   }
   
   private void textFieldError(TextField f, String message) {
      debug.print("f=" + f + " message=" + message);
      log.error(message);
      f.setStyle("-fx-background-color: " + config.gui.getWebColor(config.lightRed));
      errors.add(f);
   }
   
   private void clearTextFieldErrors() {
      debug.print("");
      if (errors.size() > 0) {
         for (int i=0; i<errors.size(); i++) {
            errors.get(i).setStyle(textbg_default);
         }
         errors.clear();
      }
   }
  
   private void create(Stage frame) {
      debug.print("frame=" + frame);
      
      // Create all the components of the dialog
      table = new autoTable();
      table.TABLE.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Tabentry>() {
         @Override
         public void changed(ObservableValue<? extends Tabentry> obs, Tabentry oldSelection, Tabentry newSelection) {
            if (newSelection != null) {
               TableRowSelected(newSelection.getType().entry);
            }
         }
      });
      table_scroll = new ScrollPane(table.TABLE);
      table_scroll.setPrefHeight(150);
      table_scroll.setFitToHeight(true);
      table_scroll.setFitToWidth(true);
            
      text = new Label();
      String message = "for Type=keywords: Multiple keywords are allowed separated by '| character";
      message += "\nkeyword=>AND  (keyword)=>OR  -keyword=>NOT";
      message += "\nEXAMPLE: Type=keywords  keywords=(basketball)|(football)|-new york";
      message += "\n  => football OR basketball NOT new york";
      text.setText(message);
      
      add = new Button("ADD");
      add.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            addCB();
         }
      });

      update = new Button("UPDATE");
      update.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            updateCB();
         }
      });

      del = new Button("DEL");
      del.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            delCB();
         }
      });

      Label type_label = new Label("Type");
      type = new ChoiceBox<String>();
      type.getItems().addAll("title", "keywords");
      type.setValue(type.getItems().get(0));
      
      Label tivo_label = new Label("TiVo");
      tivo = new ChoiceBox<String>();
      for (String s : getTivoFilterNames()) {
         tivo.getItems().add(s);
      }
      if (tivo.getItems().size() > 0)
         tivo.setValue(tivo.getItems().get(0));
      
      title = new TextField();
            
      enabled   = new CheckBox("enabled"); enabled.setSelected(true);
      metadata  = new CheckBox("metadata");
      decrypt   = new CheckBox("decrypt");
      qsfix     = new CheckBox("QS Fix");
      twpdelete = new CheckBox("TWP Delete");
      rpcdelete = new CheckBox("rpc Delete");
      comskip   = new CheckBox("Ad Detect");
      comcut    = new CheckBox("Ad Cut");
      captions  = new CheckBox("captions");
      qsfix.setOnAction(new EventHandler<ActionEvent>() {
         // Call refreshOptions whenever this is toggled
         public void handle(ActionEvent e) {
            refreshOptions();
         }
      });
      encode    = new CheckBox("encode");
      //push      = new CheckBox("push");
      suggestionsFilter_single = new CheckBox("Filter out TiVo Suggestions");
      useProgramId_unique = new CheckBox("Treat each recording as unique");
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
      custom   = new CheckBox("custom");
      
      Label comskipIni_label = new Label("comskip.ini override: ");
      comskipIni = new TextField(); comskipIni.setMinWidth(30);
      
      Label channelFilter_label = new Label("channel filter: ");
      channelFilter = new TextField(); channelFilter.setMinWidth(30);
      
      Label tivoFileNameFormat_label = new Label("file name override: ");
      tivoFileNameFormat = new TextField(); tivoFileNameFormat.setMinWidth(30);
      
      Label encoding_name_label = new Label("Encoding Name: ");
      
      encoding_name = new ComboBox<String>();
      encoding_name2 = new ComboBox<String>();
      encoding_name2_suffix = new TextField(); encoding_name2_suffix.setMinWidth(15);
      SetEncodings(encodeConfig.getValidEncodeNames());
      
      Label global_settings = new Label("GLOBAL SETTINGS:");
      
      Label check_interval_label = new Label("Check Tivos Interval (mins)");
      
      check_interval = new TextField(); check_interval.setPrefWidth(50);
      check_interval.setText("" + autoConfig.CHECK_TIVOS_INTERVAL);
      
      dry_run = new CheckBox("Dry Run Mode (test keywords only)");
      dry_run.setSelected((Boolean)(autoConfig.dryrun == 1));
      
      noJobWait = new CheckBox("Do not wait for all jobs to finish before processing new ones");
      noJobWait.setSelected((Boolean)(autoConfig.noJobWait == 1));
      
      dateFilter = new CheckBox("Date Filter");
      dateOperator = new ChoiceBox<String>();
      dateOperator.getItems().add("more than");
      dateOperator.getItems().add("less than");
      dateOperator.setValue(dateOperator.getItems().get(0));
      dateHours = new TextField("48");
      dateHours.setPrefWidth(50);
      Label dateHours_label = new Label("hours old");
      
      suggestionsFilter = new CheckBox("Filter out TiVo Suggestions");
      
      kuidFilter = new CheckBox("Only process KUID recordings");
      
      programIdFilter = new CheckBox("Do not process recordings without ProgramId");
      
      OK = new Button("OK");
      OK.setPrefWidth(200);
      OK.setId("button_autoconfig_ok");
      OK.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            okCB();
         }
      });
      
      CANCEL = new Button("CANCEL");
      CANCEL.setPrefWidth(200);
      CANCEL.setId("button_autoconfig_cancel");
      CANCEL.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            pos_x = dialog.getX(); pos_y = dialog.getY();
            dialog.hide();
         }
      });
      
      content = new VBox();
      content.setSpacing(3);
      content.setPadding(new Insets(5,5,5,5));
      
      // table
      content.getChildren().add(table_scroll);

      // text pane
      content.getChildren().add(text);

      // row 3 items
      GridPane row3 = new GridPane();
      row3.setHgap(5);
      row3.getColumnConstraints().addAll(
         util.cc_none(), util.cc_none(), util.cc_none(), util.cc_none(), util.cc_stretch()
      );
      row3.add(type_label, 0, 0);
      row3.add(type, 1, 0);
      row3.add(tivo_label, 2, 0);
      row3.add(tivo, 3, 0);
      row3.add(title, 4, 0);
      content.getChildren().add(row3);
      
      // row4
      HBox row4 = new HBox();
      row4.setSpacing(5);
      row4.getChildren().addAll(metadata, decrypt, qsfix);
      if (config.twpDeleteEnabled()) {
         row4.getChildren().add(twpdelete);         
      }
      if (config.rpcDeleteEnabled()) {
         row4.getChildren().add(rpcdelete);         
      }
      row4.getChildren().addAll(comskip, comcut, captions, encode, custom);
      content.getChildren().add(row4);
      
      // row5
      GridPane row5 = new GridPane();
      row5.setHgap(5);
      row5.getColumnConstraints().addAll(
         util.cc_none(), util.cc_none(), util.cc_stretch()
      );
      row5.setAlignment(Pos.CENTER_LEFT);
      row5.setHgap(5);
      row5.add(encoding_name, 0, 0);
      row5.add(encoding_name2, 1, 0);
      row5.add(encoding_name2_suffix, 2, 0);
      content.getChildren().add(row5);
      
      // Put these items in a grid for better alignment
      GridPane gp = new GridPane();
      gp.getColumnConstraints().addAll(util.cc_none(), util.cc_stretch());
      gp.add(encoding_name_label, 0, 0); gp.add(row5, 1, 0);
      gp.add(comskipIni_label, 0, 1); gp.add(comskipIni, 1, 1);
      gp.add(channelFilter_label, 0, 2); gp.add(channelFilter, 1, 2);
      gp.add(tivoFileNameFormat_label, 0, 3); gp.add(tivoFileNameFormat, 1, 3);
      content.getChildren().add(gp); 
            
      // row_misc
      HBox row_misc = new HBox();
      row_misc.setSpacing(5);
      row_misc.getChildren().addAll(enabled, suggestionsFilter_single, useProgramId_unique);
      content.getChildren().add(row_misc);
      
      // Add, Update, Del
      HBox buttons = new HBox();
      buttons.setSpacing(5);
      buttons.getChildren().addAll(add, update, del);
      buttons.setAlignment(Pos.CENTER);
      content.getChildren().add(buttons); 
            
      // separator
      Separator sep = new Separator();
      sep.setOrientation(Orientation.HORIZONTAL);
      content.getChildren().add(sep);
                        
      // global_settings
      content.getChildren().add(global_settings);
      
      // row_dry_run
      HBox row_dry_run = new HBox();
      row_dry_run.setSpacing(10);
      row_dry_run.getChildren().addAll(dry_run, check_interval_label, check_interval);
      row_dry_run.setAlignment(Pos.CENTER_LEFT);
      content.getChildren().add(row_dry_run);
      
      // date filter row
      HBox date = new HBox();
      date.setAlignment(Pos.CENTER_LEFT);
      date.setSpacing(5);
      date.getChildren().addAll(dateFilter, dateOperator, dateHours, dateHours_label);
      content.getChildren().add(date);

      HBox filter_panel = new HBox();
      filter_panel.setSpacing(5);
      filter_panel.getChildren().addAll(suggestionsFilter, kuidFilter, programIdFilter);
      filter_panel.setAlignment(Pos.CENTER_LEFT);
      content.getChildren().add(filter_panel);
      
      // noJobWait
      content.getChildren().add(noJobWait);
            
      // OK & CANCEL
      HBox last = new HBox();
      last.setSpacing(50);
      last.setAlignment(Pos.CENTER);
      last.getChildren().addAll(OK, CANCEL);
      content.getChildren().add(last);
                 
      // create dialog window
      dialog = new Stage();
      dialog.setOnCloseRequest(new EventHandler<WindowEvent>() {
         @Override
         public void handle(WindowEvent arg0) {
            pos_x = dialog.getX(); pos_y = dialog.getY();
         }
      });
      dialog.initOwner(frame);
      gui.LoadIcons(dialog);
      dialog.initModality(Modality.NONE);
      dialog.setTitle("kmttg auto transfers configuration");
      Scene scene = new Scene(new VBox());
      config.gui.setFontSize(scene, config.FontSize);
      ((VBox) scene.getRoot()).getChildren().add(content);
      dialog.setScene(scene);
   }
   
   // Component tooltip setup
   public void setToolTips() {
      enabled.setTooltip(getToolTip("enabled"));
      metadata.setTooltip(config.gui.getToolTip("metadata"));
      decrypt.setTooltip(config.gui.getToolTip("decrypt"));
      qsfix.setTooltip(config.gui.getToolTip("qsfix"));
      twpdelete.setTooltip(config.gui.getToolTip("twpdelete"));
      rpcdelete.setTooltip(config.gui.getToolTip("rpcdelete"));
      comskip.setTooltip(config.gui.getToolTip("comskip"));
      comcut.setTooltip(config.gui.getToolTip("comcut"));
      captions.setTooltip(config.gui.getToolTip("captions"));
      encode.setTooltip(config.gui.getToolTip("encode"));
      //push.setTooltip(config.gui.getToolTip("push"));
      custom.setTooltip(config.gui.getToolTip("custom"));
      encoding_name.setTooltip(config.gui.getToolTip("encoding"));
      encoding_name2.setTooltip(config.gui.getToolTip("encoding2"));
      encoding_name2_suffix.setTooltip(config.gui.getToolTip("encoding2_suffix"));
      table.TABLE.setTooltip(getToolTip("table"));
      type.setTooltip(getToolTip("type"));
      tivo.setTooltip(getToolTip("tivo"));
      dry_run.setTooltip(getToolTip("dry_run"));
      noJobWait.setTooltip(getToolTip("noJobWait"));
      title.setTooltip(getToolTip("title"));
      comskipIni.setTooltip(getToolTip("comskipIni"));
      channelFilter.setTooltip(getToolTip("channelFilter"));
      tivoFileNameFormat.setTooltip(getToolTip("tivoFileNameFormat"));
      check_interval.setTooltip(getToolTip("check_interval"));
      add.setTooltip(getToolTip("add"));
      update.setTooltip(getToolTip("update"));
      del.setTooltip(getToolTip("del"));      
      dateFilter.setTooltip(getToolTip("dateFilter"));
      suggestionsFilter.setTooltip(getToolTip("suggestionsFilter"));
      suggestionsFilter_single.setTooltip(getToolTip("suggestionsFilter_single"));
      useProgramId_unique.setTooltip(getToolTip("useProgramId_unique"));
      kuidFilter.setTooltip(getToolTip("kuidFilter"));
      programIdFilter.setTooltip(getToolTip("programIdFilter"));
      dateOperator.setTooltip(getToolTip("dateOperator"));
      dateHours.setTooltip(getToolTip("dateHours"));
      OK.setTooltip(getToolTip("OK"));
      CANCEL.setTooltip(getToolTip("CANCEL"));      
   }
   
   public Tooltip getToolTip(String component) {
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
      tivo.getItems().clear();
      String[] names = getTivoFilterNames();
      for (int i=0; i<names.length; ++i) {
         tivo.getItems().add(names[i]);
      }
      if (tivo.getItems().size() > 0)
         tivo.setValue(tivo.getItems().get(0));
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
         qsfix.setDisable(true);
      } else {
         qsfix.setDisable(false);
      }
      
      if (!config.twpDeleteEnabled()) {
         twpdelete.setSelected(false);
         twpdelete.setDisable(true);
      } else {
         twpdelete.setDisable(false);
      }
      
      if ( ! config.rpcDeleteEnabled() ) {
         rpcdelete.setSelected(false);
         rpcdelete.setDisable(true);
      } else {
         rpcdelete.setDisable(false);
      }

      if (! file.isFile(config.comskip)) {
         comskip.setSelected(false);
         comskip.setDisable(true);
      } else {
         comskip.setDisable(false);
      }

      if (config.VRD == 0 && ! file.isFile(config.ffmpeg)) {
         comcut.setSelected(false);
         comcut.setDisable(true);
      } else {
         comcut.setDisable(false);
      }

      if (! file.isFile(config.t2extract) && ! file.isFile(config.ccextractor)) {
         captions.setSelected(false);
         captions.setDisable(true);
      } else {
         captions.setDisable(false);
      }
      if (config.VRD == 0 && qsfix.isSelected()) {
         captions.setSelected(false);
         captions.setDisable(true);         
      }

      if (! file.isFile(config.ffmpeg) &&
          ! file.isFile(config.mencoder) &&
          ! file.isFile(config.handbrake) ) {
         encode.setSelected(false);
         encode.setDisable(true);
      } else {
         encode.setDisable(false);
      }

      /*if ( ! file.isFile(config.pyTivo_config) ) {
         push.setSelected(false);
         push.setDisable(true);
      } else {
         push.setDisable(false);
      }*/
      
      if ( ! com.tivo.kmttg.task.custom.customCommandExists() ) {
         custom.setSelected(false);
         custom.setDisable(true);
      } else {
         custom.setDisable(false);
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
      dateOperator.setValue(autoConfig.dateOperator);
      dateHours.setText("" + autoConfig.dateHours);
      suggestionsFilter.setSelected((Boolean)(autoConfig.suggestionsFilter == 1));
      kuidFilter.setSelected((Boolean)(autoConfig.kuidFilter == 1));
      programIdFilter.setSelected((Boolean)(autoConfig.programIdFilter == 1));
   }
   
   // Set encoding_name ComboBox choices
   public void SetEncodings(Stack<String> values) {
      debug.print("values=" + values);
      
      encoding_name.getItems().clear();
      encoding_name2.getItems().clear();
      
      // Second encoding optional
      encoding_name2.getItems().add(_noSecondEncodingTxt);
      
      for (int i=0; i<values.size(); ++i) {
         encoding_name.getItems().add(values.get(i));
         encoding_name2.getItems().add(values.get(i));
      }
      if (encoding_name.getItems().size() > 0)
         encoding_name.setValue(encoding_name.getItems().get(0));
      if (encoding_name2.getItems().size() > 0)
         encoding_name2.setValue(encoding_name2.getItems().get(0));      
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
      String ktype = type.getValue();
      String keywords = string.removeLeadingTrailingSpaces(title.getText());
      if (keywords.length() == 0) {
         log.error("No keywords specified");
         return;
      }
      
      // Make sure this is not a duplicate entry
      Boolean duplicate = false;
      if (table.TABLE.getItems().size() > 0) {
         for (int i=0; i<table.TABLE.getItems().size(); ++i) {
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
      Tabentry e = table.TABLE.getItems().get(row);
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
         ofp.write("<dateOperator>\n" + dateOperator.getValue() + "\n\n");
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
         
         int rows = table.TABLE.getItems().size();
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
      dialog.hide();
      
      // Update autoConfig settings      
      autoConfig.parseAuto(config.autoIni);
   }
   
   // Callback when user clicks on a table row
   // This will update component settings according to selected row data
   private void TableRowSelected(autoEntry entry) {
      enabled.setSelected((Boolean)(entry.enabled == 1));
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
      
      encoding_name.setValue(entry.encode_name);
      
      if (entry.encode_name2 != null) {
    	  encoding_name2.setValue(entry.encode_name2);
    	  encoding_name2_suffix.setText(entry.encode_name2_suffix);
      } else
    	  encoding_name2.setValue(_noSecondEncodingTxt);
      
      comskipIni.setText(entry.comskipIni);
      
      if (entry.channelFilter != null)
         channelFilter.setText(entry.channelFilter);
      else
         channelFilter.setText("");
      
      if (entry.tivoFileNameFormat != null)
         tivoFileNameFormat.setText(entry.tivoFileNameFormat);
      else
         tivoFileNameFormat.setText("");
      
      type.setValue(entry.type);
      
      entry.tivo = validateTivoName(entry.tivo);
      tivo.setValue(entry.tivo);
      
      if (entry.type.equals("title")) {
         title.setText(entry.keyword);
      } else {
         title.setText(autoConfig.keywordsToString(entry.keywords));
      }
   }
   
   private Boolean guiToEntry(autoEntry entry) {
      String ktype = type.getValue();
      String ktivo = tivo.getValue();
      String keywords = string.removeLeadingTrailingSpaces(title.getText());
      if (keywords.length() == 0) {
         log.error("No keywords specified");
         return false;
      }
      
      if (enabled.isSelected())
         entry.enabled = 1;
      else
         entry.enabled = 0;
      
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
      
      entry.encode_name = encoding_name.getValue();
      
      // Does user want to encode second time? save profile name
      if (encoding_name2.getValue().equals(_noSecondEncodingTxt))
    	  entry.encode_name2 = null;
      else {
    	  entry.encode_name2 = encoding_name2.getValue();
    	  entry.encode_name2_suffix = encoding_name2_suffix.getText();
      }

      String ini = (String)string.removeLeadingTrailingSpaces(comskipIni.getText());
      if (ini.length() > 0 && ! ini.equals("none")) {
         if ( ! file.isFile(ini) ) {
            log.error("Specified comskip.ini override file does not exist...");
         }
      }
      entry.comskipIni = ini;
      
      String cFilter = (String)string.removeLeadingTrailingSpaces(channelFilter.getText());
      if (cFilter.length() > 0)
         entry.channelFilter = cFilter;
      else
         entry.channelFilter = null;
      
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
