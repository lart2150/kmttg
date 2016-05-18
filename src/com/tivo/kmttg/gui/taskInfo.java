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

import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;

// This is used for dynamic display of running task stdout/stderr
public class taskInfo {
   backgroundProcess process;
   Timer timer;
   private Stack<String> owatch = new Stack<String>();
   private Stack<String> ewatch = new Stack<String>();
   
   private Stage dialog;
   private TextArea stdout = null;
   private TextArea stderr = null;     
   
   public taskInfo(Stage frame, String description, backgroundProcess process) {
      debug.print("frame=" + frame + " description=" + description + " process=" + process);
      Label job_label;
      Label stdout_label;
      Label stderr_label;
      this.process = process;
      
      // Define content for dialog window
      VBox content = new VBox();
      content.setSpacing(2);
      HBox.setHgrow(content, Priority.ALWAYS);  // stretch horizontally
      
      // job description label
      job_label = new Label(description);
      HBox.setHgrow(job_label, Priority.ALWAYS);  // stretch horizontally
            
      // stdout label
      stdout_label = new Label("stdout");
      
      // stdout text area
      stdout = new TextArea();
      VBox.setVgrow(stdout, Priority.ALWAYS);  // stretch vertically
      stdout.setEditable(false);
      stdout.setWrapText(true);
      
      // stderr label
      stderr_label = new Label("stderr");
      
      // stderr text area
      stderr = new TextArea();
      VBox.setVgrow(stderr, Priority.ALWAYS);  // stretch vertically
      stderr.setEditable(false);
      stderr.setWrapText(true);
      
      content.getChildren().addAll(job_label, stdout_label, stdout, stderr_label, stderr);
     
      // create and display dialog window
      dialog = new Stage();
      dialog.initModality(Modality.NONE); // Non modal
      dialog.initOwner(frame);
      gui.LoadIcons(dialog);
      dialog.setTitle("Task stdout/stderr viewer");
      Scene scene = new Scene(new VBox());
      config.gui.setFontSize(scene, config.FontSize);
      ((VBox) scene.getRoot()).getChildren().add(content);
      dialog.setScene(scene);
      dialog.setWidth(600); dialog.setHeight(400);
      dialog.show();
      
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
            stdout.appendText(s.get(i) + "\n");
         stdout.setEditable(false);
      }
   }
   
   public void appendStderr(Stack<String> s) {
      if (s != null && s.size() > 0) {
         stderr.setEditable(true);
         for (int i=0; i<s.size(); ++i)
            stderr.appendText(s.get(i) + "\n");
         stderr.setEditable(false);
      }
   }

}
