package com.tivo.kmttg.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Hashtable;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.rpc.Fixtures;
import com.tivo.kmttg.util.parseNPL;

// Every downloaded file is named from the tivoFileNameFormat template, so a
// broken [keyword] renames files for everyone using that template. The date
// keywords are formatted from the recording's gmt stamp in the default time
// zone, so the expected date parts are derived the same way rather than
// written down - the suite has to pass wherever it runs.
public class TivoFileNameTest {

   // 2020-09-13T12:26:40Z. Any instant will do, it just has to be fixed.
   private static final long GMT = 1600000000000L;

   private String previousFormat;

   @BeforeEach
   public void saveConfig() {
      previousFormat = config.tivoFileNameFormat;
   }

   @AfterEach
   public void restoreConfig() {
      config.tivoFileNameFormat = previousFormat;
   }

   @Test
   public void theDefaultTemplateIsTitleAndRecordingDate() {
      config.tivoFileNameFormat = null;
      String[] t = dateParts();
      assertEquals("The Orville (" + t[3] + "_" + t[2] + "_" + t[6] + ").TiVo",
         tivoFileName.buildTivoFileName(entry()));
   }

   // Auto transfers carry their own template on the entry
   @Test
   public void aFormatOnTheEntryOverridesTheConfiguredOne() {
      config.tivoFileNameFormat = "[title]";
      Hashtable<String,String> entry = entry();
      entry.put("episodeTitle", "Old Wounds");
      entry.put("tivoFileNameFormat", "[episodeTitle]");
      assertEquals("Old Wounds.TiVo", tivoFileName.buildTivoFileName(entry));
   }

   @Test
   public void theDateKeywordsAllComeFromTheGmtStamp() {
      String[] t = dateParts();
      config.tivoFileNameFormat = "[year]-[monthNum]-[mday]_[hour][min]_[wday]_[month]";
      assertEquals(t[6] + "-" + t[3] + "-" + t[2] + "_" + t[1] + t[0] + "_" + t[4] + "_" + t[5] + ".TiVo",
         tivoFileName.buildTivoFileName(entry()));
   }

   @Test
   public void eachRecordingKeywordIsTakenFromTheEntry() {
      String[][] cases = {
         {"title",         "The Orville"},
         {"episodeTitle",  "Old Wounds"},
         {"channelNum",    "11-1"},
         {"channel",       "WTTWDT"},
         {"tivoName",      "Bolt"},
         {"description",   "The crew takes on a mission"},
         {"movieYear",     "1984"},
         {"EpisodeNumber", "102"},
         {"recordingId",   "tivo.rc.12"},
         {"contentId",     "tivo.ct.34"},
         {"collectionId",  "tivo.cl.56"},
      };
      Hashtable<String,String> values = new Hashtable<String,String>();
      for (String[] c : cases)
         values.put(c[0], c[1]);
      for (String[] c : cases) {
         Hashtable<String,String> entry = entry();
         entry.putAll(values);
         config.tivoFileNameFormat = "[" + c[0] + "]";
         assertEquals(c[1] + ".TiVo", tivoFileName.buildTivoFileName(entry), c[0]);
      }
   }

   // A keyword with no value collapses to nothing; one that is not a keyword at
   // all is left in the name as literal text
   @Test
   public void anEmptyKeywordDisappearsAndAnUnknownOneIsLeftAlone() {
      config.tivoFileNameFormat = "[title]-[episodeTitle]-[bogus]";
      assertEquals("The Orville--bogus.TiVo", tivoFileName.buildTivoFileName(entry()));
   }

   @Test
   public void aThreeDigitEpisodeNumberSplitsOneAndTwoAndPadsTheSeason() {
      Hashtable<String,String> entry = entry();
      entry.put("EpisodeNumber", "102");
      config.tivoFileNameFormat = "[title]_[SeriesEpNumber]";
      assertEquals("The Orville_s01e02.TiVo", tivoFileName.buildTivoFileName(entry));
      // the derived halves are written back for the rest of the template to use
      assertEquals("01", entry.get("season"));
      assertEquals("02", entry.get("episode"));
   }

   @Test
   public void aFourDigitEpisodeNumberSplitsTwoAndTwo() {
      Hashtable<String,String> entry = entry();
      entry.put("EpisodeNumber", "1023");
      config.tivoFileNameFormat = "[season]x[episode]";
      assertEquals("10x23.TiVo", tivoFileName.buildTivoFileName(entry));
   }

