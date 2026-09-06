package com.tivo.kmttg.httpserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tivo.kmttg.main.config;

/**
 * Guards how the web server shapes its replies. Everything used to go out as
 * text/html, so a browser opening an API url directly rendered show titles and
 * file names as markup, and the share browser wrote file names into its index
 * page unescaped. Both put whoever names a recording in charge of what runs in
 * the page.
 *
 * Runs a real server, because content types and generated html are what is
 * being tested.
 */
public class WebServerResponseTest {

   @TempDir
   Path installDir;

   @TempDir
   Path shareDir;

   private int port;
   private String prevProgramDir, prevCache, prevOutputDir, prevMpegDir, prevMpegCutDir, prevEncodeDir;
   private int prevPort;
   private LinkedHashMap<String,String> prevShares;

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

      config.programDir = installDir.toString();
      config.httpserver_cache = installDir.resolve("web/cache").toString();
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

   // These carry file names and, from a TiVo, show titles. Served as html they
   // are a script tag away from running in the server's own origin
   @Test
   public void jsonReplies_areNotServedAsHtml() throws IOException {
      for (String path : new String[] { "/getBrowserShares", "/getTivos",
            "/getRpcTivos", "/getVideoFiles", "/transcode?running=1" })
         assertEquals("application/json; charset=utf-8", contentType(path), path);
   }

   @Test
   public void statusReplies_areNotServedAsHtml() throws IOException {
      assertEquals("text/plain; charset=utf-8", contentType("/transcode?killall=1"));
   }

   @Test
   public void jsonReplies_areStillParseable() throws IOException {
      assertEquals("[\"mpegDir\",\"mpegCutDir\",\"encodeDir\"]", body("/getBrowserShares"));
   }

   @Test
   public void directoryIndex_escapesFileNames() throws IOException {
      Files.write(shareDir.resolve("Tom & Jerry's.mp4"), "v".getBytes(StandardCharsets.UTF_8));
      String index = body("/mpegDir/");
      assertTrue(index.contains("Tom &amp; Jerry&#39;s.mp4"), "file name not escaped: " + index);
      assertTrue(!index.contains("Tom & Jerry's.mp4"), "file name written raw: " + index);
   }

   // '#' is legal in a file name and ends the url, so an unencoded link points
   // at the directory rather than at the file
   @Test
   public void directoryIndex_urlEncodesLinks() throws IOException {
      Files.write(shareDir.resolve("S01#02.mp4"), "v".getBytes(StandardCharsets.UTF_8));
      String index = body("/mpegDir/");
      assertTrue(index.contains("href=\"/mpegDir/S01%2302.mp4\""), "link not url-encoded: " + index);
      assertEquals(200, status("/mpegDir/S01%2302.mp4"), "encoded link does not resolve");
   }

   // The real thing the escaping is for. Windows rejects '<' in a file name,
   // so this only runs where a recording can actually be named this way
   @Test
   public void directoryIndex_neutralisesMarkupInFileNames() throws IOException {
      String name = "<img src=x onerror=alert(1)>.mp4";
      try {
         Files.write(shareDir.resolve(name), "v".getBytes(StandardCharsets.UTF_8));
      } catch (Exception e) {
         Assumptions.abort("filesystem will not accept a file named " + name);
      }
      String index = body("/mpegDir/");
      assertTrue(!index.contains("<img"), "markup in a file name reached the page: " + index);
      assertTrue(index.contains("&lt;img"), "file name missing from index: " + index);
   }

   /* ---- helpers ---- */

   private String contentType(String path) throws IOException {
      HttpURLConnection c = open(path);
      try {
         assertEquals(200, c.getResponseCode(), path);
         return c.getContentType();
      } finally {
         c.disconnect();
      }
   }

   private int status(String path) throws IOException {
      HttpURLConnection c = open(path);
      try {
         return c.getResponseCode();
      } finally {
         c.disconnect();
      }
   }

   private String body(String path) throws IOException {
      HttpURLConnection c = open(path);
      try (InputStream in = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream()) {
         return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
      } finally {
         c.disconnect();
      }
   }

   private HttpURLConnection open(String path) throws IOException {
      HttpURLConnection c = (HttpURLConnection) new URL("http://localhost:" + port + path).openConnection();
      c.setConnectTimeout(5000);
      c.setReadTimeout(5000);
      c.setInstanceFollowRedirects(false);
      return c;
   }

   private static int freePort() throws IOException {
      try (ServerSocket s = new ServerSocket(0)) {
         return s.getLocalPort();
      }
   }
}
