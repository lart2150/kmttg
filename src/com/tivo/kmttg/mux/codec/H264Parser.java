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

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

// H.264 (ISO/IEC 14496-10) parsing, only as far as a container needs. Three jobs:
// read the SPS for dimensions and aspect, build the avcC record a Matroska CodecPrivate
// wants, and convert Annex-B samples to the length prefixed form. Nothing here decodes.
//
// A transport stream carries Annex-B, with NAL units separated by start codes. Matroska has
// no Annex-B form at all, so that conversion is mandatory rather than an optimisation.
public class H264Parser {
   public static final int NAL_SLICE     = 1;
   public static final int NAL_IDR       = 5;
   public static final int NAL_SEI       = 6;
   public static final int NAL_SPS       = 7;
   public static final int NAL_PPS       = 8;
   public static final int NAL_AUD       = 9;
   public static final int NAL_FILLER    = 12;

   // Table E-1. Index is aspect_ratio_idc; 255 means the SPS carries the ratio itself.
   private static final int[][] SAR = {
      {0,0}, {1,1}, {12,11}, {10,11}, {16,11}, {40,33}, {24,11}, {20,11}, {32,11},
      {80,33}, {18,11}, {15,11}, {64,33}, {160,99}, {4,3}, {3,2}, {2,1}
   };

   public static class SequenceParameterSet {
      public int width;
      public int height;
      public int sarWidth = 1;
      public int sarHeight = 1;
      public int profileIdc;
      public int levelIdc;
      // frame_mbs_only_flag. False means the stream may code fields, which on a broadcast
      // means it is interlaced; the field order itself is only in a pic_struct SEI.
      public boolean frameMbsOnly = true;
      public byte[] raw;          // the NAL as it appeared, escape bytes included

      // Display size for a non square pixel aspect. 720x480 at 40:33 is NTSC widescreen;
      // ignoring the ratio squashes it to 3:2.
      public int displayWidth() {
         return sarWidth == sarHeight ? width : (int)Math.round(width * (double)sarWidth / sarHeight);
      }

      public int displayHeight() {
         return height;
      }

      public String toString() {
         return String.format("h264 profile=%d level=%d %dx%d sar=%d:%d display=%dx%d",
            profileIdc, levelIdc, width, height, sarWidth, sarHeight,
            displayWidth(), displayHeight());
      }
   }

   public static class NalUnit {
      public int type;
      public int offset;          // start of the NAL payload, past the start code
      public int length;
   }

   // Split an Annex-B buffer into NAL units. Start codes are 00 00 01 or 00 00 00 01.
   public static List<NalUnit> findNals(byte[] d) {
      List<NalUnit> out = new ArrayList<NalUnit>();
      int i = 0;
      int start = -1;
      while (i + 2 < d.length) {
         if (d[i] == 0 && d[i+1] == 0 && (d[i+2] & 0xFF) == 1) {
            if (start >= 0) close(out, d, start, i);
            i += 3;
            start = i;
         } else {
            i++;
         }
      }
      if (start >= 0) close(out, d, start, d.length);
      return out;
   }

   private static void close(List<NalUnit> out, byte[] d, int start, int end) {
      // A four byte start code is a three byte one with a leading zero, so trim it back off
      // the previous unit rather than treating it as payload.
      while (end > start && d[end-1] == 0) end--;
      if (end <= start) return;
      NalUnit n = new NalUnit();
      n.type = d[start] & 0x1F;
      n.offset = start;
      n.length = end - start;
      out.add(n);
   }

   // A keyframe is an access unit carrying an IDR slice, or failing that an I slice.
   //
   // The second half is not a nicety. Broadcast H.264 commonly contains no IDR at all: the
   // TiVo samples here have zero type 5 NALs across the whole recording and signal random
   // access with an I slice preceded by a repeated SPS/PPS instead. Keying only on IDR finds
   // nothing, which costs every Cue in the file and with it all seeking.
   public static boolean isKeyframe(byte[] d) {
      return isKeyframe(d, findNals(d));
   }

   // The list overload exists so the mux hot path can scan a payload for start codes once and
   // use the result for both the keyframe verdict and the length prefixed rewrite, instead of
   // walking every byte of every video payload twice.
   public static boolean isKeyframe(byte[] d, List<NalUnit> nals) {
      for (NalUnit n : nals) {
         if (n.type == NAL_IDR) return true;
         if (n.type == NAL_SLICE && sliceType(d, n) == SLICE_I) return true;
      }
      return false;
   }

   public static final int SLICE_I = 2;

