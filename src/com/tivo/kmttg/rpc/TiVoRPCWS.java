package com.tivo.kmttg.rpc;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class TiVoRPCWS extends WebSocketClient {

   // What tivo.com has always been asked at, and where a refused request falls back to.
   static final int SCHEMA_DEFAULT = 22;
   // Raised to the box's maxMindVersion once the connection has authenticated.
   private volatile int schema = SCHEMA_DEFAULT;
   // Atomic rather than guarded by the instance lock: the auth thread numbers its own request,
   // and a caller that arrives before authentication finishes holds that lock while it waits.
   protected final AtomicInteger rpc_id = new AtomicInteger(0);
   protected final String tivoName;
   protected final String IP;
   protected final int port;
   // Written by the auth thread onOpen starts, read by whoever is waiting on it.
   protected volatile boolean ready = false;
   // Set when the bodyAuthenticate exchange finishes, however it ends, so a caller arriving
   // afterwards is told the answer instead of waiting for a notify that has already been sent.
   private volatile boolean authDone = false;
   // tivo.com answered bodyAuthenticate and said no, as opposed to never answering at all -
   // the one failure a fresh token can fix.
   private volatile boolean authRejected = false;
   private final Object readyLock = new Object();
   // tivo.com can accept the socket and never authenticate it, which would otherwise park
   // every caller for good.
   private static final int AUTH_TIMEOUT = 30000;
   private static final int PROBE_TIMEOUT = 5000;
   // Last resort for a connection that is still open but has gone silent: onClose releases
   // waiters the moment the socket goes, so nothing healthy ever reaches this.
   private static final int RESPONSE_TIMEOUT = 120000;
   // Neither half of connecting is bounded by default - the library leaves the socket connect
   // at 0, meaning no timeout at all, and connectBlocking() waits on a latch that a server
   // which takes the socket and never finishes the handshake never opens.
   private static final int CONNECT_TIMEOUT = 30000;
   
   String tsn;
   // One in-flight request: the monitor its caller waits on and the slot the reply lands in.
   // A monitor each rather than one shared between them, so a reply wakes the thread that
   // asked for it - and so a close can wake every one of them at once.
   private static class Pending {
      String response;
      boolean done;
   }

   private final Map<Integer,Pending> pending = new ConcurrentHashMap<Integer,Pending>();
   
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
      // No extra headers; the fourth argument is the socket connect timeout.
      super(uri, protocal, null, CONNECT_TIMEOUT);
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
      
      // False covers refused, timed out and a handshake that never completed. The library
      // tears its own connect attempt down on the way out, so there is nothing to close here -
      // waitForReady then reports the failure to the caller. Not a hard bound either: that
      // teardown waits on the connect thread, which is only bounded once it owns a socket, so
      // a name that will not resolve can still take longer than this.
      if (! ws.connectBlocking(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)) {
         log.error("Could not open a connection to tivo.com");
      }
      
      return ws;
   }
   
   // The RpcId a request or a response carries, read off its header block. Zero for anything
   // this cannot read: onMessage runs on the library's read thread, where an exception ends
   // the connection, so a malformed header has to come back as "belongs to nobody".
   private static Integer rpcIdOf(String headers) {
      for (String header : headers.split("\r\n")) {
         if (header.toLowerCase().startsWith("rpcid:")) {
            try {
               return Integer.valueOf(header.substring(6).trim());
            } catch (NumberFormatException e) {
               break;
            }
         }
      }
      return Integer.valueOf(0);
   }

   // Send one request and wait for the reply that quotes its RpcId. Null when the reply never
   // came: the socket closed, the wait ran out, or the message carried no body. Registering
   // before the send is what makes a reply that beats us to the wait still count.
   private String exchange(String request, int timeout) throws InterruptedException {
      Integer rpcId = rpcIdOf(request.split("\r\n\r\n", 2)[0]);
      Pending p = new Pending();
      pending.put(rpcId, p);
      try {
         send(request);
         synchronized (p) {
            long deadline = System.currentTimeMillis() + timeout;
            while (! p.done) {
               long remaining = deadline - System.currentTimeMillis();
               if (remaining <= 0) break;
               p.wait(remaining);
            }
            return p.response;
         }
      } finally {
         pending.remove(rpcId);
      }
   }

   // True only once tivo.com has accepted the domain token. An open socket is not enough: a
   // rejected token leaves the connection open, and requests sent on it are simply never
   // answered - which is how a wrong tivo.com password used to look like a hung job.
   public boolean waitForReady() throws InterruptedException {
      synchronized (readyLock) {
         long deadline = System.currentTimeMillis() + AUTH_TIMEOUT;
         while (! authDone && isOpen()) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
               error("Timed out waiting for tivo.com to authenticate the connection");
               break;
            }
            readyLock.wait(remaining);
         }
      }
      return ready && isOpen();
   }
   
   
   @Override
   public void onOpen(ServerHandshake handshakedata) {
      log.print("WS connection started");
      TiVoRPCWS wc = this;
      Thread thread = new Thread(){
         public void run(){
            String token = config.getDomainToken();
            try {
               JSONObject credential = new JSONObject();
               JSONObject h = new JSONObject();
               JSONObject domainToken = new JSONObject();
               domainToken.put("domain", "tivo");
               domainToken.put("type", "domainToken");
               domainToken.put("token", token);
               
               credential.put("type", "domainTokenCredential");
               credential.put("domainToken", domainToken);
               h.put("credential", credential);
               String req = RpcRequest("bodyAuthenticate", false, h);
               String response = wc.exchange(req, AUTH_TIMEOUT);
               if (response == null) {
                  log.error("Timed out waiting for tivo.com to authenticate the connection");
                  wc.close();
                  return;
               }
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
                                 if (! tsn.equals(config.getTsn(tivoName))) {
                                    config.setTsn(tivoName, tsn);
                                 }
                                 wc.tsn = tsn;
                                 wc.ready = true;
                                 log.print("WS connection established");
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
                  } else {
                     // Anything but success leaves an open but unusable socket, and it is the
                     // only account of why the job is about to fail - saying nothing here is
                     // how a rejected login used to surface as "could not connect".
                     log.error("tivo.com refused the connection: " + result.get("status"));
                     wc.authRejected = true;
                     wc.close();
                  }
               } else if (result.has("type") && result.getString("type").equals("error")) {
               	String err = "";
               	if (result.has("text")) {
               		err = ": " + result.getString("text");
               	}
               	log.error("Error establishing WS connection" + err);
               	// Only an error about the token itself ("Domain token invalid") is worth a fresh
               	// login. A new login replaces the token every other session is using, so a
               	// passing server error must not trigger one.
               	wc.authRejected = isTokenError(result);
               	wc.close();
               }
               // Before authDone, so nobody sends at the default while this is being asked.
               if (wc.ready)
                  wc.negotiateSchema();
            } catch (Exception e) {
               // Named, not just described: the message alone is null for the whole NPE family.
               error("rpc Auth error - " + e);
               wc.close();
            } finally {
               synchronized(wc.readyLock){
                  wc.authDone = true;
                  wc.readyLock.notifyAll();
               }
            }
         }
      };
      thread.start();
   }
   
   public String getTsn() {
      return this.tsn;
   }

   public boolean isAuthRejected() {
      return authRejected;
   }

   static boolean isTokenError(JSONObject error) {
      String said = (error.optString("code") + " " + error.optString("text")).toLowerCase();
      return said.contains("token") || said.contains("auth");
   }

   private static final String TYPE_KEY = "tivo.com type:";

   private int schemaFor(String type) {
      Integer refused = SchemaVersions.cached(TYPE_KEY + type);
      return refused != null ? refused : schema;
   }

   private static String requestTypeOf(String request) {
      for (String header : request.split("\r\n\r\n", 2)[0].split("\r\n")) {
         if (header.startsWith("RequestType: "))
            return header.substring(13).trim();
      }
      return null;
   }

   // As TiVoRPC does on the LAN, but asked through tivo.com by the box's bodyId. Once per TiVo
   // per run; anything short of a maxMindVersion above the default leaves it where it was.
   void negotiateSchema() {
      if (tsn == null || tsn.equals("-"))
         return;
      String key = "tivo.com:" + tsn;
      Integer known = SchemaVersions.cached(key);
      if (known == null) {
         try {
            JSONObject data = new JSONObject();
            data.put("bodyId", tsn);
            // Short, and inside the auth window every caller is waiting on. Unanswered counts as
            // no answer for the run: the box may be offline, and each connection waiting for
            // it again would put this delay in front of every one of them.
            String response = exchange(RpcRequest("bodyConfigSearch", false, data, SchemaVersions.PROBE), PROBE_TIMEOUT);
            known = Math.max(SCHEMA_DEFAULT, response == null ? 0 : SchemaVersions.maxMindVersion(response));
         } catch (Exception e) {
            warn("SchemaVersion probe failed - " + e);
            return;
         }
         SchemaVersions.remember(key, known);
         log.print(tivoName + " (tivo.com): SchemaVersion " + known);
      }
      schema = known;
   }

   // Not serialized: each request waits on its own Pending and send() writes under the
   // library's own lock, so the web server's pool can have several in flight on one
   // connection. A reply, a close or a timeout all end the wait.
   public JSONObject sendRequestAndWaitForResponse(String request) {
      return sendRequestAndWaitForResponse(request, true);
   }

   // mayFallBack is false for a request sent at a version the caller chose: its refusal is
   // answered as it is, and falls nothing back.
   public JSONObject sendRequestAndWaitForResponse(String request, boolean mayFallBack) {
      String response = sendRequestAndWaitForBody(request, mayFallBack);
      if (response == null)
         return null;
      try {
         return new JSONObject(response);
      } catch (JSONException e) {
         error("WS error " + e.getMessage());
         return null;
      }
   }

   // The reply body exactly as tivo.com sent it, error or not.
   public String sendRequestAndWaitForBody(String request) {
      return sendRequestAndWaitForBody(request, true);
   }

   public String sendRequestAndWaitForBody(String request, boolean mayFallBack) {
      Integer rpcId = rpcIdOf(request.split("\r\n\r\n", 2)[0]);
      try {
         if (! this.waitForReady()) {
            error("Not connected to tivo.com - dropping request " + rpcId);
            return null;
         }
         log.print("WS Sending request: " + rpcId);
         debug.print(request);
         String response = exchange(request, RESPONSE_TIMEOUT);
         debug.print(response);
         if (response == null) {
            error("No response from tivo.com to request " + rpcId);
            return null;
         }
         // A refusal is usually one of tivo.com's own services stopping short of the box's
         // version, so it is that request type that falls back, not the whole connection.
         String type = requestTypeOf(request);
         int sentAt = SchemaVersions.of(request);
         if (mayFallBack && sentAt > SCHEMA_DEFAULT && type != null
               && SchemaVersions.isUnsupported(response)) {
            log.warn(type + ": SchemaVersion " + sentAt + " refused by tivo.com - using " + SCHEMA_DEFAULT);
            SchemaVersions.remember(TYPE_KEY + type, SCHEMA_DEFAULT);
            response = exchange(SchemaVersions.withSchema(request, SCHEMA_DEFAULT), RESPONSE_TIMEOUT);
            if (response == null) {
               error("No response from tivo.com to request " + rpcId);
               return null;
            }
         }
         return response;
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         return null;
      } catch (RuntimeException e) {
         // send() throws when the socket has gone out from under us.
         error("WS error " + e.getMessage());
         return null;
      }
   }
   
   // Deliberately not synchronized: requests are built on the auth thread and on every web
   // server thread sharing the connection. The only shared state here is the request counter.
   public String RpcRequest(String type, Boolean monitor, JSONObject data) {
      return RpcRequest(type, monitor, data, null);
   }

   // schemaVersion, when given, is the header for this one request, whatever was negotiated.
   public String RpcRequest(String type, Boolean monitor, JSONObject data, Integer schemaVersion) {
      try {
         String ResponseCount = "single";
         if (monitor)
            ResponseCount = "multiple";
         String bodyId = null;
         if (data.has("bodyId"))
            bodyId = (String) data.get("bodyId");
         String schema = String.valueOf(schemaVersion != null ? schemaVersion : schemaFor(type));
         int id = rpc_id.incrementAndGet();
         String eol = "\r\n";
         String headers =
            "Type: request" + eol +
            "RpcId: " + id + eol +
            "SchemaVersion: " + schema + eol +
            "Content-Type: application/json" + eol +
            "RequestType: " + type + eol +
            "ResponseCount: " + ResponseCount + eol;

         if (bodyId != null) {
            // kmttg's own callers pass a bare TSN; a raw request may already carry the prefix.
            if (! bodyId.startsWith("tsn:"))
               bodyId = "tsn:" + bodyId;
            headers += "BodyId: " + bodyId + eol;
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
      synchronized (readyLock) {
         authDone = true;
         readyLock.notifyAll();
      }
      // Marked done rather than left to notice the socket has gone: isOpen() still answers
      // true in here, because the library flips readyState only once this returns.
      for (Pending p : pending.values()) {
         synchronized (p) {
            p.done = true;
            p.notifyAll();
         }
      }
   }

   @Override
   public void onMessage(String message) {
      // Limit 2: everything past the blank line is the body, and a JSON body is free to
      // contain a blank line of its own - splitting on every one truncates it.
      String[] parts = message.split("\r\n\r\n", 2);
      Integer rpcId = rpcIdOf(parts[0]);

      Pending p = this.pending.get(rpcId);
      if (p == null) {
         // Not an error: a monitored request is answered more than once by design, and only
         // the first answer still has a caller waiting on it.
         print("No one waiting on rpcid " + rpcId);
         return;
      }
      synchronized (p) {
         p.response = parts.length > 1 ? parts[1] : null;
         p.done = true;
         p.notifyAll();
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
