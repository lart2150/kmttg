package com.tivo.kmttg.gui.dialog;

import com.tivo.kmttg.main.autoEntry;

public class autoTableEntry {
   public String display;
   public autoEntry entry;
   
   public autoTableEntry(autoEntry entry) {
      display = entry.type;
      this.entry = entry;
   }
   
   public String toString() {
      return display;
   }

}
