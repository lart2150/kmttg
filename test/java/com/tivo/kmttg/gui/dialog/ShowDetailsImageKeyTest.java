package com.tivo.kmttg.gui.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.tivo.kmttg.JSON.JSONObject;

/**
 * What the Show information artwork lookup is cached under.
 *
 * The choice was settled against a live TiVo rather than guessed. Every
 * artwork url comes back as
 * <code>i.tivo.com/images-production/collection/014/577/14577/...</code> -
 * that last number is the collectionId - and the whole image set is identical
 * for every episode of a series. Checked across twelve series and forty
 * contentIds, spanning seasons 1 to 27 and shows from 1984 to 2026, on
 * network, cable and PBS: always the same set, including across seven seasons
 * of one show and four years of another. So collectionId is the id the
 * artwork actually hangs off; keying by contentId would miss on every sibling
 * episode, and keying by season would split a series for no reason.
 *
 * Episodes of one series differ in contentId, so these assertions are what
 * keep the lookup collapsing to a single rpc per series.
 */
public class ShowDetailsImageKeyTest {

   @Test
   public void theCollectionWinsSoEpisodesOfASeriesShareOneLookup() throws Exception {
      // Two Murder, She Wrote episodes six seasons apart, as the TiVo returns
      // them - different content, same collection
      JSONObject s6 = new JSONObject(
         "{\"title\":\"Murder, She Wrote\",\"seasonNumber\":6," +
         "\"contentId\":\"tivo:ct.348050\",\"collectionId\":\"tivo:cl.14577\"}");
      JSONObject s12 = new JSONObject(
         "{\"title\":\"Murder, She Wrote\",\"seasonNumber\":12," +
         "\"contentId\":\"tivo:ct.1378618\",\"collectionId\":\"tivo:cl.14577\"}");

      assertEquals("tivo:cl.14577", ShowDetails.imageKey(s6));
      assertEquals(ShowDetails.imageKey(s6), ShowDetails.imageKey(s12),
         "episodes of one series must share a cache key, whatever the season");
   }

   @Test
   public void contentIdIsTheFallbackWhenThereIsNoCollection() throws Exception {
      JSONObject json = new JSONObject("{\"contentId\":\"tivo:ct.415890139\"}");
      assertEquals("tivo:ct.415890139", ShowDetails.imageKey(json));
   }

   @Test
   public void differentSeriesDoNotShareAKey() throws Exception {
      JSONObject ghosts = new JSONObject("{\"collectionId\":\"tivo:cl.444532372\"}");
      JSONObject milkStreet = new JSONObject("{\"collectionId\":\"tivo:cl.376798952\"}");
      assertEquals("tivo:cl.444532372", ShowDetails.imageKey(ghosts));
      assertEquals("tivo:cl.376798952", ShowDetails.imageKey(milkStreet));
   }

   @Test
   public void aRowWithNeitherIdIsNotCacheable() throws Exception {
      // Returning null keeps it out of the cache rather than lumping every
      // such row under one shared entry
      assertNull(ShowDetails.imageKey(new JSONObject("{\"title\":\"Cheers\"}")));
      assertNull(ShowImageCache.getUrl(null));
   }
}
