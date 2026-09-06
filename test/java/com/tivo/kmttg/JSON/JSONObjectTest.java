package com.tivo.kmttg.JSON;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests the JSONObject half of the bundled json.org parser.
 *
 * kmttg reads every TiVo RPC response through this class, so what matters is
 * not just "does it parse" but what it does with values that are missing, of
 * the wrong type, or non-finite - the accessors each have their own answer
 * (throw, return a default, coerce) and callers all over the gui depend on
 * which one they get. The kmttg copy also diverges from stock json.org in one
 * way visible here: JSONException is checked, so every accessor is a
 * compile-time decision point.
 */
public class JSONObjectTest {

   private static final String NESTED =
      "{\"title\":\"Cheers\",\"channel\":{\"channelNumber\":\"2-1\",\"callSign\":\"WBBMDT\"}," +
      "\"episodeNum\":[5],\"hd\":true,\"duration\":3600}";

   @Test
   public void parsesNestedObjectsAndArrays() throws Exception {
      JSONObject json = new JSONObject(NESTED);
      assertEquals("Cheers", json.getString("title"));
      assertEquals("2-1", json.getJSONObject("channel").getString("channelNumber"));
      assertEquals(5, json.getJSONArray("episodeNum").getInt(0));
      assertTrue(json.getBoolean("hd"));
      assertEquals(3600, json.getInt("duration"));
      assertEquals(5, json.length());
   }

   @Test
   public void reparsingItsOwnStringRoundTrips() throws Exception {
      JSONObject json = new JSONObject(NESTED);
      JSONObject again = new JSONObject(json.toString());
      assertEquals(json.length(), again.length());
      assertEquals(json.getString("title"), again.getString("title"));
      assertEquals(json.getJSONObject("channel").getString("callSign"),
                   again.getJSONObject("channel").getString("callSign"));
   }

   @Test
   public void gettersCoerceNumericStrings() throws Exception {
      JSONObject json = new JSONObject("{\"n\":\"42\",\"d\":\"1.5\",\"b\":\"TRUE\"}");
      assertEquals(42, json.getInt("n"));
      assertEquals(42L, json.getLong("n"));
      assertEquals(1.5, json.getDouble("d"), 0.0);
      assertTrue(json.getBoolean("b"));
   }

   @Test
   public void getOnMissingKeyThrowsNamingTheKey() {
      JSONObject json = new JSONObject();
      JSONException e = assertThrows(JSONException.class, () -> json.get("nope"));
      assertTrue(e.getMessage().contains("nope"), e.getMessage());
   }

   @Test
   public void gettersRejectTheWrongType() throws Exception {
      JSONObject json = new JSONObject("{\"s\":\"text\",\"o\":{},\"a\":[]}");
      assertThrows(JSONException.class, () -> json.getInt("s"));
      assertThrows(JSONException.class, () -> json.getLong("s"));
      assertThrows(JSONException.class, () -> json.getDouble("s"));
      assertThrows(JSONException.class, () -> json.getBoolean("s"));
      assertThrows(JSONException.class, () -> json.getString("o"));
      assertThrows(JSONException.class, () -> json.getJSONArray("o"));
      assertThrows(JSONException.class, () -> json.getJSONObject("a"));
   }

   @Test
   public void nullKeyIsRejected() {
      JSONObject json = new JSONObject();
      assertThrows(JSONException.class, () -> json.get(null));
      assertThrows(JSONException.class, () -> json.put(null, "v"));
      assertNull(json.opt(null));
   }

   @Test
   public void optReturnsDefaultsRatherThanThrowing() throws Exception {
      JSONObject json = new JSONObject("{\"s\":\"text\"}");
      assertEquals(0, json.optInt("missing"));
      assertEquals(7, json.optInt("s", 7));
      assertEquals(0L, json.optLong("missing"));
      assertEquals(9L, json.optLong("s", 9));
      assertTrue(Double.isNaN(json.optDouble("missing")));
      assertEquals(2.5, json.optDouble("s", 2.5), 0.0);
      assertFalse(json.optBoolean("missing"));
      assertTrue(json.optBoolean("s", true));
      assertNull(json.optJSONArray("s"));
      assertNull(json.optJSONObject("s"));
   }

