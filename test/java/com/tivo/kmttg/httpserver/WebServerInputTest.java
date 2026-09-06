package com.tivo.kmttg.httpserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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

import com.tivo.kmttg.main.config;

/**
 * Guards the parameters that reach a TiVo connection or an ffmpeg command line.
 *
 * A tivo name used to be passed straight to Remote, which treats a name it does
 * not recognise as a host name and then authenticates with the MAK - so naming
 * your own host got you the MAK and the rpc client certificate. Download urls
 * had the same problem: /transcode took one directly and /startJob took one
 * inside the recording json, and both are fetched with the MAK as the password.
 * Separately, maxrate was interpolated into an argument string that is split on
 * spaces, so a rate containing spaces added ffmpeg arguments of its own.
 *
 * These tests can only prove the refusals. Letting a request through ends in a
 * real connection to a real TiVo, or in ffmpeg, so the accepting side is pinned
 * down only as "not refused" - which is still enough to fail if a guard is
 * dropped, since dropping one turns a 403 into something else.
 */
public class WebServerInputTest {

   private static final String FOREIGN = "evil.example.com";

   @TempDir
   Path installDir;

   @TempDir
   Path shareDir;

   private int port;
   private String prevProgramDir, prevCache, prevOutputDir, prevMpegDir, prevMpegCutDir, prevEncodeDir;
   private int prevPort;
   private LinkedHashMap<String,String> prevShares;
   private LinkedHashMap<String,String> prevTivos;

   @BeforeEach
   public void startServer() throws IOException {
      Files.createDirectories(installDir.resolve("web/cache"));
      Files.write(installDir.resolve("index.html"), "<html>index</html>".getBytes(StandardCharsets.UTF_8));
      Files.write(shareDir.resolve("movie.mp4"), "video".getBytes(StandardCharsets.UTF_8));

      prevProgramDir = config.programDir;
      prevCache = config.httpserver_cache;
      prevOutputDir = config.outputDir;
      prevMpegDir = config.mpegDir;
      prevMpegCutDir = config.mpegCutDir;
      prevEncodeDir = config.encodeDir;
      prevPort = config.httpserver_port;
      prevShares = new LinkedHashMap<String,String>(config.httpserver_shares);
      prevTivos = new LinkedHashMap<String,String>(config.TIVOS);

      config.programDir = installDir.toString();
      config.httpserver_cache = installDir.resolve("web/cache").toString();
      config.outputDir = shareDir.toString();
      config.mpegDir = shareDir.toString();
      config.mpegCutDir = shareDir.toString();
      config.encodeDir = shareDir.toString();
      config.httpserver_shares.clear();
      config.httpserver_port = port = freePort();

      // One configured TiVo. Nothing listens on it, so a request that gets past
      // the guard fails to connect instead of hanging
      config.TIVOS.clear();
      config.TIVOS.put("Bolt", "127.0.0.1");

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
      config.TIVOS.clear();
      config.TIVOS.putAll(prevTivos);
   }

   // Every endpoint that ends in a connection carrying the MAK
   @Test
   public void unknownTivo_isRefusedEverywhere() throws IOException {
      for (String path : new String[] {
            "/rpc?operation=SysInfo&tivo=" + FOREIGN,
            "/rpc?operation=SPSave&tivo=" + FOREIGN,
            "/rpc?operation=keyEventMacro&sequence=select&tivo=" + FOREIGN,
            "/getMyShows?tivo=" + FOREIGN,
            "/getMyShows?xml=1&offset=0&tivo=" + FOREIGN,
            "/getToDo?tivo=" + FOREIGN,
            "/reboot?tivo=" + FOREIGN,
            "/ircode?codes=SELECT&tivo=" + FOREIGN,
            "/startJob?recording=%7B%7D&tivo=" + FOREIGN })
         assertEquals(403, status(path), path + " was allowed to name its own host");
   }

   // The guard has to admit the TiVos kmttg is configured for. Connecting then
   // fails, which is the point - it got far enough to try
   @Test
   public void configuredTivo_isNotRefused() throws IOException {
      for (String path : new String[] { "/rpc?operation=SysInfo&tivo=Bolt", "/reboot?tivo=Bolt" })
         assertNotEquals(403, status(path), path + " refused a configured TiVo");
   }

   // SPFiles and SPLoad read local files and never open a connection, so they
   // keep working without a TiVo to name
   @Test
   public void localOnlyRpcOperations_stillWork() throws IOException {
      Files.write(installDir.resolve("Bolt.sp"), "[{\"title\":\"x\"}]".getBytes(StandardCharsets.UTF_8));
      assertEquals(200, status("/rpc?operation=SPFiles&tivo=" + FOREIGN));
      assertEquals(200, status("/rpc?operation=SPLoad&tivo=" + FOREIGN + "&file="
            + URLEncoder.encode(installDir.resolve("Bolt.sp").toString(), "UTF-8")));
   }

