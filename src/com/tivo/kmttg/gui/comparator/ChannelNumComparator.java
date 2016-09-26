package com.tivo.kmttg.gui.comparator;

import java.util.Comparator;

import com.tivo.kmttg.gui.sortable.sortableChannelNum;

public class ChannelNumComparator implements Comparator<sortableChannelNum> {
   public int compare(sortableChannelNum o1, sortableChannelNum o2) {
      if (o1 != null && o2 != null) {
         if (o1.sortable > o2.sortable) return 1;
         if (o1.sortable < o2.sortable) return -1;
      }
      return 0;
   }
}
