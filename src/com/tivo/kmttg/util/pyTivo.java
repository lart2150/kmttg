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
package com.tivo.kmttg.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
//import java.net.URLEncoder;
import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.JSON.XML;
import com.tivo.kmttg.main.config;
//import com.tivo.kmttg.main.http;
//import com.tivo.kmttg.rpc.Remote;

public class pyTivo {
   
   public static Stack<Hashtable<String,String>> parsePyTivoConf(String conf) {
      Stack<Hashtable<String,String>> s = new Stack<Hashtable<String,String>>();
      String username = null;
      String password = null;
      //String mind = null;
      
      try {
         BufferedReader ifp = new BufferedReader(new FileReader(conf));
         String line = null;
         String key = null;
         Hashtable<String,String> h = new Hashtable<String,String>();
         while (( line = ifp.readLine()) != null) {
            // Get rid of leading and trailing white space
            line = line.replaceFirst("^\\s*(.*$)", "$1");
            line = line.replaceFirst("^(.*)\\s*$", "$1");
            if (line.length() == 0) continue; // skip empty lines
            if (line.matches("^#.+")) continue; // skip comment lines
            if (line.matches("^\\[.+\\]")) {
               key = line.replaceFirst("\\[", "");
               key = key.replaceFirst("\\]", "");
               if ( ! h.isEmpty() ) {
                  if (h.containsKey("share") && h.containsKey("path")) {
                     s.add(h);
                  }
                  h = new Hashtable<String,String>();
               }
               continue;               
            }
            if (key == null) continue;
            
            if (key.equalsIgnoreCase("server")) {
               if (line.matches("(?i)^port\\s*=.+")) {
                  String[] l = line.split("=");
                  if (l.length > 1) {
                     //config.pyTivo_port = string.removeLeadingTrailingSpaces(l[1]);
                  }
               }
               if (line.matches("(?i)^tivo_username\\s*=.+")) {
                  String[] l = line.split("=");
                  if (l.length > 1) {
                     username = string.removeLeadingTrailingSpaces(l[1]);
                  }
               }
               if (line.matches("(?i)^tivo_password\\s*=.+")) {
                  String[] l = line.split("=");
                  if (l.length > 1) {
                     password = string.removeLeadingTrailingSpaces(l[1]);
                  }
               }
               /*if (line.matches("(?i)^tivo_mind\\s*=.+")) {
                  String[] l = line.split("=");
                  if (l.length > 1) {
                     mind = string.removeLeadingTrailingSpaces(l[1]);
                  }
               }*/
               continue;
            }
            if (line.matches("(?i)^type\\s*=.+")) {
               if (line.matches("(?i)^.+=\\s*video.*")) {
                  if ( ! h.containsKey("share") ) {
                     h.put("share", key);
                  }
               }
               continue;
            }
            if (line.matches("(?i)^path\\s*=.+")) {
               String[] l = line.split("=");
               if (l.length > 1) {
                  String p = lowerCaseVolume(string.removeLeadingTrailingSpaces(l[1]));
                  char separator = File.separator.charAt(0);
                  if (p.charAt(p.length()-1) == separator) {
                     // Remove extra ending file separator
                     p = p.substring(0, p.length()-1);
                  }
                  h.put("path", p);
               }
            }
         }
         ifp.close();
         if ( ! h.isEmpty() ) {
            if (h.containsKey("share") && h.containsKey("path")) {
               s.add(h);
            }
         }
         
         // tivo_username & tivo_password are required for pushes to work
         if (username == null) {
            log.error("Required 'tivo_username' is not set in pyTivo config file: " + conf);
         }
         if (password == null) {
            log.error("Required 'tivo_password' is not set in pyTivo config file: " + conf);
         }
         if (username == null || password == null) {
            return null;
         }
         if (config.getTivoUsername() == null)
            config.setTivoUsername(username);
         if (config.getTivoPassword() == null)
            config.setTivoPassword(password);
         //if (mind != null)
         //   config.pyTivo_mind = mind;

      }
      catch (Exception ex) {
         log.error("Problem parsing pyTivo config file: " + conf);
         log.error(ex.toString());
         return null;
      }
      
      return s;
   }
   
   // For Windows lowercase file volume
   public static String lowerCaseVolume(String fileName) {
      String lowercased = fileName;
      if (config.OS.equals("windows")) {
         if (fileName.matches("^(.+):.*$") ) {
            String[] l = fileName.split(":");
            if (l.length > 0) {
               lowercased = l[0].toLowerCase() + ":";
               for (int i=1; i<l.length; i++) {
                  lowercased += l[i];
               }
            }
         }
      }
      return lowercased;
   }
      
   /*private static Hashtable<String,String> getShareInfo(String videoFile, Stack<Hashtable<String,String>> shares) {
      if (shares == null) {
         return null;
      }
      if (shares.size() == 0) {
         return null;
      }
      for (int i=0; i<shares.size(); ++i) {
         if (videoFile.startsWith(shares.get(i).get("path"))) {
            String shareDir = shares.get(i).get("path");
            String share = shares.get(i).get("share");
            String path = string.dirname(videoFile.substring(shareDir.length()+1, videoFile.length()));
            if (config.OS.equals("windows")) {
               path = path.replaceAll("\\\\", "/");
            }
            if (path.endsWith("/")) {
               path = path.substring(0,path.length()-1);
            }
            Hashtable<String,String> h = new Hashtable<String,String>();
            h.put("share", share);
            h.put("path", path);
            return h;
         }
      }
      return null;
   }*/
  
