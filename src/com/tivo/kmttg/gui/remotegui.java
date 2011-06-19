package com.tivo.kmttg.gui;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.image.BufferedImage;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.Stack;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class remotegui {
   private JDialog dialog = null;
   private JTabbedPane tabbed_panel = null;
   
   private todoTable tab_todo = null;
   private JComboBox tivo_todo = null;
   
   private spTable tab_sp = null;
   private JComboBox tivo_sp = null;
   
   JCheckBox available_rnpl = null;
   private JComboBox tivo_rnpl = null;
   public Hashtable<String,JSONArray> rnpldata = new Hashtable<String,JSONArray>();

   remotegui(JFrame frame) {
      
      dialog = new JDialog(frame, false); // non-modal dialog
      //dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE); // Destroy when closed
      dialog.setTitle("Remote Control");
      
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
      
      tabbed_panel.add("ToDo", panel_todo);
      
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
            // Refresh to do list
            tab_sp.TABLE.clearSelection();
            tab_sp.clear();
            dialog.repaint();
            String tivoName = (String)tivo_sp.getSelectedItem();
            SPListCB(tivoName);
         }
      });
      row1_sp.add(Box.createRigidArea(space_40));
      row1_sp.add(title_sp);
      row1_sp.add(Box.createRigidArea(space_40));
      row1_sp.add(tivo_sp_label);
      row1_sp.add(Box.createRigidArea(space_5));
      row1_sp.add(tivo_sp);
      row1_sp.add(Box.createRigidArea(space_40));
      row1_sp.add(refresh_sp);
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

      tabbed_panel.add("Season Passes", panel_sp);
      
      // NPL Tab items
      c.ipady = 0;
      c.weighty = 0.0;  // default to no vertical stretch
      c.weightx = 0.0;  // default to no horizontal stretch
      c.gridx = 0;
      c.gridy = 0;
      c.gridwidth = 1;
      c.gridheight = 1;
      c.anchor = GridBagConstraints.NORTHWEST;
      c.fill = GridBagConstraints.NONE;
      JPanel panel_rnpl = new JPanel();
      panel_rnpl.setLayout(new GridBagLayout());
      
      JPanel row1_rnpl = new JPanel();
      row1_rnpl.setLayout(new BoxLayout(row1_rnpl, BoxLayout.LINE_AXIS));
      
      JLabel title_rnpl = new JLabel("My Shows");
      
      JLabel tivo_rnpl_label = new javax.swing.JLabel();
      
      tivo_rnpl = new javax.swing.JComboBox();
      tivo_rnpl.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
             if (e.getStateChange() == ItemEvent.SELECTED) {
               tivo_rnplCB();
            }
         }
      });
      tivo_rnpl.setToolTipText(getToolTip("tivo_rnpl"));

      JButton refresh_rnpl = new JButton("Refresh");
      refresh_rnpl.setToolTipText(getToolTip("refresh_rnpl"));
      refresh_rnpl.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            // Refresh to do list
            String tivoName = (String)tivo_rnpl.getSelectedItem();
            rnplListCB(tivoName);
         }
      });
      
      available_rnpl = new JCheckBox(
         "Data available for this TiVo. You can play/delete shows from NPL."
      );
      available_rnpl.setEnabled(false);
      
      row1_rnpl.add(Box.createRigidArea(space_5));
      row1_rnpl.add(title_rnpl);
      row1_rnpl.add(Box.createRigidArea(space_5));
      row1_rnpl.add(tivo_rnpl_label);
      row1_rnpl.add(Box.createRigidArea(space_5));
      row1_rnpl.add(tivo_rnpl);
      row1_rnpl.add(Box.createRigidArea(space_5));
      row1_rnpl.add(refresh_rnpl);
      
      panel_rnpl.add(row1_rnpl, c);
      c.gridy = 1;
      panel_rnpl.add(available_rnpl, c);

      tabbed_panel.add("My Shows", panel_rnpl);
      
      setTivoNames();

      // add content to and display dialog window
      dialog.setContentPane(tabbed_panel);
      dialog.pack();
      dialog.setSize((int)(frame.getSize().width/1.3), (int)(frame.getSize().height));
      dialog.setLocationRelativeTo(config.gui.getJFrame().getJMenuBar().getComponent(0));
      tab_todo.packColumns(tab_todo.TABLE, 2);
      tab_sp.packColumns(tab_sp.TABLE, 2);
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
      job.source      = dialog.toString();
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
      
   // Submit remote SP request to Job Monitor
   public void SPListCB(String tivoName) {
      jobData job = new jobData();
      job.source      = dialog.toString();
      job.tivoName    = tivoName;
      job.type        = "remote";
      job.name        = "Remote";
      job.remote_sp   = true;
      job.sp          = tab_sp;
      jobMonitor.submitNewJob(job);
   }
   
   // TiVo selection changed for My Shows tab
   public void tivo_rnplCB() {
      if (available_rnpl != null) {
         available_rnpl.setEnabled(true);
         String tivoName = getTivoName("rnpl");
         if (rnpldata.containsKey(tivoName))
            available_rnpl.setSelected(true);
         else
            available_rnpl.setSelected(false);
         available_rnpl.setEnabled(false);
      }
   }
      
   // Submit remote NPL request to Job Monitor
   public void rnplListCB(String tivoName) {
      // Clear rnpldata for this TiVo
      rnpldata.put(tivoName, new JSONArray());
      
      // Submit job to obtain new data for this TiVo
      jobData job = new jobData();
      job.source      = dialog.toString();
      job.tivoName    = tivoName;
      job.type        = "remote";
      job.name        = "Remote";
      job.remote_rnpl = true;
      job.rnpl        = rnpldata.get(tivoName);
      jobMonitor.submitNewJob(job);
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
      if (tab.equals("rnpl"))
         return (String)tivo_rnpl.getSelectedItem();
      return null;
   }
   
   public void setTivoNames() {      
      Stack<String> tivo_stack = config.getTivoNames();
      tivo_todo.removeAllItems();
      tivo_sp.removeAllItems();
      tivo_rnpl.removeAllItems();
      for (int i=0; i<tivo_stack.size(); ++i) {
         tivo_todo.addItem(tivo_stack.get(i));
         tivo_sp.addItem(tivo_stack.get(i));
         tivo_rnpl.addItem(tivo_stack.get(i));
      }
   }
   
   public void setNPLData(String tivoName, JSONArray data) {
      available_rnpl.setEnabled(true);
      if (data == null) {
         if (rnpldata.containsKey(tivoName))
            rnpldata.remove(tivoName);

      } else {
         rnpldata.put(tivoName, data);
      }
      tivo_rnplCB();
   }
   
   private ImageIcon scale(Image src, double scale) {
      int w = (int)(scale*src.getWidth(dialog));
      int h = (int)(scale*src.getHeight(dialog));
      int type = BufferedImage.TYPE_INT_RGB;
      BufferedImage dst = new BufferedImage(w, h, type);
      Graphics2D g2 = dst.createGraphics();
      g2.drawImage(src, 0, 0, w, h, dialog);
      g2.dispose();
      return new ImageIcon(dst);
   }
   
   public String findRecordingId(String tivoName, Hashtable<String,String> nplData) {
      if ( ! rnpldata.containsKey(tivoName) )
         return null;
            
      // Match up the following
      // title, recording date, size
      String h_title = null;
      long h_date = 0;
      long h_size = 0;
      if (nplData.containsKey("titleOnly"))
         h_title = nplData.get("titleOnly");
      if (nplData.containsKey("gmt"))
         h_date = Long.parseLong(nplData.get("gmt"));
      if (nplData.containsKey("size"))
         h_size = Long.parseLong(nplData.get("size"));
      if (h_title == null || h_date == 0 || h_size == 0) {
         log.error("findRecordingId insufficient NPL data");
         return null;
      }
      JSONObject json;
      String r_title;
      long r_date;
      long r_size;
      try {
         for (int i=0; i<rnpldata.get(tivoName).length(); ++i) {
            r_title = "";
            r_date = 0;
            r_size = 0;
            json = rnpldata.get(tivoName).getJSONObject(i).getJSONArray("recording").getJSONObject(0);
            if (json.has("title"))
               r_title = json.getString("title");
            if (json.has("scheduledStartTime"))
               r_date = getLongDateFromString(json.getString("scheduledStartTime"));
            else
               r_date = getLongDateFromString(json.getString("actualStartTime"));               
            if (json.has("size"))
               r_size = json.getLong("size");
            
            if (r_title.equals(h_title) && r_date == h_date && r_size == h_size) {
               if (json.has("recordingId"))
                  return json.getString("recordingId");
            }
         }
         return null;
      } catch (JSONException e1) {
         log.error("findRecordingId - " + e1.getMessage());
         return null;
      }
   }

   private long getLongDateFromString(String date) {
      try {
         SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz");
         Date d = format.parse(date + " GMT");
         return d.getTime();
      } catch (ParseException e) {
        log.error("getLongDateFromString - " + e.getMessage());
        return 0;
      }
   }
   
   private String getToolTip(String component) {
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
      else if (component.equals("tivo_sp")) {
         text = "Select TiVo for which to retrieve Season Passes list.<br>";
         text += "<b>NOTE: This only works for Premiere models</b>.";
      }
      else if (component.equals("refresh_sp")){
         text = "<b>Refresh</b><br>";
         text += "Refresh Season Pass list of selected TiVo.<br>";
         text += "<b>NOTE: This only works for Premiere models</b>.";
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
      
      if (text.length() > 0) {
         text = "<html>" + text + "</html>";
      }
      return text;
   }

}
