package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Hashtable;

import org.junit.jupiter.api.Test;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONConverter;
import com.tivo.kmttg.JSON.JSONObject;

/**
 * Tests the "already scheduled" marker the guide, search and won't-record
 * tables put on a show ({@code __inTodo__}). It is worked out entirely from the
 * JSON - start time, channel number, title/subtitle and contentId - by matching
 * a listing against the ToDo lists of every connected TiVo, so it is sensitive
 * to the exact shape of both responses.
 *
 * The fixtures line up for real here: guide_2-1.json is the same channel and
 * day as the captured ToDo list, and five of its listings are shows that were
 * genuinely scheduled to record.
 */
public class TodoFlagTest {

   private static final String IN_TODO = "__inTodo__";

   private static Hashtable<String,JSONArray> todoLists(String... tivoNames) throws Exception {
      Hashtable<String,JSONArray> all = new Hashtable<String,JSONArray>();
      for (String tivoName : tivoNames)
         all.put(tivoName, Fixtures.load("todo.json"));
      return all;
   }

   /** A guide listing that the recorded ToDo list also contains. */
   private static JSONObject scheduledListing() throws Exception {
      JSONArray guide = Fixtures.load("guide_2-1.json");
      JSONArray todo = Fixtures.load("todo.json");
      for (int i = 0; i < guide.length(); i++) {
         JSONObject offer = guide.getJSONObject(i);
         for (int j = 0; j < todo.length(); j++) {
            JSONObject t = todo.getJSONObject(j);
            if (t.getString("startTime").equals(offer.getString("startTime"))
                  && t.getJSONObject("channel").getString("channelNumber")
                        .equals(offer.getJSONObject("channel").getString("channelNumber")))
               return offer;
         }
      }
      throw new IllegalStateException("no guide listing in the fixture is also in the ToDo list");
   }

   @Test
   public void flagsExactlyTheListingsThatAreScheduled() throws Exception {
      JSONArray guide = Fixtures.load("guide_2-1.json");
      JSONArray todo = Fixtures.load("todo.json");
      // Work out the expected count the way a viewer would: same channel, same
      // start time as something on the ToDo list.
      int expected = 0;
      for (int i = 0; i < guide.length(); i++) {
         for (int j = 0; j < todo.length(); j++) {
            JSONObject t = todo.getJSONObject(j);
            JSONObject offer = guide.getJSONObject(i);
            if (t.getString("startTime").equals(offer.getString("startTime"))
                  && t.getJSONObject("channel").getString("channelNumber")
                        .equals(offer.getJSONObject("channel").getString("channelNumber"))) {
               expected++;
               break;
            }
         }
      }
      assertTrue(expected > 0, "fixtures should overlap - same channel and day");

      Hashtable<String,JSONArray> all = todoLists("Bolt");
      int flagged = 0;
      for (int i = 0; i < guide.length(); i++) {
         JSONObject offer = guide.getJSONObject(i);
         rnpl.flagIfInTodo(offer, false, all);
         if (offer.has(IN_TODO)) {
            assertEquals("Bolt", offer.getString(IN_TODO), "wrong flag on " + offer.getString("title"));
            flagged++;
         }
      }
      assertEquals(expected, flagged, "wrong number of listings marked as scheduled");
   }

   @Test
   public void namesEveryTivoThatWillRecordIt() throws Exception {
      JSONObject offer = scheduledListing();

      rnpl.flagIfInTodo(offer, false, todoLists("Bolt", "Roamio"));

      String flag = offer.getString(IN_TODO);
      assertTrue(flag.contains("Bolt") && flag.contains("Roamio"),
         "both TiVos should be named, got: " + flag);
      assertTrue(flag.contains(", "), "TiVo names should be listed together, got: " + flag);
   }

   @Test
   public void flagsTheSameShowRecordingFromAnotherChannel() throws Exception {
      // A show airing on one channel while the TiVo records it from another:
      // same title and start time, different channel number. The flag names the
      // channel it is actually being taken from.
      JSONArray todo = Fixtures.load("todo.json");
      JSONObject scheduled = todo.getJSONObject(0);
      String recordingFrom = scheduled.getJSONObject("channel").getString("channelNumber");

      JSONObject elsewhere = new JSONObject(scheduled.toString());
      elsewhere.getJSONObject("channel").put("channelNumber", recordingFrom + "9");

      rnpl.flagIfInTodo(elsewhere, false, todoLists("Bolt"));

      assertEquals("Bolt: " + recordingFrom, elsewhere.getString(IN_TODO),
         "should point at the channel the recording comes from");

      // The match is on title *and* subtitle, so a different episode of the same
      // series airing at that moment is not the one being recorded.
      JSONObject otherEpisode = new JSONObject(elsewhere.toString());
      otherEpisode.remove(IN_TODO);
      otherEpisode.put("subtitle", "Some Other Episode");

      rnpl.flagIfInTodo(otherEpisode, false, todoLists("Bolt"));

      assertFalse(otherEpisode.has(IN_TODO), "a different episode should not be flagged");
   }

   @Test
   public void flagsTheSameShowAtAnotherTimeOnlyWhenAsked() throws Exception {
      // Same programme (contentId), different airing. The guide leaves this
      // unmarked; the won't-record table asks for it, and gets the air time.
      JSONArray todo = Fixtures.load("todo.json");
      JSONObject scheduled = todo.getJSONObject(0);

      JSONObject repeat = new JSONObject(scheduled.toString());
      repeat.put("startTime", "2026-06-14 11:00:00");
      repeat.getJSONObject("channel").put("channelNumber", "99-9");

      JSONObject ignored = new JSONObject(repeat.toString());
      rnpl.flagIfInTodo(ignored, false, todoLists("Bolt"));
      assertFalse(ignored.has(IN_TODO), "another airing should not be flagged unless asked for");

      rnpl.flagIfInTodo(repeat, true, todoLists("Bolt"));
      // The time in the flag is the airing the TiVo is taking, not this one -
      // reading it off the wrong entry would be invisible to a startsWith check.
      assertEquals("Bolt: " + JSONConverter.printableTimeFromJSON(scheduled),
         repeat.getString(IN_TODO), "flag should name the TiVo and the airing it records");
   }

   @Test
   public void leavesUnscheduledListingsAlone() throws Exception {
      // Nothing on this channel was scheduled in the captured ToDo list.
      JSONArray guide = Fixtures.load("guide_5-1.json");
      Hashtable<String,JSONArray> all = todoLists("Bolt");

      for (int i = 0; i < guide.length(); i++) {
         JSONObject offer = guide.getJSONObject(i);
         rnpl.flagIfInTodo(offer, true, all);
         assertFalse(offer.has(IN_TODO), "unscheduled listing flagged: " + offer.getString("title"));
      }
   }

   @Test
   public void ignoresListingsWithNothingToMatchOn() throws Exception {
      // A collection or person result has no start time, so there is nothing to
      // compare - it must be left alone rather than throw.
      JSONObject bare = new JSONObject();
      bare.put("title", "Some Show");

      rnpl.flagIfInTodo(bare, true, todoLists("Bolt"));

      assertFalse(bare.has(IN_TODO), "a listing with no start time should not be flagged");
   }
}
