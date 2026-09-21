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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.tivo.kmttg.mux.codec.Ac3Parser;
import com.tivo.kmttg.mux.codec.H264Parser;
import com.tivo.kmttg.mux.codec.Mpeg2Parser;
import com.tivo.kmttg.mux.mkv.MkvMuxer;

import net.straylightlabs.tivolibre.DecodeResult;
import net.straylightlabs.tivolibre.DiscontinuityReason;
import net.straylightlabs.tivolibre.ElementaryStreamInfo;
import net.straylightlabs.tivolibre.FrameSink;
import net.straylightlabs.tivolibre.PesPayload;
import net.straylightlabs.tivolibre.TivoMetadata;

// Turns the elementary streams TivoLibre delivers into an MKV file.
//
// Matroska puts Tracks ahead of the first cluster and will not let them be revised, so
// nothing can be written until the track list is final AND every track's parameters are
// known. Those parameters live in the streams themselves - dimensions in an MPEG-2 sequence
// header, sample rate in an AC-3 syncframe - so the opening payloads are buffered until
// enough has arrived to describe every track, and only then is the header written.
public class MuxSink implements FrameSink {
   // How much to buffer while waiting for track parameters. Wider than one GOP so that an
   // open GOP's leading B pictures are inside the window when the time base is locked.
   private static final int  MAX_BUFFERED   = 2000;
   private static final long MAX_BUFFER_PTS = 5 * TimeBase.PTS_HZ;

   private static class TrackState {
      int pid;
      int streamType;
      MkvMuxer.Track track = new MkvMuxer.Track();
      boolean configured;
      long samples;
      boolean isAvc;
      List<byte[]> sps = new ArrayList<byte[]>();
      List<byte[]> pps = new ArrayList<byte[]>();
   }

   private final File outFile;
   private final TimeBase time = new TimeBase();
   private final Map<Integer,TrackState> tracks = new LinkedHashMap<Integer,TrackState>();
   // The payload with the PTS already unwrapped. unwrap() carries per-PID state, so running
   // the buffer through it again at start() would count a wrap inside the window twice.
   private static class Buffered {
      final PesPayload payload;
      final long pts;
      Buffered(PesPayload payload, long pts) { this.payload = payload; this.pts = pts; }
   }

   private final List<Buffered> buffered = new ArrayList<Buffered>();

   private MkvMuxer muxer;
   private TivoMetadata metadata;
   private MetadataTags.Supplement supplement;
   private byte[] coverArt;
   private List<MkvMuxer.Chapter> chapters;
   private String coverName;
   private String coverMime;
   private boolean started;
   private boolean failed;
   private String failure;
   private long minPtsSeen = Long.MAX_VALUE;
   private long discarded;
   private long finalDurationMs;
   private int finalCueCount;
   private long lastMs;
   private final List<String> notes = new ArrayList<String>();
   // Pids that are not being carried, so each is only complained about once. The ones the
   // header deliberately left out go in here too: the program list is cumulative, so the PMT
   // that names a genuinely late stream names those again beside it, and reporting them as
   // having appeared after the header says something about them that is not true.
   private final Set<Integer> notCarried = new LinkedHashSet<Integer>();

   public MuxSink(File outFile) {
      this.outFile = outFile;
   }

   // Fields kmttg knows that the recording does not carry at all - callsign, season and
   // episode number. Set before the first payload or they miss the header.
   public void setSupplement(MetadataTags.Supplement extra) {
      this.supplement = extra;
   }

   // Poster art, as the bytes that were downloaded. Matroska carries this as an attachment
   // rather than a tag, so it does not go through MetadataTags at all. Set before the first
   // payload: attachments reserve a SeekHead entry and so must precede the header.
   public void setCoverArt(String name, String mimeType, byte[] data) {
      this.coverName = name;
      this.coverMime = mimeType;
      this.coverArt = data;
   }

   // SkipMode segment marks, already on the recording's own clock. Set before the first
   // payload: chapters reserve a SeekHead entry and so must precede the header.
   public void setChapters(List<MkvMuxer.Chapter> list) {
      this.chapters = list;
   }

