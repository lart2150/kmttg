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
package com.tivo.kmttg.rpc;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

import net.straylightlabs.tivolibre.DecodeResult;
import net.straylightlabs.tivolibre.DiscontinuityReason;
import net.straylightlabs.tivolibre.ElementaryStreamInfo;
import net.straylightlabs.tivolibre.FrameSink;
import net.straylightlabs.tivolibre.PesPayload;
import net.straylightlabs.tivolibre.TransportStreamReader;

// Reads startStreamTime out of one decrypted HLS segment - the offset ClipSegments.reanchor
// subtracts to put SkipMode cut points back on the recording's own clock.
//
// The transport stream work is tivolibre's: TransportStreamReader is built for unscrambled
// input, and it assembles stream type 0x15 rather than dropping it.
public class HlsAnchor {

   // "Metadata carried in PES packets". The PID varies per stream, so it comes from the PMT.
   private static final int METADATA_STREAM_TYPE = 0x15;

   // PRIV frames are namespaced by owner, and a stream carries other owners' frames.
   private static final String OWNER = "TiVo";
   private static final int TAG_TIME_STATS = 3;

   // startStreamTime was added later, so it is only present - and the record only long enough
   // to hold it - from version 4 on.
   private static final int MIN_VERSION = 4;
   private static final int TIME_STATS_WITH_START = 16;

   private static final double NS_PER_MS = 1e6;

   // Milliseconds to subtract, or null when this segment carries no usable TiVo ID3. Rounded:
   // the offsets it corrects are whole ms.
   public static Long fromSegment(byte[] segment) {
      debug.print("segment bytes=" + (segment == null ? 0 : segment.length));
      Long ns = startStreamTimeNs(segment);
      return ns == null ? null : Math.round(ns / NS_PER_MS);
   }

   // The raw nanosecond stamp, so tests can assert the exact captured value.
   static Long startStreamTimeNs(byte[] segment) {
      if (segment == null || segment.length == 0) return null;
      Sink sink = new Sink();
      try {
         TransportStreamReader reader = new TransportStreamReader.Builder()
            .input(new ByteArrayInputStream(segment))
            .frameSink(sink)
            .build();
         reader.read();
      } catch (RuntimeException e) {
         // The stamp arrives near the head of a segment and the trailing partial packet is
         // where a parse is most likely to fail, so a late failure must not discard an answer
         // already in hand.
         log.error("HlsAnchor - " + e.getMessage());
      }
      return sink.found;
   }

   // Every stamp in a session states the same value, so the first answer ends the search.
   private static class Sink implements FrameSink {
      private int metadataPid = -1;
      private Long found = null;

      @Override
      public void onProgram(List<ElementaryStreamInfo> streams) {
         if (streams == null) return;
         for (ElementaryStreamInfo s : streams) {
            if (s.getStreamType() == METADATA_STREAM_TYPE) {
               metadataPid = s.getPid();
               return;
            }
         }
      }

      @Override
      public void onPesPayload(PesPayload payload) {
         if (found != null || payload == null) return;
         if (metadataPid < 0 || payload.getPid() != metadataPid) return;
         found = fromId3(payload.getData());
      }

      @Override
      public void onDiscontinuity(int pid, DiscontinuityReason reason) {}

      @Override
      public void onEnd(DecodeResult result) {}
   }

   // The stamp in ns, or null - another owner's frame is normal traffic, not an error.
   static Long fromId3(byte[] data) {
      if (data == null || data.length < 10) return null;
      if (data[0] != 'I' || data[1] != 'D' || data[2] != '3') return null;
      int major = data[3] & 0xff;
      int tagSize = synchsafe(data, 6);
      if (tagSize <= 0) return null;

      int end = Math.min(data.length, 10 + tagSize);
      int p = 10;
      while (p + 10 <= end) {
         String id = new String(data, p, 4, StandardCharsets.ISO_8859_1);
         // Frame sizes became synchsafe in 2.4. Below 128 bytes the two readings agree, which
         // is why this went unnoticed until a payload grew - so pick by version, not by luck.
         int size = major >= 4 ? synchsafe(data, p + 4) : (int) readUInt32(data, p + 4);
         if (size <= 0 || p + 10 + size > end) return null;
         if (id.equals("PRIV")) {
            Long ns = fromPriv(data, p + 10, size);
            if (ns != null) return ns;
         }
         p += 10 + size;
      }
      return null;
   }

   // One PRIV frame: a NUL-terminated owner, then a version byte and TLV records.
   private static Long fromPriv(byte[] data, int off, int len) {
      int end = off + len;
      int nul = -1;
      for (int i = off; i < end; ++i) {
         if (data[i] == 0) { nul = i; break; }
      }
      if (nul < 0) return null;
      if (! OWNER.equals(new String(data, off, nul - off, StandardCharsets.ISO_8859_1))) return null;

      int p = nul + 1;
      if (p >= end) return null;
      int version = data[p++] & 0xff;
      if (version < MIN_VERSION) return null;

      while (p + 2 <= end) {
         int tag = data[p] & 0xff;
         int size = data[p + 1] & 0xff;
         int value = p + 2;
         if (value + size > end) return null;
         if (tag == TAG_TIME_STATS && size >= TIME_STATS_WITH_START) {
            // stime first, then startStreamTime. stime is whichever packet last arrived and
            // runs 5-15 s ahead of the recording's own start - TiVo's clippy.js anchors on it
            // and is late by exactly that much - so the second pair is the one to read.
            long hi = readUInt32(data, value + 8);
            long lo = readUInt32(data, value + 12);
            return (hi << 32) | lo;
         }
         p = value + size;
      }
      return null;
   }

   // ID3's 7-bits-per-byte size encoding, so a size can never look like an MPEG sync word.
   private static int synchsafe(byte[] data, int off) {
      if (off + 4 > data.length) return -1;
      for (int i = 0; i < 4; ++i) {
         if ((data[off + i] & 0x80) != 0) return -1;
      }
      return ((data[off] & 0x7f) << 21) | ((data[off + 1] & 0x7f) << 14)
           | ((data[off + 2] & 0x7f) << 7) | (data[off + 3] & 0x7f);
   }

   private static long readUInt32(byte[] data, int off) {
      if (off + 4 > data.length) return 0;
      return ((long) (data[off] & 0xff) << 24) | ((data[off + 1] & 0xff) << 16)
           | ((data[off + 2] & 0xff) << 8) | (data[off + 3] & 0xff);
   }
}