   @Test
   public void optStringDefaultsToEmptyAndStringifiesNonStrings() throws Exception {
      JSONObject json = new JSONObject("{\"n\":42,\"nul\":null}");
      assertEquals("", json.optString("missing"));
      assertEquals("fallback", json.optString("missing", "fallback"));
      assertEquals("42", json.optString("n"));
      // An explicit JSON null reads back the same as a missing key
      assertEquals("", json.optString("nul"));
   }

   @Test
   public void jsonNullIsNotJavaNull() throws Exception {
      JSONObject json = new JSONObject("{\"nul\":null}");
      assertTrue(json.has("nul"));
      assertTrue(json.isNull("nul"));
      assertTrue(json.isNull("missing"));
      assertSame(JSONObject.NULL, json.get("nul"));
      assertTrue(JSONObject.NULL.equals(null));
      assertEquals("null", JSONObject.NULL.toString());
      assertEquals("{\"nul\":null}", json.toString());
   }

   @Test
   public void putOfJavaNullRemovesTheKey() throws Exception {
      JSONObject json = new JSONObject("{\"a\":1}");
      json.put("a", (Object)null);
      assertFalse(json.has("a"));
      assertEquals(0, json.length());
   }

   @Test
   public void removeReturnsThePreviousValue() throws Exception {
      JSONObject json = new JSONObject("{\"a\":1}");
      assertEquals(1, json.remove("a"));
      assertNull(json.remove("a"));
   }

   @Test
   public void putOnceRejectsADuplicateAndPutOptSkipsNulls() throws Exception {
      JSONObject json = new JSONObject();
      json.putOnce("a", "first");
      assertThrows(JSONException.class, () -> json.putOnce("a", "second"));
      assertEquals("first", json.getString("a"));

      json.putOnce(null, "ignored").putOnce("b", null);
      assertFalse(json.has("b"));

      json.putOpt("c", null).putOpt("d", "kept");
      assertFalse(json.has("c"));
      assertEquals("kept", json.getString("d"));
   }

   @Test
   public void duplicateKeyInSourceTextIsASyntaxError() {
      assertThrows(JSONException.class, () -> new JSONObject("{\"a\":1,\"a\":2}"));
   }

   @Test
   public void accumulateCollapsesRepeatsIntoAnArray() throws Exception {
      JSONObject json = new JSONObject();
      json.accumulate("k", "one");
      assertEquals("one", json.getString("k"));
      json.accumulate("k", "two");
      JSONArray a = json.getJSONArray("k");
      assertEquals(2, a.length());
      assertEquals("one", a.getString(0));
      assertEquals("two", a.getString(1));
      json.accumulate("k", "three");
      assertEquals(3, json.getJSONArray("k").length());
   }

   @Test
   public void accumulatingAnArrayFirstNestsIt() throws Exception {
      // The one asymmetry in accumulate: an array value is wrapped, not stored
      JSONObject json = new JSONObject();
      json.accumulate("k", new JSONArray().put(1));
      assertEquals(1, json.getJSONArray("k").length());
      assertEquals(1, json.getJSONArray("k").getJSONArray(0).getInt(0));
   }

   @Test
   public void appendAlwaysBuildsAnArrayAndRefusesAScalar() throws Exception {
      JSONObject json = new JSONObject();
      json.append("k", "one").append("k", "two");
      assertEquals(2, json.getJSONArray("k").length());

      json.put("scalar", 1);
      assertThrows(JSONException.class, () -> json.append("scalar", 2));
   }

