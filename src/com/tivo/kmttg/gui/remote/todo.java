package com.tivo.kmttg.gui.remote;

import java.io.File;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;

import com.tivo.kmttg.gui.MyButton;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.todoTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.log;

public class todo {
   public VBox panel = null;
   public todoTable tab = null;
   public ComboBox<String> tivo = null;
   public Label label = null;
   public MyButton cancel = null;
   public MyButton modify = null;
   
   public todo(final Stage frame) {
      // ToDo Tab items            
      HBox row1 = new HBox();
      row1.setSpacing(5);
      row1.setPadding(new Insets(5,0,0,5));
      row1.setAlignment(Pos.CENTER_LEFT);
      
      Label title = new Label("ToDo list");
      
      Label tivo_label = new Label();
      
      tivo = new ComboBox<String>();
      tivo.valueProperty().addListener(new ChangeListener<String>() {
         @Override public void changed(ObservableValue<? extends String> ov, String oldVal, String newVal) {
            if (newVal != null && config.gui.remote_gui != null) {
                TableUtil.clear(tab.TABLE);
                label.setText("");
                String tivoName = newVal;
                config.gui.remote_gui.updateButtonStates(tivoName, "ToDo");
                if (tab.tivo_data.containsKey(tivoName))
                   tab.AddRows(tivoName, tab.tivo_data.get(tivoName));
            }
         }
      });
      tivo.setTooltip(tooltip.getToolTip("tivo_todo"));

      MyButton refresh = new MyButton("Refresh");
      refresh.setPadding(util.mediumButtonInsets());
      refresh.setTooltip(tooltip.getToolTip("refresh_todo"));
      refresh.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            // Refresh to do list
            TableUtil.clear(tab.TABLE);
            label.setText("");
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               jobData job = new jobData();
               job.source      = tivoName;
               job.tivoName    = tivoName;
               job.type        = "remote";
               job.name        = "Remote";
               job.remote_todo = true;
               job.todo        = tab;
               jobMonitor.submitNewJob(job);
            }
         }
      });

      cancel = new MyButton("Cancel");
      cancel.setPadding(util.mediumButtonInsets());
      cancel.setTooltip(tooltip.getToolTip("cancel_todo"));
      cancel.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            tab.DeleteCB();
         }
      });

      modify = new MyButton("Modify");
      modify.setPadding(util.mediumButtonInsets());
      modify.setTooltip(tooltip.getToolTip("modify_todo"));
      modify.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               tab.recordSingle(tivoName);
            }
         }
      });

      MyButton export = new MyButton("Export ...");
      export.setPadding(util.mediumButtonInsets());
      export.setTooltip(tooltip.getToolTip("export_todo"));
      export.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            final String tivoName = tivo.getValue();
            config.gui.remote_gui.Browser.getExtensionFilters().addAll(new ExtensionFilter("CSV Files", "*.csv"));
            config.gui.remote_gui.Browser.setTitle("Save to file");
            config.gui.remote_gui.Browser.setInitialFileName(
               config.programDir + File.separator + tivoName +
               "_" + TableUtil.currentYearMonthDay() + ".csv"
            );
            final File selectedFile = config.gui.remote_gui.Browser.showSaveDialog(frame);
            if (selectedFile != null) {
               Task<Void> task = new Task<Void>() {
                  @Override public Void call() {
                     log.warn("Exporting '" + tivoName + "' todo list to csv file: " + selectedFile.getAbsolutePath());
                     Remote r = config.initRemote(tivoName);
                     if (r.success) {
                        r.TodoExportCSV(selectedFile);
                        r.disconnect();
                     }
                     return null;
                  }
               };
               new Thread(task).start();
            }
         }
      });
      
      label = new Label();
      
      row1.getChildren().add(title);
      row1.getChildren().add(tivo_label);
      row1.getChildren().add(tivo);
      row1.getChildren().add(refresh);
      row1.getChildren().add(cancel);
      row1.getChildren().add(modify);
      row1.getChildren().add(export);
      row1.getChildren().add(label);
      
      tab = new todoTable();
            
      panel = new VBox();
      panel.setSpacing(1);
      panel.getChildren().addAll(row1, tab.scroll);
   }
}
