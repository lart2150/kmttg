/*
 * Copyright 2008-Present Kevin Moye <moyekj@yahoo.com>.
 *
 * This file is part of kmttg package.
 *
 * kmttg is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this project.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tivo.kmttg.mind;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Stack;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.JSON.XML;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.mind.EasySSLHelper;
import com.tivo.kmttg.mind.SimpleCookieManager;
import com.tivo.kmttg.util.log;

public class Mind {
   private Boolean debug = false;
   public String server;
   private String mindVer = "mind9";
   
   //public static final String DEFAULT_MIND_SERVER = config.pyTivo_mind;
   public static final String DEFAULT_MIND_SERVER = "mind.tivo.com:8181";
   SimpleCookieManager cm = new SimpleCookieManager();
   
   public Mind(String mindServer) {
      server = mindServer;
      if (config.getTivoUsername() == null) {
         log.error("tivo.com username & password not set in config or in pyTivo");
      }
   }

   public Mind() {
      this(DEFAULT_MIND_SERVER);
   }

   public Boolean login(final String login, final String password) {      
      String urlString = "https://" + server + "/mind/login";
      try {
         // Open connection and post data
         String urlData = "cams_security_domain=tivocom";
         urlData = urlData + "&" + "cams_login_config=http";
         urlData = urlData + "&" + "cams_cb_username=" + encode(login);
         urlData = urlData + "&" + "cams_cb_password=" + encode(password);
         urlData = urlData + "&" + "cams_original_url=" + encode("/mind/" + mindVer + "?type=infoGet");
         URL url = new URL(urlString);
         URLConnection con = EasySSLHelper.openEasySSLConnection(url);
         con.setDoOutput(true);
         OutputStreamWriter out = new OutputStreamWriter(con.getOutputStream());
         out.write(urlData);
         out.flush();
         out.close();

         cm.storeCookies(con);

         // Check response
         Boolean success = false;
         BufferedReader in = new BufferedReader( new InputStreamReader(con.getInputStream()));
         String inputLine;
         while ((inputLine = in.readLine()) != null) {
            if (inputLine.matches("^.+success.+$")) {
               success = true;
            } else {
               log.error(inputLine);
            }
            if (debug) log.print(inputLine);
         }
         if ( ! success ) {
            log.error("urlString=" + urlString + " urlData=" + urlData);
         }
         in.close();
         return success;
      }
      catch (MalformedURLException e) {
         log.error(e.toString() + " - " + urlString);
         return false;
      } 
      catch (IOException e) {
         log.error(e.toString() + " - " + urlString);
         return false;
      }
   }

   @SuppressWarnings("rawtypes")
   public Stack<String> dict_request(String type, Hashtable data) {
      String urlString = "https://" + server + "/mind/" + mindVer + "?type=" + type;
      try {
         // Open connection and post data
         byte urlData[] = dictcode(data);
         URL url = new URL(urlString);
         URLConnection con = EasySSLHelper.openEasySSLConnection(url);
         cm.setCookies(con);
         con.setDoInput(true);
         con.setDoOutput(true);
         con.setRequestProperty("Content-Type", "x-tivo/dict-binary");
         OutputStream out = con.getOutputStream();
         out.write(urlData);
         out.flush();
         cm.storeCookies(con);
         out.close();

         // Return response
         Stack<String> s = new Stack<String>();
         BufferedReader in = new BufferedReader( new InputStreamReader(con.getInputStream()));
         String inputLine;
         while ((inputLine = in.readLine()) != null) {
            s.add(inputLine);
            if (debug) log.print(inputLine);
         }
         in.close();
         return s;
      }
      catch (MalformedURLException e) {
         log.error(e.toString() + " - " + urlString);
         return null;
      } 
      catch (IOException e) {
         log.error(e.toString() + " - " + urlString);
         return null;
      }
   }
   
   private String getDateTime() {
      DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      Date date = new Date();
      return dateFormat.format(date);
   }   
   
   @SuppressWarnings({ "unchecked", "rawtypes" })
   public Boolean pushVideo(Hashtable info) throws MalformedURLException {
      // At minimum tsn & url required
      if (info.get("tsn") == null || info.get("url") == null) {
         log.error("tsn & url required");
         return false;
      }
      
      // Fill out any missing information
      if (info.get("title") == null) {
         String title = (String)info.get("url");
         String s[] = title.split("/");
         title = s[s.length-1];
         title = title.replaceFirst("^(.+)\\..+$", "$1");
         info.put("title", title);
      }
      if (info.get("source") == null) {
         info.put("source", info.get("title"));
      }
      if (info.get("description") == null) {
         info.put("description", info.get("title"));
      }
      if (info.get("duration") == null) {
         info.put("duration", "0");
      }
      if (info.get("publishDate") == null) {
         info.put("publishDate", getDateTime());
      }
      if (info.get("size") == null) {
         URL url = new URL((String)info.get("url"));
         String name = url.getFile().replaceFirst("^.+file:(.+)$", "$1");
         File f = new File(name);
         info.put("size", String.valueOf(f.length()));
      }
      if (info.get("mime") == null) {
         info.put("mime", "video/mpeg");
      }
      
      Stack<String> s = pcBodySearch();
      if (s != null) {
         String pcBodyId = getElement(s.get(0), "pcBodyId");
         if (pcBodyId != null) {
            Hashtable h = new Hashtable();
            h.put("bodyId",      info.get("tsn"));
            h.put("description", info.get("description"));
            h.put("duration",    info.get("duration"));
            h.put("publishDate", info.get("publishDate"));
            h.put("size",        info.get("size"));
            h.put("source",      info.get("source"));
            h.put("title",       info.get("title"));
            if (info.get("subtitle") != null) {
               h.put("subtitle",    info.get("subtitle"));
            }
            
            h.put("partnerId",   "tivo:pt.3187");
            h.put("pcBodyId",    pcBodyId);
            h.put("state",       "complete");
            
            String mime = (String)info.get("mime");
            String url = (String)info.get("url");
            if (mime.equals("video/mp4")) {
               h.put("encodingType", "avcL41MP4");
               //h.put("url", url + "?Format=" + mime);
            }
            else if (mime.equals("video/bif")) {
               h.put("encodingType", "vc1ApL3");
               //h.put("url", url + "?Format=" + mime);
            }
            else {
               h.put("encodingType", "mpeg2ProgramStream");
            }
            h.put("url", url);            

            s = bodyOfferModify(h);
            if (s != null) {
               String offerId = getElement(s.get(0), "offerId");
               if (offerId != null) {
                  String contentId = offerId.replaceFirst("tivo:of", "tivo:ct");
                  s = subscribe(offerId, contentId, (String)info.get("tsn"));
                  if (s != null) {
                     String err = getElement(s.get(0), "error");
                     if (err != null) {
                        log.error(err);
                        return false;
                     }
                     return true;
                  }
                  else {
                     log.error("subcscribe failed");
                     return false;
                  }
               }
               else {
                  log.error("offerId not found");
                  return false;
               }               
            }
            else {
               log.error("bodyOfferModify failed");
               return false;
            }
         } else {
            log.error("pcBodyId not found");
            return false;
         }
      }
      else {
         log.error("pcBodySearch failed");
         return false;
      }
   }
   
   @SuppressWarnings({ "unchecked", "rawtypes" })
   public Stack<String> subscribe(String offerId, String contentId, String tsn) {      
      Hashtable source = new Hashtable();
      source.put("contentId", contentId);
      source.put("offerId",   offerId);
      source.put("type",      "singleOfferSource");
      
      Hashtable h = new Hashtable();
      h.put("bodyId",      tsn);
      h.put("idSetSource", source);
      h.put("title",       "pcBodySubscription");
      h.put("uiType",      "cds");
      Stack<String> s = dict_request("subscribe&bodyId=" + tsn, h);
      return s;
   }
   
   @SuppressWarnings({"rawtypes" })
   public Stack<String> pcBodySearch() {
      Hashtable h = new Hashtable();
      Stack<String> s = dict_request("pcBodySearch", h);
      Boolean pc = false;
      for (int i=0; i<s.size(); ++i) {
         if (s.get(i).matches("^.+<name>.+$")) {
            pc = true;
         }
      }
      if ( ! pc ) {
         s = pcBodyStore("pyTivo", "true");
      }
      return s;
   }
   
   @SuppressWarnings({ "unchecked", "rawtypes" })
   public Stack<String> pcBodyStore(String name, String replace) {
      Hashtable h = new Hashtable();
      h.put("name", name);
      h.put("replaceExisting", replace);
      Stack<String> s = dict_request("pcBodyStore", h);
      return s;
   }

   @SuppressWarnings({"rawtypes" })
   public Stack<String> bodyOfferModify(Hashtable h) {
      Stack<String> s = dict_request(
         "bodyOfferModify&bodyId=" + h.get("bodyId"), h
      );
      return s;
   }      
   
   public void varint(OutputStream os, int i) throws IOException {
      while (i > 0x7f) {
         os.write(i & 0x7f);
         i >>= 7;
      }
      os.write(i|0x80);
   }

   @SuppressWarnings("rawtypes")
   public byte[] dictcode(Hashtable d) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        _dictcode(bo, d);
        return bo.toByteArray();
   }
   
   @SuppressWarnings({ "rawtypes", "unchecked" })
   private void _dictcode(OutputStream bo, Hashtable d) {
      try {
         String[] keys = (String[]) d.keySet().toArray(new String[0]);  
         Arrays.sort(keys);  
   
         for (String k : keys) {
            Object v = d.get(k);
            varint(bo, k.length());
            bo.write(k.getBytes());
            
            if ( v instanceof Hashtable ) {
               bo.write(2);
               _dictcode(bo, (Hashtable)v);
            } else {
               String s = (String)v;
               bo.write(1);
               varint(bo, s.length());
               bo.write(s.getBytes());
            }
            bo.write(0);
         }
         bo.write(0x80);
      } catch(IOException e) {
        // this shouldn't happen.
      }            
   }
   
   public String encode(String s) {
      if (s == null) {
         s = "";
      }
      try {
         return URLEncoder.encode(s, "UTF-8");
      } catch (UnsupportedEncodingException e) {
         e.printStackTrace();
         return "";
      }
   }

   public String getElement(String xml, String element) {
      String l[] = xml.split("<");
      for (int i=0; i<l.length; ++i) {
         String n = l[i].replaceFirst("^(.+)>.*$", "$1");
         String v = l[i].replaceFirst("^.+>(.*)$", "$1");
         if (n.equals(element)) {
            return v;
         }
      }
      return null;
   }
   
   public JSONArray ToDo(String tivoName) {
      if (! login(config.getTivoUsername(), config.getTivoPassword())) {
         log.error("Login to server failed: " + server);
         return null;
      }
      String tsn = config.getTsn(tivoName);
      if (tsn == null) {
         log.error("Can't determine TSN for TiVo: " + tivoName);
         return null;
      }
      String bodyId = "tsn:" + tsn;

      List<JSONObject> allShows = new ArrayList<JSONObject>();
      JSONArray sorted = new JSONArray();
      try {   
         // Top level list - run in a loop to grab all items
         Boolean stop = false;
         String command = "recordingSearch";
         Hashtable<String,Object> h = new Hashtable<String,Object>();
         h.put("bodyId", bodyId);
         h.put("state", "scheduled");
         h.put("levelOfDetail", "medium");
         h.put("count", "1");
         int offset = 0;
         while ( ! stop ) {
            h.put("offset", "" + offset);
            offset += 1;
            Stack<String> s = dict_request(command + "&bodyId=" + bodyId, h);
            if (s != null && s.size() > 0) {
               JSONObject result = XML.toJSONObject(s.get(0));
               if (result != null && result.has("recordingList")) {
                  if (result.getJSONObject("recordingList").has("recording")) {
                     JSONObject j = result.getJSONObject("recordingList").getJSONObject("recording");
                     allShows.add(j);
                  } else
                     stop = true;
               } else
                  stop = true;
            } else
               stop = true;
         } // while
         if (allShows.size() == 0)
            return null;
         
         // Sort allShows by start time
         DateComparator comparator = new DateComparator();
         Collections.sort(allShows, comparator);
         for (JSONObject ajson : allShows) {
            sorted.put(ajson);
         }
      } catch(Exception e) {
         log.error("Mind ToDo - " + e.getMessage());
         e.printStackTrace();
         return null;
      }
      return sorted;
   }
   
   public JSONArray SeasonPasses(String tivoName) {
      if (! login(config.getTivoUsername(), config.getTivoPassword())) {
         log.error("Login to server failed: " + server);
         return null;
      }
      String tsn = config.getTsn(tivoName);
      if (tsn == null) {
         log.error("Can't determine TSN for TiVo: " + tivoName);
         return null;
      }
      String bodyId = "tsn:" + tsn;

      JSONArray sorted = new JSONArray();
      try {   
         String command = "subscriptionSearch";
         Hashtable<String,Object> h = new Hashtable<String,Object>();
         h.put("bodyId", bodyId);
         h.put("noLimit", "true");
         h.put("levelOfDetail", "medium");
         h.put("orderBy", "priority");
         Stack<String> s = dict_request(command + "&bodyId=" + bodyId, h);
         if (s != null && s.size() > 0) {
            JSONObject result = XML.toJSONObject(s.get(0));
            if (result != null && result.has("subscriptionList")) {
               JSONArray a = result.getJSONObject("subscriptionList").getJSONArray("subscription");
               for (int i=0; i<a.length(); ++i) {
                  JSONObject o = a.getJSONObject(i);
                  if (o.has("idSetSource")) {
                     JSONObject id = o.getJSONObject("idSetSource");
                     if (! id.getString("type").equals("singleOfferSource"))
                        sorted.put(o);
                  }
               }
            }
         }
         if (sorted.length() == 0)
            return null;
         
      } catch(Exception e) {
         log.error("Mind SeasonPasses - " + e.getMessage());
         e.printStackTrace();
         return null;
      }
      return sorted;
   }
   
   public class DateComparator implements Comparator<JSONObject> {      
      public int compare(JSONObject j1, JSONObject j2) {
         long start1 = TableUtil.getStartTime(j1);
         long start2 = TableUtil.getStartTime(j2);
         if (start1 > start2){
            return 1;
         } else if (start1 < start2){
            return -1;
         } else {
            return 0;
         }
      }
   }
}
