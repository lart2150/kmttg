package com.tivo.kmttg.gui.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The artwork cache for the Show information dialog.
 *
 * Filling this dialog costs a fresh rpc connection to the TiVo just to find
 * the artwork url, then an http get to fetch it. Both are cached, and the url
 * is keyed by collection rather than content on purpose: checked against a
 * live TiVo, four series - including two episodes from different seasons of
 * one, and four episodes of another - all answered with identical url sets,
 * because the urls are .../images-production/collection/&lt;collectionId&gt;/...
 * So one lookup serves every episode of a series, which is the case worth
 * saving. These pin the caching itself; the key choice is asserted over in
 * {@link ShowDetailsImageKeyTest}.
 */
public class ShowImageCacheTest {

   @BeforeEach
   public void emptyTheCache() {
      ShowImageCache.clear();
   }

   @Test
   public void aUrlIsRememberedForItsCollection() {
      assertNull(ShowImageCache.getUrl("tivo:cl.444532372"));
      ShowImageCache.putUrl("tivo:cl.444532372", "http://i.tivo.com/x/showcaseBanner_240x180.jpg");
      assertEquals("http://i.tivo.com/x/showcaseBanner_240x180.jpg",
         ShowImageCache.getUrl("tivo:cl.444532372"));
      // A series never looked at is still a miss
      assertNull(ShowImageCache.getUrl("tivo:cl.999"));
   }

   @Test
   public void aDownloadedIconIsRememberedForItsUrl() {
      ImageIcon icon = new ImageIcon(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB));
      assertNull(ShowImageCache.getIcon("http://i.tivo.com/x.jpg"));
      ShowImageCache.putIcon("http://i.tivo.com/x.jpg", icon);
      // Same instance back - the point is to skip decoding it again
      assertSame(icon, ShowImageCache.getIcon("http://i.tivo.com/x.jpg"));
   }

   @Test
   public void nullsAreIgnoredRatherThanStored() {
      // imageKey returns null for a row carrying neither id, and a failed
      // fetch returns a null icon - neither should poison the cache
      ShowImageCache.putUrl(null, "http://i.tivo.com/x.jpg");
      ShowImageCache.putUrl("tivo:cl.1", null);
      ShowImageCache.putIcon(null, new ImageIcon(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)));
      ShowImageCache.putIcon("http://i.tivo.com/x.jpg", null);

      assertEquals(0, ShowImageCache.urlCount());
      assertEquals(0, ShowImageCache.iconCount());
      assertNull(ShowImageCache.getUrl(null));
      assertNull(ShowImageCache.getIcon(null));
   }

   @Test
   public void bothCachesAreBounded() {
      // Icons are the expensive ones to hold: a 120x170 image is ~80KB decoded,
      // so a long browse must not grow without limit
      ImageIcon icon = new ImageIcon(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB));
      for (int i = 0; i < 2000; ++i) {
         ShowImageCache.putUrl("tivo:cl." + i, "http://i.tivo.com/" + i + ".jpg");
         ShowImageCache.putIcon("http://i.tivo.com/" + i + ".jpg", icon);
      }
      assertTrue(ShowImageCache.urlCount() <= 500,
         "url cache grew to " + ShowImageCache.urlCount());
      assertTrue(ShowImageCache.iconCount() <= 60,
         "icon cache grew to " + ShowImageCache.iconCount());
      // The most recent entries are the ones kept
      assertNotNull(ShowImageCache.getUrl("tivo:cl.1999"));
      assertNotNull(ShowImageCache.getIcon("http://i.tivo.com/1999.jpg"));
   }

   @Test
   public void theLeastRecentlyUsedEntryIsTheOneDropped() {
      // Browsing back and forth between two series must not evict the one you
      // keep returning to
      for (int i = 0; i < 60; ++i)
         ShowImageCache.putIcon("http://i.tivo.com/" + i + ".jpg",
            new ImageIcon(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)));

      ShowImageCache.getIcon("http://i.tivo.com/0.jpg"); // touch the oldest
      ShowImageCache.putIcon("http://i.tivo.com/new.jpg",
         new ImageIcon(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)));

      assertNotNull(ShowImageCache.getIcon("http://i.tivo.com/0.jpg"),
         "the entry just used was evicted");
      assertNull(ShowImageCache.getIcon("http://i.tivo.com/1.jpg"));
   }
}
