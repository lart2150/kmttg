package com.tivo.kmttg.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Stack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Auto transfers run unattended, so keywordSearch is the only thing deciding what leaves the
// TiVo. Too loose and it fills the disk with shows nobody asked for; too tight and the user
// finds out weeks later that the season they were recording was never fetched. These tests
// drive one Now Playing entry at a time against the entries autoConfig parsed.
public class AutoKeywordMatchTest {

   @TempDir Path dir;

   private static final String[] TOUCHED = {
      "GUIMODE", "autoHistory", "autoIni", "tivoFileNameFormat", "outputDir", "mpegDir",
      "qsfixDir", "mpegCutDir", "encodeDir", "programDir", "encodeName", "autotune",
      "persistQueue", "autoskip_enabled", "combine_download_decrypt", "resumeDownloads",
   };

   private Object[] saved;
   private Stack<jobData> savedJobs;
   private Stack<autoEntry> savedKeywords;
   private LinkedHashMap<String,String> savedTivos;
   private int savedDryrun, savedDateFilter, savedSuggestions, savedKuid, savedProgramId;
   private String savedDateOperator;
   private float savedDateHours;
   // Every match logs. Pin logging to stdout so no auto.log handler is opened.
   private boolean savedStartingUp;

   @BeforeEach
   void setUp() throws Exception {
      savedStartingUp = kmttg._startingUp;
      kmttg._startingUp = true;
      savedTivos = new LinkedHashMap<String,String>(config.TIVOS);
      saved = new Object[TOUCHED.length];
      for (int i = 0; i < TOUCHED.length; i++)
         saved[i] = field(TOUCHED[i]).get(null);
      savedJobs = jobMonitor.JOBS;
      savedKeywords = autoConfig.KEYWORDS;
      savedDryrun = autoConfig.dryrun;
      savedDateFilter = autoConfig.dateFilter;
      savedSuggestions = autoConfig.suggestionsFilter;
      savedKuid = autoConfig.kuidFilter;
      savedProgramId = autoConfig.programIdFilter;
      savedDateOperator = autoConfig.dateOperator;
      savedDateHours = autoConfig.dateHours;

      jobMonitor.JOBS = new Stack<jobData>();
      autoConfig.KEYWORDS = new Stack<autoEntry>();
      autoConfig.dryrun = 0;
      autoConfig.dateFilter = 0;
      autoConfig.suggestionsFilter = 0;
      autoConfig.kuidFilter = 0;
      autoConfig.programIdFilter = 0;
      autoConfig.dateOperator = "less than";
      autoConfig.dateHours = 48;

      config.GUIMODE = false;
      config.persistQueue = false;
      config.programDir = dir.toString();
      config.outputDir = Files.createDirectories(dir.resolve("video")).toString();
      config.mpegDir = config.outputDir;
      config.qsfixDir = config.outputDir;
      config.mpegCutDir = config.outputDir;
      config.encodeDir = config.outputDir;
      config.autoHistory = dir.resolve("auto.history").toString();
      config.tivoFileNameFormat = "[title]";
      config.autotune = null;
      config.autoskip_enabled = 0;
      config.combine_download_decrypt = 0;
      config.resumeDownloads = false;
   }

   @AfterEach
   void restore() throws Exception {
      kmttg._startingUp = savedStartingUp;
      config.TIVOS.clear();
      config.TIVOS.putAll(savedTivos);
      jobMonitor.JOBS = savedJobs;
      autoConfig.KEYWORDS = savedKeywords;
      autoConfig.dryrun = savedDryrun;
      autoConfig.dateFilter = savedDateFilter;
      autoConfig.suggestionsFilter = savedSuggestions;
      autoConfig.kuidFilter = savedKuid;
      autoConfig.programIdFilter = savedProgramId;
      autoConfig.dateOperator = savedDateOperator;
      autoConfig.dateHours = savedDateHours;
      for (int i = 0; i < TOUCHED.length; i++)
         field(TOUCHED[i]).set(null, saved[i]);
   }

   private static Field field(String name) throws Exception {
      Field f = config.class.getDeclaredField(name);
      f.setAccessible(true);
      return f;
   }

   // A Now Playing entry as parseNPL builds one
   private static Hashtable<String,String> show(String title, String episode, String description) {
      Hashtable<String,String> e = new Hashtable<String,String>();
      e.put("title", title + " - " + episode);
      e.put("titleOnly", title);
      e.put("episodeTitle", episode);
      e.put("description", description);
      e.put("tivoName", "Bolt");
      e.put("url", "http://bolt/download/" + title + ".TiVo?id=1");
      e.put("url_TiVoVideoDetails", "http://bolt/TiVoVideoDetails?id=1");
      e.put("size", "4294967296");
      e.put("duration", "1800000");
      e.put("gmt", "" + System.currentTimeMillis());
      e.put("ProgramId", "EP012345670001");
      e.put("ProgramId_unique", "EP012345670001_1");
      return e;
   }

