package com.tivo.kmttg.gui.dialog;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class autoLogView {   
   private Stage dialog = null;
   private TextArea text = null;
   private static String logfile = config.autoLog + ".0";
   
   public autoLogView(Stage frame) {
      debug.print("frame=" + frame);
      
      if ( ! file.isFile(logfile)) {
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

      if (view()) {
         content.getChildren().add(text);
        
         // create and display dialog window
         dialog = new Stage();
         dialog.setTitle(logfile);
         dialog.setScene(new Scene(content));
         config.gui.setFontSize(dialog.getScene(), config.FontSize);
         dialog.setWidth(600);
         dialog.setHeight(400);
         dialog.initOwner(frame);
         dialog.show();

      } else {
         // Deallocate resources and return
         text = null;
         content = null;
         return;
      }
   }
     
   // Update text with auto log file contents
   private Boolean view() {
      try {
         BufferedReader log = new BufferedReader(new FileReader(logfile));
         String line = null;
         text.setEditable(true);
         while (( line = log.readLine()) != null) {
            text.appendText(line + "\n");
         }
         log.close();
         text.setEditable(false);
      }         
      catch (IOException ex) {
         log.error("Auto log file cannot be read: " + logfile);
         return false;
      }
      return true;
   }

}
