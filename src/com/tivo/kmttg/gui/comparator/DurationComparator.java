package com.tivo.kmttg.gui.comparator;

import java.util.Comparator;

import com.tivo.kmttg.gui.sortable.sortableDuration;

public class DurationComparator implements Comparator<sortableDuration> {
   public int compare(sortableDuration o1, sortableDuration o2) {
      if (o1.sortable > o2.sortable) return 1;
      if (o1.sortable < o2.sortable) return -1;
      return 0;
   }
}
