/*
 * Copyright 2008-Present Kevin Moye <moyekj@yahoo.com>.
 *
 * This file is part of kmttg package.
 *
 * kmttg is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this project.  If not, see <http://www.gnu.org/licenses/>.
 */
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
