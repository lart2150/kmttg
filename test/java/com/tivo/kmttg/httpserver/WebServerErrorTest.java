package com.tivo.kmttg.httpserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Guards what a client sees when a request handler throws.
 *
 * A handler that fails used to drop the socket, so the browser got a closed
 * connection rather than a status. The connection loop now catches
 * RuntimeException and answers 500 - but only while nothing has gone out yet.
 * Once a status line and a Content-Length are on the wire they cannot be
 * withdrawn: a second response written after them lands inside the first one's
 * body, and the length already promised belongs to the body that was abandoned.
 * A client reading that gets a valid-looking response with garbage in it, which
 * is worse than the dropped socket it replaced.
 *
 * These drive a bare {@link HTTPServer} with handlers that fail on purpose,
 * since no real kmttg handler is supposed to.
 */
public class WebServerErrorTest {

   private HTTPServer server;
   private int port;

   @BeforeEach
   public void startServer() throws IOException {
      try (ServerSocket probe = new ServerSocket(0)) {
         port = probe.getLocalPort();
      }
      server = new HTTPServer(port);
      HTTPServer.VirtualHost host = server.getVirtualHost(null);

      // Fails before writing anything - the case the catch is there for.
      host.addContext("/boom", new HTTPServer.ContextHandler() {
         @Override
         public int serve(HTTPServer.Request req, HTTPServer.Response resp) throws IOException {
            throw new IllegalStateException("handler blew up");
         }
      });
      // Fails part way through a body it already promised the length of.
      host.addContext("/half", new HTTPServer.ContextHandler() {
         @Override
         public int serve(HTTPServer.Request req, HTTPServer.Response resp) throws IOException {
            resp.sendHeaders(200, 500, -1, null, "text/plain", null);
            OutputStream body = resp.getBody();
            body.write("the first half of a file".getBytes(StandardCharsets.UTF_8));
            body.flush();
            throw new IllegalStateException("handler blew up mid-body");
         }
      });
      server.start();
   }

   @AfterEach
   public void stopServer() {
      if (server != null)
         server.stop();
   }

   /** Raw bytes off the socket - HttpURLConnection would hide a second response. */
   private String rawGet(String path) throws IOException {
      try (Socket sock = new Socket("127.0.0.1", port)) {
         sock.setSoTimeout(5000);
         sock.getOutputStream().write(
            ("GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n")
               .getBytes(StandardCharsets.ISO_8859_1));
         sock.getOutputStream().flush();
         ByteArrayOutputStream got = new ByteArrayOutputStream();
         InputStream in = sock.getInputStream();
         byte[] buf = new byte[4096];
         int n;
         try {
            while ((n = in.read(buf)) > 0)
               got.write(buf, 0, n);
         } catch (IOException reset) {
            // Server closed on us; whatever arrived is what the client saw.
         }
         return new String(got.toByteArray(), StandardCharsets.ISO_8859_1);
      }
   }

   private static int countStatusLines(String raw) {
      int count = 0, at = 0;
      while ((at = raw.indexOf("HTTP/1.1 ", at)) >= 0) {
         count++;
         at += 9;
      }
      return count;
   }

   @Test
   public void aHandlerThatFailsBeforeWritingStillGetsA500() throws Exception {
      String raw = rawGet("/boom");

      assertTrue(raw.startsWith("HTTP/1.1 500 "), "expected a 500, got: " + firstLine(raw));
      assertEquals(1, countStatusLines(raw), "expected exactly one response in: " + raw);
      assertTrue(raw.contains("error processing request"), "expected the error body, got: " + raw);
   }

   @Test
   public void aHandlerThatFailsMidBodyDoesNotGetASecondResponse() throws Exception {
      // The 200 and its Content-Length are already gone. Appending a 500 here
      // would put a status line and headers inside the body the client is
      // still reading against that length.
      String raw = rawGet("/half");

      assertTrue(raw.startsWith("HTTP/1.1 200 "), "the first response should stand: " + firstLine(raw));
      assertEquals(1, countStatusLines(raw), "a second response was appended: " + raw);
      assertFalse(raw.contains("error processing request"),
         "an error body must not be written into a response already in flight: " + raw);
      // The client is left with a short body against the promised length, which
      // is exactly the truncated response it can detect.
      assertTrue(raw.contains("Content-Length: 500"), "expected the original length: " + raw);
      assertTrue(raw.endsWith("the first half of a file"), "expected the partial body: " + raw);
   }

   private static String firstLine(String raw) {
      int eol = raw.indexOf("\r\n");
      return eol < 0 ? raw : raw.substring(0, eol);
   }
}
