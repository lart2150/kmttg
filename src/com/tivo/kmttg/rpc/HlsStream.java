/*
 * Copyright 2008-Present Kevin Moye <moyekj@yahoo.com>.
 *
 * This file is part of kmttg package.
 *
 * kmttg is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this project.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tivo.kmttg.rpc;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

// Starts an HLS session on the TiVo, reads the SkipMode anchor out of the first few segments,
// and tears the session down again. See SKIPMODE-HLS-PLAN.md for how this was arrived at.
//
// Two rules the code depends on and neither the API nor the box will enforce:
//
// Segments must be consumed in order from the start. The box transcodes just ahead of the
// consumer, so asking for a later one makes it seek, and the timed metadata never survives a
// seek - a strided scan of a whole recording finds nothing while a sequential read finds the
// answer on segment 2 or 3.
//
// Every streaming RPC needs bodyId, including the release. Without it middlemind answers
// routeNotFound and the release silently does nothing, stranding one of the box's two
// transcoders.
public class HlsStream {

   private static final int PORT = 49152;

   // The anchor arrives on segment 2 or 3. The rest is margin for a slow session; past it the
   // metadata is not coming and continuing only holds a transcoder.
   private static final int MAX_SEGMENTS = 12;

   private static final String UUID_NAMESPACE = "kmttg-skipmode";

   // An orphan younger than this may be another kmttg mid-fetch, which takes about 5 s.
   private static final long ORPHAN_AGE_MS = 60000;

   private static final int CONNECT_TIMEOUT = 10000;
   private static final int READ_TIMEOUT = 30000;
   private static final int SEGMENT_RETRIES = 5;

   private static final Pattern DIGITS = Pattern.compile("\\d+");

   // eTranscoderError values, from the box's own /sysinfo diagnostics page.
   private static final int ERR_NOT_READY = 0x0202;
   private static final int ERR_MAX_SESSIONS = 0x0206;
   private static final int ERR_SESSION_GONE = 0x0501;
   private static final int ERR_NO_RECORDING = 0x0502;
   private static final int ERR_TOO_LONG = 0x0503;
   private static final int ERR_PROTECTED = 0x0504;
   private static final int ERR_BAD_FORMAT = 0x0505;
   private static final int ERR_RTT = 0x0506;

   // What the box was able to say.
   //
   // retryLater is the important half. The caller writes a verdict into SkipModeRejects that
   // suppresses every future attempt, so anything that is really about the box rather than the
   // recording must defer instead. Transient is the default everywhere below: a recording
   // retried needlessly costs one refresh, a recording written off wrongly is lost until TiVo
   // reissues its clipMetadata.
   public static class Anchor {
      public final Long ms;
      public final boolean retryLater;

      private Anchor(Long ms, boolean retryLater) {
         this.ms = ms;
         this.retryLater = retryLater;
      }
   }

   private static final Anchor NONE = new Anchor(null, false);
   private static final Anchor RETRY = new Anchor(null, true);

   // Whether a TiVo can stream at all, cached for the life of a batch so an unsupported box
   // is probed once rather than once per recording.
   private enum Streaming { READY, NEVER, LATER }

   private static final Map<String,Streaming> streamingCache = new HashMap<String,Streaming>();

   public static synchronized void forgetStreamingState() {
      streamingCache.clear();
   }

   public static Anchor read(String tivoName, String recordingId) {
      Remote r = new Remote(tivoName, true);
      if (! r.success) {
         log.warn("HlsStream: could not reach " + tivoName);
         return RETRY;
      }
      try {
         return read(r, tivoName, recordingId);
      } finally {
         r.disconnect();
      }
   }

   // Takes the Remote so a batch reuses one connection, and so a replayed trace can drive this.
   static Anchor read(Remote r, String tivoName, String recordingId) {
      debug.print("tivoName=" + tivoName + " recordingId=" + recordingId);
      String ip = Remote.getIPForTivo(tivoName);
      String tsn = config.getTsn(tivoName);
      if (ip == null || tsn == null) {
         // Also how an expired tivo.com token looks: the connection opens, authentication
         // fails, and the TSN is never learned. Configuration either way, never the recording.
         log.warn("HlsStream: no address or TSN for " + tivoName);
         return RETRY;
      }
      String base = "http://" + ip + ":" + PORT;
      Streaming streaming = streamingState(base, tivoName);
      if (streaming == Streaming.NEVER) return NONE;
      if (streaming == Streaming.LATER) return RETRY;

      String uuid = clientUuid(tsn);
      releaseOrphans(base, uuid);
      Created created = create(r, tsn, recordingId, uuid);
      if (created.session == null) return created.retryLater ? RETRY : NONE;

      // Anything past this point owns a live transcoder, so every exit releases it.
      String sessionId = created.session.optString("hlsSessionId", null);
      try {
         String playlistUri = created.session.optString("playlistUri", null);
         if (sessionId == null || playlistUri == null) {
            log.warn("HlsStream: session response carried no playlist");
            return RETRY;
         }
         return readAnchor(base, playlistUri);
      } finally {
         if (sessionId != null) release(r, base, tsn, uuid, sessionId);
      }
   }

   private static synchronized Streaming streamingState(String base, String tivoName) {
      Streaming cached = streamingCache.get(base);
      if (cached != null) return cached;
      Streaming state = probeStreaming(base, tivoName);
      // Only a settled answer is worth keeping; a box that is booting should be asked again.
      if (state != Streaming.LATER) streamingCache.put(base, state);
      return state;
   }

   private static Streaming probeStreaming(String base, String tivoName) {
      Http rsp = get(base + "/sysinfo/json/svcinfo");
      if (rsp == null || rsp.code != HttpURLConnection.HTTP_OK) {
         log.warn("HlsStream: " + tivoName + " has no built-in streamer (nothing on port "
            + PORT + "), so SkipMode offsets cannot be re-anchored from it");
         return Streaming.NEVER;
      }
      try {
         JSONObject info = new JSONObject(rsp.text());
         if (info.optInt("ServiceStreamingAllowed", 0) != 1) {
            log.warn("HlsStream: streaming is not allowed on " + tivoName);
            return Streaming.NEVER;
         }
         switch (info.optInt("svcStreamingStateExt", -1)) {
            case 6:
               return Streaming.READY;
            case 4:
               log.warn("HlsStream: streaming is disabled on " + tivoName);
               return Streaming.NEVER;
            default:
               // Initialising, rebooting, updating, thermal shutdown: all of these clear
               // themselves, so nothing about this refresh should be remembered.
               log.warn("HlsStream: " + tivoName + " is not ready to stream ("
                  + streamingStateName(info.optInt("svcStreamingStateExt", -1)) + ")");
               return Streaming.LATER;
         }
      } catch (Exception e) {
         log.warn("HlsStream: could not read svcinfo - " + e.getMessage());
         return Streaming.LATER;
      }
   }

   // The uuid kmttg presents for this TiVo. The box echoes it back in /sysinfo/json/clients,
   // which is the only handle we get on our own sessions, so it has to be reproducible across
   // runs - including after a crash - and scoped to the box.
   static String clientUuid(String tsn) {
      return UUID.nameUUIDFromBytes(
         (UUID_NAMESPACE + ":" + tsn).getBytes(StandardCharsets.UTF_8)).toString();
   }

   // Reclaim sessions we left behind. Nothing on the box times them out.
   private static void releaseOrphans(String base, String uuid) {
      Http rsp = get(base + "/sysinfo/json/clients");
      if (rsp == null || rsp.code != HttpURLConnection.HTTP_OK) return;
      try {
         JSONArray clients = clientList(rsp);
         if (clients == null) return;
         long now = System.currentTimeMillis();
         for (int i = 0; i < clients.length(); ++i) {
            JSONObject c = clients.getJSONObject(i);
            // Exact match: this must never reclaim a session it did not create.
            if (! uuid.equals(c.optString("uuid", ""))) continue;
            long last = c.optLong("tLastReq", 0);
            if (last > 0 && now - last < ORPHAN_AGE_MS) continue;
            long id = c.optLong("sessionId", 0);
            if (id <= 0) continue;
            log.warn("HlsStream: reclaiming orphaned streaming session " + id);
            forceRelease(base, String.valueOf(id));
         }
      } catch (Exception e) {
         debug.print("HlsStream orphan sweep - " + e.getMessage());
      }
   }

   private static class Created {
      JSONObject session;
      boolean retryLater;
   }

   private static Created create(Remote r, String tsn, String recordingId, String uuid) {
      Created out = new Created();
      // Transient unless the box gives a reason to believe otherwise.
      out.retryLater = true;
      try {
         JSONObject device = new JSONObject();
         device.put("deviceType", "webPlayer");
         device.put("type", "deviceConfiguration");
         JSONObject encryption = new JSONObject();
         encryption.put("type", "hlsStreamEncryptionInfo");
         encryption.put("encryptionType", "hlsAes128Cbc");
         JSONObject json = new JSONObject();
         // Bare TSN: TiVoRPCWS prepends "tsn:" when it writes the BodyId header.
         json.put("bodyId", tsn);
         json.put("recordingId", recordingId);
         json.put("clientUuid", uuid);
         json.put("deviceConfiguration", device);
         json.put("sessionType", "streaming");
         json.put("hlsStreamDesiredVariantsSet", "ABR");
         json.put("supportedEncryption", encryption);
         json.put("isLocal", true);

         JSONObject result = r.Command("hlsStreamRecordingRequest", json);
         if (result == null) return out;
         if (result.has("hlsSession")) {
            out.session = result.getJSONObject("hlsSession");
            out.retryLater = false;
            return out;
         }
         String code = errorCode(result);
         if (code.equals("maxSessionsExceeded")) {
            log.warn("HlsStream: TiVo has no free transcoder right now, will try again later");
         } else {
            log.warn("HlsStream: could not start a stream - " + code);
         }
         return out;
      } catch (Exception e) {
         log.error("HlsStream create - " + e.getMessage());
         return out;
      }
   }

   // Middlemind wraps the real reason in cause.code, and routeNotFound arrives that way.
   private static String errorCode(JSONObject result) {
      String code = result.optString("errorCode", "");
      if (! code.isEmpty()) return code;
      try {
         if (result.has("cause")) {
            String inner = result.getJSONObject("cause").optString("code", "");
            if (! inner.isEmpty()) return inner;
         }
      } catch (Exception e) {
         // Fall through to the outer code.
      }
      return result.optString("code", "unknown");
   }

   private static Anchor readAnchor(String base, String playlistUri) {
      try {
         URI master = new URI(base + playlistUri);
         Http masterRsp = get(master.toString());
         if (masterRsp == null || masterRsp.code != HttpURLConnection.HTTP_OK) {
            log.warn("HlsStream: master playlist " + describe(masterRsp));
            return RETRY;
         }
         HlsPlaylist.Variant variant =
            HlsPlaylist.lowestBandwidth(HlsPlaylist.variants(masterRsp.text()));
         if (variant == null) {
            log.warn("HlsStream: master playlist listed no variants");
            return RETRY;
         }

         // Must be fetched before any of its segments - it selects the variant for the
         // session, and without it every segment answers 400.
         URI variantUri = master.resolve(variant.uri);
         Http variantRsp = get(variantUri.toString());
         if (variantRsp == null || variantRsp.code != HttpURLConnection.HTTP_OK) {
            log.warn("HlsStream: variant playlist " + describe(variantRsp));
            return RETRY;
         }
         HlsPlaylist.Media media = HlsPlaylist.media(variantRsp.text());
         if (media.keyUri == null || media.segments.isEmpty()) {
            log.warn("HlsStream: variant playlist had no key or no segments");
            return RETRY;
         }

         Http keyRsp = get(variantUri.resolve(media.keyUri).toString());
         if (keyRsp == null || keyRsp.code != HttpURLConnection.HTTP_OK
               || keyRsp.body.length != 16) {
            log.warn("HlsStream: stream key " + describe(keyRsp));
            return RETRY;
         }

         long pace = Math.max(500, media.targetDurationSec * 1000L - 200);
         int limit = Math.min(MAX_SEGMENTS, media.segments.size());
         for (int i = 0; i < limit; ++i) {
            String name = media.segments.get(i);
            long sequence = HlsPlaylist.sequenceNumber(name);
            if (sequence < 0) {
               // Skipping one would put a gap in the ordered read, which is the one thing this
               // cannot do, so stop rather than quietly carry on.
               log.warn("HlsStream: unreadable segment name '" + name + "'");
               return RETRY;
            }
            byte[] encrypted = segment(variantUri.resolve(name).toString(), pace);
            if (encrypted == null) return NONE;
            Long anchor = HlsAnchor.fromSegment(decrypt(encrypted, keyRsp.body, sequence));
            if (anchor != null) {
               log.print("Recovered SkipMode stream anchor " + anchor + " ms from segment " + name);
               return new Anchor(anchor, false);
            }
            // No pacing: the box buffers ahead, and outrunning it is self-correcting below.
         }
         log.warn("HlsStream: no timed metadata in the first " + limit + " segments");
         return NONE;
      } catch (Transient e) {
         log.warn("HlsStream: " + e.getMessage());
         return RETRY;
      } catch (Exception e) {
         log.error("HlsStream read - " + e.getMessage());
         return RETRY;
      }
   }

   // The box could not keep up, as opposed to refusing.
   private static class Transient extends Exception {
      private static final long serialVersionUID = 1L;

      Transient(String message) {
         super(message);
      }
   }

   // One segment. Returns null only when the box has refused it for a reason that will not
   // change; everything else waits or defers.
   private static byte[] segment(String url, long pace) throws Transient {
      for (int attempt = 0; attempt < SEGMENT_RETRIES; ++attempt) {
         Http rsp = get(url);
         if (rsp != null && rsp.code == HttpURLConnection.HTTP_OK) return rsp.body;
         if (rsp == null) throw new Transient("segment fetch failed");
         int reason = transcoderError(rsp.text());
         if (reason == ERR_NOT_READY) {
            debug.print("segment not ready, waiting: " + url);
            if (! sleep(pace)) throw new Transient("cancelled");
            continue;
         }
         if (permanent(reason)) {
            log.warn("HlsStream: segment fetch " + describe(rsp));
            return null;
         }
         throw new Transient("segment fetch " + describe(rsp));
      }
      throw new Transient("gave up waiting for the transcoder");
   }

   // Refusals that say something about the recording rather than the moment.
   static boolean permanent(int reason) {
      return reason == ERR_NO_RECORDING || reason == ERR_TOO_LONG
          || reason == ERR_PROTECTED || reason == ERR_BAD_FORMAT;
   }

   // AES-128-CBC, with the segment's media sequence number as the IV because the playlist
   // states none of its own.
   static byte[] decrypt(byte[] encrypted, byte[] key, long sequence) {
      if (encrypted == null || key == null) return null;
      try {
         // Whole blocks only: the box appends a few bytes past the last one and strict PKCS7
         // unpadding rejects the result.
         int usable = encrypted.length - (encrypted.length % 16);
         if (usable <= 0) return null;
         Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
         cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
            new IvParameterSpec(HlsPlaylist.iv(sequence)));
         return cipher.doFinal(encrypted, 0, usable);
      } catch (Exception e) {
         log.error("HlsStream decrypt - " + e.getMessage());
         return null;
      }
   }

   // RPC release, then make sure. The RPC is the sanctioned path but fails silently in more
   // than one way, and a stranded session costs one of the box's two transcoders.
   private static void release(Remote r, String base, String tsn, String uuid, String sessionId) {
      String id = numeric(sessionId);
      try {
         JSONObject json = new JSONObject();
         json.put("bodyId", tsn);
         json.put("clientUuid", uuid);
         json.put("hlsSessionId", sessionId);
         JSONObject result = r.Command("hlsStreamRelease", json);
         if (result != null && "success".equals(result.optString("type", ""))
               && confirmedGone(base, uuid, id)) {
            return;
         }
         log.warn("HlsStream: RPC release did not take effect, releasing directly");
      } catch (Exception e) {
         log.warn("HlsStream release - " + e.getMessage());
      }
      forceRelease(base, id);
   }

   // True only when the client list was read and our session is not in it. A listing we could
   // not read is not evidence of anything, so it falls through to the direct release.
   private static boolean confirmedGone(String base, String uuid, String id) {
      Http rsp = get(base + "/sysinfo/json/clients");
      if (rsp == null || rsp.code != HttpURLConnection.HTTP_OK) return false;
      try {
         JSONArray clients = clientList(rsp);
         if (clients == null) return false;
         for (int i = 0; i < clients.length(); ++i) {
            JSONObject c = clients.getJSONObject(i);
            if (id.equals(String.valueOf(c.optLong("sessionId", 0)))
                  && uuid.equals(c.optString("uuid", ""))) {
               return false;
            }
         }
         return true;
      } catch (Exception e) {
         debug.print("HlsStream release check - " + e.getMessage());
         return false;
      }
   }

   private static JSONArray clientList(Http rsp) throws Exception {
      JSONObject info = new JSONObject(rsp.text());
      return info.has("clients") ? info.getJSONArray("clients") : null;
   }

   // Tear a session down over plain HTTP, which needs no RPC connection and is the only option
   // for an orphan.
   //
   // The id is substituted into a fixed template and nothing else about this URL is ever built
   // from a variable. Neighbouring actions on the same endpoint release every client on the box
   // or restart its streaming service, and they differ from this one only by query string.
   private static void forceRelease(String base, String id) {
      if (id == null || id.isEmpty() || id.equals("0")) return;
      Http rsp = get(base + "/sysinfo/control?config=session&id=" + id + "&action=release");
      if (rsp == null || rsp.code != HttpURLConnection.HTTP_OK) {
         log.warn("HlsStream: could not release streaming session " + id
            + ", it may hold a transcoder until the TiVo is restarted");
      }
   }

   // Session ids arrive as "tivo:hls.<n>" but the control endpoint wants the number. Also the
   // guard that keeps anything but digits out of that URL.
   private static String numeric(String sessionId) {
      if (sessionId == null) return "";
      int dot = sessionId.lastIndexOf('.');
      String tail = dot < 0 ? sessionId : sessionId.substring(dot + 1);
      return DIGITS.matcher(tail).matches() ? tail : "";
   }

   static int transcoderError(String body) {
      if (body == null) return 0;
      try {
         JSONObject json = new JSONObject(body);
         String code = json.optString("eTranscoderError", "");
         if (code.startsWith("0x")) return Integer.parseInt(code.substring(2), 16);
      } catch (Exception e) {
         // Not every error body is JSON.
      }
      return 0;
   }

   static String transcoderErrorName(int code) {
      switch (code) {
         case ERR_NOT_READY: return "BADLY_FORMED_REQUEST";
         case ERR_MAX_SESSIONS: return "MAX_SESSIONS_EXCEEDED";
         case ERR_SESSION_GONE: return "SESSION_NOT_FOUND";
         case ERR_NO_RECORDING: return "SESSION_RECORDING_NOT_FOUND";
         case ERR_TOO_LONG: return "SESSION_RECORDING_TOO_LONG";
         case ERR_PROTECTED: return "SESSION_CONTENT_PROTECTED";
         case ERR_BAD_FORMAT: return "SESSION_CONTENT_INVALID_FORMAT";
         case ERR_RTT: return "SESSION_RTT_FAILED";
         default: return "0x" + Integer.toHexString(code);
      }
   }

   static String streamingStateName(int state) {
      switch (state) {
         case 0: return "Initializing";
         case 1: return "InGuidedSetup";
         case 2: return "RebootRequired";
         case 3: return "SoftwareUpdateRequired";
         case 4: return "Disabled";
         case 6: return "Ready";
         case 7: return "ThermalShutdown";
         default: return "unknown (" + state + ")";
      }
   }

   private static String describe(Http rsp) {
      if (rsp == null) return "request failed";
      int reason = transcoderError(rsp.text());
      return "got " + rsp.code + (reason == 0 ? "" : " " + transcoderErrorName(reason));
   }

   // False when the wait was interrupted, which is a cancelled job asking us to stop.
   private static boolean sleep(long ms) {
      try {
         Thread.sleep(ms);
         return true;
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         return false;
      }
   }

   private static class Http {
      int code;
      byte[] body;

      String text() {
         return body == null ? "" : new String(body, StandardCharsets.UTF_8);
      }
   }

   private static Http get(String url) {
      HttpURLConnection conn = null;
      try {
         conn = (HttpURLConnection) new URL(url).openConnection();
         conn.setConnectTimeout(CONNECT_TIMEOUT);
         conn.setReadTimeout(READ_TIMEOUT);
         Http rsp = new Http();
         rsp.code = conn.getResponseCode();
         InputStream in = rsp.code < 400 ? conn.getInputStream() : conn.getErrorStream();
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         if (in != null) {
            try {
               byte[] buf = new byte[8192];
               int n;
               while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
               in.close();
            }
         }
         rsp.body = out.toByteArray();
         return rsp;
      } catch (Exception e) {
         debug.print("HlsStream get " + url + " - " + e.getMessage());
         return null;
      } finally {
         if (conn != null) conn.disconnect();
      }
   }
}
