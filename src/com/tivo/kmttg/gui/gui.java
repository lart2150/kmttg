package com.tivo.kmttg.gui;

import java.awt.*;
import java.awt.event.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Stack;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.FontUIResource;

import com.tivo.kmttg.main.auto;
import com.tivo.kmttg.main.autoConfig;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.encodeConfig;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class gui {

   private configAuto config_auto = null;
   private String title = config.kmttg;
   private JFrame jFrame = null;
   private JSplitPane jContentPane = null;
   private JSplitPane splitBottom = null;
   private JTabbedPane tabbed_panel = null;
   private JMenuBar jJMenuBar = null;
   private JMenu fileMenu = null;
   private JMenu jobMenu = null;
   private JMenu autoMenu = null;
   private JMenu serviceMenu = null;
   private JMenu helpMenu = null;
   private JMenuItem helpAboutMenuItem = null;
   private JMenuItem exitMenuItem = null;
   private JMenuItem autoConfigMenuItem = null;
   private JMenuItem runInGuiMenuItem = null;
   private JCheckBoxMenuItem loopInGuiMenuItem = null;
   private JCheckBoxMenuItem toggleLaunchingJobsMenuItem = null;
   private JMenuItem addSelectedTitlesMenuItem = null;
   private JMenuItem addSelectedHistoryMenuItem = null;
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
   private JMenuItem resetServerMenuItem = null;
   private JMenuItem saveJobsMenuItem = null;
   private JMenuItem loadJobsMenuItem = null;
   
   private JComboBox encoding = null;
   private JLabel encoding_label = null;
   private JLabel encoding_description_label = null;
   public JCheckBox metadata = null;
   public JCheckBox decrypt = null;
   public JCheckBox qsfix = null;
   public JCheckBox twpdelete = null;
   public JCheckBox ipaddelete = null;
   public JCheckBox comskip = null;
   public JCheckBox comcut = null;
   public JCheckBox captions = null;
   public JCheckBox encode = null;
   public JCheckBox push = null;
   public JCheckBox custom = null;
   private JTextPane text = null;
   private jobTable jobTab = null;
   private textpane textp = null;
   private JProgressBar progressBar = null;
   public  JScrollPane jobPane = null;
   private ToolTipManager toolTips = null;
   
   private Hashtable<String,tivoTab> tivoTabs = new Hashtable<String,tivoTab>();
   private Hashtable<String,String> looksMap = new Hashtable<String,String>();
   public static Hashtable<String,Icon> Images;
   
   public remotegui remote_gui = null;
   
   public tivoTab getTab(String tabName) {
      return tivoTabs.get(tabName);
   }
   
   public JFrame getJFrame() {
      debug.print("");
      if (jFrame == null) {
         jFrame = new JFrame();
         jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
         jFrame.setJMenuBar(getJJMenuBar());
         jFrame.setContentPane(getJContentPane());
         
         // Add additional ipad remote tab if at least 1 TiVo is configured for it
         if (config.ipadEnabled()) {
            remote_gui = new remotegui(jFrame);
            tabbed_panel.add("Remote", remote_gui.getPanel());
         }
         
         //jFrame.setMinimumSize(new Dimension(700,600));
         //jFrame.setPreferredSize(jFrame.getMinimumSize());
         setFontSize(config.FontSize);
         jFrame.pack();
         jFrame.setTitle(title);
         jobTab_packColumns(5);
         
         // Restore last GUI run settings from file
         readSettings();
         
         // Enable/disable options according to configuration
         refreshOptions();
         
         // Create and enable/disable component tooltips
         toolTips = ToolTipManager.sharedInstance();
         toolTips.setDismissDelay(config.toolTipsTimeout*1000);
         toolTips.setInitialDelay(500);
         setToolTips();
         enableToolTips(config.toolTips);
         
         // Set master flag indicating that kmttg is running in GUI mode
         config.GUIMODE = true;
         
         // Create NowPlaying icons
         CreateImages();
         
         // Start NPL jobs
         if (config.npl_when_started == 1)
            initialNPL(config.TIVOS);
      }
      return jFrame;
   }

   @SuppressWarnings("unchecked")
   public void setFontSize(int fontSize) {
      Enumeration keys = UIManager.getDefaults().keys();
      while (keys.hasMoreElements()) {
         Object key = keys.nextElement();
         Object value = UIManager.get(key);
         if (value != null && value instanceof FontUIResource) {
            UIManager.put(key, null);
            Font font = UIManager.getFont(key);
            if (font != null) {
               UIManager.put(key, new FontUIResource(font.getFamily(), font.getStyle(), fontSize));
            }
         }
      }
      config.tableFont = new Font("System", Font.BOLD, fontSize);
      SwingUtilities.updateComponentTreeUI(jFrame);
      
      // Update config dialog fonts if created
      JDialog d = configMain.getDialog();
      if (d != null) {
         SwingUtilities.updateComponentTreeUI(d);
         d.pack();
      }
      
      // Update auto config dialog fonts if created
      d = configAuto.getDialog();
      if (d != null) {
         SwingUtilities.updateComponentTreeUI(d);
         d.pack();
      }
   }
   
   public void setLookAndFeel(String name) {
      if (name == null)
         name = "default";
      config.lookAndFeel = name;
      if (looksMap.size() == 0)
         getAvailableLooks();
      if (name.equals("default"))
         name = UIManager.getCrossPlatformLookAndFeelClassName();
      else
         name = looksMap.get(name);
      try {
         JDialog d = configMain.getDialog();
         JDialog a = configAuto.getDialog();
         UIManager.setLookAndFeel(name);
         SwingUtilities.updateComponentTreeUI(jFrame);
         if (d != null) {
            SwingUtilities.updateComponentTreeUI(d);
            d.pack();
         }
         if (a != null) {
            SwingUtilities.updateComponentTreeUI(a);
            a.pack();
         }
      } catch (Exception e) {
         log.error("setLookAndFeel - " + e.getMessage());
      }
   }
   
   public String[] getAvailableLooks() {
      UIManager.LookAndFeelInfo[] info = UIManager.getInstalledLookAndFeels();
      String[] looks = new String [info.length+1];
      looks[0] = "default";
      int k = 1;
      for (int i=0; i<info.length; i++) {
          looks[k++] = info[i].getName();
          looksMap.put(info[i].getName(), info[i].getClassName());
      }
      return looks;
   }
   
   public void grabFocus() {
      if (jFrame != null)
         if(! jFrame.hasFocus()) { jFrame.requestFocus(); }
   }
   
   private Container getJContentPane() {
      debug.print("");
      if (jContentPane == null) {
         
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

         int gy=0;
         
         // Cancel jobs button
         JButton cancel = new JButton("CANCEL JOBS");
         cancel.setMargin(new Insets(0,1,0,1));
         cancel.setToolTipText(getToolTip("cancel"));
         cancel.setBackground(config.lightRed);
         cancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
               cancelCB();
            }
         });
         // START JOBS button
         JButton start = new JButton("START JOBS");
         start.setMargin(new Insets(0,1,0,1));
         start.setToolTipText(getToolTip("start"));
         start.setBackground(Color.green);
         start.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
               String tivoName = getCurrentTabName();
               if (tivoName.equals("Remote"))
                  log.error("START JOBS invalid with Remote tab selected.");
               else
                  tivoTabs.get(tivoName).startCB();
            }
         });
         // Tasks
         metadata = new JCheckBox("metadata", false);         
         decrypt = new JCheckBox("decrypt", true);         
         qsfix = new JCheckBox("VRD QS fix", false);         
         twpdelete = new JCheckBox("TWP Delete", false);         
         ipaddelete = new JCheckBox("iPad Delete", false);         
         comskip = new JCheckBox("Ad Detect", false);         
         comcut = new JCheckBox("Ad Cut", false);         
         captions = new JCheckBox("captions", false);         
         encode = new JCheckBox("encode", false);
         push = new JCheckBox("push", false);
         /* This intentionally disabled for now
         encode.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               boolean selected = encode.isSelected();
               if (! file.isDir(config.VRD)) {
                  if (selected) {
                     if (config.OS.equals("windows") && file.isFile(config.mencoder)) {
                        qsfix.setEnabled(true);
                        qsfix.setSelected(true);
                     }
                  } else {
                     qsfix.setEnabled(false);
                     qsfix.setSelected(false);
                  }
               }
            }
         });
         */
         custom = new JCheckBox("custom", false);
         
         // Tasks row
         JPanel tasks_panel = new JPanel();
         tasks_panel.setLayout(new BoxLayout(tasks_panel, BoxLayout.X_AXIS));
         Dimension space_5 = new Dimension(5,0);
         tasks_panel.add(start);
         tasks_panel.add(Box.createRigidArea(space_5));
         tasks_panel.add(metadata);
         tasks_panel.add(Box.createRigidArea(space_5));
         tasks_panel.add(decrypt);
         tasks_panel.add(Box.createRigidArea(space_5));
         tasks_panel.add(qsfix);
         if (config.TivoWebPlusDelete == 1) {
            tasks_panel.add(Box.createRigidArea(space_5));
            tasks_panel.add(twpdelete);            
         }
         if (config.ipadDeleteEnabled()) {
            tasks_panel.add(Box.createRigidArea(space_5));
            tasks_panel.add(ipaddelete);            
         }
         tasks_panel.add(Box.createRigidArea(space_5));
         tasks_panel.add(comskip);
         tasks_panel.add(Box.createRigidArea(space_5));
         tasks_panel.add(comcut);
         tasks_panel.add(Box.createRigidArea(space_5));
         tasks_panel.add(captions);
         tasks_panel.add(Box.createRigidArea(space_5));
         tasks_panel.add(encode);
         tasks_panel.add(Box.createRigidArea(space_5));
         tasks_panel.add(push);
         tasks_panel.add(Box.createRigidArea(space_5));
         tasks_panel.add(custom);
         
         // Encoding row
         // Encoding label
         encoding_label = new JLabel("Encoding Profile:", JLabel.CENTER);
 
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

         // Encoding description label
         String description = "";
         if (encodeConfig.getValidEncodeNames().size() > 0) {
            description = "  " + encodeConfig.getDescription(encodeConfig.getEncodeName());
         }
         encoding_description_label = new JLabel(description);

         JPanel encoding_panel = new JPanel();
         encoding_panel.setLayout(new BoxLayout(encoding_panel, BoxLayout.X_AXIS));
         encoding_panel.add(encoding_label);
         encoding_panel.add(Box.createRigidArea(space_5));
         encoding_panel.add(encoding);
         encoding_panel.add(Box.createRigidArea(space_5));
         encoding_panel.add(encoding_description_label);
         
         // Job Monitor table
         jobTab = new jobTable();
         jobPane = new JScrollPane(jobTab.JobMonitor);
         
         // Progress Bar
         progressBar = new JProgressBar();
         progressBar.setBackground(Color.gray);
         progressBar.setForeground(Color.green);

         // Message area
         text = new JTextPane();
         textp = new textpane(text);
         text.setEditable(false);
         JScrollPane messagePane = new JScrollPane(text);
                  
         // Tabbed panel
         tabbed_panel = new JTabbedPane();
         tabbed_panel.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
               if (getCurrentTabName().equals("Remote")) {
                  // Set focus on remote pane
                  remote_gui.tabbed_panel.requestFocusInWindow();
               }
            }
        });


         // Add permanent tabs
         tivoTabs.put("FILES", new tivoTab("FILES"));
         tabbed_panel.add("FILES", tivoTabs.get("FILES").getPanel());
         
         // Add Tivo tabs
         SetTivos(config.TIVOS);
         
         // Cancel pane
         JPanel cancel_pane = new JPanel(new GridBagLayout());
         gy=0;
         c.gridx = 0;
         c.gridy = gy;
         c.weightx = 0;
         c.gridwidth = 1;
         c.anchor = GridBagConstraints.WEST;
         c.fill = GridBagConstraints.NONE;
         cancel_pane.add(cancel, c);
         
         c.gridx = 1;
         c.gridy = gy;
         c.gridwidth = 7;
         c.weightx = 1;
         c.weighty = 0;
         c.anchor = GridBagConstraints.CENTER;
         c.fill = GridBagConstraints.HORIZONTAL;
         cancel_pane.add(progressBar, c);
         
         // Create a split pane between job & messages pane
         splitBottom = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT, jobPane, messagePane
         );
         splitBottom.setDividerLocation(130);
         
         // bottomPane will consist of cancel_pane & splitBottom
         JPanel bottomPane = new JPanel(new GridBagLayout());
         
         gy=0;
         c.gridx = 0;
         c.gridy = gy;
         c.gridwidth = 8;
         c.weighty = 0;
         c.fill = GridBagConstraints.HORIZONTAL;
         c.anchor = GridBagConstraints.NORTH;
         bottomPane.add(cancel_pane, c);
         
         gy++;
         c.weightx = 1.0;    // stretch horizontally
         c.weighty = 1.0;      // stretch vertically
         c.gridwidth = 8;
         c.gridx = 0;
         c.gridy = gy;
         c.fill = GridBagConstraints.BOTH;
         c.anchor = GridBagConstraints.SOUTH;
         bottomPane.add(splitBottom, c);
         
         // topPane will consist of tasks & tabbed_panel
         JPanel topPane = new JPanel(new GridBagLayout());
         
         // Common settings for topPane
         c.gridwidth = 8;
         c.weightx = 1;
         c.weighty = 0;
         
         gy=0;
         c.gridx = 0;
         c.gridy = gy;
         c.fill = GridBagConstraints.HORIZONTAL;
         c.weighty = 0;
         topPane.add(tasks_panel, c);
         gy++;
         c.gridy = gy;
         c.fill = GridBagConstraints.NONE;
         c.anchor = GridBagConstraints.WEST;
         topPane.add(encoding_panel, c);
         
         gy++;
         c.gridx = 0;
         c.gridy = gy;
         c.weighty = 1.0;
         c.gridwidth = 1;
         c.fill = GridBagConstraints.BOTH;
         c.anchor = GridBagConstraints.NORTH;
         topPane.add(tabbed_panel, c);
         
         // Put all panels together
         jContentPane = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT, topPane, bottomPane
         );
         jContentPane.setDividerLocation(330);

         // Pack table columns when content pane resized
         jContentPane.addHierarchyBoundsListener(new HierarchyBoundsListener() {
            public void ancestorMoved(HierarchyEvent arg0) {
               // Don't care about movement
            }
            public void ancestorResized(HierarchyEvent arg0) {
               jobTab.packColumns(jobTab.JobMonitor, 2);
            }
         });
      }
      
      return jContentPane;
   }
   
   private JMenuBar getJJMenuBar() {
      debug.print("");
      if (jJMenuBar == null) {
         jJMenuBar = new JMenuBar();
         jJMenuBar.add(getFileMenu());
         jJMenuBar.add(getAutoTransfersMenu());
         // This causes remaining menus to be right justified
         jJMenuBar.add(Box.createGlue());
         jJMenuBar.add(getHelpMenu());
      }
      return jJMenuBar;
   }

   private JMenu getFileMenu() {
      debug.print("");
      if (fileMenu == null) {
         fileMenu = new JMenu();
         fileMenu.setText("File");
         fileMenu.add(getConfigureMenuItem());
         fileMenu.add(getRefreshEncodingsMenuItem());
         fileMenu.add(getSaveMessagesMenuItem());
         fileMenu.add(getClearMessagesMenuItem());
         fileMenu.add(getResetServerMenuItem());
         fileMenu.add(getJobMenu());
         fileMenu.add(getExitMenuItem());
      }
      return fileMenu;
   }
   
   private JMenu getJobMenu() {
      if (jobMenu == null) {
         jobMenu = new JMenu();
         jobMenu.setText("Jobs");
         jobMenu.add(getToggleLaunchingJobsMenuItem());
         jobMenu.add(getSaveJobsMenuItem());
         jobMenu.add(getLoadJobsMenuItem());
      }
      return jobMenu;
   }

   private JMenu getAutoTransfersMenu() {
      debug.print("");
      if (autoMenu == null) {
         autoMenu = new JMenu();
         autoMenu.setText("Auto Transfers");
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
         helpMenu = new JMenu();
         helpMenu.setText("Help");
         helpMenu.add(getHelpAboutMenuItem());
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

   private JMenuItem getSaveMessagesMenuItem() {
      debug.print("");
      if (saveMessagesMenuItem == null) {
         saveMessagesMenuItem = new JMenuItem();
         saveMessagesMenuItem.setText("Save messages to file");
         saveMessagesMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,
               Event.CTRL_MASK, true));
         saveMessagesMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               String file = config.programDir + File.separator + "kmttg.log";
               try {
                  BufferedWriter ofp = new BufferedWriter(new FileWriter(file));
                  ofp.write(text.getText());
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
         clearMessagesMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               text.setEditable(true);
               text.setText("");
               text.setEditable(false);
            }
         });
      }
      return clearMessagesMenuItem;
   }

   private JMenuItem getResetServerMenuItem() {
      debug.print("");
      if (resetServerMenuItem == null) {
         resetServerMenuItem = new JMenuItem();
         resetServerMenuItem.setText("Reset TiVo web server");
         resetServerMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R,
               Event.CTRL_MASK, true));
         resetServerMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               String tivoName = getSelectedTivoName();
               if (tivoName != null) {
                  String urlString = "http://" + config.TIVOS.get(tivoName) + "/TiVoConnect?Command=ResetServer";
                  // Add wan port if configured
                  String wan_port = config.getWanSetting(tivoName, "http");
                  if (wan_port != null)
                     urlString = string.addPort(urlString, wan_port);
                  try {
                     URL url = new URL(urlString);
                     log.warn("Resetting " + tivoName + " TiVo: " + urlString);
                     url.openConnection();
                  }
                  catch(Exception ex) {
                     log.error(ex.toString());
                  }
               } else {
                  log.error("This command must be run with a TiVo tab selected.");
               }
            }
         });
      }
      return resetServerMenuItem;
   }
   
   private JMenuItem getToggleLaunchingJobsMenuItem() {
      debug.print("");
      if (toggleLaunchingJobsMenuItem == null) {
         toggleLaunchingJobsMenuItem = new JCheckBoxMenuItem();
         toggleLaunchingJobsMenuItem.setText("Do not launch queued jobs");
         toggleLaunchingJobsMenuItem.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
               AbstractButton button = (AbstractButton) e.getItem();
               Boolean enabled = button.isSelected();
               if (enabled) {
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
               AbstractButton button = (AbstractButton) e.getItem();
               autoLoopInGUICB(button.isSelected());
            }
         });
      }
      return loopInGuiMenuItem;
   }
   
   private JMenuItem getAddSelectedTitlesMenuItem() {
      debug.print("");
      if (addSelectedTitlesMenuItem == null) {
         addSelectedTitlesMenuItem = new JMenuItem();
         addSelectedTitlesMenuItem.setText("Add selected titles");
         addSelectedTitlesMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               String tivoName = getSelectedTivoName();
               if (tivoName != null) {
                  tivoTabs.get(tivoName).autoSelectedTitlesCB();
               } else {
                  log.error("This command must be run from a TiVo tab with selected tivo shows.");
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
         configureMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O,
               Event.CTRL_MASK, true));
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
      if (! file.isFile(config.curl) && config.java_downloads == 0) {
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
      /* This intentionally disabled for now
      if (! file.isDir(config.VRD)) {
         if (config.OS.equals("windows") && file.isFile(config.mencoder) && encode.isSelected()) {
            qsfix.setEnabled(true);
            qsfix.setSelected(true);
         } else {
            qsfix.setSelected(false);
            qsfix.setEnabled(false);
         }
      } else {
         qsfix.setEnabled(true);
      }
      */
      if (! file.isDir(config.VRD)) {
         qsfix.setSelected(false);
         qsfix.setEnabled(false);
      } else {
         qsfix.setEnabled(true);
      }
      
      if (config.TivoWebPlusDelete == 0) {
         twpdelete.setSelected(false);
         twpdelete.setEnabled(false);
      } else {
         twpdelete.setEnabled(true);
      }
      
      if ( ! config.ipadDeleteEnabled() ) {
         ipaddelete.setSelected(false);
         ipaddelete.setEnabled(false);
      } else {
         ipaddelete.setEnabled(true);
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
      
      if ( ! file.isFile(config.pyTivo_config) ) {
         push.setSelected(false);
         push.setEnabled(false);
      } else {
         push.setEnabled(true);
      }
      
      if ( ! com.tivo.kmttg.task.custom.customCommandExists() ) {
         custom.setSelected(false);
         custom.setEnabled(false);
      } else {
         custom.setEnabled(true);
      }
      
      // Refresh encoding profiles in case toggled between VRD & regular
      if (config.GUIMODE) refreshEncodingProfilesCB();
      
      // Add remote tab if appropriate
      if (config.GUIMODE && config.ipadEnabled() && remote_gui == null) {
         remote_gui = new remotegui(jFrame);
         tabbed_panel.add("Remote", remote_gui.getPanel());
      }
   }
   
   // Callback for "Refresh Encoding Profiles" File menu entry
   // This will re-parse encoding files and reset Encoding Profile list in GUI
   private void refreshEncodingProfilesCB() {
      debug.print("");
      log.warn("Refreshing encoding profiles");
      encodeConfig.parseEncodingProfiles();
      //SetEncodings(encodeConfig.getValidEncodeNames());
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
      Stack<String> tivoNames = config.getTivoNames();
      if (tivoNames.size() > 0) {
         for (int i=0; i<tivoNames.size(); i++) {
            // Queue up a nowplaying list job for this tivo
            config.GUI_AUTO++;
            getTab(tivoNames.get(i)).getTable().setFolderState(false);
            jobMonitor.getNPL(tivoNames.get(i));
         }
      }
   }

   // Callback for "Loop in GUI" Auto Transfers menu entry
   // This is equivalent to auto mode run but is performed in GUI
   public void autoLoopInGUICB(Boolean enabled) {
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
            int response = JOptionPane.showConfirmDialog(
               config.gui.getJFrame(),
               question,
               "Confirm",
               JOptionPane.YES_NO_OPTION,
               JOptionPane.QUESTION_MESSAGE
            );
            if (response == JOptionPane.YES_OPTION) {
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
   private void encodingCB(JComboBox combo) {
      debug.print("combo=" + combo);
      String encodeName = (String)combo.getSelectedItem();
      config.encodeName = encodeName;
      String description = encodeConfig.getDescription(encodeName);
      // Set encoding_description_label accordingly
      encoding_description_label.setText("  " + description);
   }
 
   // Cancel button callback
   // Kill and remove selected jobs from job monitor
   private void cancelCB() {
      debug.print("");
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

   // Create tivo tabs as needed
   public void SetTivos(Hashtable<String,String> values) {
      debug.print("values=" + values);
      if ( values.size() > 1 ) {
         String[] names = new String[values.size()-1];
         int i = 0;
         String value;
         
         for (Enumeration<String> e=values.keys(); e.hasMoreElements();) {
            value = e.nextElement();
            if (! value.equals("FILES") && ! value.equals("Remote")) {
               names[i] = value;
               i++;
            }
         }
         
         // Remove unwanted tabs
         tivoTabRemoveExtra(names);
         
         // Add tabs
         for (int j=0; j<names.length; j++) {
            tivoTabAdd(names[j]);
         }
         
         // remote gui
         if (remote_gui != null)
            remote_gui.setTivoNames();

      } else {
         // Remove all tivo tabs
         while(! tabbed_panel.getTitleAt(0).equals("FILES") && ! tabbed_panel.getTitleAt(0).equals("Remote")) {
            tivoTabRemove(tabbed_panel.getTitleAt(0));
         }
      }
   }

   // Start NPL jobs for 1st time
   public void initialNPL(Hashtable<String,String> values) {
      String value;         
      for (Enumeration<String> e=values.keys(); e.hasMoreElements();) {
         value = e.nextElement();
         if (! value.equals("FILES") && ! value.equals("Remote")) {
            jobMonitor.getNPL(value);
         }
      }
   }
   
   private String getCurrentTabName() {
      return tabbed_panel.getTitleAt(tabbed_panel.getSelectedIndex());
   }
   
   public String getSelectedTivoName() {
      String tabName = getCurrentTabName();
      if (! tabName.equals("FILES") && ! tabName.equals("Remote")) {
         return tabName;
      }
      return null;
   }
   
   // Check name against existing tabbed panel names
   private Boolean tivoTabExists(String name) {
      debug.print("name=" + name);
      int numTabs = tabbed_panel.getComponentCount();
      String tabName;
      for (int i=0; i<numTabs; i++) {
         tabName = tabbed_panel.getTitleAt(i);
         if (tabName.equals(name)) {
            return true;
         }
      }
      return false;
   }
   
   private void tivoTabAdd(String name) {
      debug.print("name=" + name);
      if ( ! tivoTabExists(name) ) {
         tivoTab tab = new tivoTab(name);
         tabbed_panel.add(tab.getPanel(), 0);
         tabbed_panel.setTitleAt(0, name);
         tivoTabs.put(name,tab);
      }
   }
   
   private void tivoTabRemove(String name) {
      debug.print("name=" + name);
      if (tivoTabs.containsKey(name)) {
         tabbed_panel.remove(tivoTabs.get(name).getPanel());
         tivoTabs.remove(name);
      }
   }
   
   private void tivoTabRemoveExtra(String[] names) {
      debug.print("names=" + names);
      int numTabs = tabbed_panel.getComponentCount();
      if (numTabs > 0 && names.length > 0) {
         // Determine tabs we no longer want
         Stack<String> unwanted = new Stack<String>();
         String tabName;
         Boolean remove;
         for (int i=0; i<numTabs; i++) {
            tabName = tabbed_panel.getTitleAt(i);
            if (! tabName.equals("FILES") && ! tabName.equals("Remote")) {
               remove = true;
               for (int j=0; j<names.length; j++) {
                  if (names[j].equals(tabName)) {
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
      for (int i=0; i<tabbed_panel.getComponentCount(); ++i) {
         if (tabbed_panel.getTitleAt(i).equals(tivoName)) {
            tabbed_panel.setSelectedIndex(i);
         }
      }
   }
   
   // Add a tivo
   public void AddTivo(String name, String ip) {
      tivoTabAdd(name);
      configMain.addTivo(name, ip);
   }
   
   // Set encoding combobox choices
   public void SetEncodings(Stack<String> values) {
      debug.print("values=" + values);
      
      if (encoding != null) {      
         // Get existing setting in combobox
         String current = null;
         if (encoding.getComponentCount() > 0) {
            current = (String)encoding.getSelectedItem();
         }
         String[] names = new String[values.size()];
         for (int i=0; i<values.size(); ++i) {
            names[i] = values.get(i);
         }
         combobox.SetValues(encoding, names);
         if (current != null)
            encoding.setSelectedItem(current);
      }
   }
   
   public String GetSelectedEncoding() {
      String selected = null;
      if (encoding.getComponentCount() > 0) {
         selected = (String)encoding.getSelectedItem();
      }
      return selected;
   }
   
   public void SetSelectedEncoding(String name) {
      if (encoding.getComponentCount() > 0) {
         encoding.setSelectedItem(name);
      }
   }
   
   private void CreateImages() {
      debug.print("");
      Images = new Hashtable<String,Icon>();
      String[] names = {
         "expires-soon-recording", "save-until-i-delete-recording",
         "in-progress-recording", "in-progress-transfer",
         "expired-recording", "suggestion-recording", "folder",
         "copy-protected", "running", "queued"
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
   
   
   // Save current GUI settings to a file
   public void saveSettings() {
      if (config.gui_settings != null) {
         try {
            Dimension d = getJFrame().getSize();
            Point p = getJFrame().getLocation();
            int centerDivider = jContentPane.getDividerLocation();
            int bottomDivider = splitBottom.getDividerLocation();
            String tabName = tabbed_panel.getTitleAt(tabbed_panel.getSelectedIndex());
            BufferedWriter ofp = new BufferedWriter(new FileWriter(config.gui_settings));            
            ofp.write("# kmttg gui preferences file\n");
            ofp.write("<metadata>\n"            + metadata_setting()         + "\n");
            ofp.write("<decrypt>\n"             + decrypt_setting()          + "\n");
            ofp.write("<qsfix>\n"               + qsfix_setting()            + "\n");
            ofp.write("<twpdelete>\n"           + twpdelete_setting()        + "\n");
            ofp.write("<ipaddelete>\n"          + ipaddelete_setting()       + "\n");
            ofp.write("<comskip>\n"             + comskip_setting()          + "\n");
            ofp.write("<comcut>\n"              + comcut_setting()           + "\n");
            ofp.write("<captions>\n"            + captions_setting()         + "\n");
            ofp.write("<encode>\n"              + encode_setting()           + "\n");
            ofp.write("<push>\n"                + push_setting()             + "\n");
            ofp.write("<custom>\n"              + custom_setting()           + "\n");
            ofp.write("<encode_name>\n"         + config.encodeName          + "\n");
            ofp.write("<toolTips>\n"            + config.toolTips            + "\n");
            ofp.write("<toolTipsTimeout>\n"     + config.toolTipsTimeout     + "\n");
            ofp.write("<jobMonitorFullPaths>\n" + config.jobMonitorFullPaths + "\n");
            ofp.write("<width>\n"               + d.width                    + "\n");
            ofp.write("<height>\n"              + d.height                   + "\n");
            ofp.write("<x>\n"                   + p.x                        + "\n");
            ofp.write("<y>\n"                   + p.y                        + "\n");
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
               int[] widths = tivoTabs.get(name).getTable().getColWidths();
               ofp.write(name + "=" + widths[0]);
               for (int j=1; j<widths.length; ++j) {
                  ofp.write("," + widths[j]);
               }
               ofp.write("\n");
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
            
            ofp.write("\n");
            ofp.close();
         }         
         catch (IOException ex) {
            log.error("Problem writing to file: " + config.gui_settings);
         }         
      }
   }
   
   // Read initial settings from file
   public void readSettings() {
      if (! file.isFile(config.gui_settings)) return;
      try {
         int width = -1;
         int height = -1;
         int x = -1;
         int y = -1;
         int value;
         int centerDivider = -1, bottomDivider = -1;
         BufferedReader ifp = new BufferedReader(new FileReader(config.gui_settings));
         String line = null;
         String key = null;
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
            if (key.equals("ipaddelete")) {
               if (line.matches("1"))
                  ipaddelete.setSelected(true);
               else
                  ipaddelete.setSelected(false);
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
            if (key.equals("push")) {
               if (line.matches("1"))
                  push.setSelected(true);
               else
                  push.setSelected(false);
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
            if (key.equals("jobMonitorFullPaths")) {
               if (line.matches("1"))
                  config.jobMonitorFullPaths = 1;
               else
                  config.jobMonitorFullPaths = 0;
            }
            if (key.equals("encode_name")) {
               if (encodeConfig.isValidEncodeName(line)) {
                  encoding.setSelectedItem(line);
                  config.encodeName = line;
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
               if (remote_gui != null)
                  remote_gui.getPanel().setSelectedIndex(value);
            }
            if (key.equals("centerDivider")) {
               try {
                  centerDivider = Integer.parseInt(line);
               } catch (NumberFormatException e) {
                  centerDivider = -1;
               }
            }
            if (key.equals("bottomDivider")) {
               try {
                  bottomDivider = Integer.parseInt(line);
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
               if (tivoTabs.containsKey(l[0])) {
                  tivoTabs.get(name).getTable().setColWidths(widths);
               }
            }
            if (key.equals("showFolders")) {
               String[] l = line.split("=");
               if (l[1].equals("1")) {
                  if (tivoTabs.containsKey(l[0]))
                     tivoTabs.get(l[0]).showFoldersSet(true);
               }
            }
         }
         ifp.close();
         
         if (width != -1 && height != -1) {
            getJFrame().setSize(new Dimension(width,height));
         }
         
         if (x != -1 && y != -1) {
            getJFrame().setLocation(new Point(x,y));
         }
         
         if (centerDivider != -1)
            jContentPane.setDividerLocation(centerDivider);
         
         if (bottomDivider != -1)
            splitBottom.setDividerLocation(bottomDivider);
      }         
      catch (Exception ex) {
         log.warn("Problem parsing config file: " + config.gui_settings);
      }
   }
   
   // Component tooltip setup
   public void setToolTips() {
      metadata.setToolTipText(getToolTip("metadata"));
      decrypt.setToolTipText(getToolTip("decrypt"));
      qsfix.setToolTipText(getToolTip("qsfix"));
      twpdelete.setToolTipText(getToolTip("twpdelete"));
      ipaddelete.setToolTipText(getToolTip("ipaddelete"));
      comskip.setToolTipText(getToolTip("comskip"));
      comcut.setToolTipText(getToolTip("comcut"));
      captions.setToolTipText(getToolTip("captions"));
      encode.setToolTipText(getToolTip("encode"));
      push.setToolTipText(getToolTip("push"));
      custom.setToolTipText(getToolTip("custom"));
      encoding.setToolTipText(getToolTip("encoding"));
      jobTab.JobMonitor.setToolTipText(getToolTip("JobMonitor"));
   }
   
   // Enable/disable all tooltips
   public void enableToolTips(int flag) {
      if (flag == 1)
         toolTips.setEnabled(true);
      else
         toolTips.setEnabled(false);
   }
   
   public void setToolTipsTimeout(int timeout) {
      toolTips.setDismissDelay(timeout*1000);
   }
     
   public String getToolTip(String component) {
      String text = "";
      if (component.equals("tivos")) {
         text =  "<b>TIVOS</b><br>";
         text += "Select <b>FILES</b> mode or a <b>TiVo</b> on your network.<br>";
         text += "<b>FILES</b> mode allows you to select existing TiVo or mpeg2 files on your computer.<br>";
         text += "<b>TiVo</b> mode allows you to get a listing of all shows for a TiVo on your home network.";
      }
      else if (component.equals("add")) {
         text =  "<b>Add...</b><br>";
         text += "Brings up a file browser for selecting TiVo or mpeg2 video files to process.<br>";
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
      else if (component.equals("refresh")) {
         text =  "<b>Refresh List</b><br>";
         text += "Refresh Now Playing List for this TiVo.";
      }
      else if (component.equals("back")) {
         text =  "<b>Back</b><br>";
         text += "Exit folder view and return to top level Now Playing List for this TiVo.";
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
         text =  "<b>VRD QS fix</b><br>";
         text += "If you have VideoRedo available and configured in kmttg, this<br>";
         text += "runs the extremely useful <b>VideoRedo Quick Stream Fix</b> utility.<br>";
         //text += "Without VideoRedo this will run mpeg through mencoder filter.<br>";
         text += "Cleans up any potential glitches/errors in mpeg2 video files.<br>";
         text += "Highly recommended step if you have VideoRedo installed.<br>";
         text += "Very highly recommended step if you will be running encode.";
      }
      else if (component.equals("twpdelete")) {
         text =  "<b>TWP Delete</b><br>";
         text += "If you have TivoWebPlus configured on your TiVo(s) then if you enable this task<br>";
         text += "a TivoWebPlus http call to delete show on TiVo will be issued following<br>";
         text += "successful decrypt of a downloaded .TiVo file.";
      }
      else if (component.equals("ipaddelete")) {
         text =  "<b>iPad Delete</b><br>";
         text += "If you have Series 4 (Premiere) TiVo or later with Network Remote setting enabled<br>";
         text += "then if you enable this task, iPad style communications will be used to<br>";
         text += "delete show on TiVo following successful decrypt of a downloaded .TiVo file.<br>";
         text += "NOTE: You must also have <b>Enable iPad style communications with this TiVo</b> option<br>";
         text += "enabled under <b>Configuration-TiVos</b> for each Premiere that you want this to work with.";
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
         text += "NOTE: By default uses <b>mencoder</b> program to make the cuts which can.<br>";
         text += "cause audio/video sync problems in the resulting files.<br>";
         text += "If you have <b>VideoRedo</b> enabled then this step uses VideoRedo for making<br>";
         text += "the cuts which is a much better solution for preserving proper audio/video sync.";
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
      else if (component.equals("start")) {
         text =  "<b>START JOBS</b><br>";
         text += "Run selected tasks for all selected items in the programs/files table below.<br>";
         text += "First select 1 or more items in the list below to process.";
      }
      else if (component.equals("cancel")) {
         text =  "<b>CANCEL JOBS</b><br>";
         text += "Cancel selected jobs in <b>JOB MONITOR</b> table below.<br>";
         text += "First select 1 or more running or queued jobs in list below to abort/cancel.";
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
      
      if (text.length() > 0) {
         text = "<html>" + text + "</html>";
      }
      return text;
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
   public void jobTab_packColumns(int pad) {
      jobTab.packColumns(jobTab.JobMonitor, pad);
   }
   public jobData jobTab_GetRowData(int row) {
      return jobTab.GetRowData(row);
   }
   public void jobTab_UpdateJobMonitorRowStatus(jobData job, String status) {
      jobTab.UpdateJobMonitorRowStatus(job, status);
   }
   public void jobTab_UpdateJobMonitorRowOutput(jobData job, String status) {
      jobTab.UpdateJobMonitorRowOutput(job, status);
   }
   public void jobTab_AddJobMonitorRow(jobData job, String source, String output) {
      jobTab.AddJobMonitorRow(job, source, output);
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
   public void nplTab_SetNowPlaying(String tivoName, Stack<Hashtable<String,String>> entries) {
      if (tivoTabs.containsKey(tivoName)) {
         tivoTabs.get(tivoName).nplTab_SetNowPlaying(entries);
      }
   }
   public void nplTab_clear(String tivoName) {
      if (tivoTabs.containsKey(tivoName)) {
         tivoTabs.get(tivoName).nplTab_clear();
      }
   }
   public void nplTab_UpdateStatus(String tivoName, String status) {
      if (tivoTabs.containsKey(tivoName)) {
         tivoTabs.get(tivoName).nplTab_UpdateStatus(status);
      }
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
   public int twpdelete_setting() {
      int selected = 0;
      if (twpdelete.isSelected()) selected = 1;
      return selected;
   }
   public int ipaddelete_setting() {
      int selected = 0;
      if (ipaddelete.isSelected()) selected = 1;
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
   public int push_setting() {
      int selected = 0;
      if (push.isSelected()) selected = 1;
      return selected;
   }
   public int custom_setting() {
      int selected = 0;
      if (custom.isSelected()) selected = 1;
      return selected;
   }
   
   // Identify NPL table items associated with queued/running jobs
   public void updateNPLjobStatus(Hashtable<String,String> map) {
      Stack<String> tivoNames = config.getTivoNames();
      if (tivoNames.size() > 0) {
         for (int i=0; i<tivoNames.size(); i++) {
            nplTable npl = getTab(tivoNames.get(i)).getTable();
            npl.updateNPLjobStatus(map);
         }
      }
   }

}
