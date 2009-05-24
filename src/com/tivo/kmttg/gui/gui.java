package com.tivo.kmttg.gui;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.net.URL;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Stack;

import javax.swing.*;

import com.tivo.kmttg.main.auto;
import com.tivo.kmttg.main.autoConfig;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.encodeConfig;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.task.NowPlaying;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class gui {

   private configAuto config_auto = null;
   private String title = config.kmttg;
   private JFrame jFrame = null;
   private JPanel jContentPane = null;
   private JMenuBar jJMenuBar = null;
   private JMenu fileMenu = null;
   private JMenu autoMenu = null;
   private JMenu serviceMenu = null;
   private JMenuItem exitMenuItem = null;
   private JMenuItem autoConfigMenuItem = null;
   private JMenuItem runInGuiMenuItem = null;
   private JMenuItem addSelectedTitlesMenuItem = null;
   private JMenuItem addSelectedHistoryMenuItem = null;
   private JMenuItem logFileMenuItem = null;
   private JMenuItem configureMenuItem = null;
   private JMenuItem clearCacheMenuItem = null;
   private JMenuItem refreshEncodingsMenuItem = null;
   private JMenuItem serviceStatusMenuItem = null;
   private JMenuItem serviceInstallMenuItem = null;
   private JMenuItem serviceStartMenuItem = null;
   private JMenuItem serviceStopMenuItem = null;
   private JMenuItem serviceRemoveMenuItem = null;
   private JMenuItem backgroundJobStatusMenuItem = null;
   private JMenuItem backgroundJobEnableMenuItem = null;
   private JMenuItem backgroundJobDisableMenuItem = null;
   
   private JComboBox tivos = null;
   private JComboBox encoding = null;
   private JLabel tivo_label = null;
   private JLabel encoding_label = null;
   private JLabel encoding_description_label = null;
   private JLabel job_monitor_label = null;
   private JButton add = null;
   private JButton remove = null;
   private JButton start = null;
   private JButton cancel = null;
   private JCheckBox metadata = null;
   private JCheckBox decrypt = null;
   private JCheckBox qsfix = null;
   private JCheckBox comskip = null;
   private JCheckBox comcut = null;
   private JCheckBox captions = null;
   private JCheckBox encode = null;
   private JCheckBox custom = null;
   private JTextPane text = null;
   private nplTable nplTab = null;
   private jobTable jobTab = null;
   private textpane textp = null;
   private JProgressBar progressBar = null;
   private fileBrowser browser = null;
   public  JScrollPane nplScroll = null;
   public  JScrollPane jobScroll = null;

   public static Hashtable<String,Icon> Images;
   
   public JFrame getJFrame() {
      debug.print("");
      if (jFrame == null) {
         jFrame = new JFrame();
         jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
         jFrame.setJMenuBar(getJJMenuBar());
         jFrame.setMinimumSize(new Dimension(700,600));
         jFrame.setPreferredSize(jFrame.getMinimumSize());
         jFrame.pack();
         jFrame.setContentPane(getJContentPane());
         jFrame.setTitle(title);
         nplTab_packColumns(5);
         jobTab_packColumns(5);

         refreshOptions();
         config.GUI = true;
         
         // Create NowPlaying icons
         CreateImages();
         
         // Create File Browser instance
         browser = new fileBrowser();
      }
      return jFrame;
   }

   private Container getJContentPane() {
      debug.print("");
      if (jContentPane == null) {
         jContentPane = new JPanel(new GridBagLayout());
         jContentPane.setLayout(new GridBagLayout());

         // Pack table columns when content pane resized
         jContentPane.addHierarchyBoundsListener(new HierarchyBoundsListener() {
            public void ancestorMoved(HierarchyEvent arg0) {
               // Don't care about movement
            }
            public void ancestorResized(HierarchyEvent arg0) {
               nplTab.packColumns(nplTab.NowPlaying, 2);
               jobTab.packColumns(jobTab.JobMonitor, 2);
            }
         });
         
         GridBagConstraints c = new GridBagConstraints();

         c.fill = GridBagConstraints.HORIZONTAL;
         int gx=0, gy=0;

         // Tivos label
         tivo_label = new JLabel("TIVO", JLabel.CENTER);
         c.insets = new Insets(5, 0, 0, 0);
         c.ipady = 0;
         c.weighty = 0.0;  // default to no vertical stretch
         c.weightx = 0.0;  // default to no horizontal stretch
         c.gridx = gx++;
         c.gridy = gy;
         c.gridwidth = 1;
         c.gridheight = 1;
         c.anchor = GridBagConstraints.CENTER;
         c.fill = GridBagConstraints.HORIZONTAL;
         jContentPane.add(tivo_label, c);

         // Tivos combo box
         tivos = new JComboBox();
         SetTivos(config.TIVOS);
         tivos.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                  tivosCB(tivos);
               }
            }
         });
         c.insets = new Insets(0, 0, 0, 0);
         c.gridx = gx++;
         c.gridy = gy;
         jContentPane.add(tivos, c);

         // Add button
         add = new JButton("Add...");
         add.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
               addCB(add);
            }
         });
         c.ipadx = 0;
         c.gridx = gx++;
         c.gridy = gy;
         jContentPane.add(add, c);

         // Remove button
         remove = new JButton("Remove...");
         remove.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
               removeCB(remove);
            }
         });
         c.ipadx = 0;
         c.gridx = gx;
         c.gridy = gy;
         jContentPane.add(remove, c);
         
         // custom checkbox
         custom = new JCheckBox("custom", false);
         c.gridx = 7;
         c.gridy = gy++;
         jContentPane.add(custom, c);

         // Start jobs button
         start = new JButton("START JOBS");
         start.setBackground(Color.green);
         start.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
               startCB(start);
            }
         });
         gx = 0;
         c.ipadx = 0;
         c.gridx = gx++;
         c.gridy = gy;
         jContentPane.add(start, c);

         // Check boxes
         metadata = new JCheckBox("metadata", false);
         c.ipadx = 0;
         c.gridx = gx++;
         c.gridy = gy;
         //c.anchor = GridBagConstraints.WEST;
         jContentPane.add(metadata, c);
         
         decrypt = new JCheckBox("decrypt", true);
         c.gridx = gx++;
         c.gridy = gy;
         jContentPane.add(decrypt, c);
         
         qsfix = new JCheckBox("VRD QS fix", false);
         c.gridx = gx++;
         c.gridy = gy;
         jContentPane.add(qsfix, c);
         
         comskip = new JCheckBox("comskip", false);
         c.gridx = gx++;
         c.gridy = gy;
         jContentPane.add(comskip, c);
         
         comcut = new JCheckBox("comcut", false);
         c.gridx = gx++;
         c.gridy = gy;
         jContentPane.add(comcut, c);
         
         captions = new JCheckBox("captions", false);
         c.gridx = gx++;
         c.gridy = gy;
         jContentPane.add(captions, c);
         
         encode = new JCheckBox("encode", false);
         c.gridx = gx;
         c.gridy = gy++;
         jContentPane.add(encode, c);

         // Encoding label
         encoding_label = new JLabel("Encoding Profile:", JLabel.CENTER);
         gx = 0;
         c.gridx = gx++;
         c.gridy = gy;
         jContentPane.add(encoding_label, c);
 
         // Encoding names combo box
         encoding = new JComboBox();
         SetEncodings(encodeConfig.getValidEncodeNames());
         encoding.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                  encodingCB(encoding);
               }
            }
         });
         c.gridx = gx;
         c.gridy = gy;
         c.gridwidth = 2;
         jContentPane.add(encoding, c);
         gx += 2;

         // Encoding description label
         String description = "";
         if (encodeConfig.getValidEncodeNames().size() > 0) {
            description = "  " + encodeConfig.getDescription(encodeConfig.getEncodeName());
         }
         encoding_description_label = new JLabel(description);
         c.gridx = gx;
         c.gridy = gy++;
         c.gridwidth = 5;
         jContentPane.add(encoding_description_label, c);
         c.gridwidth = 1;
                  
         // nplTable
         nplTab = new nplTable();
         c.weightx = 1.0;    // stretch vertically
         c.weighty = 1.0;    // stretch horizontally
         c.ipadx = 0;
         c.ipady = 150;      //make this component tall
         c.gridheight = 1;
         c.gridwidth = 8;
         gx = 0;
         c.gridx = gx;
         c.gridy = gy++;
         c.fill = GridBagConstraints.BOTH;         
         // Add scrollbars to NowPlaying
         nplScroll = new JScrollPane(nplTab.NowPlaying);
         jContentPane.add(nplScroll, c);

         // Progress Bar
         progressBar = new JProgressBar();
         c.weightx = 1.0;     // stretch horizontally
         c.weighty = 0.0;     // don't stretch vertically
         c.ipady = 0;
         c.gridx = gx;
         c.gridy = gy++;
         c.fill = GridBagConstraints.HORIZONTAL;
         progressBar.setBackground(Color.gray);
         progressBar.setForeground(Color.green);
         jContentPane.add(progressBar, c);

         // Cancel jobs button
         cancel = new JButton("CANCEL JOBS");
         cancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
               cancelCB(cancel);
            }
         });
         c.weightx = 0.0;  // do not stretch horizontally
         c.ipadx = 0;
         c.ipady = 0;
         c.gridx = gx++;
         c.gridy = gy;
         c.gridwidth = 1;
         cancel.setBackground(Color.red);
         jContentPane.add(cancel, c);

         // Job Monitor label
         job_monitor_label = new JLabel("JOB MONITOR", JLabel.CENTER);
         c.gridx = gx+2;
         c.gridy = gy++;
         jContentPane.add(job_monitor_label, c);

         // Job Monitor table
         jobTab = new jobTable();
         c.weightx = 1.0;    // stretch horizontally
         c.ipady = 100;      //make this component tall
         c.gridheight = 1;
         c.gridwidth = 8;
         gx = 0;
         c.gridx = gx;
         c.gridy = gy++;
         c.fill = GridBagConstraints.BOTH;
         jobScroll = new JScrollPane(jobTab.JobMonitor);
         jContentPane.add(jobScroll, c);

         // Message area
         text = new JTextPane();
         textp = new textpane(text);
         text.setEditable(false);
         JScrollPane scrollPane3 = new JScrollPane(text);
         //c.ipady = 50;
         c.gridx = gx;
         c.gridy = gy++;
         c.fill = GridBagConstraints.BOTH;
         c.anchor = GridBagConstraints.SOUTH;
         jContentPane.add(scrollPane3, c);
         
      }
      
      return jContentPane;
   }
   
   private JMenuBar getJJMenuBar() {
      debug.print("");
      if (jJMenuBar == null) {
         jJMenuBar = new JMenuBar();
         jJMenuBar.add(getFileMenu());
         jJMenuBar.add(getAutoTransfersMenu());
      }
      return jJMenuBar;
   }

   private JMenu getFileMenu() {
      debug.print("");
      if (fileMenu == null) {
         fileMenu = new JMenu();
         fileMenu.setText("File");
         fileMenu.add(getConfigureMenuItem());
         fileMenu.add(getClearCacheMenuItem());
         fileMenu.add(getRefreshEncodingsMenuItem());
         fileMenu.add(getExitMenuItem());
      }
      return fileMenu;
   }

   private JMenu getAutoTransfersMenu() {
      debug.print("");
      if (autoMenu == null) {
         autoMenu = new JMenu();
         autoMenu.setText("Auto Transfers");
         autoMenu.add(getAutoConfigMenuItem());
         autoMenu.add(getRunInGuiMenuItem());
         if (config.OS.equals("windows"))
            autoMenu.add(getServiceMenu());
         else
            autoMenu.add(getBackgroundJobMenu());
         autoMenu.add(getAddSelectedTitlesMenuItem());
         autoMenu.add(getAddSelectedHistoryMenuItem());
         autoMenu.add(getLogFileMenuItem());
      }
      return autoMenu;
   }

   private JMenuItem getExitMenuItem() {
      debug.print("");
      if (exitMenuItem == null) {
         exitMenuItem = new JMenuItem();
         exitMenuItem.setText("Exit");
         exitMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X,
               Event.CTRL_MASK, true));
         exitMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               System.exit(0);
            }
         });
      }
      return exitMenuItem;
   }

   private JMenuItem getAutoConfigMenuItem() {
      debug.print("");
      if (autoConfigMenuItem == null) {
         autoConfigMenuItem = new JMenuItem();
         autoConfigMenuItem.setText("Configure...");
         autoConfigMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               if (config_auto == null)
                  config_auto = new configAuto();
               config_auto.display(jFrame);
            }
         });
      }
      return autoConfigMenuItem;
   }

   private JMenuItem getRunInGuiMenuItem() {
      debug.print("");
      if (runInGuiMenuItem == null) {
         runInGuiMenuItem = new JMenuItem();
         runInGuiMenuItem.setText("Run in GUI");
         runInGuiMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               autoRunInGUICB();
            }
         });
      }
      return runInGuiMenuItem;
   }

   private JMenuItem getAddSelectedTitlesMenuItem() {
      debug.print("");
      if (addSelectedTitlesMenuItem == null) {
         addSelectedTitlesMenuItem = new JMenuItem();
         addSelectedTitlesMenuItem.setText("Add selected titles");
         addSelectedTitlesMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               autoSelectedTitlesCB();
            }
         });
      }
      return addSelectedTitlesMenuItem;
   }

   private JMenuItem getAddSelectedHistoryMenuItem() {
      debug.print("");
      if (addSelectedHistoryMenuItem == null) {
         addSelectedHistoryMenuItem = new JMenuItem();
         addSelectedHistoryMenuItem.setText("Add selected to history file");
         addSelectedHistoryMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               autoSelectedHistoryCB();
            }
         });
      }
      return addSelectedHistoryMenuItem;
   }

   private JMenuItem getLogFileMenuItem() {
      debug.print("");
      if (logFileMenuItem == null) {
         logFileMenuItem = new JMenuItem();
         logFileMenuItem.setText("Examine log file...");
         logFileMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               new autoLogView(jFrame);
            }
         });
      }
      return logFileMenuItem;
   }

   private JMenuItem getConfigureMenuItem() {
      debug.print("");
      if (configureMenuItem == null) {
         configureMenuItem = new JMenuItem();
         configureMenuItem.setText("Configure...");
         configureMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               configMain.display(jFrame);
            }
         });
         configureMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O,
               Event.CTRL_MASK, true));
      }
      return configureMenuItem;
   }

   private JMenuItem getClearCacheMenuItem() {
      debug.print("");
      if (clearCacheMenuItem == null) {
         clearCacheMenuItem = new JMenuItem();
         clearCacheMenuItem.setText("Clear Cache");
         clearCacheMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               log.warn("Clearing Now Playing List cache");
               clearCacheCB();
            }
         });
         clearCacheMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A,
               Event.CTRL_MASK, true));
      }
      return clearCacheMenuItem;
   }

   private JMenuItem getRefreshEncodingsMenuItem() {
      debug.print("");
      if (refreshEncodingsMenuItem == null) {
         refreshEncodingsMenuItem = new JMenuItem();
         refreshEncodingsMenuItem.setText("Refresh Encoding Profiles");
         refreshEncodingsMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               refreshEncodingProfilesCB();
            }
         });
         refreshEncodingsMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E,
               Event.CTRL_MASK, true));
      }
      return refreshEncodingsMenuItem;
   }

   private JMenu getServiceMenu() {
      debug.print("");
      if (serviceMenu == null) {
         serviceMenu = new JMenu();
         serviceMenu.setText("Service");
         serviceMenu.add(getServiceStatusMenuItem());
         serviceMenu.add(getServiceInstallMenuItem());
         serviceMenu.add(getServiceStartMenuItem());
         serviceMenu.add(getServiceStopMenuItem());
         serviceMenu.add(getServiceRemoveMenuItem());
      }
      return serviceMenu;
   }

   private JMenuItem getServiceStatusMenuItem() {
      debug.print("");
      if (serviceStatusMenuItem == null) {
         serviceStatusMenuItem = new JMenuItem();
         serviceStatusMenuItem.setText("Status");
         serviceStatusMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               String query = auto.serviceStatus();
               if (query != null) {
                  log.warn(query);
               }
            }
         });
      }
      return serviceStatusMenuItem;
   }

   private JMenuItem getServiceInstallMenuItem() {
      debug.print("");
      if (serviceInstallMenuItem == null) {
         serviceInstallMenuItem = new JMenuItem();
         serviceInstallMenuItem.setText("Install");
         serviceInstallMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               String query = auto.serviceStatus();
               if (query != null) {                  
                  if (query.matches("^.+STATUS.+$")) {
                     log.warn("kmttg service already installed");
                     return;
                  }
                  auto.serviceCreate();
               }
            }
         });
      }
      return serviceInstallMenuItem;
   }

   private JMenuItem getServiceStartMenuItem() {
      debug.print("");
      if (serviceStartMenuItem == null) {
         serviceStartMenuItem = new JMenuItem();
         serviceStartMenuItem.setText("Start");
         serviceStartMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               String query = auto.serviceStatus();
               if (query != null) {                  
                  if (query.matches("^.+RUNNING$")) {
                     log.warn("kmttg service already running");
                     return;
                  }
                  auto.serviceStart();
               }
            }
         });
      }
      return serviceStartMenuItem;
   }

   private JMenuItem getServiceStopMenuItem() {
      debug.print("");
      if (serviceStopMenuItem == null) {
         serviceStopMenuItem = new JMenuItem();
         serviceStopMenuItem.setText("Stop");
         serviceStopMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               String query = auto.serviceStatus();
               if (query != null) {                  
                  if (query.matches("^.+STOPPED$")) {
                     log.warn("kmttg service already stopped");
                     return;
                  }
                  auto.serviceStop();
               }
            }
         });
      }
      return serviceStopMenuItem;
   }

   private JMenuItem getServiceRemoveMenuItem() {
      debug.print("");
      if (serviceRemoveMenuItem == null) {
         serviceRemoveMenuItem = new JMenuItem();
         serviceRemoveMenuItem.setText("Remove");
         serviceRemoveMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               String query = auto.serviceStatus();
               if (query != null) {
                  if (query.matches("^.+not been created.+$")) {
                     log.warn("kmttg service not installed");
                     return;
                  }
                  auto.serviceDelete();
               }
            }
         });
      }
      return serviceRemoveMenuItem;
   }

   private JMenu getBackgroundJobMenu() {
      debug.print("");
      if (serviceMenu == null) {
         serviceMenu = new JMenu();
         serviceMenu.setText("Background Job");
         serviceMenu.add(getBackgroundJobStatusMenuItem());
         serviceMenu.add(getBackgroundJobEnableMenuItem());
         serviceMenu.add(getBackgroundJobDisableMenuItem());
      }
      return serviceMenu;
   }

   private JMenuItem getBackgroundJobStatusMenuItem() {
      debug.print("");
      if (backgroundJobStatusMenuItem == null) {
         backgroundJobStatusMenuItem = new JMenuItem();
         backgroundJobStatusMenuItem.setText("Status");
         backgroundJobStatusMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               auto.unixAutoIsRunning(true);
            }
         });
      }
      return backgroundJobStatusMenuItem;
   }

   private JMenuItem getBackgroundJobEnableMenuItem() {
      debug.print("");
      if (backgroundJobEnableMenuItem == null) {
         backgroundJobEnableMenuItem = new JMenuItem();
         backgroundJobEnableMenuItem.setText("Enable");
         backgroundJobEnableMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               auto.unixAutoStart();
            }
         });
      }
      return backgroundJobEnableMenuItem;
   }

   private JMenuItem getBackgroundJobDisableMenuItem() {
      debug.print("");
      if (backgroundJobDisableMenuItem == null) {
         backgroundJobDisableMenuItem = new JMenuItem();
         backgroundJobDisableMenuItem.setText("Disable");
         backgroundJobDisableMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               auto.unixAutoKill();
            }
         });
      }
      return backgroundJobDisableMenuItem;
   }

   // This will decide which options are enabled based on current config settings
   // Options are disabled when associated config entry is not setup
   public void refreshOptions() {
      if (! file.isFile(config.curl)) {
         metadata.setSelected(false);
         metadata.setEnabled(false);
      } else {
         metadata.setEnabled(true);
      }
      if (! file.isFile(config.tivodecode)) {
         decrypt.setSelected(false);
         decrypt.setEnabled(false);
      } else {
         decrypt.setEnabled(true);
      }
      if (! file.isDir(config.VRD)) {
         qsfix.setSelected(false);
         qsfix.setEnabled(false);
      } else {
         qsfix.setEnabled(true);
      }

      if (! file.isFile(config.comskip)) {
         comskip.setSelected(false);
         comskip.setEnabled(false);
      } else {
         comskip.setEnabled(true);
      }

      if (! file.isFile(config.mencoder) && ! file.isDir(config.VRD)) {
         comcut.setSelected(false);
         comcut.setEnabled(false);
      } else {
         comcut.setEnabled(true);
      }

      if (! file.isFile(config.t2extract)) {
         captions.setSelected(false);
         captions.setEnabled(false);
      } else {
         captions.setEnabled(true);
      }

      if (! file.isFile(config.ffmpeg) &&
          ! file.isFile(config.mencoder) &&
          ! file.isFile(config.handbrake) ) {
         encode.setSelected(false);
         encode.setEnabled(false);
      } else {
         encode.setEnabled(true);
      }
      
      if ( ! com.tivo.kmttg.task.custom.customCommandExists() ) {
         custom.setSelected(false);
         custom.setEnabled(false);
      } else {
         custom.setEnabled(true);
      }
      
   }

   // Tivos cyclic change callback
   // For FILES entry clear NPL and enter FILES mode
   // For Tivo entry download Now Playing list (or build from cache)
   private void tivosCB(JComboBox combo) {
      debug.print("combo=" + combo);
      String tivoName = (String)combo.getSelectedItem();
      // Update config.tivoName
      config.tivoName = tivoName;
      
      if (tivoName.equals("FILES")) {
         nplTab.SetNowPlayingHeaders(nplTab.FILE_cols);
         add.setVisible(true);
         remove.setVisible(true);
         nplTab.clear(nplTab.NowPlaying);
      } else {
         nplTab.SetNowPlayingHeaders(nplTab.TIVO_cols);
         add.setVisible(false);
         remove.setVisible(false);
         nplTab.clear(nplTab.NowPlaying);

         Boolean use_cache = false;
         if ( config.cache.containsKey(tivoName) ) {
            if ( config.cache_times.containsKey(tivoName) ) {
               long now = new Date().getTime();
               long sdiff = (now - config.cache_times.get(tivoName))/1000;
               if ( sdiff < config.cache_time*60 ) {
                  use_cache = true;
                  log.print("NOTE: Using cached NPL for " + tivoName);
                  log.print(
                     "(" + tivoName +
                     " cache expires in "
                     + (config.cache_time*60 - sdiff) +
                     " seconds)"
                  );
               }
            }
         }
         if (use_cache) {
            nplTab_SetNowPlaying(config.cache.get(tivoName));
         } else {
            // Queue up a nowplaying list job for newly selected tivo
            NowPlaying.submitJob(tivoName);
         }
      }
   }
   
   // Callback for "Clear Cache" File menu entry
   // This clears NPL cache so as to force new NPL downloads
   private void clearCacheCB() {
      debug.print("");
      config.cache.clear();
   }
   
   // Callback for "Refresh Encoding Profiles" File menu entry
   // This will re-parse encoding files and reset Encoding Profile list in GUI
   private void refreshEncodingProfilesCB() {
      debug.print("");
      log.warn("Refreshing encoding profiles");
      encodeConfig.parseEncodingProfiles(config.encProfDir);
      SetEncodings(encodeConfig.getValidEncodeNames());
   }
   
   // Callback for "Run in GUI" Auto Transfers menu entry
   // This is equivalent to a batch mode run but is performed in GUI
   private void autoRunInGUICB() {
      debug.print("");
      config.GUI_AUTO = 0;
      if ( ! autoConfig.parseAuto(config.autoIni) ) {
         log.error("Auto Transfers config has errors or is not setup");
         return;
      }
      if ( auto.getTitleEntries().isEmpty() && auto.getKeywordsEntries().isEmpty() ) {
         log.error("No keywords defined in " + config.autoIni + "... aborting");
         return;
      }
      Stack<String> tivoNames = config.getTivoNames();
      if (tivoNames.size() > 0) {
         for (int i=0; i<tivoNames.size(); i++) {
            // Queue up a nowplaying list job for this tivo
            config.GUI_AUTO++;
            NowPlaying.submitJob(tivoNames.get(i));
         }
      }
   }
  
   // Callback for "Add selected titles" Auto Transfers menu entry
   // This will add the selected Tivo show titles to auto.ini file
   private void autoSelectedTitlesCB() {
      debug.print("");
      
      // Do nothing if in FILES mode
      if ( config.tivoName.equals("FILES") ) return;
      
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
   private void autoSelectedHistoryCB() {
      debug.print("");
      
      // Do nothing if in FILES mode
      if ( config.tivoName.equals("FILES") ) return;
      
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

   // Encoding cyclic change callback
   // Set the description according to selected item
   private void encodingCB(JComboBox combo) {
      debug.print("combo=" + combo);
      String encodeName = (String)combo.getSelectedItem();
      config.encodeName = encodeName;
      String description = encodeConfig.getDescription(encodeName);
      // Set encoding_description_label accordingly
      encoding_description_label.setText("  " + description);
   }

   // Start button callback
   // Process selected Now Playing entries
   private void startCB(JButton button) {
      debug.print("button=" + button);
      int[] rows = nplTab.GetSelectedRows();

      if (rows.length > 0) {
         int row;
         for (int i=0; i<rows.length; i++) {
            row = rows[i];
            Hashtable<String,Object> h = new Hashtable<String,Object>();
            if ( config.tivoName.equals("FILES") ) {
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
            if (config.tivoName.equals("FILES")) {
               h.put("metadataTivo", metadata.isSelected());
               h.put("metadata", false);
            } else {
               h.put("metadata", metadata.isSelected());
               h.put("metadataTivo", false);
            }
            h.put("decrypt",  decrypt.isSelected());
            h.put("qsfix",    qsfix.isSelected());
            h.put("comskip",  comskip.isSelected());
            h.put("comcut",   comcut.isSelected());
            h.put("captions", captions.isSelected());
            h.put("encode",   encode.isSelected());
            h.put("custom",   custom.isSelected());
            jobMonitor.LaunchJobs(h);
         }
      }
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
      if ( config.tivoName.equals("FILES") ) {
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
 
   // Cancel button callback
   // Kill and remove selected jobs from job monitor
   private void cancelCB(JButton button) {
      debug.print("button=" + button);
      int[] rows = jobTab.GetSelectedRows();

      if (rows.length > 0) {
         int row;
         for (int i=rows.length-1; i>=0; i--) {
            row = rows[i];
            jobData job = jobTab.GetSelectionData(row);
            if (job != null) jobMonitor.kill(job);
         }
      }
   }

   // Set tivos combobox choices
   public void SetTivos(Hashtable<String,String> values) {
      debug.print("values=" + values);
      if ( values.size() > 0 ) {
         String[] names = new String[values.size()];
         int i = 0;
         names[i++] = "FILES";
         String value;
         for (Enumeration<String> e=values.keys(); e.hasMoreElements();) {
            value = e.nextElement();
            if (! value.matches("^FILES$")) {
               names[i++] = value;
            }
         }
         combobox.SetValues(tivos, names);
         config.tivoName = names[0];
      } else {
         String[] names = {"FILES"};
         combobox.SetValues(tivos, names);
         config.tivoName = names[0];
      }      
   }
   
   // Add a tivo to tivos combobox
   public void AddTivo(String name, String ip) {
      tivos.addItem(name);
      configMain.addTivo(name, ip);
   }
   
   // Set encoding combobox choices
   public void SetEncodings(Stack<String> values) {
      debug.print("values=" + values);
      String[] names = new String[values.size()];
      for (int i=0; i<values.size(); ++i) {
         names[i] = values.get(i);
      }
      combobox.SetValues(encoding, names);
   }
   
   private void CreateImages() {
      debug.print("");
      Images = new Hashtable<String,Icon>();
      String[] names = {
         "expires-soon-recording", "save-until-i-delete-recording",
         "in-progress-recording", "in-progress-transfer",
         "expired-recording"
      };
      URL url;
      for (int i=0; i<names.length; i++) {
         url = getClass().getResource("/" + names[i] + ".png");
         if (url != null) {
            // From jar file
            Images.put(names[i], new ImageIcon(url));
         } else {
            // From eclipse
            Images.put(names[i], new ImageIcon("images/" + names[i] + ".png"));
         }
      }
   }
   
   // Abstraction methods
   public void setTitle(String s) {
      jFrame.setTitle(s);
   }
   public void text_print(String s) {
      textp.print(s);
   }
   public void text_warn(String s) {
      textp.warn(s);
   }
   public void text_error(String s) {
      textp.error(s);
   }
   public void text_print(Stack<String> s) {
      textp.print(s);
   }
   public void text_warn(Stack<String> s) {
      textp.warn(s);
   }
   public void text_error(Stack<String> s) {
      textp.error(s);
   }
   public void nplTab_packColumns(int pad) {
      nplTab.packColumns(nplTab.NowPlaying, pad);
   }
   public void jobTab_packColumns(int pad) {
      nplTab.packColumns(jobTab.JobMonitor, pad);
   }
   public void nplTab_SetNowPlaying(Stack<Hashtable<String,String>> h) {
      nplTab.SetNowPlaying(h);
   }
   public void nplTab_clear() {
      nplTab.clear(nplTab.NowPlaying);
   }
   public jobData jobTab_GetRowData(int row) {
      return jobTab.GetRowData(row);
   }
   public void jobTab_UpdateJobMonitorRowStatus(jobData job, String status) {
      jobTab.UpdateJobMonitorRowStatus(job, status);
   }
   public void jobTab_AddJobMonitorRow(jobData job, String description) {
      jobTab.AddJobMonitorRow(job, description);
   }
   public void jobTab_RemoveJobMonitorRow(jobData job) {
      jobTab.RemoveJobMonitorRow(job);
   }
   public void progressBar_setValue(int value) {
      progressBar.setValue(value);
   }
   public void refresh() {
      jContentPane.paintImmediately(jContentPane.getBounds());
   }
   
   // Returns state of checkbox options (as int for writing to auto.ini purposes)
   public int metadata_setting() {
      int selected = 0;
      if (metadata.isSelected()) selected = 1;
      return selected;
   }
   public int decrypt_setting() {
      int selected = 0;
      if (decrypt.isSelected()) selected = 1;
      return selected;
   }
   public int qsfix_setting() {
      int selected = 0;
      if (qsfix.isSelected()) selected = 1;
      return selected;
   }
   public int comskip_setting() {
      int selected = 0;
      if (comskip.isSelected()) selected = 1;
      return selected;
   }
   public int comcut_setting() {
      int selected = 0;
      if (comcut.isSelected()) selected = 1;
      return selected;
   }
   public int captions_setting() {
      int selected = 0;
      if (captions.isSelected()) selected = 1;
      return selected;
   }
   public int encode_setting() {
      int selected = 0;
      if (encode.isSelected()) selected = 1;
      return selected;
   }
   public int custom_setting() {
      int selected = 0;
      if (custom.isSelected()) selected = 1;
      return selected;
   }

}
