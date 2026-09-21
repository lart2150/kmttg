package com.tivo.kmttg.httpserver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Drives the multipart/form-data parser, which reads straight off the socket
 * and was wholly untested. What a browser posts has to come back byte for
 * byte, and the framing a client controls - the boundary, the part headers,
 * how the body ends - has to be refused when it is unusable rather than
 * followed wherever it leads.
 *
 * Feeds a whole POST through {@link HTTPServer.Request}, which is how the
 * parser gets its body, so what is being parsed is what arrives off the
 * network.
 */
public class MultipartUploadTest {

   // The shape Chrome and Firefox actually generate
   private static final String BOUNDARY = "----WebKitFormBoundaryrR7Kf2hQ";
   private static final String DELIM = "--" + BOUNDARY;

   @Test
   public void browserForm_yieldsEachPartWhole() throws Exception {
      // A file with CRLFs and NULs in it, and a line that looks like the
      // boundary token but is not the delimiter - a delimiter carries two
      // more leading dashes, and a video file will contain worse
      byte[] file = concat(
         "\r\nMPEG\0\0\r\n".getBytes(StandardCharsets.ISO_8859_1),
         (BOUNDARY + "\r\n").getBytes(StandardCharsets.ISO_8859_1),
         new byte[] { 0x00, (byte)0xff, 0x0d, 0x0a, 0x2d, 0x2d });

      Body body = new Body();
      body.text(DELIM + "\r\n"
         + "Content-Disposition: form-data; name=\"title\"\r\n\r\n"
         + "Bob's Burgers\r\n"
         + DELIM + "\r\n"
         + "Content-Disposition: form-data; name=\"upload\"; filename=\"clip.mp4\"\r\n"
         + "Content-Type: video/mp4\r\n\r\n");
      body.bytes(file);
      body.text("\r\n" + DELIM + "--\r\n");

      HTTPServer.MultipartIterator parts = iterator(body.toByteArray());

      assertTrue(parts.hasNext(), "no first part");
      HTTPServer.MultipartIterator.Part field = parts.next();
      assertEquals("title", field.name);
      assertNull(field.filename, "a plain field has no filename");
      assertEquals("Bob's Burgers", field.getString());

      assertTrue(parts.hasNext(), "no second part");
      HTTPServer.MultipartIterator.Part upload = parts.next();
      assertEquals("upload", upload.name);
      assertEquals("clip.mp4", upload.filename);
      assertEquals("video/mp4", upload.headers.get("Content-Type"));
      assertArrayEquals(file, drain(upload.body), "uploaded bytes came back changed");

      assertFalse(parts.hasNext(), "found a part after the end boundary");
   }

   // A client that drops the connection before its first delimiter is through
   // has sent no part at all, and the parser must say so rather than offer one
   @Test
   public void bodyWithNoUsableDelimiter_isRejected() throws Exception {
      for (byte[] raw : new byte[][] {
            new byte[0],                                          // nothing at all
            DELIM.substring(0, 8).getBytes(StandardCharsets.ISO_8859_1) }) {
         HTTPServer.MultipartIterator parts = iterator(raw);
         RuntimeException e = assertThrows(RuntimeException.class, () -> parts.hasNext(),
            "a body with no delimiter in it was accepted");
         assertTrue(e.getCause() instanceof IOException, "expected the IO failure, got " + e);
      }
   }

   // Each part's headers are read with the same 100 line cap as a request's,
   // so a part cannot make the server buffer header lines without end
   @Test
   public void partWithTooManyHeaders_isRefused() throws Exception {
      StringBuilder headers = new StringBuilder();
      for (int i = 0; i < 200; i++)
         headers.append("X-Pad-").append(i).append(": .\r\n");

      Body body = new Body();
      body.text(DELIM + "\r\n"
         + "Content-Disposition: form-data; name=\"upload\"\r\n"
         + headers + "\r\n"
         + "data\r\n"
         + DELIM + "--\r\n");

      HTTPServer.MultipartIterator parts = iterator(body.toByteArray());
      assertTrue(parts.hasNext());
      assertThrows(RuntimeException.class, () -> parts.next(),
         "a part with 200 header lines was accepted");
   }