   @Test
   public void incrementCreatesThenAdvancesNumericTypes() throws Exception {
      JSONObject json = new JSONObject();
      json.increment("count");
      assertEquals(1, json.getInt("count"));
      json.increment("count");
      assertEquals(2, json.getInt("count"));

      json.put("l", 5L).increment("l");
      assertEquals(6L, json.getLong("l"));
      json.put("d", 1.5).increment("d");
      assertEquals(2.5, json.getDouble("d"), 0.0);

      json.put("s", "text");
      assertThrows(JSONException.class, () -> json.increment("s"));
   }

   @Test
   public void nonFiniteNumbersAreRejectedEverywhere() {
      JSONObject json = new JSONObject();
      assertThrows(JSONException.class, () -> json.put("d", Double.NaN));
      assertThrows(JSONException.class, () -> json.put("d", Double.POSITIVE_INFINITY));
      assertThrows(JSONException.class, () -> JSONObject.testValidity(Float.valueOf(Float.NaN)));
      assertThrows(JSONException.class,
         () -> JSONObject.testValidity(Float.valueOf(Float.NEGATIVE_INFINITY)));
      assertThrows(JSONException.class, () -> JSONObject.numberToString(null));
   }

   @Test
   public void quoteEscapesWhatJsonForbids() {
      assertEquals("\"\"", JSONObject.quote(null));
      assertEquals("\"\"", JSONObject.quote(""));
      assertEquals("\"a\\\"b\"", JSONObject.quote("a\"b"));
      assertEquals("\"a\\\\b\"", JSONObject.quote("a\\b"));
      assertEquals("\"\\b\\t\\n\\f\\r\"", JSONObject.quote("\b\t\n\f\r"));
      // Only a slash that would close an HTML tag is escaped, so JSON can sit in a page
      assertEquals("\"<\\/script>\"", JSONObject.quote("</script>"));
      assertEquals("\"a/b\"", JSONObject.quote("a/b"));
      assertEquals("\"\\u0001\"", JSONObject.quote("\u0001"));
      assertEquals("\"\\u0085\"", JSONObject.quote("\u0085"));
      assertEquals("\"\\u2028\"", JSONObject.quote("\u2028"));
      assertEquals("\"\u00e9\"", JSONObject.quote("\u00e9"));
   }

   @Test
   public void stringToValueTypesBareTokens() {
      assertEquals("", JSONObject.stringToValue(""));
      assertEquals(Boolean.TRUE, JSONObject.stringToValue("TrUe"));
      assertEquals(Boolean.FALSE, JSONObject.stringToValue("false"));
      assertSame(JSONObject.NULL, JSONObject.stringToValue("NULL"));
      assertEquals(Integer.valueOf(42), JSONObject.stringToValue("42"));
      assertEquals(Integer.valueOf(-42), JSONObject.stringToValue("-42"));
      assertEquals(Double.valueOf(1.5), JSONObject.stringToValue("1.5"));
      // Anything that will not fit an int stays a Long
      assertEquals(Long.valueOf(9999999999L), JSONObject.stringToValue("9999999999"));
      // The non-standard hex form json.org accepts
      assertEquals(Integer.valueOf(31), JSONObject.stringToValue("0x1F"));
      assertEquals("12abc", JSONObject.stringToValue("12abc"));
      assertEquals("text", JSONObject.stringToValue("text"));
   }

   @Test
   public void numberFormattingTrimsTrailingZeros() throws Exception {
      assertEquals("2", JSONObject.doubleToString(2.0));
      assertEquals("1.5", JSONObject.doubleToString(1.50));
      assertEquals("null", JSONObject.doubleToString(Double.NaN));
      assertEquals("null", JSONObject.doubleToString(Double.POSITIVE_INFINITY));
      // Exponent forms are left alone - trimming zeros there would change the value
      assertEquals("1.0E10", JSONObject.doubleToString(1.0E10));
      assertEquals("5", JSONObject.numberToString(Integer.valueOf(5)));
      assertEquals("2", JSONObject.numberToString(Double.valueOf(2.0)));
   }

