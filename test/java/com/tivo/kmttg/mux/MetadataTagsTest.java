package com.tivo.kmttg.mux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tivo.kmttg.mux.mkv.MkvMuxer;

// Cover for the field to tag mapping. The pieces with something to get wrong are the name
// format, which has three shapes in the same list, and the supplement, which carries the
// fields the recording does not contain at all.
public class MetadataTagsTest {

   @Test
   void nameShapesAllSurvive() {
      // The recording writes "Surname|Forename", and these three all turn up together in the
      // credits of a single film.
      assertEquals("Jet Li", MetadataTags.displayName("Li|Jet"));
      assertEquals("DMX", MetadataTags.displayName("DMX|DMX"), "a doubled mononym is one name");
      assertEquals("Drag-On", MetadataTags.displayName("Drag-On|"), "no forename at all");
      assertEquals("Christopher Kimball", MetadataTags.displayName("Kimball|Christopher"));
   }

   @Test
   void nameHandlesOddInput() {
      assertEquals("Prince", MetadataTags.displayName("Prince"), "no separator at all");
      assertEquals("Cher", MetadataTags.displayName("|Cher"), "no surname");
      assertEquals("", MetadataTags.displayName("|"));
      assertEquals(null, MetadataTags.displayName(null));
   }

   @Test
   void supplementCarriesWhatTheRecordingCannot() {
      // callsign, season and episode are absent from every .TiVo measured, so they arrive
      // from kmttg's own job data. Without this the tags would simply be missing.
      MetadataTags.Supplement extra = new MetadataTags.Supplement();
      extra.callsign = "WGNHD";
      extra.seasonNumber = 11;
      extra.episodeNumber = 2;

      List<MkvMuxer.Tag> tags = MetadataTags.build(null, extra);
      assertEquals(3, tags.size());
      assertTrue(has(tags, MkvMuxer.TARGET_COLLECTION, "TVCHANNEL", "WGNHD"));
      assertTrue(has(tags, MkvMuxer.TARGET_SEASON, "PART_NUMBER", "11"),
         "season belongs at target 60, episode at 50 - same tag name, different level");
      assertTrue(has(tags, MkvMuxer.TARGET_EPISODE, "PART_NUMBER", "2"));
   }

   @Test
   void absentFieldsProduceNoTags() {
      assertEquals(0, MetadataTags.build(null, null).size());

      MetadataTags.Supplement empty = new MetadataTags.Supplement();
      assertEquals(0, MetadataTags.build(null, empty).size(),
         "a null callsign and zero episode must not become empty tags");

      MetadataTags.Supplement zero = new MetadataTags.Supplement();
      zero.seasonNumber = 0;
      zero.episodeNumber = 0;
      zero.callsign = "   ";
      assertEquals(0, MetadataTags.build(null, zero).size(),
         "zero is not a season number and whitespace is not a callsign");
   }

   @Test
   void kmttgPacksSeasonAndEpisodeIntoOneString() {
      // The split depends on length and comes from task/atomic.java, so both produce the same
      // numbers. These are the shapes that actually turn up.
      assertEquals("11/2", split("1102"));
      assertEquals("3/2",  split("302"));
      assertEquals("11/12", split("1112"));
      assertEquals("10/102", split("10102"), "five digits: two for season, the rest episode");
   }

   @Test
   void badEpisodeNumbersAreIgnoredRatherThanGuessed() {
      // Two digits or fewer is not a packed season+episode, and tivoFileName declines it
      // too - matching that matters, or a tag and a filename would disagree.
      for (String bad : new String[]{null, "", "   ", "abc", "s11e02", "11-02", "02", "7"}) {
         MetadataTags.Supplement s = new MetadataTags.Supplement();
         s.setEpisodeNumber(bad);
         assertEquals(null, s.seasonNumber, "season from " + bad);
         assertEquals(null, s.episodeNumber, "episode from " + bad);
      }
   }

   @Test
   void realSeasonAndEpisodeFieldsBeatThePackedString() {
      // When the NPL entry carries them, the packed fallback must not overwrite them.
      MetadataTags.Supplement s = new MetadataTags.Supplement();
      s.setSeasonEpisode("11", "2");
      s.setEpisodeNumber("9909");
      assertEquals(Integer.valueOf(11), s.seasonNumber);
      assertEquals(Integer.valueOf(2), s.episodeNumber);
   }

   @Test
   void oneRealFieldIsEnoughToBlockTheGuess() {
      // An entry can carry season without episode. Requiring both before refusing the packed
      // fallback let a guess overwrite the authoritative value.
      MetadataTags.Supplement s = new MetadataTags.Supplement();
      s.setSeasonEpisode("11", null);
      s.setEpisodeNumber("9909");
      assertEquals(Integer.valueOf(11), s.seasonNumber, "the real season must survive");
      assertEquals(null, s.episodeNumber, "and a guess must not fill the gap beside it");
   }

