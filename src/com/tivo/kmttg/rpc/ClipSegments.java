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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

// Where a recording's show segments come from, in preference order. Both sources give
// millisecond offsets on the recording's own clock, so a caller can use either without
// knowing which it got.
//
//  1. The AutoSkip table, when visualDetect has already scanned this recording. Nothing is
//     more authoritative and it costs no network at all.
//  2. clipMetadataAdjust, which returns the clipMetadata already aligned to one recording.
//     This is a tivo.com call: the local box answers routeNotFound, and so does the cloud
//     unless the request carries a bodyId, which is what makes kmttg emit the BodyId header
//     middlemind routes on. So it needs tivo.com credentials and an away mode connection.
//
// Anything else and there are no segments. A plain clipMetadataSearch is deliberately not a
// third option: its offsets are on the ad detector's window rather than the recording, and
// shifting them by the scheduled start lands within about a second rather than exactly.
public class ClipSegments {

   // One stretch of show, milliseconds from the first frame of the recording. A start can be
   // negative and an end can run past the recording: clipMetadataAdjust reports the whole
   // segment even where the recording does not cover it.
   public static class Segment {
      public final long startMs;
      public final long endMs;

      public Segment(long startMs, long endMs) {
         this.startMs = startMs;
         this.endMs = endMs;
      }
   }

   // Which of the two sources answered. Callers act on this: only SkipMode data is worth
   // writing back to the AutoSkip table, since AutoSkip data is where it would be written.
   public static final int SOURCE_AUTOSKIP = 1;
   public static final int SOURCE_SKIPMODE = 2;

   public static class Result {
      public final List<Segment> segments;
      public final int source;

      public Result(List<Segment> segments, int source) {
         this.segments = segments;
         this.source = source;
      }
   }

   // Returns null rather than an empty Result when there are no segments to be had, so a
   // caller can tell "nothing available" from "a show with no breaks".
   public static Result get(String tivoName, String contentId, String recordingId,
         String clipMetadataId, String offerId) {
      debug.print("tivoName=" + tivoName + " contentId=" + contentId);
      if (contentId == null) return null;

      List<Segment> saved = fromAutoSkip(contentId);
      if (saved != null) {
         log.print("Using AutoSkip cut points for chapters (" + saved.size() + " segments)");
         return new Result(saved, SOURCE_AUTOSKIP);
      }

      if (recordingId == null || clipMetadataId == null) return null;
      if (config.getTivoUsername() == null || config.getTivoPassword() == null) return null;

      Remote r = null;
      try {
         r = new Remote(tivoName, true);
         if (! r.success) return null;
         String chosen = chooseForAiring(r, contentId, offerId, clipMetadataId);
         List<Segment> adjusted = adjustPreferring(r, recordingId, chosen, clipMetadataId);
         if (adjusted == null) return null;
         log.print("Using tivo.com SkipMode data for chapters (" + adjusted.size() + " segments)");
         return new Result(adjusted, SOURCE_SKIPMODE);
      } finally {
         if (r != null) r.disconnect();
      }
   }

   // Correct pairings match to the second; one was seen a minute off. Far tighter than the
   // months between reruns.
   private static final long AIRING_SLACK_MS = 120000;

   // Which clipMetadata to adjust, when tivo.com holds more than one - it authors one per
   // airing, and the recording's own list carries no offerStartTime, so the first entry is
   // just whichever was authored earliest. A refinement only, never a veto: clipMetadataAdjust
   // re-anchors whatever clip it is handed onto the recording asked for, so another airing's
   // clip still fits and must not be refused. Matching wins ~2 s on the interior boundaries,
   // up to ~30 s on the first one, and the trailing end-tag segment.
   static String chooseForAiring(Remote r, String contentId, String offerId, String fallback) {
      long scheduled = offerStartTime(offerId);
      if (scheduled == 0) return fallback;
      try {
         JSONObject json = new JSONObject();
         json.put("contentId", contentId);
         JSONObject result = r.Command("clipMetadataSearch", json);
         if (result == null || ! result.has("clipMetadata")) return fallback;
         JSONArray a = result.getJSONArray("clipMetadata");
         String best = null;
         long bestDelta = 0;
         for (int i=0; i<a.length(); ++i) {
            JSONObject clip = a.getJSONObject(i);
            if (! clip.has("clipMetadataId") || ! clip.has("offerStartTime")) continue;
            if (clip.has("segmentType") && ! clip.getString("segmentType").equals("adSkip"))
               continue;
            long start = parseUtc(clip.getString("offerStartTime"), "yyyy-MM-dd HH:mm:ss");
            if (start == 0) continue;
            long delta = Math.abs(start - scheduled);
            if (delta > AIRING_SLACK_MS) continue;
            // First listed wins among equals. An airing is authored two to four times over
            // two days and the copies hold identical offsets, so there is nothing to gain
            // from preferring the newest - and the newest is routinely the one adjust then
            // answers "Requested clip metadata is not found" for.
            if (best == null || delta < bestDelta) {
               best = clip.getString("clipMetadataId");
               bestDelta = delta;
            }
         }
         return best == null ? fallback : best;
      } catch (JSONException e) {
         log.error("ClipSegments clipMetadataSearch - " + e.getMessage());
         return fallback;
      }
   }

