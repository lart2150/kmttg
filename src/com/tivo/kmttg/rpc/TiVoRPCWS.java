package com.tivo.kmttg.rpc;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.extensions.IExtension;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.protocols.IProtocol;
import org.java_websocket.protocols.Protocol;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.log;

public class TiVoRPCWS extends WebSocketClient {

   protected static final String SchemaVersion = "14";
   protected static final String SchemaVersion_newer = "17";
   protected int rpc_id = 0;
   protected final String tivoName;
   protected final String IP;
   protected final int port;
   protected Boolean ready = false;
   
   private String tsn;
   private HashMap<Integer, String> responseMap = new HashMap<Integer, String>();
   
   protected void error(String msg) {
      log.error(msg);
   }
   protected void print(String msg) {
      log.print(msg);
   }
   protected void warn(String msg) {
      log.warn(msg);
   }

   public TiVoRPCWS(URI uri, Draft protocal, String tivoName, String IP, int port) {
      super(uri, protocal);
      this.tivoName = tivoName;
      this.IP = IP;
      this.port = port;
   }


   public static TiVoRPCWS init(String tivoName, String IP, int port) throws URISyntaxException, InterruptedException {
      //System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE");
      Draft_6455 draft_mindrpc = new Draft_6455(Collections.<IExtension>emptyList(),
            Collections.<IProtocol>singletonList(new Protocol("com.tivo.mindrpc.2")));
      TiVoRPCWS ws = new TiVoRPCWS(
            new URI("wss://xmind-tp2.tivoservice.com:2196/"),
            draft_mindrpc,
            tivoName,
            IP,
            port
      );
      
      ws.connectBlocking();
      
      return ws;
   }
   
   public boolean waitForReady() throws InterruptedException {
      if (!this.isOpen()) {
         return false;
      }
      if (this.ready) {
         return true;
      }
      synchronized(this.ready){
         while (!this.ready) {
            if (!this.isOpen()) {
               return false;
            }
            this.ready.wait();
         }
         return this.isOpen();
      }
   }
   
   
   @Override
   public void onOpen(ServerHandshake handshakedata) {
      //send("Hello, it is me. Mario :)");
      log.print("WS connection started");
      TiVoRPCWS wc = this;
      Thread thread = new Thread(){
         public void run(){
            Boolean readyLock = wc.ready;
            try {
               JSONObject credential = new JSONObject();
               JSONObject h = new JSONObject();
               JSONObject domainToken = new JSONObject();
               domainToken.put("domain", "tivo");
               domainToken.put("type", "domainToken");
               domainToken.put("token", config.getDomainToken());
               
               credential.put("type", "domainTokenCredential");
               credential.put("domainToken", domainToken);
               h.put("credential", credential);
               String req = RpcRequest("bodyAuthenticate", false, h);
               String lock = "";
               synchronized(lock){
                  responseMap.put(rpc_id, lock);
                  wc.send(req);
                  lock.wait();
                  String response = responseMap.get(rpc_id);
                  responseMap.remove(rpc_id);
                  JSONObject result = new JSONObject(response);
                  if (result.has("status")) {
                     if (result.get("status").equals("success")) {
                        // Look for tivoName bodyId in deviceId JSONArray
                        boolean found = false;
                        if (result.has("deviceId")) {
                           JSONArray a = result.getJSONArray("deviceId");
                           for (int i=0; i<a.length(); ++i) {
                              JSONObject j = a.getJSONObject(i);
                              if (j.has("friendlyName")) {
                                 if (j.getString("friendlyName").equals(tivoName) && j.has("id")) {
                                    found = true;
                                    config.bodyId_set(IP, port, j.getString("id"));
                                    String tsn = j.getString("id");
                                    tsn = tsn.replaceFirst("tsn:", "");
                                    if (config.getTsn(tivoName) != tsn) {
                                       config.setTsn(tivoName, tsn);
                                    }
                                    wc.tsn = tsn;
                                    wc.ready = true;
                                    break;
                                 }
                              }
                           }
                        }
                        if (! found) {
                           if (tivoName == null) {
                              wc.tsn = "-";
                              wc.ready = true;
                           } else {
                              // Couldn't get id from response so try getting tsn from kmttg
                              String tsn = config.getTsn(tivoName);
                              if (tsn == null) {
                                 log.error("Can't determine bodyId for TiVo: " + tivoName);
                                 wc.close();
                              } else {
                                 wc.tsn = tsn;
                                 config.bodyId_set(IP, port, "tsn:" + tsn);
                                 wc.ready = true;
                              }
                           }
                        }
                     }
                  }
               }
            } catch (Exception e) {
               error("rpc Auth error - " + e.getMessage());
            }
            synchronized(readyLock){
               readyLock.notifyAll();
            }
         }
      };
      thread.start();
   }
   
