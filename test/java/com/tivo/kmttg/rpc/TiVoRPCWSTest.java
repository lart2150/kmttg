package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.java_websocket.drafts.Draft_6455;
import org.junit.jupiter.api.Test;

import com.tivo.kmttg.JSON.JSONObject;

// Cover for the two ways the away mode websocket used to hang a job rather than fail it.
//
// The first is authentication: a rejected domain token leaves the socket open, so "open" can
// never stand in for "usable" - requests on such a socket are simply never answered.
//
// The second is the reply wait. Every in-flight request used to park on the same interned ""
// string, which only worked because there was never more than one waiter, and nothing woke
// them when the connection went away.
//
// Nothing here opens a socket: send is captured, and isOpen describes the state each case is
// about. Every wait is bounded by the test rather than by the production timeouts, so a
// regression shows up as a failure instead of a suite that never finishes.
public class TiVoRPCWSTest {

   private static final Duration PATIENCE = Duration.ofSeconds(5);

   private static class FakeWs extends TiVoRPCWS {
      volatile boolean open = true;
      volatile String sent = null;

      FakeWs() throws Exception {
         super(new URI("wss://127.0.0.1:1/"), new Draft_6455(), "Bolt", "127.0.0.1", 1);
      }

      public boolean isOpen() {
         return open;
      }

      // The handshake is not what these cases are about.
      public boolean waitForReady() {
         return open;
      }

      public void send(String text) {
         sent = text;
      }
   }

   // A response as the box frames one: start line, headers, blank line, body.
   private static String reply(int rpcId, String body) {
      String eol = "\r\n";
      return "MRPC/2 0 0" + eol + "Type: response" + eol + "RpcId: " + rpcId + eol + eol + body;
   }

   // Issue a request on its own thread and hand back where its answer will land.
   private static Thread ask(final FakeWs ws, final String request,
         final AtomicReference<JSONObject> answer, final AtomicBoolean returned) {
      Thread t = new Thread() {
         public void run() {
            answer.set(ws.sendRequestAndWaitForResponse(request));
            returned.set(true);
         }
      };
      t.start();
      return t;
   }

   private static void awaitSend(FakeWs ws) throws InterruptedException {
      for (int i = 0; i < 500 && ws.sent == null; ++i) Thread.sleep(10);
      assertNotNull(ws.sent, "the request was never sent");
   }

   @Test
   void aReplyAnswersTheRequestThatAskedForIt() throws Exception {
      final FakeWs ws = new FakeWs();
      final String req = ws.RpcRequest("bodyConfigSearch", false, new JSONObject());
      final AtomicReference<JSONObject> answer = new AtomicReference<JSONObject>();
      final AtomicBoolean returned = new AtomicBoolean();

      assertTimeoutPreemptively(PATIENCE, () -> {
         Thread t = ask(ws, req, answer, returned);
         awaitSend(ws);
         ws.onMessage(reply(1, "{\"type\":\"bodyConfigList\"}"));
         t.join();
      });
      assertNotNull(answer.get(), "the reply never came back");
      assertEquals("bodyConfigList", answer.get().getString("type"));
   }

   // The shared monitor made every waiter wake on any reply. With one waiter that was merely
   // wrong in principle; the waiter woken by someone else's reply read a response that was not
   // its own.
   @Test
   void aReplyForAnotherRequestIsNotMistakenForThisOne() throws Exception {
      final FakeWs ws = new FakeWs();
      final String req = ws.RpcRequest("bodyConfigSearch", false, new JSONObject());
      final AtomicReference<JSONObject> answer = new AtomicReference<JSONObject>();
      final AtomicBoolean returned = new AtomicBoolean();

      assertTimeoutPreemptively(PATIENCE, () -> {
         Thread t = ask(ws, req, answer, returned);
         awaitSend(ws);
         ws.onMessage(reply(99, "{\"type\":\"somebodyElses\"}"));
         Thread.sleep(200);
         assertFalse(returned.get(), "another request's reply must not end this wait");
         ws.onMessage(reply(1, "{\"type\":\"mine\"}"));
         t.join();
      });
      assertEquals("mine", answer.get().getString("type"));
   }

