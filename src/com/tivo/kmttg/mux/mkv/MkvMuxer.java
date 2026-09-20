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
package com.tivo.kmttg.mux.mkv;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

// Matroska writer. Timestamps arriving here are already rebased milliseconds: this class
// does no timestamp policy, it only lays out elements.
//
// Two structural facts drive the design. Tracks sits in the segment header ahead of the
// first cluster and cannot be revised, so the track list must be final before the first
// sample. And a SimpleBlock timestamp is a signed 16 bit offset from its cluster, so a
// cluster is capped well under that range.
public class MkvMuxer {
   // Element IDs
   private static final long EBML              = 0x1A45DFA3L;
   private static final long EBML_VERSION      = 0x4286L;
   private static final long EBML_READ_VERSION = 0x42F7L;
   private static final long EBML_MAX_ID_LEN   = 0x42F2L;
   private static final long EBML_MAX_SIZE_LEN = 0x42F3L;
   private static final long DOC_TYPE          = 0x4282L;
   private static final long DOC_TYPE_VERSION  = 0x4287L;
   private static final long DOC_TYPE_READ_VER = 0x4285L;

   private static final long SEGMENT           = 0x18538067L;
   private static final long INFO              = 0x1549A966L;
   private static final long TIMESTAMP_SCALE   = 0x2AD7B1L;
   private static final long DURATION          = 0x4489L;
   private static final long SEGMENT_UID       = 0x73A4L;
   private static final long SEGMENT_TITLE     = 0x7BA9L;
   private static final long DATE_UTC          = 0x4461L;
   private static final long MATROSKA_EPOCH_MS = 978307200000L;   // 2001-01-01T00:00:00 UTC
   private static final long MUXING_APP        = 0x4D80L;
   private static final long WRITING_APP       = 0x5741L;

   private static final long TRACKS            = 0x1654AE6BL;
   private static final long TRACK_ENTRY       = 0xAEL;
   private static final long TRACK_NUMBER      = 0xD7L;
   private static final long TRACK_UID         = 0x73C5L;
   private static final long TRACK_TYPE        = 0x83L;
   private static final long TRACK_NAME        = 0x536EL;
   private static final long FLAG_DEFAULT      = 0x88L;
   private static final long FLAG_LACING       = 0x9CL;
   private static final long FLAG_INTERLACED   = 0x9AL;
   private static final long FIELD_ORDER       = 0x9DL;
   private static final long CODEC_ID          = 0x86L;
   private static final long CODEC_PRIVATE     = 0x63A2L;
   private static final long LANGUAGE          = 0x22B59CL;
   private static final long DEFAULT_DURATION  = 0x23E383L;
   private static final long VIDEO             = 0xE0L;
   private static final long PIXEL_WIDTH       = 0xB0L;
   private static final long PIXEL_HEIGHT      = 0xBAL;
   private static final long DISPLAY_WIDTH     = 0x54B0L;
   private static final long DISPLAY_HEIGHT    = 0x54BAL;
   private static final long DISPLAY_UNIT      = 0x54B2L;
   private static final long AUDIO             = 0xE1L;
   private static final long SAMPLING_FREQ     = 0xB5L;
   private static final long CHANNELS          = 0x9FL;

   private static final long CLUSTER           = 0x1F43B675L;
   private static final long CLUSTER_TIMESTAMP = 0xE7L;
   private static final long SIMPLE_BLOCK      = 0xA3L;

   private static final long CUES              = 0x1C53BB6BL;
   private static final long CUE_POINT         = 0xBBL;
   private static final long CUE_TIME          = 0xB3L;
   private static final long CUE_TRACK_POS     = 0xB7L;
   private static final long CUE_TRACK         = 0xF7L;
   private static final long CUE_CLUSTER_POS   = 0xF1L;

   private static final long SEEK_HEAD         = 0x114D9B74L;
   private static final long SEEK              = 0x4DBBL;
   private static final long SEEK_ID           = 0x53ABL;
   private static final long SEEK_POSITION     = 0x53ACL;

   private static final long TAGS              = 0x1254C367L;
   private static final long TAG               = 0x7373L;
   private static final long TARGETS           = 0x63C0L;
   private static final long TARGET_TYPE_VALUE = 0x68CAL;
   private static final long SIMPLE_TAG        = 0x67C8L;
   private static final long TAG_NAME          = 0x45A3L;
   private static final long TAG_STRING        = 0x4487L;

