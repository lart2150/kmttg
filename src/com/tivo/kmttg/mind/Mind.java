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
import java.util.Arrays;
import java.util.Date;
import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.mind.EasySSLHelper;
import com.tivo.kmttg.mind.SimpleCookieManager;
import com.tivo.kmttg.util.log;

/*
Mind mind = new Mind();
if (!mind.login(username, password)) {
   mind.printErrors();
   log.error("Failed to login to Mind");
   return false;
}
*/
public class Mind {
   public String server;
   private String mindVer = "mind9";
   
   public static final String DEFAULT_MIND_SERVER = "mind.tivo.com:8181";
   private Stack<String> errors = new Stack<String>();
   SimpleCookieManager cm = new SimpleCookieManager();
   
   public Mind(String mindServer) {
      server = mindServer;
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

         cm.storeCookies(con);

         // Check response
         Boolean success = false;
         BufferedReader in = new BufferedReader( new InputStreamReader(con.getInputStream()));
         String inputLine;
         while ((inputLine = in.readLine()) != null) {
            if (inputLine.matches("^.+success.+$")) {
               success = true;
            }
            //log.print(inputLine);
         }
         return success;
      }
      catch (MalformedURLException e) {
         errors.push(e.toString() + " - " + urlString);
         return false;
      } 
      catch (IOException e) {
         errors.push(e.toString() + " - " + urlString);
         return false;
      }
   }

   @SuppressWarnings("unchecked")
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

         // Return response
         Stack<String> s = new Stack<String>();
         BufferedReader in = new BufferedReader( new InputStreamReader(con.getInputStream()));
         String inputLine;
         while ((inputLine = in.readLine()) != null) {
            s.add(inputLine);
            //log.print(inputLine);
         }
         return s;
      }
      catch (MalformedURLException e) {
         errors.push(e.toString() + " - " + urlString);
         return null;
      } 
      catch (IOException e) {
         errors.push(e.toString() + " - " + urlString);
         return null;
      }
   }
   
   private String getDateTime() {
      DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      Date date = new Date();
      return dateFormat.format(date);
   }   
   
   @SuppressWarnings("unchecked")
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
   
   @SuppressWarnings("unchecked")
   public Stack<String> subscribe(String offerId, String contentId, String tsn) {
      errors.clear();
      
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
   
   @SuppressWarnings("unchecked")
   public Stack<String> pcBodySearch() {
      errors.clear();
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
   
   @SuppressWarnings("unchecked")
   public Stack<String> pcBodyStore(String name, String replace) {
      Hashtable h = new Hashtable();
      h.put("name", name);
      h.put("replaceExisting", replace);
      Stack<String> s = dict_request("pcBodyStore", h);
      return s;
   }

   @SuppressWarnings("unchecked")
   public Stack<String> bodyOfferModify(Hashtable h) {
      errors.clear();
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

   @SuppressWarnings("unchecked")
   public byte[] dictcode(Hashtable d) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        _dictcode(bo, d);
        return bo.toByteArray();
   }
   
   @SuppressWarnings("unchecked")
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
   
   public void printErrors() {
      for (int i=0; i<errors.size(); ++i)
         //System.out.println("ERROR: " + errors.get(i));
       log.error("ERROR: " + errors.get(i));
   }

}
