package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tivo.kmttg.main.config;

// Cover for remembering which recordings' SkipMode data did not fit, so the fetch stops asking
// every refresh. The behaviour worth pinning is when it deliberately forgets: a replacement
// clipMetadataId is new data, and a deleted recording should not keep its row.
public class SkipModeRejectsTest {

   @TempDir
   Path work;

   private String savedProgramDir;

   @BeforeEach
   void redirect() {
      savedProgramDir = config.programDir;
      config.programDir = work.toString();
   }

   @AfterEach
   void restore() {
      config.programDir = savedProgramDir;
   }

   @Test
   void aRejectIsRememberedAndRecognised() {
      SkipModeRejects.add("tivo:ct.1", "tivo:cm.1", "Some Show", "segments span ...");
      assertTrue(SkipModeRejects.isRejected("tivo:ct.1", "tivo:cm.1"));
      assertEquals(1, SkipModeRejects.load().size());
   }

   @Test
   void replacementMetadataGetsAFreshAttempt() {
      // TiVo does revoke a clipMetadata and issue another - cm.1275287 became cm.1303131 on a
      // real recording. Keying on the id is what stops this being a permanent blacklist.
      SkipModeRejects.add("tivo:ct.1", "tivo:cm.old", "Some Show", "did not fit");
      assertTrue(SkipModeRejects.isRejected("tivo:ct.1", "tivo:cm.old"));
      assertFalse(SkipModeRejects.isRejected("tivo:ct.1", "tivo:cm.new"),
         "a different clipMetadataId is data we have not tried");
   }

   @Test
   void aSecondVerdictReplacesTheRowRatherThanAppending() {
      SkipModeRejects.add("tivo:ct.1", "tivo:cm.old", "Some Show", "did not fit");
      SkipModeRejects.add("tivo:ct.1", "tivo:cm.new", "Some Show", "did not fit either");
      assertEquals(1, SkipModeRejects.load().size(), "one row per recording");
      assertTrue(SkipModeRejects.isRejected("tivo:ct.1", "tivo:cm.new"));
      assertFalse(SkipModeRejects.isRejected("tivo:ct.1", "tivo:cm.old"));
   }

   @Test
   void pruningDropsRecordingsNoLongerInMyShows() {
      SkipModeRejects.add("tivo:ct.gone", "tivo:cm.1", "Deleted Show", "did not fit");
      SkipModeRejects.add("tivo:ct.here", "tivo:cm.2", "Kept Show", "did not fit");
      SkipModeRejects.prune(new HashSet<String>(Arrays.asList("tivo:ct.here")));
      assertEquals(1, SkipModeRejects.load().size());
      assertTrue(SkipModeRejects.isRejected("tivo:ct.here", "tivo:cm.2"));
      assertFalse(SkipModeRejects.isRejected("tivo:ct.gone", "tivo:cm.1"));
   }

   @Test
   void anEmptyLiveSetNeverWipesTheFile() {
      // A failed or empty NPL must not be read as "every recording was deleted".
      SkipModeRejects.add("tivo:ct.1", "tivo:cm.1", "Some Show", "did not fit");
      SkipModeRejects.prune(new HashSet<String>());
      SkipModeRejects.prune(null);
      assertEquals(1, SkipModeRejects.load().size());
   }

   @Test
   void commentsAndRaggedLinesSurviveAReadWriteCycle() throws Exception {
      Files.write(work.resolve("SkipModeRejects.ini"),
         ("# a comment\n\ntivo:ct.1 tivo:cm.1  Show One - because\nrubbish\n")
            .getBytes(StandardCharsets.UTF_8));
      assertEquals(1, SkipModeRejects.load().size(), "the short line is ignored, not fatal");
      SkipModeRejects.add("tivo:ct.2", "tivo:cm.2", "Show Two", "because");
      String out = new String(Files.readAllBytes(work.resolve("SkipModeRejects.ini")),
         StandardCharsets.UTF_8);
      assertTrue(out.contains("Show One - because"), "the earlier note is not lost: " + out);
      assertTrue(out.contains("tivo:ct.2 tivo:cm.2"));
      assertTrue(out.startsWith("#"), "the header explains the file to whoever opens it");
   }

   @Test
   void theScanSkipsRememberedRejectsButNotNewMetadata() {
      SkipModeRejects.add("tivo:ct.bad", "tivo:cm.bad", "Bad Show", "did not fit");
      List<Hashtable<String,String>> npl = new ArrayList<Hashtable<String,String>>();
      npl.add(npl("tivo:ct.bad", "tivo:cm.bad"));   // remembered - skip
      npl.add(npl("tivo:ct.bad2", "tivo:cm.new"));  // never tried - fetch
      List<Hashtable<String,String>> missing = ClipSegments.missingFromAutoSkip(npl);
      assertEquals(1, missing.size());
      assertEquals("tivo:ct.bad2", missing.get(0).get("contentId"));

      // The same recording with replacement metadata comes back into scope.
      npl.clear();
      npl.add(npl("tivo:ct.bad", "tivo:cm.replacement"));
      assertEquals(1, ClipSegments.missingFromAutoSkip(npl).size());
   }

   private static Hashtable<String,String> npl(String contentId, String clipMetadataId) {
      Hashtable<String,String> e = new Hashtable<String,String>();
      e.put("contentId", contentId);
      e.put("clipMetadataId", clipMetadataId);
      e.put("recordingId", "tivo:rc.1");
      // AutoSkip.ini is keyed on the airing, and a recording without one is not worth
      // queueing because the fetch could not save its result afterwards
      e.put("offerId", "tivo:of." + contentId);
      e.put("title", contentId);
      return e;
   }

   // Turning on the stream anchor offers to forget these, because they were judged without it.
   @Test
   public void countAndClearDriveTheResetOfferedWhenTheAnchorIsEnabled() {
      assertEquals(0, SkipModeRejects.count(), "no file yet is not an error");
      assertTrue(SkipModeRejects.clear(), "clearing nothing succeeds");

      SkipModeRejects.add("tivo:ct.1", "tivo:cm.1", "One", "does not fit");
      SkipModeRejects.add("tivo:ct.2", "tivo:cm.2", "Two", "does not fit");
      assertEquals(2, SkipModeRejects.count());

      assertTrue(SkipModeRejects.clear());
      assertEquals(0, SkipModeRejects.count());
      assertFalse(SkipModeRejects.isRejected("tivo:ct.1", "tivo:cm.1"),
         "a cleared verdict must not survive");
   }
}
