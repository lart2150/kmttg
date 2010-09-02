package com.tivo.kmttg.gui;

import javax.swing.JComboBox;

public class combobox {
   public static void Add(JComboBox box, String value) {
      box.addItem(value);
   }   
   
   public static void SetValues(JComboBox box, String[] values) {
      box.removeAllItems();
      for (int i=0; i<values.length; i++) {
         box.addItem(values[i]);
      }
      box.revalidate();
   }
}
