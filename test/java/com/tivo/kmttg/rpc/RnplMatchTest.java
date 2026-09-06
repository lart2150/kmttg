package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.SimpleDateFormat;
import java.util.Hashtable;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;

/**
 * Tests how an NPL row from the legacy TTG listing is matched back to its RPC
 * recording ({@link rnpl#findRpcData}) and enriched from it
 * ({@link rnpl#addRpcData}).
 *
 * The two listings share no id, so the match is made on title, start time and
 * size - and the two sources disagree about all three in their own way: the
 * times drift by a few seconds, and the RPC size is in KiB where the XML size
 * is in bytes. Get any of that wrong and every row silently loses its season,
 * episode, original air date and recordingId, which is what breaks metadata and
 * the RPC-only download path.
 *
 * The recordings come from the captured My Shows fixture; the NPL rows are
 * derived from them the way parseNPL builds them out of the TTG XML.
 */
public class RnplMatchTest {

   private static final String TIVO = "Bolt";

   @BeforeEach
   public void seedRecordings() throws Exception {
      rnpl.setRpcData(TIVO, Fixtures.load("myshows.json"));
   }

   @AfterEach
   public void forgetRecordings() {
      rnpl.setRpcData(TIVO, null);
   }

   // ---- helpers ------------------------------------------------------------

   /** The recording inside the n'th My Shows wrapper. */
   private static JSONObject recording(int n) throws Exception {
      return Fixtures.load("myshows.json").getJSONObject(n).getJSONArray("recording").getJSONObject(0);
   }

   /** The NPL row parseNPL would build for a recording: title, start, size. */
   private static Hashtable<String,String> nplRow(JSONObject recording) throws Exception {
      Hashtable<String,String> h = new Hashtable<String,String>();
      h.put("titleOnly", recording.getString("title"));
      h.put("gmt", "" + gmt(recording.getString("startTime")));
      // The RPC reports KiB, the TTG XML reports bytes.
      h.put("size", "" + (recording.getLong("size") * 1024));
      return h;
   }

   private static long gmt(String tivoTime) throws Exception {
      SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      f.setTimeZone(TimeZone.getTimeZone("GMT"));
      return f.parse(tivoTime).getTime();
   }

   // ---- findRpcData --------------------------------------------------------

   @Test
   public void matchesEveryRowBackToItsOwnRecording() throws Exception {
      JSONArray shows = Fixtures.load("myshows.json");
      assertTrue(shows.length() > 0, "expected recordings in the My Shows fixture");

      for (int i = 0; i < shows.length(); i++) {
         JSONObject recording = recording(i);
         JSONObject found = rnpl.findRpcData(TIVO, nplRow(recording), true);

         assertNotNull(found, "no match for row " + i + ": " + recording.getString("title"));
         assertEquals(recording.getString("recordingId"), found.getString("recordingId"),
            "matched the wrong recording for row " + i);
      }
   }

   @Test
   public void allowsAMinuteOfClockDriftBetweenTheTwoListings() throws Exception {
      JSONObject recording = recording(0);
      Hashtable<String,String> row = nplRow(recording);
      long start = Long.parseLong(row.get("gmt"));

      row.put("gmt", "" + (start + 59000));
      assertNotNull(rnpl.findRpcData(TIVO, row, true), "59s of drift should still match");

      row.put("gmt", "" + (start - 59000));
      assertNotNull(rnpl.findRpcData(TIVO, row, true), "59s of drift the other way should match");

      row.put("gmt", "" + (start + 61000));
      assertNull(rnpl.findRpcData(TIVO, row, true), "more than a minute out is a different airing");
   }

   @Test
   public void readsTheRpcSizeAsKibibytes() throws Exception {
      JSONObject recording = recording(0);
      Hashtable<String,String> row = nplRow(recording);

      // Same recording, size read as bytes instead of KiB.
      row.put("size", "" + recording.getLong("size"));
      assertNull(rnpl.findRpcData(TIVO, row, true), "a row whose size is off should not match");

      row.put("size", "" + (recording.getLong("size") * 1024));
      assertNotNull(rnpl.findRpcData(TIVO, row, true), "KiB * 1024 should match the XML byte count");
   }

