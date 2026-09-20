package com.tivo.kmttg.captions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tivo.kmttg.main.config;

// Cover for the .srt parser behind the caption sync tools. The inputs are written into a
// temp dir here rather than checked in so the file being parsed sits next to what it
// should produce.
public class SrtReaderTest {

   @TempDir
   Path tmp;

   private Boolean prevMode;

   @BeforeEach
   public void batchMode() {
      // A failed read logs, and in GUI mode that goes through a gui singleton we have not built.
      prevMode = config.GUIMODE;
      config.GUIMODE = false;
   }

   @AfterEach
   public void restoreMode() {
      config.GUIMODE = prevMode;
   }

   // Line ending is always spelled out - real .srt files arrive both ways.
   private String srtFile(String name, String eol, String... lines) throws IOException {
      Path file = tmp.resolve(name);
      Files.write(file, String.join(eol, lines).getBytes(StandardCharsets.UTF_8));
      return file.toString();
   }

   @Test
   public void parsesAWellFormedFileIntoCaptions() throws IOException {
      String file = srtFile("show.srt", "\n",
         "1", "00:00:01,000 --> 00:00:03,500", "Hello there", "",
         "2", "00:00:05,250 --> 00:00:07,000", "Second caption", "",
         "3", "00:00:10,000 --> 00:00:12,000", "Third caption", "");

      srtReader r = new srtReader(file);

      assertNotNull(r.ccstack);
      assertEquals(3, r.ccstack.size());
      cc first = r.ccstack.get(0);
      assertEquals(1000, first.start);
      assertEquals(3500, first.stop);
      assertEquals("Hello there ", first.text);
      cc second = r.ccstack.get(1);
      assertEquals(5250, second.start);
      assertEquals(7000, second.stop);
      assertEquals("Second caption ", second.text);
      // An entry is pushed when the next timestamp line arrives, so the last one has to be
      // flushed at EOF or it is lost
      cc third = r.ccstack.get(2);
      assertEquals(10000, third.start);
      assertEquals(12000, third.stop);
      assertEquals("Third caption ", third.text);
   }

   @Test
   public void aSingleEntryFileYieldsThatCaption() throws IOException {
      String file = srtFile("one.srt", "\n",
         "1", "00:00:01,000 --> 00:00:02,000", "Only caption", "");

      srtReader r = new srtReader(file);

      assertEquals(1, r.ccstack.size());
      assertEquals("Only caption ", r.ccstack.get(0).text);
   }

   @Test
   public void parsesTimestampsToMilliseconds() throws IOException {
      String file = srtFile("times.srt", "\n",
         "1", "01:02:03,004 --> 01:02:04,567", "Text one", "",
         "2", "10:00:00,999 --> 10:00:02,000", "Text two", "");

      srtReader r = new srtReader(file);

      assertEquals(2, r.ccstack.size());
      // The comma separated field is milliseconds, not hundredths, and hours carry fully.
      assertEquals(3723004, r.ccstack.get(0).start);
      assertEquals(3724567, r.ccstack.get(0).stop);
      assertEquals(36000999, r.ccstack.get(1).start);
      assertEquals(36002000, r.ccstack.get(1).stop);
   }

   @Test
   public void joinsMultiLineCaptionTextWithASpace() throws IOException {
      String file = srtFile("multiline.srt", "\n",
         "1", "00:00:01,000 --> 00:00:04,000", "- Where are you going?", "- Out.", "");

      srtReader r = new srtReader(file);

      assertEquals(1, r.ccstack.size());
      // Each line contributes a trailing space, the last one included.
      assertEquals("- Where are you going? - Out. ", r.ccstack.get(0).text);
   }

   @Test
   public void timestampLinesAloneSeparateEntries() throws IOException {
      // No sequence numbers and no blank lines: neither is structural, so this parses the
      // same as the conventional layout.
      String file = srtFile("bare.srt", "\n",
         "00:00:01,000 --> 00:00:02,000", "First caption",
         "00:00:03,000 --> 00:00:04,000", "Second caption");

      srtReader r = new srtReader(file);

      assertEquals(2, r.ccstack.size());
      assertEquals("First caption ", r.ccstack.get(0).text);
      assertEquals(3000, r.ccstack.get(1).start);
      assertEquals("Second caption ", r.ccstack.get(1).text);
   }

   @Test
   public void crlfInputParsesTheSameAsLf() throws IOException {
      String file = srtFile("windows.srt", "\r\n",
         "1", "00:00:01,000 --> 00:00:03,500", "Hello there", "");

      srtReader r = new srtReader(file);

      assertEquals(1, r.ccstack.size());
      assertEquals(1000, r.ccstack.get(0).start);
      assertEquals(3500, r.ccstack.get(0).stop);
      // The stray CR must not survive into the caption text or the text compare in srtSync
      // would never match an LF file.
      assertEquals("Hello there ", r.ccstack.get(0).text);
   }

   @Test
   public void aMalformedTimestampMergesItsTextIntoTheNextCaption() throws IOException {
      // Two digit milliseconds fail the strict pattern but still read as a timestamp line,
      // so the entry is dropped and its text runs on into the following one.
      String file = srtFile("malformed.srt", "\n",
         "1", "00:00:01,000 --> 00:00:02,000", "First caption",
         "2", "00:00:03,00 --> 00:00:04,000", "Orphan text",
         "3", "00:00:05,000 --> 00:00:06,000", "Second caption");

      srtReader r = new srtReader(file);

      assertEquals(2, r.ccstack.size());
      assertEquals("First caption ", r.ccstack.get(0).text);
      assertEquals(5000, r.ccstack.get(1).start);
      assertEquals("Orphan text Second caption ", r.ccstack.get(1).text);
   }

   @Test
   public void anEmptyFileYieldsNoCaptions() throws IOException {
      Path file = tmp.resolve("empty.srt");
      Files.write(file, new byte[0]);

      srtReader r = new srtReader(file.toString());

      // Empty is not an error: an empty stack, not null.
      assertNotNull(r.ccstack);
      assertEquals(0, r.ccstack.size());
   }

   @Test
   public void aFileWithoutTimestampsYieldsNoCaptions() throws IOException {
      String file = srtFile("junk.srt", "\n",
         "WEBVTT", "", "this is not a subtitle file", "42", "");

      srtReader r = new srtReader(file);

      assertNotNull(r.ccstack);
      assertEquals(0, r.ccstack.size());
   }

   @Test
   public void aMissingFileYieldsANullStack() {
      srtReader r = new srtReader(tmp.resolve("nosuch.srt").toString());

      // Callers key off null to tell a read failure from a file with nothing in it.
      assertNull(r.ccstack);
   }
}
