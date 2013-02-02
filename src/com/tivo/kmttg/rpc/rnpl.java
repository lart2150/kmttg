package com.tivo.kmttg.rpc;

import java.io.BufferedReader;
import java.io.FileReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Stack;
import java.util.TimeZone;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.JSON.JSONTokener;
import com.tivo.kmttg.gui.TableUtil;
import com.tivo.kmttg.main.auto;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.log;

public class rnpl {
   private static Hashtable<String,JSONArray> rnpldata = new Hashtable<String,JSONArray>();
   
   // ***** Remote NPL related functions
   
   // Submit remote NPL request to Job Monitor
   public static void rnplListCB(String tivoName, Stack<Hashtable<String,String>> ENTRIES) {
      // Clear rnpldata for this TiVo
      //rnpldata.put(tivoName, new JSONArray());
      
      // Submit job to obtain new data for this TiVo
      jobData job = new jobData();
      job.source       = tivoName;
      job.tivoName     = tivoName;
      job.type         = "remote";
      job.name         = "Remote";
      job.remote_rnpl  = true;
      job.rnpl         = rnpldata.get(tivoName);
      job.auto_entries = ENTRIES;
      jobMonitor.submitNewJob(job);
   }
   
   public static void setNPLData(String tivoName, JSONArray data, Stack<Hashtable<String,String>> ENTRIES) {
      if (data == null) {
         if (rnpldata.containsKey(tivoName))
            rnpldata.remove(tivoName);
      } else {
         rnpldata.put(tivoName, data);
         // Add RPC data to XML data to enrich information such as originalAirDate & EpisodeNumber
         for (int i=0; i<ENTRIES.size(); ++i)
            addRpcData(tivoName, ENTRIES.get(i));
      }
      
      if (config.GUI_AUTO > 0 || config.GUIMODE) {
         // Refresh GUI table entries with new data
         config.gui.getTab(tivoName).getTable().refreshTitles();
      }
      
      // auto mode
      if (config.GUI_AUTO > 0 || ! config.GUIMODE) {
         auto.processAll(tivoName, ENTRIES);
      }
   }
   
