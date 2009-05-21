package com.tivo.kmttg.gui;

import com.tivo.kmttg.main.jobData;

public class jobEntry {
   String display;
   jobData job;
   
   jobEntry(jobData entry) {
      display = entry.type;
      job = entry;
   }
   
   public String toString() {
      return display;
   }

}
