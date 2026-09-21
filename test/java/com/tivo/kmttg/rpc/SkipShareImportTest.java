package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Stack;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;

// Skip share: somebody else's cut points for the same broadcast, arriving with their subtitle
// file so the two recordings can be lined up. Their clock is not mine - different start
// padding, a different station's feed - so every point is moved by the difference between
// where a caption we both have starts in their recording and in mine. A share that cannot be
// lined up is the case that matters, because the import replaces what is already in the table.
public class SkipShareImportTest {

   @TempDir
   Path work;

   private static final long HALF_HOUR = 1800000L;
   private static final String AIRING =
      "tivo:of.ctd.394439967.26-6.terrestrial.2026-08-23-03-30-00.1800";
   // Their recording started 5 s before mine, so every point of theirs is 5 s later in mine.
   private static final long SHIFT = 5000;

   private String savedProgramDir;
   private String savedOs;
   private int savedEnabled;
   private Boolean savedMode;
   private LinkedHashMap<String,String> savedTivos;
   private Hashtable<String,String> savedRpc;

   @BeforeEach
   void redirectProgramDir() {
      savedProgramDir = config.programDir;
      savedOs = config.OS;
      savedEnabled = config.autoskip_enabled;
      savedMode = config.GUIMODE;
      savedTivos = new LinkedHashMap<String,String>(config.TIVOS);
      savedRpc = new Hashtable<String,String>(config.enableRpc);
      config.programDir = work.toString();
      // Unzip chmods what it extracted on anything but windows, and the default here is
      // "other" - so the zip import would report failure on a box with no chmod on PATH,
      // over file modes this has no opinion about.
      config.OS = "windows";
      config.GUIMODE = false;
      config.autoskip_enabled = 1;
      config.TIVOS.put("Bolt", "192.168.1.5");
      config.enableRpc.put("Bolt", "1");
   }

   @AfterEach
   void restore() {
      config.programDir = savedProgramDir;
      config.OS = savedOs;
      config.autoskip_enabled = savedEnabled;
      config.GUIMODE = savedMode;
      config.TIVOS.clear();
      config.TIVOS.putAll(savedTivos);
      config.enableRpc.clear();
      config.enableRpc.putAll(savedRpc);
   }

   // The whole point of the feature, in arithmetic: their breaks land in my recording 5 s
   // later than in theirs, because that is where the captions we share moved to.
   @Test
   void theirCutPointsArriveMovedOntoMyRecordingsClock() throws Exception {
      SkipShare.Import("Bolt", json(), theirSrt(), theirEdl(), mySrt(), false);

      Stack<Hashtable<String,Long>> cuts = SkipManager.getEntry(AIRING);
      assertEquals(2, cuts.size());
      assertEquals(30000 + SHIFT, cuts.get(0).get("start").longValue());
      assertEquals(600000 + SHIFT, cuts.get(0).get("end").longValue());
      assertEquals(660000 + SHIFT, cuts.get(1).get("start").longValue());
      // The last segment runs to the end of MY recording, not to theirs
      assertEquals(HALF_HOUR, cuts.get(1).get("end").longValue());
      assertTrue(raw().contains("title=Cheers - The Tortelli Tort"), raw());
   }

   // A share for something else entirely - the wrong episode, or the wrong show. No caption
   // matches, so no point can be moved, and the import used to hand the table an entry with
   // no cut points after deleting the one that was there.
   @Test
   void aShareThatCannotBeLinedUpLeavesTheExistingEntryAlone() throws Exception {
      existingEntry();
      String foreign = srt(work.resolve("foreign.srt"),
         "00:00:40,000", "00:00:42,000", "COMPLETELY DIFFERENT WORDS",
         "00:10:10,000", "00:10:12,000", "NOTHING IN COMMON HERE");

      SkipShare.Import("Bolt", json(), foreign, theirEdl(), mySrt(), false);

      assertEquals(1, countEntries(raw()), "no second entry: " + raw());
      assertEquals(1, SkipManager.getEntry(AIRING).size(), "the entry already there must survive");
      assertEquals(123000L, SkipManager.getEntry(AIRING).get(0).get("end").longValue());
   }

   // Neither srt can be read - the share is missing its captions, or mine were never
   // extracted. There is nothing to line up against at all, and the table is not touched.
   @Test
   void withoutBothSubtitleFilesNothingIsImported() throws Exception {
      existingEntry();
      SkipShare.Import("Bolt", json(), theirSrt(), theirEdl(),
         work.resolve("never-extracted.srt").toString(), false);
      assertEquals(1, countEntries(raw()));
      assertEquals(123000L, SkipManager.getEntry(AIRING).get(0).get("end").longValue());
   }

