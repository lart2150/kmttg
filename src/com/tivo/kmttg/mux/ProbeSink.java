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
package com.tivo.kmttg.mux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tivo.kmttg.mux.codec.Ac3Parser;
import com.tivo.kmttg.mux.codec.Mpeg2Parser;

import net.straylightlabs.tivolibre.DecodeResult;
import net.straylightlabs.tivolibre.DiscontinuityReason;
import net.straylightlabs.tivolibre.ElementaryStreamInfo;
import net.straylightlabs.tivolibre.FrameSink;
import net.straylightlabs.tivolibre.PesPayload;

// Counts what comes out of a TivoLibre FrameSink without muxing any of it. This is the
// measuring instrument the real muxer gets checked against: payload counts and PTS ranges
// here have to line up with ffprobe on the same file before MkvMuxer is worth writing.
// Deliberately free of config, log and gui so it runs from a plain main().
public class ProbeSink implements FrameSink {
   public static class StreamStats {
      public int pid;
      public int streamType;
      public long payloads;
      public long bytes;
      public long firstPts = -1;
      public long lastPts = -1;
      public long withoutPts;
      public long withoutDts;
      public long shortOfDeclared;
      public long endedWithStream;
      // Codec level, filled only for stream types the parsers cover
      public long picI, picP, picB, picNone;
      public long syncFrames;
      public long unitsWithTail;
      public long tailBytes;
      public Mpeg2Parser.SequenceHeader sequence;
      public Ac3Parser.SyncFrame audioFormat;
   }

   private static boolean isMpeg2Video(int streamType) {
      return streamType == 0x01 || streamType == 0x02;
   }

   private static boolean isAc3(int streamType) {
      return streamType == 0x81;
   }

   private final Map<Integer,StreamStats> stats = new LinkedHashMap<Integer,StreamStats>();
   private final List<String> breaks = new ArrayList<String>();
   private List<ElementaryStreamInfo> program = new ArrayList<ElementaryStreamInfo>();
   private int programCalls = 0;
   private DecodeResult result = null;

   public void onProgram(List<ElementaryStreamInfo> streams) {
      programCalls++;
      program = streams;
      // The list is cumulative, so a stream that first appears part way through shows up as a
      // new entry here rather than as a separate notification.
      for (ElementaryStreamInfo info : streams) {
         StreamStats s = stats.get(info.getPid());
         if (s == null) {
            s = new StreamStats();
            s.pid = info.getPid();
            stats.put(info.getPid(), s);
         }
         s.streamType = info.getStreamType();
      }
   }

   public void onPesPayload(PesPayload payload) {
      StreamStats s = stats.get(payload.getPid());
      if (s == null) {
         // Shouldn't happen: suppression means every emitting PID was announced first.
         s = new StreamStats();
         s.pid = payload.getPid();
         stats.put(payload.getPid(), s);
      }
      s.payloads++;
      s.bytes += payload.getData().length;
      if (payload.hasPts()) {
         if (s.firstPts < 0) s.firstPts = payload.getPts();
         s.lastPts = payload.getPts();
      } else {
         s.withoutPts++;
      }
      if (! payload.hasDts()) s.withoutDts++;
      switch (payload.getCompleteness()) {
         case SHORT_OF_DECLARED_LENGTH: s.shortOfDeclared++; break;
         case ENDED_WITH_STREAM:        s.endedWithStream++; break;
         default: break;
      }

      byte[] data = payload.getData();
      if (isMpeg2Video(s.streamType)) {
         if (s.sequence == null) {
            // A recording can start anywhere, so keep looking until one arrives.
            s.sequence = Mpeg2Parser.findSequenceHeader(data);
         }
         switch (Mpeg2Parser.pictureCodingType(data)) {
            case Mpeg2Parser.PICTURE_I: s.picI++; break;
            case Mpeg2Parser.PICTURE_P: s.picP++; break;
            case Mpeg2Parser.PICTURE_B: s.picB++; break;
            default: s.picNone++; break;
         }
      } else if (isAc3(s.streamType)) {
         List<Ac3Parser.SyncFrame> frames = Ac3Parser.split(data);
         s.syncFrames += frames.size();
         if (s.audioFormat == null && ! frames.isEmpty()) s.audioFormat = frames.get(0);
         if (! frames.isEmpty()) {
            Ac3Parser.SyncFrame last = frames.get(frames.size() - 1);
            int consumed = last.offset + last.length;
            if (consumed < data.length) {
               s.unitsWithTail++;
               s.tailBytes += data.length - consumed;
            }
         }
      }
   }

   public void onDiscontinuity(int pid, DiscontinuityReason reason) {
      breaks.add(String.format("pid=0x%04x %s", pid, reason));
   }

   public void onEnd(DecodeResult result) {
      this.result = result;
   }

   public Map<Integer,StreamStats> getStats()          { return stats; }
   public List<String> getBreaks()                     { return breaks; }
   public List<ElementaryStreamInfo> getProgram()      { return program; }
   public int getProgramCalls()                        { return programCalls; }
   public DecodeResult getResult()                     { return result; }
}
