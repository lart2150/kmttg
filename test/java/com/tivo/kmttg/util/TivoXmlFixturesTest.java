package com.tivo.kmttg.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.w3c.dom.Document;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;

// The XML side of a TiVo, read against documents a real box served rather than hand-written
// ones. ParseNplTest and CreateMetaTest pin the parsing rules one element at a time; what
// these add is what a box emits around them - the fields it leaves out on a movie or on
// something still recording, the entities it escapes, and the paging header the Now Playing
// fetch walks. Every capture present is read, so capturing another box with
// captureXmlFixtures -Plabel=<model> widens these without anything here being edited.
public class TivoXmlFixturesTest {

   @TempDir
   Path work;

   private int savedMinChanDigits;

   @BeforeEach
   void pinGlobals() {
      savedMinChanDigits = config.MinChanDigits;
      config.MinChanDigits = 1;
   }

   @AfterEach
   void restoreGlobals() {
      config.MinChanDigits = savedMinChanDigits;
   }

   // Which box and which software, so a difference between two captures can be attributed
   // rather than guessed at. The model name is in nothing the HTTP interface serves - the
   // service number prefix is as close as it gets - which is why the label is chosen by
   // hand at capture time.
   @Test
   public void everyCaptureSaysWhatItCameFrom() throws Exception {
      for (String label : labels()) {
         String provenance = raw("capture_" + label + ".txt");
         assertTrue(provenance.contains("software: "), label + ": no software level recorded");
         assertFalse(provenance.contains("software: unknown"),
            label + ": the box did not answer when it was captured");
      }
   }

   // parseString reads the FIRST line of the document and nothing else, so a box that
   // pretty-printed its XML would return an empty list rather than an error. Nothing in
   // kmttg checks that, which makes this the assumption most worth pinning to a capture.
   @Test
   public void theWholeContainerArrivesOnOneLine() throws Exception {
      for (String label : labels()) {
         for (String which : new String[] {"page1", "page2", "item"}) {
            assertFalse(page(label, which).contains("\n"),
               "npl_" + label + "_" + which + ".xml is not on one line");
         }
      }
   }

   @Test
   public void everyRecordingOnThePageBecomesAnEntry() throws Exception {
      for (String label : labels()) {
         Stack<Hashtable<String,String>> entries = new Stack<Hashtable<String,String>>();
         Hashtable<String,Integer> header = parse(page(label, "page1"), entries);

         assertEquals(header.get("ItemCount").intValue(), entries.size(),
            label + ": the header count and the items must agree or the fetch loop mispages");
         assertTrue(header.get("TotalItems") > header.get("ItemCount"),
            label + ": the capture is meant to be one page of a longer list");
         for (Hashtable<String,String> e : entries) {
            String where = label + " " + e.get("title");
            assertEquals("Bolt", e.get("tivoName"), where);
            assertNotNull(e.get("url"), where + ": no download url");
            assertNotNull(e.get("url_TiVoVideoDetails"), where + ": no details url");
            assertNotNull(e.get("size"), where);
            assertNotNull(e.get("gmt"), where);
            // What the table sorts and the auto transfers dedup on; built from the
            // ProgramId, so an entry missing one lands here as a literal "null_".
            assertFalse(e.get("ProgramId_unique").startsWith("null"), where + ": no ProgramId");
         }
      }
   }

   // The download url is handed to the http client as it stands, so an entity left in it
   // would be sent to the TiVo literally.
   @Test
   public void noUrlKeepsAnEscapedAmpersand() throws Exception {
      for (String label : labels()) {
         for (Hashtable<String,String> e : entries(label, "page1")) {
            assertFalse(e.get("url").contains("&amp;"), e.get("url"));
            assertFalse(e.get("url_TiVoVideoDetails").contains("&amp;"),
               e.get("url_TiVoVideoDetails"));
         }
      }
   }

