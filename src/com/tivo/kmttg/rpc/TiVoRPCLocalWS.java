package com.tivo.kmttg.rpc;

import java.net.URI;
import java.net.URISyntaxException;

import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.util.log;

// The TiVo's own mind websocket on the LAN, in place of TiVoRPC's TLS port. The same requests
// as TLS - headers, SchemaVersion and its fallback all as TiVoRPC has them - but no client
// certificate: bodyAuthenticate with the MAK is all it asks.
public class TiVoRPCLocalWS extends WebSocketRPC {

   static final int DEFAULT_PORT = 1502;
   private static final int SCHEMA_OLD = 14;
   private static final int SCHEMA_DEFAULT = 17;
   private static final int SESSION_ID = TiVoRPC.newSessionId();

   private final String mak;
   // Set, as TiVoRPC.rpcOld is, when the box refuses 17. Remote saves it to config.
   volatile boolean rpcOld;
   private volatile Integer negotiated = null;

   TiVoRPCLocalWS(String tivoName, String IP, int port, String mak, boolean rpcOld) throws URISyntaxException {
      // Built from its parts rather than a string, so an IPv6 address gets its brackets.
      super(new URI("ws", null, IP, port, "/", null, null), mindrpcDraft(), tivoName, IP, port);
      this.mak = mak;
      this.rpcOld = rpcOld;
   }

   public static TiVoRPCLocalWS init(String tivoName, String IP, int port, String mak, boolean rpcOld)
         throws URISyntaxException, InterruptedException {
      if (port <= 0)
         port = DEFAULT_PORT;
      TiVoRPCLocalWS ws = new TiVoRPCLocalWS(tivoName, IP, port, mak, rpcOld);
      ws.open();
      return ws;
   }

   @Override
   protected String peer() {
      return (tivoName != null ? tivoName : IP) + " (websocket)";
   }

   @Override
   protected int defaultSchema() {
      return rpcOld ? SCHEMA_OLD : SCHEMA_DEFAULT;
   }

   @Override
   protected String moreHeaders(String bodyId) {
      return TiVoRPC.lanHeaders(bodyId != null ? bodyId : "", SESSION_ID);
   }

   // Only the TiVo dropping us is worth a warning; every Remote closes its own when done.
   @Override
   protected void closed(int code, String reason, boolean remote) {
      if (remote)
         super.closed(code, reason, remote);
   }

   @Override
   protected void authenticate() throws Exception {
      JSONObject credential = new JSONObject();
      JSONObject h = new JSONObject();
      credential.put("type", "makCredential");
      credential.put("key", mak);
      h.put("credential", credential);
      String response = exchange(RpcRequest("bodyAuthenticate", false, h), AUTH_TIMEOUT);
      if (response == null) {
         error("Timed out waiting for " + peer() + " to authenticate the connection");
         close();
         return;
      }
      // Not logged whole: the TiVo echoes the MAK back in it.
      JSONObject result = new JSONObject(response);
      if (result.optString("status").equals("success")) {
         ready = true;
      } else {
         String why = result.has("text") ? result.optString("text") : result.optString("status");
         error(peer() + " refused the connection: " + why);
         close();
      }
   }

   // As TiVoRPC.negotiatedSchema.
   @Override
   int negotiatedSchema(String type) {
      if (rpcOld)
         return SCHEMA_OLD;
      try {
         if (negotiated == null && waitForReady())
            negotiateSchema();
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
      return negotiated != null ? negotiated : SCHEMA_DEFAULT;
   }

   // Once per TiVo per run, as TiVoRPC does it. Replies here are matched by RpcId, so unlike
   // on TLS a probe that goes unanswered leaves the connection usable - it just stays at 17,
   // and the next connection asks again.
   void negotiateSchema() {
      String key = IP + ":" + port;
      Integer known = SchemaVersions.cached(key);
      if (known == null) {
         String response = null;
         try {
            JSONObject data = new JSONObject();
            data.put("bodyId", "-");
            response = exchange(RpcRequest("bodyConfigSearch", false, data, SchemaVersions.PROBE), PROBE_TIMEOUT);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
         } catch (Exception e) {
            warn("SchemaVersion probe failed - " + e);
         }
         if (response == null) {
            negotiated = SCHEMA_DEFAULT;
            return;
         }
         known = Math.max(SCHEMA_DEFAULT, SchemaVersions.maxMindVersion(response));
         SchemaVersions.remember(key, known);
         log.print(peer() + ": SchemaVersion " + known);
      }
      negotiated = known;
   }

   // Down a step from the version a refused request went out at, as TiVoRPC.fallBack: from
   // the negotiated version to 17, from 17 to 14, sending it again at each.
   @Override
   protected String answer(String request, String response, boolean mayFallBack) throws InterruptedException {
      while (response != null) {
         JSONObject j = noteError(response);
         if (j == null)
            return response;
         if (! mayFallBack || rpcOld || ! SchemaVersions.isUnsupported(j))
            return response;
         int sentAt = SchemaVersions.of(request);
         if (sentAt > SCHEMA_DEFAULT) {
            warn(peer() + ": SchemaVersion " + sentAt + " refused - using " + SCHEMA_DEFAULT);
            negotiated = SCHEMA_DEFAULT;
            SchemaVersions.remember(IP + ":" + port, SCHEMA_DEFAULT);
         } else {
            // Revert to older schema version for older TiVo software versions
            warn("Reverting to older RPC schema version.");
            rpcOld = true;
         }
         request = SchemaVersions.withSchema(request, defaultSchema());
         response = exchange(request, RESPONSE_TIMEOUT);
      }
      return null;
   }

   // The reply as JSON if it is an error, logged as TiVoRPC logs one, else null. Parsed only
   // when it could be one, so a successful reply - a page of MyShows, say - isn't parsed here
   // as well as by the caller.
   private JSONObject noteError(String response) {
      if (! response.contains("\"error\""))
         return null;
      try {
         JSONObject j = new JSONObject(response);
         if (! j.optString("type").equals("error"))
            return null;
         error("RPC error response:\n" + j.toString(3));
         return j;
      } catch (Exception e) {
         // Not JSON - the caller sees it as it is.
         return null;
      }
   }
}
