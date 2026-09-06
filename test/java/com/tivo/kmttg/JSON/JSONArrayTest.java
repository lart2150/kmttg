package com.tivo.kmttg.JSON;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests the JSONArray half of the bundled json.org parser.
 *
 * Every TiVo RPC list (todo, myshows, season passes) arrives as one of these,
 * and kmttg walks them by index, so the out-of-range and wrong-type paths are
 * as load bearing as the happy one. Two behaviours here differ from the
 * JSONObject side and are pinned deliberately: optString hands back the string
 * "null" for an explicit JSON null where JSONObject hands back the default,
 * and put(index) pads the gap with JSON nulls instead of failing.
 */
public class JSONArrayTest {

   @Test
   public void parsesMixedElementTypes() throws Exception {
      JSONArray a = new JSONArray("[1,\"two\",true,null,[3],{\"k\":\"v\"}]");
      assertEquals(6, a.length());
      assertEquals(1, a.getInt(0));
      assertEquals("two", a.getString(1));
      assertTrue(a.getBoolean(2));
      assertTrue(a.isNull(3));
      assertEquals(3, a.getJSONArray(4).getInt(0));
      assertEquals("v", a.getJSONObject(5).getString("k"));
   }

   @Test
   public void reparsingItsOwnStringRoundTrips() throws Exception {
      JSONArray a = new JSONArray("[1,\"two\",[3],{\"k\":\"v\"}]");
      JSONArray again = new JSONArray(a.toString());
      assertEquals(a.toString(), again.toString());
   }

   @Test
   public void gettersCoerceNumericStrings() throws Exception {
      JSONArray a = new JSONArray("[\"42\",\"1.5\",\"true\"]");
      assertEquals(42, a.getInt(0));
      assertEquals(42L, a.getLong(0));
      assertEquals(1.5, a.getDouble(1), 0.0);
      assertTrue(a.getBoolean(2));
   }

   @Test
   public void outOfRangeAndWrongTypeThrow() throws Exception {
      JSONArray a = new JSONArray("[\"text\",{},[]]");
      assertThrows(JSONException.class, () -> a.get(-1));
      assertThrows(JSONException.class, () -> a.get(3));
      assertThrows(JSONException.class, () -> a.getInt(0));
      assertThrows(JSONException.class, () -> a.getLong(0));
      assertThrows(JSONException.class, () -> a.getDouble(0));
      assertThrows(JSONException.class, () -> a.getBoolean(0));
      assertThrows(JSONException.class, () -> a.getString(1));
      assertThrows(JSONException.class, () -> a.getJSONArray(1));
      assertThrows(JSONException.class, () -> a.getJSONObject(2));
   }

   @Test
   public void optReturnsDefaultsRatherThanThrowing() throws Exception {
      JSONArray a = new JSONArray("[\"text\"]");
      assertNull(a.opt(-1));
      assertNull(a.opt(5));
      assertEquals(0, a.optInt(5));
      assertEquals(7, a.optInt(0, 7));
      assertEquals(0L, a.optLong(5));
      assertEquals(9L, a.optLong(0, 9));
      assertTrue(Double.isNaN(a.optDouble(5)));
      assertEquals(2.5, a.optDouble(0, 2.5), 0.0);
      assertFalse(a.optBoolean(5));
      assertTrue(a.optBoolean(0, true));
      assertNull(a.optJSONArray(0));
      assertNull(a.optJSONObject(0));
   }

   @Test
   public void optStringStringifiesAnExplicitNull() throws Exception {
      JSONArray a = new JSONArray("[42,null]");
      assertEquals("42", a.optString(0));
      assertEquals("", a.optString(5));
      assertEquals("fallback", a.optString(5, "fallback"));
      // Unlike JSONObject.optString, a stored JSON null renders as "null" here
      assertEquals("null", a.optString(1));
      assertTrue(a.isNull(1));
      assertTrue(a.isNull(9));
   }

   @Test
   public void putAtIndexPadsTheGapWithNulls() throws Exception {
      JSONArray a = new JSONArray();
      a.put(3, "fourth");
      assertEquals(4, a.length());
      assertTrue(a.isNull(0));
      assertTrue(a.isNull(2));
      assertEquals("fourth", a.getString(3));

      a.put(0, "first");
      assertEquals(4, a.length());
      assertEquals("first", a.getString(0));

      assertThrows(JSONException.class, () -> a.put(-1, "nope"));
   }

   @Test
   public void putAppendsEveryPrimitiveType() throws Exception {
      JSONArray a = new JSONArray();
      a.put(true).put(1).put(2L).put(1.5).put("s").put(JSONObject.NULL);
      assertEquals(6, a.length());
      assertTrue(a.getBoolean(0));
      assertEquals(1, a.getInt(1));
      assertEquals(2L, a.getLong(2));
      assertEquals(1.5, a.getDouble(3), 0.0);
      assertEquals("s", a.getString(4));
      assertTrue(a.isNull(5));
      assertThrows(JSONException.class, () -> a.put(Double.NaN));
   }