   private static autoEntry title(String keyword) {
      autoEntry a = new autoEntry();
      a.type = "title";
      a.keyword = keyword;
      autoConfig.KEYWORDS.add(a);
      return a;
   }

   private static autoEntry keywords(String line) {
      autoEntry a = new autoEntry();
      a.type = "keywords";
      autoConfig.stringToKeywords(line, a);
      autoConfig.KEYWORDS.add(a);
      return a;
   }

   // dryrun asks the same question without queueing anything, which is what most of these want
   private static boolean matches(Hashtable<String,String> entry) {
      autoConfig.dryrun = 1;
      try {
         return auto.keywordSearch(entry).booleanValue();
      } finally {
         autoConfig.dryrun = 0;
      }
   }

   // ---- title entries -----------------------------------------------------

   @Test
   void aTitleEntryMatchesTheSeriesAndNotTheEpisode() {
      title("ghosts");
      // Title entries match titleOnly, so an episode called "Ghosts" of something else stays
      assertTrue(matches(show("Ghosts", "Gate-gate", "The ghosts hold a seance.")));
      assertFalse(matches(show("Elsbeth", "Ghosts", "A murder at a seance.")));
   }

   @Test
   void aTitleEntryIsARegularExpression() {
      // The config dialog escapes the specials it can when it adds a title, but the field is a
      // regex and users write them
      title("ghosts|elsbeth");
      assertTrue(matches(show("Elsbeth", "Catch and Kill", "")));
      assertFalse(matches(show("Survivor", "Episode 1", "")));
   }

   @Test
   void aDisabledEntryNeverMatches() {
      title("ghosts").enabled = 0;
      assertFalse(matches(show("Ghosts", "Gate-gate", "")));
   }

   // ---- keyword entries ---------------------------------------------------

   @Test
   void everyPlainKeywordHasToBePresent() {
      keywords("football|nebraska");
      assertTrue(matches(show("College Football", "Nebraska at Iowa", "Big Ten action.")));
      assertFalse(matches(show("College Football", "Iowa at Purdue", "Big Ten action.")));
   }

   @Test
   void aLeadingMinusExcludes() {
      // The syntax the config dialog documents: "keyword=>AND  (keyword)=>OR  -keyword=>NOT"
      keywords("football|-college");
      assertTrue(matches(show("NFL Football", "Bears at Packers", "Sunday night.")));
      assertFalse(matches(show("College Football", "Nebraska at Iowa", "Big Ten action.")));
   }

   @Test
   void bracketsMakeAlternatives() {
      // The dialog's own example: (basketball)|(football)|-new york
      keywords("(basketball)|(football)|-new york");
      assertTrue(matches(show("NFL Football", "Bears at Packers", "Sunday night.")));
      assertTrue(matches(show("NBA Basketball", "Bulls at Bucks", "Central division.")));
      assertFalse(matches(show("NHL Hockey", "Blackhawks at Wild", "Central division.")));
      assertFalse(matches(show("NFL Football", "Giants at Jets", "New York rivalry.")));
   }

   @Test
   void bracketedAlternativesStillHonourThePlainKeywords() {
      // An 'and' term alongside alternatives has to hold for both branches
      keywords("2026|(olympics)|(world cup)");
      assertTrue(matches(show("Winter Olympics", "Day 3", "Coverage from 2026.")));
      assertFalse(matches(show("Winter Olympics", "Day 3", "Coverage from 2022.")));
   }

   @Test
   void keywordsMatchAcrossTheTitleEpisodeAndDescription() {
      keywords("seance");
      assertTrue(matches(show("Ghosts", "Gate-gate", "The ghosts hold a seance.")));
      assertTrue(matches(show("Ghosts", "The Seance", "")));
   }

   @Test
   void punctuationDoesNotStopAKeywordMatching() {
      // The text is stripped of , ; : . ! ? ( ) before matching, so a keyword typed without
      // punctuation still finds a description that has it
      keywords("bears at packers");
      assertTrue(matches(show("NFL Football", "Bears at Packers!", "Sunday night.")));
   }

   // ---- entries that must never be fetched --------------------------------

   @Test
   void aRecordingStillInProgressIsLeftAlone() {
      title("ghosts");
      Hashtable<String,String> entry = show("Ghosts", "Gate-gate", "");
      entry.put("InProgress", "Yes");
      assertFalse(matches(entry));
   }

   @Test
   void aCopyProtectedRecordingIsLeftAlone() {
      title("ghosts");
      Hashtable<String,String> entry = show("Ghosts", "Gate-gate", "");
      entry.put("CopyProtected", "Yes");
      assertFalse(matches(entry));
   }

