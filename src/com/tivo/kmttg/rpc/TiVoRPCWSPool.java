package com.tivo.kmttg.rpc;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;

// tivo.com websocket connections held open between web server requests, so a page making a
// run of RPCs pays for one connect and one bodyAuthenticate instead of one per request.
public class TiVoRPCWSPool {
   // Requests one connection carries at once before another is opened beside it. TiVoRPCWS
   // matches replies to requests by RpcId, so this is only about tivo.com: if it turns out not
   // to answer interleaved requests on one connection, 1 makes this a plain pool.
   static final int MAX_IN_FLIGHT = 4;
   // Per TiVo. Past this, requests share the least busy connection rather than wait for one.
   static final int MAX_CONNECTIONS = 4;
   private static final long REAP_INTERVAL = 5000;

   // Why a request never reached tivo.com. auth is the one retrying won't fix.
   public static class ConnectException extends Exception {
      private static final long serialVersionUID = 1L;
      public final boolean auth;

      public ConnectException(String msg, boolean auth) {
         super(msg);
         this.auth = auth;
      }
   }

   static class Conn {
      final TiVoRPCWS ws;
      int inFlight = 0;
      long lastUsed;

      Conn(TiVoRPCWS ws, long now) {
         this.ws = ws;
         this.lastUsed = now;
      }
   }

   private final long idleTimeout;
   // Keyed by TiVo name, "" for none: a connection learns the bodyId of the one TiVo it was
   // opened for.
   private final Map<String,List<Conn>> conns = new HashMap<String,List<Conn>>();
   // One connect at a time. A token refresh is a full tivo.com login, and two of those racing
   // each other invalidate the token the other just got.
   private final Object connectLock = new Object();
   private ScheduledExecutorService reaper = null;

   public TiVoRPCWSPool(long idleTimeout) {
      this.idleTimeout = idleTimeout;
   }

   public JSONObject command(String tivoName, String operation, JSONObject json) throws ConnectException {
      Conn c = acquire(tivoName);
      try {
         return new Remote(tivoName, c.ws).Command(operation, json);
      } finally {
         release(c);
      }
   }

   Conn acquire(String tivoName) throws ConnectException {
      String key = tivoName == null ? "" : tivoName;
      Conn c = reuse(key);
      if (c != null)
         return c;
      synchronized (connectLock) {
         // Another request may have opened one while this one waited.
         c = reuse(key);
         if (c != null)
            return c;
         c = new Conn(open(tivoName), now());
         synchronized (this) {
            c.inFlight++;
            List<Conn> list = conns.get(key);
            if (list == null) {
               list = new ArrayList<Conn>();
               conns.put(key, list);
            }
            list.add(c);
            if (reaper == null) {
               reaper = Executors.newSingleThreadScheduledExecutor(r -> {
                  Thread t = new Thread(r, "rpcws-reaper");
                  t.setDaemon(true);
                  return t;
               });
               reaper.scheduleWithFixedDelay(this::reapIdle, REAP_INTERVAL, REAP_INTERVAL, TimeUnit.MILLISECONDS);
            }
         }
         return c;
      }
   }

   // The least busy open connection, or null when a new one should be opened instead.
   private synchronized Conn reuse(String key) {
      List<Conn> list = conns.get(key);
      if (list == null)
         return null;
      Conn best = null;
      for (Iterator<Conn> it = list.iterator(); it.hasNext();) {
         Conn c = it.next();
         if (! c.ws.isOpen()) {
            // tivo.com dropped it. Anyone still waiting on it was released by onClose.
            it.remove();
            continue;
         }
         if (best == null || c.inFlight < best.inFlight)
            best = c;
      }
      if (best == null || (best.inFlight >= MAX_IN_FLIGHT && list.size() < MAX_CONNECTIONS))
         return null;
      best.inFlight++;
      return best;
   }

   synchronized void release(Conn c) {
      c.inFlight--;
      c.lastUsed = now();
   }

   synchronized void reapIdle() {
      long now = now();
      for (Iterator<List<Conn>> lists = conns.values().iterator(); lists.hasNext();) {
         List<Conn> list = lists.next();
         for (Iterator<Conn> it = list.iterator(); it.hasNext();) {
            Conn c = it.next();
            if (! c.ws.isOpen() || (c.inFlight == 0 && now - c.lastUsed >= idleTimeout)) {
               c.ws.close();
               it.remove();
            }
         }
         if (list.isEmpty())
            lists.remove();
      }
      if (conns.isEmpty())
         stopReaper();
   }

   public synchronized void closeAll() {
      for (List<Conn> list : conns.values())
         for (Conn c : list)
            c.ws.close();
      conns.clear();
      stopReaper();
   }

   synchronized int connectionCount(String tivoName) {
      List<Conn> list = conns.get(tivoName == null ? "" : tivoName);
      return list == null ? 0 : list.size();
   }

   private void stopReaper() {
      if (reaper != null) {
         reaper.shutdown();
         reaper = null;
      }
   }

   private TiVoRPCWS open(String tivoName) throws ConnectException {
      if (tokenExpired())
         refresh("The tivo.com token has expired");
      TiVoRPCWS ws = connect(tivoName);
      if (ready(ws))
         return ws;
      if (! ws.isAuthRejected())
         throw new ConnectException("Could not connect to tivo.com", false);
      // The token looked current and tivo.com refused it anyway - usually because another
      // login has replaced it since. One fresh token, and then it's the caller's problem.
      refresh("tivo.com refused the token");
      ws = connect(tivoName);
      if (ready(ws))
         return ws;
      if (ws.isAuthRejected())
         throw new ConnectException("tivo.com refused a freshly issued token", true);
      throw new ConnectException("Could not connect to tivo.com", false);
   }

   private void refresh(String why) throws ConnectException {
      if (! haveCredentials())
         throw new ConnectException(why + " and no tivo.com username and password are configured to renew it", true);
      if (refreshToken() == null)
         throw new ConnectException(why + " and renewing it failed - check the tivo.com username and password", true);
   }

   private boolean ready(TiVoRPCWS ws) throws ConnectException {
      try {
         if (ws.waitForReady())
            return true;
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         ws.close();
         throw new ConnectException("Interrupted connecting to tivo.com", false);
      }
      ws.close();
      return false;
   }

   // Seams for the tests: everything that would reach tivo.com or read the real clock.
   protected TiVoRPCWS connect(String tivoName) throws ConnectException {
      try {
         return TiVoRPCWS.init(tivoName, config.middlemind_host, config.middlemind_port);
      } catch (URISyntaxException e) {
         throw new ConnectException("Could not connect to tivo.com - " + e.getMessage(), false);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new ConnectException("Interrupted connecting to tivo.com", false);
      }
   }

   protected boolean tokenExpired() {
      return config.isDomainTokenExpired();
   }

   protected boolean haveCredentials() {
      return config.getTivoUsername() != null && config.getTivoPassword() != null;
   }

   protected String refreshToken() {
      return config.refreshDomainToken();
   }

   protected long now() {
      return System.currentTimeMillis();
   }
}
