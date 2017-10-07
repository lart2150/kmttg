package com.tivo.kmttg.gui.comparator;

import java.util.Comparator;

import javafx.scene.image.ImageView;

public class ImageViewComparator implements Comparator<ImageView> {
   public int compare(ImageView o1, ImageView o2) {
      if (o1 != null && o2 != null) {
         return o1.getImage().toString().compareToIgnoreCase(o2.getImage().toString());
      }
      return 0;
   }
}
