package com.tivo.kmttg.gui.remote;

import java.io.File;

import com.tivo.kmttg.gui.MyButton;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.thumbsTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.log;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.FileChooser.ExtensionFilter;

public class thumbs {
   public VBox panel = null;
   public thumbsTable tab = null;
   public MyButton refresh = null;
   public MyButton copy = null;
   public MyButton update = null;
   public Label label = null;
   public ComboBox<String> tivo = null;
   
   public thumbs(final Stage frame) {
      
      // Thumbs tab items      
      HBox row1 = new HBox();
      row1.setSpacing(5);
      row1.setAlignment(Pos.CENTER_LEFT);
      row1.setPadding(new Insets(5,0,0,5));
      
      Label title = new Label("Thumb Ratings");
      
      Label tivo_label = new Label();
      
      tivo = new ComboBox<String>();
      tivo.valueProperty().addListener(new ChangeListener<String>() {
         @Override public void changed(ObservableValue<? extends String> ov, String oldVal, String newVal) {
            if (newVal != null && config.gui.remote_gui != null) {                
               // TiVo selection changed for Thumbs tab
               TableUtil.clear(tab.TABLE);
               label.setText("");
               String tivoName = newVal;
               config.gui.remote_gui.updateButtonStates(tivoName, "Thumbs");
               if (tab.tivo_data.containsKey(tivoName))
                  tab.AddRows(tivoName, tab.tivo_data.get(tivoName));
               tab.updateLoadedStatus();
            }
         }
      });
      tivo.setTooltip(tooltip.getToolTip("tivo_thumbs"));

      MyButton save = new MyButton("Save...");
      save.setPadding(util.smallButtonInsets());
      save.setTooltip(tooltip.getToolTip("save_thumbs"));
      save.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            // Save thumbs list
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               if (tab.isTableLoaded()) {
                  log.error("Cannot save loaded Thumbs");
                  return;
               }  else {
                  config.gui.remote_gui.Browser.getExtensionFilters().addAll(new ExtensionFilter("Thumbs Files", "*.thumbs"));
                  config.gui.remote_gui.Browser.setTitle("Save to file");
                  config.gui.remote_gui.Browser.setInitialFileName(null);
                  final File selectedFile = config.gui.remote_gui.Browser.showSaveDialog(frame);
                  if (selectedFile != null) {
                     tab.saveThumbs(tivoName, selectedFile.getAbsolutePath());
                  }
               }
            }
         }
      });

      MyButton load = new MyButton("Load...");
      load.setPadding(util.smallButtonInsets());
      load.setTooltip(tooltip.getToolTip("load_thumbs"));
      load.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            // Load thumbs list
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               config.gui.remote_gui.Browser.getExtensionFilters().addAll(new ExtensionFilter("Thumbs Files", "*.thumbs"));
               config.gui.remote_gui.Browser.setTitle("Load thumbs file");
               config.gui.remote_gui.Browser.setInitialFileName(null);
               final File selectedFile = config.gui.remote_gui.Browser.showOpenDialog(frame);
               if (selectedFile != null) {
                  label.setText("");
                  tab.loadThumbs(selectedFile.getAbsolutePath());
               }
            }
         }
      });

      copy = new MyButton("Copy");
      copy.setPadding(util.smallButtonInsets());
      copy.setTooltip(tooltip.getToolTip("copy_thumbs"));
      copy.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            // Copy selected thumbs
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0)
               tab.copyThumbs(tivoName);
         }
      });

      refresh = new MyButton("Refresh");
      refresh.setPadding(util.smallButtonInsets());
      refresh.setTooltip(tooltip.getToolTip("refresh_thumbs"));
      refresh.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            // Refresh thumbs list
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               label.setText("");
               tab.refreshThumbs(tivoName);
            }
         }
      });

      update = new MyButton("Modify");
      update.setPadding(util.smallButtonInsets());
      update.setTooltip(tooltip.getToolTip("update_thumbs"));
      update.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            // Update thumbs list
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0)
               label.setText("");
               tab.updateThumbs(tivoName);
         }
      });
      
      label = new Label();
            
      row1.getChildren().add(title);
      row1.getChildren().add(tivo_label);
      row1.getChildren().add(tivo);
      row1.getChildren().add(refresh);
      row1.getChildren().add(save);
      row1.getChildren().add(load);
      row1.getChildren().add(copy);
      row1.getChildren().add(update);
      row1.getChildren().add(label);
      
      tab = new thumbsTable();
      
      panel = new VBox();
      panel.setSpacing(5);
      panel.getChildren().addAll(row1, tab.scroll);
      
   }
}
