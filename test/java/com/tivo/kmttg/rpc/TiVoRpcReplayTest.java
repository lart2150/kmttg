package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;

import org.junit.jupiter.api.Test;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONConverter;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.gui;
import com.tivo.kmttg.gui.remote.remotegui;
import com.tivo.kmttg.gui.remote.search;
import com.tivo.kmttg.main.config;

/**
 * Tests that exercise the real {@link Remote} read methods end to end by
 * replaying recorded (sanitized) RPC request/response traces through
 * {@link ReplayRemote}. Unlike {@link TiVoRpcFixturesTest} (which only checks
 * the JSON parsing helpers on a pre-extracted response), these drive the actual
 * Remote logic: the request building, the multi-page read loops, subscription
 * unwrapping, the collectionSearch title merge, and the start-time sorting -
 * all offline, with no live TiVo.
 *
 * The command-trace fixtures (commands_*.json) are captured by
 * RpcFixtureCapture / RecordingRemote and trimmed to the first few pages;
 * ReplayRemote returns null for any request past the trace, which is exactly
 * how each read loop detects "no more data" and terminates.
 */
public class TiVoRpcReplayTest {

   // ---- SeasonPasses: unwraps the "subscription" array ---------------------

   @Test
   public void seasonPasses_replayUnwrapsAllSubscriptions() throws Exception {
      JSONArray log = Fixtures.load("commands_seasonpasses.json");
      int rawCount = log.getJSONObject(0).getJSONObject("response")
            .getJSONArray("subscription").length();

      JSONArray sps = new ReplayRemote(log).SeasonPasses(null);

      assertNotNull(sps, "SeasonPasses returned null");
      assertEquals(rawCount, sps.length(), "should unwrap every subscription");
      for (int i = 0; i < sps.length(); i++) {
         JSONObject sp = sps.getJSONObject(i);
         assertTrue(sp.has("subscriptionId"), "missing subscriptionId at " + i);
         assertFalse(sp.getString("title").trim().isEmpty(), "blank title at " + i);
      }
   }

   // ---- ToDo: paginates across pages, then sorts oldest-first --------------

   @Test
   public void todo_replayPaginatesAndSortsAscending() throws Exception {
      JSONArray todo = new ReplayRemote(Fixtures.load("commands_todo.json")).ToDo(null);

      assertNotNull(todo, "ToDo returned null");
      // The trace spans several 20-item pages, so a single page (20) is not enough.
      assertTrue(todo.length() > 20, "expected ToDo to span multiple pages, got " + todo.length());
      assertSortedOldestFirst(todo);
   }

   // ---- CancelledShows: paginates + sorts ----------------------------------

   @Test
   public void cancelled_replayPaginatesAndSorts() throws Exception {
      JSONArray cancelled = new ReplayRemote(Fixtures.load("commands_cancelled.json")).CancelledShows(null);

      assertNotNull(cancelled, "CancelledShows returned null");
      assertTrue(cancelled.length() > 20, "expected multiple pages, got " + cancelled.length());
      assertSortedOldestFirst(cancelled);
      for (int i = 0; i < cancelled.length(); i++)
         assertTrue(cancelled.getJSONObject(i).has("state"), "cancelled entry missing state at " + i);
   }

   // ---- DeletedShows: paginates + sorts ------------------------------------

   @Test
   public void deleted_replayPaginatesAndSorts() throws Exception {
      JSONArray deleted = new ReplayRemote(Fixtures.load("commands_deleted.json")).DeletedShows(null);

      assertNotNull(deleted, "DeletedShows returned null");
      assertTrue(deleted.length() > 20, "expected multiple pages, got " + deleted.length());
      // DeletedShows sorts newest-first (sortByLatestStartDate), unlike
      // ToDo / CancelledShows which sort oldest-first.
      assertSortedNewestFirst(deleted);
   }

   // ---- getThumbs: merges collectionSearch titles onto userContent ---------

