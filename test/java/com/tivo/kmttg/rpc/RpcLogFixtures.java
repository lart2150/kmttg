package com.tivo.kmttg.rpc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;

/**
 * One-off developer tool (NOT a unit test) that turns a -rpcLog capture
 * (rpc.jsonl, written by {@link com.tivo.kmttg.util.rpcLog}) into the command
 * trace fixtures under test/resources/fixtures/ that {@link ReplayRemote}
 * replays.
 *
 * The log is already sanitized when it is written, and each line is already a
 * {type, request, response} triple plus timing, so all this does is pick the
 * lines belonging to one high-level call and rewrite them in the fixture shape.
 *
 * Slices are given as line ranges into a specific capture, each tagged with the
 * command types it is expected to contain; pointing this at a different log
 * fails loudly rather than writing a mislabeled fixture.
 *
 * Run via:  gradlew rpcLogFixtures -PrpcLog="C:\path\to\rpc.jsonl"
 */
public class RpcLogFixtures {

   /** A fixture: the file to write, and the log lines (0 based) that make it up. */
   private static class Slice {
      final String capture;   // which rpc.jsonl these line numbers belong to
      final String name;
      final int[][] ranges;   // {from, toInclusive} pairs
      final String[] types;   // every command in the slice must be one of these

      Slice(String capture, String name, String[] types, int[][] ranges) {
         this.capture = capture;
         this.name = name;
         this.types = types;
         this.ranges = ranges;
      }
   }

   // Line numbers only mean anything against the capture they were read from,
   // and -rpcLog overwrites rpc.jsonl on every run, so each slice names its
   // capture and you pick one per invocation:
   //
   //   gradlew rpcLogFixtures -PrpcLog="C:\...\rpc.jsonl" -Pcapture=2026-09-06
   //
   // Without that, a stale range quietly lands on whatever commands now sit at
   // those lines - and the type guard below will not always catch it, because
   // a range that used to be MyShows/Search can land on a run of Search.
   //
   // 2026-09-05: 2002 commands from a Bolt driving my shows, todo, deleted,
   //             guide, search, premieres, one pass edits and thumbs.
   // 2026-09-06-1: 1233 commands from the same Bolt recording and cancelling
   //             single shows, then an NPL refresh with download URLs.
   // 2026-09-06-2: 723 commands - the Info tab, a streaming walk, a one pass
   //             created / modified / deleted, and several searches.
   //
   // The comment on each slice is the call it replays.
   private static final Slice[] SLICES = {
      // MyShows(null): 2 flattened NPL pages, a Search per recording, then the
      // addSeriesID collectionSearch batch.
      new Slice("2026-09-05", "commands_myshows", new String[] { "MyShows", "Search", "collectionSearch" },
            new int[][] { {426, 427}, {471, 520}, {1558, 1558} }),
      // SeasonPasses(job): the subscription list plus the per-subscription
      // upcoming/conflicts idSequence lookups (trimmed to the first 20 passes).
      new Slice("2026-09-05", "commands_seasonpasses_job", new String[] { "SeasonPasses", "recordingSearch" },
            new int[][] { {279, 279}, {280, 319} }),
      // DeletedShows(job): the idSequence count paged in 1000 item batches,
      // then the 20 at a time listing ending on a short page.
      new Slice("2026-09-05", "commands_deleted_job", new String[] { "Deleted" },
            new int[][] { {76, 79}, {128, 128} }),
      // ToDo(job): the idSequence count, listing pages, then an empty page.
      new Slice("2026-09-05", "commands_todo_job", new String[] { "ToDo" },
            new int[][] { {418, 421}, {425, 425} }),
      // SeasonPremieres(): one channel (11-1) over two days.
      new Slice("2026-09-05", "commands_premieres", new String[] { "GridSearch" },
            new int[][] { {242, 242}, {249, 249} }),
      // ChannelList(): the full 207 channel lineup.
      new Slice("2026-09-05", "commands_channellist", new String[] { "channelSearch" },
            new int[][] { {71, 71} }),
      // seasonYearSearch(): two contentSearch pages that come back identical.
      new Slice("2026-09-05", "commands_seasonyear", new String[] { "contentSearch" },
            new int[][] { {1855, 1856} }),
      // channelSearch(collectionId): the offers a collection airs on.
      new Slice("2026-09-05", "commands_collectionchannels", new String[] { "offerSearch" },
            new int[][] { {1854, 1854} }),
      // getThumbsRating() / setThumbsRating().
      new Slice("2026-09-05", "commands_thumbsrating", new String[] { "userContentSearch", "userContentStore" },
            new int[][] { {1566, 1567} }),
      // searchKeywords("cheer"): a full page of offers then an empty one.
      new Slice("2026-09-05", "commands_searchcheer", new String[] { "OfferSearch" },
            new int[][] { {186, 187} }),

      // The recording writes, for their request shaping. Each recorded request
      // is what a real TiVo accepted, so it doubles as the expected value.
      // NOTE: index 24 is a fifth Singlerecording - the GUI passing a whole
      // existing recording back through to reschedule it. It is left out on
      // purpose: most of that object is keys FixtureJson.prune strips, so the
      // fixture would no longer be what went over the wire.
      new Slice("2026-09-06-1", "commands_writes",
            new String[] { "Singlerecording", "Cancel", "Delete", "StopRecording" },
            new int[][] { {10, 10}, {13, 13}, {27, 28}, {33, 33}, {1177, 1179} }),
      // getURLs(): recordingId -> mfs id, the lookup behind every download URL.
      new Slice("2026-09-06-1", "commands_geturls", new String[] { "idSearch" },
            new int[][] { {82, 82}, {84, 84} }),

      // streamingEntries(null): the top level My Shows listing plus the first
      // few folder reads it recurses into. The 2026-09-05 slice had only the
      // top level, so the walk hit an exhausted trace instead of real children;
      // all 38 folders would be 1.6MB of fixture for nothing extra, since
      // issued() counts the attempts either way.
      new Slice("2026-09-06-2", "commands_streaming", new String[] { "myShowsItemSearch" },
            new int[][] { {67, 72} }),
      // The Info tab: one Refresh, then Network Connect and the status poll it
      // spins on. Trimmed to the first few polls plus the ones that finish.
      new Slice("2026-09-06-2", "commands_info",
            new String[] { "SysInfo", "systemInformationGet", "WhatsOn", "TunerInfo",
                           "PhoneHome", "phoneHomeStatusEventRegister" },
            new int[][] { {1, 9}, {64, 66} }),
      // One pass created, modified and removed. Each recorded request is the
      // object the GUI handed Command, so it is the test's input as well as
      // half of what it checks.
      new Slice("2026-09-06-2", "commands_onepass",
            new String[] { "Seasonpass", "ModifySP", "Unsubscribe" },
            new int[][] { {119, 119}, {580, 580}, {722, 722} }),
   };

