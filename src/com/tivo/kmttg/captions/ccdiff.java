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
package com.tivo.kmttg.captions;

public class ccdiff {
   long start1;
   long start2;
   long stop1;
   long stop2;
   String text;
   int index1;
   int index2;
   
   public long startDiff() {
      return start2 - start1;
   }
   
   public long stopDiff() {
      return stop2 - stop1;
   }
   
   public String toString() {
      String string = "";
      long start_diff = start2 - start1;
      long stop_diff = stop2 - stop1;
      string = "index1=" + index1 + " start1=" + util.toHourMinSec(start1);
      string += " index2=" + index2 + " start2=" + util.toHourMinSec(start2);
      string += " diff=" + start_diff + "\n";
      string += "stop1=" + util.toHourMinSec(stop1);
      string += " stop2=" + util.toHourMinSec(stop2);
      string += " diff=" + stop_diff + "\n";
      string += text;
      return string;
   }
}
