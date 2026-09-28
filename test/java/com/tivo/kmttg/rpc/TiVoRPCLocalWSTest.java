package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.Test;

import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;

// The TiVo's local websocket speaks the TLS port's dialect over tivo.com's transport: the
// requests have to look like TiVoRPC's, and a refused SchemaVersion has to fall back the way
// TiVoRPC does, while replies are matched by RpcId as they are through tivo.com.
public class TiVoRPCLocalWSTest {

   private static final Duration PATIENCE = Duration.ofSeconds(20);
   private static final String UNSUPPORTED =
      "{\"type\":\"error\",\"code\":\"schemaVersion\",\"text\":\"Unsupported schema version\"}";

   private static String reply(String request, String body) {
      String id = request.replaceAll("(?s).*RpcId: (\\d+).*", "$1");
      return "MRPC/2 0 0\r\nContent-Type: application/json\r\nIsFinal: true\r\nRpcId: " + id
         + "\r\nType: response\r\n\r\n" + body;
   }

   // A TiVo answering from a queue, with no socket.
   private static class Box extends TiVoRPCLocalWS {
      final LinkedList<String> replies = new LinkedList<String>();
      final List<String> sent = new ArrayList<String>();

      Box(boolean rpcOld, String... replies) throws Exception {
         super("Bolt", "127.0.0.1", 1, "0123456789", rpcOld);
         Collections.addAll(this.replies, replies);
      }

      public boolean isOpen() {
         return true;
      }

      public boolean waitForReady() {
         return true;
      }

      public void send(String text) {
         sent.add(text);
         onMessage(reply(text, replies.removeFirst()));
      }

      List<Integer> sentAt() {
         List<Integer> out = new ArrayList<Integer>();
         for (String s : sent)
            out.add(SchemaVersions.of(s));
         return out;
      }
   }

   @Test
   void requestsCarryTheHeadersTheTlsPortGets() throws Exception {
      Box box = new Box(false, "{\"type\":\"success\"}");
      JSONObject data = new JSONObject();
      data.put("bodyId", "tsn:8490001");
      box.sendRequestAndWaitForResponse(box.RpcRequest("recordingSearch", false, data));
      String req = box.sent.get(0);
      assertTrue(req.contains("SchemaVersion: 17\r\n"), req);
      // As given - tivo.com's "tsn:" prefixing is not for the box itself
      assertTrue(req.contains("BodyId: tsn:8490001\r\n"), req);
      assertTrue(req.contains("X-ApplicationName: Quicksilver\r\n"), req);
      assertTrue(req.contains("X-ApplicationSessionId: 0x"), req);

      Box none = new Box(false, "{\"type\":\"success\"}");
      none.sendRequestAndWaitForResponse(none.RpcRequest("bodyConfigSearch", false, new JSONObject()));
      assertTrue(none.sent.get(0).contains("BodyId: \r\n"), "a blank BodyId, as TiVoRPC sends it");
   }

   @Test
   void aRefusalAt17FallsBackTo14AndStaysThere() throws Exception {
      Box box = new Box(false, UNSUPPORTED, "{\"type\":\"success\"}", "{\"type\":\"success\"}");
      JSONObject r = box.sendRequestAndWaitForResponse(box.RpcRequest("channelSearch", false, new JSONObject()));
      assertEquals("success", r.getString("type"));
      assertTrue(box.rpcOld, "Remote saves this to config");
      box.sendRequestAndWaitForResponse(box.RpcRequest("channelSearch", false, new JSONObject()));
      assertEquals(List.of(17, 14, 14), box.sentAt());
   }

   @Test
   void aRefusalAtTheNegotiatedVersionFallsBackTo17NotTo14() throws Exception {
      String bolt = "{\"type\":\"bodyConfigList\",\"bodyConfig\":[{\"maxMindVersion\":42}]}";
      Box box = new Box(false, bolt, UNSUPPORTED, "{\"type\":\"success\"}");
      SchemaVersions.forget("127.0.0.1:1");
      try {
         int v = box.negotiatedSchema("channelSearch");
         assertEquals(42, v);
         box.sendRequestAndWaitForResponse(box.RpcRequest("channelSearch", false, new JSONObject(), v));
         assertEquals(List.of(SchemaVersions.PROBE, 42, 17), box.sentAt());
         assertFalse(box.rpcOld);
         assertEquals(17, box.negotiatedSchema("channelSearch"));
      } finally {
         SchemaVersions.forget("127.0.0.1:1");
      }
   }

   // Unlike tivo.com's, a probe the box never answered is no answer for the run: this
   // connection stays at 17 and the next one asks again.
   @Test
   void anUnansweredProbeIsNotRemembered() throws Exception {
      Box silent = new Box(false) {
         public void send(String text) {
            sent.add(text);
         }
      };
      SchemaVersions.forget("127.0.0.1:1");
      try {
         assertTimeoutPreemptively(PATIENCE, () ->
            assertEquals(17, silent.negotiatedSchema("channelSearch")));
         assertNull(SchemaVersions.cached("127.0.0.1:1"));
      } finally {
         SchemaVersions.forget("127.0.0.1:1");
      }
   }

