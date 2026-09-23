package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;

/**
 * Tests {@link Remote#searchNaturalLanguage}, the Search tab's "naturalLanguage"
 * type. The voice search answers with titles only, so each title costs an
 * offerSearch for its airings, and the result has to come out in the same
 * collection structure searchKeywords builds or the search table can't show it.
 *
 * Response shapes follow a live Bolt capture (2026-09-23): a movie comes back as
 * a contentDetailUiAction naming its contentId, a series as a
 * collectionDetailUiAction naming only its collectionId, and most titles in a
 * query like "comedy movies" are streaming-only with no airing at all.
 */
public class NaturalLanguageSearchTest {

   private static JSONObject item(String kernelType, String collectionId, String contentId) throws Exception {
      JSONObject kernel = new JSONObject();
      kernel.put("type", kernelType);
      kernel.put("collectionId", collectionId);
      if (contentId != null)
         kernel.put("contentId", contentId);
      JSONObject item = new JSONObject();
      item.put("type", "feedItem");
      item.put("kernel", kernel);
      return item;
   }

   private static JSONObject offer(String title, String collectionType, boolean linear) throws Exception {
      JSONObject o = new JSONObject();
      o.put("type", "offer");
      o.put("title", title);
      o.put("collectionType", collectionType);
      o.put("startTime", "2026-09-30 23:00:00");
      if (linear) {
         JSONObject channel = new JSONObject();
         channel.put("type", "channel");
         channel.put("channelNumber", "7-1");
         o.put("channel", channel);
      }
      return o;
   }

   private static JSONObject rec(String type, JSONObject response) throws Exception {
      JSONObject r = new JSONObject();
      r.put("type", type);
      r.put("response", response);
      return r;
   }

   private static JSONObject offers(JSONObject... list) throws Exception {
      JSONObject r = new JSONObject();
      r.put("type", "offerList");
      JSONArray a = new JSONArray();
      for (JSONObject o : list)
         a.put(o);
      r.put("offer", a);
      return r;
   }

   private static JSONArray trace() throws Exception {
      JSONObject nl = new JSONObject();
      nl.put("type", "naturalLanguageFeedItemResults");
      JSONArray items = new JSONArray();
      items.put(item("contentDetailUiAction", "tivo:cl.61719", "tivo:ct.23746"));
      items.put(item("contentDetailUiAction", "tivo:cl.61383", "tivo:ct.61384"));
      items.put(item("collectionDetailUiAction", "tivo:cl.14577", null));
      nl.put("items", items);

      JSONArray log = new JSONArray();
      log.put(rec("naturalLanguageFeedItemFind", nl));
      log.put(rec("OfferSearch", offers(
         offer("The Outlaw Josey Wales", "movie", true), offer("The Outlaw Josey Wales", "movie", true))));
      // Streaming only: an offer with no channel is not a recordable airing
      log.put(rec("OfferSearch", offers(offer("For a Few Dollars More", "movie", false))));
      log.put(rec("OfferSearch", offers(
         offer("Murder, She Wrote", "series", true), offer("Murder, She Wrote", "series", true),
         offer("Murder, She Wrote", "series", true))));
      return log;
   }

   @Test
   public void listsOnlyTitlesWithAiringsInTheSearchTableShape() throws Exception {
      ReplayRemote r = new ReplayRemote(trace());
      JSONArray result = r.searchNaturalLanguage("clint eastwood westerns", null, 100);

      assertEquals(2, result.length(), "the streaming-only title should be dropped");
      JSONObject movie = result.getJSONObject(0);
      assertEquals("tivo:cl.61719", movie.getString("collectionId"));
      assertEquals("The Outlaw Josey Wales", movie.getString("title"));
      assertEquals("movie", movie.getString("type"));
      assertEquals(2, movie.getJSONArray("entries").length());
      JSONObject series = result.getJSONObject(1);
      assertEquals("Murder, She Wrote", series.getString("title"));
      assertEquals(3, series.getJSONArray("entries").length());
      assertEquals(3, r.issued("OfferSearch"));
   }

   @Test
   public void asksForAiringsByContentWhenGivenOneElseByCollection() throws Exception {
      ReplayRemote r = new ReplayRemote(trace());
      r.searchNaturalLanguage("clint eastwood westerns", null, 100);

      // The last lookup is the series, which names no content
      JSONObject last = r.lastRequest("OfferSearch");
      assertEquals("tivo:cl.14577", last.getString("collectionId"));
      assertFalse(last.has("contentId"));

      JSONObject sent = r.lastRequest("naturalLanguageFeedItemFind");
      assertEquals("clint eastwood westerns", sent.getString("transcription"));
      assertEquals("stb", sent.getString("deviceType"));
      assertEquals("mindLocale", sent.getJSONObject("locale").getString("type"));
      assertTrue(sent.has("utcOffset"), "the TiVo refuses the request without utcOffset");
   }

   @Test
   public void stopsAtMaxMatches() throws Exception {
      ReplayRemote r = new ReplayRemote(trace());
      JSONArray result = r.searchNaturalLanguage("clint eastwood westerns", null, 2);

      assertEquals(1, result.length());
      assertEquals(1, r.issued("OfferSearch"), "no lookups once max airings are listed");
   }

   @Test
   public void aSeriesReturnedTwiceIsOneRowWithoutRepeatedAirings() throws Exception {
      JSONObject nl = new JSONObject();
      nl.put("type", "naturalLanguageFeedItemResults");
      JSONArray items = new JSONArray();
      // An episode of the series, then the series itself
      items.put(item("contentDetailUiAction", "tivo:cl.14577", "tivo:ct.14578"));
      items.put(item("collectionDetailUiAction", "tivo:cl.14577", null));
      nl.put("items", items);
      JSONObject a = offer("Murder, She Wrote", "series", true);
      a.put("offerId", "tivo:of.1");
      JSONObject b = offer("Murder, She Wrote", "series", true);
      b.put("offerId", "tivo:of.2");
      JSONArray log = new JSONArray();
      log.put(rec("naturalLanguageFeedItemFind", nl));
      log.put(rec("OfferSearch", offers(a)));
      log.put(rec("OfferSearch", offers(new JSONObject(a.toString()), b)));
      ReplayRemote r = new ReplayRemote(log);

      JSONArray result = r.searchNaturalLanguage("murder she wrote", null, 100);

      assertEquals(1, result.length());
      assertEquals(2, result.getJSONObject(0).getJSONArray("entries").length(),
         "tivo:of.1 came back from both lookups and should be listed once");
   }

   @Test
   public void noItemsIsAnEmptyResult() throws Exception {
      JSONObject nl = new JSONObject();
      nl.put("type", "naturalLanguageFeedItemResults");
      nl.put("transcription", "Sorry. I couldn't get anything for you right now.");
      JSONArray log = new JSONArray();
      log.put(rec("naturalLanguageFeedItemFind", nl));
      ReplayRemote r = new ReplayRemote(log);

      assertEquals(0, r.searchNaturalLanguage("football this weekend", null, 100).length());
      assertNull(r.lastRequest("OfferSearch"));
   }
}
