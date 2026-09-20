package com.tivo.kmttg.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.kmttg;

// Xml parses the TiVoConnect container documents the TiVo web server returns.
// Parsing is in memory only, so this needs no network. The part worth pinning
// is that getElement runs the parsed text back through the HTML entity table,
// because the TiVo double escapes titles, and that a document the parser
// rejects comes back null instead of throwing at the caller.
public class XmlTest {

   private static Boolean prevGuiMode;
   private static boolean prevStartingUp;

   @BeforeAll
   static void silenceLogging() {
      prevGuiMode = config.GUIMODE;
      prevStartingUp = kmttg._startingUp;
      config.GUIMODE = false;
      kmttg._startingUp = true;
   }

   @AfterAll
   static void restoreLogging() {
      config.GUIMODE = prevGuiMode;
      kmttg._startingUp = prevStartingUp;
   }

   private static Document parse(String xml) {
      return Xml.getDocument(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
   }

   @Test
   void getElementReturnsTheFirstMatchingTagsText() {
      Document doc = parse("<TiVoContainer><Details><Title>First</Title>"
         + "<SourceSize>1234</SourceSize></Details>"
         + "<Item><Title>Second</Title></Item></TiVoContainer>");
      assertNotNull(doc);
      assertEquals("First", Xml.getElement(doc, "Title"));
      assertEquals("1234", Xml.getElement(doc, "SourceSize"));
   }

   @Test
   void getElementDecodesEntitiesTheXmlParserLeftBehind() {
      // The TiVo escapes twice, so &amp;amp; parses to &amp; and only then
      // becomes a bare ampersand
      Document doc = parse("<a><Title>Tom &amp;amp; Jerry</Title><b>caf&amp;#233;</b></a>");
      assertEquals("Tom & Jerry", Xml.getElement(doc, "Title"));
      assertEquals("caf\u00e9", Xml.getElement(doc, "b"));
   }

   @Test
   void getElementReturnsNullForATagThatIsNotThere() {
      assertNull(Xml.getElement(parse("<a><b>x</b></a>"), "Nope"));
   }

   @Test
   void getElementFlattensNestedChildText() {
      assertEquals("deep", Xml.getElement(parse("<r><outer><inner>deep</inner></outer></r>"), "outer"));
   }

   @Test
   void unparseableXmlReturnsNullRatherThanThrowing() {
      assertNull(parse("<a><b></a>"));
      assertNull(parse(""));
   }
}
