package com.tivo.kmttg.rpc;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;

/**
 * Shared shaping for the checked in RPC fixtures: drop the parts of a TiVo
 * response no kmttg code ever looks at, and write what is left in a form that
 * stays reviewable in a diff.
 *
 * A TiVo response carries a lot that kmttg never reads - artwork ids, DRM
 * playback policies, per-rating database keys, tuner flags - and a full My
 * Shows capture is mostly that. The fixtures only need to be faithful in the
 * fields the code actually consumes, so {@link #prune} strips the rest. Every
 * key in {@link #UNREAD} was checked against src/, release/web/js/ and the
 * tests before being listed; if a new caller starts reading one, take it off
 * this list and regenerate.
 *
 * NOTE: prune() only removes keys. It never rewrites a value, so what stays in
 * a fixture is still exactly what the TiVo sent.
 */
public final class FixtureJson {
   private FixtureJson() {}

   /**
    * Keys nothing in kmttg reads, grouped by where they come from. Removing
    * these is roughly half the bytes of a raw capture.
    */
   static final Set<String> UNREAD = new HashSet<String>(Arrays.asList(
      // Ratings: kmttg shows tvRating/mpaaRating, never the parental control
      // scores or the rating database's own row ids.
      "internalRating", "rating", "ratingTypeId", "ratingValueId",
      // Artwork bookkeeping. The image array itself stays - ShowDetails reads
      // imageUrl out of it - but not the per-image catalog fields.
      "imageId", "imageType",
      // Playback/DRM policy and stream plumbing; kmttg downloads over HTTP.
      "recordingPlaybackPolicy", "mrsPlaybackPolicy", "cgms", "transportType",
      "mimeType", "contentType", "bitrate", "quality", "diskPartition",
      // Channel flags kmttg never filters or displays on (it uses callSign,
      // channelNumber, sourceType, stationId and isReceived).
      "logoIndex", "affiliate", "partnerStationId", "isHdtv", "isDigital",
      "isEntitled", "isBlocked", "isKidZone", "isAdult",
      // Scheduling fields with no reader: kmttg works from scheduledStartTime /
      // startTime / duration, and the padding flags rather than these mirrors.
      "requestedEndTime", "actualEndTime", "expectedDeletion",
      "useOfferStartPadding", "useOfferEndPadding",
      // Miscellaneous unread extras.
      "shortTitle", "descriptionLanguage", "sportsEventInfo",
      "subscriptionForCollectionIdAndChannel"
   ));

   /** Strips every {@link #UNREAD} key from the array, in place. */
   public static JSONArray prune(JSONArray a) {
      pruneNode(a);
      return a;
   }

   private static void pruneNode(Object node) {
      if (node instanceof JSONObject) {
         JSONObject o = (JSONObject) node;
         for (String key : keysOf(o)) {
            if (UNREAD.contains(key))
               o.remove(key);
            else
               pruneNode(o.opt(key));
         }
      } else if (node instanceof JSONArray) {
         JSONArray a = (JSONArray) node;
         for (int i = 0; i < a.length(); ++i)
            pruneNode(a.opt(i));
      }
   }

   // Snapshot of the key set, so removing while walking is safe.
   private static String[] keysOf(JSONObject o) {
      String[] keys = new String[o.length()];
      int i = 0;
      for (Iterator<?> it = o.keys(); it.hasNext(); )
         keys[i++] = (String) it.next();
      return keys;
   }

   /**
    * Writes the array one entry per line - each recording, command or channel
    * compact on its own line. Indenting every nested key instead costs about
    * half the file for whitespace, while a single compact line makes any change
    * show up as the whole fixture being rewritten. Per-entry lines keep the
    * diffs (and `grep`) useful at a fraction of the size.
    */
   public static void write(File file, JSONArray a) throws Exception {
      StringBuilder sb = new StringBuilder("[\n");
      for (int i = 0; i < a.length(); ++i) {
         sb.append(a.get(i).toString());
         if (i < a.length() - 1)
            sb.append(',');
         sb.append('\n');
      }
      sb.append("]\n");
      try (FileWriter w = new FileWriter(file, StandardCharsets.UTF_8)) {
         w.write(sb.toString());
      }
   }

   /**
    * Re-reads every fixture in the directory and writes it back pruned and
    * reformatted. Idempotent, so it is safe to run after any capture; it exists
    * so the fixtures taken from a live TiVo get the same treatment as the ones
    * sliced out of an rpc.jsonl log.
    */
   public static void normalizeDirectory(File dir) throws Exception {
      File[] files = dir.listFiles();
      if (files == null)
         return;
      Arrays.sort(files);
      long before = 0, after = 0;
      for (File f : files) {
         if (! f.getName().endsWith(".json"))
            continue;
         before += f.length();
         List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
         write(f, prune(new JSONArray(String.join("\n", lines))));
         after += f.length();
      }
      System.out.println("  normalized " + dir + ": " + before + " -> " + after + " bytes");
   }
}
