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
 * Tests the six commands one Info tab Refresh and one Network Connect issue.
 *
 * They look interchangeable - each is a bare request with a bodyId - but three
 * details are not, and none of them show up in the response:
 *
 * <ul>
 * <li>{@code TunerInfo} is the one command here that must NOT carry a bodyId.
 * <li>{@code TunerInfo} and {@code WhatsOn} are registrations, not searches:
 *     they go out with monitor=true, which is "ResponseCount: multiple" in the
 *     RPC headers. Sent as single-response the reply is cut short.
 *<li>{@code PhoneHome} is the only command whose wire request depends on how
 *     kmttg is connected - phoneHomeRequest locally, phoneHomeSend in away mode.
 * </ul>
 *
 * Expected requests come from a capture, so they are what a TiVo answered.
 */
public class InfoCommandShapeTest {

   private static final String BODY_ID = Fixtures.BODY_ID;

   /** The first recorded command of this type, as {type, request, response}. */
   private static JSONObject recorded(String type) throws Exception {
      JSONArray log = Fixtures.load("commands_info.json");
      for (int i = 0; i < log.length(); i++)
         if (log.getJSONObject(i).getString("type").equals(type))
            return log.getJSONObject(i);
      throw new IllegalStateException("no " + type + " in commands_info.json");
   }

   /**
    * Issue a command the way the Info tab does and report what went out. Most
    * of these are handed an empty object and get their bodyId from Command;
    * the two that fall through Command's chain unchanged are handed one, since
    * that is what info.java does.
    */
   private static ShapingRemote.Sent shape(String type) throws Exception {
      JSONObject json = new JSONObject();
      if (type.equals("systemInformationGet") || type.equals("phoneHomeStatusEventRegister"))
         json.put("bodyId", BODY_ID);
      ShapingRemote r = new ShapingRemote();
      r.Command(type, json);
      assertNotNull(r.last(), type + ": Command never built a request");
      return r.last();
   }

   @Test
   public void eachInfoCommandKeepsItsOwnWireRequest() throws Exception {
      // Transposing any two of these silently returns the wrong panel's data.
      String[][] expected = {
         { "SysInfo",              "bodyConfigSearch" },
         { "systemInformationGet", "systemInformationGet" },
         { "WhatsOn",              "whatsOnSearch" },
         { "TunerInfo",            "tunerStateEventRegister" },
         { "PhoneHome",            "phoneHomeRequest" },
         { "PhoneHomeStatus",      "phoneHomeStatusEventRegister" },
      };
      for (String[] pair : expected)
         assertEquals(pair[1], shape(pair[0]).rpc, "wrong wire request for " + pair[0]);
   }

   @Test
   public void everyInfoRequestMatchesTheRecordedOne() throws Exception {
      // systemInformationGet and phoneHomeStatusEventRegister fall through
      // Command's chain unchanged; the rest are named branches. Either way the
      // bytes have to be the ones the TiVo answered.
      String[] types = { "SysInfo", "systemInformationGet", "WhatsOn", "TunerInfo",
                         "PhoneHome", "phoneHomeStatusEventRegister" };
      for (String type : types) {
         JSONObject request = recorded(type).getJSONObject("request");
         ShapingRemote.Sent sent = shape(type);

         assertEquals(request.getString("type"), sent.rpc, type + ": wrong wire request");
         assertSameJson(request, sent.json, type);
      }
   }

   @Test
   public void tunerInfoIsTheOneWithoutABodyId() throws Exception {
      // Captured proof rather than a guess: the recorded tunerStateEventRegister
      // carries nothing but its type, while its four neighbours all send a body.
      assertFalse(recorded("TunerInfo").getJSONObject("request").has("bodyId"),
         "the captured TunerInfo should have gone out without a bodyId");
      assertFalse(shape("TunerInfo").json.has("bodyId"), "TunerInfo should not send a bodyId");

      for (String type : new String[] { "SysInfo", "WhatsOn", "PhoneHome", "PhoneHomeStatus" })
         assertEquals(BODY_ID, shape(type).json.getString("bodyId"), type + " should send a bodyId");
   }

   @Test
   public void onlyTheEventRegistrationsAskForMultipleResponses() throws Exception {
      assertTrue(shape("TunerInfo").monitor, "tunerStateEventRegister streams tuner states");
      assertTrue(shape("WhatsOn").monitor, "whatsOnSearch streams what is playing");

      for (String type : new String[] { "SysInfo", "systemInformationGet", "PhoneHome", "PhoneHomeStatus" })
         assertFalse(shape(type).monitor, type + " is a single-response search");
   }

   @Test
   public void phoneHomeStatusIsTheProgressPollBehindNetworkConnect() throws Exception {
      // The Info tab sends the raw request with a bodyId of its own; the web UI
      // has no bodyId to send, so it goes through the named PhoneHomeStatus
      // branch which fills one in. Both have to reach the same wire request.
      assertEquals("phoneHomeStatusEventRegister", shape("PhoneHomeStatus").rpc);
      assertEquals(BODY_ID, shape("PhoneHomeStatus").json.getString("bodyId"));
      assertSameJson(shape("phoneHomeStatusEventRegister").json, shape("PhoneHomeStatus").json,
         "PhoneHomeStatus vs the raw poll");

      // The recorded poll ran to completion, which is what the panel waits for.
      JSONArray log = Fixtures.load("commands_info.json");
      String last = null;
      int polls = 0;
      for (int i = 0; i < log.length(); i++) {
         JSONObject rec = log.getJSONObject(i);
         if (! rec.getString("type").equals("phoneHomeStatusEventRegister"))
            continue;
         polls++;
         last = rec.getJSONObject("response").getString("status");
      }
      assertTrue(polls > 1, "expected a repeated poll, saw " + polls);
      assertEquals("succeeded", last, "the captured connect should have finished");
   }
}