   public String getTsn() {
      return this.tsn;
   }
   
   public synchronized JSONObject sendRequestAndWaitForResponse(String request) {
      String[] parts = request.split("\r\n\r\n");
      String[] headers = parts[0].split("\r\n");
      
      Integer rpcId = Integer.valueOf(0);
      for (String header : headers) {
         if (header.toLowerCase().startsWith("rpcid:")) {
            String[] rpcHeader = header.split(" ");
            rpcId = Integer.valueOf(rpcHeader[1]);
            break;
         }
      }
      try {
         this.waitForReady();
      } catch (InterruptedException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
      log.print("WS Sending request: " + rpcId);
      System.out.println(request);
      
      String lock = "";
      synchronized(lock){
         responseMap.put(rpcId, lock);
         this.send(request);
         try {
            lock.wait();

            String response = responseMap.get(rpcId);
            System.out.println(response);
            responseMap.remove(rpcId);
            JSONObject result = new JSONObject(response);
            return result;
         } catch (InterruptedException|JSONException e) {
            // TODO Auto-generated catch block
            log.error("WS error " + e.getMessage());
            return null;
         }
      }
   }
   
   public synchronized String RpcRequest(String type, Boolean monitor, JSONObject data) {
      try {
         String ResponseCount = "single";
         if (monitor)
            ResponseCount = "multiple";
         String bodyId = null;
         if (data.has("bodyId"))
            bodyId = (String) data.get("bodyId");
         String schema = "22";
         rpc_id++;
         String eol = "\r\n";
         String headers =
            "Type: request" + eol +
            "RpcId: " + rpc_id + eol +
            "SchemaVersion: " + schema + eol +
            "Content-Type: application/json" + eol +
            "RequestType: " + type + eol +
            "ResponseCount: " + ResponseCount + eol;

         if (bodyId != null) {
            headers += "BodyId: tsn:" + bodyId + eol;
         }

         data.put("type", type);

         String body = data.toString();
         String start_line = String.format("MRPC/2 %d %d", headers.length()+2, body.length());
         return start_line + eol + headers + eol + body;
      } catch (Exception e) {
         log.error("WS RpcRequest error: " + e.getMessage());
         return null;
      }
   }


   @Override
   public void onClose(int code, String reason, boolean remote) {
      log.warn("WS closed with exit code " + code + " additional info: " + reason);
      this.ready = false;
   }

   @Override
   public void onMessage(String message) {
      String[] parts = message.split("\r\n\r\n");
      String[] headers = parts[0].split("\r\n");
      
      Integer rpcId = Integer.valueOf(0);
      for (String header : headers) {
         if (header.toLowerCase().startsWith("rpcid:")) {
            String[] rpcHeader = header.split(" ");
            rpcId = Integer.valueOf(rpcHeader[1]);
            break;
         }
      }
      
      
      String lock = this.responseMap.get(rpcId);
      if (lock == null) {
         error("Unknown rpcid " + rpcId);
      }

      this.responseMap.put(rpcId, parts[1]);
      synchronized(lock) {
         lock.notify();
      }
   }

   @Override
   public void onMessage(ByteBuffer message) {
      
   }

   @Override
   public void onError(Exception ex) {
      System.err.println("an ws error occurred:" + ex);
   }

}