   @Test
   void aTivoReachedOverTheWanStaysOnTls() {
      boolean was = config.rpcWebsocket;
      config.rpcWebsocket = true;
      try {
         assertTrue(Remote.useLocalWebsocket("Bolt"));
         config.setWanSetting("Bolt", "rpc", "8413");
         assertFalse(Remote.useLocalWebsocket("Bolt"), "the websocket would send the MAK in the clear");
         config.rpcWebsocket = false;
         config.setWanSetting("Bolt", "rpc", "");
         assertFalse(Remote.useLocalWebsocket("Bolt"));
      } finally {
         config.rpcWebsocket = was;
         config.setWanSetting("Bolt", "rpc", "");
      }
   }

   @Test
   void aVersionTheCallerChoseIsAnsweredAsItIs() throws Exception {
      Box box = new Box(false, UNSUPPORTED);
      String body = box.sendRequestAndWaitForBody(box.RpcRequest("channelSearch", false, new JSONObject(), 30), false);
      assertEquals(UNSUPPORTED, body);
      assertEquals(List.of(30), box.sentAt());
      assertFalse(box.rpcOld);
   }

   // A real websocket on loopback, standing in for the TiVo's port 1502.
   private static class FakeTivo extends WebSocketServer {
      final CountDownLatch started = new CountDownLatch(1);
      final String status;
      volatile String auth;

      FakeTivo(String status) {
         super(new InetSocketAddress("127.0.0.1", 0), Collections.<Draft>singletonList(WebSocketRPC.mindrpcDraft()));
         this.status = status;
         setReuseAddr(true);
      }

      public void onStart() {
         started.countDown();
      }

      public void onOpen(WebSocket conn, ClientHandshake handshake) {
      }

      public void onClose(WebSocket conn, int code, String reason, boolean remote) {
      }

      public void onError(WebSocket conn, Exception ex) {
      }

      public void onMessage(WebSocket conn, String message) {
         String type = message.replaceAll("(?s).*RequestType: (\\w+).*", "$1");
         if (type.equals("bodyAuthenticate")) {
            auth = message;
            conn.send(reply(message, "{\"type\":\"bodyAuthenticateResponse\",\"status\":\"" + status + "\"}"));
         } else if (type.equals("bodyConfigSearch")) {
            conn.send(reply(message, "{\"type\":\"bodyConfigList\",\"bodyConfig\":[{\"bodyId\":\"tsn:8490001\"}]}"));
         } else {
            // Binary, to show those are read too
            conn.send(ByteBuffer.wrap(reply(message, "{\"type\":\"error\",\"text\":\"no such thing\"}")
               .getBytes(StandardCharsets.UTF_8)));
         }
      }

      int start(Duration wait) throws InterruptedException {
         start();
         assertTrue(started.await(wait.toMillis(), TimeUnit.MILLISECONDS), "fake TiVo never started");
         return getPort();
      }
   }

   @Test
   void aRemoteTalksToTheTivoOverItsWebsocket() throws Exception {
      FakeTivo tivo = new FakeTivo("success");
      boolean was = config.rpcWebsocket;
      config.rpcWebsocket = true;
      try {
         assertTimeoutPreemptively(PATIENCE, () -> {
            int port = tivo.start(PATIENCE);
            Remote r = new Remote("127.0.0.1", port, "0123456789", null);
            try {
               assertTrue(r.success, "bodyAuthenticate over the websocket");
               assertFalse(r.awayMode(), "a local websocket is not away mode");
               assertTrue(tivo.auth.contains("\"key\":\"0123456789\""), tivo.auth);
               JSONObject reply = r.Command("bodyConfigSearch", new JSONObject());
               assertEquals("tsn:8490001", reply.getJSONArray("bodyConfig").getJSONObject(0).getString("bodyId"));
               // Local, so an error is null with the TiVo's words kept - not away mode's result
               assertNull(r.Command("noSuchRequest", new JSONObject()));
               assertEquals("no such thing", r.getLastError().getString("text"));
            } finally {
               r.disconnect();
            }
         });
      } finally {
         config.rpcWebsocket = was;
         tivo.stop(1000);
      }
   }

   @Test
   void aRefusedMakIsNotReady() throws Exception {
      FakeTivo tivo = new FakeTivo("failure");
      try {
         assertTimeoutPreemptively(PATIENCE, () -> {
            int port = tivo.start(PATIENCE);
            TiVoRPCLocalWS ws = TiVoRPCLocalWS.init("Bolt", "127.0.0.1", port, "0000000000", false);
            try {
               assertFalse(ws.waitForReady());
            } finally {
               ws.close();
            }
         });
      } finally {
         tivo.stop(1000);
      }
   }
}