   @Test
   public void foreignTranscodeUrl_isRefused() throws IOException {
      String path = "/transcode?format=webm&name=show&tivo=Bolt&url="
            + URLEncoder.encode("http://" + FOREIGN + "/download/x.TiVo", "UTF-8");
      assertEquals(403, status(path), "transcode fetched a url off a foreign host");
   }

   // /startJob takes its urls inside the recording json rather than as
   // parameters, and there is more than one of them: __url__ is the download,
   // __url_TiVoVideoDetails__ is the extended metadata fetch. Both go out with
   // the MAK, so checking only the download leaves the leak open
   @Test
   public void foreignJobUrl_isRefused() throws IOException {
      for (String key : new String[] { "__url__", "__url_TiVoVideoDetails__" }) {
         String recording = "{\"title\":\"x\",\"" + key + "\":\"http://" + FOREIGN + "/download/x.TiVo\"}";
         String path = "/startJob?tivo=Bolt&recording=" + URLEncoder.encode(recording, "UTF-8");
         assertEquals(403, status(path), key + " was fetched from a foreign host");
      }
   }

   @Test
   public void jobUrlOnTheConfiguredTivo_isNotRefused() throws IOException {
      String recording = "{\"title\":\"x\",\"__url__\":\"http://127.0.0.1:80/download/x.TiVo\"}";
      String path = "/startJob?tivo=Bolt&recording=" + URLEncoder.encode(recording, "UTF-8");
      assertNotEquals(403, status(path), "refused a url on the configured TiVo");
   }

   // The injection: split(" ") on the argument string turns these into extra
   // ffmpeg arguments. The rest are rates ffmpeg itself would take, but the hls
   // template parses with Integer.parseInt(maxrate.replace("k","")) and threw on
   // them long before this guard existed
   @Test
   public void maxrate_rejectsAnythingButAPlainRate() throws IOException {
      for (String rate : new String[] {
            "3000k -f data " + shareDir.resolve("pwned"),
            "3000k -y /etc/passwd",
            "zzz", "5M", "3000K", "500KB", "-3000k", "" })
         assertEquals(400, status("/transcode?killall=1&maxrate=" + URLEncoder.encode(rate, "UTF-8")),
            "maxrate '" + rate + "' reached the ffmpeg command line");
   }

   // Each of these threw out of the handler, and only IOException was caught
   // around it, so the client got a closed socket instead of a response
   @Test
   public void badNumericParameters_giveAnErrorNotADroppedConnection() throws IOException {
      assertEquals(400, status("/getMyShows?xml=1&offset=abc&tivo=Bolt"), "bad offset");
      assertEquals(400, status("/transcode?format=webm&name=x&tivo=Bolt&duration=abc&url="
            + URLEncoder.encode("http://127.0.0.1:80/download/x.TiVo", "UTF-8")), "bad duration");
   }

   // config.mpegDir and friends need not exist - listFiles() returns null for a
   // missing directory, which used to take the whole request down
   @Test
   public void missingShareDirectory_doesNotBreakTheFileList() throws IOException {
      config.encodeDir = shareDir.resolve("gone").toString();
      assertEquals(200, status("/getVideoFiles"));
      assertEquals(200, status("/getVideoFileDetails"));
   }

   // URI.getHost hands back an IPv6 literal with its brackets still on, so an
   // exact compare against the bare form in config would refuse its own TiVo
   @Test
   public void ipv6AndMixedCaseHosts_matchTheConfiguredTivo() throws IOException {
      config.TIVOS.put("Edge", "::1");
      config.TIVOS.put("Named", "tivo.local");
      for (String url : new String[] {
            "http://[::1]:80/download/x.TiVo",
            "http://TiVo.LOCAL:80/download/x.TiVo" }) {
         String recording = "{\"title\":\"x\",\"__url__\":\"" + url + "\"}";
         assertNotEquals(403, status("/startJob?tivo=Bolt&recording="
               + URLEncoder.encode(recording, "UTF-8")), url + " was refused");
      }
   }

   @Test
   public void maxrate_acceptsTheRatesTheUiOffers() throws IOException {
      for (String rate : new String[] { "500k", "1000k", "2000k", "3000k", "5000k", "3000" })
         assertEquals(200, status("/transcode?killall=1&maxrate=" + rate), "rejected rate " + rate);
   }

   /* ---- helpers ---- */

   private int status(String path) throws IOException {
      HttpURLConnection c = open(path);
      try {
         return c.getResponseCode();
      } finally {
         c.disconnect();
      }
   }

   private HttpURLConnection open(String path) throws IOException {
      HttpURLConnection c = (HttpURLConnection) new URL("http://localhost:" + port + path).openConnection();
      c.setConnectTimeout(5000);
      c.setReadTimeout(15000);
      c.setInstanceFollowRedirects(false);
      return c;
   }

   private static int freePort() throws IOException {
      try (ServerSocket s = new ServerSocket(0)) {
         return s.getLocalPort();
      }
   }
}
