package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.java_websocket.drafts.Draft_6455;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.tivo.kmttg.JSON.JSONObject;

// The /rpcws pool: when it reuses a connection, when it opens another, when it lets one go, and
// what it does about a token tivo.com won't take. Nothing here reaches tivo.com - connecting,
// the token and the clock are all the test's.
public class TiVoRPCWSPoolTest {

   private static final long IDLE = 60000;

   private static class FakeWs extends TiVoRPCWS {
      volatile boolean open;
      final boolean rejected;
      volatile boolean closed = false;
      volatile String sent = null;

      FakeWs(boolean ready, boolean rejected) throws Exception {
         super(new URI("wss://127.0.0.1:1/"), new Draft_6455(), "Bolt", "127.0.0.1", 1);
         this.open = ready;
         this.rejected = rejected;
      }

      public boolean isOpen() {
         return open;
      }

      public boolean waitForReady() {
         return open;
      }

      public boolean isAuthRejected() {
         return rejected;
      }

      public void close() {
         closed = true;
         open = false;
      }

      public JSONObject sendRequestAndWaitForResponse(String request) {
         sent = request;
         try {
            return new JSONObject("{\"type\":\"bodyConfigList\"}");
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }
   }

   private static class FakePool extends TiVoRPCWSPool {
      long clock = 0;
      boolean expired = false;
      boolean credentials = true;
      String refreshed = "new-token";
      int refreshes = 0;
      final List<String> connects = new ArrayList<String>();
      // What each connect hands back, in order; a good connection once they run out.
      final LinkedList<FakeWs> upcoming = new LinkedList<FakeWs>();

      FakePool() {
         super(IDLE);
      }

      protected TiVoRPCWS connect(String tivoName) {
         connects.add(tivoName);
         try {
            return upcoming.isEmpty() ? new FakeWs(true, false) : upcoming.removeFirst();
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }

      protected boolean tokenExpired() {
         return expired;
      }

      protected boolean haveCredentials() {
         return credentials;
      }

      protected String refreshToken() {
         refreshes++;
         if (refreshed != null)
            expired = false;
         return refreshed;
      }

      protected long now() {
         return clock;
      }
   }

   private final FakePool pool = new FakePool();

   @AfterEach
   public void closePool() {
      pool.closeAll();
   }

   @Test
   void requestsOneAfterAnotherShareAConnection() throws Exception {
      TiVoRPCWSPool.Conn a = pool.acquire("Bolt");
      pool.release(a);
      TiVoRPCWSPool.Conn b = pool.acquire("Bolt");
      pool.release(b);
      assertSame(a, b);
      assertEquals(1, pool.connects.size());
   }

   @Test
   void aConnectionIsHeldForAMinuteWithoutRequestsAndNoLonger() throws Exception {
      TiVoRPCWSPool.Conn a = pool.acquire("Bolt");
      pool.release(a);

      pool.clock = IDLE - 1;
      pool.reapIdle();
      assertEquals(1, pool.connectionCount("Bolt"));
      assertFalse(((FakeWs) a.ws).closed);

      pool.clock = IDLE;
      pool.reapIdle();
      assertEquals(0, pool.connectionCount("Bolt"));
      assertTrue(((FakeWs) a.ws).closed, "an idle connection has to be closed, not just forgotten");

      pool.release(pool.acquire("Bolt"));
      assertEquals(2, pool.connects.size(), "the next request opens a fresh one");
   }

   // The minute is counted from the last request, not from when the connection was opened.
   @Test
   void eachRequestRestartsTheIdleMinute() throws Exception {
      TiVoRPCWSPool.Conn a = pool.acquire("Bolt");
      pool.release(a);
      pool.clock = IDLE - 1;
      pool.release(pool.acquire("Bolt"));
      pool.clock = IDLE + 1;
      pool.reapIdle();
      assertEquals(1, pool.connectionCount("Bolt"));
   }

   @Test
   void aConnectionWithARequestInFlightIsNeverReaped() throws Exception {
      TiVoRPCWSPool.Conn a = pool.acquire("Bolt");
      pool.clock = 10 * IDLE;
      pool.reapIdle();
      assertEquals(1, pool.connectionCount("Bolt"));
      assertFalse(((FakeWs) a.ws).closed);
      pool.release(a);
   }

   @Test
   void concurrentRequestsShareAConnectionUntilItIsFull() throws Exception {
      List<TiVoRPCWSPool.Conn> held = new ArrayList<TiVoRPCWSPool.Conn>();
      for (int i = 0; i < TiVoRPCWSPool.MAX_IN_FLIGHT; ++i)
         held.add(pool.acquire("Bolt"));
      assertEquals(1, pool.connects.size());

      TiVoRPCWSPool.Conn next = pool.acquire("Bolt");
      assertEquals(2, pool.connects.size());
      assertNotSame(held.get(0), next);
      held.add(next);

      for (TiVoRPCWSPool.Conn c : held)
         pool.release(c);
   }

