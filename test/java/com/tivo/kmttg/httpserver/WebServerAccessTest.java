package com.tivo.kmttg.httpserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
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
 * Guards the web server's file exposure. The root context used to map "/" at
 * the kmttg install dir, which put config.ini - holding the MAK, the TiVo
 * account credentials and the domain token - plus the logs and the RPC trace a
 * plain GET away. These tests pin down what may and may not be reached, so
 * re-adding a broad context or dropping the SPLoad guard fails the build
 * rather than quietly reopening the hole.
 *
 * Runs a real server against a throwaway install dir, because the thing being
 * tested is the context routing itself.
 */
public class WebServerAccessTest {

   @TempDir
   Path installDir;

   @TempDir
   Path shareDir;

   private int port;
   private Path videos;
   private String prevProgramDir, prevCache, prevMpegDir, prevMpegCutDir, prevEncodeDir;
   private int prevPort;
   private LinkedHashMap<String,String> prevShares;

   @BeforeEach
   public void startServer() throws IOException {
      // Files that must stay unreachable
      write("config.ini", "<MAK>\n1234567890\n<tivo_password>\nhunter2\n");
      write("debug.log", "secrets in here");
      write("rpc.jsonl", "{\"auth\":\"token\"}");

      // Sits next to /web and its name starts with "web", which is the whole
      // point - a string prefix check would have called it part of the context
      write("web_private/secret.txt", "not reachable");

      // Files that must stay reachable
      write("index.html", "<html>index</html>");
      write("README.html", "<html>readme</html>");
      write("web/ToDo.html", "<html>todo</html>");
      write("web/js/table.js", "// table");
      write("rc_images/tivo.png", "not really a png");
      // A valid .sp file - a JSON array, as SPSave writes
      write("Bolt.sp", "[{\"title\":\"Bob's Burgers\"}]");
      Files.createDirectories(installDir.resolve("web/cache"));

      // Same shape one level out: the served share and a sibling of it
      videos = Files.createDirectories(shareDir.resolve("videos"));
      Files.createDirectories(shareDir.resolve("videos_private"));
      Files.write(videos.resolve("movie.mp4"), "video".getBytes(StandardCharsets.UTF_8));
      Files.createDirectories(videos.resolve("sub"));
      Files.write(shareDir.resolve("videos_private/secret.txt"),
         "not reachable".getBytes(StandardCharsets.UTF_8));

      prevProgramDir = config.programDir;
      prevCache = config.httpserver_cache;
      prevMpegDir = config.mpegDir;
      prevMpegCutDir = config.mpegCutDir;
      prevEncodeDir = config.encodeDir;
      prevPort = config.httpserver_port;
      prevShares = new LinkedHashMap<String,String>(config.httpserver_shares);

      config.programDir = installDir.toString();
      config.httpserver_cache = installDir.resolve("web/cache").toString();
      config.mpegDir = videos.toString();
      config.mpegCutDir = videos.toString();
      config.encodeDir = videos.toString();
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
      config.mpegDir = prevMpegDir;
      config.mpegCutDir = prevMpegCutDir;
      config.encodeDir = prevEncodeDir;
      config.httpserver_port = prevPort;
      config.httpserver_shares.clear();
      config.httpserver_shares.putAll(prevShares);
   }

   @Test
   public void installDirFiles_areNotDownloadable() throws IOException {
      // The whole point: nothing in the install dir is served just by being there
      for (String path : new String[] { "/config.ini", "/debug.log", "/rpc.jsonl", "/Bolt.sp" })
         assertEquals(404, status(path), path + " is downloadable");
   }

   @Test
   public void installDirListing_isNotBrowsable() throws IOException {
      // "/" must give index.html, never a generated listing of the install dir
      assertEquals(200, status("/"));
      assertTrue(body("/").contains("index"), "/ did not serve index.html");
      assertTrue(!body("/").contains("config.ini"), "/ leaked a directory listing");
   }

   @Test
   public void publicPages_areStillServed() throws IOException {
      assertEquals(200, status("/"));
      assertEquals(200, status("/index.html"));
      assertEquals(200, status("/README.html"));
   }

   @Test
   public void webAssets_areStillServed() throws IOException {
      assertEquals(200, status("/web/ToDo.html"));
      assertEquals(200, status("/web/js/table.js"));
   }

