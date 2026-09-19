/*
 * Copyright 2008-Present Kevin Moye <moyekj@yahoo.com>.
 *
 * This file is part of kmttg package.
 *
 * kmttg is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this project.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tivo.kmttg.mux.codec;

// MPEG-2 video (ISO/IEC 13818-2) header parsing, only as far as a container needs:
// dimensions and frame rate for the track header, and picture_coding_type for the
// keyframe flag. Nothing here decodes a picture.
public class Mpeg2Parser {
   public static final int PICTURE_START  = 0x00000100;
   public static final int SEQUENCE_START = 0x000001B3;
   public static final int EXTENSION_START= 0x000001B5;
   public static final int GOP_START      = 0x000001B8;

   public static final int PICTURE_I = 1;
   public static final int PICTURE_P = 2;
   public static final int PICTURE_B = 3;

   // frame_rate_code -> num/den, ISO/IEC 13818-2 table 6-4. Index 0 is forbidden.
   private static final int[][] FRAME_RATE = {
      {0,0}, {24000,1001}, {24,1}, {25,1}, {30000,1001}, {30,1}, {50,1}, {60000,1001}, {60,1}
   };

   public static class SequenceHeader {
      public int width;
      public int height;
      public int frameRateNum;
      public int frameRateDen;
      public int aspectRatioCode;
      public long bitRate;          // bits/sec, 0 when the stream declares variable
      public boolean progressive;
      public byte[] raw;            // sequence header + sequence extension, verbatim

      public double frameRate() {
         return frameRateDen == 0 ? 0 : (double)frameRateNum / frameRateDen;
      }

      public String toString() {
         return String.format("%dx%d @ %.3f fps aspect=%d bitrate=%,d%s",
            width, height, frameRate(), aspectRatioCode, bitRate,
            progressive ? " progressive" : "");
      }
   }

   // Scan for a sequence header and its following sequence extension. Returns null if the
   // buffer holds no complete sequence header; callers feed successive payload units until
   // one turns up, since a recording can start anywhere.
   public static SequenceHeader findSequenceHeader(byte[] d) {
      int seq = findStartCode(d, 0, SEQUENCE_START);
      if (seq < 0 || seq + 12 > d.length) return null;

      SequenceHeader h = new SequenceHeader();
      BitReader r = new BitReader(d, seq + 4);
      h.width           = r.read(12);
      h.height          = r.read(12);
      h.aspectRatioCode = r.read(4);
      int frameRateCode = r.read(4);
      int bitRateValue  = r.read(18);
      r.read(1);                       // marker_bit
      r.read(10);                      // vbv_buffer_size_value
      r.read(1);                       // constrained_parameters_flag
      if (r.read(1) == 1) r.skip(64 * 8);   // load_intra_quantiser_matrix
      if (r.read(1) == 1) r.skip(64 * 8);   // load_non_intra_quantiser_matrix

      if (frameRateCode > 0 && frameRateCode < FRAME_RATE.length) {
         h.frameRateNum = FRAME_RATE[frameRateCode][0];
         h.frameRateDen = FRAME_RATE[frameRateCode][1];
      }
      h.bitRate = bitRateValue == 0x3FFFF ? 0 : (long)bitRateValue * 400;

      // The sequence extension carries the high bits of the dimensions. Without it this is
      // MPEG-1, and the 12-bit values above already stand alone.
      int ext = findStartCode(d, seq + 4, EXTENSION_START);
      int end = seq;
      if (ext >= 0 && ext + 10 <= d.length) {
         BitReader e = new BitReader(d, ext + 4);
         if (e.read(4) == 1) {         // extension_start_code_identifier: sequence extension
            e.read(8);                 // profile_and_level_indication
            h.progressive = e.read(1) == 1;
            e.read(2);                 // chroma_format
            h.width  |= e.read(2) << 12;
            h.height |= e.read(2) << 12;
            long bitRateExt = e.read(12);
            if (h.bitRate > 0 && bitRateExt > 0) h.bitRate += bitRateExt * 400 * 262144L;
            end = ext + 10;
         }
      }
      if (end > seq) {
         h.raw = new byte[end - seq];
         System.arraycopy(d, seq, h.raw, 0, h.raw.length);
      }
      return h;
   }

   // picture_coding_type of the first picture in this payload unit, or -1 if there is none.
   // A video payload unit holds one picture, so this is the unit's type.
   public static int pictureCodingType(byte[] d) {
      int pic = findStartCode(d, 0, PICTURE_START);
      if (pic < 0 || pic + 6 > d.length) return -1;
      BitReader r = new BitReader(d, pic + 4);
      r.read(10);                      // temporal_reference
      return r.read(3);
   }

   public static boolean isKeyframe(byte[] d) {
      return pictureCodingType(d) == PICTURE_I;
   }

   // Index of the start code, or -1. Start codes are byte aligned and prefixed 00 00 01.
   private static int findStartCode(byte[] d, int from, int code) {
      int want = code & 0xFF;
      for (int i = Math.max(from, 0); i + 3 < d.length; i++) {
         if (d[i] == 0 && d[i+1] == 0 && (d[i+2] & 0xFF) == 1 && (d[i+3] & 0xFF) == want) {
            return i;
         }
      }
      return -1;
   }

   private static class BitReader {
      private final byte[] d;
      private int bit;

      BitReader(byte[] d, int byteOffset) {
         this.d = d;
         this.bit = byteOffset * 8;
      }

      int read(int n) {
         int v = 0;
         for (int i = 0; i < n; i++) {
            int b = bit >> 3;
            if (b >= d.length) return v << (n - i);
            v = (v << 1) | ((d[b] >> (7 - (bit & 7))) & 1);
            bit++;
         }
         return v;
      }

      void skip(int n) { bit += n; }
   }
}
