package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONConverter;
import com.tivo.kmttg.JSON.JSONObject;

/**
 * Tests that exercise the parsing/conversion of real (sanitized) TiVo RPC
 * responses captured from a Bolt. These validate the code paths that consume
 * the output of the safe Remote read calls (MyShows / ToDo / DeletedShows /
 * SeasonPasses) without needing a live connection.
 *
 * Fixtures live under src/test/resources/fixtures and can be regenerated with
 * the captureRpcFixtures Gradle task.
 */
public class TiVoRpcFixturesTest {

   // ---- Season passes (Remote.SeasonPasses) -------------------------------

   @Test
   public void seasonPasses_haveIdAndTitle() throws Exception {
      JSONArray sps = Fixtures.load("seasonpasses.json");
      assertTrue(sps.length() > 0, "expected at least one season pass");
      for (int i = 0; i < sps.length(); i++) {
         JSONObject sp = sps.getJSONObject(i);
         assertTrue(sp.has("subscriptionId"), "season pass missing subscriptionId at " + i);
         assertFalse(sp.getString("subscriptionId").isEmpty(), "empty subscriptionId at " + i);
         assertTrue(sp.has("title"), "season pass missing title at " + i);
         assertFalse(sp.getString("title").trim().isEmpty(), "blank title at " + i);
      }
   }

   @Test
   public void seasonPasses_haveSubscriptionType() throws Exception {
      JSONArray sps = Fixtures.load("seasonpasses.json");
      // Every subscription should declare a type (seasonPass, wishList, etc.)
      for (int i = 0; i < sps.length(); i++) {
         assertTrue(sps.getJSONObject(i).has("type"), "subscription missing type at " + i);
      }
   }

   // ---- ToDo (Remote.ToDo) ------------------------------------------------

   @Test
   public void todo_parsesTitleChannelAndStart() throws Exception {
      JSONArray todo = Fixtures.load("todo.json");
      assertTrue(todo.length() > 0, "expected at least one ToDo entry");
      for (int i = 0; i < todo.length(); i++) {
         JSONObject entry = todo.getJSONObject(i);
         String title = JSONConverter.makeShowTitle(entry);
         assertNotNull(title, "null title at " + i);
         assertFalse(title.trim().isEmpty(), "blank title at " + i);

         assertNotNull(JSONConverter.makeChannelName(entry), "null channel at " + i);

         long start = JSONConverter.getStartTime(entry);
         assertTrue(start > 0, "non-positive start time at " + i);

         assertTrue(entry.has("recordingId"), "ToDo entry missing recordingId at " + i);
      }
   }

   // ---- Deleted (Remote.DeletedShows) -------------------------------------

   @Test
   public void deleted_parsesAndHasState() throws Exception {
      JSONArray deleted = Fixtures.load("deleted.json");
      assertTrue(deleted.length() > 0, "expected at least one deleted entry");
      for (int i = 0; i < deleted.length(); i++) {
         JSONObject entry = deleted.getJSONObject(i);
         assertFalse(JSONConverter.makeShowTitle(entry).trim().isEmpty(), "blank title at " + i);
         assertTrue(entry.has("state"), "deleted entry missing state at " + i);
         assertTrue(JSONConverter.getStartTime(entry) > 0, "non-positive start time at " + i);
      }
   }

   // ---- MyShows / Now Playing List (Remote.MyShows) -----------------------

   @Test
   public void myShows_eachWrapsARecording() throws Exception {
      JSONArray npl = Fixtures.load("myshows.json");
      assertTrue(npl.length() > 0, "expected at least one NPL entry");
      for (int i = 0; i < npl.length(); i++) {
         JSONObject wrapper = npl.getJSONObject(i);
         assertTrue(wrapper.has("recording"), "NPL entry missing recording at " + i);
         JSONObject rec = wrapper.getJSONArray("recording").getJSONObject(0);
         assertFalse(JSONConverter.makeShowTitle(rec).trim().isEmpty(), "blank title at " + i);
         assertTrue(JSONConverter.getStartTime(rec) > 0, "non-positive start time at " + i);
      }
   }

   // ---- Cancelled / Won't Record (Remote.CancelledShows) ------------------

   @Test
   public void cancelled_parsesTitleAndState() throws Exception {
      JSONArray cancelled = Fixtures.load("cancelled.json");
      assertTrue(cancelled.length() > 0, "expected at least one cancelled entry");
      for (int i = 0; i < cancelled.length(); i++) {
         JSONObject entry = cancelled.getJSONObject(i);
         assertFalse(JSONConverter.makeShowTitle(entry).trim().isEmpty(), "blank title at " + i);
         assertNotNull(JSONConverter.makeChannelName(entry), "null channel at " + i);
         assertTrue(entry.has("state"), "cancelled entry missing state at " + i);
      }
   }

   // ---- Thumbs ratings (Remote.getThumbs) ---------------------------------

