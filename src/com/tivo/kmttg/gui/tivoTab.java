package com.tivo.kmttg.gui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.HierarchyBoundsListener;
import java.awt.event.HierarchyEvent;
import java.io.File;
import java.util.Hashtable;
import java.util.Stack;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.table.TableColumnModel;

import com.tivo.kmttg.main.auto;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.task.NowPlaying;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class tivoTab {
   String tivoName = null;
   private JPanel panel = null;
   private JButton add = null;
   private JButton remove = null;
   private JButton refresh = null;
   private JLabel status = null;
   private nplTable nplTab = null;
   private fileBrowser browser = null;
   
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
         gx++;
         c.gridx = gx;
         c.gridy = gy;
         c.gridwidth = 1;
         panel.add(add, c);
   
         // Remove button
         remove = new JButton("Remove");
         remove.setMargin(new Insets(0,5,0,5));
         remove.setToolTipText(config.gui.getToolTip("remove"));
         remove.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
               removeCB(remove);
            }
         });
         gx++;
         c.gridx = gx;
         c.gridy = gy;
         panel.add(remove, c);
         gx++;
      } else {
         // This is a TiVo tab
         nplTab.SetNowPlayingHeaders(nplTab.TIVO_cols);
         
         // Refresh button
         refresh = new JButton("Refresh List");
         refresh.setMargin(new Insets(0,5,0,5));
         refresh.setToolTipText(config.gui.getToolTip("refresh"));
         refresh.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
               NowPlaying.submitJob(name);
            }
         });
         gx++;
         c.gridx = gx;
         c.gridy = gy;
         c.gridwidth = 1;
         panel.add(refresh, c);
         
         // Status label
         status = new JLabel();
         gx++;
         c.gridx = gx;
         c.gridy = gy;
         c.gridwidth = 1;
         panel.add(status, c);
      }
      
      // nplTable
      gx = 0; gy++;
      c.weightx = 1.0;    // stretch vertically
      c.weighty = 1.0;    // stretch horizontally
      c.gridheight = 1;
      c.gridwidth = 8;
      c.gridx = gx;
      c.gridy = gy;
      c.fill = GridBagConstraints.BOTH;         
      panel.add(nplTab.nplScroll, c);
   }
   
   public JPanel getPanel() {
      return panel;
   }
   
   // FILES mode add button callback
   // Bring up file browser and add selected entries to Now Playing
   private void addCB(JButton button) {
      debug.print("button=" + button);
      // Bring up File Browser
      int result = browser.Browser.showDialog(nplTab.NowPlaying, "Add");
      if (result == JFileChooser.APPROVE_OPTION) {
         File[] files = browser.Browser.getSelectedFiles();
         for (int i=0; i<files.length; ++i)
            nplTab.AddNowPlayingFileRow(files[i]);
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

   // Start button callback
   // Process selected Now Playing entries
   public void startCB() {
      debug.print("");
      int[] rows = nplTab.GetSelectedRows();

      if (rows.length > 0) {
         int row;
         for (int i=0; i<rows.length; i++) {
            row = rows[i];
            Hashtable<String,Object> h = new Hashtable<String,Object>();
            h.put("tivoName", tivoName);
            if ( tivoName.equals("FILES") ) {
               h.put("mode", "FILES");
               String fileName = nplTab.NowPlayingGetSelectionFile(row);
               if (fileName == null) return;
               h.put("startFile", fileName);
            } else {
               h.put("mode", "Download");
               Hashtable<String,String> entry = nplTab.NowPlayingGetSelectionData(row);
               if (entry == null) return;
               h.put("entry", entry);
            }
            
            // Launch jobs appropriately
            if (tivoName.equals("FILES")) {
               h.put("metadataTivo", config.gui.metadata.isSelected());
               h.put("metadata", false);
            } else {
               h.put("metadata", config.gui.metadata.isSelected());
               h.put("metadataTivo", false);
            }
            h.put("decrypt",  config.gui.decrypt.isSelected());
            h.put("qsfix",    config.gui.qsfix.isSelected());
            h.put("comskip",  config.gui.comskip.isSelected());
            h.put("comcut",   config.gui.comcut.isSelected());
            h.put("captions", config.gui.captions.isSelected());
            h.put("encode",   config.gui.encode.isSelected());
            h.put("custom",   config.gui.custom.isSelected());
            jobMonitor.LaunchJobs(h);
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
      nplTab.clear(nplTab.NowPlaying);
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
      debug.print("order=" + order);
      
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