   // A zip as the share site hands it over: the cut points and the captions they were made
   // against, unpacked into a scratch directory under programDir and thrown away afterwards.
   @Test
   void aShareZipIsUnpackedAndImported() throws Exception {
      Path zip = work.resolve("share.zip");
      try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
         add(out, "Cheers.srt", Files.readAllBytes(Path.of(theirSrt())));
         add(out, "Cheers.edl", Files.readAllBytes(Path.of(theirEdl())));
      }

      assertTrue(SkipShare.ZipImport("Bolt", json(), zip.toString(), mySrt(), false));
      assertEquals(2, SkipManager.getEntry(AIRING).size());
      assertFalse(Files.exists(work.resolve("_SkipImport_")), "the scratch directory is cleaned up");
   }

   // The dialog closes itself and prints "Successfully imported skip share" on true, so a zip
   // that carried no cut points at all must not answer true - the user would be left thinking
   // the recording has skip data.
   @Test
   void aZipWithoutCutPointsIsNotReportedAsImported() throws Exception {
      Path zip = work.resolve("captions-only.zip");
      try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
         add(out, "Cheers.srt", Files.readAllBytes(Path.of(theirSrt())));
      }

      assertFalse(SkipShare.ZipImport("Bolt", json(), zip.toString(), mySrt(), false));
      assertFalse(Files.exists(work.resolve("AutoSkip.ini")), "and nothing was written");
   }

   private static void add(ZipOutputStream out, String name, byte[] content) throws IOException {
      out.putNextEntry(new ZipEntry(name));
      out.write(content);
      out.closeEntry();
   }

   // Their cut points: a break at the head, then one ten minutes in.
   private String theirEdl() throws IOException {
      Path p = work.resolve("theirs.edl");
      Files.write(p, "0.00 30.00 0\n600.00 660.00 0\n".getBytes(StandardCharsets.UTF_8));
      return p.toString();
   }

   private String theirSrt() throws IOException {
      return srt(work.resolve("theirs.srt"),
         "00:00:40,000", "00:00:42,000", "WELCOME BACK TO THE BAR",
         "00:10:10,000", "00:10:12,000", "WHERE EVERYBODY KNOWS YOUR NAME",
         "00:11:30,000", "00:11:32,000", "SAM POURS ANOTHER ONE");
   }

   // The same captions, five seconds later, which is the only thing tying the two recordings
   // together
   private String mySrt() throws IOException {
      return srt(work.resolve("mine.srt"),
         "00:00:45,000", "00:00:47,000", "WELCOME BACK TO THE BAR",
         "00:10:15,000", "00:10:17,000", "WHERE EVERYBODY KNOWS YOUR NAME",
         "00:11:35,000", "00:11:37,000", "SAM POURS ANOTHER ONE");
   }

   private static String srt(Path p, String... blocks) throws IOException {
      StringBuilder s = new StringBuilder();
      for (int i=0; i<blocks.length; i+=3) {
         s.append((i/3)+1).append("\n")
          .append(blocks[i]).append(" --> ").append(blocks[i+1]).append("\n")
          .append(blocks[i+2]).append("\n\n");
      }
      Files.write(p, s.toString().getBytes(StandardCharsets.UTF_8));
      return p.toString();
   }

   private static JSONObject json() throws Exception {
      JSONObject j = new JSONObject();
      j.put("duration", HALF_HOUR);
      j.put("title", "Cheers");
      j.put("subtitle", "The Tortelli Tort");
      j.put("contentId", "tivo:ct.20825");
      j.put("offerId", AIRING);
      return j;
   }

   // Cut points already in the table for this airing - from comskip, or from an earlier share
   private void existingEntry() {
      Stack<Hashtable<String,Long>> cuts = new Stack<Hashtable<String,Long>>();
      Hashtable<String,Long> h = new Hashtable<String,Long>();
      h.put("start", 0L);
      h.put("end", 123000L);
      cuts.push(h);
      SkipManager.saveEntry("tivo:ct.20825", AIRING, 0L, "Cheers", "Bolt", cuts);
   }

   private String raw() throws IOException {
      return new String(Files.readAllBytes(work.resolve("AutoSkip.ini")), StandardCharsets.UTF_8);
   }

   private static int countEntries(String text) {
      int n = 0, i = 0;
      while ((i = text.indexOf("<entry>", i)) >= 0) { n++; i += 7; }
      return n;
   }
}
