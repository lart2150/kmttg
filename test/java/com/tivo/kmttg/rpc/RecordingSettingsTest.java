package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;

/**
 * Tests {@link Remote#recordingSettings}, which feeds the "TiVo Defaults" button
 * in the recording and season pass option dialogs.
 */
public class RecordingSettingsTest {

   private static JSONArray trace(String type, JSONObject response) throws Exception {
      JSONObject rec = new JSONObject();
      rec.put("type", type);
      rec.put("response", response);
      JSONArray log = new JSONArray();
      log.put(rec);
      return log;
   }

   @Test
   public void recordingSettingsAsksForTheRecordingGroup() throws Exception {
      JSONObject reply = new JSONObject();
      reply.put("type", "recordingSettings");
      reply.put("defaultShowStatus", "rerunsAllowed");
      reply.put("defaultDeletionPolicy", "whenSpaceNeeded");
      reply.put("defaultMaxRecordings", 25);
      reply.put("defaultStartTimePadding", 0);
      reply.put("defaultStopTimePadding", 0);
      ReplayRemote r = new ReplayRemote(trace("settingsGet", reply));

      JSONObject settings = r.recordingSettings();

      assertNotNull(settings);
      assertEquals(25, settings.getInt("defaultMaxRecordings"));
      assertEquals("recording", r.lastRequest("settingsGet").getString("settingGroup"));
   }

   @Test
   public void recordingSettingsIgnoresAnyOtherReply() throws Exception {
      // settingsGet answers per group; video and standbyMode come back "Not implemented"
      JSONObject reply = new JSONObject();
      reply.put("type", "audioSettings");
      ReplayRemote r = new ReplayRemote(trace("settingsGet", reply));

      assertNull(r.recordingSettings());
   }
}
