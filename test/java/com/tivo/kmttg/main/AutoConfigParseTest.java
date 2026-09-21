package com.tivo.kmttg.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Stack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// auto.ini decides what an unattended kmttg downloads. It is re-read on every pass of the auto
// loop, so a file it cannot get through does not just fail once - it stops auto transfers for
// good, from inside a timer where the user sees nothing but a stack trace. Users also hand edit
// it, which is why the malformed cases below are the interesting ones.
public class AutoConfigParseTest {

   @TempDir Path dir;

   private int savedInterval, savedDryrun, savedNoJobWait, savedDateFilter;
   private int savedSuggestions, savedKuid, savedProgramId;
   private String savedDateOperator;
   private float savedDateHours;
   private Stack<autoEntry> savedKeywords;
   // A skipped setting logs. Pin logging to stdout so no auto.log handler is opened.
   private boolean savedStartingUp;

   @BeforeEach
   void snapshot() {
      savedStartingUp = kmttg._startingUp;
      kmttg._startingUp = true;
      savedInterval = autoConfig.CHECK_TIVOS_INTERVAL;
      savedDryrun = autoConfig.dryrun;
      savedNoJobWait = autoConfig.noJobWait;
      savedDateFilter = autoConfig.dateFilter;
      savedSuggestions = autoConfig.suggestionsFilter;
      savedKuid = autoConfig.kuidFilter;
      savedProgramId = autoConfig.programIdFilter;
      savedDateOperator = autoConfig.dateOperator;
      savedDateHours = autoConfig.dateHours;
      savedKeywords = autoConfig.KEYWORDS;
      autoConfig.KEYWORDS = new Stack<autoEntry>();
   }

   @AfterEach
   void restore() {
      kmttg._startingUp = savedStartingUp;
      autoConfig.CHECK_TIVOS_INTERVAL = savedInterval;
      autoConfig.dryrun = savedDryrun;
      autoConfig.noJobWait = savedNoJobWait;
      autoConfig.dateFilter = savedDateFilter;
      autoConfig.suggestionsFilter = savedSuggestions;
      autoConfig.kuidFilter = savedKuid;
      autoConfig.programIdFilter = savedProgramId;
      autoConfig.dateOperator = savedDateOperator;
      autoConfig.dateHours = savedDateHours;
      autoConfig.KEYWORDS = savedKeywords;
   }

   private boolean parse(String body) throws Exception {
      Path ini = dir.resolve("auto.ini");
      Files.write(ini, body.getBytes(StandardCharsets.UTF_8));
      return autoConfig.parseAuto(ini.toString()).booleanValue();
   }

   private static autoEntry entry(int i) {
      return autoConfig.KEYWORDS.get(i);
   }

   @Test
   void aTitleEntryCarriesItsOwnOptions() throws Exception {
      // The shape configAuto writes: the keyword line first, then the options that belong to it
      assertTrue(parse(
         "<title>\nghosts\n<options>\nenabled 1\ntivo Living Room\nTSDownload 1\n" +
         "metadata 1\ndecrypt 1\ncomskip 1\ncomcut 1\ncaptions 0\nencode 1\ncustom 0\n" +
         "encode_name my h264 profile\ncomskipIni none\n"));

      assertEquals(1, autoConfig.KEYWORDS.size());
      assertEquals("title", entry(0).type);
      assertEquals("ghosts", entry(0).keyword);
      // tivo names and profile names have spaces, so these read to end of line rather than
      // taking the second whitespace token
      assertEquals("Living Room", entry(0).tivo);
      assertEquals("my h264 profile", entry(0).encode_name);
      assertEquals(1, entry(0).decrypt);
      assertEquals(1, entry(0).comcut);
      assertEquals(0, entry(0).captions);
   }

   @Test
   void eachEntrysOptionsStayWithThatEntry() throws Exception {
      // The parser keeps one entry open until the next keyword line closes it, so an option
      // bleeding into the following entry would download the wrong things for it.
      assertTrue(parse(
         "<title>\nghosts\n<options>\nencode 1\ntivo Bolt\n" +
         "<title>\nelsbeth\n<options>\ncomskip 1\n" +
         "<keywords>\nfootball|-college\n<options>\nenabled 0\n"));

      assertEquals(3, autoConfig.KEYWORDS.size());
      assertEquals(1, entry(0).encode);
      assertEquals("Bolt", entry(0).tivo);
      assertEquals(0, entry(1).encode, "the first entry's encode leaked into the second");
      assertEquals("all", entry(1).tivo, "the first entry's tivo leaked into the second");
      assertEquals(1, entry(1).comskip);
      assertEquals("keywords", entry(2).type);
      assertEquals(0, entry(2).enabled);
   }

   @Test
   void keywordEntriesSplitOnThePipe() throws Exception {
      assertTrue(parse("<keywords>\n(basketball)|(football)|-new york\n"));

      assertEquals(3, entry(0).keywords.size());
      assertEquals("(basketball)", entry(0).keywords.get(0));
      assertEquals("-new york", entry(0).keywords.get(2));
      // And back out the same way for the config table
      assertEquals("(basketball)|(football)|-new york",
         autoConfig.keywordsToString(entry(0).keywords));
   }

