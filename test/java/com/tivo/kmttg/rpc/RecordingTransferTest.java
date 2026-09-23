package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;

/**
 * Tests {@link Remote#recordingTransfer}.
 *
 * The reply is real: a Bolt asked to pull a recording from a TiVo that
 * doesn't exist answered with a recordingTransferResult carrying
 * reason=sourceNotFound (2026-09-23), which also showed kmttg's partner id is
 * allowed the request. A successful copy has never been seen, for want of a
 * second TiVo.
 */
public class RecordingTransferTest {

   private static JSONArray trace(String type, JSONObject response) throws Exception {
      JSONObject rec = new JSONObject();
      rec.put("type", type);
      rec.put("response", response);
      JSONArray log = new JSONArray();
      log.put(rec);
      return log;
   }

   @Test
   public void transferNamesTheRecordingAndTheTivoToPullFrom() throws Exception {
      JSONObject reply = new JSONObject();
      reply.put("type", "recordingTransferResult");
      reply.put("reason", "sourceNotFound");
      ReplayRemote r = new ReplayRemote(trace("recordingTransfer", reply));

      JSONObject result = r.recordingTransfer("tivo:rc.1", "tsn:0000000000000001");

      assertEquals("sourceNotFound", result.getString("reason"));
      JSONObject sent = r.lastRequest("recordingTransfer");
      assertEquals("tivo:rc.1", sent.getString("recordingId"));
      assertEquals("tsn:0000000000000001", sent.getString("remoteHostBodyId"));
      assertEquals(Fixtures.BODY_ID, sent.getString("bodyId"));
   }
}
