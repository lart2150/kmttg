package com.tivo.kmttg.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
import java.util.Arrays;
import java.util.Date;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Stack;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
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

import org.jdesktop.swingx.JXTable;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.main.telnet;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class remotegui {
   public JTabbedPane tabbed_panel = null;
   
   private todoTable tab_todo = null;
   private JComboBox tivo_todo = null;
   public JLabel label_todo = null;
   
   private guideTable tab_guide = null;
   public  JButton refresh_guide = null;
   private JComboBox tivo_guide = null;
   private JComboBox guide_start = null;
   private JCheckBox guide_channels = null;
   public  int guide_range = 12; // Number of hours to show in guide at a time
   private int guide_hour_increment = 12; // Number of hours for date increment
   private int guide_total_range = 11;    // Number of days
   private JButton guide_manual_record = null;
   
   private spTable tab_sp = null;
   private JComboBox tivo_sp = null;
   public  spOptions spOpt = new spOptions();
   public  recordOptions recordOpt = new recordOptions();
   public  wlOptions wlOpt = new wlOptions();
   private mRecordOptions mRecordOpt = new mRecordOptions();
   
   //private JComboBox tivo_web = null;
   //private JButton send_web = null;
   //private JTextField url_web = null;
   //public  JComboBox bookmark_web = null;
   //private JComboBox type_web = null;
   
   private JComboBox tivo_info = null;
   private JButton reboot_info = null;
   JTextPane text_info = null;
   private Hashtable<String,String> tivo_info_data = new Hashtable<String,String>();
   public Hashtable<String, JButton> buttons = new Hashtable<String, JButton>();
   private LinkedHashMap<String,String> SPS = new LinkedHashMap<String,String>();
   
   private cancelledTable tab_cancel = null;
   private JComboBox tivo_cancel = null;
   public JButton refresh_cancel = null;
   private JButton autoresolve = null;
   public JLabel label_cancel = null;
   public JCheckBox includeHistory_cancel = null;
   
   private deletedTable tab_deleted = null;
   private JComboBox tivo_deleted = null;
   public JButton refresh_deleted = null;
   public JLabel label_deleted = null;
   
   private JComboBox tivo_premiere = null;
   private JComboBox premiere_days = null;
   private premiereTable tab_premiere = null;
   private JList premiere_channels = null;
   private DefaultListModel premiere_model = new DefaultListModel();
   public Hashtable<String,JSONArray> premiere_channel_info = new Hashtable<String,JSONArray>();
   
   private JComboBox tivo_search = null;
   public searchTable tab_search = null;
   private JTextField text_search = null;
   public JButton button_search = null;
   public JSpinner max_search = null;
   public JCheckBox extendedSearch = null;
   public JCheckBox includeFree = null;
   public JCheckBox includePaid = null;
   public JCheckBox includeVod = null;
   public Hashtable<String,JSONArray> search_info = new Hashtable<String,JSONArray>();
   private AdvSearch advSearch = new AdvSearch();
   private JButton search_manual_record = null;

   private JComboBox tivo_rc = null;
   private JComboBox hme_rc = null;
   private JComboBox hme_sps = null;
   private JTextField rc_jumpto_text = null;
   private JTextField rc_jumpahead_text = null;
   private JTextField rc_jumpback_text = null;
   private Boolean cc_state = false;
   
   // These buttons selectively disabled
   public JButton cancel_todo = null;
   public JButton modify_todo = null;
   private JButton reorder_sp = null;
   public JButton record_cancel = null;
   public JButton explain_cancel = null;
   public JButton record_premiere = null;
   public JButton recordSP_premiere = null;
   public JButton wishlist_premiere = null;
   public JButton record_search = null;    
   public JButton recordSP_search = null;    
   public JButton wishlist_search = null;    
   public JButton record_guide = null;
   public JButton recordSP_guide = null;
   public JButton wishlist_guide = null;
   public JButton recover_deleted = null;
   public JButton permDelete_deleted = null;  
   private JButton rc_hme_button = null;
   private JButton rc_sps_button = null;
   private JButton rc_jumpto_button = null;
   private JButton rc_jumpahead_button = null;
   private JButton rc_jumpback_button = null;
   public JButton copy_sp = null;
   public JButton conflicts_sp = null;
   public JButton modify_sp = null;
   public JButton upcoming_sp = null;
   
   private Hashtable<String,JSONArray> all_todo = new Hashtable<String,JSONArray>();
   private long all_todo_time = 0;
   
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
   
   private class CustomButton extends JButton {
      private static final long serialVersionUID = 1L;
      public CustomButton(ImageIcon image) {
         super(image);
         setBackground(Color.black);
         setContentAreaFilled(false);
      }
      public CustomButton(String label, String toolTip, String[] macro) {
         super(label);
         setToolTipText(getToolTip(toolTip));
         if (macro != null)
            setMacroCB(this, macro);
         setBackground(Color.black);
         setForeground(Color.white);
         setContentAreaFilled(false);
      }
      protected void paintComponent(Graphics g) {
         if (getModel().isArmed()) {
            setContentAreaFilled(true);
            g.setColor(Color.lightGray);
         } else {
            setContentAreaFilled(false);
            g.setColor(getBackground());
         }
         super.paintComponent(g);
      }
      protected void paintBorder(Graphics g) {
         g.setColor(getBackground());
      }
   }
   

   remotegui(JFrame frame) {      
      Browser = new JFileChooser(config.programDir);
      Browser.setMultiSelectionEnabled(false);
      
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
            String selected = getCurrentTabName();
            if (selected.equals("Search")) {
               // Set focus on text_search field
               text_search.requestFocusInWindow();
            }
            if (selected.equals("Guide")) {
               // Reset date range in Guide start time combo box
               tab_guide.setComboBoxDates(guide_start, guide_hour_increment, guide_total_range);
            }
            if (selected.equals("Remote")) {
               // Set focus on tabbed_panel
               tabbed_panel.requestFocusInWindow();
            }
         }
      });

      // Want arrow keys to click corresponding buttons
      class ButtonPressed extends AbstractAction {
         private static final long serialVersionUID = 1L;
         private String name;
         public ButtonPressed(String name) {
            this.name = name;
         }
         public void actionPerformed(ActionEvent e) {
            if (getCurrentTabName().equals("Remote"))
               buttons.get(name).doClick();
        }
      }
      String []arrows = {"RIGHT", "LEFT", "UP", "DOWN"};
      for (int i=0; i<arrows.length; ++i) {
         tabbed_panel.getInputMap().put(KeyStroke.getKeyStroke(arrows[i]), arrows[i]);
         tabbed_panel.getActionMap().put(arrows[i], new ButtonPressed(arrows[i]));
      }
            
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
                TableUtil.clear(tab_todo.TABLE);
                label_todo.setText("");
                String tivoName = getTivoName("todo");
                updateButtonStates(tivoName, "ToDo");
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
            TableUtil.clear(tab_todo.TABLE);
            label_todo.setText("");
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

      cancel_todo = new JButton("Cancel");
      cancel_todo.setToolTipText(getToolTip("cancel_todo"));
      cancel_todo.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            tab_todo.DeleteCB();
         }
      });

      modify_todo = new JButton("Modify");
      modify_todo.setToolTipText(getToolTip("modify_todo"));
      modify_todo.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String tivoName = (String)tivo_todo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               tab_todo.recordSingle(tivoName);
            }
         }
      });

      JButton export_todo = new JButton("Export ...");
      export_todo.setToolTipText(getToolTip("export_todo"));
      export_todo.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            final String tivoName = (String)tivo_todo.getSelectedItem();
            Browser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            Browser.setFileFilter(new FileFilterSP(".csv"));
            Browser.setSelectedFile(
               new File(
                  config.programDir + File.separator + tivoName +
                  "_todo_" + TableUtil.currentYearMonthDay() + ".csv"
               )
            );
            int result = Browser.showDialog(config.gui.getJFrame(), "Export to csv file");
            if (result == JFileChooser.APPROVE_OPTION) {               
               class backgroundRun extends SwingWorker<Object, Object> {
                  protected Object doInBackground() {
                     File file = Browser.getSelectedFile();
                     log.warn("Exporting '" + tivoName + "' todo list to csv file: " + file.getAbsolutePath());
                     Remote r = config.initRemote(tivoName);
                     if (r.success) {
                        r.TodoExportCSV(file);
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
      
      label_todo = new JLabel();
      
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
      row1_todo.add(Box.createRigidArea(space_5));
      row1_todo.add(modify_todo);
      row1_todo.add(Box.createRigidArea(space_5));
      row1_todo.add(export_todo);
      row1_todo.add(Box.createRigidArea(space_5));
      row1_todo.add(label_todo);
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
                   TableUtil.clear(tab_guide.TABLE);
                   String tivoName = getTivoName("guide");
                   updateButtonStates(tivoName, "Guide");
                   if (tab_guide.tivo_data.containsKey(tivoName))
                      tab_guide.AddRows(tivoName, tab_guide.tivo_data.get(tivoName));
                }
            }
         }
      });
      tivo_guide.setToolTipText(getToolTip("tivo_guide"));
      
      guide_channels = new JCheckBox("All", false);
      guide_channels.setToolTipText(getToolTip("guide_channels"));
      
      JLabel guide_start_label = new JLabel("Start");
      guide_start = new javax.swing.JComboBox();
      guide_start.setToolTipText(getToolTip("guide_start"));
      guide_start.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
             if (e.getStateChange() == ItemEvent.SELECTED) {
                if (tab_guide.inFolder) {
                   String start = (String)guide_start.getSelectedItem();
                   if (start != null && start.length() > 0) {
                      tab_guide.updateFolder(start, guide_range);
                   }
                }
            }
         }
      });

      refresh_guide = new JButton("Channels");
      refresh_guide.setMargin(new Insets(1,1,1,1));
      refresh_guide.setToolTipText(getToolTip("refresh_guide"));
      refresh_guide.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            if (tab_guide.inFolder) {
               // Return from inside a folder to top level table display
               tab_guide.Refresh((JSONArray)null);
               tab_guide.setFolderState(false);
               if (tab_guide.folderEntryNum >= 0)
                  tab_guide.SelectFolder(tab_guide.folderName);
            } else {
               // At top level => Update current folder contents
               TableUtil.clear(tab_guide.TABLE);
               String tivoName = (String)tivo_guide.getSelectedItem();
               if (tivoName != null && tivoName.length() > 0) {
                  // Obtain and display channel list
                  tab_guide.updateChannels(tivoName);
               }
            }
         }
      });

      JButton export_channels = new JButton("Export ...");
      export_channels.setMargin(new Insets(1,1,1,1));
      export_channels.setToolTipText(getToolTip("export_channels"));
      export_channels.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            final String tivoName = (String)tivo_guide.getSelectedItem();
            Browser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            Browser.setFileFilter(new FileFilterSP(".csv"));
            Browser.setSelectedFile(new File(config.programDir + File.separator + tivoName + "_channels.csv"));
            int result = Browser.showDialog(config.gui.getJFrame(), "Export to csv file");
            if (result == JFileChooser.APPROVE_OPTION) {               
               class backgroundRun extends SwingWorker<Object, Object> {
                  protected Object doInBackground() {
                     File file = Browser.getSelectedFile();
                     log.warn("Exporting '" + tivoName + "' channel list to csv file: " + file.getAbsolutePath());
                     Remote r = config.initRemote(tivoName);
                     if (r.success) {
                        r.ChannelLineupCSV(file);
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

      record_guide = new JButton("Record");
      record_guide.setMargin(new Insets(1,1,1,1));
      record_guide.setToolTipText(getToolTip("guide_record"));
      record_guide.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            if (tab_guide.inFolder) {
               String tivoName = (String)tivo_guide.getSelectedItem();
               if (tivoName != null && tivoName.length() > 0) {
                  tab_guide.recordSingle(tivoName);
               }
            }
         }
      });

      recordSP_guide = new JButton("Season Pass");
      recordSP_guide.setMargin(new Insets(1,1,1,1));
      recordSP_guide.setToolTipText(getToolTip("guide_recordSP"));
      recordSP_guide.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            if (tab_guide.inFolder) {
               String tivoName = (String)tivo_guide.getSelectedItem();
               if (tivoName != null && tivoName.length() > 0) {
                  tab_guide.recordSP(tivoName);
               }
            }
         }
      });
      
      wishlist_guide = new JButton("WL");
      wishlist_guide.setMargin(new Insets(1,1,1,1));
      wishlist_guide.setToolTipText(getToolTip("wishlist_search"));
      wishlist_guide.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String tivoName = (String)tivo_guide.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               createWishlist(tivoName, tab_guide.TABLE);
            }
         }
      });
      
      guide_manual_record = new JButton("MR");
      guide_manual_record.setMargin(new Insets(1,1,1,1));
      guide_manual_record.setToolTipText(getToolTip("guide_manual_record"));
      guide_manual_record.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String tivoName = (String)tivo_guide.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               mRecordOpt.promptUser(tivoName);
            }
         }
      });

      JButton guide_refresh_todo = new JButton("Refresh ToDo");
      guide_refresh_todo.setMargin(new Insets(1,1,1,1));
      guide_refresh_todo.setToolTipText(getToolTip("guide_refresh_todo"));
      guide_refresh_todo.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String tivoName = (String)tivo_guide.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               class backgroundRun extends SwingWorker<Object, Object> {
                  protected Object doInBackground() {
                     log.warn("Refreshing ToDo list for Guide entries...");
                     all_todo = getTodoLists();
                     log.warn("Refresh ToDo list for Guide entries completed.");
                     return null;
                  }
               }
               backgroundRun b = new backgroundRun();
               b.execute();
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
      row1_guide.add(guide_channels);
      row1_guide.add(Box.createRigidArea(space_5));
      row1_guide.add(refresh_guide);
      row1_guide.add(Box.createRigidArea(space_5));
      row1_guide.add(export_channels);
      row1_guide.add(Box.createRigidArea(space_5));
      row1_guide.add(record_guide);
      row1_guide.add(Box.createRigidArea(space_5));
      row1_guide.add(recordSP_guide);
      row1_guide.add(Box.createRigidArea(space_5));
      row1_guide.add(wishlist_guide);
      row1_guide.add(Box.createRigidArea(space_5));
      row1_guide.add(guide_manual_record);
      row1_guide.add(Box.createRigidArea(space_5));
      row1_guide.add(guide_refresh_todo);
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
                TableUtil.clear(tab_sp.TABLE);
                String tivoName = getTivoName("sp");
                updateButtonStates(tivoName, "Season Passes");
                if (tab_sp.tivo_data.containsKey(tivoName))
                   tab_sp.AddRows(tivoName, tab_sp.tivo_data.get(tivoName));
                tab_sp.updateLoadedStatus();
            }
         }
      });
      tivo_sp.setToolTipText(getToolTip("tivo_sp"));

      JButton refresh_sp = new JButton("Refresh");
      refresh_sp.setMargin(new Insets(1,1,1,1));
      refresh_sp.setToolTipText(getToolTip("refresh_sp"));
      refresh_sp.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            // Refresh SP list
            TableUtil.clear(tab_sp.TABLE);
            tab_sp.setLoaded(false);
            String tivoName = (String)tivo_sp.getSelectedItem();
            SPListCB(tivoName);
         }
      });
      
      JButton save_sp = new JButton("Save...");
      save_sp.setMargin(new Insets(1,1,1,1));
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
                  Browser.setFileFilter(new FileFilterSP(".sp"));
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
      load_sp.setMargin(new Insets(1,1,1,1));
      load_sp.setToolTipText(getToolTip("load_sp"));
      load_sp.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            // Load SP data from a file
            Browser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            Browser.setFileFilter(new FileFilterSP(".sp"));
            int result = Browser.showDialog(config.gui.getJFrame(), "Load from file");
            if (result == JFileChooser.APPROVE_OPTION) {               
               File file = Browser.getSelectedFile();
               tab_sp.SPListLoad(file.getAbsolutePath());
            }
         }
      });         
      
      JButton export_sp = new JButton("Export...");
      export_sp.setMargin(new Insets(1,1,1,1));
      export_sp.setToolTipText(getToolTip("export_sp"));
      export_sp.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            // Export SP data to a file in csv format
            String tivoName = (String)tivo_sp.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               if (tab_sp.isTableLoaded()) {
                  log.error("Cannot export loaded Season Passes");
                  return;
               }  else {
                  Browser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                  Browser.setFileFilter(new FileFilterSP(".csv"));
                  Browser.setSelectedFile(new File(config.programDir + File.separator + tivoName + "_sp" + ".csv")
                  );
                  int result = Browser.showDialog(config.gui.getJFrame(), "Export to csv file");
                  if (result == JFileChooser.APPROVE_OPTION) {               
                     File file = Browser.getSelectedFile();
                     tab_sp.SPListExport(tivoName, file.getAbsolutePath());
                  }
               }
            }
         }
      });         
      
      copy_sp = new JButton("Copy");
      copy_sp.setMargin(new Insets(1,1,1,1));
      copy_sp.setToolTipText(getToolTip("copy_sp"));
      copy_sp.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            // Copy selected SPs to a TiVo
            String tivoName = (String)tivo_sp.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0)
               tab_sp.SPListCopy(tivoName);
         }
      });         
      
      JButton delete_sp = new JButton("Delete");
      delete_sp.setMargin(new Insets(1,1,1,1));
      delete_sp.setToolTipText(getToolTip("delete_sp"));
      delete_sp.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            // Delete selected SPs from a TiVo
            tab_sp.SPListDelete();
         }
      });         
      
      modify_sp = new JButton("Modify");
      modify_sp.setMargin(new Insets(1,1,1,1));
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
      
      reorder_sp = new JButton("Re-order");
      reorder_sp.setMargin(new Insets(1,1,1,1));
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

      upcoming_sp = new JButton("Upcoming");
      upcoming_sp.setMargin(new Insets(1,1,1,1));
      upcoming_sp.setToolTipText(getToolTip("upcoming_sp"));
      upcoming_sp.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            int selected[] = TableUtil.GetSelectedRows(tab_sp.TABLE);
            if (selected.length > 0) {
               int row = selected[0];
               JSONObject json = tab_sp.GetRowData(row);
               if (json.has("__upcoming")) {
                  // Get upcoming SP episodes and display in ToDo table
                  TableUtil.clear(tab_todo.TABLE);
                  label_todo.setText("");
                  String tivoName = (String)tivo_sp.getSelectedItem();
                  try {
                     if (tivoName != null && tivoName.length() > 0) {
                        jobData job = new jobData();
                        job.source          = tivoName;
                        job.tivoName        = tivoName;
                        job.type            = "remote";
                        job.name            = "Remote";
                        job.remote_upcoming = true;
                        job.rnpl            = json.getJSONArray("__upcoming");
                        job.todo            = tab_todo;
                        jobMonitor.submitNewJob(job);
                     }
                  } catch (JSONException e1) {
                     log.error("upcoming_sp error - " + e1.getMessage());
                  }
               } else {
                  log.warn("No upcoming episodes scheduled for selected Season Pass");
               }
            }
         }
      });

      conflicts_sp = new JButton("Conflicts");
      conflicts_sp.setMargin(new Insets(1,1,1,1));
      conflicts_sp.setToolTipText(getToolTip("conflicts_sp"));
      conflicts_sp.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            int selected[] = TableUtil.GetSelectedRows(tab_sp.TABLE);
            if (selected.length > 0) {
               int row = selected[0];
               JSONObject json = tab_sp.GetRowData(row);
               if (json.has("__conflicts")) {
                  // Get conflict SP episodes and display in Won't Record table
                  TableUtil.clear(tab_cancel.TABLE);
                  String tivoName = (String)tivo_sp.getSelectedItem();
                  try {
                     if (tivoName != null && tivoName.length() > 0) {
                        jobData job = new jobData();
                        job.source           = tivoName;
                        job.tivoName         = tivoName;
                        job.type             = "remote";
                        job.name             = "Remote";
                        job.remote_conflicts = true;
                        job.rnpl             = json.getJSONArray("__conflicts");
                        job.cancelled        = tab_cancel;
                        jobMonitor.submitNewJob(job);
                     }
                  } catch (JSONException e1) {
                     log.error("conflicts_sp error - " + e1.getMessage());
                  }
               } else {
                  log.warn("No conflicting episodes for selected Season Pass");
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
      row1_sp.add(export_sp);
      row1_sp.add(Box.createRigidArea(space_5));
      row1_sp.add(delete_sp);
      row1_sp.add(Box.createRigidArea(space_5));
      row1_sp.add(copy_sp);
      row1_sp.add(Box.createRigidArea(space_5));
      row1_sp.add(modify_sp);
      row1_sp.add(Box.createRigidArea(space_5));
      row1_sp.add(reorder_sp);
      row1_sp.add(Box.createRigidArea(space_5));
      row1_sp.add(upcoming_sp);
      row1_sp.add(Box.createRigidArea(space_5));
      row1_sp.add(conflicts_sp);
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
               updateButtonStates(getTivoName("cancel"), "Won't Record");
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
      refresh_cancel.setMargin(new Insets(1,1,1,1));
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
               // Refresh will not record list
               TableUtil.clear(tab_cancel.TABLE);
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

      record_cancel = new JButton("Record");
      record_cancel.setMargin(new Insets(1,1,1,1));
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

      explain_cancel = new JButton("Explain");
      explain_cancel.setMargin(new Insets(1,1,1,1));
      explain_cancel.setToolTipText(getToolTip("explain_cancel"));
      explain_cancel.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String tivoName = (String)tivo_cancel.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0 && tab_cancel.inFolder) {
               int selected[] = TableUtil.GetSelectedRows(tab_cancel.TABLE);
               if (selected.length > 0) {
                  tab_cancel.getConflictDetails(tivoName, selected[0]);
               }
            }
         }
      });

      JButton refresh_todo_cancel = new JButton("Refresh ToDo");
      refresh_todo_cancel.setMargin(new Insets(1,1,1,1));
      refresh_todo_cancel.setToolTipText(getToolTip("refresh_todo_cancel"));
      refresh_todo_cancel.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String tivoName = (String)tivo_cancel.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               class backgroundRun extends SwingWorker<Object, Object> {
                  protected Object doInBackground() {
                     log.warn("Refreshing ToDo list for Will Not Record matches...");
                     all_todo = getTodoLists();
                     log.warn("Refresh ToDo list for Will Not Record matches completed.");
                     return null;
                  }
               }
               backgroundRun b = new backgroundRun();
               b.execute();
            }
         }
      });
      
      autoresolve = new JButton("Autoresolve");
      autoresolve.setMargin(new Insets(1,1,1,1));
      autoresolve.setToolTipText(getToolTip("autoresolve"));
      autoresolve.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            autoresolve.setEnabled(false);
            class backgroundRun extends SwingWorker<Object, Object> {
               protected Object doInBackground() {
                  rnpl.AutomaticConflictsHandler();
                  autoresolve.setEnabled(true);
                  return null;
               }
            }
            backgroundRun b = new backgroundRun();
            b.execute();
         }
      });
      
      includeHistory_cancel = new JCheckBox("Include History", false);
      includeHistory_cancel.setToolTipText(getToolTip("includeHistory_cancel"));
      
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
      row1_cancel.add(explain_cancel);
      row1_cancel.add(Box.createRigidArea(space_5));
      row1_cancel.add(refresh_todo_cancel);
      row1_cancel.add(Box.createRigidArea(space_5));
      row1_cancel.add(autoresolve);
      row1_cancel.add(Box.createRigidArea(space_5));
      row1_cancel.add(includeHistory_cancel);
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
      
      // Deleted table items      
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
      
      JPanel panel_deleted = new JPanel();
      panel_deleted.setLayout(new GridBagLayout());
      
      JPanel row1_deleted = new JPanel();
      row1_deleted.setLayout(new BoxLayout(row1_deleted, BoxLayout.LINE_AXIS));
      
      JLabel title_deleted = new JLabel("Recently Deleted list");
      
      JLabel tivo_deleted_label = new javax.swing.JLabel();
      
      tivo_deleted = new javax.swing.JComboBox();
      tivo_deleted.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
             if (e.getStateChange() == ItemEvent.SELECTED) {               
               // TiVo selection changed for Deleted tab
               TableUtil.clear(tab_deleted.TABLE);
               label_deleted.setText("");
               String tivoName = getTivoName("deleted");
               updateButtonStates(tivoName, "Deleted");
               if (tab_deleted.tivo_data.containsKey(tivoName))
                  tab_deleted.AddRows(tivoName, tab_deleted.tivo_data.get(tivoName));
            }
         }
      });
      tivo_deleted.setToolTipText(getToolTip("tivo_deleted"));

      refresh_deleted = new JButton("Refresh");
      refresh_deleted.setMargin(new Insets(1,1,1,1));
      refresh_deleted.setToolTipText(getToolTip("refresh_deleted"));
      refresh_deleted.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            // Refresh deleted list
            TableUtil.clear(tab_deleted.TABLE);
            label_deleted.setText("");
            String tivoName = (String)tivo_deleted.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               jobData job = new jobData();
               job.source         = tivoName;
               job.tivoName       = tivoName;
               job.type           = "remote";
               job.name           = "Remote";
               job.remote_deleted = true;
               job.deleted        = tab_deleted;
               jobMonitor.submitNewJob(job);
            }
         }
      });

      recover_deleted = new JButton("Recover");
      recover_deleted.setMargin(new Insets(1,1,1,1));
      recover_deleted.setToolTipText(getToolTip("recover_deleted"));
      recover_deleted.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String tivoName = (String)tivo_deleted.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               tab_deleted.recoverSingle(tivoName);
            }
         }
      });

      permDelete_deleted = new JButton("Permanently Delete");
      permDelete_deleted.setMargin(new Insets(1,1,1,1));
      permDelete_deleted.setToolTipText(getToolTip("permDelete_deleted"));
      permDelete_deleted.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String tivoName = (String)tivo_deleted.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               tab_deleted.permanentlyDelete(tivoName);
            }
         }
      });
      
      label_deleted = new JLabel();
      
      row1_deleted.add(Box.createRigidArea(space_5));
      row1_deleted.add(title_deleted);
      row1_deleted.add(Box.createRigidArea(space_5));
      row1_deleted.add(tivo_deleted_label);
      row1_deleted.add(Box.createRigidArea(space_5));
      row1_deleted.add(tivo_deleted);
      row1_deleted.add(Box.createRigidArea(space_5));
      row1_deleted.add(refresh_deleted);
      row1_deleted.add(Box.createRigidArea(space_5));
      row1_deleted.add(recover_deleted);
      row1_deleted.add(Box.createRigidArea(space_5));
      row1_deleted.add(permDelete_deleted);
      row1_deleted.add(Box.createRigidArea(space_5));
      row1_deleted.add(label_deleted);
      panel_deleted.add(row1_deleted, c);
      
      tab_deleted = new deletedTable(config.gui.getJFrame());
      tab_deleted.TABLE.setPreferredScrollableViewportSize(tab_deleted.TABLE.getPreferredSize());
      JScrollPane tabScroll_deleted = new JScrollPane(tab_deleted.scroll);
      gy++;
      c.gridy = gy;
      c.weightx = 1.0;
      c.weighty = 1.0;
      c.gridwidth = 8;
      c.fill = GridBagConstraints.BOTH;
      panel_deleted.add(tabScroll_deleted, c);
      
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
               updateButtonStates(tivoName, "Season Premieres");
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

      JLabel premiere_days_label = new javax.swing.JLabel("Days");      
      premiere_days = new javax.swing.JComboBox();
      premiere_days.setToolTipText(getToolTip("premiere_days"));
      for (int i=1; i<=12; ++i) {
         premiere_days.addItem(i);
      }
      premiere_days.setSelectedItem(12);

      JButton refresh_premiere = new JButton("Search");
      refresh_premiere.setMargin(new Insets(1,1,1,1));
      refresh_premiere.setToolTipText(getToolTip("refresh_premiere"));
      refresh_premiere.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            // Refresh table
            TableUtil.clear(tab_premiere.TABLE);
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
      
      record_premiere = new JButton("Record");
      record_premiere.setMargin(new Insets(1,1,1,1));
      record_premiere.setToolTipText(getToolTip("record_premiere"));
      record_premiere.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String tivoName = (String)tivo_premiere.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0)
               tab_premiere.recordSingle(tivoName);
         }
      });
      
      recordSP_premiere = new JButton("Season Pass");
      recordSP_premiere.setMargin(new Insets(1,1,1,1));
      recordSP_premiere.setToolTipText(getToolTip("recordSP_premiere"));
      recordSP_premiere.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String tivoName = (String)tivo_premiere.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0)
               tab_premiere.recordSP(tivoName);
         }
      });
      
      wishlist_premiere = new JButton("WL");
      wishlist_premiere.setMargin(new Insets(1,1,1,1));
      wishlist_premiere.setToolTipText(getToolTip("wishlist_search"));
      wishlist_premiere.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String tivoName = (String)tivo_premiere.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               createWishlist(tivoName, tab_premiere.TABLE);
            }
         }
      });
      
      JButton premiere_channels_update = new JButton("Update Channels");
      premiere_channels_update.setMargin(new Insets(1,1,1,1));
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
      row1_premiere.add(premiere_days);
      row1_premiere.add(Box.createRigidArea(space_5));
      row1_premiere.add(premiere_days_label);
      row1_premiere.add(Box.createRigidArea(space_5));
      row1_premiere.add(record_premiere);
      row1_premiere.add(Box.createRigidArea(space_5));
      row1_premiere.add(recordSP_premiere);
      row1_premiere.add(Box.createRigidArea(space_5));
      row1_premiere.add(wishlist_premiere);
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
      
      /* Comment out since TiVo disabled general web access via RPC
      // Web tab items      
      gy = 0;
      c.ipady = 0;
      c.insets = new Insets(5,0,5,0); // Increase vertical spacing
      c.weighty = 0.0;  // default to no vertical stretch
      c.weightx = 0.0;  // default to no horizontal stretch
      c.gridx = 0;
      c.gridy = gy;
      c.gridwidth = 1;
      c.gridheight = 1;
      c.anchor = GridBagConstraints.CENTER;
      c.fill = GridBagConstraints.HORIZONTAL;

      JPanel panel_web = new JPanel();
      panel_web.setLayout(new GridBagLayout());
      
      tivo_web = new javax.swing.JComboBox();
      tivo_web.setToolTipText(getToolTip("tivo_web"));
      
      type_web = new javax.swing.JComboBox(new String[] {"html", "flash"});
      type_web.setToolTipText(getToolTip("type_web"));

      url_web = new JTextField(40);
      url_web.setMinimumSize(url_web.getPreferredSize());
      // Press "Send" button when enter pressed in search text field
      url_web.addKeyListener( new KeyAdapter() {
         public void keyReleased( KeyEvent e ) {
            if (e.isControlDown())
               return;
            if( e.getKeyCode() == KeyEvent.VK_ENTER ) {
               send_web.doClick();
            }
         }
      });
      url_web.setToolTipText(getToolTip("url_web"));

      send_web = new JButton("Execute");
      send_web.setToolTipText(getToolTip("send_web"));
      send_web.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            // Execute URL
            String tivoName = (String)tivo_web.getSelectedItem();
            String type = (String)type_web.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               String url = string.removeLeadingTrailingSpaces(url_web.getText());
               if (url != null && url.length() > 0) {
                  RC_webCB(tivoName, url, type);
               }
               else
                  log.error("No URL provided");
            }
         }
      });
      
      JLabel bookmark_label = new JLabel("Bookmark:");
      
      bookmark_web = new javax.swing.JComboBox();
      bookmark_web.setToolTipText(getToolTip("bookmark_web"));
      bookmark_web.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
             if (e.getStateChange() == ItemEvent.SELECTED) {
                String[] l = ((String)bookmark_web.getSelectedItem()).split("::");
                type_web.setSelectedItem(l[0]);
                url_web.setText(l[1]);
            }
         }
      });

      JButton remove_bookmark = new JButton("Remove Bookmark");
      remove_bookmark.setToolTipText(getToolTip("remove_bookmark"));
      remove_bookmark.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            if (bookmark_web.getItemCount() > 0) {
               bookmark_web.removeItemAt(bookmark_web.getSelectedIndex());
            }
         }
      });
      
      JPanel row1_web = new JPanel();
      row1_web.setLayout(new BoxLayout(row1_web, BoxLayout.LINE_AXIS));      
      row1_web.add(tivo_web);
      row1_web.add(Box.createRigidArea(space_5));
      row1_web.add(type_web);
      row1_web.add(Box.createRigidArea(space_5));
      row1_web.add(url_web);
      row1_web.add(Box.createRigidArea(space_5));
      row1_web.add(send_web);
            
      JPanel row2_web = new JPanel();
      row2_web.setLayout(new BoxLayout(row2_web, BoxLayout.LINE_AXIS));
      row2_web.add(bookmark_label);
      row2_web.add(Box.createRigidArea(space_5));
      row2_web.add(bookmark_web);
      
      JPanel row3_web = new JPanel();
      row3_web.setLayout(new BoxLayout(row3_web, BoxLayout.LINE_AXIS));
      row3_web.add(remove_bookmark);
      
      panel_web.add(row1_web, c);
      gy++;
      c.gridy = gy;
      panel_web.add(row2_web, c);
      gy++;
      c.gridy = gy;
      c.anchor = GridBagConstraints.WEST;
      panel_web.add(row3_web, c);
      */
      
      // System Information tab items      
      gy = 0;
      c.ipady = 0;
      c.insets = new Insets(0,0,0,0);
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
               updateButtonStates(tivoName, "Info");
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

      JButton netconnect_info = new JButton("Network Connect");
      netconnect_info.setToolTipText(getToolTip("netconnect_info"));
      netconnect_info.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            // Initiate a net connect on selected TiVo
            String tivoName = (String)tivo_info.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               Remote r = config.initRemote(tivoName);
               if (r.success) {
                  JSONObject result = r.Command("PhoneHome", new JSONObject());
                  if (result == null)
                     log.error("NOTE: If this TiVo is in 'Pending Restart' state Network Connect fails.");
                  else
                     log.warn("Network Connection initiated on: " + tivoName);
                  r.disconnect();
               }
            }
         }
      });

      reboot_info = new JButton("Reboot");
      reboot_info.setToolTipText(getToolTip("reboot_info"));
      reboot_info.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            // Reboot selected TiVo
            String tivoName = (String)tivo_info.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               int response = JOptionPane.showConfirmDialog(
                  config.gui.getJFrame(),
                  "Reboot " + tivoName + "?",
                  "Confirm",
                  JOptionPane.YES_NO_OPTION,
                  JOptionPane.QUESTION_MESSAGE
               );
               if (response == JOptionPane.YES_OPTION) {
                  Remote r = config.initRemote(tivoName);
                  if (r.success) {
                     r.reboot(tivoName);
                  }
               }
            }
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
      row1_info.add(Box.createRigidArea(space_5));
      row1_info.add(netconnect_info);
      row1_info.add(Box.createRigidArea(space_40));
      row1_info.add(reboot_info);
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
      
      JPanel row2_search = new JPanel();
      row2_search.setLayout(new BoxLayout(row2_search, BoxLayout.LINE_AXIS));
      
      JLabel title_search = new JLabel("Search");
      
      JLabel tivo_search_label = new javax.swing.JLabel();
      
      tivo_search = new javax.swing.JComboBox();
      tivo_search.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
             if (e.getStateChange() == ItemEvent.SELECTED) {
                String tivoName = getTivoName("search");
                updateButtonStates(tivoName, "Search");
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
      button_search.setMargin(new Insets(1,1,1,1));
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
               TableUtil.clear(tab_search.TABLE);
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
                  job.remote_search_extended = extendedSearch.isSelected();
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
            if (e.isControlDown())
               return;
            if( e.getKeyCode() == KeyEvent.VK_ENTER ) {
               button_search.doClick();
            }
         }
      });

      text_search.setToolTipText(getToolTip("text_search"));

      JButton adv_search = new JButton("Search++");
      adv_search.setMargin(new Insets(1,1,1,1));
      adv_search.setToolTipText(getToolTip("adv_search"));
      adv_search.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String tivoName = (String)tivo_search.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               advSearch.display(
                  config.gui.getJFrame(), tivoName, (Integer)max_search.getValue()
               );
            }
         }
      });

      record_search = new JButton("Record");
      record_search.setMargin(new Insets(1,1,1,1));
      record_search.setToolTipText(getToolTip("record_search"));
      record_search.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String tivoName = (String)tivo_search.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               tab_search.recordSingle(tivoName);
            }
         }
      });

      recordSP_search = new JButton("SP");
      recordSP_search.setMargin(new Insets(1,1,1,1));
      recordSP_search.setToolTipText(getToolTip("record_sp_search"));
      recordSP_search.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String tivoName = (String)tivo_search.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               tab_search.recordSP(tivoName);
            }
         }
      });
      
      wishlist_search = new JButton("WL");
      wishlist_search.setMargin(new Insets(1,1,1,1));
      wishlist_search.setToolTipText(getToolTip("wishlist_search"));
      wishlist_search.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String tivoName = (String)tivo_search.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               createWishlist(tivoName, tab_search.TABLE);
            }
         }
      });
      
      search_manual_record = new JButton("MR");
      search_manual_record.setMargin(new Insets(1,1,1,1));
      search_manual_record.setToolTipText(getToolTip("guide_manual_record"));
      search_manual_record.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String tivoName = (String)tivo_search.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               mRecordOpt.promptUser(tivoName);
            }
         }
      });

      JButton refresh_todo_search = new JButton("Refresh ToDo");
      refresh_todo_search.setMargin(new Insets(1,1,1,1));
      refresh_todo_search.setToolTipText(getToolTip("refresh_todo_search"));
      refresh_todo_search.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String tivoName = (String)tivo_search.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               class backgroundRun extends SwingWorker<Object, Object> {
                  protected Object doInBackground() {
                     log.warn("Refreshing ToDo list for Search matches...");
                     all_todo = getTodoLists();
                     log.warn("Refresh ToDo list for Search matches completed.");
                     return null;
                  }
               }
               backgroundRun b = new backgroundRun();
               b.execute();
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
      row1_search.add(adv_search);
      row1_search.add(Box.createRigidArea(space_5));
      row1_search.add(record_search);
      row1_search.add(Box.createRigidArea(space_5));
      row1_search.add(recordSP_search);
      row1_search.add(Box.createRigidArea(space_5));
      row1_search.add(wishlist_search);
      row1_search.add(Box.createRigidArea(space_5));
      row1_search.add(search_manual_record);
      row1_search.add(Box.createRigidArea(space_5));
      row1_search.add(refresh_todo_search);
      panel_search.add(row1_search, c);
      
      extendedSearch = new JCheckBox("Include streaming", false);
      extendedSearch.setToolTipText(getToolTip("extendedSearch"));
      extendedSearch.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            Boolean enabled = extendedSearch.isSelected();
            includeFree.setEnabled(enabled);
            includePaid.setEnabled(enabled);
            includeVod.setEnabled(enabled);
         }
      });      
      includeFree = new JCheckBox("Include free content", false);
      includeFree.setToolTipText(getToolTip("includeFree"));
      includeFree.setEnabled(false);
      
      includePaid = new JCheckBox("Include paid content", false);
      includePaid.setToolTipText(getToolTip("includePaid"));
      includePaid.setEnabled(false);
      
      includeVod = new JCheckBox("Include VOD", false);
      includeVod.setToolTipText(getToolTip("includeVod"));
      includeVod.setEnabled(false);
      
      gy++;
      c.gridy = gy;
      row2_search.add(Box.createRigidArea(space_5));
      row2_search.add(extendedSearch);
      row2_search.add(Box.createRigidArea(space_5));
      row2_search.add(includeFree);
      row2_search.add(Box.createRigidArea(space_5));
      row2_search.add(includePaid);
      row2_search.add(Box.createRigidArea(space_5));
      row2_search.add(includeVod);
      panel_search.add(row2_search,c);
      
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
            if (event.equals("left"))
               buttons.put("LEFT", b);
            if (event.equals("right"))
               buttons.put("RIGHT", b);
            if (event.equals("up"))
               buttons.put("UP", b);
            if (event.equals("down"))
               buttons.put("DOWN", b);
            panel_controls.add(b);
            size = b.getPreferredSize();
            b.setBounds(x+insets.left, y+insets.top, size.width-cropx, size.height-cropy);
            b.addActionListener(new java.awt.event.ActionListener() {
               public void actionPerformed(java.awt.event.ActionEvent e) {
                  // Set focus on tabbed_panel
                  tabbed_panel.requestFocusInWindow();
                  final String tivoName = (String)tivo_rc.getSelectedItem();
                  if (tivoName != null && tivoName.length() > 0) {
                     class backgroundRun extends SwingWorker<Object, Object> {
                        protected Object doInBackground() {
                           if (config.rpcEnabled(tivoName)) {
                              Remote r = config.initRemote(tivoName);
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
                           } else {
                              // Use telnet protocol
                              new telnet(config.TIVOS.get(tivoName), mapToTelnet(new String[] {event}));
                           }
                           // Set focus on tabbed_panel
                           tabbed_panel.requestFocusInWindow();
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
      JButton standby = new CustomButton(
         "Toggle standby", "standby",
         new String[] {"standby"}
      );
      size = standby.getPreferredSize();
      panel_controls.add(standby);
      standby.setBounds(500+insets.left, 10+insets.top, size.width, size.height);
      
      JButton toggle_cc = new CustomButton("Toggle CC", "toggle_cc", null);
      size = toggle_cc.getPreferredSize();
      panel_controls.add(toggle_cc);
      toggle_cc.setBounds(500+insets.left, 40+insets.top, size.width, size.height);
      toggle_cc.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String tivoName = (String)tivo_rc.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               String event;
               if (cc_state)
                  event = "ccOff";
               else
                  event = "ccOn";
               cc_state = ! cc_state;
               if (config.rpcEnabled(tivoName)) {
                  Remote r = config.initRemote(tivoName);
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
               } else {
                  // Use telnet interface
                  String[] sequence = new String[1];
                  if (event.equals("ccOff"))
                     sequence[0] = "CC_OFF";
                  if (event.equals("ccOn"))
                     sequence[0] = "CC_ON";
                  new telnet(config.TIVOS.get(tivoName), sequence);                     
               }
            }
         }
      });
      
      JButton myShows = new CustomButton("My Shows", "My Shows", null);
      size = myShows.getPreferredSize();
      panel_controls.add(myShows);
      myShows.setBounds(500+insets.left, 70+insets.top, size.width, size.height);
      myShows.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String tivoName = (String)tivo_rc.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               if (config.rpcEnabled(tivoName)) {
                  Remote r = config.initRemote(tivoName);
                  if (r.success) {
                     try {
                        JSONObject json = new JSONObject();
                        json.put("event", "nowShowing");
                        r.Command("keyEventSend", json);
                     } catch (JSONException e1) {
                        log.error("RC - " + e1.getMessage());
                     }
                     r.disconnect();
                  }
               } else {
                  // Use telnet interface
                  String[] sequence = new String[] {"NOWSHOWING"};
                  new telnet(config.TIVOS.get(tivoName), sequence);                     
               }
            }
         }
      });
            
      // Other components for the panel      
      JLabel label_rc = new JLabel("TiVo");
      
      tivo_rc = new javax.swing.JComboBox();
      tivo_rc.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {               
               String tivoName = getTivoName("rc");
               updateButtonStates(tivoName, "Remote");
            }
         }
      });
      tivo_rc.setToolTipText(getToolTip("tivo_rc"));

      rc_hme_button = new JButton("Launch App:");
      disableSpaceAction(rc_hme_button);
      rc_hme_button.setToolTipText(getToolTip("rc_hme_button"));
      rc_hme_button.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            final String name = (String)hme_rc.getSelectedItem();
            if (name != null && name.length() > 0) {
               class backgroundRun extends SwingWorker<Object, Object> {
                  protected Object doInBackground() {
                     Remote r = config.initRemote(getTivoName("rc"));
                     if (r.success) {
                        try {
                           JSONObject json = new JSONObject();
                           String uri="";
                           if (name.equals("Netflix (flash)"))
                              uri = "x-tivo:flash:uuid:F23D193D-D2C2-4D18-9ABE-FA6B8488302F";
                           if (name.equals("Netflix (html)"))
                              uri = "x-tivo:netflix:netflix";
                           if (name.equals("YouTube"))
                              uri = "x-tivo:flash:uuid:B8CEA236-0C3D-41DA-9711-ED220480778E";
                           if (name.equals("YouTube (html)"))
                              uri = "x-tivo:web:https://www.youtube.com/tv";
                           if (name.equals("Amazon (html)"))
                              uri = "x-tivo:web:https://atv-ext.amazon.com/cdp/resources/app_host/index.html?deviceTypeID=A3UXGKN0EORVOF";
                           if (name.equals("Vudu (html)"))
                              uri = "x-tivo:vudu:vudu";
                           if (name.equals("Amazon (hme)"))
                              uri = "x-tivo:hme:uuid:35FE011C-3850-2228-FBC5-1B9EDBBE5863";
                           if (name.equals("Hulu Plus"))
                              uri = "x-tivo:flash:uuid:802897EB-D16B-40C8-AEEF-0CCADB480559";
                           if (name.equals("AOL On"))
                              uri = "x-tivo:flash:uuid:EA1DEF9D-D346-4284-91A0-FEA8EAF4CD39";
                           if (name.equals("Launchpad"))
                              uri = "x-tivo:flash:uuid:545E064D-C899-407E-9814-69A021D68DAD";
                           json.put("uri", uri);
                           r.Command("Navigate", json);
                        } catch (JSONException e1) {
                           log.error("Launch App - " + e1.getMessage());
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

      // SPS backdoors
      String sps_name, sps_text;
      String sps_text_end = "Should be used while playing back a recorded show.</html>";
      sps_name = "Quick clear play bar: SPSPS";
      SPS.put(sps_name, "select play select pause select play");
      sps_text = "<html><b>" + sps_name + "</b><br>";
      sps_text += SPS.get(sps_name) + "<br>";
      sps_text += "Toggle 'clear trickplay banner quickly' setting.<br>";
      sps_text += "This will also clear any 'pause ads' quickly.<br>";
      sps_text += sps_text_end;
      SPS.put(sps_name + "_tooltip", sps_text);

      sps_name = "Clock: SPS9S";
      SPS.put(sps_name, "select play select 9 select clear");
      sps_text = "<html><b>" + sps_name + "</b><br>";
      sps_text += SPS.get(sps_name) + "<br>";
      sps_text += "Toggle on screen clock.<br>";
      sps_text += "Clock will be at top right corner for series 4 TiVos or later.<br>";
      sps_text += "Clock will be at bottom right corner for series 3 TiVos.<br>";
      sps_text += sps_text_end;
      SPS.put(sps_name + "_tooltip", sps_text);
            
      sps_name = "30 sec skip: SPS30S";
      SPS.put(sps_name, "select play select 3 0 select clear");
      sps_text = "<html><b>" + sps_name + "</b><br>";
      sps_text += SPS.get(sps_name) + "<br>";
      sps_text += "Toggle 30 sec skip binding of advance button.<br>";
      sps_text += "NOTE: Unlike other backdoors, this one survives a reboot.<br>";
      sps_text += sps_text_end;
      SPS.put(sps_name + "_tooltip", sps_text);
      
      sps_name = "Information: SPSRS";
      SPS.put(sps_name, "select play select replay select");
      sps_text = "<html><b>" + sps_name + "</b><br>";
      sps_text += SPS.get(sps_name) + "<br>";
      sps_text += "Display some video information on the screen.<br>";
      sps_text += sps_text_end;
      SPS.put(sps_name + "_tooltip", sps_text);
      
      sps_name = "Calibration: SPS7S";
      SPS.put(sps_name, "select play select 7 select clear");
      sps_text = "<html><b>" + sps_name + "</b><br>";
      sps_text += SPS.get(sps_name) + "<br>";
      sps_text += "Display calibration map for centering and overscan.<br>";
      sps_text += "NOTE: This only works for series 3 TiVos.<br>";
      sps_text += sps_text_end;
      SPS.put(sps_name + "_tooltip", sps_text);
      
      sps_name = "4x FF: SPS88S";
      SPS.put(sps_name, "select play select 8 8 select clear");
      sps_text = "<html><b>" + sps_name + "</b><br>";
      sps_text += SPS.get(sps_name) + "<br>";
      sps_text += "Toggles '4th FF press returns to play speed' setting.<br>";
      sps_text += "Series 4 software changed behavior such that beyond 3 FF presses nothing happens.<br>";
      sps_text += "When enabled a 4th FF press resumes normal play as was the case with older TiVo software.<br>";
      sps_text += sps_text_end;
      SPS.put(sps_name + "_tooltip", sps_text);
     
      rc_sps_button = new JButton("SPS backdoor:");
      disableSpaceAction(rc_sps_button);
      rc_sps_button.setToolTipText(getToolTip("rc_sps_button"));
      rc_sps_button.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String name = (String)hme_sps.getSelectedItem();
            String tivoName = (String)tivo_rc.getSelectedItem();
            if (name != null && name.length() > 0 && tivoName != null && tivoName.length() > 0) {
               executeMacro(
                  tivoName,
                  SPS.get(name).split(" ")
               );
            }
         }
      });
      
      hme_sps = new javax.swing.JComboBox();
      hme_sps.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
               String item = (String)hme_sps.getSelectedItem();
               hme_sps.setToolTipText(getToolTip(item));            
            }
         }
      });
      int sps_count = 0;
      for (String name : SPS.keySet()) {
         if (! name.contains("_tooltip")) {
            hme_sps.addItem(name);
            if (sps_count == 0)
               hme_sps.setToolTipText(SPS.get(name + "_tooltip"));
         }
         sps_count++;
      }
      
      rc_jumpto_button = new JButton("Jump to minute:");
      AddButtonShortcut(rc_jumpto_button, "Altm", KeyEvent.VK_M);
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
               final int secs = (int)(Float.parseFloat(mins_string)*60);
               class backgroundRun extends SwingWorker<Object, Object> {
                  protected Boolean doInBackground() {
                     Remote r = config.initRemote(tivoName);
                     if (r.success) {
                        JSONObject json = new JSONObject();
                        try {
                           Long pos = (long)1000*secs;
                           json.put("offset", pos);
                           r.Command("Jump", json);
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

      rc_jumpahead_button = new JButton("Skip minutes ahead:");
      AddButtonShortcut(rc_jumpahead_button, "Alt.", KeyEvent.VK_PERIOD);
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
               final int secs = (int)(Float.parseFloat(mins_string)*60);
               class backgroundRun extends SwingWorker<Object, Object> {
                  protected Boolean doInBackground() {
                     Remote r = config.initRemote(tivoName);
                     if (r.success) {
                        JSONObject json = new JSONObject();
                        JSONObject reply = r.Command("Position", json);
                        if (reply != null && reply.has("position")) {
                           try {
                              Long pos = reply.getLong("position");
                              pos += (long)1000*secs;
                              json.put("offset", pos);
                              r.Command("Jump", json);
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

      rc_jumpback_button = new JButton("Skip minutes back:");
      AddButtonShortcut(rc_jumpback_button, "Alt,", KeyEvent.VK_COMMA);
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
               final int secs = (int)(Float.parseFloat(mins_string)*60);
               class backgroundRun extends SwingWorker<Object, Object> {
                  protected Boolean doInBackground() {
                     Remote r = config.initRemote(tivoName);
                     if (r.success) {
                        JSONObject json = new JSONObject();
                        JSONObject reply = r.Command("Position", json);
                        if (reply != null && reply.has("position")) {
                           try {
                              Long pos = reply.getLong("position");
                              pos -= (long)1000*secs;
                              if (pos < 0)
                                 pos = (long)0;
                              json.put("offset", pos);
                              r.Command("Jump", json);
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
      rctop.add(Box.createRigidArea(space_5));
      rctop.add(rc_sps_button);
      rctop.add(Box.createRigidArea(space_5));
      rctop.add(hme_sps);

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
         AddPanelShortcut(panel_rc, "" + i, i, true, "" + new String("" + Character.toChars(i)[0]).toLowerCase());
         AddPanelShortcut(panel_rc, "Shift" + i, i, true, "" + Character.toChars(i)[0]);
      }
      Object[][] kb_shortcuts = new Object[][] {
         // NAME       Keyboard KeyEvent       isAscii action
         {"SPACE",     KeyEvent.VK_SPACE,      false, "forward"},
         {"BACKSPACE", KeyEvent.VK_BACK_SPACE, false, "reverse"},
         {"Shift1",    KeyEvent.VK_1,          true,  "!"},
         {"Shift2",    KeyEvent.VK_2,          true,  "@"},
         {"Shift7",    KeyEvent.VK_7,          true,  "&"},
         {"Shift8",    KeyEvent.VK_8,          true,  "*"},
         {"Shift;",    KeyEvent.VK_SEMICOLON,  true,  ":"},
         {"Shift-",    KeyEvent.VK_MINUS,      true,  "_"},
         {"COMMA",     KeyEvent.VK_COMMA,      true,  ","},
         {"PERIOD",    KeyEvent.VK_PERIOD,     true,  "."},
         {"SLASH",     KeyEvent.VK_SLASH,      true,  "/"},
         {"Shift'",    KeyEvent.VK_QUOTE,      true,  "\""},
         {"QUOTE",     KeyEvent.VK_QUOTE,      true,  "'"},
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
      tabbed_panel.add("Deleted", panel_deleted);
      tabbed_panel.add("Remote", panel_rc);
      //tabbed_panel.add("Web", panel_web);
      tabbed_panel.add("Info", panel_info);
      
      // Init the tivo comboboxes
      setTivoNames();
            
      // Pack table columns
      TableUtil.packColumns(tab_todo.TABLE, 2);
      TableUtil.packColumns(tab_guide.TABLE, 2);
      TableUtil.packColumns(tab_sp.TABLE, 2);
      TableUtil.packColumns(tab_cancel.TABLE, 2);
      TableUtil.packColumns(tab_deleted.TABLE, 2);
      TableUtil.packColumns(tab_search.TABLE, 2);
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
   
   /*
   private void RC_webCB(final String tivoName, final String url, final String type) {
      class backgroundRun extends SwingWorker<Object, Object> {
         protected Boolean doInBackground() {
            Remote r = config.initRemote(tivoName);
            if (r.success) {
               JSONObject json = new JSONObject();
               try {
                  String destination = type;
                  if (type.equals("html"))
                     destination = "web";
                  json.put("bodyId", r.bodyId_get());
                  json.put("uiDestinationType", destination);
                  json.put("uri", "x-tivo:" + destination + ":" + url);
                  log.warn(type + " url request for TiVo '" + tivoName + "' - " + url);
                  JSONObject reply = r.Command("uiNavigate", json);
                  if (reply != null)
                     log.print("Response - " + reply.toString());
               } catch (JSONException e) {
                  log.error("RC_webCB error - " + e.getMessage());
               }
               r.disconnect();
            }
            
            // Add type + url to bookmarks if not already there
            Boolean add = true;
            String entry = type + "::" + url;
            if (bookmark_web.getItemCount() > 0) {
               for (int i=0; i<bookmark_web.getItemCount(); ++i) {
                  String item = (String)bookmark_web.getItemAt(i);
                  if (item.equals(entry))
                     add = false;
               }
            }
            if (add) {
               bookmark_web.insertItemAt(entry, 0);
               bookmark_web.setSelectedIndex(0);
            }
            
            return null;
         }
      }
      backgroundRun b = new backgroundRun();
      b.execute();
   }*/
   
   private void RC_infoCB(final String tivoName) {
      class backgroundRun extends SwingWorker<Object, Object> {
         protected Boolean doInBackground() {
            Remote r = config.initRemote(tivoName);
            if (r.success) {
               JSONObject json = new JSONObject();
               JSONObject reply = r.Command("SysInfo", json);
               if (reply != null && reply.has("bodyConfig")) {
                  try {
                     String info = "";
                     // System info
                     json = reply.getJSONArray("bodyConfig").getJSONObject(0);
                     if (json.has("userDiskSize") && json.has("userDiskUsed")) {
                        Float sizeGB = (float)json.getLong("userDiskSize")/(1024*1024);
                        // Update diskSpace hash if necessary
                        Boolean update = true;
                        if (config.diskSpace.containsKey(tivoName)) {
                           if (Math.round(config.diskSpace.get(tivoName)) == Math.round(sizeGB))
                              update = false;
                        }
                        if (update) {
                           log.warn("Updating " + tivoName + " disk space to " + sizeGB + " GB");
                           config.diskSpace.put(tivoName, sizeGB);
                           config.save(config.configIni);
                        }
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

                     if (! r.awayMode() ) {
                        // What's On info
                        String [] whatson = getWhatsOn(tivoName);
                        if (whatson != null) {
                           info += String.format("%s\t\t", "What's On");
                           for (int i=0; i<whatson.length; ++i) {
                              if (i>0)
                                 info += "; ";
                              info += whatson[i];
                           }
                           info += "\n";
                        }
                        info += "\n";
                     
                        // Tuner info
                        reply = r.Command("TunerInfo", new JSONObject());
                        if (reply != null && reply.has("state")) {
                           for (int i=0; i<reply.getJSONArray("state").length(); ++i) {
                              json = reply.getJSONArray("state").getJSONObject(i);
                              info += String.format("tunerId\t\t%s\n", json.getString("tunerId"));
                              if (json.has("channel")) {
                                 info += String.format("channelNumber\t%s",
                                    json.getJSONObject("channel").getString("channelNumber")
                                 );
                                 if (json.getJSONObject("channel").has("callSign"))
                                    info += " (" + json.getJSONObject("channel").getString("callSign") + ")";
                              }
                              info += "\n\n";
                           }
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
   
   private String[] getWhatsOn(String tivoName) {
      Remote r = config.initRemote(tivoName);
      if (r.success) {
         JSONObject result = r.Command("WhatsOn", new JSONObject());
         if (result != null && result.has("whatsOn")) {
            try {
               JSONArray a = result.getJSONArray("whatsOn");
               String display[] = new String[a.length()];
               for (int i=0; i<a.length(); ++i) {
                  JSONObject o = a.getJSONObject(i);
                  if (o.has("playbackType")) {
                     display[i] = "" + o.getString("playbackType");
                     if (! o.getString("playbackType").equals("idle") && o.has("channelIdentifier")) {
                        JSONObject c = o.getJSONObject("channelIdentifier");
                        if (c.has("channelNumber"))
                           display[i] = display[i] + " (channel " + c.getString("channelNumber") + ")";
                     }
                  }
               }
               r.disconnect();
               return display;
            } catch (JSONException e1) {
               log.error("getWhatsOn json error: " + e1.getMessage());
               r.disconnect();
               return null;
            }
         }
         r.disconnect();
      }
      return null;
   }
      
   public String getCurrentTabName() {
      return tabbed_panel.getTitleAt(tabbed_panel.getSelectedIndex());
   }
   
   public JXTable getCurrentTable() {
      String tabName = getCurrentTabName();
      if (tabName.equals("ToDo"))
         return tab_todo.TABLE;
      if (tabName.equals("Season Passes"))
         return tab_sp.TABLE;
      if (tabName.equals("Won't Record"))
         return tab_cancel.TABLE;
      if (tabName.equals("Season Premieres"))
         return tab_premiere.TABLE;
      if (tabName.equals("Search"))
         return tab_search.TABLE;
      if (tabName.equals("Guide"))
         return tab_guide.TABLE;
      if (tabName.equals("Deleted"))
         return tab_deleted.TABLE;
      return null;
   }

   public String getGuideStartTime() {
	  String start = (String)guide_start.getSelectedItem();
	  if (start == null || start.length() == 0) {
		  tab_guide.setComboBoxDates(guide_start, guide_hour_increment, guide_total_range);
		  start = (String)guide_start.getSelectedItem();
	  }
      return start;
   }
      
   public int getPremiereDays() {
      return (Integer)premiere_days.getSelectedItem();
   }
   
   public String getTivoName(String tab) {
      if (tab.equals("todo") || tab.equals("ToDo"))
         return (String)tivo_todo.getSelectedItem();
      if (tab.equals("guide") || tab.equals("Guide"))
         return (String)tivo_guide.getSelectedItem();
      if (tab.equals("sp") || tab.equals("Season Passes"))
         return (String)tivo_sp.getSelectedItem();
      if (tab.equals("cancel") || tab.equals("Won't Record"))
         return (String)tivo_cancel.getSelectedItem();
      if (tab.equals("deleted") || tab.equals("Deleted"))
         return (String)tivo_deleted.getSelectedItem();
      if (tab.equals("search") || tab.equals("Search"))
         return (String)tivo_search.getSelectedItem();
      if (tab.equals("rc") || tab.equals("Remote"))
         return (String)tivo_rc.getSelectedItem();
      //if (tab.equals("web") || tab.equals("Web"))
      //   return (String)tivo_web.getSelectedItem();
      if (tab.equals("info") || tab.equals("Info"))
         return (String)tivo_info.getSelectedItem();
      if (tab.equals("premiere") || tab.equals("Season Premieres"))
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
         if (tab.equals("deleted"))
            tivo_deleted.setSelectedItem(tivoName);
         if (tab.equals("search"))
            tivo_search.setSelectedItem(tivoName);
         if (tab.equals("rc"))
            tivo_rc.setSelectedItem(tivoName);
         //if (tab.equals("web"))
         //   tivo_web.setSelectedItem(tivoName);
         if (tab.equals("info"))
            tivo_info.setSelectedItem(tivoName);
         if (tab.equals("premiere"))
            tivo_premiere.setSelectedItem(tivoName);
      }
   }
   
   public void clearTable(String tableName) {
      if (tableName.equals("sp")) {
         TableUtil.clear(tab_sp.TABLE);
      }
      if (tableName.equals("todo")) {
         TableUtil.clear(tab_todo.TABLE);
      }
      if (tableName.equals("guide")) {
         TableUtil.clear(tab_guide.TABLE);
      }
      if (tableName.equals("cancel")) {
         TableUtil.clear(tab_cancel.TABLE);
      }
      if (tableName.equals("deleted")) {
         TableUtil.clear(tab_deleted.TABLE);
      }
      if (tableName.equals("search")) {
         TableUtil.clear(tab_search.TABLE);
      }
      if (tableName.equals("premiere")) {
         TableUtil.clear(tab_premiere.TABLE);
      }
   }
   
   public void setTivoNames() { 
      tivo_todo.removeAllItems();
      tivo_guide.removeAllItems();
      tivo_sp.removeAllItems();
      tivo_cancel.removeAllItems();
      tivo_deleted.removeAllItems();
      tivo_search.removeAllItems();
      tivo_rc.removeAllItems();
      tivo_info.removeAllItems();
      //tivo_web.removeAllItems();
      tivo_premiere.removeAllItems();
      for (String tivoName : config.getTivoNames()) {
         if (config.rpcEnabled(tivoName) || config.mindEnabled(tivoName)) {
            tivo_todo.addItem(tivoName);
            tivo_guide.addItem(tivoName);
            tivo_sp.addItem(tivoName);
            tivo_cancel.addItem(tivoName);
            tivo_deleted.addItem(tivoName);
            tivo_search.addItem(tivoName);
            tivo_info.addItem(tivoName);
            tivo_premiere.addItem(tivoName);
         }
         //if (config.rpcEnabled(tivoName)) {
         //   tivo_web.addItem(tivoName);            
         //}
         // Remote tab always valid as it can use RPC or telnet
         tivo_rc.addItem(tivoName);
      }
      setHmeDestinations(getTivoName("rc"));
   }
   
   // Return list of Tivos that are rpc enabled or mind enabled and NPL capable (i.e. no Mini)
   private Stack<String> getFilteredTivoNames() {
      Stack<String> tivoNames = new Stack<String>();
      for (String tivoName : config.getTivoNames()) {
         if (config.nplCapable(tivoName)) {
            if (config.rpcEnabled(tivoName) || config.mindEnabled(tivoName))
               tivoNames.add(tivoName);
         }
      }
      return tivoNames;
   }
   
   private void updateButtonStates(String tivoName, String tab) {
      Boolean state;
      if (config.rpcEnabled(tivoName))
         state = true;
      else
         state = false;
      if (tab.equals("ToDo")) {
         cancel_todo.setEnabled(state);
         modify_todo.setEnabled(state);
      }
      if (tab.equals("Season Passes")) {
         reorder_sp.setEnabled(state);
         copy_sp.setEnabled(state);
      }
      if (tab.equals("Won't Record")) {
         record_cancel.setEnabled(state);
      }
      if (tab.equals("Season Premieres")) {
         wishlist_premiere.setEnabled(state);
         record_premiere.setEnabled(state);
         recordSP_premiere.setEnabled(state);
      }
      if (tab.equals("Search")) {
         wishlist_search.setEnabled(state);
         record_search.setEnabled(state);
         recordSP_search.setEnabled(state);
         search_manual_record.setEnabled(state);
      }
      if (tab.equals("Guide")) {
         wishlist_guide.setEnabled(state);
         record_guide.setEnabled(state);
         recordSP_guide.setEnabled(state);
         guide_manual_record.setEnabled(state);
      }
      if (tab.equals("Deleted")) {
         recover_deleted.setEnabled(state);
         permDelete_deleted.setEnabled(state);
      }
      if (tab.equals("Remote")) {
         rc_hme_button.setEnabled(state);
         rc_jumpto_button.setEnabled(state);
         rc_jumpahead_button.setEnabled(state);
         rc_jumpback_button.setEnabled(state);
      }
      if (tab.equals("Info")) {
         reboot_info.setEnabled(state);
      }
   }
   
   // NOTE: This already called in swing worker, so no need to background
   /*private String[] getHmeDestinations(String tivoName) {
      Remote r = getRemote(tivoName);
      if (r.success) {
         r.debug = false;
         JSONObject json = new JSONObject();
         JSONObject result = r.Command("Hmedestinations", json);
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
   }*/
   
   public void setHmeDestinations(final String tivoName) {
      /*
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
      */
      String[] hmeNames = {
         "Netflix (html)", "YouTube (html)", "Vudu (html)",
         "Amazon (hme)", "Hulu Plus", "AOL On", "Launchpad"
      };
      hme_rc.removeAllItems();
      for (int i=0; i<hmeNames.length; ++i)
         hme_rc.addItem(hmeNames[i]);
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
      log.warn("Collecting information on existing Season Passes...");
      for (String tivoName : getFilteredTivoNames()) {
         Remote r = config.initRemote(tivoName);
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
                           if (data.getJSONObject(j).has("__SPscheduled__")) {
                              data.getJSONObject(j).put("__SPscheduled__",
                                 data.getJSONObject(j).getString("__SPscheduled__") +
                                 ", " + tivoName
                              );
                           } else {
                              data.getJSONObject(j).put("__SPscheduled__", tivoName);
                           }
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
   
   // Obtain todo lists for specified tivo names
   // Used by Search and Guide tabs to mark recordings
   // NOTE: This called as part of a background job
   // NOTE: This uses CountDownLatch to enable waiting for multiple
   // parallel background jobs to finish before returning so that
   // ToDo lists are retrieved in parallel instead of sequentially
   public Hashtable<String,JSONArray> getTodoLists() {
      all_todo_time = new Date().getTime();
      return rnpl.getTodoLists(getFilteredTivoNames());
   }
      
   // See if given JSON entry matches any of the entries in all_todo hashtable
   public void flagIfInTodo(JSONObject entry, Boolean includeOtherTimes) {
      rnpl.flagIfInTodo(entry, includeOtherTimes, all_todo);
   }
   
   // Prompt user to create a wishlist
   public Boolean createWishlist(final String tivoName, JXTable TABLE) {
      Hashtable<String,String> hash = new Hashtable<String,String>();
      // Take title from selected table entry if there is one
      int[] selected = TABLE.getSelectedRows();
      if (selected.length > 0) {
         int row = selected[0];
         JSONObject json = TableUtil.GetRowData(TABLE, row, "DATE");
         if (json != null && json.has("title")) {
            try {
               hash.put("title", json.getString("title"));
               hash.put("title_keyword", json.getString("title"));
            } catch (JSONException e) {
               log.error("createWishlist error: " + e.getMessage());
               return false;
            }
         }
      }

      // Bring up Create Wishlist dialog
      JSONObject wl = wlOpt.promptUser("(" + tivoName + ") " + "Create Wishlist", hash);
      if (wl == null)
         return false;
      if ( ! wl.has("title")) {
         log.error("Wishlist title is required to be specified. Aborting.");
         return false;
      }
      try {
         JSONObject json = new JSONObject();
         if (wl.has("autoRecord")) {
            // Need to prompt for season pass options
            json = spOpt.promptUser("(" + tivoName + ") " + "Create ARWL - " + wl.getString("title"), null, true);
         }
         
         if (json != null) {
            // Merge wl options into json
            for (String key : JSONObject.getNames(wl))
               json.put(key, wl.get(key));
            
            // Run the RPC command in background mode
            //log.print(json.toString());
            log.warn("Creating wishlist: " + wl.getString("title"));
            final JSONObject fjson = json;
            class backgroundRun extends SwingWorker<Object, Object> {
               protected Boolean doInBackground() {
                  Remote r = config.initRemote(tivoName);
                  if (r.success) {
                     JSONObject result = r.Command("Wishlist", fjson);
                     if (result != null)
                        log.warn("Wishlist created successfully.");
                     else
                        log.error("Wishlist creation failed.");
                     r.disconnect();
                  }
                  return false;
               }
            }
            backgroundRun b = new backgroundRun();
            b.execute();
         }
         return true;
      } catch (JSONException e) {
         log.error(e.getMessage());
         log.error(Arrays.toString(e.getStackTrace()));
         return false;
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
         JButton b = new CustomButton(scale(pane, image.getImage(),scale));
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
   
   private String[] mapToTelnet(String[] sequence) {
      Stack<String> n = new Stack<String>();
      for (int i=0; i<sequence.length; ++i) {
         String u = sequence[i].toUpperCase();
         if (! u.startsWith("ACTION")) {
            if (u.equals("ZOOM"))
               u = "WINDOW";
            n.add(u);
         }
      }
      String[] mapped = new String[n.size()];
      for (int i=0; i<n.size(); ++i)
         mapped[i] = n.get(i);
      return mapped;
   }
   
   private void executeMacro(final String tivoName, final String[] sequence) {
      class backgroundRun extends SwingWorker<Object, Object> {
         protected Object doInBackground() {
            if (config.rpcEnabled(tivoName)) {
               Remote r = config.initRemote(tivoName);
               r.keyEventMacro(sequence);
            } else {
               // Use telnet protocol
               new telnet(config.TIVOS.get(tivoName), mapToTelnet(sequence));
            }
            // Set focus on tabbed_panel
            tabbed_panel.requestFocusInWindow();
            return null;
         }
      }
      backgroundRun b = new backgroundRun();
      b.execute();      
   }
   
   private void setMacroCB(JButton b, final String[] sequence) {
      b.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            // Set focus on tabbed_panel
            tabbed_panel.requestFocusInWindow();
            final String tivoName = (String)tivo_rc.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               executeMacro(tivoName, sequence);
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
               if (config.rpcEnabled(tivoName)) {
                  Remote r = config.initRemote(tivoName);
                  if (r.success) {
                     try {
                        JSONObject json = new JSONObject();
                        if (isAscii) {
                           json.put("event", "text");
                           r.Command("keyEventSend", json);
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
               } else {
                  // Use telnet protocol
                  if (isAscii)
                     new telnet(config.TIVOS.get(tivoName), new String[] {command});
                  else
                     new telnet(config.TIVOS.get(tivoName), mapToTelnet(new String[] {command}));
               }
            }
            return null;
         }
      }
      backgroundRun b = new backgroundRun();
      b.execute();
   }
   
   // Check current time vs time of last all_todo refresh to see if we need refreshed
   // Give a 15 min cushion
   private Boolean todoNeedsRefresh() {
      long cushion = 15*60*1000;
      long now = new Date().getTime();
      if (all_todo_time == 0)
         return true;
      if (now > all_todo_time + cushion)
         return true;
      return false;
   }
   
   public Boolean AllChannels() {
      return guide_channels.isSelected();
   }
   
   public void updateTodoIfNeeded(String tabName) {
      if (todoNeedsRefresh()) {
         log.warn("Refreshing todo lists");
         all_todo = getTodoLists();
      }
   }
   
   public void addEntryToTodo(String tivoName, JSONObject json) {
      if (all_todo.containsKey(tivoName))
         all_todo.get(tivoName).put(json);
   }
      
   public String getToolTip(String component) {
      debug.print("component=" + component);
      String text = "";
      if (component.equals("tivo_todo")) {
         text = "Select TiVo for which to retrieve To Do list.<br>";
         text += "NOTE: If a TiVo is missing go to Config-Tivos and turn on 'Enable iPad' setting for<br>";
         text += ">= series 4 units or provide tivo.com username & password for older units for more<br>";
         text += "limited Remote functionality. Then re-start kmttg after updating those settings.";
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
      else if (component.equals("modify_todo")){
         text = "<b>Modify</b><br>";
         text += "Modify recording options of selected show in table below.";
      }
      else if (component.equals("export_todo")){
         text = "<b>Export</b><br>";
         text += "Export selected TiVo ToDo list to a csv file which can be easily<br>";
         text += "imported into an Excel spreadsheet or equivalent.";
      }
      if (component.equals("tivo_guide")) {
         text = "Select TiVo for which to retrieve guide listings.<br>";
         text += "NOTE: If a TiVo is missing go to Config-Tivos and turn on 'Enable iPad' setting for<br>";
         text += ">= series 4 units or provide tivo.com username & password for older units for more<br>";
         text += "limited Remote functionality. Then re-start kmttg after updating those settings.";
      }
      if (component.equals("guide_start")) {
         text = "<b>Start</b><br>";
         text += "Select guide start time to use when obtaining listings.<br>";
         text += "NOTE: If you are inside a channel folder when you change this setting<br>";
         text += "the guide listings will automatically update to new date.";
      }
      if (component.equals("guide_channels")) {
         text = "<b>All</b><br>";
         text += "If this option is checked then all channels in your lineup are shown, else just<br>";
         text += "channels that are enabled in your lineup are shown.";
      }
      else if (component.equals("refresh_guide")){
         text = "<b>Channels</b><br>";
         text += "Refresh list of channels for this TiVo. Note that only channels that are enabled in<br>";
         text += "your lineup are displayed unless you enable the <b>All</b> option to the left of this button.";
      }
      else if (component.equals("back_guide")){
         text = "<b>Back</b><br>";
         text += "Return to top level folder view..";
      }
      else if (component.equals("refresh_search_folder")){
         text = "<b>Back</b><br>";
         text += "Return to top level folder view.";
      }
      else if (component.equals("guide_record")){
         text = "<b>Record</b><br>";
         text += "Schedule to record selected individual show(s) in table on selected TiVo.<br>";
         text += "NOTE: You should select TiVo you want to record on before pressing this button.<br>";
         text += "Note that if there are conflicts in this time slot kmttg will print out the conflicting<br>";
         text += "shows and will not schedule the recording.<br>";
         text += "NOTE: Not available for units older than series 4.";
      }
      else if (component.equals("export_channels")){
         text = "<b>Export ...</b><br>";
         text += "Export current channel lineup of this TiVo to CSV file.<br>";
         text += "Spreadsheet includes both included and excluded channels from channel list.<br>";
         text += "This can be useful for a new TiVo to consult a spreadsheet so as to know which<br>";
         text += "channels to keep and remove.";
      }
      else if (component.equals("guide_recordSP")){
         text = "<b>Season Pass</b><br>";
         text += "Create a season pass for show selected in table below on selected TiVo.<br>";
         text += "NOTE: You should select TiVo you want to record on before pressing this button.<br>";
         text += "Note that kmttg will attempt to avoid duplicated season passes on the destination TiVo by<br>";
         text += "checking against the current set of season passes already on the TiVo and will prompt<br>";
         text += "to modify existing season pass if found.<br>";
         text += "NOTE: The Season Pass created will have lowest priority, so you may want to adjust the<br>";
         text += "priority after creating it.<br>";
         text += "NOTE: Not available for units older than series 4.";
      }
      else if (component.equals("guide_manual_record")) {
         text = "<b>MR</b><br>";
         text += "Schedule a manual recording on selected TiVo. This can be a single manual recording or<br>";
         text += "a repeating manual recording just as can be created using TiVo GUI.";
      }
      else if (component.equals("guide_refresh_todo")) {
         text = "<b>Refresh ToDo</b><br>";
         text += "Obtain fresh ToDo list from all TiVos. kmttg will obtain ToDo list automatically when you<br>";
         text += "obtain channel list, but subsequent guide searches use that same ToDo list to highlight<br>";
         text += "programs that are scheduled to record already.<br>";
         text += "This button will refresh ToDo list in case you are actively cancelling or scheduling new<br>";
         text += "recordings while browsing guide entries.";
      }
      else if (component.equals("tivo_cancel")) {
         text = "Select TiVo for which to display list of shows that will not record.<br>";
         text += "When changing TiVo selection the table below is NOT automatically updated so that you are<br>";
         text += "able to schedule to record a show on another TiVo.<br>";
         text += "NOTE: If a TiVo is missing go to Config-Tivos and turn on 'Enable iPad' setting for<br>";
         text += ">= series 4 units or provide tivo.com username & password for older units for more<br>";
         text += "limited Remote functionality. Then re-start kmttg after updating those settings.";
      }
      else if (component.equals("refresh_cancel_top")){
         text = "<b>Refresh</b><br>";
         text += "Refresh list for selected TiVo. Click on a folder in table below to see<br>";
         text += "all shows that will not record for reason matching the folder name.";
      }
      else if (component.equals("record_cancel")){
         text = "<b>Record</b><br>";
         text += "Schedule to record selected individual show(s) in table on selected TiVo.<br>";
         text += "NOTE: You should select TiVo you want to record on before pressing this button.<br>";
         text += "Note that if there are conflicts in this time slot kmttg will print out the conflicting<br>";
         text += "shows and will not schedule the recording.<br>";
         text += "NOTE: Not available for units older than series 4.";
      }
      else if (component.equals("refresh_cancel_folder")){
         text = "<b>Back</b><br>";
         text += "Return to top level folder view.";
      }
      else if (component.equals("includeHistory_cancel")){
         text = "<b>Include History</b><br>";
         text += "Include past history prior to current time if enabled.";
      }
      else if (component.equals("extendedSearch")){
         text = "<b>Include streaming</b><br>";
         text += "If enabled, include streaming titles in search in addition to TV channels.";
      }
      else if (component.equals("includeFree")){
         text = "<b>Include free content</b><br>";
         text += "If enabled, include free streaming content.";
      }
      else if (component.equals("includePaid")){
         text = "<b>Include paid content</b><br>";
         text += "If enabled, include paid streaming content.";
      }
      else if (component.equals("includeVod")){
         text = "<b>Include VOD</b><br>";
         text += "If enabled, include VOD content.";
      }
      else if (component.equals("explain_cancel")) {
         text = "<b>Explain</b><br>";
         text += "Obtains and shows conflict details in the message window for the selected show in the table.<br>";
         text += "NOTE: This is only works for shows under 'programSourceConflict' folder.";
      }
      else if (component.equals("refresh_todo_cancel")) {
         text = "<b>Refresh ToDo</b><br>";
         text += "Obtain fresh ToDo list from all TiVos. kmttg will obtain ToDo list automatically when you<br>";
         text += "refresh the Will Not Record list, but subsequent browsing of results will use that same ToDo list<br>";
         text += "to highlight shows scheduled to record on other TiVos.<br>";
         text += "This button will refresh ToDo list in case you are actively cancelling or scheduling new<br>";
         text += "recordings since last refresh of Will Not Record list.";
      }
      else if (component.equals("autoresolve")) {
         text = "<b>Autoresolve</b><br>";
         text += "Search for all conflicts of type 'programSourceConflict' on all RPC or Mind enabled<br>";
         text += "TiVos and try and automatically schedule them to record on alternate TiVos.<br>";
         text += "NOTE: This operation can take a long time to complete. Progress is periodically<br>";
         text += "printed as 'AutomaticConflictsHandler' messages to the message window. This button<br>";
         text += "is disabled until operation completes to prevent running more than once at a time.<br>";
         text += "NOTE: You can run this operation in kmttg batch mode by starting kmttg with <b>-c</b> argument<br>";
         text += "i.e: <b>java -jar kmttg.jar -c</b>. That way you can setup a scheduler to run this<br>";
         text += "automatically without having to run it manually from the GUI.";
      }
      else if (component.equals("tivo_deleted")) {
         text = "Select TiVo for which to display list of deleted shows (in Recently Deleted state)<br>";
         text += "NOTE: If a TiVo is missing go to Config-Tivos and turn on 'Enable iPad' setting for<br>";
         text += ">= series 4 units or provide tivo.com username & password for older units for more<br>";
         text += "limited Remote functionality. Then re-start kmttg after updating those settings.";
      }
      else if (component.equals("refresh_deleted")){
         text = "<b>Refresh</b><br>";
         text += "Refresh list for selected TiVo.";
      }
      else if (component.equals("recover_deleted")){
         text = "<b>Recover</b><br>";
         text += "Recover from Recently Deleted selected individual show(s) in table on specified TiVo.";
      }
      else if (component.equals("permDelete_deleted")){
         text = "<b>Permanently Delete</b><br>";
         text += "Permanently delete selected individual show(s) in table on specified TiVo.<br>";
         text += "NOTE: Once deleted these shows are removed from Recently Deleted and can't be recovered.";
      }
      else if (component.equals("tivo_search")) {
         text = "Select TiVo for which to perform search with.<br>";
         text += "When changing TiVo selection the table below is NOT automatically updated so that you are<br>";
         text += "able to schedule to record a show on another TiVo.<br>";
         text += "NOTE: If a TiVo is missing go to Config-Tivos and turn on 'Enable iPad' setting for<br>";
         text += ">= series 4 units or provide tivo.com username & password for older units for more<br>";
         text += "limited Remote functionality. Then re-start kmttg after updating those settings.";
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
      else if (component.equals("adv_search")) {
         text = "Brings up the <b>Advanced Search</b> dialog window which has more advanced search criteria<br>";
         text += "and allows you to create and save searches much like creating wishlists on a TiVo.<br>";
         text += "The results of advanced searches are displayed in the Search table in this tab.";
      }
      else if (component.equals("max_search")) {
         text = "<b>Max</b><br>";
         text += "Specify maximum number of hits to limit search to.<br>";
         text += "Depending on keywords the higher you set this limit the longer the search will take.";
      }
      else if (component.equals("record_search")) {
         text = "<b>Record</b><br>";
         text += "Schedule a one time recording of show selected in table below on selected TiVo.<br>";
         text += "NOTE: You should select TiVo you want to record on before pressing this button.<br>";
         text += "Note that if there are conflicts in this time slot kmttg will print out the conflicting<br>";
         text += "shows and will not schedule the recording.<br>";
         text += "NOTE: Not available for units older than series 4.";
      }
      else if (component.equals("record_sp_search")) {
         text = "<b>Season Pass</b><br>";
         text += "Create a season pass for show selected in table below on selected TiVo.<br>";
         text += "NOTE: You should select TiVo you want to record on before pressing this button.<br>";
         text += "Note that kmttg will attempt to avoid duplicated season passes on the destination TiVo by<br>";
         text += "checking against the current set of season passes already on the TiVo and will prompt<br>";
         text += "to modify existing season pass if found.<br>";
         text += "NOTE: The Season Pass created will have lowest priority, so you may want to adjust the<br>";
         text += "priority after creating it.<br>";
         text += "NOTE: Not available for units older than series 4.";
      }
      else if (component.equals("wishlist_search")) {
         text = "<b>Create Wishlist</b><br>";
         text += "Create a wishlist on selected TiVo. If a show is selected in table then the title will be set<br>";
         text += "NOTE: You should select TiVo you want to record on before pressing this button.<br>";
         text += "automatically to that title in the wishlist dialog that comes up.<br>";
         text += "You are prompted with wishlist dialog where you can define wishlist with boolean logic<br>";
         text += "for keywords, title keywords, actors and directors.<br>";
         text += "NOTE: Even though a title is required to be specified, the Wishlist will be named by<br>";
         text += "TiVo according to the search elements you setup.<br>";
         text += "NOTE: Existing non-autorecord wishlists are not visible or editable via RPC and have to<br>";
         text += "be managed on the TiVo itself.";
      }
      else if (component.equals("refresh_todo_search")) {
         text = "<b>Refresh ToDo</b><br>";
         text += "Obtain fresh ToDo list from all TiVos. kmttg will obtain ToDo list automatically when you<br>";
         text += "perform the first search, but subsequent searches will use that same ToDo list to highlight<br>";
         text += "programs in search results that are scheduled to record already.<br>";
         text += "This button will refresh ToDo list in case you are actively cancelling or scheduling new<br>";
         text += "recordings since running the first search.";
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
         text = "Select TiVo for which to retrieve Season Passes list.<br>";
         text += "NOTE: If a TiVo is missing go to Config-Tivos and turn on 'Enable iPad' setting for<br>";
         text += ">= series 4 units or provide tivo.com username & password for older units for more<br>";
         text += "limited Remote functionality. Then re-start kmttg after updating those settings.";
      }
      else if (component.equals("tivo_rc")) {
         text = "Select which TiVo you want to control.<br>";
         text += "NOTE: This will use RPC or telnet protocol to communicate with your TiVo(s),<br>";
         text += "so make sure network remote setting on your TiVo is enabled.";
      }
      else if (component.equals("tivo_premiere")) {
         text = "Select TiVo to use for finding shows that are Season or Series premieres.<br>";
         text += "When changing TiVo selection the table below is NOT automatically updated so that you are<br>";
         text += "able to schedule to record a show on another TiVo.<br>";
         text += "NOTE: If a TiVo is missing go to Config-Tivos and turn on 'Enable iPad' setting for<br>";
         text += ">= series 4 units or provide tivo.com username & password for older units for more<br>";
         text += "limited Remote functionality. Then re-start kmttg after updating those settings.";
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
      else if (component.equals("premiere_days")) {
         text = "Select number of days you want to search for Season & Series premieres.";
      }
      else if (component.equals("record_premiere")){
         text = "<b>Record</b><br>";
         text += "Schedule individual recording for items selected in the table below on selected TiVo.<br>";
         text += "NOTE: You should select TiVo you want to record on before pressing this button.<br>";
         text += "Note that if there are conflicts in this time slot kmttg will print out the conflicting<br>";
         text += "shows and will not schedule the recording.<br>";
         text += "NOTE: Not available for units older than series 4.";
      }
      else if (component.equals("recordSP_premiere")){
         text = "<b>Season Pass</b><br>";
         text += "Schedule season passes for shows selected in the table below on selected TiVo.<br>";
         text += "NOTE: You should select TiVo you want to record on before pressing this button.<br>";
         text += "Note that kmttg will attempt to avoid duplicated season passes on the destination TiVo by<br>";
         text += "checking against the current set of season passes already on the TiVo and will prompt<br>";
         text += "to modify existing season pass if found.<br>";
         text += "NOTE: The Season Pass created will have lowest priority, so you may want to adjust the<br>";
         text += "priority after creating it.<br>";
         text += "NOTE: Not available for units older than series 4.";
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
      else if (component.equals("export_sp")){
         text = "<b>Export</b><br>";
         text += "Export the currently displayed Season Pass list to a csv file which can be easily<br>";
         text += "imported into an Excel spreadsheet or equivalent.<br>";
         text += "<b>NOTE: This is NOT for saving/backing up season passes, use the Save button for that.";
      }
      else if (component.equals("delete_sp")){
         text = "<b>Delete</b><br>";
         text += "This is used to remove a season pass currently selected in the table from one of your TiVos.<br>";
         text += "Select the Season Pass entry in the table and then click on this button to remove it.<br>";
         text += "This will cancel the Season Pass on the TiVo as well as remove the entry from the table.<br>";
         text += "NOTE: You can also use the keyboard <b>Delete</b> button instead if you wish.";
      }
      else if (component.equals("copy_sp")){
         text = "<b>Copy</b><br>";
         text += "This is used to copy <b>loaded</b> season passes in the table to one of your TiVos.<br>";
         text += "Select the TiVo you want to copy to and then select rows in the table that you want copied,<br>";
         text += "then press this button to perform the copy.<br>";
         text += "If you want to copy from another TiVo, first switch to that TiVo and save its<br>";
         text += "season passes to a file. Then switch to destination TiVo and load the file you just saved.<br>";
         text += "Now you can select entries in the table and use this button to copy to destination TiVo.<br>";
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
      else if (component.equals("upcoming_sp")){
         text = "<b>Upcoming</b><br>";
         text += "Retrieve and show upcoming episodes of selected Season Pass entry in the table in the ToDo tab.<br>";
         text += "NOTE: Season pass titles with upcoming shows are displayed with (#) after the title indicating the<br>";
         text += "number of upcoming recordings. Titles without the (#) at the end have no upcoming recordings.";
      }
      else if (component.equals("conflicts_sp")){
         text = "<b>Conflicts</b><br>";
         text += "Retrieve and show conflicting episodes that won't record for selected Season Pass.<br>";
         text += "Any found entries will be displayed in the Won't Record table.<br>";
         text += "NOTE: Season pass entries with conflicting shows are displayed with a darker background color.";
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
      if (component.equals("tivo_web")) {
         text = "Select TiVo for which to execute given URL.<br>";
         text += "NOTE: flash type works for series 4 and later TiVos and is very limited.<br>";
         text += "NOTE: html type also works for series 4 and later TiVos.";
      }
      if (component.equals("type_web")) {
         text = "Execute provided URL as given type.<br>";
         text += "If type specified as html, send given URL to TiVo internal web browser.<br>";
         text += "If type specified as flash, send given URL to TiVo internal flash player.<br>";
         text += "NOTE: flash type works for series 4 and later TiVos and is very limited.<br>";
         text += "NOTE: html type also works for series 4 and later TiVos.";
      }
      if (component.equals("send_web")) {
         text = "<b>Execute</b><br>";
         text += "If type specified as html, send given URL to TiVo internal web browser.<br>";
         text += "If type specified as flash, send given URL to TiVo internal flash player.<br>";
         text += "WEB NAVIGATION: Use kmttg Remote keys <b>Q, A, W, S</b> to navigate the page and <b>Select</b> button to<br>";
         text += "select or execute currently highlighted item on the page.<br>";
         text += "TYPING TEXT: You can use the kmttg <b>Remote</b> tab if there are fields where you<br>";
         text += "need to enter some text since the kmttg remote understands key presses for the basic<br>";
         text += "keyboard keys.<br>";
         text += "NOTE: flash type works for series 4 and later TiVos and is very limited.<br>";
         text += "NOTE: html type also works for series 4 and later TiVos.";
      }
      if (component.equals("url_web")) {
         text = "<b>URL</b><br>";
         text += "URL to use. Press Return in this field to send the provided URL to selected TiVo<br>";
         text += "and to add the entered URL to bookmarks below.<br>";
         text += "NOTE: flash type works for series 4 and later TiVos and is very limited.<br>";
         text += "NOTE: html type also works for series 4 and later TiVos.";
      }
      if (component.equals("tivo_info")) {
         text = "Select TiVo for which to retrieve system information.<br>";
         text += "NOTE: If a TiVo is missing go to Config-Tivos and turn on 'Enable iPad' setting for<br>";
         text += ">= series 4 units or provide tivo.com username & password for older units for more<br>";
         text += "limited Remote functionality. Then re-start kmttg after updating those settings.";
      }
      if (component.equals("bookmark_web")) {
         text = "<b>Bookmark</b><br>";
         text += "Select a previously entered URL & type in this list to set as current URL & type.";
      }
      if (component.equals("remove_bookmark")) {
         text = "<b>Remove Bookmark</b><br>";
         text += "Remove currently selected bookmark from the list.";
      }
      else if (component.equals("refresh_info")){
         text = "<b>Refresh</b><br>";
         text += "Retrieve system information for selected TiVo.";
      }
      else if (component.equals("netconnect_info")){
         text = "<b>Network Connect</b><br>";
         text += "Start a Network Connection (call home) for selected TiVo.";
      }
      else if (component.equals("reboot_info")){
         text = "<b>Reboot</b><br>";
         text += "Reboot selected TiVo. This button is a macro that directs selected DVR to<br>";
         text += "the soft reboot screen and sends the 3x thumbs down + enter sequence to<br>";
         text += "reboot it - just as if you were doing so from the Help menu on DVR itself.<br>";
         text += "A confirmation prompt is used to prevent accidental use.<br>";
         text += "NOTE: This only works for series 4 or later TiVos";
      }
      else if (component.equals("rc_jumpto_text")) {
         text = "<b>Jump to minute (Alt m)</b><br>";
         text += "Set playback position to exactly this number of minutes into the show.<br>";
         text += "NOTE: You can enter non-integer values for minutes such as 0.5";
      }
      else if (component.equals("rc_jumpahead_text")) {
         text = "<b>Skip minutes ahead (Alt .)</b><br>";
         text += "Set playback position this number of minutes ahead of current position.<br>";
         text += "NOTE: You can enter non-integer values for minutes such as 0.5";
      }
      else if (component.equals("rc_jumpback_text")) {
         text = "<b>Skip minutes back (Alt ,)</b><br>";
         text += "Set playback position this number of minutes behind current position.<br>";
         text += "NOTE: You can enter non-integer values for minutes such as 0.5";
      }
      else if (component.equals("rc_hme_button")) {
         text = "<b>Launch App</b><br>";
         text += "Launch the selected application to right of this button on the selected TiVo.";
      }
      else if (component.equals("hme_rc")) {
         text = "Select which application you want to launch on the selected TiVo.";
      }
      else if (component.equals("rc_sps_button")) {
         text = "<b>SPS backdoor</b><br>";
         text += "Execute the selected SPS backdoor on the selected TiVo.";
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
      else if (component.equals("standby")){
         text = "<b>Toggle standby</b><br>";
         text += "Toggle standby mode. In off mode audio/video outputs are disabled on the TiVo<br>";
         text += "and possible recording interruptions by Emergency Alert System (EAS) are avoided.";
      }
      else if (component.equals("toggle_cc")){
         text = "<b>Toggle CC</b><br>";
         text += "Toggle closed caption display.<br>";
         text += "NOTE: Assumes initial state of off.";
      }
      else if (component.equals("My Shows")){
         text = "<b>My Shows</b><br>";
         text += "My Shows (AKA Now Playing List).";
      }
      else if (component.contains("SPS")) {
         text = SPS.get(component + "_tooltip");
      }
      if (text.length() > 0) {
         text = "<html>" + text + "</html>";
      }
      return text;
   }

}
