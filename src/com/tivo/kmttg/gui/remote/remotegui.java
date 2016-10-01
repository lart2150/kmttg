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
package com.tivo.kmttg.gui.remote;

import java.io.File;
import java.util.Arrays;
import java.util.Hashtable;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyEvent;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.TableMap;
import com.tivo.kmttg.gui.remote.cancelled;
import com.tivo.kmttg.gui.remote.deleted;
import com.tivo.kmttg.gui.remote.guide;
import com.tivo.kmttg.gui.remote.info;
import com.tivo.kmttg.gui.remote.premiere;
import com.tivo.kmttg.gui.remote.remotecontrol;
import com.tivo.kmttg.gui.remote.search;
import com.tivo.kmttg.gui.remote.seasonpasses;
import com.tivo.kmttg.gui.remote.stream;
import com.tivo.kmttg.gui.remote.thumbs;
import com.tivo.kmttg.gui.remote.todo;
import com.tivo.kmttg.gui.remote.util;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.log;

public class remotegui {
   public TabPane tabbed_panel = null;
   
   public todo todo_tab = null;
   public guide guide_tab = null;
   public stream stream_tab = null;
   public seasonpasses sp_tab = null;
   public cancelled cancel_tab = null;
   public deleted deleted_tab = null;
   public thumbs thumbs_tab = null;
   public channels channels_tab = null;
   public premiere premiere_tab = null;
   public info info_tab = null;
   public search search_tab = null;
   public remotecontrol rc_tab = null;
   
   public FileChooser Browser = null;

   public remotegui(final Stage frame) {
      Browser = new FileChooser();
      Browser.setInitialDirectory(new File(config.programDir));
      Browser.setTitle("Choose File");
      
      tabbed_panel = new TabPane();
      tabbed_panel.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
      tabbed_panel.getSelectionModel().selectedItemProperty().addListener( new ChangeListener<Tab>() {
         @Override
         public void changed(ObservableValue<? extends Tab> ov, Tab oldTab, Tab newTab) {
            String selected = newTab.getText();
            if (selected.equals("Search")) {
               // Set focus on text_search field
               Platform.runLater(new Runnable() {
                  @Override
                  public void run() {
                     search_tab.text.requestFocus();
                  }
               });
            }
            if (selected.equals("Guide")) {
               // Reset date range in Guide start time combo box
               guide_tab.tab.setChoiceBoxDates(guide_tab.start, guide_tab.hour_increment, guide_tab.total_range);
               
               // Populate channels if empty
               if (guide_tab.ChanList.getItems().isEmpty()) {
                  if (! guide_tab.tivo.getItems().isEmpty()) {
                     guide_tab.refresh.fire();
                  }
               }
            }
            if (selected.equals("Remote")) {
               // Set focus on tabbed_panel
               Platform.runLater(new Runnable() {
                  @Override
                  public void run() {
                     tabbed_panel.requestFocus();
                  }
               });
            }
         }
      });
      
      // Tabbed panel key presses
      tabbed_panel.setOnKeyPressed(new EventHandler<KeyEvent> () {
         @Override
         public void handle(KeyEvent event) {
            // Prevent Alt press from triggering menu mnemonic
            if (event.isAltDown())
               event.consume();
            if (event.isControlDown())
               return;
            if (! event.isAltDown()) {
               for (PanelKey p : PanelKey.panelKeys) {
                  if (p.key == event.getCode()) {
                     if (p.actionName.startsWith("Shift") && ! event.isShiftDown())
                        continue;
                     if (! p.actionName.startsWith("Shift") && event.isShiftDown())
                        continue;
                     rc_tab.RC_keyPress(p.isAscii, p.command);
                     event.consume();
                     return;
                  }
               }
            }
            for (PanelKey p : PanelKey.buttonKeys) {
               if (event.isAltDown()) {
                  if (p.actionName.startsWith("Alt")) {
                     String code = event.getCode().toString();
                     if (code.equals("ALT"))
                        return;
                     if (p.key == event.getCode()) {
                        p.button.fire();
                        event.consume();
                        return;
                     }
                  }
                  continue;
               } else {
                  if (p.key == event.getCode()) {
                     if (p.actionName.startsWith("Shift") && ! event.isShiftDown())
                        continue;
                     if (! p.actionName.startsWith("Shift") && event.isShiftDown())
                        continue;
                     p.button.fire();
                     event.consume();
                     return;
                  }
               }
            }
         }
      });
      
      // Build the individual tab contents
      todo_tab = new todo(frame);
      guide_tab = new guide(frame);
      stream_tab = new stream(frame);
      sp_tab = new seasonpasses(frame);
      cancel_tab = new cancelled(frame);
      deleted_tab = new deleted(frame);
      thumbs_tab = new thumbs(frame);
      channels_tab = new channels(frame);
      premiere_tab = new premiere(frame);
      info_tab = new info(frame);
      search_tab = new search(frame);
      rc_tab = new remotecontrol(frame);
            
      // Add all panels to tabbed panel
      addTabPane("ToDo", todo_tab.panel);
      addTabPane("Season Passes", sp_tab.panel);
      addTabPane("Won't Record", cancel_tab.panel);
      addTabPane("Season Premieres", premiere_tab.panel);
      addTabPane("Search", search_tab.panel);
      addTabPane("Guide", guide_tab.panel);
      addTabPane("Streaming", stream_tab.panel);
      addTabPane("Deleted", deleted_tab.panel);
      addTabPane("Channels", channels_tab.panel);
      addTabPane("Thumbs", thumbs_tab.panel);
      addTabPane("Remote", rc_tab.panel);
      addTabPane("Info", info_tab.panel);
      
      // Init the tivo ChoiceBoxes
      setTivoNames();
            
      // Pack table columns
      TableUtil.autoSizeTableViewColumns(todo_tab.tab.TABLE, true);
      TableUtil.autoSizeTableViewColumns(guide_tab.tab.TABLE, true);
      TableUtil.autoSizeTableViewColumns(stream_tab.tab.TABLE, true);
      TableUtil.autoSizeTableViewColumns(sp_tab.tab.TABLE, true);
      TableUtil.autoSizeTableViewColumns(cancel_tab.tab.TABLE, true);
      TableUtil.autoSizeTableViewColumns(deleted_tab.tab.TABLE, true);
      TableUtil.autoSizeTableViewColumns(thumbs_tab.tab.TABLE, true);
      TableUtil.autoSizeTableViewColumns(channels_tab.tab.TABLE, true);
      TableUtil.autoSizeTableViewColumns(search_tab.tab.TABLE, true);
   }
   
