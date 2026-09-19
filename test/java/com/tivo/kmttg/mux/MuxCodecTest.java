package com.tivo.kmttg.mux;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tivo.kmttg.mux.codec.Ac3Parser;
import com.tivo.kmttg.mux.codec.Mpeg2Parser;
import com.tivo.kmttg.mux.mkv.EbmlWriter;

// Unit cover for the parts of the muxer that are pure functions over bytes. The corpus
// exercises these far harder than any fixture can, but it cannot tell you WHICH field was
// misread when a count comes out wrong, and it cannot run without 32 GB of recordings.
public class MuxCodecTest {

   // ---- Timestamps ---------------------------------------------------------------------

   @Test
   public void formatsStreamPositionsAsClockTime() {
      // The discontinuity notes are read against a player's seek bar, so these have to be the
      // positions a player would show rather than raw milliseconds.
      assertEquals("00:09:51.328", TimeBase.hms(591328));
      assertEquals("00:19:34.544", TimeBase.hms(1174544));
      assertEquals("00:00:00.000", TimeBase.hms(0));
      // Past an hour the hours field has to carry, not wrap - a three hour recording is the
      // case the 10 GB run covered.
      assertEquals("02:55:00.001", TimeBase.hms(10500001L));
      // Not expected from a locked time base, but garbage beats a bare minus sign in a log.
      assertEquals("-00:00:01.500", TimeBase.hms(-1500));
   }

   // ---- MPEG-2 -------------------------------------------------------------------------

   // sequence_header_code, then 1920 (12b), 1080 (12b), aspect 3, frame_rate_code 4,
   // bit_rate_value all ones (variable), marker, and three zeroed flags.
   private static final byte[] SEQ_1080I = {
      0, 0, 1, (byte)0xB3, 0x78, 0x04, 0x38, 0x34, (byte)0xFF, (byte)0xFF, (byte)0xF0, 0
   };

   private static byte[] pictureHeader(int codingType) {
      // temporal_reference 0 (10b) then picture_coding_type (3b), left aligned in byte 1
      return new byte[]{0, 0, 1, 0x00, 0x00, (byte)(codingType << 3), 0, 0};
   }

   @Test
   void sequenceHeaderGivesDimensionsAndFrameRate() {
      Mpeg2Parser.SequenceHeader h = Mpeg2Parser.findSequenceHeader(SEQ_1080I);
      assertNotNull(h);
      assertEquals(1920, h.width);
      assertEquals(1080, h.height);
      assertEquals(3, h.aspectRatioCode);          // 16:9 display aspect
      assertEquals(30000, h.frameRateNum);
      assertEquals(1001, h.frameRateDen);
      assertEquals(29.97, h.frameRate(), 0.001);
      // 0x3ffff means the stream declines to state a bitrate, which must not become a number
      assertEquals(0, h.bitRate);
   }

   @Test
   void sequenceHeaderIsFoundAtAnOffset() {
      // A recording starts wherever it starts, so the header is rarely at byte zero.
      byte[] padded = new byte[SEQ_1080I.length + 7];
      System.arraycopy(SEQ_1080I, 0, padded, 7, SEQ_1080I.length);
      Mpeg2Parser.SequenceHeader h = Mpeg2Parser.findSequenceHeader(padded);
      assertNotNull(h);
      assertEquals(1920, h.width);
   }

