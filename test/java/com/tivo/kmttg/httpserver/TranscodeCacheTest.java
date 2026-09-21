package com.tivo.kmttg.httpserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;

/**
 * Guards the transcode cache - the directory every stream writes its playlist
 * and segments into, and the one the Stream page's cached list plays back out
 * of. Each transcode owns the files carrying its own "t<n>" prefix, and these
 * pin that ownership: removing one cached recording must not take another's
 * files with it, and a new stream must not start writing over one.
 *
 * Works on cache files directly rather than running ffmpeg, because what is
 * being tested is which files a request claims, not what is in them.
 */
public class TranscodeCacheTest {

   @TempDir
   Path installDir;

   @TempDir
   Path shareDir;

   private int port;
   private Path cache;
   private String prevProgramDir, prevCache, prevOutputDir, prevMpegDir, prevMpegCutDir, prevEncodeDir;
   private int prevPort;
   private LinkedHashMap<String,String> prevShares;

   @BeforeEach
   public void startServer() throws IOException {
      cache = Files.createDirectories(installDir.resolve("web/cache"));
      Files.write(installDir.resolve("index.html"), "<html>index</html>".getBytes(StandardCharsets.UTF_8));

      prevProgramDir = config.programDir;
      prevCache = config.httpserver_cache;
      prevOutputDir = config.outputDir;
      prevMpegDir = config.mpegDir;
      prevMpegCutDir = config.mpegCutDir;
      prevEncodeDir = config.encodeDir;
      prevPort = config.httpserver_port;
      prevShares = new LinkedHashMap<String,String>(config.httpserver_shares);

      config.programDir = installDir.toString();
      config.httpserver_cache = cache.toString();
      config.outputDir = shareDir.toString();
      config.mpegDir = shareDir.toString();
      config.mpegCutDir = shareDir.toString();
      config.encodeDir = shareDir.toString();
      config.httpserver_shares.clear();
      config.httpserver_port = port = freePort();

      new kmttgServer();
      assertTrue(config.httpserver != null, "server failed to start");
   }

   @AfterEach
   public void stopServer() {
      if (config.httpserver != null) {
         config.httpserver.stop();
         config.httpserver = null;
      }
      config.programDir = prevProgramDir;
      config.httpserver_cache = prevCache;
      config.outputDir = prevOutputDir;
      config.mpegDir = prevMpegDir;
      config.mpegCutDir = prevMpegCutDir;
      config.encodeDir = prevEncodeDir;
      config.httpserver_port = prevPort;
      config.httpserver_shares.clear();
      config.httpserver_shares.putAll(prevShares);
   }

   // The cached list is what the browser plays from, so it has to carry the
   // url to play, the name written beside the playlist and how much of the
   // recording is in it
   @Test
   public void cachedList_describesWhatIsPlayable() throws Exception {
      cache("t0.m3u8", "#EXTM3U\n#EXTINF:10.5,\nt0-00000.ts\n#EXTINF:9.5,\n"
         + "t0-00001.ts\n#EXT-X-ENDLIST\n");
      cache("t0.m3u8.txt", "Bob's Burgers");
      cache("t0-00000.ts", "segment");
      cache("t1.webm", "webm bytes");
      cache("t1.webm.txt", "Archer");

      JSONArray cached = new JSONArray(body("/transcode?getCached=1"));
      assertEquals(2, cached.length(), "expected both cached transcodes: " + cached);
      JSONObject hls = entryFor(cached, "/web/cache/t0.m3u8");
      assertTrue(hls.getString("name").contains("Bob's Burgers"));
      assertEquals(20.0, hls.getDouble("time"), 0.01, "playlist time not summed");
      assertTrue(entryFor(cached, "/web/cache/t1.webm").getString("name").contains("Archer"));
   }

   // A transcode that was killed, or died with kmttg, leaves a playlist with no
   // #EXT-X-ENDLIST in it, which a player treats as a live stream and waits
   // forever on. Listing the cache terminates it so what was captured plays.
   @Test
   public void cachedList_terminatesAPlaylistLeftOpen() throws Exception {
      cache("t0.m3u8", "#EXTM3U\n#EXTINF:10.0,\nt0-00000.ts\n");

      JSONArray cached = new JSONArray(body("/transcode?getCached=1"));
      assertEquals(1, cached.length(), "cached list: " + cached);
      assertTrue(contents("t0.m3u8").contains("#EXT-X-ENDLIST"),
         "an abandoned playlist was left unplayable: " + contents("t0.m3u8"));
   }

