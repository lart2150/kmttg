package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.Stack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;

// AutoSkip.ini is the only place kmttg keeps commercial skip data, and eight methods here
// read or rewrite it with eight hand rolled copies of the parse. These tests pin the file
// format and what each one does with it, so a change to one of them cannot quietly disagree
// with the other seven. programDir is redirected because iniFile() resolves on every call.
public class SkipManagerIniTest {

   @TempDir
   Path work;

   private static final String AIRING_A =
      "tivo:of.ctd.394439967.26-6.terrestrial.2026-08-23-03-30-00.1800";
   private static final String AIRING_B =
      "tivo:of.ctd.366821959.48-3.terrestrial.2026-08-23-03-30-00.1800";
   private static final String AIRING_C =
      "tivo:of.ctd.394439967.26-6.terrestrial.2026-08-20-03-00-00.1800";
   private static final String AIRING_NONE = "tivo:of.ctd.1.1-1.terrestrial.2026-01-01-00-00-00.1800";

   private String savedProgramDir;
   private int savedEnabled;
   private Boolean savedMode;
   private LinkedHashMap<String,String> savedTivos;
   private Hashtable<String,String> savedRpc;

   @BeforeEach
   void redirectProgramDir() {
      savedProgramDir = config.programDir;
      savedEnabled = config.autoskip_enabled;
      savedMode = config.GUIMODE;
      savedTivos = new LinkedHashMap<String,String>(config.TIVOS);
      savedRpc = new Hashtable<String,String>(config.enableRpc);
      config.programDir = work.toString();
      config.GUIMODE = false;
      // skipEnabled() wants AutoSkip on and at least one RPC capable TiVo
      config.autoskip_enabled = 1;
      config.TIVOS.put("Bolt", "192.168.1.5");
      config.enableRpc.put("Bolt", "1");
   }

   @AfterEach
   void restore() {
      config.programDir = savedProgramDir;
      config.autoskip_enabled = savedEnabled;
      config.GUIMODE = savedMode;
      config.TIVOS.clear();
      config.TIVOS.putAll(savedTivos);
      config.enableRpc.clear();
      config.enableRpc.putAll(savedRpc);
   }

   private static Stack<Hashtable<String,Long>> cuts(long... bounds) {
      Stack<Hashtable<String,Long>> s = new Stack<Hashtable<String,Long>>();
      for (int i = 0; i < bounds.length; i += 2) {
         Hashtable<String,Long> h = new Hashtable<String,Long>();
         h.put("start", bounds[i]);
         h.put("end", bounds[i+1]);
         s.push(h);
      }
      return s;
   }

   private void save(String contentId, String offerId, String title, long... bounds) {
      SkipManager.saveEntry(contentId, offerId, 0L, title, "Bolt", cuts(bounds));
   }

   private String raw() throws IOException {
      return new String(Files.readAllBytes(work.resolve("AutoSkip.ini")), StandardCharsets.UTF_8);
   }

   @Test
   void theFileFormatIsAnEntryHeaderThenFieldsThenCutPairs() throws IOException {
      save("tivo:ct.1", AIRING_A, "Cheers", 0, 900000, 1100000, 1600000);
      assertEquals(
         "<entry>\r\n"
         + "contentId=tivo:ct.1\r\n"
         + "offerId=" + AIRING_A + "\r\n"
         + "offset=0\r\n"
         + "tivoName=Bolt\r\n"
         + "title=Cheers\r\n"
         + "0 900000\r\n"
         + "1100000 1600000\r\n", raw());
   }

   @Test
   void savingAppendsRatherThanReplacing() throws IOException {
      save("tivo:ct.1", AIRING_A, "Cheers", 0, 900000);
      save("tivo:ct.2", AIRING_B, "Frasier", 0, 800000);
      assertEquals(2, countEntries(raw()));
      assertEquals(2, SkipManager.offerIds().size());
   }

