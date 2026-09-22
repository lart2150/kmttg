package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.java_websocket.drafts.Draft_6455;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tivo.kmttg.JSON.JSONObject;

// A TiVo leaves out every field newer than the SchemaVersion it is asked at, so kmttg asks each
// box how high it goes and uses that - stepping down, and resending, when a request is refused
// at it. A version the caller chose for one request is never second-guessed.
public class SchemaNegotiationTest {

   private static final String REFUSED =
      "{\"type\":\"error\",\"code\":\"badArgument\",\"text\":\"Unsupported schema version\"}";
   private static final String BOLT = "{\"type\":\"bodyConfigList\",\"bodyConfig\":[{\"maxMindVersion\":42}]}";
   private static final String OLD_BOX = "{\"type\":\"bodyConfigList\",\"bodyConfig\":[{\"bodyId\":\"tsn:1\"}]}";
   private static final String OK = "{\"type\":\"success\"}";
   private static final Pattern SCHEMA = Pattern.compile("SchemaVersion: (\\d+)");

   @BeforeEach
   public void forgetWhatOtherTestsLearned() {
      SchemaVersions.forget(":0");
      SchemaVersions.forget("tivo.com:8490001");
   }

   private static byte[] frames(String... bodies) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      for (String body : bodies) {
         byte[] b = body.getBytes(StandardCharsets.UTF_8);
         String headers = "Type: response\r\nRpcId: 1\r\nIsFinal: true\r\n\r\n";
         out.writeBytes(("MRPC/2 " + headers.length() + " " + b.length + "\r\n" + headers)
            .getBytes(StandardCharsets.ISO_8859_1));
         out.writeBytes(b);
      }
      return out.toByteArray();
   }

   // A LAN session over canned replies, remembering the SchemaVersion of everything it sent.
   private static class Box {
      final ByteArrayOutputStream wire = new ByteArrayOutputStream();
      final TiVoRPC rpc;

      Box(String... replies) {
         rpc = new TiVoRPC(new ByteArrayInputStream(frames(replies)), wire);
      }

      List<Integer> sentAt() {
         List<Integer> versions = new ArrayList<Integer>();
         Matcher m = SCHEMA.matcher(new String(wire.toByteArray(), StandardCharsets.UTF_8));
         while (m.find())
            versions.add(Integer.valueOf(m.group(1)));
         return versions;
      }

      String request(Integer schemaVersion) throws Exception {
         return rpc.RequestRaw(rpc.RpcRequest("channelSearch", false, new JSONObject(), schemaVersion),
            schemaVersion == null);
      }
   }

   @Test
   void aRewrittenRequestStillFramesItsHeaderCorrectly() throws Exception {
      TiVoRPC rpc = new Box().rpc;
      String req = rpc.RpcRequest("channelSearch", false, new JSONObject("{\"count\":5}"), 9);
      String at42 = SchemaVersions.withSchema(req, 42);
      assertEquals(42, SchemaVersions.of(at42));
      String[] start = at42.substring(0, at42.indexOf("\r\n")).split(" ");
      String afterStart = at42.substring(at42.indexOf("\r\n") + 2);
      int headLen = Integer.parseInt(start[1]);
      assertTrue(afterStart.substring(0, headLen).endsWith("\r\n\r\n"), "header length no longer lands on the blank line");
      assertTrue(afterStart.substring(headLen).startsWith("{"), afterStart.substring(headLen));
   }

   @Test
   void theProbeAsksAtNineteenAndLaterRequestsUseTheBoxsMaximum() throws Exception {
      Box box = new Box(BOLT, OK);
      box.rpc.negotiateSchema();
      box.request(null);
      assertEquals(List.of(SchemaVersions.PROBE, 42), box.sentAt());
   }

   @Test
   void theAnswerIsRememberedSoTheNextConnectionDoesNotAsk() throws Exception {
      new Box(BOLT).rpc.negotiateSchema();
      Box second = new Box(OK);
      second.rpc.negotiateSchema();
      second.request(null);
      assertEquals(List.of(42), second.sentAt());
   }

   @Test
   void softwareWithoutMaxMindVersionStaysAtSeventeen() throws Exception {
      Box box = new Box(OLD_BOX, OK);
      box.rpc.negotiateSchema();
      box.request(null);
      assertEquals(List.of(SchemaVersions.PROBE, 17), box.sentAt());
   }

   // The probe's own refusal is the answer "go no higher", not a reason to drop to 14.
   @Test
   void aRefusedProbeStaysAtSeventeenWithoutGoingOld() throws Exception {
      Box box = new Box(REFUSED, OK);
      box.rpc.negotiateSchema();
      box.request(null);
      assertEquals(List.of(SchemaVersions.PROBE, 17), box.sentAt());
      assertFalse(box.rpc.rpcOld);
   }

   @Test
   void aRefusalAtTheNegotiatedVersionIsResentAtSeventeen() throws Exception {
      Box box = new Box(BOLT, REFUSED, OK, OK);
      box.rpc.negotiateSchema();
      assertEquals(OK, box.request(null));
      box.request(null);
      assertEquals(List.of(SchemaVersions.PROBE, 42, 17, 17), box.sentAt());
      assertFalse(box.rpc.rpcOld, "stepping down from the negotiated version is not the old-schema fallback");
   }

   @Test
   void aRefusalAtSeventeenStillFallsBackToFourteenAndNowResends() throws Exception {
      Box box = new Box(REFUSED, OK);
      assertEquals(OK, box.request(null));
      assertEquals(List.of(17, 14), box.sentAt());
      assertTrue(box.rpc.rpcOld);
   }

   @Test
   void aVersionTheCallerChoseIsAnsweredAsItIsAndMovesNothing() throws Exception {
      Box box = new Box(BOLT, REFUSED, OK);
      box.rpc.negotiateSchema();
      assertEquals(REFUSED, box.request(99));
      box.request(null);
      assertEquals(List.of(SchemaVersions.PROBE, 99, 42), box.sentAt());
   }

   // Chosen is a matter of who chose, not of the number: asking at 17 on a box already at 17
   // used to count as the default, and its refusal wrote rpcOld=1 into config.ini for every TiVo.
   @Test
   void aChosenVersionThatMatchesTheDefaultStillMovesNothing() throws Exception {
      Box box = new Box(REFUSED, OK);
      assertEquals(REFUSED, box.request(17));
      box.request(null);
      assertEquals(List.of(17, 17), box.sentAt());
      assertFalse(box.rpc.rpcOld);
   }

   // LAN replies are read in order rather than matched by RpcId, so a probe that went
   // unanswered would have its late reply read as the answer to whatever came next.
   @Test
   void aProbeWithNoAnswerLeavesTheConnectionUnusable() {
      Box box = new Box();
      box.rpc.negotiateSchema();
      assertFalse(box.rpc.getSuccess());
   }

   @Test
   void aRemoteSendsEveryRequestAtItsOverride() throws Exception {
      final Box box = new Box(OK);
      Remote r = new Remote() {
         {
            s = box.rpc;
         }
      };
      r.setSchemaVersion(33);
      r.RawCommand("deviceAdParamsGet", new JSONObject());
      assertEquals(List.of(33), box.sentAt());
   }

   // tivo.com, answering from a queue and remembering what it was sent.
   private static class Cloud extends TiVoRPCWS {
      final LinkedList<String> replies = new LinkedList<String>();
      final List<String> sent = new ArrayList<String>();

      Cloud(String... replies) throws Exception {
         super(new URI("wss://127.0.0.1:1/"), new Draft_6455(), "Bolt", "127.0.0.1", 1);
         for (String r : replies)
            this.replies.add(r);
         tsn = "8490001";
      }

      public boolean isOpen() {
         return true;
      }

      public boolean waitForReady() {
         return true;
      }

      public void send(String text) {
         sent.add(text);
         String id = text.replaceAll("(?s).*RpcId: (\\d+).*", "$1");
         onMessage("MRPC/2 0 0\r\nType: response\r\nRpcId: " + id + "\r\nIsFinal: true\r\n\r\n" + replies.removeFirst());
      }

      List<String> sentAt() {
         List<String> out = new ArrayList<String>();
         for (String s : sent)
            out.add(s.replaceAll("(?s).*RequestType: (\\w+).*", "$1") + "@" + SchemaVersions.of(s));
         return out;
      }

      String request(String type, Integer schemaVersion) throws Exception {
         return sendRequestAndWaitForBody(RpcRequest(type, false, new JSONObject(), schemaVersion),
            schemaVersion == null);
      }
   }

   @Test
   void overTivoComTheBoxsMaximumIsNegotiatedToo() throws Exception {
      Cloud cloud = new Cloud(BOLT, OK);
      cloud.negotiateSchema();
      cloud.request("channelSearch", null);
      assertEquals(List.of("bodyConfigSearch@" + SchemaVersions.PROBE, "channelSearch@42"), cloud.sentAt());
   }

   @Test
   void withoutAnAnswerTivoComStaysWhereItAlwaysWas() throws Exception {
      Cloud cloud = new Cloud(OLD_BOX, OK);
      cloud.negotiateSchema();
      cloud.request("channelSearch", null);
      assertEquals("channelSearch@" + TiVoRPCWS.SCHEMA_DEFAULT, cloud.sentAt().get(1));
   }

   // One of tivo.com's own services stopping short says nothing about the box, so only that
   // request type falls back.
   @Test
   void aTypeTivoComRefusesFallsBackAloneAndIsResent() throws Exception {
      SchemaVersions.forget("tivo.com type:feedItemFindTest");
      Cloud cloud = new Cloud(BOLT, REFUSED, OK, OK, OK);
      cloud.negotiateSchema();
      assertEquals(OK, cloud.request("feedItemFindTest", null));
      cloud.request("feedItemFindTest", null);
      cloud.request("channelSearch", null);
      assertEquals(List.of("bodyConfigSearch@" + SchemaVersions.PROBE,
         "feedItemFindTest@42", "feedItemFindTest@22", "feedItemFindTest@22", "channelSearch@42"),
         cloud.sentAt());
      SchemaVersions.forget("tivo.com type:feedItemFindTest");
   }

   @Test
   void overTivoComAVersionTheCallerChoseIsNotResent() throws Exception {
      Cloud cloud = new Cloud(BOLT, REFUSED);
      cloud.negotiateSchema();
      assertEquals(REFUSED, cloud.request("channelSearch", 99));
      assertEquals(2, cloud.sent.size());
   }

   @Test
   void overTivoComAChosenVersionThatMatchesTheNegotiatedOneIsNotResent() throws Exception {
      Cloud cloud = new Cloud(BOLT, REFUSED);
      cloud.negotiateSchema();
      assertEquals(REFUSED, cloud.request("channelSearch", 42));
      assertEquals(2, cloud.sent.size());
   }

   // A fresh login replaces the token every other session is using, so only an error about
   // the token earns one.
   @Test
   void onlyATokenErrorCountsAsTivoComRefusingTheLogin() throws Exception {
      assertTrue(TiVoRPCWS.isTokenError(new JSONObject("{\"type\":\"error\",\"text\":\"Domain token invalid\"}")));
      assertFalse(TiVoRPCWS.isTokenError(new JSONObject("{\"type\":\"error\",\"code\":\"internalError\",\"text\":\"Unexpected error (null)\"}")));
   }

   @Test
   void theVersionIsReadOnlyFromBodyConfig() {
      assertEquals(42, SchemaVersions.maxMindVersion(BOLT));
      assertEquals(0, SchemaVersions.maxMindVersion(OLD_BOX));
      assertEquals(0, SchemaVersions.maxMindVersion(REFUSED));
      assertEquals(0, SchemaVersions.maxMindVersion("not json"));
   }
}
