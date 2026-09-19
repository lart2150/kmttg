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

import java.util.ArrayList;
import java.util.List;

// AC-3 (ATSC A/52) syncframe parsing. One PES payload unit carries several syncframes
// sharing a single PTS, and a Matroska block or MP4 sample is one syncframe, so splitting
// them is this class's reason to exist. See the handoff: the library hands over units.
public class Ac3Parser {
   public static final int SYNCWORD = 0x0B77;
   public static final int SAMPLES_PER_FRAME = 1536;

   private static final int[] SAMPLE_RATE = {48000, 44100, 32000, 0};

   // Nominal bitrates in kb/s, indexed by frmsizecod >> 1. A/52 table 5.18.
   private static final int[] BITRATE = {
      32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384, 448, 512, 576, 640
   };

   // Channel count per acmod, before lfeon. A/52 table 5.8.
   private static final int[] ACMOD_CHANNELS = {2, 1, 2, 3, 3, 4, 4, 5};

   public static class SyncFrame {
      public int offset;
      public int length;
      public int sampleRate;
      public int bitRate;        // kb/s
      public int channels;       // including LFE
      public int acmod;
      public boolean lfeon;
      public int bsid;
      public int bsmod;

      public String toString() {
         return String.format("ac3 %d Hz %d kb/s %d ch acmod=%d lfe=%s len=%d",
            sampleRate, bitRate, channels, acmod, lfeon, length);
      }
   }

   // Parse the syncframe starting at offset, or null if there isn't a valid one there.
   public static SyncFrame parseAt(byte[] d, int offset) {
      if (offset + 6 > d.length) return null;
      if (((d[offset] & 0xFF) << 8 | (d[offset+1] & 0xFF)) != SYNCWORD) return null;

      BitReader r = new BitReader(d, offset + 4);   // past syncword and crc1
      int fscod      = r.read(2);
      int frmsizecod = r.read(6);
      int bsid       = r.read(5);
      int bsmod      = r.read(3);
      int acmod      = r.read(3);

      if (fscod == 3 || frmsizecod >= 38) return null;
      // bsid above 8 is E-AC-3 or an annex variant; the frame size table below does not apply.
      if (bsid > 8) return null;

      if ((acmod & 0x1) != 0 && acmod != 0x1) r.read(2);   // cmixlev
      if ((acmod & 0x4) != 0)                 r.read(2);   // surmixlev
      if (acmod == 0x2)                       r.read(2);   // dsurmod
      boolean lfeon = r.read(1) == 1;

      SyncFrame f = new SyncFrame();
      f.offset     = offset;
      f.sampleRate = SAMPLE_RATE[fscod];
      f.bitRate    = BITRATE[frmsizecod >> 1];
      f.acmod      = acmod;
      f.lfeon      = lfeon;
      f.bsid       = bsid;
      f.bsmod      = bsmod;
      f.channels   = ACMOD_CHANNELS[acmod] + (lfeon ? 1 : 0);
      f.length     = frameLength(fscod, frmsizecod);
      return f.length > 0 ? f : null;
   }

   // Frame length in bytes. A/52 5.4.1.3: the 44.1 kHz row carries a padding word selected
   // by the low bit of frmsizecod, which is why this is not simply bitrate * a constant.
   private static int frameLength(int fscod, int frmsizecod) {
      int bitRate = BITRATE[frmsizecod >> 1];
      switch (fscod) {
         case 0:  return bitRate * 2 * 2;                                   // 48 kHz
         case 1:  return (320 * bitRate / 147 + (frmsizecod & 1)) * 2;      // 44.1 kHz
         case 2:  return bitRate * 3 * 2;                                   // 32 kHz
         default: return 0;
      }
   }

   // Split one PES payload unit into its syncframes. A unit that ends mid-frame yields only
   // the whole frames ahead of the truncation: a short tail is not a sample, and the payload
   // completeness flag from the library is what says whether to expect one.
   public static List<SyncFrame> split(byte[] d) {
      List<SyncFrame> out = new ArrayList<SyncFrame>();
      int i = 0;
      // Skip anything before the first syncword; a unit should start on one.
      while (i + 1 < d.length && ((d[i] & 0xFF) << 8 | (d[i+1] & 0xFF)) != SYNCWORD) i++;
      while (i < d.length) {
         SyncFrame f = parseAt(d, i);
         if (f == null) break;
         if (i + f.length > d.length) break;      // truncated tail, not a sample
         out.add(f);
         i += f.length;
      }
      return out;
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
   }
}