   @Test
   void getEntryReturnsTheCutPairsInFileOrder() {
      save("tivo:ct.1", AIRING_A, "Cheers", 0, 900000, 1100000, 1600000);
      Stack<Hashtable<String,Long>> entry = SkipManager.getEntry(AIRING_A);
      assertEquals(2, entry.size());
      assertEquals(0L, entry.get(0).get("start").longValue());
      assertEquals(900000L, entry.get(0).get("end").longValue());
      assertEquals(1600000L, entry.get(1).get("end").longValue());
   }

   @Test
   void getEntryAnswersEmptyForAnUnknownIdAndAMissingFile() {
      assertTrue(SkipManager.getEntry(AIRING_NONE).isEmpty(), "no file at all");
      save("tivo:ct.1", AIRING_A, "Cheers", 0, 900000);
      assertTrue(SkipManager.getEntry(AIRING_NONE).isEmpty());
   }

   @Test
   void getEntriesExposesEveryHeaderFieldAndTheFirstBreakAsAd1() throws Exception {
      save("tivo:ct.1", AIRING_A, "Cheers", 0, 900000, 1100000, 1600000);
      JSONArray entries = SkipManager.getEntries();
      assertEquals(1, entries.length());
      JSONObject j = entries.getJSONObject(0);
      assertEquals("tivo:ct.1", j.getString("contentId"));
      assertEquals(AIRING_A, j.getString("offerId"));
      assertEquals("0", j.getString("offset"));
      assertEquals("Bolt", j.getString("tivoName"));
      assertEquals("Cheers", j.getString("title"));
      assertEquals("900000", j.getString("ad1"));
      assertEquals(2, j.getJSONArray("cuts").length());
   }

   @Test
   void hasEntryIsGatedOnAutoSkipBeingEnabled() {
      save("tivo:ct.1", AIRING_A, "Cheers", 0, 900000);
      assertTrue(SkipManager.hasEntry(AIRING_A));
      config.autoskip_enabled = 0;
      // The data is still there - ClipSegments relies on getEntry rather than this for exactly
      // that reason, or a save would append a second copy with AutoSkip switched off
      assertFalse(SkipManager.hasEntry(AIRING_A));
      assertFalse(SkipManager.getEntry(AIRING_A).isEmpty());
   }

   @Test
   void removeEntryLeavesTheSurvivingEntriesByteIdentical() throws IOException {
      save("tivo:ct.1", AIRING_A, "Cheers", 0, 900000);
      save("tivo:ct.2", AIRING_B, "Frasier", 0, 800000, 1000000, 1200000);
      save("tivo:ct.3", AIRING_C, "Wings", 0, 700000);
      String before = raw();
      String survivor = entryBlock(before, "tivo:ct.2");

      assertTrue(SkipManager.removeEntry(AIRING_A));

      String after = raw();
      assertEquals(2, countEntries(after));
      assertEquals(survivor, entryBlock(after, "tivo:ct.2"), "an untouched entry was rewritten");
      assertTrue(SkipManager.getEntry(AIRING_A).isEmpty());
      assertEquals(2, SkipManager.getEntry(AIRING_B).size());
   }

   @Test
   void removingSomethingThatIsNotThereReportsFalseAndChangesNothing() throws IOException {
      save("tivo:ct.1", AIRING_A, "Cheers", 0, 900000);
      String before = raw();
      assertFalse(SkipManager.removeEntry(AIRING_NONE));
      assertEquals(before, raw());
   }

   @Test
   void changeEntryUpdatesTheOffsetAndLeavesTheCutsAlone() throws Exception {
      save("tivo:ct.1", AIRING_A, "Cheers", 0, 900000, 1100000, 1600000);
      assertTrue(SkipManager.changeEntry(AIRING_A, "2500", "Cheers"));
      assertTrue(raw().contains("offset=2500\r\n"));
      assertEquals(2, SkipManager.getEntry(AIRING_A).size());
      JSONArray entries = SkipManager.getEntries();
      assertEquals("2500", entries.getJSONObject(0).getString("offset"));
   }

