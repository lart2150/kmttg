/*
 * Copyright 2008-Present Kevin Moye <moyekj@yahoo.com>.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.tivo.kmttg.rpc;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.regex.Pattern;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.util.log;

/**
 * One TiVo-native recording HLS session. The caller owns the Remote connection until close().
 * Its request uses the typed Trio fields verified against TiVo's HLS RPC implementation;
 * the box decides whether streaming is permitted for the recording.
 */
public final class NativeHlsSession implements AutoCloseable {
   /** The firmware explicitly reports maxSessionsExceeded for occupied streaming capacity. */
   public static final class BusyException extends IllegalStateException {
      public BusyException() { super("TiVo has no free streaming session"); }
   }

   private static final Pattern PLAYLIST =
      Pattern.compile("/session-streaming/[0-9]+\\.m3u8");

   private final Remote remote;
   private final String bodyId;
   private final String clientUuid;
   private final String sessionId;
   private final String playlistUri;
   private final String ip;
   private boolean closed;
   private boolean releaseConfirmed;

   private NativeHlsSession(Remote remote, String bodyId, String clientUuid,
                            String sessionId, String playlistUri, String ip) {
      this.remote = remote;
      this.bodyId = bodyId;
      this.clientUuid = clientUuid;
      this.sessionId = sessionId;
      this.playlistUri = playlistUri;
      this.ip = ip;
   }

   /** Build the wire request from the TiVo's trioschema::HlsStreamRecordingRequest. */
   static JSONObject request(String bodyId, String recordingId, String clientUuid) throws Exception {
      if (bodyId == null || bodyId.isEmpty()
            || recordingId == null || recordingId.isEmpty()
            || clientUuid == null || clientUuid.isEmpty())
         throw new IllegalArgumentException("Missing TiVo HLS request identifier");

      JSONObject device = new JSONObject();
      device.put("type", "deviceConfiguration");
      device.put("deviceType", "webPlayer");

      JSONArray supported = new JSONArray();
      for (String mode : new String[] { "hlsAes128Cbc", "none" }) {
         JSONObject encryption = new JSONObject();
         encryption.put("type", "hlsStreamEncryptionInfo");
         encryption.put("encryptionType", mode);
         supported.put(encryption);
      }

      JSONObject request = new JSONObject();
      request.put("bodyId", bodyId);
      request.put("recordingId", recordingId);
      request.put("clientUuid", clientUuid);
      request.put("sessionType", "streaming");
      request.put("hlsStreamDesiredVariantsSet", "ABR");
      request.put("isLocal", true);
      request.put("deviceConfiguration", device);
      request.put("supportedEncryption", supported);
      return request;
   }

   /**
    * Create one session; a successful return must be closed, including after HTTP failures.
    * The server owns all content policy decisions. This method never changes them.
    */
   public static NativeHlsSession start(Remote remote, String bodyId, String recordingId,
                                        String ip) throws Exception {
      String uuid = UUID.randomUUID().toString();
      JSONObject result = remote.Command("hlsStreamRecordingRequest",
            request(bodyId, recordingId, uuid));
      if (result == null) {
         JSONObject error = remote.getLastError();
         String code = errorCode(error);
         if ("maxSessionsExceeded".equals(code))
            throw new BusyException();
         throw new IllegalStateException("TiVo rejected HLS session: " + code);
      }
      JSONObject session = result.optJSONObject("hlsSession");
      if (session == null)
         throw new IllegalStateException("TiVo returned no HLS session");
      String id = session.optString("hlsSessionId", "");
      String uri = session.optString("playlistUri", "");
      NativeHlsSession created = new NativeHlsSession(remote, bodyId, uuid, id, uri, ip);
      if (id.isEmpty() || !PLAYLIST.matcher(uri).matches()) {
         // Even a malformed response may have allocated a TiVo transcoder.
         if (!id.isEmpty()) created.close();
         throw new IllegalStateException("TiVo returned invalid HLS session information");
      }
      return created;
   }

   // Local MRPC/2 errors may nest their authoritative code inside cause.
   static String errorCode(JSONObject error) {
      if (error == null) return "no response";
      String code = error.optString("errorCode", "");
      if (!code.isEmpty()) return code;
      JSONObject cause = error.optJSONObject("cause");
      if (cause != null) {
         code = cause.optString("code", "");
         if (!code.isEmpty()) return code;
      }
      return error.optString("code", "error");
   }

   public String playlistUri() {
      return playlistUri;
   }

   public String sessionId() {
      return sessionId;
   }

   public String clientUuid() {
      return clientUuid;
   }

   public synchronized boolean releaseConfirmed() {
      return releaseConfirmed;
   }

   public synchronized void close() {
      if (closed) return;
      closed = true;
      boolean released = false;
      try {
         JSONObject request = new JSONObject();
         request.put("bodyId", bodyId);
         request.put("clientUuid", clientUuid);
         request.put("hlsSessionId", sessionId);
         request.put("reason", "userRequested");
         JSONObject result = remote.Command("hlsStreamRelease", request);
         released = result != null && "success".equals(result.optString("type", ""));
      } catch (Exception e) {
         log.warn("Native HLS RPC release failed: " + e.getMessage());
      } finally {
         remote.disconnect();
      }
      if (!released) released = forceRelease();
      releaseConfirmed = released;
   }

   // TiVo has no server-side session timeout. If the RPC socket has died during
   // playback, use the firmware's narrowly scoped HTTP release route, never
   // an arbitrary user-supplied URL or device-wide transcoder reset.
   private boolean forceRelease() {
      if (ip == null || !sessionId.matches("tivo:hls\\.[0-9]+"))
         return false;
      String number = sessionId.substring("tivo:hls.".length());
      HttpURLConnection connection = null;
      try {
         connection = (HttpURLConnection) new URL("http", ip, 49152,
               "/sysinfo/control?config=session&id=" + number +
               "&action=release").openConnection();
         connection.setConnectTimeout(3000);
         connection.setReadTimeout(3000);
         if (connection.getResponseCode() == 200)
            return true;
         log.warn("Native HLS fallback release was rejected");
      } catch (Exception e) {
         log.warn("Native HLS fallback release failed: " + e.getMessage());
      } finally {
         if (connection != null) connection.disconnect();
      }
      return false;
   }

}