   public static void main(String[] args) throws Exception {
      File outDir = new File("test/resources/fixtures");
      if (args.length == 0) {
         // No capture given - just re-shape what is already checked in.
         System.out.println("No rpc.jsonl given; normalizing the existing fixtures only.");
         FixtureJson.normalizeDirectory(outDir);
         return;
      }
      String logFile = args[0];
      String capture = args.length > 1 ? args[1] : null;
      if (capture == null)
         throw new IllegalArgumentException(
               "which capture is this? pass -Pcapture=<id>, one of " + captures()
               + " - the line numbers in a slice only mean anything against the "
               + "capture they were read from");

      List<JSONObject> log = read(logFile);
      System.out.println("Read " + log.size() + " commands from " + logFile);

      outDir.mkdirs();
      int written = 0;
      for (Slice slice : SLICES) {
         if (! slice.capture.equals(capture))
            continue;
         write(outDir, slice, log);
         written++;
      }
      if (written == 0)
         throw new IllegalStateException("no slices tagged '" + capture + "'; known captures: " + captures());
      System.out.println("Wrote " + written + " of " + SLICES.length + " slices (capture " + capture + ")");
      // Give every fixture the same treatment, including the ones captured
      // from a live TiVo by RpcFixtureCapture.
      FixtureJson.normalizeDirectory(outDir);
      System.out.println("Done.");
   }

   private static String captures() {
      List<String> seen = new ArrayList<String>();
      for (Slice slice : SLICES)
         if (! seen.contains(slice.capture))
            seen.add(slice.capture);
      return String.join(", ", seen);
   }

   private static List<JSONObject> read(String file) throws Exception {
      List<JSONObject> log = new ArrayList<JSONObject>();
      try (BufferedReader in = new BufferedReader(new FileReader(file))) {
         String line;
         while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.length() > 0)
               log.add(new JSONObject(line));
         }
      }
      return log;
   }

   private static void write(File dir, Slice slice, List<JSONObject> log) throws Exception {
      JSONArray out = new JSONArray();
      for (int[] range : slice.ranges) {
         if (range[1] >= log.size())
            throw new IllegalStateException(slice.name + ": log has only " + log.size()
                  + " commands, needs line " + range[1]);
         for (int i = range[0]; i <= range[1]; ++i) {
            JSONObject entry = log.get(i);
            String type = entry.getString("type");
            if (! expected(slice, type))
               throw new IllegalStateException(slice.name + ": line " + i + " is a " + type
                     + " command, expected one of " + String.join("/", slice.types)
                     + " - is this the capture these line numbers came from?");
            // The log line carries ts/tivo/ms as well; the fixture only needs
            // what ReplayRemote reads back.
            JSONObject rec = new JSONObject();
            rec.put("type", type);
            rec.put("request", entry.has("request") ? entry.getJSONObject("request") : new JSONObject());
            if (entry.has("response"))
               rec.put("response", entry.getJSONObject("response"));
            out.put(rec);
         }
      }
      File f = new File(dir, slice.name + ".json");
      FixtureJson.write(f, FixtureJson.prune(out));
      System.out.println("  " + f.getName() + ": " + out.length() + " commands, " + f.length() + " bytes");
   }

   private static boolean expected(Slice slice, String type) {
      for (String t : slice.types)
         if (t.equals(type))
            return true;
      return false;
   }
}