   private static final long CHAPTERS          = 0x1043A770L;
   private static final long EDITION_ENTRY     = 0x45B9L;
   private static final long EDITION_UID       = 0x45BCL;
   private static final long EDITION_HIDDEN    = 0x45BDL;
   private static final long EDITION_DEFAULT   = 0x45DBL;
   private static final long CHAPTER_ATOM      = 0xB6L;
   private static final long CHAPTER_UID       = 0x73C4L;
   private static final long CHAPTER_START     = 0x91L;
   private static final long CHAPTER_END       = 0x92L;
   private static final long CHAPTER_HIDDEN    = 0x98L;
   private static final long CHAPTER_ENABLED   = 0x4598L;
   private static final long CHAPTER_DISPLAY   = 0x80L;
   private static final long CHAP_STRING       = 0x85L;
   private static final long CHAP_LANGUAGE     = 0x437CL;

   private static final long ATTACHMENTS       = 0x1941A469L;
   private static final long ATTACHED_FILE     = 0x61A7L;
   private static final long FILE_NAME         = 0x466EL;
   private static final long FILE_MIME_TYPE    = 0x4660L;
   private static final long FILE_DATA         = 0x465CL;
   private static final long FILE_UID          = 0x46AEL;

   // Matroska target levels: the series and the episode carry the same tag names at
   // different levels, which is why metadata has to arrive as fields rather than as text.
   public static final int TARGET_COLLECTION = 70;
   public static final int TARGET_SEASON     = 60;
   public static final int TARGET_EPISODE    = 50;

   // A SimpleBlock offset is a signed 16 bit millisecond value, so a cluster has to stay
   // well inside +/-32767. Five seconds also keeps a cluster to a few MB at broadcast rates.
   private static final int MAX_CLUSTER_MS = 5000;
   private static final int SEGMENT_SIZE_LEN = 8;

   public static final int TYPE_VIDEO = 1;
   public static final int TYPE_AUDIO = 2;

   public static class Track {
      public int number;
      public int type;
      public String codecId;
      public String language = "und";
      // Shown beside the track in a player's menu and in the MKVToolNix header editor.
      public String name;
      // FlagDefault. Matroska assumes 1 when the element is absent, so this is only worth
      // stating to say "not this one" about a second track of the same kind.
      public boolean isDefault = true;
      // FlagInterlaced: 0 undetermined, 1 interlaced, 2 progressive. Broadcast is interlaced
      // far more often than not, and left unstated every file claims not to know.
      public int interlaced;
      // FieldOrder: 0 progressive, 1 top field first, 6 bottom field first. -1 states none,
      // which Matroska reads as undetermined.
      public int fieldOrder = -1;
      public byte[] codecPrivate;
      public int width, height;
      public int displayWidth, displayHeight;
      // 0 = pixels (the default), 3 = display aspect ratio
      public int displayUnit;
      public double sampleRate;
      public int channels;
      public long defaultDurationNs;
   }

   public static class Tag {
      public final int target;
      public final String name;
      public final String value;

      public Tag(int target, String name, String value) {
         this.target = target;
         this.name = name;
         this.value = value;
      }
   }

   // Times are milliseconds on the same rebased clock as addSample, converted to the
   // nanoseconds Matroska wants at write time. ChapterTimeStart is one of the few elements
   // the spec does NOT scale by TimestampScale, so it cannot share the millisecond path.
   public static class Chapter {
      public final long startMs;
      public final long endMs;
      public final String name;

      public Chapter(long startMs, long endMs, String name) {
         this.startMs = startMs;
         this.endMs = endMs;
         this.name = name;
      }
   }

   private static class Attachment {
      String name;
      String mimeType;
      byte[] data;
   }

   private static class Block {
      int track;
      long ts;
      boolean key;
      byte[] data;
   }

   private final RandomAccessFile file;
   private final List<Track> tracks = new ArrayList<Track>();
   private final List<Block> pending = new ArrayList<Block>();
   private final EbmlWriter buf = new EbmlWriter();

