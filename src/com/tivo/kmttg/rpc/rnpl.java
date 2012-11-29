package com.tivo.kmttg.rpc;

import java.io.BufferedReader;
import java.io.FileReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.TimeZone;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.JSON.JSONTokener;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.log;

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
   
   public static JSONObject findRpcData(String tivoName, Hashtable<String,String> nplData) {      
      if ( ! rnpldata.containsKey(tivoName) ) {
         log.error("No data available for findRpcData");
         return null;
      }
            
      // Match up the following
      // title, recording date, size
      String h_title = null;
      //String date_long = "";
      long h_date = 0;
      long h_size = 0;
      if (nplData.containsKey("titleOnly"))
         h_title = nplData.get("titleOnly");
      if (nplData.containsKey("gmt"))
         h_date = Long.parseLong(nplData.get("gmt"));
      if (nplData.containsKey("size"))
         h_size = Long.parseLong(nplData.get("size"));
      //if (nplData.containsKey("date_long"))
         //date_long = nplData.get("date_long");
      if (h_title == null || h_date == 0 || h_size == 0) {
         log.error("findRecordingId insufficient NPL data");
         return null;
      }
      JSONObject json;
      String r_title;
      //String r_sdate = "";
      long r_date, r_diff;
      long r_size;
      long r_date_leeway = 60000; // 60 second leeway
      try {
         for (int i=0; i<rnpldata.get(tivoName).length(); ++i) {
            r_title = "";
            r_date = 0;
            r_diff = 10*r_date_leeway;
            r_size = 0;
            json = rnpldata.get(tivoName).getJSONObject(i).getJSONArray("recording").getJSONObject(0);
            if (json.has("title"))
               r_title = json.getString("title");
            if (json.has("startTime")) {
               r_date = getLongDateFromString(json.getString("startTime"));
               r_diff = Math.abs(r_date - h_date);
               //r_sdate = json.getString("startTime");
            } else {
               if (json.has("requestedStartTime")) {
                  r_date = getLongDateFromString(json.getString("requestedStartTime"));
                  r_diff = Math.abs(r_date - h_date);
                  //r_sdate = json.getString("requestedStartTime");
               }
            }
            if (r_date == 0 && json.has("scheduledStartTime"))
               r_date = getLongDateFromString(json.getString("scheduledStartTime"));               
            if (r_date == 0 && json.has("actualStartTime"))
               r_date = getLongDateFromString(json.getString("actualStartTime"));               
            if (json.has("size"))
               r_size = (long) (json.getLong("size")*Math.pow(2,10));
            
            //log.print("title: " + h_title + " : " + r_title);
            //log.print("date: " + h_date + " (" + date_long + ") : " + r_date + " (" + r_sdate + ")");
            //log.print("size: " + h_size + " : " + r_size);
            if (r_title.equals(h_title) && r_diff <= r_date_leeway && r_size == h_size) {
               return json;
            }
         }
         log.error("findRpcData failed to find a match");
         return null;
      } catch (JSONException e1) {
         log.error("findRpcData - " + e1.getMessage());
         return null;
      }
   }
   
   public static String findRecordingId(String tivoName, Hashtable<String,String> nplData) {
      JSONObject json = findRpcData(tivoName, nplData);
      if (json != null && json.has("recordingId")) {
         try {
            return json.getString("recordingId");
         } catch (JSONException e) {
            log.error("findRecordingId error - " + e.getMessage());
         }
      }
      return null;
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
   
   // Convert to GMT then to TiVo json time string format
   public static String getStringFromLongDate(long date) {
      SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      format.setTimeZone(TimeZone.getTimeZone("GMT"));
      return format.format(date);
   }
   
   // Convert msecs to "N mins"
   public static String msecsToMins(Long msecs) {
      long secs = msecs/1000;
      long mins = secs/60;
      if (mins > 0) {
         secs -= mins*60;
      }
      // Round mins +1 if secs > 30
      if (secs > 30) {
         mins += 1;
      }
      return String.format("%d mins", mins);
   }
   
   public static JSONArray loadWillNotRecordData(String fileName) {
      JSONArray a = new JSONArray();
      JSONObject o;
      try {
         BufferedReader is = new BufferedReader(new FileReader(fileName));
         String line;
         while ( (line=is.readLine()) != null ) {
            o = new JSONObject(new JSONTokener(line));
            a.put(o.getJSONArray("recording").get(0));
         }
         is.close();
         return a;
      } catch (Exception e) {
         log.error("loadWillNotRecordData - " + e.getMessage());
         return null;
      }
   }
   
   // Return null if no conflicts, error string with details if conflicts exist
   public static String recordingConflicts(JSONObject json) {
      try {
         if (json.has("conflicts")) {
            String message = "Will not record due to conflicts. Recordings in conflict:";
            JSONObject o = json.getJSONObject("conflicts");
            JSONArray a;
            if (o.has("willCancel")) {
               a = o.getJSONArray("willCancel");
               for (int j=0; j<a.length(); ++j) {
                  JSONObject jr = a.getJSONObject(j);
                  if (jr.has("losingOffer")) {
                     JSONArray ar = jr.getJSONArray("losingOffer");
                     for (int k=0; k<ar.length(); ++k) {
                        message += "\n" + ar.getJSONObject(k).getString("title");
                     }
                  }
                  if (jr.has("winningOffer")) {
                     JSONArray ar = jr.getJSONArray("winningOffer");
                     for (int k=0; k<ar.length(); ++k) {
                        message += "\n" + ar.getJSONObject(k).getString("title");
                     }
                  }
               }
            }
            return message;
         }
      } catch (JSONException e1) {
         return "recordingConflicts - " + e1.getMessage();
      }

      return null;
   }

}