   // A header line with no colon is not a header, and the parser has to say so
   // rather than treat the rest of the part as though it were headers
   @Test
   public void partWithAMalformedHeader_isRefused() throws Exception {
      Body body = new Body();
      body.text(DELIM + "\r\n"
         + "Content-Disposition: form-data; name=\"upload\"\r\n"
         + "this line has no colon\r\n\r\n"
         + "data\r\n"
         + DELIM + "--\r\n");

      HTTPServer.MultipartIterator parts = iterator(body.toByteArray());
      assertTrue(parts.hasNext());
      assertThrows(RuntimeException.class, () -> parts.next());
   }

   // The Content-Type is the only place the boundary comes from, and it is the
   // client's to choose - these are the values that make one unusable
   @Test
   public void requestWithoutAUsableBoundary_isRefused() throws Exception {
      String[] rejected = {
         "multipart/form-data",                          // no boundary at all
         "multipart/form-data; boundary=",               // empty boundary
         "application/x-www-form-urlencoded",            // not multipart
         "multipart/form-data; boundary=" + repeat('b', 71) // RFC 1521 caps it at 70
      };
      for (String contentType : rejected) {
         Body body = new Body();
         body.text(DELIM + "--\r\n");
         byte[] raw = post(contentType, body.toByteArray());
         assertThrows(IllegalArgumentException.class,
            () -> new HTTPServer.MultipartIterator(request(raw)), contentType);
      }
   }

   // A boundary of exactly 70 characters is legal, and the parser has to take
   // it - the length check is a range, and an off-by-one would refuse it
   @Test
   public void longestLegalBoundary_isAccepted() throws Exception {
      String boundary = repeat('b', 70);
      Body body = new Body();
      body.text("--" + boundary + "\r\n"
         + "Content-Disposition: form-data; name=\"title\"\r\n\r\n"
         + "x\r\n"
         + "--" + boundary + "--\r\n");

      HTTPServer.MultipartIterator parts = new HTTPServer.MultipartIterator(
         request(post("multipart/form-data; boundary=" + boundary, body.toByteArray())));
      assertTrue(parts.hasNext());
      assertEquals("x", parts.next().getString());
   }

   /* ---- helpers ---- */

   private static HTTPServer.MultipartIterator iterator(byte[] body) throws IOException {
      return new HTTPServer.MultipartIterator(
         request(post("multipart/form-data; boundary=" + BOUNDARY, body)));
   }

   // The parser takes its body from a Request, so build the POST a browser
   // sends and let the request parser hand the body over
   private static HTTPServer.Request request(byte[] raw) throws IOException {
      return new HTTPServer(0).new Request(new ByteArrayInputStream(raw));
   }

   private static byte[] post(String contentType, byte[] body) {
      String head = "POST /upload HTTP/1.1\r\n"
         + "Host: kmttg\r\n"
         + "Content-Type: " + contentType + "\r\n"
         + "Content-Length: " + body.length + "\r\n\r\n";
      return concat(head.getBytes(StandardCharsets.ISO_8859_1), body);
   }

   private static byte[] drain(InputStream in) throws IOException {
      ByteArrayOutputStream got = new ByteArrayOutputStream();
      byte[] buf = new byte[512];
      int n;
      while ((n = in.read(buf)) > 0)
         got.write(buf, 0, n);
      return got.toByteArray();
   }

   private static byte[] concat(byte[]... parts) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      for (byte[] part : parts)
         out.write(part, 0, part.length);
      return out.toByteArray();
   }

   private static String repeat(char c, int times) {
      StringBuilder s = new StringBuilder();
      for (int i = 0; i < times; i++)
         s.append(c);
      return s.toString();
   }

   private static class Body {
      private final ByteArrayOutputStream out = new ByteArrayOutputStream();

      void text(String s) {
         bytes(s.getBytes(StandardCharsets.ISO_8859_1));
      }

      void bytes(byte[] b) {
         out.write(b, 0, b.length);
      }

      byte[] toByteArray() {
         return out.toByteArray();
      }
   }
}
