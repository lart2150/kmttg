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
//import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
//import java.util.Optional;
import java.util.Stack;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
//import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;

import com.tivo.kmttg.gui.dialog.freeSpace;
import com.tivo.kmttg.gui.remote.util;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.nplTable.Tabentry;
import com.tivo.kmttg.gui.table.nplTable;
import com.tivo.kmttg.main.auto;
import com.tivo.kmttg.main.config;
//import com.tivo.kmttg.main.http;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.SkipImport;
import com.tivo.kmttg.rpc.SkipManager;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
//import com.tivo.kmttg.util.pyTivo;
import com.tivo.kmttg.util.string;

public class tivoTab {
   String tivoName = null;
   private VBox panel = null;
   private Button add = null;
   private Button remove = null;
   private Button atomic = null;
   //private Button pyTivo_stream = null;
   private Button refresh = null;
   private Button disk_usage = null;
   private Label status = null;
   private CheckBox showFolders = null;
   private CheckBox partiallyViewed = null;
   private nplTable nplTab = null;
   private fileBrowser browser = null;
   private FileChooser csvBrowser = null;
   
   tivoTab(final String name) {
      debug.print("name=" + name);
      this.tivoName = name;
      panel = new VBox();
      panel.setSpacing(5);
      panel.setPadding(new Insets(5,0,0,0));
      // Setup Col1 to fill horizontally
      ColumnConstraints fillColumn = new ColumnConstraints();
      fillColumn.setFillWidth(true);
      fillColumn.setHgrow(Priority.ALWAYS);
      RowConstraints fillVertical = new RowConstraints();
      fillVertical.setFillHeight(true);
      fillVertical.setVgrow(Priority.ALWAYS);
      nplTab = new nplTable(name);
      
      if (name.equals("FILES")) {
         // This is a FILES tab
         nplTab.SetNowPlayingHeaders(nplTab.FILE_cols);
         
         // Create File Browser instance
         browser = new fileBrowser();
         
         // Add button
         add = new Button("Add...");
         add.setTooltip(config.gui.getToolTip("add"));
         add.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               addCB(add);
            }
         });         
   
         // Remove button
         remove = new Button("Remove");
         remove.setTooltip(config.gui.getToolTip("remove"));
         remove.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               removeCB(remove);
            }
         });

         // Create row with Add, Remove, atomic
         HBox row = new HBox();
         row.setAlignment(Pos.CENTER_LEFT);
         row.setPadding(new Insets(0,0,0,5));
         row.setSpacing(5);
         row.getChildren().addAll(add, remove);
         
         // atomic button
         if ( file.isFile(config.AtomicParsley) ) {
            atomic = new Button("Run AtomicParsley");
            atomic.setTooltip(config.gui.getToolTip("atomic"));
            atomic.setOnAction(new EventHandler<ActionEvent>() {
               public void handle(ActionEvent e) {
                  atomicCB(atomic);
               }
            });
            row.getChildren().addAll(util.space(20), atomic);
         }
         
         // pyTivo stream button
         /*if ( config.rpcEnabled() && file.isFile(config.pyTivo_config) ) {
            pyTivo_stream = new Button("pyTivo stream");
            pyTivo_stream.setTooltip(config.gui.getToolTip("pyTivo_stream"));
            pyTivo_stream.setOnAction(new EventHandler<ActionEvent>() {
               public void handle(ActionEvent e) {
                  pyTivo_streamCB();
               }
            });
            row.getChildren().addAll(util.space(20), pyTivo_stream);
         }*/
         
         panel.getChildren().add(row);
      } else {         
         // This is a TiVo tab
         HBox row = new HBox();
         row.setAlignment(Pos.CENTER_LEFT);
         row.setPadding(new Insets(0,0,0,5));
         row.setSpacing(5);
         nplTab.SetNowPlayingHeaders(nplTab.TIVO_cols);
         
         // Refresh button
         refresh = new Button("Refresh");
         refresh.setTooltip(config.gui.getToolTip("refresh"));
         refresh.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               // Refresh now playing list mode
               jobMonitor.getNPL(name);
            }
         });
         row.getChildren().add(refresh);
         
         // Disk Usage button
         if ( ! tivoName.equals("FILES") ) {
            disk_usage = new Button("Disk Usage");
            disk_usage.setTooltip(config.gui.getToolTip("disk_usage"));
            disk_usage.setOnAction(new EventHandler<ActionEvent>() {
               public void handle(ActionEvent e) {
                  new freeSpace(tivoName, config.gui.getFrame());
               }
            });
            row.getChildren().add(disk_usage);
         }
         
         // Export button
         if ( ! tivoName.equals("FILES") ) {
            Button export = new Button("Export...");
            export.setTooltip(config.gui.getToolTip("export_npl"));
            export.setOnAction(new EventHandler<ActionEvent>() {
               public void handle(ActionEvent e) {
                  if (csvBrowser == null) {
                     csvBrowser = new FileChooser();
                     csvBrowser.setTitle("Export to csv file");
                     csvBrowser.getExtensionFilters().clear();
                     csvBrowser.getExtensionFilters().addAll(new ExtensionFilter("CSV Files", "*.csv"));
                     csvBrowser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ALL FILES", "*"));
                     csvBrowser.setInitialDirectory(new File(config.programDir));
                  }
                  csvBrowser.setInitialFileName(tivoName + "_npl_" + TableUtil.currentYearMonthDay() + ".csv");
                  File selectedFile = csvBrowser.showSaveDialog(config.gui.getFrame());
                  if (selectedFile != null) {
                     nplTab.exportNPL(selectedFile.getAbsolutePath());
                  }
               }
            });
            row.getChildren().add(export);
         }
         
         // Prune button
         if ( ! tivoName.equalsIgnoreCase("FILES") && config.rpcEnabled(tivoName) && SkipManager.skipEnabled()) {
            Button prune = new Button("Prune skipTable");
            prune.setTooltip(config.gui.getToolTip("prune_skipTable"));
            prune.setOnAction(new EventHandler<ActionEvent>() {
               public void handle(ActionEvent e) {
                  SkipManager.pruneEntries(tivoName, nplTab.getEntries());
               }
            });
            row.getChildren().add(prune);
         }
         
         // Import skip button
         if ( ! tivoName.equalsIgnoreCase("FILES") && config.rpcEnabled(tivoName) && SkipManager.skipEnabled()) {
            Button import_skip = new Button("Import skip");
            import_skip.setTooltip(config.gui.getToolTip("import_skip"));
            import_skip.setOnAction(new EventHandler<ActionEvent>() {
               public void handle(ActionEvent e) {
                  int[] rows = nplTab.GetSelectedRows();
                  int row;
                  for (int i=0; i<rows.length; i++) {
                     row = rows[i];
                     Stack<Hashtable<String,String>> rowData = nplTab.getRowData(row);
                     for (Hashtable<String,String> h : rowData) {
                        SkipImport.importEntry(tivoName, h);
                     }
                  }
               }
            });
            row.getChildren().add(import_skip);
         }
         
         // Status label
         status = new Label();
         row.getChildren().add(status);
         
         // showFolders and partiallyViewed
         if ( ! tivoName.equals("FILES") ) {
            showFolders = new CheckBox("Show Folders");
            showFolders.setOnAction(new EventHandler<ActionEvent>() {
               // Toggle between folder mode and non folder mode display
               public void handle(ActionEvent e) {
                  // Reset to top level display
                  nplTab.folderEntryNum = -1;
                  
                  // Refresh to show top level entries
                  nplTab.RefreshNowPlaying(null);
               }
            });
            row.getChildren().add(showFolders);
            
            if (config.rpcEnabled(tivoName)) {
               partiallyViewed = new CheckBox("Partially Viewed");
               partiallyViewed.setTooltip(config.gui.getToolTip("partiallyViewed"));
               row.getChildren().add(partiallyViewed);
               partiallyViewed.setOnAction(new EventHandler<ActionEvent>() {
                  public void handle(ActionEvent e) {
                     nplTab.displayUpdate(partiallyViewed.isSelected());
                  }
               });
            }
         }
         
         panel.getChildren().add(row);
      }
      
      // nplTable
      VBox.setVgrow(nplTab.NowPlaying, Priority.ALWAYS); // stretch vertically
      panel.getChildren().add(nplTab.NowPlaying);
   }
   
   public Boolean showFolders() {
      debug.print("");
      if (showFolders == null) return false;
      return showFolders.isSelected();
   }
   
   public Boolean partiallyViewed() {
      debug.print("");
      if (partiallyViewed == null) return false;
      return partiallyViewed.isSelected();
   }
   
   public void showFoldersVisible(Boolean visible) {
      debug.print("visible=" + visible);
      showFolders.setVisible(visible);
   }
   
   public void showDiskUsageVisible(Boolean visible) {
      debug.print("visible=" + visible);
      disk_usage.setVisible(visible);
   }
   
   public void showFoldersSet(Boolean value) {
      debug.print("value=" + value);
      showFolders.setSelected(value);
   }
   
   public VBox getPanel() {
      debug.print("");
      return panel;
   }
   
   public Button getRefreshButton() {
      debug.print("");
      return refresh;
   }
   
   public nplTable getTable() {
      debug.print("");
      return nplTab;
   }
   
   // FILES mode add button callback
   // Bring up file browser and add selected entries to Now Playing
   private void addCB(Button button) {
      debug.print("button=" + button);
      // Bring up File Browser
      browser.Browser.setTitle("Add");
      browser.Browser.setInitialDirectory(new File(config.TIVOS.get("FILES")));
      List<File> files = browser.Browser.showOpenMultipleDialog(null);
      if (files != null) {
         for (int i=0; i<files.size(); ++i) {
            // workaround for http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6351074
            // file browser trims the file name so it may have originally contained a space
            // if user selected a file that doesn't exist then look for the same name with preceeding space
            if (!files.get(i).exists()) {
               // look for same file but with space
               String new_filename = files.get(i).getParent() + File.separatorChar + " " + files.get(i).getName();
               File f = new File(new_filename);
               if (f.exists()) {
                  nplTab.AddNowPlayingFileRow(f);
               } else {
                  log.error("You selected a file which could not be found: " + files.get(i).getAbsolutePath());
               }
            } else {
               nplTab.AddNowPlayingFileRow(files.get(i));
            }
         }
      }
   }

   // FILES mode remove button callback
   // Remove selected NowPlaying entries from list
   private void removeCB(Button button) {
      debug.print("button=" + button);
      if ( tivoName.equals("FILES") ) {
         int[] rows = nplTab.GetSelectedRows();

         if (rows.length > 0) {
            int row;
            for (int i=rows.length-1; i>=0; i--) {
               row = rows[i];
               nplTab.RemoveSelectedRow(row);
            }
         }
      }
   }

   // FILES mode atomic button callback
   // Run AtomicParsley for selected FILES entries
   private void atomicCB(Button button) {
      debug.print("button=" + button);
      if ( tivoName.equals("FILES") ) {
         if (! file.isFile(config.AtomicParsley)) {
            log.error("AtomicParsley binary not found: " + config.AtomicParsley);
            return;
         }
         int[] rows = nplTab.GetSelectedRows();

         if (rows.length > 0) {
            int row;
            for (int i=rows.length-1; i>=0; i--) {
               row = rows[i];
               // Schedule an AtomicParsley job if relevant
               String encodeFile = nplTab.NowPlayingGetSelectionFile(row);
               if ( encodeFile.toLowerCase().endsWith(".mp4") ||
                    encodeFile.toLowerCase().endsWith(".m4v")) {
                  String metaFile = encodeFile + ".txt";
                  if ( ! file.isFile(metaFile) ) {
                     metaFile = string.replaceSuffix(encodeFile, "_cut.mpg.txt");
                  }
                  if ( ! file.isFile(metaFile) ) {
                     metaFile = string.replaceSuffix(encodeFile, ".mpg.txt");
                  }
                  if ( ! file.isFile(metaFile) ) {
                     metaFile = string.replaceSuffix(encodeFile, ".TiVo.txt");
                  }
                  if ( file.isFile(metaFile) ) {
                     log.warn("Manual AtomicParsley using metadata file: " + metaFile);
                     jobData new_job = new jobData();
                     new_job.source       = encodeFile;
                     new_job.tivoName     = "FILES";
                     new_job.type         = "atomic";
                     new_job.name         = config.AtomicParsley;
                     new_job.encodeFile   = encodeFile;
                     new_job.metaFile     = metaFile;
                     jobMonitor.submitNewJob(new_job);
                  } else {
                     log.error("Cannot find a pyTivo metadata file to use for AtomicParsley run: (file=" + encodeFile + ")");
                  }
               } else {
                  log.error("File does not have mp4 or m4v suffix: " + encodeFile);
               }
            }
         }
      }
   }

   // FILES mode pyTivo stream button callback
   /*private void pyTivo_streamCB() {
      debug.print("");
      if ( tivoName.equals("FILES") ) {
         if (! file.isFile(config.pyTivo_config)) {
            log.error("pyTivo config file does not exist: " + config.pyTivo_config);
            return;
         }
         int[] rows = nplTab.GetSelectedRows();
         if (rows.length <= 0)
            return;
         
         // Check if pyTivo server is alive
         String host = config.pyTivo_host;
         if (host.equals("localhost")) {
            host = http.getLocalhostIP();
            if (host == null)
               return;
         }
         String urlString = "http://" + host + ":" + config.pyTivo_port;
         if (! http.isAlive(urlString, 2)) {
            log.error("pyTivo server not responding");
            return;
         }
         
         // NOTE: This is only valid for RPC enabled TiVos
         Stack<String> o = config.getTivoNames();
         List<String> tivos = new ArrayList<String>();
         for (int j=0; j<o.size(); ++j) {
            if ( config.rpcEnabled(o.get(j)) )
               tivos.add(o.get(j));
         }
         if (tivos.size() > 0) {
            int row;
            for (int i=rows.length-1; i>=0; i--) {
               row = rows[i];
               String videoFile = nplTab.NowPlayingGetSelectionFile(row);
               ChoiceDialog<String> dialog = new ChoiceDialog<String>((String) tivos.get(0), tivos);
               dialog.setTitle("Choose destination TiVo");
               dialog.setContentText("TiVo:");
               String tivoName = null;
               Optional<String> result = dialog.showAndWait();
               if (result.isPresent())
                  tivoName = result.get();
               if (tivoName != null && tivoName.length() > 0)
                  pyTivo.streamFile(tivoName, videoFile);
            }
         } else {
            log.error("No RPC enabled TiVos found in kmttg config");
         }
      }
   }*/

   // Start button callback
   // Process selected Now Playing entries
   public void startCB() {
      debug.print("");
      int[] rows = nplTab.GetSelectedRows();

      if (rows.length > 0) {
         int row;
         for (int i=0; i<rows.length; i++) {
            row = rows[i];
            Stack<Hashtable<String,Object>> entries = new Stack<Hashtable<String,Object>>();
            if ( tivoName.equals("FILES") ) {
               Hashtable<String,Object> h = new Hashtable<String,Object>();
               h.put("tivoName", tivoName);
               h.put("mode", "FILES");
               String fileName = nplTab.NowPlayingGetSelectionFile(row);
               if (fileName != null) {
                  h.put("startFile", fileName);
                  entries.add(h);
               }
            } else {
               Stack<Hashtable<String,String>> rowData = nplTab.getRowData(row);
               for (int j=0; j<rowData.size(); ++j) {
                  Hashtable<String,Object> h = new Hashtable<String,Object>();
                  h.put("tivoName", tivoName);
                  h.put("mode", "Download");
                  h.put("entry", rowData.get(j));
                  entries.add(h);
               }
            }
            
            // Launch jobs appropriately
            for (int j=0; j<entries.size(); ++j) {
               Hashtable<String,Object> h = entries.get(j);
               if (tivoName.equals("FILES")) {
                  h.put("metadataTivo", config.gui.metadata.isSelected());
                  h.put("metadata", false);
               } else {
                  h.put("metadata", config.gui.metadata.isSelected());
                  h.put("metadataTivo", false);
               }
               h.put("decrypt",    config.gui.decrypt.isSelected());
               h.put("qsfix",      config.gui.qsfix.isSelected());
               h.put("twpdelete",  config.gui.twpdelete.isSelected());
               h.put("rpcdelete", config.gui.rpcdelete.isSelected() && config.rpcEnabled(tivoName));
               h.put("comskip",    config.gui.comskip.isSelected());
               h.put("comcut",     config.gui.comcut.isSelected());
               h.put("captions",   config.gui.captions.isSelected());
               h.put("encode",     config.gui.encode.isSelected());
               //h.put("push",       config.gui.push.isSelected());
               h.put("custom",     config.gui.custom.isSelected());
               jobMonitor.LaunchJobs(h);
            }
         }
      }
   }
   
   public void nplTab_packColumns(int pad) {
      debug.print("pad=" + pad);
      TableUtil.autoSizeTableViewColumns(nplTab.NowPlaying, false);
   }
   
   public void nplTab_SetNowPlaying(Stack<Hashtable<String,String>> h) {
      debug.print("h=" + h);
      nplTab.SetNowPlaying(h);
   }
   
   public void nplTab_UpdateStatus(String s) {
      debug.print("s=" + s);
      if (status != null)
         status.setText(s);
   }
   
   public void nplTab_clear() {
      debug.print("");
      nplTab.clear();
   }
   
   // Callback for "Add selected titles" Auto Transfers menu entry
   // This will add the selected Tivo show titles to auto.ini file
   public void autoSelectedTitlesCB() {
      debug.print("");
      
      // Do nothing if in FILES mode
      if ( tivoName.equals("FILES") ) return;
      
      // Process selected entries in nplTab
      int[] rows = nplTab.GetSelectedRows();
      if (rows.length > 0) {
         int row;
         for (int i=0; i<rows.length; i++) {
            row = rows[i];
            Hashtable<String,String> entry = nplTab.NowPlayingGetSelectionData(row);
            if (entry == null) {
               log.warn("Please select a non folder entry in table");
               continue;
            }
            if (entry.containsKey("titleOnly")) {
               auto.autoAddTitleEntryToFile(entry.get("titleOnly"));
            }
         }
      } else {
         log.error("No shows currently selected for processing");
      }
   }
   
   // Callback for "Add selected to history file" Auto Transfers menu entry
   // This will add the selected Tivo show titles to auto.history file
   public void autoSelectedHistoryCB() {
      debug.print("");
            
      // Do nothing if in FILES mode
      if ( tivoName.equals("FILES") ) return;
      
      // Process selected entries in nplTab
      int[] rows = nplTab.GetSelectedRows();
      if (rows.length > 0) {
         int row;
         for (int i=0; i<rows.length; i++) {
            row = rows[i];
            Hashtable<String,String> entry = nplTab.NowPlayingGetSelectionData(row);
            if (entry == null) {
               log.warn("Please select a non folder entry in table");
               continue;
            }
            if (entry.containsKey("ProgramId")) {
               int result = auto.AddHistoryEntry(entry);
               if (result == 1) {
                  log.print(">> Added '" + entry.get("title") + "' to " + config.autoHistory);
               }
               else if (result == 2) {
                  log.print(">> Entry '" + entry.get("title") + "' already in " + config.autoHistory);
               }
            }
         }
      } else {
         log.error("No shows currently selected for processing");
      }  
   }
   
   // Return current column name order as a string array
   public String[] getColumnOrder() {
      debug.print("");
      int size = nplTab.NowPlaying.getColumns().size();
      String[] order = new String[size];
      for (int i=0; i<size; ++i) {
         order[i] = nplTab.getColumnName(i);
      }
      return order;
   }
   
   // Change table column order according to given string array order
   public void setColumnOrder(String[] order) {
      debug.print("order=" + Arrays.toString(order));
      
      // Don't do anything if column counts don't match up
      if (nplTab.NowPlaying.getColumns().size() != order.length) return;
      
      // Re-order to desired positions
      String colName;
      int index;
      for (int i=0; i<order.length; ++i) {
         colName = order[i];
         if (colName.equals("ICON")) colName = "";
         index = TableUtil.getColumnIndex(nplTab.NowPlaying, colName);
         if ( index != -1)
            moveColumn(index, i);
      }
   }
   
   // Move a table column from -> to
   private void moveColumn(int from, int to) {
      debug.print("from=" + from + " to=" + to);
      int num = nplTab.NowPlaying.getColumns().size();
      Stack<TreeTableColumn<Tabentry,?>> order = new Stack<TreeTableColumn<Tabentry,?>>();
      for (int i=0; i<num; ++i) {
         int index = i;
         if (index == from)
            index = to;
         else if (index == to)
            index = from;
         TreeTableColumn<Tabentry,?> col = nplTab.NowPlaying.getColumns().get(index);
         order.push(col);
      }
      nplTab.NowPlaying.getColumns().clear();
      for (TreeTableColumn<Tabentry,?> col : order) {
         nplTab.NowPlaying.getColumns().add(col);
      }
   }   
}
