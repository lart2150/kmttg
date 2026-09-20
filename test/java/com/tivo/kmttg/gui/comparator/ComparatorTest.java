package com.tivo.kmttg.gui.comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tivo.kmttg.gui.sortable.sortableChannel;
import com.tivo.kmttg.gui.sortable.sortableChannelNum;
import com.tivo.kmttg.gui.sortable.sortableDate;
import com.tivo.kmttg.gui.sortable.sortableDouble;
import com.tivo.kmttg.gui.sortable.sortableDuration;
import com.tivo.kmttg.gui.sortable.sortableShow;
import com.tivo.kmttg.gui.sortable.sortableSize;
import com.tivo.kmttg.gui.table.imageCell;
import com.tivo.kmttg.main.config;

// The table column comparators. Each one is handed the sortable value object for its column
// and orders rows by the key inside it. Two things about them are load bearing and easy to
// lose: they all answer 0 when either side is null rather than throwing, which is what keeps
// a sort of a half built table from taking the table down, and several of them answer 0 for
// values they cannot read at all, leaving those rows in whatever order they were already in.
public class ComparatorTest {

   private int prevTiVoSort;

   @BeforeEach
   public void saveSortMode() {
      prevTiVoSort = config.TiVoSort;
   }

   @AfterEach
   public void restoreSortMode() {
      config.TiVoSort = prevTiVoSort;
   }

   private static sortableShow show(String titleOnly, String episodeNum, long gmt) {
      Hashtable<String,String> h = new Hashtable<String,String>();
      h.put("title", titleOnly);
      h.put("titleOnly", titleOnly);
      h.put("gmt", "" + gmt);
      if (episodeNum.length() > 0)
         h.put("EpisodeNumber", episodeNum);
      return new sortableShow(h);
   }

   private static sortableDate date(long gmt) {
      Hashtable<String,String> h = new Hashtable<String,String>();
      h.put("gmt", "" + gmt);
      return new sortableDate(h);
   }

   private static imageCell cell(String imageName) {
      imageCell c = new imageCell();
      c.imageName = imageName;
      return c;
   }

   @Test
   void channelsOrderByTheirNumericValue() {
      List<sortableChannelNum> l = new ArrayList<sortableChannelNum>(Arrays.asList(
         new sortableChannelNum("704-1"), new sortableChannelNum("11"), new sortableChannelNum("704")));
      Collections.sort(l, new ChannelNumComparator());
      assertEquals("[11, 704, 704-1]", l.toString());
      // A descending column click is the same comparator reversed
      Collections.sort(l, Collections.reverseOrder(new ChannelNumComparator()));
      assertEquals("[704-1, 704, 11]", l.toString());
   }

   @Test
   void namedChannelsOrderByNumberNotByCallSign() {
      ChannelComparator c = new ChannelComparator();
      sortableChannel two = new sortableChannel("KTVU", "2");
      sortableChannel eleven = new sortableChannel("KNTV", "11");
      assertTrue(c.compare(two, eleven) < 0);
      assertTrue(c.compare(eleven, two) > 0);
      assertEquals(0, c.compare(two, new sortableChannel("KRON", "2")));
   }

   @Test
   void datesOrderNumericallyNotAsStrings() {
      // The sort key is the gmt millisecond string; compared as text "999..." would beat "1000..."
      DateComparator c = new DateComparator();
      assertTrue(c.compare(date(999999999999L), date(1000000000000L)) < 0);
      assertTrue(c.compare(date(1000000000000L), date(999999999999L)) > 0);
      assertEquals(0, c.compare(date(1400000000000L), date(1400000000000L)));
   }

   @Test
   void durationsSizesAndDoublesOrderByValue() {
      assertTrue(new DurationComparator().compare(
         new sortableDuration(60000L), new sortableDuration(120000L)) < 0);
      assertEquals(0, new DurationComparator().compare(
         new sortableDuration(60000L), new sortableDuration(60000L)));

      assertTrue(new SizeComparator().compare(
         new sortableSize(2L << 30), new sortableSize(1L << 30)) > 0);
      assertEquals(0, new SizeComparator().compare(
         new sortableSize(1L << 30), new sortableSize(1L << 30)));

      assertTrue(new DoubleComparator().compare(
         new sortableDouble(1.5), new sortableDouble(1.75)) < 0);
      // The displays round to the same two decimals, but the sort keys still differ
      assertTrue(new DoubleComparator().compare(
         new sortableDouble(1.501), new sortableDouble(1.502)) < 0);
   }

   @Test
   void imageCellsOrderByNameIgnoringCase() {
      ImageComparator c = new ImageComparator();
      assertTrue(c.compare(cell("ApplePie"), cell("banana")) < 0);
      assertTrue(c.compare(cell("banana"), cell("ApplePie")) > 0);
      assertEquals(0, c.compare(cell("ApplePie"), cell("applepie")));
   }