   // The scheduled start embedded in an offerId, as in
   // tivo:of.ctd.10420179.2-1.terrestrial.2026-03-05-02-30-00.5400. Equals scheduledStartTime
   // but is already on the NPL entry, so matching costs no extra recordingSearch.
   private static final Pattern OFFER_TIME =
      Pattern.compile("\\.(\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2})(?:\\.|$)");

   static long offerStartTime(String offerId) {
      if (offerId == null) return 0;
      Matcher m = OFFER_TIME.matcher(offerId);
      if (! m.find()) return 0;
      return parseUtc(m.group(1), "yyyy-MM-dd-HH-mm-ss");
   }

   // Both stamps are UTC. Reading them as local time would shift every comparison by the
   // offset and match the wrong airing outside UTC. 0 for unparseable, read as "unknown".
   private static long parseUtc(String value, String pattern) {
      try {
         SimpleDateFormat sdf = new SimpleDateFormat(pattern);
         sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
         return sdf.parse(value).getTime();
      } catch (ParseException e) {
         return 0;
      }
   }

   // What visualDetect measured, stored as show segments in AutoSkip.ini. The per entry
   // offset is a playback nudge and is left out here, matching what the VPrj and EDL exports
   // in SkipImport do with the same data.
   private static List<Segment> fromAutoSkip(String contentId) {
      Stack<Hashtable<String,Long>> entries = SkipManager.getEntry(contentId);
      if (entries == null || entries.isEmpty()) return null;
      List<Segment> segments = new ArrayList<Segment>();
      for (Hashtable<String,Long> e : entries) {
         segments.add(new Segment(e.get("start"), e.get("end")));
      }
      return segments;
   }

   // Takes the Remote rather than building one so a replayed trace can drive it; the caller
   // owns the away mode connection because only middlemind serves this request. Any failure
   // is just "no segments": chapters are a bonus and must never take a remux down with them.
   static List<Segment> fromClipMetadataAdjust(Remote r, String recordingId,
         String clipMetadataId) {
      try {
         JSONObject json = new JSONObject();
         json.put("bodyId", r.bodyId_get());
         json.put("clipMetadataId", clipMetadataId);
         json.put("recordingId", recordingId);
         JSONObject clip = r.Command("clipMetadataAdjust", json);
         if (clip == null || ! clip.has("segment")) return null;

         List<Segment> segments = new ArrayList<Segment>();
         JSONArray a = clip.getJSONArray("segment");
         for (int i=0; i<a.length(); ++i) {
            JSONObject s = a.getJSONObject(i);
            long start = Long.parseLong(s.getString("startOffset"));
            long end = Long.parseLong(s.getString("endOffset"));
            // TiVo pads the list with a trailing 0 -> 0 segment on a good fraction of
            // recordings - six real segments then a seventh that is all zeros. It carries no
            // information wherever it appears, and letting it through made the ordering check
            // discard the entire recording over the padding.
            if (end <= start) continue;
            segments.add(new Segment(start, end));
         }
         return segments.isEmpty() ? null : segments;
      } catch (JSONException e) {
         log.error("ClipSegments clipMetadataAdjust - " + e.getMessage());
         return null;
      }
   }