   public static JSONObject findRpcData(String tivoName, Hashtable<String,String> nplData, Boolean silent) {      
      if ( ! rnpldata.containsKey(tivoName) ) {
         if (! silent)
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
         if (! silent)
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
            if (rnpldata.get(tivoName).getJSONObject(i).has("recording"))
               json = rnpldata.get(tivoName).getJSONObject(i).getJSONArray("recording").getJSONObject(0);
            else
               json = rnpldata.get(tivoName).getJSONObject(i);
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
            
            if (r_size == 0) h_size = 0;
            //log.print("title: " + h_title + " : " + r_title);
            //log.print("date: " + h_date + " (" + date_long + ") : " + r_date + " (" + r_sdate + ")");
            //log.print("size: " + h_size + " : " + r_size);
            if (r_title.equals(h_title) && r_diff <= r_date_leeway && r_size == h_size) {
               return json;
            }
         }
         if (! silent)
            log.error("findRpcData failed to find a match");
         return null;
      } catch (JSONException e1) {
         if (! silent)
            log.error("findRpcData - " + e1.getMessage());
         return null;
      }
   }
   
   public static String findRecordingId(String tivoName, Hashtable<String,String> nplData) {
      if (! nplData.containsKey("recordingId"))
         addRpcData(tivoName, nplData);
      if (nplData.containsKey("recordingId")) {
         return nplData.get("recordingId");
      } else {
         log.error("recordingId not available for this entry");
         return null;
      }
   }

   // Add RPC data to entries hashes where information may be missing
   // such as originalAirDate & EpisodeNumber
   public static void addRpcData(String tivoName, Hashtable<String,String> h) {
      JSONObject json = findRpcData(tivoName, h, true);
      if (json != null) {
         try {
            if (json.has("recordingId"))
               h.put("recordingId", json.getString("recordingId"));
            if (! h.containsKey("originalAirDate") && json.has("originalAirdate"))
               h.put("originalAirDate", json.getString("originalAirdate"));
            if (! h.containsKey("EpisodeNumber") && json.has("episodeNum") && json.has("seasonNumber")) {
               h.put(
                  "EpisodeNumber",
                  "" + json.get("seasonNumber") +
                  String.format("%02d", json.getJSONArray("episodeNum").get(0))
               );
            }
         } catch (JSONException e) {
            log.error("addRpcData error - " + e.getMessage());
         }
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
   public static String recordingConflicts(JSONObject json, JSONObject original) {
      try {
         if (json.has("conflicts")) {
            String message = "Will not record due to conflicts. Recordings in conflict:";
            JSONObject o = json.getJSONObject("conflicts");
            JSONArray a;
            // Get original duration
            int duration = original.getInt("duration");
            String title="", subtitle="";
            if (original.has("title"))
               title = original.getString("title");
            if (original.has("subtitle"))
               subtitle = original.getString("subtitle");
            // Using this type of hash to guarantee unique entries
            LinkedHashSet<String> h = new LinkedHashSet<String>();
            if (o.has("willCancel")) {
               a = o.getJSONArray("willCancel");
               for (int j=0; j<a.length(); ++j) {
                  JSONObject jr = a.getJSONObject(j);
                  String[] fields = {"losingOffer", "winningOffer"};
                  for (int f=0; f<fields.length; ++f) {
                     if (jr.has(fields[f])) {
                        JSONArray ar = jr.getJSONArray(fields[f]);
                        for (int k=0; k<ar.length(); ++k) {
                           JSONObject js = ar.getJSONObject(k);
                           String t="", s="";
                           if (js.has("title"))
                              t = js.getString("title");
                           if (js.has("subtitle"))
                              s = js.getString("subtitle");
                           if (t.equals(title) && s.equals(subtitle))
                              js.put("duration", duration);
                           h.add(formatEntry(js));
                        }
                     }
                  }
               }
               if (h.size() > 0) {
                  Iterator<String> itr = h.iterator();
                  while( itr.hasNext() ) {
                     message += "\n   " + itr.next();
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
   
   private static String formatEntry(JSONObject json) {
      String message = "";
      try {
         if (json.has("startTime") && json.has("duration")) {
            String start = jsonTimeToLocalTime(json.getString("startTime"), 0);
            String stop = jsonTimeToLocalTime(json.getString("startTime"), json.getLong("duration"));
            message += start + "-" + stop + " -- ";
         }
         if(json.has("title"))
            message += json.getString("title");
         if(json.has("subtitle"))
            message += " - " + json.getString("subtitle");
         if (json.has("channel")) {
            JSONObject c = json.getJSONObject("channel");
            if (c.has("callSign") && c.has("channelNumber"))
               message += " on " + c.getString("channelNumber") + "=" + c.getString("callSign");
         }
      } catch (JSONException e) {
         log.error("formatEntry error - " + e.getMessage());
      }
      return message;
   }
   
   private static String jsonTimeToLocalTime(String jsonTime, long offset) {
      long gmt = TableUtil.getLongDateFromString(jsonTime) + offset*1000;
      SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yy hh:mm a");
      return sdf.format(gmt);
   }
   
   // Dump JSON contents to message window as 1 line per key/value pair
   @SuppressWarnings("unchecked")
   public static void printJSON(JSONObject json) {
      Iterator<String> keys = json.keys();
      List<String> sortKey = new ArrayList<String>();
      while(keys.hasNext())
         sortKey.add(keys.next());
      Collections.sort(sortKey);
      for (int i=0; i<sortKey.size(); ++i) {
         try {
            log.print(sortKey.get(i) + "=" + json.get(sortKey.get(i)));
         } catch (JSONException e) {
            log.error("printJSON error - " + e.getMessage());
         }
      }
   }

}
