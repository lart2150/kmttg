package com.tivo.kmttg.gui.sortable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Hashtable;
import java.util.Locale;
import java.util.Stack;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.tivo.kmttg.JSON.JSONObject;

// The value objects behind the sortable table columns. Each is built straight from an NPL
// recording entry, or from a Stack of them when the table collapses a series into a folder,
// and splits it into the string the cell shows and the key the column sorts on. All of the
// column formatting lives here, and so does every decision about what a missing or
// unparseable field turns into.
public class SortableTest {

   private static TimeZone prevZone;
   private static Locale prevLocale;

   @BeforeAll
   public static void pinFormatting() {
      // The displays come out of SimpleDateFormat and String.format, both of which read the
      // JVM defaults, so the expected strings below are only fixed if those are.
      prevZone = TimeZone.getDefault();
      prevLocale = Locale.getDefault();
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
      Locale.setDefault(Locale.US);
   }

   @AfterAll
   public static void restoreFormatting() {
      TimeZone.setDefault(prevZone);
      Locale.setDefault(prevLocale);
   }

   private static Hashtable<String,String> entry(String... keyValues) {
      Hashtable<String,String> h = new Hashtable<String,String>();
      for (int i = 0; i < keyValues.length; i += 2)
         h.put(keyValues[i], keyValues[i+1]);
      return h;
   }

   // ---- sortableShow -------------------------------------------------------------------

   @Test
   void aRecordingSplitsIntoTitleEpisodeAndDate() {
      sortableShow s = new sortableShow(entry(
         "title", "Firefly - Shindig", "titleOnly", "Firefly",
         "episodeTitle", "Shindig", "EpisodeNumber", "104", "gmt", "1400000000000"));
      assertFalse(s.folder);
      assertEquals("Firefly", s.titleOnly);
      assertEquals("firefly", s.TiVoTitle);
      assertEquals(1400000000000L, s.gmt);
      assertEquals("Firefly [Ep 104] - Shindig", s.toString());
   }

   @Test
   void anAllZeroEpisodeNumberIsDropped() {
      // A TiVo that has no episode number sends zeroes, and "[Ep 000]" in the cell is worse
      // than no episode at all
      sortableShow s = new sortableShow(entry(
         "title", "Firefly - Shindig", "titleOnly", "Firefly",
         "episodeTitle", "Shindig", "EpisodeNumber", "000", "gmt", "1400000000000"));
      assertEquals("", s.episodeNum);
      assertEquals("Firefly - Shindig", s.toString());
   }

   @Test
   void aMovieShowsItsYearInsteadOfAnEpisode() {
      sortableShow s = new sortableShow(entry(
         "title", "Blade Runner", "titleOnly", "Blade Runner",
         "movieYear", "1982", "gmt", "1400000000000"));
      assertEquals("Blade Runner [1982]", s.toString());
   }

   @Test
   void leadingArticlesAreStrippedAndTheRestIsLowercased() {
      sortableShow s = new sortableShow(entry(
         "title", "The Americans", "titleOnly", "The Americans", "gmt", "1400000000000"));
      assertEquals("americans", s.TiVoTitle);
      assertEquals("idiot abroad", s.removeLeadingArticles("An Idiot Abroad"));
      assertEquals("bit of fry and laurie", s.removeLeadingArticles("A Bit of Fry and Laurie"));
      // Only a leading article goes - a title that has none is still folded to lower case,
      // which is what makes the alternate sort case insensitive
      assertEquals("band of brothers", s.removeLeadingArticles("Band of Brothers"));
      assertEquals("", s.removeLeadingArticles(null));
   }

   @Test
   void aFolderCountsItsChildrenAndTakesTheDateOfOne() {
      Stack<Hashtable<String,String>> f = new Stack<Hashtable<String,String>>();
      f.add(entry("title", "Firefly - Serenity", "titleOnly", "Firefly", "gmt", "1400000000000"));
      f.add(entry("title", "Firefly - Shindig", "titleOnly", "Firefly", "gmt", "1262390400000"));
      f.add(entry("title", "Firefly - Ariel", "titleOnly", "Firefly", "gmt", "1300000000000"));
      sortableShow s = new sortableShow("Firefly", f, 1);
      assertTrue(s.folder);
      assertEquals(3, s.numEntries);
      assertEquals(1262390400000L, s.gmt, "the gmt must come from the indexed child, not the first");
      assertEquals("Firefly", s.title);
      assertEquals("Firefly (3)", s.toString());
   }

   // ---- sortableDate -------------------------------------------------------------------

   @Test
   void aRecordingDateIsFormattedFromTheGmtKey() {
      sortableDate d = new sortableDate(entry("gmt", "1400000000000"));
      assertEquals("Tue 05/13/14 04:53 PM ", d.display);
      assertEquals("1400000000000", d.sortable);
      assertFalse(d.folder);
   }

