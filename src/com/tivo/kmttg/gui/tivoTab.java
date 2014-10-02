package com.tivo.kmttg.gui;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyBoundsListener;
import java.awt.event.HierarchyEvent;
import java.io.File;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Stack;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.table.TableColumnModel;

import com.tivo.kmttg.main.auto;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.http;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.pyTivo;
import com.tivo.kmttg.util.string;

public class tivoTab {
   String tivoName = null;
   private JPanel panel = null;
   private JButton add = null;
   private JButton remove = null;
   private JButton atomic = null;
   private JButton pyTivo_stream = null;
   private JButton refresh = null;
   private JButton disk_usage = null;
   private JLabel status = null;
   private JCheckBox showFolders = null;
   private nplTable nplTab = null;
   private fileBrowser browser = null;
   private JFileChooser csvBrowser = null;
   
   tivoTab(final String name) {
      debug.print("name=" + name);
      this.tivoName = name;
      panel = new JPanel(new GridBagLayout());
      nplTab = new nplTable(name);

      // Pack table columns when panel resized
      panel.addHierarchyBoundsListener(new HierarchyBoundsListener() {
         public void ancestorMoved(HierarchyEvent arg0) {
            // Don't care about movement
         }
         public void ancestorResized(HierarchyEvent arg0) {
            nplTab.packColumns(nplTab.NowPlaying, 2);
         }
      });
      
      GridBagConstraints c = new GridBagConstraints();
      c.insets = new Insets(0, 2, 0, 2);
      c.ipadx = 0;
      c.ipady = 0;
      c.weighty = 0.0;  // default to no vertical stretch
      c.weightx = 0.0;  // default to no horizontal stretch
      c.gridwidth = 1;
      c.gridheight = 1;
      c.anchor = GridBagConstraints.CENTER;
      c.fill = GridBagConstraints.HORIZONTAL;

      int gx=0, gy=0;
      
      if (name.equals("FILES")) {
         // This is a FILES tab
         nplTab.SetNowPlayingHeaders(nplTab.FILE_cols);
         
         // Create File Browser instance
         browser = new fileBrowser();
         
         // Add button
         add = new JButton("Add...");
         add.setMargin(new Insets(0,5,0,5));
         add.setToolTipText(config.gui.getToolTip("add"));
         add.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
               addCB(add);
            }
         });         
   
         // Remove button
         remove = new JButton("Remove");
         remove.setMargin(new Insets(0,5,0,5));
         remove.setToolTipText(config.gui.getToolTip("remove"));
         remove.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
               removeCB(remove);
            }
         });

         // Create row with Add, Remove, atomic
         JPanel row = new JPanel();
         row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
         Dimension space1 = new Dimension(5,0);
         Dimension space2 = new Dimension(20,0);
         row.add(add);
         row.add(Box.createRigidArea(space1));
         row.add(remove);
         
         // atomic button
         if ( file.isFile(config.AtomicParsley) ) {
            atomic = new JButton("Run AtomicParsley");
            atomic.setMargin(new Insets(0,5,0,5));
            atomic.setToolTipText(config.gui.getToolTip("atomic"));
            atomic.addActionListener(new java.awt.event.ActionListener() {
               public void actionPerformed(java.awt.event.ActionEvent e) {
                  atomicCB(atomic);
               }
            });
            row.add(Box.createRigidArea(space2));
            row.add(atomic);
         }
         
         // pyTivo stream button
         if ( config.ipadEnabled() && file.isFile(config.pyTivo_config) ) {
            pyTivo_stream = new JButton("pyTivo stream");
            pyTivo_stream.setMargin(new Insets(0,5,0,5));
            pyTivo_stream.setToolTipText(config.gui.getToolTip("pyTivo_stream"));
            pyTivo_stream.addActionListener(new java.awt.event.ActionListener() {
               public void actionPerformed(java.awt.event.ActionEvent e) {
                  pyTivo_streamCB();
               }
            });
            row.add(Box.createRigidArea(space2));
            row.add(pyTivo_stream);
         }
         
         c.gridx = gx;
         c.gridy = gy;
         c.gridwidth = 1;
         panel.add(row, c);
      } else {
         // This is a TiVo tab
         nplTab.SetNowPlayingHeaders(nplTab.TIVO_cols);
         
         // Refresh button
         refresh = new JButton("Refresh");
         refresh.setMargin(new Insets(0,5,0,5));
         refresh.setToolTipText(config.gui.getToolTip("refresh"));
         refresh.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
               if (nplTab.inFolder) {
                  // Return from folder display mode to top level mode
                  nplTab.setFolderState(false);
                  nplTab.RefreshNowPlaying(null);
                  if (nplTab.folderEntryNum >= 0) {
                     nplTab.SelectFolder(nplTab.folderName);
                  }
               } else {
                  // Refresh now playing list mode
                  //getTable().NowPlaying.clearSelection();
                  //getTable().clear();
                  jobMonitor.getNPL(name);
               }
            }
         });
         gx++;
         c.gridx = gx;
         c.gridy = gy;
         c.gridwidth = 1;
         panel.add(refresh, c);
         
         // Disk Usage button
         if ( ! tivoName.equals("FILES") ) {
            disk_usage = new JButton("Disk Usage");
            disk_usage.setMargin(new Insets(0,5,0,5));
            disk_usage.setToolTipText(config.gui.getToolTip("disk_usage"));
            disk_usage.addActionListener(new java.awt.event.ActionListener() {
               public void actionPerformed(java.awt.event.ActionEvent e) {
                  new freeSpace(tivoName, config.gui.getJFrame());
               }
            });
            gx++;
            c.gridx = gx;
            c.gridy = gy;
            panel.add(disk_usage, c);
         }
         
         // Export button
         if ( ! tivoName.equals("FILES") ) {
            JButton export = new JButton("Export...");
            export.setMargin(new Insets(0,5,0,5));
            export.setToolTipText(config.gui.getToolTip("export_npl"));
            export.addActionListener(new java.awt.event.ActionListener() {
               public void actionPerformed(java.awt.event.ActionEvent e) {
                  if (csvBrowser == null) {
                     csvBrowser = new JFileChooser(config.programDir);
                     csvBrowser.setMultiSelectionEnabled(false);
                  }
                  csvBrowser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                  csvBrowser.setFileFilter(new FileFilterSP(".csv"));
                  csvBrowser.setSelectedFile(
                     new File(
                        config.programDir + File.separator + tivoName +
                        "_npl_" + TableUtil.currentYearMonthDay() + ".csv"
                     )
                  );
                  int result = csvBrowser.showDialog(config.gui.getJFrame(), "Export to csv file");
                  if (result == JFileChooser.APPROVE_OPTION) {
                     nplTab.exportNPL(csvBrowser.getSelectedFile().getAbsolutePath());
                  }
               }
            });
            gx++;
            c.gridx = gx;
            c.gridy = gy;
            panel.add(export, c);
         }
         
         // Status label
         status = new JLabel();
         gx++;
         c.gridx = gx;
         c.gridy = gy;
         c.gridwidth = 1;
         panel.add(status, c);
         
         // showFolders
         if ( ! tivoName.equals("FILES") ) {
            showFolders = new JCheckBox("Show Folders");
            showFolders.addActionListener(new ActionListener() {
               // Toggle between folder mode and non folder mode display
               public void actionPerformed(ActionEvent e) {
                  // Reset to top level display
                  nplTab.setFolderState(false);
                  nplTab.folderEntryNum = -1;
                  
                  // Refresh to show top level entries
                  nplTab.RefreshNowPlaying(null);
               }
            });
            gx++;
            c.gridx = gx;
            c.gridy = gy;
            c.gridwidth = 1;
            panel.add(showFolders, c);
         }
      }
      
      // nplTable
      gx = 0; gy++;
      c.weightx = 1.0;    // stretch vertically
      c.weighty = 1.0;    // stretch horizontally
      c.gridheight = 1;
      c.gridwidth = 8;
      c.gridx = gx;
      c.gridy = gy;
      //c.ipady = 100;
      c.fill = GridBagConstraints.BOTH;         
      panel.add(nplTab.nplScroll, c);
   }
   
   public Boolean showFolders() {
      if (showFolders == null) return false;
      return showFolders.isSelected();
   }
   
   public void showFoldersVisible(Boolean visible) {
      showFolders.setVisible(visible);
   }
   
   public void showDiskUsageVisible(Boolean visible) {
      disk_usage.setVisible(visible);
   }
   
   public void showFoldersSet(Boolean value) {
      showFolders.setSelected(value);
   }
   
   public JPanel getPanel() {
      return panel;
   }
   
   public JButton getRefreshButton() {
      return refresh;
   }
   
   public nplTable getTable() {
      return nplTab;
   }
   
   // FILES mode add button callback
   // Bring up file browser and add selected entries to Now Playing
   private void addCB(JButton button) {
      debug.print("button=" + button);
      // Bring up File Browser
      int result = browser.Browser.showDialog(nplTab.NowPlaying, "Add");
      if (result == JFileChooser.APPROVE_OPTION) {
         File[] files = browser.Browser.getSelectedFiles();
         for (int i=0; i<files.length; ++i) {
            // workaround for http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6351074
            // file browser trims the file name so it may have originally contained a space
            // if user selected a file that doesn't exist then look for the same name with preceeding space
            if (!files[i].exists()) {
               // look for same file but with space
               String new_filename = files[i].getParent() + File.separatorChar + " " + files[i].getName();
               File f = new File(new_filename);
               if (f.exists()) {
                  nplTab.AddNowPlayingFileRow(f);
               } else {
                  log.error("You selected a file which could not be found: " + files[i].getAbsolutePath());
               }
            } else {
               nplTab.AddNowPlayingFileRow(files[i]);
            }
         }
      }
   }

   // FILES mode remove button callback
   // Remove selected NowPlaying entries from list
   private void removeCB(JButton button) {
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
   private void atomicCB(JButton button) {
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
   private void pyTivo_streamCB() {
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
         Stack<String> n = new Stack<String>();
         for (int j=0; j<o.size(); ++j) {
            if ( config.rpcEnabled(o.get(j)) )
               n.add(o.get(j));
         }
         if (n.size() > 0) {
            Object[] tivos = n.toArray();   
            int row;
            for (int i=rows.length-1; i>=0; i--) {
               row = rows[i];
               String videoFile = nplTab.NowPlayingGetSelectionFile(row);
               String tivoName = (String)JOptionPane.showInputDialog(
                 config.gui.getJFrame(),
                 videoFile,
                 "Choose destination TiVo",
                 JOptionPane.PLAIN_MESSAGE,
                 null,
                 tivos,
                 tivos[0]
               );
               if (tivoName != null && tivoName.length() > 0)
                  pyTivo.streamFile(tivoName, videoFile);
            }
         } else {
            log.error("No RPC enabled TiVos found in kmttg config");
         }
      }
   }

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
               h.put("ipaddelete", config.gui.ipaddelete.isSelected() && config.rpcEnabled(tivoName));
               h.put("comskip",    config.gui.comskip.isSelected());
               h.put("comcut",     config.gui.comcut.isSelected());
               h.put("captions",   config.gui.captions.isSelected());
               h.put("encode",     config.gui.encode.isSelected());
               h.put("push",       config.gui.push.isSelected());
               h.put("custom",     config.gui.custom.isSelected());
               jobMonitor.LaunchJobs(h);
            }
         }
      }
   }
   
   public void nplTab_packColumns(int pad) {
      debug.print("pad=" + pad);
      nplTab.packColumns(nplTab.NowPlaying, pad);
   }
   
   public void nplTab_SetNowPlaying(Stack<Hashtable<String,String>> h) {
      debug.print("h=" + h);
      nplTab.SetNowPlaying(h);
   }
   
   public void nplTab_UpdateStatus(String s) {
      debug.print("s=" + s);
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
      int size = nplTab.NowPlaying.getColumnCount();
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
      if (nplTab.NowPlaying.getColumnCount() != order.length) return;
      
      // Re-order to desired positions
      String colName;
      int index;
      for (int i=0; i<order.length; ++i) {
         colName = order[i];
         if (colName.equals("ICON")) colName = "";
         index = nplTab.getColumnIndex(colName);
         if ( index != -1)
            moveColumn(index, i);
      }
   }
   
   // Move a table column from -> to
   public void moveColumn(int from, int to) {
      debug.print("from=" + from + " to=" + to);
      TableColumnModel tableColumnModel = nplTab.NowPlaying.getTableHeader().getColumnModel();
      tableColumnModel.moveColumn(from, to);
   }
}