   @Test
   void showsOrderByTitleThenEpisodeThenDate() {
      config.TiVoSort = 0;
      List<sortableShow> l = new ArrayList<sortableShow>(Arrays.asList(
         show("Firefly", "104", 1400000000000L),
         show("Firefly", "102", 1400000000000L),
         show("Bones", "", 1400000000000L),
         show("Firefly", "102", 1300000000000L)));
      Collections.sort(l, new ShowComparator());
      assertEquals("Bones", l.get(0).toString());
      assertEquals("Firefly [Ep 102]", l.get(1).toString());
      assertEquals(1300000000000L, l.get(1).gmt, "equal titles and episodes break on date");
      assertEquals("Firefly [Ep 102]", l.get(2).toString());
      assertEquals(1400000000000L, l.get(2).gmt);
      assertEquals("Firefly [Ep 104]", l.get(3).toString());
   }

   @Test
   void theAlternateSortIgnoresLeadingArticles() {
      ShowComparator c = new ShowComparator();
      sortableShow americans = show("The Americans", "", 1400000000000L);
      sortableShow bones = show("Bones", "", 1400000000000L);
      config.TiVoSort = 0;
      assertTrue(c.compare(americans, bones) > 0, "plain sort files it under T");
      config.TiVoSort = 1;
      assertTrue(c.compare(americans, bones) < 0, "TiVo sort files it under A");
   }

   @Test
   void anUnnumberedEpisodeSortsAheadOfANumberedOne() {
      // A missing number reads as -1 on its own side rather than muting both, so the
      // comparison stays consistent whichever pair of shows it is handed
      config.TiVoSort = 0;
      ShowComparator c = new ShowComparator();
      assertTrue(c.compare(
         show("Firefly", "104", 1300000000000L), show("Firefly", "", 1400000000000L)) > 0);
      assertTrue(c.compare(
         show("Firefly", "", 1400000000000L), show("Firefly", "104", 1300000000000L)) < 0);
   }

   // The order has to be transitive or Collections.sort throws "Comparison method violates
   // its general contract!" once a list is long enough for TimSort to check
   @Test
   void mixingNumberedAndUnnumberedEpisodesStaysTransitive() {
      config.TiVoSort = 0;
      ShowComparator c = new ShowComparator();
      sortableShow numbered = show("Firefly", "104", 1300000000000L);
      sortableShow lower    = show("Firefly", "102", 1500000000000L);
      sortableShow none     = show("Firefly", "", 1400000000000L);

      assertTrue(c.compare(lower, numbered) < 0);
      assertTrue(c.compare(none, lower) < 0);
      assertTrue(c.compare(none, numbered) < 0, "none < lower < numbered must imply none < numbered");

      List<sortableShow> l = new ArrayList<sortableShow>(Arrays.asList(numbered, none, lower));
      Collections.sort(l, c);
      assertEquals("Firefly", l.get(0).toString());
      assertEquals("Firefly [Ep 102]", l.get(1).toString());
      assertEquals("Firefly [Ep 104]", l.get(2).toString());
   }

   @Test
   void nullsCompareEqualRatherThanThrowing() {
      assertNullSafe(new ShowComparator(), show("Firefly", "104", 1400000000000L));
      assertNullSafe(new ChannelComparator(), new sortableChannel("KTVU", "2"));
      assertNullSafe(new ChannelNumComparator(), new sortableChannelNum("2"));
      assertNullSafe(new DateComparator(), date(1400000000000L));
      assertNullSafe(new DoubleComparator(), new sortableDouble(1.5));
      assertNullSafe(new DurationComparator(), new sortableDuration(60000L));
      assertNullSafe(new SizeComparator(), new sortableSize(1L << 30));
      assertNullSafe(new ImageComparator(), cell("todo.png"));
      assertNullSafe(new StringChannelComparator(), "2=KTVU");
      assertNullSafe(new StringShowComparator(), "Firefly");
   }

   private static <T> void assertNullSafe(Comparator<T> c, T value) {
      assertEquals(0, c.compare(null, value));
      assertEquals(0, c.compare(value, null));
      assertEquals(0, c.compare(null, null));
   }

   @Test
   void channelStringsWithoutANumberAllCompareEqual() {
      StringChannelComparator c = new StringChannelComparator();
      assertTrue(c.compare("2=KTVU", "11-1=KNTV") < 0, "2 comes before 11.1");
      assertTrue(c.compare("11-1=KNTV", "11-2=KNTV") < 0);
      assertEquals(0, c.compare("2=KTVU", "2=KRON"));
      // Without an "=" the parse never runs and that side stays at 0, so a bare call sign
      // sorts as channel zero and two of them are a tie
      assertEquals(0, c.compare("KTVU", "KNTV"));
      assertTrue(c.compare("KTVU", "2=KRON") < 0);
      // A number that will not parse is swallowed, which makes the pair a tie, not an error
      assertEquals(0, c.compare("abc=KTVU", "2=KRON"));
   }

   @Test
   void aLeadingPriceIsStrippedBeforeShowTitlesCompare() {
      StringShowComparator c = new StringShowComparator();
      assertEquals(0, c.compare("($1.99) Rizzoli & Isles [Ep 703]", "Rizzoli & Isles [Ep 703]"));
      assertTrue(c.compare("($1.99) Bones", "Rizzoli & Isles") < 0);
      // The parenthesised prefix match is greedy, so a title carrying its own brackets loses
      // everything up to the last of them
      assertEquals(0, c.compare("($1.99) Fargo (2014) Pilot", "Pilot"));
      // compareTo, not compareToIgnoreCase - upper case still sorts ahead of lower
      assertTrue(c.compare("Bones", "bones") < 0);
   }
}
