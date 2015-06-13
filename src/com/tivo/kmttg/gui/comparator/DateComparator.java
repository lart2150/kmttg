package com.tivo.kmttg.gui.comparator;

import java.util.Comparator;

import com.tivo.kmttg.gui.sortable.sortableDate;

public class DateComparator implements Comparator<sortableDate> {
   public int compare(sortableDate o1, sortableDate o2) {
      long l1 = Long.parseLong(o1.sortable);
      long l2 = Long.parseLong(o2.sortable);
      if (l1 > l2) return 1;
      if (l1 < l2) return -1;
      return 0;
   }
}