   private void addTabPane(String name, Node content) {
      Tab tab = new Tab();
      tab.setContent(content);
      tab.setText(name);
      tabbed_panel.getTabs().add(tab);
   }
      
   public TabPane getPanel() {
      return tabbed_panel;
   }      
      
   public String getCurrentTabName() {
      return tabbed_panel.getSelectionModel().getSelectedItem().getText();
   }
   
   // Return json of currently selected row in currently showing table if any
   public JSONObject getSelectedJSON(String tabName) {
      TableMap tmap = TableMap.get(tabName);
      if (tmap != null) {
         int[] selected = tmap.getSelected();
         if (selected != null && selected.length > 0)
            return tmap.getJson(selected[0]);
      }
      return null;
   }

   public String getGuideStartTime() {
	  String start = guide_tab.start.getValue();
	  if (start == null || start.length() == 0) {
	     guide_tab.tab.setChoiceBoxDates(guide_tab.start, guide_tab.hour_increment, guide_tab.total_range);
		  start = guide_tab.start.getValue();
	  }
      return start;
   }
      
   public int getPremiereDays() {
      return Integer.parseInt(premiere_tab.days.getValue());
   }
   
   public String getTivoName(String tab) {
      if (tab.equals("todo") || tab.equals("ToDo"))
         return todo_tab.tivo.getValue();
      if (tab.equals("guide") || tab.equals("Guide"))
         return guide_tab.tivo.getValue();
      if (tab.equals("stream") || tab.equals("Streaming"))
         return stream_tab.tivo.getValue();
      if (tab.equals("sp") || tab.equals("Season Passes"))
         return sp_tab.tivo.getValue();
      if (tab.equals("cancel") || tab.equals("Won't Record"))
         return cancel_tab.tivo.getValue();
      if (tab.equals("deleted") || tab.equals("Deleted"))
         return deleted_tab.tivo.getValue();
      if (tab.equals("thumbs") || tab.equals("Thumbs"))
         return thumbs_tab.tivo.getValue();
      if (tab.equals("channels") || tab.equals("Channels"))
         return channels_tab.tivo.getValue();
      if (tab.equals("search") || tab.equals("Search"))
         return search_tab.tivo.getValue();
      if (tab.equals("rc") || tab.equals("Remote"))
         return rc_tab.tivo.getValue();
      if (tab.equals("info") || tab.equals("Info"))
         return info_tab.tivo.getValue();
      if (tab.equals("premiere") || tab.equals("Season Premieres"))
         return premiere_tab.tivo.getValue();
      return null;
   }
   
