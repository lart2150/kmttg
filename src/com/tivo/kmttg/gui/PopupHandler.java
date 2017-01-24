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
package com.tivo.kmttg.gui;

import java.util.Stack;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeTableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.SkipManager;

public class PopupHandler {
   static TableView<?> TABLE_view = null;
   static TreeTableView<?> TABLE_treeview = null;
   static ContextMenu popup = null;
   
   public static void hide() {
      if (popup != null)
         popup.hide();
   }
   
   public static void display(TableView<?> TABLE, MouseEvent e) {
      TABLE_view = TABLE;
      TABLE_treeview = null;
      popup = display(e);
      if (popup != null)
         popup.show(TABLE, e.getScreenX(), e.getScreenY());      
   }
   
   public static void display(final TreeTableView<?> TABLE, MouseEvent e) {
      TABLE_treeview = TABLE;
      TABLE_view = null;
      popup = display(e);
      if (popup != null)
         popup.show(TABLE, e.getScreenX(), e.getScreenY());      
   }
   
   private static ContextMenu display(MouseEvent e) {
      String tabName = config.gui.getCurrentTabName();
      String tivoName;
      if (tabName.equals("FILES"))
         return null;
      popup = new ContextMenu();
      Stack<PopupPair> items = new Stack<PopupPair>();
      if (tabName.equals("Remote")) {
         // This is a Remote table
         String subTabName = config.gui.remote_gui.getCurrentTabName();
         tivoName = config.gui.remote_gui.getTivoName(subTabName);
         if (config.rpcEnabled(tivoName) && subTabName.equals("ToDo")) {            
            items.add(new PopupPair("Cancel [c]", KeyCode.C, subTabName));
            items.add(new PopupPair("Modify [m]", KeyCode.M, subTabName));
            items.add(new PopupPair(
               "Add to auto transfers", config.gui.addSelectedTitlesMenuItem, subTabName)
            );
            items.add(new PopupPair("Add to history file [a]", KeyCode.A, subTabName));
         }
         if (subTabName.equals("Won't Record")) {
            if (config.rpcEnabled(tivoName))
               items.add(new PopupPair("Record [r]", KeyCode.R, subTabName));
            items.add(new PopupPair("Explain [e]", KeyCode.E, subTabName));
            items.add(new PopupPair("Tree state toggle [t]", KeyCode.T, subTabName));
            items.add(new PopupPair(
               "Add to auto transfers", config.gui.addSelectedTitlesMenuItem, subTabName)
            );
         }
         if (subTabName.equals("Streaming")) {
            items.add(new PopupPair("Tree state toggle [t]", KeyCode.T, subTabName));
         }
         if (subTabName.equals("Season Premieres") || subTabName.equals("Search") || subTabName.equals("Guide")) {            
            if (config.rpcEnabled(tivoName)) {
               items.add(new PopupPair("Record [r]", KeyCode.R, subTabName));
               items.add(new PopupPair("Season Pass [p]", KeyCode.P, subTabName));
               items.add(new PopupPair("Wishlist [w]", KeyCode.W, subTabName));
               if (subTabName.equals("Search"))
                  items.add(new PopupPair("Tree state toggle [t]", KeyCode.T, subTabName));
            }
            items.add(new PopupPair(
                  "Add to auto transfers", config.gui.addSelectedTitlesMenuItem, subTabName)
            );
            items.add(new PopupPair("Add to history file [a]", KeyCode.A, subTabName));
         }
         if (subTabName.equals("Season Passes")) {
            items.add(new PopupPair("Change Priority [p]", KeyCode.P, subTabName));
            items.add(new PopupPair("Delete [delete]", KeyCode.DELETE, subTabName));
            if (config.rpcEnabled(tivoName))
               items.add(new PopupPair("Copy [c]", KeyCode.C, subTabName));
            items.add(new PopupPair("Modify [m]", KeyCode.M, subTabName));
            items.add(new PopupPair("Upcoming [u]", KeyCode.U, subTabName));
            items.add(new PopupPair("Conflicts [o]", KeyCode.O, subTabName));
            items.add(new PopupPair("Show Information [i]", KeyCode.I, subTabName));
            items.add(new PopupPair("Check OnePasses [z]", KeyCode.Z, subTabName));
         }
         if (subTabName.equals("Thumbs") || subTabName.equals("Channels")) {
            if (config.rpcEnabled(tivoName))
               items.add(new PopupPair("Copy [c]", KeyCode.C, subTabName));
         }
         if (config.rpcEnabled(tivoName) && subTabName.equals("Deleted")) {            
            items.add(new PopupPair("Recover [r]", KeyCode.R, subTabName));
            items.add(new PopupPair("Permanently Delete [delete]", KeyCode.DELETE, subTabName));
            items.add(new PopupPair(
               "Add to auto transfers", config.gui.addSelectedTitlesMenuItem, subTabName)
            );
         }
         if (config.rpcEnabled(tivoName) && !subTabName.equals("Season Passes") && !subTabName.equals("Thumbs")
               && ! subTabName.equals("Channels"))
            items.add(new PopupPair("Show Information [i]", KeyCode.I, subTabName));
         
         // General items for all tables
         items.add(new PopupPair("Display data [j]", KeyCode.J, subTabName));
         items.add(new PopupPair("Web query [q]", KeyCode.Q, subTabName));
         if (! subTabName.equals("Streaming")) {
            items.add(new PopupPair("Change thumbs rating [ctrl-t]", config.gui.thumbsMenuItem, subTabName));
            if (config.rpcEnabled(tivoName))
               items.add(new PopupPair("Episode Info [n]", KeyCode.N, subTabName));
         }
         items.add(new PopupPair("Search table [ctrl-s]", config.gui.searchMenuItem, subTabName));
      } else {
         // This is a NPL table
         tivoName = tabName;
         if (!config.rpcEnabled(tivoName) && !config.mindEnabled(tivoName))
            items.add(new PopupPair("Get extended metadata [m]", KeyCode.M, tivoName));
         if (config.rpcEnabled(tivoName)) {
            items.add(new PopupPair("Play [p]", KeyCode.P, tivoName));
            items.add(new PopupPair("Show Information [i]", KeyCode.I, tivoName));
         }
         if (config.rpcEnabled(tivoName) || config.twpDeleteEnabled())
            items.add(new PopupPair("Delete [delete]", KeyCode.DELETE, tivoName));
         items.add(new PopupPair("Display data [j]", KeyCode.J, tivoName));
         if (config.rpcEnabled(tivoName) || config.mindEnabled(tivoName))
            items.add(new PopupPair("Episode Info [n]", KeyCode.N, tivoName));
            items.add(new PopupPair("Display RPC data [r]", KeyCode.R, tivoName));
         if (SkipManager.skipEnabled() && config.rpcEnabled(tivoName)) {
            items.add(new PopupPair("Import AutoSkip cuts [c]", KeyCode.C, tivoName));
            items.add(new PopupPair("Export AutoSkip cuts [e]", KeyCode.E, tivoName));
            items.add(new PopupPair("AutoSkip from SkipMode [v]", KeyCode.V, tivoName));
            items.add(new PopupPair("AutoSkip from SkipMode - ALL [w]", KeyCode.W, tivoName));
            items.add(new PopupPair("Play in AutoSkip mode [z]", KeyCode.Z, tivoName));
         }
         if (config.rpcEnabled(tivoName)) {
            // Intentionally hidden for now
            //items.add(new PopupPair("Display SKIP data [k]", KeyCode.K, tivoName));
         }
         items.add(new PopupPair("Web query [q]", KeyCode.Q, tivoName));
         items.add(new PopupPair("Tree state toggle [t]", KeyCode.T, tivoName));
         items.add(new PopupPair("Add to auto transfers", config.gui.addSelectedTitlesMenuItem, tivoName));
         items.add(new PopupPair("Add to history file", config.gui.addSelectedHistoryMenuItem, tivoName));
         items.add(new PopupPair("Search table [ctrl-s]", config.gui.searchMenuItem, tivoName));
      }
      
      for (int i=0; i<items.size(); ++i) {
         final KeyCode key = items.get(i).key;
         MenuItem item = new MenuItem(items.get(i).name);
         //final String tableName = items.get(i).tableName;
         final MenuItem menuitem = items.get(i).menuitem;
         if (menuitem == null) {
            // Action bound to key event
            item.setOnAction(new EventHandler<ActionEvent>() {
               public void handle(ActionEvent e) {
                  if (TABLE_view != null) {
                     KeyEvent keyEvent = new KeyEvent(
                        TABLE_view, TABLE_view, KeyEvent.KEY_PRESSED, key.getName(),
                        key.getName(), key, false, false, false, false
                     );
                     TABLE_view.fireEvent(keyEvent);
                  }
                  if (TABLE_treeview != null) {
                     KeyEvent keyEvent = new KeyEvent(
                        TABLE_treeview, TABLE_treeview, KeyEvent.KEY_PRESSED, key.getName(),
                        key.getName(), key, false, false, false, false
                     );
                     TABLE_treeview.fireEvent(keyEvent);
                  }
               }
            });
         } else {
            // Action bound to menu item
            item.setOnAction(new EventHandler<ActionEvent>() {
               public void handle(ActionEvent e) {
                  menuitem.fire();
               }
            });
         }
         popup.getItems().add(item);
      }
      return popup;
   }
}
