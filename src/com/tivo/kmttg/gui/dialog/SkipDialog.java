package com.tivo.kmttg.gui.dialog;

import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.MyTooltip;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.skipTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.SkipMode;
import com.tivo.kmttg.util.log;

public class SkipDialog {
   private Stage frame = null;
   private Stage dialog = null;
   private skipTable tab = null;
   private JSONArray data = null;
   
   public SkipDialog(Stage frame) {
      this.frame = frame;      
      getEntries();
   }
   
   // Retrieve entries from SkipMode.ini file
   private void getEntries() {
      if (tab != null)
         tab.clear();
      data = SkipMode.getEntries();      
      if (data != null && data.length() > 0) {
         if (dialog == null)
            init();
         else
            tab.AddRows(data);
      } else {
         log.warn("No data available to display");
      }
   }
   
   private void removeEntries(JSONArray entries) {
      // Run in separate background thread
      class backgroundRun extends Task<Void> {
         JSONArray entries;

         public backgroundRun(JSONArray entries) {
            this.entries = entries;
         }
         @Override
         protected Void call() {
            try {
               for (int i=0; i<entries.length(); ++i) {
                  JSONObject json = entries.getJSONObject(i);
                  SkipMode.removeEntry(json.getString("contentId"));
               }
            } catch (Exception e) {
               log.error("removeEntries - " + e.getMessage());
               return null;
            }
            return null;
         }
      }
      backgroundRun b = new backgroundRun(entries);
      new Thread(b).start();
   }
         
   private void init() {
      // Define content for dialog window
      VBox content = new VBox();

      // Refresh button
      Button refresh = new Button("Refresh");
      String tip = "<b>Refresh</b><br>Get list of SkipMode entries and refresh table.";
      refresh.setTooltip(MyTooltip.make(tip));
      refresh.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            getEntries();
         }
      });

      // Change button
      Button change = new Button("Change");
      tip = "<b>Change</b><br>Update offsets for entries you changed in the table.<br>";
      tip += "NOTE: The offset value is a time in milliseconds.";
      change.setTooltip(MyTooltip.make(tip));
      change.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            tab.changeTable();
         }
      });

      // Remove button
      Button remove = new Button("Remove");
      tip = "<b>Remove</b><br>Remove selected entry in the table from SkipMode file.";
      remove.setTooltip(MyTooltip.make(tip));
      remove.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            int[] selected = TableUtil.GetSelectedRows(tab.getTable());
            if (selected == null || selected.length != 1) {
               log.error("Must select a single table row.");
               return;
            }
            JSONArray entries = new JSONArray();
            int row = selected[0];
            JSONObject json = tab.GetRowData(row);
            if (json != null)
               entries.put(json);
            tab.RemoveRow(row);
            if (entries.length() > 0)
               removeEntries(entries);
         }
      });
      
      // Row 1 = buttons
      HBox row1 = new HBox();
      row1.setSpacing(5);
      row1.getChildren().addAll(refresh, change, remove);
      content.getChildren().add(row1);
      
      // Table
      tab = new skipTable();
      VBox.setVgrow(tab.TABLE, Priority.ALWAYS); // stretch vertically
      tab.AddRows(data);
      content.getChildren().add(tab.TABLE);
      VBox.setVgrow(content, Priority.ALWAYS); // stretch vertically

      dialog = new Stage();
      dialog.initOwner(frame);
      dialog.setTitle("SkipMode Entries");
      Scene scene = new Scene(new VBox());
      config.gui.setFontSize(scene, config.FontSize);
      ((VBox) scene.getRoot()).getChildren().add(content);
      dialog.setScene(scene);
      dialog.setWidth(frame.getWidth()/1.5);
      dialog.setHeight(frame.getHeight()/3);
      dialog.show();      
   }
}
