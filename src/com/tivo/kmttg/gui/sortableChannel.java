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
      display = channelNum + "=" + channelName;
      if (display.length() > 0)
         display += " ";
      String chan = channelNum.replaceFirst("-", ".");
      sortable = Float.parseFloat(chan);
   }
   
   public String toString() {
      return display;
   }
}
