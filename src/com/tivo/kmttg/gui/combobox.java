package com.tivo.kmttg.gui;

import javafx.scene.control.ChoiceBox;

public class combobox {
   public static void Add(ChoiceBox<String> box, String value) {
      box.getItems().add(value);
   }   
   
   public static void SetValues(ChoiceBox<String> box, String[] values) {
      box.getItems().clear();
      for (int i=0; i<values.length; i++) {
         box.getItems().add(values[i]);
      }
   }
}