   // Only ever called for a .TiVo source; a plain .ts carries no metadata and the library
   // rightly says nothing rather than handing over an empty one.
   public void onMetadata(TivoMetadata metadata) {
      this.metadata = metadata;
   }

   public void onProgram(List<ElementaryStreamInfo> streams) {
      if (started || failed) {
         // Tracks are fixed once written, so a stream that first appears mid recording - a
         // second audio language, say - cannot be carried. Said out loud rather than dropped
         // in silence: every payload on that pid then vanishes with nothing to explain it.
         for (ElementaryStreamInfo info : streams) {
            if (! tracks.containsKey(info.getPid()) && notCarried.add(info.getPid())) {
               notes.add(String.format("pid=0x%04x appeared after the header; not carried",
                  info.getPid()));
            }
         }
         return;
      }
      for (ElementaryStreamInfo info : streams) {
         if (tracks.containsKey(info.getPid())) continue;
         TrackState t = newTrack(info);
         if (t != null) tracks.put(info.getPid(), t);
         // A type we cannot describe carries no payloads worth explaining, so it is passed
         // over in silence - but it is on the list, and a later PMT repeats it.
         else notCarried.add(info.getPid());
      }
   }

   private TrackState newTrack(ElementaryStreamInfo info) {
      TrackState t = new TrackState();
      t.pid = info.getPid();
      t.streamType = info.getStreamType();
      t.track.number = tracks.size() + 1;
      t.track.language = language(info.getDescriptors());
      switch (info.getStreamType()) {
         case 0x01:
         case 0x02:
            t.track.type = MkvMuxer.TYPE_VIDEO;
            t.track.codecId = "V_MPEG2";
            return t;
         case 0x1B:
            t.track.type = MkvMuxer.TYPE_VIDEO;
            t.track.codecId = "V_MPEG4/ISO/AVC";
            t.isAvc = true;
            return t;
         case 0x81:
            t.track.type = MkvMuxer.TYPE_AUDIO;
            t.track.codecId = "A_AC3";
            return t;
         default:
            return null;   // private data and anything else we cannot describe
      }
   }

   // ISO 639 language descriptor, tag 0x0a. The only source for a track language.
   private static String language(byte[] d) {
      if (d == null) return "und";
      int i = 0;
      while (i + 2 <= d.length) {
         int tag = d[i] & 0xFF;
         int len = d[i+1] & 0xFF;
         if (i + 2 + len > d.length) break;
         if (tag == 0x0A && len >= 3) {
            return new String(d, i + 2, 3).toLowerCase();
         }
         i += 2 + len;
      }
      return "und";
   }

   public void onPesPayload(PesPayload payload) {
      if (failed) return;
      TrackState t = tracks.get(payload.getPid());
      if (t == null) return;
      if (! payload.hasPts()) { discarded++; return; }

      if (! started) {
         long pts = time.unwrap(payload.getPid(), payload.getPts());
         buffered.add(new Buffered(payload, pts));
         configure(t, payload);
         if (pts < minPtsSeen) minPtsSeen = pts;
         if (readyToStart()) start();
         return;
      }
      emit(t, payload, time.unwrap(payload.getPid(), payload.getPts()));
   }