   private long segmentDataStart = -1;
   private long durationOffset = -1;
   private long segmentSizeOffset = -1;
   private long lastTimestamp = 0;
   // Running span of the open cluster. Rescanning every pending block on each sample made
   // shouldCloseCluster quadratic in the cluster, which at broadcast rates is hundreds of
   // blocks re-walked hundreds of times a second.
   private long clusterMin = Long.MAX_VALUE;
   private long clusterMax = Long.MIN_VALUE;
   private boolean headerWritten = false;

   // Cue entries, built as clusters close
   private final List<long[]> cues = new ArrayList<long[]>();   // {time, track, clusterPos}
   private int cueTrack = -1;

   // Segment Information. Both are optional in Matroska and both were left out, which is why
   // MKVToolNix showed an empty Title and Date for every file kmttg wrote.
   private String title;
   private Long dateUtcMs;
   // Shared: seeding a SecureRandom is the expensive part and it can block the first time,
   // which is no reason to pay it again on every file a batch writes.
   private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

   private final List<Tag> tags = new ArrayList<Tag>();
   private final List<Chapter> chapters = new ArrayList<Chapter>();
   private final List<Attachment> attachments = new ArrayList<Attachment>();
   // Offsets of the SeekPosition values reserved in the SeekHead, in write order
   private final List<long[]> seekPatch = new ArrayList<long[]>();   // {fileOffset, elementId}

   public MkvMuxer(File out) throws IOException {
      if (out.exists() && ! out.delete()) {
         throw new IOException("Could not overwrite " + out);
      }
      this.file = new RandomAccessFile(out, "rw");
   }

   // Tags and attachments must be declared before the header: the SeekHead reserves one
   // entry each and is written up front, where a player can find it without scanning.
   public void addTag(Tag tag) {
      requireUnwritten();
      tags.add(tag);
   }

   public void addTags(List<Tag> list) {
      for (Tag t : list) addTag(t);
   }

   // The file's own name, which is not a tag: Matroska keeps it in Segment Information, and
   // it is what a player's title bar and the MKVToolNix header editor show. Before the header
   // like everything else in Info.
   public void setTitle(String value) {
      requireUnwritten();
      title = value == null || value.isEmpty() ? null : value;
   }

   // When the segment was made, as epoch milliseconds.
   public void setDate(long epochMs) {
      requireUnwritten();
      dateUtcMs = Long.valueOf(epochMs);
   }

   public void addChapters(List<Chapter> list) {
      requireUnwritten();
      chapters.addAll(list);
   }

   public void addAttachment(String name, String mimeType, byte[] data) {
      requireUnwritten();
      if (data == null || data.length == 0) return;
      Attachment a = new Attachment();
      a.name = name;
      a.mimeType = mimeType;
      a.data = data;
      attachments.add(a);
   }

   private void requireUnwritten() {
      if (headerWritten) {
         throw new IllegalStateException("Declare tags and attachments before the header");
      }
   }

   public void addTrack(Track t) {
      if (headerWritten) {
         throw new IllegalStateException("Tracks cannot change once the header is written");
      }
      tracks.add(t);
      if (cueTrack < 0 && t.type == TYPE_VIDEO) cueTrack = t.number;
   }

