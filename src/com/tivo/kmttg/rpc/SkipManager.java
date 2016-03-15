package com.tivo.kmttg.rpc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Stack;

import javafx.application.Platform;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.tivoTab;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class SkipManager {
   private static String ini = config.programDir + File.separator + "AutoSkip.ini";
   private static Hashtable<String,AutoSkip> instances = new Hashtable<String,AutoSkip>();
   private static Hashtable<String,SkipService> serviceInstances = new Hashtable<String,SkipService>();
   
   public static synchronized String iniFile() {
      return ini;
   }
   
   public static synchronized void disableMonitor(String tivoName) {
      debug.print("tivoName=" + tivoName);
      if ( instances.containsKey(tivoName)) {
         instances.get(tivoName).monitor = false;
      }            
   }
   
   public static synchronized Boolean skipEnabled() {
      debug.print("");
      // At least 1 TiVo needs to be RPC enabled
      return config.rpcEnabled();
   }
   
   public static synchronized void addSkip(String tivoName) {
      debug.print("tivoName=" + tivoName);
      if ( ! instances.containsKey(tivoName)) {
         instances.put(tivoName, new AutoSkip());
      }      
   }
   
   public static synchronized AutoSkip getSkip(String tivoName) {
      debug.print("tivoName=" + tivoName);
      if (instances.containsKey(tivoName))
         return instances.get(tivoName);
      else
         return null;
   }
   
   public static synchronized void startService(String tivoName) {
      debug.print("tivoName=" + tivoName);
      if ( ! serviceInstances.containsKey(tivoName)) {
         serviceInstances.put(tivoName, new SkipService(tivoName));
         serviceInstances.get(tivoName).start();
      }
   }
   
   public static synchronized void stopService(String tivoName) {
      debug.print("tivoName=" + tivoName);
      if (serviceInstances.containsKey(tivoName)) {
         serviceInstances.get(tivoName).stop();
         serviceInstances.remove(tivoName);
      }
   }
   
   /*public static synchronized SkipService getService(String tivoName) {
      debug.print("tivoName=" + tivoName);
      if (instances.containsKey(tivoName))
         return serviceInstances.get(tivoName);
      else
         return null;
   }*/
   
   public static synchronized Boolean isMonitoring(String tivoName) {
      debug.print("tivoName=" + tivoName);
      if (instances.containsKey(tivoName)) {
         return instances.get(tivoName).isMonitoring();
      }
      return false;
   }
   
   public static synchronized void disable(String tivoName) {
      debug.print("tivoName=" + tivoName);
      if (instances.containsKey(tivoName)) {
         instances.get(tivoName).disable();
         instances.remove(tivoName);
      }
   }
   
   public static synchronized void skipPlay(String tivoName, Hashtable<String,String> entry) {
      debug.print("tivoName=" + tivoName + " entry=" + entry);
      addSkip(tivoName);
      instances.get(tivoName).skipPlay(tivoName, entry);
   }
   
   // Save commercial points for current entry to ini file
   public static synchronized void saveEntry(final String contentId, String offerId, long offset,
         String title, final String tivoName, Stack<Hashtable<String,Long>> data) {
      debug.print("contentId=" + contentId + " offerId=" + offerId + " offset=" + offset);
      log.print("Saving AutoSkip entry: " + title);
      try {
         String eol = "\r\n";
         BufferedWriter ofp = new BufferedWriter(new FileWriter(ini, true));
         ofp.write("<entry>" + eol);
         ofp.write("contentId=" + contentId + eol);
         ofp.write("offerId=" + offerId + eol);
         ofp.write("offset=" + offset + eol);
         ofp.write("tivoName=" + tivoName + eol);
         ofp.write("title=" + title + eol);
         for (Hashtable<String,Long> entry : data) {
            ofp.write(entry.get("start") + " " + entry.get("end") + eol);
         }
         ofp.close();
         if (config.GUIMODE) {
            Platform.runLater(new Runnable() {
               @Override public void run() {
                  config.gui.getTab(tivoName).getTable().updateSkipStatus(contentId);
               }
            });
         }
      } catch (IOException e) {
         log.error("saveEntry - " + e.getMessage());
      }
   }
   
   public static synchronized Boolean hasEntry(String contentId) {
      debug.print("contentId=" + contentId);
      if (file.isFile(ini)) {
         try {
            BufferedReader ifp = new BufferedReader(new FileReader(ini));
            String line = null;
            while (( line = ifp.readLine()) != null) {
               if (line.contains("<entry>")) {
                  line = ifp.readLine();
                  if (line.startsWith("contentId")) {
                     String[] l = line.split("=");
                     if (l[1].equals(contentId)) {
                        ifp.close();
                        return true;
                     }
                  }
               }
            }
            ifp.close();
         } catch (Exception e) {
            log.error("readEntry - " + e.getMessage());
            log.error(Arrays.toString(e.getStackTrace()));
         }
      }
      return false;
   }
   
   // Remove any entries matching given contentId from ini file
   public static synchronized Boolean removeEntry(final String contentId) {
      debug.print("contentId=" + contentId);
      if (file.isFile(ini)) {
         try {
            Boolean itemRemoved = false;
            Stack<String> lines = new Stack<String>();
            BufferedReader ifp = new BufferedReader(new FileReader(ini));
            String line = null;
            Boolean include = true;
            String tivoName = null;
            String title = null;
            while (( line = ifp.readLine()) != null) {
               if (line.contains("<entry>")) {
                  include = true;
                  String nextline = ifp.readLine();
                  String[] l = nextline.split("=");
                  if (l[1].equals(contentId)) {
                     include = false;
                     itemRemoved = true;
                     ifp.readLine(); // offerId
                     ifp.readLine(); // offset
                     tivoName = ifp.readLine().split("=")[1];
                     title = ifp.readLine().split("=")[1];
                  }
                  if (include) {
                     lines.push(line);
                     lines.push(nextline);
                  }
               } else {
                  if (include)
                     lines.push(line);
               }
            }
            ifp.close();
            String eol = "\r\n";
            BufferedWriter ofp = new BufferedWriter(new FileWriter(ini));
            for (String l : lines) {
               ofp.write(l + eol);
            }
            ofp.close();
            if (itemRemoved) {
               log.print("Removed entry for " + tivoName + ": " + title);
               if (tivoName != null && config.GUIMODE) {
                  // Remove asterisk from associated table
                  final String final_tivoName = tivoName;
                  Platform.runLater(new Runnable() {
                     @Override public void run() {
                        tivoTab t = config.gui.getTab(final_tivoName);
                        if (t != null) {
                           t.getTable().updateSkipStatus(contentId);
                        }
                     }
                  });
               }
            }
            else
               log.print("No entry found for: " + contentId);
            return itemRemoved;
         } catch (Exception e) {
            log.error("removeEntry - " + e.getMessage());
         }
      }
      return false;
   }
   
   // Change offset for given contentId
   public static synchronized Boolean changeEntry(String contentId, String offset, String title) {
      debug.print("contentId=" + contentId + " offset=" + offset + " title=" + title);
      if (file.isFile(ini)) {
         try {
            Boolean itemChanged = false;
            Stack<String> lines = new Stack<String>();
            BufferedReader ifp = new BufferedReader(new FileReader(ini));
            String line = null;
            while (( line = ifp.readLine()) != null) {
               if (line.contains("contentId")) {
                  Boolean changed = false;
                  String[] l = line.split("=");
                  if (l[1].equals(contentId)) {
                     itemChanged = true;
                     changed = true;
                  }
                  String offerId = ifp.readLine(); // offerId
                  String nextline = ifp.readLine(); // offset
                  l = nextline.split("=");
                  lines.push(line);
                  lines.push(offerId);
                  if (changed)
                     lines.push(l[0] + "=" + offset);
                  else
                     lines.push(nextline);
               } else {
                  lines.push(line);
               }
            }
            ifp.close();
            String eol = "\r\n";
            BufferedWriter ofp = new BufferedWriter(new FileWriter(ini));
            for (String l : lines) {
               ofp.write(l + eol);
            }
            ofp.close();
            if (itemChanged)
               log.print("'" + title + "' offset updated to: " + offset);
            else
               log.print("'" + title + "' not updated.");
            return itemChanged;
         } catch (Exception e) {
            log.error("removeEntry - " + e.getMessage());
         }
      }
      return false;
   }
   
   // Return entries for use by SkipDialog table
   public static synchronized JSONArray getEntries() {
      debug.print("");
      JSONArray entries = new JSONArray();
      if (file.isFile(ini)) {
         try {
            BufferedReader ifp = new BufferedReader(new FileReader(ini));
            String line=null, contentId="", title="", offset="", offerId="", tivoName="";
            JSONArray cuts = new JSONArray();
            while (( line = ifp.readLine()) != null) {
               if (line.contains("<entry>")) {
                  if (cuts.length() > 0) {
                     JSONObject json = new JSONObject();
                     json.put("contentId", contentId);
                     json.put("offerId", offerId);
                     json.put("offset", offset);
                     json.put("tivoName", tivoName);
                     json.put("title", title);
                     json.put("ad1", "" + cuts.getJSONObject(0).get("end"));
                     json.put("cuts", cuts);
                     entries.put(json);
                  }
                  cuts = new JSONArray();
               }
               if (line.contains("contentId="))
                  contentId = line.replaceFirst("contentId=", "");
               if (line.contains("offerId="))
                  offerId = line.replaceFirst("offerId=", "");
               if (line.contains("offset="))
                  offset = line.replaceFirst("offset=", "");
               if (line.contains("tivoName="))
                  tivoName = line.replaceFirst("tivoName=", "");
               if (line.contains("title="))
                  title = line.replaceFirst("title=", "");
               if (line.matches("^[0-9]+.*")) {
                  String[] l = line.split("\\s+");
                  JSONObject j = new JSONObject();
                  j.put("start", Long.parseLong(l[0]));
                  j.put("end", Long.parseLong(l[1]));
                  cuts.put(j);
               }
            } // while
            if (cuts.length() > 0) {
               JSONObject json = new JSONObject();
               json.put("contentId", contentId);
               json.put("offerId", offerId);
               json.put("offset", offset);
               json.put("tivoName", tivoName);
               json.put("title", title);
               json.put("ad1", "" + cuts.getJSONObject(0).get("end"));
               json.put("cuts", cuts);
               entries.put(json);
            }
            ifp.close();
         } catch (Exception e) {
            log.error("getEntries - " + e.getMessage());
         }
      }
      return entries;
   }
   
   // Remove AutoSkip entries that no longer have corresponding NPL entries
   public static synchronized void pruneEntries(String tivoName, Stack<Hashtable<String,String>> nplEntries) {
      debug.print("tivoName=" + tivoName + " nplEntries=" + nplEntries);
      if (nplEntries == null)
         return;
      if (nplEntries.size() == 0)
         return;
      
      try {
         int count = 0;
         JSONArray skipEntries = getEntries();
         for (int i=0; i<skipEntries.length(); ++i) {
            JSONObject json = skipEntries.getJSONObject(i);
            if (json.getString("tivoName").equals(tivoName)) {
               Boolean exists = false;
               for (Hashtable<String,String> nplEntry : nplEntries) {
                  if (nplEntry.containsKey("offerId")) {
                     if (nplEntry.get("offerId").equals(json.getString("offerId")))
                        exists = true;
                  }
               }
               if (! exists) {
                  removeEntry(json.getString("contentId"));
                  count++;
               }
            }
         }
         if (count == 0)
            log.warn("No entries found to prune");
      } catch (JSONException e) {
         log.error("pruneEntries - " + e.getMessage());
      }
   }
      
   public static synchronized String toMinSec(long msecs) {
      debug.print("msecs=" + msecs);
      return com.tivo.kmttg.captions.util.toHourMinSec(msecs);
   }

}
