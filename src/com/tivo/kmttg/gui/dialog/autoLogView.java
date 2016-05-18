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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import com.tivo.kmttg.gui.gui;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class autoLogView {   
   private Stage dialog = null;
   private TextArea text = null;
   private static String logfile = config.autoLog + ".0";
   Timer timer;
   BufferedReader br = null;
   int max_lines = 100;
   Stack<String> lines = new Stack<String>();
   
   public autoLogView(Stage frame) {
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
      VBox content = new VBox();
      content.setSpacing(5);
      
      // text area
      text = new TextArea();
      text.setWrapText(true);
      HBox.setHgrow(text, Priority.ALWAYS);  // stretch horizontally
      VBox.setVgrow(text, Priority.ALWAYS);  // stretch vertically
      content.getChildren().add(text);

      // create and display dialog window
      dialog = new Stage();
      dialog.setTitle(logfile);
      dialog.setScene(new Scene(content));
      config.gui.setFontSize(dialog.getScene(), config.FontSize);
      dialog.setWidth(600);
      dialog.setHeight(400);
      dialog.initOwner(frame);
      gui.LoadIcons(dialog);
      dialog.show();

      // Start a timer that updates stdout/stderr text areas dynamically
      timer = new Timer();
      timer.schedule(
         new TimerTask() {
            @Override
            public void run() {
               Platform.runLater(new Runnable() {
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
            text.appendText(l + "\n");
         lines.clear();
      } catch (IOException e) {
         log.error("autoLogView update - " + e.getMessage());
      }
      text.setEditable(false);
   }
}
