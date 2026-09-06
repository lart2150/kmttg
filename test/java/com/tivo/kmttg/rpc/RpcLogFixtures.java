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
      final String name;
      final int[][] ranges;   // {from, toInclusive} pairs
      final String[] types;   // every command in the slice must be one of these

      Slice(String name, String[] types, int[][] ranges) {
         this.name = name;
         this.types = types;
         this.ranges = ranges;
      }
   }

   // Slices of the reference capture (2002 commands taken from a Bolt while
   // driving the GUI through my shows, todo, deleted, guide, search, premieres,
   // one pass edits and thumbs). The comment on each is the call it replays.
   private static final Slice[] SLICES = {
      // MyShows(null): 2 flattened NPL pages, a Search per recording, then the
      // addSeriesID collectionSearch batch.
      new Slice("commands_myshows", new String[] { "MyShows", "Search", "collectionSearch" },
            new int[][] { {426, 427}, {471, 520}, {1558, 1558} }),
      // SeasonPasses(job): the subscription list plus the per-subscription
      // upcoming/conflicts idSequence lookups (trimmed to the first 20 passes).
      new Slice("commands_seasonpasses_job", new String[] { "SeasonPasses", "recordingSearch" },
            new int[][] { {279, 279}, {280, 319} }),
      // DeletedShows(job): the idSequence count paged in 1000 item batches,
      // then the 20 at a time listing ending on a short page.
      new Slice("commands_deleted_job", new String[] { "Deleted" },
            new int[][] { {76, 79}, {128, 128} }),
      // ToDo(job): the idSequence count, listing pages, then an empty page.
      new Slice("commands_todo_job", new String[] { "ToDo" },
            new int[][] { {418, 421}, {425, 425} }),
      // SeasonPremieres(): one channel (11-1) over two days.
      new Slice("commands_premieres", new String[] { "GridSearch" },
            new int[][] { {242, 242}, {249, 249} }),
      // ChannelList(): the full 207 channel lineup.
      new Slice("commands_channellist", new String[] { "channelSearch" },
            new int[][] { {71, 71} }),
      // seasonYearSearch(): two contentSearch pages that come back identical.
      new Slice("commands_seasonyear", new String[] { "contentSearch" },
            new int[][] { {1855, 1856} }),
      // channelSearch(collectionId): the offers a collection airs on.
      new Slice("commands_collectionchannels", new String[] { "offerSearch" },
            new int[][] { {1854, 1854} }),
      // getThumbsRating() / setThumbsRating().
      new Slice("commands_thumbsrating", new String[] { "userContentSearch", "userContentStore" },
            new int[][] { {1566, 1567} }),
      // searchKeywords("cheer"): a full page of offers then an empty one.
      new Slice("commands_searchcheer", new String[] { "OfferSearch" },
            new int[][] { {186, 187} }),
      // streamingEntries(null): the top level My Shows folder listing.
      new Slice("commands_streaming", new String[] { "myShowsItemSearch" },
            new int[][] { {133, 133} }),
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
      List<JSONObject> log = read(logFile);
      System.out.println("Read " + log.size() + " commands from " + logFile);

      outDir.mkdirs();
      for (Slice slice : SLICES)
         write(outDir, slice, log);
      // Give every fixture the same treatment, including the ones captured
      // from a live TiVo by RpcFixtureCapture.
      FixtureJson.normalizeDirectory(outDir);
      System.out.println("Done.");
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