   @Test
   public void toleratesTheNonStandardSyntaxJsonOrgAccepts() throws Exception {
      assertEquals("v", new JSONObject("{key:'v'}").getString("key"));
      assertEquals(1, new JSONObject("{\"a\"=1}").getInt("a"));
      assertEquals(1, new JSONObject("{\"a\"=>1}").getInt("a"));
      assertEquals(2, new JSONObject("{\"a\":1;\"b\":2}").getInt("b"));
      // A trailing separator before the close brace is allowed
      assertEquals(1, new JSONObject("{\"a\":1,}").length());
      assertEquals(0, new JSONObject("{}").length());
   }

   @Test
   public void malformedTextIsASyntaxError() {
      assertThrows(JSONException.class, () -> new JSONObject("[1,2]"));
      assertThrows(JSONException.class, () -> new JSONObject("{\"a\" 1}"));
      assertThrows(JSONException.class, () -> new JSONObject("{\"a\":1 \"b\":2}"));
      assertThrows(JSONException.class, () -> new JSONObject("{\"a\":1"));
      assertThrows(JSONException.class, () -> new JSONObject("{\"a\":}"));
   }

   @Test
   public void prettyPrintingIndentsNestedLevels() throws Exception {
      assertEquals("{}", new JSONObject().toString(3));
      // A lone member stays on one line whatever the indent
      assertEquals("{\"a\": 1}", new JSONObject("{\"a\":1}").toString(3));

      String out = new JSONObject("{\"a\":1,\"b\":[1,2]}").toString(2);
      assertTrue(out.startsWith("{\n"), out);
      assertTrue(out.contains("\n  \""), out);
      assertTrue(out.endsWith("\n}"), out);
      assertEquals(2, new JSONObject(out).length());
   }

   @Test
   public void namesAndKeysExposeTheSameSet() throws Exception {
      JSONObject json = new JSONObject("{\"a\":1,\"b\":2}");
      assertEquals(2, json.names().length());
      assertEquals(2, JSONObject.getNames(json).length);
      assertNull(new JSONObject().names());
      assertNull(JSONObject.getNames(new JSONObject()));
      assertNull(JSONObject.getNames((Object)null));

      JSONArray values = json.toJSONArray(new JSONArray().put("b").put("a"));
      assertEquals(2, values.getInt(0));
      assertEquals(1, values.getInt(1));
      assertNull(json.toJSONArray(new JSONArray()));
      assertNull(json.toJSONArray(null));
   }

   @Test
   public void subsetConstructorCopiesOnlyTheNamedKeys() throws Exception {
      JSONObject json = new JSONObject("{\"a\":1,\"b\":2,\"c\":3}");
      JSONObject subset = new JSONObject(json, new String[] {"a", "c", "missing"});
      assertEquals(2, subset.length());
      assertEquals(1, subset.getInt("a"));
      assertEquals(3, subset.getInt("c"));
   }

   @Test
   public void mapConstructorWrapsNestedContainers() throws Exception {
      Map<String,Object> map = new HashMap<String,Object>();
      map.put("list", Arrays.asList(1, 2));
      map.put("skipped", null);
      Map<String,Object> inner = new HashMap<String,Object>();
      inner.put("x", "y");
      map.put("inner", inner);

      JSONObject json = new JSONObject(map);
      assertEquals(2, json.length());
      assertEquals(2, json.getJSONArray("list").length());
      assertEquals("y", json.getJSONObject("inner").getString("x"));
      assertEquals(0, new JSONObject((Map<?,?>)null).length());
   }

   @Test
   public void wrapNormalizesJavaValues() throws Exception {
      assertSame(JSONObject.NULL, JSONObject.wrap(null));
      assertEquals("s", JSONObject.wrap("s"));
      assertEquals(Integer.valueOf(1), JSONObject.wrap(Integer.valueOf(1)));
      assertTrue(JSONObject.wrap(new int[] {1, 2}) instanceof JSONArray);
      assertTrue(JSONObject.wrap(new ArrayList<String>()) instanceof JSONArray);
      assertTrue(JSONObject.wrap(new HashMap<String,String>()) instanceof JSONObject);
      // Anything out of the jdk is flattened to its toString
      assertTrue(JSONObject.wrap(new Date(0)) instanceof String);
   }