   @Test
   void globalSettingsLoadFromTheirOwnSections() throws Exception {
      assertTrue(parse(
         "<check_tivos_interval>\n15\n<dryrun>\n1\n<noJobWait>\n1\n" +
         "<dateFilter>\n1\n<dateOperator>\nmore than\n<dateHours>\n72.5\n" +
         "<suggestionsFilter>\n1\n<kuidFilter>\n1\n<programIdFilter>\n1\n"));

      assertEquals(15, autoConfig.CHECK_TIVOS_INTERVAL);
      assertEquals(1, autoConfig.dryrun);
      assertEquals(1, autoConfig.noJobWait);
      assertEquals(1, autoConfig.dateFilter);
      assertEquals("more than", autoConfig.dateOperator);
      assertEquals(72.5f, autoConfig.dateHours);
      assertEquals(1, autoConfig.suggestionsFilter);
      assertEquals(1, autoConfig.kuidFilter);
      assertEquals(1, autoConfig.programIdFilter);
   }

   @Test
   void commentsAndBlankLinesAreIgnored() throws Exception {
      assertTrue(parse("# kmttg auto.ini\n\n   \n<title>\n   ghosts   \n\n# note\n"));

      assertEquals(1, autoConfig.KEYWORDS.size());
      assertEquals("ghosts", entry(0).keyword);
   }

   @Test
   void aRemovedEntryIsGoneAfterTheNextParse() throws Exception {
      // The auto loop re-reads the file every pass so the user can edit it while it runs.
      assertTrue(parse("<title>\nghosts\n<title>\nelsbeth\n"));
      assertEquals(2, autoConfig.KEYWORDS.size());

      assertTrue(parse("<title>\nghosts\n"));
      assertEquals(1, autoConfig.KEYWORDS.size(), "a deleted entry is still being downloaded");
   }

   @Test
   void anAutoIniThatDoesNotExistYetIsCreatedEmpty() throws Exception {
      Path ini = dir.resolve("new-auto.ini");
      assertTrue(autoConfig.parseAuto(ini.toString()));
      assertTrue(Files.isRegularFile(ini));
      assertTrue(autoConfig.KEYWORDS.isEmpty());
   }

   @Test
   void aChannelFilterBecomesTheListTheFilterWalks() throws Exception {
      assertTrue(parse("<title>\nghosts\n<options>\nchannelExcludes 1\nchannelFilter 702,QVC\n"));

      assertEquals("702,QVC", entry(0).channelFilter);
      assertEquals(2, entry(0).channelFilterList.size());
      assertEquals("702", entry(0).channelFilterList.get(0));
      assertEquals("QVC", entry(0).channelFilterList.get(1));
      assertEquals(1, entry(0).channelExcludes);
   }

   // ---- hand edited files -------------------------------------------------

   @Test
   void aSettingBeforeAnySectionHeaderDoesNotTakeDownTheAutoLoop() throws Exception {
      // A stray line at the top of a hand edited auto.ini. There is no section open yet, and
      // the key the parser matches on is still null - which threw NullPointerException out of
      // parseAuto, out of jobMonitor.monitor and onto the timer.
      assertTrue(parse("ghosts\n<title>\nelsbeth\n"));

      assertEquals(1, autoConfig.KEYWORDS.size(), "the entries after the bad line were lost");
      assertEquals("elsbeth", entry(0).keyword);
   }

   @Test
   void anOptionWithNoValueCostsNothingButItself() throws Exception {
      // Half a line - a name with the value deleted. Everything after it in the file still has
      // to load, the same way a bad config.ini setting does.
      assertTrue(parse("<title>\nghosts\n<options>\nenabled\ncomskip 1\n"));

      assertEquals(1, autoConfig.KEYWORDS.size());
      assertEquals(1, entry(0).enabled, "the default was lost along with the bad line");
      assertEquals(1, entry(0).comskip, "options after the bad line still load");
   }

   @Test
   void anOptionWithAValueThatIsNotANumberCostsNothingButItself() throws Exception {
      assertTrue(parse("<title>\nghosts\n<options>\ndecrypt yes\nencode 1\n"));

      assertEquals(0, entry(0).decrypt);
      assertEquals(1, entry(0).encode, "options after the bad line still load");
   }

   @Test
   void anUnknownSectionIsIgnoredRatherThanFatal() throws Exception {
      assertTrue(parse("<somethingNew>\n42\n<title>\nghosts\n"));

      assertEquals(1, autoConfig.KEYWORDS.size());
      assertEquals("ghosts", entry(0).keyword);
   }

   @Test
   void anEntryWithNoOptionsKeepsTheDefaults() throws Exception {
      assertTrue(parse("<title>\nghosts\n"));

      autoEntry e = entry(0);
      assertEquals(1, e.enabled, "an entry with no options block must still be enabled");
      assertEquals("all", e.tivo);
      assertEquals("none", e.comskipIni);
      assertNull(e.channelFilter);
      assertNull(e.encode_name);
      assertNotNull(e.channelFilterList);
      assertFalse(e.channelFilterList.size() > 0);
   }
}
