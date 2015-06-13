package com.tivo.kmttg.gui;

import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;

public class PopupPair {
   public String name;
   public KeyCode key;
   public String tableName;
   public MenuItem menuitem = null;
   
   PopupPair(String name, KeyCode key, String tableName) {
      this.name = name;
      this.key = key;
      this.tableName = tableName;
   }
   
   PopupPair(String name, MenuItem menuitem, String tableName) {
      this.name = name;
      this.menuitem = menuitem;
      this.tableName = tableName;
   }
}