   public void writeHeader() throws IOException {
      if (tracks.isEmpty()) throw new IllegalStateException("No tracks");

      buf.reset();
      EbmlWriter h = new EbmlWriter();
      h.writeUInt(EBML_VERSION, 1);
      h.writeUInt(EBML_READ_VERSION, 1);
      h.writeUInt(EBML_MAX_ID_LEN, 4);
      h.writeUInt(EBML_MAX_SIZE_LEN, 8);
      h.writeString(DOC_TYPE, "matroska");
      h.writeUInt(DOC_TYPE_VERSION, 4);
      h.writeUInt(DOC_TYPE_READ_VER, 2);
      buf.writeMaster(EBML, h.toByteArray());
      buf.writeTo(file);

      // Segment with a patchable size: the length is not known until close.
      EbmlWriter s = new EbmlWriter();
      s.writeId(SEGMENT);
      s.writeTo(file);
      segmentSizeOffset = file.getFilePointer();
      file.write(EbmlWriter.sizeFixedBytes(0, SEGMENT_SIZE_LEN));
      segmentDataStart = file.getFilePointer();

      // SeekHead, so a player finds Info, Tracks, Cues and any metadata without scanning the
      // file. Positions are not known yet, so each is reserved at a fixed width and patched
      // in close() - the same trick Duration uses below.
      writeSeekHead();

      // Info. Duration is patched at close, so it is written at a fixed width.
      EbmlWriter info = new EbmlWriter();
      info.writeUInt(TIMESTAMP_SCALE, 1000000);      // 1 ms
      // A segment has to be identifiable to be linked or referred to, and every other muxer
      // writes one. Random per file, which is the point - two remuxes of the same recording
      // are two segments.
      byte[] uid = new byte[16];
      RANDOM.nextBytes(uid);
      info.writeBinary(SEGMENT_UID, uid);
      if (title != null) info.writeString(SEGMENT_TITLE, title);
      // Epoch for a Matroska date is 2001-01-01T00:00:00 UTC, not 1970.
      if (dateUtcMs != null) {
         info.writeDate(DATE_UTC, (dateUtcMs.longValue() - MATROSKA_EPOCH_MS) * 1000000L);
      }
      info.writeString(MUXING_APP, "kmttg");
      info.writeString(WRITING_APP, "kmttg");
      byte[] infoHead = info.toByteArray();
      long infoPos = file.getFilePointer() - segmentDataStart;
      buf.reset();
      buf.writeId(INFO);
      buf.writeSize(infoHead.length + 11);           // + Duration element (2 id + 1 size + 8)
      buf.writeRaw(infoHead);
      buf.writeTo(file);
      EbmlWriter d = new EbmlWriter();
      d.writeId(DURATION);
      d.writeSize(8);
      d.writeTo(file);
      durationOffset = file.getFilePointer();
      file.write(new byte[8]);

      // Tracks
      EbmlWriter all = new EbmlWriter();
      for (Track t : tracks) {
         all.writeMaster(TRACK_ENTRY, trackEntry(t));
      }
      long tracksPos = file.getFilePointer() - segmentDataStart;
      buf.reset();
      buf.writeMaster(TRACKS, all.toByteArray());
      buf.writeTo(file);

      headerWritten = true;
      patchSeek(INFO, infoPos);
      patchSeek(TRACKS, tracksPos);
   }

   // Each Seek entry is a fixed size so its position can be patched once known.
   private void writeSeekHead() throws IOException {
      List<Long> ids = new ArrayList<Long>();
      ids.add(INFO);
      ids.add(TRACKS);
      ids.add(CUES);
      if (! tags.isEmpty())        ids.add(TAGS);
      if (! chapters.isEmpty())    ids.add(CHAPTERS);
      if (! attachments.isEmpty()) ids.add(ATTACHMENTS);

      EbmlWriter body = new EbmlWriter();
      for (Long id : ids) {
         EbmlWriter e = new EbmlWriter();
         e.writeBinary(SEEK_ID, idBytes(id));
         e.writeId(SEEK_POSITION);
         e.writeSize(8);
         e.writeRaw(new byte[8]);
         body.writeMaster(SEEK, e.toByteArray());
      }
      byte[] bytes = body.toByteArray();

      long seekHeadStart = file.getFilePointer();
      buf.reset();
      buf.writeMaster(SEEK_HEAD, bytes);
      buf.writeTo(file);

      // Walk the bytes just written to record where each SeekPosition value landed.
      long base = seekHeadStart + idLength(SEEK_HEAD) + sizeLength(bytes.length);
      int offset = 0;
      for (Long id : ids) {
         int seekIdLen = idLength(SEEK_ID) + 1 + idBytes(id).length;
         int posValue = offset + idLength(SEEK) + 1 + seekIdLen + idLength(SEEK_POSITION) + 1;
         seekPatch.add(new long[]{base + posValue, id});
         offset += idLength(SEEK) + 1 + seekIdLen + idLength(SEEK_POSITION) + 1 + 8;
      }
   }

   private void patchSeek(long elementId, long position) throws IOException {
      for (long[] p : seekPatch) {
         if (p[1] == elementId) {
            long here = file.getFilePointer();
            file.seek(p[0]);
            for (int i = 7; i >= 0; i--) file.write((int)(position >> (i * 8)) & 0xFF);
            file.seek(here);
            return;
         }
      }
   }

