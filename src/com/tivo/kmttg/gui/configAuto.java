package com.tivo.kmttg.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
//import java.awt.event.ActionEvent;
//import java.awt.event.ActionListener;
import java.awt.event.HierarchyBoundsListener;
import java.awt.event.HierarchyEvent;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Stack;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

import org.jdesktop.swingx.JXTable;

import com.tivo.kmttg.main.autoConfig;
import com.tivo.kmttg.main.autoEntry;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.encodeConfig;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class configAuto {
   private static Stack<JTextField> errors = new Stack<JTextField>();
   private static Color textbg_default = null;
   
   private static JDialog dialog = null;
   private static JPanel content = null;
   private static JButton add = null;
   private static JButton del = null;
   private static JButton update = null;
   private static JTextPane text = null;
   private static JXTable table = null;
   private static JScrollPane table_scroll = null;
   private static JComboBox type = null;
   private static JComboBox tivo = null;
   private static JComboBox encoding_name = null;
   private static JCheckBox enabled = null;
   private static JCheckBox metadata = null;
   private static JCheckBox decrypt = null;
   private static JCheckBox qsfix = null;
   private static JCheckBox twpdelete = null;
   private static JCheckBox ipaddelete = null;
   private static JCheckBox comskip = null;
   private static JCheckBox comcut = null;
   private static JCheckBox captions = null;
   private static JCheckBox encode = null;
   private static JCheckBox push = null;
   private static JCheckBox custom = null;
   private static JCheckBox dry_run = null;
   private static JTextField title = null;
   private static JTextField check_interval = null;
   private static JTextField comskipIni = null;
   private static JTextField channelFilter = null;
   private static JCheckBox dateFilter = null;
   private static JCheckBox suggestionsFilter = null;
   private static JCheckBox suggestionsFilter_single = null;
   private static JCheckBox useProgramId_unique = null;
   private static JCheckBox kuidFilter = null;
   private static JCheckBox programIdFilter = null;
   private static JComboBox dateOperator = null;
   private static JTextField dateHours = null;
   private static JButton OK = null;
   private static JButton CANCEL = null;

   public void display(JFrame frame) {
      debug.print("frame=" + frame);
      // Create dialog if not already created
      if (dialog == null) {
         create(frame);
         // Set component tooltips
         setToolTips();
      }
      
      // Parse auto.ini file to define current configuration
      autoConfig.parseAuto(config.autoIni);
      
      // Clear out any error highlights
      clearTextFieldErrors();
      
      // Update component settings to current configuration
      update();
      
      // Refresh available options based on settings
      refreshOptions();
      
      // Display the dialog
      dialog.setVisible(true);
   }
   
   public static JDialog getDialog() {
      return dialog;
   }
   
   private void textFieldError(JTextField f, String message) {
      debug.print("f=" + f + " message=" + message);
      log.error(message);
      f.setBackground(config.lightRed);
      errors.add(f);
   }
   
   private void clearTextFieldErrors() {
      debug.print("");
      if (errors.size() > 0) {
         for (int i=0; i<errors.size(); i++) {
            errors.get(i).setBackground(textbg_default);
         }
         errors.clear();
      }
   }
  
   private void create(JFrame frame) {
      debug.print("frame=" + frame);
      
      // Create all the components of the dialog
      Object[][] data = {};
      String[] headers = {"Type", "Keywords"};
      table = new JXTable(data, headers);
      TableModel myModel = new configAuto.MyTableModel(data, headers);
      table.setModel(myModel);
      //table.setFillsViewportHeight(true);
      table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
      table_scroll = new JScrollPane(table);
      table_scroll.setPreferredSize(new Dimension(0, 100));
      
      // Define selection listener to update dialog fields according
      // to selected row
      table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
         public void valueChanged(ListSelectionEvent e) {
            TableRowSelected(table.getSelectedRow());
         }
      });
            
      text = new JTextPane();
      String message = "for Type=keywords: Multiple keywords are allowed separated by '| character";
      message += "\nkeyword=>AND  (keyword)=>OR  -keyword=>NOT";
      message += "\nEXAMPLE: Type=keywords  keywords=(basketball)|(football)|!new york";
      message += "\n  => football OR basketball NOT new york";
      text.setText(message);
      text.setEditable(false);
      
      add = new JButton("ADD");
      add.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            addCB();
         }
      });

      update = new JButton("UPDATE");
      update.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            updateCB();
         }
      });

      del = new JButton("DEL");
      del.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            delCB();
         }
      });

      JLabel type_label = new JLabel("Type");
      type = new JComboBox(new Object[] {"title", "keywords"});
      
      JLabel tivo_label = new JLabel("TiVo");
      tivo = new JComboBox(getTivoFilterNames());
      
      title = new JTextField();
            
      enabled   = new JCheckBox("enabled", true);
      metadata  = new JCheckBox("metadata");
      decrypt   = new JCheckBox("decrypt");
      qsfix     = new JCheckBox("QS Fix");
      twpdelete = new JCheckBox("TWP Delete");
      ipaddelete = new JCheckBox("iPad Delete");
      comskip   = new JCheckBox("Ad Detect");
      comcut    = new JCheckBox("Ad Cut");
      captions  = new JCheckBox("captions");
      encode    = new JCheckBox("encode");
      push      = new JCheckBox("push");
      suggestionsFilter_single = new JCheckBox("Filter out TiVo Suggestions");
      useProgramId_unique = new JCheckBox("Treat each recording as unique");
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
      custom   = new JCheckBox("custom");
      
      JLabel comskipIni_label = new JLabel("comskip.ini override: ");
      comskipIni = new JTextField(30);
      
      JLabel channelFilter_label = new JLabel("channel filter: ");
      channelFilter = new JTextField(30);
      
      JLabel encoding_name_label = new JLabel("Encoding Name: ");
      
      encoding_name = new JComboBox();
      SetEncodings(encodeConfig.getValidEncodeNames());
      
      JLabel global_settings = new JLabel("GLOBAL SETTINGS:");
      
      JLabel check_interval_label = new JLabel("Check Tivos Interval (mins)");
      
      check_interval = new JTextField(5);
      check_interval.setText("" + autoConfig.CHECK_TIVOS_INTERVAL);
      
      dry_run = new JCheckBox("Dry Run Mode (test keywords only)");
      dry_run.setSelected((Boolean)(autoConfig.dryrun == 1));
      
      dateFilter = new JCheckBox("Date Filter");
      dateOperator = new JComboBox();
      dateOperator.addItem("more than");
      dateOperator.addItem("less than");
      dateHours = new JTextField("48");
      JLabel dateHours_label = new JLabel("hours old");
      
      suggestionsFilter = new JCheckBox("Filter out TiVo Suggestions");
      
      kuidFilter = new JCheckBox("Only process KUID recordings");
      
      programIdFilter = new JCheckBox("Do not process recordings without ProgramId");
      
      OK = new JButton("OK");
      OK.setBackground(Color.green);
      OK.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            okCB();
         }
      });
      
      CANCEL = new JButton("CANCEL");
      CANCEL.setBackground(config.lightRed);
      CANCEL.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            dialog.setVisible(false);
         }
      });
      
      // layout manager start
      content = new JPanel(new GridBagLayout());
      GridBagConstraints c = new GridBagConstraints();
      // Pack table columns when content pane resized
      content.addHierarchyBoundsListener(new HierarchyBoundsListener() {
         public void ancestorMoved(HierarchyEvent arg0) {
            // Don't care about movement
         }
         public void ancestorResized(HierarchyEvent arg0) {
            packColumns(table, 2);
         }
      });
      
      int gy = 0;
      c.insets = new Insets(4, 2, 4, 2);
      c.ipady = 0;
      c.weighty = 0.0;  // default to no vertical stretch
      c.weightx = 0.0;  // default to no horizontal stretch
      c.gridx = 0;
      c.gridy = gy;
      c.gridwidth = 1;
      c.gridheight = 1;
      c.anchor = GridBagConstraints.CENTER;
      c.fill = GridBagConstraints.NONE;
      
      // table
      c.gridy = gy;
      c.fill = GridBagConstraints.BOTH;
      c.weightx = 1.0;
      c.weighty = 1.0;
      content.add(table_scroll, c);

      // text pane
      gy++;
      c.gridy = gy;
      c.fill = GridBagConstraints.HORIZONTAL;
      c.weightx = 1.0;
      c.weighty = 0.0;
      content.add(text, c);

      // row 3 items
      JPanel row3 = new JPanel();
      row3.setLayout(new BoxLayout(row3, BoxLayout.X_AXIS));
      Dimension space_5 = new Dimension(5,0);
      Dimension space_10 = new Dimension(10,0);
      Dimension space_50 = new Dimension(50,0);
      row3.add(type_label);
      row3.add(Box.createRigidArea(space_5));
      row3.add(type);
      row3.add(Box.createRigidArea(space_5));
      row3.add(tivo_label);
      row3.add(Box.createRigidArea(space_5));
      row3.add(tivo);
      row3.add(Box.createRigidArea(space_5));
      row3.add(title);
            
      gy++;
      c.gridy = gy;
      c.fill = GridBagConstraints.HORIZONTAL;
      c.anchor = GridBagConstraints.CENTER;
      c.weightx = 0.0;
      content.add(row3, c);
      
      // row4
      int GAP = 0;
      JPanel row4 = new JPanel();
      row4.setLayout(new BoxLayout(row4, BoxLayout.X_AXIS));
      row4.add(metadata);
      row4.add(Box.createRigidArea(space_5));
      row4.add(decrypt);
      row4.add(Box.createRigidArea(space_5));
      row4.add(qsfix);
      if (config.TivoWebPlusDelete == 1) {
         row4.add(Box.createRigidArea(space_5));
         row4.add(twpdelete);         
      }
      if (config.ipadDeleteEnabled()) {
         row4.add(Box.createRigidArea(space_5));
         row4.add(ipaddelete);         
      }
      row4.add(Box.createRigidArea(space_5));
      row4.add(comskip);
      row4.add(Box.createRigidArea(space_5));
      row4.add(comcut);
      row4.add(Box.createRigidArea(space_5));
      row4.add(captions);
      row4.add(Box.createRigidArea(space_5));
      row4.add(encode);
      row4.add(Box.createRigidArea(space_5));
      row4.add(push);
      row4.add(Box.createRigidArea(space_5));
      row4.add(custom);

      gy++;
      c.gridy = gy;
      c.fill = GridBagConstraints.NONE;
      c.anchor = GridBagConstraints.WEST;
      c.weightx = 0.0;
      content.add(row4, c);
      
      // row5
      JPanel row5 = new JPanel();
      row5.setLayout(new BoxLayout(row5, BoxLayout.X_AXIS));
      row5.add(encoding_name_label);
      row5.add(Box.createRigidArea(space_10));
      row5.add(encoding_name);
      row5.add(Box.createRigidArea(space_50));
      row5.add(add);
      row5.add(Box.createRigidArea(space_10));
      row5.add(update);
      row5.add(Box.createRigidArea(space_10));
      row5.add(del);

      gy++;
      c.gridy = gy;
      c.fill = GridBagConstraints.NONE;
      c.anchor = GridBagConstraints.WEST;
      c.weightx = 0.0;
      content.add(row5, c);
      
      // comskip.ini override      
      JPanel row_comskip = new JPanel();
      row_comskip.setLayout(new BoxLayout(row_comskip, BoxLayout.X_AXIS));
      row_comskip.add(comskipIni_label);
      row_comskip.add(Box.createRigidArea(space_5));
      row_comskip.add(comskipIni);
      
      gy++;
      c.gridy = gy;
      c.fill = GridBagConstraints.HORIZONTAL;
      c.anchor = GridBagConstraints.CENTER;
      c.weightx = 0.0;
      content.add(row_comskip, c); 
      
      // row_channelFilter
      JPanel row_channelFilter = new JPanel();
      row_channelFilter.setLayout(new BoxLayout(row_channelFilter, BoxLayout.X_AXIS));
      row_channelFilter.add(channelFilter_label);
      row_channelFilter.add(Box.createRigidArea(space_5));
      row_channelFilter.add(channelFilter);
      
      gy++;
      c.gridy = gy;
      c.fill = GridBagConstraints.HORIZONTAL;
      c.anchor = GridBagConstraints.CENTER;
      c.weightx = 0.0;
      content.add(row_channelFilter, c); 
      
      // Filter out TiVo Suggestions
      gy++;
      c.gridy = gy;
      c.fill = GridBagConstraints.NONE;
      c.anchor = GridBagConstraints.WEST;
      c.weightx = 0.0;
      content.add(suggestionsFilter_single, c);
      
      // Treat each recording as unique
      gy++;
      c.gridy = gy;
      c.fill = GridBagConstraints.NONE;
      c.anchor = GridBagConstraints.WEST;
      c.weightx = 0.0;
      content.add(useProgramId_unique, c);
      
      // enabled
      gy++;
      c.gridy = gy;
      c.fill = GridBagConstraints.NONE;
      c.anchor = GridBagConstraints.WEST;
      c.weightx = 0.0;
      content.add(enabled, c);
      
      // Add, Update, Del
      JPanel buttons = new JPanel();
      buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
      buttons.add(add);
      buttons.add(Box.createRigidArea(space_5));
      buttons.add(update);
      buttons.add(Box.createRigidArea(space_5));
      buttons.add(del);
      gy++;
      c.gridy = gy;
      c.fill = GridBagConstraints.NONE;
      c.anchor = GridBagConstraints.CENTER;
      c.weightx = 0.0;
      content.add(buttons, c); 
            
      // separator
      JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
      gy++;
      c.gridy = gy;
      c.fill = GridBagConstraints.HORIZONTAL;
      c.anchor = GridBagConstraints.WEST;
      c.weightx = 1.0;
      content.add(sep, c);
                        
      // global_settings
      gy++;
      c.gridy = gy;
      c.fill = GridBagConstraints.NONE;
      c.weightx = 0.0;
      content.add(global_settings, c);
      
      // row_dry_run
      JPanel row_dry_run = new JPanel();
      row_dry_run.setLayout(new BoxLayout(row_dry_run, BoxLayout.X_AXIS));
      row_dry_run.add(dry_run);
      row_dry_run.add(Box.createRigidArea(space_10));
      row_dry_run.add(check_interval_label);
      row_dry_run.add(Box.createRigidArea(space_10));
      row_dry_run.add(check_interval);
            
      gy++;
      c.gridy = gy;
      c.fill = GridBagConstraints.HORIZONTAL;
      c.weightx = 0.0;
      content.add(row_dry_run, c);
      
      // date filter row
      GAP = 0;
      JPanel date = new JPanel();
      date.setLayout(new BoxLayout(date, BoxLayout.X_AXIS));
      date.add(dateFilter);
      date.add(Box.createRigidArea(space_5));
      date.add(dateOperator);
      date.add(Box.createRigidArea(space_5));
      date.add(dateHours);
      date.add(Box.createRigidArea(space_5));
      date.add(dateHours_label);

      gy++;
      c.gridy = gy;
      c.fill = GridBagConstraints.HORIZONTAL;
      c.weightx = 0.0;
      content.add(date, c);

      JPanel filter_panel = new JPanel();
      filter_panel.setLayout(new BoxLayout(filter_panel, BoxLayout.X_AXIS));
      filter_panel.add(suggestionsFilter);
      filter_panel.add(Box.createRigidArea(space_5));
      filter_panel.add(kuidFilter);
      filter_panel.add(Box.createRigidArea(space_5));
      filter_panel.add(programIdFilter);

      gy++;
      c.gridy = gy;
      c.fill = GridBagConstraints.NONE;
      c.weightx = 0.0;
      content.add(filter_panel, c);
            
      // OK & CANCEL
      GAP = 0;
      JPanel last = new JPanel();
      last.setLayout(new GridLayout(1, 2, GAP, GAP));
      last.add(OK);
      last.add(CANCEL);
   
      gy++;
      c.gridy = gy;
      c.fill = GridBagConstraints.HORIZONTAL;
      c.weightx = 0.0;
      content.add(last, c);
                 
      // create dialog window
      dialog = new JDialog(frame, false); // non-modal dialog
      dialog.setTitle("kmttg auto transfers configuration");
      dialog.setContentPane(content);
      dialog.setSize(new Dimension(700,450));
      dialog.setLocationRelativeTo(config.gui.getJFrame().getJMenuBar().getComponent(0));
      dialog.pack();
   }
   
   // Component tooltip setup
   public void setToolTips() {
      enabled.setToolTipText(getToolTip("enabled"));
      metadata.setToolTipText(config.gui.getToolTip("metadata"));
      decrypt.setToolTipText(config.gui.getToolTip("decrypt"));
      qsfix.setToolTipText(config.gui.getToolTip("qsfix"));
      twpdelete.setToolTipText(config.gui.getToolTip("twpdelete"));
      ipaddelete.setToolTipText(config.gui.getToolTip("ipaddelete"));
      comskip.setToolTipText(config.gui.getToolTip("comskip"));
      comcut.setToolTipText(config.gui.getToolTip("comcut"));
      captions.setToolTipText(config.gui.getToolTip("captions"));
      encode.setToolTipText(config.gui.getToolTip("encode"));
      push.setToolTipText(config.gui.getToolTip("push"));
      custom.setToolTipText(config.gui.getToolTip("custom"));
      encoding_name.setToolTipText(config.gui.getToolTip("encoding"));
      table.setToolTipText(getToolTip("table"));
      type.setToolTipText(getToolTip("type"));
      tivo.setToolTipText(getToolTip("tivo"));
      dry_run.setToolTipText(getToolTip("dry_run"));
      title.setToolTipText(getToolTip("title"));
      comskipIni.setToolTipText(getToolTip("comskipIni"));
      channelFilter.setToolTipText(getToolTip("channelFilter"));
      check_interval.setToolTipText(getToolTip("check_interval"));
      add.setToolTipText(getToolTip("add"));
      update.setToolTipText(getToolTip("update"));
      del.setToolTipText(getToolTip("del"));      
      dateFilter.setToolTipText(getToolTip("dateFilter"));
      suggestionsFilter.setToolTipText(getToolTip("suggestionsFilter"));
      suggestionsFilter_single.setToolTipText(getToolTip("suggestionsFilter_single"));
      useProgramId_unique.setToolTipText(getToolTip("useProgramId_unique"));
      kuidFilter.setToolTipText(getToolTip("kuidFilter"));
      programIdFilter.setToolTipText(getToolTip("programIdFilter"));
      dateOperator.setToolTipText(getToolTip("dateOperator"));
      dateHours.setToolTipText(getToolTip("dateHours"));
      OK.setToolTipText(getToolTip("OK"));
      CANCEL.setToolTipText(getToolTip("CANCEL"));      
   }
   
   public String getToolTip(String component) {
      String text = "";
      if (component.equals("table")) {
         text =  "<b>auto transfers entries</b><br>";
         text += "Click on an entry to select it. Form settings will update to match<br>";
         text += "the current settings for that entry. You can then change settings as<br>";
         text += "desired and then use <b>UPDATE</b> button to apply form settings to the entry.<br>";
         text += "Use <b>ADD</b> button to add a new entry<br>";
         text += "Use <b>DEL</b> button to remove selected entries<br>";
         text += "NOTE: Entry updates are only saved after you <b>OK</b> this form.";
      }
      else if (component.equals("type")) {
         text =  "<b>Type</b><br>";
         text += "<b>title</b> means exact title matching (case insensitive).<br>";
         text += "<b>keywords</b> means keyword matching (case insensitive) with<br>";
         text += "optional logical operations as illustrated above. Consult the<br>";
         text += "documentation for all the details.";
      }
      else if (component.equals("tivo")) {
         text =  "<b>TiVo</b><br>";
         text += "Restrict transfers to be from this TiVo only.<br>";
         text += "<b>all</b> means all TiVos currently configured in kmttg.";
      }
      else if (component.equals("enabled")) {
         text =  "<b>enabled</b><br>";
         text += "You can use this option to enable or disable an Auto Transfer entry.<br>";
         text += "This is useful to temporarily disable Auto Transfer entries without having<br>";
         text += "to delete them.";
      }
      else if (component.equals("dry_run")) {
         text =  "<b>Dry Run Mode (test keywords only)</b><br>";
         text += "With this option enabled kmttg will exercise the auto transfers setup<br>";
         text += "and will print messages about what shows match your setup, but will<br>";
         text += "not actually run any transfers. This is useful for testing your auto<br>";
         text += "transfers setup to ensure it will do what you want.<br>";
         text += "<b>NOTE: Use Auto Transfers->Run Once in GUI with this option set to test</b>.";
      }
      else if (component.equals("title")) {
         text =  "<b>title/keywords</b><br>";
         text += "Type in or update title or keywords for this entry here.<br>";
         text += "Consult example above and documentation for details on keywords setup.<br>";
         text += "NOTE: title and keywords are all case insensitive.";
      }
      else if (component.equals("comskipIni")) {
         text =  "<b>comskip.ini override</b><br>";
         text += "If you wish to use a specific comskip.ini file to use with <b>comcut</b> for<br>";
         text += "this auto transfer then specify the full path to the file here.<br>";
         text += "This will override the comskip.ini file specified in main kmttg configuration.";
      }
      else if (component.equals("channelFilter")) {
         text =  "<b>channel filter</b><br>";
         text += "If you wish to filter out by channel number or name for this auto transfer<br>";
         text += "then enter either channel number or name in this field. Leave it empty if you<br>";
         text += "do not want to filter by channel number or name.";
      }
      else if (component.equals("check_interval")) {
         text =  "<b>Check Tivos Interval (mins)</b><br>";
         text += "Once you start the Auto Transfers service or background job kmttg<br>";
         text += "will run in a loop matching your Auto Transfers entries to shows<br>";
         text += "on your Tivos and performing all the selected tasks for each match.<br>";
         text += "Once all matches have been processed kmttg will sleep for this specified<br>";
         text += "amount of time before checking again.<br>";
         text += "<b>NOTE: Setting this too low will overburden your network and Tivos.</b>";
      }
      else if (component.equals("add")) {
         text =  "<b>ADD</b><br>";
         text += "Add a new Auto Transfers entry based on current form choices.<br>";
         text += "NOTE: Additions won't be saved until you <b>OK</b> this form.";
      }
      else if (component.equals("update")) {
         text =  "<b>UPDATE</b><br>";
         text += "Update the currently selected Auto Transfers entry with current form settings.<br>";
         text += "NOTE: Updates won't be saved until you <b>OK</b> this form.";
      }
      else if (component.equals("del")) {
         text =  "<b>DEL</b><br>";
         text += "Remove currently selected Auto Transfers entries.<br>";
         text += "NOTE: Removals won't be saved until you <b>OK</b> this form.";
      }
      else if (component.equals("dateFilter")) {
         text =  "<b>Date Filter</b><br>";
         text += "If enabled then only process shows earlier or later than the specified<br>";
         text += "number of hours old. Examples:<br>";
         text += "<b>less than 48</b> means only process shows earlier than 2 days old.<br>";
         text += "<b>more than 24</b> means only process shows later than 1 day old.";
      }
      else if (component.equals("dateOperator")) {
         text =  "<b>Date Filter Operator</b><br>";
         text += "Operator for Date Filter setting.";
      }
      else if (component.equals("dateHours")) {
         text =  "<b>Date Filter Hours</b><br>";
         text += "Number of hours to use for filtering by date. Examples:<br>";
         text += "<b>less than 48</b> means only process shows earlier than 2 days old.<br>";
         text += "<b>more than 24</b> means only process shows later than 1 day old.";
      }
      else if (component.equals("suggestionsFilter")) {
         text =  "<b>Filter out TiVo Suggestions</b><br>";
         text += "If enabled then do not process any TiVo Suggestions recordings.<br>";
         text += "NOTE: If enabled this filter overrides any individual suggestions filter settings.";
      }
      else if (component.equals("suggestionsFilter_single")) {
         text =  "<b>Filter out TiVo Suggestions</b><br>";
         text += "If enabled then do not process any TiVo Suggestions recordings for this entry.";
      }
      else if (component.equals("useProgramId_unique")) {
         text =  "<b>Treat each recording as unique</b><br>";
         text += "If enabled then kmttg will generate a unique ProgramId based on ProgramId and recorded<br>";
         text += "time for each recording of this program. This is useful only for programs that do not<br>";
         text += "already have unique ProgramIds for each episode, such as some news programs for example.<br>";
         text += "For such programs kmttg would not ordinarily auto download subsequent episodes because<br>";
         text += "a ProgramId entry already exists in <b>auto.history</b> file. By enabling this option<br>";
         text += "kmttg will instead use a time-based ProgramId entry so that future recordings on different<br>";
         text += "dates with same ProgramId will still auto download<br>";
         text += "<b>NOTE: Enabling this option may lead to repeated downloads of shows so use wisely/sparingly<br>";
         text += "only for shows without unique ProgramId</b>";
      }
      else if (component.equals("kuidFilter")) {
         text =  "<b>Only process KUID recordings</b><br>";
         text += "If enabled then only process recordings that are marked as<br>";
         text += "Keep Until I Delete (KUID).";
      }
      else if (component.equals("programIdFilter")) {
         text =  "<b>Do not process recordings without ProgramId</b><br>";
         text += "If enabled then do not process recordings without ProgramId.<br>";
         text += "Typically, these are programs that were transferred to your TiVo(s)<br>";
         text += "from a PC or other source other than a recorded TV station or MRV,<br>";
         text += "such as pyTivo or TiVo Desktop transfers.";
      }
      else if (component.equals("OK")) {
         text =  "<b>OK</b><br>";
         text += "Save all changes made in this form and close the form.<br>";
         text += "NOTE: You need to setup and run kmttg service on Windows for Auto Transfers to run.<br>";
         text += "For non-windows platforms you need to setup a background job for Auto Transfers to run.<br>";
         text += "You can use <b>Auto Transfers->Service</b> or <b>Auto Transfers->Background Job</b><br>";
         text += "menus to do this. Consult documentation for more details.<br>";
         text += "NOTE: Settings are saved to <b>auto.ini</b> file which resides by <b>kmttg.jar</b> file.<br>";
      }
      else if (component.equals("CANCEL")) {
         text =  "<b>CANCEL</b><br>";
         text += "Do not save any changes made in this form and close the form.<br>";
      }
      
      if (text.length() > 0) {
         text = "<html>" + text + "</html>";
      }
      return text;
   }
   
   private void setTivoFilterNames() {
      tivo.removeAllItems();
      Object[] names = getTivoFilterNames();
      for (int i=0; i<names.length; ++i) {
         tivo.addItem(names[i]);
      }
   }
   
   // Defines choices for tivo name filtering
   private Object[] getTivoFilterNames() {
      Stack<String> names = config.getTivoNames();
      names.add(0, "all");
      Object[] tivoNames = new Object[names.size()];
      for (int i=0; i<names.size(); ++i) {
         tivoNames[i] = names.get(i);
      }
      return tivoNames;
   }

   // Checks given tivo name against current valid names and resets to all if not valid
   private String validateTivoName(String tivoName) {
      if ( ! tivoName.equals("all") ) {
         Stack<String> names = config.getTivoNames();
         for (int i=0; i<names.size(); ++i) {
            if (tivoName.equals(names.get(i)))
               return tivoName;
         }
         log.error("TiVo '" + tivoName + "' currently not configured in kmttg - resetting to all");
      }
      return "all";
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
      
   }
   
   public void clearTable() {
      debug.print("");
      TableModel model = table.getModel(); 
      int numrows = model.getRowCount(); 
      for(int i = numrows - 1; i >=0; i--)
         ((DefaultTableModel) model).removeRow(i); 
   }
   
   public void addTableRow(autoEntry entry) {
      debug.print("entry=" + entry);
      Object[] data = new Object[2];
      data[0] = new autoTableEntry(entry);
      if (entry.type.equals("title")) {
         data[1] = entry.keyword;
      } else {
         data[1] = autoConfig.keywordsToString(entry.keywords);
      }
      TableModel model = table.getModel();
      ((DefaultTableModel) model).addRow(data);
   }
   
   public void removeTableRow(int row) {
      debug.print("row=" + row);
      DefaultTableModel dm = (DefaultTableModel)table.getModel();
      dm.removeRow(row);
   }
  
   public int[] getTableSelectedRows() {
      debug.print("");
      int[] rows = table.getSelectedRows();
      if (rows.length <= 0)
         log.error("No rows selected");
      return rows;
   }
     
   // Return autoEntry instance of selected entry
   public autoEntry GetRowData(int row) {
      debug.print("row=" + row);
      // Get column items for given row 
      if ( table.getRowCount() > row ) {
         autoTableEntry s = (autoTableEntry)table.getValueAt(row, 0);
         return s.entry;
      }
      return null;
   }

   
   // Override some default table model actions
   class MyTableModel extends DefaultTableModel {
      private static final long serialVersionUID = 1L;

      public MyTableModel(Object[][] data, Object[] columnNames) {
         super(data, columnNames);
      }

      @SuppressWarnings("unchecked")
      // This is used to define columns as specific classes
      public Class getColumnClass(int col) {
         if (col == 0) {
            return autoTableEntry.class;
         }
         return Object.class;
      } 
      
      // Set all cells uneditable
      public boolean isCellEditable(int row, int column) {        
         return false;
      }
   }
   
   // Pack all table columns to fit widest cell element
   public void packColumns(JTable table, int margin) {
      debug.print("table=" + table + " margin=" + margin);
      for (int c=0; c<table.getColumnCount(); c++) {
          packColumn(table, c, 2);
      }
   }
   
   // Sets the preferred width of the visible column specified by vColIndex. The column
   // will be just wide enough to show the column head and the widest cell in the column.
   // margin pixels are added to the left and right
   // (resulting in an additional width of 2*margin pixels).
   public void packColumn(JTable table, int vColIndex, int margin) {
      debug.print("table=" + table + " vColIndex=" + vColIndex + " margin=" + margin);
       DefaultTableColumnModel colModel = (DefaultTableColumnModel)table.getColumnModel();
       TableColumn col = colModel.getColumn(vColIndex);
       int width = 0;
   
       // Get width of column header
       TableCellRenderer renderer = col.getHeaderRenderer();
       if (renderer == null) {
           renderer = table.getTableHeader().getDefaultRenderer();
       }
       Component comp = renderer.getTableCellRendererComponent(
           table, col.getHeaderValue(), false, false, 0, 0);
       width = comp.getPreferredSize().width;
   
       // Get maximum width of column data
       for (int r=0; r<table.getRowCount(); r++) {
           renderer = table.getCellRenderer(r, vColIndex);
           comp = renderer.getTableCellRendererComponent(
               table, table.getValueAt(r, vColIndex), false, false, r, vColIndex);
           width = Math.max(width, comp.getPreferredSize().width);
       }
   
       // Add margin
       width += 2*margin;
              
       // Set the width
       col.setPreferredWidth(width);
       
       // Adjust last column to fit available
       int last = table.getColumnCount()-1;
       if (vColIndex == last) {
          int twidth = table.getPreferredSize().width;
          int awidth = content.getWidth();
          int offset = table_scroll.getVerticalScrollBar().getPreferredSize().width+4*margin;
          if ((awidth-offset) > twidth) {
             width += awidth-offset-twidth;
             col.setPreferredWidth(width);
          }
       }
   }
   
   // Update dialog settings based on autoConfig current settings
   public void update() {
      SetKeywords(autoConfig.KEYWORDS);
      SetEncodings(encodeConfig.getValidEncodeNames());
      setTivoFilterNames();
      check_interval.setText("" + autoConfig.CHECK_TIVOS_INTERVAL);      
      dry_run.setSelected((Boolean)(autoConfig.dryrun == 1));
      dateFilter.setSelected((Boolean)(autoConfig.dateFilter == 1));
      dateOperator.setSelectedItem(autoConfig.dateOperator);
      dateHours.setText("" + autoConfig.dateHours);
      suggestionsFilter.setSelected((Boolean)(autoConfig.suggestionsFilter == 1));
      kuidFilter.setSelected((Boolean)(autoConfig.kuidFilter == 1));
      programIdFilter.setSelected((Boolean)(autoConfig.programIdFilter == 1));
   }
   
   // Set encoding_name combobox choices
   public void SetEncodings(Stack<String> values) {
      debug.print("values=" + values);
      
      encoding_name.removeAllItems();
      for (int i=0; i<values.size(); ++i) {
         encoding_name.addItem(values.get(i));
      }
      
   }
   
   // Set table entries according to auto config setup
   public void SetKeywords(Stack<autoEntry> entries) {
      debug.print("entries=" + entries);
      clearTable();
      if (entries.size() > 0) {
         for (int i=0; i<entries.size(); i++) {
            addTableRow(entries.get(i));
         }
      }
   }   
   
   // Callback for ADD button
   // Add type & keywords as a table entry
   private void addCB() {
      debug.print("");
      String ktype = (String)type.getSelectedItem();
      String keywords = string.removeLeadingTrailingSpaces(title.getText());
      if (keywords.length() == 0) {
         log.error("No keywords specified");
         return;
      }
      
      // Make sure this is not a duplicate entry
      Boolean duplicate = false;
      if (table.getRowCount() > 0) {
         for (int i=0; i<table.getRowCount(); ++i) {
            autoEntry check = GetRowData(i);
            if (check.type.equals(ktype)) {
               if (check.type.equals("title")) {
                  if (keywords.equals(check.keyword)) duplicate = true;
               } else {
                  if (keywords.equals(autoConfig.keywordsToString(check.keywords))) duplicate = true;
               }
            }
         }
      }      
      if (duplicate) {
         log.error("Duplicate entry, not adding");
         return;
      }
      
      autoEntry entry = new autoEntry();
      // Set entry settings based on dialog settings
      guiToEntry(entry);
      
      // Add a new table row
      addTableRow(entry);
      packColumns(table,2);
   }
   
   // Callback for UPDATE button
   // Update selected table entry with dialog settings
   private void updateCB() {
      debug.print("");
      int[] rows = getTableSelectedRows();
      if (rows.length == 0) {
         log.error("No table row selected");
         return;
      }
   
      int row = rows[0]; // Process top most row
      autoEntry entry = GetRowData(row);
      
      // Update entry settings
      guiToEntry(entry);
      
      // Update table settings
      TableModel model = table.getModel();
      model.setValueAt(new autoTableEntry(entry), row, 0);
      if (entry.type.equals("title"))
         model.setValueAt(entry.keyword, row, 1);
      else
         model.setValueAt(autoConfig.keywordsToString(entry.keywords), row, 1);
      
      packColumns(table,2);
      log.warn("Updated auto transfers entry # " + (row+1));
   }
   
   // Callback for DEL button
   // Remove selected table entries
   private void delCB() {
      debug.print("");
      int[] rows = getTableSelectedRows();
      for (int i=rows.length-1; i>-1; --i) {
         removeTableRow(rows[i]);
      }
      
      packColumns(table,2);
   }
   
   // Callback for OK button
   // Save table settings to auto.ini and hide the dialog
   private void okCB() {
      debug.print("");
      clearTextFieldErrors();
      // Error checking
      int interval = 60;
      String value = string.removeLeadingTrailingSpaces(check_interval.getText());
      try {
         interval = Integer.parseInt(value);
      } catch(NumberFormatException e) {
         textFieldError(check_interval, "check interval should be an integer: '" + value + "'");
         return;
      }
      
      float hours = 48;
      value = string.removeLeadingTrailingSpaces(dateHours.getText());
      try {
         hours = Float.parseFloat(value);
      } catch(NumberFormatException e) {
         textFieldError(check_interval, "Date Filter hours should be of type float: '" + value + "'");
         return;
      }
      
      // Write to file
      try {
         BufferedWriter ofp = new BufferedWriter(new FileWriter(config.autoIni));
         ofp.write("# kmttg auto.ini file\n");
         ofp.write("<check_tivos_interval>\n" + interval + "\n\n");
         ofp.write("<dryrun>\n");
         if (dry_run.isSelected())
            ofp.write("1\n\n");
         else
            ofp.write("0\n\n");
         ofp.write("<dateFilter>\n");
         if (dateFilter.isSelected())
            ofp.write("1\n\n");
         else
            ofp.write("0\n\n");
         ofp.write("<dateOperator>\n" + dateOperator.getSelectedItem() + "\n\n");
         ofp.write("<dateHours>\n" + hours + "\n\n");
         ofp.write("<suggestionsFilter>\n");
         if (suggestionsFilter.isSelected())
            ofp.write("1\n\n");
         else
            ofp.write("0\n\n");
         ofp.write("<kuidFilter>\n");
         if (kuidFilter.isSelected())
            ofp.write("1\n\n");
         else
            ofp.write("0\n\n");
         ofp.write("<programIdFilter>\n");
         if (programIdFilter.isSelected())
            ofp.write("1\n\n");
         else
            ofp.write("0\n\n");
         
         TableModel model = table.getModel();
         int rows = model.getRowCount();
         if (rows > 0) {
            autoEntry entry;
            for (int i=0; i<rows; ++i) {
               entry = GetRowData(i);
               // Some options may have to be turned off for disabled features
               if (config.TivoWebPlusDelete == 0)
                  entry.twpdelete = 0;
               if ( ! config.ipadDeleteEnabled() )
                  entry.ipaddelete = 0;
               ofp.write("\n");
               if (entry.type.equals("title")) {
                  ofp.write("<title>\n");
                  ofp.write(entry.keyword + "\n");
               } else {
                  ofp.write("<keywords>\n");
                  ofp.write(autoConfig.keywordsToString(entry.keywords) + "\n");
               }
               ofp.write("<options>\n");
               ofp.write("enabled "             + entry.enabled             + "\n");
               ofp.write("tivo "                + entry.tivo                + "\n");
               ofp.write("metadata "            + entry.metadata            + "\n");               
               ofp.write("decrypt "             + entry.decrypt             + "\n");               
               ofp.write("qsfix "               + entry.qsfix               + "\n");               
               ofp.write("twpdelete "           + entry.twpdelete           + "\n");               
               ofp.write("ipaddelete "          + entry.ipaddelete          + "\n");               
               ofp.write("comskip "             + entry.comskip             + "\n");               
               ofp.write("comcut "              + entry.comcut              + "\n");               
               ofp.write("captions "            + entry.captions            + "\n");               
               ofp.write("encode "              + entry.encode              + "\n");
               ofp.write("push "                + entry.push                + "\n");
               ofp.write("custom "              + entry.custom              + "\n");
               ofp.write("suggestionsFilter "   + entry.suggestionsFilter   + "\n");
               ofp.write("useProgramId_unique " + entry.useProgramId_unique + "\n");
               if (entry.encode_name != null && entry.encode_name.length() > 0)
                  ofp.write("encode_name " + entry.encode_name + "\n");
               if (entry.channelFilter != null && entry.channelFilter.length() > 0)
                  ofp.write("channelFilter " + entry.channelFilter + "\n");
               if (file.isFile(entry.comskipIni))
                  ofp.write("comskipIni " + entry.comskipIni + "\n");
               else
                  ofp.write("comskipIni " + "none" + "\n");
            }
         }
         
         ofp.close();
      } catch (IOException ex) {
         log.error("Cannot write to auto config file: " + config.autoIni);
         log.error(ex.toString());
         return;
      } 
      
      log.warn("Auto config settings saved");
      
      // Close dialog
      dialog.setVisible(false);
      
      // Update autoConfig settings      
      autoConfig.parseAuto(config.autoIni);
   }
   
   // Callback when user clicks on a table row
   // This will update component settings according to selected row data
   private void TableRowSelected(int row) {
      debug.print("row=" + row);
      if (row == -1) return;
      autoEntry entry = GetRowData(row);
      enabled.setSelected((Boolean)(entry.enabled == 1));
      metadata.setSelected((Boolean)(entry.metadata == 1));
      decrypt.setSelected((Boolean)(entry.decrypt == 1));
      qsfix.setSelected((Boolean)(entry.qsfix == 1));
      twpdelete.setSelected((Boolean)(entry.twpdelete == 1));
      ipaddelete.setSelected((Boolean)(entry.ipaddelete == 1));
      comskip.setSelected((Boolean)(entry.comskip == 1));
      comcut.setSelected((Boolean)(entry.comcut == 1));
      captions.setSelected((Boolean)(entry.captions == 1));
      encode.setSelected((Boolean)(entry.encode == 1));
      push.setSelected((Boolean)(entry.push == 1));
      custom.setSelected((Boolean)(entry.custom == 1));
      suggestionsFilter_single.setSelected((Boolean)(entry.suggestionsFilter == 1));
      useProgramId_unique.setSelected((Boolean)(entry.useProgramId_unique == 1));
      
      encoding_name.setSelectedItem(entry.encode_name);
      
      comskipIni.setText(entry.comskipIni);
      
      if (entry.channelFilter != null)
         channelFilter.setText(entry.channelFilter);
      else
         channelFilter.setText("");
      
      type.setSelectedItem(entry.type);
      
      entry.tivo = validateTivoName(entry.tivo);
      tivo.setSelectedItem(entry.tivo);
      
      if (entry.type.equals("title")) {
         title.setText(entry.keyword);
      } else {
         title.setText(autoConfig.keywordsToString(entry.keywords));
      }
   }
   
   private Boolean guiToEntry(autoEntry entry) {
      String ktype = (String)type.getSelectedItem();
      String ktivo = (String)tivo.getSelectedItem();
      String keywords = string.removeLeadingTrailingSpaces(title.getText());
      if (keywords.length() == 0) {
         log.error("No keywords specified");
         return false;
      }
      
      if (enabled.isSelected())
         entry.enabled = 1;
      else
         entry.enabled = 0;
      
      if (metadata.isSelected())
         entry.metadata = 1;
      else
         entry.metadata = 0;
      
      if (decrypt.isSelected())
         entry.decrypt = 1;
      else
         entry.decrypt = 0;
      
      if (qsfix.isSelected())
         entry.qsfix = 1;
      else
         entry.qsfix = 0;
      
      if (twpdelete.isSelected())
         entry.twpdelete = 1;
      else
         entry.twpdelete = 0;
      
      if (ipaddelete.isSelected())
         entry.ipaddelete = 1;
      else
         entry.ipaddelete = 0;
      
      if (comskip.isSelected())
         entry.comskip = 1;
      else
         entry.comskip = 0;
      
      if (comcut.isSelected())
         entry.comcut = 1;
      else
         entry.comcut = 0;
      
      if (captions.isSelected())
         entry.captions = 1;
      else
         entry.captions = 0;
      
      if (encode.isSelected())
         entry.encode = 1;
      else
         entry.encode = 0;
      
      if (push.isSelected())
         entry.push = 1;
      else
         entry.push = 0;
      
      if (custom.isSelected())
         entry.custom = 1;
      else
         entry.custom = 0;
      
      if (suggestionsFilter_single.isSelected())
         entry.suggestionsFilter = 1;
      else
         entry.suggestionsFilter = 0;
      
      if (useProgramId_unique.isSelected())
         entry.useProgramId_unique = 1;
      else
         entry.useProgramId_unique = 0;
      
      entry.encode_name = (String)encoding_name.getSelectedItem();

      String ini = (String)string.removeLeadingTrailingSpaces(comskipIni.getText());
      if (ini.length() > 0 && ! ini.equals("none")) {
         if ( ! file.isFile(ini) ) {
            log.error("Specified comskip.ini override file does not exist...");
         }
      }
      entry.comskipIni = ini;
      
      String cFilter = (String)string.removeLeadingTrailingSpaces(channelFilter.getText());
      if (cFilter.length() > 0)
         entry.channelFilter = cFilter;
      else
         entry.channelFilter = null;
      
      entry.type = ktype;
      
      entry.tivo = ktivo;
      
      if (ktype.equals("title")) {
         entry.keyword = keywords;
      } else {
         autoConfig.stringToKeywords(keywords, entry);
      }
      
      return true;

   }

}
