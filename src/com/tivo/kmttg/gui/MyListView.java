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

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.DefaultListModel;
import javax.swing.JList;

// Extend JList to add rudimentary keyboard keyword matching support
public class MyListView extends JList<String> {
   private static final long serialVersionUID = 1L;
   private StringBuilder sb = new StringBuilder();
   private DefaultListModel<String> items = new DefaultListModel<String>();

   public MyListView() {
      super();
      setModel(items);
      addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(KeyEvent key) {
            handleChannelKey(key);
         }
      });
      addFocusListener(new FocusAdapter() {
         @Override
         public void focusGained(FocusEvent e) {
            ensureIndexIsVisible(getSelectedIndex());
         }
         @Override
         public void focusLost(FocusEvent e) {
            sb.delete(0, sb.length());
         }
      });
   }

   // Backing list model (was JavaFX ObservableList)
   public DefaultListModel<String> getItems() {
      return items;
   }

   // Handle keyboard pattern matching for channels ListView
   private void handleChannelKey(KeyEvent event) {
      int code = event.getKeyCode();
      if (code == KeyEvent.VK_DOWN || code == KeyEvent.VK_UP || code == KeyEvent.VK_TAB) {
          return;
      }
      event.consume();
      if (code == KeyEvent.VK_BACK_SPACE && sb.length() > 0) {
          sb.deleteCharAt(sb.length()-1);
      }
      else {
          char c = event.getKeyChar();
          if (c != KeyEvent.CHAR_UNDEFINED && !Character.isISOControl(c))
             sb.append(c);
      }

      if (sb.length() == 0)
          return;

      boolean found = false;
      for (int i=0; i<items.size(); i++) {
          if (code != KeyEvent.VK_BACK_SPACE && items.get(i).toString().toLowerCase().startsWith(sb.toString().toLowerCase())) {
              setSelectedIndex(i);
              ensureIndexIsVisible(i);
              found = true;
              break;
          }
      }

      if (!found && sb.length() > 0)
          sb.deleteCharAt(sb.length() - 1);
  }

}