   private static byte[] idBytes(long id) {
      int len = idLength(id);
      byte[] b = new byte[len];
      for (int i = 0; i < len; i++) b[i] = (byte)(id >> ((len - 1 - i) * 8));
      return b;
   }

   // The SeekHead patch offsets are computed from these, so they have to be the same rules
   // EbmlWriter actually writes by rather than a second copy of them.
   private static int idLength(long id)  { return EbmlWriter.idLength(id); }
   private static int sizeLength(long v) { return EbmlWriter.sizeLength(v); }

   private byte[] trackEntry(Track t) {
      EbmlWriter e = new EbmlWriter();
      e.writeUInt(TRACK_NUMBER, t.number);
      e.writeUInt(TRACK_UID, t.number);
      e.writeUInt(TRACK_TYPE, t.type);
      e.writeUInt(FLAG_LACING, 0);
      if (! t.isDefault) e.writeUInt(FLAG_DEFAULT, 0);
      if (t.name != null && ! t.name.isEmpty()) e.writeString(TRACK_NAME, t.name);
      e.writeString(CODEC_ID, t.codecId);
      e.writeString(LANGUAGE, t.language);
      if (t.codecPrivate != null && t.codecPrivate.length > 0) {
         e.writeBinary(CODEC_PRIVATE, t.codecPrivate);
      }
      if (t.defaultDurationNs > 0) {
         e.writeUInt(DEFAULT_DURATION, t.defaultDurationNs);
      }
      if (t.type == TYPE_VIDEO) {
         EbmlWriter v = new EbmlWriter();
         v.writeUInt(PIXEL_WIDTH, t.width);
         v.writeUInt(PIXEL_HEIGHT, t.height);
         if (t.displayWidth > 0)  v.writeUInt(DISPLAY_WIDTH, t.displayWidth);
         if (t.displayHeight > 0) v.writeUInt(DISPLAY_HEIGHT, t.displayHeight);
         if (t.displayUnit > 0)   v.writeUInt(DISPLAY_UNIT, t.displayUnit);
         if (t.interlaced > 0)    v.writeUInt(FLAG_INTERLACED, t.interlaced);
         if (t.fieldOrder >= 0)   v.writeUInt(FIELD_ORDER, t.fieldOrder);
         e.writeMaster(VIDEO, v.toByteArray());
      } else if (t.type == TYPE_AUDIO) {
         EbmlWriter a = new EbmlWriter();
         a.writeFloat64(SAMPLING_FREQ, t.sampleRate);
         a.writeUInt(CHANNELS, t.channels);
         e.writeMaster(AUDIO, a.toByteArray());
      }
      return e.toByteArray();
   }

   // Samples arrive in decode order with presentation timestamps, which is what Matroska
   // stores: there is no DTS in a SimpleBlock and the decoder does its own reordering.
   public void addSample(int track, long timestampMs, boolean keyframe, byte[] data)
         throws IOException {
      if (! headerWritten) writeHeader();
      if (shouldCloseCluster(track, timestampMs, keyframe)) flushCluster();

      Block b = new Block();
      b.track = track;
      b.ts = timestampMs;
      b.key = keyframe;
      b.data = data;
      pending.add(b);
      if (timestampMs < clusterMin) clusterMin = timestampMs;
      if (timestampMs > clusterMax) clusterMax = timestampMs;
      if (timestampMs > lastTimestamp) lastTimestamp = timestampMs;
   }

   private boolean shouldCloseCluster(int track, long ts, boolean keyframe) {
      if (pending.isEmpty()) return false;
      // Start a cluster on a video keyframe so that a Cue points at a seekable boundary.
      if (keyframe && track == cueTrack) return true;
      // Span, not distance from the minimum. An open GOP puts B pictures with LOWER
      // presentation times after the I picture in decode order, so closing whenever a block
      // fell below the running minimum made every I picture its own cluster - 27,307
      // clusters for 9,839 keyframes on a three hour recording. The cluster timestamp is the
      // minimum over all its blocks, computed at flush, so a late lower value is harmless.
      long min = Math.min(ts, clusterMin);
      long max = Math.max(ts, clusterMax);
      return max - min >= MAX_CLUSTER_MS;
   }

