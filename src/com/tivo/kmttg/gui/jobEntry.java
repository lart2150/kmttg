package com.tivo.kmttg.gui;

import com.tivo.kmttg.main.jobData;

public class jobEntry {
   public String display;
   public jobData job;
   
   public jobEntry(jobData entry) {
      display = entry.type;
      job = entry;
   }
   
   public String toString() {
      return display;
   }

}
