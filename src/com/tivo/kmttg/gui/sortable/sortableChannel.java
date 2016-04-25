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
package com.tivo.kmttg.gui.sortable;

import com.tivo.kmttg.JSON.JSONObject;

public class sortableChannel {
   public String display;
   public float sortable;
   public JSONObject json;
   
   public sortableChannel(JSONObject json, String channel) {
      this.json = json;
      display = channel;
      String chan = channel.replaceFirst("-", ".");
      sortable = Float.parseFloat(chan);
   }
   
   public sortableChannel(String channelName, String channelNum) {
      if (channelName.contains("<various>"))
         display = channelName;
      else {
         if (channelName.length() > 0 && channelNum.length() > 0)
            display = channelNum + "=" + channelName;
         else {
            display = "";
            channelNum = "0";
         }
      }
      if (display.length() > 0)
         display += " ";
      String chan = channelNum.replaceFirst("-", ".");
      sortable = Float.parseFloat(chan);
   }
   
   public String toString() {
      return display;
   }
}
