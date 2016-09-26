package com.tivo.kmttg.gui.sortable;

public class sortableChannelNum {
   public String display;
   public float sortable;
   
   public sortableChannelNum(String channelNum) {
      display = channelNum;
      String chan = channelNum.replaceFirst("-", ".");
      sortable = Float.parseFloat(chan);
   }
   
   public String toString() {
      return display;
   }
}
