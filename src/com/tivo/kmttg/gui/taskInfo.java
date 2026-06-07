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
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import com.tivo.kmttg.gui.swing.SwingUtil;
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
      JLabel job_label;
      JLabel stdout_label;
      JLabel stderr_label;
      this.process = process;

      // Define content for dialog window
      JPanel content = new JPanel();
      content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

      // job description label
      job_label = new JLabel(description);

      // stdout label
      stdout_label = new JLabel("stdout");

      // stdout text area
      stdout = new JTextArea();
      stdout.setEditable(false);
      stdout.setLineWrap(true);
      stdout.setWrapStyleWord(true);

      // stderr label
      stderr_label = new JLabel("stderr");

      // stderr text area
      stderr = new JTextArea();
      stderr.setEditable(false);
      stderr.setLineWrap(true);
      stderr.setWrapStyleWord(true);

      content.add(job_label);
      content.add(stdout_label);
      content.add(new JScrollPane(stdout));  // stretch vertically
      content.add(stderr_label);
      content.add(new JScrollPane(stderr));  // stretch vertically

      // create and display dialog window
      dialog = new JDialog(frame); // Non modal
      SwingUtil.loadIcons(dialog);
      dialog.setTitle("Task stdout/stderr viewer");
      dialog.getContentPane().add(content, BorderLayout.CENTER);
      dialog.setSize(600, 400);
      dialog.setLocationRelativeTo(frame);
      dialog.setVisible(true);

      // print available stdout/stderr
      appendStdout(process.getStdout());
      appendStderr(process.getStderr());

      // Setup process child handler to add to owatch/ewatch stacks
      process.setStdoutWatch(owatch);
      process.setStderrWatch(ewatch);

      // Start a timer that updates stdout/stderr text areas dynamically
      timer = new Timer();
      timer.schedule(
         new TimerTask() {
            @Override
            public void run() {
               SwingUtil.runLater(new Runnable() {
                  @Override public void run() {
                     update();
                  }
               });
            }
        }
        ,0,
        1000
      );
   }

   // Update text area stdout/stderr fields with process stdout/stderr
   public void update() {
      // Stop timer if dialog no longer displayed
      if (! dialog.isShowing()) {
         timer.cancel();
         dialog = null;
         process.setStdoutWatch(null);
         process.setStderrWatch(null);
         return;
      }
      if ( process.exitStatus() != -1 ) {
         // Process finished so stop timer
         // Don't return so that last flush of stdout/stderr can happen
         timer.cancel();
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
      if (s != null && s.size() > 0) {
         stdout.setEditable(true);
         for (int i=0; i<s.size(); ++i)
            stdout.append(s.get(i) + "\n");
         stdout.setEditable(false);
      }
   }

   public void appendStderr(Stack<String> s) {
      if (s != null && s.size() > 0) {
         stderr.setEditable(true);
         for (int i=0; i<s.size(); ++i)
            stderr.append(s.get(i) + "\n");
         stderr.setEditable(false);
      }
   }

}
