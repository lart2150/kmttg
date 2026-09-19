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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The two playlists a TiVo streaming session serves. Parsing only, so the shapes the box
// actually returns can be tested without a box; fetching belongs to HlsStream.
public class HlsPlaylist {

   public static class Variant {
      public final long bandwidth;
      public final String uri;

      Variant(long bandwidth, String uri) {
         this.bandwidth = bandwidth;
         this.uri = uri;
      }
   }

   public static class Media {
      public final String keyUri;
      public final List<String> segments;
      public final int targetDurationSec;

      Media(String keyUri, List<String> segments, int targetDurationSec) {
         this.keyUri = keyUri;
         this.segments = segments;
         this.targetDurationSec = targetDurationSec;
      }
   }

   private static final Pattern BANDWIDTH = Pattern.compile("BANDWIDTH=(\\d+)");
   private static final Pattern KEY_URI = Pattern.compile("URI=\"([^\"]+)\"");
   private static final Pattern TARGET_DURATION = Pattern.compile("#EXT-X-TARGETDURATION:(\\d+)");

   // Every #EXT-X-STREAM-INF and the URI on the line after it.
   public static List<Variant> variants(String master) {
      List<Variant> out = new ArrayList<Variant>();
      if (master == null) return out;
      String[] lines = master.split("\\r?\\n");
      for (int i = 0; i < lines.length; ++i) {
         String line = lines[i].trim();
         if (! line.startsWith("#EXT-X-STREAM-INF")) continue;
         if (i + 1 >= lines.length) break;
         String uri = lines[i + 1].trim();
         if (uri.isEmpty() || uri.startsWith("#")) continue;
         Matcher m = BANDWIDTH.matcher(line);
         out.add(new Variant(m.find() ? Long.parseLong(m.group(1)) : 0, uri));
      }
      return out;
   }

   // The cheapest variant to transcode. The anchor describes the source stream rather than
   // the encode, so it is identical in all of them.
   public static Variant lowestBandwidth(List<Variant> variants) {
      Variant best = null;
      if (variants == null) return null;
      for (Variant v : variants) {
         if (best == null || v.bandwidth < best.bandwidth) best = v;
      }
      return best;
   }

   public static Media media(String variant) {
      List<String> segments = new ArrayList<String>();
      String keyUri = null;
      int target = 0;
      if (variant == null) return new Media(null, segments, 0);
      for (String raw : variant.split("\\r?\\n")) {
         String line = raw.trim();
         if (line.isEmpty()) continue;
         if (line.startsWith("#EXT-X-KEY")) {
            Matcher m = KEY_URI.matcher(line);
            if (m.find()) keyUri = m.group(1);
         } else if (line.startsWith("#EXT-X-TARGETDURATION")) {
            Matcher m = TARGET_DURATION.matcher(line);
            if (m.find()) target = Integer.parseInt(m.group(1));
         } else if (! line.startsWith("#")) {
            segments.add(line);
         }
      }
      return new Media(keyUri, segments, target);
   }

   // The segment's media sequence number, which is also its decryption IV.
   //
   // These names are HEXADECIMAL - a playlist runs 0..9, A..F, 10, 11 - so a decimal reading
   // agrees for the first ten and is wrong after. It fails almost invisibly: the number sits in
   // the last four bytes of the IV, so a wrong one corrupts four bytes of the first packet and
   // leaves the rest of the segment readable. -1 for a name that is not a number.
   public static long sequenceNumber(String segmentName) {
      if (segmentName == null) return -1;
      try {
         return Long.parseLong(segmentName.trim(), 16);
      } catch (NumberFormatException e) {
         return -1;
      }
   }

   // The 16 byte big-endian IV it implies.
   public static byte[] iv(long sequenceNumber) {
      byte[] iv = new byte[16];
      for (int i = 0; i < 8; ++i) {
         iv[15 - i] = (byte) ((sequenceNumber >>> (8 * i)) & 0xff);
      }
      return iv;
   }
}
