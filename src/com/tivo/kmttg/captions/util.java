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

public class util {
   public static String toHourMinSec(long msecs) {
      int sec = (int) (msecs/1000) % 60;
      int min = (int) ((msecs/(1000*60)) % 60);
      int hour = (int) ((msecs/(1000*60*60)) % 24);
      int msec = (int) msecs - (hour*1000*60*60 + min*1000*60 + sec*1000);
      return String.format("%02d:%02d:%02d.%03d", hour, min, sec, msec);
   }

}
