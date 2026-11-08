package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;

/** Native HLS requests are pinned to the Trio schema and observed on-wire response. */
public class NativeHlsSessionTest {
   private static class FakeRemote extends Remote {
      final List<String> operations = new ArrayList<>();
      final List<JSONObject> requests = new ArrayList<>();
      JSONObject startResponse;
      JSONObject lastError;
      int disconnects;
      boolean extendFails;

      @Override
      public JSONObject getLastError() { return lastError; }

      @Override
      public JSONObject Command(String operation, JSONObject data) {
         try {
            operations.add(operation);
            requests.add(new JSONObject(data.toString()));
            if ("hlsStreamRecordingRequest".equals(operation)
                  || "hlsStreamLiveTvRequest".equals(operation))
               return startResponse;
            if ("hlsStreamExtend".equals(operation) && extendFails)
               return null;
            return new JSONObject("{\"type\":\"success\"}");
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }

      @Override
      public void disconnect() {
         disconnects++;
      }
   }

   private static JSONObject success(String id, String uri) throws Exception {
      JSONObject session = new JSONObject();
      session.put("type", "hlsSession");
      session.put("hlsSessionId", id);
      session.put("playlistUri", uri);
      JSONObject response = new JSONObject();
      response.put("type", "hlsStreamRequestResponse");
      response.put("hlsSession", session);
      return response;
   }

   @Test
   void requestExactlyMatchesFirmwareTypedFields() throws Exception {
      JSONObject request = NativeHlsSession.request("8490001", "tivo:rc.123", "some-uuid");
      assertEquals("8490001", request.getString("bodyId"));
      assertEquals("tivo:rc.123", request.getString("recordingId"));
      assertEquals("some-uuid", request.getString("clientUuid"));
      assertEquals("streaming", request.getString("sessionType"));
      assertTrue(request.getBoolean("isLocal"));
      JSONObject device = request.getJSONObject("deviceConfiguration");
      assertEquals("deviceConfiguration", device.getString("type"));
      assertEquals("webPlayer", device.getString("deviceType"));
      JSONArray offered = request.getJSONArray("supportedEncryption");
      assertEquals(2, offered.length());
      assertEquals("hlsAes128Cbc", offered.getJSONObject(0).getString("encryptionType"));
      assertEquals("none", offered.getJSONObject(1).getString("encryptionType"));
      for (int i = 0; i < offered.length(); i++)
         assertEquals("hlsStreamEncryptionInfo",
               offered.getJSONObject(i).getString("type"));
      assertEquals("ABR", request.getString("hlsStreamDesiredVariantsSet"),
            "ABR must match the firmware enum and the verified live request");
   }

   @Test
   void liveTvRequestUsesStbChannelIdNotRecordingOrTunerIndex() throws Exception {
      JSONObject request = NativeHlsSession.liveTvRequest(
            "8490001", "tivo:sch.4295950337", "unique-client-uuid");
      assertEquals("8490001", request.getString("bodyId"));
      assertEquals("tivo:sch.4295950337", request.getString("stbChannelId"));
      assertEquals("unique-client-uuid", request.getString("clientUuid"));
      assertEquals("streaming", request.getString("sessionType"));
      assertEquals("ABR", request.getString("hlsStreamDesiredVariantsSet"));
      assertEquals("webPlayer", request.getJSONObject("deviceConfiguration").getString("deviceType"));
      assertEquals("hlsAes128Cbc", request.getJSONArray("supportedEncryption")
            .getJSONObject(0).getString("encryptionType"));
      assertFalse(request.has("recordingId"));
      assertFalse(request.has("tunerId"));
      for (String invalid : new String[]{"", "tivo:ch.1234", "tivo:sch.abc", "../sysinfo",
            "tivo:sch.42&x=1"}) {
         assertThrows(IllegalArgumentException.class,
               () -> NativeHlsSession.liveTvRequest("8490001", invalid, "uuid"), invalid);
      }
   }

   @Test
   void liveTvSessionUsesCorrectRpcAndExactlyScopedRelease() throws Exception {
      FakeRemote remote = new FakeRemote();
      remote.startResponse = success("tivo:hls.71", "/session-streaming/71.m3u8");
      NativeHlsSession session = NativeHlsSession.startLiveTv(remote,
            "8490001", "tivo:sch.4295950337", "192.168.1.49");
      assertEquals("hlsStreamLiveTvRequest", remote.operations.get(0));
      assertEquals("tivo:sch.4295950337", remote.requests.get(0).getString("stbChannelId"));
      session.close();
      session.close();
      assertEquals(2, remote.operations.size());
      assertEquals("hlsStreamRelease", remote.operations.get(1));
      assertEquals(remote.requests.get(0).getString("clientUuid"),
            remote.requests.get(1).getString("clientUuid"));
      assertEquals("tivo:hls.71", remote.requests.get(1).getString("hlsSessionId"));
      assertEquals(1, remote.disconnects);
   }

   @Test
   void missingRequestIdentifiersAreRejectedBeforeRpc() {
      assertThrows(IllegalArgumentException.class,
            () -> NativeHlsSession.request("", "tivo:rc.123", "uuid"));
      assertThrows(IllegalArgumentException.class,
            () -> NativeHlsSession.request("tsn", null, "uuid"));
      assertThrows(IllegalArgumentException.class,
            () -> NativeHlsSession.request("tsn", "tivo:rc.123", ""));
   }