   public void setTivoName(String tab, String tivoName) {
      String current = getTivoName(tab);
      if ( ! tivoName.equals(current)) {
         if (tab.equals("todo"))
            todo_tab.tivo.setValue(tivoName);
         if (tab.equals("guide"))
            guide_tab.tivo.setValue(tivoName);
         if (tab.equals("stream"))
            stream_tab.tivo.setValue(tivoName);
         if (tab.equals("sp"))
            sp_tab.tivo.setValue(tivoName);
         if (tab.equals("cancel"))
            cancel_tab.tivo.setValue(tivoName);
         if (tab.equals("deleted"))
            deleted_tab.tivo.setValue(tivoName);
         if (tab.equals("thumbs"))
            thumbs_tab.tivo.setValue(tivoName);
         if (tab.equals("channels"))
            channels_tab.tivo.setValue(tivoName);
         if (tab.equals("search"))
            search_tab.tivo.setValue(tivoName);
         if (tab.equals("rc"))
            rc_tab.tivo.setValue(tivoName);
        if (tab.equals("info"))
            info_tab.tivo.setValue(tivoName);
         if (tab.equals("premiere"))
            premiere_tab.tivo.setValue(tivoName);
      }
   }
   
   public void clearTable(String tableName) {
      TableMap tmap = TableMap.get(tableName);
      if (tmap != null)
         tmap.clear();
   }
   
   public void setTivoNames() {
      todo_tab.tivo.getItems().clear();
      guide_tab.tivo.getItems().clear();
      stream_tab.tivo.getItems().clear();
      sp_tab.tivo.getItems().clear();
      cancel_tab.tivo.getItems().clear();
      deleted_tab.tivo.getItems().clear();
      thumbs_tab.tivo.getItems().clear();
      channels_tab.tivo.getItems().clear();
      search_tab.tivo.getItems().clear();
      rc_tab.tivo.getItems().clear();
      info_tab.tivo.getItems().clear();
      premiere_tab.tivo.getItems().clear();
      for (String tivoName : config.getTivoNames()) {
         if (config.rpcEnabled(tivoName) || config.mindEnabled(tivoName)) {
            todo_tab.tivo.getItems().add(tivoName);
            guide_tab.tivo.getItems().add(tivoName);
            sp_tab.tivo.getItems().add(tivoName);
            cancel_tab.tivo.getItems().add(tivoName);
            deleted_tab.tivo.getItems().add(tivoName);
            thumbs_tab.tivo.getItems().add(tivoName);            
            channels_tab.tivo.getItems().add(tivoName);            
            search_tab.tivo.getItems().add(tivoName);
            info_tab.tivo.getItems().add(tivoName);
            premiere_tab.tivo.getItems().add(tivoName);
         }
         if (config.rpcEnabled(tivoName)) {
            stream_tab.tivo.getItems().add(tivoName);
         }
         // Remote tab always valid as it can use RPC or telnet
         rc_tab.tivo.getItems().add(tivoName);
      }
      rc_tab.setHmeDestinations(getTivoName("rc"));
      setComboDefVal(todo_tab.tivo);
      setComboDefVal(guide_tab.tivo);
      setComboDefVal(stream_tab.tivo);
      setComboDefVal(sp_tab.tivo);
      setComboDefVal(cancel_tab.tivo);
      setComboDefVal(deleted_tab.tivo);
      setComboDefVal(thumbs_tab.tivo);
      setComboDefVal(channels_tab.tivo);
      setComboDefVal(search_tab.tivo);
      setComboDefVal(rc_tab.tivo);
      setComboDefVal(info_tab.tivo);
      setComboDefVal(premiere_tab.tivo);
   }
   
   private void setComboDefVal(ChoiceBox<String> box) {
      if(box.getItems().size() > 0)
         box.getSelectionModel().select(box.getItems().get(0));      
   }
      
