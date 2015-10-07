package com.tivo.kmttg.rpc;

import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.util.log;

public class SkipMode {
   
   public static Stack<Hashtable<String,String>> getShowPoints(String tivoName, JSONObject json) {
      Stack<Hashtable<String,String>> points = null;
      if (json.has("contentId")) {
         Remote r = new Remote(tivoName, true);
         if (r.success) {
            try {
               JSONObject j = new JSONObject();
               j.put("contentId", json.getString("contentId"));
               JSONObject result = r.Command("clipMetadataSearch", j);
               if (result != null && result.has("clipMetadata")) {
                  String clipMetadataId = result.getJSONArray("clipMetadata").getJSONObject(0).getString("clipMetadataId");
                  j.remove("contentId");
                  j.put("clipMetadataId", clipMetadataId);
                  result = r.Command("clipMetadataSearch", j);
                  if (result != null && result.has("clipMetadata")) {
                     JSONObject clipData = result.getJSONArray("clipMetadata").getJSONObject(0);
                     points = jsonToShowPoints(clipData);
                  }
               }
            } catch (JSONException e) {
               log.error("SkipMode getShowPoints - " + e.getMessage());
            }
            r.disconnect();
         }
      } else {
         log.error("SkipMode getShowPoints - contentId not found in json");
      }
      return points;
   }
   
   private static Stack<Hashtable<String,String>> jsonToShowPoints(JSONObject clipData) {
      Stack<Hashtable<String,String>> points = new Stack<Hashtable<String,String>>();
      try {
         if (clipData.has("syncMark") && clipData.has("segment")) {
            JSONArray syncMark = clipData.getJSONArray("syncMark");
            JSONArray segment = clipData.getJSONArray("segment");
            long offset = Long.parseLong(syncMark.getJSONObject(0).getString("timestamp"));
            for (int i=0; i<segment.length(); ++i) {
               JSONObject seg = segment.getJSONObject(i);
               long start = Long.parseLong(seg.getString("startOffset")) - offset;
               long end = Long.parseLong(seg.getString("endOffset")) - offset;
               Hashtable<String,String> h = new Hashtable<String,String>();
               h.put("start", "" + start);
               h.put("end", "" + end);
               points.push(h);
            }
         } else {
            log.error("SkipMode jsonToShowPoints - syncMark or segment data missing");
         }
      } catch (JSONException e) {
         log.error("SkipMode jsonToShowPoints - " + e.getMessage());
      }
      return points;
   }

}