   // Cluster timestamp is the minimum in the cluster, not the first block's: with B pictures
   // the arrival order is decode order, so presentation times inside a cluster are not sorted.
   private void flushCluster() throws IOException {
      if (pending.isEmpty()) return;
      long base = clusterMin;

      EbmlWriter c = new EbmlWriter();
      c.writeUInt(CLUSTER_TIMESTAMP, base);
      // The Cue must name the keyframe's own time, not the cluster's. Those were the same
      // while every keyframe opened its own cluster; now a cluster can begin with a B
      // picture displayed earlier, and a seek would land before the keyframe.
      boolean cued = false;
      long cueTime = base;
      for (Block b : pending) {
         long rel = b.ts - base;
         if (rel > Short.MAX_VALUE || rel < Short.MIN_VALUE) {
            throw new IOException("Block offset " + rel + " ms does not fit a SimpleBlock");
         }
         EbmlWriter blk = new EbmlWriter();
         blk.writeSize(b.track);
         blk.writeByte((int)(rel >> 8));
         blk.writeByte((int)rel);
         blk.writeByte(b.key ? 0x80 : 0x00);
         blk.writeRaw(b.data);
         c.writeMaster(SIMPLE_BLOCK, blk.toByteArray());
         if (! cued && b.key && b.track == cueTrack) {
            cued = true;
            cueTime = b.ts;
         }
      }

      long clusterPos = file.getFilePointer() - segmentDataStart;
      buf.reset();
      buf.writeMaster(CLUSTER, c.toByteArray());
      buf.writeTo(file);

      if (cued) cues.add(new long[]{cueTime, cueTrack, clusterPos});
      pending.clear();
      clusterMin = Long.MAX_VALUE;
      clusterMax = Long.MIN_VALUE;
   }

   public void close() throws IOException {
      if (! headerWritten) writeHeader();
      flushCluster();

      EbmlWriter all = new EbmlWriter();
      for (long[] cue : cues) {
         EbmlWriter pos = new EbmlWriter();
         pos.writeUInt(CUE_TRACK, cue[1]);
         pos.writeUInt(CUE_CLUSTER_POS, cue[2]);
         EbmlWriter pt = new EbmlWriter();
         pt.writeUInt(CUE_TIME, cue[0]);
         pt.writeMaster(CUE_TRACK_POS, pos.toByteArray());
         all.writeMaster(CUE_POINT, pt.toByteArray());
      }
      long cuesPos = file.getFilePointer() - segmentDataStart;
      buf.reset();
      buf.writeMaster(CUES, all.toByteArray());
      buf.writeTo(file);
      patchSeek(CUES, cuesPos);

      writeTags();
      writeChapters();
      writeAttachments();

      long end = file.getFilePointer();
      file.seek(durationOffset);
      file.writeDouble((double)lastTimestamp);
      file.seek(segmentSizeOffset);
      file.write(EbmlWriter.sizeFixedBytes(end - segmentDataStart, SEGMENT_SIZE_LEN));
      file.seek(end);
      file.close();
   }

   // Tags may be written after the clusters, so metadata costs no second pass and does not
   // touch the header - unlike Tracks, which is fixed before the first sample.
   private void writeTags() throws IOException {
      if (tags.isEmpty()) return;
      // One Tag element per target level, each carrying its SimpleTags.
      List<Integer> levels = new ArrayList<Integer>();
      for (Tag t : tags) if (! levels.contains(t.target)) levels.add(t.target);

      EbmlWriter all = new EbmlWriter();
      for (Integer level : levels) {
         EbmlWriter tag = new EbmlWriter();
         EbmlWriter targets = new EbmlWriter();
         targets.writeUInt(TARGET_TYPE_VALUE, level);
         tag.writeMaster(TARGETS, targets.toByteArray());
         for (Tag t : tags) {
            if (t.target != level) continue;
            EbmlWriter st = new EbmlWriter();
            st.writeString(TAG_NAME, t.name);
            st.writeString(TAG_STRING, t.value);
            tag.writeMaster(SIMPLE_TAG, st.toByteArray());
         }
         all.writeMaster(TAG, tag.toByteArray());
      }
      long pos = file.getFilePointer() - segmentDataStart;
      buf.reset();
      buf.writeMaster(TAGS, all.toByteArray());
      buf.writeTo(file);
      patchSeek(TAGS, pos);
   }