   @Test
   void aFolderDateComesFromTheIndexedChild() {
      Stack<Hashtable<String,String>> f = new Stack<Hashtable<String,String>>();
      f.add(entry("gmt", "1400000000000"));
      f.add(entry("gmt", "1262390400000"));
      sortableDate d = new sortableDate("Firefly", f, 1);
      assertTrue(d.folder);
      assertEquals("Firefly", d.folderName);
      assertEquals("1262390400000", d.sortable);
      assertEquals("Sat 01/02/10 12:00 AM ", d.display);
      assertSame(f, d.folderData);
   }

   @Test
   void onlyMinusOneCountsAsNoDateOnTheJsonPath() {
      // Zero blanks the sort key but is still handed to the formatter, so the cell reads as
      // the epoch rather than as empty
      sortableDate zero = new sortableDate(new JSONObject(), 0);
      assertEquals("0", zero.sortable);
      assertEquals("Thu 01/01/70 12:00 AM", zero.display);
      sortableDate none = new sortableDate(new JSONObject(), -1);
      assertEquals("", none.display);
      assertEquals("0", none.sortable);
   }

   @Test
   void theNamedFolderConstructorBlanksBothForAnUnsetTimestamp() {
      sortableDate unset = new sortableDate("Firefly", new JSONObject(), 0);
      assertTrue(unset.folder);
      assertEquals("", unset.display, "unlike the plain json constructor, zero blanks the cell");
      assertEquals("0", unset.sortable);
      sortableDate dated = new sortableDate("Firefly", new JSONObject(), 1400000000000L);
      assertEquals("Tue 05/13/14 04:53 PM", dated.display);
      assertEquals("1400000000000", dated.sortable);
   }

   @Test
   void aJsonFolderStackCarriesNoDateAtAll() {
      Stack<JSONObject> f = new Stack<JSONObject>();
      f.add(new JSONObject());
      sortableDate d = new sortableDate("Firefly", f);
      assertTrue(d.folder);
      assertEquals("", d.display);
      assertEquals("0", d.sortable);
      assertSame(f, d.folderData_json);
   }

   // ---- sortableDuration ---------------------------------------------------------------

   @Test
   void aDurationRoundsToTheNearestMinutePastThirtySeconds() {
      assertEquals(" 0:30 ", new sortableDuration(entry("duration", "1800000")).display);
      // Strictly greater than 30, so exactly half a minute stays put
      assertEquals(" 0:30 ", new sortableDuration(entry("duration", "1830000")).display);
      assertEquals(" 0:31 ", new sortableDuration(entry("duration", "1831000")).display);
      // Rounding 59:31 up has to carry into the hour rather than print 0:60
      assertEquals(" 1:00 ", new sortableDuration(entry("duration", "3571000")).display);
      assertEquals(1800000L, new sortableDuration(entry("duration", "1800000")).sortable.longValue());
   }

   @Test
   void aMissingOrUnparseableDurationIsZero() {
      sortableDuration missing = new sortableDuration(entry("title", "Firefly"));
      assertEquals(0L, missing.sortable.longValue());
      assertEquals(" 0:00 ", missing.display);
      sortableDuration bad = new sortableDuration(entry("duration", "1:30:00"));
      assertEquals(0L, bad.sortable.longValue());
      assertEquals(" 0:00 ", bad.display);
   }

   @Test
   void aDurationTooLargeToBeMillisecondsIsDividedByAThousand() {
      // Over 100 hours the value cannot be a recording length in ms, so it is rescaled
      assertEquals(363600L, new sortableDuration(entry("duration", "363600000")).sortable.longValue());
      assertEquals(" 0:06 ", new sortableDuration(entry("duration", "363600000")).display);
      // One millisecond under the threshold is left alone and shown as the 100 hours it claims
      assertEquals(363599999L, new sortableDuration(entry("duration", "363599999")).sortable.longValue());
      assertEquals(" 101:00 ", new sortableDuration(entry("duration", "363599999")).display);
   }

   @Test
   void aFolderSumsItsChildrenDurations() {
      Stack<Hashtable<String,String>> f = new Stack<Hashtable<String,String>>();
      f.add(entry("duration", "1800000"));
      f.add(entry("duration", "3600000"));
      f.add(entry("title", "Firefly"));      // no duration at all, contributes nothing
      f.add(entry("duration", "oops"));      // nor does one that will not parse
      sortableDuration d = new sortableDuration(f);
      assertEquals(5400000L, d.sortable.longValue());
      assertEquals(" 1:30 ", d.display);
   }

   @Test
   void aSummedFolderIsNeverRescaled() {
      // The single entry path divides anything over 100 hours by 1000; the folder path has no
      // such rescue, so a genuinely long folder keeps its real total
      Stack<Hashtable<String,String>> f = new Stack<Hashtable<String,String>>();
      for (int i = 0; i < 3; i++)
         f.add(entry("duration", "200000000"));
      sortableDuration d = new sortableDuration(f);
      assertEquals(600000000L, d.sortable.longValue());
      assertEquals(" 166:40 ", d.display);
   }

   @Test
   void anEmptyFolderNeverGetsADisplay() {
      // display is only assigned inside the accumulate loop
      sortableDuration d = new sortableDuration(new Stack<Hashtable<String,String>>());
      assertEquals(0L, d.sortable.longValue());
      assertNull(d.display);
   }

