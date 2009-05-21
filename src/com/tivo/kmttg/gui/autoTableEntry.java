package com.tivo.kmttg.gui;

import com.tivo.kmttg.main.autoEntry;

public class autoTableEntry {
   String display;
   autoEntry entry;
   
   autoTableEntry(autoEntry entry) {
      display = entry.type;
      this.entry = entry;
   }
   
   public String toString() {
      return display;
   }

}