   @Test
   void jobDataFillsTheTitleOnlyWhenTheRecordingDidNot() {
      // The two-step path never sees onMetadata, so without this a remux of an already
      // decrypted .ts would carry no title at all.
      MetadataTags.Supplement extra = new MetadataTags.Supplement();
      extra.title = "Father Brown";
      extra.seriesId = "SH0376798952";

      List<MkvMuxer.Tag> tags = MetadataTags.build(null, extra);
      assertTrue(has(tags, MkvMuxer.TARGET_EPISODE, "TITLE", "Father Brown"));
      assertTrue(has(tags, MkvMuxer.TARGET_COLLECTION, "CATALOG_NUMBER", "SH0376798952"));
   }

   @Test
   void audioLanguageComesFromTheRecordingsOwnMetadata() {
      // The PMT carries no ISO 639 descriptor on any TiVo recording, so this element is the
      // only thing that tells a Spanish capture from an English one. Both spellings are real:
      // measured on a Telemundo World Cup recording and on four English ones.
      assertEquals("spa", MetadataTags.languageFrom(
         Arrays.asList("<showing><descriptionLanguage>spa-ESP</descriptionLanguage></showing>")));
      assertEquals("eng", MetadataTags.languageFrom(
         Arrays.asList("<showing><descriptionLanguage>eng-USA</descriptionLanguage></showing>")));
   }

   @Test
   void anUnstatedLanguageStaysUnstated() {
      // Nothing to say means "und" downstream, not a guess at English.
      assertEquals(null, MetadataTags.languageFrom(Arrays.asList("<showing><title>x</title></showing>")));
      assertEquals(null, MetadataTags.languageFrom(Arrays.asList((String)null)));
      assertEquals(null, MetadataTags.languageFrom(null));
      assertEquals(null, MetadataTags.audioLanguage(null));
   }

   @Test
   void theSegmentTitleNamesTheWholeFile() {
      // Not a tag: Matroska keeps this in Segment Information, and it is the one line the
      // MKVToolNix header editor and a player's title bar show.
      MetadataTags.Supplement extra = new MetadataTags.Supplement();
      extra.title = "Father Brown";
      assertEquals("Father Brown", MetadataTags.segmentTitle(null, extra),
         "a .ts source has no recording metadata at all");
      assertEquals(null, MetadataTags.segmentTitle(null, null));

      MetadataTags.Supplement blank = new MetadataTags.Supplement();
      blank.title = "   ";
      assertEquals(null, MetadataTags.segmentTitle(null, blank), "whitespace is not a title");
   }

   @Test
   void theStarRatingIsAStarCountNotTheCode() {
      // TiVo stores 1..7 for one star to four in half steps. Writing the code bare made a
      // three star film read as a 5.
      assertEquals("1", MetadataTags.stars(1));
      assertEquals("1.5", MetadataTags.stars(2));
      assertEquals("3", MetadataTags.stars(5), "The Good Wife came back as code 5");
      assertEquals("4", MetadataTags.stars(7));
      assertEquals(null, MetadataTags.stars(0), "out of range is no rating at all");
      assertEquals(null, MetadataTags.stars(9));
   }

   @Test
   void theRatingCodeFromRpcBecomesALabel() {
      // contentSearch answers with bare codes - measured "pg", "g" and "14" across a real My
      // Shows list - and LAW_RATING is read by people, not by TiVo.
      assertEquals("TV-PG", MetadataTags.lawRating(null, "pg"));
      assertEquals("TV-14", MetadataTags.lawRating(null, "14"));
      assertEquals("TV-Y7", MetadataTags.lawRating(null, "y7"));
      assertEquals("TV-14", MetadataTags.lawRating(null, "TV-14"), "already spelled out");
      assertEquals("PG-13", MetadataTags.lawRating("pg13", null));
      // "g" and "pg" mean different things on the two scales, which is why they are separate
      // fields rather than one guessed-at string.
      assertEquals("G", MetadataTags.lawRating("g", null));
      assertEquals("TV-G", MetadataTags.lawRating(null, "g"));
      assertEquals("PG-13", MetadataTags.lawRating("pg13", "14"), "a film rating wins");
      assertEquals(null, MetadataTags.lawRating(null, null));
      assertEquals("banana", MetadataTags.lawRating(null, "banana"), "unknown still passes through");
   }

   @Test
   void theRatingReachesTheTagsFromTheJob() {
      MetadataTags.Supplement extra = new MetadataTags.Supplement();
      extra.tvRating = "pg";
      List<MkvMuxer.Tag> tags = MetadataTags.build(null, extra);
      assertTrue(has(tags, MkvMuxer.TARGET_EPISODE, "LAW_RATING", "TV-PG"));
   }

   private static String split(String packed) {
      MetadataTags.Supplement s = new MetadataTags.Supplement();
      s.setEpisodeNumber(packed);
      return s.seasonNumber + "/" + s.episodeNumber;
   }

   private static boolean has(List<MkvMuxer.Tag> tags, int target, String name, String value) {
      for (MkvMuxer.Tag t : tags) {
         if (t.target == target && t.name.equals(name) && t.value.equals(value)) return true;
      }
      return false;
   }
}
