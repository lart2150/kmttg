package com.tivo.kmttg.rpc;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;

/**
 * An offline {@link Remote} that is never connected to a TiVo. It replays a
 * recorded command trace (captured by {@link RecordingRemote}) so the real
 * high-level Remote read methods - SeasonPasses / ToDo / CancelledShows /
 * DeletedShows / getThumbs - run their actual pagination, filtering, dedup and
 * sorting logic against canned responses.
 *
 * Responses are replayed as a FIFO queue per command type: each
 * {@link #Command(String, JSONObject)} for a given type returns the next
 * recorded response of that type, in capture order. This is robust against the
 * fact that the real Command() mutates the request object in place (so a
 * recorded request is not a reliable match key) - the read loops are
 * deterministic and single-threaded, so reissuing commands in the same order
 * reproduces the original call exactly.
 *
 * When a type's queue is exhausted, Command returns {@code null}, which is
 * exactly how each read loop detects "no more data" and terminates. That lets a
 * trace trimmed to the first few pages cleanly end the loop with a partial result.
 *
 * Because exhaustion also ends a loop, "the right answer came back" alone does
 * not prove a loop stopped for its own reasons. {@link #issued(String)} reports
 * how many commands of a type were attempted, so a test can pin the stop
 * condition - e.g. that seasonYearSearch noticed a repeated page rather than
 * simply running off the end of the trace.
 *
 * A recorded response also says nothing about what was asked for, which matters
 * on the write commands - a store that sent the wrong value still comes back
 * successful. {@link #lastRequest(String)} keeps a copy of the request as it was
 * at issue time so a test can check what went out.
 */
public class ReplayRemote extends Remote {
   private final Map<String, Deque<JSONObject>> byType = new HashMap<>();
   private final Map<String, Integer> issued = new HashMap<>();
   private final Map<String, JSONObject> lastRequest = new HashMap<>();

   public ReplayRemote(JSONArray log) throws Exception {
      super(); // no-connect seam
      // Seed bodyId for this unconnected Remote (IP="", port=0) so bodyId_get()
      // returns immediately instead of issuing an (unrecorded) bodyConfigSearch.
      config.bodyId_set("", 0, "tsn:0000000000000000");
      for (int i = 0; i < log.length(); i++) {
         JSONObject rec = log.getJSONObject(i);
         if (!rec.has("response"))
            continue; // recorded a null result -> just don't enqueue it
         byType.computeIfAbsent(rec.getString("type"), k -> new ArrayDeque<>())
               .addLast(rec.getJSONObject("response"));
      }
   }

   /** How many commands of this type were attempted, replayed or not. */
   public int issued(String type) {
      return issued.getOrDefault(type, 0);
   }

   /**
    * The most recent request of this type, copied when it was issued. The read
    * loops reuse one request object across pages, so a live reference would
    * only ever show the last offset.
    */
   public JSONObject lastRequest(String type) {
      return lastRequest.get(type);
   }

   @Override
   public JSONObject Command(String type, JSONObject json) {
      issued.merge(type, 1, Integer::sum);
      try {
         lastRequest.put(type, new JSONObject(json.toString()));
      } catch (Exception e) {
         lastRequest.remove(type);
      }
      Deque<JSONObject> q = byType.get(type);
      if (q == null || q.isEmpty())
         return null; // past the recorded trace -> loop-terminating
      return q.pollFirst();
   }
}
