package com.tivo.kmttg.gui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class autoLogView {   
   private JDialog dialog = null;
   private JTextArea text = null;
   
   public autoLogView(JFrame frame) {
      debug.print("frame=" + frame);
      
      if ( ! file.isFile(config.autoLog)) {
         log.error("Auto log file not found: " + config.autoLog);
         return;
      }
      
      JPanel content;      
      
      // Define content for dialog window
      content = new JPanel(new GridBagLayout());
      content.setLayout(new GridBagLayout());
      
      GridBagConstraints c = new GridBagConstraints();
      c.fill = GridBagConstraints.HORIZONTAL;
      
      // text area
      text = new JTextArea();
      text.setEditable(false);
      text.setLineWrap(true);
      JScrollPane s1 = new JScrollPane(text);
      if (view()) {
         c.ipady = 0;
         c.weighty = 1.0;  // default to vertical stretch
         c.weightx = 1.0;  // default to horizontal stretch
         c.gridx = 0;
         c.gridy = 0;
         c.gridwidth = 1;
         c.gridheight = 1;
         c.anchor = GridBagConstraints.NORTH;
         c.fill = GridBagConstraints.BOTH;
         content.add(s1, c);
        
         // create and display dialog window
         dialog = new JDialog(frame, false); // non-modal dialog
         dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE); // Destroy when closed
         dialog.setTitle(config.autoLog);
         dialog.setContentPane(content);
         dialog.pack();
         dialog.setSize(600,400);
         dialog.setVisible(true);
      } else {
         // Deallocate resources and return
         s1 = null;
         text = null;
         c = null;
         content = null;
         return;
      }
   }
     
   // Update text with auto log file contents
   private Boolean view() {
      try {
         BufferedReader log = new BufferedReader(new FileReader(config.autoLog));
         String line = null;
         text.setEditable(true);
         while (( line = log.readLine()) != null) {
            text.append(line + "\n");
         }
         log.close();
         text.setEditable(false);
      }         
      catch (IOException ex) {
         log.error("Auto log file cannot be read: " + config.autoLog);
         return false;
      }
      return true;
   }

}
