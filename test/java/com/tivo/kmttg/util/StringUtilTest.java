package com.tivo.kmttg.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.kmttg;

// string holds the path, duration and url helpers the download paths lean on.
// The ISO 8601 converter is the load bearing one: the RPC hands back durations
// like PT1H30M, and every malformed variant has to come back null rather than
// throw, because callers read null as "length unknown".
public class StringUtilTest {

   private static final String SEP = File.separator;

   private static Boolean prevDebug;
   private static Boolean prevGuiMode;
   private static boolean prevStartingUp;

   @BeforeAll
   static void silenceLogging() {
      // The invalid-duration paths call log.error. Pin logging to stdout so no
      // debug.log lands next to the classes and no auto.log handler is opened.
      prevDebug = debug.enabled;
      prevGuiMode = config.GUIMODE;
      prevStartingUp = kmttg._startingUp;
      debug.enabled = false;
      config.GUIMODE = false;
      kmttg._startingUp = true;
   }

   @AfterAll
   static void restoreLogging() {
      debug.enabled = prevDebug;
      config.GUIMODE = prevGuiMode;
      kmttg._startingUp = prevStartingUp;
   }

   @Test
   void basenameReturnsTheLastPathSegment() {
      assertEquals("file.tar.gz", string.basename("C:" + SEP + "dir" + SEP + "file.tar.gz"));
      assertEquals("file.txt", string.basename("file.txt"));
   }

   @Test
   void basenameIgnoresATrailingSeparator() {
      // split() drops the empty trailing field, so a directory path with a
      // trailing slash still names the directory rather than coming back empty
      assertEquals("b", string.basename("a" + SEP + "b" + SEP));
   }

   @Test
   void dirnameReturnsEverythingBeforeTheLastSegment() {
      assertEquals("C:" + SEP + "dir", string.dirname("C:" + SEP + "dir" + SEP + "file.txt"));
      assertEquals(SEP + "srv" + SEP + "share",
         string.dirname(SEP + "srv" + SEP + "share" + SEP + "file.txt"));
   }

   @Test
   void dirnameIsEmptyWhenThereIsNoSeparator() {
      assertEquals("", string.dirname("file.txt"));
   }

   @Test
   void getSuffixTakesTheLastExtensionLowercased() {
      assertEquals("gz", string.getSuffix("file.tar.GZ"));
      assertEquals("tivo", string.getSuffix("Show.TiVo"));
   }

   @Test
   void getSuffixIsEmptyWithoutATrailingExtension() {
      assertEquals("", string.getSuffix("noext"));
      assertEquals("", string.getSuffix("file."));
      // A dotfile has nothing ahead of the dot, so it counts as no suffix
      assertEquals("", string.getSuffix(".hidden"));
   }

   @Test
   void replaceSuffixSwapsOnlyTheLastExtension() {
      assertEquals("show.tar.mpg", string.replaceSuffix("show.tar.gz", ".mpg"));
      // An empty replacement strips the extension outright
      assertEquals("show", string.replaceSuffix("show.TiVo", ""));
   }

   @Test
   void replaceSuffixLeavesANameWithoutAnExtensionAlone() {
      assertEquals("noext", string.replaceSuffix("noext", ".mpg"));
   }

   @Test
   void removeLeadingTrailingSpacesTrimsWhitespaceButNotTheMiddle() {
      assertEquals("a b", string.removeLeadingTrailingSpaces("  \t a b \t "));
      assertEquals("", string.removeLeadingTrailingSpaces("    "));
   }

   @Test
   void urlDecodeUnescapesPercentAndPlus() {
      assertEquals("a b c/d", string.urlDecode("a%20b+c%2Fd"));
   }

   @Test
   void urlDecodeFallsBackToTheInputOnAMalformedEscape() {
      // A bad escape raises IllegalArgumentException rather than the checked exception
      // this used to catch, and config decodes the install path through here at startup
      assertEquals("%zz", string.urlDecode("%zz"));
      assertEquals("100% done", string.urlDecode("100% done"));
   }

