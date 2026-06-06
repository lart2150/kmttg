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

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.Arrays;
import java.util.Hashtable;

import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

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
import com.tivo.kmttg.gui.swing.SwingUtil;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.log;

public class remotegui {
   public JTabbedPane tabbed_panel = null;

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

   public JFileChooser Browser = null;

   public remotegui(final JFrame frame) {
      Browser = new JFileChooser();
      Browser.setCurrentDirectory(new File(config.programDir));
      Browser.setDialogTitle("Choose File");

      tabbed_panel = new JTabbedPane();
      tabbed_panel.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            String selected = getCurrentTabName();
            if (selected == null)
               return;
            if (selected.equals("Search")) {
               // Set focus on text_search field
               SwingUtil.runLater(new Runnable() {
                  @Override
                  public void run() {
                     search_tab.text.requestFocusInWindow();
                  }
               });
            }
            if (selected.equals("Guide")) {
               // Reset date range in Guide start time combo box
               guide_tab.tab.setChoiceBoxDates(guide_tab.start, guide_tab.hour_increment, guide_tab.total_range);

               // Populate channels if empty
               if (guide_tab.ChanList.getItems().isEmpty()) {
                  if (guide_tab.tivo.getItemCount() > 0) {
                     guide_tab.refresh.doClick();
                  }
               }
            }
            if (selected.equals("Remote")) {
               // Set focus on tabbed_panel
               SwingUtil.runLater(new Runnable() {
                  @Override
                  public void run() {
                     tabbed_panel.requestFocusInWindow();
                  }
               });
            }
         }
      });

      // Tabbed panel key presses
      tabbed_panel.addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(KeyEvent event) {
            // Prevent Alt press from triggering menu mnemonic
            if (event.isAltDown())
               event.consume();
            if (event.isControlDown())
               return;
            if (! event.isAltDown()) {
               for (PanelKey p : PanelKey.panelKeys) {
                  if (p.key == event.getKeyCode()) {
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
                     if (event.getKeyCode() == KeyEvent.VK_ALT)
                        return;
                     if (p.key == event.getKeyCode()) {
                        p.button.doClick();
                        event.consume();
                        return;
                     }
                  }
                  continue;
               } else {
                  if (p.key == event.getKeyCode()) {
                     if (p.actionName.startsWith("Shift") && ! event.isShiftDown())
                        continue;
                     if (! p.actionName.startsWith("Shift") && event.isShiftDown())
                        continue;
                     p.button.doClick();
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

   private void addTabPane(String name, JComponent content) {
      tabbed_panel.addTab(name, content);
   }

   public JTabbedPane getPanel() {
      return tabbed_panel;
   }

   public String getCurrentTabName() {
      int index = tabbed_panel.getSelectedIndex();
      if (index < 0)
         return null;
      return tabbed_panel.getTitleAt(index);
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
	  String start = (String)guide_tab.start.getSelectedItem();
	  if (start == null || start.length() == 0) {
	     guide_tab.tab.setChoiceBoxDates(guide_tab.start, guide_tab.hour_increment, guide_tab.total_range);
		  start = (String)guide_tab.start.getSelectedItem();
	  }
      return start;
   }

   public int getPremiereDays() {
      return Integer.parseInt((String)premiere_tab.days.getSelectedItem());
   }

   public String getTivoName(String tab) {
      if (tab.equals("todo") || tab.equals("ToDo"))
         return (String)todo_tab.tivo.getSelectedItem();
      if (tab.equals("guide") || tab.equals("Guide"))
         return (String)guide_tab.tivo.getSelectedItem();
      if (tab.equals("stream") || tab.equals("Streaming"))
         return (String)stream_tab.tivo.getSelectedItem();
      if (tab.equals("sp") || tab.equals("Season Passes"))
         return (String)sp_tab.tivo.getSelectedItem();
      if (tab.equals("cancel") || tab.equals("Won't Record"))
         return (String)cancel_tab.tivo.getSelectedItem();
      if (tab.equals("deleted") || tab.equals("Deleted"))
         return (String)deleted_tab.tivo.getSelectedItem();
      if (tab.equals("thumbs") || tab.equals("Thumbs"))
         return (String)thumbs_tab.tivo.getSelectedItem();
      if (tab.equals("channels") || tab.equals("Channels"))
         return (String)channels_tab.tivo.getSelectedItem();
      if (tab.equals("search") || tab.equals("Search"))
         return (String)search_tab.tivo.getSelectedItem();
      if (tab.equals("rc") || tab.equals("Remote"))
         return (String)rc_tab.tivo.getSelectedItem();
      if (tab.equals("info") || tab.equals("Info"))
         return (String)info_tab.tivo.getSelectedItem();
      if (tab.equals("premiere") || tab.equals("Season Premieres"))
         return (String)premiere_tab.tivo.getSelectedItem();
      return null;
   }

   public void setTivoName(String tab, String tivoName) {
      String current = getTivoName(tab);
      if ( ! tivoName.equals(current)) {
         if (tab.equals("todo"))
            todo_tab.tivo.setSelectedItem(tivoName);
         if (tab.equals("guide"))
            guide_tab.tivo.setSelectedItem(tivoName);
         if (tab.equals("stream"))
            stream_tab.tivo.setSelectedItem(tivoName);
         if (tab.equals("sp"))
            sp_tab.tivo.setSelectedItem(tivoName);
         if (tab.equals("cancel"))
            cancel_tab.tivo.setSelectedItem(tivoName);
         if (tab.equals("deleted"))
            deleted_tab.tivo.setSelectedItem(tivoName);
         if (tab.equals("thumbs"))
            thumbs_tab.tivo.setSelectedItem(tivoName);
         if (tab.equals("channels"))
            channels_tab.tivo.setSelectedItem(tivoName);
         if (tab.equals("search"))
            search_tab.tivo.setSelectedItem(tivoName);
         if (tab.equals("rc"))
            rc_tab.tivo.setSelectedItem(tivoName);
        if (tab.equals("info"))
            info_tab.tivo.setSelectedItem(tivoName);
         if (tab.equals("premiere"))
            premiere_tab.tivo.setSelectedItem(tivoName);
      }
   }

   public void clearTable(String tableName) {
      TableMap tmap = TableMap.get(tableName);
      if (tmap != null)
         tmap.clear();
   }

   public void setTivoNames() {
      todo_tab.tivo.removeAllItems();
      guide_tab.tivo.removeAllItems();
      stream_tab.tivo.removeAllItems();
      sp_tab.tivo.removeAllItems();
      cancel_tab.tivo.removeAllItems();
      deleted_tab.tivo.removeAllItems();
      thumbs_tab.tivo.removeAllItems();
      channels_tab.tivo.removeAllItems();
      search_tab.tivo.removeAllItems();
      rc_tab.tivo.removeAllItems();
      info_tab.tivo.removeAllItems();
      premiere_tab.tivo.removeAllItems();
      for (String tivoName : config.getTivoNames()) {
         if (config.rpcEnabled(tivoName) || config.mindEnabled(tivoName)) {
            todo_tab.tivo.addItem(tivoName);
            guide_tab.tivo.addItem(tivoName);
            sp_tab.tivo.addItem(tivoName);
            cancel_tab.tivo.addItem(tivoName);
            deleted_tab.tivo.addItem(tivoName);
            thumbs_tab.tivo.addItem(tivoName);
            channels_tab.tivo.addItem(tivoName);
            search_tab.tivo.addItem(tivoName);
            info_tab.tivo.addItem(tivoName);
            premiere_tab.tivo.addItem(tivoName);
         }
         if (config.rpcEnabled(tivoName)) {
            stream_tab.tivo.addItem(tivoName);
         }
         // Remote tab always valid as it can use RPC or telnet
         rc_tab.tivo.addItem(tivoName);
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

   private void setComboDefVal(javax.swing.JComboBox<String> box) {
      if(box.getItemCount() > 0)
         box.setSelectedItem(box.getItemAt(0));
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
            Runnable task = new Runnable() {
               @Override public void run() {
                  Remote r = config.initRemote(tivoName);
                  if (r.success) {
                     JSONObject result = r.Command("Wishlist", fjson);
                     if (result != null)
                        log.warn("Wishlist created successfully.");
                     else
                        log.error("Wishlist creation failed.");
                     r.disconnect();
                  }
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
         todo_tab.cancel.setEnabled(state);
         todo_tab.modify.setEnabled(state);
      }
      if (tab.equals("Season Passes")) {
         sp_tab.reorder.setEnabled(state);
         //sp_tab.copy.setEnabled(state);
      }
      if (tab.equals("Won't Record")) {
         cancel_tab.record.setEnabled(state);
      }
      if (tab.equals("Season Premieres")) {
         premiere_tab.wishlist.setEnabled(state);
         premiere_tab.record.setEnabled(state);
         premiere_tab.recordSP.setEnabled(state);
      }
      if (tab.equals("Search")) {
         search_tab.wishlist.setEnabled(state);
         search_tab.record.setEnabled(state);
         search_tab.recordSP.setEnabled(state);
         search_tab.manual_record.setEnabled(state);
      }
      if (tab.equals("Guide")) {
         guide_tab.wishlist.setEnabled(state);
         guide_tab.record.setEnabled(state);
         guide_tab.recordSP.setEnabled(state);
         guide_tab.manual_record.setEnabled(state);
      }
      if (tab.equals("Deleted")) {
         deleted_tab.recover.setEnabled(state);
         deleted_tab.permDelete.setEnabled(state);
      }
      if (tab.equals("Remote")) {
         rc_tab.hme_button.setEnabled(state);
         rc_tab.jumpto_button.setEnabled(state);
         rc_tab.jumpahead_button.setEnabled(state);
         rc_tab.jumpback_button.setEnabled(state);
      }
      if (tab.equals("Thumbs")) {
         thumbs_tab.copy.setEnabled(state);
         thumbs_tab.update.setEnabled(state);
      }
      if (tab.equals("Channels")) {
         channels_tab.copy.setEnabled(state);
         channels_tab.update.setEnabled(state);
      }
      if (tab.equals("Info")) {
         info_tab.reboot.setEnabled(state);
      }
   }

   public Boolean AllChannels() {
      return guide_tab.guide_channels.isSelected();
   }

}
