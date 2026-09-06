package com.tivo.kmttg.JSON;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests the XML/JSON bridge and the XMLTokener under it.
 *
 * kmttg runs the old Mind (HME) queries and pyTivo's TVBusQuery through
 * XML.toJSONObject and then reaches straight into the result by key
 * (Mind.java asks for "recordingList", pyTivo for "TvBusMarshalledStruct:
 * TvBusEnvelope"), so the shape this produces - when a repeated element turns
 * into an array, when a lone element collapses to a bare value, where text
 * content lands - is what those callers are compiled against.
 */
public class XMLTest {

   private static final String TVBUS =
      "<?xml version=\"1.0\"?>\n" +
      "<TvBusMarshalledStruct:TvBusEnvelope>\n" +
      "  <showing>\n" +
      "    <duration>3600</duration>\n" +
      "    <program>\n" +
      "      <title>Cheers</title>\n" +
      "      <episodeNumber>5</episodeNumber>\n" +
      "    </program>\n" +
      "  </showing>\n" +
      "</TvBusMarshalledStruct:TvBusEnvelope>";

   @Test
   public void parsesTheNestingPyTivoReturns() throws Exception {
      JSONObject json = XML.toJSONObject(TVBUS);
      JSONObject envelope = json.getJSONObject("TvBusMarshalledStruct:TvBusEnvelope");
      JSONObject showing = envelope.getJSONObject("showing");
      assertEquals(3600, showing.getInt("duration"));
      assertEquals("Cheers", showing.getJSONObject("program").getString("title"));
      assertEquals(5, showing.getJSONObject("program").getInt("episodeNumber"));
   }

   @Test
   public void aLoneElementCollapsesToItsValue() throws Exception {
      // An element with nothing but text becomes the text, not an object
      JSONObject json = XML.toJSONObject("<a><b>text</b></a>");
      assertEquals("text", json.getJSONObject("a").getString("b"));
   }

   @Test
   public void repeatedElementsBecomeAnArray() throws Exception {
      JSONObject json = XML.toJSONObject("<list><item>1</item><item>2</item></list>");
      JSONArray items = json.getJSONObject("list").getJSONArray("item");
      assertEquals(2, items.length());
      assertEquals(1, items.getInt(0));
      assertEquals(2, items.getInt(1));

      // ...but a single occurrence stays scalar, which is why Mind.java has to
      // test the type before reading it
      JSONObject one = XML.toJSONObject("<list><item>1</item></list>");
      assertEquals(1, one.getJSONObject("list").getInt("item"));
   }

   @Test
   public void attributesBecomeKeysAlongsideChildren() throws Exception {
      JSONObject json = XML.toJSONObject("<a id=\"7\" flag><b>x</b></a>");
      JSONObject a = json.getJSONObject("a");
      assertEquals(7, a.getInt("id"));
      assertEquals("", a.getString("flag"));
      assertEquals("x", a.getString("b"));
   }

   @Test
   public void textBesideChildrenLandsUnderContent() throws Exception {
      JSONObject json = XML.toJSONObject("<a>lead<b>x</b></a>");
      JSONObject a = json.getJSONObject("a");
      assertEquals("lead", a.getString("content"));
      assertEquals("x", a.getString("b"));
   }

   @Test
   public void emptyElementsBecomeEmptyStrings() throws Exception {
      JSONObject json = XML.toJSONObject("<a><b/><c></c><d attr=\"1\"/></a>");
      JSONObject a = json.getJSONObject("a");
      assertEquals("", a.getString("b"));
      assertEquals("", a.getString("c"));
      // An empty tag that carries attributes keeps them
      assertEquals(1, a.getJSONObject("d").getInt("attr"));
   }

   @Test
   public void entitiesAreDecoded() throws Exception {
      JSONObject json = XML.toJSONObject("<a>Tom &amp; Jerry &lt;b&gt; &quot;q&quot; &apos;s&apos;</a>");
      assertEquals("Tom & Jerry <b> \"q\" 's'", json.getString("a"));
      // An entity the table does not know is passed through verbatim
      assertEquals("&nbsp;", XML.toJSONObject("<a>&nbsp;</a>").getString("a"));
   }

   @Test
   public void prologsCommentsAndCdataAreHandled() throws Exception {
      JSONObject json = XML.toJSONObject(
         "<?xml version=\"1.0\"?><!DOCTYPE a><a><!-- ignored --><![CDATA[<raw> & text]]></a>");
      // CDATA lands under "content", which then collapses because it is the
      // only thing the element holds
      assertEquals("<raw> & text", json.getString("a"));
   }

   @Test
   public void malformedMarkupIsASyntaxError() {
      assertThrows(JSONException.class, () -> XML.toJSONObject("<a>"));
      assertThrows(JSONException.class, () -> XML.toJSONObject("<a></b>"));
      assertThrows(JSONException.class, () -> XML.toJSONObject("</a>"));
      assertThrows(JSONException.class, () -> XML.toJSONObject("<a id=></a>"));
      assertThrows(JSONException.class, () -> XML.toJSONObject("<a><![CDATA nope]]></a>"));
   }

   @Test
   public void nothingOutsideATagYieldsAnEmptyObject() throws Exception {
      assertEquals(0, XML.toJSONObject("").length());
      assertEquals(0, XML.toJSONObject("just text").length());
   }

   @Test
   public void escapeCoversTheFiveMarkupCharacters() {
      assertEquals("&amp;&lt;&gt;&quot;&apos;", XML.escape("&<>\"'"));
      assertEquals("plain", XML.escape("plain"));
      assertEquals("", XML.escape(""));
   }

   @Test
   public void noSpaceRejectsWhitespaceAndEmptyNames() throws Exception {
      XML.noSpace("tagName");
      assertThrows(JSONException.class, () -> XML.noSpace(""));
      assertThrows(JSONException.class, () -> XML.noSpace("two words"));
      assertThrows(JSONException.class, () -> XML.noSpace("tab\there"));
   }

   @Test
   public void stringToValueIsStricterThanTheJsonOne() {
      assertEquals("", XML.stringToValue(""));
      assertEquals(Boolean.TRUE, XML.stringToValue("TRUE"));
      assertEquals(Boolean.FALSE, XML.stringToValue("false"));
      assertSame(JSONObject.NULL, XML.stringToValue("null"));
      assertEquals(Integer.valueOf(0), XML.stringToValue("0"));
      assertEquals(Integer.valueOf(42), XML.stringToValue("42"));
      assertEquals(Long.valueOf(9999999999L), XML.stringToValue("9999999999"));
      assertEquals(Double.valueOf(1.5), XML.stringToValue("1.5"));
      // Leading zeros mark an id, not a number - a channel or station id must
      // not lose them on the way through
      assertEquals("007", XML.stringToValue("007"));
      // and unlike JSONObject.stringToValue, hex, plus and bare exponent forms
      // stay strings
      assertEquals("0x1F", XML.stringToValue("0x1F"));
      assertEquals("+42", XML.stringToValue("+42"));
      assertEquals("1e5", XML.stringToValue("1e5"));
      assertEquals("text", XML.stringToValue("text"));
   }

   @Test
   public void toStringRendersAJsonObjectBackAsMarkup() throws Exception {
      assertEquals("<a>1</a>", XML.toString(new JSONObject("{\"a\":1}")));
      assertEquals("<r><a>1</a></r>", XML.toString(new JSONObject("{\"a\":1}"), "r"));
      assertEquals("<a/>", XML.toString(new JSONObject("{\"a\":\"\"}")));
      assertEquals("<a>x</a><a>y</a>",
         XML.toString(new JSONObject("{\"a\":[\"x\",\"y\"]}")));
      // "content" is written as the body of the enclosing tag
      assertEquals("<r>body</r>", XML.toString(new JSONObject("{\"content\":\"body\"}"), "r"));
      assertEquals("<a>&amp;</a>", XML.toString(new JSONObject("{\"a\":\"&\"}")));
   }

   @Test
   public void toStringOfBareValuesAndArrays() throws Exception {
      assertEquals("\"7\"", XML.toString(Integer.valueOf(7)));
      assertEquals("<k>7</k>", XML.toString(Integer.valueOf(7), "k"));
      assertEquals("<array>1</array><array>2</array>", XML.toString(new JSONArray("[1,2]")));
      assertEquals("<k>1</k><k>2</k>", XML.toString(new JSONArray("[1,2]"), "k"));
   }

   @Test
   public void markupSurvivesTheRoundTrip() throws Exception {
      JSONObject json = XML.toJSONObject(TVBUS);
      JSONObject again = XML.toJSONObject(XML.toString(json));
      assertEquals("Cheers",
         again.getJSONObject("TvBusMarshalledStruct:TvBusEnvelope")
              .getJSONObject("showing").getJSONObject("program").getString("title"));
   }

   @Test
   public void tokenizerReadsTagPiecesAndSkipsPast() throws Exception {
      // nextToken reads what is inside the angle brackets, so the caller has
      // already consumed the '<' - handing it one is an error
      assertThrows(JSONException.class, () -> new XMLTokener("<tag>").nextToken());

      XMLTokener x = new XMLTokener("tag attr=\"v\">body</tag>");
      assertEquals("tag", x.nextToken());
      assertEquals("attr", x.nextToken());
      assertEquals(XML.EQ, x.nextToken());
      assertEquals("v", x.nextToken());
      assertEquals(XML.GT, x.nextToken());
      assertEquals("body", x.nextContent());

      XMLTokener y = new XMLTokener("junk<!-- c -->tail");
      assertTrue(y.skipPast("-->"));
      assertEquals("tail", y.nextContent());
      assertFalse(new XMLTokener("abc").skipPast("-->"));
   }

   @Test
   public void tokenizerDecodesEntitiesAndRejectsUnterminatedOnes() throws Exception {
      assertEquals(XML.AMP, new XMLTokener("amp;").nextEntity('&'));
      assertEquals("&unknown;", new XMLTokener("unknown;").nextEntity('&'));
      assertThrows(JSONException.class, () -> new XMLTokener("amp").nextEntity('&'));
   }
}