   // Remote.css asks for these by absolute path, so losing the context would
   // leave the remote page a blank rectangle
   @Test
   public void remoteImages_areStillServed() throws IOException {
      assertEquals(200, status("/rc_images/tivo.png"));
   }

   // stop() used to return while a handler was still streaming, leaving the
   // file it was sending open. configMain stops the server and immediately
   // builds a new one, and on Windows an open handle blocks the delete of
   // anything that was being served - so stopped has to mean the files are
   // released, not just that the port is closed.
   @Test
   public void stoppingReleasesTheFilesBeingServed() throws IOException {
      // Big enough that the send cannot complete inside the socket buffer, so
      // the handler is still inside the transfer when the client walks away
      byte[] big = new byte[4 * 1024 * 1024];
      Path served = installDir.resolve("web/big.bin");
      Files.write(served, big);

      // Read the status line only, then drop the connection mid-body
      HttpURLConnection c = open("/web/big.bin");
      try {
         assertEquals(200, c.getResponseCode());
      } finally {
         c.disconnect();
      }

      kmttgServer server = config.httpserver;
      server.stop();
      config.httpserver = null;

      // The portable half: a handler is taken out of connections only in its
      // own finally, so an empty set is stop() having waited for it. Without
      // the wait the straggler is still in there.
      assertTrue(server.connections.isEmpty(),
         "stop() returned with " + server.connections.size() + " connection(s) still being handled");
      // ...and the half that only bites on Windows, which is where an open
      // handle actually blocks the delete
      Files.delete(served);
   }

   @Test
   public void videoShares_areStillServed() throws IOException {
      assertEquals(200, status("/mpegDir/movie.mp4"));
      // Browsing a share is the Share Browser's whole job
      assertEquals(200, status("/mpegDir/"));
   }

   @Test
   public void traversalOutOfAllowedContexts_isRefused() throws IOException {
      for (String path : new String[] {
            "/web/../config.ini",
            "/web/js/../../config.ini",
            "/rc_images/../config.ini",
            "/web/%2e%2e/config.ini",
            "/mpegDir/../../config.ini" })
         assertNotEquals(200, status(path), path + " escaped its context");
   }

   // Escaping upwards lands in the parent dir, which is easy to catch. Escaping
   // sideways into a directory whose name merely starts with the context's is
   // not - a string prefix test accepts it, and this used to serve the file
   @Test
   public void traversalIntoSiblingWithSharedPrefix_isRefused() throws IOException {
      for (String path : new String[] {
            "/web/../web_private/secret.txt",
            "/mpegDir/../videos_private/secret.txt",
            "/encodeDir/../videos_private/secret.txt" })
         assertNotEquals(200, status(path), path + " reached a sibling of its context");
   }

   // The OWASP encodings of ../ and ..\ - the path is decoded before it is
   // resolved, so these arrive as separators. %5c matters on Windows, where
   // a backslash is a separator and the '/'-only cases above miss it
   @Test
   public void encodedTraversal_isRefused() throws IOException {
      for (String target : new String[] {
            "/web/..%2fconfig.ini",
            "/web/%2e%2e%2fconfig.ini",
            "/web/..%5cconfig.ini",
            "/web/%2e%2e%5cconfig.ini",
            "/web/..%2fweb_private%2fsecret.txt",
            "/web/..%5cweb_private%5csecret.txt",
            "/mpegDir/..%2fvideos_private%2fsecret.txt",
            "/mpegDir/..%5cvideos_private%5csecret.txt",
            // the cache is its own context over a configurable directory
            "/web/cache/../../config.ini",
            "/web/cache/..%2f..%2fconfig.ini" })
         assertRefused(target);
   }

   // Double encoding, overlong utf-8 and the "....//" filter-dodge. None of
   // these resolve to a separator here, so they end up as ordinary missing
   // names - what matters is that none of them returns a file
   @Test
   public void obfuscatedTraversal_isRefused() throws IOException {
      for (String target : new String[] {
            "/web/%252e%252e%252fconfig.ini",
            "/web/..%255cconfig.ini",
            "/web/..%c0%afconfig.ini",
            "/web/..%c1%9cconfig.ini",
            "/web/....//config.ini",
            "/web/config.ini%00.png",
            "/web/C:/Windows/win.ini",
            "/web//etc/passwd" })
         assertRefused(target);
   }