   // Put SkipMode segments into the AutoSkip table, so the cut points reach AutoSkip playback
   // and the VPrj/EDL exports instead of being fetched again for every job. Clamped on the way
   // in: the SkipMode window overhangs the recording at both ends, and everything downstream
   // treats these as positions inside the file. A duplicate entry would be read ahead of the
   // real one, so an existing entry is replaced rather than appended to.
   public static boolean saveToAutoSkip(String contentId, String offerId, String title,
         String tivoName, List<Segment> segments, long durationMs) {
      if (segments == null || segments.isEmpty()) return false;
      // offerId is how AutoSkip recognises the recording during playback, and saveEntry writes
      // whatever it is handed - a null becomes the literal text "null" in the file, which
      // readEntry then compares against a real offerId and never matches. Such an entry shows
      // up in the Skip table and silently never fires, so no entry is the better answer.
      if (offerId == null || offerId.isEmpty()) {
         log.warn("Not saving SkipMode data for '" + title
            + "': the Now Playing entry carries no offerId");
         return false;
      }
      // Guard against data anchored to something other than this recording. Seen on about a
      // fifth of a real My Shows list: clipMetadataAdjust returns a correctly sized window
      // for the right airing - offerStartTime matches - but with its origin hours adrift, so
      // every segment lands past the end of the file. The anchor cannot be recovered from the
      // response, and writing the segments anyway would send Ad Skip to the wrong places,
      // which is worse than having no entry at all.
      String reason = rejectReason(segments, durationMs);
      if (reason != null) {
         warnRejected(title, reason);
         return false;
      }
      Stack<Hashtable<String,Long>> cuts = new Stack<Hashtable<String,Long>>();
      for (Segment s : segments) {
         long start = s.startMs < 0 ? 0 : s.startMs;
         long end = durationMs > 0 && s.endMs > durationMs ? durationMs : s.endMs;
         if (end <= start) continue;
         Hashtable<String,Long> h = new Hashtable<String,Long>();
         h.put("start", start);
         h.put("end", end);
         cuts.push(h);
      }
      if (cuts.isEmpty()) return false;
      // getEntry rather than hasEntry, which is gated on skipEnabled() and answers false with
      // AutoSkip switched off even when an entry is sitting there - the save would then append
      // a second one. Asking first also keeps removeEntry from logging "No entry found" for
      // every recording in a bulk fetch.
      if (! SkipManager.getEntry(contentId).isEmpty()) SkipManager.removeEntry(contentId);
      SkipManager.saveEntry(contentId, offerId, 0L, title, tivoName, cuts);
      return true;
   }

   // How far outside the recording a boundary may legitimately sit. The monitoring window
   // opens 30 s before the scheduled start and closes 30 s after the scheduled end, so real
   // data overhangs by about that much - measured -27.7 to -28.1 s at the head and +33 to
   // +39 s at the tail across every recording checked. Start or end padding only pulls those
   // inward. 2 minutes is generous next to that and still far tighter than the file itself.
   private static final long EDGE_SLACK_MS = 120000;

   // Whether these segments describe the recording they were fetched for at all. Shared by
   // the AutoSkip write and the chapter build, which reach it by different routes and must
   // agree - data that is refused for one is meaningless for the other.
   public static boolean fitsRecording(List<Segment> segments, long durationMs) {
      return rejectReason(segments, durationMs) == null;
   }

