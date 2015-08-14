package com.tivo.kmttg.gui.remote;

import java.util.Hashtable;
import java.util.Optional;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.MyButton;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.log;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class info {
   public VBox panel = null;   
   public ChoiceBox<String> tivo = null;
   public MyButton reboot = null;
   public TextArea text = null;
   public Hashtable<String,String> tivo_data = new Hashtable<String,String>();
   public Hashtable<String, MyButton> buttons = new Hashtable<String, MyButton>();
   
   public info (final Stage frame) {
      
      // System Information tab items      
      HBox row1 = new HBox();
      row1.setSpacing(5);
      row1.setAlignment(Pos.CENTER_LEFT);
      row1.setPadding(new Insets(5,0,0,5));

      Label title = new Label("System Information");

      Label tivo_label = new Label("");

      tivo = new ChoiceBox<String>();
      tivo.valueProperty().addListener(new ChangeListener<String>() {
         @Override public void changed(ObservableValue<? extends String> ov, String oldVal, String newVal) {
            if (newVal != null) {
               // Clear text area
               text.setEditable(true);
               text.setText("");

               // Put cached info in text area if available
               String tivoName = newVal;
               if (config.gui.remote_gui != null)
                  config.gui.remote_gui.updateButtonStates(tivoName, "Info");
               if (tivoName != null && tivoName.length() > 0) {
                  if (tivo_data.containsKey(tivoName))
                     text.setText(tivo_data.get(tivoName));
               }
               text.setEditable(false);
            }
         }
      });
      tivo.setTooltip(tooltip.getToolTip("tivo_info"));

      MyButton refresh = new MyButton("Refresh");
      refresh.setPadding(util.mediumButtonInsets());
      refresh.setTooltip(tooltip.getToolTip("refresh_info"));
      refresh.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            // Refresh info text
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0)
               RC_infoCB(tivoName);
         }
      });

      MyButton netconnect = new MyButton("Network Connect");
      netconnect.setPadding(util.mediumButtonInsets());
      netconnect.setTooltip(tooltip.getToolTip("netconnect_info"));
      netconnect.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            // Initiate a net connect on selected TiVo
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               Remote r = config.initRemote(tivoName);
               if (r.success) {
                  JSONObject result = r.Command("PhoneHome", new JSONObject());
                  if (result == null)
                     log.error("NOTE: If this TiVo is in 'Pending Restart' state Network Connect fails.");
                  else
                     log.warn("Network Connection initiated on: " + tivoName);
                  r.disconnect();
               }
            }
         }
      });

      reboot = new MyButton("Reboot");
      reboot.setPadding(util.mediumButtonInsets());
      reboot.setTooltip(tooltip.getToolTip("reboot_info"));
      reboot.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            // Reboot selected TiVo
            final String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               Platform.runLater(new Runnable() {
                  @Override public void run() {
                     Alert alert = new Alert(AlertType.CONFIRMATION);
                     alert.setTitle("Reboot " + tivoName + "?");
                     config.gui.setFontSize(alert, config.FontSize);
                     alert.setContentText("OK to reboot?");
                     Optional<ButtonType> result = alert.showAndWait();
                     if (result.get() == ButtonType.OK) {
                        Remote r = config.initRemote(tivoName);
                        if (r.success) {
                           r.reboot(tivoName);
                        }
                     }
                  }
               });
            }
         }
      });

      row1.getChildren().add(title);
      row1.getChildren().add(tivo_label);
      row1.getChildren().add(tivo);
      row1.getChildren().add(util.space(40));
      row1.getChildren().add(refresh);
      row1.getChildren().add(netconnect);
      row1.getChildren().add(util.space(40));
      row1.getChildren().add(reboot);

      text = new TextArea();
      text.setStyle("-fx-font-family: monospace;");
      text.setEditable(false);
      text.setWrapText(true); // This disables horizontal scrollbar
      VBox.setVgrow(text, Priority.ALWAYS); // stretch vertically

      panel = new VBox();
      panel.setSpacing(1);
      panel.getChildren().addAll(row1, text);

   }
   private void RC_infoCB(final String tivoName) {
      Task<Void> task = new Task<Void>() {
         @Override public Void call() {
            log.warn("Collecting info for TiVo: " + tivoName + " ...");
            Remote r = config.initRemote(tivoName);
            if (r.success) {
               JSONObject json = new JSONObject();
               JSONObject reply = r.Command("SysInfo", json);
               if (reply != null && reply.has("bodyConfig")) {
                  try {
                     String info = "";
                     // System info
                     json = reply.getJSONArray("bodyConfig").getJSONObject(0);
                     if (json.has("userDiskSize") && json.has("userDiskUsed")) {
                        Float sizeGB = (float)json.getLong("userDiskSize")/(1024*1024);
                        // Update diskSpace hash if necessary
                        Boolean update = true;
                        if (config.diskSpace.containsKey(tivoName)) {
                           if (Math.round(config.diskSpace.get(tivoName)) == Math.round(sizeGB))
                              update = false;
                        }
                        if (update) {
                           log.warn("Updating " + tivoName + " disk space to " + sizeGB + " GB");
                           config.diskSpace.put(tivoName, sizeGB);
                           config.save();
                        }
                        Float pct = (float)100*json.getLong("userDiskUsed")/json.getLong("userDiskSize");
                        String pct_string = String.format("%s (%5.2f%%)", json.get("userDiskUsed"), pct);
                        String size_string = String.format("%s (%5.2f GB)", json.get("userDiskSize"), sizeGB);
                        json.put("userDiskSize", size_string);
                        json.put("userDiskUsed", pct_string);
                     }
                     if (json.has("bodyId")) {
                        info += String.format("%-30s %s\n", "tsn",
                           json.getString("bodyId").replaceFirst("tsn:", "")
                        );
                     }
                     String[] fields = {
                        "softwareVersion", "userDiskSize", "userDiskUsed", "parentalControlsState"
                     };
                     for (int i=0; i<fields.length; ++i) {
                        if (json.has(fields[i]))
                           info += String.format("%-30s %s\n", fields[i], json.get(fields[i]));
                     }

                     if (! r.awayMode() ) {
                        // What's On info
                        String [] whatson = getWhatsOn(tivoName);
                        if (whatson != null) {
                           info += String.format("%-30s ", "What's On");
                           for (int i=0; i<whatson.length; ++i) {
                              if (i>0)
                                 info += "; ";
                              info += whatson[i];
                           }
                           info += "\n";
                        }
                        info += "\n";
                     
                        // Tuner info
                        reply = r.Command("TunerInfo", new JSONObject());
                        if (reply != null && reply.has("state")) {
                           for (int i=0; i<reply.getJSONArray("state").length(); ++i) {
                              json = reply.getJSONArray("state").getJSONObject(i);
                              info += String.format("%-30s %s\n", "tunerId", json.getString("tunerId"));
                              if (json.has("channel")) {
                                 info += String.format("%-30s %s", "channelNumber",
                                    json.getJSONObject("channel").getString("channelNumber")
                                 );
                                 if (json.getJSONObject("channel").has("callSign"))
                                    info += " (" + json.getJSONObject("channel").getString("callSign") + ")";
                              }
                              info += "\n\n";
                           }
                        }
                     }
                     
                     // Add info to text_info widget
                     text.setEditable(true);
                     text.setText(info);
                     text.setEditable(false);
                     tivo_data.put(tivoName, info);

                  } catch (JSONException e) {
                     log.error("RC_infoCB failed - " + e.getMessage());
                  }
               }
               r.disconnect();
            }
            log.warn("Done collecting info");
            return null;
         }
      };
      new Thread(task).start();
   }
   
   private String[] getWhatsOn(String tivoName) {
      Remote r = config.initRemote(tivoName);
      if (r.success) {
         JSONObject result = r.Command("WhatsOn", new JSONObject());
         if (result != null && result.has("whatsOn")) {
            try {
               JSONArray a = result.getJSONArray("whatsOn");
               String display[] = new String[a.length()];
               for (int i=0; i<a.length(); ++i) {
                  JSONObject o = a.getJSONObject(i);
                  if (o.has("playbackType")) {
                     display[i] = "" + o.getString("playbackType");
                     if (! o.getString("playbackType").equals("idle") && o.has("channelIdentifier")) {
                        JSONObject c = o.getJSONObject("channelIdentifier");
                        if (c.has("channelNumber"))
                           display[i] = display[i] + " (channel " + c.getString("channelNumber") + ")";
                     }
                  }
               }
               r.disconnect();
               return display;
            } catch (JSONException e1) {
               log.error("getWhatsOn json error: " + e1.getMessage());
               r.disconnect();
               return null;
            }
         }
         r.disconnect();
      }
      return null;
   }

}