   // Clamping lives here rather than in the caller because chapters have to be declared
   // before the header - the SeekHead reserves their entry - and the duration is not known
   // until the last cluster is written. SkipMode offsets come from a window that runs past
   // both ends of the recording, so without this a player gets chapters outside the file.
   private void writeChapters() throws IOException {
      if (chapters.isEmpty()) return;

      // Clamp first, then drop what collapsed: a SkipMode window overhangs both ends of the
      // recording, so without this a truncated or short file ends up with a run of zero
      // length chapters in the menu.
      List<Chapter> kept = new ArrayList<Chapter>();
      for (Chapter c : chapters) {
         long start = clampTime(c.startMs, 0);
         long end   = clampTime(c.endMs, start);
         if (end > start) kept.add(new Chapter(start, end, c.name));
      }
      // Something has to be written even if everything collapsed: the SeekHead reserved an
      // entry for this element back in the header, and an empty Chapters would leave that
      // entry pointing at zero. Deliberately NOT the first chapter's name: everything
      // collapsing means the marks did not describe this recording, and calling the whole
      // file "Segment 1" states something about it that is not true.
      if (kept.isEmpty()) {
         kept.add(new Chapter(0, lastTimestamp, "Chapter 1"));
      }

      EbmlWriter atoms = new EbmlWriter();
      long uid = 1;
      for (Chapter c : kept) {
         long start = c.startMs;
         long end   = c.endMs;

         EbmlWriter display = new EbmlWriter();
         display.writeString(CHAP_STRING, c.name);
         display.writeString(CHAP_LANGUAGE, "eng");

         EbmlWriter atom = new EbmlWriter();
         atom.writeUInt(CHAPTER_UID, uid++);
         atom.writeUInt(CHAPTER_START, start * 1000000);
         atom.writeUInt(CHAPTER_END, end * 1000000);
         atom.writeUInt(CHAPTER_HIDDEN, 0);
         atom.writeUInt(CHAPTER_ENABLED, 1);
         atom.writeMaster(CHAPTER_DISPLAY, display.toByteArray());
         atoms.writeMaster(CHAPTER_ATOM, atom.toByteArray());
      }

      EbmlWriter edition = new EbmlWriter();
      edition.writeUInt(EDITION_UID, 1);
      edition.writeUInt(EDITION_HIDDEN, 0);
      edition.writeUInt(EDITION_DEFAULT, 1);
      edition.writeRaw(atoms.toByteArray());

      EbmlWriter all = new EbmlWriter();
      all.writeMaster(EDITION_ENTRY, edition.toByteArray());

      long pos = file.getFilePointer() - segmentDataStart;
      buf.reset();
      buf.writeMaster(CHAPTERS, all.toByteArray());
      buf.writeTo(file);
      patchSeek(CHAPTERS, pos);
   }

   // Chapter times are bounded by the recording: at least floor, at most the last timestamp.
   private long clampTime(long ms, long floor) {
      long v = ms < floor ? floor : ms;
      return v > lastTimestamp ? lastTimestamp : v;
   }

   // Artwork is an attachment in Matroska rather than a tag, which is why cover art does not
   // go through the tag machinery at all.
   private void writeAttachments() throws IOException {
      if (attachments.isEmpty()) return;
      EbmlWriter all = new EbmlWriter();
      long uid = 1;
      for (Attachment a : attachments) {
         EbmlWriter f = new EbmlWriter();
         f.writeString(FILE_NAME, a.name);
         f.writeString(FILE_MIME_TYPE, a.mimeType);
         f.writeUInt(FILE_UID, uid++);
         f.writeBinary(FILE_DATA, a.data);
         all.writeMaster(ATTACHED_FILE, f.toByteArray());
      }
      long pos = file.getFilePointer() - segmentDataStart;
      buf.reset();
      buf.writeMaster(ATTACHMENTS, all.toByteArray());
      buf.writeTo(file);
      patchSeek(ATTACHMENTS, pos);
   }

   // Abandon the file, releasing the handle without finalising. close() cannot serve here:
   // it writes a header, and a muxer with no tracks throws rather than writing one.
   public void discard() throws IOException {
      file.close();
   }

   public long getDurationMs() { return lastTimestamp; }
   public int  getCueCount()   { return cues.size(); }
}