   @Test
   public void thumbs_replayMergesCollectionTitles() throws Exception {
      JSONArray thumbs = new ReplayRemote(Fixtures.load("commands_thumbs.json")).getThumbs(null);

      assertNotNull(thumbs, "getThumbs returned null");
      assertTrue(thumbs.length() > 0, "expected thumbs from the recorded page");

      boolean sawMergedTitle = false;
      for (int i = 0; i < thumbs.length(); i++) {
         JSONObject t = thumbs.getJSONObject(i);
         assertTrue(t.has("collectionId"), "thumbs entry missing collectionId at " + i);
         int rating = t.getInt("thumbsRating");
         assertTrue(rating >= -3 && rating <= 3, "thumbsRating out of range at " + i + ": " + rating);
         // title/collectionType are grafted on from the collectionSearch response,
         // so their presence proves the second-stage merge actually ran.
         if (t.has("title") && !t.getString("title").trim().isEmpty())
            sawMergedTitle = true;
      }
      assertTrue(sawMergedTitle, "expected getThumbs to merge collection titles onto the ratings");
   }

   // ---- searchKeywords: filters/dedups OfferSearch results into collections -

   @Test
   public void searchKeywords_replayDedupesOffersIntoCollections() throws Exception {
      // Reuse the captured OfferSearch "offer" array as a single OfferSearch
      // response (searchKeywords pages OfferSearch and groups the offers).
      JSONArray offers = Fixtures.load("search.json");
      JSONObject response = new JSONObject();
      response.put("offer", offers);
      JSONObject rec = new JSONObject();
      rec.put("type", "OfferSearch");
      rec.put("response", response);
      JSONArray log = new JSONArray();
      log.put(rec);

      // searchKeywords reaches into the Swing gui singleton; stub just the bits
      // it reads: search mode = "keywords" and no extended/streaming search.
      gui prevGui = config.gui;
      Boolean prevMode = config.GUIMODE;
      try {
         gui guiStub = mock(gui.class);
         remotegui rgStub = mock(remotegui.class);
         search searchTab = mock(search.class);
         searchTab.search_type = new JComboBox<>(new String[] { "keywords" });
         searchTab.includeFree = new JCheckBox(); // unchecked
         searchTab.includePaid = new JCheckBox(); // unchecked
         rgStub.search_tab = searchTab;
         guiStub.remote_gui = rgStub;
         config.gui = guiStub;
         config.GUIMODE = false;

         JSONArray results = new ReplayRemote(log).searchKeywords("bob", null, 10);

         assertNotNull(results, "searchKeywords returned null");
         assertTrue(results.length() > 0, "expected at least one grouped collection");

         int totalEntries = 0;
         for (int i = 0; i < results.length(); i++) {
            JSONObject c = results.getJSONObject(i);
            assertTrue(c.has("collectionId"), "collection missing collectionId at " + i);
            assertFalse(c.getString("title").trim().isEmpty(), "blank collection title at " + i);
            // collections are emitted in discovery order (order == index)
            assertEquals(i, c.getInt("order"), "collections not in discovery order at " + i);
            JSONArray entries = c.getJSONArray("entries");
            assertTrue(entries.length() > 0, "collection grouped no offers at " + i);
            totalEntries += entries.length();
         }
         // Every (epg) offer is grouped under exactly one collection, and the
         // distinct collection count is less than the raw offer count (dedup).
         assertEquals(offers.length(), totalEntries, "offers should all be grouped");
         assertTrue(results.length() < offers.length(), "expected dedup into fewer collections");
      } finally {
         config.gui = prevGui;
         config.GUIMODE = prevMode;
      }
   }

   // ---- helpers ------------------------------------------------------------

   /** Assert entries are ordered oldest start time first (non-decreasing). */
   private static void assertSortedOldestFirst(JSONArray a) throws Exception {
      long prev = Long.MIN_VALUE;
      for (int i = 0; i < a.length(); i++) {
         long start = JSONConverter.getStartTime(a.getJSONObject(i));
         assertTrue(start >= prev, "entries not sorted oldest-first at index " + i
               + " (" + start + " < " + prev + ")");
         prev = start;
      }
   }

   /** Assert entries are ordered newest start time first (non-increasing). */
   private static void assertSortedNewestFirst(JSONArray a) throws Exception {
      long prev = Long.MAX_VALUE;
      for (int i = 0; i < a.length(); i++) {
         long start = JSONConverter.getStartTime(a.getJSONObject(i));
         assertTrue(start <= prev, "entries not sorted newest-first at index " + i
               + " (" + start + " > " + prev + ")");
         prev = start;
      }
   }
}