   // A path the filesystem cannot resolve at all used to surface as a 500 from
   // a canonicalization error escaping the handler; it names nothing, so it is
   // a plain 404
   @Test
   public void unresolvablePaths_are404() throws IOException {
      for (String target : new String[] {
            "/web/config.ini%00.png",
            "/web/C:/Windows/win.ini",
            "/web/....//config.ini" })
         assertTrue(firstLine(rawGet(target)).contains("404"),
            target + " did not 404: " + firstLine(rawGet(target)));
   }

   // The guard rejects paths that leave the context, not paths that merely
   // contain dot segments - these resolve back inside and must still serve
   @Test
   public void dotSegmentsInsideTheContext_stillServe() throws IOException {
      assertEquals(200, status("/mpegDir/sub/../movie.mp4"));
      assertEquals(200, status("/mpegDir/./movie.mp4"));
      assertEquals(200, status("/web/js/../ToDo.html"));
   }

   @Test
   public void spLoad_readsARealSeasonPassFile() throws IOException {
      String path = spLoad(installDir.resolve("Bolt.sp").toString());
      assertEquals(200, status(path));
      assertTrue(body(path).contains("Bob's Burgers"), "season pass file not returned");
   }

   @Test
   public void spLoad_refusesAnythingButAnSpFileInTheInstallDir() throws IOException {
      // Has to exist, or it is rejected for being absent and the location check
      // this case is here to exercise never runs
      Files.write(shareDir.resolve("outside.sp"), "[]".getBytes(StandardCharsets.UTF_8));
      String[] rejected = {
         installDir.resolve("config.ini").toString(),   // not a .sp
         installDir.resolve("debug.log").toString(),
         "..\\..\\config.ini",                          // relative escape
         "../../config.ini",
         shareDir.resolve("outside.sp").toString(),     // .sp, but not in the install dir
         installDir.resolve("nope.sp").toString()       // .sp in the right place but absent
      };
      for (String file : rejected)
         assertNotEquals(200, status(spLoad(file)), file + " was accepted by SPLoad");
   }

   // A .sp sitting outside the install dir must not be reachable even though
   // the extension is right - the guard is about location, not just suffix
   @Test
   public void spLoad_refusesSpFileOutsideInstallDir() throws IOException {
      Path outside = shareDir.resolve("outside.sp");
      Files.write(outside, "[{\"title\":\"nope\"}]".getBytes(StandardCharsets.UTF_8));
      assertEquals(403, status(spLoad(outside.toString())));
   }

   /* ---- helpers ---- */

   // Asserts nothing was served and no secret came back. Sent over a raw socket
   // so the exact bytes reach the server - an http client is free to normalize
   // a path before it goes out, which would test the client, not the server.
   private void assertRefused(String target) throws IOException {
      String response = rawGet(target);
      assertTrue(response.startsWith("HTTP/1.1 4") || response.startsWith("HTTP/1.1 5"),
         target + " was not refused: " + firstLine(response));
      for (String secret : new String[] { "hunter2", "1234567890", "not reachable" })
         assertTrue(!response.contains(secret), target + " leaked " + secret);
   }

   private String rawGet(String target) throws IOException {
      try (Socket sock = new Socket("127.0.0.1", port)) {
         sock.setSoTimeout(5000);
         sock.getOutputStream().write(
            ("GET " + target + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
               .getBytes(StandardCharsets.ISO_8859_1));
         sock.getOutputStream().flush();
         ByteArrayOutputStream buf = new ByteArrayOutputStream();
         byte[] b = new byte[4096];
         int n;
         while ((n = sock.getInputStream().read(b)) != -1)
            buf.write(b, 0, n);
         return new String(buf.toByteArray(), StandardCharsets.ISO_8859_1);
      }
   }

   private static String firstLine(String response) {
      int eol = response.indexOf("\r\n");
      return eol == -1 ? "(no response)" : response.substring(0, eol);
   }

   private void write(String relative, String content) throws IOException {
      Path p = installDir.resolve(relative);
      Files.createDirectories(p.getParent());
      Files.write(p, content.getBytes(StandardCharsets.UTF_8));
   }

   private static String spLoad(String file) throws IOException {
      return "/rpc?operation=SPLoad&tivo=Bolt&file="
            + URLEncoder.encode(file, StandardCharsets.UTF_8.name());
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
