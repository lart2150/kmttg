package com.tivo.kmttg.JSON;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Tests the character-level scanner underneath both parsers.
 *
 * kmttg feeds it three different ways - a String, a Reader over a file
 * (JSONFile) and an InputStream - and the single-character pushback it keeps
 * is what both container parsers use to look ahead. The escape handling in
 * nextString is the part most likely to bite: a TiVo show title carrying a
 * quote or a unicode escape has to come back out intact.
 */
public class JSONTokenerTest {

   @Test
   public void readsCharactersInOrderAndReportsTheEnd() throws Exception {
      JSONTokener x = new JSONTokener("ab");
      assertTrue(x.more());
      assertEquals('a', x.next());
      assertEquals('b', x.next());
      assertFalse(x.more());
      // Past the end it keeps handing back 0 rather than failing
      assertEquals(0, x.next());
      assertTrue(x.end());
   }

   @Test
   public void backPushesOneCharacterAndOnlyOne() throws Exception {
      JSONTokener x = new JSONTokener("ab");
      assertEquals('a', x.next());
      x.back();
      assertEquals('a', x.next());
      // A second step back without an intervening read is not supported
      x.back();
      assertThrows(JSONException.class, () -> x.back());
   }

   @Test
   public void backAtTheStartIsRejected() {
      JSONTokener x = new JSONTokener("ab");
      assertThrows(JSONException.class, () -> x.back());
   }

   @Test
   public void nextCleanSkipsWhitespace() throws Exception {
      JSONTokener x = new JSONTokener("  \t\r\n {");
      assertEquals('{', x.nextClean());
      assertEquals(0, x.nextClean());
   }

   @Test
   public void nextWithAnExpectedCharacterChecksIt() throws Exception {
      JSONTokener x = new JSONTokener("{}");
      assertEquals('{', x.next('{'));
      assertThrows(JSONException.class, () -> x.next('{'));
   }

   @Test
   public void nextCountTakesAFixedRunOrFails() throws Exception {
      JSONTokener x = new JSONTokener("abcde");
      assertEquals("", x.next(0));
      assertEquals("abc", x.next(3));
      assertThrows(JSONException.class, () -> x.next(5));
   }

   @Test
   public void nextStringDecodesEveryEscape() throws Exception {
      JSONTokener x = new JSONTokener("a\\\"b\\\\c\\/d\\b\\f\\n\\r\\t\\u00e9\"");
      assertEquals("a\"b\\c/d\b\f\n\r\té", x.nextString('"'));
   }

   @Test
   public void nextStringAcceptsSingleQuotesAsTheDelimiter() throws Exception {
      assertEquals("v", new JSONTokener("v'").nextString('\''));
   }

   @Test
   public void nextStringRejectsUnterminatedAndIllegalEscapes() {
      assertThrows(JSONException.class, () -> new JSONTokener("abc").nextString('"'));
      assertThrows(JSONException.class, () -> new JSONTokener("ab\ncd\"").nextString('"'));
      assertThrows(JSONException.class, () -> new JSONTokener("a\\qb\"").nextString('"'));
   }

   @Test
   public void nextValueTypesWhateverComesNext() throws Exception {
      assertEquals("s", new JSONTokener("\"s\"").nextValue());
      assertEquals("s", new JSONTokener("'s'").nextValue());
      assertEquals(Integer.valueOf(42), new JSONTokener(" 42 ").nextValue());
      assertEquals(Double.valueOf(1.5), new JSONTokener("1.5").nextValue());
      assertEquals(Boolean.TRUE, new JSONTokener("true").nextValue());
      assertSame(JSONObject.NULL, new JSONTokener("null").nextValue());
      assertTrue(new JSONTokener("{\"a\":1}").nextValue() instanceof JSONObject);
      assertTrue(new JSONTokener("[1]").nextValue() instanceof JSONArray);
      assertThrows(JSONException.class, () -> new JSONTokener("").nextValue());
      assertThrows(JSONException.class, () -> new JSONTokener(",").nextValue());
   }

   @Test
   public void nextToStopsAtADelimiterAndTrims() throws Exception {
      JSONTokener x = new JSONTokener(" name = value \n rest");
      assertEquals("name", x.nextTo('='));
      assertEquals('=', x.next());
      // The end of the line is a delimiter too, whatever was asked for
      assertEquals("value", x.nextTo('='));

      JSONTokener y = new JSONTokener(" a b ;c");
      assertEquals("a b", y.nextTo(";,"));
      assertEquals(';', y.next());
   }

   @Test
   public void skipToFindsTheCharacterOrRewinds() throws Exception {
      JSONTokener x = new JSONTokener("abc:def");
      assertEquals(':', x.skipTo(':'));
      assertEquals(':', x.next());

      // A miss leaves the position untouched so the caller can try something else
      JSONTokener y = new JSONTokener("abc");
      assertEquals(0, y.skipTo(':'));
      assertEquals('a', y.next());
   }

   @Test
   public void dehexcharCoversBothCases() {
      assertEquals(0, JSONTokener.dehexchar('0'));
      assertEquals(9, JSONTokener.dehexchar('9'));
      assertEquals(10, JSONTokener.dehexchar('A'));
      assertEquals(15, JSONTokener.dehexchar('f'));
      assertEquals(-1, JSONTokener.dehexchar('g'));
   }

   @Test
   public void syntaxErrorNamesThePosition() throws Exception {
      JSONTokener x = new JSONTokener("line1\nline2");
      x.next(8);
      JSONException e = x.syntaxError("boom");
      assertTrue(e.getMessage().startsWith("boom at 8 "), e.getMessage());
      assertTrue(e.getMessage().contains("line 2"), e.getMessage());
      assertTrue(x.toString().contains("character"), x.toString());
   }

   @Test
   public void readsFromAReaderAndAnInputStream() throws Exception {
      JSONObject fromReader = new JSONObject(new JSONTokener(new StringReader("{\"a\":1}")));
      assertEquals(1, fromReader.getInt("a"));

      byte[] bytes = "{\"a\":2}".getBytes(StandardCharsets.UTF_8);
      JSONObject fromStream =
         new JSONObject(new JSONTokener(new ByteArrayInputStream(bytes)));
      assertEquals(2, fromStream.getInt("a"));
   }
}
