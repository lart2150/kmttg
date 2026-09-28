package com.tivo.kmttg.rpc;

import java.net.URI;
import java.net.URISyntaxException;

import org.java_websocket.drafts.Draft;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.log;

// Away mode: mind RPC through tivo.com, authenticated with the account's domain token.
public class TiVoRPCWS extends WebSocketRPC {

   // What tivo.com has always been asked at, and where a refused request falls back to.
   static final int SCHEMA_DEFAULT = 22;
   // The box's maxMindVersion, asked for only by a request that wants it (see TiVoRPC).
   private volatile Integer negotiated = null;
   // tivo.com answered bodyAuthenticate and said no, as opposed to never answering at all -
   // the one failure a fresh token can fix.
   private volatile boolean authRejected = false;

   public TiVoRPCWS(URI uri, Draft protocal, String tivoName, String IP, int port) {
      super(uri, protocal, tivoName, IP, port);
   }


   public static TiVoRPCWS init(String tivoName, String IP, int port) throws URISyntaxException, InterruptedException {
      //System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE");
      TiVoRPCWS ws = new TiVoRPCWS(
            new URI("wss://xmind-tp2.tivoservice.com:2196/"),
            mindrpcDraft(),
            tivoName,
            IP,
            port
      );
      ws.open();
      return ws;
   }

   @Override
   protected String peer() {
      return "tivo.com";
   }

   @Override
   protected int defaultSchema() {
      return SCHEMA_DEFAULT;
   }

   @Override
   protected String moreHeaders(String bodyId) {
      if (bodyId == null)
         return "";
      // kmttg's own callers pass a bare TSN; a raw request may already carry the prefix.
      if (! bodyId.startsWith("tsn:"))
         bodyId = "tsn:" + bodyId;
      return "BodyId: " + bodyId + "\r\n";
   }

   @Override
   protected void sending(Integer rpcId) {
      log.print("WS Sending request: " + rpcId);
   }

   @Override
   protected void authenticate() throws Exception {
      log.print("WS connection started");
      String token = config.getDomainToken();
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
      String response = exchange(req, AUTH_TIMEOUT);
      if (response == null) {
         log.error("Timed out waiting for tivo.com to authenticate the connection");
         close();
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
                        this.tsn = tsn;
                        ready = true;
                        log.print("WS connection established");
                        break;
                     }
                  }
               }
            }
            if (! found) {
               if (tivoName == null) {
                  this.tsn = "-";
                  ready = true;
               } else {
                  // Couldn't get id from response so try getting tsn from kmttg
                  String tsn = config.getTsn(tivoName);
                  if (tsn == null) {
                     log.error("Can't determine bodyId for TiVo: " + tivoName);
                     close();
                  } else {
                     this.tsn = tsn;
                     config.bodyId_set(IP, port, "tsn:" + tsn);
                     ready = true;
                  }
               }
            }
         } else {
            // Anything but success leaves an open but unusable socket, and it is the
            // only account of why the job is about to fail - saying nothing here is
            // how a rejected login used to surface as "could not connect".
            log.error("tivo.com refused the connection: " + result.get("status"));
            authRejected = true;
            close();
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
      	authRejected = isTokenError(result);
      	close();
      }
   }

   public boolean isAuthRejected() {
      return authRejected;
   }

   static boolean isTokenError(JSONObject error) {
      String said = (error.optString("code") + " " + error.optString("text")).toLowerCase();
      return said.contains("token") || said.contains("auth");
   }

   private static final String TYPE_KEY = "tivo.com type:";

   // The version for a request that wants the box's newest, as TiVoRPC.negotiatedSchema. A
   // type tivo.com has refused above the default stays at the default.
   @Override
   int negotiatedSchema(String type) {
      try {
         if (negotiated == null && waitForReady())
            negotiateSchema();
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
      Integer refused = SchemaVersions.cached(TYPE_KEY + type);
      if (refused != null)
         return refused;
      return negotiated != null ? negotiated : SCHEMA_DEFAULT;
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
            // Not remembered, so a later connection asks again, but this one stops asking
            warn("SchemaVersion probe failed - " + e);
            negotiated = SCHEMA_DEFAULT;
            return;
         }
         SchemaVersions.remember(key, known);
         log.print(tivoName + " (tivo.com): SchemaVersion " + known);
      }
      negotiated = known;
   }

   // A refusal is usually one of tivo.com's own services stopping short of the box's
   // version, so it is that request type that falls back, not the whole connection.
   @Override
   protected String answer(String request, String response, boolean mayFallBack) throws InterruptedException {
      String type = requestTypeOf(request);
      int sentAt = SchemaVersions.of(request);
      if (mayFallBack && sentAt > SCHEMA_DEFAULT && type != null
            && SchemaVersions.isUnsupported(response)) {
         log.warn(type + ": SchemaVersion " + sentAt + " refused by tivo.com - using " + SCHEMA_DEFAULT);
         SchemaVersions.remember(TYPE_KEY + type, SCHEMA_DEFAULT);
         return exchange(SchemaVersions.withSchema(request, SCHEMA_DEFAULT), RESPONSE_TIMEOUT);
      }
      return response;
   }

}
