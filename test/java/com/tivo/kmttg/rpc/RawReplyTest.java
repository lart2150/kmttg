package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.java_websocket.drafts.Draft_6455;
import org.junit.jupiter.api.Test;

import com.tivo.kmttg.JSON.JSONObject;

// Raw mode is for finding out what a TiVo really says to a request, so the reply has to come
// back as it was sent - errors included, nothing added - and the request has to go out under
// the name it was given, even one that collides with a kmttg command.
public class RawReplyTest {

   private static final String ERROR =
      "{\"type\":\"error\",  \"code\":\"partnerNotSupported\",\"text\":\"partner not supported\"}";

   // One response as the box frames it: start line, headers, blank line, body.
   private static byte[] frame(String body, boolean isFinal) {
      byte[] b = body.getBytes(StandardCharsets.UTF_8);
      String headers = "Type: response\r\nRpcId: 1\r\nIsFinal: " + isFinal + "\r\n\r\n";
      String start = "MRPC/2 " + headers.length() + " " + b.length + "\r\n";
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      out.writeBytes((start + headers).getBytes(StandardCharsets.ISO_8859_1));
      out.writeBytes(b);
      return out.toByteArray();
   }

   private static class LocalRemote extends Remote {
      final ByteArrayOutputStream wire = new ByteArrayOutputStream();

      LocalRemote(byte[] reply) {
         s = new TiVoRPC(new ByteArrayInputStream(reply), wire);
      }

      String sent() {
         return new String(wire.toByteArray(), StandardCharsets.UTF_8);
      }
   }

   @Test
   void aLocalRawReplyIsTheBodyAsSent() {
      LocalRemote r = new LocalRemote(frame(ERROR, true));
      assertEquals(ERROR, r.RawCommand("channelSearch", new JSONObject()));
   }

   @Test
   void aRawRequestGoesOutUnderItsOwnNameNotAKmttgCommand() {
      // MyShows is kmttg's name for a recordingFolderItemSearch; raw must not translate it.
      LocalRemote r = new LocalRemote(frame("{\"type\":\"success\"}", true));
      r.RawCommand("MyShows", new JSONObject());
      assertTrue(r.sent().contains("RequestType: MyShows\r\n"), r.sent());
      assertFalse(r.sent().contains("recordingFolderItemSearch"), r.sent());
   }

   // Existing callers still see null for an error, but the TiVo's words are no longer lost.
   @Test
   void aCommandErrorIsStillNullButKeptAsTheTivoSentIt() throws Exception {
      LocalRemote r = new LocalRemote(frame(ERROR, true));
      assertNull(r.Command("channelSearch", new JSONObject()));
      JSONObject err = r.getLastError();
      assertEquals("partner not supported", err.getString("text"));
      assertFalse(err.has("IsFinal"), "IsFinal is a header, not part of the TiVo's error");
   }

   @Test
   void aSuccessClearsTheLastError() throws Exception {
      byte[] a = frame(ERROR, true), b = frame("{\"type\":\"success\"}", true);
      byte[] both = new byte[a.length + b.length];
      System.arraycopy(a, 0, both, 0, a.length);
      System.arraycopy(b, 0, both, a.length, b.length);
      LocalRemote r = new LocalRemote(both);
      r.Command("channelSearch", new JSONObject());
      r.Command("channelSearch", new JSONObject());
      assertNull(r.getLastError());
   }

   // in.read answers -1 once the TiVo has gone. That is no reply, whatever the framing promised.
   @Test
   void aReplyCutShortIsNoReply() {
      byte[] whole = frame("{\"type\":\"success\",\"padding\":\"xxxxxxxxxxxxxxxx\"}", true);
      byte[] cut = new byte[whole.length - 10];
      System.arraycopy(whole, 0, cut, 0, cut.length);
      LocalRemote r = new LocalRemote(cut);
      assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
         assertNull(r.RawCommand("channelSearch", new JSONObject())));
   }

   private static class AnsweringWs extends TiVoRPCWS {
      final String body;
      volatile String sent;

      AnsweringWs(String body) throws Exception {
         super(new URI("wss://127.0.0.1:1/"), new Draft_6455(), "Bolt", "127.0.0.1", 1);
         this.body = body;
      }

      public boolean isOpen() {
         return true;
      }

      public boolean waitForReady() {
         return true;
      }

      // Answers at once: the request is registered before it is sent, so this still counts.
      public void send(String text) {
         sent = text;
         String id = text.replaceAll("(?s).*RpcId: (\\d+).*", "$1");
         onMessage("MRPC/2 0 0\r\nType: response\r\nRpcId: " + id + "\r\nIsFinal: true\r\n\r\n" + body);
      }
   }

   @Test
   void anAwayRawReplyIsTheBodyAsSent() throws Exception {
      AnsweringWs ws = new AnsweringWs(ERROR);
      assertEquals(ERROR, new Remote("Bolt", ws).RawCommand("channelSearch", new JSONObject()));
   }

   @Test
   void aBodyIdIsPrefixedOnceWhetherOrNotItCameWithTsn() throws Exception {
      AnsweringWs ws = new AnsweringWs("{}");
      String prefixed = ws.RpcRequest("channelSearch", false, new JSONObject("{\"bodyId\":\"tsn:8490001\"}"));
      String bare = ws.RpcRequest("channelSearch", false, new JSONObject("{\"bodyId\":\"8490001\"}"));
      assertTrue(prefixed.contains("BodyId: tsn:8490001\r\n"), prefixed);
      assertTrue(bare.contains("BodyId: tsn:8490001\r\n"), bare);
   }
}