   // slice_type from the slice header. Only the first two fields are needed, so this reads a
   // few bytes rather than the whole NAL. Values 5..9 repeat 0..4 for "all slices this type".
   static int sliceType(byte[] d, NalUnit n) {
      byte[] rbsp = unescape(d, n.offset, Math.min(n.length, 16));
      if (rbsp.length < 2) return -1;
      BitReader r = new BitReader(rbsp);
      r.read(8);                               // nal header
      r.ue();                                  // first_mb_in_slice
      int t = r.ue();
      return t >= 5 ? t - 5 : t;
   }

   public static SequenceParameterSet parseSps(byte[] d, int offset, int length) {
      byte[] rbsp = unescape(d, offset, length);
      if (rbsp.length < 4) return null;

      SequenceParameterSet sps = new SequenceParameterSet();
      sps.raw = new byte[length];
      System.arraycopy(d, offset, sps.raw, 0, length);

      BitReader r = new BitReader(rbsp);
      r.read(8);                               // nal header
      sps.profileIdc = r.read(8);
      r.read(8);                               // constraint flags + reserved
      sps.levelIdc = r.read(8);
      r.ue();                                  // seq_parameter_set_id

      int chromaFormatIdc = 1;
      if (isHighProfile(sps.profileIdc)) {
         chromaFormatIdc = r.ue();
         if (chromaFormatIdc == 3) r.read(1);  // separate_colour_plane_flag
         r.ue();                               // bit_depth_luma_minus8
         r.ue();                               // bit_depth_chroma_minus8
         r.read(1);                            // qpprime_y_zero_transform_bypass_flag
         if (r.read(1) == 1) {                 // seq_scaling_matrix_present_flag
            int lists = chromaFormatIdc != 3 ? 8 : 12;
            for (int i = 0; i < lists; i++) {
               if (r.read(1) == 1) skipScalingList(r, i < 6 ? 16 : 64);
            }
         }
      }

      r.ue();                                  // log2_max_frame_num_minus4
      int pocType = r.ue();
      if (pocType == 0) {
         r.ue();                               // log2_max_pic_order_cnt_lsb_minus4
      } else if (pocType == 1) {
         r.read(1);                            // delta_pic_order_always_zero_flag
         r.se();                               // offset_for_non_ref_pic
         r.se();                               // offset_for_top_to_bottom_field
         int n = r.ue();
         for (int i = 0; i < n; i++) r.se();   // offset_for_ref_frame
      }
      r.ue();                                  // max_num_ref_frames
      r.read(1);                               // gaps_in_frame_num_value_allowed_flag

      int widthMbs  = r.ue() + 1;
      int heightMap = r.ue() + 1;
      int frameMbsOnly = r.read(1);
      sps.frameMbsOnly = frameMbsOnly == 1;
      if (frameMbsOnly == 0) r.read(1);        // mb_adaptive_frame_field_flag
      r.read(1);                               // direct_8x8_inference_flag

      int cropLeft = 0, cropRight = 0, cropTop = 0, cropBottom = 0;
      if (r.read(1) == 1) {                    // frame_cropping_flag
         cropLeft   = r.ue();
         cropRight  = r.ue();
         cropTop    = r.ue();
         cropBottom = r.ue();
      }

      // Crop offsets are in chroma samples, so the unit depends on the chroma format and,
      // for the vertical axis, on whether the stream is frame-only.
      int subWidthC  = (chromaFormatIdc == 1 || chromaFormatIdc == 2) ? 2 : 1;
      int subHeightC = (chromaFormatIdc == 1) ? 2 : 1;
      int cropUnitX  = (chromaFormatIdc == 0) ? 1 : subWidthC;
      int cropUnitY  = ((chromaFormatIdc == 0) ? 1 : subHeightC) * (2 - frameMbsOnly);

      sps.width  = widthMbs * 16 - cropUnitX * (cropLeft + cropRight);
      sps.height = (2 - frameMbsOnly) * heightMap * 16 - cropUnitY * (cropTop + cropBottom);

      if (r.read(1) == 1) {                    // vui_parameters_present_flag
         if (r.read(1) == 1) {                 // aspect_ratio_info_present_flag
            int idc = r.read(8);
            if (idc == 255) {
               sps.sarWidth  = r.read(16);
               sps.sarHeight = r.read(16);
            } else if (idc > 0 && idc < SAR.length) {
               sps.sarWidth  = SAR[idc][0];
               sps.sarHeight = SAR[idc][1];
            }
         }
      }
      if (sps.sarWidth <= 0 || sps.sarHeight <= 0) {
         sps.sarWidth = 1;
         sps.sarHeight = 1;
      }
      return sps.width > 0 && sps.height > 0 ? sps : null;
   }

