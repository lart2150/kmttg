/*
 * Copyright 2008-Present Kevin Moye <moyekj@yahoo.com>.
 *
 * This file is part of kmttg package.
 *
 * kmttg is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this project.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tivo.kmttg.gui.dialog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.ImageIcon;

/**
 * Session cache for the Show information artwork: the url lookup, which costs
 * an rpc connection, and the download it names. Keyed by collection because
 * the artwork belongs to the series - see ShowDetailsImageKeyTest. In memory
 * only, since artwork can be re-issued upstream.
 */
final class ShowImageCache {
   // Urls are cheap to hold; a decoded 120x170 icon is ~80KB, so it gets a
   // much smaller bound. Both drop least-recently-used first.
   private static final int MAX_URLS = 500;
   private static final int MAX_ICONS = 60;

   private static final Map<String,String> urls = lru(MAX_URLS);
   private static final Map<String,ImageIcon> icons = lru(MAX_ICONS);

   private ShowImageCache() {}

   private static <V> Map<String,V> lru(final int max) {
      return Collections.synchronizedMap(new LinkedHashMap<String,V>(16, 0.75f, true) {
         private static final long serialVersionUID = 1L;
         @Override protected boolean removeEldestEntry(Map.Entry<String,V> eldest) {
            return size() > max;
         }
      });
   }

   // collectionId (or contentId for a row without one) -> artwork url
   static String getUrl(String collectionId) {
      return collectionId == null ? null : urls.get(collectionId);
   }

   static void putUrl(String collectionId, String url) {
      if (collectionId != null && url != null)
         urls.put(collectionId, url);
   }

   static ImageIcon getIcon(String url) {
      return url == null ? null : icons.get(url);
   }

   static void putIcon(String url, ImageIcon icon) {
      if (url != null && icon != null)
         icons.put(url, icon);
   }

   static void clear() {
      urls.clear();
      icons.clear();
   }

   static int urlCount() { return urls.size(); }
   static int iconCount() { return icons.size(); }
}