   // ---- filters -----------------------------------------------------------

   @Test
   void anEntryLimitedToOneTivoIgnoresTheOthers() {
      title("ghosts").tivo = "Roamio";
      assertFalse(matches(show("Ghosts", "Gate-gate", "")));

      autoConfig.KEYWORDS.clear();
      title("ghosts").tivo = "Bolt";
      assertTrue(matches(show("Ghosts", "Gate-gate", "")));
   }

   @Test
   void theDateWindowKeepsRecentRecordingsAndDropsOldOnes() {
      title("ghosts");
      autoConfig.dateFilter = 1;
      autoConfig.dateOperator = "less than";
      autoConfig.dateHours = 48;

      Hashtable<String,String> old = show("Ghosts", "Gate-gate", "");
      old.put("gmt", "" + (System.currentTimeMillis() - 72L*3600*1000));
      assertFalse(matches(old));
      assertTrue(matches(show("Ghosts", "Gate-gate", "")));
   }

   @Test
   void theDateWindowCanBeTurnedAroundToCatchUpOnABacklog() {
      title("ghosts");
      autoConfig.dateFilter = 1;
      autoConfig.dateOperator = "more than";
      autoConfig.dateHours = 48;

      Hashtable<String,String> old = show("Ghosts", "Gate-gate", "");
      old.put("gmt", "" + (System.currentTimeMillis() - 72L*3600*1000));
      assertTrue(matches(old));
      assertFalse(matches(show("Ghosts", "Gate-gate", "")));
   }

   @Test
   void tivoSuggestionsAreSkippedGloballyOrPerEntry() {
      autoEntry a = title("ghosts");
      Hashtable<String,String> suggested = show("Ghosts", "Gate-gate", "");
      suggested.put("suggestion", "yes");
      assertTrue(matches(suggested), "suggestions are fetched until the filter is on");

      autoConfig.suggestionsFilter = 1;
      assertFalse(matches(suggested));

      autoConfig.suggestionsFilter = 0;
      a.suggestionsFilter = 1;
      assertFalse(matches(suggested));
      assertTrue(matches(show("Ghosts", "Gate-gate", "")), "a real recording is still fetched");
   }

   @Test
   void theKuidFilterKeepsOnlyKeepUntilIDeleteRecordings() {
      title("ghosts");
      autoConfig.kuidFilter = 1;
      assertFalse(matches(show("Ghosts", "Gate-gate", "")));

      Hashtable<String,String> kuid = show("Ghosts", "Gate-gate", "");
      kuid.put("kuid", "yes");
      assertTrue(matches(kuid));
   }

   @Test
   void theProgramIdFilterDropsTheMadeUpIds() {
      // parseNPL builds an id out of the url and size when the TiVo gives none, and marks it
      // by the underscore. Those never de-duplicate against auto.history.
      title("ghosts");
      autoConfig.programIdFilter = 1;
      assertTrue(matches(show("Ghosts", "Gate-gate", "")));

      Hashtable<String,String> fake = show("Ghosts", "Gate-gate", "");
      fake.put("ProgramId", "123456_4294967296");
      assertFalse(matches(fake));
   }

   @Test
   void aChannelFilterCanSelectOrExcludeByNumberOrCallSign() {
      autoEntry a = title("ghosts");
      a.channelFilter = "702,QVC";
      a.channelFilterList = java.util.Arrays.asList("702", "QVC");

      Hashtable<String,String> onList = show("Ghosts", "Gate-gate", "");
      onList.put("channelNum", "702");
      onList.put("channel", "CBS");
      Hashtable<String,String> offList = show("Ghosts", "Gate-gate", "");
      offList.put("channelNum", "24");
      offList.put("channel", "CBS");
      Hashtable<String,String> byName = show("Ghosts", "Gate-gate", "");
      byName.put("channelNum", "24");
      byName.put("channel", "QVC");

      // Default is select-only: nothing off the list is fetched
      assertTrue(matches(onList));
      assertFalse(matches(offList));
      assertTrue(matches(byName), "the call sign list is checked when the number does not hit");

      a.channelExcludes = 1;
      assertFalse(matches(onList));
      assertTrue(matches(offList));
      assertFalse(matches(byName));
   }

   // ---- queueing ----------------------------------------------------------

   @Test
   void aMatchQueuesTheTaskSetTheEntryAsksFor() {
      autoEntry a = title("ghosts");
      a.decrypt = 1;
      a.TSDownload = 1;

      assertTrue(auto.keywordSearch(show("Ghosts", "Gate-gate", "")));

      assertEquals(2, jobMonitor.JOBS.size(), "expected a download and a decrypt");
      assertEquals("javadownload", jobMonitor.JOBS.get(0).type);
      assertEquals("tivolibre", jobMonitor.JOBS.get(1).type);
      assertEquals(Integer.valueOf(1), jobMonitor.JOBS.get(0).TSDownload);
   }