   @Test
   public void beanConstructorReadsGetters() throws Exception {
      JSONObject json = new JSONObject(new Show("Cheers", 5, true));
      assertEquals("Cheers", json.getString("title"));
      assertEquals(5, json.getInt("episode"));
      assertTrue(json.getBoolean("hd"));
      assertFalse(json.has("class"));
   }

   @Test
   public void namedFieldConstructorReadsPublicFields() throws Exception {
      JSONObject json = new JSONObject(new Fields(), new String[] {"visible", "hidden", "absent"});
      assertEquals(1, json.length());
      assertEquals("here", json.getString("visible"));
   }

   @Test
   public void valueToStringHonoursJsonStringImplementers() throws Exception {
      assertEquals("null", JSONObject.valueToString(null));
      assertEquals("null", JSONObject.valueToString(JSONObject.NULL));
      assertEquals("3", JSONObject.valueToString(Integer.valueOf(3)));
      assertEquals("true", JSONObject.valueToString(Boolean.TRUE));
      assertEquals("\"s\"", JSONObject.valueToString("s"));
      assertEquals("[1,2]", JSONObject.valueToString(new int[] {1, 2}));
      assertEquals("[1,2]", JSONObject.valueToString(Arrays.asList(1, 2)));
      assertEquals("{\"raw\":1}", JSONObject.valueToString(new Raw("{\"raw\":1}")));
      assertThrows(JSONException.class, () -> JSONObject.valueToString(new Raw(null)));
   }

   @Test
   public void writeEmitsTheCompactFormThroughAWriter() throws Exception {
      JSONObject json = new JSONObject("{\"a\":{\"b\":[1,2]}}");
      StringWriter out = new StringWriter();
      json.write(out);
      assertEquals("{\"a\":{\"b\":[1,2]}}", out.toString());
      assertEquals(json.toString(), out.toString());
   }

   @Test
   public void aBrokenWriterComesBackAsAJsonExceptionCarryingTheCause() throws Exception {
      // The web server writes JSON straight at a socket, so the IOException
      // from a client that hung up has to arrive wrapped, not raw
      JSONObject json = new JSONObject("{\"a\":1}");
      IOException cause = new IOException("hung up");

      JSONException e = assertThrows(JSONException.class, () -> json.write(new Broken(cause)));
      assertSame(cause, e.getCause());
      assertEquals("hung up", e.getMessage());

      JSONArray array = new JSONArray("[1]");
      assertSame(cause,
         assertThrows(JSONException.class, () -> array.write(new Broken(cause))).getCause());
   }

   /** A writer that fails the way a dropped connection does. */
   private static class Broken extends Writer {
      private final IOException failure;
      Broken(IOException failure) { this.failure = failure; }

      public void write(char[] buf, int off, int len) throws IOException { throw failure; }
      public void flush() throws IOException { throw failure; }
      public void close() throws IOException { throw failure; }
   }

   /** Bean for the getter-reflecting constructor. */
   public static class Show {
      private final String title;
      private final int episode;
      private final boolean hd;

      Show(String title, int episode, boolean hd) {
         this.title = title;
         this.episode = episode;
         this.hd = hd;
      }

      public String getTitle() { return title; }
      public int getEpisode() { return episode; }
      public boolean isHd() { return hd; }
   }

   /** Only public fields are reachable by the names[] constructor. */
   public static class Fields {
      public String visible = "here";
      @SuppressWarnings("unused")
      private String hidden = "gone";
   }

   /** A value that serializes itself, well or badly. */
   private static class Raw implements JSONString {
      private final String text;
      Raw(String text) { this.text = text; }
      public String toJSONString() { return text; }
   }
}
