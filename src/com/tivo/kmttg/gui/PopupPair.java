package com.tivo.kmttg.gui;

import javax.swing.JMenuItem;

public class PopupPair {
   public String name;
   public int key;
   public String tableName;
   public JMenuItem menuitem = null;
   
   PopupPair(String name, int key, String tableName) {
      this.name = name;
      this.key = key;
      this.tableName = tableName;
   }
   
   PopupPair(String name, JMenuItem menuitem, String tableName) {
      this.name = name;
      this.menuitem = menuitem;
      this.tableName = tableName;
   }
}
