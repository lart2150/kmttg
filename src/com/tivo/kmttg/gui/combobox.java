package com.tivo.kmttg.gui;

import javafx.scene.control.ComboBox;

public class combobox {
   public static void Add(ComboBox<String> box, String value) {
      box.getItems().add(value);
   }   
   
   public static void SetValues(ComboBox<String> box, String[] values) {
      box.getItems().clear();
      for (int i=0; i<values.length; i++) {
         box.getItems().add(values[i]);
      }
   }
}
