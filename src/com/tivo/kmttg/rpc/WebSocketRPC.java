package com.tivo.kmttg.rpc;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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

import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

// Mind RPC over a websocket, one MRPC/2 message per websocket message, each reply matched to
// its request by RpcId. Shared by tivo.com (TiVoRPCWS) and the TiVo's own websocket on the
// LAN (TiVoRPCLocalWS), which differ only in how they authenticate, the headers they send
// and how a refused SchemaVersion falls back.
public abstract class WebSocketRPC extends WebSocketClient {

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
   // The server can accept the socket and never authenticate it, which would otherwise park
   // every caller for good.
   protected static final int AUTH_TIMEOUT = 30000;
   protected static final int PROBE_TIMEOUT = 5000;
   // Last resort for a connection that is still open but has gone silent: onClose releases
   // waiters the moment the socket goes, so nothing healthy ever reaches this.
   protected static final int RESPONSE_TIMEOUT = 120000;
   // Neither half of connecting is bounded by default - the library leaves the socket connect
   // at 0, meaning no timeout at all, and connectBlocking() waits on a latch that a server
   // which takes the socket and never finishes the handshake never opens.
   protected static final int CONNECT_TIMEOUT = 30000;

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

   protected WebSocketRPC(URI uri, Draft protocal, String tivoName, String IP, int port) {
      // No extra headers; the fourth argument is the socket connect timeout.
      super(uri, protocal, null, CONNECT_TIMEOUT);
      this.tivoName = tivoName;
      this.IP = IP;
      this.port = port;
   }

   static Draft_6455 mindrpcDraft() {
      return new Draft_6455(Collections.<IExtension>emptyList(),
            Collections.<IProtocol>singletonList(new Protocol("com.tivo.mindrpc.2")));
   }

   // Who is on the other end, for messages.
   protected abstract String peer();

   // bodyAuthenticate, on the thread onOpen starts. Sets ready on success; closes otherwise.
   protected abstract void authenticate() throws Exception;

   // The SchemaVersion header when a request doesn't name one.
   protected abstract int defaultSchema();

   // The headers after ResponseCount, each ending in CRLF.
   protected abstract String moreHeaders(String bodyId);

   // The version for a request that wants the box's newest rather than kmttg's own.
   abstract int negotiatedSchema(String type);

   // A reply that refuses the SchemaVersion the request went out at, answered: the request
   // sent again at a version that falls back, or the reply as it is. Null if a resend went
   // unanswered.
   protected String answer(String request, String response, boolean mayFallBack) throws InterruptedException {
      return response;
   }

   protected void sending(Integer rpcId) {
   }

   protected void closed(int code, String reason, boolean remote) {
      warn("WS closed with exit code " + code + " additional info: " + reason);
   }

   // False covers refused, timed out and a handshake that never completed. The library tears
   // its own connect attempt down on the way out, so there is nothing to close here -
   // waitForReady then reports the failure to the caller. Not a hard bound either: that
   // teardown waits on the connect thread, which is only bounded once it owns a socket, so a
   // name that will not resolve can still take longer than this.
   protected boolean open() throws InterruptedException {
      if (connectBlocking(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS))
         return true;
      error("Could not open a connection to " + peer());
      return false;
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

   static String requestTypeOf(String request) {
      for (String header : request.split("\r\n\r\n", 2)[0].split("\r\n")) {
         if (header.startsWith("RequestType: "))
            return header.substring(13).trim();
      }
      return null;
   }

   // Send one request and wait for the reply that quotes its RpcId. Null when the reply never
   // came: the socket closed, the wait ran out, or the message carried no body. Registering
   // before the send is what makes a reply that beats us to the wait still count.
   protected String exchange(String request, int timeout) throws InterruptedException {
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

   // True only once the server has accepted the credential. An open socket is not enough: a
   // rejected one leaves the connection open, and requests sent on it are simply never
   // answered - which is how a wrong tivo.com password used to look like a hung job.
   public boolean waitForReady() throws InterruptedException {
      synchronized (readyLock) {
         long deadline = System.currentTimeMillis() + AUTH_TIMEOUT;
         while (! authDone && isOpen()) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
               error("Timed out waiting for " + peer() + " to authenticate the connection");
               break;
            }
            readyLock.wait(remaining);
         }
      }
      return ready && isOpen();
   }

   @Override
   public void onOpen(ServerHandshake handshakedata) {
      Thread thread = new Thread(){
         public void run(){
            try {
               authenticate();
            } catch (Exception e) {
               // Named, not just described: the message alone is null for the whole NPE family.
               error("rpc Auth error - " + e);
               close();
            } finally {
               synchronized(readyLock){
                  authDone = true;
                  readyLock.notifyAll();
               }
            }
         }
      };
      thread.start();
   }

   public String getTsn() {
      return this.tsn;
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

   // The reply body exactly as the server sent it, error or not.
   public String sendRequestAndWaitForBody(String request) {
      return sendRequestAndWaitForBody(request, true);
   }

   public String sendRequestAndWaitForBody(String request, boolean mayFallBack) {
      Integer rpcId = rpcIdOf(request.split("\r\n\r\n", 2)[0]);
      try {
         if (! this.waitForReady()) {
            error("Not connected to " + peer() + " - dropping request " + rpcId);
            return null;
         }
         sending(rpcId);
         debug.print(request);
         String response = exchange(request, RESPONSE_TIMEOUT);
         debug.print(response);
         if (response != null)
            response = answer(request, response, mayFallBack);
         if (response == null) {
            error("No response from " + peer() + " to request " + rpcId);
            return null;
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
         String schema = String.valueOf(schemaVersion != null ? schemaVersion : defaultSchema());
         int id = rpc_id.incrementAndGet();
         String eol = "\r\n";
         String headers =
            "Type: request" + eol +
            "RpcId: " + id + eol +
            "SchemaVersion: " + schema + eol +
            "Content-Type: application/json" + eol +
            "RequestType: " + type + eol +
            "ResponseCount: " + ResponseCount + eol +
            moreHeaders(bodyId);

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
      closed(code, reason, remote);
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

   // Both servers answer in text frames; a binary one is read the same way rather than lost.
   @Override
   public void onMessage(ByteBuffer message) {
      onMessage(StandardCharsets.UTF_8.decode(message).toString());
   }

   @Override
   public void onError(Exception ex) {
      System.err.println("an ws error occurred:" + ex);
   }

}