   // Null when the segments are usable, otherwise why they are not, phrased for a log line.
   //
   // About a fifth of a real My Shows list fails this: clipMetadataAdjust returns a window of
   // the right size for the right airing - offerStartTime matches - but with its origin hours
   // adrift, so every segment lands past the end of the file. One recording went further and
   // returned segment lengths that do not describe the airing's breaks at all, which no
   // re-anchoring could fix. Using any of it would put chapters and Ad Skip jumps in the wrong
   // places, which is worse than having neither.
   //
   // What this deliberately cannot catch: data that is structurally perfect and merely
   // anchored a few seconds off. That is not computable from the response, and it is why the
   // offsets are used exactly as TiVo reports them rather than being corrected.
   public static String rejectReason(List<Segment> segments, long durationMs) {
      if (segments == null || segments.isEmpty()) return "no segments";

      // Ordering first: it needs no duration, and a list that runs backwards or overlaps is
      // corrupt whatever the recording looks like.
      long content = 0;
      for (int i=0; i<segments.size(); ++i) {
         Segment seg = segments.get(i);
         if (seg.endMs <= seg.startMs) {
            return "segment " + (i+1) + " ends at " + SkipManager.toMinSec(seg.endMs)
               + ", at or before its start of " + SkipManager.toMinSec(seg.startMs);
         }
         if (i > 0 && seg.startMs < segments.get(i-1).endMs) {
            return "segment " + (i+1) + " starts at " + SkipManager.toMinSec(seg.startMs)
               + ", before segment " + i + " ends at "
               + SkipManager.toMinSec(segments.get(i-1).endMs);
         }
         content += seg.endMs - seg.startMs;
      }

      if (durationMs <= 0) return null;   // nothing to measure against

      long first = segments.get(0).startMs;
      long last = segments.get(segments.size()-1).endMs;
      if (first < -EDGE_SLACK_MS) {
         return "first segment starts at " + SkipManager.toMinSec(first)
            + ", too far before the recording";
      }
      if (first >= durationMs || last <= 0 || last > durationMs + EDGE_SLACK_MS) {
         return "segments span " + SkipManager.toMinSec(first) + " to "
            + SkipManager.toMinSec(last) + " but the recording is only "
            + SkipManager.toMinSec(durationMs) + " long";
      }
      // More show than there is recording means the segments describe something else, even
      // when each one happens to land inside the file.
      if (content > durationMs + EDGE_SLACK_MS) {
         return "segments claim " + SkipManager.toMinSec(content)
            + " of content but the recording is only " + SkipManager.toMinSec(durationMs);
      }
      return null;
   }

   // Segments shifted onto the recording, when the ones tivo.com returned describe the right
   // airing but start from the wrong zero.
   //
   // clipMetadataAdjust states offsets on the tuner's stream clock, whose zero is when that
   // tuner last tuned the channel. A recording that began its own tuner session is already
   // recording-relative and passes; one recorded partway into a continuous capture is out by
   // however long that capture had been running, which is what refuses about a third of a real
   // My Shows list. The shift is startStreamTime, and only the box can say it - see HlsStream.
   //
   // Returns the originals unchanged whenever it cannot help: already usable, disabled, no
   // anchor available, or an anchor that does not make them fit. That last case matters - one
   // recording came back with segment lengths that describe no airing at all, and no amount of
   // re-anchoring should make that look acceptable.
   public static Anchored reanchor(String tivoName, String recordingId,
         List<Segment> segments, long durationMs) {
      return reanchor(null, tivoName, recordingId, segments, durationMs);
   }

   // Takes an already connected away-mode Remote when the caller has one. A batch holds its
   // connection open across every recording, and opening a second websocket per recording just
   // to ask one question cost a connect and an authenticate each time.
   public static Anchored reanchor(Remote r, String tivoName, String recordingId,
         List<Segment> segments, long durationMs) {
      if (config.autoskip_stream_anchor != 1) return Anchored.unchanged(segments);
      if (segments == null || segments.isEmpty()) return Anchored.unchanged(segments);
      if (recordingId == null || tivoName == null) return Anchored.unchanged(segments);
      if (rejectReason(segments, durationMs) == null) return Anchored.unchanged(segments);

      HlsStream.Anchor anchor = r == null
         ? HlsStream.read(tivoName, recordingId)
         : HlsStream.read(r, tivoName, recordingId);
      // The TiVo was busy or unreachable. Saying nothing is the point: a verdict recorded now
      // would be about the box, and it would stop this recording ever being tried again.
      if (anchor.retryLater) return Anchored.retryLater(segments);
      if (anchor.ms == null) return Anchored.unchanged(segments);

      List<Segment> shifted = shift(segments, anchor.ms);
      String reason = rejectReason(shifted, durationMs);
      if (reason != null) {
         log.warn("Stream anchor did not make the SkipMode data fit: " + reason);
         return Anchored.unchanged(segments);
      }
      log.print("Re-anchored SkipMode data by " + anchor.ms + " ms using the recording's stream");
      return Anchored.rescued(shifted);
   }

   // Segments to use, and whether the TiVo was simply unavailable. A caller that remembers
   // rejections must not remember one when retryLater is set.
   public static class Anchored {
      public final List<Segment> segments;
      public final boolean retryLater;

      private Anchored(List<Segment> segments, boolean retryLater) {
         this.segments = segments;
         this.retryLater = retryLater;
      }

      // The segments were usable as they were, or nothing could be done about them.
      static Anchored unchanged(List<Segment> segments) {
         return new Anchored(segments, false);
      }