   private void configure(TrackState t, PesPayload payload) {
      if (t.configured) return;
      byte[] d = payload.getData();
      if (t.isAvc) {
         // avcC needs both parameter sets, and they do not have to arrive in the same unit.
         for (H264Parser.NalUnit n : H264Parser.findNals(d)) {
            byte[] nal = new byte[n.length];
            System.arraycopy(d, n.offset, nal, 0, n.length);
            // Only keep a parameter set that parses. Storing the first one unconditionally
            // meant a single damaged SPS early in the stream locked the track out for good,
            // because every later good one was skipped by the isEmpty() test.
            if (n.type == H264Parser.NAL_SPS && t.sps.isEmpty()
                  && H264Parser.parseSps(nal, 0, nal.length) != null) {
               t.sps.add(nal);
            }
            if (n.type == H264Parser.NAL_PPS && t.pps.isEmpty()) t.pps.add(nal);
         }
         if (t.sps.isEmpty() || t.pps.isEmpty()) return;
         H264Parser.SequenceParameterSet sps =
            H264Parser.parseSps(t.sps.get(0), 0, t.sps.get(0).length);
         if (sps == null) return;
         t.track.width  = sps.width;
         t.track.height = sps.height;
         // H.264 carries the aspect in the SPS VUI, not in anything setDisplaySize knows.
         // State it as a ratio rather than a rounded pixel size: 720 at 40:33 is 872.7 px,
         // and rounding that to 873 turns a clean 20:11 into 291:160.
         if (sps.sarWidth != sps.sarHeight) {
            long w = (long)sps.width * sps.sarWidth;
            long h = (long)sps.height * sps.sarHeight;
            long g = gcd(w, h);
            t.track.displayWidth  = (int)(w / g);
            t.track.displayHeight = (int)(h / g);
            t.track.displayUnit   = 3;          // display aspect ratio
         }
         // The SPS says whether fields can be coded at all; which field leads is only in a
         // pic_struct SEI, so the order is left undetermined rather than guessed.
         t.track.interlaced = sps.frameMbsOnly ? 2 : 1;
         if (sps.frameMbsOnly) t.track.fieldOrder = 0;
         t.track.codecPrivate = H264Parser.buildAvcC(t.sps, t.pps);
         t.configured = t.track.codecPrivate != null;
         return;
      }
      if (t.track.type == MkvMuxer.TYPE_VIDEO) {
         Mpeg2Parser.SequenceHeader h = Mpeg2Parser.findSequenceHeader(d);
         if (h == null || h.width <= 0 || h.height <= 0) return;
         t.track.width  = h.width;
         t.track.height = h.height;
         setDisplaySize(t.track, h.aspectRatioCode);
         if (h.frameRateNum > 0) {
            t.track.defaultDurationNs = 1000000000L * h.frameRateDen / h.frameRateNum;
         }
         t.track.interlaced = h.progressive ? 2 : 1;
         if (h.progressive) {
            t.track.fieldOrder = 0;
         } else {
            // Per picture in MPEG-2, but a broadcast holds it constant, so the first coded
            // picture speaks for the track. Absent when this unit carried no extension.
            int tff = Mpeg2Parser.topFieldFirst(d);
            if (tff >= 0) t.track.fieldOrder = tff == 1 ? 1 : 6;
         }
         t.configured = true;
      } else {
         List<Ac3Parser.SyncFrame> frames = Ac3Parser.split(d);
         if (frames.isEmpty()) return;
         Ac3Parser.SyncFrame f = frames.get(0);
         t.track.sampleRate = f.sampleRate;
         t.track.channels   = f.channels;
         t.configured = true;
      }
   }

   // aspect_ratio_information gives a display aspect, not a pixel one, so the display size is
   // derived from the height. Square pixels need no hint.
   private static void setDisplaySize(MkvMuxer.Track track, int aspectCode) {
      if (aspectCode == 2) {
         track.displayWidth  = track.height * 4 / 3;
         track.displayHeight = track.height;
      } else if (aspectCode == 3) {
         track.displayWidth  = track.height * 16 / 9;
         track.displayHeight = track.height;
      }
   }

   private static long gcd(long a, long b) {
      while (b != 0) { long t = a % b; a = b; b = t; }
      return a == 0 ? 1 : a;
   }

   // What a player shows beside the audio track. The codec is already stated on its own, so
   // this names the layout, which is the part a viewer actually chooses on.
   private static String channelLayout(int channels) {
      switch (channels) {
         case 1:  return "Mono";
         case 2:  return "Stereo";
         case 6:  return "Surround 5.1";
         case 8:  return "Surround 7.1";
         default: return channels > 0 ? channels + " channels" : null;
      }
   }

   private int audioTrackCount() {
      int n = 0;
      for (TrackState t : tracks.values()) {
         if (t.track.type == MkvMuxer.TYPE_AUDIO) n++;
      }
      return n;
   }