   // getEntries drops an entry with no cut pairs, so a recording can carry the Skip marker in
   // the NPL - which asks contentIds/hasEntry - while the Skip dialog that would let you
   // delete it never lists it
   @Test
   void anEntryWithNoCutsIsVisibleToHasEntryButNotToGetEntries() throws Exception {
      save("tivo:ct.1", AIRING_A, "Cheers");
      assertTrue(SkipManager.hasEntry(AIRING_A));
      assertTrue(SkipManager.offerIds().contains(AIRING_A));
      assertEquals(0, SkipManager.getEntries().length());
   }

   // Every reader splits or strips differently, so a value carrying an = does not survive the
   // same way through all of them
   @Test
   void aTitleContainingAnEqualsSignReadsBackWhole() throws Exception {
      save("tivo:ct.1", AIRING_A, "Deal=No Deal", 0, 900000);
      assertEquals("Deal=No Deal", SkipManager.getEntries().getJSONObject(0).getString("title"));
   }

   // Two airings of one episode: the same half hour on two stations, which is real in a My
   // Shows list. Each station inserts its own breaks, so the cut points genuinely differ and
   // the contentId the two share cannot say which set belongs to which recording.
   @Test
   void twoAiringsOfOneContentIdAreKeptApart() throws IOException {
      save("tivo:ct.20825", AIRING_A, "Cheers", 0, 900000);
      save("tivo:ct.20825", AIRING_B, "Cheers", 0, 850000);
      assertEquals(2, countEntries(raw()));

      assertEquals(900000L, SkipManager.getEntry(AIRING_A).get(0).get("end").longValue());
      assertEquals(850000L, SkipManager.getEntry(AIRING_B).get(0).get("end").longValue());

      // and removing one airing leaves the other where it is
      assertTrue(SkipManager.removeEntry(AIRING_A));
      assertEquals(1, countEntries(raw()));
      assertTrue(SkipManager.getEntry(AIRING_A).isEmpty());
      assertEquals(850000L, SkipManager.getEntry(AIRING_B).get(0).get("end").longValue());
   }

   // offerId is optional in the NPL data, so plenty of callers can reach these with nothing
   // in hand. "No id" has to mean "no entry" - reading the file with a null id used to be the
   // internal signal for "every entry", which would hand back a stranger's cut points.
   @Test
   void anAbsentAiringMatchesNothingRatherThanEverything() throws IOException {
      save("tivo:ct.1", AIRING_A, "Cheers", 0, 900000);
      save("tivo:ct.2", AIRING_B, "Frasier", 0, 800000);

      for (String none : new String[]{null, ""}) {
         assertFalse(SkipManager.hasEntry(none), "hasEntry(" + none + ")");
         assertTrue(SkipManager.getEntry(none).isEmpty(), "getEntry(" + none + ")");
         assertFalse(SkipManager.removeEntry(none), "removeEntry(" + none + ")");
         assertFalse(SkipManager.changeEntry(none, "99", "x"), "changeEntry(" + none + ")");
      }
      // and nothing was rewritten on the way through
      assertEquals(2, countEntries(raw()));
      assertEquals(900000L, SkipManager.getEntry(AIRING_A).get(0).get("end").longValue());
   }

   // An entry with no airing could never be looked up again, and the remove-then-save every
   // caller does would append a fresh copy of it on each run
   @Test
   void anEntryWithNoAiringIsNotWrittenAtAll() throws IOException {
      SkipManager.saveEntry("tivo:ct.1", null, 0L, "Cheers", "Bolt", cuts(0, 900000));
      assertFalse(Files.exists(work.resolve("AutoSkip.ini")));

      save("tivo:ct.1", AIRING_A, "Cheers", 0, 900000);
      SkipManager.saveEntry("tivo:ct.2", "", 0L, "Frasier", "Bolt", cuts(0, 800000));
      assertEquals(1, countEntries(raw()));
   }

   private static int countEntries(String text) {
      int n = 0, i = 0;
      while ((i = text.indexOf("<entry>", i)) >= 0) { n++; i += 7; }
      return n;
   }

   // The <entry> block for a given contentId, so a test can assert it came through a rewrite
   // untouched rather than merely still being present
   private static String entryBlock(String text, String contentId) {
      for (String block : text.split("<entry>")) {
         if (block.contains("contentId=" + contentId + "\r\n"))
            return block;
      }
      return null;
   }
}
