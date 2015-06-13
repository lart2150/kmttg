package com.tivo.kmttg.gui.remote;

import java.io.File;

import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.MyButton;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.guideTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.kmttg;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.log;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.FileChooser.ExtensionFilter;

public class guide {
   public VBox panel = null;
   public guideTable tab = null;
   public ListView<String> ChanList = null;
   public  MyButton refresh = null;
   public ComboBox<String> tivo = null;
   public ComboBox<String> start = null;
   public CheckBox guide_channels = null;
   public MyButton record = null;
   public MyButton recordSP = null;
   public MyButton wishlist = null;
   public  int range = 12; // Number of hours to show in guide at a time
   public int hour_increment = 12; // Number of hours for date increment
   public int total_range = 11;    // Number of days
   public MyButton manual_record = null;
   
   public guide(final Stage frame) {
      
      // Guide Tab items            
      HBox row1 = new HBox();
      row1.setSpacing(5);
      row1.setAlignment(Pos.CENTER_LEFT);
      row1.setPadding(new Insets(5,0,0,5)); // top, right, bottom, left
      
      Label title = new Label("Guide");
      
      Label tivo_label = new Label();
      
      tivo = new ComboBox<String>();
      tivo.valueProperty().addListener(new ChangeListener<String>() {
         @Override public void changed(ObservableValue<? extends String> ov, String oldVal, String newVal) {
            // Don't do anything if oldVal is null or kmttg starting (implies values being reset)
            if (kmttg._startingUp || oldVal == null) return;
            
            if (newVal != null) {
                // Refresh channel list and clear table
               ChanList.getItems().clear();
               tab.clear();
               String tivoName = newVal;
               config.gui.remote_gui.updateButtonStates(tivoName, "Guide");
               tab.updateChannels(tivoName, false);
               if (tab.tivo_data.containsKey(tivoName))
                  tab.AddRows(tivoName, tab.tivo_data.get(tivoName));
            }
         }
      });
      tivo.setTooltip(tooltip.getToolTip("tivo_guide"));
      
      guide_channels = new CheckBox("All");
      guide_channels.setSelected(false);
      guide_channels.setTooltip(tooltip.getToolTip("guide_channels"));
      
      Label guide_start_label = new Label("Start");
      start = new ComboBox<String>();
      start.setTooltip(tooltip.getToolTip("guide_start"));
      // When start time changes need to update the table when appropriate
      start.valueProperty().addListener(new ChangeListener<String>() {
         @Override public void changed(ObservableValue<? extends String> ov, String oldVal, String newVal) {
            if (newVal != null) {
               String tivoName = tivo.getValue();
               if (tivoName != null && ChanList != null) {
                  String chanName = ChanList.getSelectionModel().getSelectedItem();
                  if (chanName != null)
                     tab.updateTable(tivoName, chanName);
               }
            }
         }
      });

      refresh = new MyButton("Channels");
      refresh.setPadding(util.smallButtonInsets());
      refresh.setTooltip(tooltip.getToolTip("refresh_guide"));
      refresh.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            ChanList.getItems().clear();
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               // Obtain and display channel list
               tab.updateChannels(tivoName, true);
            }
         }
      });

      MyButton export_channels = new MyButton("Export ...");
      export_channels.setPadding(util.smallButtonInsets());
      export_channels.setTooltip(tooltip.getToolTip("export_channels"));
      export_channels.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            final String tivoName = tivo.getValue();
            config.gui.remote_gui.Browser.getExtensionFilters().addAll(new ExtensionFilter("CSV Files", "*.csv"));
            config.gui.remote_gui.Browser.setTitle("Save to file");
            config.gui.remote_gui.Browser.setInitialFileName(
               config.programDir + File.separator + tivoName + "_channels.csv"
            );
            final File selectedFile = config.gui.remote_gui.Browser.showSaveDialog(frame);
            if (selectedFile != null) {
               Task<Void> task = new Task<Void>() {
                  @Override public Void call() {
                     log.warn("Exporting '" + tivoName + "' channel list to csv file: " + selectedFile.getAbsolutePath());
                     Remote r = config.initRemote(tivoName);
                     if (r.success) {
                        r.ChannelLineupCSV(selectedFile);
                        r.disconnect();
                     }
                     return null;
                  }
               };
               new Thread(task).start();
            }
         }
      });

      record = new MyButton("Record");
      record.setPadding(util.smallButtonInsets());
      record.setTooltip(tooltip.getToolTip("guide_record"));
      record.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               tab.recordSingle(tivoName);
            }
         }
      });

      recordSP = new MyButton("Season Pass");
      recordSP.setPadding(util.smallButtonInsets());
      recordSP.setTooltip(tooltip.getToolTip("guide_recordSP"));
      recordSP.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               tab.recordSP(tivoName);
            }
         }
      });
      
      wishlist = new MyButton("WL");
      wishlist.setPadding(util.smallButtonInsets());
      wishlist.setTooltip(tooltip.getToolTip("wishlist_search"));
      wishlist.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               int[] selected = TableUtil.GetSelectedRows(tab.TABLE);
               if (selected.length > 0) {
                  JSONObject json = tab.GetRowData(selected[0]);
                  config.gui.remote_gui.createWishlist(tivoName, json);
               }
            }
         }
      });
      
      manual_record = new MyButton("MR");
      manual_record.setPadding(util.smallButtonInsets());
      manual_record.setTooltip(tooltip.getToolTip("guide_manual_record"));
      manual_record.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               util.mRecordOpt.promptUser(tivoName);
            }
         }
      });

      MyButton guide_refresh_todo = new MyButton("Refresh ToDo");
      guide_refresh_todo.setPadding(util.smallButtonInsets());
      guide_refresh_todo.setTooltip(tooltip.getToolTip("guide_refresh_todo"));
      guide_refresh_todo.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               Task<Void> task = new Task<Void>() {
                  @Override public Void call() {
                     log.warn("Refreshing ToDo list for Guide entries...");
                     util.all_todo = util.getTodoLists();
                     log.warn("Refresh ToDo list for Guide entries completed.");
                     return null;
                  }
               };
               new Thread(task).start();
            }
         }
      });
      
      row1.getChildren().add(title);
      row1.getChildren().add(tivo_label);
      row1.getChildren().add(tivo);
      row1.getChildren().add(guide_start_label);
      row1.getChildren().add(start);
      row1.getChildren().add(guide_channels);
      row1.getChildren().add(refresh);
      row1.getChildren().add(export_channels);
      row1.getChildren().add(record);
      row1.getChildren().add(recordSP);
      row1.getChildren().add(wishlist);
      row1.getChildren().add(manual_record);
      row1.getChildren().add(guide_refresh_todo);
      
      tab = new guideTable();
      
      ChanList = new ListView<String>();
      ChanList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
      ChanList.setOrientation(Orientation.VERTICAL);
      // When a list item is selected, update the table when appropriate
      ChanList.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
         @Override
         public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
            if (newValue != null) {
               String tivoName = tivo.getValue();
               if (tivoName != null) {
                  tab.updateTable(tivoName, newValue);
               }
            }
         }
      });
      ChanList.setTooltip(tooltip.getToolTip("guideChanList"));
      
      GridPane tab_row = new GridPane();
      tab_row.setHgap(5);
      tab_row.setPadding(new Insets(0,0,0,5));
      tab_row.getColumnConstraints().add(0, util.cc_none());
      tab_row.getColumnConstraints().add(1, util.cc_stretch());
      ChanList.setMinWidth(150); ChanList.setMaxWidth(150);
      tab_row.add(ChanList, 0, 0);
      tab_row.add(tab.scroll, 1, 0);
            
      panel = new VBox();
      panel.setSpacing(5);
      panel.getChildren().addAll(row1, tab_row);      
   }

}
