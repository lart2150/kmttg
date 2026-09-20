package com.tivo.kmttg.captions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tivo.kmttg.main.config;

// Cover for the caption diff that lines one recording's timeline up against another's, and
// for the lookups the cut tools use to land on a caption boundary.
public class SrtSyncTest {

   @TempDir
   Path tmp;

   private Boolean prevMode;

   @BeforeEach
   public void batchMode() {
      prevMode = config.GUIMODE;
      config.GUIMODE = false;
   }

   @AfterEach
   public void restoreMode() {
      config.GUIMODE = prevMode;
   }

   private String srtFile(String name, String... lines) throws IOException {
      Path file = tmp.resolve(name);
      Files.write(file, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
      return file.toString();
   }

   // Two airings of the same show. The second runs 5 seconds late and one caption reads
   // differently, which is the shape the sync has to cope with. Each file carries a trailing
   // entry because the parser drops the last one.
   private String baseline() throws IOException {
      return srtFile("baseline.srt",
         "1", "00:00:10,000 --> 00:00:12,000", "Alpha caption here", "",
         "2", "00:00:20,000 --> 00:00:22,000", "Bravo caption here", "",
         "3", "00:00:30,000 --> 00:00:32,000", "Charlie caption here", "",
         "4", "00:00:40,000 --> 00:00:42,000", "tail", "");
   }

   private String shifted() throws IOException {
      return srtFile("shifted.srt",
         "1", "00:00:15,000 --> 00:00:17,000", "Alpha caption here", "",
         "2", "00:00:25,000 --> 00:00:27,000", "Bravo differs here", "",
         "3", "00:00:35,000 --> 00:00:37,000", "Charlie caption here", "",
         "4", "00:00:45,000 --> 00:00:47,000", "tail", "");
   }

   private srtSync sync() throws IOException {
      return new srtSync(baseline(), shifted(), false);
   }

   @Test
   public void matchedCaptionsCarryBothTimelinesAndPositions() throws IOException {
      srtSync s = sync();

      assertNotNull(s.ccstack);
      // Bravo's text differs between the two airings, so only two of the three sync.
      assertEquals(2, s.ccstack.size());
      ccdiff first = s.ccstack.get(0);
      assertEquals("Alpha caption here ", first.text);
      assertEquals(10000, first.start1);
      assertEquals(15000, first.start2);
      assertEquals(12000, first.stop1);
      assertEquals(17000, first.stop2);
      assertEquals(5000, first.startDiff());
      assertEquals(5000, first.stopDiff());
      // Indices are 1 based positions within each file's caption list, and they count the
      // unmatched captions too.
      assertEquals(1, first.index1);
      assertEquals(1, first.index2);
      ccdiff second = s.ccstack.get(1);
      assertEquals("Charlie caption here ", second.text);
      assertEquals(30000, second.start1);
      assertEquals(35000, second.start2);
      assertEquals(3, second.index1);
      assertEquals(3, second.index2);
   }

   @Test
   public void captionTextShorterThanTheMinimumIsNotSynced() throws IOException {
      // "Yes. " is 5 characters with its trailing space, under the 8 character floor, so it
      // is passed over even though both files agree on text and time exactly.
      String file1 = srtFile("short1.srt",
         "1", "00:00:10,000 --> 00:00:11,000", "Yes.", "",
         "2", "00:00:20,000 --> 00:00:21,000", "Long enough text", "",
         "3", "00:00:30,000 --> 00:00:31,000", "tail", "");
      String file2 = srtFile("short2.srt",
         "1", "00:00:10,000 --> 00:00:11,000", "Yes.", "",
         "2", "00:00:20,000 --> 00:00:21,000", "Long enough text", "",
         "3", "00:00:30,000 --> 00:00:31,000", "tail", "");

      srtSync s = new srtSync(file1, file2, false);

      assertEquals(1, s.ccstack.size());
      assertEquals("Long enough text ", s.ccstack.get(0).text);
   }

   @Test
   public void identicalTextTooFarApartInTimeIsNotSynced() throws IOException {
      // Exactly 60 seconds apart. The window is a strict less than, so this is out.
      String file1 = srtFile("far1.srt",
         "1", "00:00:10,000 --> 00:00:12,000", "Identical text here", "",
         "2", "00:00:20,000 --> 00:00:22,000", "tail", "");
      String file2 = srtFile("far2.srt",
         "1", "00:01:10,000 --> 00:01:12,000", "Identical text here", "",
         "2", "00:01:20,000 --> 00:01:22,000", "tail", "");

      srtSync s = new srtSync(file1, file2, false);

      assertNotNull(s.ccstack);
      assertEquals(0, s.ccstack.size());
   }

   @Test
   public void findBeforeReturnsTheLastCaptionEndedByThatTime() throws IOException {
      srtSync s = sync();

      // Nothing has ended yet ahead of the first caption.
      assertNull(s.findBefore(5000));
      assertEquals(10000, s.findBefore(20000).start1);
      // The comparison is a strict >, so a caption ending exactly at the time counts as before it.
      assertEquals(10000, s.findBefore(12000).start1);
      // Past the end of the synced range the last caption is still the one before it - this
      // is where SkipShare asks about the final commercial break
      assertEquals(30000, s.findBefore(40000).start1);
   }

   @Test
   public void findAfterReturnsTheFirstCaptionStartingLater() throws IOException {
      srtSync s = sync();

      assertEquals(10000, s.findAfter(0).start1);
      // Strict > again: a caption starting exactly at the time is not "after" it.
      assertEquals(30000, s.findAfter(10000).start1);
      assertNull(s.findAfter(35000));
   }

   @Test
   public void anUnreadableFileYieldsANullStackAndNoLookups() throws IOException {
      String missing = tmp.resolve("nosuch.srt").toString();

      srtSync s = new srtSync(missing, shifted(), false);

      assertNull(s.ccstack);
      assertNull(s.findBefore(0));
      assertNull(s.findAfter(0));
      // Either side failing to read kills the whole diff.
      assertNull(new srtSync(baseline(), missing, false).ccstack);
   }

   @Test
   public void theDebugFlagOnlyDumpsToStdout() throws IOException {
      srtSync quiet = sync();
      srtSync loud = new srtSync(baseline(), shifted(), true);

      assertEquals(quiet.ccstack.size(), loud.ccstack.size());
      assertEquals(quiet.ccstack.get(0).start1, loud.ccstack.get(0).start1);
      loud.print();
   }

   @Test
   public void ccdiffDescribesBothTimelines() throws IOException {
      String text = sync().ccstack.get(0).toString();

      assertTrue(text.contains("index1=1"), text);
      assertTrue(text.contains("index2=1"), text);
      assertTrue(text.contains("diff=5000"), text);
      assertTrue(text.contains("Alpha caption here"), text);
   }
}