   // Titles and descriptions go into file names and the pyTivo sidecar, so an entity that
   // survived the parse would be written to disk as "&quot;".
   @Test
   public void titlesAndDescriptionsComeOutAsText() throws Exception {
      int escaped = 0;
      for (String label : labels()) {
         if (page(label, "page1").contains("&quot;")) escaped++;
         for (Hashtable<String,String> e : entries(label, "page1")) {
            String text = e.get("title") + " " + e.get("description");
            assertFalse(text.contains("&amp;") || text.contains("&quot;") || text.contains("&#"),
               "undecoded entity in: " + text);
            // The rating service's copyright line is cut out of every description it ends.
            assertFalse(text.contains("Copyright Rovi"), "copyright notice left in: " + text);
         }
      }
      assumeTrue(escaped > 0, "no capture holds an escaped entity to decode");
   }

   // Introduced in TiVo 20.2.2 and read as an override, which only shows on a recording
   // whose showing began before the capture did - a movie joined in progress, here.
   @Test
   public void theDateComesFromTheShowingRatherThanTheCapture() throws Exception {
      int overrides = 0;
      for (String label : labels()) {
         String xml = page(label, "page1");
         for (Hashtable<String,String> e : entries(label, "page1")) {
            assertTrue(xml.contains("<ShowingStartTime>" + e.get("gmt_hex") + "<"),
               e.get("title") + ": the date did not come from the showing");
            if (! xml.contains("<CaptureDate>" + e.get("gmt_hex") + "<")) overrides++;
         }
      }
      assumeTrue(overrides > 0, "no captured recording has the two times differing");
   }

   // A digital channel arrives as major-minor, and the sortable form pads the minor so the
   // guide order survives a text sort within one major channel.
   @Test
   public void digitalChannelsGetASortableForm() throws Exception {
      int checked = 0;
      for (String label : labels()) {
         for (Hashtable<String,String> e : entries(label, "page1")) {
            String channel = e.get("channelNum");
            if (channel == null || ! channel.contains("-")) continue;
            String[] parts = channel.split("-");
            assertEquals(parts[0] + "." + String.format("%03d", Integer.parseInt(parts[1])),
               e.get("sortableChannel"), channel);
            checked++;
         }
      }
      assumeTrue(checked > 0, "no capture holds an over-the-air channel");
   }

   // A recording still being made has no byte offset to resume from, so a resumed download of
   // one starts over. Its duration is what has been recorded so far rather than what was
   // scheduled - zero if it is caught in its first seconds - so nothing can read a length for
   // one off the NPL either.
   @Test
   public void aRecordingStillInProgressIsFlaggedAndHasNoOffset() throws Exception {
      int inProgress = 0;
      for (String label : labels()) {
         for (Hashtable<String,String> e : entries(label, "page1")) {
            if (! "Yes".equals(e.get("InProgress"))) continue;
            inProgress++;
            assertEquals("in-progress-recording", e.get("ExpirationImage"), e.get("title"));
            assertFalse(e.containsKey("ByteOffset"),
               e.get("title") + ": offset on a live recording");
         }
      }
      assumeTrue(inProgress > 0, "no capture caught a recording in progress");
   }

   // The fetch pages by AnchorOffset and folds the pages together, so the two pages have to
   // describe different recordings - a page that repeated the first would be silently
   // dropped by uniquify and the list would come up short.
   @Test
   public void theSecondPageContinuesRatherThanRepeatingTheFirst() throws Exception {
      for (String label : labels()) {
         Stack<Hashtable<String,String>> all = new Stack<Hashtable<String,String>>();
         parse(page(label, "page1"), all);
         int firstPage = all.size();
         parse(page(label, "page2"), all);

         Set<String> ids = new HashSet<String>();
         for (Hashtable<String,String> e : all) ids.add(e.get("ProgramId_unique"));
         assertEquals(all.size(), ids.size(), label + ": the pages overlap");

         Stack<Hashtable<String,String>> unique =
            parseNPL.uniquify(all, new Hashtable<String,Integer>());
         assertEquals(firstPage * 2, unique.size(), label + ": uniquify dropped a recording");
      }
   }

   // What jobMonitor asks for when resuming a download. The answer for a recording still
   // being made is no offset at all, which is why a resumed one of those starts over.
   @Test
   public void aQueryItemDocumentDescribesTheOneRecording() throws Exception {
      for (String label : labels()) {
         Document doc = Xml.getDocument(new ByteArrayInputStream(
            page(label, "item").getBytes(StandardCharsets.UTF_8)));
         assertNotNull(doc, label);
         assertEquals(1, doc.getElementsByTagName("Item").getLength(), label);
         assertTrue(doc.getElementsByTagName("Title").getLength() > 0, label);
         if ("Yes".equals(text(doc, "InProgress"))) {
            assertEquals(0, doc.getElementsByTagName("ByteOffset").getLength(),
               label + ": a live recording offering an offset would change what resume means");
         }
      }
   }

