package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

// Cover for reading the SkipMode anchor out of a transcoded segment.
//
// The fixture is three real transport stream packets - PAT, PMT and the one metadata packet -
// carved out of a live HLS segment for recording rc.16455369. 564 bytes, and it carries the
// whole path: the PMT declares stream type 0x15 so tivolibre assembles that PID, and the PES
// on it is an ID3 tag holding TiVo's PRIV frame.
//
// The expected value is ground truth. It was read off the box and independently matches the
// figure recorded when the offsets were first reverse engineered, so a change here means the
// parse broke, not that the number moved.
public class HlsAnchorTest {

   // startStreamTime for rc.16455369, in the nanoseconds the payload states it in.
   private static final long EXPECTED_NS = 7259888155555L;

   private byte[] fixture(String name) throws Exception {
      try (InputStream is = HlsAnchorTest.class.getResourceAsStream("/fixtures/" + name)) {
         assertNotNull(is, "missing fixture: /fixtures/" + name);
         return is.readAllBytes();
      }
   }

   @Test
   public void readsAnchorFromRealSegment() throws Exception {
      byte[] segment = fixture("hls_id3_timestats.ts");
      assertEquals(564, segment.length, "fixture should be three 188 byte packets");
      assertEquals(EXPECTED_NS, HlsAnchor.startStreamTimeNs(segment));
   }

   // What callers actually use: whole milliseconds, because that is the unit the offsets it
   // corrects are expressed in.
   @Test
   public void roundsToMilliseconds() throws Exception {
      assertEquals(7259888L, HlsAnchor.fromSegment(fixture("hls_id3_timestats.ts")));
   }

   @Test
   public void nothingFromEmptyOrMissingInput() {
      assertNull(HlsAnchor.fromSegment(null));
      assertNull(HlsAnchor.fromSegment(new byte[0]));
   }

   // A segment with no metadata PID at all is the normal case for a stream being consumed by
   // seeking rather than in order, so it has to answer "no" rather than throw.
   @Test
   public void nothingFromAStreamWithoutTheMetadataPid() throws Exception {
      byte[] segment = fixture("hls_id3_timestats.ts");
      // Blank the third packet, the only one carrying the metadata PID.
      byte[] trimmed = new byte[376];
      System.arraycopy(segment, 0, trimmed, 0, 376);
      assertNull(HlsAnchor.fromSegment(trimmed));
   }

   @Test
   public void ignoresAnotherOwnersPrivFrame() {
      byte[] id3 = id3WithPriv("Xbox", (byte) 4, timeStats());
      assertNull(HlsAnchor.fromId3(id3));
   }

   // Below version 4 the record is too short to hold startStreamTime, and reading the bytes
   // that would follow it would invent an anchor out of whatever came next.
   @Test
   public void ignoresPayloadVersionsBeforeFour() {
      assertNull(HlsAnchor.fromId3(id3WithPriv(OWNER, (byte) 3, timeStats())));
   }

   @Test
   public void ignoresATimeStatsTooShortToHoldTheAnchor() {
      // Only the stime pair, which is what a pre-startStreamTime record looks like.
      byte[] shortRecord = new byte[] {
         3, 8, 0, 0, 6, (byte) 0x9b, (byte) 0x89, 0x4f, 0x08, 0x23
      };
      assertNull(HlsAnchor.fromId3(id3WithPriv(OWNER, (byte) 4, shortRecord)));
   }

   @Test
   public void readsAnchorPastAnEarlierTlvRecord() {
      // A STREAM_STATS record ahead of TIME_STATS, which is the real ordering - the parse has
      // to skip by declared length rather than assuming position.
      byte[] padded = concat(new byte[] { 1, 4, 0, 0, 0, 0 }, timeStats());
      assertEquals(EXPECTED_NS, HlsAnchor.fromId3(id3WithPriv(OWNER, (byte) 4, padded)));
   }

   private static final String OWNER = "TiVo";

   // A TIME_STATS record: tag, length, then the stime and startStreamTime pairs.
   private static byte[] timeStats() {
      byte[] r = new byte[18];
      r[0] = TAG_TIME_STATS;
      r[1] = 16;
      putUInt32(r, 2, 1690L);            // stime.hi, a little ahead of the anchor
      putUInt32(r, 6, 2303099427L);
      putUInt32(r, 10, EXPECTED_NS >>> 32);
      putUInt32(r, 14, EXPECTED_NS & 0xffffffffL);
      return r;
   }

   private static final byte TAG_TIME_STATS = 3;

   private static byte[] id3WithPriv(String owner, byte version, byte[] tlv) {
      byte[] ownerBytes = owner.getBytes(StandardCharsets.ISO_8859_1);
      int frameLen = ownerBytes.length + 1 + 1 + tlv.length;
      byte[] out = new byte[10 + 10 + frameLen];
      out[0] = 'I'; out[1] = 'D'; out[2] = '3';
      out[3] = 4; out[4] = 0; out[5] = 0;
      putSynchsafe(out, 6, 10 + frameLen);
      out[10] = 'P'; out[11] = 'R'; out[12] = 'I'; out[13] = 'V';
      putSynchsafe(out, 14, frameLen);
      int p = 20;
      System.arraycopy(ownerBytes, 0, out, p, ownerBytes.length);
      p += ownerBytes.length;
      out[p++] = 0;
      out[p++] = version;
      System.arraycopy(tlv, 0, out, p, tlv.length);
      return out;
   }

   private static void putSynchsafe(byte[] b, int off, int v) {
      b[off] = (byte) ((v >> 21) & 0x7f);
      b[off + 1] = (byte) ((v >> 14) & 0x7f);
      b[off + 2] = (byte) ((v >> 7) & 0x7f);
      b[off + 3] = (byte) (v & 0x7f);
   }

   private static void putUInt32(byte[] b, int off, long v) {
      b[off] = (byte) ((v >> 24) & 0xff);
      b[off + 1] = (byte) ((v >> 16) & 0xff);
      b[off + 2] = (byte) ((v >> 8) & 0xff);
      b[off + 3] = (byte) (v & 0xff);
   }

   private static byte[] concat(byte[] a, byte[] b) {
      byte[] out = new byte[a.length + b.length];
      System.arraycopy(a, 0, out, 0, a.length);
      System.arraycopy(b, 0, out, a.length, b.length);
      return out;
   }
}
