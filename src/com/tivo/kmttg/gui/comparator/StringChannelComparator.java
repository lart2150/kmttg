package com.tivo.kmttg.gui.comparator;

import java.util.Comparator;

public class StringChannelComparator implements Comparator<String >{
   public int compare(String o1, String o2) {
      if (o1 != null && o2 != null) {
         float c1=0, c2=0;
         try {
            if (o1.contains("-"))
               o1 = o1.replace("-", ".");
            if (o2.contains("-"))
               o2 = o2.replace("-", ".");
            if (o1.contains("="))
               c1 = Float.parseFloat(o1.split("=")[0]);
            if (o2.contains("="))
               c2 = Float.parseFloat(o2.split("=")[0]);
            if (c1 > c2)
               return 1;
            if (c1 < c2)
               return -1;
         } catch (Exception e) {}
      }
      return 0;
   }
}
