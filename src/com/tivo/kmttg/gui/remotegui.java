package com.tivo.kmttg.gui;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.Hashtable;
import java.util.Stack;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONFile;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class remotegui {
   private JFrame dialog = null;
   private JTabbedPane tabbed_panel = null;
   public int tivo_count = 0;
   
   private todoTable tab_todo = null;
   private JComboBox tivo_todo = null;
   
   private spTable tab_sp = null;
   private JComboBox tivo_sp = null;
   
   private JComboBox tivo_info = null;
   JTextPane text_info = null;
   private Hashtable<String,String> tivo_info_data = new Hashtable<String,String>();
   
   private cancelledTable tab_cancel = null;
   private JComboBox tivo_cancel = null;
   public JButton refresh_cancel = null;
   public JLabel label_cancel = null;
   
   private JComboBox tivo_rc = null;
   private JComboBox hme_rc = null;
   private JTextField rc_jumpto_text = null;
   private JTextField rc_jumpahead_text = null;
   private JTextField rc_jumpback_text = null;
   private Hashtable<String, String> hme = new Hashtable<String, String>();
   
   private JFileChooser Browser = null;

   remotegui(JFrame frame) {
      
      dialog = new JFrame("kmttg Remote Control");
      
      Browser = new JFileChooser(config.programDir);
      Browser.setMultiSelectionEnabled(false);
      
      // Define content for dialog window
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
               tivo_todoCB();
            }
         }
      });
      tivo_todo.setToolTipText(getToolTip("tivo_todo"));

      JButton refresh_todo = new JButton("Refresh");
      //ImageIcon image = new ImageIcon("c:/home/tivoapp/pngs/remote-button-TIVO-63x86.png");
      //JButton refresh_todo = new JButton(scale(image.getImage(),0.5));
      refresh_todo.setToolTipText(getToolTip("refresh_todo"));
      refresh_todo.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            // Refresh to do list
            tab_todo.TABLE.clearSelection();
            tab_todo.clear();
            dialog.repaint();
            String tivoName = (String)tivo_todo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0)
               ToDoListCB(tivoName);
         }
      });
      row1_todo.add(Box.createRigidArea(space_40));
      row1_todo.add(title_todo);
      row1_todo.add(Box.createRigidArea(space_40));
      row1_todo.add(tivo_todo_label);
      row1_todo.add(Box.createRigidArea(space_5));
      row1_todo.add(tivo_todo);
      row1_todo.add(Box.createRigidArea(space_40));
      row1_todo.add(refresh_todo);
      panel_todo.add(row1_todo, c);
      
      tab_todo = new todoTable(dialog);
      tab_todo.TABLE.setPreferredScrollableViewportSize(tab_todo.TABLE.getPreferredSize());
      JScrollPane tabScroll_todo = new JScrollPane(tab_todo.scroll);
      gy++;
      c.gridy = gy;
      c.weightx = 1.0;
      c.weighty = 1.0;
      c.gridwidth = 8;
      c.fill = GridBagConstraints.BOTH;
      panel_todo.add(tabScroll_todo, c);
            
      // Season Passes Tab items      
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
               tivo_spCB();
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
            dialog.repaint();
            String tivoName = (String)tivo_sp.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0)
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
               Browser.setFileSelectionMode(JFileChooser.FILES_ONLY);
               Browser.setSelectedFile(new File(config.programDir + File.separator + tivoName + ".sp"));
               int result = Browser.showDialog(dialog, "Save to file");
               if (result == JFileChooser.APPROVE_OPTION) {               
                  File file = Browser.getSelectedFile();
                  SPListSave(tivoName, file.getAbsolutePath());
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
            int result = Browser.showDialog(dialog, "Load from file");
            if (result == JFileChooser.APPROVE_OPTION) {               
               File file = Browser.getSelectedFile();
               SPListLoad(file.getAbsolutePath());
            }
         }
      });         
      
      JButton copy_sp = new JButton("Copy to TiVo");
      copy_sp.setToolTipText(getToolTip("copy_sp"));
      copy_sp.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            // Copy selected SPs to a TiVo
            String tivoName = (String)tivo_sp.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0)
               SPListCopy(tivoName);
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
      panel_sp.add(row1_sp, c);
      
      tab_sp = new spTable(dialog);
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
               tivo_cancelCB();
            }
         }
      });
      tivo_cancel.setToolTipText(getToolTip("tivo_cancel"));

      refresh_cancel = new JButton("Refresh");
      label_cancel = new JLabel("Top Level View");
      //ImageIcon image = new ImageIcon("c:/home/tivoapp/pngs/remote-button-TIVO-63x86.png");
      //JButton refresh_todo = new JButton(scale(image.getImage(),0.5));
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
               dialog.repaint();
               String tivoName = (String)tivo_cancel.getSelectedItem();
               if (tivoName != null && tivoName.length() > 0)
                  cancelListCB(tivoName);
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
      row1_cancel.add(label_cancel);
      panel_cancel.add(row1_cancel, c);
      
      tab_cancel = new cancelledTable(dialog);
      tab_cancel.TABLE.setPreferredScrollableViewportSize(tab_cancel.TABLE.getPreferredSize());
      JScrollPane tabScroll_cancel = new JScrollPane(tab_cancel.scroll);
      gy++;
      c.gridy = gy;
      c.weightx = 1.0;
      c.weighty = 1.0;
      c.gridwidth = 8;
      c.fill = GridBagConstraints.BOTH;
      panel_cancel.add(tabScroll_cancel, c);
      
      // System Information tab items      
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
               tivo_infoCB();
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
      
      // Remote Control Tab items
      gy = 0;
      c.insets = new Insets(0, 2, 0, 2);
      c.ipady = 0;
      c.weighty = 0.0;  // default to no vertical stretch
      c.weightx = 0.0;  // default to no horizontal stretch
      c.gridx = 0;
      c.gridy = gy;
      c.gridwidth = 1;
      c.gridheight = 1;
      c.anchor = GridBagConstraints.NORTHWEST;
      c.fill = GridBagConstraints.HORIZONTAL;

      JPanel panel_rc = new JPanel(new GridBagLayout());
      
      JLabel title_rc = new JLabel("Advanced Remote Controls");            
      tivo_rc = new javax.swing.JComboBox();
      tivo_rc.setToolTipText(getToolTip("tivo_rc"));

      c.gridx = 0;
      c.gridy = gy;
      panel_rc.add(title_rc, c);
      c.gridx = 1;
      c.gridy = gy;
      panel_rc.add(tivo_rc, c);

      JButton rc_jumpto_button = new JButton("Jump to minute:");
      rc_jumpto_button.setToolTipText(getToolTip("rc_jumpto_text"));
      rc_jumpto_button.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String tivoName = (String)tivo_rc.getSelectedItem();
            String mins_string = string.removeLeadingTrailingSpaces(rc_jumpto_text.getText());
            if (tivoName == null || tivoName.length() == 0)
               return;
            if (mins_string == null || mins_string.length() == 0)
               return;
            try {
               int mins = Integer.parseInt(mins_string);
               RC_jumptoCB(tivoName, mins);
            } catch (NumberFormatException e1) {
               log.error("Illegal number of minutes specified: " + mins_string);
               return;
            }            
         }
      });
      rc_jumpto_text = new JTextField(15);
      rc_jumpto_text.setToolTipText(getToolTip("rc_jumpto_text"));
      rc_jumpto_text.setText("0");
      
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      panel_rc.add(rc_jumpto_button, c);
      c.gridx = 1;
      panel_rc.add(rc_jumpto_text, c);

      JButton rc_jumpahead_button = new JButton("Skip minutes ahead:");
      rc_jumpahead_button.setToolTipText(getToolTip("rc_jumpahead_text"));
      rc_jumpahead_button.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String tivoName = (String)tivo_rc.getSelectedItem();
            String mins_string = string.removeLeadingTrailingSpaces(rc_jumpahead_text.getText());
            if (tivoName == null || tivoName.length() == 0)
               return;
            if (mins_string == null || mins_string.length() == 0)
               return;
            try {
               int mins = Integer.parseInt(mins_string);
               RC_jumpaheadCB(tivoName, mins);
            } catch (NumberFormatException e1) {
               log.error("Illegal number of minutes specified: " + mins_string);
               return;
            }            
         }
      });
      rc_jumpahead_text = new JTextField(15);
      rc_jumpahead_text.setToolTipText(getToolTip("rc_jumpahead_text"));
      rc_jumpahead_text.setText("5");
      
      gy++;
      c.gridx = 0;
      c.gridy = gy;      
      panel_rc.add(rc_jumpahead_button, c);
      c.gridx = 1;
      panel_rc.add(rc_jumpahead_text, c);

      JButton rc_jumpback_button = new JButton("Skip minutes back:");
      rc_jumpback_button.setToolTipText(getToolTip("rc_jumpback_text"));
      rc_jumpback_button.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String tivoName = (String)tivo_rc.getSelectedItem();
            String mins_string = string.removeLeadingTrailingSpaces(rc_jumpback_text.getText());
            if (tivoName == null || tivoName.length() == 0)
               return;
            if (mins_string == null || mins_string.length() == 0)
               return;
            try {
               int mins = Integer.parseInt(mins_string);
               RC_jumpbackCB(tivoName, mins);
            } catch (NumberFormatException e1) {
               log.error("Illegal number of minutes specified: " + mins_string);
               return;
            }            
         }
      });
      rc_jumpback_text = new JTextField(15);
      rc_jumpback_text.setToolTipText(getToolTip("rc_jumpback_text"));
      rc_jumpback_text.setText("5");
      gy++;
      c.gridx = 0;
      c.gridy = gy;      
      panel_rc.add(rc_jumpback_button, c);
      c.gridx = 1;
      panel_rc.add(rc_jumpback_text, c);
   
      hme_rc = new javax.swing.JComboBox();
      hme_rc.setToolTipText(getToolTip("hme_rc"));

      JButton rc_hme_button = new JButton("HME Jump:");
      rc_hme_button.setToolTipText(getToolTip("rc_hme_button"));
      rc_hme_button.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            String name = (String)hme_rc.getSelectedItem();
            if (name != null && name.length() > 0) {
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
            }
         }
      });
      
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      panel_rc.add(rc_hme_button, c);
      c.gridx = 1;
      panel_rc.add(hme_rc, c);
      
      tabbed_panel.add("ToDo", panel_todo);
      tabbed_panel.add("Season Passes", panel_sp);
      tabbed_panel.add("Will Not Record", panel_cancel);
      tabbed_panel.add("Advanced Controls", panel_rc);
      tabbed_panel.add("System Information", panel_info);
      
      setTivoNames();
            
      // add content to and display dialog window
      dialog.setContentPane(tabbed_panel);
      dialog.pack();
      dialog.setSize((int)(frame.getSize().width/1.3), (int)(frame.getSize().height/2));
      dialog.setLocationRelativeTo(config.gui.getJFrame().getJMenuBar().getComponent(0));
      tab_todo.packColumns(tab_todo.TABLE, 2);
      tab_sp.packColumns(tab_sp.TABLE, 2);
      tab_cancel.packColumns(tab_sp.TABLE, 2);
      if (tivo_count == 0) {
         log.warn("No Premieres currently enabled for Remote Control in kmttg configuration");
         return;
      }
      
      if (config.gui.remote_gui_dimensions.size() > 0) {
         // Previously saved dimensions for the remote
         int width = -1, height = -1, x = -1, y = -1;
         if (config.gui.remote_gui_dimensions.containsKey("width"))
            width = config.gui.remote_gui_dimensions.get("width");
         if (config.gui.remote_gui_dimensions.containsKey("height"))
            height = config.gui.remote_gui_dimensions.get("height");
         if (config.gui.remote_gui_dimensions.containsKey("x"))
            x = config.gui.remote_gui_dimensions.get("x");
         if (config.gui.remote_gui_dimensions.containsKey("y"))
            y = config.gui.remote_gui_dimensions.get("y");
         if (width != -1 && height != -1)
            dialog.setSize(new Dimension(width,height));
         if (x != -1 && y != -1)
            dialog.setLocation(new Point(x,y));
      }
      dialog.setVisible(true);      
   }
   
   // TiVo selection changed for ToDo tab
   public void tivo_todoCB() {
      tab_todo.TABLE.clearSelection();
      tab_todo.clear();
      String tivoName = getTivoName("todo");
      if (tab_todo.tivo_data.containsKey(tivoName))
         tab_todo.AddRows(tivoName, tab_todo.tivo_data.get(tivoName));
   }
      
   // Submit remote ToDo List request to Job Monitor
   public void ToDoListCB(String tivoName) {
      jobData job = new jobData();
      job.source      = tivoName;
      job.tivoName    = tivoName;
      job.type        = "remote";
      job.name        = "Remote";
      job.remote_todo = true;
      job.todo        = tab_todo;
      jobMonitor.submitNewJob(job);
   }
   
   // TiVo selection changed for Season Passes tab
   public void tivo_spCB() {
      tab_sp.TABLE.clearSelection();
      tab_sp.clear();
      String tivoName = getTivoName("sp");
      if (tab_sp.tivo_data.containsKey(tivoName))
         tab_sp.AddRows(tivoName, tab_sp.tivo_data.get(tivoName));
   }
   
   // TiVo selection changed for System Information tab
   public void tivo_infoCB() {
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
   
   public Boolean RC_jumptoCB(String tivoName, Integer mins) {
      Remote r = new Remote(tivoName);
      if (r.success) {
         JSONObject json = new JSONObject();
         try {
            System.out.println("tivoName=" + tivoName + " mins=" + mins);
            Long pos = (long)60000*mins;
            json.put("offset", pos);
            r.Command("jump", json);
            r.disconnect();
         } catch (JSONException e) {
            log.error("RC_jumptoCB failed - " + e.getMessage());
            return false;
         }
      }
      return true;
   }
   
   public Boolean RC_jumpaheadCB(String tivoName, Integer mins) {
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
               r.disconnect();
            } catch (JSONException e) {
               log.error("RC_jumptoCB failed - " + e.getMessage());
               return false;
            }
         }
      }
      return true;
   }
   
   public Boolean RC_jumpbackCB(String tivoName, Integer mins) {
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
               r.disconnect();
            } catch (JSONException e) {
               log.error("RC_jumptoCB failed - " + e.getMessage());
               return false;
            }
         }
      }
      return true;
   }
   
   // Submit remote Not Record request to Job Monitor
   public void cancelListCB(String tivoName) {
      jobData job = new jobData();
      job.source        = tivoName;
      job.tivoName      = tivoName;
      job.type          = "remote";
      job.name          = "Remote";
      job.remote_cancel = true;
      job.cancelled     = tab_cancel;
      jobMonitor.submitNewJob(job);
   }
   
   // TiVo selection changed for Not Record tab
   public void tivo_cancelCB() {
      tab_cancel.setFolderState(false);
      tab_cancel.TABLE.clearSelection();
      tab_cancel.clear();
      String tivoName = getTivoName("cancel");
      if (tab_cancel.tivo_data.containsKey(tivoName))
         tab_cancel.AddRows(tivoName, tab_cancel.tivo_data.get(tivoName));
   }
   
   private Boolean RC_infoCB(String tivoName) {
      Remote r = new Remote(tivoName);
      if (r.success) {
         JSONObject json = new JSONObject();
         JSONObject reply = r.Command("sysInfo", json);
         if (reply != null && reply.has("bodyConfig")) {
            try {
               String info = "";
               // System info
               json = reply.getJSONArray("bodyConfig").getJSONObject(0);
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
               r.disconnect();
               
               // Add info to text_info widget
               text_info.setEditable(true);
               text_info.setText(info);
               text_info.setEditable(false);
               tivo_info_data.put(tivoName, info);

            } catch (JSONException e) {
               log.error("RC_infoCB failed - " + e.getMessage());
               return false;
            }
         }
      }
      return true;
   }
   
   private void SPListSave(String tivoName, String file) {
      if (tab_sp.tivo_data.containsKey(tivoName) && tab_sp.tivo_data.get(tivoName).length() > 0) {
         log.warn("Saving '" + tivoName + "' SP list to file: " + file);
         JSONFile.write(tab_sp.tivo_data.get(tivoName), file);
      } else {
         log.error("No data available to save.");
      }
   }
   
   private void SPListLoad(String file) {
      log.print("Loading SP data from file: " + file);
      JSONArray data = JSONFile.readJSONArray(file);
      if (data != null && data.length() > 0) {
         tab_sp.clear();
         tab_sp.AddRows(data);
         tab_sp.updateTitleCols(" Loaded:");
      }
   }
   
   private void SPListCopy(String tivoName) {
      //SeasonPasses
      int[] selected = tab_sp.GetSelectedRows();
      if (selected.length > 0) {
         int row;
         JSONArray existing;
         JSONObject json, result;
         Remote r = new Remote(tivoName);
         if (r.success) {
            // First load existing SPs from tivoName to check against
            existing = r.SeasonPasses();
            if (existing == null) {
               log.error("Failed to grab existing SPs to check against for TiVo: " + tivoName);
               return;
            }
            // Now proceed with subscriptions
            log.print("Copying Season Passes to TiVo: " + tivoName);
            for (int i=0; i<selected.length; ++i) {
               row = selected[i];
               json = tab_sp.GetRowData(row);
               if (json != null) {
                  try {
                     // Check against existing
                     Boolean schedule = true;
                     for (int j=0; j<existing.length(); ++j) {
                        if(json.getString("title").equals(existing.getJSONObject(j).getString("title")))
                           schedule = false;
                     }
                     
                     // OK to subscribe
                     if (schedule) {
                        log.print("Scheduling: " + json.getString("title"));
                        result = r.Command("seasonpass", json);
                        if (result != null)
                           log.print(result.toString());
                     } else {
                        log.warn("Existing SP with same title found, not scheduling: " + json.getString("title"));
                     }
                  } catch (JSONException e) {
                     log.error("SPListCopy - " + e.getMessage());
                  }
               }
            }
            r.disconnect();
         }
      }
   }
            
   public void display() {
      if (dialog != null)
         dialog.setVisible(true);
   }
   
   public String getTivoName(String tab) {
      if (tab.equals("todo"))
         return (String)tivo_todo.getSelectedItem();
      if (tab.equals("sp"))
         return (String)tivo_sp.getSelectedItem();
      if (tab.equals("cancel"))
         return (String)tivo_cancel.getSelectedItem();
      if (tab.equals("rc"))
         return (String)tivo_rc.getSelectedItem();
      if (tab.equals("info"))
         return (String)tivo_info.getSelectedItem();
      return null;
   }
   
   public void setTivoName(String tab, String tivoName) {
      String current = getTivoName(tab);
      if ( ! tivoName.equals(current)) {
         if (tab.equals("todo"))
            tivo_todo.setSelectedItem(tivoName);
         if (tab.equals("sp"))
            tivo_sp.setSelectedItem(tivoName);
         if (tab.equals("cancel"))
            tivo_cancel.setSelectedItem(tivoName);
         if (tab.equals("rc"))
            tivo_rc.setSelectedItem(tivoName);
         if (tab.equals("info"))
            tivo_info.setSelectedItem(tivoName);
      }
   }
   
   public void setTivoNames() { 
      tivo_count = 0;
      Stack<String> tivo_stack = config.getTivoNames();
      tivo_todo.removeAllItems();
      tivo_sp.removeAllItems();
      tivo_cancel.removeAllItems();
      tivo_rc.removeAllItems();
      tivo_info.removeAllItems();
      for (int i=0; i<tivo_stack.size(); ++i) {
         if (config.getRpcSetting(tivo_stack.get(i)).equals("1")) {
            tivo_count++;
            tivo_todo.addItem(tivo_stack.get(i));
            tivo_sp.addItem(tivo_stack.get(i));
            tivo_cancel.addItem(tivo_stack.get(i));
            tivo_rc.addItem(tivo_stack.get(i));
            tivo_info.addItem(tivo_stack.get(i));
         }
      }
      if (tivo_count > 0)
         setHmeDestinations(getTivoName("rc"));
   }
   
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
   
   public void setHmeDestinations(String tivoName) {
      String[] hmeNames = getHmeDestinations(tivoName);
      hme_rc.removeAllItems();
      for (int i=0; i<hmeNames.length; ++i)
         hme_rc.addItem(hmeNames[i]);
   }
      
   /*private ImageIcon scale(Image src, double scale) {
      int w = (int)(scale*src.getWidth(dialog));
      int h = (int)(scale*src.getHeight(dialog));
      int type = BufferedImage.TYPE_INT_RGB;
      BufferedImage dst = new BufferedImage(w, h, type);
      Graphics2D g2 = dst.createGraphics();
      g2.drawImage(src, 0, 0, w, h, dialog);
      g2.dispose();
      return new ImageIcon(dst);
   }*/
   
   public Dimension getDimension() {
      return dialog.getSize();
   }
   
   public Point getLocation() {
      return dialog.getLocation();
   }
      
   public String getToolTip(String component) {
      debug.print("component=" + component);
      String text = "";
      if (component.equals("tivo_todo")) {
         text = "Select TiVo for which to retrieve To Do list.<br>";
         text += "<b>NOTE: This only works for Premiere models</b>.";
      }
      else if (component.equals("refresh_todo")){
         text = "<b>Refresh</b><br>";
         text += "Refresh To Do list of selected TiVo.<br>";
         text += "<b>NOTE: This only works for Premiere models</b>.";
      }
      else if (component.equals("tivo_cancel")) {
         text = "Select TiVo for which to display list of shows that will not record.<br>";
         text += "<b>NOTE: This only works for Premiere models</b>.";
      }
      else if (component.equals("refresh_cancel_top")){
         text = "<b>Refresh</b><br>";
         text += "Refresh list for selected TiVo. Click on a folder in table below to see<br>";
         text += "all shows that will not record for reason matching the folder name.<br>";
         text += "<b>NOTE: This only works for Premiere models</b>.";
      }
      else if (component.equals("refresh_cancel_folder")){
         text = "<b>Back</b><br>";
         text += "Return to top level folder view.<br>";
         text += "<b>NOTE: This only works for Premiere models</b>.";
      }
      else if (component.equals("tivo_sp")) {
         text = "Select TiVo for which to retrieve Season Passes list.<br>";
         text += "<b>NOTE: This only works for Premiere models</b>.";
      }
      else if (component.equals("tivo_rc")) {
         text = "Select which TiVo you want to control.<br>";
         text += "<b>NOTE: This only works for Premiere models</b>.";
      }
      else if (component.equals("refresh_sp")){
         text = "<b>Refresh</b><br>";
         text += "Refresh Season Pass list of selected TiVo. Note that by selecting 1 or more rows in the<br>";
         text += "table and using keyboard <b>Delete</b> button you can unsubscribe Season Passes.<br>";
         text += "<b>NOTE: This only works for Premiere models</b>.";
      }
      else if (component.equals("cancel_sp")){
         text = "<b>Refresh</b><br>";
         text += "Refresh Not Record list of selected TiVo. Click on folder in table to see all<br>";
         text += "entries associated with it.<br>";
         text += "<b>NOTE: This only works for Premiere models</b>.";
      }
      else if (component.equals("save_sp")){
         text = "<b>Save</b><br>";
         text += "Save the currently displayed Season Pass list to a file. This file can then be loaded<br>";
         text += "at a later date into this table, then entries from the table can be copied to your TiVos<br>";
         text += "if desired by selecting entries in the table and clicking on <b>Copy to TiVo</b> button.<br>";
         text += "i.e. This is a way to backup your season passes.";
      }
      else if (component.equals("load_sp")){
         text = "<b>Load</b><br>";
         text += "Load a previously saved Season Pass list from a file. When loaded the table will have a<br>";
         text += "<b>Loaded: </b> prefix in the TITLE column indicating that these were loaded from a file<br>";
         text += "to distinguish from normal case where they were obtained from displayed TiVo name.<br>";
         text += "Note that loaded season passes can then be copied to TiVos by selecting the TiVo you want to<br>";
         text += "copy to, then selecting rows in the table you want to copy and then clicking on the <b>Copy to TiVo</b><br>";
         text += "button.";
      }
      else if (component.equals("copy_sp")){
         text = "<b>Copy to TiVo</b><br>";
         text += "This is used to copy season passes displayed in the Season Pass table to 1 of your TiVos.<br>";
         text += "Select the TiVo you want to copy to and then select rows in the table that you want copied,<br>";
         text += "then press this button to perform the copy.<br>";
         text += "Note that kmttg will attempt to avoid duplicated season passes on the destination TiVo by<br>";
         text += "checking against the current set of season passes already on the TiVo.";
      }
      else if (component.equals("tivo_rnpl")) {
         text = "Select TiVo for which to retrieve My Shows list.<br>";
         text += "<b>NOTE: This only works for Premiere models</b>.<br>";
         text += "NOTE: You can select items in table to PLAY or DELETE<br>";
         text += "using <b>Space bar</b> to play, <b>Delete</b> button to delete.<br>";
         text += "NOTE: Only 1 item can be deleted or played at a time.";
      }
      else if (component.equals("refresh_rnpl")){
         text = "<b>Refresh</b><br>";
         text += "Refresh My Shows list of selected TiVo.<br>";
         text += "<b>NOTE: This only works for Premiere models</b>.<br>";
         text += "NOTE: You can select items in table to PLAY or DELETE<br>";
         text += "using <b>Space bar</b> to play, <b>Delete</b> button to delete.<br>";
         text += "NOTE: Only 1 item can be deleted or played at a time.";
      }
      else if (component.equals("tab_rnpl")) {
         text = "NOTE: You can select items in table to PLAY or DELETE<br>";
         text += "using <b>Space bar</b> to play, <b>Delete</b> button to delete.<br>";
         text += "NOTE: Only 1 item can be deleted or played at a time.";
      }
      else if (component.equals("rc_jumpto_text")) {
         text = "Set playback position to exactly this number of minutes into the show.";
      }
      else if (component.equals("rc_jumpahead_text")) {
         text = "Set playback position this number of minutes ahead of current position.";
      }
      else if (component.equals("rc_jumpback_text")) {
         text = "Set playback position this number of minutes behind current position.";
      }
      else if (component.equals("rc_hme_button")) {
         text = "Jump to the specified HME application for the selected TiVo.";
      }
      else if (component.equals("hme_rc")) {
         text = "Select which HME application you want to jump to for selected TiVo.";
      }
      
      if (text.length() > 0) {
         text = "<html>" + text + "</html>";
      }
      return text;
   }

}
