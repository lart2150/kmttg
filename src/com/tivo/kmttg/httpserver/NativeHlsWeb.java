/*
 * Copyright 2008-Present Kevin Moye <moyekj@yahoo.com>.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.tivo.kmttg.httpserver;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.NativeHlsSession;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.log;

/**
 * Optional same-origin proxy for TiVo-native recording HLS. No arbitrary URL forwarding:
 * the host comes from a configured TiVo, port is fixed by its HTTP transcoder, paths
 * are constrained to the allocated session, and clients need an unpredictable token.
 * Media keys are proxied as binary, never written to disk or logged.
 */
final class NativeHlsWeb {
   private static final String PREFIX = "/nativeHls/media/";
   private static final int PORT = 49152;
   private static final long IDLE_MS = 5 * 60 * 1000L;
   private static final int MAX_SESSIONS = 2;
   private static final int MAX_RESPONSE = 32 * 1024 * 1024;
   private static final Pattern TOKEN = Pattern.compile("[a-f0-9]{48}");
   private static final SecureRandom RANDOM = new SecureRandom();

   private static class Active {
      final NativeHlsSession session;
      final String ip;
      final String sessionNumber;
      volatile long lastUse = System.currentTimeMillis();

      Active(NativeHlsSession session, String ip) {
         this.session = session;
         this.ip = ip;
         String uri = session.playlistUri();
         this.sessionNumber = uri.substring("/session-streaming/".length(),
               uri.length() - ".m3u8".length());
      }

      boolean owns(String path) {
         return ownsPath(sessionNumber, path);
      }
   }

   // TiVo's nested playlist references have two numeric folders and use hex segment names.
   // Never allow arbitrary URLs, other session IDs, path traversal, or unknown filename types.
   static boolean ownsPath(String sessionNumber, String path) {
      if (sessionNumber == null || !sessionNumber.matches("[0-9]+") || path == null)
         return false;
      String first = "/session-streaming/" + sessionNumber;
      return path.equals(first + ".m3u8")
         || path.matches(Pattern.quote(first) +
               "/[0-9]+/(?:playlist\\.m3u8|keyfile\\.kbin|[0-9a-fA-F]+)");
   }