   @Test
   public void thumbs_haveCollectionAndRating() throws Exception {
      JSONArray thumbs = Fixtures.load("thumbs.json");
      assertTrue(thumbs.length() > 0, "expected at least one thumbs entry");
      for (int i = 0; i < thumbs.length(); i++) {
         JSONObject t = thumbs.getJSONObject(i);
         assertTrue(t.has("collectionId"), "thumbs entry missing collectionId at " + i);
         assertTrue(t.has("thumbsRating"), "thumbs entry missing thumbsRating at " + i);
         int rating = t.getInt("thumbsRating");
         assertTrue(rating >= -3 && rating <= 3, "thumbsRating out of range at " + i + ": " + rating);
      }
   }

   // ---- Channel list (channelSearch) --------------------------------------

   @Test
   public void channels_haveNumberAndCallSign() throws Exception {
      JSONArray channels = Fixtures.load("channels.json");
      assertTrue(channels.length() > 0, "expected at least one channel");
      for (int i = 0; i < channels.length(); i++) {
         JSONObject ch = channels.getJSONObject(i);
         assertTrue(ch.has("channelNumber"), "channel missing channelNumber at " + i);
         assertFalse(ch.getString("channelNumber").trim().isEmpty(), "blank channelNumber at " + i);
         assertTrue(ch.has("callSign"), "channel missing callSign at " + i);
      }
   }

   // ---- Keyword search (OfferSearch for "bob") ----------------------------

   @Test
   public void search_parsesOffersAndMatchesKeyword() throws Exception {
      JSONArray offers = Fixtures.load("search.json");
      assertTrue(offers.length() > 0, "expected at least one search result");
      boolean matchedKeyword = false;
      for (int i = 0; i < offers.length(); i++) {
         JSONObject offer = offers.getJSONObject(i);
         assertFalse(JSONConverter.makeShowTitle(offer).trim().isEmpty(), "blank title at " + i);
         assertTrue(offer.has("collectionId"), "offer missing collectionId at " + i);
         if (offer.getString("title").toLowerCase().contains("bob"))
            matchedKeyword = true;
      }
      assertTrue(matchedKeyword, "expected at least one 'bob' search result to contain the keyword");
   }

   // ---- Guide listings (gridRowSearch) ------------------------------------

   @Test
   public void guide_channel2_1_parses() throws Exception {
      assertGuideChannel("guide_2-1.json", "2-1");
   }

   @Test
   public void guide_channel5_1_parses() throws Exception {
      assertGuideChannel("guide_5-1.json", "5-1");
   }

   private void assertGuideChannel(String fixture, String channelNumber) throws Exception {
      JSONArray offers = Fixtures.load(fixture);
      assertTrue(offers.length() > 0, "expected guide listings in " + fixture);
      for (int i = 0; i < offers.length(); i++) {
         JSONObject offer = offers.getJSONObject(i);
         assertFalse(JSONConverter.makeShowTitle(offer).trim().isEmpty(), "blank title at " + i + " in " + fixture);
         assertTrue(JSONConverter.getStartTime(offer) > 0, "non-positive start time at " + i + " in " + fixture);
         assertTrue(offer.has("collectionId"), "offer missing collectionId at " + i + " in " + fixture);
         assertTrue(offer.has("channel"), "offer missing channel at " + i + " in " + fixture);
         assertEquals(channelNumber, offer.getJSONObject("channel").getString("channelNumber"),
            "guide listing on wrong channel at " + i + " in " + fixture);
      }
   }

   // ---- JSONConverter behavior on real data -------------------------------

   @Test
   public void startTime_matchesScheduledStartTimeString() throws Exception {
      // For an entry that has the ISO scheduledStartTime string, getStartTime
      // should equal parsing that string via getLongDateFromString.
      JSONArray todo = Fixtures.load("todo.json");
      boolean checkedOne = false;
      for (int i = 0; i < todo.length(); i++) {
         JSONObject entry = todo.getJSONObject(i);
         if (entry.has("scheduledStartTime")) {
            long fromString = JSONConverter.getLongDateFromString(entry.getString("scheduledStartTime"));
            assertEquals(fromString, JSONConverter.getStartTime(entry),
               "getStartTime should match parsed scheduledStartTime at " + i);
            checkedOne = true;
            break;
         }
      }
      assertTrue(checkedOne, "no ToDo entry had a scheduledStartTime to verify");
   }

   @Test
   public void episodeShows_haveProgramId() throws Exception {
      // Episodic entries should yield a programId via the id helper.
      JSONArray deleted = Fixtures.load("deleted.json");
      boolean checkedOne = false;
      for (int i = 0; i < deleted.length(); i++) {
         JSONObject entry = deleted.getJSONObject(i);
         if (entry.has("isEpisode") && entry.getBoolean("isEpisode")) {
            String pid = id.programId(entry);
            assertNotNull(pid, "episodic entry produced null programId at " + i);
            assertFalse(pid.trim().isEmpty(), "episodic entry produced blank programId at " + i);
            checkedOne = true;
            break;
         }
      }
      assertTrue(checkedOne, "no episodic deleted entry found to verify programId");
   }
}
