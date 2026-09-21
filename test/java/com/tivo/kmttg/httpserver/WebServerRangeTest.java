package com.tivo.kmttg.httpserver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tivo.kmttg.main.config;

/**
 * Guards partial retrieval of the video files the share browser serves. Every
 * player that opens one of these urls seeks with a Range header - Safari and
 * the Android players ask for the tail of an mp4 first to find its index - so
 * ranges are the normal way these files are read, not an edge case.
 *
 * The invariant each case checks is the one a client cannot recover from: the
 * body that arrives has to be exactly the bytes the Content-Length and
 * Content-Range promised. A response that promises more than the file holds
 * ends with the connection dropped part way through, which the player shows as
 * a failed video rather than as a seek that went wrong.
 *
 * Runs a real server over a raw socket, because an http client normalizes the
 * Range header and hides a short body.
 */
public class WebServerRangeTest {

   private static final int LENGTH = 300;

   @TempDir
   Path installDir;

   @TempDir
   Path shareDir;

   private int port;
   private byte[] content;
   private String prevProgramDir, prevCache, prevOutputDir, prevMpegDir, prevMpegCutDir, prevEncodeDir;
   private int prevPort;
   private LinkedHashMap<String,String> prevShares;

   @BeforeEach
   public void startServer() throws IOException {
      Files.createDirectories(installDir.resolve("web/cache"));
      Files.write(installDir.resolve("index.html"), "<html>index</html>".getBytes(StandardCharsets.UTF_8));

      // Recognisable bytes, so a range that returns the wrong part of the file
      // is a mismatch rather than a coincidence
      content = new byte[LENGTH];
      for (int i = 0; i < LENGTH; i++)
         content[i] = (byte)(i % 251);
      Files.write(shareDir.resolve("movie.mp4"), content);

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

   @Test
   public void explicitRange_sendsExactlyThoseBytes() throws IOException {
      Reply r = get("bytes=100-199");
      assertEquals(206, r.status);
      assertEquals("bytes 100-199/300", r.header("Content-Range"));
      assertArrayEquals(Arrays.copyOfRange(content, 100, 200), r.body);
   }

   // How a player resumes an interrupted download
   @Test
   public void openEndedRange_sendsTheRestOfTheFile() throws IOException {
      Reply r = get("bytes=290-");
      assertEquals(206, r.status);
      assertEquals("bytes 290-299/300", r.header("Content-Range"));
      assertArrayEquals(Arrays.copyOfRange(content, 290, 300), r.body);
   }

   // How a player finds an mp4's index when it sits at the end of the file
   @Test
   public void suffixRange_sendsTheLastBytes() throws IOException {
      Reply r = get("bytes=-10");
      assertEquals(206, r.status);
      assertEquals("bytes 290-299/300", r.header("Content-Range"));
      assertArrayEquals(Arrays.copyOfRange(content, 290, 300), r.body);
   }

   // Same request against a file shorter than the tail being asked for - a
   // player asking for the last 128k of a small clip does exactly this.
   // RFC7233#2.1: the whole file is the answer. Computing a negative start
   // instead promised a body longer than the file, and the transfer then ran
   // off the end and dropped the connection mid-response.
   @Test
   public void suffixRangeLongerThanTheFile_sendsTheWholeFile() throws IOException {
      Reply r = get("bytes=-131072");
      assertTrue(r.status == 200 || r.status == 206, "unexpected status " + r.status);
      assertArrayEquals(content, r.body, "the whole file should have come back");
      String range = r.header("Content-Range");
      if (range != null)
         assertEquals("bytes 0-299/300", range);
   }

   // A seek past the end of a file that has since been truncated or replaced
   @Test
   public void rangeStartingPastTheEnd_isUnsatisfiable() throws IOException {
      Reply r = get("bytes=500-600");
      assertEquals(416, r.status);
      // Tells the client how long the file really is, so it can seek again
      assertEquals("bytes */300", r.header("Content-Range"));
   }

   // RFC7233#3.1 - a Range that cannot be parsed is ignored, not an error, so
   // the client still gets its video instead of a failure it cannot act on
   @Test
   public void malformedRange_isIgnoredAndTheWholeFileIsSent() throws IOException {
      for (String range : new String[] {
            "bytes=abc", "bytes=", "bytes=-", "bytes=--5", "bytes=199-100",
            "bytes=1-2-3", "bytes=+10-20", "seconds=0-10", "0-10" }) {
         Reply r = get(range);
         assertEquals(200, r.status, range);
         assertArrayEquals(content, r.body, range);
      }
   }

   // Multiple ranges are answered with the one range that covers them all, so
   // what goes out is a superset of what was asked for - which is allowed, as
   // long as the Content-Range says so and the body matches it
   @Test
   public void multipleRanges_sendOneCoveringRange() throws IOException {
      Reply r = get("bytes=0-9,200-209");
      assertEquals(206, r.status);
      assertEquals("bytes 0-209/300", r.header("Content-Range"));
      assertArrayEquals(Arrays.copyOfRange(content, 0, 210), r.body);
   }

   // A player checks what a seek would cost before making it
   @Test
   public void headWithARange_sendsTheHeadersAndNoBody() throws IOException {
      Reply r = request("HEAD", "bytes=100-199");
      assertEquals(206, r.status);
      assertEquals("bytes 100-199/300", r.header("Content-Range"));
      assertEquals("100", r.header("Content-Length"));
      assertEquals(0, r.body.length, "HEAD sent a body");
   }

   /* ---- helpers ---- */

   private Reply get(String range) throws IOException {
      return request("GET", range);
   }

   private Reply request(String method, String range) throws IOException {
      String head = method + " /mpegDir/movie.mp4 HTTP/1.1\r\nHost: localhost\r\n"
         + (range == null ? "" : "Range: " + range + "\r\n")
         + "Connection: close\r\n\r\n";
      try (Socket sock = new Socket("127.0.0.1", port)) {
         sock.setSoTimeout(5000);
         sock.getOutputStream().write(head.getBytes(StandardCharsets.ISO_8859_1));
         sock.getOutputStream().flush();
         ByteArrayOutputStream buf = new ByteArrayOutputStream();
         InputStream in = sock.getInputStream();
         byte[] b = new byte[4096];
         int n;
         try {
            while ((n = in.read(b)) > 0)
               buf.write(b, 0, n);
         } catch (IOException reset) {
            // The server hung up; what arrived is what the client saw
         }
         return new Reply(buf.toByteArray(), method, range);
      }
   }

   /** A raw response split into its status, headers and body bytes. */
   private static class Reply {
      final int status;
      final byte[] body;
      private final String headers;

      Reply(byte[] raw, String method, String range) {
         String text = new String(raw, StandardCharsets.ISO_8859_1);
         int end = text.indexOf("\r\n\r\n");
         assertTrue(end > 0, "no headers in the response to Range: " + range);
         headers = text.substring(0, end + 2);
         body = Arrays.copyOfRange(raw, end + 4, raw.length);
         status = Integer.parseInt(headers.substring(9, 12));
         // The promise a client reads the body against. A mismatch is the
         // failure mode every case here is really guarding against
         String length = header("Content-Length");
         if (length != null && ! method.equals("HEAD"))
            assertEquals(Integer.parseInt(length), body.length,
               "Content-Length does not match the body that arrived, for Range: " + range);
      }

      String header(String name) {
         String want = "\r\n" + name.toLowerCase(Locale.US) + ": ";
         String hay = headers.toLowerCase(Locale.US);
         int at = hay.indexOf(want);
         if (at < 0)
            return null;
         return headers.substring(at + want.length(), headers.indexOf("\r\n", at + 2)).trim();
      }
   }

   private static int freePort() throws IOException {
      try (ServerSocket s = new ServerSocket(0)) {
         return s.getLocalPort();
      }
   }
}
