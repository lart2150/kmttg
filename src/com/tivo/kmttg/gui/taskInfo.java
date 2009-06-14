package com.tivo.kmttg.gui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Stack;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Timer;

import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;

// This is used for dynamic display of running task stdout/stderr
public class taskInfo {
   backgroundProcess process;
   Timer timer;
   private Stack<String> owatch = new Stack<String>();
   private Stack<String> ewatch = new Stack<String>();
   
   private JDialog dialog;
   private JTextArea stdout = null;
   private JTextArea stderr = null;     
   
   public taskInfo(JFrame frame, String description, backgroundProcess process) {
      debug.print("frame=" + frame + " description=" + description + " process=" + process);
      JPanel content;      
      JLabel job_label;
      JLabel stdout_label;
      JLabel stderr_label;
      this.process = process;
      
      // Define content for dialog window
      content = new JPanel(new GridBagLayout());
      content.setLayout(new GridBagLayout());
      
      GridBagConstraints c = new GridBagConstraints();
      c.fill = GridBagConstraints.HORIZONTAL;
      int gx=0, gy=0;
      
      // job description label
      job_label = new JLabel(description);
      c.insets = new Insets(5, 0, 0, 0);
      c.ipady = 0;
      c.weighty = 0;    // default to no vertical stretch
      c.weightx = 1.0;  // default to horizontal stretch
      c.gridx = gx;
      c.gridy = gy++;
      c.gridwidth = 1;
      c.gridheight = 1;
      c.anchor = GridBagConstraints.NORTH;
      c.fill = GridBagConstraints.NONE;
      content.add(job_label, c);
            
      // stdout label
      stdout_label = new JLabel("stdout");
      c.anchor = GridBagConstraints.WEST;
      c.weighty = 0.0;  // do not stretch vertically
      c.gridx = gx;
      c.gridy = gy++;
      c.fill = GridBagConstraints.NONE;
      content.add(stdout_label, c);
      
      // stdout text area
      c.anchor = GridBagConstraints.NORTH;
      c.weighty = 1.0;  // stretch vertically
      c.gridx = gx;
      c.gridy = gy++;
      c.ipady = 100;    // Make this taller
      c.fill = GridBagConstraints.BOTH;
      c.gridwidth = 1;
      c.gridheight = 5;
      stdout = new JTextArea();
      stdout.setEditable(false);
      stdout.setLineWrap(true);
      JScrollPane s1 = new JScrollPane(stdout);
      content.add(s1, c);
     
      
      // stderr label
      gy += 5;
      stderr_label = new JLabel("stderr");
      c.anchor = GridBagConstraints.WEST;
      c.weighty = 0.0;  // do not stretch vertically
      c.gridx = gx;
      c.gridy = gy++;
      c.ipady = 0;      // nominal height
      c.gridwidth = 1;
      c.gridheight = 1;
      c.fill = GridBagConstraints.NONE;
      content.add(stderr_label, c);
      
      // stderr text area
      c.anchor = GridBagConstraints.NORTH;
      c.weighty = 1.0;  // stretch vertically
      c.gridx = gx;
      c.gridy = gy++;
      c.ipady = 100;    // make this taller
      c.fill = GridBagConstraints.BOTH;
      stderr = new JTextArea();
      stderr.setEditable(false);
      stderr.setLineWrap(true);
      JScrollPane s2 = new JScrollPane(stderr);
      content.add(s2, c);
     
      // create and display dialog window
      dialog = new JDialog(frame, false); // non-modal dialog
      dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE); // Destroy when closed
      dialog.setTitle("Task stdout/stderr viewer");
      dialog.setContentPane(content);
      dialog.pack();
      dialog.setSize(600,400);
      dialog.setVisible(true);
      
      process.setStdoutWatch(owatch);
      process.setStderrWatch(ewatch);
      
      // Start a timer that updates stdout/stderr text areas dynamically
      timer = new Timer(1000, new ActionListener() {
         public void actionPerformed(ActionEvent evt) {
            update();
         }    
      });
      timer.start();
   }
     
   // Update text area stdout/stderr fields with process stdout/stderr
   public void update() {
      // Stop timer if dialog no longer displayed
      if (! dialog.isShowing()) {
         timer.stop();
         dialog = null;
         process.setStdoutWatch(null);
         process.setStderrWatch(null);
         return;
      }
      if ( process.exitStatus() != -1 ) {
         // Process finished so stop timer
         // Don't return so that last flush of stdout/stderr can happen
         timer.stop();
         process.setStdoutWatch(null);
         process.setStderrWatch(null);
      }
      if ( owatch.size() > 0 ) {
         appendStdout(owatch);
         owatch.clear();
      }
      if ( ewatch.size() > 0 ) {
         appendStderr(ewatch);
         ewatch.clear();
      }
   }
      
   public void appendStdout(Stack<String> s) {
      stdout.setEditable(true);
      for (int i=0; i<s.size(); ++i)
         stdout.append(s.get(i) + "\n");
      stdout.setEditable(false);
   }
   
   public void appendStderr(Stack<String> s) {
      stderr.setEditable(true);
      for (int i=0; i<s.size(); ++i)
         stderr.append(s.get(i) + "\n");
      stderr.setEditable(false);
   }

}
