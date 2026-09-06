package com.tivo.kmttg.rpc;

import static com.tivo.kmttg.rpc.JsonAssert.assertSameJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;

/**
 * Tests the request-shaping half of {@link Remote#Command} - the chain that
 * turns a friendly name plus a few ids into the wire request the TiVo acts on.
 *
 * This is where the write commands live, and they are almost all the same
 * request: cancelling a scheduled recording, deleting a finished one, stopping
 * one in progress and permanently purging one are four `recordingUpdate` calls
 * that differ only in a `state` string. One wrong constant there means a menu
 * item silently does something else to the viewer's recordings, and the TiVo
 * answers `{"type":"success"}` either way - the response cannot tell you.
 *
 * The expected values are the requests a real TiVo accepted, replayed out of a
 * capture rather than written by hand. {@link ShapingRemote} reproduces the one
 * thing the real transport does to a request body (stamping the wire name on as
 * `type`), so a shaped request and a captured one compare directly.
 */
public class RemoteCommandShapeTest {

   /** The n'th recorded command of this type, as {type, request, response}. */
   private static JSONObject recorded(String type, int n) throws Exception {
      JSONArray log = Fixtures.load("commands_writes.json");
      int seen = 0;
      for (int i = 0; i < log.length(); i++) {
         JSONObject rec = log.getJSONObject(i);
         if (rec.getString("type").equals(type) && seen++ == n)
            return rec;
      }
      throw new IllegalStateException("no " + type + " #" + n + " in commands_writes.json");
   }

   /** A recorded request with the given keys taken back off. */
   private static JSONObject without(JSONObject request, String... added) throws Exception {
      JSONObject caller = new JSONObject(request.toString());
      for (String key : added)
         caller.remove(key);
      return caller;
   }

   // ---- the recordingUpdate family ----------------------------------------

   @Test
   public void cancelRebuildsTheRecordedRequest() throws Exception {
      // What the ToDo tab sends: just the recordingId, as an array.
      JSONObject request = recorded("Cancel", 0).getJSONObject("request");
      ShapingRemote r = new ShapingRemote();

      r.Command("Cancel", without(request, "state", "bodyId", "type"));

      assertNotNull(r.last(), "Command never built a request");
      assertEquals("recordingUpdate", r.last().rpc);
      assertSameJson(request, r.last().json, "Cancel");
   }

   @Test
   public void deleteRebuildsTheRecordedRequest() throws Exception {
      JSONObject request = recorded("Delete", 0).getJSONObject("request");
      ShapingRemote r = new ShapingRemote();

      r.Command("Delete", without(request, "state", "bodyId", "type"));

      assertEquals("recordingUpdate", r.last().rpc);
      assertSameJson(request, r.last().json, "Delete");
   }

   @Test
   public void stopRecordingRebuildsTheRecordedRequest() throws Exception {
      // Note the recorded shape: My Shows sends recordingId as a bare string
      // here, not the array the other three send. Command passes it straight
      // through, so the difference has to survive.
      JSONObject request = recorded("StopRecording", 0).getJSONObject("request");
      assertTrue(request.get("recordingId") instanceof String,
         "expected the captured StopRecording to carry a bare recordingId");
      ShapingRemote r = new ShapingRemote();

      r.Command("StopRecording", without(request, "state", "bodyId", "type"));

      assertEquals("recordingUpdate", r.last().rpc);
      assertSameJson(request, r.last().json, "StopRecording");
   }

   @Test
   public void everyRecordingUpdateSetsItsOwnState() throws Exception {
      // The whole family in one place. Cancel, Delete and StopRecording are
      // pinned to a real capture above; Undelete and PermanentlyDelete have no
      // recorded request here, so this is what stops their states drifting.
      String[][] expected = {
         { "Cancel",            "cancelled" },
         { "Delete",            "deleted" },
         { "Undelete",          "complete" },
         { "StopRecording",     "complete" },
         { "PermanentlyDelete", "contentDeleted" },
      };
      for (String[] pair : expected) {
         JSONObject ids = new JSONObject();
         ids.put("recordingId", new JSONArray("[\"tivo:rc.1\"]"));
         ShapingRemote r = new ShapingRemote();

         r.Command(pair[0], ids);

         assertEquals("recordingUpdate", r.last().rpc, pair[0] + " should be a recordingUpdate");
         assertEquals(pair[1], r.last().json.getString("state"), "wrong state for " + pair[0]);
         assertEquals(Fixtures.BODY_ID, r.last().json.getString("bodyId"),
            pair[0] + " should carry the bodyId");
      }
   }

   // ---- Singlerecording: the subscribe wrapper -----------------------------