      // The stream anchor moved them onto the recording.
      static Anchored rescued(List<Segment> segments) {
         return new Anchored(segments, false);
      }

      static Anchored retryLater(List<Segment> segments) {
         return new Anchored(segments, true);
      }
   }

   // Both ends of every segment moved back by the stream's start. Separate from reanchor so
   // the arithmetic can be tested without a TiVo to ask for the anchor.
   static List<Segment> shift(List<Segment> segments, long anchorMs) {
      List<Segment> shifted = new ArrayList<Segment>();
      for (Segment seg : segments) {
         shifted.add(new Segment(seg.startMs - anchorMs, seg.endMs - anchorMs));
      }
      return shifted;
   }

   // Validates and, on failure, remembers the verdict so the same clipMetadata is not fetched
   // again on every refresh. Returns true when the segments are usable.
   public static boolean remember(String contentId, String clipMetadataId, String title,
         List<Segment> segments, long durationMs) {
      String reason = rejectReason(segments, durationMs);
      if (reason == null) return true;
      warnRejected(title, reason);
      SkipModeRejects.add(contentId, clipMetadataId, title, reason);
      return false;
   }

   public static void warnRejected(String title, String reason) {
      log.warn("Ignoring SkipMode data for '" + title + "': " + reason);
   }

   // Told how far along a batch is, and answers whether to keep going - which is how a job
   // gets cancelled part way through without this class knowing what a job is.
   public interface Progress {
      boolean update(int done, int total, String title);
   }

   // tivo.com is a third party service and this loop is the one place kmttg hits it in bulk,
   // so requests are spaced rather than issued as fast as they complete. Small next to a round
   // trip; the wall clock here is dominated by the network either way.
   private static final long PACE_MS = 200;

   // What a batch did. Nothing saved is not the same answer as never having run: a batch that
   // could not reach tivo.com has to be reported as a failure rather than as a completed run
   // that happened to write nothing.
   public static class Batch {
      public final int saved;
      public final String failure;
      Batch(int saved, String failure) {
         this.saved = saved;
         this.failure = failure;
      }
   }

   // Fill in AutoSkip entries for a batch of recordings, over ONE away mode connection: the
   // websocket handshake and tivo.com auth cost far more than the requests, so opening one per
   // recording would dominate a run of any size.
   // A batch asks the box once whether it can stream at all, rather than once per recording -
   // on a TiVo that cannot, the probe and its warning would otherwise repeat for every entry.
   public static Batch fetchMissing(String tivoName, List<Hashtable<String,String>> entries,
         Progress progress) {
      if (entries == null || entries.isEmpty()) return new Batch(0, null);
      if (config.getTivoUsername() == null || config.getTivoPassword() == null) {
         return new Batch(0, "no tivo.com username and password configured");
      }
      // Fresh each batch: a box that was rebooting last time may be ready now.
      HlsStream.forgetStreamingState();
      Remote r = new Remote(tivoName, true);
      // Most often a tivo.com login that no longer works - the specific reason has already
      // been logged by whichever step rejected it.
      if (! r.success) {
         r.disconnect();
         return new Batch(0, "could not connect to tivo.com");
      }
      int saved = 0;
      try {
         int done = 0;
         for (Hashtable<String,String> e : entries) {
            done++;
            String title = e.get("title");
            if (progress != null && ! progress.update(done, entries.size(), title)) break;

            String contentId = e.get("contentId");
            if (contentId == null || e.get("recordingId") == null
                  || e.get("clipMetadataId") == null) continue;
            // Re-checked per recording rather than trusted from the scan: the list was built
            // before the batch started and a download in the meantime may have filled one in.
            if (! SkipManager.getEntry(contentId).isEmpty()) continue;

            // Counted on the write, not the fetch: data that does not fit the recording is
            // fetched successfully and still stores nothing.
            if (fetchOne(r, tivoName, e)) saved++;
            if (! pace()) break;
         }
      } finally {
         r.disconnect();
      }
      return new Batch(saved, null);
   }

   // Spacing is per request, not per recording: fetchOne makes two cloud calls, three when the
   // preferred clip has to fall back, so pacing only the loop would treble the burst rate.
   // False when interrupted, which is how a cancelled batch stops instead of running on
   // unpaced - every later sleep would throw straight away and issue its requests back to back.
   private static boolean pace() {
      try {
         Thread.sleep(PACE_MS);
         return true;
      } catch (InterruptedException ie) {
         Thread.currentThread().interrupt();
         return false;
      }
   }

