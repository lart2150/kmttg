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
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.Stack;

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
         String clipMetadataId) {
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
         List<Segment> adjusted = fromClipMetadataAdjust(r, recordingId, clipMetadataId);
         if (adjusted == null) return null;
         log.print("Using tivo.com SkipMode data for chapters (" + adjusted.size() + " segments)");
         return new Result(adjusted, SOURCE_SKIPMODE);
      } finally {
         if (r != null && r.success) r.disconnect();
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

   // Fill in AutoSkip entries for a batch of recordings, over ONE away mode connection: the
   // websocket handshake and tivo.com auth cost far more than the requests, so opening one per
   // recording would dominate a run of any size. Returns how many entries were written.
   public static int fetchMissing(String tivoName, List<Hashtable<String,String>> entries,
         Progress progress) {
      if (entries == null || entries.isEmpty()) return 0;
      if (config.getTivoUsername() == null || config.getTivoPassword() == null) {
         log.error("SkipMode fetch needs a tivo.com username and password");
         return 0;
      }
      Remote r = new Remote(tivoName, true);
      if (! r.success) {
         log.error("SkipMode fetch could not connect to tivo.com");
         return 0;
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

            List<Segment> segments =
               fromClipMetadataAdjust(r, e.get("recordingId"), e.get("clipMetadataId"));
            if (segments == null) {
               log.warn("No SkipMode data from tivo.com for: " + title);
            } else if (saveToAutoSkip(contentId, e.get("offerId"), title, tivoName, segments,
                  durationMs(e.get("duration")))) {
               // Counted on the write, not the fetch: data that does not fit the recording is
               // fetched successfully and still stores nothing.
               saved++;
            }
            try {
               Thread.sleep(PACE_MS);
            } catch (InterruptedException ie) {
               Thread.currentThread().interrupt();
               break;
            }
         }
      } finally {
         r.disconnect();
      }
      return saved;
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
      for (Hashtable<String,String> e : nplEntries) {
         if (e.get("contentId") == null || e.get("clipMetadataId") == null) continue;
         if (e.get("recordingId") == null) continue;
         if (known.contains(e.get("contentId"))) continue;
         missing.add(e);
      }
      return missing;
   }
}
