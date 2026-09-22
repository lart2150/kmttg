package com.tivo.kmttg.httpserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
   private String rawAnswer;
   private boolean rawCalled;
   private Integer lastSchema;

   @BeforeEach
   public void startServer() throws IOException {
      try (ServerSocket probe = new ServerSocket(0)) {
         port = probe.getLocalPort();
      }
      server = new kmttgServer(port);
      server.wsPool = new TiVoRPCWSPool(60000) {
         public JSONObject command(String tivoName, String operation, JSONObject json,
               Integer schemaVersion) throws ConnectException {
            lastSchema = schemaVersion;
            lastTivo = tivoName;
            lastOperation = operation;
            lastJson = json.toString();
            if (failWith != null)
               throw failWith;
            return answer;
         }

         public String rawCommand(String tivoName, String operation, JSONObject json,
               Integer schemaVersion) throws ConnectException {
            lastSchema = schemaVersion;
            rawCalled = true;
            lastOperation = operation;
            if (failWith != null)
               throw failWith;
            return rawAnswer;
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
      assertKmttgError(c, "renewing it failed");
   }

   @Test
   void anUnreachableTivoComIsA500() throws Exception {
      failWith = new TiVoRPCWSPool.ConnectException("Could not connect to tivo.com", false);
      HttpURLConnection c = get("?tivo=Bolt&operation=bodyConfigSearch");
      assertEquals(500, c.getResponseCode());
      assertKmttgError(c, "Could not connect");
   }

   // A named command's null may be kmttg failing before anything was sent, so it isn't
   // reported as the TiVo not answering.
   @Test
   void aNamedCommandWithNoResultIsA500() throws Exception {
      answer = null;
      HttpURLConnection c = get("?tivo=Bolt&operation=bodyConfigSearch");
      assertEquals(500, c.getResponseCode());
      assertKmttgError(c, "operation failed");
   }

   @Test
   void jsonThatWontParseIsA400() throws Exception {
      HttpURLConnection c = get("?tivo=Bolt&operation=bodyConfigSearch&json=" + enc("{not json"));
      assertEquals(400, c.getResponseCode());
      assertKmttgError(c, "not valid JSON");
   }

   @Test
   void aMissingOperationIsA400() throws Exception {
      HttpURLConnection c = get("?tivo=Bolt");
      assertEquals(400, c.getResponseCode());
      assertKmttgError(c, "missing 'operation'");
   }

   // Still a failure to the pages, which throw on a non-2xx, but in the TiVo's own words, and
   // on a status of its own so a client can tell it from kmttg failing.
   @Test
   void aTivoErrorFailsWithTheTivosJson() throws Exception {
      answer = new JSONObject("{\"type\":\"error\",\"code\":\"x\",\"text\":\"partner not supported\"}");
      HttpURLConnection c = get("?tivo=Bolt&operation=channelSearch");
      assertEquals(502, c.getResponseCode());
      assertTrue(c.getContentType().startsWith("application/json"), c.getContentType());
      JSONObject err = new JSONObject(body(c));
      assertEquals("error", err.getString("type"));
      assertEquals("partner not supported", err.getString("text"));
   }

   @Test
   void rawPassesTheReplyOnByteForByte() throws Exception {
      rawAnswer = "{\"type\":\"channelList\",  \"channel\":[ ]}";
      HttpURLConnection c = get("?tivo=Bolt&raw=1&operation=MyShows");
      assertEquals(200, c.getResponseCode());
      assertEquals(rawAnswer, body(c));
      assertTrue(rawCalled, "raw=1 must not go through the named commands");
      assertEquals("MyShows", lastOperation);
   }

   // tivo.com sends some errors pretty-printed (feedItemFind's internalError, for one). They
   // go out compact and typed like every other error.
   @Test
   void aPrettyPrintedRawErrorGoesOutCompactAsJson() throws Exception {
      rawAnswer = "{\n   \"type\": \"error\",\n   \"code\": \"internalError\",\n   \"text\": \"Unexpected error (null)\"\n}";
      HttpURLConnection c = get("?raw=1&operation=feedItemFind");
      assertEquals(502, c.getResponseCode());
      assertTrue(c.getContentType().startsWith("application/json"), c.getContentType());
      String got = body(c);
      assertFalse(got.contains("\n") || got.contains(": "), got);
      assertEquals("Unexpected error (null)", new JSONObject(got).getString("text"));
   }

   @Test
   void noRawReplyIsAKmttgError() throws Exception {
      rawAnswer = null;
      HttpURLConnection c = get("?tivo=Bolt&raw=1&operation=channelSearch");
      assertEquals(504, c.getResponseCode());
      assertKmttgError(c, "No reply");
   }

   @Test
   void schemaVersionIsPassedForThisRequestOnly() throws Exception {
      rawAnswer = "{}";
      assertEquals(200, get("?raw=1&operation=deviceAdParamsGet&schemaVersion=42").getResponseCode());
      assertEquals(Integer.valueOf(42), lastSchema);
      assertEquals(200, get("?raw=1&operation=deviceAdParamsGet").getResponseCode());
      assertEquals(null, lastSchema, "no parameter means the negotiated version");
   }

   @Test
   void aSchemaVersionThatIsNotANumberIsA400() throws Exception {
      HttpURLConnection c = get("?operation=bodyConfigSearch&schemaVersion=latest");
      assertEquals(400, c.getResponseCode());
      assertKmttgError(c, "schemaVersion");
   }

   private static void assertKmttgError(HttpURLConnection c, String text) throws Exception {
      assertTrue(c.getContentType().startsWith("application/json"), c.getContentType());
      JSONObject err = new JSONObject(body(c));
      assertEquals("kmttgError", err.getString("type"));
      assertTrue(err.getString("text").contains(text), err.getString("text"));
   }
}
