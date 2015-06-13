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
