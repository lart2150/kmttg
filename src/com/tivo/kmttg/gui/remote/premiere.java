package com.tivo.kmttg.gui.remote;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Stack;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.MyButton;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.premiereTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class premiere {
   public VBox panel = null;
   public premiereTable tab = null;   
   public ComboBox<String> tivo = null;
   public ComboBox<String> days = null;
   public ListView<String> channels = null;
   public Hashtable<String,JSONArray> channel_info = new Hashtable<String,JSONArray>();
   public MyButton record = null;
   public MyButton recordSP = null;
   public MyButton wishlist = null;

   public premiere(final Stage frame) {
      
      // Premiere tab items            
      HBox row1 = new HBox();
      row1.setSpacing(5);
      row1.setAlignment(Pos.CENTER_LEFT);
      row1.setPadding(new Insets(5,0,0,5));
      
      Label title = new Label("Season Premieres");
      
      Label tivo_label = new Label();
      
      tivo = new ComboBox<String>();
      tivo.valueProperty().addListener(new ChangeListener<String>() {
         @Override public void changed(ObservableValue<? extends String> ov, String oldVal, String newVal) {
            if (newVal != null) {   
               // Clear channel list
               channels.getItems().clear();
               
               String tivoName = newVal;
               if (config.gui.remote_gui != null)
                  config.gui.remote_gui.updateButtonStates(tivoName, "Season Premieres");
               // Load channel list for this TiVo
               loadChannelInfo(tivoName);
            }
         }
      });
      tivo.setTooltip(tooltip.getToolTip("tivo_premiere"));

      Label days_label = new Label("Days");      
      days = new ComboBox<String>();
      days.setTooltip(tooltip.getToolTip("premiere_days"));
      for (int i=1; i<=12; ++i) {
         days.getItems().add("" + i);
      }
      days.setValue("12");

      MyButton refresh = new MyButton("Search");
      refresh.setPadding(util.smallButtonInsets());
      refresh.setTooltip(tooltip.getToolTip("refresh_premiere"));
      refresh.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            // Refresh table
            TableUtil.clear(tab.TABLE);
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {            
               // This updates premiere_channel_info "isSelected" settings
               if ( ! updateSelectedChannels(tivoName) )
                  return;
               
               // Save channel information to file
               saveChannelInfo(tivoName);
               
               // Now search for Premieres in background mode
               jobData job = new jobData();
               job.source          = tivoName;
               job.tivoName        = tivoName;
               job.type            = "remote";
               job.name            = "Remote";
               job.remote_premiere = true;
               job.premiere        = tab;
               jobMonitor.submitNewJob(job);
            }
         }
      });
      
      record = new MyButton("Record");
      record.setPadding(util.smallButtonInsets());
      record.setTooltip(tooltip.getToolTip("record_premiere"));
      record.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0)
               tab.recordSingle(tivoName);
         }
      });
      
      recordSP = new MyButton("Season Pass");
      recordSP.setPadding(util.smallButtonInsets());
      recordSP.setTooltip(tooltip.getToolTip("recordSP_premiere"));
      recordSP.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0)
               tab.recordSP(tivoName);
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
               JSONObject json = null;
               if (selected.length > 0)
                  json = tab.GetRowData(selected[0]);
               config.gui.remote_gui.createWishlist(tivoName, json);
            }
         }
      });
      
      MyButton channels_update = new MyButton("Update Channels");
      channels_update.setPadding(util.smallButtonInsets());
      channels_update.setTooltip(tooltip.getToolTip("premiere_channels_update"));
      channels_update.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            String tivoName = tivo.getValue();
            if (tivoName != null && tivoName.length() > 0) {
               // Build list of received channels for this TiVo
               jobData job = new jobData();
               job.source          = tivoName;
               job.tivoName        = tivoName;
               job.type            = "remote";
               job.name            = "Remote";
               job.remote_channels = true;
               jobMonitor.submitNewJob(job);
            }
         }
      });
      
      channels = new ListView<String>();
      channels.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
      channels.setOrientation(Orientation.VERTICAL);
      
      channels.setTooltip(tooltip.getToolTip("premiere_channels"));
      
      row1.getChildren().add(title);
      row1.getChildren().add(tivo_label);
      row1.getChildren().add(tivo);
      row1.getChildren().add(refresh);
      row1.getChildren().add(days);
      row1.getChildren().add(days_label);
      row1.getChildren().add(record);
      row1.getChildren().add(recordSP);
      row1.getChildren().add(wishlist);
      row1.getChildren().add(util.space(40));
      row1.getChildren().add(channels_update);
      
      tab = new premiereTable();
      
      GridPane row2 = new GridPane();
      row2.setHgap(5);
      row2.setPadding(new Insets(0,0,0,5));      
      row2.getColumnConstraints().add(0, util.cc_stretch());
      row2.getColumnConstraints().add(1, util.cc_none());
      channels.setMinWidth(150); channels.setMaxWidth(150);
      row2.add(tab.scroll, 0, 0);
      row2.add(channels, 1, 0);

      panel = new VBox();
      panel.setSpacing(5);
      panel.getChildren().addAll(row1, row2);
      
   }
   
   // Read channel info from a file
   // Columns are:
   // channelNumber, callSign, sourceType, isSelected
   public void loadChannelInfo(String tivoName) {
      String fileName = config.programDir + File.separator + tivoName + ".channels";
      if (file.isFile(fileName)) {
         try {
            JSONArray a = new JSONArray();
            String line;
            Stack<Integer> selected = new Stack<Integer>();
            int count = 0;
            BufferedReader ifp = new BufferedReader(new FileReader(fileName));
            while ( (line=ifp.readLine()) != null ) {
               line = line.replaceFirst("^\\s*(.*$)", "$1");
               line = line.replaceFirst("^(.*)\\s*$", "$1");
               if (line.length() == 0) continue; // skip empty lines
               if (line.matches("^#.+")) continue; // skip comment lines
               String Fields[] = line.split(", ");
               JSONObject json = new JSONObject();
               json.put("channelNumber", Fields[0]);
               json.put("callSign", Fields[1]);
               json.put("sourceType", Fields[2]);
               json.put("isSelected", Fields[3]);
               if (Fields[3].equals("true"))
                  selected.add(count);
               a.put(json);
               count++;
            }
            ifp.close();
            if (a.length() > 0) {
               putChannelData(tivoName, a);
            }
            if (selected.size() > 0) {
               for (int i=0; i<selected.size(); ++i) {
                  channels.getSelectionModel().select(selected.get(i));
               }
            }
         } catch (Exception e1) {
            log.error("loadChannelInfo - " + e1.getMessage());
         }
      }
   }
   
   public Boolean updateSelectedChannels(String tivoName) {
      try {
         // Reset "isSelected" entries for premiere_channel_info for this TiVo
         if (channel_info.containsKey(tivoName)) {
            for (int i=0; i<channel_info.get(tivoName).length(); ++i) {
               channel_info.get(tivoName).getJSONObject(i).put("isSelected", "false");
            }
   
            // Set "isSelected" to true for selected ones
            ObservableList<Integer> sel = channels.getSelectionModel().getSelectedIndices();
            if (sel.size() < 1) {
               log.error("No channels selected in channel list for processing.");
               return false;
            }
            for (Integer row : sel) {
               channel_info.get(tivoName).getJSONObject(row).put("isSelected", "true");
            }
         } else {
            log.error("No channel information available - use Channels button to get list of channels");
            return false;
         }
      } catch (JSONException e1) {
         log.error("channelInfoToArray - " + e1.getMessage());
         return false;
      }
      return true;
   }
   
   // Return channel information for selected entries in channel list
   // NOTE: This is called from remote "premieres" task
   public JSONArray getSelectedChannelData(String tivoName) {
      JSONArray a = new JSONArray();
      try {
         if (channel_info.containsKey(tivoName)) {
            ObservableList<Integer> sel = channels.getSelectionModel().getSelectedIndices();
            for (Integer row : sel) {
               a.put(channel_info.get(tivoName).getJSONObject(row));
            }
         }
      } catch (JSONException e) {
         log.error("getSelectedChannelData - " + e.getMessage());
      }
      return a;
   }
   
   // Populate channel list for given TiVo in Premieres tab
   // NOTE: This is called from remote "channels" task
   public void putChannelData(String tivoName, JSONArray channelInfo) {
      // 1st save selected channels list in a hash for easy access
      ObservableList<Integer> sel = channels.getSelectionModel().getSelectedIndices();
      Hashtable<String,Boolean> h = new Hashtable<String,Boolean>();
      for (Integer row : sel) {
         String channelNumber = channels.getItems().get(row);
         h.put(channelNumber, true);
      }
      
      // Now reset GUI list and global
      channel_info.put(tivoName, channelInfo);
      channels.getItems().clear();
      try {
         String channelNumber, callSign;
         ObservableList<String> oblist = FXCollections.observableList(new ArrayList<String>());
         for (int i=0; i<channelInfo.length(); ++i) {
            channelNumber = channelInfo.getJSONObject(i).getString("channelNumber");
            callSign = channelInfo.getJSONObject(i).getString("callSign");
            oblist.add(i, channelNumber + "=" + callSign);
         }
         channels.setItems(oblist);
         
         // Re-select channels if available
         for (int k=0; k<channels.getItems().size(); ++k) {
            channelNumber = channels.getItems().get(k);
            JSONObject json = channel_info.get(tivoName).getJSONObject(k);
            if (h.containsKey(channelNumber)) {
               channels.getSelectionModel().select(k);
               json.put("isSelected", "true");
            } else {
               json.put("isSelected", "false");
            }
         }
      } catch (JSONException e) {
         log.error("putChannelData - " + e.getMessage());
      }
   }  
   
   // Write channel info to a file
   // Columns are:
   // channelNumber, callSign, sourceType, isSelected
   public void saveChannelInfo(String tivoName) {
       try {
         JSONObject json;
         if (channel_info.containsKey(tivoName)) {
            String fileName = config.programDir + File.separator + tivoName + ".channels";
            log.warn("Saving channel info to file: " + fileName);
            BufferedWriter ofp = new BufferedWriter(new FileWriter(fileName));
            String eol = "\r\n";
            ofp.write("#channelNumber, callSign, sourceType, isSelected" + eol);
            for (int i=0; i<channel_info.get(tivoName).length(); ++i) {
               json = channel_info.get(tivoName).getJSONObject(i);
               ofp.write(json.getString("channelNumber"));
               ofp.write(", " + json.getString("callSign"));
               ofp.write(", " + json.getString("sourceType"));
               ofp.write(", " + json.getString("isSelected") + eol);
            } // for
            ofp.close();
         }
      } catch (Exception e1) {
         log.error("saveChannelInfo - " + e1.getMessage());
      }
   }
   
   // NOTE: This called as part of a background job
   public void TagPremieresWithSeasonPasses(JSONArray data) {
      log.warn("Collecting information on existing Season Passes...");
      for (String tivoName : util.getFilteredTivoNames()) {
         Remote r = config.initRemote(tivoName);
         if (r.success) {
            JSONArray existing = r.SeasonPasses(null);
            if (existing != null) {
               // Add special json entry to mark entries that already have season passes
               String sp_title, entry_title;
               try {
                  for (int i=0; i<existing.length(); ++i) {
                     sp_title = existing.getJSONObject(i).getString("title");
                     for (int j=0; j<data.length(); ++j) {
                        entry_title = data.getJSONObject(j).getString("title");
                        if (sp_title.equals(entry_title)) {
                           // Add flag to JSON object indicating it's already a scheduled SP on this TiVo
                           if (data.getJSONObject(j).has("__SPscheduled__")) {
                              data.getJSONObject(j).put("__SPscheduled__",
                                 data.getJSONObject(j).getString("__SPscheduled__") +
                                 ", " + tivoName
                              );
                           } else {
                              data.getJSONObject(j).put("__SPscheduled__", tivoName);
                           }
                        }
                     }
                  }
               } catch (JSONException e1) {
                  log.error("RC keyPressed - " + e1.getMessage());
               }
            }
            r.disconnect();
         }
      }
   }
}