   @Test
   void isoDurationConvertsHoursAndMinutes() {
      assertEquals(Long.valueOf(5400000L), string.isoDurationToMsecs("PT1H30M"));
   }

   @Test
   void isoDurationConvertsDaysHoursMinutesAndSeconds() {
      assertEquals(Long.valueOf(93784000L), string.isoDurationToMsecs("P1DT2H3M4S"));
   }

   @Test
   void aLeadingMinusNegatesTheWholeDuration() {
      assertEquals(Long.valueOf(-30000L), string.isoDurationToMsecs("-PT30S"));
   }

   @Test
   void fractionalSecondsSurviveTheConversion() {
      assertEquals(Long.valueOf(1500L), string.isoDurationToMsecs("PT1.5S"));
      assertEquals(Long.valueOf(0L), string.isoDurationToMsecs("PT0S"));
   }

   @Test
   void minutesOnlyCountAfterTheTimeMarker() {
      // Ahead of T an M is months, which the converter refuses outright
      assertNull(string.isoDurationToMsecs("P1M"));
      assertEquals(Long.valueOf(60000L), string.isoDurationToMsecs("PT1M"));
   }

   @Test
   void invalidDurationReturnsNull() {
      assertNull(string.isoDurationToMsecs("1H"));   // no leading P
      assertNull(string.isoDurationToMsecs("PT"));   // T with nothing after it
      assertNull(string.isoDurationToMsecs("PT1"));  // value with no unit
      assertNull(string.isoDurationToMsecs("P1Y"));  // years are not handled
   }

   @Test
   void addPortReplacesAnExistingPort() {
      assertEquals("https://192.168.1.10:443/TiVoConnect?a=1",
         string.addPort("https://192.168.1.10:8080/TiVoConnect?a=1", "443"));
   }

   @Test
   void addPortAddsAPortWhenThereIsNone() {
      assertEquals("https://192.168.1.10:443/TiVoConnect?a=1",
         string.addPort("https://192.168.1.10/TiVoConnect?a=1", "443"));
   }

   @Test
   void addPortLeavesAUrlWithoutAPathAlone() {
      // The host pattern needs something after the trailing slash to match
      assertEquals("https://192.168.1.10", string.addPort("https://192.168.1.10", "443"));
      assertEquals("https://192.168.1.10/", string.addPort("https://192.168.1.10/", "443"));
   }

   @Test
   void getTimeRemainingReportsMinutesFromFiveUp() {
      // 100 bytes in 1 sec = 100 bytes/sec, 36000 bytes left = 360 secs
      assertEquals("time remaining: 6 mins", string.getTimeRemaining(2000, 1000, 36100, 100));
      // 30000 bytes left = 300 secs, exactly the 5 minute boundary
      assertEquals("time remaining: 5 mins", string.getTimeRemaining(2000, 1000, 30100, 100));
   }

   @Test
   void getTimeRemainingRollsMinutesIntoSecondsBelowFive() {
      assertEquals("time remaining: 9 secs", string.getTimeRemaining(2000, 1000, 1000, 100));
      // Under 5 minutes the whole estimate is reported in seconds, not 4m50s
      assertEquals("time remaining: 290 secs", string.getTimeRemaining(2000, 1000, 29100, 100));
   }

   @Test
   void getTimeRemainingIsNotAvailableWithoutProgress() {
      assertEquals("time remaining: N/A", string.getTimeRemaining(100, 100, 1000, 10));
      assertEquals("time remaining: N/A", string.getTimeRemaining(2000, 1000, 1000, 0));
   }

   @Test
   void getTimeRemainingIsNotAvailableOnAStalledTransfer() {
      // The rate is whole bytes/sec, so under a byte a second it truncates to zero.
      // Dividing by it used to abort the download this is only meant to be reporting on
      assertEquals("time remaining: N/A", string.getTimeRemaining(3000, 1000, 100000, 1));
   }

   @Test
   void utfStringLeavesAsciiUnchanged() {
      assertEquals("Plain ASCII 123", string.utfString("Plain ASCII 123"));
      assertEquals("", string.utfString(""));
   }
}