   // Removing one cached recording used to take every file whose name started
   // with the same text, and "t1" is the start of "t10" - so clearing an old
   // transcode deleted a later one's playlist and segments too
   @Test
   public void removingOneCachedTranscode_leavesTheOthersAlone() throws Exception {
      cache("t1.m3u8", "#EXTM3U\n#EXT-X-ENDLIST\n");
      cache("t1.m3u8.txt", "old show");
      cache("t1-00000.ts", "old segment");
      cache("t10.m3u8", "#EXTM3U\n#EXT-X-ENDLIST\n");
      cache("t10.m3u8.txt", "kept show");
      cache("t10-00000.ts", "kept segment");

      String message = body("/transcode?removeCached=" + enc("/web/cache/t1.m3u8"));

      assertFalse(exists("t1.m3u8"), "the transcode asked about was not removed");
      assertFalse(exists("t1-00000.ts"), "its segments were left behind");
      assertTrue(exists("t10.m3u8"), "removing t1 deleted t10's playlist");
      assertTrue(exists("t10-00000.ts"), "removing t1 deleted t10's segments");
      assertTrue(exists("t10.m3u8.txt"), "removing t1 deleted t10's name file");
      assertEquals("Removed 1 cached items", message);
   }

   @Test
   public void removingEverything_clearsTheCache() throws Exception {
      cache("t1.m3u8", "#EXTM3U\n#EXT-X-ENDLIST\n");
      cache("t1-00000.ts", "segment");
      cache("t2.webm", "webm bytes");

      assertEquals("Removed 2 cached items", body("/transcode?removeCached=all"));
      assertEquals(0, cache.toFile().list().length, "the cache still holds files");
   }

   // The browser sends whatever url its cached list was built from, which may
   // name a transcode that has already been cleared
   @Test
   public void removingSomethingThatIsNotThere_removesNothing() throws Exception {
      cache("t1.m3u8", "#EXTM3U\n#EXT-X-ENDLIST\n");

      assertEquals("Removed 0 cached items",
         body("/transcode?removeCached=" + enc("/web/cache/t7.m3u8")));
      assertTrue(exists("t1.m3u8"), "an unrelated transcode was removed");
   }

   // A stream from a TiVo names its playlist and segments when it starts. It
   // used to take the prefix straight off the counter without looking at the
   // cache, so it wrote over whatever cached transcode already held that name.
   @Test
   public void tivoStream_doesNotTakeACachedTranscodesFileNames() throws Exception {
      cache("t1.m3u8", "#EXTM3U\n#EXT-X-ENDLIST\n");
      cache("t1.m3u8.txt", "a recording the user kept");
      config.httpserver.transcode_counter = 1;

      TiVoTranscode tc = new TiVoTranscode(
         "http://127.0.0.1/download/x.TiVo?id=1", "Show", "Bolt");
      String javaHome = System.getProperty("java.home");
      try {
         // Points the decoder at a jre that is not there, so the run fails on
         // its first exec and nothing is spawned. The cache file names are
         // already chosen by then, which is what this is about.
         System.setProperty("java.home", installDir.resolve("no-jre").toString());
         assertNull(tc.hls(), "the transcode should have failed without a jre");
      } finally {
         System.setProperty("java.home", javaHome);
         tc.kill();
      }

      assertFalse(new File(tc.segmentFile).exists(),
         "a new stream claimed a cached transcode's playlist: " + tc.segmentFile);
      assertTrue(contents("t1.m3u8").contains("#EXT-X-ENDLIST"),
         "the cached playlist was overwritten");
   }

   /* ---- helpers ---- */

   private void cache(String name, String content) throws IOException {
      Files.write(cache.resolve(name), content.getBytes(StandardCharsets.UTF_8));
   }

   private boolean exists(String name) {
      return Files.exists(cache.resolve(name));
   }

   private String contents(String name) throws IOException {
      return new String(Files.readAllBytes(cache.resolve(name)), StandardCharsets.UTF_8);
   }

   private static JSONObject entryFor(JSONArray cached, String url) throws Exception {
      for (int i = 0; i < cached.length(); ++i) {
         JSONObject json = cached.getJSONObject(i);
         if (url.equals(json.getString("url")))
            return json;
      }
      throw new AssertionError(url + " is not in the cached list: " + cached);
   }

   private static String enc(String value) throws IOException {
      return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
   }

   private String body(String path) throws IOException {
      HttpURLConnection c = (HttpURLConnection)
         new URL("http://localhost:" + port + path).openConnection();
      c.setConnectTimeout(5000);
      c.setReadTimeout(15000);
      c.setInstanceFollowRedirects(false);
      try (InputStream in = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream()) {
         assertEquals(200, c.getResponseCode(), path);
         return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
      } finally {
         c.disconnect();
      }
   }

   private static int freePort() throws IOException {
      try (ServerSocket s = new ServerSocket(0)) {
         return s.getLocalPort();
      }
   }
}