   private final Map<String, Active> active = new ConcurrentHashMap<>();
   private final ScheduledExecutorService expiration = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "kmttg-native-hls-cleanup");
      t.setDaemon(true);
      return t;
   });

   NativeHlsWeb() {
      expiration.scheduleWithFixedDelay(() -> {
         removeExpired();
         NativeHlsRecovery.recoverOrphans();
      }, 2, 30, TimeUnit.SECONDS);
   }

   static String newToken() {
      byte[] bytes = new byte[24];
      RANDOM.nextBytes(bytes);
      StringBuilder out = new StringBuilder(48);
      for (byte b : bytes) out.append(String.format("%02x", b & 0xff));
      return out.toString();
   }

   private static void json(HTTPServer.Response resp, int code, JSONObject object) throws IOException {
      resp.getHeaders().replace("Content-Type", "application/json; charset=utf-8");
      resp.getHeaders().replace("Cache-Control", "no-store");
      resp.getHeaders().replace("X-Content-Type-Options", "nosniff");
      resp.send(code, object.toString());
   }

   private static void error(HTTPServer.Response resp, int status, String description) throws IOException {
      JSONObject out = new JSONObject();
      try {
         out.put("error", description);
      } catch (Exception e) {
         throw new IOException(e);
      }
      json(resp, status, out);
   }

   private static boolean isControl(HTTPServer.Request req) {
      // A custom header forces cross-origin browsers to preflight; no CORS is enabled.
      return "POST".equals(req.getMethod())
         && "1".equals(req.getHeaders().get("X-Kmttg-Playback"));
   }

   void serve(HTTPServer.Request req, HTTPServer.Response resp) throws IOException {
      String path = req.getPath();
      if ("/nativeHls/start".equals(path)) {
         if (!isControl(req)) {
            error(resp, 403, "Native playback requires a same-origin POST");
            return;
         }
         start(req, resp);
      } else if ("/nativeHls/stop".equals(path)) {
         if (!isControl(req)) {
            error(resp, 403, "Native playback requires a same-origin POST");
            return;
         }
         String token = req.getParams().get("token");
         Active ended = TOKEN.matcher(token == null ? "" : token).matches()
               ? active.remove(token) : null;
         if (ended != null) releaseSession(ended);
         json(resp, 200, new JSONObject());
      } else if ("/nativeHls/ping".equals(path)) {
         if (!isControl(req)) {
            error(resp, 403, "Native playback requires a same-origin POST");
            return;
         }
         String token = req.getParams().get("token");
         Active current = TOKEN.matcher(token == null ? "" : token).matches()
               ? active.get(token) : null;
         if (current == null) {
            error(resp, 404, "Native playback session expired");
            return;
         }
         current.lastUse = System.currentTimeMillis();
         json(resp, 200, new JSONObject());
      } else if (path.startsWith(PREFIX)) {
         if (!"GET".equals(req.getMethod())) {
            error(resp, 405, "Media requests must use GET");
            return;
         }
         media(path.substring(PREFIX.length()), resp);
      } else {
         error(resp, 404, "Unknown native playback endpoint");
      }
   }

   private synchronized void start(HTTPServer.Request req, HTTPServer.Response resp) throws IOException {
      removeExpired();
      if (active.size() >= MAX_SESSIONS) {
         error(resp, 503, "Native playback session limit reached");
         return;
      }
      String tivo = req.getParams().get("tivo");
      String recordingId = req.getParams().get("recordingId");
      if (tivo == null || recordingId == null
            || recordingId.length() > 180 || recordingId.length() < 5
            || !recordingId.matches("[A-Za-z0-9:._-]+")
            || !config.TIVOS.containsKey(tivo)) {
         error(resp, 400, "Invalid TiVo or recording");
         return;
      }
      String bodyId = config.getTsn(tivo);
      String ip = config.TIVOS.get(tivo);
      if (bodyId == null || bodyId.isEmpty() || ip == null) {
         error(resp, 400, "TiVo is not configured for RPC");
         return;
      }
      InetAddress address = InetAddress.getByName(ip);
      if (!address.isSiteLocalAddress() && !address.isLoopbackAddress()) {
         error(resp, 403, "Native streaming only supports local TiVos");
         return;
      }

      Remote remote = new Remote(tivo); // local, authenticated MRPC/2
      if (!remote.success) {
         remote.disconnect();
         error(resp, 502, "Could not authenticate with TiVo");
         return;
      }
      try {
         JSONObject lookup = new JSONObject();
         lookup.put("recordingId", recordingId);
         JSONObject details = remote.Command("Search", lookup);
         if (!copyFreely(details, recordingId)) {
            error(resp, 403, "Recording is not eligible for native playback");
            return;
         }
         // The firmware exposes its transcoder capability and state on the
         // dedicated streaming HTTP service, before any session is allocated.
         if (!streamingReady(address.getHostAddress(), PORT)) {
            error(resp, 503, "This TiVo does not have a ready native HLS service");
            return;
         }
         NativeHlsSession session = NativeHlsSession.start(remote, bodyId,
               recordingId, address.getHostAddress());
         remote = null; // session now owns the RPC connection
         try {
            // Durable before telling the browser to use it: SIGKILL bypasses finally.
            NativeHlsRecovery.save(session.clientUuid(), session.sessionId(),
                  address.getHostAddress());
            String token = newToken();
            Active current = new Active(session, address.getHostAddress());
            active.put(token, current);
            try {
               JSONObject response = new JSONObject();
               response.put("token", token);
               response.put("playlist", PREFIX + token + session.playlistUri());
               json(resp, 200, response);
            } catch (Exception e) {
               active.remove(token, current);
               releaseSession(current);
               throw e;
            }
         } catch (Exception e) {
            // If durable bookkeeping fails, never leave the transcoder allocated.
            if (!session.releaseConfirmed()) {
               session.close();
               if (session.releaseConfirmed())
                  NativeHlsRecovery.remove(session.clientUuid());
            }
            throw e;
         }
      } catch (Exception e) {
         log.warn("Native HLS start: " + e.getMessage());
         error(resp, 502, "TiVo could not start native playback");
      } finally {
         if (remote != null) remote.disconnect();
      }
   }

   static boolean streamingReady(String ip, int port) {
      HttpURLConnection connection = null;
      try {
         connection = (HttpURLConnection) new URL("http", ip, port,
               "/sysinfo/json/svcinfo").openConnection();
         connection.setConnectTimeout(2500);
         connection.setReadTimeout(2500);
         connection.setInstanceFollowRedirects(false);
         if (connection.getResponseCode() != 200) return false;
         byte[] body;
         try (InputStream input = connection.getInputStream()) {
            body = input.readNBytes(64 * 1024 + 1);
         }
         if (body.length > 64 * 1024) return false;
         JSONObject info = new JSONObject(new String(body,
               java.nio.charset.StandardCharsets.UTF_8));
         return info.optInt("ServiceStreamingAllowed", 0) == 1
               && info.optInt("svcStreamingStateExt", -1) == 6;
      } catch (Exception e) {
         // Unsupported model, booting/rebooting, or unreachable service.
         return false;
      } finally {
         if (connection != null) connection.disconnect();
      }
   }

   static boolean copyFreely(JSONObject details, String id) {
      if (details == null) return false;
      JSONArray recordings = details.optJSONArray("recording");
      if (recordings == null || recordings.length() != 1) return false;
      JSONObject recording = recordings.optJSONObject(0);
      if (recording == null || !id.equals(recording.optString("recordingId", ""))) return false;
      JSONObject drm = recording.optJSONObject("drm");
      return "complete".equals(recording.optString("state", ""))
         && drm != null && "copyFreely".equals(drm.optString("cgms", ""))
         && drm.optBoolean("tivoToGo", false)
         && "allowed".equals(drm.optString("recordingPlaybackPolicy", ""));
   }

   private void media(String suffix, HTTPServer.Response resp) throws IOException {
      int slash = suffix.indexOf('/');
      String token = slash >= 0 ? suffix.substring(0, slash) : "";
      String path = slash >= 0 ? suffix.substring(slash) : "";
      Active current = TOKEN.matcher(token).matches() ? active.get(token) : null;
      if (current == null || !current.owns(path)) {
         error(resp, 404, "Unknown HLS session or media path");
         return;
      }
      current.lastUse = System.currentTimeMillis();
      String contentType = path.endsWith(".m3u8") ? "application/vnd.apple.mpegurl"
         : path.endsWith(".kbin") ? "application/octet-stream" : "video/mp2t";
      HttpURLConnection conn = (HttpURLConnection)
         new URL("http", current.ip, PORT, path).openConnection();
      conn.setInstanceFollowRedirects(false);
      conn.setConnectTimeout(5000);
      conn.setReadTimeout(15000);
      try {
         int code = conn.getResponseCode();
         if (code != 200) {
            error(resp, 502, "TiVo HLS media request failed");
            return;
         }
         long length = conn.getContentLengthLong();
         if (length > MAX_RESPONSE) {
            error(resp, 502, "TiVo HLS response is too large");
            return;
         }
         resp.getHeaders().replace("Cache-Control", "no-store");
         resp.getHeaders().replace("X-Content-Type-Options", "nosniff");
         // Chunked output avoids holding media segments or AES keys in memory or on disk.
         HTTPServer.ChunkedOutputStream out = resp.getChunkedBody();
         resp.sendHeaders(200, -1, -1, null, contentType, null);
         try (InputStream in = conn.getInputStream()) {
            writeChunks(in, out);
         }
      } finally {
         conn.disconnect();
      }
   }

   // HTTPServer's ChunkedOutputStream is NOT an automatically chunking wrapper:
   // write() emits unframed bytes. Its writeChunk()/writeTrailingChunk() contract
   // must be followed explicitly (see HTTPServer.handleTrace).
   static void writeChunks(InputStream in, HTTPServer.ChunkedOutputStream out) throws IOException {
      byte[] buf = new byte[8192];
      int count;
      long total = 0;
      while ((count = in.read(buf)) != -1) {
         total += count;
         if (total > MAX_RESPONSE) throw new IOException("TiVo HLS response limit");
         out.writeChunk(buf, 0, count);
      }
      out.writeTrailingChunk(null);
      out.flush();
   }

   private void removeExpired() {
      long now = System.currentTimeMillis();
      for (Map.Entry<String, Active> entry : active.entrySet()) {
         if (now - entry.getValue().lastUse > IDLE_MS
               && active.remove(entry.getKey(), entry.getValue())) {
            releaseSession(entry.getValue());
         }
      }
   }

   private static void releaseSession(Active session) {
      session.session.close();
      if (session.session.releaseConfirmed())
         NativeHlsRecovery.remove(session.session.clientUuid());
   }

   void close() {
      expiration.shutdownNow();
      for (Map.Entry<String, Active> entry : active.entrySet()) {
         if (active.remove(entry.getKey(), entry.getValue()))
            releaseSession(entry.getValue());
      }
   }
}
