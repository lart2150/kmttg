package com.tivo.kmttg.rpc;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class rnpl {
   private static Hashtable<String,JSONArray> rnpldata = new Hashtable<String,JSONArray>();
   
   // ***** Remote NPL related functions
   
   // Submit remote NPL request to Job Monitor
   public static void rnplListCB(String tivoName) {
      // Clear rnpldata for this TiVo
      //rnpldata.put(tivoName, new JSONArray());
      
      // Submit job to obtain new data for this TiVo
      jobData job = new jobData();
      job.source      = tivoName;
      job.tivoName    = tivoName;
      job.type        = "remote";
      job.name        = "Remote";
      job.remote_rnpl = true;
      job.rnpl        = rnpldata.get(tivoName);
      jobMonitor.submitNewJob(job);
   }
   
   public static void setNPLData(String tivoName, JSONArray data) {
      if (data == null) {
         if (rnpldata.containsKey(tivoName))
            rnpldata.remove(tivoName);
      } else {
         rnpldata.put(tivoName, data);
      }
   }
   
   public static String findRecordingId(String tivoName, Hashtable<String,String> nplData) {
      if ( ! rnpldata.containsKey(tivoName) ) {
         log.error("No data available for findRecordingId");
         return null;
      }
            
      // Match up the following
      // title, recording date, size
      String h_title = null;
      long h_date = 0;
      long h_size = 0;
      if (nplData.containsKey("titleOnly"))
         h_title = nplData.get("titleOnly");
      if (nplData.containsKey("gmt"))
         h_date = Long.parseLong(nplData.get("gmt"));
      if (nplData.containsKey("size"))
         h_size = Long.parseLong(nplData.get("size"));
      if (h_title == null || h_date == 0 || h_size == 0) {
         log.error("findRecordingId insufficient NPL data");
         return null;
      }
      JSONObject json;
      String r_title;
      long r_date;
      long r_size;
      try {
         for (int i=0; i<rnpldata.get(tivoName).length(); ++i) {
            r_title = "";
            r_date = 0;
            r_size = 0;
            json = rnpldata.get(tivoName).getJSONObject(i).getJSONArray("recording").getJSONObject(0);
            if (json.has("title"))
               r_title = string.utfString(json.getString("title"));
            if (json.has("scheduledStartTime"))
               r_date = getLongDateFromString(json.getString("scheduledStartTime"));
            else
               r_date = getLongDateFromString(json.getString("actualStartTime"));               
            if (json.has("size"))
               r_size = (long) (json.getLong("size")*Math.pow(2,10));
            
            //log.print("title: " + h_title + " : " + r_title);
            //log.print("date: " + h_date + " : " + r_date);
            //log.print("size: " + h_size + " : " + r_size);
            if (r_title.equals(h_title) && r_date == h_date && r_size == h_size) {
               if (json.has("recordingId"))
                  return json.getString("recordingId");
            }
         }
         log.error("findRecordingId failed to find a match");
         return null;
      } catch (JSONException e1) {
         log.error("findRecordingId - " + e1.getMessage());
         return null;
      }
   }
   
   private static long getLongDateFromString(String date) {
      try {
         SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz");
         Date d = format.parse(date + " GMT");
         return d.getTime();
      } catch (ParseException e) {
        log.error("getLongDateFromString - " + e.getMessage());
        return 0;
      }
   }

}
