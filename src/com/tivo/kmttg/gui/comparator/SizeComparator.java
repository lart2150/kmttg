package com.tivo.kmttg.gui.comparator;

import java.util.Comparator;

import com.tivo.kmttg.gui.sortable.sortableSize;

public class SizeComparator implements Comparator<sortableSize> {
   public int compare(sortableSize s1, sortableSize s2) {
      if (s1 != null && s2 != null) {
         if (s1.sortable > s2.sortable) return 1;
         if (s1.sortable < s2.sortable) return -1;
      }
      return 0;
   }
}