   @Test
   void theLongConstructorsDifferOverSecondsAndOverZero() {
      assertEquals(" 1:30:30", new sortableDuration(5430000L).display);
      assertEquals(" 0:00:00", new sortableDuration(0L).display);
      // The two argument form blanks a zero instead of printing one
      assertEquals("", new sortableDuration(0L, true).display);
      assertEquals(" 1:30 ", new sortableDuration(5430000L, false).display);
      assertEquals(5430000L, new sortableDuration(5430000L, false).sortable.longValue());
      // Both of these carry the same over-100-hours rescue as the entry constructor
      assertEquals(363600L, new sortableDuration(363600000L).sortable.longValue());
      assertEquals(" 0:06:03", new sortableDuration(363600000L).display);
      assertEquals(363600L, new sortableDuration(363600000L, false).sortable.longValue());
   }

   // ---- sortableSize -------------------------------------------------------------------

   @Test
   void aRecordingSizeKeepsThePreformattedLabelAndTheRawByteCount() {
      sortableSize s = new sortableSize(entry("size", "1610612736", "sizeGB", "1.50 GB"));
      assertEquals("1.50 GB ", s.display);
      assertEquals(1610612736L, s.sortable);
   }

   @Test
   void aFolderSumsBytesAndFormatsGibibytes() {
      Stack<Hashtable<String,String>> f = new Stack<Hashtable<String,String>>();
      for (int i = 0; i < 3; i++)
         f.add(entry("size", "1073741824"));
      sortableSize s = new sortableSize(f);
      assertEquals(3221225472L, s.sortable);
      assertEquals("3.00 GB ", s.display);
      // Formatted outside the loop here, so unlike the duration folder an empty stack is 0
      assertEquals("0.00 GB ", new sortableSize(new Stack<Hashtable<String,String>>()).display);
      assertEquals("1.50 GB ", new sortableSize(1610612736L).display);
   }

   // ---- sortableChannel / sortableChannelNum -------------------------------------------

   @Test
   void aDashedChannelNumberSortsAsADecimal() {
      sortableChannelNum c = new sortableChannelNum("704-1");
      assertEquals("704-1", c.display);
      assertEquals(704.1f, c.sortable, 0.001f);
      assertEquals(11.0f, new sortableChannelNum("11").sortable, 0.001f);
   }

   @Test
   void aChannelPairsItsNumberWithItsCallSign() {
      sortableChannel c = new sortableChannel("KTVU", "2");
      assertEquals("2=KTVU ", c.display);
      assertEquals(2.0f, c.sortable, 0.001f);
      // A folder whose children are on different channels keeps the marker nplTable passes
      sortableChannel various = new sortableChannel("<various>", "0");
      assertEquals("<various> ", various.display);
      assertEquals(0.0f, various.sortable, 0.001f);
   }

   @Test
   void aChannelMissingEitherHalfDisplaysNothingAndSortsToZero() {
      sortableChannel[] blanks = {
         new sortableChannel("", ""), new sortableChannel("KTVU", ""), new sortableChannel("", "2")
      };
      for (sortableChannel c : blanks) {
         assertEquals("", c.display);
         assertEquals(0.0f, c.sortable, 0.001f);
      }
   }

   @Test
   void theJsonChannelConstructorKeepsTheChannelStringVerbatim() {
      sortableChannel c = new sortableChannel(new JSONObject(), "13-2");
      assertEquals("13-2", c.display, "no trailing space is appended on this path");
      assertEquals(13.2f, c.sortable, 0.001f);
   }

   @Test
   void aNonNumericChannelNumberThrowsRatherThanSortingToZero() {
      // Neither of these guards the parse the way sortableDuration does
      assertThrows(NumberFormatException.class, () -> new sortableChannelNum("KTVU"));
      assertThrows(NumberFormatException.class, () -> new sortableChannel("<various>", ""));
   }

   // ---- sortableDouble / sortableInt / sortableString -----------------------------------

   @Test
   void aDoubleIsPaddedToTwoDecimals() {
      sortableDouble d = new sortableDouble(3.14159);
      assertEquals(" 3.14 ", d.display);
      assertEquals(3.14159, d.sortable, 0.00001);
      assertEquals(" 0.00 ", new sortableDouble(0.0).display);
      assertEquals(" 2.35 ", new sortableDouble(2.349).display);
   }

   @Test
   void theScalarCellsCarryTheirJsonAlongsideTheDisplay() {
      JSONObject json = new JSONObject();
      sortableInt i = new sortableInt(json, 3);
      assertEquals("3", i.display);
      assertEquals(3, i.sortable);
      assertSame(json, i.json);

      sortableString s = new sortableString(json, "Firefly");
      assertEquals("Firefly", s.display);
      assertSame(json, s.json);

      // The no argument form is the placeholder an unfilled row starts life with
      sortableString empty = new sortableString();
      assertEquals("", empty.display);
      assertNotNull(empty.json);
   }
}
