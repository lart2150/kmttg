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

import com.tivo.kmttg.gui.sortable.sortableChannel;

public class ChannelComparator implements Comparator<sortableChannel> {
   public int compare(sortableChannel o1, sortableChannel o2) {
      if (o1 != null && o2 != null) {
         if (o1.sortable > o2.sortable) return 1;
         if (o1.sortable < o2.sortable) return -1;
      }
      return 0;
   }
}
