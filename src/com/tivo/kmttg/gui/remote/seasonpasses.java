package com.tivo.kmttg.gui.remote;

import java.io.File;

import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.MyButton;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.spTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
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
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.FileChooser.ExtensionFilter;

public class seasonpasses {
   public VBox panel = null;
   public spTable tab = null;
   public ComboBox<String> tivo = null;
   public MyButton copy = null;
   public MyButton conflicts = null;
   public MyButton modify = null;
   public MyButton upcoming = null;   
   public MyButton reorder = null;
   
   public seasonpasses(final Stage frame) {
      
      // Season Passes Tab items      
      HBox row1 = new HBox();
      row1.setSpacing(5);
      row1.setAlignment(Pos.CENTER_LEFT);
      row1.setPadding(new Insets(5,0,0,5));

      Label title = new Label("Season Passes");

      Label tivo_label = new Label();

      tivo = new ComboBox<String>();
      tivo.valueProperty().addListener(new ChangeListener<String>() {
         @Override public void changed(ObservableValue<? extends String> ov, String oldVal, String newVal) {
            if (newVal != null && config.gui.remote_gui != null) {
               TableUtil.clear(tab.TABLE);
               String tivoName = newVal;
               config.gui.remote_gui.updateButtonStates(tivoName, "Season Passes");
               if (tab.tivo_data.containsKey(tivoName))
                  tab.AddRows(tivoName, tab.tivo_data.get(tivoName));
               tab.updateLoadedStatus();
            }
         }
      });
      tivo.setTooltip(tooltip.getToolTip("tivo_sp"));

      MyButton refresh = new MyButton("Refresh");
      refresh.setPadding(util.smallButtonInsets());
      refresh.setTooltip(tooltip.getToolTip("refresh_sp"));
      refresh.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            // Refresh SP list
            TableUtil.clear(tab.TABLE);
            tab.setLoaded(false);
            SPListCB(tivo.getValue());
         }
      });

      MyButton save = new MyButton("Save...");
      save.setPadding(util.smallButtonInsets());
      save.setTooltip(tooltip.getToolTip("save_sp"));
      save.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            // Save SP data to a file
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               if (tab.isTableLoaded()) {
                  log.error("Cannot save loaded Season Passes");
                  return;
               }  else {
                  config.gui.remote_gui.Browser.getExtensionFilters().clear();
                  config.gui.remote_gui.Browser.getExtensionFilters().addAll(new ExtensionFilter("SP Files", "*.sp"));
                  config.gui.remote_gui.Browser.setTitle("Save to file");
                  config.gui.remote_gui.Browser.setInitialFileName(
                     config.programDir + File.separator + tivoName + ".sp"
                  );
                  final File selectedFile = config.gui.remote_gui.Browser.showSaveDialog(frame);
                  if (selectedFile != null) {
                     tab.SPListSave(tivoName, selectedFile.getAbsolutePath());
                  }
               }
            }
         }
      });         

      MyButton load = new MyButton("Load...");
      load.setPadding(util.smallButtonInsets());
      load.setTooltip(tooltip.getToolTip("load_sp"));
      load.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            // Load SP data from a file
            config.gui.remote_gui.Browser.getExtensionFilters().clear();
            config.gui.remote_gui.Browser.getExtensionFilters().addAll(new ExtensionFilter("SP Files", "*.sp"));
            config.gui.remote_gui.Browser.setTitle("Load from file");
            config.gui.remote_gui.Browser.setInitialFileName(null);
            final File selectedFile = config.gui.remote_gui.Browser.showOpenDialog(frame);
            if (selectedFile != null) {
               tab.SPListLoad(selectedFile.getAbsolutePath());
            }
         }
      });         

      MyButton export = new MyButton("Export...");
      export.setPadding(util.smallButtonInsets());
      export.setTooltip(tooltip.getToolTip("export_sp"));
      export.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            // Export SP data to a file in csv format
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               if (tab.isTableLoaded()) {
                  log.error("Cannot export loaded Season Passes");
                  return;
               }  else {
                  config.gui.remote_gui.Browser.getExtensionFilters().clear();
                  config.gui.remote_gui.Browser.getExtensionFilters().addAll(new ExtensionFilter("CSV Files", "*.csv"));
                  config.gui.remote_gui.Browser.setTitle("Export to csv file");
                  config.gui.remote_gui.Browser.setInitialFileName(
                     config.programDir + File.separator + tivoName + "" + ".csv"
                  );
                  final File selectedFile = config.gui.remote_gui.Browser.showSaveDialog(frame);
                  if (selectedFile != null) {
                     tab.SPListExport(tivoName, selectedFile.getAbsolutePath());
                  }
               }
            }
         }
      });         

      copy = new MyButton("Copy");
      copy.setPadding(util.smallButtonInsets());
      copy.setTooltip(tooltip.getToolTip("copy_sp"));
      copy.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            // Copy selected SPs to a TiVo
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0)
               tab.SPListCopy(tivoName);
         }
      });         

      MyButton delete = new MyButton("Delete");
      delete.setPadding(util.smallButtonInsets());
      delete.setTooltip(tooltip.getToolTip("delete_sp"));
      delete.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            tab.SPListDelete();
         }
      });         

      modify = new MyButton("Modify");
      modify.setPadding(util.smallButtonInsets());
      modify.setTooltip(tooltip.getToolTip("modify_sp"));
      modify.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            // Modify selected SP
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               if (tab.isTableLoaded()) {
                  log.error("Cannot modify loaded Season Passes");
                  return;
               }  else {
                  tab.SPListModify(tivoName);
               }
            }
         }
      });         

      reorder = new MyButton("Re-order");
      reorder.setPadding(util.smallButtonInsets());
      reorder.setTooltip(tooltip.getToolTip("reorder_sp"));
      reorder.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            // Re-prioritize SPs on TiVo to match current table row order
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               if (tab.isTableLoaded()) {
                  log.error("Cannot re-order loaded Season Passes");
                  return;
               }  else {
                  tab.SPReorderCB(tivoName);
               }
            }
         }
      });         

      upcoming = new MyButton("Upcoming");
      upcoming.setPadding(util.smallButtonInsets());
      upcoming.setTooltip(tooltip.getToolTip("upcoming_sp"));
      upcoming.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            int selected[] = TableUtil.GetSelectedRows(tab.TABLE);
            if (selected.length > 0) {
               int row = selected[0];
               JSONObject json = tab.GetRowData(row);
               if (json.has("__upcoming")) {
                  // Get upcoming SP episodes and display in ToDo table
                  config.gui.remote_gui.todo_tab.tab.clear();
                  config.gui.remote_gui.todo_tab.label.setText("");
                  String tivoName = tivo.getValue();
                  try {
                     if (tivoName != null && tivoName.length() > 0) {
                        jobData job = new jobData();
                        job.source          = tivoName;
                        job.tivoName        = tivoName;
                        job.type            = "remote";
                        job.name            = "Remote";
                        job.remote_upcoming = true;
                        job.rnpl            = json.getJSONArray("__upcoming");
                        job.todo            = config.gui.remote_gui.todo_tab.tab;
                        jobMonitor.submitNewJob(job);
                     }
                  } catch (JSONException e1) {
                     log.error("upcoming error - " + e1.getMessage());
                  }
               } else {
                  log.warn("No upcoming episodes scheduled for selected Season Pass");
               }
            }
         }
      });

      conflicts = new MyButton("Conflicts");
      conflicts.setPadding(util.smallButtonInsets());
      conflicts.setTooltip(tooltip.getToolTip("conflicts_sp"));
      conflicts.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            int selected[] = TableUtil.GetSelectedRows(tab.TABLE);
            if (selected.length > 0) {
               int row = selected[0];
               JSONObject json = tab.GetRowData(row);
               if (json.has("__conflicts")) {
                  // Get conflict SP episodes and display in Won't Record table
                  config.gui.remote_gui.cancel_tab.tab.clear();
                  String tivoName = tivo.getValue();
                  try {
                     if (tivoName != null && tivoName.length() > 0) {
                        jobData job = new jobData();
                        job.source           = tivoName;
                        job.tivoName         = tivoName;
                        job.type             = "remote";
                        job.name             = "Remote";
                        job.remote_conflicts = true;
                        job.rnpl             = json.getJSONArray("__conflicts");
                        job.cancelled        = config.gui.remote_gui.cancel_tab.tab;
                        jobMonitor.submitNewJob(job);
                     }
                  } catch (JSONException e1) {
                     log.error("conflicts error - " + e1.getMessage());
                  }
               } else {
                  log.warn("No conflicting episodes for selected Season Pass");
               }
            }
         }
      });

      row1.getChildren().add(title);
      row1.getChildren().add(tivo_label);
      row1.getChildren().add(tivo);
      row1.getChildren().add(refresh);
      row1.getChildren().add(save);
      row1.getChildren().add(load);
      row1.getChildren().add(export);
      row1.getChildren().add(delete);
      row1.getChildren().add(copy);
      row1.getChildren().add(modify);
      row1.getChildren().add(reorder);
      row1.getChildren().add(upcoming);
      row1.getChildren().add(conflicts);

      tab = new spTable();
      VBox.setVgrow(tab.TABLE, Priority.ALWAYS); // stretch vertically

      panel = new VBox();
      panel.setSpacing(1);
      panel.getChildren().addAll(row1, tab.TABLE);      
   }
   
   // Submit remote SP request to Job Monitor
   public void SPListCB(String tivoName) {
      jobData job = new jobData();
      job.source      = tivoName;
      job.tivoName    = tivoName;
      job.type        = "remote";
      job.name        = "Remote";
      job.remote_sp   = true;
      job.sp          = tab;
      jobMonitor.submitNewJob(job);
   }

}
