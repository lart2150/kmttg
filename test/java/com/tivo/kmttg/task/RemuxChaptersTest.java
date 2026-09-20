package com.tivo.kmttg.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tivo.kmttg.mux.mkv.MkvMuxer;
import com.tivo.kmttg.rpc.ClipSegments;

// Cover for turning show segments into the alternating chapter list. Pure, so the off by one
// at each end is worth pinning: n segments give 2n-1 chapters, the breaks are the gaps between
// them, and nothing is invented before the first segment or after the last.
public class RemuxChaptersTest {

   private static List<ClipSegments.Segment> segments(long... bounds) {
      List<ClipSegments.Segment> l = new ArrayList<ClipSegments.Segment>();
      for (int i = 0; i < bounds.length; i += 2) {
         l.add(new ClipSegments.Segment(bounds[i], bounds[i+1]));
      }
      return l;
   }

   @Test
   void segmentsAndBreaksAlternateAndCoverTheWholeTimeline() {
      List<MkvMuxer.Chapter> c = remux.buildChapters(segments(0, 100, 200, 300, 400, 500));
      assertEquals(5, c.size(), "3 segments -> 3 shows + 2 breaks");
      assertEquals("Segment 1", c.get(0).name);
      assertEquals("Commercials 1", c.get(1).name);
      assertEquals("Segment 2", c.get(2).name);
      assertEquals("Commercials 2", c.get(3).name);
      assertEquals("Segment 3", c.get(4).name);
      // No gaps: each chapter starts where the previous one ended.
      for (int i = 1; i < c.size(); i++) {
         assertEquals(c.get(i-1).endMs, c.get(i).startMs, "gap before chapter " + i);
      }
      assertEquals(0, c.get(0).startMs);
      assertEquals(500, c.get(4).endMs);
   }

   @Test
   void breaksSpanTheGapsNotTheSegments() {
      List<MkvMuxer.Chapter> c = remux.buildChapters(segments(0, 100, 250, 300));
      assertEquals(100, c.get(1).startMs);
      assertEquals(250, c.get(1).endMs);
   }

   @Test
   void noBreakIsAddedAfterTheLastSegment() {
      List<MkvMuxer.Chapter> c = remux.buildChapters(segments(0, 100));
      assertEquals(1, c.size(), "one segment is one chapter and no trailing break");
      assertEquals("Segment 1", c.get(0).name);
   }

   @Test
   void emptyInputProducesNoChapters() {
      assertTrue(remux.buildChapters(segments()).isEmpty());
   }

   @Test
   void theTrailingBoundIsPassedThroughForTheMuxerToClamp() {
      // The SkipMode window overhangs the end of the recording, and clamping cannot happen
      // here because the duration is not known until the last cluster is written, so that
      // bound has to survive the trip intact.
      List<MkvMuxer.Chapter> c = remux.buildChapters(segments(-27739, 912511, 1107328, 7232203));
      assertEquals(7232203, c.get(2).endMs);
   }

   @Test
   void theFirstChapterStartsAtTheFileEvenWhenTheShowDoesNot() {
      // Start padding puts the show nearly four minutes in; the chapter still has to open at
      // the first frame or the menu looks like it is missing an entry.
      List<MkvMuxer.Chapter> c = remux.buildChapters(segments(228000, 912511, 1107328, 1800000));
      assertEquals(0, c.get(0).startMs);
      assertEquals(912511, c.get(0).endMs, "only the start moves");
      assertEquals(1107328, c.get(2).startMs, "later segments are untouched");
   }

   @Test
   void aFirstSegmentBeforeTheFileAlsoStartsAtZero() {
      List<MkvMuxer.Chapter> c = remux.buildChapters(segments(-27739, 912511));
      assertEquals(0, c.get(0).startMs);
   }
}
