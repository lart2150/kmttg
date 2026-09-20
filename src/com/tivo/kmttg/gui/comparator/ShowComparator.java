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

import com.tivo.kmttg.gui.sortable.sortableShow;
import com.tivo.kmttg.main.config;

public class ShowComparator implements Comparator<sortableShow> {
   public int compare(sortableShow s1, sortableShow s2) {
      if (s1 != null && s2 != null) {
         int e1 = episodeNum(s1);
         int e2 = episodeNum(s2);
         // Sort 1st by titleOnly, then by episode, then by date
         int result;
         if (config.TiVoSort == 1)         
            result = s1.TiVoTitle.compareToIgnoreCase(s2.TiVoTitle);
         else
            result = s1.titleOnly.compareToIgnoreCase(s2.titleOnly);
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

   // Each show is read on its own. Reading the pair together, and leaving both at -1
   // unless both carried a number, made a numbered and an unnumbered episode fall
   // through to date - an order that is not transitive, which Collections.sort rejects
   // outright once a list is big enough for it to notice.
   private static int episodeNum(sortableShow s) {
      if (s.episodeNum.length() == 0)
         return -1;
      return Integer.parseInt(s.episodeNum);
   }
}