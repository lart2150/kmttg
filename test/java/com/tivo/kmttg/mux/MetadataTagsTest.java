package com.tivo.kmttg.mux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