   @Test
   public void doesNotMatchADifferentShowAtTheSameTime() throws Exception {
      Hashtable<String,String> row = nplRow(recording(0));
      row.put("titleOnly", "Something Else Entirely");

      assertNull(rnpl.findRpcData(TIVO, row, true), "title has to match too");
   }

   @Test
   public void needsTitleStartAndSizeToLookAnythingUp() throws Exception {
      JSONObject recording = recording(0);
      for (String missing : new String[] { "titleOnly", "gmt", "size" }) {
         Hashtable<String,String> row = nplRow(recording);
         row.remove(missing);
         assertNull(rnpl.findRpcData(TIVO, row, true), "should give up with no " + missing);
      }
   }

   @Test
   public void findsNothingForATivoWithNoDataLoaded() throws Exception {
      assertNull(rnpl.findRpcData("Roamio", nplRow(recording(0)), true),
         "a TiVo whose listing has not been fetched has nothing to match against");
   }

   // ---- addRpcData ---------------------------------------------------------

   @Test
   public void enrichesTheRowWithTheIdsTheXmlDoesNotCarry() throws Exception {
      JSONObject recording = recording(0);
      Hashtable<String,String> row = nplRow(recording);

      rnpl.addRpcData(TIVO, row);

      assertEquals(recording.getString("recordingId"), row.get("recordingId"));
      assertEquals(recording.getString("collectionId"), row.get("collectionId"));
      assertEquals(recording.getString("contentId"), row.get("contentId"));
      assertEquals(recording.getString("offerId"), row.get("offerId"));
      // findRecordingId is the RPC-only download path's way in, and it goes
      // through addRpcData for rows that have not been enriched yet.
      assertEquals(recording.getString("recordingId"), rnpl.findRecordingId(TIVO, nplRow(recording)));
   }

   @Test
   public void formatsSeasonAndEpisodeTheWayTheMetadataExpects() throws Exception {
      JSONArray shows = Fixtures.load("myshows.json");
      boolean checked = false;

      for (int i = 0; i < shows.length() && ! checked; i++) {
         JSONObject recording = recording(i);
         if (! recording.has("seasonNumber") || ! recording.has("episodeNum"))
            continue;
         Hashtable<String,String> row = nplRow(recording);

         rnpl.addRpcData(TIVO, row);

         int season = recording.getInt("seasonNumber");
         int episode = recording.getJSONArray("episodeNum").getInt(0);
         // season/episode are zero padded to two digits each, but EpisodeNumber
         // is the season unpadded followed by the padded episode - S5E7 is
         // season "05", episode "07", EpisodeNumber "507".
         assertEquals(String.format("%02d", season), row.get("season"));
         assertEquals(String.format("%02d", episode), row.get("episode"));
         assertEquals("" + season + String.format("%02d", episode), row.get("EpisodeNumber"));
         if (recording.has("originalAirdate"))
            assertEquals(recording.getString("originalAirdate"), row.get("originalAirDate"),
               "originalAirdate should land in the row as originalAirDate");
         checked = true;
      }
      assertTrue(checked, "no episodic recording in the My Shows fixture");
   }

   @Test
   public void carriesTheClipMetadataIdForRecordingsThatHaveOne() throws Exception {
      // Skip mode needs clipMetadataId, which only some recordings carry.
      JSONArray shows = Fixtures.load("myshows.json");
      boolean checked = false;

      for (int i = 0; i < shows.length() && ! checked; i++) {
         JSONObject recording = recording(i);
         if (! recording.has("clipMetadata"))
            continue;
         Hashtable<String,String> row = nplRow(recording);

         rnpl.addRpcData(TIVO, row);

         assertEquals(recording.getJSONArray("clipMetadata").getJSONObject(0).getString("clipMetadataId"),
            row.get("clipMetadataId"));
         checked = true;
      }
      assertTrue(checked, "no recording with clip metadata in the My Shows fixture");
   }

   @Test
   public void leavesARowAloneWhenNothingMatches() throws Exception {
      Hashtable<String,String> row = nplRow(recording(0));
      row.put("titleOnly", "Something Else Entirely");
      int before = row.size();

      rnpl.addRpcData(TIVO, row);

      assertEquals(before, row.size(), "an unmatched row should not gain fields");
      assertFalse(row.containsKey("recordingId"), "an unmatched row should not gain a recordingId");
   }
}
