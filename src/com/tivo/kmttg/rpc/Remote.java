package com.tivo.kmttg.rpc;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Random;
import java.util.Scanner;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.TableUtil;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class Remote {
   public Boolean debug = false;
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
   
   public class NaiveTrustManager implements X509TrustManager {
     /**
      * Doesn't throw an exception, so this is how it approves a certificate.
      * @see javax.net.ssl.X509TrustManager#checkClientTrusted(java.security.cert.X509Certificate[], String)
      **/
     public void checkClientTrusted ( X509Certificate[] cert, String authType )
                 throws CertificateException 
     {
     }

     /**
      * Doesn't throw an exception, so this is how it approves a certificate.
      * @see javax.net.ssl.X509TrustManager#checkServerTrusted(java.security.cert.X509Certificate[], String)
      **/
     public void checkServerTrusted ( X509Certificate[] cert, String authType ) 
        throws CertificateException 
     {
     }

     /**
      * @see javax.net.ssl.X509TrustManager#getAcceptedIssuers()
      **/
     public X509Certificate[] getAcceptedIssuers ()
     {
       //return null;  // I've seen someone return new X509Certificate[ 0 ]; 
        return new X509Certificate[ 0 ];
     }
   }
   
   public final void createSocketFactory() {
     if ( sslSocketFactory == null ) {
       try {
          KeyStore keyStore = KeyStore.getInstance("PKCS12");
          // This is default USA password
          String password = "mpE7Qy8cSqdf";
          InputStream keyInput;
          if (cdata == null) {
             // Installation dir cdata.p12 file takes priority if it exists
             String cdata = config.programDir + "/cdata.p12";
             if ( file.isFile(cdata) ) {
                keyInput = new FileInputStream(cdata);
                cdata = config.programDir + "/cdata.password";
                if (file.isFile(cdata)) {
                   password = new Scanner(new File(cdata)).useDelimiter("\\A").next();
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
          KeyManagerFactory fac = KeyManagerFactory.getInstance("SunX509");
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
      String wan_port = config.getWanSetting(tivoName, "ipad");
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
         error("RemoteInit - (IP=" + IP + ", port=" + port + "): " + e.getMessage());
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
         rpc_id++;
         String eol = "\r\n";
         String headers =
            "Type: request" + eol +
            "RpcId: " + rpc_id + eol +
            "SchemaVersion: 9" + eol +
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
               error("RPC error response: " + j.getString("text"));
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
         out.close();
         in.close();
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
            // Expects JSONArray of all SP's "subscriptionId" in the order you want them
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
            json.put("bodyId", bodyId_get());
            if (away) {
               json.put("levelOfDetail", "medium");
               req = RpcRequest("recordingSearch", false, json);
            } else {
               // Expects count=# in initial json, offset=# after first call
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
            json.put("isReceived", "true");
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
            if ( ! json.has("orderBy") )
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
            // These are required for wishlist types
            // NOTE: Advanced wishlist SPs don't contain idSetSource so don't work
            if (json.has("title"))
               o.put("title", json.getString("title"));
            if (json.has("folderingRules"))
               o.put("folderingRules", json.getString("folderingRules"));
            o.put("bodyId", bodyId_get());
            o.put("ignoreConflicts", "true");
            // This option allows season passes not currently in guide to be scheduled
            o.put("bodyGeneratesCandidates", true);
            req = RpcRequest("subscribe", false, o);
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
            //   "duration":1800,"time":"2012-11-16 09:30:00","channel":{channel info}
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
   
   // Get list of all shows (drilling down into folders for individual shows)
   public JSONArray MyShows(jobData job) {
      JSONArray allShows = new JSONArray();
      JSONObject result = null;

      try {
         // Top level list - run in a loop to grab all items
         Boolean stop = false;
         int offset = 0;
         JSONObject json = new JSONObject();
         json.put("count", 50);
         while ( ! stop ) {
            if (job != null && config.GUIMODE)
               config.gui.jobTab_UpdateJobMonitorRowOutput(job, "NP List");
            result = Command("MyShows", json);
            if (result != null && result.has("recordingFolderItem")) {
               JSONArray items = (JSONArray) result.get("recordingFolderItem");
               offset += items.length();
               json.put("offset", offset);
               if (items.length() == 0)
                  stop = true;
               JSONObject item;
               String title;
               for (int i=0; i<items.length(); ++i) {
                  title = null;
                  item = items.getJSONObject(i);
                  if (item.has("folderItemCount")) {
                     if (item.getInt("folderItemCount") > 0) {
                        // Type folder has to be further drilled down
                        if (item.has("title"))
                           title = item.getString("title");
                        if (title != null && title.equals("HD Recordings")) {
                           // Skip drilling into "HD Recordings" folder
                           continue;
                        }
                        result = Command(
                           "FolderIds",
                           new JSONObject("{\"parentRecordingFolderItemId\":\"" + item.get("recordingFolderItemId") + "\"}")
                        );
                        if (result != null) {
                           JSONArray ids = result.getJSONArray("objectIdAndType");
                           for (int j=0; j<ids.length(); ++j) {
                              JSONArray id = new JSONArray();
                              id.put(ids.get(j));
                              JSONObject s = new JSONObject();
                              s.put("objectIdAndType",id);
                              result = Command("SearchIds", s);
                              if (result != null) {
                                 s = result.getJSONArray("recordingFolderItem").getJSONObject(0);
                                 result = Command(
                                    "Search",
                                    new JSONObject("{\"recordingId\":\"" + s.get("childRecordingId") + "\"}")
                                 );
                                 if (result != null) {
                                    allShows.put(result);
                                 }
                              }
                           }
                        }
                     }
                  } else {
                     // Individual entry just add to items array
                     result = Command(
                        "Search",
                        new JSONObject("{\"recordingId\":\"" + item.getString("childRecordingId") + "\"}")
                     );
                     if (result != null)
                        allShows.put(result);
                  }
               } // for
            } else {
               // result == null
               stop = true;
            } // if
         } // while
      } catch (JSONException e) {
         error("rpc MyShows error - " + e.getMessage());
         return null;
      }

      return allShows;
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
            if (job != null && config.GUIMODE)
               config.gui.jobTab_UpdateJobMonitorRowOutput(job, "NP List");
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
               config.gui.jobTab_UpdateJobMonitorRowOutput(job, "ToDo list: " + offset);
               if ( jobMonitor.isFirstJobInMonitor(job) ) {
                  config.gui.setTitle("ToDo: " + offset + " " + config.kmttg);
               }
            }
         } // while         
      } catch (JSONException e) {
         error("rpc ToDo error - " + e.getMessage());
         return null;
      }

      return TableUtil.sortByOldestStartDate(allShows);
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
               config.gui.jobTab_UpdateJobMonitorRowOutput(job, "Will Not Record list: " + offset);
               if ( jobMonitor.isFirstJobInMonitor(job) ) {
                  config.gui.setTitle("Not rec: " + offset + " " + config.kmttg);
               }
            }
         } // while
      } catch (JSONException e) {
         error("rpc CancelledShows error - " + e.getMessage());
         return null;
      }

      return TableUtil.sortByOldestStartDate(allShows);
   }
   
   // Get list of all shows in Deleted state
   public JSONArray DeletedShows(jobData job) {
      JSONArray allShows = new JSONArray();
      JSONObject result = null;

      try {
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
               config.gui.jobTab_UpdateJobMonitorRowOutput(job, "Deleted list: " + offset);
               if ( jobMonitor.isFirstJobInMonitor(job) ) {
                  config.gui.setTitle("Deleted: " + offset + " " + config.kmttg);
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
                  // Find upcoming & conflicts entries for each SP and add data to each JSON
                  if (j.has("subscriptionId")) {
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
         json.put("subscriptionId", job.remote_orderIds);
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
   public JSONArray ChannelList(jobData job) {
      JSONObject result = null;
      try {
         // Top level list
         JSONObject json = new JSONObject();
         json.put("noLimit", "true");
         json.put("bodyId", bodyId_get());
         if (job != null && config.GUIMODE)
            config.gui.jobTab_UpdateJobMonitorRowOutput(job, "Channel List");
         result = Command("channelSearch", json);
         if (result != null && result.has("channel")) {
            // Only want received channels returned
            JSONArray a = new JSONArray();
            for (int i=0; i<result.getJSONArray("channel").length(); ++i) {
               json = result.getJSONArray("channel").getJSONObject(i);
               if (json.getBoolean("isReceived"))
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
   
   public JSONArray SeasonPremieres(JSONArray channelNumbers, jobData job, int total_days) {
      if (channelNumbers == null)
         return null;
      if (channelNumbers.length() == 0)
         return null;   
      
      JSONObject json;
      JSONArray data = new JSONArray();
      Date now = new Date();
      long start = now.getTime();
      long day_increment = 1*24*60*60*1000;
      long stop = start + day_increment;
      try {
         // Set shorter timeout since some requests fail for some reason (especially for Linux)
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
               if (result != null && result.has("gridRow")) {
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
                              data.put(json);
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
         if (data.length() == 0) {
            log.warn("No show premieres found.");
         } else {
            // Tag json entries in data that already have Season Passes scheduled
            config.gui.remote_gui.TagPremieresWithSeasonPasses(data);
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
      JSONObject collections = new JSONObject();
      int order = 0;
      try {
         Boolean stop = false;
         int offset = 0;
         int count = 50;
         
         // Update job monitor output column name
         if (job != null && config.GUIMODE) {
            config.gui.jobTab_UpdateJobMonitorRowOutput(job, "Keyword Search: " + keyword);
         }
         
         while ( ! stop ) {
            // Loop OfferSearch queries until no more results returned
            JSONObject json = new JSONObject();
            json.put("count", count);
            json.put("offset", offset);
            json.put("keyword", keyword);
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
         }
      } catch (JSONException e) {
         log.error("searchButtonCB failed - " + e.getMessage());
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
            if(title.equals(existing.getJSONObject(j).getString("title"))) {
               if (channel.length() > 0 && existing.getJSONObject(j).has("channel")) {
                  if (channel.equals(existing.getJSONObject(j).getString("channel"))) {
                     schedule = false;
                     existingSP = existing.getJSONObject(j);
                  }
               } else {
                  schedule = false;
                  existingSP = existing.getJSONObject(j);
               }
            }
         }
         
         // OK to subscribe
         if (schedule) {
            JSONObject o = config.gui.remote_gui.spOpt.promptUser(
               "(" + tivoName + ") " + "Create SP - " + title, null
            );
            if (o != null) {
               log.print("Scheduling SP: '" + title + "' on TiVo: " + tivoName);
               JSONObject idSetSource = new JSONObject();
               idSetSource.put("collectionId", json.getString("collectionId"));
               idSetSource.put("type", "seasonPassSource");
               idSetSource.put("channel", json.getJSONObject("channel"));
               o.put("idSetSource", idSetSource);   
               JSONObject result = Command("Seasonpass", o);
               if (result != null) {
                  log.print("success");
               }
            }
         } else {
            log.warn("Existing SP with same title found, prompting to modify instead.");
            if (existingSP != null) {
               JSONObject result = config.gui.remote_gui.spOpt.promptUser(
                  "(" + tivoName + ") " + "Modify SP - " + title, existingSP
               );
               if (result != null) {
                  if (Command("ModifySP", result) != null) {
                     log.warn("Modified SP '" + title + "' for TiVo: " + tivoName);
                  }
               }
            }
         }
      } catch (JSONException e) {
         log.error("SPschedule - " + e.getMessage());
      }
   }
      
   private void print(String message) {
      log.print(message);
   }
   
   private void error(String message) {
      log.error(message);
   }
}
