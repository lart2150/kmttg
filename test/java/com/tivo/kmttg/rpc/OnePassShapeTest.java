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
 * Tests how a One Pass is created, edited and removed - the three commands
 * behind the Season Passes tab's Season Pass / Modify / Delete.
 *
 * Creating and editing are the same wire request, {@code subscribe}. What
 * separates them is a single {@code subscriptionId}: with it the TiVo edits the
 * existing pass, without it it adds a second one recording the same series. The
 * response is a subscription either way, so nothing downstream notices.
 *
 * Both take a whole row - a guide offer, or a pass read back off the TiVo - and
 * copy a whitelist of fields out of it into the request. Anything not on that
 * list is dropped, which is how a padding or keep setting goes missing without
 * an error anywhere.
 *
 * The recorded requests here are the objects the GUI handed {@code Command}, so
 * each one is both the test's input and half of what it checks.
 */
public class OnePassShapeTest {

   private static final String BODY_ID = Fixtures.BODY_ID;

   /** The recorded command of this type, as {type, request, response}. */
   private static JSONObject recorded(String type) throws Exception {
      JSONArray log = Fixtures.load("commands_onepass.json");
      for (int i = 0; i < log.length(); i++)
         if (log.getJSONObject(i).getString("type").equals(type))
            return log.getJSONObject(i);
      throw new IllegalStateException("no " + type + " in commands_onepass.json");
   }

   private static JSONObject caller(String type) throws Exception {
      // Command mutates what it is given, so hand it a copy.
      return new JSONObject(recorded(type).getJSONObject("request").toString());
   }

   private static ShapingRemote.Sent shape(String type, JSONObject json) throws Exception {
      ShapingRemote r = new ShapingRemote();
      r.Command(type, json);
      assertNotNull(r.last(), type + ": Command never built a request");
      return r.last();
   }

   // ---- creating ----------------------------------------------------------

   @Test
   public void seasonPassCopiesTheSettingsOffTheRow() throws Exception {
      JSONObject row = caller("Seasonpass");
      ShapingRemote.Sent sent = shape("Seasonpass", row);

      assertEquals("subscribe", sent.rpc);
      assertEquals(BODY_ID, sent.json.getString("bodyId"));
      assertEquals("true", sent.json.getString("ignoreConflicts"));
      // Every setting the viewer picked in the dialog rides on these.
      assertEquals(row.getString("showStatus"), sent.json.getString("showStatus"));
      assertEquals(row.getString("keepBehavior"), sent.json.getString("keepBehavior"));
      assertEquals(row.getInt("maxRecordings"), sent.json.getInt("maxRecordings"));
      assertEquals(row.getInt("startTimePadding"), sent.json.getInt("startTimePadding"));
      assertEquals(row.getInt("endTimePadding"), sent.json.getInt("endTimePadding"));
      assertSameJson(row.getJSONObject("idSetSource"), sent.json.getJSONObject("idSetSource"),
         "Seasonpass idSetSource");
      // No subscriptionId: this is a new pass, not an edit.
      assertFalse(sent.json.has("subscriptionId"), "a new pass must not carry a subscriptionId");
   }

   @Test
   public void seasonPassDropsTheTitleOnASeasonPassSource() throws Exception {
      // Deliberate: a title with special characters stops the TiVo scheduling
      // the pass, and it does not need one for this source type. The captured
      // row has both a title and a seasonPassSource, so it proves the rule ran.
      JSONObject row = caller("Seasonpass");
      assertEquals("seasonPassSource", row.getJSONObject("idSetSource").getString("type"));
      assertTrue(row.has("title"), "the recorded row should have a title to drop");

      assertFalse(shape("Seasonpass", row).json.has("title"),
         "title must not be sent for a seasonPassSource");
   }

   @Test
   public void seasonPassKeepsTheTitleForOtherSourceTypes() throws Exception {
      // A wishlist pass has no series behind it, so the title is what names it.
      JSONObject row = caller("Seasonpass");
      row.getJSONObject("idSetSource").put("type", "wishListSource");

      assertEquals(row.getString("title"), shape("Seasonpass", row).json.getString("title"),
         "a wishlist pass needs its title");
   }