   @Test
   void sessionReleaseIsTypedAndIdempotent() throws Exception {
      FakeRemote r = new FakeRemote();
      r.startResponse = success("tivo:hls.31", "/session-streaming/31.m3u8");
      NativeHlsSession session = NativeHlsSession.start(r, "8490001", "tivo:rc.123", "192.168.1.49");
      assertEquals("/session-streaming/31.m3u8", session.playlistUri());
      assertEquals("tivo:hls.31", session.sessionId());
      assertEquals("hlsStreamRecordingRequest", r.operations.get(0));
      JSONObject created = r.requests.get(0);

      session.close();
      session.close();
      assertEquals(2, r.operations.size(), "release must not occur twice");
      assertEquals("hlsStreamRelease", r.operations.get(1));
      JSONObject release = r.requests.get(1);
      assertEquals(created.getString("clientUuid"), release.getString("clientUuid"));
      assertEquals("8490001", release.getString("bodyId"));
      assertEquals("tivo:hls.31", release.getString("hlsSessionId"));
      assertEquals("userRequested", release.getString("reason"));
      assertEquals(1, r.disconnects);
   }

   @Test
   void browserHeartbeatExtendsTheActualTivoSession() throws Exception {
      FakeRemote r = new FakeRemote();
      r.startResponse = success("tivo:hls.31", "/session-streaming/31.m3u8");
      NativeHlsSession session = NativeHlsSession.start(r,
            "8490001", "tivo:rc.123", "192.168.1.49");
      assertTrue(session.extend());
      assertEquals("hlsStreamExtend", r.operations.get(1));
      assertEquals("8490001", r.requests.get(1).getString("bodyId"));
      assertEquals("tivo:hls.31", r.requests.get(1).getString("hlsSessionId"));
      assertEquals(2, r.requests.get(1).length(),
            "Do not invent unverified fields in the extend request");
      r.extendFails = true;
      assertFalse(session.extend(), "The failed RPC must not report success");
      session.close();
      assertFalse(session.extend(), "A closed session cannot be extended");
      assertEquals(4, r.operations.size(), "Only one release should be issued");
      assertEquals("hlsStreamRelease", r.operations.get(3));
   }

   @Test
   void eachSessionUsesIndependentUuid() throws Exception {
      FakeRemote first = new FakeRemote();
      FakeRemote second = new FakeRemote();
      first.startResponse = success("tivo:hls.3", "/session-streaming/3.m3u8");
      second.startResponse = success("tivo:hls.4", "/session-streaming/4.m3u8");
      try (NativeHlsSession a = NativeHlsSession.start(first, "8490001", "tivo:rc.123", "192.168.1.49");
           NativeHlsSession b = NativeHlsSession.start(second, "8490001", "tivo:rc.456", "192.168.1.49")) {
         assertNotEquals(first.requests.get(0).getString("clientUuid"),
               second.requests.get(0).getString("clientUuid"));
      }
   }

   @Test
   void invalidPlaylistReleasesTheAllocatedSession() throws Exception {
      FakeRemote r = new FakeRemote();
      r.startResponse = success("tivo:hls.123", "http://attacker.example/video.m3u8");
      assertThrows(IllegalStateException.class,
            () -> NativeHlsSession.start(r, "8490001", "tivo:rc.123", "192.168.1.49"));
      assertEquals(2, r.operations.size());
      assertEquals("hlsStreamRelease", r.operations.get(1));
      assertEquals(1, r.disconnects);
   }

   @Test
   void failedCreateNeverFakesASessionOrCallsRelease() {
      FakeRemote r = new FakeRemote();
      assertThrows(IllegalStateException.class,
            () -> NativeHlsSession.start(r, "8490001", "tivo:rc.123", "192.168.1.49"));
      assertEquals(1, r.operations.size());
      assertTrue(r.requests.get(0).has("supportedEncryption"));
   }

   @Test
   void firmwareSessionCapacityErrorIsDistinctFromNetworkOrAuthenticationErrors() throws Exception {
      FakeRemote r = new FakeRemote();
      r.lastError = new JSONObject("{\"type\":\"error\",\"errorCode\":\"maxSessionsExceeded\"}");
      assertThrows(NativeHlsSession.BusyException.class,
            () -> NativeHlsSession.start(r, "8490001", "tivo:rc.123", "192.168.1.49"));
      assertEquals(1, r.operations.size(), "A rejected start must never release someone else's session");
      assertEquals("maxSessionsExceeded", NativeHlsSession.errorCode(r.lastError));
      assertEquals("maxSessionsExceeded", NativeHlsSession.errorCode(
            new JSONObject("{\"type\":\"error\",\"cause\":{\"code\":\"maxSessionsExceeded\"}}")));
      assertEquals("no response", NativeHlsSession.errorCode(null));
   }

   @Test
   void missingSessionIdDoesNotInventAReleaseIdentifier() throws Exception {
      FakeRemote r = new FakeRemote();
      r.startResponse = success("", "/session-streaming/123.m3u8");
      assertThrows(IllegalStateException.class,
            () -> NativeHlsSession.start(r, "8490001", "tivo:rc.123", "192.168.1.49"));
      assertEquals(1, r.operations.size());
   }
}
