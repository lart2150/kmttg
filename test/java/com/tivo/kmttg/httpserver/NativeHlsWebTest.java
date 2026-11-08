package com.tivo.kmttg.httpserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;

/** No TiVo required: guard the proxy and session-eligibility boundary. */
public class NativeHlsWebTest {
   private kmttgServer server;
   private int port;

   @BeforeEach
   void start() throws Exception {
      try (ServerSocket s = new ServerSocket(0)) {
         port = s.getLocalPort();
      }
      server = new kmttgServer(port);
      server.start();
   }

   @AfterEach
   void shutdown() {
      if (server != null) server.stop();
   }

   private int status(String path, String method, boolean header) throws Exception {
      HttpURLConnection c = (HttpURLConnection)
         new URL("http://127.0.0.1:" + port + path).openConnection();
      c.setRequestMethod(method);
      c.setConnectTimeout(3000);
      c.setReadTimeout(3000);
      if (header) c.setRequestProperty("X-Kmttg-Playback", "1");
      try {
         return c.getResponseCode();
      } finally {
         c.disconnect();
      }
   }

   @Test
   void controlRequestsNeedTheSameOriginCustomHeader() throws Exception {
      assertEquals(403, status("/nativeHls/start", "GET", false));
      assertEquals(403, status("/nativeHls/start", "POST", false));
      assertEquals(403, status("/nativeHls/stop", "POST", false));
   }

   @Test
   void invalidTiVoNeverOpensAnRpcConnection() throws Exception {
      assertEquals(400, status("/nativeHls/start?tivo=UNKNOWN-KMTTG-TEST"
         + "&recordingId=tivo%3Arc.123", "POST", true));
      assertEquals(400, status("/nativeHls/start?tivo=UNKNOWN-KMTTG-TEST"
         + "&recordingId=not_valid%2Fpath", "POST", true));
   }

   @Test
   void mediaProxyRejectsUnknownTokenAndUnknownMethods() throws Exception {
      String token = NativeHlsWeb.newToken();
      assertEquals(404, status("/nativeHls/media/" + token
         + "/session-streaming/1.m3u8", "GET", false));
      assertEquals(405, status("/nativeHls/media/" + token
         + "/session-streaming/1.m3u8", "POST", true));
   }

   @Test
   void tokensAreUnpredictableAndNeverContainUrlControlCharacters() {
      Set<String> tokens = new HashSet<>();
      for (int i = 0; i < 4000; ++i) {
         String token = NativeHlsWeb.newToken();
         assertTrue(token.matches("[a-f0-9]{48}"));
         assertTrue(tokens.add(token), "A session token was reused");
      }
   }

   @Test
   void mediaPathsAreScopedToExactlyOneHlsSession() {
      String prefix = "/session-streaming/42";
      assertTrue(NativeHlsWeb.ownsPath("42", prefix + ".m3u8"));
      assertTrue(NativeHlsWeb.ownsPath("42", prefix + "/1/playlist.m3u8"));
      assertTrue(NativeHlsWeb.ownsPath("42", prefix + "/1/keyfile.kbin"));
      assertTrue(NativeHlsWeb.ownsPath("42", prefix + "/1/0"));
      assertTrue(NativeHlsWeb.ownsPath("42", prefix + "/1/AF"));
      for (String path : new String[] {
         "/session-streaming/43.m3u8",
         "/session-streaming/43/1/keyfile.kbin",
         "/session-streaming/42/../../sysinfo/control",
         "/session-streaming/42/1/../../../etc/passwd",
         "/session-streaming/42/1/http://127.0.0.1/private",
         "/session-streaming/42/1/keyfile.kbin?copy=1",
         "/session-streaming/42/1/keyfile.kbin%00",
         "/session-streaming/42/1/playlist.m3u8/anything",
         "http://127.0.0.1/session-streaming/42.m3u8",
         "/session-streaming/42/1/otherfile.ts",
         "/session-streaming/42/1/playlist.m3u8#fragment"
      }) {
         assertFalse(NativeHlsWeb.ownsPath("42", path), path);
      }
      assertFalse(NativeHlsWeb.ownsPath("abc", prefix + ".m3u8"));
   }

   @Test
   void hlsProxyWritesCorrectChunkFramesAndFinalTerminator() throws Exception {
      // Regression: FilterOutputStream.write() bypasses the HTTPServer chunk header.
      byte[] sample = "The TiVo returned a valid M3U8".getBytes(StandardCharsets.UTF_8);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      HTTPServer.ChunkedOutputStream out = new HTTPServer.ChunkedOutputStream(bytes);
      NativeHlsWeb.writeChunks(new ByteArrayInputStream(sample), out);
      String wire = bytes.toString(StandardCharsets.ISO_8859_1);
      assertEquals(Integer.toHexString(sample.length) + "\r\n"
         + new String(sample, StandardCharsets.UTF_8) + "\r\n0\r\n\r\n", wire);
   }

   private JSONObject details(String cgms, boolean tivoToGo,
                              String state, String policy) throws Exception {
      JSONObject drm = new JSONObject();
      drm.put("cgms", cgms);
      drm.put("tivoToGo", tivoToGo);
      drm.put("recordingPlaybackPolicy", policy);
      JSONObject recording = new JSONObject();
      recording.put("recordingId", "tivo:rc.123");
      recording.put("state", state);
      recording.put("drm", drm);
      JSONArray array = new JSONArray();
      array.put(recording);
      JSONObject response = new JSONObject();
      response.put("recording", array);
      return response;
   }

   @Test
   void eligibleRecordingRequiresExplicitCopyFreelyDrmAndCompleteState() throws Exception {
      JSONObject good = details("copyFreely", true, "complete", "allowed");
      assertTrue(NativeHlsWeb.copyFreely(good, "tivo:rc.123"));
      assertFalse(NativeHlsWeb.copyFreely(good, "tivo:rc.other"));
      assertFalse(NativeHlsWeb.copyFreely(null, "tivo:rc.123"));
      assertFalse(NativeHlsWeb.copyFreely(new JSONObject(), "tivo:rc.123"));
      assertFalse(NativeHlsWeb.copyFreely(details("copyOnce", true, "complete", "allowed"),
            "tivo:rc.123"));
      assertFalse(NativeHlsWeb.copyFreely(details("copyNever", true, "complete", "allowed"),
            "tivo:rc.123"));
      assertFalse(NativeHlsWeb.copyFreely(details("copyFreely", false, "complete", "allowed"),
            "tivo:rc.123"));
      assertFalse(NativeHlsWeb.copyFreely(details("copyFreely", true, "inProgress", "allowed"),
            "tivo:rc.123"));
      assertFalse(NativeHlsWeb.copyFreely(details("copyFreely", true, "complete", "denied"),
            "tivo:rc.123"));
      JSONArray ambiguous = good.getJSONArray("recording");
      ambiguous.put(details("copyFreely", true, "complete", "allowed")
            .getJSONArray("recording").getJSONObject(0));
      assertFalse(NativeHlsWeb.copyFreely(good, "tivo:rc.123"),
            "Ambiguous recording search must never authorize playback");
   }
}