   public static Boolean streamFile(String tivoName, String videoFile) {
      videoFile = pyTivo.lowerCaseVolume(videoFile);

      /* Disabled because push disabled
      Stack<Hashtable<String,String>> shares = pyTivo.parsePyTivoConf(config.pyTivo_config);      
      if (shares == null) {
         log.error("No pyTivo video shares found in pyTivo config file: " + config.pyTivo_config);
         return false;
      }
      if (shares.size() == 0) {
         log.error("No pyTivo video shares found in pyTivo config file: " + config.pyTivo_config);
         return false;
      }
      // Check that file to be pushed resides under a pyTivo share
      Hashtable<String,String> h = getShareInfo(videoFile, shares);
      if ( h == null ) {
         log.error("This file is not located in a pyTivo share directory");
         log.error("Available pyTivo shares:");
         log.error(shares.toString());
         return false;
      }
      String share = urlEncode(h.get("share"));
      String path = urlEncode(h.get("path"));
      if (! path.startsWith("/"))
         path = "/" + path;
      String file = urlEncode(string.basename(videoFile));
      String shareString = "/" + share + path + "/" + file;

      String host = config.pyTivo_host;
      if (host.equals("localhost")) {
         host = http.getLocalhostIP();
         if (host == null)
            return false;
      }
      String mime = "video/x-tivo-mpeg";
      if (file.toLowerCase().endsWith(".mp4"))
         mime = "video/mp4";
      if (file.toLowerCase().endsWith(".tivo")) {
         log.error(".TiVo files not supported for streaming");
         return false;
      }
      
      // Get video info using ffmpeg and set mime
      int duration = 0;
      Hashtable<String,String> vInfo = ffmpeg.getVideoInfo(videoFile);
      if (vInfo != null) {
         if (vInfo.containsKey("container")) {
            String c = vInfo.get("container");
            if (! c.equals("mpeg") && ! c.equals("mp4")) {
               log.error("Unsupported container for streaming: " + c);
               return false;
            }
            if (c.equals("mp4"))
               mime = "video/mp4";
         }
         if (vInfo.containsKey("video")) {
            String v = vInfo.get("video");
            if (! v.equals("mpeg2video") && ! v.equals("h264")) {
               log.error("Unsupported video for streaming: " + v);
               return false;
            }
            if (v.equals("h264"))
               mime = "video/mp4";
         }
         if (vInfo.containsKey("duration")) {
            duration = Integer.parseInt(vInfo.get("duration"));
         }
      }
      
      // Get title, subtitle, description from pyTivo query
      JSONObject videoInfo = getVideoDetails(host, config.pyTivo_port, share, path, file);
      if (videoInfo == null) {
         log.error("Could not determine video information from pyTivo");
         return false;
      }
      
      try {
         String title = file;
         String subtitle = "";
         String description = "";
         if (videoInfo.has("program")) {
            JSONObject program = videoInfo.getJSONObject("program");
            if (program.has("title"))
               title = program.getString("title");
            if (program.has("subtitle"))
               subtitle = program.getString("subtitle");
            if (program.has("description"))
               description = program.getString("description");
         }
         String urlString = "http://" + host + ":" + config.pyTivo_port;
         urlString += shareString;
         urlString += "?Format=" + urlEncode(mime);
         log.print("using pyTivo url: " + urlString);
         
         Remote r = new Remote(tivoName);
         if (r.success) {
            JSONObject param = new JSONObject();
            param.put("uri", urlString);
            param.put("title", title);
            param.put("subtitle", subtitle);
            param.put("description", description);
            if (duration > 0)
               param.put("duration", duration);
            JSONObject json = new JSONObject();
            json.put("uiDestinationType", "hme");
            json.put("uri", "x-tivo:hme:uuid:863cb78f-efdd-4106-b572-51733983dc76");
            json.put("parameters", param);
            JSONObject result = r.Command("uiNavigate", json);
            if (result != null)
               log.print("Initiated stream of file:" + videoFile);
         }
      } catch (Exception e) {
         log.error("streamFile - " + e.getMessage());
         return false;
      }
      */
      return true;
   }
   
   /*private static String urlEncode(String s) {
      String encoded;
      try {
         encoded = URLEncoder.encode(s, "UTF-8");
         return encoded;
      } catch (Exception e) {
         log.error("Cannot encode url: " + s);
         log.error(e.toString());
         return s;
      }
   }*/
   
   // Get details on a specific video
   public static JSONObject getVideoDetails(String pyTivo_host, String pyTivo_port, String share, String path, String file) {
      String urlString = "http://" + pyTivo_host + ":" + pyTivo_port +
      "/TiVoConnect?Command=TVBusQuery&Container=";
      urlString += share + "&File=" + path + "/" + file;
      try {
         URL url = new URL(urlString);
         URLConnection con = url.openConnection();
         BufferedReader in = new BufferedReader( new InputStreamReader(con.getInputStream()));
         String line;
         String xml = "";
         while ((line = in.readLine()) != null) {
            xml += line + "\n";
         }
         in.close();
         if (xml.length() > 0) {
            JSONObject result = XML.toJSONObject(xml);
            if (result != null && result.has("TvBusMarshalledStruct:TvBusEnvelope")) {
               JSONObject json = result.getJSONObject("TvBusMarshalledStruct:TvBusEnvelope");
               if (json.has("showing"))
                  return json.getJSONObject("showing");
            }
         }
      } catch (Exception e) {
         log.error("getVideoDetails - " + e.getMessage());
      }

      return null;
   }

}
