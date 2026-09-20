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

   protected static final String SchemaVersion = "14";
   protected static final String SchemaVersion_newer = "17";
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
   private final Object readyLock = new Object();
   // tivo.com can accept the socket and never authenticate it, which would otherwise park
   // every caller for good.
   private static final int AUTH_TIMEOUT = 30000;
   // Last resort for a connection that is still open but has gone silent: onClose releases
   // waiters the moment the socket goes, so nothing healthy ever reaches this.
   private static final int RESPONSE_TIMEOUT = 120000;
   // Neither half of connecting is bounded by default - the library leaves the socket connect
   // at 0, meaning no timeout at all, and connectBlocking() waits on a latch that a server
   // which takes the socket and never finishes the handshake never opens.
   private static final int CONNECT_TIMEOUT = 30000;
   
   private String tsn;
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
      Integer rpcId = rpcIdOf(request.split("\r\n\r\n")[0]);
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
                     wc.close();
                  }
               } else if (result.has("type") && result.getString("type").equals("error")) {
               	String err = "";
               	if (result.has("text")) {
               		err = ": " + result.getString("text");
               	}
               	log.error("Error establishing WS connection" + err);
               	wc.close();
               }
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
   
   // Still serialized per connection: RPCs have always gone out one at a time here and
   // nothing asks for more. What changed is that a reply, a close or a timeout all end the
   // wait - it used to end only on a reply.
   public synchronized JSONObject sendRequestAndWaitForResponse(String request) {
      Integer rpcId = rpcIdOf(request.split("\r\n\r\n")[0]);
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
         return new JSONObject(response);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         return null;
      } catch (JSONException e) {
         error("WS error " + e.getMessage());
         return null;
      } catch (RuntimeException e) {
         // send() throws when the socket has gone out from under us.
         error("WS error " + e.getMessage());
         return null;
      }
   }
   
   // Deliberately not synchronized on the instance: the bodyAuthenticate request is built on
   // the auth thread, and sendRequestAndWaitForResponse holds the instance lock while it waits
   // for that authentication to finish. Sharing one lock would have the caller block the very
   // handshake it is waiting on. The only shared state here is the request counter.
   public String RpcRequest(String type, Boolean monitor, JSONObject data) {
      try {
         String ResponseCount = "single";
         if (monitor)
            ResponseCount = "multiple";
         String bodyId = null;
         if (data.has("bodyId"))
            bodyId = (String) data.get("bodyId");
         String schema = "22";
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
      String[] parts = message.split("\r\n\r\n");
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
