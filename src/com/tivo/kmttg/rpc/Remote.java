package com.tivo.kmttg.rpc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.log;

public class Remote {
   public Boolean debug = false;
   public Boolean success = true;
   private int port = 1413;
   private String MAK = null;
   private int timeout = 120; // read timeout in secs
   private int rpc_id = 0;
   private int session_id = 0;
   private Socket socket = null;
   private BufferedReader in = null;
   private BufferedWriter out = null;
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
   
   public final SSLSocketFactory getSocketFactory() {
     if ( sslSocketFactory == null ) {
       try {
         TrustManager[] tm = new TrustManager[] { new NaiveTrustManager() };
         SSLContext context = SSLContext.getInstance("SSL");
         context.init( new KeyManager[0], tm, new SecureRandom( ) );

         sslSocketFactory = (SSLSocketFactory) context.getSocketFactory ();

       } catch (KeyManagementException e) {
         error("No SSL algorithm support: " + e.getMessage()); 
       } catch (NoSuchAlgorithmException e) {
         error("Exception when setting up the Naive key management." + e.getMessage());
       }
     }
     return sslSocketFactory;
   }
   
   public Remote(String tivoName, String MAK) {
      try {
         this.MAK = MAK;
         String IP = config.TIVOS.get(tivoName);
         if (IP == null)
            IP = tivoName;
         getSocketFactory();
         session_id = new Random(0x27dc20).nextInt();
         int use_port = port;
         String wan_port = config.getWanSetting(tivoName, "ipad");
         if (wan_port != null)
            use_port = Integer.parseInt(wan_port);
         socket = sslSocketFactory.createSocket(IP, use_port);
         socket.setSoTimeout(timeout*1000);
         in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
         out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
         if ( ! Auth() )
            success = false;

      } catch (Exception e) {
         error("rpc Remote - " + e.getMessage());
         success = false;
      }
   }
   
