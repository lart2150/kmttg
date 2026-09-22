package com.tivo.kmttg.httpserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.rpc.TiVoRPCWSPool;

// What a /rpcws caller sees for each way a request can end. The pool is replaced, so the
// cases are about the status each outcome maps to, not about tivo.com.
public class RpcWsEndpointTest {

   private kmttgServer server;
   private int port;
   private String lastTivo, lastOperation, lastJson;
   private TiVoRPCWSPool.ConnectException failWith;
   private JSONObject answer;

   @BeforeEach
   public void startServer() throws IOException {
      try (ServerSocket probe = new ServerSocket(0)) {
         port = probe.getLocalPort();
      }
      server = new kmttgServer(port);
      server.wsPool = new TiVoRPCWSPool(60000) {
         public JSONObject command(String tivoName, String operation, JSONObject json)
               throws ConnectException {
            lastTivo = tivoName;
            lastOperation = operation;
            lastJson = json.toString();
            if (failWith != null)
               throw failWith;
            return answer;
         }
      };
      server.start();
   }

   @AfterEach
   public void stopServer() {
      server.stop();
   }

   private HttpURLConnection get(String query) throws IOException {
      HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/rpcws" + query).openConnection();
      c.setReadTimeout(5000);
      return c;
   }

   private static String body(HttpURLConnection c) throws IOException {
      InputStream in = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
   }

   private static String enc(String s) {
      return URLEncoder.encode(s, StandardCharsets.UTF_8);
   }

   @Test
   void aReplyComesBackAsJson() throws Exception {
      answer = new JSONObject("{\"type\":\"bodyConfigList\"}");
      HttpURLConnection c = get("?tivo=Bolt&operation=bodyConfigSearch&json=" + enc("{\"bodyId\":\"-\"}"));
      assertEquals(200, c.getResponseCode());
      assertEquals("bodyConfigList", new JSONObject(body(c)).getString("type"));
      assertEquals("Bolt", lastTivo);
      assertEquals("bodyConfigSearch", lastOperation);
      assertEquals("{\"bodyId\":\"-\"}", lastJson);
   }

   @Test
   void tivoIsOptional() throws Exception {
      answer = new JSONObject("{\"type\":\"success\"}");
      assertEquals(200, get("?operation=bodyConfigSearch").getResponseCode());
      assertEquals(null, lastTivo);
   }

   @Test
   void aTokenThatCantBeRenewedIsA401() throws Exception {
      failWith = new TiVoRPCWSPool.ConnectException("The tivo.com token has expired and renewing it failed", true);
      HttpURLConnection c = get("?tivo=Bolt&operation=bodyConfigSearch");
      assertEquals(401, c.getResponseCode());
      assertTrue(body(c).contains("renewing it failed"));
   }

   @Test
   void anUnreachableTivoComIsA500() throws Exception {
      failWith = new TiVoRPCWSPool.ConnectException("Could not connect to tivo.com", false);
      assertEquals(500, get("?tivo=Bolt&operation=bodyConfigSearch").getResponseCode());
   }

   @Test
   void noReplyIsA500() throws Exception {
      answer = null;
      assertEquals(500, get("?tivo=Bolt&operation=bodyConfigSearch").getResponseCode());
   }

   @Test
   void aMissingOperationIsA400() throws Exception {
      assertEquals(400, get("?tivo=Bolt").getResponseCode());
   }
}