   @Test
   void dryRunReportsTheMatchWithoutFetchingAnything() {
      title("ghosts").decrypt = 1;
      autoConfig.dryrun = 1;

      assertTrue(auto.keywordSearch(show("Ghosts", "Gate-gate", "")));
      assertTrue(jobMonitor.JOBS.isEmpty(), "dry run queued a download");
   }

   @Test
   void aShowMatchingTwoEntriesIsOnlyFetchedOnce() {
      // Overlapping rules are normal - a title entry for the series and a keyword entry for
      // the sport it is part of. Queueing it twice would download it twice.
      title("ghosts").decrypt = 1;
      keywords("seance").decrypt = 1;

      assertTrue(auto.keywordSearch(show("Ghosts", "Gate-gate", "The ghosts hold a seance.")));
      assertEquals(2, jobMonitor.JOBS.size(), "the show was queued by both entries");
   }

   @Test
   void aShowAlreadyInTheHistoryIsNotFetchedAgain() throws Exception {
      // auto.history is what stops the next pass over the Now Playing list re-downloading
      // everything the last one took.
      Files.write(Path.of(config.autoHistory),
         "EP012345670001 Ghosts - Gate-gate\r\n".getBytes(StandardCharsets.UTF_8));
      title("ghosts");

      auto.keywordSearch(show("Ghosts", "Gate-gate", ""));
      assertTrue(jobMonitor.JOBS.isEmpty(), "a show already fetched was queued again");
   }

   @Test
   void historyEntriesAreWrittenWithLeadingZerosStripped() throws Exception {
      // The XML and the RPC disagree about leading zeros in a program id, so both halves
      // normalise before comparing - otherwise the same show is fetched twice.
      Files.write(Path.of(config.autoHistory),
         "EP0000123401 Ghosts\r\n".getBytes(StandardCharsets.UTF_8));

      assertTrue(auto.keywordMatchHistory("EP123401"));
      assertTrue(auto.keywordMatchHistory("EP0000123401"));
      assertFalse(auto.keywordMatchHistory("EP123402"));
   }

   @Test
   void aDownloadedShowIsAddedToTheHistory() throws Exception {
      jobData job = new jobData();
      job.ProgramId = "EP012345670001";
      job.title = "Ghosts - Gate-gate";

      assertEquals(1, auto.AddHistoryEntry(job));
      assertEquals(2, auto.AddHistoryEntry(job), "the same show was written to history twice");
      assertTrue(new String(Files.readAllBytes(Path.of(config.autoHistory)),
         StandardCharsets.UTF_8).contains("EP012345670001 Ghosts - Gate-gate"));
   }

   @Test
   void anAutoSkipWorkingCopyIsKeptOutOfTheHistory() {
      // AutoSkip downloads a few seconds of a recording to find its cut points. Recording that
      // as fetched would stop auto transfers ever taking the show itself.
      jobData job = new jobData();
      job.ProgramId = "EP012345670001";
      job.tivoFile = Path.of(config.outputDir, "AutoSkip-Ghosts.TiVo").toString();

      assertEquals(0, auto.AddHistoryEntry(job));
      assertFalse(Files.exists(Path.of(config.autoHistory)));
   }

   @Test
   void theTivosAutoTransfersRunForAreTheEnabledEntriesOwn() {
      config.TIVOS.clear();
      config.TIVOS.put("Bolt", "192.168.1.5");
      config.TIVOS.put("Roamio", "192.168.1.6");
      config.autoIni = dir.resolve("auto.ini").toString();
      // getTiVos re-reads auto.ini, so the entries have to be in the file
      writeAutoIni("<title>\nghosts\n<options>\ntivo Roamio\n"
                 + "<title>\nelsbeth\n<options>\nenabled 0\ntivo Bolt\n");

      Stack<String> tivos = auto.getTiVos();
      assertEquals(1, tivos.size(), "expected only the enabled entry's TiVo: " + tivos);
      assertEquals("Roamio", tivos.get(0));

      // An entry for "all" means every TiVo, whatever the others say
      writeAutoIni("<title>\nghosts\n<options>\ntivo all\n");
      assertEquals(2, auto.getTiVos().size());
   }

   private void writeAutoIni(String body) {
      try {
         Files.write(Path.of(config.autoIni), body.getBytes(StandardCharsets.UTF_8));
      } catch (Exception e) {
         throw new IllegalStateException(e);
      }
      assertNotNull(autoConfig.parseAuto(config.autoIni));
   }
}