   // Adjust with the preferred clip, falling back to the one the NPL carried when the
   // preferred one cannot be resolved. clipMetadataSearch lists ids that clipMetadataAdjust
   // then refuses with "Requested clip metadata is not found", so a refinement has to be able
   // to hand the fallback back rather than losing the recording.
   private static List<Segment> adjustPreferring(Remote r, String recordingId, String chosen,
         String fallback) {
      List<Segment> segments = fromClipMetadataAdjust(r, recordingId, chosen);
      if (segments == null && fallback != null && ! fallback.equals(chosen)) {
         log.warn("clipMetadata " + chosen + " did not adjust, falling back to " + fallback);
         pace();
         segments = fromClipMetadataAdjust(r, recordingId, fallback);
      }
      return segments;
   }

   // One recording's fetch. Takes the Remote for the same reason fromClipMetadataAdjust does:
   // fetchMissing owns the away mode connection, so this is the only part a replayed trace can
   // drive. Returns whether an AutoSkip entry was written.
   static boolean fetchOne(Remote r, String tivoName, Hashtable<String,String> e) {
      String contentId = e.get("contentId"), title = e.get("title");
      long duration = durationMs(e.get("duration"));
      String chosen = chooseForAiring(r, contentId, e.get("offerId"), e.get("clipMetadataId"));
      pace();
      List<Segment> segments =
         adjustPreferring(r, e.get("recordingId"), chosen, e.get("clipMetadataId"));
      if (segments == null) {
         log.warn("No SkipMode data from tivo.com for: " + title);
         return false;
      }
      // Offsets anchored to the tuner rather than the recording are the single biggest reason
      // this refuses data, so give the box a chance to say where its stream started before
      // writing that verdict down. No-ops unless the option is on and the data actually fails.
      Anchored anchored = reanchor(r, tivoName, e.get("recordingId"), segments, duration);
      segments = anchored.segments;
      // The TiVo was busy or unreachable. Leave the recording untouched so the next refresh
      // picks it up again - remembering now would cache a fact about the box, not the data.
      if (anchored.retryLater) {
         log.warn("Leaving '" + title + "' for a later refresh: the TiVo could not be asked");
         return false;
      }
      // Keyed on the NPL's id, not the one chooseForAiring settled on: the scan that reads
      // this back only ever sees the NPL's, so storing anything else means the row never
      // matches and the recording is refetched on every refresh.
      if (! remember(contentId, e.get("clipMetadataId"), title, segments, duration)) return false;
      return saveToAutoSkip(contentId, e.get("offerId"), title, tivoName, segments, duration);
   }

   // NPL entries carry duration as a msec string. Absent or unparseable means unknown, which
   // saveToAutoSkip reads as "do not clamp the end".
   private static long durationMs(String value) {
      if (value == null) return 0;
      try {
         return Long.parseLong(value.trim());
      } catch (NumberFormatException e) {
         return 0;
      }
   }

   // Recordings the TiVo says have SkipMode but that the AutoSkip table has nothing for.
   // Entirely local, and the table is read once rather than once per recording: getEntry
   // re-parses the whole ini for each id, so asking it about a full Now Playing list turns a
   // single file read into hundreds of them on the job monitor's thread.
   public static List<Hashtable<String,String>> missingFromAutoSkip(
         List<Hashtable<String,String>> nplEntries) {
      List<Hashtable<String,String>> missing = new ArrayList<Hashtable<String,String>>();
      if (nplEntries == null) return missing;
      Set<String> known = SkipManager.contentIds();
      Map<String,String> rejected = SkipModeRejects.load();
      for (Hashtable<String,String> e : nplEntries) {
         if (e.get("contentId") == null || e.get("clipMetadataId") == null) continue;
         if (e.get("recordingId") == null) continue;
         if (known.contains(e.get("contentId"))) continue;
         // Already tried and found not to fit. Compared on the clipMetadataId so replacement
         // metadata for the same recording still gets a fresh attempt.
         if (e.get("clipMetadataId").equals(rejected.get(e.get("contentId")))) continue;
         missing.add(e);
      }
      return missing;
   }
}