   private static boolean isHighProfile(int p) {
      return p == 100 || p == 110 || p == 122 || p == 244 || p == 44 || p == 83
          || p == 86  || p == 118 || p == 128 || p == 138 || p == 139 || p == 134 || p == 135;
   }

   private static void skipScalingList(BitReader r, int size) {
      int last = 8, next = 8;
      for (int i = 0; i < size; i++) {
         if (next != 0) {
            next = (last + r.se() + 256) % 256;
         }
         last = next == 0 ? last : next;
      }
   }

   // Remove emulation prevention bytes: a 0x03 inserted after any 00 00 so the payload can
   // never contain a start code. Parsing the SPS without doing this reads garbage from the
   // first such sequence onward.
   public static byte[] unescape(byte[] d, int offset, int length) {
      ByteArrayOutputStream out = new ByteArrayOutputStream(length);
      int zeros = 0;
      for (int i = offset; i < offset + length && i < d.length; i++) {
         int b = d[i] & 0xFF;
         if (zeros == 2 && b == 0x03) {
            zeros = 0;
            continue;
         }
         out.write(b);
         zeros = (b == 0) ? zeros + 1 : 0;
      }
      return out.toByteArray();
   }

   // AVCDecoderConfigurationRecord, ISO/IEC 14496-15. This is the Matroska CodecPrivate for
   // V_MPEG4/ISO/AVC, and the MP4 avcC box when that arrives.
   public static byte[] buildAvcC(List<byte[]> spsList, List<byte[]> ppsList) {
      if (spsList.isEmpty() || ppsList.isEmpty()) return null;
      byte[] sps = spsList.get(0);
      if (sps.length < 4) return null;

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      out.write(1);                            // configurationVersion
      out.write(sps[1] & 0xFF);                // AVCProfileIndication
      out.write(sps[2] & 0xFF);                // profile_compatibility
      out.write(sps[3] & 0xFF);                // AVCLevelIndication
      out.write(0xFF);                         // 6 bits reserved | lengthSizeMinusOne = 3
      out.write(0xE0 | (spsList.size() & 0x1F));
      for (byte[] s : spsList) {
         out.write((s.length >> 8) & 0xFF);
         out.write(s.length & 0xFF);
         out.write(s, 0, s.length);
      }
      out.write(ppsList.size() & 0xFF);
      for (byte[] p : ppsList) {
         out.write((p.length >> 8) & 0xFF);
         out.write(p.length & 0xFF);
         out.write(p, 0, p.length);
      }
      return out.toByteArray();
   }

   // Annex-B to length prefixed, with a four byte length to match lengthSizeMinusOne = 3.
   // Parameter sets and access unit delimiters are dropped: the first two live in avcC and
   // the third has no meaning once samples are framed by the container.
   public static byte[] toLengthPrefixed(byte[] d) {
      return toLengthPrefixed(d, findNals(d));
   }

   public static byte[] toLengthPrefixed(byte[] d, List<NalUnit> nals) {
      ByteArrayOutputStream out = new ByteArrayOutputStream(d.length);
      for (NalUnit n : nals) {
         if (n.type == NAL_SPS || n.type == NAL_PPS || n.type == NAL_AUD
               || n.type == NAL_FILLER) {
            continue;
         }
         out.write((n.length >> 24) & 0xFF);
         out.write((n.length >> 16) & 0xFF);
         out.write((n.length >> 8) & 0xFF);
         out.write(n.length & 0xFF);
         out.write(d, n.offset, n.length);
      }
      return out.toByteArray();
   }

   // Exp-Golomb reader. The SPS is a bit stream, and every field after a variable length one
   // shifts, so this cannot be done with byte offsets.
   private static class BitReader {
      private final byte[] d;
      private int bit;

      BitReader(byte[] d) { this.d = d; }

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

      // Unsigned exp-Golomb: count leading zeros, then read that many bits.
      int ue() {
         int zeros = 0;
         // Capped at 31, not 32: a Java shift is taken mod 32, so (1 << 32) - 1 evaluates to
         // 0 and a damaged stream's long zero run read back as a small plausible value. At 31
         // the result is large or negative instead, which parseSps rejects on width/height.
         while (read(1) == 0 && zeros < 31 && (bit >> 3) < d.length) zeros++;
         if (zeros == 0) return 0;
         return (1 << zeros) - 1 + read(zeros);
      }

      int se() {
         int k = ue();
         return (k & 1) == 1 ? (k + 1) / 2 : -(k / 2);
      }
   }
}