   private boolean readyToStart() {
      for (TrackState t : tracks.values()) {
         if (! t.configured) {
            // Give up waiting rather than buffer a whole recording for a track that never
            // describes itself; that track is then dropped in start().
            return buffered.size() >= MAX_BUFFERED
               || (minPtsSeen != Long.MAX_VALUE && bufferSpan() > MAX_BUFFER_PTS);
         }
      }
      return true;
   }

   private long bufferSpan() {
      return buffered.get(buffered.size() - 1).pts - minPtsSeen;
   }

   private void start() {
      try {
         muxer = new MkvMuxer(outFile);
         // Tags reserve a SeekHead entry, so they must be declared before the header.
         muxer.addTags(MetadataTags.build(metadata, supplement));
         // Segment Information, which is not a tag. Falling back to now for the date rather
         // than leaving it out: a .ts source carries no metadata at all, and the muxing time
         // is what DateUTC means anyway.
         muxer.setTitle(MetadataTags.segmentTitle(metadata, supplement));
         Long recorded = MetadataTags.recordedDate(metadata);
         muxer.setDate(recorded == null ? System.currentTimeMillis() : recorded.longValue());
         if (chapters != null && ! chapters.isEmpty()) {
            muxer.addChapters(chapters);
         }
         if (coverArt != null && coverArt.length > 0) {
            muxer.addAttachment(coverName, coverMime, coverArt);
         }
         List<Integer> drop = new ArrayList<Integer>();
         for (Map.Entry<Integer,TrackState> e : tracks.entrySet()) {
            if (! e.getValue().configured) {
               notes.add(String.format("pid=0x%04x never described itself; dropped", e.getKey()));
               drop.add(e.getKey());
            }
         }
         for (Integer pid : drop) {
            tracks.remove(pid);
            notCarried.add(pid);
         }
         if (tracks.isEmpty()) {
            fail("No describable tracks found");
            return;
         }
         // Only where the PMT said nothing, which on a TiVo recording is always: the language
         // descriptor is stripped, so without this every audio track is "und" and a player
         // choosing between tracks has nothing to go on.
         //
         // Only when there is exactly one audio track, though. The guide text says what the
         // program is in, not what each track is in, so on a broadcast carrying a SAP it would
         // label both tracks the same - and a player set to prefer that language would then
         // pick whichever came first rather than the one it wants. "und" on both is a worse
         // label but a better answer.
         String lang = MetadataTags.audioLanguage(metadata);
         if (lang != null && audioTrackCount() == 1) {
            for (TrackState t : tracks.values()) {
               if (t.track.type == MkvMuxer.TYPE_AUDIO && "und".equals(t.track.language)) {
                  t.track.language = lang;
               }
            }
         }
         // The first track of each kind is the default one. Matroska assumes default when the
         // flag is absent, so without this a second audio track would be announced as being
         // just as default as the first.
         boolean haveVideo = false, haveAudio = false;
         for (TrackState t : tracks.values()) {
            if (t.track.type == MkvMuxer.TYPE_VIDEO) {
               t.track.isDefault = ! haveVideo;
               haveVideo = true;
            } else if (t.track.type == MkvMuxer.TYPE_AUDIO) {
               t.track.isDefault = ! haveAudio;
               haveAudio = true;
               if (t.track.name == null) t.track.name = channelLayout(t.track.channels);
            }
         }
         // Renumber BEFORE adding: addTrack latches the first video track's number as the cue
         // track, so numbering afterwards left it pointing at whatever track inherited that
         // number - an audio one when a dropped track preceded the video in the PMT.
         int n = 1;
         for (TrackState t : tracks.values()) t.track.number = n++;
         for (TrackState t : tracks.values()) muxer.addTrack(t.track);

         time.lock(minPtsSeen);
         muxer.writeHeader();
         started = true;
         for (Buffered b : buffered) {
            TrackState t = tracks.get(b.payload.getPid());
            if (t != null) emit(t, b.payload, b.pts);
         }
         buffered.clear();
      } catch (IOException e) {
         fail("Could not start the muxer: " + e.getMessage());
      }
   }

