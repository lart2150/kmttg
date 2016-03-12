package com.tivo.kmttg.gui.dialog;

import java.io.File;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.MyTooltip;
import com.tivo.kmttg.gui.remote.util;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class SkipShare {
   private Stage frame = null;
   private Stage dialog = null;
   private FileChooser FileBrowser = null;
   private CheckBox debug = null;
   private TextField zipFile = null;
   private TextField srtFile = null;
   private String tivo = null;
   private JSONObject json = null;
   
   public SkipShare(Stage frame, String tivo, JSONObject json, String zipFileName, String srtFileName) {
      this.frame = frame;
      this.tivo = tivo;
      this.json = json;
      if (dialog == null)
         init();
      else
         dialog.show();
      zipFile.setText(zipFileName);
      srtFile.setText(srtFileName);
   }
         
   private void init() {
      FileBrowser = new FileChooser(); FileBrowser.setInitialDirectory(new File(config.programDir));
      // Define content for dialog window
      VBox content = new VBox();
      content.setPadding(new Insets(5,5,5,5));
      content.setSpacing(5);
      
      GridPane panel = new GridPane();      
      panel.setAlignment(Pos.CENTER);
      panel.setVgap(5);
      panel.setHgap(5);
      panel.getColumnConstraints().addAll(util.cc_none(), util.cc_stretch());
      panel.setPadding(new Insets(5,5,5,5)); // top, right, bottom, left

      // Import button
      Button Import = new Button("Import");
      String tip = "<b>Import</b><br>Import given zip file and srt file into AutoSkip table.";
      Import.setTooltip(MyTooltip.make(tip));
      Import.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            String zip = string.removeLeadingTrailingSpaces(zipFile.getText());
            String srt = string.removeLeadingTrailingSpaces(srtFile.getText());
            if (! file.isFile(zip)) {
               log.error("zip file not found: " + zip);
               return;
            }
            if (! file.isFile(srt)) {
               log.error("srt file not found: " + srt);
               return;
            }
            if (com.tivo.kmttg.rpc.SkipShare.Import(tivo, json, zip, srt, debug.isSelected())) {
               log.print("Successfully imported skip share");
               dialog.close();
            } else {
               log.error("Skip share import failed");
            }
         }
      });
      
      // debug boolean
      debug = new CheckBox("ENABLE DEBUG");
      
      panel.add(Import, 0, 0);
      panel.add(debug, 1, 0);

      // Row 2 = zipFile field
      Label zipFile_label = new Label("Zip File (double click to browse)");
      zipFile = new TextField();
      zipFile.setPrefWidth(50);
      zipFile.setOnMouseClicked(new EventHandler<MouseEvent>() {
         @Override
         public void handle(MouseEvent mouseEvent) {
            if( mouseEvent.getButton().equals(MouseButton.PRIMARY) ) {
               if (mouseEvent.getClickCount() == 2) {
                  File selectedFile = FileBrowser.showOpenDialog(config.gui.getFrame());
                  if (selectedFile != null) {
                     zipFile.setText(selectedFile.getPath());
                  }
               }
            }
         }
      });
      panel.add(zipFile_label, 0, 1);
      panel.add(zipFile, 1, 1);
      
      // srtFile
      Label srtFile_label = new Label("srt File (double click to browse)");
      srtFile = new TextField();
      srtFile.setPrefWidth(50);
      srtFile.setOnMouseClicked(new EventHandler<MouseEvent>() {
         @Override
         public void handle(MouseEvent mouseEvent) {
            if( mouseEvent.getButton().equals(MouseButton.PRIMARY) ) {
               if (mouseEvent.getClickCount() == 2) {
                  File selectedFile = FileBrowser.showOpenDialog(config.gui.getFrame());
                  if (selectedFile != null) {
                     srtFile.setText(selectedFile.getPath());
                  }
               }
            }
         }
      });
      panel.add(srtFile_label, 0, 2);
      panel.add(srtFile, 1, 2);
      content.getChildren().add(panel);

      dialog = new Stage();
      dialog.initOwner(frame);
      dialog.setTitle("AutoSkip Import");
      Scene scene = new Scene(new VBox());
      config.gui.setFontSize(scene, config.FontSize);
      ((VBox) scene.getRoot()).getChildren().add(content);
      dialog.setScene(scene);
      dialog.setMinWidth(600);
      dialog.show();      
   }
}
