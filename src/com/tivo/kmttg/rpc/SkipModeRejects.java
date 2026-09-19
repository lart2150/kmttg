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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

// Recordings whose tivo.com SkipMode data did not describe the recording, remembered so the
// fetch does not ask again on every NPL refresh. Roughly a third of a real My Shows list is
// like this, and each one costs a cloud round trip to rediscover.
//
// Keyed on contentId AND clipMetadataId, not contentId alone. TiVo does revoke a clipMetadata
// and issue a replacement - one was seen going from cm.1275287 to cm.1303131 - and a new id is
// new data that deserves a fresh attempt. Remembering the id is what makes this a cache rather
// than a blacklist.
//
// Entries are pruned against the NPL, so a deleted recording does not keep its row forever.
public class SkipModeRejects {

   // Resolved per call rather than latched at class load: programDir is only known once config
   // has initialised, and a test pointing it elsewhere has no way to know if we loaded first.
   public static synchronized String iniFile() {
      return config.programDir + File.separator + "SkipModeRejects.ini";
   }

   // contentId -> clipMetadataId that failed. LinkedHashMap so rewrites keep file order.
   public static synchronized Map<String,String> load() {
      Map<String,String> rejects = new LinkedHashMap<String,String>();
      if (! file.isFile(iniFile())) return rejects;
      try (BufferedReader ifp = new BufferedReader(new FileReader(iniFile()))) {
         String line;
         while ((line = ifp.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            // "<contentId> <clipMetadataId> anything else" - the tail is a human-readable
            // title and reason, deliberately ignored so it can be edited freely.
            String[] l = line.split("\\s+");
            if (l.length >= 2) rejects.put(l[0], l[1]);
         }
      } catch (Exception e) {
         log.error("SkipModeRejects load - " + e.getMessage());
      }
      return rejects;
   }

   // True when this exact clipMetadata has already been tried and found not to fit. A changed
   // clipMetadataId is a miss, so replacement metadata is fetched again.
   public static synchronized boolean isRejected(String contentId, String clipMetadataId) {
      if (contentId == null || clipMetadataId == null) return false;
      return clipMetadataId.equals(load().get(contentId));
   }

   public static synchronized void add(String contentId, String clipMetadataId, String title,
         String reason) {
      debug.print("contentId=" + contentId);
      if (contentId == null || clipMetadataId == null) return;
      Map<String,String> rejects = load();
      // Replaces any older id for the same recording rather than accumulating rows.
      rejects.put(contentId, clipMetadataId);
      List<String> notes = new ArrayList<String>();
      notes.add("# Recordings whose tivo.com SkipMode data did not fit the recording.");
      notes.add("# <contentId> <clipMetadataId>  title - reason (the tail is informational)");
      notes.add("# Delete this file to make kmttg try all of them again.");
      write(rejects, contentId, (title == null ? "" : title)
         + (reason == null ? "" : " - " + reason), notes);
   }

   // Drop anything the NPL no longer lists, so the file tracks the recordings that exist.
   public static synchronized void prune(Set<String> liveContentIds) {
      if (liveContentIds == null || liveContentIds.isEmpty()) return;
      Map<String,String> rejects = load();
      if (rejects.isEmpty()) return;
      Map<String,String> kept = new LinkedHashMap<String,String>();
      for (Map.Entry<String,String> e : rejects.entrySet()) {
         if (liveContentIds.contains(e.getKey())) kept.put(e.getKey(), e.getValue());
      }
      if (kept.size() == rejects.size()) return;
      log.print("Pruned " + (rejects.size() - kept.size())
         + " SkipMode reject entries for recordings no longer in My Shows");
      write(kept, null, null, null);
   }

   // Rewrites the whole file: it is one short line per recording, and a rewrite keeps the
   // comment header and the "latest id wins" behaviour simple.
   private static void write(Map<String,String> rejects, String noteId, String note,
         List<String> header) {
      Map<String,String> notes = existingNotes();
      if (noteId != null) notes.put(noteId, note);
      if (header == null) header = defaultHeader();
      try (BufferedWriter ofp = new BufferedWriter(new FileWriter(iniFile(), false))) {
         String eol = "\r\n";
         for (String h : header) ofp.write(h + eol);
         for (Map.Entry<String,String> e : rejects.entrySet()) {
            String tail = notes.get(e.getKey());
            ofp.write(e.getKey() + " " + e.getValue()
               + (tail == null || tail.isEmpty() ? "" : "  " + tail) + eol);
         }
      } catch (Exception e) {
         log.error("SkipModeRejects write - " + e.getMessage());
      }
   }

   private static List<String> defaultHeader() {
      List<String> h = new ArrayList<String>();
      h.add("# Recordings whose tivo.com SkipMode data did not fit the recording.");
      h.add("# <contentId> <clipMetadataId>  title - reason (the tail is informational)");
      h.add("# Delete this file to make kmttg try all of them again.");
      return h;
   }

   // The informational tail per recording, so a rewrite does not throw away what was recorded.
   private static Map<String,String> existingNotes() {
      Map<String,String> notes = new HashMap<String,String>();
      if (! file.isFile(iniFile())) return notes;
      try (BufferedReader ifp = new BufferedReader(new FileReader(iniFile()))) {
         String line;
         while ((line = ifp.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] l = line.split("\\s+", 3);
            if (l.length == 3) notes.put(l[0], l[2].trim());
         }
      } catch (Exception e) {
         log.error("SkipModeRejects notes - " + e.getMessage());
      }
      return notes;
   }
}
