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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Stack;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;

import com.tivo.kmttg.gui.swing.TreeTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.SkipManager;

public class PopupHandler {
   static JTable TABLE_target = null;
   static JPopupMenu popup = null;

   public static void hide() {
      if (popup != null)
         popup.setVisible(false);
   }

   public static void display(JTable TABLE, MouseEvent e) {
      TABLE_target = TABLE;
      popup = display(e);
      if (popup != null)
         popup.show(TABLE, e.getX(), e.getY());
   }

   public static void display(TreeTable<?> TABLE, MouseEvent e) {
      TABLE_target = TABLE.table;
      popup = display(e);
      if (popup != null)
         popup.show(TABLE.table, e.getX(), e.getY());
   }

   private static JPopupMenu display(MouseEvent e) {
      String tabName = config.gui.getCurrentTabName();
      String tivoName;
      if (tabName.equals("FILES"))
         return null;
      popup = new JPopupMenu();
      Stack<PopupPair> items = new Stack<PopupPair>();
      if (tabName.equals("Remote")) {
         // This is a Remote table
         String subTabName = config.gui.remote_gui.getCurrentTabName();
         tivoName = config.gui.remote_gui.getTivoName(subTabName);
         if (config.rpcEnabled(tivoName) && subTabName.equals("ToDo")) {
            items.add(new PopupPair("Cancel [c]", KeyEvent.VK_C, subTabName));
            items.add(new PopupPair("Modify [m]", KeyEvent.VK_M, subTabName));
            items.add(new PopupPair(
               "Add to auto transfers", config.gui.addSelectedTitlesMenuItem, subTabName)
            );
            items.add(new PopupPair("Add to history file [a]", KeyEvent.VK_A, subTabName));
         }
         if (subTabName.equals("Won't Record")) {
            if (config.rpcEnabled(tivoName))
               items.add(new PopupPair("Record [r]", KeyEvent.VK_R, subTabName));
            items.add(new PopupPair("Explain [e]", KeyEvent.VK_E, subTabName));
            items.add(new PopupPair("Tree state toggle [t]", KeyEvent.VK_T, subTabName));
            items.add(new PopupPair(
               "Add to auto transfers", config.gui.addSelectedTitlesMenuItem, subTabName)
            );
         }
         if (subTabName.equals("Streaming")) {
            items.add(new PopupPair("Tree state toggle [t]", KeyEvent.VK_T, subTabName));
         }
         if (subTabName.equals("Season Premieres") || subTabName.equals("Search") || subTabName.equals("Guide")) {
            if (config.rpcEnabled(tivoName)) {
               items.add(new PopupPair("Record [r]", KeyEvent.VK_R, subTabName));
               items.add(new PopupPair("Season Pass [p]", KeyEvent.VK_P, subTabName));
               items.add(new PopupPair("Wishlist [w]", KeyEvent.VK_W, subTabName));
               if (subTabName.equals("Search"))
                  items.add(new PopupPair("Tree state toggle [t]", KeyEvent.VK_T, subTabName));
            }
            items.add(new PopupPair(
                  "Add to auto transfers", config.gui.addSelectedTitlesMenuItem, subTabName)
            );
            items.add(new PopupPair("Add to history file [a]", KeyEvent.VK_A, subTabName));
         }
         if (subTabName.equals("Season Passes")) {
            items.add(new PopupPair("Change Priority [p]", KeyEvent.VK_P, subTabName));
            items.add(new PopupPair("Delete [delete]", KeyEvent.VK_DELETE, subTabName));
            if (config.rpcEnabled(tivoName))
               items.add(new PopupPair("Copy [c]", KeyEvent.VK_C, subTabName));
            items.add(new PopupPair("Modify [m]", KeyEvent.VK_M, subTabName));
            items.add(new PopupPair("Upcoming [u]", KeyEvent.VK_U, subTabName));
            items.add(new PopupPair("Conflicts [o]", KeyEvent.VK_O, subTabName));
            items.add(new PopupPair("Show Information [i]", KeyEvent.VK_I, subTabName));
            items.add(new PopupPair("Check OnePasses [z]", KeyEvent.VK_Z, subTabName));
         }
         if (subTabName.equals("Thumbs") || subTabName.equals("Channels")) {
            if (config.rpcEnabled(tivoName))
               items.add(new PopupPair("Copy [c]", KeyEvent.VK_C, subTabName));
         }
         if (config.rpcEnabled(tivoName) && subTabName.equals("Deleted")) {
            items.add(new PopupPair("Recover [r]", KeyEvent.VK_R, subTabName));
            items.add(new PopupPair("Permanently Delete [delete]", KeyEvent.VK_DELETE, subTabName));
            items.add(new PopupPair(
               "Add to auto transfers", config.gui.addSelectedTitlesMenuItem, subTabName)
            );
         }
         if (config.rpcEnabled(tivoName) && !subTabName.equals("Season Passes") && !subTabName.equals("Thumbs")
               && ! subTabName.equals("Channels"))
            items.add(new PopupPair("Show Information [i]", KeyEvent.VK_I, subTabName));

         // General items for all tables
         items.add(new PopupPair("Display data [j]", KeyEvent.VK_J, subTabName));
         items.add(new PopupPair("Web query [q]", KeyEvent.VK_Q, subTabName));
         if (! subTabName.equals("Streaming")) {
            items.add(new PopupPair("Change thumbs rating [ctrl-t]", config.gui.thumbsMenuItem, subTabName));
            if (config.rpcEnabled(tivoName))
               items.add(new PopupPair("Episode Info [n]", KeyEvent.VK_N, subTabName));
         }
         items.add(new PopupPair("Search table [ctrl-s]", config.gui.searchMenuItem, subTabName));
      } else {
         // This is a NPL table
         tivoName = tabName;
         if (!config.rpcEnabled(tivoName) && !config.mindEnabled(tivoName))
            items.add(new PopupPair("Get extended metadata [m]", KeyEvent.VK_M, tivoName));
         if (config.rpcEnabled(tivoName)) {
            items.add(new PopupPair("Play [p]", KeyEvent.VK_P, tivoName));
            items.add(new PopupPair("Show Information [i]", KeyEvent.VK_I, tivoName));
         }
         if (config.rpcEnabled(tivoName) || config.twpDeleteEnabled())
            items.add(new PopupPair("Delete [delete]", KeyEvent.VK_DELETE, tivoName));
         items.add(new PopupPair("Display data [j]", KeyEvent.VK_J, tivoName));
         if (config.rpcEnabled(tivoName) || config.mindEnabled(tivoName))
            items.add(new PopupPair("Episode Info [n]", KeyEvent.VK_N, tivoName));
            items.add(new PopupPair("Display RPC data [r]", KeyEvent.VK_R, tivoName));
         if (SkipManager.skipEnabled() && config.rpcEnabled(tivoName)) {
            items.add(new PopupPair("Import AutoSkip cuts [c]", KeyEvent.VK_C, tivoName));
            items.add(new PopupPair("Export AutoSkip cuts [e]", KeyEvent.VK_E, tivoName));
            items.add(new PopupPair("AutoSkip from SkipMode [v]", KeyEvent.VK_V, tivoName));
            items.add(new PopupPair("AutoSkip from SkipMode - ALL [w]", KeyEvent.VK_W, tivoName));
            items.add(new PopupPair("Play in AutoSkip mode [z]", KeyEvent.VK_Z, tivoName));
         }
         items.add(new PopupPair("Web query [q]", KeyEvent.VK_Q, tivoName));
         items.add(new PopupPair("Tree state toggle [t]", KeyEvent.VK_T, tivoName));
         items.add(new PopupPair("Add to auto transfers", config.gui.addSelectedTitlesMenuItem, tivoName));
         items.add(new PopupPair("Add to history file", config.gui.addSelectedHistoryMenuItem, tivoName));
         items.add(new PopupPair("Search table [ctrl-s]", config.gui.searchMenuItem, tivoName));
      }

      for (int i=0; i<items.size(); ++i) {
         final int key = items.get(i).key;
         JMenuItem item = new JMenuItem(items.get(i).name);
         final JMenuItem menuitem = items.get(i).menuitem;
         if (menuitem == null) {
            // Action bound to key event - dispatch synthetic key press to
            // table so its KeyListener performs the action
            item.addActionListener(new ActionListener() {
               public void actionPerformed(ActionEvent e) {
                  if (TABLE_target != null) {
                     KeyEvent keyEvent = new KeyEvent(
                        TABLE_target, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                        0, key, KeyEvent.CHAR_UNDEFINED
                     );
                     TABLE_target.dispatchEvent(keyEvent);
                  }
               }
            });
         } else {
            // Action bound to menu item
            item.addActionListener(new ActionListener() {
               public void actionPerformed(ActionEvent e) {
                  menuitem.doClick();
               }
            });
         }
         popup.add(item);
      }
      return popup;
   }
}
