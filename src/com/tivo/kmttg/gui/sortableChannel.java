package com.tivo.kmttg.gui;

import com.tivo.kmttg.JSON.JSONObject;

public class sortableChannel {
   String display;
   float sortable;
   JSONObject json;
   
   sortableChannel(JSONObject json, String channel) {
      this.json = json;
      display = channel;
      String chan = channel.replaceFirst("-", ".");
      sortable = Float.parseFloat(chan);
   }
   
   sortableChannel(String channelName, String channelNum) {
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
