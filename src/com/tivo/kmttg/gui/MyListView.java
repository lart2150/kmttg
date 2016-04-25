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

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

// Extend ListView to add rudimentary keyboard keyword matching support
public class MyListView extends ListView<String> {
   private StringBuilder sb = new StringBuilder();
   public MyListView() {
      super();
      setOnKeyPressed(new EventHandler<KeyEvent>() {
         public void handle(KeyEvent key) {
            handleChannelKey(key);
         }
      });
      focusedProperty().addListener(new ChangeListener<Boolean>() {
         @Override
         public void changed(ObservableValue<? extends Boolean> observable, Boolean oldVal, Boolean newVal) {
            if (newVal) {
               scrollTo(getSelectionModel().getSelectedIndex());
            } else {
               sb.delete(0, sb.length());
            }
         }
      });
   }
   
   
   // Handle keyboard pattern matching for channels ListView
   private void handleChannelKey(KeyEvent event) {
      event.consume();
      if (event.getCode() == KeyCode.DOWN || event.getCode() == KeyCode.UP || event.getCode() == KeyCode.TAB) {
          return;
      }
      else if (event.getCode() == KeyCode.BACK_SPACE && sb.length() > 0) {
          sb.deleteCharAt(sb.length()-1);
      }
     else {
          sb.append(event.getText());
      }

      if (sb.length() == 0) 
          return;
      
      boolean found = false;
      ObservableList<String> items = getItems();
      for (int i=0; i<items.size(); i++) {
          if (event.getCode() != KeyCode.BACK_SPACE && items.get(i).toString().toLowerCase().startsWith(sb.toString().toLowerCase())) {
              getSelectionModel().clearAndSelect(i);           
              scrollTo(getSelectionModel().getSelectedIndex());
              found = true;
              break;
          }
      }
      
      if (!found && sb.length() > 0)
          sb.deleteCharAt(sb.length() - 1);
  }

}
