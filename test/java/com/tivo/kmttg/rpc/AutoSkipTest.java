package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Hashtable;
import java.util.Stack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tivo.kmttg.main.config;

// What AutoSkip decides once the table has been read and playback has a position: where the
// show segments really are after the stored offset is applied, and where to jump from a
// position inside a commercial. Everything either side of that - the timer, the Position
// query, the jump itself - needs a TiVo answering, so these drive the two ends that do not.
public class AutoSkipTest {

   @TempDir
   Path work;

   private static final String AIRING =
      "tivo:of.ctd.394439967.26-6.terrestrial.2026-08-23-03-30-00.1800";

   private String savedProgramDir;
   private int savedPadStart, savedPadStop;
   private Boolean savedMode;

   @BeforeEach
   void redirectProgramDir() {
      savedProgramDir = config.programDir;
      savedPadStart = config.autoskip_padding_start;
      savedPadStop = config.autoskip_padding_stop;
      savedMode = config.GUIMODE;
      config.programDir = work.toString();
      config.autoskip_padding_start = 0;
      config.autoskip_padding_stop = 0;
      config.GUIMODE = false;
   }

   @AfterEach
   void restore() {
      config.programDir = savedProgramDir;
      config.autoskip_padding_start = savedPadStart;
      config.autoskip_padding_stop = savedPadStop;
      config.GUIMODE = savedMode;
   }

   // Three show segments of a half hour episode, the shape the table holds after a SkipMode
   // fetch: show, break, show, break, show.
   private static final long[] EPISODE = {0, 900000, 1100000, 1600000, 1800000, 2000000};

   @Test
   void readEntryLoadsTheSegmentsForTheAiringBeingPlayedBack() {
      save(0L, EPISODE);
      AutoSkip skip = new AutoSkip();
      assertTrue(skip.readEntry(AIRING));
      assertEquals(3, skip.skipData.size());
      assertSegment(skip, 0, 0, 900000);
      assertSegment(skip, 2, 1800000, 2000000);
   }

   // The offset is what the Skip dialog's "adjust" writes when the cut points are right but
   // the whole set lands early or late - a recording that started a couple of seconds after
   // the broadcast did. Segment 1 starts where the file starts whatever the offset says, so
   // only its end moves; everything after it moves whole.
   @Test
   void aStoredOffsetMovesEveryPointButTheStartOfTheFirstSegment() {
      save(2500L, EPISODE);
      AutoSkip skip = new AutoSkip();
      assertTrue(skip.readEntry(AIRING));
      assertSegment(skip, 0, 0, 902500);
      assertSegment(skip, 1, 1102500, 1602500);
      assertSegment(skip, 2, 1802500, 2002500);
   }

   // The adjustment is made on a copy. The same AutoSkip reads the table again on the next
   // recording, and applying an offset to the entries it was handed would leave the shift
   // baked into whatever else is holding them.
   @Test
   void applyingTheOffsetDoesNotTouchWhatWasReadFromTheFile() throws IOException {
      save(2500L, EPISODE);
      AutoSkip skip = new AutoSkip();
      skip.readEntry(AIRING);
      assertEquals(900000L, skip.skipData_orig.get(0).get("end").longValue());
      assertTrue(raw().contains("0 900000"), "and the file itself is untouched: " + raw());
   }

   // A hand edited ini, or one written by a version that put something else here. The offset
   // is read every time a show starts playing, so throwing would take out the playback that
   // is already under way rather than just this entry.
   @Test
   void anOffsetThatIsNotANumberIsTreatedAsNoOffset() throws IOException {
      writeIni("offset=2.5", EPISODE);
      AutoSkip skip = new AutoSkip();
      assertTrue(skip.readEntry(AIRING));
      assertSegment(skip, 0, 0, 900000);
   }

   @Test
   void anAiringWithNothingToSkipIsDeclinedRatherThanPlayed() {
      AutoSkip skip = new AutoSkip();
      assertFalse(skip.readEntry(AIRING), "no file at all");

      save(0L, EPISODE);
      assertFalse(skip.readEntry("tivo:of.ctd.1.1-1.terrestrial.2026-01-01-00-00-00.1800"),
         "an airing the table has never heard of");

      // An entry with no cut pairs - what a failed import leaves behind. There is nothing to
      // monitor, and the first thing the caller would do with it is read segment 1.
      SkipManager.saveEntry("tivo:ct.2", "tivo:of.empty", 0L, "Ghost", "Bolt",
         new Stack<Hashtable<String,Long>>());
      assertFalse(skip.readEntry("tivo:of.empty"));
   }

