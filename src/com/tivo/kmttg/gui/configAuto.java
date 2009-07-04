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
   private static JComboBox encoding_name = null;
   private static JCheckBox metadata = null;
   private static JCheckBox decrypt = null;
   private static JCheckBox qsfix = null;
   private static JCheckBox comskip = null;
   private static JCheckBox comcut = null;
   private static JCheckBox captions = null;
   private static JCheckBox encode = null;
   private static JCheckBox custom = null;
   private static JCheckBox dry_run = null;
   private static JTextField title = null;
   private static JTextField check_interval = null;
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
      
      title = new JTextField();
            
      metadata = new JCheckBox("metadata");
      decrypt  = new JCheckBox("decrypt");
      qsfix    = new JCheckBox("VRD QS fix");
      comskip  = new JCheckBox("Ad Detect");
      comcut   = new JCheckBox("Ad Cut");
      captions = new JCheckBox("captions");
      encode   = new JCheckBox("encode");
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
      
      JLabel encoding_name_label = new JLabel("Encoding Name: ");
      
      encoding_name = new JComboBox();
      SetEncodings(encodeConfig.getValidEncodeNames());
      
      JLabel global_settings = new JLabel("GLOBAL SETTINGS:");
      
      JLabel check_interval_label = new JLabel("Check Tivos Interval (mins)");
      
      check_interval = new JTextField();
      check_interval.setText("" + autoConfig.CHECK_TIVOS_INTERVAL);
      
      dry_run = new JCheckBox("Dry Run Mode (test keywords only)");
      dry_run.setSelected((Boolean)(autoConfig.dryrun == 1));
      
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
      c.gridx = 0;
      c.gridy = gy;
      c.gridwidth = 8;     
      c.fill = GridBagConstraints.BOTH;
      c.weightx = 1.0;
      c.weighty = 1.0;
      //c.ipady = 150;
      content.add(table_scroll, c);

      // text
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      c.gridwidth = 8;
      c.fill = GridBagConstraints.HORIZONTAL;
      c.weightx = 1.0;
      c.weighty = 0.0;
      c.ipady = 0;
      content.add(text, c);
      
      // type_label
      gy++;
      c.gridx = 0;
      c.gridy = gy;
      c.gridwidth = 1;
      c.fill = GridBagConstraints.NONE;
      c.weightx = 0.0;
      content.add(type_label, c);
      
      // type
      c.gridx = 1;
      c.gridy = gy;
      c.gridwidth = 1;
      c.fill = GridBagConstraints.NONE;
      c.weightx = 0.0;
      content.add(type, c);
           
      // title
      c.gridx = 2;
      c.gridy = gy;
      c.gridwidth = 6;     
      c.fill = GridBagConstraints.HORIZONTAL;
      c.weightx = 1.0;
      content.add(title, c); 
      
      // row4
      int GAP = 0;
      JPanel row4 = new JPanel();
      row4.setLayout(new GridLayout(1, 8, GAP, GAP));
      row4.add(metadata);
      row4.add(decrypt);
      row4.add(qsfix);
      row4.add(comskip);
      row4.add(comcut);
      row4.add(captions);
      row4.add(encode);
      row4.add(custom);

      gy++;
      int gx = 0;
      c.gridx = gx++;
      c.gridy = gy;
      c.gridwidth = 8;
      c.fill = GridBagConstraints.NONE;
      c.weightx = 0.0;
      content.add(row4, c);
      
      // encoding_name_label
      gy++;
      gx = 0;
      c.gridx = gx;
      c.gridy = gy;
      c.gridwidth = 2;
      c.fill = GridBagConstraints.NONE;
      c.weightx = 0.0;
      content.add(encoding_name_label, c);
            
      // encoding_name
      gx += c.gridwidth;
      c.gridx = gx;
      c.gridy = gy;
      c.gridwidth = 2;
      c.anchor = GridBagConstraints.WEST;
      c.fill = GridBagConstraints.NONE;
      c.weightx = 0.0;
      content.add(encoding_name, c);
      
      // add
      gx += c.gridwidth;
      c.gridx = gx;
      c.gridy = gy;
      c.gridwidth = 1;
      c.anchor = GridBagConstraints.NORTH;
      c.fill = GridBagConstraints.HORIZONTAL;
      c.weightx = 0.0;
      content.add(add, c);
      
      // update
      gx += c.gridwidth;
      c.gridx = gx;
      c.gridy = gy;
      c.gridwidth = 1;
      c.anchor = GridBagConstraints.NORTH;
      c.fill = GridBagConstraints.HORIZONTAL;
      c.weightx = 0.0;
      content.add(update, c);
      
      // del
      gx += c.gridwidth;
      c.gridx = gx;
      c.gridy = gy;
      c.gridwidth = 1;
      c.anchor = GridBagConstraints.WEST;
      c.fill = GridBagConstraints.NONE;
      c.weightx = 0.0;
      content.add(del, c);
      
      // separator
      JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
      gy++;
      gx=0;
      c.gridx = gx;
      c.gridy = gy;
      c.gridwidth = 8;
      c.fill = GridBagConstraints.HORIZONTAL;
      c.weightx = 1.0;
      content.add(sep, c);
                        
      // global_settings
      gy++;
      gx = 0;
      c.gridx = gx;
      c.gridy = gy;
      c.gridwidth = 2;
      c.fill = GridBagConstraints.NONE;
      c.weightx = 0.0;
      content.add(global_settings, c);
      
      // dry_run
      gx += c.gridwidth;
      c.ipady = -8;
      c.gridx = gx;
      c.gridy = gy;
      c.gridwidth = 2;
      c.fill = GridBagConstraints.NONE;
      c.weightx = 0.0;
      content.add(dry_run, c);
      
      // check_interval_label
      gx += c.gridwidth;
      c.ipady = 0;
      c.gridx = gx;
      c.gridy = gy;
      c.gridwidth = 2;
      c.fill = GridBagConstraints.NONE;
      c.weightx = 0.0;
      content.add(check_interval_label, c);
      
      // check_interval
      gx += c.gridwidth;
      c.gridx = gx;
      c.gridy = gy;
      c.gridwidth = 2;
      c.fill = GridBagConstraints.HORIZONTAL;
      c.weightx = 1.0;
      content.add(check_interval, c);

      // OK & CANCEL
      GAP = 0;
      JPanel last = new JPanel();
      last.setLayout(new GridLayout(1, 2, GAP, GAP));
      last.add(OK);
      last.add(CANCEL);
   
      // OK
      gy++;
      gx = 2;
      c.gridx = gx;
      c.gridy = gy;
      c.gridwidth = 2;
      c.fill = GridBagConstraints.HORIZONTAL;
      c.weightx = 0.0;
      content.add(last, c);
                 
      // create dialog window
      dialog = new JDialog(frame, false); // non-modal dialog
      dialog.setTitle("kmttg auto transfers configuration");
      dialog.setContentPane(content);
      dialog.setSize(new Dimension(700,300));
      dialog.setLocationRelativeTo(config.gui.getJFrame().getJMenuBar().getComponent(0));
      dialog.pack();
   }
   
   // Component tooltip setup
   public void setToolTips() {
      metadata.setToolTipText(config.gui.getToolTip("metadata"));
      decrypt.setToolTipText(config.gui.getToolTip("decrypt"));
      qsfix.setToolTipText(config.gui.getToolTip("qsfix"));
      comskip.setToolTipText(config.gui.getToolTip("comskip"));
      comcut.setToolTipText(config.gui.getToolTip("comcut"));
      captions.setToolTipText(config.gui.getToolTip("captions"));
      encode.setToolTipText(config.gui.getToolTip("encode"));
      custom.setToolTipText(config.gui.getToolTip("custom"));
      encoding_name.setToolTipText(config.gui.getToolTip("encoding"));
      table.setToolTipText(getToolTip("table"));
      type.setToolTipText(getToolTip("type"));
      dry_run.setToolTipText(getToolTip("dry_run"));
      title.setToolTipText(getToolTip("title"));
      check_interval.setToolTipText(getToolTip("check_interval"));
      add.setToolTipText(getToolTip("add"));
      update.setToolTipText(getToolTip("update"));
      del.setToolTipText(getToolTip("del"));      
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
      else if (component.equals("dry_run")) {
         text =  "<b>Dry Run Mode (test keywords only)</b><br>";
         text += "With this option enabled kmttg will exercise the auto transfers setup<br>";
         text += "and will print messages about what shows match your setup, but will<br>";
         text += "not actually run any transfers. This is useful for testing your auto<br>";
         text += "transfers setup to ensure it will do what you want.<br>";
         text += "<b>NOTE: Use Auto Transfers->Run in GUI with this option set to test</b>.";
      }
      else if (component.equals("title")) {
         text =  "<b>title/keywords</b><br>";
         text += "Type in or update title or keywords for this entry here.<br>";
         text += "Consult example above and documentation for details on keywords setup.<br>";
         text += "NOTE: title and keywords are all case insensitive.";
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
      check_interval.setText("" + autoConfig.CHECK_TIVOS_INTERVAL);      
      dry_run.setSelected((Boolean)(autoConfig.dryrun == 1));
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
      
      // Write to file
      try {
         BufferedWriter ofp = new BufferedWriter(new FileWriter(config.autoIni));
         ofp.write("# kmttg auto.ini file\n");
         ofp.write("<check_tivos_interval>\n" + interval + "\n\n");
         ofp.write("<dryrun>\n");
         if (dry_run.isSelected())
            ofp.write("1\n");
         else
            ofp.write("0\n");
         
         TableModel model = table.getModel();
         int rows = model.getRowCount();
         if (rows > 0) {
            autoEntry entry;
            for (int i=0; i<rows; ++i) {
               entry = GetRowData(i);
               ofp.write("\n");
               if (entry.type.equals("title")) {
                  ofp.write("<title>\n");
                  ofp.write(entry.keyword + "\n");
               } else {
                  ofp.write("<keywords>\n");
                  ofp.write(autoConfig.keywordsToString(entry.keywords) + "\n");
               }
               ofp.write("<options>\n");               
               ofp.write("metadata " + entry.metadata + "\n");               
               ofp.write("decrypt "  + entry.decrypt  + "\n");               
               ofp.write("qsfix "    + entry.qsfix    + "\n");               
               ofp.write("comskip "  + entry.comskip  + "\n");               
               ofp.write("comcut "   + entry.comcut   + "\n");               
               ofp.write("captions " + entry.captions + "\n");               
               ofp.write("encode "   + entry.encode   + "\n");
               ofp.write("custom "   + entry.custom   + "\n");
               if (entry.encode_name != null && entry.encode_name.length() > 0)
                  ofp.write("encode_name " + entry.encode_name + "\n");               
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
      metadata.setSelected((Boolean)(entry.metadata == 1));
      decrypt.setSelected((Boolean)(entry.decrypt == 1));
      qsfix.setSelected((Boolean)(entry.qsfix == 1));
      comskip.setSelected((Boolean)(entry.comskip == 1));
      comcut.setSelected((Boolean)(entry.comcut == 1));
      captions.setSelected((Boolean)(entry.captions == 1));
      encode.setSelected((Boolean)(entry.encode == 1));
      custom.setSelected((Boolean)(entry.custom == 1));
      
      encoding_name.setSelectedItem(entry.encode_name);
      
      type.setSelectedItem(entry.type);
      
      if (entry.type.equals("title")) {
         title.setText(entry.keyword);
      } else {
         title.setText(autoConfig.keywordsToString(entry.keywords));
      }
   }
   
   private Boolean guiToEntry(autoEntry entry) {
      String ktype = (String)type.getSelectedItem();
      String keywords = string.removeLeadingTrailingSpaces(title.getText());
      if (keywords.length() == 0) {
         log.error("No keywords specified");
         return false;
      }
      
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
      
      if (custom.isSelected())
         entry.custom = 1;
      else
         entry.custom = 0;
      
      entry.encode_name = (String)encoding_name.getSelectedItem();
      
      entry.type = ktype;
      
      if (ktype.equals("title")) {
         entry.keyword = keywords;
      } else {
         autoConfig.stringToKeywords(keywords, entry);
      }
      
      return true;

   }

}
