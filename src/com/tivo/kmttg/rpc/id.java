package com.tivo.kmttg.rpc;

import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.util.log;

public class id {
   
   public static void printIds(JSONObject json) {
      String p = programId(json);
      String s = seriesId(json);
      if (p != null || s != null)
         log.print("pyTivo metadata id fields:");
      if (p != null)
         log.print("programId : " + p);
      if (s != null)
         log.print("seriesId : " + s);
   }
   
   // Return a Rovi style programId based on json containing collectionId & contentId
   // "collectionId":"tivo:cl.328422972" "contentId": "tivo:ct.328421836" => EP0328422972-0328421836
   public static String programId(JSONObject json) {
      try {
         if (json.has("collectionId") && json.has("contentId")) {
            String collectionId = json.getString("collectionId");
            String contentId = json.getString("contentId");
            String prefix = "EP";
            if (json.has("collectionType") && json.getString("collectionType").equals("movie"))
               prefix = "MV";
            return prefix + prefix(collectionId) + "-" + prefix(contentId);
         }
      } catch (Exception e) {
         log.error("programId - " + e.getMessage());
      }
      return null;
   }
   
   // Return a Rovi style seriesId based on json containing collectionId
   // "collectionId":"tivo:cl.328422972" => SH0328422972
   public static String seriesId(JSONObject json) {
      try {
         if (json.has("collectionId")) {
            String collectionId = json.getString("collectionId");
            String prefix = "SH";
            if (json.has("collectionType") && json.getString("collectionType").equals("movie"))
               prefix = "MV";
            return prefix + prefix(collectionId);
         }
      } catch (Exception e) {
         log.error("seriesId - " + e.getMessage());
      }
      return null;
   }
   
   // tivo:cl.328422972 --> 0328422972
   private static String prefix(String id) {
      String result = id;
      result = result.replaceFirst("[^\\d]+", "");
      return String.format("%010d", Integer.parseInt(result));
   }
}
