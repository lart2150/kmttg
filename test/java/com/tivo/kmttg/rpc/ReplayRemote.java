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
 */
public class ReplayRemote extends Remote {
   private final Map<String, Deque<JSONObject>> byType = new HashMap<>();

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

   @Override
   public JSONObject Command(String type, JSONObject json) {
      Deque<JSONObject> q = byType.get(type);
      if (q == null || q.isEmpty())
         return null; // past the recorded trace -> loop-terminating
      return q.pollFirst();
   }
}
