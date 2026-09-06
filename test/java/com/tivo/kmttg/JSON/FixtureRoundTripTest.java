package com.tivo.kmttg.JSON;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.util.Iterator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.tivo.kmttg.rpc.Fixtures;

/**
 * Drives the parser over the captured TiVo responses rather than hand written
 * snippets.
 *
 * The hand written tests cover each method; these cover the payloads kmttg
 * actually sees - a few thousand values deep in nesting, with the escapes and
 * accented titles a real channel lineup carries. Serialize/reparse is asserted
 * to be idempotent, which catches a quoting bug in either direction: anything
 * the writer emits that the reader then reads differently shows up as a
 * mismatch on the second pass.
 */
public class FixtureRoundTripTest {

   @ParameterizedTest
   @ValueSource(strings = {
      "todo.json", "myshows.json", "seasonpasses.json", "channels.json",
      "cancelled.json", "deleted.json", "search.json", "thumbs.json",
      "guide_2-1.json"
   })
   public void serializingAndReparsingIsIdempotent(String fixture) throws Exception {
      JSONArray json = Fixtures.load(fixture);
      assertTrue(json.length() > 0, fixture + " is empty");

      String once = json.toString();
      String twice = new JSONArray(once).toString();
      assertEquals(once, twice, fixture + " does not survive a reparse");
   }

   @ParameterizedTest
   @ValueSource(strings = {"todo.json", "channels.json", "guide_2-1.json"})
   public void prettyPrintingReparsesToTheSameData(String fixture) throws Exception {
      JSONArray json = Fixtures.load(fixture);
      String indented = json.toString(3);
      assertTrue(indented.contains("\n"), fixture + " was not indented");
      assertEquals(json.toString(), new JSONArray(indented).toString());
   }

   @ParameterizedTest
   @ValueSource(strings = {"todo.json", "myshows.json", "channels.json"})
   public void theWriterAgreesWithToString(String fixture) throws Exception {
      JSONArray json = Fixtures.load(fixture);
      StringWriter out = new StringWriter();
      json.write(out);
      assertEquals(json.toString(), out.toString());
   }

   @Test
   public void accentedTitlesSurviveTheRoundTrip() throws Exception {
      JSONArray json = Fixtures.load("cancelled.json");
      String text = json.toString();
      // The capture carries non-ASCII, and quote() only escapes the control and
      // separator ranges, so it has to come back through as itself
      assertTrue(nonAscii(text), "fixture no longer carries non-ASCII");
      assertTrue(nonAscii(new JSONArray(text).toString()));
   }

   @Test
   public void everyParsedValueIsAJsonType() throws Exception {
      // A raw Java null anywhere in the tree would mean the parser dropped
      // something on the floor; JSON null has to arrive as JSONObject.NULL
      for (String fixture : new String[] {"todo.json", "myshows.json", "channels.json"}) {
         JSONArray json = Fixtures.load(fixture);
         for (int i = 0; i < json.length(); ++i)
            checkTypes(json.get(i), fixture + "[" + i + "]");
      }
   }

   @Test
   public void knownKeysReadBackWithTheirTypes() throws Exception {
      // A spot check that the values are typed, not just present - kmttg calls
      // getString/getInt on exactly these
      JSONArray todo = Fixtures.load("todo.json");
      JSONObject first = todo.getJSONObject(0);
      assertNotNull(first.getString("title"));
      assertTrue(first.getInt("duration") > 0);
      assertFalse(first.getString("startTime").isEmpty());

      JSONArray channels = Fixtures.load("channels.json");
      JSONObject channel = channels.getJSONObject(0);
      assertFalse(channel.getString("channelNumber").isEmpty());
      assertFalse(channel.getString("callSign").isEmpty());
   }

   private static void checkTypes(Object value, String where) {
      assertNotNull(value, where + " parsed to a java null");
      if (value instanceof JSONObject) {
         JSONObject json = (JSONObject)value;
         for (Iterator<?> it = json.keys(); it.hasNext(); ) {
            String key = (String)it.next();
            checkTypes(json.opt(key), where + "." + key);
         }
      } else if (value instanceof JSONArray) {
         JSONArray array = (JSONArray)value;
         for (int i = 0; i < array.length(); ++i)
            checkTypes(array.opt(i), where + "[" + i + "]");
      } else {
         assertTrue(value instanceof String || value instanceof Number ||
                    value instanceof Boolean || value == JSONObject.NULL,
            where + " parsed to an unexpected " + value.getClass().getName());
      }
   }

   private static boolean nonAscii(String text) {
      for (int i = 0; i < text.length(); ++i)
         if (text.charAt(i) > 127)
            return true;
      return false;
   }
}
