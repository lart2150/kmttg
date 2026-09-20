package com.tivo.kmttg.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Hashtable;

import org.junit.jupiter.api.Test;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.rpc.Fixtures;

// Whether a show can be copied off the TiVo. The recording carries two answers
// - cgms, the broadcast flag, and tivoToGo, the TiVo's own verdict - and the NPL
// marks a show when either says no. They land on separate keys on purpose:
// CopyProtected is the one that refuses a download, so only the TiVo's own
// verdict may set it.
public class NplEntryTest {

   private static JSONObject recording(String cgms, Boolean tivoToGo) throws Exception {
      JSONObject json = new JSONObject();
      json.put("title", "Matlock");
      JSONObject drm = new JSONObject();
      if (cgms != null)
         drm.put("cgms", cgms);
      if (tivoToGo != null)
         drm.put("tivoToGo", tivoToGo.booleanValue());
      json.put("drm", drm);
      return json;
   }

   @Test
   void copyFreelyLeavesNoFlagAtAll() throws Exception {
      // The NPL keys off the presence of these, not their value, so a copy freely
      // show must not put either there with a "No" in it
      Hashtable<String,String> entry = parseNPL.rpcToHashEntry("Bolt", recording("copyFreely", true));
      assertFalse(entry.containsKey("CopyProtected"));
      assertFalse(entry.containsKey("CopyRestricted"));
   }

   @Test
   void aRestrictedCgmsFlagMarksTheShowWithoutBlockingIt() throws Exception {
      // The broadcast flag is worth an icon, but it is not the TiVo refusing the
      // transfer - jobMonitor and auto both stop dead on CopyProtected, so a show
      // the TiVo will still hand over must not carry it.
      for (String cgms : new String[]{"copyOnce", "copyNever"}) {
         Hashtable<String,String> entry = parseNPL.rpcToHashEntry("Bolt", recording(cgms, true));
         assertEquals("Yes", entry.get("CopyRestricted"), cgms);
         assertFalse(entry.containsKey("CopyProtected"), cgms);
      }
   }

   @Test
   void tivoToGoAloneStillMarksTheShow() throws Exception {
      // A show can be copyFreely off the air and still not transferable
      assertEquals("Yes", parseNPL.rpcToHashEntry("Bolt", recording("copyFreely", false)).get("CopyProtected"));
      // ...and a response carrying neither field leaves the show unmarked
      assertFalse(parseNPL.rpcToHashEntry("Bolt", recording(null, null)).containsKey("CopyProtected"));
   }

   @Test
   void everyCapturedRecordingAgreesWithItsDrmBlock() throws Exception {
      JSONArray myshows = Fixtures.load("myshows.json");
      assertTrue(myshows.length() > 0, "expected My Shows entries");

      for (int i = 0; i < myshows.length(); i++) {
         JSONObject json = myshows.getJSONObject(i).getJSONArray("recording").getJSONObject(0);
         JSONObject drm = json.has("drm") ? json.getJSONObject("drm") : new JSONObject();
         boolean cgms = drm.has("cgms") && ! drm.getString("cgms").equals("copyFreely");
         boolean blocked = drm.has("tivoToGo") && ! drm.getBoolean("tivoToGo");

         Hashtable<String,String> entry = parseNPL.rpcToHashEntry("Bolt", json);
         assertEquals(blocked, entry.containsKey("CopyProtected"),
            "CopyProtected disagrees with tivoToGo at row " + i);
         assertEquals(cgms, entry.containsKey("CopyRestricted"),
            "CopyRestricted disagrees with cgms at row " + i);
      }
   }
}