   @Test
   public void seasonPassStripsAWishlistIdOffTheSource() throws Exception {
      // A pass saved to a file carries the id of the wishlist it came from; sent
      // back it refers to a wishlist on the old TiVo, not this one.
      JSONObject row = caller("Seasonpass");
      row.getJSONObject("idSetSource").put("wishlistId", "tivo:wl.12345");

      ShapingRemote.Sent sent = shape("Seasonpass", row);

      assertFalse(sent.json.getJSONObject("idSetSource").has("wishlistId"),
         "wishlistId should not be sent back");
   }

   @Test
   public void seasonPassSendsNothingTheRowDidNotHave() throws Exception {
      // Every field is copied under an if(has), so an absent setting has to stay
      // absent rather than arrive as a default the viewer never chose.
      JSONObject row = caller("Seasonpass");
      assertFalse(row.has("recordingQuality"), "the recorded row should not set a quality");
      assertFalse(row.has("hdOnly"), "the recorded row should not set hdOnly");

      ShapingRemote.Sent sent = shape("Seasonpass", row);

      assertFalse(sent.json.has("recordingQuality"), "recordingQuality invented");
      assertFalse(sent.json.has("hdOnly"), "hdOnly invented");
      assertFalse(sent.json.has("hdPreference"), "hdPreference invented");
   }

   // ---- editing -----------------------------------------------------------

   @Test
   public void modifyCopiesTheSettingsAndTheSubscriptionId() throws Exception {
      JSONObject row = caller("ModifySP");
      ShapingRemote.Sent sent = shape("ModifySP", row);

      assertEquals("subscribe", sent.rpc);
      assertEquals(BODY_ID, sent.json.getString("bodyId"));
      assertEquals("true", sent.json.getString("ignoreConflicts"));
      // Without this the TiVo adds a second pass instead of editing this one.
      assertEquals(row.getString("subscriptionId"), sent.json.getString("subscriptionId"),
         "the subscriptionId is the whole difference between editing and creating");
      assertEquals(row.getString("recordingQuality"), sent.json.getString("recordingQuality"));
      assertEquals(row.getInt("maxRecordings"), sent.json.getInt("maxRecordings"));
      assertEquals(row.getString("keepBehavior"), sent.json.getString("keepBehavior"));
      assertEquals(row.getString("showStatus"), sent.json.getString("showStatus"));
      assertEquals(row.getString("folderingRules"), sent.json.getString("folderingRules"));
      assertSameJson(row.getJSONObject("idSetSource"), sent.json.getJSONObject("idSetSource"),
         "ModifySP idSetSource");
   }

   @Test
   public void modifyKeepsTheTitleThatCreateWouldHaveDropped() throws Exception {
      // The two branches differ here, and both captured rows are the same pass
      // with the same seasonPassSource - so this is the asymmetry, not the data.
      JSONObject modifyRow = caller("ModifySP");
      assertEquals("seasonPassSource", modifyRow.getJSONObject("idSetSource").getString("type"));

      assertEquals(modifyRow.getString("title"), shape("ModifySP", modifyRow).json.getString("title"));
      assertFalse(shape("Seasonpass", caller("Seasonpass")).json.has("title"),
         "the create branch is the one that drops it");
   }

   // ---- removing ----------------------------------------------------------

   @Test
   public void unsubscribeSendsTheRecordedRequest() throws Exception {
      // A bare subscriptionId, not the array the recording writes use.
      JSONObject request = recorded("Unsubscribe").getJSONObject("request");
      assertTrue(request.get("subscriptionId") instanceof String,
         "expected the captured Unsubscribe to carry a bare subscriptionId");
      JSONObject row = new JSONObject();
      row.put("subscriptionId", request.getString("subscriptionId"));

      ShapingRemote.Sent sent = shape("Unsubscribe", row);

      assertEquals("unsubscribe", sent.rpc);
      assertSameJson(request, sent.json, "Unsubscribe");
   }

   @Test
   public void removingAndEditingActOnTheSamePass() throws Exception {
      // Sanity check on the fixture itself: the capture edits a pass and then
      // deletes it, so the two ids line up. If they ever stop, the tests above
      // are describing two different passes.
      assertEquals(recorded("ModifySP").getJSONObject("request").getString("subscriptionId"),
         recorded("Unsubscribe").getJSONObject("request").getString("subscriptionId"),
         "the captured edit and delete should be the same One Pass");
   }
}
