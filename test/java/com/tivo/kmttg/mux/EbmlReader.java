package com.tivo.kmttg.mux;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Reads back what the muxer actually wrote. A byte scan can prove a string reached the file,
// but not which track carries it, and a header that shifts by a byte whenever anything ahead
// of it changes makes fixed offsets worse than useless - so the questions that matter here
// (which track is the cue track, what language track 2 claims, when a block is timed) need a
// reader rather than a search.
class EbmlReader {
   static final long SEGMENT           = 0x18538067L;
   static final long INFO              = 0x1549A966L;
   static final long DURATION          = 0x4489L;
   static final long TRACKS            = 0x1654AE6BL;
   static final long TRACK_ENTRY       = 0xAEL;
   static final long TRACK_NUMBER      = 0xD7L;
   static final long TRACK_TYPE        = 0x83L;
   static final long TRACK_NAME        = 0x536EL;
   static final long FLAG_DEFAULT      = 0x88L;
   static final long CODEC_ID          = 0x86L;
   static final long CODEC_PRIVATE     = 0x63A2L;
   static final long LANGUAGE          = 0x22B59CL;
   static final long DEFAULT_DURATION  = 0x23E383L;
   static final long VIDEO             = 0xE0L;
   static final long PIXEL_WIDTH       = 0xB0L;
   static final long PIXEL_HEIGHT      = 0xBAL;
   static final long DISPLAY_WIDTH     = 0x54B0L;
   static final long DISPLAY_HEIGHT    = 0x54BAL;
   static final long DISPLAY_UNIT      = 0x54B2L;
   static final long AUDIO             = 0xE1L;
   static final long SAMPLING_FREQ     = 0xB5L;
   static final long CHANNELS          = 0x9FL;
   static final long CLUSTER           = 0x1F43B675L;
   static final long CLUSTER_TIMESTAMP = 0xE7L;
   static final long SIMPLE_BLOCK      = 0xA3L;
   static final long CUES              = 0x1C53BB6BL;
   static final long CUE_POINT         = 0xBBL;
   static final long CUE_TIME          = 0xB3L;
   static final long CUE_TRACK_POS     = 0xB7L;
   static final long CUE_TRACK         = 0xF7L;

   // Which ids hold other elements rather than a value. Anything not named here is left as
   // bytes, which is why this list only has to cover what the tests ask about.
   private static final Set<Long> MASTERS = new HashSet<Long>(Arrays.asList(
      SEGMENT, INFO, TRACKS, TRACK_ENTRY, VIDEO, AUDIO, CLUSTER, CUES, CUE_POINT,
      CUE_TRACK_POS));

   final long id;
   final byte[] data;
   private List<EbmlReader> kids;

   private EbmlReader(long id, byte[] data) {
      this.id = id;
      this.data = data;
   }

   static class Block {
      int track;
      long ms;
      boolean key;
      byte[] data;
   }

   static List<EbmlReader> parse(byte[] b) {
      List<EbmlReader> out = new ArrayList<EbmlReader>();
      int i = 0;
      while (i < b.length) {
         int idLen = idLength(b[i] & 0xFF);
         if (idLen < 0 || i + idLen > b.length) break;
         long id = 0;
         for (int j = 0; j < idLen; j++) id = (id << 8) | (b[i+j] & 0xFFL);
         i += idLen;
         if (i >= b.length) break;
         int sizeLen = sizeLength(b[i] & 0xFF);
         if (sizeLen < 0 || i + sizeLen > b.length) break;
         long size = b[i] & (0xFFL >> sizeLen);
         for (int j = 1; j < sizeLen; j++) size = (size << 8) | (b[i+j] & 0xFFL);
         i += sizeLen;
         int len = (int)Math.min(size, b.length - i);
         out.add(new EbmlReader(id, Arrays.copyOfRange(b, i, i + len)));
         i += len;
      }
      return out;
   }

   static EbmlReader segment(byte[] file) {
      for (EbmlReader e : parse(file)) {
         if (e.id == SEGMENT) return e;
      }
      return null;
   }

   static List<EbmlReader> trackEntries(byte[] file) {
      EbmlReader tracks = segment(file).child(TRACKS);
      return tracks == null ? Collections.<EbmlReader>emptyList() : tracks.childrenOf(TRACK_ENTRY);
   }

   // Every SimpleBlock in the file, with its cluster-relative offset already folded back in,
   // so a block's timestamp here is the millisecond a player would show it at.
   static List<Block> blocks(byte[] file) {
      List<Block> out = new ArrayList<Block>();
      for (EbmlReader cluster : segment(file).childrenOf(CLUSTER)) {
         long base = cluster.child(CLUSTER_TIMESTAMP).uint();
         for (EbmlReader sb : cluster.childrenOf(SIMPLE_BLOCK)) {
            byte[] b = sb.data;
            int trackLen = sizeLength(b[0] & 0xFF);
            long track = b[0] & (0xFFL >> trackLen);
            for (int j = 1; j < trackLen; j++) track = (track << 8) | (b[j] & 0xFFL);
            int p = trackLen;
            short rel = (short)(((b[p] & 0xFF) << 8) | (b[p+1] & 0xFF));
            Block blk = new Block();
            blk.track = (int)track;
            blk.ms = base + rel;
            blk.key = (b[p+2] & 0x80) != 0;
            blk.data = Arrays.copyOfRange(b, p + 3, b.length);
            out.add(blk);
         }
      }
      return out;
   }

   List<EbmlReader> children() {
      if (kids == null) {
         kids = MASTERS.contains(Long.valueOf(id)) ? parse(data)
                                                   : Collections.<EbmlReader>emptyList();
      }
      return kids;
   }

   EbmlReader child(long wanted) {
      for (EbmlReader e : children()) {
         if (e.id == wanted) return e;
      }
      return null;
   }

   List<EbmlReader> childrenOf(long wanted) {
      List<EbmlReader> out = new ArrayList<EbmlReader>();
      for (EbmlReader e : children()) {
         if (e.id == wanted) out.add(e);
      }
      return out;
   }

   boolean has(long wanted) {
      return child(wanted) != null;
   }

   // The value of a child, or the default when the element is absent - which in Matroska is
   // how "yes, like everything else" is stated for flags such as FlagDefault.
   long uintOr(long wanted, long fallback) {
      EbmlReader e = child(wanted);
      return e == null ? fallback : e.uint();
   }

   String stringOr(long wanted, String fallback) {
      EbmlReader e = child(wanted);
      return e == null ? fallback : e.str();
   }

   long uint() {
      long v = 0;
      for (byte b : data) v = (v << 8) | (b & 0xFFL);
      return v;
   }

   double f64() {
      long bits = 0;
      for (byte b : data) bits = (bits << 8) | (b & 0xFFL);
      return data.length == 4 ? Float.intBitsToFloat((int)bits) : Double.longBitsToDouble(bits);
   }

   String str() {
      try {
         return new String(data, "UTF-8");
      } catch (UnsupportedEncodingException e) {
         throw new IllegalStateException(e);
      }
   }

   private static int idLength(int first) {
      if ((first & 0x80) != 0) return 1;
      if ((first & 0x40) != 0) return 2;
      if ((first & 0x20) != 0) return 3;
      if ((first & 0x10) != 0) return 4;
      return -1;
   }

   private static int sizeLength(int first) {
      for (int i = 0; i < 8; i++) {
         if ((first & (0x80 >> i)) != 0) return i + 1;
      }
      return -1;
   }
}