   // Everything past the first two digits is the episode, so a long number is
   // not truncated
   @Test
   public void anEpisodeNumberLongerThanFourDigitsKeepsTheRemainderAsTheEpisode() {
      Hashtable<String,String> entry = entry();
      entry.put("EpisodeNumber", "12345");
      config.tivoFileNameFormat = "[SeriesEpNumber]";
      assertEquals("s12e345.TiVo", tivoFileName.buildTivoFileName(entry));
   }

   @Test
   public void anEpisodeNumberOfTwoDigitsOrLessIsNotSplit() {
      for (String epnum : new String[] {"", "1", "12"}) {
         Hashtable<String,String> entry = entry();
         entry.put("EpisodeNumber", epnum);
         config.tivoFileNameFormat = "[title][SeriesEpNumber]";
         assertEquals("The Orville.TiVo", tivoFileName.buildTivoFileName(entry), epnum);
         assertEquals("", entry.get("SeriesEpNumber"), epnum);
      }
   }

   // The RPC NPL supplies season and episode separately, and those win over
   // anything derived from EpisodeNumber - including the zero padding
   @Test
   public void anEntryThatAlreadyCarriesSeasonAndEpisodeIsUsedAsIs() {
      Hashtable<String,String> entry = entry();
      entry.put("EpisodeNumber", "1023");
      entry.put("season", "3");
      entry.put("episode", "7");
      config.tivoFileNameFormat = "[SeriesEpNumber]";
      assertEquals("s3e7.TiVo", tivoFileName.buildTivoFileName(entry));
   }

   // Quoted text inside the brackets is conditional on the keyword beside it
   @Test
   public void quotedTextIsDroppedWithTheKeywordItDecorates() {
      config.tivoFileNameFormat = "[title][\" - \" episodeTitle]";
      Hashtable<String,String> entry = entry();
      entry.put("episodeTitle", "Old Wounds");
      assertEquals("The Orville - Old Wounds.TiVo", tivoFileName.buildTivoFileName(entry));
      assertEquals("The Orville.TiVo", tivoFileName.buildTivoFileName(entry()));
   }

   @Test
   public void mainTitleMapsToTheTitleWithoutTheEpisode() {
      Hashtable<String,String> entry = entry();
      entry.put("title", "The Orville - Old Wounds");
      entry.put("titleOnly", "The Orville");
      config.tivoFileNameFormat = "[mainTitle]";
      assertEquals("The Orville.TiVo", tivoFileName.buildTivoFileName(entry));
   }

   @Test
   public void originalAirDateFallsBackToTheRecordingDate() {
      String[] t = dateParts();
      config.tivoFileNameFormat = "[originalAirDate]_[oad_no_dashes]";
      assertEquals(t[6] + "-" + t[3] + "-" + t[2] + "_" + t[6] + t[3] + t[2] + ".TiVo",
         tivoFileName.buildTivoFileName(entry()));

      Hashtable<String,String> entry = entry();
      entry.put("originalAirDate", "2026-09-18");
      assertEquals("2026-09-18_20260918.TiVo", tivoFileName.buildTivoFileName(entry));
   }

   // startTime is filled in from extended metadata, which needs a tivo to ask -
   // without one the keyword evaluates empty rather than blocking the download
   @Test
   public void startTimeIsOnlyResolvedForAnEntryThatNamesItsTivo() {
      config.tivoFileNameFormat = "[title]_[startTime]";
      Hashtable<String,String> entry = entry();
      entry.put("tivoName", "Bolt");
      entry.put("startTime", "2026-09-19_2100");
      assertEquals("The Orville_2026-09-19_2100.TiVo", tivoFileName.buildTivoFileName(entry));
      assertEquals("The Orville_.TiVo", tivoFileName.buildTivoFileName(entry()));
   }

   @Test
   public void charactersAWindowsPathCannotHoldAreRemovedOrReplaced() {
      Hashtable<String,String> entry = entry();
      entry.put("title", "Bob's *Burger* & \"Fries\": Part 2/3 | Ep<1>?!");
      config.tivoFileNameFormat = "[title]";
      assertEquals("Bobs Burger and Fries Part 2_3 _ Ep_1_.TiVo", tivoFileName.buildTivoFileName(entry));
   }

