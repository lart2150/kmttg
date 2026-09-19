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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import net.straylightlabs.tivolibre.ElementaryStreamInfo;
import net.straylightlabs.tivolibre.TivoDecoder;
import net.straylightlabs.tivolibre.TransportStreamReader;

// Standalone entry point for the remux work: reads a decrypted .ts and reports what the
// FrameSink delivered. No GUI, no job plumbing, no config, so it stays runnable as
//    java -cp release/kmttg.jar com.tivo.kmttg.mux.RemuxMain FILE.ts
// Compare its video payload count and first PTS against:
//    ffprobe -v error -select_streams v -show_entries frame=pts,pkt_dts -of csv FILE.ts
public class RemuxMain {
   public static void main(String[] args) throws Exception {
      if (args.length < 1) {
         System.err.println("usage: RemuxMain <file.ts|file.TiVo> [out.mkv]");
         System.err.println("       a .TiVo input reads the MAK from $KMTTG_MAK");
         System.err.println("       a third arg writes the decrypted .ts too (dual mode)");
         System.exit(2);
      }
      String path = args[0];
      // With an output path this muxes; without one it only measures.
      MuxSink mux = args.length > 1 ? new MuxSink(new File(args[1])) : null;
      ProbeSink probe = mux == null ? new ProbeSink() : null;

      net.straylightlabs.tivolibre.FrameSink sink =
         mux == null ? (net.straylightlabs.tivolibre.FrameSink)probe : mux;

      long start = System.currentTimeMillis();
      boolean ok;
      InputStream in = new BufferedInputStream(new FileInputStream(path), 1 << 20);
      try {
         if (path.toLowerCase().endsWith(".tivo")) {
            // Fused path: decrypt and mux in one read, with no intermediate .ts on disk.
            // The MAK is taken from the environment rather than the argument list so it
            // stays out of shell history and process listings.
            String mak = System.getenv("KMTTG_MAK");
            if (mak == null || mak.isEmpty()) {
               System.err.println("A .TiVo input needs a MAK: set KMTTG_MAK in the environment");
               System.exit(2);
            }
            // Dual mode: a third argument also writes the decrypted .ts, for the users who
            // still need it on disk for comskip or caption extraction. One read either way.
            OutputStream ts = args.length > 2
               ? new BufferedOutputStream(new FileOutputStream(args[2]), 1 << 20) : null;
            try {
               TivoDecoder.Builder b = new TivoDecoder.Builder()
                  .input(in)
                  .frameSink(sink)
                  .mak(mak);
               if (ts != null) b.output(ts);
               ok = b.build().decode();
            } finally {
               if (ts != null) ts.close();
            }
         } else {
            ok = new TransportStreamReader.Builder()
               .input(in)
               .frameSink(sink)
               .build()
               .read();
         }
      } finally {
         in.close();
      }
      long elapsed = System.currentTimeMillis() - start;

      if (mux != null) {
         reportMux(path, args[1], mux, ok, elapsed);
         System.exit(ok && ! mux.isFailed() ? 0 : 1);
      }
      report(path, probe, ok, elapsed);
      System.exit(ok ? 0 : 1);
   }

   private static void reportMux(String in, String out, MuxSink mux, boolean ok, long elapsed) {
      System.out.printf("%s -> %s%n  read()=%s in %,d ms%n", in, out, ok, elapsed);
      if (mux.isFailed()) {
         System.out.println("  FAILED: " + mux.getFailure());
         return;
      }
      for (Map.Entry<Integer,Long> e : mux.getSampleCounts().entrySet()) {
         System.out.printf("    pid=0x%04x samples=%,d%n", e.getKey(), e.getValue());
      }
      // Both forms: the milliseconds are what the baselines in MUX-PLAN.md are written in.
      System.out.printf("  duration=%s (%,d ms) cues=%,d%n",
         TimeBase.hms(mux.getDurationMs()), mux.getDurationMs(), mux.getCueCount());
      System.out.printf("  noPts dropped=%,d clamped=%,d%n",
         mux.getDiscardedNoPts(), mux.getClampedTimestamps());
      System.out.printf("  notes: %d%n", mux.getNotes().size());
      for (String n : mux.getNotes()) System.out.println("    " + n);
   }

   private static void report(String path, ProbeSink sink, boolean ok, long elapsed) {
      System.out.printf("%s%n  read()=%s in %,d ms%n", path, ok, elapsed);
      System.out.printf("  onProgram calls: %d%n", sink.getProgramCalls());
      for (ElementaryStreamInfo info : sink.getProgram()) {
         System.out.printf("    pid=0x%04x stream_type=0x%02x descriptors=%d bytes%n",
            info.getPid(), info.getStreamType(),
            info.getDescriptors() == null ? 0 : info.getDescriptors().length);
      }

      System.out.println("  streams:");
      for (Map.Entry<Integer,ProbeSink.StreamStats> e : sink.getStats().entrySet()) {
         ProbeSink.StreamStats s = e.getValue();
         System.out.printf(
            "    pid=0x%04x type=0x%02x payloads=%,d bytes=%,d pts=[%d..%d] noPts=%d noDts=%d short=%d atEof=%d%n",
            s.pid, s.streamType, s.payloads, s.bytes, s.firstPts, s.lastPts,
            s.withoutPts, s.withoutDts, s.shortOfDeclared, s.endedWithStream);
         if (s.sequence != null) {
            System.out.printf("        seq: %s%n", s.sequence);
            System.out.printf("        pictures: I=%,d P=%,d B=%,d none=%,d%n",
               s.picI, s.picP, s.picB, s.picNone);
         }
         if (s.audioFormat != null) {
            System.out.printf("        %s%n", s.audioFormat);
            System.out.printf("        syncframes: %,d  unitsWithTail=%,d tailBytes=%,d%n",
               s.syncFrames, s.unitsWithTail, s.tailBytes);
         }
      }

      System.out.printf("  discontinuities: %d%n", sink.getBreaks().size());
      for (String b : sink.getBreaks()) {
         System.out.println("    " + b);
      }
      System.out.printf("  result: %s%n", sink.getResult());
   }
}