   // See if given JSON entry matches any of the entries in all_todo hashtable
   public void flagIfInTodo(JSONObject entry, Boolean includeOtherTimes) {
      rnpl.flagIfInTodo(entry, includeOtherTimes, util.all_todo);
   }
   
   // Prompt user to create a wishlist
   public Boolean createWishlist(final String tivoName, JSONObject table_json) {
      Hashtable<String,String> hash = new Hashtable<String,String>();
      // Take title from json if there is one
      if (table_json != null && table_json.has("title")) {
         try {
            hash.put("title", table_json.getString("title"));
            hash.put("title_keyword", table_json.getString("title"));
         } catch (JSONException e) {
            log.error("createWishlist error: " + e.getMessage());
            return false;
         }
      }

      // Bring up Create Wishlist dialog
      JSONObject wl = util.wlOpt.promptUser("(" + tivoName + ") " + "Create Wishlist", hash);
      if (wl == null)
         return false;
      if ( ! wl.has("title")) {
         log.error("Wishlist title is required to be specified. Aborting.");
         return false;
      }
      try {
         JSONObject json = new JSONObject();
         if (wl.has("autoRecord")) {
            // Need to prompt for season pass options
            json = util.spOpt.promptUser(
               tivoName, "(" + tivoName + ") " + "Create ARWL - " + wl.getString("title"), null, true
            );
         }
         
         if (json != null) {
            // Merge wl options into json
            for (String key : JSONObject.getNames(wl))
               json.put(key, wl.get(key));
            
            // Run the RPC command in background mode
            //log.print(json.toString());
            log.warn("Creating wishlist: " + wl.getString("title"));
            final JSONObject fjson = json;
            Task<Boolean> task = new Task<Boolean>() {
               @Override public Boolean call() {
                  Remote r = config.initRemote(tivoName);
                  if (r.success) {
                     JSONObject result = r.Command("Wishlist", fjson);
                     if (result != null)
                        log.warn("Wishlist created successfully.");
                     else
                        log.error("Wishlist creation failed.");
                     r.disconnect();
                  }
                  return false;
               }
            };
            new Thread(task).start();
         }
         return true;
      } catch (JSONException e) {
         log.error(e.getMessage());
         log.error(Arrays.toString(e.getStackTrace()));
         return false;
      }
   }
   
   public void updateButtonStates(String tivoName, String tab) {
      Boolean state;
      if (config.rpcEnabled(tivoName))
         state = true;
      else
         state = false;
      if (tab.equals("ToDo")) {
         todo_tab.cancel.setDisable(!state);
         todo_tab.modify.setDisable(!state);
      }
      if (tab.equals("Season Passes")) {
         sp_tab.reorder.setDisable(!state);
         //sp_tab.copy.setDisable(!state);
      }
      if (tab.equals("Won't Record")) {
         cancel_tab.record.setDisable(!state);
      }
      if (tab.equals("Season Premieres")) {
         premiere_tab.wishlist.setDisable(!state);
         premiere_tab.record.setDisable(!state);
         premiere_tab.recordSP.setDisable(!state);
      }
      if (tab.equals("Search")) {
         search_tab.wishlist.setDisable(!state);
         search_tab.record.setDisable(!state);
         search_tab.recordSP.setDisable(!state);
         search_tab.manual_record.setDisable(!state);
      }
      if (tab.equals("Guide")) {
         guide_tab.wishlist.setDisable(!state);
         guide_tab.record.setDisable(!state);
         guide_tab.recordSP.setDisable(!state);
         guide_tab.manual_record.setDisable(!state);
      }
      if (tab.equals("Deleted")) {
         deleted_tab.recover.setDisable(!state);
         deleted_tab.permDelete.setDisable(!state);
      }
      if (tab.equals("Remote")) {
         rc_tab.hme_button.setDisable(!state);
         rc_tab.jumpto_button.setDisable(!state);
         rc_tab.jumpahead_button.setDisable(!state);
         rc_tab.jumpback_button.setDisable(!state);
      }
      if (tab.equals("Thumbs")) {
         thumbs_tab.copy.setDisable(!state);
         thumbs_tab.update.setDisable(!state);
      }
      if (tab.equals("Channels")) {
         channels_tab.copy.setDisable(!state);
         channels_tab.update.setDisable(!state);
      }
      if (tab.equals("Info")) {
         info_tab.reboot.setDisable(!state);
      }
   }
         
   public Boolean AllChannels() {
      return guide_tab.guide_channels.isSelected();
   }

}