   @Test
   public void removeSpecialCharsMapsEachCharacterTheSameWayOnItsOwn() {
      assertEquals("a_b", tivoFileName.removeSpecialChars("a/b"));
      assertEquals("ab", tivoFileName.removeSpecialChars("a\\b"));
      assertEquals("aandb", tivoFileName.removeSpecialChars("a&b"));
      assertEquals("a_b_c_d", tivoFileName.removeSpecialChars("a<b>c|d"));
      assertEquals("abcdefg", tivoFileName.removeSpecialChars("a*b\"c'd:e;f`g"));
      assertEquals("abc", tivoFileName.removeSpecialChars("a!b?c"));
      assertEquals("ab", tivoFileName.removeSpecialChars("a$b"));
      // The "^" rule is a regex anchor matching the empty start of the string,
      // so a literal caret is left in the name
      assertEquals("a^b", tivoFileName.removeSpecialChars("a^b"));
   }

   @Test
   public void theSubFolderKeywordBecomesAPathSeparator() {
      Hashtable<String,String> entry = entry();
      entry.put("episodeTitle", "Old Wounds");
      config.tivoFileNameFormat = "[title][/][title] - [episodeTitle]";
      assertEquals("The Orville" + File.separator + "The Orville - Old Wounds.TiVo",
         tivoFileName.buildTivoFileName(entry));
   }

   // Windows silently drops a trailing dot or space from a directory name, so
   // the folder would not be the one kmttg thinks it created
   @Test
   public void aFolderMayNotEndInDotsOrSpaces() {
      Hashtable<String,String> entry = entry();
      entry.put("title", "Dr. Who. ");
      entry.put("episodeTitle", "Old Wounds");
      config.tivoFileNameFormat = "[title][/][episodeTitle]";
      assertEquals("Dr. Who" + File.separator + "Old Wounds.TiVo", tivoFileName.buildTivoFileName(entry));
   }

   @Test
   public void aTemplateThatLeavesTheBaseNameEmptyIsRefused() {
      config.tivoFileNameFormat = "[episodeTitle]";
      assertNull(tivoFileName.buildTivoFileName(entry()));
      // and the same once a folder has been named, where only the base is empty
      config.tivoFileNameFormat = "[title][/][episodeTitle]";
      assertNull(tivoFileName.buildTivoFileName(entry()));
   }

   // Real captured recordings through a template using most of the keywords:
   // whatever a title contains, what comes out has to be writable here
   @Test
   public void everyCapturedRecordingProducesAUsableFileName() throws Exception {
      JSONArray myshows = Fixtures.load("myshows.json");
      assertTrue(myshows.length() > 0, "expected My Shows entries");
      config.tivoFileNameFormat =
         "[mainTitle][/][title][\"-\" SeriesEpNumber]-[wday]_[month]_[mday]_[hour][min]";

      for (int i = 0; i < myshows.length(); i++) {
         Hashtable<String,String> entry = parseNPL.rpcToHashEntry(
            "Bolt", myshows.getJSONObject(i).getJSONArray("recording").getJSONObject(0));
         String name = tivoFileName.buildTivoFileName(entry);
         assertNotNull(name, "no file name for row " + i);
         assertTrue(name.endsWith(".TiVo"), name);
         for (String part : name.split(Pattern.quote(File.separator))) {
            for (char c : "*?\"<>|:/\\".toCharArray())
               assertTrue(part.indexOf(c) < 0, "row " + i + " kept a '" + c + "': " + name);
         }
         String folder = name.substring(0, name.indexOf(File.separator));
         assertTrue(! folder.endsWith(".") && ! folder.endsWith(" "), "row " + i + " folder: " + name);
      }
   }

   private static Hashtable<String,String> entry() {
      Hashtable<String,String> entry = new Hashtable<String,String>();
      entry.put("gmt", "" + GMT);
      entry.put("title", "The Orville");
      entry.put("titleOnly", "The Orville");
      return entry;
   }

   // min hour mday monthNum wday month year, as buildTivoFileName derives them
   private static String[] dateParts() {
      return new SimpleDateFormat("mm HH dd MM E MMM yyyy").format(GMT).split("\\s+");
   }
}