   // The sidecar the encoders and pyTivo read back. Driven from the documents themselves
   // rather than from a constructed one: a real details document carries elements kmttg was
   // never told about, and the parse has to walk past them.
   @Test
   public void everyCapturedDetailsDocumentBecomesASidecar() throws Exception {
      int movies = 0;
      for (String name : detailFixtures()) {
         String xml = raw(name);
         jobData job = job(xml);
         assertTrue(createMeta.createMetaFile(job, null), name);
         String sidecar = read(job.metaFile);
         for (String line : sidecar.split("\r\n")) {
            assertTrue(line.matches("^\\S+ : .*$"), name + ": malformed sidecar line: " + line);
         }
         assertTrue(sidecar.contains("title : "), name + ": no title");
         assertTrue(sidecar.contains("iso_duration : "), name + ": no duration");
         // A movie is described differently from an episode, and the year is what the file
         // name keyword and pyTivo both use to tell two films of one name apart.
         if (xml.contains("<movieYear>")) {
            movies++;
            assertTrue(sidecar.contains("movieYear : "), name + ": the year was dropped");
         }
      }
      assumeTrue(movies > 0, "no capture holds a movie - raise -PitemCount to reach one");
   }

   /* ---- helpers ---- */

   // Every capture present. Adding a box means running captureXmlFixtures again; nothing
   // here has to be edited for its fixtures to be read.
   private static List<String> labels() throws Exception {
      List<String> labels = new ArrayList<String>();
      for (String name : fixtureDir().list()) {
         if (name.startsWith("npl_") && name.endsWith("_page1.xml"))
            labels.add(name.substring(4, name.length() - "_page1.xml".length()));
      }
      assertFalse(labels.isEmpty(), "no captured NPL fixtures to read");
      Collections.sort(labels);
      return labels;
   }

   private static List<String> detailFixtures() throws Exception {
      List<String> found = new ArrayList<String>();
      for (String name : fixtureDir().list()) {
         if (name.startsWith("videodetails_") && name.endsWith(".xml")) found.add(name);
      }
      assertFalse(found.isEmpty(), "no captured details documents to read");
      Collections.sort(found);
      return found;
   }

   private static File fixtureDir() throws Exception {
      return new File(TivoXmlFixturesTest.class.getResource("/fixtures").toURI());
   }

   private static String raw(String name) throws Exception {
      try (InputStream is = TivoXmlFixturesTest.class.getResourceAsStream("/fixtures/" + name)) {
         assertNotNull(is, "missing fixture: /fixtures/" + name);
         return new String(is.readAllBytes(), StandardCharsets.UTF_8);
      }
   }

   private static String page(String label, String which) throws Exception {
      return raw("npl_" + label + "_" + which + ".xml");
   }

   private static Hashtable<String,Integer> parse(String xml,
         Stack<Hashtable<String,String>> into) {
      Hashtable<String,Integer> header = parseNPL.parseString(xml, "Bolt", into);
      assertNotNull(header, "the container did not parse");
      return header;
   }

   private static Stack<Hashtable<String,String>> entries(String label, String which)
         throws Exception {
      Stack<Hashtable<String,String>> entries = new Stack<Hashtable<String,String>>();
      parse(page(label, which), entries);
      return entries;
   }

   private static String text(Document doc, String tag) {
      if (doc.getElementsByTagName(tag).getLength() == 0) return null;
      return doc.getElementsByTagName(tag).item(0).getTextContent();
   }

   private jobData job(String xml) throws IOException {
      jobData job = new jobData();
      Path details = work.resolve("details.xml");
      Files.write(details, xml.getBytes(StandardCharsets.UTF_8));
      job.metaTmpFile = details.toString();
      job.metaFile = work.resolve("show.txt").toString();
      return job;
   }

   private static String read(String file) throws IOException {
      return new String(Files.readAllBytes(Path.of(file)), Charset.defaultCharset());
   }
}
