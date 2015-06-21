package com.tivo.kmttg.gui.comparator;

import java.util.Comparator;

import com.tivo.kmttg.gui.sortable.sortableShow;

public class ShowComparator implements Comparator<sortableShow> {
   public int compare(sortableShow s1, sortableShow s2) {
      if (s1 != null && s2 != null) {
         int e1=-1, e2=-1;
         if (s1.episodeNum.length() > 0 && s2.episodeNum.length() > 0) {
            e1 = Integer.parseInt(s1.episodeNum);
            e2 = Integer.parseInt(s2.episodeNum);
         }
         // Sort 1st by titleOnly, then by episode, then by date
         int result = s1.titleOnly.compareToIgnoreCase(s2.titleOnly);
         if (result == 0) {
            if (e1 > e2) result = 1;
            if (e1 < e2) result = -1;
         }
         if (result == 0) {
            if (s1.gmt > s2.gmt) result = 1;
            if (s1.gmt < s2.gmt) result = -1;
         }
         return result;
      }
      return 0;
   }
}