   public String RpcRequest(String type, Boolean monitor, JSONObject data) {
      try {
         String ResponseCount = "single";
         if (monitor)
            ResponseCount = "multiple";
         String body_id = "";
         if (data.has("body_id"))
            body_id = (String) data.get("body_id");
         rpc_id++;
         String eol = "\r\n";
         String headers =
            "Type: request" + eol +
            "RpcId: " + rpc_id + eol +
            "SchemaVersion: 7" + eol +
            "Content-Type: application/json" + eol +
            "RequestType: " + type + eol +
            "ResponseCount: " + ResponseCount + eol +
            "BodyId: " + body_id + eol +
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
   
   public Boolean Write(String data) {
      try {
         if (debug) {
            print("WRITE " + data);
         }
         out.write(data);
         out.flush();
      } catch (IOException e) {
         error("rpc Write error - " + e.getMessage());
         return false;
      }
      return true;
   }
   
   public JSONObject Read() {
      String buf = "";
      Integer head_len = null;
      Integer body_len = null;
      
      Pattern p = Pattern.compile("MRPC/2\\s+(\\d+)\\s+(\\d+)");
      Matcher match;
      try {
         while(true) {
            buf += in.readLine();
            match = p.matcher(buf);
            if (match.matches()) {
               head_len = Integer.parseInt(match.group(1));
               body_len = Integer.parseInt(match.group(2));
               break;
            }
         }
         buf = "";
      
         if (head_len != null && body_len != null) {
            char[] cb = new char[1024];
            int num;
            while(buf.length() < head_len + body_len) {
               num = in.read(cb, 0, cb.length);
               buf += String.copyValueOf(cb, 0, num);
            }
            if (debug) {
               print("READ " + buf);
            }
            // Pull out IsFinal value from header
            Boolean IsFinal;
            if (buf.substring(0, head_len-1).contains("IsFinal: true"))
               IsFinal = true;
            else
               IsFinal = false;
            
            // Return json contents with IsFinal flag added
            buf = buf.substring(head_len, head_len + body_len);
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
   
   public void disconnect() {
      try {
         out.close();
         in.close();
      } catch (IOException e) {
         error("rpc disconnect error - " + e.getMessage());
      }
   }
   
   public JSONObject Key(String type, JSONObject json) {
      String req = null;
      if (json == null)
         json = new JSONObject();
      try {
         if (type.equals("playback")) {
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
         else if (type.equals("delete")) {
            // Delete an existing recording
            // Expects "recordingId" of type JSONArray in json
            json.put("state", "deleted");
            json.put("bodyId", "-");
            req = RpcRequest("recordingUpdate", false, json);
         }
         else if (type.equals("cancel")) {
            // Cancel a recording in ToDo list
            // Expects "recordingId" of type JSONArray in json
            json.put("state", "cancelled");
            json.put("bodyId", "-");
            req = RpcRequest("recordingUpdate", false, json);
         }
         else if (type.equals("prioritize")) {
            // Re-prioritize a season pass
            // Expects "subscriptionId" of type JSONArray in json
            // Also expects "priority" of type int in json (2512=>take place of current 2512)
            json.put("bodyId", "-");
            req = RpcRequest("subscriptionsReprioritize", false, json);
         }
         else if (type.equals("Search")) {
            // Individual item search
            // Expects "recordingId" in json
            json.put("levelOfDetail", "medium");
            json.put("bodyId", "-");
            req = RpcRequest("recordingSearch", false, json);
         }
         else if (type.equals("FolderIds")) {
            // Folder id search
            // Expects "parentRecordingFolderItemId" in json
            json.put("format", "idSequence");
            json.put("bodyId", "-");
            // NOTE: Since this format is idSequence perhaps monitor should be true?
            req = RpcRequest("recordingFolderItemSearch", false, json);
         }
         else if (type.equals("SearchIds")) {
            // Expects "objectIdAndType" in json
            json.put("bodyId", "-");
            req = RpcRequest("recordingFolderItemSearch", false, json);
         }
         else if (type.equals("MyShows")) {
            json.put("bodyId", "-");
            json.put("noLimit", "true");
            req = RpcRequest("recordingFolderItemSearch", false, json);
         }
         else if (type.equals("ToDo")) {
            json.put("format", "idSequence");
            json.put("bodyId", "-");
            json.put("noLimit", "true");
            json.put("state", new JSONArray("[\"scheduled\"]"));
            req = RpcRequest("recordingSearch", false, json);
         }
         else if (type.equals("ToDoIds")) {
            // Expects "objectIdAndType" in json
            json.put("bodyId", "-");
            req = RpcRequest("recordingSearch", false, json);
         }
         else if (type.equals("SeasonPasses")) {
            json.put("levelOfDetail", "medium");
            json.put("bodyId", "-");
            json.put("noLimit", "true");
            req = RpcRequest("subscriptionSearch", false, json);
         }
         else if (type.equals("subscribe")) {
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
            o.put("bodyId", "-");
            o.put("ignoreConflicts", "true");
            req = RpcRequest("subscribe", false, o);
         }
         else if (type.equals("position")) {
            json.put("throttleDelay", 1000);
            req = RpcRequest("videoPlaybackInfoEventRegister", false, json);
         }
         else if (type.equals("jump")) {
            // Expects "offset" in json
            req = RpcRequest("videoPlaybackPositionSet", false, json);
         }
         else if (type.equals("sysInfo")) {
            // Returns userDiskSize among other info
            json.put("bodyId", "-");
            req = RpcRequest("bodyConfigSearch", false, json);
         }
         else if (type.equals("tunerInfo")) {
            // Returns info about both tuners
            req = RpcRequest("tunerStateEventRegister", true, json);
         }
         // Other interesting ones to look at:
         // unifiedItemSearch
         //  followed by recordingSearch with offerId & state=[scheduled] to find cancellations
         else {
            // Not recognized => just use type
            req = RpcRequest(type, false, json);
         }
         
         if (req != null) {
            Write(req);
            return Read();
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
   public JSONArray MyShows() {
      JSONArray allShows = new JSONArray();
      JSONObject result = null;

      try {
         // Top level list
         result = Key("MyShows", new JSONObject());
         if (result != null && result.has("recordingFolderItem")) {
            JSONArray items = (JSONArray) result.get("recordingFolderItem");
            JSONObject item;
            String title;
            for (int i=0; i<items.length(); ++i) {
               title = null;
               item = items.getJSONObject(i);
               if (item.has("folderItemCount")) {
                  // Type folder has to be further drilled down
                  if (item.has("title"))
                     title = item.getString("title");
                  if (title != null && title.equals("HD Recordings")) {
                     // Skip drilling into "HD Recordings" folder
                     continue;
                  }
                  result = Key(
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
                        result = Key("SearchIds", s);
                        if (result != null) {
                           s = result.getJSONArray("recordingFolderItem").getJSONObject(0);
                           result = Key(
                                 "Search",
                                 new JSONObject("{\"recordingId\":\"" + s.get("childRecordingId") + "\"}")
                              );
                           if (result != null) {
                              allShows.put(result);
                           }
                        }
                     }
                  }
               } else {
                  // Individual entry just add to items array                  
                  result = Key(
                     "Search",
                     new JSONObject("{\"recordingId\":\"" + item.get("childRecordingId") + "\"}")
                  );
                  if (result != null)
                     allShows.put(result);
               }
            } // for
         } // if
      } catch (JSONException e) {
         error("rpc MyShows error - " + e.getMessage());
         return null;
      }

      return allShows;
   }
   
   // Get to do list of all shows
   public JSONArray ToDo() {
      JSONArray allShows = new JSONArray();
      JSONObject result = null;

      try {
         // Top level list
         result = Key("ToDo", new JSONObject());
         if (result != null && result.has("objectIdAndType")) {
            JSONArray items = result.getJSONArray("objectIdAndType");
            for (int j=0; j<items.length(); ++j) {
               JSONArray id = new JSONArray();
               id.put(items.get(j));
               JSONObject s = new JSONObject();
               s.put("objectIdAndType",id);
               result = Key("ToDoIds", s);
               if (result != null && result.has("recording")) {
                  s = result.getJSONArray("recording").getJSONObject(0);
                  allShows.put(s);
                  //print(s.toString());
               }
            }
         } // if
      } catch (JSONException e) {
         error("rpc ToDo error - " + e.getMessage());
         return null;
      }

      return allShows;
   }
   
   // Get all season passes
   public JSONArray SeasonPasses() {
      JSONObject result = null;
      result = Key("SeasonPasses", new JSONObject());
      if (result != null && result.has("subscription")) {
         try {
            return result.getJSONArray("subscription");
         } catch (JSONException e) {
            error("rpc SeasonPasses error - " + e.getMessage());
            return null;
         }
      }
      return null;
   }
   
   private void print(String message) {
      log.print(message);
   }
   
   private void error(String message) {
      log.error(message);
   }
}
