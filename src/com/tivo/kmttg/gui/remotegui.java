package com.tivo.kmttg.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Hashtable;
import java.util.Stack;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.main.telnet;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class remotegui {
   public JTabbedPane tabbed_panel = null;
   public int tivo_count = 0;
   
   private todoTable tab_todo = null;
   private JComboBox tivo_todo = null;
   
   private guideTable tab_guide = null;
   public  JButton refresh_guide = null;
   private JComboBox tivo_guide = null;
   private JComboBox guide_start = null;
   private JSpinner  guide_range = null;
   private int guide_hour_increment = 12; // Number of hours for date increment
   private int guide_total_range = 11;    // Number of days
   
   private spTable tab_sp = null;
   private JComboBox tivo_sp = null;
   public  spOptions spOpt = null;
   public  recordOptions recordOpt = null;
   
   private JComboBox tivo_info = null;
   JTextPane text_info = null;
   private Hashtable<String,String> tivo_info_data = new Hashtable<String,String>();
   
   private cancelledTable tab_cancel = null;
   private JComboBox tivo_cancel = null;
   public JButton refresh_cancel = null;
   public JLabel label_cancel = null;
   
   private JComboBox tivo_premiere = null;
   private premiereTable tab_premiere = null;
   private JList premiere_channels = null;
   private DefaultListModel premiere_model = new DefaultListModel();
   public Hashtable<String,JSONArray> premiere_channel_info = new Hashtable<String,JSONArray>();
   
   private JComboBox tivo_search = null;
   private searchTable tab_search = null;
   private JTextField text_search = null;
   public JButton button_search = null;
   private JSpinner max_search = null;
   public Hashtable<String,JSONArray> search_info = new Hashtable<String,JSONArray>();

   private JComboBox tivo_rc = null;
   private JComboBox hme_rc = null;
   private JTextField rc_jumpto_text = null;
   private JTextField rc_jumpahead_text = null;
   private JTextField rc_jumpback_text = null;
   private Hashtable<String, String> hme = new Hashtable<String, String>();
   private Boolean cc_state = false;
   
   private JFileChooser Browser = null;
   
   public class ClickAction extends AbstractAction {
      private static final long serialVersionUID = 1L;
      private JButton button = null;
      private Boolean isAscii;
      private String command;
      public ClickAction(JButton button) {
         this.button = button;
      }
      public ClickAction(Boolean isAscii, String command) {
         this.isAscii = isAscii;
         this.command = command;
      }
       
      public void actionPerformed(ActionEvent e) {
         // Don't execute panel event if focus is in one of the text widgets
         if (rc_jumpto_text.isFocusOwner())
            return;
         if (rc_jumpahead_text.isFocusOwner())
            return;
         if (rc_jumpback_text.isFocusOwner())
            return;
         if (button != null)
            button.doClick();
         else
            RC_keyPress(isAscii, command);
      }
   }

   remotegui(JFrame frame) {      
      Browser = new JFileChooser(config.programDir);
      Browser.setMultiSelectionEnabled(false);
      Browser.addChoosableFileFilter(new FileFilterSP());
      
      // Define content for main tabbed pane
      int gy = 0;
      GridBagConstraints c = new GridBagConstraints();
      c.ipady = 0;
      c.weighty = 0.0;  // default to no vertical stretch
      c.weightx = 0.0;  // default to no horizontal stretch
      c.gridx = 0;
      c.gridy = gy;
      c.gridwidth = 1;
      c.gridheight = 1;
      c.anchor = GridBagConstraints.CENTER;
      c.fill = GridBagConstraints.HORIZONTAL;
      
      tabbed_panel = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
      tabbed_panel.addChangeListener(new ChangeListener() {
         public void stateChanged(ChangeEvent e) {
            String selected = tabbed_panel.getTitleAt(tabbed_panel.getSelectedIndex());
            if (selected.equals("Search")) {
               // Set focus on text_search field
               text_search.requestFocusInWindow();
            }
            if (selected.equals("Guide")) {
               // Reset date range in Guide start time combo box
               tab_guide.setComboBoxDates(guide_start, guide_hour_increment, guide_total_range);
            }
         }
     });

      InputMap im = tabbed_panel.getInputMap();
      im.put(KeyStroke.getKeyStroke("pressed RIGHT"), "none");
      im.put(KeyStroke.getKeyStroke("released RIGHT"), "none");
      im.put(KeyStroke.getKeyStroke("pressed LEFT"), "none");
      im.put(KeyStroke.getKeyStroke("released LEFT"), "none");
      im.put(KeyStroke.getKeyStroke("pressed UP"), "none");
      im.put(KeyStroke.getKeyStroke("released UP"), "none");
      im.put(KeyStroke.getKeyStroke("pressed DOWN"), "none");
      im.put(KeyStroke.getKeyStroke("released DOWN"), "none");
            
      // ToDo Title + Tivo Selector + Refresh button
      Dimension space_40 = new Dimension(40,0);
      Dimension space_5 = new Dimension(5,0);
      
      // ToDo Tab items      
      JPanel panel_todo = new JPanel();
      panel_todo.setLayout(new GridBagLayout());
      
      JPanel row1_todo = new JPanel();
      row1_todo.setLayout(new BoxLayout(row1_todo, BoxLayout.LINE_AXIS));
      
      JLabel title_todo = new JLabel("ToDo list");
      
      JLabel tivo_todo_label = new javax.swing.JLabel();
      
      tivo_todo = new javax.swing.JComboBox();
      tivo_todo.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
             if (e.getStateChange() == ItemEvent.SELECTED) {
                tab_todo.TABLE.clearSelection();
                tab_todo.clear();
                String tivoName = getTivoName("todo");
                if (tab_todo.tivo_data.containsKey(tivoName))
                   tab_todo.AddRows(tivoName, tab_todo.tivo_data.get(tivoName));
            }
         }
      });
      tivo_todo.setToolTipText(getToolTip("tivo_todo"));

      JButton refresh_todo = new JButton("Refresh");
      refresh_todo.setToolTipText(getToolTip("refresh_todo"));
      refresh_todo.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            // Refresh to do list
            tab_todo.TABLE.clearSelection();
            tab_todo.clear();
            String tivoName = (String)tivo_todo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               jobData job = new jobData();
               job.source      = tivoName;
               job.tivoName    = tivoName;
               job.type        = "remote";
               job.name        = "Remote";
               job.remote_todo = true;
               job.todo        = tab_todo;
               jobMonitor.submitNewJob(job);
            }
         }
      });

      JButton cancel_todo = new JButton("Cancel");
      cancel_todo.setToolTipText(getToolTip("cancel_todo"));
      cancel_todo.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            tab_todo.DeleteCB();
         }
      });
      
      row1_todo.add(Box.createRigidArea(space_40));
      row1_todo.add(title_todo);
      row1_todo.add(Box.createRigidArea(space_5));
      row1_todo.add(tivo_todo_label);
      row1_todo.add(Box.createRigidArea(space_5));
      row1_todo.add(tivo_todo);
      row1_todo.add(Box.createRigidArea(space_5));
      row1_todo.add(refresh_todo);
      row1_todo.add(Box.createRigidArea(space_5));
      row1_todo.add(cancel_todo);
      panel_todo.add(row1_todo, c);
      
      tab_todo = new todoTable(config.gui.getJFrame());
      tab_todo.TABLE.setPreferredScrollableViewportSize(tab_todo.TABLE.getPreferredSize());
      JScrollPane tabScroll_todo = new JScrollPane(tab_todo.scroll);
      gy++;
      c.gridy = gy;
      c.weightx = 1.0;
      c.weighty = 1.0;
      c.gridwidth = 8;
      c.fill = GridBagConstraints.BOTH;
      panel_todo.add(tabScroll_todo, c);
      
      // Guide Tab items
      gy = 0;
      c.ipady = 0;
      c.weighty = 0.0;  // default to no vertical stretch
      c.weightx = 0.0;  // default to no horizontal stretch
      c.gridx = 0;
      c.gridy = gy;
      c.gridwidth = 1;
      c.gridheight = 1;
      c.anchor = GridBagConstraints.CENTER;
      c.fill = GridBagConstraints.HORIZONTAL;
      JPanel panel_guide = new JPanel();
      
      panel_guide.setLayout(new GridBagLayout());
      
      JPanel row1_guide = new JPanel();
      row1_guide.setLayout(new BoxLayout(row1_guide, BoxLayout.LINE_AXIS));
      
      JLabel title_guide = new JLabel("Guide");
      
      JLabel tivo_guide_label = new javax.swing.JLabel();
      
      tivo_guide = new javax.swing.JComboBox();
      tivo_guide.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
             if (e.getStateChange() == ItemEvent.SELECTED) {
                if ( ! tab_guide.inFolder ) {
                   // Refresh channel list only if not inside a folder
                   tab_guide.TABLE.clearSelection();
                   tab_guide.clear();
                   String tivoName = getTivoName("guide");
                   if (tab_guide.tivo_data.containsKey(tivoName))
                      tab_guide.AddRows(tivoName, tab_guide.tivo_data.get(tivoName));
                }
            }
         }
      });
      tivo_guide.setToolTipText(getToolTip("tivo_guide"));
      
      JLabel guide_start_label = new JLabel("Start");
      guide_start = new javax.swing.JComboBox();
      guide_start.setToolTipText(getToolTip("guide_start"));
      guide_start.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
             if (e.getStateChange() == ItemEvent.SELECTED) {
                if (tab_guide.inFolder) {
                   String start = (String)guide_start.getSelectedItem();
                   if (start != null && start.length() > 0) {
                      int range = (Integer)guide_range.getValue();
                      tab_guide.updateFolder(start, range);
                   }
                }
            }
         }
      });

      
      JLabel guide_range_label = new JLabel("Range");
      SpinnerModel guide_range_spinner = new SpinnerNumberModel(6, 1, 12, 1);
      guide_range = new javax.swing.JSpinner(guide_range_spinner);
      guide_range.setToolTipText(getToolTip("guide_range"));
      guide_range.addChangeListener(new ChangeListener() {
         public void stateChanged(ChangeEvent e) {
            if (tab_guide.inFolder) {
               String start = (String)guide_start.getSelectedItem();
               if (start != null && start.length() > 0) {
                  int range = (Integer)guide_range.getValue();
                  tab_guide.updateFolder(start, range);
               }
            }
         }
      });

      refresh_guide = new JButton("Channels");
      refresh_guide.setToolTipText(getToolTip("refresh_guide"));
      refresh_guide.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            if (tab_guide.inFolder) {
               // Return from inside a folder to top level table display
               tab_guide.setFolderState(false);
               tab_guide.Refresh((JSONArray)null);
               if (tab_guide.folderEntryNum >= 0)
                  tab_guide.SelectFolder(tab_guide.folderName);
            } else {
               // At top level => Update current folder contents
               tab_guide.TABLE.clearSelection();
               tab_guide.clear();
               String tivoName = (String)tivo_guide.getSelectedItem();
               if (tivoName != null && tivoName.length() > 0) {
                  // Obtain and display channel list only if necessary
                  tab_guide.updateChannels(tivoName);
               }
            }
         }
      });

      JButton guide_record = new JButton("Record");
      guide_record.setToolTipText(getToolTip("guide_record"));
      guide_record.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            if (tab_guide.inFolder) {
               String tivoName = (String)tivo_guide.getSelectedItem();
               if (tivoName != null && tivoName.length() > 0) {
                  tab_guide.recordSingle(tivoName);
               }
            }
         }
      });

      JButton guide_recordSP = new JButton("Season Pass");
      guide_recordSP.setToolTipText(getToolTip("guide_recordSP"));
      guide_recordSP.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            if (tab_guide.inFolder) {
               String tivoName = (String)tivo_guide.getSelectedItem();
               if (tivoName != null && tivoName.length() > 0) {
                  tab_guide.recordSingle(tivoName);
               }
            }
         }
      });
      
      row1_guide.add(Box.createRigidArea(space_5));
      row1_guide.add(title_guide);
      row1_guide.add(Box.createRigidArea(space_5));
      row1_guide.add(tivo_guide_label);
      row1_guide.add(Box.createRigidArea(space_5));
      row1_guide.add(tivo_guide);
      row1_guide.add(Box.createRigidArea(space_5));
      row1_guide.add(guide_start_label);
      row1_guide.add(Box.createRigidArea(space_5));
      row1_guide.add(guide_start);
      row1_guide.add(Box.createRigidArea(space_5));
      row1_guide.add(guide_range_label);
      row1_guide.add(Box.createRigidArea(space_5));
      row1_guide.add(guide_range);
      row1_guide.add(Box.createRigidArea(space_5));
      row1_guide.add(refresh_guide);
      row1_guide.add(Box.createRigidArea(space_5));
      row1_guide.add(guide_record);
      row1_guide.add(Box.createRigidArea(space_5));
      row1_guide.add(guide_recordSP);
      panel_guide.add(row1_guide, c);
      
      tab_guide = new guideTable(config.gui.getJFrame());
      tab_guide.TABLE.setPreferredScrollableViewportSize(tab_guide.TABLE.getPreferredSize());
      JScrollPane tabScroll_guide = new JScrollPane(tab_guide.scroll);
      gy++;
      c.gridy = gy;
      c.weightx = 1.0;
      c.weighty = 1.0;
      c.gridwidth = 8;
      c.fill = GridBagConstraints.BOTH;
      panel_guide.add(tabScroll_guide, c);
            
      // Season Passes Tab items      
      gy = 0;
      c.ipady = 0;
      c.weighty = 0.0;  // default to no vertical stretch
      c.weightx = 0.0;  // default to no horizontal stretch
      c.gridx = 0;
      c.gridy = gy;
      c.gridwidth = 1;
      c.gridheight = 1;
      c.anchor = GridBagConstraints.CENTER;
      c.fill = GridBagConstraints.HORIZONTAL;

      JPanel panel_sp = new JPanel();
      panel_sp.setLayout(new GridBagLayout());
      
      JPanel row1_sp = new JPanel();
      row1_sp.setLayout(new BoxLayout(row1_sp, BoxLayout.LINE_AXIS));
      
      JLabel title_sp = new JLabel("Season Passes");
      
      JLabel tivo_sp_label = new javax.swing.JLabel();
      
      tivo_sp = new javax.swing.JComboBox();
      tivo_sp.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
             if (e.getStateChange() == ItemEvent.SELECTED) {
                tab_sp.TABLE.clearSelection();
                tab_sp.clear();
                String tivoName = getTivoName("sp");
                if (tab_sp.tivo_data.containsKey(tivoName))
                   tab_sp.AddRows(tivoName, tab_sp.tivo_data.get(tivoName));
            }
         }
      });
      tivo_sp.setToolTipText(getToolTip("tivo_sp"));

      JButton refresh_sp = new JButton("Refresh");
      refresh_sp.setToolTipText(getToolTip("refresh_sp"));
      refresh_sp.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            // Refresh SP list
            tab_sp.TABLE.clearSelection();
            tab_sp.clear();
            String tivoName = (String)tivo_sp.getSelectedItem();
            SPListCB(tivoName);
         }
      });
      
      JButton save_sp = new JButton("Save...");
      save_sp.setToolTipText(getToolTip("save_sp"));
      save_sp.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            // Save SP data to a file
            String tivoName = (String)tivo_sp.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               if (tab_sp.isTableLoaded()) {
                  log.error("Cannot save loaded Season Passes");
                  return;
               }  else {
                  Browser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                  Browser.setSelectedFile(new File(config.programDir + File.separator + tivoName + ".sp"));
                  int result = Browser.showDialog(config.gui.getJFrame(), "Save to file");
                  if (result == JFileChooser.APPROVE_OPTION) {               
                     File file = Browser.getSelectedFile();
                     tab_sp.SPListSave(tivoName, file.getAbsolutePath());
                  }
               }
            }
         }
      });         
      
      JButton load_sp = new JButton("Load...");
      load_sp.setToolTipText(getToolTip("load_sp"));
      load_sp.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            // Load SP data from a file
            Browser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            int result = Browser.showDialog(config.gui.getJFrame(), "Load from file");
            if (result == JFileChooser.APPROVE_OPTION) {               
               File file = Browser.getSelectedFile();
               tab_sp.SPListLoad(file.getAbsolutePath());
            }
         }
      });         
      
      JButton copy_sp = new JButton("Copy");
      copy_sp.setToolTipText(getToolTip("copy_sp"));
      copy_sp.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            // Copy selected SPs to a TiVo
            String tivoName = (String)tivo_sp.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0)
               tab_sp.SPListCopy(tivoName);
         }
      });         
      
      JButton modify_sp = new JButton("Modify");
      modify_sp.setToolTipText(getToolTip("modify_sp"));
      modify_sp.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            // Modify selected SP
            String tivoName = (String)tivo_sp.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               if (tab_sp.isTableLoaded()) {
                  log.error("Cannot modify loaded Season Passes");
                  return;
               }  else {
                  tab_sp.SPListModify(tivoName);
               }
            }
         }
      });         
      
      JButton reorder_sp = new JButton("Re-order");
      reorder_sp.setToolTipText(getToolTip("reorder_sp"));
      reorder_sp.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            // Re-prioritize SPs on TiVo to match current table row order
            String tivoName = (String)tivo_sp.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               if (tab_sp.isTableLoaded()) {
                  log.error("Cannot re-order loaded Season Passes");
                  return;
               }  else {
                  tab_sp.SPReorderCB(tivoName);
               }
            }
         }
      });         
      
      row1_sp.add(Box.createRigidArea(space_5));
      row1_sp.add(title_sp);
      row1_sp.add(Box.createRigidArea(space_5));
      row1_sp.add(tivo_sp_label);
      row1_sp.add(Box.createRigidArea(space_5));
      row1_sp.add(tivo_sp);
      row1_sp.add(Box.createRigidArea(space_5));
      row1_sp.add(refresh_sp);
      row1_sp.add(Box.createRigidArea(space_5));
      row1_sp.add(save_sp);
      row1_sp.add(Box.createRigidArea(space_5));
      row1_sp.add(load_sp);
      row1_sp.add(Box.createRigidArea(space_5));
      row1_sp.add(copy_sp);
      row1_sp.add(Box.createRigidArea(space_5));
      row1_sp.add(modify_sp);
      row1_sp.add(Box.createRigidArea(space_5));
      row1_sp.add(reorder_sp);
      panel_sp.add(row1_sp, c);
      
      tab_sp = new spTable(config.gui.getJFrame());
      tab_sp.TABLE.setPreferredScrollableViewportSize(tab_sp.TABLE.getPreferredSize());
      JScrollPane tabScroll_sp = new JScrollPane(tab_sp.scroll);
      gy++;
      c.gridy = gy;
      c.weightx = 1.0;
      c.weighty = 1.0;
      c.gridwidth = 8;
      c.fill = GridBagConstraints.BOTH;
      panel_sp.add(tabScroll_sp, c);
      
      // Cancelled table items      
      gy = 0;
      c.ipady = 0;
      c.weighty = 0.0;  // default to no vertical stretch
      c.weightx = 0.0;  // default to no horizontal stretch
      c.gridx = 0;
      c.gridy = gy;
      c.gridwidth = 1;
      c.gridheight = 1;
      c.anchor = GridBagConstraints.CENTER;
      c.fill = GridBagConstraints.HORIZONTAL;
      
      JPanel panel_cancel = new JPanel();
      panel_cancel.setLayout(new GridBagLayout());
      
      JPanel row1_cancel = new JPanel();
      row1_cancel.setLayout(new BoxLayout(row1_cancel, BoxLayout.LINE_AXIS));
      
      JLabel title_cancel = new JLabel("Not Record list");
      
      JLabel tivo_cancel_label = new javax.swing.JLabel();
      
      tivo_cancel = new javax.swing.JComboBox();
      tivo_cancel.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
             if (e.getStateChange() == ItemEvent.SELECTED) {               
               // TiVo selection changed for Not Record tab
               // NOTE: Don't want to reset table in case we want to record a show on another TiVo
               /*
               tab_cancel.setFolderState(false);
               tab_cancel.TABLE.clearSelection();
               tab_cancel.clear();
               String tivoName = getTivoName("cancel");
               if (tab_cancel.tivo_data.containsKey(tivoName))
                  tab_cancel.AddRows(tivoName, tab_cancel.tivo_data.get(tivoName));
               */
            }
         }
      });
      tivo_cancel.setToolTipText(getToolTip("tivo_cancel"));

      refresh_cancel = new JButton("Refresh");
      label_cancel = new JLabel("Top Level View");
      refresh_cancel.setToolTipText(getToolTip("refresh_cancel_top"));
      refresh_cancel.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            if (tab_cancel.inFolder) {
               // Return to top level table display
               tab_cancel.setFolderState(false);
               tab_cancel.Refresh(null);
               if (tab_cancel.folderEntryNum >= 0)
                  tab_cancel.SelectFolder(tab_cancel.folderName);
            } else {
               // Refresh to do list
               tab_cancel.TABLE.clearSelection();
               tab_cancel.clear();
               String tivoName = (String)tivo_cancel.getSelectedItem();
               if (tivoName != null && tivoName.length() > 0) {
                  jobData job = new jobData();
                  job.source        = tivoName;
                  job.tivoName      = tivoName;
                  job.type          = "remote";
                  job.name          = "Remote";
                  job.remote_cancel = true;
                  job.cancelled     = tab_cancel;
                  jobMonitor.submitNewJob(job);
               }
            }
         }
      });

      JButton record_cancel = new JButton("Record");
      record_cancel.setToolTipText(getToolTip("record_cancel"));
      record_cancel.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            if (tab_cancel.inFolder) {
               String tivoName = (String)tivo_cancel.getSelectedItem();
               if (tivoName != null && tivoName.length() > 0)
                  tab_cancel.recordSingle(tivoName);
            }
         }
      });
      
      row1_cancel.add(Box.createRigidArea(space_5));
      row1_cancel.add(title_cancel);
      row1_cancel.add(Box.createRigidArea(space_5));
      row1_cancel.add(tivo_cancel_label);
      row1_cancel.add(Box.createRigidArea(space_5));
      row1_cancel.add(tivo_cancel);
      row1_cancel.add(Box.createRigidArea(space_5));
      row1_cancel.add(refresh_cancel);
      row1_cancel.add(Box.createRigidArea(space_5));
      row1_cancel.add(record_cancel);
      row1_cancel.add(Box.createRigidArea(space_5));
      row1_cancel.add(label_cancel);
      panel_cancel.add(row1_cancel, c);
      
      tab_cancel = new cancelledTable(config.gui.getJFrame());
      tab_cancel.TABLE.setPreferredScrollableViewportSize(tab_cancel.TABLE.getPreferredSize());
      JScrollPane tabScroll_cancel = new JScrollPane(tab_cancel.scroll);
      gy++;
      c.gridy = gy;
      c.weightx = 1.0;
      c.weighty = 1.0;
      c.gridwidth = 8;
      c.fill = GridBagConstraints.BOTH;
      panel_cancel.add(tabScroll_cancel, c);
      
      // Premiere tab items      
      gy = 0;
      c.ipady = 0;
      c.weighty = 0.0;  // default to no vertical stretch
      c.weightx = 0.0;  // default to no horizontal stretch
      c.gridx = 0;
      c.gridy = gy;
      c.gridwidth = 1;
      c.gridheight = 1;
      c.anchor = GridBagConstraints.CENTER;
      c.fill = GridBagConstraints.HORIZONTAL;
      
      JPanel panel_premiere = new JPanel();
      panel_premiere.setLayout(new GridBagLayout());
      
      JPanel row1_premiere = new JPanel();
      row1_premiere.setLayout(new BoxLayout(row1_premiere, BoxLayout.LINE_AXIS));
      
      JLabel title_premiere = new JLabel("Season Premieres");
      
      JLabel tivo_premiere_label = new javax.swing.JLabel();
      
      tivo_premiere = new javax.swing.JComboBox();
      tivo_premiere.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
             if (e.getStateChange() == ItemEvent.SELECTED) {
               // Clear channel list
               premiere_model.clear();
               
               String tivoName = getTivoName("premiere");
               // Load channel list for this TiVo
               loadChannelInfo(tivoName);
               
               // NOTE: Don't want to reset premieres table in case we want to record a show on another TiVo
               /*
               // Clear premieres table
               tab_premiere.TABLE.clearSelection();
               tab_premiere.clear();
               
               // Update premieres table
               if (tab_premiere.tivo_data.containsKey(tivoName))
                  tab_premiere.AddRows(tivoName, tab_premiere.tivo_data.get(tivoName));
               */
            }
         }
      });
      tivo_premiere.setToolTipText(getToolTip("tivo_premiere"));

      JButton refresh_premiere = new JButton("Search");
      refresh_premiere.setToolTipText(getToolTip("refresh_premiere"));
      refresh_premiere.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            // Refresh table
            tab_premiere.TABLE.clearSelection();
            tab_premiere.clear();
            String tivoName = (String)tivo_premiere.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {            
               // This updates premiere_channel_info "isSelected" settings
               if ( ! updateSelectedChannels(tivoName) )
                  return;
               
               // Save channel information to file
               saveChannelInfo(tivoName);
               
               // Now search for Premieres in background mode
               jobData job = new jobData();
               job.source          = tivoName;
               job.tivoName        = tivoName;
               job.type            = "remote";
               job.name            = "Remote";
               job.remote_premiere = true;
               job.premiere        = tab_premiere;
               jobMonitor.submitNewJob(job);
            }
         }
      });
      
      JButton record_premiere = new JButton("Record");
      record_premiere.setToolTipText(getToolTip("record_premiere"));
      record_premiere.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String tivoName = (String)tivo_premiere.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0)
               tab_premiere.recordSP(tivoName);
         }
      });
      
      JButton premiere_channels_update = new JButton("Update Channels");
      premiere_channels_update.setToolTipText(getToolTip("premiere_channels_update"));
      premiere_channels_update.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String tivoName = (String)tivo_premiere.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               // Build list of received channels for this TiVo
               jobData job = new jobData();
               job.source          = tivoName;
               job.tivoName        = tivoName;
               job.type            = "remote";
               job.name            = "Remote";
               job.remote_channels = true;
               jobMonitor.submitNewJob(job);
            }
         }
      });
      
      premiere_model = new DefaultListModel();
      premiere_channels = new JList(premiere_model);
      premiere_channels.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
      premiere_channels.setLayoutOrientation(JList.VERTICAL);
      premiere_channels.setVisibleRowCount(4);
      JScrollPane listScroller = new JScrollPane(premiere_channels);
      
      premiere_channels.setToolTipText(getToolTip("premiere_channels"));
      
      row1_premiere.add(Box.createRigidArea(space_5));
      row1_premiere.add(title_premiere);
      row1_premiere.add(Box.createRigidArea(space_5));
      row1_premiere.add(tivo_premiere_label);
      row1_premiere.add(Box.createRigidArea(space_5));
      row1_premiere.add(tivo_premiere);
      row1_premiere.add(Box.createRigidArea(space_5));
      row1_premiere.add(refresh_premiere);
      row1_premiere.add(Box.createRigidArea(space_5));
      row1_premiere.add(record_premiere);
      row1_premiere.add(Box.createRigidArea(space_40));
      row1_premiere.add(premiere_channels_update);
      panel_premiere.add(row1_premiere, c);
      gy++;
      c.gridy = gy;
      
      tab_premiere = new premiereTable(config.gui.getJFrame());
      tab_premiere.TABLE.setPreferredScrollableViewportSize(tab_premiere.TABLE.getPreferredSize());
      JScrollPane tabScroll_premiere = new JScrollPane(tab_premiere.scroll);
      
      JPanel row2_premiere = new JPanel();
      row2_premiere.setLayout(new GridBagLayout());
      c.gridy = 0;
      c.gridx = 0;
      c.weightx = 0.9;
      c.weighty = 1.0;
      c.fill = GridBagConstraints.BOTH;
      row2_premiere.add(tabScroll_premiere, c);
      c.gridx = 1;
      c.weightx = 0.1;      
      row2_premiere.add(listScroller, c);

      gy++;
      c.gridx = 0;
      c.gridy = gy;
      c.weightx = 1.0;
      c.weighty = 1.0;
      c.gridwidth = 8;
      c.fill = GridBagConstraints.BOTH;
      panel_premiere.add(row2_premiere, c);
      
      // System Information tab items      
      gy = 0;
      c.ipady = 0;
      c.weighty = 0.0;  // default to no vertical stretch
      c.weightx = 0.0;  // default to no horizontal stretch
      c.gridx = 0;
      c.gridy = gy;
      c.gridwidth = 1;
      c.gridheight = 1;
      c.anchor = GridBagConstraints.CENTER;
      c.fill = GridBagConstraints.HORIZONTAL;

      JPanel panel_info = new JPanel();
      panel_info.setLayout(new GridBagLayout());
      
      JPanel row1_info = new JPanel();
      row1_info.setLayout(new BoxLayout(row1_info, BoxLayout.LINE_AXIS));
      
      JLabel title_info = new JLabel("System Information");
      
      JLabel tivo_info_label = new javax.swing.JLabel();
      
      tivo_info = new javax.swing.JComboBox();
      tivo_info.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
             if (e.getStateChange() == ItemEvent.SELECTED) {
               // Clear text area
               text_info.setEditable(true);
               text_info.setText("");
               
               // Put cached info in text area if available
               String tivoName = getTivoName("info");
               if (tivoName != null && tivoName.length() > 0) {
                  if (tivo_info_data.containsKey(tivoName))
                     text_info.setText(tivo_info_data.get(tivoName));
               }
               text_info.setEditable(false);
            }
         }
      });
      tivo_info.setToolTipText(getToolTip("tivo_info"));

      JButton refresh_info = new JButton("Refresh");
      refresh_info.setToolTipText(getToolTip("refresh_info"));
      refresh_info.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            // Refresh info text
            String tivoName = (String)tivo_info.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0)
               RC_infoCB(tivoName);
         }
      });
      row1_info.add(Box.createRigidArea(space_40));
      row1_info.add(title_info);
      row1_info.add(Box.createRigidArea(space_40));
      row1_info.add(tivo_info_label);
      row1_info.add(Box.createRigidArea(space_5));
      row1_info.add(tivo_info);
      row1_info.add(Box.createRigidArea(space_40));
      row1_info.add(refresh_info);
      panel_info.add(row1_info, c);
      
      text_info = new JTextPane();
      text_info.setEditable(false);
      JScrollPane tabScroll_info = new JScrollPane(text_info);
      gy++;
      c.gridy = gy;
      c.weightx = 1.0;
      c.weighty = 1.0;
      c.gridwidth = 8;
      c.fill = GridBagConstraints.BOTH;
      panel_info.add(tabScroll_info, c);
      
      // Search tab items      
      gy = 0;
      c.ipady = 0;
      c.weighty = 0.0;  // default to no vertical stretch
      c.weightx = 0.0;  // default to no horizontal stretch
      c.gridx = 0;
      c.gridy = gy;
      c.gridwidth = 1;
      c.gridheight = 1;
      c.anchor = GridBagConstraints.CENTER;
      c.fill = GridBagConstraints.HORIZONTAL;
      
      JPanel panel_search = new JPanel();
      panel_search.setLayout(new GridBagLayout());
      
      JPanel row1_search = new JPanel();
      row1_search.setLayout(new BoxLayout(row1_search, BoxLayout.LINE_AXIS));
      
      JLabel title_search = new JLabel("Search");
      
      JLabel tivo_search_label = new javax.swing.JLabel();
      
      tivo_search = new javax.swing.JComboBox();
      tivo_search.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
             if (e.getStateChange() == ItemEvent.SELECTED) {
               // NOTE: Don't want to reset table in case we want to record a show on another TiVo
               /*
               tab_search.TABLE.clearSelection();
               tab_search.clear();
               String tivoName = getTivoName("search");
               if (tab_search.tivo_data.containsKey(tivoName))
                  tab_search.AddRows(tivoName, tab_search.tivo_data.get(tivoName));
               */
            }
         }
      });
      tivo_search.setToolTipText(getToolTip("tivo_search"));

      button_search = new JButton("Search");
      button_search.setToolTipText(getToolTip("button_search"));
      button_search.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            if (tab_search.inFolder) {
               // Return to top level table display
               tab_search.setFolderState(false);
               tab_search.Refresh((JSONArray)null);
               if (tab_search.folderEntryNum >= 0)
                  tab_search.SelectFolder(tab_search.folderName);
            } else {
               // New search
               tab_search.TABLE.clearSelection();
               tab_search.clear();
               String tivoName = (String)tivo_search.getSelectedItem();
               if (tivoName != null && tivoName.length() > 0) {
                  String keyword = string.removeLeadingTrailingSpaces(text_search.getText());
                  if (keyword == null || keyword.length() == 0)
                     return;
                  int max = (Integer)max_search.getValue();
                  
                  jobData job = new jobData();
                  job.source                = tivoName;
                  job.tivoName              = tivoName;
                  job.type                  = "remote";
                  job.name                  = "Remote";
                  job.search                = tab_search;
                  job.remote_search_max     = max;
                  job.remote_search         = true;
                  job.remote_search_keyword = keyword;
                  jobMonitor.submitNewJob(job);
               }
            }
         }
      });

      text_search = new JTextField(15);
      text_search.setMinimumSize(text_search.getPreferredSize());
      // Press "Search" button when enter pressed in search text field
      text_search.addKeyListener( new KeyAdapter() {
         public void keyReleased( KeyEvent e ) {
            if( e.getKeyCode() == KeyEvent.VK_ENTER ) {
               button_search.doClick();
            }
         }
      });

      text_search.setToolTipText(getToolTip("text_search"));

      JButton record_search = new JButton("Record");
      record_search.setToolTipText(getToolTip("record_search"));
      record_search.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            if (tab_search.inFolder) {
               String tivoName = (String)tivo_search.getSelectedItem();
               if (tivoName != null && tivoName.length() > 0) {
                  tab_search.recordSingle(tivoName);
               }
            }
         }
      });

      JButton record_sp_search = new JButton("Season Pass");
      record_sp_search.setToolTipText(getToolTip("record_sp_search"));
      record_sp_search.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            if (tab_search.inFolder) {
               String tivoName = (String)tivo_search.getSelectedItem();
               if (tivoName != null && tivoName.length() > 0) {
                  tab_search.recordSP(tivoName);
               }
            }
         }
      });

      JLabel max_search_label = new JLabel("Max");
      SpinnerModel spinner_model = new SpinnerNumberModel(100, 50, 800, 50);      
      max_search = new JSpinner(spinner_model);
      max_search.setToolTipText(getToolTip("max_search"));
      
      row1_search.add(Box.createRigidArea(space_5));
      row1_search.add(title_search);
      row1_search.add(Box.createRigidArea(space_5));
      row1_search.add(tivo_search_label);
      row1_search.add(Box.createRigidArea(space_5));
      row1_search.add(tivo_search);
      row1_search.add(Box.createRigidArea(space_5));
      row1_search.add(button_search);
      row1_search.add(Box.createRigidArea(space_5));
      row1_search.add(text_search);
      row1_search.add(Box.createRigidArea(space_5));
      row1_search.add(max_search_label);
      row1_search.add(Box.createRigidArea(space_5));
      row1_search.add(max_search);
      row1_search.add(Box.createRigidArea(space_5));
      row1_search.add(record_search);
      row1_search.add(Box.createRigidArea(space_5));
      row1_search.add(record_sp_search);
      panel_search.add(row1_search, c);
      
      tab_search = new searchTable(config.gui.getJFrame());
      tab_search.TABLE.setPreferredScrollableViewportSize(tab_search.TABLE.getPreferredSize());
      JScrollPane tabScroll_search = new JScrollPane(tab_search.scroll);
      gy++;
      c.gridy = gy;
      c.weightx = 1.0;
      c.weighty = 1.0;
      c.gridwidth = 8;
      c.fill = GridBagConstraints.BOTH;
      panel_search.add(tabScroll_search, c);

      // Remote Control Tab items
      gy = 0;
      
      // TiVo Remote control panel
      JPanel panel_controls = new JPanel();
      panel_controls.setLayout(null);
      panel_controls.setBackground(Color.black);
      Insets insets = panel_controls.getInsets();      
      Dimension size;      
      Object[][] Buttons = {
         {"channelUp",   "channel_up.png",   0.5,   5,  10,  0, 0, "PAGE_UP",   KeyEvent.VK_PAGE_UP},
         {"lab_channel", "channel_label.png",0.7,  20,  40,  0, 0, null,        -1},
         {"channelDown", "channel_down.png", 0.5,   5,  55,  0, 0, "PAGE_DOWN", KeyEvent.VK_PAGE_DOWN},
         {"left",        "left.png",         0.5,  10,  85, 20, 0, "LEFT",      KeyEvent.VK_LEFT},
         {"zoom",        "zoom.png",         0.7,   5, 130,  0, 0, "AltZ",      KeyEvent.VK_Z},
         {"tivo",        "tivo.png",         0.7,  55,   0,  0, 0, "AltT",      KeyEvent.VK_T},
         {"up",          "up.png",           0.5,  65,  40, 20, 0, "UP",        KeyEvent.VK_UP},
         {"select",      "select.png",       0.5,  60,  85,  0, 0, "AltS",      KeyEvent.VK_S},
         {"down",        "down.png",         0.5,  65, 125, 20, 0, "DOWN",      KeyEvent.VK_DOWN},
         {"liveTv",      "livetv.png",       0.7, 115,  20,  0, 0, "AltL",      KeyEvent.VK_L},
         {"info",        "info.png",         0.7, 115,  55,  0, 0, "AltI",      KeyEvent.VK_I},
         {"right",       "right.png",        0.5, 120,  85, 20, 0, "RIGHT",     KeyEvent.VK_RIGHT},
         {"guide",       "guide.png",        0.7, 115, 130,  0, 0, "AltG",      KeyEvent.VK_G},
         {"num1",        "1.png",            0.7, 200,   0, 10, 0, "1",         KeyEvent.VK_1},
         {"num2",        "2.png",            0.7, 245,   0, 10, 0, "2",         KeyEvent.VK_2},
         {"num3",        "3.png",            0.7, 290,   0, 10, 0, "3",         KeyEvent.VK_3},
         {"num4",        "4.png",            0.7, 200,  35, 10, 0, "4",         KeyEvent.VK_4},
         {"num5",        "5.png",            0.7, 245,  35, 10, 0, "5",         KeyEvent.VK_5},
         {"num6",        "6.png",            0.7, 290,  35, 10, 0, "6",         KeyEvent.VK_6},
         {"num7",        "7.png",            0.7, 200,  70, 10, 0, "7",         KeyEvent.VK_7},
         {"num8",        "8.png",            0.7, 245,  70, 10, 0, "8",         KeyEvent.VK_8},
         {"num9",        "9.png",            0.7, 290,  70, 10, 0, "9",         KeyEvent.VK_9},
         {"clear",       "clear.png",        0.7, 200, 105, 10, 0, "DELETE",    KeyEvent.VK_DELETE},
         {"num0",        "0.png",            0.7, 245, 105, 10, 0, "0",         KeyEvent.VK_0},
         {"enter",       "enter.png",        0.7, 290, 105, 10, 0, "ENTER",     KeyEvent.VK_ENTER},
         {"actionA",     "A.png",            0.7, 185, 135, 10, 0, "AltA",      KeyEvent.VK_A},
         {"actionB",     "B.png",            0.7, 225, 135, 10, 0, "AltB",      KeyEvent.VK_B},
         {"actionC",     "C.png",            0.7, 265, 135, 10, 0, "AltC",      KeyEvent.VK_C},
         {"actionD",     "D.png",            0.7, 305, 135, 10, 0, "AltD",      KeyEvent.VK_D},
         {"thumbsDown",  "thumbsdown.png",   0.7, 355,   0, 10, 0, "SUBTRACT",  KeyEvent.VK_SUBTRACT},
         {"reverse",     "reverse.png",      0.5, 355,  55, 10, 0, "ShiftCOMMA",KeyEvent.VK_COMMA},
         {"replay",      "replay.png",       0.7, 355, 105, 10, 0, "Shift9",    KeyEvent.VK_9},
         {"play",        "play.png",         0.7, 400,  10, 20, 0, "CLOSEB",    KeyEvent.VK_CLOSE_BRACKET},
         {"pause",       "pause.png",        0.4, 400,  50, 10, 0, "OPENB",     KeyEvent.VK_OPEN_BRACKET},
         {"slow",        "slow.png",         0.7, 400,  90, 20, 0, "ShiftBACK", KeyEvent.VK_BACK_SLASH},
         {"record",      "record.png",       0.7, 400, 130, 10, 0, "AltR",      KeyEvent.VK_R},
         {"thumbsUp",    "thumbsup.png",     0.7, 445,   0, 10, 0, "ADD",       KeyEvent.VK_ADD},
         {"forward",     "forward.png",      0.5, 445,  55, 10, 0, "ShiftPER",  KeyEvent.VK_PERIOD},
         {"advance",     "advance.png",      0.7, 445, 105, 10, 0, "Shift0",    KeyEvent.VK_0},
      };
      for (int i=0; i<Buttons.length; ++i) {
         final String event = (String)Buttons[i][0];
         String imageName = (String)Buttons[i][1];
         double scale = (Double)Buttons[i][2];
         int x = (Integer)Buttons[i][3];
         int y = (Integer)Buttons[i][4];
         int cropx = (Integer)Buttons[i][5];
         int cropy = (Integer)Buttons[i][6];
         String keyName = (String)Buttons[i][7];
         int keyEvent = (Integer)Buttons[i][8];
         if (event.startsWith("lab_")) {
            JLabel l = ImageLabel(panel_controls, imageName, scale);
            if (l == null) continue;
            panel_controls.add(l);
            size = l.getPreferredSize();
            l.setBounds(x+insets.left, y+insets.top, size.width, size.height);
         } else {
            JButton b = ImageButton(panel_controls, imageName, scale);
            if (b == null) continue;
            b.setToolTipText(getToolTip(event));
            panel_controls.add(b);
            size = b.getPreferredSize();
            b.setBounds(x+insets.left, y+insets.top, size.width-cropx, size.height-cropy);
            b.addActionListener(new java.awt.event.ActionListener() {
               public void actionPerformed(java.awt.event.ActionEvent e) {
                  final String tivoName = (String)tivo_rc.getSelectedItem();
                  if (tivoName != null && tivoName.length() > 0) {
                     class backgroundRun extends SwingWorker<Object, Object> {
                        protected Object doInBackground() {
                           Remote r = new Remote(tivoName);
                           if (r.success) {
                              try {
                                 JSONObject json = new JSONObject();
                                 json.put("event", event);
                                 r.Command("keyEventSend", json);
                              } catch (JSONException e1) {
                                 log.error("RC - " + e1.getMessage());
                              }
                              r.disconnect();
                           }
                           return null;
                        }
                     }
                     backgroundRun b = new backgroundRun();
                     b.execute();
                  }
               }
            });
            if (keyName != null && keyEvent != -1) {
               AddButtonShortcut(b, keyName, keyEvent);
            }
         }
      }
      
      // Special buttons
      JButton sps9s = new JButton("Clock: SPS9S");
      sps9s.setToolTipText(getToolTip("sps9s"));
      setMacroCB(sps9s, new String[] {"select", "play", "select", "9", "select", "clear"});
      size = sps9s.getPreferredSize();
      sps9s.setBackground(Color.black);
      sps9s.setBorderPainted(false);
      sps9s.setForeground(Color.white);
      panel_controls.add(sps9s);
      sps9s.setBounds(500+insets.left, 10+insets.top, size.width, size.height);
      
      /*JButton sps30s = new JButton("30ss: SPS30S");
      sps30s.setToolTipText(getToolTip("sps30s"));
      setMacroCB(sps30s, new String[] {"select", "play", "select", "3", "0", "select", "clear"});
      size = sps30s.getPreferredSize();
      sps30s.setBackground(Color.black);
      sps30s.setBorderPainted(false);
      sps30s.setForeground(Color.white);
      panel_controls.add(sps30s);
      sps30s.setBounds(500+insets.left, 40+insets.top, size.width, size.height);*/
      
      JButton spsps = new JButton("Banner: SPSPS");
      spsps.setToolTipText(getToolTip("spsps"));
      setMacroCB(spsps, new String[] {"select", "play", "select", "pause", "select", "play"});
      size = spsps.getPreferredSize();
      spsps.setBackground(Color.black);
      spsps.setBorderPainted(false);
      spsps.setForeground(Color.white);
      panel_controls.add(spsps);
      spsps.setBounds(500+insets.left, 40+insets.top, size.width, size.height);
      
      JButton standby = new JButton("Toggle standby");
      standby.setToolTipText(getToolTip("standby"));
      setMacroCB(standby, new String[] {"standby"});
      size = standby.getPreferredSize();
      standby.setBackground(Color.black);
      standby.setBorderPainted(false);
      standby.setForeground(Color.white);
      panel_controls.add(standby);
      standby.setBounds(500+insets.left, 70+insets.top, size.width, size.height);
      
      JButton toggle_cc = new JButton("Toggle CC");
      toggle_cc.setToolTipText(getToolTip("toggle_cc"));
      toggle_cc.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String tivoName = (String)tivo_rc.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               String IP = config.TIVOS.get(tivoName);
               if (cc_state)
                  new telnet(IP, new String[]{"CC_OFF"});
               else
                  new telnet(IP, new String[]{"CC_ON"});
               cc_state = ! cc_state;
            }
         }
      });

      size = toggle_cc.getPreferredSize();
      toggle_cc.setBackground(Color.black);
      toggle_cc.setBorderPainted(false);
      toggle_cc.setForeground(Color.white);
      panel_controls.add(toggle_cc);
      toggle_cc.setBounds(500+insets.left, 100+insets.top, size.width, size.height);
            
      // Other components for the panel      
      JLabel label_rc = new JLabel("TiVo");
      
      tivo_rc = new javax.swing.JComboBox();
      tivo_rc.setToolTipText(getToolTip("tivo_rc"));

      JButton rc_hme_button = new JButton("HME Jump:");
      disableSpaceAction(rc_hme_button);
      rc_hme_button.setToolTipText(getToolTip("rc_hme_button"));
      rc_hme_button.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            final String name = (String)hme_rc.getSelectedItem();
            if (name != null && name.length() > 0) {
               class backgroundRun extends SwingWorker<Object, Object> {
                  protected Object doInBackground() {
                     Remote r = new Remote(getTivoName("rc"));
                     if (r.success) {
                        try {
                           JSONObject json = new JSONObject();
                           json.put("uri", hme.get(name));
                           r.Command("navigate", json);
                        } catch (JSONException e1) {
                           log.error("HME Jump - " + e1.getMessage());
                        }
                        r.disconnect();
                     }
                     return null;
                  }
               }
               backgroundRun b = new backgroundRun();
               b.execute();
            }
         }
      });
      
      hme_rc = new javax.swing.JComboBox();
      hme_rc.setToolTipText(getToolTip("hme_rc"));
      
      JButton rc_jumpto_button = new JButton("Jump to minute:");
      disableSpaceAction(rc_jumpto_button);
      rc_jumpto_button.setToolTipText(getToolTip("rc_jumpto_text"));
      rc_jumpto_button.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            final String tivoName = (String)tivo_rc.getSelectedItem();
            String mins_string = string.removeLeadingTrailingSpaces(rc_jumpto_text.getText());
            if (tivoName == null || tivoName.length() == 0)
               return;
            if (mins_string == null || mins_string.length() == 0)
               return;
            try {
               final int mins = Integer.parseInt(mins_string);
               class backgroundRun extends SwingWorker<Object, Object> {
                  protected Boolean doInBackground() {
                     Remote r = new Remote(tivoName);
                     if (r.success) {
                        JSONObject json = new JSONObject();
                        try {
                           Long pos = (long)60000*mins;
                           json.put("offset", pos);
                           r.Command("jump", json);
                        } catch (JSONException e) {
                           log.error("Jump to minute failed - " + e.getMessage());
                        }
                        r.disconnect();
                     }
                     return null;
                  }
               }
               backgroundRun b = new backgroundRun();
               b.execute();
            } catch (NumberFormatException e1) {
               log.error("Illegal number of minutes specified: " + mins_string);
               return;
            }            
         }
      });
      rc_jumpto_text = new JTextField(15);
      rc_jumpto_text.setToolTipText(getToolTip("rc_jumpto_text"));
      rc_jumpto_text.setText("0");

      JButton rc_jumpahead_button = new JButton("Skip minutes ahead:");
      disableSpaceAction(rc_jumpahead_button);
      rc_jumpahead_button.setToolTipText(getToolTip("rc_jumpahead_text"));
      rc_jumpahead_button.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            final String tivoName = (String)tivo_rc.getSelectedItem();
            String mins_string = string.removeLeadingTrailingSpaces(rc_jumpahead_text.getText());
            if (tivoName == null || tivoName.length() == 0)
               return;
            if (mins_string == null || mins_string.length() == 0)
               return;
            try {
               final int mins = Integer.parseInt(mins_string);
               class backgroundRun extends SwingWorker<Object, Object> {
                  protected Boolean doInBackground() {
                     Remote r = new Remote(tivoName);
                     if (r.success) {
                        JSONObject json = new JSONObject();
                        JSONObject reply = r.Command("position", json);
                        if (reply != null && reply.has("position")) {
                           try {
                              Long pos = reply.getLong("position");
                              pos += (long)60000*mins;
                              json.put("offset", pos);
                              r.Command("jump", json);
                           } catch (JSONException e) {
                              log.error("Skip minutes ahead failed - " + e.getMessage());
                           }
                        }
                        r.disconnect();
                     }
                     return null;
                  }
               }
               backgroundRun b = new backgroundRun();
               b.execute();
            } catch (NumberFormatException e1) {
               log.error("Illegal number of minutes specified: " + mins_string);
               return;
            }            
         }
      });
      rc_jumpahead_text = new JTextField(15);
      rc_jumpahead_text.setToolTipText(getToolTip("rc_jumpahead_text"));
      rc_jumpahead_text.setText("5");

      JButton rc_jumpback_button = new JButton("Skip minutes back:");
      disableSpaceAction(rc_jumpback_button);
      rc_jumpback_button.setToolTipText(getToolTip("rc_jumpback_text"));
      rc_jumpback_button.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            final String tivoName = (String)tivo_rc.getSelectedItem();
            String mins_string = string.removeLeadingTrailingSpaces(rc_jumpback_text.getText());
            if (tivoName == null || tivoName.length() == 0)
               return;
            if (mins_string == null || mins_string.length() == 0)
               return;
            try {
               final int mins = Integer.parseInt(mins_string);
               class backgroundRun extends SwingWorker<Object, Object> {
                  protected Boolean doInBackground() {
                     Remote r = new Remote(tivoName);
                     if (r.success) {
                        JSONObject json = new JSONObject();
                        JSONObject reply = r.Command("position", json);
                        if (reply != null && reply.has("position")) {
                           try {
                              Long pos = reply.getLong("position");
                              pos -= (long)60000*mins;
                              if (pos < 0)
                                 pos = (long)0;
                              json.put("offset", pos);
                              r.Command("jump", json);
                           } catch (JSONException e) {
                              log.error("Skip minutes back failed - " + e.getMessage());
                           }
                        }
                        r.disconnect();
                     }
                     return null;
                  }
               }
               backgroundRun b = new backgroundRun();
               b.execute();
            } catch (NumberFormatException e1) {
               log.error("Illegal number of minutes specified: " + mins_string);
               return;
            }            
         }
      });
      rc_jumpback_text = new JTextField(15);
      rc_jumpback_text.setToolTipText(getToolTip("rc_jumpback_text"));
      rc_jumpback_text.setText("5");
      
      // Top panel
      JPanel rctop = new JPanel();
      rctop.setLayout(new BoxLayout(rctop, BoxLayout.LINE_AXIS));
      rctop.add(Box.createRigidArea(space_5));
      rctop.add(label_rc);
      rctop.add(Box.createRigidArea(space_5));
      rctop.add(tivo_rc);
      rctop.add(Box.createRigidArea(space_5));
      rctop.add(rc_hme_button);
      rctop.add(Box.createRigidArea(space_5));
      rctop.add(hme_rc);

      // Bottom panel
      JPanel rcbot = new JPanel();
      rcbot.setLayout(new BoxLayout(rcbot, BoxLayout.LINE_AXIS));
      rcbot.add(rc_jumpto_button);
      rcbot.add(Box.createRigidArea(space_5));
      rcbot.add(rc_jumpto_text);
      rcbot.add(Box.createRigidArea(space_5));
      rcbot.add(rc_jumpback_button);
      rcbot.add(Box.createRigidArea(space_5));
      rcbot.add(rc_jumpback_text);
      rcbot.add(Box.createRigidArea(space_5));
      rcbot.add(rc_jumpahead_button);
      rcbot.add(Box.createRigidArea(space_5));
      rcbot.add(rc_jumpahead_text);

      // Combine all RC panels together
      JPanel panel_rc = new JPanel(new BorderLayout());
      panel_rc.add(rctop, BorderLayout.PAGE_START);
      panel_rc.add(panel_controls, BorderLayout.CENTER);
      panel_rc.add(rcbot, BorderLayout.PAGE_END);
      
      // RC tab keyboard shortcuts without buttons
      for (int i=KeyEvent.VK_A; i<=KeyEvent.VK_Z; ++i) {
         AddPanelShortcut(panel_rc, "" + i, i, true, "" + Character.toChars(i)[0]);
      }
      Object[][] kb_shortcuts = new Object[][] {
         // NAME       Keyboard KeyEvent       isAscii action
         {"SPACE",     KeyEvent.VK_SPACE,      false, "forward"},
         {"BACKSPACE", KeyEvent.VK_BACK_SPACE, false, "reverse"},
         {"Shift8",    KeyEvent.VK_8,          true,  "*"},
         {"Shift-",    KeyEvent.VK_MINUS,      true,  "_"},
         {"Shift7",    KeyEvent.VK_7,          true,  "&"},
         {"COMMA",     KeyEvent.VK_COMMA,      true,  ","},
         {"NUMPAD0",   KeyEvent.VK_NUMPAD0,    true,  "0"},
         {"NUMPAD1",   KeyEvent.VK_NUMPAD1,    true,  "1"},
         {"NUMPAD2",   KeyEvent.VK_NUMPAD2,    true,  "2"},
         {"NUMPAD3",   KeyEvent.VK_NUMPAD3,    true,  "3"},
         {"NUMPAD4",   KeyEvent.VK_NUMPAD4,    true,  "4"},
         {"NUMPAD5",   KeyEvent.VK_NUMPAD5,    true,  "5"},
         {"NUMPAD6",   KeyEvent.VK_NUMPAD6,    true,  "6"},
         {"NUMPAD7",   KeyEvent.VK_NUMPAD7,    true,  "7"},
         {"NUMPAD8",   KeyEvent.VK_NUMPAD8,    true,  "8"},
         {"NUMPAD9",   KeyEvent.VK_NUMPAD9,    true,  "9"},
      };
      for (int i=0; i<kb_shortcuts.length; ++i) {
         AddPanelShortcut(
            panel_rc,
            (String)kb_shortcuts[i][0],
            (Integer)kb_shortcuts[i][1],
            (Boolean)kb_shortcuts[i][2],
            (String)kb_shortcuts[i][3]
         );
      }
      panel_rc.setFocusable(true);
      
      // Add all panels to tabbed panel
      tabbed_panel.add("ToDo", panel_todo);
      tabbed_panel.add("Season Passes", panel_sp);
      tabbed_panel.add("Won't Record", panel_cancel);
      tabbed_panel.add("Season Premieres", panel_premiere);
      tabbed_panel.add("Search", panel_search);
      tabbed_panel.add("Guide", panel_guide);
      tabbed_panel.add("Remote", panel_rc);
      tabbed_panel.add("Info", panel_info);
      
      // Init the tivo comboboxes
      setTivoNames();
            
      // Pack table columns
      tab_todo.packColumns(tab_todo.TABLE, 2);
      tab_guide.packColumns(tab_guide.TABLE, 2);
      tab_sp.packColumns(tab_sp.TABLE, 2);
      tab_cancel.packColumns(tab_sp.TABLE, 2);
      tab_search.packColumns(tab_search.TABLE, 2);
      if (tivo_count == 0) {
         log.warn("No Premieres currently enabled for Remote Control in kmttg configuration");
         return;
      }
   }
      
   public JTabbedPane getPanel() {
      return tabbed_panel;
   }
   
   // Submit remote SP request to Job Monitor
   public void SPListCB(String tivoName) {
      jobData job = new jobData();
      job.source      = tivoName;
      job.tivoName    = tivoName;
      job.type        = "remote";
      job.name        = "Remote";
      job.remote_sp   = true;
      job.sp          = tab_sp;
      jobMonitor.submitNewJob(job);
   }
   
   private void RC_infoCB(final String tivoName) {
      class backgroundRun extends SwingWorker<Object, Object> {
         protected Boolean doInBackground() {
            Remote r = new Remote(tivoName);
            if (r.success) {
               JSONObject json = new JSONObject();
               JSONObject reply = r.Command("sysInfo", json);
               if (reply != null && reply.has("bodyConfig")) {
                  try {
                     String info = "";
                     // System info
                     json = reply.getJSONArray("bodyConfig").getJSONObject(0);
                     if (json.has("userDiskSize") && json.has("userDiskUsed")) {
                        Float sizeGB = (float)json.getLong("userDiskSize")/(1024*1024);
                        Float pct = (float)100*json.getLong("userDiskUsed")/json.getLong("userDiskSize");
                        String pct_string = String.format("%s (%5.2f%%)", json.get("userDiskUsed"), pct);
                        String size_string = String.format("%s (%5.2f GB)", json.get("userDiskSize"), sizeGB);
                        json.put("userDiskSize", size_string);
                        json.put("userDiskUsed", pct_string);
                     }
                     if (json.has("bodyId")) {
                        info += String.format("%s\t\t%s\n", "tsn",
                           json.getString("bodyId").replaceFirst("tsn:", "")
                        );
                     }
                     String[] fields = {
                        "softwareVersion", "userDiskSize", "userDiskUsed", "parentalControlsState"
                     };
                     for (int i=0; i<fields.length; ++i) {
                        if (json.has(fields[i]))
                           info += String.format("%s\t%s\n", fields[i], json.get(fields[i]));
                     }
                     info += "\n";
                     
                     // Tuner info
                     reply = r.Command("tunerInfo", new JSONObject());
                     if (reply != null && reply.has("state")) {
                        for (int i=0; i<reply.getJSONArray("state").length(); ++i) {
                           json = reply.getJSONArray("state").getJSONObject(i);
                           info += String.format("tunerId\t\t%s\n", json.getString("tunerId"));
                           info += String.format("channelNumber\t%s (%s)\n",
                              json.getJSONObject("channel").getString("channelNumber"),
                              json.getJSONObject("channel").getString("callSign")
                           );
                           info += "\n";
                        }
                     }
                     
                     // Add info to text_info widget
                     text_info.setEditable(true);
                     text_info.setText(info);
                     text_info.setEditable(false);
                     tivo_info_data.put(tivoName, info);

                  } catch (JSONException e) {
                     log.error("RC_infoCB failed - " + e.getMessage());
                  }
               }
               r.disconnect();
            }
            return null;
         }
      }
      backgroundRun b = new backgroundRun();
      b.execute();
   }

   public String getGuideStartTime() {
      return (String)guide_start.getSelectedItem();
   }
   
   public int getGuideRange() {
      return (Integer)guide_range.getValue();
   }
   
   public String getTivoName(String tab) {
      if (tab.equals("todo"))
         return (String)tivo_todo.getSelectedItem();
      if (tab.equals("guide"))
         return (String)tivo_guide.getSelectedItem();
      if (tab.equals("sp"))
         return (String)tivo_sp.getSelectedItem();
      if (tab.equals("cancel"))
         return (String)tivo_cancel.getSelectedItem();
      if (tab.equals("search"))
         return (String)tivo_search.getSelectedItem();
      if (tab.equals("rc"))
         return (String)tivo_rc.getSelectedItem();
      if (tab.equals("info"))
         return (String)tivo_info.getSelectedItem();
      if (tab.equals("premiere"))
         return (String)tivo_premiere.getSelectedItem();
      return null;
   }
   
   public void setTivoName(String tab, String tivoName) {
      String current = getTivoName(tab);
      if ( ! tivoName.equals(current)) {
         if (tab.equals("todo"))
            tivo_todo.setSelectedItem(tivoName);
         if (tab.equals("guide"))
            tivo_guide.setSelectedItem(tivoName);
         if (tab.equals("sp"))
            tivo_sp.setSelectedItem(tivoName);
         if (tab.equals("cancel"))
            tivo_cancel.setSelectedItem(tivoName);
         if (tab.equals("search"))
            tivo_search.setSelectedItem(tivoName);
         if (tab.equals("rc"))
            tivo_rc.setSelectedItem(tivoName);
         if (tab.equals("info"))
            tivo_info.setSelectedItem(tivoName);
         if (tab.equals("premiere"))
            tivo_premiere.setSelectedItem(tivoName);
      }
   }
   
   public void clearTable(String tableName) {
      if (tableName.equals("sp")) {
         tab_sp.TABLE.clearSelection();
         tab_sp.clear();
      }
      if (tableName.equals("todo")) {
         tab_todo.TABLE.clearSelection();
         tab_todo.clear();
      }
      if (tableName.equals("guide")) {
         tab_guide.TABLE.clearSelection();
         tab_guide.clear();
      }
      if (tableName.equals("cancel")) {
         tab_cancel.TABLE.clearSelection();
         tab_cancel.clear();
      }
      if (tableName.equals("search")) {
         tab_search.TABLE.clearSelection();
         tab_search.clear();
      }
      if (tableName.equals("premiere")) {
         tab_premiere.TABLE.clearSelection();
         tab_premiere.clear();
      }
   }
   
   public void setTivoNames() { 
      tivo_count = 0;
      Stack<String> tivo_stack = config.getTivoNames();
      tivo_todo.removeAllItems();
      tivo_guide.removeAllItems();
      tivo_sp.removeAllItems();
      tivo_cancel.removeAllItems();
      tivo_search.removeAllItems();
      tivo_rc.removeAllItems();
      tivo_info.removeAllItems();
      tivo_premiere.removeAllItems();
      for (int i=0; i<tivo_stack.size(); ++i) {
         if (config.getRpcSetting(tivo_stack.get(i)).equals("1")) {
            tivo_count++;
            tivo_todo.addItem(tivo_stack.get(i));
            tivo_guide.addItem(tivo_stack.get(i));
            tivo_sp.addItem(tivo_stack.get(i));
            tivo_cancel.addItem(tivo_stack.get(i));
            tivo_search.addItem(tivo_stack.get(i));
            tivo_rc.addItem(tivo_stack.get(i));
            tivo_info.addItem(tivo_stack.get(i));
            tivo_premiere.addItem(tivo_stack.get(i));
         }
      }
      if (tivo_count > 0) {
         setHmeDestinations(getTivoName("rc"));
      }
   }
   
   private String[] getTivoNames(JComboBox component) {
      String[] names = new String[component.getItemCount()];
      for (int i=0; i<component.getItemCount(); ++i) {
         names[i] = (String)component.getItemAt(i);
      }
      return names;
   }
   
   // NOTE: This already called in swing worker, so no need to background
   private String[] getHmeDestinations(String tivoName) {
      Remote r = new Remote(tivoName);
      if (r.success) {
         r.debug = false;
         JSONObject json = new JSONObject();
         JSONObject result = r.Command("hmedestinations", json);
         JSONArray a;
         try {
            a = result.getJSONArray("uiDestinationInstance");
            if (a != null) {
               String[] hmeNames = new String[a.length()];
               for (int i=0; i<a.length(); ++i) {
                  String name = a.getJSONObject(i).getString("name");
                  hmeNames[i] = name;
                  hme.put(name, a.getJSONObject(i).getString("uri"));
               }
               return hmeNames;
            }
         } catch (JSONException e) {
            log.error("getHmeDestinations - " + e.getMessage());
         }
         r.disconnect();
      }
      return new String[0];
   }
   
   public void setHmeDestinations(final String tivoName) {
      // NOTE: Run in background mode so as not to slow down remotegui display
      class backgroundRun extends SwingWorker<Object, Object> {
         protected Object doInBackground() {
            String[] hmeNames = getHmeDestinations(tivoName);
            hme_rc.removeAllItems();
            for (int i=0; i<hmeNames.length; ++i)
               hme_rc.addItem(hmeNames[i]);
            return null;
         }
      }
      backgroundRun b = new backgroundRun();
      b.execute();
   }
   
   // Write channel info to a file
   // Columns are:
   // channelNumber, callSign, sourceType, isSelected
   public void saveChannelInfo(String tivoName) {
       try {
         JSONObject json;
         if (premiere_channel_info.containsKey(tivoName)) {
            String fileName = config.programDir + File.separator + tivoName + ".channels";
            log.warn("Saving channel info to file: " + fileName);
            BufferedWriter ofp = new BufferedWriter(new FileWriter(fileName));
            String eol = "\r\n";
            ofp.write("#channelNumber, callSign, sourceType, isSelected" + eol);
            for (int i=0; i<premiere_channel_info.get(tivoName).length(); ++i) {
               json = premiere_channel_info.get(tivoName).getJSONObject(i);
               ofp.write(json.getString("channelNumber"));
               ofp.write(", " + json.getString("callSign"));
               ofp.write(", " + json.getString("sourceType"));
               ofp.write(", " + json.getString("isSelected") + eol);
            } // for
            ofp.close();
         }
      } catch (Exception e1) {
         log.error("saveChannelInfo - " + e1.getMessage());
      }
   }
   
   // Read channel info from a file
   // Columns are:
   // channelNumber, callSign, sourceType, isSelected
   public void loadChannelInfo(String tivoName) {
      String fileName = config.programDir + File.separator + tivoName + ".channels";
      if (file.isFile(fileName)) {
         try {
            log.warn("Loading channel info from file: " + fileName);
            JSONArray a = new JSONArray();
            String line;
            Stack<Integer> selected = new Stack<Integer>();
            int count = 0;
            BufferedReader ifp = new BufferedReader(new FileReader(fileName));
            while ( (line=ifp.readLine()) != null ) {
               line = line.replaceFirst("^\\s*(.*$)", "$1");
               line = line.replaceFirst("^(.*)\\s*$", "$1");
               if (line.length() == 0) continue; // skip empty lines
               if (line.matches("^#.+")) continue; // skip comment lines
               String Fields[] = line.split(", ");
               JSONObject json = new JSONObject();
               json.put("channelNumber", Fields[0]);
               json.put("callSign", Fields[1]);
               json.put("sourceType", Fields[2]);
               json.put("isSelected", Fields[3]);
               if (Fields[3].equals("true"))
                  selected.add(count);
               a.put(json);
               count++;
            }
            ifp.close();
            if (a.length() > 0) {
               putChannelData(tivoName, a);
            }
            if (selected.size() > 0) {
               for (int i=0; i<selected.size(); ++i) {
                  premiere_channels.addSelectionInterval(selected.get(i), selected.get(i));
               }
            }
         } catch (Exception e1) {
            log.error("loadChannelInfo - " + e1.getMessage());
         }
      }
   }
   
   public Boolean updateSelectedChannels(String tivoName) {
      try {
         // Reset "isSelected" entries for premiere_channel_info for this TiVo
         if (premiere_channel_info.containsKey(tivoName)) {
            for (int i=0; i<premiere_channel_info.get(tivoName).length(); ++i) {
               premiere_channel_info.get(tivoName).getJSONObject(i).put("isSelected", "false");
            }
   
            // Set "isSelected" to true for selected ones
            int[] selected = premiere_channels.getSelectedIndices();
            if (selected.length < 1) {
               log.error("No channels selected in channel list for processing.");
               return false;
            }
            for (int i=0; i<selected.length; ++i) {
               premiere_channel_info.get(tivoName).getJSONObject(selected[i]).put("isSelected", "true");
            }
         } else {
            log.error("No channel information available - use Channels button to get list of channels");
            return false;
         }
      } catch (JSONException e1) {
         log.error("channelInfoToArray - " + e1.getMessage());
         return false;
      }
      return true;
   }
   
   // Return channel information for selected entries in channel list
   // NOTE: This is called from remote "premieres" task
   public JSONArray getSelectedChannelData(String tivoName) {
      JSONArray a = new JSONArray();
      if (premiere_channel_info.containsKey(tivoName)) {
         try {
            int[] selected = premiere_channels.getSelectedIndices();
            for (int i=0; i<selected.length; ++i) {
               a.put(premiere_channel_info.get(tivoName).getJSONObject(selected[i]));
            }
         } catch (JSONException e) {
            log.error("getSelectedChannelData - " + e.getMessage());
         }
      }
      return a;
   }
   
   // Populate channel list for given TiVo in Premieres tab
   // NOTE: This is called from remote "channels" task
   public void putChannelData(String tivoName, JSONArray channelInfo) {      
      // 1st save selected channels list in a hash for easy access
      int[] selected = premiere_channels.getSelectedIndices();
      Hashtable<String,Boolean> h = new Hashtable<String,Boolean>();
      for (int j=0; j<selected.length; ++j) {
         String channelNumber = premiere_model.get(selected[j]).toString();
         h.put(channelNumber, true);
      }
      
      // Now reset GUI list and global
      premiere_channel_info.put(tivoName, channelInfo);
      premiere_model.clear();
      try {
         String channelNumber, callSign;
         for (int i=0; i<channelInfo.length(); ++i) {
            channelNumber = channelInfo.getJSONObject(i).getString("channelNumber");
            callSign = channelInfo.getJSONObject(i).getString("callSign");
            premiere_model.add(i, channelNumber + "=" + callSign);
         }
         tabbed_panel.revalidate();
         
         // Re-select channels if available
         for (int k=0; k<premiere_model.getSize(); ++k) {
            channelNumber = premiere_model.get(k).toString();
            JSONObject json = premiere_channel_info.get(tivoName).getJSONObject(k);
            if (h.containsKey(channelNumber)) {
               premiere_channels.addSelectionInterval(k,k);
               json.put("isSelected", "true");
            } else {
               json.put("isSelected", "false");
            }
         }
      } catch (JSONException e) {
         log.error("putChannelData - " + e.getMessage());
      }
   }  
   
   // NOTE: This called as part of a background job
   public void TagPremieresWithSeasonPasses(JSONArray data) {
      String[] tivoNames = getTivoNames(tivo_premiere);
      for (int t=0; t<tivoNames.length; ++t) {
         Remote r = new Remote(tivoNames[t]);
         if (r.success) {
            JSONArray existing = r.SeasonPasses(null);
            if (existing != null) {
               // Add special json entry to mark entries that already have season passes
               String sp_title, entry_title;
               try {
                  for (int i=0; i<existing.length(); ++i) {
                     sp_title = existing.getJSONObject(i).getString("title");
                     for (int j=0; j<data.length(); ++j) {
                        entry_title = data.getJSONObject(j).getString("title");
                        if (sp_title.equals(entry_title)) {
                           // Add flag to JSON object indicating it's already a scheduled SP on this TiVo
                           data.getJSONObject(j).put("__SPscheduled__", true);
                        }
                     }
                  }
               } catch (JSONException e1) {
                  log.error("RC keyPressed - " + e1.getMessage());
               }
            }
            r.disconnect();
         }
      }
   }
   
   private static ImageIcon scale(Container dialog, Image src, double scale) {
      int w = (int)(scale*src.getWidth(dialog));
      int h = (int)(scale*src.getHeight(dialog));
      int type = BufferedImage.TYPE_INT_RGB;
      BufferedImage dst = new BufferedImage(w, h, type);
      Graphics2D g2 = dst.createGraphics();
      g2.drawImage(src, 0, 0, w, h, dialog);
      g2.dispose();
      return new ImageIcon(dst);
   }

   private JButton ImageButton(Container pane, String imageFile, double scale) {
      String f = config.programDir + File.separator + "rc_images" + File.separator + imageFile;
      if (file.isFile(f)) {
         ImageIcon image = new ImageIcon(f);
         JButton b = new JButton(scale(pane, image.getImage(),scale));
         b.setBackground(Color.black);
         b.setBorderPainted(false);
         disableSpaceAction(b);
         return b;
      }
      log.error("Installation issue: image file not found: " + f);
      return null;
   }
   
   private void AddButtonShortcut(JButton b, String actionName, int key) {
      InputMap inputMap = b.getInputMap(JButton.WHEN_IN_FOCUSED_WINDOW);
      int modifier = 0;
      if (actionName.startsWith("Shift"))
         modifier = ActionEvent.SHIFT_MASK;
      if (actionName.startsWith("Alt"))
         modifier = ActionEvent.ALT_MASK;
      inputMap.put(KeyStroke.getKeyStroke(key, modifier), actionName);
      b.getActionMap().put(actionName, new ClickAction(b));
   }
   
   private void disableSpaceAction(JButton b) {
      InputMap im = b.getInputMap();
      im.put(KeyStroke.getKeyStroke("pressed SPACE"), "none");
      im.put(KeyStroke.getKeyStroke("released SPACE"), "none");
   }
   
   private void AddPanelShortcut(JPanel p, String actionName, int key, Boolean isAscii, String command) {
      InputMap inputMap = p.getInputMap(JPanel.WHEN_IN_FOCUSED_WINDOW);
      int modifier = 0;
      if (actionName.startsWith("Shift"))
         modifier = ActionEvent.SHIFT_MASK;
      if (actionName.startsWith("Alt"))
         modifier = ActionEvent.ALT_MASK;
      KeyStroke k = KeyStroke.getKeyStroke(key, modifier);
      inputMap.put(k, actionName);
      p.getActionMap().put(actionName, new ClickAction(isAscii, command));
   }
   
   private void setMacroCB(JButton b, final String[] sequence) {
      b.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            final String tivoName = (String)tivo_rc.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               class backgroundRun extends SwingWorker<Object, Object> {
                  protected Object doInBackground() {
                     Remote r = new Remote(tivoName);
                     if (r.success) {
                        try {
                           JSONObject result;
                           for (int i=0; i<sequence.length; ++i) {
                              JSONObject json = new JSONObject();
                              if (sequence[i].matches("^[0-9]")) {
                                 json.put("event", "ascii");
                                 json.put("value", sequence[i].toCharArray()[0]);
                              } else {
                                 json.put("event", sequence[i]);
                              }
                              result = r.Command("keyEventSend", json);
                              if (result == null) break;
                           }
                        } catch (JSONException e1) {
                           log.error("Macro CB - " + e1.getMessage());
                        }
                        r.disconnect();
                     }
                     return null;
                  }
               }
               backgroundRun b = new backgroundRun();
               b.execute();
            }
         }
      });
   }

   private JLabel ImageLabel(Container pane, String imageFile, double scale) {
      String f = config.programDir + File.separator + "rc_images" + File.separator + imageFile;
      if (file.isFile(f)) {
         ImageIcon image = new ImageIcon(f);
         JLabel l = new JLabel(scale(pane, image.getImage(),scale));
         l.setBackground(Color.black);
         return l;
      }
      log.error("Installation issue: image file not found: " + f);
      return null;
   }
   
   // This handles key presses in RC panel not bound to buttons
   private void RC_keyPress(final Boolean isAscii, final String command) {
      class backgroundRun extends SwingWorker<Object, Object> {
         protected Object doInBackground() {
            String tivoName = (String)tivo_rc.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               Remote r = new Remote(tivoName);
               if (r.success) {
                  try {
                     JSONObject json = new JSONObject();
                     if (isAscii) {
                        json.put("event", "ascii");
                        json.put("value", command.toCharArray()[0]);
                     }
                     else {
                        json.put("event", command);
                     }
                     r.Command("keyEventSend", json);
                  } catch (JSONException e1) {
                     log.error("RC keyPressed - " + e1.getMessage());
                  }
                  r.disconnect();
               }
            }
            return null;
         }
      }
      backgroundRun b = new backgroundRun();
      b.execute();
   }
      
   public String getToolTip(String component) {
      debug.print("component=" + component);
      String text = "";
      if (component.equals("tivo_todo")) {
         text = "Select TiVo for which to retrieve To Do list.";
      }
      else if (component.equals("refresh_todo")){
         text = "<b>Refresh</b><br>";
         text += "Refresh To Do list of selected TiVo.";
      }
      else if (component.equals("cancel_todo")){
         text = "<b>Cancel</b><br>";
         text += "Cancel ToDo recordings selected in table below. As a shortcut you can also use the<br>";
         text += "<b>Delete</b> keyboard button to cancel selected shows in the table as well.";
      }
      if (component.equals("tivo_guide")) {
         text = "Select TiVo for which to retrieve guide listings.";
      }
      if (component.equals("guide_start")) {
         text = "<b>Start</b><br>";
         text += "Select guide start time to use when obtaining listings.<br>";
         text += "NOTE: If you are inside a channel folder when you change this setting<br>";
         text += "the guide listings will automatically update to new date.";
      }
      if (component.equals("guide_range")) {
         text = "<b>Range</b><br>";
         text += "Select how many hours of guide information to retrieve.<br>";
         text += "NOTE: You can type in a number (up to 12) and then press enter to update.<br>";
         text += "NOTE: If you are inside a channel folder when you change this setting<br>";
         text += "the guide listings will automatically update to new date.";
      }
      else if (component.equals("refresh_guide")){
         text = "<b>Channels</b><br>";
         text += "Retrieve list of channels for this TiVo if necessary.";
      }
      else if (component.equals("refresh_search_folder")){
         text = "<b>Back</b><br>";
         text += "Return to top level folder view.";
      }
      else if (component.equals("guide_record")){
         text = "<b>Record</b><br>";
         text += "Schedule to record selected individual show(s) in table on specified TiVo.<br>";
         text += "NOTE: The scheduling uses low priority such that if there are no time slots available<br>";
         text += "then the scheduling will fail. i.e. Only schedules to record if there are no conflicts.";
      }
      else if (component.equals("guide_recordSP")){
         text = "<b>Season Pass</b><br>";
         text += "Create a season pass for show selected in table below.";
      }
      else if (component.equals("tivo_cancel")) {
         text = "Select TiVo for which to display list of shows that will not record.";
      }
      else if (component.equals("refresh_cancel_top")){
         text = "<b>Refresh</b><br>";
         text += "Refresh list for selected TiVo. Click on a folder in table below to see<br>";
         text += "all shows that will not record for reason matching the folder name.";
      }
      else if (component.equals("record_cancel")){
         text = "<b>Record</b><br>";
         text += "Schedule to record selected individual show(s) in table on specified TiVo.<br>";
         text += "NOTE: The scheduling uses low priority such that if there are no time slots available<br>";
         text += "then the scheduling will fail. i.e. Only schedules to record if there are no conflicts.";
      }
      else if (component.equals("refresh_cancel_folder")){
         text = "<b>Back</b><br>";
         text += "Return to top level folder view.";
      }
      else if (component.equals("tivo_search")) {
         text = "Select TiVo for which to perform search with.";
      }
      else if (component.equals("button_search")) {
         text = "<b>Search</b><br>";
         text += "Start a search of specified keywords.";
      }
      else if (component.equals("text_search")) {
         text = "Specify keywords to search for. NOTE: Multiple words mean logical AND operation.<br>";
         text += "To shorten search times include more words in the search. Very generic/short<br>";
         text += "keywords will lead to much longer search times.";
      }
      else if (component.equals("max_search")) {
         text = "<b>Max</b><br>";
         text += "Specify maximum number of hits to limit search to.<br>";
         text += "Depending on keywords the higher you set this limit the longer the search will take.";
      }
      else if (component.equals("record_search")) {
         text = "<b>Record</b><br>";
         text += "Schedule a one time recording of show selected in table below.";
      }
      else if (component.equals("record_sp_search")) {
         text = "<b>Season Pass</b><br>";
         text += "Create a season pass for show selected in table below.<br>";
         text += "NOTE: The scheduling uses low priority such that if there are no time slots available<br>";
         text += "then the scheduling will fail. i.e. Only schedules to record if there are no conflicts.";
      }
      else if (component.equals("refresh_search_top")){
         text = "<b>Refresh</b><br>";
         text += "Click on a folder in table below to show related search matches.";
      }
      else if (component.equals("refresh_search_folder")){
         text = "<b>Back</b><br>";
         text += "Return to top level folder view.";
      }
      else if (component.equals("tivo_sp")) {
         text = "Select TiVo for which to retrieve Season Passes list.";
      }
      else if (component.equals("tivo_rc")) {
         text = "Select which TiVo you want to control.";
      }
      else if (component.equals("tivo_premiere")) {
         text = "Select TiVo to use for finding shows that are Season or Series premieres.";
      }
      else if (component.equals("refresh_premiere")){
         text = "<b>Search</b><br>";
         text += "Find season & series premieres on all the channels selected in the<br>";
         text += "channels list. This saves currently selected channel list to file as well.<br>";
         text += "<b>NOTE: TiVo guide data does not have episode number information for some shows,<br>";
         text += "and hence those shows cannot be identified as premieres.</b>.";
      }
      else if (component.equals("premiere_channels_update")){
         text = "<b>Update Channels</b><br>";
         text += "Use this button to obtain list of channels received from selected TiVo.<br>";
         text += "Once you have the list then you can select which channels to include in the<br>";
         text += "search for season & series premieres (in list to right of the table below).";
      }
      else if (component.equals("premiere_channels")) {
         text = "Select which channels you want to include in the search for Season & Series<br>";
         text += "premieres. NOTE: The more channels you include the longer the search will take.<br>";
         text += "Use shift and left mouse button to select a range of channels or control + left<br>";
         text += "mouse button to add individual channels to selected set.";
      }
      else if (component.equals("record_premiere")){
         text = "<b>Record</b><br>";
         text += "Schedule season passes selected in the table below on selected TiVo.<br>";
         text += "Note that kmttg will attempt to avoid duplicated season passes on the destination TiVo by<br>";
         text += "checking against the current set of season passes already on the TiVo.<br>";
         text += "NOTE: By default the following settings are used to record Season Passes, so you may want<br>";
         text += "adjust the SP settings on the TiVo after scheduling:<br>";
         text += "lowest priority, recordingQuality=best, maxRecordings=25, keepBehavior=fifo, first run only";
      }
      else if (component.equals("refresh_sp")){
         text = "<b>Refresh</b><br>";
         text += "Refresh Season Pass list of selected TiVo. Note that by selecting 1 or more rows in the<br>";
         text += "table and using keyboard <b>Delete</b> button you can unsubscribe Season Passes.";
      }
      else if (component.equals("cancel_sp")){
         text = "<b>Refresh</b><br>";
         text += "Refresh Not Record list of selected TiVo. Click on folder in table to see all<br>";
         text += "entries associated with it.";
      }
      else if (component.equals("save_sp")){
         text = "<b>Save</b><br>";
         text += "Save the currently displayed Season Pass list to a file. This file can then be loaded<br>";
         text += "at a later date into this table, then entries from the table can be copied to your TiVos<br>";
         text += "if desired by selecting entries in the table and clicking on <b>Copy</b> button.<br>";
         text += "i.e. This is a way to backup your season passes.";
      }
      else if (component.equals("load_sp")){
         text = "<b>Load</b><br>";
         text += "Load a previously saved Season Pass list from a file. When loaded the table will have a<br>";
         text += "<b>Loaded: </b> prefix in the TITLE column indicating that these were loaded from a file<br>";
         text += "to distinguish from normal case where they were obtained from displayed TiVo name.<br>";
         text += "Note that loaded season passes can then be copied to TiVos by selecting the TiVo you want to<br>";
         text += "copy to, then selecting rows in the table you want to copy and then clicking on the <b>Copy</b><br>";
         text += "button.";
      }
      else if (component.equals("copy_sp")){
         text = "<b>Copy</b><br>";
         text += "This is used to copy <b>loaded</b>season passes in the table to one of your TiVos.<br>";
         text += "Select the TiVo you want to copy to and then select rows in the table that you want copied,<br>";
         text += "then press this button to perform the copy.<br>";
         text += "If you want to copy from another Premiere, first switch to that Premiere and save its<br>";
         text += "season passes to a file. Then switch to destination Premiere and load the file you just saved.<br>";
         text += "Now you can select entries in the table and use this button to copy to destination Premiere.<br>";
         text += "Note that kmttg will attempt to avoid duplicated season passes on the destination TiVo by<br>";
         text += "checking against the current set of season passes already on the TiVo.";
      }
      else if (component.equals("modify_sp")){
         text = "<b>Modify</b><br>";
         text += "This is used to modify a season passes selected in the Season Pass table.<br>";
         text += "Select the season pass you want to modify in the table and then press this button to bring<br>";
         text += "up a dialog with season pass options that can be modified.";
      }
      else if (component.equals("reorder_sp")){
         text = "<b>Re-order</b><br>";
         text += "This is used to change priority order of season passes on selected TiVo to match the current<br>";
         text += "order displayed in the table. In order to change row order in the table you can use the mouse<br>";
         text += "to drag and drop rows to new locations. You can also select a row in the table and use the keyboard<br>";
         text += "<b>Up</b> and <b>Down</b> keys to move the row up and down. Once you are happy with priority order<br>";
         text += "displayed in the table use this button to have kmttg change the priority order on your TiVo.";
      }
      else if (component.equals("tivo_rnpl")) {
         text = "Select TiVo for which to retrieve My Shows list.<br>";
         text += "NOTE: You can select items in table to PLAY or DELETE<br>";
         text += "using <b>Space bar</b> to play, <b>Delete</b> button to delete.<br>";
         text += "NOTE: Only 1 item can be deleted or played at a time.";
      }
      else if (component.equals("refresh_rnpl")){
         text = "<b>Refresh</b><br>";
         text += "Refresh My Shows list of selected TiVo.<br>";
         text += "NOTE: You can select items in table to PLAY or DELETE<br>";
         text += "using <b>Space bar</b> to play, <b>Delete</b> button to delete.<br>";
         text += "NOTE: Only 1 item can be deleted or played at a time.";
      }
      else if (component.equals("tab_rnpl")) {
         text = "NOTE: You can select items in table to PLAY or DELETE<br>";
         text += "using <b>Space bar</b> to play, <b>Delete</b> button to delete.<br>";
         text += "NOTE: Only 1 item can be deleted or played at a time.";
      }
      else if (component.equals("refresh_info")){
         text = "<b>Refresh</b><br>";
         text += "Retrieve system information for selected TiVo.";
      }
      else if (component.equals("rc_jumpto_text")) {
         text = "<b>Jump to minute</b><br>";
         text += "Set playback position to exactly this number of minutes into the show.";
      }
      else if (component.equals("rc_jumpahead_text")) {
         text = "<b>Skip minutes ahead</b><br>";
         text += "Set playback position this number of minutes ahead of current position.";
      }
      else if (component.equals("rc_jumpback_text")) {
         text = "<b>Skip minutes back</b><br>";
         text += "Set playback position this number of minutes behind current position.";
      }
      else if (component.equals("rc_hme_button")) {
         text = "<b>HME Jump</b><br>";
         text += "Jump to the specified HME application for the selected TiVo.";
      }
      else if (component.equals("hme_rc")) {
         text = "Select which HME application you want to jump to for selected TiVo.";
      }
      else if (component.equals("channelUp")) {
         text = "pg up";
      }
      else if (component.equals("channelDown")) {
         text = "pg down";
      }
      else if (component.equals("left")) {
         text = "left arrow";
      }
      else if (component.equals("zoom")) {
         text = "Alt z";
      }
      else if (component.equals("tivo")) {
         text = "Alt t";
      }
      else if (component.equals("up")) {
         text = "up arrow";
      }
      else if (component.equals("select")) {
         text = "Alt s";
      }
      else if (component.equals("down")) {
         text = "down arrow";
      }
      else if (component.equals("liveTv")) {
         text = "Alt l";
      }
      else if (component.equals("info")) {
         text = "Alt i";
      }
      else if (component.equals("right")) {
         text = "right arrow";
      }
      else if (component.equals("guide")) {
         text = "Alt g";
      }
      else if (component.equals("num1")) {
         text = "1";
      }
      else if (component.equals("num2")) {
         text = "2";
      }
      else if (component.equals("num3")) {
         text = "3";
      }
      else if (component.equals("num4")) {
         text = "4";
      }
      else if (component.equals("num5")) {
         text = "5";
      }
      else if (component.equals("num6")) {
         text = "6";
      }
      else if (component.equals("num7")) {
         text = "7";
      }
      else if (component.equals("num8")) {
         text = "8";
      }
      else if (component.equals("num9")) {
         text = "9";
      }
      else if (component.equals("clear")) {
         text = "delete";
      }
      else if (component.equals("num0")) {
         text = "0";
      }
      else if (component.equals("enter")) {
         text = "enter";
      }
      else if (component.equals("actionA")) {
         text = "Alt a";
      }
      else if (component.equals("actionB")) {
         text = "Alt b";
      }
      else if (component.equals("actionC")) {
         text = "Alt c";
      }
      else if (component.equals("actionD")) {
         text = "Alt d";
      }
      else if (component.equals("thumbsDown")) {
         text = "KP -";
      }
      else if (component.equals("reverse")) {
         text = "&lt";
      }
      else if (component.equals("replay")) {
         text = "(";
      }
      else if (component.equals("play")) {
         text = "]";
      }
      else if (component.equals("pause")) {
         text = "[";
      }
      else if (component.equals("slow")) {
         text = "|";
      }
      else if (component.equals("record")) {
         text = "Alt r";
      }
      else if (component.equals("thumbsUp")) {
         text = "KP +";
      }
      else if (component.equals("forward")) {
         text = "&gt";
      }
      else if (component.equals("advance")) {
         text = ")";
      }      
      else if (component.equals("sps9s")){
         text = "<b>Clock: SPS9S</b><br>";
         text += "Select, Play, Select, 9, Select, Clear<br>";
         text += "Toggle on screen clock on bottom right corner.<br>";
         text += "Should be used when watching live tv or video playback.";
      }
      else if (component.equals("sps30s")){
         text = "<b>30ss: SPS30S</b><br>";
         text += "Select, Play, Select, 3, 0, Select, Clear<br>";
         text += "Toggle 30 sec skip binding of advance button.<br>";
         text += "Should be used when watching live tv or video playback.";
      }
      else if (component.equals("spsps")){
         text = "<b>Banner: SPSPS</b><br>";
         text += "Select, Play, Select, Pause, Select, Play<br>";
         text += "Toggle 'clear banner quickly' setting.<br>";
         text += "Should be used when watching live tv or video playback.<br>";
         text += "NOTE: Before enable you need to pause program and hide pause banner first and<br>.";
         text += "then resume play, then press this button.";
      }
      else if (component.equals("standby")){
         text = "<b>Toggle standby</b><br>";
         text += "Toggle standby mode. In off mode audio/video outputs are disabled on the TiVo<br>";
         text += "and possible recording interruptions by Emergency Alert System (EAS) are avoided.";
      }
      else if (component.equals("toggle_cc")){
         text = "<b>Toggle CC</b><br>";
         text += "Toggle closed caption display.<br>";
         text += "NOTE: Assumes initial state of off.<br>";
         text += "NOTE: Actually uses 'telnet' interface rather than iPad protocol.";
      }
      if (text.length() > 0) {
         text = "<html>" + text + "</html>";
      }
      return text;
   }

}