   @Test
   public void singleRecordingWrapsTheRecordedOfferSource() throws Exception {
      // Unlike the recordingUpdate family, this one nests what the caller passed
      // inside a subscribe envelope - so the capture is the inner half, and the
      // wrapper around it is what the test has to check.
      JSONObject offerSource = recorded("Singlerecording", 0).getJSONObject("request");
      ShapingRemote r = new ShapingRemote();

      r.Command("Singlerecording", without(offerSource, "type"));

      assertEquals("subscribe", r.last().rpc);
      JSONObject sent = r.last().json;
      assertSameJson(offerSource, sent.getJSONObject("idSetSource"), "Singlerecording idSetSource");
      assertEquals(Fixtures.BODY_ID, sent.getString("bodyId"));
      assertEquals("best", sent.getString("recordingQuality"));
      assertEquals(1, sent.getInt("maxRecordings"), "a single recording is exactly one");
      assertEquals("false", sent.getString("ignoreConflicts"));
      // Padding and keep behaviour are read off the caller's offer source and
      // repeated on the wrapper - the TiVo reads them there, not from the inner.
      assertEquals(offerSource.getString("keepBehavior"), sent.getString("keepBehavior"));
      assertEquals(offerSource.getInt("startTimePadding"), sent.getInt("startTimePadding"));
      assertEquals(offerSource.getInt("endTimePadding"), sent.getInt("endTimePadding"));
   }

   @Test
   public void singleRecordingCarriesEveryRecordedVariant() throws Exception {
      // The capture has three, with different keep behaviour and padding.
      JSONArray log = Fixtures.load("commands_writes.json");
      int checked = 0;
      for (int i = 0; i < log.length(); i++) {
         JSONObject rec = log.getJSONObject(i);
         if (! rec.getString("type").equals("Singlerecording"))
            continue;
         JSONObject offerSource = rec.getJSONObject("request");
         ShapingRemote r = new ShapingRemote();

         r.Command("Singlerecording", without(offerSource, "type"));

         assertSameJson(offerSource, r.last().json.getJSONObject("idSetSource"),
            "Singlerecording #" + checked);
         assertEquals(offerSource.getString("keepBehavior"), r.last().json.getString("keepBehavior"),
            "keepBehavior lost on Singlerecording #" + checked);
         checked++;
      }
      assertTrue(checked >= 3, "expected several recorded Singlerecording variants, saw " + checked);
   }

   @Test
   public void singleRecordingDefaultsToFifoWhenNoKeepBehaviourIsGiven() throws Exception {
      JSONObject offerSource = new JSONObject();
      offerSource.put("contentId", "tivo:ct.1");
      offerSource.put("offerId", "tivo:of.1");
      ShapingRemote r = new ShapingRemote();

      r.Command("Singlerecording", offerSource);

      assertEquals("fifo", r.last().json.getString("keepBehavior"),
         "an unspecified keep behaviour should not become 'forever'");
      assertFalse(r.last().json.has("startTimePadding"), "no padding should be invented");
      assertFalse(r.last().json.has("endTimePadding"), "no padding should be invented");
   }

   @Test
   public void singleRecordingMovesConflictsOnlyOntoTheWrapper() throws Exception {
      // conflictsOnly asks the TiVo what this would clash with instead of
      // scheduling it. The TiVo only honours it on the wrapper, so leaving a
      // copy on the offer source would schedule the recording for real.
      JSONObject offerSource = new JSONObject();
      offerSource.put("contentId", "tivo:ct.1");
      offerSource.put("offerId", "tivo:of.1");
      offerSource.put("conflictsOnly", true);
      ShapingRemote r = new ShapingRemote();

      r.Command("Singlerecording", offerSource);

      assertEquals(true, r.last().json.get("conflictsOnly"), "conflictsOnly lost on the way out");
      assertFalse(r.last().json.getJSONObject("idSetSource").has("conflictsOnly"),
         "conflictsOnly must not be left on the offer source");
   }

   // ---- re-prioritizing ----------------------------------------------------

   @Test
   public void prioritizeKeepsItsOwnWireRequest() throws Exception {
      // Unsubscribe is the neighbouring one-liner and is covered, against a real
      // captured request, in OnePassShapeTest.
      ShapingRemote prio = new ShapingRemote();
      JSONObject order = new JSONObject();
      order.put("subscriptionIdV2", new JSONArray("[\"tivo:sb.2\",\"tivo:sb.1\"]"));
      prio.Command("Prioritize", order);
      assertEquals("subscriptionsReprioritize", prio.last().rpc);
      assertEquals("[\"tivo:sb.2\",\"tivo:sb.1\"]",
         prio.last().json.getJSONArray("subscriptionIdV2").toString(),
         "the requested order is the whole point of the call");
   }
}
