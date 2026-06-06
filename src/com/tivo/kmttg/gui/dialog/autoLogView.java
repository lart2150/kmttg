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
package com.tivo.kmttg.gui.dialog;

import java.awt.BorderLayout;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import com.tivo.kmttg.gui.swing.SwingUtil;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class autoLogView {
   private JDialog dialog = null;
   private JTextArea text = null;
   private static String logfile = config.autoLog + ".0";
   Timer timer;
   BufferedReader br = null;
   int max_lines = 100;
   Stack<String> lines = new Stack<String>();

   public autoLogView(JFrame frame) {
      debug.print("frame=" + frame);

      if ( ! file.isFile(logfile)) {
         log.error("Auto log file not found: " + logfile);
         return;
      }

      try {
         br = new BufferedReader(new FileReader(logfile));
      } catch (FileNotFoundException e) {
         log.error("Auto log file not found: " + logfile);
         return;
      }

      // Define content for dialog window
      JPanel content = new JPanel(new BorderLayout());

      // text area
      text = new JTextArea();
      text.setLineWrap(true);
      text.setWrapStyleWord(true);
      content.add(new JScrollPane(text), BorderLayout.CENTER);  // stretch horizontally and vertically

      // create and display dialog window
      dialog = new JDialog(frame);
      dialog.setTitle(logfile);
      dialog.getContentPane().add(content);
      dialog.setSize(600, 400);
      SwingUtil.loadIcons(dialog);
      dialog.setLocationRelativeTo(frame);
      dialog.setVisible(true);

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

   private void update() {
      if (! dialog.isShowing()) {
         timer.cancel();
         try {
            br.close();
         } catch (IOException e) {}
         text = null;
         return;
      }
      String line = null;
      text.setEditable(true);
      try {
         while (( line = br.readLine()) != null) {
            if (lines.size() > max_lines)
               lines.remove(0);
            lines.push(line);
         }
         for (String l : lines)
            text.append(l + "\n");
         lines.clear();
      } catch (IOException e) {
         log.error("autoLogView update - " + e.getMessage());
      }
      text.setEditable(false);
   }
}
