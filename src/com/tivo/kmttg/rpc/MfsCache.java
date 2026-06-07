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
package com.tivo.kmttg.rpc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.log;

/**
 * Persistent cache of recordingId -&gt; mfs id mappings, keyed per TiVo. This
 * mapping is stable for the life of a recording, so caching it lets the NPL
 * fetch ({@link Remote#MyShows}) skip the per-recording idSearch RPC - which
 * exists only to build the legacy TTG download URLs - for any recording seen
 * before, including across restarts.
 *
 * Stored as tab-separated lines (tivoName\trecordingId\tmfsId) under
 * &lt;programDir&gt;/cache-recordingToMFS.txt.
 */
public class MfsCache {
   private static final Map<String,String> cache = new ConcurrentHashMap<String,String>();
   private static boolean loaded = false;
   private static boolean dirty = false;

   private static String file() {
      return config.programDir + File.separator + "cache-recordingToMFS.txt";
   }

   private static String key(String tivoName, String recordingId) {
      return tivoName + "\t" + recordingId;
   }

   public static synchronized void load() {
      if (loaded)
         return;
      loaded = true;
      File f = new File(file());
      if (! f.isFile())
         return;
      try (BufferedReader in = new BufferedReader(new FileReader(f))) {
         String line;
         while ((line = in.readLine()) != null) {
            String[] p = line.split("\t", 3);
            if (p.length == 3)
               cache.put(p[0] + "\t" + p[1], p[2]);
         }
      } catch (Exception e) {
         log.error("MfsCache load - " + e.getMessage());
      }
   }

   /** Cached mfs id for the recording on the given TiVo, or null if unknown. */
   public static String get(String tivoName, String recordingId) {
      if (tivoName == null || recordingId == null)
         return null;
      if (! loaded)
         load();
      return cache.get(key(tivoName, recordingId));
   }

   public static void put(String tivoName, String recordingId, String mfsId) {
      if (tivoName == null || recordingId == null || mfsId == null)
         return;
      String previous = cache.put(key(tivoName, recordingId), mfsId);
      if (! mfsId.equals(previous))
         dirty = true;
   }

   /** Persist the cache to disk, but only if it changed since the last save. */
   public static synchronized void save() {
      if (! dirty)
         return;
      try (BufferedWriter out = new BufferedWriter(new FileWriter(file()))) {
         for (Map.Entry<String,String> e : cache.entrySet()) {
            // key is already tivoName\trecordingId
            out.write(e.getKey() + "\t" + e.getValue());
            out.write("\n");
         }
         dirty = false;
      } catch (Exception e) {
         log.error("MfsCache save - " + e.getMessage());
      }
   }

   // Reset in-memory state (test support).
   static synchronized void reset() {
      cache.clear();
      loaded = false;
      dirty = false;
   }
}