   @Test
   public void putAtIndexTakesTheSameTypes() throws Exception {
      JSONArray a = new JSONArray("[0,0,0,0,0]");
      a.put(0, true).put(1, 7).put(2, 8L).put(3, 1.5).put(4, (Object)"s");
      assertTrue(a.getBoolean(0));
      assertEquals(7, a.getInt(1));
      assertEquals(8L, a.getLong(2));
      assertEquals(1.5, a.getDouble(3), 0.0);
      assertEquals("s", a.getString(4));
      assertThrows(JSONException.class, () -> a.put(0, Double.NaN));
   }

   @Test
   public void collectionsAndMapsBecomeNestedContainers() throws Exception {
      JSONArray a = new JSONArray();
      Map<String,Object> map = new HashMap<String,Object>();
      map.put("k", "v");
      a.put(Arrays.asList(1, 2)).put(map);
      assertEquals(2, a.getJSONArray(0).length());
      assertEquals("v", a.getJSONObject(1).getString("k"));

      a.put(0, Arrays.asList(1, 2, 3)).put(1, map);
      assertEquals(3, a.getJSONArray(0).length());
      assertEquals("v", a.getJSONObject(1).getString("k"));
   }

   @Test
   public void constructedFromCollectionsAndJavaArrays() throws Exception {
      assertEquals(2, new JSONArray(Arrays.asList("a", "b")).length());
      assertEquals(0, new JSONArray((java.util.Collection<?>)null).length());
      assertEquals(3, new JSONArray(new int[] {1, 2, 3}).getInt(2));
      assertEquals("b", new JSONArray(new String[] {"a", "b"}).getString(1));
      assertThrows(JSONException.class, () -> new JSONArray((Object)"not an array"));
   }

   @Test
   public void removeClosesTheHole() throws Exception {
      JSONArray a = new JSONArray("[1,2,3]");
      assertEquals(2, a.remove(1));
      assertEquals(2, a.length());
      assertEquals(3, a.getInt(1));
   }

   @Test
   public void joinRendersElementsAsJsonSeparatedByTheGivenText() throws Exception {
      assertEquals("1,\"two\",true", new JSONArray("[1,\"two\",true]").join(","));
      assertEquals("1 | 2", new JSONArray("[1,2]").join(" | "));
      assertEquals("", new JSONArray().join(","));
   }

   @Test
   public void toJSONObjectPairsNamesWithValues() throws Exception {
      JSONArray values = new JSONArray("[1,2]");
      JSONObject json = values.toJSONObject(new JSONArray().put("a").put("b"));
      assertEquals(1, json.getInt("a"));
      assertEquals(2, json.getInt("b"));

      assertNull(values.toJSONObject(null));
      assertNull(values.toJSONObject(new JSONArray()));
      assertNull(new JSONArray().toJSONObject(new JSONArray().put("a")));

      // More names than values leaves the extra keys off entirely
      JSONObject sparse = values.toJSONObject(new JSONArray().put("a").put("b").put("c"));
      assertEquals(2, sparse.length());
   }

   @Test
   public void toleratesTheNonStandardSyntaxJsonOrgAccepts() throws Exception {
      assertEquals(0, new JSONArray("[]").length());
      assertEquals("v", new JSONArray("['v']").getString(0));
      assertEquals(2, new JSONArray("[1;2]").getInt(1));
      // A missing element between commas becomes a JSON null
      JSONArray holes = new JSONArray("[1,,3]");
      assertEquals(3, holes.length());
      assertTrue(holes.isNull(1));
      // A trailing separator before the close bracket is allowed
      assertEquals(1, new JSONArray("[1,]").length());
   }

   @Test
   public void malformedTextIsASyntaxError() {
      assertThrows(JSONException.class, () -> new JSONArray("{\"a\":1}"));
      assertThrows(JSONException.class, () -> new JSONArray("[1,2}"));
      assertThrows(JSONException.class, () -> new JSONArray("[1,2"));
   }

   @Test
   public void prettyPrintingIndentsEachElement() throws Exception {
      assertEquals("[]", new JSONArray().toString(3));
      // A lone element stays on one line whatever the indent
      assertEquals("[1]", new JSONArray("[1]").toString(3));

      String out = new JSONArray("[1,{\"a\":1,\"b\":2}]").toString(2);
      assertTrue(out.startsWith("[\n"), out);
      assertTrue(out.endsWith("\n]"), out);
      assertEquals(2, new JSONArray(out).length());
   }

   @Test
   public void writeEmitsTheCompactFormThroughAWriter() throws Exception {
      JSONArray a = new JSONArray("[1,{\"k\":[2]},\"s\"]");
      StringWriter out = new StringWriter();
      a.write(out);
      assertEquals("[1,{\"k\":[2]},\"s\"]", out.toString());
      assertEquals(a.toString(), out.toString());
   }

   @Test
   public void nullSurvivesTheRoundTripAsJsonNull() throws Exception {
      JSONArray a = new JSONArray().put(JSONObject.NULL);
      assertEquals("[null]", a.toString());
      assertSame(JSONObject.NULL, new JSONArray("[null]").get(0));
   }
}
