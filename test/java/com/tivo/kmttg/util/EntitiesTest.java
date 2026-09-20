package com.tivo.kmttg.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

// Entities is the HTML4 entity table used on show titles and descriptions
// coming off the TiVo web server. Both directions are single pass and
// deliberately lax: anything the table does not know about is copied through
// verbatim rather than mangled or dropped.
public class EntitiesTest {

   @Test
   void quoteHtmlEscapesTheCharactersThatHaveEntities() {
      assertEquals("Tom &amp; Jerry &lt;b&gt; &quot;x&quot;",
         Entities.quoteHtml("Tom & Jerry <b> \"x\""));
   }

   @Test
   void quoteHtmlMapsAccentedAndSymbolCharacters() {
      assertEquals("caf&eacute; &copy; &nbsp;!", Entities.quoteHtml("caf\u00e9 \u00a9 \u00a0!"));
   }

   @Test
   void quoteHtmlLeavesCharactersWithoutAnEntityAlone() {
      // The HTML4 table has no apostrophe entity, so it stays a bare quote
      assertEquals("it's a/b {1}", Entities.quoteHtml("it's a/b {1}"));
   }

   @Test
   void namedEntitiesDecodeToTheirCharacter() {
      assertEquals("Tom & Jerry <b> caf\u00e9",
         Entities.replaceHtmlEntities("Tom &amp; Jerry &lt;b&gt; caf&eacute;"));
   }

   @Test
   void numericEntitiesDecodeToTheirCharacter() {
      assertEquals("caf\u00e9 & \u00a0end", Entities.replaceHtmlEntities("caf&#233; &#38; &#160;end"));
   }

   @Test
   void unknownEntitiesAreLeftVerbatim() {
      // Names are case sensitive and only the decimal numeric form is in the
      // table, so a hex reference is not an entity here
      assertEquals("&bogus; &AMP; &#x27;", Entities.replaceHtmlEntities("&bogus; &AMP; &#x27;"));
   }

   @Test
   void anAmpersandWithoutASemicolonIsLiteral() {
      assertEquals("AT&T and a&", Entities.replaceHtmlEntities("AT&T and a&"));
   }

   @Test
   void aSemicolonFurtherDownTheLineIsNotAnEntity() {
      assertEquals("a & b; c", Entities.replaceHtmlEntities("a & b; c"));
   }

   @Test
   void decodingRunsOnlyOncePerString() {
      // An escaped escape decodes one level, it does not keep unwrapping
      assertEquals("&lt;", Entities.replaceHtmlEntities("&amp;lt;"));
   }

   @Test
   void quotingThenDecodingRoundTrips() {
      String s = "Tom & Jerry <b> \"caf\u00e9\" \u00a9";
      assertEquals(s, Entities.replaceHtmlEntities(Entities.quoteHtml(s)));
      assertEquals("&amp;lt;", Entities.quoteHtml("&lt;"));
   }

   @Test
   void emptyStringsPassThroughBothWays() {
      assertEquals("", Entities.quoteHtml(""));
      assertEquals("", Entities.replaceHtmlEntities(""));
   }
}
