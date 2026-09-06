package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;

import org.junit.jupiter.api.Test;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONConverter;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.gui;
import com.tivo.kmttg.gui.remote.premiere;
import com.tivo.kmttg.gui.remote.remotegui;
import com.tivo.kmttg.gui.remote.search;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;

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
      try (GuiStub guiStub = new GuiStub(false)) {
         guiStub.withKeywordSearchTab();

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
      }
   }

   // ---- searchKeywords: pages OfferSearch until a page carries no offers ---

   @Test
   public void searchKeywords_replayPagesUntilOffersRunOut() throws Exception {
      // Real two page trace: 14 offers, then a page with no "offer" key at all,
      // which is what actually ends the loop (max is set high enough that the
      // "enough matches" cutoff cannot).
      JSONArray log = Fixtures.load("commands_searchcheer.json");
      int rawOffers = log.getJSONObject(0).getJSONObject("response").getJSONArray("offer").length();

      try (GuiStub guiStub = new GuiStub(false)) {
         guiStub.withKeywordSearchTab();
         ReplayRemote r = new ReplayRemote(log);

         JSONArray results = r.searchKeywords("cheer", null, 1000);

         assertEquals(2, r.issued("OfferSearch"), "should stop on the first offer-less page");
         assertTrue(results.length() > 0 && results.length() < rawOffers,
            "expected the offers deduped into fewer collections, got " + results.length());
         int totalEntries = 0;
         for (int i = 0; i < results.length(); i++)
            totalEntries += results.getJSONObject(i).getJSONArray("entries").length();
         assertEquals(rawOffers, totalEntries, "every offer should land in exactly one collection");
      }
   }

   // ---- MyShows: NPL pages, a Search per recording, then the seriesId merge -

   @Test
   public void myShows_replayFetchesEveryRecordingAndMergesSeriesIds() throws Exception {
      ReplayRemote r = new ReplayRemote(Fixtures.load("commands_myshows.json"));

      JSONArray npl = r.MyShows(null);

      assertNotNull(npl, "MyShows returned null");
      // Two recorded folder pages of 25, then a third read that runs off the end
      // of the trimmed trace - so this pins that it kept paging past page one,
      // not the real "empty page" stop condition.
      assertEquals(3, r.issued("MyShows"), "should keep paging after a full page");
      assertEquals(50, npl.length(), "expected one entry per recording on the two pages");
      // One Search per unique recording, then a single batched collectionSearch
      // (addSeriesID batches 25 collections at a time, and 50 recordings here
      // cover fewer than that).
      assertEquals(50, r.issued("Search"), "expected a Search per recording");
      assertEquals(1, r.issued("collectionSearch"), "expected one batched seriesId lookup");

      // What addSeriesID should have grafted on: each collectionSearch result's
      // partnerCollectionId with its "epgProvider:cl." prefix stripped, onto
      // every recording of that collection and no others. The trace is trimmed,
      // so the recorded response only covers some of the 50 recordings here.
      Map<String,String> expected = seriesIdsFrom(Fixtures.load("commands_myshows.json"));
      assertFalse(expected.isEmpty(), "fixture should carry a collectionSearch response");

      int merged = 0;
      for (int i = 0; i < npl.length(); i++) {
         JSONObject rec = npl.getJSONObject(i).getJSONArray("recording").getJSONObject(0);
         assertFalse(JSONConverter.makeShowTitle(rec).trim().isEmpty(), "blank title at " + i);
         assertTrue(rec.has("recordingId"), "NPL entry missing recordingId at " + i);
         String seriesId = rec.has("collectionId") ? expected.get(rec.getString("collectionId")) : null;
         if (seriesId == null) {
            assertFalse(rec.has("__SeriesId__"), "unexpected __SeriesId__ at " + i);
         } else {
            assertEquals(seriesId, rec.getString("__SeriesId__"), "wrong __SeriesId__ at " + i);
            merged++;
         }
      }
      assertTrue(merged > 0, "expected addSeriesID to graft __SeriesId__ onto the recordings");
   }

   /** collectionId -> the seriesId addSeriesID should derive, read off the trace. */
   private static Map<String,String> seriesIdsFrom(JSONArray log) throws Exception {
      Map<String,String> ids = new HashMap<>();
      for (int i = 0; i < log.length(); i++) {
         JSONObject rec = log.getJSONObject(i);
         if (! rec.getString("type").equals("collectionSearch"))
            continue;
         JSONArray found = rec.getJSONObject("response").getJSONArray("collection");
         for (int j = 0; j < found.length(); j++) {
            JSONObject c = found.getJSONObject(j);
            if (c.has("partnerCollectionId"))
               ids.put(c.getString("collectionId"),
                  c.getString("partnerCollectionId").replaceFirst("epgProvider:cl\\.", ""));
         }
      }
      return ids;
   }

   // ---- SeasonPasses(job): grafts upcoming recordings onto each pass -------

   @Test
   public void seasonPasses_replayAttachesUpcomingRecordings() throws Exception {
      JSONArray log = Fixtures.load("commands_seasonpasses_job.json");
      int subscriptions = log.getJSONObject(0).getJSONObject("response")
            .getJSONArray("subscription").length();

      jobData job = new jobData();
      job.tivoName = "Bolt";
      try (GuiStub guiStub = new GuiStub(false)) { // GUIMODE off: no job monitor writes
         ReplayRemote r = new ReplayRemote(log);

         JSONArray sps = r.SeasonPasses(job);

         assertNotNull(sps, "SeasonPasses returned null");
         assertEquals(subscriptions, sps.length(), "should unwrap every subscription");
         // Two idSequence lookups per pass - upcoming, then conflicts.
         assertEquals(2 * subscriptions, r.issued("recordingSearch"),
            "expected an upcoming and a conflicts lookup per pass");

         // The trace pairs each pass with an upcoming query then a conflicts
         // query, in that order, so pass i's ids have to be the i'th upcoming
         // response - a pass grafted with its neighbour's ids would still look
         // right by count alone. The trace covers the first 20 passes.
         JSONArray searches = new JSONArray();
         for (int i = 0; i < log.length(); i++)
            if (log.getJSONObject(i).getString("type").equals("recordingSearch"))
               searches.put(log.getJSONObject(i).getJSONObject("response"));
         int sawUpcoming = 0;
         for (int i = 0; 2 * i < searches.length(); i++) {
            JSONObject upcoming = searches.getJSONObject(2 * i);
            JSONObject sp = sps.getJSONObject(i);
            if (! upcoming.has("objectIdAndType")) {
               assertFalse(sp.has("__upcoming"), "unexpected __upcoming at " + i);
               continue;
            }
            JSONArray ids = upcoming.getJSONArray("objectIdAndType");
            assertEquals(ids.toString(), sp.getJSONArray("__upcoming").toString(),
               "wrong upcoming recordings grafted onto pass " + i);
            // The idSequence format returns bare object ids, not recordings.
            assertTrue(ids.getString(0).matches("[0-9]+"), "unexpected __upcoming id shape at " + i);
            sawUpcoming++;
         }
         assertTrue(sawUpcoming > 0, "expected at least one pass with upcoming recordings");

         // Nothing was conflicting on the recorded TiVo, so every conflicts
         // query came back without an objectIdAndType to graft on.
         for (int i = 0; i < sps.length(); i++)
            assertFalse(sps.getJSONObject(i).has("__conflicts"), "unexpected __conflicts at " + i);
      }
   }

   // ---- DeletedShows(job): counts ids in 1000 item batches first -----------

   @Test
   public void deleted_replayCountsIdSequenceInBatches() throws Exception {
      // The idSequence count comes back capped at 1000, so the count loop has to
      // re-query from that offset; the recorded trace is 1000 then 12.
      try (GuiStub guiStub = new GuiStub(true)) { // count branch only runs in GUI mode
         ReplayRemote r = new ReplayRemote(Fixtures.load("commands_deleted_job.json"));
         jobData job = new jobData();

         JSONArray deleted = r.DeletedShows(job);

         assertNotNull(deleted, "DeletedShows returned null");
         // 2 count queries + 3 listing pages: 20, 20, then a short page that
         // ends the listing loop on its own rather than by running out of trace.
         assertEquals(5, r.issued("Deleted"), "expected two count queries then three pages");
         assertEquals(52, deleted.length(), "expected the short final page to end the listing");
         assertSortedNewestFirst(deleted);
         // The two count queries came back 1000 and 12; that running total shows
         // up nowhere but the job monitor line, so nothing else would notice the
         // second batch being dropped or overwriting the first.
         verify(guiStub.guiMock, atLeastOnce())
            .jobTab_UpdateJobMonitorRowOutput(job, "Deleted list: 52/1012");
      }
   }

   // ---- ToDo(job): asks for the total up front ----------------------------

   @Test
   public void todo_replayCountsWithIdSequenceBeforePaging() throws Exception {
      try (GuiStub guiStub = new GuiStub(true)) {
         ReplayRemote r = new ReplayRemote(Fixtures.load("commands_todo_job.json"));
         jobData job = new jobData();

         JSONArray todo = r.ToDo(job);

         assertNotNull(todo, "ToDo returned null");
         // 1 count query + 3 pages of 20 + a response carrying no recordings,
         // which is what ends the loop.
         assertEquals(5, r.issued("ToDo"), "expected a count query, three pages and a stop");
         assertEquals(60, todo.length(), "expected every recording on the three pages");
         assertSortedOldestFirst(todo);
         // The idSequence count (104, the whole list) is only visible in the job
         // monitor line - the listing itself is trimmed to three pages.
         verify(guiStub.guiMock, atLeastOnce())
            .jobTab_UpdateJobMonitorRowOutput(job, "ToDo list: 60/104");
      }
   }

   // ---- SeasonPremieres: keeps only first episodes, deduped and sorted -----

   @Test
   public void premieres_replayKeepsOnlyFirstEpisodes() throws Exception {
      JSONArray channels = new JSONArray();
      JSONObject channel = new JSONObject();
      channel.put("channelNumber", "11-1");
      channel.put("sourceType", "terrestrial");
      channel.put("channelId", "tivo:ch.7208979");
      channel.put("stationId", "tivo:st.10420334");
      channels.put(channel);

      try (GuiStub guiStub = new GuiStub(false)) {
         guiStub.withPremiereTab(); // SeasonPremieres tags its results via the GUI
         JSONArray log = Fixtures.load("commands_premieres.json");
         ReplayRemote r = new ReplayRemote(log);

         JSONArray premieres = r.SeasonPremieres(channels, null, 2);

         assertNotNull(premieres, "SeasonPremieres returned null");
         assertEquals(2, r.issued("GridSearch"), "expected one guide read per channel per day");
         assertSortedOldestFirst(premieres);
         // Pin the count, not just "some": the filter throwing away too little
         // is the failure that a per-entry check cannot see.
         assertEquals(3, premieres.length(),
            "expected 3 premieres out of the " + offersIn(log) + " recorded listings");

         Set<String> offerIds = new HashSet<>();
         for (int i = 0; i < premieres.length(); i++) {
            JSONObject p = premieres.getJSONObject(i);
            assertEquals("series", p.getString("collectionType"), "non-series kept at " + i);
            assertFalse(p.has("repeat") && p.getBoolean("repeat"), "repeat kept at " + i);
            assertTrue(isFirstEpisode(p), "not a first episode at " + i + ": " + p.getString("title"));
            assertTrue(offerIds.add(p.getString("offerId")), "duplicate offer at " + i);
         }
      }
   }

   /** Every guide listing in a recorded GridSearch trace. */
   private static int offersIn(JSONArray log) throws Exception {
      int offers = 0;
      for (int i = 0; i < log.length(); i++)
         offers += log.getJSONObject(i).getJSONObject("response")
               .getJSONArray("gridRow").getJSONObject(0).getJSONArray("offer").length();
      return offers;
   }

   /** The three shapes SeasonPremieres accepts as "episode one". */
   private static boolean isFirstEpisode(JSONObject offer) throws Exception {
      if (offer.has("episodeNum"))
         return offer.getJSONArray("episodeNum").getInt(0) == 1;
      if (offer.has("partNumber") && offer.getInt("partNumber") == 1)
         return true;
      return offer.has("subtitle")
         && (offer.getString("subtitle").equals("Pilot")
             || offer.getString("subtitle").equals("Series Premiere"));
   }

   // ---- ChannelList: the received-channels filter --------------------------

   @Test
   public void channelList_replayFiltersToReceivedChannels() throws Exception {
      JSONArray log = Fixtures.load("commands_channellist.json");
      JSONArray all = log.getJSONObject(0).getJSONObject("response").getJSONArray("channel");
      int received = 0;
      for (int i = 0; i < all.length(); i++)
         if (all.getJSONObject(i).getBoolean("isReceived"))
            received++;
      assertTrue(received > 0 && received < all.length(), "fixture should have both kinds of channel");

      try (GuiStub guiStub = new GuiStub(false)) {
         // "All channels" off in the guide tab, so receivedOnly actually filters.
         guiStub.withAllChannels(false);

         assertEquals(all.length(), new ReplayRemote(log).ChannelList(null, false).length(),
            "receivedOnly=false should keep the whole lineup");
         assertEquals(received, new ReplayRemote(log).ChannelList(null, true).length(),
            "receivedOnly=true should drop the channels the TiVo does not get");

         // The guide tab's "all channels" toggle overrides receivedOnly.
         guiStub.withAllChannels(true);
         assertEquals(all.length(), new ReplayRemote(log).ChannelList(null, true).length(),
            "the all-channels toggle should override receivedOnly");
      }
   }

   // ---- seasonYearSearch: stops when a page repeats -------------------------

   @Test
   public void seasonYear_replayStopsOnARepeatedPage() throws Exception {
      // The TiVo ignores offset on this search: both recorded pages come back
      // identical. Only the first-contentId check ends the loop - without it the
      // real code would keep asking forever.
      ReplayRemote r = new ReplayRemote(Fixtures.load("commands_seasonyear.json"));

      JSONObject info = r.seasonYearSearch("tivo:cl.19245");

      assertEquals(2, r.issued("contentSearch"), "should stop as soon as a page repeats");
      // It really did ask for the next page - the TiVo just ignored the offset.
      assertEquals(30, r.lastRequest("contentSearch").getInt("offset"),
         "the second read should have asked for the next page");
      assertTrue(info.has("maxSeason"), "seasoned series should report maxSeason, got " + info);
      assertEquals(5, info.getInt("maxSeason"), "expected the highest season in the recorded page");
      assertFalse(info.has("years"), "a series with real season numbers should not fall back to years");
   }

   // ---- channelSearch(collectionId): unique channels a series airs on -------

   @Test
   public void collectionChannels_replayDedupesByCallSign() throws Exception {
      JSONArray log = Fixtures.load("commands_collectionchannels.json");
      JSONArray offers = log.getJSONObject(0).getJSONObject("response").getJSONArray("offer");
      Set<String> expected = new LinkedHashSet<>();
      for (int i = 0; i < offers.length(); i++)
         expected.add(offers.getJSONObject(i).getJSONObject("channel").getString("callSign"));
      ReplayRemote r = new ReplayRemote(log);

      JSONArray channels = r.channelSearch("tivo:cl.19245");

      assertEquals(1, r.issued("offerSearch"), "isBottom should end the paging after one read");
      // The recorded collection airs all 30 of its offers on one station, so the
      // dedup has to collapse every one of them down to that single channel.
      Set<String> callSigns = new LinkedHashSet<>();
      for (int i = 0; i < channels.length(); i++)
         assertTrue(callSigns.add(channels.getJSONObject(i).getString("callSign")),
            "duplicate callSign at " + i);
      assertEquals(expected, callSigns,
         "expected " + offers.length() + " offers deduped to " + expected);
   }

   // ---- thumbs ratings: read, and write only when allowed ------------------

   @Test
   public void thumbsRating_replayReadsTheStoredRating() throws Exception {
      ReplayRemote r = new ReplayRemote(Fixtures.load("commands_thumbsrating.json"));
      JSONObject show = new JSONObject();
      show.put("collectionId", "tivo:cl.19388");

      assertEquals(-1, r.getThumbsRating(show), "expected the recorded thumbs-down");
      assertEquals(1, r.issued("userContentSearch"));
      assertEquals("tivo:cl.19388", r.lastRequest("userContentSearch").getString("collectionId"),
         "should have looked up the show's own collection");
   }

   @Test
   public void thumbsRating_replayFindsCollectionIdInsideAOnePass() throws Exception {
      // One pass JSON carries the collectionId a level down, under idSetSource.
      ReplayRemote r = new ReplayRemote(Fixtures.load("commands_thumbsrating.json"));
      JSONObject idSetSource = new JSONObject();
      idSetSource.put("collectionId", "tivo:cl.19388");
      JSONObject pass = new JSONObject();
      pass.put("idSetSource", idSetSource);

      assertEquals(-1, r.getThumbsRating(pass), "should fall back to idSetSource.collectionId");
   }

   @Test
   public void thumbsRating_replayLeavesAnExistingRatingAloneUnlessOverridden() throws Exception {
      JSONObject show = new JSONObject();
      show.put("collectionId", "tivo:cl.19388");

      // Without override an existing (non-zero) rating wins and nothing is written.
      ReplayRemote keep = new ReplayRemote(Fixtures.load("commands_thumbsrating.json"));
      assertTrue(keep.setThumbsRating(show, 3, false), "should report success without writing");
      assertEquals(1, keep.issued("userContentSearch"), "should read the rating before deciding");
      assertEquals(0, keep.issued("userContentStore"), "must not overwrite an existing rating");

      // With override it goes straight to the store, no read first.
      ReplayRemote force = new ReplayRemote(Fixtures.load("commands_thumbsrating.json"));
      assertTrue(force.setThumbsRating(show, -1, true), "override should store the rating");
      assertEquals(1, force.issued("userContentStore"));
      assertEquals(0, force.issued("userContentSearch"), "override should not read first");
      // A store that sent the wrong rating still comes back successful, so the
      // return value alone says nothing - check what actually went out.
      JSONObject stored = force.lastRequest("userContentStore");
      assertEquals(-1, stored.getInt("thumbsRating"), "stored the wrong rating");
      assertEquals("tivo:cl.19388", stored.getString("collectionId"), "stored against the wrong show");
   }

   // ---- streamingEntries: walks into every My Shows folder -----------------

   @Test
   public void streaming_replayRecursesIntoEveryFolder() throws Exception {
      JSONArray log = Fixtures.load("commands_streaming.json");
      JSONArray top = log.getJSONObject(0).getJSONObject("response").getJSONArray("myShowsItem");
      int folders = 0;
      for (int i = 0; i < top.length(); i++)
         if (top.getJSONObject(i).has("isFolder") && top.getJSONObject(i).getBoolean("isFolder"))
            folders++;
      assertTrue(folders > 0, "fixture should contain folders to recurse into");

      // The trace carries the top level plus the first few folder reads, so the
      // walk really does descend and chew through their contents rather than
      // getting a null back on the first recursive call.
      int replayedChildren = 0, nestedFolders = 0;
      for (int i = 1; i < log.length(); i++) {
         JSONArray items = log.getJSONObject(i).getJSONObject("response").getJSONArray("myShowsItem");
         replayedChildren += items.length();
         for (int j = 0; j < items.length(); j++)
            if (items.getJSONObject(j).has("isFolder") && items.getJSONObject(j).getBoolean("isFolder"))
               nestedFolders++;
      }
      assertTrue(replayedChildren > 100,
         "expected the folder reads to carry real contents, got " + replayedChildren);
      assertEquals(0, nestedFolders, "My Shows folders do not nest, so the walk is one level deep");

      ReplayRemote r = new ReplayRemote(log);
      JSONArray entries = r.streamingEntries(null);

      assertEquals(1 + folders, r.issued("myShowsItemSearch"),
         "expected the top level read plus one per folder");
      assertEquals(0, r.issued("collectionSearch"),
         "nothing in the listing is on demand, so nothing should be looked up");
      // Nothing in the recorded listing advertises onDemandAvailability, so the
      // walk finds no streaming entries - it must come back empty, not blow up
      // on the hundreds of ordinary recordings it walks past.
      assertEquals(0, entries.length(), "expected no streaming entries from recorded shows");
   }

   // ---- helpers ------------------------------------------------------------

   /**
    * Installs a Mockito {@code gui} singleton for the run of one test, since the
    * Remote read methods reach into it for job monitor and tab state, and puts
    * the real one back on close. The mock's defaults are what these tests want:
    * jobTab_GetRowData returns null, so jobMonitor.isFirstJobInMonitor is false
    * and the progress bar / window title branches stay out of the way.
    */
   private static class GuiStub implements AutoCloseable {
      final gui guiMock = mock(gui.class);
      final remotegui remote = mock(remotegui.class);
      private final gui prevGui = config.gui;
      private final Boolean prevMode = config.GUIMODE;

      GuiStub(boolean guiMode) {
         guiMock.remote_gui = remote;
         config.gui = guiMock;
         config.GUIMODE = guiMode;
      }

      /** Search tab set to plain keyword search with no extended search. */
      void withKeywordSearchTab() {
         search searchTab = mock(search.class);
         searchTab.search_type = new JComboBox<>(new String[] { "keywords" });
         searchTab.includeFree = new JCheckBox(); // unchecked
         searchTab.includePaid = new JCheckBox(); // unchecked
         remote.search_tab = searchTab;
      }

      /** Premieres tab, which SeasonPremieres calls back into with its results. */
      void withPremiereTab() {
         remote.premiere_tab = mock(premiere.class);
      }

      /** The guide tab's "all channels" toggle, read by ChannelList. */
      void withAllChannels(boolean all) {
         when(remote.AllChannels()).thenReturn(all);
      }

      @Override
      public void close() {
         config.gui = prevGui;
         config.GUIMODE = prevMode;
      }
   }


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
