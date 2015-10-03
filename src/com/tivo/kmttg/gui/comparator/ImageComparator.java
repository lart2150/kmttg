package com.tivo.kmttg.gui.comparator;

import java.util.Comparator;

import com.tivo.kmttg.gui.table.imageCell;

public class ImageComparator implements Comparator<imageCell> {
   public int compare(imageCell o1, imageCell o2) {
      if (o1 != null && o2 != null) {
         return o1.imageName.compareToIgnoreCase(o2.imageName);
      }
      return 0;
   }
}

