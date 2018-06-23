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
package com.tivo.kmttg.rpc;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Random;
import java.util.Scanner;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;

import javafx.application.Platform;
import javafx.concurrent.Task;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.remote.util;
import com.tivo.kmttg.gui.sortable.sortableDuration;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class Remote {
   public Boolean debug = com.tivo.kmttg.util.debug.enabled;
   public String SchemaVersion = "14";
   public String SchemaVersion_newer = "17";
   public Boolean success = true;
   private String IP = null;
   private String cdata = null;
   private String tivoName = null;
   private Boolean away = false;
   private int port = 1413;
   private String MAK = null;
   private int timeout = 120; // read timeout in secs
   private int rpc_id = 0;
   private int session_id = 0;
   private SSLSocket socket = null;
   private DataInputStream in = null;
   private DataOutputStream out = null;
   private SSLSocketFactory sslSocketFactory = null;
   private int attempt = 0;
   
   public class NaiveTrustManager implements X509TrustManager {
     // Doesn't throw an exception, so this is how it approves a certificate.
     public void checkClientTrusted ( X509Certificate[] cert, String authType )
                 throws CertificateException {}

     // Doesn't throw an exception, so this is how it approves a certificate.
     public void checkServerTrusted ( X509Certificate[] cert, String authType ) 
        throws CertificateException {}

     public X509Certificate[] getAcceptedIssuers () {
        return new X509Certificate[0];
     }
   }
   
   public final void createSocketFactory() {
     if ( sslSocketFactory == null ) {
       try {
          KeyStore keyStore = KeyStore.getInstance("PKCS12");
          // This is default USA password
          String password = "5vPNhg6sV4tD"; // expires 12/18/2020
          //String password = "LwrbLEFYvG"; // expires 4/29/2018
          InputStream keyInput;
          if (cdata == null) {
             // Installation dir cdata.p12 file takes priority if it exists
             String cdata = config.programDir + "/cdata.p12";
             if ( file.isFile(cdata) ) {
                keyInput = new FileInputStream(cdata);
                cdata = config.programDir + "/cdata.password";
                if (file.isFile(cdata)) {
                   Scanner s = new Scanner(new File(cdata));
                   password = s.useDelimiter("\\A").next();
                   s.close();
                } else {
                   error("cdata.p12 file present, but cdata.password is not");
                }
             } else {
                // Read default USA cdata.p12 from kmttg.jar
                keyInput = getClass().getResourceAsStream("/cdata.p12");
             }
          }
          else
             keyInput = new FileInputStream(cdata);
          keyStore.load(keyInput, password.toCharArray());
          keyInput.close();
          KeyManagerFactory fac = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
          fac.init(keyStore, password.toCharArray());
          SSLContext context = SSLContext.getInstance("TLS");
          TrustManager[] tm = new TrustManager[] { new NaiveTrustManager() };
          context.init(fac.getKeyManagers(), tm, new SecureRandom());
          sslSocketFactory = context.getSocketFactory();
       } catch (KeyManagementException e) {
         error("KeyManagementException - " + e.getMessage()); 
       } catch (NoSuchAlgorithmException e) {
         error("NoSuchAlgorithmException - " + e.getMessage());
       } catch (KeyStoreException e) {
          error("KeyStoreException - " + e.getMessage());
       } catch (FileNotFoundException e) {
          error("FileNotFoundException - " + e.getMessage());
       } catch (CertificateException e) {
          error("CertificateException - " + e.getMessage());
       } catch (IOException e) {
          error("IOException - " + e.getMessage());
       } catch (UnrecoverableKeyException e) {
          error("UnrecoverableKeyException - " + e.getMessage());
       }
     }
   }
   
   // This constructor designed to be use by kmttg
   public Remote(String tivoName) {
      this.MAK = config.MAK;
      String IP = config.TIVOS.get(tivoName);
      if (IP == null)
         IP = tivoName;
      int use_port = port;
      String wan_port = config.getWanSetting(tivoName, "rpc");
      if (wan_port != null)
         use_port = Integer.parseInt(wan_port);

      RemoteInit(IP, use_port, MAK);
   }
   
   // This constructor designed to be use by kmttg
   public Remote(String tivoName, Boolean away) {
      this.tivoName = tivoName;
      this.away = away;
      this.MAK = config.MAK;
      String IP = config.middlemind_host;
      int port = config.middlemind_port;
      RemoteInit(IP, port, MAK);
   }
   
   // This constructor designed for use without kmttg config
   public Remote(String IP, int port, String MAK, String cdata) {
      this.MAK = MAK;
      this.cdata = cdata;
      RemoteInit(IP, port, MAK);
   }
   
   // This constructor designed for use without kmttg config
   public Remote(String tivoName, String IP, int port, String MAK, String cdata) {
      this.tivoName = tivoName;
      this.MAK = MAK;
      this.cdata = cdata;
      RemoteInit(IP, port, MAK);
   }
   
   private void RemoteInit(String IP, int port, String MAK) {
      this.IP = IP;
      this.port = port;
      createSocketFactory();
      session_id = new Random(0x27dc20).nextInt();
      try {
         socket = (SSLSocket) sslSocketFactory.createSocket(IP, port);
         socket.setNeedClientAuth(true);
         socket.setEnableSessionCreation(true);
         socket.setSoTimeout(timeout*1000);
         socket.startHandshake();
         in = new DataInputStream(socket.getInputStream());
         out = new DataOutputStream(socket.getOutputStream());
         if (IP.endsWith("tivo.com")) {
            if ( ! Auth_web() ) {
               success = false;
               return;
            }
         } else {
            if ( ! Auth() ) {
               success = false;
               return;
            }
            bodyId_get();
         }
      } catch (Exception e) {
         if (attempt == 0 && e.getMessage() != null && e.getMessage().contains("UNKNOWN ALERT")) {
            // Try it again as this could be temporary glitch
            attempt = 1;
            log.warn("RemoteInit 2nd attempt...");
            RemoteInit(IP, port, MAK);
            return;
         }
         error("RemoteInit - (IP=" + IP + ", port=" + port + "): " + e.getMessage());
         error(Arrays.toString(e.getStackTrace()));
         success = false;
      }
   }
   
   // NOTE: This retrieves and stores bodyId in config hashtable if not previously stored
   public String bodyId_get() {
      String id = config.bodyId_get(IP, port);
      if (id.equals("")) {
         JSONObject json = new JSONObject();
         try {
            json.put("bodyId", "-");
            JSONObject reply = Command("bodyConfigSearch", json);
            if (reply != null && reply.has("bodyConfig")) {
               json = reply.getJSONArray("bodyConfig").getJSONObject(0);
               if (json.has("bodyId")) {
                  id = json.getString("bodyId");
                  config.bodyId_set(IP, port, id);
               } else {
                  log.error("Failed to determine bodyId: IP=" + IP + " port=" + port);
               }
            }
         } catch (JSONException e) {
            log.error("bodyId_get failed - " + e.getMessage());
         }
      }
      if (id.equals(""))
         id = "-";
      return id;
   }
   
   public String RpcRequest(String type, Boolean monitor, JSONObject data) {
      try {
         String ResponseCount = "single";
         if (monitor)
            ResponseCount = "multiple";
         String bodyId = "";
         if (data.has("bodyId"))
            bodyId = (String) data.get("bodyId");
         String schema = SchemaVersion_newer;
         if (config.rpcOld == 1)
            schema = SchemaVersion;
         rpc_id++;
         String eol = "\r\n";
         String headers =
            "Type: request" + eol +
            "RpcId: " + rpc_id + eol +
            "SchemaVersion: " + schema + eol +
            "Content-Type: application/json" + eol +
            "RequestType: " + type + eol +
            "ResponseCount: " + ResponseCount + eol +
            "BodyId: " + bodyId + eol +
            "X-ApplicationName: Quicksilver" + eol +
            "X-ApplicationVersion: 1.2" + eol +
            String.format("X-ApplicationSessionId: 0x%x", session_id) + eol;
         data.put("type", type);

         String body = data.toString();
         String start_line = String.format("MRPC/2 %d %d", headers.length()+2, body.length());
         return start_line + eol + headers + eol + body + "\n";
      } catch (Exception e) {
         error("RpcRequest error: " + e.getMessage());
         return null;
      }
   }
   
   private Boolean Auth() {
      try {
         JSONObject credential = new JSONObject();
         JSONObject h = new JSONObject();
         credential.put("type", "makCredential");
         credential.put("key", MAK);
         h.put("credential", credential);
         String req = RpcRequest("bodyAuthenticate", false, h);
         if (Write(req) ) {
            JSONObject result = Read();
            if (result.has("status")) {
               if (result.get("status").equals("success"))
                  return true;
            }
         }
      } catch (Exception e) {
         error("rpc Auth error - " + e.getMessage());
      }
      return false;
   }
   
   private Boolean Auth_web() {
      try {
         if (config.getTivoUsername() == null) {
            log.error("tivo.com username & password not set in kmttg or pyTivo config");
            return false;
         }

         JSONObject credential = new JSONObject();
         JSONObject h = new JSONObject();
         credential.put("type", "mmaCredential");
         credential.put("username", config.getTivoUsername());
         credential.put("password", config.getTivoPassword());
         h.put("credential", credential);
         String req = RpcRequest("bodyAuthenticate", false, h);
         if (Write(req) ) {
            JSONObject result = Read();
            if (result.has("status")) {
               if (result.get("status").equals("success")) {
                  // Look for tivoName bodyId in deviceId JSONArray
                  Boolean found = false;
                  if (result.has("deviceId")) {
                     JSONArray a = result.getJSONArray("deviceId");
                     for (int i=0; i<a.length(); ++i) {
                        JSONObject j = a.getJSONObject(i);
                        if (j.has("friendlyName")) {
                           if (j.getString("friendlyName").equals(tivoName) && j.has("id")) {
                              found = true;
                              config.bodyId_set(IP, port, j.getString("id"));
                              if (config.getTsn(tivoName) == null) {
                                 String tsn = j.getString("id");
                                 tsn = tsn.replaceFirst("tsn:", "");
                                 config.setTsn(tivoName, tsn);
                              }
                           }
                        }
                     }
                  }
                  if (! found) {
                     // Couldn't get id from response so try getting tsn from kmttg
                     String tsn = config.getTsn(tivoName);
                     if (tsn == null) {
                        log.error("Can't determine bodyId for TiVo: " + tivoName);
                        return null;
                     }
                     config.bodyId_set(IP, port, "tsn:" + tsn);
                  }
                  return true;
               }
            }
         }
      } catch (Exception e) {
         error("rpc Auth error - " + e.getMessage());
      }
      return false;
   }
   
   public Boolean Write(String data) {
      try {
         if (debug) {
            print("WRITE: " + data);
         }
         if (out == null)
            return false;
         out.write(data.getBytes());
         out.flush();
      } catch (IOException e) {
         error("rpc Write error - " + e.getMessage());
         return false;
      }
      return true;
   }
   
   @SuppressWarnings("deprecation")
   public JSONObject Read() {
      String buf = "";
      Integer head_len;
      Integer body_len;
      
      try {
         // Expect line of format: MRPC/2 76 1870
         // 1st number is header length, 2nd number body length
         buf = in.readLine();
         if (debug) {
            print("READ: " + buf);
         }
         if (buf != null && buf.matches("^.*MRPC/2.+$")) {
            String[] split = buf.split(" ");
            head_len = Integer.parseInt(split[1]);
            body_len = Integer.parseInt(split[2]);
            
            byte[] headers = new byte[head_len];
            readBytes(headers, head_len);
   
            byte[] body = new byte[body_len];
            readBytes(body, body_len);
            
            if (debug) {
               print("READ: " + new String(headers) + new String(body));
            }
            
            // Pull out IsFinal value from header
            Boolean IsFinal;
            buf = new String(headers, "UTF8");
            if (buf.contains("IsFinal: true"))
               IsFinal = true;
            else
               IsFinal = false;
            
            // Return json contents with IsFinal flag added
            buf = new String(body, "UTF8");
            JSONObject j = new JSONObject(buf);
            if (j.has("type") && j.getString("type").equals("error")) {
               error("RPC error response:\n" + j.toString(3));
               if (j.has("text") && j.getString("text").equals("Unsupported schema version")) {
                  // Revert to older schema version for older TiVo software versions
                  log.warn("Reverting to older RPC schema version - try command again.");
                  config.rpcOld = 1;
                  config.save();
               }
               return null;
            }
            j.put("IsFinal", IsFinal);
            return j;

         }         
      } catch (Exception e) {
         error("rpc Read error - " + e.getMessage());
         return null;
      }
      return null;
   }
   
   public Boolean awayMode() {
      return away;
   }
   
   private void readBytes(byte[] body, int len) throws IOException {
      int bytesRead = 0;
      while (bytesRead < len) {
         bytesRead += in.read(body, bytesRead, len - bytesRead);
      }
   }
   
   public void disconnect() {
      try {
         if (out != null) out.close();
         if (in != null) in.close();
      } catch (IOException e) {
         error("rpc disconnect error - " + e.getMessage());
      }
   }
   
   // RPC command set
   // NOTE: By convention upper case commands are ones for which I have
   // wrappers in place, lower case are native RPC calls
   public JSONObject Command(String type, JSONObject json) {
      String req = null;
      if (json == null)
         json = new JSONObject();
      try {
         if (type.equals("Help")) {            
            // Query middlemind.tivo.com for syntax of a particular RPC command
            // Expects RPC command name as "name" in json, such as "keyEventSend"
            if (! json.has("levelOfDetail"))
               json.put("levelOfDetail", "high");
            req = RpcRequest("schemaElementGet", false, json);
         }
         else if (type.equals("Playback")) {
            // Play an existing recording
            // Expects "id" in json
            json.put("uri", "x-tivo:classicui:playback");
            JSONObject parameters = new JSONObject();
            parameters.put("fUseTrioId", "true");
            parameters.put("recordingId", json.get("id"));
            parameters.put("fHideBannerOnEnter", "true");
            json.remove("id");
            json.put("parameters", parameters);
            req = RpcRequest("uiNavigate", false, json);
         }
         else if (type.equals("Flash")) {
            // Run a flash swf (ActionScript 2)
            // Expects swf uri in json.
            // Example: http://www.bbc.co.uk/science/humanbody/sleep/sheep/reaction_version5.swf
            json.put("bodyId", bodyId_get());
            json.put("uiDestinationType", "flash");
            json.put("uri", "x-tivo:flash:" + json.getString("uri"));
            req = RpcRequest("uiNavigate", false, json);
         }
         else if (type.equals("Uidestinations")) {
            // List available uri destinations for uiNavigate
            json.put("bodyId", bodyId_get());
            json.put("uiDestinationType", "classicui");
            json.put("levelOfDetail", "high");
            json.put("noLimit", "true");
            req = RpcRequest("uiDestinationInstanceSearch", false, json);
         }
         else if (type.equals("Navigate")) {
            // Navigation command - expects uri in json
            req = RpcRequest("uiNavigate", false, json);
         }
         else if (type.equals("Hmedestinations")) {
            // List available hme destinations for uiNavigate
            //json.put("bodyId", bodyId_get());
            json.put("uiDestinationType", "hme");
            json.put("levelOfDetail", "high");
            json.put("noLimit", "true");
            req = RpcRequest("uiDestinationInstanceSearch", false, json);
         }
         else if (type.equals("Flashdestinations")) {
            // List available flash destinations for uiNavigate
            //json.put("bodyId", bodyId_get());
            json.put("uiDestinationType", "flash");
            json.put("levelOfDetail", "high");
            json.put("noLimit", "true");
            req = RpcRequest("uiDestinationInstanceSearch", false, json);
         }
         else if (type.equals("Delete")) {
            // Delete an existing recording
            // Expects "recordingId" of type JSONArray in json
            json.put("state", "deleted");
            json.put("bodyId", bodyId_get());
            req = RpcRequest("recordingUpdate", false, json);
         }
         else if (type.equals("Undelete")) {
            // Recover a recording from Recently Deleted
            // Expects "recordingId" of type JSONArray in json
            json.put("state", "complete");
            json.put("bodyId", bodyId_get());
            req = RpcRequest("recordingUpdate", false, json);
         }
         else if (type.equals("PermanentlyDelete")) {
            // Permanently delete an existing recording (usually from Recently Deleted)
            // Expects "recordingId" of type JSONArray in json
            json.put("state", "contentDeleted");
            json.put("bodyId", bodyId_get());
            req = RpcRequest("recordingUpdate", false, json);
         }
         else if (type.equals("StopRecording")) {
            // Stop an existing recording
            // Expects "recordingId" of type JSONArray in json
            json.put("state", "complete");
            json.put("bodyId", bodyId_get());
            req = RpcRequest("recordingUpdate", false, json);
         }
         else if (type.equals("SetBookmark")) {
            // Set bookmark position for a recording
            // Expects "recordingId" of type JSONArray in json
            // Expects "bookmarkPosition" of type JSONInteger in json
            json.put("bodyId", bodyId_get());
            req = RpcRequest("recordingUpdate", false, json);
         }
         else if (type.equals("Cancel")) {
            // Cancel a recording in ToDo list
            // Expects "recordingId" of type JSONArray in json
            json.put("state", "cancelled");
            json.put("bodyId", bodyId_get());
            req = RpcRequest("recordingUpdate", false, json);
         }
         else if (type.equals("Prioritize")) {
            // Re-prioritize season passes
            // Expects JSONArray of all SP's "subscriptionIdV2" in the order you want them
            json.put("bodyId", bodyId_get());
            req = RpcRequest("subscriptionsReprioritize", false, json);
         }
         else if (type.equals("Search")) {
            // Individual item search
            // Expects "recordingId" in json
            json.put("levelOfDetail", "medium");
            json.put("bodyId", bodyId_get());
            req = RpcRequest("recordingSearch", false, json);
         }
         else if (type.equals("FolderIds")) {
            // Folder id search
            // Expects "parentRecordingFolderItemId" in json
            json.put("format", "idSequence");
            json.put("bodyId", bodyId_get());
            // NOTE: Since this format is idSequence perhaps monitor should be true?
            req = RpcRequest("recordingFolderItemSearch", false, json);
         }
         else if (type.equals("SearchIds")) {
            // Expects "objectIdAndType" in json
            json.put("bodyId", bodyId_get());
            req = RpcRequest("recordingFolderItemSearch", false, json);
         }
         else if (type.equals("MyShows")) {
            // Expects count=# in initial json, offset=# after first call
            json.put("bodyId", bodyId_get());
            if (away) {
               json.put("levelOfDetail", "medium");
               req = RpcRequest("recordingSearch", false, json);
            } else {
               req = RpcRequest("recordingFolderItemSearch", false, json);
            }
         }
         else if (type.equals("ToDo")) {
            // Get list of recordings that are expected to record
            // Expects count=# in initial json, offset=# after first call
            json.put("bodyId", bodyId_get());
            json.put("levelOfDetail", "medium");
            json.put("state", new JSONArray("[\"inProgress\",\"scheduled\"]"));
            req = RpcRequest("recordingSearch", false, json);
         }
         else if (type.equals("SearchId")) {
            // Expects "objectIdAndType" in json
            json.put("bodyId", bodyId_get());
            json.put("levelOfDetail", "medium");
            req = RpcRequest("recordingSearch", false, json);
         }
         else if (type.equals("Cancelled")) {
            // Get list of recordings that will not record
            // Expects count=# in initial json, offset=# after first call
            json.put("bodyId", bodyId_get());
            json.put("levelOfDetail", "medium");
            json.put("state", new JSONArray("[\"cancelled\"]"));
            req = RpcRequest("recordingSearch", false, json);
         }
         else if (type.equals("Deleted")) {
            // Get list or recordings that are in Recently Deleted
            // Expects count=# in initial json, offset=# after first call
            json.put("bodyId", bodyId_get());
            json.put("levelOfDetail", "medium");
            json.put("state", new JSONArray("[\"deleted\"]"));
            req = RpcRequest("recordingSearch", false, json);
         }
         else if (type.equals("GridSearch")) {
            // Search for future recordings (12 days from now)
            // Expects extra search criteria in json, such as:
            // "title":"The Voice"
            // "anchorChannelIdentifier":{"channelNumber":"761","type":"channelIdentifier","sourceType":"cable"}
            json.put("bodyId", bodyId_get());
            json.put("levelOfDetail", "medium");
            //json.put("isReceived", "true");
            json.put("orderBy", new JSONArray("[\"channelNumber\"]"));
            req = RpcRequest("gridRowSearch", false, json);
         }
         else if (type.equals("OfferSearch")) {
            // Keyword search
            // Expects "keyword" JSON object in json, such as:
            // "keyword":"house"
            // Also expects "count" and "offset" to be set
            json.put("bodyId", bodyId_get());
            if ( ! away && ! json.has("includeUnifiedItemType") ) {
               JSONArray a = new JSONArray();
               a.put("collection"); a.put("content"); a.put("person");
               json.put("includeUnifiedItemType", a);               
            }
            json.put("levelOfDetail", "medium");
            if ( ! away )
               json.put("mergeOverridingCollections", true);
            json.put("namespace", "refserver");
            if ( ! json.has("orderBy") && ! json.has("credit") )
               json.put("orderBy", new JSONArray("[\"relevance\"]"));
            json.put("searchable", true);
            Date now = new Date();
            json.put("minStartTime", rnpl.getStringFromLongDate(now.getTime()));
            req = RpcRequest("offerSearch", false, json);
         }
         else if (type.equals("SeasonPasses")) {
            json.put("levelOfDetail", "medium");
            json.put("bodyId", bodyId_get());
            json.put("noLimit", "true");
            req = RpcRequest("subscriptionSearch", false, json);
         }
         else if (type.equals("Seasonpass")) {
            // Subscribe a season pass
            // Expects several fields in json, (levelOfDetail=medium)
            // Usually this will be JSONObject read from a JSONArray of all
            // season passes that were saved to a file
            JSONObject o = new JSONObject();
            if (json.has("recordingQuality"))
               o.put("recordingQuality", json.getString("recordingQuality"));
            if (json.has("maxRecordings"))
               o.put("maxRecordings", json.getInt("maxRecordings"));
            if (json.has("keepBehavior"))
               o.put("keepBehavior", json.getString("keepBehavior"));
            if (json.has("idSetSource"))
               o.put("idSetSource", json.getJSONObject("idSetSource"));
            if (json.has("showStatus"))
               o.put("showStatus", json.getString("showStatus"));
            if (json.has("endTimePadding"))
               o.put("endTimePadding", json.getInt("endTimePadding"));
            if (json.has("startTimePadding"))
               o.put("startTimePadding", json.getInt("startTimePadding"));
            if (json.has("hdPreference"))
               o.put("hdPreference", json.getString("hdPreference"));
            if (json.has("hdOnly"))
               o.put("hdOnly", json.getBoolean("hdOnly"));
            // These are required for wishlist types
            // NOTE: Advanced wishlist SPs don't contain idSetSource so don't work
            // NOTE: title is not needed for type=seasonPassSource and titles with special
            // characters can prevent scheduling from working, so don't use title for this type
            String wltype = null;
            if (json.has("idSetSource")) {
               JSONObject temp = json.getJSONObject("idSetSource");
               if (temp.has("wishlistId"))
                  temp.remove("wishlistId");
               if (temp.has("type"))
                  wltype = temp.getString("type");
            }            
            if (json.has("title") && ! wltype.equals("seasonPassSource"))
               o.put("title", json.getString("title"));
            if (json.has("folderingRules"))
               o.put("folderingRules", json.getString("folderingRules"));
            o.put("bodyId", bodyId_get());
            o.put("ignoreConflicts", "true");
            // This option allows season passes not currently in guide to be scheduled
            //o.put("bodyGeneratesCandidates", true);
            req = RpcRequest("subscribe", false, o);
         }
         else if (type.equals("ChannelUpdate")) {
            // Update a channel isReceived/isFavorite settings
            // Expects a channel info json with info such as example below
            // "type" : "channel"
            // "channelNumber" : "298"
            // "sourceType" : "cable"
            // "stationId" : "tivo:st.67251839"
            // "isReceived" : false
            // "isFavorite" : false
            
            // fieldNumber map: 16=isFavorite, 19=isReceived, 29=isBlocked
            //JSONObject favorite = new JSONObject();
            //favorite.put("type", "updateTemplate");
            //favorite.put("fieldNumber", 16);
            JSONObject received = new JSONObject();
            received.put("type", "updateTemplate");
            received.put("fieldNumber", 19);
            
            JSONArray template = new JSONArray();
            //template.put(favorite);
            template.put(received);
            
            JSONObject o = new JSONObject();
            o.put("bodyId", bodyId_get());
            o.put("channel", json);
            o.put("updateTemplate", template);
            req = RpcRequest("channelUpdate", false, o);
         }
         else if (type.equals("ModifySP")) {
            // Modify a season pass
            // Expects several fields in json, (levelOfDetail=medium)
            // NOTE: Expects collectionId inside idSetSource
            // NOTE: Expects subscriptionId of existing SP
            JSONObject o = new JSONObject();
            o.put("recordingQuality", json.getString("recordingQuality"));
            o.put("maxRecordings", json.getInt("maxRecordings"));
            o.put("keepBehavior", json.getString("keepBehavior"));
            o.put("showStatus", json.getString("showStatus"));
            o.put("endTimePadding", json.getInt("endTimePadding"));
            o.put("startTimePadding", json.getInt("startTimePadding"));
            o.put("idSetSource", json.getJSONObject("idSetSource"));
            o.put("subscriptionId", json.getString("subscriptionId"));
            if (json.has("hdPreference"))
               o.put("hdPreference", json.getString("hdPreference"));
            if (json.has("hdOnly"))
               o.put("hdOnly", json.getBoolean("hdOnly"));
            if (json.has("title"))
               o.put("title", json.getString("title"));
            if (json.has("folderingRules"))
               o.put("folderingRules", json.getString("folderingRules"));
            o.put("bodyId", bodyId_get());
            o.put("ignoreConflicts", "true");
            req = RpcRequest("subscribe", false, o);
         }
         else if (type.equals("Singlerecording")) {
            // Subscribe a single recording
            // Expects both contentId & offerId in json:
            JSONObject o = new JSONObject();
            json.put("type", "singleOfferSource");
            o.put("bodyId", bodyId_get());
            o.put("idSetSource", json);
            o.put("recordingQuality", "best");
            o.put("maxRecordings", 1);
            o.put("ignoreConflicts", "false");
            if ( json.has("keepBehavior"))
               o.put("keepBehavior", json.getString("keepBehavior"));
            else
               o.put("keepBehavior", "fifo");
            if (json.has("endTimePadding"))
               o.put("endTimePadding", json.getInt("endTimePadding"));
            if (json.has("startTimePadding"))
               o.put("startTimePadding", json.getInt("startTimePadding"));
            // conflictsOnly=true => don't actually subscribe, but check for conflicts
            if (json.has("conflictsOnly")) {
               o.put("conflictsOnly", json.get("conflictsOnly"));
               json.remove("conflictsOnly");
            }
            req = RpcRequest("subscribe", false, o);
         }
         else if (type.equals("Manual")) {
            // Create a manual recording
            // Expects json such as following:
            // repeating - "idSetSource":
            //   "duration":1800,"timeOfDayLocal":"02:00:00","type":"repeatingTimeChannelSource",
            //   "channel":{channel info},"dayOfWeek":["monday","tuesday","wednesday","thursday","friday"]
            // single - "idSetSource":
            //   "duration":1800,"time":"2012-11-16 09:30:00","type":"singleTimeChannelSource",
            //   "channel":{channel info}
            JSONObject o = new JSONObject();
            o.put("idSetSource", json);
            o.put("bodyId", bodyId_get());
            req = RpcRequest("subscribe", false, o);
         }
         else if (type.equals("Wishlist")) {
            // Create an auto-record wishlist
            // Expects json such as following which will be idSetSource:
            // "title":"LAKERS", (This is required)
            // "keywordOp":["required"],"keyword":["LAKERS"],
            // "titleKeywordOp":["required"],"titleKeyword":["NBA BASKETBALL"],
            // Op types: required, optional, not
            // Other: creditOp,credit,categoryId
            // "creditOp":["required","required"],
            // "credit":[
            //   {"last":"Kline","first":"Kevin","role":"actor","type":"credit"}
            //   {"last":"Eastwood","first":"Clint","role":"director","type":"credit"}
            // ]
            JSONObject o = new JSONObject();
            json.put("type", "wishListSource");
            o.put("bodyId", bodyId_get());
            o.put("title", json.getString("title"));
            json.remove("title");
            o.put("ignoreConflicts", "true");
            if (json.has("autoRecord")) {
               o.put("autoRecord", json.getBoolean("autoRecord"));
               json.remove("autoRecord");
            } else
               o.put("autoRecord", false);
            
            if (o.getBoolean("autoRecord")) {
               o.put("recordingQuality", "best");
               if (json.has("maxRecordings")) {
                  o.put("maxRecordings", json.getInt("maxRecordings"));
                  json.remove("maxRecordings");
               } else
                  o.put("maxRecordings", 5);
               if ( json.has("keepBehavior")) {
                  o.put("keepBehavior", json.getString("keepBehavior"));
                  json.remove("keepBehavior");
               } else
                  o.put("keepBehavior", "fifo");
               if (json.has("showStatus")) {
                  o.put("showStatus", json.getString("showStatus"));
                  json.remove("showStatus");
               } else {
                  o.put("showStatus", "firstRunOnly");
               }
               if (json.has("endTimePadding")) {
                  o.put("endTimePadding", json.getInt("endTimePadding"));
                  json.remove("endTimePadding");
               }
               if (json.has("startTimePadding")) {
                  o.put("startTimePadding", json.getInt("startTimePadding"));
                  json.remove("startTimePadding");
               }
            }
            o.put("idSetSource", json);
            req = RpcRequest("subscribe", false, o);
         }
         else if (type.equals("Unsubscribe")) {
            // Unsubscribe a season pass
            // Expects subscriptionId in json
            json.put("bodyId", bodyId_get());
            req = RpcRequest("unsubscribe", false, json);
         }
         else if (type.equals("ContentLocatorStore")) {
            // Add a streaming partner content locator (bookmark) to My Shows
            // Expects both contentId & collectionId in json:
            json.put("bodyId", bodyId_get());
            req = RpcRequest("contentLocatorStore", false, json);
         }
         else if (type.equals("ContentLocatorRemove")) {
            // Remove a streaming partner content locator (bookmark) to My Shows
            // Expects contentId in json:
            json.put("bodyId", bodyId_get());
            req = RpcRequest("contentLocatorRemove", false, json);
         }
         else if (type.equals("Position")) {
            json.put("throttleDelay", 1000);
            req = RpcRequest("videoPlaybackInfoEventRegister", false, json);
         }
         else if (type.equals("Jump")) {
            // Expects "offset" in json
            req = RpcRequest("videoPlaybackPositionSet", false, json);
         }
         else if (type.equals("SysInfo")) {
            // Returns userDiskSize among other info
            json.put("bodyId", bodyId_get());
            req = RpcRequest("bodyConfigSearch", false, json);
         }
         else if (type.equals("TunerInfo")) {
            // Returns info about both tuners
            req = RpcRequest("tunerStateEventRegister", true, json);
         }
         else if (type.equals("PhoneHome")) {
            // Request a network connection
            json.put("bodyId", bodyId_get());
            if (away)
               req = RpcRequest("phoneHomeSend", false, json);
            else
               req = RpcRequest("phoneHomeRequest", true, json);
         }
         else if (type.equals("WhatsOn")) {
            // Request info on what is currently playing on the TiVo
            json.put("bodyId", bodyId_get());
            req = RpcRequest("whatsOnSearch", true, json);
         }
         else {
            // Not recognized => just use type
            req = RpcRequest(type, false, json);
         }
         
         if (req != null) {
            if ( Write(req) )
               return Read();
            else
               return null;
         } else {
            error("rpc: unhandled Key type: " + type);
            return null;
         }
      } catch (JSONException e) {
         error("rpc Key error - " + e.getMessage());
         return null;
      }
   }
   
   // Get flat list of all shows
   public JSONArray MyShows(jobData job) {
      Hashtable<String,Integer> unique = new Hashtable<String,Integer>();
      Hashtable<String,Integer> collections = new Hashtable<String,Integer>();
      JSONArray allShows = new JSONArray();
      JSONObject result = null;
      Boolean stop = false;
      int count = 25;
      int offset = 0;
      int limit_npl_fetches = 0;
      if (job != null)
         limit_npl_fetches = config.getLimitNplSetting(job.tivoName);
      int fetchCount = 0;

      try {
         JSONObject json = new JSONObject();
         //json.put("count", count);
         json.put("flatten", true);
         JSONArray items = new JSONArray();
         while (! stop) {
            json.put("offset", offset);
            json.put("count", 25);
            result = Command("MyShows", json);
            if (result != null && result.has("recordingFolderItem")) {
               JSONArray a = result.getJSONArray("recordingFolderItem");
               count = a.length();
               for (int i=0; i<a.length(); ++i) {
                  JSONObject j = a.getJSONObject(i);
                  // Single item
                  String id = j.getString("childRecordingId");
                  if (! unique.containsKey(id))
                     items.put(j);
               } // for i
               if (count == 0)
                  stop = true;
            } else {
               stop = true;
            }
            offset += count;
            fetchCount++;
            if (limit_npl_fetches > 0 && fetchCount >= limit_npl_fetches) {
               log.warn(job.tivoName + ": Further NPL listings not obtained due to fetch limit=" + limit_npl_fetches + " exceeded.");
               stop = true;
            }
         } // while
         if (job != null && config.GUIMODE) {
            String c = "0/" + items.length();
            config.gui.jobTab_UpdateJobMonitorRowOutput(job, "NP List: " + c);
            if ( jobMonitor.isFirstJobInMonitor(job) )
               config.gui.setTitle("playlist: " + c + " " + config.kmttg);
         }
         
         // items contains unique flat list of ids to search for
         count = 0;
         int total = items.length();
         for (int k=0; k<items.length(); ++k) {
            JSONObject item = items.getJSONObject(k);
            String id = item.getString("childRecordingId");
            result = Command(
               "Search",
               new JSONObject("{\"recordingId\":\"" + id + "\"}")
            );
            if (result != null && result.has("recording")) {
               JSONObject entry = result.getJSONArray("recording").getJSONObject(0);
               if (job != null && job.getURLs) {
                  if (!getURLs(job.tivoName, entry)) {
                     return null;
                  }
               }
               // For series types saved collectionId in collections so as to get seriesId later
               if (entry.has("isEpisode") && entry.getBoolean("isEpisode")) {
                  if (entry.has("collectionId")) {
                     String s = entry.getString("collectionId");
                     if ( ! collections.containsKey(s) )
                        collections.put(s, 1);
                  }
               }
               allShows.put(result);
            } else {
               stop = true;
            }
            count++;
            if (job != null && config.GUIMODE && count % 25 == 0) {
               String c = "" + allShows.length() + "/" + total;
               config.gui.jobTab_UpdateJobMonitorRowOutput(job, "NP List: " + c);
               if ( jobMonitor.isFirstJobInMonitor(job) )
                  config.gui.setTitle("playlist: " + c + " " + config.kmttg);
            }
         } // for k
      } catch (JSONException e) {
         error("rpc MyShows error - " + e.getMessage());
         return null;
      }
      
      // Process collections to efficiently get seriesId information
      if (collections.size() > 0)
         addSeriesID(allShows, collections);

      return allShows;
   }
   
   // Get flat list of all shows with partiallyViewed filter enabled
   public JSONArray MyShowsWatched(jobData job) {
      JSONArray allShows = new JSONArray();
      Hashtable<String,Integer> collections = new Hashtable<String,Integer>();
      try {
         JSONObject json = new JSONObject();
         json.put("bodyId", bodyId_get());
         JSONObject filter = new JSONObject();
         filter.put("type", "recordingFilter");
         filter.put("filterType", "partiallyViewed");
         filter.put("active", true);
         json.put("filter", filter);
         json.put("format", "idSequence");
         JSONObject result = Command("recordingFolderItemSearch", json);
         if (result != null && result.has("objectIdAndType")) {
            JSONArray ids = result.getJSONArray("objectIdAndType");
            if (job != null && config.GUIMODE) {
               String c = "" + ids.length() + " shows";
               config.gui.jobTab_UpdateJobMonitorRowOutput(job, "NP List: " + c);
               if ( jobMonitor.isFirstJobInMonitor(job) )
                  config.gui.setTitle("playlist: " + c + " " + config.kmttg);
            }
            int max = 25; // limit SearchIds queries to at most 25 at a time
            int index = 0;
            while (index < ids.length()) {
               JSONArray a = new JSONArray();
               for (int i=0; i<max; ++i) {
                  if (index < ids.length())
                     a.put(ids.get(index));
                  index++;
               }
               JSONObject j = new JSONObject();
               j.put("objectIdAndType", a);
               result = Command("SearchIds", j);
               if (result != null && result.has("recordingFolderItem")) {
                  JSONArray entries = result.getJSONArray("recordingFolderItem");
                  for (int i=0; i<entries.length(); ++i) {
                     j = entries.getJSONObject(i);
                     if (j.has("childRecordingId")) {
                        JSONObject jj = new JSONObject();
                        jj.put("recordingId", j.getString("childRecordingId"));
                        result = Command("Search", jj);
                        if (result != null && result.has("recording")) {
                           JSONObject entry = result.getJSONArray("recording").getJSONObject(0);
                           if (job != null && job.getURLs) {
                              if (!getURLs(job.tivoName, entry)) {
                                 return null;
                              }
                           }
                           // For series types saved collectionId in collections so as to get seriesId later
                           if (entry.has("isEpisode") && entry.getBoolean("isEpisode")) {
                              if (entry.has("collectionId")) {
                                 String s = entry.getString("collectionId");
                                 if ( ! collections.containsKey(s) )
                                    collections.put(s, 1);
                              }
                           }
                           allShows.put(result);
                        }
                     }
                  }
               }
               String c = "" + allShows.length() + "/" + ids.length();
               config.gui.jobTab_UpdateJobMonitorRowOutput(job, "NP List: " + c);
               if ( jobMonitor.isFirstJobInMonitor(job) )
                  config.gui.setTitle("playlist: " + c + " " + config.kmttg);
            } // while
         }         
      } catch (JSONException e) {
         error("rpc MyShowsWatched error - " + e.getMessage());
         return null;
      }
      
      // Process collections to efficiently get seriesId information
      if (collections.size() > 0)
         addSeriesID(allShows, collections);
      
      return allShows;      
   }
   
   // Find mfs id based on RPC recordingId and then build equivalent
   // traditional TTG URLs based on the mfs id.
   // This is needed when obtaining NPL listings using only RPC which
   // doesn't have the TTG URLs in JSON data.
   private Boolean getURLs(String tivoName, JSONObject json) {
      try {
         JSONObject j = new JSONObject();
         j.put("bodyId", bodyId_get());
         j.put("namespace", "mfs");
         j.put("objectId", json.getString("recordingId"));
         JSONObject result = Command("idSearch", j);
         if (result != null) {
            if (result.has("objectId")) {
               String id = result.getJSONArray("objectId").getString(0);
               id = id.replaceFirst("mfs:rc\\.", "");
               String ip = config.TIVOS.get(tivoName);
               String port_http = config.getWanSetting(tivoName, "http");
               if (port_http == null)
                  port_http = "80";
               String port_https = config.getWanSetting(tivoName, "https");
               if (port_https == null)
                  port_https = "443";
               String fname = URLEncoder.encode(id, "UTF-8");
               if (json.has("title"))
                  fname = URLEncoder.encode(json.getString("title"), "UTF-8");
               String url = "http://" + ip + ":" + port_http + "/download/" +
                  fname + ".TiVo?Container=%2FNowPlaying&id=" + id;
               String url_details = "https://" + ip + ":" + port_https +
                  "/TiVoVideoDetails?id=" + id;
               json.put("__url__", url);
               json.put("__url_TiVoVideoDetails__", url_details);
               return true;
            }
         }         
      }
      catch (Exception e) {
         log.error("Remote getURLs - " + e.getMessage());
      }
      log.error("Remote getURLs - failed to retrieve mfs URLs");
      return false;
   }
   
   // Add seriesID information to MyShows data based on collectionSearch data
   private void addSeriesID(JSONArray allShows, Hashtable<String,Integer> collections) {
      try {
         JSONArray ids = new JSONArray();
         Hashtable<String,String> map = new Hashtable<String,String>();
         for (String collectionId : collections.keySet())
            ids.put(collectionId);
         int max = 25; // Limit searches to 25 at a time
         int index = 0;
         JSONArray a = new JSONArray();
         while (index < ids.length()) {
            if (a.length() >= max) {
               // Limit reached, so search and then empty a
               addToCollectionMap(a, map);
               a = new JSONArray();
            }
            a.put(ids.getString(index));
            index++;
         }
         // Search for any remaining entries
         if (a.length() > 0)
            addToCollectionMap(a, map);
         
         if (map.size() > 0) {
            for (String collectionId : map.keySet()) {
               for (int i=0; i<allShows.length(); ++i) {
                  JSONObject json = allShows.getJSONObject(i);
                  JSONObject entry = json.getJSONArray("recording").getJSONObject(0);
                  if (entry.has("collectionId") && entry.getString("collectionId").equals(collectionId)) {
                     entry.put("__SeriesId__", map.get(collectionId));
                  }
               }
            }
         }
      } catch (JSONException e) {
         log.error("Remote addSeriesID - " + e.getMessage());
      }
   }
   
   private void addToCollectionMap(JSONArray a, Hashtable<String,String> map) {
      try {
         JSONObject json = new JSONObject();
         json.put("count", a.length());
         json.put("collectionId", a);
         JSONObject result = Command("collectionSearch", json);
         if (result != null && result.has("collection")) {
            JSONArray items = result.getJSONArray("collection");
            for (int i=0; i<items.length(); ++i) {
               JSONObject j = items.getJSONObject(i);
               if (j.has("partnerCollectionId")) {
                  String sid = j.getString("partnerCollectionId");
                  sid = sid.replaceFirst("epgProvider:cl\\.", "");
                  map.put(j.getString("collectionId"), sid);
               }
            }
         }
      } catch (JSONException e) {
         log.error("Remote addToCollectionMap - " + e.getMessage());
      }
      
   }
   
   // Get list of all shows (drilling down into folders for individual shows)
   public JSONArray MyShowsS3(jobData job) {
      JSONArray allShows = new JSONArray();
      JSONObject result = null;

      try {
         // Top level list - run in a loop to grab all items
         Boolean stop = false;
         int offset = 0;
         JSONObject json = new JSONObject();
         json.put("count", 50);
         while ( ! stop ) {
            if (job != null && config.GUIMODE) {
               config.gui.jobTab_UpdateJobMonitorRowOutput(job, "NP List: " + allShows.length());
               if ( jobMonitor.isFirstJobInMonitor(job) )
                  config.gui.setTitle("playlist: " + allShows.length() + " " + config.kmttg);
            }
            result = Command("MyShows", json);
            if (result != null && result.has("recording")) {
               JSONArray items = (JSONArray) result.get("recording");
               offset += items.length();
               json.put("offset", offset);
               if (items.length() == 0)
                  stop = true;
               for (int i=0; i<items.length(); ++i) {
                  allShows.put(items.getJSONObject(i));
               } // for
            } else {
               // result == null
               stop = true;
            } // if
         } // while
      } catch (JSONException e) {
         error("rpc MyShowsS3 error - " + e.getMessage());
         return null;
      }

      return allShows;
   }
   
   // Get to do list of all shows
   public JSONArray ToDo(jobData job) {
      JSONArray allShows = new JSONArray();
      JSONObject result = null;

      try {
         int total = 0;
         if (job != null && config.GUIMODE && ! away) {
            // Get total count quickly
            JSONObject j = new JSONObject();
            j.put("format", "idSequence");
            j.put("noLimit", "true");
            result = Command("ToDo", j);
            if (result != null && result.has("objectIdAndType")) {
               total = result.getJSONArray("objectIdAndType").length();
            }
         }
         // Top level list - run in a loop to grab all items, 20 at a time
         Boolean stop = false;
         JSONObject json = new JSONObject();
         json.put("count", 20);
         int offset = 0;
         if (job != null && config.GUIMODE)
            config.gui.jobTab_UpdateJobMonitorRowOutput(job, "ToDo list");
         while ( ! stop ) {
            result = Command("ToDo", json);
            if (result != null && result.has("recording")) {
               JSONArray a = result.getJSONArray("recording");
               for (int i=0; i<a.length(); ++i)
                  allShows.put(a.getJSONObject(i));
               offset += a.length();
               json.put("offset", offset);
               if (a.length() == 0)
                  stop = true;
            } else {
               stop = true;
            }
            
            // Update status in job monitor
            if (job != null && config.GUIMODE) {
               if (away)
                  config.gui.jobTab_UpdateJobMonitorRowOutput(job, "ToDo list: " + offset);
               else
                  config.gui.jobTab_UpdateJobMonitorRowOutput(job, "ToDo list: " + offset + "/" + total);
               if ( jobMonitor.isFirstJobInMonitor(job) ) {
                  if (away) {
                     config.gui.setTitle("ToDo: " + offset + " " + config.kmttg);
                  } else {
                     int pct = (int) ((float)(offset)/total*100);
                     config.gui.setTitle("ToDo: " + pct + "% " + config.kmttg);
                     config.gui.progressBar_setValue(pct);
                  }
               }
            }
         } // while         
      } catch (JSONException e) {
         error("rpc ToDo error - " + e.getMessage());
         return null;
      }

      return TableUtil.sortByOldestStartDate(allShows);
   }
   
   // Create CSV file from todo list
   public void TodoExportCSV(File file) {
      try {
         // Top level list
         BufferedWriter ofp = new BufferedWriter(new FileWriter(file));
         JSONArray todo = ToDo(null);
         if (todo != null) {
            ofp.write("DATE,SORTABLE DATE,SHOW,CHANNEL,DURATION\r\n");
            for (int i=0; i<todo.length(); ++i) {
               JSONObject json = todo.getJSONObject(i);
               String startString=null, endString=null, duration="";
               long start=0, end=0;
               if (json.has("scheduledStartTime")) {
                  startString = json.getString("scheduledStartTime");
                  start = TableUtil.getLongDateFromString(startString);
                  endString = json.getString("scheduledEndTime");
                  end = TableUtil.getLongDateFromString(endString);
               } else if (json.has("startTime")) {
                  start = TableUtil.getStartTime(json);
                  end = TableUtil.getEndTime(json);
               }
               if (end != 0 && start != 0)
                  duration = string.removeLeadingTrailingSpaces(sortableDuration.millisecsToHMS(end-start, false));
               String date = "";
               String date_sortable = "";
               if (start != 0) {
                  SimpleDateFormat sdf = new SimpleDateFormat("E MM/dd/yy hh:mm a");
                  date = sdf.format(start);
                  sdf = new SimpleDateFormat("yyyyMMddHHmm");
                  date_sortable = sdf.format(start);
               }
               String show = string.removeLeadingTrailingSpaces(TableUtil.makeShowTitle(json));
               String channel = string.removeLeadingTrailingSpaces(TableUtil.makeChannelName(json));
               ofp.write(date + "," + date_sortable + ",\"" + show + "\"," + channel + "," + duration + "\r\n");
            }
         } else {
            log.error("Error getting ToDo list for TiVo: " + tivoName);
         }
         ofp.close();
         log.warn("ToDo list export completed successfully.");
      } catch (Exception e) {
         error("rpc TodoExportCSV error - " + e.getMessage());
         return;
      }
   }
   
   // Similar to ToDo but for upcoming episode IDs obtained from a Season Pass
   // It's assumed job.rnpl has JSONArray of objectIdAndType
   public JSONArray Upcoming(jobData job) {
      JSONArray allShows = new JSONArray();
      try {
         JSONObject json = new JSONObject();
         json.put("objectIdAndType", job.rnpl);
         JSONObject result = Command("SearchId", json);
         if (result != null && result.has("recording")) {
            JSONArray id = result.getJSONArray("recording");
            for (int j=0; j<id.length(); ++j)
               allShows.put(id.getJSONObject(j));
         }
      } catch (JSONException e) {
         error("rpc Upcoming error - " + e.getMessage());
         return null;
      }

      return TableUtil.sortByOldestStartDate(allShows);
   }
   
   // Get list of all shows that won't record
   public JSONArray CancelledShows(jobData job) {
      JSONArray allShows = new JSONArray();
      JSONObject result = null;

      try {
         int total = 0;
         if (job != null && config.GUIMODE && ! away) {
            // Get total count quickly
            JSONObject j = new JSONObject();
            j.put("format", "idSequence");
            j.put("noLimit", "true");
            result = Command("Cancelled", j);
            if (result != null && result.has("objectIdAndType")) {
               total = result.getJSONArray("objectIdAndType").length();
            }
         }
         // Top level list - run in a loop to grab all items, 20 at a time
         Boolean stop = false;
         JSONObject json = new JSONObject();
         json.put("count", 20);
         int offset = 0;
         if (job != null && config.GUIMODE)
            config.gui.jobTab_UpdateJobMonitorRowOutput(job, "Will Not Record list");
         while ( ! stop ) {
            result = Command("Cancelled", json);
            if (result != null && result.has("recording")) {
               JSONArray a = result.getJSONArray("recording");
               for (int i=0; i<a.length(); ++i)
                  allShows.put(a.getJSONObject(i));
               offset += a.length();
               json.put("offset", offset);
               if (a.length() == 0)
                  stop = true;
            } else {
               stop = true;
            }
            
            // Update status in job monitor
            if (job != null && config.GUIMODE) {
               if (away)
                  config.gui.jobTab_UpdateJobMonitorRowOutput(job, "Will Not Record list: " + offset);
               else
                  config.gui.jobTab_UpdateJobMonitorRowOutput(job, "Will Not Record list: " + offset + "/" + total);
               if ( jobMonitor.isFirstJobInMonitor(job) ) {
                  if (away) {
                     config.gui.setTitle("Not rec: " + offset + " " + config.kmttg);
                  } else {
                     int pct = (int) ((float)(offset)/total*100);
                     config.gui.setTitle("Not rec: " + pct + "% " + config.kmttg);
                     config.gui.progressBar_setValue(pct);
                  }
               }
            }
         } // while
      } catch (JSONException e) {
         error("rpc CancelledShows error - " + e.getMessage());
         return null;
      }

      return TableUtil.sortByOldestStartDate(allShows);
   }
   
   // Get list of all conflicts with cancellationReason=programSourceConflict
   public JSONArray GetProgramSourceConflicts(Hashtable<String,JSONArray> all_todo) {
      JSONArray data = CancelledShows(null);
      if (data != null) {
         JSONObject json;
         try {
            long now = new Date().getTime();
            JSONArray conflicts = new JSONArray();
            for (int i=0; i<data.length(); ++i) {
               json = data.getJSONObject(i);
               if (json.has("scheduledStartTime")) {
                  // Filter out past recordings
                  Long start = TableUtil.getLongDateFromString(json.getString("scheduledStartTime"));
                  if (start >= now) {
                     // Filter out by cancellationReason
                     if (json.has("cancellationReason") &&
                         json.getString("cancellationReason").equals("programSourceConflict")) {
                        rnpl.flagIfInTodo(json, true, all_todo);
                        if (! json.has("__inTodo__"))
                           // programSourceConflict not in any todo list => candidate
                           conflicts.put(json);
                     }
                  }
               }
            }
            return conflicts;
         } catch (JSONException e) {
            log.error(Arrays.toString(e.getStackTrace()));
         }
      }
      return null;
   }
   
   // Get list of all shows in Deleted state
   public JSONArray DeletedShows(jobData job) {
      JSONArray allShows = new JSONArray();
      JSONObject result = null;

      try {
         int total = 0;
         if (job != null && config.GUIMODE && ! away) {
            // Get total count quickly
            JSONObject j = new JSONObject();
            j.put("format", "idSequence");
            j.put("noLimit", "true");
            result = Command("Deleted", j);
            if (result != null && result.has("objectIdAndType")) {
               total = result.getJSONArray("objectIdAndType").length();
            }
         }
         // Top level list - run in a loop to grab all items, 20 at a time
         Boolean stop = false;
         JSONObject json = new JSONObject();
         json.put("count", 20);
         int offset = 0;
         if (job != null && config.GUIMODE)
            config.gui.jobTab_UpdateJobMonitorRowOutput(job, "Deleted list");
         while ( ! stop ) {
            result = Command("Deleted", json);
            if (result != null && result.has("recording")) {
               JSONArray a = result.getJSONArray("recording");
               for (int i=0; i<a.length(); ++i)
                  allShows.put(a.getJSONObject(i));
               offset += a.length();
               json.put("offset", offset);
               if (a.length() == 0)
                  stop = true;
            } else {
               stop = true;
            }
            
            // Update status in job monitor
            if (job != null && config.GUIMODE) {
               if (away)
                  config.gui.jobTab_UpdateJobMonitorRowOutput(job, "Deleted list: " + offset);
               else
                  config.gui.jobTab_UpdateJobMonitorRowOutput(job, "Deleted list: " + offset + "/" + total);
               if ( jobMonitor.isFirstJobInMonitor(job) ) {
                  if (away) {
                     config.gui.setTitle("Deleted: " + offset + " " + config.kmttg);
                  } else {
                     int pct = (int) ((float)(offset)/total*100);
                     config.gui.setTitle("Deleted: " + pct + "% " + config.kmttg);
                     config.gui.progressBar_setValue(pct);
                  }
               }
            }
         } // while
      } catch (JSONException e) {
         error("rpc DeletedShows error - " + e.getMessage());
         return null;
      }

      return TableUtil.sortByLatestStartDate(allShows);
   }
   
   // Get all season passes
   public JSONArray SeasonPasses(jobData job) {
      JSONObject result = null;
      if (job != null && config.GUIMODE)
         config.gui.jobTab_UpdateJobMonitorRowOutput(job, "Season Passes");
      result = Command("SeasonPasses", new JSONObject());
      if (result != null) {
         try {
            if (result.has("subscription")) {
               JSONArray entries = new JSONArray();
               for (int i=0; i<result.getJSONArray("subscription").length(); ++i) {
                  JSONObject j = result.getJSONArray("subscription").getJSONObject(i);
                  if (away) {
                     // Filter out certain season pass titles in away mode
                     if (j.has("title")) {
                        if (j.getString("title").equals("Music Choice"))
                           continue;
                        if (j.getString("title").equals("Amazon Video On Demand"))
                           continue;
                     }
                  }
                  // Find upcoming & conflicts entries for each SP and add data to each JSON.
                  // Only do this when job != null since for other uses just the raw SP data
                  // is all we want for speed purposes
                  if (job != null && j.has("subscriptionId")) {
                     JSONObject json = new JSONObject();
                     json.put("subscriptionId", j.getString("subscriptionId"));
                     json.put("bodyId", bodyId_get());
                     json.put("format", "idSequence");
                     json.put("state", new JSONArray("[\"inProgress\",\"scheduled\"]"));
                     JSONObject r = Command("recordingSearch", json);
                     if (r != null && r.has("objectIdAndType"))
                        j.put("__upcoming", r.getJSONArray("objectIdAndType"));
                     json.put("state", new JSONArray("[\"cancelled\"]"));
                     r = Command("recordingSearch", json);
                     if (r != null && r.has("objectIdAndType"))
                        j.put("__conflicts", r.getJSONArray("objectIdAndType"));
                  }
                  entries.put(j);
               }
               return entries;
            }
            else
               return new JSONArray();
         } catch (JSONException e) {
            error("rpc SeasonPasses error - " + e.getMessage());
            return null;
         }
      }
      return null;
   }
   
   // Re-order season passes
   public JSONArray SPReorder(jobData job) {
      JSONObject json = new JSONObject();
      try {
         json.put("subscriptionIdV2", job.remote_orderIds);
      } catch (JSONException e1) {
         log.error("ReorderCB - " + e1.getMessage());
         return null;
      }
      if (job != null && config.GUIMODE)
         config.gui.jobTab_UpdateJobMonitorRowOutput(job, "Re-order Season Passes");
      JSONObject result = Command("Prioritize", json);
      if (result != null) {
         log.warn("Season Pass priority order updated for TiVo: " + job.tivoName);
      } else {
         log.error("Failed to update Season Pass priority order for TiVo: " + job.tivoName);
         return null;
      }
      return new JSONArray();
   }
   
   // Get list of channels received
   public JSONArray ChannelList(jobData job, Boolean receivedOnly) {
      JSONObject result = null;
      try {
         // Top level list
         JSONObject json = new JSONObject();
         Boolean all_channels = config.gui.remote_gui.AllChannels();
         json.put("noLimit", "true");
         json.put("bodyId", bodyId_get());
         if (job != null && config.GUIMODE)
            config.gui.jobTab_UpdateJobMonitorRowOutput(job, "Channel List");
         result = Command("channelSearch", json);
         if (result != null && result.has("channel")) {
            // Only want received channels returned
            JSONArray a = new JSONArray();
            for (int i=0; i<result.getJSONArray("channel").length(); ++i) {
               Boolean add = true;
               json = result.getJSONArray("channel").getJSONObject(i);
               if ( receivedOnly ) {
                  if (! all_channels && ! json.getBoolean("isReceived"))
                     add = false;
               }
               if (add)
                  a.put(json);
            }
            return a;
         } else {
            error("rpc ChannelList error - no channels obtained");
         }
      } catch (JSONException e) {
         error("rpc ChannelList error - " + e.getMessage());
         return null;
      }
      return null;
   }
   
   // Create CSV file from channel lineup - included and excluded channels
   public void ChannelLineupCSV(File file) {
      JSONObject result = null;
      try {
         // Top level list
         Hashtable<String,JSONArray> h = new Hashtable<String,JSONArray>();
         JSONObject json = new JSONObject();
         json.put("noLimit", "true");
         json.put("bodyId", bodyId_get());
         result = Command("channelSearch", json);
         if (result != null && result.has("channel")) {
            // Only want received channels returned
            JSONArray included = new JSONArray();
            JSONArray excluded = new JSONArray();
            for (int i=0; i<result.getJSONArray("channel").length(); ++i) {
               json = result.getJSONArray("channel").getJSONObject(i);
               if (json.getBoolean("isReceived"))
                  included.put(json);
               else
                  excluded.put(json);
            }
            h.put("included", included);
            h.put("excluded", excluded);
            
            BufferedWriter ofp = new BufferedWriter(new FileWriter(file));
            ofp.write("CHANNEL NUMBER,CHANNEL NAME,INCLUDED\r\n");
            for (int i=0; i<included.length(); ++i) {
               JSONObject j = included.getJSONObject(i);
               String channelNumber = "";
               if (j.has("channelNumber"))
                  channelNumber = j.getString("channelNumber");
               String callSign = "";
               if (j.has("callSign"))
                  callSign = j.getString("callSign");
               ofp.write(channelNumber + "," + callSign + ",YES\r\n");
            }
            for (int i=0; i<excluded.length(); ++i) {
               JSONObject j = excluded.getJSONObject(i);
               String channelNumber = "";
               if (j.has("channelNumber"))
                  channelNumber = j.getString("channelNumber");
               String callSign = "";
               if (j.has("callSign"))
                  callSign = j.getString("callSign");
               ofp.write(channelNumber + "," + callSign + ",NO\r\n");
            }
            ofp.close();
            log.warn("Channel lineup export completed successfully.");
         } else {
            error("rpc ChannelLineupCSV error - no channels obtained");
         }
      } catch (Exception e) {
         error("rpc ChannelLineupCSV error - " + e.getMessage());
         return;
      }
   }
   
   public JSONArray SeasonPremieres(JSONArray channelNumbers, jobData job, int total_days) {
      if (channelNumbers == null)
         return null;
      if (channelNumbers.length() == 0)
         return null;   
      
      JSONObject json;
      LinkedHashMap<String,JSONObject> unique = new LinkedHashMap<String,JSONObject>();
      JSONArray data = new JSONArray();
      Date now = new Date();
      long start = now.getTime();
      long day_increment = 1*24*60*60*1000;
      long stop = start + day_increment;
      try {
         // Set shorter timeout in case some requests fail
         socket.setSoTimeout(20*1000);
         // Search 1 day at a time
         int item = 0;
         int total_items = total_days*channelNumbers.length();
         for (int day=1; day<=total_days; ++day) {
            // Now do searches for each channel
            JSONObject channel, result;
            for (int i=0; i<channelNumbers.length(); ++i) {
               channel = channelNumbers.getJSONObject(i);
               json = new JSONObject();
               JSONObject c = new JSONObject();
               c.put("channelNumber", channel.getString("channelNumber"));
               c.put("type", "channelIdentifier");
               c.put("sourceType", channel.getString("sourceType"));
               if (! channel.getString("channelId").equals("none")) {
                  c.put("channelId", channel.getString("channelId"));
               }
               if (! channel.getString("stationId").equals("none")) {
                  c.put("stationId", channel.getString("stationId"));
               }
               json.put("anchorChannelIdentifier", c);
               json.put("maxStartTime", rnpl.getStringFromLongDate(stop));
               json.put("minEndTime", rnpl.getStringFromLongDate(start));
               
               // Update status in job monitor
               if (job != null && config.GUIMODE) {
                  config.gui.jobTab_UpdateJobMonitorRowOutput(job, "Season & Series premieres");
                  String message = "Processing day=" + day + ", channel=" + channel.getString("channelNumber");
                  config.gui.jobTab_UpdateJobMonitorRowStatus(job, message);
                  if ( jobMonitor.isFirstJobInMonitor(job) ) {
                     int pct = (int) ((float)(item)/total_items*100);
                     config.gui.setTitle("Premieres: " + pct + "% " + config.kmttg);
                     config.gui.progressBar_setValue(pct);
                  }
               }
               
               result = Command("GridSearch", json);
               if (result != null && result.has("gridRow") && result.getJSONArray("gridRow").getJSONObject(0).has("offer")) {
                  JSONArray a = result.getJSONArray("gridRow").getJSONObject(0).getJSONArray("offer");
                  for (int j=0; j<a.length(); ++j) {
                     json = a.getJSONObject(j);
                     // Filter out entries we want
                     // collectionType == "series"
                     if (json.has("collectionType") && json.getString("collectionType").equals("series")) {
                        Boolean match = false;
                        if (json.has("episodeNum")) {
                           // episodeNum == 1
                           if (json.getJSONArray("episodeNum").getInt(0) == 1)
                              match = true;
                        } else {
                           // Mini series have partNumber instead of episodeNum
                           if (json.has("partNumber") && json.getInt("partNumber") == 1)
                                 match = true;                              
                           // Some series don't have episode information, so look at subtitle
                           if ( json.has("subtitle") ) {
                              String subtitle = json.getString("subtitle");
                              if (subtitle.equals("Pilot") || subtitle.equals("Series Premiere"))
                                 match = true;
                           }
                        }
                        if (match) {
                           // repeat != true
                           if ( ! json.has("repeat") || (json.has("repeat") && ! json.getBoolean("repeat")) ) {
                              unique.put(json.getString("offerId"), json);
                           }   
                        }
                     }
                  }
               }
               item += 1;
            }
            start += day_increment;
            stop += day_increment;
         }
         if (unique.isEmpty()) {
            log.warn("No show premieres found.");
         } else {
            for (JSONObject j : unique.values() )
               data.put(j);
            // Tag json entries in data that already have Season Passes scheduled
            config.gui.remote_gui.premiere_tab.TagPremieresWithSeasonPasses(data);
         }
      } catch (Exception e) {
         error("SeasonPremieres - " + e.getMessage());
         return null;
      }
      return TableUtil.sortByOldestStartDate(data);
   }
   
   // This returns JSONArray of JSON objects each of following structure:
   // String    title
   // String    type
   // String    collectionId
   // JSONArray entries
   public JSONArray searchKeywords(String keyword, jobData job, int max) {
      JSONArray table_entries = new JSONArray();
      JSONObject collections = new JSONObject();
      int order = 0;
      try {
         Boolean stop = false;
         int offset = 0;
         int count = 50;
         
         String search_type = (String)config.gui.remote_gui.search_tab.search_type.getValue();
         
         // Role type search
         JSONArray credit = null;
         if (! search_type.equals("keywords")) {
            credit = rnpl.parseCreditString(keyword, search_type);
         }
         
         // Update job monitor output column name
         if (job != null) {
            config.gui.jobTab_UpdateJobMonitorRowOutput(job, "Guide Search: " + keyword);
         }
         
         while ( ! stop ) {
            // Loop OfferSearch queries until no more results returned
            JSONObject json = new JSONObject();
            json.put("count", count);
            json.put("offset", offset);
            if (credit == null)
               json.put("keyword", keyword);
            else
               json.put("credit", credit);
            JSONObject result = Command("OfferSearch", json);
            if (result == null) {
               log.error("Keyword search failed for: '" + keyword + "'");
               stop = true;
            } else {                        
               // Filter out non-TiVo entries and get full info on them
               if (result.has("offer")) {
                  JSONArray entries = result.getJSONArray("offer");
                  for (int i=0; i<entries.length(); ++i) {
                     // Filter out non-TiVo entries
                     json = entries.getJSONObject(i);
                     if (json.has("partnerCollectionId") && json.has("title") && json.has("collectionId")) {
                        String partner = json.getString("partnerCollectionId");
                        if ( ! partner.startsWith("epg") )
                           continue;
                        String title = json.getString("title");
                        String collectionId = json.getString("collectionId");
                        String collectionType = "";
                        if (json.has("collectionType"))
                           collectionType = json.getString("collectionType");
                        if (! collections.has(collectionId)) {
                           JSONObject new_json = new JSONObject();
                           new_json.put("collectionId", collectionId);
                           new_json.put("title", title);
                           new_json.put("type", collectionType);
                           new_json.put("entries", new JSONArray());
                           new_json.put("order", order);
                           collections.put(collectionId, new_json);
                           order++;
                        }
                        collections.getJSONObject(collectionId).getJSONArray("entries").put(json);
                     }
                  }
                  offset += entries.length();
                  String message = "Matches: " + offset ;
                  config.gui.jobTab_UpdateJobMonitorRowStatus(job, message);
                  if ( jobMonitor.isFirstJobInMonitor(job) ) {
                     config.gui.setTitle("Search: " + offset + " " + config.kmttg);
                  }
                  if (offset >= max-1)
                     stop = true;
                  if (entries.length() == 0)
                     stop = true;
               } else {
                  // result did not return unifiedItem so must be done or errored out
                  stop = true;
               }                        
            }
         } // while
         log.warn(">> Keyword search completed: '" + keyword + "' on TiVo: " + job.tivoName);
         
         // Now generate table_entries in priority order
         if (collections.length() > 0) {
            JSONArray keys = collections.names();
            for (int i=0; i<order; ++i) {
               for (int j=0; j<keys.length(); ++j) {
                  if (collections.getJSONObject(keys.getString(j)).getInt("order") == i) {
                     table_entries.put(collections.getJSONObject(keys.getString(j)));
                     break;
                  }
               }
            }
         }
         
         // Add extended search results if requested
         Boolean includeFree = config.gui.remote_gui.search_tab.includeFree.isSelected();
         Boolean includePaid = config.gui.remote_gui.search_tab.includePaid.isSelected();
         if (includeFree || includePaid) {
            collections = extendedSearch(keyword, credit, includeFree, includePaid, job, max);
            if (collections != null && collections.length() > 0) {
               order = collections.getInt("order");
               JSONArray keys = collections.names();
               for (int i=0; i<order; ++i) {
                  for (int j=0; j<keys.length(); ++j) {
                     if (! keys.getString(j).equals("order")) {
                        if (collections.getJSONObject(keys.getString(j)).getInt("order") == i) {
                           table_entries.put(collections.getJSONObject(keys.getString(j)));
                           break;
                        }
                     }
                  }
               }
            }
         }
      } catch (JSONException e) {
         log.error("searchKeywords failed - " + e.getMessage());
      }
      
      return table_entries;
   }

   // Advanced Search (used by AdvSearch GUI and remote task)
   public JSONArray AdvSearch(jobData job) {
      Boolean includeFree = config.gui.remote_gui.search_tab.includeFree.isSelected();
      Boolean includePaid = config.gui.remote_gui.search_tab.includePaid.isSelected();
      try {
         String commandName = "offerSearch";
         JSONObject collections = new JSONObject();
         int order = 0;
         Boolean stop = false;
         int linearoffset = 0;
         int streamoffset = 0;
         int count = 50;
         int match_count = 0;
         JSONObject json = job.remote_adv_search_json;
         JSONObject content = new JSONObject(); // Used to avoid duplicate streaming entries
         
         // Change commandName to collectionSearch if appropriate
         Boolean noKeywords = true;
         String s[] = {
            "title", "titleKeyword", "subtitleKeyword", "subtitle",
            "descriptionKeyword", "creditKeyword", "keyword"
         };
         for (String name : s) {
            if ( json.has(name) )
               noKeywords = false;
         }
         if (noKeywords)
            commandName = "collectionSearch";
         if (commandName.equals("collectionSearch") && json.has("collectionType")) {
            if (job.remote_adv_search_cat == null && json.getString("collectionType").equals("movie"))
               if (config.getTivoUsername() != null)
                  job.remote_adv_search_cat = "Movies";
         }
         
         String categoryId = null;
         if (job.remote_adv_search_cat != null) {
            // Need to search for categoryId based on category name
            categoryId = getCategoryId(job.tivoName, job.remote_adv_search_cat);
         }
         
         // Update job monitor output column name
         if (job != null && config.GUIMODE) {
            config.gui.jobTab_UpdateJobMonitorRowOutput(job, "Advanced Search");
         }
         
         json.put("bodyId", bodyId_get());
         if (categoryId != null) {
            JSONArray a = new JSONArray();
            a.put(categoryId);
            json.put("categoryId", a);
         }
         
         // Get list of object IDs matching raw search criteria
         //log.print("commandName=" + commandName); // debug
         //log.print(json.toString(3)); // debug
         JSONArray entries = new JSONArray();
         while ( ! stop ) {
            // Linear search
            json.put("count", count);
            json.put("offset", linearoffset);
            JSONObject result = Command(commandName, json);
            if (result == null) {
               log.error("AdvSearch failed.");
               stop = true;
            } else {
               if (result.has("collectionId") || result.has("offerId")) {
                  JSONArray a;
                  if (result.has("collectionId"))
                     a = result.getJSONArray("collectionId");
                  else
                     a = result.getJSONArray("offerId");
                  linearoffset += a.length();
                  String message = "Initial Linear Matches: " + linearoffset ;
                  config.gui.jobTab_UpdateJobMonitorRowStatus(job, message);
                  if ( jobMonitor.isFirstJobInMonitor(job) ) {
                     config.gui.setTitle("Adv Search: " + linearoffset + " " + config.kmttg);
                  }
                  for (int i=0; i<a.length(); i++)
                     entries.put(a.get(i));
                  if (result.has("isBottom") && result.getBoolean("isBottom"))
                     stop = true;
               } else {
                  stop = true;
               }
            }
            
            if (includeFree || includePaid) {
               // Stream search
               json.put("count", count);
               json.put("offset", streamoffset);
               json.put("namespace", "trioserver");
               result = Command(commandName, json);
               if (result == null) {
                  log.error("AdvSearch failed.");
                  stop = true;
               } else {
                  if (result.has("collectionId") || result.has("offerId")) {
                     JSONArray a;
                     if (result.has("collectionId"))
                        a = result.getJSONArray("collectionId");
                     else
                        a = result.getJSONArray("offerId");
                     streamoffset += a.length();
                     String message = "Initial Stream Matches: " + streamoffset ;
                     config.gui.jobTab_UpdateJobMonitorRowStatus(job, message);
                     if ( jobMonitor.isFirstJobInMonitor(job) ) {
                        config.gui.setTitle("Adv Search: " + streamoffset + " " + config.kmttg);
                     }
                     for (int i=0; i<a.length(); i++)
                        entries.put(a.get(i));
                     if (result.has("isBottom") && result.getBoolean("isBottom"))
                        stop = true;
                  } else {
                     stop = true;
                  }
               }
               json.remove("namespace");
            }
         } // while

         // Now get details of each entry and apply any further filters
         String additional[] = {
            "namespace", "searchable", "minStartTime",
            "movieYear", "originalAirYear",
            "hdtv", "favoriteChannelsOnly", "receivedChannelsOnly"
         };
         JSONArray filtered_entries = new JSONArray();
         outerloop:
         for (int i=0; i<entries.length(); ++i) {
            String id = entries.getString(i);
            Boolean include = true;
            JSONObject json_id = new JSONObject();
            json_id.put("bodyId", bodyId_get());
            json_id.put("count", 20);
            json_id.put("levelOfDetail", "high");
            for (String entry : additional) {
               if (json.has(entry))
                  json_id.put(entry, json.get(entry));
            }
            if (commandName.equals("collectionSearch"))
               json_id.put("collectionId", id);
            else
               json_id.put("offerId", id);
            JSONObject result = Command("offerSearch", json_id);
            if (result != null && result.has("offer")) {
               if (json_id.has("offerId"))
                  json_id.remove("offerId");
               if (json_id.has("collectionId"))
                  json_id.remove("collectionId");
               JSONArray a = result.getJSONArray("offer");
               for (int k=0; k<a.length(); ++k) {
                  JSONObject j = a.getJSONObject(k);
                  // Channel filter
                  if (job.remote_adv_search_chans != null && j.has("channel")) {
                     include = false;
                     if (j.getJSONObject("channel").has("channelNumber")) {
                        String channelNumber = j.getJSONObject("channel").getString("channelNumber");
                        for (String chan : job.remote_adv_search_chans) {
                           if (chan.equals(channelNumber))
                              include = true;
                        }
                     }
                  } // if channel
                  
                  // ! includePaid filter
                  if ( j.has("price") && ! includePaid) {
                     if ( ! j.getString("price").equals("USD.0") )
                        include = false;
                  }
                  
                  // Filter out unavailable partners
                  if (include && (includeFree || includePaid)) {
                     String partnerName = TableUtil.getPartnerName(j);
                     if (partnerName.startsWith("tivo:pt"))
                        include = false;
                  }
                  
                  // Filter out streaming duplicates
                  if (include && (includeFree || includePaid) && j.has("partnerId")) {
                     String contentId = j.getString("contentId") + j.getString("partnerId");
                     if (content.has(contentId))
                        include = false;
                     else
                        content.put(contentId, 1);
                  }
                  
                  if (include) {
                     match_count++;
                     if (match_count > job.remote_search_max)
                        break outerloop;
                     filtered_entries.put(j);
                     String message = "Matches: " + match_count ;
                     config.gui.jobTab_UpdateJobMonitorRowStatus(job, message);
                     if ( jobMonitor.isFirstJobInMonitor(job) ) {
                        config.gui.setTitle("Adv Search: " + match_count + " " + config.kmttg);
                     }
                  } // if include
               } // for k
            } // result != null
         } // for
         filtered_entries = TableUtil.sortByOldestStartDate(filtered_entries);
         
         // Sort into collections
         for (int i=0; i<filtered_entries.length(); ++i) {
            JSONObject j = filtered_entries.getJSONObject(i);
            String title = j.getString("title");
            String collectionId = j.getString("collectionId");
            String collectionType = "";
            if (j.has("collectionType"))
               collectionType = j.getString("collectionType");
            if (! collections.has(collectionId)) {
               JSONObject new_json = new JSONObject();
               new_json.put("collectionId", collectionId);
               new_json.put("title", title);
               new_json.put("type", collectionType);
               new_json.put("entries", new JSONArray());
               new_json.put("order", order);
               collections.put(collectionId, new_json);
               order++;
            }
            collections.getJSONObject(collectionId).getJSONArray("entries").put(j);
         }
         
         // Sort collections by episode #
         if (collections.length() > 0) {
            JSONArray keys = collections.names();
            for (int i=0; i<keys.length(); ++i) {
               String key = keys.getString(i);
               if ( ! key.equals("order") ) {
                  JSONArray a = collections.getJSONObject(key).getJSONArray("entries");
                  collections.getJSONObject(key).put("entries", TableUtil.sortByEpisode(a));
               }
            }
         }
         
         log.warn(">> Advanced search completed on TiVo: " + job.tivoName);
         
         // Now generate table_entries in priority order
         if (collections.length() > 0) {
            JSONArray table_entries = new JSONArray();
            JSONArray keys = collections.names();
            for (int i=0; i<order; ++i) {
               for (int j=0; j<keys.length(); ++j) {
                  if (collections.getJSONObject(keys.getString(j)).getInt("order") == i) {
                     table_entries.put(collections.getJSONObject(keys.getString(j)));
                     break;
                  }
               }
            }
            return table_entries;
         } else {
            log.warn("NOTE: No matches found during the search.");
         }
      } catch (JSONException e) {
         log.error("AdvSearch failed - " + e.getMessage());
      } // try
      return null;
   }
   
   // Search streaming content
   public JSONObject extendedSearch(
         String keyword, JSONArray credit, Boolean includeFree, Boolean includePaid, jobData job, int max) {
      JSONObject collections = new JSONObject();
      if (config.getTivoUsername() == null) {
         log.error("Streaming entry searches require tivo.com login username and password");
         log.error("You should supply tivo.com username and password under config-Tivos tab");
         return collections;
      }
      JSONObject content = new JSONObject(); // Used to avoid duplicate contentId entries
      Remote r = new Remote(job.tivoName, true);
      if (r.success) {
         JSONArray titles = new JSONArray();
         int order = 0;
         int matched = 0;
         try {
            int count = 50;
            
            // Update job monitor output column name
            if (job != null && config.GUIMODE) {
               config.gui.jobTab_UpdateJobMonitorRowOutput(job, "Extended Search: " + keyword);
            }
            
            JSONObject json = new JSONObject();
            json.put("count", count);
            if (credit == null)
               json.put("keyword", keyword);
            else
               json.put("credit", credit);
            json.put("includeBroadcast", true);
            json.put("orderBy", "strippedTitle");
            json.put("mergeOverridingCollections", true);
            JSONObject collectionResult = r.Command("collectionSearch", json);
            if (collectionResult == null) {
               log.error("Extended keyword search failed for: '" + keyword + "'");
            } else {                        
               if (collectionResult.has("collection")) {
                  JSONArray collectionEntries = collectionResult.getJSONArray("collection");
                  for (int i=0; i<collectionEntries.length(); ++i) {
                     if (titles.length() >= max)
                        break;
                     JSONObject c = collectionEntries.getJSONObject(i);
                     if (c.has("collectionId")) {
                        // Use offerSearch to get episode details
                        JSONObject json2 = new JSONObject();
                        json2.put("count", count);
                        json2.put("namespace", "trioserver");
                        json2.put("levelOfDetail", "medium");
                        json2.put("collectionId", c.getString("collectionId"));
                        Boolean stop = false;
                        int offset = 0;
                        while (! stop) {
                           json2.put("offset", offset);
                           stop = true;
                           JSONObject offerResult = r.Command("offerSearch", json2);
                           if (offerResult != null && offerResult.has("offer")) {
                              stop = false;
                              JSONArray offerEntries = offerResult.getJSONArray("offer");
                              offset += offerEntries.length();
                              if (offerEntries.length() == 0 || offerEntries.length() < count)
                                 stop = true;
                              for (int ii=0; ii<offerEntries.length(); ii++) {
                                 if (titles.length() >= max)
                                    break;
                                 JSONObject entry = offerEntries.getJSONObject(ii);
                                 
                                 // NOTE: Filter out paid items if includePaid == false
                                 Boolean add = true;
                                 if ( entry.has("episodic") && entry.getBoolean("episodic") && ! entry.has("episodeNum"))
                                    add = false;
                                 if ( entry.has("price") && ! includePaid) {
                                    if ( ! entry.getString("price").equals("USD.0") )
                                       add = false;
                                 }
                                 // Filter out no longer supported collectionType=webVideo
                                 if (entry.has("collectionType") && entry.getString("collectionType").equals("webVideo"))
                                    add = false;
                                 if ( ! entry.has("partnerId") || ! entry.has("contentId"))
                                    add = false;
                                 
                                 // Filter out unavailable partners
                                 String partnerName = TableUtil.getPartnerName(entry);
                                 if (partnerName.startsWith("tivo:pt"))
                                    add = false;
                                 
                                 if (add) {
                                    String contentId = entry.getString("contentId") + entry.getString("partnerId");
                                    if ( ! content.has(contentId) ) {
                                       matched++;
                                       titles.put(entry);
                                       content.put(contentId, 1);
                                    }
                                 }                        
                                    
                                 if (job != null) {
                                    String message = "Ext Matches: " + matched;
                                    config.gui.jobTab_UpdateJobMonitorRowStatus(job, message);
                                    if ( jobMonitor.isFirstJobInMonitor(job) ) {
                                       config.gui.setTitle("Ext Search: " + matched + " " + config.kmttg);
                                    }
                                 }
                              } // for ii
                           } // if offerResult
                           if (titles.length() >= max)
                              stop = true;
                        } // while ! stop
                     } // if c.has("collectionId")
                  } // for
               } // if result.has("collection")
            } // if collectionResult
            
            // Sort into collections
            for (int i=0; i<titles.length(); ++i) {
               JSONObject j = titles.getJSONObject(i);
               String partnerId = "";
               if (j.has("partnerId"))
                  partnerId = j.getString("partnerId");
               String title = j.getString("title");
               String collectionId = j.getString("collectionId");
               String collectionType = "";
               if (j.has("collectionType"))
                  collectionType = j.getString("collectionType");
               if (! collections.has(collectionId)) {
                  JSONObject new_json = new JSONObject();
                  if (partnerId.length() > 0)
                     new_json.put("partnerId", partnerId);
                  new_json.put("collectionId", collectionId);
                  new_json.put("title", title);
                  new_json.put("type", collectionType);
                  new_json.put("entries", new JSONArray());
                  new_json.put("order", order);
                  collections.put(collectionId, new_json);
                  order++;
               }
               collections.getJSONObject(collectionId).getJSONArray("entries").put(j);
            }
            collections.put("order", order);
            
            // Sort collections by episode #
            if (collections.length() > 0) {
               JSONArray keys = collections.names();
               for (int i=0; i<keys.length(); ++i) {
                  String key = keys.getString(i);
                  if ( ! key.equals("order") ) {
                     JSONArray a = collections.getJSONObject(key).getJSONArray("entries");
                     collections.getJSONObject(key).put("entries", TableUtil.sortByEpisode(a));
                  }
               }
            }
            
            if (job != null)
               log.warn(">> Extended search completed on TiVo: " + job.tivoName);
         } catch (JSONException e) {
            log.error("extendedSearch failed - " + e.getMessage());
         }
         r.disconnect();
      }
      
      return collections;
   }
   
   // Return list of My Shows streaming entries (streaming folders or individual streaming items)
   // (For initial call parentId=null)
   public JSONArray streamingEntries(String parentId) {
      JSONArray entries = new JSONArray();
      try {
         JSONObject json = new JSONObject();
         json.put("bodyId", bodyId_get());
         json.put("levelOfDetail", "medium");
         json.put("noLimit", true);
         if (parentId != null)
            json.put("parentMyShowsItemId", parentId);
         JSONObject result = Command("myShowsItemSearch", json);
         if (result != null && result.has("myShowsItem")) {
            JSONArray a = result.getJSONArray("myShowsItem");
            for (int i=0; i<a.length(); ++i) {
               JSONObject entry = a.getJSONObject(i);
               if (entry.has("onDemandAvailability") && entry.has("collectionId")) {
                  JSONObject j = new JSONObject();
                  j.put("collectionId", entry.get("collectionId"));
                  j.put("levelOfDetail", "high");
                  JSONObject r = Command("collectionSearch", j);
                  if (r != null && r.has("collection")) {
                     JSONObject o = r.getJSONArray("collection").getJSONObject(0);
                     o.put("myShowsItemId", entry.getString("myShowsItemId"));
                     if (entry.has("contentId"))
                        o.put("contentId", entry.getString("contentId"));
                     if (entry.has("startTime"))
                        o.put("startTime", entry.getString("startTime"));
                     if (entry.has("isFolder"))
                        o.put("isFolder", entry.getBoolean("isFolder"));
                     JSONObject avail = entry.getJSONArray("onDemandAvailability").getJSONObject(0);
                     if (avail.has("brandingPartnerId"))
                        o.put("partnerId", avail.getJSONArray("brandingPartnerId").getString(0));
                     entries.put(o);
                  }
               } else if (entry.has("isFolder") && entry.getBoolean("isFolder")) {
                  JSONArray children = streamingEntries(entry.getString("myShowsItemId"));
                  for (int k=0; k<children.length(); ++k)
                     entries.put(children.getJSONObject(k));
               }
            }
         }
      } catch (Exception e) {
         log.error("streamingEntries - " + e.getMessage());
      }
      return(entries);
   }
   
   // Given a collectionId return channels in guide associated with it
   public JSONArray channelSearch(String collectionId) {
      LinkedHashMap<String,JSONObject> unique = new LinkedHashMap<String,JSONObject>();
      JSONArray channels = new JSONArray();
      try {
         int count = 30;
         int offset = 0;
         JSONObject json = new JSONObject();
         json.put("bodyId", bodyId_get());
         json.put("collectionId", collectionId);
         json.put("levelOfDetail", "low");
         json.put("count", count);
         Boolean stop = false;
         while (! stop) {
            json.put("offset", offset);
            JSONObject result = Command("offerSearch", json);
            offset += count;
            if (result != null && result.has("offer")) {
               if (result.has("isBottom") && result.getBoolean("isBottom"))
                  stop = true;
               JSONArray offers = result.getJSONArray("offer");
               if (offers.length() == 0)
                  stop = true;
               for (int i=0; i<offers.length(); ++i) {
                  JSONObject offer = offers.getJSONObject(i);
                  if (offer.has("channel")) {
                     JSONObject channel = offer.getJSONObject("channel");
                     unique.put(channel.getString("callSign"), channel);
                  }
               }
            } else {
               stop = true;
            }
         }
         if (! unique.isEmpty()) {
            for (JSONObject j : unique.values()) {
               channels.put(j);
            }
         }
      } catch (Exception e) {
         log.error("channelSearch - " + e.getMessage());
      }
      return channels;
   }
   
   // Given a collectionId return greatest season number found
   // Return JSONObject with "maxSeason" integer or "years" JSONArray
   public JSONObject seasonYearSearch(String collectionId) {
      JSONObject info = new JSONObject();
      int maxSeason = 1;
      // Using SortedSet so as to get unique array
      Hashtable<Integer,Stack<Integer>> seasons = new Hashtable<Integer,Stack<Integer>>();
      Hashtable<Integer,Stack<Integer>> years = new Hashtable<Integer,Stack<Integer>>();
      try {
         int count = 30;
         int offset = 0;
         JSONObject json = new JSONObject();
         JSONObject last = new JSONObject();
         json.put("collectionId", collectionId);
         json.put("filterUnavailable", false);
         json.put("orderBy", "seasonNumber");
         json.put("levelOfDetail", "medium");
         json.put("count", count);
         json.put("omitPgdImages", true);
         Boolean stop = false;
         while (! stop) {
            json.put("offset", offset);
            JSONObject result = Command("contentSearch", json);
            offset += count;
            if (result != null && result.has("content")) {
               if (result.has("isBottom") && result.getBoolean("isBottom"))
                  stop = true;
               JSONArray matches = result.getJSONArray("content");
               if (matches.length() == 0)
                  stop = true;
               for (int i=0; i<matches.length(); ++i) {
                  JSONObject j = matches.getJSONObject(i);
                  // This code needed because in many cases isBottom/isTop not in result so would never stop
                  if (i==0 && last != null && last.has("content")) {
                     JSONObject c = last.getJSONArray("content").getJSONObject(0);
                     if (c.has("contentId") && j.has("contentId") && c.getString("contentId").equals(j.getString("contentId")))
                        stop = true;
                  }
                  // Skip non episode matches
                  if (j.has("isEpisode") && ! j.getBoolean("isEpisode"))
                     continue;
                  int year = 0, season = 0;
                  if (j.has("seasonNumber"))
                     season = j.getInt("seasonNumber");
                  if (j.has("originalAirYear"))
                     year = j.getInt("originalAirYear");
                  if (season > 0) {
                     if (season < 100) {
                        // Assuming any season < 100 is an actual season rather than a Rovi year
                        if (! seasons.containsKey(season))
                           seasons.put(season, new Stack<Integer>());
                        if (year > 0) {
                           Stack<Integer> years_stack = seasons.get(season);
                           years_stack.push(year);
                           seasons.put(season, years_stack);
                        }
                     } else {
                        // Rovi has some season numbers as years, so treat it as years
                        years.put(season, new Stack<Integer>());
                     }
                  }
                  if (year > 0) {
                     if (! years.containsKey(year))
                        years.put(year, new Stack<Integer>());
                     if (season > 0) {
                        Stack<Integer> seasons_stack = years.get(year);
                        seasons_stack.push(season);
                        years.put(year, seasons_stack);
                     }
                  }
               }
            } else {
               stop = true;
            }
            last = result;
         } // while
         if (seasons.size() > 0) {
            for (int season : seasons.keySet()) {
               if (season > maxSeason)
                  maxSeason = season;
            }
         }
         info.put("maxSeason", maxSeason);
         Boolean useYears = false;
         if (seasons.size() == 0 && years.size() > 0)
            useYears = true;
         if (seasons.size() > 0 && years.size() > 0) {
            // If there is a year without any season, then use years range instead
            for (int year : years.keySet()) {
               Stack<Integer> seasons_stack = years.get(year);
               if (seasons_stack.isEmpty())
                  useYears = true;
            }
         }
         if (useYears) {
            info.remove("maxSeason");
            // 1st make unique array of years
            SortedSet<Integer> unique = new TreeSet<Integer>();
            for (int year : years.keySet())
               unique.add(year);
            Object y[] = unique.toArray();
            Arrays.sort(y);
            JSONArray a = new JSONArray();
            for (Object year : y)
               a.put(year);
            info.put("years", a);
         }
      } catch (Exception e) {
         log.error("seasonSearch - " + e.getMessage());
      }
      //log.print(">>seasonSearch completes");
      return info;
   }
   
   // Given a collectionId return all episodes
   public JSONArray getEpisodes(String collectionId) {
      JSONArray episodes = new JSONArray();
      Hashtable<String,Integer> unique = new Hashtable<String,Integer>();
      try {
         // If there's a SP with matching collectionId then may restrict seasons
         int startFrom = 1;
         int possible = getOnePassStartFrom(collectionId);
         if (possible > 0)
            startFrom = possible;
         
         int count = 25;
         int offset = 0;
         JSONObject json = new JSONObject();
         if (! awayMode())
            json.put("bodyId", bodyId_get());
         json.put("collectionId", collectionId);
         if (! awayMode())
            json.put("filterUnavailable", false);
         JSONArray orderBy = new JSONArray();
         orderBy.put("seasonNumber");
         orderBy.put("episodeNum");
         json.put("orderBy", orderBy);
         json.put("levelOfDetail", "medium");
         json.put("count", count);
         //json.put("omitPgdImages", true);
         Boolean stop = false;
         while (! stop) {
            json.put("offset", offset);
            JSONObject result = Command("contentSearch", json);
            offset += count;
            if (result != null && result.has("content")) {
               if (result.has("isBottom") && result.getBoolean("isBottom"))
                  stop = true;
               JSONArray matches = result.getJSONArray("content");
               if (matches.length() == 0)
                  stop = true;
               for (int i=0; i<matches.length(); ++i) {
                  JSONObject j = matches.getJSONObject(i);
                  // Skip non episode matches
                  if (j.has("isEpisode") && ! j.getBoolean("isEpisode"))
                     continue;
                  if (startFrom > 1) {
                     // Filter according to startSeasonOrYear
                     int start = getStartSeasonOrYear(j);
                     if (start != -1 && start < startFrom)
                        continue;
                  }
                  String title = TableUtil.makeShowTitle(j);
                  if (title != null && title.length() > 0) {
                     if (unique.containsKey(title))
                        continue;
                     unique.put(title, 1);
                  }
                  episodes.put(j);
               }
            } else {
               stop = true;
            }
         }
      } catch (Exception e) {
         log.error("getEpisodes - " + e.getMessage());
      }
      return episodes;
   }
   
   // Return all thumbs ratings for a TiVo
   public JSONArray getThumbs(jobData job) {
      JSONArray thumbs = new JSONArray();
      try {
         int count = 50;
         int offset = 0;
         JSONObject json = new JSONObject();
         json.put("bodyId", bodyId_get());
         json.put("levelOfDetail", "medium");
         json.put("count", count);
         Boolean stop = false;
         while (! stop) {
            json.put("offset", offset);
            JSONObject result = Command("userContentSearch", json);
            offset += count;
            if (result != null && result.has("userContent")) {                              
               JSONArray matches = result.getJSONArray("userContent");
               if (matches.length() == 0)
                  stop = true;
               // NOTE: Performing collectionSearch with 50 at a time is a huge speedup
               JSONObject j = new JSONObject();
               JSONArray ids = new JSONArray();
               for (int i=0; i<matches.length(); ++i) {
                  JSONObject t = matches.getJSONObject(i);
                  ids.put(t.getString("collectionId"));
               }
               j.put("collectionId", ids);
               j.put("count", ids.length());
               result = Command("collectionSearch", j);
               if (result != null && result.has("collection")) {
                  JSONArray a = result.getJSONArray("collection");
                  for (int i=0; i<a.length(); ++i) {                  
                     JSONObject r = a.getJSONObject(i);
                     JSONObject t = matches.getJSONObject(i);
                     if (r.has("title"))
                        t.put("title", r.getString("title"));
                     if (r.has("collectionType"))
                        t.put("collectionType", r.getString("collectionType"));
                     thumbs.put(t);
                  }
                  // Update status in job monitor
                  if (job != null && config.GUIMODE) {
                     config.gui.jobTab_UpdateJobMonitorRowOutput(job, "Thumbs List");
                     config.gui.jobTab_UpdateJobMonitorRowStatus(job, "count=" + thumbs.length());
                     if ( jobMonitor.isFirstJobInMonitor(job) ) {
                        config.gui.setTitle("Thumbs: " + thumbs.length() + " " + config.kmttg);
                     }
                  }
               }
            } else
               stop = true;
         }
      } catch (Exception e) {
         log.error("getThumbs - " + e.getMessage());
      }
      return thumbs;
   }
   
   // Get thumbs rating for given json collectionId if it exists
   // Returns 0 if none
   public int getThumbsRating(JSONObject json) {
      int thumbsRating = 0;
      try {
         String collectionId = null;
         if (json.has("collectionId"))
            collectionId = json.getString("collectionId");
         if (collectionId == null && json.has("idSetSource")) {
            JSONObject id = json.getJSONObject("idSetSource");
            if (id.has("collectionId"))
               collectionId = id.getString("collectionId");
         }
         if (collectionId != null) {
            JSONObject o = new JSONObject();
            o.put("bodyId", bodyId_get());
            o.put("collectionId", collectionId);
            JSONObject result = Command("userContentSearch", o);
            if (result != null && result.has("userContent")) {
               JSONObject j = result.getJSONArray("userContent").getJSONObject(0);
               if (j.has("thumbsRating"))
                  thumbsRating = j.getInt("thumbsRating");
            }
         }
      } catch (JSONException e) {
         log.error("getThumbsRating - " + e.getMessage());
      }
      return thumbsRating;
   }
   
   // Set thumb rating for given json collectionId if it exists
   // If override is true, then override existing thumb value
   public Boolean setThumbsRating(JSONObject json, int thumbsRating, Boolean override) {
      Boolean success = false;
      try {
         String collectionId = null;
         if (json.has("collectionId"))
            collectionId = json.getString("collectionId");
         if (collectionId == null && json.has("idSetSource")) {
            JSONObject id = json.getJSONObject("idSetSource");
            if (id.has("collectionId"))
               collectionId = id.getString("collectionId");
         }
         if (collectionId != null) {
            if (! override) {
               // Don't override if thumbs rating already exists
               thumbsRating = getThumbsRating(json);
               if (thumbsRating != 0)
                  return true;
            }
            JSONObject o = new JSONObject();
            o.put("bodyId", bodyId_get());
            o.put("collectionId", collectionId);
            o.put("thumbsRating", thumbsRating);
            JSONObject result = Command("userContentStore", o);
            if (result != null)
               success = true;
         }
      } catch (JSONException e) {
         log.error("setThumbsRating - " + e.getMessage());
      }
      return success;
   }
   
   // Return a One Pass with given collectionId if it exists
   public JSONObject findSP(String collectionId) {
      try {
         JSONObject json = new JSONObject();
         json.put("bodyId", bodyId_get());
         json.put("levelOfDetail", "medium");
         json.put("collectionId", collectionId);
         JSONObject result = Command("subscriptionSearch", json);
         if (result != null && result.has("subscription")) {
            return result.getJSONArray("subscription").getJSONObject(0);
         }
      } catch (JSONException e) {
         log.error("findSP - " + e.getMessage());
      }
      return null;
   }
   
   private int getOnePassStartFrom(String collectionId) {
      JSONObject sp = findSP(collectionId);
      if (sp != null) {
         return getStartSeasonOrYear(sp);
      }
      return -1;
   }
   
   private int getStartSeasonOrYear(JSONObject json) {
      if (json != null) {
         try {
            if (json.has("idSetSource")) {
               JSONObject id = json.getJSONObject("idSetSource");
               if (id.has("startSeasonOrYear")) {
                  return id.getInt("startSeasonOrYear");
               }
            }
            if (json.has("seasonNumber")) {
               return json.getInt("seasonNumber");
            }
            if (json.has("originalAirYear")) {
               return json.getInt("originalAirYear");
            }
         } catch (JSONException e) {
            log.error("getStartSeasonOrYear - " + e.getMessage());
         }
      }
      return -1;
   }
   
   // Return full list of category names (including sub-categories)
   public Stack<String> getCategoryNames(String tivoName) {
      Stack<String> categories = new Stack<String>();
      Hashtable<String,Integer> unique = new Hashtable<String,Integer>();
      Remote r = new Remote(tivoName, true);
      if (r.success) {
         try {
            JSONObject json = new JSONObject();
            json.put("orderBy", "label");
            json.put("topLevelOnly", false);
            json.put("noLimit", true);
            JSONObject result = r.Command("categorySearch", json);
            if (result != null && result.has("category")) {
               JSONArray cat = result.getJSONArray("category");
               for (int i=0; i<cat.length(); ++i) {
                  JSONObject j = cat.getJSONObject(i);
                  if (j.has("label")) {
                     String label = j.getString("label");
                     if (! label.endsWith(" ") && ! unique.containsKey(label)) {
                        unique.put(label, 1);
                        categories.push(label);
                     }
                  }
               }
            }
         } catch (JSONException e) {
            log.error("Remote getCategoryNames - " + e.getMessage());
            return null;
         }
         r.disconnect();
      }
      return categories;      
   }
   
   private String getCategoryId(String tivoName, String categoryName) {
      Remote r = new Remote(tivoName, true);
      if (r.success) {
         try {
            JSONObject json = new JSONObject();
            json.put("orderBy", "label");
            json.put("topLevelOnly", false);
            json.put("noLimit", true);
            JSONObject result = r.Command("categorySearch", json);
            if (result != null && result.has("category")) {
               JSONArray top = result.getJSONArray("category");
               for (int i=0; i<top.length(); ++i) {
                  if (top.getJSONObject(i).has("partnerId"))
                     top.remove(i);
               }
               for (int i=0; i<top.length(); ++i) {
                  JSONObject j = top.getJSONObject(i);
                  if (j.has("label") && j.getString("label").equals(categoryName)) {
                     r.disconnect();
                     return j.getString("categoryId");
                  }
               }
            }
         } catch (JSONException e) {
            log.error("Remote getCategoryId - " + e.getMessage());
            return null;
         }
         r.disconnect();
      }
      return null;
   }
   
   // Method use by various RPC tables for SP scheduling
   // NOTE: This should be called in a separate thread
   public void SPschedule(String tivoName, JSONObject json, JSONArray existing) {
      JSONObject existingSP = null;
      try {
         String title = json.getString("title");
         String channel = "";
         if (json.has("channel")) {
            JSONObject o = json.getJSONObject("channel");
            if (o.has("callSign"))
               channel = o.getString("callSign");
         }
         // Check against existing
         Boolean schedule = true;
         for (int j=0; j<existing.length(); ++j) {
            JSONObject e = existing.getJSONObject(j);
            if(title.equals(e.getString("title"))) {
               if (channel.length() > 0 && e.has("idSetSource")) {
                  JSONObject id = e.getJSONObject("idSetSource");
                  if (id.has("channel")) {
                     JSONObject c = id.getJSONObject("channel");
                     String callSign = "";
                     if (c.has("callSign"))
                        callSign = c.getString("callSign");
                     if (channel.equals(callSign)) {
                        schedule = false;
                        existingSP = e;
                     }
                  }
               } else {
                  schedule = false;
                  existingSP = existing.getJSONObject(j);
               }
            }
         }
         
         // If schedule == false and collectionId exists check for same collectionId
         // If different collectionId then this should be new, not modify
         if (! schedule) {
            if (existingSP.has("idSetSource")) {
               JSONObject sp_id = existingSP.getJSONObject("idSetSource");
               if (sp_id.has("collectionId")) {
                  if (json.has("collectionId")) {
                     String sp_cid = sp_id.getString("collectionId");
                     String json_cid = json.getString("collectionId");
                     if (! sp_cid.equals(json_cid)) {
                        schedule = true;
                        existingSP = null;
                     }
                  }
               }
            }
         }
         
         // OK to subscribe
         class backgroundRun implements Runnable {
            Boolean schedule;
            String tivoName;
            JSONObject json;
            String title;
            JSONObject existingSP;
            public backgroundRun(Boolean schedule, String tivoName, JSONObject json, String title, JSONObject existingSP) {
               this.schedule = schedule;
               this.tivoName = tivoName;
               this.json = json;
               this.title = title;
               this.existingSP = existingSP;
            }
            @Override public void run() {
               if (schedule) {            
                  // Streaming source should default to "Streaming Only" setting for Include
                  JSONObject o = null;
                  String include_value = (String)util.spOpt.getIncludeValue();
                  if (include_value.equals("Recordings Only") && json.has("partnerId"))
                     util.spOpt.setIncludeValue("Streaming Only");
                  
                  // Non streaming source should default to "Recordings Only"
                  if (! include_value.equals("Recordings Only") && ! json.has("partnerId"))
                     util.spOpt.setIncludeValue("Recordings Only");
      
                  o = util.spOpt.promptUser(
                     tivoName, "(" + tivoName + ") " + "Create SP - " + title, json, false
                  );
                  if (o != null) {
                     log.print("Scheduling SP: '" + title + "' on TiVo: " + tivoName);
                     if (o.has("idSetSource")) {
                        try {
                           JSONObject idSetSource = o.getJSONObject("idSetSource");
                           if (idSetSource.has("consumptionSource")) {
                              // Has streaming elements
                              idSetSource.put("collectionId", json.getString("collectionId"));
                              idSetSource.put("type", "seasonPassSource");
                           } else {
                              // Recordings only
                              idSetSource.put("collectionId", json.getString("collectionId"));
                              idSetSource.put("type", "seasonPassSource");
                              idSetSource.put("channel", json.getJSONObject("channel"));
                              o.put("idSetSource", idSetSource);
                           }
                        } catch (JSONException e) {
                           log.error("SPSchedule - " + e.getMessage());
                        }
                     } else {
                        try {
                        // Recordings only
                        JSONObject idSetSource = new JSONObject();
                        idSetSource.put("collectionId", json.getString("collectionId"));
                        idSetSource.put("type", "seasonPassSource");
                        idSetSource.put("channel", json.getJSONObject("channel"));
                        o.put("idSetSource", idSetSource);
                        } catch (JSONException e) {
                           log.error("SPSchedule - " + e.getMessage());
                        }
                     }
                     JSONObject result = Command("Seasonpass", o);
                     if (result != null) {
                        log.print("success");
                        TableUtil.addTivoNameFlagtoJson(json, "__SPscheduled__", tivoName);
                        // Set thumbs rating if not already set
                        setThumbsRating(json, 1, false);
                     }
                  }
               } else {
                  log.warn("Existing SP with same title + callSign found, prompting to modify instead.");
                  if (existingSP != null) {
                     JSONObject result = util.spOpt.promptUser(
                        tivoName, "(" + tivoName + ") " + "Modify SP - " + title, existingSP, TableUtil.isWL(existingSP)
                     );
                     if (result != null) {
                        if (Command("ModifySP", result) != null) {
                           log.warn("Modified SP '" + title + "' for TiVo: " + tivoName);
                        }
                     }
                  }
               }
            }
         }
         Platform.runLater(new backgroundRun(schedule, tivoName, json, title, existingSP));
      } catch (JSONException e) {
         log.error("SPschedule - " + e.getMessage());
      }
   }
   
   public void keyEventMacro(String[] sequence) {
      try {
         JSONObject result;
         for (int i=0; i<sequence.length; ++i) {
            JSONObject json = new JSONObject();
            if (sequence[i].matches("^[0-9]")) {
               json.put("event", "ascii");
               json.put("value", sequence[i].toCharArray()[0]);
            } else {
               json.put("event", sequence[i]);
            }
            result = Command("keyEventSend", json);
            if (result == null) break;
         }
      } catch (JSONException e1) {
         log.error("Macro CB - " + e1.getMessage());
      }
      disconnect();
   }
   
   // Background mode reboot sequence for a TiVo
   public void reboot(final String tivoName) {
      Task<Void> task = new Task<Void>() {
         @Override public Void call() {
            try {
               JSONObject json = new JSONObject();
               json.put("bodyId", bodyId_get());
               json.put("uri", "x-tivo:classicui:restartDvr");
               JSONObject result = Command("uiNavigate", json);
               if (result != null) {
                  Thread.sleep(5000);
                  String[] keys = {"thumbsDown", "thumbsDown", "thumbsDown", "enter"};
                  for (String key : keys) {
                     JSONObject j = new JSONObject();
                     j.put("event", key);
                     result = Command("keyEventSend", j);
                     if (result == null) break;
                  }
                  log.warn("Rebooting TiVo: " + tivoName);
                  disconnect();
               }
            } catch (Exception e) {
               log.error("reboot - " + e.getMessage());
            }
            return null;
         }
      };
      new Thread(task).start();
   }
   
   // RPC query for tivo.com SKIP data based on contentId
   public JSONObject getClipData(String contentId, String clipMetadataId) {
      try {
         JSONObject json = new JSONObject();
         if (clipMetadataId == null) {
            json.put("contentId", contentId);
            JSONObject result = Command("clipMetadataSearch", json);
            if (result != null && result.has("clipMetadata")) {
               clipMetadataId = result.getJSONArray("clipMetadata").getJSONObject(0).getString("clipMetadataId");
            } else
               return null;
         }
         json = new JSONObject();
         JSONArray idArray = new JSONArray();
         idArray.put(clipMetadataId);
         json.put("clipMetadataId", idArray);
         JSONObject result = Command("clipMetadataSearch", json);
         if (result != null && result.has("clipMetadata")) {
            JSONArray clipMetadata = result.getJSONArray("clipMetadata");
            for (int i=0; i<clipMetadata.length(); ++i) {
               JSONObject clip = clipMetadata.getJSONObject(i);
               if (clip.has("clipMetadataId") && clip.getString("clipMetadataId").equals(clipMetadataId))
                  return clip;
            }
         }
      } catch (JSONException e) {
         log.error("getClipData - " + e.getMessage());
      }
      return null;
   }
   
   // RPC query for tivo.com SKIP data based on contentId
   public void printClipData(String contentId) {
      try {
         JSONObject data = getClipData(contentId, null);
         if (data != null) {
            log.warn("\nSKIP data available for contentId: " + contentId);
            if (data.has("syncMark"))
               data.remove("syncMark");
            data.put("syncMark", "<syncMark array removed for display purposes>");
            log.print(data.toString(3));
         } else {
            log.warn("\nSKIP data not available for contentId: " + contentId);
         }
      } catch (JSONException e) {
         log.error("printClipData - " + e.getMessage());
      }
   }
   
   // Go through all OnePasses and check that stationId of each OnePass has a corresponding
   // guide channel stationId
   // Also check that idSetSource/collectionId matches collectionId of future linear airings of
   // the series if there are any. This was born out of the fact that Rovi data sometimes
   // changes collectionId of a series making it so OnePasses don't record future recordings
   // NOTE: This procedure should be called in background mode
   public void checkOnePasses(String tivoName) {
      JSONArray passes = SeasonPasses(null);
      int warnings = 0;
      if (passes != null) {
         JSONArray channels = ChannelList(null, false);
         if (channels != null) {
            try {
               Hashtable<String,JSONObject> channelMap = new Hashtable<String,JSONObject>();
               for (int i=0; i<channels.length(); ++i) {
                  JSONObject channel = channels.getJSONObject(i);
                  if (channel.has("channelNumber")) {
                     channelMap.put(channel.getString("channelNumber"), channel);
                  }
               }
               for (int i=0; i<passes.length(); ++i) {
                  JSONObject pass = passes.getJSONObject(i);
                  if (pass.has("idSetSource")) {
                     String pass_title = pass.getString("title");
                     JSONObject id = pass.getJSONObject("idSetSource");
                     // stationId check
                     if (id.has("channel")) {
                        JSONObject channel = id.getJSONObject("channel");
                        String pass_channelNumber = null;
                        String pass_stationId = null;
                        if (channel.has("channelNumber")) {
                           pass_channelNumber = channel.getString("channelNumber");
                        }
                        if (channel.has("stationId")) {
                           pass_stationId = channel.getString("stationId");
                        }
                        if (pass_channelNumber != null && pass_stationId != null) {
                           String pass_callSign = channel.getString("callSign");
                           if (channelMap.containsKey(pass_channelNumber)) {
                              JSONObject guide_channel = channelMap.get(pass_channelNumber);
                              String guide_stationId = guide_channel.getString("stationId");
                              String guide_callSign = guide_channel.getString("callSign");
                              log.print("INFO: pass title=" + pass_title + " channelNum=" + pass_channelNumber + " callSign=" + pass_callSign + " stationId=" + pass_stationId +
                                 " : guide callSign=" + guide_callSign + " stationId=" + guide_stationId);
                              if (! guide_stationId.equals(pass_stationId)) {
                                 warnings++;
                                 log.warn("Mismatch for OnePass: " + pass_title);
                                 log.warn("OnePass channelNum=" + pass_channelNumber +  ", OnePass stationId=" + pass_stationId +
                                   ": guide stationId=" + guide_stationId + ", guide callSign=" + guide_callSign);
                              }
                           } else {
                              warnings++;
                              log.warn("OnePass channelNumber=" + pass_channelNumber + " not available in guide channel list: " + pass_title);
                           }
                        }
                     }
                     
                     // collectionId check
                     if (id.has("collectionId")) {
                        String collectionId = id.getString("collectionId");
                        JSONObject j = new JSONObject();
                        j.put("bodyId", bodyId_get());
                        j.put("title", pass_title);
                        j.put("collectionType", "series");
                        JSONObject result = Command("offerSearch", j);
                        if (result != null && result.has("offer")) {
                           JSONArray offers = result.getJSONArray("offer");
                           if (offers.length() > 0) {
                              JSONObject offer = offers.getJSONObject(0);
                              if (offer.has("collectionId")) {
                                 //log.print(pass_title + " checking against collectionId=" + offer.getString("collectionId"));
                                 if ( ! collectionId.equals(offer.getString("collectionId")) ) {
                                    log.warn("Potential issue for OnePass: " + pass_title);
                                    log.warn(
                                       "OnePass collectionId=" + collectionId +
                                       " doesn't match collectionId of upcoming recordings with same title"
                                    );
                                    warnings++;
                                 }
                              }
                           }
                        }
                     }
                  }
               } // for
               log.print("OnePass checks completed with " + warnings + " warnings");
            } catch (JSONException e) {
               error("checkOnePasses - " + e.getMessage());
            }
         } else {
            error("checkOnePasses - trouble getting channel data");
         }
      } else {
         error("checkOnePasses - trouble getting OnePass data");
      }
   }
      
   private void print(String message) {
      log.print(message);
   }
   
   private void error(String message) {
      log.error(message);
   }
}