   @Test
   void noSequenceHeaderReturnsNullRatherThanGuessing() {
      assertNull(Mpeg2Parser.findSequenceHeader(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}));
      assertNull(Mpeg2Parser.findSequenceHeader(new byte[0]));
   }

   @Test
   void pictureCodingTypeDrivesTheKeyframeFlag() {
      assertEquals(Mpeg2Parser.PICTURE_I, Mpeg2Parser.pictureCodingType(pictureHeader(1)));
      assertEquals(Mpeg2Parser.PICTURE_P, Mpeg2Parser.pictureCodingType(pictureHeader(2)));
      assertEquals(Mpeg2Parser.PICTURE_B, Mpeg2Parser.pictureCodingType(pictureHeader(3)));
      assertTrue(Mpeg2Parser.isKeyframe(pictureHeader(1)));
      // Only I pictures may be marked keyframe; a P marked key sends a seek somewhere broken
      assertTrue(! Mpeg2Parser.isKeyframe(pictureHeader(2)));
      assertTrue(! Mpeg2Parser.isKeyframe(pictureHeader(3)));
      assertEquals(-1, Mpeg2Parser.pictureCodingType(new byte[]{9, 9, 9, 9}));
   }

   // ---- AC-3 ---------------------------------------------------------------------------

   // syncword, crc1, then fscod=0 (48 kHz), frmsizecod, bsid=8, bsmod=0, acmod, lfe.
   private static byte[] ac3Frame(int frmsizecod, int acmod, boolean lfe, int length) {
      byte[] f = new byte[length];
      f[0] = 0x0B; f[1] = 0x77;
      f[2] = 0; f[3] = 0;
      f[4] = (byte)((0 << 6) | (frmsizecod & 0x3F));          // fscod=0, frmsizecod
      // bsid 5b = 8, bsmod 3b = 0
      f[5] = (byte)(8 << 3);
      // lfeon does not sit at a fixed bit: what precedes it depends on acmod. 3/2 carries
      // cmixlev and surmixlev ahead of it, plain stereo carries dsurmod. Build it positionally
      // rather than assuming, which is the mistake this fixture made first time round.
      int bits = acmod, n = 3;
      if ((acmod & 0x1) != 0 && acmod != 0x1) { bits <<= 2; n += 2; }   // cmixlev
      if ((acmod & 0x4) != 0)                 { bits <<= 2; n += 2; }   // surmixlev
      if (acmod == 0x2)                       { bits <<= 2; n += 2; }   // dsurmod
      bits = (bits << 1) | (lfe ? 1 : 0); n += 1;
      f[6] = (byte)(bits << (8 - n));
      return f;
   }

   @Test
   void ac3StereoAt384kSizesTo1536Bytes() {
      // 384 kb/s is BITRATE index 14, so frmsizecod 28. At 48 kHz the frame is bitrate*4.
      Ac3Parser.SyncFrame f = Ac3Parser.parseAt(ac3Frame(28, 2, false, 1536), 0);
      assertNotNull(f);
      assertEquals(48000, f.sampleRate);
      assertEquals(384, f.bitRate);
      assertEquals(2, f.channels);
      assertEquals(1536, f.length);
   }

   @Test
   void ac3FiveOneCountsTheLfe() {
      // acmod 7 is 3/2, five full range channels, plus LFE makes six.
      Ac3Parser.SyncFrame f = Ac3Parser.parseAt(ac3Frame(28, 7, true, 1536), 0);
      assertNotNull(f);
      assertEquals(6, f.channels);
      assertTrue(f.lfeon);
   }

   @Test
   void ac3RejectsNonFrames() {
      assertNull(Ac3Parser.parseAt(new byte[]{0, 1, 2, 3, 4, 5, 6, 7}, 0));
      assertNull(Ac3Parser.parseAt(new byte[]{0x0B}, 0));
   }

   @Test
   void splitYieldsEveryWholeFrameAndRefusesATruncatedTail() {
      // Two whole frames plus 500 bytes of a third: exactly the shape a unit cut short by a
      // break has, and the third must not be emitted as if it were a sample.
      byte[] buf = new byte[1536 * 2 + 500];
      System.arraycopy(ac3Frame(28, 2, false, 1536), 0, buf, 0, 1536);
      System.arraycopy(ac3Frame(28, 2, false, 1536), 0, buf, 1536, 1536);
      System.arraycopy(ac3Frame(28, 2, false, 500), 0, buf, 3072, 500);

      List<Ac3Parser.SyncFrame> frames = Ac3Parser.split(buf);
      assertEquals(2, frames.size());
      assertEquals(0, frames.get(0).offset);
      assertEquals(1536, frames.get(1).offset);
   }

   // ---- EBML ---------------------------------------------------------------------------

   @Test
   void vintSizeWidensAtEachBoundary() {
      // The all-ones value at each width is reserved as "unknown size", so a real length of
      // that value has to widen instead. Off by one here corrupts every element after it.
      assertArrayEquals(new byte[]{(byte)0x80}, sized(0));
      assertArrayEquals(new byte[]{(byte)0x81}, sized(1));
      assertArrayEquals(new byte[]{(byte)0xFE}, sized(126));
      assertEquals(2, sized(127).length, "127 is the 1 byte reserved value, so it must widen");
      assertEquals(2, sized(128).length);
      assertEquals(3, sized(16383).length, "16383 is the 2 byte reserved value");
   }

   private static byte[] sized(long v) {
      EbmlWriter w = new EbmlWriter();
      w.writeSize(v);
      return w.toByteArray();
   }

   @Test
   void uIntElementUsesTheNarrowestEncoding() {
      EbmlWriter w = new EbmlWriter();
      w.writeUInt(0x9FL, 6);                      // Channels = 6
      assertArrayEquals(new byte[]{(byte)0x9F, (byte)0x81, 6}, w.toByteArray());
   }

   @Test
   void fixedWidthSizeIsPatchableToTheSameWidth() {
      // The Segment size is written before its length is known and patched at close, so both
      // encodings must occupy exactly the reserved width.
      assertEquals(8, EbmlWriter.sizeFixedBytes(0, 8).length);
      assertEquals(8, EbmlWriter.sizeFixedBytes(1551130944L, 8).length);
   }

   // ---- TimeBase -----------------------------------------------------------------------

   @Test
   void rebaseIsGlobalSoTheInterStreamOffsetSurvives() {
      // The reference recording has audio leading video by 49,230 ticks. Rebasing per stream
      // would zero both and desynchronise the file; only the global minimum preserves it.
      TimeBase t = new TimeBase();
      long audio = 8240316184L;
      long video = 8240365414L;
      t.lock(Math.min(audio, video));
      assertEquals(0, t.toMillis(audio));
      assertEquals(547, t.toMillis(video));
   }

   @Test
   void ptsUnwrapsAcrossThe33BitBoundary() {
      TimeBase t = new TimeBase();
      long nearTop = (1L << 33) - 1000;
      assertEquals(nearTop, t.unwrap(0x31, nearTop));
      // Wrapping to a small value must keep going up, not jump back 26 hours.
      assertEquals((1L << 33) + 500, t.unwrap(0x31, 500));
      assertEquals((1L << 33) + 9000, t.unwrap(0x31, 9000));
   }

   @Test
   void unwrapIsPerPidSoOneStreamDoesNotDragAnother() {
      TimeBase t = new TimeBase();
      t.unwrap(0x31, (1L << 33) - 1000);
      t.unwrap(0x31, 500);                        // video wraps
      assertEquals(4000, t.unwrap(0x34, 4000));   // audio has not, and must not be shifted
   }

   @Test
   void bPicturesStraddlingTheWrapDoNotDoubleCountIt() {
      // Decode order across the 33-bit wrap: P B B I B B P, where the B pictures after the
      // post-wrap I are displayed BEFORE it and so arrive as a large forward jump. Treating
      // one as a new cycle used to add 2^33 and move the reference up, so every later
      // picture counted the wrap again and the video ran 26.5 hours ahead of the audio.
      TimeBase t = new TimeBase();
      long R = 1L << 33;
      long[] raw = { R - 3000, R - 2000, R - 1000, 1000, R - 500, R - 400, 2000 };
      long[] want = { R - 3000, R - 2000, R - 1000, R + 1000, R - 500, R - 400, R + 2000 };
      for (int i = 0; i < raw.length; i++) {
         assertEquals(want[i], t.unwrap(0x31, raw[i]), "sample " + i);
      }
   }

   @Test
   void aRealWrapStillCounts() {
      TimeBase t = new TimeBase();
      long R = 1L << 33;
      assertEquals(R - 100, t.unwrap(0x31, R - 100));
      assertEquals(R + 50, t.unwrap(0x31, 50), "a genuine wrap must still advance");
      assertEquals(R + 9000, t.unwrap(0x31, 9000));
   }

   @Test
   void negativeRebaseIsClampedAndCounted() {
      // An open GOP can put a B picture ahead of the locked base. Matroska cluster timestamps
      // are unsigned, so it clamps - but silently would hide a mislocked base.
      TimeBase t = new TimeBase();
      t.lock(1000);
      assertEquals(0, t.getClamped());
      assertEquals(0, t.toMillis(900));
      assertEquals(1, t.getClamped());
   }
}
