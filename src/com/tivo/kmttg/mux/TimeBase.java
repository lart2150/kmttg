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

import java.util.HashMap;
import java.util.Map;

// All timestamp policy for the muxer lives here. TivoLibre reports what the stream says and
// nothing more, by agreement, so rebasing, the 33 bit wrap and the gap decision are ours.
//
// Three rules, and the reasons matter more than the code:
//  - Rebase to the minimum across ALL streams, never per stream. Matroska cluster timestamps
//    are unsigned, and the inter-stream offset is the A/V sync: on the reference recording
//    audio leads video by about a second, and collapsing that desynchronises the file.
//  - Gaps are preserved. This is a remux, so a 39 second excision stays 39 seconds of
//    timeline rather than being closed up.
//  - Nothing is invented. A payload with no PTS is interpolated from the frame rate by the
//    caller if at all; this class never fabricates an anchor.
public class TimeBase {
   public static final long PTS_HZ = 90000;

   // A stream position as hh:mm:ss.mmm. "~591,328 ms" and "00:09:51.328" are the same instant,
   // but only one of them can be typed into a seek bar to go and look at what happened there.
   public static String hms(long ms) {
      String sign = ms < 0 ? "-" : "";
      long t = ms < 0 ? -ms : ms;
      return String.format("%s%02d:%02d:%02d.%03d",
         sign, t / 3600000, (t / 60000) % 60, (t / 1000) % 60, t % 1000);
   }

   private static final long PTS_RANGE = 1L << 33;
   private static final long PTS_HALF  = 1L << 32;

   private static class Wrap {
      long lastRaw = -1;
      long wraps = 0;
   }

   private final Map<Integer,Wrap> wrap = new HashMap<Integer,Wrap>();
   private long base = Long.MIN_VALUE;
   private long clamped = 0;

   // Unwrap a raw 33 bit PTS into a monotonic value. The wrap is per PID, but every stream
   // shares the 90 kHz clock and wraps at the same raw value, so they stay consistent.
   public long unwrap(int pid, long raw) {
      Wrap w = wrap.get(pid);
      if (w == null) {
         w = new Wrap();
         wrap.put(pid, w);
      }
      if (w.lastRaw < 0) {
         w.lastRaw = raw;
         return raw;
      }
      if (raw < w.lastRaw - PTS_HALF) {
         w.wraps++;
      }
      long unwrapped = raw + w.wraps * PTS_RANGE;

      // Decode order is not display order. Around the wrap, a B picture displayed just
      // before it arrives after a post-wrap I or P picture, as a large FORWARD jump. Adding
      // the current wrap count would push it a whole range ahead and - because it also moved
      // lastRaw up - make the next genuine picture look like another wrap, doubling the
      // error for the rest of the recording. Such a sample belongs to the previous cycle,
      // and it must not disturb the running state.
      long reference = w.lastRaw + w.wraps * PTS_RANGE;
      if (unwrapped > reference + PTS_HALF && w.wraps > 0) {
         return unwrapped - PTS_RANGE;
      }
      w.lastRaw = raw;
      return unwrapped;
   }

   public boolean isLocked()   { return base != Long.MIN_VALUE; }
   public long getBase()       { return base; }
   public long getClamped()    { return clamped; }

   public void lock(long minPts) {
      base = minPts;
   }

   // Rebased milliseconds. Clamping should be rare: the caller locks the base from a window
   // wider than one GOP, so an open GOP's leading B pictures are normally already inside it.
   public long toMillis(long unwrapped) {
      long rel = unwrapped - base;
      if (rel < 0) {
         clamped++;
         rel = 0;
      }
      return rel * 1000 / PTS_HZ;
   }
}