   @Test
   void inACommercialTheJumpTargetIsTheStartOfTheNextShowSegment() {
      AutoSkip skip = loaded(EPISODE);
      assertEquals(1100000L, skip.getClosest(950000), "first break");
      assertEquals(1800000L, skip.getClosest(1700000), "second break");
      // Just inside the break, and just before the show comes back
      assertEquals(1100000L, skip.getClosest(900001));
      assertEquals(1100000L, skip.getClosest(1099999));
   }

   @Test
   void insideTheShowThereIsNothingToJumpTo() {
      AutoSkip skip = loaded(EPISODE);
      assertEquals(-1L, skip.getClosest(0));
      assertEquals(-1L, skip.getClosest(450000));
      assertEquals(-1L, skip.getClosest(900000), "the last frame of the segment is still show");
      assertEquals(-1L, skip.getClosest(1100000));
   }

   // Past the last segment there is nothing ahead, and jumping to the nearest start would
   // throw playback back into the middle of the episode.
   @Test
   void itWillNotJumpBackwards() {
      AutoSkip skip = loaded(EPISODE);
      assertEquals(-1L, skip.getClosest(2000001));
      assertEquals(-1L, skip.getClosest(3600000));
   }

   // A recording whose only commercial is in front of the show - what comskip leaves when it
   // finds one break at the head, and what a SkipMode fetch leaves for a programme with no
   // breaks of its own. There is one show segment and one place to jump to.
   @Test
   void aCommercialBeforeTheOnlyShowSegmentIsStillSkipped() {
      AutoSkip skip = loaded(new long[]{30000, 1800000});
      assertEquals(30000L, skip.getClosest(0));
      assertEquals(30000L, skip.getClosest(29999));
      assertEquals(-1L, skip.getClosest(30000), "and once inside it, nothing to do");
   }

   // Start padding is how somebody who does not fully trust the cut points asks to see the
   // tail of each break. AutoSkip jumps to start+padding, so the position it lands on has to
   // read as already in the show - otherwise the next tick jumps again from where it just
   // landed and the viewer sees a stutter at every break.
   @Test
   void aPaddedJumpDoesNotImmediatelyJumpAgain() {
      config.autoskip_padding_start = -2000;
      AutoSkip skip = loaded(EPISODE);
      assertEquals(1100000L, skip.getClosest(950000));
      assertEquals(-1L, skip.getClosest(1098000), "landed where the padded jump put it");
      assertEquals(-1L, skip.getClosest(1099000));
   }

   // Stop padding extends the same window past the end of a segment, for cut points that end
   // a moment before the show actually does.
   @Test
   void stopPaddingHoldsOffTheNextSkip() {
      config.autoskip_padding_stop = 3000;
      AutoSkip skip = loaded(EPISODE);
      assertEquals(-1L, skip.getClosest(902000), "still counted as show");
      assertEquals(1100000L, skip.getClosest(905000));
   }

   private AutoSkip loaded(long... bounds) {
      save(0L, bounds);
      AutoSkip skip = new AutoSkip();
      assertTrue(skip.readEntry(AIRING));
      return skip;
   }

   private static void save(long offset, long... bounds) {
      Stack<Hashtable<String,Long>> cuts = new Stack<Hashtable<String,Long>>();
      for (int i=0; i<bounds.length; i+=2) {
         Hashtable<String,Long> h = new Hashtable<String,Long>();
         h.put("start", bounds[i]);
         h.put("end", bounds[i+1]);
         cuts.push(h);
      }
      SkipManager.saveEntry("tivo:ct.1", AIRING, offset, "Cheers", "Bolt", cuts);
   }

   // The same file saveEntry writes, with one header field replaced by something it never
   // writes itself
   private void writeIni(String offsetLine, long... bounds) throws IOException {
      String eol = "\r\n";
      StringBuilder s = new StringBuilder("<entry>" + eol
         + "contentId=tivo:ct.1" + eol
         + "offerId=" + AIRING + eol
         + offsetLine + eol
         + "tivoName=Bolt" + eol
         + "title=Cheers" + eol);
      for (int i=0; i<bounds.length; i+=2)
         s.append(bounds[i] + " " + bounds[i+1] + eol);
      Files.write(work.resolve("AutoSkip.ini"), s.toString().getBytes(StandardCharsets.UTF_8));
   }

   private String raw() throws IOException {
      return new String(Files.readAllBytes(work.resolve("AutoSkip.ini")), StandardCharsets.UTF_8);
   }

   private static void assertSegment(AutoSkip skip, int i, long start, long end) {
      assertEquals(start, skip.skipData.get(i).get("start").longValue(), "segment " + i + " start");
      assertEquals(end, skip.skipData.get(i).get("end").longValue(), "segment " + i + " end");
   }
}