   private void emit(TrackState t, PesPayload payload, long pts) {
      if (failed) return;
      try {
         byte[] d = payload.getData();
         if (t.isAvc) {
            // One start code scan for both answers below: this is every byte of every video
            // payload, so walking it twice is a second pass over the whole recording.
            List<H264Parser.NalUnit> nals = H264Parser.findNals(d);
            // Matroska has no Annex-B form, so every sample is rewritten length prefixed.
            byte[] sample = H264Parser.toLengthPrefixed(d, nals);
            // A repeated parameter set access unit - AUD, SPS, PPS and no slice - converts to
            // nothing, and Matroska forbids an empty block. Broadcast streams send these
            // before random access points, so this is the common case, not a corner one.
            if (sample.length == 0) return;
            lastMs = time.toMillis(pts);
            muxer.addSample(t.track.number, lastMs, H264Parser.isKeyframe(d, nals), sample);
            t.samples++;
         } else if (t.track.type == MkvMuxer.TYPE_VIDEO) {
            lastMs = time.toMillis(pts);
            muxer.addSample(t.track.number, lastMs, Mpeg2Parser.isKeyframe(d), d);
            t.samples++;
         } else {
            // One payload unit holds several AC-3 syncframes sharing a PTS. Each is its own
            // block, timed from the unit's PTS by its position, so the anchor re-establishes
            // at every unit rather than accumulating drift.
            List<Ac3Parser.SyncFrame> frames = Ac3Parser.split(d);
            for (int i = 0; i < frames.size(); i++) {
               Ac3Parser.SyncFrame f = frames.get(i);
               long offset = (long)i * Ac3Parser.SAMPLES_PER_FRAME * TimeBase.PTS_HZ / f.sampleRate;
               byte[] sample = new byte[f.length];
               System.arraycopy(d, f.offset, sample, 0, f.length);
               muxer.addSample(t.track.number, time.toMillis(pts + offset), true, sample);
               t.samples++;
            }
         }
      } catch (IOException e) {
         fail("Write failed: " + e.getMessage());
      }
   }

   public void onDiscontinuity(int pid, DiscontinuityReason reason) {
      notes.add(String.format("pid=0x%04x at ~%s %s", pid, TimeBase.hms(lastMs), reason));
   }

   public void onEnd(DecodeResult result) {
      // A recording shorter than the buffering window never reached start(), so without this
      // it produces no file and no explanation - just "remux failed on: X".
      if (muxer == null && ! failed && ! buffered.isEmpty()) start();
      if (muxer == null) return;
      if (failed) {
         // close() would call writeHeader(), which throws when no track was describable -
         // an unchecked exception escaping a FrameSink callback into the decode loop.
         abort();
         return;
      }
      try {
         muxer.close();
         finalDurationMs = muxer.getDurationMs();
         finalCueCount = muxer.getCueCount();
         muxer = null;
      } catch (IOException e) {
         fail("Could not finish the file: " + e.getMessage());
         abort();
      }
   }

   // Release the output file without finalising it. Safe to call more than once, and safe
   // when the muxer never got a track, which is exactly when close() cannot be used.
   public void abort() {
      if (muxer == null) return;
      try {
         muxer.discard();
      } catch (IOException e) {
         // nothing useful left to do; the file is being abandoned either way
      }
      muxer = null;
   }

   private void fail(String why) {
      if (! failed) {
         failed = true;
         failure = why;
      }
   }

   public boolean isFailed()             { return failed; }
   public String getFailure()            { return failure; }
   public List<String> getNotes()        { return notes; }
   public long getDiscardedNoPts()       { return discarded; }
   public long getClampedTimestamps()    { return time.getClamped(); }
   public long getDurationMs()           { return finalDurationMs; }
   public int  getCueCount()             { return finalCueCount; }

   public Map<Integer,Long> getSampleCounts() {
      Map<Integer,Long> out = new LinkedHashMap<Integer,Long>();
      for (Map.Entry<Integer,TrackState> e : tracks.entrySet()) {
         out.put(e.getKey(), e.getValue().samples);
      }
      return out;
   }
}