   // Past the cap a request shares the least busy connection rather than waiting for one.
   @Test
   void noMoreThanTheCapIsEverOpened() throws Exception {
      int all = TiVoRPCWSPool.MAX_IN_FLIGHT * TiVoRPCWSPool.MAX_CONNECTIONS;
      List<TiVoRPCWSPool.Conn> held = new ArrayList<TiVoRPCWSPool.Conn>();
      for (int i = 0; i < all + 3; ++i)
         held.add(pool.acquire("Bolt"));
      assertEquals(TiVoRPCWSPool.MAX_CONNECTIONS, pool.connects.size());
      for (TiVoRPCWSPool.Conn c : held)
         pool.release(c);
   }

   @Test
   void eachTivoGetsItsOwnConnection() throws Exception {
      TiVoRPCWSPool.Conn bolt = pool.acquire("Bolt");
      TiVoRPCWSPool.Conn roamio = pool.acquire("Roamio");
      assertNotSame(bolt, roamio);
      assertEquals(List.of("Bolt", "Roamio"), pool.connects);
      pool.release(bolt);
      pool.release(roamio);
   }

   @Test
   void aConnectionTivoComDroppedIsNotReused() throws Exception {
      TiVoRPCWSPool.Conn a = pool.acquire("Bolt");
      pool.release(a);
      ((FakeWs) a.ws).open = false;
      TiVoRPCWSPool.Conn b = pool.acquire("Bolt");
      assertNotSame(a, b);
      assertEquals(1, pool.connectionCount("Bolt"));
      pool.release(b);
   }

   @Test
   void anExpiredTokenIsRenewedBeforeConnecting() throws Exception {
      pool.expired = true;
      pool.release(pool.acquire("Bolt"));
      assertEquals(1, pool.refreshes);
      assertEquals(1, pool.connects.size());
   }

   @Test
   void anExpiredTokenWithNoCredentialsIsAnAuthFailure() {
      pool.expired = true;
      pool.credentials = false;
      TiVoRPCWSPool.ConnectException e = assertThrows(TiVoRPCWSPool.ConnectException.class,
         () -> pool.acquire("Bolt"));
      assertTrue(e.auth);
      assertEquals(0, pool.refreshes, "no login is attempted without credentials");
      assertEquals(0, pool.connects.size());
   }

   @Test
   void anExpiredTokenThatWontRenewIsAnAuthFailure() {
      pool.expired = true;
      pool.refreshed = null;
      TiVoRPCWSPool.ConnectException e = assertThrows(TiVoRPCWSPool.ConnectException.class,
         () -> pool.acquire("Bolt"));
      assertTrue(e.auth);
      assertEquals(0, pool.connects.size());
   }

   // A token that looks current can still have been replaced by another login.
   @Test
   void aRefusedTokenIsRenewedOnceAndTheConnectRetried() throws Exception {
      FakeWs refused = new FakeWs(false, true);
      pool.upcoming.add(refused);
      TiVoRPCWSPool.Conn c = pool.acquire("Bolt");
      assertEquals(1, pool.refreshes);
      assertEquals(2, pool.connects.size());
      assertTrue(refused.closed);
      assertNotSame(refused, c.ws);
      pool.release(c);
   }

   @Test
   void aTokenRefusedEvenWhenFreshIsAnAuthFailure() throws Exception {
      pool.upcoming.add(new FakeWs(false, true));
      pool.upcoming.add(new FakeWs(false, true));
      TiVoRPCWSPool.ConnectException e = assertThrows(TiVoRPCWSPool.ConnectException.class,
         () -> pool.acquire("Bolt"));
      assertTrue(e.auth);
      assertEquals(1, pool.refreshes, "one renewal, not a login loop");
      assertEquals(0, pool.connectionCount("Bolt"));
   }

   @Test
   void anUnreachableTivoComIsNotAnAuthFailureAndCostsNoLogin() throws Exception {
      pool.upcoming.add(new FakeWs(false, false));
      TiVoRPCWSPool.ConnectException e = assertThrows(TiVoRPCWSPool.ConnectException.class,
         () -> pool.acquire("Bolt"));
      assertFalse(e.auth);
      assertEquals(0, pool.refreshes);
   }

   @Test
   void aCommandGoesOutOnThePooledConnectionAndGivesItBack() throws Exception {
      JSONObject result = pool.command("Bolt", "bodyConfigSearch", new JSONObject());
      assertEquals("bodyConfigList", result.getString("type"));
      pool.clock = IDLE;
      pool.reapIdle();
      assertEquals(0, pool.connectionCount("Bolt"), "a command must release its connection");
   }
}
