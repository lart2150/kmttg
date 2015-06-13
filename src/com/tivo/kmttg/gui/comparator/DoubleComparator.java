package com.tivo.kmttg.gui.comparator;

import java.util.Comparator;

import com.tivo.kmttg.gui.sortable.sortableDouble;

public class DoubleComparator implements Comparator<sortableDouble> {
   public int compare(sortableDouble o1, sortableDouble o2) {
      if (o1.sortable > o2.sortable) return 1;
      if (o1.sortable < o2.sortable) return -1;
      return 0;
   }
}