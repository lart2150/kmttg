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

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.TableColumnModel;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.dialog.ShowDetails;
import com.tivo.kmttg.gui.dialog.SkipDialog;
import com.tivo.kmttg.gui.dialog.autoLogView;
import com.tivo.kmttg.gui.dialog.configAuto;
import com.tivo.kmttg.gui.dialog.configMain;
import com.tivo.kmttg.gui.remote.remotegui;
import com.tivo.kmttg.gui.swing.SwingUtil;
import com.tivo.kmttg.gui.swing.Theme;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.jobTable;
import com.tivo.kmttg.gui.table.nplTable;
import com.tivo.kmttg.install.mainInstall;
import com.tivo.kmttg.install.update;
import com.tivo.kmttg.main.auto;
import com.tivo.kmttg.main.autoConfig;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.encodeConfig;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.main.kmttg;
import com.tivo.kmttg.rpc.SkipManager;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class gui {

   private JFrame jFrame = null;
   private configAuto config_auto = null;
   private String title = config.kmttg;
   private JSplitPane jContentPane = null;
   private JSplitPane splitBottom = null;
   private JTabbedPane tabbed_panel = null;
   private JMenuBar menuBar = null;
   private JMenu fileMenu = null;
   private JMenu jobMenu = null;
   private JMenu autoMenu = null;
   private JMenu serviceMenu = null;
   private JMenu helpMenu = null;
   private JMenuItem helpAboutMenuItem = null;
   private JMenuItem helpUpdateMenuItem = null;
   private JMenuItem helpToolsUpdateMenuItem = null;
   private JMenuItem exitMenuItem = null;
   private JMenuItem autoConfigMenuItem = null;
   private JMenuItem runInGuiMenuItem = null;
   private JCheckBoxMenuItem loopInGuiMenuItem = null;
   private JCheckBoxMenuItem resumeDownloadsMenuItem = null;
   private JCheckBoxMenuItem toggleLaunchingJobsMenuItem = null;
   public  JMenuItem addSelectedTitlesMenuItem = null;
   public  JMenuItem addSelectedHistoryMenuItem = null;
   private JMenuItem logFileMenuItem = null;
   private JMenuItem configureMenuItem = null;
   private JMenuItem refreshEncodingsMenuItem = null;
   private JMenuItem serviceStatusMenuItem = null;
   private JMenuItem serviceInstallMenuItem = null;
   private JMenuItem serviceStartMenuItem = null;
   private JMenuItem serviceStopMenuItem = null;
   private JMenuItem serviceRemoveMenuItem = null;
   private JMenuItem backgroundJobStatusMenuItem = null;
   private JMenuItem backgroundJobEnableMenuItem = null;
   private JMenuItem backgroundJobDisableMenuItem = null;
   private JMenuItem saveMessagesMenuItem = null;
   private JMenuItem clearMessagesMenuItem = null;
   private JMenuItem saveJobsMenuItem = null;
   private JMenuItem loadJobsMenuItem = null;
   private JMenuItem metadataMenuItem = null;
   public JMenuItem searchMenuItem = null;
   private JMenuItem autoSkipMenuItem = null;
   private JMenu autoSkipServiceMenu = null;
   public JMenuItem thumbsMenuItem = null;

   private JComboBox<String> encoding = null;
   private JLabel encoding_label = null;
   private JLabel encoding_description_label = null;
   public JButton start = null;
   public JButton cancel = null;
   public JCheckBox TSdownload = null;
   public JCheckBox metadata = null;
   public JCheckBox decrypt = null;
   public JCheckBox qsfix = null;
   public JCheckBox twpdelete = null;
   public JCheckBox rpcdelete = null;
   public JCheckBox comskip = null;
   public JCheckBox comcut = null;
   public JCheckBox captions = null;
   public JCheckBox encode = null;
   public JCheckBox custom = null;
   private textpane textp = null;
   private jobTable jobTab = null;
   private JProgressBar progressBar = null;
   public  JScrollPane jobPane = null;

   private Hashtable<String,tivoTab> tivoTabs = new Hashtable<String,tivoTab>();
   public static Hashtable<String,java.awt.Image> Images;

   public remotegui remote_gui = null;
   public slingboxgui  slingbox_gui = null;

   public ShowDetails show_details = null;


   public JFrame getFrame() {
      debug.print("");
      return jFrame;
   }

   public tivoTab getTab(String tabName) {
      debug.print("tabName=" + tabName);
      return tivoTabs.get(tabName);
   }

   public void Launch() {
      debug.print("");
      SwingUtil.runLater(new Runnable() {
         @Override public void run() {
            start();
         }
      });
   }

   public void start() {
      debug.print("");

      // Apply look and feel before building components
      Theme.apply(config.lookAndFeel);
      Theme.setFontSize(config.FontSize);

      jFrame = new JFrame(title);

      // Load icons for system usage
      LoadIcons(jFrame);

      // Create NowPlaying icons (needed by table entries)
      CreateImages();

      // Build main canvas components
      getContentPane();
      config.gui = this;

      jFrame.setJMenuBar(getJMenuBar());
      jFrame.getContentPane().add(jContentPane, BorderLayout.CENTER);

      // Add additional rpc remote tab
      remote_gui = new remotegui(jFrame);
      addTabPane("Remote", tabbed_panel, remote_gui.getPanel());

      // Init TableMap utility class
      TableMap.init();

      jobTab_packColumns(5);
      jFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
      jFrame.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent event) {
            saveSettings();
            System.exit(0);
         }
      });
      jFrame.setSize(1000, 800);
      jFrame.setLocationRelativeTo(null);

      // Restore last GUI run settings from file
      SwingUtil.runLater(new Runnable() {
         @Override public void run() {
            readSettings();
            // Enable/disable options according to configuration
            refreshOptions(true);
         }
      });

      // Create and enable/disable component tooltips
      MyTooltip.enableToolTips(config.toolTips);
      setToolTips();

      // Set master flag indicating that kmttg is running in GUI mode
      config.GUIMODE = true;

      jFrame.setVisible(true);

      // Set initial divider positions once frame is visible
      SwingUtil.runLater(new Runnable() {
         @Override public void run() {
            jContentPane.setDividerLocation(0.57);
            splitBottom.setDividerLocation(0.55);
         }
      });

      // Initialize AutoSkip entries with possible auto starts
      // to after configuration reads and gui setups.
      for (int i = 0; i < config.getTivoNames().size(); i++) {
          String tivoName = config.getTivoNames().get(i);
          if (config.rpcEnabled(tivoName)) {
              // AutoSkip service capable
              config.gui.addAutoSkipServiceItem(tivoName);
          }
      }

      // Init show_details dialog
      show_details = new ShowDetails(jFrame, null);

      // Start NPL jobs
      if (config.npl_when_started == 1) {
         SwingUtil.runLater(new Runnable() {
            @Override public void run() {
               initialNPL(config.TIVOS);
            }
         });
      }

      config.gui = this;

      // Download tools if necessary
      mainInstall.install();

      // Invoke a 1000ms period timer for job monitor
      kmttg.timer = new Timer();
      kmttg.timer.schedule(
         new TimerTask() {
             @Override
             public void run() {
                SwingUtil.runLater(new Runnable() {
                   @Override public void run() {
                      jobMonitor.monitor(config.gui);
                   }
                });
             }
         }
         ,0,
         1000
      );

      kmttg._startingUp = false;
   }

   public void setFontSize(int fontSize) {
      debug.print("fontSize=" + fontSize);
      Theme.setFontSize(fontSize);
   }

   public void setLookAndFeel(String name) {
      debug.print("name=" + name);
      Theme.apply(name);
   }

   public List<String> getAvailableLooks() {
      debug.print("");
      return Theme.getAvailableLooks();
   }

   public void grabFocus() {
      debug.print("");
      if (jFrame != null)
         if(! jFrame.isFocused()) { jFrame.toFront(); jFrame.requestFocus(); }
   }

   private JSplitPane getContentPane() {
      debug.print("");
      if (jContentPane == null) {

         // CANCEL JOBS button (light red - former kmttg.css button_job_cancel)
         cancel = new JButton("CANCEL JOBS");
         cancel.setBackground(new java.awt.Color(0xFA, 0xBE, 0xBE));
         cancel.setForeground(java.awt.Color.BLACK);
         cancel.setToolTipText(getToolTip("cancel"));
         cancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               cancelCB();
            }
         });

         // START JOBS button (light green - former kmttg.css button_job_start)
         start = new JButton("START JOBS");
         start.setBackground(new java.awt.Color(0x90, 0xEE, 0x90));
         start.setForeground(java.awt.Color.BLACK);
         start.setToolTipText(getToolTip("start"));
         start.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               String tivoName = getCurrentTabName();
               if (tivoName.equals("Remote"))
                  log.error("START JOBS invalid with Remote tab selected.");
               else
                  tivoTabs.get(tivoName).startCB();
            }
         });

         // Download option
         TSdownload = new JCheckBox("TS downloads"); TSdownload.setSelected(true);
         TSdownload.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
               if (TSdownload.isSelected())
                  config.TSDownload = 1;
               else
                  config.TSDownload = 0;
            }
         });

         // Tasks
         metadata = new JCheckBox("metadata"); metadata.setSelected(false);
         decrypt = new JCheckBox("decrypt"); decrypt.setSelected(true);
         qsfix = new JCheckBox("QS Fix"); qsfix.setSelected(false);
         qsfix.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               refreshOptions(false);
            }
         });
         twpdelete = new JCheckBox("TWP Delete"); twpdelete.setSelected(false);
         rpcdelete = new JCheckBox("rpc Delete");  rpcdelete.setSelected(false);
         comskip = new JCheckBox("Ad Detect"); comskip.setSelected(false);
         comcut = new JCheckBox("Ad Cut"); comcut.setSelected(false);
         captions = new JCheckBox("captions"); captions.setSelected(false);
         encode = new JCheckBox("encode"); encode.setSelected(false);
         custom = new JCheckBox("custom"); custom.setSelected(false);

         // Tasks row
         JPanel tasks_panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
         tasks_panel.add(start);
         tasks_panel.add(Box.createHorizontalStrut(5));
         tasks_panel.add(TSdownload);
         tasks_panel.add(metadata);
         tasks_panel.add(decrypt);
         tasks_panel.add(qsfix);
         if (config.twpDeleteEnabled()) {
            tasks_panel.add(twpdelete);
         }
         if (config.rpcDeleteEnabled()) {
            tasks_panel.add(rpcdelete);
         }
         tasks_panel.add(comskip);
         tasks_panel.add(comcut);
         tasks_panel.add(captions);
         tasks_panel.add(encode);
         tasks_panel.add(custom);

         // Encoding row
         // Encoding label
         encoding_label = new JLabel("Encoding Profile:");

         // Encoding names combo box
         encoding = new JComboBox<String>();
         SetEncodings(encodeConfig.getValidEncodeNames());
         encoding.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
               if (encoding.getSelectedItem() != null) {
                  encodingCB(encoding);
               }
            }
         });

         // Encoding description label
         String description = "";
         if (encodeConfig.getValidEncodeNames().size() > 0) {
            description = "  " + encodeConfig.getDescription(encodeConfig.getEncodeName());
         }
         encoding_description_label = new JLabel(description);

         JPanel encoding_panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
         encoding_panel.add(encoding_label);
         encoding_panel.add(encoding);
         encoding_panel.add(encoding_description_label);

         // Job Monitor table
         jobTab = new jobTable();
         jobPane = new JScrollPane(jobTab.JobMonitor);

         // Progress Bar (light green fill - former kmttg.css progressbar_job)
         progressBar = new JProgressBar(0, 100);
         progressBar.setForeground(new java.awt.Color(0x90, 0xEE, 0x90));
         progressBar.setValue(0);

         // Message area
         textp = new textpane();

         // Tabbed panel
         tabbed_panel = new JTabbedPane();
         tabbed_panel.addChangeListener(new ChangeListener() {
            @Override public void stateChanged(ChangeEvent e) {
               if (getCurrentTabName() != null && getCurrentTabName().equals("Remote")) {
                  // Set focus on remote pane
                  if (remote_gui != null)
                     remote_gui.tabbed_panel.requestFocus();
               }
            }
         });

         // Add permanent tabs
         tivoTabs.put("FILES", new tivoTab("FILES"));
         addTabPane("FILES", tabbed_panel, tivoTabs.get("FILES").getPanel());

         // Add Tivo tabs
         SetTivos(config.TIVOS);

         // Cancel pane: cancel button at left, progress bar stretches
         JPanel cancel_pane = new JPanel(new BorderLayout(5, 0));
         cancel_pane.add(cancel, BorderLayout.WEST);
         cancel_pane.add(progressBar, BorderLayout.CENTER);

         // Create a split pane between job & messages pane
         splitBottom = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
         splitBottom.setTopComponent(jobPane);
         splitBottom.setBottomComponent(textp.getPane());
         splitBottom.setResizeWeight(0.55);

         // bottomPane will consist of cancel_pane & splitBottom
         JPanel bottomPane = new JPanel(new BorderLayout());
         bottomPane.add(cancel_pane, BorderLayout.NORTH);
         bottomPane.add(splitBottom, BorderLayout.CENTER);

         // topPane will consist of tasks & tabbed_panel
         JPanel optionRows = new JPanel();
         optionRows.setLayout(new javax.swing.BoxLayout(optionRows, javax.swing.BoxLayout.Y_AXIS));
         optionRows.add(tasks_panel);
         optionRows.add(encoding_panel);
         JPanel topPane = new JPanel(new BorderLayout());
         topPane.add(optionRows, BorderLayout.NORTH);
         topPane.add(tabbed_panel, BorderLayout.CENTER);

         // Put all panels together
         jContentPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
         jContentPane.setTopComponent(topPane);
         jContentPane.setBottomComponent(bottomPane);
         jContentPane.setResizeWeight(0.57);
      }

      return jContentPane;
   }

   private JMenuBar getJMenuBar() {
      debug.print("");
      if (menuBar == null) {
         menuBar = new JMenuBar();
         menuBar.add(getFileMenu());
         menuBar.add(getAutoTransfersMenu());
         menuBar.add(getHelpMenu());
      }
      return menuBar;
   }

   private JMenu getFileMenu() {
      debug.print("");
      if (fileMenu == null) {
         fileMenu = new JMenu("File");
         fileMenu.add(getConfigureMenuItem());
         fileMenu.add(getRefreshEncodingsMenuItem());
         fileMenu.add(getSaveMessagesMenuItem());
         fileMenu.add(getClearMessagesMenuItem());
         fileMenu.add(getResumeDownloadsMenuItem());
         fileMenu.add(getJobMenu());
         fileMenu.add(getMetadataMenuItem());
         fileMenu.add(getSearchMenuItem());
         if (config.rpcEnabled() && SkipManager.skipEnabled()) {
            fileMenu.add(getAutoSkipMenuItem());
            fileMenu.add(getAutoSkipServiceMenu());
         }
         // Create thumbs menu item but don't add to File menu
         getThumbsMenuItem();
         fileMenu.add(getExitMenuItem());
      }
      return fileMenu;
   }

   private JMenu getJobMenu() {
      debug.print("");
      if (jobMenu == null) {
         jobMenu = new JMenu("Jobs");
         jobMenu.add(getToggleLaunchingJobsMenuItem());
         jobMenu.add(getSaveJobsMenuItem());
         jobMenu.add(getLoadJobsMenuItem());
      }
      return jobMenu;
   }

   private JMenu getAutoTransfersMenu() {
      debug.print("");
      if (autoMenu == null) {
         autoMenu = new JMenu("Auto Transfers");
         autoMenu.add(getAutoConfigMenuItem());
         if (config.OS.equals("windows"))
            autoMenu.add(getServiceMenu());
         else
            autoMenu.add(getBackgroundJobMenu());
         autoMenu.add(getAddSelectedTitlesMenuItem());
         autoMenu.add(getAddSelectedHistoryMenuItem());
         autoMenu.add(getLogFileMenuItem());
         autoMenu.add(getRunInGuiMenuItem());
         autoMenu.add(getLoopInGuiMenuItem());
      }
      return autoMenu;
   }

   private JMenu getHelpMenu() {
      debug.print("");
      if (helpMenu == null) {
         helpMenu = new JMenu("Help");
         helpMenu.add(getHelpAboutMenuItem());
         helpMenu.add(getHelpUpdateMenuItem());
         if (config.OS.equals("windows") || config.OS.equals("mac"))
            helpMenu.add(getHelpToolsUpdateMenuItem());
      }
      return helpMenu;
   }

   private JMenuItem getHelpAboutMenuItem() {
      debug.print("");
      if (helpAboutMenuItem == null) {
         helpAboutMenuItem = new JMenuItem();
         helpAboutMenuItem.setText("About...");
         helpAboutMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               help.showHelp();
            }
         });
      }
      return helpAboutMenuItem;
   }

   private JMenuItem getHelpUpdateMenuItem() {
      debug.print("");
      if (helpUpdateMenuItem == null) {
         helpUpdateMenuItem = new JMenuItem();
         helpUpdateMenuItem.setText("Update kmttg...");
         helpUpdateMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               update.update_kmttg_background();
            }
         });
      }
      return helpUpdateMenuItem;
   }

   private JMenuItem getHelpToolsUpdateMenuItem() {
      debug.print("");
      if (helpToolsUpdateMenuItem == null) {
         helpToolsUpdateMenuItem = new JMenuItem();
         helpToolsUpdateMenuItem.setText("Update tools...");
         helpToolsUpdateMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               update.update_tools_background();
            }
         });
      }
      return helpToolsUpdateMenuItem;
   }

   private JMenuItem getExitMenuItem() {
      debug.print("");
      if (exitMenuItem == null) {
         exitMenuItem = new JMenuItem();
         exitMenuItem.setText("Exit");
         exitMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               saveSettings();
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

   private JMenuItem getSaveMessagesMenuItem() {
      debug.print("");
      if (saveMessagesMenuItem == null) {
         saveMessagesMenuItem = new JMenuItem();
         saveMessagesMenuItem.setText("Save messages to file");
         saveMessagesMenuItem.setAccelerator(KeyStroke.getKeyStroke("control M"));
         saveMessagesMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               String file = config.programDir + File.separator + "kmttg.log";
               String eol = "\n";
               if (config.OS.equals("windows"))
                  eol = "\r\n";
               try {
                  StringBuilder sb = new StringBuilder();
                  String[] lines = textp.getText().split("\n");
                  for (String line : lines)
                     sb.append(line + eol);
                  BufferedWriter ofp = new BufferedWriter(new FileWriter(file));
                  ofp.write(sb.toString());
                  ofp.close();
                  log.warn("Saved output messages to file: " + file);
               } catch (IOException ex) {
                  log.error("Problem writing to file: " + file);
               }
            }
         });
      }
      return saveMessagesMenuItem;
   }

   private JMenuItem getClearMessagesMenuItem() {
      debug.print("");
      if (clearMessagesMenuItem == null) {
         clearMessagesMenuItem = new JMenuItem();
         clearMessagesMenuItem.setText("Clear all messages");
         clearMessagesMenuItem.setAccelerator(KeyStroke.getKeyStroke("control L"));
         clearMessagesMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               textp.clear();
            }
         });
      }
      return clearMessagesMenuItem;
   }

   private JMenuItem getToggleLaunchingJobsMenuItem() {
      debug.print("");
      if (toggleLaunchingJobsMenuItem == null) {
         toggleLaunchingJobsMenuItem = new JCheckBoxMenuItem();
         toggleLaunchingJobsMenuItem.setText("Do not launch queued jobs");
         toggleLaunchingJobsMenuItem.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
               if (toggleLaunchingJobsMenuItem.isSelected()) {
                  jobMonitor.NoNewJobs = true;
                  log.warn("Launching queued jobs disabled. Queued jobs will not be launched.");
               } else {
                  jobMonitor.NoNewJobs = false;
                  log.warn("Launching queued jobs enabled. Resuming normal job processing.");
               }
            }
         });
      }
      return toggleLaunchingJobsMenuItem;
   }

   private JMenuItem getSaveJobsMenuItem() {
      debug.print("");
      if (saveJobsMenuItem == null) {
         saveJobsMenuItem = new JMenuItem();
         saveJobsMenuItem.setText("Save queued jobs");
         saveJobsMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               jobMonitor.saveQueuedJobs();
            }
         });
      }
      return saveJobsMenuItem;
   }

   private JMenuItem getLoadJobsMenuItem() {
      debug.print("");
      if (loadJobsMenuItem == null) {
         loadJobsMenuItem = new JMenuItem();
         loadJobsMenuItem.setText("Load queued jobs");
         loadJobsMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               jobMonitor.loadQueuedJobs();
            }
         });
      }
      return loadJobsMenuItem;
   }

   private JMenuItem getRunInGuiMenuItem() {
      debug.print("");
      if (runInGuiMenuItem == null) {
         runInGuiMenuItem = new JMenuItem();
         runInGuiMenuItem.setText("Run Once in GUI");
         runInGuiMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               autoRunInGUICB();
            }
         });
      }
      return runInGuiMenuItem;
   }

   private JMenuItem getLoopInGuiMenuItem() {
      debug.print("");
      if (loopInGuiMenuItem == null) {
         loopInGuiMenuItem = new JCheckBoxMenuItem();
         loopInGuiMenuItem.setText("Loop in GUI");
         loopInGuiMenuItem.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
               autoLoopInGUICB(loopInGuiMenuItem.isSelected());
            }
         });
      }
      return loopInGuiMenuItem;
   }

   private JMenuItem getResumeDownloadsMenuItem() {
      debug.print("");
      if (resumeDownloadsMenuItem == null) {
         resumeDownloadsMenuItem = new JCheckBoxMenuItem();
         resumeDownloadsMenuItem.setText("Resume Downloads");
         resumeDownloadsMenuItem.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
               config.resumeDownloads = resumeDownloadsMenuItem.isSelected();
            }
         });
      }
      return resumeDownloadsMenuItem;
   }

   private JMenuItem getAddSelectedTitlesMenuItem() {
      debug.print("");
      if (addSelectedTitlesMenuItem == null) {
         addSelectedTitlesMenuItem = new JMenuItem();
         addSelectedTitlesMenuItem.setText("Add selected titles");
         addSelectedTitlesMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               TableMap tmap = TableMap.getCurrent();
               if (tmap == null || (tmap != null && ! tmap.isRemote())) {
                  // Non remote table
                  tivoTabs.get(getSelectedTivoName()).autoSelectedTitlesCB();
                  return;
               }
               // Processing for remote tables
               if (tmap != null) {
                  int[] selected = tmap.getSelected();
                  if (selected != null && selected.length > 0) {
                     for (int row : selected) {
                        JSONObject json = tmap.getJson(row);
                        if (json != null && json.has("title")) {
                           try {
                              auto.autoAddTitleEntryToFile(json.getString("title"));
                           } catch (JSONException e1) {
                              log.error("Add selected titles json exception - " + e1.getMessage());
                           }
                        }
                     }
                  } else {
                     log.error("No show selected in table");
                     return;
                  }
               }
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
               String tivoName = getSelectedTivoName();
               if (tivoName != null) {
                  tivoTabs.get(tivoName).autoSelectedHistoryCB();
               } else {
                  log.error("This command must be run from a TiVo tab with selected tivo shows.");
               }
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
         configureMenuItem.setAccelerator(KeyStroke.getKeyStroke("control O"));
      }
      return configureMenuItem;
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
         refreshEncodingsMenuItem.setAccelerator(KeyStroke.getKeyStroke("control E"));
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
               }
               auto.serviceStart();
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
               }
               auto.serviceStop();
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

   private JMenuItem getMetadataMenuItem() {
      debug.print("");
      if (metadataMenuItem == null) {
         metadataMenuItem = new JMenuItem();
         metadataMenuItem.setText("Download Metadata");
         metadataMenuItem.setAccelerator(KeyStroke.getKeyStroke("control R"));
         metadataMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               String tivoName = getSelectedTivoName();
               if (tivoName == null) {
                  log.error("Please select 1 or more shows in a TiVo tab for metadata creation command");
                  return;
               }
               nplTable nplTab = tivoTabs.get(tivoName).getTable();
               int[] rows = nplTab.GetSelectedRows();
               if (rows.length > 0) {
                  int row;
                  for (int i=0; i<rows.length; i++) {
                     row = rows[i];
                     Stack<Hashtable<String,Object>> entries = new Stack<Hashtable<String,Object>>();
                     Stack<Hashtable<String,String>> rowData = nplTab.getRowData(row);
                     for (int j=0; j<rowData.size(); ++j) {
                        Hashtable<String,Object> h = new Hashtable<String,Object>();
                        h.put("tivoName", tivoName);
                        h.put("mode", "Download");
                        h.put("nodownload", true);
                        h.put("entry", rowData.get(j));
                        entries.add(h);
                     }

                     // Launch metadata jobs
                     for (int j=0; j<entries.size(); ++j) {
                        Hashtable<String,Object> h = entries.get(j);
                        h.put("metadata",     true);
                        h.put("metadataTivo", false);
                        h.put("decrypt",      false);
                        h.put("qsfix",        false);
                        h.put("twpdelete",    false);
                        h.put("rpcdelete",    false);
                        h.put("comskip",      false);
                        h.put("comcut",       false);
                        h.put("captions",     false);
                        h.put("encode",       false);
                        h.put("custom",       false);
                        jobMonitor.LaunchJobs(h);
                     }
                  }
               }
            }
         });
      }
      return metadataMenuItem;
   }

   private JMenuItem getSearchMenuItem() {
      debug.print("");
      if (searchMenuItem == null) {
         searchMenuItem = new JMenuItem();
         searchMenuItem.setText("Search Table...");
         searchMenuItem.setAccelerator(KeyStroke.getKeyStroke("control S"));
         searchMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               TableUtil.SearchGUI();
            }
         });
      }
      return searchMenuItem;
   }

   private JMenuItem getAutoSkipMenuItem() {
      debug.print("");
      if (autoSkipMenuItem == null) {
         autoSkipMenuItem = new JMenuItem();
         autoSkipMenuItem.setText("AutoSkip Table...");
         autoSkipMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               new SkipDialog(config.gui.getFrame());
            }
         });
      }
      return autoSkipMenuItem;
   }

   private JMenu getAutoSkipServiceMenu() {
      debug.print("");
      if (autoSkipServiceMenu == null) {
         autoSkipServiceMenu = new JMenu();
         autoSkipServiceMenu.setText("AutoSkip Service");
      }
      return autoSkipServiceMenu;
   }

   public void addAutoSkipServiceItem(String tivoName) {
      if ( ! SkipManager.skipEnabled() ) return;
      if (autoSkipServiceMenu == null)
         getAutoSkipServiceMenu();
      for (int i=0; i<autoSkipServiceMenu.getItemCount(); ++i) {
         JMenuItem existing = autoSkipServiceMenu.getItem(i);
         if (existing != null && existing.getText().equals(tivoName))
            return;
      }
      final JCheckBoxMenuItem item = new JCheckBoxMenuItem();
      item.setText(tivoName);
      item.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
            if (! item.isSelected()) {
               SkipManager.stopService(tivoName);
               config.autoskip_ServiceItems.put(tivoName, false);
               config.save();
               return;
            }

            JSONArray skipData = SkipManager.getEntries();
            if (skipData == null) {
               log.warn("No skip table data available - ignoring skip service request");
               disableAutoSkipServiceItem(tivoName);
               config.autoskip_ServiceItems.put(tivoName, false);
               config.save();
               return;
            }
            SkipManager.startService(tivoName);
            Boolean b = config.autoskip_ServiceItems.get(tivoName);
            if( b == null || b == false) {
                config.autoskip_ServiceItems.put(tivoName, true);
                config.save();
            }
         }
      });
      autoSkipServiceMenu.add(item);
      Boolean b = config.autoskip_ServiceItems.get(tivoName);
      if (b != null && b) {
          item.setSelected(true);
      }
   }

   public void removeAutoSkipServiceItem(String tivoName) {
      for (int i=autoSkipServiceMenu.getItemCount()-1; i>=0; --i) {
         JMenuItem item = autoSkipServiceMenu.getItem(i);
         if (item != null && item.getText().equals(tivoName)) {
            autoSkipServiceMenu.remove(i);
         }
      }
   }

   public void disableAutoSkipServiceItem(String tivoName) {
      for (int i=0; i<autoSkipServiceMenu.getItemCount(); ++i) {
         JMenuItem item = autoSkipServiceMenu.getItem(i);
         if (item instanceof JCheckBoxMenuItem && item.getText().equals(tivoName))
            ((JCheckBoxMenuItem)item).setSelected(false);
      }
   }

   private JMenuItem getThumbsMenuItem() {
      debug.print("");
      if (thumbsMenuItem == null) {
         thumbsMenuItem = new JMenuItem();
         thumbsMenuItem.setText("Set Thumbs rating...");
         thumbsMenuItem.setAccelerator(KeyStroke.getKeyStroke("control T"));
         thumbsMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               TableUtil.ThumbsGUI();
            }
         });
      }
      return thumbsMenuItem;
   }

   // This will decide which options are enabled based on current config settings
   // Options are disabled when associated config entry is not setup
   public void refreshOptions(Boolean refreshProfiles) {
      debug.print("refreshProfiles=" + refreshProfiles);

      if (config.TSDownload == 1)
         TSdownload.setSelected(true);
      else
         TSdownload.setSelected(false);

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

      // Refresh encoding profiles in case toggled between VRD & regular
      if (config.GUIMODE && refreshProfiles) refreshEncodingProfilesCB();

      // Add remote tab if appropriate
      if (config.GUIMODE && remote_gui == null) {
         remote_gui = new remotegui(jFrame);
         addTabPane("Remote", tabbed_panel, remote_gui.getPanel());
      }

      // Add slingbox tab if appropriate
      if (config.slingBox == 1) {
         if (slingbox_gui == null)
            slingbox_gui = new slingboxgui(jFrame);
         addTabPane("Slingbox", tabbed_panel, slingbox_gui.getPanel());
      }
      if (config.slingBox == 0 && slingbox_gui != null) {
         int index = tabbed_panel.indexOfTab("Slingbox");
         if (index >= 0)
            tabbed_panel.removeTabAt(index);
      }
   }

   // Callback for "Refresh Encoding Profiles" File menu entry
   // This will re-parse encoding files and reset Encoding Profile list in GUI
   private void refreshEncodingProfilesCB() {
      debug.print("");
      log.warn("Refreshing encoding profiles");
      encodeConfig.parseEncodingProfiles();
   }

   // Callback for "Run Once in GUI" Auto Transfers menu entry
   // This is equivalent to a batch mode run but is performed in GUI
   public void autoRunInGUICB() {
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
      Stack<String> tivoNames = auto.getTiVos();
      if (tivoNames.size() > 0) {
         for (int i=0; i<tivoNames.size(); i++) {
            // Queue up a nowplaying list job for this tivo
            config.GUI_AUTO++;
            tivoTab t = getTab(tivoNames.get(i));
            if (t != null) {
               jobMonitor.getNPL(tivoNames.get(i));
            }
         }
      }
   }

   // Callback for "Loop in GUI" Auto Transfers menu entry
   // This is equivalent to auto mode run but is performed in GUI
   public void autoLoopInGUICB(Boolean enabled) {
      debug.print("enabled=" + enabled);
      // This triggers jobMonitor to clear launch hash
      config.GUI_AUTO = -1;

      // If button enabled then start Loop in GUI mode, else exit that mode
      if (enabled) {
         // If kmttg service or background job running prompt user to stop it
         Boolean auto_running = false;
         String question = "";
         if (config.OS.equals("windows")) {
            // Query to stop windows service if it's running
            String query = auto.serviceStatus();
            if (query != null && query.matches("^.+RUNNING$")) {
               auto_running = true;
               question = "kmttg service is currently running. Stop the service?";
            }
         } else {
            auto_running = auto.unixAutoIsRunning(false);
            question = "kmttg background job is currently running. Stop the job?";
         }
         if (auto_running) {
            int result = JOptionPane.showConfirmDialog(
               jFrame, question, "Confirm", JOptionPane.OK_CANCEL_OPTION
            );
            if (result == JOptionPane.OK_OPTION) {
               if (config.OS.equals("windows")) {
                  auto.serviceStop();
               } else {
                  auto.unixAutoKill();
               }
            }
         }

         // Start Loop in GUI mode
         config.GUI_LOOP = 1;
         log.warn("\nAuto Transfers Loop in GUI enabled");
      } else {
         // Stop Loop in GUI mode
         log.warn("\nAuto Transfers Loop in GUI disabled");
         config.GUI_LOOP = 0;
         log.stopLogger();
      }
   }

   // Encoding cyclic change callback
   // Set the description according to selected item
   private void encodingCB(JComboBox<String> combo) {
      debug.print("combo=" + combo);
      String encodeName = (String)combo.getSelectedItem();
      if (encodeName == null)
         return;
      config.encodeName = encodeName;
      String description = encodeConfig.getDescription(encodeName);
      // Set encoding_description_label accordingly
      encoding_description_label.setText("  " + description);
   }

   // Cancel button callback
   // Kill and remove selected jobs from job monitor
   private void cancelCB() {
      debug.print("");
      int[] rows = TableUtil.GetSelectedRows(jobTab.JobMonitor);

      if (rows.length > 0) {
         int row;
         for (int i=rows.length-1; i>=0; i--) {
            row = rows[i];
            jobData job = jobTab.GetSelectionData(row);
            if (job != null) jobMonitor.kill(job);
         }
      }
   }

   // Add a new tab pane
   private void addTabPane(String name, JTabbedPane pane, JComponent content) {
      debug.print("name=" + name + " pane=" + pane + " content=" + content);
      // Prevent duplicates
      if (pane.indexOfTab(name) == -1) {
         pane.addTab(name, content);
      }
   }

   // Create tivo tabs as needed
   public void SetTivos(LinkedHashMap<String,String> values) {
      debug.print("values=" + values);
      if ( values.size() > 1 ) {
         String[] names = new String[values.size()-1];
         int i = 0;
         for (String value : values.keySet()) {
            if (! value.equals("FILES") && ! value.equals("Remote")) {
               if (config.nplCapable(value)) {
                  names[i] = value;
                  i++;
               }
            }
         }

         // Remove unwanted tabs
         tivoTabRemoveExtra(names);

         // Add tabs
         for (int j=i-1; j>=0; j--) {
            tivoTabAdd(names[j]);
         }

         // remote gui
         if (remote_gui != null)
            remote_gui.setTivoNames();

      } else {
         // Remove all tivo tabs
         while (tabbed_panel.getTabCount() > 0) {
            String itemName = tabbed_panel.getTitleAt(0);
            if (itemName.equals("FILES") || itemName.equals("Remote"))
               break;
            tivoTabRemove(itemName);
         }
      }
   }

   // Start NPL jobs for 1st time
   public void initialNPL(LinkedHashMap<String,String> values) {
      debug.print("values=" + values);
      for (String value : values.keySet()) {
         if (! value.equals("FILES") && ! value.equals("Remote") && config.nplCapable(value)) {
            jobMonitor.getNPL(value);
         }
      }
   }

   public String getCurrentTabName() {
      debug.print("");
      int index = tabbed_panel.getSelectedIndex();
      if (index < 0)
         return null;
      return tabbed_panel.getTitleAt(index);
   }

   public String getSelectedTivoName() {
      debug.print("");
      String tabName = getCurrentTabName();
      if (tabName != null && ! tabName.equals("FILES") && ! tabName.equals("Remote") && ! tabName.equals("Slingbox")) {
         return tabName;
      }
      return null;
   }

   public String getCurrentRemoteTivoName() {
      debug.print("");
      if (getCurrentTabName().equals("Remote"))
         return config.gui.remote_gui.getTivoName(config.gui.remote_gui.getCurrentTabName());
      return null;
   }

   public JSONObject getCurrentRemoteJson() {
      debug.print("");
      if (getCurrentTabName().equals("Remote"))
         return config.gui.remote_gui.getSelectedJSON(config.gui.remote_gui.getCurrentTabName());
      return null;
   }

   // Check name against existing tabbed panel names
   private Boolean tivoTabExists(String name) {
      debug.print("name=" + name);
      return tabbed_panel.indexOfTab(name) >= 0;
   }

   private void tivoTabAdd(String name) {
      debug.print("name=" + name);
      if ( ! tivoTabExists(name) ) {
         tivoTab tab = new tivoTab(name);
         tabbed_panel.insertTab(name, null, tab.getPanel(), null, 0);
         tivoTabs.put(name,tab);
      }
   }

   private void tivoTabRemove(String name) {
      debug.print("name=" + name);
      if (tivoTabs.containsKey(name)) {
         int index = tabbed_panel.indexOfTab(name);
         if (index >= 0)
            tabbed_panel.removeTabAt(index);
         tivoTabs.remove(name);
      }
   }

   private void tivoTabRemoveExtra(String[] names) {
      debug.print("names=" + Arrays.toString(names));
      int numTabs = tabbed_panel.getTabCount();
      if (numTabs > 0 && names.length > 0) {
         // Determine tabs we no longer want
         Stack<String> unwanted = new Stack<String>();
         String tabName;
         Boolean remove;
         for (int i=0; i<numTabs; i++) {
            tabName = tabbed_panel.getTitleAt(i);
            if (tabName != null && ! tabName.equals("FILES") && ! tabName.equals("Remote")) {
               remove = true;
               for (int j=0; j<names.length; j++) {
                  if (names[j] != null && names[j].equals(tabName)) {
                     remove = false;
                  }
               }
               if (remove) {
                  unwanted.add(tabName);
               }
            }
         }
         // Now remove the unwanted tabs
         if (unwanted.size() > 0) {
            for (int i=0; i<unwanted.size(); i++) {
               tivoTabRemove(unwanted.get(i));
            }
         }
      }
   }

   // Set current tab to this tivo (if valid)
   public void SetTivo(String tivoName) {
      debug.print("tivoName=" + tivoName);
      int index = tabbed_panel.indexOfTab(tivoName);
      if (index >= 0)
         tabbed_panel.setSelectedIndex(index);
   }

   // Add a tivo
   public void AddTivo(String name, String ip) {
      debug.print("name=" + name + " ip=" + ip);
      tivoTabAdd(name);
      configMain.addTivo(name, ip);
   }

   // Set encoding ComboBox choices
   public void SetEncodings(final Stack<String> values) {
      debug.print("values=" + values);

      if (encoding != null) {
         SwingUtil.runLater(new Runnable() {
            @Override public void run() {
               // Get existing setting in ComboBox
               String current = null;
               if (encoding.getItemCount() > 0) {
                  current = (String)encoding.getSelectedItem();
               }
               Boolean valid = false;
               String[] names = new String[values.size()];
               for (int i=0; i<values.size(); ++i) {
                  names[i] = values.get(i);
                  if (current != null && current.equals(names[i]))
                     valid = true;
               }
               combobox.SetValues(encoding, names);
               if (! valid)
                  current = null;
               if (current != null)
                  encoding.setSelectedItem(current);
               else {
                  if (encoding.getItemCount() > 0)
                     encoding.setSelectedIndex(0);
               }
            }
         });
      }
   }

   public void SetSelectedEncoding(final String name) {
      debug.print("name=" + name);
      SwingUtil.runLater(new Runnable() {
          @Override public void run() {
            if (encoding.getItemCount() > 0) {
               encoding.setSelectedItem(name);
            }
          }
      });
   }

   private void CreateImages() {
      debug.print("");
      Images = new Hashtable<String,java.awt.Image>();
      String[] names = {
         "expires-soon-recording", "save-until-i-delete-recording",
         "in-progress-recording", "in-progress-transfer",
         "expired-recording", "suggestion-recording", "folder",
         "copy-protected", "running", "queued", "skipmode",
         "image-season-pass", "image-season-pass-wishlist",
         "image-single-explicit-record"
      };
      for (int i=0; i<names.length; i++) {
         try {
            // From jar file
            Images.put(names[i], ImageIO.read(getClass().getResourceAsStream("/" + names[i] + ".png")));
         } catch (Exception e) {
            try {
               // From eclipse
               Images.put(names[i], ImageIO.read(new File("images/" + names[i] + ".png")));
            } catch (Exception e2) {
               debug.print(e2.toString());
            }
         }
      }
   }

   public static void LoadIcons(Window window) {
      SwingUtil.loadIcons(window);
   }

   // Save current GUI settings to a file
   public void saveSettings() {
      debug.print("");
      if (config.gui_settings != null) {
         if (slingbox_gui != null)
            slingbox_gui.updateConfig();
         try {
            double centerDivider = dividerFraction(jContentPane);
            double bottomDivider = dividerFraction(splitBottom);
            String tabName = getCurrentTabName();
            int width = jFrame.getWidth(); if (width <0) width = 0;
            int height = jFrame.getHeight(); if (height <0) height = 0;
            int x = jFrame.getX(); if (x <0) x = 0;
            int y = jFrame.getY(); if (y <0) y = 0;
            BufferedWriter ofp = new BufferedWriter(new FileWriter(config.gui_settings));
            ofp.write("# kmttg gui preferences file\n");
            ofp.write("<GUI_LOOP>\n"            + config.GUI_LOOP            + "\n");
            ofp.write("<TSdownload>\n"          + TSdownload_setting()       + "\n");
            ofp.write("<metadata>\n"            + metadata_setting()         + "\n");
            ofp.write("<decrypt>\n"             + decrypt_setting()          + "\n");
            ofp.write("<qsfix>\n"               + qsfix_setting()            + "\n");
            ofp.write("<twpdelete>\n"           + twpdelete_setting()        + "\n");
            ofp.write("<rpcdelete>\n"           + rpcdelete_setting()        + "\n");
            ofp.write("<comskip>\n"             + comskip_setting()          + "\n");
            ofp.write("<comcut>\n"              + comcut_setting()           + "\n");
            ofp.write("<captions>\n"            + captions_setting()         + "\n");
            ofp.write("<encode>\n"              + encode_setting()           + "\n");
            ofp.write("<custom>\n"              + custom_setting()           + "\n");
            ofp.write("<encode_name>\n"         + config.encodeName          + "\n");
            ofp.write("<toolTips>\n"            + config.toolTips            + "\n");
            ofp.write("<toolTipsDelay>\n"       + config.toolTipsDelay       + "\n");
            ofp.write("<toolTipsTimeout>\n"     + config.toolTipsTimeout     + "\n");
            ofp.write("<slingBox>\n"            + config.slingBox            + "\n");
            ofp.write("<slingBox_perl>\n"       + config.slingBox_perl       + "\n");
            ofp.write("<slingBox_dir>\n"        + config.slingBox_dir        + "\n");
            ofp.write("<slingBox_ip>\n"         + config.slingBox_ip         + "\n");
            ofp.write("<slingBox_port>\n"       + config.slingBox_port       + "\n");
            ofp.write("<slingBox_pass>\n"       + config.slingBox_pass       + "\n");
            ofp.write("<slingBox_res>\n"        + config.slingBox_res        + "\n");
            ofp.write("<slingBox_vbw>\n"        + config.slingBox_vbw        + "\n");
            ofp.write("<slingBox_type>\n"       + config.slingBox_type       + "\n");
            ofp.write("<slingBox_container>\n"  + config.slingBox_container  + "\n");
            ofp.write("<jobMonitorFullPaths>\n" + config.jobMonitorFullPaths + "\n");
            ofp.write("<width>\n"               + width                      + "\n");
            ofp.write("<height>\n"              + height                     + "\n");
            ofp.write("<x>\n"                   + x                          + "\n");
            ofp.write("<y>\n"                   + y                          + "\n");
            ofp.write("<centerDivider>\n"       + centerDivider              + "\n");
            ofp.write("<bottomDivider>\n"       + bottomDivider              + "\n");
            if (remote_gui != null) {
               int tabIndex_r = remote_gui.tabbed_panel.getSelectedIndex();
               ofp.write("<tab_remote>\n"       + tabIndex_r                 + "\n");
            }
            ofp.write("<tab>\n"                 + tabName                    + "\n");

            ofp.write("<columnOrder>\n");
            String name, colName;
            // NPL & Files tables
            for (Enumeration<String> e=tivoTabs.keys(); e.hasMoreElements();) {
               name = e.nextElement();
               String order[] = tivoTabs.get(name).getColumnOrder();
               colName = order[0];
               if (colName.equals("")) colName = "ICON";
               ofp.write(name + "=" + colName);
               for (int j=1; j<order.length; ++j) {
                  colName = order[j];
                  if (colName.equals("")) colName = "ICON";
                  ofp.write("," + colName);
               }
               ofp.write("\n");
            }
            // Job table
            String order[] = jobTab.getColumnOrder();
            ofp.write("JOBS=" + order[0]);
            for (int j=1; j<order.length; ++j) {
               ofp.write("," + order[j]);
            }
            ofp.write("\n\n");

            ofp.write("<columnWidths>\n");
            for (Enumeration<String> e=tivoTabs.keys(); e.hasMoreElements();) {
               name = e.nextElement();
               writeWidths(name, tivoTabs.get(name).getTable().NowPlaying.table, ofp);
            }

            writeWidths("jobTable", config.gui.jobTab.JobMonitor, ofp);
            if (remote_gui != null) {
               writeWidths("todoTable", remote_gui.todo_tab.tab.TABLE, ofp);
               writeWidths("spTable", remote_gui.sp_tab.tab.TABLE, ofp);
               writeWidths("premiereTable", remote_gui.premiere_tab.tab.TABLE, ofp);
               writeWidths("guideTable", remote_gui.guide_tab.tab.TABLE, ofp);
               writeWidths("deletedTable", remote_gui.deleted_tab.tab.TABLE, ofp);
               writeWidths("channelsTable", remote_gui.channels_tab.tab.TABLE, ofp);
               writeWidths("thumbsTable", remote_gui.thumbs_tab.tab.TABLE, ofp);
               writeWidths("cancelTable", remote_gui.cancel_tab.tab.TABLE.table, ofp);
               writeWidths("searchTable", remote_gui.search_tab.tab.TABLE.table, ofp);
               writeWidths("streamTable", remote_gui.stream_tab.tab.TABLE.table, ofp);
            }
            ofp.write("\n");

            ofp.write("<showFolders>\n");
            for (Enumeration<String> e=tivoTabs.keys(); e.hasMoreElements();) {
               name = e.nextElement();
               if ( ! name.equals("FILES") && ! name.equals("Remote") ) {
                  if (tivoTabs.get(name).showFolders()) {
                     ofp.write(name + "=" + 1 + "\n");
                  } else {
                     ofp.write(name + "=" + 0 + "\n");
                  }
               }
            }
            if (remote_gui != null) {
               String[]names = {
                  "todo", "sp", "cancel", "premiere", "search", "guide", "stream", "deleted", "thumbs", "rc", "info"
               };
               ofp.write("\n<rpc_tivo>\n");
               for (String tab : names)
                  ofp.write(tab + "=" + remote_gui.getTivoName(tab) + "\n");
               ofp.write("\n<rpc_includePast>\n");
               if (remote_gui.cancel_tab.includeHistory.isSelected())
                  ofp.write("1\n");
               else
                  ofp.write("0\n");

               // Search max hits
               int max = (Integer) remote_gui.search_tab.max.getValue();
               ofp.write("\n<rpc_search_max>\n");
               ofp.write("" + max + "\n");

               // Search streaming settings
               ofp.write("\n<rpc_search_type>\n");
               ofp.write("" + remote_gui.search_tab.search_type.getSelectedItem());

               int includeFree = 0;
               if (remote_gui.search_tab.includeFree.isSelected())
                  includeFree = 1;
               ofp.write("\n<rpc_search_includeFree>\n");
               ofp.write("" + includeFree + "\n");

               int includePaid = 0;
               if (remote_gui.search_tab.includePaid.isSelected())
                  includePaid = 1;
               ofp.write("\n<rpc_search_includePaid>\n");
               ofp.write("" + includePaid + "\n");

               // Record dialog
               JSONObject json = com.tivo.kmttg.gui.remote.util.recordOpt.getValues();
               if (json != null) {
                  try {
                     ofp.write("\n<rpc_recordOpt>\n");
                     String[] n = {"keepBehavior", "startTimePadding", "endTimePadding", "anywhere"};
                     for (int j=0; j<n.length; ++j) {
                        ofp.write(n[j] + "=" + json.get(n[j]) + "\n");
                     }
                  } catch (JSONException e) {
                     log.error(e.getMessage());
                     log.error(Arrays.toString(e.getStackTrace()));
                  }
               }

               // SP dialog
               json = com.tivo.kmttg.gui.remote.util.spOpt.getValues();
               if (json != null) {
                  try {
                     ofp.write("\n<rpc_spOpt>\n");
                     String[] n = {"showStatus", "maxRecordings", "keepBehavior", "startTimePadding", "endTimePadding"};
                     for (int j=0; j<n.length; ++j) {
                        ofp.write(n[j] + "=" + json.get(n[j]) + "\n");
                     }
                  } catch (JSONException e) {
                     log.error(e.getMessage());
                     log.error(Arrays.toString(e.getStackTrace()));
                  }
               }
            }

            ofp.write("\n");
            ofp.close();
         }
         catch (IOException ex) {
            log.error("Problem writing to file: " + config.gui_settings);
         }
      }
   }

   // Fraction of split pane taken by top component
   private double dividerFraction(JSplitPane pane) {
      int total = pane.getHeight() - pane.getDividerSize();
      if (total <= 0)
         return -1;
      return (double)pane.getDividerLocation() / total;
   }

   // Read initial settings from file
   public void readSettings() {
      debug.print("");
      if (! file.isFile(config.gui_settings)) {
         return;
      }
      try {
         int width = -1;
         int height = -1;
         int x = -1;
         int y = -1;
         int value;
         double centerDivider = -1, bottomDivider = -1;
         BufferedReader ifp = new BufferedReader(new FileReader(config.gui_settings));
         String line = null;
         String key = null;
         JSONObject rpc_recordOpt = new JSONObject();
         JSONObject rpc_spOpt = new JSONObject();
         while (( line = ifp.readLine()) != null) {
            // Get rid of leading and trailing white space
            line = line.replaceFirst("^\\s*(.*$)", "$1");
            line = line.replaceFirst("^(.*)\\s*$", "$1");
            if (line.length() == 0) continue; // skip empty lines
            if (line.matches("^#.+")) continue; // skip comment lines
            if (line.matches("^<.+>")) {
               key = line.replaceFirst("<", "");
               key = key.replaceFirst(">", "");
               continue;
            }
            if (key.equals("GUI_LOOP")) {
               if (line.matches("1"))
                  loopInGuiMenuItem.setSelected(true);
            }
            if (key.equals("TSdownload")) {
               if (line.matches("1"))
                  TSdownload.setSelected(true);
               else
                  TSdownload.setSelected(false);
            }
            if (key.equals("metadata")) {
               if (line.matches("1"))
                  metadata.setSelected(true);
               else
                  metadata.setSelected(false);
            }
            if (key.equals("decrypt")) {
               if (line.matches("1"))
                  decrypt.setSelected(true);
               else
                  decrypt.setSelected(false);
            }
            if (key.equals("qsfix")) {
               if (line.matches("1"))
                  qsfix.setSelected(true);
               else
                  qsfix.setSelected(false);
            }
            if (key.equals("twpdelete")) {
               if (line.matches("1"))
                  twpdelete.setSelected(true);
               else
                  twpdelete.setSelected(false);
            }
            if (key.equals("rpcdelete")) {
               if (line.matches("1"))
                  rpcdelete.setSelected(true);
               else
                  rpcdelete.setSelected(false);
            }
            if (key.equals("comskip")) {
               if (line.matches("1"))
                  comskip.setSelected(true);
               else
                  comskip.setSelected(false);
            }
            if (key.equals("comcut")) {
               if (line.matches("1"))
                  comcut.setSelected(true);
               else
                  comcut.setSelected(false);
            }
            if (key.equals("captions")) {
               if (line.matches("1"))
                  captions.setSelected(true);
               else
                  captions.setSelected(false);
            }
            if (key.equals("encode")) {
               if (line.matches("1"))
                  encode.setSelected(true);
               else
                  encode.setSelected(false);
            }
            if (key.equals("custom")) {
               if (line.matches("1"))
                  custom.setSelected(true);
               else
                  custom.setSelected(false);
            }
            if (key.equals("toolTips")) {
               if (line.matches("1"))
                  config.toolTips = 1;
               else
                  config.toolTips = 0;
            }
            if (key.equals("slingBox")) {
               if (line.matches("1"))
                  config.slingBox = 1;
               else
                  config.slingBox = 0;
            }
            if (key.equals("slingBox_pass"))
               config.slingBox_pass = line;
            if (key.equals("slingBox_ip"))
               config.slingBox_ip = line;
            if (key.equals("slingBox_port"))
               config.slingBox_port = line;
            if (key.equals("slingBox_perl"))
               config.slingBox_perl = line;
            if (key.equals("slingBox_dir"))
               config.slingBox_dir = line;
            if (key.equals("slingBox_res"))
               config.slingBox_res = line;
            if (key.equals("slingBox_vbw"))
               config.slingBox_vbw = line;
            if (key.equals("slingBox_type"))
               config.slingBox_type = line;
            if (key.equals("slingBox_container"))
               config.slingBox_container = line;
            if (key.equals("jobMonitorFullPaths")) {
               if (line.matches("1"))
                  config.jobMonitorFullPaths = 1;
               else
                  config.jobMonitorFullPaths = 0;
            }
            if (key.equals("encode_name")) {
               config.encodeName_orig = line;
               if (encodeConfig.isValidEncodeName(line)) {
                  config.encodeName = line;
                  // runlater needed else doesn't get set right at kmttg startup
                  final String line_final = line;
                  SwingUtil.runLater(new Runnable() {
                     @Override public void run() {
                        config.encodeName = line_final;
                        encoding.setSelectedItem(line_final);
                     }
                  });
               }
            }
            if (key.equals("toolTipsDelay")) {
               try {
                  config.toolTipsDelay = Integer.parseInt(line);
               } catch (NumberFormatException e) {
                  config.toolTipsDelay = 2;
               }
            }
            if (key.equals("toolTipsTimeout")) {
               try {
                  config.toolTipsTimeout = Integer.parseInt(line);
               } catch (NumberFormatException e) {
                  config.toolTipsTimeout = 20;
               }
            }
            if (key.equals("width")) {
               try {
                  width = Integer.parseInt(line);
               } catch (NumberFormatException e) {
                  width = -1;
               }
            }
            if (key.equals("height")) {
               try {
                  height = Integer.parseInt(line);
               } catch (NumberFormatException e) {
                  height = -1;
               }
            }
            if (key.equals("x")) {
               try {
                  x = Integer.parseInt(line);
               } catch (NumberFormatException e) {
                  x = -1;
               }
            }
            if (key.equals("y")) {
               try {
                  y = Integer.parseInt(line);
               } catch (NumberFormatException e) {
                  y = -1;
               }
            }
            if (key.equals("tab_remote")) {
               try {
                  value = Integer.parseInt(line);
               } catch (NumberFormatException e) {
                  value = 0;
               }
               if (remote_gui != null) {
                  class backgroundRun implements Runnable {
                     int value;
                     public backgroundRun(int value) {
                        this.value = value;
                     }
                     @Override public void run() {
                        if (value >= 0 && value < remote_gui.getPanel().getTabCount())
                           remote_gui.getPanel().setSelectedIndex(value);
                     }
                  }
                  SwingUtil.runLater(new backgroundRun(value));
               }
            }
            if (key.equals("centerDivider")) {
               try {
                  centerDivider = Double.parseDouble(line);
               } catch (NumberFormatException e) {
                  centerDivider = -1;
               }
            }
            if (key.equals("bottomDivider")) {
               try {
                  bottomDivider = Double.parseDouble(line);
               } catch (NumberFormatException e) {
                  bottomDivider = -1;
               }
            }
            if (key.equals("tab")) {
               SetTivo(line);
            }
            if (key.equals("columnOrder")) {
               String[] l = line.split("=");
               String[] order = l[1].split(",");
               if (tivoTabs.containsKey(l[0])) {
                  tivoTabs.get(l[0]).setColumnOrder(order);
               }
               if (l[0].equals("JOBS")) {
                  jobTab.setColumnOrder(order);
               }
            }
            if (key.equals("columnWidths")) {
               String[] l = line.split("=");
               String name = l[0];
               String[] order = l[1].split(",");
               int[] widths = new int[order.length];
               for (int i=0; i<order.length; ++i) {
                  widths[i] = Integer.parseInt(order[i]);
               }
               if (tivoTabs.containsKey(name)) {
                  setWidths(tivoTabs.get(name).getTable().NowPlaying.table, widths);
               }
               if (name.equals("jobTable")) {
                  setWidths(config.gui.jobTab.JobMonitor, widths);
               }
               if (remote_gui != null) {
                  if (name.equals("todoTable"))
                     setWidths(remote_gui.todo_tab.tab.TABLE, widths);
                  if (name.equals("spTable"))
                     setWidths(remote_gui.sp_tab.tab.TABLE, widths);
                  if (name.equals("premiereTable"))
                     setWidths(remote_gui.premiere_tab.tab.TABLE, widths);
                  if (name.equals("guideTable"))
                     setWidths(remote_gui.guide_tab.tab.TABLE, widths);
                  if (name.equals("deletedTable"))
                     setWidths(remote_gui.deleted_tab.tab.TABLE, widths);
                  if (name.equals("channelsTable"))
                     setWidths(remote_gui.channels_tab.tab.TABLE, widths);
                  if (name.equals("thumbsTable"))
                     setWidths(remote_gui.thumbs_tab.tab.TABLE, widths);
                  if (name.equals("cancelTable"))
                     setWidths(remote_gui.cancel_tab.tab.TABLE.table, widths);
                  if (name.equals("searchTable"))
                     setWidths(remote_gui.search_tab.tab.TABLE.table, widths);
                  if (name.equals("streamTable"))
                     setWidths(remote_gui.stream_tab.tab.TABLE.table, widths);
               }
            }
            if (key.equals("showFolders")) {
               String[] l = line.split("=");
               if (l[1].equals("1")) {
                  if (tivoTabs.containsKey(l[0]))
                     tivoTabs.get(l[0]).showFoldersSet(true);
               }
            }
            if (key.equals("rpc_tivo") && remote_gui != null) {
               String[] l = line.split("=");
               if (l.length == 2 && tivoTabs.containsKey(l[1]))
                  remote_gui.setTivoName(l[0], l[1]);
            }

            if (key.equals("rpc_search_max") && remote_gui != null) {
               try {
                  int max = Integer.parseInt(line);
                  remote_gui.search_tab.max.setValue(max);
               }
               catch (NumberFormatException ex) {
                  // Don't do anything here
               }
            }

            if (key.equals("rpc_search_type") && remote_gui != null) {
               String search_type = string.removeLeadingTrailingSpaces(line);
               remote_gui.search_tab.search_type.setSelectedItem(search_type);
            }

            if (key.equals("rpc_search_includeFree") && remote_gui != null) {
               try {
                  int includeFree = Integer.parseInt(line);
                  remote_gui.search_tab.includeFree.setSelected(includeFree == 1);
               }
               catch (NumberFormatException ex) {
                  // Don't do anything here
               }
            }

            if (key.equals("rpc_search_includePaid") && remote_gui != null) {
               try {
                  int includePaid = Integer.parseInt(line);
                  remote_gui.search_tab.includePaid.setSelected(includePaid == 1);
               }
               catch (NumberFormatException ex) {
                  // Don't do anything here
               }
            }

            if (key.equals("rpc_includePast") && remote_gui != null) {
               if (line.matches("1"))
                  remote_gui.cancel_tab.includeHistory.setSelected(true);
            }

            if (key.equals("rpc_recordOpt") && remote_gui != null) {
               String[] l = line.split("=");
               if (l.length == 2) {
                  rpc_recordOpt.put(l[0], l[1]);
               }
            }
            if (key.equals("rpc_spOpt") && remote_gui != null) {
               String[] l = line.split("=");
               if (l.length == 2) {
                  rpc_spOpt.put(l[0], l[1]);
               }
            }
         }
         ifp.close();

         if (remote_gui != null) {
            if (rpc_recordOpt.length() > 0) {
               com.tivo.kmttg.gui.remote.util.recordOpt.setValues(rpc_recordOpt);
            }
            if (rpc_spOpt.length() > 0) {
               com.tivo.kmttg.gui.remote.util.spOpt.setValues(rpc_spOpt);
            }
         }

         if (width > 0 && height > 0) {
            jFrame.setSize(width, height);
         }

         if (x >= 0 && y >= 0) {
            Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            if (x < bounds.getMinX()) x = (int)bounds.getMinX();
            if (x > bounds.getMaxX()) x = (int)bounds.getMinX();
            if (y < bounds.getMinY()) y = (int)bounds.getMinY();
            if (y > bounds.getMaxY()) y = (int)bounds.getMinY();
            jFrame.setLocation(x, y);
         }

         class backgroundRun implements Runnable {
            double centerDivider, bottomDivider;
            public backgroundRun(double centerDivider, double bottomDivider) {
               this.centerDivider = centerDivider;
               this.bottomDivider = bottomDivider;
            }
            @Override public void run() {
               if (centerDivider > 0 && centerDivider < 1)
                  jContentPane.setDividerLocation(centerDivider);

               if (bottomDivider > 0 && bottomDivider < 1)
                  splitBottom.setDividerLocation(bottomDivider);
            }
         }
         SwingUtil.runLater(new backgroundRun(centerDivider, bottomDivider));
      }
      catch (Exception ex) {
         log.warn("Problem parsing config file: " + config.gui_settings);
         log.warn(Arrays.toString(ex.getStackTrace()));
      }
   }

   private void writeWidths(String name, JTable table, BufferedWriter ofp) {
      try {
         ofp.write("" + name + "=");
         TableColumnModel cols = table.getColumnModel();
         for (int i=0; i<cols.getColumnCount(); ++i) {
            int w = cols.getColumn(i).getWidth();
            if (i==0)
               ofp.write("" + w);
            else
               ofp.write("," + w);
         }
         ofp.write("\n");
      } catch (Exception e) {
         log.error("writeWidths - " + e.getMessage());
      }
   }

   private void setWidths(JTable table, int[] widths) {
      TableColumnModel cols = table.getColumnModel();
      for (int i=0; i<cols.getColumnCount(); ++i) {
         try {
            cols.getColumn(i).setPreferredWidth(widths[i]);
         } catch (Exception e) {
            // Ignore exceptions
         }
      }
   }

   // Component tooltip setup
   public void setToolTips() {
      debug.print("");
      TSdownload.setToolTipText(getToolTip("TSdownload"));
      metadata.setToolTipText(getToolTip("metadata"));
      decrypt.setToolTipText(getToolTip("decrypt"));
      qsfix.setToolTipText(getToolTip("qsfix"));
      twpdelete.setToolTipText(getToolTip("twpdelete"));
      rpcdelete.setToolTipText(getToolTip("rpcdelete"));
      comskip.setToolTipText(getToolTip("comskip"));
      comcut.setToolTipText(getToolTip("comcut"));
      captions.setToolTipText(getToolTip("captions"));
      encode.setToolTipText(getToolTip("encode"));
      custom.setToolTipText(getToolTip("custom"));
      encoding.setToolTipText(getToolTip("encoding"));
   }

   public String getToolTip(String component) {
      debug.print("component=" + component);
      String text = "";
      if (component.equals("tivos")) {
         text =  "<b>TIVOS</b><br>";
         text += "Select <b>FILES</b> mode or a <b>TiVo</b> on your network.<br>";
         text += "<b>FILES</b> mode allows you to select existing TiVo or mpeg2 files on your computer.<br>";
         text += "<b>TiVo</b> mode allows you to get a listing of all shows for a TiVo on your home network.";
      }
      else if (component.equals("add")) {
         text =  "<b>Add...</b><br>";
         text += "Brings up a file browser for selecting video files to process.<br>";
         text += "Selected files are added to files table below.<br>";
         text += "NOTE: For Mac OS you can get to other disk volumes by browsing to<br>";
         text += "<b>/Volumes</b> with the browser.";
      }
      else if (component.equals("remove")) {
         text =  "<b>Remove</b><br>";
         text += "Removes selected file entries from files table below.";
      }
      else if (component.equals("atomic")) {
         text =  "<b>Run AtomicParsley</b><br>";
         text += "Run AtomicParsley on files selected in table below.<br>";
         text += "This is only supported for mp4/m4v files ending in .mp4 or .m4v suffix.<br>";
         text += "Change <b>Files of Type</b> to <b>All Files</b> in File browser to see all file types.<br>";
         text += "NOTE: There must be accompanying pyTivo metadata .txt file for this command to work.";
      }
      else if (component.equals("partiallyViewed")) {
         text = "<b>Partially Viewed</b><br>";
         text += "If enabled then only shows that have been partially watched will be obtained on Refresh.<br>";
         text += "If you refresh with this option turned off then this can be used to toggle between full<br>";
         text += "listings and only partially watched show listings.<br>";
         text += "If you refresh with this option turned on, it's a quick way to get only partially viewed<br>";
         text += "shows compared to retrieving full list of shows.<br>";
         text += "NOTE: This option only applies when <b>Use RPC to get NPL when possible</b> option is enabled.";
      }
      else if (component.equals("pyTivo_stream")) {
         text =  "<b>pyTivo stream</b><br>";
         text += "Use TiVoCast HME app with pyTivo as a video server to stream a video file to<br>";
         text += "a series 4 or later TiVo. pyTivo.conf must be configured in kmttg and the video<br>";
         text += "file must be within a pyTivo video share folder structure.<br>";
         text += "NOTE: You must have at least 1 series 4 or later TiVo with rpc style communications<br>";
         text += "enabled to use as a destination TiVo.<br>";
         text += "VIDEO RESTRICTIONS: Source video must be either:<br>";
         text += "<b>Unencrypted mpeg2 program stream</b> (.TiVo files won't work).<br>";
         text += "<b>mp4 container with H.264 video and either AC3 or AAC audio</b><br>";
         text += "Any other type of video won't work, and transcoding pyTivo videos won't work.";
      }
      else if (component.equals("refresh")) {
         text =  "<b>Refresh List</b><br>";
         text += "Refresh Now Playing List for this TiVo.";
      }
      else if (component.equals("back")) {
         text =  "<b>Back</b><br>";
         text += "Exit folder view and return to top level Now Playing List for this TiVo.";
      }
      else if (component.equals("TSdownload")) {
         text =  "<b>TS downloads</b><br>";
         text += "If enabled then downloads from TiVo will be using Transport Stream<br>";
         text += "contanier instead of mpeg2 Program Stream container.<br>";
         text += "H.264 recordings <b>MUST</b> be downloaded as Transport Stream or<br>";
         text += "else you will only get audio as part of the download.<br>";
         text += "TS downloads can contain more glitches and <b>Resume Downloads</b><br>";
         text += "don't work with TS downloads, so there are disadvantages to them.";
      }
      else if (component.equals("metadata")) {
         text =  "<b>metadata</b><br>";
         text += "Creates a <b>pyTivo</b> compatible metadata file.<br>";
         text += "This is a text file that accompanies video file that contains<br>";
         text += "extended program information about the video file.<br>";
         text += "Useful if you use pyTivo to copy video files back to your Tivos.<br>";
         text += "Under configuration <b>Program Options</b> tab there is an option<br>";
         text += "called <b>metadata files</b> where you can specify which video files<br>";
         text += "to create metadata files for.";
      }
      else if (component.equals("decrypt")) {
         text =  "<b>decrypt</b><br>";
         text += "Decrypts encrypted TiVo files that were downloaded from TiVos.<br>";
         text += "Converts video file to normal unencrypted mpeg2 program stream format<br>";
         text += "which can be played back by most video players without need to have Tivo<br>";
         text += "Desktop installed. NOTE: This is quick and does not affect video quality.<br>";
         text += "This is also necessary before doing any further video file processing<br>";
         text += "with kmttg, so most often you should leave this option enabled.";
      }
      else if (component.equals("qsfix")) {
         text =  "<b>QS Fix</b><br>";
         text += "If you have VideoRedo available and configured in kmttg, this<br>";
         text += "runs the extremely useful <b>VideoRedo Quick Stream Fix</b> utility.<br>";
         text += "Without VideoRedo this will run mpeg through <b>ffmpeg</b> remux.<br>";
         text += "If neither tool is configured then this task is unavailable.<br>";
         text += "This task cleans up any potential glitches/errors in mpeg2 video files.<br>";
         text += "Highly recommended step if you have VideoRedo and/or ffmpeg installed.<br>";
         text += "Very highly recommended step if you will be further processing mpeg2 files<br>";
         text += "for cutting out commercials and/or encoding to new formats.";
      }
      else if (component.equals("twpdelete")) {
         text =  "<b>TWP Delete</b><br>";
         text += "If you have TivoWebPlus configured on your TiVo(s) then if you enable this task<br>";
         text += "a TivoWebPlus http call to delete show on TiVo will be issued following<br>";
         text += "successful decrypt of a downloaded .TiVo file.";
      }
      else if (component.equals("rpcdelete")) {
         text =  "<b>rpc Delete</b><br>";
         text += "If you have Series 4 (Premiere) TiVo or later with Network Remote setting enabled<br>";
         text += "then if you enable this task, rpc style communications will be used to<br>";
         text += "delete show on TiVo following successful decrypt of a downloaded .TiVo file.";
      }
      else if (component.equals("comskip")) {
         text =  "<b>Ad Detect</b><br>";
         text += "Automated commercials detection tool (defaults to <b>comskip</b> tool).<br>";
         text += "NOTE: Typically automated commercial detection is NOT very accurate.<br>";
         text += "NOTE: If you have <b>VideoRedo</b> enabled you can choose to use.<br>";
         text += "VideoRedo <b>AdScan</b> instead of comskip if you wish.<br>";
         text += "With VideoRedo configured you can also use this step to create a <b>.VPrj</b><br>";
         text += "file that you can open up in VideoRedo as a starting point for manual<br>";
         text += "commercial editing. See documentation for more details.";
      }
      else if (component.equals("comcut")) {
         text =  "<b>Ad Cut</b><br>";
         text += "Automatically cut out commercials detected in <b>Ad Detect</b> step.<br>";
         text += "NOTE: By default uses <b>ffmpeg</b> program to make the cuts if available/configured<br>";
         text += "in kmttg and VideoRedo not available/configured.<br>";
         text += "If you have <b>VideoRedo</b> enabled then this step uses VideoRedo for making<br>";
         text += "the cuts which is a better solution than ffmpeg for preserving proper audio/video sync.";
      }
      else if (component.equals("captions")) {
         text =  "<b>captions</b><br>";
         text += "Generates a <b>.srt</b> captions file which is a text file containing<br>";
         text += "closed captioning text. This file can be used with several<br>";
         text += "video playback tools to display closed captions during playback.<br>";
         text += "Also for example <b>streambaby</b> can use this file.";
      }
      else if (component.equals("encode")) {
         text =  "<b>encode</b><br>";
         text += "Encode mpeg2 video file to a different video format.<br>";
         text += "Select video format desired using <b>Encoding Profile</b>.<br>";
         text += "Useful to create videos compatible with portable devices or<br>";
         text += "to reduce file sizes.";
      }
      else if (component.equals("push")) {
         text =  "<b>push</b><br>";
         text += "Contact pyTivo server to initiate a push of a video file to a TiVo.<br>";
         text += "pyTivo server must be running and the file to be pushed should<br>";
         text += "reside in a defined pyTivo share directory. In order for this task<br>";
         text += "to be available you must define path to pyTivo.conf file in kmttg<br>";
         text += "configuration. The TiVo you want to push to is also defined there.";
      }
      else if (component.equals("custom")) {
         text =  "<b>custom</b><br>";
         text += "Run a custom script/program that you define in kmttg configuration.<br>";
         text += "This task is always the last task to run in set of tasks<br>";
         text += "and is useful for post-processing purposes.";
      }
      else if (component.equals("encoding")) {
         text =  "<b>Encoding Profile</b><br>";
         text += "Choose one of the pre-defined encoding profiles to<br>";
         text += "use when running <b>encode</b> step to encode to a<br>";
         text += "different video format. By convention there are 2 different<br>";
         text += "prefix names used for encoding profiles by kmttg:<br>";
         text += "<b>ff_</b> indicates <b>ffmpeg</b> encoding tool is used.<br>";
         text += "<b>hb_</b> indicates <b>handbrake</b> encoding tool is used.<br>";
         text += "NOTE: You can create your own custom encoding profiles.";
      }
      else if (component.equals("encoding2")) {
          text =  "<b>2nd Encoding Profile</b><br>";
          text += "This will let you select a second encoding profile to use<br>";
          text += "that will create a second file.<br>";
          text += "Choose one of the pre-defined encoding profiles to<br>";
          text += "use when running <b>encode</b> step to encode to a<br>";
          text += "different video format. By convention there are 2 different<br>";
          text += "prefix names used for encoding profiles by kmttg:<br>";
          text += "<b>ff_</b> indicates <b>ffmpeg</b> encoding tool is used.<br>";
          text += "<b>hb_</b> indicates <b>handbrake</b> encoding tool is used.<br>";
          text += "NOTE: You can create your own custom encoding profiles.";
       }
      else if (component.equals("encoding2_suffix")) {
          text =  "<b>Second Encoding Suffix</b><br>";
          text += "This will add a suffix to your second encoding file to <br>";
          text += "differentiate it from the first encoding. It will only be used<br>";
          text += "if you select an encoding profile for a second encoding.<br>";
          text += "If you enter 'iPhone' here then your second encoding will turn<br>";
          text += "out being named 'filename_iPhone.ext'";
       }
      else if (component.equals("start")) {
         text =  "<b>START JOBS</b><br>";
         text += "Run selected tasks for all selected items in the programs/files table below.<br>";
         text += "First select 1 or more items in the list below to process.<br>";
         text += "NOTE: You can press <b>s</b> on keyboard when focus is in NPL table to activate this button";
      }
      else if (component.equals("cancel")) {
         text =  "<b>CANCEL JOBS</b><br>";
         text += "Cancel selected jobs in <b>JOB MONITOR</b> table below.<br>";
         text += "First select 1 or more running or queued jobs in list below to abort/cancel.<br>";
         text += "NOTE: You can press <b>c</b> on keyboard when focus is in JOBS table to activate this button";
      }
      else if (component.equals("JobMonitor")) {
         text =  "<b>JOB</b><br>";
         text += "Double click on a running job to see program output.";
      }
      else if (component.equals("disk_usage")) {
         text =  "<b>Disk Usage</b><br>";
         text += "Display disk usage statistics and channel bit rate information for this TiVo";
      }
      else if (component.equals("total_disk_space")) {
         text =  "<b>Total Disk Space (GB)</b><br>";
         text += "Enter total disk space capacity in GB for this TiVo and then press <b>Enter</b><br>";
         text += "to update this window and save the value.";
      }
      else if (component.equals("export_npl")) {
         text =  "<b>Export</b><br>";
         text += "Export NPL entries to a csv file which can be easily imported into an Excel<br>";
         text += "spreadsheet or equivalent.<br>";
         text += "NOTE: The list is exported as displayed, so if you want all individual entries<br>";
         text += "in the spreadsheet then disable <b>Show Folders</b> before exporting.";
      }
      else if (component.equals("prune_skipTable")) {
         text =  "<b>Prune skipTable</b><br>";
         text += "Remove entries in AutoSkip table that are deleted from this TiVo.<br>";
         text += "Instead of manually having to prune AutoSkip table this is useful to automatically<br>";
         text += "remove entries that are no longer useful.";
      }
      else if (component.equals("import_skip")) {
         text =  "<b>Import skip</b><br>";
         text += "For selected entries in the table import skip information from comskip or VideoRedo project<br>";
         text += "files into skip table. Files are attempted to be located automatically based on the current<br>";
         text += "naming template and the defined locations for <b>.TiVo Output Dir</b> and/or <b>.mpg Output Dir</b>.<br>";
         text += "If no file is located automatically then you are prompted to provide one.";
      }

      return MyTooltip.make(text);
   }

   // Abstraction methods
   public void setTitle(final String s) {
      debug.print("s=" + s);
      SwingUtil.runLater(new Runnable() {
         @Override public void run() {
            jFrame.setTitle(s);
         }
      });
   }
   public void text_print(String s) {
      debug.print("s=" + s);
      textp.print(s);
   }
   public void text_warn(String s) {
      debug.print("s=" + s);
      textp.warn(s);
   }
   public void text_error(String s) {
      debug.print("s=" + s);
      textp.error(s);
   }
   public void text_print(Stack<String> s) {
      debug.print("s=" + s);
      textp.print(s);
   }
   public void text_warn(Stack<String> s) {
      debug.print("s=" + s);
      textp.warn(s);
   }
   public void text_error(Stack<String> s) {
      debug.print("s=" + s);
      textp.error(s);
   }
   public void jobTab_packColumns(int pad) {
      debug.print("pad=" + pad);
      if (jobTab != null && jobTab.JobMonitor != null)
         TableUtil.autoSizeTableViewColumns(jobTab.JobMonitor, true);
   }
   public jobData jobTab_GetRowData(int row) {
      debug.print("row=" + row);
      return jobTab.GetRowData(row);
   }
   public void jobTab_UpdateJobMonitorRowStatus(final jobData job, final String status) {
      debug.print("job=" + job + " status=" + status);
      SwingUtil.runLater(new Runnable() {
         @Override public void run() {
            jobTab.UpdateJobMonitorRowStatus(job, status);
         }
      });
   }
   public void jobTab_UpdateJobMonitorRowOutput(final jobData job, final String status) {
      debug.print("job=" + job + " status=" + status);
      SwingUtil.runLater(new Runnable() {
         @Override public void run() {
            jobTab.UpdateJobMonitorRowOutput(job, status);
         }
      });
   }
   public void jobTab_AddJobMonitorRow(final jobData job, final String source, final String output) {
      debug.print("job=" + job + " source=" + source + " output=" + output);
      SwingUtil.runLater(new Runnable() {
         @Override public void run() {
            jobTab.AddJobMonitorRow(job, source, output);
         }
      });
   }
   public void jobTab_RemoveJobMonitorRow(final jobData job) {
      debug.print("job=" + job);
      SwingUtil.runLater(new Runnable() {
         @Override public void run() {
            jobTab.RemoveJobMonitorRow(job);
         }
      });
   }
   public void progressBar_setValue(final int value) {
      debug.print("value=" + value);
      SwingUtil.runLater(new Runnable() {
         @Override public void run() {
            progressBar.setValue(value);
         }
      });
   }
   public void refresh() {
      debug.print("");
      jContentPane.revalidate();
      jContentPane.repaint();
   }
   public void nplTab_SetNowPlaying(String tivoName, Stack<Hashtable<String,String>> entries) {
      debug.print("tivoName=" + tivoName + " entries=" + entries);
      if (tivoTabs.containsKey(tivoName)) {
         tivoTabs.get(tivoName).nplTab_SetNowPlaying(entries);
      }
   }
   public void nplTab_clear(String tivoName) {
      debug.print("tivoName=" + tivoName);
      if (tivoTabs.containsKey(tivoName)) {
         tivoTabs.get(tivoName).nplTab_clear();
      }
   }
   public void nplTab_UpdateStatus(String tivoName, String status) {
      debug.print("tivoName=" + tivoName + " status=" + status);
      if (tivoTabs.containsKey(tivoName)) {
         tivoTabs.get(tivoName).nplTab_UpdateStatus(status);
      }
   }

   // Returns state of checkbox options (as int for writing to auto.ini purposes)
   public int TSdownload_setting() {
      debug.print("");
      int selected = 0;
      if (TSdownload.isSelected()) selected = 1;
      return selected;
   }
   public int metadata_setting() {
      debug.print("");
      int selected = 0;
      if (metadata.isSelected()) selected = 1;
      return selected;
   }
   public int decrypt_setting() {
      debug.print("");
      int selected = 0;
      if (decrypt.isSelected()) selected = 1;
      return selected;
   }
   public int qsfix_setting() {
      debug.print("");
      int selected = 0;
      if (qsfix.isSelected()) selected = 1;
      return selected;
   }
   public int twpdelete_setting() {
      debug.print("");
      int selected = 0;
      if (twpdelete.isSelected()) selected = 1;
      return selected;
   }
   public int rpcdelete_setting() {
      debug.print("");
      int selected = 0;
      if (rpcdelete.isSelected()) selected = 1;
      return selected;
   }
   public int comskip_setting() {
      debug.print("");
      int selected = 0;
      if (comskip.isSelected()) selected = 1;
      return selected;
   }
   public int comcut_setting() {
      debug.print("");
      int selected = 0;
      if (comcut.isSelected()) selected = 1;
      return selected;
   }
   public int captions_setting() {
      debug.print("");
      int selected = 0;
      if (captions.isSelected()) selected = 1;
      return selected;
   }
   public int encode_setting() {
      debug.print("");
      int selected = 0;
      if (encode.isSelected()) selected = 1;
      return selected;
   }
   public int custom_setting() {
      debug.print("");
      int selected = 0;
      if (custom.isSelected()) selected = 1;
      return selected;
   }

   // Identify NPL table items associated with queued/running jobs
   public void updateNPLjobStatus(Hashtable<String,String> map) {
      debug.print("map=" + map);
      Stack<String> tivoNames = config.getNplTivoNames();
      if (tivoNames.size() > 0) {
         for (int i=0; i<tivoNames.size(); i++) {
            tivoTab t = getTab(tivoNames.get(i));
            if (t != null) {
               nplTable npl = t.getTable();
               npl.updateNPLjobStatus(map);
            }
         }
      }
   }

   public String getWebColor(java.awt.Color color) {
      debug.print("color=" + color);
      return SwingUtil.webColor(color);
   }


}
