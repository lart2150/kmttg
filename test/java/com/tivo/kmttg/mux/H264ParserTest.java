package com.tivo.kmttg.mux;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tivo.kmttg.mux.codec.H264Parser;

// H.264 parsing cover. The SPS below is lifted verbatim from Cradle 2 the Grave, so this is
// a real broadcast parameter set rather than a hand rolled one: 720x480 Main at SAR 40:33,
// which is NTSC 16:9 anamorphic.
public class H264ParserTest {

   private static final byte[] REAL_SPS = {
      (byte)0x67, (byte)0x4D, (byte)0x40, (byte)0x1E, (byte)0x9A, (byte)0x52, (byte)0x81,
      (byte)0x68, (byte)0xF2, (byte)0xC1, (byte)0x54, (byte)0x83, (byte)0x03, (byte)0x03,
      (byte)0x20, (byte)0x00, (byte)0x00, (byte)0x7D, (byte)0x20, (byte)0x00, (byte)0x1D,
      (byte)0x4C, (byte)0x12, (byte)0x80
   };

   @Test
   void realSpsGivesDimensionsAndAnamorphicAspect() {
      H264Parser.SequenceParameterSet sps =
         H264Parser.parseSps(REAL_SPS, 0, REAL_SPS.length);
      assertNotNull(sps);
      assertEquals(720, sps.width);
      assertEquals(480, sps.height);
      assertEquals(77, sps.profileIdc, "Main profile");
      // 40:33 is the NTSC widescreen pixel aspect. Reading it as square squashes the picture.
      assertEquals(40, sps.sarWidth);
      assertEquals(33, sps.sarHeight);
   }

   @Test
   void emulationPreventionBytesAreRemoved() {
      // 00 00 03 in the stream means the 03 was inserted so the payload could not contain a
      // start code. Leaving it in makes every field after it read as garbage.
      byte[] escaped  = {0x00, 0x00, 0x03, 0x01, 0x00, 0x00, 0x03, 0x02};
      byte[] expected = {0x00, 0x00, 0x01, 0x00, 0x00, 0x02};
      assertArrayEquals(expected, H264Parser.unescape(escaped, 0, escaped.length));
   }

   @Test
   void unescapeLeavesInnocentThreesAlone() {
      byte[] d = {0x00, 0x03, 0x01, 0x03, 0x00, 0x00, 0x01};
      assertArrayEquals(d, H264Parser.unescape(d, 0, d.length));
   }

   @Test
   void nalsAreFoundAcrossBothStartCodeLengths() {
      byte[] d = {
         0, 0, 1, 0x67, 0x11,             // 3 byte start code, SPS
         0, 0, 0, 1, 0x68, 0x22,          // 4 byte start code, PPS
         0, 0, 1, 0x65, 0x33, 0x44        // IDR slice
      };
      List<H264Parser.NalUnit> nals = H264Parser.findNals(d);
      assertEquals(3, nals.size());
      assertEquals(H264Parser.NAL_SPS, nals.get(0).type);
      assertEquals(H264Parser.NAL_PPS, nals.get(1).type);
      assertEquals(H264Parser.NAL_IDR, nals.get(2).type);
      // The leading zero of a 4 byte start code belongs to the code, not to the NAL before it
      assertEquals(2, nals.get(0).length);
   }

   @Test
   void idrIsAKeyframe() {
      byte[] d = {0, 0, 1, 0x65, 0x33, 0x44};
      assertTrue(H264Parser.isKeyframe(d));
   }

   @Test
   void anISliceIsAKeyframeEvenWithNoIdrPresent() {
      // This is the case that matters on real broadcast streams: the TiVo H.264 samples carry
      // zero IDR NALs in an entire recording. Keying only on IDR yields no Cues and no seeking.
      // slice header: first_mb_in_slice ue=0 (bit 1), slice_type ue=7 (I, all slices).
      // 0 -> '1', 7 -> '0001000'. Together '10001000' = 0x88.
      byte[] iSlice = {0, 0, 1, 0x41, (byte)0x88};
      assertTrue(H264Parser.isKeyframe(iSlice), "an I slice must count as a random access point");

      // slice_type ue=5 is P-all: '1' then '00110' -> '10011 0...' = 0x98
      byte[] pSlice = {0, 0, 1, 0x41, (byte)0x98};
      assertFalse(H264Parser.isKeyframe(pSlice), "a P slice must not be marked keyframe");
   }

   @Test
   void avcCCarriesTheParameterSetsAndAFourByteLengthSize() {
      byte[] sps = {0x67, 0x4D, 0x40, 0x1E, 0x11};
      byte[] pps = {0x68, (byte)0xEE, 0x3C, (byte)0x80};
      byte[] avcC = H264Parser.buildAvcC(Arrays.asList(sps), Arrays.asList(pps));
      assertNotNull(avcC);

      assertEquals(1,    avcC[0] & 0xFF, "configurationVersion");
      assertEquals(0x4D, avcC[1] & 0xFF, "AVCProfileIndication from sps[1]");
      assertEquals(0x40, avcC[2] & 0xFF, "profile_compatibility from sps[2]");
      assertEquals(0x1E, avcC[3] & 0xFF, "AVCLevelIndication from sps[3]");
      // lengthSizeMinusOne must be 3: samples are written with 4 byte lengths below.
      assertEquals(0xFF, avcC[4] & 0xFF);
      assertEquals(0xE1, avcC[5] & 0xFF, "one SPS");
      assertEquals(sps.length, ((avcC[6] & 0xFF) << 8) | (avcC[7] & 0xFF));
   }

   @Test
   void avcCNeedsBothParameterSets() {
      byte[] sps = {0x67, 0x4D, 0x40, 0x1E, 0x11};
      assertEquals(null, H264Parser.buildAvcC(Arrays.<byte[]>asList(), Arrays.asList(sps)));
      assertEquals(null, H264Parser.buildAvcC(Arrays.asList(sps), Arrays.<byte[]>asList()));
   }

   @Test
   void lengthPrefixedConversionDropsParameterSetsAndDelimiters() {
      byte[] d = {
         0, 0, 1, 0x09, 0x10,             // AUD  - dropped, meaningless once framed
         0, 0, 1, 0x67, 0x11, 0x22,       // SPS  - dropped, lives in avcC
         0, 0, 1, 0x68, 0x33,             // PPS  - dropped, lives in avcC
         0, 0, 1, 0x65, 0x44, 0x55, 0x66  // IDR  - kept
      };
      byte[] out = H264Parser.toLengthPrefixed(d);
      // One NAL of 4 bytes, behind a 4 byte big endian length
      assertEquals(8, out.length);
      assertEquals(0, out[0]);
      assertEquals(0, out[1]);
      assertEquals(0, out[2]);
      assertEquals(4, out[3]);
      assertEquals(0x65, out[4] & 0xFF);
      assertEquals(0x66, out[7] & 0xFF);
   }

   @Test
   void displayWidthScalesByThePixelAspect() {
      H264Parser.SequenceParameterSet sps =
         H264Parser.parseSps(REAL_SPS, 0, REAL_SPS.length);
      assertNotNull(sps);
      // 720 * 40/33 is 872.7. The muxer writes a reduced ratio rather than this rounding,
      // but the helper should still describe the intended display width.
      assertEquals(873, sps.displayWidth());
      assertEquals(480, sps.displayHeight());
   }
}