   // The reply wait used to end only on a reply, so a connection that dropped mid batch left
   // the job parked for good.
   @Test
   void aCloseEndsTheWaitInsteadOfLeavingItParked() throws Exception {
      final FakeWs ws = new FakeWs();
      final String req = ws.RpcRequest("bodyConfigSearch", false, new JSONObject());
      final AtomicReference<JSONObject> answer = new AtomicReference<JSONObject>();
      final AtomicBoolean returned = new AtomicBoolean();

      assertTimeoutPreemptively(PATIENCE, () -> {
         Thread t = ask(ws, req, answer, returned);
         awaitSend(ws);
         // isOpen still answers true inside onClose: the library flips readyState afterwards.
         ws.onClose(1006, "connection lost", true);
         t.join();
      });
      assertTrue(returned.get(), "the caller has to be released");
      assertNull(answer.get(), "a dropped connection is no answer");
   }

   // The web server's pool puts several requests on one connection, so the second must go out
   // while the first is still waiting, and each must get its own reply whatever the order.
   @Test
   void requestsInFlightTogetherAreEachAnsweredInAnyOrder() throws Exception {
      final java.util.concurrent.atomic.AtomicInteger sends = new java.util.concurrent.atomic.AtomicInteger();
      final FakeWs ws = new FakeWs() {
         public void send(String text) {
            sends.incrementAndGet();
         }
      };
      final String first = ws.RpcRequest("bodyConfigSearch", false, new JSONObject());
      final String second = ws.RpcRequest("bodyConfigSearch", false, new JSONObject());
      final AtomicReference<JSONObject> a1 = new AtomicReference<JSONObject>();
      final AtomicReference<JSONObject> a2 = new AtomicReference<JSONObject>();

      assertTimeoutPreemptively(PATIENCE, () -> {
         Thread t1 = ask(ws, first, a1, new AtomicBoolean());
         Thread t2 = ask(ws, second, a2, new AtomicBoolean());
         for (int i = 0; i < 500 && sends.get() < 2; ++i) Thread.sleep(10);
         assertEquals(2, sends.get(), "the second request has to go out while the first waits");
         ws.onMessage(reply(2, "{\"type\":\"second\"}"));
         ws.onMessage(reply(1, "{\"type\":\"first\"}"));
         t1.join();
         t2.join();
      });
      assertEquals("first", a1.get().getString("type"));
      assertEquals("second", a2.get().getString("type"));
   }

   @Test
   void anOpenButUnauthenticatedConnectionIsNotReady() throws Exception {
      final boolean[] open = { true };
      final TiVoRPCWS ws = new TiVoRPCWS(new URI("wss://127.0.0.1:1/"), new Draft_6455(),
            "Bolt", "127.0.0.1", 1) {
         public boolean isOpen() {
            return open[0];
         }
      };
      // What a rejected token amounts to: the handshake finished, ready was never set, and the
      // close that follows is the only notification a waiter will ever get.
      ws.onClose(1000, "auth failed", true);
      assertTimeoutPreemptively(PATIENCE, () ->
         assertFalse(ws.waitForReady(), "an unauthenticated socket must not report ready"));
   }

   @Test
   void aConnectionThatNeverOpenedIsNotReady() throws Exception {
      final boolean[] open = { false };
      final TiVoRPCWS ws = new TiVoRPCWS(new URI("wss://127.0.0.1:1/"), new Draft_6455(),
            "Bolt", "127.0.0.1", 1) {
         public boolean isOpen() {
            return open[0];
         }
      };
      assertTimeoutPreemptively(PATIENCE, () ->
         assertFalse(ws.waitForReady(), "a closed socket answers at once rather than waiting"));
   }
}
